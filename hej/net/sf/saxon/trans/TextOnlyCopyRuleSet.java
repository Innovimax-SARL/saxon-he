////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.XPathContextMajor;
import net.sf.saxon.expr.instruct.ParameterSet;
import net.sf.saxon.expr.instruct.TailCall;
import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.rules.BuiltInRuleSet;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.AtomicValue;

/**
 * The built-in rule set used for 1.0 and 2.0, which for document and element nodes does an apply-templates
 * to children, and for text nodes and attribute nodes copies the node.
 */
public class TextOnlyCopyRuleSet implements BuiltInRuleSet {

    private static TextOnlyCopyRuleSet THE_INSTANCE = new TextOnlyCopyRuleSet();

    /**
     * Get the singleton instance of this class
     *
     * @return the singleton instance
     */

    public static TextOnlyCopyRuleSet getInstance() {
        return THE_INSTANCE;
    }

    private TextOnlyCopyRuleSet() {
    }

    /**
     * Perform the built-in template action for a given item.
     *
     * @param item         the item to be processed
     * @param parameters   the parameters supplied to apply-templates
     * @param tunnelParams the tunnel parameters to be passed through
     * @param context      the dynamic evaluation context
     * @param locationId   location of the instruction (apply-templates, apply-imports etc) that caused
     *                     the built-in template to be invoked
     * @throws net.sf.saxon.trans.XPathException
     *          if any dynamic error occurs
     */

    public void process(Item item, ParameterSet parameters,
                        ParameterSet tunnelParams, /*@NotNull*/ XPathContext context,
                        Location locationId) throws XPathException {
        if (item instanceof NodeInfo) {
            NodeInfo node = (NodeInfo) item;
            switch (node.getNodeKind()) {
                case Type.DOCUMENT:
                case Type.ELEMENT:
                    FocusIterator iter = new FocusTrackingIterator(node.iterateAxis(AxisInfo.CHILD));
                    XPathContextMajor c2 = context.newContext();
                    c2.setOrigin(this);
                    c2.setCurrentIterator(iter);
                    TailCall tc = c2.getCurrentMode().getActor().applyTemplates(parameters, tunnelParams, c2, locationId);
                    while (tc != null) {
                        tc = tc.processLeavingTail();
                    }
                    return;
                case Type.TEXT:
                    // NOTE: I tried changing this to use the text node's copy() method, but
                    // performance was worse
                case Type.ATTRIBUTE:
                    context.getReceiver().characters(item.getStringValueCS(), locationId, 0);
                    return;
                case Type.COMMENT:
                case Type.PROCESSING_INSTRUCTION:
                case Type.NAMESPACE:
                    // no action
            }
        } else if (item instanceof AtomicValue) {
            context.getReceiver().characters(item.getStringValueCS(), locationId, 0);
        } else {
            // no action (e.g. for function items
        }
    }

    /**
     * Get the default action for unmatched nodes
     *
     * @param nodeKind the node kind
     * @return the default action for unmatched nodes: one of DEEP_COPY, SHALLOW_SKIP, DEEP_SKIP, FAIL, etc
     */
    public int[] getActionForParentNodes(int nodeKind) {
        return new int[]{APPLY_TEMPLATES_TO_CHILDREN};
    }
}

