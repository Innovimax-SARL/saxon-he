////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.expr.sort.DocumentSorter;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.*;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ErrorType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.UType;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.IntegerValue;

import java.util.ArrayList;
import java.util.List;


/**
 * A slash expression is any expression using the binary slash operator "/". The parser initially generates a slash
 * expression for all occurrences of the binary "/" operator, wrapped in a documentSort() function to do the sorting
 * and de-duplication required by the semantics of path expressions. The documentSort() is subsequently stripped off
 * by the optimizer if sorting and deduplication is found to be unnecessary. The slash expression itself, therefore,
 * does not perform sorting or de-duplication.
 */

public class RawSlashExpression extends BinaryExpression
        implements ContextSwitchingExpression, ContextMappingFunction {

    /**
     * Constructor
     *
     * @param start The left hand operand (which must always select a sequence of nodes).
     * @param step  The step to be followed from each node in the start expression to yield a new
     *              sequence; this may return either nodes or atomic values (but not a mixture of the two)
     */

    public RawSlashExpression(Expression start, Expression step) {
        super(start, Token.SLASH, step);
    }

    @Override
    protected OperandRole getOperandRole(int arg) {
        return arg==0 ? OperandRole.FOCUS_CONTROLLING_SELECT : OperandRole.FOCUS_CONTROLLED_ACTION;
    }

    /**
     * Get the left-hand operand
     * @return the left-hand operand
     */

    public Expression getStart() {
        return getLhsExpression();
    }

    /**
     * Set the left-hand operand
     * @param start the left-hand operand
     */

    public void setStart(Expression start) {
        setLhsExpression(start);
    }

    /**
     * Get the right-hand operand
     * @return the right-hand operand
     */

    public Expression getStep() {
        return getRhsExpression();
    }

    /**
     * Set the right-hand operand
     * @param step the right-hand operand
     */

    public void setStep(Expression step) {
        setRhsExpression(step);
    }

    @Override
    public String getExpressionName() {
        return "rawPathExp";
    }

    /**
     * Get the start expression (the left-hand operand)
     *
     * @return the first operand
     */

    public Expression getSelectExpression() {
        return getStart();
    }

    /**
     * Get the step expression (the right-hand operand)
     *
     * @return the second operand
     */

    public Expression getActionExpression() {
        return getStep();
    }

    /**
     * Determine the data type of the items returned by this exprssion
     *
     * @return the type of the step
     */

    /*@NotNull*/
    public final ItemType getItemType() {
        return getStep().getItemType();
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
        return getStep().getStaticUType(getStart().getStaticUType(contextItemType));
    }


    /**
     * For an expression that returns an integer or a sequence of integers, get
     * a lower and upper bound on the values of the integers that may be returned, from
     * static analysis. The default implementation returns null, meaning "unknown" or
     * "not applicable". Other implementations return an array of two IntegerValue objects,
     * representing the lower and upper bounds respectively. The values
     * UNBOUNDED_LOWER and UNBOUNDED_UPPER are used by convention to indicate that
     * the value may be arbitrarily large. The values MAX_STRING_LENGTH and MAX_SEQUENCE_LENGTH
     * are used to indicate values limited by the size of a string or the size of a sequence.
     *
     * @return the lower and upper bounds of integer values in the result, or null to indicate
     *         unknown or not applicable.
     */
    @Override
    public IntegerValue[] getIntegerBounds() {
        return getStep().getIntegerBounds();
    }

    /**
     * Type-check the expression
     */

    /*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {

        // Check the first operand
        getLhs().typeCheck(visitor, contextInfo);

        // Now check the second operand
        ItemType startType = getStart().getItemType();
        if (startType.equals(ErrorType.getInstance())) {
            return Literal.makeEmptySequence();
        }
        ContextItemStaticInfo cit = visitor.getConfiguration().makeContextItemStaticInfo(startType, false);
        cit.setContextSettingExpression(getStart());
        getRhs().typeCheck(visitor, cit);

        // Now convert to a SlashExpression, adding document sorting/deduplication if necessary
        SlashExpression e2 = new SlashExpression(getStart(), getStep());
        ExpressionTool.copyLocationInfo(this, e2);

        Expression result = e2;
        UType t = e2.getStaticUType(contextInfo.getContextItemUType());
        if (t.overlaps(UType.ANY_NODE)) {
            if (t.overlaps(UType.ANY_ATOMIC.union(UType.FUNCTION))) {
                result = new HomogeneityChecker(result);
            } else if ((e2.getSpecialProperties() & StaticProperty.ORDERED_NODESET) != 0) {
                // no action
            } else if ((e2.getSpecialProperties() & StaticProperty.REVERSE_DOCUMENT_ORDER) != 0) {
                result = SystemFunction.makeCall("reverse", getRetainedStaticContext(), e2);
            } else {
                result = new DocumentSorter(e2);
            }
        }

        ExpressionTool.copyLocationInfo(this, result);
        return result.simplify().typeCheck(visitor, contextInfo);
    }


    /**
     * Return the estimated cost of evaluating an expression. This is a very crude measure based
     * on the syntactic form of the expression (we have no knowledge of data values). We take
     * the cost of evaluating a simple scalar comparison or arithmetic expression as 1 (one),
     * and we assume that a sequence has length 5. The resulting estimates may be used, for
     * example, to reorder the predicates in a filter expression so cheaper predicates are
     * evaluated first.
     */
    @Override
    public int getCost() {
        return getLhsExpression().getCost() * getRhsExpression().getCost();
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
        setStart(getStart().unordered(retainAllNodes, forStreaming));
        setStep(getStep().unordered(retainAllNodes, forStreaming));
        return this;
    }


    /**
     * Add a representation of this expression to a PathMap. The PathMap captures a map of the nodes visited
     * by an expression in a source tree.
     *
     * @param pathMap        the PathMap to which the expression should be added
     * @param pathMapNodeSet
     * @return the pathMapNode representing the focus established by this expression, in the case where this
     *         expression is the first operand of a path expression or filter expression
     */

    public PathMap.PathMapNodeSet addToPathMap(PathMap pathMap, PathMap.PathMapNodeSet pathMapNodeSet) {
        PathMap.PathMapNodeSet target = getStart().addToPathMap(pathMap, pathMapNodeSet);
        return getStep().addToPathMap(pathMap, target);
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
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings
     */

    /*@NotNull*/
    public RawSlashExpression copy(RebindingMap rebindings) {
        RawSlashExpression exp = (RawSlashExpression)ExpressionTool.makePathExpression(getStart().copy(rebindings), getStep().copy(rebindings), false);
        ExpressionTool.copyLocationInfo(this, exp);
        return exp;
    }


    /**
     * Determine the static cardinality of the expression
     */

    public int computeCardinality() {
        int c1 = getStart().getCardinality();
        int c2 = getStep().getCardinality();
        return Cardinality.multiply(c1, c2);
    }

    /**
     * Convert this expression to an equivalent XSLT pattern
     *
     * @param config the Saxon configuration
     * @param is30   true if this is XSLT 3.0
     * @return the equivalent pattern
     * @throws XPathException
     *          if conversion is not possible
     */
    @Override
    public Pattern toPattern(Configuration config, boolean is30) throws XPathException {
        Expression head = getLeadingSteps();
        Expression tail = getLastStep();
        if (head instanceof ItemChecker) {
            // No need to typecheck the context item
            ItemChecker checker = (ItemChecker) head;
            if (checker.getBaseExpression() instanceof ContextItemExpression) {
                return tail.toPattern(config, is30);
            }
        }

        Pattern tailPattern = tail.toPattern(config, is30);
        if (tailPattern instanceof NodeTestPattern) {
            if (tailPattern.getItemType() instanceof ErrorType) {
                return tailPattern;
            }
        } else if (tailPattern instanceof GeneralNodePattern) {
            return new GeneralNodePattern(this, (NodeTest)tailPattern.getItemType());
        }

        byte axis = AxisInfo.PARENT;
        Pattern headPattern = null;
        if (head instanceof RawSlashExpression) {
            RawSlashExpression start = (RawSlashExpression) head;
            if (start.getActionExpression() instanceof AxisExpression) {
                AxisExpression mid = (AxisExpression) start.getActionExpression();
                if (mid.getAxis() == AxisInfo.DESCENDANT_OR_SELF &&
                        (mid.getNodeTest() == null || mid.getNodeTest() instanceof AnyNodeTest)) {
                    axis = AxisInfo.ANCESTOR;
                    headPattern = start.getSelectExpression().toPattern(config, is30);
                }
            }
        }
        if (headPattern == null) {
            axis = PatternMaker.getAxisForPathStep(tail);
            headPattern = head.toPattern(config, is30);
        }
        return new AncestorQualifiedPattern(tailPattern, headPattern, axis);
    }

    /**
     * Is this expression the same as another expression?
     */

    public boolean equals(Object other) {
        if (!(other instanceof RawSlashExpression)) {
            return false;
        }
        RawSlashExpression p = (RawSlashExpression) other;
        return getStart().equals(p.getStart()) && getStep().equals(p.getStep());
    }

    /**
     * get HashCode for comparing two expressions
     */

    public int hashCode() {
        return "SlashExpression".hashCode() + getStart().hashCode() + getStep().hashCode();
    }

    /**
     * Iterate the path-expression in a given context
     *
     * @param context the evaluation context
     */

    /*@NotNull*/
    public SequenceIterator iterate(final XPathContext context) throws XPathException {

        // This class delivers the result of the path expression in unsorted order,
        // without removal of duplicates. If sorting and deduplication are needed,
        // this is achieved by wrapping the path expression in a DocumentSorter

        FocusIterator result = new FocusTrackingIterator(getStart().iterate(context));
        XPathContext context2 = context.newMinorContext();
        context2.setCurrentIterator(result);
        return new ContextMappingIterator<Item>(this, context2);
    }

    /**
     * Mapping function, from a node returned by the start iteration, to a sequence
     * returned by the child.
     */

    public SequenceIterator map(XPathContext context) throws XPathException {
        return getStep().iterate(context);
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    public void export(ExpressionPresenter destination) throws XPathException {
        destination.startElement("slash", this);
        getStart().export(destination);
        getStep().export(destination);
        destination.endElement();
    }

    /**
     * The toString() method for an expression attempts to give a representation of the expression
     * in an XPath-like form, but there is no guarantee that the syntax will actually be true XPath.
     * In the case of XSLT instructions, the toString() method gives an abstracted view of the syntax
     *
     * @return a representation of the expression as a string
     */

    public String toString() {
        return ExpressionTool.parenthesize(getStart()) + "/" + ExpressionTool.parenthesize(getStep());
    }

//    @Override
//    public String toShortString() {
//        return getStart().toShortString() + "/" +  getStep().toShortString();
//    }

    /**
     * Get the first step in this expression. A path expression A/B/C is represented as (A/B)/C, but
     * the first step is A
     *
     * @return the first step in the expression, after expanding any nested path expressions
     */

    public Expression getFirstStep() {
        if (getStart() instanceof RawSlashExpression) {
            return ((RawSlashExpression) getStart()).getFirstStep();
        } else {
            return getStart();
        }
    }


    /**
     * Get all steps after the first.
     * This is complicated by the fact that A/B/C is represented as ((A/B)/C; we are required
     * to return B/C
     *
     * @return a path expression containing all steps in this path expression other than the first,
     *         after expanding any nested path expressions
     */

    public Expression getRemainingSteps() {
        if (getStart() instanceof RawSlashExpression) {
            List<Expression> list = new ArrayList<Expression>(8);
            gatherSteps(list);
            Expression rem = rebuildSteps(list.subList(1, list.size()));
            ExpressionTool.copyLocationInfo(this, rem);
            return rem;
        } else {
            return getStep();
        }
    }

    /**
     * Flatten the path expression into a flat list of steps
     * @param list a list of expressions making up this path expression
     */

    private void gatherSteps(List<Expression> list) {
        if (getStart() instanceof RawSlashExpression) {
            ((RawSlashExpression)getStart()).gatherSteps(list);
        } else {
            list.add(getStart());
        }
        if (getStep() instanceof RawSlashExpression) {
            ((RawSlashExpression) getStep()).gatherSteps(list);
        } else {
            list.add(getStep());
        }
    }

    /**
     * Build a tree from a flat list of steps
     * @param list the list of steps
     * @return an Expression, generally a right-heavy binary tree
     */

    private Expression rebuildSteps(List<Expression> list) {
        if (list.size() == 1) {
            return list.get(0).copy(new RebindingMap());
        } else {
            return new RawSlashExpression(list.get(0).copy(new RebindingMap()), rebuildSteps(list.subList(1, list.size())));
        }
    }

    /**
     * Get the last step of the path expression
     *
     * @return the last step in the expression, after expanding any nested path expressions
     */

    public Expression getLastStep() {
        if (getStep() instanceof RawSlashExpression) {
            return ((RawSlashExpression) getStep()).getLastStep();
        } else {
            return getStep();
        }
    }

    /**
     * Get a path expression consisting of all steps except the last
     *
     * @return a path expression containing all steps in this path expression other than the last,
     *         after expanding any nested path expressions
     */

    public Expression getLeadingSteps() {
        if (getStep() instanceof RawSlashExpression) {
            List<Expression> list = new ArrayList<Expression>(8);
            gatherSteps(list);
            Expression rem = rebuildSteps(list.subList(0, list.size()-1));
            ExpressionTool.copyLocationInfo(this, rem);
            return rem;
        } else {
            return getStart();
        }
    }

    /**
     * Test whether a path expression is an absolute path - that is, a path whose first step selects a
     * document node
     *
     * @return true if the first step in this path expression selects a document node
     */

    public boolean isAbsolute() {
        Expression first = getFirstStep();
        if (first.getItemType().getPrimitiveType() == Type.DOCUMENT) {
            return true;
        }
        // This second test allows keys to be built. See XMark q9.
//        if (first instanceof AxisExpression && ((AxisExpression)first).getContextItemType().getPrimitiveType() == Type.DOCUMENT) {
//            return true;
//        };
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
        return "ForEach"; // sic
    }
}

