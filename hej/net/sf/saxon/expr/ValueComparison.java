////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.expr.sort.*;
import net.sf.saxon.functions.*;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.NoDynamicContextException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.*;

/**
 * ValueComparison: a boolean expression that compares two atomic values
 * for equals, not-equals, greater-than or less-than. Implements the operators
 * eq, ne, lt, le, gt, ge
 */

public final class ValueComparison extends BinaryExpression implements ComparisonExpression, Negatable {

    private AtomicComparer comparer;
    /*@Nullable*/ private BooleanValue resultWhenEmpty = null;
    private boolean needsRuntimeCheck;

    /**
     * Create a comparison expression identifying the two operands and the operator
     *
     * @param p1 the left-hand operand
     * @param op the operator, as a token returned by the Tokenizer (e.g. Token.LT)
     * @param p2 the right-hand operand
     */

    public ValueComparison(Expression p1, int op, Expression p2) {
        super(p1, op, p2);
    }

    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     *
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     *         The name will always be in the form of a lexical XML QName, and should match the name used
     *         in explain() output displaying the expression.
     */

    public String getExpressionName() {
        return "ValueComparison";
    }

    /**
     * Set the AtomicComparer used to compare atomic values
     *
     * @param comparer the AtomicComparer
     */

    public void setAtomicComparer(AtomicComparer comparer) {
        this.comparer = comparer;
    }

    /**
     * Get the AtomicComparer used to compare atomic values. This encapsulates any collation that is used.
     * Note that the comparer is always known at compile time.
     */

    public AtomicComparer getAtomicComparer() {
        return comparer;
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
        return false;
    }

    /**
     * Set the result to be returned if one of the operands is an empty sequence
     *
     * @param value the result to be returned if an operand is empty. Supply null to mean the empty sequence.
     */

    public void setResultWhenEmpty(BooleanValue value) {
        resultWhenEmpty = value;
    }

    /**
     * Get the result to be returned if one of the operands is an empty sequence
     *
     * @return BooleanValue.TRUE, BooleanValue.FALSE, or null (meaning the empty sequence)
     */

    public BooleanValue getResultWhenEmpty() {
        return resultWhenEmpty;
    }

    /**
     * Determine whether a run-time check is needed to check that the types of the arguments
     * are comparable
     *
     * @return true if a run-time check is needed
     */

    public boolean needsRuntimeComparabilityCheck() {
        return needsRuntimeCheck;
    }

    /**
     * Type-check the expression
     */

