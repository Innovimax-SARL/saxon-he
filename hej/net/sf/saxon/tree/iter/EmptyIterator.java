////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.iter;

import net.sf.saxon.expr.LastPositionFinder;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.EmptySequence;

/**
 * EmptyIterator: an iterator over an empty sequence. Since such an iterator has no state,
 * only one instance is required; therefore a singleton instance is available via the static
 * getInstance() method.
 */

public class EmptyIterator implements SequenceIterator,
        ReversibleIterator, LastPositionFinder, GroundedIterator,
        LookaheadIterator, UnfailingIterator, AtomizedValueIterator {

    private static EmptyIterator theInstance = new EmptyIterator();

    /**
     * Get an EmptyIterator, an iterator over an empty sequence.
     *
     * @return an EmptyIterator (in practice, this always returns the same
     *         one)
     */
    /*@NotNull*/
    public static EmptyIterator getInstance() {
        return theInstance;
    }


    public static EmptyIterator emptyIterator() {
        return theInstance;
    }

    /**
     * Protected constructor
     */

    protected EmptyIterator() {
    }

    /**
     * Deliver the atomic value that is next in the atomized result
     *
     * @return the next atomic value
     * @throws net.sf.saxon.trans.XPathException
     *          if a failure occurs reading or atomizing the next value
     */
    public AtomicSequence nextAtomizedValue() throws XPathException {
        return null;
    }

    /**
     * Get the next item.
     *
     * @return the next item. For the EmptyIterator this is always null.
     */
    public Item next() {
        return null;
    }

    /**
     * Get the position of the last item in the sequence.
     *
     * @return the position of the last item in the sequence, always zero in
     *         this implementation
     */
    public int getLength() {
        return 0;
    }

    public void close() {
    }

    /**
     * Get another iterator over the same items, in reverse order.
     *
     * @return a reverse iterator over an empty sequence (in practice, it
     *         returns the same iterator each time)
     */
    /*@NotNull*/
    public EmptyIterator getReverseIterator() {
        return this;
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
        return GROUNDED | LAST_POSITION_FINDER | LOOKAHEAD | ATOMIZING;
    }

    /**
     * Return a Value containing all the items in the sequence returned by this
     * SequenceIterator. This should be an "in-memory" value, not a Closure.
     *
     * @return the corresponding Value
     */

    public GroundedValue materialize() {
        return EmptySequence.getInstance();
    }

    @Override
    public GroundedValue getResidue() throws XPathException {
        return EmptySequence.getInstance();
    }

    /**
     * Determine whether there are more items to come. Note that this operation
     * is stateless and it is not necessary (or usual) to call it before calling
     * next(). It is used only when there is an explicit need to tell if we
     * are at the last element.
     *
     * @return true if there are more nodes
     */

    public boolean hasNext() {
        return false;
    }

    /**
     * An empty iterator for use where a sequence of nodes is required
     */

    public static class OfNodes extends EmptyIterator implements AxisIterator {

        public final static OfNodes THE_INSTANCE = new OfNodes();
        /**
         * Get the next item.
         *
         * @return the next item. For the EmptyIterator this is always null.
         */
        public NodeInfo next() {
            return null;
        }

    }

    /**
     * An empty iterator for use where a sequence of atomic values is required
     */

    public static class OfAtomic extends EmptyIterator implements AtomicIterator {

        public final static OfAtomic THE_INSTANCE = new OfAtomic();

        /**
         * Get the next item.
         *
         * @return the next item. For the EmptyIterator this is always null.
         */
        public AtomicValue next() {
            return null;
        }

    }


}

