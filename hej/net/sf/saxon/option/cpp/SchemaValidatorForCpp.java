package net.sf.saxon.option.cpp;


import net.sf.saxon.Configuration;
import net.sf.saxon.Version;
import net.sf.saxon.event.Builder;
import net.sf.saxon.event.NamespaceReducer;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.StreamWriterToReceiver;
import net.sf.saxon.lib.Initializer;
import net.sf.saxon.s9api.*;
import net.sf.saxon.value.DateTimeValue;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.Source;
import javax.xml.transform.SourceLocator;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.StringReader;

/**
 * A <tt>SchemaValidator</tt> is an object that is used for validating instance documents against a schema.
 * This class is a wrapper for the SchemaValidator designed for Saxon/C.
 */
public class SchemaValidatorForCpp extends SaxonCAPI {

    private SchemaManager schemaManager = null;
    private Source source = null;
    private String xmlString = null;
    private Serializer serializer = null;
    private MyErrorListener listener = null;
    XMLStreamWriter streamWriter = null;
    private boolean reporting = false;
    private boolean verbose = false;


    /*
    *   Default constructor to create a Schema valditor based on the s9api. Assume license file is available.
     *   throw SaxonApiException if processor not licensed
    **/

    public SchemaValidatorForCpp() throws SaxonApiException {
        processor = new Processor(true);
        schemaManager = processor.getSchemaManager();

        if(debug && !processor.isSchemaAware()) {
            SaxonCException ex =   new SaxonCException("Processor is not licensed for schema processing!");
            saxonExceptions.add(ex);
            throw ex;
        }
    }

    /*
    *   Default constructor to create a Schema valditor based on the s9api. Assume license file is available.
    *   @param proc - Supplied processor. Assume that license feature is enabled
     *   throw SaxonApiException if processor not licensed
    **/
    public SchemaValidatorForCpp(Processor proc) throws SaxonApiException {
        processor = proc;
        schemaManager = processor.getSchemaManager();
        if(!processor.isSchemaAware()) {
            SaxonCException ex = new SaxonCException("Processor is not licensed for schema processing!");
            saxonExceptions.add(ex);
            throw  ex;
        }
    }

    /**
     * Set reporting feature on validator
     *
     * @param r true or false value
     */
    public void reporting(boolean r){
        reporting = r;
    }

    private void setValidationReportAsNode() throws SaxonApiException {
        DocumentBuilder builder = processor.newDocumentBuilder();
        streamWriter = builder.newBuildingStreamWriter();
        listener = new MyErrorListener(streamWriter);
        listener.setConfiguration(processor.getUnderlyingConfiguration());
    }

    /**
     * Set verbose mode to output to the terminal validation exceptions.
     * The default is on providing the reporting feature has not been enabled. In which case the user would have to switch this on
     * @param verbose
     */
    public void setVerbose(boolean verbose){
        this.verbose = verbose;
    }

    private void setValidationReportFileName(String reportFileName) throws SaxonApiException {

        Serializer destination = processor.newSerializer();
        destination.setOutputProperty(Serializer.Property.INDENT, "yes");
        destination.setOutputProperty(Serializer.Property.METHOD, "xml");
        destination.setOutputFile(new File(reportFileName));
        listener = new MyErrorListener(streamWriter);
        listener.setConfiguration(processor.getUnderlyingConfiguration());
        listener.setDestination(destination);


    }


    /**
     *  Internal use only
     * */
    private void setSource(Source s) {
        source = s;
    }


    public XdmNode getValidationReport() throws SaxonApiException {
        if(reporting && streamWriter != null) {
            return ((BuildingStreamWriterImpl) streamWriter).getDocumentNode();
        }
        return null;
    }


       /**
     * Error Listener to capture errors
    */
       public class MyErrorListener implements ErrorListener, Initializer {

