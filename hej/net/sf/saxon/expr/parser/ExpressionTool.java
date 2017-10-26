////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.parser;

import com.saxonica.ee.stream.StreamInstr;
import net.sf.saxon.Configuration;
import net.sf.saxon.Controller;
import net.sf.saxon.event.SequenceOutputter;
import net.sf.saxon.event.SequenceReceiver;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.flwor.Clause;
import net.sf.saxon.expr.flwor.FLWORExpression;
import net.sf.saxon.expr.flwor.LocalVariableBinding;
import net.sf.saxon.expr.instruct.*;
import net.sf.saxon.expr.sort.ConditionalSorter;
import net.sf.saxon.expr.sort.DocumentSorter;
import net.sf.saxon.functions.Current;
import net.sf.saxon.functions.CurrentGroup;
import net.sf.saxon.functions.IriToUri;
import net.sf.saxon.functions.RegexGroup;
import net.sf.saxon.lib.Logger;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.StandardLogger;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.Pattern;
import net.sf.saxon.query.QueryModule;
import net.sf.saxon.style.Compilation;
import net.sf.saxon.style.ExpressionContext;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.SymbolicName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.value.*;

import javax.xml.transform.SourceLocator;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * This class, ExpressionTool, contains a number of useful static methods
 * for manipulating expressions. Most importantly, it provides the factory
 * method make() for constructing a new expression
 */

public class ExpressionTool {

    public static final int UNDECIDED = -1;
    public static final int NO_EVALUATION_NEEDED = 0;
    public static final int EVALUATE_VARIABLE = 1;
    public static final int MAKE_CLOSURE = 3;
    public static final int MAKE_MEMO_CLOSURE = 4;
    public static final int RETURN_EMPTY_SEQUENCE = 5;
    public static final int EVALUATE_AND_MATERIALIZE_VARIABLE = 6;
    public static final int CALL_EVALUATE_ITEM = 7;
    public static final int ITERATE_AND_MATERIALIZE = 8;
    public static final int PROCESS = 9;
    public static final int LAZY_TAIL_EXPRESSION = 10;
    public static final int SHARED_APPEND_EXPRESSION = 11;
    public static final int MAKE_INDEXED_VARIABLE = 12;
    public static final int MAKE_SINGLETON_CLOSURE = 13;
    public static final int EVALUATE_SUPPLIED_PARAMETER = 14;
    public static final int STREAMING_ARGUMENT = 15;

    private ExpressionTool() {
    }

    /**
     * Parse an XPath expression. This performs the basic analysis of the expression against the
     * grammar, it binds variable references and function calls to variable definitions and
     * function definitions, and it performs context-independent expression rewriting for
     * optimization purposes.
     *
     * @param expression   The expression (as a character string)
     * @param env          An object giving information about the compile-time
     *                     context of the expression
     * @param start        position of the first significant character in the expression
     * @param terminator   The token that marks the end of this expression; typically
     *                     Token.EOF, but may for example be a right curly brace
     * @param codeInjector true  allows injection of tracing, debugging, or performance monitoring code; null if
     *                     not required
     * @return an object of type Expression
     * @throws XPathException if the expression contains a static error
     */

    /*@NotNull*/
    public static Expression make(String expression, StaticContext env,
                                  int start, int terminator,
                                  CodeInjector codeInjector) throws XPathException {
        XPathParser parser = env.getConfiguration().newExpressionParser("XP", false, 31);
        if (codeInjector != null) {
            parser.setCodeInjector(codeInjector);
        }
        if (terminator == -1) {
            terminator = Token.EOF;
        }
        Expression exp = parser.parse(expression, start, terminator, env);
        setDeepRetainedStaticContext(exp, env.makeRetainedStaticContext());
        exp = exp.simplify();
        return exp;
    }

    /**
     * Ensure that every node in the expression tree has a retained static context.
     * A node that already has a retained static context will propagate it to its children.
     * @param exp the root of the expression tree
     * @param rsc the retained static context to be applied to nodes that do not already have one,
     *            and that do not have an ancestor with an existing retained static context.
     */

    public static void setDeepRetainedStaticContext(Expression exp, RetainedStaticContext rsc) {
        if (exp.getLocalRetainedStaticContext() == null) {
            exp.setRetainedStaticContextLocally(rsc);
        } else {
            rsc = exp.getLocalRetainedStaticContext();
        }
        for (Operand o : exp.operands()) {
            setDeepRetainedStaticContext(o.getChildExpression(), rsc);
        }
    }

    /**
     * Copy location information (the line number and reference to the container) from one expression
     * to another
     *
     * @param from the expression containing the location information
     * @param to   the expression to which the information is to be copied
     */

    public static void copyLocationInfo(Expression from, Expression to) {
        if (from != null && to != null) {
            if (to.getLocation() == null || to.getLocation() == ExplicitLocation.UNKNOWN_LOCATION) {
                to.setLocation(from.getLocation());
            }
            //to.setContainer(from.getContainer());
            if (to.getRetainedStaticContext() == null) {
                to.setRetainedStaticContextLocally(from.getRetainedStaticContext());
            }
        }
    }

    /**
     * Remove unwanted sorting from an expression, at compile time, if and only if it is known
     * that the result of the expression will be homogeneous (all nodes, or all atomic values).
     * This is done when we need the effective boolean value of a sequence: the EBV of a
     * homogenous sequence does not depend on its order, but this is not true when atomic
     * values and nodes are mixed: (N, AV) is true, but (AV, N) is an error.
     *
     * @param exp          the expression to be optimized
     * @param forStreaming true if streamed evaluation of the expression is required
     * @return the expression after rewriting
     * @throws net.sf.saxon.trans.XPathException if a static error is found while doing the rewrite
     */

    public static Expression unsortedIfHomogeneous(Expression exp, boolean forStreaming)
            throws XPathException {
        if (exp instanceof Literal) {
            return exp;   // fast exit
        }
        if (exp.getItemType() instanceof AnyItemType) {
            return exp;
        } else {
            return exp.unordered(false, forStreaming);
        }
    }

    /**
     * Determine the method of evaluation to be used when lazy evaluation of an expression is
     * preferred. This method is called at compile time, after all optimizations have been done,
     * to determine the preferred strategy for lazy evaluation, depending on the type of expression.
     *
     * @param exp the expression to be evaluated
     * @return an integer constant identifying the evaluation mode
     */

    public static int lazyEvaluationMode(Expression exp) {
        if (exp instanceof Literal) {
            return NO_EVALUATION_NEEDED;

        } else if (exp instanceof VariableReference) {
            return EVALUATE_VARIABLE;

        } else if (exp instanceof SuppliedParameterReference) {
            return EVALUATE_SUPPLIED_PARAMETER;

        } else if ((exp.getDependencies() &
                (StaticProperty.DEPENDS_ON_POSITION |
                        StaticProperty.DEPENDS_ON_LAST |
                        StaticProperty.DEPENDS_ON_CURRENT_ITEM |
                        StaticProperty.DEPENDS_ON_CURRENT_GROUP |
                        StaticProperty.DEPENDS_ON_REGEX_GROUP)) != 0) {
            // we can't save these values in the closure, so we evaluate
            // the expression now if they are needed
            return eagerEvaluationMode(exp);

        } else if (exp instanceof ErrorExpression) {
            return CALL_EVALUATE_ITEM;
            // evaluateItem() on an error expression throws the latent exception

        } else if (!Cardinality.allowsMany(exp.getCardinality())) {
            // singleton expressions are always evaluated eagerly
            return eagerEvaluationMode(exp);

        } else if (exp instanceof TailExpression) {
            // Treat tail recursion as a special case, to avoid creating a deeply-nested
            // tree of Closures. If this expression is a TailExpression, and its first
            // argument is also a TailExpression, we combine the two TailExpressions into
            // one and return a closure over that.
            TailExpression tail = (TailExpression) exp;
            Expression base = tail.getBaseExpression();
            if (base instanceof VariableReference) {
                return LAZY_TAIL_EXPRESSION;
            } else {
                return MAKE_CLOSURE;
            }

        } else if (exp instanceof Block && ((Block) exp).isCandidateForSharedAppend()) {
            // If the expression is a Block, that is, it is appending a value to a sequence,
            // then we have the opportunity to use a shared list underpinning the old value and
            // the new. This takes precedence over lazy evaluation (it would be possible to do this
            // lazily, but more difficult). We currently do this for any Block that has a variable
            // reference as one of its subexpressions. The most common case is that the first argument is a reference
            // to an argument of recursive function, where the recursive function returns the result of
            // appending to the sequence.
            return SHARED_APPEND_EXPRESSION;

        } else {
            // create a Closure, a wrapper for the expression and its context
            return MAKE_CLOSURE;
        }
    }

