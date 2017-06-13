////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.parser.*;
import net.sf.saxon.expr.sort.AtomicComparer;
import net.sf.saxon.expr.sort.DoubleSortComparer;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.NumericValue;

/**
 * This class implements a comparison of a numeric value to an integer constant using one of the operators
 * eq, ne, lt, gt, le, ge. The semantics are identical to ValueComparison, but this is a fast path for an
 * important common case.
 */

public class CompareToIntegerConstant extends UnaryExpression implements ComparisonExpression {

    private long comparand;
    private int operator;

    /**
     * Create the expression
     *
     * @param operand   the operand to be compared with an integer constant. This must
     *                  have a static type of NUMERIC, and a cardinality of EXACTLY ONE
     * @param operator  the comparison operator,
     *                  one of {@link Token#FEQ}, {@link Token#FNE}, {@link Token#FGE},
     *                  {@link Token#FGT}, {@link Token#FLE}, {@link Token#FLT}
     * @param comparand the integer constant
     */

    public CompareToIntegerConstant(Expression operand, int operator, long comparand) {
        super(operand);
        this.operator = operator;
        this.comparand = comparand;
    }

    @Override
    protected OperandRole getOperandRole() {
        return OperandRole.SINGLE_ATOMIC;
    }

    public Expression getLhsExpression() {
        return getBaseExpression();
    }

    public Operand getLhs() {
        return getOperand();
    }

    public Expression getRhsExpression() {
        return new Literal(new Int64Value(comparand));
    }

    public Operand getRhs() {
        return new Operand(this, getRhsExpression(), OperandRole.SINGLE_ATOMIC);
    }

    /**
     * Get the integer value on the rhs of the expression
     *
     * @return the integer constant
     */

    public long getComparand() {
        return comparand;
    }

    /**
     * Get the comparison operator
     *
     * @return one of {@link Token#FEQ}, {@link Token#FNE}, {@link Token#FGE},
     *         {@link Token#FGT}, {@link Token#FLE}, {@link Token#FLT}
     */

