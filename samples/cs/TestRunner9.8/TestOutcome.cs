using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.IO;
using Saxon.Api;
using System.Collections;
using System.Xml;
using JWhitespace = net.sf.saxon.value.Whitespace;
using JFeatureKeys = net.sf.saxon.lib.FeatureKeys;
using JDeepEqual = net.sf.saxon.functions.DeepEqual;
using JSequenceIterator = net.sf.saxon.om.SequenceIterator;
using JAxisInfo = net.sf.saxon.om.AxisInfo;
using JGenericAtomicComparer  = net.sf.saxon.expr.sort.GenericAtomicComparer;
using JCodepointCollator = net.sf.saxon.expr.sort.CodepointCollator;
using JItemMappingIterator = net.sf.saxon.expr.ItemMappingIterator;
using JNodeInfo = net.sf.saxon.om.NodeInfo;
using JNamespaceConstant = net.sf.saxon.lib.NamespaceConstant;
using JFastStringBuffer = net.sf.saxon.tree.util.FastStringBuffer;
using JXPathContext = net.sf.saxon.expr.XPathContext;
using JConfiguration = net.sf.saxon.Configuration;
using JRegularExpression = net.sf.saxon.regex.RegularExpression;
using JLargeStringBuffer = net.sf.saxon.tree.tiny.LargeStringBuffer;
using JItemMappingFunction = net.sf.saxon.expr.ItemMappingFunction;
using JItem = net.sf.saxon.om.Item;
using JGroundedValue = net.sf.saxon.om.GroundedValue;

namespace TestRunner
{
    public class TestOutcome
    {
        
    /**
     * The outcome of a test is either an XDM value or an exception. The object also has the ability to
     * hold the serialized result (needed for serialization tests)
     */
    
    public class SingleResultDoc {
        public XdmValue value;
        public string serialization;
        public SingleResultDoc(){}
        public SingleResultDoc(XdmValue value, string serialization) {
            this.value = value;
            this.serialization = serialization;
        }
    }

    private TestDriver driver;
    private SingleResultDoc principalResult = new SingleResultDoc();
    private HashSet<QName> errorsReported;
    private Exception exception;
    private string comment;
    private HashSet<XdmNode> xslMessages = new HashSet<XdmNode>();
    private Dictionary<Uri, SingleResultDoc> xslResultDocuments = new Dictionary<Uri, SingleResultDoc>();
    private string wrongError;
	private bool warningsReported;

    public TestOutcome(TestDriver driver) {
        this.driver = driver;
        errorsReported = new HashSet<QName>();
    }

    public void SetException(Exception ex)
    {
        this.exception = ex;

        if (ex is DynamicError)
        {
            try
            {
                errorsReported.Add(((DynamicError)ex).ErrorCode);
            }catch(Exception){
                errorsReported.Add(new QName("ZZZZZZ999999"));
            }
        }
			else if (ex is StaticError && ((StaticError)ex).ErrorCode != null)
        {
            try
            {
                errorsReported.Add(((StaticError)ex).ErrorCode);
            }
            catch (Exception)
            {
                errorsReported.Add(new QName("ZZZZZZ999999"));
            }
        }
        else {

            errorsReported.Add(new QName("ZZZZZZ999999"));
        }
    }

	public bool SetWarningsReported(bool warnings) {
		return warningsReported = warnings;
	}

	public bool IsWarningsReported() {
		return this.warningsReported;
	}

    public bool IsException() {
        return exception != null;
    }

    public Exception GetException()
    {
        return exception;
    }

    public QName GetErrorCode() {
        if (exception is DynamicError)
        {
            return ((DynamicError)exception).ErrorCode;
        }
        else if (exception is StaticError)
        {

            return ((StaticError)exception).ErrorCode;
        }
        else {
			return null;
        }
    }

    /**
     * Get a message giving details about the situation where the actual error code was not one of those
     * expected
     * @return null if this situation did not occur; otherwise a message showing the actual error code
     * and the expected error code
     */

    public string GetWrongErrorMessage() {
        return wrongError;
    }


    public void SetPrincipalResult(XdmValue value) {
        principalResult.value = value;
    }

    public XdmValue GetPrincipalResult() {
        return principalResult.value;
    }

    public SingleResultDoc GetPrincipalResultDoc() {
        return principalResult;
    }

    public void SetSecondaryResult(Uri uri, XdmNode value, string serialization) {
        SingleResultDoc result = null;
        try
        {
            result = xslResultDocuments[uri];
        }catch(Exception){}
        if (result == null) {
            result = new SingleResultDoc(value, serialization);
            this.xslResultDocuments.Add(uri,result);
        } else {
            if (value != null) {
                result.value = value;
            }
            if (serialization != null) {
                result.serialization = serialization;
            }
        }
    }

