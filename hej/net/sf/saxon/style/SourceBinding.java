////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.instruct.DocumentInstr;
import net.sf.saxon.expr.instruct.GlobalVariable;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.trans.Visibility;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.AttributeLocation;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.type.UType;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.value.Whitespace;

import java.util.ArrayList;
import java.util.List;


/**
 * Helper class for xsl:variable and xsl:param elements. <br>
 */

public class SourceBinding {

    private StyleElement sourceElement;
    private StructuredQName name;
    private Expression select = null;
    private SequenceType declaredType = null;
    private SequenceType inferredType = null;
    protected String constantText = null;
    protected SlotManager slotManager = null;  // used only for global variable declarations
    private Visibility visibility;
    private GroundedValue constantValue = null;

    private int properties;

    public static final int PRIVATE = 1;
    public static final int GLOBAL = 2;
    public static final int PARAM = 4;
    public static final int TUNNEL = 8;
    public static final int REQUIRED = 16;
    public static final int IMPLICITLY_REQUIRED = 32;
    public static final int ASSIGNABLE = 64;
    public static final int SELECT = 128;
    public static final int AS = 256;
    public static final int DISALLOWS_CONTENT = 512;
    public static final int STATIC = 2048;
    public static final int VISIBILITY = 4096;
    public static final int IMPLICITLY_DECLARED = 8192;

    // List of VariableReference objects that reference this XSLVariableDeclaration
    private List<BindingReference> references = new ArrayList<BindingReference>(10);

    public SourceBinding(StyleElement sourceElement) {
        this.sourceElement = sourceElement;
    }


    public void prepareAttributes(int permittedAttributes) throws XPathException {

        AttributeCollection atts = sourceElement.getAttributeList();

        String selectAtt = null;
        String asAtt = null;
        String requiredAtt = null;
        String tunnelAtt = null;
        String assignableAtt = null;
        String staticAtt = null;
        String visibilityAtt = null;

        for (int a = 0; a < atts.getLength(); a++) {
            String f = atts.getQName(a);
            if (f.equals("name")) {
                if (name == null || name.equals(errorName())) {
                    processVariableName(atts.getValue(a));
                }
            } else if (f.equals("select")) {
                if ((permittedAttributes & SELECT) != 0) {
                    selectAtt = atts.getValue(a);
                } else {
                    sourceElement.compileErrorInAttribute(
                            "The select attribute is not permitted on a function parameter", "XTSE0760", "select");
                }
            } else if (f.equals("as") && ((permittedAttributes & AS) != 0)) {
                asAtt = atts.getValue(a);
            } else if (f.equals("required") && ((permittedAttributes & REQUIRED) != 0)) {
                requiredAtt = Whitespace.trim(atts.getValue(a));
            } else if (f.equals("tunnel")) {
                tunnelAtt = Whitespace.trim(atts.getValue(a));
            } else if (f.equals("static") && ((permittedAttributes & STATIC) != 0)) {
                staticAtt = Whitespace.trim(atts.getValue(a));
            } else if (f.equals("visibility") && ((permittedAttributes & VISIBILITY) != 0)) {
                visibilityAtt = Whitespace.trim(atts.getValue(a));
            } else if (atts.getLocalName(a).equals("assignable") && NamespaceConstant.SAXON.equals(atts.getURI(a)) && ((permittedAttributes & ASSIGNABLE) != 0)) {
                assignableAtt = Whitespace.trim(atts.getValue(a));
            } else {
                sourceElement.checkUnknownAttribute(atts.getNodeName(a));
            }
        }

        if (name == null) {
            sourceElement.reportAbsence("name");
            name = errorName();
        }

        if (selectAtt != null) {
            select = sourceElement.makeExpression(selectAtt, sourceElement.getAttributeList().getIndex("", "select"));
        }

        if (requiredAtt != null) {
            boolean required = sourceElement.processBooleanAttribute("required", requiredAtt);
            setProperty(REQUIRED, required);
            if (required && select != null) {
                sourceElement.compileError("xsl:param: cannot supply a default value when required='yes'");
            }
        }

        if (tunnelAtt != null) {
            boolean tunnel = sourceElement.processBooleanAttribute("tunnel", tunnelAtt);
            if (tunnel && ((permittedAttributes & TUNNEL) == 0)) {
                sourceElement.compileErrorInAttribute("The only permitted value of the 'tunnel' attribute is 'no'", "XTSE0020", "tunnel");
            }
            setProperty(TUNNEL, tunnel);
        }

        if (assignableAtt != null) {
            boolean assignable = sourceElement.processBooleanAttribute("saxon:assignable", assignableAtt);
            setProperty(ASSIGNABLE, assignable);
        }

        if (staticAtt != null) {
            boolean statick = sourceElement.processBooleanAttribute("static", staticAtt);
            setProperty(STATIC, statick);
            if (statick) {
                setProperty(SourceBinding.DISALLOWS_CONTENT, true);
            }
            if (statick && !hasProperty(GLOBAL)) {
                sourceElement.compileErrorInAttribute("Only global declarations can be static", "XTSE0020", "static");
            }
        }

        if (asAtt != null) {
            try {
                declaredType = sourceElement.makeSequenceType(asAtt);
            } catch (XPathException e) {
                sourceElement.compileErrorInAttribute(e.getMessage(), e.getErrorCodeLocalPart(), "as");
            }
        }

        if (visibilityAtt != null) {
            if (hasProperty(PARAM)) {
                sourceElement.compileErrorInAttribute("The visibility attribute is not allowed on xsl:param", "XTSE0020", "visibility");
            } else {
                sourceElement.check30attribute("visibility");
                visibility = sourceElement.interpretVisibilityValue(visibilityAtt, "");
            }
            if (!hasProperty(GLOBAL)) {
                sourceElement.compileErrorInAttribute("The visibility attribute is allowed only on global declarations", "XTSE0020", "visibility");
            }
        }

        if (hasProperty(STATIC) && visibility != Visibility.PRIVATE && visibilityAtt != null) {
            sourceElement.compileErrorInAttribute("A static variable or parameter must be private", "XTSE0020", "static");
        }
    }

