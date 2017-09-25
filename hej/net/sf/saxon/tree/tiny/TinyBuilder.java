////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.tiny;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Builder;
import net.sf.saxon.event.BuilderMonitor;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.ReceiverOptions;
import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.lib.FeatureKeys;
import net.sf.saxon.om.NamespaceBinding;
import net.sf.saxon.om.NamespaceBindingSet;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;
import net.sf.saxon.type.Type;

import java.util.Arrays;


/**
 * The TinyBuilder class is responsible for taking a stream of SAX events and constructing
 * a Document tree, using the "TinyTree" implementation.
 *
 * @author Michael H. Kay
 */

public class TinyBuilder extends Builder {

    public static final int PARENT_POINTER_INTERVAL = 10;
    // a lower value allocates more parent pointers which takes more space but reduces
    // the length of parent searches

    /*@Nullable*/ private TinyTree tree;

    private int currentDepth = 0;
    private int nodeNr = 0;             // this is the local sequence within this document
    private boolean ended = false;
    private Statistics statistics = Statistics.TEMPORARY_TREE_STATISTICS;
    private boolean markDefaultedAttributes = false;

    /**
     * Create a TinyTree builder
     *
     * @param pipe information about the pipeline leading up to this Builder
     */

    public TinyBuilder(/*@NotNull*/ PipelineConfiguration pipe) {
        super(pipe);
        Configuration config = pipe.getConfiguration();
        //markDefaultedAttributes = true;
        markDefaultedAttributes = config.isExpandAttributeDefaults() && config.getBooleanProperty(FeatureKeys.MARK_DEFAULTED_ATTRIBUTES);
        //System.err.println("TinyBuilder " + this);
    }

    /**
     * Set the size parameters for the tree
     *
     * @param stats an object holding the expected number of non-attribute nodes, the expected
     *              number of attributes, the expected number of namespace declarations, and the expected total length of
     *              character data
     */

    public void setStatistics(Statistics stats) {
        statistics = stats;
    }

    /*@NotNull*/ private int[] prevAtDepth = new int[100];
    // this array is scaffolding used while constructing the tree, it is
    // not present in the final tree. For each level of the tree, it records the
    // node number of the most recent node at that level.

    /*@NotNull*/ private int[] siblingsAtDepth = new int[100];
    // more scaffolding. For each level of the tree, this array records the
    // number of siblings processed at that level. When this exceeds a threshold value,
    // a dummy node is inserted into the arrays to contain a parent pointer: this it to
    // prevent excessively long searches for a parent node, which is normally found by
    // scanning the siblings. The value is then reset to zero.

    private boolean isIDElement = false;

    /**
     * Get the tree being built by this builder
     *
     * @return the TinyTree
     */

    /*@Nullable*/
    public TinyTree getTree() {
        return tree;
    }

    /**
     * Get the current depth in the tree
     *
     * @return the current depth
     */

    public int getCurrentDepth() {
        return currentDepth;
    }

    /**
     * Open the event stream
     */

    public void open() {
        //System.err.println("TinyBuilder " + this + " open; " + started);
        if (started) {
            // this happens when using an IdentityTransformer
            return;
        }
        if (tree == null) {
            tree = new TinyTree(config, statistics);
            currentDepth = 0;
            if (lineNumbering) {
                tree.setLineNumbering();
            }
        }
        super.open();
    }

    /**
     * Write a document node to the tree
     */

    public void startDocument(int properties) throws XPathException {
//        if (currentDepth == 0 && tree.numberOfNodes != 0) {
//            System.err.println("**** FOREST DOCUMENT **** " + tree.numberOfNodes);
//        }
//        System.err.println("New doc: free memory: " + Runtime.getRuntime().freeMemory());
//        if (sizeParams != null) {
//            System.err.println("Size params: " + sizeParams[0] + ", " + sizeParams[1] + ", " + sizeParams[2] + ", " + sizeParams[3]);
//        }
        if ((started && !ended) || currentDepth > 0) {
            // this happens when using an IdentityTransformer, or when copying a document node to form
            // the content of an element
            return;
        }

        started = true;
        ended = false;

        TinyTree tt = tree;
        assert tt != null;
        currentRoot = new TinyDocumentImpl(tt);
        TinyDocumentImpl doc = (TinyDocumentImpl) currentRoot;
        doc.setSystemId(getSystemId());
        doc.setBaseURI(getBaseURI());

        currentDepth = 0;

        int nodeNr = tt.addDocumentNode((TinyDocumentImpl) currentRoot);
        prevAtDepth[0] = nodeNr;
        prevAtDepth[1] = -1;
        siblingsAtDepth[0] = 0;
        siblingsAtDepth[1] = 0;
        tt.next[nodeNr] = -1;

        currentDepth++;
    }

