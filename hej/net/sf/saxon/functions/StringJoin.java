////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.ZeroOrOne;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.StringValue;

/**
 * fn:string-join(string* $sequence, string $separator)
 */

public class StringJoin extends FoldingFunction {

    private boolean returnEmptyIfEmpty;

    /**
     * Indicate that when the input sequence (first argument) is empty, the function should return
     * an empty sequence rather than an empty string
     *
     * @param option true if an empty sequence should be returned when the input is an empty sequence.
     */

    public void setReturnEmptyIfEmpty(boolean option) {
        returnEmptyIfEmpty = option;
    }

    public boolean isReturnEmptyIfEmpty() {
        return returnEmptyIfEmpty;
    }

    /**
     * Determine the cardinality of the function.
     */
    @Override
    public int getCardinality(Expression[] arguments) {
        if (returnEmptyIfEmpty) {
            return StaticProperty.ALLOWS_ZERO_OR_ONE;
        } else {
            return StaticProperty.EXACTLY_ONE;
        }
    }

    /**
     * Determine whether two expressions are equivalent
     */
    @Override
    public boolean equals(Object o) {
        return (o instanceof StringJoin) &&
                super.equals(o) &&
                returnEmptyIfEmpty == ((StringJoin) o).returnEmptyIfEmpty;
    }

    /**
     * Allow the function to create an optimized call based on the values of the actual arguments
     *
     * @param visitor     the expression visitor
     * @param contextInfo information about the context item
     * @param arguments   the supplied arguments to the function call
     * @return either a function call on this function, or an expression that delivers
     * the same result, or null indicating that no optimization has taken place
     * @throws net.sf.saxon.trans.XPathException if an error is detected
     */
    @Override
    public Expression makeOptimizedFunctionCall(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo, Expression... arguments) throws XPathException {
        Expression e2 = super.makeOptimizedFunctionCall(visitor, contextInfo, arguments);
        if (e2 != null) {
            return e2;
        }
        int card = arguments[0].getCardinality();
        if (!Cardinality.allowsMany(card)) {
            if (Cardinality.allowsZero(card)) {
                return SystemFunction.makeCall("string", getRetainedStaticContext(), arguments[0]);
            } else {
                return arguments[0];
            }
        }
        return null;
    }

    @Override
    public Fold getFold(XPathContext context, Sequence... additionalArguments) throws XPathException {
        CharSequence separator = "";
        if (additionalArguments.length > 0) {
            separator = additionalArguments[0].head().getStringValueCS();
        }
        return new StringJoinFold(separator);
    }

    private class StringJoinFold implements Fold {

        private int position = 0;
        private CharSequence separator;
        private FastStringBuffer data;

        public StringJoinFold(CharSequence separator) {
            this.separator = separator;
            this.data = new FastStringBuffer(FastStringBuffer.C64);
        }

        /**
         * Process one item in the input sequence, returning a new copy of the working data
         *
         * @param item the item to be processed from the input sequence
         * @throws net.sf.saxon.trans.XPathException if a dynamic error occurs
         */
        public void processItem(Item item) throws XPathException {
            if (position == 0) {
                data.append(item.getStringValueCS());
                position = 1;
            } else {
                data.append(separator);
                data.append(item.getStringValueCS());
            }
        }

        /**
         * Ask whether the computation has completed. A function that can deliver its final
         * result without reading the whole input should return true; this will be followed
         * by a call on result() to deliver the final result.
         */
        public boolean isFinished() {
            return false;
        }

        /**
         * Compute the final result of the function, when all the input has been processed
         * @return the result of the function
         * @throws net.sf.saxon.trans.XPathException
         *          if a dynamic error occurs
         */
        public ZeroOrOne<StringValue> result() throws XPathException {
            if (position == 0 && returnEmptyIfEmpty) {
                return ZeroOrOne.empty();
            } else {
                return new ZeroOrOne<StringValue>(new StringValue(data));
            }
        }
    }

    public String getCompilerName() {
        return "StringJoinCompiler";
    }


}

