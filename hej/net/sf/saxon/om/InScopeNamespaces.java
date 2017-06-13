////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.tree.util.NamespaceIterator;

import java.util.Iterator;

/**
 * Represents the set of all namespace bindings that are in scope for a particular element node
 */
public class InScopeNamespaces implements NamespaceBindingSet {

    private NodeInfo element;

    public InScopeNamespaces(NodeInfo element) {
        this.element = element;
    }

    public NodeInfo getElement() {
        return element;
    }

    /**
     * Returns an iterator over the in-scope namespace bindings of the element.
     *
     * @return an Iterator.
     */
    @Override
    public Iterator<NamespaceBinding> iterator() {
        return NamespaceIterator.iterateNamespaces(element);
    }
}

