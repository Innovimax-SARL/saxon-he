////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import javax.xml.transform.stream.StreamResult;

/**
 * Interface to diagnostic event logging mechanism.
 * A Logger can be registered at the level of the Saxon Configuration.
 * The default implementation for the Java platform writes all messages to System.err
 */
public abstract class Logger {

    public static final int INFO = 0;
    public static final int WARNING = 1;
    public static final int ERROR = 2;
    public static final int DISASTER = 3;

    /**
     * Log a message with level {@link Logger#INFO}
     * @param message the message to be logged
     */
    public void info(String message) {
        println(message, INFO);
    }

    /**
     * Log a message with level {@link Logger#WARNING}
     * @param message the message to be logged
     */
    public void warning(String message) {
        println(message, WARNING);
    }

    /**
     * Log a message with level {@link Logger#ERROR}
     * @param message the message to be logged
     */
    public void error(String message) {
        println(message, ERROR);
    }

    /**
     * Log a message with level {@link Logger#DISASTER}
     * @param message the message to be logged
     */
    public void disaster(String message) {
        println(message, DISASTER);
    }

    /**
     * Log a message. To be implemented in a concrete subclass
     * @param message The message to be output
     * @param severity The severity level. One of {@link Logger#INFO}, {@link Logger#WARNING}, {@link Logger#ERROR},
     * {@link Logger#DISASTER}
     */

    public abstract void println(String message, int severity);

    /**
     * Close the logger, indicating that no further messages will be written
     * and that underlying streams should be closed, if they were created by the Logger
     * itself rather than by the user.
     */

    public void close() {}

    /**
     * Get a JAXP StreamResult object allowing serialized XML to be written to this Logger
     * @return a StreamResult that serializes XML to this Logger
     */

    public abstract StreamResult asStreamResult();
}

