////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.tiny;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.CopyInformee;
import net.sf.saxon.event.CopyNamespaceSensitiveException;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.ReceiverOptions;
import net.sf.saxon.expr.parser.ExplicitLocation;
import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.NamespaceIterator;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.type.*;
import net.sf.saxon.value.AtomicValue;


/**
 * A node in the XML parse tree representing an XML element.<P>
 * This class is an implementation of NodeInfo. The object is a wrapper around
 * one entry in the arrays maintained by the TinyTree. Note that the same node
 * might be represented by different TinyElementImpl objects at different times.
 *
 * @author Michael H. Kay
 */

public final class TinyElementImpl extends TinyParentNodeImpl {

    /**
     * Constructor - create a tiny element node
     *
     * @param tree   the Tinytree containing the node
     * @param nodeNr the node number
     */

    public TinyElementImpl(TinyTree tree, int nodeNr) {
        this.tree = tree;
        this.nodeNr = nodeNr;
    }

    /**
     * Return the type of node.
     *
     * @return Type.ELEMENT
     */

    public final int getNodeKind() {
        return Type.ELEMENT;
    }

    /**
     * Get the base URI of this element node. This will be the same as the System ID unless
     * xml:base has been used.
     */

    public String getBaseURI() {
        return Navigator.getBaseURI(this);
    }

    /**
     * Get the type annotation of this node, if any. The type annotation is represented as
     * SchemaType object.
     * <p/>
     * <p>Types derived from a DTD are not reflected in the result of this method.</p>
     *
     * @return For element and attribute nodes: the type annotation derived from schema
     *         validation (defaulting to xs:untyped and xs:untypedAtomic in the absence of schema
     *         validation). For comments, text nodes, processing instructions, and namespaces: null.
     *         For document nodes, either xs:untyped if the document has not been validated, or
     *         xs:anyType if it has.
     * @since 9.4
     */
    @Override
    public SchemaType getSchemaType() {
        return tree.getSchemaType(nodeNr);
    }

    /**
     * Get the typed value.
     *
     * @return the typed value. It will be a Value representing a sequence whose items are atomic
     *         values.
     */

    public AtomicSequence atomize() throws XPathException {
        return tree.getTypedValueOfElement(this);
    }

    /**
     * Get all namespace undeclarations and undeclarations defined on this element.
     *
     * @param buffer If this is non-null, and the result array fits in this buffer, then the result
     *               may overwrite the contents of this array, to avoid the cost of allocating a new array on the heap.
     * @return An array of objects representing the namespace declarations and undeclarations present on
     *         this element. For a node other than an element, return null. Otherwise, the returned array is a
     *         sequence of namespace binding objects (essentially prefix/uri pairs)
     *         If the URI is null, then this is a namespace undeclaration rather than a declaration.
     *         The XML namespace is never included in the list. If the supplied array is larger than required,
     *         then the first unused entry will be set to null.
     */

    /*@Nullable*/
    public NamespaceBinding[] getDeclaredNamespaces(NamespaceBinding[] buffer) {
        return getDeclaredNamespaces(tree, nodeNr, buffer);
    }

    /**
     * Static method to get all namespace undeclarations and undeclarations defined on a given element,
     * without instantiating the node object.
     *
     * @param tree   The tree containing the given element node
     * @param nodeNr The node number of the given element node within the tinyTree
     * @param buffer If this is non-null, and the result array fits in this buffer, then the result
     *               may overwrite the contents of this array, to avoid the cost of allocating a new array on the heap.
     * @return An array of objects representing the namespace declarations and undeclarations present on
     *         this element. For a node other than an element, return null. Otherwise, the returned array is a
     *         sequence of namespace binding objects (essentially prefix/uri pairs)
     *         If the URI is null, then this is a namespace undeclaration rather than a declaration.
     *         The XML namespace is never included in the list. If the supplied array is larger than required,
     *         then the first unused entry will be set to null.
     */

