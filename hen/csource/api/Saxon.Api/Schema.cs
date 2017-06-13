using System;
using System.IO;
using System.Xml;
using System.Collections;
using System.Collections.Generic;
using javax.xml.transform;
using javax.xml.transform.stream;
using JAugmentedSource = net.sf.saxon.lib.AugmentedSource;
using JConfiguration = net.sf.saxon.Configuration;
using JController = net.sf.saxon.Controller;
using JFeatureKeys = net.sf.saxon.lib.FeatureKeys;
using JSchemaURIResolver = net.sf.saxon.lib.SchemaURIResolver;
using JReceiver = net.sf.saxon.@event.Receiver;
using JAtomicType = net.sf.saxon.type.AtomicType;
using JSchemaType = net.sf.saxon.type.SchemaType;
using JBuiltInAtomicType = net.sf.saxon.type.BuiltInAtomicType;
using JValidation = net.sf.saxon.lib.Validation;
using JParseOptions = net.sf.saxon.lib.ParseOptions;
using JInvalidityHandler = net.sf.saxon.lib.InvalidityHandler;
using JStandardInvalidityHandler = net.sf.saxon.lib.StandardInvalidityHandler;
using JInvalidityReportGenerator = net.sf.saxon.lib.InvalidityReportGenerator;
using JNodeInfo = net.sf.saxon.om.NodeInfo;
using JPullSource = net.sf.saxon.pull.PullSource;
using JPullProvider = net.sf.saxon.pull.PullProvider;
using JPipelineConfiguration = net.sf.saxon.@event.PipelineConfiguration;
using JSink = net.sf.saxon.@event.Sink;
using JSender = net.sf.saxon.@event.Sender;
using JDotNetInputStream = net.sf.saxon.dotnet.DotNetInputStream;
using JDotNetPullProvider = net.sf.saxon.dotnet.DotNetPullProvider;
using JDotNetReader = net.sf.saxon.dotnet.DotNetReader;
using JSchemaException = net.sf.saxon.type.SchemaException;


namespace Saxon.Api
{

    /// <summary>
    /// A <c>SchemaManager</c> is responsible for compiling schemas and
    /// maintaining a cache of compiled schemas that can be used for validating
    /// instance documents.
    /// </summary>
    /// <remarks>
    /// <para>To obtain a <c>SchemaManager</c>, use the 
    /// <c>SchemaManager</c> property of the <c>Processor</c> object.</para>
    /// <para>In a schema-aware Processor there is exactly one
    /// <c>SchemaManager</c> (in a non-schema-aware Processor there is none).</para>
    /// <para>The cache of compiled schema definitions can include only one schema
    /// component (for example a type, or an element declaration) with any given name.
    /// An attempt to compile two different schemas in the same namespace will usually
    /// therefore fail.</para>
    /// <para>As soon as a type definition or element declaration is used for the first
    /// time in a validation episode, it is marked as being "sealed": this prevents subsequent
    /// modifications to the component. Examples of modifications that are thereby disallowed
    /// include adding to the substitution group of an existing element declaration, adding subtypes
    /// to an existing type, or redefining components using &lt;xs:redefine&gt;</para>
    /// </remarks>

    [Serializable]
    public class SchemaManager
    {

        private JConfiguration config;
        private IList errorList = null;
        private net.sf.saxon.s9api.SchemaManager schemaManager;
		private Processor processor;

        // internal constructor: the public interface is a factory method
        // on the Processor object

        internal SchemaManager(net.sf.saxon.Configuration config)
        {
            this.config = (JConfiguration)config;
        }

        internal SchemaManager(Processor processor)
        {
			this.processor = processor;
			this.schemaManager = processor.JProcessor.getSchemaManager();
			this.config = (JConfiguration)processor.Implementation;
        }

        /// <summary>
        /// The version of the W3C XML Schema Specification handled by this SchemaManager
        /// </summary>
        /// <remarks>
        /// <para>The value must be "1.0" (indicating XML Schema 1.0) or "1.1" (indicating XML Schema 1.1.
        /// The default is "1.0". New constructs defined in XSD 1.1 are rejected unless this property
        /// is set to "1.1" before compiling the schema.
        /// </para>
        /// </remarks>
        /// 
        public String XsdVersion {
            get {
				return schemaManager.getXsdVersion();
            }
            set {
				schemaManager.setXsdVersion (value);
            }
        }


