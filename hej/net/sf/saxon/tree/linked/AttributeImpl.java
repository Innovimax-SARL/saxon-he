////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.linked;

import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.ReceiverOptions;
import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.AttributeCollectionImpl;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.*;

/**
 * A node in the "linked" tree representing an attribute. Note that this is
 * generated only "on demand", when the attribute is selected by a path expression.<P>
 * <p/>
 * <p>It is possible for multiple AttributeImpl objects to represent the same attribute node.
 * The identity of an attribute node is determined by the identity of the element, and the index
 * position of the attribute within the element. Index positions are not reused when an attribute
 * is deleted, and are retained when an attribute is renamed.</p>
 * <p/>
 * <p>This object no longer caches information such as the name code and string value, because
 * these would become invalid when the element node is modified.</p>
 *
 * @author Michael H. Kay
 */

public class AttributeImpl extends NodeImpl {

    /**
     * Construct an Attribute node for the n'th attribute of a given element
     *
     * @param element The element containing the relevant attribute
     * @param index   The index position of the attribute starting at zero
     */

    public AttributeImpl(ElementImpl element, int index) {
        setRawParent(element);
        setSiblingPosition(index);
    }

    /**
     * Get the name of the node. Returns null for an unnamed node
     *
     * @return the name of the node
     */
    @Override
    public NodeName getNodeName() {
        if (getRawParent() == null || getSiblingPosition() == -1) {
            // implies this node is deleted
            return null;
        }
        return ((ElementImpl) getRawParent()).getAttributeList().getNodeName(getSiblingPosition());
    }

    /**
     * Get the fingerprint of the node. This is used to compare whether two nodes
     * have equivalent names. Return -1 for a node with no name.
     */
    @Override
    public int getFingerprint() {
        if (getRawParent() == null || getSiblingPosition() == -1) {
            // implies this node is deleted
            return -1;
        }
        return ((ElementImpl) getRawParent()).getAttributeList().getFingerprint(getSiblingPosition());
    }

    /**
     * Get the type annotation
     *
     * @return the type annotation of the node, if any
     */

    public SchemaType getSchemaType() {
        return ((ElementImpl) getRawParent()).getAttributeList().getTypeAnnotation(getSiblingPosition());
    }


    /**
     * Determine whether this node has the is-id property
     *
     * @return true if the node is an ID
     */

    public boolean isId() {
        try {
            return getFingerprint() == StandardNames.XML_ID || getSchemaType().isIdType();
        } catch (MissingComponentException e) {
            return false;
        }
    }

    /**
     * Determine whether this node has the is-idref property
     *
     * @return true if the node is an IDREF or IDREFS element or attribute
     */

    public boolean isIdref() {
        AttributeCollection alist = ((ElementImpl) getRawParent()).getAttributeList();
        if ((alist.getProperties(getSiblingPosition()) & ReceiverOptions.IS_IDREF) != 0) {
            return true;
        }
        try {
            return getSchemaType().isIdRefType();
        } catch (MissingComponentException e) {
            return false;
        }
    }

    /**
     * Determine whether the node has the is-nilled property
     *
     * @return true if the node has the is-nilled property
     */

    public boolean isNilled() {
        return false;
    }

    /**
     * Determine whether this is the same node as another node
     *
     * @return true if this Node object and the supplied Node object represent the
     *         same node in the tree.
     */

    public boolean isSameNodeInfo(NodeInfo other) {
        if (!(other instanceof AttributeImpl)) {
            return false;
        }
        if (this == other) {
            return true;
        }
        AttributeImpl otherAtt = (AttributeImpl) other;
        return getRawParent().isSameNodeInfo(otherAtt.getRawParent()) && getSiblingPosition() == otherAtt.getSiblingPosition();
    }

    /**
     * The hashCode() method obeys the contract for hashCode(): that is, if two objects are equal
     * (represent the same node) then they must have the same hashCode()
     *
     * @since 8.7 Previously, the effect of the equals() and hashCode() methods was not defined. Callers
     *        should therefore be aware that third party implementations of the NodeInfo interface may
     *        not implement the correct semantics.
     */

    public int hashCode() {
        return getRawParent().hashCode() ^ (getSiblingPosition() << 16);
    }

    /**
     * Get the node sequence number (in document order). Sequence numbers are monotonic but not
     * consecutive. In the current implementation, parent nodes (elements and roots) have a zero
     * least-significant word, while namespaces, attributes, text nodes, comments, and PIs have
     * the top word the same as their owner and the bottom half reflecting their relative position.
     */

    protected long getSequenceNumber() {
        long parseq = getRawParent().getSequenceNumber();
        return (parseq == -1L ? parseq : parseq + 0x8000 + getSiblingPosition());
        // note the 0x8000 is to leave room for namespace nodes
    }

    /**
     * Return the type of node.
     *
     * @return Node.ATTRIBUTE
     */

    public final int getNodeKind() {
        return Type.ATTRIBUTE;
    }

    /**
     * Return the character value of the node.
     *
     * @return the attribute value
     */

    public String getStringValue() {
        return ((ElementImpl) getRawParent()).getAttributeList().getValue(getSiblingPosition());
    }

    /**
     * Get next sibling - not defined for attributes
     */

    /*@Nullable*/
    public NodeImpl getNextSibling() {
        return null;
    }

    /**
     * Get previous sibling - not defined for attributes
     */

    /*@Nullable*/
    public NodeImpl getPreviousSibling() {
        return null;
    }

    /**
     * Get the previous node in document order (skipping attributes)
     */

    /*@NotNull*/
    public NodeImpl getPreviousInDocument() {
        return (NodeImpl) getParent();
    }

    /**
     * Get the next node in document order (skipping attributes)
     */

