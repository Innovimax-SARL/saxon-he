using System;
using System.IO;
using System.Xml;
using System.Collections;
using JConfiguration = net.sf.saxon.Configuration;
using JPipelineConfiguration = net.sf.saxon.@event.PipelineConfiguration;
using JReceiver = net.sf.saxon.@event.Receiver;
using JSink = net.sf.saxon.@event.Sink;
using JProperties = java.util.Properties;
using JOutputStream = java.io.OutputStream;
using JWriter = java.io.Writer;
using JCharSequence = java.lang.CharSequence;
using JFileOutputStream = java.io.FileOutputStream;
using JXPathException = net.sf.saxon.trans.XPathException;
using JResult = javax.xml.transform.Result;
using JStreamResult = javax.xml.transform.stream.StreamResult;
using JCharacterMapIndex = net.sf.saxon.serialize.CharacterMapIndex;
using JBuilder = net.sf.saxon.@event.Builder;
using JTinyBuilder = net.sf.saxon.tree.tiny.TinyBuilder;
using JLinkedTreeBuilder = net.sf.saxon.tree.linked.LinkedTreeBuilder;
using JSchemaType = net.sf.saxon.type.SchemaType;
using JLocation = net.sf.saxon.expr.parser.Location;
using JExplicitLocation = net.sf.saxon.expr.parser.ExplicitLocation;
using JProxyReceiver = net.sf.saxon.@event.ProxyReceiver;
using JNodeName = net.sf.saxon.om.NodeName;
using JItem = net.sf.saxon.om.Item;
using JNodeInfo = net.sf.saxon.om.NodeInfo;
using JDotNetDomBuilder = net.sf.saxon.dotnet.DotNetDomBuilder;
using JDotNetOutputStream = net.sf.saxon.dotnet.DotNetOutputStream;
using JDotNetWriter = net.sf.saxon.dotnet.DotNetWriter;
using JDotNetReceiver = net.sf.saxon.dotnet.DotNetReceiver;
using JComplexContentOutputter = net.sf.saxon.@event.ComplexContentOutputter;
using JNamespaceReducer = net.sf.saxon.@event.NamespaceReducer;


namespace Saxon.Api
{



    /// <summary>
    /// An abstract destination for the results of a query or transformation
    /// </summary>
    /// <remarks>
    /// <para>Note to implementors: To implement a new kind of destination, you need
    /// to supply a method <c>getResult</c> which returns an implementation of
    /// the JAXP <c>Result</c> interface. Optionally, if the destination
    /// performs serialization, you can also implement <c>getOutputProperties</c>,
    /// which returns the properties used for serialization.
    /// </para>
    /// </remarks>


    public abstract class XmlDestination
    {

        /// <summary>
        /// Get a <c>Result</c> to which the XML document can be sent as a series
        /// of events. This method is intended primarily for internal use.
        /// </summary>
        /// <remarks>
        /// The returned value must be an implementation of the JAXP <c>Result</c> interface that is
        /// recognized by Saxon.
        /// </remarks>
        /// <param name="pipe">Configuration information for use by the implementation</param>

        public abstract JReceiver GetReceiver(JPipelineConfiguration pipe);

        /// <summary>
        /// Get a set of <c>Properties</c> representing the parameters to the serializer.
        /// The default implementation returns an empty set of properties.
        /// </summary>

        public virtual JProperties GetOutputProperties()
        {
            return new JProperties();
        }

        /// <summary>
        /// Close the Destination, releasing any resources that need to be released.
        /// </summary>
        /// <remarks>
        /// This method is called by the system on completion of a query or transformation.
        /// Some kinds of Destination may need to close an output stream, others might
        /// not need to do anything. The default implementation does nothing.
        /// </remarks>

        public virtual void Close()
        {
        }


    }

    /// <summary>
    /// A <c>Serializer</c> takes a tree representation of XML and turns
    /// it into lexical XML markup.
    /// </summary>
    /// <remarks>
    /// Note that this is serialization in the sense of the W3C XSLT and XQuery specifications.
    /// Unlike the class <c>System.Xml.Serialization.XmlSerializer</c>, this object does not
    /// serialize arbitrary CLI objects.
    /// </remarks>

    public class Serializer : XmlDestination
    {

