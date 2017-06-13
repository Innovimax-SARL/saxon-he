////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.z;

/**
 * An IntPredicate formed as the difference of two other predicates: it matches
 * an integer if the first operand matches the integer and the second does not
 */

public class IntExceptPredicate implements IntPredicate {

    private IntPredicate p1;
    private IntPredicate p2;

    public IntExceptPredicate(IntPredicate p1, IntPredicate p2) {
        this.p1 = p1;
        this.p2 = p2;
    }

    /**
     * Ask whether a given value matches this predicate
     *
     * @param value the value to be tested
     * @return true if the value matches; false if it does not
     */
    public boolean matches(int value) {
        return p1.matches(value) && !p2.matches(value);
    }

    /**
     * Get the operands
     *
     * @return an array containing the two operands
     */

    public IntPredicate[] getOperands() {
        return new IntPredicate[]{p1, p2};
    }
}

