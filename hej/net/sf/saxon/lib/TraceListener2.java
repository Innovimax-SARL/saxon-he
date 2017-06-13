////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.om.Item;
import net.sf.saxon.trans.Mode;

/**
 * This interface defines methods that are called by Saxon during the execution of
 * a stylesheet, if tracing is switched on. Tracing can be switched on by nominating
 * an implementation of this class using the TRACE_LISTENER feature of the TransformerFactory,
 * or using the addTraceListener() method of the Controller, which is Saxon's implementation
 * of the JAXP javax.xml.transform.Transformer interface.
 *
 * @since 9.7. The TraceListener2 interface extends the TraceListener interface, which
 * is retained for compatibility.
 */

public interface TraceListener2 extends TraceListener {

    /**
     * Method called when a search for a template rule is about to start
     */

    void startRuleSearch();

    /**
     * Method called when a rule search has completed.
     * @param rule the rule (or possible built-in ruleset) that has been selected
     * @param mode the mode in operation
     * @param item  the item that was checked against
     */

    void endRuleSearch(Object rule, Mode mode, Item item);
}