    public int getComparisonOperator() {
        return operator;
    }

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is provided directly. The other methods will always be available
     * indirectly, using an implementation that relies on one of the other methods.
     *
     * @return the value {@link #EVALUATE_METHOD}
     */

    public int getImplementationMethod() {
        return EVALUATE_METHOD;
    }

    public int computeSpecialProperties() {
        return StaticProperty.NON_CREATIVE;
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings
     */

    /*@NotNull*/
    public Expression copy(RebindingMap rebindings) {
        CompareToIntegerConstant c2 = new CompareToIntegerConstant(getLhsExpression().copy(rebindings), operator, comparand);
        ExpressionTool.copyLocationInfo(this, c2);
        return c2;
    }

     /**
     * Is this expression the same as another expression?
     *
     * @param other the expression to be compared with this one
     * @return true if the two expressions are statically equivalent
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof CompareToIntegerConstant && ((CompareToIntegerConstant)other).getLhsExpression().equals(getLhsExpression())
                && ((CompareToIntegerConstant)other).comparand == comparand
                && ((CompareToIntegerConstant)other).operator == operator;
    }

    /**
     * Hashcode supporting equals()
     */

    public int hashCode() {
        int h = 0x836b12a0;
        return h + getLhsExpression().hashCode() ^ (int)comparand;
    }



    /**
     * Evaluate an expression as a single item. This always returns either a single Item or
     * null (denoting the empty sequence). No conversion is done. This method should not be
     * used unless the static type of the expression is a subtype of "item" or "item?": that is,
     * it should not be called if the expression may return a sequence. There is no guarantee that
     * this condition will be detected.
     *
     * @param context The context in which the expression is to be evaluated
     * @return the node or atomic value that results from evaluating the
     *         expression; or null to indicate that the result is an empty
     *         sequence
     * @throws net.sf.saxon.trans.XPathException
     *          if any dynamic error occurs evaluating the
     *          expression
     */

    public BooleanValue evaluateItem(XPathContext context) throws XPathException {
        return BooleanValue.get(effectiveBooleanValue(context));
    }

    /**
     * Get the effective boolean value of the expression. This returns false if the value
     * is the empty sequence, a zero-length string, a number equal to zero, or the boolean
     * false. Otherwise it returns true.
     *
     * @param context The context in which the expression is to be evaluated
     * @return the effective boolean value
     * @throws net.sf.saxon.trans.XPathException
     *          if any dynamic error occurs evaluating the
     *          expression
     */

    public boolean effectiveBooleanValue(XPathContext context) throws XPathException {
        NumericValue n = (NumericValue) getLhsExpression().evaluateItem(context);
        if (n.isNaN()) {
            return operator == Token.FNE;
        }
        int c = n.compareTo(comparand);
        switch (operator) {
            case Token.FEQ:
                return c == 0;
            case Token.FNE:
                return c != 0;
            case Token.FGT:
                return c > 0;
            case Token.FLT:
                return c < 0;
            case Token.FGE:
                return c >= 0;
            case Token.FLE:
                return c <= 0;
            default:
                throw new UnsupportedOperationException("Unknown operator " + operator);
        }
    }

    public int computeCardinality() {
        return StaticProperty.EXACTLY_ONE;
    }

    /**
     * Perform optimisation of an expression and its subexpressions. This is the third and final
     * phase of static optimization.
     * <p/>
     * <p>This method is called after all references to functions and variables have been resolved
     * to the declaration of the function or variable, and after all type checking has been done.</p>
     *
     * @param visitor         the expression visitor
     * @param contextInfo the static type of "." at the point where this expression is invoked.
     *                        The parameter is set to null if it is known statically that the context item will be undefined.
     *                        If the type of the context item is not known statically, the argument is set to
     *                        {@link net.sf.saxon.type.Type#ITEM_TYPE}
     * @return the original expression, rewritten if appropriate to optimize execution
     * @throws XPathException if an error is discovered during this phase
     *                        (typically a type error)
     */

    /*@NotNull*/
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        optimizeChildren(visitor, contextInfo);
        if (getLhsExpression() instanceof Literal) {
            Literal lit = Literal.makeLiteral(BooleanValue.get(effectiveBooleanValue(null)));
            ExpressionTool.copyLocationInfo(this, lit);
            return lit;
        }
        return this;
    }

    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     *
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     * The name will always be in the form of a lexical XML QName, and should match the name used
     * in export() output displaying the expression.
     */
    @Override
    public String getExpressionName() {
        return "compareToInt";
    }


    /**
     * Determine the data type of the expression, if possible. All expression return
     * sequences, in general; this method determines the type of the items within the
     * sequence, assuming that (a) this is known in advance, and (b) it is the same for
     * all items in the sequence.
     * <p/>
     * <p>This method should always return a result, though it may be the best approximation
     * that is available at the time.</p>
     *
     * @return a value such as Type.STRING, Type.BOOLEAN, Type.NUMBER,
     *         Type.NODE, or Type.ITEM (meaning not known at compile time)
     */

    /*@NotNull*/
    public ItemType getItemType() {
        return BuiltInAtomicType.BOOLEAN;
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    public void export(ExpressionPresenter destination) throws XPathException {
        destination.startElement("compareToInt", this);
        destination.emitAttribute("op", Token.tokens[operator]);
        destination.emitAttribute("val", comparand + "");
        getLhsExpression().export(destination);
        destination.endElement();
    }

    /**
     * <p>The toString() method for an expression attempts to give a representation of the expression
     * in an XPath-like form.</p>
     * <p/>
     * <p>For subclasses of Expression that represent XPath expressions, the result should always be a string that
     * parses as an XPath 3.0 expression</p>
     *
     * @return a representation of the expression as a string
     */
    @Override
    public String toString() {
        return ExpressionTool.parenthesize(getLhsExpression()) + " " +
                Token.tokens[operator] + " " + comparand;
    }

    /**
     * Produce a short string identifying the expression for use in error messages
     *
     * @return a short string, sufficient to identify the expression
     */
    @Override
    public String toShortString() {
        return getLhsExpression().toShortString() + " " + Token.tokens[operator] + " " + comparand;
    }

    /**
     * Get the AtomicComparer used to compare atomic values. This encapsulates any collation that is used
     */

    public AtomicComparer getAtomicComparer() {
        return DoubleSortComparer.getInstance();
        // Note: this treats NaN=NaN as true, but it doesn't matter, because the rhs will never be NaN.
    }

    /**
     * Get the primitive (singleton) operator used: one of Token.FEQ, Token.FNE, Token.FLT, Token.FGT,
     * Token.FLE, Token.FGE
     */

    public int getSingletonOperator() {
        return operator;
    }

    /**
     * Determine whether untyped atomic values should be converted to the type of the other operand
     *
     * @return true if untyped values should be converted to the type of the other operand, false if they
     *         should be converted to strings.
     */

    public boolean convertsUntypedToOther() {
        return true;
    }
}

