////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans.rules;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.trans.SimpleMode;

/**
 * A simple class for holding stateful details of a rule search operation
 * Can record call statistics and time.
 * <p>Subclasses can be used to hold information such as precondition sets etc.</p>
 * <p>A new RuleSearchState (or subclass) should be created at the start of each check through a rule set.
 * This can be achieved through {@link SimpleMode#makeRuleSearchState(RuleChain, XPathContext)}.</p>
 */
public class RuleSearchState {

    public RuleSearchState(SimpleMode mode) {

    }


}

