////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.testdriver;

import net.sf.saxon.Configuration;
import net.sf.saxon.Controller;
import net.sf.saxon.lib.*;
import net.sf.saxon.s9api.*;
import net.sf.saxon.trace.XSLTTraceListener;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AtomicValue;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.SourceLocator;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * This class runs the W3C XSLT Test Suite, driven from the test catalog.
 */
public class Xslt30TestSuiteDriverHE extends TestDriver {


    public Xslt30TestSuiteDriverHE() {
        spec = Spec.XT30;
        setDependencyData();
    }


    public static void main(String[] args) throws Exception {
        if (args.length == 0 || args[0].equals("-?")) {
            usage();
        }
        new Xslt30TestSuiteDriverHE().go(args);
    }

    protected static void usage() {
        System.err.println("java com.saxonica.testdriver.Xslt30TestSuiteDriver[HE] testsuiteDir catalog [-o:resultsdir] [-s:testSetName]" +
                                   " [-t:testNamePattern] [-bytecode:on|off|debug] [-export] [-tree] [-lang] [-save] [-streaming:off|std|ext]" +
                                   " [-xt30:on] [-T] [-js]");
    }

    @Override
    public String catalogNamespace() {
        return "http://www.w3.org/2012/10/xslt-test-catalog";
    }

    @Override
    protected void writeResultFilePreamble(Processor processor, XdmNode catalog)
            throws Exception {
        super.writeResultFilePreamble(processor, catalog);
    }

    @Override
    public void processSpec(String specStr) {
        if (specStr.equals("XT10")) {
            spec = Spec.XT10;
        } else if (specStr.equals("XT20")) {
            spec = Spec.XT20;
        } else if (specStr.equals("XT30")) {
            spec = Spec.XT30;
        } else {
            throw new IllegalArgumentException("Unknown spec " + specStr);
        }
        resultsDoc = new Xslt30TestReport(this, spec);
        // No action: always use XSLT
    }

    @Override
    protected void createGlobalEnvironments(XdmNode catalog, XPathCompiler xpc) throws SaxonApiException {
        for (XdmItem env : xpc.evaluate("//environment", catalog)) {
            Environment.processEnvironment(
                    this, xpc, env, globalEnvironments, localEnvironments.get("default"));
        }
    }

    protected boolean isSlow(String testName) {
        return testName.startsWith("regex-classes")
                || testName.equals("normalize-unicode-008")
        || (testName.equals("function-1031") && driverProc.getSaxonEdition().equals("HE"));
              //  || testName.equals("catalog-005");
    }


