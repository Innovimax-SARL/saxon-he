////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pull;


import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.ReceiverOptions;
import net.sf.saxon.expr.parser.ExplicitLocation;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.CharSlice;
import net.sf.saxon.tree.tiny.CompressedWhitespace;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;
import net.sf.saxon.type.Untyped;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.Whitespace;

import javax.xml.stream.*;
import javax.xml.stream.events.EntityDeclaration;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * This class implements the Saxon PullProvider API on top of a standard StAX parser
 * (or any other StAX XMLStreamReader implementation)
 */

public class StaxBridge implements PullProvider {

    private XMLStreamReader reader;
    private StaxAttributes attributes = new StaxAttributes();
    private PipelineConfiguration pipe;
    private NamePool namePool;
    private HashMap<String, NodeName> nameCache = new HashMap<String, NodeName>();
    private List unparsedEntities = null;
    int currentEvent = START_OF_INPUT;
    int depth = 0;
    boolean ignoreIgnorable = false;
    
    /**
     * Create a new instance of the class
     */

    public StaxBridge() {

    }

    /**
     * Supply an input stream containing XML to be parsed. A StAX parser is created using
     * the JAXP XMLInputFactory.
     *
     * @param systemId    The Base URI of the input document
     * @param inputStream the stream containing the XML to be parsed
     * @throws XPathException if an error occurs creating the StAX parser
     */

    public void setInputStream(String systemId, InputStream inputStream) throws XPathException {
        try {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            //XMLInputFactory factory = new WstxInputFactory();
            factory.setXMLReporter(new StaxErrorReporter());
            reader = factory.createXMLStreamReader(systemId, inputStream);
        } catch (XMLStreamException e) {
            throw new XPathException(e);
        }
    }

    /**
     * Supply an XMLStreamReader: the events reported by this XMLStreamReader will be translated
     * into PullProvider events
     *
     * @param reader the supplier of XML events, typically an XML parser
     */

    public void setXMLStreamReader(XMLStreamReader reader) {
        this.reader = reader;
    }

    /**
     * Set configuration information. This must only be called before any events
     * have been read.
     */

    public void setPipelineConfiguration(PipelineConfiguration pipe) {
        this.pipe = new PipelineConfiguration(pipe);
        this.namePool = pipe.getConfiguration().getNamePool();
        ignoreIgnorable = pipe.getConfiguration().getParseOptions().getSpaceStrippingRule() != NoElementsSpaceStrippingRule.getInstance();
    }

    /**
     * Get configuration information.
     */

    public PipelineConfiguration getPipelineConfiguration() {
        return pipe;
    }

    /**
     * Get the XMLStreamReader used by this StaxBridge. This is available only after
     * setInputStream() or setXMLStreamReader() has been called
     *
     * @return the instance of XMLStreamReader allocated when setInputStream() was called,
     *         or the instance supplied directly to setXMLStreamReader()
     */

    public XMLStreamReader getXMLStreamReader() {
        return reader;
    }

    /**
     * Get the name pool
     *
     * @return the name pool
     */

    public NamePool getNamePool() {
        return pipe.getConfiguration().getNamePool();
    }

    /**
     * Get the next event
     *
     * @return an integer code indicating the type of event. The code
     *         {@link #END_OF_INPUT} is returned at the end of the sequence.
     */

    public int next() throws XPathException {
        if (currentEvent == START_OF_INPUT) {
            // StAX isn't reporting START_DOCUMENT so we supply it ourselves
            currentEvent = START_DOCUMENT;
            return currentEvent;
        }
        if (currentEvent == END_OF_INPUT || currentEvent == END_DOCUMENT) {
            try {
                reader.close();
            } catch (XMLStreamException e) {
                throw new XPathException(e);
            }
            return END_OF_INPUT;
        }
        try {
            if (reader.hasNext()) {
                int event = reader.next();
                //System.err.println("Read event " + event);
                currentEvent = translate(event);
            } else {
                currentEvent = END_OF_INPUT;
            }
        } catch (XMLStreamException e) {
            String message = e.getMessage();
            // Following code recognizes the messages produced by the Sun Zephyr parser
            if (message.startsWith("ParseError at")) {
                int c = message.indexOf("\nMessage: ");
                if (c > 0) {
                    message = message.substring(c + 10);
                }
            }
            XPathException err = new XPathException("Error reported by XML parser: " + message, e);
            err.setErrorCode(SaxonErrorCode.SXXP0003);
            err.setLocator(translateLocation(e.getLocation()));
            throw err;
        }
        return currentEvent;
    }


