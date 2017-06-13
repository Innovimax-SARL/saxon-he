////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.lib.TraceListener;
import net.sf.saxon.om.*;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.SequenceType;

import java.util.HashMap;

/**
 * An IterateInstr is the compiled form of an xsl:iterate instruction
 */

public final class IterateInstr extends Instruction implements ContextSwitchingExpression {

    private Operand selectOp;
    private Operand actionOp;
    private Operand initiallyOp;
    private Operand onCompletionOp;

    /**
     * Create an xsl:iterate instruction
     *
     * @param select       the select expression
     * @param initiallyExp the initialization of the xsl:param elements
     * @param action       the body of the xsl:iterate loop
     * @param onCompletion   the expression to be evaluated before final completion, may be null
     */

    public IterateInstr(Expression select, LocalParamBlock initiallyExp, Expression action, /*@Nullable*/ Expression onCompletion) {
        if (onCompletion == null) {
            onCompletion = Literal.makeEmptySequence();
        }
        selectOp = new Operand(this, select, OperandRole.FOCUS_CONTROLLING_SELECT);
        actionOp = new Operand(this, action, OperandRole.FOCUS_CONTROLLED_ACTION);
        initiallyOp = new Operand(this, initiallyExp,
                                  new OperandRole(OperandRole.CONSTRAINED_CLASS, OperandUsage.NAVIGATION, SequenceType.ANY_SEQUENCE));
        onCompletionOp = new Operand(this, onCompletion,
                                     new OperandRole(OperandRole.USES_NEW_FOCUS, OperandUsage.TRANSMISSION));
    }

    public void setSelect(Expression select) {
        selectOp.setChildExpression(select);
    }

    public LocalParamBlock getInitiallyExp() {
        return (LocalParamBlock)initiallyOp.getChildExpression();
    }

    public void setInitiallyExp(LocalParamBlock initiallyExp) {
        initiallyOp.setChildExpression(initiallyExp);
    }

    public void setAction(Expression action) {
        actionOp.setChildExpression(action);
    }

    public Expression getOnCompletion() {
        return onCompletionOp.getChildExpression();
    }

    public void setOnCompletion(Expression onCompletion) {
        onCompletionOp.setChildExpression(onCompletion);
    }

    @Override
    public Iterable<Operand> operands() {
        return operandList(selectOp, actionOp, initiallyOp, onCompletionOp);
    }



    /**
     * Get the namecode of the instruction for use in diagnostics
     *
     * @return a code identifying the instruction: typically but not always
     *         the fingerprint of a name in the XSLT namespace
     */

    @Override
    public int getInstructionNameCode() {
        return StandardNames.XSL_ITERATE;
    }

    /**
     * Get the select expression (the select attribute of the xsl:iterate)
     *
     * @return the select expression
     */

    public Expression getSelectExpression() {
        return selectOp.getChildExpression();
    }


    /**
     * Get the action expression (the content of the xsl:iterate)
     *
     * @return the body of the xsl:iterate loop
     */

    public Expression getActionExpression() {
        return actionOp.getChildExpression();
    }

    /**
     * Type-check the expression
     */

    /*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {

        selectOp.typeCheck(visitor, contextInfo);
        initiallyOp.typeCheck(visitor, contextInfo);

        ItemType selectType = getSelectExpression().getItemType();
        if (selectType == ErrorType.getInstance()) {
            return Literal.makeEmptySequence();
        }

        ContextItemStaticInfo cit = visitor.getConfiguration().makeContextItemStaticInfo(getSelectExpression().getItemType(), false);
        cit.setContextSettingExpression(getSelectExpression());
        actionOp.typeCheck(visitor, cit);

        onCompletionOp.typeCheck(visitor, ContextItemStaticInfo.ABSENT);

        if (Literal.isEmptySequence(getSelectExpression())) {
            return getSelectExpression();
        }
        if (Literal.isEmptySequence(getActionExpression())) {
            return getActionExpression();
        }
        return this;
    }

    /*@NotNull*/
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {

        selectOp.optimize(visitor, contextInfo);
        initiallyOp.optimize(visitor, contextInfo);

        ContextItemStaticInfo cit2 = visitor.getConfiguration().makeContextItemStaticInfo(getSelectExpression().getItemType(), false);
        cit2.setContextSettingExpression(getSelectExpression());
        actionOp.optimize(visitor, cit2);

        onCompletionOp.optimize(visitor, ContextItemStaticInfo.ABSENT);

        if (Literal.isEmptySequence(getSelectExpression())) {
            return getSelectExpression();
        }
        if (Literal.isEmptySequence(getActionExpression())) {
            return getActionExpression();
        }

        return this;
    }

