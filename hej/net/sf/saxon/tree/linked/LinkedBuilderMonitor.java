////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.linked;

import net.sf.saxon.event.BuilderMonitor;
import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NameTest;
import net.sf.saxon.pattern.SameNameTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SimpleType;
import net.sf.saxon.type.Type;

/**
 * Monitor construction of a TinyTree. This allows a marker to be set during tree construction, in such a way
 * that the node corresponding to the marker can be retrieved at the end of tree construction. This is used in the
 * implementation of the XSLT 3.0 snapshot function.
 */
public class LinkedBuilderMonitor extends BuilderMonitor {

    private LinkedTreeBuilder builder;
    private int mark = -1;
    /*@Nullable*/ private NodeInfo markedNode;

    public LinkedBuilderMonitor(/*@NotNull*/ LinkedTreeBuilder builder) {
        super(builder);
        this.builder = builder;
    }

    public void markNextNode(int nodeKind) {
        mark = nodeKind;
    }

    @Override
    public void markAttributeNode(NodeInfo origin) {
        NodeInfo element = builder.getCurrentParentNode();
        markedNode = element.iterateAxis(
                AxisInfo.ATTRIBUTE, new SameNameTest(origin)).next();
        mark = -1;
    }

    @Override
    public void markNamespaceNode(NodeInfo origin) {
        NodeInfo element = builder.getCurrentParentNode();
        NamePool pool = builder.getConfiguration().getNamePool();
        String prefix = origin.getLocalPart();
        markedNode = element.iterateAxis(
                AxisInfo.NAMESPACE, new NameTest(Type.NAMESPACE, "", prefix, pool)).next();
        mark = -1;
    }

    public void startDocument(int properties) throws XPathException {
        super.startDocument(properties);
        if (mark == Type.DOCUMENT) {
            markedNode = builder.getCurrentParentNode();
        }
        mark = -1;
    }


    public void startContent() throws XPathException {
        super.startContent();
        if (mark == Type.ELEMENT) {
            markedNode = builder.getCurrentParentNode();
        }
        mark = -1;
    }

    public void characters(CharSequence chars, Location locationId, int properties) throws XPathException {
        super.characters(chars, locationId, properties);
        if (mark == Type.TEXT) {
            markedNode = builder.getCurrentLeafNode();
        }
        mark = -1;
    }

    public void comment(CharSequence chars, Location locationId, int properties) throws XPathException {
        super.comment(chars, locationId, properties);
        if (mark == Type.COMMENT) {
            markedNode = builder.getCurrentLeafNode();
        }
        mark = -1;
    }

    public void processingInstruction(String target, CharSequence data, Location locationId, int properties) throws XPathException {
        super.processingInstruction(target, data, locationId, properties);
        if (mark == Type.PROCESSING_INSTRUCTION) {
            markedNode = builder.getCurrentLeafNode();
        }
        mark = -1;
    }

    public void attribute(NodeName name, SimpleType typeCode, CharSequence value, Location locationId, int properties)
            throws XPathException {
        super.attribute(name, typeCode, value, locationId, properties);
        if (mark == Type.ATTRIBUTE) {
            NodeInfo element = builder.getCurrentParentNode();
            markedNode = element.iterateAxis(
                    AxisInfo.ATTRIBUTE, new NameTest(Type.ATTRIBUTE, name, builder.getConfiguration().getNamePool())).next();
            mark = -1;
        }
    }

    public void namespace(/*@NotNull*/ NamespaceBindingSet namespaceBindings, int properties) throws XPathException {
        super.namespace(namespaceBindings, properties);
        if (mark == Type.NAMESPACE) {
            for (NamespaceBinding ns : namespaceBindings) {
                NodeInfo element = builder.getCurrentParentNode();
                NamePool pool = builder.getConfiguration().getNamePool();
                String prefix = ns.getPrefix();
                markedNode = element.iterateAxis(
                        AxisInfo.NAMESPACE, new NameTest(Type.NAMESPACE, "", prefix, pool)).next();
                mark = -1;
            }
        }
    }

    /*@Nullable*/
    public NodeInfo getMarkedNode() {
        return markedNode;
    }
}

