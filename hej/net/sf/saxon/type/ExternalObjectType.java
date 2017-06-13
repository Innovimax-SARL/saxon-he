////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;

/**
 * This class represents the type of an external object returned by
 * an extension function, or supplied as an external variable/parameter.
 */
public abstract class ExternalObjectType {

    /**
     * Get the name of this type.
     *
     * @return the fully qualified name of the Java or .NET class.
     */

    public abstract String getName();

    /**
     * Get the target namespace of this type. For Java this is always {@link net.sf.saxon.lib.NamespaceConstant#JAVA_TYPE}.
     * For .net it is always {@link net.sf.saxon.lib.NamespaceConstant#DOT_NET_TYPE}
     *
     * @return the target namespace of this type definition.
     */

    public abstract String getTargetNamespace();

    /**
     * Ask whether this is an external type
     * @return true (it is)
     */

    public boolean isExternalType(){
        return true;
    }

    /**
     * Get the name of this type
     * @return a name whose namespace indicates the space of Java or .net classes, and whose local name
     * is derived from the fully qualified name of the Java or .net class
     */

    public abstract StructuredQName getTypeName();

    /**
     * Ask whether this is a plain type (a type whose instances are always atomic values)
     * @return false. External object types are not considered to be atomic types
     */

    public final boolean isPlainType() {
        return false;
    }

    /**
     * Return the integer fingerprint of the underlying primitive type
     * @return the fingerprint of the primitive type
     */
    public abstract int getPrimitiveType();

    /**
     * Generate Javascript code to test whether an item conforms to this item type
     *
     * @return a Javascript instruction or sequence of instructions, which can be used as the body
     * of a Javascript function, and which returns a boolean indication whether the value of the
     * variable "item" is an instance of this item type.
     * @throws XPathException if JS code cannot be generated for this item type, for example because
     *                        the test is schema-aware.
     * @param knownToBe
     */

    public String generateJavaScriptItemTypeTest(ItemType knownToBe) throws XPathException {
        throw new XPathException("Cannot generate JS code for external object tests", SaxonErrorCode.SXJS0001);
    }

    /**
     * Generate Javascript code to convert a supplied Javascript value to this item type,
     * if conversion is possible, or throw an error otherwise.
     *
     * @param errorCode the error to be thrown if conversion is not possible
     * @return a Javascript instruction or sequence of instructions, which can be used as the body
     * of a Javascript function, and which returns the result of conversion to this type, or throws
     * an error if conversion is not possible. The variable "val" will hold the supplied Javascript
     * value.
     */
    public String generateJavaScriptItemTypeAcceptor(String errorCode) throws XPathException {
        throw new XPathException("Cannot generate JS code for external object tests", SaxonErrorCode.SXJS0001);
    }


}
