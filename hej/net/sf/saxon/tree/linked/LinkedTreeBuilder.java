////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.linked;

import net.sf.saxon.event.Builder;
import net.sf.saxon.event.BuilderMonitor;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.ReceiverOptions;
import net.sf.saxon.expr.parser.ExplicitLocation;
import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.AttributeCollectionImpl;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;
import net.sf.saxon.value.Whitespace;

import java.util.ArrayList;
import java.util.Arrays;


/**
 * The LinkedTreeBuilder class is responsible for taking a stream of Receiver events and constructing
 * a Document tree using the linked tree implementation.
 *
 * @author Michael H. Kay
 */

public class LinkedTreeBuilder extends Builder

{
//    private static AttributeCollectionImpl emptyAttributeCollection =
//    				new AttributeCollectionImpl((Configuration)null);

    /*@Nullable*/ private ParentNodeImpl currentNode;
    private boolean contentStarted = false;     // provides a minimal check on correct sequence of calls
    private NodeFactory nodeFactory;
    /*@NotNull*/ private int[] size = new int[100];          // stack of number of children for each open node
    private int depth = 0;
    private ArrayList<NodeImpl[]> arrays = new ArrayList<NodeImpl[]>(20);       // reusable arrays for creating nodes
    private NodeName elementNodeName;
    private SchemaType elementType;
    private boolean isNilled;
    private Location pendingLocation;
    /*@Nullable*/ private AttributeCollectionImpl attributes;
    private NamespaceBinding[] namespaces;
    private int namespacesUsed;
    private boolean allocateSequenceNumbers = true;
    private int nextNodeNumber = 1;
    private static final NamespaceBinding[] EMPTY_NAMESPACE_LIST = new NamespaceBinding[0];

    /**
     * Create a Builder and initialise variables
     *
     * @param pipe the pipeline configuration
     */

    public LinkedTreeBuilder(PipelineConfiguration pipe) {
        super(pipe);
        nodeFactory = DefaultNodeFactory.THE_INSTANCE;
        // System.err.println("new TreeBuilder " + this);
    }

    /**
     * Get the current root node. This will normally be a document node, but if the root of the tree
     * is an element node, it can be an element.
     *
     * @return the root of the tree that is currently being built, or that has been most recently built
     *         using this builder
     */

    /*@Nullable*/
    public NodeInfo getCurrentRoot() {
        NodeInfo physicalRoot = currentRoot;
        if (physicalRoot instanceof DocumentImpl && ((DocumentImpl) physicalRoot).isImaginary()) {
            return ((DocumentImpl) physicalRoot).getDocumentElement();
        } else {
            return physicalRoot;
        }
    }

    public void reset() {
        super.reset();
        currentNode = null;
        nodeFactory = DefaultNodeFactory.THE_INSTANCE;
        depth = 0;
        allocateSequenceNumbers = true;
        nextNodeNumber = 1;
    }

    /**
     * Set whether the builder should allocate sequence numbers to elements as they are added to the
     * tree. This is normally done, because it provides a quick way of comparing document order. But
     * nodes added using XQuery update are not sequence-numbered.
     *
     * @param allocate true if sequence numbers are to be allocated
     */

    public void setAllocateSequenceNumbers(boolean allocate) {
        allocateSequenceNumbers = allocate;
    }

    /**
     * Set the Node Factory to use. If none is specified, the Builder uses its own.
     *
     * @param factory the node factory to be used. This allows custom objects to be used to represent
     *                the elements in the tree.
     */

    public void setNodeFactory(NodeFactory factory) {
        nodeFactory = factory;
    }

    /**
     * Open the stream of Receiver events
     */

    public void open() {
        started = true;
        depth = 0;
        size[depth] = 0;
        if (arrays == null) {
            arrays = new ArrayList<NodeImpl[]>(20);
        }
        super.open();
    }


    /**
     * Start of a document node.
     * This event is ignored: we simply add the contained elements to the current document
     */

    public void startDocument(int properties) throws XPathException {
        DocumentImpl doc = new DocumentImpl();
        currentRoot = doc;
        doc.setSystemId(getSystemId());
        doc.setBaseURI(getBaseURI());
        doc.setConfiguration(config);
        currentNode = doc;
        depth = 0;
        size[depth] = 0;
        if (arrays == null) {
            arrays = new ArrayList<NodeImpl[]>(20);
        }
        doc.setRawSequenceNumber(0);
        if (lineNumbering) {
            doc.setLineNumbering();
        }
        contentStarted = true;
    }

    /**
     * Notify the end of the document
     */

    public void endDocument() throws XPathException {
        //System.err.println("End document depth=" + depth);
        currentNode.compact(size[depth]);
    }

    /**
     * Close the stream of Receiver events
     */