        private JProperties props = new JProperties();
		private JCharacterMapIndex characterMap;
		private JProperties defaultOutputProperties = null;
        private JOutputStream outputStream = null;
		private String filename = null;
        private JWriter writer = null;
        private bool mustClose = true;
        private Processor processor = null;
        private JConfiguration config = null; // Beware: this will often be null

        /// <summary>QName identifying the serialization parameter "method". If the method
        /// is a user-defined method, then it is given as a QName in Clark notation, that is
        /// "{uri}local".</summary>

        public static readonly QName METHOD =
            new QName("", "method");

        /// <summary>QName identifying the serialization parameter "byte-order-mark"</summary>

        public static readonly QName BYTE_ORDER_MARK =
            new QName("", "byte-order-mark");

        /// <summary>QName identifying the serialization parameter "cdata-section-elements".
        /// The value of this parameter is given as a space-separated list of expanded QNames in
        /// Clark notation, that is "{uri}local".</summary>

        public static readonly QName CDATA_SECTION_ELEMENTS =
            new QName("", "cdata-section-elements");

        /// <summary>QName identifying the serialization parameter "doctype-public"</summary>

        public static readonly QName DOCTYPE_PUBLIC =
            new QName("", "doctype-public");

        /// <summary>QName identifying the serialization parameter "doctype-system"</summary>

        public static readonly QName DOCTYPE_SYSTEM =
            new QName("", "doctype-system");

        /// <summary>QName identifying the serialization parameter "encoding"</summary>

        public static readonly QName ENCODING =
            new QName("", "encoding");

        /// <summary>QName identifying the serialization parameter "escape-uri-attributes".
        /// The value is the string "yes" or "no".</summary>

        public static readonly QName ESCAPE_URI_ATTRIBUTES =
            new QName("", "escape-uri-attributes");

        /// <summary>QName identifying the serialization parameter "include-content-type".
        /// The value is the string "yes" or "no".</summary>

        public static readonly QName INCLUDE_CONTENT_TYPE =
            new QName("", "include-content-type");

        /// <summary>QName identifying the serialization parameter "indent".
        /// The value is the string "yes" or "no".</summary>

        public static readonly QName INDENT =
            new QName("", "indent");

        /// <summary>QName identifying the serialization parameter "media-type".</summary>

        public static readonly QName MEDIA_TYPE =
            new QName("", "media-type");

        /// <summary>QName identifying the serialization parameter "normalization-form"</summary>

        public static readonly QName NORMALIZATION_FORM =
            new QName("", "normalization-form");

        /// <summary>QName identifying the serialization parameter "omit-xml-declaration".
        /// The value is the string "yes" or "no".</summary>

        public static readonly QName OMIT_XML_DECLARATION =
            new QName("", "omit-xml-declaration");

        /// <summary>QName identifying the serialization parameter "standalone".
        /// The value is the string "yes" or "no" or "omit".</summary>

        public static readonly QName STANDALONE =
            new QName("", "standalone");

        /// <summary>QName identifying the serialization parameter "suppress-indentation"
        /// (introduced in XSLT/XQuery 3.0). Previously available as "saxon:suppress-indentation"
        /// The value is the string "yes" or "no" or "omit".</summary>

        public static readonly QName SUPPRESS_INDENTATION =
            new QName("", "suppress-indentation");

        /// <summary>QName identifying the serialization parameter "undeclare-prefixes".
        /// The value is the string "yes" or "no".</summary>

        public static readonly QName UNDECLARE_PREFIXES =
            new QName("", "undeclare-prefixes");

        /// <summary>QName identifying the serialization parameter "use-character-maps".
        /// This is available only with XSLT. The value of the parameter is a list of expanded QNames
        /// in Clark notation giving the names of character maps defined in the XSLT stylesheet.</summary>

        public static readonly QName USE_CHARACTER_MAPS =
            new QName("", "use-character-maps");

        /// <summary>QName identifying the serialization parameter "version"</summary>

        public static readonly QName VERSION =
            new QName("", "version");

        private static readonly String SAXON = NamespaceConstant.SAXON;


        /// <summary>QName identifying the serialization parameter "saxon:character-representation"</summary>


        public static readonly QName SAXON_CHARACTER_REPRESENTATION =
            new QName(SAXON, "saxon:character-representation");

        /// <summary>QName identifying the serialization parameter "saxon:indent-spaces". The value
        /// is an integer (represented as a string) indicating the amount of indentation required.
        /// If specified, this parameter overrides indent="no".</summary>

