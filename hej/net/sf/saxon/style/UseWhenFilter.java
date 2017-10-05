////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.Controller;
import net.sf.saxon.event.ProxyReceiver;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.StartTagBuffer;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.XPathContextMajor;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.functions.DocumentFn;
import net.sf.saxon.functions.ElementAvailable;
import net.sf.saxon.functions.FunctionLibraryList;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.trans.packages.UsePack;
import net.sf.saxon.tree.AttributeLocation;
import net.sf.saxon.tree.linked.DocumentImpl;
import net.sf.saxon.tree.util.AttributeCollectionImpl;
import net.sf.saxon.type.*;
import net.sf.saxon.value.*;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Stack;

/**
 * This is a filter inserted into the input pipeline for processing stylesheet modules, whose
 * task is to evaluate use-when expressions and discard those parts of the stylesheet module
 * for which the use-when attribute evaluates to false.
 *
 * <p>Originally, with XSLT 2.0, this class did use-when filtering and very little else.
 * In XSLT 3.0 its role has expanded: it evaluates shadow attributes and static variables,
 * and collects information about package dependencies.</p>
 */

public class UseWhenFilter extends ProxyReceiver {

    private StartTagBuffer startTag;
    private int depthOfHole = 0;
    private boolean emptyStylesheetElement = false;
    private Stack<String> defaultNamespaceStack = new Stack<String>();
    private Stack<Integer> versionStack = new Stack<Integer>();
    private DateTimeValue currentDateTime = DateTimeValue.getCurrentDateTime(null);
    private Compilation compilation;
    private Stack<String> systemIdStack = new Stack<String>();
    private Stack<URI> baseUriStack = new Stack<URI>();
    private NestedIntegerValue precedence;
    private int importCount = 0;
    private boolean dropUnderscoredAttributes;



    /**
     * Create a UseWhenFilter
     *
     * @param compilation the compilation episode
     * @param next        the next receiver in the pipeline
     * @param precedence the import precedence expressed as a dotted-decimal integer, e.g. 1.4.6
     */

    public UseWhenFilter(Compilation compilation, Receiver next, NestedIntegerValue precedence) {
        super(next);
        this.compilation = compilation;
        this.precedence = precedence;
    }

    /**
     * Set the start tag buffer
     *
     * @param startTag a preceding filter on the pipeline that buffers the attributes of a start tag
     */

    public void setStartTagBuffer(StartTagBuffer startTag) {
        this.startTag = startTag;
    }

    /**
     * Start of document
     */

    public void open() throws XPathException {
        nextReceiver.open();
        try {
            String sysId = getSystemId();
            if (sysId == null) {
                sysId = "";
            }
            systemIdStack.push(sysId);
            baseUriStack.push(new URI(sysId));
        } catch (URISyntaxException e) {
            throw new XPathException("Invalid URI for stylesheet: " + getSystemId());
        }
    }

    /**
     * Notify the start of an element.
     *  @param elemName   the name of the element.
     * @param typeCode   integer code identifying the element's type within the name pool.
     * @param location
     * @param properties bit-significant properties of the element node
     */

