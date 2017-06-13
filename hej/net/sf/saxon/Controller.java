////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon;

import net.sf.saxon.event.*;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.accum.AccumulatorManager;
import net.sf.saxon.expr.instruct.*;
import net.sf.saxon.expr.parser.ExplicitLocation;
import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.expr.parser.PathMap;
import net.sf.saxon.functions.AccessorFn;
import net.sf.saxon.functions.IriToUri;
import net.sf.saxon.lib.*;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.resource.CollectionURIResolverWrapper;
import net.sf.saxon.serialize.Emitter;
import net.sf.saxon.serialize.ImplicitResultChecker;
import net.sf.saxon.style.StylesheetPackage;
import net.sf.saxon.trace.TraceEventMulticaster;
import net.sf.saxon.trans.*;
import net.sf.saxon.trans.rules.RuleManager;
import net.sf.saxon.tree.iter.*;
import net.sf.saxon.tree.tiny.Statistics;
import net.sf.saxon.tree.tiny.TinyBuilder;
import net.sf.saxon.tree.wrapper.SpaceStrippedDocument;
import net.sf.saxon.tree.wrapper.SpaceStrippedNode;
import net.sf.saxon.tree.wrapper.TypeStrippedDocument;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.Untyped;
import net.sf.saxon.value.DateTimeValue;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.SingletonClosure;
import org.xml.sax.SAXParseException;

import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * The Controller underpins Saxon's implementation of the JAXP Transformer class, and represents
 * an executing instance of a transformation or query. Multiple concurrent executions of
 * the same transformation or query will use different Controller instances. This class is
 * therefore not thread-safe.
 * <p/>
 * The Controller is serially reusable: when one transformation or query
 * is finished, it can be used to run another. However, there is no advantage in doing this
 * rather than allocating a new Controller each time.
 * <p/>
 * A dummy Controller is created when running free-standing XPath expressions.
 * <p/>
 * The Controller holds those parts of the dynamic context that do not vary during the course
 * of a transformation or query, or that do not change once their value has been computed.
 * This also includes those parts of the static context that are required at run-time.
 * <p/>
 * Many methods on the Controller are designed for internal use and should not be
 * considered stable. From release 8.4 onwards, those methods that are considered sufficiently
 * stable to constitute path of the Saxon public API are labelled with the JavaDoc tag "since":
 * the value indicates the release at which the method was added to the public API.
 * <p/>
 * <p>Prior to Saxon 9.6 the Controller implemented (extended) the JAXP {@link Transformer}
 * interface, and advanced applications were able to down-cast the Transformer to a Controller.
 * This is no longer the case. Instead, the JAXP factory delivers an instance of {@link net.sf.saxon.jaxp.TransformerImpl},
 * from which the Controller is accessible if required. Because the Controller is no longer required
 * to implement the JAXP interface, it has been possible to make it less monolithic, so some of the
 * things it did are now done elsewhere: for example, it no longer handles global parameters</p>
 *
 * @author Michael H. Kay
 * @since 8.4 From 9.6 this class should no longer be considered a public API.
 */

public class Controller implements ContextOriginator {

    private Configuration config;
    private Executable executable;

    private Item globalContextItem;
    private boolean globalContextItemPreset;
    private Map<PackageData, Bindery> binderies;
    private GlobalParameterSet globalParameters;
    private boolean convertParameters = true;
    private Map<GlobalVariable, Set<GlobalVariable>> globalVariableDependencies = new HashMap<GlobalVariable, Set<GlobalVariable>>();
    private String messageReceiverClassName;
    private Receiver messageReceiver;
    private TraceListener traceListener;
    private boolean tracingPaused;
    private Logger traceFunctionDestination;
    private boolean assertionsEnabled = true;
    private URIResolver standardURIResolver;
    private URIResolver userURIResolver;
    private Receiver principalResult;
    private String principalResultURI;
    private String cookedPrincipalResultURI;
    private boolean thereHasBeenAnExplicitResultDocument;
    private OutputURIResolver outputURIResolver;
    private UnparsedTextURIResolver unparsedTextResolver;
    private String defaultCollectionURI;
    private UnfailingErrorListener errorListener;
    private int recoveryPolicy;
    private TreeModel treeModel = TreeModel.TINY_TREE;
    private NamedTemplate initialTemplate = null;
    private HashSet<DocumentURI> allOutputDestinations;
    private DocumentPool sourceDocumentPool;
    private SequenceOutputter reusableSequenceOutputter = null;
    private HashMap<String, Object> userDataTable;
    private DateTimeValue currentDateTime;
    private boolean dateTimePreset = false;
    private Mode initialMode = null;
    private Function initialFunction = null;
    private NodeInfo lastRememberedNode = null;
    private int lastRememberedNumber = -1;
    private PathMap pathMap = null;
    private int validationMode = Validation.DEFAULT;
    private boolean inUse = false;
    private boolean stripSourceTrees = true;
    private boolean buildTree = true;
    private Map<StructuredQName, Sequence> initialTemplateParams;
    private Map<StructuredQName, Sequence> initialTemplateTunnelParams;
    private Stack<AttributeSet> attributeSetEvaluationStack = new Stack<AttributeSet>();
    private StylesheetCache stylesheetCache = null;
    private AccumulatorManager accumulatorManager = null;
    private CollectionFinder collectionFinder = null;
    private final Map<StructuredQName, Integer> messageCounters = new HashMap<StructuredQName, Integer>();

    public final static String ANONYMOUS_PRINCIPAL_OUTPUT_URI = "dummy:/anonymous/principal/result";


    /**
     * Create a Controller and initialise variables. Note: XSLT applications should
     * create the Controller by using the JAXP newTransformer() method, or in S9API
     * by using XsltExecutable.load()
     *
     * @param config The Configuration used by this Controller
     */

    public Controller(Configuration config) {
        this.config = config;
        // create a dummy executable
        executable = new Executable(config);
        sourceDocumentPool = new DocumentPool();
        reset();
    }

    /**
     * Create a Controller and initialise variables.
     *
     * @param config     The Configuration used by this Controller
     * @param executable The executable used by this Controller
     */

    public Controller(Configuration config, Executable executable) {
        this.config = config;
        this.executable = executable;
        sourceDocumentPool = new DocumentPool();
        reset();
    }

    /**
     * <p>Reset this <code>Transformer</code> to its original configuration.</p>
     * <p/>
     * <p><code>Transformer</code> is reset to the same state as when it was created with
     * {@link javax.xml.transform.TransformerFactory#newTransformer()},
     * {@link javax.xml.transform.TransformerFactory#newTransformer(javax.xml.transform.Source source)} or
     * {@link javax.xml.transform.Templates#newTransformer()}.
     * <code>reset()</code> is designed to allow the reuse of existing <code>Transformer</code>s
     * thus saving resources associated with the creation of new <code>Transformer</code>s.</p>
     * <p>
     * <i>The above is from the JAXP specification. With Saxon, it's unlikely that reusing a Transformer will
     * give any performance benefits over creating a new one. The one case where it might be beneficial is
     * to reuse the document pool (the set of documents that have been loaded using the doc() or document()
     * functions). Therefore, this method does not clear the document pool. If you want to clear the document
     * pool, call the method {@link #clearDocumentPool} as well.</i>
     * <p/>
     * <p>The reset <code>Transformer</code> is not guaranteed to have the same {@link javax.xml.transform.URIResolver}
     * or {@link javax.xml.transform.ErrorListener} <code>Object</code>s, e.g. {@link Object#equals(Object obj)}.
     * It is guaranteed to have a functionally equal <code>URIResolver</code>
     * and <code>ErrorListener</code>.</p>
     *
     * @since 1.5
     */

    public void reset() {
        globalParameters = new GlobalParameterSet();
        standardURIResolver = config.getSystemURIResolver();
        userURIResolver = config.getURIResolver();
        outputURIResolver = config.getOutputURIResolver();
        unparsedTextResolver = config.getUnparsedTextURIResolver();
        setErrorListener(config.getErrorListener());
        recoveryPolicy = config.getRecoveryPolicy();
        validationMode = config.getSchemaValidationMode();
        accumulatorManager = new AccumulatorManager();
        if (errorListener instanceof StandardErrorListener) {
            // if using a standard error listener, make a fresh one
            // for each transformation, because it is stateful - and also because the
            // host language is now known (a Configuration can serve multiple host languages)
            Logger ps = ((StandardErrorListener) errorListener).getLogger();
            errorListener = ((StandardErrorListener) errorListener).makeAnother(executable.getHostLanguage());
            ((StandardErrorListener) errorListener).setLogger(ps);
            ((StandardErrorListener) errorListener).setRecoveryPolicy(recoveryPolicy);
        }

        traceListener = null;
        traceFunctionDestination = config.getLogger();
        TraceListener tracer;
        try {
            tracer = config.makeTraceListener();
        } catch (XPathException err) {
            throw new IllegalStateException(err.getMessage());
        }
        if (tracer != null) {
            addTraceListener(tracer);
        }

        setModel(config.getParseOptions().getModel());

        globalContextItem = null;
        messageReceiver = null;
        currentDateTime = null;
        dateTimePreset = false;
        initialMode = null;
        initialTemplate = null;
        clearPerTransformationData();
    }

    /**
     * Reset variables that need to be reset for each transformation if the controller
     * is serially reused
     */

    private synchronized void clearPerTransformationData() {
        userDataTable = new HashMap<String, Object>(20);
        principalResult = null;
        allOutputDestinations = null;
        thereHasBeenAnExplicitResultDocument = false;
        lastRememberedNode = null;
        lastRememberedNumber = -1;
        tracingPaused = false;
        if (!globalContextItemPreset) {
            globalContextItem = null;
        }
        messageCounters.clear();
    }

    /**
     * Get the Configuration associated with this Controller. The Configuration holds
     * settings that potentially apply globally to many different queries and transformations.
     *
     * @return the Configuration object
     * @since 8.4
     */
    public Configuration getConfiguration() {
        return config;
    }

    /**
     * Set the initial mode for the transformation.
     * <p/>
     * XSLT 2.0 allows a transformation to be started in a mode other than the default mode.
     * The transformation then starts by looking for the template rule in this mode that best
     * matches the initial context node.
     * <p/>
     * This method may eventually be superseded by a standard JAXP method.
     *
     * @param expandedModeName the name of the initial mode.  The mode is
     *                         supplied as an expanded QName. Two special values are recognized, in the
     *                         reserved XSLT namespace:
     *                         xsl:unnamed to indicate the mode with no name, and xsl:default to indicate the
     *                         mode defined in the stylesheet header as the default mode.
     *                         The value null also indicates the unnamed mode.
     * @throws XPathException if the requested mode is not defined in the stylesheet
     * @since 8.4 Changed in 9.6 to accept a StructuredQName, and to throw an error if the mode
     * has not been defined. Changed in 9.7 so that null indicates the default mode, which defaults
     * to the unnamed mode but can be set otherwise in the stylesheet.
     */

    public void setInitialMode(StructuredQName expandedModeName) throws XPathException {
        if (expandedModeName == null || expandedModeName.equals(Mode.UNNAMED_MODE_NAME)) {
            initialMode = ((PreparedStylesheet) executable).getRuleManager().obtainMode(Mode.UNNAMED_MODE_NAME, true);
        } else if (expandedModeName.equals(Mode.DEFAULT_MODE_NAME)) {
            StructuredQName defaultModeName = ((StylesheetPackage) executable.getTopLevelPackage()).getDefaultMode();
            if (!expandedModeName.equals(defaultModeName)) {
                setInitialMode(defaultModeName);
            }
        } else {
            initialMode = ((PreparedStylesheet) executable).getRuleManager().obtainMode(expandedModeName, false);
            boolean declaredModes = ((StylesheetPackage) executable.getTopLevelPackage()).isDeclaredModes();
            if (initialMode == null) {
                throw new XPathException("Requested initial mode " + expandedModeName + " is not defined in the stylesheet", "XTDE0045");
            } else if (declaredModes && initialMode.getDeclaringComponent().getVisibility() == Visibility.PRIVATE) {
                throw new XPathException("Requested initial mode " + expandedModeName + " is private", "XTDE0045");
            } else if (!declaredModes && initialMode.isEmpty()) {
                throw new XPathException("Requested initial mode " + expandedModeName + " contains no template rules", "XTDE0045");
            }
        }
    }

    /**
     * Get the name of the initial mode for the transformation
     *
     * @return the name of the initial mode; null indicates
     * that the initial mode is the unnamed mode.
     */

    public StructuredQName getInitialModeName() {
        return initialMode == null ? null : initialMode.getModeName();
    }

    /**
     * Get the initial mode for the transformation
     *
     * @return the initial mode. This will be the default/unnamed mode if no specific mode
     * has been requested
     */

    /*@NotNull*/
    public Mode getInitialMode() {
        if (initialMode == null) {
            StructuredQName defaultMode = ((StylesheetPackage) executable.getTopLevelPackage()).getDefaultMode();
            if (defaultMode != null) {
                initialMode = ((PreparedStylesheet) executable).getRuleManager().obtainMode(defaultMode, false);
            }
        }
        return initialMode;
    }

