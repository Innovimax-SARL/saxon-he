using System;
using System.Collections;
using System.Collections.Generic;
using System.Text;
using System.Xml;
using System.IO;
using System.Text.RegularExpressions;
using System.Globalization;
using Saxon.Api;
using TestRunner;


//using net.sf.saxon.Version;
using JFeatureKeys = net.sf.saxon.lib.FeatureKeys;
using JConfiguration = net.sf.saxon.Configuration;
using JVersion = net.sf.saxon.Version;

namespace TestRunner
{



    /**
     * Test Driver for the QT3 test suite
     */
    public class QT3TestSuiteDriver : TestDriver
    {


        public static string RNS = "http://www.w3.org/2012/08/qt-fots-results";
        public static string CNS = "http://www.w3.org/2010/09/qt-fots-catalog";


        public object getDependencyDictionary()
        {
            return dependencyDictionary;
        }

        private Dictionary<string, Dependency> dependencyDictionary = new Dictionary<string, Dependency>();


        private Spec spec;
        //private FotsResultsDocument resultsDoc;

        public override string catalogNamespace()
        {
            return CNS;
        }
			

        public override void processSpec(string specStr)
        {

            if (specStr.Equals("XP20"))
            {
                spec = Spec.XP20;
            }
            else if (specStr.Equals("XP30"))
            {
                spec = Spec.XP30;
            }
            else if (specStr.Equals("XQ10"))
            {
                spec = Spec.XQ10;
            }
            else if (specStr.Equals("XQ30"))
            {
                spec = Spec.XQ30;
            }
            else if (specStr.Equals("XQ31"))
            {
                spec = Spec.XQ31;
            }
            else
            {
                System.Console.WriteLine("The specific language must be one of the following: XP20, XP30, XQ10, XQ30 or XQ31");
            }
            resultsDoc = new FotsResultsDocument(this.getResultsDir(), spec);
        }

        

        public static void Main(string[] args)
        {

            if (args.Length == 0 || args[0].Equals("-?"))
            {
                System.Console.WriteLine("testsuiteDir catalog [-o:resultsdir] [-s:testSetName]" +
                        " [-t:testNamePattern] [-unfolded] [-bytecode:on|off|debug] [-tree] [-lang:XP20|XP30|XQ10|XQ30|XQ31]");
                return; 
            }
            System.Console.WriteLine("Testing Saxon " + (new Processor()).ProductVersion);
            new QT3TestSuiteDriver().go(args);
        }



        protected override void createGlobalEnvironments(XdmNode catalog, XPathCompiler xpc)
        {
            Environment environment = null;

            Environment defaultEnv = null;
            try
            {
                defaultEnv = localEnvironments["default"];
            }
            catch (Exception) { }
            foreach (XdmItem env in xpc.Evaluate("//environment", catalog))
            {
                environment = Environment.processEnvironment(
                        this, xpc, env, globalEnvironments, defaultEnv);
            }
            buildDependencyDictionary(driverProc, environment);
        }

        /**
         * Decide whether a dependency is satisfied
         *
         * @param dependency the dependency element in the catalog
         * @param env        an environment in the catalog, which can be modified to satisfy the dependency if necessary.
         *                   May be null.
         * @return true if the environment satisfies the dependency, else false
         */
        