           private XMLStreamWriter writer;
           private int warningCount = 0;
           private int errorCount = 0;
           private Configuration config = null;
           private Builder builder;
           private String xsdversion = "1.0";
           private Destination destination;
           private String systemId = null;
           private boolean verbose = false;    //TODO


           public MyErrorListener(XMLStreamWriter writer)  {
                this.writer = writer;
           }


           public MyErrorListener(Configuration config, Receiver receiver)  {
               if (receiver instanceof Builder) {
                   builder = (Builder) receiver;
               }
               Receiver r = new NamespaceReducer(receiver);
               writer = new StreamWriterToReceiver(r);
           }

            public MyErrorListener(Configuration config) {
                this.config = config;
            }

           public void setConfiguration(Configuration c){
               config = c;
           }


           public void setDestination(Destination destination) throws SaxonApiException {
               this.destination = destination;
               Receiver r = destination.getReceiver(getConfiguration());
               r = new NamespaceReducer(r);
               writer = new StreamWriterToReceiver(r);
           }

           public Configuration getConfiguration(){
               return config;
           }

           public int getErrorCount(){
               return errorCount;
           }

           public int getWarningCount(){
               return warningCount;
           }

           public void error(TransformerException exception) throws TransformerException {
               errorCount++;
               try {

                   writer.writeStartElement("error");
                   int lineNumber = -1;
                   int columnNumber = -1;
                   String fileName = "";
                   if(exception.getLocator() != null) {
                       SourceLocator locator = exception.getLocator();
                       lineNumber = locator.getLineNumber();
                       columnNumber = locator.getColumnNumber();
                       fileName =   locator.getSystemId();
                   }
                   writer.writeAttribute("line", String.valueOf(lineNumber));
                   writer.writeAttribute("position", String.valueOf(columnNumber));
                   writer.writeCharacters(exception.getMessage());
                   if(verbose){
                       System.err.println("Validation error on line "+lineNumber+" column "+columnNumber+" of "+fileName+":\n" +exception.getMessage());
                   }
                   writer.writeEndElement();
               } catch (XMLStreamException e) {
                   throw new TransformerException(e);
               }

           }

           public void fatalError(TransformerException exception) throws TransformerException {
               try {
                   writer.writeStartElement("fatal");
                   int lineNumber = -1;
                   int columnNumber = -1;
                   String fileName = "";
                   if(exception.getLocator() != null) {
                       SourceLocator locator = exception.getLocator();
                       lineNumber = locator.getLineNumber();
                       columnNumber = locator.getColumnNumber();
                       fileName =   locator.getSystemId();
                   }
                   writer.writeAttribute("line", String.valueOf(lineNumber));
                   writer.writeAttribute("position", String.valueOf(columnNumber));
                   writer.writeCharacters(exception.getMessage());
                   if(verbose){
                       System.err.println("Validation error on line "+lineNumber+" column "+columnNumber+" of "+fileName+":\n" +exception.getMessage());
                   }
                   writer.writeEndElement();
               } catch (XMLStreamException e) {
                   throw new TransformerException(e);
               }
           }

           public void warning(TransformerException exception) throws TransformerException {
               warningCount++;
               try {
                   writer.writeStartElement("warning");
                   int lineNumber = -1;
                   int columnNumber = -1;
                   String fileName = "";
                   if(exception.getLocator() != null) {
                       SourceLocator locator = exception.getLocator();
                       lineNumber = locator.getLineNumber();
                       columnNumber = locator.getColumnNumber();
                       fileName =   locator.getSystemId();

                   }
                   writer.writeAttribute("line", String.valueOf(lineNumber));
                   writer.writeAttribute("position", String.valueOf(columnNumber));
                   writer.writeCharacters(exception.getMessage());
                   if(verbose){
                       System.err.println("Validation error on line "+lineNumber+" column "+columnNumber+" of "+fileName+":\n" +exception.getMessage());
                   }
                   writer.writeEndElement();
               } catch (XMLStreamException e) {
                   throw new TransformerException(e);
               }
           }

