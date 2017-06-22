////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.type.*;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.IntegerRange;
import net.sf.saxon.value.SequenceExtent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * An expression that delivers the concatenation of the results of its subexpressions. This may
 * represent an XSLT sequence constructor, or an XPath/XQuery expression of the form (a,b,c,d).
 */

public class Block extends Instruction {

    // TODO: allow the last expression in a Block to be a tail-call of a function, at least in push mode

    private Operand[] operanda;
    private boolean allNodesUntyped;

    /**
     * Create a block, supplying its child expressions
     * @param children the child expressions in order
     */

    public Block(Expression[] children) {
        operanda = new Operand[children.length];
        for (int i=0; i<children.length; i++) {
            operanda[i] = new Operand(this, children[i], OperandRole.SAME_FOCUS_ACTION);
        }
        for (Expression e : children) {
            adoptChildExpression(e);
        }
    }

    @Override
    public boolean isInstruction() {
        return false;
    }

    /**
     * Get the n'th child expression
     * @param n the position of the child expression required (zero-based)
     * @return the child expression at that position
     */
    
    private Expression child(int n) {
        return operanda[n].getChildExpression();
    }

    /**
     * Set the n'th child expression
     * @param n the position of the child expression to be modified (zero-based)
     * @param child the child expression at that position
     */

    private void setChild(int n, Expression child) {
        operanda[n].setChildExpression(child);
    }

    /**
     * Get the number of children
     * @return the number of child subexpressions
     */

    private int size() {
        return operanda.length;
    }