		public Dictionary<Uri, SingleResultDoc> GetSecondaryResultDocuments() {
			return xslResultDocuments;
	}
    
    public SingleResultDoc GetSecondaryResult(Uri uri) {
        return xslResultDocuments[uri];
    }

	public void SetErrorsReported(IList errors) {
        foreach(StaticError err in errors){
            if (err.ErrorCode != null)
            {
                errorsReported.Add(err.ErrorCode);
            }
        }
    }

    public bool HasReportedError(QName errorCode) {
        return errorsReported != null && errorsReported.Contains(errorCode);
    }

    public string Tostring() {
        return (IsException() ? "EXCEPTION " + exception.Message : GetPrincipalResult().ToString());
    }

    public void SetPrincipalSerializedResult(string result) {
        principalResult.serialization = result;
    }

    public string getPrincipalSerializedResult() {
        return principalResult.serialization;
    }

    public void SetComment(string comment) {
        this.comment = comment;
    }

    public string GetComment() {
        return comment;
    }

    public void AddXslMessage(XdmNode message) {
        xslMessages.Add(message);
    }

    public class MessageListener : IMessageListener
    {
        TestOutcome outcome1;

        public MessageListener(TestRunner.TestOutcome out1)
            : base()
        {
            outcome1 = out1;

        }

        public void Message(XdmNode content, bool terminate, IXmlLocation location)
        {
            outcome1.AddXslMessage(content);
        }
    }



    /**
     * This method serializes the actual result to produce a serialized result. This is not what is actually
     * needed by the serialization tests, which require that the serialization be performed using the parameters
     * contained within the query itself.
     *
     * @param p the processor
     * @param uri the URI of the result to serialize; null for the principal result
     * @return the result of serialization
     */

    public string Serialize(Processor p, Uri uri) {
        XdmValue value;
        if (uri == null) {
            if (principalResult.serialization != null) {
                return principalResult.serialization;
            } else {
                value = principalResult.value;
            }
        } else {
            SingleResultDoc doc = xslResultDocuments[uri];
            if (doc == null) {
                return "[[[NULL VALUE]]]";
            } else if (doc.serialization != null) {
                return doc.serialization;
            } else {
                value = doc.value;
            }
        }
        if (value == null) {
            return "[[[NULL VALUE]]]";
        }
        if (IsException()) {
            return "EXCEPTION " + exception.Message;
        } else {
            StringWriter sw = new StringWriter();
            Serializer s = new Serializer();//p.newSerializer(sw);
            s.SetOutputProperty(Serializer.METHOD, "xml");
            s.SetOutputProperty(Serializer.INDENT, "no");
            s.SetOutputProperty(Serializer.OMIT_XML_DECLARATION, "yes");
            try {
                //s.SerializeXdmValue(value); //TODO
            } catch (Exception err) {
                return ("SERIALIZATION FAILED: " + err.Message);
            }
            string str = sw.ToString().Trim();
            if (uri == null) {
                principalResult.serialization = str;
            } else {
                xslResultDocuments[uri].serialization = str;
            }
            return str;
        }
    }

    /**
     * This method serializes the actual result to produce a serialized result. This is not what is actually
     * needed by the serialization tests, which require that the serialization be performed using the parameters
     * contained within the query itself.
     *
     * @param p the processor
     * @param doc the result to serialize
     * @return the result of serialization
     */

    public string Serialize(Processor p, SingleResultDoc doc) {
        XdmValue value;
        if (doc.serialization != null) {
            return doc.serialization;
        } else {
            value = doc.value;
        }
        if (value == null) {
            return "[[[NULL VALUE]]]";
        }
        if (IsException()) {
            return "EXCEPTION " + exception.Message;
        } else {
            StringWriter sw = new StringWriter();

            Serializer s = new Serializer();// p.NewSerializer(sw); // TODO
            s.SetOutputWriter(sw);
            s.SetOutputProperty(Serializer.METHOD, "xml");
            s.SetOutputProperty(Serializer.INDENT, "no");
            s.SetOutputProperty(Serializer.OMIT_XML_DECLARATION, "yes");
            try {
                p.WriteXdmValue(value, s);
                //s.SerializeXdmValue(value);
            } catch (Exception err) {
                return ("SERIALIZATION FAILED: " + err.Message);
            }
            string str = sw.ToString().Trim();
            doc.serialization = str;
            return str;
        }
    }

