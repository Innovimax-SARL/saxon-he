////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.dom;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Sender;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.Statistics;
import net.sf.saxon.tree.tiny.TinyBuilder;
import net.sf.saxon.tree.tiny.TinyDocumentImpl;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.transform.sax.SAXSource;
import java.io.File;
import java.io.IOException;

/**
 * This class implements the JAXP DocumentBuilder interface, allowing a Saxon TinyTree to be
 * constructed using standard JAXP parsing interfaces. The returned DOM document node is a wrapper
 * over the Saxon TinyTree structure. Note that although this wrapper
 * implements the DOM interfaces, it is read-only, and all attempts to update it will throw
 * an exception. No schema or DTD validation is carried out on the document.
 * The DocumentBuilder is always namespace-aware.
 */

public class DocumentBuilderImpl extends DocumentBuilder {

    private Configuration config;
    private ParseOptions parseOptions = new ParseOptions();

    /**
     * Set the Saxon Configuration to be used by the document builder.
     * This non-JAXP method must be called if the resulting document is to be used
     * within a Saxon query or transformation. If no Configuration is supplied,
     * Saxon creates a Configuration on the first call to the {@link #parse} method,
     * and subsequent calls reuse the same Configuration.
     * <p/>
     * <p>As an alternative to calling this method, a Configuration can be supplied by calling
     * <code>setAttribute(FeatureKeys.CONFIGURATION, config)</code> on the <code>DocumentBuilderFactory</code>
     * object, where <code>config</code> can be obtained by calling
     * <code>getAttribute(FeatureKeys.CONFIGURATION)</code> on the <code>TransformerFactory</code>.</p>
     *
     * @param config the Saxon configuration
     * @since Saxon 8.8
     */

    public void setConfiguration(Configuration config) {
        this.config = config;
    }

    /**
     * Get the Saxon Configuration to be used by the document builder. This is
     * a non-JAXP method.
     *
     * @return the Configuration previously supplied to {@link #setConfiguration},
     * or the Configuration created automatically by Saxon on the first call to the
     * {@link #parse} method, or a newly constructed Configuration if no Configuration has been supplied and
     * the {@link #parse} method has not been called.
     * @since Saxon 8.8
     */

    public Configuration getConfiguration() {
        if (config == null) {
            config = new Configuration();
        }
        return config;
    }

    /**
     * Indicates whether or not this document builder is configured to
     * understand namespaces.
     *
     * @return true if this document builder is configured to understand
     * namespaces. This implementation always returns true.
     */

    public boolean isNamespaceAware() {
        return true;
    }

    /**
     * Determine whether the document builder should perform DTD validation
     *
     * @param state set to true to request DTD validation
     */

    public void setValidating(boolean state) {
        parseOptions.setDTDValidationMode(state ? Validation.STRICT : Validation.SKIP);
    }

    /**
     * Indicates whether or not this document builder is configured to
     * validate XML documents against a DTD.
     *
     * @return true if this parser is configured to validate
     * XML documents against a DTD; false otherwise.
     */

    public boolean isValidating() {
        return parseOptions.getDTDValidationMode() == Validation.STRICT;
    }

    /**
     * Create a new Document Node.
     *
     * @throws UnsupportedOperationException (always). The only way to build a document using this DocumentBuilder
     *                                       implementation is by using the parse() method.
     */

    public Document newDocument() {
        throw new UnsupportedOperationException("The only way to build a document using this DocumentBuilder is with the parse() method");
    }

    /**
     * Parse the content of the given input source as an XML document
     * and return a new DOM {@link Document} object.
     * <p/>
     * <p>Note: for this document to be usable as part of a Saxon query or transformation,
     * the document should be built within the {@link Configuration} in which that query
     * or transformation is running. This can be achieved using the non-JAXP
     * {@link #setConfiguration} method.
     *
     * @param in InputSource containing the content to be parsed. Note that if
     *           an EntityResolver or ErrorHandler has been supplied, then the XMLReader contained
     *           in this InputSource will be modified to register this EntityResolver or ErrorHandler,
     *           replacing any that was previously registered.
     * @return A new DOM Document object.
     * @throws SAXException If any parse errors occur.
     */

    public Document parse(InputSource in) throws SAXException {
        try {
            if (config == null) {
                config = new Configuration();
            }
            TinyBuilder builder = new TinyBuilder(config.makePipelineConfiguration());
            builder.setStatistics(Statistics.SOURCE_DOCUMENT_STATISTICS);
            SAXSource source = new SAXSource(in);
            source.setSystemId(in.getSystemId());
            Sender.send(source, builder, parseOptions);
            TinyDocumentImpl doc = (TinyDocumentImpl) builder.getCurrentRoot();
            builder.reset();
            return (Document) DocumentOverNodeInfo.wrap(doc);
        } catch (XPathException err) {
            throw new SAXException(err);
        }
    }