    /**
     * Get the accumulator manager for this transformation. May be null if no accumulators are in use
     *
     * @return the accumulator manager, which holds dynamic information about the values of each
     * accumulator for each document
     */

    public AccumulatorManager getAccumulatorManager() {
        return accumulatorManager;
    }

    /**
     * Get the value of a supplied parameter (XSLT) or external variable (XQuery)
     *
     * @param name the QName of the parameter
     * @return the supplied value of the parameter, if such a parameter exists, and if a value
     * was supplied. Returns null if the parameter is not declared or if no value was supplied,
     * even if there is a default defined in the stylesheet or query.
     */

    public Sequence getParameter(StructuredQName name) {
        return globalParameters.get(name);
    }

    /**
     * Get the value of a parameter, converted and/or type-checked
     *
     * @param name         the name of the stylesheet parameter (XSLT) or external variable (XQuery)
     * @param requiredType the declared type of the parameter
     * @param context      the dynamic evaluation context
     * @return the parameter value if defined, or null otherwise. If the option
     * {@link #setApplyFunctionConversionRulesToExternalVariables(boolean)}} is set, the supplied
     * value is converted to the required type. Otherwise, the supplied value is checked
     * against the required type.
     */

    public Sequence getConvertedParameter(StructuredQName name, SequenceType requiredType, XPathContext context)
            throws XPathException {
        Sequence val = globalParameters.convertParameterValue(name, requiredType, convertParameters, context);
        if (val != null) {

            // Check that any nodes belong to the right configuration

            Configuration config = getConfiguration();
            SequenceIterator iter = val.iterate();
            Item next;
            while ((next = iter.next()) != null) {
                if (next instanceof NodeInfo && !config.isCompatible(((NodeInfo) next).getConfiguration())) {
                    throw new XPathException("A node supplied in a global parameter must be built using the same Configuration " +
                                                     "that was used to compile the stylesheet or query", SaxonErrorCode.SXXP0004);
                }
            }

            // If the supplied value is a document node, and the document node has a systemID that is an absolute
            // URI, and the absolute URI does not already exist in the document pool, then register it in the document
            // pool, so that the document-uri() function will find it there, and so that a call on doc() will not
            // reload it.

            if (val instanceof NodeInfo && ((NodeInfo) val).getNodeKind() == Type.DOCUMENT) {
                String systemId = ((NodeInfo) val).getRoot().getSystemId();
                try {
                    if (systemId != null && new URI(systemId).isAbsolute()) {
                        DocumentPool pool = getDocumentPool();
                        if (pool.find(systemId) == null) {
                            pool.add(((NodeInfo) val).getTreeInfo(), systemId);
                        }
                    }
                } catch (URISyntaxException err) {
                    // ignore it
                }
            }

            val = SequenceTool.toGroundedValue(val);
        }
        return val;
    }

    /**
     * Set the base output URI.
     * <p/>
     * <p>This defaults to the system ID of the Result object for the principal output
     * of the transformation if this is known; if it is not known, it defaults
     * to the current directory.</p>
     * <p/>
     * <p> The base output URI is used for resolving relative URIs in the <code>href</code> attribute
     * of the <code>xsl:result-document</code> instruction.</p>
     *
     * @param uri the base output URI
     * @since 8.4
     */

    public void setBaseOutputURI(String uri) {
        principalResultURI = uri;
    }

    /**
     * Get the base output URI.
     * <p/>
     * <p>This returns the value set using the {@link #setBaseOutputURI} method. If no value has been set
     * explicitly, then the method returns null if called before the transformation, or the computed
     * default base output URI if called after the transformation.</p>
     * <p/>
     * <p> The base output URI is used for resolving relative URIs in the <code>href</code> attribute
     * of the <code>xsl:result-document</code> instruction.</p>
     *
     * @return the base output URI
     * @since 8.4
     */

    /*@Nullable*/
    public String getBaseOutputURI() {
        return principalResultURI;
    }

    /**
     * Get the base output URI after processing. The processing consists of (a) defaulting
     * to the current user directory if no base URI is available and if the stylesheet is trusted,
     * and (b) applying IRI-to-URI escaping
     *
     * @return the base output URI after processing.
     */

    /*@Nullable*/
    public String getCookedBaseOutputURI() {
        if (cookedPrincipalResultURI == null) {
            String base = getBaseOutputURI();
            if (base == null && config.getBooleanProperty(FeatureKeys.ALLOW_EXTERNAL_FUNCTIONS)) {
                // if calling external functions is allowed, then the stylesheet is trusted, so
                // we allow it to write to files relative to the current directory
                base = new File(System.getProperty("user.dir")).toURI().toString();
            }
            if (base != null) {
                base = IriToUri.iriToUri(base).toString();
            }
            cookedPrincipalResultURI = base;
        }
        return cookedPrincipalResultURI;
    }

    /**
     * Get the principal result destination.
     * <p>This method is intended for internal use only. It is typically called by Saxon during the course
     * of a transformation, to discover the result that was supplied in the transform() call.</p>
     *
     * @return the Result object supplied as the principal result destination.
     */

    /*@Nullable*/
    public Receiver getPrincipalResult() {
        return principalResult;
    }

    /**
     * Check that an output destination has not been used before, optionally adding
     * this URI to the set of URIs that have been used.
     *
     * @param uri the URI to be used as the output destination
     * @return true if the URI is available for use; false if it has already been used.
     * <p/>
     * This method is intended for internal use only.
     */

    public synchronized boolean checkUniqueOutputDestination(/*@Nullable*/ DocumentURI uri) {
        if (uri == null) {
            return true;    // happens when writing say to an anonymous StringWriter
        }
        if (allOutputDestinations == null) {
            allOutputDestinations = new HashSet<DocumentURI>(20);
        }

        return !allOutputDestinations.contains(uri);
    }

    /**
     * Add a URI to the set of output destinations that cannot be written to, either because
     * they have already been written to, or because they have been read
     *
     * @param uri A URI that is not available as an output destination
     */

    public void addUnavailableOutputDestination(DocumentURI uri) {
        if (allOutputDestinations == null) {
            allOutputDestinations = new HashSet<DocumentURI>(20);
        }
        allOutputDestinations.add(uri);
    }

    /**
     * Remove a URI from the set of output destinations that cannot be written to or read from.
     * Used to support saxon:discard-document()
     *
     * @param uri A URI that is being made available as an output destination
     */

    public void removeUnavailableOutputDestination(DocumentURI uri) {
        if (allOutputDestinations != null) {
            allOutputDestinations.remove(uri);
        }
    }


    /**
     * Determine whether an output URI is available for use. This method is intended
     * for use by applications, via an extension function.
     *
     * @param uri A uri that the application is proposing to use in the href attribute of
     *            xsl:result-document: if this function returns false, then the xsl:result-document
     *            call will fail saying the URI has already been used.
     * @return true if the URI is available for use. Note that this function is not "stable":
     * it may return different results for the same URI at different points in the transformation.
     */

    public boolean isUnusedOutputDestination(DocumentURI uri) {
        return allOutputDestinations == null || !allOutputDestinations.contains(uri);
    }

    /**
     * Check whether an XSLT implicit result tree can be written. This is allowed only if no xsl:result-document
     * has been written for the principal output URI
     *
     * @throws XPathException if the implicit result document cannot be written because an explicit result document
     *                        has been written to the same URI.
     */

    public void checkImplicitResultTree() throws XPathException {
        String implicitURI = principalResultURI;
        if (implicitURI == null) {
            implicitURI = ANONYMOUS_PRINCIPAL_OUTPUT_URI;
        }
        //if (principalResultURI != null) {

        DocumentURI documentURI = new DocumentURI(implicitURI);
        synchronized(this) {
            if (!checkUniqueOutputDestination(documentURI)) {
                XPathException err = new XPathException(
                        "Cannot write an implicit result document if an explicit result document has been written to the same URI: " +
                                (implicitURI.equals(ANONYMOUS_PRINCIPAL_OUTPUT_URI) ?
                                        "(no URI supplied)" : implicitURI));
                err.setErrorCode("XTDE1490");
                throw err;
            } else {
                addUnavailableOutputDestination(documentURI);
            }
        }
        //}
    }

    /**
     * Set that an explicit result tree has been written using xsl:result-document
     */

    public void setThereHasBeenAnExplicitResultDocument() {
        thereHasBeenAnExplicitResultDocument = true;
    }

    /**
     * Test whether an explicit result tree has been written using xsl:result-document
     *
     * @return true if the transformation has evaluated an xsl:result-document instruction
     */

    public boolean hasThereBeenAnExplicitResultDocument() {
        return thereHasBeenAnExplicitResultDocument;
    }

    /**
     * Allocate a SequenceOutputter for a new output destination.
     *
     * @param size the estimated size of the output sequence
     * @return SequenceOutputter the allocated SequenceOutputter
     */

    /*@NotNull*/
    public synchronized SequenceOutputter allocateSequenceOutputter(int size) {
        PipelineConfiguration pipe = makePipelineConfiguration();
        return new SequenceOutputter(pipe, size);
    }

    /**
     * Accept a SequenceOutputter that is now available for reuse
     *
     * @param out the SequenceOutputter that is available for reuse
     */

    public void reuseSequenceOutputter(SequenceOutputter out) {
        reusableSequenceOutputter = out;
    }

    /**
     * Say whether the principal transformation results should generate a result tree, or should be returned in raw sequence form
     *
     * @param build set to true if a tree should be built, false if the results should be delivered in raw form
     */

    public void setBuildTree(boolean build) {
        buildTree = build;
    }

    /**
     * Ask whether the principal transformation results should generate a result tree, or should be returned in raw sequence form
     *
     * @return true if a tree should be built, false if the results should be delivered in raw form
     */

    public boolean isBuildTree() {
        return buildTree;
    }


    ///////////////////////////////////////////////////////////////////////////////

    /**
     * Set the initial named template to be used as the entry point, when the transformation is invoked
     * via the {@link #transform(Source, Receiver)} method.
     *
     * <p>XSLT 2.0 allows a transformation to start by executing a named template, rather than
     * by matching an initial context node in a source document. This method may eventually
     * be superseded by a standard JAXP method once JAXP supports XSLT 2.0.</p>
     *
     * <p>Note that any parameters supplied using {@link #initializeController(net.sf.saxon.expr.instruct.GlobalParameterSet)}
     * are used as the values of global stylesheet parameters. There is no way to supply values for local parameters
     * of the initial template.</p>
     *
     * @param qName The expanded name of the template, or null to indicate that there should be no initial template.
     * @throws XPathException if there is no named template with this name
     * @since 8.4. Changed in 9.6 to accept a StructuredQName instead of a QName in Clark notation
     */

    public void setInitialTemplate(StructuredQName qName) throws XPathException {
        if (qName == null) {
            initialTemplate = null;
            return;
        }
        NamedTemplate t = ((PreparedStylesheet) getExecutable()).getNamedTemplate(qName);
        if (t == null) {
            XPathException err = new XPathException("The requested initial template "
                                                            + qName.getDisplayName() + " does not exist");
            err.setErrorCode("XTDE0040");
            reportFatalError(err);
            throw err;
        } else {
            initialTemplate = t;
        }
    }

    /**
     * Get the initial template
     *
     * @return the name of the initial template, as an expanded name in Clark format if set, or null otherwise
     * @since 8.7. Changed in 9.6 to return a StructuredQName rather than a Clark name
     */

    /*@Nullable*/
    public StructuredQName getInitialTemplate() {
        if (initialTemplate == null) {
            return null;
        } else {
            return initialTemplate.getTemplateName();
        }
    }

    /**
     * Set parameters for the initial template (whether this is a named template, or a template
     * rule invoked to process the initial input item)
     *
     * @param params Tunnel or non-tunnel parameters to be supplied to the initial template. The supplied
     *               values will be converted to the required type using the function conversion rules.
     *               Any surplus parameters are silently ignored. May be null.
     * @param tunnel true if these are tunnel parameters; false if they are non-tunnel parameters
     * @since 9.6
     */

    public void setInitialTemplateParameters(Map<StructuredQName, Sequence> params, boolean tunnel) {
        if (tunnel) {
            this.initialTemplateTunnelParams = params;
        } else {
            this.initialTemplateParams = params;
        }

    }

    /**
     * Get the parameters for the initial template
     *
     * @param tunnel true if the parameters to be returned are the tunnel parameters; false for the non-tunnel parameters
     * @return the parameters supplied using {@link #setInitialTemplateParameters(java.util.Map, boolean)}.
     * May be null.
     * @since 9.6
     */

    public Map<StructuredQName, Sequence> getInitialTemplateParameters(boolean tunnel) {
        return tunnel ? initialTemplateTunnelParams : initialTemplateParams;
    }


    ///////////////////////////////////////////////////////////////////////////////

