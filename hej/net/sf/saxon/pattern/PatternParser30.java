////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.instruct.Choose;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.functions.Doc;
import net.sf.saxon.functions.KeyFn;
import net.sf.saxon.functions.Root_1;
import net.sf.saxon.functions.SuperId;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.NumericType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.SequenceType;

/**
 * Parser for XSLT patterns. This is created by overriding selected parts of the standard ExpressionParser.
 */

public class PatternParser30 extends XPathParser implements PatternParser {

    int inPredicate = 0;

    /**
     * Parse a string representing an XSLT pattern
     *
     *
     * @param pattern the pattern expressed as a String
     * @param env     the static context for the pattern
     * @param lineNumber
     * @return a Pattern object representing the result of parsing
     * @throws net.sf.saxon.trans.XPathException
     *          if the pattern contains a syntax error
     */

    /*@NotNull*/
    public Pattern parsePattern(String pattern, StaticContext env, int lineNumber) throws XPathException {
        this.env = env;
        charChecker = env.getConfiguration().getValidCharacterChecker();
        language = XSLT_PATTERN;
        String trimmed = pattern.trim();
        if (trimmed.startsWith("(:")) {
            Tokenizer t = new Tokenizer();
            t.languageLevel = 30;
            t.tokenize(trimmed, 0, -1);
            int start = t.currentTokenStartOffset;
            trimmed = trimmed.substring(start);
        }
        if (trimmed.startsWith(".")) {
            Expression e = parse(pattern, 0, Token.EOF, env);
            if (e instanceof ContextItemExpression) {
                return new UniversalPattern();
            } else if (e instanceof FilterExpression) {
                Expression predicate = null;
                while (e instanceof FilterExpression) {
                    Expression filter = ((FilterExpression) e).getActionExpression();
                    e = ((FilterExpression) e).getSelectExpression();
                    // Need to consider the possibility of a numeric predicate
                    ItemType filterType = filter.getItemType();
                    TypeHierarchy th = env.getConfiguration().getTypeHierarchy();
                    int rel = th.relationship(filterType, NumericType.getInstance());
                    if (rel != TypeHierarchy.DISJOINT) {
                        // the predicate may be numeric
                        if (rel == TypeHierarchy.SAME_TYPE || rel == TypeHierarchy.SUBSUMED_BY) {
                            // the predicate IS numeric: rewrite as N eq 1, since other values don't match
                            filter = new ValueComparison(filter, Token.FEQ, Literal.makeLiteral(Int64Value.PLUS_ONE));
                        } else {
                            // the predicate MIGHT BE numeric: rewrite as
                            // let $P := predicate return if ($P instance of xs:numeric) then ($P eq 1) else $P
                            LetExpression let = new LetExpression();
                            StructuredQName varName =
                                new StructuredQName("vv", NamespaceConstant.SAXON_GENERATED_VARIABLE, "v" + filter.hashCode());
                            let.setVariableQName(varName);
                            InstanceOfExpression condition =
                                new InstanceOfExpression(new LocalVariableReference(let), SequenceType.SINGLE_NUMERIC);
                            LocalVariableReference ref = new LocalVariableReference(let);
                            ref.setStaticType(SequenceType.SINGLE_NUMERIC, null, 0);
                            ValueComparison comparison =
                                new ValueComparison(ref, Token.FEQ, Literal.makeLiteral(Int64Value.PLUS_ONE));
                            Choose choice = new Choose(new Expression[]{condition, Literal.makeLiteral(BooleanValue.TRUE)},
                                                       new Expression[]{comparison, new LocalVariableReference(let)});
                            let.setSequence(filter);
                            let.setAction(choice);
                            let.setRequiredType(SequenceType.ANY_SEQUENCE);
                            let.setRetainedStaticContext(env.makeRetainedStaticContext());
                            filter = let;
                        }
                    }
                    if (predicate == null) {
                        predicate = filter;
                    } else {
                        predicate = new AndExpression(filter, predicate);
                    }
                }
                if (e instanceof ContextItemExpression) {
                    return new BooleanExpressionPattern(predicate);
                }
            }
            grumble("Pattern starting with '.' must be followed by a sequence of predicates");
            return null;
        } else {
            Expression exp = parse(pattern, 0, Token.EOF, env);
            exp.setRetainedStaticContext(env.makeRetainedStaticContext());

            // If we have a union pattern, check that neither operand is a PredicatePattern
            if (exp instanceof VennExpression) {
                checkNoPredicatePattern(((VennExpression) exp).getLhsExpression());
                checkNoPredicatePattern(((VennExpression) exp).getRhsExpression());
            }
            ExpressionVisitor visitor = ExpressionVisitor.make(env);
            ContextItemStaticInfo cit = visitor.getConfiguration().makeContextItemStaticInfo(AnyItemType.getInstance(), true);
            Pattern pat = PatternMaker.fromExpression(exp.simplify().typeCheck(visitor, cit), env.getConfiguration(), true);
            if (exp instanceof FilterExpression && ((FilterExpression)exp).getBase() instanceof ContextItemExpression) {
                grumble("A predicatePattern can appear only at the outermost level (parentheses not allowed)");
            }
            if (exp instanceof FilterExpression && pat instanceof NodeTestPattern) {
                // the pattern has been simplified but needs to retain a default priority based on its syntactic form (test match89)
                pat.setPriority(0.5);
            }
            return pat;
        }
    }

