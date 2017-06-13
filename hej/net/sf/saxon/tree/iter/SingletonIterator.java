////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.iter;

import net.sf.saxon.expr.LastPositionFinder;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.ZeroOrOne;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.EmptySequence;


/**
 * SingletonIterator: an iterator over a sequence of zero or one values
 */

public class SingletonIterator implements SequenceIterator, UnfailingIterator,
        ReversibleIterator, LastPositionFinder, GroundedIterator, LookaheadIterator {

    private Item item;
    boolean gone = false;

    /**
     * Private constructor: external classes should use the factory method
     *
     * @param value the item to iterate over
     */

    private SingletonIterator(Item value) {
        this.item = value;
    }

    /**
     * Factory method.
     *
     * @param item the item to iterate over
     * @return a SingletonIterator over the supplied item, or an EmptyIterator
     *         if the supplied item is null.
     */

    /*@NotNull*/
    public static UnfailingIterator makeIterator(Item item) {
        if (item == null) {
            return EmptyIterator.emptyIterator();
        } else {
            return new SingletonIterator(item);
        }
    }

    /**
     * Determine whether there are more items to come. Note that this operation
     * is stateless and it is not necessary (or usual) to call it before calling
     * next(). It is used only when there is an explicit need to tell if we
     * are at the last element.
     *
     * @return true if there are more items
     */

    public boolean hasNext() {
        return !gone;
    }

    /*@Nullable*/
    public Item next() {
        if (gone) {
            return null;
        } else {
            gone = true;
            return item;
        }
    }

    public int getLength() {
        return 1;
    }

    public void close() {
    }

    /*@NotNull*/
    public SingletonIterator getReverseIterator() {
        return new SingletonIterator(item);
    }

    public Item getValue() {
        return item;
    }

    /**
     * Return a Value containing all the items in the sequence returned by this
     * SequenceIterator
     *
     * @return the corresponding Value. If the value is a closure or a function call package, it will be
     *         evaluated and expanded.
     */

    /*@NotNull*/
    public GroundedValue materialize() {
        if (item instanceof GroundedValue) {
            return (GroundedValue) item;
        } else {
            return new ZeroOrOne<Item>(item);
        }
    }

    @Override
    public GroundedValue getResidue() throws XPathException {
        return gone ? EmptySequence.getInstance() : materialize();
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
        return GROUNDED | LAST_POSITION_FINDER | LOOKAHEAD;
    }

}

