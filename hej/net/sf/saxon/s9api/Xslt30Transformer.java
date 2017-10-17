////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.Configuration;
import net.sf.saxon.Controller;
import net.sf.saxon.PreparedStylesheet;
import net.sf.saxon.event.*;
import net.sf.saxon.expr.Component;
import net.sf.saxon.expr.XPathContextMajor;
import net.sf.saxon.expr.instruct.GlobalParameterSet;
import net.sf.saxon.expr.instruct.UserFunction;
import net.sf.saxon.expr.instruct.UserFunctionParameter;
import net.sf.saxon.expr.parser.ExplicitLocation;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.lib.*;
import net.sf.saxon.om.*;
import net.sf.saxon.serialize.ReconfigurableSerializer;
import net.sf.saxon.trans.Mode;
import net.sf.saxon.trans.SymbolicName;
import net.sf.saxon.trans.Visibility;
import net.sf.saxon.trans.XPathException;
import org.jetbrains.annotations.NotNull;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import java.io.File;
import java.io.OutputStream;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

/**
 * An <code>Xslt30Transformer</code> represents a compiled and loaded stylesheet ready for execution.
 * The <code>Xslt30Transformer</code> holds details of the dynamic evaluation context for the stylesheet.
 * <p/>
 * <p>The <code>Xslt30Transformer</code> differs from {@link XsltTransformer} is supporting new options
 * for invoking a stylesheet, corresponding to facilities defined in the XSLT 3.0 specification. However,
 * it is not confined to use with XSLT 3.0, and most of the new invocation facilities (for example,
 * calling a stylesheet-defined function directly) work equally well with XSLT 2.0 and in some cases
 * XSLT 1.0 stylesheets.</p>
 * <p/>
 * <p>An <code>Xslt30Transformer</code> must not be used concurrently in multiple threads.
 * It is safe, however, to reuse the object within a single thread to run the same
 * stylesheet several times. Running the stylesheet does not change the context
 * that has been established.</p>
 * <p/>
 * <p>An <code>Xslt30Transformer</code> is always constructed by running the <code>Load30</code>
 * method of an {@link XsltExecutable}.</p>
 * <p/>
 * <p>Unlike <code>XsltTransformer</code>, an <code>Xslt30Transformer</code> is not a <code>Destination</code>.
 * To pipe the results of one transformation into another, the target should be an <code>XsltTransfomer</code>
 * rather than an <code>Xslt30Transformer</code>.</p>
 * <p/>
 * <p>Evaluation of an Xslt30Transformer proceeds in a number of phases:</p>
 * <p/>
 * <ol>
 * <li>First, values may be supplied for stylesheet parameters and for the global context item. The
 * global context item is used when initializing global variables. Unlike earlier transformation APIs,
 * the global context item is quite independent of the "principal Source document".</li>
 * <li>The stylesheet may now be repeatedly invoked. Each invocation takes one of three forms:
 * <ol>
 * <li>Invocation by applying templates. In this case, the information required is (i) an initial
 * mode (which defaults to the unnamed mode), (ii) an initial match sequence, which is any
 * XDM value, which is used as the effective "select" expression of the implicit apply-templates
 * call, and (iii) optionally, values for the tunnel and non-tunnel parameters defined on the
 * templates that get invoked (equivalent to using <code>xsl:with-param</code> on the implicit
 * <code>apply-templates</code> call).</li>
 * <li>Invocation by calling a named template. In this case, the information required is
 * (i) the name of the initial template (which defaults to "xsl:initial-template"), and
 * (ii) optionally, values for the tunnel and non-tunnel parameters defined on the
 * templates that get invoked (equivalent to using <code>xsl:with-param</code> on the implicit
 * <code>call-template</code> instruction).</li>
 * <li>Invocation by calling a named function. In this case, the information required is
 * the sequence of arguments to the function call.</li>
 * </ol>
 * </li>
 * <li>Whichever invocation method is chosen, the result may either be returned directly, as an arbitrary
 * XDM value, or it may effectively be wrapped in an XML document. If it is wrapped in an XML document,
 * that document can be processed in a number of ways, for example it can be materialized as a tree in
 * memory, it can be serialized as XML or HTML, or it can be subjected to further transformation.</li>
 * </ol>
 * <p/>
 * <p>Once the stylesheet has been invoked (using any of these methods), the values of the global context
 * item and stylesheet parameters cannot be changed. If it is necessary to run another transformation with
 * a different context item or different stylesheet parameters, a new <code>Xslt30Transformer</code>
 * should be created from the original <code>XsltExecutable</code>.</p>
 *
 * @since 9.6
 */
public class Xslt30Transformer {

    private Processor processor;
    private Controller controller;
    private GlobalParameterSet globalParameterSet;
    private boolean primed = false;
    private boolean baseOutputUriWasSet = false;
    private Item globalContextItem = null;
    private boolean alreadyStripped;

