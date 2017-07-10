////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.instruct.Block;
import net.sf.saxon.expr.instruct.UserFunction;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trace.LocationKind;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.SymbolicName;
import net.sf.saxon.trans.Visibility;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.UType;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.Whitespace;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * This class represents a call to a user-defined function in the stylesheet or query.
 */

public class UserFunctionCall extends FunctionCall implements UserFunctionResolvable, ComponentInvocation, ContextOriginator {

    private SequenceType staticType;
    private UserFunction function;
    private int bindingSlot = -1;
    private int tailCall = NOT_TAIL_CALL;
    private StructuredQName name;

    public boolean isBeingInlined() {
        return beingInlined;
    }

    public void setBeingInlined(boolean beingInlined) {
        this.beingInlined = beingInlined;
    }

    private boolean beingInlined = false;


    /*@Nullable*/ private int[] argumentEvaluationModes = null;

    public static final int NOT_TAIL_CALL = 0;
    public static final int FOREIGN_TAIL_CALL = 1;
    public static final int SELF_TAIL_CALL = 2;

    /**
     * Create a function call to a user-written function in a query or stylesheet
     */

    public UserFunctionCall() {
    }


    /**
     * Set the name of the function being called
     *
     * @param name the name of the function
     */

    public final void setFunctionName(StructuredQName name) {
        this.name = name;
    }



    /**
     * Set the static type
     *
     * @param type the static type of the result of the function call
     */

    public void setStaticType(SequenceType type) {
        staticType = type;
    }

    /**
     * Create the reference to the function to be called
     *
     * @param compiledFunction the function being called
     */

    public void setFunction(UserFunction compiledFunction) {
        function = compiledFunction;
    }

    /**
     * Set the binding slot to be used. This is the offset within the binding vector of the containing
     * component where the actual target template is to be found. The target function is not held directly
     * in the UserFunctionCall expression itself because it can be overridden in a using package.
     *
     * @param slot the offset in the binding vector of the containing package where the target template
     *             can be found.
     */


    public void setBindingSlot(int slot) {
        this.bindingSlot = slot;
    }

    /**
      * Get the binding slot to be used. This is the offset within the binding vector of the containing
      * component where the actual target template is to be found.
      *
      * @return the offset in the binding vector of the containing package where the target template
      *         can be found.
      */

     public int getBindingSlot() {
         return bindingSlot;
     }




    /**
     * Get the function that is being called by this function call. This is the provisional
     * binding: the actual function might be an override of this one.
     *
     * @return the function being called
     */

    public UserFunction getFunction() {
        return function;
    }

    public Component getFixedTarget() {
        Visibility v = function.getDeclaringComponent().getVisibility();
        if (v == Visibility.PRIVATE || v == Visibility.FINAL) {
            return function.getDeclaringComponent();
        } else {
            return null;
        }
    }

    /**
     * Determine whether this is a tail call (not necessarily a recursive tail call)
     *
     * @return true if this function call is a tail call
     */

    public boolean isTailCall() {
        return tailCall != NOT_TAIL_CALL;
    }

    public boolean isRecursiveTailCall() {
        return tailCall == SELF_TAIL_CALL;
    }

    /**
     * Get the qualified of the function being called
     *
     * @return the qualified name
     */

    public final StructuredQName getFunctionName() {
        if (name == null) {
            return function.getFunctionName();
        } else {
            return name;
        }
    }

    public SymbolicName getSymbolicName() {
        return new SymbolicName.F(getFunctionName(), getArity());
    }

    public Component getTarget() {
        return function.getDeclaringComponent();
    }

    /**
     * Get the evaluation modes that have been determined for each of the arguments
     *
     * @return an array of integers representing the evaluation modes, one for each argument
     */

    public int[] getArgumentEvaluationModes() {
        return argumentEvaluationModes;
    }

    /**
     * Set the argument evaluation modes
     * @param evalModes the argument evaluation modes to be used
     */

    public void setArgumentEvaluationModes(int[] evalModes) {
        argumentEvaluationModes = evalModes;
    }

    /**
     * Pre-evaluate a function at compile time. This version of the method suppresses
     * early evaluation by doing nothing.
     *
     * @param visitor an expression visitor
     */

