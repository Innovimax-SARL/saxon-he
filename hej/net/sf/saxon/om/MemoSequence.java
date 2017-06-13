////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.expr.LastPositionFinder;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.ArrayIterator;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.tree.iter.GroundedIterator;
import net.sf.saxon.tree.iter.SingletonIterator;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.SequenceExtent;

import java.util.Arrays;

/**
 * A Sequence implementation that represents a lazy evaluation of a supplied iterator. Items
 * are read from the base iterator only when they are first required, and are then remembered
 * within the local storage of the MemoSequence, which eventually (if the sequence is read
 * to the end) contains the entire value.
 */

public class MemoSequence implements Sequence {

    SequenceIterator inputIterator;
    //boolean used = false;

    /*@Nullable*/ private Item[] reservoir = null;
    private int used;
    protected int state;

    // State in which no items have yet been read
    private static final int UNREAD = 0;

    // State in which zero or more items are in the reservoir and it is not known
    // whether more items exist
    private static final int MAYBE_MORE = 1;

    // State in which all the items are in the reservoir
    private static final int ALL_READ = 3;

    // State in which we are getting the base iterator. If the closure is called in this state,
    // it indicates a recursive entry, which is only possible on an error path
    private static final int BUSY = 4;

    // State in which we know that the value is an empty sequence
    protected static final int EMPTY = 5;


    public MemoSequence(SequenceIterator iterator) {
        this.inputIterator = iterator;
    }

    public Item head() throws XPathException {
        return iterate().next();
    }


    public synchronized SequenceIterator iterate() throws XPathException {

        switch (state) {
            case UNREAD:
                state = BUSY;
                if (inputIterator instanceof EmptyIterator) {
                    state = EMPTY;
                    return inputIterator;
                }
                reservoir = new Item[50];
                used = 0;
                state = MAYBE_MORE;
                return new ProgressiveIterator();

            case MAYBE_MORE:
                return new ProgressiveIterator();

            case ALL_READ:
                switch (used) {
                    case 0:
                        state = EMPTY;
                        return EmptyIterator.emptyIterator();
                    case 1:
                        assert reservoir != null;
                        return SingletonIterator.makeIterator(reservoir[0]);
                    default:
                        return new ArrayIterator(reservoir, 0, used);
                }

            case BUSY:
                // recursive entry: can happen if there is a circularity involving variable and function definitions
                // Can also happen if variable evaluation is attempted in a debugger, hence the cautious message
                XPathException de = new XPathException("Attempt to access a variable while it is being evaluated");
                de.setErrorCode("XTDE0640");
                //de.setXPathContext(context);
                throw de;

            case EMPTY:
                return EmptyIterator.emptyIterator();

            default:
                throw new IllegalStateException("Unknown iterator state");

        }
    }

    /**
     * Get the Nth item in the sequence (0-based), reading new items into the internal reservoir if necessary
     * @param n the index of the required item
     * @return the Nth item if it exists, or null otherwise
     * @throws XPathException if the input sequence cannot be read
     */

    public synchronized Item itemAt(int n) throws XPathException {
        if (n < 0) {
            return null;
        }
        if (reservoir != null && n < used) {
            return reservoir[n];
        }
        if (state == ALL_READ || state == EMPTY) {
            return null;
        }
        if (state == UNREAD) {
            Item item = inputIterator.next();
            if (item == null) {
                state = EMPTY;
                return null;
            } else {
                state = MAYBE_MORE;
                reservoir = new Item[50];
                append(item);
                if (n == 0) {
                    return item;
                }
            }
        }
        // We have read some items from the input sequence but not enough. Read as many more as are needed.
        int diff = n - used + 1;
        while (diff-- > 0) {
            Item i = inputIterator.next();
            if (i == null) {
                state = ALL_READ;
                condense();
                return null;
            }
            append(i);
            state = MAYBE_MORE;
        }
        //noinspection ConstantConditions
        return reservoir[n];

    }


    /**
     * Append an item to the reservoir
     *
     * @param item the item to be added
     */

    private void append(Item item) {
        assert reservoir != null;
        if (used >= reservoir.length) {
            reservoir = Arrays.copyOf(reservoir, used * 2);
        }
        reservoir[used++] = item;
    }

    /**
     * Release unused space in the reservoir (provided the amount of unused space is worth reclaiming)
     */

    private void condense() {
        if (reservoir != null && reservoir.length - used > 30) {
            reservoir = Arrays.copyOf(reservoir, used);
        }
    }


    /**
     * A ProgressiveIterator starts by reading any items already held in the reservoir;
     * when the reservoir is exhausted, it reads further items from the inputIterator,
     * copying them into the reservoir as they are read.
     */

    public final class ProgressiveIterator implements SequenceIterator, LastPositionFinder, GroundedIterator {

        int position = -1;  // zero-based position in the reservoir of the
        // item most recently read

        /**
         * Create a ProgressiveIterator
         */

        public ProgressiveIterator() {
        }

        /**
         * Get the containing MemoSequence
         *
         */

        public MemoSequence getMemoSequence() {
            return MemoSequence.this;
        }

        /*@Nullable*/
        public Item next() throws XPathException {
            synchronized (MemoSequence.this) {
                // synchronized for the case where a multi-threaded xsl:for-each is reading the variable
                if (position == -2) {   // means we've already returned null once, keep doing so if called again.
                    return null;
                }
                if (++position < used) {
                    assert reservoir != null;
                    return reservoir[position];
                } else if (state == ALL_READ) {
                    // someone else has read the input to completion in the meantime
                    position = -2;
                    return null;
                } else {
                    assert inputIterator != null;
                    Item i = inputIterator.next();
                    if (i == null) {
                        state = ALL_READ;
                        condense();
                        position = -2;
                        return null;
                    }
                    position = used;
                    append(i);
                    state = MAYBE_MORE;
                    return i;
                }
            }
        }

        public void close() {
        }

        /**
         * Get the last position (that is, the number of items in the sequence)
         */

        public int getLength() throws XPathException {
            if (state == ALL_READ) {
                return used;
            } else if (state == EMPTY) {
                return 0;
            } else {
                // save the current position
                int savePos = position;
                // fill the reservoir
                while (next() != null) {}
                // reset the current position
                position = savePos;
                // return the total number of items
                return used;
            }
        }

        /**
         * Return a value containing all the items in the sequence returned by this
         * SequenceIterator
         *
         * @return the corresponding value
         */

        /*@Nullable*/
        public GroundedValue materialize() throws XPathException {
            if (state == ALL_READ) {
                return makeExtent();
            } else if (state == EMPTY) {
                return EmptySequence.getInstance();
            } else {
                // fill the reservoir
                while (next() != null) {}
                return makeExtent();
            }
        }

        private GroundedValue makeExtent() {
            if (used == reservoir.length) {
                return new SequenceExtent(reservoir);
            } else {
                return SequenceExtent.makeSequenceExtent(Arrays.asList(reservoir).subList(0, used));
            }
        }

        @Override
        public GroundedValue getResidue() throws XPathException {
            return new SequenceExtent(this);
        }

        /**
         * Get properties of this iterator, as a bit-significant integer.
         *
         * @return the properties of this iterator. This will be some combination of
         *         properties such as {@link #GROUNDED} and {@link #LAST_POSITION_FINDER}. It is always
         *         acceptable to return the value zero, indicating that there are no known special properties.
         */

        public int getProperties() {
            // bug 1740 shows that it is better to report the iterator as grounded even though this
            // may trigger eager evaluation of the underlying sequence.
            return GROUNDED | LAST_POSITION_FINDER;
        }
    }

}

