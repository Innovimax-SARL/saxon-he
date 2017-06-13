////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.accum;

import net.sf.saxon.om.TreeInfo;

import java.util.Set;

/**
 * Definition of the class that manages accumulators dynamically.  This is a marker interface (essentially,
 * a dummy implementation for Saxon-HE which does not currently support accumulators)
 */

public interface IAccumulatorManager {

    /**
     * By default, all accumulators are applicable to any given tree. If this method is called,
     * a specific set of accumulators are registered as applicable. This set may be empty.
     *
     * @param tree         the document tree in question
     * @param accumulators the set of accumulators that are appicable
     */

    void setApplicableAccumulators(TreeInfo tree, Set<? extends Accumulator> accumulators);

    /**
     * Ask whether a particular accumulator is applicable to a particular tree
     *
     * @param tree        the tree in question
     * @param accumulator the accumulator in question
     * @return true if the accumulator is applicable to this tree, otherwise false
     */

    boolean isApplicable(TreeInfo tree, Accumulator accumulator);


}

