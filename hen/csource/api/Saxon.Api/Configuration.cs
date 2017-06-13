using System;
using System.IO;
using System.Xml;
using System.Collections;
using System.Collections.Specialized;
using System.Reflection;
using System.Globalization;
using javax.xml.transform;
using javax.xml.transform.stream;
using JClass = java.lang.Class;
using JBoolean = java.lang.Boolean;
using JResult = javax.xml.transform.Result;
using JSource = javax.xml.transform.Source;
using JProperties = java.util.Properties;
using JBufferedReader = java.io.BufferedReader;
using JStringReader = java.io.StringReader;
using JConfiguration = net.sf.saxon.Configuration;
using JFeatureKeys = net.sf.saxon.lib.FeatureKeys;
using JVersion = net.sf.saxon.Version;
using JDotNetNodeWrapper = net.sf.saxon.dotnet.DotNetNodeWrapper;
using JLogger = net.sf.saxon.lib.Logger;
using JDotNetDocumentWrapper = net.sf.saxon.dotnet.DotNetDocumentWrapper;
using JDotNetObjectModel = net.sf.saxon.dotnet.DotNetObjectModel;
using JNodeInfo = net.sf.saxon.om.NodeInfo;
using JTreeInfo = net.sf.saxon.om.TreeModel;
using JSequence = net.sf.saxon.om.Sequence;
using JPipelineConfiguration = net.sf.saxon.@event.PipelineConfiguration;
using JXPathContext = net.sf.saxon.expr.XPathContext;
using JSequenceExtent = net.sf.saxon.value.SequenceExtent;
using AugmentedSource = net.sf.saxon.lib.AugmentedSource;
using NodeName = net.sf.saxon.om.NodeName;
using FingerprintedQName = net.sf.saxon.om.FingerprintedQName;
using Whitespace = net.sf.saxon.value.Whitespace;
using StaticQueryContext = net.sf.saxon.query.StaticQueryContext;
using JReceiver = net.sf.saxon.@event.Receiver;
using JTreeReceiver = net.sf.saxon.@event.TreeReceiver;
using JNamespaceReducer = net.sf.saxon.@event.NamespaceReducer;
using JValidation = net.sf.saxon.lib.Validation;
using JXPathException = net.sf.saxon.trans.XPathException;
using JProcessor = net.sf.saxon.s9api.Processor;
using JParseOptions = net.sf.saxon.lib.ParseOptions;
using JItem = net.sf.saxon.om.Item;
using JPullSource = net.sf.saxon.pull.PullSource;
using JPullProvider = net.sf.saxon.pull.PullProvider;
using JDotNetWriter = net.sf.saxon.dotnet.DotNetWriter;
using JDotNetInputStream = net.sf.saxon.dotnet.DotNetInputStream;
using JDotNetURIResolver = net.sf.saxon.dotnet.DotNetURIResolver;
using JDotNetEnumerableCollection = net.sf.saxon.dotnet.DotNetEnumerableCollection;
using JDotNetPullProvider = net.sf.saxon.dotnet.DotNetPullProvider;
using JDotNetReader = net.sf.saxon.dotnet.DotNetReader;
using JDotNetComparator = net.sf.saxon.dotnet.DotNetComparator;
using JSpaceStrippingRule = net.sf.saxon.om.SpaceStrippingRule;
using JWhitespace = net.sf.saxon.value.Whitespace;
using JPreparedStylesheet = net.sf.saxon.PreparedStylesheet;
using JFilterFactory = net.sf.saxon.@event.FilterFactory;
using JProxyReceiver = net.sf.saxon.@event.ProxyReceiver;
using net.sf.saxon.om;
using net.sf.saxon.trace;
using net.sf.saxon.@event;
using net.sf.saxon.type;

namespace Saxon.Api
{

    /// <summary>
    /// The Processor class serves three purposes: it allows global Saxon configuration
    /// options to be set; it acts as a factory for generating XQuery, XPath, and XSLT
    /// compilers; and it owns certain shared resources such as the Saxon NamePool and 
    /// compiled schemas. This is the first object that a Saxon application should create. Once
    /// established, a Processor may be used in multiple threads.
    /// </summary>

    [Serializable]
    public class Processor 
    {
        
        //Transformation data variables
        private SchemaManager schemaManager = null;
        /*internal JConfiguration config;
       */
        private TextWriter textWriter = Console.Error;
        private JProcessor processor;
        /// <summary>
        /// Create a new Processor. This Processor will have capabilities that depend on the version
        /// of the software that has been loaded, and on the features that have been licensed.
        /// </summary>

        public Processor()
        {
            
            /*config = JConfiguration.newConfiguration();
            config.registerExternalObjectModel(new DotNetObjectModelDefinition());
            config.setProcessor(this);*/
            processor = new JProcessor(false);
            processor.getUnderlyingConfiguration().registerExternalObjectModel(new DotNetObjectModelDefinition());
        }

        /// <summary>
        /// Create a Processor.
        /// </summary>
        /// <param name="licensedEdition">Set to true if the Processor is to use a licensed edition of Saxon
        /// (that is, Saxon-PE or Saxon-EE). If true, the Processor will attempt to enable the capabilities
        /// of the licensed edition of Saxon, according to the version of the software that is loaded, and will
        /// verify the license key. If false, the Processor will load a default Configuration that gives restricted
        /// capability and does not require a license, regardless of which version of the software is actually being run.</param>

        public Processor(bool licensedEdition) 
            // newline needed by documentation stylesheet
            : this(licensedEdition, false) { }

        /// <summary>
        /// Create a Processor.
        /// </summary>
        /// <param name="licensedEdition">Set to true if the Processor is to use a licensed edition of Saxon
        /// (that is, Saxon-PE or Saxon-EE). If true, the Processor will attempt to enable the capabilities
        /// of the licensed edition of Saxon, according to the version of the software that is loaded, and will
        /// verify the license key. If false, the Processor will load a default Configuration that gives restricted
        /// capability and does not require a license, regardless of which version of the software is actually being run.</param>
        /// <param name="loadLocally">This option has no effect at this release.</param>

        public Processor(bool licensedEdition, bool loadLocally)
        {
            processor = new JProcessor(licensedEdition);
			processor.getUnderlyingConfiguration ().registerExternalObjectModel (new DotNetObjectModelDefinition());
            /*if (licensedEdition)
            {
                
                config = JConfiguration.newConfiguration();
                schemaManager = new SchemaManager(config);
            }
            else
            {
                config = new JConfiguration();
            }
            config.registerExternalObjectModel(new DotNetObjectModelDefinition());
            config.setProcessor(this);*/
        }