    /**
     * Determine the method of evaluation to be used when lazy evaluation of an expression is
     * preferred. This method is called at compile time, after all optimizations have been done,
     * to determine the preferred strategy for lazy evaluation, depending on the type of expression.
     *
     * @param exp the expression to be evaluated
     * @return an integer constant identifying the evaluation mode
     */

    public static int eagerEvaluationMode(Expression exp) {
        if (exp instanceof Literal && !(((Literal) exp).getValue() instanceof Closure)) {
            return NO_EVALUATION_NEEDED;
        }
        if (exp instanceof VariableReference) {
            return EVALUATE_AND_MATERIALIZE_VARIABLE;
        }
        int m = exp.getImplementationMethod();
        if ((m & Expression.EVALUATE_METHOD) != 0 && !Cardinality.allowsMany(exp.getCardinality())) {
            return CALL_EVALUATE_ITEM;
        } else if ((m & Expression.ITERATE_METHOD) != 0) {
            return ITERATE_AND_MATERIALIZE;
        } else {
            return PROCESS;
        }
    }


    /**
     * Do lazy evaluation of an expression. This will return a value, which may optionally
     * be a SequenceIntent, which is a wrapper around an iterator over the value of the expression.
     *
     * @param exp            the expression to be evaluated
     * @param evaluationMode the evaluation mode for this expression
     * @param context        the run-time evaluation context for the expression. If
     *                       the expression is not evaluated immediately, then parts of the
     *                       context on which the expression depends need to be saved as part of
     *                       the Closure
     * @param ref            an indication of how the value will be used. The value 1 indicates that the value
     *                       is only expected to be used once, so that there is no need to keep it in memory. A small value >1
     *                       indicates multiple references, so the value will be saved when first evaluated. The special value
     *                       FILTERED indicates a reference within a loop of the form $x[predicate], indicating that the value
     *                       should be saved in a way that permits indexing.
     * @return a value: either the actual value obtained by evaluating the
     * expression, or a Closure containing all the information needed to
     * evaluate it later
     * @throws XPathException if any error occurs in evaluating the
     *                        expression
     */

    public static Sequence evaluate(Expression exp, int evaluationMode, XPathContext context, int ref)
            throws XPathException {
        switch (evaluationMode) {

            case NO_EVALUATION_NEEDED:
                return ((Literal) exp).getValue();

            case EVALUATE_VARIABLE:
                return ((VariableReference) exp).evaluateVariable(context);

            case EVALUATE_SUPPLIED_PARAMETER:
                return ((SuppliedParameterReference) exp).evaluateVariable(context);

            case MAKE_CLOSURE:
                return Closure.make(exp, context, ref);
                //return SequenceExtent.makeSequenceExtent(exp.iterate(context));

            case MAKE_MEMO_CLOSURE:
                return Closure.make(exp, context, ref == 1 ? 10 : ref);
                //return SequenceExtent.makeSequenceExtent(exp.iterate(context));

            case MAKE_SINGLETON_CLOSURE:
                return new SingletonClosure(exp, context);
                //return SequenceExtent.makeSequenceExtent(exp.iterate(context));

            case RETURN_EMPTY_SEQUENCE:
                return EmptySequence.getInstance();

            case EVALUATE_AND_MATERIALIZE_VARIABLE:
                Sequence v = ((VariableReference) exp).evaluateVariable(context);
                if (v instanceof Closure) {
                    return SequenceExtent.makeSequenceExtent(v.iterate());
                } else {
                    return v;
                }

            case CALL_EVALUATE_ITEM:
                Item item = exp.evaluateItem(context);
                if (item == null) {
                    return EmptySequence.getInstance();
                } else {
                    return item;
                }

            case UNDECIDED:
            case ITERATE_AND_MATERIALIZE:
                if (ref == FilterExpression.FILTERED) {
                    return context.getConfiguration().makeSequenceExtent(exp, ref, context);
                } else {
                    return SequenceExtent.makeSequenceExtent(exp.iterate(context));
                }

            case PROCESS:
                Controller controller = context.getController();
                SequenceReceiver saved = context.getReceiver();
                SequenceOutputter seq = controller.allocateSequenceOutputter(20);
                seq.getPipelineConfiguration().setHostLanguage(exp.getPackageData().getHostLanguage());
                context.setReceiver(seq);
                seq.open();
                exp.process(context);
                seq.close();
                context.setReceiver(saved);
                Sequence val = seq.getSequence();
                seq.reset();
                return val;

            case LAZY_TAIL_EXPRESSION: {
                TailExpression tail = (TailExpression) exp;
                VariableReference vr = (VariableReference) tail.getBaseExpression();
                Sequence base = evaluate(vr, EVALUATE_VARIABLE, context, ref);
                if (base instanceof MemoClosure) {
                    SequenceIterator it = base.iterate();
                    base = SequenceTool.toGroundedValue(it);
                }
                if (base instanceof IntegerRange) {
                    long start = ((IntegerRange) base).getStart() + tail.getStart() - 1;
                    long end = ((IntegerRange) base).getEnd();
                    if (start == end) {
                        return Int64Value.makeIntegerValue(end);
                    } else if (start > end) {
                        return EmptySequence.getInstance();
                    } else {
                        return new IntegerRange(start, end);
                    }
                }
                if (base instanceof SequenceExtent) {
                    if (tail.getStart() > ((SequenceExtent) base).getLength()) {
                        return EmptySequence.getInstance();
                    } else {
                        return new SequenceExtent(
                                (SequenceExtent) base,
                                tail.getStart() - 1,
                                ((SequenceExtent) base).getLength() - tail.getStart() + 1).reduce();
                    }
                }

                return Closure.make(tail, context, ref);
            }

            case SHARED_APPEND_EXPRESSION: {
                if (exp instanceof Block) {
                    Block block = (Block) exp;
                    Operand[] children = block.getOperanda();
                    List<GroundedValue> subsequences = new ArrayList<GroundedValue>(children.length);
                    for (Operand o : children) {
                        Expression child = o.getChildExpression();
                        if (Cardinality.allowsMany(child.getCardinality())) {
                            subsequences.add(SequenceTool.toGroundedValue(child.iterate(context)));
                        } else {
                            Item j = child.evaluateItem(context);
                            if (j != null) {
                                subsequences.add(j instanceof GroundedValue ? (GroundedValue) j : new One(j));
                            }
                        }
                    }
                    return new Chain(subsequences);
                } else {
                    // it's not a Block: it must have been rewritten after deciding to use this evaluation mode
                    return SequenceExtent.makeSequenceExtent(exp.iterate(context));
                }
            }

            case MAKE_INDEXED_VARIABLE:
                return context.getConfiguration().obtainOptimizer().makeIndexedValue(exp.iterate(context));

            case STREAMING_ARGUMENT:
                return context.getConfiguration().obtainOptimizer().evaluateStreamingArgument(exp, context);

            default:
                throw new IllegalArgumentException("Unknown evaluation mode " + evaluationMode);

        }
    }

    /**
     * Do lazy evaluation of an expression. This will return a value, which may optionally
     * be a SequenceIntent, which is a wrapper around an iterator over the value of the expression.
     *
     * @param exp     the expression to be evaluated
     * @param context the run-time evaluation context for the expression. If
     *                the expression is not evaluated immediately, then parts of the
     *                context on which the expression depends need to be saved as part of
     *                the Closure
     * @param ref     an indication of how the value will be used. The value 1 indicates that the value
     *                is only expected to be used once, so that there is no need to keep it in memory. A small value >1
     *                indicates multiple references, so the value will be saved when first evaluated. The special value
     *                FILTERED indicates a reference within a loop of the form $x[predicate], indicating that the value
     *                should be saved in a way that permits indexing.
     * @return a value: either the actual value obtained by evaluating the
     * expression, or a Closure containing all the information needed to
     * evaluate it later
     * @throws XPathException if any error occurs in evaluating the
     *                        expression
     */

    public static Sequence lazyEvaluate(Expression exp, XPathContext context, int ref) throws XPathException {
        final int evaluationMode = lazyEvaluationMode(exp);
        return evaluate(exp, evaluationMode, context, ref);
    }

    /**
     * Evaluate an expression now; lazy evaluation is not permitted in this case
     *
     * @param exp     the expression to be evaluated
     * @param context the run-time evaluation context
     * @return the result of evaluating the expression
     * @throws net.sf.saxon.trans.XPathException if any dynamic error occurs evaluating the
     *                                           expression
     */

