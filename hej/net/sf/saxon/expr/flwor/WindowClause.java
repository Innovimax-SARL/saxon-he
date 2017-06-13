////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.flwor;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Operand;
import net.sf.saxon.expr.OperandRole;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.PathMap;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.z.IntHashMap;

import java.util.Iterator;
import java.util.List;

/**
 * Implements an XQuery 3.0 sliding or tumbling window clause within a FLWOR expression
 */
public class WindowClause extends Clause {

    private boolean sliding;
    private boolean includeUnclosedWindows = true;
    private Operand sequenceOp;
    private Operand startConditionOp;
    private Operand endConditionOp;
    private IntHashMap<LocalVariableBinding> windowVars = new IntHashMap<LocalVariableBinding>(10);

    public static final int WINDOW_VAR = 0;
    public static final int START_ITEM = 1;
    public static final int START_ITEM_POSITION = 2;
    public static final int START_PREVIOUS_ITEM = 3;
    public static final int START_NEXT_ITEM = 4;
    public static final int END_ITEM = 5;
    public static final int END_ITEM_POSITION = 6;
    public static final int END_PREVIOUS_ITEM = 7;
    public static final int END_NEXT_ITEM = 8;

    public WindowClause() {
    }

    @Override
    public int getClauseKey() {
        return WINDOW;
    }

    public void setIsSlidingWindow(boolean sliding) {
        this.sliding = sliding;
    }

    public boolean isSlidingWindow() {
        return sliding;
    }

    public boolean isTumblingWindow() {
        return !sliding;
    }

    public void setIncludeUnclosedWindows(boolean include) {
        this.includeUnclosedWindows = include;
    }

    public boolean isIncludeUnclosedWindows() {
        return includeUnclosedWindows;
    }

    public void initSequence(FLWORExpression flwor, Expression sequence) {
        sequenceOp = new Operand(flwor, sequence, OperandRole.INSPECT);
    }

    public void setSequence(Expression sequence) {
        sequenceOp.setChildExpression(sequence);
    }

    public Expression getSequence() {
        return sequenceOp.getChildExpression();
    }

    public void initStartCondition(FLWORExpression flwor, Expression startCondition) {
        startConditionOp = new Operand(flwor, startCondition, OperandRole.INSPECT);
    }

    public void setStartCondition(Expression startCondition) {
        startConditionOp.setChildExpression(startCondition);
    }

    public Expression getStartCondition() {
        return startConditionOp.getChildExpression();
    }

    public void initEndCondition(FLWORExpression flwor, Expression endCondition) {
        endConditionOp = new Operand(flwor, endCondition, OperandRole.INSPECT);
    }

    public void setEndCondition(Expression endCondition) {
        endConditionOp.setChildExpression(endCondition);
    }

    public Expression getEndCondition() {
        return endConditionOp == null ? null : endConditionOp.getChildExpression();
    }


    public void setVariableBinding(int role, LocalVariableBinding binding) throws XPathException {
        for (Iterator<LocalVariableBinding> iter = windowVars.valueIterator(); iter.hasNext(); ) {
            if (iter.next().getVariableQName().equals(binding.getVariableQName())) {
                throw new XPathException("Two variables in a window clause cannot have the same name (" +
                        binding.getVariableQName().getDisplayName() + ")", "XQST0103");
            }
        }
        windowVars.put(role, binding);
    }

    public LocalVariableBinding getVariableBinding(int role) {
        return windowVars.get(role);
    }

    @Override
    public Clause copy(FLWORExpression flwor, RebindingMap rebindings) {
        WindowClause wc = new WindowClause();
        wc.setLocation(getLocation());
        wc.setPackageData(getPackageData());
        wc.sliding = sliding;
        wc.includeUnclosedWindows = includeUnclosedWindows;
        wc.initSequence(flwor, getSequence().copy(rebindings));
        wc.initStartCondition(flwor, getStartCondition().copy(rebindings));
        wc.initEndCondition(flwor, getEndCondition().copy(rebindings));
        wc.windowVars = windowVars;
        return wc;
    }

    /**
     * Get a pull-mode tuple stream that implements the functionality of this clause, taking its
     * input from another tuple stream which this clause modifies
     *
     * @param base    the input tuple stream
     * @param context the dynamic evaluation context
     * @return the output tuple stream
     */
    @Override
    public TuplePull getPullStream(TuplePull base, XPathContext context) {
        return new WindowClausePull(base, this, context);
    }

    /**
     * Get a push-mode tuple stream that implements the functionality of this clause, supplying its
     * output to another tuple stream
     *
     * @param destination the output tuple stream
     * @param context     the dynamic evaluation context
     * @return the push tuple stream that implements the functionality of this clause of the FLWOR
     *         expression
     */
    @Override
    public TuplePush getPushStream(TuplePush destination, XPathContext context) {
        return new WindowClausePush(destination, this);
    }

    /**
     * Process the subexpressions of this clause
     *
     * @param processor the expression processor used to process the subexpressions
     */
    @Override
    public void processOperands(OperandProcessor processor) throws XPathException {
        processor.processOperand(sequenceOp);
        processor.processOperand(startConditionOp);
        if (endConditionOp != null) {
            processor.processOperand(endConditionOp);
        }
    }

    @Override
    public void addToPathMap(PathMap pathMap, PathMap.PathMapNodeSet pathMapNodeSet) {
        throw new UnsupportedOperationException("Cannot use document projection with windowing");
    }

