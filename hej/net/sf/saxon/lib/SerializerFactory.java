////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.serialize.XHTMLPrefixRemover;
import net.sf.saxon.Configuration;
import net.sf.saxon.event.*;
import net.sf.saxon.om.NameChecker;
import net.sf.saxon.om.NamespaceResolver;
import net.sf.saxon.om.QNameException;
import net.sf.saxon.om.TreeInfo;
import net.sf.saxon.query.SequenceWrapper;
import net.sf.saxon.serialize.*;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.BigDecimalValue;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamResult;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

/**
 * Helper class to construct a serialization pipeline for a given result destination
 * and a given set of output properties. The pipeline is represented by a Receiver object
 * to which result tree events are sent.
 * <p/>
 * Since Saxon 8.8 is is possible to write a subclass of SerializerFactory and register it
 * with the Configuration, allowing customisation of the Serializer pipeline.
 * <p/>
 * The class includes methods for instantiating each of the components used on the Serialization
 * pipeline. This allows a customized SerializerFactory to replace any or all of these components
 * by subclasses that refine the behaviour.
 */

public class SerializerFactory {

    Configuration config;

    private static Class staxResultClass;

    static {
        try {
            staxResultClass = Class.forName("javax.xml.transform.stax.StAXResult");
        } catch (Exception err) {
            // no action; if StAXSource isn't available then we don't use it.
        }
    }

    /**
     * Create a SerializerFactory
     *
     * @param config the Saxon Configuration
     */

    public SerializerFactory(Configuration config) {
        this.config = config;
    }

    public Configuration getConfiguration() {
        return config;
    }
    /**
     * Create a serializer with given output properties, and return
     * an XMLStreamWriter that can be used to feed events to the serializer.
     *
     * @param result     the destination of the serialized output (wraps a Writer, an OutputStream, or a File)
     * @param properties the serialization properties to be used
     * @return a serializer in the form of an XMLStreamWriter
     * @throws net.sf.saxon.trans.XPathException
     *          if any error occurs
     */

    public StreamWriterToReceiver getXMLStreamWriter(
            StreamResult result,
            Properties properties) throws XPathException {
        Receiver r = getReceiver(result, config.makePipelineConfiguration(), properties);
        r = new NamespaceReducer(r);
        return new StreamWriterToReceiver(r);
    }

    /**
     * Get a Receiver that wraps a given Result object. Saxon calls this method to construct
     * a serialization pipeline. The method can be overridden in a subclass; alternatively, the
     * subclass can override the various methods used to instantiate components of the serialization
     * pipeline.
     * <p/>
     * <p>Note that this method ignores the {@link SaxonOutputKeys#WRAP} output property. If
     * wrapped output is required, the user must create a {@link net.sf.saxon.query.SequenceWrapper} directly.</p>
     * <p>The effect of the method changes in Saxon 9.7 so that for serialization methods other than
     * "json" and "adaptive", the returned Receiver performs the function of "sequence normalization" as
     * defined in the Serialization specification. Previously the client code handled this by wrapping the
     * result in a ComplexContentOutputter (usually as a side-effect of called XPathContext.changeOutputDestination()).
     * Wrapping in a ComplexContentOutputter is no longer necessary, though it does no harm because the ComplexContentOutputter
     * is idempotent.</p>
     *
     * @param result The final destination of the serialized output. Usually a StreamResult,
     *               but other kinds of Result are possible.
     * @param pipe   The PipelineConfiguration.
     * @param props  The serialization properties. If this includes the property {@link SaxonOutputKeys#USE_CHARACTER_MAPS}
     *               then the PipelineConfiguration must contain a non-null Controller, and the Executable associated with this Controller
     *               must have a CharacterMapIndex which is used to resolve the names of the character maps appearing in this property.
     * @return the newly constructed Receiver that performs the required serialization
     * @throws net.sf.saxon.trans.XPathException
     *          if any failure occurs
     */

    public SequenceReceiver getReceiver(Result result,
                                PipelineConfiguration pipe,
                                Properties props)
            throws XPathException {
        if (pipe.getController() != null) {
            return getReceiver(result, pipe, props, pipe.getController().getExecutable().getCharacterMapIndex());
        } else {
            return getReceiver(result, pipe, props, null);
        }
    }

    /**
     * Get a Receiver that wraps a given Result object. Saxon calls this method to construct
     * a serialization pipeline. The method can be overridden in a subclass; alternatively, the
     * subclass can override the various methods used to instantiate components of the serialization
     * pipeline.
     * <p/>
     * <p>Note that this method ignores the {@link SaxonOutputKeys#WRAP} output property. If
     * wrapped output is required, the user must create a {@link net.sf.saxon.query.SequenceWrapper} directly.</p>
     * <p>The effect of the method changes in Saxon 9.7 so that for serialization methods other than
     * "json" and "adaptive", the returned Receiver performs the function of "sequence normalization" as
     * defined in the Serialization specification. Previously the client code handled this by wrapping the
     * result in a ComplexContentOutputter (usually as a side-effect of called XPathContext.changeOutputDestination()).
     * Wrapping in a ComplexContentOutputter is no longer necessary, though it does no harm because the ComplexContentOutputter
     * is idempotent.</p>
     *
     * @param result       The final destination of the serialized output. Usually a StreamResult,
     *                     but other kinds of Result are possible.
     * @param pipe         The PipelineConfiguration.
     * @param props        The serialization properties
     * @param charMapIndex The index of character maps. Required if any of the serialization properties
     *                     is {@link SaxonOutputKeys#USE_CHARACTER_MAPS}, in which case the named character maps listed in that
     *                     property must be present in the index of character maps.
     * @return the newly constructed Receiver that performs the required serialization
     * @throws XPathException if a serializer cannot be created
     */

