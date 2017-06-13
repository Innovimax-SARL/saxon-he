using System;
using System.IO;
using System.Xml;
using System.Collections;
using System.Globalization;
using JConfiguration = net.sf.saxon.Configuration;
using JController = net.sf.saxon.Controller;
using JItem = net.sf.saxon.om.Item;
using JGroundedValue = net.sf.saxon.om.GroundedValue;
using JNodeInfo = net.sf.saxon.om.NodeInfo;
using JSequence = net.sf.saxon.om.Sequence;
using JSequenceExtent = net.sf.saxon.value.SequenceExtent;
using JSequenceType = net.sf.saxon.value.SequenceType;
using JStaticQueryContext = net.sf.saxon.query.StaticQueryContext;
using JDynamicQueryContext = net.sf.saxon.query.DynamicQueryContext;
using JXQueryExpression = net.sf.saxon.query.XQueryExpression;
using JDotNetStandardModuleURIResolver = net.sf.saxon.dotnet.DotNetStandardModuleURIResolver;
using JDotNetComparator = net.sf.saxon.dotnet.DotNetComparator;
using JDotNetInputStream = net.sf.saxon.dotnet.DotNetInputStream;
using JDotNetURIResolver = net.sf.saxon.dotnet.DotNetURIResolver;
using JDotNetReader = net.sf.saxon.dotnet.DotNetReader;
using JValidation = net.sf.saxon.lib.Validation;
using JXPathException = net.sf.saxon.trans.XPathException;
using JStreamSource = javax.xml.transform.stream.StreamSource;
using JUserFunction = net.sf.saxon.expr.instruct.UserFunction;
using JBoolean = java.lang.Boolean;
using JDecimalValue = net.sf.saxon.value.DecimalValue;


namespace Saxon.Api
{

    /// <summary>
    /// An XQueryCompiler object allows XQuery queries to be compiled.
    /// </summary>
    /// <remarks>
    /// <para>To construct an <c>XQueryCompiler</c>, use the factory method
    /// <c>newXQueryCompiler</c> on the <c>Processor</c> object.</para>
    /// <para>The <c>XQueryCompiler</c> holds information that represents the static context
    /// for the queries that it compiles. This information remains intact after performing
    /// a compilation. An <c>XQueryCompiler</c> may therefore be used repeatedly to compile multiple
    /// queries. Any changes made to the <c>XQueryCompiler</c> (that is, to the
    /// static context) do not affect queries that have already been compiled.
    /// An <c>XQueryCompiler</c> may be used concurrently in multiple threads, but
    /// it should not then be modified once initialized.</para>
    /// 
    /// </remarks>

    [Serializable]
    public class XQueryCompiler
    {

        private JConfiguration config;
        private Processor processor;
        private JStaticQueryContext env;
        private IQueryResolver moduleResolver;
        private IList errorList;

        // internal constructor: the public interface is a factory method
        // on the Processor object

        internal XQueryCompiler(Processor processor)
        {
            this.processor = processor;
			this.config = processor.Implementation;
            this.env = config.newStaticQueryContext();
            env.setModuleURIResolver(new JDotNetStandardModuleURIResolver(processor.XmlResolver));
        }

        /// <summary>
        /// Create a collation based on a given <c>CompareInfo</c> and <c>CompareOptions</c>    
        /// </summary>
        /// <param name="uri">The collation URI to be used within the XPath expression to refer to this collation</param>
        /// <param name="compareInfo">The <c>CompareInfo</c>, which determines the language-specific
        /// collation rules to be used</param>
        /// <param name="options">Options to be used in performing comparisons, for example
        /// whether they are to be case-blind and/or accent-blind</param>
        /// <param name="isDefault">If true, this collation will be used as the default collation</param>

