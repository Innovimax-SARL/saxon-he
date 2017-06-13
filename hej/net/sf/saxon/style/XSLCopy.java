////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.instruct.*;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.AttributeCollection;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.Whitespace;

/**
 * Handler for xsl:copy elements in stylesheet. <br>
 */

public class XSLCopy extends StyleElement {

    private String use;                     // value of use-attribute-sets attribute
    private StructuredQName[] attributeSets = null;
    private boolean copyNamespaces = true;
    private boolean inheritNamespaces = true;
    private int validationAction = Validation.PRESERVE;
    private SchemaType schemaType = null;
    private Expression select = null;
    private boolean selectSpecified = false;

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
        String copyNamespacesAtt = null;
        String validationAtt = null;
        String typeAtt = null;
        String inheritAtt = null;
        String selectAtt = null;

        for (int a = 0; a < atts.getLength(); a++) {
            String f = atts.getQName(a);
            if (f.equals("use-attribute-sets")) {
                use = atts.getValue(a);
            } else if (f.equals("copy-namespaces")) {
                copyNamespacesAtt = Whitespace.trim(atts.getValue(a));
            } else if (f.equals("select")) {
                selectAtt = atts.getValue(a);
            } else if (f.equals("type")) {
                typeAtt = Whitespace.trim(atts.getValue(a));
            } else if (f.equals("validation")) {
                validationAtt = Whitespace.trim(atts.getValue(a));
            } else if (f.equals("inherit-namespaces")) {
                inheritAtt = Whitespace.trim(atts.getValue(a));
            } else {
                checkUnknownAttribute(atts.getNodeName(a));
            }
        }

        if (copyNamespacesAtt != null) {
            copyNamespaces = processBooleanAttribute("copy-namespaces", copyNamespacesAtt);
        }

        if (typeAtt != null && validationAtt != null) {
            compileError("The type and validation attributes must not both be specified", "XTSE1505");
        }

        if (validationAtt != null) {
            validationAction = validateValidationAttribute(validationAtt);
        } else {
            validationAction = getDefaultValidation();
        }

        if (typeAtt != null) {
            schemaType = getSchemaType(typeAtt);
            if (!isSchemaAware()) {
                compileError("The @type attribute is available only with a schema-aware XSLT processor", "XTSE1660");
            }
            validationAction = Validation.BY_TYPE;
        }
        if (inheritAtt != null) {
            inheritNamespaces = processBooleanAttribute("inherit-namespaces", inheritAtt);
        }

        if (selectAtt != null) {
            select = makeExpression(selectAtt, getAttributeList().getIndex("", "select"));
            selectSpecified = true;
        }

    }

    public void validate(ComponentDeclaration decl) throws XPathException {
        if (use != null) {
            // get the names of referenced attribute sets
            attributeSets = getUsedAttributeSets(use);
        }

        if (select == null) {
            select = new ContextItemExpression();
            select.setLocation(allocateLocation());
        }
    }

    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {

        select = typeCheck("select", select);
        try {
            RoleDiagnostic role = new RoleDiagnostic(RoleDiagnostic.INSTRUCTION, "xsl:copy/select", 0);
            role.setErrorCode("XTTE3180");
            select = getConfiguration().getTypeChecker(false).staticTypeCheck(select, SequenceType.OPTIONAL_ITEM,
                                                                              role, makeExpressionVisitor());
        } catch (XPathException err) {
            compileError(err);
        }

        Expression content = compileSequenceConstructor(exec, decl, true);

        if (attributeSets != null) {
            Expression use = UseAttributeSet.makeUseAttributeSets(attributeSets, this);
            // The use-attribute-sets is ignored unless the context item is an element node. So we
            // wrap the UseAttributeSet instructions in a conditional to perform a run-time test
            Expression condition = new InstanceOfExpression(
                    new ContextItemExpression(),
                    SequenceType.makeSequenceType(NodeKindTest.ELEMENT, StaticProperty.EXACTLY_ONE));
            Expression choice = Choose.makeConditional(condition, use);
            if (content == null) {
                content = choice;
            } else {
                content = Block.makeBlock(choice, content);
                content.setLocation(
                    allocateLocation());
            }
        }

        if (content == null) {
            content = Literal.makeEmptySequence();
        }

        Copy inst = new Copy(//select,
                //selectSpecified,
                copyNamespaces,
                inheritNamespaces,
                schemaType,
                validationAction);

        inst.setContentExpression(content);

        if (selectSpecified) {
            return new ForEach(select, inst);
        } else {
            return inst;
        }
    }

}

