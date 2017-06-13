////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.Configuration;
import net.sf.saxon.functions.Nilled_1;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.NodeVectorTree;
import net.sf.saxon.tree.tiny.TinyTree;
import net.sf.saxon.type.*;
import net.sf.saxon.z.IntPredicate;

/**
 * NodeTest is an interface that enables a test of whether a node matches particular
 * conditions. ContentTypeTest tests for an element or attribute node with a particular
 * type annotation.
 *
 * @author Michael H. Kay
 */

public class ContentTypeTest extends NodeTest {

    private int kind;          // element or attribute
    private SchemaType schemaType;
    private Configuration config;
    private boolean nillable = false;

    /**
     * Create a ContentTypeTest
     *
     * @param nodeKind   the kind of nodes to be matched: always elements or attributes
     * @param schemaType the required type annotation, as a simple or complex schema type
     * @param config     the Configuration, supplied because this KindTest needs access to schema information
     * @param nillable   indicates whether an element with xsi:nil=true satisifies the test
     */

    public ContentTypeTest(int nodeKind, SchemaType schemaType, Configuration config, boolean nillable) {
        this.kind = nodeKind;
        this.schemaType = schemaType;
        this.config = config;
        this.nillable = nillable;
    }

    /**
     * Get the corresponding {@link net.sf.saxon.type.UType}. A UType is a union of primitive item
     * types.
     *
     * @return the smallest UType that subsumes this item type
     */
    public UType getUType() {
        return kind == Type.ELEMENT ? UType.ELEMENT : UType.ATTRIBUTE;
    }

    /**
     * Indicate whether nilled elements should be matched (the default is false)
     *
     * @param nillable true if nilled elements should be matched
     */
    public void setNillable(boolean nillable) {
        this.nillable = nillable;
    }

    /**
     * The test is nillable if a question mark was specified as the occurrence indicator
     *
     * @return true if the test is nillable
     */

    public boolean isNillable() {
        return nillable;
    }

    public SchemaType getSchemaType() {
        return schemaType;
    }

    public int getNodeKind() {
        return kind;
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
        return kind == nodeKind && matchesAnnotation(annotation);
    }

    @Override
    public IntPredicate getMatcher(final NodeVectorTree tree) {
        final byte[] nodeKindArray = tree.getNodeKindArray();
        return new IntPredicate() {
            @Override
            public boolean matches(int nodeNr) {
                return nodeKindArray[nodeNr] == kind &&
                        matchesAnnotation(((TinyTree) tree).getSchemaType(nodeNr)) &&
                        (nillable || !((TinyTree) tree).isNilled(nodeNr));
            }
        };
    }

    /**
     * Test whether this node test is satisfied by a given node. This alternative
     * method is used in the case of nodes where calculating the fingerprint is expensive,
     * for example DOM or JDOM nodes.
     *
     * @param node the node to be matched
     */

    public boolean matchesNode(/*@NotNull*/ NodeInfo node) {
        return node.getNodeKind() == kind &&
                matchesAnnotation(node.getSchemaType())
                && (nillable || !Nilled_1.isNilled(node));
    }

    private boolean matchesAnnotation(SchemaType annotation) {
        if (schemaType == AnyType.getInstance()) {
            return true;
        }

        if (annotation.equals(schemaType)) {
            return true;
        }

        // see if the type annotation is a subtype of the required type

        if (annotation == null) {
            // only true if annotation = XS_ANY_TYPE
            return false;
        }
        int r = config.getTypeHierarchy().schemaTypeRelationship(annotation, schemaType);
        return r == TypeHierarchy.SAME_TYPE || r == TypeHierarchy.SUBSUMED_BY;
    }

    /**
     * Determine the default priority of this node test when used on its own as a Pattern
     */

