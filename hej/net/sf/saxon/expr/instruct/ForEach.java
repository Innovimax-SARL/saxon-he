////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.Controller;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.lib.TraceListener;
import net.sf.saxon.om.*;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ErrorType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.UType;
import net.sf.saxon.value.Cardinality;


/**
 * Handler for xsl:for-each elements in a stylesheet. The same class handles the "!" operator in XPath 3.0,
 * which has identical semantics to xsl:for-each, and it is used to support the "/" operator in cases where it
 * is known that the rhs delivers atomic values.
 */

public class ForEach extends Instruction implements ContextMappingFunction, ContextSwitchingExpression {

    protected boolean containsTailCall;
    protected Operand selectOp;
    protected Operand actionOp;
    protected Operand threadsOp;
    protected boolean isInstruction;

    /**
     * Create an xsl:for-each instruction
     *
     * @param select the select expression
     * @param action the body of the xsl:for-each loop
     */

    public ForEach(Expression select, Expression action) {
        this(select, action, false, null);
    }

    /**
     * Create an xsl:for-each instruction
     *
     * @param select           the select expression
     * @param action           the body of the xsl:for-each loop
     * @param containsTailCall true if the body of the loop contains a tail call on the containing function
     * @param threads          if >1 causes multithreaded execution (Saxon-EE only)
     */

    public ForEach(Expression select, Expression action, boolean containsTailCall, Expression threads) {
        selectOp = new Operand(this, select, OperandRole.FOCUS_CONTROLLING_SELECT);
        actionOp = new Operand(this, action, OperandRole.FOCUS_CONTROLLED_ACTION);
        if (threads != null) {
            threadsOp = new Operand(this, threads, OperandRole.SINGLE_ATOMIC);
        }
        this.containsTailCall = containsTailCall && action instanceof TailCallReturner;
    }

    /**
     * Say whether this ForEach expression originates as an XSLT instruction
     *
     * @param inst true if this is an xsl:for-each instruction; false if it is the XPath "!" operator
     */

    public void setInstruction(boolean inst) {
        isInstruction = inst;
    }

    /**
     * Ask whether this expression is an instruction. In XSLT streamability analysis this
     * is used to distinguish constructs corresponding to XSLT instructions from other constructs.
     *
     * @return true if this construct originates as an XSLT instruction
     */

    @Override
    public boolean isInstruction() {
        return isInstruction;
    }


    /**
     * Get the select expression
     *
     * @return the select expression. Note this will have been wrapped in a sort expression
     *         if sorting was requested.
     */

    public Expression getSelect() {
        return selectOp.getChildExpression();
    }

    /**
     * Set the select expression
     * @param select the select expression of the for-each
     */

    public void setSelect(Expression select) {
        selectOp.setChildExpression(select);
    }

    /**
     * Get the action expression (in XSLT, the body of the xsl:for-each instruction
     * @return the action expression
     */

    public Expression getAction() {
        return actionOp.getChildExpression();
    }

    /**
     * Set the action expression (in XSLT, the body of the xsl:for-each instruction)
     * @param action the action expression
     */

    public void setAction(Expression action) {
        actionOp.setChildExpression(action);
    }

    /**
     * Get the expression used to determine how many threads to use when multi-threading
     * @return the saxon:threads expression if present, or null otherwise
     */

    public Expression getThreads() {
        return threadsOp == null ? null : threadsOp.getChildExpression();
    }

    /**
     * Set the expression used to determine how many threads to use when multi-threading
     * @param threads the saxon:threads expression if present, or null otherwise
     */

    public void setThreads(Expression threads) {
        if (threads != null) {
            if (threadsOp == null) {
                threadsOp = new Operand(this, threads, OperandRole.SINGLE_ATOMIC);
            } else {
                threadsOp.setChildExpression(threads);
            }
        }
    }

    /**
     * Get the operands of this expression
     * @return the operands
     */

    @Override
    public Iterable<Operand> operands() {
        if (threadsOp == null) {
            return operandList(selectOp, actionOp);
        } else {
            return operandList(selectOp, actionOp, threadsOp);
        }
    }

    /**
     * Get the name of this instruction for diagnostic and tracing purposes
     *
     * @return the code for name xsl:for-each
     */

    public int getInstructionNameCode() {
        return StandardNames.XSL_FOR_EACH;
    }

    /**
     * Get the select expression
     *
     * @return the select expression. Note this will have been wrapped in a sort expression
     *         if sorting was requested.
     */

