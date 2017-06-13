////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.Configuration;
import net.sf.saxon.resource.AbstractResourceCollection;
import net.sf.saxon.trans.XPathException;

/**
 * A ResourceFactory is used for constructing a particular type of resource
 */

public interface ResourceFactory {
    /**
     * Create a Resource with given content
     * @param config the Saxon configuration
     * @param resourceURI the URI identifying the resource
     * @param contentType the content type ( = media type or MIME type) of the resource
     * @param details the stream of bytes making up the binary content of the resource
     * @return the resource
     * @throws XPathException if a failure occurs creating the resource
     */
    Resource makeResource(Configuration config, String resourceURI, String contentType, AbstractResourceCollection.InputDetails details)
        throws XPathException;
}