        /// <summary>
        /// Create a Processor, based on configuration information supplied in a configuration file.
        /// </summary>
        /// <remarks>
        /// Not fully supported in this release: for experimental use only.
        /// </remarks>
        /// <param name="configurationFile">A stream holding the text of the XML configuration file. Details of the file format
        /// can be found in the Saxon documentation.</param>
        /// 

        public Processor(Stream configurationFile)
        {
            StreamSource ss = new StreamSource(new JDotNetInputStream(configurationFile));
            JConfiguration config = JConfiguration.readConfiguration(ss);
            config.registerExternalObjectModel(new DotNetObjectModelDefinition());
            //config.setProcessor(this);
            processor = new JProcessor(config);
        }


        /// <summary>
        /// Get the full name of the Saxon product version implemented by this Processor
        /// </summary>

        public string ProductTitle
        {
            get { return JVersion.getProductTitle(); }
        }

        /// <summary>
        /// Get the Saxon product version number (for example, "9.2.0.2")
        /// </summary>

        public string ProductVersion
        {
            get { return JVersion.getProductVersion(); }
        }

        /// <summary>
        /// Get the Saxon product edition (for example, "EE" for Enterprise Edition)
        /// </summary>
        /// 

        public string Edition
        {
            get { return processor.getUnderlyingConfiguration().getEditionCode(); }
        }



        /// <summary>
        /// Gets the SchemaManager for the Processor. Returns null
        /// if the Processor is not schema-aware.
        /// </summary>

        public SchemaManager SchemaManager
        {
            get {
                if (schemaManager == null)
                {
                    schemaManager = new SchemaManager(this);
                }
                
                return schemaManager; }
        }

        /// <summary>
        /// An XmlResolver, which will be used while compiling and running queries, 
        /// XPath expressions, and stylesheets, if no other XmlResolver is nominated
        /// </summary>
        /// <remarks>
        /// <para>By default an <c>XmlUrlResolver</c> is used. This means that the responsibility
        /// for resolving and dereferencing URIs rests with the .NET platform, not with the
        /// GNU Classpath.</para>
        /// <para>When Saxon invokes a user-written <c>XmlResolver</c>, the <c>GetEntity</c> method
        /// may return any of: a <c>System.IO.Stream</c>; a <c>System.IO.TextReader</c>; or a
        /// <c>java.xml.transform.Source</c>.</para>
        /// </remarks>

        public XmlResolver XmlResolver
        {
            get
            {
				URIResolver resolver = processor.getUnderlyingConfiguration ().getURIResolver ();
				if (resolver is JDotNetURIResolver) {
					return ((JDotNetURIResolver)resolver).getXmlResolver ();
				} else {
					return new XmlUrlResolver();
				}
            }
            set
            {
                processor.getUnderlyingConfiguration().setURIResolver(new JDotNetURIResolver(value));
            }
        }


        /// <summary>
        /// A TextWriter used to get and set the errors from the standard output Writer
        /// </summary>
        /// <remarks>
        /// <para>By default the <c>Console.Error</c> is used on the .NET platform.</para>
        /// <para>A user can supply their own TextWriter to redirect error messages from the standard output.</para>
        /// </remarks>
        public TextWriter ErrorWriter 
        {

            get 
            {
                return textWriter;
            
            }
            set 
            {
                textWriter = value;
				StandardLogger logger = new StandardLogger (value); 
				processor.getUnderlyingConfiguration ().setLogger (logger);
            }
        
        
        }

        /// <summary>
        /// Create a new <c>DocumentBuilder</c>, which may be used to build XDM documents from
        /// a variety of sources.
        /// </summary>
        /// <returns>A new <c>DocumentBuilder</c></returns>

        public DocumentBuilder NewDocumentBuilder()
        {
            DocumentBuilder builder = new DocumentBuilder(this);
            builder.XmlResolver = XmlResolver;
            return builder;
        }

        /// <summary>
        /// Create a new XQueryCompiler, which may be used to compile XQuery queries.
        /// </summary>
        /// <remarks>
        /// The returned XQueryCompiler retains a live link to the Processor, and
        /// may be affected by subsequent changes to the Processor.
        /// </remarks>
        /// <returns>A new XQueryCompiler</returns>

        public XQueryCompiler NewXQueryCompiler()
        {
            return new XQueryCompiler(this);
        }

        /// <summary>
        /// Create a new XsltCompiler, which may be used to compile XSLT stylesheets.
        /// </summary>
        /// <remarks>
        /// The returned XsltCompiler retains a live link to the Processor, and
        /// may be affected by subsequent changes to the Processor.
        /// </remarks>
        /// <returns>A new XsltCompiler</returns>

        public XsltCompiler NewXsltCompiler()
        {
            return new XsltCompiler(this);
        }

        /// <summary>
        /// Create a new XPathCompiler, which may be used to compile XPath expressions.
        /// </summary>
        /// <remarks>
        /// The returned XPathCompiler retains a live link to the Processor, and
        /// may be affected by subsequent changes to the Processor.
        /// </remarks>
        /// <returns>A new XPathCompiler</returns>

        public XPathCompiler NewXPathCompiler()
        {
            return new XPathCompiler(this);
        }

        /// <summary>
        /// Create a Serializer
        /// </summary>
        ///  <returns> a new Serializer </returns>
        public Serializer NewSerializer() {
            Serializer s = new Serializer();
            s.SetProcessor(this);
            return s;
        }

        /// <summary>
        /// Create a Serializer initialized to write to a given Writer.
        /// Closing the writer after use is the responsibility of the caller.
        /// </summary>
        /// <param name="textWriter">writer The TextWriter to which the Serializer will write</param>
        ///  <returns> a new Serializer </returns>
        public Serializer NewSerializer(TextWriter textWriter)
        {
            Serializer s = new Serializer();
            s.SetProcessor(this);
            s.SetOutputWriter(textWriter);
            return s;
        }

        /// <summary>
        /// Create a Serializer initialized to write to a given OutputStream.
        /// Closing the output stream after use is the responsibility of the caller.
        /// </summary>
        /// <param name="stream">stream The OutputStream to which the Serializer will write</param>
        ///  <returns> a new Serializer </returns>
        public Serializer NewSerializer(Stream stream)
        {
            Serializer s = new Serializer();
            s.SetProcessor(this);
            s.SetOutputStream(stream);
            return s;
        }

