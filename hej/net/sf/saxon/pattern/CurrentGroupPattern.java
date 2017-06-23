////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;

/**
 * This is a special pattern that matches the "anchor node". It is used for the selectors
 * that arise when evaluating XPath expressions in streaming mode; the anchor
 * node is the context node for the streamed XPath evaluation.
 *
 * Given a streamed evaluation of an expression such as ./BOOKS/BOOK/PRICE, the way we evaluate
 * this is to turn it into a pattern, which is then tested against all descendant nodes.
 * Conceptually the pattern is $A/BOOKS/BOOK/PRICE, where $A is referred to as the anchor
 * node. When we evaluate the pattern against (say) a PRICE element, the match will only succeed
 * if the name of the element is "PRICE" and its ancestors are, in order, a BOOK element, a
 * BOOKS element, and the anchor node $A.
 */
public class CurrentGroupPattern extends AnchorPattern {

    private static CurrentGroupPattern THE_INSTANCE = new CurrentGroupPattern();

    public static CurrentGroupPattern getInstance() {
        return THE_INSTANCE;
    }

    protected CurrentGroupPattern() {
    }

    /**
     * Ask whether the pattern is anchored on a call on current-group()
     *
     * @return true if calls on matchesBeneathAnchor should test with all nodes in the
     * current group as anchor nodes. If false, only the first node in a group is
     * treated as the anchor node
     */

    public boolean matchesCurrentGroup() {
        return true;
    }


    @Override
    public void export(ExpressionPresenter presenter) throws XPathException {
        presenter.startElement("p.currentGroup");
        presenter.endElement();
    }

}

