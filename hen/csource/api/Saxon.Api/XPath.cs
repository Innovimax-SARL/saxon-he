using System;
using System.Xml;
using System.Collections;
using System.Globalization;
using JIterator = java.util.Iterator;
using JConfiguration = net.sf.saxon.Configuration;
using JXPathEvaluator = net.sf.saxon.sxpath.XPathEvaluator;
using JItem = net.sf.saxon.om.Item;
using JSequenceExtent = net.sf.saxon.value.SequenceExtent;
using JSequence = net.sf.saxon.om.Sequence;
using JNodeInfo = net.sf.saxon.om.NodeInfo;
using JStructuredQName = net.sf.saxon.om.StructuredQName;
using JIndependentContext = net.sf.saxon.sxpath.IndependentContext;
using JXPathExpression = net.sf.saxon.sxpath.XPathExpression;
using JExpression = net.sf.saxon.expr.Expression;
using JXPathContext = net.sf.saxon.expr.XPathContext;
using JXPathDynamicContext = net.sf.saxon.sxpath.XPathDynamicContext;
using JXPathVariable = net.sf.saxon.sxpath.XPathVariable;
using JDecimalValue = net.sf.saxon.value.DecimalValue;
using JStaticProperty = net.sf.saxon.expr.StaticProperty;
using JSaxonApiException = net.sf.saxon.s9api.SaxonApiException;
using JDotNetComparator = net.sf.saxon.dotnet.DotNetComparator;
using JDotNetURIResolver = net.sf.saxon.dotnet.DotNetURIResolver;


namespace Saxon.Api
{

    /// <summary>
    /// An XPathCompiler object allows XPath queries to be compiled.
    /// The compiler holds information that represents the static context
    /// for the expression.
    /// </summary>
    /// <remarks>
    /// <para>To construct an XPathCompiler, use the factory method
    /// <c>newXPathCompiler</c> on the <c>Processor</c> object.</para>
    /// <para>An XPathCompiler may be used repeatedly to compile multiple
    /// queries. Any changes made to the XPathCompiler (that is, to the
    /// static context) do not affect queries that have already been compiled.
    /// An XPathCompiler may be used concurrently in multiple threads, but
    /// it should not then be modified once initialized.</para>
    /// <para> The <code>XPathCompiler</code> has the ability to maintain a cache of compiled
    /// expressions. This is active only if enabled by setting the <c>Caching</c> property.
    /// If caching is enabled, then the compiler will recognize an attempt to compile
    /// the same expression twice, and will avoid the cost of recompiling it. The cache
    /// is emptied by any method that changes the static context for subsequent expressions,
    /// for example, by setting the <c>BaseUri</c> property. Unless the cache is emptied,
    /// it grows indefinitely: compiled expressions are never discarded.</para>
    /// </remarks>

    [Serializable]
    public class XPathCompiler
    {

        private JConfiguration config;
        private Processor processor;
        private JIndependentContext env;
        private Hashtable cache = null;

        // internal constructor: the public interface is a factory method
        // on the Processor object