    public static Sequence eagerEvaluate(Expression exp, XPathContext context) throws XPathException {
        final int evaluationMode = eagerEvaluationMode(exp);
        return evaluate(exp, evaluationMode, context, 10);
    }

    /**
     * Scan an expression to find and mark any recursive tail function calls
     *
     * @param exp   the expression to be analyzed
     * @param qName the name of the containing function
     * @param arity the arity of the containing function
     * @return 0 if no tail call was found; 1 if a tail call to a different function was found;
     * 2 if a tail call to the specified function was found. In this case the
     * UserFunctionCall object representing the tail function call will also have been marked as
     * a tail call.
     */

    public static int markTailFunctionCalls(Expression exp, StructuredQName qName, int arity) {
        return exp.markTailFunctionCalls(qName, arity);
    }

    /**
     * Construct indent string, for diagnostic output
     *
     * @param level the indentation level (the number of spaces to return)
     * @return a string of "level*2" spaces
     */

    public static String indent(int level) {
        FastStringBuffer fsb = new FastStringBuffer(level);
        for (int i = 0; i < level; i++) {
            fsb.append("  ");
        }
        return fsb.toString();
    }

    /**
     * Ask whether one expression is a subexpression of another
     * @param a the containing expression
     * @param b the putative contained expression
     * @return true if and only if b is a subexpression (at some level) of a
     */

    public static boolean contains(Expression a, Expression b) {
        Expression temp = b;
        while (temp != null) {
            if (temp == a) {
                return true;
            } else {
                temp = temp.getParentExpression();
            }
        }
        return false;
    }

    /**
     * Determine whether an expression contains a LocalParamSetter subexpression
     *
     * @param exp the expression to be tested
     * @return true if the exprssion contains a local parameter setter
     */

    public static boolean containsLocalParam(Expression exp) {
        return contains(exp, true, new ExpressionPredicate() {
            public boolean matches(Expression e) {
                return e instanceof LocalParam;
            }
        });
    }

    /**
     * Determine whether an expression contains a local variable reference,
     * other than a reference to a local variable whose binding occurs
     * within this expression
     *
     * @param exp the expression to be tested
     * @return true if the expression contains a local variable reference to a local
     * variable declared outside this expression.
     */

    public static boolean containsLocalVariableReference(final Expression exp) {
        return contains(exp, false, new ExpressionPredicate() {
            public boolean matches(Expression e) {
                if (e instanceof LocalVariableReference) {
                    LocalVariableReference vref = (LocalVariableReference)e;
                    LocalBinding binding = vref.getBinding();
                    return !(binding instanceof Expression && contains(exp, (Expression) binding));
                }
                return false;
            }
        });
    }

    /**
     * Test whether a given expression is, or contains, at any depth, an expression that satisfies a supplied
     * condition
     *
     * @param exp           the given expression
     * @param sameFocusOnly if true, only subexpressions evaluated in the same focus are searched. If false,
     *                      all subexpressions are searched
     * @param predicate     the condition to be satisfied
     * @return true if the given expression is or contains an expression that satisfies the condition.
     */

