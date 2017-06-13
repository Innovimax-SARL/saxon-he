////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.Controller;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.lib.TraceListener;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NamespaceResolver;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trace.InstructionInfo;
import net.sf.saxon.trace.LocationKind;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;

import java.util.HashMap;
import java.util.Iterator;

/**
 * A wrapper expression used to trace expressions in XPath and XQuery.
 */

public class TraceExpression extends Instruction implements InstructionInfo {

    private Operand baseOp;
    private StructuredQName objectName;
    private int constructType;
    /*@Nullable*/ private NamespaceResolver namespaceResolver = null;
    private HashMap<String, Object> properties = new HashMap<String, Object>(10);

    /**
     * Create a trace expression that traces execution of a given child expression
     *
     * @param child the expression to be traced. This will be available to the TraceListener
     *              as the value of the "expression" property of the InstructionInfo.
     */
    public TraceExpression(Expression child) {
        baseOp = new Operand(this, child, OperandRole.SAME_FOCUS_ACTION);
        setProperty("expression", child);
    }

    public Expression getChild() {
        return baseOp.getChildExpression();
    }

    public void setChild(Expression child) {
        baseOp.setChildExpression(child);
    }

    @Override
    public Iterable<Operand> operands() {
        return baseOp;
    }

    /**
     * Set the type of construct. This will generally be a constant
     * in class {@link LocationKind}
     *
     * @param type an integer code for the type of construct being traced
     */

    public void setConstructType(int type) {
        constructType = type;
    }

    /**
     * Get the construct type. This will generally be a constant
     * in class {@link LocationKind}
     */
    public int getConstructType() {
        return constructType;
    }

    /**
     * Set the namespace context for the instruction being traced. This is needed if the
     * tracelistener wants to evaluate XPath expressions in the context of the current instruction
     *
     * @param resolver The namespace resolver, or null if none is needed
     */

    public void setNamespaceResolver(/*@Nullable*/ NamespaceResolver resolver) {
        namespaceResolver = resolver;
    }

    /**
     * Get the namespace resolver to supply the namespace context of the instruction
     * that is being traced
     *
     * @return the namespace resolver, or null if none is in use
     */

    /*@Nullable*/
    public NamespaceResolver getNamespaceResolver() {
        return namespaceResolver;
    }

    /**
     * Set a name identifying the object of the expression, for example a function name, template name,
     * variable name, key name, element name, etc. This is used only where the name is known statically.
     *
     * @param qName the name of the object, or null if not applicable
     */

    public void setObjectName(StructuredQName qName) {
        objectName = qName;
    }

    /**
     * Get a name identifying the object of the expression, for example a function name, template name,
     * variable name, key name, element name, etc. This is used only where the name is known statically.
     *
     * @return the name of the object, or null if not applicable
     */

    public StructuredQName getObjectName() {
        return objectName;
    }

    /**
     * Set a named property of the instruction/expression
     *
     * @param name  the name of the property
     * @param value the value of the property
     */

    public void setProperty(String name, Object value) {
        properties.put(name, value);
    }

    /**
     * Get a named property of the instruction/expression
     *
     * @param name the name of the property
     * @return the value of the property
     */

    public Object getProperty(String name) {
        return properties.get(name);
    }

    /**
     * Get an iterator over all the properties available. The values returned by the iterator
     * will be of type String, and each string can be supplied as input to the getProperty()
     * method to retrieve the value of the property.
     */

    public Iterator<String> getProperties() {
        return properties.keySet().iterator();
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
        return "trace";
    }

    /**
     * Get the InstructionInfo details about the construct. This is to satisfy the InstructionInfoProvider
     * interface.
     *
     * @return the instruction details
     */

    public InstructionInfo getInstructionInfo() {
        return this;
    }

    /*@NotNull*/
    public Expression copy(RebindingMap rebindings) {
        TraceExpression t = new TraceExpression(getChild().copy(rebindings));
        t.setLocation(getLocation());   // Bug 3034
        t.objectName = objectName;
        t.namespaceResolver = namespaceResolver;
        t.constructType = constructType;
        return t;
    }

    /**
     * Determine whether this is an updating expression as defined in the XQuery update specification
     *
     * @return true if this is an updating expression
     */

    @Override
    public boolean isUpdatingExpression() {
        return getChild().isUpdatingExpression();
    }

    /**
     * Determine whether this is a vacuous expression as defined in the XQuery update specification
     *
     * @return true if this expression is vacuous
     */

    @Override
    public boolean isVacuousExpression() {
        return getChild().isVacuousExpression();
    }

    /**
     * Check to ensure that this expression does not contain any inappropriate updating subexpressions.
     * This check is overridden for those expressions that permit updating subexpressions.
     *
     * @throws net.sf.saxon.trans.XPathException
     *          if the expression has a non-permitted updating subexpression
     */

    @Override
    public void checkForUpdatingSubexpressions() throws XPathException {
        getChild().checkForUpdatingSubexpressions();
    }

    public int getImplementationMethod() {
        return getChild().getImplementationMethod();
    }

