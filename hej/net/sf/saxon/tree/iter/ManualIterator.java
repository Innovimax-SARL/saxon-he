////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.iter;

import net.sf.saxon.expr.LastPositionFinder;
import net.sf.saxon.om.FocusIterator;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.One;
import net.sf.saxon.trans.XPathException;


/**
 * ManualIterator: a pseudo-iterator used while streaming. It has a current node and a current position
 * which are set manually. Calling last() is an error. Calling next() always returns null.
 */

public class ManualIterator implements FocusIterator, UnfailingIterator,
        ReversibleIterator, LastPositionFinder, GroundedIterator, LookaheadIterator {

    private Item item;
    private int position;
    private LastPositionFinder lastPositionFinder;

    /**
     * Create an uninitialized ManualIterator: this is only usable after the context item, position, and size (if required)
     * have been initialized using setter methods.
     */

    public ManualIterator() {
        item = null;
        position = 0;
    }

    /**
     * Create a ManualIterator initializing the context item and position.
     * The value of "last()" for such an iterator is unknown unless a LastPositionFinder is supplied.
     * @param value the context item. May be null if the value is to be initialized later.
     * @param position the context position
     */

    public ManualIterator(Item value, int position) {
        this.item = value;
        this.position = position;
    }

    /**
     * Create a ManualIterator supplying the context item, and setting the value of
     * both "position()" and "last()" implicitly to 1.
     * @param value the context item
     */

    public ManualIterator(Item value) {
        this.item = value;
        this.position = 1;
        this.lastPositionFinder = new LastPositionFinder() {
            public int getLength() {
                return 1;
            }
        };
    }

    public void setContextItem(Item value) {
        this.item = value;
    }

    public void setLastPositionFinder(LastPositionFinder finder) {
        this.lastPositionFinder = finder;
    }

    public void incrementPosition() {
        position++;
    }

    public void setPosition(int position) {
        this.position = position;
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
        try {
            return position() != getLength();
        } catch (XPathException e) {
            return false; // should not happen
        }
    }

    public Item next() {
        return null;
    }

    public Item current() {
        return item;
    }

    /**
     * Return the current position in the sequence.
     *
     * @return 0 before the first call on next(); 1 before the second call on next(); -1 after the second
     *         call on next().
     */
    public int position() {
        return position;
    }

    public int getLength() throws XPathException {
        if (lastPositionFinder == null) {
            throw new UnsupportedOperationException("last() cannot be used when streaming");
        } else {
            return lastPositionFinder.getLength();
        }
    }

    public void close() {
    }

    public ManualIterator getReverseIterator() {
        return new ManualIterator(item);
    }

    /**
     * Return a Value containing all the items in the sequence returned by this
     * SequenceIterator
     *
     * @return the corresponding Value. If the value is a closure or a function call package, it will be
     *         evaluated and expanded.
     */

    /*@Nullable*/
    public GroundedValue materialize() {
        if (item instanceof GroundedValue) {
            return (GroundedValue) item;
        } else {
            return new One<Item>(item);
        }
    }

    @Override
    public GroundedValue getResidue() throws XPathException {
        return materialize();
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

// Copyright (c) 2009 Saxonica Limited.