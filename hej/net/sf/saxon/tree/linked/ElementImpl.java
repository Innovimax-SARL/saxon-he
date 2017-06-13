////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.linked;

import net.sf.saxon.event.CopyInformee;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.ReceiverOptions;
import net.sf.saxon.expr.parser.ExplicitLocation;
import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.util.AttributeCollectionImpl;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.tree.util.NamespaceIterator;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.type.*;
import net.sf.saxon.value.Whitespace;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * ElementImpl implements an element with no attributes or namespace declarations.<P>
 * This class is an implementation of NodeInfo. For elements with attributes or
 * namespace declarations, class ElementWithAttributes is used.
 *
 * @author Michael H. Kay
 */


public class ElementImpl extends ParentNodeImpl implements NamespaceResolver {

    private NodeName nodeName;
    private SchemaType type = Untyped.getInstance();
    private AttributeCollection attributeList;      // this excludes namespace attributes
    /*@Nullable*/ private NamespaceBinding[] namespaceList = null;             // list of namespace codes

    /**
     * Construct an empty ElementImpl
     */

    public ElementImpl() {
    }

    /**
     * Set the attribute list
     *
     * @param atts the list of attributes of this element (not including namespace attributes)
     */

    public void setAttributeList(AttributeCollection atts) {
        this.attributeList = atts;
    }

    /**
     * Set the namespace list
     *
     * @param namespaces an integer array of namespace codes
     */

    public void setNamespaceList(NamespaceBinding[] namespaces) {
        this.namespaceList = namespaces;
    }

    /**
     * Set the node name
     * @param name the node name
     */

    public void setNodeName(NodeName name) {
        this.nodeName = name;
    }

    /**
     * Initialise a new ElementImpl with an element name
     *
     * @param elemName       Integer representing the element name, with namespaces resolved
     * @param elementType    the schema type of the element node
     * @param atts           The attribute list: always null
     * @param parent         The parent node
     * @param sequenceNumber Integer identifying this element within the document
     */

    public void initialise(/*@NotNull*/ NodeName elemName, SchemaType elementType, AttributeCollectionImpl atts, /*@NotNull*/ NodeInfo parent,
                           int sequenceNumber) {
        this.nodeName = elemName;
        this.type = elementType;
        setRawParent((ParentNodeImpl) parent);
        setRawSequenceNumber(sequenceNumber);
        attributeList = atts;
    }

    /**
     * Get the name of the node. Returns null for an unnamed node
     *
     * @return the name of the node
     */
    @Override
    public NodeName getNodeName() {
        return nodeName;
    }

    /**
     * Set location information for this node
     *
     * @param systemId the base URI
     * @param line     the line number if known
     * @param column   the column number if known
     */

    public void setLocation(String systemId, int line, int column) {
        DocumentImpl root = getRawParent().getPhysicalRoot();
        root.setLineAndColumn(getRawSequenceNumber(), line, column);
        root.setSystemId(getRawSequenceNumber(), systemId);
    }

    /**
     * Set the system ID of this node. This method is provided so that a NodeInfo
     * implements the javax.xml.transform.Source interface, allowing a node to be
     * used directly as the Source of a transformation
     */

    public void setSystemId(String uri) {
        getPhysicalRoot().setSystemId(getRawSequenceNumber(), uri);
    }

    /**
     * Get the root node
     */

    public NodeInfo getRoot() {
        ParentNodeImpl up = getRawParent();
        if (up == null || (up instanceof DocumentImpl && ((DocumentImpl) up).isImaginary())) {
            return this;
        } else {
            return up.getRoot();
        }
    }

    /**
     * Get the system ID of the entity containing this element node.
     */

    /*@Nullable*/
    public final String getSystemId() {
        DocumentImpl root = getPhysicalRoot();
        return root == null ? null : root.getSystemId(getRawSequenceNumber());
    }

    /**
     * Get the base URI of this element node. This will be the same as the System ID unless
     * xml:base has been used.
     */

    public String getBaseURI() {
        return Navigator.getBaseURI(this);
    }

