using System;
using System.Collections;
using System.Collections.Generic;
using System.Text;
using Saxon.Api;
using System.Xml;
using System.IO;
using System.Text.RegularExpressions;
using System.Globalization;
using TestRunner;


//using net.sf.saxon.Version;
using JFeatureKeys = net.sf.saxon.lib.FeatureKeys;

namespace TestRunner
{



public abstract class TestDriver {

    protected string resultsDir = null;
    public ResultsDocument resultsDoc;
    protected int successes = 0;
    protected int total = 200000;
    protected int failures = 0;
    protected int notrun = 0;
    protected int wrongErrorResults = 0;
    protected bool unfolded = false;
    protected bool saveResults = false;
    protected int generateByteCode = 0;
    protected TreeModel treeModel = TreeModel.TinyTree;
    protected bool debug = false;
    protected Regex testPattern = null;
    protected string requestedTestSet = null;
    protected string testSuiteDir;
    protected Processor driverProc = new Processor(true);
    protected Serializer driverSerializer;
	protected Spec spec;
    protected Dictionary<string, XdmNode> exceptionsMap = new Dictionary<string, XdmNode>();
    protected Dictionary<string, Environment> globalEnvironments = new Dictionary<string, Environment>();
    protected Dictionary<string, Environment> localEnvironments = new Dictionary<string, Environment>();
	protected IFeedbackListener feedback = new DefaultFeedbackListener();
	protected bool useXslt30Transformer = true;  // Temporary for controlling test processor


    public abstract string catalogNamespace();

    public int GenerateByteCode {
        get { return generateByteCode; }
        set { generateByteCode = value; }
    }

		public class DefaultFeedbackListener : IFeedbackListener
		{

			public void Feedback(int passed, int failed, int total)
			{
				Console.WriteLine("Done " + (passed + failed) + " of " + total);
		

			}


			public void Message(String message, bool popup)
			{
				Console.WriteLine (message);
			}
		}


		public Spec Spec {
			get{return spec;}
		}
    public void setFeedbackListener(IFeedbackListener f)
    {
        feedback = f;
    }

    public int Failures
    {
        get { return failures; }
        set { failures = value; }
    }

    public bool Unfolded
    {
        get { return unfolded; }
        set { unfolded = value; }
    }