    /*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {

        resetLocalStaticProperties();
        typeCheckChildren(visitor, contextInfo);

        Configuration config = visitor.getConfiguration();
        StaticContext env = visitor.getStaticContext();

        if (Literal.isEmptySequence(getLhsExpression())) {
            return resultWhenEmpty == null ? getLhsExpression() : Literal.makeLiteral(resultWhenEmpty);
        }

        if (Literal.isEmptySequence(getRhsExpression())) {
            return resultWhenEmpty == null ? getRhsExpression() : Literal.makeLiteral(resultWhenEmpty);
        }

        if (comparer instanceof UntypedNumericComparer) {
            return this; // we've already done all that needs to be done
        }

        final SequenceType optionalAtomic = SequenceType.OPTIONAL_ATOMIC;
        TypeChecker tc = config.getTypeChecker(false);

        RoleDiagnostic role0 = new RoleDiagnostic(RoleDiagnostic.BINARY_EXPR, Token.tokens[operator], 0);
        setLhsExpression(tc.staticTypeCheck(getLhsExpression(), optionalAtomic, role0, visitor));

        RoleDiagnostic role1 = new RoleDiagnostic(RoleDiagnostic.BINARY_EXPR, Token.tokens[operator], 1);
        setRhsExpression(tc.staticTypeCheck(getRhsExpression(), optionalAtomic, role1, visitor));

        PlainType t0 = getLhsExpression().getItemType().getAtomizedItemType();
        PlainType t1 = getRhsExpression().getItemType().getAtomizedItemType();

        if (t0.isExternalType() || t1.isExternalType()) {
            XPathException err = new XPathException("Cannot perform comparisons involving external objects");
            err.setIsTypeError(true);
            err.setErrorCode("XPTY0004");
            err.setLocation(getLocation());
            throw err;
        }

        BuiltInAtomicType p0 = (BuiltInAtomicType) t0.getPrimitiveItemType();
        if (p0.equals(BuiltInAtomicType.UNTYPED_ATOMIC)) {
            p0 = BuiltInAtomicType.STRING;
        }
        BuiltInAtomicType p1 = (BuiltInAtomicType) t1.getPrimitiveItemType();
        if (p1.equals(BuiltInAtomicType.UNTYPED_ATOMIC)) {
            p1 = BuiltInAtomicType.STRING;
        }

        needsRuntimeCheck =
                p0.equals(BuiltInAtomicType.ANY_ATOMIC) || p1.equals(BuiltInAtomicType.ANY_ATOMIC);

        if (!needsRuntimeCheck && !Type.isPossiblyComparable(p0, p1, Token.isOrderedOperator(operator))) {
            boolean opt0 = Cardinality.allowsZero(getLhsExpression().getCardinality());
            boolean opt1 = Cardinality.allowsZero(getRhsExpression().getCardinality());
            if (opt0 || opt1) {
                // This is a comparison such as (xs:integer? eq xs:date?). This is almost
                // certainly an error, but we need to let it through because it will work if
                // one of the operands is an empty sequence.

                String which = null;
                if (opt0) {
                    which = "the first operand is";
                }
                if (opt1) {
                    which = "the second operand is";
                }
                if (opt0 && opt1) {
                    which = "one or both operands are";
                }

                visitor.getStaticContext().issueWarning("Comparison of " + t0.toString() +
                        (opt0 ? "?" : "") + " to " + t1.toString() +
                        (opt1 ? "?" : "") + " will fail unless " + which + " empty", getLocation());
                needsRuntimeCheck = true;
            } else {
                String message = "In {" + toShortString() + "}: cannot compare " +
                        t0.toString() + " to " + t1.toString();
                XPathException err = new XPathException(message);
                err.setIsTypeError(true);
                err.setErrorCode("XPTY0004");
                err.setLocation(getLocation());
                throw err;
            }
        }
        if (!(operator == Token.FEQ || operator == Token.FNE)) {
            if (!p0.isOrdered(true)) {
                XPathException err = new XPathException("Type " + t0.toString() + " is not an ordered type");
                err.setErrorCode("XPTY0004");
                err.setIsTypeError(true);
                err.setLocation(getLocation());
                throw err;
            }
            if (!p1.isOrdered(true)) {
                XPathException err = new XPathException("Type " + t1.toString() + " is not an ordered type");
                err.setErrorCode("XPTY0004");
                err.setIsTypeError(true);
                err.setLocation(getLocation());
                throw err;
            }
        }

        if (comparer == null) {
            // In XSLT, only do this the first time through, otherwise the default-collation attribute may be missed
            final String defaultCollationName = env.getDefaultCollationName();
            StringCollator comp = config.getCollation(defaultCollationName);
            if (comp == null) {
                comp = CodepointCollator.getInstance();
            }
            comparer = GenericAtomicComparer.makeAtomicComparer(
                    p0, p1, comp, env.getConfiguration().getConversionContext());
        }
        return this;
    }

    /**
     * Perform optimisation of an expression and its subexpressions.
     * <p/>
     * <p>This method is called after all references to functions and variables have been resolved
     * to the declaration of the function or variable, and after all type checking has been done.</p>
     *
     * @param visitor         an expression visitor
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

        TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();

        optimizeChildren(visitor, contextInfo);

        Sequence value0 = null;
        Sequence value1 = null;

        if (getLhsExpression() instanceof Literal) {
            value0 = ((Literal) getLhsExpression()).getValue();
        }

        if (getRhsExpression() instanceof Literal) {
            value1 = ((Literal) getRhsExpression()).getValue();
        }

        // evaluate the expression now if both arguments are constant

        if ((value0 != null) && (value1 != null)) {
            try {
                AtomicValue r = evaluateItem(visitor.getStaticContext().makeEarlyEvaluationContext());
                return Literal.makeLiteral(r == null ? EmptySequence.getInstance() : r);
            } catch (NoDynamicContextException e) {
                // early evaluation failed, typically because the implicit context isn't available.
                // Try again at run-time
                return this;
            }
        }

        // optimise count(x) eq N (or gt N, ne N, eq N, etc)
        // TODO: extend this to cases where N is not a literal

        if (getLhsExpression().isCallOn(Count.class) && Literal.isAtomic(getRhsExpression())) {
            Expression e2 = optimizeCount(false);
            if (e2 != null) {
                return e2.optimize(visitor, contextInfo);
            }
        } else if (getRhsExpression().isCallOn(Count.class) && Literal.isAtomic(getLhsExpression())) {
            Expression e2 = optimizeCount(true);
            if (e2 != null) {
                return e2.optimize(visitor, contextInfo);
            }
        }


        // optimise string-length(x) = 0, >0, !=0 etc

        if (getLhsExpression().isCallOn(StringLength_1.class) &&
                isZero(value1)) {
            Expression arg = ((SystemFunctionCall) getLhsExpression()).getArg(0);
            switch (operator) {
                case Token.FEQ:
                case Token.FLE:
                    return SystemFunction.makeCall("not", getRetainedStaticContext(), arg);
                case Token.FNE:
                case Token.FGT:
                    return SystemFunction.makeCall("boolean", getRetainedStaticContext(), arg);
                case Token.FGE:
                    return Literal.makeLiteral(BooleanValue.TRUE);
                case Token.FLT:
                    return Literal.makeLiteral(BooleanValue.FALSE);
            }
        }

        // optimise (0 = string-length(x)), etc

        if (getRhsExpression().isCallOn(StringLength_1.class) &&
                isZero(value0)) {
            Expression arg = ((SystemFunctionCall) getRhsExpression()).getArg(0);
            switch (operator) {
                case Token.FEQ:
                case Token.FGE:
                    return SystemFunction.makeCall("not", getRetainedStaticContext(), arg);
                case Token.FNE:
                case Token.FLT:
                    return SystemFunction.makeCall("boolean", getRetainedStaticContext(), arg);
                case Token.FLE:
                    return Literal.makeLiteral(BooleanValue.TRUE);
                case Token.FGT:
                    return Literal.makeLiteral(BooleanValue.FALSE);
            }
        }

        // optimise string="" etc
        // Note we can change S!="" to boolean(S) for cardinality zero-or-one, but we can only
        // change S="" to not(S) for cardinality exactly-one.

        int p0 = getLhsExpression().getItemType().getPrimitiveType();
        if ((p0 == StandardNames.XS_STRING ||
                p0 == StandardNames.XS_ANY_URI ||
                p0 == StandardNames.XS_UNTYPED_ATOMIC) &&
                getRhsExpression() instanceof Literal &&
                ((Literal) getRhsExpression()).getValue() instanceof StringValue &&
                ((StringValue) ((Literal) getRhsExpression()).getValue()).isZeroLength() &&
                comparer instanceof CodepointCollatingComparer) {

            switch (operator) {
                case Token.FNE:
                case Token.FGT:
                    return SystemFunction.makeCall("boolean", getRetainedStaticContext(), getLhsExpression());
                case Token.FEQ:
                case Token.FLE:
                    if (getLhsExpression().getCardinality() == StaticProperty.EXACTLY_ONE) {
                        return SystemFunction.makeCall("not", getRetainedStaticContext(), getLhsExpression());
                    }
            }
        }

        // optimize "" = string etc

        int p1 = getRhsExpression().getItemType().getPrimitiveType();
        if ((p1 == StandardNames.XS_STRING ||
                p1 == StandardNames.XS_ANY_URI ||
                p1 == StandardNames.XS_UNTYPED_ATOMIC) &&
                getLhsExpression() instanceof Literal &&
                ((Literal) getLhsExpression()).getValue() instanceof StringValue &&
                ((StringValue) ((Literal) getLhsExpression()).getValue()).isZeroLength() &&
                comparer instanceof CodepointCollatingComparer) {

            switch (operator) {
                case Token.FNE:
                case Token.FLT:
                    return SystemFunction.makeCall("boolean", getRetainedStaticContext(), getRhsExpression());
                case Token.FEQ:
                case Token.FGE:
                    if (getRhsExpression().getCardinality() == StaticProperty.EXACTLY_ONE) {
                        return SystemFunction.makeCall("not", getRetainedStaticContext(), getRhsExpression());
                    }
            }
        }


        // optimise [position()=last()] etc

        if (getLhsExpression().isCallOn(PositionAndLast.Position.class) &&
                getRhsExpression().isCallOn(PositionAndLast.Last.class)) {
            switch (operator) {
                case Token.FEQ:
                case Token.FGE:
                    IsLastExpression iletrue = new IsLastExpression(true);
                    ExpressionTool.copyLocationInfo(this, iletrue);
                    return iletrue;
                case Token.FNE:
                case Token.FLT:
                    IsLastExpression ilefalse = new IsLastExpression(false);
                    ExpressionTool.copyLocationInfo(this, ilefalse);
                    return ilefalse;
                case Token.FGT:
                    return Literal.makeLiteral(BooleanValue.FALSE);
                case Token.FLE:
                    return Literal.makeLiteral(BooleanValue.TRUE);
            }
        }
        if (getLhsExpression().isCallOn(PositionAndLast.Last.class) &&
                getRhsExpression().isCallOn(PositionAndLast.Position.class)) {
            switch (operator) {
                case Token.FEQ:
                case Token.FLE:
                    IsLastExpression iletrue = new IsLastExpression(true);
                    ExpressionTool.copyLocationInfo(this, iletrue);
                    return iletrue;
                case Token.FNE:
                case Token.FGT:
                    IsLastExpression ilefalse = new IsLastExpression(false);
                    ExpressionTool.copyLocationInfo(this, ilefalse);
                    return ilefalse;
                case Token.FLT:
                    return Literal.makeLiteral(BooleanValue.FALSE);
                case Token.FGE:
                    return Literal.makeLiteral(BooleanValue.TRUE);
            }
        }

        // optimize comparison against an integer constant

        if (value1 instanceof Int64Value &&
                getLhsExpression().getCardinality() == StaticProperty.EXACTLY_ONE &&
                th.isSubType(getLhsExpression().getItemType(), NumericType.getInstance())) {
            return new CompareToIntegerConstant(getLhsExpression(), operator, ((Int64Value) value1).longValue());
        }

        if (value0 instanceof Int64Value &&
                getRhsExpression().getCardinality() == StaticProperty.EXACTLY_ONE &&
                th.isSubType(getRhsExpression().getItemType(), NumericType.getInstance())) {
            return new CompareToIntegerConstant(getRhsExpression(), Token.inverse(operator), ((Int64Value) value0).longValue());
        }

        // optimize (boolean expression) == (boolean literal)

        if (p0 == StandardNames.XS_BOOLEAN &&
                p1 == StandardNames.XS_BOOLEAN &&
                (operator == Token.FEQ || operator == Token.FNE) &&
                getLhsExpression().getCardinality() == StaticProperty.EXACTLY_ONE &&
                getRhsExpression().getCardinality() == StaticProperty.EXACTLY_ONE &&
                (getLhsExpression() instanceof Literal || getRhsExpression() instanceof Literal)) {
            Literal literal = (Literal) (getLhsExpression() instanceof Literal ? getLhsExpression() : getRhsExpression());
            Expression other = getLhsExpression() instanceof Literal ? getRhsExpression() : getLhsExpression();
            boolean negate = (operator == Token.FEQ) != ((BooleanValue) literal.getValue()).getBooleanValue();
            if (negate) {
                Expression fn = SystemFunction.makeCall("not", getRetainedStaticContext(), other);
                ExpressionTool.copyLocationInfo(this, fn);
                return fn.optimize(visitor, contextInfo);
            } else {
                return other;
            }
        }


        // optimize generate-id(X) = generate-id(Y) as "X is Y"
        // This construct is often used in XSLT 1.0 stylesheets.
        // Only do this if we know the arguments are singletons, because "is" doesn't
        // do first-value extraction.

        if (getLhsExpression().isCallOn(GenerateId_1.class) && getRhsExpression().isCallOn(GenerateId_1.class)) {
            SystemFunctionCall f0 = (SystemFunctionCall) getLhsExpression();
            SystemFunctionCall f1 = (SystemFunctionCall) getRhsExpression();
            if (!Cardinality.allowsMany(f0.getArg(0).getCardinality()) &&
                    !Cardinality.allowsMany(f1.getArg(0).getCardinality()) &&
                    (operator == Token.FEQ)) {
                IdentityComparison id =
                        new IdentityComparison(f0.getArg(0),
                                Token.IS,
                                f1.getArg(0));
                id.setGenerateIdEmulation(true);
                ExpressionTool.copyLocationInfo(this, id);
                return id.typeCheck(visitor, contextInfo).optimize(visitor, contextInfo);
            }
        }

        return this;
    }

    /**
     * Optimize comparisons of count(X) to a literal value. The objective here is to count items in the sequence only
     * until the result of the comparison is deducible; for example to evaluate (count(X)>2) we can stop at the third
     * item in the sequence.
     *
     * @param inverted true if the call to count(X) is the right-hand operand
     * @return the optimized expression, or null if no optimization is possible
     * @throws XPathException if an error occurs
     */