    /**
     * Get the variables bound by this clause
     *
     * @return the variable bindings
     */
    @Override
    public LocalVariableBinding[] getRangeVariables() {
        LocalVariableBinding[] vars = new LocalVariableBinding[windowVars.size()];
        int i = 0;
        Iterator<LocalVariableBinding> iter = windowVars.valueIterator();
        while (iter.hasNext()) {
            vars[i++] = iter.next();
        }
        return vars;
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     *
     * @param out the expression presenter used to display the structure
     */
    @Override
    public void explain(ExpressionPresenter out) throws XPathException {
        out.startElement(isSlidingWindow() ? "slidingWindow" : "tumblingWindow");
        out.startSubsidiaryElement("select");
        getSequence().export(out);
        out.endSubsidiaryElement();
        out.startSubsidiaryElement("start");
        getStartCondition().export(out);
        out.endSubsidiaryElement();
        if (endConditionOp != null) {
            out.startSubsidiaryElement("end");
            getEndCondition().export(out);
            out.endSubsidiaryElement();
        }
        out.endElement();
    }

    /**
     * Determine whether the current item is the start of a new window
     *
     * @param previous the item before the current item (null if the current item is the first)
     * @param current  the current item
     * @param next     the item after the current item (null if the current item is the last)
     * @param position the position of the current item in the input sequence
     * @param context  the dynamic evaluation context
     * @return true if the current item forms the start of a new window
     * @throws XPathException
     */

    protected boolean matchesStart(Item previous, Item current, Item next, int position, XPathContext context) throws XPathException {
        WindowClause clause = this;
        LocalVariableBinding binding;
        binding = clause.getVariableBinding(WindowClause.START_ITEM);
        if (binding != null) {
            context.setLocalVariable(binding.getLocalSlotNumber(), current);
        }
        binding = clause.getVariableBinding(WindowClause.START_ITEM_POSITION);
        if (binding != null) {
            context.setLocalVariable(binding.getLocalSlotNumber(), new Int64Value(position));
        }
        binding = clause.getVariableBinding(WindowClause.START_NEXT_ITEM);
        if (binding != null) {
            context.setLocalVariable(binding.getLocalSlotNumber(), makeValue(next));
        }
        binding = clause.getVariableBinding(WindowClause.START_PREVIOUS_ITEM);
        if (binding != null) {
            context.setLocalVariable(binding.getLocalSlotNumber(), makeValue(previous));
        }
        return clause.getStartCondition().effectiveBooleanValue(context);
    }

    /**
     * Determine whether the current item is the last item in a window
     *
     * @param window   the window in question
     * @param previous the item before the current item (null if the current item is the first)
     * @param current  the current item
     * @param next     the item after the current item (null if the current item is the last)
     * @param position the position of the current item in the input sequence
     * @param context  the dynamic evaluation context
     * @return true if the current item is the last item in the specified window
     * @throws XPathException
     */

    protected boolean matchesEnd(Window window, Item previous, Item current, Item next, int position, XPathContext context) throws XPathException {
        WindowClause clause = this;
        LocalVariableBinding binding;
        binding = clause.getVariableBinding(WindowClause.START_ITEM);
        if (binding != null) {
            context.setLocalVariable(binding.getLocalSlotNumber(), window.startItem);
        }
        binding = clause.getVariableBinding(WindowClause.START_ITEM_POSITION);
        if (binding != null) {
            context.setLocalVariable(binding.getLocalSlotNumber(), new Int64Value(window.startPosition));
        }
        binding = clause.getVariableBinding(WindowClause.START_NEXT_ITEM);
        if (binding != null) {
            context.setLocalVariable(binding.getLocalSlotNumber(), makeValue(window.startNextItem));
        }
        binding = clause.getVariableBinding(WindowClause.START_PREVIOUS_ITEM);
        if (binding != null) {
            context.setLocalVariable(binding.getLocalSlotNumber(), makeValue(window.startPreviousItem));
        }
        binding = clause.getVariableBinding(WindowClause.END_ITEM);
        if (binding != null) {
            context.setLocalVariable(binding.getLocalSlotNumber(), current);
        }
        binding = clause.getVariableBinding(WindowClause.END_ITEM_POSITION);
        if (binding != null) {
            context.setLocalVariable(binding.getLocalSlotNumber(), new Int64Value(position));
        }
        binding = clause.getVariableBinding(WindowClause.END_NEXT_ITEM);
        if (binding != null) {
            context.setLocalVariable(binding.getLocalSlotNumber(), makeValue(next));
        }
        binding = clause.getVariableBinding(WindowClause.END_PREVIOUS_ITEM);
        if (binding != null) {
            context.setLocalVariable(binding.getLocalSlotNumber(), makeValue(previous));
        }
        return clause.getEndCondition().effectiveBooleanValue(context);
    }

    protected static Sequence makeValue(/*@Nullable*/ Item item) {
        if (item == null) {
            return EmptySequence.getInstance();
        } else {
            return item;
        }
    }

    /**
     * Information about a window: the items making up the window, as well as the variables relating to the
     * start and end of the window, and the status of the winoow in relation to the processing of the current
     * input sequence.
     */

    protected static class Window {
        public Item startItem;
        public int startPosition;
        public Item startPreviousItem;
        public Item startNextItem;
        public Item endItem;
        public int endPosition = 0;
        public Item endPreviousItem;
        public Item endNextItem;
        public List<Item> contents;
        public boolean isDespatched = false;

        /**
         * Ask whether we have found the last item in the window
         *
         * @return true if we have found the last item; false if we are still looking for it.
         */
        public boolean isFinished() {
            return endPosition > 0;
        }

        /**
         * Ask whether the tuple corresponding to this window has been despatched to the output tuple
         * stream. This does not always happen immediately the end item is determined, because tuples
         * corresponding to the various windows must be despached in order of their start items.
         *
         * @return true if the tuple corresponding to this window has been despatched.
         */
        public boolean isDespatched() {
            return isDespatched;
        }
    }
}

// Copyright (c) 2011 Saxonica Limited.