    /**
     * Get the declaration in the stylesheet
     *
     * @return the node in the stylesheet containing this variable binding
     */

    public StyleElement getSourceElement() {
        return sourceElement;
    }

    /**
     * Set the name of the variable
     *
     * @param name the name of the variable as a QName
     */

    public void setVariableQName(StructuredQName name) {
        this.name = name;
    }

    /**
     * Set the declared type of the variable
     *
     * @param declaredType the declared type
     */

    public void setDeclaredType(SequenceType declaredType) {
        this.declaredType = declaredType;
    }

    /**
     * Process the QName of the variable. Validate the name and place it in the "name" field;
     * if invalid, construct an error message and place a dummy name in the "name" field for
     * recovery purposes.
     *
     * @param nameAttribute the lexical QName
     * @throws XPathException if the name is invalid
     */

    private void processVariableName(String nameAttribute) throws XPathException {
        try {
            if (nameAttribute != null) {
                name = sourceElement.makeQName(nameAttribute);
            }
        } catch (NamespaceException err) {
            name = errorName();
            StructuredQName n = StructuredQName.fromClarkName("name");
            throw new XPathException("Invalid variable name: " + err.getMessage(), "XTSE0020", new AttributeLocation(sourceElement, n));
        } catch (XPathException err) {
            name = errorName();
            StructuredQName n = StructuredQName.fromClarkName("name");
            throw new XPathException("Invalid variable name: " + err.getMessage() +
                    (nameAttribute.startsWith("$") ? " (No '$' sign needed)" : ""), "XTSE0020",
                    new AttributeLocation(sourceElement, n));
        }
    }

    /**
     * Fallback name to be returned if there is anything wrong with the variable's real name
     * (after reporting an error)
     *
     * @return a fallback name
     */

    private StructuredQName errorName() {
        return new StructuredQName("saxon", NamespaceConstant.SAXON, "error-variable-name");
    }

    /**
     * Validate the declaration
     *
     * @throws XPathException if the declaration is invalid
     */

