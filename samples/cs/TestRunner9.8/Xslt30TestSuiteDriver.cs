using System;
using System.Collections;
using System.Collections.Generic;
using System.Text;
using System.Xml;
using System.IO;
using System.Text.RegularExpressions;
using System.Globalization;
using Saxon.Api;


using JFeatureKeys = net.sf.saxon.lib.FeatureKeys;
using JConfiguration = net.sf.saxon.Configuration;
using JVersion = net.sf.saxon.Version;
using JResult = javax.xml.transform.Result;

namespace TestRunner
{
/**
 * This class runs the W3C XSLT Test Suite, driven from the test catalog.
 */
public class Xslt30TestSuiteDriver : TestDriver {

    public static void MainXXXX(string[] args) {
        if (args.Length == 0 || args[0].Equals("-?")) {
            System.Console.WriteLine("testsuiteDir catalog [-o:resultsdir] [-s:testSetName]" +
                    " [-t:testNamePattern] [-bytecode:on|off|debug] [-tree] [-lang] [-save]");
        }

        System.Console.WriteLine("Testing Saxon " + (new Processor()).ProductVersion);
        new Xslt30TestSuiteDriver().go(args);
    }


    
    public override string catalogNamespace() {
        return "http://www.w3.org/2012/10/xslt-test-catalog";
    }

