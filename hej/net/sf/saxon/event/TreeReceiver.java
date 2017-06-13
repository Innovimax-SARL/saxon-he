////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.expr.parser.ExplicitLocation;
import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.ma.arrays.ArrayItem;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.ObjectValue;

import java.util.Arrays;

/**
 * A TreeReceiver acts as a bridge between a SequenceReceiver, which can receive
 * events for constructing any kind of sequence, and an ordinary Receiver, which
 * only handles events relating to the building of trees. To do this, it has to
 * process any items added to the sequence using the append() interface; all other
 * events are passed through unchanged.
 * <p/>
 * <p>If atomic items are appended to the sequence, then adjacent atomic items are
 * turned in to a text node by converting them to strings and adding a single space
 * as a separator.</p>
 * <p/>
 * <p>If a document node is appended to the sequence, then the document node is ignored
 * and its children are appended to the sequence.</p>
 * <p/>
 * <p>If any other node is appended to the sequence, then it is pushed to the result
 * as a sequence of Receiver events, which may involve walking recursively through the
 * contents of a tree.</p>
 */

public class TreeReceiver extends SequenceReceiver {
    private Receiver nextReceiver;
    private int level = 0;
    private boolean[] isDocumentLevel = new boolean[20];
    // The sequence of events can include startElement/endElement pairs or startDocument/endDocument
    // pairs at any level. A startDocument/endDocument pair is essentially ignored except at the
    // outermost level, except that a namespace or attribute node cannot be sent when we're at a
    // document level. See for example schema90963-err.xsl
    private boolean inStartTag = false;
    private boolean serializing = false;

    /**
     * Create a TreeReceiver
     *
     * @param nextInChain the receiver to which events will be directed, after
     *                    expanding append events into more primitive tree-based events
     */

    public TreeReceiver(/*@NotNull*/ Receiver nextInChain) {
        super(nextInChain.getPipelineConfiguration());
        nextReceiver = nextInChain;
        previousAtomic = false;
        setPipelineConfiguration(nextInChain.getPipelineConfiguration());
    }

    public void setSystemId(String systemId) {
        if (systemId != null && !systemId.equals(this.systemId)) {
            this.systemId = systemId;
            if (nextReceiver != null) {
                nextReceiver.setSystemId(systemId);
            }
        }
    }

    public void setPipelineConfiguration(/*@NotNull*/ PipelineConfiguration pipe) {
        if (pipelineConfiguration != pipe) {
            pipelineConfiguration = pipe;
            if (nextReceiver != null) {
                nextReceiver.setPipelineConfiguration(pipe);
            }
        }
    }

    /**
     * Say whether this ComplexContentOutputter is performing serialization
     *
     * @param serializing true if this class implements the sequence normalization function of the serialization
     *                    spec; false if it is constructing document or element nodes in XSLT or XQuery
     */

    public void setSerializing(boolean serializing) {
        this.serializing = serializing;
    }

    /**
     * Ask whether this ComplexContentOutputter is performing serialization
     *
     * @return true if this class implements the sequence normalization function of the serialization
     * spec; false if it is constructing document or element nodes in XSLT or XQuery
     */

    public boolean isSerializing() {
        return serializing;
    }


    /**
     * Get the underlying Receiver (that is, the next one in the pipeline)
     *
     * @return the underlying Receiver
     */

    public Receiver getUnderlyingReceiver() {
        return nextReceiver;
    }

    /**
     * Start of event sequence
     */

    public void open() throws XPathException {
        if (nextReceiver == null) {
            throw new IllegalStateException("TreeReceiver.open(): no underlying receiver provided");
        }
        nextReceiver.open();
        previousAtomic = false;
    }

    /**
     * End of event sequence
     */

    public void close() throws XPathException {
        if (nextReceiver != null) {
            nextReceiver.close();
        }
        previousAtomic = false;
    }

    /**
     * Start of a document node.
     */

    public void startDocument(int properties) throws XPathException {
        if (level == 0) {
            nextReceiver.startDocument(properties);
        }
        if (isDocumentLevel.length - 1 < level) {
            isDocumentLevel = Arrays.copyOf(isDocumentLevel, level * 2);
        }
        isDocumentLevel[level++] = true;
    }

    /**
     * Notify the end of a document node
     */

    public void endDocument() throws XPathException {
        level--;
        if (level == 0) {
            nextReceiver.endDocument();
        }
    }

    /**
     * Notify the start of an element
     *  @param nameCode   integer code identifying the name of the element within the name pool.
     * @param typeCode   integer code identifying the element's type within the name pool.
     * @param location
     * @param properties bit-significant properties of the element node
     */

    public void startElement(NodeName nameCode, SchemaType typeCode, Location location, int properties) throws XPathException {
        if (inStartTag) {
            startContent();
        }
        inStartTag = true;
        nextReceiver.startElement(nameCode, typeCode, location, properties);
        previousAtomic = false;
        if (isDocumentLevel.length - 1 < level) {
            isDocumentLevel = Arrays.copyOf(isDocumentLevel, level * 2);
        }
        isDocumentLevel[level++] = false;
    }

    /**
     * Notify a namespace. Namespaces are notified <b>after</b> the startElement event, and before
     * any children for the element. The namespaces that are reported are only required
     * to include those that are different from the parent element; however, duplicates may be reported.
     * A namespace must not conflict with any namespaces already used for element or attribute names.
     *
     * @param namespaceBindings the prefix/uri pair
     * @throws IllegalStateException: attempt to output a namespace when there is no open element
     *                                start tag
     */

