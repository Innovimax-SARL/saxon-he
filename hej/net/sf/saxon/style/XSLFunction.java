////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.Component;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.TailCallLoop;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.expr.instruct.UserFunction;
import net.sf.saxon.expr.instruct.UserFunctionParameter;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.query.Annotation;
import net.sf.saxon.query.AnnotationList;
import net.sf.saxon.trace.LocationKind;
import net.sf.saxon.trans.*;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.Whitespace;

import java.util.ArrayList;
import java.util.List;

/**
 * Handler for xsl:function elements in stylesheet (XSLT 2.0). <BR>
 * Attributes: <br>
 * name gives the name of the function
 * saxon:memo-function=yes|no indicates whether it acts as a memo function.
 */

public class XSLFunction extends StyleElement implements StylesheetComponent {

    private boolean doneAttributes = false;
    /*@Nullable*/ private String nameAtt = null;
    private String asAtt = null;
    private SequenceType resultType;
    private SlotManager stackFrameMap;
    private boolean memoFunction = false;
    private String overrideExtensionFunctionAtt = null;
    private boolean overrideExtensionFunction = true;
    private int numberOfArguments = -1;  // -1 means not yet known
    private UserFunction compiledFunction;
    private Visibility visibility;
    private FunctionStreamability streamability;
    private UserFunction.Determinism determinism = UserFunction.Determinism.PROACTIVE;

    /**
     * Get the corresponding Procedure object that results from the compilation of this
     * StylesheetProcedure
     */
    public UserFunction getActor() {
        return compiledFunction;
    }

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

        if (doneAttributes) {
            return;
        }
        doneAttributes = true;

        AttributeCollection atts = getAttributeList();

        overrideExtensionFunctionAtt = null;
        String visibilityAtt = null;
        String cacheAtt = null;
        String newEachTimeAtt = null;
        String streamabilityAtt = null;

        for (int a = 0; a < atts.getLength(); a++) {
            String uri = atts.getURI(a);
            String local = atts.getLocalName(a);
            if ("".equals(uri)) {
                if (local.equals("name")) {
                    nameAtt = Whitespace.trim(atts.getValue(a));
                    assert nameAtt != null;
                    if (nameAtt.indexOf(':') < 0) {
                        nameAtt = "Q{" + NamespaceConstant.SAXON + "}error-function-name";
                        compileError("Function name must have a namespace prefix", "XTSE0740");
                    }
                    try {
                        setObjectName(makeQName(nameAtt));
                    } catch (NamespaceException err) {
                        compileError(err.getMessage(), "XTSE0280");
                    } catch (XPathException err) {
                        compileError(err);
                    }
                } else if (local.equals("as")) {
                    asAtt = atts.getValue(a);
                } else if (local.equals("visibility")) {
                    visibilityAtt = Whitespace.trim(atts.getValue(a));
                } else if (local.equals("streamability")) {
                    streamabilityAtt = Whitespace.trim(atts.getValue(a));
                } else if (local.equals("override")) {
                    String overrideAtt = Whitespace.trim(atts.getValue(a));
                    boolean override = processBooleanAttribute("override", overrideAtt);
                    if (overrideExtensionFunctionAtt != null) {
                        if (override != overrideExtensionFunction) {
                            compileError("Attributes override-extension-function and override are both used, but do not match", "XTSE0020");
                        }
                    } else {
                        overrideExtensionFunctionAtt = overrideAtt;
                        overrideExtensionFunction = override;
                    }
                    compileWarning("The xsl:function/@override attribute is deprecated; use override-extension-function", SaxonErrorCode.SXWN9014);
                } else if (local.equals("override-extension-function")) {
                    String overrideExtAtt = Whitespace.trim(atts.getValue(a));
                    boolean overrideExt = processBooleanAttribute("override-extension-function", overrideExtAtt);
                    if (overrideExtensionFunctionAtt != null) {
                        if (overrideExt != overrideExtensionFunction) {
                            compileError("Attributes override-extension-function and override are both used, but do not match", "XTSE0020");
                        }
                    } else {
                        overrideExtensionFunctionAtt = overrideExtAtt;
                        overrideExtensionFunction = overrideExt;
                    }
                    if (local.equals("override")) {
                        compileWarning("The xsl:function/@override attribute is deprecated; use override-extension-function", SaxonErrorCode.SXWN9014);
                    }
                } else if (local.equals("cache")) {
                    cacheAtt = Whitespace.trim(atts.getValue(a));
                } else if (local.equals("new-each-time")) {
                    newEachTimeAtt = Whitespace.trim(atts.getValue(a));
                } else {
                    checkUnknownAttribute(atts.getNodeName(a));
                }
            } else if (local.equals("memo-function") && uri.equals(NamespaceConstant.SAXON)) {
                memoFunction = processBooleanAttribute("saxon:memo-function", atts.getValue(a));
            } else {
                checkUnknownAttribute(atts.getNodeName(a));
            }
        }