    public void startElement(NodeName elemName, SchemaType typeCode, Location location, int properties) throws XPathException {
        boolean inXsltNamespace = elemName.hasURI(NamespaceConstant.XSLT);
        String stdAttUri = inXsltNamespace ? "" : NamespaceConstant.XSLT;
        defaultNamespaceStack.push(startTag.getAttribute(stdAttUri, "xpath-default-namespace"));
        if (emptyStylesheetElement) {
            depthOfHole++;
            return;
        }
        if (depthOfHole == 0) {
            URI baseUri = processBaseUri(location);

            boolean ignore = false;
            int fp = -1;
            if (inXsltNamespace) {
                fp = elemName.obtainFingerprint(getNamePool());
            }

            int version = Integer.MIN_VALUE;
            if (inXsltNamespace) {
                if (fp != StandardNames.XSL_OUTPUT) {
                    String versionAtt = startTag.getAttribute("", "version");
                    version = processVersionAttribute(versionAtt);
                }
            } else {
                // simplified stylesheet: look for xsl:version attribute
                String versionAtt = startTag.getAttribute(NamespaceConstant.XSLT, "version");
                version = processVersionAttribute(versionAtt);
            }

            if (version == Integer.MIN_VALUE) {
                version = versionStack.isEmpty() ? 30 : versionStack.peek();
            }
            versionStack.push(version);

            if (inXsltNamespace && defaultNamespaceStack.size() == 2
                    && version > 30 && !ElementAvailable.isXslt30Element(fp)) {
                // top level unknown XSLT element is ignored in forwards-compatibility mode
                ignore = true;
            }

            if (inXsltNamespace && !ignore) {
                processShadowAttributes(elemName, location, baseUri);
            }

            boolean isStylesheetElement = inXsltNamespace &&
                    (elemName.getLocalPart().equals("stylesheet") || elemName.getLocalPart().equals("transform") || elemName.getLocalPart().equals("package"));

            if (!ignore) {
                String useWhen = startTag.getAttribute(stdAttUri, "use-when");

                if (useWhen != null) {
                    AttributeCollection allAtts = startTag.getAllAttributes();
                    NodeName attName = allAtts.getNodeName(allAtts.getIndex(stdAttUri, "use-when"));
                    AttributeLocation attLoc = new AttributeLocation(elemName.getStructuredQName(), attName.getStructuredQName(), location);
                    boolean use = evaluateUseWhen(useWhen, attLoc, baseUri.toString());
                    ignore = !use;
                }

                if (ignore) {
                    if (isStylesheetElement) {
                        emptyStylesheetElement = true;
                    } else {
                        depthOfHole = 1;
                        return;
                    }
                }
            }

            if (inXsltNamespace) {

                // Handle static variables and parameters

                boolean isVariable = elemName.getLocalPart().equals("variable");
                boolean isParam = elemName.getLocalPart().equals("param");

                // Note, the general policy here is to ignore errors on this initial pass through the stylesheet,
                // if they will be checked and reported more thoroughly later on.

                String staticStr = Whitespace.trim(startTag.getAttribute("", "static"));
                if ((isVariable || isParam) &&
                        defaultNamespaceStack.size() == 2 && StyleElement.isYes(staticStr)) {

                    processStaticVariable(elemName, location, baseUri, precedence);
                }

                // Handle xsl:include and xsl:import

                boolean isInclude = elemName.getLocalPart().equals("include");
                boolean isImport = elemName.getLocalPart().equals("import");
                if ((isInclude || isImport) && defaultNamespaceStack.size() == 2) {
                    // We need to process the included/imported stylesheet now, because its static variables
                    // can be used later in this module
                    processIncludeImport(elemName, location, baseUri, isImport);
                }

                // Handle xsl:import-schema

                if (elemName.getLocalPart().equals("import-schema") && defaultNamespaceStack.size() == 2) {
                    compilation.setSchemaAware(true);   // bug 3105
                }

                // Handle xsl:use-package

                if (elemName.getLocalPart().equals("use-package") && defaultNamespaceStack.size() == 2) {
                    if (precedence.getDepth() > 1) {
                        throw new XPathException("xsl:use-package cannot appear in an imported stylesheet", "XTSE3008");
                    }
                    AttributeCollection atts = startTag.getAllAttributes();
                    String name = atts.getValue("", "name");
                    String pversion = atts.getValue("", "package-version");
                    if (name != null) {
                        try {
                            UsePack use = new UsePack(name, pversion, location.saveLocation());
                            compilation.registerPackageDependency(use);
                        } catch (XPathException err) {
                            // No action, error will be reported later.
                        }

                    }
                }
            }
            dropUnderscoredAttributes = inXsltNamespace;
            nextReceiver.startElement(elemName, typeCode, location, properties);
        } else {
            depthOfHole++;
        }
    }

