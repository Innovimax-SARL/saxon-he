////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.om.AttributeCollection;
import net.sf.saxon.om.NamespaceBinding;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Whitespace;


/**
 * An xsl:namespace-alias element in the stylesheet. <br>
 */

public class XSLNamespaceAlias extends StyleElement {

    private String stylesheetURI;
    private NamespaceBinding resultNamespaceBinding;

    /**
     * Ask whether this node is a declaration, that is, a permitted child of xsl:stylesheet
     * (including xsl:include and xsl:import).
     *
     * @return true for this element
     */

    @Override
    public boolean isDeclaration() {
        return true;
    }

    public void prepareAttributes() throws XPathException {

        String stylesheetPrefix = null;
        String resultPrefix = null;

        AttributeCollection atts = getAttributeList();

        for (int a = 0; a < atts.getLength(); a++) {
            String f = atts.getQName(a);
            if (f.equals("stylesheet-prefix")) {
                stylesheetPrefix = Whitespace.trim(atts.getValue(a));
            } else if (f.equals("result-prefix")) {
                resultPrefix = Whitespace.trim(atts.getValue(a));
            } else {
                checkUnknownAttribute(atts.getNodeName(a));
            }
        }
        if (stylesheetPrefix == null) {
            reportAbsence("stylesheet-prefix");
            return;
        }
        if (stylesheetPrefix.equals("#default")) {
            stylesheetPrefix = "";
        }
        if (resultPrefix == null) {
            reportAbsence("result-prefix");
            return;
        }
        if (resultPrefix.equals("#default")) {
            resultPrefix = "";
        }
        stylesheetURI = getURIForPrefix(stylesheetPrefix, true);
        if (stylesheetURI == null) {
            compileError("stylesheet-prefix " + stylesheetPrefix + " has not been declared", "XTSE0812");
            // recovery action
            stylesheetURI = "";
            resultNamespaceBinding = NamespaceBinding.DEFAULT_UNDECLARATION;
            return;
        }
        String resultURI = getURIForPrefix(resultPrefix, true);
        if (resultURI == null) {
            compileError("result-prefix " + resultPrefix + " has not been declared", "XTSE0812");
            // recovery action
            stylesheetURI = "";
            resultURI = "";
        }
        resultNamespaceBinding = new NamespaceBinding(resultPrefix, resultURI);
    }

    public void validate(ComponentDeclaration decl) throws XPathException {
        checkTopLevel("XTSE0010", false);
    }

    /*@Nullable*/
    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        return null;
    }

    public void index(ComponentDeclaration decl, PrincipalStylesheetModule top) throws XPathException {
        top.addNamespaceAlias(decl);
    }

    public String getStylesheetURI() {
        return stylesheetURI;
    }

    public NamespaceBinding getResultNamespaceBinding() {
        return resultNamespaceBinding;
    }

}

