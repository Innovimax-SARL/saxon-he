////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.TryCatch;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.om.AttributeCollection;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.pattern.QNameTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;

import java.util.ArrayList;
import java.util.List;


/**
 * Handler for xsl:try elements in stylesheet.
 * The xsl:try element contains a sequence constructor or a select expression,
 * which defines the expression to be evaluated, and it may contain one or more
 * xsl:catch elements, which define the value to be returned in the event of
 * dynamic errors.
 */

public class XSLTry extends StyleElement {

    private Expression select;
    private boolean rollbackOutput = true;
    private List<QNameTest> catchTests = new ArrayList<QNameTest>();
    private List<Expression> catchExprs = new ArrayList<Expression>();

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
        String rollbackOutputAtt = null;

        AttributeCollection atts = getAttributeList();

        for (int a = 0; a < atts.getLength(); a++) {
            String f = atts.getNodeName(a).getDisplayName();
            if (f.equals("select")) {
                selectAtt = atts.getValue(a);
                select = makeExpression(selectAtt, a);
            } else if (f.equals("rollback-output")) {
                rollbackOutputAtt = atts.getValue(a);
            } else {
                checkUnknownAttribute(atts.getNodeName(a));
            }
        }

        if (rollbackOutputAtt != null) {
            rollbackOutput = processBooleanAttribute("rollback-output", rollbackOutputAtt);
            // TODO: not currently used
        }
    }

    protected boolean isPermittedChild(StyleElement child) {
        return child instanceof XSLCatch;
    }

    public void validate(ComponentDeclaration decl) throws XPathException {
        select = typeCheck("select", select);
        boolean foundCatch = false;
        AxisIterator kids = iterateAxis(AxisInfo.CHILD);
        NodeInfo kid;
        while ((kid = kids.next()) != null) {
            if (kid instanceof XSLCatch) {
                foundCatch = true;
            } else if (kid instanceof XSLFallback) {
                // no action;
            } else {
                if (foundCatch) {
                    compileError("xsl:catch elements must come after all other children of xsl:try (excepting xsl:fallback)", "XTSE0010");
                }
                if (select != null) {
                    compileError("An " + getDisplayName() + " element with a select attribute must be empty", "XTSE3140");
                }
            }
        }
        if (!foundCatch) {
            compileError("xsl:try must have at least one xsl:catch child element", "XTSE0010");
        }
    }

    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        Expression content = compileSequenceConstructor(exec, decl, true);
        if (select == null) {
            select = content;
        }
        TryCatch expr = new TryCatch(select);
        for (int i = 0; i < catchTests.size(); i++) {
            expr.addCatchExpression(catchTests.get(i), catchExprs.get(i));
        }
        return expr;
    }

    public void addCatchClause(QNameTest nameTest, Expression catchExpr) {
        catchTests.add(nameTest);
        catchExprs.add(catchExpr);
    }

}