    public SequenceReceiver getReceiver(Result result,
                                PipelineConfiguration pipe,
                                Properties props,
                                /*@Nullable*/ CharacterMapIndex charMapIndex)
            throws XPathException {
        String nextInChain = props.getProperty(SaxonOutputKeys.NEXT_IN_CHAIN);
        if (nextInChain != null && !nextInChain.isEmpty()) {
            String href = props.getProperty(SaxonOutputKeys.NEXT_IN_CHAIN);
            String base = props.getProperty(SaxonOutputKeys.NEXT_IN_CHAIN_BASE_URI);
            if (base == null) {
                base = "";
            }
            Properties sansNext = new Properties(props);
            sansNext.setProperty(SaxonOutputKeys.NEXT_IN_CHAIN, "");
            return prepareNextStylesheet(pipe, href, base, result);
        }
        String paramDoc = props.getProperty(SaxonOutputKeys.PARAMETER_DOCUMENT);
        if (paramDoc != null) {
            String base = props.getProperty(SaxonOutputKeys.PARAMETER_DOCUMENT_BASE_URI);
            if (base == null) {
                base = result.getSystemId();
            }
            Properties props2 = new Properties(props);
            Source source;
            try {
                source = pipe.getConfiguration().getURIResolver().resolve(paramDoc, base);
            } catch (TransformerException e) {
                throw XPathException.makeXPathException(e);
            }
            ParseOptions options = new ParseOptions();
            options.setSchemaValidationMode(Validation.LAX);
            TreeInfo doc = pipe.getConfiguration().buildDocumentTree(source);
            SerializationParamsHandler ph = new SerializationParamsHandler();
            ph.setSerializationParams(doc.getRootNode());
            Properties paramDocProps = ph.getSerializationProperties();
            Enumeration<?> names = paramDocProps.propertyNames();
            while (names.hasMoreElements()) {
                String name = (String)names.nextElement();
                String value = paramDocProps.getProperty(name);
                props2.setProperty(name, value);
            }
            props = props2;
            CharacterMap characterMap = ph.getCharacterMap();
            if (characterMap != null) {
                CharacterMapIndex index = new CharacterMapIndex();
                charMapIndex.putCharacterMap(characterMap.getName(), characterMap);
                props.setProperty(SaxonOutputKeys.USE_CHARACTER_MAPS, characterMap.getName().getClarkName());
            }
        }
        if (result instanceof Emitter) {
            if (((Emitter) result).getOutputProperties() == null) {
                ((Emitter) result).setOutputProperties(props);
            }
            return (Emitter) result;
        } else if (result instanceof JSONEmitter) {
            if (((JSONEmitter) result).getOutputProperties() == null) {
                ((JSONEmitter) result).setOutputProperties(props);
            }
            return (JSONEmitter) result;
        } else if (result instanceof AdaptiveEmitter) {
            if (((AdaptiveEmitter) result).getOutputProperties() == null) {
                ((AdaptiveEmitter) result).setOutputProperties(props);
            }
            return (AdaptiveEmitter) result;
        } else if (result instanceof ComplexContentOutputter) {
            return (ComplexContentOutputter)result;
        } else if (result instanceof Receiver) {
            Receiver receiver = (Receiver) result;
            receiver.setSystemId(result.getSystemId());
            receiver.setPipelineConfiguration(pipe);
            ComplexContentOutputter out = new ComplexContentOutputter(pipe);
            out.setHostLanguage(pipe.getHostLanguage());
            out.setReceiver(receiver);
            return out;
        } else if (result instanceof SAXResult) {
            ContentHandlerProxy proxy = newContentHandlerProxy();
            proxy.setUnderlyingContentHandler(((SAXResult) result).getHandler());
            proxy.setPipelineConfiguration(pipe);
            proxy.setOutputProperties(props);
            if ("yes".equals(props.getProperty(SaxonOutputKeys.SUPPLY_SOURCE_LOCATOR))) {
                if (pipe.getConfiguration().isCompileWithTracing()) {
                    pipe.getController().addTraceListener(proxy.getTraceListener());
                } else {
                    throw new XPathException(
                            "Cannot use saxon:supply-source-locator unless tracing was enabled at compile time", SaxonErrorCode.SXSE0002);
                }
            }
            //proxy.open();
            return makeSequenceNormalizer(proxy, props);
        } else if (result instanceof StreamResult) {

            // The "target" is the start of the output pipeline, the Receiver that
            // instructions will actually write to (except that other things like a
            // NamespaceReducer may get added in front of it). The "emitter" is the
            // last thing in the output pipeline, the Receiver that actually generates
            // characters or bytes that are written to the StreamResult.

            SequenceReceiver target;
            String method = props.getProperty(OutputKeys.METHOD);
            if (method == null) {
                return newUncommittedSerializer(result, new Sink(pipe), props, charMapIndex);
            }

            Emitter emitter = null;

            CharacterMapExpander characterMapExpander = null;
            String useMaps = props.getProperty(SaxonOutputKeys.USE_CHARACTER_MAPS);
            if (useMaps != null) {
                if (charMapIndex == null) {
                    XPathException de = new XPathException("Cannot use character maps in an environment with no Controller");
                    de.setErrorCode(SaxonErrorCode.SXSE0001);
                    throw de;
                }
                characterMapExpander = charMapIndex.makeCharacterMapExpander(useMaps, new Sink(pipe), this);
            }

            ProxyReceiver normalizer = null;
            String normForm = props.getProperty(SaxonOutputKeys.NORMALIZATION_FORM);
            if (normForm != null && !normForm.equals("none")) {
                normalizer = newUnicodeNormalizer(new Sink(pipe), props);
            }

            if ("html".equals(method)) {
                emitter = newHTMLEmitter(props);
                emitter.setPipelineConfiguration(pipe);
                target = createHTMLSerializer(emitter, props, pipe, characterMapExpander, normalizer);

            } else if ("xml".equals(method)) {
                emitter = newXMLEmitter(props);
                emitter.setPipelineConfiguration(pipe);
                target = createXMLSerializer((XMLEmitter) emitter, props, pipe, characterMapExpander, normalizer);

            } else if ("xhtml".equals(method)) {
                emitter = newXHTMLEmitter(props);
                emitter.setPipelineConfiguration(pipe);
                target = createXHTMLSerializer(emitter, props, pipe, characterMapExpander, normalizer);

            } else if ("text".equals(method)) {
                emitter = newTEXTEmitter();
                emitter.setPipelineConfiguration(pipe);
                target = createTextSerializer(emitter, props, characterMapExpander, normalizer);

            } else if ("json".equals(method)) {
                StreamResult sr = (StreamResult) result;
                props.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
                JSONEmitter je = new JSONEmitter(pipe, sr, props);
                return customizeJSONSerializer(je, props, characterMapExpander, normalizer);

            } else if ("adaptive".equals(method)) {
                TEXTEmitter te = new TEXTEmitter();
                te.setPipelineConfiguration(pipe);
                te.setOutputProperties(props);
                Writer writer = ((StreamResult)result).getWriter();
                if (writer == null) {
                    OutputStream os = ((StreamResult) result).getOutputStream();
                    String encoding = props.getProperty("encoding");
                    if (encoding == null) {
                        encoding = "UTF-8";
                    }
                    try {
                        writer = new OutputStreamWriter(os, encoding);
                    } catch (UnsupportedEncodingException e) {
                        throw new XPathException(e);
                    }
                }
                AdaptiveEmitter je = new AdaptiveEmitter(pipe, writer);
                je.setOutputProperties(props);
                StreamResult sr = (StreamResult) result;
                te.setStreamResult(sr);
                return customizeAdaptiveSerializer(je, props, characterMapExpander, normalizer);

            } else if (method.startsWith("{" + NamespaceConstant.SAXON + "}")) {
                target = createSaxonSerializationMethod(method, props, pipe, characterMapExpander, normalizer);
                if (target instanceof Emitter) {
                    emitter = (Emitter) target;
                }

            } else {
                SequenceReceiver userReceiver;
                if (pipe == null) {
                    throw new XPathException("Unsupported serialization method " + method);
                } else {
                    userReceiver = createUserDefinedOutputMethod(method, props, pipe);
                    target = userReceiver;
                    if (userReceiver instanceof Emitter) {
                        emitter = (Emitter) userReceiver;
                    } else {
                        return userReceiver;
                    }
                }
            }
            if (emitter != null) {
                emitter.setOutputProperties(props);
                StreamResult sr = (StreamResult) result;
                emitter.setStreamResult(sr);
            }
            return target;

        } else if (staxResultClass != null && staxResultClass.isAssignableFrom(result.getClass())) {
            // TODO: dynamic loading is no longer needed here
            StAXResultHandler handler = (StAXResultHandler) config.getDynamicLoader().getInstance("net.sf.saxon.stax.StAXResultHandlerImpl", getClass().getClassLoader());
            Receiver r = handler.getReceiver(result, props);
            r.setPipelineConfiguration(pipe);
            return makeSequenceNormalizer(r, props);
        } else {
            if (pipe != null) {
                // try to find an external object model that knows this kind of Result
                List externalObjectModels = pipe.getConfiguration().getExternalObjectModels();
                for (Object externalObjectModel : externalObjectModels) {
                    ExternalObjectModel model = (ExternalObjectModel) externalObjectModel;
                    Receiver builder = model.getDocumentBuilder(result);
                    if (builder != null) {
                        builder.setSystemId(result.getSystemId());
                        builder.setPipelineConfiguration(pipe);
                        return new TreeReceiver(builder);
                    }
                }
            }
        }

        throw new IllegalArgumentException("Unknown type of result: " + result.getClass());
    }