    private void checkNoPredicatePattern(Expression exp) throws XPathException {
        if (exp instanceof ContextItemExpression) {
            grumble("A predicatePattern can appear only at the outermost level (union operator not allowed)");
        }
        if (exp instanceof FilterExpression) {
            checkNoPredicatePattern(((FilterExpression) exp).getBase());
        }
        if (exp instanceof VennExpression) {
            checkNoPredicatePattern(((VennExpression) exp).getLhsExpression());
            checkNoPredicatePattern(((VennExpression) exp).getRhsExpression());
        }
    }

    /**
     * Callback to tailor the tokenizer
     */

    protected void customizeTokenizer(Tokenizer t) {
        // no action
    }

    /**
     * Override the parsing of top-level expressions
     *
     * @return the parsed expression
     * @throws net.sf.saxon.trans.XPathException
     *
     */

    /*@NotNull*/
    public Expression parseExpression() throws XPathException {
        if (inPredicate > 0) {
            return super.parseExpression();
        } else {
            return parseBinaryExpression(parsePathExpression(), 10);
        }
    }

    /**
     * Parse a basic step expression (without the predicates)
     *
     * @param firstInPattern true only if we are parsing the first step in a
     *                       RelativePathPattern in the XSLT Pattern syntax
     * @return the resulting subexpression
     * @throws net.sf.saxon.trans.XPathException
     *          if any error is encountered
     */

    /*@NotNull*/
    protected Expression parseBasicStep(boolean firstInPattern) throws XPathException {
        if (inPredicate > 0) {
            return super.parseBasicStep(firstInPattern);
        } else {
            switch (t.currentToken) {
                case Token.DOLLAR:
                    if (!firstInPattern) {
                        grumble("In an XSLT 3.0 pattern, a variable reference is allowed only as the first step in a path");
                        return null;
                    } else {
                        return super.parseBasicStep(firstInPattern);
                    }

                case Token.STRING_LITERAL:
                case Token.NUMBER:
                case Token.KEYWORD_CURLY:
                case Token.ELEMENT_QNAME:
                case Token.ATTRIBUTE_QNAME:
                case Token.NAMESPACE_QNAME:
                case Token.PI_QNAME:
                case Token.TAG:
                case Token.NAMED_FUNCTION_REF:
                case Token.DOTDOT:
                    grumble("Token " + currentTokenDisplay() + " not allowed here in an XSLT pattern");
                    return null;
                case Token.FUNCTION:
                    if (!firstInPattern) {
                        grumble("In an XSLT pattern, a function call is allowed only as the first step in a path");
                    }
                    return super.parseBasicStep(firstInPattern);
                default:
                    return super.parseBasicStep(firstInPattern);

            }
        }
    }

    @Override
    protected void testPermittedAxis(byte axis, String errorCode) throws XPathException {
        if (inPredicate == 0) {
            if (!AxisInfo.isSubtreeAxis[axis]) {
                grumble("The " + AxisInfo.axisName[axis] + " is not allowed in a pattern");
            }
        }
    }

    /**
     * Parse an expression appearing within a predicate. This enables full XPath parsing, without
     * the normal rules that apply within an XSLT pattern
     *
     * @return the parsed expression that appears within the predicate
     * @throws net.sf.saxon.trans.XPathException
     *
     */

    /*@NotNull*/
    protected Expression parsePredicate() throws XPathException {
        boolean disallow = t.disallowUnionKeyword;
        t.disallowUnionKeyword = false;
        ++inPredicate;
        Expression exp = parseExpression();
        --inPredicate;
        t.disallowUnionKeyword = disallow;
        return exp;
    }

    /**
     * Parse a function call appearing within a pattern. Unless within a predicate, this
     * imposes the constraints on which function calls are allowed to appear in a pattern
     *
     * @return the expression that results from the parsing (usually a FunctionCall)
     * @throws net.sf.saxon.trans.XPathException
     *
     * @param prefixArgument
     */

    /*@NotNull*/
    public Expression parseFunctionCall(Expression prefixArgument) throws XPathException {
        Expression fn = super.parseFunctionCall(prefixArgument);
        if (inPredicate <= 0 && !fn.isCallOn(SuperId.class) && !fn.isCallOn(KeyFn.class) &&
                !fn.isCallOn(Doc.class) && !fn.isCallOn(Root_1.class)) {
            grumble("The " + fn.toString() + " function is not allowed at the head of a pattern");
        }
        return fn;
    }

    public Expression parseFunctionArgument() throws XPathException {
        if (inPredicate > 0) {
            return super.parseFunctionArgument();
        } else {
            switch (t.currentToken) {
                case Token.DOLLAR:
                    return parseVariableReference();

                case Token.STRING_LITERAL:
                    return parseStringLiteral(true);

                case Token.NUMBER:
                    return parseNumericLiteral(true);

                default:
                    grumble("A function argument in an XSLT pattern must be a variable reference or literal");
                    return null;
            }
        }
    }

    public Expression makeTracer(int startOffset, Expression exp, int construct, StructuredQName qName) {
        // Suppress tracing of pattern evaluation
        return exp;
    }
}