        internal XPathCompiler(Processor proc)
        {
            this.processor = proc;
			this.config = proc.Implementation;
            this.env = new JIndependentContext(config);
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

        public void DeclareCollation(Uri uri, CompareInfo compareInfo, CompareOptions options, Boolean isDefault)
        {
			JDotNetComparator comparator = new JDotNetComparator(uri.ToString(), compareInfo, options);
            env.declareCollation(uri.ToString(), comparator, isDefault);
        }



        /// <summary>
        /// Declare a namespace for use by the XPath expression.
        /// </summary>
        /// <param name="prefix">The namespace prefix to be declared. Use
        /// a zero-length string to declare the default namespace (that is, the
        /// default namespace for elements and types).</param>
        /// <param name="uri">The namespace URI. It is possible to specify
        /// a zero-length string to "undeclare" a namespace.</param>

        public void DeclareNamespace(String prefix, String uri)
        {
            if (cache != null)
            {
                cache.Clear();
            }
            env.declareNamespace(prefix, uri);
        }

        public string GetNamespaceURI(string lexicalName, Boolean useDefault) 
        {            
			String[] parts = net.sf.saxon.om.NameChecker.checkQNameParts(net.sf.saxon.value.Whitespace.trimWhitespace(lexicalName));
            return env.getNamespaceResolver().getURIForPrefix(parts[0], useDefault);
        
        }

        /// <summary>
        /// Get the Processor from which this XPathCompiler was constructed
        /// </summary>
        public Processor Processor
        {
            get { return processor; }
            set { processor = value; }
        }

        /// <summary>
        /// Import schema definitions for a specified namespace. That is, add the element and attribute declarations and type definitions
        /// contained in a given namespace to the static context for the XPath expression.
        /// </summary>
        /// <remarks>
        /// <para>This method will not cause the schema to be loaded. That must be done separately, using the
        /// <c>SchemaManager</c>}. This method will not fail if the schema has not been loaded (but in that case
        /// the set of declarations and definitions made available to the XPath expression is empty). The schema
        /// document for the specified namespace may be loaded before or after this method is called.
        /// </para>
        /// <para>
        /// This method does not bind a prefix to the namespace. That must be done separately, using the
        /// <c>declareNamespace</c> method.
        /// </para>
        /// </remarks>
        /// <param name="uri">The namespace URI whose declarations and type definitions are to
        /// be made available for use within the XPath expression.</param>
        /// 

        public void ImportSchemaNamespace(String uri)
        {
            if (cache != null)
            {
                cache.Clear();
            }
            env.getImportedSchemaNamespaces().add(uri);
        }


        /// <summary>
        /// This property indicates whether the XPath expression may contain references to variables that have not been
        /// explicitly declared by calling <c>DeclareVariable</c>. The property is false by default (that is, variables
        /// must be declared).
        /// </summary>
        /// <remarks>
        /// If undeclared variables are permitted, then it is possible to determine after compiling the expression which
        /// variables it refers to by calling the method <c>EnumerateExternalVariables</c> on the <c>XPathExecutable</c> object.
        /// </remarks>
        /// 
        public Boolean AllowUndeclaredVariables
        {
            get
            {
                return env.isAllowUndeclaredVariables();
            }
            set
            {
                if (cache != null)
                {
                    cache.Clear();
                }
                env.setAllowUndeclaredVariables(value);
            }
        }

        /// <summary>
        /// Say whether XPath expressions compiled using this XPathCompiler are
        /// schema-aware. They will automatically be schema-aware if the method
        /// <see cref="#ImportSchemaNamespace"/> is called. An XPath expression
        /// must be marked as schema-aware if it is to handle typed (validated)
        /// input documents.
        /// </summary>

        public Boolean SchemaAware
        {
            get {
				return env.getPackageData().isSchemaAware();
            }
            set 
            {
                env.setSchemaAware(value);
            }
        }


        private int checkSingleChar(String s)
        {
            int[] e = net.sf.saxon.value.StringValue.expand(s);
            if (e.Length != 1)
            {
                throw new ArgumentException("Attribute \"" + s + "\" should be a single character");
            }
            return e[0];

        }

        /// <summary>
        /// Sets a property of a selected decimal format, for use by the <c>format-number</c> function.
        /// </summary>
        /// <remarks>
        /// This method checks that the value is valid for the particular property, but it does not
        /// check that all the values for the decimal format are consistent (for example, that the
        /// decimal separator and grouping separator have different values). This consistency
        /// check is performed only when the decimal format is used.
        /// </remarks>
        /// <param name="format">The name of the decimal format whose property is to be set.
        ///  Supply null to set a property of the default (unnamed) decimal format.
        ///  This correponds to a name used in the third argument of <c>format-number</c>.</param>
        /// <param name="property">The name of the property to set: one of
        ///   "decimal-separator", "grouping-separator", "infinity", "NaN",
        ///   "minus-sign", "percent", "per-mille", "zero-digit", "digit",
        ///   or "pattern-separator".</param>
        /// <param name="value">The new value for the property.</param>

        public void SetDecimalFormatProperty(QName format, String property, String value){
            net.sf.saxon.trans.DecimalFormatManager dfm = env.getDecimalFormatManager();
            if (dfm == null)
            {
				dfm = new net.sf.saxon.trans.DecimalFormatManager(net.sf.saxon.Configuration.XPATH, env.getXPathVersion());
                env.setDecimalFormatManager(dfm);
            }
            
            net.sf.saxon.om.StructuredQName sqname = null;
            net.sf.saxon.trans.DecimalSymbols symbols = null;
            
            if (format != null) {
                sqname = net.sf.saxon.om.StructuredQName.fromClarkName(format.ClarkName);
                symbols = dfm.obtainNamedDecimalFormat(sqname);
            } else {
                symbols = dfm.getDefaultDecimalFormat();
            }

            if (property.Equals("decimal-separator"))
            {
                symbols.setDecimalSeparator(value);
            }
            else if (property.Equals("grouping-separator"))
            {
                symbols.setGroupingSeparator(value);
            }
            else if (property.Equals("infinity"))
            {
                symbols.setInfinity(value);
            }
            else if (property.Equals("NaN"))
            {
                symbols.setNaN(value);
            }
            else if (property.Equals("minus-sign"))
            {
                symbols.setMinusSign(value);
            }
            else if (property.Equals("percent"))
            {
                symbols.setPercent(value);
            }
            else if (property.Equals("per-mille"))
            {
                symbols.setPerMille(value);
            }
            else if (property.Equals("zero-digit"))
            {
                symbols.setZeroDigit(value);
                if (!net.sf.saxon.trans.DecimalSymbols.isValidZeroDigit(symbols.getZeroDigit()))
                {
                    throw new ArgumentException("Value supplied for zero-digit is not a Unicode digit representing zero");
                } 
            }
            else if (property.Equals("digit"))
            {
                symbols.setDigit(value);
            }
            else if (property.Equals("pattern-separator"))
            {
                symbols.setPatternSeparator(value);
            }
            else
            {
                throw new ArgumentException("Unknown decimal format property " + property);
            }

        }

        /// <summary>
        /// Declare a variable for use by the XPath expression. If the expression
        /// refers to any variables, then they must be declared here, unless the
        /// <c>AllowUndeclaredVariables</c> property has been set to true.
        /// </summary>
        /// <param name="name">The name of the variable, as a <c>QName</c></param>


        public void DeclareVariable(QName name)
        {
            if (cache != null)
            {
                cache.Clear();
            }
            JXPathVariable var = env.declareVariable(name.ToQNameValue());
            //declaredVariables.Add(var);
        }

        /// <summary>
        /// This property indicates which version of XPath language syntax is accepted. The default
        /// value is "1.0". This property must be set to "3.0" before compiling a query that
        /// uses XPath 3.0 (formerly known as XPath 2.1) syntax.
        /// </summary>
        /// <remarks>
        /// <para>Support for XPath 3.0 is currently limited: for details see the Saxon documentation.</para>
        /// <para><i>Property added in Saxon 9.4</i></para></remarks>


        public string XPathLanguageVersion
        {
			get {
				if (env.getXPathVersion() == 20) {
					return "2.0";
				}
				else if (env.getXPathVersion() == 30) {
					return "3.0";
				}
				else if (env.getXPathVersion() == 31) {
					return "3.1";
				}
				else throw new StaticError(new net.sf.saxon.trans.XPathException("Unknown XPath version " + env.getXPathVersion()));

			}
            set { 
				int level = 30;
				if (value.ToString() == "2.0") {
					level = 20;
				}
				else if (value.ToString() == "3.0") {
					level = 30;
				}
				else if (value.ToString() == "3.1") {
					level = 31;
				}
				env.setXPathLanguageLevel(level); }
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
        /// The base URI of the expression, which forms part of the static context
        /// of the expression. This is used for resolving any relative URIs appearing
        /// within the expression, for example in references to library modules, schema
        /// locations, or as an argument to the <c>doc()</c> function.
        /// </summary>

        public String BaseUri
        {
			get { return env.getStaticBaseURI(); }
            set {
                if (cache != null)
                {
                    cache.Clear();
                }
                env.setBaseURI(value); 
            }
        }

        /// <summary>
        /// XPath 1.0 Backwards Compatibility Mode. If true, backwards compatibility mode
        /// is set. In backwards compatibility mode, more implicit type conversions are
        /// allowed in XPath expressions, for example it is possible to compare a number
        /// with a string. The default is false (backwards compatibility mode is off).
        /// </summary>


        public Boolean BackwardsCompatible
        {
            get { return env.isInBackwardsCompatibleMode(); }
            set {
                if (cache != null)
                {
                    cache.Clear();
                }
                env.setBackwardsCompatibilityMode(value);
            }
        }

        /// <summary>
        /// XPath 1.0 Backwards Compatibility Mode. If true, backwards compatibility mode
        /// is set. In backwards compatibility mode, more implicit type conversions are
        /// allowed in XPath expressions, for example it is possible to compare a number
        /// with a string. The default is false (backwards compatibility mode is off).
        /// </summary>


        public Boolean Caching
        {
            get { 
                return cache != null; 
            }
            set
            {
                cache = (value ? new Hashtable() : null);
            }
        }



        /// <summary>
        /// Compile an expression supplied as a String.
        /// </summary>
        /// <example>
        /// <code>
        /// XPathExecutable q = compiler.Compile("distinct-values(//*/node-name()");
        /// </code>
        /// </example>
        /// <param name="source">A string containing the source text of the XPath expression</param>
        /// <returns>An <c>XPathExecutable</c> which represents the compiled xpath expression object.
        /// The XPathExecutable may be run as many times as required, in the same or a different
        /// thread. The <c>XPathExecutable</c> is not affected by any changes made to the <c>XPathCompiler</c>
        /// once it has been compiled.</returns>
        /// <exception cref="StaticError">
        /// Throws a <c>Saxon.Api.StaticError</c> if there is any static error in the XPath expression.
        /// This includes both syntax errors, semantic errors such as references to undeclared functions or
        /// variables, and statically-detected type errors.
        /// </exception>

        public XPathExecutable Compile(String source)
        {
            if (cache != null)
            {
                XPathExecutable exec = (XPathExecutable)cache[source];
                if (exec != null)
                {
                    return exec;
                }
            }
            try
            {
                JIndependentContext ic = env;
                if (ic.isAllowUndeclaredVariables())
                {
                    // self-declaring variables modify the static context. The XPathCompiler must not change state
                    // as the result of compiling an expression, so we need to copy the static context.
                    ic = new JIndependentContext(env);
                    for (JIterator iter = env.iterateExternalVariables(); iter.hasNext(); )
                    {
                        JXPathVariable var = (JXPathVariable)iter.next();
                        JXPathVariable var2 = ic.declareVariable(var.getVariableQName());
                    }
                }
                JXPathEvaluator eval = new JXPathEvaluator(config);
                eval.setStaticContext(ic);
                JXPathExpression cexp = eval.createExpression(source);
                XPathExecutable exec = new XPathExecutable(cexp, config, ic);
                if (cache != null)
                {
                    cache[source] = exec;
                }
                return exec;
            }
            catch (net.sf.saxon.trans.XPathException err)
            {
                throw new StaticError(err);
            }
        }

        /// <summary>
        /// Compile and execute an expression supplied as a String, with a given context item.
        /// </summary>
        /// <param name="expression">A string containing the source text of the XPath expression</param>
        /// <param name="contextItem">The context item to be used for evaluation of the XPath expression.
        /// May be null, in which case the expression is evaluated without any context item.</param>
        /// <returns>An <c>XdmValue</c> which is the result of evaluating the XPath expression.</returns>
        /// <exception cref="StaticError">
        /// Throws a <c>Saxon.Api.StaticError</c> if there is any static error in the XPath expression.
        /// This includes both syntax errors, semantic errors such as references to undeclared functions or
        /// variables, and statically-detected type errors.
        /// </exception>
        /// <exception cref="DynamicError">
        /// Throws a <c>Saxon.Api.DynamicError</c> if there is any dynamic error during evaluation of the XPath expression.
        /// This includes, for example, referring to the context item if no context item was supplied.
        /// </exception>

        public XdmValue Evaluate(String expression, XdmItem contextItem)
        {
            XPathSelector xs = Compile(expression).Load();
            if (contextItem != null)
            {
                xs.ContextItem = contextItem;
            }
            return xs.Evaluate();
        }

        /// <summary>
        /// Compile and execute an expression supplied as a String, with a given context item, where
        /// the expression is expected to return a single item as its result
        /// </summary>
        /// <param name="expression">A string containing the source text of the XPath expression</param>
        /// <param name="contextItem">The context item to be used for evaluation of the XPath expression.
        /// May be null, in which case the expression is evaluated without any context item.</param>
        /// <returns>If the XPath expression returns a singleton, then the the <c>XdmItem</c> 
        /// which is the result of evaluating the XPath expression. If the expression returns an empty sequence,
        /// then null. If the expression returns a sequence containing more than one item, then the first
        /// item in the result.</returns>
        /// <exception cref="StaticError">
        /// Throws a <c>Saxon.Api.StaticError</c> if there is any static error in the XPath expression.
        /// This includes both syntax errors, semantic errors such as references to undeclared functions or
        /// variables, and statically-detected type errors.
        /// </exception>
        /// <exception cref="DynamicError">
        /// Throws a <c>Saxon.Api.DynamicError</c> if there is any dynamic error during evaluation of the XPath expression.
        /// This includes, for example, referring to the context item if no context item was supplied.
        /// </exception>

        public XdmItem EvaluateSingle(String expression, XdmItem contextItem)
        {
            XPathSelector xs = Compile(expression).Load();
            if (contextItem != null)
            {
                xs.ContextItem = contextItem;
            }
            return xs.EvaluateSingle();
        }

    }

    /// <summary>
    /// An <c>XPathExecutable</c> represents the compiled form of an XPath expression. 
    /// To evaluate the expression,
    /// it must first be loaded to form an <c>XPathSelector</c>.
    /// </summary>
    /// <remarks>
    /// <para>An <c>XPathExecutable</c> is immutable, and therefore thread-safe. It is simplest to
    /// load a new <c>XPathSelector</c> each time the expression is to be evaluated. However, the 
    /// <c>XPathSelector</c> is serially reusable within a single thread.</para>
    /// <para>An <c>XPathExecutable</c> is created by using one of the <c>Compile</c>
    /// methods on the <c>XPathCompiler</c> class.</para>
    /// </remarks>    

    [Serializable]
    public class XPathExecutable
    {

        private JXPathExpression exp;
        private JConfiguration config;
        private JIndependentContext env;
        //private ArrayList declaredVariables;

        // internal constructor

        internal XPathExecutable(JXPathExpression exp, JConfiguration config,
            JIndependentContext env /*, ArrayList declaredVariables*/)
        {
            this.exp = exp;
            this.config = config;
            this.env = env;
            //this.declaredVariables = declaredVariables;
        }

        /// <summary>
        /// Get a list of external variables used by the expression. This will include both variables that were explicitly
        /// declared to the <c>XPathCompiler</c>, and (if the <c>AllowUndeclaredVariables</c> option was set) variables that
        /// are referenced within the expression but not explicitly declared.
        /// </summary>
        /// <returns>
        /// An IEnumerator over the names of the external variables, as instances of <c>QName</c>.</returns>
        
        public IEnumerator EnumerateExternalVariables()
        {
            ArrayList list = new ArrayList();
            JIterator iter = env.iterateExternalVariables();
            while (iter.hasNext())
            {
                JXPathVariable var = (JXPathVariable)iter.next();
                JStructuredQName q = var.getVariableQName();
                list.Add(new QName(q.getPrefix(), q.getURI(), q.getLocalPart()));
            }
            return list.GetEnumerator();
        }

        
        /// <summary>
        /// Get the required cardinality of a declared variable in the static context of the expression.
        /// The occurrence indicator, one of '?' (zero-or-one), 
        /// '*' (zero-or-more), '+' (one-or-more), ' ' (a single space) (exactly one),
        /// or 'º' (masculine ordinal indicator, xBA) (exactly zero). The type empty-sequence()
        /// can be represented by an occurrence indicator of 'º' with any item type.
        /// If the variable was explicitly declared, this will be the occurrence indicator that was set when the
        /// variable was declared. If no item type was set, it will be <see cref="net.sf.saxon.s9api.OccurrenceIndicator#ZERO_OR_MORE"/>.
        /// If the variable was implicitly declared by reference (which can happen only when the
        /// allowUndeclaredVariables option is set), the returned type will be
        /// <see cref="net.sf.saxon.s9api.OccurrenceIndicator#ZERO_OR_MORE"/>.
        /// If no variable with the specified QName has been declared either explicitly or implicitly,
        /// the method returns 0.
        /// </summary>
        /// <param name="variableName">the name of a declared variable</param>
        /// <returns>the required cardinality.</returns>
 

        public char GetRequiredCardinalityForVariable(QName variableName)
        {
            JXPathVariable var = env.getExternalVariable(variableName.ToStructuredQName());
            if (var == null)
            {
                return '0';
            }
            else
            {
                return GetOccurrenceIndicator(var.getRequiredType().getCardinality());
            }
        }


        //internal method

        internal char GetOccurrenceIndicator(int occurrenceIndicator)
        {
          
            
                switch (occurrenceIndicator)
                {
                    case JStaticProperty.ALLOWS_ZERO_OR_MORE:

                        return XdmSequenceType.ZERO_OR_MORE;

                    case JStaticProperty.ALLOWS_ONE_OR_MORE:

                        return XdmSequenceType.ONE_OR_MORE;

                    case JStaticProperty.ALLOWS_ZERO_OR_ONE:

                        return XdmSequenceType.ZERO_OR_ONE;

                    case JStaticProperty.EXACTLY_ONE:

                        return XdmSequenceType.ONE;

                    case JStaticProperty.ALLOWS_ZERO:

                        return XdmSequenceType.ZERO;

                    default:
                        throw new ArgumentException("Unknown occurrence indicator");
                }
            
        }


        /// <summary>
        /// Load the compiled XPath expression to prepare it for execution.
        /// </summary>
        /// <returns>
        /// An <c>XPathSelector</c>. The returned <c>XPathSelector</c> can be used to
        /// set up the dynamic context, and then to evaluate the expression.
        /// </returns>

        public XPathSelector Load()
        {
            ArrayList declaredVariables = new ArrayList();
            JIterator iter = env.iterateExternalVariables();
            while (iter.hasNext())
            {
                JXPathVariable var = (JXPathVariable)iter.next();
                declaredVariables.Add(var);
            }
            return new XPathSelector(exp, config, declaredVariables);
        }
    }

    /// <summary inherits="IEnumerable">
    /// An <c>XPathSelector</c> represents a compiled and loaded XPath expression ready for execution.
    /// The <c>XPathSelector</c> holds details of the dynamic evaluation context for the XPath expression.
    /// </summary>
    /// <remarks>
    /// <para>An <c>XPathSelector</c> should not be used concurrently in multiple threads. It is safe,
    /// however, to reuse the object within a single thread to evaluate the same XPath expression several times.
    /// Evaluating the expression does not change the context that has been established.</para>
    /// <para>An <c>XPathSelector</c> is always constructed by running the <c>Load</c> method of
    /// an <c>XPathExecutable</c>.</para>
    /// </remarks>     

    [Serializable]
    public class XPathSelector : IEnumerable
    {

        private JXPathExpression exp;
        private JConfiguration config;
        private JXPathDynamicContext dynamicContext;
        //private JIndependentContext env;
        private ArrayList declaredVariables; // a list of XPathVariable objects

        // internal constructor

        internal XPathSelector(JXPathExpression exp, JConfiguration config,
            ArrayList declaredVariables)
        {
            this.exp = exp;
            this.config = config;
            //this.env = env;
            this.declaredVariables = declaredVariables;
            this.dynamicContext = exp.createDynamicContext(null);
        }

        /// <summary>
        /// The context item for the XPath expression evaluation.
        /// </summary>
        /// <remarks> This may be either a node or an atomic
        /// value. Most commonly it will be a document node, which might be constructed
        /// using the <c>Build</c> method of the <c>DocumentBuilder</c> object.
        /// </remarks>

        public XdmItem ContextItem
        {
            get { return (XdmItem)XdmValue.Wrap(dynamicContext.getContextItem()); }
            set {
				if (value == null) {
					throw new ArgumentException("contextItem is null");
				}
				if (exp.getInternalExpression ().getPackageData ().isSchemaAware ()) {
					JItem it = ((XdmItem)value).Unwrap().head();
					if (it is JNodeInfo && (((JNodeInfo)it).getTreeInfo().isTyped())) {
						throw new ArgumentException(
							"The supplied node has been schema-validated, but the XPath expression was compiled without schema-awareness");
					
					}
				
				}

				dynamicContext.setContextItem((JItem)value.Unwrap()); }
        }

        /// <summary>
        /// Set the value of a variable
        /// </summary>
        /// <param name="name">The name of the variable. This must match the name of a variable
        /// that was declared to the XPathCompiler. No error occurs if the expression does not
        /// actually reference a variable with this name.</param>
        /// <param name="value">The value to be given to the variable.</param>
        

        public void SetVariable(QName name, XdmValue value)
        {
            JXPathVariable var = null;
            String uri = (name.Uri == null ? "" : name.Uri);
            String local = name.LocalName;
            foreach (JXPathVariable v in declaredVariables)
            {
                String vuri = v.getVariableQName().getURI();
                if (vuri == null)
                {
                    vuri = "";
                }
                if (vuri == uri && v.getVariableQName().getLocalPart() == local)
                {
                    var = v;
                    break;
                }
            }
            if (var == null)
            {
                // TODO: this seems to conflict with the documentation of the method
                throw new ArgumentException("Variable has not been declared: " + name);
            }
            dynamicContext.setVariable(var, value.Unwrap());
        }

        /// <summary>
        /// The <code>XmlResolver</code> to be used at run-time to resolve and dereference URIs
        /// supplied to the <c>doc()</c> function.
        /// </summary>

        public XmlResolver InputXmlResolver
        {
            get
            {
                return ((JDotNetURIResolver)dynamicContext.getURIResolver()).getXmlResolver();
            }
            set
            {
                dynamicContext.setURIResolver(new JDotNetURIResolver(value));
            }
        }

        /// <summary>
        /// Evaluate the expression, returning the result as an <c>XdmValue</c> (that is,
        /// a sequence of nodes and/or atomic values).
        /// </summary>
        /// <remarks>
        /// Although a singleton result <i>may</i> be represented as an <c>XdmItem</c>, there is
        /// no guarantee that this will always be the case. If you know that the expression will return at
        /// most one node or atomic value, it is best to use the <c>EvaluateSingle</c> method, which 
        /// does guarantee that an <c>XdmItem</c> (or null) will be returned.
        /// </remarks>
        /// <returns>
        /// An <c>XdmValue</c> representing the results of the expression. 
        /// </returns>
        /// <exception cref="DynamicError">
        /// Throws <c>Saxon.Api.DynamicError</c> if the evaluation of the XPath expression fails
        /// with a dynamic error.
        /// </exception>

        public XdmValue Evaluate()
        {
            try {
                JSequence value = (JSequence)JSequenceExtent.makeSequenceExtent(
                    exp.iterate(dynamicContext));
                return XdmValue.Wrap(value);
            } catch (net.sf.saxon.trans.XPathException err) {
                throw new DynamicError(err);
            }
        }

        /// <summary>
        /// Evaluate the XPath expression, returning the result as an <c>XdmItem</c> (that is,
        /// a single node or atomic value).
        /// </summary>
        /// <returns>
        /// An <c>XdmItem</c> representing the result of the expression, or null if the expression
        /// returns an empty sequence. If the expression returns a sequence of more than one item,
        /// any items after the first are ignored.
        /// </returns>
        /// <exception cref="DynamicError">
        /// Throws <c>Saxon.Api.DynamicError</c> if the evaluation of the XPath expression fails
        /// with a dynamic error.
        /// </exception>


        public XdmItem EvaluateSingle()
        {
            try
            {
                net.sf.saxon.om.Item i = exp.evaluateSingle(dynamicContext);
                if (i == null)
                {
                    return null;
                }
                return (XdmItem)XdmValue.Wrap(i);
            } catch (net.sf.saxon.trans.XPathException err) {
                throw new DynamicError(err);
            }
        }
        
        /// <summary>
        /// Evaluate the effective boolean value of the XPath expression, returning the result as a <c>Boolean</c>
        /// </summary>
        /// <returns>
        /// A <c>Boolean</c> representing the result of the expression, converted to its
        /// effective boolean value as if by applying the XPath boolean() function
        /// </returns>
        /// <exception cref="DynamicError">
        /// Throws <c>Saxon.Api.DynamicError</c> if the evaluation of the XPath expression fails
        /// with a dynamic error.
        /// </exception>


        public Boolean EffectiveBooleanValue()
        {
            try
            {
                return exp.effectiveBooleanValue(dynamicContext);
            } catch (net.sf.saxon.trans.XPathException err) {
                throw new DynamicError(err);
            }
        }
        

        /// <summary>
        /// Evaluate the expression, returning the result as an <c>IEnumerator</c> (that is,
        /// an enumerator over a sequence of nodes and/or atomic values).
        /// </summary>
        /// <returns>
        /// An enumerator over the sequence that represents the results of the expression.
        /// Each object in this sequence will be an instance of <c>XdmItem</c>. Note
        /// that the expression may be evaluated lazily, which means that a successful response
        /// from this method does not imply that the expression has executed successfully: failures
        /// may be reported later while retrieving items from the iterator. 
        /// </returns>
        /// <exception cref="DynamicError">
        /// May throw a <c>Saxon.Api.DynamicError</c> if the evaluation of the XPath expression fails
        /// with a dynamic error. However, some errors will not be detected during the invocation of this
        /// method, but only when stepping through the returned <c>SequenceEnumerator</c>.
        /// </exception>

        public IEnumerator GetEnumerator()
        {
            try {
                return new SequenceEnumerator(exp.iterate(dynamicContext));
            } catch (net.sf.saxon.trans.XPathException err) {
                throw new DynamicError(err);
            }
        }

    }

}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////