    /**
     * Protected constructor
     *
     * @param processor        the S9API processor
     * @param controller       the Saxon controller object
     * @param staticParameters the static parameters supplied at stylesheet compile time
     */

    protected Xslt30Transformer(Processor processor, Controller controller, GlobalParameterSet staticParameters) {
        this.processor = processor;
        this.controller = controller;
        globalParameterSet = new GlobalParameterSet(staticParameters);
    }

    /**
     * Set the schema validation mode for the transformation. This indicates how source documents
     * loaded specifically for this transformation will be handled. This applies to the
     * principal source document if supplied as a SAXSource or StreamSource, and to all
     * documents loaded during the transformation using the <code>doc()</code>, <code>document()</code>,
     * or <code>collection()</code> functions.
     *
     * @param mode the validation mode. Passing null causes no change to the existing value.
     *             Passing {@link ValidationMode#DEFAULT} resets to the initial value, which determines
     *             the validation requirements from the Saxon Configuration.
     */

    public void setSchemaValidationMode(ValidationMode mode) {
        if (mode != null) {
            controller.setSchemaValidationMode(mode.getNumber());
        }
    }

    /**
     * Get the schema validation mode for the transformation. This indicates how source documents
     * loaded specifically for this transformation will be handled. This applies to the
     * principal source document if supplied as a SAXSource or StreamSource, and to all
     * documents loaded during the transformation using the <code>doc()</code>, <code>document()</code>,
     * or <code>collection()</code> functions.
     *
     * @return the validation mode.
     */

    public ValidationMode getSchemaValidationMode() {
        return ValidationMode.get(controller.getSchemaValidationMode());
    }


    /**
     * Supply the context item to be used when evaluating global variables and parameters. A call on
     * <code>setGlobalContextItem(node)</code> is equivalent to a call on <code>setGlobalContextItem(node, false)</code>:
     * that is, it is assumed that stripping of type annotations and whitespace text nodes has not yet been
     * performed. It is more efficient to do this stripping while constructing the source tree.
     *
     * @param globalContextItem the item to be used as the context item within the initializers
     *                          of global variables and parameters. This argument can be null if no context item is to be
     *                         supplied.
     * @throws IllegalStateException if the transformation has already been evaluated by calling one of the methods
     *                               <code>applyTemplates</code>, <code>callTemplate</code>, or <code>callFunction</code>
     * @throws SaxonApiException     if a required parameter is not present; if a parameter cannot be converted
     *                               to the required type; if the context item cannot be converted to the required type.
     */

    public void setGlobalContextItem(XdmItem globalContextItem) throws SaxonApiException {
        setGlobalContextItem(globalContextItem, false);
    }

    /**
     * Supply the context item to be used when evaluating global variables and parameters.
     *
     * @param globalContextItem the item to be used as the context item within the initializers
     *                          of global variables and parameters. This argument can be null if no context item is to be
     *                          supplied.
     * @param alreadyStripped   true if any stripping of type annotations or whitespace text nodes specified
     *                          by the stylesheet has already taken place
     * @throws IllegalStateException if the transformation has already been evaluated by calling one of the methods
     *                               <code>applyTemplates</code>, <code>callTemplate</code>, or <code>callFunction</code>
     * @throws SaxonApiException     if a required parameter is not present; if a parameter cannot be converted
     *                               to the required type; if the context item cannot be converted to the required type.
     */

    public void setGlobalContextItem(XdmItem globalContextItem, boolean alreadyStripped) throws SaxonApiException {
        if (primed) {
            throw new IllegalStateException("Stylesheet has already been evaluated");
        }
        this.globalContextItem = globalContextItem == null ? null : globalContextItem.getUnderlyingValue();
        this.alreadyStripped = alreadyStripped;
    }

    /**
     * Set the initial context node for the transformation.
     *
     * @param node the initial context node, or null if there is to be no initial context node
     * @deprecated since 9.7.0.1 - use {@link #setGlobalContextItem}
     */

    public void setInitialContextNode(XdmNode node) {
        if (primed) {
            throw new IllegalStateException("Stylesheet has already been evaluated");
        }
        this.globalContextItem = node.getUnderlyingNode();
    }

    /**
     * Set the initial context item for the transformation.
     *
     * @param item the initial context item, or null if there is to be no initial context item
     * @deprecated since 9.8 - use {@link #setGlobalContextItem}
     */

    public void setInitialContextItem(XdmItem item) {
        if (primed) {
            throw new IllegalStateException("Stylesheet has already been evaluated");
        }
        this.globalContextItem = (Item)item.getUnderlyingValue();
    }