    public Expression preEvaluate(ExpressionVisitor visitor) {
        return this;
    }

    /**
     * Determine the data type of the expression, if possible
     *
     * @return Type.ITEM (meaning not known in advance)
     */

    /*@NotNull*/
    public ItemType getItemType() {
        if (staticType == null) {
            // the actual type is not known yet, so we return an approximation
            return AnyItemType.getInstance();
        } else {
            return staticType.getPrimaryType();
        }
    }

    /**
     * Get the static type of the expression as a UType, following precisely the type
     * inference rules defined in the XSLT 3.0 specification.
     *
     * @param contextItemType the static type of the context item
     * @return the static item type of the expression according to the XSLT 3.0 defined rules
     */
    @Override
    public UType getStaticUType(UType contextItemType) {
        UserFunction f = getFunction();
        if (f == null) {
            // Happens when called during parsing
            return UType.ANY;
        }
        return f.getResultType().getPrimaryType().getUType();
    }

    public int getIntrinsicDependencies() {
        return StaticProperty.DEPENDS_ON_USER_FUNCTIONS;
    }

    /**
     * Determine whether this is an updating expression as defined in the XQuery update specification
     *
     * @return true if this is an updating expression
     */

    public boolean isUpdatingExpression() {
        return function.isUpdating();
    }

    /**
     * Compute the special properties of this expression. These properties are denoted by a bit-significant
     * integer, possible values are in class {@link net.sf.saxon.expr.StaticProperty}. The "special" properties are properties
     * other than cardinality and dependencies, and most of them relate to properties of node sequences, for
     * example whether the nodes are in document order.
     *
     * @return the special properties, as a bit-significant integer
     */

    protected int computeSpecialProperties() {
        // Inherit the properties of the function being called if possible. But we have to prevent
        // looping when the function is recursive. For safety, we only consider the properties of the
        // function body if it contains no further function calls.
        // TODO: this approach is not safe if the function can be overridden in another package
        if (function == null) {
            return super.computeSpecialProperties();
//        } else if (function.getDeclaredStreamability() != FunctionStreamability.UNCLASSIFIED) {
//            // TODO: this is a first approximation. Need to consider grounded nodes in the result...
//            int p = StaticProperty.ORDERED_NODESET;
//            if (function.getDeclaredStreamability() == FunctionStreamability.SHALLOW_DESCENT) {
//                p |= StaticProperty.PEER_NODESET;
//            }
//            return p;
        } else if (function.getBody() != null) {
            int props;
            List<UserFunction> calledFunctions = new ArrayList<UserFunction>();
            ExpressionTool.gatherCalledFunctions(function.getBody(), calledFunctions);
            if (calledFunctions.isEmpty()) {
                props = function.getBody().getSpecialProperties();
            } else {
                props = super.computeSpecialProperties();
            }
            if (function.getDeterminism() != UserFunction.Determinism.PROACTIVE) {
                props |= StaticProperty.NON_CREATIVE;
            }
            return props;
        } else {
            return super.computeSpecialProperties();
        }
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings
     */

    /*@NotNull*/
    public Expression copy(RebindingMap rebindings) {
        if (function == null) {
            // not bound yet, we have no way to register the new copy with the XSLFunction
            throw new UnsupportedOperationException("UserFunctionCall.copy()");
        }
        UserFunctionCall ufc = new UserFunctionCall();
        ufc.setFunction(function);
        ufc.setStaticType(staticType);
        int numArgs = getArity();
        Expression[] a2 = new Expression[numArgs];
        for (int i = 0; i < numArgs; i++) {
            a2[i] = getArg(i).copy(rebindings);
        }
        ufc.setArguments(a2);
        ExpressionTool.copyLocationInfo(this, ufc);
        return ufc;
    }

    /**
     * Determine the cardinality of the result
     */

    public int computeCardinality() {
        if (staticType == null) {
            // the actual type is not known yet, so we return an approximation
            return StaticProperty.ALLOWS_ZERO_OR_MORE;
        } else {
            return staticType.getCardinality();
        }
    }

    /*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        Expression e = super.typeCheck(visitor, contextInfo);
        if (e != this) {
            return e;
        }
        if (function != null) {
            checkFunctionCall(function, visitor);
            if (staticType == null || staticType == SequenceType.ANY_SEQUENCE) {
                // try to get a better type
                staticType = function.getResultType();
            }
        }
        return this;
    }

    /*@NotNull*/
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        //function.setOnFirstCall(new DeferredAction(visitor, contextItemType));
        Expression e = super.optimize(visitor, contextItemType);
        if (e == this && function != null) {
            Expression e2 = visitor.getConfiguration().obtainOptimizer().tryInlineFunctionCall(
                    this, visitor, contextItemType);
            if (e2 != this) {
                return e2.optimize(visitor, contextItemType);
            }
            return e2;
        }
        return e;
    }