    protected SequenceReceiver makeSequenceNormalizer(Receiver receiver, Properties properties) {
        NamespaceReducer ne = new NamespaceReducer(receiver);
        ne.setSystemId(receiver.getSystemId());
        PipelineConfiguration pipe = receiver.getPipelineConfiguration();
        if (properties.getProperty(SaxonOutputKeys.ITEM_SEPARATOR) == null) {
            ComplexContentOutputter out = new ComplexContentOutputter(pipe);
            out.setHostLanguage(pipe.getHostLanguage());
            out.setSerializing(true);
            out.setReceiver(ne);
            return out;
        } else {
            SequenceNormalizer sn = new SequenceNormalizer(ne, properties.getProperty(SaxonOutputKeys.ITEM_SEPARATOR));
            sn.setPipelineConfiguration(pipe);
            return sn;
        }
    }

    /**
     * Create a serialization pipeline to implement the HTML output method. This method is protected
     * so that it can be customized in a user-written SerializerFactory
     *
     * @param emitter              the emitter at the end of the pipeline (created using the method {@link #newHTMLEmitter}
     * @param props                the serialization properties
     * @param pipe                 the pipeline configuration information
     * @param characterMapExpander the filter to be used for expanding character maps defined in the stylesheet
     * @param normalizer           the filter used for Unicode normalization
     * @return a Receiver acting as the entry point to the serialization pipeline
     * @throws XPathException if a failure occurs
     */

    protected SequenceReceiver createHTMLSerializer(
            Emitter emitter, Properties props, PipelineConfiguration pipe,
            CharacterMapExpander characterMapExpander, ProxyReceiver normalizer) throws XPathException {
        Receiver target;
        target = emitter;
        if (!"no".equals(props.getProperty(OutputKeys.INDENT))) {
            target = newHTMLIndenter(target, props);
        }
        if (normalizer != null) {
            normalizer.setUnderlyingReceiver(target);
            target = normalizer;
        }
        if (characterMapExpander != null) {
            characterMapExpander.setUnderlyingReceiver(target);
            target = characterMapExpander;
        }
        String cdataElements = props.getProperty(OutputKeys.CDATA_SECTION_ELEMENTS);
        if (cdataElements != null && cdataElements.length() > 0) {
            target = newCDATAFilter(target, props);
        }

        if (SaxonOutputKeys.isXhtmlHtmlVersion5(props)) {
            target = addHtml5Component(target, props);
        }

        if (!"no".equals(props.getProperty(SaxonOutputKeys.ESCAPE_URI_ATTRIBUTES))) {
            target = newHTMLURIEscaper(target, props);
        }
        if (!"no".equals(props.getProperty(SaxonOutputKeys.INCLUDE_CONTENT_TYPE))) {
            target = newHTMLMetaTagAdjuster(target, props);
        }
        String attributeOrder = props.getProperty(SaxonOutputKeys.ATTRIBUTE_ORDER);
        if (attributeOrder != null && attributeOrder.length() > 0) {
            target = newAttributeSorter(target, props);
        }
        return makeSequenceNormalizer(target, props);
    }

