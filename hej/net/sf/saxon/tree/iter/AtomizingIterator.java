////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.iter;

import net.sf.saxon.om.AtomicSequence;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AtomicValue;

/**
 * AtomizingIterator returns the atomization of an underlying sequence supplied
 * as an iterator.  We use a specialist class rather than a general-purpose
 * MappingIterator for performance, especially as the relationship of items
 * in the result sequence to those in the base sequence is often one-to-one.
 * <p/>
 * This AtomizingIterator is capable of handling list-typed nodes whose atomized value
 * is a sequence of more than one item. When it is known that all input will be untyped,
 * an {@link UntypedAtomizingIterator} is used in preference.
 */

public class AtomizingIterator implements SequenceIterator {

    private SequenceIterator base;
    /*@Nullable*/ private AtomicSequence currentValue = null;
    private int currentValuePosition = 1;
    private int currentValueSize = 1;

    /**
     * Construct an AtomizingIterator that will atomize the values returned by the base iterator.
     *
     * @param base the base iterator
     */

    public AtomizingIterator(SequenceIterator base) {
        this.base = base;
    }

    /*@Nullable*/
    public AtomicValue next() throws XPathException {
        while (true) {
            if (currentValue != null) {
                if (currentValuePosition < currentValueSize) {
                    return currentValue.itemAt(currentValuePosition++);
                } else {
                    currentValue = null;
                }
            }
            Item nextSource = base.next();
            if (nextSource != null) {
                AtomicSequence v = nextSource.atomize();
                if (v instanceof AtomicValue) {
                    return (AtomicValue) v;
                } else {
                    currentValue = v;
                    currentValuePosition = 0;
                    currentValueSize = currentValue.getLength();
                    // now go round the loop to get the first item from the atomized value
                }
            } else {
                currentValue = null;
                return null;
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

