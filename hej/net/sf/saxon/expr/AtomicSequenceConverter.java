////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.SequenceExtent;
import net.sf.saxon.value.StringValue;


/**
 * An AtomicSequenceConverter is an expression that performs a cast on each member of
 * a supplied sequence
 */

public class AtomicSequenceConverter extends UnaryExpression {

    public static ToStringMappingFunction TO_STRING_MAPPER = new ToStringMappingFunction();

    private PlainType requiredItemType;
    /*@Nullable*/ protected Converter converter;

    /**
     * Constructor
     *
     * @param sequence         this must be a sequence of atomic values. This is not checked; a ClassCastException
     *                         will occur if the precondition is not satisfied.
     * @param requiredItemType the item type to which all items in the sequence should be converted,
     */

    public AtomicSequenceConverter(Expression sequence, PlainType requiredItemType) {
        super(sequence);
        this.requiredItemType = requiredItemType;
    }

    public void allocateConverter(Configuration config, boolean allowNull) {
        allocateConverter(config, allowNull, getBaseExpression().getItemType());
    }

    public void allocateConverter(Configuration config, boolean allowNull, ItemType sourceType) {
        final ConversionRules rules = config.getConversionRules();
        if (sourceType instanceof ErrorType) {
            converter = Converter.IDENTITY_CONVERTER;
        } else if (!(sourceType instanceof AtomicType)) {
            converter = null;
        } else if (requiredItemType instanceof AtomicType) {
            converter = rules.getConverter((AtomicType) sourceType, (AtomicType) requiredItemType);
        } else if (((SimpleType) requiredItemType).isUnionType()) {
            converter = new StringConverter.StringToUnionConverter(requiredItemType, rules);
        }

        if (converter == null && !allowNull) {
            // source type not known statically; create a converter that decides at run-time
            converter = new Converter(rules) {
                /*@NotNull*/
                public ConversionResult convert(/*@NotNull*/ AtomicValue input) {
                    Converter converter = rules.getConverter(input.getPrimitiveType(), (AtomicType) requiredItemType);
                    if (converter == null) {
                        return new ValidationFailure("Cannot convert value from " + input.getPrimitiveType() + " to " + requiredItemType);
                    } else {
                        return converter.convert(input);
                    }
                }
            };
        }

    }

    protected OperandRole getOperandRole() {
        return OperandRole.ATOMIC_SEQUENCE;
    }

    /**
     * Get the required item type (the target type of the conversion
     *
     * @return the required item type
     */

    public PlainType getRequiredItemType() {
        return requiredItemType;
    }

    /**
     * Get the converter used to convert the items in the sequence
     *
     * @return the converter. Note that a converter is always allocated during the typeCheck() phase,
     *         even if the source type is not known.
     */

    public Converter getConverter() {
        return converter;
    }

    public void setConverter(Converter converter) {
        this.converter = converter;
    }

    /**
     * Simplify an expression
     *
     */

    /*@NotNull*/
    public Expression simplify() throws XPathException {
        Expression operand = getBaseExpression().simplify();
        setBaseExpression(operand);
        if (operand instanceof Literal && requiredItemType instanceof AtomicType) {
            if (Literal.isEmptySequence(operand)) {
                return operand;
            }
            Configuration config = getConfiguration();
            allocateConverter(config, true);
            if (converter != null) {
                GroundedValue val = SequenceExtent.makeSequenceExtent(
                        iterate(new EarlyEvaluationContext(config)));
                Literal result = Literal.makeLiteral(val);
                result.setRetainedStaticContext(operand.getRetainedStaticContext());
                return result;
            }
        }
        return this;
    }

    /**
     * Type-check the expression
     */

