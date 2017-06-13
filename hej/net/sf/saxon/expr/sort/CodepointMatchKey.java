////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.regex.UnicodeString;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.Base64BinaryValue;

/**
 * A match key for comparing strings (represented as an array of characters) using codepoint collation.
 */
public class CodepointMatchKey implements Comparable, AtomicMatchKey {

    private UnicodeString value;

    public CodepointMatchKey(CharSequence in) {
        value = UnicodeString.makeUnicodeString(in);
    }

    public UnicodeString getValue() {
        return value;
    }

    public int compareTo(Object o) {
        if (o instanceof CodepointMatchKey) {
            return value.compareTo(((CodepointMatchKey) o).value);
        } else {
            throw new ClassCastException();
        }
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof CodepointMatchKey && compareTo(o) == 0);
    }

    /**
     * Get an atomic value that encapsulates this match key. Needed to support the collation-key() function.
     *
     * @return an atomic value that encapsulates this match key
     */
    public AtomicValue asAtomic() {
        return new Base64BinaryValue(value.getCodepointCollationKey());
    }
}