    /**
     * Create a serialization pipeline to implement the text output method. This method is protected
     * so that it can be customized in a user-written SerializerFactory
     *
     * @param emitter              the emitter at the end of the pipeline (created using the method {@link #newTEXTEmitter}
     * @param props                the serialization properties
     * @param characterMapExpander the filter to be used for expanding character maps defined in the stylesheet
     * @param normalizer           the filter used for Unicode normalization
     * @return a Receiver acting as the entry point to the serialization pipeline
     * @throws XPathException if a failure occurs
     */

    protected SequenceReceiver createTextSerializer(
            Emitter emitter, Properties props,
            CharacterMapExpander characterMapExpander, ProxyReceiver normalizer) throws XPathException {
        Receiver target;
        target = emitter;
        if (characterMapExpander != null) {
            characterMapExpander.setUnderlyingReceiver(target);
            characterMapExpander.setUseNullMarkers(false);
            target = characterMapExpander;
        }
        if (normalizer != null) {
            normalizer.setUnderlyingReceiver(target);
            target = normalizer;
        }
        target = addTextOutputFilter(target, props);
        return makeSequenceNormalizer(target, props);
    }

    /**
     * Create a serialization pipeline to implement the JSON output method. This method is protected
     * so that it can be customized in a user-written SerializerFactory
     *
     * @param emitter              the emitter at the end of the pipeline (created using the method {@link #newTEXTEmitter}
     * @param props                the serialization properties
     * @param characterMapExpander the filter to be used for expanding character maps defined in the stylesheet
     * @param normalizer           the filter used for Unicode normalization
     * @return a Receiver acting as the entry point to the serialization pipeline
     * @throws XPathException if a failure occurs
     */

    protected SequenceReceiver customizeJSONSerializer(
        JSONEmitter emitter, Properties props,
        CharacterMapExpander characterMapExpander, ProxyReceiver normalizer) throws XPathException {
        if (normalizer instanceof UnicodeNormalizer) {
            emitter.setNormalizer(((UnicodeNormalizer)normalizer).getNormalizer());
        }
        if (characterMapExpander != null) {
            emitter.setCharacterMap(characterMapExpander.getCharacterMap());
        }
        return emitter;
    }

    /**
     * Create a serialization pipeline to implement the Adaptive output method. This method is protected
     * so that it can be customized in a user-written SerializerFactory
     *
     * @param emitter              the emitter at the end of the pipeline
     * @param props                the serialization properties
     * @param characterMapExpander the filter to be used for expanding character maps defined in the stylesheet
     * @param normalizer           the filter used for Unicode normalization
     * @return a Receiver acting as the entry point to the serialization pipeline
     * @throws XPathException if a failure occurs
     */

    protected SequenceReceiver customizeAdaptiveSerializer(
            AdaptiveEmitter emitter, Properties props,
            CharacterMapExpander characterMapExpander, ProxyReceiver normalizer) throws XPathException {
        return emitter;
    }


    /**
     * Create a serialization pipeline to implement the XHTML output method. This method is protected
     * so that it can be customized in a user-written SerializerFactory
     *
     * @param emitter              the emitter at the end of the pipeline (created using the method {@link #newXHTMLEmitter}
     * @param props                the serialization properties
     * @param pipe                 the pipeline configuration information
     * @param characterMapExpander the filter to be used for expanding character maps defined in the stylesheet
     * @param normalizer           the filter used for Unicode normalization
     * @return a Receiver acting as the entry point to the serialization pipeline
     * @throws XPathException if a failure occurs
     */

    protected SequenceReceiver createXHTMLSerializer(
            Emitter emitter, Properties props, PipelineConfiguration pipe,
            CharacterMapExpander characterMapExpander, ProxyReceiver normalizer) throws XPathException {
        Receiver target = emitter;
        if (!"no".equals(props.getProperty(OutputKeys.INDENT))) {
            target = newXHTMLIndenter(target, props);
        }
        if (normalizer != null) {
            normalizer.setUnderlyingReceiver(target);
            target = normalizer;
        }
        if (characterMapExpander != null) {
            characterMapExpander.setUnderlyingReceiver(target);
            characterMapExpander.setPipelineConfiguration(pipe);
            target = characterMapExpander;
        }
        String cdataElements = props.getProperty(OutputKeys.CDATA_SECTION_ELEMENTS);
        if (cdataElements != null && cdataElements.length() > 0) {
            target = newCDATAFilter(target, props);
        }

        if (SaxonOutputKeys.isXhtmlHtmlVersion5(props)) {
            target = addHtml5Component(target, props);
        }
        if (!"no".equals(props.getProperty(SaxonOutputKeys.ESCAPE_URI_ATTRIBUTES))) {
            target = newXHTMLURIEscaper(target, props);
        }
        if (!"no".equals(props.getProperty(SaxonOutputKeys.INCLUDE_CONTENT_TYPE))) {
            target = newXHTMLMetaTagAdjuster(target, props);
        }
        String attributeOrder = props.getProperty(SaxonOutputKeys.ATTRIBUTE_ORDER);
        if (attributeOrder != null && attributeOrder.length() > 0) {
            target = newAttributeSorter(target, props);
        }
        return makeSequenceNormalizer(target, props);
    }

    public Receiver addHtml5Component(Receiver target, Properties outputProperties) {
        target = new NamespaceReducer(target);
        target = new XHTMLPrefixRemover(target);
        return target;
    }

    /**
     * Create a serialization pipeline to implement the XML output method. This method is protected
     * so that it can be customized in a user-written SerializerFactory
     *
     * @param emitter              the emitter at the end of the pipeline (created using the method {@link #newXHTMLEmitter}
     * @param props                the serialization properties
     * @param pipe                 the pipeline configuration information
     * @param characterMapExpander the filter to be used for expanding character maps defined in the stylesheet
     * @param normalizer           the filter used for Unicode normalization
     * @return a Receiver acting as the entry point to the serialization pipeline
     * @throws XPathException if a failure occurs
     */

