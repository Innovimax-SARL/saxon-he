////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.sql;

import com.saxonica.xsltextn.ExtensionElementFactory;
import net.sf.saxon.style.StyleElement;

/**
 * Class SQLElementFactory.
 * A "Factory" for SQL extension nodes in the stylesheet tree.
 * <p>Note: despite its package name, this class is not part of Saxon-HE. Rather, it is part of an
 * open-source plug-in to Saxon-PE and Saxon-EE. This accounts for the reference to code in the
 * com.saxonica package.</p>
 * <p/>
 * <p>From Saxon 9.2 the standard namespace associated with this extension is "http://saxon.sf.net/sql".
 * However, it can be registered under a different namespace if required.</p>
 */

// Note: despite its package name, this class is not part of Saxon-HE. Rather, it is part of an
// open-source

public class SQLElementFactory implements ExtensionElementFactory {

    /**
     * Identify the class to be used for stylesheet elements with a given local name.
     * The returned class must extend net.sf.saxon.style.StyleElement
     *
     * @return null if the local name is not a recognised element type in this
     *         namespace.
     */

    /*@Nullable*/
    public Class<? extends StyleElement> getExtensionClass(String localname) {
        if (localname.equals("connect")) return SQLConnect.class;
        if (localname.equals("insert")) return SQLInsert.class;
        if (localname.equals("update")) return SQLUpdate.class;
        if (localname.equals("delete")) return SQLDelete.class;
        if (localname.equals("column")) return SQLColumn.class;
        if (localname.equals("close")) return SQLClose.class;
        if (localname.equals("query")) return SQLQuery.class;
        if (localname.equals("execute")) return SQLExecute.class;
        return null;
    }

}

