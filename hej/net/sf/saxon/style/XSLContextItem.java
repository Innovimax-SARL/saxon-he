////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.om.AttributeCollection;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.Whitespace;


/**
 * An xsl:context-item element in the stylesheet. <br>
 */

// TODO: use the type information to improve static type checking of the template body.

public class XSLContextItem extends StyleElement {

    private ItemType requiredType = AnyItemType.getInstance();
    private boolean mayBeOmitted = true;
    //private boolean mayBeSupplied = true;
    private boolean absentFocus = false;


    public void prepareAttributes() throws XPathException {

        String asAtt = null;
        String useAtt = null;

        AttributeCollection atts = getAttributeList();

        for (int a = 0; a < atts.getLength(); a++) {
            String f = atts.getQName(a);
            if (f.equals("as")) {
                asAtt = Whitespace.trim(atts.getValue(a));
            } else if (f.equals("use")) {
                useAtt = Whitespace.trim(atts.getValue(a));
            } else {
                checkUnknownAttribute(atts.getNodeName(a));
            }
        }
        if (asAtt != null) {
            net.sf.saxon.value.SequenceType st = makeSequenceType(asAtt);
            if (st.getCardinality() != StaticProperty.EXACTLY_ONE) {
                compileError("The xsl:context-item/@use attribute must be an item type (no occurrence indicator allowed)", "XTSE0020");
                return;
            }
            requiredType = st.getPrimaryType();
        }
        if (useAtt != null) {
            if (useAtt.equals("required")) {
                mayBeOmitted = false;
            } else if (useAtt.equals("optional")) {
                // no action, this is the default
            } else if (useAtt.equals("absent")) {
                absentFocus = true;
            } else {
                invalidAttribute("use", "required|optional|absent");
            }
        }
        if (asAtt != null && absentFocus) {
            compileError("The 'as' attribute must be omitted when use='absent' is specified", "XTSE3089");
        }
    }

    /**
     * Check that the stylesheet element is valid. This is called once for each element, after
     * the entire tree has been built. As well as validation, it can perform first-time
     * initialisation. The default implementation does nothing; it is normally overriden
     * in subclasses.
     *
     * @param decl the declaration to be validated
     * @throws XPathException if any error is found during validation
     */

    public void validate(ComponentDeclaration decl) throws XPathException {
        if (!(getParent() instanceof XSLTemplate)) {
            compileError("xsl:context-item can appear only as a child of xsl:template");
            return;
        }
        if (mayBeOmitted && ((XSLTemplate) getParent()).getTemplateName() == null) {
            compileError("xsl:context-item appearing in an xsl:template declaration with no name attribute must specify use=required",
                "XTSE0020");
        }
        ((XSLTemplate)getParent()).setContextItemRequirements(requiredType, mayBeOmitted, absentFocus);
        AxisIterator precSib = iterateAxis(AxisInfo.PRECEDING_SIBLING);
        NodeInfo prec;
        while ((prec = precSib.next()) != null) {
            if (prec.getNodeKind() != Type.TEXT || !Whitespace.isWhite(prec.getStringValueCS())) {
                compileError("xsl:context-item must be the first child of xsl:template");
            } else {
                // OK, whitespace text nodes allowed
            }
        }
    }

    public ItemType getRequiredContextItemType() {
        return requiredType;
    }

    public boolean isMayBeOmitted() {
        return mayBeOmitted;
    }

    public boolean isAbsentFocus() {
        return absentFocus;
    }


}
