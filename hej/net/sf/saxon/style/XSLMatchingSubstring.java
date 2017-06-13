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
 * Handler for xsl:matching-substring and xsl:non-matching-substring elements in stylesheet.
 * New at XSLT 2.0<BR>
 */

public class XSLMatchingSubstring extends StyleElement {


    public void prepareAttributes() throws XPathException {
        AttributeCollection atts = getAttributeList();
        for (int a = 0; a < atts.getLength(); a++) {
            checkUnknownAttribute(atts.getNodeName(a));
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
        if (!(getParent() instanceof XSLAnalyzeString)) {
            compileError(getDisplayName() + " must be immediately within xsl:analyze-string", "XTSE0010");
        }
    }

    /*@NotNull*/
    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        throw new UnsupportedOperationException("XSLMatchingSubstring#compile() should not be called");
    }

}

