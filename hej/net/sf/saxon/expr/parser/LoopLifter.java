////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.parser;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.instruct.Choose;
import net.sf.saxon.lib.FeatureKeys;
import net.sf.saxon.lib.Logger;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.SequenceType;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Class to handle loop-lifting optimization, that is, extraction of subexpressions appearing within
 * a loop when there is no dependency on the controlling variable of the loop. This handles both
 * focus-dependent loops (such as xsl:for-each) and variable-dependent loops (such as XPath for-expressions),
 * and also takes into account specialist loops such as xsl:for-each-group, xsl:merge, and xsl:analyze-string.
 *
 * The class is instantiated to perform optimization of a component such as a function or template, and
 * it contains temporary instance-level data pertaining to that function or template.
 */

public class LoopLifter {

    /**
     * Apply loop-lifting to an expression (typically the body of a template or function)
     * @param exp the expression to which loop lifting is applied
     * @return the optimized expression
     */

    public static Expression process(Expression exp, ExpressionVisitor visitor, ContextItemStaticInfo contextInfo)
    throws XPathException {
        //exp.verifyParentPointers();
        LoopLifter lifter = new LoopLifter(exp, visitor.getConfiguration());
        RetainedStaticContext rsc = exp.getRetainedStaticContext();
        lifter.gatherInfo(exp);
        lifter.loopLift(exp);
        lifter.root.setRetainedStaticContext(rsc);
        lifter.root.setParentExpression(null);
        if (lifter.changed) {
            ExpressionTool.resetPropertiesWithinSubtree(lifter.root);
            Expression e2 = lifter.root.optimize(visitor, contextInfo);
            e2.setParentExpression(null);
            return e2;
        } else {
            return lifter.root;
        }
    }

    private Expression root;
    private Configuration config;
    private int sequence = 0;
    private boolean changed = false;
    private boolean tracing = false;

    private static class ExpInfo {
        Expression expression;
        int loopLevel;
        boolean multiThreaded;
        Map<Expression, Boolean> dependees = new IdentityHashMap<Expression, Boolean>();

    }

    private Map<Expression, ExpInfo> expInfoMap = new IdentityHashMap<Expression, ExpInfo>();

    public LoopLifter(Expression root, Configuration config) {
        this.root = root;
        this.config = config;
        this.tracing = config.getBooleanProperty(FeatureKeys.TRACE_OPTIMIZER_DECISIONS);
    }

    public Expression getRoot() {
        return root;
    }

    /**
     * Gather information about an expression. The information (in the form of an ExpInfo object)
     * is added to the expInfoMap, which is indexed by expression.
     * @param exp the expression for which information is required
     */

    public void gatherInfo(Expression exp) {
        gatherInfo(exp, 0, 0, false);
    }

    private void gatherInfo(Expression exp, int level, int loopLevel, boolean multiThreaded) {
        ExpInfo info = new ExpInfo();
        info.expression = exp;
        info.loopLevel = loopLevel;
        info.multiThreaded = multiThreaded;
        expInfoMap.put(exp, info);
        Expression scope = exp.getScopingExpression();
        if (scope != null) {
            markDependencies(exp, scope);
        }
        boolean threaded = multiThreaded || exp.isMultiThreaded(config);
        // Don't loop-lift out of a conditional, because it can lead to type errors
        Expression choose = getContainingConditional(exp);
        if (choose != null) {
            markDependencies(exp, choose);
        }
        for (Operand o : exp.operands()) {
            gatherInfo(o.getChildExpression(), level+1, o.isEvaluatedRepeatedly() ? loopLevel+1 : loopLevel, threaded);
        }
    }

    private Expression getContainingConditional(Expression exp) {
        Expression parent = exp.getParentExpression();
        while (parent != null) {
            if (parent instanceof Choose) {
                Operand o = ExpressionTool.findOperand(parent, exp);
                if (o == null) {
                    throw new AssertionError();
                }
                if (o.getOperandRole().isInChoiceGroup()) {
                    return parent;
                }
            }
            // TODO: need similar for SwitchExpression (EE only)
            exp = parent;
            parent = parent.getParentExpression();
        }
        return null;
    }

    /**
     * Register the dependencies of an expressions, and its applicable ancestor expressions, on some ancestor
     * expression that binds a variable or the focus
     * @param exp the dependent expression
     * @param variableSetter the expression that sets the focus or the variable in question. May be null, in which
     *                       case no dependencies are marked.
     */