           public void initialize(Configuration config) throws TransformerException {
               config.setErrorListener(this);
           }

           public void setXsdVersion(String version) {
                   xsdversion = version;
               }



           /**
            * Set the XML document that is to be validated
            *
            * @param id of the source document
            */
           public void setSystemId(String id) {
               systemId = id;
           }

           public void startReporting(String systemId) throws SaxonApiException {
                  this.systemId = systemId;

                      try {
                          setXsdVersion(config.getXsdVersion() == Configuration.XSD11 ? "1.1" : "1.0");
                          writer.writeStartDocument();
                          writer.setDefaultNamespace("http://saxon.sf.net/ns/validation");
                          writer.writeStartElement("http://saxon.sf.net/ns/validation", "validation-report");

                          if (systemId != null) {
                              writer.writeAttribute("system-id", systemId);
                          }
                      } catch (XMLStreamException e) {
                          throw new SaxonApiException(e);
                      }

              }



           public void endReporting() throws SaxonApiException {
                   createMetaData();
                   try {

                       writer.writeEndElement();//</validation-report>
                       writer.writeEndDocument();
                       writer.flush();
                       writer.close();
                       if (destination != null) {
                           destination.close();
                       }
                   } catch (XMLStreamException e) {
                       throw new SaxonApiException(e);
                   }


               }

           public void createMetaData() throws SaxonApiException {
                   try {
                       writer.writeStartElement("meta-data");
                       writer.writeStartElement("validator");
                       writer.writeAttribute("name", Version.getProductName() + "-" + getConfiguration().getEditionCode());
                       writer.writeAttribute("version", Version.getProductVersion());
                       writer.writeEndElement(); //</validator>
                       writer.writeStartElement("results");
                       writer.writeAttribute("errors", "" + errorCount);
                       writer.writeAttribute("warnings", "" + warningCount);
                       writer.writeEndElement(); //</results>
                       writer.writeStartElement("schema");
                       /* TODO if (schemaName != null) {
                           writer.writeAttribute("file", schemaName);
                       } */
                       writer.writeAttribute("xsd-version", xsdversion);
                       writer.writeEndElement(); //</schema>
                       writer.writeStartElement("run");
                       writer.writeAttribute("at", DateTimeValue.getCurrentDateTime(null).getStringValue());
                       writer.writeEndElement(); //</run>
                       writer.writeEndElement(); //</meta-data>
                   } catch (XMLStreamException ex) {
                       throw new SaxonApiException(ex);
                   }
               }


           public void setVerbose(boolean verbose) {
               this.verbose = verbose;
           }
       }



    /**
     * Register the Schema by file name.
     *
     * @param cwd    - Current Working directory
     * @param xsd    - File name of the schema relative to the cwd
     * @param params - parameters and property names given as an array of stings.
     *               We handle processor properties here
     * @param values - the values of the parameters and properties. given as a array of Java objects
     */
    public void registerSchema(String cwd, String xsd, String[] params, Object[] values) throws SaxonApiException {
        setProperties(params, values);

        if (xsd == null) {
            SaxonCException ex = new SaxonCException("Schema document not found");
            saxonExceptions.add(ex);
            throw ex;
        }
        Source source_xsd = resolveFileToSource(cwd, xsd);

        //validator.setErrorListener(errorListener);
        schemaManager.load(source_xsd);


    }

   /**
     * Register the Schema which is given as a string representation.
     *
     * @param cwd      - Current Working directory
     * @param xsd      - File name of the schema relative to the cwd
     * @param systemId - The system ID of the document
     * @param params   - parameters and property names given as an array of stings.
     *                 We handle processor properties here
     * @param values   - the values of the parameters and properties. given as a array of Java objects
     */
    public void registerSchemaString(String cwd, String xsd, String systemId, String[] params, Object[] values) throws SaxonApiException {
        setProperties(params, values);

        if (xsd == null) {
            SaxonCException ex = new SaxonCException("Schema document not found");
            saxonExceptions.add(ex);
            throw ex;
        }


        //validator.setErrorListener(errorListener);

        schemaManager.load(new StreamSource(new StringReader(xsd), systemId));


    }