    @Override
    protected void runTestCase(XdmNode testCase, XPathCompiler xpath) throws SaxonApiException {

        final TestOutcome outcome = new TestOutcome(this);
        String testName = testCase.getAttributeValue(new QName("name"));
        String testSetName = testCase.getParent().getAttributeValue(new QName("name"));

        if (exceptionsMap.containsKey("$" + testSetName)) {
            notrun++;
            XdmNode exceptionElement = exceptionsMap.get("$" + testSetName);
            resultsDoc.writeTestcaseElement(testName, exceptionElement.getAttributeValue(new QName("report")),
                    exceptionElement.axisIterator(Axis.CHILD, new QName("reason")).next().getStringValue());
            return;
        }

        if (exceptionsMap.containsKey(testName)) {
            notrun++;
            XdmNode exceptionElement = exceptionsMap.get(testName);
            resultsDoc.writeTestcaseElement(testName, exceptionElement.getAttributeValue(new QName("report")),
                    exceptionElement.axisIterator(Axis.CHILD, new QName("reason")).next().getStringValue());
            return;
        }

        if (isSlow(testName)) {
            notrun++;
            resultsDoc.writeTestcaseElement(testName, "tooBig", "requires excessive resources");
            return;
        }

        XdmValue specAtt = xpath.evaluateSingle("(/test-set/dependencies/spec/@value, ./dependencies/spec/@value)[last()]", testCase);
        String spec = specAtt == null ? "XSLT10+" : specAtt.toString();

        final Environment env = getEnvironment(testCase, xpath);
        if (env == null) {
            resultsDoc.writeTestcaseElement(testName, "notRun", "test catalog error");
            notrun++;
            return;
        }
        for (XdmItem dep : xpath.evaluate("(/test-set/dependencies/*, ./dependencies/*)", testCase)) {
            if (!ensureDependencySatisfied((XdmNode) dep, env)) {
                notrun++;
                String type = ((XdmNode) dep).getNodeName().getLocalName();
                String value = ((XdmNode) dep).getAttributeValue(new QName("", "value"));
                if (value == null) {
                    value = type;
                } else {
                    value = type + ":" + value;
                }
                if ("false".equals(((XdmNode) dep).getAttributeValue(new QName("", "satisfied")))) {
                    value = "!" + value;
                }
                String message = "dependency not satisfied: " + value;
                if (value.startsWith("feature:")) {
                    message = "requires optional " + value;
                }
                resultsDoc.writeTestcaseElement(testName, "n/a", message);
                return;
            }
        }

        if (env.failedToBuild) {
            resultsDoc.writeTestcaseElement(testName, "fail", "unable to build environment");
            noteFailure(testSetName, testName);
            return;
        }

        if (!env.usable) {
            resultsDoc.writeTestcaseElement(testName, "n/a", "environment dependencies not satisfied");
            notrun++;
            return;
        }


        if (testName.contains("environment-variable")) {
            EnvironmentVariableResolver resolver = new EnvironmentVariableResolver() {
                public Set<String> getAvailableEnvironmentVariables() {
                    Set<String> strings = new HashSet<String>();
                    strings.add("QTTEST");
                    strings.add("QTTEST2");
                    strings.add("QTTESTEMPTY");
                    return strings;
                }

                public String getEnvironmentVariable(String name) {
                    if (name.equals("QTTEST")) {
                        return "42";
                    } else if (name.equals("QTTEST2")) {
                        return "other";
                    } else if (name.equals("QTTESTEMPTY")) {
                        return "";
                    } else {
                        return null;
                    }
                }
            };
            env.processor.setConfigurationProperty(FeatureKeys.ENVIRONMENT_VARIABLE_RESOLVER, resolver);
        }

        if (testName.contains("load-xquery-module")) {
            env.processor.getUnderlyingConfiguration().setModuleURIResolver(new ModuleURIResolver() {
                public StreamSource[] resolve(String moduleURI, String baseURI, String[] locations) throws XPathException {
                    File file = queryModules.get(moduleURI);
                    if (file == null) {
                        return null;
                    }
                    try {
                        StreamSource ss = new StreamSource(new FileInputStream(file), baseURI);
                        return new StreamSource[]{ss};
                    } catch (FileNotFoundException e) {
                        throw new XPathException(e);
                    }
                }
            });
        }
        XdmNode testInput = (XdmNode) xpath.evaluateSingle("test", testCase);
        XdmNode stylesheet = (XdmNode) xpath.evaluateSingle("stylesheet[not(@role='secondary')]", testInput);
        XdmNode postureAndSweep = (XdmNode) xpath.evaluateSingle("posture-and-sweep", testInput);
        XdmNode principalPackage = (XdmNode) xpath.evaluateSingle("package[@role='principal']", testInput);
        XdmValue usedPackages = xpath.evaluate("package[@role='secondary']", testInput);

        XsltExecutable sheet = env.xsltExecutable;
        ErrorCollector collector = new ErrorCollector();
        if (quiet) {
            collector.setLogger(new Logger() {
                @Override
                public void println(String message, int severity) {
                    // no action
                }

                @Override
                public StreamResult asStreamResult() {
                    return null;
                }
            });
        }
        String xsltLanguageVersion = spec.contains("XSLT30") || spec.contains("XSLT20+") ? "3.0" : "2.0";

        if (postureAndSweep != null && runPostureAndSweepTests) {
            runStreamabilityTests(xpath, testCase);
            return;
        }
        if (postureAndSweep != null && !runPostureAndSweepTests) {
            return;
        }

        boolean strictStreamability = xpath.evaluate("result//error[@code='XTSE3430']", testCase).size() > 0;
        if (strictStreamability) {
            env.processor.setConfigurationProperty(FeatureKeys.STRICT_STREAMABILITY, true);
            env.resetActions.add(new Environment.ResetAction() {
                @Override
                public void reset(Environment env) {
                    env.processor.setConfigurationProperty(FeatureKeys.STRICT_STREAMABILITY, false);
                }
            });
        }

        boolean staticError = xpath.evaluate("result//error[starts-with(@code, 'XTSE')]", testCase).size() > 0;
        if (staticError && jitFlag) {
            env.xsltCompiler.setJustInTimeCompilation(false);
            env.resetActions.add(new Environment.ResetAction() {
                @Override
                public void reset(Environment env) {
                    env.xsltCompiler.setJustInTimeCompilation(true);
                }
            });
        }

        if (stylesheet != null) {
            String fileName = stylesheet.getAttributeValue(new QName("file"));

            Source styleSource = new StreamSource(testCase.getBaseURI().resolve(fileName).toString());

            XsltCompiler compiler = env.xsltCompiler;
            compiler.setErrorListener(collector);
            initPatternOptimization(compiler);
            compiler.clearParameters();
            //compiler.getUnderlyingCompilerInfo().setRuleOptimization(optimizeRules);
            //compiler.getUnderlyingCompilerInfo().setRulePreconditions(preconditionsRules);
            prepareForSQL(compiler.getProcessor());

            for (XdmItem param : xpath.evaluate("param[@static=('yes','true','1')]", testInput)) {
                String name = ((XdmNode) param).getAttributeValue(new QName("name"));
                String select = ((XdmNode) param).getAttributeValue(new QName("select"));
                XdmValue value;
                try {
                    value = xpath.evaluate(select, null);
                } catch (SaxonApiException e) {
                    System.err.println("*** Error evaluating parameter " + name + ": " + e.getMessage());
                    throw e;
                }
                compiler.setParameter(new QName(name), value);
            }
            for (XdmItem pack : usedPackages) {
                String fileName2 = ((XdmNode) pack).getAttributeValue(new QName("file"));
                Source styleSource2 = new StreamSource(testCase.getBaseURI().resolve(fileName2).toString());
                XsltPackage xpack = compiler.compilePackage(styleSource2);
                xpack = exportImportPackage(testName, testSetName, outcome, compiler, xpack, collector);
                compiler.importPackage(xpack);
            }
            sheet = exportImport(testName, testSetName, outcome, compiler, sheet, collector, styleSource);


            String optimizationAssertion = optimizationAssertions.get(testName);
            if (optimizationAssertion != null && sheet != null) {
                try {
                    assertOptimization(sheet, optimizationAssertion);
                    System.err.println("Optimization OK: " + optimizationAssertion);
                } catch (SaxonApiException e) {
                    System.err.println("Optimization assertion failed: " + optimizationAssertion);
                }
            }
        } else if (principalPackage != null) {
            XsltCompiler compiler = env.xsltCompiler;
            //compiler.setXsltLanguageVersion(xsltLanguageVersion);
            compiler.setErrorListener(collector);
            compiler.clearParameters();
            for (XdmItem param : xpath.evaluate("param[@static=('yes','true','1')]", testInput)) {
                String name = ((XdmNode) param).getAttributeValue(new QName("name"));
                String select = ((XdmNode) param).getAttributeValue(new QName("select"));
                XdmValue value;
                try {
                    value = xpath.evaluate(select, null);
                } catch (SaxonApiException e) {
                    System.err.println("*** Error evaluating parameter " + name + ": " + e.getMessage());
                    throw e;
                }
                compiler.setParameter(new QName(name), value);
            }

            for (XdmItem pack : usedPackages) {
                String fileName = ((XdmNode) pack).getAttributeValue(new QName("file"));
                Source styleSource = new StreamSource(testCase.getBaseURI().resolve(fileName).toString());
                XsltPackage xpack = compiler.compilePackage(styleSource);
                compiler.importPackage(xpack);
            }
            String fileName = principalPackage.getAttributeValue(new QName("file"));
            Source styleSource = new StreamSource(testCase.getBaseURI().resolve(fileName).toString());

            try {
                XsltPackage xpack = compiler.compilePackage(styleSource);
                sheet = xpack.link();
            } catch (SaxonApiException err) {
                System.err.println(err.getMessage());
                outcome.setException(err);
                outcome.setErrorsReported(collector.getErrorCodes());
            } catch (Exception err) {
                err.printStackTrace();
                System.err.println(err.getMessage());
                outcome.setException(new SaxonApiException(err));
                outcome.setErrorsReported(collector.getErrorCodes());
            }

            sheet = exportImport(testName, testSetName, outcome, compiler, sheet, collector, styleSource);

        }

        // TODO - trap multiples?
        XdmValue initialMatchSelection = null;
        XdmNode initialMode = (XdmNode) xpath.evaluateSingle("initial-mode", testInput);
        XdmNode initialFunction = (XdmNode) xpath.evaluateSingle("initial-function", testInput);
        XdmNode initialTemplate = (XdmNode) xpath.evaluateSingle("initial-template", testInput);

        QName initialModeName = null;
        if (initialMode != null) {
            String attValue = initialMode.getAttributeValue(new QName("name"));
            if ("#unnamed".equals(attValue)) {
                initialModeName = new QName("xsl", NamespaceConstant.XSLT, "unnamed");
            } else if ("#default".equals(attValue)) {
                initialModeName = new QName("xsl", NamespaceConstant.XSLT, "default");
            } else {
                initialModeName = getQNameAttribute(xpath, testInput, "initial-mode/@name");
            }
            String select = initialMode.getAttributeValue(new QName("select"));
            if (select != null) {
                initialMatchSelection = env.xpathCompiler.evaluate(select, null);
                if (runWithJS) {
                    resultsDoc.writeTestcaseElement(testName, "n/a", "Test driver limitation: cannot set initial match selection");
                    notrun++;
                    return;
                }
            }
        }

        QName initialTemplateName = getQNameAttribute(xpath, testInput, "initial-template/@name");
        QName initialFunctionName = getQNameAttribute(xpath, testInput, "initial-function/@name");

        if (runWithJS && !outcome.isException()) {
            initializeForSaxonJS(xpath, outcome, testName, env, testInput, initialMode, initialTemplate, initialModeName, initialTemplateName, initialFunctionName);
            return;
        }

        if (sheet != null && !runWithJS) {
            XdmItem contextItem = env.contextItem;


            String outputUri = xpath.evaluate("string(output/@file)", testInput).toString();
            if ("".equals(outputUri)) {
                outputUri = null;
            }
            URI baseOut = new File(resultsDir + "/results/output.xml").toURI();
            String baseOutputURI = new File(resultsDir + "/results/output.xml").toURI().toString();
            if (outputUri != null) {
                //baseOutputURI = testInput.getBaseURI().resolve(outputUri).toString();
                baseOutputURI = baseOut.resolve(outputUri).toString();
            }
            boolean failure;
            if (useXslt30Transformer) {
                failure = runWithXslt30Transformer(testCase, xpath, outcome, testName,
                                                   testSetName, env, testInput, sheet, collector,
                                                   xsltLanguageVersion, contextItem, initialMode,
                                                   initialFunction, initialTemplate, initialModeName,
                                                   initialMatchSelection, initialTemplateName, baseOutputURI);
            } else {
                failure = runWithXsltTransformer(xpath, outcome, testName, testSetName, env, testInput,
                                                 sheet, collector, contextItem, initialMode, initialModeName,
                                                 initialTemplateName, baseOutputURI);
            }
            if (failure) {
                return;
            }
        }

        for (Environment.ResetAction action : env.resetActions) {
            action.reset(env);
        }
        env.resetActions.clear();

        boolean expectEarlyExit = ((XdmAtomicValue)xpath.evaluateSingle("result/@early-exit-possible = 'true'", testCase)).getBooleanValue();
        if (expectEarlyExit != collector.isMadeEarlyExit()) {
            outcome.setComment(expectEarlyExit ? "Failed to make early exit" : "Unexpected early exit");
        }

        XdmNode assertion = (XdmNode) xpath.evaluateSingle("result/*", testCase);
        if (assertion == null) {
            noteFailure(testSetName, testName);
            resultsDoc.writeTestcaseElement(testName, "fail", "No test assertions found");
            return;
        }
        XPathCompiler assertionXPath = env.processor.newXPathCompiler();
        assertionXPath.setSchemaAware(true);
        assertionXPath.setBaseURI(assertion.getBaseURI());
        boolean success = outcome.testAssertion(assertion, outcome.getPrincipalResultDoc(), assertionXPath, xpath, debug);
        if (success) {
            successes++;
            resultsDoc.writeTestcaseElement(testName, "pass", outcome.getComment());
        } else {
            if (outcome.getWrongErrorMessage() != null) {
                outcome.setComment(outcome.getWrongErrorMessage());
                wrongErrorResults++;
                successes++;
                resultsDoc.writeTestcaseElement(testName, "wrongError", outcome.getComment());
            } else {
                noteFailure(testSetName, testName);
                // MHK: Nov 2015 - temporary diagnostics
                if (outcome.getException() != null && outcome.getComment() == null) {
                    outcome.setComment(outcome.getException().getMessage());
                }
                resultsDoc.writeTestcaseElement(testName, "fail", outcome.getComment());
            }
        }
    }

