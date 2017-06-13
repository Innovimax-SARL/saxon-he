////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.ReceiverOptions;
import net.sf.saxon.event.SequenceWriter;
import net.sf.saxon.expr.parser.ExplicitLocation;
import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;

/**
 * This class implements a Receiver that can receive xsl:message output and send it to a
 * user-supplied MessageListener.
 */

class MessageListenerProxy extends SequenceWriter {

    private MessageListener listener;
    private boolean terminate;
    private Location locationId;
    private String errorCode;

    protected MessageListenerProxy(MessageListener listener, PipelineConfiguration pipe) {
        super(pipe);
        // See bug 2104. We use the Linked Tree model because the TinyTree can use excessive memory. This
        // is because the initial size allocation is based on the size of source documents, which might be large;
        // also because we store several messages in a single TinyTree; and because we fail to condense the tree.
        setTreeModel(TreeModel.LINKED_TREE);
        this.listener = listener;
    }

    /**
     * Get the wrapped MessageListener
     *
     * @return the wrapped MessageListener
     */

    public MessageListener getMessageListener() {
        return listener;
    }


    /**
     * Start of a document node.
     */

    public void startDocument(int properties) throws XPathException {
        terminate = (properties & ReceiverOptions.TERMINATE) != 0;
        locationId = null;
        errorCode = null;
        super.startDocument(properties);
    }


    /**
     * Output an element start tag.
     *  @param nameCode   The element name code - a code held in the Name Pool
     * @param typeCode   Integer code identifying the type of this element. Zero identifies the default
     *                   type, that is xs:anyType
     * @param location
     * @param properties bit-significant flags indicating any special information
     */

    public void startElement(NodeName nameCode, SchemaType typeCode, Location location, int properties) throws XPathException {
        if (this.locationId == null) {
            this.locationId = location;
        }
        super.startElement(nameCode, typeCode, location, properties);
    }

    @Override
    public void processingInstruction(String target, CharSequence data, Location locationId, int properties) throws XPathException {
        if (target.equals("error-code") && errorCode == null) {
            // Suppress the error code, not used in this interface
            errorCode = data.toString();
        } else {
            super.processingInstruction(target, data, locationId, properties);
        }
    }


    /**
     * Produce text content output. <BR>
     *
     * @param s          The String to be output
     * @param locationId
     *@param properties bit-significant flags for extra information, e.g. disable-output-escaping  @throws net.sf.saxon.trans.XPathException
     *          for any failure
     */

    public void characters(CharSequence s, Location locationId, int properties) throws XPathException {
        if (this.locationId == null) {
            this.locationId = locationId;
        }
        super.characters(s, locationId, properties);
    }


    /**
     * Append an item to the sequence, performing any necessary type-checking and conversion
     */

    public void append(Item item, Location locationId, int copyNamespaces) throws XPathException {
        if (this.locationId == null) {
            this.locationId = locationId;
        }
        super.append(item, locationId, copyNamespaces);
    }

    /**
     * Abstract method to be supplied by subclasses: output one item in the sequence.
     *
     * @param item the item to be written to the sequence
     */

    public void write(Item item) throws XPathException {
        Location loc;
        if (locationId == null) {
            loc = ExplicitLocation.UNKNOWN_LOCATION;
        } else {
            loc = locationId.saveLocation();
        }
        listener.message(new XdmNode((NodeInfo) item), terminate, loc);
    }
}

