////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2014 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.cpp;


import com.saxonica.functions.extfn.cpp.NativeCall;
import net.sf.saxon.Configuration;
import net.sf.saxon.lib.*;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.s9api.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.BuiltInType;
import net.sf.saxon.type.Converter;
import net.sf.saxon.type.ValidationException;
import net.sf.saxon.value.*;
import org.xml.sax.InputSource;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;


/**
 * This class holds common attributes and methods required in the XsltProcessor, XQueryEngine and XPathProcessor
 */

public class SaxonCAPI {
    protected Properties props = null;
    protected Processor processor = null;
    protected XdmNode doc = null;
    protected List<SaxonCException> saxonExceptions = new ArrayList<SaxonCException>();
    protected List<SaxonCException> saxonWarnings = new ArrayList<SaxonCException>();
    public static boolean debug = false;
    protected Serializer serializer = null;
    protected InputStream in = null;
    protected boolean schemaAware = false;
    protected Source source = null;

    public static String RESOURCES_DIR = null;


    /**
     * Default Constructor. Creates a processor that does not require a license edition
     */
    public SaxonCAPI() {
        processor = new Processor(false);

        if (debug) {
            System.err.println("New processor created in SAxonCAPI(), Processor: " + System.identityHashCode(processor));
        }

    }

    /**
     * Constructor with license edition flag
     *
     * @param license - specify license edition flag
     */
    public SaxonCAPI(boolean license) {
        processor = new Processor(license);

        if (debug) {
            System.err.println("New processor created in SAxonCAPI(l), Processor: " + System.identityHashCode(processor));
        }
    }

    /**
     * Constructor
     *
     * @param proc    - specify processor object
     */
    public SaxonCAPI(Processor proc) {
        if (proc != null) {
            processor = proc;

            if (debug) {
                System.err.println("New processor created, Processor: " + System.identityHashCode(processor));
            }
        }
    }

    public static void setLibrary(String cwd, String libName){
//#if EE==true || PE==true
        NativeCall.loadLibrary(cwd, libName);
//#endif
    }


    /**
     * Static method to create Processor object given a configuration file
     * @param  configFile
     */
    public static Processor createSaxonProcessor(String configFile) throws SaxonApiException {
        Configuration config = null;
        try {
            config = Configuration.readConfiguration((new StreamSource(configFile)));

        } catch (XPathException e) {
            throw new SaxonApiException(e);
        }


        if (config == null) {
            config = Configuration.newConfiguration();
        }

        //config.setHostLanguage(Configuration.XQUERY);
        return new Processor(config);

    }


    /**
     * Error Listener to capture errors
     */
    protected ErrorListener errorListener = new StandardErrorListener() {

        @Override
        public void warning(TransformerException exception) {
            saxonWarnings.add(new SaxonCException(exception));

            try {
                super.error(exception);
            } catch (Exception ex) {
            }

        }

        @Override
        public void error(TransformerException exception) {
            if (Configuration.RECOVER_WITH_WARNINGS == Configuration.RECOVER_SILENTLY && !(exception instanceof ValidationException)) {
                // do nothing
                return;
            }
            saxonExceptions.add(new SaxonCException(exception));
            try {
                super.error(exception);
            } catch (Exception ex) {
            }
        }

        @Override
        public void fatalError(TransformerException exception) {
            if (exception instanceof XPathException && ((XPathException) exception).hasBeenReported()) {
                       // don't report the same error twice
                       return;
            }
            saxonExceptions.add(new SaxonCException(exception));
            try {
                super.fatalError(exception);
            } catch (Exception ex) {
            }
        }
    };

    public static String getProductVersion(Processor processor){
        return processor.getUnderlyingConfiguration().getProductTitle();
    }

    /**
     * Get the Schema manager
     *
     * @return SchemaManager
     */
    public SchemaManager getSchemaManager() {
        return processor.getSchemaManager();
    }

    /**
     * Get the Processor object created
     *
     * @return Processor
     */
    public Processor getProcessor() {
        return processor;
    }



