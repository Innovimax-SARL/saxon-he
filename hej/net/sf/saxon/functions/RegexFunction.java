////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.Configuration;
import net.sf.saxon.Version;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.regex.RegularExpression;
import net.sf.saxon.trans.XPathException;

import java.util.ArrayList;
import java.util.List;

/**
 * This class implements any of the functions matches(), replace(), tokenize(), analyze-string(), in the
 * version where a flags argument is present in the argument list
 */
public abstract class RegexFunction extends SystemFunction {

    private RegularExpression staticRegex;

    public RegularExpression getStaticRegex() {
        return staticRegex;
    }

    private void tryToBindRegularExpression(Expression[] arguments) {
        // For all these functions, the regular expression is the second argument, and the flags
        // argument is the last argument.
        if (arguments[1] instanceof Literal && arguments[arguments.length - 1] instanceof Literal) {
            try {
                Configuration config = getRetainedStaticContext().getConfiguration();
                String re = ((Literal) arguments[1]).getValue().getStringValue();
                String flags = ((Literal) arguments[arguments.length - 1]).getValue().getStringValue();
                String hostLang = "XP30";
                if (config.getXsdVersion() == Configuration.XSD11) {
                    hostLang += "/XSD11";
                }
                List<String> warnings = new ArrayList<String>(1);
                staticRegex = Version.platform.compileRegularExpression(config, re, flags, hostLang, warnings);
                if (!allowRegexMatchingEmptyString() && staticRegex.matches("")) {
                    staticRegex = null;
                    // will cause a dynamic error
                }
            } catch (XPathException err) {
                // If the regex is invalid, we leave it to be evaluated again at execution time
            }
        }
    }

    protected abstract boolean allowRegexMatchingEmptyString();

    /**
     * Make an expression that either calls this function, or that is equivalent to a call
     * on this function
     *
     * @param arguments the supplied arguments to the function call
     * @return either a function call on this function, or an expression that delivers
     * the same result
     */
    @Override
    public Expression makeFunctionCall(Expression... arguments) {
        tryToBindRegularExpression(arguments);
        return super.makeFunctionCall(arguments);
    }

    /**
     * Allow the function to create an optimized call based on the values of the actual arguments
     *
     * @param visitor     the expression visitor
     * @param contextInfo information about the context item
     * @param arguments   the supplied arguments to the function call. Note: modifying the contents
     *                    of this array should not be attempted, it is likely to have no effect.
     * @return either a function call on this function, or an expression that delivers
     * the same result, or null indicating that no optimization has taken place
     * @throws XPathException if an error is detected
     */
    @Override
    public Expression makeOptimizedFunctionCall(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo, Expression... arguments) throws XPathException {
        tryToBindRegularExpression(arguments);
        return super.makeOptimizedFunctionCall(visitor, contextInfo, arguments);
    }


    /**
     * Get the regular expression at evaluation time
     * @param args the argument values in the function call
     * @return the compiled regular expression; either the expression pre-compiled statically,
     * or the result of compiling it dynamically
     * @throws XPathException if the regular expression is invalid
     */

    protected RegularExpression getRegularExpression(Sequence[] args) throws XPathException {
        if (staticRegex != null) {
            return staticRegex;
        }
        Configuration config = getRetainedStaticContext().getConfiguration();
        String re = args[1].head().getStringValue();
        String flags = args[args.length - 1].head().getStringValue();
        String hostLang = "XP30";
        if (config.getXsdVersion() == Configuration.XSD11) {
            hostLang += "/XSD11";
        }
        List<String> warnings = new ArrayList<String>(1);
        RegularExpression regex = Version.platform.compileRegularExpression(config, re, flags, hostLang, warnings);
        if (!allowRegexMatchingEmptyString() && regex.matches("")) {
            throw new XPathException("The regular expression must not be one that matches a zero-length string", "FORX0003");
        }
        return regex;
    }


}

