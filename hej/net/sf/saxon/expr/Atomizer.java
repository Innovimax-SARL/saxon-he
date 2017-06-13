////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.event.ReceiverOptions;
import net.sf.saxon.expr.instruct.Block;
import net.sf.saxon.expr.instruct.Choose;
import net.sf.saxon.expr.instruct.ValueOf;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.ma.arrays.ArrayItemType;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NameTest;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.*;
import net.sf.saxon.type.*;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.Cardinality;


/**
 * An Atomizer is an expression corresponding essentially to the fn:data() function: it
 * maps a sequence by replacing nodes with their typed values
 */

public final class Atomizer extends UnaryExpression {

    private boolean untyped = false;       //set to true if it is known that the nodes being atomized will be untyped
    private boolean singleValued = false; // set to true if all atomized nodes will atomize to a single atomic value
    /*@Nullable*/ private ItemType operandItemType = null;

    /**
     * Constructor
     *
     * @param sequence the sequence to be atomized
     */

    public Atomizer(Expression sequence) {
        super(sequence);
        sequence.setFlattened(true);
    }

    /**
     * Make an atomizer with a given operand
     *
     * @param sequence the operand
     * @return an Atomizer that atomizes the given operand, or another expression that returns the same result
     */

    public static Expression makeAtomizer(Expression sequence) {
        if (sequence instanceof Literal && ((Literal) sequence).getValue() instanceof AtomicSequence) {
            return sequence;
        } else {
            return new Atomizer(sequence);
        }
    }