    public void close() throws XPathException {
        // System.err.println("TreeBuilder: " + this + " End document");
        if (currentNode == null) {
            return;    // can be called twice on an error path
        }
        currentNode.compact(size[depth]);
        currentNode = null;

        // we're not going to use this Builder again so give the garbage collector
        // something to play with
        arrays = null;

        super.close();
        nodeFactory = DefaultNodeFactory.THE_INSTANCE;
    }

    /**
     * Notify the start of an element
     */

    public void startElement(NodeName nodeName, SchemaType type, Location location, int properties) throws XPathException {
        //System.err.println("LinkedTreeBuilder: " + this + " Start element depth=" + depth);
        if (currentNode == null) {
            startDocument(0);
            ((DocumentImpl) currentRoot).setImaginary(true);
        }
        elementNodeName = nodeName;
        elementType = type;
        isNilled = (properties & ReceiverOptions.NILLED_ELEMENT) != 0;
        pendingLocation = location;
        namespacesUsed = 0;
        attributes = null;
        contentStarted = false;
    }

    public void namespace(NamespaceBindingSet namespaceBindings, int properties) {
        if (contentStarted) {
            throw new IllegalStateException("namespace() called after startContent()");
        }
        if (namespaces == null) {
            namespaces = new NamespaceBinding[5];
        }
        for (NamespaceBinding ns : namespaceBindings) {
            if (namespacesUsed == namespaces.length) {
                namespaces = Arrays.copyOf(namespaces, namespaces.length * 2);
            }
            namespaces[namespacesUsed++] = ns;
        }
    }

    public void attribute(NodeName attName, SimpleType typeCode, /*@NotNull*/ CharSequence value, Location locationId, int properties)
            throws XPathException {
        properties &= ~ReceiverOptions.DISABLE_ESCAPING;
        if (contentStarted) {
            throw new IllegalStateException("attribute() called after startContent()");
        }
        if (attributes == null) {
            attributes = new AttributeCollectionImpl(config);
        }
        if (attName.hasURI(NamespaceConstant.XML) && attName.getLocalPart().equals("id")) {
            value = Whitespace.trim(value);
        }
        attributes.addAttribute(attName, typeCode, value.toString(), locationId, properties);
    }

    public void startContent() throws XPathException {
        // System.err.println("TreeBuilder: " + this + " startContent()");
        if (contentStarted) {
            throw new IllegalStateException("startContent() called more than once");
        }
        contentStarted = true;
        if (attributes == null) {
            attributes = AttributeCollectionImpl.EMPTY_ATTRIBUTE_COLLECTION;
        } else {
            attributes.compact();
        }

        NamespaceBinding[] nslist = namespaces;
        if (nslist == null || namespacesUsed == 0) {
            nslist = EMPTY_NAMESPACE_LIST;
        }

        ElementImpl elem = nodeFactory.makeElementNode(
                currentNode, elementNodeName, elementType, isNilled,
                attributes, nslist, namespacesUsed,
                pipe,
                pendingLocation, allocateSequenceNumbers ? nextNodeNumber++ : -1);

        namespacesUsed = 0;
        attributes = null;

        // the initial array used for pointing to children will be discarded when the exact number
        // of children in known. Therefore, it can be reused. So we allocate an initial array from
        // a pool of reusable arrays. A nesting depth of >20 is so rare that we don't bother.

        while (depth >= arrays.size()) {
            arrays.add(new NodeImpl[20]);
        }
        elem.setChildren(arrays.get(depth));

        currentNode.addChild(elem, size[depth]++);
        if (depth >= size.length - 1) {
            size = Arrays.copyOf(size, size.length * 2);
        }
        size[++depth] = 0;
        namespacesUsed = 0;

        if (currentNode instanceof TreeInfo) {
            ((DocumentImpl) currentNode).setDocumentElement(elem);
        }

        currentNode = elem;
    }

    /**
     * Notify the end of an element
     */

    public void endElement() throws XPathException {
        //System.err.println("End element depth=" + depth);
        if (!contentStarted) {
            throw new IllegalStateException("missing call on startContent()");
        }
        currentNode.compact(size[depth]);
        depth--;
        currentNode = (ParentNodeImpl) currentNode.getParent();
    }

    /**
     * Notify a text node. Adjacent text nodes must have already been merged
     */

    public void characters(/*@NotNull*/ CharSequence chars, Location locationId, int properties) throws XPathException {
        // System.err.println("Characters: " + chars.toString() + " depth=" + depth);
        if (!contentStarted) {
            throw new IllegalStateException("missing call on startContent()");
        }
        if (chars.length() > 0) {
            NodeInfo prev = currentNode.getNthChild(size[depth] - 1);
            if (prev instanceof TextImpl) {
                ((TextImpl) prev).appendStringValue(chars.toString());
            } else {
                TextImpl n = nodeFactory.makeTextNode(currentNode, chars);
                //TextImpl n = new TextImpl(chars.toString());
                currentNode.addChild(n, size[depth]++);
            }
        }
    }