        /// <summary>
        /// The XML version used in this <c>Processor</c> (for example, this determines what characters
        /// are permitted in a name)
        /// </summary>
        /// <remarks>
        /// The value must be 1.0 or 1.1, as a <c>decimal</c>. The default version is currently 1.0, but may
        /// change in the future.
        /// </remarks>

        public decimal XmlVersion
        {
            get
            {
                return (processor.getUnderlyingConfiguration().getXMLVersion() == JConfiguration.XML10 ? 1.0m : 1.1m);
            }
            set
            {
                if (value == 1.0m)
                {
                    processor.setXmlVersion("1.0");
                }
                else if (value == 1.1m)
                {
                    processor.setXmlVersion("1.1");
                }
                else
                {
                    throw new ArgumentException("Invalid XML version: " + value);
                }
            }
        }

        /// <summary>
        /// Create a collation based on a given <c>CompareInfo</c> and <c>CompareOptions</c>    
        /// </summary>
        /// <param name="uri">The collation URI to be used within the XPath expression to refer to this collation</param>
        /// <param name="compareInfo">The <c>CompareInfo</c>, which determines the language-specific
        /// collation rules to be used</param>
        /// <param name="options">Options to be used in performing comparisons, for example
        /// whether they are to be case-blind and/or accent-blind</param>

        public void DeclareCollation(Uri uri, CompareInfo compareInfo, CompareOptions options)
        {
            JDotNetComparator comparator = new JDotNetComparator(uri.ToString(), compareInfo, options);
            Implementation.registerCollation(uri.ToString(), comparator);
        }

        /// <summary>
        /// Register a named collection. A collection is identified by a URI (the collection URI),
        /// and its content is represented by an <c>IEnumerable</c> that enumerates the contents
        /// of the collection. The values delivered by this enumeration are Uri values, which 
        /// can be mapped to nodes using the registered <c>XmlResolver</c>.
        /// </summary>
        /// <param name="collectionUri">The URI used to identify the collection in a call
        /// of the XPath <c>collection()</c> function. The default collection is registered
        /// by supplying null as the value of this argument (this is the collection returned
        /// when the XPath <c>collection()</c> function is called with no arguments).</param> 
        /// <param name="contents">An enumerable object that represents the contents of the
        /// collection, as a sequence of document URIs. The enumerator returned by this
        /// IEnumerable object must return instances of the Uri class.</param>
        /// <remarks>
        /// <para>Collections should be stable: that is, two calls to retrieve the same collection URI
        /// should return the same sequence of document URIs. This requirement is imposed by the
        /// W3C specifications, but in the case of a user-defined collection it is not enforced by
        /// the Saxon product.</para>
        /// <para>A collection may be replaced by specifying the URI of an existing
        /// collection.</para>
        /// <para>Collections registered with a processor are available to all queries and stylesheets
        /// running under the control of that processor. Collections should not normally be registered
        /// while queries and transformations are in progress.</para>
        /// </remarks>
        /// 

        public void RegisterCollection(Uri collectionUri, IEnumerable contents)
        {
            String u = (collectionUri == null ? null : collectionUri.ToString());
            JConfiguration config = processor.getUnderlyingConfiguration();
            config.registerCollection(u, new JDotNetEnumerableCollection(config, contents));
        }

        /// <summary>
        /// Register an extension function with the Processor
        /// </summary>
        /// <param name="function">
        /// An object that defines the extension function, including its name, arity, arguments types, and
        /// a reference to the class that implements the extension function call.
        /// </param>

        public void RegisterExtensionFunction(ExtensionFunctionDefinition function)
        {
            WrappedExtensionFunctionDefinition f = new WrappedExtensionFunctionDefinition(function);
            processor.registerExtensionFunction(f);
        }

        /// <summary>
        /// Copy an XdmValue to an XmlDestination
        /// </summary>
        /// <remarks>
        /// This method can be used to copy any kind of <c>XdmValue</c> to any kind
        /// of <c>XdmDestination</c>. The supplied <c>XdmValue</c> is first converted
        /// to an XML document according to the rules of the XSLT/XQuery serialization
        /// specification (for example, if the <c>XdmValue</c> is a sequence of atomic
        /// values, they will be turned in a text node in which the values are converted
        /// to strings and separated by single spaces). The resulting document is then
        /// written to the supplied <c>XmlDestination</c>.</remarks>
        /// <param name="sequence">The value to be written</param>
        /// <param name="destination">The destination to which the value should be written</param>
        /// 

        public void WriteXdmValue(XdmValue sequence, XmlDestination destination)
        {
            try
            {
				JPipelineConfiguration pipe = processor.getUnderlyingConfiguration().makePipelineConfiguration();
				JResult result = destination.GetReceiver(pipe);
				JReceiver r = processor.getUnderlyingConfiguration().getSerializerFactory().getReceiver(result,
                    pipe, destination.GetOutputProperties());
                r = new JNamespaceReducer(r);
                JTreeReceiver tree = new JTreeReceiver(r);
                tree.open();
                tree.startDocument(0);
                foreach (XdmItem it in sequence)
                {
                    tree.append((JItem)it.Unwrap());
                }
                tree.endDocument();
                tree.close();
            } catch(JXPathException err) {
                throw new DynamicError(err);
            }
        }


        /// <summary>
        /// The underlying Configuration object in the Saxon implementation
        /// </summary>
        /// <remarks>
        /// <para>This property provides access to internal methods in the Saxon engine that are
        /// not specifically exposed in the .NET API. In general these methods should be
        /// considered to be less stable than the classes in the Saxon.Api namespace.</para> 
        /// <para>The internal methods follow
        /// Java naming conventions rather than .NET conventions.</para>
        /// <para>Information about the returned object (and the objects it provides access to)
        /// is included in the Saxon JavaDoc docmentation, available 
        /// <link href="http://www.saxonica.com/documentation/javadoc/index.html">online</link>.
        /// </para>
        /// </remarks>

        public net.sf.saxon.Configuration Implementation
        {
            get { return processor.getUnderlyingConfiguration(); }
        }

        public net.sf.saxon.s9api.Processor JProcessor
        {
            get { return processor;}
        }

        /// <summary>
        /// Set a configuration property
        /// </summary>
        /// <remarks>
        /// <para>This method provides the ability to set named properties of the configuration.
        /// The property names are set as strings, whose values can be found in the Java
        /// class <c>net.sf.saxon.FeatureKeys</c>. The property values are always strings. 
        /// Properties whose values are other types are not available via this interface:
        /// however all properties have an effective equivalent whose value is a string.
        /// Note that on/off properties are set using the strings "true" and "false".</para>
        /// <para><i>Method added in Saxon 9.1</i></para>
        /// </remarks>
        /// <param name="name">The property name</param>
        /// <param name="value">The property value</param>

