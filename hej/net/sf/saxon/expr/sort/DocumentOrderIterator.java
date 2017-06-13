////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.expr.LastPositionFinder;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.ListIterator;

import java.util.ArrayList;

/**
 * DocumentOrderIterator takes as input an iteration of nodes in any order, and
 * returns as output an iteration of the same nodes in document order, eliminating
 * any duplicates.
 */

public final class DocumentOrderIterator implements SequenceIterator, Sortable {

    private SequenceIterator iterator;
    private ArrayList<NodeInfo> sequence;
    private ItemOrderComparer comparer;
    private NodeInfo current = null;

    /**
     * Iterate over a sequence in document order.
     * @param base the input sequence to be sorted
     * @param comparer the comparer used for comparing node positions
     * @throws XPathException if the input sequence cannot be evaluated
     */

    public DocumentOrderIterator(SequenceIterator base, ItemOrderComparer comparer) throws XPathException {

        this.comparer = comparer;

        int len = (base.getProperties() & SequenceIterator.LAST_POSITION_FINDER) == 0
                ? 50
                : ((LastPositionFinder) base).getLength();
        sequence = new ArrayList<NodeInfo>(len);
        NodeInfo node;
        while ((node = (NodeInfo)base.next()) != null) {
            sequence.add(node);
        }
        //System.err.println("sort into document order: sequence length = " + sequence.getLength());
        if (sequence.size() > 1) {
            GenericSorter.quickSort(0, sequence.size(), this);
        }
        iterator = new ListIterator(sequence);
    }

    /**
     * Private constructor used only by getAnother()
     */

    private DocumentOrderIterator() {
    }

    /**
     * Compare two nodes in document sequence
     * (needed to implement the Sortable interface)
     */

    public int compare(int a, int b) {
        //System.err.println("compare " + a + " with " + b);
        return comparer.compare(sequence.get(a), sequence.get(b));
    }

    /**
     * Swap two nodes (needed to implement the Sortable interface)
     */

    public void swap(int a, int b) {
        NodeInfo temp = sequence.get(a);
        sequence.set(a, sequence.get(b));
        sequence.set(b, temp);
    }

    // Implement the SequenceIterator as a wrapper around the underlying iterator
    // over the sequenceExtent, but looking ahead to remove duplicates.

    public NodeInfo next() throws XPathException {
        while (true) {
            NodeInfo next = (NodeInfo)iterator.next();
            if (next == null) {
                current = null;
                return null;
            }
            if (current == null || !next.isSameNodeInfo(current)) {
                current = next;
                return current;
            }
        }
    }

    /**
     * Get properties of this iterator, as a bit-significant integer.
     *
     * @return the properties of this iterator. This will be some combination of
     *         properties such as {@link #GROUNDED}, {@link #LAST_POSITION_FINDER},
     *         and {@link #LOOKAHEAD}. It is always
     *         acceptable to return the value zero, indicating that there are no known special properties.
     *         It is acceptable for the properties of the iterator to change depending on its state.
     */

    public int getProperties() {
        return 0;
    }

    public void close() {
    }

}