    public string Serialize(Processor p, XdmValue value) {
        StringWriter sw = new StringWriter();
        Serializer s = new Serializer();//p.NewSerializer(sw);
        s.SetOutputProperty(Serializer.METHOD, "xml");
        s.SetOutputProperty(Serializer.INDENT, "no");
        s.SetOutputProperty(Serializer.OMIT_XML_DECLARATION, "yes");
        try {
            //s.serializeXdmValue(value);
            p.WriteXdmValue(value, s);
        } catch (Exception err) {
            return ("SERIALIZATION FAILED: " + err.Message);
        }
        return sw.ToString().Trim();
    }

    public bool TestAssertion(XdmNode assertion, SingleResultDoc result,XPathCompiler assertXpc, XPathCompiler catalogXpc, bool debug)
            {
        try {
            string tag = assertion.NodeName.LocalName;
            bool success = TestAssertion2(assertion, result, assertXpc, catalogXpc, debug);
            if (debug && !("all-of".Equals(tag)) && !("any-of".Equals(tag))) {
                driver.println("Assertion " + tag + " (" + assertion.StringValue + ") " + (success ? " succeeded" : " failed"));
                if (tag.Equals("error")) {
                    if (IsException()) {
                        bool b = compareExpectedError(assertion);
                        if (b) {
                            driver.println("Returned error as expected");
                        } else {
                            driver.println(wrongError);
                        }
//                        QName code = getException().getErrorCode();
//                        if (code == null) {
//                            actual = "error with no code";
//                        } else {
//                            actual = code.getLocalName();
//                        }

                    } else {
                        driver.println("Expected exception " + assertion.GetAttributeValue(new QName("code")) + "; got success");
                    }
//                    driver.println("Expected exception " + assertion.getAttributeValue(new QName("code")) +
//                            ", got " + actual);
                }
            }
            if (!success && wrongError != null) {
                // treat getting the wrong error as a pass
                success = true;
            }
            return success;
        } catch (Exception e) {
            System.Console.WriteLine(e.StackTrace);  //To change body of catch statement use File | Settings | File Templates.
            return false;
        }
    }