    /*@Nullable*/
    private Expression optimizeCount(boolean inverted) throws XPathException {
        SystemFunctionCall countFn = (SystemFunctionCall) (inverted ? getRhsExpression() : getLhsExpression());
        Expression sequence = countFn.getArg(0);
        sequence = sequence.unordered(false, false);
        Optimizer opt = getConfiguration().obtainOptimizer();

        AtomicValue literalOperand = (AtomicValue) ((Literal) (inverted ? getLhsExpression() : getRhsExpression())).getValue();
        int op = inverted ? Token.inverse(operator) : operator;

        if (isZero(literalOperand)) {
            if (op == Token.FEQ || op == Token.FLE) {
                // rewrite count(x)=0 as empty(x)
                Expression result = SystemFunction.makeCall("empty", getRetainedStaticContext(), sequence);
                opt.trace("Rewrite count()=0 as:", result);
                return result;
            } else if (op == Token.FNE || op == Token.FGT) {
                // rewrite count(x)!=0, count(x)>0 as exists(x)
                Expression result = SystemFunction.makeCall("exists", getRetainedStaticContext(), sequence);
                opt.trace("Rewrite count()>0 as:", result);
                return result;
            } else if (op == Token.FGE) {
                // rewrite count(x)>=0 as true()
                return Literal.makeLiteral(BooleanValue.TRUE);
            } else {  // singletonOperator == Token.FLT
                // rewrite count(x)<0 as false()
                return Literal.makeLiteral(BooleanValue.FALSE);
            }
        } else if (literalOperand instanceof NumericValue) {
            long operand;
            if (literalOperand instanceof IntegerValue) {
                operand = ((IntegerValue) literalOperand).longValue();
            } else if (literalOperand.isNaN()) {
                return Literal.makeLiteral(BooleanValue.get(op == Token.FNE));
            } else if (((NumericValue) literalOperand).isWholeNumber()) {
                operand = ((NumericValue) literalOperand).longValue();
            } else if (op == Token.FEQ) {
                return Literal.makeLiteral(BooleanValue.FALSE);
            } else if (op == Token.FNE) {
                return Literal.makeLiteral(BooleanValue.TRUE);
            } else if (op == Token.FGT || op == Token.FGE) {
                operand = ((NumericValue) literalOperand).ceiling().longValue();
                op = Token.FGE;
            } else /*if (op == Token.FLT || op == Token.FLE)*/ {
                operand = ((NumericValue) literalOperand).floor().longValue();
                op = Token.FLE;
            }
            if (operand < 0) {
                switch (op) {
                    case Token.FEQ:
                    case Token.FLT:
                    case Token.FLE:
                        return Literal.makeLiteral(BooleanValue.FALSE);
                    default:
                        return Literal.makeLiteral(BooleanValue.TRUE);
                }
            }
            if (operand > Integer.MAX_VALUE) {
                switch (op) {
                    case Token.FEQ:
                    case Token.FGT:
                    case Token.FGE:
                        return Literal.makeLiteral(BooleanValue.FALSE);
                    default:
                        return Literal.makeLiteral(BooleanValue.TRUE);
                }
            }
            if (sequence instanceof TailExpression || sequence.isCallOn(Subsequence_2.class)) {
                // it's probably the result of a previous optimization
                return null;
            }
            switch (op) {
                case Token.FEQ:
                case Token.FNE:
                case Token.FLE:
                case Token.FLT:
                    // rewrite count(E) op N as count(subsequence(E, 1, N+1)) op N
                    Expression ss = SystemFunction.makeCall("subsequence",
                        getRetainedStaticContext(), sequence,
                        Literal.makeLiteral(IntegerValue.PLUS_ONE),
                        Literal.makeLiteral(Int64Value.makeIntegerValue(operand + 1)));
                    Expression ct = SystemFunction.makeCall("count", getRetainedStaticContext(), ss);
                    CompareToIntegerConstant ctic = new CompareToIntegerConstant(ct, op, operand);
                    opt.trace("Rewrite count()~N as:", ctic);
                    ExpressionTool.copyLocationInfo(this, ctic);
                    return ctic;
                case Token.FGE:
                case Token.FGT:
                    // rewrite count(x) gt n as exists(x[n+1])
                    //     and count(x) ge n as exists(x[n])
                    TailExpression tail = new TailExpression(sequence, (int) (op == Token.FGE ? operand : operand + 1));
                    ExpressionTool.copyLocationInfo(this, tail);
                    Expression result = SystemFunction.makeCall("exists", getRetainedStaticContext(), tail);
                    ExpressionTool.copyLocationInfo(this, result);
                    opt.trace("Rewrite count()>=N as:", result);
                    return result;
            }
        }
        return null;
    }


