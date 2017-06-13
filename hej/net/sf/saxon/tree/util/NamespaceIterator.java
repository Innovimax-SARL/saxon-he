////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.util;

import net.sf.saxon.event.Receiver;
import net.sf.saxon.om.InScopeNamespaces;
import net.sf.saxon.om.NamespaceBinding;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.TinyNodeImpl;
import net.sf.saxon.type.Type;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

/**
 * This class provides an iterator over the namespace codes representing the in-scope namespaces
 * of any node. It relies on nodes to implement the method
 * {@link net.sf.saxon.om.NodeInfo#getDeclaredNamespaces(net.sf.saxon.om.NamespaceBinding[])}.
 * <p/>
 * <p>The result does not include the XML namespace.</p>
 */
public class NamespaceIterator implements Iterator<NamespaceBinding> {

    /*@Nullable*/ private NodeInfo element;
    private int index;
    /*@Nullable*/ private NamespaceBinding next;
    private NamespaceBinding[] localDeclarations;
    HashSet<String> undeclaredPrefixes;

    /**
     * Factory method: create an iterator over the in-scope namespace codes for an element
     *
     * @param element the element (or other node) whose in-scope namespaces are required. If this
     *                is not an element, the result will be an empty iterator
     * @return an iterator over the namespace codes. A namespace code is an integer that represents
     *         a prefix-uri binding; the prefix and URI can be obtained by reference to the name pool. This
     *         iterator will represent all the in-scope namespaces, without duplicates, and respecting namespace
     *         undeclarations. It does not include the XML namespace.
     */

    public static Iterator<NamespaceBinding> iterateNamespaces(/*@NotNull*/ NodeInfo element) {
        if (element.getNodeKind() == Type.ELEMENT) {
            return new NamespaceIterator(element);
        } else {
            List<NamespaceBinding> e = Collections.emptyList();
            return e.iterator();
        }
    }

    /**
     * Send all the in-scope namespaces for a node (except the XML namespace) to a specified receiver
     *
     * @param element  the element in question (the method does nothing if this is not an element)
     * @param receiver the receiver to which the namespaces are notified
     * @throws XPathException if a failure is reported by the downstream Receiver
     */

    public static void sendNamespaces(/*@NotNull*/ NodeInfo element, /*@NotNull*/ Receiver receiver) throws XPathException {
        if (element instanceof TinyNodeImpl && !((TinyNodeImpl)element).getTree().isUsesNamespaces()) {
            return;
        }
        while (element != null && element.getNodeKind() == Type.ELEMENT) {
            // find the first ancestor-or-self element that has namespace declarations, and send all its in-scope
            // namespaces to the receiver. The significance of this is that with shallow copy, the namespace set
            // will usually be the same as the parent element's namespace set, and will then be quickly discarded
            // by the ComplexContentOutputter.
            NamespaceBinding[] localNamespaces = element.getDeclaredNamespaces(null);
            if (localNamespaces.length > 0) {
                receiver.namespace(new InScopeNamespaces(element), 0);
                return;
            }
            element = element.getParent();
        }
//            for (Iterator<NamespaceBinding> iter = iterateNamespaces(element); iter.hasNext(); ) {
//                NamespaceBinding nb = iter.next();
//                receiver.namespace(nb, 0);
//            }
    }

    private NamespaceIterator(/*@NotNull*/ NodeInfo element) {
        this.element = element;
        undeclaredPrefixes = new HashSet<String>(8);
        index = 0;
        localDeclarations = element.getDeclaredNamespaces(null);
    }

    public boolean hasNext() {
        if (element == null || (next == null && index != 0)) {
            return false;
        }
        advance();
        return next != null;
    }

    /*@Nullable*/
    public NamespaceBinding next() {
        return next;
    }

    private void advance() {
        while (true) {
            boolean ascend = index >= localDeclarations.length;
            NamespaceBinding nsCode = null;
            if (!ascend) {
                nsCode = localDeclarations[index++];
                ascend = nsCode == null;
            }
            if (ascend) {
                element = element.getParent();
                if (element != null && element.getNodeKind() == Type.ELEMENT) {
                    localDeclarations = element.getDeclaredNamespaces(localDeclarations);
                    index = 0;
                    continue;
                } else {
                    next = null;
                    return;
                }
            }
            String uri = nsCode.getURI();
            String prefix = nsCode.getPrefix();
            if (uri.isEmpty()) {
                // this is an undeclaration
                undeclaredPrefixes.add(prefix);
            } else {
                if (undeclaredPrefixes.add(prefix)) {
                    // it was added, so it's new, so return it
                    next = nsCode;
                    return;
                }
                // else it wasn't added, so we've already seen it
            }
        }
    }

    public void remove() {
        throw new UnsupportedOperationException();
    }
}