    private void processIncludeImport(NodeName elemName, Location location, URI baseUri, boolean isImport) throws XPathException {
        String href = Whitespace.trim(startTag.getAttribute("", "href"));
        if (href == null) {
            throw new XPathException("Missing href attribute on " + elemName.getDisplayName(), "XTSE0010");
        }

        // TODO: handle fragments
        URIResolver resolver = compilation.getCompilerInfo().getURIResolver();
        String baseUriStr = baseUri.toString();
        DocumentURI key = DocumentFn.computeDocumentKey(href, baseUriStr, compilation.getPackageData(), resolver, false);
        Map<DocumentURI, TreeInfo> map = compilation.getStylesheetModules();
        if (map.containsKey(key)) {
            // No action needed
        } else {
            Source source;
            try {
                source = resolver.resolve(href, baseUriStr);
            } catch (TransformerException e) {
                throw XPathException.makeXPathException(e);
            }
            if (source == null) {
                source = getConfiguration().getSystemURIResolver().resolve(href, baseUriStr);
            }
            NestedIntegerValue newPrecedence = precedence;
            if (isImport) {
                newPrecedence = precedence.getStem().append(precedence.getLeaf() - 1).append(2 * ++importCount);
            }
            try {
                DocumentImpl includedDoc = StylesheetModule.loadStylesheetModule(source, false, compilation, newPrecedence);
                map.put(key, includedDoc);
            } catch (XPathException e) {
                e.setLocation(location);
                e.maybeSetErrorCode("XTSE0165");
                if ("XTSE0180".equals(e.getErrorCodeLocalPart())) {
                    if (isImport) {
                        e.setErrorCode("XTSE0210");
                    }
                }
                if (!e.hasBeenReported()) {
                    compilation.reportError(e);
                }
                throw e;
            }
        }
    }

    private void processStaticVariable(NodeName elemName, Location location, URI baseUri, NestedIntegerValue precedence) throws XPathException {
        String nameStr = startTag.getAttribute("", "name");
        String asStr = startTag.getAttribute("", "as");
        String requiredStr = Whitespace.trim(startTag.getAttribute("", "required"));
        boolean isRequired = StyleElement.isYes(requiredStr);


        UseWhenStaticContext staticContext = new UseWhenStaticContext(compilation, startTag);
        staticContext.setBaseURI(baseUri.toString());
        staticContext.setContainingLocation(
                new AttributeLocation(elemName.getStructuredQName(), new StructuredQName("", "", "as"), location));
        SequenceType requiredType = SequenceType.ANY_SEQUENCE;
        if (asStr != null) {
            XPathParser parser = compilation.getConfiguration().newExpressionParser("XP", false,31);
            parser.setLanguage(XPathParser.XPATH, 31);
            requiredType = parser.parseSequenceType(asStr, staticContext);
        }

        StructuredQName varName;
        try {
            varName = StructuredQName.fromLexicalQName(nameStr, false, true, startTag);
        } catch (XPathException err) {
            throw createXPathException(
                    "Invalid variable name:" + nameStr + ". " + err.getMessage(),
                    err.getErrorCodeLocalPart(), location);
        }

        boolean isVariable = elemName.getLocalPart().equals("variable");
        boolean isParam = elemName.getLocalPart().equals("param");
        boolean isSupplied = isParam && compilation.getParameters().containsKey(varName);
        AttributeLocation attLoc =
                new AttributeLocation(elemName.getStructuredQName(), new StructuredQName("", "", "select"), location);

        if (isParam) {
            if (isRequired && !isSupplied) {
                String selectStr = startTag.getAttribute("", "select");
                if (selectStr != null) {
                    throw createXPathException("Cannot supply a default value when required='yes'", "XTSE0010", attLoc);
                } else {
                    throw createXPathException(
                            "No value was supplied for the required static parameter $" + varName.getDisplayName(),
                            "XTDE0050", location);
                }
            }

            if (isSupplied) {
                Sequence suppliedValue = compilation.getParameters()
                        .convertParameterValue(varName, requiredType, true, staticContext.makeEarlyEvaluationContext());

                compilation.declareStaticVariable(varName, SequenceTool.toGroundedValue(suppliedValue), precedence, isParam);
            }
        }

        if (isVariable || !isSupplied) {
            String selectStr = startTag.getAttribute("", "select");
            GroundedValue value;
            if (selectStr == null) {
                if (isVariable) {
                    throw createXPathException(
                            "The select attribute is required for a static global variable",
                            "XTSE0010", location);
                } else if (!Cardinality.allowsZero(requiredType.getCardinality())) {
                    throw createXPathException("The parameter is implicitly required because it does not accept an "
                                                       + "empty sequence, but no value has been supplied", "XTDE0700", location);
                } else {
                    value = asStr == null ? StringValue.EMPTY_STRING : EmptySequence.getInstance();
                    compilation.declareStaticVariable(varName, value, precedence, isParam);
                }

            } else {
                try {
                    staticContext.setContainingLocation(attLoc);
                    Sequence seq = evaluateStatic(selectStr, location, staticContext);
                    value = SequenceTool.toGroundedValue(seq);
                } catch (XPathException e) {
                    throw createXPathException("Error in " + elemName.getLocalPart() + " expression. " + e.getMessage(),
                            e.getErrorCodeLocalPart(), attLoc);
                }
            }
            RoleDiagnostic role = new RoleDiagnostic(RoleDiagnostic.VARIABLE, varName.getDisplayName(), 0);
            role.setErrorCode("XTDE0050");
            TypeHierarchy th = getConfiguration().getTypeHierarchy();
            Sequence seq = th.applyFunctionConversionRules(value, requiredType, role, attLoc);
            value = SequenceTool.toGroundedValue(seq);
            try {
                compilation.declareStaticVariable(varName, value, precedence, isParam);
            } catch (XPathException e) {
                throw createXPathException(e.getMessage(), e.getErrorCodeLocalPart(), attLoc);
            }
        }
    }