    /**
     * Parse the content of the given file as an XML document
     * and return a new DOM {@link Document} object.
     * An <code>IllegalArgumentException</code> is thrown if the
     * <code>File</code> is <code>null</code> null.
     * <p/>
     * <p><i>This implementation differs from the parent implementation
     * by using a correct algorithm for filename-to-uri conversion.<i></p>
     *
     * @param f The file containing the XML to parse.
     * @return A new DOM Document object.
     * @throws java.io.IOException If any IO errors occur.
     * @throws SAXException        If any parse errors occur.
     */

    public Document parse(/*@Nullable*/ File f) throws SAXException, IOException {
        if (f == null) {
            throw new IllegalArgumentException("File cannot be null");
        }

        String uri = f.toURI().toString();
        InputSource in = new InputSource(uri);
        return parse(in);
    }


    /**
     * Specify the {@link EntityResolver} to be used to resolve
     * entities present in the XML document to be parsed.
     *
     * @param er The <code>EntityResolver</code> to be used to resolve entities
     *           present in the XML document to be parsed.
     */

    public void setEntityResolver(EntityResolver er) {
        parseOptions.setEntityResolver(er);
    }

    /**
     * Specify the {@link ErrorHandler} to be used by the parser.
     *
     * @param eh The <code>ErrorHandler</code> to be used by the parser.
     */


    public void setErrorHandler(ErrorHandler eh) {
        parseOptions.setErrorHandler(eh);
    }

    /**
     * Obtain an instance of a {@link DOMImplementation} object.
     *
     * @return A new instance of a <code>DOMImplementation</code>.
     */

    public DOMImplementation getDOMImplementation() {
        return newDocument().getImplementation();
    }

    /**
     * <p>Set state of XInclude processing.</p>
     * <p/>
     * <p>If XInclude markup is found in the document instance, should it be
     * processed as specified in <a href="http://www.w3.org/TR/xinclude/">
     * XML Inclusions (XInclude) Version 1.0</a>.</p>
     * <p/>
     * <p>XInclude processing defaults to <code>false</code>.</p>
     *
     * @param state Set XInclude processing to <code>true</code> or
     *              <code>false</code>
     */
    public void setXIncludeAware(boolean state) {
        parseOptions.setXIncludeAware(state);
    }


    /**
     * <p>Get the XInclude processing mode for this parser.</p>
     *
     * @return the return value of
     * the {@link javax.xml.parsers.DocumentBuilderFactory#isXIncludeAware()}
     * when this parser was created from factory.
     * @throws UnsupportedOperationException For backward compatibility, when implementations for
     *                                       earlier versions of JAXP is used, this exception will be
     *                                       thrown.
     * @see javax.xml.parsers.DocumentBuilderFactory#setXIncludeAware(boolean)
     * @since JAXP 1.5, Saxon 8.9
     */
    public boolean isXIncludeAware() {
        return parseOptions.isXIncludeAware();
    }

    /**
     * Set the space-stripping action to be applied to the source document
     *
     * @param stripAction one of {@link net.sf.saxon.value.Whitespace#IGNORABLE},
     *                    {@link net.sf.saxon.value.Whitespace#ALL}, or {@link net.sf.saxon.value.Whitespace#NONE}
     * @since 8.9
     */

    public void setStripSpace(int stripAction) {
        parseOptions.setStripSpace(stripAction);
    }

    /**
     * Get the space-stripping action to be applied to the source document
     *
     * @return one of {@link net.sf.saxon.value.Whitespace#IGNORABLE},
     * {@link net.sf.saxon.value.Whitespace#ALL}, or {@link net.sf.saxon.value.Whitespace#NONE}
     * @since 8.9
     */

    public int getStripSpace() {
        return parseOptions.getStripSpace();
    }

    /**
     * Set the XML parsing options to be used
     *
     * @param options the XML parsing options. Options set using this method will override any options previously set
     *                using other methods; options subsequently set using other methods will modify the parseOptions
     *                object supplied using this method
     * @since 9.3
     */

    public void setParseOptions(ParseOptions options) {
        this.parseOptions = options;
    }

    /**
     * Get the XML parsing options that have been set using setParseOptions and other setter methods
     *
     * @return the XML parsing options to be used
     * @since 9.3
     */

    public ParseOptions getParseOptions() {
        return parseOptions;
    }

}