        public static readonly QName SAXON_INDENT_SPACES =
            new QName(SAXON, "saxon:indent-spaces");

        /// <summary>QName identifying the serialization parameter "saxon:double-space". The value of this 
        /// parameter is given as a space-separated list of expanded QNames in Clark notation, that is 
        /// "{uri}local"; each QName identifies an element that should be preceded by an extra blank line within
        /// indented output.</summary>

        public static readonly QName SAXON_DOUBLE_SPACE =
            new QName(SAXON, "saxon:double-space");

        /// <summary>QName identifying the serialization parameter "saxon:double-space". The value of this 
        /// parameter is given as a space-separated list of expanded QNames in Clark notation, that is 
        /// "{uri}local"; each QName identifies an element whose content should not be indented even when
        /// indent=yes is specified.</summary>

        public static readonly QName SAXON_SUPPRESS_INDENTATION =
            new QName(SAXON, "saxon:suppress-indentation");

        /// <summary>QName identifying the serialization parameter "saxon:next-in-chain". This
        /// is available only with XSLT, and identifies the URI of a stylesheet that is to be used to
        /// process the results before passing them to their final destination.</summary>

        public static readonly QName NEXT_IN_CHAIN =
            new QName(SAXON, "saxon:next-in-chain");

        /// <summary>QName identifying the serialization parameter "saxon:require-well-formed". The
        /// value is the string "yes" or "no". If set to "yes", the output must be a well-formed
        /// document, or an error will be reported. ("Well-formed" here means that the document node
        /// must have exactly one element child, and no text node children other than whitespace-only
        /// text nodes).</summary>

        public static readonly QName SAXON_REQUIRE_WELL_FORMED =
            new QName(SAXON, "saxon:require-well-formed");



        /// <summary>Create a Serializer</summary>

        public Serializer()
        {
        }

        /// <summary>Set a serialization property</summary>
        /// <remarks>In the case of XSLT, properties set within the serializer override
        /// any properties set in <c>xsl:output</c> declarations in the stylesheet.
        /// Similarly, with XQuery, they override any properties set in the Query
        /// prolog using <c>declare option saxon:output</c>.</remarks>
        /// <example>
        ///   <code>
        ///     Serializer qout = new Serializer();
        ///     qout.SetOutputProperty(Serializer.METHOD, "xml");
        ///     qout.SetOutputProperty(Serializer.INDENT, "yes");
        ///     qout.SetOutputProperty(Serializer.SAXON_INDENT_SPACES, "1");
        ///   </code>
        /// </example> 
        /// <param name="name">The name of the serialization property to be set</param>
        /// <param name="value">The value to be set for the serialization property. May be null
        /// to unset the property (that is, to set it back to the default value).</param>

        public void SetOutputProperty(QName name, String value)
        {
            props.setProperty(name.ClarkName, value);
        }

        /// <summary>
        /// Set default output properties, for use when no explicit properties are set using SetProperty().
        /// The values supplied are typically those specified in the stylesheet or query. In the case of XSLT,
        /// they are the properties associated with unamed <c>xsl:output</c> declarations.
        /// </summary>
        /// <param name="props"></param>
		public void SetDefaultOutputProperties(JProperties props)
		{
			this.defaultOutputProperties = props;
		}

        /// <summary>
        /// Set a character map to be used
        /// </summary>
        /// <param name="charMap">the character map</param>
		public void SetCharacterMap(JCharacterMapIndex charMap)
		{
			this.characterMap = charMap;
		}
			

        /// <summary>Specify the destination of the serialized output, in the
        /// form of a file name</summary>
        /// <param name="filename">The name of the file to receive the serialized output</param>
		/// <exception name="DynamicError">Throws a <c>DynamicError</c> if it is not possible to 
		/// create an output stream to write to this file, for example, if the filename is in a
        /// directory that does not exist.</exception>

        public void SetOutputFile(String filename)
        {
            try
            {
                outputStream = new JFileOutputStream(filename);
				this.filename = filename;
                mustClose = true;
            }
            catch (java.io.IOException err)
            {
                JXPathException e = new JXPathException(err);
                throw new DynamicError(e);
            }
        }

		internal String GetFilename(){
			return filename;
		}

        /// <summary>Specify the destination of the serialized output, in the
        /// form of a <c>Stream</c></summary>
        /// <remarks>Saxon will not close the stream on completion; this is the
        /// caller's responsibility.</remarks>
        /// <param name="stream">The stream to which the output will be written.
        /// This must be a stream that allows writing.</param>