    /*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        typeCheckChildren(visitor, contextInfo);
        Configuration config = visitor.getConfiguration();
        final TypeHierarchy th = config.getTypeHierarchy();
        Expression operand = getBaseExpression();
        if (th.isSubType(operand.getItemType(), requiredItemType)) {
            return operand;
        } else {
            if (converter == null) {
                allocateConverter(config, true);
            }
            return this;
        }
    }

    /**
     * Perform optimisation of an expression and its subexpressions.
     */
    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        Expression e = super.optimize(visitor, contextInfo);
        if (e != this) {
            return e;
        }
        if (getBaseExpression() instanceof UntypedSequenceConverter) {
            UntypedSequenceConverter asc = (UntypedSequenceConverter) getBaseExpression();
            ItemType ascType = asc.getItemType();
            if (ascType == requiredItemType) {
                return getBaseExpression();
            } else if ((requiredItemType == BuiltInAtomicType.STRING || requiredItemType == BuiltInAtomicType.UNTYPED_ATOMIC) &&
                    (ascType == BuiltInAtomicType.STRING || ascType == BuiltInAtomicType.UNTYPED_ATOMIC)) {
                UntypedSequenceConverter old = (UntypedSequenceConverter) getBaseExpression();
                UntypedSequenceConverter asc2 = new UntypedSequenceConverter(
                        old.getBaseExpression(),
                        requiredItemType);
                return asc2.typeCheck(visitor, contextInfo)
                        .optimize(visitor, contextInfo);
            }
        } else if (getBaseExpression() instanceof AtomicSequenceConverter) {
            AtomicSequenceConverter asc = (AtomicSequenceConverter) getBaseExpression();
            ItemType ascType = asc.getItemType();
            if (ascType == requiredItemType) {
                return getBaseExpression();
            } else if ((requiredItemType == BuiltInAtomicType.STRING || requiredItemType == BuiltInAtomicType.UNTYPED_ATOMIC) &&
                    (ascType == BuiltInAtomicType.STRING || ascType == BuiltInAtomicType.UNTYPED_ATOMIC)) {
                AtomicSequenceConverter old = (AtomicSequenceConverter) getBaseExpression();
                AtomicSequenceConverter asc2 = new AtomicSequenceConverter(
                        old.getBaseExpression(),
                        requiredItemType
                );
                return asc2.typeCheck(visitor, contextInfo)
                        .optimize(visitor, contextInfo);
            }
        }
        return this;

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
     * Determine the special properties of this expression
     *
     * @return {@link StaticProperty#NON_CREATIVE}.
     */

    public int computeSpecialProperties() {
        return super.computeSpecialProperties() | StaticProperty.NON_CREATIVE;
    }


    @Override
    public String getStreamerName() {
        return "AtomicSequenceConverter";
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings
     */

    /*@NotNull*/
    public Expression copy(RebindingMap rebindings) {
        AtomicSequenceConverter atomicConverter = new AtomicSequenceConverter(getBaseExpression().copy(rebindings), requiredItemType);
        ExpressionTool.copyLocationInfo(this, atomicConverter);
        atomicConverter.setConverter(converter);
        return atomicConverter;
    }

    /**
     * Iterate over the sequence of values
     */

    /*@NotNull*/
    public SequenceIterator iterate(final XPathContext context) throws XPathException {
        SequenceIterator base = getBaseExpression().iterate(context);
        if (converter == null) {
            allocateConverter(context.getConfiguration(), false);
        }
        if (converter == Converter.TO_STRING) {
            return new ItemMappingIterator(base, TO_STRING_MAPPER, true);
        } else {
            AtomicSequenceMappingFunction mapper = new AtomicSequenceMappingFunction();
            mapper.setConverter(converter);
            return new ItemMappingIterator(base, mapper, true);
        }
    }

    /**
     * Mapping function wrapped around a converter
     */

    public static class AtomicSequenceMappingFunction implements ItemMappingFunction {
        private Converter converter;

        public void setConverter(Converter converter) {
            this.converter = converter;
        }

        public AtomicValue mapItem(Item item) throws XPathException {
            return converter.convert((AtomicValue) item).asAtomic();
        }
    }

    /**
     * Mapping function that converts every item in a sequence to a string
     */

    public static class ToStringMappingFunction
            implements ItemMappingFunction {
        public StringValue mapItem(Item item) throws XPathException {
            return StringValue.makeStringValue(item.getStringValueCS());
        }
    }


    /**
     * Evaluate as an Item. This should only be called if the AtomicSequenceConverter has cardinality zero-or-one
     */

    public AtomicValue evaluateItem(XPathContext context) throws XPathException {
        if (converter == null) {
            allocateConverter(context.getConfiguration(), false);
        }
        AtomicValue item = (AtomicValue) getBaseExpression().evaluateItem(context);
        if (item == null) {
            return null;
        }
        return converter.convert(item).asAtomic();
    }

    /**
     * Determine the data type of the items returned by the expression, if possible
     *
     * @return a value such as Type.STRING, Type.BOOLEAN, Type.NUMBER, Type.NODE,
     *         or Type.ITEM (meaning not known in advance)
     */

    /*@NotNull*/
    public ItemType getItemType() {
        return requiredItemType;
    }

    /**
     * Determine the static cardinality of the expression
     */

    public int computeCardinality() {
        return getBaseExpression().getCardinality();
    }

    /**
     * Is this expression the same as another expression?
     */

    public boolean equals(Object other) {
        return super.equals(other) &&
                requiredItemType.equals(((AtomicSequenceConverter) other).requiredItemType);
    }

    /**
     * get HashCode for comparing two expressions. Note that this hashcode gives the same
     * result for (A op B) and for (B op A), whether or not the operator is commutative.
     */

    @Override
    public int hashCode() {
        return super.hashCode() ^ requiredItemType.hashCode();
    }


    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     *
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     *         The name will always be in the form of a lexical XML QName, and should match the name used
     *         in explain() output displaying the expression.
     */
    @Override
    public String getExpressionName() {
        return "convert";
    }

    @Override
    protected String displayOperator(Configuration config) {
        return "convert";
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    public void export(ExpressionPresenter destination) throws XPathException {
        destination.startElement("convert", this);
        destination.emitAttribute("from", getBaseExpression().getItemType().toString());
        destination.emitAttribute("to", requiredItemType.toString());
        if (converter instanceof Converter.PromoterToDouble || converter instanceof Converter.PromoterToFloat) {
            destination.emitAttribute("flags", "p");
        }
        //destination.emitAttribute("using", converter.getClass().toString());
        getBaseExpression().export(destination);
        destination.endElement();
    }

}

