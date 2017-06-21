////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;


import net.sf.saxon.Configuration;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.instruct.*;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.expr.sort.SortKeyDefinition;
import net.sf.saxon.expr.sort.SortKeyDefinitionList;
import net.sf.saxon.functions.Current;
import net.sf.saxon.functions.FunctionLibraryList;
import net.sf.saxon.lib.FeatureKeys;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.*;
import net.sf.saxon.trace.InstructionInfo;
import net.sf.saxon.trace.LocationKind;
import net.sf.saxon.trans.*;
import net.sf.saxon.tree.AttributeLocation;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.iter.ListIterator;
import net.sf.saxon.tree.linked.ElementImpl;
import net.sf.saxon.tree.linked.NodeImpl;
import net.sf.saxon.tree.linked.TextImpl;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.tree.util.NamespaceIterator;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.type.*;
import net.sf.saxon.value.BigDecimalValue;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.Whitespace;

import javax.xml.transform.SourceLocator;
import javax.xml.transform.TransformerException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * Abstract superclass for all element nodes in the stylesheet.
 * <p>Note: this class implements Locator. The element retains information about its own location
 * in the stylesheet, which is useful when an XSLT static error is found.</p>
 */

public abstract class StyleElement extends ElementImpl {

    /*@Nullable*/ protected String[] extensionNamespaces = null;        // a list of URIs
    private String[] excludedNamespaces = null;           // a list of URIs
    protected int version = -1;                 // the effective version of this element
    protected ExpressionContext staticContext = null;
    protected XPathException validationError = null;
    protected int reportingCircumstances = REPORT_ALWAYS;
    protected String defaultXPathNamespace = null;
    protected String defaultCollationName = null;
    protected StructuredQName defaultMode;
    protected boolean expandText = false;
    private boolean explaining = false;  // true if saxon:explain="yes"
    private StructuredQName objectName;  // for instructions that define an XSLT named object, the name of that object
    private String baseURI;
    private Compilation compilation;
    private ExplicitLocation savedLocation = null;
    private int defaultValidation = Validation.DEFAULT;


    // Conditions under which an error is to be reported

    public static final int REPORT_ALWAYS = 1;
    public static final int REPORT_UNLESS_FORWARDS_COMPATIBLE = 2;
    public static final int REPORT_IF_INSTANTIATED = 3;
    public static final int REPORT_STATICALLY_UNLESS_FALLBACK_AVAILABLE = 4;
    public static final int REPORT_DYNAMICALLY_UNLESS_FALLBACK_AVAILABLE = 5;
    public static final int IGNORED_INSTRUCTION = 6;

    protected int actionsCompleted = 0;
    public static final int ACTION_VALIDATE = 1;
    public static final int ACTION_COMPILE = 2;
    public static final int ACTION_TYPECHECK = 4;
    public static final int ACTION_OPTIMIZE = 8;
    public static final int ACTION_FIXUP = 16;
    public static final int ACTION_PROCESS_ATTRIBUTES = 32;


    /**
     * Constructor
     */

    public StyleElement() {
    }

    public Compilation getCompilation() {
        return compilation;
    }

    public void setCompilation(Compilation compilation) {
        this.compilation = compilation;
    }

    public StylesheetPackage getPackageData() {
        return getPrincipalStylesheetModule().getStylesheetPackage();
    }


    /**
     * Get the static context for expressions on this element
     *
     * @return the static context
     */

    public ExpressionContext getStaticContext() {
        if (staticContext == null) {
            staticContext = new ExpressionContext(this, null);
        }
        return staticContext;
    }

    public ExpressionContext getStaticContext(StructuredQName attributeName) {
        return new ExpressionContext(this, attributeName);
    }


    /**
     * Get the base URI of the element, which acts as the static base URI for XPath expressions defined
     * on this element. This is an expensive operation so the result is cached
     *
     * @return the base URI
     */

    public String getBaseURI() {
        if (baseURI == null) {
            baseURI = super.getBaseURI();
        }
        return baseURI;
    }

    /**
     * Make an expression visitor
     *
     * @return the expression visitor
     */

    public ExpressionVisitor makeExpressionVisitor() {
        return ExpressionVisitor.make(getStaticContext());
    }

    /**
     * Ask whether the code is compiled in schema-aware mode
     *
     * @return true if the compilation is schema-aware
     */

    public boolean isSchemaAware() {
        return getCompilation().isSchemaAware();
    }

    /**
     * Determine whether saxon:explain has been set to "yes"
     *
     * @return true if saxon:explain has been set to "yes" on this element
     */

    protected boolean isExplaining() {
        return explaining;
    }

    /**
     * Make this node a substitute for a temporary one previously added to the tree. See
     * StyleNodeFactory for details. "A node like the other one in all things but its class".
     * Note that at this stage, the node will not yet be known to its parent, though it will
     * contain a reference to its parent; and it will have no children.
     *
     * @param temp the element which this one is substituting for
     */

    public void substituteFor(StyleElement temp) {
        setRawParent(temp.getRawParent());
        setAttributeList(temp.getAttributeList());
        setNamespaceList(temp.getNamespaceList());
        setNodeName(temp.getNodeName());
        setRawSequenceNumber(temp.getRawSequenceNumber());
        extensionNamespaces = temp.extensionNamespaces;
        excludedNamespaces = temp.excludedNamespaces;
        version = temp.version;
        staticContext = temp.staticContext;
        validationError = temp.validationError;
        reportingCircumstances = temp.reportingCircumstances;
        compilation = temp.compilation;
        //lineNumber = temp.lineNumber;
    }

    /**
     * Set a validation error. This is an error detected during construction of this element on the
     * stylesheet, but which is not to be reported until later.
     *
     * @param reason        the details of the error
     * @param circumstances a code identifying the circumstances under which the error is to be reported
     */

    public void setValidationError(TransformerException reason,
                                   int circumstances) {
        validationError = XPathException.makeXPathException(reason);
        reportingCircumstances = circumstances;
    }

    public void setIgnoreInstruction() {
        reportingCircumstances = IGNORED_INSTRUCTION;
    }

    /**
     * Ask whether this node is an instruction. The default implementation says it isn't.
     *
     * @return true if this element is an instruction
     */

    public boolean isInstruction() {
        return false;
    }

    /**
     * Ask whether this node is a declaration, that is, a permitted child of xsl:stylesheet
     * (including xsl:include and xsl:import). The default implementation returns false
     *
     * @return true if the element is a permitted child of xsl:stylesheet or xsl:transform
     */

    public boolean isDeclaration() {
        return false;
    }

    /**
     * Get the visibility of the component. Returns the actual value of the visibility attribute,
     * after validation, unless this is absent, in which case it returns the default value of PRIVATE.
     *
     * @return the declared visibility of the component, or PRIVATE if the visibility attribute is absent.
     * @throws XPathException
     */

    public Visibility getVisibility() throws XPathException {
        String vis = getAttributeValue("", "visibility");
        if (vis == null) {
            return Visibility.PRIVATE;
        } else {
            return interpretVisibilityValue(vis, "");
        }
    }

    /**
     * Get the visibility of the component. Returns the actual value of the visibility attribute,
     * after validation, unless this is absent, in which case it returns null.
     *
     * @return the declared visibility of the component, or null if the visibility attribute is absent.
     * @throws XPathException
     */

    public Visibility getDeclaredVisibility() throws XPathException {
        String vis = getAttributeValue("", "visibility");
        if (vis == null) {
            return null;
        } else {
            return interpretVisibilityValue(vis, "");
        }
    }

    /**
     * Mark tail-recursive calls on templates and functions.
     * For most instructions, this returns false.
     *
     * @return true if one or more tail calls were identified
     */

    protected boolean markTailCalls() {
        return false;
    }

    /**
     * Determine whether this type of element is allowed to contain a sequence constructor
     *
     * @return true if this instruction is allowed to contain a sequence constructor
     */

    protected boolean mayContainSequenceConstructor() {
        return false;
    }

    /**
     * Determine whether this type of element is allowed to contain an xsl:fallback
     * instruction. Note that this is only relevant if the element is an instruction.
     *
     * @return true if this element is allowed to contain an xsl:fallback
     */

    protected boolean mayContainFallback() {
        return mayContainSequenceConstructor();
    }

    /**
     * Determine whether this type of element is allowed to contain an xsl:param element
     *
     * @param attName if null, the method tests whether an xsl:param child is allowed.
     *                If non-null, it tests whether an xsl:param child with the given attribute name is allowed
     * @return true if this element is allowed to contain an xsl:param
     */

    protected boolean mayContainParam(String attName) {
        return false;
    }

    /**
     * Get the containing XSLStylesheet or XSLPackage element
     *
     * @return the XSLStylesheet element representing the outermost element of the containing
     * stylesheet module. Return null if there is no containing XSLStylesheet element
     */

    public XSLModuleRoot getContainingStylesheet() {
        NodeInfo parent = getParent();
        if (parent instanceof XSLModuleRoot) {
            return (XSLModuleRoot) parent;
        } else if (parent instanceof StyleElement) {
            return ((StyleElement) parent).getContainingStylesheet();
        } else {
            // this can happen when early errors are detected in a simplified stylesheet,
            // or when the element is part of a package outside any xsl:stylesheet
            return null;
        }
    }

    /**
     * Get the effective value of the default-validation attribute
     *
     * @return the value of default-validation, as a constant from the {@link Validation} class,
     * or Validation.STRIP if there is no containing element with a default-validation
     * attribute.
     */

    public int getDefaultValidation() {
        int v = defaultValidation;
        NodeInfo p = this;
        while (v == Validation.DEFAULT) {
            p = p.getParent();
            if (!(p instanceof StyleElement)) {
                return Validation.STRIP;
            }
            v = ((StyleElement) p).defaultValidation;
        }
        return v;
    }

    /**
     * Make a structured QName, using this Element as the context for namespace resolution.
     * If the name is unprefixed, the default namespace is <b>not</b> used.
     *
     * @param lexicalQName The lexical QName as written, in the form "[prefix:]localname". The name must have
     *                     already been validated as a syntactically-correct QName. Leading and trailing whitespace
     *                     will be trimmed. If XSLT 3.0 is enabled, then the EQName syntax "Q{uri}local" is also
     *                     accepted.
     * @return the StructuredQName representation of this lexical QName
     * @throws XPathException     if the qname is not a lexically-valid QName, or if the name
     *                            is in a reserved namespace.
     * @throws NamespaceException if the prefix of the qname has not been declared
     */

    public final StructuredQName makeQName(String lexicalQName)
            throws XPathException, NamespaceException {

        StructuredQName qName;
        try {
            qName = StructuredQName.fromLexicalQName(lexicalQName, false,
                                                     true, this);
        } catch (XPathException e) {
            e.setIsStaticError(true);
            String code = e.getErrorCodeLocalPart();
            if ("FONS0004".equals(code)) {
                e.setErrorCode("XTSE0280");
            } else if ("FOCA0002".equals(code)) {
                e.setErrorCode("XTSE0020");
            } else if (code == null) {
                e.setErrorCode("XTSE0020");
            }
            e.setLocator(this);
            throw e;
        }
        if (NamespaceConstant.isReserved(qName.getURI())) {
            if (qName.hasURI(NamespaceConstant.XSLT)) {
                if (qName.getLocalPart().equals("initial-template")
                        && (this instanceof XSLTemplate || this instanceof XSLCallTemplate)) {
                    return qName;
                }
                if (qName.getLocalPart().equals("original")) {
                    // OK if within xsl:override
                    NameTest nodeTest = new NameTest(Type.ELEMENT, StandardNames.XSL_OVERRIDE, getNamePool());
                    if (iterateAxis(AxisInfo.ANCESTOR, nodeTest).next() != null) {
                        return qName;
                    }
                }
            }
            XPathException err = new XPathException("Namespace prefix " +
                                                            qName.getPrefix() + " refers to a reserved namespace");
            err.setIsStaticError(true);
            err.setErrorCode("XTSE0080");
            throw err;
        }
        return qName;
    }

