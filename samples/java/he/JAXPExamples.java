package he;
import net.sf.saxon.jaxp.TransformerImpl;
import net.sf.saxon.tree.util.Orphan;
import net.sf.saxon.type.Type;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.*;
import org.xml.sax.helpers.XMLFilterImpl;

import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.net.URL;
import java.util.Properties;


/**
 * Some examples to show how the JAXP API can be used.
 * <p/>
 * <p>JAXP provides interfaces for XSLT transformation, XPath processing, and
 * XSD schema processing. These examples are primarily concerned with
 * XSLT transformation.</p>
 * <p/>
 * <p>JAXP was designed to support XSLT 1.0 and XPath 1.0, and has not
 * moved forward to newer versions. It therefore does not give access to
 * all the capabilities in the Saxon product. It is useful for applications
 * where portability is important, but the preferred Java API for use with
 * Saxon is now the s9api interface. Since Saxon 9.6, JAXP interfaces are
 * implemented as a layer above the s9api interface.</p>
 * <p/>
 * <p>JAXP leaves some details implementation-defined, which means that
 * Saxon is not always 100% compatible with other implementations. (Also,
 * there is no published compatibility test suite). This means that the
 * JAXP factory mechanism, which allows application code to run with
 * different XSLT or XPath engines depending on what it finds on the classpath,
 * can be dangerous, because it allows the application to be executed with
 * an XSLT or XPath engine that has not been tested. Furthermore, the classpath
 * search mechanism is notoriously slow. Loading a specific implementation,
 * or at least checking that the implementation that has been loaded is
 * a known and supported one, is therefore recommended.</p>
 * <p/>
 * <p>Since 9.6 the Saxon JAR files do not include the data in the manifest
 * that causes them to be loaded in response to a JAXP search for an XPath
 * engine. This is because applications using such a search are very often
 * unprepared to get an XPath 2.0 engine in response. Saxon continues to
 * support JAXP interfaces to XPath, but the factory must be explicitly
 * instantiated. For the transformation and validation engines, Saxon
 * still responds to JAXP loading requests.</p>
 *
 * <p>Many of these examples use Saxon with DOM input or output. Saxon
 * works with the DOM, but this is an inefficient way of using Saxon;
 * Saxon performs much better when it uses its own tree model internally.
 * It's therefore best to use a <code>StreamSource</code> or <code>SAXSource</code>
 * for the input, and a <code>StreamResult</code> or <code>SAXResult</code>
 * for the output. If you need to hold the tree structure in the application
 * (for example because you want to construct a tree once, and then use it
 * in several transformations), this is best done using Saxon-specific
 * APIs, for example the s9api DocumentBuilder.</p>
 * <p/>
 * <p>The original version of these examples was written by Scott Boag.<p/>
 */
public class JAXPExamples {

