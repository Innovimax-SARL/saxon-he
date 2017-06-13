////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.sort.AtomicSortComparer;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.value.AtomicValue;

/**
 * A comparer that compares atomic values for equality, with the properties:
 * - non-comparable types compare false
 * - NaN compares equal to NaN
 */
public class EquivalenceComparer extends AtomicSortComparer {

    protected EquivalenceComparer(StringCollator collator, int itemType, XPathContext context) {
        super(collator, itemType, context);
    }

    /**
     * Compare two values that are known to be non-comparable. In the base class this method
     * throws a ClassCastException. In this subclass it is overridden to return
     * {@link net.sf.saxon.om.SequenceTool#INDETERMINATE_ORDERING}
     */

    @Override
    protected int compareNonComparables(AtomicValue a, AtomicValue b) {
        return SequenceTool.INDETERMINATE_ORDERING;
    }

    /**
     * Create a string representation of this AtomicComparer that can be saved in a compiled
     * package and used to reconstitute the AtomicComparer when the package is reloaded
     *
     * @return a string representation of the AtomicComparer
     */
    @Override
    public String save() {
        return "EQUIV|" + super.save();
    }
}

// Copyright (c) 2010 Saxonica Limited.



