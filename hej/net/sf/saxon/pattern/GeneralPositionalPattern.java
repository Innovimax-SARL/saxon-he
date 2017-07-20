////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import com.saxonica.ee.stream.Streamability;
import com.saxonica.ee.stream.Sweep;
import net.sf.saxon.Configuration;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.ManualIterator;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ErrorType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.UType;
import net.sf.saxon.value.NumericValue;

/**
 * A GeneralPositionalPattern is a pattern of the form A[P] where A is an axis expression using the child axis
 * and P is an expression that depends on the position. When this kind of pattern is used for matching streamed nodes,
 * it relies on the histogram data of preceding siblings maintained as part of a
 * {@link com.saxonica.ee.stream.om.FleetingParentNode}
 * <p/>
 * This class handles cases where the predicate P is arbitrarily complex. Simple comparisons of position() against
 * an integer value are handled by the class SimplePositionalPattern.
 */
public class GeneralPositionalPattern extends Pattern {

    private NodeTest nodeTest;
    private Expression positionExpr;
    private boolean usesPosition = true;

    /**
     * Create a GeneralPositionalPattern
     *
     * @param base         the base expression (to be matched independently of position)
     * @param positionExpr the positional filter which matches only if the position of the node is correct
     */

    public GeneralPositionalPattern(NodeTest base, Expression positionExpr) {
        this.nodeTest = base;
        this.positionExpr = positionExpr;
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
        return new Operand(this, positionExpr, OperandRole.FOCUS_CONTROLLED_ACTION);
    }

    /**
     * Get the filter assocated with the pattern
     *
     * @return the filter predicate
     */

    public Expression getPositionExpr() {
        return positionExpr;
    }

    /**
     * Get the base pattern
     *
     * @return the base pattern before filtering
     */

    public NodeTest getNodeTest() {
        return nodeTest;
    }

    /**
     * Simplify the pattern: perform any context-independent optimisations
     *
     */

    public Pattern simplify() throws XPathException {
        positionExpr = positionExpr.simplify();
        return this;
    }

    /**
     * Type-check the pattern, performing any type-dependent optimizations.
     *
     * @param visitor         an expression visitor
     * @param contextItemType the type of the context item at the point where the pattern appears
     * @return the optimised Pattern
     */

    public Pattern typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {

        // analyze each component of the pattern
        ContextItemStaticInfo cit = visitor.getConfiguration().makeContextItemStaticInfo(getItemType(), false);

        positionExpr = positionExpr.typeCheck(visitor, cit);
        positionExpr = ExpressionTool.unsortedIfHomogeneous(positionExpr, false);


        return this;

    }

    /**
     * Perform optimisation of an expression and its subexpressions. This is the third and final
     * phase of static optimization.
     * <p/>
     * <p>This method is called after all references to functions and variables have been resolved
     * to the declaration of the function or variable, and after all type checking has been done.</p>
     *
     * @param visitor     an expression visitor
     * @param contextInfo the static type of "." at the point where this expression is invoked.
     *                    The parameter is set to null if it is known statically that the context item will be undefined.
     *                    If the type of the context item is not known statically, the argument is set to
     *                    {@link Type#ITEM_TYPE}
     * @return the original expression, rewritten if appropriate to optimize execution
     * @throws XPathException if an error is discovered during this phase
     *                        (typically a type error)
     */
    @Override
    public Pattern optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        Configuration config = visitor.getConfiguration();
        ContextItemStaticInfo cit = config.makeContextItemStaticInfo(getItemType(), false);
        positionExpr = positionExpr.optimize(visitor, cit);

        if (Literal.isConstantBoolean(positionExpr, true)) {
            return new NodeTestPattern(nodeTest);
        } else if (Literal.isConstantBoolean(positionExpr, false)) {
            // if a filter is constant false, the pattern doesn't match anything
            return new NodeTestPattern(ErrorType.getInstance());
        }

        if ((positionExpr.getDependencies() & StaticProperty.DEPENDS_ON_POSITION) == 0) {
            usesPosition = false;
        }

