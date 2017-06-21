using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using Saxon.Api;
using JXPathException = net.sf.saxon.trans.XPathException;

namespace TestRunner
{
   


	public class ErrorCollector : IMessageListener {

		private IList<QName> errorCodes = new List<QName>();

		TestOutcome outcome1;

		public ErrorCollector(TestOutcome out1)
			: base()
		{
			outcome1 = out1;
		}

		public void Message(XdmNode content, bool terminate, IXmlLocation location)
		{
			outcome1.AddXslMessage(content);
		}

    
    public void error(Exception exception) {
        addErrorCode(exception);
        //super.error(exception);
    }

    
    public void fatalError(Exception exception)  {
        addErrorCode(exception);
        //super.fatalError(exception);
    }

    /**
     * Make a clean copy of this ErrorListener. This is necessary because the
     * standard error listener is stateful (it remembers how many errors there have been)
     *
     * @param hostLanguage the host language (not used by this implementation)
     * @return a copy of this error listener
     */
    
    public ErrorCollector makeAnother(int hostLanguage) {
        return this;
    }

    private void addErrorCode(Exception exception) {
			if (exception is JXPathException) {
            String errorCode = ((JXPathException) exception).getErrorCodeLocalPart();
            if (errorCode != null) {
                String ns = ((JXPathException) exception).getErrorCodeNamespace();
					if (ns != null && !net.sf.saxon.lib.NamespaceConstant.ERR.Equals(ns)) {
                    errorCode = "Q{" + ns + "}" + errorCode;
                }
					errorCodes.Add(new QName(errorCode));
            } else {
					errorCodes.Add(new QName("ZZZZ9999"));
            }
        }
    }

	public IList<QName> getErrorCodes() {
        return errorCodes;
    }


}
}