        /// <summary>
        /// This property provides a way to set the catalog file which will be used by the Apache catalog resolver.
        /// </summary>
        /// <para>The value of the xml.catalog.files</para>
        public String Catalog
        {
            set {
                net.sf.saxon.trans.XmlCatalogResolver.setCatalog(value, this.config, false);
            }
        }


        /// <summary>
        /// The SchemaResolver is a user-supplied class used for resolving references to
        /// schema documents. It applies to references from one schema document to another
        /// appearing in <c>xs:import</c>, <c>xs:include</c>, and <c>xs:redefine</c>; to
        /// references from an instance document to a schema in <c>xsi:schemaLocation</c> and
        /// <c>xsi:noNamespaceSchemaLocation</c>, to <c>xsl:import-schema</c> in XSLT, and to
        /// the <c>import schema</c> declaration in XQuery.
        /// </summary>

        public SchemaResolver SchemaResolver
        {
            get
            {
                JSchemaURIResolver r = schemaManager.getSchemaURIResolver();
                if (r is DotNetSchemaURIResolver)
                {
                    return ((DotNetSchemaURIResolver)r).resolver;
                }
                else
                {
                    return null;
                }
            }
            set
            {
                schemaManager.setSchemaURIResolver(new DotNetSchemaURIResolver(value));
            }
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
        /// <para>Note that this error list is used only for errors detected during the compilation
        /// of the schema. It is not used for errors detected when using the schema to validate
        /// a source document.</para>
        /// </remarks>

        public IList ErrorList
        {
            set
            {
                errorList = value;
                schemaManager.setErrorListener(new ErrorGatherer(errorList));
            }
            get
            {
                return errorList;
            }
        }




        /// <summary>
        /// Compile a schema supplied as a Stream. The resulting schema components are added
        /// to the cache.
        /// </summary>
        /// <param name="input">A stream containing the source text of the schema. This method
        /// will consume the supplied stream. It is the caller's responsibility to close the stream
        /// after use.</param>
        /// <param name="baseUri">The base URI of the schema document, for resolving any references to other
        /// schema documents</param>        

        public void Compile(Stream input, Uri baseUri)
        {
            StreamSource ss = new StreamSource(new JDotNetInputStream(input));
            ss.setSystemId(baseUri.ToString());
			schemaManager.load (ss);
        }

        /// <summary>
        /// Compile a schema, retrieving the source using a URI. The resulting schema components are added
        /// to the cache.
        /// </summary>
        /// <remarks>
        /// The document located via the URI is parsed using the <c>System.Xml</c> parser.
        /// </remarks>
        /// <param name="uri">The URI identifying the location where the schema document can be
        /// found</param>

        public void Compile(Uri uri)
        {
            StreamSource ss = new StreamSource(uri.ToString());
            JAugmentedSource aug = JAugmentedSource.makeAugmentedSource(ss);
            aug.setPleaseCloseAfterUse(true);
			schemaManager.load (aug);
        }

        /// <summary>
        /// Compile a schema, delivered using an XmlReader. The resulting schema components are added
        /// to the cache.
        /// </summary>
        /// <remarks>
        /// The <c>XmlReader</c> is responsible for parsing the document; this method builds a tree
        /// representation of the document (in an internal Saxon format) and compiles it.
        /// The <c>XmlReader</c> is used as supplied; it is the caller's responsibility to ensure that
        /// its settings are appropriate for parsing a schema document (for example, that entity references
        /// are expanded and whitespace is retained.)
        /// </remarks>
        /// <param name="reader">The XmlReader (that is, the XML parser) used to supply the source schema document</param>

        public void Compile(XmlReader reader)
        {
            JPullProvider pp = new JDotNetPullProvider(reader);
            pp.setPipelineConfiguration(config.makePipelineConfiguration());
            // pp = new PullTracer(pp);  /* diagnostics */
            JPullSource ss = new JPullSource(pp);
            ss.setSystemId(reader.BaseURI);
			schemaManager.load (ss);
        }

        /// <summary>
        /// Compile a schema document, located at an XdmNode. This may be a document node whose
        /// child is an <c>xs:schema</c> element, or it may be
        /// the <c>xs:schema</c> element itself. The resulting schema components are added
        /// to the cache.
        /// </summary>
        /// <param name="node">The document node or the outermost element node of a schema document.</param>

        public void Compile(XdmNode node)
        {
            ErrorGatherer eg = null;
            if (errorList != null)
            {
                eg = new ErrorGatherer(errorList);
            }
            try
            {
                config.readInlineSchema((JNodeInfo)node.value, null, eg);
            }
            catch (JSchemaException e)
            {
                throw new StaticError(e);
            }
        }

        /// <summary>
        /// Create a new <c>SchemaValidator</c>, which may be used for validating instance
        /// documents.
        /// </summary>
        /// <remarks>
        /// <para>The <c>SchemaValidator</c> uses the cache of schema components held by the
        /// <c>SchemaManager</c>. It may also add new components to this cache (for example,
        /// when the instance document references a schema using <c>xsi:schemaLocation</c>).
        /// It is also affected by changes to the schema cache that occur after the 
        /// <c>SchemaValidator</c> is created.</para>
        /// <para>When schema components are used for validating instance documents (or for compiling
        /// schema-aware queries and stylesheets) they are <i>sealed</i> to prevent subsequent modification.
        /// The modifications disallowed once a component is sealed include adding to the substitution group
        /// of an element declaration, adding subtypes derived by extension to an existing complex type, and
        /// use of <c>&lt;xs:redefine&gt;</c></para>
        /// </remarks>

        public SchemaValidator NewSchemaValidator()
        {
			return new SchemaValidator(this.processor);
        }

        /// <summary>
        /// Factory method to get an <c>AtomicType</c> object representing the atomic type with a given QName.
        /// </summary>
        /// <remarks>
        /// It is undefined whether two calls on this method supplying the same QName will return the same
        /// <c>XdmAtomicType</c> object instance.
        /// </remarks>
        /// <param name="qname">The QName of the required type</param>
        /// <returns>An <c>AtomicType</c> object representing this type if it is present in this schema (and is an
        /// atomic type); otherwise, null. </returns>

        public XdmAtomicType GetAtomicType(QName qname)
        {
			JSchemaType type = config.getSchemaType(qname.ToStructuredQName());
            if (type is JBuiltInAtomicType)
            {
                return XdmAtomicType.BuiltInAtomicType(qname);
            }
            else if (type is JAtomicType)
            {
                return new XdmAtomicType((JAtomicType)type);
            }
            else
            {
                return null;
            }
        }

    }