    /*@Nullable*/
    public static NamespaceBinding[] getDeclaredNamespaces(/*@NotNull*/ TinyTree tree, int nodeNr, /*@Nullable*/ NamespaceBinding[] buffer) {
        int ns = tree.beta[nodeNr]; // by convention
        if (ns > 0) {
            int count = 0;
            while (ns < tree.numberOfNamespaces &&
                    tree.namespaceParent[ns] == nodeNr) {
                count++;
                ns++;
            }
            if (count == 0) {
                return NamespaceBinding.EMPTY_ARRAY;
            } else if (buffer != null && count <= buffer.length) {
                System.arraycopy(tree.namespaceBinding, tree.beta[nodeNr], buffer, 0, count);
                if (count < buffer.length) {
                    buffer[count] = null;
                }
                return buffer;
            } else {
                NamespaceBinding[] array = new NamespaceBinding[count];
                System.arraycopy(tree.namespaceBinding, tree.beta[nodeNr], array, 0, count);
                return array;
            }
        } else {
            return NamespaceBinding.EMPTY_ARRAY;
        }
    }


    /**
     * Get the string value of a given attribute of this node
     *
     * @param uri   the namespace URI of the attribute name. Supply the empty string for an attribute
     *              that is in no namespace
     * @param local the local part of the attribute name.
     * @return the attribute value if it exists, or null if it does not exist. Always returns null
     *         if this node is not an element.
     * @since 9.4
     */

    @Override
    public String getAttributeValue(/*@NotNull*/ String uri, /*@NotNull*/ String local) {
        int a = tree.alpha[nodeNr];
        if (a < 0) {
            return null;
        }
        NamePool pool = getNamePool();
        while (a < tree.numberOfAttributes && tree.attParent[a] == nodeNr) {
            int fp = tree.attCode[a] & NamePool.FP_MASK;
            // Avoid allocating a name code for an ad-hoc request
            if (pool.getLocalName(fp).equals(local) && pool.getURI(fp).equals(uri)) {
                return tree.attValue[a].toString();
            }
            a++;
        }
        return null;
    }

    /**
     * Get the value of the attribute with a given fingerprint.
     *
     * @param fp the fingerprint of the required attribute
     * @return the string value of the attribute if present, or null if absent
     */

    public String getAttributeValue(int fp) {
        // NB: Used from generated bytecode
        int a = tree.alpha[nodeNr];
        if (a < 0) {
            return null;
        }
        while (a < tree.numberOfAttributes && tree.attParent[a] == nodeNr) {
            if (fp == (tree.attCode[a] & NamePool.FP_MASK)) {
                return tree.attValue[a].toString();
            }
            a++;
        }
        return null;
    }

    /**
     * Copy this node to a given receiver
     *
     * @param copyOptions determines handling of namespaces, etc
     * @param location
     */