    protected void initializeForSaxonJS(XPathCompiler xpath, TestOutcome outcome, String testName, Environment env, XdmNode testInput, XdmNode initialMode, XdmNode initialTemplate, QName initialModeName, QName initialTemplateName, QName initialFunctionName) throws SaxonApiException {

    }

    public void runJSTransform(Environment env, TestOutcome outcome, QName initialTemplateName,
                               QName initialModeName, QName initialFunctionName,
                               URI baseOutputURI) {
        // exists to be overridden
    }

    private boolean runWithXsltTransformer(XPathCompiler xpath, final TestOutcome outcome, String testName,
                                           String testSetName, Environment env, XdmNode testInput,
                                           XsltExecutable sheet, ErrorCollector collector,
                                           XdmItem contextItem, XdmNode initialMode, QName initialModeName,
                                           QName initialTemplateName, String baseOutputURI) {
        try {
            XsltTransformer transformer = sheet.load();
            transformer.setURIResolver(env);
            transformer.setBaseOutputURI(baseOutputURI);
            if (env.unparsedTextResolver != null) {
                transformer.getUnderlyingController().setUnparsedTextURIResolver(env.unparsedTextResolver);
            }
            if (initialTemplateName != null) {
                transformer.setInitialTemplate(initialTemplateName);
            }
            if (initialMode != null) {
                try {
                    transformer.setInitialMode(initialModeName);
                } catch (IllegalArgumentException e) {
                    if (e.getCause() instanceof XPathException) {
                        collector.fatalError((XPathException) e.getCause());
                        throw new SaxonApiException(e.getCause());
                    } else {
                        throw e;
                    }
                }
            }
            for (XdmItem param : xpath.evaluate("param[not(@static='yes')]", testInput)) {
                String name = ((XdmNode) param).getAttributeValue(new QName("name"));
                String select = ((XdmNode) param).getAttributeValue(new QName("select"));
                XdmValue value;
                try {
                    value = xpath.evaluate(select, null);
                } catch (SaxonApiException e) {
                    System.err.println("*** Error evaluating parameter " + name + ": " + e.getMessage());
                    throw e;
                }
                transformer.setParameter(new QName(name), value);
                setGlobalParameter(new QName(name), value);
            }
            if (contextItem != null) {
                transformer.setInitialContextNode((XdmNode) contextItem);
            }
            if (env.streamedFile != null) {
                transformer.setSource(new StreamSource(env.streamedFile));
            } else if (env.streamedContent != null) {
                transformer.setSource(new StreamSource(new StringReader(env.streamedContent), "inlineDoc"));
            }
            for (QName varName : env.params.keySet()) {
                transformer.setParameter(varName, env.params.get(varName));
                setGlobalParameter(varName, env.params.get(varName));
            }
            transformer.setErrorListener(collector);
           /* if (outputUri != null) {
                transformer.setBaseOutputURI(testInput.getBaseURI().resolve(outputUri).toString());
            } else {
                transformer.setBaseOutputURI(new File(resultsDir + "/results/output.xml").toURI().toString());
            }*/
            transformer.setMessageListener(new MessageListener() {
                public void message(XdmNode content, boolean terminate, SourceLocator locator) {
                    outcome.addXslMessage(content);
                    System.err.println(content.getStringValue());
                }
            });


            // Run the transformation twice, once for serialized results, once for a tree.
            // TODO: we could be smarter about this and capture both

            // run with serialization
            StringWriter sw = new StringWriter();
            Serializer serializer = env.processor.newSerializer(sw);
            transformer.setDestination(serializer);
            transformer.getUnderlyingController().setOutputURIResolver(
                    new OutputResolver(env.processor, outcome, true));
            transformer.transform();
            outcome.setPrincipalSerializedResult(sw.toString());
            if (saveResults) {
                // currently, only save the principal result file
                saveResultsToFile(sw.toString(),
                        new File(resultsDir + "/results/" + testSetName + "/" + testName + ".out"));
                Map<URI, TestOutcome.SingleResultDoc> xslResultDocuments = outcome.getSecondaryResultDocuments();
                for (Map.Entry<URI, TestOutcome.SingleResultDoc> entry : xslResultDocuments.entrySet()) {
                    URI key = entry.getKey();
                    if (key != null) {
                        String path = key.getPath();
                        String serialization = outcome.serialize(env.processor, entry.getValue());
                        saveResultsToFile(serialization, new File(path));
                    }
                }
            }

            // run without serialization
            if (env.streamedContent != null) {
                transformer.setSource(new StreamSource(new StringReader(env.streamedContent), "inlineDoc"));
            }
            XdmDestination destination = new XdmDestination();
            transformer.setDestination(destination);
            transformer.getUnderlyingController().setOutputURIResolver(
                    new OutputResolver(env.processor, outcome, false));
            transformer.transform();
            outcome.setPrincipalResult(destination.getXdmNode());
            outcome.setWarningsReported(collector.getFoundWarnings());
            //}
        } catch (SaxonApiException err) {
            outcome.setException(err);
            if (collector.getErrorCodes().isEmpty()) {
                outcome.addReportedError(err.getErrorCode().getLocalName());
            } else {
                outcome.setErrorsReported(collector.getErrorCodes());
            }
        } catch (Exception err) {
            err.printStackTrace();
            noteFailure(testSetName, testName);
            resultsDoc.writeTestcaseElement(testName, "fail", "*** crashed " + err.getClass() + ": " + err.getMessage());
            return true;
        }
        return false;
    }