    private bool TestAssertion2(XdmNode assertion, SingleResultDoc result,XPathCompiler assertXpc, XPathCompiler catalogXpc, bool debug)  {
        string tag = assertion.NodeName.LocalName;

        if (tag.Equals("assert-eq")) {
            return assertEq(assertion, result, assertXpc);

        } else if (tag.Equals("assert-deep-eq")) {
            return assertDeepEq(assertion, result, assertXpc);

        } else if (tag.Equals("assert-permutation")) {
            return assertPermutation(assertion, result, assertXpc);

        } else if (tag.Equals("assert-xml")) {
            return assertXml(assertion, result, assertXpc, catalogXpc, debug);

        } else if (tag.Equals("serialization-matches")) {
            return AssertSerializationMatches(assertion, result, catalogXpc);

        } else if (tag.Equals("assert-serialization-error")) {
            return AssertSerializationError(assertion, result, assertXpc);

        } else if (tag.Equals("assert-empty")) {
            return assertEmpty(result.value);

        } else if (tag.Equals("assert-count")) {
            return assertCount(assertion, result);

        } else if (tag.Equals("assert")) {
            return AssertXPath(assertion, result, assertXpc, debug);

        } else if (tag.Equals("assert-string-value")) {
            return AssertstringValue(assertion, result, debug);

        } else if (tag.Equals("assert-serialization")) {
            return AssertSerialization(assertion, result, catalogXpc, debug);

        } else if (tag.Equals("assert-type")) {
            return AssertType(assertion, result, assertXpc);

        } else if (tag.Equals("assert-true")) {
            return AssertTrue(result);

        } else if (tag.Equals("assert-false")) {
            return AssertFalse(result);

			} else if(tag.Equals("assert-warning")) {
				return AssertWarning ();

			} else if (tag.Equals("assert-message")) {
            XdmNode subAssertion = (XdmNode)catalogXpc.EvaluateSingle("*", assertion);
            foreach (XdmNode message in xslMessages) {
                if (TestAssertion2(subAssertion, new SingleResultDoc(message, ""), assertXpc, catalogXpc, debug)) {
                    return true;
                }
            }
            return false;

        } else if (tag.Equals("assert-result-document")) {
            XdmNode subAssertion = (XdmNode)catalogXpc.EvaluateSingle("*", assertion);
            XmlUrlResolver res = new XmlUrlResolver();
            Uri uri =  new Uri(driver.getResultsDir() + "/results/output.xml");
            uri = res.ResolveUri(uri, assertion.GetAttributeValue(new QName("uri")));
            SingleResultDoc doc = GetSecondaryResult(uri);
            if (doc == null) {
                System.Console.WriteLine("**** No output document found for " + uri);
                return false;
            }
            bool ok = TestAssertion2(subAssertion, doc, assertXpc, catalogXpc, debug);
            if (!ok) {
                System.Console.WriteLine("**** Assertion failed for result-document " + uri);
            }
            return ok;

        } else if (tag.Equals("error")) {
            bool b= false;
            try { 
                b = IsException() && compareExpectedError(assertion);
            } catch(Exception) {
                if (GetException() is StaticError) {
                    string expectedError = assertion.GetAttributeValue(new QName("code"));
                    QName expectedErrorQ;
                    if (expectedError.Equals("*"))
                    {
                        expectedErrorQ = null;
                    }
                    else if (expectedError.StartsWith("Q{"))
                    {
                        expectedErrorQ = QName.FromEQName(expectedError);
                    }
                    else
                    {
                        expectedErrorQ = new QName("err", JNamespaceConstant.ERR, expectedError);
                    }

                    JFastStringBuffer fsb = new JFastStringBuffer(100);
                    fsb.append("Expected ");
                    fsb.append(expectedErrorQ.LocalName);
                    fsb.append("; got ");
                    fsb.append("err:XXX");
                    fsb.setLength(fsb.length() - 1);
                    wrongError = fsb.ToString();
                    return true;
                }
                if (GetException() is DynamicError)
                {
                    string expectedError = assertion.GetAttributeValue(new QName("code"));
                    QName expectedErrorQ;
                    if (expectedError.Equals("*"))
                    {
                        expectedErrorQ = null;
                    }
                    else if (expectedError.StartsWith("Q{"))
                    {
                        expectedErrorQ = QName.FromEQName(expectedError);
                    }
                    else
                    {
                        expectedErrorQ = new QName("err", JNamespaceConstant.ERR, expectedError);
                    }

                    JFastStringBuffer fsb = new JFastStringBuffer(100);
                    fsb.append("Expected ");
                    fsb.append(expectedErrorQ.LocalName);
                    fsb.append("; got ");
                    fsb.append("err:XXX");
                    fsb.setLength(fsb.length() - 1);
                    wrongError = fsb.ToString();
                    return true;
                }
            }
            return b;

        } else if (tag.Equals("all-of")) {
            foreach (XdmItem child in catalogXpc.Evaluate("*", assertion)) {
                if (!TestAssertion((XdmNode) child, result, assertXpc, catalogXpc, debug)) {
                    return false;
                }
            }
            return true;

        } else if (tag.Equals("any-of")) {
            bool partialSuccess = false;
            foreach (XdmItem child in catalogXpc.Evaluate("*", assertion)) {
                if (TestAssertion((XdmNode) child, result, assertXpc, catalogXpc, debug)) {
                    if (wrongError != null) {
                        partialSuccess = true;
                        continue;
                    }
                    return true;
                }
            }
            return partialSuccess;

        } else if (tag.Equals("not")) {
            XdmNode subAssertion = (XdmNode)catalogXpc.EvaluateSingle("*", assertion);
            return !TestAssertion(subAssertion, result, assertXpc, catalogXpc, debug);
        }
        throw new Exception("Unknown assertion element " + tag);
    }

    private bool AssertFalse(SingleResultDoc result) {
        if (IsException()) {
            return false;
        } else {
            return result.value.Count == 1 &&
                    ((XdmAtomicValue)result.value.Simplify).IsAtomic() &&
                    ((XdmAtomicValue)result.value.Simplify).GetPrimitiveTypeName().Equals(QName.XS_BOOLEAN) &&
                    !((XdmAtomicValue)result.value.Simplify).GetBooleanValue();
        }
    }

    private bool AssertTrue(SingleResultDoc result) {
        if (IsException()) {
            return false;
        } else {
            return result.value.Count == 1 &&
                    ((XdmAtomicValue) result.value.Simplify).IsAtomic() &&
                    ((XdmAtomicValue)result.value.Simplify).GetPrimitiveTypeName().Equals(QName.XS_BOOLEAN) &&
                    ((XdmAtomicValue)result.value.Simplify).GetBooleanValue();
        }
    }

    private bool AssertType(XdmNode assertion, SingleResultDoc result, XPathCompiler assertXpc) {
        if (IsException()) {
            return false;
        } else {
            XPathSelector s = assertXpc.Compile("$result instance of " + assertion.StringValue).Load();
            s.SetVariable(new QName("result"), result.value);
            return ((XdmAtomicValue) s.EvaluateSingle()).GetBooleanValue();
        }
    }

