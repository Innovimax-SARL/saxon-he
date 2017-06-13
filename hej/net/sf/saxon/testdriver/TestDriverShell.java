////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.testdriver;

/**
 * Abstraction of the user interface shell for the test driver: allows either a GUI or a command line interface.
 */
public class TestDriverShell {

    public void alert(String error) {
    }

    public void println(String data) {
        System.err.println(data);
    }

    public void printResults(String message, String resultFileStr, String resultsDir) {
        System.err.println(message);
    }
}

