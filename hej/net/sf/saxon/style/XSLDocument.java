////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.instruct.DocumentInstr;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.AttributeCollection;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.value.Whitespace;

/**
 * An xsl:document instruction in the stylesheet. <BR>
 * This instruction creates a document node in the result tree, optionally
 * validating it.
 */

public class XSLDocument extends StyleElement {

    private int validationAction = Validation.STRIP;
    /*@Nullable*/ private SchemaType schemaType = null;

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
        AttributeCollection atts = getAttributeList();

        String validationAtt = null;
        String typeAtt = null;

        for (int a = 0; a < atts.getLength(); a++) {
            String f = atts.getQName(a);
            if (f.equals("validation")) {
                validationAtt = Whitespace.trim(atts.getValue(a));
            } else if (f.equals("type")) {
                typeAtt = Whitespace.trim(atts.getValue(a));
            } else {
                checkUnknownAttribute(atts.getNodeName(a));
            }
        }

        if (validationAtt == null) {
            validationAction = getDefaultValidation();
        } else {
            validationAction = validateValidationAttribute(validationAtt);
        }
        if (typeAtt != null) {
            if (!isSchemaAware()) {
                compileError("The @type attribute is available only with a schema-aware XSLT processor", "XTSE1660");
            }
            schemaType = getSchemaType(typeAtt);
            validationAction = Validation.BY_TYPE;
        }

        if (typeAtt != null && validationAtt != null) {
            compileError("The @validation and @type attributes are mutually exclusive", "XTSE1505");
        }
    }

    public void validate(ComponentDeclaration decl) throws XPathException {
        //
    }

    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {

        DocumentInstr inst = new DocumentInstr(false, null);
        inst.setValidationAction(validationAction, schemaType);
        Expression b = compileSequenceConstructor(exec, decl, true);
        if (b == null) {
            b = Literal.makeEmptySequence();
        }
        inst.setContentExpression(b);
        return inst;
    }

}

