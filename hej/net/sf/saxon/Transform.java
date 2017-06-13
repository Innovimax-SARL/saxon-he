////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon;

import net.sf.saxon.expr.instruct.GlobalContextRequirement;
import net.sf.saxon.expr.instruct.TerminationException;
import net.sf.saxon.lib.*;
import net.sf.saxon.s9api.*;
import net.sf.saxon.style.StylesheetPackage;
import net.sf.saxon.trace.AbstractTraceListener;
import net.sf.saxon.trace.TimingCodeInjector;
import net.sf.saxon.trace.TimingTraceListener;
import net.sf.saxon.trans.CommandLineOptions;
import net.sf.saxon.trans.CompilerInfo;
import net.sf.saxon.trans.LicenseException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.trans.packages.PackageDetails;
import net.sf.saxon.trans.packages.PackageLibrary;
import net.sf.saxon.value.DateTimeValue;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * This <b>Transform</b> class is the command-line entry point to the Saxon XSLT Processor.
 * <p/>
 * <p>It is possible to subclass this class to provide a customized command line interface. In writing such
 * a subclass:</p>
 * <p/>
 * <ul>
 * <li>The {@link #main} method should instantiate the class and call the {@link #doTransform} method, passing the
 * argument list. The argument list can be augmented or modified if required: for example, by adding a -config
 * argument to cause the configuration to be initialized from a configuration file.</li>
 * <li>The {@link #setFactoryConfiguration} method can be implemented
 */

public class Transform {

    protected Processor processor;
    protected XsltCompiler compiler;
    protected boolean useURLs = false;
    protected boolean showTime = false;
    protected int repeat = 1;
    /*@Nullable*/ protected String sourceParserName = null;
    protected boolean schemaAware = false;
    protected boolean allowExit = true;
    protected boolean run = true;

    /**
     * Main program, can be used directly from the command line.
     * <p>The format is:</P>
     * <p>java net.sf.saxon.Transform [options] <I>source-file</I> <I>style-file</I> &gt;<I>output-file</I></P>
     * <p>followed by any number of parameters in the form {keyword=value}... which can be
     * referenced from within the stylesheet.</p>
     * <p>This program applies the XSL style sheet in style-file to the source XML document in source-file.</p>
     *
     * @param args List of arguments supplied on operating system command line
     * @throws java.lang.Exception Indicates that a compile-time or
     *                             run-time error occurred
     */

    public static void main(String args[])
            throws java.lang.Exception {
        // the real work is delegated to another routine so that it can be used in a subclass
        new Transform().doTransform(args, "java net.sf.saxon.Transform");
    }

    /**
     * Set the options that are recognized on the command line. This method can be overridden in a subclass
     * to define additional command line options.
     *
     * @param options the CommandLineOptions in which the recognized options are to be registered.
     */

    public void setPermittedOptions(CommandLineOptions options) {
        options.addRecognizedOption("a", CommandLineOptions.TYPE_BOOLEAN,
                                    "Use <?xml-stylesheet?> processing instruction to identify stylesheet");
        options.addRecognizedOption("catalog", CommandLineOptions.TYPE_FILENAME | CommandLineOptions.VALUE_REQUIRED,
                                    "Use specified catalog file to resolve URIs");
        options.addRecognizedOption("config", CommandLineOptions.TYPE_FILENAME | CommandLineOptions.VALUE_REQUIRED,
                                    "Use specified configuration file");
        options.addRecognizedOption("cr", CommandLineOptions.TYPE_CLASSNAME | CommandLineOptions.VALUE_REQUIRED,
                                    "Use specified collection URI resolver class");
        options.addRecognizedOption("diag", CommandLineOptions.TYPE_FILENAME,
                                    "Display runtime diagnostics");
        options.addRecognizedOption("dtd", CommandLineOptions.TYPE_ENUMERATION,
                                    "Validate using DTD");
        options.setPermittedValues("dtd", new String[]{"on", "off", "recover"}, "on");
        options.addRecognizedOption("ea", CommandLineOptions.TYPE_BOOLEAN,
                                    "Enable assertions");
        options.addRecognizedOption("expand", CommandLineOptions.TYPE_BOOLEAN,
                                    "Expand attribute defaults from DTD or Schema");
        options.addRecognizedOption("explain", CommandLineOptions.TYPE_FILENAME,
                                    "Display compiled expression tree and optimization decisions in human-readable form");
        options.addRecognizedOption("export", CommandLineOptions.TYPE_FILENAME,
                                    "Display compiled expression tree and optimization decisions for exportation");
        options.addRecognizedOption("ext", CommandLineOptions.TYPE_BOOLEAN,
                                    "Allow calls to Java extension functions and xsl:result-document");
        options.addRecognizedOption("im", CommandLineOptions.TYPE_QNAME | CommandLineOptions.VALUE_REQUIRED,
                                    "Name of initial mode");
        options.addRecognizedOption("init", CommandLineOptions.TYPE_CLASSNAME,
                                    "User-supplied net.sf.saxon.lib.Initializer class to initialize the Saxon Configuration");
        options.addRecognizedOption("it", CommandLineOptions.TYPE_QNAME,
                                    "Name of initial template");
        options.addRecognizedOption("jit", CommandLineOptions.TYPE_QNAME,
                                    "Just-in-time compilation");
        options.addRecognizedOption("l", CommandLineOptions.TYPE_BOOLEAN,
                                    "Maintain line numbers for source documents");
        options.addRecognizedOption("lib", CommandLineOptions.TYPE_FILENAME_LIST | CommandLineOptions.VALUE_REQUIRED,
                                    "List of file names of library packages used by the stylesheet");
        options.addRecognizedOption("license", CommandLineOptions.TYPE_BOOLEAN,
                                    "Check for local license file");
        options.addRecognizedOption("m", CommandLineOptions.TYPE_CLASSNAME,
                                    "Use named class to handle xsl:message output");
        options.addRecognizedOption("nogo", CommandLineOptions.TYPE_BOOLEAN,
                                    "Compile only, no evaluation");
        options.addRecognizedOption("now", CommandLineOptions.TYPE_DATETIME | CommandLineOptions.VALUE_REQUIRED,
                                    "Run with specified current date/time");
        options.addRecognizedOption("o", CommandLineOptions.TYPE_FILENAME | CommandLineOptions.VALUE_REQUIRED,
                                    "Use specified file for primary output");
        options.addRecognizedOption("opt", CommandLineOptions.TYPE_STRING | CommandLineOptions.VALUE_REQUIRED,
                                    "Enable/disable optimization options [-]cfgklmsvwx");
        options.addRecognizedOption("or", CommandLineOptions.TYPE_CLASSNAME | CommandLineOptions.VALUE_REQUIRED,
                                    "Use named OutputURIResolver class");
        options.addRecognizedOption("outval", CommandLineOptions.TYPE_ENUMERATION | CommandLineOptions.VALUE_REQUIRED,
                                    "Action when validation of output file fails");
        options.setPermittedValues("outval", new String[]{"recover", "fatal"}, null);
        options.addRecognizedOption("p", CommandLineOptions.TYPE_BOOLEAN,
                                    "Recognize query parameters in URI passed to doc()");

        options.addRecognizedOption("quit", CommandLineOptions.TYPE_BOOLEAN | CommandLineOptions.VALUE_REQUIRED,
                                    "Quit JVM if transformation fails");
        options.addRecognizedOption("r", CommandLineOptions.TYPE_CLASSNAME | CommandLineOptions.VALUE_REQUIRED,
                                    "Use named URIResolver class");
        options.addRecognizedOption("relocate", CommandLineOptions.TYPE_BOOLEAN,
                                    "Produce relocatable packages");
        options.addRecognizedOption("repeat", CommandLineOptions.TYPE_INTEGER | CommandLineOptions.VALUE_REQUIRED,
                                    "Run N times for performance measurement");
        options.addRecognizedOption("s", CommandLineOptions.TYPE_FILENAME | CommandLineOptions.VALUE_REQUIRED,
                                    "Source file for primary input");
        options.addRecognizedOption("sa", CommandLineOptions.TYPE_BOOLEAN,
                                    "Run in schema-aware mode");
        options.addRecognizedOption("scmin", CommandLineOptions.TYPE_FILENAME,
                                    "Pre-load schema in SCM format");
        options.addRecognizedOption("strip", CommandLineOptions.TYPE_ENUMERATION | CommandLineOptions.VALUE_REQUIRED,
                                    "Handling of whitespace text nodes in source documents");
        options.setPermittedValues("strip", new String[]{"none", "all", "ignorable"}, null);
        options.addRecognizedOption("t", CommandLineOptions.TYPE_BOOLEAN,
                                    "Display version and timing information, and names of output files");
        options.addRecognizedOption("target", CommandLineOptions.TYPE_ENUMERATION | CommandLineOptions.VALUE_REQUIRED,
                                    "Target Saxon edition for execution via -export");
        options.setPermittedValues("target", new String[]{"EE", "PE", "HE", "JS"}, null);
        options.addRecognizedOption("T", CommandLineOptions.TYPE_CLASSNAME,
                                    "Use named TraceListener class, or standard TraceListener");
        options.addRecognizedOption("TJ", CommandLineOptions.TYPE_BOOLEAN,
                                    "Debug binding and execution of extension functions");
        options.setPermittedValues("TJ", new String[]{"on", "off"}, "on");
        options.addRecognizedOption("Tlevel", CommandLineOptions.TYPE_STRING,
                                    "Level of detail for trace listener output");
        options.setPermittedValues("Tlevel", new String[]{"none", "low", "normal", "high"}, "normal");
        options.addRecognizedOption("Tout", CommandLineOptions.TYPE_FILENAME,
                                    "File for trace listener output");
        options.addRecognizedOption("TP", CommandLineOptions.TYPE_FILENAME,
                                    "Use profiling trace listener, with specified output file");
        options.addRecognizedOption("threads", CommandLineOptions.TYPE_INTEGER | CommandLineOptions.VALUE_REQUIRED,
                                    "Run stylesheet on directory of files divided in N threads");
        options.addRecognizedOption("tree", CommandLineOptions.TYPE_ENUMERATION | CommandLineOptions.VALUE_REQUIRED,
                                    "Use specified tree model for source documents");
        options.setPermittedValues("tree", new String[]{"linked", "tiny", "tinyc"}, null);
        options.addRecognizedOption("traceout", CommandLineOptions.TYPE_FILENAME | CommandLineOptions.VALUE_REQUIRED,
                                    "File for output of trace() and -T output");
        options.addRecognizedOption("u", CommandLineOptions.TYPE_BOOLEAN,
                                    "Interpret filename arguments as URIs");
        options.setPermittedValues("u", new String[]{"on", "off"}, "on");
        options.addRecognizedOption("val", CommandLineOptions.TYPE_ENUMERATION | CommandLineOptions.VALUE_REQUIRED,
                                    "Apply validation to source documents");
        options.setPermittedValues("val", new String[]{"strict", "lax"}, "strict");
        options.addRecognizedOption("versionmsg", CommandLineOptions.TYPE_BOOLEAN,
                                    "Output warning when stylesheet specifies version='1.0");
        options.addRecognizedOption("warnings", CommandLineOptions.TYPE_ENUMERATION | CommandLineOptions.VALUE_REQUIRED,
                                    "Handling of recoverable dynamic errors");
        options.setPermittedValues("warnings", new String[]{"silent", "recover", "fatal"}, null);
        options.addRecognizedOption("x", CommandLineOptions.TYPE_CLASSNAME | CommandLineOptions.VALUE_REQUIRED,
                                    "Use named XMLReader class for parsing source documents");
        options.addRecognizedOption("xi", CommandLineOptions.TYPE_BOOLEAN,
                                    "Expand XInclude directives in source documents");
        options.addRecognizedOption("xmlversion", CommandLineOptions.TYPE_ENUMERATION | CommandLineOptions.VALUE_REQUIRED,
                                    "Indicate whether XML 1.1 is supported");
        options.setPermittedValues("xmlversion", new String[]{"1.0", "1.1"}, null);
        options.addRecognizedOption("xsd", CommandLineOptions.TYPE_FILENAME_LIST | CommandLineOptions.VALUE_REQUIRED,
                                    "List of schema documents to be preloaded");
        options.addRecognizedOption("xsdversion", CommandLineOptions.TYPE_ENUMERATION | CommandLineOptions.VALUE_REQUIRED,
                                    "Indicate whether XSD 1.1 is supported");
        options.setPermittedValues("xsdversion", new String[]{"1.0", "1.1"}, null);
        options.addRecognizedOption("xsiloc", CommandLineOptions.TYPE_BOOLEAN,
                                    "Load schemas named in xsi:schemaLocation (default on)");
        options.addRecognizedOption("xsl", CommandLineOptions.TYPE_FILENAME | CommandLineOptions.VALUE_REQUIRED,
                                    "Main stylesheet file");
        options.addRecognizedOption("y", CommandLineOptions.TYPE_CLASSNAME | CommandLineOptions.VALUE_REQUIRED,
                                    "Use named XMLReader class for parsing stylesheet and schema documents");
        options.addRecognizedOption("?", CommandLineOptions.VALUE_PROHIBITED,
                                    "Display command line help text");

    }


    class TransformThread extends Thread {
        private File outputDir;
        private XsltExecutable sheet;
        private CommandLineOptions options;
        private Logger traceDestination;
        private List<Source> sources;
        private int start;

        TransformThread(int i, XsltExecutable st, List<Source> s, File out, CommandLineOptions opt, Logger trace) {
            start = i;
            sheet = st;
            sources = s;
            options = opt;
            outputDir = out;
            traceDestination = trace;
        }

        public long getStart() {
            return start;
        }

        @Override
        public void run() {
            try {
                processDirectory(sources, sheet, outputDir, options, traceDestination);
            } catch (Exception err) {
                err.printStackTrace();
            }
        }

    }


    /**
     * Support method for main program. This support method can also be invoked from subclasses
     * that support the same command line interface
     *
     * @param args    the command-line arguments
     * @param command the form of the command as written by the user. Not used, retained for backwards compatibility
     */

    public void doTransform(String args[], String command) {

        Configuration config;
        String sourceFileName = null;
        String styleFileName = null;
        String packageCatalogName = null;
        File outputFile = null;
        String outputFileName = null;
        boolean useAssociatedStylesheet;
        boolean wholeDirectory = false;
        boolean dtdValidation = false;
        String styleParserName = null;
        boolean explain = false;
        boolean export = false;
        String explainOutputFileName = null;
        String exportOutputFileName = null;
        String additionalSchemas = null;
        Logger traceDestination = null;             // project:preconditions
        TraceListener traceListener = null;
        boolean closeTraceDestination = false;
        TransformThread[] th = null;
        int threadCount = 0;
        boolean jit = true;

        CommandLineOptions options = new CommandLineOptions();
        setPermittedOptions(options);
        try {
            options.setActualOptions(args);
        } catch (XPathException err) {
            quit(err.getMessage(), 2);
        }


        schemaAware = false;
        String configFile = options.getOptionValue("config");
        if (configFile != null) {
            try {
                config = Configuration.readConfiguration(new StreamSource(configFile));
                initializeConfiguration(config);
                processor = new Processor(config);
                schemaAware = config.isLicensedFeature(Configuration.LicenseFeature.ENTERPRISE_XSLT);
            } catch (XPathException e) {
                quit(e.getMessage(), 2);
            }
        }

        if (processor == null && !schemaAware) {
            schemaAware = options.testIfSchemaAware();
        }

        if (processor == null) {
            processor = new Processor(true);
            config = processor.getUnderlyingConfiguration();
            initializeConfiguration(config);
            config.setVersionWarning(true);  // unless suppressed by command line options
            try {
                setFactoryConfiguration(schemaAware, null);
                CompilerInfo defaultCompilerInfo = config.getDefaultXsltCompilerInfo();
                if (schemaAware) {
                    defaultCompilerInfo.setSchemaAware(true);
                } else {
                    defaultCompilerInfo.setSchemaAware(false);
                }
            } catch (Exception err) {
                err.printStackTrace();
                quit(err.getMessage(), 2);
            }
        }

        config = processor.getUnderlyingConfiguration();

        // Process the named options

        try {

            // Apply those options which simply update the Configuration

            options.applyToConfiguration(processor);

            // Now create the XsltCompiler (it will pick up options from the configuration)

            compiler = processor.newXsltCompiler();

            // Apply options that are processed locally

            allowExit = !"off".equals(options.getOptionValue("quit"));

            useAssociatedStylesheet = "on".equals(options.getOptionValue("a"));

            String value = options.getOptionValue("explain");
            if (value != null) {
                explain = true;
                processor.setConfigurationProperty(FeatureKeys.TRACE_OPTIMIZER_DECISIONS, true);
                jit = false;
                compiler.setJustInTimeCompilation(jit);
                if (!"".equals(value)) {
                    explainOutputFileName = value;
                }
            }
            value = options.getOptionValue("export");
            if (value != null) {
                export = true;
                jit = false;
                compiler.setJustInTimeCompilation(jit);
                if (!"".equals(value)) {
                    exportOutputFileName = value;
                }
            }

            value = options.getOptionValue("target");
            if (value != null) {
                compiler.setTargetEdition(value);
            }
            value = options.getOptionValue("relocate");
            if (value != null && !"off".equals(value)) {
                compiler.setRelocatable(true);
            }

            value = options.getOptionValue("jit");
            if (value != null) {
                if ("on".equals(value) && exportOutputFileName == null && run) {
                    //TODO the combination of the options jit, nogo and export should give an warning
                    jit = true;
                } else if ("off".equals(value)) {
                    jit = false;
                }
                compiler.setJustInTimeCompilation(jit);

            }

            value = options.getOptionValue("lib");
            if (value != null) {
                StringTokenizer st = new StringTokenizer(value, File.pathSeparator);
                Set<File> packs = new HashSet<File>();
                while (st.hasMoreTokens()) {
                    String n = st.nextToken();
                    packs.add(new File(n));
                }
                PackageLibrary lib = null;
                try {
                    lib = new PackageLibrary(compiler.getUnderlyingCompilerInfo(), packs);
                } catch (XPathException e) {
                    quit(e.getMessage(), 2);
                }
                compiler.getUnderlyingCompilerInfo().setPackageLibrary(lib);
            }

            value = options.getOptionValue("o");
            if (value != null) {
                outputFileName = value;
            }

            value = options.getOptionValue("nogo");
            if (value != null) {
                run = false;
                jit = false;
            }

            value = options.getOptionValue("p");
            if ("on".equals(value)) {
                config.setParameterizedURIResolver();
                useURLs = true;
            }

            value = options.getOptionValue("repeat");
            if (value != null) {
                try {
                    repeat = Integer.parseInt(value);
                } catch (NumberFormatException err) {
                    badUsage("Bad number after -repeat");
                }
            }

            value = options.getOptionValue("s");
            if (value != null) {
                sourceFileName = value;
            }

            value = options.getOptionValue("threads");
            if (value != null) {
                threadCount = Integer.parseInt(value);
            }

            value = options.getOptionValue("t");
            if (value != null) {
                System.err.println(config.getProductTitle());
                System.err.println(Version.platform.getPlatformVersion());
                processor.setConfigurationProperty(FeatureKeys.TIMING, true);
                showTime = true;
            }

            value = options.getOptionValue("T");
            if (value != null) {
                if ("".equals(value)) {
                    traceListener = new net.sf.saxon.trace.XSLTTraceListener();
                } else {
                    traceListener = config.makeTraceListener(value);
                }
                processor.setConfigurationProperty(FeatureKeys.TRACE_LISTENER, traceListener);
                processor.setConfigurationProperty(FeatureKeys.LINE_NUMBERING, true);

                value = options.getOptionValue("Tlevel");
                if (value != null && traceListener instanceof AbstractTraceListener) {
                    if (value.equals("none")) {
                        ((AbstractTraceListener)traceListener).setLevelOfDetail(0);
                    } else if (value.equals("low")) {
                        ((AbstractTraceListener) traceListener).setLevelOfDetail(1);
                    } else if (value.equals("normal")) {
                        ((AbstractTraceListener) traceListener).setLevelOfDetail(2);
                    } else if (value.equals("high")) {
                        ((AbstractTraceListener) traceListener).setLevelOfDetail(3);
                    }
                }
            }

            value = options.getOptionValue("TP");
            if (value != null) {
                traceListener = new TimingTraceListener();
                processor.setConfigurationProperty(FeatureKeys.TRACE_LISTENER, traceListener);
                processor.setConfigurationProperty(FeatureKeys.LINE_NUMBERING, true);
                compiler.getUnderlyingCompilerInfo().setCodeInjector(new TimingCodeInjector());
                if (value.length() > 0) {
                    traceListener.setOutputDestination(
                            new StandardLogger(new File(value)));
                }
            }

            value = options.getOptionValue("traceout");
            if (value == null) {
                if (traceDestination == null) {
                    traceDestination = config.getLogger();
                }
            } else {
                if (value.equals("#err")) {
                    traceDestination = new StandardLogger();
                } else if (value.equals("#out")) {
                    traceDestination = new StandardLogger(System.out);
                } else if (value.equals("#null")) {
                    traceDestination = null;
                } else {
                    traceDestination = new StandardLogger(new File(value));
                    closeTraceDestination = true;
                }
            }

            value = options.getOptionValue("u");
            if (value != null) {
                useURLs = "on".equals(value);
            }

            value = options.getOptionValue("x");
            if (value != null) {
                sourceParserName = value;
                processor.setConfigurationProperty(FeatureKeys.SOURCE_PARSER_CLASS, sourceParserName);
            }

            value = options.getOptionValue("xsd");
            if (value != null) {
                additionalSchemas = value;
            }

            value = options.getOptionValue("xsdversion");
            if (value != null) {
                processor.setConfigurationProperty(FeatureKeys.XSD_VERSION, value);
            }

            value = options.getOptionValue("xsl");
            if (value != null) {
                styleFileName = value;
            }

            value = options.getOptionValue("y");
            if (value != null) {
                styleParserName = value;
                processor.setConfigurationProperty(FeatureKeys.STYLE_PARSER_CLASS, value);
            }

            value = options.getOptionValue("?");
            if (value != null) {
                badUsage("");
            }


            // Check for a license if appropriate

            if (!config.getEditionCode().equals("HE")) {
                String lic = options.getOptionValue("license");
                if (lic == null || "on".equals(lic)) {
                    config.displayLicenseMessage();
                } else {
                    config.disableLicensing();
                }
            }


            //compilerInfo.setRecoveryPolicy(config.getRecoveryPolicy());

            // Apply options defined locally in a subclass

            applyLocalOptions(options, config);

            // Check consistency of selected options

            if (options.getOptionValue("it") != null && useAssociatedStylesheet) {
                badUsage("-it and -a options cannot be used together");
            }

            List<String> positional = options.getPositionalOptions();
            int currentPositionalOption = 0;

            if (run && options.getOptionValue("it") == null && sourceFileName == null) {
                if (positional.size() == currentPositionalOption) {
                    badUsage("No source file name");
                }
                sourceFileName = positional.get(currentPositionalOption++);
            }

            if (!useAssociatedStylesheet && styleFileName == null) {
                if (positional.size() == currentPositionalOption) {
                    badUsage("No stylesheet file name");
                }
                styleFileName = positional.get(currentPositionalOption++);
            }

            if (currentPositionalOption < positional.size()) {
                badUsage("Unrecognized option: " + positional.get(currentPositionalOption));
            }

//            config.displayLicenseMessage();
//            if (schemaAware) {
//                config.checkLicensedFeature(Configuration.LicenseFeature.ENTERPRISE_XSLT, "schema-aware XSLT", -1);
//            }

            if (traceListener instanceof AbstractTraceListener && traceDestination != null) {
                traceListener.setOutputDestination(traceDestination);
            }

            value = options.getOptionValue("scmin");
            if (value != null) {
                config.importComponents(new StreamSource(value));
            }

            if (additionalSchemas != null) {
                CommandLineOptions.loadAdditionalSchemas(config, additionalSchemas);
            }

            List<Source> sources = new ArrayList<Source>();
            if (sourceFileName != null) {
                boolean useSAXSource = sourceParserName != null || dtdValidation;
                wholeDirectory = CommandLineOptions.loadDocuments(sourceFileName, useURLs, processor, useSAXSource, sources);

                sources = preprocess(sources);
                if (wholeDirectory) {
                    if (outputFileName == null) {
                        quit("To process a directory, -o must be specified", 2);
                    } else if (outputFileName.equals(sourceFileName)) {
                        quit("Output directory must be different from input", 2);
                    } else {
                        outputFile = new File(outputFileName);
                        if (!outputFile.isDirectory()) {
                            quit("Input is a directory, but output is not", 2);
                        }
                    }
                }
            }

            if (outputFileName != null && !wholeDirectory) {
                outputFile = new File(outputFileName);
                if (outputFile.isDirectory()) {
                    quit("Output is a directory, but input is not", 2);
                }
            }

            if (useAssociatedStylesheet) {
                if (wholeDirectory) {
                    processDirectoryAssoc(sources, outputFile, options, traceDestination);
                } else {
                    processFileAssoc(sources.get(0), null, outputFile, options,
                                     traceDestination);
                }
            } else {

                long startTime = now();

                boolean isURI = useURLs || CommandLineOptions.isImplicitURI(styleFileName);
                XsltExecutable sheet = null;

                Source styleSource = null;
                XMLReader styleParser = null;

                if (isURI) {
                    styleSource = config.getURIResolver().resolve(styleFileName, null);
                    if (styleSource == null) {
                        styleSource = config.getSystemURIResolver().resolve(styleFileName, null);
                    }
                } else if (styleFileName.equals("-")) {
                    // take input from stdin
                    if (styleParserName == null) {
                        styleSource = new StreamSource(System.in);
                    } else if (Version.platform.isJava()) {
                        styleParser = config.getStyleParser();
                        styleSource = new SAXSource(styleParser, new InputSource(System.in));
                    } else {
                        styleSource = new StreamSource(System.in);
                    }
                } else {
                    PackageLibrary library = config.getDefaultXsltCompilerInfo().getPackageLibrary();
                    PackageDetails details = library.findDetailsForAlias(styleFileName);
                    if (details != null) {
                        XsltPackage pack = compiler.obtainPackageWithAlias(styleFileName);
                        sheet = pack.link();
                    } else {
                        File sheetFile = new File(styleFileName);
                        if (!sheetFile.exists()) {
                            quit("Stylesheet file " + sheetFile + " does not exist", 2);
                        }
                        if (styleParserName == null) {
                            styleSource = new StreamSource(sheetFile.toURI().toString());
                        } else {
                            InputSource eis = new InputSource(sheetFile.toURI().toString());
                            styleParser = config.getStyleParser();
                            styleSource = new SAXSource(styleParser, eis);
                        }
                    }
                }

                if (styleSource == null && sheet == null) {
                    quit("URIResolver for stylesheet file must return a Source", 2);
                }


                if (sheet == null) {
                    for (int j = 0; j < repeat; j++) {
                        // Repeat loop is to get reliable performance data

                        options.applyStaticParams(compiler);
                        ///////////////////////////////////////
                        sheet = compiler.compile(styleSource);
                        ///////////////////////////////////////
                        if (schemaAware) {
                            int licenseId = sheet.getUnderlyingCompiledStylesheet().
                                    getTopLevelPackage().getLocalLicenseId();
                            config.checkLicensedFeature(Configuration.LicenseFeature.ENTERPRISE_XSLT, "schema-aware XSLT", licenseId);
                        }
                        if (styleParser != null) {
                            config.reuseStyleParser(styleParser);
                            // pointless, because the Configuration won't be used again; but we want to set a good example
                        }
                        if (showTime) {
                            long endTime = now();
                            System.err.println("Stylesheet compilation time: " + CommandLineOptions.showExecutionTimeNano(endTime - startTime));
                            startTime = endTime;
                        }
                    }
                }

                if (explain) {
                    Serializer out;
                    if (explainOutputFileName == null) {
                        out = processor.newSerializer(System.err);
                    } else {
                        out = processor.newSerializer(new File(explainOutputFileName));
                    }
                    out.setOutputProperty(Serializer.Property.METHOD, "xml");
                    out.setOutputProperty(Serializer.Property.INDENT, "yes");
                    out.setOutputProperty(Serializer.Property.OMIT_XML_DECLARATION, "yes");
                    out.setOutputProperty(Serializer.Property.SAXON_INDENT_SPACES, "2");
                    sheet.explain(out);
                }
                if (export) {
                    sheet.export(new FileOutputStream(exportOutputFileName));
                }

                if (run) {

                    if (wholeDirectory) {
                        if ((threadCount > 0) && (sources.size() > 1)) {
                            if (threadCount > sources.size()) {
                                threadCount = sources.size();
                            }

                            //calculate sources per thread
                            int sourcesPerThread = (int) Math.floor(sources.size() / threadCount);

                            //split remainder of sources amongst rem threads
                            int rem = sources.size() % threadCount;
                            th = new TransformThread[threadCount];
                            // long elapsedTime = System.nanoTime();
                            for (int i = 0, j = 0, z = 0; i < sources.size(); j++, i += sourcesPerThread + z) {
                                z = j < rem ? 1 : 0;  //split remainder of sources amongst rem threads
                                th[j] = new TransformThread(i, sheet, sources.subList(i, i + sourcesPerThread + z), outputFile, options, traceDestination);
                                th[j].start();
                            }
                            for (TransformThread aTh : th) {
                                aTh.join();
                            }
                            // elapsedTime = System.nanoTime() - elapsedTime;
                            // System.out.println("Total Elapsed Time: "+elapsedTime/1e6 + "ms");


                        } else {
                            processDirectory(sources, sheet, outputFile, options, traceDestination);
                        }
                    } else {
                        Source source = sources == null || sources.isEmpty() ? null : sources.get(0);
                        processFile(source, sheet, outputFile, options, traceDestination);
                    }
                    if (closeTraceDestination) {
                        traceDestination.close();
                    }
                }
            }
        } catch (TerminationException err) {
            quit(err.getMessage(), 1);
        } catch (SaxonApiException err) {
            //err.printStackTrace();
            quit(err.getMessage(), 2);
        } catch (TransformerException err) {
            //err.printStackTrace();
            quit("Transformation failed: " + err.getMessage(), 2);
        } catch (TransformerFactoryConfigurationError err) {
            //err.printStackTrace();
            quit("Transformation failed: " + err.getMessage(), 2);
        } catch (LicenseException err) {
            quit("Transformation failed: " + err.getMessage(), 2);
        } catch (Exception err2) {
            err2.printStackTrace();
            quit("Fatal error during transformation: " + err2.getClass().getName() + ": " +
                         (err2.getMessage() == null ? " (no message)" : err2.getMessage()), 2);
        }
    }


    /**
     * Customisation hook called immediately after the Configuration
     * object is instantiated. The intended purpose of this hook is to allow
     * a subclass to supply an OEM license key programmatically, but it can also
     * be used for other initialization of the Configuration. This method is
     * called before analyzing the command line options, so configuration settings
     * made at this stage may be overridden when the command line options are processed.
     * However, if a configuration file is used, the settings defined in the configuration
     * file will have been applied.
     *
     * @param config the Configuration object
     */

    protected void initializeConfiguration(Configuration config) {
        // no action: provided for subclasses to override
    }

    /**
     * Customisation hook called immediately after the Configuration
     * object is instantiated. This hook is retained for backwards
     * compatibility but it is recommended to implement {@link #initializeConfiguration}
     * in preference. This method is called after {@link #initializeConfiguration},
     * but only if the configuration was not created using a configuration file.
     * The default implementation does nothing.
     *
     * @param schemaAware True if the transformation is to be schema-aware
     * @param className   Always null.
     * @throws LicenseException can be thrown if there is no valid license available
     */

    public void setFactoryConfiguration(boolean schemaAware, String className) throws LicenseException {
        // no action
    }

    /**
     * Customisation hook: apply options defined locally in a subclass. This method
     * allows a subclass to recognize and implement command line options that are not recognized
     * by the superclass. To prevent Saxon rejecting such options as errors, the method
     * {@link #setPermittedOptions} must be overridden in the subclass to add details of
     * options recognized in the subclass.
     *
     * @param options the CommandLineOptions. This will contain details of all the options
     *                that were specified on the command line. Those that are recognized by the standard Saxon
     *                command line interface will already have been processed; other options can now be processed
     *                by the subclass.
     * @param config  the Saxon Configuration
     */

    protected void applyLocalOptions(CommandLineOptions options, Configuration config) {
        // no action: provided for subclasses to override
    }


    /**
     * Preprocess the list of sources. This method exists so that it can be
     * overridden in a subclass, typically to handle kinds of Source implemented as
     * extensions to the basic Saxon capability
     *
     * @param sources the list of Source objects
     * @return a revised list of Source objects
     * @throws XPathException if a Source object is not recognized
     */

    public List<Source> preprocess(List<Source> sources) throws XPathException {
        return sources;
    }

    /**
     * Get the configuration.
     *
     * @return the Saxon configuration
     */

    protected Configuration getConfiguration() {
        return processor.getUnderlyingConfiguration();
    }

    /**
     * Exit with a message
     *
     * @param message The message to be output
     * @param code    The result code to be returned to the operating
     *                system shell
     */

    protected void quit(String message, int code) {
        System.err.println(message);
        if (allowExit) {
            System.exit(code);
        } else {
            throw new RuntimeException(message);
        }
    }

    /**
     * Process each file in the source directory using its own associated stylesheet
     *
     * @param sources          The sources in the directory to be processed
     * @param outputDir        The directory in which output files are to be
     *                         created
     * @param options          Command line options
     * @param traceDestination output destination for fn:trace() calls
     * @throws Exception when any error occurs during a transformation
     */

    private void processDirectoryAssoc(List<Source> sources, File outputDir,
                                       CommandLineOptions options, Logger traceDestination)
            throws Exception {

        int failures = 0;
        for (Source source : sources) {
            String localName = getLocalFileName(source);
            try {
                processFileAssoc(source, localName, outputDir, options, traceDestination);
            } catch (SaxonApiException err) {
                failures++;
                System.err.println("While processing " + localName +
                                           ": " + err.getMessage() + '\n');
            }
        }
        if (failures > 0) {
            throw new XPathException(failures + " transformation" +
                                             (failures == 1 ? "" : "s") + " failed");
        }
    }

    /**
     * Make an output file in the output directory, with filename extension derived from the
     * media-type produced by the stylesheet
     *
     * @param directory The directory in which the file is to be created
     * @param localName The local name of the file within the
     *                  directory, excluding the file type suffix
     * @param sheet     The Templates object identifying the stylesheet -
     *                  used to determine the output method, and hence the suffix to be
     *                  used for the filename
     * @return The newly created file
     */

    private File makeOutputFile(File directory, String localName, XsltExecutable sheet) {
        String mediaType = sheet.getUnderlyingCompiledStylesheet().getDefaultOutputProperties().getProperty(OutputKeys.MEDIA_TYPE);
        String suffix = ".xml";
        if ("text/html".equals(mediaType)) {
            suffix = ".html";
        } else if ("text/plain".equals(mediaType)) {
            suffix = ".txt";
        }
        String prefix = localName;
        if (localName.endsWith(".xml") || localName.endsWith(".XML")) {
            prefix = localName.substring(0, localName.length() - 4);
        }
        return new File(directory, prefix + suffix);
    }


    /**
     * Process a single source file using its associated stylesheet(s)
     *
     * @param sourceInput      Identifies the source file to be transformed
     * @param localName        The local name of the file within the
     *                         directory, excluding the file type suffix
     * @param outputFile       The output file to contain the results of the
     *                         transformation
     * @param options          Command line options
     * @param traceDestination Destination for trace output
     * @throws SaxonApiException If the transformation fails
     */

    private void processFileAssoc(Source sourceInput, String localName, File outputFile,
                                  CommandLineOptions options, Logger traceDestination)
            throws SaxonApiException {
        if (showTime) {
            System.err.println("Processing " + sourceInput.getSystemId() + " using associated stylesheet");
        }
        long startTime = now();

        XdmNode sourceDoc = processor.newDocumentBuilder().build(sourceInput);

        Source style = compiler.getAssociatedStylesheet(sourceDoc.asSource(), null, null, null);
        XsltExecutable sheet = compiler.compile(style);

        if (showTime) {
            System.err.println("Prepared associated stylesheet " + style.getSystemId());
        }

        Xslt30Transformer transformer = newTransformer(sheet, options, traceDestination);

        File outFile = outputFile;
        if (outFile != null && outFile.isDirectory()) {
            outFile = makeOutputFile(outFile, localName, sheet);
        }

        Serializer serializer =
                outputFile == null ?
                        transformer.newSerializer(System.out) :
                        transformer.newSerializer(outFile);
        options.setSerializationParams(serializer);

        transformer.setGlobalContextItem(sourceDoc);
        transformer.applyTemplates(sourceDoc, serializer);


        if (showTime) {
            long endTime = now();
            System.err.println("Execution time: " + CommandLineOptions.showExecutionTimeNano(endTime - startTime));
        }
    }

    /**
     * Create a new Transformer. This method is protected so it can be overridden in a subclass, allowing additional
     * options to be set on the Transformer
     *
     * @param sheet            The XsltExecutable object representing the compiled stylesheet
     * @param options          The commmand line options
     * @param traceDestination destination for trace output
     * @return the newly constructed Controller to be used for the transformation
     * @throws SaxonApiException if any error occurs
     */

    protected Xslt30Transformer newTransformer(
            XsltExecutable sheet, CommandLineOptions options, Logger traceDestination) throws SaxonApiException {
        final Xslt30Transformer transformer = sheet.load30();
        final Map<QName, XdmValue> params = new HashMap<QName, XdmValue>();
        options.setParams(processor, new CommandLineOptions.ParamSetter() {
            public void setParam(QName qName, XdmValue value) {
                params.put(qName, value);
            }
        });
        transformer.setStylesheetParameters(params);
        transformer.setTraceFunctionDestination(traceDestination);
        String initialMode = options.getOptionValue("im");
        if (initialMode != null) {
            transformer.setInitialMode(QName.fromClarkName(initialMode));
        }

        String now = options.getOptionValue("now");
        if (now != null) {
            try {
                DateTimeValue currentDateTime = (DateTimeValue) DateTimeValue.makeDateTimeValue(
                        now, getConfiguration().getConversionRules()).asAtomic();
                transformer.getUnderlyingController().setCurrentDateTime(currentDateTime);
            } catch (XPathException e) {
                throw new SaxonApiException("Failed to set current time", e);
            }
        }
        // Code to enable/disable assertions at run-time is relevant only when we're running pre-compiled packages
        if ("on".equals(options.getOptionValue("ea"))) {
            transformer.getUnderlyingController().setAssertionsEnabled(true);
        } else if ("off".equals(options.getOptionValue("ea"))) {
            transformer.getUnderlyingController().setAssertionsEnabled(true);
        }
        return transformer;
    }

    /**
     * Get current time in nanoseconds
     *
     * @return the current time in nanoseconds (since VM startup)
     */

    protected static long now() {
        return System.nanoTime();
    }

    /**
     * Process each file in the source directory using the same supplied stylesheet
     *
     * @param sources          The sources in the directory to be processed
     * @param sheet            The Templates object identifying the stylesheet
     * @param outputDir        The directory in which output files are to be
     *                         created
     * @param options          Command line options
     * @param traceDestination Destination for output from fn:trace() calls
     * @throws SaxonApiException when any error occurs during a
     *                           transformation
     */

    private void processDirectory(List<Source> sources, XsltExecutable sheet, File outputDir, CommandLineOptions options,
                                  Logger traceDestination)
            throws SaxonApiException {
        int failures = 0;
        for (Source source : sources) {
            String localName = getLocalFileName(source);
            try {
                File outputFile = makeOutputFile(outputDir, localName, sheet);
                processFile(source, sheet, outputFile, options, traceDestination);
            } catch (SaxonApiException err) {
                failures++;
                System.err.println("While processing " + localName + ": " + err.getMessage() + '\n');
            }
        }
        if (failures > 0) {
            throw new SaxonApiException(failures + " transformation" +
                                                (failures == 1 ? "" : "s") + " failed");
        }
    }

    private static String getLocalFileName(Source source) {
        try {
            String path = new URI(source.getSystemId()).getPath();
            while (true) {
                int sep = path.indexOf('/');
                if (sep < 0) {
                    return path;
                } else {
                    path = path.substring(sep + 1);
                }
            }
        } catch (URISyntaxException err) {
            throw new IllegalArgumentException(err.getMessage());
        }
    }

    /**
     * Process a single file using a supplied stylesheet
     *
     * @param source           The source XML document to be transformed (maybe null if an initial template
     *                         is specified)
     * @param sheet            The Templates object identifying the stylesheet
     * @param outputFile       The output file to contain the results of the
     *                         transformation
     * @param options          The command line options
     * @param traceDestination Destination for output from fn:trace() function
     * @throws SaxonApiException If the transformation fails
     */

    protected void processFile(/*@Nullable*/ Source source, XsltExecutable sheet, /*@Nullable*/ File outputFile, CommandLineOptions options,
                               Logger traceDestination)
            throws SaxonApiException {

        long totalTime = 0;
        int runs = 0;
        int halfway = repeat / 2 - 1;
        for (int r = 0; r < repeat; r++) {      // repeat is for internal testing/timing
            if (showTime) {
                String msg = "Processing ";
                if (source != null) {
                    msg += source.getSystemId();
                } else {
                    msg += " (no source document)";
                }
                String initialMode = options.getOptionValue("im");
                if (initialMode != null) {
                    msg += " initial mode = " + initialMode;
                }
                String initialTemplate = options.getOptionValue("it");
                if (initialTemplate != null) {
                    msg += " initial template = " + (initialTemplate.isEmpty() ? "xsl:initial-template" : initialTemplate);
                }
                System.err.println(msg);
            }
            long startTime = now();
            if (r == halfway) {
                runs = 0;
                totalTime = 0;
            }
            runs++;

            if (r < halfway) {
                traceDestination = null;
            }
            Xslt30Transformer transformer = newTransformer(sheet, options, traceDestination);
            Serializer serializer =
                    outputFile == null ?
                            transformer.newSerializer(System.out) :
                            transformer.newSerializer(outputFile);
            options.setSerializationParams(serializer);

            boolean buildResultTree;
            Properties props = serializer.getCombinedOutputProperties();
            String buildTreeProperty = props.getProperty(SaxonOutputKeys.BUILD_TREE);
            if ("yes".equals(buildTreeProperty)) {
                buildResultTree = true;
            } else if ("no".equals(buildTreeProperty)) {
                buildResultTree = false;
            } else {
                String method = props.getProperty(OutputKeys.METHOD);
                buildResultTree = !("json".equals(method) || "adaptive".equals(method));
            }

            String initialTemplate = options.getOptionValue("it");

            if (source != null) {
                PreparedStylesheet pss = sheet.getUnderlyingCompiledStylesheet();
                GlobalContextRequirement requirement = pss.getGlobalContextRequirement();
                boolean buildSourceTree;
                if (requirement == null) {
                    buildSourceTree = initialTemplate != null ||
                            !transformer.getUnderlyingController().getInitialMode().isDeclaredStreamable();
                } else {
                    buildSourceTree = !requirement.isAbsentFocus();
                }
                if (buildSourceTree) {
                    DocumentBuilder builder = processor.newDocumentBuilder();
                    StylesheetPackage top = pss.getTopLevelPackage();
                    if (!top.isStripsTypeAnnotations()) {
                        int validationMode = getConfiguration().getSchemaValidationMode();
                        if (validationMode == Validation.STRICT) {
                            builder.setSchemaValidator(processor.getSchemaManager().newSchemaValidator());
                        } else if (validationMode == Validation.LAX) {
                            SchemaValidator validator = processor.getSchemaManager().newSchemaValidator();
                            validator.setLax(true);
                            builder.setSchemaValidator(validator);
                        }
                    }
                    builder.setWhitespaceStrippingPolicy(sheet.getWhitespaceStrippingPolicy());
                    XdmNode node = builder.build(source);
                    transformer.setGlobalContextItem(node, true);
                    source = node.asSource();
                }
            }

            //if (r != repeat - 1) {
            /*if (r < halfway) {
                transformer.setTraceListener(null);
                transformer.setTraceFunctionDestination(null);
            }*/

            if (initialTemplate != null) {
                QName initialTemplateName;
                if (initialTemplate.isEmpty()) {
                    initialTemplateName = new QName("xsl", NamespaceConstant.XSLT, "initial-template");
                } else {
                    initialTemplateName = QName.fromClarkName(initialTemplate);
                }
                if (buildResultTree) {
                    transformer.callTemplate(initialTemplateName, serializer);
                } else {
                    XdmValue result = transformer.callTemplate(initialTemplateName);
                    serializer.serializeXdmValue(result);
                }
            } else {
                if (buildResultTree) {
                    transformer.applyTemplates(source, serializer);
                } else {
                    XdmValue result = transformer.applyTemplates(source);
                    serializer.serializeXdmValue(result);
                }
            }

            long endTime = now();
            totalTime += endTime - startTime;
            if (showTime) {
                System.err.println("Execution time: " + CommandLineOptions.showExecutionTimeNano(endTime - startTime));
                System.err.println("Memory used: " + String.format("%,d", Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()));
                if (repeat > 1) {
                    System.err.println("-------------------------------");
                    Runtime.getRuntime().gc();
                }
            }
            if (repeat == 999999 && totalTime > 60000) {
                break;
            }
        }
        if (repeat > 1) {
            System.err.println("*** Average execution time over last " + runs + " runs: " +
                                       CommandLineOptions.showExecutionTimeNano(totalTime / runs));
        }
    }


    /**
     * Report incorrect usage of the command line, with a list of the options and arguments that are available
     *
     * @param message The error message
     */
    protected void badUsage(String message) {
        if (!"".equals(message)) {
            System.err.println(message);
        }
        if (!showTime) {
            System.err.println(getConfiguration().getProductTitle());
        }
        System.err.println("Usage: see http://www.saxonica.com/documentation/index.html#!using-xsl/commandline");
        System.err.println("Format: " + getClass().getName() + " options params");
        CommandLineOptions options = new CommandLineOptions();
        setPermittedOptions(options);
        System.err.println("Options available:" + options.displayPermittedOptions());
        System.err.println("Use -XYZ:? for details of option XYZ");
        System.err.println("Params: ");
        System.err.println("  param=value           Set stylesheet string parameter");
        System.err.println("  +param=filename       Set stylesheet document parameter");
        System.err.println("  ?param=expression     Set stylesheet parameter using XPath");
        System.err.println("  !param=value          Set serialization parameter");
        if (allowExit) {
            if ("".equals(message)) {
                System.exit(0);
            } else {
                System.exit(2);
            }
        } else {
            throw new RuntimeException(message);
        }
    }


}

