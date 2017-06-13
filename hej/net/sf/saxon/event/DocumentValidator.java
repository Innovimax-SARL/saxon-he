////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.value.Whitespace;

/**
 * DocumentValidator checks that a document is well-formed: specifically, that it contains a single element
 * node child and no text node children.
 */

public class DocumentValidator extends ProxyReceiver {
    private boolean foundElement = false;
    private int level = 0;
    private String errorCode;

    public DocumentValidator(Receiver next, String errorCode) {
        super(next);
        this.errorCode = errorCode;
    }

    public void setPipelineConfiguration(/*@NotNull*/ PipelineConfiguration config) {
        super.setPipelineConfiguration(config);
    }

    /**
     * Start of an element
     */

    public void startElement(NodeName nameCode, SchemaType typeCode, Location location, int properties) throws XPathException {
        if (foundElement && level == 0) {
            throw new XPathException("A valid document must have only one child element", errorCode);
        }
        foundElement = true;
        level++;
        nextReceiver.startElement(nameCode, typeCode, location, properties);
    }

    /**
     * Character data
     */

    public void characters(CharSequence chars, Location locationId, int properties) throws XPathException {
        if (level == 0) {
            if (Whitespace.isWhite(chars)) {
                return; // ignore whitespace outside the outermost element
            }
            throw new XPathException("A valid document must contain no text outside the outermost element", errorCode);
        }
        nextReceiver.characters(chars, locationId, properties);
    }

    /**
     * End of element
     */

    public void endElement() throws XPathException {
        level--;
        nextReceiver.endElement();
    }

    /**
     * Notify the end of a document node
     */

    public void endDocument() throws XPathException {
        if (level == 0) {
            if (!foundElement) {
                throw new XPathException("A valid document must have a child element", errorCode);
            }
            foundElement = false;
            nextReceiver.endDocument();
            level = -1;
        }
    }
}

// Copyright (c) 2004-2007 Saxonica Limited

// This file was previously included in the open-source Saxon-B product, but was unused in Saxon-B.
