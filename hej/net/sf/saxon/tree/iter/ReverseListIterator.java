////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.iter;

import net.sf.saxon.expr.LastPositionFinder;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;

import java.util.List;


/**
 * ReverseListIterator is used to enumerate items held in an array in reverse order.
 *
 * @author Michael H. Kay
 */


public class ReverseListIterator implements UnfailingIterator,
        ReversibleIterator, LookaheadIterator, LastPositionFinder {

    List<? extends Item> items;
    int index = 0;
    int start;
    int end;         // item after the last to be output

    /**
     * Create an iterator a slice of an array
     *
     * @param items The list of items
     */

    public ReverseListIterator(List<? extends Item> items) {
        this.items = items;
        index = items.size() - 1;

    }

    /**
     * Determine whether there are more items to come. Note that this operation
     * is stateless and it is not necessary (or usual) to call it before calling
     * next(). It is used only when there is an explicit need to tell if we
     * are at the last element.
     *
     * @return true if there are more items in the sequence
     */

    public boolean hasNext() {
        return index >= 0;
    }

    /*@Nullable*/
    public Item next() {
        if (index >= 0) {
            return items.get(index--);
        } else {
            return null;
        }
    }

    public int getLength() {
        return items.size();
    }

    public void close() {
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
        return LAST_POSITION_FINDER;
    }

    /**
     * Get an iterator that processes the same items in reverse order.
     * Since this iterator is processing the items backwards, this method
     * returns an ArrayIterator that processes them forwards.
     *
     * @return a new ArrayIterator
     */

    public SequenceIterator getReverseIterator() {
        return new ListIterator(items);
    }
}