    protected SequenceReceiver createXMLSerializer(
            XMLEmitter emitter, Properties props, PipelineConfiguration pipe,
            CharacterMapExpander characterMapExpander, ProxyReceiver normalizer) throws XPathException {
        Receiver target;

        if ("yes".equals(props.getProperty(OutputKeys.INDENT))) {
            target = newXMLIndenter(emitter, props);
        } else {
            target = emitter;
        }
        if ("1.0".equals(props.getProperty(OutputKeys.VERSION)) &&
                pipe.getConfiguration().getXMLVersion() == Configuration.XML11) {
            // Check result meets XML 1.0 constraints if configuration allows XML 1.1 input but
            // this result document must conform to 1.0
            target = newXML10ContentChecker(target, props);
        }
        if (normalizer != null) {
            normalizer.setUnderlyingReceiver(target);
            target = normalizer;
        }
        if (characterMapExpander != null) {
            characterMapExpander.setUnderlyingReceiver(target);
            target = characterMapExpander;
        }
        String cdataElements = props.getProperty(OutputKeys.CDATA_SECTION_ELEMENTS);
        if (cdataElements != null && cdataElements.length() > 0) {
            target = newCDATAFilter(target, props);
        }
        String attributeOrder = props.getProperty(SaxonOutputKeys.ATTRIBUTE_ORDER);
        if (attributeOrder != null && attributeOrder.length() > 0) {
            target = newAttributeSorter(target, props);
        }
        return makeSequenceNormalizer(target, props);
    }

    protected SequenceReceiver createSaxonSerializationMethod(
            String method, Properties props,
            PipelineConfiguration pipe, CharacterMapExpander characterMapExpander,
            ProxyReceiver normalizer) throws XPathException {
        throw new XPathException("Saxon serialization methods require Saxon-PE to be enabled");
    }

    /**
     * Create a serialization pipeline to implement a user-defined output method. This method is protected
     * so that it can be customized in a user-written SerializerFactory
     *
     * @param method the name of the user-defined output method, as a QName in Clark format
     *               (that is "{uri}local").
     * @param props  the serialization properties
     * @param pipe   the pipeline configuration information
     * @return a Receiver acting as the entry point to the serialization pipeline
     * @throws XPathException if a failure occurs
     */

    protected SequenceReceiver createUserDefinedOutputMethod(String method, Properties props, PipelineConfiguration pipe) throws XPathException {
        Receiver userReceiver;// See if this output method is recognized by the Configuration
        userReceiver = pipe.getConfiguration().makeEmitter(method, props);
        userReceiver.setPipelineConfiguration(pipe);
        if (userReceiver instanceof ContentHandlerProxy &&
                "yes".equals(props.getProperty(SaxonOutputKeys.SUPPLY_SOURCE_LOCATOR))) {
            if (pipe.getConfiguration().isCompileWithTracing()) {
                pipe.getController().addTraceListener(
                        ((ContentHandlerProxy) userReceiver).getTraceListener());
            } else {
                throw new XPathException(
                        "Cannot use saxon:supply-source-locator unless tracing was enabled at compile time", SaxonErrorCode.SXSE0002);
            }
        }
        return userReceiver instanceof SequenceReceiver ? (SequenceReceiver)userReceiver : new TreeReceiver(userReceiver);
    }


    /**
     * Create a ContentHandlerProxy. This method exists so that it can be overridden in a subclass.
     *
     * @return the newly created ContentHandlerProxy.
     */

    protected ContentHandlerProxy newContentHandlerProxy() {
        return new ContentHandlerProxy();
    }

    /**
     * Create an UncommittedSerializer. This method exists so that it can be overridden in a subclass.
     *
     * @param result     the result destination
     * @param next       the next receiver in the pipeline
     * @param properties the serialization properties
     * @return the newly created UncommittedSerializer.
     */

    protected UncommittedSerializer newUncommittedSerializer(Result result, Receiver next, Properties properties, CharacterMapIndex charMap) {
        return new UncommittedSerializer(result, next, properties, charMap);
    }

    /**
     * Create a new XML Emitter. This method exists so that it can be overridden in a subclass.
     *
     * @param properties the output properties
     * @return the newly created XML emitter.
     */

    protected Emitter newXMLEmitter(Properties properties) {
        return new XMLEmitter();
    }

    /**
     * Create a new HTML Emitter. This method exists so that it can be overridden in a subclass.
     *
     * @param properties the output properties
     * @return the newly created HTML emitter.
     */

    protected Emitter newHTMLEmitter(Properties properties) {
        HTMLEmitter emitter;
        // Note, we recognize html-version even when running XSLT 2.0.
        if (SaxonOutputKeys.isHtmlVersion5(properties)) {
            emitter = new HTML50Emitter();
        } else {
            emitter = new HTML40Emitter();
        }
        return emitter;
    }

    /**
     * Create a new XHTML Emitter. This method exists so that it can be overridden in a subclass.
     *
     * @param properties the output properties
     * @return the newly created XHTML emitter.
     */

    protected Emitter newXHTMLEmitter(Properties properties) {
        boolean is5 = SaxonOutputKeys.isXhtmlHtmlVersion5(properties);
        XMLEmitter emitter = is5 ? new XHTML5Emitter() : new XHTML1Emitter();
        return emitter;
    }

    /**
     * Add a filter to the text output method pipeline. This does nothing unless overridden
     * in a subclass
     *
     * @param next       the next receiver (typically the TextEmitter)
     * @param properties the output properties
     * @return the receiver to be used in place of the "next" receiver
     * @throws XPathException if the operation fails
     */

    public Receiver addTextOutputFilter(Receiver next, Properties properties) throws XPathException {
        return next;
    }

    /**
     * Create a new Text Emitter. This method exists so that it can be overridden in a subclass.
     *
     * @return the newly created text emitter.
     */