    private void markDependencies(Expression exp, Expression variableSetter) {
        Expression parent;
        if (variableSetter != null) {
            parent = exp;
            while (parent != null && parent != variableSetter) {
                try {
                    expInfoMap.get(parent).dependees.put(variableSetter, true);
                } catch (NullPointerException e) {
                    e.printStackTrace();
                }
                parent = parent.getParentExpression();
            }
        }
    }


    private void loopLift(Expression exp) {
        ExpInfo info = expInfoMap.get(exp);
        if (!info.multiThreaded) {
            if (info.loopLevel > 0 && exp.getNetCost() > 0) {
                if (info.dependees.isEmpty() && exp.isLiftable()) {
                    root = lift(exp, root);
                } else {
                    Expression child = exp;
                    ExpInfo expInfo = expInfoMap.get(exp);
                    Expression parent = exp.getParentExpression();
                    while (parent != null) {
                        if (expInfo.dependees.get(parent) != null) {
                            ExpInfo childInfo = expInfoMap.get(child);
                            if (expInfo.loopLevel != childInfo.loopLevel) {
                                Operand o = ExpressionTool.findOperand(parent, child);
                                if (exp.isLiftable() && !(child instanceof PseudoExpression) && !o.getOperandRole().isConstrainedClass()) {
                                    Expression lifted = lift(exp, child);
                                    o.setChildExpression(lifted);
                                }
                            }
                            break;
                        }
                        child = parent;
                        parent = parent.getParentExpression();
                    }
                }
            }
            //ExpressionTool.validateTree(exp);
            for (Operand o : exp.operands()) {
                if (!o.getOperandRole().isConstrainedClass()) {
                    loopLift(o.getChildExpression());
                }
            }
        }
    }

    private Expression lift(Expression child, Expression newAction) {

        changed = true;
        ExpInfo childInfo = expInfoMap.get(child);
        ExpInfo actionInfo = expInfoMap.get(newAction);

        final int hoist = childInfo.loopLevel - actionInfo.loopLevel;

        Expression oldParent = child.getParentExpression();
        Operand oldOperand = ExpressionTool.findOperand(oldParent, child);
        assert oldOperand != null;

        LetExpression let = new LetExpression();
        let.setVariableQName(new StructuredQName("vv", NamespaceConstant.SAXON_GENERATED_VARIABLE, "v" + sequence++));
        SequenceType type = SequenceType.makeSequenceType(child.getItemType(), child.getCardinality());
        let.setRequiredType(type);
        ExpressionTool.copyLocationInfo(child, let);
        let.setSequence(child);
        let.setNeedsLazyEvaluation(true);
        let.setEvaluationMode(Cardinality.allowsMany(child.getCardinality()) ? ExpressionTool.MAKE_MEMO_CLOSURE : ExpressionTool.MAKE_SINGLETON_CLOSURE);
        let.setAction(newAction);
        let.adoptChildExpression(newAction);
//        if (indexed) {
//            let.setIndexedVariable();
//        }


        ExpInfo letInfo = new ExpInfo();
        letInfo.expression = let;
        letInfo.dependees = childInfo.dependees;
        letInfo.dependees.putAll(actionInfo.dependees);
        letInfo.loopLevel = actionInfo.loopLevel;
        expInfoMap.put(let, letInfo);

        try {
            ExpressionTool.processExpressionTree(child, null, new ExpressionAction() {
                public boolean process(Expression expression, Object result) throws XPathException {
                    ExpInfo info = expInfoMap.get(expression);
                    info.loopLevel -= hoist;
                    return false;
                }
            });
        } catch (XPathException e) {
            e.printStackTrace();
        }

        LocalVariableReference var = new LocalVariableReference(let);
        int properties = child.getSpecialProperties() & StaticProperty.NOT_UNTYPED_ATOMIC;
        var.setStaticType(type, null, properties);
        let.addReference(var, true);
        ExpressionTool.copyLocationInfo(child, var);
        oldOperand.setChildExpression(var);

        if (tracing) {
            Logger err = config.getLogger();
            err.info("OPT : At line " + child.getLocation().getLineNumber() + " of " + child.getLocation().getSystemId());
            err.info("OPT : Lifted (" + child.toShortString() + ") above (" + newAction.toShortString() + ") on line " + newAction.getLocation().getLineNumber());
            err.info("OPT : Expression after rewrite: " + let.toString());
        }
        return let;
    }


}