        if (nameAtt == null) {
            reportAbsence("name");
            nameAtt = "xsl:unnamed-function-" + generateId();
        }

        if (asAtt == null) {
            resultType = SequenceType.ANY_SEQUENCE;
        } else {
            resultType = makeSequenceType(asAtt);
        }

        if (visibilityAtt == null) {
            visibility = Visibility.PRIVATE;
        } else {
            check30attribute("visibility");
            visibility = interpretVisibilityValue(visibilityAtt, "");
        }

        if (streamabilityAtt == null) {
            streamability = FunctionStreamability.UNCLASSIFIED;
        } else {
            check30attribute("streamability");
            streamability = getStreamabilityValue(streamabilityAtt);
            if (streamability.isStreaming()) {
                boolean streamable = processStreamableAtt("yes");
                if (!streamable) {
                    streamability = FunctionStreamability.UNCLASSIFIED;
                }
            }
        }

        if (newEachTimeAtt != null) {
            if ("maybe".equals(newEachTimeAtt)) {
                determinism = UserFunction.Determinism.ELIDABLE;
            } else {
                try {
                    boolean b = processBooleanAttribute("new-each-time", newEachTimeAtt);
                    determinism = b ? UserFunction.Determinism.PROACTIVE : UserFunction.Determinism.DETERMINISTIC;
                } catch (XPathException e) {
                    invalidAttribute("new-each-time", "yes|no|maybe");
                }
            }
        }

        boolean cache = false;
        if (cacheAtt != null) {
            cache = processBooleanAttribute("cache", cacheAtt);
        }

