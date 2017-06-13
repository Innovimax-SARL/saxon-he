////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.event.Receiver;
import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.wrapper.AbstractVirtualNode;
import net.sf.saxon.type.Type;

import javax.xml.transform.Source;

/**
 * The class DocumentInfo is retained in Saxon 9.7 to preserve a level of backwards compatibility
 * for applications that use the method {@link net.sf.saxon.Configuration#buildDocument(Source)}
 * method to construct a tree. In earlier releases it was an interface implemented by all document
 * nodes; from 9.7 it is a wrapper object around the NodeInfo object that represents the actual
 * document node
 */
public class DocumentInfo extends AbstractVirtualNode {

    public DocumentInfo(NodeInfo node) {
        if (node.getNodeKind() != Type.DOCUMENT) {
            throw new IllegalArgumentException("DocumentInfo must only be used for document nodes");
        }
        this.node = node;
        this.parent = null;
        this.docWrapper = node.getTreeInfo();
    }

    @Override
    public void copy(Receiver out, int copyOptions, Location locationId) throws XPathException {
        node.copy(out, copyOptions, locationId);
    }

    @Override
    public NodeInfo getParent() {
        return null;
    }

    @Override
    public AxisIterator iterateAxis(byte axisNumber) {
        return node.iterateAxis(axisNumber);
    }
}

