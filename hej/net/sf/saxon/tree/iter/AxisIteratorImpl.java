////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.iter;

/**
 * A SequenceIterator is used to iterate over a sequence. An AxisIterator
 * is a SequenceIterator that always iterates over a set of nodes, and that
 * throws no exceptions; it also supports the ability
 * to find the last() position, again with no exceptions.
 * This class is an abstract implementation of AxisIterator that is used
 * as a base class for many concrete implementations. The main functionality
 * that it provides is maintaining the current position.
 */

public abstract class AxisIteratorImpl implements AxisIterator {

    //protected int position = 0;
    //protected NodeInfo current;

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
        return 0;
    }

}