    @Override
    public Iterable<Operand> operands() {
        return Arrays.asList(operanda);
    }

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
        if (binding instanceof LocalParam) {
            for (Operand o : operanda) {
                if (o.getChildExpression() == binding) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Static factory method to create a block. If one of the arguments is already a block,
     * the contents will be merged into a new composite block
     *
     * @param e1 the first subexpression (child) of the block
     * @param e2 the second subexpression (child) of the block
     * @return a Block containing the two subexpressions, and if either of them is a block, it will
     *         have been collapsed to create a flattened sequence
     */

    public static Expression makeBlock(/*@Nullable*/ Expression e1, Expression e2) {
        if (e1 == null || Literal.isEmptySequence(e1)) {
            return e2;
        }
        if (e2 == null || Literal.isEmptySequence(e2)) {
            return e1;
        }
        if (e1 instanceof Block || e2 instanceof Block) {
            List<Expression> list = new ArrayList<Expression>(10);
            if (e1 instanceof Block) {
                for (Operand o : e1.operands()) {
                    list.add(o.getChildExpression());
                }
            } else {
                list.add(e1);
            }
            if (e2 instanceof Block) {
                for (Operand o : e2.operands()) {
                    list.add(o.getChildExpression());
                }
            } else {
                list.add(e2);
            }

            Expression[] exps = new Expression[list.size()];
            exps = list.toArray(exps);
            return new Block(exps);

        } else {
            Expression[] exps = {e1, e2};
            return new Block(exps);

        }
    }

    /**
     * Static factory method to create a block from a list of expressions
     *
     * @param list      the list of expressions making up this block. The members of the List must
     *                  be instances of Expression. The list is effectively copied; subsequent changes
     *                  to the contents of the list have no effect.
     * @return a Block containing the two subexpressions, and if either of them is a block, it will
     *         have been collapsed to create a flattened sequence
     */

    public static Expression makeBlock(List<Expression> list) {
        if (list.size() == 0) {
            return Literal.makeEmptySequence();
        } else if (list.size() == 1) {
            return list.get(0);
        } else {
            Expression[] exps = new Expression[list.size()];
            exps = list.toArray(exps);
            return new Block(exps);
        }
    }

    public String getExpressionName() {
        return "sequence";
    }

    /**
     * Get the children of this instruction
     *
     * @return the children of this instruction, as an array of Operand objects. May return
     *         a zero-length array if there are no children.
     */

    public Operand[] getOperanda() {
        return operanda;
    }


    public int computeSpecialProperties() {
        if (size() == 0) {
            // An empty sequence has all special properties except "has side effects".
            return StaticProperty.SPECIAL_PROPERTY_MASK & ~StaticProperty.HAS_SIDE_EFFECTS;
        }
        int p = super.computeSpecialProperties();
        if (allNodesUntyped) {
            p |= StaticProperty.ALL_NODES_UNTYPED;
        }
        // if all the expressions are axis expressions, we have a same-document node-set
        boolean allAxisExpressions = true;
        boolean allChildAxis = true;
        boolean allSubtreeAxis = true;
        for (Operand o : operands()) {
            Expression child = o.getChildExpression();
            if (!(child instanceof AxisExpression)) {
                allAxisExpressions = false;
                allChildAxis = false;
                allSubtreeAxis = false;
                break;
            }
            byte axis = ((AxisExpression) child).getAxis();
            if (axis != AxisInfo.CHILD) {
                allChildAxis = false;
            }
            if (!AxisInfo.isSubtreeAxis[axis]) {
                allSubtreeAxis = false;
            }
        }
        if (allAxisExpressions) {
            p |= StaticProperty.CONTEXT_DOCUMENT_NODESET |
                    StaticProperty.SINGLE_DOCUMENT_NODESET |
                    StaticProperty.NON_CREATIVE;
            // if they all use the child axis, then we have a peer node-set
            if (allChildAxis) {
                p |= StaticProperty.PEER_NODESET;
            }
            if (allSubtreeAxis) {
                p |= StaticProperty.SUBTREE_NODESET;
            }
            // special case: selecting attributes then children, node-set is sorted
            if (size() == 2 &&
                    ((AxisExpression) child(0)).getAxis() == AxisInfo.ATTRIBUTE &&
                    ((AxisExpression) child(1)).getAxis() == AxisInfo.CHILD) {
                p |= StaticProperty.ORDERED_NODESET;
            }
        }
        return p;
    }

    /**
     * Determine whether the block includes any instructions that might return nodes with a type annotation
     * @param insn the instruction (for example this block)
     * @param th the type hierarchy cache
     * @return true if any expression in the block can return type-annotated nodes
     */

    public static boolean mayReturnTypedNodes(Instruction insn, TypeHierarchy th) {
        for (Operand o : insn.operands()) {
            Expression exp = o.getChildExpression();
            if ((exp.getSpecialProperties() & StaticProperty.ALL_NODES_UNTYPED) == 0) {
                ItemType it = exp.getItemType();
                if (th.relationship(it, NodeKindTest.ELEMENT) != TypeHierarchy.DISJOINT ||
                        th.relationship(it, NodeKindTest.ATTRIBUTE) != TypeHierarchy.DISJOINT ||
                        th.relationship(it, NodeKindTest.ATTRIBUTE) != TypeHierarchy.DISJOINT) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Merge any adjacent instructions that create literal text nodes
     *
     * @return the expression after merging literal text instructions
     */

    public Expression mergeAdjacentTextInstructions() {
        boolean[] isLiteralText = new boolean[size()];
        boolean hasAdjacentTextNodes = false;
        for (int i = 0; i < size(); i++) {
            isLiteralText[i] = child(i) instanceof ValueOf &&
                    ((ValueOf) child(i)).getSelect() instanceof StringLiteral &&
                    !((ValueOf) child(i)).isDisableOutputEscaping();

            if (i > 0 && isLiteralText[i] && isLiteralText[i - 1]) {
                hasAdjacentTextNodes = true;
            }
        }
        if (hasAdjacentTextNodes) {
            List<Expression> content = new ArrayList<Expression>(size());
            String pendingText = null;
            for (int i = 0; i < size(); i++) {
                if (isLiteralText[i]) {
                    pendingText = (pendingText == null ? "" : pendingText) +
                            ((StringLiteral) ((ValueOf) child(i)).getSelect()).getStringValue();
                } else {
                    if (pendingText != null) {
                        ValueOf inst = new ValueOf(new StringLiteral(pendingText), false, false);
                        content.add(inst);
                        pendingText = null;
                    }
                    content.add(child(i));
                }
            }
            if (pendingText != null) {
                ValueOf inst = new ValueOf(new StringLiteral(pendingText), false, false);
                content.add(inst);
            }
            return makeBlock(content);
        } else {
            return this;
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
        Expression[] c2 = new Expression[size()];
        for (int c = 0; c < size(); c++) {
            c2[c] = child(c).copy(rebindings);
        }
        Block b2 = new Block(c2);
        for (int c = 0; c < size(); c++) {
            b2.adoptChildExpression(c2[c]);
        }
        b2.allNodesUntyped = allNodesUntyped;
        ExpressionTool.copyLocationInfo(this, b2);
        return b2;
    }

    /**
     * Determine the data type of the items returned by this expression
     *
     * @return the data type
     */

    /*@NotNull*/
    public final ItemType getItemType() {
        if (size() == 0) {
            return ErrorType.getInstance();
        }
        ItemType t1 = null;
        TypeHierarchy th = getConfiguration().getTypeHierarchy();
        for (int i = 0; i < size(); i++) {
            Expression child = child(i);
            if (!(child instanceof Message)) {
                ItemType t = child.getItemType();
                t1 = (t1 == null ? t : Type.getCommonSuperType(t1, t, th));
                if (t1 instanceof AnyItemType) {
                    return t1;  // no point going any further
                }
            }
        }
        return t1 == null ? ErrorType.getInstance() : t1;
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
            if (size() == 0) {
                return UType.VOID;
            }
            UType t1 = child(0).getStaticUType(contextItemType);
            for (int i = 1; i < size(); i++) {
                t1 = t1.union(child(i).getStaticUType(contextItemType));
                if (t1 == UType.ANY) {
                    return t1;  // no point going any further
                }
            }
            return t1;
        }
    }

    /**
     * Determine the cardinality of the expression
     */

    public final int getCardinality() {
        if (size() == 0) {
            return StaticProperty.EMPTY;
        }
        int c1 = child(0).getCardinality();
        for (int i = 1; i < size(); i++) {
            c1 = Cardinality.sum(c1, child(i).getCardinality());
            if (c1 == StaticProperty.ALLOWS_ZERO_OR_MORE) {
                break;
            }
        }
        return c1;
    }

    /**
     * Determine whether this instruction creates new nodes.
     * This implementation returns true if any child instruction
     * returns true.
     */

    public final boolean mayCreateNewNodes() {
        return someOperandCreatesNewNodes();
    }


    /**
     * Check to ensure that this expression does not contain any updating subexpressions.
     * This check is overridden for those expressions that permit updating subexpressions.
     *
     * @throws net.sf.saxon.trans.XPathException
     *          if the expression has a non-permitted updateing subexpression
     */

    public void checkForUpdatingSubexpressions() throws XPathException {
        if (size() < 2) {
            return;
        }
        boolean updating = false;
        boolean nonUpdating = false;
        for (Operand o : operands()) {
            Expression child = o.getChildExpression();
            if (!ExpressionTool.isAllowedInUpdatingContext(child)) {
                if (updating) {
                    XPathException err = new XPathException(
                            "If any subexpression is updating, then all must be updating", "XUST0001");
                    err.setLocation(child.getLocation());
                    throw err;
                }
                nonUpdating = true;
            }
            if (child.isUpdatingExpression()) {
                if (nonUpdating) {
                    XPathException err = new XPathException(
                            "If any subexpression is updating, then all must be updating", "XUST0001");
                    err.setLocation(child.getLocation());
                    throw err;
                }
                updating = true;
            }
        }
    }

    /**
     * Determine whether this is a vacuous expression as defined in the XQuery update specification
     *
     * @return true if this expression is vacuous
     */

    public boolean isVacuousExpression() {
        // true if all subexpressions are vacuous
        for (Operand o : operands()) {
            Expression child = o.getChildExpression();
            if (!child.isVacuousExpression()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Simplify an expression. This performs any static optimization (by rewriting the expression
     * as a different expression). The default implementation does nothing.
     *
     *
     *
     * @throws XPathException if an error is discovered during expression
     *                        rewriting
     */

    /*@NotNull*/
    public Expression simplify() throws XPathException {
        boolean allAtomic = true;
        boolean nested = false;

        for (int c = 0; c < size(); c++) {
            setChild(c, child(c).simplify());
            if (!Literal.isAtomic(child(c))) {
                allAtomic = false;
            }
            if (child(c) instanceof Block) {
                nested = true;
            } else if (Literal.isEmptySequence(child(c))) {
                nested = true;
            }
        }
        if (size() == 1) {
            Expression e = getOperanda()[0].getChildExpression();
            e.setParentExpression(getParentExpression());
            return e;
        } else if (size() == 0) {
            Expression result = Literal.makeEmptySequence();
            ExpressionTool.copyLocationInfo(this, result);
            result.setParentExpression(getParentExpression());
            return result;
        } else if (nested) {
            List<Expression> list = new ArrayList<Expression>(size() * 2);
            flatten(list);
            Expression[] children = new Expression[list.size()];
            for (int i = 0; i < list.size(); i++) {
                children[i] = list.get(i);
            }
            Block newBlock = new Block(children);
            ExpressionTool.copyLocationInfo(this, newBlock);
            return newBlock.simplify();
        } else if (allAtomic) {
            AtomicValue[] values = new AtomicValue[size()];
            for (int c = 0; c < size(); c++) {
                values[c] = (AtomicValue) ((Literal) child(c)).getValue();
            }
            Expression result = Literal.makeLiteral(new SequenceExtent(values));
            ExpressionTool.copyLocationInfo(this, result);
            result.setParentExpression(getParentExpression());
            return result;
        } else {
            return this;
        }
    }

    /**
     * Simplify the contents of a Block by merging any nested blocks, merging adjacent
     * literals, and eliminating any empty sequences.
     *
     * @param targetList the new list of expressions comprising the contents of the block
     *                   after simplification
     * @throws XPathException should not happen
     */

    private void flatten(List<Expression> targetList) throws XPathException {
        List<Item> currentLiteralList = null;
        for (Operand o : operands()) {
            Expression child = o.getChildExpression();
            if (Literal.isEmptySequence(child)) {
                // do nothing, omit it from the output
            } else if (child instanceof Block) {
                flushCurrentLiteralList(currentLiteralList, targetList);
                currentLiteralList = null;
                ((Block) child).flatten(targetList);
            } else if (child instanceof Literal && !(((Literal) child).getValue() instanceof IntegerRange)) {
                SequenceIterator iterator = ((Literal) child).getValue().iterate();
                if (currentLiteralList == null) {
                    currentLiteralList = new ArrayList<Item>(10);
                }
                Item item;
                while ((item = iterator.next()) != null) {
                    currentLiteralList.add(item);
                }
                // no-op
            } else {
                flushCurrentLiteralList(currentLiteralList, targetList);
                currentLiteralList = null;
                targetList.add(child);
            }
        }
        flushCurrentLiteralList(currentLiteralList, targetList);
    }

    private void flushCurrentLiteralList(List<Item> currentLiteralList, List<Expression> list) throws XPathException {
        if (currentLiteralList != null) {
            SequenceIterator iter = new net.sf.saxon.tree.iter.ListIterator(currentLiteralList);
            Literal lit = Literal.makeLiteral(SequenceExtent.makeSequenceExtent(iter));
            lit.setRetainedStaticContext(getRetainedStaticContext());
            list.add(lit);
        }
    }

    /**
     * Determine whether the block is a candidate for evaluation using a "shared append expression"
     * where the result of the evaluation is a sequence implemented as a list of subsequences
     * @return true if the block is a candidate for "shared append" processing
     */

    public boolean isCandidateForSharedAppend() {
        for (Operand o : operands()) {
            Expression exp = o.getChildExpression();
            if (exp instanceof VariableReference || exp instanceof Literal) {
                return true;
            }
        }
        return false;
    }

    /*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        typeCheckChildren(visitor, contextInfo);
        if (!mayReturnTypedNodes(this, visitor.getConfiguration().getTypeHierarchy())) {
            resetLocalStaticProperties();
            allNodesUntyped = true;
        }
        return this;
    }

    /*@NotNull*/
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        optimizeChildren(visitor, contextInfo);
        boolean canSimplify = false;
        boolean prevLiteral = false;
        // Simplify the expression by collapsing nested blocks and merging adjacent literals
        for (Operand o : operands()) {
            Expression child = o.getChildExpression();
            if (child instanceof Block) {
                canSimplify = true;
                break;
            }
            if (child instanceof Literal && !(((Literal) child).getValue() instanceof IntegerRange)) {
                if (prevLiteral || Literal.isEmptySequence(child)) {
                    canSimplify = true;
                    break;
                }
                prevLiteral = true;
            } else {
                prevLiteral = false;
            }
        }
        if (canSimplify) {
            List<Expression> list = new ArrayList<Expression>(size() * 2);
            flatten(list);
            Expression result = Block.makeBlock(list);
            result.setRetainedStaticContext(getRetainedStaticContext());
            return result;
        }
        if (size() == 0) {
            return Literal.makeEmptySequence();
        } else if (size() == 1) {
            return child(0);
        } else {
            return this;
        }
    }


    /**
     * Check that any elements and attributes constructed or returned by this expression are acceptable
     * in the content model of a given complex type. It's always OK to say yes, since the check will be
     * repeated at run-time. The process of checking element and attribute constructors against the content
     * model of a complex type also registers the type of content expected of those constructors, so the
     * static validation can continue recursively.
     */

    public void checkPermittedContents(SchemaType parentType, boolean whole) throws XPathException {
        for (Operand o : operands()) {
            Expression child = o.getChildExpression();
            child.checkPermittedContents(parentType, false);
        }
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("sequence", this);
        for (Operand o : operands()) {
            Expression child = o.getChildExpression();
            child.export(out);
        }
        out.endElement();
    }

    @Override
    public String toShortString() {
        return "(" + child(0).toShortString() + ", ...)";
    }

    public TailCall processLeavingTail(XPathContext context) throws XPathException {
        TailCall tc = null;
        for (Operand o : operands()) {
            Expression child = o.getChildExpression();
            try {
                if (child instanceof TailCallReturner) {
                    tc = ((TailCallReturner) child).processLeavingTail(context);
                } else {
                    child.process(context);
                    tc = null;
                }
            } catch (XPathException e) {
                e.maybeSetLocation(child.getLocation());
                e.maybeSetContext(context);
                throw e;
            }
        }
        return tc;
    }

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is provided. This implementation provides both iterate() and
     * process() methods natively.
     */

    public int getImplementationMethod() {
        return ITERATE_METHOD | PROCESS_METHOD;
    }

    /**
     * Iterate over the results of all the child expressions
     */

    /*@NotNull*/
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        if (size() == 0) {
            return EmptyIterator.emptyIterator();
        } else if (size() == 1) {
            return child(0).iterate(context);
        } else {
            return new BlockIterator(operanda, context);
        }
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
        for (Operand o : operands()) {
            Expression child = o.getChildExpression();
            child.evaluatePendingUpdates(context, pul);
        }
    }

    @Override
    public String getStreamerName() {
        return "Block";
    }

}