    /**
     * Notify a processing instruction
     */

    public void processingInstruction(String name, /*@NotNull*/ CharSequence remainder, Location locationId, int properties) {
        if (!contentStarted) {
            throw new IllegalStateException("missing call on startContent()");
        }
        ProcInstImpl pi = new ProcInstImpl(name, remainder.toString());
        currentNode.addChild(pi, size[depth]++);
        pi.setLocation(locationId.getSystemId(), locationId.getLineNumber());
    }

    /**
     * Notify a comment
     */

    public void comment(/*@NotNull*/ CharSequence chars, Location locationId, int properties) throws XPathException {
        if (!contentStarted) {
            throw new IllegalStateException("missing call on startContent()");
        }
        CommentImpl comment = new CommentImpl(chars.toString());
        currentNode.addChild(comment, size[depth]++);
    }

    /**
     * Get the current document or element node
     *
     * @return the most recently started document or element node (to which children are currently being added)
     *         In the case of elements, this is only available after startContent() has been called
     */

    /*@Nullable*/
    public ParentNodeImpl getCurrentParentNode() {
        return currentNode;
    }

    /**
     * Get the current text, comment, or processing instruction node
     *
     * @return if any text, comment, or processing instruction nodes have been added to the current parent
     *         node, then return that text, comment, or PI; otherwise return null
     */

    /*@NotNull*/
    public NodeImpl getCurrentLeafNode() {
        return currentNode.getLastChild();
    }


    /**
     * graftElement() allows an element node to be transferred from one tree to another.
     * This is a dangerous internal interface which is used only to contruct a stylesheet
     * tree from a stylesheet using the "literal result element as stylesheet" syntax.
     * The supplied element is grafted onto the current element as its only child.
     *
     * @param element the element to be grafted in as a new child.
     */

    public void graftElement(ElementImpl element) {
        currentNode.addChild(element, size[depth]++);
    }

    /**
     * Set an unparsed entity URI for the document
     */

    public void setUnparsedEntity(String name, String uri, String publicId) {
        if (((DocumentImpl) currentRoot).getUnparsedEntity(name) == null) {
            // bug 2187
            ((DocumentImpl) currentRoot).setUnparsedEntity(name, uri, publicId);
        }
    }

    /**
     * Get a builder monitor for this builder. This must be called immediately after opening the builder,
     * and all events to the builder must thenceforth be sent via the BuilderMonitor.
     *
     * @return a new BuilderMonitor appropriate to this kind of Builder; or null if the Builder does
     *         not provide this service
     */

    /*@NotNull*/
    public BuilderMonitor getBuilderMonitor() {
        return new LinkedBuilderMonitor(this);
    }

    //////////////////////////////////////////////////////////////////////////////
    // Inner class DefaultNodeFactory. This creates the nodes in the tree.
    // It can be overridden, e.g. when building the stylesheet tree
    //////////////////////////////////////////////////////////////////////////////

    private static class DefaultNodeFactory implements NodeFactory {

        public static DefaultNodeFactory THE_INSTANCE = new DefaultNodeFactory();

        /*@NotNull*/
        public ElementImpl makeElementNode(
                /*@NotNull*/ NodeInfo parent,
                /*@NotNull*/ NodeName nodeName,
                SchemaType elementType,
                boolean isNilled,
                AttributeCollectionImpl attlist,
                NamespaceBinding[] namespaces,
                int namespacesUsed,
                /*@NotNull*/ PipelineConfiguration pipe,
                Location locationId,
                int sequenceNumber)

        {
            ElementImpl e = new ElementImpl();
            if (namespacesUsed > 0) {
                e.setNamespaceDeclarations(namespaces, namespacesUsed);
            }

            e.initialise(nodeName, elementType, attlist, parent, sequenceNumber);
            if (isNilled) {
                e.setNilled();
            }
            if (locationId != ExplicitLocation.UNKNOWN_LOCATION && sequenceNumber >= 0) {
                String baseURI = locationId.getSystemId();
                int lineNumber = locationId.getLineNumber();
                int columnNumber = locationId.getColumnNumber();
                e.setLocation(baseURI, lineNumber, columnNumber);
            }
            return e;
        }

        /**
         * Make a text node
         *
         * @param content the content of the text node
         * @return the constructed text node
         */
        public TextImpl makeTextNode(NodeInfo parent, CharSequence content) {
            return new TextImpl(content.toString());
        }
    }


}