    public TreeModel TreeModel
    {
        get { return treeModel; }
        set { treeModel = value; }
    }
    public void go(string[] args) {

    //    AutoActivate.activate(driverProc);
        driverSerializer = driverProc.NewSerializer();
        //testSuiteDir = args[0];



        testSuiteDir = args[0];
        if (testSuiteDir.EndsWith("/"))
        {
            testSuiteDir = testSuiteDir.Substring(0, testSuiteDir.Length - 1);
        }
				

        resultsDir = args[1];
        if (resultsDir.EndsWith("/"))
        {
            resultsDir = resultsDir.Substring(0, resultsDir.Length - 1);
        }
        string catalog = testSuiteDir + "/catalog.xml";
        string specStr = null;

        for (int i = 2; i < args.Length; i++) {
            if (args[i].StartsWith("-t:")) {
                testPattern = new Regex(args[i].Substring(3));
            }
            if (args[i].StartsWith("-s:")) {
                requestedTestSet = args[i].Substring(3);
            }
            if (args[i].StartsWith("-debug")) {
                debug = true;
            }
            if (args[i].Equals("-unfolded")) {
                unfolded = true;
            }
            if (args[i].Equals("-save"))
            {
                saveResults = true;
            }
            if (args[i].StartsWith("-bytecode"))
            {
                if (args[i].Substring(10).Equals("on"))
                {
                    generateByteCode = 1;
                }
                else if (args[i].Substring(10).Equals("debug"))
                {
                    generateByteCode = 2;
                }
                else
                {
                    generateByteCode = 0;
                }
            }
           /* if (args[i].StartsWith("-tree"))
            {
                if (args[i].Substring(6).EqualsIgnoreCase("dom"))
                {
                    treeModel = new DOMObjectModel();
                }
                else if (args[i].Substring(6).EqualsIgnoreCase("jdom"))
                {
                    treeModel = new JDOMObjectModel();
                }
                else if (args[i].Substring(6).EqualsIgnoreCase("jdom2"))
                {
                    treeModel = new JDOM2ObjectModel();
                }
                else if (args[i].Substring(6).EqualsIgnoreCase("dom4j"))
                {
                    treeModel = new DOM4JObjectModel();
                }
                else if (args[i].Substring(6).EqualsIgnoreCase("xom"))
                {
                    treeModel = new XOMObjectModel();
                }
                else if (args[i].Substring(6).EqualsIgnoreCase("axiom"))
                {
                    treeModel = new AxiomObjectModel();
                }
                else if (args[i].Substring(6).EqualsIgnoreCase("tinytree"))
                {
                    treeModel = TreeModel.TINY_TREE;
                }
                else if (args[i].Substring(6).EqualsIgnoreCase("condensed"))
                {
                    treeModel = TreeModel.TINY_TREE_CONDENSED;
                }
                else if (args[i].Substring(6).EqualsIgnoreCase("linked"))
                {
                    treeModel = TreeModel.LINKED_TREE;
                }
                else
                {
                    throw new Exception("The TreeModel specified does not exist");
                }
            }*/
            if (args[i].StartsWith("-lang"))
            {
                specStr = args[i].Substring(6);
                processSpec(specStr);
            }

			// Temporary for controlling test processor
			if (args[i].StartsWith("-xt30")) {
				if (args[i].Substring(6).Equals("on")) {
					useXslt30Transformer = true;
				} else if (args[i].Substring(6).Equals("off")) {
					useXslt30Transformer = false;
				}
			}
        }
        if (resultsDir == null)
        {
            feedback.Message("No results directory specified (use -o:dirname)", true);
            /*if (guiForm == null)
            {
                System.exit(2);
            }*/
        }
        if (resultsDoc == null)
        {
            feedback.Message("No results document specified (use -lang option)", true);
            /*if (guiForm == null)
            {
                System.exit(2);
            }*/
        }

        driverSerializer.SetOutputStream(System.Console.OpenStandardError());
        driverSerializer.SetOutputProperty(Serializer.OMIT_XML_DECLARATION, "yes");
        processCatalog(catalog);
        printResults(resultsDir + "/results" + driverProc.ProductVersion + ".xml");
    }

    public string getResultsDir() {
        return resultsDir;
    }

    public abstract void processSpec(string specStr);


    private void processCatalog(String catalogFile) {
       if (generateByteCode == 1) {
            driverProc.SetProperty(JFeatureKeys.GENERATE_BYTE_CODE, "true");
            driverProc.SetProperty(JFeatureKeys.DEBUG_BYTE_CODE, "false");
        } else if (generateByteCode == 2) {
            driverProc.SetProperty(JFeatureKeys.GENERATE_BYTE_CODE, "true");
            driverProc.SetProperty(JFeatureKeys.DEBUG_BYTE_CODE, "true");
        } else {
            driverProc.SetProperty(JFeatureKeys.GENERATE_BYTE_CODE, "false");
            driverProc.SetProperty(JFeatureKeys.DEBUG_BYTE_CODE, "false");
        }
        DocumentBuilder catbuilder = driverProc.NewDocumentBuilder();
        catbuilder.TreeModel = treeModel;
        XdmNode catalog = catbuilder.Build(new Uri(catalogFile.ToString())); 
        XPathCompiler xpc = driverProc.NewXPathCompiler();
        //xpc.XPathLanguageVersion = "3.1";
        xpc.Caching = true;
        xpc.DeclareNamespace("", catalogNamespace());

        createGlobalEnvironments(catalog, xpc);

        try {
            writeResultFilePreamble(driverProc, catalog);
        } catch (Exception e) {
            System.Console.WriteLine(e.Message);
        }

        readExceptionsFile();


        if (requestedTestSet != null) {
            try {
                XdmNode funcSetNode = (XdmNode) xpc.EvaluateSingle("//test-set[@name='" + requestedTestSet + "']", catalog);
                if (funcSetNode == null) {
                    throw new Exception("Test-set " + requestedTestSet + " not found!");
                }
                processTestSet(catbuilder, xpc, funcSetNode);
            } catch (Exception e1) {
                //e1.printStackTrace();
                System.Console.WriteLine(e1.Message);
            }
        } else {
            foreach (XdmItem testSet in xpc.Evaluate("//test-set", catalog)) {
                processTestSet(catbuilder, xpc, ((XdmNode) testSet.Simplify));
            }
        }
        try {
            writeResultFilePostamble();
        } catch (Exception e) {
            System.Console.WriteLine(e.Message);
            //e.printStackTrace();
        }


    }