    private void setProperties(String[] params, Object[] values) throws SaxonApiException {
        if (params != null && values != null) {
            for (int i = 0; i < params.length; i++) {
                if (params[i].startsWith("http://saxon.sf.net/feature")) {
                    if (debug) {
                        System.err.println("java: parameter name:" + params[i]);
                        System.err.println("java: parameter length:" + params[i].length());
                    }
                    String name = params[i];
                    String value = (String) values[i];
                    processor.setConfigurationProperty(name, value);
                } else if(params[i].equals("xsdversion")){
                        if(values[i] instanceof String) {
                            String xsdversion = (String)values[i];
                            getSchemaManager().setXsdVersion(xsdversion);
                        } else {
                            SaxonCException ex = new SaxonCException("XSD version has not been correctly set");
                            saxonExceptions.add(ex);
                            throw ex;
                        }
                }
            }
        }
    }



    /**
     * Validate an instance document supplied as a Source object
     * @param cwd  - Current working directory
     * @param sourceFilename  - The name of the file to be validated
     * @param outfilename  - The name of the file where output from the validator will be sent. Can be null.
     * @param params - Parameters and properties names required by the Validator. This could contain the source as a node , source as string or file name, validator options, etc
     * @param values -  The values for the parameters and properties required by the Validator
     *
     **/
    public void validate(String cwd, String sourceFilename, String outfilename, String[] params, Object[] values) throws SaxonApiException {
        source = null; //This is required to make sure the source object created from a previous call is not used
        SchemaValidator validator = null;
        if(!processor.isSchemaAware()) {
            SaxonCException ex = new SaxonCException("Processor is not licensed for schema processing!");
            saxonExceptions.add(ex);
            throw ex;
        }
        reporting = false;
        verbose = false;

        /*if (xsd == null && validator == null && sourceFilename == null) {
            throw new SaxonApiException("Schema document not found");
        } */

//        if (xsd != null && validator == null) {
//            Source source_xsd = resolveFileToSource(cwd, xsd);
//
//            schemaManager.load(source_xsd);
//            validator = schemaManager.newSchemaValidator();
//            validator.setErrorListener(errorListener);
//        }

        validator = schemaManager.newSchemaValidator();


        if (outfilename != null) {
            serializer = resolveOutputFile(processor, cwd, outfilename);

            serializer.setOutputProperty(Serializer.Property.INDENT, "yes");
            serializer.setOutputProperty(Serializer.Property.OMIT_XML_DECLARATION, "yes");
            validator.setDestination(serializer);
        }
        applySchemaProperties(cwd, processor, this, validator, params, values);



        if(source == null && sourceFilename != null && !sourceFilename.isEmpty()) {
            source = resolveFileToSource(cwd, sourceFilename);
        }
        if(source == null && xmlString != null) {
            source = parseXmlString(null, xmlString).asSource();
        }
        if (source != null) {
            if(reporting) {
                listener.setVerbose(verbose);
                 validator.setErrorListener(listener);
                 listener.startReporting(source.getSystemId());
             }
            validator.validate(source);
            if(reporting) {
                listener.endReporting();
            }
        } else {
            SaxonCException ex = new SaxonCException("Source document not found");
            saxonExceptions.add(ex);
            throw ex;
        }

    }

