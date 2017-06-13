////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.om.AttributeCollection;
import net.sf.saxon.trans.XPathException;

/**
 * xsl:fallback element in stylesheet. <br>
 */

public class XSLFallback extends StyleElement {

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

    /**
     * Ask whether variables declared in an "uncle" element are visible.
     *
     * @return true for all elements except xsl:fallback and saxon:catch
     */

    protected boolean seesAvuncularVariables() {
        return false;
    }

    public void prepareAttributes() throws XPathException {
        AttributeCollection atts = getAttributeList();
        for (int a = 0; a < atts.getLength(); a++) {
            checkUnknownAttribute(atts.getNodeName(a));
        }
    }

    public void validate(ComponentDeclaration decl) throws XPathException {
        // Parent elements are now responsible for validating their children
//        StyleElement parent = (StyleElement)getParent();
//        if (!parent.mayContainFallback()) {
//            compileError("xsl:fallback is not allowed as a child of " + parent.getDisplayName(), "XT0010");
//        }
    }

    /*@Nullable*/
    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        // if we get here, then the parent instruction is OK, so the fallback is not activated
        return null;
    }

}