    /**
     * Add a function library that recognizes the function call xsl:original, which is permitted
     * within a function that overrides another
     *
     * @param list the function library list to which the new function library should be added
     */

    public void addXSLOverrideFunctionLibrary(FunctionLibraryList list) {
        //Do nothing unless this element is an xsl:override
    }

    /**
     * Get the first ancestor element in the stylesheet tree that has a given name, supplied by
     * fingerprint. Used particularly in Saxon-HE code to find elements such as xsl:override and
     * xsl:use-package that are not present in the Saxon-HE codebase
     *
     * @param fingerprint the name of the required element
     * @return the first (innermost) ancestor with the required name, or null if none is found
     */

    public StyleElement findAncestorElement(int fingerprint) {
        AxisIterator iter = iterateAxis(AxisInfo.ANCESTOR);
        NodeInfo node;
        while ((node = iter.next()) != null) {
            if (node instanceof StyleElement && ((StyleElement) node).getFingerprint() == fingerprint) {
                return (StyleElement) node;
            }
        }
        return null;
    }

    /**
     * Assuming this is an xsl:use-package element, find the package to which it refers.
     *
     * @return the package referenced by this xsl:use-package element; or null if this is not
     * an xsl:use-package element
     */

    public StylesheetPackage getUsedPackage() {
        return null;
    }

    /**
     * Check that a reference to xsl:original appears within an xsl:override element, and that
     * the name of the containing component matches the name of a component in the used stylesheet
     * package; return the component in that package with matching symbolic name
     *
     * @param componentKind the kind of component required, e.g. StandardNames.XSL_TEMPLATE
     * @return the component with matching name in the used stylesheet
     * @throws XPathException
     */

    public Actor getXslOriginal(int componentKind) throws XPathException {
        StyleElement container = componentKind == getFingerprint() ? this : findAncestorElement(componentKind);
        if (container == null || !(container instanceof StylesheetComponent)) {
            throw new XPathException(
                    "A reference to xsl:original appears within the wrong kind of component: in this case" +
                            ", it must be within xsl:" + getNamePool().getLocalName(componentKind), "XTSE0650", this);
        }
        SymbolicName originalName = ((StylesheetComponent) container).getSymbolicName();
        StyleElement xslOverride = container.findAncestorElement(StandardNames.XSL_OVERRIDE);
        if (xslOverride == null) {
            throw new XPathException("A reference to xsl:original can be used only within an xsl:override element");
        }
        StyleElement usePackage = xslOverride.findAncestorElement(StandardNames.XSL_USE_PACKAGE);
        if (usePackage == null) {
            throw new XPathException("The parent of xsl:override must be an xsl:use-package element", "XTSE0010", xslOverride);
        }

        Component overridden = usePackage.getUsedPackage().getComponent(originalName);
        if (overridden == null) {
            // the error will be detected and reported elsewhere
            return null;
        }
        return overridden.getActor();
    }

    /**
     * Get the component that this declaration overrides, or null if this is not an overriding declaration
     *
     * @return the overridden component, or null
     */

    public Component getOverriddenComponent() {
        if (!(this instanceof StylesheetComponent)) {
            return null;
        }
        SymbolicName originalName = ((StylesheetComponent) this).getSymbolicName();
        StyleElement xslOverride = findAncestorElement(StandardNames.XSL_OVERRIDE);
        if (xslOverride == null) {
            return null;
        }
        StyleElement usePackage = xslOverride.findAncestorElement(StandardNames.XSL_USE_PACKAGE);
        if (usePackage == null) {
            return null;
        }

        return usePackage.getUsedPackage().getComponent(originalName);
    }

    /**
     * Make a NamespaceContext object representing the list of in-scope namespaces. This will
     * be a copy of the namespace context with no references to objects in the stylesheet tree,
     * so that it can be kept until run-time without locking the tree down in memory.
     *
     * @return a copy of the namespace context
     */

    public SavedNamespaceContext makeNamespaceContext() {
        return new SavedNamespaceContext(NamespaceIterator.iterateNamespaces(this));
    }

    public RetainedStaticContext makeRetainedStaticContext() {
        return getStaticContext().makeRetainedStaticContext();
    }

    /**
     * Ask whether this instruction requires a different retained static context from the containing
     * (parent) instruction. That is, this instruction changes the static base URI, the default collation,
     * or the set of in-scope namespaces.
     *
     * @return true if the context for evaluating this instruction differs in relevant ways from that
     * of the calling instruction
     */
    public boolean changesRetainedStaticContext() {
        if (!ExpressionTool.equalOrNull(getBaseURI(), getParent().getBaseURI())) {
            return true;
        }
        if (defaultCollationName != null) {
            return true;
        }
        if (getDeclaredNamespaces(NamespaceBinding.EMPTY_ARRAY).length != 0) {
            return true;
        }
        if (!(getParent() instanceof StyleElement)) {
            return true;
        }
        if (getEffectiveVersion() != ((StyleElement) getParent()).getEffectiveVersion()) {
            return true;
        }
        return false;
    }

    /**
     * Get the namespace context of the instruction.
     *
     * @return the namespace context. This method does not make a copy of the namespace context,
     * so a reference to the returned NamespaceResolver will lock the stylesheet tree in memory.
     */

    public NamespaceResolver getNamespaceResolver() {
        return this;
    }

    /**
     * Process the attributes of this element and all its children
     *
     * @throws XPathException in the event of a static error being detected
     */

    public void processAllAttributes() throws XPathException {
        processDefaultCollationAttribute();
        processDefaultMode();
        staticContext = new ExpressionContext(this, null);
        processAttributes();
        AxisIterator kids = iterateAxis(AxisInfo.CHILD);
        NodeInfo child;
        while ((child = kids.next()) != null) {
            if (child instanceof StyleElement) {
                ((StyleElement) child).processAllAttributes();
                if (((StyleElement) child).explaining) {
                    // saxon:explain on any element in a template/function now causes an explanation at the
                    // level of the template/function
                    explaining = true;
                }
            }
        }
    }

    /**
     * Process the standard attributes such as [xsl:]default-collation
     *
     * @param namespace either "" to find the attributes in the null namespace,
     *                  or NamespaceConstant.XSLT to find them in the XSLT namespace
     * @throws net.sf.saxon.trans.XPathException if any of the standard attributes is incorrect
     */

    public void processStandardAttributes(String namespace) throws XPathException {
        //processDefaultCollationAttribute(namespace);
        processExtensionElementAttribute(namespace);
        processExcludedNamespaces(namespace);
        processVersionAttribute(namespace);
        processDefaultXPathNamespaceAttribute(namespace);
        processDefaultValidationAttribute(namespace);
        processExpandTextAttribute(namespace);
    }

    /**
     * Get an attribute value given the Clark name of the attribute (that is,
     * the name in {uri}local format).
     *
     * @param clarkName the name of the attribute in {uri}local format
     * @return the value of the attribute if it exists, or null otherwise
     */

    public String getAttributeValue(String clarkName) {
        NodeName nn = FingerprintedQName.fromClarkName(clarkName);
        return getAttributeValue(nn.getURI(), nn.getLocalPart());
    }

    /**
     * Process the attribute list for the element. This is a wrapper method that calls
     * prepareAttributes (provided in the subclass) and traps any exceptions
     *
     * @throws net.sf.saxon.trans.XPathException if a static error is detected
     */

    protected final void processAttributes() throws XPathException {
        try {
            prepareAttributes();
        } catch (XPathException err) {
            compileError(err);
        }
    }

    /**
     * Check whether an unknown attribute is permitted.
     *
     * @param nc The name code of the attribute name
     * @throws XPathException (and reports the error) if this is an attribute
     *                        that is not permitted on the containing element
     */

    protected void checkUnknownAttribute(NodeName nc) throws XPathException {

        String attributeURI = nc.getURI();
        String elementURI = getURI();
        String clarkName = nc.getStructuredQName().getClarkName();

        if (clarkName.equals(StandardNames.SAXON_EXPLAIN)) {
            String value = getAttributeValue(clarkName);
            explaining = processBooleanAttribute("saxon:explain", value);
        }

        if (forwardsCompatibleModeIsEnabled()) {
            // then unknown attributes are permitted and ignored
            return;
        }

        // allow xsl:extension-element-prefixes etc on an extension element

        if (isInstruction() &&
                attributeURI.equals(NamespaceConstant.XSLT) &&
                !elementURI.equals(NamespaceConstant.XSLT) &&
                (clarkName.endsWith("}default-collation") ||
                         clarkName.endsWith("}default-mode") ||
                         clarkName.endsWith("}xpath-default-namespace") ||
                         clarkName.endsWith("}expand-text") ||
                         clarkName.endsWith("}extension-element-prefixes") ||
                         clarkName.endsWith("}exclude-result-prefixes") ||
                         clarkName.endsWith("}version") ||
                         clarkName.endsWith("}default-validation") ||
                         clarkName.endsWith("}use-when"))) {
            return;
        }

        // allow standard attributes on an XSLT element

        if (elementURI.equals(NamespaceConstant.XSLT) &&
                (clarkName.equals("default-collation") ||
                         clarkName.equals("default-mode") ||
                         clarkName.equals("expand-text") ||
                         clarkName.equals("xpath-default-namespace") ||
                         clarkName.equals("extension-element-prefixes") ||
                         clarkName.equals("exclude-result-prefixes") ||
                         clarkName.equals("version") ||
                         clarkName.equals("default-validation") ||
                         clarkName.equals("use-when"))) {
            return;
        }

        if ("".equals(attributeURI) || NamespaceConstant.XSLT.equals(attributeURI)) {
            compileErrorInAttribute("Attribute " + Err.wrap(nc.getDisplayName(), Err.ATTRIBUTE) +
                                            " is not allowed on element " + Err.wrap(getDisplayName(), Err.ELEMENT),
                                    "XTSE0090", clarkName);
        }
    }

    /**
     * Check an attribute that is permitted under 3.0 but not under 2.0
     */

    public void check30attribute(String attName) throws XPathException {
        // no action
    }


    /**
     * Set the attribute list for the element. This is called to process the attributes (note
     * the distinction from processAttributes in the superclass).
     * Must be supplied in a subclass
     *
     * @throws net.sf.saxon.trans.XPathException if a static error is detected
     */

    protected abstract void prepareAttributes() throws XPathException;

    /**
     * Find the last child instruction of this instruction. Returns null if
     * there are no child instructions, or if the last child is a text node.
     *
     * @return the last child instruction, or null if there are no child instructions
     */

