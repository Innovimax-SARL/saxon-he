////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.parser.ExplicitLocation;
import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.ma.arrays.ArrayItem;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.Orphan;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.ObjectValue;

import java.util.Arrays;

/**
 * This class is used for generating complex content, that is, the content of an
 * element or document node. It enforces the rules on the order of events within
 * complex content (attributes and namespaces must come first), and it implements
 * part of the namespace fixup rules, in particular, it ensures that there is a
 * namespace node for the namespace used in the element name and in each attribute
 * name.
 * <p/>
 * <p>The same ComplexContentOutputter may be used for generating an entire XML
 * document; it is not necessary to create a new outputter for each element node.</p>
 *
 * @author Michael H. Kay
 */

public final class ComplexContentOutputter extends SequenceReceiver {

    private Receiver nextReceiver;
    // the next receiver in the output pipeline

    private int pendingStartTagDepth = -2;
    // -2 means we are at the top level, or immediately within a document node
    // -1 means we are in the content of an element node whose start tag is complete
    private NodeName pendingStartTag = null;
    private int level = -1; // records the number of startDocument or startElement events
    // that have not yet been closed. Note that startDocument and startElement
    // events may be arbitrarily nested; startDocument and endDocument
    // are ignored unless they occur at the outermost level, except that they
    // still change the level number
    private boolean[] currentLevelIsDocument = new boolean[20];
    private InScopeNamespaces[] copyNamespacesStack = new InScopeNamespaces[20];
    private Boolean elementIsInNullNamespace;
    private NodeName[] pendingAttCode = new NodeName[20];
    private SimpleType[] pendingAttType = new SimpleType[20];
    private String[] pendingAttValue = new String[20];
    private Location[] pendingAttLocation = new Location[20];
    private int[] pendingAttProp = new int[20];
    private int pendingAttListSize = 0;

    private NamespaceBinding[] pendingNSList = new NamespaceBinding[20];
    private int pendingNSListSize = 0;

    private SchemaType currentSimpleType = null;  // any other value means we are currently writing an
    // element of a particular simple type

    private int startElementProperties;
    private Location startElementLocationId = ExplicitLocation.UNKNOWN_LOCATION;
    private boolean declaresDefaultNamespace;
    private int hostLanguage = Configuration.XSLT;
    private boolean serializing = false;


    /**
     * Create a ComplexContentOutputter
     *
     * @param pipe the pipeline configuration
     */

    public ComplexContentOutputter(/*@NotNull*/ PipelineConfiguration pipe) {
        super(pipe);
        //System.err.println("ComplexContentOutputter init");
    }

    /**
     * Static factory method to create an push pipeline containing a ComplexContentOutputter
     * @param receiver the destination to which the constructed complex content will be written
     * @param options options for validating the output stream; may be null
     * @throws XPathException if any dynamic error occurs; and
     *                        specifically, if an attempt is made to switch to a final output
     *                        destination while writing a temporary tree or sequence @param isFinal true if the destination is a final result tree
     *                        (either the principal output or a secondary result tree); false if  @param validation Validation to be performed on the output document
     */

    public static SequenceReceiver makeComplexContentReceiver(Receiver receiver, ParseOptions options)
        throws XPathException {
//        System.err.println("CHANGE OUTPUT DESTINATION new=" + receiver);

        String systemId = receiver.getSystemId();
        boolean validate = options != null && options.getSchemaValidationMode() != Validation.PRESERVE;

        if (receiver instanceof ComplexContentOutputter && !validate) {
            return (ComplexContentOutputter) receiver;
        }

        PipelineConfiguration pipe = receiver.getPipelineConfiguration();
        ComplexContentOutputter out = new ComplexContentOutputter(pipe);
        out.setHostLanguage(pipe.getHostLanguage());

        // add a filter to remove duplicate namespaces

        NamespaceReducer ne = new NamespaceReducer(receiver);
        ne.setSystemId(receiver.getSystemId());
        receiver = ne;

        // add a validator to the pipeline if required

        if (validate) {
            Configuration config = pipe.getConfiguration();
            receiver = config.getDocumentValidator(ne, receiver.getSystemId(), options, null);
        }

        out.setReceiver(receiver);
        out.setSystemId(systemId);
        return out;
    }