    //protected void clearGlobalParameters(){}

    protected void setGlobalParameter(QName qName, XdmValue value) {
        // For overriding in Javascript driver
    }

//    protected void clearInitialFunctionArguments() {
//    }
//
//    protected void addInitialFunctionArgument(XdmValue value) {
//        // For overriding in Javascript driver
//    }

    protected boolean runWithXslt30Transformer(XdmNode testCase, XPathCompiler xpath, final TestOutcome outcome,
                                               String testName, String testSetName, Environment env,
                                               XdmNode testInput, XsltExecutable sheet, ErrorCollector collector,
                                               String xsltLanguageVersion, XdmItem contextItem, XdmNode initialMode,
                                               XdmNode initialFunction,
                                               XdmNode initialTemplate, QName initialModeName, XdmValue initialMatchSelection,
                                               QName initialTemplateName, String baseOutputURI) {
        try {

            boolean assertsSerial = xpath.evaluate("result//(assert-serialization|assert-serialization-error|serialization-matches)", testCase).size() > 0;
            boolean resultAsTree = env.outputTree;
            boolean serializationDeclared = env.outputSerialize;
            XdmNode needsTree = (XdmNode) xpath.evaluateSingle("output/@tree", testInput);
            if (needsTree != null) {
                resultAsTree = needsTree.getStringValue().equals("yes");
            }
            XdmNode needsSerialization = (XdmNode) xpath.evaluateSingle("output/@serialize", testInput);
            if (needsSerialization != null) {
                serializationDeclared = needsSerialization.getStringValue().equals("yes");
            }
            boolean resultSerialized = serializationDeclared || assertsSerial;

//                    if (assertsSerial) {
//                        String comment = outcome.getComment();
//                        comment = (comment == null ? "" : comment) + "*Serialization " + (serializationDeclared ? "declared* " : "required* ");
//                        outcome.setComment(comment);
//                    }


            Xslt30Transformer transformer = sheet.load30();
            transformer.setURIResolver(env);
            if (env.unparsedTextResolver != null) {
                transformer.getUnderlyingController().setUnparsedTextURIResolver(env.unparsedTextResolver);
            }
            if (tracing) {
                transformer.setTraceListener(new XSLTTraceListener());
            }

            Map<QName, XdmValue> caseGlobalParams = getNamedParameters(xpath, testInput, false, false);
            Map<QName, XdmValue> caseStaticParams = getNamedParameters(xpath, testInput, true, false);
            Map<QName, XdmValue> globalParams = new HashMap<QName, XdmValue>(env.params);
            globalParams.putAll(caseStaticParams);
            globalParams.putAll(caseGlobalParams);  // has to be this way to ensure test-local overrides global
            transformer.setStylesheetParameters(globalParams);

            if (contextItem != null) {
                transformer.setGlobalContextItem(contextItem);
            }

            transformer.setErrorListener(collector);

            transformer.setBaseOutputURI(baseOutputURI);

            transformer.setMessageListener(new MessageListener2() {
                public void message(XdmNode content, QName errorCode, boolean terminate, SourceLocator locator) {
                    outcome.addXslMessage(content);
                    System.err.println(content.getStringValue());
                }
            });

            XdmValue result = null;

            StringWriter sw = new StringWriter();

            Serializer serializer = env.processor.newSerializer(sw);
            //serializer.setOutputProperty(Serializer.Property.OMIT_XML_DECLARATION, "yes");

            OutputResolver serializingOutput = new OutputResolver(env.processor, outcome, true);
            Controller controller = transformer.getUnderlyingController();

            controller.setOutputURIResolver(serializingOutput);
            Destination dest = null;
            if (resultAsTree) {
                // If we want non-serialized, we need to accumulate any result documents as trees too
                controller.setOutputURIResolver(
                        new OutputResolver(env.processor, outcome, false));
                dest = new XdmDestination();
            } else {
                controller.setBuildTree(false);
            }
            if (resultSerialized) {
                dest = serializer;
            }

            Source src = null;
            if (env.streamedFile != null) {
                src = new StreamSource(env.streamedFile);
            } else if (env.streamedContent != null) {
                src = new StreamSource(new StringReader(env.streamedContent), "inlineDoc");
            } else if (initialTemplate == null && contextItem != null) {
                src = ((XdmNode) contextItem).getUnderlyingNode();
            }

            if (src == null && initialFunction == null && initialTemplateName == null && initialModeName == null) {
                initialTemplateName = new QName("xsl", NamespaceConstant.XSLT, "initial-template");
            }

            try {
                if (initialModeName != null) {
                    transformer.setInitialMode(initialModeName);
                } else {
                    controller.getInitialMode();   /// has the side effect of setting to the unnamed
                }
            } catch (IllegalArgumentException e) {
                if (e.getCause() instanceof XPathException) {
                    collector.fatalError((XPathException) e.getCause());
                    throw new SaxonApiException(e.getCause());
                } else {
                    throw e;
                }
            }

            if (initialMode != null || initialTemplate != null) {
                XdmNode init = initialMode == null ? initialTemplate : initialMode;
                Map<QName, XdmValue> params = getNamedParameters(xpath, init, false, false);
                Map<QName, XdmValue> tunnelledParams = getNamedParameters(xpath, init, false, true);
                if (xsltLanguageVersion.equals("2.0")) {
                    if (!(params.isEmpty() && tunnelledParams.isEmpty())) {
                        System.err.println("*** Initial template parameters ignored for XSLT 2.0");
                    }
                } else {
                    transformer.setInitialTemplateParameters(params, false);
                    transformer.setInitialTemplateParameters(tunnelledParams, true);
                }
            }

            if (initialTemplateName != null) {
                transformer.setGlobalContextItem(contextItem);
                if (dest == null) {
                    result = transformer.callTemplate(initialTemplateName);
                } else {
                    transformer.callTemplate(initialTemplateName, dest);
                }
            } else if (initialFunction != null) {
                QName name = getQNameAttribute(xpath, initialFunction, "@name");
                XdmValue[] params = getParameters(xpath, initialFunction);
                if (dest == null) {
                    result = transformer.callFunction(name, params);
                } else {
                    transformer.callFunction(name, params, dest);
                }
            } else if (initialMatchSelection != null) {
                if (dest == null) {
                    result = transformer.applyTemplates(initialMatchSelection);
                } else {
                    transformer.applyTemplates(initialMatchSelection, dest);
                }
            } else {
                if (dest == null) {
                    result = transformer.applyTemplates(src);
                } else {
                    transformer.applyTemplates(src, dest);
                }
            }

            outcome.setWarningsReported(collector.getFoundWarnings());
            if (resultAsTree && !resultSerialized) {
                result = ((XdmDestination) dest).getXdmNode();
            }
            if (resultSerialized) {
                outcome.setPrincipalSerializedResult(sw.toString());
            }
            outcome.setPrincipalResult(result);

            if (saveResults) {
                String s = sw.toString();
                // If a transform result is entirely xsl:result-document, then result will be null
                if (!resultSerialized && result != null) {
                    StringWriter sw2 = new StringWriter();
                    Serializer se = env.processor.newSerializer(sw2);
                    se.setOutputProperty(Serializer.Property.OMIT_XML_DECLARATION, "yes");
                    env.processor.writeXdmValue(result, se);
                    se.close();
                    s = sw2.toString();
                }
                // currently, only save the principal result file in the result directory
                saveResultsToFile(s,
                        new File(resultsDir + "/results/" + testSetName + "/" + testName + ".out"));
                Map<URI, TestOutcome.SingleResultDoc> xslResultDocuments = outcome.getSecondaryResultDocuments();
                for (Map.Entry<URI, TestOutcome.SingleResultDoc> entry : xslResultDocuments.entrySet()) {
                    URI key = entry.getKey();
                    String path = key.getPath();
                    String serialization = outcome.serialize(env.processor, entry.getValue());
                    saveResultsToFile(serialization, new File(path));
                }
            }
        } catch (SaxonApiException err) {
            if (err.getCause() instanceof XPathException &&
                    !((XPathException) err.getCause()).hasBeenReported()) {
                System.err.println("Thrown exception " + ((XPathException) err.getCause()).getErrorCodeLocalPart() +
                        ": " + err.getCause().getMessage());
            }
            outcome.setException(err);
            if (collector.getErrorCodes().isEmpty()) {
                if (err.getErrorCode() == null) {
                    outcome.addReportedError("Error_with_no_error_code");
                } else {
                    outcome.addReportedError(err.getErrorCode().getLocalName());
                }
            } else {
                outcome.setErrorsReported(collector.getErrorCodes());
            }
        } catch (Exception err) {
            err.printStackTrace();
            noteFailure(testSetName, testName);
            resultsDoc.writeTestcaseElement(testName, "fail", "*** crashed " + err.getClass() + ": " + err.getMessage());
            return true;
        }
        return false;
    }