        public void DeclareCollation(Uri uri, CompareInfo compareInfo, CompareOptions options, Boolean isDefault) {
			JDotNetComparator comparator = new JDotNetComparator(uri.ToString(), compareInfo, options);
			config.registerCollation(uri.ToString(), comparator);
            if(isDefault) {
                env.declareDefaultCollation(uri.ToString());
            }
        }

        /// <summary>
        /// Declare a namespace for use by the query. This has the same
        /// status as a namespace appearing within the query prolog (though
        /// a declaration in the query prolog of the same prefix will take
        /// precedence)
        /// </summary>
        /// <param name="prefix">The namespace prefix to be declared. Use
        /// a zero-length string to declare the default namespace (that is, the
        /// default namespace for elements and types).</param>
        /// <param name="uri">The namespace URI. It is possible to specify
        /// a zero-length string to "undeclare" a namespace.</param>

        public void DeclareNamespace(String prefix, String uri)
        {
            env.declareNamespace(prefix, uri);
        }


        /// <summary>
        /// Get the Processor from which this XQueryCompiler was constructed
        /// </summary>
        public Processor Processor
        {
            get {return processor;}
            set {processor = value;}
        }

        /// <summary>
        /// The required context item type for the expression. This is used for
        /// optimizing the expression at compile time, and to check at run-time
        /// that the value supplied for the context item is the correct type.
        /// </summary>

        public XdmItemType ContextItemType
        {
            get { return XdmItemType.MakeXdmItemType(env.getRequiredContextItemType()); }
            set { env.setRequiredContextItemType(value.Unwrap()); }
        }

        /// <summary>
        /// The base URI of the query, which forms part of the static context
        /// of the query. This is used for resolving any relative URIs appearing
        /// within the query, for example in references to library modules, schema
        /// locations, or as an argument to the <c>doc()</c> function.
        /// </summary>


        public String BaseUri
        {
            get { return env.getBaseURI(); }
            set { env.setBaseURI(value); }
        }


        /// <summary>
        /// Say that the query must be compiled to be schema-aware, even if it contains no
        /// "import schema" declarations. Normally a query is treated as schema-aware
        /// only if it contains one or more "import schema" declarations. If it is not schema-aware,
        /// then all input documents must be untyped (or xs:anyType), and validation of temporary nodes is disallowed
        /// (though validation of the final result tree is permitted). Setting the argument to true
        /// means that schema-aware code will be compiled regardless.
        /// schemaAware If true, the stylesheet will be compiled with schema-awareness
        /// enabled even if it contains no xsl:import-schema declarations. If false, the stylesheet
        /// is treated as schema-aware only if it contains one or more xsl:import-schema declarations.
        /// </summary>

        public Boolean SchemaAware
        {
            get
            {
                return env.isSchemaAware();
            }
            set
            {
                env.setSchemaAware(value);
            }
        }

        /// <summary>
        /// This property indicates whether XQuery Update syntax is accepted. The default
        /// value is false. This property must be set to true before compiling a query that
        /// uses update syntax.
        /// </summary>
        /// <remarks>
        /// <para>This propery must be set to true before any query can be compiled
        /// that uses updating syntax. This applies even if the query is not actually an updating
        /// query (for example, a copy-modify expression). XQuery Update syntax is accepted
        /// only by Saxon-SA. Non-updating queries are accepted regardless of the value of this
        /// property.</para>
        /// <para><i>Property added in Saxon 9.1</i></para></remarks>


        public bool UpdatingEnabled
        {
            get { return env.isUpdatingEnabled(); }
            set { env.setUpdatingEnabled(value); }
        }

        /// <summary>
        /// This property indicates which version of XQuery language syntax is accepted. The default
        /// value is "1.0". This property must be set to "3.0" before compiling a query that
        /// uses XQuery 3.0 (formerly known as XQuery 1.1) syntax.
        /// </summary>
        /// <remarks>
        /// <para>Support for XQuery 3.0 is currently limited: for details see the Saxon documentation.
        /// It cannot be used together with
        /// XQuery Updates. As well as enabling XQuery 3.0 via this API call, it must also be enabled
        /// by setting version="3.0" in the query prolog.</para>
        /// <para><i>Property added in Saxon 9.2</i></para></remarks>