        public void SetProperty(String name, String value)
        {
            processor.setConfigurationProperty(name, value);
        }

        /// <summary>
        /// Get the value of a configuration property
        /// </summary>
        /// <remarks>
        /// <para>This method provides the ability to get named properties of the configuration.
        /// The property names are supplied as strings, whose values can be found in the Java
        /// class <c>net.sf.saxon.FeatureKeys</c>. The property values are always returned as strings. 
        /// Properties whose values are other types are returned by converting the value to a string.
        /// Note that on/off properties are returned using the strings "true" and "false".</para>
        /// <para><i>Method added in Saxon 9.1</i></para>
        /// </remarks>
        /// <param name="name">The property name</param>
        /// <returns>The property value, as a string; or null if the property is unset.</returns>

        public String GetProperty(String name)
        {
            Object obj = processor.getConfigurationProperty(name);
            return (obj == null ? null : obj.ToString());
        }

    }

    /// <summary>
    /// The <c>DocumentBuilder</c> class enables XDM documents to be built from various sources.
    /// The class is always instantiated using the <c>NewDocumentBuilder</c> method
    /// on the <c>Processor</c> object.
    /// </summary>

    [Serializable]
    public class DocumentBuilder
    {

        private Processor processor;
        private JConfiguration config;
        private XmlResolver xmlResolver;
        private SchemaValidationMode validation;
        private bool dtdValidation;
        private bool lineNumbering;
        private WhitespacePolicy whitespacePolicy;
        private Uri baseUri;
        private QName topLevelElement;
        private TreeModel treeModel = TreeModel.Unspecified;
		private XQueryExecutable projectionQuery;

        internal DocumentBuilder(Processor processor)
        {
            this.processor = processor;
            this.config = processor.Implementation;
            this.xmlResolver = new XmlUrlResolver();
        }

        /// <summary>
        /// An XmlResolver, which will be used to resolve URIs of documents being loaded
        /// and of references to external entities within those documents (including any external DTD).
        /// </summary>
        /// <remarks>
        /// <para>By default an <c>XmlUrlResolver</c> is used. This means that the responsibility
        /// for resolving and dereferencing URIs rests with the .NET platform (and not with the
        /// GNU Classpath).</para>
        /// <para>When Saxon invokes a user-written <c>XmlResolver</c>, the <c>GetEntity</c> method
        /// may return any of: a <c>System.IO.Stream</c>; a <c>System.IO.TextReader</c>; or a
        /// <c>java.xml.transform.Source</c>. However, if the <c>XmlResolver</c> is called
        /// by the XML parser to resolve external entity references, then it must return an 
        /// instance of <c>System.IO.Stream</c>.</para>
        /// </remarks>

        public XmlResolver XmlResolver
        {
            get
            {
                return xmlResolver;
            }
            set
            {
                xmlResolver = value;
            }
        }

        /// <summary>
        /// Determines whether line numbering is enabled for documents loaded using this
        /// <c>DocumentBuilder</c>.
        /// </summary>
        /// <remarks>
        /// <para>By default, line numbering is disabled.</para>
        /// <para>Line numbering is not available for all kinds of source: in particular,
        /// it is not available when loading from an existing XmlDocument.</para>
        /// <para>The resulting line numbers are accessible to applications using the
        /// extension function saxon:line-number() applied to a node.</para>  
        /// <para>Line numbers are maintained only for element nodes; the line number
        /// returned for any other node will be that of the most recent element.</para> 
        /// </remarks>

        public bool IsLineNumbering
        {
            get
            {
                return lineNumbering;
            }
            set
            {
                lineNumbering = value;
            }
        }

        /// <summary>
        /// Determines whether schema validation is applied to documents loaded using this
        /// <c>DocumentBuilder</c>, and if so, whether it is strict or lax.
        /// </summary>
        /// <remarks>
        /// <para>By default, no schema validation takes place.</para>
        /// <para>This option requires the schema-aware version of the Saxon product (Saxon-SA).</para>
        /// </remarks>

        public SchemaValidationMode SchemaValidationMode
        {
            get
            {
                return validation;
            }
            set
            {
                validation = value;
            }
        }

        /// <summary>
        /// The required name of the top level element in a document instance being validated
        /// against a schema.
        /// </summary>
        /// <remarks>
        /// <para>If this property is set, and if schema validation is requested, then validation will
        /// fail unless the outermost element of the document has the required name.</para>
        /// <para>This option requires the schema-aware version of the Saxon product (Saxon-SA).</para>
        /// </remarks> 

        public QName TopLevelElementName
        {
            get
            {
                return topLevelElement;
            }
            set
            {
                topLevelElement = value;
            }
        }

        /// <summary>
        /// Determines whether DTD validation is applied to documents loaded using this
        /// <c>DocumentBuilder</c>.
        /// </summary>
        /// <remarks>
        ///
        /// <para>By default, no DTD validation takes place.</para>
        /// 
        /// </remarks>

        public bool DtdValidation
        {
            get
            {
                return dtdValidation;
            }
            set
            {
                dtdValidation = value;
            }
        }

        /// <summary>
        /// Determines the whitespace stripping policy applied when loading a document
        /// using this <c>DocumentBuilder</c>.
        /// </summary>
        /// <remarks>
        /// <para>By default, whitespace text nodes appearing in element-only content
        /// are stripped, and all other whitespace text nodes are retained.</para>
        /// </remarks>

        public WhitespacePolicy WhitespacePolicy
        {
            get
            {
                return whitespacePolicy;
            }
            set
            {
                whitespacePolicy = value;
            }
        }

        ///<summary>
        /// The Tree Model implementation to be used for the constructed document. By default
        /// the TinyTree is used. The main reason for using the LinkedTree alternative is if
        /// updating is required (the TinyTree is not updateable).
        ///</summary>

        public TreeModel TreeModel
        {
            get
            {
                return treeModel;
            }
            set
            {
                treeModel = value;
            }
        }

        /// <summary>
        /// The base URI of a document loaded using this <c>DocumentBuilder</c>.
        /// This is used for resolving any relative URIs appearing
        /// within the document, for example in references to DTDs and external entities.
        /// </summary>
        /// <remarks>
        /// This information is required when the document is loaded from a source that does not
        /// provide an intrinsic URI, notably when loading from a Stream or a TextReader.
        /// </remarks>