        // See if the expression is now known to be non-positional (see bugs 1908, 1992, test mode-0011)
        if (!FilterExpression.isPositionalFilter(positionExpr, config.getTypeHierarchy())) {
            byte axis = AxisInfo.CHILD;
            if (nodeTest.getPrimitiveType() == Type.ATTRIBUTE) {
                axis = AxisInfo.ATTRIBUTE;
            } else if (nodeTest.getPrimitiveType() == Type.NAMESPACE) {
                axis = AxisInfo.NAMESPACE;
            }
            AxisExpression ae = new AxisExpression(axis, nodeTest);
            FilterExpression fe = new FilterExpression(ae, positionExpr);
            return PatternMaker.fromExpression(fe, config, true)
                .typeCheck(visitor, contextInfo);
        }
        return this;
    }

    /**
     * Get the dependencies of the pattern. The only possible dependency for a pattern is
     * on local variables. This is analyzed in those patterns where local variables may appear.
     */

    public int getDependencies() {
        // the only dependency that's interesting is a dependency on local variables
        return positionExpr.getDependencies() & StaticProperty.DEPENDS_ON_LOCAL_VARIABLES;
    }

    /**
     * Allocate slots to any variables used within the pattern
     *
     * @param slotManager manages allocation of slots in a stack frame
     * @param nextFree    the next slot that is free to be allocated @return the next slot that is free to be allocated
     */

    public int allocateSlots(SlotManager slotManager, int nextFree) {
        return ExpressionTool.allocateSlots(positionExpr, nextFree, slotManager);
    }

    /**
     * Determine whether the pattern matches a given item.
     *
     * @param item the item to be tested
     * @return true if the pattern matches, else false
     */

    public boolean matches(Item item, XPathContext context) throws XPathException {
        return item instanceof NodeInfo && matchesBeneathAnchor((NodeInfo) item, null, context);
    }

    /**
     * Determine whether this pattern matches a given Node within the subtree rooted at a given
     * anchor node. This method is used when the pattern is used for streaming.
     *
     * @param node    The NodeInfo representing the Element or other node to be tested against the Pattern
     * @param anchor  The anchor node, which must match any AnchorPattern subpattern
     * @param context The dynamic context. Only relevant if the pattern
     *                uses variables, or contains calls on functions such as document() or key().
     * @return true if the node matches the Pattern, false otherwise
     */

    public boolean matchesBeneathAnchor(NodeInfo node, NodeInfo anchor, XPathContext context) throws XPathException {
        return internalMatches(node, anchor, context);
    }

    /**
     * Test whether the pattern matches, but without changing the current() node
     */

    private boolean internalMatches(NodeInfo node, NodeInfo anchor, XPathContext context) throws XPathException {
        // System.err.println("Matching node type and fingerprint");
        if (!nodeTest.matchesNode(node)) {
            return false;
        }

        XPathContext c2 = context.newMinorContext();
        ManualIterator iter = new ManualIterator(node);
        c2.setCurrentIterator(iter);

        try {
            XPathContext c = c2;
            if (usesPosition) {
                ManualIterator man = new ManualIterator(node, getActualPosition(node, Integer.MAX_VALUE));
                XPathContext c3 = c2.newMinorContext();
                c3.setCurrentIterator(man);
                c = c3;
            }
            Item predicate = positionExpr.evaluateItem(c);
            if (predicate instanceof NumericValue) {
                NumericValue position = (NumericValue) positionExpr.evaluateItem(context);
                int requiredPos = position.asSubscript();
                return requiredPos != -1 && getActualPosition(node, requiredPos) == requiredPos;
            } else {
                return ExpressionTool.effectiveBooleanValue(predicate);
            }

        } catch (XPathException.Circularity e) {
            throw e;
        } catch (XPathException e) {
            if ("XTDE0640".equals(e.getErrorCodeLocalPart())) {
                // Treat circularity error as fatal (test error213)
                throw e;
            }
            XPathException err = new XPathException("An error occurred matching pattern {" + toString() + "}: ", e);
            err.setXPathContext(c2);
            err.setErrorCodeQName(e.getErrorCodeQName());
            err.setLocation(getLocation());
            c2.getController().recoverableError(err);
            return false;
        }
    }

    private int getActualPosition(NodeInfo node, int max) {
        return Navigator.getSiblingPosition(node, nodeTest, max);
    }


    /**
     * Get a UType indicating which kinds of items this Pattern can match.
     *
     * @return a UType indicating all the primitive types of item that the pattern can match.
     */
    @Override
    public UType getUType() {
        return nodeTest.getUType();
    }

    /**
     * Determine the fingerprint of nodes to which this pattern applies.
     * Used for optimisation.
     *
     * @return the fingerprint of nodes matched by this pattern.
     */

    public int getFingerprint() {
        return nodeTest.getFingerprint();
    }

    /**
     * Get an ItemType that all the nodes matching this pattern must satisfy
     */

    public ItemType getItemType() {
        return nodeTest;
    }

    /**
     * Determine whether this pattern is the same as another pattern
     *
     * @param other the other object
     */

    public boolean equals(Object other) {
        if (other instanceof GeneralPositionalPattern) {
            GeneralPositionalPattern fp = (GeneralPositionalPattern) other;
            return nodeTest.equals(fp.nodeTest) && positionExpr.equals(fp.positionExpr);
        } else {
            return false;
        }
    }

    /**
     * hashcode supporting equals()
     */

    public int hashCode() {
        return nodeTest.hashCode() ^ positionExpr.hashCode();
    }

