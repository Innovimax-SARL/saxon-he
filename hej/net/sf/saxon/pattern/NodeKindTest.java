////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.tree.tiny.NodeVectorTree;
import net.sf.saxon.type.*;
import net.sf.saxon.z.IntPredicate;

/**
 * NodeTest is an interface that enables a test of whether a node has a particular
 * name and kind. A NodeKindTest matches the node kind only.
 *
 * @author Michael H. Kay
 */

public class NodeKindTest extends NodeTest {

    public static final NodeKindTest DOCUMENT = new NodeKindTest(Type.DOCUMENT);
    public static final NodeKindTest ELEMENT = new NodeKindTest(Type.ELEMENT);
    public static final NodeKindTest ATTRIBUTE = new NodeKindTest(Type.ATTRIBUTE);
    public static final NodeKindTest TEXT = new NodeKindTest(Type.TEXT);
    public static final NodeKindTest COMMENT = new NodeKindTest(Type.COMMENT);
    public static final NodeKindTest PROCESSING_INSTRUCTION = new NodeKindTest(Type.PROCESSING_INSTRUCTION);
    public static final NodeKindTest NAMESPACE = new NodeKindTest(Type.NAMESPACE);


    private int kind;
    private UType uType;

    private NodeKindTest(int nodeKind) {
        kind = nodeKind;
        uType = UType.fromTypeCode(nodeKind);
    }

    /**
     * Get the node kind matched by this test
     *
     * @return the matching node kind
     */

    public int getNodeKind() {
        return kind;
    }

    /**
     * Get the corresponding {@link net.sf.saxon.type.UType}. A UType is a union of primitive item
     * types.
     *
     * @return the smallest UType that subsumes this item type
     */
    public UType getUType() {
        return uType;
    }

    /**
     * Make a test for a given kind of node
     */

    public static NodeTest makeNodeKindTest(int kind) {
        switch (kind) {
            case Type.DOCUMENT:
                return DOCUMENT;
            case Type.ELEMENT:
                return ELEMENT;
            case Type.ATTRIBUTE:
                return ATTRIBUTE;
            case Type.COMMENT:
                return COMMENT;
            case Type.TEXT:
                return TEXT;
            case Type.PROCESSING_INSTRUCTION:
                return PROCESSING_INSTRUCTION;
            case Type.NAMESPACE:
                return NAMESPACE;
            case Type.NODE:
                return AnyNodeTest.getInstance();
            default:
                throw new IllegalArgumentException("Unknown node kind " + kind + " in NodeKindTest");
        }
    }

    public boolean matches(Item item, /*@NotNull*/TypeHierarchy th) {
        return item instanceof NodeInfo && kind == ((NodeInfo) item).getNodeKind();
    }

    /**
     * Test whether this node test is satisfied by a given node. This method is only
     * fully supported for a subset of NodeTests, because it doesn't provide all the information
     * needed to evaluate all node tests. In particular (a) it can't be used to evaluate a node
     * test of the form element(N,T) or schema-element(E) where it is necessary to know whether the
     * node is nilled, and (b) it can't be used to evaluate a node test of the form
     * document-node(element(X)). This in practice means that it is used (a) to evaluate the
     * simple node tests found in the XPath 1.0 subset used in XML Schema, and (b) to evaluate
     * node tests where the node kind is known to be an attribute.
     *
     * @param nodeKind   The kind of node to be matched
     * @param name       identifies the expanded name of the node to be matched.
     *                   The value should be null for a node with no name.
     * @param annotation The actual content type of the node
     */
    @Override
    public boolean matches(int nodeKind, NodeName name, SchemaType annotation) {
        return kind == nodeKind;
    }

    public IntPredicate getMatcher(final NodeVectorTree tree) {
        final byte[] nodeKindArray = tree.getNodeKindArray();
        if (kind == Type.TEXT) {
            return new IntPredicate() {
                @Override
                public boolean matches(int nodeNr) {
                    int k = nodeKindArray[nodeNr];
                    return k == Type.TEXT || k == Type.WHITESPACE_TEXT;
                }
            };
        } else {
            return new IntPredicate() {
                @Override
                public boolean matches(int nodeNr) {
                    return nodeKindArray[nodeNr] == kind;
                }
            };
        }
    }

    /**
     * Test whether this node test is satisfied by a given node. This alternative
     * method is used in the case of nodes where calculating the fingerprint is expensive,
     * for example DOM or JDOM nodes.
     *
     * @param node the node to be matched
     */

    public boolean matchesNode(NodeInfo node) {
        return node.getNodeKind() == kind;
    }


    /**
     * Determine the default priority of this node test when used on its own as a Pattern
     */

    public final double getDefaultPriority() {
        return -0.5;
    }

    /**
     * Determine the types of nodes to which this pattern applies. Used for optimisation.
     *
     * @return the type of node matched by this pattern. e.g. Type.ELEMENT or Type.TEXT
     */

    public int getPrimitiveType() {
        return kind;
    }