        public Uri BaseUri
        {
            get { return baseUri; }
            set { baseUri = value; }
        }


		///<summary>
     	/// Set a compiled query to be used for implementing document projection. The effect of using
     	/// this option is that the tree constructed by the DocumentBuilder contains only those parts
    	/// of the source document that are needed to answer this query. Running this query against
    	/// the projected document should give the same results as against the raw document, but the
     	/// projected document typically occupies significantly less memory. It is permissible to run
    	/// other queries against the projected document, but unless they are carefully chosen, they
    	/// will give the wrong answer, because the document being used is different from the original.
		/// </summary>
    	/// <para>The query should be written to use the projected document as its initial context item.
    	/// For example, if the query is <code>//ITEM[COLOR='blue')</code>, then only <code>ITEM</code>
    	/// elements and their <code>COLOR</code> children will be retained in the projected document.</para>
    	/// <para>This facility is only available in Saxon-EE; if the facility is not available,
    	/// calling this method has no effect.</para>
		///<para>query the compiled query used to control document projection</para>
    	/// @since 9.6
    

		public XQueryExecutable DocumentProjectionQuery {
			get { return projectionQuery;}
			set { projectionQuery = value;}

		}

        /// <summary>
        /// Load an XML document, retrieving it via a URI.
        /// </summary>
        /// <remarks>
        /// <para>Note that the type <c>Uri</c> requires an absolute URI.</para>
        /// <para>The URI is dereferenced using the registered <c>XmlResolver</c>.</para>
        /// <para>This method takes no account of any fragment part in the URI.</para>
        /// <para>The <c>role</c> passed to the <c>GetEntity</c> method of the <c>XmlResolver</c> 
        /// is "application/xml", and the required return type is <c>System.IO.Stream</c>.</para>
        /// <para>The document located via the URI is parsed using the <c>System.Xml</c> parser.</para>
        /// <para>Note that the Microsoft <c>System.Xml</c> parser does not report whether attributes are
        /// defined in the DTD as being of type <c>ID</c> and <c>IDREF</c>. This is true whether or not
        /// DTD-based validation is enabled. This means that such attributes are not accessible to the 
        /// <c>id()</c> and <c>idref()</c> functions.</para>
        /// </remarks>
        /// <param name="uri">The URI identifying the location where the document can be
        /// found. This will also be used as the base URI of the document (regardless
        /// of the setting of the <c>BaseUri</c> property).</param>
        /// <returns>An <c>XdmNode</c>. This will be
        ///  the document node at the root of the tree of the resulting in-memory document. 
        /// </returns>

        public XdmNode Build(Uri uri)
        {
            Object obj = XmlResolver.GetEntity(uri, "application/xml", System.Type.GetType("System.IO.Stream"));
            if (obj is Stream)
            {
                try
                {
                    return Build((Stream)obj, uri);
                }
                finally
                {
                    ((Stream)obj).Close();
                }
            }
            else
            {
                throw new ArgumentException("Invalid type of result from XmlResolver.GetEntity: " + obj);
            }
        }

        /// <summary>
        /// Load an XML document supplied as raw (lexical) XML on a Stream.
        /// </summary>
        /// <remarks>
        /// <para>The document is parsed using the Microsoft <c>System.Xml</c> parser if the
        /// "http://saxon.sf.net/feature/preferJaxpParser" property on the <c>Processor</c> is set to false;
        /// otherwise it is parsed using the Apache Xerces XML parser.</para>
        /// <para>Before calling this method, the <c>BaseUri</c> property must be set to identify the
        /// base URI of this document, used for resolving any relative URIs contained within it.</para>
        /// <para>Note that the Microsoft <c>System.Xml</c> parser does not report whether attributes are
        /// defined in the DTD as being of type <c>ID</c> and <c>IDREF</c>. This is true whether or not
        /// DTD-based validation is enabled. This means that such attributes are not accessible to the 
        /// <c>id()</c> and <c>idref()</c> functions.</para>         
        /// </remarks>
        /// <param name="input">The Stream containing the XML source to be parsed</param>
        /// <returns>An <c>XdmNode</c>, the document node at the root of the tree of the resulting
        /// in-memory document
        /// </returns>

        public XdmNode Build(Stream input)
        {
            if (baseUri == null)
            {
                throw new ArgumentException("No base URI supplied");
            }
            return Build(input, baseUri);
        }

        // Build a document from a given stream, with the base URI supplied
        // as an extra argument

        internal XdmNode Build(Stream input, Uri baseUri)
        {
            Source source;
			JParseOptions options = new JParseOptions(config.getParseOptions());

            if (processor.GetProperty("http://saxon.sf.net/feature/preferJaxpParser") == "true")
            {
                source = new StreamSource(new JDotNetInputStream(input), baseUri.ToString());
				options.setEntityResolver(new JDotNetURIResolver(XmlResolver));
            }
            else
            {

                XmlReaderSettings settings = new XmlReaderSettings();
				settings.DtdProcessing = DtdProcessing.Parse;   // must expand entity references


                //((XmlTextReader)parser).Normalization = true;
                if (whitespacePolicy != null) {
                    int optioni = whitespacePolicy.ordinal();
                    if (optioni == JWhitespace.XSLT)
                    {
                        options.setStripSpace(JWhitespace.XSLT);
                        options.addFilter(whitespacePolicy.makeStripper());
                    }
                    else {
                        options.setStripSpace(optioni);
                    }


                }
               
                if (xmlResolver != null)
                {
                    settings.XmlResolver = xmlResolver;
                }
                
                settings.ValidationType = (dtdValidation ? ValidationType.DTD : ValidationType.None);
                
                XmlReader parser = XmlReader.Create(input, settings, baseUri.ToString());
                source = new JPullSource(new JDotNetPullProvider(parser));
                source.setSystemId(baseUri.ToString());
            }
            augmentParseOptions(options);
			JNodeInfo doc = config.buildDocument(source, options);
			return (XdmNode)XdmValue.Wrap(doc);
        }

