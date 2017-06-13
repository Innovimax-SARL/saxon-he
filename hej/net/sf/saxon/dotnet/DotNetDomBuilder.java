////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.dotnet;

import cli.System.Xml.*;
import net.sf.saxon.Configuration;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.NamePool;
import net.sf.saxon.om.NamespaceBinding;
import net.sf.saxon.om.NamespaceBindingSet;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;


/**
 * DotNetDomBuilder is a Receiver that constructs an XmlDocument, the .NET implementation of a DOM
 */

@SuppressWarnings("unused")
public class DotNetDomBuilder implements Receiver {

    protected PipelineConfiguration pipe;
    protected NamePool namePool;
    protected String systemId;
    protected XmlNode currentNode;
    protected XmlDocument document;

    /**
     * Set the namePool in which all name codes can be found
     */

    public void setPipelineConfiguration(/*@NotNull*/ PipelineConfiguration pipe) {
        this.pipe = pipe;
        this.namePool = pipe.getConfiguration().getNamePool();
    }

    /**
     * Get the pipeline configuration used for this document
     */

    /*@NotNull*/
    public PipelineConfiguration getPipelineConfiguration() {
        return pipe;
    }

    /**
     * Get the configuration used for this document
     *
     * @return the Saxon Configuration object
     */

    public Configuration getConfiguration() {
        return pipe.getConfiguration();
    }

    /**
     * Set the System ID
     */

    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    /**
     * Get the System ID
     */

    public String getSystemId() {
        return systemId;
    }


    /**
     * Start of the document.
     */

    public void open() {
    }

    /**
     * End of the document.
     */

    public void close() {
    }

    /**
     * Start of a document node.
     */

    public void startDocument(int properties) throws XPathException {
        if (document == null) {
            document = new XmlDocument();
            currentNode = document;
        }
    }

    /**
     * Notify the end of a document node
     */

    public void endDocument() throws XPathException {
    }

    /**
     * Start of an element.
     */

    public void startElement(NodeName nameCode, SchemaType typeCode, Location location, int properties) throws XPathException {
        if (document == null) {
            document = new XmlDocument();
            currentNode = document;
        }
        String prefix = nameCode.getPrefix();
        String local = nameCode.getLocalPart();
        String uri = nameCode.getURI();
        try {
            XmlElement element = document.CreateElement(prefix, local, uri);
            currentNode.AppendChild(element);
            currentNode = element;
        } catch (Exception err) {
            throw new XPathException(err);
        }
    }

    public void namespace(NamespaceBindingSet namespaceBindings, int properties) throws XPathException {
        try {
            for (NamespaceBinding ns : namespaceBindings) {
                String prefix = ns.getPrefix();
                String uri = ns.getURI();
                XmlElement element = (XmlElement) currentNode;
                if (!uri.equals(NamespaceConstant.XML)) {
                    if (prefix.isEmpty()) {
                        element.SetAttribute("xmlns", uri);
                    } else {
                        // an odd way to do it, but using SetAttribute hits problems
                        XmlAttribute decl = document.CreateAttribute("xmlns", prefix, "http://www.w3.org/2000/xmlns/");
                        decl.set_InnerText(uri);
                        element.get_Attributes().Append(decl);
                    }
                }
            }
        } catch (Exception err) {
            throw new XPathException(err);
        }
    }

    public void attribute(NodeName nameCode, SimpleType typeCode, CharSequence value, Location locationId, int properties)
            throws XPathException {
        String prefix = nameCode.getPrefix();
        String localName = nameCode.getLocalPart();
        String uri = nameCode.getURI();
        try {
            XmlElement element = (XmlElement) currentNode;
            XmlAttribute attr = document.CreateAttribute(prefix, localName, uri);
            attr.set_Value(value.toString());
            element.get_Attributes().Append(attr);
        } catch (Exception err) {
            throw new XPathException(err);
        }
    }

    public void startContent() throws XPathException {
    }

    /**
     * End of an element.
     */

    public void endElement() throws XPathException {
        currentNode.Normalize();
        currentNode = currentNode.get_ParentNode();

    }


    /**
     * Character data.
     */

    public void characters(CharSequence chars, Location locationId, int properties) throws XPathException {
        try {
            XmlText text = document.CreateTextNode(chars.toString());
            currentNode.AppendChild(text);
        } catch (Exception err) {
            throw new XPathException(err);
        }
    }


    /**
     * Handle a processing instruction.
     */

    public void processingInstruction(String target, CharSequence data, Location locationId, int properties)
            throws XPathException {
        try {
            XmlProcessingInstruction pi =
                    document.CreateProcessingInstruction(target, data.toString());
            currentNode.AppendChild(pi);
        } catch (Exception err) {
            throw new XPathException(err);
        }
    }

    /**
     * Handle a comment.
     */

    public void comment(CharSequence chars, Location locationId, int properties) throws XPathException {
        try {
            XmlComment comment = document.CreateComment(chars.toString());
            currentNode.AppendChild(comment);
        } catch (Exception err) {
            throw new XPathException(err);
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
        return false;
    }

    /**
     * Notify an unparsed entity URI.
     *
     * @param name     The name of the unparsed entity
     * @param systemID The system identifier of the unparsed entity
     * @param publicID The public identifier of the unparsed entity
     */

    public void setUnparsedEntity(String name, String systemID, String publicID) throws XPathException {
        // no-op
    }

    /**
     * Set the attachment point, an existing node to which the new data will be added as a subtree
     *
     * @param attachmentPoint the document node, document fragment node, or element node to
     *                        which the new data will be attached
     */

    public void setAttachmentPoint(XmlNode attachmentPoint) {
        currentNode = attachmentPoint;
        if (attachmentPoint instanceof XmlDocument) {
            document = (XmlDocument) attachmentPoint;
        } else {
            document = attachmentPoint.get_OwnerDocument();
        }
    }

    /**
     * Get the constructed document
     *
     * @return the document node of the DOM document
     */

    public XmlDocument getDocumentNode() {
        return document;
    }
}

