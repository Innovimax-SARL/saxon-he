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
import net.sf.saxon.om.NodeName;
import net.sf.saxon.serialize.charcode.UTF16CharacterSet;
import net.sf.saxon.serialize.charcode.XMLCharacterData;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;

/**
 * This class is used on the serialization pipeline to check that the document conforms
 * to XML 1.0 rules. It is placed on the pipeline only when the configuration permits
 * XML 1.1 constructs, but the particular output document is being serialized as XML 1.0
 *
 * <p>Simplified in Saxon 9.6 because the rules for XML Names in 1.0 are now the same as
 * the rules in 1.1; only the rules for valid characters are different.</p>
 */

public class XML10ContentChecker extends ProxyReceiver {


    public XML10ContentChecker(Receiver next) {
        super(next);
    }

    /**
     * Notify the start of an element
     *  @param elemName   integer code identifying the name of the element within the name pool.
     * @param typeCode   integer code identifying the element's type within the name pool.
     * @param location
     * @param properties properties of the element node
     */

    public void startElement(NodeName elemName, SchemaType typeCode, Location location, int properties) throws XPathException {
        nextReceiver.startElement(elemName, typeCode, location, properties);
    }


    /**
     * Notify an attribute. Attributes are notified after the startElement event, and before any
     * children. Namespaces and attributes may be intermingled.
     *
     * @param attName    The name of the attribute, as held in the name pool
     * @param typeCode   The type of the attribute, as held in the name pool
     * @param locationId
     *@param properties Bit significant value. The following bits are defined:
     *                   <dd>DISABLE_ESCAPING</dd>    <dt>Disable escaping for this attribute</dt>
     *                   <dd>NO_SPECIAL_CHARACTERS</dd>      <dt>Attribute value contains no special characters</dt>  @throws IllegalStateException: attempt to output an attribute when there is no open element
     *                                start tag
     */

    public void attribute(NodeName attName, SimpleType typeCode, CharSequence value, Location locationId, int properties) throws XPathException {
        checkString(value, locationId);
        nextReceiver.attribute(attName, typeCode, value, locationId, properties);
    }

    /**
     * Character data
     */

    public void characters(CharSequence chars, Location locationId, int properties) throws XPathException {
        checkString(chars, locationId);
        nextReceiver.characters(chars, locationId, properties);
    }

    /**
     * Output a comment
     */

    public void comment(CharSequence chars, Location locationId, int properties) throws XPathException {
        checkString(chars, locationId);
        nextReceiver.comment(chars, locationId, properties);
    }

    /**
     * Processing Instruction
     */

    public void processingInstruction(String target, /*@NotNull*/ CharSequence data, Location locationId, int properties) throws XPathException {
        checkString(data, locationId);
        nextReceiver.processingInstruction(target, data, locationId, properties);
    }

    /**
     * Check that a string consists of valid XML 1.0 characters (UTF-16 encoded)
     *
     * @param in         the string to be checked
     * @param locationId the location of the string
     */

    private void checkString(CharSequence in, Location locationId) throws XPathException {
        final int len = in.length();
        for (int c = 0; c < len; c++) {
            int ch32 = in.charAt(c);
            if (UTF16CharacterSet.isHighSurrogate(ch32)) {
                char low = in.charAt(++c);
                ch32 = UTF16CharacterSet.combinePair((char) ch32, low);
            }
            if (!XMLCharacterData.isValid10(ch32)) {
                XPathException err = new XPathException("The result tree contains a character not allowed by XML 1.0 (hex " +
                        Integer.toHexString(ch32) + ')');
                err.setErrorCode("SERE0006");
                err.setLocator(locationId);
                throw err;
            }
        }
    }

}