    /// <summary>
    /// A <c>SchemaValidator</c> is an object that is used for validating instance documents
    /// against a schema. The schema consists of the collection of schema components that are
    /// available within the schema cache maintained by the <c>SchemaManager</c>, together with
    /// any additional schema components located during the course of validation by means of an
    /// <c>xsl:schemaLocation</c> or <c>xsi:noNamespaceSchemaLocation</c> attribute within the
    /// instance document.
    /// </summary>
    /// <remarks>
    /// If validation fails, an exception is thrown. If validation succeeds, the validated
    /// document can optionally be written to a specified destination. This will be a copy of
    /// the original document, augmented with default values for absent elements and attributes,
    /// and carrying type annotations derived from the schema processing. Saxon does not deliver
    /// the full PSVI as described in the XML schema specifications, only the subset of the
    /// PSVI properties featured in the XDM data model.
    /// </remarks>    

    [Serializable]
    public class SchemaValidator
    {

        private JConfiguration config;
        private bool lax = false;
        private Source source;
		private List<Source> sources = new List<Source>();
        private XmlDestination destination;
        private IList errorList = null;
        private bool useXsiSchemaLocation;
		private Processor processor = null;
		private JInvalidityHandler invalidityHandler;

        // internal constructor

        internal SchemaValidator(Processor processor)
        {
			this.config = processor.Implementation;
            Object obj = config.getConfigurationProperty(JFeatureKeys.USE_XSI_SCHEMA_LOCATION);
            useXsiSchemaLocation = ((java.lang.Boolean)obj).booleanValue();
			invalidityHandler = new net.sf.saxon.lib.StandardInvalidityHandler (processor.Implementation);
        }