    public void setPipelineConfiguration(/*@NotNull*/ PipelineConfiguration pipe) {
        if (pipelineConfiguration != pipe) {
            pipelineConfiguration = pipe;
            if (nextReceiver != null) {
                nextReceiver.setPipelineConfiguration(pipe);
            }
        }
    }

    @Override
    public void setSystemId(String systemId) {
        super.setSystemId(systemId);
        nextReceiver.setSystemId(systemId);
    }

    /**
     * Set the host language
     *
     * @param language the host language, for example {@link Configuration#XQUERY}
     */

    public void setHostLanguage(int language) {
        hostLanguage = language;
    }

    /**
     * Say whether this ComplexContentOutputter is performing serialization
     * @param serializing true if this class implements the sequence normalization function of the serialization
     *             spec; false if it is constructing document or element nodes in XSLT or XQuery
     */

    public void setSerializing(boolean serializing) {
        this.serializing = serializing;
    }

    /**
     * Ask whether this ComplexContentOutputter is performing serialization
     *
     * @return true if this class implements the sequence normalization function of the serialization
     *                    spec; false if it is constructing document or element nodes in XSLT or XQuery
     */

    public boolean isSerializing() {
        return serializing;
    }


    /**
     * Set the receiver (to handle the next stage in the pipeline) directly
     *
     * @param receiver the receiver to handle the next stage in the pipeline
     */

    public void setReceiver(Receiver receiver) {
        this.nextReceiver = receiver;
    }

    /**
     * Get the next receiver in the processing pipeline
     *
     * @return the receiver which this ComplexContentOutputter writes to
     */

    public Receiver getReceiver() {
        return nextReceiver;
    }


    /**
     * Start the output process
     */

    public void open() throws XPathException {
        nextReceiver.open();
        previousAtomic = false;
    }

    /**
     * Start of a document node.
     */

    public void startDocument(int properties) throws XPathException {
        level++;
        if (level == 0) {
            nextReceiver.startDocument(properties);
        } else if (pendingStartTagDepth >= 0) {
            startContent();
            pendingStartTagDepth = -2;
        }
        previousAtomic = false;
        if (currentLevelIsDocument.length < level + 1) {
            currentLevelIsDocument = Arrays.copyOf(currentLevelIsDocument, level * 2);
        }
        currentLevelIsDocument[level] = true;
    }

    /**
     * Notify the end of a document node
     */

    public void endDocument() throws XPathException {
        if (level == 0) {
            nextReceiver.endDocument();
        }
        previousAtomic = false;
        level--;
    }

    /**
     * Notify an unparsed entity URI.
     *
     * @param name     The name of the unparsed entity
     * @param systemID The system identifier of the unparsed entity
     * @param publicID The public identifier of the unparsed entity
     */
    @Override
    public void setUnparsedEntity(String name, String systemID, String publicID) throws XPathException {
        nextReceiver.setUnparsedEntity(name, systemID, publicID);
    }

    /**
     * Produce text content output. <BR>
     * Special characters are escaped using XML/HTML conventions if the output format
     * requires it.
     *
     * @param s The String to be output
     * @param locationId
     * @throws XPathException for any failure
     */

    public void characters(CharSequence s, Location locationId, int properties) throws XPathException {
        previousAtomic = false;
        if (s == null) {
            return;
        }
        int len = s.length();
        if (len == 0) {
            return;
        }
        if (pendingStartTagDepth >= 0) {
            startContent();
        }
        nextReceiver.characters(s, locationId, properties);
    }

    /**
     * Output an element start tag. <br>
     * The actual output of the tag is deferred until all attributes have been output
     * using attribute().
     *
     * @param elemName The element name
     * @param location
     */

    public void startElement(NodeName elemName, SchemaType typeCode, Location location, int properties) throws XPathException {
        //System.err.println("CCO " + this + "StartElement " + nameCode);
        level++;
        if (pendingStartTagDepth >= 0) {
            startContent();
        }
        startElementProperties = properties;
        startElementLocationId = location.saveLocation();
        pendingAttListSize = 0;
        pendingNSListSize = 0;
        pendingStartTag = elemName;
        pendingStartTagDepth = 1;
        elementIsInNullNamespace = null; // meaning not yet computed
        declaresDefaultNamespace = false;
        currentSimpleType = typeCode;
        previousAtomic = false;
        if (currentLevelIsDocument.length < level + 1) {
            currentLevelIsDocument = Arrays.copyOf(currentLevelIsDocument, level * 2);
            copyNamespacesStack = Arrays.copyOf(copyNamespacesStack, level * 2);
        }
        currentLevelIsDocument[level] = false;
    }