    public void validate() throws XPathException {
        if (select != null && sourceElement.hasChildNodes()) {
            sourceElement.compileError("An " + sourceElement.getDisplayName() + " element with a select attribute must be empty", "XTSE0620");
        }
        if (hasProperty(DISALLOWS_CONTENT) && sourceElement.hasChildNodes()) {
            if (isStatic()) {
                sourceElement.compileError("A static variable or parameter must have no content", "XTSE0010");
            } else {
                sourceElement.compileError("Within xsl:function, an xsl:param element must have no content", "XTSE0620");
            }
        }
        if (visibility == Visibility.ABSTRACT && (select != null || sourceElement.hasChildNodes())) {
            sourceElement.compileError("An abstract variable must have no select attribute and no content", "XTSE0620");
        }
    }

    /**
     * Hook to allow additional validation of a parent element immediately after its
     * children have been validated.
     *
     * @throws XPathException if the declaration is invalid
     */

    public void postValidate() throws XPathException {
        checkAgainstRequiredType(declaredType);

        if (select == null && !hasProperty(DISALLOWS_CONTENT) && visibility != Visibility.ABSTRACT) {
            AxisIterator kids = sourceElement.iterateAxis(AxisInfo.CHILD);
            NodeInfo first = kids.next();
            if (first == null) {
                if (declaredType == null) {
                    select = new StringLiteral(StringValue.EMPTY_STRING);
                    select.setRetainedStaticContext(sourceElement.makeRetainedStaticContext());
                } else {
                    if (sourceElement instanceof XSLLocalParam || sourceElement instanceof XSLGlobalParam) {
                        if (!hasProperty(REQUIRED)) {
                            if (Cardinality.allowsZero(declaredType.getCardinality())) {
                                select = Literal.makeEmptySequence();
                            } else {
                                // The implicit default value () is not valid for the required type, so
                                // it is treated as if there is no default
                                setProperty(IMPLICITLY_REQUIRED, true);
                            }
                        }
                    } else {
                        if (Cardinality.allowsZero(declaredType.getCardinality())) {
                            select = Literal.makeEmptySequence();
                            select.setRetainedStaticContext(sourceElement.makeRetainedStaticContext());
                        } else {
                            sourceElement.compileError("The implicit value () is not valid for the declared type", "XTTE0570");
                        }
                    }
                }
            } else {
                if (kids.next() == null) {
                    // there is exactly one child node
                    if (first.getNodeKind() == Type.TEXT) {
                        // it is a text node: optimize for this case
                        constantText = first.getStringValue();
                    }
                }

            }
        }
        select = sourceElement.typeCheck("select", select);
    }

    public boolean isStatic() {
        return hasProperty(STATIC);
    }

    /**
     * Check the supplied select expression against the required type.
     *
     * @param required The type required by the variable declaration, or in the case
     *                 of xsl:with-param, the signature of the called template
     * @throws net.sf.saxon.trans.XPathException if an error is detected
     */

    public void checkAgainstRequiredType(SequenceType required)
            throws XPathException {
        if (visibility != Visibility.ABSTRACT) {
            try {
                if (required != null) {
                    // check that the expression is consistent with the required type
                    if (select != null) {
                        int category = RoleDiagnostic.VARIABLE;
                        String errorCode = "XTTE0570";
                        if (sourceElement instanceof XSLLocalParam) {
                            category = RoleDiagnostic.PARAM;
                            errorCode = "XTTE0600";
                        } else if (sourceElement instanceof XSLWithParam || sourceElement instanceof XSLGlobalParam) {
                            category = RoleDiagnostic.PARAM;
                            errorCode = "XTTE0590";
                        }
                        RoleDiagnostic role = new RoleDiagnostic(category, name.getDisplayName(), 0);
                        //role.setSourceLocator(new ExpressionLocation(this));
                        role.setErrorCode(errorCode);
                        select = sourceElement.getConfiguration().getTypeChecker(false).staticTypeCheck(select, required, role, sourceElement.makeExpressionVisitor());
                    } else {
                        // do the check later
                    }
                }
            } catch (XPathException err) {
                err.setLocator(sourceElement);   // because the expression wasn't yet linked into the module
                sourceElement.compileError(err);
                select = new ErrorExpression(err);
            }
        }
    }


    /**
     * Get the name of the variable
     *
     * @return the variable's name
     */

    public StructuredQName getVariableQName() {
        if (name == null) {
            try {
                processVariableName(sourceElement.getAttributeValue("", "name"));
            } catch (XPathException e) {
                return errorName();
            }
        }
        return name;
    }