    public void copy(/*@NotNull*/ Receiver receiver, int copyOptions, Location location) throws XPathException {

        // control vars
        short level = -1;
        boolean closePending = false;
        short startLevel = tree.depth[nodeNr];
        boolean first = true;
        boolean disallowNamespaceSensitiveContent =
                ((copyOptions & CopyOptions.TYPE_ANNOTATIONS) != 0) &&
                        ((copyOptions & CopyOptions.SOME_NAMESPACES) == 0);

        Configuration config = tree.getConfiguration();
        NamePool pool = config.getNamePool();
        int next = nodeNr;
        CopyInformee<Location> informee = (CopyInformee<Location>) receiver.getPipelineConfiguration().getComponent(CopyInformee.class.getName());
        boolean typed = CopyOptions.includes(copyOptions, CopyOptions.TYPE_ANNOTATIONS);
        SchemaType elementType = Untyped.getInstance();
        SimpleType attributeType = BuiltInAtomicType.UNTYPED_ATOMIC;

        do {

            // determine node depth
            short nodeLevel = tree.depth[next];

            // extra close required?
            if (closePending) {
                level++;
            }

            // close former elements
            for (; level > nodeLevel; level--) {
                receiver.endElement();
            }

            // new node level
            level = nodeLevel;

            // output depends on node kind
            switch (tree.nodeKind[next]) {
                case Type.ELEMENT: {

                    // start element
                    if (typed) {
                        elementType = tree.getSchemaType(next);
                        if (disallowNamespaceSensitiveContent) {
                            try {
                                checkNotNamespaceSensitiveElement(elementType, next);
                            } catch (CopyNamespaceSensitiveException e) {
                                int lang = receiver.getPipelineConfiguration().getHostLanguage();
                                e.setErrorCode(lang == Configuration.XSLT ? "XTTE0950" : "XQTY0086");
                                throw e;
                            }
                        }
                    }
                    if (informee != null) {
                        Location loc = informee.notifyElementNode(tree.getNode(next));
                        if (loc != null) {
                            location = loc;
                        }
                    }
                    int nameCode = tree.nameCode[next];
                    int fp = nameCode & NamePool.FP_MASK;
                    String prefix = tree.getPrefix(next);
                    int options = !first && (copyOptions & CopyOptions.SOME_NAMESPACES) != 0
                            ? ReceiverOptions.NAMESPACE_OK
                            : 0;

                    if (location.getLineNumber() < tree.getLineNumber(next)) {
                        String systemId = location.getSystemId() == null ? getSystemId() : location.getSystemId();
                        location = new ExplicitLocation(systemId, tree.getLineNumber(next), getColumnNumber());
                    }
                    // bug 2209
                    receiver.startElement(new CodedName(fp, prefix, pool),
                            elementType, location, options);

                    // there is an element to close
                    closePending = true;

                    // output namespaces
                    if ((copyOptions & CopyOptions.SOME_NAMESPACES) != 0 && tree.usesNamespaces) {
                        if (first) {
                            if ((copyOptions & CopyOptions.LOCAL_NAMESPACES) != 0) {
                                NamespaceBinding[] localNamespaces = getDeclaredNamespaces(null);
                                for (NamespaceBinding ns : localNamespaces) {
                                    if (ns == null) {
                                        break;
                                    }
                                    receiver.namespace(ns, 0);
                                }
                            } else if ((copyOptions & CopyOptions.ALL_NAMESPACES) != 0) {
                                NamespaceIterator.sendNamespaces(this, receiver);
                            }
                        } else {
                            int ns = tree.beta[next]; // by convention
                            if (ns > 0) {
                                while (ns < tree.numberOfNamespaces &&
                                        tree.namespaceParent[ns] == next) {
                                    NamespaceBinding nscode = tree.namespaceBinding[ns];
                                    receiver.namespace(nscode, 0);
                                    ns++;
                                }
                            }
                        }
                    }
                    first = false;

                    // output attributes

                    int att = tree.alpha[next];
                    if (att >= 0) {
                        while (att < tree.numberOfAttributes && tree.attParent[att] == next) {
                            int attCode = tree.attCode[att];
                            int attfp = attCode & NamePool.FP_MASK;
                            if (typed) {
                                attributeType = tree.getAttributeType(att);
                                if (disallowNamespaceSensitiveContent) {
                                    try {
                                        checkNotNamespaceSensitiveAttribute(attributeType, att);
                                    } catch (CopyNamespaceSensitiveException e) {
                                        int lang = receiver.getPipelineConfiguration().getHostLanguage();
                                        e.setErrorCode(lang == Configuration.XSLT ? "XTTE0950" : "XQTY0086");
                                        throw e;
                                    }
                                }
                            }
                            String attPrefix = tree.prefixPool.getPrefix(attCode >> 20);
                            receiver.attribute(new CodedName(attfp, attPrefix, pool), attributeType, tree.attValue[att], location, 0);
                            att++;
                        }
                    }

                    // start content
                    receiver.startContent();
                    break;
                }
                case Type.TEXT: {

                    // don't close text nodes
                    closePending = false;

                    // output characters
                    final CharSequence value = TinyTextImpl.getStringValue(tree, next);
                    receiver.characters(value, location, ReceiverOptions.WHOLE_TEXT_NODE);
                    break;
                }

                case Type.WHITESPACE_TEXT: {

                    // don't close text nodes
                    closePending = false;

                    // output characters
                    final CharSequence value = WhitespaceTextImpl.getStringValueCS(tree, next);
                    receiver.characters(value, location, ReceiverOptions.WHOLE_TEXT_NODE);
                    break;
                }

                case Type.COMMENT: {

                    // don't close text nodes
                    closePending = false;

                    // output copy of comment
                    int start = tree.alpha[next];
                    int len = tree.beta[next];
                    if (len > 0) {
                        receiver.comment(tree.commentBuffer.subSequence(start, start + len), location, 0);
                    } else {
                        receiver.comment("", ExplicitLocation.UNKNOWN_LOCATION, 0);
                    }
                    break;
                }
                case Type.PROCESSING_INSTRUCTION: {

                    // don't close text nodes
                    closePending = false;

                    // output copy of PI
                    NodeInfo pi = tree.getNode(next);
                    receiver.processingInstruction(pi.getLocalPart(), pi.getStringValue(), location, 0);
                    break;
                }

                case Type.PARENT_POINTER: {
                    closePending = false;
                }
            }

            next++;

        } while (next < tree.numberOfNodes && tree.depth[next] > startLevel);

        // close all remaining elements
        if (closePending) {
            level++;
        }
        for (; level > startLevel; level--) {
            receiver.endElement();
        }
    }

