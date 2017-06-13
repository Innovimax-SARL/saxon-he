////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.expr.parser.ExplicitLocation;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;

/**
 * Copies a sequence, supplied as a SequenceIterator, to a push pipeline, represented by
 * a SequenceReceiver
 */
public class SequenceCopier {

    private SequenceCopier() {
    }

    public static void copySequence(SequenceIterator in, SequenceReceiver out) throws XPathException {
        out.open();
        Item item;
        while ((item = in.next()) != null) {
            out.append(item, ExplicitLocation.UNKNOWN_LOCATION, NodeInfo.ALL_NAMESPACES);
        }
        out.close();
    }
}

