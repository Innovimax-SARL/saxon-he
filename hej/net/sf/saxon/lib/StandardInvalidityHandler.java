////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.instruct.*;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trace.InstructionInfo;
import net.sf.saxon.trans.KeyDefinition;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.type.ValidationException;
import net.sf.saxon.type.ValidationFailure;
import net.sf.saxon.value.EmptySequence;

import javax.xml.transform.SourceLocator;
import javax.xml.transform.dom.DOMLocator;

/**
 * This class <code>StandardInvalidityHandler</code>, despite its name, is not directly used by Saxon, and in particular
 * it is NOT the default InvalidityHandler. It is however available for use by applications, either directly or by
 * subclassing. (The default InvalidityHandler wraps a supplied ErrorListener).
 *
 * This InvalidityHandler logs validation error messages to a supplied {@link Logger}, using the Logger belonging
 * to the supplied {@link Configuration} as the default destination.
 */

public class StandardInvalidityHandler implements InvalidityHandler {

    private Configuration config;
    private Logger logger;

    /**
     * Create a Standard Invalidity Handler
     * @param config the Saxon configuration
     */

    public StandardInvalidityHandler(Configuration config) {
        this.config = config;
    }

    /**
     * Set output destination for error messages (default is the Logger registered with the Configuration)
     *
     * @param logger The Logger to use for error messages
     */

    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    /**
     * Get the error output stream
     *
     * @return the error output stream
     */

    public Logger getLogger() {
        return logger;
    }

    public Configuration getConfiguration(){
        return config;
    }

    /**
     * At the start of a validation episode, initialize the handler
     *
     * @param systemId optional; may be used to represent the destination of any report produced
     * @throws XPathException if initialization of the invalidity handler fails for any reason
     */
    public void startReporting(String systemId) throws XPathException {
        // no action
    }

    /**
     * Receive notification of a validity error.
     * @param failure Information about the nature of the invalidity
     */

    public void reportInvalidity(Invalidity failure) throws XPathException{
        Logger localLogger = logger;
        if (localLogger == null) {
            localLogger = config.getLogger();
        }

        String explanation = getExpandedMessage(failure);
        String constraintReference = getConstraintReferenceMessage(failure);
        String validationLocation = ((ValidationFailure) failure).getValidationLocationText();
        String contextLocation = ((ValidationFailure) failure).getContextLocationText();
        String finalMessage = "Validation error " +
                getLocationMessage(failure) +
                "\n  " +
                wordWrap(explanation) +
                //wordWrap(validationLocation.isEmpty() ? "" : "\n  " + validationLocation) +
                wordWrap(contextLocation.isEmpty() ? "" : "\n  " + contextLocation) +
                wordWrap(constraintReference == null ? "" : "\n  " + constraintReference) +
                StandardErrorListener.getOffenderListText((ValidationFailure)failure);

        localLogger.error(finalMessage);
    }


    /**
     * Get a string identifying the location of an error.
     *
     * @param err the exception containing the location information
     * @return a message string describing the location
     */

    public String getLocationMessage(Invalidity err) {
        String locMessage = "";
        String systemId = null;
        NodeInfo node = ((ValidationFailure)err).getInvalidNode();
        String path;
        String nodeMessage = null;
        int lineNumber = -1;
        SourceLocator loc = err;
        if (loc instanceof DOMLocator) {
            nodeMessage = "at " + ((DOMLocator) loc).getOriginatingNode().getNodeName() + ' ';
        } else if (loc instanceof ValidationException && loc.getLineNumber() == -1 && (path = ((ValidationException) loc).getPath()) != null) {
            nodeMessage = "at " + path + ' ';
        } else if (loc instanceof Instruction) {
            String instructionName = StandardErrorListener.getInstructionName((Instruction) loc);
            if (!"".equals(instructionName)) {
                nodeMessage = "at " + instructionName + ' ';
            }
            systemId = loc.getSystemId();
            lineNumber = loc.getLineNumber();
        } else if (loc instanceof Actor) {
            String kind = "procedure";
            if (loc instanceof UserFunction) {
                kind = "function";
            } else if (loc instanceof NamedTemplate) {
                kind = "template";
            } else if (loc instanceof AttributeSet) {
                kind = "attribute-set";
            } else if (loc instanceof KeyDefinition) {
                kind = "key";
            }
            systemId = loc.getSystemId();
            lineNumber = loc.getLineNumber();
            nodeMessage = "at " + kind + " ";
            StructuredQName name = ((InstructionInfo) loc).getObjectName();
            if (name != null) {
                nodeMessage += name.toString();
                nodeMessage += " ";
            }
        }
        if (lineNumber == -1) {
            lineNumber = loc.getLineNumber();
        }
        boolean containsLineNumber = lineNumber != -1;
        if (node != null) {
            nodeMessage = "at " + Navigator.getPath(node) + ' ';
        }
        if (nodeMessage != null) {
            locMessage += nodeMessage;
        }
        if (containsLineNumber) {
            locMessage += "on line " + lineNumber + ' ';
            if (loc.getColumnNumber() != -1) {
                locMessage += "column " + loc.getColumnNumber() + ' ';
            }
        }

        if (systemId != null && systemId.isEmpty()) {
            systemId = null;
        }
        if (systemId == null) {
            systemId = loc.getSystemId();
        }
        if (systemId != null && systemId.length() != 0) {
            locMessage += (containsLineNumber ? "of " : "in ") + abbreviatePath(systemId) + ':';
        }
        return locMessage;
    }

    /**
     * Abbreviate a URI (if requested)
     *
     * @param uri the URI to be abbreviated
     * @return the abbreviated URI, unless full path names were requested, in which case
     *         the URI as supplied
     */

    /*@Nullable*/
    public static String abbreviatePath(String uri) {
        if (uri == null) {
            return "*unknown*";
        }
        int slash = uri.lastIndexOf('/');
        if (slash >= 0 && slash < uri.length() - 1) {
            return uri.substring(slash + 1);
        } else {
            return uri;
        }
    }

    /**
     * Get a string containing the message for this exception and all contained exceptions
     *
     * @param err the exception containing the required information
     * @return a message that concatenates the message of this exception with its contained exceptions,
     *         also including information about the error code and location.
     */

    public String getExpandedMessage(Invalidity err) {
        String code = err.getErrorCode();
        return (code==null ? "" : code + ": ") + err.getMessage();
    }

    /**
     * Wordwrap an error message into lines of 72 characters or less (if possible)
     *
     * @param message the message to be word-wrapped
     * @return the message after applying word-wrapping
     */

    private static String wordWrap(String message) {
        return StandardErrorListener.wordWrap(message);
    }

    /**
     * Get the constraint reference as a string for inserting into an error message.
     *
     * @return the reference as a message, or null if no information is available
     */

    /*@Nullable*/
    public static String getConstraintReferenceMessage(Invalidity err) {
        if (err.getSchemaPart() == -1) {
            return null;
        }
        return "See http://www.w3.org/TR/xmlschema-" + err.getSchemaPart() + "/#" + err.getConstraintName()
            + " clause " + err.getConstraintClauseNumber();
    }

    /**
     * Get the value to be associated with a validation exception. May return null.
     * In the case of the InvalidityReportGenerator, this returns the XML document
     * containing the validation report
     *
     * @return a value (or null). This will be the value returned as the value of
     * the variable $err:value during try/catch processing
     */
    public Sequence endReporting() throws XPathException {
        return EmptySequence.getInstance();
    }
}

