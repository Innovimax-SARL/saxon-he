////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.Navigator;

import javax.xml.transform.TransformerException;

/**
 * The <b>StaticError</b> class contains information about a static error detected during compilation of a stylesheet, query, or XPath expression.
 */
public class StaticError {

    XPathException exception;
    Location locator = null;
    boolean isWarning = false;


    public StaticError(TransformerException err, boolean isWarning){
        exception = XPathException.makeXPathException(err);
        locator = exception.getLocator();
        this.isWarning = isWarning;
    }

     public StaticError(TransformerException err){
        exception = XPathException.makeXPathException(err);
        locator = exception.getLocator();

    }

    public void setWarning(boolean warning) {
        isWarning = warning;
    }

    /**
     * The error code, as a QName. May be null if no error code has been assigned
     *
     * @return QName
     */
    public QName getErrorCode(){
        return new QName(exception.getErrorCodeQName());
    }


    /**
     * Return the error message  associated with this error
     * @return String
     */
    public String getMessage(){
        return exception.getMessage();
    }

    /**
     *  The URI of the query or stylesheet module in which the error was detected (as a string)
     *  May be null if the location of the error is unknown, or if the error is not localized
     *  to a specific module, or if the module in question has no known URI (for example, if
     *  it was supplied as an anonymous Stream)
     *
     * @return String
     */
    public String getModuleUri(){
        return exception.getLocator().getSystemId();
    }


    /**
     *  The coloumn number locating the error within a query or stylesheet module
     *
     * @return int
     */
    public int getColumnNumber() {
        Location locator = exception.getLocator();
        if (locator != null) {
            return locator.getColumnNumber();
        }
        return -1;
    }

    /**
     *  The line number locating the error within a query or stylesheet module
     *
     * @return int
     */
    public int getLineNumber(){
        Location locator = exception.getLocator();
        if (locator != null){
            return locator.getLineNumber();
        }
        return -1;
    }


    /**
     * Get a name identifying the kind of instruction, in terms meaningful to a user. This method
     * is not used in the case where the instruction name code is a standard name (<1024).
     *
     * @return a name identifying the kind of instruction, in terms meaningful to a user.
     *         The name will always be in the form of a lexical XML QName, and should match the name used
     *         in explain() output displaying the instruction.
     */
    public String getInstructionName(){
        return ((NodeInfo)locator).getDisplayName();
    }


    /**
     * Indicate whether this error is being reported as a warning condition.
     * If so, applications may ignore the condition, though the results may not be as intended.
     * @return boolean
     */
    public boolean isWarning(){
        return isWarning;
    }


    /**
     * Indicate whether this condition is a type error.
     * @return boolean
     */
    public boolean isTypeError(){
        return exception.isTypeError();
    }


    /**
     * Get the absolute XPath expression that identifies the node within its document
     *  where the error occurred, if available
     *
     * @return String - a path expression
     */
    public String getPath(){
        NodeInfo node = null;

        if (locator instanceof NodeInfo) {
            node = (NodeInfo) locator;
            return Navigator.getPath(node);
        }
        return null;
    }

    /**
     * The underlying exception is returned. This is unstable as this is an internal object
     *
     * @return XPathException
     */
    public XPathException getUnderlyingException(){
        return exception;
    }

}