    public Expression getSelectExpression() {
        return getSelect();
    }

    /**
     * Set the select expression
     *
     * @param select the select expression
     */

    public void setSelectExpression(Expression select) {
        this.setSelect(select);
    }

    /**
     * Set the action expression
     *
     * @param action the select expression
     */

    public void setActionExpression(Expression action) {
        this.setAction(action);
    }

    /**
     * Get the subexpression that is evaluated in the new context
     *
     * @return the subexpression evaluated in the context set by the controlling expression
     */
    public Expression getActionExpression() {
        return getAction();
    }

    /**
     * Get the number of threads requested
     *
     * @return the value of the saxon:threads attribute
     */

    public Expression getNumberOfThreadsExpression() {
        return getThreads();
    }

    /**
     * Determine the data type of the items returned by this expression
     *
     * @return the data type
     */

    /*@NotNull*/
    public final ItemType getItemType() {
        return getAction().getItemType();
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
        if (isInstruction()) {
            return super.getStaticUType(contextItemType);
        } else {
            return getAction().getStaticUType(getSelect().getStaticUType(contextItemType));
        }
    }


    /**
     * Determine whether this instruction creates new nodes.
     * This implementation returns true if the "action" creates new nodes.
     * (Nodes created by the condition can't contribute to the result).
     */

    public final boolean mayCreateNewNodes() {
        int props = getAction().getSpecialProperties();
        return (props & StaticProperty.NON_CREATIVE) == 0;
    }

    /*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {

        selectOp.typeCheck(visitor, contextInfo);

        ItemType selectType = getSelect().getItemType();
        if (selectType == ErrorType.getInstance()) {
            return Literal.makeEmptySequence();
        }

        ContextItemStaticInfo cit = visitor.getConfiguration().makeContextItemStaticInfo(getSelect().getItemType(), false);
        cit.setContextSettingExpression(getSelect());
        actionOp.typeCheck(visitor, cit);

        if (!Cardinality.allowsMany(getSelect().getCardinality())) {
            actionOp.setOperandRole(actionOp.getOperandRole().modifyProperty(OperandRole.SINGLETON, true));
        }

        return this;
    }

    /*@NotNull*/
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        selectOp.optimize(visitor, contextInfo);

        ContextItemStaticInfo cit = visitor.getConfiguration().makeContextItemStaticInfo(getSelect().getItemType(), false);
        cit.setContextSettingExpression(getSelect());
        actionOp.optimize(visitor, cit);

        if (!visitor.isOptimizeForStreaming()) {
            // Don't eliminate a void for-each if streaming, because it can consume the stream: see test accumulator-015
            if (Literal.isEmptySequence(getSelect())) {
                return getSelect();
            }
            if (Literal.isEmptySequence(getAction())) {
                return getAction();
            }
        }