    /**
     * Output one or more namespace declarations. <br>
     * This is added to a list of pending namespaces for the current start tag.
     * If there is already another declaration of the same prefix, this one is
     * ignored, unless the REJECT_DUPLICATES flag is set, in which case this is an error.
     * Note that unlike SAX2 startPrefixMapping(), this call is made AFTER writing the start tag.
     *
     * @param nsBindings The namespace bindings
     * @throws XPathException if there is no start tag to write to (created using writeStartTag),
     *                        or if character content has been written since the start tag was written.
     */

    public void namespace(NamespaceBindingSet nsBindings, int properties)
            throws XPathException {

        // Optimization for recursive shallow-copy added in 9.8 - see bug 3011.
        if (nsBindings instanceof InScopeNamespaces) {
            copyNamespacesStack[level] = (InScopeNamespaces)nsBindings;
            if (level > 1 && copyNamespacesStack[level-1] instanceof InScopeNamespaces &&
                    copyNamespacesStack[level - 1].getElement().isSameNodeInfo(((InScopeNamespaces)nsBindings).getElement())) {
                // Ignore these namespaces if they are the same as the namespaces for the parent element
                return;
            }
        }

        // System.err.println("Write namespace prefix=" + (nscode>>16) + " uri=" + (nscode&0xffff));
        for (NamespaceBinding ns : nsBindings) {
            if (pendingStartTagDepth < 0) {
                throw NoOpenStartTagException.makeNoOpenStartTagException(
                        Type.NAMESPACE,
                        ns.getPrefix(),
                        hostLanguage,
                        pendingStartTagDepth == -2,
                        isSerializing(),
                    startElementLocationId);
            }

            // elimination of namespaces already present on an outer element of the
            // result tree is done by the NamespaceReducer.

            // Handle declarations whose prefix is duplicated for this element.

            boolean rejectDuplicates = (properties & ReceiverOptions.REJECT_DUPLICATES) != 0;

            for (int i = 0; i < pendingNSListSize; i++) {
                if (nsBindings.equals(pendingNSList[i])) {
                    // same prefix and URI: ignore this duplicate
                    return;
                }
                if (ns.getPrefix().equals(pendingNSList[i].getPrefix())) {
                    if (pendingNSList[i].isDefaultUndeclaration() || ns.isDefaultUndeclaration()) {
                        // xmlns="" overridden by xmlns="abc"
                        pendingNSList[i] = ns;
                    } else if (rejectDuplicates) {
                        String prefix = ns.getPrefix();
                        String uri1 = ns.getURI();
                        String uri2 = pendingNSList[i].getURI();
                        XPathException err = new XPathException("Cannot create two namespace nodes with the same prefix mapped to different URIs (prefix=" +
                                (prefix.isEmpty() ? "\"\"" : prefix) + ", URI=" +
                                (uri1.isEmpty() ? "\"\"" : uri1) + ", URI=" +
                                (uri2.isEmpty() ? "\"\"" : uri2) + ")");
                        err.setErrorCode(hostLanguage == Configuration.XSLT ? "XTDE0430" : "XQDY0102");
                        throw err;
                    } else {
                        // same prefix, do a quick exit
                        return;
                    }
                }
            }

            // It is an error to output a namespace node for the default namespace if the element
            // itself is in the null namespace, as the resulting element could not be serialized

            if (ns.getPrefix().isEmpty() && (ns.getURI().length() != 0)) {
                declaresDefaultNamespace = true;
                if (elementIsInNullNamespace == null) {
                    elementIsInNullNamespace = pendingStartTag.hasURI("");
                }
                if (elementIsInNullNamespace) {
                    XPathException err = new XPathException("Cannot output a namespace node for the default namespace when the element is in no namespace");
                    err.setErrorCode(hostLanguage == Configuration.XSLT ? "XTDE0440" : "XQDY0102");
                    throw err;
                }
            }

            // if it's not a duplicate namespace, add it to the list for this start tag

            if (pendingNSListSize + 1 > pendingNSList.length) {
                pendingNSList = Arrays.copyOf(pendingNSList, pendingNSListSize * 2);
            }
            pendingNSList[pendingNSListSize++] = ns;
            previousAtomic = false;
        }
    }


