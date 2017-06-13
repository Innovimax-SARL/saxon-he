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
import net.sf.saxon.ma.arrays.ArrayItem;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.linked.LinkedTreeBuilder;
import net.sf.saxon.tree.util.Orphan;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.ObjectValue;

/**
 * The SequenceWriter is used when writing a sequence of atomic values and nodes, for
 * example, when xsl:variable is used with content and an "as" attribute. The SequenceWriter
 * builds the sequence; the concrete subclass is responsible for deciding what to do with the
 * resulting items.
 * <p/>
 * <p>This class is not used to build temporary trees. For that, the ComplexContentOutputter
 * is used.</p>
 *
 * @author Michael H. Kay
 */

public abstract class SequenceWriter extends SequenceReceiver {
    private Receiver outputter = null;
    private TreeModel treeModel = null;
    private Builder builder = null;
    private int level = 0;
    private boolean inStartTag = false;

    public SequenceWriter(/*@NotNull*/ PipelineConfiguration pipe) {
        super(pipe);
        //System.err.println("SequenceWriter init");
    }

    /**
     * Abstract method to be supplied by subclasses: output one item in the sequence.
     *
     * @param item the item to be written to the sequence
     * @throws net.sf.saxon.trans.XPathException
     *          if any failure occurs while writing the item
     */

    public abstract void write(Item item) throws XPathException;

    /**
     * Start of a document node.
     */

    public void startDocument(int properties) throws XPathException {
        if (outputter == null) {
            createTree((properties & ReceiverOptions.MUTABLE_TREE) != 0);
        }
        if (level++ == 0) {
            outputter.startDocument(properties);
        }
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
        if (builder != null) {
            builder.setUnparsedEntity(name, systemID, publicID);
        }
    }

    /**
     * Create a (skeletal) tree to hold a document or element node.
     *
     * @param mutable set to true if the tree is required to support in-situ updates (other that the initial
     *                sequential writing of nodes to construct the tree)
     * @throws net.sf.saxon.trans.XPathException
     *          if any error occurs while creating the tree
     */

    private void createTree(boolean mutable) throws XPathException {
        PipelineConfiguration pipe = getPipelineConfiguration();
        if (treeModel != null) {
            builder = treeModel.makeBuilder(pipe);
        } else {
            if (mutable) {
                TreeModel model = pipe.getController().getModel();
                if (model.isMutable()) {
                    builder = pipe.getController().makeBuilder();
                } else {
                    builder = new LinkedTreeBuilder(pipe);
                }
            } else {
                builder = pipe.getController().makeBuilder();
            }
        }
        builder.setPipelineConfiguration(pipe);
        builder.setSystemId(systemId);
        builder.setBaseURI(systemId);
        builder.setTiming(false);

        NamespaceReducer reducer = new NamespaceReducer(builder);

        ComplexContentOutputter cco = new ComplexContentOutputter(pipe);
        cco.setHostLanguage(pipe.getHostLanguage());
        cco.setReceiver(reducer);
        outputter = cco;

        outputter.setSystemId(systemId);
        outputter.setPipelineConfiguration(pipe);
        outputter.open();
    }

    /**
     * Get the tree model that will be used for creating trees when events are written to the sequence
     * @return the tree model, if one has been set using setTreeModel(); otherwise null
     */

    public TreeModel getTreeModel() {
        return treeModel;
    }

    /**
     * Set the tree model that will be used for creating trees when events are written to the sequence
     * @param treeModel the tree model to be used. If none has been set, the default tree model for the configuration
     * is used, unless a mutable tree is required and the default tree model is not mutable, in which case a linked
     * tree is used.
     */

    public void setTreeModel(TreeModel treeModel) {
        this.treeModel = treeModel;
    }

    /**
     * Notify the end of a document node
     */

    public void endDocument() throws XPathException {
        if (--level == 0) {
            outputter.endDocument();
            outputter = null;
            NodeInfo doc = builder.getCurrentRoot();
            // add the constructed document to the result sequence
            append(doc, ExplicitLocation.UNKNOWN_LOCATION, NodeInfo.ALL_NAMESPACES);
        }
        previousAtomic = false;
    }

