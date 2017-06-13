////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;

/**
 * This class is a filter that passes all Receiver events through unchanged,
 * except that it changes namecodes to allow for the source and the destination
 * using different NamePools. This is necessary when a stylesheet has been constructed
 * as a general document (e.g. as the result of a transformation) and is passed to
 * newTemplates() to be compiled as a stylesheet.
 *
 * @author Michael Kay
 */


public class NamePoolConverter extends ProxyReceiver {

    NamePool oldPool;
    NamePool newPool;

    /**
     * Constructor
     *
     * @param next    the next receiver in the pipeline
     * @param oldPool the old namepool
     * @param newPool th new namepool
     */

    public NamePoolConverter(Receiver next, NamePool oldPool, NamePool newPool) {
        super(next);
        this.oldPool = oldPool;
        this.newPool = newPool;
    }

    /**
     * Set the underlying emitter. This call is mandatory before using the Emitter.
     * This version is modified from that of the parent class to avoid setting the namePool
     * of the destination Receiver.
     */

    @Override
    public void setUnderlyingReceiver(/*@NotNull*/ Receiver receiver) {
        nextReceiver = receiver;
    }

    /**
     * Output element start tag
     */

    public void startElement(NodeName nameCode, SchemaType typeCode, Location location, int properties) throws XPathException {
        int fp = newPool.allocateFingerprint(nameCode.getURI(), nameCode.getLocalPart());
        nextReceiver.startElement(new CodedName(fp, nameCode.getPrefix(), newPool), typeCode, location, properties);
    }

    /**
     * Handle a namespace
     */

    public void namespace(NamespaceBindingSet namespaceBindings, int properties) throws XPathException {
        nextReceiver.namespace(namespaceBindings, properties);
    }

    /**
     * Handle an attribute
     */

    public void attribute(NodeName nameCode, SimpleType typeCode, CharSequence value, Location locationId, int properties)
            throws XPathException {
        int fp = newPool.allocateFingerprint(nameCode.getURI(), nameCode.getLocalPart());
        nextReceiver.attribute(new CodedName(fp, nameCode.getPrefix(), newPool), typeCode, value, locationId, properties);
    }

}

