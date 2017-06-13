////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans.rules;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.instruct.ParameterSet;
import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.om.Item;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.Type;

/**
 * The rule set introduced in XSLT 3.0, which (for any kind of node) simply ignores the node
 * and its descendants.
 */
public class DeepSkipRuleSet implements BuiltInRuleSet {

    private static DeepSkipRuleSet THE_INSTANCE = new DeepSkipRuleSet();

    /**
     * Get the singleton instance of this class
     *
     * @return the singleton instance
     */

    public static DeepSkipRuleSet getInstance() {
        return THE_INSTANCE;
    }

    private DeepSkipRuleSet() {
    }

    /**
     * Perform the built-in template action for a given item.
     *  @param item         the item to be processed
     * @param parameters   the parameters supplied to apply-templates
     * @param tunnelParams the tunnel parameters to be passed through
     * @param context      the dynamic evaluation context
     * @param locationId   location of the instruction (apply-templates, apply-imports etc) that caused
*                     the built-in template to be invoked     @throws net.sf.saxon.trans.XPathException
     */

    public void process(Item item, ParameterSet parameters,
                        ParameterSet tunnelParams, XPathContext context,
                        Location locationId) throws XPathException {
        // do nothing
    }

    /**
     * Get the default action for unmatched nodes
     *
     * @param nodeKind the node kind
     * @return the default action for unmatched element nodes: one of DEEP_COPY, APPLY_TEMPLATES, DEEP_SKIP, FAIL
     */
    public int[] getActionForParentNodes(int nodeKind) {
        if (nodeKind == Type.DOCUMENT) {
            return new int[]{APPLY_TEMPLATES_TO_CHILDREN};
        } else {
            return new int[]{DEEP_SKIP};
        }
    }
}