    /**
     * Method main
     *
     * @param argv command line arguments.
     *             There is a single argument, the name of the test to be run. The default is "all",
     *             which runs all tests.
     */
    public static void main(String[] argv) {

        if (!new File("data/books.xml").exists()) {
            throw new IllegalStateException("These tests must be run with the samples folder as the current directory");
        }

        String test = "all";
        if (argv.length > 0) {
            test = argv[0];
        }

        System.setProperty("javax.xml.transform.TransformerFactory", "net.sf.saxon.TransformerFactoryImpl");


        if (test.equals("all") || test.equals("ParseOnly")) {
            System.out.println("\n\n==== ParseOnly ====");

            try {
                exampleParseOnly("data/books.xml");
            } catch (Exception ex) {
                handleException(ex);
            }
        }


        if (test.equals("all") || test.equals("Simple1")) {
            System.out.println("\n\n==== Simple1 ====");

            try {
                exampleSimple1("data/books.xml", "styles/books.xsl");
            } catch (Exception ex) {
                handleException(ex);
            }
        }

        if (test.equals("all") || test.equals("Simple2")) {
            System.out.println("\n\n==== Simple2 ====");

            try {
                exampleSimple2("data/books.xml", "styles/books.xsl");
            } catch (Exception ex) {
                handleException(ex);
            }
        }

        if (test.equals("all") || test.equals("FromStream")) {
            System.out.println("\n\n==== FromStream ====");

            try {
                exampleFromStream("data/books.xml", "styles/books.xsl");
            } catch (Exception ex) {
                handleException(ex);
            }
        }

        if (test.equals("all") || test.equals("FromReader")) {
            System.out.println("\n\n==== FromReader ====");

            try {
                exampleFromReader("data/books.xml", "styles/books.xsl");
            } catch (Exception ex) {
                handleException(ex);
            }
        }

        if (test.equals("all") || test.equals("UseTemplatesObj")) {
            System.out.println("\n\n==== UseTemplatesObj ====");

            try {
                exampleUseTemplatesObj("data/books.xml", "data/more-books.xml", "styles/books.xsl");
            } catch (Exception ex) {
                handleException(ex);
            }
        }

        if (test.equals("all") || test.equals("UseTemplatesHandler")) {
            System.out.println("\n\n==== UseTemplatesHandler ====");

            try {
                exampleUseTemplatesHandler("data/books.xml", "styles/books.xsl");
            } catch (Exception ex) {
                handleException(ex);
            }
        }

        if (test.equals("all") || test.equals("SAXResult")) {
            System.out.println("\n\n==== SAXResult ====");

            try {
                exampleSAXResult("data/books.xml", "styles/books.xsl");
            } catch (Exception ex) {
                handleException(ex);
            }
        }

        if (test.equals("all") || test.equals("ContentHandlerToContentHandler")) {
            System.out
                    .println("\n\n==== ContentHandlerToContentHandler ====");

            try {
                exampleContentHandlerToContentHandler("data/books.xml", "styles/books.xsl");
            } catch (Exception ex) {
                handleException(ex);
            }
        }

        if (test.equals("all") || test.equals("XMLReader")) {
            System.out.println("\n\n==== XMLReader ====");

            try {
                exampleXMLReader("data/books.xml", "styles/books.xsl");
            } catch (Exception ex) {
                handleException(ex);
            }
        }

        if (test.equals("all") || test.equals("XMLFilter")) {
            System.out.println("\n\n==== XMLFilter ====");

            try {
                exampleXMLFilter("data/books.xml", "styles/books.xsl");
            } catch (Exception ex) {
                handleException(ex);
            }
        }

        if (test.equals("all") || test.equals("XMLFilterChain")) {
            System.out.println("\n\n==== XMLFilterChain ====");

            try {
                exampleXMLFilterChain("data/books.xml", "styles/rename-to-lowercase.xsl",
                        "styles/add-ids.xsl", "styles/summarize.xsl");
            } catch (Exception ex) {
                handleException(ex);
            }
        }

        if (test.equals("all") || test.equals("DOMsubtree")) {
            System.out.println("\n\n==== DOMsubtree ====");

            try {
                exampleDOMsubtree("data/books.xml");
            } catch (Exception ex) {
                handleException(ex);
            }
        }

        if (test.equals("all") || test.equals("DOMtoDOM")) {
            System.out.println("\n\n==== DOMtoDOM ====");

            try {
                exampleDOMtoDOM("data/books.xml", "styles/books.xsl");
            } catch (Exception ex) {
                handleException(ex);
            }
        }

        if (test.equals("all") || test.equals("Param")) {
            System.out.println("\n\n==== Param ====");

            try {
                exampleParam("data/books.xml", "styles/books.xsl");
            } catch (Exception ex) {
                handleException(ex);
            }
        }

        if (test.equals("all") || test.equals("TransformerReuse")) {
            System.out.println("\n\n==== TransformerReuse ====");

            try {
                exampleTransformerReuse("data/books.xml", "styles/books.xsl");
            } catch (Exception ex) {
                handleException(ex);
            }
        }

        if (test.equals("all") || test.equals("OutputProperties")) {
            System.out.println("\n\n==== OutputProperties ====");

            try {
                exampleOutputProperties("data/books.xml", "styles/books.xsl");
            } catch (Exception ex) {
                handleException(ex);
            }
        }

        if (test.equals("all") || test.equals("UseAssociated")) {
            System.out.println("\n\n==== UseAssociated ====");

            try {
                exampleUseAssociated("data/books.xml");
            } catch (Exception ex) {
                handleException(ex);
            }
        }

        if (test.equals("all") || test.equals("ContentHandlertoDOM")) {
            System.out.println("\n\n==== ContentHandlertoDOM ====");

            try {
                exampleContentHandlertoDOM("data/books.xml", "styles/books.xsl");
            } catch (Exception ex) {
                handleException(ex);
            }
        }

        if (test.equals("all") || test.equals("AsSerializer")) {
            System.out.println("\n\n==== AsSerializer ====");

            try {
                exampleAsSerializer("data/books.xml");
            } catch (Exception ex) {
                handleException(ex);
            }
        }

        if (test.equals("all") || test.equals("NewDOMSerializer")) {
            System.out.println("\n\n==== NewDOMSerializer ====");

            try {
                exampleNewDOMSerializer();
            } catch (Exception ex) {
                handleException(ex);
            }
        }

        if (test.equals("all") || test.equals("UsingURIResolver")) {
            System.out.println("\n\n==== UsingURIResolver ====");

            try {
                exampleUsingURIResolver("data/books.xml", "styles/books.xsl");
            } catch (Exception ex) {
                handleException(ex);
            }
        }

        System.out.println("\n==== done! ====");
    }

    /**
     * Show the simplest possible transformation from system id
     * to output stream.
     *
     * @param sourceID file name of the source file
     * @param xslID    file name of the stylesheet file
     * @throws TransformerException in the event of a static or dynamic error
     */
    public static void exampleSimple1(String sourceID, String xslID)
            throws TransformerException {

        // Create a transform factory instance.
        TransformerFactory tfactory = TransformerFactory.newInstance();

        // Create a transformer for the stylesheet.
        Transformer transformer =
                tfactory.newTransformer(new StreamSource(xslID));

        // Transform the source XML to System.out.
        transformer.transform(new StreamSource(sourceID),
                new StreamResult(System.out));
    }

    /**
     * Example that shows XML parsing only (no transformation)
     *
     * @param sourceID file name of the source file
     * @throws TransformerException in the event of a static or dynamic error
     */

