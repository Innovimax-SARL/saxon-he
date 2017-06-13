////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.CardinalityCheckingIterator;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.om.LazySequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;

/**
 * This class supports the XPath 2.0 functions exactly-one(), one-or-more(), zero-or-one().
 * Because Saxon doesn't do strict static type checking, these are essentially identity
 * functions; the run-time type checking is done as part of the function call mechanism
 */

public abstract class TreatFn extends SystemFunction implements Callable {

    /**
     * Return the error code to be used for type errors
     */

    public abstract String getErrorCodeForTypeErrors();

    public abstract int getRequiredCardinality();




    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        SequenceIterator iterator = arguments[0].iterate();
        int card = getRequiredCardinality();
        RoleDiagnostic role = makeRoleDiagnostic();
        iterator = new CardinalityCheckingIterator(iterator, card, role, null);
        return new LazySequence(iterator);
    }

    public RoleDiagnostic makeRoleDiagnostic() {
        RoleDiagnostic role = new RoleDiagnostic(RoleDiagnostic.FUNCTION, getFunctionName().getDisplayName(), 0);
        role.setErrorCode(getErrorCodeForTypeErrors());
        return role;
    }

    public String getStreamerName() {
        return "TreatFn";
    }

    public static class ExactlyOne extends TreatFn {
        public int getRequiredCardinality() {
            return StaticProperty.EXACTLY_ONE;
        }

        public String getErrorCodeForTypeErrors() {
            return "FORG0005";
        }
    }

    public static class OneOrMore extends TreatFn {
        public int getRequiredCardinality() {
            return StaticProperty.ALLOWS_ONE_OR_MORE;
        }

        public String getErrorCodeForTypeErrors() {
            return "FORG0004";
        }
    }

    public static class ZeroOrOne extends TreatFn {
        public int getRequiredCardinality() {
            return StaticProperty.ALLOWS_ZERO_OR_ONE;
        }

        public String getErrorCodeForTypeErrors() {
            return "FORG0003";
        }
    }


}

