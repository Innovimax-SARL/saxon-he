////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.StringLiteral;
import net.sf.saxon.expr.instruct.Block;
import net.sf.saxon.expr.instruct.ValueOf;
import net.sf.saxon.om.AttributeCollection;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.Item;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.linked.TextImpl;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.value.Whitespace;

import java.util.ArrayList;
import java.util.List;

/**
 * Handler for xsl:text elements in stylesheet. <BR>
 */

public class XSLText extends XSLLeafNodeConstructor {

    private boolean disable = false;
    private StringValue value;


    public void prepareAttributes() throws XPathException {

        String disableAtt = null;

        AttributeCollection atts = getAttributeList();

        for (int a = 0; a < atts.getLength(); a++) {
            String f = atts.getQName(a);
            if (f.equals("disable-output-escaping")) {
                disableAtt = Whitespace.trim(atts.getValue(a));
            } else {
                checkUnknownAttribute(atts.getNodeName(a));
            }
        }

        if (disableAtt != null) {
            disable = processBooleanAttribute("disable-output-escaping", disableAtt);
        }
    }

    public void validate(ComponentDeclaration decl) throws XPathException {

        AxisIterator kids = iterateAxis(AxisInfo.CHILD);
        value = StringValue.EMPTY_STRING;
        Item child;
        while ((child = kids.next()) != null) {
            if (child instanceof StyleElement) {
                ((StyleElement) child).compileError("xsl:text must not contain child elements", "XTSE0010");
                return;
            } else {
                value = StringValue.makeStringValue(child.getStringValueCS());
                //continue;
            }
        }
        super.validate(decl);
    }

    /**
     * Get the error code to be returned when the element has a select attribute but is not empty.
     *
     * @return the error code defined for this condition, for this particular instruction
     */

    /*@Nullable*/
    protected String getErrorCodeForSelectPlusContent() {
        return null;     // not applicable
    }

    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        if (isExpandingText()) {
            TextImpl child = (TextImpl)iterateAxis(AxisInfo.CHILD).next();
            if (child != null) {
                List<Expression> contents = new ArrayList<Expression>(10);
                compileContentValueTemplate(child, contents);
                Expression block = Block.makeBlock(contents);
                block.setLocation(allocateLocation());
                return block;
            } else {
                return new ValueOf(new StringLiteral(StringValue.EMPTY_STRING), disable, false);
            }
        } else {
            return new ValueOf(Literal.makeLiteral(value), disable, false);
        }
    }

}