    public static void exampleParseOnly(String sourceID) throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser parser = factory.newSAXParser();
        parser.parse(sourceID, new org.xml.sax.helpers.DefaultHandler());
        System.out.println("\nParsing complete\n");
    }

    /**
     * Show the simplest possible transformation from File
     * to a File.
     *
     * @param sourceID file name of the source file
     * @param xslID    file name of the stylesheet file
     * @throws TransformerException in the event of a static or dynamic error
     */
    public static void exampleSimple2(String sourceID, String xslID)
            throws TransformerException {

        // Create a transform factory instance.
        TransformerFactory tfactory = TransformerFactory.newInstance();

        // Create a transformer for the stylesheet.
        Transformer transformer =
                tfactory.newTransformer(new StreamSource(xslID));

        // Transform the source XML to System.out.
        transformer.transform(new StreamSource(sourceID),
                new StreamResult(new File("Simple2.out")));

        System.out.println("\nOutput written to Simple2.out\n");
    }

    /**
     * Show simple transformation from input stream to output stream.
     *
     * @param sourceID file name of the source file
     * @param xslID    file name of the stylesheet file
     * @throws TransformerException  in the event of a static or dynamic error
     * @throws FileNotFoundException if either input file does not exist
     */
    public static void exampleFromStream(String sourceID, String xslID)
            throws TransformerException, FileNotFoundException {

        // Create a transform factory instance.
        TransformerFactory tfactory = TransformerFactory.newInstance();
        InputStream xslIS = new BufferedInputStream(new FileInputStream(xslID));
        StreamSource xslSource = new StreamSource(xslIS, xslID);

        // Create a transformer for the stylesheet.
        Transformer transformer = tfactory.newTransformer(xslSource);
        InputStream xmlIS = new BufferedInputStream(new FileInputStream(sourceID));
        StreamSource xmlSource = new StreamSource(xmlIS, sourceID);

        // Transform the source XML to System.out.
        transformer.transform(xmlSource, new StreamResult(System.out));
    }

    /**
     * Show simple transformation from reader to output stream.  In general
     * this use case is discouraged, since the XML encoding can not be
     * processed.
     *
     * @param sourceID file name of the source file
     * @param xslID    file name of the stylesheet file
     * @throws TransformerException  in the event of a static or dynamic error
     * @throws FileNotFoundException if either input file does not exist
     */
    public static void exampleFromReader(String sourceID, String xslID)
            throws TransformerException, FileNotFoundException {

        // Create a transform factory instance.
        TransformerFactory tfactory = TransformerFactory.newInstance();

        // Note that in this case the XML encoding can not be processed!
        Reader xslReader = new BufferedReader(new FileReader(xslID));
        StreamSource xslSource = new StreamSource(xslReader, xslID);

        // Create a transformer for the stylesheet.
        Transformer transformer = tfactory.newTransformer(xslSource);

        // Note that in this case the XML encoding can not be processed!
        Reader xmlReader = new BufferedReader(new FileReader(sourceID));
        StreamSource xmlSource = new StreamSource(xmlReader, sourceID);

        // Transform the source XML to System.out.
        transformer.transform(xmlSource, new StreamResult(System.out));
    }

    /**
     * Perform two transformations using a compiled stylesheet (a Templates object),
     * using the compiled stylesheet to transform two source files
     *
     * @param sourceID1 file name of the first source file
     * @param sourceID2 file name of the second source file
     * @param xslID     file name of the stylesheet file
     * @throws TransformerException in the event of a static or dynamic error
     */
    public static void exampleUseTemplatesObj(
            String sourceID1, String sourceID2, String xslID)
            throws TransformerException {

        TransformerFactory tfactory = TransformerFactory.newInstance();

        // Create a templates object, which is the processed,
        // thread-safe representation of the stylesheet.
        Templates templates = tfactory.newTemplates(new StreamSource(xslID));

        // Illustrate the fact that you can make multiple transformers
        // from the same stylesheet.
        Transformer transformer1 = templates.newTransformer();
        Transformer transformer2 = templates.newTransformer();

        System.out.println("\n\n----- transform of " + sourceID1 + " -----");
        transformer1.transform(new StreamSource(sourceID1),
                new StreamResult(System.out));
        System.out.println("\n\n----- transform of " + sourceID2 + " -----");
        transformer2.transform(new StreamSource(sourceID2),
                new StreamResult(System.out));
    }

    /**
     * Perform a transformation using a compiled stylesheet (a Templates object)
     *
     * @param sourceID file name of the source file
     * @param xslID    file name of the stylesheet file
     * @throws IOException                  if an input file is not available
     * @throws SAXException                 if an input file cannot be parsed
     * @throws ParserConfigurationException if the XML parser cannot be properly configured
     * @throws TransformerException         if the transformation fails with a static or dynamic error
     */
    public static void exampleUseTemplatesHandler(String sourceID, String xslID)
            throws TransformerException, SAXException, IOException, ParserConfigurationException {

        TransformerFactory tfactory = TransformerFactory.newInstance();

        // Does this factory support SAX features?
        if (tfactory.getFeature(SAXSource.FEATURE)) {

            // If so, we can safely cast.
            SAXTransformerFactory stfactory = (SAXTransformerFactory) tfactory;

            // Create a Templates ContentHandler to handle parsing of the
            // stylesheet.
            javax.xml.transform.sax.TemplatesHandler templatesHandler =
                    stfactory.newTemplatesHandler();

            // Create an XMLReader and set its features.
            XMLReader reader = makeXMLReader();
            reader.setFeature("http://xml.org/sax/features/namespaces", true);
            reader.setFeature("http://xml.org/sax/features/namespace-prefixes", false);

            // Create a XMLFilter that modifies the stylesheet
            XMLFilter filter = new ModifyStylesheetFilter();
            filter.setParent(reader);

            filter.setContentHandler(templatesHandler);

            // Parse the stylesheet, preprocessing it using the filter
            filter.parse(new InputSource(xslID));

            // Get the Templates object (generated during the parsing of the stylesheet)
            // from the TemplatesHandler.
            Templates templates = templatesHandler.getTemplates();
            Transformer transformer = templates.newTransformer();

            transformer.setOutputProperty(OutputKeys.INDENT, "yes");

            // Do the transformation
            transformer.transform(
                    new StreamSource(sourceID), new StreamResult(System.out));

        } else {
            System.out.println("The factory is not a SAXTransformerFactory");
        }

    }

    /**
     * This class is a SAX filter used to modify a stylesheet by changing
     * all element names other than those prefixed "xsl:" to start with "XX".
     */

    private static class ModifyStylesheetFilter extends XMLFilterImpl {
        public void startDocument() throws SAXException {
            System.err.println("ModifyStylesheetFilter#startDocument");
            super.startDocument();
        }

        public void startElement(String namespaceURI, String localName,
                                 String qName, Attributes atts) throws SAXException {
            String lname = qName.startsWith("xsl:") ? localName : "XX" + localName;
            super.startElement(namespaceURI, lname, qName, atts);
        }

        public void endElement(String namespaceURI, String localName,
                               String qName) throws SAXException {
            String lname = qName.startsWith("xsl:") ? localName : "XX" + localName;
            super.endElement(namespaceURI, lname, qName);
        }
    }

    /**
     * Send output to a user-specified ContentHandler.
     *
     * @param sourceID file name of the source file
     * @param xslID    file name of the stylesheet file
     * @throws IOException          if an input file is not available
     * @throws TransformerException if the transformation fails with a static or dynamic error
     */

    public static void exampleSAXResult(String sourceID, String xslID)
            throws TransformerException, IOException {

        TransformerFactory tfactory = TransformerFactory.newInstance();

        // Does this factory support SAX features?
        if (tfactory.getFeature(SAXResult.FEATURE)) {

            // Get a transformer in the normal way:
            Transformer transformer = tfactory.newTransformer(new StreamSource(xslID));

            // Get the source as a StreamSource
            Reader xmlReader = new BufferedReader(new FileReader(sourceID));
            StreamSource xmlSource = new StreamSource(xmlReader, sourceID);

            // Set the result handling to be a serialization to System.out.
            Result result = new SAXResult(new ExampleContentHandler());

            // Do the transformation
            transformer.transform(xmlSource, result);

        } else {
            System.out.println("The factory is not a SAXTransformerFactory");
        }
    }

    /**
     * Show the Transformer using SAX events in and SAX events out.
     *
     * @param sourceID file name of the source file
     * @param xslID    file name of the stylesheet file
     * @throws IOException                  if an input file is not available
     * @throws SAXException                 if an input file cannot be parsed
     * @throws ParserConfigurationException if the XML parser cannot be properly configured
     * @throws TransformerException         if the transformation fails with a static or dynamic error
     */
    public static void exampleContentHandlerToContentHandler(String sourceID, String xslID)
            throws TransformerException, SAXException, ParserConfigurationException, IOException {

        TransformerFactory tfactory = TransformerFactory.newInstance();

        // Does this factory support SAX features?
        if (tfactory.getFeature(SAXSource.FEATURE)) {

            // If so, we can safely cast.
            SAXTransformerFactory stfactory = (SAXTransformerFactory) tfactory;

            // A TransformerHandler is a ContentHandler that will listen for
            // SAX events, and transform them to the result.
            TransformerHandler handler = stfactory.newTransformerHandler(new StreamSource(xslID));

            // Set the result handling to be a serialization to System.out.
            Result result = new SAXResult(new ExampleContentHandler());
            handler.setResult(result);

            // Create a reader, and set its content handler to be the TransformerHandler.
            XMLReader reader = makeXMLReader();
            reader.setContentHandler(handler);

            // It's a good idea for the parser to send lexical events (such as comments and CDATA sections).
            // The TransformerHandler is also a LexicalHandler.
            reader.setProperty(
                    "http://xml.org/sax/properties/lexical-handler", handler);

            // Parse the source XML, and send the parse events to the TransformerHandler.
            reader.parse(sourceID);
        } else {
            System.out.println("The factory is not a SAXTransformerFactory");
        }
    }

    /**
     * Show the Transformer as a SAX2 XMLReader.  An XMLFilter obtained
     * from newXMLFilter should act as a transforming XMLReader if setParent is not
     * called.  Internally, an XMLReader is created as the parent for the XMLFilter.
     *
     * @param sourceID file name of the source file
     * @param xslID    file name of the stylesheet file
     * @throws IOException          if an input file is not available
     * @throws SAXException         if an input file cannot be parsed
     * @throws TransformerException if the transformation fails with a static or dynamic error
     */
    public static void exampleXMLReader(String sourceID, String xslID)
            throws TransformerException, SAXException, IOException {

        TransformerFactory tfactory = TransformerFactory.newInstance();

        if (tfactory.getFeature(SAXSource.FEATURE)) {
            XMLReader reader = ((SAXTransformerFactory) tfactory).newXMLFilter(new StreamSource(new File(xslID)));

            reader.setContentHandler(new ExampleContentHandler());
            reader.parse(new InputSource(new File(sourceID).toURI().toString()));
        } else {
            System.out.println("The factory is not a SAXTransformerFactory");
        }
    }

    /**
     * Show the Transformer as a simple XMLFilter.  This is pretty similar
     * to exampleXMLReader, except that here the parent XMLReader is created
     * by the caller, instead of automatically within the XMLFilter.  This
     * gives the caller more direct control over the parent reader.
     *
     * @param sourceID file name of the source file
     * @param xslID    file name of the stylesheet file
     * @throws IOException                  if an input file is not available
     * @throws SAXException                 if an input file cannot be parsed
     * @throws ParserConfigurationException if the XML parser cannot be properly configured
     * @throws TransformerException         if the transformation fails with a static or dynamic error
     */
    public static void exampleXMLFilter(String sourceID, String xslID)
            throws TransformerException, SAXException, IOException, ParserConfigurationException {

        TransformerFactory tfactory = TransformerFactory.newInstance();
        XMLReader reader = makeXMLReader();

        // The transformer will use a SAX parser as its reader.

        try {
            reader.setFeature("http://xml.org/sax/features/namespace-prefixes", true);
        } catch (SAXException se) {
            System.err.println("SAX Parser doesn't report namespace prefixes!");
            throw se;
        }

        final StreamSource style = new StreamSource(new File(xslID));
        XMLFilter filter = ((SAXTransformerFactory) tfactory).newXMLFilter(style);

        filter.setParent(reader);
        filter.setContentHandler(new ExampleContentHandler());

        // Now, when you call transformer.parse, it will set itself as
        // the content handler for the parser object (its "parent"), and
        // will then call the parse method on the parser.
        filter.parse(new InputSource(new File(sourceID).toURI().toString()));
    }

    /**
     * This example shows how to chain events from one Transformer
     * to another transformer, using the Transformer as a
     * SAX2 XMLFilter/XMLReader.
     *
     * @param sourceID file name of the source file
     * @param xslID_1  file name of the first stylesheet file
     * @param xslID_2  file name of the second stylesheet file
     * @param xslID_3  file name of the third stylesheet file
     * @throws IOException                  if an input file is not available
     * @throws SAXException                 if an input file cannot be parsed
     * @throws ParserConfigurationException if the XML parser cannot be properly configured
     * @throws TransformerException         if the transformation fails with a static or dynamic error
     */
    public static void exampleXMLFilterChain(String sourceID, String xslID_1, String xslID_2, String xslID_3)
            throws TransformerException, SAXException, IOException, ParserConfigurationException {

        TransformerFactory tfactory = TransformerFactory.newInstance();

        if (tfactory.getFeature(SAXSource.FEATURE)) {
            SAXTransformerFactory stf = (SAXTransformerFactory) tfactory;
            XMLReader reader = makeXMLReader();

            XMLFilter filter1 = stf.newXMLFilter(new StreamSource(new File(xslID_1)));
            XMLFilter filter2 = stf.newXMLFilter(new StreamSource(new File(xslID_2)));
            XMLFilter filter3 = stf.newXMLFilter(new StreamSource(new File(xslID_3)));

            if (filter1 != null) {   // If one succeeds, assume all will succeed.

                // transformer1 will use a SAX parser as its reader.
                filter1.setParent(reader);

                // transformer2 will use transformer1 as its reader.
                filter2.setParent(filter1);

                // transformer3 will use transformer2 as its reader.
                filter3.setParent(filter2);
                filter3.setContentHandler(new ExampleContentHandler());

                // filter3.setContentHandler(new org.xml.sax.helpers.DefaultHandler());
                // Now, when you call transformer3 to parse, it will set
                // itself as the ContentHandler for transform2, and
                // call transform2.parse, which will set itself as the
                // content handler for transform1, and call transform1.parse,
                // which will set itself as the content listener for the
                // SAX parser, and then call the parse method on the parser.
                filter3.parse(new InputSource(new File(sourceID).toURI().toString()));
            } else {
                System.out.println("The factory doesn't support newXMLFilter()");
            }
        } else {
            System.out.println("The factory is not a SAXTransformerFactory");
        }
    }


    /**
     * Show how to extract a subtree of a DOM by using the identity
     * transformer starting at a non-root element of the supplied DOM
     *
     * @param sourceID file name of the source file
     * @throws IOException                  if an input file is not available
     * @throws SAXException                 if an input file cannot be parsed
     * @throws ParserConfigurationException if the XML parser cannot be properly configured
     * @throws TransformerException         if the transformation fails with a static or dynamic error
     */
    public static void exampleDOMsubtree(String sourceID)
            throws TransformerException, SAXException, IOException, ParserConfigurationException {

        TransformerFactory tfactory = TransformerFactory.newInstance();
        Transformer transformer = tfactory.newTransformer();

        if (tfactory.getFeature(DOMSource.FEATURE)) {

            DocumentBuilderFactory dfactory = DocumentBuilderFactory.newInstance();
            System.err.println("Using DocumentBuilderFactory " + dfactory.getClass());

            dfactory.setNamespaceAware(true);

            DocumentBuilder docBuilder = dfactory.newDocumentBuilder();
            System.err.println("Using DocumentBuilder " + docBuilder.getClass());

            String sourceURI = new File(sourceID).toURI().toString();
            Document doc = docBuilder.parse(new InputSource(sourceURI));
            Node bar = doc.getDocumentElement().getFirstChild();
            while (bar.getNodeType() != Node.ELEMENT_NODE) {
                bar = bar.getNextSibling();
            }

            System.err.println("Source document built OK");

            DOMSource ds = new DOMSource(bar);
            ds.setSystemId(sourceURI);
            transformer.transform(ds, new StreamResult(System.out));
            System.err.println("Transformation done OK");
        } else {
            throw new org.xml.sax.SAXNotSupportedException(
                    "DOM node processing not supported!");
        }
    }


    /**
     * Show how to transform a DOM tree into another DOM tree.
     * This uses the javax.xml.parsers to parse an XML file into a
     * DOM, and create an output DOM. In this example, Saxon uses a
     * third-party DOM as both input and output.
     *
     * @param sourceID file name of the source file
     * @param xslID    file name of the stylesheet file
     * @throws IOException                  if an input file is not available
     * @throws SAXException                 if an input file cannot be parsed
     * @throws ParserConfigurationException if the XML parser cannot be properly configured
     * @throws TransformerException         if the transformation fails with a static or dynamic error
     */
    public static void exampleDOMtoDOM(String sourceID, String xslID)
            throws TransformerException, SAXException, IOException, ParserConfigurationException {

        String factory = getDocumentBuilderFactory();

        if (factory == null) {
            System.err.println("No third-party DOM Builder found");
            return;
        }

        System.setProperty("javax.xml.parsers.DocumentBuilderFactory", factory);

        TransformerFactory tfactory = TransformerFactory.newInstance();

        if (tfactory.getFeature(DOMSource.FEATURE)) {
            DocumentBuilderFactory dfactory = DocumentBuilderFactory.newInstance();
            System.err.println("Using DocumentBuilderFactory " + dfactory.getClass());

            dfactory.setNamespaceAware(true);

            DocumentBuilder docBuilder = dfactory.newDocumentBuilder();
            System.err.println("Using DocumentBuilder " + docBuilder.getClass());

            String xslURI = new File(xslID).toURI().toString();
            Node xsldoc = docBuilder.parse(new InputSource(xslURI));
            System.err.println("Stylesheet document built OK");
            DOMSource dsource = new DOMSource(xsldoc);

            // If we don't do this, the transformer won't know how to
            // resolve relative URLs in the stylesheet.
            dsource.setSystemId(xslURI);

            Templates templates = tfactory.newTemplates(dsource);
            Transformer transformer = templates.newTransformer();

            String sourceURI = new File(sourceID).toURI().toString();
            Document doc = docBuilder.parse(new InputSource(sourceURI));
            System.err.println("Source document built OK");

            DOMSource ds = new DOMSource(doc);
            ds.setSystemId(sourceURI);

            // use a DOMResult holder for the transformation result DOM tree
            Document out = docBuilder.newDocument();
            transformer.transform(ds, new DOMResult(out));
            System.err.println("Transformation done OK");

            // Serialize the output so we can see the transformation actually worked
            Transformer serializer = tfactory.newTransformer();
            serializer.transform(new DOMSource(out), new StreamResult(System.out));

        } else {
            throw new org.xml.sax.SAXNotSupportedException(
                    "DOM node processing not supported!");
        }
    }


    /**
     * Get a DocumentBuilderFactory for a third-party DOM
     *
     * @return the name of the chosen DocumentBuilderFactory class
     */

    private static String getDocumentBuilderFactory() {

        // Try the Apache Xerces parser. This is the recommended parser for use with Saxon

        try {
            Class.forName("org.apache.xerces.jaxp.DocumentBuilderFactoryImpl");
            return "org.apache.xerces.jaxp.DocumentBuilderFactoryImpl";
        } catch (Exception e) {
            //
        }

        // Try the parser bundled in the JDK, which is an older version of Xerces

        try {
            Class.forName("com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl");
            return "com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl";
        } catch (Exception e) {
            //
        }

        return null;

    }


    /**
     * This shows how to set a parameter for use by the templates. It uses
     * two transformers to show that different parameters may be set
     * on different transformers.
     *
     * @param sourceID file name of the source file
     * @param xslID    file name of the stylesheet file
     * @throws TransformerException if a static or dynamic error occurs
     */
    public static void exampleParam(String sourceID, String xslID)
            throws TransformerException {

        // Create a transform factory instance.
        TransformerFactory tfactory = TransformerFactory.newInstance();
        Templates templates =
                tfactory.newTemplates(new StreamSource(new File(xslID)));

        // Create two transformers for the stylesheet.
        Transformer transformer1 = templates.newTransformer();
        Transformer transformer2 = templates.newTransformer();

        System.out.println("\nTransform 1 and 2 use different transformers");
        System.out.println("\n\n----- Transform 1: set top-author parameter -----");
        transformer1.setParameter("top-author", "Jane Austen");
        transformer1.transform(new StreamSource(new File(sourceID)),
                new StreamResult(System.out));

        System.out.println("\n\n----- Transform 2: set head-title parameter -----");
        transformer2.setParameter("head-title", "A List of Books");
        transformer2.transform(new StreamSource(new File(sourceID)),
                new StreamResult(System.out));
    }

    /**
     * Show that a transformer can be reused, and show resetting
     * a parameter on the transformer.
     *
     * @param sourceID file name of the source file
     * @param xslID    file name of the stylesheet file
     * @throws TransformerException if a static or dynamic error occurs
     */
    public static void exampleTransformerReuse(String sourceID, String xslID)
            throws TransformerException {

        // Create a transform factory instance.
        TransformerFactory tfactory = TransformerFactory.newInstance();

        // Create a transformer for the stylesheet.
        Transformer transformer =
                tfactory.newTransformer(new StreamSource(new File(xslID)));

        System.out.println("\nTransform 1 and 2 use the same transformer");
        System.out.println("\n\n----- Transform 1: set top-author parameter -----");
        transformer.setParameter("top-author", "Jane Austen");

        // Transform the source XML to System.out.
        transformer.transform(new StreamSource(new File(sourceID)),
                new StreamResult(System.out));

        System.out.println("\n\n----- Transform 2: reset top-author parameter -----");
        transformer.setParameter("top-author", "Thomas Hardy");

        // Transform the source XML to System.out.
        transformer.transform(new StreamSource(new File(sourceID)),
                new StreamResult(System.out));
    }

    /**
     * Show how to override output properties.
     *
     * @param sourceID file name of the source file
     * @param xslID    file name of the stylesheet file
     * @throws TransformerException if a static or dynamic error occurs
     */
    public static void exampleOutputProperties(String sourceID, String xslID)
            throws TransformerException {

        TransformerFactory tfactory = TransformerFactory.newInstance();
        Templates templates =
                tfactory.newTemplates(new StreamSource(new File(xslID)));
        Properties oprops = templates.getOutputProperties();
        oprops.setProperty(OutputKeys.INDENT, "no");

        Transformer transformer = templates.newTransformer();

        transformer.setOutputProperties(oprops);
        transformer.transform(new StreamSource(new File(sourceID)),
                new StreamResult(System.out));
    }

    /**
     * Show how to get stylesheets that are associated with a given
     * xml document via the xml-stylesheet PI (see http://www.w3.org/TR/xml-stylesheet/).
     *
     * @param sourceID file name of the source file
     * @throws TransformerException if a static or dynamic error occurs
     */
    public static void exampleUseAssociated(String sourceID)
            throws TransformerException {

        TransformerFactory tfactory = TransformerFactory.newInstance();

        // The DOM tfactory will have it's own way, based on DOM2,
        // of getting associated stylesheets.
        if (tfactory instanceof SAXTransformerFactory) {
            SAXTransformerFactory stf = (SAXTransformerFactory) tfactory;
            Source sources =
                    stf.getAssociatedStylesheet(new StreamSource(sourceID), null,
                            null, null);

            if (null != sources) {
                Transformer transformer = tfactory.newTransformer(sources);

                transformer.transform(new StreamSource(sourceID),
                        new StreamResult(System.out));
            } else {
                System.out.println("Can't find the associated stylesheet!");
            }
        }
    }

    /**
     * Show the Transformer using SAX events in and DOM nodes out.
     *
     * @param sourceID file name of the source file
     * @param xslID    file name of the stylesheet file
     * @throws IOException                  if an input file is not available
     * @throws SAXException                 if an input file cannot be parsed
     * @throws ParserConfigurationException if the XML parser cannot be properly configured
     * @throws TransformerException         if the transformation fails with a static or dynamic error
     */
    public static void exampleContentHandlertoDOM(String sourceID, String xslID)
            throws TransformerException, SAXException, IOException, ParserConfigurationException {

        TransformerFactory tfactory = TransformerFactory.newInstance();

        // Make sure the transformer factory we obtained supports both
        // DOM and SAX.
        if (tfactory.getFeature(SAXSource.FEATURE)
                && tfactory.getFeature(DOMSource.FEATURE)) {

            // We can now safely cast to a SAXTransformerFactory.
            SAXTransformerFactory sfactory = (SAXTransformerFactory) tfactory;

            // Create an Document node as the root for the output.
            DocumentBuilderFactory dfactory =
                    DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = dfactory.newDocumentBuilder();
            org.w3c.dom.Document outNode = docBuilder.newDocument();

            // Create a ContentHandler that can liston to SAX events
            // and transform the output to DOM nodes.
            TransformerHandler handler =
                    sfactory.newTransformerHandler(new StreamSource(xslID));

            handler.setResult(new DOMResult(outNode));

            // Create a reader and set it's ContentHandler to be the
            // transformer.
            XMLReader reader = makeXMLReader();

            reader.setContentHandler(handler);
            reader.setProperty(
                    "http://xml.org/sax/properties/lexical-handler", handler);

            // Send the SAX events from the parser to the transformer,
            // and thus to the DOM tree.
            reader.parse(sourceID);

            // Serialize the node for diagnosis.
            exampleSerializeNode(outNode);
        } else {
            System.out.println(
                    "Can't do exampleContentHandlerToDOM because tfactory is not a SAXTransformerFactory");
        }
    }

    /**
     * Show a transformation using a user-written URI Resolver.
     *
     * @param sourceID file name of the source file
     * @param xslID    file name of the stylesheet file
     * @throws TransformerException if the transformation fails with a static or dynamic error
     */

    public static void exampleUsingURIResolver(String sourceID, String xslID)
            throws TransformerException {

        // Create a transform factory instance.
        TransformerFactory tfactory = TransformerFactory.newInstance();

        // Create a transformer for the stylesheet.
        Transformer transformer =
                tfactory.newTransformer(new StreamSource(xslID));

        // Set the URIResolver
        transformer.setURIResolver(new UserURIResolver(transformer));

        // Transform the source XML to System.out.
        transformer.transform(new StreamSource(sourceID),
                new StreamResult(System.out));

    }

    /**
     * A sample URIResolver. This handles a URI ending with ".txt". It loads the
     * text file identified by the URI, assuming it is in ISO-8859-1 encoding,
     * into a tree containing a single text node. It returns this
     * result tree to the transformer, exploiting the fact that a Saxon NodeInfo
     * can be used as a Source. If the URI doesn't end with ".txt", it hands over
     * to the standard URI resolver by returning null.
     */

    public static class UserURIResolver implements URIResolver {

        Transformer transformer;

        /**
         * Create a URIResolver
         *
         * @param t the JAXP Transformer
         */
        public UserURIResolver(Transformer t) {
            transformer = t;
        }

        /**
         * Resolve a URI
         *
         * @param base The base URI that should be used. May be null if uri is absolute.
         * @param href The relative or absolute URI. May be an empty string. May contain
         *             a fragment identifier starting with "#", which must be the value of an ID attribute
         *             in the referenced XML document.
         * @return a Source object representing an XML document
         */

        public Source resolve(String href, String base)
                throws TransformerException {
            if (href.endsWith(".txt")) {
                try {
                    URL url = new URL(new URL(base), href);
                    java.io.InputStream in = url.openConnection().getInputStream();
                    java.io.InputStreamReader reader =
                            new java.io.InputStreamReader(in, "iso-8859-1");

                    // this could be vastly more efficient, but it doesn't matter here

                    StringBuilder sb = new StringBuilder();
                    int c;
                    while ((c = reader.read()) >= 0) {
                        sb.append((char) c);
                    }
                    Orphan textNode = new Orphan(((TransformerImpl) transformer).getConfiguration());
                    textNode.setNodeKind(Type.TEXT);
                    textNode.setStringValue(sb.toString());
                    return textNode;
                } catch (Exception err) {
                    throw new TransformerException(err);
                }
            } else {
                return null;
            }
        }

    } // end of inner class UserURIResolver


    /**
     * Serialize a node to System.out.
     * @param node the node to be serialized
     * @throws TransformerException         if the transformation fails with a static or dynamic error
     */
    public static void exampleSerializeNode(Node node)
            throws TransformerException {

        TransformerFactory tfactory = TransformerFactory.newInstance();

        // This creates a transformer that does a simple identity transform,
        // and thus can be used for all intents and purposes as a serializer.
        Transformer serializer = tfactory.newTransformer();

        serializer.setOutputProperty(OutputKeys.INDENT, "yes");
        serializer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        serializer.transform(new DOMSource(node),
                new StreamResult(System.out));
    }

    /**
     * A fuller example showing how the TrAX interface can be used
     * to serialize a DOM tree.
     *
     * @param sourceID file name of the source file
     * @throws IOException                  if an input file is not available
     * @throws SAXException                 if an input file cannot be parsed
     * @throws ParserConfigurationException if the XML parser cannot be properly configured
     * @throws TransformerException         if the transformation fails with a static or dynamic error
     */
    public static void exampleAsSerializer(String sourceID)
            throws TransformerException, SAXException, IOException, ParserConfigurationException {

        DocumentBuilderFactory dfactory =
                DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = dfactory.newDocumentBuilder();
        Node doc =
                docBuilder.parse(new InputSource(sourceID));
        TransformerFactory tfactory = TransformerFactory.newInstance();

        // This creates a transformer that does a simple identity transform,
        // and thus can be used for all intents and purposes as a serializer.
        Transformer serializer = tfactory.newTransformer();
        Properties oprops = new Properties();

        oprops.setProperty("method", "html");

        // The following property setting should be ignored in Saxon-HE
        oprops.setProperty("{http://saxon.sf.net/}indent-spaces", "2");

        serializer.setOutputProperties(oprops);
        serializer.transform(new DOMSource(doc),
                new StreamResult(System.out));
    }


    /**
     * An example showing how to serialize a program-constructed DOM tree.
     * @throws ParserConfigurationException if the XML parser cannot be properly configured
     * @throws TransformerException         if the transformation fails with a static or dynamic error
     */

    public static void exampleNewDOMSerializer()
            throws TransformerException, ParserConfigurationException {

        DocumentBuilderFactory dfactory =
                DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = dfactory.newDocumentBuilder();
        org.w3c.dom.Document doc = docBuilder.newDocument();
        org.w3c.dom.Element docElement = doc.createElementNS("nsx.uri", "x");
        doc.appendChild(docElement);

        TransformerFactory tfactory = TransformerFactory.newInstance();

        // This creates a transformer that does a simple identity transform,
        // and thus can be used for all intents and purposes as a serializer.
        Transformer serializer = tfactory.newTransformer();
        Properties oprops = new Properties();

        oprops.setProperty("method", "xml");
        oprops.setProperty("indent", "yes");
        serializer.setOutputProperties(oprops);
        serializer.transform(new DOMSource(doc),
                new StreamResult(System.out));
    }


    /**
     * Utility routine to handle any exception thrown by a test case
     * @param ex the exception that was thrown
     */

    private static void handleException(Exception ex) {

        System.out.println("EXCEPTION: " + ex);
        ex.printStackTrace();

        if (ex instanceof TransformerConfigurationException) {
            System.out.println();
            System.out.println("Test failed");

            Throwable ex1 =
                    ((TransformerConfigurationException) ex).getException();

            if (ex1 != null) {    // added by MHK
                ex1.printStackTrace();

                if (ex1 instanceof SAXException) {
                    Exception ex2 = ((SAXException) ex1).getException();

                    System.out.println("Internal sub-exception: ");
                    ex2.printStackTrace();
                }
            }
        }
    }

    /**
     * Make an XMLReader (a SAX Parser)
     *
     * @return the constructed XMLReader
     * @throws ParserConfigurationException if the parser cannot be properly configured
     * @throws SAXException if the parser cannot be instantiated
     */

    private static XMLReader makeXMLReader() throws ParserConfigurationException, SAXException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newSAXParser().getXMLReader();
    }
}