    /**
     * Test whether it is possible to generate byte-code for the instruction.
     * This is not possible unless any xsl:break or xsl:next-iteration instructions
     * are part of the same compilation unit, which is not the case if they appear
     * within a try-catch.
     * @return true if the instruction does not contain an xsl:break or xsl:next-iteration instruction
     * within a try/catch block
     */

    public boolean isCompilable() {
        return !containsBreakOrNextIterationWithinTryCatch(this, false);
    }

    private static boolean containsBreakOrNextIterationWithinTryCatch(Expression exp, boolean withinTryCatch) {
        if (exp instanceof BreakInstr || exp instanceof NextIteration) {
            return withinTryCatch;
        } else {
            boolean found = false;
            boolean inTryCatch = withinTryCatch || exp instanceof TryCatch;
            for (Operand o : exp.operands()) {
                if (containsBreakOrNextIterationWithinTryCatch(o.getChildExpression(), inTryCatch)) {
                    found = true;
                    break;
                }
            }
            return found;
        }
    }

    /**
     * Ensure that slots are allocated to parameters and with-param instructions.
     * This is normally done during compilation, but not in the case of an xsl:iterate
     * instruction constructed dynamically to support xsl:copy/@on-empty
     * @param nextFree the next slot available to be allocated, before allocating slots to this expression
     * @return the next slot available to be allocated, after allocating slots to this expression
     */

    public int allocateParameterSlots(int nextFree) {
        HashMap<StructuredQName, Integer> slotMap = new HashMap<StructuredQName, Integer>();
        for (Operand o : getInitiallyExp().operands()) {
            LocalParam b = (LocalParam)o.getChildExpression();
            if (b.getLocalSlotNumber() >= 0) {
                slotMap.put(b.getVariableQName(), b.getLocalSlotNumber());
            } else {
                int slot = nextFree++;
                b.setSlotNumber(slot);
                slotMap.put(b.getVariableQName(), slot);
            }
        }
        setWithParamSlots(getActionExpression(), slotMap);
        return nextFree;
    }

    private static void setWithParamSlots(Expression exp, HashMap<StructuredQName, Integer> slotMap) {
        if (exp instanceof NextIteration) {
            for (WithParam p : ((NextIteration) exp).getParameters()) {
                p.setSlotNumber(slotMap.get(p.getVariableQName()));
            }
        } else if (!(exp instanceof IterateInstr)) {
            for (Operand o : exp.operands()) {
                setWithParamSlots(o.getChildExpression(), slotMap);
            }
        } // Else no action - bug 2561
    }


    /**
     * Determine the data type of the items returned by this expression
     *
     * @return the data type
     */

    /*@NotNull*/
    public final ItemType getItemType() {
        if (Literal.isEmptySequence(getOnCompletion())) {
            return getActionExpression().getItemType();
        } else {
            TypeHierarchy th = getConfiguration().getTypeHierarchy();
            return Type.getCommonSuperType(getActionExpression().getItemType(), getOnCompletion().getItemType(), th);
        }
    }

    /**
     * Determine whether this instruction creates new nodes.
     * This implementation returns true if the "action" creates new nodes.
     * (Nodes created by the condition can't contribute to the result).
     */

