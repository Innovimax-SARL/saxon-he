////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.sql;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.om.AttributeCollection;
import net.sf.saxon.style.Compilation;
import net.sf.saxon.style.ComponentDeclaration;
import net.sf.saxon.style.StyleElement;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.Whitespace;


/**
 * An sql:column element in the stylesheet.
 */

public class SQLColumn extends StyleElement {

    private String name;
    private Expression select;

    /**
     * Determine whether this node is an instruction.
     *
     * @return false - it is not an instruction
     */

    public boolean isInstruction() {
        return false;
    }

    /**
     * Determine whether this type of element is allowed to contain a template-body
     *
     * @return false: no, it may not contain a template-body
     */

    public boolean mayContainSequenceConstructor() {
        return false;
    }

    protected boolean mayContainFallback() {
        return false;
    }

    public void prepareAttributes() throws XPathException {

        AttributeCollection atts = getAttributeList();

        String selectAtt = null;
        String nameAtt = null;

        for (int a = 0; a < atts.getLength(); a++) {
            String name = atts.getQName(a);
            if (name.equals("name")) {
                nameAtt = Whitespace.trim(atts.getValue(a));
            } else if (name.equals("select")) {
                selectAtt = atts.getValue(a);
                select = makeExpression(selectAtt, a);
            } else {
                checkUnknownAttribute(atts.getNodeName(a));
            }
        }

        if (nameAtt == null) {
            reportAbsence("name");
            name = "saxon-dummy-column";
        } else {
            name = SQLConnect.quoteSqlName(nameAtt);
        }

    }


    public void validate(ComponentDeclaration decl) throws XPathException {
        select = typeCheck("select", select);
        try {
            RoleDiagnostic role =
                    new RoleDiagnostic(RoleDiagnostic.INSTRUCTION, "sql:column/select", 0);
            select = getConfiguration().getTypeChecker(false).staticTypeCheck(select,
                    SequenceType.SINGLE_ATOMIC,
                                                                              role, makeExpressionVisitor());

        } catch (XPathException err) {
            compileError(err);
        }
    }

    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        return select;
    }

    public String getColumnName() {
        return Navigator.getAttributeValue(this, "", "name");
    }


}

