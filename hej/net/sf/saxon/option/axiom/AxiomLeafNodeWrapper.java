////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.axiom;

import net.sf.saxon.event.Receiver;
import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.om.NamespaceBinding;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.pattern.AnyNodeTest;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.NamespaceNode;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.tree.wrapper.AbstractNodeWrapper;
import net.sf.saxon.tree.wrapper.SiblingCountingNode;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Type;
import org.apache.axiom.om.*;

import java.util.Iterator;

/**
 * A node in the XDM tree. This is the implementation of the NodeInfo interface used as a wrapper for
 * Axiom comment, text, and processing instruction nodes.</p>
 *
 * @author Michael H. Kay
 */

public class AxiomLeafNodeWrapper extends AbstractNodeWrapper implements SiblingCountingNode {

    protected OMNode node;

    protected short nodeKind;

    private AxiomParentNodeWrapper parent;  // null means unknown
    private AxiomDocument docWrapper;  // null means unknown

    protected int index; // position among siblings; -1 means unknown

    /**
     * This constructor is protected: nodes should be created using the wrap
     * factory method on the DocumentWrapper class
     *
     * @param node       The Axiom node to be wrapped
     * @param docWrapper The wrapper of the containing document node - must not be null
     * @param parent     The NodeWrapper that wraps the parent of this node; null means unknwon
     * @param index      Position of this node among its siblings
     */
    protected AxiomLeafNodeWrapper(OMNode node, AxiomDocument docWrapper, AxiomParentNodeWrapper parent, int index) {
        if (docWrapper == null) {
            throw new NullPointerException();
        }
        int kind = node.getType();
        switch (kind) {
            case OMNode.TEXT_NODE:
                nodeKind = Type.TEXT;
                break;
            case OMNode.COMMENT_NODE:
                nodeKind = Type.COMMENT;
                break;
            case OMNode.PI_NODE:
                nodeKind = Type.PROCESSING_INSTRUCTION;
                break;
            default:
                throwIllegalNode(node);
                return;// keep compiler happy
        }
        this.node = node;
        this.parent = parent;
        this.docWrapper = docWrapper;
        this.index = index;
        this.treeInfo = docWrapper;
    }


    private static void throwIllegalNode(/*@Nullable*/ OMNode node) {
        String str = node == null ?
                "NULL" :
                node.getClass() + " instance " + node.toString();
        throw new IllegalArgumentException("Bad node type in XOM! " + str);
    }

    /**
     * Get the underlying XOM node, to implement the VirtualNode interface
     */

    public Object getUnderlyingNode() {
        return node;
    }

    /**
     * Return the type of node.
     *
     * @return one of the values Node.ELEMENT, Node.TEXT, Node.ATTRIBUTE, etc.
     */