    /**
     * Validate an instance document supplied as a Source object with the validated document returned to the calling program
     *
     * @param cwd  - Current working directory
     * @param sourceFilename  - The name of the file to be validated
     * @param params - Parameters and properties names required by the Validator. This could contain the source as a node , source as string or file name, validator options, etc
     * @param values -  The values for the parameters and properties required by the Validator
     * @return XdmNode
     *
     **/
    public XdmNode validateToNode(String cwd, String sourceFilename, String[] params, Object[] values) throws SaxonApiException {

        source = null; //This is required to make sure the source object created from a previous call is not used
        SchemaValidator validator = schemaManager.newSchemaValidator();

        reporting = false;
        verbose = false;

//        if (xsd != null && validator == null) {
//            Source source_xsd = resolveFileToSource(cwd, xsd);
//            validator.setErrorListener(errorListener);
//            schemaManager.load(source_xsd);
//            validator = schemaManager.newSchemaValidator();
//        } else {
//            validator = schemaManager.newSchemaValidator();
//        }

        if(sourceFilename != null) {
            return parseXmlFile(cwd, validator, sourceFilename);
        }
        applySchemaProperties(cwd, processor, this, validator, params, values);


        if(source != null) {
            if(reporting) {
                listener.setVerbose(verbose);
                validator.setErrorListener(listener);
                listener.startReporting(source.getSystemId());
            }
            XdmDestination destination = new XdmDestination();
            validator.setDestination(destination);
            validator.validate(source);
            if(reporting) {
                listener.endReporting();
            }
            return destination.getXdmNode();
        }

        if(xmlString != null) {
            return parseXmlString(validator, xmlString);
        }
        return null;
    }


    /**
     * Applies the properties and parameters required in the transformation.
     * In addition we can supply the source, stylesheet and output file names.
     * We can also supply values to xsl:param and xsl:variables required in the stylesheet.
     * The parameter names and values are supplied as a two arrays in the form of a key and value.
     *
     * @param cwd       - current working directory
     * @param processor - required to use the same processor as for the compiled stylesheet
     * @param thisClass - pass the current object to set local variables supplied in the parameters
     * @param validator
     * @param params    - parameters and property names given as an array of stings
     * @param values    - the values of the parameters and properties. given as a array of Java objects
     */
    public static void applySchemaProperties(String cwd, Processor processor, SchemaValidatorForCpp thisClass, SchemaValidator validator, String[] params, Object[] values) throws SaxonApiException {
        if (params != null) {
            String initialTemplate;
            String initialMode;
            XdmNode node;
            String outfile = null;

            DocumentBuilder builder = processor.newDocumentBuilder();

            if (params.length != values.length) {
                throw new SaxonApiException("Length of params array not equal to the length of values array");
            }
            if (params.length != 0) {
                if (cwd != null && cwd.length() > 0) {
                    if (!cwd.endsWith("/")) {
                        cwd = cwd.concat("/");
                    }
                }
                for (int i = 0; i < params.length; i++) {
                    if (debug) {
                        System.err.println("parameter name:" + params[i]);
                        System.err.println("parameter length:" + params[i].length());
                    }
                    if (params[i].equals("lax")) {
                        validator.setLax(((Boolean) values[i]).booleanValue());
                    } else if (params[i].equals("element-name")) {
                        String paramName = (String) values[i];
                        QName qname = QName.fromClarkName(paramName);
                        validator.setDocumentElementName(qname);
                    } else if (params[i].equals("element-type")) {
                        String paramName = (String) values[i];
                        QName qname = QName.fromClarkName(paramName);
                        validator.setDocumentElementTypeName(qname);
                    } else if (params[i].equals("report-file")) {
                        thisClass.reporting(true);
                        if (values[i] != null && values[i] instanceof String) {
                            thisClass.setValidationReportFileName((String) values[i]);
                        }

                    } else if (params[i].equals("verbose")) {
                        if (values[i] != null && values[i] instanceof String) {
                            thisClass.setVerbose(Boolean.parseBoolean((String)values[i]));
                        }

                    } else if (params[i].equals("report-node")) {
                            thisClass.reporting(true);
                            thisClass.setValidationReportAsNode();
                    } else if (params[i].equals("o") && outfile == null) {
                        if (values[i] instanceof String) {
                            outfile = (String) values[i];
                            thisClass.serializer = thisClass.resolveOutputFile(processor, cwd, outfile);

                            thisClass.serializer.setOutputProperty(Serializer.Property.INDENT, "yes");
                            thisClass.serializer.setOutputProperty(Serializer.Property.OMIT_XML_DECLARATION, "yes");
                            validator.setDestination(thisClass.serializer);
                        }
                    } else if (params[i].equals("s")) {
                        if (values[i] instanceof String) {
                            thisClass.setSource(thisClass.resolveFileToSource(cwd, (String) values[i]));
                        } else if (debug) {
                            System.err.println("DEBUG: value error for property 's'");
                        }
                    } else if (params[i].equals("item") || params[i].equals("node") || params[i].equals("param:node")) {
                        if (debug) {
                            System.err.println("DEBUG: is null value=" + (values[i] == null));
                            if (values[i] != null) {
                                System.err.println("DEBUG: Type of value=" + (values[i]).getClass().getName());

                            }
                            System.err.println("DEBUG: setting the source for node");
                            System.err.println("DEBUG: is value a XdmNode=" + (values[i] instanceof XdmNode));
                            System.err.println("DEBUG: is value a XdmValue=" + (values[i] instanceof XdmValue));

                        }
                        Object value = values[i];
                        if (value instanceof XdmNode) {
                            node = (XdmNode) value;
                            thisClass.setSource((node).asSource());
                        } else if (debug) {
                            System.err.println("Type of node Property error.");
                        }
                    } else if (params[i].equals("resources")) {
                        char separatorChar = '/';
                        if (SaxonCAPI.RESOURCES_DIR == null && values[i] instanceof String) {
                            String dir1 = (String) values[i];
                            if (!dir1.endsWith("/")) {
                                dir1 = dir1.concat("/");
                            }
                            if (File.separatorChar != '/') {
                                dir1.replace(separatorChar, File.separatorChar);
                                separatorChar = '\\';
                                dir1.replace('/', '\\');
                            }
                            SaxonCAPI.RESOURCES_DIR = dir1;

                        }

                    } else if (params[i].equals("string")) {
                        thisClass.setsourceAsString((String) values[i]);
                    }

             }

                }
            }

        }