    public final double getDefaultPriority() {
        return 0;
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
     * Get the content type allowed by this NodeTest (that is, the type annotation of the matched nodes).
     * Return AnyType if there are no restrictions. The default implementation returns AnyType.
     */

    public SchemaType getContentType() {
        return schemaType;
    }

    /**
     * Get the item type of the atomic values that will be produced when an item
     * of this type is atomized (assuming that atomization succeeds)
     */

    /*@NotNull*/
    public AtomicType getAtomizedItemType() {
        SchemaType type = schemaType;
        try {
            if (type.isAtomicType()) {
                return (AtomicType) type;
            } else if (type instanceof ListType) {
                SimpleType mem = ((ListType) type).getItemType();
                if (mem.isAtomicType()) {
                    return (AtomicType) mem;
                }
            } else if (type instanceof ComplexType && ((ComplexType) type).isSimpleContent()) {
                SimpleType ctype = ((ComplexType) type).getSimpleContentType();
                assert ctype != null;
                if (ctype.isAtomicType()) {
                    return (AtomicType) ctype;
                } else if (ctype instanceof ListType) {
                    SimpleType mem = ((ListType) ctype).getItemType();
                    if (mem.isAtomicType()) {
                        return (AtomicType) mem;
                    }
                }
            }
        } catch (MissingComponentException e) {
            return BuiltInAtomicType.ANY_ATOMIC;
        }
        return BuiltInAtomicType.ANY_ATOMIC;
    }

    /**
     * Ask whether values of this type are atomizable
     *
     * @return true unless it is known that these items will be elements with element-only
     *         content, in which case return false
     */

    public boolean isAtomizable() {
        return !(schemaType.isComplexType() &&
                ((ComplexType) schemaType).getVariety() == ComplexType.VARIETY_ELEMENT_ONLY);
    }

    /**
     * Visit all the schema components used in this ItemType definition
     *
     * @param visitor the visitor class to be called when each component is visited
     */

    public void visitNamedSchemaComponents(SchemaComponentVisitor visitor) throws XPathException {
        visitor.visitSchemaComponent(schemaType);
    }

    public String toString() {
        return (kind == Type.ELEMENT ? "element(*, " : "attribute(*, ") +
                schemaType.getEQName() + ')';
    }

    /**
     * Returns a hash code value for the object.
     */

    public int hashCode() {
        return kind << 20 ^ schemaType.hashCode();
    }

    /**
     * Indicates whether some other object is "equal to" this one.
     */
    public boolean equals(Object other) {
        return other instanceof ContentTypeTest &&
                ((ContentTypeTest) other).kind == kind &&
                ((ContentTypeTest) other).schemaType == schemaType &&
                ((ContentTypeTest) other).nillable == nillable;
    }

    /**
     * Generate Javascript code to test whether an item conforms to this item type
     *
     * @return a Javascript instruction or sequence of instructions, which can be used as the body
     * of a Javascript function, and which returns a boolean indication whether the value of the
     * variable "item" is an instance of this item type.
     * @throws XPathException if JS code cannot be generated for this item type, for example because
     *                        the test is schema-aware.
     * @param knownToBe
     */
    @Override
    public String generateJavaScriptItemTypeTest(ItemType knownToBe) throws XPathException {
        if (kind == Type.ELEMENT) {
            if (schemaType == Untyped.getInstance() || schemaType == AnyType.getInstance()) {
                return NodeKindTest.makeNodeKindTest(getNodeKind()).generateJavaScriptItemTypeTest(AnyItemType.getInstance());
            } else if (schemaType == BuiltInAtomicType.UNTYPED_ATOMIC || schemaType == BuiltInAtomicType.ANY_ATOMIC) {
                return "return false;";
            }
        } else if (kind == Type.ATTRIBUTE) {
            if (schemaType == Untyped.getInstance() || schemaType == AnyType.getInstance()
                    || schemaType == BuiltInAtomicType.UNTYPED_ATOMIC || schemaType == BuiltInAtomicType.ANY_ATOMIC) {
                return NodeKindTest.makeNodeKindTest(getNodeKind()).generateJavaScriptItemTypeTest(AnyItemType.getInstance());
            }
        }
        throw new XPathException("Cannot generate JS code to test type annotations", SaxonErrorCode.SXJS0001);
    }
}

