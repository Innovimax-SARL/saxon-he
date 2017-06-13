////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.trans.XPathException;


/**
 * MessageEmitter is the default Receiver for xsl:message output.
 * It is the same as XMLEmitter except for an extra newline at the end of the message
 */

public class MessageEmitter extends XMLEmitter {

    @Override
    public void processingInstruction(String target, CharSequence data, Location locationId, int properties) throws XPathException {
        if (!suppressProcessingInstruction(target, data, locationId, properties)) {
            super.processingInstruction(target, data, locationId, properties);
        }
    }

    /**
     * Method to decide whether a processing instruction in the message should be suppressed. The default
     * implementation suppresses a processing instruction whose target is "error-code"; this processing
     * instruction is inserted into the message; its data part is an EQName containing the error code.
     * @param target the processing instruction target (that is, name)
     * @param data the data part of the processing instruction
     * @param locationId the location, which in the case of the error-code processing instruction, holds
     *                   the location of the originating xsl:message instruction
     * @param properties currently 0.
     * @return true if the processing instruction is to be suppressed from the displayed message.
     */

    protected boolean suppressProcessingInstruction(String target, CharSequence data, Location locationId, int properties) {
        return target.equals("error-code");
    }

    @Override
    public void endDocument() throws XPathException {
        try {
            if (writer != null) {
                writer.write('\n');
                writer.flush();
            }
        } catch (java.io.IOException err) {
            throw new XPathException(err);
        }
        super.endDocument();
    }

    @Override
    public void close() throws XPathException {
        try {
            if (writer != null) {
                writer.flush();
            }
        } catch (java.io.IOException err) {
            throw new XPathException(err);
        }
        super.close();
    }

}