    public int getNodeKind() {
        return nodeKind;
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

    public SchemaType getSchemaType() {
        return null;
    }

    /**
     * Determine the relative position of this node and another node, in
     * document order. The other node will always be in the same document.
     *
     * @param other The other node, whose position is to be compared with this
     *              node
     * @return -1 if this node precedes the other node, +1 if it follows the
     *         other node, or 0 if they are the same node. (In this case,
     *         isSameNode() will always return true, and the two nodes will
     *         produce the same result for generateId())
     */

    public int compareOrder(NodeInfo other) {
        if (other instanceof AxiomDocument) {
            return +1;
        } else if (other instanceof AxiomAttributeWrapper) {
            if (other.getParent() == this.getParent()) {
                return +1;
            } else {
                return getParent().compareOrder(other.getParent());
            }
        } else if (other instanceof NamespaceNode) {
            return -other.compareOrder(this);
        } else {
            return Navigator.compareOrder(this, (SiblingCountingNode) other);
        }
    }

    /**
     * Get the value of the item as a CharSequence. This is in some cases more efficient than
     * the version of the method that returns a String.
     */

    public CharSequence getStringValueCS() {
        switch (nodeKind) {
            case Type.TEXT:
                return ((OMText) node).getText();
            case Type.COMMENT:
                return ((OMComment) node).getValue();
            case Type.PROCESSING_INSTRUCTION:
                return ((OMProcessingInstruction) node).getValue();
            default:
                throw new AssertionError();
        }
    }

    /**
     * Get the local part of the name of this node. This is the name after the
     * ":" if any.
     *
     * @return the local part of the name. For an unnamed node, returns "".
     */

    public String getLocalPart() {
        switch (nodeKind) {
            case Type.PROCESSING_INSTRUCTION:
                return ((OMProcessingInstruction) node).getTarget();
            default:
                return "";
        }
    }

    /**
     * Get the prefix of the name of the node. This is defined only for elements and attributes.
     * If the node has no prefix, or for other kinds of node, return a zero-length string.
     *
     * @return The prefix of the name of the node.
     */

    public String getPrefix() {
        return "";
    }

    /**
     * Get the URI part of the name of this node. This is the URI corresponding
     * to the prefix, or the URI of the default namespace if appropriate.
     *
     * @return The URI of the namespace of this node. For an unnamed node, or
     *         for a node with an empty prefix, return an empty string.
     */

    public String getURI() {
        return "";
    }

    /**
     * Get the display name of this node. For elements and attributes this is
     * [prefix:]localname. For unnamed nodes, it is an empty string.
     *
     * @return The display name of this node. For a node with no name, return an
     *         empty string.
     */

    public String getDisplayName() {
        switch (nodeKind) {
            case Type.PROCESSING_INSTRUCTION:
                return ((OMProcessingInstruction) node).getTarget();
            default:
                return "";
        }
    }

    /**
     * Get the NodeInfo object representing the parent of this node
     */

    public AxiomParentNodeWrapper getParent() {
        if (parent == null) {
            OMContainer rawParent = node.getParent();
            if (rawParent instanceof OMDocument) {
                parent = (AxiomDocumentNodeWrapper)docWrapper.getRootNode();
            } else {
                parent = (AxiomElementNodeWrapper) AxiomDocument.makeWrapper(((OMElement) rawParent), docWrapper, null, -1);
            }
        }
        return parent;
    }

    /**
     * Get the index position of this node among its siblings (starting from 0)
     */

    public int getSiblingPosition() {
        if (index != -1) {
            return index;
        }

        OMContainer p = node.getParent();
        int ix = 0;
        for (Iterator kids = p.getChildren(); kids.hasNext(); ) {
            if (kids.next() == node) {
                return (index = ix);
            }
            ix++;
        }
        throw new IllegalStateException("Bad child/parent relationship in Axiom tree");
    }

    @Override
    protected AxisIterator iterateAttributes(NodeTest nodeTest) {
        return EmptyIterator.OfNodes.THE_INSTANCE;
    }

    @Override
    protected AxisIterator iterateChildren(NodeTest nodeTest) {
        return EmptyIterator.OfNodes.THE_INSTANCE;
    }

    @Override
    protected AxisIterator iterateSiblings(NodeTest nodeTest, boolean forwards) {
        if (forwards) {
            if (nodeTest instanceof AnyNodeTest) {
                return new AxiomDocument.FollowingSiblingIterator(node, parent, docWrapper);
            } else {
                return new Navigator.AxisFilter(
                        new AxiomDocument.FollowingSiblingIterator(node, parent, docWrapper), nodeTest);
            }
        } else {
            if (nodeTest instanceof AnyNodeTest) {
                return new AxiomDocument.PrecedingSiblingIterator(node, parent, docWrapper);
            } else {
                return new Navigator.AxisFilter(
                        new AxiomDocument.PrecedingSiblingIterator(node, parent, docWrapper), nodeTest);
            }
        }
    }

    @Override
    protected AxisIterator iterateDescendants(NodeTest nodeTest, boolean includeSelf) {
        throw new UnsupportedOperationException(); // shouldn't be called on this kind of node
    }

    /**
     * Get the root node of the tree containing this node
     *
     * @return the NodeInfo representing the top-level ancestor of this node.
     *         This will not necessarily be a document node
     */

    public NodeInfo getRoot() {
        return getParent().getRoot();
    }

    /**
     * Determine whether the node has any children. <br />
     * Note: the result is equivalent to <br />
     * getEnumeration(Axis.CHILD, AnyNodeTest.getInstance()).hasNext()
     */

    public boolean hasChildNodes() {
        return false;
    }

    /**
     * Get a character string that uniquely identifies this node. Note:
     * a.isSameNode(b) if and only if generateId(a)==generateId(b)
     *
     * @param buffer a buffer to contain a string that uniquely identifies this node, across all documents
     */

    public void generateId(FastStringBuffer buffer) {
        Navigator.appendSequentialKey(this, buffer, true);
        //buffer.append(Navigator.getSequentialKey(this));
    }

    /**
     * Copy this node to a given outputter (deep copy)
     */

    public void copy(Receiver out, int copyOptions,
                     Location locationId) throws XPathException {
        Navigator.copy(this, out, copyOptions, locationId);
    }

    /**
     * Get all namespace undeclarations and undeclarations defined on this element.
     *
     * @param buffer If this is non-null, and the result array fits in this buffer, then the result
     *               may overwrite the contents of this array, to avoid the cost of allocating a new array on the heap.
     * @return An array of integers representing the namespace declarations and undeclarations present on
     *         this element. For a node other than an element, return null. Otherwise, the returned array is a
     *         sequence of namespace codes, whose meaning may be interpreted by reference to the name pool. The
     *         top half word of each namespace code represents the prefix, the bottom half represents the URI.
     *         If the bottom half is zero, then this is a namespace undeclaration rather than a declaration.
     *         The XML namespace is never included in the list. If the supplied array is larger than required,
     *         then the first unused entry will be set to -1.
     *         <p/>
     *         <p>For a node other than an element, the method returns null.</p>
     */

    public NamespaceBinding[] getDeclaredNamespaces(NamespaceBinding[] buffer) {
        return null;
    }

    /**
     * Determine whether this node has the is-id property
     *
     * @return true if the node is an ID
     */

    public boolean isId() {
        return false;
    }

    /**
     * Determine whether this node has the is-idref property
     *
     * @return true if the node is an IDREF or IDREFS element or attribute
     */

    public boolean isIdref() {
        return false;
    }

    /**
     * Determine whether the node has the is-nilled property
     *
     * @return true if the node has the is-nilled property
     */

    public boolean isNilled() {
        return false;
    }

}

