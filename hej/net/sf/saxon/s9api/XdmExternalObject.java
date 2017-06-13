////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.value.ObjectValue;

/**
 * The class XdmExternalObject represents an XDM item that wraps an external (Java or .NET) object.
 * As such, it is outside the scope of the XDM specification (but permitted as an extension).
 * <p/>
 * <p>In releases prior to 9.5, external objects in Saxon were represented as atomic values. From
 * 9.5 they are represented as a fourth kind of item, alongside nodes, atomic values, and functions.</p>
 */
public class XdmExternalObject extends XdmItem {

    /**
     * Create an XdmExternalObject that wraps a supplied Java object
     * @param value the supplied Java object
     */

    public XdmExternalObject(Object value) {
        super(new ObjectValue<Object>(value));
    }

    /**
     * Get the wrapped Java object
     *
     * @return the wrapped object
     */

    public Object getExternalObject() {
        return ((ObjectValue) getUnderlyingValue()).getObject();
    }

    /**
     * Get the result of converting the external value to a string.
     * @return the result of applying toString() to the wrapped external object.
     */

    public String toString() {
        return getExternalObject().toString();
    }


}

