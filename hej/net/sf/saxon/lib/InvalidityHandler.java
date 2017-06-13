////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;

/**
 * Interface for reporting validation errors found during validation of an instance document
 * against a schema.
 */
public interface InvalidityHandler {

    /**
     * At the start of a validation episode, initialize the handler
     * @param systemId optional; may be used to represent the destination of any report produced
     * @throws XPathException if initialization of the invalidity handler fails for any reason
     */

    void startReporting(String systemId) throws XPathException;

    /**
     * Report a validation error found during validation of an instance document
     * against a schema
     * @param failure details of the validation error
     * @throws XPathException - if the validation error cannot be reported.
     * This is fatal and will cause the validation run to be abandoned
     */

    void reportInvalidity(Invalidity failure) throws XPathException;

    /**
     * At the end of a validation episode, do any closedown actions, and optionally return
     * information collected in the course of validation (for example a list of error messages).
     * @return a value to be associated with a validation exception. May be the empty sequence.
     * In the case of the InvalidityReportGenerator, this returns the XML document
     * containing the validation report. This will be the value returned as the value of
     * the variable $err:value during try/catch processing
     * @throws XPathException if an error occurs creating any validation report
     */

    Sequence endReporting() throws XPathException;
}


