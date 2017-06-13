////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.dom;

import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.NamePool;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.pattern.NameTest;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.Untyped;
import org.w3c.dom.*;

/**
 * This class is an implementation of the DOM Element class that wraps a Saxon NodeInfo
 * representation of an element node.
 * <p/>
 * The class provides read-only access to the tree; methods that request updates all fail
 * with an UnsupportedOperationException.
 * <p/>
 * Note that contrary to the DOM specification, this implementation does not expose namespace
 * declarations as attributes.
 */

public class ElementOverNodeInfo extends NodeOverNodeInfo implements Element {

    /**
     * The name of the element (DOM interface).
     */

    public String getTagName() {
        return node.getDisplayName();
    }

    /**
     * Returns a <code>NodeList</code> of all descendant <code>Elements</code>
     * with a given tag name, in document order.
     *
     * @param name The name of the tag to match on. The special value "*"
     *             matches all tags.
     * @return A list of matching <code>Element</code> nodes.
     */
    public NodeList getElementsByTagName(String name) {
        return DocumentOverNodeInfo.getElementsByTagName(node, name);
    }

    /**
     * Returns a <code>NodeList</code> of all the descendant
     * <code>Elements</code> with a given local name and namespace URI in
     * document order.
     *
     * @param namespaceURI The namespace URI of the elements to match on. The
     *                     special value "*" matches all namespaces.
     * @param localName    The local name of the elements to match on. The
     *                     special value "*" matches all local names.
     * @return A new <code>NodeList</code> object containing all the matched
     *         <code>Elements</code>.
     * @throws org.w3c.dom.DOMException NOT_SUPPORTED_ERR: May be raised if the implementation does not
     *                                  support the feature <code>"XML"</code> and the language exposed
     *                                  through the Document does not support XML Namespaces (such as [<a href='http://www.w3.org/TR/1999/REC-html401-19991224/'>HTML 4.01</a>]).
     * @since DOM Level 2
     */
    public NodeList getElementsByTagNameNS(String namespaceURI, String localName) throws DOMException {
        return DocumentOverNodeInfo.getElementsByTagNameNS(node, namespaceURI, localName);
    }

    /**
     * Retrieves an attribute value by name.
     * This implementation does not expose namespace nodes as attributes.
     *
     * @param name The QName of the attribute to retrieve.
     * @return The <code>Attr</code> value as a string, or the empty string if
     *         that attribute does not have a specified or default value.
     */

    public String getAttribute(String name) {
        AxisIterator atts = node.iterateAxis(AxisInfo.ATTRIBUTE);
        while (true) {
            NodeInfo att = atts.next();
            if (att == null) {
                return "";
            }
            if (att.getDisplayName().equals(name)) {
                String val = att.getStringValue();
                if (val == null) return "";
                return val;
            }
        }
    }

    /**
     * Retrieves an attribute node by name.
     * This implementation does not expose namespace nodes as attributes.
     * <br> To retrieve an attribute node by qualified name and namespace URI,
     * use the <code>getAttributeNodeNS</code> method.
     *
     * @param name The name (<code>nodeName</code> ) of the attribute to
     *             retrieve.
     * @return The <code>Attr</code> node with the specified name (
     *         <code>nodeName</code> ) or <code>null</code> if there is no such
     *         attribute.
     */

    public Attr getAttributeNode(String name) {
        AxisIterator atts = node.iterateAxis(AxisInfo.ATTRIBUTE);
        while (true) {
            NodeInfo att = atts.next();
            if (att == null) {
                return null;
            }
            if (att.getDisplayName().equals(name)) {
                return (AttrOverNodeInfo) wrap(att);
            }
        }
    }

    /**
     * Adds a new attribute node. Always fails
     *
     * @throws org.w3c.dom.DOMException NO_MODIFICATION_ALLOWED_ERR: Raised if this node is readonly.
     */

    public Attr setAttributeNode(Attr newAttr) throws DOMException {
        disallowUpdate();
        return null;
    }

    /**
     * Removes the specified attribute. Always fails
     *
     * @throws org.w3c.dom.DOMException NO_MODIFICATION_ALLOWED_ERR: Raised if this node is readonly.
     */

    public void removeAttribute(String oldAttr) throws DOMException {
        disallowUpdate();
    }

    /**
     * Removes the specified attribute node. Always fails
     *
     * @throws org.w3c.dom.DOMException NO_MODIFICATION_ALLOWED_ERR: Raised if this node is readonly.
     */

    public Attr removeAttributeNode(Attr oldAttr) throws DOMException {
        disallowUpdate();
        return null;
    }


    /**
     * Retrieves an attribute value by local name and namespace URI.
     * This implementation does not expose namespace nodes as attributes.
     *
     * @param namespaceURI The  namespace URI of the attribute to retrieve.
     * @param localName    The  local name of the attribute to retrieve.
     * @return The <code>Attr</code> value as a string, or the empty string if
     *         that attribute does not have a specified or default value.
     * @since DOM Level 2
     */

    public String getAttributeNS(String namespaceURI, String localName) {
        String val = Navigator.getAttributeValue(node, (namespaceURI == null ? "" : namespaceURI), localName);
        if (val == null) return "";
        return val;
    }

