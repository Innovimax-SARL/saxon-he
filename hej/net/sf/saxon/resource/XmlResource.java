////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.resource;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.functions.URIQueryParameters;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.Resource;
import net.sf.saxon.lib.ResourceFactory;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.trans.XPathException;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;

/**
 * The class is an implementation of the generic Resource object (typically an item in a collection)
 * representing an XML document
 */

public class XmlResource implements Resource {
    private Source source;
    private ParseOptions options;
    private NodeInfo doc;
    private Configuration config;
    private int onError = URIQueryParameters.ON_ERROR_FAIL;

    public final static ResourceFactory FACTORY = new ResourceFactory() {
        public Resource makeResource(Configuration config, String resourceURI, String contentType, AbstractResourceCollection.InputDetails details) throws XPathException {
            Source source = new StreamSource(details.inputStream, resourceURI);
            ParseOptions options = details.parseOptions;
            if (options == null) {
                options = config.getParseOptions();
            }
            return new XmlResource(config, source, options, details.onError);
        }
    };

    /**
     * Create an XML resource using a specific node
     *
     * @param doc    the node in question (usually but not necessarily a document node)
     */

    public XmlResource(NodeInfo doc) {
        this.config = doc.getConfiguration();
        this.doc = doc;
    }

    /**
     * Create an XML resource using a specific node. (Method retained for backwards compatibility).
     * @param config the Saxon Configuration. This must be the configuration to which the node
     *               belongs.
     * @param doc the node in question (usually but not necessarily a document node)
     */

    public XmlResource(Configuration config, NodeInfo doc) {
        this.config = config;
        this.doc = doc;
        if (config != doc.getConfiguration()) {
            throw new IllegalArgumentException("Supplied node belongs to wrong configuration");
        }
    }

    /**
     * Create an XML resource using a JAXP Source object
     *
     * @param config the Saxon Configuration
     * @param source    the JAXP Source object
     * @param options options for parsing the XML if it needs to be parsed
     * @param onError flag indicating what should happen on a parsing error: one of
     *                {@link URIQueryParameters#ON_ERROR_FAIL}, {@link URIQueryParameters#ON_ERROR_WARNING},
     *                or {@link URIQueryParameters#ON_ERROR_IGNORE}
     */

    public XmlResource(Configuration config, Source source, ParseOptions options, int onError) {
        this.config = config;
        this.source = source;
        this.options = options;
        this.onError = onError;
    }

    public String getResourceURI() {
        if (doc == null) {
            return source.getSystemId();
        } else {
            return doc.getSystemId();
        }
    }

    /**
     * Get an item representing the resource: in this case a document node for the XML document.
     * @param context the XPath evaluation context
     * @return the document; or null if there is an error and the error is to be ignored
     * @throws XPathException if (for example) XML parsing fails
     */

    public Item getItem(XPathContext context) throws XPathException {
        if (doc == null) {
            try {
                doc = config.buildDocumentTree(source, options).getRootNode();
            } catch (XPathException e) {
                if (onError == URIQueryParameters.ON_ERROR_FAIL) {
                    XPathException e2 = new XPathException("collection(): failed to parse XML file " + source.getSystemId() + ": " + e.getMessage(),
                                                           e.getErrorCodeLocalPart());
                    throw e2;
                } else if (onError == URIQueryParameters.ON_ERROR_WARNING) {
                    context.getController().warning("collection(): failed to parse XML file " + source.getSystemId() + ": " + e.getMessage(), e.getErrorCodeLocalPart(), null);
                } else {
                    return null;
                }
            }
            if (source instanceof StreamSource && ((StreamSource)source).getInputStream() != null) {
                try {
                    ((StreamSource) source).getInputStream().close();
                } catch (IOException e) {
                    // ignore the failure
                }
            }
        }
        return doc;
    }

    /**
     * Get the media type (MIME type) of the resource if known
     *
     * @return the string "application/xml"
     */

    public String getContentType() {
        return "application/xml";
    }


}