        public override bool dependencyIsSatisfied(XdmNode dependency, Environment env)
        {
            string type = dependency.GetAttributeValue(new QName("type"));
            string value = dependency.GetAttributeValue(new QName("value"));
            bool inverse = "false".Equals(dependency.GetAttributeValue(new QName("satisfied")));
            if ("xml-version".Equals(type))
            {
                if (value.Equals("1.0:4-") && !inverse)
                {
                    // we don't support XML 1.0 4th edition or earlier
                    return false;
                }
                if (value.Contains("1.1") && !inverse)
                {
                    if (env != null)
                    {
                        env.processor.SetProperty(JFeatureKeys.XML_VERSION, "1.1");
                    }
                    else
                    {
                        return false;
                    }
                }
                else if (value.Contains("1.0") && !inverse)
                {
                    if (env != null)
                    {
                        env.processor.SetProperty(JFeatureKeys.XML_VERSION, "1.0");
                    }
                    else
                    {
                        return false;
                    }
                }
                return true;
            }
            else if ("xsd-version".Equals(type))
            {
                if ("1.1".Equals(value))
                {
                    if (env != null)
                    {
                        env.processor.SetProperty(JFeatureKeys.XSD_VERSION, (inverse ? "1.0" : "1.1"));
                    }
                    else
                    {
                        return false;
                    }
                }
                else if ("1.0".Equals(value))
                {
                    if (env != null)
                    {
                        env.processor.SetProperty(JFeatureKeys.XSD_VERSION, (inverse ? "1.1" : "1.0"));
                    }
                    else
                    {
                        return false;
                    }
                }
                return true;
            }
            else if ("limits".Equals(type))
            {
                return "year_lt_0".Equals(value) && !inverse;
            }
            else if ("spec".Equals(type))
            {
                return true;
            }
            else if ("collection-stability".Equals(type))
            {
                // SAXON has a problem here - we don't support stable collections
                return ("false".Equals(value) != inverse);
            }
            else if ("default-language".Equals(type))
            {
                return ("en".Equals(value) != inverse);
            }
            else if ("directory-as-collection-uri".Equals(type))
            {
                return ("true".Equals(value) != inverse);
            }
            else if ("language".Equals(type))
            {
                return (("en".Equals(value) || "de".Equals(value) || "fr".Equals(value)) != inverse);
            }
            else if ("calendar".Equals(type))
            {
                return (("AD".Equals(value) || "ISO".Equals(value)) != inverse);
            }
            else if ("format-integer-sequence".Equals(type))
            {
                return !inverse;
            }
            else if ("unicode-normalization-form".Equals(type))
            {
                return value.Equals("FULLY-NORMALIZED", StringComparison.OrdinalIgnoreCase) ? inverse : !inverse;
            }
            else if ("feature".Equals(type))
            {
                if ("namespace-axis".Equals(value))
                {
                    return !inverse;
                }
                else if ("higherOrderFunctions".Equals(value))
                {
                    return !inverse;
                }
                else if ("schemaImport".Equals(value) || "schemaValidation".Equals(value) || "schemaAware".Equals(value))
                {
                    //if (!treeModel.isSchemaAware()) {//TODO
                    //  return false; // cannot use the selected tree model for schema-aware tests
                    //}
                    // Need to reset these after use for this query??
                    if (env != null)
                    {
                        if (inverse)
                        {
                            // force use of a non-schema-aware processor by creating a ProfessionalConfiguration
                            /* ProfessionalConfiguration pConfig = new ProfessionalConfiguration();
                             pConfig.setNamePool(env.processor.getUnderlyingConfiguration().getNamePool());
                             final Processor savedProcessor = env.processor;
                             final XPathCompiler savedXPathCompiler = env.xpathCompiler;
                             final XQueryCompiler savedXQueryCompiler = env.xqueryCompiler;
                             final XsltCompiler savedXsltCompiler = env.xsltCompiler;
                             env.processor = new Processor(pConfig);
                             env.xpathCompiler = env.processor.newXPathCompiler();
                             env.xqueryCompiler = env.processor.newXQueryCompiler();
                             env.xsltCompiler = env.processor.newXsltCompiler();
                             env.xpathCompiler.setSchemaAware(false);
                             env.xqueryCompiler.setSchemaAware(false);
                             env.xsltCompiler.setSchemaAware(false);
                             env.resetAction = new Environment.ResetAction() {
                                 public void reset(Environment env) {
                                     env.processor = savedProcessor;
                                     env.xpathCompiler = savedXPathCompiler;
                                     env.xqueryCompiler = savedXQueryCompiler;
                                     env.xsltCompiler = savedXsltCompiler;
                                 }
                             }; */
                        }
                        else
                        {
                            env.xpathCompiler.SchemaAware = true;
                            env.xqueryCompiler.SchemaAware = true;
                            if (env.xsltCompiler != null) {
                                env.xsltCompiler.SchemaAware = true;
                            }
                        }
                    }
                    return true;
                }
                else if ("xpath-1.0-compatibility".Equals(value))
                {
                    if (env != null)
                    {
                        env.xpathCompiler.BackwardsCompatible = !inverse;
                        return true;
                    }
                    else
                    {
                        return false;
                    }
                }
                else if ("staticTyping".Equals(value))
                {
                    return inverse;
                }
                else if ("moduleImport".Equals(value))
                {
                    return !inverse;
                }
                else if ("schema-location-hint".Equals(value))
                {
                    return !inverse;
                } else if ("info-dtd".Equals(value)) {
                    if (treeModel == TreeModel.TinyTree || treeModel == TreeModel.LinkedTree || treeModel == TreeModel.TinyTreeCondensed)
                    {
                        return !inverse;
                    }
                    else {
                        return inverse;
                    }
                } else if ("serialization".Equals(value)) {
                    return true;
                } else if ("non_codepoint_collation".Equals(value)) {
                    return !inverse;
                } else if ("non_empty_sequence_collection".Equals(value)) {
                    return !inverse;
                } else if ("fn-transform-XSLT".Equals(value)) {
                    return !inverse;
                } else if ("fn-transform-XSLT30".Equals(value)) {
                    String edition = env.processor.Edition;
                    return (edition.Equals("PE") || edition.Equals("EE")) ^ inverse;
                } else if ("fn-format-integer-CLDR".Equals(value)) {
                    String edition = env.processor.Edition;
                    return (edition.Equals("PE") || edition.Equals("EE")) ^ inverse;
                } else if ("unicode-version".Equals(value)) {
                    return true;
                } else
                {
                    println("**** feature = " + value + "  ????");
                    return false;
                }
            }
            else
            {
                println("**** dependency not recognized: " + type);
                return false;
            }
        }