        public void SetOutputStream(Stream stream)
        {
            outputStream = new JDotNetOutputStream(stream);
            mustClose = false;
        }
			
    	// <summary>Get the current output destination.</summary> 
		// <returns>JOutputStream</returns>
		public JOutputStream GetOutputDestination(){
			return outputStream;		
		}


		/// <summary>Set the Processor associated with this Serializer. This will be called automatically if the
		/// serializer is created using one of the <c>Processor.NewSerializer()</c> methods.</summary>
		/// <param name="processor"> processor the associated Processor</param>
        
        public void SetProcessor(Processor processor)
        {
            this.processor = processor;
            this.config = processor.Implementation;
        }

        /// <summary>Specify the destination of the serialized output, in the
        /// form of a <c>TextWriter</c></summary>
        /// <remarks>Note that when writing to a <c>TextWriter</c>, character encoding is
        /// the responsibility of the <c>TextWriter</c>, not the <c>Serializer</c>. This
        /// means that the encoding requested in the output properties is ignored; it also
        /// means that characters that cannot be represented in the target encoding will
        /// use whatever fallback representation the <c>TextWriter</c> defines, rather than
        /// being represented as XML character references.</remarks>
        /// <param name="textWriter">The stream to which the output will be written.
        /// This must be a stream that allows writing. Saxon will not close the
        /// <c>textWriter</c> on completion; this is the caller's responsibility.</param>

        public void SetOutputWriter(TextWriter textWriter)
        {
            writer = new JDotNetWriter(textWriter);
            mustClose = false;
        }


        internal JReceiver GetReceiver(JConfiguration config)
        {
            JPipelineConfiguration pipe = config.makePipelineConfiguration();
            return config.getSerializerFactory().getReceiver(
                    GetResult(pipe),
                    pipe,
				GetOutputProperties(), characterMap);

        }

		public override JReceiver GetReceiver(JPipelineConfiguration pipe){
			return GetReceiver (pipe.getConfiguration());
		}


        /// <summary inherit="yes"/>

        internal JResult GetResult(JPipelineConfiguration pipe)
        {
            if (outputStream != null)
            {
                return new JStreamResult(outputStream);
            }
            else if (writer != null)
            {
                return new JStreamResult(writer);
            }
            else
            {
                return new JStreamResult(new JDotNetWriter(Console.Out));
            }
        }

        /// <summary inherit="yes"/>

        public override JProperties GetOutputProperties()
        {
			JProperties properties = (defaultOutputProperties == null ? new JProperties () : new JProperties (defaultOutputProperties));
			java.util.Enumeration propsEnum =  props.keys();
			while (propsEnum.hasMoreElements()) {
				object obj = propsEnum.nextElement();
				String value = (String)(props.get((String)obj));
				properties.setProperty ((String)obj, value);
			}
			return properties;
        }


        /// <summary>
        /// Serialize an XdmNode to the selected output destination using this serializer
        /// </summary>
        /// <param name="node">The node to be serialized</param>
        /// <remarks>since 9.8</remarks>
        public void SerializeXdmNode(XdmNode node)
        {
            net.sf.saxon.query.QueryResult.serialize(node.Implementation, GetResult(config.makePipelineConfiguration()), GetOutputProperties());
        }


        /// <summary>
        /// Serialize an arbitary XdmValue to the selected output destination using this serializer.
        /// The supplied sequence is first wrapped in a document node according to the rules given in section 2
        /// (Sequence Normalization) of the document nodes is then serialized using the serialization parameters 
        /// defined in this serializer.
        /// </summary>
        /// <param name="value"> The value to be serialized</param>
        /// <remarks>since 9.8</remarks>
        public void SerializeXdmValue(XdmValue value) {
            if (value is XdmNode)
            {
                SerializeXdmNode((XdmNode)value);  
            }
            else {
                net.sf.saxon.query.QueryResult.serializeSequence(value.value.iterate(), config, GetResult(config.makePipelineConfiguration()), GetOutputProperties());
            }

        }

        /// <summary inherit="yes"/>

        public override void Close()
        {
            if (mustClose)
            {
                if (outputStream != null)
                {
                    outputStream.close();
                }
                if (writer != null)
                {
                    writer.close();
                }
            }
        }
    }