    /**
     * Make a PipelineConfiguration based on the properties of this Controller.
     * <p/>
     * This interface is intended primarily for internal use, although it may be necessary
     * for applications to call it directly if they construct pull or push pipelines
     *
     * @return a newly constructed PipelineConfiguration holding a reference to this
     * Controller as well as other configuration information.
     */

    /*@NotNull*/
    public PipelineConfiguration makePipelineConfiguration() {
        PipelineConfiguration pipe = config.makePipelineConfiguration();
        pipe.setURIResolver(userURIResolver == null ? standardURIResolver : userURIResolver);
        pipe.getParseOptions().setSchemaValidationMode(validationMode); // added in 9.7
        pipe.getParseOptions().setErrorListener(errorListener); // added in 9.7.0.4
        pipe.setController(this);
        final Executable executable = getExecutable();
        if (executable != null) {
            // can be null for an IdentityTransformer
            pipe.setHostLanguage(executable.getHostLanguage());
        }
        return pipe;
    }

    /**
     * Set the message receiver class name
     *
     * @param name the full name of the class to be instantiated to provide a receiver for xsl:message output. The name must
     *             be the name of a class that implements the {@link net.sf.saxon.event.Receiver} interface
     */

    public void setMessageReceiverClassName(String name) {
        this.messageReceiverClassName = name;
    }

    /**
     * Make a Receiver to be used for xsl:message output.
     * <p/>
     * This method is intended for internal use only.
     *
     * @return The newly constructed message Emitter
     * @throws XPathException if any dynamic error occurs; in
     *                        particular, if the registered MessageEmitter class is not an
     *                        Emitter
     */

    /*@NotNull*/
    private Receiver makeMessageReceiver() throws XPathException {
        Object messageReceiver = config.getInstance(messageReceiverClassName, null);
        if (!(messageReceiver instanceof Receiver)) {
            throw new XPathException(messageReceiverClassName + " is not a Receiver");
        }
        ((Receiver) messageReceiver).setPipelineConfiguration(makePipelineConfiguration());
        setMessageEmitter((Receiver) messageReceiver);
        return (Receiver) messageReceiver;
    }

    /**
     * Set the Receiver to be used for xsl:message output.
     * <p>
     * Recent versions of the JAXP interface specify that by default the
     * output of xsl:message is sent to the registered ErrorListener. Saxon
     * does not implement this convention. Instead, the output is sent
     * to a default message emitter, which is a slightly customised implementation
     * of the standard Saxon Emitter interface.</p>
     * <p>
     * This interface can be used to change the way in which Saxon outputs
     * xsl:message output.</p>
     * <p>
     * It is not necessary to use this interface in order to change the destination
     * to which messages are written: that can be achieved by obtaining the standard
     * message emitter and calling its {@link Emitter#setWriter} method.</p>
     * <p>
     * Although any <code>Receiver</code> can be supplied as the destination for messages,
     * applications may find it convenient to implement a subclass of {@link net.sf.saxon.event.SequenceWriter},
     * in which only the abstract <code>write()</code> method is implemented. This will have the effect that the
     * <code>write()</code> method is called to output each message as it is generated, with the <code>Item</code>
     * that is passed to the <code>write()</code> method being the document node at the root of an XML document
     * containing the contents of the message.
     * <p>
     * This method is intended for use by advanced applications. The Receiver interface
     * itself is subject to change in new Saxon releases.</p>
     * <p>
     * The supplied Receiver will have its open() method called once at the start of
     * the transformation, and its close() method will be called once at the end of the
     * transformation. Each individual call of an xsl:message instruction is wrapped by
     * calls of startDocument() and endDocument(). If terminate="yes" is specified on the
     * xsl:message call, the properties argument of the startDocument() call will be set
     * to the value {@link ReceiverOptions#TERMINATE}.</p>
     *
     * @param receiver The receiver to receive xsl:message output.
     * @since 8.4; changed in 8.9 to supply a Receiver rather than an Emitter
     */

    public void setMessageEmitter(/*@NotNull*/ Receiver receiver) {
        messageReceiver = receiver;
        receiver.setPipelineConfiguration(makePipelineConfiguration());
        if (receiver instanceof Emitter && ((Emitter) receiver).getOutputProperties() == null) {
            try {
                Properties props = new Properties();
                props.setProperty(OutputKeys.METHOD, "xml");
                props.setProperty(OutputKeys.INDENT, "yes");
                props.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
                ((Emitter) receiver).setOutputProperties(props);
            } catch (XPathException e) {
                // no action
            }
        }
    }

    /**
     * Get the Receiver used for xsl:message output. This returns the emitter
     * previously supplied to the {@link #setMessageEmitter} method, or the
     * default message emitter otherwise.
     *
     * @return the Receiver being used for xsl:message output
     * @since 8.4; changed in 8.9 to return a Receiver rather than an Emitter
     */

    /*@Nullable*/
    public Receiver getMessageEmitter() {
        return messageReceiver;
    }

    /**
     * Increment a counter in the message counters. This method is called automatically
     * when xsl:message is executed, to increment the counter associated with a given
     * error code used in the xsl:message. The counters are available (in Saxon-PE and -EE)
     * using the saxon:message-counter() extension function.
     * @param code the error code whose counter is to be incremented
     */

    public void incrementMessageCounter(StructuredQName code) {
        synchronized(messageCounters) {
            Integer c = messageCounters.get(code);
            int n = c == null ? 1 : c + 1;
            messageCounters.put(code, n);
        }
    }

    /**
     * Get the message counters
     * @return a snapshot copy of the table of message counters, indicating the number of times xsl:message
     * has been called for each distinct error code
     */

    public Map<StructuredQName, Integer> getMessageCounters() {
        synchronized(messageCounters) {
            return new HashMap<StructuredQName, Integer>(messageCounters);
        }
    }

    /**
     * Set the policy for handling recoverable XSLT errors.
     * <p/>
     * <p>Since 9.3 this call has no effect unless the error listener in use is a {@link StandardErrorListener}
     * or a subclass thereof. Calling this method then results in a call to the StandardErrorListener
     * to set the recovery policy, and the action that is taken on calls of the various methods
     * error(), fatalError(), and warning() is then the responsibility of the ErrorListener itself.</p>
     * <p/>
     * <p>Since 9.2 the policy for handling the most common recoverable error, namely the ambiguous template
     * match that arises when a node matches more than one match pattern, is a compile-time rather than run-time
     * setting, and can be controlled using {@link net.sf.saxon.trans.CompilerInfo#setRecoveryPolicy(int)} </p>
     *
     * @param policy the recovery policy to be used. The options are {@link Configuration#RECOVER_SILENTLY},
     *               {@link Configuration#RECOVER_WITH_WARNINGS}, or {@link Configuration#DO_NOT_RECOVER}.
     * @since 8.7.1
     */

    public void setRecoveryPolicy(int policy) {
        recoveryPolicy = policy;
        if (errorListener instanceof StandardErrorListener) {
            ((StandardErrorListener) errorListener).setRecoveryPolicy(policy);
        }
    }

    /**
     * Get the policy for handling recoverable errors
     *
     * @return the current policy. If none has been set with this Controller, the value registered with the
     * Configuration is returned.
     * @since 8.7.1
     */

    public int getRecoveryPolicy() {
        return recoveryPolicy;
    }

    /**
     * Set the error listener.
     *
     * @param listener the ErrorListener to be used
     */

    public void setErrorListener(ErrorListener listener) {
        if (listener instanceof UnfailingErrorListener) {
            errorListener = (UnfailingErrorListener) listener;
        } else {
            errorListener = new DelegatingErrorListener(listener);
        }
    }

    /**
     * Get the error listener.
     *
     * @return the ErrorListener in use. Note that this is not necessarily the ErrorListener that was supplied
     * to the {@link #setErrorListener(ErrorListener)} method; if that was not an {@link UnfailingErrorListener},
     * it will have been wrapped in a {@link DelegatingErrorListener}, and it is the DelegatingErrorListener
     * that this method returns.
     */

    public UnfailingErrorListener getErrorListener() {
        return errorListener;
    }

    /**
     * Report a recoverable error. This is an XSLT concept: by default, such an error results in a warning
     * message, and processing continues. In XQuery, however, there are no recoverable errors so a fatal
     * error is reported.
     * <p/>
     * This method is intended for internal use only.
     *
     * @param err An exception holding information about the error
     * @throws XPathException if the error listener decides not to
     *                        recover from the error
     */

    public void recoverableError(XPathException err) throws XPathException {
        if (executable.getHostLanguage() == Configuration.XQUERY || recoveryPolicy == Configuration.DO_NOT_RECOVER) {
            throw err;
        } else if (executable.getHostLanguage() == Configuration.XSLT) {
            errorListener.warning(err);
        } else {
            errorListener.error(err);
        }
    }

    /**
     * Report a fatal error
     *
     * @param err the error to be reported
     */

    public void reportFatalError(XPathException err) {
        if (!err.hasBeenReported()) {
            if (err.getHostLanguage() == null) {
                if (executable.getHostLanguage() == Configuration.XSLT) {
                    err.setHostLanguage("XSLT");
                } else if (executable.getHostLanguage() == Configuration.XQUERY) {
                    err.setHostLanguage("XQuery");
                }
            }
            getErrorListener().fatalError(err);
            err.setHasBeenReported(true);
        }
    }

    /**
     * Report a run-time warning
     * @param message   the warning message
     * @param errorCode the local part of the error code (in the ERR namespace). May be null.
     * @param locator the location in the source code. May be null.
     */

    public void warning(String message, String errorCode, Location locator) {
        getErrorListener().warning(new XPathException(message, errorCode, locator));
    }


    /////////////////////////////////////////////////////////////////////////////////////////
    // Methods for managing the various runtime control objects
    /////////////////////////////////////////////////////////////////////////////////////////


    /**
     * Get the Executable object.
     * <p/>
     * This method is intended for internal use only.
     *
     * @return the Executable (which represents the compiled stylesheet)
     */

    public Executable getExecutable() {
        return executable;
    }

    /**
     * Get the document pool. This is used only for source documents, not for stylesheet modules.
     * <p/>
     * This method is intended for internal use only.
     *
     * @return the source document pool
     */

    public DocumentPool getDocumentPool() {
        return sourceDocumentPool;
    }

    /**
     * Clear the document pool.
     * This is sometimes useful when re-using the same Transformer
     * for a sequence of transformations, but it isn't done automatically, because when
     * the transformations use common look-up documents, the caching is beneficial.
     */

    public void clearDocumentPool() {
        for (PackageData pack : getExecutable().getPackages()) {
            sourceDocumentPool.discardIndexes(pack.getKeyManager());
        }
        sourceDocumentPool = new DocumentPool();
    }

    /**
     * Get the bindery for the global variables in a particular package.
     * <p/>
     * This method is intended for internal use only.
     *
     * @param packageData the package for which the variables are required
     * @return the Bindery (in which values of all variables for the requested package are held)
     */

    public Bindery getBindery(PackageData packageData) {
        Bindery b = binderies.get(packageData);
        if (b == null) {
            b = new Bindery(packageData);
            binderies.put(packageData, b);
        }
        return b;
    }

    /**
     * Set the item used as the context for evaluating global variables. This value is used
     * as the global context item by XQuery and XPath, and also by XSLT 3.0 interfaces such
     * as {@link #applyTemplates(Sequence, Receiver)} and {@link #callTemplate(StructuredQName, Receiver)}.
     * It is not used by the {@link #transform(Source, Receiver)} and {@link #transformDocument(NodeInfo, Receiver)}
     * methods; by contrast, these overwrite the global context item with a value based on the node supplied
     * as the source of the transformation.
     *
     * @param contextItem the context item for evaluating global variables, or null if there is none
     * @since 9.7
     */

    public void setGlobalContextItem(Item contextItem) {
        setGlobalContextItem(contextItem, false);
    }

    /**
     * Set the item used as the context for evaluating global variables. This value is used
     * as the global context item by XQuery and XPath, and also by XSLT 3.0 interfaces such
     * as {@link #applyTemplates(Sequence, Receiver)} and {@link #callTemplate(StructuredQName, Receiver)}.
     * It is not used by the {@link #transform(Source, Receiver)} and {@link #transformDocument(NodeInfo, Receiver)}
     * methods; by contrast, these overwrite the global context item with a value based on the node supplied
     * as the source of the transformation.
     *
     * @param contextItem the context item for evaluating global variables, or null if there is none
     * @param alreadyStripped true if any stripping of type annotations or whitespace text node specified
     *                        in the stylesheet has already been carried out
     * @since 9.7
     */

    public void setGlobalContextItem(Item contextItem, boolean alreadyStripped) {
        if (!alreadyStripped) {
            // Bug 2929 - don't do space-stripping twice
            if (globalContextItem instanceof SpaceStrippedNode && ((SpaceStrippedNode) globalContextItem).getUnderlyingNode() == contextItem) {
                return;
            }
            if (contextItem instanceof NodeInfo) {
                // In XSLT, apply strip-space and strip-type-annotations options
                contextItem = prepareInputTree((NodeInfo) contextItem);
            }
        }
        this.globalContextItem = contextItem;
        this.globalContextItemPreset = true;
    }


