////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StringLiteral;
import net.sf.saxon.expr.instruct.Block;
import net.sf.saxon.expr.instruct.Message;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.AttributeCollection;
import net.sf.saxon.om.NamespaceException;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Whitespace;


/**
 * An xsl:message element in the stylesheet. <br>
 */

public final class XSLMessage extends StyleElement {

    private Expression terminate = null;
    private Expression select = null;
    private Expression errorCode = null;
    private Expression timer = null;

    /**
     * Determine whether this node is an instruction.
     *
     * @return true - it is an instruction
     */

    public boolean isInstruction() {
        return true;
    }

    /**
     * Determine whether this type of element is allowed to contain a template-body
     *
     * @return true: yes, it may contain a template-body
     */

    public boolean mayContainSequenceConstructor() {
        return true;
    }

    public void prepareAttributes() throws XPathException {

        String terminateAtt = null;
        String selectAtt = null;
        String errorCodeAtt = null;
        AttributeCollection atts = getAttributeList();

        for (int a = 0; a < atts.getLength(); a++) {
            String f = atts.getQName(a);
            if (f.equals("terminate")) {
                terminateAtt = Whitespace.trim(atts.getValue(a));
                terminate = makeAttributeValueTemplate(terminateAtt, a);
            } else if (f.equals("select")) {
                selectAtt = atts.getValue(a);
                select = makeExpression(selectAtt, a);
            } else if (f.equals("error-code")) {
                errorCodeAtt = atts.getValue(a);
                errorCode = makeAttributeValueTemplate(errorCodeAtt, a);
            } else if (atts.getURI(a).equals(NamespaceConstant.SAXON) && atts.getLocalName(a).equals("time")) {
                boolean timed = processBooleanAttribute("saxon:time", atts.getValue(a));
                if (timed) {
                    timer = makeExpression(
                            "format-dateTime(Q{http://saxon.sf.net/}timestamp(),'[Y0001]-[M01]-[D01]T[H01]:[m01]:[s01].[f,3-3] - ')", a);
                }
            } else {
                checkUnknownAttribute(atts.getNodeName(a));
            }
        }

        if (terminateAtt == null) {
            terminateAtt = "no";
            terminate = makeAttributeValueTemplate(terminateAtt, -1);
        }

        checkAttributeValue("terminate", terminateAtt, true, StyleElement.YES_NO);

    }

    public void validate(ComponentDeclaration decl) throws XPathException {
        select = typeCheck("select", select);
        terminate = typeCheck("terminate", terminate);
        if (errorCode == null) {
            errorCode = new StringLiteral("Q{http://www.w3.org/2005/xqt-errors}XTMM9000");
        } else {
            errorCode = typeCheck("error-code", errorCode);
        }
    }

    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        Expression b = compileSequenceConstructor(exec, decl, true);
        if (b != null) {
            if (select == null) {
                select = b;
            } else {
                select = Block.makeBlock(select, b);
                select.setLocation(
                    allocateLocation());
            }
        }
        if (timer != null) {
            select = Block.makeBlock(timer, select);
        }
        if (select == null) {
            select = new StringLiteral("xsl:message (no content)");
        }

        if (errorCode instanceof StringLiteral) {
            // resolve any QName prefix now
            String code = ((StringLiteral) errorCode).getStringValue();
            if (code.contains(":") && !code.startsWith("Q{")) {
                try {
                    StructuredQName name = makeQName(code);
                    errorCode = new StringLiteral("Q" + name.getClarkName());
                } catch (NamespaceException err) {
                    errorCode = new StringLiteral("Q{http://www.w3.org/2005/xqt-errors}XTMM9000");
                }
            }
        }
        Message m = new Message(select, terminate, errorCode);
        m.setRetainedStaticContext(makeRetainedStaticContext());
        return m;
    }

}