        /**
         * Run a test case
         *
         *
         * @param testCase the test case element in the catalog
         * @param xpc      the XPath compiler to be used for compiling XPath expressions against the catalog
         * @
         */

        protected override void runTestCase(XdmNode testCase, XPathCompiler xpc)
        {
            bool run = true;
            bool xpDependency = false;
            string hostLang;
            string langVersion;
            Spec specOpt = Spec.NULL;
            XPathCompiler xpath = driverProc.NewXPathCompiler();
            string testCaseName = testCase.GetAttributeValue(new QName("name"));
            string testSetName = testCase.Parent.GetAttributeValue(new QName("name"));
            bool needSerializedResult = ((XdmAtomicValue)xpc.EvaluateSingle(
                    "exists(./result//assert-serialization-error) or exists(./result//serialization-matches)", testCase)).GetBooleanValue();
            bool needResultValue = true;
            if (needSerializedResult)
            {
                needResultValue = ((XdmAtomicValue)xpc.EvaluateSingle(
                        "exists(./result//*[not(self::serialization-matches or self::assert-serialization-error or self::any-of or self::all-of)])", testCase)).GetBooleanValue();
            }

            XdmNode alternativeResult = null;
            XdmNode optimization = null;


            hostLang = ((SpecAttr)spec.GetAttr()).sname;
            langVersion = ((SpecAttr)spec.GetAttr()).version;


            Environment env = getEnvironment(testCase, xpc);
            if (env == null)
            {
                notrun++;
                return;
            }
            env.xpathCompiler.BackwardsCompatible = false;
            env.processor.XmlVersion = (decimal)1.0;


			//test
			/*bool icuColCheck = net.sf.saxon.Version.platform.hasICUCollator ();
			bool icuNumCheck = net.sf.saxon.Version.platform.hasICUNumberer();
			Console.WriteLine ("ICUCol: " + (icuColCheck ? "true" : "false"));
			Console.WriteLine ("ICUNum: " + (icuNumCheck ? "true" : "false"));*/
			//end of test
            foreach (XdmItem dependency in xpc.Evaluate("/*/dependency, ./dependency", testCase))
            {
                string type = ((XdmNode)dependency).GetAttributeValue(new QName("type"));
                if (type == null)
                {
                    // throw new IllegalStateException("dependency/@type is missing"); //TODO
                }
                string value = ((XdmNode)dependency).GetAttributeValue(new QName("value"));
                if (value == null)
                {
                    //throw new IllegalStateException("dependency/@value is missing"); //TODO
                }

                if (type.Equals("spec"))
                {
                    bool applicable = false;
                    if (!value.Contains(((SpecAttr)spec.GetAttr()).sname))
                    {
                        applicable = false;
                    }
                    else if (value.Contains(((SpecAttr)spec.GetAttr()).svname))
                    {
                        applicable = true;
                    }
                    else if ( ( ((SpecAttr)spec.GetAttr()).svname.Equals("XQ30") || ((SpecAttr)spec.GetAttr()).svname.Equals("XQ31")) && (value.Contains("XQ10+") || value.Contains("XQ30+")) )
                    {
                        applicable = true;
                    }
                    else if ( ( ((SpecAttr)spec.GetAttr()).svname.Equals("XP30") || ((SpecAttr)spec.GetAttr()).svname.Equals("XP31")) && (value.Contains("XP20+") || value.Contains("XP30+")) )
                    {
                        applicable = true;
                    }
                    if (!applicable)
                    {
                        writeTestcaseElement(testCaseName, "n/a", "not" + ((SpecAttr)spec.GetAttr()).svname, spec);
                        notrun++;
                        return;
                    }
                }
                if (langVersion.Equals("3.0"))
                {
                    /* EnvironmentVariableResolver resolver = new EnvironmentVariableResolver() {
                         public Set<string> getAvailableEnvironmentVariables() {
                             Set<string> strings = new HashSet<string>();
                             strings.add("QTTEST");
                             strings.add("QTTEST2");
                             strings.add("QTTESTEMPTY");
                             return strings;
                         }

                         public string getEnvironmentVariable(string name) {
                             if (name.Equals("QTTEST")) {
                                 return "42";
                             } else if (name.Equals("QTTEST2")) {
                                 return "other";
                             } else if (name.Equals("QTTESTEMPTY")) {
                                 return "";
                             } else {
                                 return null;
                             }
                         }
                     }; */
                    //TODO
                    //env.processor.SetProperty(JFeatureKeys.ENVIRONMENT_VARIABLE_RESOLVER, resolver);
                }
                /*if (type.Equals("feature") && value.Equals("xpath-1.0-compatibility"))
                {
                    hostLang = "XP";
                    langVersion = "3.0";
                    xpDependency = true;
                    specOpt = Spec.XP30;
                }
                if (type.Equals("feature") && value.Equals("namespace-axis"))
                {
                    hostLang = "XP";
                    langVersion = "3.0";
                    xpDependency = true;
                    specOpt = Spec.XP30;
                }*/
                if (!dependencyIsSatisfied((XdmNode)dependency, env))
                {
                    println("*** Dependency not satisfied: " + ((XdmNode)dependency).GetAttributeValue(new QName("type")));
                    writeTestcaseElement(testCaseName, "n/a", "Dependency not satisfied", spec);
                    run = false;
                    notrun++;
                    return;
                }
            }

            XdmNode exceptionElement;
            
            try{
                exceptionElement = exceptionsMap[testCaseName];

            } catch(Exception) {
                exceptionElement = null;
            }
            if (exceptionElement != null)
            {
                XdmItem config = xpath.EvaluateSingle("configuration", exceptionElement);

                string runAtt = exceptionElement.GetAttributeValue(new QName("run"));
                string reasonMsg = xpath.EvaluateSingle("reason", exceptionElement).ToString();
                string reportAtt = exceptionElement.GetAttributeValue(new QName("report"));

                if (config != null)
                {
                    XdmItem paramValue = xpath.EvaluateSingle("param[@name='not-unfolded' and @value='yes']/@name", config);
                    if (unfolded && paramValue != null)
                    {
                        writeTestcaseElement(testCaseName, "notRun", reasonMsg, spec);
                        notrun++;
                        return;
                    }
                }

                if ("false".Equals(runAtt))
                {
                    writeTestcaseElement(testCaseName, reportAtt, reasonMsg, spec);
                    notrun++;
                    return;
                }

                alternativeResult = (XdmNode)xpc.EvaluateSingle("result", exceptionElement);
                optimization = (XdmNode)xpc.EvaluateSingle("optimization", exceptionElement);
            }

            if (run && (specOpt == Spec.NULL || specOpt == spec))
            {

                TestOutcome outcome = new TestOutcome(this);
                string exp = null;
                try
                {
                    exp = xpc.Evaluate("if (test/@file) then unparsed-text(resolve-uri(test/@file, base-uri(.))) else string(test)", testCase).ToString();
                }
                catch (Exception err)
                {
                    println("*** Failed to read query: " + err.Message);
                    outcome.SetException((DynamicError)err);
                }

                //noinspection ThrowableResultOfMethodCallIgnored
                if (outcome.GetException() == null)
                {
                    if (hostLang.Equals("XP") || hostLang.Equals("XT"))
                    {
                        XPathCompiler testXpc = env.xpathCompiler;
                        testXpc.XPathLanguageVersion = langVersion;
                        testXpc.DeclareNamespace("fn", "http://www.w3.org/2005/xpath-functions");
                        testXpc.DeclareNamespace("xs", "http://www.w3.org/2001/XMLSchema");
                        testXpc.DeclareNamespace("map", "http://www.w3.org/2005/xpath-functions/map");
                        //testXpc.DeclareNamespace("math", NamespaceConstant.MATH);
                        //testXpc.DeclareNamespace("Dictionary", NamespaceConstant.Dictionary_FUNCTIONS);

                        try
                        {
                            XPathSelector selector = testXpc.Compile(exp).Load();
                            foreach (QName varName in env.params1.Keys)
                            {
                                selector.SetVariable(varName, env.params1[varName]);
                            }
                            if (env.contextItem != null)
                            {
                                selector.ContextItem = env.contextItem;
                            }
                            selector.InputXmlResolver = new TestUriResolver(env);

                            if (env.unparsedTextResolver != null)
                            {
                                //selector.getUnderlyingXPathContext().setUnparsedTextURIResolver(env.unparsedTextResolver); //TODO
                            }
                            XdmValue result = selector.Evaluate();
                            outcome.SetPrincipalResult(result);
                        }
                        catch (Exception err)
                        {
                            println(err.Message);
                            
                           outcome.SetException(err);
                            

                        }
                    }
                    else if (hostLang.Equals("XQ"))
                    {
                        XQueryCompiler testXqc = env.xqueryCompiler;
                        testXqc.XQueryLanguageVersion = langVersion;
                        testXqc.DeclareNamespace("fn", "http://www.w3.org/2005/xpath-functions");
                        testXqc.DeclareNamespace("xs", "http://www.w3.org/2001/XMLSchema");
                        //testXqc.DeclareNamespace("math", NamespaceConstant.MATH);
                        testXqc.DeclareNamespace("map", "http://www.w3.org/2005/xpath-functions/map");
                        testXqc.DeclareNamespace("array", "http://www.w3.org/2005/xpath-functions/array");
                        // ErrorCollector errorCollector = new ErrorCollector();
                        testXqc.ErrorList = new ArrayList();
                        string decVars = env.paramDecimalDeclarations.ToString();
                        if (decVars.Length != 0)
                        {
                            int x = (exp.IndexOf("(:%DECL%:)"));
                            if (x < 0)
                            {
                                exp = decVars + exp;
                            }
                            else
                            {
                                exp = exp.Substring(0, x) + decVars + exp.Substring(x + 13);
                            }
                        }
                        string vars = env.paramDeclarations.ToString();
                        if (vars.Length != 0)
                        {
                            int x = (exp.IndexOf("(:%VARDECL%:)"));
                            if (x < 0)
                            {
                                exp = vars + exp;
                            }
                            else
                            {
                                exp = exp.Substring(0, x) + vars + exp.Substring(x + 13);
                            }
                        }
                         ModuleResolver mr = new ModuleResolver(xpc);
                         mr.setTestCase(testCase);
                          testXqc.QueryResolver = mr;// .setModuleURIResolver(mr); //TODO
						testXqc.QueryResolver = mr;
                        try
                        {
                            XQueryExecutable q = testXqc.Compile(exp);
                            if (optimization != null)
                            {
                                // Test whether required optimizations have been performed
                                XdmDestination expDest = new XdmDestination();
                                JConfiguration config = driverProc.Implementation;
                                //ExpressionPresenter presenter = new ExpressionPresenter(config, expDest.getReceiver(config));
                                //q.getUnderlyingCompiledQuery().explain(presenter);
                                //presenter.close();
                                XdmNode explanation = expDest.XdmNode;
                                XdmItem optResult = xpc.EvaluateSingle(optimization.GetAttributeValue(new QName("assert")), explanation);
                                if (((XdmAtomicValue)optResult).GetBooleanValue())
                                {
                                    println("Optimization result OK");
                                }
                                else
                                {
                                    println("Failed optimization test");
                                    Serializer ser = new Serializer();
                                    ser.SetOutputStream((Stream)System.Console.OpenStandardError());
                                    driverProc.WriteXdmValue(explanation, ser);
                                    writeTestcaseElement(testCaseName, "fail", "Failed optimization assertions", spec);
                                    failures++;
                                    return;
                                }

                            }
                            XQueryEvaluator selector = q.Load();
                            foreach (QName varName in env.params1.Keys)
                            {
                                selector.SetExternalVariable(varName, env.params1[varName]);
                            }
                            if (env.contextItem != null)
                            {
                                selector.ContextItem = env.contextItem;
                            }
							selector.InputXmlResolver = env;
                            //selector.InputXmlResolver =  .SetURIResolver(new TestURIResolver(env)); //TODO
                            if (env.unparsedTextResolver != null)
                            {
                                selector.Implementation.setUnparsedTextURIResolver(env.unparsedTextResolver);// TODO
                            }
                            if (needSerializedResult)
                            {
                                StringWriter sw = new StringWriter();
                                Serializer serializer = new Serializer(); //env.processor.NewSerializer(sw); //TODO
                                serializer.SetOutputWriter(sw);
                                selector.Run(serializer);
                                outcome.SetPrincipalSerializedResult(sw.ToString());
                            }
                            if (needResultValue)
                            {
                                XdmValue result = selector.Evaluate();
                                outcome.SetPrincipalResult(result);
                            }
                        }
                        catch (Exception err)
                        {
							println("in TestSet " + testSetName + err.StackTrace);
                            println(err.Message);
                            outcome.SetException(err);
							outcome.SetErrorsReported((IList)testXqc.ErrorList);
                        }
                    }
                    else
                    {
                        writeTestcaseElement(testCaseName, "notRun", "No processor found", spec);
                        notrun++;
                        return;
                    }
                }

                if (env.resetAction != null)
                {
                    env.resetAction.reset(env);
                }
                XdmNode assertion;
                if (alternativeResult != null)
                {
                    assertion = (XdmNode)xpc.EvaluateSingle("*[1]", alternativeResult);
                }
                else
                {
                    assertion = (XdmNode)xpc.EvaluateSingle("result/*[1]", testCase);
                }
                if (assertion == null)
                {
                    println("*** No assertions found for test case " + testCaseName);
                    writeTestcaseElement(testCaseName, "disputed", "No assertions in test case", spec);
                    feedback.Feedback(successes, failures++, total);
                    return;
                }
                XPathCompiler assertXpc = env.processor.NewXPathCompiler();
                assertXpc.XPathLanguageVersion = "3.1";
                assertXpc.DeclareNamespace("fn", "http://www.w3.org/2005/xpath-functions");
                assertXpc.DeclareNamespace("xs", "http://www.w3.org/2001/XMLSchema");
                assertXpc.DeclareNamespace("math", "http://www.w3.org/2005/xpath-functions/math");
                assertXpc.DeclareNamespace("map", "http://www.w3.org/2005/xpath-functions/map");
                assertXpc.DeclareNamespace("array", "http://www.w3.org/2005/xpath-functions/array");
                assertXpc.DeclareNamespace("j", "http://www.w3.org/2005/xpath-functions");
                assertXpc.DeclareVariable(new QName("result"));

                bool b = outcome.TestAssertion(assertion, outcome.GetPrincipalResultDoc(), assertXpc, xpc, debug);
                if (b)
                {
                    //println("OK");
                    writeTestcaseElement(testCaseName, "pass", null, spec);
                    feedback.Feedback(successes++, failures, total);

                }
                else
                {

                    if (outcome.IsException())
                    {
                        XdmItem expectedError = xpc.EvaluateSingle("result//error/@code", testCase);

                        if (expectedError == null)
                        {
                            //                        if (debug) {
                            //                            outcome.getException().printStackTrace(System.out);
                            //                        }
                          
                            writeTestcaseElement(testCaseName, "fail", "Expected success, got ", spec);
                            println("*** fail, result " + outcome.GetException() +
                                    " Expected success.");
                            feedback.Feedback(successes, failures++, total);
                        }
                        else
                        {
                            writeTestcaseElement(testCaseName, "wrongError",
                                    "Expected error:" + expectedError.ToString() + ", got " + outcome.GetErrorCode().LocalName, spec);
                            println("*** fail, result " + outcome.GetErrorCode().LocalName +
                                    " Expected error:" + expectedError.ToString());
                            wrongErrorResults++;
                            feedback.Feedback(successes++, failures, total);
                        }

                    }
                    else
                    {
                        writeTestcaseElement(testCaseName, "fail", "Wrong results, got " +
                                truncate(outcome.Serialize(assertXpc.Processor, outcome.GetPrincipalResultDoc())), spec);
                        feedback.Feedback(successes, failures++, total);
                        if (debug)
                        {
                            try
                            {
                                println("Result:");
                                driverProc.WriteXdmValue(outcome.GetPrincipalResult(), driverSerializer);
                                println("<=======");
                            }
                            catch (Exception err)
                            {
                            }
                            //println(outcome.getResult());
                        }
                        else
                        {
                            println("*** fail (use -debug to show actual result)");
                            //failures++;
                        }
                    }
                }
            }
        }

