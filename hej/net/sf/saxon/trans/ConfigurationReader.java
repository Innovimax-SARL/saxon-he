////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;


import net.sf.saxon.Configuration;
import net.sf.saxon.Version;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.instruct.ResultDocument;
import net.sf.saxon.expr.parser.ExplicitLocation;
import net.sf.saxon.expr.parser.Token;
import net.sf.saxon.expr.parser.XPathParser;
import net.sf.saxon.functions.ResolveURI;
import net.sf.saxon.lib.*;
import net.sf.saxon.om.NamespaceResolver;
import net.sf.saxon.om.QNameParser;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.sxpath.IndependentContext;
import net.sf.saxon.trans.packages.PackageDetails;
import net.sf.saxon.trans.packages.PackageLibrary;
import net.sf.saxon.trans.packages.VersionedPackageName;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.SchemaException;
import net.sf.saxon.value.SequenceExtent;
import org.xml.sax.*;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * Class used to read a config.xml file and transfer all settings from the file to the Configuration
 */

public class ConfigurationReader implements ContentHandler, NamespaceResolver {

    private int level = 0;
    private String section = null;
    private String subsection = null;
    private FastStringBuffer buffer = new FastStringBuffer(100);
    protected Configuration config;
    private ClassLoader classLoader = null;
    private List<XPathException> errors = new ArrayList<XPathException>();
    private Locator locator;
    private Stack<List<String[]>> namespaceStack = new Stack<List<String[]>>();
    private PackageLibrary packageLibrary;
    private PackageDetails currentPackage;

    public ConfigurationReader() {
    }

    /**
     * Set the ClassLoader to be used for dynamic loading of the configuration, and for dynamic loading
     * of other classes used within the configuration. By default the class loader of this class is used.
     *
     * @param classLoader the ClassLoader to be used
     */

    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /**
     * Create a Configuration based on the contents of this configuration file
     *
     * @param source the Source of the configuration file
     * @return the constructed Configuration
     * @throws XPathException
     */

    public Configuration makeConfiguration(Source source) throws XPathException {
        InputSource is;
        XMLReader parser = null;
        if (source instanceof SAXSource) {
            parser = ((SAXSource) source).getXMLReader();
            is = ((SAXSource) source).getInputSource();
        } else if (source instanceof StreamSource) {
            is = new InputSource(source.getSystemId());
            is.setCharacterStream(((StreamSource) source).getReader());
            is.setByteStream(((StreamSource) source).getInputStream());
        } else {
            throw new XPathException("Source for configuration file must be a StreamSource or SAXSource");
        }
        if (parser == null) {
            // Don't use the parser from the pool, it might be validating
            parser = Version.platform.loadParser();
            try {
                parser.setFeature("http://xml.org/sax/features/namespaces", true);
                parser.setFeature("http://xml.org/sax/features/namespace-prefixes", false);
            } catch (SAXNotRecognizedException e) {
                throw new TransformerFactoryConfigurationError(e);
            } catch (SAXNotSupportedException e) {
                throw new TransformerFactoryConfigurationError(e);
            }
        }
        try {
            parser.setContentHandler(this);
            parser.parse(is);
        } catch (IOException e) {
            throw new XPathException("Failed to read config file", e);
        } catch (SAXException e) {
            throw new XPathException("Failed to parse config file", e);
        }

        if (!errors.isEmpty()) {
            ErrorListener listener;
            if (config == null) {
                listener = new StandardErrorListener();
            } else {
                listener = config.getErrorListener();
            }
            try {
                for (XPathException err : errors) {
                    listener.warning(err);
                }
            } catch (TransformerException e) {
                //
            }
            throw errors.get(0);
        }
        return config;
    }

    public void setDocumentLocator(Locator locator) {
        this.locator = locator;
    }

    public void startDocument() throws SAXException {
        namespaceStack.push(new ArrayList<String[]>());
    }

