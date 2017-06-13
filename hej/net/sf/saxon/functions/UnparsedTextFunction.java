////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.*;
import net.sf.saxon.lib.FeatureKeys;
import net.sf.saxon.regex.LatinString;
import net.sf.saxon.serialize.charcode.UTF16CharacterSet;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.z.IntPredicate;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.UnmappableCharacterException;


/**
 * Abstract superclass containing common code supporting the functions
 * unparsed-text(), unparsed-text-lines(), and unparsed-text-available()
 */

public abstract class UnparsedTextFunction extends SystemFunction {

    public int getSpecialProperties(Expression[] arguments) {
        int p = super.getSpecialProperties(arguments);
        if (getRetainedStaticContext().getConfiguration().getBooleanProperty(FeatureKeys.STABLE_UNPARSED_TEXT)) {
            return p;
        } else {
            return p & ~StaticProperty.NON_CREATIVE;
            // Pretend the function is creative to prevent the result going into a global variable,
            // which takes excessive memory. Unless we're caching anyway, for stability reasons.
        }
    }

    /**
     * Get the prefix of the error code for dynamic errors: "XTDE" for XSLT 2.0, "FOUT" for XPath 3.0
     * @param context the dynamic context
     * @return the first four characters of the error code to be used
     */

    protected static String getErrorCodePrefix(XPathContext context) {
        return "FOUT";
    }


    /**
     * Supporting routine to load one external file given a URI (href) and a baseURI
     * @param absoluteURI the absolutized URI
     * @param encoding the character encoding
     * @param context the XPath dynamic context
     * @return the content of the file
     * @throws XPathException if the file cannot be read
     */

    public static CharSequence readFile(URI absoluteURI, String encoding, XPathContext context)
            throws XPathException {

        final Configuration config = context.getConfiguration();
        IntPredicate checker = config.getValidCharacterChecker();

        // Use the URI machinery to validate and resolve the URIs

        Reader reader;
        try {
            reader = context.getController().getUnparsedTextURIResolver().resolve(absoluteURI, encoding, config);
        } catch (XPathException err) {
            err.maybeSetErrorCode(getErrorCodePrefix(context) + "1170");
            throw err;
        }
        try {
            return readFile(checker, reader, context);
        } catch (java.io.UnsupportedEncodingException encErr) {
            XPathException e = new XPathException("Unknown encoding " + Err.wrap(encoding), encErr);
            e.setErrorCode(getErrorCodePrefix(context) + "1190");
            throw e;
        } catch (java.io.IOException ioErr) {
//            System.err.println("ProxyHost: " + System.getProperty("http.proxyHost"));
//            System.err.println("ProxyPort: " + System.getProperty("http.proxyPort"));
            XPathException e = handleIOError(absoluteURI, ioErr, context);
            throw e;
        }
    }

    public static URI getAbsoluteURI(String href, String baseURI, XPathContext context) throws XPathException {
        URI absoluteURI;
        try {
            absoluteURI = ResolveURI.makeAbsolute(href, baseURI);
        } catch (java.net.URISyntaxException err) {
            XPathException e = new XPathException(err.getReason() + ": " + err.getInput(), err);
            e.setErrorCode(getErrorCodePrefix(context) + "1170");
            throw e;
        }

        if (absoluteURI.getFragment() != null) {
            XPathException e = new XPathException("URI for unparsed-text() must not contain a fragment identifier");
            e.setErrorCode(getErrorCodePrefix(context) + "1170");
            throw e;
        }

        // The URL dereferencing classes throw all kinds of strange exceptions if given
        // ill-formed sequences of %hh escape characters. So we do a sanity check that the
        // escaping is well-formed according to UTF-8 rules

        EncodeForUri.checkPercentEncoding(absoluteURI.toString());
        return absoluteURI;
    }

    public static XPathException handleIOError(URI absoluteURI, IOException ioErr, XPathContext context) {
        String message = "Failed to read input file";
        if (absoluteURI != null && !ioErr.getMessage().equals(absoluteURI.toString())) {
            message += ' ' + absoluteURI.toString();
        }
        message += " (" + ioErr.getClass().getName() + ')';
        XPathException e = new XPathException(message, ioErr);
        String errorCode = "FOUT1200";
        if (context != null) {
            if (ioErr instanceof MalformedInputException) {
                errorCode = getErrorCodePrefix(context) + "1200";
            } else if (ioErr instanceof CharacterCodingException) {
                errorCode = getErrorCodePrefix(context) + "1200";
            } else if (ioErr instanceof UnmappableCharacterException) {
                errorCode = getErrorCodePrefix(context) + "1190";
            } else {
                errorCode = getErrorCodePrefix(context) + "1170";
            }
        }
        e.setErrorCode(errorCode);
        return e;
    }

    /**
     * Read the contents of an unparsed text file
     *
     * @param checker predicate for checking whether characters are valid XML characters
     * @param reader  Reader to be used for reading the file
     * @param context the XPath dynamic context
     * @return a CharSequence representing the contents of the file
     * @throws IOException    if a failure occurs reading the file
     * @throws XPathException if the file contains illegal characters
     */

    public static CharSequence readFile(IntPredicate checker, Reader reader, XPathContext context) throws IOException, XPathException {
        FastStringBuffer sb = new FastStringBuffer(2048);
        char[] buffer = new char[2048];
        boolean first = true;
        int actual;
        int line = 1;
        int column = 1;
        boolean latin = true;
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
                if (ch32 > 255) {
                    latin = false;
                    if (UTF16CharacterSet.isHighSurrogate(ch32)) {
                        if (c == actual) {
                            actual = reader.read(buffer, 0, 2048);
                            c = 0;
                        }
                        char low = buffer[c++];
                        ch32 = UTF16CharacterSet.combinePair((char) ch32, low);
                    }
                }
                if (!checker.matches(ch32)) {
                    XPathException err = new XPathException("The unparsed-text file contains a character that is illegal in XML (line=" +
                            line + " column=" + column + " value=hex " + Integer.toHexString(ch32) + ')');
                    err.setErrorCode(getErrorCodePrefix(context) + "1190");
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
        if (latin) {
            return new LatinString(sb);
        } else {
            return sb.condense();
        }
    }


}