    private int translate(int event) throws XPathException {
        //System.err.println("EVENT " + event);
        switch (event) {
            case XMLStreamConstants.ATTRIBUTE:
                return ATTRIBUTE;
            case XMLStreamConstants.CDATA:
                return TEXT;
            case XMLStreamConstants.CHARACTERS:
                if (depth == 0 && reader.isWhiteSpace()) {
                    return next();
//                    } else if (reader.isWhiteSpace()) {
//                        return next();
                } else {
//                        System.err.println("TEXT[" + new String(reader.getTextCharacters(), reader.getTextStart(), reader.getTextLength()) + "]");
//                        System.err.println("  ARRAY length " + reader.getTextCharacters().length + "[" + new String(reader.getTextCharacters(), 0, reader.getTextStart() + reader.getTextLength()) + "]");
//                        System.err.println("  START: " + reader.getTextStart() + " LENGTH " + reader.getTextLength());
                    return TEXT;
                }
            case XMLStreamConstants.COMMENT:
                return COMMENT;
            case XMLStreamConstants.DTD:
                unparsedEntities = (List) reader.getProperty("javax.xml.stream.entities");
                return next();
            case XMLStreamConstants.END_DOCUMENT:
                return END_DOCUMENT;
            case XMLStreamConstants.END_ELEMENT:
                depth--;
                return END_ELEMENT;
            case XMLStreamConstants.ENTITY_DECLARATION:
                return next();
            case XMLStreamConstants.ENTITY_REFERENCE:
                return next();
            case XMLStreamConstants.NAMESPACE:
                return NAMESPACE;
            case XMLStreamConstants.NOTATION_DECLARATION:
                return next();
            case XMLStreamConstants.PROCESSING_INSTRUCTION:
                return PROCESSING_INSTRUCTION;
            case XMLStreamConstants.SPACE:
                if (depth == 0) {
                    return next();
                } else if (ignoreIgnorable) {
                    // (Brave attempt, but Woodstox doesn't seem to report ignorable whitespace)
                    return next();
                } else {
                    return TEXT;
                }
            case XMLStreamConstants.START_DOCUMENT:
                return next();  // we supplied the START_DOCUMENT ourselves
            //return START_DOCUMENT;
            case XMLStreamConstants.START_ELEMENT:
                depth++;
                return START_ELEMENT;
            default:
                throw new IllegalStateException("Unknown StAX event " + event);


        }
    }

    /**
     * Get the event most recently returned by next(), or by other calls that change
     * the position, for example getStringValue() and skipToMatchingEnd(). This
     * method does not change the position of the PullProvider.
     *
     * @return the current event
     */

    public int current() {
        return currentEvent;
    }

    /**
     * Get the attributes associated with the current element. This method must
     * be called only after a START_ELEMENT event has been notified. The contents
     * of the returned AttributeCollection are guaranteed to remain unchanged
     * until the next START_ELEMENT event, but may be modified thereafter. The object
     * should not be modified by the client.
     * <p/>
     * <p>Attributes may be read before or after reading the namespaces of an element,
     * but must not be read after the first child node has been read, or after calling
     * one of the methods skipToEnd(), getStringValue(), or getTypedValue().</p>
     *
     * @return an AttributeCollection representing the attributes of the element
     *         that has just been notified.
     */

    public AttributeCollection getAttributes() throws XPathException {
        return attributes;
    }

    /**
     * Get the namespace declarations associated with the current element. This method must
     * be called only after a START_ELEMENT event has been notified. In the case of a top-level
     * START_ELEMENT event (that is, an element that either has no parent node, or whose parent
     * is not included in the sequence being read), the NamespaceDeclarations object returned
     * will contain a namespace declaration for each namespace that is in-scope for this element
     * node. In the case of a non-top-level element, the NamespaceDeclarations will contain
     * a set of namespace declarations and undeclarations, representing the differences between
     * this element and its parent.
     * <p/>
     * <p>It is permissible for this method to return namespace declarations that are redundant.</p>
     * <p/>
     * <p>The NamespaceDeclarations object is guaranteed to remain unchanged until the next START_ELEMENT
     * event, but may then be overwritten. The object should not be modified by the client.</p>
     * <p/>
     * <p>Namespaces may be read before or after reading the attributes of an element,
     * but must not be read after the first child node has been read, or after calling
     * one of the methods skipToEnd(), getStringValue(), or getTypedValue().</p>*
     */

