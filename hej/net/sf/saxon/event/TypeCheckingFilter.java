////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.expr.parser.ExplicitLocation;
import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.expr.parser.Token;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NamespaceBindingSet;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.pattern.CombinedNodeTest;
import net.sf.saxon.pattern.ContentTypeTest;
import net.sf.saxon.pattern.NameTest;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.Cardinality;

import java.util.HashSet;

/**
 * A filter on the push pipeline that performs type checking, both of the item type and the
 * cardinality.
 * <p/>
 * Note that the TypeCheckingFilter cannot currently check document node tests of the form
 * document-node(element(X,Y)), so it is not invoked in such cases. This isn't a big problem, because most
 * instructions that return document nodes materialize them anyway.
 */

public class TypeCheckingFilter extends ProxyReceiver {

    private ItemType itemType;
    private int cardinality;
    private RoleDiagnostic role;
    private Location locator;
    private int count = 0;
    private int level = 0;
    private HashSet<Long> checkedElements = new HashSet<Long>(10);
    // used to avoid repeated checking when a template creates large numbers of elements of the same type
    // The key is a (namecode, typecode) pair, packed into a single long

    public TypeCheckingFilter(Receiver next) {
        super(next);
    }

    public void setRequiredType(ItemType type, int cardinality, RoleDiagnostic role, Location locator) {
        itemType = type;
        this.cardinality = cardinality;
        this.role = role;
        this.locator = locator;
    }

    /**
     * Notify an attribute. Attributes are notified after the startElement event, and before any
     * children. Namespaces and attributes may be intermingled.
     *
     * @param nameCode   The name of the attribute, as held in the name pool
     * @param typeCode   The type of the attribute, as held in the name pool
     * @param locationId
     * @param properties Bit significant value. The following bits are defined:
     *                   <dd>DISABLE_ESCAPING</dd>    <dt>Disable escaping for this attribute</dt>
     *                   <dd>NO_SPECIAL_CHARACTERS</dd>      <dt>Attribute value contains no special characters</dt>  @throws IllegalStateException: attempt to output an attribute when there is no open element
     *                                start tag
     */

    public void attribute(NodeName nameCode, SimpleType typeCode, CharSequence value, Location locationId, int properties) throws XPathException {
        if (level == 0) {
            if (++count == 2) {
                checkAllowsMany(locationId);
            }
            ItemType type = new CombinedNodeTest(
                    new NameTest(Type.ATTRIBUTE, nameCode, getNamePool()),
                    Token.INTERSECT,
                    new ContentTypeTest(Type.ATTRIBUTE, typeCode, getConfiguration(), false));
            checkItemType(type, locationId);
        }
        nextReceiver.attribute(nameCode, typeCode, value, locationId, properties);
    }

    /**
     * Character data
     */

    public void characters(CharSequence chars, Location locationId, int properties) throws XPathException {
        if (level == 0) {
            if (++count == 2) {
                checkAllowsMany(locationId);
            }
            checkItemType(NodeKindTest.TEXT, locationId);
        }
        nextReceiver.characters(chars, locationId, properties);
    }

    /**
     * Output a comment
     */

    public void comment(CharSequence chars, Location locationId, int properties) throws XPathException {
        if (level == 0) {
            if (++count == 2) {
                checkAllowsMany(locationId);
            }
            checkItemType(NodeKindTest.COMMENT, locationId);
        }
        nextReceiver.comment(chars, locationId, properties);    //To change body of overridden methods use File | Settings | File Templates.
    }

    /**
     * Notify a namespace. Namespaces are notified <b>after</b> the startElement event, and before
     * any children for the element. The namespaces that are reported are only required
     * to include those that are different from the parent element; however, duplicates may be reported.
     * A namespace must not conflict with any namespaces already used for element or attribute names.
     *
     * @param namespaceBindings the prefix/uri pair
     * @throws IllegalStateException: attempt to output a namespace when there is no open element
     *                                start tag
     */

    public void namespace(NamespaceBindingSet namespaceBindings, int properties) throws XPathException {
        if (level == 0) {
            if (++count == 2) {
                checkAllowsMany(ExplicitLocation.UNKNOWN_LOCATION);
            }
            checkItemType(NodeKindTest.NAMESPACE, ExplicitLocation.UNKNOWN_LOCATION);
        }
        nextReceiver.namespace(namespaceBindings, properties);    //To change body of overridden methods use File | Settings | File Templates.
    }

    /**
     * Processing Instruction
     */

    public void processingInstruction(String target, CharSequence data, Location locationId, int properties) throws XPathException {
        if (level == 0) {
            if (++count == 2) {
                checkAllowsMany(locationId);
            }
            checkItemType(NodeKindTest.PROCESSING_INSTRUCTION, locationId);
        }
        nextReceiver.processingInstruction(target, data, locationId, properties);
    }