    protected StyleElement getLastChildInstruction() {
        StyleElement last = null;
        AxisIterator kids = iterateAxis(AxisInfo.CHILD);
        NodeInfo child;
        while ((child = kids.next()) != null) {
            if (child instanceof StyleElement) {
                last = (StyleElement) child;
            } else {
                last = null;
            }
        }
        return last;
    }

    /**
     * Compile an XPath expression in the context of this stylesheet element
     *
     * @param expression the source text of the XPath expression
     * @param attIndex   the index position of the attribute containing the XPath expression, or
     *                   -1 if the expression is in a text node
     * @return the compiled expression tree for the XPath expression
     * @throws net.sf.saxon.trans.XPathException if a static error is detected in the XPath expression
     */

    public Expression makeExpression(String expression, int attIndex) throws XPathException {
        try {
            StaticContext env = staticContext;
            if (attIndex >= 0) {
                StructuredQName attName = getAttributeList().getNodeName(attIndex).getStructuredQName();
                env = getStaticContext(attName);
            }
            return ExpressionTool.make(expression, env, 0, Token.EOF,
                                       getCompilation().getCompilerInfo().getCodeInjector());
        } catch (XPathException err) {
            err.maybeSetLocation(allocateLocation());
            if (err.isReportableStatically()) {
                compileError(err);
            }
            ErrorExpression erexp = new ErrorExpression(err);
            erexp.setRetainedStaticContext(makeRetainedStaticContext());
            erexp.setLocation(allocateLocation());
            return erexp;
        }
    }

    /**
     * Make a pattern in the context of this stylesheet element
     *
     * @param pattern the source text of the pattern
     * @return the compiled pattern
     * @throws net.sf.saxon.trans.XPathException if a static error is detected in the pattern
     */

    public Pattern makePattern(String pattern, String attributeName)
            throws XPathException {
        try {
            StaticContext env = getStaticContext(new StructuredQName("", "", attributeName));
            Pattern p = Pattern.make(pattern, env, getCompilation().getPackageData());
            p.setLocation(allocateLocation());
            return p;
        } catch (XPathException err) {
            if ("XPST0003".equals(err.getErrorCodeLocalPart())) {
                err.setErrorCode("XTSE0340");
            }
            compileError(err);
            NodeTestPattern nsp = new NodeTestPattern(AnyNodeTest.getInstance());
            nsp.setLocation(allocateLocation());
            return nsp;
        }
    }

    /**
     * Make an attribute value template in the context of this stylesheet element
     *
     * @param expression the source text of the attribute value template
     * @return a compiled XPath expression that computes the value of the attribute (including
     * concatenating the results of embedded expressions with any surrounding fixed text)
     * @throws net.sf.saxon.trans.XPathException if a static error is detected in the AVT
     */

    protected Expression makeAttributeValueTemplate(String expression, int attIndex) throws XPathException {
        StaticContext env = attIndex == -1 ?
                staticContext :
                getStaticContext(getAttributeList().getNodeName(attIndex).getStructuredQName());
        if (attIndex >= 0) {
            StructuredQName attName = getAttributeList().getNodeName(attIndex).getStructuredQName();
            env = getStaticContext(attName);
        }
        try {
            return AttributeValueTemplate.make(expression, env);
        } catch (XPathException err) {
            compileError(err);
            return new StringLiteral(expression);
        }
    }


    /**
     * Check the value of an attribute, as supplied statically
     *
     * @param name    the name of the attribute
     * @param value   the value of the attribute
     * @param avt     set to true if the value is permitted to be an attribute value template
     * @param allowed list of permitted values, which must be in alphabetical order
     * @throws net.sf.saxon.trans.XPathException if the value given for the attribute is not a permitted value
     */

    protected void checkAttributeValue(String name, String value, boolean avt, String[] allowed) throws XPathException {
        if (avt && value.contains("{")) {
            return;
        }
        if (Arrays.binarySearch(allowed, value) < 0) {
            FastStringBuffer sb = new FastStringBuffer(FastStringBuffer.C64);
            sb.append("Invalid value for ");
            sb.append("@");
            sb.append(name);
            sb.append(". Value must be one of (");
            for (int i = 0; i < allowed.length; i++) {
                sb.append(i == 0 ? "" : "|");
                sb.append(allowed[i]);
            }
            sb.append(")");
            compileError(sb.toString(), "XTSE0020");
        }
    }

    protected final static String[] YES_NO = new String[]{"0", "1", "false", "no", "true", "yes"};


    /**
     * Process an attribute whose value is yes, no, true, false, 1, or 0; returning true or false.
     *
     * @param name  the name of the attribute (used for diagnostics)
     * @param value the value of the attribute
     * @throws XPathException if the value is not one of the above
     */

    public boolean processBooleanAttribute(String name, String value) throws XPathException {
        String s = Whitespace.trim(value);
        if (isYes(s)) {
            return true;
        } else if (isNo(s)) {
            return false;
        } else {
            invalidAttribute(name, "yes|no | true|false | 1|0");
            return false; // never get here
        }

    }

    public static boolean isYes(String s) {
        return "yes".equals(s) || "true".equals(s) || "1".equals(s);
    }

    public static boolean isNo(String s) {
        return "no".equals(s) || "false".equals(s) || "0".equals(s);
    }

    /**
     * Process an attribute whose value is a SequenceType
     *
     * @param sequenceType the source text of the attribute
     * @return the processed sequence type
     * @throws XPathException if the syntax is invalid or for example if it refers to a type
     *                        that is not in the static context
     */

    public SequenceType makeSequenceType(String sequenceType)
            throws XPathException {
        getStaticContext();
        try {
            XPathParser parser =
                    getConfiguration().newExpressionParser("XP", false, 31);
            QNameParser qp = new QNameParser(staticContext.getNamespaceResolver());
            qp.setAcceptEQName(staticContext.getXPathVersion() >= 30);
            qp.setDefaultNamespace("");
            qp.setErrorOnBadSyntax("XPST0003");
            qp.setErrorOnUnresolvedPrefix("XPST0081");
            parser.setQNameParser(qp);
            return parser.parseSequenceType(sequenceType, staticContext);
        } catch (XPathException err) {
            compileError(err);
            // recovery path after reporting an error, e.g. undeclared namespace prefix
            return SequenceType.ANY_SEQUENCE;
        }
    }

    /**
     * Process the [xsl:]extension-element-prefixes attribute if there is one
     *
     * @param ns the namespace URI of the attribute - either the XSLT namespace or "" for the null namespace
     * @throws net.sf.saxon.trans.XPathException if the value of the attribute is invalid
     */

    protected void processExtensionElementAttribute(String ns)
            throws XPathException {
        String ext = getAttributeValue(ns, "extension-element-prefixes");
        if (ext != null) {
            // go round twice, once to count the values and next to add them to the array
            int count = 0;
            StringTokenizer st1 = new StringTokenizer(ext, " \t\n\r", false);
            while (st1.hasMoreTokens()) {
                st1.nextToken();
                count++;
            }
            extensionNamespaces = new String[count];
            count = 0;
            StringTokenizer st2 = new StringTokenizer(ext, " \t\n\r", false);
            while (st2.hasMoreTokens()) {
                String s = st2.nextToken();
                if ("#default".equals(s)) {
                    s = "";
                }
                String uri = getURIForPrefix(s, false);
                if (uri == null) {
                    extensionNamespaces = null;
                    compileError("Namespace prefix " + s + " is undeclared", "XTSE1430");
                } else if (NamespaceConstant.isReserved(uri)) {
                    compileWarning("Namespace " + uri + " is reserved: it cannot be used for extension instructions " +
                                           "(perhaps exclude-result-prefixes was intended).",
                                   SaxonErrorCode.SXWN9007);
                    extensionNamespaces[count++] = uri;
                } else {
                    extensionNamespaces[count++] = uri;
                }
            }
        }
    }

    /**
     * Process the [xsl:]exclude-result-prefixes attribute if there is one
     *
     * @param ns the namespace URI of the attribute required, either the XSLT namespace or ""
     * @throws net.sf.saxon.trans.XPathException if the value of the attribute is invalid
     */

    protected void processExcludedNamespaces(String ns)
            throws XPathException {
        String ext = getAttributeValue(ns, "exclude-result-prefixes");
        if (ext != null) {
            if ("#all".equals(Whitespace.trim(ext))) {
                Iterator<NamespaceBinding> codes = NamespaceIterator.iterateNamespaces(this);
                List<String> excluded = new ArrayList<String>();
                while (codes.hasNext()) {
                    excluded.add(codes.next().getURI());
                }
                excludedNamespaces = excluded.toArray(new String[excluded.size()]);
            } else {
                // go round twice, once to count the values and next to add them to the array
                int count = 0;
                StringTokenizer st1 = new StringTokenizer(ext, " \t\n\r", false);
                while (st1.hasMoreTokens()) {
                    st1.nextToken();
                    count++;
                }
                excludedNamespaces = new String[count];
                count = 0;
                StringTokenizer st2 = new StringTokenizer(ext, " \t\n\r", false);
                while (st2.hasMoreTokens()) {
                    String s = st2.nextToken();
                    if ("#default".equals(s)) {
                        s = "";
                    } else if ("#all".equals(s)) {
                        compileError("In exclude-result-prefixes, cannot mix #all with other values", "XTSE0020");
                    }
                    String uri = getURIForPrefix(s, true);
                    if (uri == null) {
                        excludedNamespaces = null;
                        compileError("Namespace prefix " + s + " is not declared", "XTSE0808");
                        break;
                    }
                    excludedNamespaces[count++] = uri;
                    if (s.isEmpty() && uri.isEmpty()) {
                        compileError("Cannot exclude the #default namespace when no default namespace is declared",
                                     "XTSE0809");
                    }
                }
            }
        }
    }

    /**
     * Process the [xsl:]version attribute if there is one
     *
     * @param ns the namespace URI of the attribute required, either the XSLT namespace or ""
     * @throws net.sf.saxon.trans.XPathException if the value of the attribute is invalid
     */

    protected void processVersionAttribute(String ns) throws XPathException {
        String v = Whitespace.trim(getAttributeValue(ns, "version"));
        if (v != null) {
            ConversionResult val = BigDecimalValue.makeDecimalValue(v, true);
            if (val instanceof ValidationFailure) {
                version = 30;
                compileError("The version attribute must be a decimal literal", "XTSE0110");
            } else {
                // Note this will normalize the decimal so that trailing spaces are not significant
                version = ((BigDecimalValue) val).getDecimalValue().multiply(BigDecimal.TEN).intValue();
                if (version < 20 && version != 10) {
                    // XSLT 2.0 says use backwards compatible mode. XSLT 3.0 says we can raise an error.
                    // Both allow a warning
                    issueWarning("Unrecognized version " + val + ": treated as 1.0", this);
                    version = 10;
                } else if (version > 20 && version < 30) {
                    issueWarning("Unrecognized version " + val + ": treated as 2.0", this);
                    version = 20;
                }
            }
        }
    }

    /**
     * Get the numeric value of the version number appearing as an attribute on this element,
     * or inherited from its ancestors
     *
     * @return the version number times ten as an integer
     */

    public int getEffectiveVersion() {
        if (version == -1) {
            NodeInfo node = getParent();
            if (node instanceof StyleElement) {
                version = ((StyleElement) node).getEffectiveVersion();
            } else {
                return 20;    // defensive programming
            }
        }
        return version;
    }

