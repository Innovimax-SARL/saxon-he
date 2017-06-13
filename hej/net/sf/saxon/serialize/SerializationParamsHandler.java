////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import net.sf.saxon.expr.instruct.ResultDocument;
import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.regex.UnicodeString;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.type.Type;
import net.sf.saxon.z.IntHashMap;

import java.util.*;

/**
 * This class handles a set of serialization parameters provided in the form of an XDM instance
 * as specified in the Serialization 3.0 definition, section 3.1
 */
public class SerializationParamsHandler {

    public static final String NAMESPACE = NamespaceConstant.OUTPUT;
    Properties properties;
    CharacterMap characterMap;
    Location locator;

    private static final Set<String> VALUE_ONLY = new HashSet<String>(Arrays.asList(
        new String[]{"value"}
    ));

    private static final Set<String> CHARMAP_ATTS = new HashSet<String>(Arrays.asList(
        new String[]{"character", "map-string"}
    ));

    /**
     * Set the location of the instruction to be used for error message reporting
     * @param locator the location for error reporting
     */

    public void setLocator(Location locator) {
        this.locator = locator;
    }

    /**
     * Set the serialization parameters in the form of an XDM instance
     *
     * @param node either the serialization-parameters element node, or a document node having
     *             this element as its only child
     * @throws XPathException if incorrect serialization parameters are found
     */

    public void setSerializationParams(NodeInfo node) throws XPathException {
        properties = new Properties();
        if (node.getNodeKind() == Type.DOCUMENT) {
            node = Navigator.getOutermostElement(node.getTreeInfo());
        }
        if (node.getNodeKind() != Type.ELEMENT) {
            throw new XPathException("Serialization params: node must be a document or element node");
        }
        if (!node.getLocalPart().equals("serialization-parameters")) {
            throw new XPathException("Serialization params: element name must be 'serialization-parameters");
        }
        if (!node.getURI().equals(NAMESPACE)) {
            throw new XPathException("Serialization params: element namespace must be " + NAMESPACE);
        }
        Set<String> none = Collections.emptySet();
        allowAttribute(node, none);
        Set<NodeName> nodeNames = new HashSet<NodeName>();
        AxisIterator kids = node.iterateAxis(AxisInfo.CHILD, NodeKindTest.ELEMENT);
        NodeInfo child;
        while ((child = kids.next()) != null) {
            if (!nodeNames.add(NameOfNode.makeName(child))) {
                throw new XPathException("Duplicated serialization parameter " + child.getDisplayName(), "SEPM0019");
            }
            String lname = child.getLocalPart();
            String uri = child.getURI();
            if (uri.isEmpty()) {
                throw new XPathException("Serialization parameter " + lname + " is in no namespace", "SEPM0017");
            }
            if (NamespaceConstant.OUTPUT.equals(uri)) {
                uri = "";
            }
            if ("".equals(uri) && lname.equals("use-character-maps")) {
                allowAttribute(child, none);
                AxisIterator gKids = child.iterateAxis(AxisInfo.CHILD, NodeKindTest.ELEMENT);
                NodeInfo gChild;
                IntHashMap<String> map = new IntHashMap<String>();
                while ((gChild = gKids.next()) != null) {
                    allowAttribute(gChild, CHARMAP_ATTS);
                    if (!(gChild.getURI().equals(NAMESPACE) && gChild.getLocalPart().equals("character-map"))) {
                        if (gChild.getURI().equals(NAMESPACE) || gChild.getURI().isEmpty()) {
                            throw new XPathException("Invalid child of use-character-maps: " + gChild.getDisplayName(), "SEPM0017");
                        }
                    }
                    String ch = getAttribute(gChild, "character");
                    String str = getAttribute(gChild, "map-string");
                    UnicodeString chValue = UnicodeString.makeUnicodeString(ch);

                    if (chValue.uLength() != 1) {
                        throw new XPathException("In the serialization parameters, the value of @character in the character map " +
                            "must be a single Unicode character", "SEPM0017");
                    }
                    int code = chValue.uCharAt(0);
                    String prev = map.put(code, str);
                    if (prev != null) {
                        throw new XPathException("In the serialization parameters, the character map contains two entries for the character \\u" +
                            Integer.toHexString(65536+code).substring(1), "SEPM0018");
                    }
                }
                characterMap = new CharacterMap(NameOfNode.makeName(node).getStructuredQName(), map);
            } else {
                allowAttribute(child, VALUE_ONLY);
                String value = getAttribute(child, "value");
                try {
                    ResultDocument.setSerializationProperty(properties, uri, lname, value,
                        new InscopeNamespaceResolver(child), false, node.getConfiguration());
                } catch (XPathException err) {
                    if ("XQST0109".equals(err.getErrorCodeLocalPart()) || "SEPM0016".equals(err.getErrorCodeLocalPart())) {
                        if ("".equals(uri)) {
                            XPathException e2 = new XPathException("Unknown serialization parameter " +
                                Err.depict(child), "SEPM0017");
                            e2.setLocator(locator);
                            throw e2;
                        }
                        // Unknown serialization parameter - no action, ignore the error
                    } else {
                        throw err;
                    }
                }
            }

        }
    }

    /**
     * Check that no no-namespace attributes are present on an element, unless they have a specified local name
     * @param element the element to be checked
     * @param allowedNames a list of permitted local names of attributes
     * @throws XPathException if there is a no-namespace attribute present whose name is not the name supplied
     */

    private void allowAttribute(NodeInfo element, Set<String> allowedNames) throws XPathException {
        NodeInfo att;
        AxisIterator iter = element.iterateAxis(AxisInfo.ATTRIBUTE);
        while ((att = iter.next()) != null) {
            if ("".equals(att.getURI()) && !allowedNames.contains(att.getLocalPart())) {
                throw new XPathException("In serialization parameters, attribute @" + att.getLocalPart() +
                    " must not appear on element " + element.getDisplayName(), "SEPM0017");
            }
        }
    }

    /**
     * Check that a given attribute is present on an element, and return its value
     *
     * @param element      the element to be checked
     * @param localName   the required attribute
     * @return the value of the attribute
     * @throws XPathException if the attribute does not exist
     */

    private String getAttribute(NodeInfo element, String localName) throws XPathException {
        String value = element.getAttributeValue("", localName);
        if (value == null) {
            throw new XPathException("In serialization parameters, attribute @" + localName +
                " is missing on element " + element.getDisplayName());
        }
        return value;
    }


    public Properties getSerializationProperties() {
        return properties;
    }

    public CharacterMap getCharacterMap() {
        return characterMap;
    }
}