    /**
     * Output an attribute value. <br>
     * This is added to a list of pending attributes for the current start tag, overwriting
     * any previous attribute with the same name. <br>
     * This method should NOT be used to output namespace declarations.<br>
     *
     * @param attName    The name of the attribute
     * @param value      The value of the attribute
     * @param locationId
     *@param properties Bit fields containing properties of the attribute to be written  @throws XPathException if there is no start tag to write to (created using writeStartTag),
     *                        or if character content has been written since the start tag was written.
     */

    public void attribute(NodeName attName, SimpleType typeCode, CharSequence value, Location locationId, int properties) throws XPathException {
        //System.err.println("Write attribute " + nameCode + "=" + value + " to Outputter " + this);
        if (pendingStartTagDepth < 0) {
            // The complexity here is in identifying the right error message and error code

            XPathException err = NoOpenStartTagException.makeNoOpenStartTagException(
                    Type.ATTRIBUTE,
                    attName.getDisplayName(),
                    hostLanguage,
                    level < 0 || currentLevelIsDocument[level],
                    isSerializing(),
                startElementLocationId);
            err.setLocator(locationId);
            throw err;
        }

        // if this is a duplicate attribute, overwrite the original, unless
        // the REJECT_DUPLICATES option is set.

        for (int a = 0; a < pendingAttListSize; a++) {
            if (pendingAttCode[a].equals(attName)) {
                if (hostLanguage == Configuration.XSLT) {
                    pendingAttType[a] = typeCode;
                    pendingAttValue[a] = value.toString();
                    // we have to copy the CharSequence, because some kinds of CharSequence are mutable.
                    pendingAttLocation[a] = locationId;
                    pendingAttProp[a] = properties;
                    return;
                } else {
                    XPathException err = new XPathException("Cannot create an element having two attributes with the same name: " +
                            Err.wrap(attName.getDisplayName(), Err.ATTRIBUTE));
                    err.setErrorCode("XQDY0025");
                    throw err;
                }
            }
        }

        // for top-level attributes (attributes whose parent element is not being copied),
        // check that the type annotation is not namespace-sensitive (because the namespace context might
        // be different, and we don't do namespace fixup for prefixes in content: see bug 4151

        if (level == 0 && !typeCode.equals(BuiltInAtomicType.UNTYPED_ATOMIC) /**/ && currentLevelIsDocument[0] /**/) {
            // commenting-out in line above done MHK 22 Jul 2011 to pass test Constr-cont-nsmode-8
            // reverted 2011-07-27 to pass tests in qischema family
            if (typeCode.isNamespaceSensitive()) {
                XPathException err = new XPathException("Cannot copy attributes whose type is namespace-sensitive (QName or NOTATION): " +
                        Err.wrap(attName.getDisplayName(), Err.ATTRIBUTE));
                err.setErrorCode(hostLanguage == Configuration.XSLT ? "XTTE0950" : "XQTY0086");
                throw err;
            }
        }

        // otherwise, add this one to the list

        if (pendingAttListSize >= pendingAttCode.length) {
            pendingAttCode = Arrays.copyOf(pendingAttCode, pendingAttListSize * 2);
            pendingAttType = Arrays.copyOf(pendingAttType, pendingAttListSize * 2);
            pendingAttValue = Arrays.copyOf(pendingAttValue, pendingAttListSize * 2);
            pendingAttLocation = Arrays.copyOf(pendingAttLocation, pendingAttListSize * 2);
            pendingAttProp = Arrays.copyOf(pendingAttProp, pendingAttListSize * 2);
        }

        pendingAttCode[pendingAttListSize] = attName;
        pendingAttType[pendingAttListSize] = typeCode;
        pendingAttValue[pendingAttListSize] = value.toString();
        pendingAttLocation[pendingAttListSize] = locationId;
        pendingAttProp[pendingAttListSize] = properties;
        pendingAttListSize++;
        previousAtomic = false;
    }

    /**
     * Check that the prefix for an element or attribute is acceptable, allocating a substitute
     * prefix if not. The prefix is acceptable unless a namespace declaration has been
     * written that assignes this prefix to a different namespace URI. This method
     * also checks that the element or attribute namespace has been declared, and declares it
     * if not.
     *
     * @param nodeName the proposed name, including proposed prefix
     * @param seq      sequence number, used for generating a substitute prefix when necessary.
     *                 The value 0 is used for element names; values greater than 0 are used
     *                 for attribute names.
     * @return a nameCode to use in place of the proposed nameCode (or the original nameCode
     *         if no change is needed)
     * @throws net.sf.saxon.trans.XPathException
     *          if an error occurs writing the new
     *          namespace node
     */

