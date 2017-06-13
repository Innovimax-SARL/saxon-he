////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.FeatureKeys;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.serialize.charcode.UTF16CharacterSet;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.UnparsedTextIterator;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.z.IntPredicate;

import java.io.*;
import java.net.URI;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.UnmappableCharacterException;


public class UnparsedTextLines extends UnparsedTextFunction implements Callable {

    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as SequenceIterators
     * @return the result of the evaluation, in the form of a SequenceIterator
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        StringValue hrefVal = (StringValue) arguments[0].head();
        String encoding = getArity() == 2 ? arguments[1].head().getStringValue() : null;
        return SequenceTool.toLazySequence(evalUnparsedTextLines(hrefVal, encoding, context));
    }

    private SequenceIterator evalUnparsedTextLines(StringValue hrefVal, String encoding, XPathContext context) throws XPathException {
        if (hrefVal == null) {
            return net.sf.saxon.tree.iter.EmptyIterator.emptyIterator();
        }
        String href = hrefVal.getStringValue();
        boolean stable = context.getConfiguration().getBooleanProperty(FeatureKeys.STABLE_UNPARSED_TEXT);
        if (stable) {
            // if results have to be stable, the text has to be read into memory and cached
            StringValue content = UnparsedText.evalUnparsedText(hrefVal, getStaticBaseUriString(), encoding, context);
            URI abs = getAbsoluteURI(href, getStaticBaseUriString());
            LineNumberReader reader = new LineNumberReader(new StringReader(content.getStringValue()));
            return new UnparsedTextIterator(reader, abs, context, encoding);
        } else {
            final URI absoluteURI = getAbsoluteURI(href, getRetainedStaticContext().getStaticBaseUriString());
            return new UnparsedTextIterator(absoluteURI, context, encoding, null);
        }
    }

    /**
     * Supporting routine to load one external file given a URI (href) and a baseURI
     */

    public CharSequence readFile(String href, String baseURI, String encoding, XPathContext context)
            throws XPathException {

        final Configuration config = context.getConfiguration();
        IntPredicate checker = config.getValidCharacterChecker();

        // Use the URI machinery to validate and resolve the URIs

        URI absoluteURI = getAbsoluteURI(href, baseURI);

        Reader reader;
        try {
            reader = context.getController().getUnparsedTextURIResolver().resolve(absoluteURI, encoding, config);
        } catch (XPathException err) {
            err.maybeSetErrorCode("FOUT1170");
            throw err;
        }
        try {
            return readFile(checker, reader);
        } catch (UnsupportedEncodingException encErr) {
            XPathException e = new XPathException("Unknown encoding " + Err.wrap(encoding), encErr);
            e.setErrorCode("FOUT1190");
            throw e;
        } catch (IOException ioErr) {
//            System.err.println("ProxyHost: " + System.getProperty("http.proxyHost"));
//            System.err.println("ProxyPort: " + System.getProperty("http.proxyPort"));
            throw handleIOError(absoluteURI, ioErr);
        }
    }

    public static URI getAbsoluteURI(String href, String baseURI) throws XPathException {
        URI absoluteURI;
        try {
            absoluteURI = ResolveURI.makeAbsolute(href, baseURI);
        } catch (java.net.URISyntaxException err) {
            XPathException e = new XPathException(err.getReason() + ": " + err.getInput(), err);
            e.setErrorCode("FOUT1170");
            throw e;
        }

        if (absoluteURI.getFragment() != null) {
            XPathException e = new XPathException("URI for unparsed-text() must not contain a fragment identifier");
            e.setErrorCode("FOUT1170");
            throw e;
        }

        // The URL dereferencing classes throw all kinds of strange exceptions if given
        // ill-formed sequences of %hh escape characters. So we do a sanity check that the
        // escaping is well-formed according to UTF-8 rules

        EncodeForUri.checkPercentEncoding(absoluteURI.toString());
        return absoluteURI;
    }

    public static XPathException handleIOError(URI absoluteURI, IOException ioErr) {
        String message = "Failed to read input file";
        if (!ioErr.getMessage().equals(absoluteURI.toString())) {
            message += ' ' + absoluteURI.toString();
        }
        message += " (" + ioErr.getClass().getName() + ')';
        XPathException e = new XPathException(message, ioErr);
        String errorCode;
        if (ioErr instanceof MalformedInputException) {
            errorCode = "FOUT1200";
        } else if (ioErr instanceof CharacterCodingException) {
            errorCode = "FOUT1200";
        } else if (ioErr instanceof UnmappableCharacterException) {
            errorCode = "FOUT1190";
        } else {
            errorCode = "FOUT1170";
        }
        e.setErrorCode(errorCode);
        return e;
    }

    /**
     * Read the contents of an unparsed text file
     *
     * @param checker NameChecker for checking whether characters are valid XML characters
     * @param reader  Reader to be used for reading the file
     * @return a CharSequence representing the contents of the file
     * @throws java.io.IOException if a failure occurs reading the file
     * @throws net.sf.saxon.trans.XPathException
     *                             if the file contains illegal characters
     */

    public static CharSequence readFile(IntPredicate checker, Reader reader) throws IOException, XPathException {
        FastStringBuffer sb = new FastStringBuffer(2048);
        char[] buffer = new char[2048];
        boolean first = true;
        int actual;
        int line = 1;
        int column = 1;
        while (true) {
            actual = reader.read(buffer, 0, 2048);
            if (actual < 0) {
                break;
            }
            for (int c = 0; c < actual; ) {
                int ch32 = buffer[c++];
                if (ch32 == '\n') {
                    line++;
                    column = 0;
                }
                column++;
                if (UTF16CharacterSet.isHighSurrogate(ch32)) {
                    if (c == actual) {
                        actual = reader.read(buffer, 0, 2048);
                        c = 0;
                    }
                    char low = buffer[c++];
                    ch32 = UTF16CharacterSet.combinePair((char) ch32, low);
                }
                if (!checker.matches(ch32)) {
                    XPathException err = new XPathException("The unparsed-text file contains a character that is illegal in XML (line=" +
                            line + " column=" + column + " value=hex " + Integer.toHexString(ch32) + ')');
                    err.setErrorCode("FOUT1190");
                    throw err;
                }
            }
            if (first) {
                first = false;
                if (buffer[0] == '\ufeff') {
                    // don't include the BOM in the result
                    sb.append(buffer, 1, actual - 1);
                } else {
                    sb.append(buffer, 0, actual);
                }
            } else {
                sb.append(buffer, 0, actual);
            }
        }
        reader.close();
        return sb.condense();
    }

    // diagnostic method to output the octets of a file

    public static void main(String[] args) throws Exception {
        FastStringBuffer sb1 = new FastStringBuffer(FastStringBuffer.C256);
        FastStringBuffer sb2 = new FastStringBuffer(FastStringBuffer.C256);
        File file = new File(args[0]);
        InputStream is = new FileInputStream(file);
        while (true) {
            int b = is.read();
            if (b < 0) {
                System.out.println(sb1.toString());
                System.out.println(sb2.toString());
                break;
            }
            sb1.append(Integer.toHexString(b) + " ");
            sb2.append((char) b + " ");
            if (sb1.length() > 80) {
                System.out.println(sb1.toString());
                System.out.println(sb2.toString());
                sb1 = new FastStringBuffer(FastStringBuffer.C256);
                sb2 = new FastStringBuffer(FastStringBuffer.C256);
            }
        }
        is.close();
    }

}

// Copyright (c) 2012 Saxonica Limited.