        private string truncate(string in1)
        {
            if (in1.Length > 80)
            {
                return in1.Substring(0, 80) + "...";
            }
            else
            {
                return in1;
            }
        }



        public void writeResultFilePreamble(Processor processor, XdmNode catalog)
        {
            resultsDoc.writeResultFilePreamble(processor, catalog);
        }

        public void writeResultFilePostamble()
        {
            resultsDoc.writeResultFilePostamble();
        }


        public void startTestSetElement(XdmNode testSetNode)
        {
            resultsDoc.startTestSetElement(testSetNode);
        }


        protected void writeTestSetEndElement()
        {
            resultsDoc.endElement();
        }

        private void writeTestcaseElement(string name, string result, string comment, Spec specOpt)
        {
            resultsDoc.writeTestcaseElement(name, result, comment);
        }

		public class ModuleResolver : IQueryResolver {

            XPathCompiler catXPC;
            XdmNode testCase;

            public ModuleResolver(XPathCompiler xpc)
            {
                this.catXPC = xpc;
            }

            public void setTestCase(XdmNode testCase)
            {
                this.testCase = testCase;
            }


			public Object GetEntity(Uri absoluteUri) {
				String fullPath = absoluteUri.AbsolutePath;
				Stream stream = new FileStream(fullPath, FileMode.Open, FileAccess.Read);
				//javax.xml.transform.Source sourcei = new javax.xml.transform.stream.StreamSource(uri);

				return stream;
			}