    /**
     * Get the effective version in a form suitable for display (for example "1.0" or "2.0")
     * @return the version number in conventional format
     */

    public String getEffectiveVersionAsString() {
        return Double.toString(getEffectiveVersion()/10);
    }

    /**
     * Validate the value of the [xsl:]validation attribute
     *
     * @param value the raw value of the attribute
     * @return the encoded value of the attribute
     * @throws XPathException if the attribute value is invalid
     */

    protected int validateValidationAttribute(String value) throws XPathException {
        int code = Validation.getCode(value);
        if (code == Validation.INVALID) {
            String prefix = this instanceof LiteralResultElement ? "xsl:" : "";
            compileError("Invalid value of " + prefix + "validation attribute", "XTSE0020");
            code = getDefaultValidation();
        }
        if (!isSchemaAware()) {
            if (code == Validation.STRICT) {
                compileError("To perform validation, a schema-aware XSLT processor is needed", "XTSE1660");
            }
            code = Validation.STRIP;
        }
        return code;
    }

    /**
     * Determine whether forwards-compatible mode is enabled for this element
     *
     * @return true if forwards-compatible mode is enabled
     */

    public boolean forwardsCompatibleModeIsEnabled() {
        return getEffectiveVersion() > 30;
    }

    /**
     * Determine whether 1.0-compatible mode is enabled for this element
     *
     * @return true if 1.0 compatable mode is enabled, that is, if this or an enclosing
     * element specifies an [xsl:]version attribute whose value is less than 2.0
     */

    public boolean xPath10ModeIsEnabled() {
        return getEffectiveVersion() < 20;
    }

    /**
     * Process the [xsl:]default-collation attribute if there is one
     *
     * @throws net.sf.saxon.trans.XPathException if the value is not a valid URI, or not a recognized collation URI
     */

    protected void processDefaultCollationAttribute() throws XPathException {
        String ns = getURI().equals(NamespaceConstant.XSLT) ? "" : NamespaceConstant.XSLT;
        String v = getAttributeValue(ns, "default-collation");
        if (v != null) {
            StringTokenizer st = new StringTokenizer(v, " \t\n\r", false);
            while (st.hasMoreTokens()) {
                String uri = st.nextToken();
                if (uri.equals(NamespaceConstant.CODEPOINT_COLLATION_URI)) {
                    defaultCollationName = uri;
                    return;
                } else if (uri.startsWith("http://saxon.sf.net/")) {
                    defaultCollationName = uri;
                    return;
                } else {
                    URI collationURI;
                    try {
                        collationURI = new URI(uri);
                        if (!collationURI.isAbsolute()) {
                            URI base = new URI(getBaseURI());
                            collationURI = base.resolve(collationURI);
                            uri = collationURI.toString();
                        }
                    } catch (URISyntaxException err) {
                        compileError("default collation '" + uri + "' is not a valid URI");
                        uri = NamespaceConstant.CODEPOINT_COLLATION_URI;
                    }

                    if (uri.startsWith("http://saxon.sf.net/")) {
                        defaultCollationName = uri;
                        return;
                    }

                    if (uri.startsWith("http://www.w3.org/2013/collation/UCA")) {
                        defaultCollationName = uri;
                        return;
                    }

                    if (getConfiguration().getCollation(uri) != null) {
                        defaultCollationName = uri;
                        return;
                    }

                }
                // if not recognized, try the next URI in order
            }
            compileErrorInAttribute("No recognized collation URI found in default-collation attribute", "XTSE0125",
                                    new StructuredQName("", ns, "default-collation").getClarkName());
        }
    }

    /**
     * Get the default collation for this stylesheet element. If no default collation is
     * specified in the stylesheet, return the Unicode codepoint collation name.
     *
     * @return the name of the default collation
     */

    protected String getDefaultCollationName() {
        StyleElement e = this;
        while (true) {
            if (e.defaultCollationName != null) {
                return e.defaultCollationName;
            }
            NodeInfo p = e.getParent();
            if (!(p instanceof StyleElement)) {
                break;
            }
            e = (StyleElement) p;
        }
        return getConfiguration().getDefaultCollationName();
    }

    /**
     * Find a named collation. Note this method should only be used at compile-time, before declarations
     * have been pre-processed. After that time, use getCollation().
     *
     * @param name    identifies the name of the collation required
     * @param baseURI the base URI to be used for resolving the collation name if it is relative
     * @return null if the collation is not found
     * @throws XPathException if either URI is invalid as a URI
     */

    public StringCollator findCollation(String name, String baseURI) throws XPathException {
        return getConfiguration().getCollation(name, baseURI);
    }

    /**
     * Process the [xsl:]default-mode attribute if there is one
     *
     * @throws net.sf.saxon.trans.XPathException if the value is not a valid EQName, or the token #unnamed
     */

    protected void processDefaultMode() throws XPathException {
        String ns = getURI().equals(NamespaceConstant.XSLT) ? "" : NamespaceConstant.XSLT;
        String v = getAttributeValue(ns, "default-mode");
        if (v != null) {
            if (v.equals("#unnamed")) {
                defaultMode = Mode.UNNAMED_MODE_NAME;
            } else {
                try {
                    defaultMode = makeQName(v); // should check that this is actually a mode on a template
                } catch (NamespaceException e) {
                    compileError(e.getMessage(), "XTSE0280");
                }
            }
        }
    }

    /**
     * Get the default mode for this stylesheet element.
     *
     * @return the name of the default mode
     */

    public StructuredQName getDefaultMode() throws XPathException {
        if (defaultMode == null) {
            processDefaultMode();
            if (defaultMode == null) {
                NodeInfo p = getParent();
                if (p instanceof StyleElement) {
                    return defaultMode = ((StyleElement) p).getDefaultMode();
                } else {
                    return defaultMode = Mode.UNNAMED_MODE_NAME;
                }
            }
        }
        return defaultMode;
    }


    /**
     * Check whether a particular extension element namespace is defined on this node.
     * This checks this node only, not the ancestor nodes.
     * The implementation checks whether the prefix is included in the
     * [xsl:]extension-element-prefixes attribute.
     *
     * @param uri the namespace URI being tested
     * @return true if this namespace is defined on this element as an extension element namespace
     */