    /**
     * Get a mask indicating which kinds of nodes this NodeTest can match. This is a combination
     * of bits: 1<<Type.ELEMENT for element nodes, 1<<Type.TEXT for text nodes, and so on.
     */

    public int getNodeKindMask() {
        return 1 << kind;
    }

    /**
     * Get the content type allowed by this NodeTest (that is, the type of content allowed).
     * Return AnyType if there are no restrictions.
     */

    public SchemaType getContentType() {
        switch (kind) {
            case Type.DOCUMENT:
                return AnyType.getInstance();
            case Type.ELEMENT:
                return AnyType.getInstance();
            case Type.ATTRIBUTE:
                return AnySimpleType.getInstance();
            case Type.COMMENT:
                return BuiltInAtomicType.STRING;
            case Type.TEXT:
                return BuiltInAtomicType.UNTYPED_ATOMIC;
            case Type.PROCESSING_INSTRUCTION:
                return BuiltInAtomicType.STRING;
            case Type.NAMESPACE:
                return BuiltInAtomicType.STRING;
            default:
                throw new AssertionError("Unknown node kind");
        }
    }

    /**
     * Get the content type allowed by this NodeTest (that is, the type annotation).
     * Return AnyType if there are no restrictions. The default implementation returns AnyType.
     */

    /*@NotNull*/
    public AtomicType getAtomizedItemType() {
        switch (kind) {
            case Type.DOCUMENT:
                return BuiltInAtomicType.UNTYPED_ATOMIC;
            case Type.ELEMENT:
                return BuiltInAtomicType.ANY_ATOMIC;
            case Type.ATTRIBUTE:
                return BuiltInAtomicType.ANY_ATOMIC;
            case Type.COMMENT:
                return BuiltInAtomicType.STRING;
            case Type.TEXT:
                return BuiltInAtomicType.UNTYPED_ATOMIC;
            case Type.PROCESSING_INSTRUCTION:
                return BuiltInAtomicType.STRING;
            case Type.NAMESPACE:
                return BuiltInAtomicType.STRING;
            default:
                throw new AssertionError("Unknown node kind");
        }
    }

    /*@NotNull*/
    public String toString() {
        return toString(kind);
    }

    public static String toString(int kind) {
        switch (kind) {
            case Type.DOCUMENT:
                return "document-node()";
            case Type.ELEMENT:
                return "element()";
            case Type.ATTRIBUTE:
                return "attribute()";
            case Type.COMMENT:
                return "comment()";
            case Type.TEXT:
                return "text()";
            case Type.PROCESSING_INSTRUCTION:
                return "processing-instruction()";
            case Type.NAMESPACE:
                return "namespace-node()";
            default:
                return "** error **";
        }
    }

    /**
     * Get the name of a node kind
     *
     * @param kind the node kind, for example Type.ELEMENT or Type.ATTRIBUTE
     * @return the name of the node kind, for example "element" or "attribute"
     */

    public static String nodeKindName(int kind) {
        switch (kind) {
            case Type.DOCUMENT:
                return "document";
            case Type.ELEMENT:
                return "element";
            case Type.ATTRIBUTE:
                return "attribute";
            case Type.COMMENT:
                return "comment";
            case Type.TEXT:
                return "text";
            case Type.PROCESSING_INSTRUCTION:
                return "processing-instruction";
            case Type.NAMESPACE:
                return "namespace";
            default:
                return "** error **";
        }
    }


    /**
     * Returns a hash code value for the object.
     */

    public int hashCode() {
        return kind;
    }

    /**
     * Indicates whether some other object is "equal to" this one.
     */
    public boolean equals(Object other) {
        return other instanceof NodeKindTest &&
                ((NodeKindTest) other).kind == kind;
    }

    /**
     * Generate Javascript code to test whether an item conforms to this item type
     *
     * @return a Javascript instruction or sequence of instructions, which can be used as the body
     * of a Javascript function, and which returns a boolean indication whether the value of the
     * variable "item" is an instance of this item type.
     * @param knownToBe
     */
    @Override
    public String generateJavaScriptItemTypeTest(ItemType knownToBe) {
        String instNode = knownToBe instanceof NodeTest ? " " : " SaxonJS.U.isNode(item) && ";
        switch (getNodeKind()) {
            case Type.DOCUMENT:
                return "return" + instNode + "(item.nodeType===9||item.nodeType===11);";
            case Type.ELEMENT:
                return "return" + instNode + "item.nodeType===1;";
            case Type.TEXT:
                return "return" + instNode + "item.nodeType===3;";
            case Type.COMMENT:
                return "return" + instNode + "item.nodeType===8;";
            case Type.PROCESSING_INSTRUCTION:
                return "return" + instNode + "item.nodeType===7&&item.target!=='xml';";
            case Type.ATTRIBUTE:
                return "return SaxonJS.U.isAttr(item)";
            case Type.NAMESPACE:
                return "return SaxonJS.U.isNamespaceNode(item)";
            default:
                return "return false;";
        }
    }

}