    public void writeResultFilePreamble(Processor processor, XdmNode catalog) {
        resultsDoc = new Xslt30ResultsDocument(this.resultsDir, Spec.XT30);
        //super.writeResultFilePreamble(processor, catalog);
    }

    
    public override void processSpec(string specStr) {
			if (specStr.Equals("XT10")) {
				spec = Spec.XT10;
			} else if (specStr.Equals("XT20")) {
				spec = Spec.XT20;
			} else if (specStr.Equals("XT30")) {
				spec = Spec.XT30;
			} else {
				throw new Exception("Unknown spec " + specStr);
			}
        resultsDoc = new Xslt30ResultsDocument(this.resultsDir, Spec.XT30);
        // No action: always use XSLT
    }

    
    protected override void createGlobalEnvironments(XdmNode catalog, XPathCompiler xpc) {
        Environment environment = null;
        Environment defaultEnv = null;
         try
            {
                defaultEnv = localEnvironments["default"];
            }
            catch (Exception) { }
        foreach (XdmItem env in xpc.Evaluate("//environment", catalog)) {
            environment = Environment.processEnvironment(this, xpc, env, globalEnvironments, defaultEnv);
        }
        //buildDependencyMap(driverProc, environment);
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

		internal Dictionary <QName, XdmValue> GetNamedParameters(XPathCompiler xpath, XdmNode node, bool getStatic, bool tunnel)  {
			Dictionary<QName, XdmValue> params1 = new Dictionary<QName, XdmValue>();
			int i = 1;
			string staticTest = getStatic ? "@static='yes'" : "not(@static='yes')";
			foreach (XdmItem parami in xpath.Evaluate("param[" + staticTest + "]", node)) {
				QName name = GetQNameAttribute(xpath, parami, "@name");
				string select = ((XdmNode) parami).GetAttributeValue(new QName("select"));
				string tunnelled = ((XdmNode) parami).GetAttributeValue(new QName("tunnel"));
				bool required = tunnel == (tunnelled != null && tunnelled.Equals("yes"));
				XdmValue value;
				if (name == null) {
					System.Console.WriteLine("*** No name for parameter " + i + " in initial-template");
					throw new Exception("*** No name for parameter " + i + " in initial-template");
				}
				try {
					value = xpath.Evaluate(select, null);
					i++;
				} catch (Exception e) {
					System.Console.WriteLine("*** Error evaluating parameter " + name + " in initial-template : " + e.Message);
					throw e;
				}
				if (required) {
					params1.Add(name, value);
				}
			}
			return params1;
		}

		internal XdmValue[] getParameters(XPathCompiler xpath, XdmNode node) {
			List<XdmValue> params1 = new List<XdmValue>();

			int i = 1;
			foreach (XdmItem param in xpath.Evaluate("param[not(@static='yes')]", node)) {
				string select = ((XdmNode) param).GetAttributeValue(new QName("select"));
				XdmValue value;
				try {
					value = xpath.Evaluate(select, null);
					i++;
				} catch (Exception e) {
					System.Console.WriteLine("*** Error evaluating parameter " + i + " in initial-function : " + e.Message);
					throw e;
				}
				params1.Add(value);
			}
			return params1.ToArray();
		}



		internal static QName GetQNameAttribute(XPathCompiler xpath, XdmItem contextItem, string attributePath) {
			string exp = "for $att in " + attributePath +
				" return if (contains($att, ':')) then resolve-QName($att, $att/..) else " +
				" if (contains($att,'{')) then QName(substring-before(substring-after($att,'{'),'}'),substring-after($att,'}')) else" +
				" QName('', $att)";
			XdmAtomicValue qname = (XdmAtomicValue) xpath.EvaluateSingle(exp, contextItem);

			return qname == null ? null : new QName(qname.ToString());
		}

    private bool isSlow(string testName) {
			return testName.StartsWith("regex-classes")||
				testName.Equals("normalize-unicode-008");
    }


    protected override void runTestCase(XdmNode testCase, XPathCompiler xpath)  {

        TestOutcome outcome = new TestOutcome(this);
        string testName = testCase.GetAttributeValue(new QName("name"));
        string testSetName = testCase.Parent.GetAttributeValue(new QName("name"));
        ////
        if (testName.Equals("type-0174"))
        {
            int num = 0;
            System.Console.WriteLine("Test driver" + num);

        }

        ///
        if (exceptionsMap.ContainsKey(testName)) {
            notrun++;
            resultsDoc.writeTestcaseElement(testName, "notRun", exceptionsMap[testName].GetAttributeValue(new QName("reason")));
            return;
        }

        if (exceptionsMap.ContainsKey(testName) || isSlow(testName)) {
            notrun++;
            resultsDoc.writeTestcaseElement(testName, "notRun", "requires excessive resources");
            return;
        }

       

        XdmValue specAtt = (XdmValue)(xpath.EvaluateSingle("(/test-set/dependencies/spec/@value, ./dependencies/spec/@value)[last()]", testCase));
        string spec = specAtt.ToString();

        Environment env = getEnvironment(testCase, xpath);
        if(env == null) {
            resultsDoc.writeTestcaseElement(testName, "notRun", "test catalog error");
            return;
        }

        /*if(testName("environment-variable")) {
                        EnvironmentVariableResolver resolver = new EnvironmentVariableResolver() {
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
                }; //TODO
            } */
         //   env.processor.SetProperty(JFeatureKeys.ENVIRONMENT_VARIABLE_RESOLVER, resolver); //TODO
        
        XdmNode testInput = (XdmNode) xpath.EvaluateSingle("test", testCase);
        XdmNode stylesheet = (XdmNode) xpath.EvaluateSingle("stylesheet", testInput);
		XdmNode pack = (XdmNode) xpath.EvaluateSingle("package", testInput);


        foreach (XdmItem dep in xpath.Evaluate("(/test-set/dependencies/*, ./dependencies/*)", testCase)) {
            if (!dependencyIsSatisfied((XdmNode)dep, env)) {
                notrun++;
                resultsDoc.writeTestcaseElement(testName, "notRun", "dependency not satisfied");
                return;
            }
        }

        XsltExecutable sheet = env.xsltExecutable;
       //ErrorCollector collector = new ErrorCollector();
			string baseOutputURI = resultsDir + "/results/output.xml";
			ErrorCollector collector = new ErrorCollector(outcome);
			IList errorList = new List<StaticError> ();
        XmlUrlResolver res = new XmlUrlResolver();
		string xsltLanguageVersion = spec.Contains("XSLT30") || spec.Contains("XSLT20+") ? "3.0" : "2.0";
        if (stylesheet != null) {
            XsltCompiler compiler = env.xsltCompiler;
				compiler.ErrorList = errorList;
            Uri hrefFile = res.ResolveUri(stylesheet.BaseUri, stylesheet.GetAttributeValue(new QName("file")));
            Stream stream = new FileStream(hrefFile.AbsolutePath, FileMode.Open, FileAccess.Read);
            compiler.BaseUri = hrefFile;
            compiler.XsltLanguageVersion = (spec.Contains("XSLT30") || spec.Contains("XSLT20+") ? "3.0" : "2.0");

			foreach (XdmItem param in xpath.Evaluate("param[@static='yes']", testInput)) {
					String name = ((XdmNode) param).GetAttributeValue(new QName("name"));
					String select = ((XdmNode) param).GetAttributeValue(new QName("select"));
					XdmValue value;
					try {
						value = xpath.Evaluate(select, null);
					} catch (Exception e) {
						Console.WriteLine("*** Error evaluating parameter " + name + ": " + e.Message);
						//throw e;
						continue;
					}
					compiler.SetParameter(new QName(name), value);

				}

            try
            {
                sheet = compiler.Compile(stream);
            } catch(Exception err){
					Console.WriteLine (err.Message);
					//Console.WriteLine(err.StackTrace);
					IEnumerator enumerator = errorList.GetEnumerator();
					bool checkCur = enumerator.MoveNext();
					/*if (checkCur && enumerator.Current != null) {
						outcome.SetException ((Exception)(enumerator.Current));
					} else {
						Console.WriteLine ("Error: Unknown exception thrown");
					}*/
					outcome.SetErrorsReported (errorList);
                
                //outcome.SetErrorsReported(collector.GetErrorCodes);
            }

           
          //  compiler.setErrorListener(collector);
			}  else if (pack != null) {
				Uri hrefFile = res.ResolveUri(pack.BaseUri, pack.GetAttributeValue(new QName("file")));
				Stream stream = new FileStream(hrefFile.AbsolutePath, FileMode.Open, FileAccess.Read);

				XsltCompiler compiler = env.xsltCompiler;
				compiler.ErrorList = errorList;
				compiler.XsltLanguageVersion =  (spec.Contains("XSLT30") || spec.Contains("XSLT20+") ? "3.0" : "2.0");
				//compiler.setErrorListener(collector);

				try {
					XsltPackage xpack = compiler.CompilePackage(stream);
					sheet = xpack.Link();
				} catch (Exception err) {
					Console.WriteLine (err.Message);
					IEnumerator enumerator = errorList.GetEnumerator ();
					enumerator.MoveNext ();
					outcome.SetException ((Exception)(enumerator.Current));
					outcome.SetErrorsReported (errorList);
				}
			}

        if (sheet != null) {
            XdmItem contextItem = env.contextItem;
			XdmNode initialMode = (XdmNode) xpath.EvaluateSingle("initial-mode", testInput);
			XdmNode initialFunction = (XdmNode) xpath.EvaluateSingle("initial-function", testInput);
			XdmNode initialTemplate = (XdmNode) xpath.EvaluateSingle("initial-template", testInput);

			QName initialModeName = GetQNameAttribute(xpath, testInput, "initial-mode/@name");
			QName initialTemplateName = GetQNameAttribute(xpath, testInput, "initial-template/@name");

			if (useXslt30Transformer) {
				try {

						bool assertsSerial = xpath.Evaluate("result//(assert-serialization|assert-serialization-error|serialization-matches)", testCase).Count > 0;
						bool resultAsTree = env.outputTree;
						bool serializationDeclared = env.outputSerialize;
						XdmNode needsTree = (XdmNode) xpath.EvaluateSingle("output/@tree", testInput);
						if (needsTree != null) {
							resultAsTree = needsTree.StringValue.Equals("yes");
						}
						XdmNode needsSerialization = (XdmNode) xpath.EvaluateSingle("output/@serialize", testInput);
						if (needsSerialization != null) {
							serializationDeclared = needsSerialization.StringValue.Equals("yes");
						}
						bool resultSerialized = serializationDeclared || assertsSerial;

						if (assertsSerial) {
							String comment = outcome.GetComment();
							comment = (comment == null ? "" : comment) + "*Serialization " + (serializationDeclared ? "declared* " : "required* ");
							outcome.SetComment(comment);
						}


						Xslt30Transformer transformer = sheet.Load30();
						transformer.InputXmlResolver = env;
						if (env.unparsedTextResolver != null) {
							transformer.GetUnderlyingController.setUnparsedTextURIResolver(env.unparsedTextResolver);
						}

						Dictionary<QName, XdmValue> caseGlobalParams = GetNamedParameters(xpath, testInput, false, false);
						Dictionary<QName, XdmValue> caseStaticParams = GetNamedParameters(xpath, testInput, true, false);
						Dictionary<QName, XdmValue> globalParams = new Dictionary<QName, XdmValue>(env.params1);

						foreach(KeyValuePair<QName, XdmValue> entry in caseGlobalParams) {
							globalParams.Add(entry.Key, entry.Value);

						}

						foreach(KeyValuePair<QName, XdmValue> entry in caseStaticParams) {
							globalParams.Add(entry.Key, entry.Value);

						}


						transformer.SetStylesheetParameters(globalParams);

						if (contextItem != null) {
							transformer.GlobalContextItem = contextItem;
						}

						transformer.MessageListener = collector;

						transformer.BaseOutputURI  = baseOutputURI;

						transformer.MessageListener = new TestOutcome.MessageListener(outcome);

						XdmValue result = null;

						TextWriter sw = new StringWriter();

						Serializer serializer = env.processor.NewSerializer();

						serializer.SetOutputWriter(sw);
						//serializer.setOutputProperty(Serializer.Property.OMIT_XML_DECLARATION, "yes");

						OutputResolver serializingOutput = new OutputResolver(env.processor, outcome, true);
						net.sf.saxon.Controller controller = transformer.GetUnderlyingController;

						controller.setOutputURIResolver(serializingOutput);
						XmlDestination dest = null;
						if (resultAsTree) {
							// If we want non-serialized, we need to accumulate any result documents as trees too
							controller.setOutputURIResolver(
								new OutputResolver(env.processor, outcome, false));
							dest = new XdmDestination();
						}
						if (resultSerialized) {
							dest = serializer;
						}

						Stream src = null;
						Uri srcBaseUri = new Uri("http://uri");
						XdmNode srcNode = null;
						DocumentBuilder builder2 = env.processor.NewDocumentBuilder();

						if (env.streamedPath != null) {
							src = new FileStream(env.streamedPath, FileMode.Open, FileAccess.Read);
							srcBaseUri = new Uri(env.streamedPath);
						} else if (env.streamedContent != null) {
							byte[] byteArray = Encoding.UTF8.GetBytes(env.streamedContent);
							src = new MemoryStream(byteArray);//, "inlineDoc");
							builder2.BaseUri = new Uri("http://uri");
						} else if (initialTemplate == null && contextItem != null) {
							srcNode = (XdmNode) (contextItem);
						}

						if (initialMode != null) {
							QName name = GetQNameAttribute(xpath, initialMode, "@name");
							try {
								if (name != null) {
									transformer.InitialMode = name;
								} else {
									controller.getInitialMode();   /// has the side effect of setting to the unnamed
								}
							} catch (Exception e) {
								if (e.InnerException is net.sf.saxon.trans.XPathException) {
									Console.WriteLine(e.Message);
									outcome.SetException(e);
									//throw new SaxonApiException(e.getCause());
								} else {
									throw e;
								}
							}
						}
						if (initialMode != null || initialTemplate != null) {
							XdmNode init = (XdmNode)(initialMode == null ? initialTemplate : initialMode);
							Dictionary<QName, XdmValue> params1 = GetNamedParameters(xpath, init, false, false);
							Dictionary<QName, XdmValue> tunnelledParams = GetNamedParameters(xpath, init, false, true);
							if (xsltLanguageVersion.Equals("2.0")) {
								if (!(params1.Count == 0  && tunnelledParams.Count == 0)) {
									Console.WriteLine("*** Initial template parameters ignored for XSLT 2.0");
								}
							} else {
								transformer.SetInitialTemplateParameters(params1, false);
								transformer.SetInitialTemplateParameters(tunnelledParams, true);
							}
						}


						if (initialTemplate != null) {
							QName name = GetQNameAttribute(xpath, initialTemplate, "@name");
							transformer.GlobalContextItem = contextItem;
							if (dest == null) {
								result = transformer.CallTemplate(name);
							} else {
								transformer.CallTemplate(name, dest);
							}
						} else if (initialFunction != null) {
							QName name = getQNameAttribute(xpath, initialFunction, "@name");
							XdmValue[] params2 = getParameters(xpath, initialFunction);
							if (dest == null) {
								result = transformer.CallFunction(name, params2);
							} else {
								transformer.CallFunction(name, params2, dest);
							}
						} else {
							if (dest == null) {
								if(src != null) {
									result = transformer.ApplyTemplates(src, srcBaseUri);
								} else {
									result = transformer.ApplyTemplates(srcNode);
								}
							} else {
								if(src != null) {
									transformer.ApplyTemplates(src, dest);
								} else {
									transformer.ApplyTemplates(srcNode, dest);
								}
							}
						}

						//outcome.SetWarningsReported(collector.getFoundWarnings());
						if (resultAsTree && !resultSerialized) {
							result = ((XdmDestination) (dest)).XdmNode;
						}
						if (resultSerialized) {
							outcome.SetPrincipalSerializedResult(sw.ToString());
						}
						outcome.SetPrincipalResult(result);

						if (saveResults) {
							String s = sw.ToString();
							// If a transform result is entirely xsl:result-document, then result will be null
							if (!resultSerialized && result != null) {
								StringWriter sw2 = new StringWriter();
								Serializer se = env.processor.NewSerializer(sw2);
								se.SetOutputProperty(Serializer.OMIT_XML_DECLARATION, "yes");
								env.processor.WriteXdmValue(result, se);
								se.Close();
								s = sw2.ToString();
							}
							// currently, only save the principal result file in the result directory
							saveResultsToFile(s, resultsDir + "/results/" + testSetName + "/" + testName + ".out");
							Dictionary<Uri, TestOutcome.SingleResultDoc> xslResultDocuments = outcome.GetSecondaryResultDocuments();
							foreach (KeyValuePair<Uri, TestOutcome.SingleResultDoc> entry in xslResultDocuments) {
								Uri key = entry.Key;
								String path = key.AbsolutePath;
								String serialization = outcome.Serialize(env.processor, entry.Value);

								saveResultsToFile(serialization, path);
							}
						}
					} catch (Exception err) {
						//if (err.getCause() is XPathException &&
							//!((XPathException) err.getCause()).hasBeenReported()) {
							//System.err.println("Unreported ERROR: " + err.getCause());
						//}
						outcome.SetException(err);
						if (collector.getErrorCodes ().Count > 0) {
							outcome.SetErrorsReported ((IList)collector.getErrorCodes());
						}
						//Console.WriteLine(err.StackTrace);
							/*if(err.getErrorCode() == null) {
                            int b = 3 + 4;  }
                        if(err.getErrorCode() != null)
							outcome.AddReportedError(err.getErrorCode().getLocalName());
						} else {
							outcome.SetErrorsReported(collector.getErrorCodes());
						}*/
					} /*catch (Exception err) {
						err.printStackTrace();
						failures++;
						resultsDoc.writeTestcaseElement(testName, "fail", "*** crashed " + err.getClass() + ": " + err.getMessage());
						return;
					}*/
		} else {

            try {
                XsltTransformer transformer = sheet.Load();
                
                //transformer.SetURIResolver(env); //TODO
                if (env.unparsedTextResolver != null) {
                    transformer.Implementation.setUnparsedTextURIResolver(env.unparsedTextResolver);
                }
                if (initialTemplate != null) {
                    transformer.InitialTemplate = initialTemplateName;
                }
                if (initialMode != null) {
                    transformer.InitialMode = initialModeName;
                }
                foreach (XdmItem param in xpath.Evaluate("param", testInput)) {
                    string name = ((XdmNode)param).GetAttributeValue(new QName("name"));
                    string select = ((XdmNode) param).GetAttributeValue(new QName("select"));
                    XdmValue value = xpath.Evaluate(select, null);
                    transformer.SetParameter(new QName(name), value);
                }
                if (contextItem != null) {
                    transformer.InitialContextNode = (XdmNode)contextItem;
                }
                if (env.streamedPath != null) {
                    transformer.SetInputStream(new FileStream(env.streamedPath, FileMode.Open, FileAccess.Read), testCase.BaseUri);
                }
                foreach (QName varName in env.params1.Keys) {
                    transformer.SetParameter(varName, env.params1[varName]);
                }
                //transformer.setErrorListener(collector);
                transformer.BaseOutputUri = new Uri(resultsDir + "/results/output.xml");
                /*transformer.MessageListener = (new MessageListener() {
                    public void message(XdmNode content, bool terminate, SourceLocator locator) {
                        outcome.addXslMessage(content);
                    }
                });*/


                // Run the transformation twice, once for serialized results, once for a tree.
                // TODO: we could be smarter about this and capture both

                // run with serialization
                StringWriter sw = new StringWriter();
                Serializer serializer = env.processor.NewSerializer(sw);
                transformer.Implementation.setOutputURIResolver(new OutputResolver(driverProc, outcome, true));

            
                transformer.Run(serializer);
               
                outcome.SetPrincipalSerializedResult(sw.ToString());
                if (saveResults) {
                    // currently, only save the principal result file
                    saveResultsToFile(sw.ToString(),
                            resultsDir + "/results/" + testSetName + "/" + testName + ".out");
                }
                transformer.MessageListener = new TestOutcome.MessageListener(outcome);

                // run without serialization
                if (env.streamedPath != null)
                {
                    transformer.SetInputStream(new FileStream(env.streamedPath, FileMode.Open, FileAccess.Read), testCase.BaseUri); 
                }
                XdmDestination destination = new XdmDestination();
                 transformer.Implementation.setOutputURIResolver(
                    new OutputResolver(env.processor, outcome, false));
                transformer.Run(destination);
               
                //transformer. .transform();
                outcome.SetPrincipalResult(destination.XdmNode);
                //}
            } catch (Exception err) {
                outcome.SetException(err);
                //outcome.SetErrorsReported(collector.getErrorCodes());
               // err.printStackTrace();
               // failures++;
                //resultsDoc.writeTestcaseElement(testName, "fail", "*** crashed " + err.Message);
                //return;
            }
        }
        XdmNode assertion = (XdmNode) xpath.EvaluateSingle("result/*", testCase);
        if (assertion == null) {
            failures++;
            resultsDoc.writeTestcaseElement(testName, "fail", "No test assertions found");
            return;
        }
        XPathCompiler assertionXPath = env.processor.NewXPathCompiler();
        //assertionXPath.setLanguageVersion("3.0");
        bool success = outcome.TestAssertion(assertion, outcome.GetPrincipalResultDoc(), assertionXPath, xpath, debug);
        if (success) {
            if (outcome.GetWrongErrorMessage() != null) {
                outcome.SetComment(outcome.GetWrongErrorMessage());
                wrongErrorResults++;
            } else {
                successes++;
            }
            resultsDoc.writeTestcaseElement(testName, "pass", outcome.GetComment());
        } else {
            failures++;
            resultsDoc.writeTestcaseElement(testName, "fail", outcome.GetComment());
        }
    }
}
		

	



   

   internal bool mustSerialize(XdmNode testCase, XPathCompiler xpath) {
        return saveResults ||
                ((XdmAtomicValue) xpath.EvaluateSingle(
                "exists(./result//(assert-serialization-error|serialization-matches|assert-serialization)[not(parent::*[self::assert-message|self::assert-result-document])])", testCase)).GetBooleanValue();
    }

    private void saveResultsToFile(string content, string filePath) {
        try {
            System.IO.File.WriteAllText(filePath, content);
        } catch (IOException e) {
            println("*** Failed to save results to " + filePath);
            throw e;
        }
    }


    
    public override bool dependencyIsSatisfied(XdmNode dependency, Environment env) {
        string type = dependency.NodeName.LocalName;
        string value = dependency.GetAttributeValue(new QName("value"));
        bool inverse = "false".Equals(dependency.GetAttributeValue(new QName("satisfied")));
        if ("spec".Equals(type)) {
				bool atLeast = value.EndsWith("+");
				value = value.Replace("+", "");

				String specName = ((SpecAttr)spec.GetAttr()).svname.Replace("XT", "XSLT");
				int order = value.CompareTo(specName);
				return atLeast ? order <= 0 : order == 0;
        } else if ("feature".Equals(type)) {
//            <xs:enumeration value="backwards_compatibility" />
//            <xs:enumeration value="disabling_output_escaping" />
//            <xs:enumeration value="schema_aware" />
//            <xs:enumeration value="namespace_axis" />
//            <xs:enumeration value="streaming" />
//            <xs:enumeration value="XML_1.1" />

            if ("XML_1.1".Equals(value) && !inverse) {
                if (env != null) {
                    env.processor.XmlVersion = (decimal)1.1;
                    return true;
                } else {
                    return false;
                }
            } else if ("disabling_output_escaping".Equals(value)) {
                return !inverse;
            } else if ("schema_aware".Equals(value)) {
					if (!env.xsltCompiler.SchemaAware) {
                    return false; // cannot use the selected tree model for schema-aware tests
                }
                if (env != null) {
                    env.xsltCompiler.SchemaAware = !inverse;
                }
                return true;
            } else if ("namespace_axis".Equals(value)) {
                return !inverse;
            } else if ("streaming".Equals(value)) {
                return !inverse;
            } else if ("backwards_compatibility".Equals(value)) {
                return !inverse;
            }
            return false;
        } else if ("xsd-version".Equals(type)) {
            if ("1.1".Equals(value)) {
                if (env != null) {
                    env.processor.SetProperty(JFeatureKeys.XSD_VERSION, (inverse ? "1.0" : "1.1"));
                } else {
                    return false;
                }
            } else if ("1.0".Equals(value)) {
                if (env != null) {
                    env.processor.SetProperty(JFeatureKeys.XSD_VERSION, (inverse ? "1.1" : "1.0"));
                } else {
                    return false;
                }
            }
            return true;
        } else if ("available_documents".Equals(type)) {
            return !inverse;
        } else if ("default_language_for_numbering".Equals(type)) {
            return !inverse;
        } else if ("languages_for_numbering".Equals(type)) {
            return !inverse;
        } else if ("supported_calendars_in_date_formatting_functions".Equals(type)) {
            return !inverse;
        } else if ("default_calendar_in_date_formatting_functions".Equals(type)) {
            return !inverse;
        } else if ("maximum_number_of_decimal_digits".Equals(type)) {
            return !inverse;
//        } else if ("collation_uri".Equals(type)) {
//            return !inverse;
//        } else if ("statically_known_collations".Equals(type)) {
//            if (value.Equals("http://www.w3.org/xslts/collation/caseblind") && !inverse) {
//                env.processor.getUnderlyingConfiguration().setCollationURIResolver(
//                        new StandardCollationURIResolver() {
//                            public stringCollator resolve(string uri, string base, Configuration config) {
//                                if ("http://www.w3.org/xslts/collation/caseblind".Equals(uri)) {
//                                    return super.resolve("http://saxon.sf.net/collation?ignore-case=yes", "", config);
//                                } else {
//                                    return super.resolve(uri, base, config);
//                                }
//                            }
//                        }
//                );
//            }
//            // Alternative case-blind collation URI used in QT3 tests
//            if (value.Equals("http://www.w3.org/2010/09/qt-fots-catalog/collation/caseblind") && !inverse) {
//                env.processor.getUnderlyingConfiguration().setCollationURIResolver(
//                        new StandardCollationURIResolver() {
//                            public stringCollator resolve(string uri, string base, Configuration config) {
//                                if ("http://www.w3.org/2010/09/qt-fots-catalog/collation/caseblind".Equals(uri)) {
//                                    return super.resolve("http://saxon.sf.net/collation?ignore-case=yes", "", config);
//                                } else {
//                                    return super.resolve(uri, base, config);
//                                }
//                            }
//                        }
//                );
//            }
//            return true;
        } else if ("default_output_encoding".Equals(type)) {
            return !inverse;
        } else if ("unparsed_text_encoding".Equals(type)) {
            return !inverse;
        } else if ("year_component_values".Equals(type)) {
            return !inverse;
        } else if ("additional_normalization_form".Equals(type)) {
            return !inverse;
        } else if ("recognize_id_as_uri_fragment".Equals(type)) {
            return !inverse;
        } else if ("on-multiple-match".Equals(type)) {
            if (value.Equals("error")) {
                env.xsltCompiler.GetUnderlyingCompilerInfo().setRecoveryPolicy((int)RecoveryPolicy.DoNotRecover);
            } else {
                env.xsltCompiler.GetUnderlyingCompilerInfo().setRecoveryPolicy((int)RecoveryPolicy.RecoverSilently);
            }
            return true;
        } else if ("ignore-doc-failure".Equals(type)) {
            if (value.Equals("false")) {
                env.xsltCompiler.GetUnderlyingCompilerInfo().setRecoveryPolicy((int)RecoveryPolicy.DoNotRecover);
            } else {
                env.xsltCompiler.GetUnderlyingCompilerInfo().setRecoveryPolicy((int)RecoveryPolicy.RecoverSilently);
            }
            return true;
        } else if ("combinations_for_numbering".Equals(type)) {
            return !inverse;
        } else {
            println("**** dependency not recognized: " + type);
            return false;
        }
    }

    /*private static string getCanonicalPath(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException err) {
            return file.getAbsolutePath();
        }
    }*/

    private static QName getQNameAttribute(XPathCompiler xpath, XdmItem contextItem, string attributePath)  {
        string exp = "for $att in " + attributePath +
                " return if (contains($att, ':')) then resolve-QName($att, $att/..) else QName('', $att)";
        XdmAtomicValue qname = (XdmAtomicValue) xpath.EvaluateSingle(exp, contextItem);
        return (qname == null ? null : (QName) qname.Value);
    }

    private class OutputResolver : net.sf.saxon.lib.OutputURIResolver{

        private Processor proc;
        private TestOutcome outcome;
        private XdmDestination destination;
        private java.io.StringWriter stringWriter;
        bool serialized;
        Uri uri;

        public OutputResolver(Processor proc, TestOutcome outcome, bool serialized) {
            this.proc = proc;
            this.outcome = outcome;
            this.serialized = serialized;
        }

        public net.sf.saxon.lib.OutputURIResolver newInstance()
        {
            return new OutputResolver(proc, outcome, serialized);
        }
         XmlUrlResolver res = new XmlUrlResolver();
         public JResult resolve(string href, string base1)
         {
            try {
                uri = res.ResolveUri(new Uri(base1), href);
                if (serialized) {
                  
                    stringWriter = new java.io.StringWriter();
                    javax.xml.transform.stream.StreamResult result =  new javax.xml.transform.stream.StreamResult(stringWriter);
                    result.setSystemId(uri.ToString());
                    return result; 
                } else {
                    destination = new XdmDestination();
                    ((XdmDestination)destination).BaseUri = uri;
						return destination.GetReceiver(proc.Implementation.makePipelineConfiguration());
                }
            } catch (Exception e) {
                throw e;
            }
        }

        public void close(JResult result) {
            if (serialized) {
                outcome.SetSecondaryResult(uri, null, stringWriter.ToString());
            } else {
                XdmDestination xdm = (XdmDestination)destination;
                outcome.SetSecondaryResult(xdm.BaseUri, xdm.XdmNode, null);
            }
        }

    }



}
		



}