        /// <summary>
        /// The validation mode may be either strict or lax. The default is strict;
        /// this property is set to indicate that lax validation is required. With strict validation,
        /// validation fails if no element declaration can be located for the outermost element. With lax
        /// validation, the absence of an element declaration results in the content being considered valid.
        /// </summary>

        public bool IsLax
        {
            get { return lax; }
            set { lax = value; }
        }

        /// <summary>
        /// This property defines whether the schema processor will recognize, and attempt to
        /// dereference, any <c>xsi:schemaLocation</c> and <c>xsi:noNamespaceSchemaLocation</c>
        /// attributes encountered in the instance document. The default value is true.
        /// </summary>

        public Boolean UseXsiSchemaLocation
        {
            get
            {
                return useXsiSchemaLocation;
            }
            set
            {
                useXsiSchemaLocation = value;
            }
        }


		/// <summary>Setup Validation Reporting feature which saves the validation errors in an XML file</summary>
		/// <param name="destination"> destination where XML will be sent</param>
     
		public void SetValidityReporting(XmlDestination destination) {
            JInvalidityReportGenerator reporter = processor.Implementation.createValidityReporter();
            reporter.setReceiver(destination.GetReceiver(processor.Implementation.makePipelineConfiguration()));

            this.invalidityHandler = reporter;
		}


		
		/// <summary>Set the InvalidityHandler to be used when validating instance documents</summary>
		/// <param name="inHandler">handler the InvalidityHandler to be used</param>
		public void SetInvalidityHandler(IInvalidityHandler inHandler){
			this.invalidityHandler = new InvalidityHandlerWrapper(inHandler);
		}

        /// <summary>
        /// Add an instance document to the list of documents to be validated.
        /// </summary>
        /// <param name="source">Stream source of the document</param>
        /// <param name="baseUri">Base Uri of the source document</param>
		public void AddSource(Stream source, Uri baseUri){
			StreamSource ss = new StreamSource(new JDotNetInputStream(source));
			ss.setSystemId(baseUri.ToString());
			sources.Add (ss);
		}

        /// <summary>
        /// Add an instance document to the list of documents to be validated
        /// </summary>
        /// <param name="uri">Uri of the source document</param>
		public void AddSource(Uri uri){
			StreamSource ss = new StreamSource(uri.ToString());
			JAugmentedSource aug = JAugmentedSource.makeAugmentedSource(ss);
			aug.setPleaseCloseAfterUse(true);
			sources.Add (aug);
		}

        /// <summary>
        /// Add an instance document to the list of documents to be validated
        /// </summary>
        /// <param name="reader">Source document added a a XmlReader</param>
		public void AddSource(XmlReader reader){
			JPullProvider pp = new JDotNetPullProvider(reader);
			JPipelineConfiguration pipe = config.makePipelineConfiguration();
			pipe.setUseXsiSchemaLocation(useXsiSchemaLocation);
			pp.setPipelineConfiguration(pipe);
			// pp = new PullTracer(pp);  /* diagnostics */
			JPullSource psource = new JPullSource(pp);
			psource.setSystemId(reader.BaseURI);
			sources.Add (psource);
		}

        /// <summary>
        /// Add an instance document to the list of documents to be validated.
        /// </summary>
        /// <param name="source">supplied as a XdmNode value</param>
		public void AddSource(XdmNode source){
			sources.Add((JNodeInfo)source.value);
		}

        /// <summary>
        /// Supply the instance document to be validated in the form of a Stream
        /// </summary>
        /// <param name="source">A stream containing the XML document to be parsed
        /// and validated. This stream will be consumed by the validation process,
        /// but it will not be closed after use: that is the responsibility of the
        /// caller.</param>
        /// <param name="baseUri">The base URI to be used for resolving any relative
        /// references, for example a reference to an <c>xsi:schemaLocation</c></param>                  

        public void SetSource(Stream source, Uri baseUri)
        {
            StreamSource ss = new StreamSource(new JDotNetInputStream(source));
            ss.setSystemId(baseUri.ToString());
            this.source = ss;
			sources.Clear ();
        }

