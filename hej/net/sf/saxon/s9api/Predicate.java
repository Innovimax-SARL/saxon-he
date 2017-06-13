////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

/**
 * A generic class representing a callback to test whether a supplied value matches a condition or not.
 */

public interface Predicate<T extends Object> {

    /**
     * Ask whether a given value matches the predicate
     * @param value the value to be tested
     * @return true if the value matches the predicate, false otherwise
     */

    boolean matches(T value);
}

