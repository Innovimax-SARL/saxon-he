////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.instruct.CopyOf;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.AttributeCollection;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.value.Whitespace;


/**
 * An xsl:copy-of element in the stylesheet. <br>
 */

public final class XSLCopyOf extends StyleElement {

    /*@Nullable*/ private Expression select;
    private boolean copyNamespaces;
    private boolean copyAccumulators;
    private int validation = Validation.PRESERVE;
    private SchemaType schemaType;

    /**
     * Determine whether this node is an instruction.
     *
     * @return true - it is an instruction
     */

    public boolean isInstruction() {
        return true;
    }

    public void prepareAttributes() throws XPathException {

        AttributeCollection atts = getAttributeList();
        String selectAtt = null;
        String copyNamespacesAtt = null;
        String copyAccumulatorsAtt = null;
        String validationAtt = null;
        String typeAtt = null;

        for (int a = 0; a < atts.getLength(); a++) {
            String f = atts.getQName(a);
            if (f.equals("select")) {
                selectAtt = atts.getValue(a);
                select = makeExpression(selectAtt, a);
            } else if (f.equals("copy-namespaces")) {
                copyNamespacesAtt = Whitespace.trim(atts.getValue(a));
            } else if (f.equals("copy-accumulators")) {
                copyAccumulatorsAtt = Whitespace.trim(atts.getValue(a));
            } else if (f.equals("validation")) {
                validationAtt = Whitespace.trim(atts.getValue(a));
            } else if (f.equals("type")) {
                typeAtt = Whitespace.trim(atts.getValue(a));
            } else if (atts.getLocalName(a).equals("read-once") && atts.getURI(a).equals(NamespaceConstant.SAXON)) {
                compileError("The saxon:read-once attribute is no longer available - use xsl:stream");
            } else {
                checkUnknownAttribute(atts.getNodeName(a));
            }
        }

        if (selectAtt == null) {
            reportAbsence("select");
        }

        if (copyAccumulatorsAtt == null) {
            copyAccumulators = false;
        } else {
            copyAccumulators = processBooleanAttribute("copy-accumulators", copyAccumulatorsAtt);
        }

        if (copyNamespacesAtt == null) {
            copyNamespaces = true;
        } else {
            copyNamespaces = processBooleanAttribute("copy-namespaces", copyNamespacesAtt);
        }

        if (validationAtt != null) {
            validation = validateValidationAttribute(validationAtt);
        } else {
            validation = getDefaultValidation();
        }

        if (typeAtt != null) {
            schemaType = getSchemaType(typeAtt);
            if (!isSchemaAware()) {
                compileError("The @type attribute is available only with a schema-aware XSLT processor", "XTSE1660");
            }
            validation = Validation.BY_TYPE;
        }

        if (typeAtt != null && validationAtt != null) {
            compileError("The @validation and @type attributes are mutually exclusive", "XTSE1505");
        }

    }

    public void validate(ComponentDeclaration decl) throws XPathException {
        checkEmpty();
        select = typeCheck("select", select);
    }

    public Expression compile(Compilation exec, ComponentDeclaration decl) {
        CopyOf inst = new CopyOf(select, copyNamespaces, validation, schemaType, false);
        inst.setCopyAccumulators(copyAccumulators);
        inst.setCopyLineNumbers(exec.getConfiguration().isLineNumbering());
        inst.setSchemaAware(exec.isSchemaAware());
        return inst;
    }

}