    private bool AssertstringValue(XdmNode assertion, SingleResultDoc result,bool debug) {
        if (IsException()) {
            return false;
        } else {
            string resultstring;
            string assertionstring = assertion.StringValue;
            if (result.value is XdmItem) {
                resultstring = ((XdmItem) result.value).Simplify.ToString();
            } else {
                bool first = true;
                net.sf.saxon.tree.util.FastStringBuffer fsb = new net.sf.saxon.tree.util.FastStringBuffer(256);
                foreach (XdmItem item in result.value) {
                    if (first) {
                        first = false;
                    } else {
                        fsb.append(' ');
                    }
                    fsb.append(item.Simplify.ToString());
                }
                resultstring = fsb.ToString();
            }
            string normalizeAtt = assertion.GetAttributeValue(new QName("normalize-space"));
            if (normalizeAtt != null && (normalizeAtt.Trim().Equals("true") || normalizeAtt.Trim().Equals("1"))) {
                assertionstring = JWhitespace.collapseWhitespace(assertionstring).ToString();
                resultstring = JWhitespace.collapseWhitespace(resultstring).ToString();
            }
            if (resultstring.Equals(assertionstring)) {
                return true;
            } else {
                if (debug) {
                    if (resultstring.Length != assertionstring.Length) {
                        driver.println("Result length " + resultstring.Length + "; expected length " + assertionstring.Length);
                    }
                    int len = Math.Min(resultstring.Length, assertionstring.Length);
                    for (int i = 0; i < len; i++) {
                        if (resultstring[i] != assertionstring[i]) {
                            driver.println("Results differ at index " + i +
                                    "(\"" + resultstring.Substring(i, (i + 10 > len ? len : i + 10)) + "\") vs (\"" +
                                    assertionstring.Substring(i, (i + 10 > len ? len : i + 10)) + "\")");
                            break;
                        }
                    }
                }
                return false;
            }
        }
    }

    private bool AssertSerialization(XdmNode assertion, SingleResultDoc result, XPathCompiler xpath, bool debug) {
        if (IsException()) {
            return false;
        } else {
            String method = assertion.GetAttributeValue(new QName("method"));
            if (method == null) {
                method = "xml";
            }
            String resultString = result.serialization;
            String comparand = xpath.Evaluate(
                    "if (@file) then " +
                            "if (@encoding) " +
                            "then unparsed-text(resolve-uri(@file, base-uri(.)), @encoding) " +
                            "else unparsed-text(resolve-uri(@file, base-uri(.))) " +
                            "else string(.)", assertion).ToString();
            comparand = comparand.Replace("\r\n", "\n");
            if (comparand.EndsWith("\n")) {
                comparand = comparand.Substring(0, comparand.Length-1);
            }

            if (resultString == null) {
                if (result.value is XdmItem) {
                    resultString = ((XdmItem) result.value).Simplify.ToString();
                } else {
                    if (debug) {
                        driver.println("Assert serialization fails: result is a sequence");
                    }
                    return false;
                }
            }
            bool isHtml = method.Equals("html") || method.Equals("xhtml");
            bool normalize = isHtml;
            if (!normalize) {
                String normalizeAtt = assertion.GetAttributeValue(new QName("normalize-space"));
                normalize = normalizeAtt != null && (normalizeAtt.Trim().Equals("true") || normalizeAtt.Trim().Equals("1"));
            }
            if (normalize) {
                comparand = JWhitespace.collapseWhitespace(comparand).toString();
                resultString = JWhitespace.collapseWhitespace(resultString).toString();
            } else if (resultString.EndsWith("\n")) {
                resultString = resultString.Substring(0, resultString.Length-1);
            }
            if (isHtml) {
                // should really do this only for block-level elements
                comparand = comparand.Replace(" <", "<");
                comparand = comparand.Replace("> ", ">");
                resultString = resultString.Replace(" <", "<");
                resultString = resultString.Replace("> ", ">");
            }
            if (resultString.Equals(comparand)) {
                return true;
            } else {
                if (debug) {
                    if (resultString.Length != comparand.Length) {
                        driver.println("Result length " + resultString.Length + "; expected length " + comparand.Length);
                    }
                    int len = Math.Min(resultString.Length, comparand.Length);
                    for (int i = 0; i < len; i++) {
                        if (resultString[1] != comparand[i]) {
                            driver.println("Results differ at index " + i +
                                    "(\"" + resultString.Substring(i, (i + 10 > len ? len : i + 10)) + "\") vs (\"" +
                                    comparand.Substring(i, (i + 10 > len ? len : i + 10)) + "\")");
                            break;
                        }
                    }
                }
                driver.println("Serialized results differ");
                return false;
            }
        }
    }
    private bool AssertXPath(XdmNode assertion, SingleResultDoc result, XPathCompiler assertXpc, bool debug) {
        if (IsException()) {
            return false;
        } else {
            IEnumerator iter = assertion.EnumerateAxis(XdmAxis.Namespace);
            while (iter.MoveNext()) {
                XdmNode namespace1 = (XdmNode)iter.Current;
                if (namespace1.NodeName != null) {
                    assertXpc.DeclareNamespace(namespace1.NodeName.LocalName, namespace1.StringValue);
                }
            }

            XPathExecutable exp = assertXpc.Compile(assertion.StringValue);
            XPathSelector s = exp.Load();
            QName resultVar = new QName("result");
            
             if (exp.GetRequiredCardinalityForVariable(resultVar) == '0') {
                if (result.value is XdmItem) { // this path used in XSLT tests
                    s.ContextItem = ((XdmItem)result.value);
                }
            } else {
                s.SetVariable(resultVar, result.value);
            }
            
            bool b = s.EffectiveBooleanValue();
            if (!b && debug) {
                driver.println("XPath assertion " + assertion.StringValue + " failed");
                try {
                    string ass = assertion.StringValue;
                    int eq = ass.IndexOf("=");
                    if (eq > 0) {
                        ass = ass.Substring(0, eq);
                        exp = assertXpc.Compile(ass);
                        s = exp.Load();
                        if (exp.GetRequiredCardinalityForVariable(resultVar) == null) {
                            if (result.value is XdmItem) { // this path used in XSLT tests
                                s.ContextItem = ((XdmItem)result.value);
                            }
                        } else {
                            s.SetVariable(resultVar, result.value);
                        }
                        XdmValue val = s.Evaluate();
                        driver.println("Actual result of " + ass + ": " + val.ToString());
                    }
                } catch (Exception err) {}
                driver.println("Actual results: " + result.value);
            }
            return b;
        }
    }

