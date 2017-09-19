////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.StringLiteral;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.expr.parser.TypeChecker;
import net.sf.saxon.lib.FeatureKeys;
import net.sf.saxon.ma.map.MapType;
import net.sf.saxon.om.AttributeCollection;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.value.Whitespace;

/**
 * Handler for xsl:evaluate elements in XSLT 3.0 stylesheet. <br>
 */

public class XSLEvaluate extends StyleElement {

    Expression xpath = null;
    SequenceType requiredType = SequenceType.ANY_SEQUENCE;
    Expression namespaceContext = null;
    Expression contextItem = null;
    Expression baseUri = null;
    Expression schemaAware = null;
    Expression withParams = null;

    /**
     * Determine whether this node is an instruction.
     *
     * @return true - it is an instruction
     */

    public boolean isInstruction() {
        return true;
    }

    /**
     * Specify that xsl:sort is a permitted child
     */

    protected boolean isPermittedChild(StyleElement child) {
        return child instanceof XSLLocalParam;
    }

    /**
     * Determine the type of item returned by this instruction (only relevant if
     * it is an instruction).
     *
     * @return the item type returned
     */

    protected ItemType getReturnedItemType() {
        return AnyItemType.getInstance();
    }

    /**
     * Determine whether this type of element is allowed to contain a sequence constructor
     *
     * @return false: no, it may not contain a sequence constructor
     */

    public boolean mayContainSequenceConstructor() {
        return false;
    }


    public void prepareAttributes() throws XPathException {

        AttributeCollection atts = getAttributeList();

        String xpathAtt = null;
        String asAtt = null;
        String contextItemAtt = null;
        String baseUriAtt = null;
        String namespaceContextAtt = null;
        String schemaAwareAtt = null;
        String withParamsAtt = null;

        for (int a = 0; a < atts.getLength(); a++) {
            String f = atts.getQName(a);
            if (f.equals("xpath")) {
                xpathAtt = atts.getValue(a);
                xpath = makeExpression(xpathAtt, a);
            } else if (f.equals("as")) {
                asAtt = atts.getValue(a);
            } else if (f.equals("context-item")) {
                contextItemAtt = atts.getValue(a);
                contextItem = makeExpression(contextItemAtt, a);
            } else if (f.equals("base-uri")) {
                baseUriAtt = atts.getValue(a);
                baseUri = makeAttributeValueTemplate(baseUriAtt, a);
            } else if (f.equals("namespace-context")) {
                namespaceContextAtt = atts.getValue(a);
                namespaceContext = makeExpression(namespaceContextAtt, a);
            } else if (f.equals("schema-aware")) {
                schemaAwareAtt = Whitespace.trim(atts.getValue(a));
                schemaAware = makeAttributeValueTemplate(schemaAwareAtt, a);
            } else if (f.equals("with-params")) {
                withParamsAtt = atts.getValue(a);
                withParams = makeExpression(withParamsAtt, a);
            } else {
                checkUnknownAttribute(atts.getNodeName(a));
            }
        }

        if (xpathAtt == null) {
            reportAbsence("xpath");
        }

        if (asAtt != null) {
            requiredType = makeSequenceType(asAtt);
            try {
                requiredType = makeSequenceType(asAtt);
            } catch (XPathException e) {
                compileErrorInAttribute(e.getMessage(), e.getErrorCodeLocalPart(), "as");
            }
        }

        if (contextItemAtt == null) {
            contextItem = Literal.makeEmptySequence();
        }

        if (schemaAwareAtt == null) {
            schemaAware = StringLiteral.makeLiteral(new StringValue("no"));
        } else if (schemaAware instanceof StringLiteral) {
            checkAttributeValue("schema-aware", schemaAwareAtt, true, StyleElement.YES_NO);
        }

        if (withParamsAtt == null) {
            withParamsAtt = "map{}";
            withParams = makeExpression(withParamsAtt, -1);
        }
    }


    public void validate(ComponentDeclaration decl) throws XPathException {
        getContainingPackage().setRetainUnusedFunctions();
        xpath = typeCheck("select", xpath);
        baseUri = typeCheck("base-uri", baseUri);
        contextItem = typeCheck("context-item", contextItem);
        namespaceContext = typeCheck("namespace-context", namespaceContext);
        schemaAware = typeCheck("schema-aware", schemaAware);
        withParams = typeCheck("with-params", withParams);
        AxisIterator kids = iterateAxis(AxisInfo.CHILD);
        NodeInfo child;
        while ((child = kids.next()) != null) {
            if (child instanceof XSLWithParam || child instanceof XSLFallback) {
                // OK
            } else if (child.getNodeKind() == Type.TEXT) {
                // with xml:space=preserve, white space nodes may still be there
                if (!Whitespace.isWhite(child.getStringValueCS())) {
                    compileError("No character data is allowed within xsl:evaluate", "XTSE0010");
                }
            } else {
                compileError("Child element " + Err.wrap(child.getDisplayName(), Err.ELEMENT) +
                        " is not allowed as a child of xsl:evaluate", "XTSE0010");
            }
        }
        try {
            ExpressionVisitor visitor = makeExpressionVisitor();
            TypeChecker tc = getConfiguration().getTypeChecker(false);
            RoleDiagnostic role = new RoleDiagnostic(RoleDiagnostic.INSTRUCTION, "xsl:evaluate/xpath", 0);
            xpath = tc.staticTypeCheck(xpath, SequenceType.SINGLE_STRING, role, visitor);

            role = new RoleDiagnostic(RoleDiagnostic.INSTRUCTION, "xsl:evaluate/context-item", 0);
            role.setErrorCode("XTTE3210");
            contextItem = tc.staticTypeCheck(contextItem, SequenceType.OPTIONAL_ITEM, role, visitor);

            role = new RoleDiagnostic(RoleDiagnostic.INSTRUCTION, "xsl:evaluate/namespace-context", 0);
            role.setErrorCode("XTTE3170");
            if (namespaceContext != null) {
                namespaceContext = tc.staticTypeCheck(namespaceContext, SequenceType.SINGLE_NODE, role, visitor);
            }

            role = new RoleDiagnostic(RoleDiagnostic.INSTRUCTION, "xsl:evaluate/with-params", 0);
            role.setErrorCode("XTTE3170");
            withParams = tc.staticTypeCheck(withParams,
                                            SequenceType.makeSequenceType(MapType.ANY_MAP_TYPE, StaticProperty.EXACTLY_ONE), role, visitor);

        } catch (XPathException err) {
            compileError(err);
        }
    }

    public Expression getTargetExpression() {
        return xpath;
    }

    public Expression getContextItemExpression() {
        return contextItem;
    }

    public Expression getBaseUriExpression() {
        return baseUri;
    }

    public Expression getNamespaceContextExpression() {
        return namespaceContext;
    }

    public Expression getSchemaAwareExpression() {
        return schemaAware;
    }

    public Expression getWithParamsExpression() {
        return withParams;
    }

    public SequenceType getRequiredType() {
        return requiredType;
    }

    /*@Nullable*/
    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        if (getConfiguration().getBooleanProperty(FeatureKeys.DISABLE_XSL_EVALUATE)) {
            // If xsl:evaluate is statically disabled then we should execute any fallback children
            validationError = new XPathException("xsl:evaluate is not available in this configuration", "XTDE3175");
            return fallbackProcessing(exec, decl, this);
        } else {
            return getConfiguration().makeEvaluateInstruction(this, decl);
        }
    }


}