    /**
     * Get the item used as the context for evaluating global variables. In XQuery this
     * is the same as the initial context item; in XSLT 1.0 and 2.0 it is the root of the tree containing
     * the initial context node; in XSLT 3.0 it can be set independently of the initial match selection.
     *
     * @return the context item for evaluating global variables, or null if there is none
     * @since 9.7
     */

    /*@Nullable*/
    public Item getGlobalContextItem() {
        return globalContextItem;
        // See bug 5224, which points out that the rules for XQuery 1.0 weren't clearly defined
    }

    /**
     * Set an object that will be used to resolve URIs used in
     * document(), etc.
     *
     * @param resolver An object that implements the URIResolver interface, or
     *                 null.
     */

    public void setURIResolver(URIResolver resolver) {
        userURIResolver = resolver;
        if (resolver instanceof StandardURIResolver) {
            ((StandardURIResolver) resolver).setConfiguration(getConfiguration());
        }
    }

    /**
     * Get the URI resolver.
     * <p/>
     * <p><i>This method changed in Saxon 8.5, to conform to the JAXP specification. If there
     * is no user-specified URIResolver, it now returns null; previously it returned the system
     * default URIResolver.</i></p>
     *
     * @return the user-supplied URI resolver if there is one, or null otherwise.
     */

    public URIResolver getURIResolver() {
        return userURIResolver;
    }

    /**
     * Get the fallback URI resolver. This is the URIResolver that Saxon uses when
     * the user-supplied URI resolver returns null.
     * <p/>
     * This method is intended for internal use only.
     *
     * @return the the system-defined URIResolver
     */

    public URIResolver getStandardURIResolver() {
        return standardURIResolver;
    }

    /**
     * Set the URI resolver for secondary output documents.
     * <p/>
     * XSLT 2.0 introduces the <code>xsl:result-document</code instruction,
     * allowing a transformation to have multiple result documents. JAXP does
     * not yet support this capability. This method allows an OutputURIResolver
     * to be specified that takes responsibility for deciding the destination
     * (and, if it wishes, the serialization properties) of secondary output files.
     * <p/>
     * In Saxon 9.5, because xsl:result-document is now multi-threaded, the
     * supplied resolver is cloned each time a new result document is created.
     * The cloned resolved is therefore able to maintain information about
     * the specific result document for use when its close() method is called,
     * without worrying about thread safety.
     *
     * @param resolver An object that implements the OutputURIResolver
     *                 interface, or null.
     * @since 8.4
     */

    public void setOutputURIResolver(/*@Nullable*/ OutputURIResolver resolver) {
        if (resolver == null) {
            outputURIResolver = config.getOutputURIResolver();
        } else {
            outputURIResolver = resolver;
        }
    }

    /**
     * Get the output URI resolver.
     *
     * @return the user-supplied URI resolver if there is one, or the
     * system-defined one otherwise.
     * @see #setOutputURIResolver
     * @since 8.4
     */

    /*@Nullable*/
    public OutputURIResolver getOutputURIResolver() {
        return outputURIResolver;
    }

    /**
     * Set an UnparsedTextURIResolver to be used to resolve URIs passed to the XSLT
     * unparsed-text() function.
     *
     * @param resolver the unparsed text URI resolver to be used. This replaces any unparsed text
     *                 URI resolver previously registered.
     * @since 8.9
     */

    public void setUnparsedTextURIResolver(UnparsedTextURIResolver resolver) {
        unparsedTextResolver = resolver;
    }

    /**
     * Get the URI resolver for the unparsed-text() function. This will
     * return the UnparsedTextURIResolver previously set using the {@link #setUnparsedTextURIResolver}
     * method.
     *
     * @return the registered UnparsedTextURIResolver
     * @since 8.9
     */

    public UnparsedTextURIResolver getUnparsedTextURIResolver() {
        return unparsedTextResolver;
    }

    /**
     * Set the CollectionURIResolver used for resolving collection URIs.
     * Defaults to the CollectionURIResolver registered with the Configuration
     *
     * @param resolver the resolver for references to collections
     * @since 9.4
     * @deprecated since 9.7. Use {@link #setCollectionFinder(CollectionFinder)}
     */

    public void setCollectionURIResolver(CollectionURIResolver resolver) {
        setCollectionFinder(new CollectionURIResolverWrapper(resolver));
    }

    /**
     * Get the CollectionURIResolver used for resolving references to collections.
     * If none has been set on the Controller, returns the
     * CollectionURIResolver registered with the Configuration
     *
     * @return the resolver for references to collections
     * @since 9.4
     * @deprecated since 9.7. Use {@link #getCollectionFinder()}
     */

    /*@NotNull*/
    public CollectionURIResolver getCollectionURIResolver() {
        CollectionFinder finder = getCollectionFinder();
        if (finder instanceof CollectionURIResolverWrapper) {
            return ((CollectionURIResolverWrapper) finder).getCollectionURIResolver();
        } else {
            return null;
        }
    }

    /**
     * Get the collection finder associated with this configuration. This is used to dereference
     * collection URIs used in the fn:collection and fn:uri-collection functions
     *
     * @return the CollectionFinder to be used
     * @since 9.7
     */


    public CollectionFinder getCollectionFinder() {
        if (collectionFinder == null) {
            collectionFinder = config.getCollectionFinder();
        }
        return collectionFinder;
    }

    /**
     * Set the collection finder associated with this configuration. This is used to dereference
     * collection URIs used in the fn:collection and fn:uri-collection functions
     *
     * @param cf the CollectionFinder to be used
     * @since 9.7
     */

    public void setCollectionFinder(CollectionFinder cf) {
        collectionFinder = cf;
    }

    /**
     * Set the name of the default collection. Defaults to the default collection
     * name registered with the Configuration.
     *
     * @param uri the collection URI of the default collection. May be null, to cause
     *            fallback to the collection name registered with the Configuration. The name will be passed
     *            to the collection URI resolver to identify the documents in the collection, unless
     *            the name is <code>http://saxon.sf.net/collection/empty</code> which always refers
     *            to the empty collection.
     * @since 9.4
     */

    public void setDefaultCollection(String uri) {
        defaultCollectionURI = uri;
    }

    /**
     * Get the name of the default collection. Defaults to the default collection
     * name registered with the Configuration.
     *
     * @return the collection URI of the default collection. If no value has been
     * set explicitly, the collection URI registered with the Configuration is returned
     * @since 9.4
     */

    public String getDefaultCollection() {
        return defaultCollectionURI == null ? getConfiguration().getDefaultCollection() : defaultCollectionURI;
    }


    /**
     * Ask whether source documents loaded using the doc(), document(), and collection()
     * functions, or supplied as a StreamSource or SAXSource to the transform() or addParameter() method
     * should be subjected to schema validation
     *
     * @return the schema validation mode previously set using setSchemaValidationMode(),
     * or the default mode (derived from the global Configuration) otherwise.
     */

    public int getSchemaValidationMode() {
        return validationMode;
    }

    /**
     * Say whether source documents loaded using the doc(), document(), and collection()
     * functions, or supplied as a StreamSource or SAXSource to the transform() or addParameter() method,
     * should be subjected to schema validation. The default value is taken
     * from the corresponding property of the Configuration.
     *
     * @param validationMode the validation (or construction) mode to be used for source documents.
     *                       One of {@link Validation#STRIP}, {@link Validation#PRESERVE}, {@link Validation#STRICT},
     *                       {@link Validation#LAX}
     * @since 9.2
     */

    public void setSchemaValidationMode(int validationMode) {
        this.validationMode = validationMode;
    }


    /**
     * Get the KeyManager.
     * <p/>
     * This method is intended for internal use only.
     *
     * @return the KeyManager, which holds details of all key declarations
     */

    public KeyManager getKeyManager() {
        return executable.getKeyManager();
    }

    /**
     * Set the tree model to use. Default is the tiny tree
     *
     * @param model typically one of the constants {@link net.sf.saxon.om.TreeModel#TINY_TREE},
     *              {@link net.sf.saxon.om.TreeModel#TINY_TREE_CONDENSED}, or {@link net.sf.saxon.om.TreeModel#LINKED_TREE}.
     *              It is also possible to use a user-defined tree model.
     * @since 9.2
     */

    public void setModel(TreeModel model) {
        treeModel = model;
    }

    /**
     * Get the tree model that will be used.
     *
     * @return typically one of the constants {@link net.sf.saxon.om.TreeModel#TINY_TREE},
     * {@link TreeModel#TINY_TREE_CONDENSED}, or {@link TreeModel#LINKED_TREE}.
     * It is also possible to use a user-defined tree model.
     * @since 9.2
     */

    public TreeModel getModel() {
        return treeModel;
    }


    /**
     * Make a builder for the selected tree model.
     *
     * @return an instance of the Builder for the chosen tree model
     * @since 8.4
     */

    public Builder makeBuilder() {
        Builder b = treeModel.makeBuilder(makePipelineConfiguration());
        b.setTiming(config.isTiming());
        b.setLineNumbering(config.isLineNumbering());
        return b;
    }

    /**
     * Say whether the transformation should perform whitespace stripping as defined
     * by the xsl:strip-space and xsl:preserve-space declarations in the stylesheet
     * in the case where a source tree is supplied to the transformation as a tree
     * (typically a DOMSource, or a Saxon NodeInfo).
     * The default is true. It is legitimate to suppress whitespace
     * stripping if the client knows that all unnecessary whitespace has already been removed
     * from the tree before it is processed. Note that this option applies to all source
     * documents for which whitespace-stripping is normally applied, that is, both the
     * principal source documents, and documents read using the doc(), document(), and
     * collection() functions. It does not apply to source documents that are supplied
     * in the form of a SAXSource or StreamSource, for which whitespace is stripped
     * during the process of tree construction.
     * <p>Generally, stripping whitespace speeds up the transformation if it is done
     * while building the source tree, but slows it down if it is applied to a tree that
     * has already been built. So if the same source tree is used as input to a number
     * of transformations, it is better to strip the whitespace once at the time of
     * tree construction, rather than doing it on-the-fly during each transformation.</p>
     *
     * @param strip true if whitespace is to be stripped from supplied source trees
     *              as defined by xsl:strip-space; false to suppress whitespace stripping
     * @since 9.3
     */

    public void setStripSourceTrees(boolean strip) {
        stripSourceTrees = strip;
    }

    /**
     * Ask whether the transformation will perform whitespace stripping for supplied source trees as defined
     * by the xsl:strip-space and xsl:preserve-space declarations in the stylesheet.
     *
     * @return true unless whitespace stripping has been suppressed using
     * {@link #setStripSourceTrees(boolean)}.
     * @since 9.3
     */

    public boolean isStripSourceTree() {
        return stripSourceTrees;
    }

    /**
     * Ask whether the executable is a stylesheet whose top-level package
     * contains an xsl:strip-space declaration requesting stripping of whitespace
     * from the principal source document to the transformation
     * @return true if whitespace stripping has been requested
     */

    private boolean isStylesheetContainingStripSpace() {
        SpaceStrippingRule rule;
        return executable instanceof PreparedStylesheet &&
                (rule = ((PreparedStylesheet)executable).getTopLevelPackage().getSpaceStrippingRule()) != null &&
                rule != NoElementsSpaceStrippingRule.getInstance();
    }

    /**
     * Ask whether the executable is a stylesheet whose top-level package
     * contains requests stripping of type annotations
     *
     * @return true if stripping of type annotations has been requested
     */

    public boolean isStylesheetStrippingTypeAnnotations() {
        return executable instanceof PreparedStylesheet &&
                ((PreparedStylesheet) executable).getTopLevelPackage().isStripsTypeAnnotations();
    }

    /**
     * Make a Stripper configured to implement the whitespace stripping rules.
     * In the case of XSLT the whitespace stripping rules are normally defined
     * by <code>xsl:strip-space</code> and <code>xsl:preserve-space</code elements
     * in the stylesheet. Alternatively, stripping of all whitespace text nodes
     * may be defined at the level of the Configuration, using the code
     * {@code Configuration.getParseOptions().setSpaceStrippingRules(AllElementsSpaceStrippingRule.getInstance()}.
     *
     * @param next the Receiver to which the events filtered by this stripper are
     *             to be sent (often a Builder). May be null if the stripper is not being used for filtering
     *             into a Builder or other Receiver.
     * @return the required Stripper. A Stripper may be used in two ways. It acts as
     * a filter applied to an event stream, that can be used to remove the events
     * representing whitespace text nodes before they reach a Builder. Alternatively,
     * it can be used to define a view of an existing tree in which the whitespace
     * text nodes are dynamically skipped while navigating the XPath axes.
     * @since 8.4 - Generalized in 8.5 to accept any Receiver as an argument
     */