    /**
     * Look for an exceptions.xml document with the general format:
     * <p/>
     * <exceptions xmlns="...test catalog namespace...">
     * <exception test-set ="testset1" test-case="testcase" run="yes/no/not-unfolded"
     * bug="bug-reference" reason="">
     * <results>
     * ... alternative expected results ...
     * </results>
     * <optimization>
     * ... assertions about the "explain" tree
     * </optimization>
     * </exception>
     * </exceptions>
     */

    protected void readExceptionsFile() {

        XdmNode exceptionsDoc = null;
        DocumentBuilder exceptBuilder = driverProc.NewDocumentBuilder();
        QName testCase = new QName("", "test-case");
        try {
            exceptionsDoc = exceptBuilder.Build(new Uri(resultsDir + "/exceptions.xml"));
            IEnumerator iter = exceptionsDoc.EnumerateAxis(XdmAxis.Descendant, new QName("", "exception"));
            while (iter.Current != null) {
                XdmNode entry = (XdmNode) iter.Current;
                string test = entry.GetAttributeValue(testCase);
                if (test != null) {
                    exceptionsMap.Add(test, entry);
                }
                iter.MoveNext();
            }
        } catch (Exception e) {
            feedback.Message("*** Failed to process exceptions file: "+ e.Message, true);
            //printError("*** Failed to process exceptions file: ", e.getMessage()); //TODO - review this code later
        }

    }

    protected abstract void createGlobalEnvironments(
            XdmNode catalog, XPathCompiler xpc);

    protected void createLocalEnvironments(XdmNode testSetDocNode) {
        localEnvironments.Clear();
        Environment defaultEnvironment =
				Environment.createLocalEnvironment(testSetDocNode.BaseUri, generateByteCode, unfolded, spec);
        localEnvironments.Add("default", defaultEnvironment);
    }

    protected Environment getEnvironment(XdmNode testCase, XPathCompiler xpc) {
        string testCaseName = testCase.GetAttributeValue(new QName("name"));
        XdmNode environmentNode = (XdmNode) xpc.EvaluateSingle("environment", testCase);
        Environment env;
        if (environmentNode == null) {
            env = localEnvironments["default"];
        } else {
            string envName = environmentNode.GetAttributeValue(new QName("ref"));
            if (envName == null || envName.Equals("")) {
                env = Environment.processEnvironment(this, xpc, environmentNode, null, localEnvironments["default"]);
                bool baseUriCheck = ((XdmAtomicValue) xpc.EvaluateSingle("static-base-uri/@uri='#UNDEFINED'", environmentNode)).GetBooleanValue();
                if (baseUriCheck) {
                    //writeTestcaseElement(testCaseName, "notRun", "static-base-uri not supported", null);
                    return null;
                }
            } else {
                try
                {
                    env = localEnvironments[envName];
                }catch(Exception){
                    env = null;
                }
                if (env == null) {
                    try
                    {
                        env = globalEnvironments[envName];
                    }
                    catch (Exception e) { }
                }
                if (env == null) {
                    foreach (XdmItem e in xpc.Evaluate("//environment[@name='" + envName + "']", testCase)) {
                        Environment.processEnvironment(this, xpc, e, localEnvironments, localEnvironments["default"]);
                    }
                    try
                    {
                        env = localEnvironments[envName];
                    } catch(Exception e) {}
                }
                if (env == null) {
                    System.Console.WriteLine("*** Unknown environment " + envName);
                    //println("*** Unknown environment " + envName);
                    //writeTestcaseElement(testCaseName, "fail", "Environment " + envName + " not found", null);
                    failures++;
                    return null;
                }

            }
        }
        return env;
    }

