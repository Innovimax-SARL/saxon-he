////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.parser.*;
import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.om.*;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.*;

/**
 * Expression class for a cast to a union type
 */
public class CastToUnion extends UnaryExpression {

    private UnionType targetType;
    private boolean allowEmpty;

    /**
     * Create a cast expression to a union type
     * @param source the operand of the cast
     * @param targetType the union type that is the result of the cast
     * @param allowEmpty true if an empty sequence may be supplied as input, converting to an empty sequence on output
     */

    public CastToUnion(Expression source, UnionType targetType, boolean allowEmpty) {
        super(source);
        this.targetType = targetType;
        this.allowEmpty = allowEmpty;
    }

    /**
     * Get the usage (in terms of streamability analysis) of the single operand
     * @return the operand usage
     */

    protected OperandRole getOperandRole() {
        return OperandRole.SINGLE_ATOMIC;
    }

    /**
     * Ask whether the value of the operand is allowed to be empty
     * @return true if an empty sequence may be supplied as input, converting to an empty sequence on output
     */

    public boolean isAllowEmpty() {
        return allowEmpty;
    }

    /**
     * Get the union type that is the target of this cast operation
     * @return the target type of the cast
     */

    public UnionType getTargetType() {
        return targetType;
    }

    /**
     * Get the namespace resolver that will be used to resolve any namespace-sensitive values (such as QNames) when casting
     * @return the namespace resolver, or null if there is none.
     */

    public NamespaceResolver getNamespaceResolver() {
        return getRetainedStaticContext();
    }

    /**
     * Type-check the expression
     */

    /*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        typeCheckChildren(visitor, contextInfo);
        SequenceType atomicType = SequenceType.makeSequenceType(BuiltInAtomicType.ANY_ATOMIC, getCardinality());

        RoleDiagnostic role = new RoleDiagnostic(RoleDiagnostic.TYPE_OP, "cast as", 0);
        Expression operand = visitor.getConfiguration().getTypeChecker(false).staticTypeCheck(
                getBaseExpression(), atomicType, role, visitor);
        setBaseExpression(operand);

        if (operand instanceof Literal) {
            GroundedValue literalOperand = ((Literal) operand).getValue();
            if (literalOperand instanceof AtomicValue) {
                GroundedValue av = SequenceTool.toGroundedValue(iterate(visitor.getStaticContext().makeEarlyEvaluationContext()));
                return Literal.makeLiteral(av);
            }
            if (literalOperand.getLength() == 0) {
                if (allowEmpty) {
                    return operand;
                } else {
                    XPathException err = new XPathException("Cast can never succeed: the operand must not be an empty sequence");
                    err.setErrorCode("XPTY0004");
                    err.setLocation(getLocation());
                    err.setIsTypeError(true);
                    throw err;
                }
            }
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
     * @throws net.sf.saxon.trans.XPathException
     *          if an error is discovered during this phase
     *          (typically a type error)
     */