    /**
     * Supply the values of global stylesheet variables and parameters.
     *
     * @param parameters a map whose keys are QNames identifying global stylesheet parameters,
     *                   and whose corresponding values are the values to be assigned to those parameters. If necessary
     *                   the supplied values are converted to the declared type of the parameter.
     *                   The contents of the supplied map are copied by this method,
     *                   so subsequent changes to the map have no effect.
     * @throws IllegalStateException if the transformation has already been evaluated by calling one of the methods
     *                               <code>applyTemplates</code>, <code>callTemplate</code>, or <code>callFunction</code>
     * @throws SaxonApiException     if a required parameter is not present; if a parameter cannot be converted
     *                               to the required type; if the context item cannot be converted to the required type.
     *                               Note that this method may detect any errors immediately, or may cause the
     *                               errors to be reported later, when the value of the parameter is actually used.
     */

    public void setStylesheetParameters(Map<QName, XdmValue> parameters) throws SaxonApiException {
        if (primed) {
            throw new IllegalStateException("Stylesheet has already been evaluated");
        }
        if (globalParameterSet == null) {
            globalParameterSet = new GlobalParameterSet();
        }
        for (Map.Entry<QName, XdmValue> param : parameters.entrySet()) {
            globalParameterSet.put(param.getKey().getStructuredQName(), param.getValue().getUnderlyingValue());
        }
    }


    private void prime() throws SaxonApiException {
        if (!primed) {
            if (globalParameterSet == null) {
                globalParameterSet = new GlobalParameterSet();
            }
            controller.setGlobalContextItem(globalContextItem, alreadyStripped);
            try {
                controller.initializeController(globalParameterSet);
            } catch (XPathException e) {
                throw new SaxonApiException(e);
            }
        }
        primed = true;
    }

    /**
     * Set the base output URI.
     * <p/>
     * <p>This defaults to the system ID of the Destination for the principal output
     * of the transformation if a destination is supplied and its System ID is known; in other cases, it defaults
     * to the current directory.</p>
     * <p/>
     * <p>If no base output URI is supplied, but the <code>Destination</code> of the transformation
     * is a <code>Serializer</code> that writes to a file, then the URI of this file is used as
     * the base output URI.</p>
     * <p/>
     * <p> The base output URI is used for resolving relative URIs in the <code>href</code> attribute
     * of the <code>xsl:result-document</code> instruction.</p>
     *
     * @param uri the base output URI
     */

