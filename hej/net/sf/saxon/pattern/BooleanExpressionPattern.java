////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.om.Item;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.ManualIterator;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.UType;

/**
 * A BooleanExpressionPattern is a pattern of the form .[ Expr ] introduced in XSLT 3.0. It matches
 * an item if the expression has an effective boolean value of true() when evaluated with that item
 * as the singleton focus.
 *
 * @author Michael H. Kay
 */

public class BooleanExpressionPattern extends Pattern implements PatternWithPredicate {

    private Expression expression;

    /**
     * Create a BooleanExpressionPattern
     *
     * @param expression the expression to be evaluated (the expression in the predicate)
     */

    public BooleanExpressionPattern(Expression expression) {
        this.expression = expression;
        setPriority(1);
    }

    @Override
    public Expression getPredicate() {
        return expression;
    }

    /**
     * Get the immediate sub-expressions of this expression, with information about the relationship
     * of each expression to its parent expression. Default implementation
     * works off the results of iterateSubExpressions()
     * <p/>
     * <p>If the expression is a Callable, then it is required that the order of the operands
     * returned by this function is the same as the order of arguments supplied to the corresponding
     * call() method.</p>
     *
     * @return an iterator containing the sub-expressions of this expression
     */
    @Override
    public Iterable<Operand> operands() {
        return new Operand(this, expression, OperandRole.SINGLE_ATOMIC);
    }

    /**
     * Get a UType indicating which kinds of items this Pattern can match.
     *
     * @return a UType indicating all the primitive types of item that the pattern can match.
     */
    @Override
    public UType getUType() {
        if (expression instanceof InstanceOfExpression) {
            return ((InstanceOfExpression) expression).getRequiredItemType().getUType();
        } else {
            return UType.ANY;
        }
    }

    /**
     * Allocate slots to any variables used within the pattern
     *
     * @param slotManager holds details of the allocated slots
     * @param nextFree    the next slot that is free to be allocated @return the next slot that is free to be allocated
     */

    public int allocateSlots(SlotManager slotManager, int nextFree) {
        return ExpressionTool.allocateSlots(expression, nextFree, slotManager);
    }

    /**
     * Type-check the pattern.
     *
     * @param visitor         the expression visitor
     * @param contextItemType the type of the context item at the point where the pattern
     *                        is defined. Set to null if it is known that the context item is undefined.
     * @return the optimised Pattern
     */
    @Override
    public Pattern typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        ContextItemStaticInfo cit = visitor.getConfiguration().getDefaultContextItemStaticInfo();
        expression = expression.typeCheck(visitor, cit);
        return this;
    }

    @Override
    public Pattern optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        ContextItemStaticInfo cit = visitor.getConfiguration().getDefaultContextItemStaticInfo();
        expression = expression.optimize(visitor, cit);
        return this;
    }

    /**
     * Determine whether this Pattern matches the given item. This is the main external interface
     * for matching patterns: it sets current() to the node being tested
     *
     * @param item    The item to be tested against the Pattern
     * @param context The context in which the match is to take place. Only relevant if the pattern
     *                uses variables, or contains calls on functions such as document() or key().
     * @return true if the item matches the Pattern, false otherwise
     */

    public boolean matches(Item item, XPathContext context) {
        XPathContext c2 = context.newMinorContext();
        ManualIterator iter = new ManualIterator(item);
        c2.setCurrentIterator(iter);
        try {
//            if (comp != null) {
//                return comp.effectiveBooleanValue(c2);
//            }
            return expression.effectiveBooleanValue(c2);
        } catch (XPathException e) {
            return false;
        }
    }

    /**
     * Get an Itemtype that all the items matching this pattern must satisfy
     */

    public ItemType getItemType() {
        return AnyItemType.getInstance();
    }

    /**
     * Determine the name fingerprint of nodes to which this pattern applies. Used for
     * optimisation.
     *
     * @return A fingerprint that the nodes must match, or -1 if it can match multiple fingerprints
     */

    public int getFingerprint() {
        return -1;
    }

    /**
     * Display the pattern for diagnostics
     */

    public String toString() {
        return ".[" + expression.toString() + "]";
    }

    /**
     * Determine whether this pattern is the same as another pattern
     *
     * @param other the other object
     */

    public boolean equals(/*@NotNull*/ Object other) {
        return (other instanceof BooleanExpressionPattern) &&
                ((BooleanExpressionPattern) other).expression.equals(expression);
    }

    /**
     * Hashcode supporting equals()
     */

    public int hashCode() {
        return 0x7aeffea9 ^ expression.hashCode();
    }

    /**
     * Copy a pattern. This makes a deep copy.
     *
     * @return the copy of the original pattern
     * @param rebindings
     */

    /*@NotNull*/
    public Pattern copy(RebindingMap rebindings) {
        BooleanExpressionPattern n = new BooleanExpressionPattern(expression.copy(rebindings));
        ExpressionTool.copyLocationInfo(this, n);
        return n;
    }

    public void export(ExpressionPresenter presenter) throws XPathException {
        presenter.startElement("p.booleanExp");
        /*if (comp != null) {
            comp.export(presenter);
        } else { */
            expression.export(presenter);
        //}
        presenter.endElement();
    }



//    CompiledExpression comp;
//
//    public CompiledExpression compileToByteCode() {
//        CompilerService cs = new CompilerService(expression.getConfiguration());
//        comp = cs.compileToByteCode(expression, this.getClass().getSimpleName(), Expression.EFFECTIVE_BOOLEAN_VALUE);
//        return comp;
//    }


    public Expression getExpression() {
        return expression;
    }


}