        if (determinism == UserFunction.Determinism.DETERMINISTIC || cache) {
            memoFunction = true;
        }
    }

    private FunctionStreamability getStreamabilityValue(String s) throws XPathException {
        if (s.contains(":")) {
            // QNames are allowed but not recognized by Saxon
            try {
                makeQName(s);
            } catch (NamespaceException e) {
                throw new XPathException(e);
            }
            return FunctionStreamability.UNCLASSIFIED;
        }
        for (FunctionStreamability v : FunctionStreamability.values()) {
            if (v.streamabilityStr.equals(s)) {
                return v;
            }
        }
        invalidAttribute("visibility", "unclassified|absorbing|inspection|filter|shallow-descent|deep-descent|ascent");
        return null;
    }

    /**
     * Get a name identifying the object of the expression, for example a function name, template name,
     * variable name, key name, element name, etc. This is used only where the name is known statically.
     * If there is no name, the value will be -1.
     */

    /*@NotNull*/
    public StructuredQName getObjectName() {
        StructuredQName qn = super.getObjectName();
        if (qn == null) {
            nameAtt = Whitespace.trim(getAttributeValue("", "name"));
            if (nameAtt == null) {
                return new StructuredQName("saxon", NamespaceConstant.SAXON, "badly-named-function" + generateId());
            }
            try {
                qn = makeQName(nameAtt);
                setObjectName(qn);
            } catch (NamespaceException err) {
                return new StructuredQName("saxon", NamespaceConstant.SAXON, "badly-named-function" + generateId());
            } catch (XPathException err) {
                return new StructuredQName("saxon", NamespaceConstant.SAXON, "badly-named-function" + generateId());
            }
        }
        return qn;
    }

    /**
     * Determine whether this type of element is allowed to contain a template-body.
     *
     * @return true: yes, it may contain a general template-body
     */

    public boolean mayContainSequenceConstructor() {
        return true;
    }

    protected boolean mayContainParam(String attName) {
        return !"required".equals(attName);
    }

    /**
     * Specify that xsl:param is a permitted child
     */

    protected boolean isPermittedChild(StyleElement child) {
        return child instanceof XSLLocalParam;
    }

    public Visibility getVisibility() {
        if (visibility == null) {
            try {
                String vAtt = getAttributeValue("", "visibility");
                return vAtt == null ? Visibility.PRIVATE : interpretVisibilityValue(Whitespace.trim(vAtt), "");
            } catch (XPathException e) {
                return Visibility.PRIVATE;
            }
        }
        return visibility;
    }

    public SymbolicName.F getSymbolicName() {
        return new SymbolicName.F(getObjectName(), getNumberOfArguments());
    }

    public void checkCompatibility(Component component) throws XPathException {
        if (compiledFunction == null) {
            getCompiledFunction();
        }

        TypeHierarchy th = getConfiguration().getTypeHierarchy();
        UserFunction other = (UserFunction) component.getActor();
        if (!compiledFunction.getSymbolicName().equals(other.getSymbolicName())) {
            // Can't happen
            compileError("The overriding xsl:function " + nameAtt + " does not match the overridden function: " +
                    "the function name/arity does not match", "XTSE3070");
        }
        if (!compiledFunction.getDeclaredResultType().isSameType(other.getDeclaredResultType(), th)) {
            compileError("The overriding xsl:function " + nameAtt + " does not match the overridden function: " +
                    "the return type does not match", "XTSE3070");
        }
        if (!compiledFunction.getDeclaredStreamability().equals(other.getDeclaredStreamability())) {
            compileError("The overriding xsl:function " + nameAtt + " does not match the overridden function: " +
                    "the streamability category does not match", "XTSE3070");
        }
        if (!compiledFunction.getDeterminism().equals(other.getDeterminism())) {
            compileError("The overriding xsl:function " + nameAtt + " does not match the overridden function: " +
                    "the new-each-time attribute does not match", "XTSE3070");
        }

        for (int i = 0; i < getNumberOfArguments(); i++) {
            if (!compiledFunction.getArgumentType(i).isSameType(other.getArgumentType(i), th)) {
                compileError("The overriding xsl:function " + nameAtt + " does not match the overridden function: " +
                        "the type of the " + RoleDiagnostic.ordinal(i + 1) + " argument does not match", "XTSE3070");
            }
        }
    }

    /**
     * Is override-extension-function="yes"?.
     *
     * @return true if override-extension-function="yes" was specified, otherwise false
     */

    public boolean isOverrideExtensionFunction() {
        if (overrideExtensionFunctionAtt == null) {
            // this is a forwards reference
            try {
                prepareAttributes();
            } catch (XPathException e) {
                // no action: error will be caught later
            }
        }
        return overrideExtensionFunction;
    }

    public void index(ComponentDeclaration decl, PrincipalStylesheetModule top) throws XPathException {
        //getSkeletonCompiledFunction();
        getCompiledFunction();
        top.indexFunction(decl);
    }

    /**
     * Notify all references to this function of the data type.
     *
     * @throws XPathException
     */

    public void fixupReferences() throws XPathException {
//        for (UserFunctionReference reference : references) {
//            if (reference instanceof UserFunctionCall) {
//                ((UserFunctionCall) reference).setStaticType(resultType);
//            }
//        }
        super.fixupReferences();
    }

    public void validate(ComponentDeclaration decl) throws XPathException {

        stackFrameMap = getConfiguration().makeSlotManager();

        // check the element is at the top level of the stylesheet

        checkTopLevel("XTSE0010", true);
        int arity = getNumberOfArguments();
//        if (compiledFunction != null) {
//            completeCompiledFunction();
//        }

        if (arity == 0 && streamability != FunctionStreamability.UNCLASSIFIED) {
            compileError("A function with no arguments must have streamability=unclassified", "XTSE3155");
        }

    }


    /**
     * Compile the function definition to create an executable representation
     * The compileDeclaration() method has the side-effect of binding
     * all references to the function to the executable representation
     * (a UserFunction object)
     *
     * @throws XPathException
     */

    public void compileDeclaration(Compilation compilation, ComponentDeclaration decl) throws XPathException {
        Expression exp = compileSequenceConstructor(compilation, decl, false);
        if (exp == null) {
            exp = Literal.makeEmptySequence();
        } else if (Literal.isEmptySequence(exp)) {
            // no action
        } else {
            if (visibility == Visibility.ABSTRACT) {
                compileError("A function defined with visibility='abstract' must have no body");
            }
            exp = exp.simplify();
        }

        UserFunction fn = getCompiledFunction();
        fn.setBody(exp);
        fn.setStackFrameMap(stackFrameMap);
        bindParameterDefinitions(fn);
        fn.setRetainedStaticContext(makeRetainedStaticContext());
        fn.setOverrideExtensionFunction(overrideExtensionFunction);

        if (memoFunction && !fn.isMemoFunction()) {
            compileWarning("Memo functions are not available in Saxon-HE: saxon:memo-function attribute ignored",
                    SaxonErrorCode.SXWN9011);
        }

        Component overridden = getOverriddenComponent();
        if (overridden != null) {
            checkCompatibility(overridden);
        }
    }

    public void optimize(ComponentDeclaration declaration) throws XPathException {
        Expression exp = compiledFunction.getBody();
        ExpressionTool.resetPropertiesWithinSubtree(exp);
        ExpressionVisitor visitor = makeExpressionVisitor();
        Expression exp2 = exp.typeCheck(visitor, ContextItemStaticInfo.ABSENT);

        if (streamability.isStreaming()) {
            visitor.setOptimizeForStreaming(true);
        }
        exp2 = ExpressionTool.optimizeComponentBody(exp2, getCompilation(), visitor, ContextItemStaticInfo.ABSENT, true);

        // Add trace wrapper code if required
        exp2 = makeTraceInstruction(this, exp2);
        compiledFunction.setBody(exp2);

        // Assess the streamability of the function body
        if (streamability.isStreaming()) {
            visitor.getConfiguration().obtainOptimizer().assessFunctionStreamability(this, compiledFunction);
        }

        allocateLocalSlots(exp2);
        if (exp2 != exp) {
            compiledFunction.setBody(exp2);
        }

        if (!streamability.isStreaming()) {
            int tailCalls = ExpressionTool.markTailFunctionCalls(exp2, getObjectName(), getNumberOfArguments());
            if (tailCalls != 0) {
                compiledFunction.setTailRecursive(tailCalls > 0, tailCalls > 1);
                exp2 = compiledFunction.getBody();
                compiledFunction.setBody(new TailCallLoop(compiledFunction, exp2));
                //compiledFunction.getBody().setPostureAndSweep(exp2.getPostureAndSweepIfKnown());
            }
        }

        compiledFunction.computeEvaluationMode();

        if (streamability.isStreaming()) {
            compiledFunction.prepareForStreaming();
        } else if (visitor.getConfiguration().isDeferredByteCode(Configuration.XSLT)) {
            compiledFunction.setBody(getConfiguration().obtainOptimizer().makeByteCodeCandidate(compiledFunction, compiledFunction.getBody(), nameAtt));
        }

        if (isExplaining()) {
            exp2.explain(getConfiguration().getLogger());
        }
    }

    /**
     * Generate byte code if appropriate
     *
     * @param opt the optimizer
     * @throws net.sf.saxon.trans.XPathException if bytecode generation fails
     */
    public void generateByteCode(Optimizer opt) throws XPathException {
        // Generate byte code if appropriate

        if (getCompilation().getCompilerInfo().isGenerateByteCode() &&
                streamability == FunctionStreamability.UNCLASSIFIED) {
            try {
                ICompilerService compilerService = getConfiguration().makeCompilerService(Configuration.XSLT);
                Expression cbody = opt.compileToByteCode(compilerService, compiledFunction.getBody(), nameAtt,
                                                         Expression.PROCESS_METHOD | Expression.ITERATE_METHOD);
                if (cbody != null) {
                    compiledFunction.setBody(cbody);
                }
            } catch (Exception e) {
                System.err.println("Failed while compiling function " + nameAtt);
                e.printStackTrace();
                throw new XPathException(e);
            }
        }
    }



    /**
     * Get associated stack frame details.
     *
     * @return the associated SlotManager object
     */

    public SlotManager getSlotManager() {
        return stackFrameMap;
    }

    /**
     * Get the type of value returned by this function
     *
     * @return the declared result type, or the inferred result type
     * if this is more precise
     */
    public SequenceType getResultType() {
        if (resultType == null) {
            // may be handling a forwards reference - see hof-038
            String asAtt = getAttributeValue("", "as");
            if (asAtt != null) {
                try {
                    resultType = makeSequenceType(asAtt);
                } catch (XPathException err) {
                    // the error will be reported when we get round to processing the function declaration
                }
            }
        }
        return resultType == null ? SequenceType.ANY_SEQUENCE : resultType;
    }

    /**
     * Get the number of arguments declared by this function (that is, its arity).
     *
     * @return the arity of the function
     */

    public int getNumberOfArguments() {
        if (numberOfArguments == -1) {
            numberOfArguments = 0;
            AxisIterator kids = iterateAxis(AxisInfo.CHILD);
            while (true) {
                Item child = kids.next();
                if (child instanceof XSLLocalParam) {
                    numberOfArguments++;
                } else {
                    return numberOfArguments;
                }
            }
        }
        return numberOfArguments;
    }

    /**
     * Set the definitions of the parameters in the compiled function, as an array.
     *
     * @param fn the compiled object representing the user-written function
     */

    public void setParameterDefinitions(UserFunction fn) {
        UserFunctionParameter[] params = new UserFunctionParameter[getNumberOfArguments()];
        fn.setParameterDefinitions(params);
        int count = 0;
        AxisIterator kids = iterateAxis(AxisInfo.CHILD);
        NodeInfo node;
        while ((node = kids.next()) != null) {
            if (node instanceof XSLLocalParam) {
                UserFunctionParameter param = new UserFunctionParameter();
                params[count] = param;
                param.setRequiredType(((XSLLocalParam) node).getRequiredType());
                param.setVariableQName(((XSLLocalParam) node).getVariableQName());
                param.setSlotNumber(((XSLLocalParam) node).getSlotNumber());
                if (count == 0 && streamability != FunctionStreamability.UNCLASSIFIED) {
                    param.setFunctionStreamability(streamability);
                }
                count++;
            } else {
                break;
            }
        }
    }

    /**
     * For each local parameter definition, fix up all references to the function parameter
     * @param fn the compiled user function
     */

    private void bindParameterDefinitions(UserFunction fn) {
        UserFunctionParameter[] params = fn.getParameterDefinitions();
        int count = 0;
        AxisIterator kids = iterateAxis(AxisInfo.CHILD);
        NodeInfo node;
        while ((node = kids.next()) != null) {
            if (node instanceof XSLLocalParam) {
                UserFunctionParameter param = params[count++];
                param.setRequiredType(((XSLLocalParam) node).getRequiredType());
                param.setVariableQName(((XSLLocalParam) node).getVariableQName());
                param.setSlotNumber(((XSLLocalParam) node).getSlotNumber());
                ((XSLLocalParam) node).getSourceBinding().fixupBinding(param);
            }
        }
    }

    /**
     * Get the argument types
     *
     * @return the declared types of the arguments
     */

    public SequenceType[] getArgumentTypes() {
        SequenceType[] types = new SequenceType[getNumberOfArguments()];
        int count = 0;
        AxisIterator kids = iterateAxis(AxisInfo.CHILD);
        NodeInfo node;
        while ((node = kids.next()) != null) {
            if (node instanceof XSLLocalParam) {
                types[count++] = ((XSLLocalParam) node).getRequiredType();
            }
        }
        return types;
    }

    /**
     * Get the compiled function
     *
     * @return the object representing the compiled user-written function
     */

    public UserFunction getCompiledFunction() {
        if (compiledFunction == null) {
            try {
                prepareAttributes();
                UserFunction fn = getConfiguration().newUserFunction(memoFunction, streamability);
                fn.setPackageData(getCompilation().getPackageData());
                fn.setFunctionName(getObjectName());
                setParameterDefinitions(fn);
                fn.setResultType(getResultType());
                fn.setLineNumber(getLineNumber());
                fn.setSystemId(getSystemId());
                fn.makeDeclaringComponent(visibility, getContainingPackage());
                fn.setDeclaredVisibility(getDeclaredVisibility());
                fn.setDeclaredStreamability(streamability);
                fn.setDeterminism(determinism);
                List<Annotation> annotations = new ArrayList<Annotation>();
                if (memoFunction) {
                    annotations.add(new Annotation(new StructuredQName("saxon", NamespaceConstant.SAXON, "memo-function")));
                }
                fn.setAnnotations(new AnnotationList(annotations));
                compiledFunction = fn;
            } catch (XPathException err) {
                return null;
            }
        }
        return compiledFunction;
    }


    /**
     * Get the type of construct. This will be a constant in
     * class {@link LocationKind}. This method is part of the
     * {@link net.sf.saxon.trace.InstructionInfo} interface
     */

    public int getConstructType() {
        return StandardNames.XSL_FUNCTION;
    }

}