    public void setBaseOutputURI(String uri) {
        controller.setBaseOutputURI(uri);
        baseOutputUriWasSet = uri != null;
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
     */

    public String getBaseOutputURI() {
        return controller.getBaseOutputURI();
    }

    /**
     * Set an object that will be used to resolve URIs used in
     * fn:doc() and related functions.
     *
     * @param resolver An object that implements the URIResolver interface, or
     *                 null.
     */

    public void setURIResolver(URIResolver resolver) {
        controller.setURIResolver(resolver);
    }

    /**
     * Get the URI resolver.
     *
     * @return the user-supplied URI resolver if there is one, or null otherwise
     */

    public URIResolver getURIResolver() {
        return controller.getURIResolver();
    }


    /**
     * Set the ErrorListener to be used during this transformation
     *
     * @param listener The error listener to be used. This is notified of all dynamic errors detected during the
     *                 transformation.
     */

    public void setErrorListener(ErrorListener listener) {
        controller.setErrorListener(listener);
    }

    /**
     * Get the ErrorListener being used during this compilation episode
     *
     * @return listener The error listener in use. This is notified of all dynamic errors detected during the
     * transformation. If no user-supplied ErrorListener has been set the method will return a system-supplied
     * ErrorListener. If an explicit ErrorListener has been set using {@link #setErrorListener(ErrorListener)},
     * then that ErrorListener will generally be returned, unless the internal ErrorListener has been changed
     * by some other mechanism.
     */

    public ErrorListener getErrorListener() {
        UnfailingErrorListener uel = controller.getErrorListener();
        if (uel instanceof DelegatingErrorListener) {
            return ((DelegatingErrorListener)uel).getBaseErrorListener();
        } else {
            return uel;
        }
    }

    /**
     * Set the MessageListener to be notified whenever the stylesheet evaluates an
     * <code>xsl:message</code> instruction.  If no MessageListener is nominated,
     * the output of <code>xsl:message</code> instructions will be serialized and sent
     * to the standard error stream.
     *
     * @param listener the MessageListener to be used
     */

    public void setMessageListener(MessageListener listener) {
        controller.setMessageEmitter(new MessageListenerProxy(listener, controller.makePipelineConfiguration()));
    }

    /**
     * Set the MessageListener to be notified whenever the stylesheet evaluates an
     * <code>xsl:message</code> instruction.  If no MessageListener is nominated,
     * the output of <code>xsl:message</code> instructions will be serialized and sent
     * to the standard error stream.
     *
     * <p>The <code>MessageListener2</code> interface differs from <code>MessageListener</code>
     * in allowing the error code supplied to xsl:message to be notified.</p>
     *
     * @param listener the MessageListener to be used
     */

    public void setMessageListener(MessageListener2 listener) {
        controller.setMessageEmitter(new MessageListener2Proxy(listener, controller.makePipelineConfiguration()));
    }

    /**
     * Get the MessageListener to be notified whenever the stylesheet evaluates an
     * <code>xsl:message</code> instruction. If no MessageListener has been nominated,
     * return null
     *
     * @return the user-supplied MessageListener, or null if none has been supplied
     */

    public MessageListener getMessageListener() {
        Receiver r = controller.getMessageEmitter();
        if (r instanceof MessageListenerProxy) {
            return ((MessageListenerProxy) r).getMessageListener();
        } else {
            return null;
        }
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
        return controller.isAssertionsEnabled();
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
        controller.setAssertionsEnabled(enabled);
    }


    /**
     * Set a TraceListener to be notified of all events occurring during the transformation.
     * This will only be effective if the stylesheet was compiled with trace code enabled
     * (see {@link XsltCompiler#setCompileWithTracing(boolean)})
     *
     * @param listener the TraceListener to be used. Note that the TraceListener has access to
     *                 interal Saxon interfaces which may vary from one release to the next. It is also possible that
     *                 the TraceListener interface itself may be changed in future releases.
     */

    public void setTraceListener(TraceListener listener) {
        controller.setTraceListener(listener);
    }

    /**
     * Get the TraceListener to be notified of all events occurring during the transformation.
     * If no TraceListener has been nominated, return null
     *
     * @return the user-supplied TraceListener, or null if none has been supplied
     */

    public TraceListener getTraceListener() {
        return controller.getTraceListener();
    }

    /**
     * Set the destination for output from the fn:trace() function.
     * By default, the destination is System.err. If a TraceListener is in use,
     * this is ignored, and the trace() output is sent to the TraceListener.
     *
     * @param stream the PrintStream to which trace output will be sent. If set to
     *               null, trace output is suppressed entirely. It is the caller's responsibility
     *               to close the stream after use.
     */

    public void setTraceFunctionDestination(Logger stream) {
        controller.setTraceFunctionDestination(stream);
    }

    /**
     * Get the destination for output from the fn:trace() function.
     *
     * @return the Logger to which trace output will be sent. If no explicitly
     * destination has been set, returns System.err. If the destination has been set
     * to null to suppress trace output, returns null.
     */

    public Logger getTraceFunctionDestination() {
        return controller.getTraceFunctionDestination();
    }


    /**
     * Set parameters to be passed to the initial template. These are used
     * whether the transformation is invoked by applying templates to an initial source item,
     * or by invoking a named template. The parameters in question are the xsl:param elements
     * appearing as children of the xsl:template element.
     * <p/>
     * <p>The parameters are supplied in the form of a map; the key is a QName which must
     * match the name of the parameter; the associated value is an XdmValue containing the
     * value to be used for the parameter. If the initial template defines any required
     * parameters, the map must include a corresponding value. If the initial template defines
     * any parameters that are not present in the map, the default value is used. If the map
     * contains any parameters that are not defined in the initial template, these values
     * are silently ignored.</p>
     * <p/>
     * <p>The supplied values are converted to the required type using the function conversion
     * rules. If conversion is not possible, a run-time error occurs (not now, but later, when
     * the transformation is actually run).</p>
     * <p/>
     * <p>The <code>XsltTransformer</code> retains a reference to the supplied map, so parameters can be added or
     * changed until the point where the transformation is run.</p>
     * <p/>
     * <p>The XSLT 3.0 specification makes provision for supplying parameters to the initial
     * template, as well as global stylesheet parameters. Although there is no similar provision
     * in the XSLT 1.0 or 2.0 specifications, this method works for all stylesheets, regardless whether
     * XSLT 3.0 is enabled or not.</p>
     *
     * @param parameters the parameters to be used for the initial template
     * @param tunnel     true if these values are to be used for setting tunnel parameters;
     *                   false if they are to be used for non-tunnel parameters
     * @throws SaxonApiException if running as an XSLT 2.0 processor, in which case initial template
     * parameters are not allowed
     */

    public void setInitialTemplateParameters(Map<QName, XdmValue> parameters, boolean tunnel) throws SaxonApiException {
        Map<StructuredQName, Sequence> templateParams = new HashMap<StructuredQName, Sequence>();
        for (Map.Entry<QName, XdmValue> entry : parameters.entrySet()) {
            templateParams.put(entry.getKey().getStructuredQName(), entry.getValue().getUnderlyingValue());
        }
        controller.setInitialTemplateParameters(templateParams, tunnel);
    }

    /**
     * Set the initial mode for the transformation
     *
     * @param modeName the name of the initial mode. Two special values are recognized, in the
     *                 reserved XSLT namespace:
     *                 xsl:unnamed to indicate the mode with no name, and xsl:default to indicate the
     *                 mode defined in the stylesheet header as the default mode.
     *                 The value null also indicates the default mode (which defaults to the unnamed
     *                 mode, but can be set differently in an XSLT 3.0 stylesheet).
     * @throws IllegalArgumentException if the requested mode is not defined in the stylesheet
     * @since changed in 9.6 to throw an exception if the mode is not defined in the stylesheet.
     * Chaned in 9.7 so that null means the default mode, not necessarily the unnamed mode.
     */

    public void setInitialMode(QName modeName) throws IllegalArgumentException {
        try {
            controller.setInitialMode(modeName == null ? null : modeName.getStructuredQName());
        } catch (XPathException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Get the name of the initial mode for the transformation, if one has been set.
     *
     * @return the initial mode for the transformation. Returns null if no mode has been set,
     * or if the mode was set to null to represent the default (unnamed) mode
     */

    public QName getInitialMode() {
        Mode mode = controller.getInitialMode();
        if (mode == null) {
            return null;
        } else {
            return new QName(mode.getModeName());
        }
    }


    /**
     * Invoke the stylesheet by applying templates to a supplied Source document, sending the results (wrapped
     * in a document node) to a given Destination. The invocation uses any initial mode set using {@link #setInitialMode(QName)},
     * and any template parameters set using {@link #setInitialTemplateParameters(java.util.Map, boolean)}.
     *
     * @param source      the source document. For streamed processing, this must be a SAXSource or StreamSource.
     *                    <p/>
     *                    <p>Note: supplying a <code>DOMSource</code> is allowed, but is much less efficient than using a
     *                    <code>StreamSource</code> or <code>SAXSource</code> and leaving Saxon to build the tree in its own
     *                    internal format. To apply more than one transformation to the same source document, the source document
     *                    tree can be pre-built using a {@link DocumentBuilder}.</p>
     * @param destination the destination of the result document produced by wrapping the result of the apply-templates
     *                    call in a document node.  If the destination is a {@link Serializer}, then the serialization
     *                    parameters set in the serializer are combined with those defined in the stylesheet
     *                    (the parameters set in the serializer take precedence).
     * @throws SaxonApiException if the transformation fails
     */

    public void applyTemplates(Source source, Destination destination) throws SaxonApiException {
        prime();
        try {
            Receiver out = getDestinationReceiver(destination);
            setBuildTreeConditionally(destination);
            controller.transform(source, out);
        } catch (XPathException e) {
            if (!e.hasBeenReported()) {
                try {
                    getErrorListener().fatalError(e);
                } catch (TransformerException e1) {
                    // ignore secondary error
                }
            }
            throw new SaxonApiException(e);
        }
    }



    /**
     * Invoke the stylesheet by applying templates to a supplied Source document, returning the raw results
     * as an {@link XdmValue}. The invocation uses any initial mode set using {@link #setInitialMode(QName)},
     * and any template parameters set using {@link #setInitialTemplateParameters(java.util.Map, boolean)}.
     *
     * @param source the source document. For streamed processing, this must be a SAXSource or StreamSource.
     *               <p/>
     *               <p>Note: supplying a <code>DOMSource</code> is allowed, but is much less efficient than using a
     *               <code>StreamSource</code> or <code>SAXSource</code> and leaving Saxon to build the tree in its own
     *               internal format. To apply more than one transformation to the same source document, the source document
     *               tree can be pre-built using a {@link DocumentBuilder}.</p>
     * @return the raw result of processing the supplied Source using the selected template rule, without
     * wrapping the returned sequence in a document node
     * @throws SaxonApiException if the transformation fails
     */

    public XdmValue applyTemplates(Source source) throws SaxonApiException {
        prime();
        try {
            PipelineConfiguration pipe = controller.makePipelineConfiguration();
            SequenceOutputter out = new SequenceOutputter(pipe, 1);
            controller.setBuildTree(false);
            controller.transform(source, out);
            Sequence result = out.getSequence();
            return XdmValue.wrap(result);
        } catch (XPathException e) {
            if (!e.hasBeenReported()) {
                try {
                    getErrorListener().fatalError(e);
                } catch (TransformerException e1) {
                    // ignore secondary error
                }
            }
            throw new SaxonApiException(e);
        }
    }

    /**
     * Invoke the stylesheet by applying templates to a supplied input sequence, sending the results (wrapped
     * in a document node) to a given Destination. The invocation uses any initial mode set using {@link #setInitialMode(QName)},
     * and any template parameters set using {@link #setInitialTemplateParameters(java.util.Map, boolean)}.
     *
     * @param selection   the initial value to which templates are to be applied (equivalent to the <code>select</code>
     *                    attribute of <code>xsl:apply-templates</code>)
     * @param destination the destination for the result document. In most cases this causes the raw result of
     *                     the transformation to be wrapped in a document node. However, if the destination
     *                     is a Serializer and the output method is "json" or "adaptive", then
     *                     no wrapping takes place.
     * @throws SaxonApiException if the transformation fails
     * @since 9.6. Changed in 9.7.0.1 so that if a Serializer is supplied as the Destination, it will not
     * be modified by this method to set output properties from the stylesheet; instead, the Serializer
     * should be initialized by calling the <code>newSerializer</code> method on this <code>Xslt30Transformer</code>
     */

    public void applyTemplates(XdmValue selection, Destination destination) throws SaxonApiException {
        prime();
        try {
            Receiver out = getDestinationReceiver(destination);
            if (baseOutputUriWasSet) {
                out.setSystemId(controller.getBaseOutputURI());
            }
            setBuildTreeConditionally(destination);
            controller.applyTemplates(selection.getUnderlyingValue(), out);
            destination.close();
        } catch (XPathException e) {
            if (!e.hasBeenReported()) {
                try {
                    getErrorListener().fatalError(e);
                } catch (TransformerException e1) {
                    // ignore secondary error
                }
            }
            throw new SaxonApiException(e);
        }
    }

    /**
     * Invoke the stylesheet by applying templates to a supplied input sequence, returning the raw results.
     * as an {@link XdmValue}. The invocation uses any initial mode set using {@link #setInitialMode(QName)},
     * and any template parameters set using {@link #setInitialTemplateParameters(java.util.Map, boolean)}.
     *
     * @param selection the initial value to which templates are to be applied (equivalent to the <code>select</code>
     *                  attribute of <code>xsl:apply-templates</code>)
     * @return the raw result of applying templates to the supplied selection value, without wrapping in
     * a document node or serializing the result. If there is more that one item in the selection, the result
     * is the concatenation of the results of applying templates to each item in turn.
     * @throws SaxonApiException if the transformation fails
     */

    public XdmValue applyTemplates(XdmValue selection) throws SaxonApiException {
        prime();
        try {
            controller.setBuildTree(false);
            PipelineConfiguration pipe = controller.makePipelineConfiguration();
            SequenceOutputter out = new SequenceOutputter(pipe, 1);
            controller.applyTemplates(selection.getUnderlyingValue(), out);
            Sequence result = out.getSequence();
            return XdmValue.wrap(result);
        } catch (XPathException e) {
            if (!e.hasBeenReported()) {
                try {
                    getErrorListener().fatalError(e);
                } catch (TransformerException e1) {
                    // ignore secondary error
                }
            }
            throw new SaxonApiException(e);
        }
    }

    /**
     * Invoke a transformation by calling a named template. The results of calling
     * the template are wrapped in a document node, which is then sent to the specified
     * destination. If {@link #setInitialTemplateParameters(java.util.Map, boolean)} has been
     * called, then the parameters supplied are made available to the called template (no error
     * occurs if parameters are supplied that are not used).
     *
     * @param templateName the name of the initial template. This must match the name of a
     *                     public named template in the stylesheet. If the value is null,
     *                     the QName <code>xsl:initial-template</code> is used.
     * @param destination  the destination for the result document. In most cases this causes the raw result of
     *                     the transformation to be wrapped in a document node. However, if the destination
     *                     is a Serializer and the output method is "json" or "adaptive", then
     *                     no wrapping takes place.
     * @throws SaxonApiException if there is no named template with this name, or if any dynamic
     *                           error occurs during the transformation
     * @since 9.6. Changed in 9.7.0.1 so that if a Serializer is supplied as the Destination, it will not
     * be modified by this method to set output properties from the stylesheet; instead, the Serializer
     * should be initialized by calling the <code>newSerializer</code> method on this <code>Xslt30Transformer</code>
     */

    public void callTemplate(QName templateName, Destination destination) throws SaxonApiException {
        prime();
        if (templateName == null) {
            templateName = new QName("xsl", NamespaceConstant.XSLT, "initial-template");
        }
        try {
            Receiver out = getDestinationReceiver(destination);
            if (baseOutputUriWasSet) {
                out.setSystemId(controller.getBaseOutputURI());
            }
            setBuildTreeConditionally(destination);
            controller.callTemplate(templateName.getStructuredQName(), out);
        } catch (XPathException e) {
            if (!e.hasBeenReported()) {
                try {
                    getErrorListener().fatalError(e);
                } catch (TransformerException e1) {
                    // ignore secondary error
                }
            }
            throw new SaxonApiException(e);
        }
    }

    private void setBuildTreeConditionally(Destination destination) {
        if (destination instanceof Serializer) {
            String method = ((Serializer) destination).getOutputProperty(Serializer.Property.METHOD);
            if (method == null) {
                method = controller.getExecutable().getDefaultOutputProperties().getProperty("method");
            }
            if ("json".equals(method) || "adaptive".equals(method)) {
                controller.setBuildTree(false);
            }
        } else {
            controller.setBuildTree(true);
        }
    }

    /**
     * Invoke a transformation by calling a named template. The results of calling
     * the template are returned as a raw value, without wrapping in a document node
     * or serializing.
     *
     * @param templateName the name of the initial template. This must match the name of a
     *                     public named template in the stylesheet. If the value is null,
     *                     the QName <code>xsl:initial-template</code> is used.
     * @return the raw results of the called template, without wrapping in a document node
     * or serialization.
     * @throws SaxonApiException if there is no named template with this name, or if any dynamic
     *                           error occurs during the transformation
     */


    public XdmValue callTemplate(QName templateName) throws SaxonApiException {
        prime();
        if (templateName == null) {
            templateName = new QName("xsl", NamespaceConstant.XSLT, "initial-template");
        }
        try {
            PipelineConfiguration pipe = controller.makePipelineConfiguration();
            SequenceOutputter out = new SequenceOutputter(pipe, 1);
            controller.setBuildTree(false);
            controller.callTemplate(templateName.getStructuredQName(), out);
            Sequence result = out.getSequence();
            return XdmValue.wrap(result);
        } catch (XPathException e) {
            if (!e.hasBeenReported()) {
                try {
                    getErrorListener().fatalError(e);
                } catch (TransformerException e1) {
                    // ignore secondary error
                }
            }
            throw new SaxonApiException(e);
        }

    }

    /**
     * Call a public user-defined function in the stylesheet.
     *
     * @param function  The name of the function to be called
     * @param arguments The values of the arguments to be supplied to the function. These
     *                  will be converted if necessary to the type as defined in the function signature, using
     *                  the function conversion rules.
     * @return the result of calling the function. This is the raw result, without wrapping in a document
     * node and without serialization.
     * @throws SaxonApiException if no function has been defined with the given name and arity;
     *                           or if any of the arguments does not match its required type according to the function
     *                           signature; or if a dynamic error occurs in evaluating the function.
     */

    public XdmValue callFunction(QName function, XdmValue[] arguments) throws SaxonApiException {
        prime();
        try {
            Component f = getFunctionComponent(function, arguments);
            UserFunction uf = (UserFunction) f.getActor();
            Sequence[] vr = typeCheckFunctionArguments(uf, arguments);

            XPathContextMajor context = controller.newXPathContext();
            context.setCurrentComponent(f);
            context.setTemporaryOutputState(StandardNames.XSL_FUNCTION);
            Sequence result = uf.call(context, vr);
            result = SequenceTool.toGroundedValue(result);
            return XdmValue.wrap(result);
        } catch (XPathException e) {
            if (!e.hasBeenReported()) {
                try {
                    getErrorListener().fatalError(e);
                } catch (TransformerException e1) {
                    // ignore secondary error
                }
            }
            throw new SaxonApiException(e);
        }
    }

    @NotNull
    private Component getFunctionComponent(QName function, XdmValue[] arguments) throws XPathException {
        SymbolicName fName = new SymbolicName.F(function.getStructuredQName(), arguments.length);
        PreparedStylesheet pss = (PreparedStylesheet) controller.getExecutable();
        Component f = pss.getComponent(fName);
        if (f == null) {
            throw new XPathException("No public function with name " + function.getClarkName() +
                    " and arity " + arguments.length + " has been declared in the stylesheet", "XTDE0041");
        } else if (f.getVisibility() != Visibility.FINAL && f.getVisibility() != Visibility.PUBLIC) {
            throw new XPathException("Cannot invoke " + fName + " externally, because it is not public", "XTDE0041");
        }
        return f;
    }

    @NotNull
    private Sequence[] typeCheckFunctionArguments(UserFunction uf, XdmValue[] arguments) throws XPathException {
        Configuration config = processor.getUnderlyingConfiguration();
        UserFunctionParameter[] params = uf.getParameterDefinitions();
        Sequence[] vr = new Sequence[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            net.sf.saxon.value.SequenceType type = params[i].getRequiredType();
            vr[i] = arguments[i].getUnderlyingValue();
            if (!type.matches(vr[i], config.getTypeHierarchy())) {
                RoleDiagnostic role = new RoleDiagnostic(RoleDiagnostic.FUNCTION, uf.getFunctionName().getDisplayName(), i);
                vr[i] = config.getTypeHierarchy().applyFunctionConversionRules(vr[i], type, role, ExplicitLocation.UNKNOWN_LOCATION);
            }
        }
        return vr;
    }

    /**
     * Call a public user-defined function in the stylesheet, wrapping the result in an XML document, and sending
     * this document to a specified destination
     *
     * @param function    The name of the function to be called
     * @param arguments   The values of the arguments to be supplied to the function. These
     *                    will be converted if necessary to the type as defined in the function signature, using
     *                    the function conversion rules.
     * @param destination the destination of the result document produced by wrapping the result of the apply-templates
     *                    call in a document node.
     * @throws SaxonApiException in the event of a dynamic error
     * @since 9.6. Changed in 9.7.0.1 so that if a Serializer is supplied as the Destination, it will not
     * be modified by this method to set output properties from the stylesheet; instead, the Serializer
     * should be initialized by calling the <code>newSerializer</code> method on this <code>Xslt30Transformer</code>
     */

    public void callFunction(QName function, XdmValue[] arguments, Destination destination) throws SaxonApiException {
        prime();
        try {
            Component f = getFunctionComponent(function, arguments);
            UserFunction uf = (UserFunction) f.getActor();
            Sequence[] vr = typeCheckFunctionArguments(uf, arguments);

            XPathContextMajor context = controller.newXPathContext();
            context.setCurrentComponent(f);
            context.setTemporaryOutputState(StandardNames.XSL_FUNCTION);

            if (destination instanceof Serializer) {
                Serializer serializer = (Serializer) destination;
                serializer.setDefaultOutputProperties(controller.getExecutable().getDefaultOutputProperties());
                serializer.setCharacterMap(controller.getExecutable().getCharacterMapIndex());
            }

            Receiver receiver = destination.getReceiver(controller.getConfiguration());
            receiver.open();
            if (!(receiver instanceof SequenceReceiver)) {
                receiver = new TreeReceiver(receiver);
            }
            context.setReceiver((SequenceReceiver)receiver);
            uf.process(vr, context);
            receiver.close();

        } catch (XPathException e) {
            if (!e.hasBeenReported()) {
                try {
                    getErrorListener().fatalError(e);
                } catch (TransformerException e1) {
                    // ignore secondary error
                }
            }
            throw new SaxonApiException(e);
        }

        destination.close();
    }

    /**
     * Create a serializer initialised to use the default output parameters defined in the stylesheet.
     * These serialization parameters can be overridden by use of
     * {@link Serializer#setOutputProperty(Serializer.Property, String)}.
     * @since 9.7.0.1
     */

    public Serializer newSerializer() {
        Serializer serializer = processor.newSerializer();
        serializer.setDefaultOutputProperties(controller.getExecutable().getDefaultOutputProperties());
        serializer.setCharacterMap(controller.getExecutable().getCharacterMapIndex());
        return serializer;
    }

    /**
     * Create a serializer initialised to use the default output parameters defined in the stylesheet.
     * These serialization parameters can be overridden by use of
     * {@link Serializer#setOutputProperty(Serializer.Property, String)}.
     *
     * @param file the output file to which the serializer will write its output. As well as initializing
     *             the serializer to write to this output file, this method sets the base output URI of this
     *             Xslt30Transformer to be the URI of this file.
     * @since 9.7.0.1
     */

    public Serializer newSerializer(File file) {
        Serializer serializer = processor.newSerializer(file);
        serializer.setDefaultOutputProperties(controller.getExecutable().getDefaultOutputProperties());
        serializer.setCharacterMap(controller.getExecutable().getCharacterMapIndex());
        setBaseOutputURI(file.toURI().toString());
        return serializer;
    }

    /**
     * Create a serializer initialised to use the default output parameters defined in the stylesheet.
     * These serialization parameters can be overridden by use of
     * {@link Serializer#setOutputProperty(Serializer.Property, String)}.
     * @param writer the Writer to which the serializer will write
     * @since 9.7.0.1
     */

    public Serializer newSerializer(Writer writer) {
        Serializer serializer = newSerializer();
        serializer.setOutputWriter(writer);
        return serializer;
    }

    /**
     * Create a serializer initialised to use the default output parameters defined in the stylesheet.
     * These serialization parameters can be overridden by use of
     * {@link Serializer#setOutputProperty(Serializer.Property, String)}.
     *
     * @param stream the output stream to which the serializer will write
     * @since 9.7.0.1
     */

    public Serializer newSerializer(OutputStream stream) {
        Serializer serializer = newSerializer();
        serializer.setOutputStream(stream);
        return serializer;
    }

    private Receiver getDestinationReceiver(Destination destination) throws SaxonApiException {
        if (destination instanceof Serializer) {
            Serializer serializer = (Serializer) destination;
            serializer.setDefaultOutputProperties(controller.getExecutable().getDefaultOutputProperties());
            serializer.setCharacterMap(controller.getExecutable().getCharacterMapIndex());
            PipelineConfiguration pipe = controller.makePipelineConfiguration();
            Receiver r = serializer.getReceiver(pipe);
            return new ReconfigurableSerializer(r, serializer.getLocalOutputProperties(), serializer.getResult());
        } else {
            Receiver r = destination.getReceiver(controller.getConfiguration());
            PipelineConfiguration pipe = r.getPipelineConfiguration();
            pipe.setController(controller);
            return r;
        }
    }


    /**
     * Get the underlying Controller used to implement this XsltTransformer. This provides access
     * to lower-level methods not otherwise available in the s9api interface. Note that classes
     * and methods obtained by this route cannot be guaranteed stable from release to release.
     *
     * @return the underlying {@link Controller}
     */

    public Controller getUnderlyingController() {
        return controller;
    }
}