    public NamespaceBinding[] getNamespaceDeclarations() throws XPathException {
        int n = reader.getNamespaceCount();
        if (n == 0) {
            return NamespaceBinding.EMPTY_ARRAY;
        } else {
            NamespaceBinding[] bindings = new NamespaceBinding[n];
            for (int i = 0; i < n; i++) {
                String prefix = reader.getNamespacePrefix(i);
                if (prefix == null) {
                    prefix = "";
                }
                String uri = reader.getNamespaceURI(i);
                if (uri == null) {
                    uri = "";
                }
                bindings[i] = new NamespaceBinding(prefix, uri);
            }
            return bindings;
        }
    }

    /**
     * Skip the current subtree. This method may be called only immediately after
     * a START_DOCUMENT or START_ELEMENT event. This call returns the matching
     * END_DOCUMENT or END_ELEMENT event; the next call on next() will return
     * the event following the END_DOCUMENT or END_ELEMENT.
     */

    public int skipToMatchingEnd() throws XPathException {
        switch (currentEvent) {
            case START_DOCUMENT:
                currentEvent = END_DOCUMENT;
                return currentEvent;
            case START_ELEMENT:
                try {
                    int skipDepth = 0;
                    while (reader.hasNext()) {
                        int event = reader.next();
                        if (event == XMLStreamConstants.START_ELEMENT) {
                            skipDepth++;
                        } else if (event == XMLStreamConstants.END_ELEMENT) {
                            if (skipDepth-- == 0) {
                                currentEvent = END_ELEMENT;
                                return currentEvent;
                            }
                        }
                    }
                } catch (XMLStreamException e) {
                    throw new XPathException(e);
                }
                throw new IllegalStateException(
                        "Element start has no matching element end");
            default:
                throw new IllegalStateException(
                        "Cannot call skipToMatchingEnd() except when at start of element or document");

        }
    }

    /**
     * Close the event reader. This indicates that no further events are required.
     * It is not necessary to close an event reader after {@link #END_OF_INPUT} has
     * been reported, but it is recommended to close it if reading terminates
     * prematurely. Once an event reader has been closed, the effect of further
     * calls on next() is undefined.
     */

    public void close() {
        try {
            reader.close();
        } catch (XMLStreamException e) {
            //
        }
    }

    /**
     * Get the NodeName identifying the name of the current node. This method
     * can be used after the {@link #START_ELEMENT}, {@link #PROCESSING_INSTRUCTION},
     * {@link #ATTRIBUTE}, or {@link #NAMESPACE} events. With some PullProvider implementations,
     * it can also be used after {@link #END_ELEMENT}, but this is not guaranteed: a client who
     * requires the information at that point (for example, to do serialization) should insert an
     * {@link com.saxonica.xqj.pull.ElementNameTracker} into the pipeline.
     * If called at other times, the result is undefined and may result in an IllegalStateException.
     * If called when the current node is an unnamed namespace node (a node representing the default namespace)
     * the returned value is null.
     *
     * @return the NodeName. The NodeName can be used to obtain the prefix, local name,
     * and namespace URI.
     */
    public NodeName getNodeName() {
        if (currentEvent == START_ELEMENT || currentEvent == END_ELEMENT) {
            String local = reader.getLocalName();
            String uri = reader.getNamespaceURI();
            // We keep a cache indexed by local name, on the assumption that most of the time, a given
            // local name will only ever be used with the same prefix and URI
            NodeName cached = nameCache.get(local);
            if (cached != null && cached.hasURI(uri) && cached.getPrefix().equals(reader.getPrefix())) {
                return cached;
            } else {
                int fp = namePool.allocateFingerprint(uri, local);
                if (uri == null) {
                    cached = new NoNamespaceName(local, fp);
                } else {
                    cached = new FingerprintedQName(reader.getPrefix(), uri, local, fp);
                }
                nameCache.put(local, cached);
                return cached;
            }
        } else if (currentEvent == PROCESSING_INSTRUCTION) {
            String local = reader.getPITarget();
            return new NoNamespaceName(local);
        } else {
            throw new IllegalStateException();
        }
    }

    /**
     * Get the string value of the current element, text node, processing-instruction,
     * or top-level attribute or namespace node, or atomic value.
     * <p/>
     * <p>In other situations the result is undefined and may result in an IllegalStateException.</p>
     * <p/>
     * <p>If the most recent event was a {@link #START_ELEMENT}, this method causes the content
     * of the element to be read. The current event on completion of this method will be the
     * corresponding {@link #END_ELEMENT}. The next call of next() will return the event following
     * the END_ELEMENT event.</p>
     *
     * @return the String Value of the node in question, defined according to the rules in the
     *         XPath data model.
     */

