////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.jdom;

import net.sf.saxon.Configuration;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.GenericTreeInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.tree.iter.AxisIterator;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;

import java.util.HashMap;
import java.util.List;

/**
 * The tree information for a tree acting as a wrapper for a JDOM Document.
 * @since 9.7: this class no longer implements NodeInfo; the document node itself
 * is now an instance of JDOMNodeWrapper.
 */

public class JDOMDocumentWrapper extends GenericTreeInfo {

    private HashMap<String, Element> idIndex;

    /**
     * Create a Saxon wrapper for a JDOM document
     *
     * @param doc     The JDOM document
     * @param config  The Saxon Configuration
     */

    public JDOMDocumentWrapper(Document doc, Configuration config) {
        super(config);
        setRootNode(wrap(doc));
        setSystemId(doc.getBaseURI());
    }

    /**
     * Wrap a node in the JDOM document.
     *
     * @param node The node to be wrapped. This must be a node in the same document
     *             (the system does not check for this).
     * @return the wrapping NodeInfo object
     */

    public JDOMNodeWrapper wrap(Object node) {
        return JDOMNodeWrapper.makeWrapper(node, this);
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
     * @param getParent
     * @return the element node with the given ID if there is one, otherwise null.
     */

    /*@Nullable*/
    public NodeInfo selectID(String id, boolean getParent) {
        if (idIndex == null) {
            idIndex = new HashMap<String, Element>(100);
            AxisIterator iter = getRootNode().iterateAxis(AxisInfo.DESCENDANT, NodeKindTest.ELEMENT);
            while (true) {
                NodeInfo node = (NodeInfo) iter.next();
                if (node == null) {
                    break;
                }
                Element element = (Element) ((JDOMNodeWrapper) node).node;
                List attributes = element.getAttributes();
                for (int a = 0; a < attributes.size(); a++) {
                    Attribute att = (Attribute) attributes.get(a);
                    if (att.getAttributeType() == Attribute.ID_TYPE) {
                        idIndex.put(att.getValue(), element);
                    }
                }
            }
        }
        Element element = idIndex.get(id);
        return element == null ? null : wrap(element);
    }




}

