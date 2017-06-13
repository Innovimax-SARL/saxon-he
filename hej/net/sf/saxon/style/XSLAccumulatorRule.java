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
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.pattern.Pattern;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Whitespace;

/**
 * Handler for xsl:accumulator-rule elements in a stylesheet (XSLT 3.0).
 */

public class XSLAccumulatorRule extends StyleElement {

    private Pattern match;
    private boolean postDescent;
    private Expression select;

    public void prepareAttributes() throws XPathException {

        String matchAtt = null;
        String newValueAtt = null;

        AttributeCollection atts = getAttributeList();
        for (int a = 0; a < atts.getLength(); a++) {
            String f = atts.getQName(a);
            if (f.equals("match")) {
                matchAtt = atts.getValue(a);
            } else if (f.equals("select")) {
                newValueAtt = atts.getValue(a);
                select = makeExpression(newValueAtt, a);
            } else if (f.equals("phase")) {
                String phaseAtt = Whitespace.trim(atts.getValue(a));
                if ("start".equals(phaseAtt)) {
                    postDescent = false;
                } else if ("end".equals(phaseAtt)) {
                    postDescent = true;
                } else {
                    postDescent = true;
                    compileError("phase must be 'start' or 'end'", "XTSE0020");
                }
            } else {
                checkUnknownAttribute(atts.getNodeName(a));
            }
        }

        if (matchAtt == null) {
            reportAbsence("match");
            matchAtt = "non-existent-element";
        }
        match = makePattern(matchAtt, "match");

    }


    public void validate(ComponentDeclaration decl) throws XPathException {
        select = typeCheck("select", select);
        match = typeCheck("match", match);
        if (select != null && iterateAxis(AxisInfo.CHILD).next() != null) {
            compileError("If the xsl:accumulator-rule element has a select attribute then it must have no children");
        }
    }

    public Expression getNewValueExpression(Compilation compilation, ComponentDeclaration decl) throws XPathException {
        if (select == null) {
            select = compileSequenceConstructor(compilation, decl, true);
        }
        return select;
    }

    public Pattern getMatch() {
        return match;
    }

    public void setMatch(Pattern match) {
        this.match = match;
    }

    public boolean isPostDescent() {
        return postDescent;
    }

    public void setPostDescent(boolean postDescent) {
        this.postDescent = postDescent;
    }

    public Expression getSelect() {
        return select;
    }

    public void setSelect(Expression select) {
        this.select = select;
    }

    public SourceBinding hasImplicitBinding(StructuredQName name) {
        if (name.getLocalPart().equals("value") && name.hasURI("")) {
            SourceBinding sb = new SourceBinding(this);
            sb.setVariableQName(new StructuredQName("", "", "value"));
            assert ((XSLAccumulator)getParent()) != null;
            sb.setDeclaredType(((XSLAccumulator)getParent()).getResultType());
            sb.setProperty(SourceBinding.IMPLICITLY_DECLARED, true);
            return sb;
        } else {
            return null;
        }
    }
}