    /**
     * Get the attribute list. Note that if the attribute list is empty, it should not be modified, as it
     * will be shared by other elements. Instead, set a new attribute list.
     *
     * @return the list of attributes of this element (not including namespace attributes)
     */

    public AttributeCollection gsetAttributeCollection() {
        return this.attributeList;
    }


    /**
     * Determine whether the node has the is-nilled property
     *
     * @return true if the node has the is-nilled property
     */

    public boolean isNilled() {
        return getPhysicalRoot().isNilledElement(this);
    }

    /**
     * Set the type annotation on a node. This must only be called when the caller has verified (by validation)
     * that the node is a valid instance of the specified type. The call is ignored if the node is not an element
     * or attribute node.
     *
     * @param type the type annotation
     */

    public void setTypeAnnotation(SchemaType type) {
        this.type = type;
    }

    /**
     * Say that the element has the nilled property
     */

    public void setNilled() {
        getPhysicalRoot().addNilledElement(this);
    }

    /**
     * Get the type annotation
     *
     * @return the type annotation of the node
     */

    @Override
    public SchemaType getSchemaType() {
        return type;
    }

    /**
     * Get the line number of the node within its source document entity
     */

    public int getLineNumber() {
        DocumentImpl root = getPhysicalRoot();
        if (root == null) {
            return -1;
        } else {
            return root.getLineNumber(getRawSequenceNumber());
        }
    }

    /**
     * Get the line number of the node within its source document entity
     */

    public int getColumnNumber() {
        DocumentImpl root = getPhysicalRoot();
        if (root == null) {
            return -1;
        } else {
            return root.getColumnNumber(getRawSequenceNumber());
        }
    }

    /**
     * Get a character string that uniquely identifies this node
     *
     * @param buffer to contain the generated ID
     */

    public void generateId(/*@NotNull*/ FastStringBuffer buffer) {
        int sequence = getRawSequenceNumber();
        if (sequence >= 0) {
            getPhysicalRoot().generateId(buffer);
            buffer.append("e");
            buffer.append(Integer.toString(sequence));
        } else {
            getRawParent().generateId(buffer);
            buffer.append("f");
            buffer.append(Integer.toString(getSiblingPosition()));
        }
    }

    /**
     * Return the kind of node.
     *
     * @return Type.ELEMENT
     */

    public final int getNodeKind() {
        return Type.ELEMENT;
    }

    /**
     * Copy this node to a given Receiver.
     * <p/>
     * This method is primarily for internal use. It should not be considered a stable
     * part of the Saxon API.
     *
     * @param out         the Receiver to which the node should be copied. It is the caller's
     *                    responsibility to ensure that this Receiver is open before the method is called
     *                    (or that it is self-opening), and that it is closed after use.
     * @param copyOptions a selection of the options defined in {@link CopyOptions}
     * @param location  If non-null, identifies the location of the instruction
     *                    that requested this copy. If zero, indicates that the location information
     *                    is not available
     * @throws XPathException if any downstream error occurs
     */

    public void copy(Receiver out, int copyOptions, Location location) throws XPathException {

        SchemaType typeCode = CopyOptions.includes(copyOptions, CopyOptions.TYPE_ANNOTATIONS) ?
                getSchemaType() : Untyped.getInstance();
        CopyInformee informee = (CopyInformee) out.getPipelineConfiguration().getComponent(CopyInformee.class.getName());
        if (informee != null) {
            Object o = informee.notifyElementNode(this);
            if (o instanceof Location) {
                location = (Location)o;
            }
        }

        out.startElement(NameOfNode.makeName(this), typeCode, location, 0);

        // output the namespaces

        int childCopyOptions = copyOptions & ~CopyOptions.ALL_NAMESPACES;
        if ((copyOptions & CopyOptions.LOCAL_NAMESPACES) != 0) {
            NamespaceBinding[] localNamespaces = getDeclaredNamespaces(null);
            for (NamespaceBinding ns : localNamespaces) {
                if (ns == null) {
                    break;
                }
                out.namespace(ns, 0);
            }
        } else if ((copyOptions & CopyOptions.ALL_NAMESPACES) != 0) {
            NamespaceIterator.sendNamespaces(this, out);
            childCopyOptions |= CopyOptions.LOCAL_NAMESPACES;
        }

        // output the attributes

        if (attributeList != null) {
            for (int i = 0; i < attributeList.getLength(); i++) {
                NodeName nc = attributeList.getNodeName(i);
                if (nc != null) {
                    // if attribute hasn't been deleted
                    out.attribute(nc, BuiltInAtomicType.UNTYPED_ATOMIC, attributeList.getValue(i), ExplicitLocation.UNKNOWN_LOCATION, 0);
                }
            }
        }

        out.startContent();

        // output the children

        NodeImpl next = getFirstChild();
        while (next != null) {
            next.copy(out, childCopyOptions, location);
            next = next.getNextSibling();
        }

        out.endElement();
    }

