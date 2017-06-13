////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.parser;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.VariableReference;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.LazySequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.SequenceExtent;

/**
 * An Evaluator evaluates an expression to return a sequence
 */
public abstract class Evaluator {

    /**
     * Evaluate an expression to return a sequence
     * @param expr the expression to be evaluated
     * @param context the dynamic context for evaluation
     * @return the result of the evaluation
     * @throws XPathException if any dynamic error occurs during the evaluation
     */

    public abstract Sequence evaluate(Expression expr, XPathContext context) throws XPathException;

    public final static Evaluator LITERAL = new Evaluator() {
        @Override
        public Sequence evaluate(Expression expr, XPathContext context) throws XPathException {
            return ((Literal)expr).getValue();
        }
    };

    public final static Evaluator VARIABLE = new Evaluator() {
        @Override
        public Sequence evaluate(Expression expr, XPathContext context) throws XPathException {
            try {
                return ((VariableReference) expr).evaluateVariable(context);
            } catch (ClassCastException e) {
                // should not happen
                assert false;
                return LAZY_SEQUENCE.evaluate(expr, context);
            }
        }
    };

    public final static Evaluator SINGLE_ITEM = new Evaluator() {
        @Override
        public Item evaluate(Expression expr, XPathContext context) throws XPathException {
            return expr.evaluateItem(context);
        }
    };

    public final static Evaluator OPTIONAL_ITEM = new Evaluator() {
        @Override
        public Sequence evaluate(Expression expr, XPathContext context) throws XPathException {
            Item item = expr.evaluateItem(context);
            return item==null ? EmptySequence.getInstance() : item;
        }
    };

    public final static Evaluator LAZY_SEQUENCE = new Evaluator() {
        @Override
        public Sequence evaluate(Expression expr, XPathContext context) throws XPathException {
            SequenceIterator iter = expr.iterate(context);
            return new LazySequence(iter);
        }
    };

    public final static Evaluator EAGER_SEQUENCE = new Evaluator() {
        @Override
        public Sequence evaluate(Expression expr, XPathContext context) throws XPathException {
            SequenceIterator iter = expr.iterate(context);
            return SequenceExtent.makeSequenceExtent(iter);
        }
    };
}



