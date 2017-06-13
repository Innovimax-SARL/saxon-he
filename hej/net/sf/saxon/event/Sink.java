////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NamespaceBindingSet;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;

/**
 * A Sink is an Receiver that discards all information passed to it
 */

public class Sink extends SequenceReceiver {

    public Sink(PipelineConfiguration pipe) {
        super(pipe);
    }

    /**
     * Start of event stream
     */

    public void open() {
    }

    /**
     * End of event stream
     */

    public void close() {
    }

    /**
     * Start of a document node.
     */

    public void startDocument(int properties) {
    }

    /**
     * Notify the end of a document node
     */

    public void endDocument() {
    }

    /**
     * Notify the start of an element
     *  @param nameCode   integer code identifying the name of the element within the name pool.
     * @param typeCode   integer code identifying the element's type within the name pool.
     * @param location
     * @param properties for future use. Should be set to zero.
     */

    public void startElement(NodeName nameCode, SchemaType typeCode, Location location, int properties) {
    }

    /**
     * Notify a namespace. Namespaces are notified <b>after</b> the startElement event, and before
     * any children for the element. The namespaces that are reported are only required
     * to include those that are different from the parent element; however, duplicates may be reported.
     * A namespace must not conflict with any namespaces already used for element or attribute names.
     *
     * @param namespaceBindings the prefix/uri pair representing the namespace binding to be written
     * @throws java.lang.IllegalStateException:
     *          attempt to output a namespace when there is no open element
     *          start tag
     */

    public void namespace(NamespaceBindingSet namespaceBindings, int properties) {

    }

    /**
     * Notify an attribute. Attributes are notified after the startElement event, and before any
     * children. Namespaces and attributes may be intermingled.
     *
     * @param nameCode   The name of the attribute, as held in the name pool
     * @param typeCode   The type of the attribute, as held in the name pool
     * @param locationId
     *@param properties Bit significant value. The following bits are defined:
     *                   <dd>DISABLE_ESCAPING</dd>    <dt>Disable escaping for this attribute</dt>
     *                   <dd>NO_SPECIAL_CHARACTERS</dd>      <dt>Attribute value contains no special characters</dt>  @throws java.lang.IllegalStateException:
     *          attempt to output an attribute when there is no open element
     *          start tag
     */

    public void attribute(NodeName nameCode, SimpleType typeCode, CharSequence value, Location locationId, int properties) {
    }

    /**
     * Notify the start of the content, that is, the completion of all attributes and namespaces.
     * Note that the initial receiver of output from XSLT instructions will not receive this event,
     * it has to detect it itself. Note that this event is reported for every element even if it has
     * no attributes, no namespaces, and no content.
     */


    public void startContent() {
    }

    /**
     * End of element
     */

    public void endElement() {
    }

    /**
     * Character data
     */

    public void characters(CharSequence chars, Location locationId, int properties) {
    }


    /**
     * Processing Instruction
     */

    public void processingInstruction(String target, CharSequence data, Location locationId, int properties) {
    }

    /**
     * Output a comment
     */

    public void comment(CharSequence chars, Location locationId, int properties) {
    }


    /**
     * Append an arbitrary item (node or atomic value) to the output
     *  @param item           the item to be appended
     * @param locationId     the location of the calling instruction, for diagnostics
     * @param copyNamespaces if the item is an element node, this indicates whether its namespaces
 *                       need to be copied. Values are {@link net.sf.saxon.om.NodeInfo#ALL_NAMESPACES},
 *                       {@link net.sf.saxon.om.NodeInfo#LOCAL_NAMESPACES}, {@link net.sf.saxon.om.NodeInfo#NO_NAMESPACES}
     */

    public void append(Item item, Location locationId, int copyNamespaces) {
    }

    /**
     * Set the URI for an unparsed entity in the document.
     */

    public void setUnparsedEntity(String name, String uri, String publicId) {
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

