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
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.type.*;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.SequenceType;

/**
 * Expression class for a cast to a List type
 */
public class CastToList extends UnaryExpression {

    private ListType targetType;
    private boolean allowEmpty;

    public CastToList(Expression source, ListType targetType, boolean allowEmpty) {
        super(source);
        this.targetType = targetType;
        this.allowEmpty = allowEmpty;
    }

    public boolean isAllowEmpty() {
        return allowEmpty;
    }

    public ListType getTargetType() {
        return targetType;
    }

    public NamespaceResolver getNamespaceResolver() {
        return getRetainedStaticContext();
    }

    protected OperandRole getOperandRole() {
        return OperandRole.SINGLE_ATOMIC;
    }

    /**
     * Type-check the expression
     */

    /*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        typeCheckChildren(visitor, contextInfo);
        SequenceType atomicType = SequenceType.makeSequenceType(
                BuiltInAtomicType.STRING,
                allowEmpty ? StaticProperty.ALLOWS_ZERO_OR_ONE : StaticProperty.EXACTLY_ONE);

        RoleDiagnostic role = new RoleDiagnostic(RoleDiagnostic.TYPE_OP, "cast as", 0);
        Expression operand = visitor.getConfiguration().getTypeChecker(false).staticTypeCheck(
                getBaseExpression(), atomicType, role, visitor);
        setBaseExpression(operand);

        final TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
        boolean maybeString = th.relationship(operand.getItemType(), BuiltInAtomicType.STRING) != TypeHierarchy.DISJOINT;
        boolean maybeUntyped = th.relationship(operand.getItemType(), BuiltInAtomicType.UNTYPED_ATOMIC) != TypeHierarchy.DISJOINT;

        if (!maybeString && !maybeUntyped) {
            XPathException err = new XPathException("Casting to list requires an xs:string or xs:untypedAtomic operand");
            err.setErrorCode("XPTY0004");
            err.setLocation(getLocation());
            err.setIsTypeError(true);
            throw err;
        }

        if (operand instanceof Literal) {
            GroundedValue literalOperand = ((Literal) operand).getValue();
            if (literalOperand instanceof AtomicValue) {
                try {
                    SequenceIterator seq = iterate(visitor.getStaticContext().makeEarlyEvaluationContext());
                    return Literal.makeLiteral(SequenceTool.toGroundedValue(seq));
                } catch (XPathException err) {
                    err.maybeSetErrorCode("FORG0001");
                    err.setLocation(getLocation());
                    err.setIsTypeError(true);
                    throw err;
                }
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
        return StaticProperty.ALLOWS_ZERO_OR_MORE;
    }

    /**
     * Get the static type of the expression
     * <p/>
     * <p/>
     * <p/>
     * /*@NotNull
     */
    public ItemType getItemType() {
        try {
            if (targetType.getItemType() instanceof ItemType) {
                return (ItemType) targetType.getItemType();
            }
        } catch (MissingComponentException e) {
            //
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
        return UType.ANY_ATOMIC;
    }


    /**
     * Determine the special properties of this expression
     *
     * @return {@link net.sf.saxon.expr.StaticProperty#NON_CREATIVE}.
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
        CastToList c = new CastToList(getBaseExpression().copy(rebindings), targetType, allowEmpty);
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
        AtomicValue value = (AtomicValue) getBaseExpression().evaluateItem(context);
        if (value == null) {
            if (allowEmpty) {
                return EmptyIterator.emptyIterator();
            } else {
                XPathException e = new XPathException("Cast does not allow an empty sequence");
                e.setXPathContext(context);
                e.setLocation(getLocation());
                e.setErrorCode("XPTY0004");
                throw e;
            }
        }
        return cast(value.getStringValueCS(), targetType, getRetainedStaticContext(),
                context.getConfiguration().getConversionRules()).iterate();
    }

    /**
     * Cast a string value to a list type
     *
     * @param value      the input string value
     * @param targetType the target list type
     * @param nsResolver the namespace context, needed if the type is namespace-sensitive
     * @param rules      the conversion rules
     * @return the sequence of atomic values that results from the conversion
     * @throws XPathException if the conversion fails
     */

    public static AtomicSequence cast(CharSequence value, ListType targetType, final NamespaceResolver nsResolver, final ConversionRules rules)
            throws XPathException {
        ValidationFailure failure = targetType.validateContent(value, nsResolver, rules);
        if (failure != null) {
            throw failure.makeException();
        }
        return targetType.getTypedValue(value, nsResolver, rules);
    }


    /**
     * Is this expression the same as another expression?
     */

    public boolean equals(Object other) {
        return super.equals(other) &&
                other instanceof CastToList &&
                targetType == ((CastToList) other).targetType &&
                allowEmpty == ((CastToList) other).allowEmpty &&
                ExpressionTool.equalOrNull(getRetainedStaticContext(), ((CastToList)other).getRetainedStaticContext());
    }

    /**
     * get HashCode for comparing two expressions.
     */

    @Override
    public int hashCode() {
        return super.hashCode() ^ targetType.hashCode();
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
        return "castToList";
    }

    /**
     * The toString() method for an expression attempts to give a representation of the expression
     * in an XPath-like form, but there is no guarantee that the syntax will actually be true XPath.
     * In the case of XSLT instructions, the toString() method gives an abstracted view of the syntax
     */

    public String toString() {
        return targetType.getEQName() + "(" + getBaseExpression().toString() + ")";
    }

    @Override
    public String toShortString() {
        return targetType.getDisplayName() + "(" + getBaseExpression().toShortString() + ")";
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("castToList", this);
        out.emitAttribute("as", targetType.toString());
        getBaseExpression().export(out);
        out.endElement();
    }

}

// Copyright (c) 2011-2012 Saxonica Limited.