    /**
     * Callback interface for SAX: not for application use
     */

    public void endDocument() throws XPathException {
//        System.err.println("TinyBuilder: " + this + " End document");

        // Add a stopper node to ensure no-one walks off the end of the array; but
        // decrement numberOfNodes so the next node will overwrite it
        tree.addNode(Type.STOPPER, 0, 0, 0, -1);
        tree.numberOfNodes--;

        if (currentDepth > 1) {
            return;
        }
        // happens when copying a document node as the child of an element

        if (ended) {
            return;  // happens when using an IdentityTransformer
        }
        ended = true;

        prevAtDepth[currentDepth] = -1;
        currentDepth--;

    }

    public void reset() {
        super.reset();
        tree = null;
        currentDepth = 0;
        nodeNr = 0;
        ended = false;
        statistics = Statistics.TEMPORARY_TREE_STATISTICS;
    }

    public void close() throws XPathException {
        TinyTree tt = tree;
        if (tt != null) {
            tt.addNode(Type.STOPPER, 0, 0, 0, -1);
            tt.condense(statistics);
        }
        super.close();
    }

    /**
     * Notify the start tag of an element
     */

    public void startElement(/*@NotNull*/ NodeName elemName, SchemaType type, Location location, int properties) throws XPathException {
//        if (currentDepth == 0 && tree.numberOfNodes != 0) {
//            System.err.println("**** FOREST ELEMENT **** trees=" + tree.rootIndexUsed );
//        }

        // if the number of siblings exceeds a certain threshold, add a parent pointer, in the form
        // of a pseudo-node

        TinyTree tt = tree;
        assert tt != null;

        if (siblingsAtDepth[currentDepth] > PARENT_POINTER_INTERVAL) {
            nodeNr = tt.addNode(Type.PARENT_POINTER, currentDepth, prevAtDepth[currentDepth - 1], 0, 0);
            int prev = prevAtDepth[currentDepth];
            if (prev > 0) {
                tt.next[prev] = nodeNr;
            }
            tt.next[nodeNr] = prevAtDepth[currentDepth - 1];
            prevAtDepth[currentDepth] = nodeNr;
            siblingsAtDepth[currentDepth] = 0;
        }

        // now add the element node itself
        int fp = elemName.obtainFingerprint(namePool);
        int prefixCode = tree.prefixPool.obtainPrefixCode(elemName.getPrefix());
        int nameCode = (prefixCode << 20) | fp;
        nodeNr = tt.addNode(Type.ELEMENT, currentDepth, -1, -1, nameCode);

        isIDElement = (properties & ReceiverOptions.IS_ID) != 0;
        int typeCode = type.getFingerprint();
        if (typeCode != StandardNames.XS_UNTYPED) {

            tt.setElementAnnotation(nodeNr, type);
            if ((properties & ReceiverOptions.NILLED_ELEMENT) != 0) {
                tt.setNilled(nodeNr);
            }
            if (!isIDElement && type.isIdType()) {
                isIDElement = true;
            }
        }

        if (currentDepth == 0) {
            prevAtDepth[0] = nodeNr;
            prevAtDepth[1] = -1;
            //tree.next[0] = -1;
            currentRoot = tt.getNode(nodeNr);
        } else {
            int prev = prevAtDepth[currentDepth];
            if (prev > 0) {
                tt.next[prev] = nodeNr;
            }
            tt.next[nodeNr] = prevAtDepth[currentDepth - 1];   // *O* owner pointer in last sibling
            prevAtDepth[currentDepth] = nodeNr;
            siblingsAtDepth[currentDepth]++;
        }
        currentDepth++;

        if (currentDepth == prevAtDepth.length) {
            prevAtDepth = Arrays.copyOf(prevAtDepth, currentDepth * 2);
            siblingsAtDepth = Arrays.copyOf(siblingsAtDepth, currentDepth * 2);
        }
        prevAtDepth[currentDepth] = -1;
        siblingsAtDepth[currentDepth] = 0;

        if (!pipe.isLocationIsCodeLocation() && location.getSystemId() != null) {
            tt.setSystemId(nodeNr, location.getSystemId());
        } else if (currentDepth == 1) {
            tt.setSystemId(nodeNr, systemId);
        }
        if (lineNumbering) {
            tt.setLineNumber(nodeNr, location.getLineNumber(), location.getColumnNumber());
        }
    }

