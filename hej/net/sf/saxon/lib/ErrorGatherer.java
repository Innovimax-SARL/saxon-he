////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.s9api.StaticError;
import net.sf.saxon.trans.XPathException;
import javax.xml.transform.TransformerException;
import java.util.ArrayList;
import java.util.List;


public class ErrorGatherer implements UnfailingErrorListener{

    List <StaticError> errorList = new ArrayList<StaticError>();


    public ErrorGatherer(List <StaticError> errorList){
         this.errorList = errorList;

    }

    public void warning(TransformerException exception)  {
        XPathException newXPathException = XPathException.makeXPathException(exception);
        errorList.add(new StaticError(newXPathException, true));
    }

    public void error(TransformerException exception)  {
        XPathException newXPathException = XPathException.makeXPathException(exception);
        errorList.add(new StaticError(newXPathException, false));
    }

    public void fatalError(TransformerException exception)  {
        XPathException newXPathException = XPathException.makeXPathException(exception);
        errorList.add(new StaticError(newXPathException, false));
    }
}