    public final boolean mayCreateNewNodes() {
        return (getActionExpression().getSpecialProperties() &
                getOnCompletion().getSpecialProperties() &
                StaticProperty.NON_CREATIVE) == 0;
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
     * @param pathMapNodeSet the set of nodes in the path map that are affected
     * @return the pathMapNode representing the focus established by this expression, in the case where this
     *         expression is the first operand of a path expression or filter expression. For an expression that does
     *         navigation, it represents the end of the arc in the path map that describes the navigation route. For other
     *         expressions, it is the same as the input pathMapNode.
     */

    public PathMap.PathMapNodeSet addToPathMap(PathMap pathMap, PathMap.PathMapNodeSet pathMapNodeSet) {
        // TODO account for navigation done by the "initially" and "finally" expressions
        PathMap.PathMapNodeSet target = getSelectExpression().addToPathMap(pathMap, pathMapNodeSet);
        return getActionExpression().addToPathMap(pathMap, target);
    }

    /**
     * Compute the dependencies of an expression, as the union of the
     * dependencies of its subexpressions. (This is overridden for path expressions
     * and filter expressions, where the dependencies of a subexpression are not all
     * propogated). This method should be called only once, to compute the dependencies;
     * after that, getDependencies should be used.
     *
     * @return the depencies, as a bit-mask
     */

//    public int computeDependencies() {
//        // Some of the dependencies aren't relevant. Note that the sort keys are absorbed into the select
//        // expression.
//        int dependencies = 0;
//        dependencies |= getSelect().getDependencies();
//        dependencies |= getInitiallyExp().getDependencies();
//        dependencies |= getAction().getDependencies() & ~StaticProperty.DEPENDS_ON_FOCUS;
//        dependencies |= getOnCompletion().getDependencies() & ~StaticProperty.DEPENDS_ON_FOCUS;
//        return dependencies;
//    }


    /**
     * Ask whether this expression is, or contains, the binding of a given variable
     *
     * @param binding the variable binding
     * @return true if this expression is the variable binding (for example a ForExpression
     * or LetExpression) or if it is a FLWOR expression that binds the variable in one of its
     * clauses.
     */
    @Override
    public boolean hasVariableBinding(Binding binding) {
        LocalParamBlock paramBlock = getInitiallyExp();
        for (Operand o : paramBlock.operands()) {
            LocalParam setter = (LocalParam)o.getChildExpression();
            if (setter == binding) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the (partial) name of a class that supports streaming of this kind of expression
     *
     * @return the partial name of a class that can be instantiated to provide streaming support in Saxon-EE,
     * or null if there is no such class
     */
    @Override
    public String getStreamerName() {
        return "Iterate";
    }

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is provided. This implementation provides both iterate() and
     * process() methods natively.
     */

    public int getImplementationMethod() {
        return PROCESS_METHOD;
    }

    /**
     * Check that any elements and attributes constructed or returned by this expression are acceptable
     * in the content model of a given complex type. It's always OK to say yes, since the check will be
     * repeated at run-time. The process of checking element and attribute constructors against the content
     * model of a complex type also registers the type of content expected of those constructors, so the
     * static validation can continue recursively.
     */

    public void checkPermittedContents(SchemaType parentType, boolean whole) throws XPathException {
        getActionExpression().checkPermittedContents(parentType, false);
        getOnCompletion().checkPermittedContents(parentType, false);
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings
     */

    /*@NotNull*/
    public Expression copy(RebindingMap rebindings) {
        IterateInstr exp = new IterateInstr(
                getSelectExpression().copy(rebindings),
                (LocalParamBlock) getInitiallyExp().copy(rebindings),
                getActionExpression().copy(rebindings),
                getOnCompletion().copy(rebindings));
        ExpressionTool.copyLocationInfo(this, exp);
        return exp;
    }


    public TailCall processLeavingTail(XPathContext context) throws XPathException {
        FocusIterator iter = new FocusTrackingIterator(getSelectExpression().iterate(context));

        XPathContextMajor c2 = context.newContext();
        c2.setOrigin(this);
        c2.setCurrentIterator(iter);
        c2.setCurrentTemplateRule(null);

        boolean tracing = context.getController().isTracing();
        TraceListener listener = tracing ? context.getController().getTraceListener() : null;

        getInitiallyExp().process(context);

        while (true) {
            Item item = iter.next();
            if (item != null) {
                if (tracing) {
                    listener.startCurrentItem(item);
                }
                getActionExpression().process(c2);
                if (tracing) {
                    listener.endCurrentItem(item);
                }
                TailCallLoop.TailCallInfo comp = c2.getTailCallInfo();
                if (comp == null) {
                    // no xsl:next-iteration or xsl:break was encountered; just loop around
                } else if (comp instanceof BreakInstr) {
                    // indicates a xsl:break instruction was encountered: break the loop
                    //System.err.println("IterateInstr found xsl:break");
                    iter.close();
                    return null;
                } else {
                    // a xsl:next-iteration instruction was encountered.
                    // It will have reset the parameters to the loop; we just need to loop round
                }
            } else {
                // Execute on-completion instruction
                XPathContextMinor c3 = context.newMinorContext();
                c3.setCurrentIterator(null);
                getOnCompletion().process(c3);
                break;
            }
        }
        return null;
    }


    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("iterate", this);
        out.setChildRole("select");
        getSelectExpression().export(out);
        out.setChildRole("params");
        getInitiallyExp().export(out);
        if (!Literal.isEmptySequence(getOnCompletion())) {
            out.setChildRole("on-completion");
            getOnCompletion().export(out);
        }
        out.setChildRole("action");
        getActionExpression().export(out);
        out.endElement();
    }



}



