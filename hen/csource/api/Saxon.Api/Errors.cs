using System;
using System.IO;
using System.Xml;
using System.Collections;
using javax.xml.transform;
using javax.xml.transform.stream;
using XPathException = net.sf.saxon.trans.XPathException;
using JException = java.lang.Exception;
using JInvalidityReportGenerator = net.sf.saxon.lib.InvalidityReportGenerator;
using JValidationFailure = net.sf.saxon.type.ValidationFailure;
using net.sf.saxon.om;
using net.sf.saxon.lib;

namespace Saxon.Api
{

    /// <summary>
    /// The StaticError class contains information about a static error detected during
    /// compilation of a stylesheet, query, or XPath expression.
    /// </summary>

    [Serializable]
    public class StaticError : Exception
    {

        private XPathException exception;
        internal bool isWarning;

		// internal constructor: Create a new StaticError, wrapping a Saxon XPathException

        internal StaticError(JException err)
        {
            if (err is XPathException)
            {
                this.exception = (XPathException)err;
            }
            else
            {
                this.exception = XPathException.makeXPathException(err);
            }
        }

        /// <summary>
        /// The error code, as a QName. May be null if no error code has been assigned
        /// </summary>

        public QName ErrorCode
        {
            get
            {
				if (exception.getErrorCodeLocalPart () != null) {
					return new QName ("err",
						exception.getErrorCodeNamespace (),
						exception.getErrorCodeLocalPart ());
				} else {
					return null;
				}
            }
        }

        /// <summary>
        /// Return the message associated with this error
        /// </summary>

        public override String Message
        {
            get
            {
                return exception.getMessage();
            }
        }


        /// <summary>
        /// Return the message associated with this error concatenated with the message from the causing exception
        /// </summary> 
        public String InnerMessage
        {
            get {

                return exception.getMessage() +": " + exception.getCause().Message;
            }
        
        }


       

        /// <summary>
        /// The URI of the query or stylesheet module in which the error was detected
        /// (as a string)
        /// </summary>
        /// <remarks>
        /// May be null if the location of the error is unknown, or if the error is not
        /// localized to a specific module, or if the module in question has no known URI
        /// (for example, if it was supplied as an anonymous Stream)
        /// </remarks>

        public String ModuleUri
        {
            get
            {
                if (exception.getLocator() == null)
                {
                    return null;
                }
                return exception.getLocator().getSystemId();
            }
        }

        /// <summary>
        /// The line number locating the error within a query or stylesheet module
        /// </summary>
        /// <remarks>
        /// May be set to -1 if the location of the error is unknown
        /// </remarks>        

        public int LineNumber
        {
            get
            {
                SourceLocator loc = exception.getLocator();
                if (loc == null)
                {
                    if (exception.getException() is TransformerException)
                    {
                        loc = ((TransformerException)exception.getException()).getLocator();
                        if (loc != null)
                        {
                            return loc.getLineNumber();
                        }
                    }
                    return -1;
                }
                return loc.getLineNumber();
            }
        }


        /// <summary>
        /// The line number locating the error within a query or stylesheet module
        /// </summary>
        /// <remarks>
        /// May be set to -1 if the location of the error is unknown
        /// </remarks>        

        public int ColumnNumber
        {
            get
            {
                SourceLocator loc = exception.getLocator();
                if (loc == null)
                {
                    if (exception.getException() is TransformerException)
                    {
                        loc = ((TransformerException)exception.getException()).getLocator();
                        if (loc != null)
                        {
                            return loc.getColumnNumber();
                        }
                    }
                    return -1;
                }
                return loc.getColumnNumber();
            }
        }

        /// <summary>
        /// Indicate whether this error is being reported as a warning condition. If so, applications
        /// may ignore the condition, though the results may not be as intended.
        /// </summary>

        public bool IsWarning
        {
            get
            {
                return isWarning;
            }
            set
            {
                isWarning = value;
            }
        }

        /// <summary>
        /// Indicate whether this condition is a type error.
        /// </summary>

        public bool IsTypeError
        {
            get
            {
                return exception.isTypeError();
            }
        }

        /// <summary>
        /// Return the underlying exception. This is unstable as this is an internal object
        /// </summary>
        /// <returns>XPathException</returns>
        public XPathException UnderlyingException
        {
            get
            {
                return exception;
            }
        }

        /// <summary>
        /// Return the error message.
        /// </summary>

        public override String ToString()
        {
            return exception.getMessage();
        }
    }

    /// <summary>
    /// The DynamicError class contains information about a dynamic error detected during
    /// execution of a stylesheet, query, or XPath expression.
    /// </summary>

    [Serializable]
    public class DynamicError : Exception
    {

        private XPathException exception;
        internal bool isWarning;

        /// <summary>
        /// Create a new DynamicError, specifying the error message
        /// </summary>
        /// <param name="message">The error message</param>

        public DynamicError(String message)
        {
            exception = new XPathException(message);
        }

		// internal constructor: Create a new DynamicError, wrapping a Saxon XPathException