    protected void initPatternOptimization(XsltCompiler compiler) {
    }

    protected XsltExecutable exportImport(String testName, String testSetName, TestOutcome outcome, XsltCompiler compiler, XsltExecutable sheet, ErrorCollector collector, Source styleSource) {
        try {
            if (export) {
                sheet = exportStylesheet(testName, testSetName, compiler, sheet, styleSource);
            } else if (sheet == null) {
                sheet = compiler.compile(styleSource);
            }
        } catch (SaxonApiException err) {
            outcome.setException(err);
            if (err.getErrorCode() != null) {
                collector.getErrorCodes().add(err.getErrorCode().getLocalName());
            }
            outcome.setErrorsReported(collector.getErrorCodes());
        } catch (Exception err) {
            err.printStackTrace();
            System.err.println(err.getMessage());
            outcome.setException(new SaxonApiException(err));
            outcome.setErrorsReported(collector.getErrorCodes());
        }
        return sheet;
    }

    protected XsltExecutable exportStylesheet(String testName, String testSetName, XsltCompiler compiler, XsltExecutable sheet, Source styleSource) throws SaxonApiException {
        try {
            File exportFile = new File(resultsDir + "/export/" + testSetName + "/" + testName + ".sef");
            XsltPackage compiledPack = compiler.compilePackage(styleSource);
            compiledPack.save(exportFile);
            sheet = reloadExportedStylesheet(compiler, exportFile);
        } catch (SaxonApiException e) {
            try {
                compiler.getErrorListener().error(XPathException.makeXPathException(e));
            } catch (TransformerException te) {
                assert false;
            }
            //System.err.println(e.getMessage());
            //e.printStackTrace();  //temporary, for debugging
            throw e;
        }
        return sheet;
    }