    /**
     * Execute this instruction, with the possibility of returning tail calls if there are any.
     * This outputs the trace information via the registered TraceListener,
     * and invokes the instruction being traced.
     *
     * @param context the dynamic execution context
     * @return either null, or a tail call that the caller must invoke on return
     * @throws net.sf.saxon.trans.XPathException
     *
     */
    public TailCall processLeavingTail(XPathContext context) throws XPathException {
        Controller controller = context.getController();
        assert controller != null;
        TraceListener listener = controller.getTraceListener();
        if (controller.isTracing()) {
            assert listener != null;
            listener.enter(getInstructionInfo(), context);
        }
        // Don't attempt tail call optimization when tracing, the results are too confusing
        getChild().process(context);
        if (controller.isTracing()) {
            assert listener != null;
            listener.leave(getInstructionInfo());
        }
        return null;
    }

    /**
     * Get the item type of the items returned by evaluating this instruction
     *
     * @return the static item type of the instruction
     */

    /*@NotNull*/
    public ItemType getItemType() {
        return getChild().getItemType();
    }

    /**
     * Determine the static cardinality of the expression. This establishes how many items
     * there will be in the result of the expression, at compile time (i.e., without
     * actually evaluating the result.
     *
     * @return one of the values Cardinality.ONE_OR_MORE,
     *         Cardinality.ZERO_OR_MORE, Cardinality.EXACTLY_ONE,
     *         Cardinality.ZERO_OR_ONE, Cardinality.EMPTY. This default
     *         implementation returns ZERO_OR_MORE (which effectively gives no
     *         information).
     */

    public int getCardinality() {
        return getChild().getCardinality();
    }

    /**
     * Determine which aspects of the context the expression depends on. The result is
     * a bitwise-or'ed value composed from constants such as {@link net.sf.saxon.expr.StaticProperty#DEPENDS_ON_CONTEXT_ITEM} and
     * {@link net.sf.saxon.expr.StaticProperty#DEPENDS_ON_CURRENT_ITEM}. The default implementation combines the intrinsic
     * dependencies of this expression with the dependencies of the subexpressions,
     * computed recursively. This is overridden for expressions such as FilterExpression
     * where a subexpression's dependencies are not necessarily inherited by the parent
     * expression.
     *
     * @return a set of bit-significant flags identifying the dependencies of
     *         the expression
     */

    public int getDependencies() {
        return getChild().getDependencies();
    }

    /**
     * Determine whether this instruction creates new nodes.
     */

    public final boolean mayCreateNewNodes() {
        return (getChild().getSpecialProperties() & StaticProperty.NON_CREATIVE) == 0;
    }

    /**
     * Return the estimated cost of evaluating an expression. For a TraceExpression we return zero,
     * because ideally we don't want trace expressions to affect optimization decisions.
     * @return zero
     */
    @Override
    public int getNetCost() {
        return 0;
    }

    /**
     * Evaluate an expression as a single item. This always returns either a single Item or
     * null (denoting the empty sequence). No conversion is done. This method should not be
     * used unless the static type of the expression is a subtype of "item" or "item?": that is,
     * it should not be called if the expression may return a sequence. There is no guarantee that
     * this condition will be detected.
     *
     * @param context The context in which the expression is to be evaluated
     * @return the node or atomic value that results from evaluating the
     *         expression; or null to indicate that the result is an empty
     *         sequence
     * @throws net.sf.saxon.trans.XPathException
     *          if any dynamic error occurs evaluating the
     *          expression
     */

    public Item evaluateItem(XPathContext context) throws XPathException {
        Controller controller = context.getController();
        assert controller != null;
        if (controller.isTracing()) {
            controller.getTraceListener().enter(getInstructionInfo(), context);
        }
        Item result = getChild().evaluateItem(context);
        if (controller.isTracing()) {
            controller.getTraceListener().leave(getInstructionInfo());
        }
        return result;
    }

    /**
     * Return an Iterator to iterate over the values of a sequence. The value of every
     * expression can be regarded as a sequence, so this method is supported for all
     * expressions. This default implementation handles iteration for expressions that
     * return singleton values: for non-singleton expressions, the subclass must
     * provide its own implementation.
     *
     * @param context supplies the context for evaluation
     * @return a SequenceIterator that can be used to iterate over the result
     *         of the expression
     * @throws net.sf.saxon.trans.XPathException
     *          if any dynamic error occurs evaluating the
     *          expression
     */

    /*@NotNull*/
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        Controller controller = context.getController();
        assert controller != null;
        if (controller.isTracing()) {
            controller.getTraceListener().enter(getInstructionInfo(), context);
        }
        SequenceIterator result = getChild().iterate(context);
        if (controller.isTracing()) {
            controller.getTraceListener().leave(getInstructionInfo());
        }
        return result;
    }

    public int getInstructionNameCode() {
        if (getChild() instanceof Instruction) {
            return ((Instruction) getChild()).getInstructionNameCode();
        } else {
            return -1;
        }
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    public void export(ExpressionPresenter out) throws XPathException {
        getChild().export(out);
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
        Controller controller = context.getController();
        assert controller != null;
        if (controller.isTracing()) {
            controller.getTraceListener().enter(getInstructionInfo(), context);
        }
        getChild().evaluatePendingUpdates(context, pul);
        if (controller.isTracing()) {
            controller.getTraceListener().leave(getInstructionInfo());
        }
    }

    public String getSystemId() {
        return getLocation().getSystemId();
    }

    public int getColumnNumber() {
        return getLocation().getColumnNumber();
    }

    public String getPublicId() {
        return getLocation().getPublicId();
    }

    public int getLineNumber() {
        return getLocation().getLineNumber();
    }

    public Location saveLocation() {
        return this;
    }


}