        public string XQueryLanguageVersion
        {
			get { 
				if (env.getLanguageVersion() == 10) {
						return "1.0";
					}
                else if (env.getLanguageVersion() == 11)
                {
                    return "3.0";
                }
                else if (env.getLanguageVersion() == 30) {
						return "3.0";
					}
				else if (env.getLanguageVersion() == 31) {
						return "3.1";
					}
					else throw new StaticError(new net.sf.saxon.trans.XPathException("Unknown XQuery version " + env.getLanguageVersion()));
			}
            set { 
				int level = 10;
				if (value.ToString() == "1.0") {
					level = 10;
				}
                else if (value.ToString() == "1.1")
                {
                    level = 30;
                }
                else if(value.ToString() == "3.0") {
					level = 30;
				}
				else if (value.ToString() == "3.1") {
					level = 31;
				}
				env.setLanguageVersion(level);
			}
        }


        /// <summary>
        /// A user-supplied <c>IQueryResolver</c> used to resolve location hints appearing in an
        /// <c>import module</c> declaration.
        /// </summary>
        /// <remarks>
        /// <para>In the absence of a user-supplied <c>QueryResolver</c>, an <c>import module</c> declaration
        /// is interpreted as follows. First, if the module URI identifies an already loaded module, that module
        /// is used and the location hints are ignored. Otherwise, each URI listed in the location hints is
        /// resolved using the <c>XmlResolver</c> registered with the <c>Processor</c>.</para>
        /// </remarks>

        public IQueryResolver QueryResolver
        {
            get { return moduleResolver; }
            set
            {
                moduleResolver = value;
                env.setModuleURIResolver(new DotNetModuleURIResolver(value));
            }
        }

        /// <summary>
        /// List of errors. The caller should supply an empty list before calling Compile;
        /// the processor will then populate the list with error information obtained during
        /// the compilation. Each error will be included as an object of type StaticError.
        /// If no error list is supplied by the caller, error information will be written to
        /// the standard error stream.
        /// </summary>
        /// <remarks>
        /// By supplying a custom List with a user-written add() method, it is possible to
        /// intercept error conditions as they occur.
        /// </remarks>

        public IList ErrorList
        {
            set
            {
                errorList = value;
                env.setErrorListener(new ErrorGatherer(value));
            }
            get
            {
                return errorList;
            }
        }

        /// <summary>
        /// Compile a query supplied as a Stream.
        /// </summary>
        /// <remarks>
        /// <para>The XQuery processor attempts to deduce the encoding of the query
        /// by looking for a byte-order-mark, or if none is present, by looking
        /// for the encoding declaration in the XQuery version declaration.
        /// For this to work, the stream must have the <c>CanSeek</c> property.
        /// If no encoding information is present, UTF-8 is assumed.</para>
        /// <para>The base URI of the query is set to the value of the <c>BaseUri</c>
        /// property. If this has not been set, then the base URI will be undefined, which
        /// means that any use of an expression that depends on the base URI will cause
        /// an error.</para>
        /// </remarks>
        /// <example>
        /// <code>
        /// XQueryExecutable q = compiler.Compile(new FileStream("input.xq", FileMode.Open, FileAccess.Read));
        /// </code>
        /// </example>
        /// <param name="query">A stream containing the source text of the query</param>
        /// <returns>An <c>XQueryExecutable</c> which represents the compiled query object.
        /// The XQueryExecutable may be run as many times as required, in the same or a different
        /// thread. The <c>XQueryExecutable</c> is not affected by any changes made to the <c>XQueryCompiler</c>
        /// once it has been compiled.</returns>
        /// <exception cref="StaticError">Throws a StaticError if errors were detected
        /// during static analysis of the query. Details of the errors will be added as StaticError
        /// objects to the ErrorList if supplied; otherwise they will be written to the standard
        /// error stream. The exception that is returned is merely a summary indicating the
        /// status.</exception>