    /**
     * Delete this node (that is, detach it from its parent)
     */

    public void delete() {
        DocumentImpl root = getPhysicalRoot();
        super.delete();
        if (root != null) {
            AxisIterator iter = iterateAxis(AxisInfo.DESCENDANT_OR_SELF, NodeKindTest.ELEMENT);
            while (true) {
                ElementImpl n = (ElementImpl) iter.next();
                int atts = attributeList.getLength();
                for (int index = 0; index < atts; index++) {
                    if (attributeList.isId(index)) {
                        root.deregisterID(attributeList.getValue(index));
                    }
                }
                if (n == null) {
                    break;
                }
                root.deIndex(n);
            }
        }
    }

    /**
     * Rename this node
     *
     * @param newName the new name
     */

    public void rename(NodeName newName) {
        String prefix = newName.getPrefix();
        String uri = newName.getURI();
        NamespaceBinding ns = new NamespaceBinding(prefix, uri);
        String uc = getURIForPrefix(prefix, true);
        if (uc == null) {
            uc = "";
        }
        if (!uc.equals(uri)) {
            if (uc.isEmpty()) {
                addNamespace(ns, true);
            } else {
                throw new IllegalArgumentException(
                    "Namespace binding of new name conflicts with existing namespace binding");
            }
        }
        nodeName = newName;
    }

    /**
     * Add a namespace binding (that is, a namespace node) to this element. This call has no effect if applied
     * to a node other than an element.
     *
     * @param nscode  The namespace code representing the (prefix, uri) pair of the namespace binding to be
     *                added. If the target element already has a namespace binding with this (prefix, uri) pair, the call has
     *                no effect. If the target element currently has a namespace binding with this prefix and a different URI, an
     *                exception is raised.
     * @param inherit If true, the new namespace binding will be inherited by any children of the target element
     *                that do not already have a namespace binding for the specified prefix, recursively.
     *                If false, the new namespace binding will not be inherited.
     * @throws IllegalArgumentException if the target element already has a namespace binding for this prefix,
     *                                  or if the namespace code represents a namespace undeclaration
     */

    public void addNamespace(/*@NotNull*/ NamespaceBinding nscode, boolean inherit) {
        if (nscode.getURI().isEmpty()) {
            throw new IllegalArgumentException("Cannot add a namespace undeclaration");
        }
        addNamespaceInternal(nscode, true);

        // The data model is such that namespaces are inherited by default. If inheritance is NOT requested,
        // we must process the children to add namespace undeclarations
        if (hasChildNodes() && !inherit) {
            AxisIterator kids = iterateChildren(NodeKindTest.ELEMENT);
            while (true) {
                ElementImpl child = (ElementImpl) kids.next();
                if (child == null) {
                    break;
                }
                child.addNamespaceInternal(nscode, false);
            }
        }
    }

    private void addNamespaceInternal(/*@NotNull*/ NamespaceBinding nscode, boolean externalCall) {
        if (namespaceList == null) {
            namespaceList = new NamespaceBinding[]{nscode};
        } else {
            NamespaceBinding[] nsList = namespaceList;
            for (int i = 0; i < nsList.length; i++) {
                if (nsList[i].equals(nscode)) {
                    return;
                }
                if (nsList[i].getPrefix().equals(nscode.getPrefix())) {
                    if (nsList[i].getURI().isEmpty()) {
                        // this is an undeclaration; replace it with the new declaration
                        nsList[i] = nscode;
                        return;
                    } else if (externalCall) {
                        throw new IllegalArgumentException("New namespace conflicts with existing namespace binding");
                    } else {
                        return;
                    }
                }
            }
            int len = nsList.length;
            NamespaceBinding[] ns2 = new NamespaceBinding[len + 1];
            System.arraycopy(nsList, 0, ns2, 0, len);
            ns2[len] = nscode;
            namespaceList = ns2;
        }
    }