    private void processShadowAttributes(NodeName elemName, Location location, URI baseUri) throws XPathException {
        AttributeCollection atts = startTag.getAllAttributes();
        for (int a=0; a<atts.getLength(); a++) {
            String local = atts.getLocalName(a);
            if (local.startsWith("_") && atts.getURI(a).equals("") && local.length() >= 2) {
                String value = atts.getValue(a);
                AttributeLocation attLocation = new AttributeLocation(elemName.getStructuredQName(), atts.getNodeName(a).getStructuredQName(), location);
                String newValue = processShadowAttribute(value, baseUri.toString(), attLocation);
                String plainName = local.substring(1);
                NodeName newName = new NoNamespaceName(plainName);
                // if a corresponding attribute exists with no underscore, overwrite it. The attribute()
                // method ensures that the shadow attribute won't be passed down the pipeline.
                // Otherwise overwrite the shadow attribute itself.
                int index = atts.getIndex("", plainName);
                if (index == -1) {
                    index = a;
                }
                ((AttributeCollectionImpl)atts).setAttribute(
                        index, newName, BuiltInAtomicType.UNTYPED_ATOMIC, newValue, atts.getLocation(a), 0);
            }
        }
    }

    private URI processBaseUri(Location location) throws XPathException {
        String systemId = location.getSystemId();
        if (systemId == null) {
            systemId = getSystemId();
        }
        URI baseUri;
        if (systemId == null || systemId.equals(systemIdStack.peek())) {
            baseUri = baseUriStack.peek();
        } else {
            try {
                baseUri = new URI(systemId);
            } catch (URISyntaxException e) {
                throw new XPathException("Invalid URI for stylesheet entity: " + systemId);
            }
        }
        String baseUriAtt = startTag.getAttribute(NamespaceConstant.XML, "base");
        if (baseUriAtt != null) {
            try {
                baseUri = baseUri.resolve(baseUriAtt);
            } catch (IllegalArgumentException iae) {
                throw new XPathException("Invalid URI in xml:base attribute: " + baseUriAtt + ". " + iae.getMessage());
            }
        }
        baseUriStack.push(baseUri);
        systemIdStack.push(systemId);
        return baseUri;
    }

    private int processVersionAttribute(String version) throws XPathException {
        if (version != null) {
            ConversionResult cr = BigDecimalValue.makeDecimalValue(version, true);
            if (cr instanceof ValidationFailure) {
                throw new XPathException("Invalid version number: " + version, "XTSE0110");
            }
            BigDecimalValue d = (BigDecimalValue) cr.asAtomic();
            return d.getDecimalValue().multiply(BigDecimal.TEN).intValue();
        } else {
            return Integer.MIN_VALUE;
        }
    }

    private String processShadowAttribute(String expression, String baseUri, AttributeLocation loc) throws XPathException {
        UseWhenStaticContext staticContext = new UseWhenStaticContext(compilation, startTag);
        staticContext.setBaseURI(baseUri);
        staticContext.setContainingLocation(loc);
        setNamespaceBindings(staticContext);
        Expression expr = AttributeValueTemplate.make(expression, staticContext);
        expr = typeCheck(expr, staticContext);
        SlotManager stackFrameMap = allocateSlots(expression, expr);
        XPathContext dynamicContext = makeDynamicContext(staticContext);
        ((XPathContextMajor) dynamicContext).openStackFrame(stackFrameMap);
        return expr.evaluateAsString(dynamicContext).toString();
    }