        public XQueryExecutable Compile(Stream query)
        {
            try
            {
                JXQueryExpression exp = env.compileQuery(new JDotNetInputStream(query), null);
                return new XQueryExecutable(exp);
            }
            catch (JXPathException e)
            {
                throw new StaticError(e);
            }
        }

        /// <summary>
        /// Compile a query supplied as a String.
        /// </summary>
        /// <remarks>
        /// Using this method the query processor is provided with a string of Unicode
        /// characters, so no decoding is necessary. Any encoding information present in the
        /// version declaration is therefore ignored.
        /// </remarks>
        /// <example>
        /// <code>
        /// XQueryExecutable q = compiler.Compile("distinct-values(//*/node-name()");
        /// </code>
        /// </example>
        /// <param name="query">A string containing the source text of the query</param>
        /// <returns>An <c>XQueryExecutable</c> which represents the compiled query object.
        /// The XQueryExecutable may be run as many times as required, in the same or a different
        /// thread. The <c>XQueryExecutable</c> is not affected by any changes made to the <c>XQueryCompiler</c>
        /// once it has been compiled.</returns>
        /// <exception cref="StaticError">Throws a StaticError if errors were detected
        /// during static analysis of the query. Details of the errors will be added as StaticError
        /// objects to the ErrorList if supplied; otherwise they will be written to the standard
        /// error stream. The exception that is returned is merely a summary indicating the
        /// status.</exception>        

        public XQueryExecutable Compile(String query)
        {
            try
            {
                JXQueryExpression exp = env.compileQuery(query);
                return new XQueryExecutable(exp);
            }
            catch (JXPathException e)
            {
                throw new StaticError(e);
            }
        }

        /// <summary>
        /// Escape hatch to the underying Java implementation
        /// </summary>

        public net.sf.saxon.query.StaticQueryContext Implementation
        {
            get { return env; }
        }
    }

    /// <summary>
    /// An <c>XQueryExecutable</c> represents the compiled form of a query. To execute the query,
    /// it must first be loaded to form an <c>XQueryEvaluator</c>.
    /// </summary>
    /// <remarks>
    /// <para>An <c>XQueryExecutable</c> is immutable, and therefore thread-safe. It is simplest to
    /// load a new <c>XQueryEvaluator</c> each time the query is to be run. However, the 
    /// <c>XQueryEvaluator</c> is serially reusable within a single thread.</para>
    /// <para>An <c>XQueryExecutable</c> is created by using one of the <c>Compile</c>
    /// methods on the <c>XQueryCompiler</c> class.</para>
    /// </remarks>    

    [Serializable]
    public class XQueryExecutable
    {

        private JXQueryExpression exp;

        // internal constructor

        internal XQueryExecutable(JXQueryExpression exp)
        {
            this.exp = exp;
        }

        /// <summary>Ask whether this is an updating query (that is, one that returns a pending
        /// update list rather than a convensional value).</summary>
        /// <remarks><para><i>Property added in Saxon 9.1</i></para></remarks>

        public bool IsUpdateQuery
        {
            get { return exp.isUpdateQuery(); }
        }

		public JXQueryExpression getUnderlyingCompiledQuery(){
			return exp;
		}

        /// <summary>
        /// Load the query to prepare it for execution.
        /// </summary>
        /// <returns>
        /// An <c>XQueryEvaluator</c>. The returned <c>XQueryEvaluator</c> can be used to
        /// set up the dynamic context for query evaluation, and to run the query.
        /// </returns>

        public XQueryEvaluator Load()
        {
            return new XQueryEvaluator(exp);
        }
    }