    public static boolean contains(Expression exp, boolean sameFocusOnly, ExpressionPredicate predicate) {
        if (predicate.matches(exp)) {
            return true;
        }
        for (Operand info : exp.operands()) {
            if ((info.hasSameFocus() || !sameFocusOnly) && contains(info.getChildExpression(), sameFocusOnly, predicate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determine whether an expression possible calls (directly or indirectly) xsl:result-document, or
     * has other context dependencies that prevent function inlining,
     * which we assume is true if it contains a template call.
     * A call on result-document disqualifies the function from being
     * inlined, because error XTDE1480 would then not be detected. We don't have to worry about functions
     * containing further function calls because they are disqualified from inlining anyway.
     *
     * @param exp the expression to be tested
     * @return true if there is any possibility that calling the function might result in a call on
     * xsl:result-document
     */

    public static boolean changesXsltContext(Expression exp) {

        exp = exp.getInterpretedExpression();

        if (exp instanceof ResultDocument || exp instanceof CallTemplate || exp instanceof ApplyTemplates ||
                exp instanceof NextMatch || exp instanceof ApplyImports || exp.isCallOn(RegexGroup.class)
                || exp.isCallOn(CurrentGroup.class)) {
            return true;
        }
        for (Operand o : exp.operands()) {
            if (changesXsltContext(o.getChildExpression())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Return true if a given expression is evaluated repeatedly
     * when a given ancestor expression is evaluated once
     *
     * @param child    the expression to be tested
     * @param ancestor the ancestor expression. May be null, in which case the search goes all the way
     *                 to the base of the stack.
     * @return true if the current expression is evaluated repeatedly
     */

    public static boolean isLoopingSubexpression(Expression child, Expression ancestor) {
        while (true) {

            Expression parent = child.getParentExpression();
            if (parent == null) {
                return false;
            }
            if (hasLoopingSubexpression(parent, child)) {
                return true;
            }
            if (parent == ancestor) {
                return false;
            }
            child = parent;
        }
    }

    public static boolean isLoopingReference(VariableReference reference, Binding binding) {
        Expression child = reference;
        Expression parent = child.getParentExpression();
        while (true) {
            if (parent == null) {
                // haven't found the binding on the stack, so the safe thing is to assume we're in a loop
                return true;
            }
            if (parent instanceof FLWORExpression) {
                if (parent.hasVariableBinding(binding)) {
                    // The variable is declared in one of the clauses of the FLWOR expression
                    return ((FLWORExpression) parent).hasLoopingVariableReference(binding);
                } else {
                    // The variable is declared outside the FLWOR expression
                    if (hasLoopingSubexpression(parent, child)) {
                        return true;
                    }
                }
            } else if (parent.getExpressionName().equals("tryCatch")) {
                return true; // not actually a loop, but it's a simple way to prevent inlining of variables (test QT3 try-007)
            } else {
                if (parent instanceof ForEachGroup && parent.hasVariableBinding(binding)) {
                    return false;
                }
                if (hasLoopingSubexpression(parent, child)) {
                    return true;
                }
                if (parent.hasVariableBinding(binding)) {
                    return false;
                }
            }
            child = parent;
            parent = child.getParentExpression();
        }
    }

    public static boolean hasLoopingSubexpression(Expression parent, Expression child) {
        for (Operand info : parent.operands()) {
            if (info.getChildExpression() == child) {
                return info.isEvaluatedRepeatedly();
            }
        }
        return false;
    }

    /**
     * Get the focus-setting container of an expression
     *
     *      *
     * <p>Note, this always returns an expression or null. Unlike the like-named concept in the
     * spec, it can't return a component such as a template or an attribute set.</p>
     * @param exp the expression whose focus-setting container is required
     * @return the focus-setting container, or null if the focus for the expression is the same as the
     * focus for the containing component as a whole
     */

    public static Expression getFocusSettingContainer(Expression exp) {
        Expression child = exp;
        Expression parent = child.getParentExpression();
        while (parent != null) {
            Operand o = findOperand(parent, child);
            if (o == null) {
                throw new AssertionError();
            }
            if (!o.hasSameFocus()) {
                return parent;
            }
            child = parent;
            parent = child.getParentExpression();
        }
        return null;
    }

    /**
     * Get the context-document-setting container of an expression. This is the same as the
     * focus-setting container, except when the context-setting operand is a single-document node-set,
     * in which case we can go up one more level. For example, given /a/b[/d/e = 3], the predicate
     * /d/e = 3 can be loop-lifted from the filter expression, because it is known that the base
     * expression /a/b is a single-document node-set.
     *
     * @param exp the expression whose document-context-setting container is required
     * @return the context-document-setting container, or null if the focus for the expression is the same as the
     * focus for the containing component as a whole
     */

    public static Expression getContextDocumentSettingContainer(Expression exp) {
        Expression child = exp;
        Expression parent = child.getParentExpression();
        while (parent != null) {
            if (parent instanceof ContextSwitchingExpression) {
                ContextSwitchingExpression switcher = (ContextSwitchingExpression)parent;
                if (child == switcher.getActionExpression()) {
                    if ((switcher.getSelectExpression().getSpecialProperties() & StaticProperty.CONTEXT_DOCUMENT_NODESET) != 0) {
                        parent.resetLocalStaticProperties();
                        parent.getSpecialProperties();
                        return getContextDocumentSettingContainer(parent);
                    }
                }
            }
            Operand o = findOperand(parent, child);
            if (o == null) {
                throw new AssertionError();
            }
            if (!o.hasSameFocus()) {
                return parent;
            }
            child = parent;
            parent = child.getParentExpression();
        }
        return null;
    }

    /**
     * Reset the static properties for the current expression and for all its containing expressions.
     * This should be done whenever the expression is changed in a way that might
     * affect the properties. It causes the properties to be recomputed next time they are needed.
     *
     * @param exp the expression whose properties are to be reset; the method also resets local
     *            properties for all its ancestor expressions.
     */

    public static void resetStaticProperties(Expression exp) {
        int i = 0;
        while (exp != null) {
            exp.resetLocalStaticProperties();
            exp = exp.getParentExpression();
            if (i++ > 100000) {
                throw new IllegalStateException("Loop in parent expression chain");
            }
        }
    }

    /**
     * Return true if two objects are equal or if both are null
     *
     * @param x the first object
     * @param y the second object
     * @return true if both x and y are null or if x.equals(y)
     */

    public static boolean equalOrNull(Object x, Object y) {
        if (x == null) {
            return y == null;
        } else {
            return (y != null) && x.equals(y);
        }
    }

    /**
     * Helper method to construct an iterator over the results of the expression when all that
     * the expression itself offers is a process() method. This builds the entire results of the
     * expression as a sequence in memory and then iterates over it.
     *
     * @param context the dynamic evaluation context
     * @return an iterator over the results of the expression
     * @throws XPathException if a dynamic error occurs
     */

    public static SequenceIterator getIteratorFromProcessMethod(Expression exp, XPathContext context) throws XPathException {
        Controller controller = context.getController();
        assert controller != null;
        SequenceReceiver saved = context.getReceiver();
        SequenceOutputter seq = controller.allocateSequenceOutputter(20);
        seq.getPipelineConfiguration().setHostLanguage(exp.getPackageData().getHostLanguage());
        context.setReceiver(seq);
        exp.process(context);
        context.setReceiver(saved);
        seq.close();
        return seq.iterate();
    }

    /**
     * Helper method to construct an item representing the results of the expression when all that
     * the expression itself offers is a process() method.
     *
     * @param context the dynamic evaluation context
     * @return an iterator over the results of the expression
     * @throws XPathException if a dynamic error occurs
     */

    public static Item getItemFromProcessMethod(Expression exp, XPathContext context) throws XPathException {
        Controller controller = context.getController();
        assert controller != null;
        SequenceReceiver saved = context.getReceiver();
        SequenceOutputter seq = controller.allocateSequenceOutputter(1);
        seq.getPipelineConfiguration().setHostLanguage(exp.getPackageData().getHostLanguage());
        context.setReceiver(seq);

        exp.process(context);

        context.setReceiver(saved);
        seq.close();
        Item result = seq.getFirstItem();
        seq.reset();
        return result;
    }

    public static interface ExpressionPredicate {
        public boolean matches(Expression e);
    }

    /**
     * Allocate slot numbers to range variables
     *
     * @param exp      the expression whose range variables need to have slot numbers assigned
     * @param nextFree the next slot number that is available for allocation
     * @param frame    a SlotManager object that is used to track the mapping of slot numbers
     *                 to variable names for debugging purposes. May be null.
     * @return the next unallocated slot number.
     */

    public static int allocateSlots(Expression exp, int nextFree, SlotManager frame) {

        if (exp instanceof Assignation) {
            ((Assignation) exp).setSlotNumber(nextFree);
            int count = ((Assignation) exp).getRequiredSlots();
            nextFree += count;
            if (frame != null) {
                frame.allocateSlotNumber(((Assignation) exp).getVariableQName());
            }
        }
        if (exp instanceof LocalParam && ((LocalParam) exp).getSlotNumber() < 0) {
            ((LocalParam) exp).setSlotNumber(nextFree++);
        }

        if (exp instanceof FLWORExpression) {
            for (Clause c : ((FLWORExpression) exp).getClauseList()) {
                for (LocalVariableBinding b : c.getRangeVariables()) {
                    b.setSlotNumber(nextFree++);
                    frame.allocateSlotNumber(b.getVariableQName());
                }
            }
        }
        if (exp instanceof VariableReference) {
            VariableReference var = (VariableReference) exp;
            Binding binding = var.getBinding();
            if (exp instanceof LocalVariableReference) {
                ((LocalVariableReference) var).setSlotNumber(((LocalBinding) binding).getLocalSlotNumber());
            }
            if (binding instanceof Assignation && ((LocalBinding) binding).getLocalSlotNumber() < 0) {
                // This indicates something badly wrong: we've found a variable reference on the tree, that's
                // bound to a variable declaration that is no longer on the tree. All we can do is print diagnostics.
                // The most common reason for this failure is that the declaration of the variable was removed
                // from the tree in the mistaken belief that there were no references to the variable. Variable
                // references are counted during the typeCheck phase, so this can happen if typeCheck() fails to
                // visit some branch of the expression tree.
                Assignation decl = (Assignation) binding;
                Logger err;
                try {
                    err = exp.getConfiguration().getLogger();
                } catch (Exception ex) {
                    err = new StandardLogger();
                }
                String msg = "*** Internal Saxon error: local variable encountered whose binding has been deleted";
                err.error(msg);
                err.error("Variable name: " + decl.getVariableName());
                err.error("Line number of reference: " + var.getLocation().getLineNumber() + " in " + var.getLocation().getSystemId());
                err.error("Line number of declaration: " + decl.getLocation().getLineNumber() + " in " + decl.getLocation().getSystemId());
                err.error("DECLARATION:");
                try {
                    decl.explain(err);
                } catch (Exception e) {
                    // ignore the secondary error
                }
                throw new IllegalStateException(msg);
            }

        }

        if (exp instanceof Pattern) {
            nextFree = ((Pattern) exp).allocateSlots(frame, nextFree);
        } else {
            for (Operand o : exp.operands()) {
                nextFree = allocateSlots(o.getChildExpression(), nextFree, frame);
            }
        }
        //#if PE==true || EE==true
//        if (exp instanceof NextIteration) {
//            WithParam.setSlotNumbers(((NextIteration)exp).getParameters());
//        }
        if (exp instanceof IterateInstr) {
            nextFree = ((IterateInstr) exp).allocateParameterSlots(nextFree);
        }
//#endif
//#if PE==true || EE==true
//        if (exp instanceof NextIteration) {
//            WithParam.setSlotNumbers(((NextIteration)exp).getParameters());
//        }
        if (exp instanceof IterateInstr) {
            nextFree = ((IterateInstr) exp).allocateParameterSlots(nextFree);
        }
//#endif
        return nextFree;

        // Note, we allocate a distinct slot to each range variable, even if the
        // scopes don't overlap. This isn't strictly necessary, but might help
        // debugging.
    }

    /**
     * Determine the effective boolean value of a sequence, given an iterator over the sequence
     *
     * @param iterator An iterator over the sequence whose effective boolean value is required
     * @return the effective boolean value
     * @throws XPathException if a dynamic error occurs
     */
    public static boolean effectiveBooleanValue(SequenceIterator iterator) throws XPathException {
        Item first = iterator.next();
        if (first == null) {
            return false;
        }
        if (first instanceof NodeInfo) {
            iterator.close();
            return true;
        } else if (first instanceof AtomicValue) {
            if (first instanceof BooleanValue) {
                if (iterator.next() != null) {
                    ebvError("a sequence of two or more items starting with a boolean");
                }
                return ((BooleanValue) first).getBooleanValue();
            } else if (first instanceof StringValue) {   // includes anyURI value
                if (iterator.next() != null) {
                    ebvError("a sequence of two or more items starting with a string");
                }
                return !((StringValue) first).isZeroLength();
            } else if (first instanceof NumericValue) {
                if (iterator.next() != null) {
                    ebvError("a sequence of two or more items starting with a numeric value");
                }
                final NumericValue n = (NumericValue) first;
                return (n.compareTo(0) != 0) && !n.isNaN();
            } else {
                ebvError("a sequence starting with an atomic value other than a boolean, number, string, or URI");
                return false;
            }
        } else if (first instanceof Function) {
            ebvError("a sequence starting with a function");
            return false;
        } else if (first instanceof ObjectValue) {
            if (iterator.next() != null) {
                ebvError("a sequence of two or more items starting with an external object value");
            }
            return true;
        }
        ebvError("a sequence starting with an item of unknown kind");
        return false;
    }

    /**
     * Determine the effective boolean value of a single item
     *
     * @param item the item whose effective boolean value is required
     * @return the effective boolean value
     * @throws XPathException if a dynamic error occurs
     */
    public static boolean effectiveBooleanValue(Item item) throws XPathException {
        if (item == null) {
            return false;
        }
        if (item instanceof NodeInfo) {
            return true;
        } else {
            if (item instanceof BooleanValue) {
                return ((BooleanValue) item).getBooleanValue();
            } else if (item instanceof StringValue) {   // includes anyURI value
                return !((StringValue) item).isZeroLength();
            } else if (item instanceof NumericValue) {
                final NumericValue n = (NumericValue) item;
                return (n.compareTo(0) != 0) && !n.isNaN();
            } else if (item instanceof ObjectValue) {
                // return true if external object reference is not null
                return ((ObjectValue) item).getObject() != null;
            } else {
                ebvError("an atomic value other than a boolean, number, string, or URI");
                return false;
            }
        }
    }

    /**
     * Report an error in computing the effective boolean value of an expression
     *
     * @param reason the nature of the error
     * @throws XPathException always
     */

    public static void ebvError(String reason) throws XPathException {
        XPathException err = new XPathException("Effective boolean value is not defined for " + reason);
        err.setErrorCode("FORG0006");
        err.setIsTypeError(true);
        throw err;
    }

    /**
     * Ask whether an expression has a dependency on the focus
     *
     * @param exp the expression
     * @return true if the value of the expression depends on the context item, position, or size
     */

    public static boolean dependsOnFocus(Expression exp) {
        return (exp.getDependencies() & StaticProperty.DEPENDS_ON_FOCUS) != 0;
    }

    /**
     * Determine whether an expression depends on any one of a set of variables
     *
     * @param exp         the expression being tested
     * @param bindingList the set of variables being tested
     * @return true if the expression depends on one of the given variables
     */

    public static boolean dependsOnVariable(Expression exp, final Binding[] bindingList) {
        return !(bindingList == null || bindingList.length == 0) &&
                contains(exp, false, new ExpressionPredicate() {
                    public boolean matches(Expression e) {
                        if (e instanceof VariableReference) {
                            for (Binding binding : bindingList) {
                                if (((VariableReference) e).getBinding() == binding) {
                                    return true;
                                }
                            }
                        }
                        return false;
                    }
                });
    }

    /**
     * Gather a list of all the variable bindings on which a given expression depends
     *
     * @param e    the expression being tested
     * @param list a list to which the bindings are to be added. The items in this list must
     *             implement {@link Binding}
     */

    public static void gatherReferencedVariables(Expression e, List<Binding> list) {
        if (e instanceof VariableReference) {
            Binding binding = ((VariableReference) e).getBinding();
            if (!list.contains(binding)) {
                list.add(binding);
            }
        } else {
            for (Operand o : e.operands()) {
                if (!o.getOperandRole().isInChoiceGroup()) {
                    gatherReferencedVariables(o.getChildExpression(), list);
                }
            }
        }
    }

    /**
     * Determine whether the expression contains any variable references or calls to user-written functions
     *
     * @param exp the expression being tested
     * @return true if the expression includes a variable reference or function call, or an XSLT construct
     * equivalent to a function call (e.g call-template). Also returns true if the expression includes
     * a variable binding element, as (a) this is likely to mean it also contains a reference, and (b)
     * it also needs to be caught on the same paths.
     */

    public static boolean refersToVariableOrFunction(Expression exp) {
        return contains(exp, false, new ExpressionPredicate() {
            public boolean matches(Expression e) {
                return e instanceof VariableReference || e instanceof UserFunctionCall || e instanceof Binding
                        || e instanceof CallTemplate || e instanceof ApplyTemplates
                        || e instanceof ApplyImports;
                // TODO: what about dynamic function calls and function-lookup?
            }
        });
//        if (e instanceof VariableReference || e instanceof UserFunctionCall || e instanceof Binding
//                || e instanceof CallTemplate || e instanceof ApplyTemplates || e instanceof ApplyImports || e instanceof NextMatch) {
//            return true;
//        } else {
//            for (Iterator children = e.iterateSubExpressions(); children.hasNext(); ) {
//                Expression child = (Expression) children.next();
//                if (refersToVariableOrFunction(child)) {
//                    return true;
//                }
//            }
//        }
//        return false;
    }

    /**
     * Determine whether an expression contains a call on the function with a given name
     *
     * @param exp           The expression being tested
     * @param qName         The name of the function
     * @param sameFocusOnly If true, only expressions with the same (top-level) focus are searched
     * @return true if the expression contains a call on the function
     */

    public static boolean callsFunction(Expression exp, final StructuredQName qName, boolean sameFocusOnly) {
        return contains(exp, sameFocusOnly, new ExpressionPredicate() {
            public boolean matches(Expression e) {
                return e instanceof FunctionCall && qName.equals(((FunctionCall) e).getFunctionName());
            }
        });
    }

    /**
     * Determine whether an expression has a subexpression of a given implementation class
     *
     * @param exp      The expression being tested
     * @param subClass The impementation class of the sought subexpression
     * @return true if the expression contains a subexpression that is an instance of the specified class
     */

    public static boolean containsSubexpression(Expression exp, final Class<? extends Expression> subClass) {
        return contains(exp, false, new ExpressionPredicate() {
            public boolean matches(Expression e) {
                return subClass.isAssignableFrom(e.getClass());
            }
        });
    }


    /**
     * Gather a list of all the user-defined functions which a given expression calls directly
     *
     * @param e    the expression being tested
     * @param list a list of the functions that are called. The items in this list must
     *             be objects of class {@link UserFunction}
     */

    public static void gatherCalledFunctions(Expression e, List<UserFunction> list) {
        if (e instanceof UserFunctionCall) {
            UserFunction function = ((UserFunctionCall) e).getFunction();
            if (!list.contains(function)) {
                list.add(function);
            }
        } else {
            for (Operand o : e.operands()) {
                gatherCalledFunctions(o.getChildExpression(), list);
            }
        }
    }

    /**
     * Gather a list of the names of the user-defined functions which a given expression calls directly
     *
     * @param e    the expression being tested
     * @param list a list of the functions that are called. The items in this list are strings in the format
     *             "{uri}local/arity"
     */

    public static void gatherCalledFunctionNames(Expression e, List<SymbolicName> list) {
        if (e instanceof UserFunctionCall) {
            list.add(((UserFunctionCall) e).getSymbolicName());
        } else {
            for (Operand o : e.operands()) {
                gatherCalledFunctionNames(o.getChildExpression(), list);
            }
        }
    }

    /**
     * Optimize the implementation of a component such as a function, template, or global variable
     *
     * @param body           the expression forming the body of the component
     * @param compilation    the current compilation
     * @param visitor        the expression visitor
     * @param cit            information about the context item for evaluation of the component body
     * @param extractGlobals true if constant expressions are to be extracted as global variables
     * @return the optimized expression body
     * @throws XPathException if anything goes wrong
     */

    public static Expression optimizeComponentBody(
            Expression body, final Compilation compilation, ExpressionVisitor visitor, ContextItemStaticInfo cit, boolean extractGlobals)
            throws XPathException {
        final Configuration config = visitor.getConfiguration();
        Optimizer opt = config.obtainOptimizer();
        StaticContext env = visitor.getStaticContext();
        boolean compileWithTracing = config.isCompileWithTracing();
        if (!compileWithTracing) {
            // Bug 3496 - desperate attempts to discover whether tracing was enabled for this particular compilation
            if (compilation != null) {
                compileWithTracing = compilation.getCompilerInfo().isCompileWithTracing();
            } else if (env instanceof QueryModule) {
                compileWithTracing = ((QueryModule)env).getUserQueryContext().isCompileWithTracing();
            } else if (env instanceof ExpressionContext) {
                compileWithTracing = ((ExpressionContext)env).getStyleElement().getCompilation().getCompilerInfo().isCompileWithTracing();
            }
        }
        if (opt.isOptionSet(OptimizerOptions.MISCELLANEOUS) && !compileWithTracing) {
            ExpressionTool.resetPropertiesWithinSubtree(body);
            body = body.optimize(visitor, cit);
            body.setParentExpression(null);
            if (opt.isOptionSet(OptimizerOptions.LOOP_LIFTING)) {
                body = LoopLifter.process(body, visitor, cit);
            }
            if (extractGlobals && compilation != null) {
                Expression exp2 = opt.promoteExpressionsToGlobal(body, compilation.getPrincipalStylesheetModule(), visitor);
                if (exp2 != null) {
                    // Try another optimization pass: extracting global variables can identify things that are indexable
                    ExpressionTool.resetPropertiesWithinSubtree(exp2);
                    body = exp2.optimize(visitor, cit);
                }
            }
        } else {
            body = avoidDocumentSort(body);
        }

        if (!visitor.isOptimizeForStreaming()) {
            body = opt.eliminateCommonSubexpressions(body);
        }
        opt.injectByteCodeCandidates(body);

        //#if EE==true
        if (compilation != null) {
            ExpressionTool.processExpressionTree(body, null, new ExpressionAction() {
                public boolean process(Expression expression, Object result) throws XPathException {
                    if (expression instanceof StreamInstr) {
//                        List<String> reasons = new ArrayList<String>();
//                        ((StreamInstr) expression).checkStreamability(reasons, config.getStreamability());
                        ((StreamInstr) expression).prepareForStreaming(compilation.getPackageData());
                    }
                    return false;
                }
            });
        }
        //#endif
        computeEvaluationModesForUserFunctionCalls(body);
        body.restoreParentPointers();
        return body;
    }

    /**
     * An optimization-lite pass over the expression tree that attempts to eliminate unnecessary
     * sorting into document order. We avoid a full optimization when STRICT_STREAMABILITY analysis
     * is requested, because it rewrites non-streamable expressions into streamable expressions;
     * but to get the streamability right, we need as a minimum to eliminate unwanted document sort
     * operations.
     */

    private static Expression avoidDocumentSort(Expression exp) {
        if (exp instanceof DocumentSorter) {
            Expression base = ((DocumentSorter) exp).getBaseExpression();
            if ((base.getSpecialProperties() & StaticProperty.ORDERED_NODESET) != 0) {
                return base;
            }
            return exp;
        } else if (exp instanceof ConditionalSorter) {
            DocumentSorter sorter = ((ConditionalSorter)exp).getDocumentSorter();
            Expression eliminatedSorter = avoidDocumentSort(sorter);
            if (eliminatedSorter != sorter) {
                return eliminatedSorter;
            }
        }
        for (Operand o : exp.operands()) {
            o.setChildExpression(avoidDocumentSort(o.getChildExpression()));
        }
        return exp;
    }

    /**
     * Compute argument evaluation modes for all calls on user defined functions with
     * a specified expression
     */

    public static void computeEvaluationModesForUserFunctionCalls(Expression exp) throws XPathException {
        ExpressionTool.processExpressionTree(exp, null, new ExpressionAction() {
            public boolean process(Expression expression, Object result) throws XPathException {
                if (expression instanceof UserFunctionCall) {
                    ((UserFunctionCall) expression).computeArgumentEvaluationModes();
                }
                return false;
            }
        });
    }

    /**
     * Clear all computed streamability properties for an expression and its contained subtree
     * @param exp the expression whose streamability data is to be reset
     * @throws XPathException should not happen
     */

    public static void clearStreamabilityData(Expression exp) throws XPathException {
        ExpressionTool.processExpressionTree(exp, null, new ExpressionAction() {
            public boolean process(Expression expression, Object result) throws XPathException {
                expression.setExtraProperty("P+S", null);
                expression.setExtraProperty("inversion", null);
                return false;
            }
        });
    }

    /**
     * Reset cached static properties within a subtree, meaning that they have to be
     * recalulated next time they are required
     *
     * @param exp the root of the subtree within which static properties should be reset
     */

    public static void resetPropertiesWithinSubtree(Expression exp) {
        exp.resetLocalStaticProperties();
        if (exp instanceof LocalVariableReference) {
            LocalVariableReference ref = (LocalVariableReference) exp;
            Binding binding = ref.getBinding();
            if (binding instanceof Assignation) {
                binding.addReference(ref, ref.isInLoop());
            }
        }
        for (Operand o : exp.operands()) {
            resetPropertiesWithinSubtree(o.getChildExpression());
            o.getChildExpression().setParentExpression(exp);
        }

    }

    /**
     * Resolve calls to the XSLT current() function within an expression
     *
     * @param exp the expression within which calls to current() should be resolved
     * @return the expression after resolving calls to current()
     * @throws XPathException if a static error is detected
     */

    public static Expression resolveCallsToCurrentFunction(Expression exp)
            throws XPathException {
        if (exp.isCallOn(Current.class)) {
            ContextItemExpression cie = new ContextItemExpression();
            copyLocationInfo(exp, cie);
            return cie;
        } else {
            if (callsFunction(exp, Current.FN_CURRENT, true)) {
                // replace trivial (same-focus) calls to current by a simple "."
                replaceTrivialCallsToCurrent(exp);
            }
            if (callsFunction(exp, Current.FN_CURRENT, false)) {
                // replace non-trivial (different-focus) calls to current by a variable reference
                LetExpression let = new LetExpression();
                let.setVariableQName(
                        new StructuredQName("vv", NamespaceConstant.SAXON_GENERATED_VARIABLE, "current" + exp.hashCode()));
                let.setRequiredType(SequenceType.SINGLE_ITEM);
                let.setSequence(new CurrentItemExpression());
                replaceCallsToCurrent(exp, let);
                let.setAction(exp);
                return let;
            } else {
                return exp;
            }
        }
    }

    /**
     * Get a list of all references to a particular variable within a subtree
     *
     * @param exp     the expression at the root of the subtree
     * @param binding the variable binding whose references are sought
     * @param list    a list to be populated with the references to this variable
     */

    public static void gatherVariableReferences(Expression exp, Binding binding, List<VariableReference> list) {
        if (exp instanceof VariableReference &&
                ((VariableReference) exp).getBinding() == binding) {
            list.add((VariableReference) exp);
        } else {
            for (Operand o : exp.operands()) {
                gatherVariableReferences(o.getChildExpression(), binding, list);
            }
        }
    }

    /**
     * Process every node on a subtree of the expression tree using a supplied action.
     *
     * @param root   the root of the subtree to be processed
     * @param result an arbitrary object that is passed to each action call and that can be
     *               updated to gather results of the processing
     * @param action an action to be performed on each node of the expression tree. Processing
     *               stops if any action returns the value true
     * @return true if any call on the action operand returned true
     * @throws XPathException
     */

    public static boolean processExpressionTree(Expression root, Object result, ExpressionAction action) throws XPathException {
        boolean done = action.process(root, result);
        if (!done) {
            for (Operand o : root.operands()) {
                done = processExpressionTree(o.getChildExpression(), result, action);
                if (done) {
                    return true;
                }
            }
        }
        return false;
    }

//    /**
//     * Process all references to a particular variable within a subtree
//     *
//     * @param exp     the expression at the root of the subtree
//     * @param binding the variable binding whose references are sought
//     * @param action  the action to be applied to all references to this variable
//     * @return true if processing finished early at the request of an action invocation; false if processing ran to completion
//     */
//
//    public static boolean processVariableReferences(Expression exp, Binding binding, ExpressionAction<VariableReference> action) throws XPathException {
//        if (exp instanceof VariableReference && ((VariableReference) exp).getBinding() == binding) {
//            return action.process((VariableReference) exp);
//        } else {
//            for (Operand o : exp.operands()) {
//                boolean done = processVariableReferences(o.getChildExpression(), binding, action);
//                if (done) {
//                    return true;
//                }
//            }
//            return false;
//        }
//    }

    /**
     * Callback for selecting expressions in the tree
     */

    public interface ExpressionSelector {
        boolean matches(Expression exp);
    }

    /**
     * Replace all selected subexpressions within a subtree
     *
     * @param exp         the expression at the root of the subtree
     * @param selector    callback to determine whether a subexpression is selected
     * @param replacement the expression to be used in place of the variable reference
     * @param mustCopy    if true, the replacement expression must be copied before use
     * @return true if a replacement has been performed (in which case the replacement expression
     * must be copied before being used again).
     */

    public static boolean replaceSelectedSubexpressions(
            Expression exp, ExpressionSelector selector, Expression replacement, boolean mustCopy) {
        boolean replaced = false;
        for (Operand o : exp.operands()) {
            if (replaced) {
                mustCopy = true;
            }
            Expression child = o.getChildExpression();
            if (selector.matches(child)) {
                Expression e2 = mustCopy ? replacement.copy(new RebindingMap()) : replacement;
                o.setChildExpression(e2);
                replaced = true;
            } else {
                replaced = replaceSelectedSubexpressions(child, selector, replacement, mustCopy);
            }
        }
        return replaced;
    }

    /**
     * Replace all references to a particular variable within a subtree
     *
     * @param exp         the expression at the root of the subtree
     * @param binding     the variable binding whose references are sought
     * @param replacement the expression to be used in place of the variable reference
     * @param mustCopy    true if the replacement expression must be copied before use
     */

    public static void replaceVariableReferences(Expression exp, final Binding binding, Expression replacement, boolean mustCopy) {
        ExpressionSelector selector = new ExpressionSelector() {
            public boolean matches(Expression child) {
                return child instanceof VariableReference && ((VariableReference) child).getBinding() == binding;
            }
        };
        replaceSelectedSubexpressions(exp, selector, replacement, mustCopy);
    }

    /**
     * Determine how often a variable is referenced. This is the number of times
     * it is referenced at run-time: so a reference in a loop counts as "many". This code
     * currently handles local variables (Let expressions) and function parameters. It is
     * not currently used for XSLT template parameters. It's not the end of the world if
     * the answer is wrong (unless it's wrongly given as zero), but if wrongly returned as
     * 1 then the variable will be repeatedly evaluated.
     *
     * @param exp     the expression within which variable references are to be counted
     * @param binding identifies the variable of interest
     * @param inLoop  true if the expression is within a loop, in which case a reference counts as many.
     *                This should be set to false on the initial call, it may be set to true on an internal recursive
     *                call
     * @return the number of references. The interesting values are 0, 1,  "many" (represented
     * by any value >1), and the special value FILTERED, which indicates that there are
     * multiple references and one or more of them is of the form $x[....] indicating that an
     * index might be useful.
     */

    public static int getReferenceCount(Expression exp, Binding binding, boolean inLoop) {
        int rcount = 0;
        if (exp instanceof VariableReference && ((VariableReference) exp).getBinding() == binding) {
            if (((VariableReference) exp).isFiltered()) {
                return FilterExpression.FILTERED;
            } else {
                rcount += inLoop ? 10 : 1;
            }
        } else if ((exp.getDependencies() & StaticProperty.DEPENDS_ON_LOCAL_VARIABLES) == 0) {
            return 0;
        } else {
            for (Operand info : exp.operands()) {
                Expression child = info.getChildExpression();
                boolean childLoop = inLoop || info.isEvaluatedRepeatedly();
                rcount += getReferenceCount(child, binding, childLoop);
                if (rcount >= FilterExpression.FILTERED) {
                    break;
                }
            }
        }
        return rcount;
    }

    /**
     * Get the size of an expression tree (the number of subexpressions it contains)
     *
     * @param exp the expression whose size is required
     * @return the size of the expression tree, as the number of nodes
     */

    public static int expressionSize(Expression exp) {
        exp = exp.getInterpretedExpression();
        int total = 1;
        for (Operand o : exp.operands()) {
            total += expressionSize(o.getChildExpression());
        }
        return total;
    }


    /**
     * Rebind all variable references to a binding
     *
     * @param exp        the expression whose contained variable references are to be rebound
     * @param oldBinding the old binding for the variable references
     * @param newBinding the new binding to which the variables should be rebound
     */

    public static void rebindVariableReferences(
            Expression exp, Binding oldBinding, Binding newBinding) {
        if (exp instanceof VariableReference) {
            if (((VariableReference) exp).getBinding() == oldBinding) {
                ((VariableReference) exp).fixup(newBinding);
            }
        } else {
            for (Operand o : exp.operands()) {
                rebindVariableReferences(o.getChildExpression(), oldBinding, newBinding);
            }
        }
    }

    /**
     * Make a mapping expression. The resulting expression will include logic to check that the first operand
     * returns nodes, and that the expression as a whole is homogeneous, unless the caller requests otherwise.
     *
     * @param start              the start expression (the first operand of "/")
     * @param step               the step expression (the second operand of "/")
     * @param sortAndDeduplicate set to true if this is a path expression ("/") where the result needs to be
     *                           homogenous, and needs to be sorted and deduplicated if it consists of nodes. Set to false if the caller
     *                           knows that this is not necessary (typically because it has already been checked).
     * @return the resulting expression.
     */

    /*@NotNull*/
    public static Expression makePathExpression(
            /*@NotNull*/ Expression start, /*@NotNull*/ Expression step, boolean sortAndDeduplicate) {

//        if (sortAndDeduplicate) {
//            // if the final expression is to be sorted and deduplicated, then there is no need to apply this process
//            // to the results of subexpressions
        // code removed 2011-11-01 MHK - causes test expr36 to fail
//            start = removeSorting(start);
//            step = removeSorting(step);
//        }

        // the expression /.. is sometimes used to represent the empty node-set. Applying this simplification
        // now avoids generating warnings for this case.
        if (start instanceof RootExpression &&
                step instanceof AxisExpression && ((AxisExpression) step).getAxis() == AxisInfo.PARENT) {
            return Literal.makeEmptySequence();
        }

        SlashExpression expr = new SlashExpression(start, step);

        // If start is a path expression such as a, and step is b/c, then
        // instead of a/(b/c) we construct (a/b)/c. This is because it often avoids
        // a sort.

        // The "/" operator in XPath 2.0 is not always left-associative. Problems
        // can occur if position() and last() are used on the rhs, or if node-constructors
        // appear, e.g. //b/../<d/>. So we only do this rewrite if the step is a path
        // expression in which both operands are axis expressions optionally with predicates

        if (step instanceof SlashExpression) {
            SlashExpression stepPath = (SlashExpression) step;
            if (isFilteredAxisPath(stepPath.getSelectExpression()) && isFilteredAxisPath(stepPath.getActionExpression())) {
                expr.setStart(ExpressionTool.makePathExpression(start, stepPath.getSelectExpression(), false));
                expr.setStep(stepPath.getActionExpression());
            }
        }

        if (sortAndDeduplicate) {
            // The HomogeneityChecker not only checks homogeneity, but also sorts nodes into document order
            return new HomogeneityChecker(expr);
        } else {
            return expr;
        }
    }

    public static Expression makeRawPathExpression(
            /*@NotNull*/ Expression start, /*@NotNull*/ Expression step, boolean sortAndDeduplicate) {

        return new RawSlashExpression(start, step);
    }


    /**
     * Find the operand corresponding to a particular child expression
     *
     * @param parentExpression the parent expression
     * @param childExpression  the child expression
     * @return the relevant operand, or null if not found
     */

    public static Operand findOperand(Expression parentExpression, Expression childExpression) {
        for (Operand o : parentExpression.operands()) {
            if (o.getChildExpression() == childExpression) {
                return o;
            }
        }
        return null;
    }

    /**
     * Determine whether an expression is an axis step with optional filter predicates.
     *
     * @param exp the expression to be examined
     * @return true if the supplied expression is an AxisExpression, or an AxisExpression wrapped by one
     * or more filter expressions
     */

    private static boolean isFilteredAxisPath(Expression exp) {
        return unfilteredExpression(exp, true) instanceof AxisExpression;
    }

    /**
     * Get the expression that remains after removing any filter predicates
     *
     * @param exp             the expression to be examined
     * @param allowPositional true if positional predicates are allowed
     * @return the expression underlying exp after removing any predicates
     */

    public static Expression unfilteredExpression(Expression exp, boolean allowPositional) {
        if (exp instanceof FilterExpression && (allowPositional || !((FilterExpression) exp).isFilterIsPositional())) {
            return unfilteredExpression(((FilterExpression) exp).getSelectExpression(), allowPositional);
        } else if (exp instanceof SingleItemFilter && allowPositional) {
            return unfilteredExpression(((SingleItemFilter) exp).getBaseExpression(), allowPositional);
        } else {
            return exp;
        }
    }

    /**
     * Try to factor out dependencies on the context item, by rewriting an expression f(.) as
     * let $dot := . return f($dot). This is not always possible, for example where f() is an extension
     * function call that uses XPathContext as an implicit argument. However, doing this increases the
     * chances of distributing a "where" condition in a FLWOR expression to the individual input sequences
     * selected by the "for" clauses.
     *
     * @param exp             the expression from which references to "." should be factored out if possible
     * @param contextItemType the static type of the context item
     * @return either the expression, after binding "." to a local variable and replacing all references to it;
     * or null, if no changes were made.
     */

    public static Expression tryToFactorOutDot(Expression exp, ItemType contextItemType) {
        if (exp instanceof ContextItemExpression) {
            return null;
        } else if (exp instanceof LetExpression && ((LetExpression) exp).getSequence() instanceof ContextItemExpression) {
            Expression action = ((LetExpression) exp).getAction();
            boolean changed = factorOutDot(action, (LetExpression) exp);
            if (changed) {
                exp.resetLocalStaticProperties();
            }
            return exp;
        } else if ((exp.getDependencies() &
                (StaticProperty.DEPENDS_ON_CONTEXT_ITEM | StaticProperty.DEPENDS_ON_CONTEXT_DOCUMENT)) != 0) {
            LetExpression let = new LetExpression();
            let.setVariableQName(
                    new StructuredQName("saxon", NamespaceConstant.SAXON, "dot" + exp.hashCode()));
            let.setRequiredType(SequenceType.makeSequenceType(contextItemType, StaticProperty.EXACTLY_ONE));
            let.setSequence(new ContextItemExpression());
            let.setAction(exp);
            boolean changed = factorOutDot(exp, let);
            if (changed) {
                return let;
            } else {
                return exp;
            }
        } else {
            return null;
        }
    }

    /**
     * Replace references to the context item with references to a variable
     *
     * @param exp      the expression in which the replacement is to take place
     * @param variable the declaration of the variable
     * @return true if replacement has taken place (at any level)
     */

    public static boolean factorOutDot(Expression exp, Binding variable) {
        boolean changed = false;
        if ((exp.getDependencies() &
                (StaticProperty.DEPENDS_ON_CONTEXT_ITEM | StaticProperty.DEPENDS_ON_CONTEXT_DOCUMENT)) != 0) {
            for (Operand info : exp.operands()) {
                if (info.hasSameFocus()) {
                    Expression child = info.getChildExpression();
                    if (child instanceof ContextItemExpression) {
                        VariableReference ref = variable.isGlobal() ?
                                new GlobalVariableReference((GlobalVariable) variable) :
                                new LocalVariableReference((LocalBinding) variable);
                        copyLocationInfo(child, ref);
                        info.setChildExpression(ref);
                        changed = true;
                    } else if (child instanceof AxisExpression ||
                            child instanceof RootExpression) {
                        VariableReference ref = variable.isGlobal() ?
                                new GlobalVariableReference((GlobalVariable) variable) :
                                new LocalVariableReference((LocalBinding) variable);
                        copyLocationInfo(child, ref);
                        Expression path = ExpressionTool.makePathExpression(ref, child, false);
                        info.setChildExpression(path);
                        changed = true;
                    } else {
                        changed |= factorOutDot(child, variable);
                    }
                }
            }
        }
        if (changed) {
            exp.resetLocalStaticProperties();
        }
        return changed;
    }

    /**
     * Inline variable references.
     *
     * @param expr        the expression containing the variable references to be inlined
     * @param binding     the variable binding to be inlined
     * @param replacement the expression to be used as a replacement for the variable reference
     * @return true if any replacement was carried out within the subtree of this expression
     */

    public static boolean inlineVariableReferences(Expression expr, Binding binding, Expression replacement) {
        return inlineVariableReferencesInternal(expr, binding, replacement);
    }

    public static boolean inlineVariableReferencesInternal(Expression expr, Binding binding, Expression replacement) {
        if (expr instanceof TryCatch && !(replacement instanceof Literal)) {
            // Don't inline variable references within a try/catch, as this will lead to errors in the
            // variable's initializer being incorrectly caught by the catch clause. See XSLT3 test try-029.
            return false;
        } else {
            boolean found = false;
            for (Operand o : expr.operands()) {
                Expression child = o.getChildExpression();
                if (child instanceof VariableReference &&
                        ((VariableReference) child).getBinding() == binding) {
                    Expression copy;
                    try {
                        copy = replacement.copy(new RebindingMap());
                        ExpressionTool.copyLocationInfo(child, copy);
                    } catch (UnsupportedOperationException err) {
                        // If we can't make a copy, return the original. This is safer than it seems,
                        // because on the paths where this happens, we are merely moving the expression from
                        // one place to another, not replicating it
                        copy = replacement;
                    }
                    o.setChildExpression(copy);
                    found = true;
                } else {
                    found |= inlineVariableReferencesInternal(child, binding, replacement);
                }
            }
            if (found) {
                expr.resetLocalStaticProperties();
            }
            return found;
        }
    }

    /**
     * Replace trivial calls to current() by a context item expression ({@code .}).
     *
     * @param expr the expression potentially containing the calls to be replaced
     * @return true if any replacement was carried out within the subtree of this expression
     */

    public static boolean replaceTrivialCallsToCurrent(Expression expr) {
        boolean found = false;
        for (Operand o : expr.operands()) {
            if (o.hasSameFocus()) {
                Expression child = o.getChildExpression();
                if (child.isCallOn(Current.class)) {
                    CurrentItemExpression var = new CurrentItemExpression();
                    ExpressionTool.copyLocationInfo(child, var);
                    o.setChildExpression(var);
                    found = true;
                } else {
                    found = replaceTrivialCallsToCurrent(child);
                }
            }
        }
        if (found) {
            expr.resetLocalStaticProperties();
        }
        return found;
    }

    /**
     * Replace calls to current() by a variable reference.
     *
     * @param expr    the expression potentially containing the calls to be replaced
     * @param binding the variable binding to be referenced
     * @return true if any replacement was carried out within the subtree of this expression
     */

    public static boolean replaceCallsToCurrent(Expression expr, LocalBinding binding) {
        boolean found = false;
        for (Operand o : expr.operands()) {
            Expression child = o.getChildExpression();
            if (child.isCallOn(Current.class)) {
                LocalVariableReference var = new LocalVariableReference(binding);
                ExpressionTool.copyLocationInfo(child, var);
                o.setChildExpression(var);
                binding.addReference(var, true);
                found = true;
            } else {
                found = replaceCallsToCurrent(child, binding);
            }
        }
        if (found) {
            expr.resetLocalStaticProperties();
        }
        return found;
    }


    /**
     * Determine whether the expression is either an updating expression, or an expression that is permitted
     * in a context where updating expressions are allowed
     *
     * @param exp the expression under test
     * @return true if the expression is an updating expression, or an empty sequence, or a call on error()
     */

    public static boolean isAllowedInUpdatingContext(Expression exp) {
        return exp.isUpdatingExpression() || exp.isVacuousExpression();
    }


    public static String getCurrentDirectory() {
        String dir;
        try {
            dir = System.getProperty("user.dir");
        } catch (Exception geterr) {
            // this doesn't work when running an applet
            return null;
        }
        if (!dir.endsWith("/")) {
            dir = dir + '/';
        }

        URI currentDirectoryURL = new File(dir).toURI();
        return currentDirectoryURL.toString();
    }

    /**
     * Determine the base URI of an expression, so that it can be saved on the expression tree for use
     * when the expression is evaluated
     *
     * @param env     the static context
     * @param locator location of the expression for error messages
     * @param fail    if true, the method throws an exception when there is no absolute base URI; otherwise, the
     *                method returns null
     * @return the absolute base URI of the expression
     * @throws net.sf.saxon.trans.XPathException if an error occurs
     */

    public static URI getBaseURI(StaticContext env, SourceLocator locator, boolean fail) throws XPathException {
        URI expressionBaseURI = null;
        String base = null;
        try {
            base = env.getStaticBaseURI();
            if (base == null) {
                base = getCurrentDirectory();
            }
            if (base != null) {
                expressionBaseURI = new URI(base);
            }
        } catch (URISyntaxException e) {
            // perhaps escaping special characters will fix the problem

            String esc = IriToUri.iriToUri(base).toString();
            try {
                expressionBaseURI = new URI(esc);
            } catch (URISyntaxException e2) {
                // don't fail unless the base URI is actually needed (it usually isn't)
                expressionBaseURI = null;
            }

            if (expressionBaseURI == null && fail) {
                XPathException err = new XPathException("The base URI " + Err.wrap(env.getStaticBaseURI(), Err.URI) +
                                                                " is not a valid URI");
                err.setLocator(locator);
                throw err;
            }
        }
        return expressionBaseURI;
    }

    /**
     * Display an expression adding parentheses if it is possible they are necessary
     * because the expression has sub-expressions
     *
     * @param exp the expression to be displayed
     * @return a representation of the expression in parentheses
     */

    public static String parenthesize(Expression exp) {
        if (exp.operands().iterator().hasNext()) {
            return "(" + exp.toString() + ")";
        } else {
            return exp.toString();
        }
    }

    public static void validateTree(Expression exp) {
        try {
            for (Operand o : exp.checkedOperands()) {
                validateTree(o.getChildExpression());
            }
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
    }

    /**
     * Ask whether a supplied expression is a nested node constructor. That is, return true if the child expression
     * creates nodes that will only be used as children of some parent node (meaning that they never need to be copied).
     * @param child child expression to be tested
     * @return true if the node constructed by the child expression does not need to be copied.
     */

    public static boolean isLocalConstructor(Expression child) {
        if (!(child instanceof ParentNodeConstructor || child instanceof SimpleNodeConstructor)) {
            return false;
        }
        Expression parent = child.getParentExpression();
        while (parent != null) {
            if (parent instanceof ParentNodeConstructor) {
                return true;
            }
            Operand o = findOperand(parent, child);
            if (o.getUsage() != OperandUsage.TRANSMISSION) {
                return false;
            }
            child = parent;
            parent = parent.getParentExpression();
        }
        return false;
    }
}