    /*@Nullable*/
    public NodeImpl getNextInDocument(NodeImpl anchor) {
        if (anchor == this) return null;
        return ((NodeImpl) getParent()).getNextInDocument(anchor);
    }

    /**
     * Get sequential key. Returns key of owning element with the attribute index as a suffix
     *
     * @param buffer a buffer to which the generated ID will be written
     */

    public void generateId(/*@NotNull*/ FastStringBuffer buffer) {
        getParent().generateId(buffer);
        buffer.append('a');
        buffer.append(Integer.toString(getSiblingPosition()));
    }

    /**
     * Copy this node to a given outputter
     */

    public void copy(/*@NotNull*/ Receiver out, int copyOptions, Location locationId) throws XPathException {
        SimpleType typeCode = (CopyOptions.includes(copyOptions, CopyOptions.TYPE_ANNOTATIONS) ?
                ((SimpleType) getSchemaType()) : BuiltInAtomicType.UNTYPED_ATOMIC);
        out.attribute(NameOfNode.makeName(this), typeCode, getStringValue(), locationId, 0);
    }

    /**
     * Delete this node (that is, detach it from its parent)
     */

    public void delete() {
        if (getRawParent() != null) {
            getRawParent().removeAttribute(this);
        }
        setRawParent(null);
        setSiblingPosition(-1);
    }

    /**
     * Test whether this MutableNodeInfo object represents a node that has been deleted.
     * Generally, such a node is unusable, and any attempt to use it will result in an exception
     * being thrown
     *
     * @return true if this node has been deleted
     */

    public boolean isDeleted() {
        // Note that the attribute node may be represented by more than one object
        return getSiblingPosition() == -1 || getFingerprint() == -1 || (getRawParent() != null && getRawParent().isDeleted());
    }

    /**
     * Replace this node with a given sequence of nodes
     *
     * @param replacement the replacement nodes (which for this version of the method must be attribute
     *                    nodes - they may use any implementation of the NodeInfo interface).
     *                    The target attribute node is deleted, and the replacement nodes are added to the
     *                    parent element; if they have the same names as existing nodes, then the existing nodes will be
     *                    overwritten.
     * @param inherit     set to true if new child elements are to inherit the in-scope namespaces
     *                    of their new parent. Not used when replacing attribute nodes.
     * @throws IllegalArgumentException if any of the replacement nodes is not an attribute
     * @throws IllegalStateException    if this node has been deleted or has no parent node
     *                                  or if two of the replacement nodes have the same name
     */

    public void replace(/*@NotNull*/ NodeInfo[] replacement, boolean inherit) {
        if (isDeleted()) {
            throw new IllegalStateException("Cannot replace a deleted node");
        }
        if (getParent() == null) {
            throw new IllegalStateException("Cannot replace a parentless node");
        }
        ParentNodeImpl element = getRawParent();
        delete();
        for (NodeInfo n : replacement) {
            if (n.getNodeKind() != Type.ATTRIBUTE) {
                throw new IllegalArgumentException("Replacement nodes must be attributes");
            }
            element.addAttribute(NameOfNode.makeName(n), BuiltInAtomicType.UNTYPED_ATOMIC, n.getStringValue(), 0);
        }
    }

    /**
     * Rename this node
     *
     * @param newNameCode the NamePool code of the new name
     */

    public void rename(NodeName newNameCode) {
        // The attribute node itself is transient; we need to update the attribute collection held in the parent
        if (getRawParent() != null) {
            ((AttributeCollectionImpl) ((ElementImpl) getRawParent()).getAttributeList()).renameAttribute(getSiblingPosition(), newNameCode);
            String newURI = newNameCode.getURI();
            if (newURI.length() != 0) {
                // new attribute name is in a namespace
                String newPrefix = newNameCode.getPrefix();
                NamespaceBinding newBinding = new NamespaceBinding(newPrefix, newURI);
                String oldURI = ((ElementImpl) getRawParent()).getURIForPrefix(newPrefix, false);
                if (oldURI == null) {
                    getRawParent().addNamespace(newBinding, false);
                } else if (!oldURI.equals(newURI)) {
                    throw new IllegalArgumentException(
                            "Namespace binding of new name conflicts with existing namespace binding");
                }
            }
        }
    }

    public void replaceStringValue(CharSequence stringValue) {
        if (getRawParent() != null) {
            AttributeCollectionImpl atts = (AttributeCollectionImpl) ((ElementImpl) getRawParent()).getAttributeList();
            atts.replaceAttribute(getSiblingPosition(), stringValue);
        }
    }


    /**
     * Remove type information from this node (and its ancestors, recursively).
     * This method implements the upd:removeType() primitive defined in the XQuery Update specification
     */

    public void removeTypeAnnotation() {
        if (getRawParent() != null) {
            AttributeCollectionImpl atts = (AttributeCollectionImpl) ((ElementImpl) getRawParent()).getAttributeList();
            atts.setTypeAnnotation(getSiblingPosition(), BuiltInAtomicType.UNTYPED_ATOMIC);
            getRawParent().removeTypeAnnotation();
        }
    }


    /**
     * Set the type annotation on a node. This must only be called when the caller has verified (by validation)
     * that the node is a valid instance of the specified type. The call is ignored if the node is not an element
     * or attribute node.
     *
     * @param type the type annotation (possibly including high bits set to indicate the isID, isIDREF, and
     *                 isNilled properties)
     */

    public void setTypeAnnotation(SchemaType type) {
        if (getRawParent() != null) {
            AttributeCollectionImpl atts = (AttributeCollectionImpl) ((ElementImpl) getRawParent()).getAttributeList();
            atts.setTypeAnnotation(getSiblingPosition(), (SimpleType)type);
        }
    }
}

