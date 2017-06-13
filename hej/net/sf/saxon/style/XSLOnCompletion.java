////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.om.AttributeCollection;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;

/**
 * An xsl:on-completion element in the stylesheet (XSLT 3.0). <br>
 */

public class XSLOnCompletion extends StyleElement {

    private Expression select;

    /**
     * Determine whether this node is an instruction.
     *
     * @return true - it is an instruction
     */

    public boolean isInstruction() {
        return true;
    }

    /**
     * Determine whether this type of element is allowed to contain a sequence constructor
     *
     * @return true: yes, it may contain a sequence constructor
     */

    public boolean mayContainSequenceConstructor() {
        return true;
    }

    public void prepareAttributes() throws XPathException {
        String selectAtt = null;

        AttributeCollection atts = getAttributeList();

        for (int a = 0; a < atts.getLength(); a++) {
            String f = atts.getQName(a);
            if (f.equals("select")) {
                selectAtt = atts.getValue(a);
                select = makeExpression(selectAtt, a);
            } else {
                checkUnknownAttribute(atts.getNodeName(a));
            }
        }

    }

    public void validate(ComponentDeclaration decl) throws XPathException {
        StyleElement parent = (StyleElement) getParent();
        if (!(parent instanceof XSLIterate)) {
            compileError("xsl:on-completion is not allowed as a child of " + parent.getDisplayName(), "XTSE0010");
        }
        // See W3C bug 24179, which changes the position of xsl:on-completion within xsl:iterate
        // For the time being we allow it anywhere, and give a warning if it is in the wrong place
        AxisIterator sibs = iterateAxis(AxisInfo.PRECEDING_SIBLING, NodeKindTest.ELEMENT);
        NodeInfo sib;
        while ((sib = sibs.next()) != null) {
            if (!(sib instanceof XSLFallback || sib instanceof XSLLocalParam)) {
                compileWarning("The rules for xsl:iterate have changed (see W3C bug 24179): " +
                        "xsl:on-completion must now be the first child of xsl:iterate after the xsl:param elements", "XTSE0010");
            }
        }
        if (select != null && iterateAxis(AxisInfo.CHILD).next() != null) {
            compileError("An xsl:on-completion element with a select attribute must be empty", "XTSE3125");
        }
        select = typeCheck("select", select);
    }

    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        if (select == null) {
            return compileSequenceConstructor(exec, decl, true);
        } else {
            return select;
        }
    }


}