    /**
     * Start of a document node.
     */

    public void startDocument(int properties) throws XPathException {
        if (level == 0) {
            if (++count == 2) {
                checkAllowsMany(ExplicitLocation.UNKNOWN_LOCATION);
            }
            checkItemType(NodeKindTest.DOCUMENT, ExplicitLocation.UNKNOWN_LOCATION);
        }
        level++;
        nextReceiver.startDocument(properties);
    }

    /**
     * Notify the start of an element
     *  @param nodeName   integer code identifying the name of the element within the name pool.
     * @param typeCode   integer code identifying the element's type within the name pool.
     * @param location
     * @param properties properties of the element node
     */

    public void startElement(NodeName nodeName, SchemaType typeCode, Location location, int properties) throws XPathException {
        if (level == 0) {
            if (++count == 1) {
                // don't bother with any caching on the first item, it will often be the only one
                ItemType type = new CombinedNodeTest(
                        new NameTest(Type.ELEMENT, nodeName, getNamePool()),
                        Token.INTERSECT,
                        new ContentTypeTest(Type.ELEMENT, typeCode, getConfiguration(), false));
                checkItemType(type, location);
            } else {
                if (count == 2) {
                    checkAllowsMany(location);
                }
                long key = (long) nodeName.obtainFingerprint(getNamePool()) << 32 | (long) typeCode.getFingerprint();
                if (!checkedElements.contains(key)) {
                    ItemType type = new CombinedNodeTest(
                            new NameTest(Type.ELEMENT, nodeName, getNamePool()),
                            Token.INTERSECT,
                            new ContentTypeTest(Type.ELEMENT, typeCode, getConfiguration(), false));
                    checkItemType(type, location);
                    checkedElements.add(key);
                }
            }
        }
        level++;
        nextReceiver.startElement(nodeName, typeCode, location, properties);
    }

    /**
     * Notify the end of a document node
     */

    public void endDocument() throws XPathException {
        level--;
        nextReceiver.endDocument();
    }

    /**
     * End of element
     */

    public void endElement() throws XPathException {
        level--;
        nextReceiver.endElement();
    }

    /**
     * End of event stream
     */

    public void close() throws XPathException {
        if (count == 0 && !Cardinality.allowsZero(cardinality)) {
            XPathException err = new XPathException("An empty sequence is not allowed as the " +
                    role.getMessage());
            String errorCode = role.getErrorCode();
            err.setErrorCode(errorCode);
            if (!"XPDY0050".equals(errorCode)) {
                err.setIsTypeError(true);
            }
            throw err;
        }
        // don't pass on the close event
    }

    /**
     * Output an item (atomic value or node) to the sequence
     */

    public void append(Item item, Location locationId, int copyNamespaces) throws XPathException {
        if (level == 0) {
            if (++count == 2) {
                checkAllowsMany(locationId);
            }
            checkItem(item, locationId);
        }
        if (nextReceiver instanceof SequenceReceiver) {
            ((SequenceReceiver) nextReceiver).append(item, locationId, copyNamespaces);
        } else {
            super.append(item, locationId, copyNamespaces);
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
        return true;
    }

    private void checkItemType(ItemType type, Location locationId) throws XPathException {
        if (!getConfiguration().getTypeHierarchy().isSubType(type, itemType)) {
            throwTypeError(type, locationId);
        }
    }

    private void checkItem(Item item, Location locationId) throws XPathException {
        if (!itemType.matches(item, getConfiguration().getTypeHierarchy())) {
            throwTypeError(Type.getItemType(item, getConfiguration().getTypeHierarchy()), locationId);
        }
    }

    private void throwTypeError(ItemType type, Location locationId) throws XPathException {
        String message = role.composeErrorMessage(itemType, type);
        String errorCode = role.getErrorCode();
        XPathException err = new XPathException(message);
        err.setErrorCode(errorCode);
        if (!"XPDY0050".equals(errorCode)) {
            err.setIsTypeError(true);
        }
        if (locationId == null) {
            err.setLocation(locator);
        } else {
            err.setLocation(locationId.saveLocation());
        }
        throw err;
    }

    private void checkAllowsMany(Location locationId) throws XPathException {
        if (!Cardinality.allowsMany(cardinality)) {
            XPathException err = new XPathException("A sequence of more than one item is not allowed as the " +
                    role.getMessage());
            String errorCode = role.getErrorCode();
            err.setErrorCode(errorCode);
            if (!"XPDY0050".equals(errorCode)) {
                err.setIsTypeError(true);
            }
            if (locationId == null || locationId == ExplicitLocation.UNKNOWN_LOCATION) {
                err.setLocator(locator);
            } else {
                err.setLocator(locationId);
            }
            throw err;
        }
    }


}