    protected XsltExecutable reloadExportedStylesheet(XsltCompiler compiler, File exportFile) throws SaxonApiException {
        return compiler.loadExecutablePackage(exportFile.toURI());
    }

    private XsltPackage exportImportPackage(String testName, String testSetName, TestOutcome outcome, XsltCompiler compiler, XsltPackage pack, ErrorCollector collector) {
        try {
            if (export) {
                try {
                    File exportFile = new File(resultsDir + "/export/" + testSetName + "/" + testName + ".base.sef");
                    pack.save(exportFile);
                    return compiler.loadLibraryPackage(exportFile.toURI());
                } catch (SaxonApiException e) {
                    compiler.getErrorListener().error(XPathException.makeXPathException(e));
                    //e.printStackTrace();  //temporary, for debugging
                    throw e;
                }
            } else {
                return pack;
            }
        } catch (SaxonApiException err) {
            outcome.setException(err);
            if (err.getErrorCode() != null) {
                collector.getErrorCodes().add(err.getErrorCode().getLocalName());
            }
            outcome.setErrorsReported(collector.getErrorCodes());
        } catch (Exception err) {
            err.printStackTrace();
            System.err.println(err.getMessage());
            outcome.setException(new SaxonApiException(err));
            outcome.setErrorsReported(collector.getErrorCodes());
        }
        return pack;
    }

    /**
     * Run streamability tests
     */

    public void runStreamabilityTests(XPathCompiler xpc, XdmNode testCase) {
        // no action for Saxon-HE - ignore the test
    }

    /**
     * Return a set of named parameters as a map
     *
     * @param xpath     The XPath compiler to use
     * @param node      The node to search for <param> children
     * @param getStatic Whether to collect static or non-static sets
     * @param tunnel    Whether to collect tunnelled or non-tunnelled sets
     * @return Map of the evaluated parameters, keyed by QName
     * @throws SaxonApiException
     */
    protected Map<QName, XdmValue> getNamedParameters(XPathCompiler xpath, XdmNode node, boolean getStatic, boolean tunnel) throws SaxonApiException {
        Map<QName, XdmValue> params = new HashMap<QName, XdmValue>();
        int i = 1;
        String staticTest = getStatic ? "@static='yes'" : "not(@static='yes')";
        for (XdmItem param : xpath.evaluate("param[" + staticTest + "]", node)) {
            QName name = getQNameAttribute(xpath, param, "@name");
            String select = ((XdmNode) param).getAttributeValue(new QName("select"));
            String tunnelled = ((XdmNode) param).getAttributeValue(new QName("tunnel"));
            QName as = getQNameAttribute(xpath, param, "@as");   // TODO: it won't always be a QName, could be a sequence type
            boolean required = tunnel == (tunnelled != null && tunnelled.equals("yes"));
            XdmValue value;
            if (name == null) {
                System.err.println("*** No name for parameter " + i + " in initial-template");
                throw new SaxonApiException("*** No name for parameter " + i + " in initial-template");
            }
            try {
                value = xpath.evaluate(select, null);
                i++;
            } catch (SaxonApiException e) {
                System.err.println("*** Error evaluating parameter " + name + " in initial-template : " + e.getMessage());
                throw e;
            }
            if (as != null) {
                value = new XdmAtomicValue(((AtomicValue) value.getUnderlyingValue()).getStringValue(),
                        new ItemTypeFactory(xpath.getProcessor()).getAtomicType(as));
            }
            if (required) {
                params.put(name, value);
            }
        }
        return params;
    }

    /**
     * Return a set of unnamed parameters as an array
     *
     * @param xpath The XPath compiler to use
     * @param node  The node to search for <param> children
     * @return Array of the parameter values
     * @throws SaxonApiException
     */
    protected XdmValue[] getParameters(XPathCompiler xpath, XdmNode node) throws SaxonApiException {
        ArrayList<XdmValue> params = new ArrayList<XdmValue>();

        int i = 1;
        for (XdmItem param : xpath.evaluate("param[not(@static='yes')]", node)) {
            String select = ((XdmNode) param).getAttributeValue(new QName("select"));
            XdmValue value;
            try {
                value = xpath.evaluate(select, null);
                i++;
            } catch (SaxonApiException e) {
                System.err.println("*** Error evaluating parameter " + i + " in initial-function : " + e.getMessage());
                throw e;
            }
            params.add(value);
        }
        return params.toArray(new XdmValue[params.size()]);
    }

    @SuppressWarnings({"ResultOfMethodCallIgnored"})
    protected void saveResultsToFile(String content, File file) {
        try {
            if (!file.exists()) {
                File directory = file.getParentFile();
                if (directory != null && !directory.exists()) {
                    directory.mkdirs();
                }
                file.createNewFile();
            }
            FileWriter writer = new FileWriter(file);
            writer.append(content);
            writer.close();
        } catch (IOException e) {
            System.err.println("*** Failed to save results to " + file.getAbsolutePath());
            e.printStackTrace();
        }
    }

    protected void assertOptimization(XsltExecutable stylesheet, String assertion) throws SaxonApiException {

        XdmDestination builder = new XdmDestination();
        stylesheet.explain(builder);
        builder.close();
        XdmNode expressionTree = builder.getXdmNode();
        XPathCompiler xpe = stylesheet.getProcessor().newXPathCompiler();
        XPathSelector exp = xpe.compile(assertion).load();
        exp.setContextItem(expressionTree);
        XdmAtomicValue bv = (XdmAtomicValue) exp.evaluateSingle();
        if (bv==null || !bv.getBooleanValue()) {
            println("** Optimization assertion failed");
            println(expressionTree.toString());
            throw new SaxonApiException("Expected optimization not performed");
        }

    }