    /**
     * Replace the string-value of this node
     *
     * @param stringValue the new string value
     */

    public void replaceStringValue(/*@NotNull*/ CharSequence stringValue) {
        if (stringValue.length() == 0) {
            setChildren(null);
        } else {
            TextImpl text = new TextImpl(stringValue.toString());
            text.setRawParent(this);
            setChildren(text);
        }
    }

    /**
     * Add an attribute to this element node.
     * <p/>
     * <p>If this node is not an element, or if the supplied node is not an attribute, the method
     * takes no action. If the element already has an attribute with this name, the method
     * throws an exception.</p>
     * <p/>
     * <p>This method does not perform any namespace fixup. It is the caller's responsibility
     * to ensure that any namespace prefix used in the name of the attribute (or in its value
     * if it has a namespace-sensitive type) is declared on this element.</p>
     *
     * @param nameCode   the name of the new attribute
     * @param attType    the type annotation of the new attribute
     * @param value      the string value of the new attribute
     * @param properties properties including IS_ID and IS_IDREF properties
     * @throws IllegalStateException if the element already has an attribute with the given name.
     */

    public void addAttribute(/*@NotNull*/ NodeName nameCode, SimpleType attType, /*@NotNull*/ CharSequence value, int properties) {
        if (attributeList == null || attributeList.getLength() == 0) {
            attributeList = new AttributeCollectionImpl(getConfiguration());
        }
        AttributeCollectionImpl atts = (AttributeCollectionImpl) attributeList;
        int index = atts.findByNodeName(nameCode);
        if (index == -1) {
            atts.addAttribute(nameCode, attType, value.toString(), ExplicitLocation.UNKNOWN_LOCATION, 0);
        } else {
            throw new IllegalStateException(
                    "Cannot add an attribute to an element as it already has an attribute with the specified name");
        }
        if (!nameCode.hasURI("")) {
            // The new attribute name is in a namespace
            NamespaceBinding binding = nameCode.getNamespaceBinding();
            String prefix = binding.getPrefix();
            String uc = getURIForPrefix(prefix, false);
            if (uc == null) {
                // The namespace is not already declared on the element
                addNamespace(binding, true);
            } else if (!uc.equals(binding.getURI())) {
                throw new IllegalStateException(
                        "Namespace binding of new name conflicts with existing namespace binding");
            }
        }
        if ((properties & ReceiverOptions.IS_ID) != 0) {
            DocumentImpl root = getPhysicalRoot();
            if (root != null) {
                root.registerID(this, Whitespace.trim(value));
            }
        }
    }

    /**
     * Remove an attribute from this element node
     * <p/>
     * <p>If this node is not an element, or if the specified node is not an attribute
     * of this element, this method takes no action.</p>
     * <p/>
     * <p>The attribute object itself becomes unusable; any attempt to use this attribute object,
     * or any other object representing the same node, is likely to result in an exception.</p>
     *
     * @param attribute the attribute node to be removed
     */

    public void removeAttribute(/*@NotNull*/ NodeInfo attribute) {
        if (!(attribute instanceof AttributeImpl)) {
            return; // no action
        }
        AttributeCollectionImpl atts = (AttributeCollectionImpl) getAttributeList();
        int index = ((AttributeImpl) attribute).getSiblingPosition();
        if (index >= 0 && atts.isId(index)) {
            DocumentImpl root = getPhysicalRoot();
            root.deregisterID(atts.getValue(index));
        }
        atts.removeAttribute(index);
        ((AttributeImpl) attribute).setRawParent(null);
    }


    /**
     * Remove type information from this node (and its ancestors, recursively).
     * This method implements the upd:removeType() primitive defined in the XQuery Update specification
     */