        /// <summary>
        /// Supply the instance document to be validated in the form of a Uri reference
        /// </summary>
        /// <param name="uri">URI of the document to be validated</param>                  

        public void SetSource(Uri uri)
        {
            StreamSource ss = new StreamSource(uri.ToString());
            JAugmentedSource aug = JAugmentedSource.makeAugmentedSource(ss);
            aug.setPleaseCloseAfterUse(true);
            this.source = aug;
			sources.Clear ();
        }

        /// <summary>
        /// Supply the instance document to be validated, in the form of an XmlReader.
        /// </summary>
        /// <remarks>
        /// The XmlReader is responsible for parsing the document; this method validates it.
        /// </remarks>
        /// <param name="reader">The <c>XmlReader</c> used to read and parse the instance
        /// document being validated. This is used as supplied. For conformance, use of a
        /// plain <c>XmlTextReader</c> is discouraged, because it does not expand entity
        /// references. This may cause validation failures.
        /// </param>

        public void SetSource(XmlReader reader)
        {
            JPullProvider pp = new JDotNetPullProvider(reader);
            JPipelineConfiguration pipe = config.makePipelineConfiguration();
            pipe.setUseXsiSchemaLocation(useXsiSchemaLocation);
            pp.setPipelineConfiguration(pipe);
            // pp = new PullTracer(pp);  /* diagnostics */
            JPullSource psource = new JPullSource(pp);
            psource.setSystemId(reader.BaseURI);
            this.source = psource;
			sources.Clear ();
        }

        /// <summary>
        /// Supply the instance document to be validated in the form of an XdmNode
        /// </summary>
        /// <remarks>
        /// <para>The supplied node must be either a document node or an element node.
        /// If an element node is supplied, then the subtree rooted at this element is
        /// validated as if it were a complete document: that is, it must not only conform
        /// to the structure required of that element, but any referential constraints
        /// (keyref, IDREF) must be satisfied within that subtree.
        /// </para>
        /// </remarks>
        /// <param name="source">The document or element node at the root of the tree
        /// to be validated</param>        

        public void SetSource(XdmNode source)
        {
            this.source = (JNodeInfo)source.value;
			sources.Clear ();
        }

        /// <summary>
        /// Supply the destination to hold the validated document. If no destination
        /// is supplied, the validated document is discarded.
        /// </summary>
        /// <remarks>
        /// The destination differs from the source in that (a) default values of missing
        /// elements and attributes are supplied, and (b) the typed values of elements and
        /// attributes are available. However, typed values can only be accessed if the result
        /// is represented using the XDM data model, that is, if the destination is supplied
        /// as an XdmDestination.
        /// </remarks>
        /// <param name="destination">
        /// The destination to hold the validated document.
        /// </param>

        public void SetDestination(XmlDestination destination)
        {
            this.destination = destination;
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
        /// Run the validation of the supplied source document, optionally
        /// writing the validated document to the supplied destination.
        /// </summary>

        public void Run()
        {
            JAugmentedSource aug = JAugmentedSource.makeAugmentedSource(source);
            aug.setSchemaValidationMode(lax ? JValidation.LAX : JValidation.STRICT);
            JReceiver receiver;
            JPipelineConfiguration pipe = config.makePipelineConfiguration();
            if (destination == null)
            {               
                receiver = new JSink(pipe);
            }
            else if (destination is Serializer)
            {
                receiver = ((Serializer)destination).GetReceiver(config);
            }
            else
            {
                Result result = destination.GetReceiver(pipe);
                if (result is JReceiver)
                {
					receiver = (JReceiver)result;
                }
                else
                {
                    throw new ArgumentException("Unknown type of destination");
                }
            }
            pipe.setUseXsiSchemaLocation(useXsiSchemaLocation);
            receiver.setPipelineConfiguration(pipe);
			JParseOptions parseOptions = null;
			if (errorList != null) {
				invalidityHandler = new net.sf.saxon.lib.InvalidityHandlerWrappingErrorListener (new ErrorGatherer (errorList));
			}
			parseOptions = new JParseOptions ();
            parseOptions.setInvalidityHandler (invalidityHandler);
				
            JSender.send(aug, receiver, parseOptions);
        }

    }