    /**
     * Reset the static properties of the expression to -1, so that they have to be recomputed
     * next time they are used.
     */
    @Override
    public void resetLocalStaticProperties() {
        super.resetLocalStaticProperties();
        argumentEvaluationModes = null;
    }

    /**
     * Compute the evaluation mode of each argument
     */

    public synchronized void computeArgumentEvaluationModes() {
        int numArgs = getArity();
        argumentEvaluationModes = new int[numArgs];
        for (int i = 0; i < numArgs; i++) {
            if (i == 0 && function.getDeclaredStreamability().isConsuming()) {
                argumentEvaluationModes[i] = ExpressionTool.STREAMING_ARGUMENT;
            } else if (function.getParameterDefinitions()[i].isIndexedVariable()) {
                argumentEvaluationModes[i] = ExpressionTool.MAKE_INDEXED_VARIABLE;
            } else {
                Expression arg = getArg(i);
                if ((arg.getDependencies() & StaticProperty.DEPENDS_ON_USER_FUNCTIONS) != 0) {
                    // if the argument contains a call to a user-defined function, then it might be a recursive call.
                    // It's better to evaluate it now, rather than waiting until we are on a new stack frame, as
                    // that can blow the stack if done repeatedly. (See test func42)
                    argumentEvaluationModes[i] = ExpressionTool.eagerEvaluationMode(arg);
                } else if (!Cardinality.allowsMany(arg.getCardinality()) && arg.getCost() < 20) {
                    // the argument is cheap to evaluate and doesn't use much memory...
                    argumentEvaluationModes[i] = ExpressionTool.eagerEvaluationMode(arg);
                } else if (arg instanceof Block && ((Block) arg).isCandidateForSharedAppend()) {
                    // If the expression is a Block, that is, it is appending a value to a sequence,
                    // then we have the opportunity to use a shared list underpinning the old value and
                    // the new. This takes precedence over lazy evaluation (it would be possible to do this
                    // lazily, but more difficult). We currently do this for any Block that has a variable
                    // reference as one of its subexpressions. The most common case is that the first argument is a reference
                    // to an argument of recursive function, where the recursive function returns the result of
                    // appending to the sequence.
                    argumentEvaluationModes[i] = ExpressionTool.SHARED_APPEND_EXPRESSION;
                } else {
                    argumentEvaluationModes[i] = ExpressionTool.MAKE_MEMO_CLOSURE;
                }
            }
        }
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
     * @return the pathMapNode representing the focus established by this expression, in the case where this
     *         expression is the first operand of a path expression or filter expression. For an expression that does
     *         navigation, it represents the end of the arc in the path map that describes the navigation route. For other
     *         expressions, it is the same as the input pathMapNode.
     */

    public PathMap.PathMapNodeSet addToPathMap(PathMap pathMap, PathMap.PathMapNodeSet pathMapNodeSet) {
        return addExternalFunctionCallToPathMap(pathMap, pathMapNodeSet);
    }

    /**
     * Mark tail-recursive calls on stylesheet functions. This marks the function call as tailRecursive if
     * if is a call to the containing function, and in this case it also returns "true" to the caller to indicate
     * that a tail call was found.
     */

    public int markTailFunctionCalls(StructuredQName qName, int arity) {
        tailCall = getFunctionName().equals(qName) &&
                arity == getArity() ? SELF_TAIL_CALL : FOREIGN_TAIL_CALL;
        return tailCall;
    }

    public int getImplementationMethod() {
        if (Cardinality.allowsMany(getCardinality())) {
            return ITERATE_METHOD | PROCESS_METHOD;
        } else {
            return EVALUATE_METHOD;
        }
    }

    /**
     * Call the function, returning the value as an item. This method will be used
     * only when the cardinality is zero or one. If the function is tail recursive,
     * it returns an Object representing the arguments to the next (recursive) call
     */

    public Item evaluateItem(XPathContext c) throws XPathException {
        return callFunction(c).head();
    }

    /**
     * Call the function, returning an iterator over the results. (But if the function is
     * tail recursive, it returns an iterator over the arguments of the recursive call)
     */

    /*@NotNull*/
    public SequenceIterator iterate(XPathContext c) throws XPathException {
        return callFunction(c).iterate();
    }


    /**
     * Evaluate an updating expression, adding the results to a Pending Update List.
     * The default implementation of this method, which is used for non-updating expressions,
     * throws an UnsupportedOperationException
     *
     * @param context the XPath dynamic evaluation context
     * @param pul     the pending update list to which the results should be written
     */

    public void evaluatePendingUpdates(XPathContext context, PendingUpdateList pul) throws XPathException {
        Sequence[] actualArgs = evaluateArguments(context);
        XPathContextMajor c2 = context.newCleanContext();
        c2.setOrigin(this);
        function.callUpdating(actualArgs, c2, pul);
    }

    // KILROY
    public static Map<String, Integer> counters = new HashMap<String, Integer>();
    public static void showCounters() {
        for (Map.Entry<String, Integer> entry : counters.entrySet()) {
            if (entry.getValue() > 100) {
                System.err.println(entry.getKey() + " :  " + entry.getValue());
            }
        }
    }

    /**
     * This is the method that actually does the function call (in pull mode)
     *
     * @param context the dynamic context
     * @return the result of the function
     * @throws XPathException if dynamic errors occur
     */
    private Sequence callFunction(XPathContext context) throws XPathException {
        UserFunction targetFunction;
        Sequence[] actualArgs = evaluateArguments(context);
        XPathContextMajor c2;
        if (isTailCall()) {
            requestTailCall(context, actualArgs);
            return EmptySequence.getInstance();
        }

        if (bindingSlot >= 0) {
            Component target = getTargetComponent(context);
            targetFunction = (UserFunction)target.getActor();
            if (target.getVisibility() == Visibility.ABSENT) {
                throw new XPathException("Cannot call a function defined with visibility=absent", "XTDE3052");
            }
            c2 = targetFunction.makeNewContext(context);
            c2.setCurrentComponent(target);
            c2.setOrigin(this);
        } else {
            targetFunction = function;
            c2 = targetFunction.makeNewContext(context);
            c2.setOrigin(this);
        }

        // KILROY
        String key = context.getCurrentComponent().getActor().getSymbolicName() + " -> " +
                targetFunction.getSymbolicName();
        Integer count = counters.get(key);
        if (count == null) {
            counters.put(key, 1);
        } else {
            counters.put(key, count+1);
        }

        try {
            Sequence s = targetFunction.call(c2, actualArgs);
            return s;
        } catch (StackOverflowError err) {
            throw new XPathException("Too many nested function calls. May be due to infinite recursion",
                SaxonErrorCode.SXLM0001, getLocation());
        }
    }

    private void requestTailCall(XPathContext context, Sequence[] actualArgs) throws XPathException {
        if (bindingSlot >= 0) {
            TailCallLoop.TailCallComponent info = new TailCallLoop.TailCallComponent();
            Component target = getTargetComponent(context);
            info.component = target;
            info.function = (UserFunction) target.getActor();
            ((XPathContextMajor) context).requestTailCall(info, actualArgs);

        } else {
            TailCallLoop.TailCallFunction info = new TailCallLoop.TailCallFunction();
            info.function = function;
            ((XPathContextMajor) context).requestTailCall(info, actualArgs);
        }

    }

    /**
     * Process the function call in push mode
     *
     * @param context the XPath dynamic context
     * @throws XPathException
     */

    public void process(XPathContext context) throws XPathException {

        Sequence[] actualArgs = evaluateArguments(context);

        if (isTailCall()) {
            requestTailCall(context, actualArgs);
            return;
        }

        if (bindingSlot >= 0) {
            Component target = getTargetComponent(context);
            UserFunction targetFunction = (UserFunction) target.getActor();
            if (target.getVisibility() == Visibility.ABSENT) {
                throw new XPathException("Cannot call a function defined with visibility=absent", "XTDE3052");
            }
            XPathContextMajor c2 = targetFunction.makeNewContext(context);
            c2.setCurrentComponent(target);
            targetFunction.process(actualArgs, c2);
        } else {
            XPathContextMajor c2 = function.makeNewContext(context);
            function.process(actualArgs, c2);
        }
    }

    public Component getTargetComponent(XPathContext context) {
        if (bindingSlot == -1) {
            // fallback for non-package code
            return function.getDeclaringComponent();
        } else {
            return context.getTargetComponent(bindingSlot);
        }
    }

    public UserFunction getTargetFunction(XPathContext context) {
        return (UserFunction)getTargetComponent(context).getActor();
    }

    public Sequence[] evaluateArguments(XPathContext c) throws XPathException {
        return evaluateArguments(c, false);
    }


    public Sequence[] evaluateArguments(XPathContext c, boolean streamed) throws XPathException {
        int numArgs = getArity();
        Sequence[] actualArgs = new Sequence[numArgs];
        synchronized(this) {
            if (argumentEvaluationModes == null) {
                // should have been done at compile time
                computeArgumentEvaluationModes();
            }
        }
        for (int i = 0; i < numArgs; i++) {

            int refs = 10; // was function.getParameterDefinitions()[i].getReferenceCount();
            int evalMode = argumentEvaluationModes[i];
            if (evalMode == ExpressionTool.STREAMING_ARGUMENT && !streamed) {
                evalMode = ExpressionTool.ITERATE_AND_MATERIALIZE;
            }
            actualArgs[i] = ExpressionTool.evaluate(getArg(i), evalMode, c, refs);

            if (actualArgs[i] == null) {
                actualArgs[i] = EmptySequence.getInstance();
            }
//            // If the argument has come in as a (non-memo) closure but there are multiple references to it,
//            // then we materialize it in memory now. This shouldn't really happen but it does (tour.xq)
//            if (refs > 1 && actualArgs[i] instanceof Closure) {
//                actualArgs[i] = ((Closure) actualArgs[i]).reduce();
//            }
        }
        return actualArgs;
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("ufCall", this);
        if (getFunctionName() != null) {
            out.emitAttribute("name", getFunctionName().getEQName());
            out.emitAttribute("tailCall",
                    tailCall == NOT_TAIL_CALL ? "false" : tailCall == SELF_TAIL_CALL ? "self" : "foreign");
        }
        out.emitAttribute("bSlot", "" + getBindingSlot());
        if (getArgumentEvaluationModes() != null && getArity() > 0) {
            FastStringBuffer fsb = new FastStringBuffer(FastStringBuffer.C64);
            int[] e = getArgumentEvaluationModes();
            for (int i : e) {
                fsb.append(i+" ");
            }
            out.emitAttribute("eval", Whitespace.trim(fsb));
        }
        for (Operand o : operands()) {
            o.getChildExpression().export(out);
        }
        if (getFunctionName() == null) {
            out.setChildRole("inline");
            function.getBody().export(out);
            out.endElement();
        }
        out.endElement();
    }

    public int getConstructType() {
        return LocationKind.FUNCTION_CALL;
    }

    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     *
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     * The name will always be in the form of a lexical XML QName, and should match the name used
     * in explain() output displaying the expression.
     */
    @Override
    public String getExpressionName() {
        return "userFunctionCall";
    }

    public Object getProperty(String name) {
        if (name.equals("target")) {
            return function;
        }
        return super.getProperty(name);
    }

    public StructuredQName getObjectName() {
        return getFunctionName();
    }



}