    // Dependencies which are always satisfied in Saxon. (Note, this doesn't necessarily
    // mean that we support all possible values for the dependency, only that we support
    // all the values that actually appear in the test suite as it exists today.)

    // The string in question is either a feature name, or it takes the form "feature/value"
    // where feature is the feature name (element name of a child of the dependencies element)
    // and value is the value of the "value" attribute.

    protected Set<String> alwaysOn = new HashSet<String>();

    // Dependencies which are always satisfied in Saxon-EE but not in Saxon-HE or -PE

    protected Set<String> needsEE = new HashSet<String>();

    // Dependencies which are always satisfied in Saxon-PE and -EE but not in Saxon-HE

    protected Set<String> needsPE = new HashSet<String>();

    // Dependencies which are never satisfied in Saxon

    protected Set<String> alwaysOff = new HashSet<String>();

    protected void setDependencyData() {
        alwaysOn.add("feature/disabling_output_escaping");
        alwaysOn.add("feature/serialization");
        alwaysOn.add("feature/namespace_axis");
        alwaysOn.add("feature/dtd");
        alwaysOn.add("feature/built_in_derived_types");
        alwaysOn.add("feature/remote_http");
        alwaysOn.add("feature/xsl-stylesheet-processing-instruction");
        alwaysOn.add("feature/fn-transform-XSLT");
        alwaysOn.add("available_documents");
        alwaysOn.add("ordinal_scheme_name");
        alwaysOn.add("default_calendar_in_date_formatting_functions");
        alwaysOn.add("supported_calendars_in_date_formatting_functions");
        alwaysOn.add("maximum_number_of_decimal_digits");
        alwaysOn.add("default_output_encoding");
        alwaysOn.add("unparsed_text_encoding");
        alwaysOn.add("recognize_id_as_uri_fragment");
        alwaysOn.add("feature/XPath_3.1");

        needsPE.add("feature/backwards_compatibility");
        needsPE.add("feature/Saxon-PE");
        needsPE.add("feature/dynamic_evaluation");

        needsEE.add("languages_for_numbering");
        needsEE.add("feature/streaming");
        needsEE.add("feature/schema_aware");
        needsEE.add("feature/Saxon-EE");
        //needsEE.add("feature/XSD_1.1");


        needsEE.add("feature/xquery_invocation");
        needsEE.add("feature/higher_order_functions");

        alwaysOn.add("detect_accumulator_cycles");
    }

    /**
     * Ensure that a dependency is satisfied, first by checking whether Saxon supports
     * the requested feature, and if necessary by reconfiguring Saxon so that it does;
     * if configuration changes are made, then resetActions should be registered to
     * reverse the changes when the test is complete.
     *
     * @param dependency the dependency to be checked
     * @param env        the environment in which the test runs. The method may modify this
     *                   environment provided the changes are reversed for subsequent tests.
     * @return true if the test can proceed, false if the dependencies cannot be
     * satisfied.
     */

    @Override
    public boolean ensureDependencySatisfied(XdmNode dependency, Environment env) {
        String type = dependency.getNodeName().getLocalName();
        String value = dependency.getAttributeValue(new QName("value"));

        String tv = type + "/" + (value == null ? "*" : value);

        boolean inverse = "false".equals(dependency.getAttributeValue(new QName("satisfied")));
        boolean needed = !"false".equals(dependency.getAttributeValue(new QName("satisfied")));

        if (alwaysOn.contains(type) || alwaysOn.contains(tv)) {
            return needed;
        }
        if (alwaysOff.contains(type) || alwaysOff.contains(tv)) {
            return !needed;
        }
        String edition = env.processor.getSaxonEdition();
        if (needsPE.contains(type) || needsPE.contains(tv)) {
            return (edition.equals("PE") || edition.equals("EE")) == needed;
        }
        if (needsEE.contains(type) || needsEE.contains(tv)) {
            return edition.equals("EE") == needed;
        }

        if ("spec".equals(type)) {
            boolean atLeast = value.endsWith("+");
            value = value.replace("+", "");
            String specName = spec.specAndVersion.replace("XT", "XSLT");
            int order = value.compareTo(specName);
            return atLeast ? order <= 0 : order == 0;
            //return !(value.equals("XSLT20") && spec == Spec.XT30);
        } else if ("feature".equals(type)) {

            if ("XML_1.1".equals(value)) {
                String requiredVersion = inverse ? "1.0" : "1.1";
                final String oldVersion = env.processor.getXmlVersion();
                if (env != null) {
                    env.resetActions.add(new Environment.ResetAction() {
                        @Override
                        public void reset(Environment env) {
                            env.processor.setXmlVersion(oldVersion);
                        }
                    });
                    env.processor.setXmlVersion(requiredVersion);
                    return true;
                } else {
                    return false;
                }
            } else if ("XSD_1.1".equals(value)) {
                    String requiredVersion = inverse ? "1.0" : "1.1";
                    final String oldVersion = (String)env.processor.getConfigurationProperty(FeatureKeys.XSD_VERSION);
                    if (!oldVersion.equals(requiredVersion)) {
                        env.processor.setConfigurationProperty(FeatureKeys.XSD_VERSION, requiredVersion);
                        env.resetActions.add(new Environment.ResetAction() {
                            @Override
                            public void reset(Environment env) {
                                env.processor.setConfigurationProperty(FeatureKeys.XSD_VERSION, oldVersion);
                            }
                        });
                    }
                    return true;
            } else if ("higher_order_functions".equals(value)) {
                return (edition.equals("PE") || edition.equals("EE")) ^ inverse;
            } else if ("simple-uca-fallback".equals(value)) {
                return !inverse;
            } else if ("advanced-uca-fallback".equals(value)) {
                return (edition.equals("PE") || edition.equals("EE")) ^ inverse;
            } else {
                System.err.println("*** Unknown feature in HE: " + value);
                return env.processor.getSaxonEdition().equals("HE") ? false : null;
            }

        } else if ("default_language_for_numbering".equals(type)) {
            final String old = (String) env.processor.getConfigurationProperty(FeatureKeys.DEFAULT_LANGUAGE);
            if (!value.equals(old)) {
                env.processor.setConfigurationProperty(FeatureKeys.DEFAULT_LANGUAGE, value);
                env.resetActions.add(new Environment.ResetAction() {
                    public void reset(Environment env) {
                        env.processor.setConfigurationProperty(FeatureKeys.DEFAULT_LANGUAGE, old);
                    }
                });
            }
            return true;
        } else if ("enable_assertions".equals(type)) {
            boolean on = !inverse;
            final boolean old = env.xsltCompiler.isAssertionsEnabled();
            env.xsltCompiler.setAssertionsEnabled(on);
            env.resetActions.add(new Environment.ResetAction() {
                public void reset(Environment env) {
                    env.xsltCompiler.setAssertionsEnabled(old);
                }
            });
            return true;
        } else if ("extension-function".equals(type)) {
            if (value.equals("Q{http://relaxng.org/ns/structure/1.0}schema-report#1")) {
                try {
                    Configuration config = env.processor.getUnderlyingConfiguration();
                    Object sf = config.getInstance("net.cfoster.saxonjing.SchemaFunction", null);
                    env.processor.registerExtensionFunction((ExtensionFunctionDefinition)sf);
                    Object sfd = config.getInstance("net.cfoster.saxonjing.SchemaReportFunction", null);
                    env.processor.registerExtensionFunction((ExtensionFunctionDefinition) sfd);
                    return true;
                } catch (XPathException err) {
                    System.err.println("Failed to load Saxon-Jing extension functions");
                    return false;
                }
            }
            return false;
        } else if ("year_component_values".equals(type)) {
            if ("support year zero".equals(value)) {
                if (env != null) {
                    env.processor.setConfigurationProperty(FeatureKeys.XSD_VERSION, inverse ? "1.0" : "1.1");
                    return true;
                } else {
                    return false;
                }
            }
            return !inverse;
        } else if ("additional_normalization_form".equals(type)) {
            if (value.equals("support FULLY-NORMALIZED")) {
                return inverse;
            }
            return !inverse;

        } else if ("on-multiple-match".equals(type)) {
            env.resetActions.add(new Environment.ResetAction() {
                @Override
                public void reset(Environment env) {
                    env.xsltCompiler.getUnderlyingCompilerInfo().setRecoveryPolicy(Configuration.RECOVER_WITH_WARNINGS);
                }
            });
            if (value.equals("error")) {
                env.xsltCompiler.getUnderlyingCompilerInfo().setRecoveryPolicy(Configuration.DO_NOT_RECOVER);
            } else {
                env.xsltCompiler.getUnderlyingCompilerInfo().setRecoveryPolicy(Configuration.RECOVER_SILENTLY);
            }
            return true;
        } else if ("ignore-doc-failure".equals(type)) {
            env.resetActions.add(new Environment.ResetAction() {
                @Override
                public void reset(Environment env) {
                    env.xsltCompiler.getUnderlyingCompilerInfo().setRecoveryPolicy(Configuration.RECOVER_WITH_WARNINGS);
                }
            });
            if (value.equals("false")) {
                env.xsltCompiler.getUnderlyingCompilerInfo().setRecoveryPolicy(Configuration.DO_NOT_RECOVER);
            } else {
                env.xsltCompiler.getUnderlyingCompilerInfo().setRecoveryPolicy(Configuration.RECOVER_SILENTLY);
            }
            return true;
        } else if ("combinations_for_numbering".equals(type)) {
            if (value.equals("COPTIC EPACT DIGIT ONE") || value.equals("SINHALA ARCHAIC DIGIT ONE") || value.equals("MENDE KIKAKUI DIGIT ONE")) {
                return false;
            }
            return !inverse;
        } else if ("xsd-version".equals(type)) {
            return env.processor.getSaxonEdition().equals("HE") ? false : null;
        } else if ("sweep_and_posture".equals(type)) {
            return env.processor.getSaxonEdition().equals("HE") ? inverse : null;
        } else if ("unicode-version".equals(type)) {
            return value.equals("6.0"); // Avoid running Unicode 9.0 tests - they are slow!
        } else {
            println("**** dependency not recognized for HE: " + type);
            return false;
        }
    }

