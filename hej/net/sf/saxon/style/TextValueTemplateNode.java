////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.instruct.ValueOf;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.linked.TextImpl;

/**
 * A text node in an XSLT 3.0 stylesheet that may or may not contain a text value template
 */
public class TextValueTemplateNode extends TextImpl {

    private Expression contentExp;
    private TextValueTemplateContext staticContext;

    public TextValueTemplateNode(String value) {
        super(value);
    }

    public Expression getContentExpression() {
        return contentExp;
    }

    public TextValueTemplateContext getStaticContext() {
        if (staticContext == null) {
            staticContext = new TextValueTemplateContext((StyleElement) getParent(), this);
        }
        return staticContext;
    }

    public void validate() throws XPathException {
        contentExp = AttributeValueTemplate.make(getStringValue(), getStaticContext());
        contentExp = new ValueOf(contentExp, false, true);
        contentExp.setRetainedStaticContext(((StyleElement)getParent()).makeRetainedStaticContext());
        contentExp = ((StyleElement)getParent()).typeCheck("tvt", contentExp);
    }
}