        if (threadsOp != null && !Literal.isEmptySequence(getThreads())) {
            return visitor.getConfiguration().obtainOptimizer().generateMultithreadedInstruction(this);
        }
        return this;
    }

    /**
     * Replace this expression by a simpler expression that delivers the results without regard
     * to order.
     *
     * @param retainAllNodes set to true if the result must contain exactly the same nodes as the
     *                       original; set to false if the result can eliminate (or introduce) duplicates.
     * @param forStreaming  set to true if optimizing for streaming
     */
    @Override
    public Expression unordered(boolean retainAllNodes, boolean forStreaming) throws XPathException {
        setSelect(getSelect().unordered(retainAllNodes, forStreaming));
        setAction(getAction().unordered(retainAllNodes, forStreaming));
        return this;
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
        PathMap.PathMapNodeSet target = getSelect().addToPathMap(pathMap, pathMapNodeSet);
        return getAction().addToPathMap(pathMap, target);
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings
     */

    /*@NotNull*/
    public Expression copy(RebindingMap rebindings) {
        ForEach f2 = new ForEach(getSelect().copy(rebindings), getAction().copy(rebindings), containsTailCall, getThreads());
        ExpressionTool.copyLocationInfo(this, f2);
        f2.setInstruction(isInstruction());
        return f2;
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
//        dependencies |= getAction().getDependencies() & ~StaticProperty.DEPENDS_ON_FOCUS;
//        return dependencies;
//    }

    /**
     * Get the static properties of this expression (other than its type). The result is
     * bit-signficant. These properties are used for optimizations. In general, if
     * property bit is set, it is true, but if it is unset, the value is unknown.
     *
     * @return a set of flags indicating static properties of this expression
     */
    @Override
    public int computeSpecialProperties() {
        int p = super.computeSpecialProperties();
        if (getSelect().getCardinality() == StaticProperty.EXACTLY_ONE) {
            p |= getAction().getSpecialProperties();
        } else {
            p |= getAction().getSpecialProperties() & StaticProperty.ALL_NODES_UNTYPED;
        }
        return p;
    }

    @Override
    public boolean alwaysCreatesNewNodes() {
        return (getAction() instanceof Instruction) && ((Instruction)getAction()).alwaysCreatesNewNodes();
    }

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is provided. This implementation provides both iterate() and
     * process() methods natively.
     */

    public int getImplementationMethod() {
        return ITERATE_METHOD | PROCESS_METHOD | Expression.WATCH_METHOD | Expression.ITEM_FEED_METHOD;
    }

    /**
     * Check that any elements and attributes constructed or returned by this expression are acceptable
     * in the content model of a given complex type. It's always OK to say yes, since the check will be
     * repeated at run-time. The process of checking element and attribute constructors against the content
     * model of a complex type also registers the type of content expected of those constructors, so the
     * static validation can continue recursively.
     */

    public void checkPermittedContents(SchemaType parentType, boolean whole) throws XPathException {
        getAction().checkPermittedContents(parentType, false);
    }

    public TailCall processLeavingTail(XPathContext context) throws XPathException {
        Controller controller = context.getController();
        assert controller != null;
        FocusIterator iter = new FocusTrackingIterator(getSelect().iterate(context));

        XPathContextMajor c2 = context.newContext();
        c2.setOrigin(this);
        c2.setCurrentIterator(iter);
        c2.setCurrentTemplateRule(null);

        if (containsTailCall) {
            if (controller.isTracing()) {
                TraceListener listener = controller.getTraceListener();
                assert listener != null;
                Item item = iter.next();
                if (item == null) {
                    return null;
                }
                listener.startCurrentItem(item);
                TailCall tc = ((TailCallReturner) getAction()).processLeavingTail(c2);
                listener.endCurrentItem(item);
                return tc;
            } else {
                Item item = iter.next();
                if (item == null) {
                    return null;
                }
                return ((TailCallReturner) getAction()).processLeavingTail(c2);
            }
        } else {
            if (controller.isTracing()) {
                TraceListener listener = controller.getTraceListener();
                assert listener != null;
                Item item;
                while ((item = iter.next()) != null) {
                    listener.startCurrentItem(item);
                    getAction().process(c2);
                    listener.endCurrentItem(item);
                }
            } else {
                while (iter.next() != null) {
                    getAction().process(c2);
                }
            }
        }
        return null;
    }

    /**
     * Return an Iterator to iterate over the values of the sequence.
     *
     * @param context supplies the context for evaluation
     * @return a SequenceIterator that can be used to iterate over the result
     *         of the expression
     * @throws XPathException if any dynamic error occurs evaluating the
     *                        expression
     */

    /*@NotNull*/
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        FocusIterator master = new FocusTrackingIterator(getSelect().iterate(context));
        XPathContextMinor c2 = context.newMinorContext();
        c2.setCurrentIterator(master);
        return new ContextMappingIterator<Item>(this, c2);
    }

    /**
     * Map one item to a sequence.
     *
     * @param context The processing context. The item to be mapped is the context item identified
     *                from this context: the values of position() and last() also relate to the set of items being mapped
     * @return a SequenceIterator over the sequence of items that the supplied input
     *         item maps to
     */

    public SequenceIterator map(XPathContext context) throws XPathException {
        return getAction().iterate(context);
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("forEach", this);
        getSelect().export(out);
        getAction().export(out);
        explainThreads(out);
        out.endElement();
    }

    protected void explainThreads(ExpressionPresenter out) throws XPathException {
        // no action in this class: implemented in subclass
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
        return ExpressionTool.parenthesize(getSelect()) + " ! " + ExpressionTool.parenthesize(getAction());
    }

    @Override
    public String toShortString() {
        return getSelect().toShortString() + "!" + getAction().toShortString();
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
        return "forEach";
    }

    /**
     * Get the (partial) name of a class that supports streaming of this kind of expression
     *
     * @return the partial name of a class that can be instantiated to provide streaming support in Saxon-EE,
     * or null if there is no such class
     */
    @Override
    public String getStreamerName() {
        return "ForEach";
    }
}