    /**
     * Check whether this specific instance of the expression is negatable
     *
     * @return true if it is
     * @param th the type hierarchy
     */

    public boolean isNegatable(TypeHierarchy th) {
        // Expression is not negatable if it might involve NaN
        return !maybeNaN(getLhsExpression(), th) && !maybeNaN(getRhsExpression(), th);
    }

    private boolean maybeNaN(Expression exp, TypeHierarchy th) {
        return th.relationship(exp.getItemType(), BuiltInAtomicType.DOUBLE) != TypeHierarchy.DISJOINT ||
                th.relationship(exp.getItemType(), BuiltInAtomicType.FLOAT) != TypeHierarchy.DISJOINT;
    }

    /**
     * Return the negation of this value comparison: that is, a value comparison that returns true()
     * if and only if the original returns false(). The result must be the same as not(this) even in the
     * case where one of the operands is ().
     *
     * @return the inverted comparison
     */

    public Expression negate() {
        ValueComparison vc = new ValueComparison(getLhsExpression(), Token.negate(operator), getRhsExpression());
        vc.comparer = comparer;
        if (resultWhenEmpty == null || resultWhenEmpty == BooleanValue.FALSE) {
            vc.resultWhenEmpty = BooleanValue.TRUE;
        } else {
            vc.resultWhenEmpty = BooleanValue.FALSE;
        }
        ExpressionTool.copyLocationInfo(this, vc);
        return vc;
    }


