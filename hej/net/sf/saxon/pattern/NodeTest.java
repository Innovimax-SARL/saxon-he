////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.NodeVectorTree;
import net.sf.saxon.type.*;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.z.IntPredicate;
import net.sf.saxon.z.IntSet;
import net.sf.saxon.z.IntUniversalSet;

import java.util.Iterator;
import java.util.Set;

/**
 * A NodeTest is a simple kind of pattern that enables a context-free test of whether
 * a node matches a given node kind and name. There are several kinds of node test: a full name test, a prefix test, and an
 * "any node of a given type" test, an "any node of any type" test, a "no nodes"
 * test (used, e.g. for "@comment()").
 * <p/>
 * <p>As well as being used to support XSLT pattern matching, NodeTests act as predicates in
 * axis steps, and also act as item types for type matching.</p>
 * <p/>
 * <p>For use in user-written application calling {@link NodeInfo#iterateAxis(byte, NodeTest)},
 * it is possible to write a user-defined subclass of <code>NodeTest</code> that implements
 * a single method, {@link #matches(int, NodeName, SchemaType)}</p>
 *
 * @author Michael H. Kay
 */

public abstract class NodeTest implements ItemType.WithSequenceTypeCache {

    private SequenceType _one;
    private SequenceType _oneOrMore;
    private SequenceType _zeroOrOne;
    private SequenceType _zeroOrMore;

    /**
     * Determine the default priority to use if this node-test appears as a match pattern
     * for a template with no explicit priority attribute.
     *
     * @return the default priority for the pattern
     */

    public abstract double getDefaultPriority();


    public boolean matches(Item item, TypeHierarchy th) {
        return item instanceof NodeInfo && matchesNode((NodeInfo) item);
    }

    /**
     * Get the primitive item type corresponding to this item type. For item(),
     * this is Type.ITEM. For node(), it is Type.NODE. For specific node kinds,
     * it is the value representing the node kind, for example Type.ELEMENT.
     * For anyAtomicValue it is Type.ATOMIC_VALUE. For numeric it is Type.NUMBER.
     * For other atomic types it is the primitive type as defined in XML Schema,
     * except that INTEGER is considered to be a primitive type.
     */

    /*@NotNull*/
    public ItemType getPrimitiveItemType() {
        int p = getPrimitiveType();
        if (p == Type.NODE) {
            return AnyNodeTest.getInstance();
        } else {
            return NodeKindTest.makeNodeKindTest(p);
        }
    }

    /**
     * Get the basic kind of object that this ItemType matches: for a NodeTest, this is the kind of node,
     * or Type.Node if it matches different kinds of nodes.
     *
     * @return the node kind matched by this node test
     */

    public int getPrimitiveType() {
        return Type.NODE;
    }

    /**
     * Get the name of the nodes matched by this nodetest, if it matches a specific name.
     * Return -1 if the node test matches nodes of more than one name
     */

    public int getFingerprint() {
        return -1;
    }

    /**
     * Get the name of the nodes matched by this nodetest, if it matches a specific name.
     * Return null if the node test matches nodes of more than one name
     */

    public StructuredQName getMatchingNodeName() {
        return null;
    }


    /**
     * Determine whether this item type is an atomic type
     *
     * @return true if this is ANY_ATOMIC_TYPE or a subtype thereof
     */
    public boolean isAtomicType() {
        return false;
    }

    /**
     * Determine whether this item type is atomic (that is, whether it can ONLY match
     * atomic values)
     *
     * @return false: this is not ANY_ATOMIC_TYPE or a subtype thereof
     */

    public boolean isPlainType() {
        return false;
    }

    /**
     * Get the item type of the atomic values that will be produced when an item
     * of this type is atomized (assuming that atomization succeeds)
     */

    /*@NotNull*/
    public AtomicType getAtomizedItemType() {
        // This is overridden for a ContentTypeTest
        return BuiltInAtomicType.ANY_ATOMIC;
    }

    /**
     * Ask whether values of this type are atomizable
     *
     * @return true unless it is known that these items will be elements with element-only
     *         content, in which case return false
     */

    public boolean isAtomizable() {
        // This is overridden for a ContentTypeTest
        return true;
    }

    /**
     * Get a matching function that can be used to test whether numbered nodes in a TinyTree
     * or DominoTree satisfy the node test. (Calling this matcher must give the same result
     * as calling <code>matchesNode(tree.getNode(nodeNr))</code>, but it may well be faster).
     * @param tree the tree against which the returned function will operate
     * @return an IntPredicate; the matches() method of this predicate takes a node number
     * as input, and returns true if and only if the node identified by this node number
     * matches the node test.
     */