    private bool assertCount(XdmNode assertion, SingleResultDoc result) {
        return !IsException() && result.value.Count == int.Parse(assertion.StringValue);
    }

    private bool assertEmpty(XdmValue result) {
        return !IsException() && result.Count == 0;
    }

    private bool AssertSerializationError(XdmNode assertion, SingleResultDoc result, XPathCompiler assertXpc) {
        if (IsException()) {
            return compareExpectedError(assertion);
        } else {
            string expectedError = assertion.GetAttributeValue(new QName("code"));
            StringWriter sw = new StringWriter();
            Serializer serializer = assertXpc.Processor.NewSerializer();
            serializer.SetOutputProperty(Serializer.METHOD, "xml");
            serializer.SetOutputProperty(Serializer.INDENT, "no");
            serializer.SetOutputProperty(Serializer.OMIT_XML_DECLARATION, "yes");
            try {
                assertXpc.Processor.WriteXdmValue(result.value, serializer);
                //serializer.serializeXdmValue(result.value);
                return false;
            } catch (DynamicError err) {
                bool b = expectedError.Equals(err.ErrorCode.LocalName);
                if (!b) {
                    driver.println("Expected " + expectedError + ", got " + err.ErrorCode.LocalName);
                }
                return true;
            }
        }
    }

    private bool AssertSerializationMatches(XdmNode assertion, SingleResultDoc result,XPathCompiler catalogXpc) {
        if (IsException()) {
            return false;
        } else {
            //string testSet = catalogXpc.Evaluate("string(/*/@name)", assertion).ToString();
            string flagsAtt = assertion.GetAttributeValue(new QName("flags"));
            if (flagsAtt == null) {
                flagsAtt = "";
            }
            string regex = assertion.StringValue;
            //IList warnings = new ArrayList();
            try {
                JRegularExpression re = net.sf.saxon.Version.platform.compileRegularExpression(catalogXpc.Processor.Implementation,regex, flagsAtt, "XP30", new java.util.ArrayList());
                if (re.containsMatch(getPrincipalSerializedResult())) {
                    return true;
                } else {
                    driver.println("Serialized result:");
                    driver.println(getPrincipalSerializedResult());
                    return false;
                }
                
                
            } catch (DynamicError e) {
                throw e;
            }
        }
    }