        internal DynamicError(TransformerException err)
        {
            if (err is XPathException)
            {
                this.exception = (XPathException)err;
            }
            else
            {
                this.exception = XPathException.makeXPathException(err);
            }
        }

        /// <summary>
        /// The error code, as a QName. May be null if no error code has been assigned
        /// </summary>

        public QName ErrorCode
        {
            get
            {
                return new QName("err",
                        exception.getErrorCodeNamespace(),
                        exception.getErrorCodeLocalPart());
            }
        }

        /// <summary>
        /// Return the message associated with this error
        /// </summary>

        public override String Message
        {
            get
            {
                return exception.getMessage();
            }
        }

        /// <summary>
        /// The URI of the query or stylesheet module in which the error was detected
        /// (as a string)
        /// </summary>
        /// <remarks>
        /// May be null if the location of the error is unknown, or if the error is not
        /// localized to a specific module, or if the module in question has no known URI
        /// (for example, if it was supplied as an anonymous Stream)
        /// </remarks>

        public String ModuleUri
        {
            get
            {
                if (exception.getLocator() == null)
                {
                    return null;
                }
                return exception.getLocator().getSystemId();
            }
        }

        /// <summary>
        /// The line number locating the error within a query or stylesheet module
        /// </summary>
        /// <remarks>
        /// May be set to -1 if the location of the error is unknown
        /// </remarks>        

        public int LineNumber
        {
            get
            {
                SourceLocator loc = exception.getLocator();
                if (loc == null)
                {
                    if (exception.getException() is TransformerException)
                    {
                        loc = ((TransformerException)exception.getException()).getLocator();
                        if (loc != null)
                        {
                            return loc.getLineNumber();
                        }
                    }
                    return -1;
                }
                return loc.getLineNumber();
            }
        }

        /// <summary>
        /// Indicate whether this error is being reported as a warning condition. If so, applications
        /// may ignore the condition, though the results may not be as intended.
        /// </summary>

        public bool IsWarning
        {
            get
            {
                return isWarning;
            }
            set
            {
                isWarning = value;
            }
        }

        /// <summary>
        /// Indicate whether this condition is a type error.
        /// </summary>

        public bool IsTypeError
        {
            get
            {
                return exception.isTypeError();
            }
        }

        /// <summary>
        /// Return the error message.
        /// </summary>

        public override String ToString()
        {
            return exception.getMessage();
        }

		/// <summary>
		/// Return the underlying exception. This is unstable as this is an internal object
		/// </summary>
		/// <returns>XPathException</returns>
		public XPathException UnderlyingException
		{
			get
			{
				return exception;
			}
		}



	}

	/// <summary>
	/// Error gatherer. This class is used to provide customized error handling. </summary>
	/// <remarks><para>If an application does <em>not</em> register its own custom
	/// <code>ErrorListener</code>, the default <code>ErrorGatherer</code>
	/// is used which keeps track of all warnings and errors in a list.
	/// and does not throw any <code>Exception</code>s.
	/// Applications are <em>strongly</em> encouraged to register and use
	/// <code>ErrorListener</code>s that insure proper behavior for warnings and
	/// errors.</para>
	/// </remarks>
    [Serializable]
    internal class ErrorGatherer : javax.xml.transform.ErrorListener
    {

        private IList errorList;


		/// <summary>
		/// Initializes a new instance of the <see cref="Saxon.Api.ErrorGatherer"/> class.
		/// </summary>
		/// <param name="errorList">Error list.</param>
        public ErrorGatherer(IList errorList)
        {
            this.errorList = errorList;
        }

		/// <summary>
		/// Warning the specified exception.
		/// </summary>
		/// <param name="exception">TransformerException.</param>
        public void warning(TransformerException exception)
        {
            StaticError se = new StaticError(exception);
            se.isWarning = true;
            //Console.WriteLine("(Adding warning " + exception.getMessage() + ")");
            errorList.Add(se);
        }

		/// <summary>
		/// Report a Transformer exception thrown.
		/// </summary>
		/// <param name="error">Error.</param>
        public void error(TransformerException error)
        {
            StaticError se = new StaticError(error);
            se.isWarning = false;
            //Console.WriteLine("(Adding error " + error.getMessage() + ")");
            errorList.Add(se);
        }

		/// <summary>
		/// Report a fatal exception thrown.
		/// </summary>
		/// <param name="error">TransformerException.</param>
        public void fatalError(TransformerException error)
        {
            StaticError se = new StaticError(error);
            se.isWarning = false;
            errorList.Add(se);
            //Console.WriteLine("(Adding fatal error " + error.getMessage() + ")");
        }


		/// <summary>
		/// Gets the error list.
		/// </summary>
		/// <returns>Returns the error list</returns>
		public IList ErrorList {
			get { return errorList;}
		}
    }


	/// <summary>
	/// Interface for reporting validation errors found during validation of an instance document
	/// against a schema.
	/// </summary>
	public interface IInvalidityHandler {