    public XPathException createXPathException(String message, String errorCode, Location location) throws XPathException {
        XPathException err = new XPathException(message);
        err.setErrorCode(errorCode);
        err.setIsStaticError(true);
        err.setLocator(location.saveLocation());
        getPipelineConfiguration().getErrorListener().fatalError(err);
        err.setHasBeenReported(true);
        return err;
    }

    /**
     * Notify a namespace. Namespaces are notified <b>after</b> the startElement event, and before
     * any children for the element. The namespaces that are reported are only required
     * to include those that are different from the parent element; however, duplicates may be reported.
     * A namespace must not conflict with any namespaces already used for element or attribute names.
     *
     * @param namespaceBindings the namespace to be notified
     * @throws IllegalStateException: attempt to output a namespace when there is no open element
     *                                start tag
     */

    public void namespace(NamespaceBindingSet namespaceBindings, int properties) throws XPathException {
        if (depthOfHole == 0) {
            nextReceiver.namespace(namespaceBindings, properties);
        }
    }

    /**
     * Notify an attribute. Attributes are notified after the startElement event, and before any
     * children. Namespaces and attributes may be intermingled.
     *
     * @param attName    The name of the attribute, as held in the name pool
     * @param typeCode   The type of the attribute, as held in the name pool
     * @param locationId
     *@param properties Bit significant value. The following bits are defined:
     *                   <dd>DISABLE_ESCAPING</dd>    <dt>Disable escaping for this attribute</dt>
     *                   <dd>NO_SPECIAL_CHARACTERS</dd>      <dt>Attribute value contains no special characters</dt>  @throws IllegalStateException: attempt to output an attribute when there is no open element
     *                                start tag
     */

    public void attribute(NodeName attName, SimpleType typeCode, CharSequence value, Location locationId, int properties) throws XPathException {
        if (depthOfHole == 0 && (!dropUnderscoredAttributes || !(attName.getLocalPart().startsWith("_") && attName.hasURI("")))) {
            nextReceiver.attribute(attName, typeCode, value, locationId, properties);
        }
    }

    /**
     * Notify the start of the content, that is, the completion of all attributes and namespaces.
     * Note that the initial receiver of output from XSLT instructions will not receive this event,
     * it has to detect it itself. Note that this event is reported for every element even if it has
     * no attributes, no namespaces, and no content.
     */

    public void startContent() throws XPathException {
        if (depthOfHole == 0) {
            nextReceiver.startContent();
        }
        dropUnderscoredAttributes = false;
    }

    /**
     * End of element
     */

    public void endElement() throws XPathException {
        defaultNamespaceStack.pop();
        if (depthOfHole > 0) {
            depthOfHole--;
        } else {
            systemIdStack.pop();
            baseUriStack.pop();
            versionStack.pop();
            nextReceiver.endElement();
        }
    }

    /**
     * Character data
     */

    public void characters(CharSequence chars, Location locationId, int properties) throws XPathException {
        if (depthOfHole == 0) {
            nextReceiver.characters(chars, locationId, properties);
        }
    }

    /**
     * Processing Instruction
     */

    public void processingInstruction(String target, CharSequence data, Location locationId, int properties) {
        // these are ignored in a stylesheet
    }

    /**
     * Output a comment
     */

    public void comment(CharSequence chars, Location locationId, int properties) throws XPathException {
        // these are ignored in a stylesheet
    }

    /**
     * Evaluate a use-when attribute
     *
     * @param expression the expression to be evaluated
     * @param location identifies the location of the expression in case error need to be reported
     * @param baseUri the base URI of the element containing the expression
     * @return the effective boolean value of the result of evaluating the expression
     * @throws XPathException if evaluation of the expression fails
     */