    public void namespace(NamespaceBindingSet namespaceBindings, int properties) throws XPathException {
        assert tree != null;
        for (NamespaceBinding ns : namespaceBindings) {
            tree.addNamespace(nodeNr, ns);
        }
    }

    public void attribute(/*@NotNull*/ NodeName attName, SimpleType type, CharSequence value, Location locationId, int properties)
            throws XPathException {
        // System.err.println("attribute " + nameCode + "=" + value);
        int fp = attName.obtainFingerprint(namePool);
        String prefix = attName.getPrefix();
        int nameCode = prefix.isEmpty() ? fp : (tree.prefixPool.obtainPrefixCode(prefix) << 20) | fp;
        assert tree != null;
        assert currentRoot != null;
        tree.addAttribute(currentRoot, nodeNr, nameCode, type, value, properties);
        if (markDefaultedAttributes && ((properties & ReceiverOptions.DEFAULTED_ATTRIBUTE) != 0)) {
            tree.markDefaultedAttribute(tree.numberOfAttributes - 1);
        }
    }

    public void startContent() {
        nodeNr++;
    }

    /**
     * Notify the end of an element node
     */

    public void endElement() throws XPathException {
        TinyTree tt = tree;
        assert tt != null;
//        System.err.println("End element");
        prevAtDepth[currentDepth] = -1;
        siblingsAtDepth[currentDepth] = 0;
        currentDepth--;
        if (isIDElement) {
            // we're relying on the fact that an ID element has no element children!
            tt.indexIDElement(currentRoot, prevAtDepth[currentDepth]);
            isIDElement = false;
        }
    }

    /**
     * Get the last completed element node. This is used during checking of schema assertions,
     * which happens while the tree is still under construction. It is also used when copying
     * accumulator values to the new tree from a streamed input. This method is called immediately after
     * a call on endElement(), and it returns the element that has just ended.
     *
     * @return the last completed element node, that is, the element whose endElement event is the most recent
     *         endElement event to be reported, or null if there is no such element
     */

    public TinyNodeImpl getLastCompletedElement() {
        if (tree == null) {
            return null;
        }
        return tree.getNode(currentDepth >= 0 ? prevAtDepth[currentDepth] : 0);
        // Note: reading an incomplete tree needs care if it constructs a prior index, etc.
    }


    /**
     * Notify a text node
     */

    public void characters(/*@NotNull*/ CharSequence chars, Location locationId, int properties) throws XPathException {
        if (chars instanceof CompressedWhitespace &&
                (properties & ReceiverOptions.WHOLE_TEXT_NODE) != 0) {
            TinyTree tt = tree;
            assert tt != null;
            long lvalue = ((CompressedWhitespace) chars).getCompressedValue();
            nodeNr = tt.addNode(Type.WHITESPACE_TEXT, currentDepth, (int)(lvalue >> 32), (int)lvalue, -1);

            int prev = prevAtDepth[currentDepth];
            if (prev > 0) {
                tt.next[prev] = nodeNr;
            }
            tt.next[nodeNr] = prevAtDepth[currentDepth - 1];   // *O* owner pointer in last sibling
            prevAtDepth[currentDepth] = nodeNr;
            siblingsAtDepth[currentDepth]++;
            return;
        }

        final int len = chars.length();
        if (len > 0) {
            nodeNr = makeTextNode(chars, len);
        }
    }

    /**
     * Create a text node. Separate method so it can be overridden. If the current node
     * on the tree is already a text node, the new text will be appended to it.
     *
     * @param chars the contents of the text node
     * @param len   the length of the text node
     * @return the node number of the created text node, or the text node to which
     *         this text has been appended.
     */

