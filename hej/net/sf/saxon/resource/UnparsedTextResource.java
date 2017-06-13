////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.resource;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.Resource;
import net.sf.saxon.lib.ResourceFactory;
import net.sf.saxon.lib.StandardUnparsedTextResolver;
import net.sf.saxon.om.Item;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.StringValue;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * This class implements th interface Resource. We handle unparded text here.
 * The Resource objects belong to a collection
 * It is used to support the fn:collection() and fn:uri-collection() functions.
 *
 * @since 9.7
 */
public class UnparsedTextResource implements Resource {
    private String encoding;
    private String href = null;
    private String unparsedText = null;
    private InputStream inputStream;

    /**
     * Create an UnparsedTextResource
     * @param config the Saxon Configuration (not currently used)
     * @param href the URI of the resource
     * @param details the input stream giving access to the content of the resource, plus encoding info
     */

    public UnparsedTextResource(Configuration config, String href, AbstractResourceCollection.InputDetails details) {
        this.href = href;
        this.inputStream = details.inputStream;
        this.encoding = details.encoding;
    }

    public final static ResourceFactory FACTORY = new ResourceFactory() {
        public Resource makeResource(Configuration config, String resourceURI, String contentType, AbstractResourceCollection.InputDetails details) throws XPathException {
            return new UnparsedTextResource(config, resourceURI, details);
        }
    };

    public String getResourceURI() {
        return href;
    }

    public Item getItem(XPathContext context) throws XPathException {
        if (unparsedText == null) {
            StringBuilder builder = null;
            try {
                String enc = encoding;
                if (enc == null) {
                    enc = StandardUnparsedTextResolver.inferStreamEncoding(inputStream, null);
                }
                builder = CatalogCollection.makeStringBuilderFromStream(inputStream, enc);
            } catch (FileNotFoundException e) {
                throw new XPathException(e);
            } catch (IOException e) {
                throw new XPathException(e);
            }
            unparsedText = builder.toString();
        }
        return new StringValue(unparsedText);
    }

    /**
     * Get the media type (MIME type) of the resource if known
     * @return the string "text/plain"
     */

    public String getContentType() {
        return "text/plain";
    }


}
