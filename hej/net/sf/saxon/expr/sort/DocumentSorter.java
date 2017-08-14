////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.pattern.Pattern;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.SequenceType;


/**
 * A DocumentSorter is an expression that sorts a sequence of nodes into
 * document order.
 */
public class DocumentSorter extends UnaryExpression {

    private ItemOrderComparer comparer;

    public DocumentSorter(Expression base) {
        super(base);
        int props = base.getSpecialProperties();
        if (((props & StaticProperty.CONTEXT_DOCUMENT_NODESET) != 0) ||
                (props & StaticProperty.SINGLE_DOCUMENT_NODESET) != 0) {
            comparer = LocalOrderComparer.getInstance();
        } else {
            comparer = GlobalOrderComparer.getInstance();
        }
    }

    public DocumentSorter(Expression base, boolean intraDocument) {
        super(base);
        if (intraDocument) {
            comparer = LocalOrderComparer.getInstance();
        } else {
            comparer = GlobalOrderComparer.getInstance();
        }
    }

    protected OperandRole getOperandRole() {
        return OperandRole.SAME_FOCUS_ACTION;
    }

    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     *
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     *         The name will always be in the form of a lexical XML QName, and should match the name used
     *         in explain() output displaying the expression.
     */

    public String getExpressionName() {
        return "docOrder";
    }

    public ItemOrderComparer getComparer() {
        return comparer;
    }

    /*@NotNull*/
    public Expression simplify() throws XPathException {
        Expression operand = getBaseExpression().simplify();
        if ((operand.getSpecialProperties() & StaticProperty.ORDERED_NODESET) != 0) {
            // this can happen as a result of further simplification
            return operand;
        }
        return this;
    }

    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        Expression e2 = super.typeCheck(visitor, contextInfo);
        if (e2 != this) {
            return e2;
        }
        RoleDiagnostic role = new RoleDiagnostic(RoleDiagnostic.DOCUMENT_ORDER, "", 0);
        Expression operand = visitor.getConfiguration().getTypeChecker(false).staticTypeCheck(
                getBaseExpression(), SequenceType.NODE_SEQUENCE, role, visitor);
        setBaseExpression(operand);
        return this;
    }

    /*@NotNull*/
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        getOperand().optimize(visitor, contextInfo);
        Expression operand = getBaseExpression();
        boolean tryHarder = operand.isStaticPropertiesKnown();
        while (true) {
            if ((operand.getSpecialProperties() & StaticProperty.ORDERED_NODESET) != 0) {
                // this can happen as a result of further simplification
                return operand;
            }
            if (!Cardinality.allowsMany(operand.getCardinality())) {
                return operand;
            }
            // Bug 3389: try to rewrite sort(conditionalSort($var/child::x) / child::y)
            // as conditionalSort($var, (child::x/child::y))
            if (operand instanceof SlashExpression) {
                SlashExpression slash = (SlashExpression)operand;
                if (slash.getLhsExpression() instanceof ConditionalSorter &&
                        (slash.getRhsExpression().getSpecialProperties() & StaticProperty.PEER_NODESET) != 0) {
                    ConditionalSorter c = (ConditionalSorter)slash.getLhsExpression();
                    DocumentSorter d = c.getDocumentSorter();
                    Expression condition = c.getCondition();
                    SlashExpression s = new SlashExpression(d.getBaseExpression(), slash.getRhsExpression());
                    return new ConditionalSorter(condition, new DocumentSorter(s));
                }
            }
            // Try once more after recomputing the static properties of the expression
            if (tryHarder) {
                operand.resetLocalStaticProperties();
                tryHarder = false;
            } else {
                break;
            }
        }
        if (operand instanceof SlashExpression && !visitor.isOptimizeForStreaming()) {
            return visitor.getConfiguration().obtainOptimizer().makeConditionalDocumentSorter(
                    this, (SlashExpression) operand);
        }
        return this;
    }

    /**
     * Return the net cost of evaluating this expression, excluding the cost of evaluating
     * its operands. We take the cost of evaluating a simple scalar comparison or arithmetic
     * expression as 1 (one).
     *
     * @return the intrinsic cost of this operation, excluding the costs of evaluating
     * the operands
     */
    @Override
    public int getNetCost() {
        return 30;
    }

    /**
     * Replace this expression by a simpler expression that delivers the results without regard
     * to order.
     *
     * @param retainAllNodes set to true if the result must contain exactly the same nodes as the
     *                       original; set to false if the result can eliminate (or introduce) duplicates.
     * @param forStreaming set to true if optimizing for streaming
     */
    @Override
    public Expression unordered(boolean retainAllNodes, boolean forStreaming) throws XPathException {
        Expression operand = getBaseExpression().unordered(retainAllNodes, forStreaming);
        if ((operand.getSpecialProperties() & StaticProperty.ORDERED_NODESET) != 0) {
            return operand;
        }
        if (!retainAllNodes) {
            return operand;
        } else if (operand instanceof SlashExpression) {
            // handle the common case of //section/head where it is safe to remove sorting, because
            // no duplicates need to be removed
            SlashExpression exp = (SlashExpression)operand;
            Expression a = exp.getSelectExpression();
            Expression b = exp.getActionExpression();
            a = ExpressionTool.unfilteredExpression(a, false);
            b = ExpressionTool.unfilteredExpression(b, false);
            if (a instanceof AxisExpression &&
                    (((AxisExpression)a).getAxis() == AxisInfo.DESCENDANT || ((AxisExpression)a).getAxis() == AxisInfo.DESCENDANT_OR_SELF) &&
                    b instanceof AxisExpression &&
                    ((AxisExpression)b).getAxis() == AxisInfo.CHILD) {
                return operand.unordered(retainAllNodes, false);
            }
        }
        setBaseExpression(operand);
        return this;
    }


    public int computeSpecialProperties() {
        return getBaseExpression().getSpecialProperties() | StaticProperty.ORDERED_NODESET;
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings
     */

    /*@NotNull*/
    public Expression copy(RebindingMap rebindings) {
        DocumentSorter ds = new DocumentSorter(getBaseExpression().copy(rebindings));
        ExpressionTool.copyLocationInfo(this, ds);
        return ds;
    }

    /**
     * Convert this expression to an equivalent XSLT pattern
     *
     * @param config the Saxon configuration
     * @param is30   true if this is XSLT 3.0
     * @return the equivalent pattern
     * @throws net.sf.saxon.trans.XPathException
     *          if conversion is not possible
     */
    @Override
    public Pattern toPattern(Configuration config, boolean is30) throws XPathException {
        return getBaseExpression().toPattern(config, is30);
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

    /*@NotNull*/
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        return new DocumentOrderIterator(getBaseExpression().iterate(context), comparer);
    }

    public boolean effectiveBooleanValue(XPathContext context) throws XPathException {
        return getBaseExpression().effectiveBooleanValue(context);
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("docOrder", this);
        out.emitAttribute("intra", comparer instanceof LocalOrderComparer ? "1" : "0");
        getBaseExpression().export(out);
        out.endElement();
    }


    /**
     * Get the (partial) name of a class that supports streaming of this kind of expression
     *
     * @return the partial name of a class that can be instantiated to provide streaming support in Saxon-EE,
     * or null if there is no such class
     */
    @Override
    public String getStreamerName() {
        return "DocumentSorterAdjunct";
    }
}