    protected Emitter newTEXTEmitter() {
        return new TEXTEmitter();
    }

    /**
     * Create a new Adaptive Emitter. This method exists so that it can be overridden in a subclass.
     *
     * @return the newly created adaptive emitter.
     */

    protected SequenceWriter newAdaptiveEmitter(PipelineConfiguration pipe, Writer writer) {
        return new AdaptiveEmitter(pipe, writer);
    }


    /**
     * Create a new XML Indenter. This method exists so that it can be overridden in a subclass.
     *
     * @param next             the next receiver in the pipeline
     * @param outputProperties the serialization parameters
     * @return the newly created XML indenter.
     */

    protected ProxyReceiver newXMLIndenter(XMLEmitter next, Properties outputProperties) {
        XMLIndenter r = new XMLIndenter(next);
        r.setOutputProperties(outputProperties);
        return r;
    }

    /**
     * Create a new HTML Indenter. This method exists so that it can be overridden in a subclass.
     *
     * @param next             the next receiver in the pipeline
     * @param outputProperties the serialization parameters
     * @return the newly created HTML indenter.
     */

    protected ProxyReceiver newHTMLIndenter(Receiver next, Properties outputProperties) {
        return new HTMLIndenter(next, "html");
    }

    /**
     * Create a new XHTML Indenter. This method exists so that it can be overridden in a subclass.
     *
     * @param next             the next receiver in the pipeline
     * @param outputProperties the serialization parameters
     * @return the newly created XHTML indenter.
     */

    protected ProxyReceiver newXHTMLIndenter(Receiver next, Properties outputProperties) {
        return new HTMLIndenter(next, "xhtml");
    }

    /**
     * Create a new XHTML MetaTagAdjuster, responsible for insertion, removal, or replacement of meta
     * elements. This method exists so that it can be overridden in a subclass.
     *
     * @param next             the next receiver in the pipeline
     * @param outputProperties the serialization parameters
     * @return the newly created XHTML MetaTagAdjuster.
     */

    protected MetaTagAdjuster newXHTMLMetaTagAdjuster(Receiver next, Properties outputProperties) {
        MetaTagAdjuster r = new MetaTagAdjuster(next);
        r.setIsXHTML(true);
        r.setOutputProperties(outputProperties);
        return r;
    }

    /**
     * Create a new XHTML MetaTagAdjuster, responsible for insertion, removal, or replacement of meta
     * elements. This method exists so that it can be overridden in a subclass.
     *
     * @param next             the next receiver in the pipeline
     * @param outputProperties the serialization parameters
     * @return the newly created HTML MetaTagAdjuster.
     */

    protected MetaTagAdjuster newHTMLMetaTagAdjuster(Receiver next, Properties outputProperties) {
        MetaTagAdjuster r = new MetaTagAdjuster(next);
        r.setIsXHTML(false);
        r.setOutputProperties(outputProperties);
        return r;
    }

    /**
     * Create a new HTML URI Escaper, responsible for percent-encoding of URIs in
     * HTML output documents. This method exists so that it can be overridden in a subclass.
     *
     * @param next             the next receiver in the pipeline
     * @param outputProperties the serialization parameters
     * @return the newly created HTML URI escaper.
     */

    protected ProxyReceiver newHTMLURIEscaper(Receiver next, Properties outputProperties) {
        return new HTMLURIEscaper(next);
    }

    /**
     * Create a new XHTML URI Escaper, responsible for percent-encoding of URIs in
     * HTML output documents. This method exists so that it can be overridden in a subclass.
     *
     * @param next             the next receiver in the pipeline
     * @param outputProperties the serialization parameters
     * @return the newly created HTML URI escaper.
     */

    protected ProxyReceiver newXHTMLURIEscaper(Receiver next, Properties outputProperties) {
        return new XHTMLURIEscaper(next);
    }


    /**
     * Create a new CDATA Filter, responsible for insertion of CDATA sections where required.
     * This method exists so that it can be overridden in a subclass.
     *
     * @param next             the next receiver in the pipeline
     * @param outputProperties the serialization parameters
     * @return the newly created CDATA filter.
     * @throws net.sf.saxon.trans.XPathException
     *          if an error occurs
     */

    protected ProxyReceiver newCDATAFilter(Receiver next, Properties outputProperties) throws XPathException {
        CDATAFilter r = new CDATAFilter(next);
        r.setOutputProperties(outputProperties);
        return r;
    }

    /**
     * Create a new AttributeSorter, responsible for sorting of attributes into a specified order.
     * This method exists so that it can be overridden in a subclass. The Saxon-HE version of
     * this method returns the supplied receiver unchanged (attribute sorting is not supported
     * in Saxon-HE)
     *
     * @param next             the next receiver in the pipeline
     * @param outputProperties the serialization parameters
     * @return the newly created CDATA filter.
     * @throws net.sf.saxon.trans.XPathException
     *          if an error occurs
     */

    protected Receiver newAttributeSorter(Receiver next, Properties outputProperties) throws XPathException {
        return next;
    }


    /**
     * Create a new XML 1.0 content checker, responsible for checking that the output conforms to
     * XML 1.0 rules (this is used only if the Configuration supports XML 1.1 but the specific output
     * file requires XML 1.0). This method exists so that it can be overridden in a subclass.
     *
     * @param next             the next receiver in the pipeline
     * @param outputProperties the serialization parameters
     * @return the newly created XML 1.0 content checker.
     */

    protected ProxyReceiver newXML10ContentChecker(Receiver next, Properties outputProperties) {
        return new XML10ContentChecker(next);
    }

    /**
     * Create a Unicode Normalizer. This method exists so that it can be overridden in a subclass.
     *
     * @param next             the next receiver in the pipeline
     * @param outputProperties the serialization parameters
     * @return the newly created Unicode normalizer.
     * @throws net.sf.saxon.trans.XPathException
     *          if an error occurs
     */

    protected ProxyReceiver newUnicodeNormalizer(Receiver next, Properties outputProperties) throws XPathException {
        String normForm = outputProperties.getProperty(SaxonOutputKeys.NORMALIZATION_FORM);
        return new UnicodeNormalizer(normForm, next);
    }