    /// <summary>
    /// A <c>DomDestination</c> represents an XmlDocument that is constructed to hold the
    /// output of a query or transformation.
    /// </summary>
    /// <remarks>
    /// No data needs to be supplied to the <c>DomDestination</c> object. The query or transformation
    /// populates an <c>XmlDocument</c>, which may then be retrieved as the value of the <c>XmlDocument</c>
    /// property
    /// </remarks>

    public class DomDestination : XmlDestination
    {

        internal JDotNetDomBuilder builder;

        /// <summary>Construct a <c>DomDestination</c></summary>
        /// <remarks>With this constructor, the system will create a new DOM Document
        /// to act as the destination of the query or transformation results. The document
        /// node of the new document may be retrieved via the XmlDocument property.</remarks>

        public DomDestination()
        {
            builder = new JDotNetDomBuilder();
        }

        /// <summary>Construct a <c>DomDestination</c> based on an existing Document node.</summary>
        /// <remarks>The new data will be added as a child of the supplied node.</remarks>
        /// <param name="attachmentPoint">The document node to which new contents will
        /// be attached. To ensure that the new document is well-formed, this document node
        /// should have no existing children.</param>

        public DomDestination(XmlDocument attachmentPoint)
        {
            builder = new JDotNetDomBuilder();
            builder.setAttachmentPoint(attachmentPoint);
        }

        /// <summary>Construct a <c>DomDestination</c> based on an existing DocumentFragment node.</summary>
        /// <remarks>The new data will be added as a child of the supplied node.</remarks>
        /// <param name="attachmentPoint">The document fragment node to which new contents will
        /// be attached. The new contents will be added after any existing children.</param>

        public DomDestination(XmlDocumentFragment attachmentPoint)
        {
            builder = new JDotNetDomBuilder();
            builder.setAttachmentPoint(attachmentPoint);
        }

        /// <summary>Construct a <c>DomDestination</c> based on an existing Element node.</summary>
        /// <remarks>The new data will be added as a child of the supplied element node.</remarks>
        /// <param name="attachmentPoint">The element node to which new contents will
        /// be attached. The new contents will be added after any existing children.</param>

        public DomDestination(XmlElement attachmentPoint)
        {
            builder = new JDotNetDomBuilder();
            builder.setAttachmentPoint(attachmentPoint);
        }

        /// <summary>After construction, retrieve the constructed document node</summary>
        /// <remarks>If the zero-argument constructor was used, this will be a newly
        /// constructed document node. If the constructor supplied a document node, the
        /// same document node will be returned. If the constructor supplied a document fragment
        /// node or an element node, this method returns the <c>OwnerDocument</c> property of 
        /// that node.</remarks>

        public XmlDocument XmlDocument
        {
            get { return builder.getDocumentNode(); }
        }

		/// <summary>
		/// Return a Receiver. Saxon calls this method to obtain a Receiver, to which it then sends
		/// a sequence of events representing the content of an XML document.
        /// </summary>
		/// <param name="pipe">Pipe. The Saxon pipeline configuration. This is supplied so that the destination can
		/// use information from the <code>PipelineConfiguration</code> (for example, a reference to the name pool)
		/// to construct or configure the returned Receiver.</param>
		/// <returns>The receiver.</returns>

        public override JReceiver GetReceiver(JPipelineConfiguration pipe)
        {
			builder.setPipelineConfiguration (pipe);
            return builder;
        }

		/// <summary>
		/// Gets the receiver.
		/// </summary>
		/// <param name="config">Config.The Saxon configuration. This is supplied so that the destination can
		/// use information from the <code>Configuration</code> (for example, a reference to the name pool)
		/// to construct or configure the returned Receiver.</param>
		/// <returns>The receiver.</returns>
		public JReceiver GetReceiver(JConfiguration config)
		{
			builder.setPipelineConfiguration (config.makePipelineConfiguration());
			return builder;
		}
    }

    /// <summary>
    /// A <c>NullDestination</c> is an XmlDestination that discards all its output.
    /// </summary>

    public class NullDestination : XmlDestination
    {
        /// <summary>Construct a <c>NullDestination</c></summary>

        public NullDestination()
        { }

       
		/// <summary>
		/// Gets the receiver.
		/// </summary>
		/// <returns>The receiver.</returns>
		/// <param name="pipe">Pipe. The PipelineConfiguration</param>
		public override JReceiver GetReceiver(JPipelineConfiguration pipe)
        {
            return new JSink(pipe);
        }

    }

