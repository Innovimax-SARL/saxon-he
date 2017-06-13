////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.resource;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.CollectionFinder;
import net.sf.saxon.lib.CollectionURIResolver;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.ResourceCollection;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AnyURIValue;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


/**
 * The class bridges the old CollectionURIResolver interface to the new CollectionFinder interface; it means
 * that existing CollectionURIResolver implementations can continue to be used with Saxon 9.7.
 */

public class CollectionURIResolverWrapper implements CollectionFinder {

    private CollectionURIResolver collectionURIResolver = null;

    /**
     * Create a wrapper for a CollectionURIResolver
     * @param cr the CollectionURIResolver
     */

    public CollectionURIResolverWrapper(CollectionURIResolver cr) {
        collectionURIResolver = cr;
    }


    public ResourceCollection findCollection(final XPathContext context, final String collectionURI) throws XPathException {
        return new ResourceCollection() {
            private SpaceStrippingRule whitespaceRules;

            public String getCollectionURI() {
                return collectionURI;
            }

            public Iterator<String> getResourceURIs(XPathContext context) throws XPathException {
                List<String> uris = new ArrayList<String>();
                SequenceIterator iter = collectionURIResolver.resolve(collectionURI, "", context);
                Item item;
                while ((item = iter.next()) != null) {
                    if (item instanceof AnyURIValue) {
                        uris.add(item.getStringValue());
                    } else if (item instanceof NodeInfo) {
                        uris.add(((NodeInfo)item).getSystemId());
                    } else {
                        throw new XPathException("Result of CollectionURIResolver must consist of xs:anyURI values and/or nodes");
                    }
                }
                return uris.iterator();
            }

            public Iterator<XmlResource> getResources(XPathContext context) throws XPathException {
                List<XmlResource> resources = new ArrayList<XmlResource>();
                SequenceIterator iter = collectionURIResolver.resolve(collectionURI, "", context);
                Configuration config = context.getConfiguration();
                URIResolver uriResolver = config.getURIResolver();
                ParseOptions options = new ParseOptions(config.getParseOptions());
                options.setSpaceStrippingRule(whitespaceRules);
                Item item;
                while ((item = iter.next()) != null) {
                    if (item instanceof AnyURIValue) {
                        Source source;
                        try {
                            source = uriResolver.resolve(item.getStringValue(), "");
                        } catch (TransformerException e) {
                            throw XPathException.makeXPathException(e);
                        }
                        TreeInfo treeInfo = config.buildDocumentTree(source, options);
                        resources.add(new XmlResource(treeInfo.getRootNode()));
                    } else if (item instanceof NodeInfo) {
                        resources.add(new XmlResource((NodeInfo)item));
                    } else {
                        throw new XPathException("Result of CollectionURIResolver must consist of xs:anyURI values and/or nodes");
                    }
                }

                return resources.iterator();
            }

            public boolean isStable(XPathContext context) {
                return false;
            }

            public boolean stripWhitespace(SpaceStrippingRule rules) {
                whitespaceRules = rules;
                return true;
            }
        };
    }


    public CollectionURIResolver getCollectionURIResolver() {
        return collectionURIResolver;
    }
}