    public Stripper makeStripper(/*@Nullable*/ Receiver next) {
        if (next == null) {
            next = new Sink(makePipelineConfiguration());
        }
        return new Stripper(getSpaceStrippingRule(), next);
    }

    /**
     * Return the default whitespace-stripping rules that apply to this transformation or query.
     * @return If the configuration-level whitespace-stripping rule is to strip whitespace for
     * all elements, then AllElementsSpaceStrippingRule.getInstance(). Otherwise,
     */

    public SpaceStrippingRule getSpaceStrippingRule() {
        if (config.getParseOptions().getSpaceStrippingRule() == AllElementsSpaceStrippingRule.getInstance()) {
            return AllElementsSpaceStrippingRule.getInstance();
        } else if (executable instanceof PreparedStylesheet) {
            SpaceStrippingRule rule = ((PreparedStylesheet)executable).getTopLevelPackage().getSpaceStrippingRule();
            if (rule != null) {
                return rule;
            }
        }
        return NoElementsSpaceStrippingRule.getInstance();
    }

    /**
     * Add a document to the document pool, and check that it is suitable for use in this query or
     * transformation. This check rejects the document if document has been validated (and thus carries
     * type annotations) but the query or transformation is not schema-aware.
     * <p/>
     * This method is intended for internal use only.
     *
     * @param doc the root node of the document to be added. Must not be null.
     * @param uri the document-URI property of this document. If non-null, the document is registered
     *            in the document pool with this as its document URI.
     * @throws XPathException if an error occurs
     */
    public void registerDocument(TreeInfo doc, DocumentURI uri) throws XPathException {
        if (!getExecutable().isSchemaAware() && !Untyped.getInstance().equals(doc.getRootNode().getSchemaType())) {
            boolean isXSLT = getExecutable().getHostLanguage() == Configuration.XSLT;
            String message;
            if (isXSLT) {
                message = "The source document has been schema-validated, but" +
                        " the stylesheet is not schema-aware. A stylesheet is schema-aware if" +
                        " either (a) it contains an xsl:import-schema declaration, or (b) the stylesheet compiler" +
                        " was configured to be schema-aware.";
            } else {
                message = "The source document has been schema-validated, but" +
                        " the query is not schema-aware. A query is schema-aware if" +
                        " either (a) it contains an 'import schema' declaration, or (b) the query compiler" +
                        " was configured to be schema-aware.";
            }
            throw new XPathException(message);
        }
        if (uri != null) {
            sourceDocumentPool.add(doc, uri);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Methods for registering and retrieving handlers for template rules
    ////////////////////////////////////////////////////////////////////////////////

    /**
     * Get the Rule Manager.
     * <p/>
     * This method is intended for internal use only.
     *
     * @return the Rule Manager, used to hold details of template rules for
     * all modes; or null in the case of a non-XSLT controller
     */
    public RuleManager getRuleManager() {
        Executable exec = getExecutable();
        return exec instanceof PreparedStylesheet ? ((PreparedStylesheet) getExecutable()).getRuleManager() : null;
    }

    /////////////////////////////////////////////////////////////////////////
    // Methods for tracing
    /////////////////////////////////////////////////////////////////////////

    /**
     * Set a TraceListener, replacing any existing TraceListener
     * <p>This method has no effect unless the stylesheet or query was compiled
     * with tracing enabled.</p>
     *
     * @param listener the TraceListener to be set. May be null, in which case
     *                 trace events will not be reported
     * @since 9.2
     */

    public void setTraceListener(TraceListener listener) {
        this.traceListener = listener;
    }

    /**
     * Get the TraceListener. By default, there is no TraceListener, and this
     * method returns null. A TraceListener may be added using the method
     * {@link #addTraceListener}. If more than one TraceListener has been added,
     * this method will return a composite TraceListener. Because the form
     * this takes is implementation-dependent, this method is not part of the
     * stable Saxon public API.
     *
     * @return the TraceListener used for XSLT or XQuery instruction tracing, or null if absent.
     */
    /*@Nullable*/
    public TraceListener getTraceListener() {
        return traceListener;
    }

    /**
     * Test whether instruction execution is being traced. This will be true
     * if (a) at least one TraceListener has been registered using the
     * {@link #addTraceListener} method, and (b) tracing has not been temporarily
     * paused using the {@link #pauseTracing} method.
     *
     * @return true if tracing is active, false otherwise
     * @since 8.4
     */

    public final boolean isTracing() {
        return traceListener != null && !tracingPaused;
    }

    /**
     * Pause or resume tracing. While tracing is paused, trace events are not sent to any
     * of the registered TraceListeners.
     *
     * @param pause true if tracing is to pause; false if it is to resume
     * @since 8.4
     */
    public final void pauseTracing(boolean pause) {
        tracingPaused = pause;
    }

    /**
     * Adds the specified trace listener to receive trace events from
     * this instance. Note that although TraceListeners can be added
     * or removed dynamically, this has no effect unless the stylesheet
     * or query has been compiled with tracing enabled. This is achieved
     * by calling {@link Configuration#setTraceListener} or by setting
     * the attribute {@link net.sf.saxon.lib.FeatureKeys#TRACE_LISTENER} on the
     * TransformerFactory. Conversely, if this property has been set in the
     * Configuration or TransformerFactory, the TraceListener will automatically
     * be added to every Controller that uses that Configuration.
     *
     * @param trace the trace listener. If null is supplied, the call has no effect.
     * @since 8.4
     */

    public void addTraceListener(/*@Nullable*/ TraceListener trace) {
        if (trace != null) {
            traceListener = TraceEventMulticaster.add(traceListener, trace);
        }
    }

    /**
     * Removes the specified trace listener so that the listener will no longer
     * receive trace events.
     *
     * @param trace the trace listener.
     * @since 8.4
     */

    public void removeTraceListener(TraceListener trace) {
        traceListener = TraceEventMulticaster.remove(traceListener, trace);
    }

    /**
     * Set the destination for output from the fn:trace() function.
     * By default, the destination is System.err. If a TraceListener is in use,
     * this is ignored, and the trace() output is sent to the TraceListener.
     *
     * @param stream the PrintStream to which trace output will be sent. If set to
     *               null, trace output is suppressed entirely. It is the caller's responsibility
     *               to close the stream after use.
     * @since 9.1. Changed in 9.6 to use a Logger
     */

    public void setTraceFunctionDestination(Logger stream) {
        traceFunctionDestination = stream;
    }

    /**
     * Get the destination for output from the fn:trace() function.
     *
     * @return the PrintStream to which trace output will be sent. If no explicitly
     * destination has been set, returns System.err. If the destination has been set
     * to null to suppress trace output, returns null.
     * @since 9.1. Changed in 9.6 to use a Logger
     */

    public Logger getTraceFunctionDestination() {
        return traceFunctionDestination;
    }

    /**
     * Ask whether assertions (xsl:assert instructions) have been enabled at run time. By default
     * they are disabled at compile time. If assertions are enabled at compile time, then by
     * default they will also be enabled at run time; but they can be disabled at run time by
     * specific request. At compile time, assertions can be enabled for some packages and
     * disabled for others; at run-time, they can only be enabled or disabled globally.
     *
     * @return true if assertions are enabled at run time
     * @since 9.7
     */

    public boolean isAssertionsEnabled() {
        return assertionsEnabled;
    }

    /**
     * Ask whether assertions (xsl:assert instructions) have been enabled at run time. By default
     * they are disabled at compile time. If assertions are enabled at compile time, then by
     * default they will also be enabled at run time; but they can be disabled at run time by
     * specific request. At compile time, assertions can be enabled for some packages and
     * disabled for others; at run-time, they can only be enabled or disabled globally.
     *
     * @param enabled true if assertions are to be enabled at run time; this has no effect
     *                if assertions were disabled (for a particular package) at compile time
     * @since 9.7
     */

    public void setAssertionsEnabled(boolean enabled) {
        this.assertionsEnabled = enabled;
    }

    /**
     * Initialize the controller ready for a new transformation. This method should not normally be called by
     * users (it is done automatically when transform() is invoked). However, it is available as a low-level API
     * especially for use with XQuery.
     *
     * @param params the values of stylesheet parameters. This must include static parameters as well as non-static parameters.
     * @throws XPathException if an error occurs, for example if a required parameter is not supplied.
     */

    public void initializeController(GlobalParameterSet params) throws XPathException {



        // get a new bindery, to clear out any variables from previous runs

        //topLevelBindery = new Bindery();
        binderies = new HashMap<PackageData, Bindery>();
//        if (executable.getTopLevelPackage() != null) {
//            binderies.put(executable.getTopLevelPackage(), topLevelBindery);
//        }
        //topLevelBindery.allocateGlobals(executable.getGlobalVariableMap());

        // if parameters were supplied, set them up

        try {
            executable.checkAllRequiredParamsArePresent(params);
        } catch (XPathException e) {
            if (!e.hasBeenReported()) {
                getErrorListener().fatalError(e);
                throw e;
            }
        }
        globalParameters = params;

        // Check the global context item

        globalContextItem = executable.checkInitialContextItem(globalContextItem, newXPathContext());

        if (traceListener != null) {
            traceListener.open(this);
            preEvaluateGlobals(newXPathContext());
        }
    }

    public void setApplyFunctionConversionRulesToExternalVariables(boolean applyConversionRules) {
        convertParameters = applyConversionRules;
        //topLevelBindery.setApplyFunctionConversionRulesToExternalVariables(applyConversionRules);
    }


    /////////////////////////////////////////////////////////////////////////
    // Allow user data to be associated with nodes on a tree
    /////////////////////////////////////////////////////////////////////////

    /**
     * Get user data associated with a key. To retrieve user data, two objects are required:
     * an arbitrary object that may be regarded as the container of the data (originally, and
     * typically still, a node in a tree), and a name. The name serves to distingush data objects
     * associated with the same node by different client applications.
     * <p/>
     * This method is intended primarily for internal use, though it may also be
     * used by advanced applications.
     *
     * @param key  an object acting as a key for this user data value. This must be equal
     *             (in the sense of the equals() method) to the key supplied when the data value was
     *             registered using {@link #setUserData}.
     * @param name the name of the required property
     * @return the value of the required property
     */

    public Object getUserData(Object key, String name) {
        String keyValue = key.hashCode() + " " + name;
        // System.err.println("getUserData " + name + " on object returning " + userDataTable.get(key));
        return userDataTable.get(keyValue);
    }

    /**
     * Set user data associated with a key. To store user data, two objects are required:
     * an arbitrary object that may be regarded as the container of the data (originally, and
     * typically still, a node in a tree), and a name. The name serves to distingush data objects
     * associated with the same node by different client applications.
     * <p/>
     * This method is intended primarily for internal use, though it may also be
     * used by advanced applications.
     *
     * @param key  an object acting as a key for this user data value. This can be any object, for example
     *             a node or a string. If data for the given object and name already exists, it is overwritten.
     * @param name the name of the required property
     * @param data the value of the required property. If null is supplied, any existing entry
     *             for the key is removed.
     */

    public void setUserData(Object key, String name, /*@Nullable*/ Object data) {
        // System.err.println("setUserData " + name + " on object to " + data);
        String keyVal = key.hashCode() + " " + name;
        if (data == null) {
            userDataTable.remove(keyVal);
        } else {
            userDataTable.put(keyVal, data);
        }
    }

    private void checkReadiness() throws XPathException {
        if (inUse) {
            throw new IllegalStateException(
                    "The Controller is being used recursively or concurrently. This is not permitted.");
        }
        if (binderies == null) {
            throw new IllegalStateException("The Controller has not been initialized");
        }
        inUse = true;
        clearPerTransformationData();
        if (executable == null) {
            throw new XPathException("Stylesheet has not been prepared");
        }
        if (!dateTimePreset) {
            currentDateTime = null;     // reset at start of each transformation
        }

    }

    /**
     * Perform a transformation from a Source document to a Result document.
     *
     * @param source   The input for the source tree. May be null if and only if an
     *                 initial template has been supplied.
     * @param receiver The destination for the streamed result tree.
     * @throws XPathException if the transformation fails. As a
     *                        special case, the method throws a TerminationException (a subclass
     *                        of XPathException) if the transformation was terminated using
     *                        xsl:message terminate="yes".
     */

    public void transform(Source source, Receiver receiver) throws XPathException {
        checkReadiness();
        if (source instanceof TreeInfo) {
            source = ((TreeInfo) source).getRootNode();
        }

        if (source instanceof SAXSource && config.getBooleanProperty(FeatureKeys.IGNORE_SAX_SOURCE_PARSER)) {
            // This option is provided to allow the parser set by applications such as Ant to be overridden by
            // the parser requested using FeatureKeys.SOURCE_PARSER
            ((SAXSource) source).setXMLReader(null);
        }

        boolean close = false;
        try {
            NodeInfo startNode = null;
            boolean wrap = true;
            boolean streaming = false;
            int validationMode = getSchemaValidationMode();
            Source underSource = source;
            if (source instanceof AugmentedSource) {
                Boolean localWrap = ((AugmentedSource) source).getWrapDocument();
                if (localWrap != null) {
                    wrap = localWrap;
                }
                close = ((AugmentedSource) source).isPleaseCloseAfterUse();
                int localValidate = ((AugmentedSource) source).getSchemaValidation();
                if (localValidate != Validation.DEFAULT) {
                    validationMode = localValidate;
                }
                if (validationMode == Validation.STRICT || validationMode == Validation.LAX) {
                    // If validation of a DOMSource or NodeInfo is requested, we must copy it, we can't wrap it
                    wrap = false;
                }
                underSource = ((AugmentedSource) source).getContainedSource();
            }
            Source s2 = config.getSourceResolver().resolveSource(underSource, config);
            if (s2 != null) {
                underSource = s2;
            }
            if (wrap && (underSource instanceof NodeInfo || underSource instanceof DOMSource)) {
                // Bug 2929: don't do space-stripping twice
                if (globalContextItem instanceof SpaceStrippedNode && ((SpaceStrippedNode) globalContextItem).getUnderlyingNode() == underSource) {
                    startNode = (SpaceStrippedNode) globalContextItem;
                } else if (globalContextItem == source) {
                    // if globalContextItem exists, then it will already have been space-stripped
                    startNode = (NodeInfo) globalContextItem;
                } else {
                    startNode = prepareInputTree(underSource);
                    if (startNode == null) {
                        throw new XPathException("The transformation source is a whitespace text node that is stripped by the transformation");
                    }
                }
                String uri = underSource.getSystemId();
                TreeInfo root = startNode.getTreeInfo();
                if (root != null) {
                    registerDocument(startNode.getTreeInfo(), uri == null ? null : new DocumentURI(uri));
                }

            } else if (source == null) {
                if (initialMode != null) {
                    throw new XPathException("Either a source document or initial match selection must be specified for an initial mode", "XTDE0044");
                }
                if (initialTemplate == null && initialFunction == null) {
                    NamedTemplate t = ((PreparedStylesheet) getExecutable()).getNamedTemplate(
                            new StructuredQName("", NamespaceConstant.XSLT, "initial-template"));
                    if (t == null) {
                        throw new XPathException("Either a source document, an initial template or an initial function must be specified");
                    } else {
                        initialTemplate = t;
                    }
                }
            } else {
                Mode mode = getInitialMode();
                if (mode == null) {// || (initialMode != null && mode.isEmpty())) {
                    throw new XPathException("Requested initial mode " +
                                                     (initialMode == null ? "" : initialMode.getModeName().getDisplayName()) +
                                                     " does not exist", "XTDE0045");
                }
                if (mode.isDeclaredStreamable()) {
                    if (source instanceof StreamSource || source instanceof SAXSource) {
                        streaming = true;
                        transformStream(source, mode, receiver);
                    } else {
                        throw new XPathException("Requested initial mode " +
                                                         (initialMode == null ? "" : initialMode.getModeName().getDisplayName()) +
                                                         " is streamable: must supply a SAXSource or StreamSource", "SXST0061");
                    }
                } else {
                    // The input is a SAXSource or StreamSource or AugmentedSource, or
                    // a DOMSource with wrap=no: build the document tree

                    startNode = makeSourceTree(source, close, validationMode);
                }
            }
            if (!streaming) {
                transformDocument(startNode, receiver);
            }

        } catch (TerminationException err) {
            //System.err.println("Processing terminated using xsl:message");
            if (!err.hasBeenReported()) {
                reportFatalError(err);
            }
            throw err;
        } catch (XPathException err) {
            Throwable cause = err.getException();
            if (cause != null && cause instanceof SAXParseException) {
                // This generally means the error was already reported.
                // But if a RuntimeException occurs in Saxon during a callback from
                // the Crimson parser, Crimson wraps this in a SAXParseException without
                // reporting it further.
                SAXParseException spe = (SAXParseException) cause;
                cause = spe.getException();
                if (cause instanceof RuntimeException) {
                    reportFatalError(err);
                }
            } else {
                reportFatalError(err);
            }
            throw err;
        } finally {
            inUse = false;
            if (close && source instanceof AugmentedSource) {
                ((AugmentedSource) source).close();
            }
            if (messageReceiver != null) {
                messageReceiver.close();
            }
            //principalResultURI = null;
        }
    }

    /**
     * Make a source tree from a source supplied as a StreamSource or SAXSource
     * @param source the source
     * @param close true if the source is to be closed after use
     * @param validationMode indicates whether the source should be schema-validated
     * @return the root of the constructed tree
     * @throws XPathException if tree construction fails
     */

    public NodeInfo makeSourceTree(Source source, boolean close, int validationMode) throws XPathException {
        if (source instanceof SAXSource && config.getBooleanProperty(FeatureKeys.IGNORE_SAX_SOURCE_PARSER)) {
            // This option is provided to allow the parser set by applications such as Ant to be overridden by
            // the parser requested using FeatureKeys.SOURCE_PARSER
            ((SAXSource) source).setXMLReader(null);
        }
        Builder sourceBuilder = makeBuilder();
        if (sourceBuilder instanceof TinyBuilder) {
            ((TinyBuilder) sourceBuilder).setStatistics(Statistics.SOURCE_DOCUMENT_STATISTICS);
        }
        //Sender sender = new Sender(sourceBuilder.getPipelineConfiguration());
        Receiver r = sourceBuilder;
        if (config.isStripsAllWhiteSpace() || isStylesheetContainingStripSpace() ||
                validationMode == Validation.STRICT || validationMode == Validation.LAX) {
            r = makeStripper(sourceBuilder);
        }
        if (isStylesheetStrippingTypeAnnotations()) {
            r = config.getAnnotationStripper(r);
        }
        PipelineConfiguration pipe = sourceBuilder.getPipelineConfiguration();
        pipe.getParseOptions().setSchemaValidationMode(validationMode);
        r.setPipelineConfiguration(pipe);
        Sender.send(source, r, null);
        if (close) {
            ((AugmentedSource) source).close();
        }
        NodeInfo doc = sourceBuilder.getCurrentRoot();
        globalContextItem = doc;
        sourceBuilder.reset();
        if (source.getSystemId() != null) {
            registerDocument(doc.getTreeInfo(), new DocumentURI(source.getSystemId()));
        }
        return doc;
    }

    /**
     * Perform a transformation from a Source document to a Result document.
     *
     * @param source            The input for the source tree. May be null if and only if an
     *                          initial template has been supplied.
     * @param outputDestination The destination for the result sequence.
     * @throws XPathException if the transformation fails. As a
     *                        special case, the method throws a TerminationException (a subclass
     *                        of XPathException) if the transformation was terminated using
     *                        xsl:message terminate="yes".
     */

    public void applyTemplates(Sequence source, Receiver outputDestination) throws XPathException {
        checkReadiness();
        boolean close = false;

        try {
            Mode mode = initialMode;
            if (mode == null) {
                mode = ((PreparedStylesheet) executable).getRuleManager().getUnnamedMode();
            }
            if (mode == null) {
                throw new XPathException("Requested initial mode " +
                                                 (initialMode == null ? "#unnamed" : initialMode.getModeName().getDisplayName()) +
                                                 " does not exist", "XTDE0045");
            }

            warningIfStreamable(mode);

            openMessageEmitter();

            // In tracing/debugging mode, evaluate all the global variables first
//            if (traceListener != null) {
//                preEvaluateGlobals(initialContext);
//            }

            // Determine whether we need to close the output stream at the end. We
            // do this if the Result object is a StreamResult and is supplied as a
            // system ID, not as a Writer or OutputStream

            boolean mustClose = false;
            //        boolean mustClose = (outputDestination instanceof StreamResult &&
            //                ((StreamResult) outputDestination).getOutputStream() == null);

            principalResult = outputDestination;
            if (principalResultURI == null) {
                principalResultURI = outputDestination.getSystemId();
            }

            // Process the source document by applying template rules to the initial context node

            ParameterSet ordinaryParams = null;
            if (initialTemplateParams != null) {
                ordinaryParams = new ParameterSet(initialTemplateParams);
            }
            ParameterSet tunnelParams = null;
            if (initialTemplateTunnelParams != null) {
                tunnelParams = new ParameterSet(initialTemplateTunnelParams);
            }

            XPathContextMajor initialContext = newXPathContext();
            initialContext.createThreadManager();
            initialContext.setOrigin(this);

            final Mode finalMode = mode;

            SequenceIterator iter = source.iterate();

            MappingFunction preprocessor = new MappingFunction() {
                public SequenceIterator map(Item item) throws XPathException {
                    if (item instanceof NodeInfo) {
                        NodeInfo node = (NodeInfo) item;
                        if (node.getConfiguration() == null) {
                            // must be a non-standard document implementation
                            throw new XPathException("The supplied source document must be associated with a Configuration");
                        }

                        if (!node.getConfiguration().isCompatible(executable.getConfiguration())) {
                            throw new XPathException(
                                    "Source document and stylesheet must use the same or compatible Configurations",
                                    SaxonErrorCode.SXXP0004);
                        }

                        if (node.getTreeInfo().isTyped() && !executable.isSchemaAware()) {
                            throw new XPathException("Cannot use a schema-validated source document unless the stylesheet is schema-aware");
                        }

                        if (isStylesheetStrippingTypeAnnotations() && node != globalContextItem) {
                            TreeInfo docInfo = node.getTreeInfo();
                            if (docInfo.isTyped()) {
                                TypeStrippedDocument strippedDoc = new TypeStrippedDocument(docInfo);
                                node = strippedDoc.wrap(node);
                            }
                        }

                        if (isStylesheetContainingStripSpace() && isStripSourceTree() && !(node instanceof SpaceStrippedNode)
                                && node != globalContextItem) {
                            SpaceStrippedDocument strippedDoc = new SpaceStrippedDocument(node.getTreeInfo(), getSpaceStrippingRule());
                            // Edge case: the item might itself be a whitespace text node that is stripped
                            if (!SpaceStrippedNode.isPreservedNode(node, strippedDoc, node.getParent())) {
                                return EmptyIterator.getInstance();
                            }
                            node = strippedDoc.wrap(node);
                        }

                        if (getAccumulatorManager() != null) {
                            getAccumulatorManager().setApplicableAccumulators(node.getTreeInfo(), finalMode.getAccumulators());
                        }
                        return SingletonIterator.makeIterator(node);
                    } else {
                        return SingletonIterator.makeIterator(item);
                    }
                }
            };

            iter = new MappingIterator(iter, preprocessor);
            initialContext.setCurrentIterator(new FocusTrackingIterator(iter));
            initialContext.setCurrentMode(mode.getDeclaringComponent());  // TODO: it should be the top-level override of the mode
            initialContext.setCurrentComponent(mode.getDeclaringComponent());

            outputDestination = openResult(outputDestination, initialContext);

            TailCall tc = mode.applyTemplates(ordinaryParams, tunnelParams, initialContext, ExplicitLocation.UNKNOWN_LOCATION);
            while (tc != null) {
                tc = tc.processLeavingTail();
            }

            initialContext.waitForChildThreads();

            closeResult(outputDestination, mustClose, initialContext);

        } catch (TerminationException err) {
            //System.err.println("Processing terminated using xsl:message");
            if (!err.hasBeenReported()) {
                reportFatalError(err);
            }
            throw err;
        } catch (XPathException err) {
            Throwable cause = err.getException();
            if (cause != null && cause instanceof SAXParseException) {
                // This generally means the error was already reported.
                // But if a RuntimeException occurs in Saxon during a callback from
                // the Crimson parser, Crimson wraps this in a SAXParseException without
                // reporting it further.
                SAXParseException spe = (SAXParseException) cause;
                cause = spe.getException();
                if (cause instanceof RuntimeException) {
                    reportFatalError(err);
                }
            } else {
                reportFatalError(err);
            }
            throw err;
        } finally {
            inUse = false;
            closeMessageEmitter();
            if (traceListener != null) {
                traceListener.close();
            }
            if (close && source instanceof AugmentedSource) {
                ((AugmentedSource) source).close();
            }
            principalResultURI = null;
        }
    }


    /**
     * Prepare an input tree for processing. This is used when either the initial
     * input, or a Source returned by the document() function, is a NodeInfo or a
     * DOMSource. The preparation consists of wrapping a DOM document inside a wrapper
     * that implements the NodeInfo interface, and/or adding a space-stripping wrapper
     * if the stylesheet strips whitespace nodes, and/or adding a type-stripping wrapper
     * if the stylesheet strips input type annotations.
     * <p/>
     * This method is intended for internal use.
     *
     * @param source the input tree. Must be either a DOMSource or a NodeInfo
     * @return the NodeInfo representing the input node, suitably wrapped. Exceptionally,
     * the the source is a whitespace text node that is itself stripped, return null.
     */

    public NodeInfo prepareInputTree(Source source) {
        NodeInfo start = getConfiguration().unravel(source);
        // Stripping type annotations happens before stripping of whitespace
        if (isStylesheetStrippingTypeAnnotations()) {
            TreeInfo docInfo = start.getTreeInfo();
            if (docInfo.isTyped()) {
                TypeStrippedDocument strippedDoc = new TypeStrippedDocument(docInfo);
                start = strippedDoc.wrap(start);
            }
        }
        if (stripSourceTrees && isStylesheetContainingStripSpace()) {
            TreeInfo docInfo = start.getTreeInfo();
            SpaceStrippedDocument strippedDoc = new SpaceStrippedDocument(docInfo, getSpaceStrippingRule());
            // Edge case: the global context item might itself be a whitespace text node that is stripped
            if (!SpaceStrippedNode.isPreservedNode(start, strippedDoc, start.getParent())) {
                return null;
            }
            start = strippedDoc.wrap(start);
        }
        return start;
    }

    /**
     * Transform a source XML document supplied as a tree. <br>
     * <p/>
     * This method is intended for internal use. External applications should use
     * the {@link #transform} method, which is part of the JAXP interface. Note that
     * <code>NodeInfo</code> implements the JAXP <code>Source</code> interface, so
     * it may be supplied directly to the transform() method.
     *
     * @param startNode         A Node that identifies the source document to be
     *                          transformed and the node where the transformation should start.
     *                          May be null if the transformation is to start using an initial template.
     * @param outputDestination The output destination
     * @throws XPathException if any dynamic error occurs
     */

    public void transformDocument(NodeInfo startNode, Receiver outputDestination)
            throws XPathException {

        if (executable == null) {
            throw new XPathException("Stylesheet has not been compiled");
        }

        openMessageEmitter();

        // Determine whether we need to close the output stream at the end. We
        // do this if the Result object is a StreamResult and is supplied as a
        // system ID, not as a Writer or OutputStream

        boolean mustClose = false;
//        boolean mustClose = (outputDestination instanceof StreamResult &&
//                ((StreamResult) outputDestination).getOutputStream() == null);

        try {
            principalResult = outputDestination;
            if (principalResultURI == null) {
                principalResultURI = outputDestination.getSystemId();
            }

            XPathContextMajor initialContext = newXPathContext();
            initialContext.createThreadManager();
            initialContext.setOrigin(this);

            if (startNode != null) {

                globalContextItem = startNode.getRoot();

                if (startNode.getConfiguration() == null) {
                    // must be a non-standard document implementation
                    throw new XPathException("The supplied source document must be associated with a Configuration");
                }

                if (!startNode.getConfiguration().isCompatible(executable.getConfiguration())) {
                    throw new XPathException(
                            "Source document and stylesheet must use the same or compatible Configurations",
                            SaxonErrorCode.SXXP0004);
                }

                if (startNode.getTreeInfo().isTyped() && !executable.isSchemaAware()) {
                    throw new XPathException("Cannot use a schema-validated source document unless the stylesheet is schema-aware");
                }
                UnfailingIterator currentIter = SingletonIterator.makeIterator(startNode);
                FocusIterator focus = new FocusTrackingIterator(currentIter);
                if (initialTemplate != null) {
                    focus.next();
                }
                initialContext.setCurrentIterator(focus);
            }

            // In tracing/debugging mode, evaluate all the global variables first
            /* See bug 2606
            if (traceListener != null) {
                preEvaluateGlobals(initialContext);
            } */

            outputDestination = openResult(outputDestination, initialContext);

            // Process the source document by applying template rules to the initial context node

            ParameterSet ordinaryParams = null;
            if (initialTemplateParams != null) {
                ordinaryParams = new ParameterSet(initialTemplateParams);
            }
            ParameterSet tunnelParams = null;
            if (initialTemplateTunnelParams != null) {
                tunnelParams = new ParameterSet(initialTemplateTunnelParams);
            }

            if (initialTemplate == null) {
                Mode mode = initialMode;
                if (mode == null) {
                    mode = ((PreparedStylesheet) executable).getRuleManager().getUnnamedMode();
                }
//                if (initialMode != null && mode.isEmpty()) {
//                    throw new XPathException("Requested initial mode " +
//                            (initialMode == null ? "" : initialMode.getModeName().getDisplayName()) +
//                            " does not exist", "XTDE0045");
//                }
                Component modeComponent = mode.getDeclaringComponent();
                if (modeComponent.getVisibility() == Visibility.PRIVATE &&
                        ((StylesheetPackage) executable.getTopLevelPackage()).isDeclaredModes() &&
                        !mode.isUnnamedMode()) {
                    throw new XPathException((initialMode == null ? "" : initialMode.getModeTitle()) +
                            " has private visibility", "XTDE0045");
                }

                if (getAccumulatorManager() != null) {
                    getAccumulatorManager().setApplicableAccumulators(startNode.getTreeInfo(), mode.getAccumulators());
                }
                warningIfStreamable(mode);

                if (startNode.getNodeKind() == Type.DOCUMENT && !getConfiguration().getBooleanProperty(FeatureKeys.SUPPRESS_XSLT_NAMESPACE_CHECK)) {
                    // Check for the common error where the source document is in a namespace, but the stylesheet is not
                    // designed to match that namespace.
                    NodeInfo topElement = startNode.iterateAxis(AxisInfo.CHILD, NodeKindTest.ELEMENT).next();
                    if (topElement != null) {
                        String uri = topElement.getURI();
                        Set<String> explicitNamespaces = mode.getExplicitNamespaces(getConfiguration().getNamePool());
                        if (!explicitNamespaces.isEmpty() && !explicitNamespaces.contains(uri)) {
                            // We've established that there are no template rules matching the top-level element.
                            // But that's not a strong enough test, especially with document types that have an envelope/payload
                            // structure. We only report a warning if the set of namespaces in stylesheet patterns is completely
                            // disjoint from the set of namespaces on element names in the source document.
                            AxisIterator allElements = startNode.iterateAxis(AxisInfo.DESCENDANT, NodeKindTest.ELEMENT);
                            boolean found = false;
                            NodeInfo element;
                            while ((element = allElements.next()) != null) {
                                String ns = element.getURI();
                                if (explicitNamespaces.contains(ns)) {
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) {
                                String suffix = "(Use --suppressXsltNamespaceCheck:on to avoid this warning)";
                                if (explicitNamespaces.size() == 1 && explicitNamespaces.contains("")) {
                                    warning("The source document is in namespace " + uri +
                                                    ", but all the template rules match elements in no namespace " + suffix, SaxonErrorCode.SXXP0005, null);
                                } else if (uri.equals("")) {
                                    warning("The source document is in no namespace" +
                                                    ", but the template rules all expect elements in a namespace " + suffix, SaxonErrorCode.SXXP0005, null);
                                } else {
                                    warning("The source document is in namespace " + uri +
                                                    ", but none of the template rules match elements in this namespace " + suffix, SaxonErrorCode.SXXP0005, null);
                                }
                            }
                        }
                    }
                }
                // TODO: should start with the top-level override of the mode
                initialContext.setCurrentMode(mode.getDeclaringComponent());
                initialContext.setCurrentComponent(mode.getDeclaringComponent());
                TailCall tc = mode.applyTemplates(ordinaryParams, tunnelParams, initialContext, ExplicitLocation.UNKNOWN_LOCATION);
                while (tc != null) {
                    tc = tc.processLeavingTail();
                }
            } else {
                NamedTemplate t = initialTemplate;
                XPathContextMajor c2 = initialContext.newContext();
                initialContext.setOrigin(this);
                c2.setCurrentComponent(t.getDeclaringComponent());
                c2.openStackFrame(t.getStackFrameMap());
                c2.setLocalParameters(ordinaryParams);
                c2.setTunnelParameters(tunnelParams);

                TailCall tc = t.expand(c2);
                while (tc != null) {
                    tc = tc.processLeavingTail();
                }
            }

            initialContext.waitForChildThreads();

            closeResult(outputDestination, mustClose, initialContext);

        } finally {
            closeMessageEmitter();
            if (traceListener != null) {
                traceListener.close();
            }

        }
    }

    private void warningIfStreamable(Mode mode) {
        if (mode.isDeclaredStreamable()) {
            warning((initialMode == null ? "" : initialMode.getModeTitle()) +
                            " is streamable, but the input is not supplied as a stream", null, null);
        }
    }

    /**
     * Transform a source XML document supplied as a tree. <br>
     * <p/>
     * This method is intended for internal use. External applications should use
     * the {@link #transform} method, which is part of the JAXP interface. Note that
     * <code>NodeInfo</code> implements the JAXP <code>Source</code> interface, so
     * it may be supplied directly to the transform() method.
     *
     * @param initialTemplateName the entry point, the name of a named template
     * @param outputDestination   The output destination
     * @throws XPathException if any dynamic error occurs
     */

    public void callTemplate(StructuredQName initialTemplateName, Receiver outputDestination)
            throws XPathException {
        checkReadiness();
        if (initialMode != null && !initialMode.getModeName().equals(
                ((StylesheetPackage)getExecutable().getTopLevelPackage()).getDefaultMode())) {
            throw new XPathException("Initial mode and template cannot both be defined", "XTDE0047");
        }
        openMessageEmitter();

        // Determine whether we need to close the output stream at the end. We
        // do this if the Result object is a StreamResult and is supplied as a
        // system ID, not as a Writer or OutputStream

        boolean mustClose = false;

        try {
            principalResult = outputDestination;
            if (principalResultURI == null) {
                principalResultURI = outputDestination.getSystemId();
            }

            XPathContextMajor initialContext = newXPathContext();
            initialContext.createThreadManager();
            initialContext.setOrigin(this);

            if (globalContextItem != null) {

                if (globalContextItem instanceof NodeInfo) {
                    NodeInfo startNode = (NodeInfo) globalContextItem;
                    if (startNode.getConfiguration() == null) {
                        // must be a non-standard document implementation
                        throw new XPathException("The supplied source document must be associated with a Configuration");
                    }

                    if (!startNode.getConfiguration().isCompatible(executable.getConfiguration())) {
                        throw new XPathException(
                                "Source document and stylesheet must use the same or compatible Configurations",
                                SaxonErrorCode.SXXP0004);
                    }

                    if (startNode.getTreeInfo().isTyped() && !executable.isSchemaAware()) {
                        throw new XPathException("Cannot use a schema-validated source document unless the stylesheet is schema-aware");
                    }
                }

                initialContext.setCurrentIterator(new ManualIterator(globalContextItem));

            }


            // In tracing/debugging mode, evaluate all the global variables first
            /* See bug 2606
            if (traceListener != null) {
                preEvaluateGlobals(initialContext);
            } */

            outputDestination = openResult(outputDestination, initialContext);

            // Process the source document by applying template rules to the initial context node

            ParameterSet ordinaryParams = null;
            if (initialTemplateParams != null) {
                ordinaryParams = new ParameterSet(initialTemplateParams);
            }
            ParameterSet tunnelParams = null;
            if (initialTemplateTunnelParams != null) {
                tunnelParams = new ParameterSet(initialTemplateTunnelParams);
            }

            StylesheetPackage pack = (StylesheetPackage) executable.getTopLevelPackage();
            Component initialComponent = pack.getComponent(new SymbolicName(StandardNames.XSL_TEMPLATE, initialTemplateName));
            if (initialComponent == null) {
                throw new XPathException("Template " + initialTemplateName.getDisplayName() + " does not exist (or is not public)", "XTDE0040");
            }
            //NamedTemplate t = ((PreparedStylesheet) executable).getNamedTemplate(initialTemplateName);
            NamedTemplate t = (NamedTemplate) initialComponent.getActor();

            XPathContextMajor c2 = initialContext.newContext();
            initialContext.setOrigin(this);
            c2.setCurrentComponent(initialComponent);
            c2.openStackFrame(t.getStackFrameMap());
            c2.setLocalParameters(ordinaryParams);
            c2.setTunnelParameters(tunnelParams);

            TailCall tc = t.expand(c2);
            while (tc != null) {
                tc = tc.processLeavingTail();
            }

            initialContext.waitForChildThreads();

            closeResult(outputDestination, mustClose, initialContext);
        } finally {
            if (traceListener != null) {
                traceListener.close();
            }
            closeMessageEmitter();
            inUse = false;
        }
    }


    /**
     * Transform a source XML document supplied as a stream, in streaming mode. <br>
     * <p/>
     * This method is intended for internal use. External applications should use
     * the {@link #transform} method, which is part of the JAXP interface. Note that
     * <code>NodeInfo</code> implements the JAXP <code>Source</code> interface, so
     * it may be supplied directly to the transform() method.
     *
     * @param source the principal input document, supplied as a
     *               {@link SAXSource} or {@link StreamSource}
     * @param mode   the initial mode, which must be a streaming mode
     * @param result The output destination
     * @throws XPathException if any dynamic error occurs
     */

    private void transformStream(Source source, Mode mode, Receiver result)
            throws XPathException {
        openMessageEmitter();

        // Determine whether we need to close the output stream at the end. We
        // do this if the Result object is a StreamResult and is supplied as a
        // system ID, not as a Writer or OutputStream

        boolean mustClose = result instanceof StreamResult &&
                ((StreamResult) result).getOutputStream() == null;

        try {
            principalResult = result;
            if (principalResultURI == null) {
                principalResultURI = result.getSystemId();
            }

            XPathContextMajor initialContext = newXPathContext();
            initialContext.setOrigin(this);

            globalContextItem = null;

            result = openResult(result, initialContext);

            // Process the source document by applying template rules to the initial context node

            if (!mode.isDeclaredStreamable()) {
                if (config.getBooleanProperty(FeatureKeys.STREAMING_FALLBACK)) {
                    warning("Mode is not streamable; attempting fallback to non-streamed evaluation", "", null);
                    TreeInfo doc = config.buildDocumentTree(source);
                    initialMode = mode;
                    transformDocument(doc.getRootNode(), result);
                    return;
                } else {
                    throw new XPathException("mode supplied to transformStream() must be streamable");
                }
            }
            Receiver despatcher = config.makeStreamingTransformer(initialContext, mode);
            if (despatcher == null) {
                throw new XPathException("Streaming requires Saxon-EE");
            }
            if (config.isStripsAllWhiteSpace() || isStylesheetContainingStripSpace()) {
                despatcher = makeStripper(despatcher);
            }
            PipelineConfiguration pipe = despatcher.getPipelineConfiguration();
            pipe.getParseOptions().setSchemaValidationMode(validationMode);
            boolean verbose = getConfiguration().isTiming();
            if (verbose) {
                getConfiguration().getLogger().info("Streaming " + source.getSystemId());
            }
            try {
                Sender.send(source, despatcher, null);
            } catch (QuitParsingException e) {
                if (verbose) {
                    getConfiguration().getLogger().info("Streaming " + source.getSystemId() + " : early exit");
                }
            }

            closeResult(result, mustClose, initialContext);

        } finally {

            if (traceListener != null) {
                traceListener.close();
            }
            closeMessageEmitter();

        }

    }


    /**
     * Get a receiver to which the input to this transformation can be supplied
     * as a stream of events, causing the transformation to be executed in streaming mode. <br>
     * <p/>
     * This method is intended for internal use. External applications should use
     * the {@link #transform} method, which is part of the JAXP interface. Note that
     * <code>NodeInfo</code> implements the JAXP <code>Source</code> interface, so
     * it may be supplied directly to the transform() method.
     * <p/>
     *
     * @param mode   the initial mode, which must be a streaming mode
     * @param result The output destination
     * @return a receiver to which events can be streamed
     * @throws XPathException if any dynamic error occurs
     */

    /*@Nullable*/
    public Receiver getStreamingReceiver(Mode mode, Receiver result)
            throws XPathException {
        // TODO: this method does not check that the Controller is not already in use, nor does it mark it as being in use.
        // System.err.println("*** TransformDocument");
        if (executable == null) {
            throw new XPathException("Stylesheet has not been compiled");
        }

        openMessageEmitter();

        // Determine whether we need to close the output stream at the end. We
        // do this if the Result object is a StreamResult and is supplied as a
        // system ID, not as a Writer or OutputStream

        final boolean mustClose =
                result instanceof StreamResult && ((StreamResult) result).getOutputStream() == null;

        principalResult = result;
        if (principalResultURI == null) {
            principalResultURI = result.getSystemId();
        }

        final XPathContextMajor initialContext = newXPathContext();
        initialContext.setOrigin(this);

        globalContextItem = null;
        final Result result2 = openResult(result, initialContext);

        // Process the source document by applying template rules to the initial context node

        if (!mode.isDeclaredStreamable()) {
            throw new XPathException("mode supplied to getStreamingReceiver() must be streamable");
        }
        Receiver despatcher = config.makeStreamingTransformer(initialContext, mode);
        if (despatcher == null) {
            throw new XPathException("Streaming requires Saxon-EE");
        }
        if (config.isStripsAllWhiteSpace() || isStylesheetContainingStripSpace()) {
            despatcher = makeStripper(despatcher);
        }
        despatcher.setPipelineConfiguration(makePipelineConfiguration());

        return new ProxyReceiver(despatcher) {
            public void close() throws XPathException {
                if (traceListener != null) {
                    traceListener.close();
                }
                closeResult(result2, mustClose, initialContext);
                closeMessageEmitter();
            }
        };

    }


    private void closeResult(Result result, boolean mustClose, XPathContextMajor initialContext) throws XPathException {
        Receiver out = initialContext.getReceiver();
        if (buildTree) {
            out.endDocument();
        }
        out.close();

        if (mustClose && result instanceof StreamResult) {
            OutputStream os = ((StreamResult) result).getOutputStream();
            if (os != null) {
                try {
                    os.close();
                } catch (IOException err) {
                    throw new XPathException(err);
                }
            }
        }
    }

    private Receiver openResult(Receiver result, XPathContextMajor initialContext) throws XPathException {

        Receiver receiver = result;

        // if this is the implicit XSLT result document, and if the executable is capable
        // of creating a secondary result document, then add a filter to check the first write

        boolean openNow = false;
        if (buildTree && getExecutable().createsSecondaryResult()) {
            receiver = new ImplicitResultChecker(receiver, this);
            receiver.setPipelineConfiguration(makePipelineConfiguration());
        } else {
            openNow = true;
        }

        receiver.getPipelineConfiguration().setController(this);
        SequenceReceiver out;
        if (result instanceof ComplexContentOutputter) {
            out = (SequenceReceiver) receiver;
        } else if (buildTree) {
            out = ComplexContentOutputter.makeComplexContentReceiver(receiver, null);
        } else if (receiver instanceof SequenceReceiver) {
            out = (SequenceReceiver) receiver;
        } else {
            out = new TreeReceiver(receiver);
        }
        initialContext.setReceiver(out);

        if (openNow) {
            out.open();
            if (buildTree) {
                out.startDocument(0);
            }
        }
        return result;
    }

    private void openMessageEmitter() throws XPathException {
        if (getMessageEmitter() == null) {
            Receiver me = makeMessageReceiver();
            setMessageEmitter(me);
            if (me instanceof Emitter && ((Emitter) me).getWriter() == null) {
                ((Emitter) me).setStreamResult(getConfiguration().getLogger().asStreamResult());
            }
        }
        getMessageEmitter().open();
    }


    private void closeMessageEmitter() throws XPathException {
        Receiver me = getMessageEmitter();
        if (me != null) {
            me.close();
        }
    }


    /**
     * Pre-evaluate global variables (when debugging/tracing).
     * <p/>
     * This method is intended for internal use.
     *
     * @param context the dynamic context for evaluating the global variables
     * @throws XPathException - should not happen.
     */

    public void preEvaluateGlobals(XPathContext context) throws XPathException {
        for (PackageData pack : getExecutable().getPackages()) {
            for (GlobalVariable var : pack.getGlobalVariableList()) {
                if (!var.isUnused()) {
                    try {
                        var.evaluateVariable(context, var.getDeclaringComponent());
                    } catch (XPathException err) {
                        // Don't report an exception unless the variable is actually evaluated
                        SingletonClosure closure = new SingletonClosure(new ErrorExpression(err), context);
                        getBindery(var.getPackageData()).setGlobalVariable(var, closure);
                    }
                }
            }
        }
    }

    /**
     * Register the dependency of one variable ("one") upon another ("two"), throwing an exception if this would establish
     * a cycle of dependencies.
     *
     * @param one the first (dependent) variable
     * @param two the second (dependee) variable
     * @throws XPathException if adding this dependency creates a cycle of dependencies among global variables.
     */

    public synchronized void registerGlobalVariableDependency(GlobalVariable one, GlobalVariable two) throws XPathException {
        if (one == two) {
            throw new XPathException.Circularity("Circular dependency among global variables: "
                                                         + one.getVariableQName().getDisplayName() + " depends on its own value");
        }
        Set<GlobalVariable> transitiveDependencies = globalVariableDependencies.get(two);
        if (transitiveDependencies != null) {
            if (transitiveDependencies.contains(one)) {
                throw new XPathException.Circularity("Circular dependency among variables: "
                                                             + one.getVariableQName().getDisplayName() + " depends on the value of "
                                                             + two.getVariableQName().getDisplayName() + ", which depends directly or indirectly on the value of "
                                                             + one.getVariableQName().getDisplayName());
            }
            for (GlobalVariable var : transitiveDependencies) {
                // register the transitive dependencies
                registerGlobalVariableDependency(one, var);
            }
        }
        Set<GlobalVariable> existingDependencies = globalVariableDependencies.get(one);
        if (existingDependencies == null) {
            existingDependencies = new HashSet<GlobalVariable>();
            globalVariableDependencies.put(one, existingDependencies);
        }
        existingDependencies.add(two);

    }


    /**
     * Set the current date and time for this query or transformation.
     * This method is provided primarily for testing purposes, to allow tests to be run with
     * a fixed date and time. The supplied date/time must include a timezone, which is used
     * as the implicit timezone.
     * <p/>
     * <p>Note that comparisons of date/time values currently use the implicit timezone
     * taken from the system clock, not from the value supplied here.</p>
     *
     * @param dateTime the date/time value to be used as the current date and time
     * @throws IllegalStateException             if a current date/time has already been
     *                                           established by calling getCurrentDateTime(), or by a previous call on setCurrentDateTime()
     * @throws net.sf.saxon.trans.XPathException if the supplied dateTime contains no timezone
     */

    public void setCurrentDateTime(/*@NotNull*/ DateTimeValue dateTime) throws XPathException {
        if (currentDateTime == null) {
            if (dateTime.getComponent(AccessorFn.Component.TIMEZONE) == null) {
                throw new XPathException("No timezone is present in supplied value of current date/time");
            }
            currentDateTime = dateTime;
            dateTimePreset = true;
        } else {
            throw new IllegalStateException(
                    "Current date and time can only be set once, and cannot subsequently be changed");
        }
    }

    /**
     * Get the current date and time for this query or transformation.
     * All calls during one transformation return the same answer.
     *
     * @return Get the current date and time. This will deliver the same value
     * for repeated calls within the same transformation
     */

    /*@Nullable*/
    public DateTimeValue getCurrentDateTime() {
        if (currentDateTime == null) {
            currentDateTime = new DateTimeValue(new GregorianCalendar(), true);
        }
        return currentDateTime;
    }

    /**
     * Get the implicit timezone for this query or transformation
     *
     * @return the implicit timezone as an offset in minutes
     */

    public int getImplicitTimezone() {
        return getCurrentDateTime().getTimezoneInMinutes();
    }

    /////////////////////////////////////////
    // Methods for handling dynamic context
    /////////////////////////////////////////

    /**
     * Make an XPathContext object for expression evaluation.
     * <p/>
     * This method is intended for internal use.
     *
     * @return the new XPathContext
     */

    public XPathContextMajor newXPathContext() {
        XPathContextMajor c = new XPathContextMajor(this);
        c.setCurrentOutputUri(principalResultURI);
        return c;
    }

    /**
     * Set the last remembered node, for node numbering purposes.
     * <p/>
     * This method is strictly for internal use only.
     *
     * @param node   the node in question
     * @param number the number of this node
     */

    public synchronized void setRememberedNumber(NodeInfo node, int number) {
        lastRememberedNode = node;
        lastRememberedNumber = number;
    }

    /**
     * Get the number of a node if it is the last remembered one.
     * <p/>
     * This method is strictly for internal use only.
     *
     * @param node the node for which remembered information is required
     * @return the number of this node if known, else -1.
     */

    public synchronized int getRememberedNumber(NodeInfo node) {
        if (lastRememberedNode == node) {
            return lastRememberedNumber;
        }
        return -1;
    }

    /**
     * Indicate whether document projection should be used, and supply the PathMap used to control it.
     * Note: this is available only under Saxon-EE.
     *
     * @param pathMap a path map to be used for projecting source documents
     */

    public void setUseDocumentProjection(PathMap pathMap) {
        this.pathMap = pathMap;
    }

    /**
     * Get the path map used for document projection, if any.
     *
     * @return the path map to be used for document projection, if one has been supplied; otherwise null
     */

    /*@Nullable*/
    public PathMap getPathMapForDocumentProjection() {
        return pathMap;
    }

    /**
     * Get the stack of attribute sets being evaluated (for detection of cycles)
     *
     * @return the attribute set evaluation stack
     */

    public Stack<AttributeSet> getAttributeSetEvaluationStack() {
        return attributeSetEvaluationStack;
    }

    /**
     * Get the cache of stylesheets (cached during calls on fn:transform()) for this query or transformation.
     *
     * @return the stylesheet cache
     */

    public synchronized StylesheetCache getStylesheetCache() {
        if (stylesheetCache == null) {
            this.stylesheetCache = new StylesheetCache();
        }
        return stylesheetCache;
    }


}