    /// <summary>
    /// The SchemaResolver is a user-supplied class used for resolving references to
    /// schema documents. It applies to references from one schema document to another
    /// appearing in <c>xs:import</c>, <c>xs:include</c>, and <c>xs:redefine</c>; to
    /// references from an instance document to a schema in <c>xsi:schemaLocation</c> and
    /// <c>xsi:noNamespaceSchemaLocation</c>, to <c>xsl:import-schema</c> in XSLT, and to
    /// the <c>import schema</c> declaration in XQuery.
    /// </summary>


    public interface SchemaResolver
    {

        /// <summary>
        /// Given a targetNamespace and a set of location hints, return a set of schema documents.
        /// </summary>
        /// <param name="targetNamespace">The target namespace of the required schema components</param>
        /// <param name="baseUri">The base URI of the module containing the reference to a schema document
        /// declaration</param>
        /// <param name="locationHints">The sequence of URIs (if any) listed as location hints.
        /// In most cases there will only be one; but the <c>import schema</c> declaration in
        /// XQuery permits several.</param>
        /// <returns>A set of absolute Uris identifying the query modules to be loaded. There is no requirement
        /// that these correspond one-to-one with the URIs defined in the <c>locationHints</c>. The 
        /// returned URIs will be dereferenced by calling the <c>GetEntity</c> method.
        /// </returns>

        /**public**/ Uri[] GetSchemaDocuments(String targetNamespace, Uri baseUri, String[] locationHints);

        /// <summary>
        /// Dereference a URI returned by <c>GetModules</c> to retrieve a <c>Stream</c> containing
        /// the actual XML schema document.
        /// </summary>
        /// <param name="absoluteUri">A URI returned by the <code>GetSchemaDocuments</code> method.</param>
        /// <returns>Either a <c>Stream</c> or a <c>String</c> containing the query text. 
        /// The supplied URI will be used as the base URI of the query module.</returns>

        /**public**/ Object GetEntity(Uri absoluteUri);

    }



    /// <summary>
	/// internal class that wraps a (.NET) QueryResolver to create a (Java) SchemaURIResolver
    /// </summary>

    internal class DotNetSchemaURIResolver : net.sf.saxon.lib.SchemaURIResolver
    {

        internal SchemaResolver resolver;
        internal JConfiguration config;

		/// <summary>
		/// Initializes a new instance of the <see cref="Saxon.Api.DotNetSchemaURIResolver"/> class.
		/// </summary>
		/// <param name="resolver">Resolver.</param>
        public DotNetSchemaURIResolver(SchemaResolver resolver)
        {
            this.resolver = resolver;
        }

        public void setConfiguration(JConfiguration config)
        {
            this.config = config;
        }

		/// <summary>
		/// Resolve the specified targetNamespace, baseURI and locations.
		/// </summary>
		/// <param name="targetNamespace">Target namespace.</param>
		/// <param name="baseURI">BaseURI.</param>
		/// <param name="locations">Locations.</param>
        public Source[] resolve(String targetNamespace, String baseURI, String[] locations)
        {
			if (config.isSchemaAvailable(targetNamespace) && !(java.lang.Boolean.valueOf(((java.lang.Object)config.getConfigurationProperty(JFeatureKeys.MULTIPLE_SCHEMA_IMPORTS)).toString()).booleanValue()))
            {
                return new Source[0];
            }
            Uri baseU = (baseURI == null ? null : new Uri(baseURI));
            Uri[] modules = resolver.GetSchemaDocuments(targetNamespace, baseU, locations);
            StreamSource[] ss = new StreamSource[modules.Length];
            for (int i = 0; i < ss.Length; i++)
            {
                ss[i] = new StreamSource();
                ss[i].setSystemId(modules[i].ToString());
                Object doc = resolver.GetEntity(modules[i]);
                if (doc is Stream)
                {
                    ss[i].setInputStream(new JDotNetInputStream((Stream)doc));
                }
                else if (doc is String)
                {
                    ss[i].setReader(new JDotNetReader(new StringReader((String)doc)));
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
// Copyright (c) 2016 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////