    /// <summary inherits="IEnumerable">
    /// An <c>XQueryEvaluator</c> represents a compiled and loaded query ready for execution.
    /// The <c>XQueryEvaluator</c> holds details of the dynamic evaluation context for the query.
    /// </summary>
    /// <remarks>
    /// <para>An <c>XQueryEvaluator</c> should not be used concurrently in multiple threads. It is safe,
    /// however, to reuse the object within a single thread to run the same query several times.
    /// Running the query does not change the context that has been established.</para>
    /// <para>An <c>XQueryEvaluator</c> is always constructed by running the <c>Load</c> method of
    /// an <c>XQueryExecutable</c>.</para>
    /// </remarks>     

    [Serializable]
    public class XQueryEvaluator : IEnumerable
    {

        private JXQueryExpression exp;
        private JDynamicQueryContext context;
        private JController controller;
		private StandardLogger traceFunctionDestination;

        // internal constructor

        internal XQueryEvaluator(JXQueryExpression exp)
        {
            this.exp = exp;
            this.context =
				new JDynamicQueryContext(exp.getConfiguration());
        }

        /// <summary>
        /// The context item for the query.
        /// </summary>
        /// <remarks> This may be either a node or an atomic
        /// value. Most commonly it will be a document node, which might be constructed
        /// using the <c>LoadDocument</c> method of the <c>Processor</c> object.
        /// </remarks>

        public XdmItem ContextItem
        {
            get { return (XdmItem)XdmValue.Wrap(context.getContextItem()); }
            set { context.setContextItem((JItem)value.Unwrap()); }
        }

        /// <summary>
        /// The <c>SchemaValidationMode</c> to be used in this transformation, especially for documents
        /// loaded using the <c>doc()</c>, <c>document()</c>, or <c>collection()</c> functions.
        /// </summary>
        /// 

        public SchemaValidationMode SchemaValidationMode
        {
            get
            {
                switch (context.getSchemaValidationMode())
                {
                    case JValidation.STRICT:
                        return SchemaValidationMode.Strict;
                    case JValidation.LAX:
                        return SchemaValidationMode.Lax;
                    case JValidation.STRIP:
                        return SchemaValidationMode.None;
                    case JValidation.PRESERVE:
                        return SchemaValidationMode.Preserve;
                    case JValidation.DEFAULT:
                    default:
                        return SchemaValidationMode.Unspecified;
                }
            }

            set
            {
                switch (value)
                {
                    case SchemaValidationMode.Strict:
                        context.setSchemaValidationMode(JValidation.STRICT);
                        break;
                    case SchemaValidationMode.Lax:
                        context.setSchemaValidationMode(JValidation.LAX);
                        break;
                    case SchemaValidationMode.None:
                        context.setSchemaValidationMode(JValidation.STRIP);
                        break;
                    case SchemaValidationMode.Preserve:
                        context.setSchemaValidationMode(JValidation.PRESERVE);
                        break;
                    case SchemaValidationMode.Unspecified:
                    default:
                        context.setSchemaValidationMode(JValidation.DEFAULT);
                        break;
                }
            }
        }

        /// <summary>
        /// The <code>XmlResolver</code> to be used at run-time to resolve and dereference URIs
        /// supplied to the <c>doc()</c> function.
        /// </summary>

        public XmlResolver InputXmlResolver
        {
            get
            {
                return ((JDotNetURIResolver)context.getURIResolver()).getXmlResolver();
            }
            set
            {
                context.setURIResolver(new JDotNetURIResolver(value));
            }
        }

        /// <summary>
        /// Set the value of an external variable declared in the query.
        /// </summary>
        /// <param name="name">The name of the external variable, expressed
        /// as a QName. If an external variable of this name has been declared in the
        /// query prolog, the given value will be assigned to the variable. If the
        /// variable has not been declared, calling this method has no effect (it is
        /// not an error).</param>
        /// <param name="value">The value to be given to the external variable.
        /// If the variable declaration defines a required type for the variable, then
        /// this value must match the required type: no conversions are applied.</param>