    /// <summary>
    /// A <c>TextWriterDestination</c> is an implementation of <c>XmlDestination</c> that wraps
    /// an instance of <c>XmlWriter</c>.
    /// </summary>
    /// <remarks>
    /// <para>The name <c>TextWriterDestination</c> is a misnomer; originally this class would
    /// only wrap an <c>XmlTextWriter</c>. It will now wrap any <c>XmlWriter</c>.</para>
    /// <para>Note that when a <c>TextWriterDestination</c> is used to process the output of a stylesheet
    /// or query, the output format depends only on the way the underlying <c>XmlWriter</c>
    /// is configured; serialization parameters present in the stylesheet or query are ignored.
    /// The XSLT <c>disable-output-escaping</c> option is also ignored. If serialization
    /// is to be controlled from the stylesheet or query, use a <c>Serializer</c> as the
    /// <c>Destination</c>.</para>
    /// </remarks>

    public class TextWriterDestination : XmlDestination
    {

        internal XmlWriter writer;
        internal bool closeAfterUse = true;

        /// <summary>Construct a TextWriterDestination</summary>
        /// <param name="writer">The <c>XmlWriter</c> that is to be notified of the events
        /// representing the XML document.</param>

        public TextWriterDestination(XmlWriter writer)
        {
            this.writer = writer;
        }

        /// <summary>
        /// The <c>CloseAfterUse</c> property indicates whether the underlying <c>XmlWriter</c> is closed
        /// (by calling its <c>Close()</c> method) when Saxon has finished writing to it. The default
        /// value is true, in which case <c>Close()</c> is called. If the property is set to <c>false</c>,
        /// Saxon will refrain from calling the <c>Close()</c> method, and merely call <c>Flush()</c>,
        /// which can be useful if further output is to be written to the <c>XmlWriter</c> by the application.
        /// </summary>

        public bool CloseAfterUse
        {
            get { return closeAfterUse; }
            set { closeAfterUse = value; }
        }

        
		/// <summary>
		/// Return a Receiver. Saxon calls this method to obtain a Java Receiver, to which it then sends
		/// a sequence of events representing the content of an XML document. The method is intended
		/// primarily for internal use, and may give poor diagnostics if used incorrectly.
		/// </summary>
		/// <returns>The receiver.</returns>
		/// <param name="pipe">JPipelineConfiguration. The Saxon configuration. This is supplied so that the destination can
		/// use information from the configuration (for example, a reference to the name pool)
		/// to construct or configure the returned Receiver.</param>
		public override JReceiver GetReceiver(JPipelineConfiguration pipe)
        {
            JDotNetReceiver dnr = new JDotNetReceiver(writer);
			dnr.setPipelineConfiguration (pipe);
            dnr.setCloseAfterUse(closeAfterUse);
            return dnr;
            //net.sf.saxon.@event.TracingFilter filter = new net.sf.saxon.@event.TracingFilter();
            //filter.setUnderlyingReceiver(dnr);
            //return filter;
        }

    }


    /// <summary>
    /// An <c>XdmDestination</c> is an <c>XmlDestination</c> in which an <c>XdmNode</c> 
    /// is constructed to hold the output of a query or transformation: 
    /// that is, a tree using Saxon's implementation of the XDM data model
    /// </summary>
    /// <remarks>
    /// <para>No data needs to be supplied to the <c>XdmDestination</c> object. The query or transformation
    /// populates an <c>XmlNode</c>, which may then be retrieved as the value of the <c>XmlNode</c>
    /// property.</para>
    /// <para>An <c>XdmDestination</c> can be reused to hold the results of a second transformation only
    /// if the <c>reset</c> method is first called to reset its state.</para>
    /// </remarks>

    public class XdmDestination : XmlDestination
    {
        internal TreeModel treeModel;
        internal Uri baseUri;
        internal JBuilder builder;

        /// <summary>Construct an <c>XdmDestination</c></summary>

        public XdmDestination()
        {
            //builder = new JTinyBuilder();
        }

        ///<summary>
        /// The Tree Model implementation to be used for the constructed document. By default
        /// the TinyTree is used. The main reason for using the LinkedTree alternative is if
        /// updating is required (the TinyTree is not updateable)
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

        /// <summary>This property determines the base URI of the constructed XdmNode. 
        /// If the baseURI property of the XdmDestination is set before the destination is written to,
        /// then the constructed XdmNode will have this base URI. Setting this property after constructing the node
        /// has no effect.
        /// </summary>