    /*@NotNull*/
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        Expression e2 = super.optimize(visitor, contextInfo);
        if (e2 != this) {
            return e2;
        }
        // if the operand can't be empty, then set allowEmpty to false to provide more information for analysis
        if (!Cardinality.allowsZero(getBaseExpression().getCardinality())) {
            allowEmpty = false;
            resetLocalStaticProperties();
        }
        return this;
    }

    /**
     * Get the static cardinality of the expression
     */

    public int computeCardinality() {
        int c = StaticProperty.ALLOWS_ONE;
        if (allowEmpty && Cardinality.allowsZero(getBaseExpression().getCardinality())) {
            c |= StaticProperty.ALLOWS_ZERO;
        }
        try {
            if (targetType.containsListType()) {
                c |= StaticProperty.ALLOWS_ZERO;
                c |= StaticProperty.ALLOWS_MANY;
            }
        } catch (MissingComponentException e) {
            c |= StaticProperty.ALLOWS_ZERO;
            c |= StaticProperty.ALLOWS_MANY;
        }
        return c;
    }

    /**
     * Get the static type of the expression
     */

    /*@NotNull*/
    public ItemType getItemType() {
        if (targetType instanceof PlainType) {
            return targetType;
        }
        return BuiltInAtomicType.ANY_ATOMIC;
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
        return targetType.getUType();
    }


    /**
     * Determine the special properties of this expression
     *
     * @return {@link StaticProperty#NON_CREATIVE}.
     */

    public int computeSpecialProperties() {
        int p = super.computeSpecialProperties();
        return p | StaticProperty.NON_CREATIVE;
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings
     */

    /*@NotNull*/
    public Expression copy(RebindingMap rebindings) {
        CastToUnion c = new CastToUnion(getBaseExpression().copy(rebindings), targetType, allowEmpty);
        ExpressionTool.copyLocationInfo(this, c);
        c.setRetainedStaticContext(getRetainedStaticContext());
        return c;
    }

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is provided directly. The other methods will always be available
     * indirectly, using an implementation that relies on one of the other methods.
     *
     * @return the implementation method, for example {@link #ITERATE_METHOD} or {@link #EVALUATE_METHOD} or
     * {@link #PROCESS_METHOD}
     */
    @Override
    public int getImplementationMethod() {
        return ITERATE_METHOD;
    }

    /**
     * Evaluate the expression
     */

    public SequenceIterator iterate(XPathContext context) throws XPathException {
        ConversionRules rules = context.getConfiguration().getConversionRules();
        AtomicValue value = (AtomicValue) getBaseExpression().evaluateItem(context);
        if (value == null) {
            if (allowEmpty) {
                return null;
            } else {
                XPathException e = new XPathException("Cast does not allow an empty sequence");
                e.setXPathContext(context);
                e.setLocation(getLocation());
                e.setErrorCode("XPTY0004");
                throw e;
            }
        }

        try {
            AtomicSequence result = cast(value, targetType, getRetainedStaticContext(), rules);
            return result.iterate();
        } catch (XPathException err) {
            err.maybeSetContext(context);
            err.maybeSetLocation(getLocation());
            err.setErrorCode("FORG0001");
            throw err;
        }
    }

    /**
     * Static method to perform the castable check of an atomic value to a union type
     *
     * @param value      the input value to be converted. Must not be null.
     * @param targetType the union type to which the value is to be converted
     * @param nsResolver the namespace context, required if the type is namespace-sensitive
     * @param context    the XPath dynamic evaluation context
     * @return the result of the conversion (may be a sequence if the union includes list types in its membership)
     */
    public static boolean castable(AtomicValue value, UnionType targetType,
                                   NamespaceResolver nsResolver, XPathContext context) {

        try {
            CastToUnion.cast(value, targetType, nsResolver, context.getConfiguration().getConversionRules());
            return true;
        } catch (XPathException err) {
            return false;
        }
    }

    /**
     * Static method to perform the cast of an atomic value to a union type
     *
     * @param value      the input value to be converted. Must not be null.
     * @param targetType the union type to which the value is to be converted
     * @param nsResolver the namespace context, required if the type is namespace-sensitive
     * @param rules      the conversion rules
     * @return the result of the conversion (may be a sequence if the union includes list types in its membership)
     * @throws XPathException if the conversion fails
     */

    public static AtomicSequence cast(AtomicValue value, UnionType targetType,
                                      NamespaceResolver nsResolver, ConversionRules rules)
            throws XPathException {
        //ConversionRules rules = context.getConfiguration().getConversionRules();
        if (value == null) {
            throw new NullPointerException();
        }

        // 1. If the value is a string or untypedAtomic, try casting to each of the member types

        if (value instanceof StringValue && !(value instanceof AnyURIValue)) {
            try {
                return targetType.getTypedValue(value.getStringValueCS(), nsResolver, rules);
            } catch (ValidationException e) {
                e.setErrorCode("FORG0001");
                throw e;
            }
        }

        // 2. If the value is an instance of a type in the transitive membership of the union, return it unchanged

        AtomicType label = value.getItemType();
        Iterable<PlainType> memberTypes = targetType.getPlainMemberTypes();

        // 2a. Is the type annotation itself a member type of the union?
        for (PlainType member : memberTypes) {
            if (label.equals(member)) {
                return value;
            }
        }
        // 2b. Failing that, is some supertype of the type annotation a member type of the union?
        for (PlainType member : memberTypes) {
            AtomicType t = label;
            while (t != null) {
                if (t.equals(member)) {
                    return value;
                } else {
                    t = t.getBaseType() instanceof AtomicType ? (AtomicType) t.getBaseType() : null;
                }
            }
        }

        // 3. if the value can be cast to any of the member types, return the result of that cast

        for (PlainType type : memberTypes) {
            if (type instanceof AtomicType) {
                Converter c = rules.getConverter(value.getItemType(), (AtomicType) type);
                if (c != null) {
                    ConversionResult result = c.convert(value);
                    if (result instanceof AtomicValue) {
                        return (AtomicValue) result;
                    }
                }
            }
        }

        throw new XPathException("Cannot convert the supplied value to " + targetType.getDescription(), "FORG0001");
    }

    public String getExpressionName() {
        return "castToUnion";
    }


    /**
     * Is this expression the same as another expression?
     */

    public boolean equals(Object other) {
        return super.equals(other) &&
                other instanceof CastToUnion &&
                targetType == ((CastToUnion) other).targetType &&
                allowEmpty == ((CastToUnion) other).allowEmpty &&
                ExpressionTool.equalOrNull(getRetainedStaticContext(), ((CastToUnion) other).getRetainedStaticContext());
    }

    /**
     * get HashCode for comparing two expressions.
     */

    @Override
    public int hashCode() {
        return super.hashCode() ^ targetType.hashCode();
    }

    /**
     * The toString() method for an expression attempts to give a representation of the expression
     * in an XPath-like form, but there is no guarantee that the syntax will actually be true XPath.
     * In the case of XSLT instructions, the toString() method gives an abstracted view of the syntax
     */

    public String toString() {
        return targetType.getEQName() + "(" + getBaseExpression().toString() + ")";
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("castToUnion", this);
        out.emitAttribute("as", targetType.toString());
        getBaseExpression().export(out);
        out.endElement();
    }

}

// Copyright (c) 2011 Saxonica Limited.


