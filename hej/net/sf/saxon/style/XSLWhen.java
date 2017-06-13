////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.trans.XPathException;


/**
 * Handler for xsl:when elements in stylesheet. <br>
 * The xsl:while element has a mandatory attribute test, a boolean expression.
 */

public class XSLWhen extends StyleElement {

    private Expression test;

    public Expression getCondition() {
        return test;
    }


    public void prepareAttributes() throws XPathException {
        test = XSLIf.prepareTestAttribute(this);
        if (test == null) {
            reportAbsence("test");
        }
    }

    /**
     * Determine whether this type of element is allowed to contain a template-body
     *
     * @return true: yes, it may contain a template-body
     */

    public boolean mayContainSequenceConstructor() {
        return true;
    }

    public void validate(ComponentDeclaration decl) throws XPathException {
        if (!(getParent() instanceof XSLChoose)) {
            compileError("xsl:when must be immediately within xsl:choose", "XTSE0010");
        }
        test = typeCheck("test", test);
    }

    /**
     * Mark tail-recursive calls on stylesheet functions. For most instructions, this does nothing.
     */

    public boolean markTailCalls() {
        StyleElement last = getLastChildInstruction();
        return last != null && last.markTailCalls();
    }

    /*@Nullable*/
    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        return null;
        // compilation is handled from the xsl:choose element
    }

}