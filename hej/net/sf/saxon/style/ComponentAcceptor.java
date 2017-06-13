////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;


import net.sf.saxon.expr.Component;
import net.sf.saxon.trans.XPathException;

/**
 * An abstraction of the capability of the xsl:accept declaration, provided because the real xsl:accept
 * element is not available in Saxon-HE. Conceptually, the ComponentAcceptor is a rule that modifies
 * the visibility of components accepted from a used package.
 */

public interface ComponentAcceptor {

    /**
     * Accept a component from a used package, modifying its visibility if necessary
     * @param component the component to be accepted; as a side-effect of this method, the
     *                  visibility of the component may change
     * @throws XPathException if the requested visibility is incompatible with the declared
     * visibility
     */

    void acceptComponent(Component component) throws XPathException;
}

