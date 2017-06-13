////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.dotnet;

import cli.System.Xml.XmlWriter;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.om.NamespaceBinding;
import net.sf.saxon.om.NamespaceBindingSet;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;

/**
 * This class is a Saxon Receiver that writes events through to a .NET XmlTextWriter
 */

public class DotNetReceiver implements Receiver {

    private XmlWriter writer;
    private PipelineConfiguration pipe;
    private String systemId;
    private boolean closeAfterUse = true;

    /**
     * Create a Receiver that directs output to a .NET XmlTextWriter
     *
     * @param writer the .NET XmlTextWriter
     */

    public DotNetReceiver(XmlWriter writer) {
        this.writer = writer;
    }

    /**
     * Say whether the XmlWriter is to be closed after use. The default is true.
     *
     * @param close if true, the XmlWriter is closed at the end of the document.
     *              If not, it is merely flushed.
     */

    public void setCloseAfterUse(boolean close) {
        closeAfterUse = close;
    }

    /**
     * Notify an attribute. Attributes are notified after the startElement event, and before any
     * children. Namespaces and attributes may be intermingled.
     *
     * @param nameCode   The name of the attribute, as held in the name pool
     * @param typeCode   The type of the attribute, as held in the name pool. The additional bit
     *                   NodeInfo.IS_DTD_TYPE may be set to indicate a DTD-derived type.
     * @param locationId an integer which can be interpreted using a LocationMap to return
     *                   information such as line number and system ID. If no location information is available,
     *                   the value zero is supplied.
     * @param properties Bit significant value. The following bits are defined:
     *                   <dt>DISABLE_ESCAPING</dt>    <dd>Disable escaping for this attribute</dd>
     *                   <dt>NO_SPECIAL_CHARACTERS</dt>      <dd>Attribute value contains no special characters</dd>
     * @throws IllegalStateException: attempt to output an attribute when there is no open element
     *                                start tag
     */

    public void attribute(NodeName nameCode, SimpleType typeCode, CharSequence value, Location locationId, int properties) throws XPathException {
        writer.WriteAttributeString(
                nameCode.getPrefix(),
                nameCode.getLocalPart(),
                nameCode.getURI(),
                value.toString()
        );
    }

    /**
     * Notify character data. Note that some receivers may require the character data to be
     * sent in a single event, but in general this is not a requirement.
     *  @param chars      The characters
     * @param locationId an integer which can be interpreted using a LocationMap to return
     *                   information such as line number and system ID. If no location information is available,
     *                   the value zero is supplied.
     * @param properties Bit significant value. The following bits are defined:
 *                   <dt>DISABLE_ESCAPING</dt>           <dd>Disable escaping for this text node</dd>
     */

    public void characters(CharSequence chars, Location locationId, int properties) throws XPathException {
        writer.WriteString(chars.toString());
    }

    /**
     * Notify the end of the event stream
     */

    public void close() throws XPathException {
        if (closeAfterUse) {
            writer.Close();
        } else {
            writer.Flush();
        }
    }

    /**
     * Notify a comment. Comments are only notified if they are outside the DTD.
     *
     * @param content    The content of the comment
     * @param locationId an integer which can be interpreted using a LocationMap to return
     *                   information such as line number and system ID. If no location information is available,
     *                   the value zero is supplied.
     * @param properties Additional information about the comment. The following bits are
     *                   defined:
     *                   <dt>CHECKED</dt>    <dd>Comment is known to be legal (e.g. doesn't contain "--")</dd>
     * @throws IllegalArgumentException: the content is invalid for an XML comment
     */

    public void comment(CharSequence content, Location locationId, int properties) throws XPathException {
        writer.WriteComment(content.toString());
    }

    /**
     * Notify the end of a document node
     */

    public void endDocument() throws XPathException {
        writer.WriteEndDocument();
    }

    /**
     * Notify the end of an element. The receiver must maintain a stack if it needs to know which
     * element is ending.
     */

    public void endElement() throws XPathException {
        writer.WriteEndElement();
    }

