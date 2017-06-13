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
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: 05/07/2013
 * Time: 12:28
 * To change this template use File | Settings | File Templates.
 */
public interface UnfailingErrorListener extends ErrorListener {
    void warning(TransformerException exception);

    void error(TransformerException exception);

    void fatalError(TransformerException exception);
}