        /// <summary>
        /// Load an XML document supplied using a TextReader.
        /// </summary>
        /// <remarks>
        /// <para>The document is parsed using the Microsoft <c>System.Xml</c> parser if the
        /// "http://saxon.sf.net/feature/preferJaxpParser" property on the <c>Processor</c> is set to false;
        /// otherwise it is parsed using the Apache Xerces XML parser.</para>
        /// <para>Before calling this method, the <c>BaseUri</c> property must be set to identify the
        /// base URI of this document, used for resolving any relative URIs contained within it.</para>
        /// <para>Note that the Microsoft <c>System.Xml</c> parser does not report whether attributes are
        /// defined in the DTD as being of type <c>ID</c> and <c>IDREF</c>. This is true whether or not
        /// DTD-based validation is enabled. This means that such attributes are not accessible to the 
        /// <c>id()</c> and <c>idref()</c> functions.</para>         
        /// </remarks>
        /// <param name="input">The <c>TextReader</c> containing the XML source to be parsed</param>
        /// <returns>An <c>XdmNode</c>, the document node at the root of the tree of the resulting
        /// in-memory document
        /// </returns>

        public XdmNode Build(TextReader input)
        {
            if (baseUri == null)
            {
                throw new ArgumentException("No base URI supplied");
            }
            return Build(input, baseUri);
        }

        // Build a document from a given stream, with the base URI supplied
        // as an extra argument

        internal XdmNode Build(TextReader input, Uri baseUri)
        {
            Source source;
			JParseOptions options = new JParseOptions(config.getParseOptions());
            if (processor.GetProperty("http://saxon.sf.net/feature/preferJaxpParser") == "true")
            {
                source = new StreamSource(new JDotNetReader(input), baseUri.ToString());
                options.setEntityResolver(new JDotNetURIResolver(XmlResolver));
            }
            else
            {

                XmlReaderSettings settings = new XmlReaderSettings();
                settings.DtdProcessing = DtdProcessing.Parse;   // must expand entity references


                //((XmlTextReader)parser).Normalization = true;
                if (whitespacePolicy != null)
                {
                    int option = whitespacePolicy.ordinal();
                    if (option == JWhitespace.XSLT) {
                        options.setStripSpace(JWhitespace.NONE);
                        options.addFilter(whitespacePolicy.makeStripper());
                    } else {
                        options.setStripSpace(option);
                    }
                
                }
                if (xmlResolver != null)
                {
                    settings.XmlResolver = xmlResolver;
                }

                settings.ValidationType = (dtdValidation ? ValidationType.DTD : ValidationType.None);

                XmlReader parser = XmlReader.Create(input, settings, baseUri.ToString());
                source = new JPullSource(new JDotNetPullProvider(parser));
                source.setSystemId(baseUri.ToString());
            }
            augmentParseOptions(options);
			JNodeInfo doc = config.buildDocument(source, options);
            return (XdmNode)XdmValue.Wrap(doc);
        }

        private Source augmentSource(Source source)
        {
            if (validation != SchemaValidationMode.None)
            {
                source = AugmentedSource.makeAugmentedSource(source);
                if (validation == SchemaValidationMode.Strict)
                {
                    ((AugmentedSource)source).setSchemaValidationMode(JValidation.STRICT);
                }
                else if (validation == SchemaValidationMode.Lax)
                {
                    ((AugmentedSource)source).setSchemaValidationMode(JValidation.LAX);
                }
                else if (validation == SchemaValidationMode.None)
                {
                    ((AugmentedSource)source).setSchemaValidationMode(JValidation.STRIP);
                }
                else if (validation == SchemaValidationMode.Preserve)
                {
                    ((AugmentedSource)source).setSchemaValidationMode(JValidation.PRESERVE);
                }
            }
            if (topLevelElement != null)
            {
                source = AugmentedSource.makeAugmentedSource(source);
                ((AugmentedSource)source).setTopLevelElement(
                    new FingerprintedQName(
						topLevelElement.Prefix, topLevelElement.Uri.ToString(), topLevelElement.LocalName).getStructuredQName());
            }

            if (whitespacePolicy != WhitespacePolicy.PreserveAll)
            {
                source = AugmentedSource.makeAugmentedSource(source);
                if (whitespacePolicy == WhitespacePolicy.StripIgnorable)
                {
                    ((AugmentedSource)source).setStripSpace(Whitespace.IGNORABLE);
                }
                else
                {
                    ((AugmentedSource)source).setStripSpace(Whitespace.ALL);
                }
            }
            if (treeModel != TreeModel.Unspecified)
            {
                source = AugmentedSource.makeAugmentedSource(source);
                if (treeModel == TreeModel.TinyTree)
                {
                    ((AugmentedSource)source).setModel(net.sf.saxon.om.TreeModel.TINY_TREE);
                }
                else if (treeModel == TreeModel.TinyTreeCondensed)
                {
                    ((AugmentedSource)source).setModel(net.sf.saxon.om.TreeModel.TINY_TREE_CONDENSED);
                }
                else
                {
                    ((AugmentedSource)source).setModel(net.sf.saxon.om.TreeModel.LINKED_TREE);
                }
            }
            if (lineNumbering)
            {
                source = AugmentedSource.makeAugmentedSource(source);
                ((AugmentedSource)source).setLineNumbering(true);
            }
            if (dtdValidation)
            {
                source = AugmentedSource.makeAugmentedSource(source);
                ((AugmentedSource)source).setDTDValidationMode(JValidation.STRICT);
            }
			if (projectionQuery != null) {
				net.sf.saxon.query.XQueryExpression exp = projectionQuery.getUnderlyingCompiledQuery();
				net.sf.saxon.@event.FilterFactory ff = config.makeDocumentProjector(exp);
				if (ff != null) {
					source = AugmentedSource.makeAugmentedSource(source);
					((AugmentedSource)source).addFilter (ff);
				}
			}
            return source;
        }