    /**
     * Get the pipeline configuration
     */

    /*@NotNull*/
    public PipelineConfiguration getPipelineConfiguration() {
        return pipe;
    }

    /**
     * Notify a namespace. Namespaces are notified <b>after</b> the startElement event, and before
     * any children for the element. The namespaces that are reported are only required
     * to include those that are different from the parent element. The events represent namespace
     * declarations and undeclarations rather than in-scope namespace nodes: an undeclaration is represented
     * by a namespace code of zero. If the sequence of namespace events contains two
     * A namespace must not conflict with any namespaces already used for element or attribute names.
     *  @param namespaceBindings the prefix/uri pair to be notified
     * @param properties       The most important property is REJECT_DUPLICATES. If this property is set, the
     *                         namespace declaration will be rejected if it conflicts with a previous declaration of the same
     *                         prefix. If the property is not set, the namespace declaration will be ignored if it conflicts
     *                         with a previous declaration. This reflects the fact that when copying a tree, namespaces for child
     *                         elements are emitted before the namespaces of their parent element. Unfortunately this conflicts
     *                         with the XSLT rule for complex content construction, where the recovery action in the event of
     */

    public void namespace(NamespaceBindingSet namespaceBindings, int properties) throws XPathException {
        for (NamespaceBinding ns : namespaceBindings) {
            String prefix = ns.getPrefix();
            String uri = ns.getURI();
            if (prefix.isEmpty()) {
                writer.WriteAttributeString("", "xmlns", null, uri);
            } else {
                writer.WriteAttributeString("xmlns", prefix, null, uri);
            }
        }
    }

    /**
     * Notify the start of the event stream
     */

    public void open() throws XPathException {
        // no-op
    }

    /**
     * Output a processing instruction
     *
     * @param name       The PI name. This must be a legal name (it will not be checked).
     * @param data       The data portion of the processing instruction
     * @param locationId an integer which can be interpreted using a LocationMap to return
     *                   information such as line number and system ID. If no location information is available,
     *                   the value zero is supplied.
     * @param properties Additional information about the PI. The following bits are
     *                   defined:
     *                   <dt>CHECKED</dt>    <dd>Data is known to be legal (e.g. doesn't contain "?>")</dd>
     * @throws IllegalArgumentException: the content is invalid for an XML processing instruction
     */

    public void processingInstruction(String name, CharSequence data, Location locationId, int properties) throws XPathException {
        writer.WriteProcessingInstruction(name, data.toString());
    }

    /**
     * Set the pipeline configuration
     */

    public void setPipelineConfiguration(/*@NotNull*/ PipelineConfiguration pipe) {
        this.pipe = pipe;
    }

    /**
     * Set the System ID of the destination tree
     */

    public void setSystemId(String systemId) {
        this.systemId = systemId;
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
     * Notify the start of the content, that is, the completion of all attributes and namespaces.
     * Note that the initial receiver of output from XSLT instructions will not receive this event,
     * it has to detect it itself. Note that this event is reported for every element even if it has
     * no attributes, no namespaces, and no content.
     */

    public void startContent() throws XPathException {
        // no-op
    }

    /**
     * Notify the start of a document node
     */

    public void startDocument(int properties) throws XPathException {
        writer.WriteStartDocument();
    }

    /**
     * Notify the start of an element
     * @param nameCode   integer code identifying the name of the element within the name pool.
     * @param typeCode   integer code identifying the element's type within the name pool. The value -1
 *                   indicates the default type, xs:untyped.
     * @param location line number information
     * @param properties bit-significant properties of the element node. If there are no revelant
     */

    public void startElement(NodeName nameCode, SchemaType typeCode, Location location, int properties) throws XPathException {
        writer.WriteStartElement(
                nameCode.getPrefix(),
                nameCode.getLocalPart(),
                nameCode.getURI()
        );
    }

    /**
     * Get the system identifier that was set with setSystemId.
     *
     * @return The system identifier that was set with setSystemId,
     *         or null if setSystemId was not called.
     */
    public String getSystemId() {
        return systemId;
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
}

