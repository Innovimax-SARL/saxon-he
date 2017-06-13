////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trace;

import net.sf.saxon.expr.flwor.Clause;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.trans.Mode;

/**
 * A Simple trace listener for XQuery that writes messages (by default) to System.err
 */

public class XQueryTraceListener extends AbstractTraceListener {

    /**
     * Generate attributes to be included in the opening trace element
     */

    /*@NotNull*/
    protected String getOpeningAttributes() {
        return "";
    }

    /**
     * Get the trace element tagname to be used for a particular construct. Return null for
     * trace events that are ignored by this trace listener.
     */

    /*@Nullable*/
    protected String tag(int construct) {
        switch (construct) {
            case StandardNames.XSL_FUNCTION:
                return "function";
            case StandardNames.XSL_VARIABLE:
                return "variable";
            case StandardNames.XSL_ELEMENT:
                return "element";
            case StandardNames.XSL_ATTRIBUTE:
                return "attribute";
            case StandardNames.XSL_COMMENT:
                return "comment";
            case StandardNames.XSL_DOCUMENT:
                return "document";
            case StandardNames.XSL_PROCESSING_INSTRUCTION:
                return "processing-instruction";
            case StandardNames.XSL_TEXT:
                return "text";
            case StandardNames.XSL_NAMESPACE:
                return "namespace";
            case LocationKind.LITERAL_RESULT_ELEMENT:
                return "element";
            case LocationKind.LITERAL_RESULT_ATTRIBUTE:
                return "attribute";
            case LocationKind.FUNCTION_CALL:
                //return "function-call";
                return null;
            case LocationKind.FOR_EXPRESSION:
                return "for";
            case LocationKind.LET_EXPRESSION:
                return "let";
            case LocationKind.WHERE_CLAUSE:
                return "where";
            case LocationKind.ORDER_BY_CLAUSE:
                return "sort";
            case LocationKind.RETURN_EXPRESSION:
                return "return";
            case LocationKind.COPY_MODIFY_EXPRESSION:
                return "modify";
            case LocationKind.INSERT_EXPRESSION:
                return "insert";
            case LocationKind.DELETE_EXPRESSION:
                return "delete";
            case LocationKind.REPLACE_EXPRESSION:
                return "replace";
            case LocationKind.RENAME_EXPRESSION:
                return "rename";
            case LocationKind.TYPESWITCH_EXPRESSION:
                return "typeswitch";
            case LocationKind.VALIDATE_EXPRESSION:
                return "validate";
            case LocationKind.IF_EXPRESSION:
                return "if";
            case LocationKind.THEN_EXPRESSION:
                return "then";
            case LocationKind.ELSE_EXPRESSION:
                return "else";
            case LocationKind.CASE_EXPRESSION:
                return "case";
            case LocationKind.SWITCH_EXPRESSION:
                return "switch";
            case LocationKind.DEFAULT_EXPRESSION:
                return "default";
            case LocationKind.TRACE_CALL:
                return "user-trace";
            case LocationKind.CLAUSE_BASE + Clause.COUNT:
                return "count";
            case LocationKind.CLAUSE_BASE + Clause.FOR:
                return "for";
            case LocationKind.CLAUSE_BASE + Clause.LET:
                return "let";
            case LocationKind.CLAUSE_BASE + Clause.GROUPBYCLAUSE:
                return "group-by";
            case LocationKind.CLAUSE_BASE + Clause.ORDERBYCLAUSE:
                return "order-by";
            case LocationKind.CLAUSE_BASE + Clause.WHERE:
                return "where";
            case LocationKind.CLAUSE_BASE + Clause.WINDOW:
                return "window";
            default:
                //return "Other";
                return null;
        }
    }
    /**
     * Called at the start of a rule search
     */
    @Override
    public void startRuleSearch() {
        // do nothing
    }

    /**
     * Called at the end of a rule search
     * @param rule the rule that has been selected
     * @param mode
     * @param item
     */
    @Override
    public void endRuleSearch(Object rule, Mode mode, Item item) {
        // do nothing
    }

}