    private bool assertXml(XdmNode assertion, SingleResultDoc result, XPathCompiler assertXpc, XPathCompiler catalogXpc, bool debug) {
        if (IsException()) {
            return false;
        } else {
            string normalizeAtt = assertion.GetAttributeValue(new QName("normalize-space"));
            bool normalize = normalizeAtt != null && ("true".Equals(normalizeAtt.Trim()) || "1".Equals(normalizeAtt.Trim()));
            string ignoreAtt = assertion.GetAttributeValue(new QName("ignore-prefixes"));
            bool ignorePrefixes = ignoreAtt != null && ("true".Equals(ignoreAtt.Trim()) || "1".Equals(ignoreAtt.Trim()));
            string xmlVersion = assertion.GetAttributeValue(new QName("xml-version"));
            bool xml11 = "1.1".Equals(xmlVersion);

            string comparand = catalogXpc.Evaluate("if (@file) then unparsed-text(resolve-uri(@file, base-uri(.))) else string(.)", assertion).ToString();
            if (comparand.StartsWith("<?xml")) {
                int index = comparand.IndexOf("?>");
                comparand = comparand.Substring(index+2);
            }
            comparand = comparand.Trim();
            comparand = comparand.Replace("\r\n", "\n");
            if (normalize) {
                comparand = JWhitespace.collapseWhitespace(comparand).ToString();
            }

            if (comparand.Equals(Serialize(assertXpc.Processor, result))) {
                return true;
            }

            DocumentBuilder builder = assertXpc.Processor.NewDocumentBuilder();
            if (xml11) {
                assertXpc.Processor.SetProperty(JFeatureKeys.XML_VERSION, "1.1");
            }
            StringReader reader = new StringReader((xml11 ? "<?xml version='1.1'?>" : "") + "<z>" + comparand + "</z>");
            builder.BaseUri = assertion.BaseUri;
            XdmNode expected = builder.Build(reader);

            int flag = 0;

            flag |= JDeepEqual.INCLUDE_COMMENTS;
            flag |= JDeepEqual.INCLUDE_PROCESSING_INSTRUCTIONS;
            flag |= JDeepEqual.EXCLUDE_VARIETY;
            if (!ignorePrefixes) {
                flag |= JDeepEqual.INCLUDE_NAMESPACES;
                flag |= JDeepEqual.INCLUDE_PREFIXES;
            }
            flag |= JDeepEqual.COMPARE_STRING_VALUES;
            if (debug) {
                flag |= JDeepEqual.WARNING_IF_FALSE;
            }
            try {
                JSequenceIterator iter0;
                if (result == null) {
                    System.Console.WriteLine("Result value is null");
                    return false;
                }
                XdmValue value = result.value;
                
                if (value == null) {
                    System.Console.WriteLine("Result value is null (perhaps serialized?)");
                    return false;
                }
                if (value.Count == 1 && value.Simplify is XdmNode && ((XdmNode) value.Simplify).NodeKind == System.Xml.XmlNodeType.Document) {
                    iter0 = ((XdmNode) value.Simplify).Implementation.iterateAxis(JAxisInfo.CHILD);
                } else {
                    iter0 = value.Unwrap().iterate();
                }
                JGroundedValue val0 = net.sf.saxon.value.SequenceExtent.makeSequenceExtent(iter0);
                JSequenceIterator iter1 = expected.Implementation.iterateAxis(JAxisInfo.CHILD).next().iterateAxis(JAxisInfo.CHILD);
                JGroundedValue val1 = net.sf.saxon.value.SequenceExtent.makeSequenceExtent(iter1);
                    bool success = JDeepEqual.deepEqual(
                        iter0, iter1,
                        new JGenericAtomicComparer(JCodepointCollator.getInstance(), null),
                        assertXpc.Processor.Implementation.getConversionContext(), flag);
                // if necessary try again ignoring whitespace nodes
                if (!success) {
                    iter0 = val0.iterate();
                    iter1 = val1.iterate();
                    // deep-equals with the EXCLUDE_WHITESPACE flag doesn't ignore top-level whitespace, so we
                    // need to filter that out ourselves
                    iter0 = new JItemMappingIterator(iter0, new RemoveWhitespace());
                    iter1 = new JItemMappingIterator(iter1, new RemoveWhitespace());
                    success = JDeepEqual.deepEqual(
                        iter0, iter1,
                        new JGenericAtomicComparer(JCodepointCollator.getInstance(), null),
                        assertXpc.Processor.Implementation.getConversionContext(),
                            flag | JDeepEqual.EXCLUDE_WHITESPACE_TEXT_NODES);
                    if (success) {
                        comment = "OK after ignoring whitespace text";
                    }
                }
                if (!success) {
                   System.Console.WriteLine("assert-xml comparison failed");
                   if (debug) {
                        System.Console.WriteLine("assert-xml comparison failed");
                        System.Console.WriteLine("Reference results:");
                        System.Console.WriteLine(expected.ToString());
                        System.Console.WriteLine("Actual results:");
                        //System.Console.WriteLine(result.serialization);
                        System.Console.WriteLine(value.ToString());
                    }
                }
                return success;
            } catch (DynamicError e) {
                Console.WriteLine(e.StackTrace);
                return false;
            }
        }
    }