        public void SetExternalVariable(QName name, XdmValue value)
        {
			context.setParameter(name.ToStructuredQName(), value.Unwrap());
        }

        /// <summary>
        /// Destination for output of messages produced using &lt;trace()&gt;. 
        /// <para>If no specific destination is supplied by the caller, message information will be written to
        /// the standard error stream.</para>
        /// </summary>
        /// <remarks>
        /// <para>The supplied destination is ignored if a <c>TraceListener</c> is in use.</para>
        /// <para><i>Property added in Saxon 9.1</i></para>
		/// <para>Since 9.6. Changed in 9.6 to use a StandardLogger</para>
        /// </remarks>

        public StandardLogger TraceFunctionDestination
        {
            set
            {
				traceFunctionDestination = value;
				context.setTraceFunctionDestination(value);
            }
            get
            {
                return traceFunctionDestination;
            }
        }


        /// <summary>
        /// Evaluate the query, returning the result as an <c>XdmValue</c> (that is,
        /// a sequence of nodes and/or atomic values).
        /// </summary>
        /// <returns>
        /// An <c>XdmValue</c> representing the results of the query
        /// </returns>
        /// <exception cref="DynamicError">Throws a DynamicError if any run-time failure
        /// occurs while evaluating the query.</exception>

        public XdmValue Evaluate()
        {
            try
            {
                JGroundedValue value = JSequenceExtent.makeSequenceExtent(exp.iterator(context));
                return XdmValue.Wrap(value);
            }
            catch (JXPathException err)
            {
                throw new DynamicError(err);
            }
        }

        /// <summary>
        /// Evaluate the query, returning the result as an <c>XdmItem</c> (that is,
        /// a single node or atomic value).
        /// </summary>
        /// <returns>
        /// An <c>XdmItem</c> representing the result of the query, or null if the query
        /// returns an empty sequence. If the query returns a sequence of more than one item,
        /// any items after the first are ignored.
        /// </returns>
        /// <exception cref="DynamicError">Throws a DynamicError if any run-time failure
        /// occurs while evaluating the expression.</exception>

        public XdmItem EvaluateSingle()
        {
            try
            {
                return (XdmItem)XdmValue.Wrap(exp.iterator(context).next());
            }
            catch (JXPathException err)
            {
                throw new DynamicError(err);
            }
        }

        /// <summary>
        /// Evaluate the query, returning the result as an <c>IEnumerator</c> (that is,
        /// an enumerator over a sequence of nodes and/or atomic values).
        /// </summary>
        /// <returns>
        /// An enumerator over the sequence that represents the results of the query.
        /// Each object in this sequence will be an instance of <c>XdmItem</c>. Note
        /// that the query may be evaluated lazily, which means that a successful response
        /// from this method does not imply that the query has executed successfully: failures
        /// may be reported later while retrieving items from the iterator. 
        /// </returns>
        /// <exception cref="DynamicError">Throws a DynamicError if any run-time failure
        /// occurs while evaluating the expression.</exception>

        public IEnumerator GetEnumerator()
        {
            try
            {
                return new SequenceEnumerator(exp.iterator(context));
            }
            catch (JXPathException err)
            {
                throw new DynamicError(err);
            }
        }

        /// <summary>
        /// Evaluate the query, sending the result to a specified destination.
        /// </summary>
        /// <param name="destination">
        /// The destination for the results of the query. The class <c>XmlDestination</c>
        /// is an abstraction that allows a number of different kinds of destination
        /// to be specified.
        /// </param>
        /// <exception cref="DynamicError">Throws a DynamicError if any run-time failure
        /// occurs while evaluating the expression.</exception>