    public IntPredicate getMatcher(final NodeVectorTree tree) {
        return new IntPredicate() {
            @Override
            public boolean matches(int nodeNr) {
                return matchesNode(tree.getNode(nodeNr));
            }
        };
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
     * @param annotation The actual content type of the node. Null means no constraint.
     * @return true if the node matches this node test
     */

    public abstract boolean matches(int nodeKind, NodeName name, SchemaType annotation);

    /**
     * Test whether this node test is satisfied by a given node. This alternative
     * method is used in the case of nodes where calculating the fingerprint is expensive,
     * for example DOM or JDOM nodes. The default implementation calls the method
     * {@link #matches(int, NodeName, SchemaType)}
     *
     * @param node the node to be matched
     * @return true if the node test is satisfied by the supplied node, false otherwise
     */

    public boolean matchesNode(/*@NotNull*/ NodeInfo node) {
        return matches(node.getNodeKind(), NameOfNode.makeName(node), node.getSchemaType());
    }

    /**
     * Get a mask indicating which kinds of nodes this NodeTest can match. This is a combination
     * of bits: 1<<Type.ELEMENT for element nodes, 1<<Type.TEXT for text nodes, and so on. The default
     * implementation indicates that nodes of all kinds are matched.
     *
     * @return a combination of bits: 1<<Type.ELEMENT for element nodes, 1<<Type.TEXT for text nodes, and so on
     */

    public int getNodeKindMask() {
        return 1 << Type.ELEMENT | 1 << Type.TEXT | 1 << Type.COMMENT | 1 << Type.PROCESSING_INSTRUCTION |
                1 << Type.ATTRIBUTE | 1 << Type.NAMESPACE | 1 << Type.DOCUMENT;
    }

    /**
     * Get the content type allowed by this NodeTest (that is, the type annotation of the matched nodes).
     * Return AnyType if there are no restrictions. The default implementation returns AnyType.
     *
     * @return the type annotation that all nodes matching this NodeTest must satisfy
     */

    public SchemaType getContentType() {
        Set<PrimitiveUType> m = getUType().decompose();
        Iterator<PrimitiveUType> it = m.iterator();
        if (m.size() == 1 && it.hasNext()) {
            PrimitiveUType p = it.next();
            switch (p) {
                case DOCUMENT:
                    return AnyType.getInstance();
                case ELEMENT:
                    return AnyType.getInstance();
                case ATTRIBUTE:
                    return AnySimpleType.getInstance();
                case COMMENT:
                    return BuiltInAtomicType.STRING;
                case TEXT:
                    return BuiltInAtomicType.UNTYPED_ATOMIC;
                case PI:
                    return BuiltInAtomicType.STRING;
                case NAMESPACE:
                    return BuiltInAtomicType.STRING;
            }
        }
        return AnyType.getInstance();
    }

    /**
     * Get the set of node names allowed by this NodeTest. This is returned as a set of Integer fingerprints.
     * If all names are permitted (i.e. there are no constraints on the node name), returns IntUniversalSet.getInstance().
     * The default implementation returns the universal set.
     *
     * @return the set of integer fingerprints of the node names that this node test can match.
     */

    /*@NotNull*/
    public IntSet getRequiredNodeNames() {
        return IntUniversalSet.getInstance();
    }

    /**
     * Determine whether the content type (if present) is nillable
     *
     * @return true if the content test (when present) can match nodes that are nilled
     */

    public boolean isNillable() {
        return true;
    }

    /**
     * Visit all the schema components used in this ItemType definition
     *
     * @param visitor the visitor class to be called when each component is visited
     */

    public void visitNamedSchemaComponents(SchemaComponentVisitor visitor) throws XPathException {
        // no action
    }

    /**
     * Copy a NodeTest.
     * Since they are never written to except in their constructors, returns the same.
     *
     * @return the original nodeTest
     */

    /*@NotNull*/
    public NodeTest copy() {
        return this;
    }

    /**
     * Generate Javascript code to convert a supplied Javascript value to this item type,
     * if conversion is possible, or throw an error otherwise.
     *
     * @param errorCode the error to be thrown if conversion is not possible
     * @param targetVersion
     * @return a Javascript instruction or sequence of instructions, which can be used as the body
     * of a Javascript function, and which returns the result of conversion to this type, or throws
     * an error if conversion is not possible. The variable "val" will hold the supplied Javascript
     * value.
     */
    public String generateJavaScriptItemTypeAcceptor(String errorCode, int targetVersion) throws XPathException {
        return "function test(item) {" + generateJavaScriptItemTypeTest(AnyItemType.getInstance(), targetVersion) + "};" +
                "if (test(val)) {return val;} else {throw SaxonJS.XError('Conversion failed', '" + errorCode + "');}";
    }

    /**
     * Get a sequence type representing exactly one instance of this atomic type
     *
     * @return a sequence type representing exactly one instance of this atomic type
     * @since 9.8.0.2
     */

    public SequenceType one() {
        if (_one == null) {
            _one = new SequenceType(this, StaticProperty.EXACTLY_ONE);
        }
        return _one;
    }

    /**
     * Get a sequence type representing zero or one instances of this atomic type
     *
     * @return a sequence type representing zero or one instances of this atomic type
     * @since 9.8.0.2
     */

    public SequenceType zeroOrOne() {
        if (_zeroOrOne == null) {
            _zeroOrOne = new SequenceType(this, StaticProperty.ALLOWS_ZERO_OR_ONE);
        }
        return _zeroOrOne;
    }

    /**
     * Get a sequence type representing one or more instances of this atomic type
     *
     * @return a sequence type representing one or more instances of this atomic type
     * @since 9.8.0.2
     */

    public SequenceType oneOrMore() {
        if (_oneOrMore == null) {
            _oneOrMore = new SequenceType(this, StaticProperty.ALLOWS_ONE_OR_MORE);
        }
        return _oneOrMore;
    }

    /**
     * Get a sequence type representing one or more instances of this atomic type
     *
     * @return a sequence type representing one or more instances of this atomic type
     * @since 9.8.0.2
     */

    public SequenceType zeroOrMore() {
        if (_zeroOrMore == null) {
            _zeroOrMore = new SequenceType(this, StaticProperty.ALLOWS_ZERO_OR_MORE);
        }
        return _zeroOrMore;
    }

}