    /**
     * Output an element start tag.
     *  @param elemName   The element name code - a code held in the Name Pool
     * @param typeCode   Integer code identifying the type of this element. Zero identifies the default
     *                   type, that is xs:anyType
     * @param location
     * @param properties bit-significant flags indicating any special information
     */

    public void startElement(NodeName elemName, SchemaType typeCode, Location location, int properties) throws XPathException {

        if (inStartTag) {
            startContent();
        }

        if (outputter == null) {
            createTree((properties & ReceiverOptions.MUTABLE_TREE) != 0);
        }
        //System.err.println("SEQUENCE_WRITER startElement " + this);
        outputter.startElement(elemName, typeCode, location, properties);
        level++;
        inStartTag = true;
        previousAtomic = false;
    }

    /**
     * Output an element end tag.
     */

    public void endElement() throws XPathException {
        if (inStartTag) {
            startContent();
        }
        //System.err.println("SEQUENCE_WRITER endElement " + this);
        outputter.endElement();
        if (--level == 0) {
            outputter.close();
            outputter = null;
            NodeInfo element = builder.getCurrentRoot();
            append(element, ExplicitLocation.UNKNOWN_LOCATION, NodeInfo.ALL_NAMESPACES);
        }
        previousAtomic = false;
    }

    /**
     * Output a namespace declaration. <br>
     * This is added to a list of pending namespaces for the current start tag.
     * If there is already another declaration of the same prefix, this one is
     * ignored.
     * Note that unlike SAX2 startPrefixMapping(), this call is made AFTER writing the start tag.
     *
     * @param namespaceBindings The namespace binding
     * @param properties       Allows special properties to be passed if required
     * @throws net.sf.saxon.trans.XPathException
     *          if there is no start tag to write to (created using writeStartTag),
     *          or if character content has been written since the start tag was written.
     */

    public void namespace(NamespaceBindingSet namespaceBindings, int properties)
            throws XPathException {
        if (level == 0) {
            for (NamespaceBinding ns : namespaceBindings) {
                Orphan o = new Orphan(getConfiguration());
                o.setNodeKind(Type.NAMESPACE);
                o.setNodeName(new NoNamespaceName(ns.getPrefix()));
                o.setStringValue(ns.getURI());
                append(o, ExplicitLocation.UNKNOWN_LOCATION, NodeInfo.ALL_NAMESPACES);
            }
        } else {
            outputter.namespace(namespaceBindings, properties);
        }
        previousAtomic = false;
    }

    /**
     * Output an attribute value. <br>
     *
     * @param attName    An integer code representing the name of the attribute, as held in the Name Pool
     * @param typeCode   Integer code identifying the type annotation of the attribute; zero represents
     *                   the default type (xs:untypedAtomic)
     * @param value      The value of the attribute
     * @param locationId
     *@param properties Bit significant flags for passing extra information to the serializer, e.g.
     *                   to disable escaping  @throws net.sf.saxon.trans.XPathException
     *          if there is no start tag to write to (created using writeStartTag),
     *          or if character content has been written since the start tag was written.
     */

    public void attribute(NodeName attName, SimpleType typeCode, CharSequence value, Location locationId, int properties)
            throws XPathException {
        if (level == 0) {
            Orphan o = new Orphan(getConfiguration());
            o.setNodeKind(Type.ATTRIBUTE);
            o.setNodeName(attName);
            o.setStringValue(value);
            o.setTypeAnnotation(typeCode);
            append(o, locationId, NodeInfo.ALL_NAMESPACES);
        } else {
            outputter.attribute(attName, typeCode, value, locationId, properties);
        }
        previousAtomic = false;
    }

    /**
     * The startContent() event is notified after all namespaces and attributes of an element
     * have been notified, and before any child nodes are notified.
     *
     * @throws net.sf.saxon.trans.XPathException
     *          for any failure
     */

    public void startContent() throws XPathException {
        inStartTag = false;
        outputter.startContent();
        previousAtomic = false;
    }