        public void Run(XmlDestination destination)
        {
            try
            {
                exp.run(context, destination.GetReceiver(context.getConfiguration().makePipelineConfiguration()), destination.GetOutputProperties());
            }
            catch (JXPathException err)
            {
                throw new DynamicError(err);
            }
            destination.Close();
        }

        /// <summary>
        /// Execute an updating query.
        /// </summary>
        /// <returns>An array containing the root nodes of documents that have been
        /// updated by the query.</returns>
        /// <exception cref="DynamicError">Throws a DynamicError if any run-time failure
        /// occurs while evaluating the expression, or if the expression is not an
        /// updating query.</exception>

        public XdmNode[] RunUpdate()
        {
            if (!exp.isUpdateQuery())
            {
                throw new DynamicError("Not an updating query");
            }
            try
            {
                java.util.Set updatedDocs = exp.runUpdate(context);
                XdmNode[] result = new XdmNode[updatedDocs.size()];
                int i = 0;
                for (java.util.Iterator iter = updatedDocs.iterator(); iter.hasNext(); )
                {
                    result[i++] = (XdmNode)XdmValue.Wrap((JNodeInfo)iter.next());
                }
                return result;
            }
            catch (JXPathException err)
            {
                throw new DynamicError(err);
            }
        }

    ///<summary>
    /// Call a global user-defined function in the compiled query.
    ///</summary>
    ///<remarks>
    /// If this is called more than once (to evaluate the same function repeatedly with different arguments,
    /// or to evaluate different functions) then the sequence of evaluations uses the same values of global
    /// variables including external variables (query parameters); the effect of any changes made to query parameters
    /// between calls is undefined.
    /// </remarks>
    /// <param name="function">
    /// The name of the function to be called
    /// </param>
    /// <param name="arguments">
    /// The values of the arguments to be supplied to the function. These
    /// must be of the correct type as defined in the function signature (there is no automatic
    /// conversion to the required type).
    /// </param>
    /// <exception cref="ArgumentException">If no function has been defined with the given name and arity
    /// or if any of the arguments does not match its required type according to the function
    /// signature.</exception>
    /// <exception cref="DynamicError">If a dynamic error occurs in evaluating the function.
    /// </exception>

    public XdmValue CallFunction(QName function, XdmValue[] arguments) {
			JUserFunction fn = exp.getMainModule().getUserDefinedFunction(
                function.Uri, function.LocalName, arguments.Length);
        if (fn == null) {
            throw new ArgumentException("No function with name " + function.ClarkName +
                    " and arity " + arguments.Length + " has been declared in the query");
        }
        try {
            // TODO: use the same controller in other interfaces such as run(), and expose it in a trapdoor API
            if (controller == null) {
					controller = exp.newController(context);
                context.initializeController(controller);
					controller.initializeController(context.getParameters());                
            }
            JSequence[] vr = new JSequence[arguments.Length];

            for (int i=0; i<arguments.Length; i++) {
                JSequenceType type = fn.getParameterDefinitions()[i].getRequiredType();
                vr[i] = arguments[i].Unwrap();
					if (!type.matches(vr[i], controller.getConfiguration().getTypeHierarchy())) {
                    throw new ArgumentException("Argument " + (i+1) +
                            " of function " + function.ClarkName +
                            " does not match the required type " + type.toString());
                }
            }
            JSequence result = fn.call(vr, controller);
            return XdmValue.Wrap(result);
        } catch (JXPathException e) {
            throw new DynamicError(e);
        }
    }



        /// <summary>
        /// Escape hatch to the <c>net.sf.saxon.query.DynamicQueryContext</c> object in the underlying Java implementation
        /// </summary>

        public JDynamicQueryContext Implementation
        {
            get { return context; }
        }

    }


    /// <summary>
    /// Interface defining a user-supplied class used to retrieve XQuery library modules listed
    /// in an <c>import module</c> declaration in the query prolog.
    /// </summary>


    public interface IQueryResolver
    {

