////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.tiny;

import net.sf.saxon.event.BuilderMonitor;
import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NameTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;
import net.sf.saxon.type.Type;

/**
 * Monitor construction of a TinyTree. This allows a marker to be set during tree construction, in such a way
 * that the node corresponding to the marker can be retrieved at the end of tree construction. This is used in the
 * implementation of the XSLT 3.0 snapshot function.
 */
public class TinyBuilderMonitor extends BuilderMonitor {

    private TinyBuilder builder;
    private int mark = -1;
    private int markedNodeNr = -1;
    private int markedAttribute = -1;
    private int markedNamespace = -1;

    public TinyBuilderMonitor(/*@NotNull*/ TinyBuilder builder) {
        super(builder);
        this.builder = builder;
    }

    public void markNextNode(int nodeKind) {
        mark = nodeKind;
    }

    @Override
    public void markAttributeNode(NodeInfo origin) {
        int[] atts = builder.getTree().getAttributeNameCodeArray();
        int used = builder.getTree().getNumberOfAttributes();
        int fp = origin.getFingerprint();
        for (int i=used-1; i>=0; i--) {
            if ((atts[i] & NamePool.FP_MASK) == fp) {
                markedAttribute = i;
                return;
            }
        }
        throw new IllegalStateException("Selected attribute not found");
    }

    @Override
    public void markNamespaceNode(NodeInfo origin) {
        NamespaceBinding[] atts = builder.getTree().getNamespaceBindings();
        int used = builder.getTree().getNumberOfNamespaces();
        String local = origin.getLocalPart();
        for (int i=used-1; i>=0; i--) {
            if (atts[i].getPrefix().equals(local)) {
                markedNamespace = i;
                return;
            }
        }
        throw new IllegalStateException("Selected namespace not found");
    }

    public void startDocument(int properties) throws XPathException {
        if (mark == Type.DOCUMENT) {
            markedNodeNr = builder.getTree().getNumberOfNodes();
        }
        mark = -1;
        super.startDocument(properties);
    }

    public void startElement(NodeName nameCode, SchemaType typeCode, Location location, int properties) throws XPathException {
        if (mark == Type.ELEMENT) {
            markedNodeNr = builder.getTree().getNumberOfNodes();
        }
        mark = -1;
        super.startElement(nameCode, typeCode, location, properties);
    }

    public void characters(CharSequence chars, Location locationId, int properties) throws XPathException {
        if (mark == Type.TEXT) {
            markedNodeNr = builder.getTree().getNumberOfNodes();
        }
        mark = -1;
        super.characters(chars, locationId, properties);
    }

    public void comment(CharSequence chars, Location locationId, int properties) throws XPathException {
        if (mark == Type.COMMENT) {
            markedNodeNr = builder.getTree().getNumberOfNodes();
        }
        mark = -1;
        super.comment(chars, locationId, properties);
    }

    public void processingInstruction(String target, CharSequence data, Location locationId, int properties) throws XPathException {
        if (mark == Type.PROCESSING_INSTRUCTION) {
            markedNodeNr = builder.getTree().getNumberOfNodes();
        }
        mark = -1;
        super.processingInstruction(target, data, locationId, properties);
    }

    public void attribute(NodeName nameCode, SimpleType typeCode, CharSequence value, Location locationId, int properties) throws XPathException {
        if (mark == Type.ATTRIBUTE) {
            markedAttribute = builder.getTree().getNumberOfAttributes();
        }
        mark = -1;
        super.attribute(nameCode, typeCode, value, locationId, properties);
    }

    public void namespace(NamespaceBindingSet namespaceBindings, int properties) throws XPathException {
        if (mark == Type.NAMESPACE) {
            markedNamespace = builder.getTree().getNumberOfNamespaces();
        }
        mark = -1;
        super.namespace(namespaceBindings, properties);
    }

    /*@Nullable*/
    public NodeInfo getMarkedNode() {
        if (markedNodeNr != -1) {
            return builder.getTree().getNode(markedNodeNr);
        } else if (markedAttribute != -1) {
            return builder.getTree().getAttributeNode(markedAttribute);
        } else if (markedNamespace != -1) {
            NamespaceBinding nscode = builder.getTree().namespaceBinding[markedNamespace];
            NamePool pool = builder.getConfiguration().getNamePool();
            String prefix = nscode.getPrefix();
            NodeInfo parent = builder.getTree().getNode(builder.getTree().namespaceParent[markedNamespace]);
            NameTest test = new NameTest(Type.NAMESPACE, "", prefix, pool);
            AxisIterator iter = parent.iterateAxis(AxisInfo.NAMESPACE, test);
            return iter.next();
        } else {
            return null;
        }
    }
}