    public static void applyToConfiguration(Processor processor, String[] names, String[] values) throws SaxonApiException {


        Configuration config = processor.getUnderlyingConfiguration();
        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            String value = values[i];

            if (name.equals("l")) {
                if (value != null) {
                    processor.setConfigurationProperty(FeatureKeys.LINE_NUMBERING,
                            "on".equals(value));
                }
            } else if (name.equals("dtd")) {
                if ("on".equals(value)) {
                    config.getParseOptions().setDTDValidationMode(Validation.STRICT);
                } else if ("off".equals(value)) {
                    config.getParseOptions().setDTDValidationMode(Validation.SKIP);
                } else if ("recover".equals(value)) {
                    config.getParseOptions().setDTDValidationMode(Validation.LAX);
                }

            } else if (name.equals("expand")) {
                config.getParseOptions().setExpandAttributeDefaults("on".equals(value));
            } else if (name.equals("opt")) {
                if (value != null) {
                    config.setConfigurationProperty(FeatureKeys.OPTIMIZATION_LEVEL, value);
                }

            } else if (name.equals("outval")) {

                Boolean isRecover = "recover".equals(value);
                config.setConfigurationProperty(FeatureKeys.VALIDATION_WARNINGS, isRecover);
                config.setConfigurationProperty(FeatureKeys.VALIDATION_COMMENTS, isRecover);


            } else if (name.equals("strip")) {

                config.setConfigurationProperty(FeatureKeys.STRIP_WHITESPACE, value);
            } else if (name.equals("val")) {
                if ("strict".equals(value)) {
                    processor.setConfigurationProperty(FeatureKeys.SCHEMA_VALIDATION, Validation.STRICT);
                } else if ("lax".equals(value)) {
                    processor.setConfigurationProperty(FeatureKeys.SCHEMA_VALIDATION, Validation.LAX);
                }

            } else if (name.equals("xsdversion")) {
                processor.setConfigurationProperty(FeatureKeys.XSD_VERSION, value);

            } else if (name.equals("xmlversion")) {

                processor.setConfigurationProperty(FeatureKeys.XML_VERSION, value);

            } else if (name.equals("xi")) {

                processor.setConfigurationProperty(FeatureKeys.XINCLUDE, "on".equals(value));

            } else if (name.equals("xsiloc")) {
                processor.setConfigurationProperty(FeatureKeys.USE_XSI_SCHEMA_LOCATION, "on".equals(value));
            } else if (name.startsWith("http://saxon.sf.net/feature/") && value != null) {

                try {
                    processor.setConfigurationProperty(name, value);
                } catch (IllegalArgumentException err) {
                    throw new SaxonApiException(err.getMessage());
                }
            }
        }



    }


    /**
     * set debug mode on or off
     *
     * @param d - flag for debug mode
     */
    public static void setDebugMode(boolean d) {
        debug = d;
    }

    /**
     * get the input stream
     *
     * @return InputStream - created input stream
     */
    public InputStream getInputStream() {
        return in;
    }


    /**
     * Get the exceptions thrown during the compile and execution of the XSLT/XQuery
     *
     * @return SaxCEExeption[] -- array of the exceptions
     */
    public SaxonCException[] getExceptions() {
        if (saxonExceptions.size() > 0) {
            return saxonExceptions.toArray(new SaxonCException[saxonExceptions.size()]);
        } else
            return null;
    }


    /**
     * Get the exceptions thrown during the compile and execution of the XSLT/XQuery
     *
     * @return SaxCEExeption[] -- array of the exceptions
     */
    public SaxonCException[] getWarnings() {
        if (saxonWarnings.size() > 0) {
            return saxonWarnings.toArray(new SaxonCException[saxonWarnings.size()]);
        } else
            return null;
    }


    /**
     * Check for exceptions thrown
     *
     * @return boolean - Return true if exception thrown during the process and false otherwise.
     */
    public boolean checkException() {
        return saxonExceptions.size() > 0;
    }

    /**
     * Clear exceptions recorded during the process
     */
    public void clearExceptions() {
        saxonWarnings.clear();
        saxonExceptions.clear();
    }

    /**
     * Get a particular exceptions
     *
     * @param i - index into the list of thrown exceptions
     * @return SaxonCException - Saxon/C wrapped exception
     */
    public SaxonApiException getException(int i) {
        if (i < saxonExceptions.size()) {
            return saxonExceptions.get(i);
        } else {
            return null;
        }
    }


    /**
     * parse XML document supplied by file
     *
     * @param cwd      - Current Working directory
     * @param filename - File name of the XML document to parse
     * @return XdmNode
     */
    public XdmNode parseXmlFile(String cwd, String filename) throws SaxonApiException {
        try {
            doc = parseXmlFile(processor, cwd, null, filename);
            return doc;
        } catch (SaxonApiException ex) {
            saxonExceptions.add(new SaxonCException(ex.getCause()));
            throw ex;
        }
    }


    /**
     * parse XML document with addition parameters. Document supplied by file name.
     *
     * @param cwd       - Current Working directory
     * @param validator - Supplied Schema validator
     * @param filename  - File name of the XML document to parse
     * @return XdmNode
     */
    public XdmNode parseXmlFile(String cwd, SchemaValidator validator, String filename) throws SaxonApiException {
        try {
            doc = parseXmlFile(processor, cwd, validator, filename);
            return doc;
        } catch (SaxonApiException ex) {
            saxonExceptions.add(new SaxonCException(ex.getCause()));
            throw ex;
        }
    }


    /**
     * parse XML document supplied string
     *
     * @param xml - string representation of XML document
     * @return XdmNode
     */
    public XdmNode parseXmlString(String xml) throws SaxonApiException {
        return parseXmlString(null, xml);
    }

    /**
     * parse XML document supplied string
     *
     * @param xml       - string representation of XML document
     * @param validator - Supplied Schema validator
     * @return XdmNode
     */
    public XdmNode parseXmlString(SchemaValidator validator, String xml) throws SaxonApiException {
        try {
            doc = parseXmlString(processor, validator, xml);
            if (debug) {
                System.err.println("xmlParseString, Processor: " + System.identityHashCode(processor));
            }
            return doc;
        } catch (SaxonApiException ex) {
            saxonExceptions.add(new SaxonCException(ex.getCause()));
            throw ex;
        }
    }


    /**
     * Create an Xdm atomic value from string representation
     *
     * @param typeStr  - Local name of a type in the XML Schema namespace.
     * @param valueStr - The value given in a string form.
     *                 In the case of a QName the value supplied must be in clark notation. {uri}local
     * @return XdmValue - value
     */
    public static XdmValue createXdmAtomicItem(String typeStr, String valueStr) throws SaxonApiException {

        int fp = StandardNames.getFingerprint(NamespaceConstant.SCHEMA, typeStr);

        BuiltInAtomicType type = (BuiltInAtomicType) BuiltInType.getSchemaType(fp);
        if (type == null) {
            throw new SaxonApiException("Unknown built in type: " + typeStr + " not found");
        }

        if (type.isNamespaceSensitive()) {
            StructuredQName value = StructuredQName.fromClarkName(valueStr);
            return XdmValue.wrap(new QNameValue(value, type));
        }

        ConversionRules rules = new ConversionRules();
        Converter converter = rules.getConverter(BuiltInAtomicType.STRING, type);


        try {
            return XdmValue.wrap(converter.convert(new StringValue(valueStr)).asAtomic());
        } catch (ValidationException e) {
            throw new SaxonApiException(e);
        }


    }

    /**
     * Wrap a boxed primitive type as an XdmValue.
     *
     * @param value - boxed primitive type
     * @return XdmValue
     */
    public static XdmValue getXdmValue(Object value) {
        XdmValue valueForCpp = null;
        if (value instanceof Integer) {
            valueForCpp = XdmValue.wrap(new Int64Value(((Integer) value).intValue()));
        } else if (value instanceof Boolean) {
            valueForCpp = XdmValue.wrap(BooleanValue.get(((Boolean) value).booleanValue()));
        } else if (value instanceof Double) {
            valueForCpp = XdmValue.wrap(DoubleValue.makeDoubleValue(((Double) value).doubleValue()));
        } else if (value instanceof Float) {
            valueForCpp = XdmValue.wrap(FloatValue.makeFloatValue(((Float) value).floatValue()));
        } else if (value instanceof Long) {
            valueForCpp = XdmValue.wrap(Int64Value.makeIntegerValue((((Long) value).longValue())));
        } else if (value instanceof String) {
            valueForCpp = XdmValue.wrap(StringValue.makeStringValue(((String) value)));
        }
        return valueForCpp;
    }

    // This method used to be used internally
    public static XdmNode parseXmlString(Processor processor, SchemaValidator validator, String xml) throws SaxonApiException {
        try {
            StringReader reader = new StringReader(xml);
            DocumentBuilder builder = processor.newDocumentBuilder();
            if (validator != null) {
                builder.setSchemaValidator(validator);
            }
            XdmNode doc = builder.build(new SAXSource(new InputSource(reader)));
            if (debug) {
                System.err.println("xmlParseString, Processor: " + System.identityHashCode(processor));
                System.err.println("XdmNode: " + System.identityHashCode(doc));
                net.sf.saxon.type.ItemType suppliedItemType = SequenceTool.getItemType(doc.getUnderlyingValue(), processor.getUnderlyingConfiguration().getTypeHierarchy());
                System.err.println("xmlParserString, ItemType: " + suppliedItemType.toString());
            }
            return doc;
        } catch (SaxonApiException ex) {
            throw ex;
        }
    }

    public static String getTypeName(String typeStr) {

        int fp = StandardNames.getFingerprint(NamespaceConstant.SCHEMA, typeStr);

        BuiltInAtomicType typei = (BuiltInAtomicType) BuiltInType.getSchemaType(fp);
        StructuredQName sname = typei.getTypeName();
        return new QName(sname.getPrefix(), sname.getURI(), sname.getLocalPart()).getClarkName();

    }

    public static String getStringValue(XdmValue value) {
        return value.toString();
    }


    public static XdmNode parseXmlFile(Processor processor, String cwd, SchemaValidator validator, String filename) throws SaxonApiException {
        try {
            DocumentBuilder builder = processor.newDocumentBuilder();
            Source source = null;
            if (cwd != null && cwd.length() > 0) {
                if (!cwd.endsWith("/")) {
                    cwd = cwd.concat("/");
                }
                File file = new File(cwd, filename);
                source = new StreamSource(file);
                source.setSystemId(file.getAbsolutePath());
            } else {
                source = new StreamSource(new File(filename));
            }
            if (validator != null) {
                builder.setSchemaValidator(validator);
            }
            return builder.build(source);
        } catch (SaxonApiException ex) {
            throw ex;
        }
    }

    /**
     * Create a File object given the filename and the cwd used fix-up the location of the file.
     *
     * @param cwd      - Supply the current working directory which the filename will be resolved against
     * @param filename
     * @return file object
     */
    public File absoluteFile(String cwd, String filename) {
        char separatorChar = '/';
        if (File.separatorChar != '/') {
            filename.replace(separatorChar, File.separatorChar);
            cwd.replace(separatorChar, File.separatorChar);
            separatorChar = '\\';
        }
        if (!cwd.endsWith(String.valueOf(separatorChar))) {
            cwd = cwd.concat(String.valueOf(separatorChar));
        }
        File absFile = new File(filename);
        String fullpath;
        if (!absFile.isAbsolute()) {
            fullpath = cwd + filename;
        } else {
            fullpath = filename;
        }
        return new File(fullpath);
    }

    /**
     * Resolve the file with the cwd
     * deprecated method
     *
     * @param cwd      - Current working directory
     * @param filename -
     * @return File object
     * @throws SaxonApiException
     */
    public File resolveFile(String cwd, String filename) throws SaxonApiException {
        URI cwdURI = null;
        File file = null;
        if (cwd != null && cwd.length() > 0) {

            try {

                file = absoluteFile(cwd, filename);

            } catch (Exception e) {
                try {
                    cwdURI = new URI(cwd + filename);
                    file = new File(cwdURI);
                } catch (URISyntaxException e1) {
                    throw new SaxonApiException(e1);
                }

            }

            return file;
        } else {
            return new File(filename);
        }
    }


    /**
     * Resolve file name. Returns the file as a Source object
     *
     * @param cwd      - Current working directory
     * @param filename
     * @return Source
     * @throws SaxonApiException
     */
    public Source resolveFileToSource(String cwd, String filename) throws SaxonApiException {

        Source source = null;
        File file = null;

        if (cwd != null && cwd.length() > 0) {
            if (!cwd.endsWith("/")) {
                cwd = cwd.concat("/");
            }

            try {
                if (filename.startsWith("http")) {
                    URI fileURI = null;
                    fileURI = new URI(filename);
                    URL url = fileURI.toURL();
                    InputStream in = url.openStream();
                    source = new StreamSource(in);
                    source.setSystemId(filename);

                } else {

                    file = absoluteFile(cwd, filename);
                    source = new StreamSource(file);
                    source.setSystemId(file.getAbsolutePath());
                }
            } catch (Exception e) {
                URL url = null;
                try {
                    URI fileURI = null;
                    fileURI = new URI(filename);
                    url = fileURI.toURL();
                    InputStream in = url.openStream();
                    source = new StreamSource(in);
                    source.setSystemId(filename);
                } catch (MalformedURLException e1) {
                    throw new SaxonApiException(e);
                } catch (IOException e1) {
                    throw new SaxonApiException(e);
                } catch (URISyntaxException e1) {
                    throw new SaxonApiException(e1);
                }

            }


            return source;
        } else {
            file = new File(filename);
            StreamSource stream = new StreamSource(file);
            stream.setSystemId(filename);
            source = stream;
            return source;
        }
    }

    /**
     * Resolve the output file and wrap it into a Serializer for use in XQuery and XSLT processors
     *
     * @param processor - The same processor used in XQuery or XSLT engine must be used. Otherwise errors might occur
     * @param cwd       - Current working directory
     * @param outfile   - the output filename where result will be stored
     * @return Serializer
     * @throws SaxonApiException
     */
    public Serializer resolveOutputFile(Processor processor, String cwd, String outfile) throws SaxonApiException {
        Serializer serializer = null;
        File file = absoluteFile(cwd, outfile);

        serializer = processor.newSerializer(file);
        return serializer;
    }

}