    /**
     * Return a QNamed value from an attribute. This can handle active namespace prefix bindings or Clark notations in the
     * attribute string values
     *
     * @param xpath         XPath compiler
     * @param contextItem   Context item
     * @param attributePath Path to the required (singleton?) attribute
     * @return the value of the attribute as a QName
     * @throws SaxonApiException
     */
    protected static QName getQNameAttribute(XPathCompiler xpath, XdmItem contextItem, String attributePath) throws SaxonApiException {
        String exp = "for $att in " + attributePath +
                " return if (contains($att, ':')) then resolve-QName($att, $att/..) else " +
                " if (contains($att,'{')) then QName(substring-before(substring-after($att,'{'),'}'),substring-after($att,'}')) else" +
                " if ($att = '#unnamed') then QName('http://saxon.sf.net/', 'unnamed') else " +
                " if ($att = '#default') then QName('http://saxon.sf.net/', 'default') else " +
                " QName('', $att)";
        XdmAtomicValue qname = (XdmAtomicValue) xpath.evaluateSingle(exp, contextItem);
        return qname == null ? null : (QName) qname.getValue();
    }

    protected static class OutputResolver implements OutputURIResolver {

        private Processor proc;
        private TestOutcome outcome;
        private Destination destination;
        private StringWriter stringWriter;
        boolean serialized;
        URI uri;

        public OutputResolver(Processor proc, TestOutcome outcome, boolean serialized) {
            this.proc = proc;
            this.outcome = outcome;
            this.serialized = serialized;
        }

        public OutputResolver newInstance() {
            return new OutputResolver(proc, outcome, serialized);
        }

        public Result resolve(String href, String base) throws XPathException {
            try {
                uri = new URI(base).resolve(href);
                if (serialized) {
                    //destination = proc.newSerializer();
                    stringWriter = new StringWriter();
                    StreamResult result = new StreamResult(stringWriter);
                    result.setSystemId(uri.toString());
                    return result;
//                    ((Serializer)destination).setOutputWriter(stringWriter);
//                    Receiver r = destination.getReceiver(proc.getUnderlyingConfiguration());
//                    r.setSystemId(uri.toString());
//                    return r;
                } else {
                    destination = new XdmDestination();
                    ((XdmDestination) destination).setBaseURI(uri);
                    return destination.getReceiver(proc.getUnderlyingConfiguration());
                }
            } catch (SaxonApiException e) {
                throw new XPathException(e);
            } catch (URISyntaxException e) {
                throw new XPathException(e);
            }
        }

        public void close(Result result) throws XPathException {
            if (serialized) {
                outcome.setSecondaryResult(uri, null, stringWriter == null ? "" : stringWriter.toString());
            } else {
                XdmDestination xdm = (XdmDestination) destination;
                if (xdm != null) {
                    outcome.setSecondaryResult(xdm.getBaseURI(), xdm.getXdmNode(), null);
                }
            }
        }

    }
}