    public void removeTypeAnnotation() {
        if (getSchemaType() != Untyped.getInstance()) {
            type = AnyType.getInstance();
            getRawParent().removeTypeAnnotation();
        }
    }

    /**
     * Set the namespace declarations for the element
     *
     * @param namespaces     the list of namespace codes
     * @param namespacesUsed the number of entries in the list that are used
     */

    public void setNamespaceDeclarations(NamespaceBinding[] namespaces, int namespacesUsed) {
        namespaceList = new NamespaceBinding[namespacesUsed];
        System.arraycopy(namespaces, 0, namespaceList, 0, namespacesUsed);
    }

    /**
     * Get the namespace URI corresponding to a given prefix. Return null
     * if the prefix is not in scope.
     *
     * @param prefix     the namespace prefix. May be the zero-length string, indicating
     *                   that there is no prefix. This indicates either the default namespace or the
     *                   null namespace, depending on the value of useDefault.
     * @param useDefault true if the default namespace is to be used when the
     *                   prefix is "". If false, the method returns "" when the prefix is "".
     * @return the uri for the namespace, or null if the prefix is not in scope.
     *         The "null namespace" is represented by the pseudo-URI "".
     */

    /*@Nullable*/
    public String getURIForPrefix(/*@NotNull*/ String prefix, boolean useDefault) {
        if (prefix.equals("xml")) {
            return NamespaceConstant.XML;
        }
        if (prefix.isEmpty() && !useDefault) {
            return NamespaceConstant.NULL;
        }

        if (namespaceList != null) {
            for (NamespaceBinding aNamespaceList : namespaceList) {
                if (aNamespaceList.getPrefix().equals(prefix)) {
                    String uri = aNamespaceList.getURI();
                    return uri.isEmpty() && prefix.length() != 0 ? null : uri;
                }
            }
        }
        NodeInfo next = getRawParent();
        if (next.getNodeKind() == Type.DOCUMENT) {
            return prefix.isEmpty() ? NamespaceConstant.NULL : null;
        } else {
            return ((ElementImpl) next).getURIForPrefix(prefix, useDefault);
        }
    }

    /**
     * Get an iterator over all the prefixes declared in this namespace context. This will include
     * the default namespace (prefix="") and the XML namespace where appropriate
     */

    /*@Nullable*/
    public Iterator<String> iteratePrefixes() {
        return new Iterator<String>() {
            /*@Nullable*/ private NamePool pool = null;
            private Iterator<NamespaceBinding> iter = NamespaceIterator.iterateNamespaces(ElementImpl.this);

            public boolean hasNext() {
                return pool == null || iter.hasNext();
            }

            public String next() {
                if (pool == null) {
                    pool = getNamePool();
                    return "xml";
                } else {
                    return iter.next().getPrefix();
                }
            }

            public void remove() {
                throw new UnsupportedOperationException("remove");
            }
        };
    }

    /**
     * Search the in-scope namespaces to see whether a given namespace is in scope.
     *
     * @param uri The URI to be matched.
     * @return true if the namespace is in scope
     */

