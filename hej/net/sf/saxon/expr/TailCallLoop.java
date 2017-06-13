////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.instruct.UserFunction;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.value.SequenceExtent;

/**
 * A TailCallLoop wraps the body of a function that contains tail-recursive function calls. On completion
 * of the "real" body of the function it tests whether the function has executed a tail call, and if so,
 * iterates to evaluate the tail call.
 */

public final class TailCallLoop extends UnaryExpression {

    UserFunction containingFunction;

    /**
     * Constructor - create a TailCallLoop
     *
     * @param function the function in which this tail call loop appears
     * @param body the function body (before wrapping in the tail call loop)
     */

    public TailCallLoop(UserFunction function, Expression body) {
        super(body);
        containingFunction = function;
    }

    /**
     * Get the containing function
     *
     * @return the containing function
     */

    public UserFunction getContainingFunction() {
        return containingFunction;
    }

    /**
     * Type-check the expression
     */

    /*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        typeCheckChildren(visitor, contextInfo);
        return this;
    }

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is provided. This implementation provides both iterate() and
     * process() methods natively.
     */

    public int getImplementationMethod() {
        return getBaseExpression().getImplementationMethod();
    }

    @Override
    protected OperandRole getOperandRole() {
        return OperandRole.SAME_FOCUS_ACTION;
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings
     */

    /*@NotNull*/
    public Expression copy(RebindingMap rebindings) {
       throw new UnsupportedOperationException("TailCallLoop.copy()");
        /*TailCallLoop e2 = new TailCallLoop(containingFunction);
        e2.setBaseExpression(getBaseExpression().copy());
        return e2;*/
    }

    /**
     * Iterate over the sequence of values
     */

    /*@NotNull*/
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        final XPathContextMajor cm = (XPathContextMajor) context;
        while (true) {
            SequenceIterator iter = getBaseExpression().iterate(cm);
            GroundedValue extent = SequenceExtent.makeSequenceExtent(iter);
            TailCallInfo tail = cm.getTailCallInfo();
            if (tail == null) {
                return extent.iterate();
            } else {
                UserFunction target = establishTargetFunction(tail, cm);
                if (target != containingFunction) {
                    return tailCallDifferentFunction(target, cm).iterate();
                }
                // otherwise, loop round to execute the tail call
            }
        }
    }

    /**
     * Evaluate as an Item.
     */

    public Item evaluateItem(XPathContext context) throws XPathException {
        final XPathContextMajor cm = (XPathContextMajor) context;
        while (true) {
            Item item = getBaseExpression().evaluateItem(context);

            TailCallInfo tail = cm.getTailCallInfo();
            if (tail == null) {
                return item;
            } else {
                UserFunction target = establishTargetFunction(tail, cm);
                if (target != containingFunction) {
                    return tailCallDifferentFunction(target, cm).head();
                }
                // otherwise, loop round to execute the tail call
            }
        }
    }

    private UserFunction establishTargetFunction(TailCallInfo tail, XPathContextMajor cm) {
        if (tail instanceof TailCallFunction) {
            return ((TailCallFunction) tail).function;
        } else if (tail instanceof TailCallComponent) {
            Component targetComponent = ((TailCallComponent) tail).component;
            cm.setCurrentComponent(targetComponent);
            return (UserFunction) targetComponent.getActor();
        } else {
            throw new AssertionError();
        }
    }

    /**
     * Process the function body
     *
     * @param context The dynamic context, giving access to the current node,
     *                the current variables, etc.
     */

    public void process(XPathContext context) throws XPathException {
        final XPathContextMajor cm = (XPathContextMajor) context;
        Expression operand = getBaseExpression();
        while (true) {
            operand.process(context);
            TailCallInfo tail = cm.getTailCallInfo();
            if (tail == null) {
                return;
            } else {
                UserFunction target = establishTargetFunction(tail, cm);
                if (target != containingFunction) {
                    SequenceTool.process(tailCallDifferentFunction(target, cm), cm, operand.getLocation());
                    return;
                }
                // otherwise, loop round to execute the tail call
            }
        }
    }

    /**
     * Make a tail call on a different function. This reuses the context object and the stack frame array
     * where possible, but it does consume some Java stack space. It's still worth it, because we don't use
     * as much stack as we would if we didn't return down to the TailCallLoop level.
     *
     * @param userFunction the function to be called
     * @param cm the dynamic context
     * @return the result of calling the other function
     * @throws XPathException if the called function fails
     */

    /*@Nullable*/
    private Sequence tailCallDifferentFunction(UserFunction userFunction, XPathContextMajor cm) throws XPathException {
        cm.resetStackFrameMap(userFunction.getStackFrameMap(), userFunction.getArity());
        try {
            return ExpressionTool.evaluate(userFunction.getBody(), userFunction.getEvaluationMode(), cm, 1);
        } catch (XPathException err) {
            err.maybeSetLocation(getLocation());
            err.maybeSetContext(cm);
            throw err;
        }
    }


    /**
     * Determine the data type of the items returned by the expression
     * <p/>
     * <p/>
     * /*@NotNull
     */
    public ItemType getItemType() {
        return getBaseExpression().getItemType();
    }

    /**
     * Give a string representation of the expression name for use in diagnostics
     *
     * @return the expression name, as a string
     */

    public String getExpressionName() {
        return "tailCallLoop";
    }



    public interface TailCallInfo {};

    protected static class TailCallComponent implements TailCallInfo {
        public Component component;
        public UserFunction function;
    }

    protected static class TailCallFunction implements TailCallInfo {
        public UserFunction function;
    }




}

