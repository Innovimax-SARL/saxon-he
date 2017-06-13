////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.expr.parser.ExplicitLocation;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.transform.ErrorListener;

/**
 * A default implementation of the SAX ErrorHandler interface. Used by Saxon to catch XML parsing errors
 * if no error handler is supplied by the application.
 */

public class StandardErrorHandler implements org.xml.sax.ErrorHandler {

    ////////////////////////////////////////////////////////////////////////////
    // Implement the org.xml.sax.ErrorHandler interface.
    ////////////////////////////////////////////////////////////////////////////

    private ErrorListener errorListener;
    private int warningCount = 0;
    private int errorCount = 0;
    private int fatalErrorCount = 0;
    private boolean silent = false;

    public StandardErrorHandler(ErrorListener listener) {
        if (listener == null) {
            throw new NullPointerException();
        }
        errorListener = listener;
    }

    /**
     * Indicate whether the error handler should report errors to the ErrorListener
     * @param silent if true, errors should not be reported. Used during doc-available() processing
     */

    public void setSilent(boolean silent) {
        this.silent = silent;
    }

    /**
     * Callback interface for SAX: not for application use
     */

    public void warning(SAXParseException e) {
        try {
            warningCount++;
            if (!silent) {
                errorListener.warning(XPathException.makeXPathException(e));
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Callback interface for SAX: not for application use
     */

    public void error(SAXParseException e) throws SAXException {
        //System.err.println("ErrorHandler.error " + e.getMessage());
        errorCount++;
        if (!silent) {
            reportError(e, false);
        }
    }

    /**
     * Callback interface for SAX: not for application use
     */

    public void fatalError(/*@NotNull*/ SAXParseException e) throws SAXException {
        //System.err.println("ErrorHandler.fatalError " + e.getMessage());
        fatalErrorCount++;
        if (!silent) {
            reportError(e, true);
        }
        throw e;
    }

    /**
     * Common routine for SAX errors and fatal errors
     *
     * @param e       the exception being handled
     * @param isFatal true if the error is classified as fatal
     */

    protected void reportError(SAXParseException e, boolean isFatal) {
        try {
            ExplicitLocation loc =
                    new ExplicitLocation(e.getSystemId(), e.getLineNumber(), e.getColumnNumber());
            XPathException err = new XPathException("Error reported by XML parser", loc, e);
            err.setErrorCode(SaxonErrorCode.SXXP0003);
            if (isFatal) {
                errorListener.fatalError(err);
            } else {
                errorListener.error(err);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Return the number of warnings (including warnings) reported
     *
     * @return the number of warnings
     */

    public int getWarningCount() {
        return warningCount;
    }

    /**
     * Return the number of errors reported
     *
     * @return the number of non-fatal errors
     */

    public int getErrorCount() {
        return errorCount;
    }

    /**
     * Return the number of fatal errors reported
     *
     * @return the number of fatal errors
     */

    public int getFatalErrorCount() {
        return fatalErrorCount;
    }
}