    /**
     * Check whether the content of an element is namespace-sensitive
     *
     *
     * @param type   the type annotation of the node
     * @param nodeNr the the node number of the elemente
     * @throws XPathException if an error occurs
     */

    private void checkNotNamespaceSensitiveElement(SchemaType type, int nodeNr) throws XPathException {
        if (type instanceof SimpleType && ((SimpleType) type).isNamespaceSensitive()) {
            if (type.isAtomicType()) {
                throw new CopyNamespaceSensitiveException(
                        "Cannot copy QName or NOTATION values without copying namespaces");
            } else {
                // For a union or list type, we need to check whether the actual value is namespace-sensitive
                AtomicSequence value = tree.getTypedValueOfElement(nodeNr);
                for (AtomicValue val : value) {
                    if (val.getPrimitiveType().isNamespaceSensitive()) {
                        throw new CopyNamespaceSensitiveException(
                                "Cannot copy QName or NOTATION values without copying namespaces");
                    }
                }
            }
        }
    }

    /**
     * Check whether the content of an attribute is namespace-sensitive
     *
     *
     * @param type   the type annotation of the node
     * @param nodeNr the the node number of the elemente
     * @throws XPathException  if an error occurs
     */

    private void checkNotNamespaceSensitiveAttribute(SimpleType type, int nodeNr) throws XPathException {
        if (type.isNamespaceSensitive()) {
            if (type.isAtomicType()) {
                throw new CopyNamespaceSensitiveException(
                        "Cannot copy QName or NOTATION values without copying namespaces");
            } else {
                // For a union or list type, we need to check whether the actual value is namespace-sensitive
                AtomicSequence value = tree.getTypedValueOfAttribute(null, nodeNr);
                for (AtomicValue val : value) {
                    if (val.getPrimitiveType().isNamespaceSensitive()) {
                        throw new CopyNamespaceSensitiveException(
                                "Cannot copy QName or NOTATION values without copying namespaces");
                    }
                }
            }
        }
    }


    /**
     * Get the namespace URI corresponding to a given prefix. Return null
     * if the prefix is not in scope.
     *
     * @param prefix     the namespace prefix. May be the zero-length string, indicating
     *                   that there is no prefix. This indicates either the default namespace or the
     *                   null namespace, depending on the value of useDefault.
     * @param useDefault true if the default namespace is to be used when the
     *                   prefix is "". If false, the method returns "" when the prefix is "".
     * @return the uri for the namespace, or null if the prefix is not in scope.
     *         The "null namespace" is represented by the pseudo-URI "".
     */

    /*@Nullable*/
    public String getURIForPrefix(/*@Nullable*/ String prefix, boolean useDefault) {
        if (!useDefault && (prefix == null || prefix.isEmpty())) {
            return "";
        }
        int ns = tree.beta[nodeNr]; // by convention
        if (ns > 0) {
            while (ns < tree.numberOfNamespaces &&
                    tree.namespaceParent[ns] == nodeNr) {
                NamespaceBinding nscode = tree.namespaceBinding[ns];
                if (nscode.getPrefix().equals(prefix)) {
                    String uri = nscode.getURI();
                    if (uri.isEmpty()) {
                        // this is a namespace undeclaration, so the prefix is not in scope
                        if (prefix.isEmpty()) {
                            // the namespace xmlns="" is always in scope
                            return "";
                        } else {
                            return null;
                        }
                    } else {
                        return uri;
                    }
                }
                ns++;
            }
        }

        // now search the namespaces defined on the ancestor nodes.

        NodeInfo parent = getParent();
        if (parent instanceof NamespaceResolver) {
            return ((NamespaceResolver) parent).getURIForPrefix(prefix, useDefault);
        }
        return null;
    }

    /**
     * Determine whether this node has the is-id property
     *
     * @return true if the node is an ID
     */

    public boolean isId() {
        // this looks very inefficient, but the method isn't actually used...
        return tree.isIdElement(nodeNr);
    }

    /**
     * Determine whether this node has the is-idref property
     *
     * @return true if the node is an IDREF or IDREFS element or attribute
     */

    public boolean isIdref() {
        return tree.isIdrefElement(nodeNr);
    }


}