        /// <summary>
        /// At the start of a validation episode, initialize the handler
        /// </summary>
        /// <param name="systemId">systemId optional; may be used to represent the destination of any
        /// report produced</param>
		/**public**/ void startReporting(String systemId);


		/// <summary>
		/// Report a validation error found during validation of an instance document
		/// against a schema
		/// </summary>
		/// <param name="failure">failure details of the validation error</param>
		/**public**/ void reportInvalidity (StaticError i);
		

		/// <summary>
		/// At the end of a validation episode, do any closedown actions, and optionally return
		/// information collected in the course of validation (for example a list of error messages).
		/// </summary>
		/// <returns>a value to be associated with a validation exception. May be the empty sequence.
		/// In the case of the InvalidityReportGenerator, this returns the XML document
		/// containing the validation report. This will be the value returned as the value of
	    /// the variable $err:value during try/catch processing</returns>
		/**public**/ XdmValue endReporting(); 
		



	}


    /// <summary>
    /// This class InvalidityHandlerWrapper extends the standard error handler for errors found during
    /// validation of an instance document against a schema, used if user specifies -report option on validate.
    /// Its effect is to output the validation errors found into the filename specified in an XML format.
    /// This is a wrapper class to wrap a .NET InvalidatityHandler class for interfacing within Java.
    /// </summary>
    public class InvalidityHandlerWrapper : net.sf.saxon.lib.InvalidityHandler
    {

        private IInvalidityHandler inHandler;

        /// <summary>
        /// reate a Standard Invalidity Handler
        /// </summary>
        /// <param name="inHandler">The .NEt IInvalidtityHandler</param>
        public InvalidityHandlerWrapper(IInvalidityHandler inHandler) {
            this.inHandler = inHandler;
        }


        /// <summary>
        /// Get the value to be associated with a validation exception. May return null.
        /// In the case of the InvalidityGenerator, this returns the XML document
        /// containing the validation report
        /// </summary>
        /// <returns>a value (or null). This will be the value returned as the value of the variable
        /// $err:value during try/catch processor</returns>
        public Sequence endReporting()
        {
            return inHandler.endReporting().Unwrap();
        }

        /// <summary>
        /// Receive notification of a validity error.
        /// </summary>
        /// <param name="i">Information about the nature of the invalidity</param>
        public void reportInvalidity(Invalidity i)
        {
            if (i is JValidationFailure) { 
                StaticError error = new StaticError(((JValidationFailure)i).makeException());
                inHandler.reportInvalidity(error);
            }

            
        }


        /// <summary>
        /// At the start of a validation episode, initialize the handler
        /// </summary>
        /// <param name="systemId">Is optional; may be used to represent the destination of any report produced</param>
        public void startReporting(string systemId)
        {
            inHandler.startReporting(systemId);
        }

        
	}






	/// <summary>
	/// <para>If an application does <em>not</em> register its own custom
	/// <code>ErrorListener</code>, the default <code>ErrorGatherer</code>
	/// is used which keeps track of all warnings and errors in a list.
	/// and does not throw any <code>Exception</code>s.
	/// Applications are <em>strongly</em> encouraged to register and use
	/// <code>ErrorListener</code>s that insure proper behavior for warnings and
	/// errors.</para>
	/// </summary>
	[Serializable]
	internal class InvalidityGatherer : net.sf.saxon.lib.InvalidityHandler
	{


		private IList errorList;



		/// <summary>
		/// Initializes a new instance of the <see cref="Saxon.Api.ErrorGatherer"/> class.
		/// </summary>
		/// <param name="invalidityHandler">Invalidity handler.</param>

		public InvalidityGatherer(IList errorList)
		{
            this.errorList = errorList;
		}

		public void startReporting(String systemId) {
			//invalidityHandler.startReporting (systemId);
		}

		public net.sf.saxon.om.Sequence endReporting() {
            //return invalidityHandler.endReporting ();
            return null;
		}

        /// <summary>
        /// List of errors. The caller may supply an empty list before calling Compile;
        /// the processor will then populate the list with error information obtained during
        /// the schema compilation. Each error will be included as an object of type StaticError.
        /// If no error list is supplied by the caller, error information will be written to
        /// the standard error stream.
        /// </summary>
        /// <remarks>
        /// <para>By supplying a custom List with a user-written add() method, it is possible to
        /// intercept error conditions as they occur.</para>
        /// <para>Note that this error list is used only for errors detected while 
        /// using the schema to validate a source document. It is not used to report errors
        /// in the schema itself.</para>
        /// </remarks>

        public IList ErrorList
        {
            set
            {
                errorList = value;
            }
            get
            {
                return errorList;
            }
        }



        /// <summary>
        /// 
        /// </summary>
        /// <param name="failure">net.sf.saxon.type.ValidationFailure.</param>
        public void reportInvalidity(net.sf.saxon.lib.Invalidity failure)
		{
			StaticError se = new StaticError(((net.sf.saxon.type.ValidationFailure)failure).makeException());
            errorList.Add(se);

			//invalidityHandler.reportInvalidity (se);
		}


	}




}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2016 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////