    /**
     * Create a new CharacterMapExpander. This method exists so that it can be overridden in a subclass.
     *
     * @param next the next receiver in the pipeline
     * @return the newly created CharacterMapExpander.
     */

    public CharacterMapExpander newCharacterMapExpander(Receiver next) {
        return new CharacterMapExpander(next);
    }

    /**
     * Prepare another stylesheet to handle the output of this one.
     * <p/>
     * This method is intended for internal use, to support the
     * <code>saxon:next-in-chain</code> extension.
     *
     * @param pipe the current transformation
     * @param href       URI of the next stylesheet to be applied
     * @param baseURI    base URI for resolving href if it's a relative
     *                   URI
     * @param result     the output destination of the current stylesheet
     * @return a replacement destination for the current stylesheet
     * @throws XPathException if any dynamic error occurs
     */

    public SequenceReceiver prepareNextStylesheet(PipelineConfiguration pipe, String href, String baseURI, Result result)
            throws XPathException {
        pipe.getConfiguration().checkLicensedFeature(Configuration.LicenseFeature.PROFESSIONAL_EDITION, "saxon:next-in-chain", -1);
        return null;
    }

    /**
     * Get a SequenceWrapper, a class that serializes an XDM sequence with full annotation of item types, node kinds,
     * etc. There are variants for Saxon-HE and Saxon-PE
     * @param destination the place where the wrapped sequence will be sent
     * @return the new SequenceWrapper
     */

    public SequenceWrapper newSequenceWrapper(Receiver destination) {
        return new SequenceWrapper(destination);
    }

    /**
     * Check that a supplied output property is valid, and normalize the value (specifically in the case of boolean
     * values where yes|true|1 are normalized to "yes", and no|false|0 are normalized to "no").
     *
     * @param key     the name of the property, in Clark format
     * @param value   the value of the property. This may be set to null, in which case no validation takes place.
     *                The value must be in JAXP format, that is, with lexical QNames expanded to Clark names
     * @return normalized value of the property, or null if the supplied value is null
     * @throws XPathException if the property name or value is invalid
     */

    public String checkOutputProperty(String key, String value) throws XPathException {
        if (!key.startsWith("{")) {
            if (key.equals(SaxonOutputKeys.ALLOW_DUPLICATE_NAMES)) {
                if (value != null) {
                    value = checkYesOrNo(key, value);
                }
            } else if (key.equals(SaxonOutputKeys.BUILD_TREE)) {
                if (value != null) {
                    value = checkYesOrNo(key, value);
                }
            } else if (key.equals(SaxonOutputKeys.BYTE_ORDER_MARK)) {
                if (value != null) {
                    value = checkYesOrNo(key, value);
                }
            } else if (key.equals(OutputKeys.CDATA_SECTION_ELEMENTS)) {
                if (value != null) {
                    checkListOfClarkNames(key, value);
                }
            } else if (key.equals(OutputKeys.DOCTYPE_PUBLIC)) {
                if (value != null) {
                    checkPublicIdentifier(value);
                }
            } else if (key.equals(OutputKeys.DOCTYPE_SYSTEM)) {
                if (value != null) {
                    checkSystemIdentifier(value);
                }
            } else if (key.equals(OutputKeys.ENCODING)) {
                // no constraints
            } else if (key.equals(SaxonOutputKeys.ESCAPE_URI_ATTRIBUTES)) {
                if (value != null) {
                    value = checkYesOrNo(key, value);
                }
            } else if (key.equals(SaxonOutputKeys.HTML_VERSION)) {
                if (value != null) {
                    checkDecimal(key, value);
                }
            } else if (key.equals(SaxonOutputKeys.INCLUDE_CONTENT_TYPE)) {
                if (value != null) {
                    value = checkYesOrNo(key, value);
                }
            } else if (key.equals(OutputKeys.INDENT)) {
                if (value != null) {
                    value = checkYesOrNo(key, value);
                }
            } else if (key.equals(SaxonOutputKeys.ITEM_SEPARATOR)) {
                // no checking needed

            } else if (key.equals(OutputKeys.METHOD) || key.equals(SaxonOutputKeys.JSON_NODE_OUTPUT_METHOD)) {
                if (value != null) {
                    checkMethod(key, value);
                }
            } else if (key.equals(OutputKeys.MEDIA_TYPE)) {
                // no constraints
            } else if (key.equals(SaxonOutputKeys.NORMALIZATION_FORM)) {
                if (value != null) {
                    checkNormalizationForm(value);
                }
            } else if (key.equals(OutputKeys.OMIT_XML_DECLARATION)) {
                if (value != null) {
                    value = checkYesOrNo(key, value);
                }
            } else if (key.equals(OutputKeys.STANDALONE)) {
                if (value != null && !value.equals("omit")) {
                    value = checkYesOrNo(key, value);
                }
            } else if (key.equals(SaxonOutputKeys.SUPPRESS_INDENTATION)) {
                if (value != null) {
                    checkListOfClarkNames(key, value);
                }
            } else if (key.equals(SaxonOutputKeys.UNDECLARE_PREFIXES)) {
                if (value != null) {
                    value = checkYesOrNo(key, value);
                }
            } else if (key.equals(SaxonOutputKeys.USE_CHARACTER_MAPS)) {
                if (value != null) {
                    checkListOfClarkNames(key, value);
                }
            } else if (key.equals(OutputKeys.VERSION)) {
                // no constraints

            } else if (key.equals(SaxonOutputKeys.PARAMETER_DOCUMENT)) {
                // no checking
            } else {
                throw new XPathException("Unknown serialization parameter " + Err.wrap(key), "XQST0109");
            }
        } else if (key.startsWith("{http://saxon.sf.net/}")) {
            // Some Saxon serialization parameters are recognized in HE if they are used for internal purposes
            if (key.equals(SaxonOutputKeys.STYLESHEET_VERSION)) {
                // return
            } else if (key.equals(SaxonOutputKeys.PARAMETER_DOCUMENT_BASE_URI)) {
                // return
            } else if (key.equals(SaxonOutputKeys.SUPPLY_SOURCE_LOCATOR)) {
                if (value != null) {
                    value = checkYesOrNo(key, value);
                }
            } else if (key.equals(SaxonOutputKeys.UNFAILING)) {
                if (value != null) {
                    value = checkYesOrNo(key, value);
                }
            } else {
                throw new XPathException("Serialization parameter " + Err.wrap(key) + " not available in Saxon-HE", "XQST0109");
            }
        } else {
            //return;
        }
        return value;
    }

