////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.TransformerException;

/**
 * UnfailingErrorListener is an implementation of the JAXP ErrorListener interface
 * that wraps a supplied ErrorListener and never throws an exception
 */
public class DelegatingErrorListener implements UnfailingErrorListener {

    private ErrorListener base;

    public DelegatingErrorListener(ErrorListener base) {
        if (base == null) {
            throw new NullPointerException();
        }
        this.base = base;
    }

    public void warning(TransformerException exception) {
        try {
            base.warning(exception);
        } catch (TransformerException e) {
            // no action
        }
    }

    public void error(TransformerException exception) {
        try {
            base.error(exception);
        } catch (TransformerException e) {
            // no action
        }
    }

    public void fatalError(TransformerException exception) {
        try {
            base.fatalError(exception);
        } catch (TransformerException e) {
            // no action
        }
    }

    /**
     * Get the ErrorListener that this DelegatingErrorListener delegates to.
     * @return the ErrorListener originally supplied when this DelegatingErrorListener was created.
     */

    public ErrorListener getBaseErrorListener() {
        return base;
    }
}