    private NodeName checkProposedPrefix(NodeName nodeName, int seq) throws XPathException {
        NamespaceBinding binding = nodeName.getNamespaceBinding();
        String nsprefix = binding.getPrefix();

        for (int i = 0; i < pendingNSListSize; i++) {
            if (nsprefix.equals(pendingNSList[i].getPrefix())) {
                // same prefix
                if (binding.getURI().equals(pendingNSList[i].getURI())) {
                    // same URI
                    return nodeName;    // all is well
                } else {
                    String prefix = getSubstitutePrefix(binding, seq);
                    NodeName newName = new FingerprintedQName(prefix, nodeName.getURI(), nodeName.getLocalPart());
                    namespace(newName.getNamespaceBinding(), 0);
                    return newName;
                }
            }
        }
        // no declaration of this prefix: declare it now
        if (seq > 0 && nsprefix.isEmpty()) {
            // This is an attribute and the prefix is "" - need to invent a prefix
            // See bug 3068 and unit test ParserTest/testXercesSchemaDefaultedAttributes
            String prefix = getSubstitutePrefix(binding, seq);
            NodeName newName = new FingerprintedQName(prefix, nodeName.getURI(), nodeName.getLocalPart());
            namespace(newName.getNamespaceBinding(), 0);
            return newName;
        }
        namespace(binding, 0);
        return nodeName;
    }

    /**
     * It is possible for a single output element to use the same prefix to refer to different
     * namespaces. In this case we have to generate an alternative prefix for uniqueness. The
     * one we generate is based on the sequential position of the element/attribute: this is
     * designed to ensure both uniqueness (with a high probability) and repeatability
     *
     * @param nscode the proposed namespace code
     * @param seq    sequence number for use in the substitute prefix
     * @return a prefix to use in place of the one originally proposed
     */

    private String getSubstitutePrefix(NamespaceBinding nscode, int seq) {
        if (nscode.getURI().equals(NamespaceConstant.XML)) {
            return "xml";
        }
        return nscode.getPrefix() + '_' + seq;
    }

    /**
     * Output an element end tag.
     */

    public void endElement() throws XPathException {
        //System.err.println("Write end tag " + this + " : " + name);
        if (pendingStartTagDepth >= 0) {
            startContent();
        } else {
            pendingStartTagDepth = -2;
            pendingStartTag = null;
        }

        // write the end tag

        nextReceiver.endElement();
        level--;
        previousAtomic = false;
    }

    /**
     * Write a comment
     */

    public void comment(CharSequence comment, Location locationId, int properties) throws XPathException {
        if (pendingStartTagDepth >= 0) {
            startContent();
        }
        nextReceiver.comment(comment, locationId, properties);
        previousAtomic = false;
    }

    /**
     * Write a processing instruction
     */

    public void processingInstruction(String target, CharSequence data, Location locationId, int properties) throws XPathException {
        if (pendingStartTagDepth >= 0) {
            startContent();
        }
        nextReceiver.processingInstruction(target, data, locationId, properties);
        previousAtomic = false;
    }

    /**
     * Append an arbitrary item (node or atomic value) to the output
     *  @param item           the item to be appended
     * @param locationId     the location of the calling instruction, for diagnostics
     * @param copyNamespaces if the item is an element node, this indicates whether its namespaces
 *                       need to be copied. Values are {@link NodeInfo#ALL_NAMESPACES},
 *                       {@link NodeInfo#LOCAL_NAMESPACES}, {@link NodeInfo#NO_NAMESPACES}
     */