    /**
     * Test whether an expression is constant zero
     *
     * @param v the value to be tested
     * @return true if the operand is the constant zero (of any numeric data type)
     */

    private static boolean isZero(Sequence v) {
        return v instanceof NumericValue && ((NumericValue) v).compareTo(0) == 0;
    }


    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings
     */

    /*@NotNull*/
    public Expression copy(RebindingMap rebindings) {
        ValueComparison vc = new ValueComparison(getLhsExpression().copy(rebindings), operator, getRhsExpression().copy(rebindings));
        ExpressionTool.copyLocationInfo(this, vc);
        vc.comparer = comparer;
        vc.resultWhenEmpty = resultWhenEmpty;
        vc.needsRuntimeCheck = needsRuntimeCheck;
        return vc;
    }

    /**
     * Evaluate the effective boolean value of the expression
     *
     * @param context the given context for evaluation
     * @return a boolean representing the result of the comparison of the two operands
     */

    public boolean effectiveBooleanValue(XPathContext context) throws XPathException {
        try {
            AtomicValue v0 = (AtomicValue) getLhsExpression().evaluateItem(context);
            if (v0 == null) {
                return resultWhenEmpty == BooleanValue.TRUE;  // normally false
            }
            AtomicValue v1 = (AtomicValue) getRhsExpression().evaluateItem(context);
            if (v1 == null) {
                return resultWhenEmpty == BooleanValue.TRUE;  // normally false
            }
            return compare(v0, operator, v1, comparer.provideContext(context), needsRuntimeCheck);
        } catch (XPathException e) {
            // re-throw the exception with location information added
            e.maybeSetLocation(getLocation());
            e.maybeSetContext(context);
            throw e;
        }
    }