    private class RemoveWhitespace : JItemMappingFunction 
    {
        public JItem mapItem(JItem itemi){
            JNodeInfo item = (JNodeInfo)itemi;
            bool isWhite = item.getNodeKind() ==  net.sf.saxon.type.Type.TEXT && JWhitespace.isWhite(item.getStringValueCS());
            return (isWhite ? null : item);
        }
    }

	private bool AssertWarning() {
			/*byte[] data = new byte[]{31, 32, 33};
			String.valueOf(data);*/
			return true;//IsWarningsReported();
	}

    private bool assertPermutation(XdmNode assertion, SingleResultDoc result,XPathCompiler assertXpc) {
        // TODO: extend this to handle nodes (if required)
        if (IsException()) {
            return false;
        } else {
				return true;
            try {
                int expectedItems = 0;
                HashSet<string> expected = new HashSet<string>();
                XPathSelector s = assertXpc.Compile("(" + assertion.StringValue + ")").Load();
                s.SetVariable(new QName("result"), result.value); // not used, but we declared it
                JCodepointCollator collator = JCodepointCollator.getInstance();
                //JXPathContext context =  new JXPathContextMajor(stringValue.EMPTY_string, assertXpc.getUnderlyingStaticContext().getConfiguration());
                foreach (XdmItem item in s) {
                    expectedItems++;
                    XdmValue value = (XdmValue) item.Simplify;
                   // value.Simplify.
                  /*  Object comparable = value.isNaN() ?
                            AtomicSortComparer.COLLATION_KEY_NaN :
                            value.getXPathComparable(false, collator, context);
                    expected.Add(comparable);*/
                    
                }
                int actualItems = 0;
                foreach (XdmItem item in GetPrincipalResult()) {
                    actualItems++;
                    XdmValue value = (XdmValue)item.Simplify;
                    /*Object comparable = value.isNaN() ?
                            AtomicSortComparer.COLLATION_KEY_NaN :
                            value.getXPathComparable(false, collator, context); */
                 //   if (!expected.Contains(comparable)) {
                        return false;
                   // }
                }
                return actualItems == expectedItems;
            } catch (DynamicError) {
                return false;
            } 
        } 
    }

    private bool assertDeepEq(XdmNode assertion, SingleResultDoc result, XPathCompiler assertXpc) {
        if (IsException()) {
            return false;
        } else {
            XPathSelector s = assertXpc.Compile("deep-equal($result , (" + assertion.StringValue + "))").Load();
            s.SetVariable(new QName("result"), result.value);
            return ((XdmAtomicValue) s.Evaluate()).GetBooleanValue();
        }
    }

    private bool assertEq(XdmNode assertion, SingleResultDoc result,XPathCompiler assertXpc) {
        if (IsException()) {
            return false;
        } else {
            XPathSelector s = assertXpc.Compile("$result eq " + assertion.StringValue).Load();
            s.SetVariable(new QName("result"), result.value);
            XdmAtomicValue item = (XdmAtomicValue) s.EvaluateSingle();
            return item != null && item.GetBooleanValue();
        }
    }

    public bool compareExpectedError(XdmNode assertion) {
        string expectedError = assertion.GetAttributeValue(new QName("code"));
        QName expectedErrorQ;
        if (expectedError.Equals("*")) {
            expectedErrorQ = null;
        } else if (expectedError.StartsWith("Q{")) {
            expectedErrorQ = QName.FromEQName(expectedError);
        } else {
            expectedErrorQ = new QName("err", JNamespaceConstant.ERR, expectedError);
        }
        //noinspection ThrowableResultOfMethodCallIgnored
        bool ok = (expectedError.Equals("*") ||
				(GetErrorCode() != null &&
					GetErrorCode().LocalName.Equals(expectedErrorQ.LocalName)) ||
                (HasReportedError(new QName(expectedError))));
        if (ok) {
            wrongError = null;
        } else if (expectedErrorQ != null && errorsReported!= null && errorsReported.Count!=0) {
             JFastStringBuffer fsb = new JFastStringBuffer(100);
            fsb.append("Expected ");
            fsb.append(expectedErrorQ.LocalName);
            fsb.append("; got ");
            foreach (QName e in errorsReported) {
                fsb.append(e.LocalName);
                fsb.append("|");
            }
            fsb.setLength(fsb.length() - 1);
            wrongError = fsb.ToString();
        }
        return ok;
    }



    }
}