    /**
     * Set a boolean property of the variable
     *
     * @param prop the property to be set (e.g. {@link #TUNNEL})
     * @param flag true to set the property on, false to set it off
     */

    public void setProperty(int prop, boolean flag) {
        if (flag) {
            properties |= prop;
        } else {
            properties &= ~prop;
        }
    }

    /**
     * Get a boolean property of the variable
     *
     * @param prop the property whose value is required
     * @return true if the variable has the specified property, otherwise false
     */

    public boolean hasProperty(int prop) {
        return (properties & prop) != 0;
    }

    /**
     * Get all the known references to this variable
     *
     * @return the list of references
     */

    public List<BindingReference> getReferences() {
        return references;
    }

    /**
     * Get the SlotManager associated with this stylesheet construct. The SlotManager contains the
     * information needed to manage the local stack frames used by run-time instances of the code.
     *
     * @return the associated SlotManager object
     */

    public SlotManager getSlotManager() {
        return slotManager;
    }

    /**
     * If the element contains a sequence constructor, convert this to an expression and assign it to the
     * select attribute
     *
     * @param compilation the compilation episode
     * @param decl        the declaration being compiled
     * @throws XPathException if a static error is found, for example a type error
     */

    public void handleSequenceConstructor(Compilation compilation, ComponentDeclaration decl) throws XPathException {
        // handle the "temporary tree" case by creating a Document sub-instruction
        // to construct and return a document node.
        if (sourceElement.hasChildNodes()) {
            if (declaredType == null) {
                Expression b = sourceElement.compileSequenceConstructor(compilation, decl, true);
                if (b == null) {
                    b = Literal.makeEmptySequence();
                }
                boolean textonly = UType.TEXT.subsumes(b.getItemType().getUType());
                DocumentInstr doc = new DocumentInstr(textonly, constantText);
                doc.setContentExpression(b);
                doc.setRetainedStaticContext(sourceElement.makeRetainedStaticContext());
                select = doc;
            } else {
                select = sourceElement.compileSequenceConstructor(compilation, decl, true);
                if (select == null) {
                    select = Literal.makeEmptySequence();
                }
                try {
                    RoleDiagnostic role =
                            new RoleDiagnostic(RoleDiagnostic.VARIABLE, name.getDisplayName(), 0);
                    role.setErrorCode("XTTE0570");
                    select = select.simplify();
                    select = sourceElement.getConfiguration().getTypeChecker(false).staticTypeCheck(
                            select, declaredType, role, sourceElement.makeExpressionVisitor());
                } catch (XPathException err) {
                    err.setLocator(sourceElement);
                    sourceElement.compileError(err);
                    select = new ErrorExpression(err);
                }
            }
        }
    }

    /**
     * Get the type actually declared for the attribute
     *
     * @return the type appearing in the "as" attribute, or null if the attribute was absent
     */

    public SequenceType getDeclaredType() {
        if (declaredType == null) {
            // may be handling a forwards reference - see hof-038
            String asAtt = sourceElement.getAttributeValue("", "as");
            if (asAtt == null) {
                return null;
            } else {
                try {
                    declaredType = sourceElement.makeSequenceType(asAtt);
                } catch (XPathException err) {
                    // the error will be reported when we get round to processing the function declaration
                }
            }

        }
        return declaredType;
    }

    /**
     * Get the select expression actually appearing in the variable declaration
     *
     * @return the select expression as it appears, or null if it is absent
     */

    public Expression getSelectExpression() {
        return select;
    }

    /**
     * Get the best available static type of the variable.
     *
     * @param useContentRules set to true if the standard rules for xsl:variable and similar elements apply,
     *                        whereby the element's contained sequence constructor substitutes for the select attribute
     * @return the static type declared for the variable, or inferred from its initialization
     */