    public CharSequence getStringValue() throws XPathException {
        switch (currentEvent) {
            case TEXT:
                CharSlice cs = new CharSlice(reader.getTextCharacters(), reader.getTextStart(), reader.getTextLength());
                return CompressedWhitespace.compress(cs);

            case COMMENT:
                return new CharSlice(reader.getTextCharacters(), reader.getTextStart(), reader.getTextLength());

            case PROCESSING_INSTRUCTION:
                String s = reader.getPIData();
                // The BEA parser includes the separator space in the value,
                // which isn't part of the XPath data model
                return Whitespace.removeLeadingWhitespace(s);

            case START_ELEMENT:
                FastStringBuffer combinedText = null;
                try {
                    int depth = 0;
                    while (reader.hasNext()) {
                        int event = reader.next();
                        if (event == XMLStreamConstants.CHARACTERS) {
                            if (combinedText == null) {
                                combinedText = new FastStringBuffer(FastStringBuffer.C64);
                            }
                            combinedText.append(
                                    reader.getTextCharacters(), reader.getTextStart(), reader.getTextLength());
                        } else if (event == XMLStreamConstants.START_ELEMENT) {
                            depth++;
                        } else if (event == XMLStreamConstants.END_ELEMENT) {
                            if (depth-- == 0) {
                                currentEvent = END_ELEMENT;
                                if (combinedText != null) {
                                    return combinedText.condense();
                                } else {
                                    return "";
                                }
                            }
                        }
                    }
                } catch (XMLStreamException e) {
                    throw new XPathException(e);
                }
            default:
                throw new IllegalStateException("getStringValue() called when current event is " + currentEvent);

        }
    }

    /**
     * Get an atomic value. This call may be used only when the last event reported was
     * ATOMIC_VALUE. This indicates that the PullProvider is reading a sequence that contains
     * a free-standing atomic value; it is never used when reading the content of a node.
     */

    public AtomicValue getAtomicValue() {
        throw new IllegalStateException();
    }

    /**
     * Get the type annotation of the current attribute or element node, or atomic value.
     * The result of this method is undefined unless the most recent event was START_ELEMENT,
     * ATTRIBUTE, or ATOMIC_VALUE.
     *
     * @return the type annotation.
     */

    public SchemaType getSchemaType() {
        if (currentEvent == START_ELEMENT) {
            return Untyped.getInstance();
        } else if (currentEvent == ATTRIBUTE) {
            return BuiltInAtomicType.UNTYPED_ATOMIC;
        } else {
            return null;
        }
    }

    /**
     * Get the location of the current event.
     * For an event stream representing a real document, the location information
     * should identify the location in the lexical XML source. For a constructed document, it should
     * identify the location in the query or stylesheet that caused the node to be created.
     * A value of null can be returned if no location information is available.
     */

    public net.sf.saxon.expr.parser.Location getSourceLocator() {
        return translateLocation(reader.getLocation());
    }

    /**
     * Translate a StAX Location object to a Saxon Locator
     *
     * @param location the StAX Location object
     * @return a Saxon/SAX SourceLocator object
     */

    private ExplicitLocation translateLocation(Location location) {
        if (location == null) {
            return ExplicitLocation.UNKNOWN_LOCATION;
        } else {
            return new ExplicitLocation(location.getSystemId(), location.getLineNumber(), location.getColumnNumber());
        }
    }

    /**
     * Implement the Saxon AttributeCollection interface over the StAX interface.
     */

    private class StaxAttributes implements AttributeCollection {

        /**
         * Return the number of attributes in the list.
         *
         * @return The number of attributes in the list.
         */

        public int getLength() {
            return reader.getAttributeCount();
        }

        public int getFingerprint(int index) {
            String local = reader.getAttributeLocalName(index);
            String uri = reader.getAttributeNamespace(index);
            if (uri == null) {
                uri = "";
            }
            return getNamePool().allocateFingerprint(uri, local);
        }

