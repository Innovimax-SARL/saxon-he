////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import com.saxonica.ee.stream.Streamability;
import com.saxonica.ee.stream.Sweep;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.functions.Current;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.ManualIterator;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.UType;

/**
 * Class for handling patterns with simple non-positional boolean predicates
 */
public class BasePatternWithPredicate extends Pattern implements PatternWithPredicate {

    private Pattern basePattern;
    private Expression predicate;

    public BasePatternWithPredicate(Pattern basePattern, Expression predicate) {
        this.basePattern = basePattern;
        this.predicate = predicate;
        adoptChildExpression(basePattern);
        adoptChildExpression(predicate);
    }

    public Expression getPredicate() {
        return predicate;
    }
    public Pattern getBasePattern() {
        return basePattern;
    }

    /**
     * Replace any calls on current() by a variable reference bound to the supplied binding
     */
    @Override
    public void bindCurrent(LocalBinding binding) {
        if (predicate.isCallOn(Current.class)) {
            predicate = new LocalVariableReference(binding);
        } else if (ExpressionTool.callsFunction(predicate, Current.FN_CURRENT, false)) {
            replaceCurrent(predicate, binding);
        }
        basePattern.bindCurrent(binding);
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
        return operandList(
                new Operand(this, basePattern, OperandRole.ATOMIC_SEQUENCE),
                new Operand(this, predicate, OperandRole.FOCUS_CONTROLLED_ACTION));
    }

    /**
     * Allocate slots to any variables used within the pattern
     *
     * @param slotManager holds details of the allocated slots
     * @param nextFree    the next slot that is free to be allocated @return the next slot that is free to be allocated
     */

    public int allocateSlots(SlotManager slotManager, int nextFree) {
        int n = ExpressionTool.allocateSlots(predicate, nextFree, slotManager);
        return basePattern.allocateSlots(slotManager, n);
    }

    /**
     * Determine whether this Pattern matches the given Node.
     *
     * @param item    The NodeInfo representing the Element or other node to be tested against the Pattern
     * @param context The dynamic context. Only relevant if the pattern
     *                uses variables, or contains calls on functions such as document() or key().
     * @return true if the node matches the Pattern, false otherwise
     */
    @Override
    public boolean matches(Item item, XPathContext context) throws XPathException {
        if (!basePattern.matches(item, context)) {
            return false;
        }
        return matchesPredicate(item, context);
    }

    private boolean matchesPredicate(Item item, XPathContext context) throws XPathException {
        XPathContext c2 = context.newMinorContext();
        ManualIterator si = new ManualIterator(item);
        c2.setCurrentIterator(si);
        try {
            return predicate.effectiveBooleanValue(c2);
        } catch (XPathException.Circularity e) {
            throw e;
        } catch (XPathException ex) {
            if ("XTDE0640".equals(ex.getErrorCodeLocalPart())) {
                // Treat circularity error as fatal (test error213)
                throw ex;
            }
            XPathException err = new XPathException("An error occurred matching pattern {" + toString() + "}: ", ex);
            err.setXPathContext(c2);
            err.setErrorCodeQName(ex.getErrorCodeQName());
            err.setLocation(getLocation());
            c2.getController().recoverableError(err);
            return false;
        }
    }

    @Override
    public boolean matchesBeneathAnchor(NodeInfo node, NodeInfo anchor, XPathContext context) throws XPathException {
        return basePattern.matchesBeneathAnchor(node, anchor, context) &&
                matchesPredicate(node, context);
    }

    /**
     * Get a UType indicating which kinds of items this Pattern can match.
     *
     * @return a UType indicating all the primitive types of item that the pattern can match.
     */
    @Override
    public UType getUType() {
        return basePattern.getUType();
    }

    /**
     * Determine the name fingerprint of nodes to which this pattern applies. Used for
     * optimisation.
     *
     * @return A fingerprint that the nodes must match, or -1 if it can match multiple fingerprints,
     * or it if matches atomic values
     */
    @Override
    public int getFingerprint() {
        return basePattern.getFingerprint();
    }

    /**
     * Get an ItemType that all the items matching this pattern must satisfy
     *
     * @return an ItemType, as specific as possible, which all the matching items satisfy
     */
    /*@Nullable*/
    @Override
    public ItemType getItemType() {
        return basePattern.getItemType();
    }

    /**
     * Get the dependencies of the pattern. The only possible dependency for a pattern is
     * on local variables. This is analyzed in those patterns where local variables may appear.
     *
     * @return the dependencies, as a bit-significant mask
     */
    @Override
    public int getDependencies() {
        return predicate.getDependencies();
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
        basePattern = basePattern.typeCheck(visitor, contextItemType);
        ContextItemStaticInfo cit = visitor.getConfiguration().makeContextItemStaticInfo(basePattern.getItemType(), false);
        predicate = predicate.typeCheck(visitor, cit);
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
        basePattern = basePattern.optimize(visitor, contextInfo);
        ContextItemStaticInfo cit = visitor.getConfiguration().makeContextItemStaticInfo(basePattern.getItemType(), false);
        predicate = predicate.optimize(visitor, cit);
        predicate = visitor.getConfiguration().obtainOptimizer().eliminateCommonSubexpressions(predicate);
        return this;
    }

    /**
     * Convert the pattern to a typed pattern, in which an element name is treated as
     * schema-element(N)
     *
     * @param val either "strict" or "lax" depending on the value of xsl:mode/@typed
     * @return either the original pattern unchanged, or a new pattern as the result of the
     * conversion
     * @throws net.sf.saxon.trans.XPathException if the pattern cannot be converted
     */
    @Override
    public Pattern convertToTypedPattern(String val) throws XPathException {
        Pattern b2 = basePattern.convertToTypedPattern(val);
        if (b2 == basePattern) {
            return this;
        } else {
            return new BasePatternWithPredicate(b2, predicate);
        }
    }

    //#ifdefined STREAM
    @Override
    public boolean isMotionless() {
        ContextItemStaticInfo cio = getConfiguration().makeContextItemStaticInfo(getItemType(), false);
        cio.setContextPostureStriding();
        Streamability.getStreamability(predicate, cio, null);
        return basePattern.isMotionless() &&
                Streamability.getSweep(predicate) == Sweep.MOTIONLESS;
    }

    //#endif

    /**
     * Display the pattern for diagnostics
     */

    public String toString() {
        return basePattern.toString() + "[" + predicate.toString() + "]";
    }

    @Override
    public String toShortString() {
        return basePattern.toShortString() + "[" + predicate.toShortString() + "]";
    }


    /**
     * Copy a pattern. This makes a deep copy.
     *
     * @return the copy of the original pattern
     * @param rebindings
     */

    /*@NotNull*/
    public Pattern copy(RebindingMap rebindings) {
        BasePatternWithPredicate n = new BasePatternWithPredicate(basePattern.copy(rebindings), predicate.copy(rebindings));
        ExpressionTool.copyLocationInfo(this, n);
        return n;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof BasePatternWithPredicate &&
                ((BasePatternWithPredicate)obj).basePattern.equals(basePattern) &&
                ((BasePatternWithPredicate) obj).predicate.equals(predicate);
    }

    @Override
    public int hashCode() {
        return basePattern.hashCode() ^ predicate.hashCode();
    }

    public void export(ExpressionPresenter presenter) throws XPathException {
        presenter.startElement("p.withPredicate");
        basePattern.export(presenter);
        predicate.export(presenter);
        presenter.endElement();
    }

}