    public void writeResultFilePreamble(Processor processor, XdmNode catalog) {
        resultsDoc.writeResultFilePreamble(processor, catalog);
    }

    public void writeResultFilePostamble(){
        resultsDoc.writeResultFilePostamble();
    }

    public void startTestSetElement(XdmNode testSetNode) {
        resultsDoc.startTestSetElement(testSetNode);
    }

    public void writeTestSetEndElement() {
        resultsDoc.endElement();
    }


    private void processTestSet(DocumentBuilder catbuilder, XPathCompiler xpc, XdmNode testSetNode) {
        string testName;
        string testSet;
        startTestSetElement(testSetNode);
        Uri testSetFile = new Uri(testSuiteDir + "/" + testSetNode.GetAttributeValue(new QName("file")));
        XdmNode testSetDocNode = catbuilder.Build(testSetFile);
        createLocalEnvironments(testSetDocNode);
        bool run = true;
        // TODO: this won't pick up any test-set level dependencies in the XSLT 3.0 catalog
        if (((XdmAtomicValue) xpc.Evaluate("exists(/test-set/dependency)", testSetDocNode).Simplify).GetBooleanValue()) {
            foreach (XdmItem dependency in xpc.Evaluate("/test-set/dependency", testSetDocNode)) {
                if (!dependencyIsSatisfied((XdmNode) dependency, localEnvironments["default"])) {
                    run = false;
                }
            }
        }
        if (run) {
            if (testPattern == null) {
                foreach (XdmItem env in xpc.Evaluate("//environment[@name]", testSetDocNode)) {
                    Environment.processEnvironment(this, xpc, env, localEnvironments, localEnvironments["default"]);
                }
            }
            testSet = xpc.EvaluateSingle("/test-set/@name", testSetDocNode).ToString();
            foreach (XdmItem testCase in xpc.Evaluate("//test-case", testSetDocNode)) {

                testName = xpc.EvaluateSingle("@name", testCase).ToString();

                ////
                //if (testName.Equals("type-0174"))
                //{
                //    int num = 0;
                //    System.Console.WriteLine("Test driver" + num);

                //}

                ///
                if (testPattern != null && !testPattern.IsMatch(testName)) {
                    continue;
                }
                println("-s:" + testSet + " -t:" + testName);

                runTestCase((XdmNode) testCase, xpc);
            }
        }
        writeTestSetEndElement();
    }

    protected abstract void runTestCase(XdmNode testCase, XPathCompiler catalogXpc);

    //public void setTestDriverForm(TestDriverForm gui) {
    //    guiForm = gui;
    //}

    public void println(string data)
    {
        //if (guiForm != null)
        //{
            //guiForm.setResultTextArea(data);
        //}
        //else
        {
            feedback.Message(data+"\n", false);
        }
    }

    public void printResults(string resultsFileStr)
    {
       /* if (guiForm != null)
        {
            guiForm.printResults("Result: " + successes + " successes, " + failures + " failures, " + wrongErrorResults + " incorrect ErrorCode, " + notrun + " not run", resultsFileStr, resultsDir);
        }
        else
        {*/
        feedback.Message(successes + " successes, " + failures + " failures, " + wrongErrorResults + " incorrect ErrorCode, " + notrun + " not run", false);
        //}
    }

    public void printError(string error, string message)
    {
        /*if (guiForm != null)
        {
            guiForm.errorPopup(error);
            System.err.println(error + message);
        }
        else
        {*/
        feedback.Message(error + message, true);
        //}
    }

    public abstract bool dependencyIsSatisfied(XdmNode dependency, Environment env);

}

//
// The contents of this file are subject to the Mozilla Public License Version 1.0 (the "License");
// you may not use this file except in compliance with the License. You may obtain a copy of the
// License at http://www.mozilla.org/MPL/
//
// Software distributed under the License is distributed on an "AS IS" basis,
// WITHOUT WARRANTY OF ANY KIND, either express or implied.
// See the License for the specific language governing rights and limitations under the License.
//
// The Original Code is: all this file.
//
// The Initial Developer of the Original Code is Saxonica Limited
//
// Portions created by (your name) are Copyright (C) (your legal entity). All Rights Reserved.
//
// Contributor(s): none.
//

}