    /**
     * Compare two atomic values, using a specified operator and collation
     *
     * @param v0         the first operand
     * @param op         the operator, as defined by constants such as {@link net.sf.saxon.expr.parser.Token#FEQ} or
     *                   {@link net.sf.saxon.expr.parser.Token#FLT}
     * @param v1         the second operand
     * @param comparer   the Collator to be used when comparing strings
     * @param checkTypes set to true if it is necessary to check that the types of the arguments are comparable
     * @return the result of the comparison: -1 for LT, 0 for EQ, +1 for GT
     * @throws XPathException if the values are not comparable
     */

    public static boolean compare(AtomicValue v0, int op, AtomicValue v1, AtomicComparer comparer, boolean checkTypes)
            throws XPathException {
        if (checkTypes &&
                !Type.isGuaranteedComparable(v0.getPrimitiveType(), v1.getPrimitiveType(), Token.isOrderedOperator(op))) {
            XPathException e2 = new XPathException("Cannot compare " + Type.displayTypeName(v0) +
                    " to " + Type.displayTypeName(v1));
            e2.setErrorCode("XPTY0004");
            e2.setIsTypeError(true);
            throw e2;
        }
        if (v0.isNaN() || v1.isNaN()) {
            return op == Token.FNE;
        }
        try {
            switch (op) {
                case Token.FEQ:
                    return comparer.comparesEqual(v0, v1);
                case Token.FNE:
                    return !comparer.comparesEqual(v0, v1);
                case Token.FGT:
                    return comparer.compareAtomicValues(v0, v1) > 0;
                case Token.FLT:
                    return comparer.compareAtomicValues(v0, v1) < 0;
                case Token.FGE:
                    return comparer.compareAtomicValues(v0, v1) >= 0;
                case Token.FLE:
                    return comparer.compareAtomicValues(v0, v1) <= 0;
                default:
                    throw new UnsupportedOperationException("Unknown operator " + op);
            }
        } catch (ClassCastException err) {
            XPathException e2 = new XPathException("Cannot compare " + Type.displayTypeName(v0) +
                    " to " + Type.displayTypeName(v1));
            e2.setErrorCode("XPTY0004");
            e2.setIsTypeError(true);
            throw e2;
        }
    }

