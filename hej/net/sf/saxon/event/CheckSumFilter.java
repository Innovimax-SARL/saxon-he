////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;
import net.sf.saxon.value.Whitespace;

/**
 * A filter to go on a Receiver pipeline and calculate a checksum of the data passing through the pipeline.
 * Optionally the filter will also check any checksum (represented by a processing instruction with name
 * SIGMA) found in the file.
 *
 * <p>The checksum takes account of element, attribute, and text nodes only. The order of attributes
 * within an element makes no difference.</p>
 */

public class CheckSumFilter extends ProxyReceiver {

    private final static boolean DEBUG = false;

    int checksum = 0;
    int sequence = 0;
    boolean checkExistingChecksum = false;
    boolean checksumCorrect = false;
    boolean checksumFound = false;

    public final static String SIGMA = "\u03A3";

    public CheckSumFilter(Receiver nextReceiver) {
        super(nextReceiver);
    }

    /**
     * Ask the filter to check any existing checksums found in the file
     * @param check true if existing checksums are to be checked
     */

    public void setCheckExistingChecksum(boolean check) {
        this.checkExistingChecksum = check;
    }

    @Override
    public void startDocument(int properties) throws XPathException {
        if (DEBUG) {
            System.err.println("CHECKSUM - START DOC");
        }
        super.startDocument(properties);
    }

    /**
     * Append an arbitrary item (node or atomic value) to the output
     *  @param item           the item to be appended
     * @param locationId     the location of the calling instruction, for diagnostics
     * @param copyNamespaces if the item is an element node, this indicates whether its namespaces
 *                       need to be copied. Values are {@link NodeInfo#ALL_NAMESPACES},
 *                       {@link NodeInfo#LOCAL_NAMESPACES}, {@link NodeInfo#NO_NAMESPACES}
     */
    @Override
    public void append(Item item, Location locationId, int copyNamespaces) throws XPathException {
        checksum ^= hash(item.toString(), sequence++);
        if (DEBUG) {
            System.err.println("After append: " + Integer.toHexString(checksum));
        }
        super.append(item, locationId, copyNamespaces);
    }

    /**
     * Notify an attribute. Attributes are notified after the startElement event, and before any
     * children. Namespaces and attributes may be intermingled.
     *  @param nameCode   The name of the attribute, as held in the name pool
     * @param typeCode   The type of the attribute, as held in the name pool
     * @param value      The attribute value
     * @param locationId The location of the attribute
     * @param properties Bit significant value. The following bits are defined:
*                   <dd>DISABLE_ESCAPING</dd>    <dt>Disable escaping for this attribute</dt>
*                   <dd>NO_SPECIAL_CHARACTERS</dd>      <dt>Attribute value contains no special characters</dt>  @throws IllegalStateException: attempt to output an attribute when there is no open element
     */
    @Override
    public void attribute(NodeName nameCode, SimpleType typeCode, CharSequence value, Location locationId, int properties) throws XPathException {
        checksum ^= hash(nameCode, sequence);
        checksum ^= hash(value, sequence);
        if (DEBUG) {
            System.err.println("After attribute " + nameCode.getDisplayName() + ": " + checksum +
                                       "(" + hash(nameCode, sequence) + "," + hash(value, sequence) + ")");
        }
        super.attribute(nameCode, typeCode, value, locationId, properties);
    }

    /**
     * Character data
     */
    @Override
    public void characters(CharSequence chars, Location locationId, int properties) throws XPathException {
        if (!Whitespace.isWhite(chars)) {
            checksum ^= hash(chars, sequence++);
            if (DEBUG) {
                System.err.println("After characters " + chars + ": " + Integer.toHexString(checksum));
            }
        }
        super.characters(chars, locationId, properties);
    }

    /**
     * Notify the start of an element
     * @param elemName   integer code identifying the name of the element within the name pool.
     * @param typeCode   integer code identifying the element's type within the name pool.
     * @param location location of the element
     * @param properties properties of the element node
     */
    @Override
    public void startElement(NodeName elemName, SchemaType typeCode, Location location, int properties) throws XPathException {
        checksum ^= hash(elemName, sequence++);
        if (DEBUG) {
            System.err.println("After startElement " + elemName.getDisplayName() + ": " + checksum);
        }
        checksumCorrect = false;
        super.startElement(elemName, typeCode, location, properties);
    }

    /**
     * End of element
     */
    @Override
    public void endElement() throws XPathException {
        checksum ^= 1;
        if (DEBUG) {
            System.err.println("After endElement: " + checksum);
        }
        super.endElement();
    }

    /**
     * Processing Instruction
     */
    @Override
    public void processingInstruction(String target, CharSequence data, Location locationId, int properties) throws XPathException {
        if (target.equals(SIGMA)) {
            checksumFound = true;
            if (checkExistingChecksum) {
                try {
                    int found = (int) Long.parseLong("0" + data.toString(), 16);
                    checksumCorrect = found == checksum;
                } catch (NumberFormatException e) {
                    checksumCorrect = false;
                }
            }
        }
        super.processingInstruction(target, data, locationId, properties);
    }

    /**
     * Ask whether a checksum has been found
     * @return true if a checksum processing instruction has been found (whether or not the checksum was correct)
     */

    public boolean isChecksumFound() {
        return checksumFound;
    }

    /**
     * Get the accumulated checksum
     * @return the checksum of the events passed through the filter so far.
     */

    public int getChecksum() {
        return checksum;
    }

    /**
     * Ask if a correct checksum has been found in the file
     * @return true if a checksum has been found, if its value matches, and if no significant data has been encountered
     * after the checksum
     */

    public boolean isChecksumCorrect() {
        return checksumCorrect;
    }

    private int hash(CharSequence s, int sequence) {
        int h = sequence<<8;
        for (int i=0; i<s.length(); i++) {
            h = (h<<1) + s.charAt(i);
        }
        return h;
    }

    private int hash(NodeName n, int sequence) {
        //System.err.println("hash(" + n.getLocalPart() + ") " + hash(n.getLocalPart(), sequence) + "/" + hash(n.getURI(), sequence));
        return hash(n.getLocalPart(), sequence) ^ hash(n.getURI(), sequence);
    }
}

