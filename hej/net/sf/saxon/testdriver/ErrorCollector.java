////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.testdriver;

import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.StandardErrorListener;
import net.sf.saxon.trans.QuitParsingException;
import net.sf.saxon.trans.XPathException;

import javax.xml.transform.TransformerException;
import java.util.HashSet;
import java.util.Set;

public class ErrorCollector extends StandardErrorListener {

    private Set<String> errorCodes = new HashSet<String>();
    private boolean foundWarnings = false;
    private boolean madeEarlyExit = false;

    public TransformerException lastException;

    /**
     * Receive notification of a warning.
     * <p/>
     * <p>Transformers can use this method to report conditions that
     * are not errors or fatal errors.  The default behaviour is to
     * take no action.</p>
     * <p/>
     * <p>After invoking this method, the Transformer must continue with
     * the transformation. It should still be possible for the
     * application to process the document through to the end.</p>
     *
     * @param exception The warning information encapsulated in a
     *                  transformer exception.
     * @see javax.xml.transform.TransformerException
     */
    @Override
    public void warning(TransformerException exception) {
        if (exception instanceof QuitParsingException) {
            System.err.println("** Early exit from parsing");
            madeEarlyExit = true;
        } else {
            foundWarnings = true;
        }
        super.warning(exception);
    }

    @Override
    public void error(TransformerException exception) {
        lastException = exception;
        addErrorCode(exception);
        super.error(exception);
    }

    @Override
    public void fatalError(TransformerException exception) {
        addErrorCode(exception);
        super.fatalError(exception);
    }

    /**
     * Make a clean copy of this ErrorListener. This is necessary because the
     * standard error listener is stateful (it remembers how many errors there have been)
     *
     * @param hostLanguage the host language (not used by this implementation)
     * @return a copy of this error listener
     */
    @Override
    public StandardErrorListener makeAnother(int hostLanguage) {
        return this;
    }

    private void addErrorCode(TransformerException exception) {
        if (exception instanceof XPathException) {
            String errorCode = ((XPathException) exception).getErrorCodeLocalPart();
            if (errorCode != null) {
                String ns = ((XPathException) exception).getErrorCodeNamespace();
                if (ns != null && !NamespaceConstant.ERR.equals(ns)) {
                    errorCode = "Q{" + ns + "}" + errorCode;
                }
                errorCodes.add(errorCode);
            } else {
                errorCodes.add("error-with-no-error-code");
            }
        }
    }

    public Set<String> getErrorCodes() {
        return errorCodes;
    }

    public boolean getFoundWarnings() {
        return foundWarnings;
    }

    public boolean isMadeEarlyExit() {
        return madeEarlyExit;
    }
}