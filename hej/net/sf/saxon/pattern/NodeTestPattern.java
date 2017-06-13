////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.SchemaDeclaration;
import net.sf.saxon.type.UType;

/**
 * A NodeTestPattern is a pattern that consists simply of a NodeTest
 *
 * @author Michael H. Kay
 */

public class NodeTestPattern extends Pattern {

    private NodeTest nodeTest;


    /**
     * Create an NodeTestPattern that matches all items of a given type
     *
     * @param test the type that the items must satisfy for the pattern to match
     */

    public NodeTestPattern(NodeTest test) {
        nodeTest = test;
        setPriority(test.getDefaultPriority());
    }

    /**
     * Determine whether this Pattern matches the given Node. This is the main external interface
     * for matching patterns: it sets current() to the node being tested
     *
     * @param item    The item to be tested against the Pattern
     * @param context The context in which the match is to take place.
     * @return true if the item matches the Pattern, false otherwise
     */

    public boolean matches(Item item, XPathContext context) {
        return item instanceof NodeInfo && nodeTest.matchesNode((NodeInfo)item);
    }

    /**
     * Get a NodeTest that all the nodes matching this pattern must satisfy
     */

    public NodeTest getItemType() {
        return nodeTest;
    }

    /**
     * Get a UType indicating which kinds of items this Pattern can match.
     *
     * @return a UType indicating all the primitive types of item that the pattern can match.
     */
    @Override
    public UType getUType() {
        return nodeTest.getUType();
    }

    /**
     * Determine the name fingerprint of nodes to which this pattern applies. Used for
     * optimisation.
     *
     * @return A fingerprint that the nodes must match, or -1 if it can match multiple fingerprints
     */

    public int getFingerprint() {
        return nodeTest.getFingerprint();
    }

    /**
     * Display the pattern for diagnostics
     */

    public String toString() {
        return nodeTest.toString();
    }

    /**
     * Determine whether this pattern is the same as another pattern
     *
     * @param other the other object
     */

    public boolean equals(/*@NotNull*/ Object other) {
        return (other instanceof NodeTestPattern) &&
                ((NodeTestPattern) other).nodeTest.equals(nodeTest);
    }

    /**
     * Hashcode supporting equals()
     */

    public int hashCode() {
        return 0x7aeffea8 ^ nodeTest.hashCode();
    }

    /**
     * Convert the pattern to a typed pattern, in which an element name is treated as
     * schema-element(N)
     *
     * @param val either "strict" or "lax" depending on the value of xsl:mode/@typed
     * @return either the original pattern unchanged, or a new pattern as the result of the
     * conversion
     */
    @Override
    public Pattern convertToTypedPattern(String val) throws XPathException {
        if (nodeTest instanceof NameTest && nodeTest.getUType() == UType.ELEMENT) {
            SchemaDeclaration decl = getConfiguration().getElementDeclaration(nodeTest.getMatchingNodeName());
            if (decl == null) {
                if ("lax".equals(val)) {
                    return this;
                } else {
                    // See spec bug 25517
                    throw new XPathException("The mode specifies typed='strict', " +
                            "but there is no schema element declaration named " + nodeTest.toString(), "XTSE3105");
                }
            } else {
                NodeTest schemaNodeTest = decl.makeSchemaNodeTest();
                return new NodeTestPattern(schemaNodeTest);
            }
        } else {
            return this;
        }
    }

    public void export(ExpressionPresenter presenter) throws XPathException {
        presenter.startElement("p.nodeTest");
        presenter.emitAttribute("test", nodeTest.toString());
        if ("JS".equals(presenter.getOption("target"))) {
            try {
                presenter.emitAttribute("jsTest", nodeTest.generateJavaScriptItemTypeTest(AnyItemType.getInstance()));
            } catch (XPathException e) {
                e.maybeSetLocation(getLocation());
                throw e;
            }
        }
        presenter.endElement();
    }

    /**
     * Copy a pattern. This makes a deep copy.
     *
     * @return the copy of the original pattern
     * @param rebindings
     */

    /*@NotNull*/
    public Pattern copy(RebindingMap rebindings) {
        NodeTestPattern n = new NodeTestPattern(nodeTest.copy());
        ExpressionTool.copyLocationInfo(this, n);
        return n;
    }

    public NodeTest getNodeTest() {
        return nodeTest;
    }
}