    /**
     * Produce text content output. <BR>
     *
     * @param s          The String to be output
     * @param locationId
     *@param properties bit-significant flags for extra information, e.g. disable-output-escaping  @throws net.sf.saxon.trans.XPathException
     *          for any failure
     */

    public void characters(CharSequence s, Location locationId, int properties) throws XPathException {
        if (level == 0) {
            Orphan o = new Orphan(getConfiguration());
            o.setNodeKind(Type.TEXT);
            o.setStringValue(s.toString());
            append(o, locationId, NodeInfo.ALL_NAMESPACES);
        } else {
            if (s.length() > 0) {
                if (inStartTag) {
                    startContent();
                }
                outputter.characters(s, locationId, properties);
            }
        }
        previousAtomic = false;
    }

    /**
     * Write a comment.
     */

    public void comment(CharSequence comment, Location locationId, int properties) throws XPathException {
        if (inStartTag) {
            startContent();
        }
        if (level == 0) {
            Orphan o = new Orphan(getConfiguration());
            o.setNodeKind(Type.COMMENT);
            o.setStringValue(comment);
            append(o, locationId, NodeInfo.ALL_NAMESPACES);
        } else {
            outputter.comment(comment, locationId, properties);
        }
        previousAtomic = false;
    }

    /**
     * Write a processing instruction
     * No-op in this implementation
     */

    public void processingInstruction(String target, CharSequence data, Location locationId, int properties) throws XPathException {
        if (inStartTag) {
            startContent();
        }
        if (level == 0) {
            Orphan o = new Orphan(getConfiguration());
            o.setNodeName(new NoNamespaceName(target));
            o.setNodeKind(Type.PROCESSING_INSTRUCTION);
            o.setStringValue(data);
            append(o, locationId, NodeInfo.ALL_NAMESPACES);
        } else {
            outputter.processingInstruction(target, data, locationId, properties);
        }
        previousAtomic = false;
    }

    /**
     * Close the output
     */

    public void close() throws XPathException {
        previousAtomic = false;
        if (outputter != null) {
            outputter.close();
        }
    }

    /**
     * Append an item to the sequence, performing any necessary type-checking and conversion
     */

    public void append(/*@Nullable*/ Item item, Location locationId, int copyNamespaces) throws XPathException {

        if (item == null) {
            return;
        }

        if (level == 0) {
            write(item);
            previousAtomic = false;
        } else {
            if (item instanceof AtomicValue || item instanceof ObjectValue) {
                // If an atomic value is written to a tree, and the previous item was also
                // an atomic value, then add a single space to separate them
                if (previousAtomic) {
                    outputter.characters(" ", ExplicitLocation.UNKNOWN_LOCATION, 0);
                }
                outputter.characters(item.getStringValueCS(), ExplicitLocation.UNKNOWN_LOCATION, 0);
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
                XPathException err = new XPathException("Cannot write a function item to an XML tree", "FOTY0013");
                err.setLocator(locationId.saveLocation());
                throw err;
            } else {
                NodeInfo node = (NodeInfo) item;
                if (node.getNodeKind() == Type.ATTRIBUTE && ((SimpleType) node.getSchemaType()).isNamespaceSensitive()) {
                    XPathException err = new XPathException("Cannot copy attributes whose type is namespace-sensitive (QName or NOTATION): " +
                            Err.wrap(node.getDisplayName(), Err.ATTRIBUTE));
                    err.setErrorCode(getPipelineConfiguration().getHostLanguage() == Configuration.XSLT ? "XTTE0950" : "XQTY0086");
                    throw err;
                }
                ((NodeInfo) item).copy(outputter, CopyOptions.ALL_NAMESPACES | CopyOptions.TYPE_ANNOTATIONS, locationId);
                previousAtomic = false;
            }
        }
    }

    /**
     * Ask whether this Receiver (or the downstream pipeline) makes any use of the type annotations
     * supplied on element and attribute events
     *
     * @return true if the Receiver makes any use of this information. If false, the caller
     *         may supply untyped nodes instead of supplying the type annotation
     */

    public boolean usesTypeAnnotations() {
        return outputter == null || outputter.usesTypeAnnotations();
    }
}