		private void augmentParseOptions(JParseOptions options)
		{
			if (validation != SchemaValidationMode.None)
			{
				
				if (validation == SchemaValidationMode.Strict)
				{
					options.setSchemaValidationMode(JValidation.STRICT);
				}
				else if (validation == SchemaValidationMode.Lax)
				{
					options.setSchemaValidationMode(JValidation.LAX);
				}
				else if (validation == SchemaValidationMode.None)
				{
					options.setSchemaValidationMode(JValidation.STRIP);
				}
				else if (validation == SchemaValidationMode.Preserve)
				{
					options.setSchemaValidationMode(JValidation.PRESERVE);
				}
			}
			if (topLevelElement != null)
			{
				
				options.setTopLevelElement(
					new FingerprintedQName(
						topLevelElement.Prefix, topLevelElement.Uri.ToString(), topLevelElement.LocalName).getStructuredQName());
			}

			if (whitespacePolicy != null)
			{
                int option = whitespacePolicy.ordinal();
				if (option == JWhitespace.XSLT)
				{
					options.setStripSpace(JWhitespace.NONE);
                    options.addFilter(whitespacePolicy.makeStripper());
				}
				else
				{
					options.setStripSpace(option);
				}
			}
			if (treeModel != TreeModel.Unspecified)
			{
				
				if (treeModel == TreeModel.TinyTree)
				{
					options.setModel(net.sf.saxon.om.TreeModel.TINY_TREE);
				}
				else if (treeModel == TreeModel.TinyTreeCondensed)
				{
					options.setModel(net.sf.saxon.om.TreeModel.TINY_TREE_CONDENSED);
				}
				else
				{
					options.setModel(net.sf.saxon.om.TreeModel.LINKED_TREE);
				}
			}
			if (lineNumbering)
			{

				options.setLineNumbering(true);
			}
			if (dtdValidation)
			{
				
				options.setDTDValidationMode(JValidation.STRICT);
			}
			if (projectionQuery != null) {
				net.sf.saxon.query.XQueryExpression exp = projectionQuery.getUnderlyingCompiledQuery();
				net.sf.saxon.@event.FilterFactory ff = config.makeDocumentProjector(exp);
				if (ff != null) {
					
					options.addFilter (ff);
				}
			}

		}

        /// <summary>
        /// Load an XML document, delivered using an XmlReader.
        /// </summary>
        /// <remarks>
        /// <para>The XmlReader is responsible for parsing the document; this method builds a tree
        /// representation of the document (in an internal Saxon format) and returns its document node.
        /// The XmlReader is not required to perform validation but it must expand any entity references.
        /// Saxon uses the properties of the <c>XmlReader</c> as supplied.</para>
        /// <para>Use of a plain <c>XmlTextReader</c> is discouraged, because it does not expand entity
        /// references. This should only be used if you know in advance that the document will contain
        /// no entity references (or perhaps if your query or stylesheet is not interested in the content
        /// of text and attribute nodes). Instead, with .NET 1.1 use an <c>XmlValidatingReader</c> (with <c>ValidationType</c>
        /// set to <c>None</c>). The constructor for <c>XmlValidatingReader</c> is obsolete in .NET 2.0,
        /// but the same effect can be achieved by using the <c>Create</c> method of <c>XmlReader</c> with
        /// appropriate <c>XmlReaderSettings</c></para>
        /// <para>Conformance with the W3C specifications requires that the <c>Normalization</c> property
        /// of an <c>XmlTextReader</c> should be set to <c>true</c>. However, Saxon does not insist
        /// on this.</para>
        /// <para>If the <c>XmlReader</c> performs schema validation, Saxon will ignore any resulting type
        /// information. Type information can only be obtained by using Saxon's own schema validator, which
        /// will be run if the <c>SchemaValidationMode</c> property is set to <c>Strict</c> or <c>Lax</c></para>
        /// <para>Note that the Microsoft <c>System.Xml</c> parser does not report whether attributes are
        /// defined in the DTD as being of type <c>ID</c> and <c>IDREF</c>. This is true whether or not
        /// DTD-based validation is enabled. This means that such attributes are not accessible to the 
        /// <c>id()</c> and <c>idref()</c> functions.</para>
        /// <para>Note that setting the <c>XmlResolver</c> property of the <c>DocumentBuilder</c>
        /// has no effect when this method is used; if an <c>XmlResolver</c> is required, it must
        /// be set on the <c>XmlReader</c> itself.</para>
        /// </remarks>
        /// <param name="reader">The XMLReader that supplies the parsed XML source</param>
        /// <returns>An <c>XdmNode</c>, the document node at the root of the tree of the resulting
        /// in-memory document
        /// </returns>

        public XdmNode Build(XmlReader reader)
        {
            JPullProvider pp = new JDotNetPullProvider(reader);
            pp.setPipelineConfiguration(config.makePipelineConfiguration());
            // pp = new PullTracer(pp);  /* diagnostics */
            Source source = new JPullSource(pp);
            source.setSystemId(reader.BaseURI);
			JParseOptions options = new JParseOptions(config.getParseOptions());

            augmentParseOptions(options);
			JNodeInfo doc = config.buildDocument(source, options);
            return (XdmNode)XdmValue.Wrap(doc);
        }

        /// <summary>
        /// Load an XML DOM document, supplied as an <c>XmlNode</c>, into a Saxon XdmNode.
        /// </summary>
        /// <remarks>
        /// <para>
        /// The returned document will contain only the subtree rooted at the supplied node.
        /// </para>
        /// <para>
        /// This method copies the DOM tree to create a Saxon tree. See the <c>Wrap</c> method for
        /// an alternative that creates a wrapper around the DOM tree, allowing it to be modified in situ.
        /// </para>
        /// </remarks>
        /// <param name="source">The DOM Node to be copied to form a Saxon tree</param>
        /// <returns>An <c>XdmNode</c>, the document node at the root of the tree of the resulting
        /// in-memory document
        /// </returns>

        public XdmNode Build(XmlNode source)
        {
            return Build(new XmlNodeReader(source));
        }

        public XdmNode Build(XdmNode source)
        {
			return (XdmNode)XdmNode.Wrap(config.buildDocument(source.Implementation));
        }

        /// <summary>
        /// Wrap an XML DOM document, supplied as an <c>XmlNode</c>, as a Saxon XdmNode.
        /// </summary>
        /// <remarks>
        /// <para>
        /// This method must be applied at the level of the Document Node. Unlike the
        /// <c>Build</c> method, the original DOM is not copied. This saves memory and
        /// time, but it also means that it is not possible to perform operations such as
        /// whitespace stripping and schema validation.
        /// </para>
        /// </remarks>
        /// <param name="doc">The DOM document node to be wrapped</param>
        /// <returns>An <c>XdmNode</c>, the Saxon document node at the root of the tree of the resulting
        /// in-memory document
        /// </returns>

        public XdmNode Wrap(XmlDocument doc)
        {
            String baseu = (baseUri == null ? null : baseUri.ToString());
            JDotNetDocumentWrapper wrapper = new JDotNetDocumentWrapper(doc, baseu, config);
			return (XdmNode)XdmValue.Wrap(wrapper.getRootNode());
        }


    }
    
    /// <summary>The default Logger used by Saxon on the .NET platform. All messages are written by
    /// default to System.err. The logger can be configured by setting a different output
    /// destination, and by setting a minimum threshold for the severity of messages to be output.</summary>

    public class StandardLogger : JLogger {
        
       private TextWriter outi = Console.Error;
       JDotNetWriter writer;
		int threshold = JLogger.INFO;
		java.io.PrintStream stream;

