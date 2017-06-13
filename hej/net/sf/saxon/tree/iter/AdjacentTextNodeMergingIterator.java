////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.iter;

import net.sf.saxon.expr.AdjacentTextNodeMerger;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.tree.util.Orphan;
import net.sf.saxon.type.Type;

/**
 * AdjacentTextNodeMergingIterator is an iterator that eliminates zero-length text nodes
 * and merges adjacent text nodes from the underlying iterator
 */

public class AdjacentTextNodeMergingIterator implements LookaheadIterator {

    private SequenceIterator base;
    /*@Nullable*/ private Item next;

    public AdjacentTextNodeMergingIterator(/*@NotNull*/ SequenceIterator base) throws XPathException {
        this.base = base;
        next = base.next();
    }

    public boolean hasNext() {
        return next != null;
    }

    /*@Nullable*/
    public Item next() throws XPathException {
        Item current = next;
        if (current == null) {
            return null;
        }
        next = base.next();

        if (AdjacentTextNodeMerger.isTextNode(current)) {
            FastStringBuffer fsb = new FastStringBuffer(FastStringBuffer.C256);
            fsb.append(current.getStringValueCS());
            while (next != null && AdjacentTextNodeMerger.isTextNode(next)) {
                fsb.append(next.getStringValueCS() /*.toString() */);
                // NOTE: toString() shouldn't be necessary - added 2011-05-05 for bug workaround; removed again 2011-07-14
                next = base.next();
            }
            if (fsb.isEmpty()) {
                return next();
            } else {
                Orphan o = new Orphan(((NodeInfo) current).getConfiguration());
                o.setNodeKind(Type.TEXT);
                o.setStringValue(fsb);
                current = o;
                return current;
            }
        } else {
            return current;
        }
    }

    public void close() {
        base.close();
    }

    public int getProperties() {
        return LOOKAHEAD;
    }
}