    protected boolean definesExtensionElement(String uri) {
        if (extensionNamespaces == null) {
            return false;
        }
        for (String extensionNamespace : extensionNamespaces) {
            if (extensionNamespace.equals(uri)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check whether a namespace uri defines an extension element. This checks whether the
     * namespace is defined as an extension namespace on this or any ancestor node.
     *
     * @param uri the namespace URI being tested
     * @return true if the URI is an extension element namespace URI
     */

    public boolean isExtensionNamespace(String uri) {
        NodeInfo anc = this;
        while (anc instanceof StyleElement) {
            if (((StyleElement) anc).definesExtensionElement(uri)) {
                return true;
            }
            anc = anc.getParent();
        }
        return false;
    }

    /**
     * Check whether this node excludes a particular namespace from the result.
     * This method checks this node only, not the ancestor nodes.
     *
     * @param uri the namespace URI being tested
     * @return true if the namespace is excluded by virtue of an [xsl:]exclude-result-prefixes attribute
     */

    protected boolean definesExcludedNamespace(String uri) {
        if (excludedNamespaces == null) {
            return false;
        }
        for (String excludedNamespace : excludedNamespaces) {
            if (excludedNamespace.equals(uri)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check whether a namespace uri defines an namespace excluded from the result.
     * This checks whether the namespace is defined as an excluded namespace on this
     * or any ancestor node.
     *
     * @param uri the namespace URI being tested
     * @return true if this namespace URI is a namespace excluded by virtue of exclude-result-prefixes
     * on this element or on an ancestor element
     */

    public boolean isExcludedNamespace(String uri) {
        if (uri.equals(NamespaceConstant.XSLT) || uri.equals(NamespaceConstant.XML)) {
            return true;
        }
        if (isExtensionNamespace(uri)) {
            return true;
        }
        NodeInfo anc = this;
        while (anc instanceof StyleElement) {
            if (((StyleElement) anc).definesExcludedNamespace(uri)) {
                return true;
            }
            anc = anc.getParent();
        }
        return false;
    }

    /**
     * Process the [xsl:]xpath-default-namespace attribute if there is one
     *
     * @param ns the namespace URI of the attribute required  (the default namespace or the XSLT namespace.)
     */

    protected void processDefaultXPathNamespaceAttribute(String ns) {
        String v = getAttributeValue(ns, "xpath-default-namespace");
        if (v != null) {
            defaultXPathNamespace = v;
        }
    }

    /**
     * Get the default XPath namespace for elements and types
     *
     * @return the default namespace for elements and types.
     * Return {@link NamespaceConstant#NULL} for the non-namespace
     */

    public String getDefaultXPathNamespace() {
        NodeInfo anc = this;
        while (anc instanceof StyleElement) {
            String x = ((StyleElement) anc).defaultXPathNamespace;
            if (x != null) {
                return x;
            }
            anc = anc.getParent();
        }
        return NamespaceConstant.NULL;
        // indicates that the default namespace is the null namespace
    }

    /**
     * Process the [xsl:]expand-text attribute if there is one (and if XSLT 3.0 is enabled)
     *
     * @param ns the namespace URI of the attribute required  (the default namespace or the XSLT namespace.)
     * @throws XPathException if the value of the attribute is invalid
     */

    protected void processExpandTextAttribute(String ns) throws XPathException {
        String v = getAttributeValue(ns, "expand-text");
        if (v != null) {
            expandText = processBooleanAttribute("expand-text", v);
        } else {
            NodeInfo parent = getParent();
            expandText = parent instanceof StyleElement && ((StyleElement) parent).expandText;
        }
    }

    /**
     * Process the [xsl:]expand-text attribute if there is one
     *
     * @param ns the namespace URI of the attribute required  (the default namespace or the XSLT namespace.)
     * @throws XPathException if the value of the attribute is invalid
     */

    protected void processDefaultValidationAttribute(String ns) throws XPathException {
        String v = getAttributeValue(ns, "default-validation");
        if (v != null) {
            int val = Validation.getCode(v);
            if (val == Validation.STRIP || val == Validation.PRESERVE) {
                defaultValidation = val;
            } else {
                compileErrorInAttribute("@default-validation must be preserve|strip", "XTSE1660", "default-validation");
            }
        }
    }

    /**
     * Ask whether content value templates are available within this element
     *
     * @return true if content value templates are enabled
     */

    public boolean isExpandingText() {
        return expandText;
    }


    /**
     * Get the Schema type definition for a type named in the stylesheet (in a
     * "type" attribute).
     *
     * @param typeAtt the value of the type attribute
     * @return the corresponding schema type
     * @throws XPathException if the type is not declared in an
     *                        imported schema, or is not a built-in type
     */

    public SchemaType getSchemaType(String typeAtt) throws XPathException {
        try {
            String uri;
            String lname;
            if (typeAtt.startsWith("Q{")) {
                try {
                    StructuredQName q = makeQName(typeAtt);
                    uri = q.getURI();
                    lname = q.getLocalPart();
                } catch (NamespaceException e) {
                    // should not happen
                    throw new XPathException(e);
                }

            } else {
                String[] parts = NameChecker.getQNameParts(typeAtt);
                lname = parts[1];
                if ("".equals(parts[0])) {
                    // Name is unprefixed: use the default-xpath-namespace
                    uri = getDefaultXPathNamespace();
                } else {
                    uri = getURIForPrefix(parts[0], false);
                    if (uri == null) {
                        compileError("Namespace prefix for type annotation is undeclared", "XTSE1520");
                        return null;
                    }
                }
            }
            if (uri.equals(NamespaceConstant.SCHEMA)) {
                SchemaType t = BuiltInType.getSchemaTypeByLocalName(lname);
                if (t == null) {
                    compileError("Unknown built-in type " + typeAtt, "XTSE1520");
                    return null;
                }
                return t;
            }

            // not a built-in type: look in the imported schemas

            if (!getPrincipalStylesheetModule().isImportedSchema(uri)) {
                compileError("There is no imported schema for the namespace of type " + typeAtt, "XTSE1520");
                return null;
            }
            StructuredQName qName = new StructuredQName("", uri, lname);
            SchemaType stype = getConfiguration().getSchemaType(qName);
            if (stype == null) {
                compileError("There is no type named " + typeAtt + " in an imported schema", "XTSE1520");
            }
            return stype;

        } catch (QNameException err) {
            compileError("Invalid type name. " + err.getMessage(), "XTSE1520");
        }
        return null;
    }

    /**
     * Get the type annotation to use for a given schema type
     *
     * @param schemaType the schema type
     * @return the corresponding numeric type annotation
     */

    public SimpleType getTypeAnnotation(SchemaType schemaType) {
        return (SimpleType) schemaType;
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
    }

    /**
     * Hook to allow additional validation of a parent element immediately after its
     * children have been validated.
     *
     * @throws XPathException if any error is found during post-traversal validation
     */

    public void postValidate() throws XPathException {
    }

    /**
     * Method supplied by declaration elements to add themselves to a stylesheet-level index
     *
     * @param decl the Declaration being indexed. (This corresponds to the StyleElement object
     *             except in cases where one module is imported several times with different precedence.)
     * @param top  represents the outermost XSLStylesheet or XSLPackage element
     * @throws XPathException if any error is encountered
     */

    public void index(ComponentDeclaration decl, PrincipalStylesheetModule top) throws XPathException {
    }

    /**
     * Type-check an expression. This is called to check each expression while the containing
     * instruction is being validated. It is not just a static type-check, it also adds code
     * to perform any necessary run-time type checking and/or conversion.
     *
     * @param name the name of the attribute containing the expression to be checked (used for diagnostics)
     * @param exp  the expression to be checked
     * @return the (possibly rewritten) expression after type checking
     * @throws XPathException if type-checking fails statically, that is, if it can be determined that the
     *                        supplied value for the expression cannot possibly be of the required type
     */

    // Note: the typeCheck() call is done at the level of individual path expression; the optimize() call is done
    // for a template or function as a whole. We can't do it all at the function/template level because
    // the static context (e.g. namespaces) changes from one XPath expression to another.
    public Expression typeCheck(String name, Expression exp) throws XPathException {

        if (exp == null) {
            return null;
        }
        Configuration config = getConfiguration();
        if (config.getBooleanProperty(FeatureKeys.STRICT_STREAMABILITY)) {
            return exp;
        }
        try {
            exp = exp.typeCheck(makeExpressionVisitor(), config.makeContextItemStaticInfo(Type.ITEM_TYPE, true));
            exp = ExpressionTool.resolveCallsToCurrentFunction(exp);
            //            if (explaining) {
            //                System.err.println("Attribute '" + name + "' of element '" + getDisplayName() + "' at line " + getLineNumber() + ':');
            //                System.err.println("Static type: " +
            //                        SequenceType.makeSequenceType(exp.getItemType(), exp.getCardinality()));
            //                System.err.println("Optimized expression tree:");
            //                exp.display(10, getNamePool(), System.err);
            //            }
            CodeInjector injector = getCompilation().getCompilerInfo().getCodeInjector();
            if (injector != null) {
                return injector.inject(exp, getStaticContext(), LocationKind.XPATH_IN_XSLT, new StructuredQName("", "", name));
            }
            return exp;
        } catch (XPathException err) {
            // we can't report a dynamic error such as divide by zero unless the expression
            // is actually executed.
            //err.printStackTrace();
            if (err.isReportableStatically()) {
                err.setLocation(new AttributeLocation(this, StructuredQName.fromClarkName(name)));
                compileError(err);
                return exp;
            } else {
                ErrorExpression erexp = new ErrorExpression(err);
                erexp.setLocation(allocateLocation());
                return erexp;
            }
        }
    }

    /**
     * Allocate slots in the local stack frame to range variables used in an XPath expression
     *
     * @param exp the XPath expression for which slots are to be allocated
     */

    public void allocateLocalSlots(Expression exp) {
        SlotManager slotManager = getContainingSlotManager();
        if (slotManager == null) {
            throw new AssertionError("Slot manager has not been allocated");
        } else {
            int firstSlot = slotManager.getNumberOfVariables();
            int highWater = ExpressionTool.allocateSlots(exp, firstSlot, slotManager);
            if (highWater > firstSlot) {
                slotManager.setNumberOfVariables(highWater);
                // This algorithm is not very efficient because it never reuses
                // a slot when a variable goes out of scope. But at least it is safe.
                // Note that range variables within XPath expressions need to maintain
                // a slot until the instruction they are part of finishes, e.g. in
                // xsl:for-each.
            }
        }
    }

    /**
     * Type-check a pattern. This is called to check each pattern while the containing
     * instruction is being validated. It is not just a static type-check, it also adds code
     * to perform any necessary run-time type checking and/or conversion.
     *
     * @param name    the name of the attribute holding the pattern, for example "match": used in
     *                diagnostics
     * @param pattern the compiled pattern
     * @return the original pattern, or a substitute pattern if it has been rewritten. Returns null
     * if and only if the supplied pattern is null.
     * @throws net.sf.saxon.trans.XPathException if the pattern fails optimistic static type-checking
     */

    public Pattern typeCheck(String name, Pattern pattern) throws XPathException {
        if (pattern == null) {
            return null;
        }
        try {
            ItemType cit = Type.ITEM_TYPE;
            pattern = pattern.typeCheck(makeExpressionVisitor(), getConfiguration().makeContextItemStaticInfo(cit, true));
            boolean usesCurrent = false;

            for (Operand o : pattern.operands()) {
                Expression filter = o.getChildExpression();
                if (ExpressionTool.callsFunction(filter, Current.FN_CURRENT, false)) {
                    usesCurrent = true;
                    break;
                }
            }
            if (usesCurrent) {
                PatternThatSetsCurrent p2 = new PatternThatSetsCurrent(pattern);
                pattern.bindCurrent(p2.getCurrentBinding());
                pattern = p2;
            }

            return pattern;
        } catch (XPathException err) {
            // we can't report a dynamic error such as divide by zero unless the pattern
            // is actually executed. We don't have an error pattern available, so we
            // construct one
            if (err.isReportableStatically()) {
                XPathException e2 = new XPathException("Error in " + name + " pattern", err);
                e2.setLocator(this);
                e2.setErrorCodeQName(err.getErrorCodeQName());
                throw e2;
            } else {
                Pattern p = new BasePatternWithPredicate(
                        new NodeTestPattern(ErrorType.getInstance()), new ErrorExpression(err));
                p.setLocation(allocateLocation());
                return p;
            }
        }
    }

    /**
     * Fix up references from XPath expressions. Overridden for function declarations
     * and variable declarations
     *
     * @throws net.sf.saxon.trans.XPathException if any references cannot be fixed up.
     */

    public void fixupReferences() throws XPathException {
        AxisIterator kids = iterateAxis(AxisInfo.CHILD);
        NodeInfo child;
        while ((child = kids.next()) != null) {
            if (child instanceof StyleElement) {
                ((StyleElement) child).fixupReferences();
            }
        }
    }

    /**
     * Get the SlotManager for the containing Procedure definition
     *
     * @return the SlotManager associated with the containing Function, Template, etc,
     * or null if there is no such containing Function, Template etc.
     */

    public SlotManager getContainingSlotManager() {
        NodeImpl node = this;
        while (true) {
            NodeImpl next = node.getParent();
            if (next instanceof XSLModuleRoot || next.getFingerprint() == StandardNames.XSL_OVERRIDE) {
                if (node instanceof StylesheetComponent) {
                    return ((StylesheetComponent) node).getSlotManager();
                } else {
                    return null;
                }
            }
            node = next;
        }
    }


    /**
     * Recursive walk through the stylesheet to validate all nodes
     *
     * @param decl              the declaration to be validated
     * @param excludeStylesheet true if the XSLStylesheet element is to be excluded
     * @throws XPathException if validation fails
     */

    public void validateSubtree(ComponentDeclaration decl, boolean excludeStylesheet) throws XPathException {
        if (isActionCompleted(StyleElement.ACTION_VALIDATE)) {
            return;
        }
        setActionCompleted(StyleElement.ACTION_VALIDATE);
        if (validationError != null) {
            if (reportingCircumstances == REPORT_ALWAYS) {
                compileError(validationError);
            } else if (reportingCircumstances == REPORT_UNLESS_FORWARDS_COMPATIBLE
                    && !forwardsCompatibleModeIsEnabled()) {
                compileError(validationError);
            } else if (reportingCircumstances == REPORT_STATICALLY_UNLESS_FALLBACK_AVAILABLE) {
//                if (!forwardsCompatibleModeIsEnabled()) {
//                    compileError(validationError);
//                } else {
                    boolean hasFallback = false;
                    AxisIterator kids = iterateAxis(AxisInfo.CHILD);
                    NodeInfo child;
                    while ((child = kids.next()) != null) {
                        if (child instanceof XSLFallback) {
                            hasFallback = true;
                            ((XSLFallback) child).validateSubtree(decl, false);
                        }
                    }
                    if (!hasFallback) {
                        compileError(validationError);
                    }
//                }
            } else if (reportingCircumstances == REPORT_DYNAMICALLY_UNLESS_FALLBACK_AVAILABLE) {
                AxisIterator kids = iterateAxis(AxisInfo.CHILD);
                NodeInfo child;
                while ((child = kids.next()) != null) {
                    if (child instanceof XSLFallback) {
                        ((XSLFallback) child).validateSubtree(decl, false);
                    }
                }
            }
        } else {
            try {
                validate(decl);
            } catch (XPathException err) {
                compileError(err);
            }
            validateChildren(decl, excludeStylesheet);
            if (getCompilation().getErrorCount() == 0) {
                postValidate();
            }
        }
    }

    /**
     * Validate the children of this node, recursively. Overridden for top-level
     * data elements.
     *
     * @param decl              the declaration whose children are to be validated
     * @param excludeStylesheet true if the xsl:stylesheet element is to be excluded
     * @throws XPathException if validation fails
     */

    protected void validateChildren(ComponentDeclaration decl, boolean excludeStylesheet) throws XPathException {
        boolean containsInstructions = mayContainSequenceConstructor();
        AxisIterator kids = iterateAxis(AxisInfo.CHILD);
        StyleElement lastChild = null;
        boolean endsWithTextTemplate = false;
        NodeInfo child;
        while ((child = kids.next()) != null) {
            if (child instanceof StyleElement) {
                if (!(excludeStylesheet && child instanceof XSLStylesheet)) {
                    endsWithTextTemplate = false;
                    if (containsInstructions && !((StyleElement) child).isInstruction()
                            && !isPermittedChild((StyleElement) child)) {
                        ((StyleElement) child).compileError("An " + getDisplayName() + " element must not contain an " +
                                                                    child.getDisplayName() + " element", "XTSE0010");
                    }
                    ((StyleElement) child).validateSubtree(decl, excludeStylesheet);
                    lastChild = (StyleElement) child;
                }
            } else {
                endsWithTextTemplate = examineTextNode(child);
            }
        }
        if (lastChild instanceof XSLLocalVariable &&
                !(this instanceof XSLStylesheet) && !endsWithTextTemplate) {
            lastChild.compileWarning("A variable with no following sibling instructions has no effect",
                                     SaxonErrorCode.SXWN9001);
        }
    }

    /**
     * Examine a text node in the stylesheet to see if it is a text value template
     *
     * @param node the text node
     * @throws XPathException if the node is is a text value template with variable content
     */

    private boolean examineTextNode(NodeInfo node) throws XPathException {
        if (node instanceof TextValueTemplateNode) {
            ((TextValueTemplateNode) node).validate();
            return !(((TextValueTemplateNode) node).getContentExpression() instanceof Literal);
        } else {
            return false;
        }
    }

    /**
     * Check whether a given child is permitted for this element. This method is used when a non-instruction
     * child element such as xsl:sort is encountered in a context where instructions would normally be expected.
     *
     * @param child the child that may or may not be permitted
     * @return true if the child is permitted.
     */

    protected boolean isPermittedChild(StyleElement child) {
        return false;
    }

    /**
     * Get the principal stylesheet module of the package in which
     * this XSLT element appears
     *
     * @return the containing package
     */

    public PrincipalStylesheetModule getPrincipalStylesheetModule() {
        return getCompilation().getPrincipalStylesheetModule();
    }

    /**
     * Get the containing package (the principal stylesheet module of the package in which
     * this XSLT element appears)
     *
     * @return the containing package
     */

    public StylesheetPackage getContainingPackage() {
        return getPrincipalStylesheetModule().getStylesheetPackage();
    }

    /**
     * Check that among the children of this element, any xsl:sort elements precede any other elements
     *
     * @param sortRequired true if there must be at least one xsl:sort element
     * @throws XPathException if invalid
     */

    protected void checkSortComesFirst(boolean sortRequired) throws XPathException {
        AxisIterator kids = iterateAxis(AxisInfo.CHILD);
        boolean sortFound = false;
        boolean nonSortFound = false;
        NodeInfo child;
        while ((child = kids.next()) != null) {
            if (child instanceof XSLSort) {
                if (nonSortFound) {
                    ((XSLSort) child).compileError("Within " + getDisplayName() +
                                                           ", xsl:sort elements must come before other instructions", "XTSE0010");
                }
                sortFound = true;
            } else if (child.getNodeKind() == Type.TEXT) {
                // with xml:space=preserve, white space nodes may still be there
                if (!Whitespace.isWhite(child.getStringValueCS())) {
                    nonSortFound = true;
                }
            } else {
                nonSortFound = true;
            }
        }
        if (sortRequired && !sortFound) {
            compileError(getDisplayName() + " must have at least one xsl:sort child", "XTSE0010");
        }
    }

    /**
     * Convenience method to check that the stylesheet element is at the top level (that is,
     * as a child of xsl:stylesheet or xsl:transform)
     *
     * @param errorCode     the error to throw if it is not at the top level; defaults to XTSE0010
     *                      if the value is null
     * @param allowOverride true if the element is allowed to appear as a child of xsl:override
     * @throws XPathException if not at top level
     */

    public void checkTopLevel(/*@NotNull*/ String errorCode, boolean allowOverride) throws XPathException {
        if (getParent().getFingerprint() == StandardNames.XSL_OVERRIDE) {
            if (!allowOverride) {
                compileError("Element " + getDisplayName() + " is not allowed as a child of xsl:override");
            }
        } else if (!isTopLevel()) {
            compileError("Element " + getDisplayName() + " must be top-level (a child of xsl:stylesheet, xsl:transform, or xsl:package)", errorCode);
        }
    }

    /**
     * Convenience method to check that the stylesheet element is empty
     *
     * @throws XPathException if it is not empty
     */

    public void checkEmpty() throws XPathException {
        if (hasChildNodes()) {
            compileError("Element must be empty", "XTSE0260");
        }
    }

    /**
     * Convenience method to report the absence of a mandatory attribute
     *
     * @param attribute the name of the attribute whose absence is to be reported
     * @throws XPathException if the attribute is missing
     */

    public void reportAbsence(String attribute)
            throws XPathException {
        compileError("Element must have an @" + attribute + " attribute", "XTSE0010");
    }


    /**
     * Compile the instruction on the stylesheet tree into an executable instruction
     * for use at run-time.
     *
     * @param compilation the compilation episode
     * @param decl        the containing top-level declaration, for example xsl:function or xsl:template
     * @return either a ComputedExpression, or null. The value null is returned when compiling an instruction
     * that returns a no-op, or when compiling a top-level object such as an xsl:template that compiles
     * into something other than an instruction.
     * @throws net.sf.saxon.trans.XPathException if validation fails
     */

    public Expression compile(Compilation compilation, ComponentDeclaration decl) throws XPathException {
        // no action: default for non-instruction elements
        return null;
    }

    protected boolean isWithinDeclaredStreamableConstruct() {
        String streamable = getAttributeValue("streamable");
        if (streamable != null) {
            streamable = Whitespace.trim(streamable);
            if ("yes".equals(streamable) || "1".equals(streamable) || "true".equals(streamable)) {
                return true;
            }
        }
        ;
        NodeInfo parent = getParent();
        return parent instanceof StyleElement && ((StyleElement) parent).isWithinDeclaredStreamableConstruct();
    }

    protected String generateId() {
        FastStringBuffer buff = new FastStringBuffer(FastStringBuffer.C16);
        generateId(buff);
        return buff.toString();
    }

    /**
     * Compile a declaration in the stylesheet tree
     * for use at run-time.
     *
     * @param compilation the compilation episode
     * @param decl        the containing top-level declaration, for example xsl:function or xsl:template
     * @throws net.sf.saxon.trans.XPathException if compilation fails
     */

    public void compileDeclaration(Compilation compilation, ComponentDeclaration decl) throws XPathException {
        // no action: default for elements that are not declarations
    }

    /**
     * Compile the children of this instruction on the stylesheet tree, adding the
     * subordinate instructions to the parent instruction on the execution tree.
     *
     * @param compilation   the Executable
     * @param decl          the Declaration of the containing top-level stylesheet element
     * @param includeParams true if xsl:param elements are to be treated as child instructions (true
     *                      for templates but not for functions)
     * @return the compiled sequence constructor
     * @throws net.sf.saxon.trans.XPathException if compilation fails
     */

    public Expression compileSequenceConstructor(Compilation compilation, ComponentDeclaration decl,
                                                 boolean includeParams)
            throws XPathException {
        // If there are any xsl:on-empty or xsl:on-non-empty children, then reorder the children so
        // that local variable declarations come first. This is necessary to ensure that the instructions
        // remain part of a single "block", since the containing block affects the semantics of
        // on-empty and on-non-empty. Moving variables to come first would probably be a safe strategy in all
        // cases, but there might be a performance disadvantage in some cases, and it's unnecessarily disruptive,
        // especially if there are calls on user extension functions having side-effects.
        // Note: we have already bound variable references to their declarations at this stage, so the reordering
        // does not change the scope of variables.
        // We also move any on-empty instructions to the end of the list, since this makes streaming easier.
        boolean containsEmptyTest = false;
        boolean containsLocalVariable = false;
        AxisIterator kids = iterateAxis(AxisInfo.CHILD);
        NodeInfo child;
        while ((child = kids.next()) != null) {
            int fp = ((NodeImpl) child).getFingerprint();
            if (fp == StandardNames.XSL_VARIABLE) {
                containsLocalVariable = true;
            }
            if (fp == StandardNames.XSL_ON_EMPTY || fp == StandardNames.XSL_ON_NON_EMPTY) {
                containsEmptyTest = true;
            }
        }
        if (containsEmptyTest) {
            List<NodeInfo> vars = new ArrayList<NodeInfo>();
            List<NodeInfo> onEmpties = new ArrayList<NodeInfo>();
            List<NodeInfo> others = new ArrayList<NodeInfo>();
            kids = iterateAxis(AxisInfo.CHILD);
            while ((child = kids.next()) != null) {
                int fp = ((NodeImpl) child).getFingerprint();
                if (fp == StandardNames.XSL_VARIABLE || fp == StandardNames.XSL_PARAM) {
                    vars.add(child);
                } else if (fp == StandardNames.XSL_ON_EMPTY) {
                    onEmpties.add(child);
                } else {
                    others.add(child);
                }
            }
            vars.addAll(others);
            vars.addAll(onEmpties);
            return compileSequenceConstructor(compilation, decl, new ListIterator(vars), includeParams);
        } else {
            return compileSequenceConstructor(compilation, decl, iterateAxis(AxisInfo.CHILD), includeParams);
        }
    }


    /**
     * Compile the children of this instruction on the stylesheet tree, adding the
     * subordinate instructions to the parent instruction on the execution tree.
     *
     * @param compilation   the Executable
     * @param decl          the Declaration of the containing top-level stylesheet element
     * @param iter          Iterator over the children. This is used in the case where there are children
     *                      that are not part of the sequence constructor, for example the xsl:sort children of xsl:for-each;
     *                      the iterator can be positioned past such elements.
     * @param includeParams true if xsl:param elements are to be treated as child instructions (true
     *                      for templates but not for functions)
     * @return the compiled sequence constructor
     * @throws net.sf.saxon.trans.XPathException if compilation fails
     */

    public Expression compileSequenceConstructor(Compilation compilation, ComponentDeclaration decl,
                                                 SequenceIterator iter, boolean includeParams)
            throws XPathException {

        Location locationId = allocateLocation();
        List<Expression> contents = new ArrayList<Expression>(10);
        boolean containsSpecials = false;
        NodeInfo node;
        while ((node = (NodeInfo) iter.next()) != null) {
            if (node.getNodeKind() == Type.TEXT) {
                if (isExpandingText()) {
                    compileContentValueTemplate((TextImpl) node, contents);
                } else {
                    // handle literal text nodes by generating an xsl:value-of instruction, unless expand-text is enabled
                    AxisIterator lookahead = node.iterateAxis(AxisInfo.FOLLOWING_SIBLING);
                    NodeInfo sibling = lookahead.next();
                    if (!(sibling instanceof XSLLocalParam || sibling instanceof XSLSort)) {
                        // The test for XSLParam and XSLSort is to eliminate whitespace nodes that have been retained
                        // because of xml:space="preserve"
                        Expression text = new ValueOf(new StringLiteral(node.getStringValue()), false, false);
                        text.setLocation(allocateLocation());

                        CodeInjector injector = getCompilation().getCompilerInfo().getCodeInjector();
                        if (injector != null) {
                            Expression tracer = injector.inject(text, getStaticContext(), StandardNames.XSL_TEXT, null);
                            tracer.setLocation(text.getLocation());
                            text = tracer;
                        }

                        contents.add(text);
                    }
                }

            } else if (node instanceof XSLLocalVariable) {
                XSLLocalVariable var = (XSLLocalVariable) node;
                SourceBinding sourceBinding = var.getSourceBinding();
                var.compileLocalVariable(compilation, decl);

                Expression tail = compileSequenceConstructor(compilation, decl, iter, includeParams);
                if (tail == null || Literal.isEmptySequence(tail)) {
                    // this doesn't happen, because if there are no instructions following
                    // a variable, we'll have taken the var==null path above
                    //return result;
                } else {
                    LetExpression let = new LetExpression();
                    let.setInstruction(true);
                    let.setRequiredType(var.getRequiredType());
                    let.setVariableQName(sourceBinding.getVariableQName());
                    let.setSequence(sourceBinding.getSelectExpression());
                    let.setAction(tail);
                    sourceBinding.fixupBinding(let);
                    locationId = ((StyleElement) node).allocateLocation();
                    let.setLocation(locationId);
                    if (getCompilation().getCompilerInfo().isCompileWithTracing()) {
                        TraceExpression t = new TraceExpression(let);
                        t.setConstructType(LocationKind.LET_EXPRESSION);
                        t.setObjectName(var.getSourceBinding().getVariableQName());
                        t.setNamespaceResolver(getNamespaceResolver());
                        contents.add(t);
                    } else {
                        contents.add(let);
                    }
                    if (var.changesRetainedStaticContext()) {
                        let.setRetainedStaticContext(makeRetainedStaticContext());
                    }
                    //result.setLocationId(locationId);
                }


            } else if (node instanceof StyleElement) {
                StyleElement snode = (StyleElement) node;
                int fp = snode.getFingerprint();
                if (fp == StandardNames.XSL_ON_EMPTY || fp == StandardNames.XSL_ON_NON_EMPTY) {
                    containsSpecials = true;
                }
                Expression child;
                if (snode.validationError != null && !(snode instanceof AbsentExtensionElement)) {
                    if (snode.reportingCircumstances == REPORT_IF_INSTANTIATED) {
                        child = new ErrorExpression(snode.validationError);
                    } else {
                        child = fallbackProcessing(compilation, decl, snode);
                    }

                } else {
                    child = snode.compile(compilation, decl);
                    if (child != null) {
                        if (snode.changesRetainedStaticContext()) {
                            child.setRetainedStaticContext(snode.makeRetainedStaticContext());
                        }
                        if (includeParams || !(node instanceof XSLLocalParam)) {
                            if (getCompilation().getCompilerInfo().isCompileWithTracing()) {
                                child = makeTraceInstruction(snode, child);
                            }
                        }
                    }
                }
                if (child != null) {
                    contents.add(child);
                }
            }
        }
        if (containsSpecials) {
            return new ConditionalBlock(contents);
        }
        Expression block = Block.makeBlock(contents);
        if (block.getLocation() == null) {
            block.setLocation(locationId);
        }
        if (block.getRetainedStaticContext() == null) {
            block.setRetainedStaticContext(makeRetainedStaticContext());
        }
        return block;
    }

    /**
     * Compile a content value text node.
     *
     * @param node     the text node potentially containing the template
     * @param contents a list to which expressions representing the fixed and variable parts of the content template
     *                 will be appended
     * @throws XPathException if a static error is found
     */

    public void compileContentValueTemplate(TextImpl node, List<Expression> contents) throws XPathException {
        if (node instanceof TextValueTemplateNode) {
            Expression exp = ((TextValueTemplateNode) node).getContentExpression();
            if (getConfiguration().getBooleanProperty(FeatureKeys.STRICT_STREAMABILITY) && !(exp instanceof Literal)) {
                exp = new SequenceInstr(exp);
            }
            contents.add(exp);
        } else {
            contents.add(new StringLiteral(node.getStringValue()));
        }
    }


    /**
     * Create a trace instruction to wrap a real instruction
     *
     * @param source the parent element
     * @param child  the compiled expression tree for the instruction to be traced
     * @return a wrapper instruction that performs the tracing (if activated at run-time)
     */

    protected static Expression makeTraceInstruction(StyleElement source, Expression child) {
        if (child instanceof TraceExpression && !(source instanceof StylesheetComponent)) {
            return child;
            // this can happen, for example, after optimizing a compile-time xsl:if
        }
        CodeInjector injector = source.getCompilation().getCompilerInfo().getCodeInjector();
        if (injector != null) {
            int construct = source.getFingerprint();
            if (source instanceof LiteralResultElement) {
                construct = LocationKind.LITERAL_RESULT_ELEMENT;
            }
            Expression tracer = injector.inject(child, source.getStaticContext(), construct, source.getObjectName());
            tracer.setLocation(source.allocateLocation());
            return tracer;
        }
        return child;
    }

    /**
     * Perform fallback processing. Generate fallback code for an extension
     * instruction that is not recognized by the implementation.
     *
     * @param exec        the Executable
     * @param decl        the Declaration of the top-level element containing the extension instruction
     * @param instruction The unknown extension instruction
     * @return the expression tree representing the fallback code
     * @throws net.sf.saxon.trans.XPathException if any error occurs
     */

    protected Expression fallbackProcessing(Compilation exec, ComponentDeclaration decl, StyleElement instruction)
            throws XPathException {
        // process any xsl:fallback children; if there are none,
        // generate code to report the original failure reason
        Expression fallback = null;
        AxisIterator kids = instruction.iterateAxis(AxisInfo.CHILD);
        NodeInfo child;
        while ((child = kids.next()) != null) {
            if (child instanceof XSLFallback) {
                //fallback.setLocationId(allocateLocationId(getSystemId(), child.getLineNumber()));
                //((XSLFallback)child).compileChildren(exec, fallback, true);
                Expression b = ((XSLFallback) child).compileSequenceConstructor(exec, decl, true);
                if (b == null) {
                    b = Literal.makeEmptySequence();
                }
                if (fallback == null) {
                    fallback = b;
                } else {
                    fallback = Block.makeBlock(fallback, b);
                    fallback.setLocation(
                            allocateLocation());
                }
            }
        }
        if (fallback != null) {
            return fallback;
        } else {
            return new ErrorExpression(instruction.validationError);
            //            compileError(instruction.validationError);
            //            return EmptySequence.getInstance();
        }

    }

    /**
     * Allocate a location
     *
     * @return an location which can be used to report the location of the instruction
     */

    protected Location allocateLocation() {
        if (savedLocation == null) {
            savedLocation = new ExplicitLocation(this);
        }
        return savedLocation;
    }

    /**
     * Construct sort keys for a SortedIterator
     *
     * @param compilation the compilation episode
     * @param decl        the declaration containing the sort keys  @throws XPathException if any error is detected
     * @return an array of SortKeyDefinition objects if there are any sort keys;
     * or null if there are none.
     * @throws XPathException if an error is found
     */

    public SortKeyDefinitionList makeSortKeys(Compilation compilation, ComponentDeclaration decl) throws XPathException {
        // handle sort keys if any

        int numberOfSortKeys = 0;
        AxisIterator kids = iterateAxis(AxisInfo.CHILD);
        NodeInfo child;
        while ((child = kids.next()) != null) {
            if (child instanceof XSLSortOrMergeKey) {
                ((XSLSortOrMergeKey) child).compile(compilation, decl);
                if (child instanceof XSLSort) {
                    if (numberOfSortKeys != 0 && ((XSLSort) child).getStable() != null) {
                        compileError("stable attribute may appear only on the first xsl:sort element", "XTSE1017");
                    }
                }
                numberOfSortKeys++;
            }
        }

        if (numberOfSortKeys > 0) {
            SortKeyDefinition[] keys = new SortKeyDefinition[numberOfSortKeys];
            kids = iterateAxis(AxisInfo.CHILD);
            int k = 0;
            while ((child = kids.next()) != null) {
                if (child instanceof XSLSortOrMergeKey) {
                    keys[k++] = (SortKeyDefinition) ((XSLSortOrMergeKey) child).getSortKeyDefinition().simplify();
                }
            }
            return new SortKeyDefinitionList(keys);

        } else {
            return null;
        }
    }

    /**
     * Get the list of attribute-set names associated with this element.
     * This is used for xsl:element, xsl:copy, xsl:attribute-set, and on literal
     * result elements
     *
     * @param use the original value of the [xsl:]use-attribute-sets attribute
     * @return an array of names of the attribute sets
     * @throws net.sf.saxon.trans.XPathException if any error is detected
     */

    protected StructuredQName[] getUsedAttributeSets(String use)
            throws XPathException {

        List<StructuredQName> nameList = new ArrayList<StructuredQName>(4);
        StringTokenizer st = new StringTokenizer(use, " \t\n\r", false);
        while (st.hasMoreTokens()) {
            String asetname = st.nextToken();
            StructuredQName name;
            try {
                name = makeQName(asetname);
            } catch (NamespaceException err) {
                compileError(err.getMessage(), "XTSE0710");
                name = null;
            } catch (XPathException err) {
                compileError(err.getMessage(), "XTSE0710");
                name = null;
            }
            nameList.add(name);
        }
        return nameList.toArray(new StructuredQName[nameList.size()]);
    }

    /**
     * Process the value of the visibility attribute (XSLT 3.0)
     *
     * @param s     the value of the attribute after whitespace collapsing
     * @param flags contains "h" if the value "hidden" is allowed, "a" if the value "absent" is allowed
     * @return the corresponding visibility
     * @throws XPathException if the value is invalid
     */

    protected Visibility interpretVisibilityValue(String s, String flags) throws XPathException {
        for (Visibility v : Visibility.values()) {
            if (v.visibilityStr.equals(s) &&
                    (flags.contains("h") || !s.equals("hidden")) &&
                    (flags.contains("a") || !s.equals("absent"))) {
                return v;
            }
        }
        invalidAttribute("visibility", "public|final|private|abstract" +
                (flags.contains("h") ? "|hidden" : "") +
                (flags.contains("a") ? "|absent" : "")
        );
        return null;
    }

    /**
     * Get the list of xsl:with-param elements for a calling element (apply-templates,
     * call-template, apply-imports, next-match). This method can be used to get either
     * the tunnel parameters, or the non-tunnel parameters.
     *
     * @param compilation the compilation episode
     * @param decl        the containing stylesheet declaration
     * @param tunnel      true if the tunnel="yes" parameters are wanted, false to get
     * @return an array of WithParam objects for either the ordinary parameters
     * or the tunnel parameters, as an array containing the results of
     * compiling the xsl:with-param children of this instruction (if any)
     * @throws XPathException if any error is detected
     */

    public WithParam[] getWithParamInstructions(Expression parent, Compilation compilation, ComponentDeclaration decl, boolean tunnel)
            throws XPathException {
        int count = 0;
        AxisIterator kids = iterateAxis(AxisInfo.CHILD);
        NodeInfo child;
        while ((child = kids.next()) != null) {
            if (child instanceof XSLWithParam) {
                XSLWithParam wp = (XSLWithParam) child;
                if (wp.getSourceBinding().hasProperty(SourceBinding.TUNNEL) == tunnel) {
                    count++;
                }
            }
        }
        if (count == 0) {
            return WithParam.EMPTY_ARRAY;
        }
        WithParam[] array = new WithParam[count];
        count = 0;
        kids = iterateAxis(AxisInfo.CHILD);
        while ((child = kids.next()) != null) {
            if (child instanceof XSLWithParam) {
                XSLWithParam wp = (XSLWithParam) child;
                if (wp.getSourceBinding().hasProperty(SourceBinding.TUNNEL) == tunnel) {
                    WithParam p = wp.compileWithParam(parent, compilation, decl);
                    array[count++] = p;
                }
            }
        }
        return array;
    }

    /**
     * Report an error with diagnostic information
     *
     * @param error contains information about the error
     * @throws XPathException always, after reporting the error to the ErrorListener
     */

    public void compileError(XPathException error)
            throws XPathException {
        error.setIsStaticError(true);
        // Set the location of the error if there is no current location information,
        // or if the current location information is local to an XPath expression, unless we are
        // positioned on an xsl:function or xsl:template, in which case this would lose too much information
        if (error.getLocator() == null ||
                ((error.getLocator() instanceof ExplicitLocation ||
                          error.getLocator() instanceof Expression) && !(this instanceof StylesheetComponent))) {
            error.setLocator(this);
        }
        // Add a reference to the stylesheet node to any AttributeLocation: see bug 2662
        if (error.getLocator() instanceof AttributeLocation) {
            ((AttributeLocation) error.getLocator()).setElementNode(this);
        }
        if (error.getLocator() instanceof XPathParser.NestedLocation) {
            Location container = ((XPathParser.NestedLocation) error.getLocator()).getContainingLocation();
            if (container instanceof AttributeLocation) {
                ((AttributeLocation) container).setElementNode(this);
            }
        }
        getCompilation().reportError(error);
    }

    /**
     * Report a static error in the stylesheet
     *
     * @param message the error message
     * @throws XPathException always, after reporting the error to the ErrorListener
     */

    public void compileError(String message)
            throws XPathException {
        compileError(message, "XTSE0010");
        //XPathException tce = new XPathException(message);
        //tce.setLocation(getLocation());
        //compileError(tce);
    }

    /**
     * Compile time error, specifying an error code
     *
     * @param message   the error message
     * @param errorCode the error code. May be null if not known or not defined
     * @throws XPathException always, after reporting the error to the ErrorListener
     */

    public void compileError(String message, StructuredQName errorCode) throws XPathException {
        XPathException tce = new XPathException(message);
        tce.setErrorCodeQName(errorCode);
        tce.setLocator(this);
        compileError(tce);
    }

    /**
     * Compile time error, specifying an error code
     *
     * @param message   the error message
     * @param errorCode the error code. May be null if not known or not defined
     * @throws XPathException always, after reporting the error to the ErrorListener
     */

    public void compileError(String message, String errorCode) throws XPathException {
        compileError(new XPathException(message, errorCode, this));
    }

    /**
     * Compile time error, specifying an error code and the name of the attribute that
     * is in error.
     *
     * @param message       the error message
     * @param errorCode     the error code. May be null if not known or not defined
     * @param attributeName the name of the attribute. For attributes in no namespace
     *                      this is the local part of the name; for namespaced attributes
     *                      a name in Clark format may be supplied.
     * @throws XPathException always, after reporting the error to the ErrorListener
     */

    public void compileErrorInAttribute(String message, String errorCode, String attributeName) throws XPathException {
        StructuredQName att = StructuredQName.fromClarkName(attributeName);
        Location location = new AttributeLocation(this, att);
        compileError(new XPathException(message, errorCode, location));
    }

    protected void invalidAttribute(String attributeName, String allowedValues) throws XPathException {
        compileErrorInAttribute("Attribute " + getDisplayName() + "/@" + attributeName + " must be " + allowedValues,
                                "XTSE0020", attributeName);
    }

    protected void undeclaredNamespaceError(String prefix, String errorCode, String attributeName) throws XPathException {
        if (errorCode == null) {
            errorCode = "XTSE0280";
        }
        compileErrorInAttribute("Undeclared namespace prefix " + Err.wrap(prefix), errorCode, attributeName);
    }

    public void compileWarning(String message, StructuredQName errorCode)
            throws XPathException {
        XPathException tce = new XPathException(message);
        tce.setErrorCodeQName(errorCode);
        tce.setLocator(this);
        getCompilation().reportWarning(tce);
    }

    public void compileWarning(String message, String errorCode)
            throws XPathException {
        XPathException tce = new XPathException(message);
        tce.setErrorCode(errorCode);
        tce.setLocator(this);
        getCompilation().reportWarning(tce);
    }

    /**
     * Report a warning to the error listener
     *
     * @param error an exception containing the warning text
     */

    protected void issueWarning(XPathException error) {
        if (error.getLocator() == null) {
            error.setLocator(this);
        }
        getCompilation().reportWarning(error);
    }

    /**
     * Report a warning to the error listener
     *
     * @param message the warning message text
     * @param locator the location of the problem in the source stylesheet
     */

    protected void issueWarning(String message, SourceLocator locator) {
        XPathException tce = new XPathException(message);
        if (locator == null) {
            tce.setLocator(this);
        } else {
            tce.setLocator(locator);
        }
        issueWarning(tce);
    }

    /**
     * Test whether this is a top-level element
     *
     * @return true if the element is a child of the xsl:stylesheet or xsl:package element
     */

    public boolean isTopLevel() {
        return getParent() instanceof XSLModuleRoot;
    }

    /**
     * Ask whether this element contains a binding for a variable with a given name; and if it does,
     * return the source binding information
     *
     * @param name the variable name
     * @return the binding information if this element binds a variable of this name; otherwise null
     */

    public SourceBinding getBindingInformation(StructuredQName name) {
        return null;
    }

    /**
     * Bind a variable used in this element to the compiled form of the XSLVariable element in which it is
     * declared
     *
     * @param qName The name of the variable
     * @return the XSLVariableDeclaration (that is, an xsl:variable or xsl:param instruction) for the variable,
     * or null if no declaration of the variable can be found
     */

    public SourceBinding bindVariable(StructuredQName qName) {

        SourceBinding decl = bindLocalVariable(qName);
        if (decl != null) {
            return decl;
        }

        // Now check for a global variable
        // we rely on the search following the order of decreasing import precedence.
        SourceBinding binding = getPrincipalStylesheetModule().getGlobalVariableBinding(qName);
        if (binding == null || Navigator.isAncestorOrSelf(binding.getSourceElement(), this)) {
            // test case variable-0118
            return null;
        } else {
            return binding;
        }
    }

    /**
     * Bind a variable reference used in this element to the compiled form of the XSLVariable element in which it is
     * declared, considering only local variables and params
     *
     * @param qName The name of the variable
     * @return the XSLVariableDeclaration (that is, an xsl:variable or xsl:param instruction) for the variable,
     * or null if no local declaration of the variable can be found
     */

    public SourceBinding bindLocalVariable(StructuredQName qName) {
        NodeInfo curr = this;
        NodeInfo prev = this;

        SourceBinding implicit = hasImplicitBinding(qName);
        if (implicit != null) {
            return implicit;
        }

        // first search for a local variable declaration
        if (!isTopLevel()) {
            AxisIterator preceding = curr.iterateAxis(AxisInfo.PRECEDING_SIBLING);
            while (true) {
                curr = preceding.next();
                while (curr == null) {
                    curr = prev.getParent();
                    if (curr instanceof StyleElement) {
                        implicit = ((StyleElement) curr).hasImplicitBinding(qName);
                        if (implicit != null) {
                            return implicit;
                        }
                    }
                    while (curr instanceof StyleElement && !((StyleElement) curr).seesAvuncularVariables()) {
                        // a local variable is not visible within a sibling xsl:fallback or xsl:catch element
                        curr = curr.getParent();
                    }
                    prev = curr;
                    if (curr.getParent() instanceof XSLModuleRoot) {
                        break;   // top level
                    }
                    preceding = curr.iterateAxis(AxisInfo.PRECEDING_SIBLING);
                    curr = preceding.next();
                }
                if (curr.getParent() instanceof XSLModuleRoot) {
                    break;
                }
                if (curr instanceof XSLGeneralVariable) {
                    SourceBinding sourceBinding = ((XSLGeneralVariable) curr).getBindingInformation(qName);
                    if (sourceBinding != null) {
                        return sourceBinding;
                    }
                }

            }
        }
        return null;
    }

    /**
     * Ask whether variables declared in an "uncle" element are visible.
     *
     * @return true for all elements except xsl:fallback and saxon:catch
     */

    protected boolean seesAvuncularVariables() {
        return true;
    }

    /**
     * Ask whether this particular element implicitly binds a given variable (used for xsl:accumulator-rule)
     */

    protected SourceBinding hasImplicitBinding(StructuredQName name) {
        return null;
    }

    /**
     * Get the type of construct. This will be a constant in
     * class {@link LocationKind}. This method is part of the {@link InstructionInfo} interface
     */

    public int getConstructType() {
        return getFingerprint();
    }

    /**
     * Get a name identifying the object of the expression, for example a function name, template name,
     * variable name, key name, element name, etc. This is used only where the name is known statically.
     * If there is no name, the value will be null.
     *
     * @return the name of the object declared in this element, if any
     */

    public StructuredQName getObjectName() {
        return objectName;
    }

    /**
     * Set the object name, for example the name of a function, variable, or template declared on this element
     *
     * @param qName the object name as a QName
     */

    public void setObjectName(StructuredQName qName) {
        objectName = qName;
    }

    /**
     * Get the value of a particular property of the instruction. This is part of the
     * {@link InstructionInfo} interface for run-time tracing and debugging. The properties
     * available include all the attributes of the source instruction (named by the attribute name):
     * these are all provided as string values.
     *
     * @param name The name of the required property
     * @return The value of the requested property, or null if the property is not available
     */

    public Object getProperty(String name) {
        return getAttributeValue("", name);
    }

    /**
     * Get an iterator over all the properties available. The values returned by the iterator
     * will be of type String, and each string can be supplied as input to the getProperty()
     * method to retrieve the value of the property.
     */

    public Iterator<String> getProperties() {
        List<String> list = new ArrayList<String>(10);
        AxisIterator it = iterateAxis(AxisInfo.ATTRIBUTE);
        NodeImpl a;
        while ((a = (NodeImpl) it.next()) != null) {
            list.add(NameOfNode.makeName(a).getStructuredQName().getClarkName());
        }
        return list.iterator();
    }

    /**
     * Get the host language (XSLT, XQuery, XPath) used to implement the code in this container
     *
     * @return typically {@link net.sf.saxon.Configuration#XSLT} or {@link net.sf.saxon.Configuration#XQUERY}
     */

    public int getHostLanguage() {
        return Configuration.XSLT;
    }

    /**
     * Ask if an action on this StyleElement has been completed
     *
     * @param action for example ACTION_VALIDATE
     * @return true if the action has already been performed
     */

    public boolean isActionCompleted(int action) {
        return (actionsCompleted & action) != 0;
    }

    /**
     * Say that an action on this StyleElement has been completed
     *
     * @param action for example ACTION_VALIDATE
     */

    public void setActionCompleted(int action) {
        actionsCompleted |= action;
    }

}