       public StandardLogger() {
           writer = new JDotNetWriter(outi);
       }

       public StandardLogger(TextWriter w) {
       	writer = new JDotNetWriter(w);

       }

		public JDotNetWriter UnderlyingTextWriter {
			set {
				writer = value;
			}
			get {
				return writer;
			}


		}


	/// <summary> Set the minimum threshold for the severity of messages to be output. Defaults to
    /// <see cref="net.sf.saxon.lib.Logger.INFO"/>. Messages whose severity is below this threshold will be ignored </summary>
    /// <param> threshold the minimum severity of messages to be output. </param>
     
		public int Threshold {

			set { 
				threshold = value;
			}

			get { 
				return threshold;
			}

		}

        public override StreamResult asStreamResult()
        {
            
			return new StreamResult(writer);
        }

        public override void println(string str, int severity)
        {
			if (severity >= threshold) {
				writer.write (str);
			}
        }
    }


    /// <summary>
    /// Enumeration identifying the various Schema validation modes
    /// </summary>

    public enum SchemaValidationMode
    {
        /// <summary>No validation (or strip validation, which removes existing type annotations)</summary> 
        None,
        /// <summary>Strict validation</summary>
        Strict,
        /// <summary>Lax validation</summary>
        Lax,
        /// <summary>Validation mode preserve, which preserves any existing type annotations</summary>
        Preserve,
        /// <summary>Unspecified validation: this means that validation is defined elsewhere, for example in the
        /// Saxon Configuration</summary>
        Unspecified
    }


    /// <summary>
    ///  WhitespacePolicy is a class defining the possible policies for handling
    /// whitespace text nodes in a source document.
    /// Please note that since Saxon 9.7.0.8 this class has been refactored from the enumeration
    /// type with the same name and therefore will work as before.
    /// </summary>
    [Serializable]
    public class WhitespacePolicy
    {
       private int policy;
       JSpaceStrippingRule stripperRules;

        /// <summary>All whitespace text nodes are stripped</summary>
        public static WhitespacePolicy StripAll = new WhitespacePolicy(JWhitespace.NONE);

        /// <summary>Whitespace text nodes appearing in element-only content are stripped</summary>
        public static WhitespacePolicy StripIgnorable = new WhitespacePolicy(JWhitespace.IGNORABLE);

        /// <summary>No whitespace is stripped</summary>
        public static WhitespacePolicy PreserveAll = new WhitespacePolicy(JWhitespace.ALL);

        /// <summary>Unspecified means that no other value has been specifically requested</summary>
        public static WhitespacePolicy Unspecified = new WhitespacePolicy(JWhitespace.UNSPECIFIED);

        private WhitespacePolicy(int policy)
        {
            this.policy = policy;
        }

        internal WhitespacePolicy(JPreparedStylesheet executable)
        {
            policy = Whitespace.XSLT;
            stripperRules = executable.getTopLevelPackage().getStripperRules();
        }

        public static WhitespacePolicy makeCustomPolicy(Predicate<QName> elementTest) {
            JSpaceStrippingRule rule = new SpaceStrippingRule(elementTest);

            WhitespacePolicy wsp = new WhitespacePolicy(JWhitespace.XSLT);
            wsp.stripperRules = rule;
            return wsp;
        }

        internal int ordinal() {
            return policy;
        }

        internal net.sf.saxon.@event.FilterFactory makeStripper() {
            return new FilterFactory(stripperRules);
        }

       

    }

    public interface Predicate<T> {
        bool matches(T value);
    }

    internal class SpaceStrippingRule : JSpaceStrippingRule
    {
        private Predicate<QName> elementTest;

        public SpaceStrippingRule(Predicate<QName> elementTest)
        {
            this.elementTest = elementTest;
        }

        public void export(ExpressionPresenter ep)
        {
            throw new NotImplementedException(); 
        }

        public byte isSpacePreserving(NodeName nn)
        {
            return elementTest.matches(new QName(nn.getStructuredQName().ToString())) ? 
                net.sf.saxon.@event.Stripper.ALWAYS_STRIP :
                net.sf.saxon.@event.Stripper.ALWAYS_PRESERVE;
        }

        public int isSpacePreserving(NodeName nn, SchemaType st)
        {
            throw new NotImplementedException();
        }

        public JProxyReceiver makeStripper(JReceiver r)
        {
            throw new NotImplementedException();
        }
    }

    internal class FilterFactory : JFilterFactory
    {
        JSpaceStrippingRule stripperRules;
        public FilterFactory(JSpaceStrippingRule sr)
        {
            stripperRules = sr;
        }
        public JProxyReceiver makeFilter(JReceiver r)
        {
            return new net.sf.saxon.@event.Stripper(stripperRules, r);
        }
    }

    /// <summary>
    /// Enumeration identifying the different tree model implementations
    /// </summary>
    /// 
    public enum TreeModel
    {
        /// <summary>
        /// Saxon TinyTree. This is the default model and is suitable for most purposes.
        /// </summary>
        TinyTree,
        /// <summary>
        /// Saxon Condensed TinyTree. This is a variant of the TinyTree that shares storage for 
        /// duplicated text and attribute nodes. It gives a further saving in space occupied, at the cost
        /// of some increase in the time taken for tree construction.
        /// </summary>
        TinyTreeCondensed,
        /// <summary>
        /// Saxon LinkedTree. This tree model is primarily useful when using XQuery Update, since it is the
        /// only standard tree model to support updates.
        /// </summary>
        LinkedTree,
        /// <summary>
        /// Unspecified tree model. This value is used to indicate that there is no preference for any specific
        /// tree model, which allows the choice to fall back to other interfaces.
        /// </summary>
        Unspecified
    }

    internal class DotNetObjectModelDefinition : JDotNetObjectModel
    {

        public override bool isXdmValue(object obj)
        {
            return obj is XdmValue;
        }

        public override bool isXdmAtomicValueType(System.Type type)
        {
            return typeof(XdmAtomicValue).IsAssignableFrom(type);
        }

        public override bool isXdmValueType(System.Type type)
        {
            return typeof(XdmValue).IsAssignableFrom(type);
        }

        public override JSequence unwrapXdmValue(object obj)
        {
            return ((XdmValue)obj).Unwrap();
        }

        public override object wrapAsXdmValue(JSequence value)
        {
            return XdmValue.Wrap(value);
        }

        public override bool isXmlNodeType(System.Type type)
        {
            return typeof(System.Xml.XmlNode).IsAssignableFrom(type);
        }

    }

}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2016 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////