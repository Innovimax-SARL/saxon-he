////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.om.AtomicSequence;
import net.sf.saxon.om.AtomizedValueIterator;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AtomicValue;

/**
 * This iterator returns a sequence of atomic values, the result of atomizing the sequence
 * of nodes returned by an underlying SequenceIterator.
 */

public final class AxisAtomizingIterator implements SequenceIterator {

    private AtomizedValueIterator base;
    private AtomicSequence results = null;
    private int atomicPosition = 0;

    /**
     * Construct an atomizing iterator
     *
     * @param base the base iterator (whose nodes are to be atomized)
     */

    public AxisAtomizingIterator(AtomizedValueIterator base) {
        this.base = base;
    }

    public AtomicValue next() throws XPathException {
        while (true) {
            if (results != null) {
                if (atomicPosition < results.getLength()) {
                    return results.itemAt(atomicPosition++);
                } else {
                    results = null;
                    continue;
                }
            }

            AtomicSequence atomized = base.nextAtomizedValue();
            if (atomized == null) {
                results = null;
                return null;
            }
            if (atomized instanceof AtomicValue) {
                // common case (the atomized value of the node is a single atomic value)
                results = null;
                return (AtomicValue) atomized;
            } else {
                results = atomized;
                atomicPosition = 0;
                // continue
            }
        }
    }

    public void close() {
        base.close();
    }

    /**
     * Get properties of this iterator, as a bit-significant integer.
     *
     * @return the properties of this iterator. This will be some combination of
     *         properties such as {@link net.sf.saxon.om.SequenceIterator#GROUNDED}, {@link net.sf.saxon.om.SequenceIterator#LAST_POSITION_FINDER},
     *         and {@link net.sf.saxon.om.SequenceIterator#LOOKAHEAD}. It is always
     *         acceptable to return the value zero, indicating that there are no known special properties.
     *         It is acceptable for the properties of the iterator to change depending on its state.
     */

    public int getProperties() {
        return 0;
    }

}

