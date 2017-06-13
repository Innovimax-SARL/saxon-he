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
import net.sf.saxon.ma.json.ParseJsonFn;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.StringValue;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * A Resource (that is, an item in a collection) holding JSON content
 */

public class JSONResource implements Resource {
    private String href;
    private String jsonStr;
    private InputStream inputStream;
    private String encoding;

    public final static ResourceFactory FACTORY = new ResourceFactory() {
        public Resource makeResource(Configuration config, String resourceURI, String contentType, AbstractResourceCollection.InputDetails details) throws XPathException {
            return new JSONResource(resourceURI, details);
        }
    };

    /**
     * Create the resource
     * @param href the URI of the resource
     * @param details the inputstream holding the JSON content plus details of encoding etc
     */

    public JSONResource(String href, AbstractResourceCollection.InputDetails details) {
        this.href = href;
        this.inputStream = details.inputStream;
        this.encoding = details.encoding;
        if (encoding == null) {
            encoding = "UTF-8";
        }
    }

    /**
     * Create the resource
     *
     * @param href the URI of the resource
     * @param in   the inputstream holding the JSON content
     */

    public JSONResource(String href, InputStream in) {
        this.href = href;
        this.inputStream = in;
        this.encoding = "utf-8";
    }


    public String getResourceURI() {
        return href;
    }

    public Item getItem(XPathContext context) throws XPathException {
        if (jsonStr == null) {
            try {
                StringBuilder sb = CatalogCollection.makeStringBuilderFromStream(inputStream, encoding);
                jsonStr = sb.toString();
            } catch (IOException e) {
                throw new XPathException(e);
            }
        }
        Map<String, Sequence> options = new HashMap<String, Sequence>();
        options.put("liberal", BooleanValue.FALSE);
        options.put("duplicates", new StringValue("use-first"));
        options.put("escape", BooleanValue.FALSE);
        return ParseJsonFn.parse(jsonStr, options, context);
    }

    /**
     * Get the media type (MIME type) of the resource if known
     *
     * @return the string "application/json"
     */

    public String getContentType() {
        return "application/json";
    }


}