    protected OperandRole getOperandRole() {
        return OperandRole.ATOMIC_SEQUENCE;
    }

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is provided directly. The other methods will always be available
     * indirectly, using an implementation that relies on one of the other methods.
     *
     * @return the implementation method, for example {@link #ITERATE_METHOD} or {@link #EVALUATE_METHOD} or
     *         {@link #PROCESS_METHOD}
     */

    public int getImplementationMethod() {
        return ITERATE_METHOD | WATCH_METHOD;
    }

    public ItemType getOperandItemType() {
        if (operandItemType == null) {
            operandItemType = getBaseExpression().getItemType();
        }
        return operandItemType;
    }

    /**
     * Simplify an expression
     *
     */

    /*@NotNull*/
    public Expression simplify() throws XPathException {
        //untyped = !getContainer().getPackageData().isSchemaAware();
        untyped = !getPackageData().isSchemaAware();
        computeSingleValued(getConfiguration().getTypeHierarchy());
        Expression operand = getBaseExpression().simplify();
        if (operand instanceof Literal) {
            GroundedValue val = ((Literal) operand).getValue();

            if (val instanceof AtomicValue) {
                return operand;
            }
            SequenceIterator iter = val.iterate();
            Item i;
            while ((i = iter.next()) != null) {
                if (i instanceof NodeInfo) {
                    return this;
                }
                if (i instanceof Function) {
                    if (((Function)i).isArray()) {
                        return this;
                    } else {
                        XPathException err = new XPathException("Cannot atomize a function item", "FOTY0013");
                        err.setLocation(getLocation());
                        throw err;
                    }
                }
            }
            // if all items in the sequence are atomic (they generally will be, since this is
            // done at compile time), then return the sequence
            return operand;
        } else if (operand instanceof ValueOf && (((ValueOf) operand).getOptions() & ReceiverOptions.DISABLE_ESCAPING) == 0) {
            // XSLT users tend to use ValueOf unnecessarily
            return ((ValueOf) operand).convertToCastAsString();
        }
        setBaseExpression(operand);
        return this;
    }

    /**
     * Type-check the expression
     */

    /*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        typeCheckChildren(visitor, contextInfo);
        untyped = untyped | !visitor.getStaticContext().getPackageData().isSchemaAware();

        // If the configuration allows typed data, check whether the content type of these particular nodes is untyped
        final TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
        computeSingleValued(th);
        resetLocalStaticProperties();
        ItemType operandType = getOperandItemType();
        if (th.isSubType(operandType, BuiltInAtomicType.ANY_ATOMIC)) {
            return getBaseExpression();
        }
        if (!operandType.isAtomizable()) {
            XPathException err;
            if (operandType instanceof FunctionItemType) {
                err = new XPathException(
                        "Cannot atomize a function item", "FOTY0013");
            } else {
                err = new XPathException(
                        "Cannot atomize an element that is defined in the schema to have element-only content", "FOTY0012");
            }
            err.setIsTypeError(true);
            err.setLocation(getLocation());
            throw err;
        }
        getBaseExpression().setFlattened(true);
        return this;
    }

    private void computeSingleValued(TypeHierarchy th) {
        ItemType operandType = getOperandItemType();
        if (th.relationship(operandType, ArrayItemType.ANY_ARRAY_TYPE) != TypeHierarchy.DISJOINT) {
            singleValued = false;
        } else {
            singleValued = untyped;
            if (!singleValued) {
                ItemType nodeType = getBaseExpression().getItemType();
                if (nodeType instanceof NodeTest) {
                    SchemaType st = ((NodeTest) nodeType).getContentType();
                    if (st == Untyped.getInstance() || st.isAtomicType() || (st.isComplexType() && st != AnyType.getInstance())) {
                        singleValued = true;
                    }
                    if (!nodeType.getUType().overlaps(UType.ELEMENT.union(UType.ATTRIBUTE))) {
                        singleValued = true;
                    }
                }
            }
        }
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
        Expression exp = super.optimize(visitor, contextInfo);
        if (exp == this) {
            final TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
            Expression operand = getBaseExpression();
            if (th.isSubType(operand.getItemType(), BuiltInAtomicType.ANY_ATOMIC)) {
                return operand;
            }
            if (operand instanceof ValueOf && (((ValueOf) operand).getOptions() & ReceiverOptions.DISABLE_ESCAPING) == 0) {
                // XSLT users tend to use ValueOf unnecessarily
                Expression cast = ((ValueOf) operand).convertToCastAsString();
                return cast.optimize(visitor, contextInfo);
            }
            if (operand instanceof LetExpression || operand instanceof ForExpression) {
                // replace data(let $x := y return z) by (let $x := y return data(z))
                Expression action = ((Assignation) operand).getAction();
                ((Assignation) operand).setAction(new Atomizer(action));
                return operand.optimize(visitor, contextInfo);
            }
            if (operand instanceof Choose) {
                // replace data(if x then y else z) by (if x then data(y) else data(z)
                ((Choose)operand).atomizeActions();
                return operand.optimize(visitor, contextInfo);
            }
            if (operand instanceof Block) {
                // replace data((x,y,z)) by (data(x), data(y), data(z)) as some of the atomizers
                // may prove to be redundant. (Also, it helps streaming)
                Operand[] children = ((Block) operand).getOperanda();
                Expression[] atomizedChildren = new Expression[children.length];
                for (int i = 0; i < children.length; i++) {
                    atomizedChildren[i] = new Atomizer(children[i].getChildExpression());
                }
                Block newBlock = new Block(atomizedChildren);
                return newBlock.typeCheck(visitor, contextInfo).optimize(visitor, contextInfo);
            }
            if (untyped && operand instanceof AxisExpression &&
                    ((AxisExpression)operand).getAxis() == AxisInfo.ATTRIBUTE &&
                    ((AxisExpression) operand).getNodeTest() instanceof NameTest) {
                StructuredQName name = ((AxisExpression) operand).getNodeTest().getMatchingNodeName();
                FingerprintedQName qName = new FingerprintedQName(name, visitor.getConfiguration().getNamePool());
                AttributeGetter ag = new AttributeGetter(qName);
                int checks = 0;
                if (((AxisExpression) operand).isContextPossiblyUndefined()) {
                    checks |= AttributeGetter.CHECK_CONTEXT_ITEM_PRESENT;
                }
                if (!(((AxisExpression) operand).getContextItemType() instanceof NodeTest)) {
                    checks |= AttributeGetter.CHECK_CONTEXT_ITEM_IS_NODE;
                }
                ag.setRequiredChecks(checks);
                ExpressionTool.copyLocationInfo(this, ag);
                return ag;
            }
        }
        return exp;
    }

    /**
     * Ask whether it is known that any nodes in the input will always be untyped
     *
     * @return true if it is known that all nodes in the input will be untyped
     */
    public boolean isUntyped() {
        return untyped;
    }


    /**
     * Determine the special properties of this expression
     *
     * @return {@link StaticProperty#NON_CREATIVE}.
     */

    public int computeSpecialProperties() {
        int p = super.computeSpecialProperties();
        p &= ~StaticProperty.NODESET_PROPERTIES;
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
        Atomizer copy = new Atomizer(getBaseExpression().copy(rebindings));
        copy.untyped = untyped;
        copy.singleValued = singleValued;
        ExpressionTool.copyLocationInfo(this, copy);
        return copy;
    }

    @Override
    public String getStreamerName() {
        return "Atomizer";
    }

    /**
     * Iterate over the sequence of values
     */

    /*@NotNull*/
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        SequenceIterator base = getBaseExpression().iterate(context);
        return getAtomizingIterator(base, untyped && operandItemType instanceof NodeTest);
    }

    /**
     * Evaluate as an Item. This should only be called if the Atomizer has cardinality zero-or-one,
     * which will only be the case if the underlying expression has cardinality zero-or-one.
     */

    public AtomicValue evaluateItem(XPathContext context) throws XPathException {
        Item i = getBaseExpression().evaluateItem(context);
        if (i == null) {
            return null;
        } else {
            return i.atomize().head();
        }
    }

    /**
     * Determine the data type of the items returned by the expression, if possible
     *
     * @return a value such as Type.STRING, Type.BOOLEAN, Type.NUMBER. For this class, the
     *         result is always an atomic type, but it might be more specific.
     */

    /*@NotNull*/
    public ItemType getItemType() {
        operandItemType = getBaseExpression().getItemType();
        TypeHierarchy th = getConfiguration().getTypeHierarchy();
        return getAtomizedItemType(getBaseExpression(), untyped, th);
    }

    /**
     * Compute the type that will result from atomizing the result of a given expression
     *
     * @param operand       the given expression
     * @param alwaysUntyped true if it is known that nodes will always be untyped
     * @param th            the type hierarchy cache
     * @return the item type of the result of evaluating the operand expression, after atomization, or
     *         xs:error if it is known that atomization will return an error
     */

    public static ItemType getAtomizedItemType(Expression operand, boolean alwaysUntyped, TypeHierarchy th) {
        ItemType in = operand.getItemType();
        if (in.isPlainType()) {
            return in;
        } else if (in instanceof NodeTest) {
            UType kinds = in.getUType();
            if (alwaysUntyped) {
                // Some node-kinds always have a typed value that's a string

                if (STRING_KINDS.subsumes(kinds)) {
                    return BuiltInAtomicType.STRING;
                }
                // Some node-kinds are always untyped atomic; some are untypedAtomic provided that the configuration
                // is untyped

                if (UNTYPED_IF_UNTYPED_KINDS.subsumes(kinds)) {
                    return BuiltInAtomicType.UNTYPED_ATOMIC;
                }
            } else {
                if (UNTYPED_KINDS.subsumes(kinds)) {
                    return BuiltInAtomicType.UNTYPED_ATOMIC;
                }
            }

            return in.getAtomizedItemType();
        } else if (in instanceof JavaExternalObjectType) {
            return in.getAtomizedItemType();
        } else if (in instanceof ArrayItemType) {
            PlainType ait = ((ArrayItemType)in).getMemberType().getPrimaryType().getAtomizedItemType();
            return ait == null ? ErrorType.getInstance() : ait;
        } else if (in instanceof FunctionItemType) {
            return ErrorType.getInstance();
        }
        return BuiltInAtomicType.ANY_ATOMIC;
    }

    /**
     * Node kinds whose typed value is always a string
     */
    public static final UType STRING_KINDS =
            UType.NAMESPACE.union(UType.COMMENT).union(UType.PI);

    /**
     * Node kinds whose typed value is always untypedAtomic
     */

    public static final UType UNTYPED_KINDS =
            UType.TEXT.union(UType.DOCUMENT);

    /**
     * Node kinds whose typed value is untypedAtomic if the configuration is untyped
     */

    public static final UType UNTYPED_IF_UNTYPED_KINDS =
            UType.TEXT.union(UType.ELEMENT).union(UType.DOCUMENT).union(UType.ATTRIBUTE);

    /**
     * Determine the static cardinality of the expression
     */

    public int computeCardinality() {
        ItemType in = getOperandItemType();
        Expression operand = getBaseExpression();
        if (singleValued) {
            return operand.getCardinality();
        } else if (untyped && in instanceof NodeTest) {
            return operand.getCardinality();
        } else if (Cardinality.allowsMany(operand.getCardinality())) {
            return StaticProperty.ALLOWS_ZERO_OR_MORE;
        } else if (in.isPlainType()) {
            return operand.getCardinality();
        } else if (in instanceof NodeTest) {
            SchemaType schemaType = ((NodeTest) in).getContentType();
            if (schemaType.isAtomicType()) {
                // can return at most one atomic value per node
                return operand.getCardinality();
            }
        }
        return StaticProperty.ALLOWS_ZERO_OR_MORE;
    }


    /**
     * Add a representation of this expression to a PathMap. The PathMap captures a map of the nodes visited
     * by an expression in a source tree.
     * <p/>
     * <p>The default implementation of this method assumes that an expression does no navigation other than
     * the navigation done by evaluating its subexpressions, and that the subexpressions are evaluated in the
     * same context as the containing expression. The method must be overridden for any expression
     * where these assumptions do not hold. For example, implementations exist for AxisExpression, ParentExpression,
     * and RootExpression (because they perform navigation), and for the doc(), document(), and collection()
     * functions because they create a new navigation root. Implementations also exist for PathExpression and
     * FilterExpression because they have subexpressions that are evaluated in a different context from the
     * calling expression.</p>
     *
     * @param pathMap        the PathMap to which the expression should be added
     * @param pathMapNodeSet the PathMapNodeSet to which the paths embodied in this expression should be added
     * @return the pathMapNodeSet representing the points in the source document that are both reachable by this
     *         expression, and that represent possible results of this expression. For an expression that does
     *         navigation, it represents the end of the arc in the path map that describes the navigation route. For other
     *         expressions, it is the same as the input pathMapNode.
     */

    public PathMap.PathMapNodeSet addToPathMap(PathMap pathMap, PathMap.PathMapNodeSet pathMapNodeSet) {
        PathMap.PathMapNodeSet result = getBaseExpression().addToPathMap(pathMap, pathMapNodeSet);
        if (result != null) {
            TypeHierarchy th = getConfiguration().getTypeHierarchy();
            ItemType operandItemType = getBaseExpression().getItemType();
            if (th.relationship(NodeKindTest.ELEMENT, operandItemType) != TypeHierarchy.DISJOINT ||
                    th.relationship(NodeKindTest.DOCUMENT, operandItemType) != TypeHierarchy.DISJOINT) {
                result.setAtomized();
            }
        }
        return null;
    }

    /**
     * Get an iterator that returns the result of atomizing the sequence delivered by the supplied
     * iterator
     *
     * @param base    the supplied iterator, the input to atomization
     * @param oneToOne this can safely be set to true if it is known that all nodes in the base sequence will
     *                be untyped and that there will be no arrays in the base sequence; but it is always OK
     *                to set it to false.
     * @return an iterator that returns atomic values, the result of the atomization
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic evaluation error occurs
     */

    public static SequenceIterator getAtomizingIterator(SequenceIterator base, boolean oneToOne) throws XPathException {
        int properties = base.getProperties();
        if ((properties & SequenceIterator.LAST_POSITION_FINDER) != 0) {
            int count = ((LastPositionFinder) base).getLength();
            if (count == 0) {
                return EmptyIterator.emptyIterator();
            } else if (count == 1) {
                Item first = base.next();
                return first.atomize().iterate();
            }
        } else if ((properties & SequenceIterator.ATOMIZING) != 0) {
            return new AxisAtomizingIterator((AtomizedValueIterator)base);
        }
        if (oneToOne) {
            return new UntypedAtomizingIterator(base);
        } else {
            return new AtomizingIterator(base);
        }
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    public String getExpressionName() {
        return "data";
    }

    /**
     * The toString() method for an expression attempts to give a representation of the expression
     * in an XPath-like form, but there is no guarantee that the syntax will actually be true XPath.
     * In the case of XSLT instructions, the toString() method gives an abstracted view of the syntax
     */
    @Override
    public String toString() {
        return "data(" + getBaseExpression().toString() + ")";
    }

    @Override
    public String toShortString() {
        return getBaseExpression().toShortString();
    }

    /**
     * Implement the mapping function. This is stateless, so there is a singleton instance.
     */

    public static class AtomizingFunction implements MappingFunction {

        /**
         * Private constructor, ensuring that everyone uses the singleton instance
         */

        private AtomizingFunction() {
        }

        private static final AtomizingFunction theInstance = new AtomizingFunction();

        /**
         * Get the singleton instance
         *
         * @return the singleton instance of this mapping function
         */

        public static AtomizingFunction getInstance() {
            return theInstance;
        }

        public AtomicIterator map(Item item) throws XPathException {
            if (item instanceof NodeInfo) {
                return ((NodeInfo) item).atomize().iterate();
            } else if (item instanceof AtomicValue) {
                return new SingleAtomicIterator((AtomicValue)item);
            } else {
                throw new XPathException("Cannot atomize a function item or external object", "FOTY0013");
            }
        }
    }


}