			internal Uri resolve(Uri basei, String child) { 
			
				return (new XmlUrlResolver ()).ResolveUri (testCase.BaseUri, child);
			
			
			}


			public Uri[] GetModules(String moduleUri, Uri baseUri, String[] locationHints){
				XdmValue files = catXPC.Evaluate("./module[@uri='" + moduleUri + "']/@file/string()", testCase);
				if (files.Count == 0) {
					throw new Exception("Failed to find module entry for " + moduleUri);
				}

				Uri[] fullPaths = new Uri[1];


				fullPaths [0] = resolve (baseUri, files.Unwrap().head().getStringValue());
				
			
				return fullPaths;
			}

            /*public StreamSource[] resolve(string moduleURI, string baseURI, string[] locations) {
                try {
                    XdmValue files = catXPC.Evaluate("./module[@uri='" + moduleURI + "']/@file/string()", testCase);
                    if (files.size() == 0) {
                        throw new XPathException("Failed to find module entry for " + moduleURI);
                    }
                    StreamSource[] ss = new StreamSource[files.size()];
                    for (int i = 0; i < files.size(); i++) {
                        URI uri = testCase.getBaseURI().resolve(files.itemAt(i).ToString());
                        ss[i] = getQuerySource(uri);
                    }
                    return ss;
                } catch (SaxonApiException e) {
                    throw new XPathException(e);
                }
            }*/
        }