    protected static String checkYesOrNo(String key, String value) throws XPathException {
        if ("yes".equals(value) || "true".equals(value) || "1".equals(value)) {
            return "yes";
        } else if ("no".equals(value) || "false".equals(value) || "0".equals(value)) {
            return "no";
        } else {
            throw new XPathException("Serialization parameter " + Err.wrap(key) + " must have the value yes|no, true|false, or 1|0", "SEPM0016");
        }
    }

    private void checkMethod(String key, String value) throws XPathException {
        if (!"xml".equals(value) && !"html".equals(value) && !"xhtml".equals(value) && !"text".equals(value)) {
            if (!SaxonOutputKeys.JSON_NODE_OUTPUT_METHOD.equals(key) && ("json".equals(value) || "adaptive".equals(value))) {
                return;
            }
            if (isValidClarkName(value)) {
                checkExtensions(value);
            } else {
                throw new XPathException("Invalid value for serialization method: " +
                                                 "must be xml|html|xhtml|text|json|adaptive, or a QName in '{uri}local' form", "SEPM0016");
            }
        }

    }

    private static void checkNormalizationForm(String value) throws XPathException {
        if (!NameChecker.isValidNmtoken(value)) {
            throw new XPathException("Invalid value for normalization-form: " +
                                             "must be NFC, NFD, NFKC, NFKD, fully-normalized, or none", "SEPM0016");
        }
    }

    private static boolean isValidClarkName(/*@NotNull*/ String value) {
        if (value.isEmpty() || value.charAt(0) != '{') {
            return false;
        }
        int closer = value.indexOf('}');
        return closer >= 1 &&
                closer != value.length() - 1 &&
                NameChecker.isValidNCName(value.substring(closer + 1));
    }

    protected static void checkNonNegativeInteger(String key, String value) throws XPathException {
        try {
            int n = Integer.parseInt(value);
            if (n < 0) {
                throw new XPathException("Value of " + Err.wrap(key) + " must be a non-negative integer", "SEPM0016");
            }
        } catch (NumberFormatException err) {
            throw new XPathException("Value of " + Err.wrap(key) + " must be a non-negative integer", "SEPM0016");
        }
    }

    private static void checkDecimal(String key, String value) throws XPathException {
        if (!BigDecimalValue.castableAsDecimal(value)) {
            throw new XPathException("Value of " + Err.wrap(key) +
                                             " must be a decimal number", "SEPM0016");
        }
    }

    protected static void checkListOfClarkNames(String key, String value) throws XPathException {
        StringTokenizer tok = new StringTokenizer(value, " \t\n\r", false);
        while (tok.hasMoreTokens()) {
            String s = tok.nextToken();
            if (isValidClarkName(s) || NameChecker.isValidNCName(s)) {
                // ok
            } else {
                throw new XPathException("Value of " + Err.wrap(key) +
                                                 " must be a list of QNames in '{uri}local' notation", "SEPM0016");
            }
        }
    }

    private static Pattern publicIdPattern = Pattern.compile("^[\\s\\r\\na-zA-Z0-9\\-'()+,./:=?;!*#@$_%]*$");

    private static void checkPublicIdentifier(String value) throws XPathException {
        if (!publicIdPattern.matcher(value).matches()) {
            throw new XPathException("Invalid character in doctype-public parameter", "SEPM0016");
        }
    }

    private static void checkSystemIdentifier(/*@NotNull*/ String value) throws XPathException {
        if (value.contains("'") && value.contains("\"")) {
            throw new XPathException("The doctype-system parameter must not contain both an apostrophe and a quotation mark", "SEPM0016");
        }
    }

    /**
     * Process a serialization property whose value is a list of element names, for example cdata-section-elements
     *
     * @param value        The value of the property as written
     * @param nsResolver   The namespace resolver to use; may be null if prevalidated is set or if names are supplied
     *                     in Clark format
     * @param useDefaultNS
     * @param prevalidated true if the property has already been validated
     * @param errorCode    The error code to return in the event of problems
     * @return The list of element names with lexical QNames replaced by Clark names, starting with a single space
     * @throws XPathException if any error is found in the list of element names, for example, an undeclared namespace prefix
     */

    /*@NotNull*/
    public static String parseListOfNodeNames(
            String value, /*@Nullable*/ NamespaceResolver nsResolver, boolean useDefaultNS, boolean prevalidated, /*@NotNull*/  String errorCode)
            throws XPathException {
        String s = "";
        StringTokenizer st = new StringTokenizer(value, " \t\n\r", false);
        while (st.hasMoreTokens()) {
            String displayname = st.nextToken();
            if (prevalidated || (nsResolver == null)) {
                s += ' ' + displayname;
            } else if (displayname.startsWith("Q{")) {
                s += ' ' + displayname.substring(1);
            } else {
                try {
                    String[] parts = NameChecker.getQNameParts(displayname);
                    String muri = nsResolver.getURIForPrefix(parts[0], useDefaultNS);
                    if (muri == null) {
                        throw new XPathException("Namespace prefix '" + parts[0] + "' has not been declared", errorCode);
                    }
                    s += " {" + muri + '}' + parts[1];
                } catch (QNameException err) {
                    throw new XPathException("Invalid element name. " + err.getMessage(), errorCode);
                }
            }
        }
        return s;
    }

    protected void checkExtensions(String key /*@Nullable*/) throws XPathException {
        throw new XPathException("Serialization property " + Err.wrap(key) + " is not available in Saxon-HE");
    }


}

