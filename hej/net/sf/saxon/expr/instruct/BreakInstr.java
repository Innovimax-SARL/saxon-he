////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;

import java.util.Collections;

/**
 * A compiled xsl:break instruction. The effect of executing this instruction is to register with the
 * dynamic context that a tail call on a pseudo-function break() has been made; the enclosing xsl:iterate
 * loop detects this tail call request and uses it as a signal to terminate execution of the loop.
 */
public class BreakInstr extends Instruction implements TailCallLoop.TailCallInfo {

    /**
     * Create the instruction
     */
    public BreakInstr() {
    }

    @Override
    public Iterable<Operand> operands() {
        return Collections.emptyList();
    }

    /*@NotNull*/
    public Expression copy(RebindingMap rebindings) {
        return this;
    }


    public boolean mayCreateNewNodes() {
        // this is a fiction, but it prevents the instruction being moved to a global variable,
        // which would be pointless and possibly harmful
        return true;
    }

    /**
     * Ask whether the expression can be lifted out of a loop, assuming it has no dependencies
     * on the controlling variable/focus of the loop
     */

    public boolean isLiftable() {
        return false;
    }


    /*@Nullable*/
    public TailCall processLeavingTail(XPathContext context) throws XPathException {
        markContext(context);
        return null;
    }

    public void markContext(XPathContext context) {
        XPathContext c = context;
        while (!(c instanceof XPathContextMajor)) {
            c = c.getCaller();
        }
        ((XPathContextMajor) c).requestTailCall(this, null);
    }

    @Override
    public String getExpressionName() {
        return "xsl:break";
    }

    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("break", this);
        out.endElement();
    }
}