    private void setsourceAsString(String value) {
        xmlString = value;

    }

  public static void testValidator3(SchemaValidatorForCpp val) throws SaxonApiException {
        String cwd = "/Users/ond1/work/development/svn/saxon-dev/src/c/Saxon.C.Api/cppTests/";
 System.out.println("Test 3: Validate Schema from string");
  String sch1 = "<?xml version='1.0' encoding='UTF-8'?><schema targetNamespace='http://myexample/family' " +
          "xmlns:fam='http://myexample/family' xmlns='http://www.w3.org/2001/XMLSchema'><element name='FamilyMember'" +
          " type='string' /><element name='Parent' type='string' substitutionGroup='fam:FamilyMember'/>" +
          "<element name='Child' type='string' substitutionGroup='fam:FamilyMember'/><element name='Family'><" +
          "complexType><sequence><element ref='fam:FamilyMember' maxOccurs='unbounded'/></sequence></complexType>" +
          "</element>  </schema>";

val.registerSchemaString(cwd, sch1, "file///o", null, null);

	val.validate(cwd, "family.xml",  null, null, null);

}




    public static void main(String[] args) throws SaxonApiException {
        Processor processor = new Processor(true);
        SchemaValidatorForCpp validatorForCpp = new SchemaValidatorForCpp(processor);

        XdmValue item = SaxonCAPI.createXdmAtomicItem("QName", "{http://myDomain.co.uk/namespaces/ns1}myElement");
        validatorForCpp.getProcessor().setConfigurationProperty("http://saxon.sf.net/feature/validation-warnings", "true");
        validatorForCpp.getProcessor().setConfigurationProperty("http://saxon.sf.net/feature/xsd-version", "1.1");
        String cwd = "/Users/ond1/work/development/svn/saxon-dev/tests/junit/testdata/";
        Serializer serializer = processor.newSerializer();
        XdmNode resultNode = null;
        String resultStr = null;
        String[] paramsx = {"report-node", "verbose"};
        Object[] valuesx = {"true", "false"};
        validatorForCpp.registerSchema("/Users/ond1/work/development/files/millicom/LineNumber", "schema.xsd", null, null);
        validatorForCpp.validate("/Users/ond1/work/development/files/millicom/LineNumber", "example1.xml", null, paramsx, valuesx);
        resultNode = validatorForCpp.getValidationReport();

        if(resultNode != null) {
            resultStr = serializer.serializeNodeToString(resultNode);
            System.err.println("Validation Report-Millicom:" + resultStr);
        }



        System.out.println("\n\n Testing family.xml\n");
        validatorForCpp.registerSchema("/Users/ond1", "family-ext.xsd", null, null);
        validatorForCpp.registerSchema("/Users/ond1", "family.xsd", null, null);
        System.err.println("=============");
        validatorForCpp.validate("/Users/ond1/", "family.xml", null, paramsx, valuesx);
        XdmNode resultNode2 = validatorForCpp.getValidationReport();

        if(resultNode2 != null) {
            String resultStr2 = serializer.serializeNodeToString(resultNode2);
            System.err.println("Validation Report2:" + resultStr2);
        }
        System.err.println("=============");
         String invalid_xml = "<?xml version='1.0'?><request><a/><!--comment--></request>";
        String sch1 = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" elementFormDefault=\"qualified\"" +
                " attributeFormDefault=\"unqualified\">\n" +
                "\t<xs:element name=\"request\">\n" +
                "\t\t<xs:complexType>\n" +
                "\t\t\t<xs:sequence>\n" +
                "\t\t\t\t<xs:element name=\"a\" type=\"xs:string\"/>\n" +
                "\t\t\t\t<xs:element name=\"b\" type=\"xs:string\"/>\n" +
                "\t\t\t</xs:sequence>\n" +
                "\t\t\t<xs:assert test='count(child::node()) = 3'/>\n" +
                "\t\t</xs:complexType>\n" +
                "\t</xs:element>\n" +
                "</xs:schema>";


        String doc1 = "<request xmlns=\"http://myexample/family\">\n" +
                "  <Parent>John</Parent>\n" +
                "  <Child>Alice</Child>\n" +
                "</request>";
        String[] paramsSch = {"xsdversion"};
        Object[] valuesSch = {"1.1"};
        XdmNode sourceNode = validatorForCpp.parseXmlString(invalid_xml);
        String[] params = {"node", "xsdversion", "report-file"};
        Object[] values = {sourceNode, "1.1", "validationReport3.xml"};
        validatorForCpp.registerSchemaString(cwd, sch1, "file///o", paramsSch, valuesSch);

        validatorForCpp.validate(cwd, null, null, params, values);

        XdmNode sourceNode2 = validatorForCpp.parseXmlString(doc1);
        String[] params1 = {"string", "xsd-string", "xsdversion"};
        Object[] values1 = {doc1, sch1, "1.1"};

        System.err.println("Test 3: ");
        testValidator3(validatorForCpp);

        System.err.println("Test 5: ");
        validatorForCpp.registerSchemaString(cwd, sch1, "file///o1", paramsSch, valuesSch);
        //XdmNode resultDoc = validatorForCpp.validateToNode(cwd, null, null, null, params1, values1);

        validatorForCpp.validate(cwd, null, null, params1, values1);
        XdmNode outputNode2 = validatorForCpp.getValidationReport();

        XdmNode resultNode3 = validatorForCpp.getValidationReport();

        if(resultNode3 != null){
            resultStr = serializer.serializeNodeToString(resultNode);
            System.err.println("Validation Report3:"+resultStr);

        }


    }

}
