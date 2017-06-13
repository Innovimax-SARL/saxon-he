////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans.rules;

import net.sf.saxon.expr.ContextOriginator;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.instruct.ParameterSet;
import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.om.Item;
import net.sf.saxon.trans.XPathException;

/**
 * Defines a set of built-in template rules (rules for use when no user-defined template
 * rules match a given node)
 */
public interface BuiltInRuleSet extends ContextOriginator {

    /**
     * Perform the built-in template action for a given item.
     *
     * @param item         the item to be processed
     * @param parameters   the parameters supplied to apply-templates
     * @param tunnelParams the tunnel parameters to be passed through
     * @param context      the dynamic evaluation context
     * @param locationId   location of the instruction (apply-templates, apply-imports etc) that caused
     *                     the built-in template to be invoked
     * @throws XPathException if any dynamic error occurs
     */

    void process(Item item,
                 ParameterSet parameters,
                 ParameterSet tunnelParams,
                 XPathContext context,
                 Location locationId) throws XPathException;

    /**
     * Get the action for unmatched element and document nodes (used when streaming)
     *
     * @param nodeKind the node kind: either Type.DOCUMENT or Type.ELEMENT
     * @return the sequence of actions to be taken
     */

    int[] getActionForParentNodes(int nodeKind);

    int DEEP_COPY = 1;
    int DEEP_SKIP = 3;
    int FAIL = 4;
    int SHALLOW_COPY = 5;
    int APPLY_TEMPLATES_TO_ATTRIBUTES = 6;
    int APPLY_TEMPLATES_TO_CHILDREN = 7;

}

