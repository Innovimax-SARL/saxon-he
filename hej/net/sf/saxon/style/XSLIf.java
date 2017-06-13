////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.instruct.Choose;
import net.sf.saxon.om.AttributeCollection;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.BooleanValue;


/**
 * Handler for xsl:if elements in stylesheet. <br>
 * The xsl:if element has a mandatory attribute test, a boolean expression.
 * The content is output if the test condition is true.
 */

public class XSLIf extends StyleElement {

    /*@Nullable*/ private Expression test;

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
        test = prepareTestAttribute(this);
        if (test == null) {
            reportAbsence("test");
        }
    }

    /**
     * Process all the attributes, for an element where the only permitted attribute is "test"
     *
     * @param se the containing element
     * @return the expression represented by the test attribute, or null if the attribute is absent
     * @throws XPathException if an error is encountered
     */

    public static Expression prepareTestAttribute(StyleElement se) throws XPathException {

        String testAtt = null;

        AttributeCollection atts = se.getAttributeList();

        for (int a = 0; a < atts.getLength(); a++) {
            String f = atts.getQName(a);
            if (f.equals("test")) {
                testAtt = atts.getValue(a);
            } else {
                se.checkUnknownAttribute(atts.getNodeName(a));
            }
        }

        if (testAtt == null) {
            return null;
        } else {
            try {
                return se.makeExpression(testAtt, atts.getIndex("", "test"));
            } catch (XPathException err) {
                // already reported: prevent further errors
                return Literal.makeLiteral(BooleanValue.FALSE);
            }
        }
    }

    public void validate(ComponentDeclaration decl) throws XPathException {
        test = typeCheck("test", test);
    }

    /**
     * Mark tail-recursive calls on stylesheet functions. For most instructions, this does nothing.
     */

    public boolean markTailCalls() {
        StyleElement last = getLastChildInstruction();
        return last != null && last.markTailCalls();
    }

    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        if (test instanceof Literal) {
            GroundedValue testVal = ((Literal) test).getValue();
            // condition known statically, so we only need compile the code if true.
            // This can happen with expressions such as test="function-available('abc')".
            try {
                if (testVal.effectiveBooleanValue()) {
                    return compileSequenceConstructor(exec, decl, true);
                } else {
                    return null;
                }
            } catch (XPathException err) {
                // fall through to non-optimizing case
            }
        }

        Expression action = compileSequenceConstructor(exec, decl, true);
        if (action == null) {
            return null;
        }
        Expression[] conditions = {test};
        Expression[] actions = {action};

        Choose choose = new Choose(conditions, actions);
        choose.setInstruction(true);
        return choose;
    }


}