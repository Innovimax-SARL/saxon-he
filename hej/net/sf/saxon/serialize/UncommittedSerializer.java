////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import net.sf.saxon.event.ProxyReceiver;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.SaxonOutputKeys;
import net.sf.saxon.lib.SerializerFactory;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.Whitespace;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * This class is used when the decision on which serialization method to use has to be delayed until the first
 * element is read. It buffers comments and processing instructions until that happens; then when the first
 * element arrives it creates a real serialization pipeline and uses that for future output.
 *
 * @author Michael H. Kay
 */

public class UncommittedSerializer extends ProxyReceiver {

    private boolean committed = false;
    private List<PendingNode> pending = null;
    private Result finalResult;
    private Properties outputProperties;
    private CharacterMapIndex charMapIndex;

    /**
     * Create an uncommitted Serializer
     *
     * @param finalResult      the output destination
     * @param next             the next receiver in the pipeline
     * @param outputProperties the serialization properties
     * @param charMap          the index of named character maps
     */

    public UncommittedSerializer(Result finalResult, Receiver next, Properties outputProperties, CharacterMapIndex charMap) {
        super(next);
        this.finalResult = finalResult;
        this.outputProperties = outputProperties;
        this.charMapIndex = charMap;
    }

    public void open() throws XPathException {
        committed = false;
    }

    /**
     * End of document
     */

    public void close() throws XPathException {
        // empty output: must send a beginDocument()/endDocument() pair to the content handler
        if (!committed) {
            switchToMethod("xml");
        }
        getUnderlyingReceiver().close();
    }

    /**
     * Produce character output using the current Writer. <BR>
     */

    public void characters(CharSequence chars, Location locationId, int properties) throws XPathException {
        if (committed) {
            getUnderlyingReceiver().characters(chars, locationId, properties);
        } else {
            if (pending == null) {
                pending = new ArrayList<PendingNode>(10);
            }
            PendingNode node = new PendingNode();
            node.kind = Type.TEXT;
            node.name = null;
            node.content = chars.toString();    // needs to be immutable
            node.locationId = locationId.saveLocation();
            node.properties = properties;
            pending.add(node);
            if (!Whitespace.isWhite(chars)) {
                switchToMethod("xml");
            }
        }
    }

    /**
     * Processing Instruction
     */

    public void processingInstruction(String target, CharSequence data, Location locationId, int properties) throws XPathException {
        if (committed) {
            getUnderlyingReceiver().processingInstruction(target, data, locationId, properties);
        } else {
            if (pending == null) {
                pending = new ArrayList<PendingNode>(10);
            }
            PendingNode node = new PendingNode();
            node.kind = Type.PROCESSING_INSTRUCTION;
            node.name = target;
            node.content = data;
            node.locationId = locationId;
            node.properties = properties;
            pending.add(node);
        }
    }

    /**
     * Output a comment
     */

    public void comment(CharSequence chars, Location locationId, int properties) throws XPathException {
        if (committed) {
            getUnderlyingReceiver().comment(chars, locationId, properties);
        } else {
            if (pending == null) {
                pending = new ArrayList<PendingNode>(10);
            }
            PendingNode node = new PendingNode();
            node.kind = Type.COMMENT;
            node.name = null;
            node.content = chars;
            node.locationId = locationId;
            node.properties = properties;
            pending.add(node);
        }
    }

    /**
     * Output an element start tag. <br>
     * This can only be called once: it switches to a substitute output generator for XML, XHTML, or HTML,
     * depending on the element name.
     *  @param elemName   The element name (tag)
     * @param typeCode   The type annotation
     * @param location
     * @param properties Bit field holding special properties of the element
     */

    public void startElement(NodeName elemName, SchemaType typeCode, Location location, int properties) throws XPathException {
        if (!committed) {
            String name = elemName.getLocalPart();
            String uri = elemName.getURI();
            if (name.equalsIgnoreCase("html") && uri.isEmpty()) {
                switchToMethod("html");
            } else if (name.equals("html") && uri.equals(NamespaceConstant.XHTML)) {
                String version = outputProperties.getProperty(SaxonOutputKeys.STYLESHEET_VERSION);
                if ("10".equals(version)) {
                    switchToMethod("xml");
                } else {
                    switchToMethod("xhtml");
                }
            } else {
                switchToMethod("xml");
            }
        }
        getUnderlyingReceiver().startElement(elemName, typeCode, location, properties);
    }

    /**
     * Switch to a specific emitter once the output method is known
     *
     * @param method the method to switch to (xml, html, xhtml)
     */

    private void switchToMethod(String method) throws XPathException {
        Properties newProperties = new Properties(outputProperties);
        newProperties.setProperty(OutputKeys.METHOD, method);
        SerializerFactory sf = getConfiguration().getSerializerFactory();
        Receiver target = sf.getReceiver(finalResult, getPipelineConfiguration(), newProperties, charMapIndex);
        committed = true;
        target.open();
        target.startDocument(0);
        if (pending != null) {
            for (PendingNode node : pending) {
                switch (node.kind) {
                    case Type.COMMENT:
                        target.comment(node.content, node.locationId, node.properties);
                        break;
                    case Type.PROCESSING_INSTRUCTION:
                        target.processingInstruction(node.name, node.content, node.locationId, node.properties);
                        break;
                    case Type.TEXT:
                        target.characters(node.content, node.locationId, node.properties);
                        break;
                }
            }
            pending = null;
        }
        setUnderlyingReceiver(target);
    }

    /**
     * Append an arbitrary item (node or atomic value) to the output
     *
     * @param item           the item to be appended
     * @param locationId     the location of the calling instruction, for diagnostics
     * @param copyNamespaces if the item is an element node, this indicates whether its namespaces
     *                       need to be copied. Values are {@link NodeInfo#ALL_NAMESPACES},
     *                       {@link NodeInfo#LOCAL_NAMESPACES}, {@link NodeInfo#NO_NAMESPACES}
     */
    @Override
    public void append(Item item, Location locationId, int copyNamespaces) throws XPathException {
        if (item instanceof NodeInfo) {
            ((NodeInfo)item).copy(this, NodeInfo.ALL_NAMESPACES, locationId);
        } else {
            // Need to do something more intelligent here
            characters(item.getStringValueCS(), locationId, 0);
        }
    }

    /**
     * A text, comment, or PI node that hasn't been output yet because we don't yet know what output
     * method to use
     */

    private static final class PendingNode {
        int kind;
        String name;
        CharSequence content;
        int properties;
        Location locationId;
    }

}