    /**
     * Adds a new attribute. Always fails
     *
     * @param name  The name of the attribute to create or alter.
     * @param value Value to set in string form.
     * @throws org.w3c.dom.DOMException INVALID_CHARACTER_ERR: Raised if the specified name is not an XML
     *                                  name according to the XML version in use specified in the
     *                                  <code>Document.xmlVersion</code> attribute.
     *                                  <br>NO_MODIFICATION_ALLOWED_ERR: Raised if this node is readonly.
     */
    public void setAttribute(String name, String value) throws DOMException {
        disallowUpdate();
    }

    /**
     * Adds a new attribute. Always fails.
     *
     * @param namespaceURI  The  namespace URI of the attribute to create or
     *                      alter.
     * @param qualifiedName The  qualified name of the attribute to create or
     *                      alter.
     * @param value         The value to set in string form.
     * @throws org.w3c.dom.DOMException NO_MODIFICATION_ALLOWED_ERR: Raised if this node is readonly.
     */

    public void setAttributeNS(String namespaceURI,
                               String qualifiedName,
                               String value)
            throws DOMException {
        disallowUpdate();
    }

    /**
     * Removes an attribute by local name and namespace URI. Always fails
     *
     * @throws org.w3c.dom.DOMException NO_MODIFICATION_ALLOWED_ERR: Raised if this node is readonly.
     * @since DOM Level 2
     */

    public void removeAttributeNS(String namespaceURI,
                                  String localName)
            throws DOMException {
        disallowUpdate();
    }

    /**
     * Retrieves an <code>Attr</code> node by local name and namespace URI.
     * This implementation does not expose namespace nodes as attributes.
     *
     * @param namespaceURI The  namespace URI of the attribute to retrieve.
     * @param localName    The  local name of the attribute to retrieve.
     * @return The <code>Attr</code> node with the specified attribute local
     *         name and namespace URI or <code>null</code> if there is no such
     *         attribute.
     * @since DOM Level 2
     */

    public Attr getAttributeNodeNS(String namespaceURI, String localName) {
        NamePool pool = node.getConfiguration().getNamePool();
        NameTest test = new NameTest(Type.ATTRIBUTE, namespaceURI, localName, pool);
        AxisIterator atts = node.iterateAxis(AxisInfo.ATTRIBUTE, test);
        return (Attr) wrap(atts.next());
    }

    /**
     * Add a new attribute. Always fails.
     *
     * @param newAttr The <code>Attr</code> node to add to the attribute list.
     * @return If the <code>newAttr</code> attribute replaces an existing
     *         attribute with the same  local name and  namespace URI , the
     *         replaced <code>Attr</code> node is returned, otherwise
     *         <code>null</code> is returned.
     * @throws org.w3c.dom.DOMException <br> NO_MODIFICATION_ALLOWED_ERR: Raised if this node is readonly.
     * @since DOM Level 2
     */

    public Attr setAttributeNodeNS(Attr newAttr)
            throws DOMException {
        disallowUpdate();
        return null;
    }

    /**
     * Returns <code>true</code> when an attribute with a given name is
     * specified on this element or has a default value, <code>false</code>
     * otherwise.
     * This implementation does not expose namespace nodes as attributes.
     *
     * @param name The name of the attribute to look for.
     * @return <code>true</code> if an attribute with the given name is
     *         specified on this element or has a default value, <code>false</code>
     *         otherwise.
     * @since DOM Level 2
     */

    public boolean hasAttribute(String name) {
        AxisIterator atts = node.iterateAxis(AxisInfo.ATTRIBUTE);
        while (true) {
            NodeInfo att = atts.next();
            if (att == null) {
                return false;
            }
            if (att.getDisplayName().equals(name)) {
                return true;
            }
        }
    }

    /**
     * Returns <code>true</code> when an attribute with a given local name
     * and namespace URI is specified on this element or has a default value,
     * <code>false</code> otherwise.
     * This implementation does not expose namespace nodes as attributes.
     *
     * @param namespaceURI The  namespace URI of the attribute to look for.
     * @param localName    The  local name of the attribute to look for.
     * @return <code>true</code> if an attribute with the given local name and
     *         namespace URI is specified or has a default value on this element,
     *         <code>false</code> otherwise.
     * @since DOM Level 2
     */

    public boolean hasAttributeNS(String namespaceURI, String localName) {
        return (Navigator.getAttributeValue(node, (namespaceURI == null ? "" : namespaceURI), localName) != null);
    }

    /**
     * Mark an attribute as an ID. Always fails.
     *
     * @throws DOMException
     */

    public void setIdAttribute(String name,
                               boolean isId)
            throws DOMException {
        disallowUpdate();
    }

    /**
     * Mark an attribute as an ID. Always fails.
     *
     * @throws DOMException
     */

    public void setIdAttributeNS(String namespaceURI,
                                 String localName,
                                 boolean isId)
            throws DOMException {
        disallowUpdate();
    }

    /**
     * Mark an attribute as an ID. Always fails.
     *
     * @throws DOMException
     */

    public void setIdAttributeNode(Attr idAttr,
                                   boolean isId)
            throws DOMException {
        disallowUpdate();
    }


    /**
     * Get the schema type information for this node.
     *
     * @return the type information. Returns null for an untyped node.
     */

    /*@Nullable*/
    public TypeInfo getSchemaTypeInfo() {
        SchemaType type = node.getSchemaType();
        if (type == null || Untyped.getInstance().equals(type) || BuiltInAtomicType.UNTYPED_ATOMIC.equals(type)) {
            return null;
        }
        return new TypeInfoImpl(node.getConfiguration(), type);
    }

}

