////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.Configuration;
import net.sf.saxon.tree.util.DocumentNumberAllocator;

import java.util.*;

/**
 * A generic (model-independent) implementation of the DocumentInfo interface, suitable for use with all
 * tree models where the object representing the document node does not itself act as the DocumentInfo
 * implementation
 */

public class GenericTreeInfo implements TreeInfo {

    private Configuration config;
    private NodeInfo root;
    private String systemId;
    private Map<String, Object> userData;
    private long documentNumber = -1;

    /**
     * Create the TreeInfo
     * @param config the Saxon Configuration
     */

    public GenericTreeInfo(Configuration config) {
        this.config = config;
    }

    /**
     * Create the TreeInfo
     * @param config the Saxon Configuration
     * @param root the root node
     * @throws java.lang.IllegalArgumentException if the supplied node is not parentless
     */

    public GenericTreeInfo(Configuration config, NodeInfo root) {
        this.config = config;
        setRootNode(root);
    }

    /**
     * Set the configuration (containing the name pool used for all names in this document)
     *
     * @param config the configuration
     */

    public void setConfiguration(Configuration config) {
        this.config = config;
    }

    /**
     * Get the Configuration to which this document belongs
     *
     * @return the configuration
     */
    public Configuration getConfiguration() {
        return config;
    }

    /**
     * Set the root node of the tree
     * @param root the root node (not necessarily a document node
     * @throws java.lang.IllegalArgumentException if the supplied node is not parentless
     */

    public void setRootNode(NodeInfo root) {
        if (root.getParent() != null) {
            throw new IllegalArgumentException("The root node of a tree must be parentless");
        }
        this.root = root;
    }

    /**
     * Get the NodeInfo object representing the root of the tree (not necessarily a document node)
     *
     * @return the root node
     */
    public NodeInfo getRootNode() {
        return root;
    }

    /**
     * Set the systemId of the document node (for most implementations, this is likely to be the systemId
     * of the entire tree
     * @param systemId the system ID
     */

    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    /**
     * Get the systemId of the document node
     * @return the system ID
     */

    public String getSystemId() {
        return systemId;
    }

    /**
     * Get the Public ID of the entity containing the node.
     *
     * @return null (always)
     * @since 9.7
     */
    public String getPublicId() {
        return null;
    }

    /**
     * Get the document number, which identifies this document uniquely within a Configuration
     * @return the document number
     */

    public long getDocumentNumber() {
        if (documentNumber == -1) {
            DocumentNumberAllocator dna = config.getDocumentNumberAllocator();
            synchronized (this) {
                if (documentNumber == -1) {
                    documentNumber = dna.allocateDocumentNumber();
                }
            }
        }
        return documentNumber;
    }

    /**
     * Set the document number, which identifies this document uniquely within a Configuration
     * @param documentNumber the document number allocated to this document
     */

    public synchronized void setDocumentNumber(long documentNumber) {
        this.documentNumber = documentNumber;
    }


    /**
     * Ask whether the document contains any nodes whose type annotation is anything other than
     * UNTYPED
     *
     * @return true if the document contains elements whose type is other than UNTYPED
     */
    public boolean isTyped() {
        return false;
    }

    /**
     * Get the element with a given ID, if any
     *
     * @param id        the required ID value
     * @param getParent true if running the element-with-id() function rather than the id()
     *                  function; the difference is that in the case of an element of type xs:ID, the parent of
     *                  the element should be returned, not the element itself.
     * @return the element with the given ID, or null if there is no such ID
     * present (or if the parser has not notified attributes as being of
     * type ID)
     * @since 8.4. Second argument added in 9.2.
     */
    public NodeInfo selectID(String id, boolean getParent) {
        return null;
    }

    /**
     * Get the list of unparsed entities defined in this document
     *
     * @return an Iterator, whose items are of type String, containing the names of all
     * unparsed entities defined in this document. If there are no unparsed entities or if the
     * information is not available then an empty iterator is returned
     * @since 9.1
     */
    public Iterator<String> getUnparsedEntityNames() {
        List<String> e = Collections.emptyList();
        return e.iterator();
    }

    /**
     * Get the unparsed entity with a given name
     *
     * @param name the name of the entity
     * @return if the entity exists, return an array of two Strings, the first
     * holding the system ID of the entity (as an absolute URI if possible),
     * the second holding the public ID if there is one, or null if not.
     * If the entity does not exist, the method returns null.
     * Applications should be written on the assumption that this array may
     * be extended in the future to provide additional information.
     * @since 8.4
     */
    public String[] getUnparsedEntity(String name) {
        return null;
    }

    /**
     * Set user data on the document node. The user data can be retrieved subsequently
     * using {@link #getUserData}
     *
     * @param key   A string giving the name of the property to be set. Clients are responsible
     *              for choosing a key that is likely to be unique. Must not be null. Keys used internally
     *              by Saxon are prefixed "saxon:".
     * @param value The value to be set for the property. May be null, which effectively
     */
    public void setUserData(String key, Object value) {
        if (userData == null) {
            userData = new HashMap<String, Object>();
        }
        userData.put(key, value);
    }

    /**
     * Get user data held in the document node. This retrieves properties previously set using
     * {@link #setUserData}
     *
     * @param key A string giving the name of the property to be retrieved.
     * @return the value of the property, or null if the property has not been defined.
     */
    public Object getUserData(String key) {
        if (userData == null) {
            return userData;
        } else {
            return userData.get(key);
        }
    }

    public boolean isStreamed() {
        return false;
    }


}