        public Uri BaseUri
        {
            get { return baseUri; }
            set { baseUri = value; }
        }


        /// <summary>Reset the state of the <c>XdmDestination</c> so that it can be used to hold
        /// the result of another query or transformation.</summary>

        public void Reset()
        {
            builder = null;
        }

        /// <summary>After construction, retrieve the constructed document node</summary>
        /// <remarks>
        /// <para>The value of the property will be null if no data has been written to the
        /// XdmDestination, either because the process that writes to the destination has not
        /// yet been run, or because the process produced no output.</para>
        /// </remarks>

        public XdmNode XdmNode
        {
            get
            {
                JNodeInfo node = builder.getCurrentRoot();
                return (node == null ? null : (XdmNode)XdmValue.Wrap(builder.getCurrentRoot()));
            }
        }

		/// <summary>
		/// Return a Receiver. Saxon calls this method to obtain a Receiver, to which it then sends
		/// a sequence of events representing the content of an XML document.
        /// </summary>
        /// <returns>The receiver to which events are to be sent</returns>
		/// <param name="pipe">Pipe. The Saxon configuration. This is supplied so that the destination can
		/// use information from the configuration (for example, a reference to the name pool)
		/// to construct or configure the returned Receiver.</param>

		public override JReceiver GetReceiver(JPipelineConfiguration pipe)
        {
            builder = (treeModel == TreeModel.TinyTree ? (JBuilder)new JTinyBuilder(pipe) : (JBuilder)new JLinkedTreeBuilder(pipe));
            if (baseUri != null)
            {
                builder.setBaseURI(baseUri.ToString());
            }

            TreeProtector protector = new TreeProtector(builder);
            JComplexContentOutputter cco = new JComplexContentOutputter(pipe);
            JNamespaceReducer reducer = new JNamespaceReducer(protector);
            cco.setReceiver(reducer);
            return cco;
        }

        /**
 * TreeProtector is a filter that ensures that the events reaching the Builder constitute a single
 * tree rooted at an element or document node (because anything else will crash the builder)
 */

        public class TreeProtector : JProxyReceiver
        {

            private int level = 0;
            private bool ended = false;

            public TreeProtector(JReceiver next)
                : base(next)
            {

            }

            public override void startDocument(int properties)
            {
                if (ended)
                {
                    JXPathException e = new JXPathException("Only a single document can be written to an XdmDestination");
                    throw new DynamicError(e);
                }
                base.startDocument(properties);
                level++;
            }

            public override void endDocument()
            {
                base.endDocument();
                level--;
                if (level == 0)
                {
                    ended = true;
                }
            }

            public override void startElement(JNodeName nameCode, JSchemaType typeCode, JLocation location, int properties)
            {
                if (ended)
                {
                    JXPathException e = new JXPathException("Only a single root node can be written to an XdmDestination");
                    throw new DynamicError(e);
                }
                base.startElement(nameCode, typeCode, location, properties);
                level++;
            }

            public override void endElement()
            {
                base.endElement();
                level--;
                if (level == 0)
                {
                    ended = true;
                }
            }

			public override void characters(JCharSequence chars, JLocation location, int properties)
            {
                if (level == 0)
                {
                    JXPathException e = new JXPathException("When writing to an XdmDestination, text nodes are only allowed within a document or element node");
                    throw new DynamicError(e);
                }
                base.characters(chars, location, properties);
            }


			public override void processingInstruction(String target, JCharSequence data, JLocation location, int properties)
            {
                if (level == 0)
                {
                    JXPathException e = new JXPathException("When writing to an XdmDestination, processing instructions are only allowed within a document or element node");
                    throw new DynamicError(e);
                }
                base.processingInstruction(target, data, location, properties);
            }


			public override void comment(JCharSequence chars, JLocation location, int properties)
            {
                if (level == 0)
                {
                    JXPathException e = new JXPathException("When writing to an XdmDestination, comment nodes are only allowed within a document or element node");
                }
                base.comment(chars, location, properties);
            }


			public override void append(JItem item, JLocation location, int copyNamespaces)
            {
                if (level == 0)
                {
                    JXPathException e = new JXPathException("When writing to an XdmDestination, atomic values are only allowed within a document or element node");
                }
                base.append(item, location, copyNamespaces);
            }

        }
    }


}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2016 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////