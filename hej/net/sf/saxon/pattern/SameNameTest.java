////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.om.*;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.NodeVectorTree;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.UType;
import net.sf.saxon.z.IntPredicate;
import net.sf.saxon.z.IntSet;
import net.sf.saxon.z.IntSingletonSet;

/**
 * NodeTest is an interface that enables a test of whether a node has a particular
 * name and type. A SameNameTest matches a node that has the same node kind and name
 * as a supplied node.
 *
 * <p>Note: it's not safe to use this if the supplied node is mutable.</p>
 *
 * @author Michael H. Kay
 */

public class SameNameTest extends NodeTest implements QNameTest {

    private NodeInfo origin;
    /**
     * Create a SameNameTest to match nodes by name
     *
     * @param origin the node whose node kind and name must be matched
     * @since 9.0
     */

    public SameNameTest(NodeInfo origin) {
        this.origin = origin;
    }

    /**
     * Get the node kind that this name test matches
     *
     * @return the matching node kind
     */

    public int getNodeKind() {
        return origin.getNodeKind();
    }

    /**
     * Get the corresponding {@link net.sf.saxon.type.UType}. A UType is a union of primitive item
     * types.
     *
     * @return the smallest UType that subsumes this item type
     */
    public UType getUType() {
        return UType.fromTypeCode(origin.getNodeKind());
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
        if (nodeKind != origin.getNodeKind()) {
            return false;
        }
        if (name.hasFingerprint() && origin.hasFingerprint()) {
            return name.getFingerprint() == origin.getFingerprint();
        } else {
            return name.hasURI(origin.getURI()) && name.getLocalPart().equals(origin.getLocalPart());
        }
    }

    public IntPredicate getMatcher(final NodeVectorTree tree) {
        final byte[] nodeKindArray = tree.getNodeKindArray();
        final int[] nameCodeArray = tree.getNameCodeArray();
        return new IntPredicate() {
            @Override
            public boolean matches(int nodeNr) {
                int k = nodeKindArray[nodeNr];
                if (k == Type.WHITESPACE_TEXT) {
                    k = Type.TEXT;
                }
                if (k != origin.getNodeKind()) {
                    return false;
                } else if (origin.hasFingerprint()) {
                    return (nameCodeArray[nodeNr] & 0xfffff) == origin.getFingerprint();
                } else {
                    return Navigator.haveSameName(tree.getNode(nodeNr), origin);
                }
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

    public boolean matchesNode(NodeInfo node) {
        return node == origin ||
            (node.getNodeKind() == origin.getNodeKind() && Navigator.haveSameName(node, origin));
    }

    /**
     * Test whether the NameTest matches a given QName
     *
     * @param qname the QName to be matched
     * @return true if the name matches
     */

    public boolean matches(StructuredQName qname) {
        return NameOfNode.makeName(origin).getStructuredQName().equals(qname);
    }

    /**
     * Determine the default priority of this node test when used on its own as a Pattern
     */

    public final double getDefaultPriority() {
        return 0.0;
    }

    /**
     * Get the fingerprint required
     */

    public int getFingerprint() {
        if (origin.hasFingerprint()) {
            return origin.getFingerprint();
        } else {
            NamePool pool = origin.getConfiguration().getNamePool();
            return pool.allocateFingerprint(origin.getURI(), origin.getLocalPart());
        }
    }

    /**
     * Determine the types of nodes to which this pattern applies. Used for optimisation.
     * For patterns that match nodes of several types, return Type.NODE
     *
     * @return the type of node matched by this pattern. e.g. Type.ELEMENT or Type.TEXT
     */

    public int getPrimitiveType() {
        return origin.getNodeKind();
    }

    /**
     * Get a mask indicating which kinds of nodes this NodeTest can match. This is a combination
     * of bits: 1<<Type.ELEMENT for element nodes, 1<<Type.TEXT for text nodes, and so on.
     */

    public int getNodeKindMask() {
        return 1 << origin.getNodeKind();
    }

    /**
     * Get the set of node names allowed by this NodeTest. This is returned as a set of Integer fingerprints.
     * A null value indicates that all names are permitted (i.e. that there are no constraints on the node name.
     * The default implementation returns null.
     */

    /*@NotNull*/
    public IntSet getRequiredNodeNames() {
        return new IntSingletonSet(getFingerprint());
    }

    /**
     * Get the namespace URI matched by this nametest
     *
     * @return the namespace URI (using "" for the "null namepace")
     */

    public String getNamespaceURI() {
        return origin.getURI();
    }

    /**
     * Get the local name matched by this nametest
     *
     * @return the local name
     */

    public String getLocalPart() {
        return origin.getLocalPart();
    }

    public String toString() {
        switch (origin.getNodeKind()) {
            case Type.ELEMENT:
                return "element(" + NameOfNode.makeName(origin).getStructuredQName().getEQName() + ")";
            case Type.ATTRIBUTE:
                return "attribute(" + NameOfNode.makeName(origin).getStructuredQName().getEQName() + ")";
            case Type.PROCESSING_INSTRUCTION:
                return "processing-instruction(" + origin.getLocalPart() + ')';
            case Type.NAMESPACE:
                return "namespace-node(" + origin.getLocalPart() + ')';
            case Type.COMMENT:
                return "comment()";
            case Type.DOCUMENT:
                return "document-node()";
            case Type.TEXT:
                return "text()";
            default:
                return "***";
        }
    }

    /**
     * Returns a hash code value for the object.
     */

    public int hashCode() {
        return origin.getNodeKind() << 20 ^ origin.getURI().hashCode() ^ origin.getLocalPart().hashCode();
    }

    /**
     * Determines whether two NameTests are equal
     */

    public boolean equals(Object other) {
        return other instanceof SameNameTest &&
                matchesNode(((SameNameTest) other).origin);
    }

    /**
     * Generate Javascript code to test if a name matches the test.
     *
     * @return JS code as a string. The generated code will be used
     * as the body of a JS function in which the argument name "q" is an
     * XdmQName object holding the name. The XdmQName object has properties
     * uri and local.
     * @param targetVersion
     */
    public String generateJavaScriptNameTest(int targetVersion) {
        // Not applicable
        return "false";
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
     * @param targetVersion
     */
    @Override
    public String generateJavaScriptItemTypeTest(ItemType knownToBe, int targetVersion) throws XPathException {
        throw new XPathException("Cannot generate JS code for a SameNameTest", SaxonErrorCode.SXJS0001);
    }
}