    protected int makeTextNode(CharSequence chars, int len) {
        TinyTree tt = tree;
        assert tt != null;
        int bufferStart = tt.getCharacterBuffer().length();
        tt.appendChars(chars);
        int n = tt.numberOfNodes - 1;
        if (tt.nodeKind[n] == Type.TEXT && tt.depth[n] == currentDepth) {
            // merge this text node with the previous text node
            tt.beta[n] += len;
        } else {
            nodeNr = tt.addNode(Type.TEXT, currentDepth, bufferStart, len, -1);

            int prev = prevAtDepth[currentDepth];
            if (prev > 0) {
                tt.next[prev] = nodeNr;
            }
            tt.next[nodeNr] = prevAtDepth[currentDepth - 1];
            prevAtDepth[currentDepth] = nodeNr;
            siblingsAtDepth[currentDepth]++;
        }
        return nodeNr;
    }

    /**
     * Callback interface for SAX: not for application use<BR>
     */

    public void processingInstruction(String piname, /*@NotNull*/ CharSequence remainder, Location locationId, int properties) throws XPathException {
        TinyTree tt = tree;
        assert tt != null;

        if (tt.commentBuffer == null) {
            tt.commentBuffer = new FastStringBuffer(FastStringBuffer.C256);
        }
        int s = tt.commentBuffer.length();
        tt.commentBuffer.append(remainder.toString());
        int nameCode = namePool.allocateFingerprint("", piname);

        nodeNr = tt.addNode(Type.PROCESSING_INSTRUCTION, currentDepth, s, remainder.length(),
                nameCode);

        int prev = prevAtDepth[currentDepth];
        if (prev > 0) {
            tt.next[prev] = nodeNr;
        }
        tt.next[nodeNr] = prevAtDepth[currentDepth - 1];   // *O* owner pointer in last sibling
        prevAtDepth[currentDepth] = nodeNr;
        siblingsAtDepth[currentDepth]++;

        tt.setSystemId(nodeNr, locationId.getSystemId());
        if (lineNumbering) {
            tt.setLineNumber(nodeNr, locationId.getLineNumber(), locationId.getColumnNumber());
        }
    }

    /**
     * Callback interface for SAX: not for application use
     */

    public void comment(/*@NotNull*/ CharSequence chars, Location locationId, int properties) throws XPathException {

        TinyTree tt = tree;
        assert tt != null;

        if (tt.commentBuffer == null) {
            tt.commentBuffer = new FastStringBuffer(FastStringBuffer.C256);
        }
        int s = tt.commentBuffer.length();
        tt.commentBuffer.append(chars.toString());
        nodeNr = tt.addNode(Type.COMMENT, currentDepth, s, chars.length(), -1);

        int prev = prevAtDepth[currentDepth];
        if (prev > 0) {
            tt.next[prev] = nodeNr;
        }
        tt.next[nodeNr] = prevAtDepth[currentDepth - 1];   // *O* owner pointer in last sibling
        prevAtDepth[currentDepth] = nodeNr;
        siblingsAtDepth[currentDepth]++;

    }

    /**
     * Set an unparsed entity in the document
     */

    public void setUnparsedEntity(String name, String uri, String publicId) {
        if (tree.getUnparsedEntity(name) == null) {
            // bug 2187
            tree.setUnparsedEntity(name, uri, publicId);
        }
    }

    /*@NotNull*/
    public BuilderMonitor getBuilderMonitor() {
        return new TinyBuilderMonitor(this);
    }

    /**
     * Copy an element node and its subtree from another TinyTree instance
     * @param source the TinyTree from which a subtree is to be copied
     * @param nodeNr the node number of the element node to be copied
     */

    public void bulkCopy(TinyTree source, int nodeNr) {
        int newNodeNr = tree.numberOfNodes;
        tree.bulkCopy(source, nodeNr, currentDepth);
        int prev = prevAtDepth[currentDepth];
        if (prev > 0) {
            tree.next[prev] = newNodeNr;
        }
        tree.next[newNodeNr] = prevAtDepth[currentDepth - 1];
        prevAtDepth[currentDepth] = newNodeNr;
        siblingsAtDepth[currentDepth]++;
        //tree.diagnosticDump();
    }
}

