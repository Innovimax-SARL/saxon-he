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

    /**
     * An evaluator for arguments supplied as a literal
     */

    public final static Evaluator LITERAL = new Evaluator() {
        @Override
        public Sequence evaluate(Expression expr, XPathContext context) throws XPathException {
            return ((Literal)expr).getValue();
        }
    };

    /**
     * An evaluator for arguments supplied as a variable reference
     */

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

    /**
     * A (default) evaluator for arguments supplied as an expression that will always return a
     * singleton item
     */

    public final static Evaluator SINGLE_ITEM = new Evaluator() {
        @Override
        public Item evaluate(Expression expr, XPathContext context) throws XPathException {
            return expr.evaluateItem(context);
        }
    };

    /**
     * A (default) evaluator for arguments supplied as an expression that will return either a
     * singleton item, or an empty sequence
     */

    public final static Evaluator OPTIONAL_ITEM = new Evaluator() {
        @Override
        public Sequence evaluate(Expression expr, XPathContext context) throws XPathException {
            Item item = expr.evaluateItem(context);
            return item==null ? EmptySequence.getInstance() : item;
        }
    };

    /**
     * An evaluator for arguments that in general return a sequence, where the sequence is evaluated
     * lazily on first use. This is appropriate when calling a function which might not use the value, or
     * might not use all of it. It returns a {@code LazySequence}, which can only be read once, so
     * this is only suitable for use when calling a function that can be trusted to read the argument
     * once only.
     */

    public final static Evaluator LAZY_SEQUENCE = new Evaluator() {
        @Override
        public Sequence evaluate(Expression expr, XPathContext context) throws XPathException {
            SequenceIterator iter = expr.iterate(context);
            return new LazySequence(iter);
        }
    };

    /**
     * An evaluator for arguments that in general return a sequence, where the sequence is evaluated
     * eagerly. This is appropriate when it is known that the function will always use the entire value,
     * or when it will use it more than once.
     */

    public final static Evaluator EAGER_SEQUENCE = new Evaluator() {
        @Override
        public Sequence evaluate(Expression expr, XPathContext context) throws XPathException {
            SequenceIterator iter = expr.iterate(context);
            return SequenceExtent.makeSequenceExtent(iter);
        }
    };
}



