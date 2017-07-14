////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2014 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.cpp;

import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.trans.XPathException;

/**
 * * This class is to use with Saxon on C++
 */
public class SaxonCException extends SaxonApiException {
    private String errorCode;
    private int linenumber = -1;
    private boolean isTypeError = false;
    private boolean isStaticError = false;
    private boolean isGlobalError = false;

    public SaxonCException(Throwable cause) {
        super(cause);
        errorCode = "";
        if(cause instanceof XPathException) {
            XPathException exception = (XPathException)cause;
            isStaticError = exception.isStaticError();
            isTypeError = exception.isTypeError();
            StructuredQName errorCodeQN = exception.getErrorCodeQName();
            if(errorCodeQN != null) {
                errorCode = errorCodeQN.toString();

            } else {
                errorCode = "";
            }
        }

    }

   /*public static SaxonCException makeSaxonCException(Throwable cause){
        SaxonCException cException = new SaxonCException(cause);
        if(cause instanceof XPathException) {
            StructuredQName qname = ((XPathException) cause).getErrorCodeQName();
              if(qname != null) {
                  cException.set

              }
        }
    }    */



    /**
     * Create a SaxonApiException
     *
     * @param message the message
     */

    public SaxonCException(String message) {
        super(message);
        errorCode = "";

    }

    public SaxonCException(String message, Throwable cause) {
        super(message, cause);
        if(cause instanceof XPathException) {
            XPathException exception = (XPathException)cause;
            isStaticError = exception.isStaticError();
            isTypeError = exception.isTypeError();
            errorCode = exception.getErrorCodeQName().toString();
        }

    }



    public String getErrorCodeString(){
        if(errorCode != null) {
            return errorCode;
        } else if (super.getErrorCode() != null){
            return super.getErrorCode().toString();
        } else {
            return "";
        }

    }

    public String getErrorMessage(){
        String message = super.getMessage();
        if(message == null) {
            return "";
        }
        return message;
    }

    public int getLinenumber(){
        return linenumber;
    }

    public boolean isGlobalError(){
        return isGlobalError;
    }

     public boolean isTypeError(){
        return isTypeError;
    }

    public boolean isStaticError(){
        return isStaticError;
    }
}