        /// <summary>
        /// Given a module URI and a set of location hints, return a set of query modules.
        /// </summary>
        /// <param name="moduleUri">The URI of the required library module as written in the
        /// <c>import module</c> declaration</param>
        /// <param name="baseUri">The base URI of the module containing the <c>import module</c>
        /// declaration</param>
        /// <param name="locationHints">The sequence of URIs (if any) listed as location hints
        /// in the <c>import module</c> declaration in the query prolog.</param>
        /// <returns>A set of absolute Uris identifying the query modules to be loaded. There is no requirement
        /// that these correspond one-to-one with the URIs defined in the <c>locationHints</c>. The 
        /// returned URIs will be dereferenced by calling the <c>GetEntity</c> method.
        /// </returns>

        /**public**/ Uri[] GetModules(String moduleUri, Uri baseUri, String[] locationHints);

        /// <summary>
        /// Dereference a URI returned by <c>GetModules</c> to retrieve a <c>Stream</c> containing
        /// the actual query text.
        /// </summary>
        /// <param name="absoluteUri">A URI returned by the <code>GetModules</code> method.</param>
        /// <returns>Either a <c>Stream</c> or a <c>String</c> containing the query text. 
        /// The supplied URI will be used as the base URI of the query module.</returns>

        /**public**/ Object GetEntity(Uri absoluteUri);

    }

   /// <summary>
	/// Internal class that wraps a (.NET) IQueryResolver to create a (Java) ModuleURIResolver
	/// <para>A ModuleURIResolver is used when resolving references to
	/// query modules. It takes as input a URI that identifies the module to be loaded, and a set of
	/// location hints, and returns one or more StreamSource obects containing the queries
	/// to be imported.</para>
   /// </summary>

    internal class DotNetModuleURIResolver : net.sf.saxon.lib.ModuleURIResolver
    {

        private IQueryResolver resolver;

		/// <summary>
		/// Initializes a new instance of the <see cref="Saxon.Api.DotNetModuleURIResolver"/> class.
		/// </summary>
		/// <param name="resolver">Resolver.</param>
        public DotNetModuleURIResolver(IQueryResolver resolver)
        {
            this.resolver = resolver;
        }


		/// <summary>
		/// Resolve a module URI and associated location hints.
		/// </summary>
		/// <param name="moduleURI">ModuleURI. The module namespace URI of the module to be imported; or null when
		/// loading a non-library module.</param>
		/// <param name="baseURI">BaseURI. The base URI of the module containing the "import module" declaration;
		/// null if no base URI is known</param>
		/// <param name="locations">Locations. The set of URIs specified in the "at" clause of "import module",
		/// which serve as location hints for the module</param>
		/// <returns>an array of StreamSource objects each identifying the contents of a module to be
		/// imported. Each StreamSource must contain a
		/// non-null absolute System ID which will be used as the base URI of the imported module,
		/// and either an InputSource or a Reader representing the text of the module. The method
		/// may also return null, in which case the system attempts to resolve the URI using the
		/// standard module URI resolver.</returns>
        public JStreamSource[] resolve(String moduleURI, String baseURI, String[] locations)
        {
            Uri baseU = (baseURI == null ? null : new Uri(baseURI));
            Uri[] modules = resolver.GetModules(moduleURI, baseU, locations);
            JStreamSource[] ss = new JStreamSource[modules.Length];
            for (int i = 0; i < ss.Length; i++)
            {
                ss[i] = new JStreamSource();
                ss[i].setSystemId(modules[i].ToString());
                Object query = resolver.GetEntity(modules[i]);
                if (query is Stream)
                {
                    ss[i].setInputStream(new JDotNetInputStream((Stream)query));
                }
                else if (query is String)
                {
                    ss[i].setReader(new JDotNetReader(new StringReader((String)query)));
                }
                else
                {
                    throw new ArgumentException("Invalid response from GetEntity()");
                }
            }
            return ss;
        }
    }






}


////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////