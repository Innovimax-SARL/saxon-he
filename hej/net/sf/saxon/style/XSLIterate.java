////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.instruct.IterateInstr;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.instruct.LocalParam;
import net.sf.saxon.expr.instruct.LocalParamBlock;
import net.sf.saxon.om.AttributeCollection;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.ListIterator;

import java.util.ArrayList;
import java.util.List;

/**
 * Handler for xsl:iterate elements in stylesheet. <br>
 */

public class XSLIterate extends StyleElement {

    Expression select = null;
    boolean compilable;

    /**
     * Determine whether this node is an instruction.
     *
     * @return true - it is an instruction
     */

    public boolean isInstruction() {
        return true;
    }

    /**
     * Specify that xsl:param is a permitted child
     */

    protected boolean isPermittedChild(StyleElement child) {
        return child instanceof XSLLocalParam || child instanceof XSLOnCompletion;
    }

    /**
     * Determine whether this type of element is allowed to contain a sequence constructor
     *
     * @return true: yes, it may contain a sequence constructor
     */

    public boolean mayContainSequenceConstructor() {
        return true;
    }

    /**
     * Determine whether this type of element is allowed to contain an xsl:param element
     *
     * @param attName if null, the method tests whether an xsl:param child is allowed.
     *                If non-null, it tests whether an xsl:param child with the given attribute name is allowed
     * @return true if this element is allowed to contain an xsl:param
     */

    protected boolean mayContainParam(String attName) {
        return !"required".equals(attName);
    }

    public void prepareAttributes() throws XPathException {

        AttributeCollection atts = getAttributeList();

        String selectAtt = null;

        for (int a = 0; a < atts.getLength(); a++) {
            String f = atts.getQName(a);
            if (f.equals("select")) {
                selectAtt = atts.getValue(a);
                select = makeExpression(selectAtt, a);
            } else {
                checkUnknownAttribute(atts.getNodeName(a));
            }
        }

        if (selectAtt == null) {
            reportAbsence("select");
        }

    }

    public void setCompilable(boolean compilable) {
        this.compilable = compilable;
    }

    public boolean isCompilable() {
        return compilable;
    }

    public void validate(ComponentDeclaration decl) throws XPathException {
        //checkParamComesFirst(false);
        select = typeCheck("select", select);
        if (!hasChildNodes()) {
            compileWarning("An empty xsl:iterate instruction has no effect", SaxonErrorCode.SXWN9009);
        }
    }

    /*@Nullable*/
    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        SequenceIterator children = iterateAxis(AxisInfo.CHILD);
        List<NodeInfo> nonFinallyChildren = new ArrayList<NodeInfo>();
        Expression finallyExp = null;
        List<XSLLocalParam> params = new ArrayList<XSLLocalParam>();
        while (true) {
            NodeInfo node = (NodeInfo) children.next();
            if (node == null) {
                break;
            } else if (node instanceof XSLLocalParam) {
                params.add((XSLLocalParam) node);
            } else if (node instanceof XSLOnCompletion) {
                finallyExp = ((XSLOnCompletion) node).compile(exec, decl);
            } else {
                nonFinallyChildren.add(node);
            }
        }
        LocalParam[] compiledParams = new LocalParam[params.size()];
        for (int i = 0; i < params.size(); i++) {
            compiledParams[i] = (LocalParam) params.get(i).compile(exec, decl);
            if (compiledParams[i].isImplicitlyRequiredParam()) {
                // see spec bug 25158; Saxon bug 2041
                compileError("The parameter must be given an initial value because () is not valid, given the declared type", "XTSE3520");
            }
        }
        LocalParamBlock paramBlock = new LocalParamBlock(compiledParams);
        Expression action = compileSequenceConstructor(exec, decl, new ListIterator(nonFinallyChildren), false);
        if (action == null) {
            // body of xsl:iterate is empty: it's a no-op.
            return Literal.makeEmptySequence();
        }
        try {
            action = action.simplify();
            return new IterateInstr(select, paramBlock, action, finallyExp);
        } catch (XPathException err) {
            compileError(err);
            return null;
        }
    }


}