        /**
         * Get the node name of an attribute (by position)
         *
         * @param index The position of the attribute in the list
         * @return The node name of the attribute, or null if there is no attribute in that position
         */
        public NodeName getNodeName(int index) {
            String local = reader.getAttributeLocalName(index);
            String uri = reader.getAttributeNamespace(index);
            String prefix = reader.getAttributePrefix(index);
            if (prefix == null) {
                prefix = "";
            }
            if (uri == null) {
                uri = "";
            }
            return new FingerprintedQName(prefix, uri, local);
        }

        /**
         * Get the type annotation of an attribute (by position).
         *
         * @param index The position of the attribute in the list.
         * @return The type annotation
         */

        public SimpleType getTypeAnnotation(int index) {
            String type = reader.getAttributeType(index);
            if ("ID".equals(type)) {
                return BuiltInAtomicType.ID;
            }
            return BuiltInAtomicType.UNTYPED_ATOMIC;
        }

        /**
         * Get the locationID of an attribute (by position)
         *
         * @param index The position of the attribute in the list.
         * @return The location of the attribute. This can be used to obtain the
         *         actual system identifier and line number of the relevant location
         */

        public net.sf.saxon.expr.parser.Location getLocation(int index) {
            return ExplicitLocation.UNKNOWN_LOCATION;
        }

        /**
         * Get the systemId part of the location of an attribute, at a given index.
         * <p/>
         * <p>Attribute location information is not available from a SAX parser, so this method
         * is not useful for getting the location of an attribute in a source document. However,
         * in a Saxon result document, the location information represents the location in the
         * stylesheet of the instruction used to generate this attribute, which is useful for
         * debugging.</p>
         *
         * @param index the required attribute
         * @return the systemId of the location of the attribute
         */

        public String getSystemId(int index) {
            return reader.getLocation().getSystemId();
        }

        /**
         * Get the line number part of the location of an attribute, at a given index.
         * <p/>
         * <p>Attribute location information is not available from a SAX parser, so this method
         * is not useful for getting the location of an attribute in a source document. However,
         * in a Saxon result document, the location information represents the location in the
         * stylesheet of the instruction used to generate this attribute, which is useful for
         * debugging.</p>
         *
         * @param index the required attribute
         * @return the line number of the location of the attribute
         */

        public int getLineNumber(int index) {
            return reader.getLocation().getLineNumber();
        }

        /**
         * Get the properties of an attribute (by position)
         *
         * @param index The position of the attribute in the list.
         * @return The properties of the attribute. This is a set
         *         of bit-settings defined in class {@link net.sf.saxon.event.ReceiverOptions}. The
         *         most interesting of these is {{@link net.sf.saxon.event.ReceiverOptions#DEFAULTED_ATTRIBUTE},
         *         which indicates an attribute that was added to an element as a result of schema validation.
         */

        public int getProperties(int index) {
            int properties = 0;
            if (!reader.isAttributeSpecified(index)) {
                properties |= ReceiverOptions.DEFAULTED_ATTRIBUTE;
            }
            if (isIdref(index)) {
                properties |= ReceiverOptions.IS_IDREF | ReceiverOptions.ID_IDREF_CHECKED;
            }
            return properties;
        }

        /**
         * Get the prefix of the name of an attribute (by position).
         *
         * @param index The position of the attribute in the list.
         * @return The prefix of the attribute name as a string, or null if there
         *         is no attribute at that position. Returns "" for an attribute that
         *         has no prefix.
         */

        public String getPrefix(int index) {
            return getNodeName(index).getPrefix();
        }

        /**
         * Get the lexical QName of an attribute (by position).
         *
         * @param index The position of the attribute in the list.
         * @return The lexical QName of the attribute as a string, or null if there
         *         is no attribute at that position.
         */

        public String getQName(int index) {
            return getNodeName(index).getDisplayName();
        }

        /**
         * Get the local name of an attribute (by position).
         *
         * @param index The position of the attribute in the list.
         * @return The local name of the attribute as a string, or null if there
         *         is no attribute at that position.
         */

        public String getLocalName(int index) {
            return reader.getAttributeLocalName(index);
        }

        /**
         * Get the namespace URI of an attribute (by position).
         *
         * @param index The position of the attribute in the list.
         * @return The local name of the attribute as a string, or null if there
         *         is no attribute at that position.
         */

        public String getURI(int index) {
            return reader.getAttributeNamespace(index);
        }

        /**
         * Get the index of an attribute (by name).
         *
         * @param uri       The namespace uri of the attribute.
         * @param localname The local name of the attribute.
         * @return The index position of the attribute
         */

        public int getIndex(String uri, String localname) {
            for (int i = 0; i < getLength(); i++) {
                if (getLocalName(i).equals(localname) && getURI(i).equals(uri)) {
                    return i;
                }
            }
            return -1;
        }