        public class TestUriResolver : XmlUrlResolver
        {
            Environment env1;

            public TestUriResolver(Environment env)
            {
                this.env1 = env;
            }

            public override Uri ResolveUri(Uri base1, string href)
            {
                XdmNode node = null;
                try
                {
                    node = env1.sourceDocs[href];
                }
                catch (Exception)
                {
                    try
                    {
                        return (new XmlUrlResolver()).ResolveUri(base1, href);
                    }
                    catch (Exception) {
                        return null;
                    }

                }
                if (node == null)
                {
                    return null;
                }
                else
                {
                    return node.DocumentUri;
                }
            }
        }



        /**
         * Static method called as an external function call to evaluate a literal when running in "unfolded" mode.
         * The function simply returns the value of its argument - but the optimizer doesn't know that, so it
         * can't pre-evaluate the call at compile time.
         *
         * @param value the value to be returned
         * @return the supplied value, unchanged
         */

        /*public static Sequence lazyLiteral(Sequence value) {
            return value;
        } */

        /**
         * Collect together information about all the dependencies of tests that use a given environment
         * @param processor the Saxon processor
         * @param env the environment for which dependency information is to be gathered
         * @
         */

        private void buildDependencyDictionary(Processor processor, Environment env)
        {
            XQueryCompiler xqCompiler = processor.NewXQueryCompiler();
            xqCompiler.XQueryLanguageVersion = "3.0";
			xqCompiler.BaseUri = testSuiteDir;
            XdmValue result = xqCompiler.Compile(
			"        declare namespace fots = \"http://www.w3.org/2010/09/qt-fots-catalog\";\n" +
			"        let $testsets := doc('" + testSuiteDir + "/catalog.xml')//fots:test-set/@file/doc(resolve-uri(., exactly-one(base-uri(.))))\n" +
			"        for $dependencyTS in $testsets//fots:dependency\n" +
			"        let $name := $dependencyTS/@type\n" +
			"        let $value := $dependencyTS/@value\n" +
			"        group by $name, $value\n" +
			"        order by $name, $value\n" +
			"        return <dependency type='{$name}' value='{$value}' />").Load().Evaluate();


            foreach (XdmItem item in result)
            {
                XdmNode node = (XdmNode)item;
                string type = node.GetAttributeValue(new QName("type"));
                string value = node.GetAttributeValue(new QName("value"));
                addDependency(type, value, dependencyIsSatisfied(node, env));
            }


        }


        protected class Dependency
        {
            public string dType;
            public bool satisfied;
        }


        public void addDependency(string depStr, string value, bool satisfied)
        {
            if (!dependencyDictionary.ContainsKey(value))
            {
                Dependency dep = new Dependency();
                dep.dType = depStr;
                dep.satisfied = satisfied;
                dependencyDictionary.Add(value, dep);
            }
        }

       
            


        
    }
}

//
// The contents of this file are subject to the Mozilla Public License Version
// 1.0 (the "License");
// you may not use this file except in compliance with the License. You may
// obtain a copy of the
// License at http://www.mozilla.org/MPL/
//
// Software distributed under the License is distributed on an "AS IS" basis,
// WITHOUT WARRANTY OF ANY KIND, either express or implied.
// See the License for the specific language governing rights and limitations
// under the License.
//
// The Original Code is: all this file.
//
// The Initial Developer of the Original Code is Michael Kay,
//
// Portions created by (your name) are Copyright (C) (your legal entity). All
// Rights Reserved.
//
// Contributor(s): none.
//