    public void endDocument() throws SAXException {
        namespaceStack.pop();
        config.getDefaultXsltCompilerInfo().setPackageLibrary(packageLibrary);
    }

    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        namespaceStack.peek().add(new String[]{prefix, uri});
    }

    public void endPrefixMapping(String prefix) throws SAXException {

    }

    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
        buffer.setLength(0);
        if (NamespaceConstant.SAXON_CONFIGURATION.equals(uri)) {
            if (level == 0) {
                if (!"configuration".equals(localName)) {
                    error(localName, null, null, "configuration");
                }
                String edition = atts.getValue("edition");
                if (edition == null) {
                    edition = "HE";
                }
                if (edition.equals("HE")) {
                    config = new Configuration();
                } else if (edition.equals("PE")) {
                    config = Configuration.makeLicensedConfiguration(classLoader, "com.saxonica.config.ProfessionalConfiguration");
                } else if (edition.equals("EE")) {
                    config = Configuration.makeLicensedConfiguration(classLoader, "com.saxonica.config.EnterpriseConfiguration");
                } else {
                    error("configuration", "edition", edition, "HE|PE|EE");
                    config = new Configuration();
                }
                packageLibrary = new PackageLibrary(config.getDefaultXsltCompilerInfo());
                String licenseLoc = atts.getValue("licenseFileLocation");
                if (licenseLoc != null && !edition.equals("HE")) {
                    String base = locator.getSystemId();
                    try {
                        URI absoluteLoc = ResolveURI.makeAbsolute(licenseLoc, base);
                        config.setConfigurationProperty(FeatureKeys.LICENSE_FILE_LOCATION, absoluteLoc.toString());
                    } catch (Exception err) {
                        errors.add(new XPathException("Failed to process license at " + licenseLoc, err));
                    }
                }
                String label = atts.getValue("label");
                if (label != null) {
                    config.setLabel(label);
                }
                config.getDynamicLoader().setClassLoader(classLoader);
            }
            if (level == 1) {
                section = localName;
                if ("global".equals(localName)) {
                    readGlobalElement(atts);
                } else if ("serialization".equals(localName)) {
                    readSerializationElement(atts);
                } else if ("xquery".equals(localName)) {
                    readXQueryElement(atts);
                } else if ("xslt".equals(localName)) {
                    readXsltElement(atts);
                } else if ("xsltPackages".equals(localName)) {
                    // no action until later;
                } else if ("xsd".equals(localName)) {
                    readXsdElement(atts);
                } else if ("resources".equals(localName)) {
                    // no action until later
                } else if ("collations".equals(localName)) {
                    // no action until later
                } else if ("localizations".equals(localName)) {
                    readLocalizationsElement(atts);
                } else {
                    error(localName, null, null, null);
                }
            } else if (level == 2) {
                subsection = localName;
                if ("resources".equals(section)) {
                    if ("fileExtension".equals(localName)) {
                        readFileExtension(atts);
                    }
                    // no action until endElement()
                } else if ("collations".equals(section)) {
                    if (!"collation".equals(localName)) {
                        error(localName, null, null, "collation");
                    } else {
                        readCollation(atts);
                    }
                } else if ("localizations".equals(section)) {
                    if (!"localization".equals(localName)) {
                        error(localName, null, null, "localization");
                    } else {
                        readLocalization(atts);
                    }
                } else if ("xslt".equals(section)) {
                    if ("extensionElement".equals(localName)) {
                        readExtensionElement(atts);
                    } else {
                        error(localName, null, null, null);
                    }
                } else if ("xsltPackages".equals(section)) {
                    if ("package".equals(localName)) {
                        readXsltPackage(atts);
                    }
                }
            } else if (level == 3) {
                if ("package".equals(subsection)) {
                    if ("withParam".equals(localName)) {
                        readWithParam(atts);
                    } else {
                        error(localName, null, null, null);
                    }
                }
            }
        } else {
            errors.add(new XPathException("Configuration elements must be in namespace " + NamespaceConstant.SAXON_CONFIGURATION));
        }
        level++;
        namespaceStack.push(new ArrayList<String[]>());
    }

    private void readGlobalElement(Attributes atts) {
        Properties props = new Properties();
        for (int i = 0; i < atts.getLength(); i++) {
            String name = atts.getLocalName(i);
            String value = atts.getValue(i);
            if (value.length() != 0 && atts.getURI(i).isEmpty()) {
                props.put(name, value);
            }
        }
        props.put("#element", "global");
        applyProperty(props, "allowExternalFunctions", FeatureKeys.ALLOW_EXTERNAL_FUNCTIONS);
        applyProperty(props, "allowMultiThreading", FeatureKeys.ALLOW_MULTITHREADING);
        applyProperty(props, "allowOldJavaUriFormat", FeatureKeys.ALLOW_OLD_JAVA_URI_FORMAT);
        applyProperty(props, "allowSyntaxExtensions", FeatureKeys.ALLOW_SYNTAX_EXTENSIONS);
        applyProperty(props, "collationUriResolver", FeatureKeys.COLLATION_URI_RESOLVER_CLASS);
        applyProperty(props, "collectionUriResolver", FeatureKeys.COLLECTION_URI_RESOLVER_CLASS);
        applyProperty(props, "compileWithTracing", FeatureKeys.COMPILE_WITH_TRACING);
        applyProperty(props, "debugByteCode", FeatureKeys.DEBUG_BYTE_CODE);
        applyProperty(props, "debugByteCodeDirectory", FeatureKeys.DEBUG_BYTE_CODE_DIR);
        applyProperty(props, "defaultCollation", FeatureKeys.DEFAULT_COLLATION);
        applyProperty(props, "defaultCollection", FeatureKeys.DEFAULT_COLLECTION);
        applyProperty(props, "defaultRegexEngine", FeatureKeys.DEFAULT_REGEX_ENGINE);
        applyProperty(props, "displayByteCode", FeatureKeys.DISPLAY_BYTE_CODE);
        applyProperty(props, "dtdValidation", FeatureKeys.DTD_VALIDATION);
        applyProperty(props, "dtdValidationRecoverable", FeatureKeys.DTD_VALIDATION_RECOVERABLE);
        applyProperty(props, "eagerEvaluation", FeatureKeys.EAGER_EVALUATION);
        applyProperty(props, "entityResolver", FeatureKeys.ENTITY_RESOLVER_CLASS);
        applyProperty(props, "errorListener", FeatureKeys.ERROR_LISTENER_CLASS);
        applyProperty(props, "environmentVariableResolver", FeatureKeys.ENVIRONMENT_VARIABLE_RESOLVER_CLASS);
        applyProperty(props, "expandAttributeDefaults", FeatureKeys.EXPAND_ATTRIBUTE_DEFAULTS);
        applyProperty(props, "generateByteCode", FeatureKeys.GENERATE_BYTE_CODE);
        applyProperty(props, "ignoreSAXSourceParser", FeatureKeys.IGNORE_SAX_SOURCE_PARSER);
        applyProperty(props, "lineNumbering", FeatureKeys.LINE_NUMBERING);
        applyProperty(props, "markDefaultedAttributes", FeatureKeys.MARK_DEFAULTED_ATTRIBUTES);
        applyProperty(props, "maxCompiledClasses", FeatureKeys.MAX_COMPILED_CLASSES);
        applyProperty(props, "optimizationLevel", FeatureKeys.OPTIMIZATION_LEVEL);
        applyProperty(props, "parser", FeatureKeys.SOURCE_PARSER_CLASS);
        applyProperty(props, "preEvaluateDoc", FeatureKeys.PRE_EVALUATE_DOC_FUNCTION);
        applyProperty(props, "preferJaxpParser", FeatureKeys.PREFER_JAXP_PARSER);
        applyProperty(props, "recognizeUriQueryParameters", FeatureKeys.RECOGNIZE_URI_QUERY_PARAMETERS);
        applyProperty(props, "schemaValidation", FeatureKeys.SCHEMA_VALIDATION_MODE);
        applyProperty(props, "serializerFactory", FeatureKeys.SERIALIZER_FACTORY_CLASS);
        applyProperty(props, "sourceResolver", FeatureKeys.SOURCE_RESOLVER_CLASS);
        applyProperty(props, "stableCollectionUri", FeatureKeys.STABLE_COLLECTION_URI);
        applyProperty(props, "stableUnparsedText", FeatureKeys.STABLE_UNPARSED_TEXT);
        applyProperty(props, "standardErrorOutputFile", FeatureKeys.STANDARD_ERROR_OUTPUT_FILE);
        applyProperty(props, "streamability", FeatureKeys.STREAMABILITY);
        applyProperty(props, "streamingFallback", FeatureKeys.STREAMING_FALLBACK);
        applyProperty(props, "stripSpace", FeatureKeys.STRIP_WHITESPACE);
        applyProperty(props, "styleParser", FeatureKeys.STYLE_PARSER_CLASS);
        applyProperty(props, "suppressEvaluationExpiryWarning", FeatureKeys.SUPPRESS_EVALUATION_EXPIRY_WARNING);
        applyProperty(props, "suppressXsltNamespaceCheck", FeatureKeys.SUPPRESS_XSLT_NAMESPACE_CHECK);
        applyProperty(props, "timing", FeatureKeys.TIMING);
        applyProperty(props, "traceExternalFunctions", FeatureKeys.TRACE_EXTERNAL_FUNCTIONS);
        applyProperty(props, "traceListener", FeatureKeys.TRACE_LISTENER_CLASS);
        applyProperty(props, "traceListenerOutputFile", FeatureKeys.TRACE_LISTENER_OUTPUT_FILE);
        applyProperty(props, "traceOptimizerDecisions", FeatureKeys.TRACE_OPTIMIZER_DECISIONS);
        applyProperty(props, "treeModel", FeatureKeys.TREE_MODEL_NAME);
        applyProperty(props, "unparsedTextUriResolver", FeatureKeys.UNPARSED_TEXT_URI_RESOLVER_CLASS);
        applyProperty(props, "uriResolver", FeatureKeys.URI_RESOLVER_CLASS);
        applyProperty(props, "usePiDisableOutputEscaping", FeatureKeys.USE_PI_DISABLE_OUTPUT_ESCAPING);
        applyProperty(props, "useTypedValueCache", FeatureKeys.USE_TYPED_VALUE_CACHE);
        applyProperty(props, "validationComments", FeatureKeys.VALIDATION_COMMENTS);
        applyProperty(props, "validationWarnings", FeatureKeys.VALIDATION_WARNINGS);
        applyProperty(props, "versionOfXml", FeatureKeys.XML_VERSION);
        applyProperty(props, "xInclude", FeatureKeys.XINCLUDE);
    }

    private void applyProperty(Properties props, String propertyName, String featureKey) {
        String value = props.getProperty(propertyName);
        if (value != null) {
            try {
                config.setConfigurationProperty(featureKey, value);
            } catch (IllegalArgumentException e) {
                String message = e.getMessage();
                if (message.startsWith(featureKey)) {
                    message = message.replace(featureKey, "Value");
                }
                if (message.startsWith("Unknown configuration property")) {
                    message = "Property not available in Saxon-" + config.getEditionCode();
                }
                error(props.getProperty("#element"), propertyName, value, message);
            }
        }
    }

    private void readSerializationElement(Attributes atts) {
        Properties props = new Properties();
        for (int i = 0; i < atts.getLength(); i++) {
            String uri = atts.getURI(i);
            String name = atts.getLocalName(i);
            String value = atts.getValue(i);
            if (value.isEmpty()) {
                continue;
            }
            try {
                ResultDocument.setSerializationProperty(props,
                                                        uri, name, value, this, false, config);
            } catch (XPathException e) {
                errors.add(e);
            }
        }
        config.setDefaultSerializationProperties(props);
    }

    private void readCollation(Attributes atts) {
        Properties props = new Properties();
        String collationUri = null;
        for (int i = 0; i < atts.getLength(); i++) {
            if (atts.getURI(i).isEmpty()) {
                String name = atts.getLocalName(i);
                String value = atts.getValue(i);
                if (value.isEmpty()) {
                    continue;
                }
                if ("uri".equals(name)) {
                    collationUri = value;
                } else {
                    props.put(name, value);
                }
            }
        }
        if (collationUri == null) {
            errors.add(new XPathException("collation specified with no uri"));
        }
        StringCollator collator = null;
        try {
            collator = Version.platform.makeCollation(config, props, collationUri);
        } catch (XPathException e) {
            errors.add(e);
        }
        config.registerCollation(collationUri, collator);

    }

    private void readLocalizationsElement(Attributes atts) {
        for (int i = 0; i < atts.getLength(); i++) {
            if (atts.getURI(i).isEmpty()) {
                String name = atts.getLocalName(i);
                String value = atts.getValue(i);
                if ("defaultLanguage".equals(name) && value.length() > 0) {
                    config.setConfigurationProperty(FeatureKeys.DEFAULT_LANGUAGE, value);
                }
                if ("defaultCountry".equals(name) && value.length() > 0) {
                    config.setConfigurationProperty(FeatureKeys.DEFAULT_COUNTRY, value);
                }
            }
        }
    }

    private void readLocalization(Attributes atts) {
        String lang = null;
        Properties properties = new Properties();
        for (int i = 0; i < atts.getLength(); i++) {
            if (atts.getURI(i).isEmpty()) {
                String name = atts.getLocalName(i);
                String value = atts.getValue(i);
                if ("lang".equals(name) && value.length() > 0) {
                    lang = value;
                } else if (value.length() > 0) {
                    properties.setProperty(name, value);
                }
            }
        }
        if (lang != null) {
            LocalizerFactory factory = config.getLocalizerFactory();
            if (factory != null) {
                factory.setLanguageProperties(lang, properties);
            }
        }
    }

    private void readFileExtension(Attributes atts) {
        String extension = atts.getValue("", "extension");
        String mediaType = atts.getValue("", "mediaType");
        if (extension == null) {
            error("fileExtension", "extension", null, null);
        }
        if (mediaType == null) {
            error("fileExtension", "mediaType", null, null);
        }
        config.registerFileExtension(extension, mediaType);
    }

    /**
     * Process details of XSLT extension elements. Overridden in Saxon-PE configuration reader
     *
     * @param atts the attributes of the extensionElement element in the configuration file
     */

    protected void readExtensionElement(Attributes atts) {
        XPathException err = new XPathException("Extension elements are not available in Saxon-HE");
        err.setLocator(ExplicitLocation.makeFromSax(locator));
        errors.add(err);
    }

    protected void readXsltPackage(Attributes atts) {

        String name = atts.getValue("name");
        String version = atts.getValue("version");
        VersionedPackageName vpn = null;
        PackageDetails details = new PackageDetails();
        try {
            vpn = new VersionedPackageName(name, version);
        } catch (XPathException err) {
            error("package", "version", version, null);
        }
        details.nameAndVersion = vpn;
        currentPackage = details;
        String sourceLoc = atts.getValue("sourceLocation");
        StreamSource source = null;
        if (sourceLoc != null) {
            try {
                source = new StreamSource(
                        ResolveURI.makeAbsolute(sourceLoc, locator.getSystemId()).toString());
            } catch (URISyntaxException e) {
                error("package", "sourceLocation", sourceLoc, "Requires a valid URI.");
            }
            details.sourceLocation = source;
        }
        String exportLoc = atts.getValue("exportLocation");
        if (exportLoc != null) {
            try {
                source = new StreamSource(
                        ResolveURI.makeAbsolute(exportLoc, locator.getSystemId()).toString());
            } catch (URISyntaxException e) {
                error("package", "exportLocation", exportLoc, "Requires a valid URI.");
            }
            details.exportLocation = source;
        }
        String priority = atts.getValue("priority");
        if (priority != null) {
            try {
                details.priority = Integer.parseInt(priority);
            } catch (NumberFormatException err) {
                error("package", "priority", priority, "Requires an integer.");
            }
        }
        details.baseName = atts.getValue("base");
        details.shortName = atts.getValue("shortName");

        packageLibrary.addPackage(details);
    }

    protected void readWithParam(Attributes atts) {
        if (currentPackage.exportLocation != null) {
            error("withParam", null, null, "Not allowed when @exportLocation exists");
        }
        String name = atts.getValue("name");
        if (name == null) {
            error("withParam", "name", null, null);
        }
        QNameParser qp = new QNameParser(this);
        qp.setAcceptEQName(true);
        StructuredQName qName = null;
        try {
            qName = qp.parse(name);
        } catch (XPathException e) {
            error("withParam", "name", name, "Requires valid QName");
        }
        String select = atts.getValue("select");
        if (select == null) {
            error("withParam", "select", null, null);
        }
        IndependentContext env = new IndependentContext(config);
        env.setNamespaceResolver(this);
        XPathParser parser = new XPathParser();
        Sequence value = null;
        try {
            Expression exp = parser.parse(select, 0, Token.EOF, env);
            value = SequenceExtent.makeSequenceExtent(exp.iterate(env.makeEarlyEvaluationContext()));
        } catch (XPathException e) {
            error(e);
        }
        if (currentPackage.staticParams == null) {
            currentPackage.staticParams = new HashMap<StructuredQName, Sequence>();
        }
        currentPackage.staticParams.put(qName, value);
    }


    private void readXQueryElement(Attributes atts) {
        Properties props = new Properties();
        for (int i = 0; i < atts.getLength(); i++) {
            String name = atts.getLocalName(i);
            String value = atts.getValue(i);
            if (value.length() != 0 && atts.getURI(i).isEmpty()) {
                props.put(name, value);
            }
        }
        props.put("#element", "xquery");
        applyProperty(props, "allowUpdate", FeatureKeys.XQUERY_ALLOW_UPDATE);
        applyProperty(props, "constructionMode", FeatureKeys.XQUERY_CONSTRUCTION_MODE);
        applyProperty(props, "defaultElementNamespace", FeatureKeys.XQUERY_DEFAULT_ELEMENT_NAMESPACE);
        applyProperty(props, "defaultFunctionNamespace", FeatureKeys.XQUERY_DEFAULT_FUNCTION_NAMESPACE);
        applyProperty(props, "emptyLeast", FeatureKeys.XQUERY_EMPTY_LEAST);
        applyProperty(props, "inheritNamespaces", FeatureKeys.XQUERY_INHERIT_NAMESPACES);
        applyProperty(props, "moduleUriResolver", FeatureKeys.MODULE_URI_RESOLVER_CLASS);
        applyProperty(props, "preserveBoundarySpace", FeatureKeys.XQUERY_PRESERVE_BOUNDARY_SPACE);
        applyProperty(props, "preserveNamespaces", FeatureKeys.XQUERY_PRESERVE_NAMESPACES);
        applyProperty(props, "requiredContextItemType", FeatureKeys.XQUERY_REQUIRED_CONTEXT_ITEM_TYPE);
        applyProperty(props, "schemaAware", FeatureKeys.XQUERY_SCHEMA_AWARE);
        applyProperty(props, "staticErrorListener", FeatureKeys.XQUERY_STATIC_ERROR_LISTENER_CLASS);
        applyProperty(props, "version", FeatureKeys.XQUERY_VERSION);
    }

    private void readXsltElement(Attributes atts) {
        Properties props = new Properties();
        for (int i = 0; i < atts.getLength(); i++) {
            String name = atts.getLocalName(i);
            String value = atts.getValue(i);
            if (value.length() != 0 && atts.getURI(i).isEmpty()) {
                props.put(name, value);
            }
        }
        props.put("#element", "xslt");
        applyProperty(props, "disableXslEvaluate", FeatureKeys.DISABLE_XSL_EVALUATE);
        applyProperty(props, "enableAssertions", FeatureKeys.XSLT_ENABLE_ASSERTIONS);
        applyProperty(props, "initialMode", FeatureKeys.XSLT_INITIAL_MODE);
        applyProperty(props, "initialTemplate", FeatureKeys.XSLT_INITIAL_TEMPLATE);
        applyProperty(props, "messageEmitter", FeatureKeys.MESSAGE_EMITTER_CLASS);
        applyProperty(props, "outputUriResolver", FeatureKeys.OUTPUT_URI_RESOLVER_CLASS);
        applyProperty(props, "recoveryPolicy", FeatureKeys.RECOVERY_POLICY_NAME);
        applyProperty(props, "resultDocumentThreads", FeatureKeys.RESULT_DOCUMENT_THREADS);
        applyProperty(props, "schemaAware", FeatureKeys.XSLT_SCHEMA_AWARE);
        applyProperty(props, "staticErrorListener", FeatureKeys.XSLT_STATIC_ERROR_LISTENER_CLASS);
        applyProperty(props, "staticUriResolver", FeatureKeys.XSLT_STATIC_URI_RESOLVER_CLASS);
        applyProperty(props, "strictStreamability", FeatureKeys.STRICT_STREAMABILITY);
        applyProperty(props, "styleParser", FeatureKeys.STYLE_PARSER_CLASS);
        applyProperty(props, "version", FeatureKeys.XSLT_VERSION);
        applyProperty(props, "versionWarning", FeatureKeys.VERSION_WARNING);
    }

    private void readXsdElement(Attributes atts) {
        Properties props = new Properties();
        for (int i = 0; i < atts.getLength(); i++) {
            String name = atts.getLocalName(i);
            String value = atts.getValue(i);
            if (value.length() != 0 && atts.getURI(i).isEmpty()) {
                props.put(name, value);
            }
        }
        props.put("#element", "xsd");
        applyProperty(props, "assertionsCanSeeComments", FeatureKeys.ASSERTIONS_CAN_SEE_COMMENTS);
        applyProperty(props, "implicitSchemaImports", FeatureKeys.IMPLICIT_SCHEMA_IMPORTS);
        applyProperty(props, "multipleSchemaImports", FeatureKeys.MULTIPLE_SCHEMA_IMPORTS);
        applyProperty(props, "occurrenceLimits", FeatureKeys.OCCURRENCE_LIMITS);
        applyProperty(props, "schemaUriResolver", FeatureKeys.SCHEMA_URI_RESOLVER_CLASS);
        applyProperty(props, "thresholdForCompilingTypes", FeatureKeys.THRESHOLD_FOR_COMPILING_TYPES);
        applyProperty(props, "useXsiSchemaLocation", FeatureKeys.USE_XSI_SCHEMA_LOCATION);
        applyProperty(props, "version", FeatureKeys.XSD_VERSION);
    }

    private void error(String element, String attribute, String actual, String required) {
        XPathException err;
        if (attribute == null) {
            err = new XPathException("Invalid configuration element " + element);
        } else if (actual == null) {
            err = new XPathException("Missing configuration property " +
                                             element + "/@" + attribute);
        } else {
            err = new XPathException("Invalid configuration property " +
                                             element + "/@" + attribute + ". Supplied value '" + actual + "'. " + required);
        }
        err.setLocator(ExplicitLocation.makeFromSax(locator));
        errors.add(err);
    }

    protected void error(XPathException err) {
        err.setLocator(ExplicitLocation.makeFromSax(locator));
        errors.add(err);
    }

    protected void errorClass(String element, String attribute, String actual, Class required, Exception cause) {
        XPathException err = new XPathException("Invalid configuration property " +
                                                        element + (attribute == null ? "" : "/@" + attribute) +
                                                        ". Supplied value '" + actual +
                                                        "', required value is the name of a class that implements '" + required.getName() + "'", cause);
        err.setLocator(ExplicitLocation.makeFromSax(locator));
        errors.add(err);
    }

    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (level == 3 && "resources".equals(section)) {
            String content = buffer.toString();
            if (content.length() != 0) {
                if ("externalObjectModel".equals(localName)) {
                    try {
                        ExternalObjectModel model = (ExternalObjectModel) config.getInstance(content, null);
                        config.registerExternalObjectModel(model);
                    } catch (XPathException e) {
                        errorClass("externalObjectModel", null, content, ExternalObjectModel.class, e);
                    } catch (ClassCastException cce) {
                        errorClass("externalObjectModel", null, content, ExternalObjectModel.class, cce);
                    }
                } else if ("extensionFunction".equals(localName)) {
                    try {
                        ExtensionFunctionDefinition model = (ExtensionFunctionDefinition) config.getInstance(content, null);
                        config.registerExtensionFunction(model);
                    } catch (XPathException e) {
                        errorClass("extensionFunction", null, content, ExtensionFunctionDefinition.class, e);
                    } catch (ClassCastException cce) {
                        errorClass("extensionFunction", null, content, ExtensionFunctionDefinition.class, cce);
                    } catch (IllegalArgumentException iae) {
                        errorClass("extensionFunction", null, content, ExtensionFunctionDefinition.class, iae);
                    }
                } else if ("schemaDocument".equals(localName)) {
                    try {
                        Source source = getInputSource(content);
                        config.addSchemaSource(source);
                    } catch (SchemaException e) {
                        errors.add(XPathException.makeXPathException(e));
                    } catch (XPathException e) {
                        errors.add(e);
                    }
                } else if ("schemaComponentModel".equals(localName)) {
                    try {
                        Source source = getInputSource(content);
                        config.importComponents(source);
                    } catch (XPathException e) {
                        errors.add(e);
                    }
                } else if ("fileExtension".equals(localName)) {
                    // already done at startElement time
                } else {
                    error(localName, null, null, null);
                }
            }
        }
        level--;
        buffer.setLength(0);
        namespaceStack.pop();
    }

    private Source getInputSource(String href) throws XPathException {
        try {
            String base = locator.getSystemId();
            URI abs = ResolveURI.makeAbsolute(href, base);
            return new StreamSource(abs.toString());
        } catch (URISyntaxException e) {
            throw new XPathException(e);
        }
    }

    public void characters(char ch[], int start, int length) throws SAXException {
        buffer.append(ch, start, length);
    }

    public void ignorableWhitespace(char ch[], int start, int length) throws SAXException {

    }

    public void processingInstruction(String target, String data) throws SAXException {

    }

    public void skippedEntity(String name) throws SAXException {

    }

    /////////////////////////////////////////////////////
    // Implement NamespaceResolver
    /////////////////////////////////////////////////////

    /**
     * Get the namespace URI corresponding to a given prefix. Return null
     * if the prefix is not in scope.
     *
     * @param prefix     the namespace prefix. May be the zero-length string, indicating
     *                   that there is no prefix. This indicates either the default namespace or the
     *                   null namespace, depending on the value of useDefault.
     * @param useDefault true if the default namespace is to be used when the
     *                   prefix is "". If false, the method returns "" when the prefix is "".
     * @return the uri for the namespace, or null if the prefix is not in scope.
     * The "null namespace" is represented by the pseudo-URI "".
     */

    public String getURIForPrefix(String prefix, boolean useDefault) {
        for (int i = namespaceStack.size() - 1; i >= 0; i--) {
            List<String[]> list = namespaceStack.get(i);
            for (String[] pair : list) {
                if (pair[0].equals(prefix)) {
                    return pair[1];
                }
            }
        }
        return null;
    }

    /**
     * Get an iterator over all the prefixes declared in this namespace context. This will include
     * the default namespace (prefix="") and the XML namespace where appropriate
     */
    
    public Iterator<String> iteratePrefixes() {
        Set<String> prefixes = new HashSet<String>();
        for (List<String[]> list : namespaceStack) {
            for (String[] pair : list) {
                prefixes.add(pair[0]);
            }
        }
        return prefixes.iterator();
    }
}