        /**
         * Get the index, given the fingerprint
         */

        public int getIndexByFingerprint(int fingerprint) {
            return getIndex(getNamePool().getURI(fingerprint), getNamePool().getLocalName(fingerprint));
        }

        /**
         * Get the attribute value using its fingerprint
         */

        public String getValueByFingerprint(int fingerprint) {
            return getValue(getIndexByFingerprint(fingerprint));
        }

        /**
         * Get the value of an attribute (by name).
         *
         * @param uri       The namespace uri of the attribute.
         * @param localname The local name of the attribute.
         * @return The index position of the attribute
         */

        public String getValue(String uri, String localname) {
            return reader.getAttributeValue(uri, localname);
        }

        /**
         * Get the value of an attribute (by position).
         *
         * @param index The position of the attribute in the list.
         * @return The attribute value as a string, or null if
         *         there is no attribute at that position.
         */

        public String getValue(int index) {
            return reader.getAttributeValue(index);
        }

        /**
         * Determine whether a given attribute has the is-ID property set
         */

        public boolean isId(int index) {
            return "ID".equals(reader.getAttributeType(index));
        }

        /**
         * Determine whether a given attribute has the is-idref property set
         */

        public boolean isIdref(int index) {
            String attributeType = reader.getAttributeType(index);
            return "IDREF".equals(attributeType) || "IDREFS".equals(attributeType);
        }
    }

    /**
     * Get a list of unparsed entities.
     *
     * @return a list of unparsed entities, or null if the information is not available, or
     *         an empty list if there are no unparsed entities. Each item in the list will
     *         be an instance of {@link net.sf.saxon.pull.UnparsedEntity}
     */

    public List<UnparsedEntity> getUnparsedEntities() {
        if (unparsedEntities == null) {
            return null;
        }
        List<UnparsedEntity> list = new ArrayList<UnparsedEntity>(unparsedEntities.size());
        for (Object ent : unparsedEntities) {
            String name = null;
            String systemId = null;
            String publicId = null;
            String baseURI = null;
            if (ent instanceof EntityDeclaration) {
                // This is what we would expect from the StAX API spec
                EntityDeclaration ed = (EntityDeclaration) ent;
                name = ed.getName();
                systemId = ed.getSystemId();
                publicId = ed.getPublicId();
                baseURI = ed.getBaseURI();
            } else if (ent.getClass().getName().equals("com.ctc.wstx.ent.UnparsedExtEntity")) {
                // Woodstox 3.0.0 returns this: use introspection to get the data we need
                try {
                    Class woodstoxClass = ent.getClass();
                    Class<?>[] noArgClasses = new Class<?>[0];
                    Object[] noArgs = new Object[0];
                    Method method = woodstoxClass.getMethod("getName", noArgClasses);
                    name = (String) method.invoke(ent, noArgs);
                    method = woodstoxClass.getMethod("getSystemId", noArgClasses);
                    systemId = (String) method.invoke(ent, noArgs);
                    method = woodstoxClass.getMethod("getPublicId", noArgClasses);
                    publicId = (String) method.invoke(ent, noArgs);
                    method = woodstoxClass.getMethod("getBaseURI", noArgClasses);
                    baseURI = (String) method.invoke(ent, noArgs);
                } catch (NoSuchMethodException e) {
                    //
                } catch (IllegalAccessException e) {
                    //
                } catch (InvocationTargetException e) {
                    //
                }
            }
            if (name != null) {
                if (baseURI != null && systemId != null) {
                    try {
                        systemId = new URI(baseURI).resolve(systemId).toString();
                    } catch (URISyntaxException err) {
                        //
                    }
                }
                UnparsedEntity ue = new UnparsedEntity();
                ue.setName(name);
                ue.setSystemId(systemId);
                ue.setPublicId(publicId);
                ue.setBaseURI(baseURI);
                list.add(ue);
            }
        }
        return list;
    }

    /**
     * Error reporting class for StAX parser errors
     */

    private class StaxErrorReporter implements XMLReporter {

        public void report(String message, String errorType,
                           Object relatedInformation, Location location)
                throws XMLStreamException {
            ExplicitLocation loc = translateLocation(location);
            XPathException err = new XPathException("Error reported by XML parser: " + message + " (" + errorType + ')');
            err.setLocator(loc);
            pipe.getErrorListener().error(err);
        }

    }

}

