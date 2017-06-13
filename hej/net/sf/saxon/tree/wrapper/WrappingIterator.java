////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.wrapper;

import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.tree.iter.AxisIterator;

/**
 * A WrappingIterator delivers wrappers for the nodes delivered
 * by its underlying iterator. It is used when no whitespace stripping
 * is actually needed, e.g. for the attribute axis. But we still need to
 * create wrappers, so that further iteration remains in the virtual layer
 * rather than switching to the real nodes.
 */

public class WrappingIterator implements AxisIterator {

    AxisIterator base;
    VirtualNode parent;
    /*@Nullable*/ NodeInfo current;
    boolean atomizing = false;
    WrappingFunction wrappingFunction;

    /**
     * Create a WrappingIterator
     *
     * @param base   The underlying iterator
     * @param parent If all the nodes to be wrapped have the same parent,
     *               it can be specified here. Otherwise specify null.
     */

    public WrappingIterator(AxisIterator base, WrappingFunction function, VirtualNode parent) {
        this.base = base;
        this.wrappingFunction = function;
        this.parent = parent;
    }


    /*@Nullable*/
    public NodeInfo next() {
        Item n = base.next();
        if (n instanceof NodeInfo && !atomizing) {
            current = wrappingFunction.makeWrapper((NodeInfo) n, parent);
        } else {
            current = (NodeInfo) n;
        }
        return current;
    }

    /*@Nullable*/
    public NodeInfo current() {
        return current;
    }

    public void close() {
        base.close();
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