    /**
     * Evaluate the expression in a given context
     *
     * @param context the given context for evaluation
     * @return a BooleanValue representing the result of the numeric comparison of the two operands,
     *         or null representing the empty sequence
     */

    public BooleanValue evaluateItem(XPathContext context) throws XPathException {
        try {
            AtomicValue v0 = (AtomicValue) getLhsExpression().evaluateItem(context);
            if (v0 == null) {
                return resultWhenEmpty;
            }
            AtomicValue v1 = (AtomicValue) getRhsExpression().evaluateItem(context);
            if (v1 == null) {
                return resultWhenEmpty;
            }
            return BooleanValue.get(compare(v0, operator, v1, comparer.provideContext(context), needsRuntimeCheck));
        } catch (XPathException e) {
            // re-throw the exception with location information added
            e.maybeSetLocation(getLocation());
            e.maybeSetContext(context);
            throw e;
        }
    }

    /**
     * Call the Callable.
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as Sequences.
     *                  <p>Generally it is advisable, if calling iterate() to process a supplied sequence, to
     *                  call it only once; if the value is required more than once, it should first be converted
     *                  to a {@link net.sf.saxon.om.GroundedValue} by calling the utility methd
     *                  SequenceTool.toGroundedValue().</p>
     *                  <p>If the expected value is a single item, the item should be obtained by calling
     *                  Sequence.head(): it cannot be assumed that the item will be passed as an instance of
     *                  {@link net.sf.saxon.om.Item} or {@link net.sf.saxon.value.AtomicValue}.</p>
     *                  <p>It is the caller's responsibility to perform any type conversions required
     *                  to convert arguments to the type expected by the callee. An exception is where
     *                  this Callable is explicitly an argument-converting wrapper around the original
     *                  Callable.</p>
     * @return the result of the evaluation, in the form of a Sequence. It is the responsibility
     * of the callee to ensure that the type of result conforms to the expected result type.
     * @throws net.sf.saxon.trans.XPathException if a dynamic error occurs during the evaluation of the expression
     */
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        return null;
    }

    /**
     * Determine the data type of the expression
     *
     * @return Type.BOOLEAN
     */

    /*@NotNull*/
    public ItemType getItemType() {
        return BuiltInAtomicType.BOOLEAN;
    }

    /**
     * Get the static type of the expression as a UType, following precisely the type
     * inference rules defined in the XSLT 3.0 specification.
     *
     * @return the static item type of the expression according to the XSLT 3.0 defined rules
     * @param contextItemType
     */
    @Override
    public UType getStaticUType(UType contextItemType) {
        return UType.BOOLEAN;
    }

    /**
     * Determine the static cardinality.
     */

    public int computeCardinality() {
        if (resultWhenEmpty != null) {
            return StaticProperty.EXACTLY_ONE;
        } else {
            return super.computeCardinality();
        }
    }

    @Override
    public String tag() {
        return "vc";
    }

    @Override
    protected void explainExtraAttributes(ExpressionPresenter out) {
        if (resultWhenEmpty != null) {
            out.emitAttribute("onEmpty", resultWhenEmpty.getBooleanValue() ? "1" : "0");
        }
        out.emitAttribute("comp", comparer.save());
    }



}