    public void namespace(NamespaceBindingSet namespaceBindings, int properties) throws XPathException {
        boolean documentLevel = level == 0 || isDocumentLevel[level - 1];
        if (documentLevel || !inStartTag) {
            //noinspection LoopStatementThatDoesntLoop
            for (NamespaceBinding ns : namespaceBindings) {
                // Throw an exception if there is an least one namespace binding in the set
                throw NoOpenStartTagException.makeNoOpenStartTagException(
                        Type.NAMESPACE, ns.getPrefix(),
                        getPipelineConfiguration().getHostLanguage(),
                        documentLevel, isSerializing(),
                        ExplicitLocation.UNKNOWN_LOCATION);
            }
        }
        nextReceiver.namespace(namespaceBindings, properties);
        previousAtomic = false;
    }

    /**
     * Notify an attribute. Attributes are notified after the startElement event, and before any
     * children. Namespaces and attributes may be intermingled.
     *
     * @param nameCode   The name of the attribute, as held in the name pool
     * @param typeCode   The type of the attribute, as held in the name pool
     * @param locationId
     *@param properties Bit significant value. The following bits are defined:
     *                   <dd>DISABLE_ESCAPING</dd>    <dt>Disable escaping for this attribute</dt>
     *                   <dd>NO_SPECIAL_CHARACTERS</dd>      <dt>Attribute value contains no special characters</dt>  @throws IllegalStateException: attempt to output an attribute when there is no open element
     *                                start tag
     */

    public void attribute(NodeName nameCode, SimpleType typeCode, CharSequence value, Location locationId, int properties)
            throws XPathException {
        boolean documentLevel = level == 0 || isDocumentLevel[level - 1];
        if (documentLevel || !inStartTag) {
            throw NoOpenStartTagException.makeNoOpenStartTagException(
                    Type.ATTRIBUTE, nameCode.getDisplayName(),
                    getPipelineConfiguration().getHostLanguage(),
                    documentLevel, isSerializing(),
                ExplicitLocation.UNKNOWN_LOCATION);
        }
        nextReceiver.attribute(nameCode, typeCode, value, locationId, properties);
        previousAtomic = false;
    }

    /**
     * Notify the start of the content, that is, the completion of all attributes and namespaces.
     * Note that the initial receiver of output from XSLT instructions will not receive this event,
     * it has to detect it itself. Note that this event is reported for every element even if it has
     * no attributes, no namespaces, and no content.
     */


    public void startContent() throws XPathException {
        inStartTag = false;
        nextReceiver.startContent();
        previousAtomic = false;
    }

    /**
     * End of element
     */

    public void endElement() throws XPathException {
        if (inStartTag) {
            startContent();
        }
        nextReceiver.endElement();
        previousAtomic = false;
        level--;
    }

    /**
     * Character data
     */

    public void characters(CharSequence chars, Location locationId, int properties) throws XPathException {
        if (chars.length() > 0) {
            if (inStartTag) {
                startContent();
            }
            nextReceiver.characters(chars, locationId, properties);
        }
        previousAtomic = false;
    }


    /**
     * Processing Instruction
     */

    public void processingInstruction(String target, CharSequence data, Location locationId, int properties) throws XPathException {
        if (inStartTag) {
            startContent();
        }
        nextReceiver.processingInstruction(target, data, locationId, properties);
        previousAtomic = false;
    }

    /**
     * Output a comment
     */

    public void comment(CharSequence chars, Location locationId, int properties) throws XPathException {
        if (inStartTag) {
            startContent();
        }
        nextReceiver.comment(chars, locationId, properties);
        previousAtomic = false;
    }


    /**
     * Set the URI for an unparsed entity in the document.
     */

    public void setUnparsedEntity(String name, String uri, String publicId) throws XPathException {
        nextReceiver.setUnparsedEntity(name, uri, publicId);
    }

    /**
     * Append an arbitrary item (node or atomic value) to the output
     */

    public void append(/*@Nullable*/ Item item, Location locationId, int copyNamespaces) throws XPathException {
        if (item != null) {
            if (item instanceof AtomicValue || item instanceof ObjectValue) {
                if (previousAtomic) {
                    characters(" ", locationId, 0);
                }
                characters(item.getStringValueCS(), locationId, 0);
                previousAtomic = true;
            } else if (item instanceof ArrayItem) {
                for (Sequence seq : ((ArrayItem)item).getMembers()) {
                    Item it;
                    SequenceIterator iter = seq.iterate();
                    while ((it = iter.next()) != null) {
                        append(it, locationId, copyNamespaces);
                    }
                }
            } else if (item instanceof Function) {
                String what = "a function item";
                if (item instanceof MapItem) {
                    what = "a map";
//                } else if (item instanceof ArrayItem) {
//                    what = "an array";
                }
                throw new XPathException("Cannot add " + what + " to a tree", "FOTY0013");
            } else if (((NodeInfo) item).getNodeKind() == Type.DOCUMENT) {
                startDocument(0); // needed to ensure that illegal namespaces or attributes in the content are caught
                SequenceIterator iter = ((NodeInfo) item).iterateAxis(AxisInfo.CHILD);
                Item it;
                while ((it = iter.next()) != null) {
                    append(it, locationId, copyNamespaces);
                }
                previousAtomic = false;
                endDocument();
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
     * Ask whether this Receiver (or the downstream pipeline) makes any use of the type annotations
     * supplied on element and attribute events
     *
     * @return true if the Receiver makes any use of this information. If false, the caller
     *         may supply untyped nodes instead of supplying the type annotation
     */

    public boolean usesTypeAnnotations() {
        return nextReceiver.usesTypeAnnotations();
    }
}

