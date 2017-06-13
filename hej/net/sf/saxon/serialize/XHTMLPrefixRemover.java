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
import net.sf.saxon.om.FingerprintedQName;
import net.sf.saxon.om.NamespaceBinding;
import net.sf.saxon.om.NamespaceBindingSet;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;

/**
 * Filter to change elements in the XHTML, SVG, or MathML namespaces so they have no prefix (that is,
 * to make these the default namespace). This filter must be followed by a NamespaceReducer in case
 * there are any attributes in these namespaces, as this will cause the namespace declarations to
 * be reinstated.
 */

public class XHTMLPrefixRemover extends ProxyReceiver {

    public XHTMLPrefixRemover(Receiver next) {
        super(next);
    }
    /**
     * Is the active namespace one that requires special (X)HTML(5) prefix treatment?
     * @param uri URI of the namespace
     * @return  true if requires special treatment
     */
    private boolean isSpecial(String uri) {
        return uri.equals(NamespaceConstant.XHTML) ||
                uri.equals(NamespaceConstant.SVG) ||
                uri.equals(NamespaceConstant.MATHML);
    }
    /**
     * Notify the start of an element
     *
     * Specific treatment of elements in XHTML, SVG and MathML namespaces forces a namespace to be emitted too,
     * rather than by higher level default behaviour.
     *  @param elemName   integer code identifying the name of the element within the name pool.
     * @param typeCode   integer code identifying the element's type within the name pool.
     * @param location
     * @param properties properties of the element node
     */
    @Override
    public void startElement(NodeName elemName, SchemaType typeCode, Location location, int properties) throws XPathException {
        if (isSpecial(elemName.getURI())) {
            if (!elemName.getPrefix().isEmpty()) {
                elemName = new FingerprintedQName("", elemName.getURI(), elemName.getLocalPart());
            }
            super.startElement(elemName, typeCode, location, properties);
            super.namespace(new NamespaceBinding("", elemName.getURI()), properties);
        } else {
            super.startElement(elemName, typeCode, location, properties);
        }
    }


    /**
     * Notify a namespace. Namespaces are notified <b>after</b> the startElement event, and before
     * any children for the element. The namespaces that are reported are only required
     * to include those that are different from the parent element; however, duplicates may be reported.
     * A namespace must not conflict with any namespaces already used for element or attribute names.
     *
     * Any special namespaces (for XHTML, SVG and MathML) are not written out -
     * they are specifically emitted with elements or attributes requiring them
     *
     * @param namespaceBindings the prefix/uri pair representing the namespace binding
     * @param properties       any special properties to be passed on this call
     * @throws IllegalStateException: attempt to output a namespace when there is no open element
     *                                start tag
     */
    @Override
    public void namespace(NamespaceBindingSet namespaceBindings, int properties) throws XPathException {
        for (NamespaceBinding ns : namespaceBindings) {
            if (!isSpecial(ns.getURI())) {
                super.namespace(ns, properties);
            }
        }
    }

    /**
     * Notify an attribute. Attributes are notified after the startElement event, and before any
     * children. Namespaces and attributes may be intermingled.
     *
     * Specific treatment of attributes in XHTML, SVG and MathML namespaces forces a namespace to be emitted too,
     * rather than by higher level default behaviour.
     *
     * @param nameCode   The name of the attribute, as held in the name pool
     * @param typeCode   The type of the attribute, as held in the name pool
     * @param locationId
     *@param properties Bit significant value. The following bits are defined:
     *                   <dd>DISABLE_ESCAPING</dd>    <dt>Disable escaping for this attribute</dt>
     *                   <dd>NO_SPECIAL_CHARACTERS</dd>      <dt>Attribute value contains no special characters</dt>  @throws IllegalStateException: attempt to output an attribute when there is no open element
     *                                start tag
     */
    public void attribute(NodeName nameCode, SimpleType typeCode, CharSequence value, Location locationId, int properties)
            throws XPathException {
        if(isSpecial(nameCode.getURI())) {
            super.namespace(nameCode.getNamespaceBinding(),properties);
        }
        super.attribute(nameCode, typeCode, value, locationId, properties);
    }
}