    public void append(Item item, Location locationId, int copyNamespaces) throws XPathException {
        if (item != null) {
            if (item instanceof AtomicValue || item instanceof ObjectValue) {
                if (previousAtomic) {
                    characters(" ", locationId, 0);
                }
                characters(item.getStringValueCS(), locationId, 0);
                previousAtomic = true;
            } else if (item instanceof ArrayItem) {
                for (Sequence member : (ArrayItem) item) {
                    SequenceIterator iter = member.iterate();
                    Item it;
                    while ((it = iter.next()) != null) {
                        append(it, locationId, copyNamespaces);
                    }
                }
            } else if (item instanceof Function) {
                if (SequenceReceiver.isTrueSequenceReceiver(nextReceiver)) {
                    ((SequenceReceiver)nextReceiver).append(item);
                } else {
                    String kind = "a function item";
                    if (item instanceof MapItem) {
                        kind = "a map";
                    }
                    boolean isXSLT = getPipelineConfiguration().getHostLanguage() == Configuration.XSLT;
                    throw new XPathException("Cannot add " + kind + " to an XML tree", isXSLT ? "XTDE0450" : "FOTY0013");
                }
            } else if (((NodeInfo) item).getNodeKind() == Type.DOCUMENT) {
                startDocument(0);
                SequenceIterator iter = ((NodeInfo) item).iterateAxis(AxisInfo.CHILD);
                Item it;
                while ((it = iter.next()) != null) {
                    append(it, locationId, copyNamespaces);
                }
                endDocument();
                previousAtomic = false;
            } else if (item instanceof Orphan && ((Orphan) item).isDisableOutputEscaping()) {
                // see test case doe-0185 - needed for output buffered within try/catch
                characters(item.getStringValueCS(), locationId, ReceiverOptions.DISABLE_ESCAPING);
                previousAtomic = false;
            } else {
                int copyOptions = CopyOptions.TYPE_ANNOTATIONS;
                if (copyNamespaces == NodeInfo.LOCAL_NAMESPACES) {
                    copyOptions |= CopyOptions.LOCAL_NAMESPACES;
                } else if (copyNamespaces == NodeInfo.ALL_NAMESPACES) {
                    copyOptions |= CopyOptions.ALL_NAMESPACES;
                }
                ((NodeInfo) item).copy(this, copyOptions, locationId);
                previousAtomic = false;
            }
        }
    }


    /**
     * Close the output
     */

    public void close() throws XPathException {
        // System.err.println("Close " + this + " using emitter " + emitter.getClass());
        nextReceiver.close();
        previousAtomic = false;
    }

    /**
     * Flush out a pending start tag
     */

    public void startContent() throws XPathException {

        if (pendingStartTagDepth < 0) {
            // this can happen if the method is called from outside,
            // e.g. from a SequenceOutputter earlier in the pipeline
            return;
        }

        int props = startElementProperties;
        NodeName elcode = pendingStartTag;
        if (declaresDefaultNamespace || pendingStartTag.getPrefix().length() != 0) {
            // skip this check if the element is unprefixed and no xmlns="abc" declaration has been encountered
            elcode = checkProposedPrefix(pendingStartTag, 0);
            props = startElementProperties | ReceiverOptions.NAMESPACE_OK;
        }
        nextReceiver.startElement(elcode, currentSimpleType, startElementLocationId, props);

        for (int a = 0; a < pendingAttListSize; a++) {
            NodeName attcode = pendingAttCode[a];
            if (!attcode.hasURI("")) {    // non-null prefix
                attcode = checkProposedPrefix(attcode, a + 1);
                pendingAttCode[a] = attcode;
            }
        }

        for (int n = 0; n < pendingNSListSize; n++) {
            nextReceiver.namespace(pendingNSList[n], 0);
        }

        for (int a = 0; a < pendingAttListSize; a++) {
            nextReceiver.attribute(pendingAttCode[a],
                    pendingAttType[a],
                    pendingAttValue[a],
                    pendingAttLocation[a],
                    pendingAttProp[a]);
        }

        nextReceiver.startContent();

        pendingAttListSize = 0;
        pendingNSListSize = 0;
        pendingStartTagDepth = -1;
        previousAtomic = false;
    }

    /**
     * Ask whether this Receiver (or the downstream pipeline) makes any use of the type annotations
     * supplied on element and attribute events
     *
     * @return true if the Receiver makes any use of this information. If false, the caller
     *         may supply untyped nodes instead of supplying the type annotation
     */

    public boolean usesTypeAnnotations() {
        return nextReceiver.usesTypeAnnotations();
    }

    public void beforeBulkCopy() throws XPathException {
        level++;
        if (pendingStartTagDepth >= 0) {
            startContent();
        }
    }

    public void afterBulkCopy() {
        level--;
        previousAtomic = false;
    }
}