    public SequenceType getInferredType(boolean useContentRules) {
        if (inferredType != null) {
            return inferredType;
        }
        Visibility visibility;
        try {
            visibility = sourceElement.getVisibility();
        } catch (XPathException err) {
            visibility = Visibility.PUBLIC; // error will be caught some time
        }
        if (hasProperty(PARAM) || hasProperty(ASSIGNABLE) ||
                !(visibility == Visibility.PRIVATE || visibility == Visibility.FINAL)) {
            SequenceType declared = getDeclaredType();
            return inferredType = declared == null ? SequenceType.ANY_SEQUENCE : declared;
        }
        if (select != null) {
            TypeHierarchy th = sourceElement.getConfiguration().getTypeHierarchy();
            if (Literal.isEmptySequence(select)) {
                // returning Type.EMPTY gives problems with static type checking
                return inferredType = declaredType == null ? SequenceType.ANY_SEQUENCE : declaredType;
            }
            ItemType actual = select.getItemType();
            int card = select.getCardinality();
            if (declaredType != null) {
                if (!th.isSubType(actual, declaredType.getPrimaryType())) {
                    actual = declaredType.getPrimaryType();
                }
                if (!Cardinality.subsumes(declaredType.getCardinality(), card)) {
                    card = declaredType.getCardinality();
                }
            }
            inferredType = SequenceType.makeSequenceType(actual, card);
            return inferredType;
        }
        if (useContentRules) {
            if (sourceElement.hasChildNodes()) {
                if (declaredType == null) {
                    return SequenceType.makeSequenceType(NodeKindTest.DOCUMENT, StaticProperty.EXACTLY_ONE);
                } else {
                    return declaredType;
                }
            } else {
                if (declaredType == null) {
                    // no select attribute or content: value is an empty string
                    return SequenceType.SINGLE_STRING;
                } else {
                    return declaredType;
                }
            }
        }
        return declaredType;
    }

    /**
     * Method called by VariableReference to register the variable reference for
     * subsequent fixup
     *
     * @param ref the variable reference being registered
     */

    public void registerReference(BindingReference ref) {
        references.add(ref);
    }


    public GroundedValue getConstantValue() {
        if(constantValue == null) {
            final SequenceType type = getInferredType(true);
            final TypeHierarchy th = sourceElement.getConfiguration().getTypeHierarchy();
            if (!hasProperty(ASSIGNABLE) && !hasProperty(PARAM) && !(visibility == Visibility.PUBLIC || visibility == Visibility.ABSTRACT)) {
                if (select instanceof Literal) {
                    // we can't rely on the constant value because it hasn't yet been type-checked,
                    // which could change it (eg by numeric promotion). Rather than attempt all the type-checking
                    // now, we do a quick check. See test bug64
                    int relation = th.relationship(select.getItemType(), type.getPrimaryType());
                    if (relation == TypeHierarchy.SAME_TYPE || relation == TypeHierarchy.SUBSUMED_BY) {
                        constantValue = ((Literal) select).getValue();
                    }
                }
            }
        }
        return constantValue;
    }

    /**
     * Notify all references to this variable of the data type
     *
     * @param compiledGlobalVariable null if this is a local variable; otherwise, the compiled global variable
     * @throws XPathException if the declaration is invalid
     */

    public void fixupReferences(GlobalVariable compiledGlobalVariable) throws XPathException {
        final SequenceType type = getInferredType(true);
        final TypeHierarchy th = sourceElement.getConfiguration().getTypeHierarchy();
        GroundedValue constantValue = null;
        int properties = 0;
        if (!hasProperty(ASSIGNABLE) && !hasProperty(PARAM) && !(visibility == Visibility.PUBLIC || visibility == Visibility.ABSTRACT)) {
            /*if (select instanceof Literal) {
                // we can't rely on the constant value because it hasn't yet been type-checked,
                // which could change it (eg by numeric promotion). Rather than attempt all the type-checking
                // now, we do a quick check. See test bug64
                int relation = th.relationship(select.getItemType(), type.getPrimaryType());
                if (relation == TypeHierarchy.SAME_TYPE || relation == TypeHierarchy.SUBSUMED_BY) {
                    constantValue = ((Literal) select).getValue();
                }
            } */
            if (select != null) {
                properties = select.getSpecialProperties();
            }
        }
        for (BindingReference reference : references) {
            if (compiledGlobalVariable != null) {
                reference.fixup(compiledGlobalVariable);
            }
            reference.setStaticType(type, getConstantValue(), properties);
        }
    }

    /**
     * Notify all variable references of the Binding instruction
     *
     * @param binding the Binding that represents this variable declaration in the executable code tree
     */

    protected void fixupBinding(Binding binding) {
        for (BindingReference reference : references) {
            reference.fixup(binding);
        }
    }

}

