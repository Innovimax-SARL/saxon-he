////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.ma.arrays.ArrayItem;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.AtomicValue;

/**
 * This receiver is inserted into the output pipeline whenever on-empty or on-non-empty is used (XSLT 3.0).
 * It passes all events to the underlying receiver unchanged, but invokes a callback action when the
 * first item is written.
 */
public class SignificantItemDetector extends ProxyReceiver {

    private int level = 0;
    private boolean empty = true;
    private Action trigger;

    public interface Action {
        void doAction() throws XPathException;
    }

    public SignificantItemDetector(Receiver next, Action trigger) {
        super(next);
        this.trigger = trigger;
    }

    private void start() throws XPathException {
        if (/*level==0 && */empty) {
            trigger.doAction();
            empty = false;
        }
    }

    /**
     * Start of a document node.
     */
    @Override
    public void startDocument(int properties) throws XPathException {
        if (level++ != 0) {
            super.startDocument(properties);
        }
    }

    @Override
    public void startElement(NodeName elemName, SchemaType typeCode, Location location, int properties) throws XPathException {
        start();
        level++;
        super.startElement(elemName, typeCode, location, properties);
    }

    @Override
    public void namespace(NamespaceBindingSet namespaceBindings, int properties) throws XPathException {
        start();
        super.namespace(namespaceBindings, properties);
    }

    @Override
    public void attribute(NodeName nameCode, SimpleType typeCode, CharSequence value, Location locationId, int properties) throws XPathException {
        start();
        super.attribute(nameCode, typeCode, value, locationId, properties);
    }

    @Override
    public void characters(CharSequence chars, Location locationId, int properties) throws XPathException {
        if (chars.length() > 0) {
            start();
        }
        super.characters(chars, locationId, properties);
    }

    @Override
    public void processingInstruction(String target, CharSequence data, Location locationId, int properties) throws XPathException {
        start();
        super.processingInstruction(target, data, locationId, properties);
    }

    @Override
    public void comment(CharSequence chars, Location locationId, int properties) throws XPathException {
        start();
        super.comment(chars, locationId, properties);
    }

    public static boolean isSignificant(Item item) {
        if (item instanceof NodeInfo) {
            NodeInfo node = (NodeInfo) item;
            if (node.getNodeKind() == Type.TEXT && node.getStringValue().isEmpty()) {
                return false;
            }
            if (node.getNodeKind() == Type.DOCUMENT && !node.hasChildNodes()) {
                return false;
            }
        } else if (item instanceof AtomicValue) {
            return !item.getStringValue().isEmpty();
        } else if (item instanceof ArrayItem) {
            if (((ArrayItem) item).isEmpty()) {
                return true;
            } else {
                for (Sequence mem : (ArrayItem) item) {
                    try {
                        SequenceIterator memIter = mem.iterate();
                        Item it;
                        while ((it = memIter.next()) != null) {
                            if (isSignificant(it)) {
                                return true;
                            }
                        }
                    } catch (XPathException e) {
                        return true;
                    }
                }
                return false;
            }
        }
        return true;
    }

    @Override
    public void append(Item item, Location locationId, int copyNamespaces) throws XPathException {
        if (isSignificant(item)) {
            start();
        }
        super.append(item, locationId, copyNamespaces);
    }

    /**
     * Notify the end of a document node
     */
    @Override
    public void endDocument() throws XPathException {
        if (--level != 0) {
            super.endDocument();
        }
    }

    /**
     * End of element
     */
    @Override
    public void endElement() throws XPathException {
        level--;
        super.endElement();
    }

    /**
     * Ask if the sequence that has been written so far is considered empty
     * @return true if no significant items have been written (or started)
     */

    public boolean isEmpty() {
        return empty;
    }

}