//#ifdefined STREAM

    /**
     * Test whether a pattern is motionless, that is, whether it can be evaluated against a node
     * without repositioning the input stream. This is a necessary condition for patterns used
     * as the match pattern of a streamed template rule.
     *
     * @return true if the pattern is motionless, that is, if it can be evaluated against a streamed
     * node without changing the position in the streamed input file
     */

    public boolean isMotionless() {
        ContextItemStaticInfo csi = getConfiguration().makeContextItemStaticInfo(getItemType(), false);
        csi.setContextPostureStriding();
        Streamability.getStreamability(positionExpr, csi, null);
        return Streamability.getSweep(positionExpr) == Sweep.MOTIONLESS;
    }
//#endif

    /**
     * Copy a pattern. This makes a deep copy.
     *
     * @return the copy of the original pattern
     * @param rebindings
     */

    /*@NotNull*/
    public Pattern copy(RebindingMap rebindings) {
        GeneralPositionalPattern n = new GeneralPositionalPattern(nodeTest.copy(), positionExpr.copy(rebindings));
        ExpressionTool.copyLocationInfo(this, n);
        return n;
    }

    /**
     * Get a string representation of the pattern. This will be in a form similar to the
     * original pattern text, but not necessarily identical. It is not guaranteed to be
     * in legal pattern syntax.
     */
    @Override
    public String toString() {
        return nodeTest.toString() + "[" + positionExpr.toString() + "]";
    }

    public void export(ExpressionPresenter presenter) throws XPathException {
        presenter.startElement("p.genPos");
        presenter.emitAttribute("type", nodeTest.toString());
        if ("JS".equals(presenter.getOption("target"))) {
            try {
                presenter.emitAttribute("jsTest", nodeTest.generateJavaScriptItemTypeTest(AnyItemType.getInstance()));
            } catch (XPathException e) {
                e.maybeSetLocation(getLocation());
                throw e;
            }
        }
        positionExpr.export(presenter);
        presenter.endElement();
    }

    /*
    * Optimization
    * Note that any replacement by a BooleanExpressionPattern based on the positionExpr
    * REQUIRES that the position has been set appropriately in the context for its evaluation.
    * This is NOT the case in generalized {@link net.sf.saxon.trans.Mode.getRule()},
    * but is under {@link com.saxonica.ee.optim.ModeEE.getRule()}
    * This should be checked.
     *//*
    public Pattern optimizeForName(int i) {
        Object o = nodeTest.optimizeForName(i);
        if (o == null) {
            return null;
        } else {
            return optimizePosition();
        }
    }

    public Pattern optimizeForType(int i) {
        Object o = nodeTest.optimizeForType(i);
        if (o == null) {
            return null;
        } else {
            return optimizePosition();
        }
    }

    private Pattern optimizePosition()  {
        Expression pe = makePosition(positionExpr, nodeTest);
        BooleanExpressionPattern p = new BooleanExpressionPattern(pe);
        ExpressionTool.copyLocationInfo(this, p);
        //p.setPackageData(getPackageData());
        return p;
    }

    public Pattern applyAxis(byte axis) {
        Expression pe = makePosition(positionExpr, nodeTest);
        FilterExpression filter = new FilterExpression(new AxisExpression(axis, nodeTest), pe);
        BooleanExpressionPattern p = new BooleanExpressionPattern(filter);
        ExpressionTool.copyLocationInfo(this, p);
        //p.setPackageData(getPackageData());
        return p;
    }

    public static Expression makePosition(Expression expr, NodeTest nodeTest) {
        Expression n = expr;
        RetainedStaticContext rsc = expr.getRetainedStaticContext();
        if (expr instanceof Literal) {
            GroundedValue value = ((Literal) expr).getValue();
            try {
                if (value instanceof IntegerValue) {
                    Expression operands[] = new Expression[1];
                    operands[0] = new AxisExpression(AxisInfo.PRECEDING_SIBLING, nodeTest);
                    Expression position = SystemFunction.makeCall("count", rsc, operands);
                    n = new CompareToIntegerConstant(position, Token.FEQ,
                            ((IntegerValue)value).longValue() - 1);
                    return n;
                }

                Expression position = SystemFunction.makeCall("position", rsc);
                n = new CompareToIntegerConstant(position, Token.FEQ,
                        ((IntegerValue) ((Literal) expr).getValue()).longValue());
            } catch (XPathException e) {
                e.printStackTrace();
            }
        } else if (expr instanceof CompareToIntegerConstant) {
            CompareToIntegerConstant c = (CompareToIntegerConstant)expr;
            Operand op1 = expr.operands().iterator().next();
            Expression e1 = op1.getChildExpression();
            if (e1.isCallOn(PositionAndLast.class)) {
                Expression position = SystemFunction.makeCall("count",
                    rsc, new AxisExpression(AxisInfo.PRECEDING_SIBLING, nodeTest));
                n = new CompareToIntegerConstant(position, c.getComparisonOperator(),
                        c.getComparand() - 1);
                return n;
            }
        }
        return n;
    }*/

}
// Copyright (c) 2012 Saxonica Limited