////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.resource;

import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.functions.CallableFunction;
import net.sf.saxon.lib.Resource;
import net.sf.saxon.ma.map.HashTrieMap;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.FunctionItemType;
import net.sf.saxon.type.SpecificFunctionType;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;

import java.util.Map;

/**
 * Created by mike on 28/10/15.
 */
public class MetadataResource implements Resource {

    private Map<String, Sequence> properties;
    private String resourceURI;
    private Resource content;

    public MetadataResource(String resourceURI, Resource content, Map<String, Sequence> properties) {
        this.resourceURI = resourceURI;
        this.content = content;
        this.properties = properties;
    }

    public String getContentType() {
        return content.getContentType();
    }

    public String getResourceURI() {
        return resourceURI;
    }

    public Item getItem(XPathContext context) throws XPathException {

        // Create a map for the result
        HashTrieMap map = new HashTrieMap();

        // Add the custom properties of the resource
        for (Map.Entry<String, Sequence> entry : properties.entrySet()) {
             map = map.addEntry(StringValue.makeStringValue(entry.getKey()), entry.getValue());
        }

        // Add the resourceURI of the resource as the "name" property
        map = map.addEntry(StringValue.makeStringValue("name"), StringValue.makeStringValue(resourceURI));

        // Add a fetch() function, which can be used to fetch the resource
        Callable fetcher = new Callable() {
            public Item call(XPathContext context, Sequence[] arguments) throws XPathException {
                return content.getItem(context);
            }
        };

        FunctionItemType fetcherType = new SpecificFunctionType(new SequenceType[0], SequenceType.SINGLE_ITEM);
        CallableFunction fetcherFunction = new CallableFunction(0, fetcher, fetcherType);

        map = map.addEntry(StringValue.makeStringValue("fetch"), fetcherFunction);
        return map;
    }
}