    public boolean evaluateUseWhen(String expression, AttributeLocation location, String baseUri) throws XPathException {
        UseWhenStaticContext staticContext = new UseWhenStaticContext(compilation, startTag);
        staticContext.setBaseURI(baseUri);
        staticContext.setContainingLocation(location);
        setNamespaceBindings(staticContext);
        Expression expr = ExpressionTool.make(expression, staticContext,
            0, Token.EOF, null);
        expr.setRetainedStaticContext(staticContext.makeRetainedStaticContext());
        expr = typeCheck(expr, staticContext);
        SlotManager stackFrameMap = allocateSlots(expression, expr);
        XPathContext dynamicContext = makeDynamicContext(staticContext);
        //dynamicContext.getController().getExecutable().setFunctionLibrary((FunctionLibraryList)staticContext.getFunctionLibrary());
        ((XPathContextMajor) dynamicContext).openStackFrame(stackFrameMap);
        return expr.effectiveBooleanValue(dynamicContext);
    }

    private SlotManager allocateSlots(String expression, Expression expr) {
        SlotManager stackFrameMap = getPipelineConfiguration().getConfiguration().makeSlotManager();
        if (expression.indexOf('$') >= 0) {
            ExpressionTool.allocateSlots(expr, stackFrameMap.getNumberOfVariables(), stackFrameMap);
        }
        return stackFrameMap;
    }


    private void setNamespaceBindings(UseWhenStaticContext staticContext) {
        staticContext.setDefaultElementNamespace(NamespaceConstant.NULL);
        for (int i = defaultNamespaceStack.size() - 1; i >= 0; i--) {
            String uri = defaultNamespaceStack.get(i);
            if (uri != null) {
                staticContext.setDefaultElementNamespace(uri);
                break;
            }
        }
    }

    private Expression typeCheck(Expression expr, UseWhenStaticContext staticContext) throws XPathException {
        ItemType contextItemType = Type.ITEM_TYPE;
        ContextItemStaticInfo cit = getConfiguration().makeContextItemStaticInfo(contextItemType, true);
        ExpressionVisitor visitor = ExpressionVisitor.make(staticContext);
        return expr.typeCheck(visitor, cit);
    }

    private XPathContext makeDynamicContext(UseWhenStaticContext staticContext) throws XPathException {
        Controller controller = new Controller(getConfiguration());
        controller.getExecutable().setFunctionLibrary((FunctionLibraryList) staticContext.getFunctionLibrary());
        if (staticContext.getXPathVersion() < 30) {
            controller.setURIResolver(new URIPreventer());
        }
        controller.setCurrentDateTime(currentDateTime);
        // this is to ensure that all use-when expressions in a module use the same date and time
        XPathContext dynamicContext = controller.newXPathContext();
        dynamicContext = dynamicContext.newCleanContext();
        return dynamicContext;
    }


    /**
     * Evaluate a static expression (to initialize a static variable)
     *
     * @param expression the expression to be evaluated
     * @param locationId identifies the location of the expression in case error need to be reported
     * @param staticContext the static context for evaluation of the expression
     * @return the effective boolean value of the result of evaluating the expression
     * @throws XPathException if evaluation of the expression fails
     */

    public Sequence evaluateStatic(String expression, Location locationId, UseWhenStaticContext staticContext) throws XPathException {
        setNamespaceBindings(staticContext);
        Expression expr = ExpressionTool.make(expression, staticContext,
            0, Token.EOF, null);
        expr = typeCheck(expr, staticContext);
        SlotManager stackFrameMap = getPipelineConfiguration().getConfiguration().makeSlotManager();
        ExpressionTool.allocateSlots(expr, stackFrameMap.getNumberOfVariables(), stackFrameMap);
        XPathContext dynamicContext = makeDynamicContext(staticContext);
        ((XPathContextMajor) dynamicContext).openStackFrame(stackFrameMap);
        return SequenceExtent.makeSequenceExtent(expr.iterate(dynamicContext));
    }



    /**
     * Define a URIResolver that disallows all URIs
     */

    private static class URIPreventer implements URIResolver {
        /**
         * Called by the processor when it encounters
         * an xsl:include, xsl:import, or document() function.
         *
         * @param href An href attribute, which may be relative or absolute.
         * @param base The base URI against which the first argument will be made
         *             absolute if the absolute URI is required.
         * @return A Source object, or null if the href cannot be resolved,
         *         and the processor should try to resolve the URI itself.
         * @throws XPathException if an error occurs when trying to
         *                        resolve the URI.
         */
        /*@NotNull*/
        public Source resolve(String href, String base) throws XPathException {
            throw new XPathException("No external documents are available within an [xsl]use-when expression");
        }
    }


}