    /*@Nullable*/
    public boolean isInScopeNamespace(/*@NotNull*/ String uri) {
        if (uri.equals(NamespaceConstant.XML)) {
            return true;
        }
        for (NamespaceBinding b : namespaceList) {
            if (b.getURI().equals(uri)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get all namespace undeclarations and undeclarations defined on this element.
     *
     * @param buffer If this is non-null, and the result array fits in this buffer, then the result
     *               may overwrite the contents of this array, to avoid the cost of allocating a new array on the heap.
     * @return An array of NamespaceBinding objects representing the namespace declarations and undeclarations present on
     *         this element. For a node other than an element, return null.
     *         The XML namespace is never included in the list. If the supplied array is larger than required,
     *         then the first unused entry will be set to null.
     *         <p/>
     *         <p>For a node other than an element, the method returns null.</p>
     */

    public NamespaceBinding[] getDeclaredNamespaces(NamespaceBinding[] buffer) {
        return namespaceList == null ? NamespaceBinding.EMPTY_ARRAY : namespaceList;
    }

    /**
     * Ensure that a child element being inserted into a tree has the right namespace declarations.
     * Redundant declarations should be removed. If the child is in the null namespace but the parent has a default
     * namespace, xmlns="" should be added. If inherit is false, namespace undeclarations should be added for all
     * namespaces that are declared on the parent but not on the child.
     *
     * @param inherit true if the child is to inherit the inscope namespaces of its new parent
     */

    protected void fixupInsertedNamespaces(boolean inherit) {
        if (getRawParent().getNodeKind() == Type.DOCUMENT) {
            return;
        }

        Set<NamespaceBinding> childNamespaces = new HashSet<NamespaceBinding>();
        if (namespaceList != null) {
            childNamespaces.addAll(Arrays.asList(namespaceList));
        }

        NamespaceResolver inscope = new InscopeNamespaceResolver(getRawParent());

        // If the child is in the null namespace but the parent has a default namespace, xmlns="" should be added.

        if (getURI().isEmpty() && inscope.getURIForPrefix("", true).length() != 0) {
            childNamespaces.add(NamespaceBinding.DEFAULT_UNDECLARATION);
        }

        // Namespaces present on the parent but not on the child should be undeclared (if requested)

        if (!inherit) {
            Iterator it = inscope.iteratePrefixes();
            while (it.hasNext()) {
                String prefix = (String) it.next();
                if (!prefix.equals("xml")) {
                    boolean found = false;
                    if (namespaceList != null) {
                        for (NamespaceBinding aNamespaceList : namespaceList) {
                            if (aNamespaceList.getPrefix().equals(prefix)) {
                                found = true;
                                break;
                            }
                        }
                    }
                    if (!found) {
                        childNamespaces.add(new NamespaceBinding(prefix, ""));
                    }
                }
            }
        }

        // Redundant namespaces should be removed

        if (namespaceList != null) {
            for (NamespaceBinding nscode : namespaceList) {
                String prefix = nscode.getPrefix();
                String uri = nscode.getURI();
                String parentUri = inscope.getURIForPrefix(prefix, true);
                if (parentUri != null && parentUri.equals(uri)) {
                    // the namespace declaration is redundant
                    childNamespaces.remove(nscode);
                }
            }
        }
        NamespaceBinding[] n2 = new NamespaceBinding[childNamespaces.size()];
        int j = 0;
        for (NamespaceBinding childNamespace : childNamespaces) {
            n2[j++] = childNamespace;
        }
        namespaceList = n2;
    }

    /**
     * Get the attribute list for this element.
     *
     * @return The attribute list. This will not include any
     *         namespace attributes. The attribute names will be in expanded form, with prefixes
     *         replaced by URIs
     */

    public AttributeCollection getAttributeList() {
        return attributeList == null ? AttributeCollectionImpl.EMPTY_ATTRIBUTE_COLLECTION : attributeList;
    }

    /**
     * Get the namespace list for this element.
     *
     * @return The raw namespace list, as an array of name codes
     */

    /*@Nullable*/
    public NamespaceBinding[] getNamespaceList() {
        return namespaceList;
    }

    /**
     * Get the value of a given attribute of this node
     *
     * @param uri       the namespace URI of the attribute name, or "" if the attribute is not in a namepsace
     * @param localName the local part of the attribute name
     * @return the attribute value if it exists or null if not
     */

    /*@Nullable*/
    public String getAttributeValue(/*@NotNull*/ String uri, /*@NotNull*/ String localName) {
        return attributeList == null ? null : attributeList.getValue(uri, localName);
    }

    /**
     * Determine whether this node has the is-id property
     *
     * @return true if the node is an ID
     */

    public boolean isId() {
        // This is an approximation. For a union type, we check that the actual value is a valid NCName,
        // but we don't check that it was validated against the member type of the union that is an ID type.
        try {
            SchemaType type = getSchemaType();
            return type.getFingerprint() == StandardNames.XS_ID ||
                    type.isIdType() && NameChecker.isValidNCName(getStringValueCS());
        } catch (MissingComponentException e) {
            return false;
        }
    }
}

