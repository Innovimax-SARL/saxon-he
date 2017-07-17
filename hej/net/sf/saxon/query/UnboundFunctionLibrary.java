////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.query;

import net.sf.saxon.functions.CallableFunction;
import net.sf.saxon.Configuration;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.XPathParser;
import net.sf.saxon.functions.FunctionLibrary;
import net.sf.saxon.lib.FeatureKeys;
import net.sf.saxon.om.Function;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.SymbolicName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AnyFunctionType;

import java.util.ArrayList;
import java.util.List;

/**
 * An UnboundFunctionLibrary is not a real function library; rather, it is used to keep track of function calls
 * that cannot yet be bound to a known declared function, but will have to be bound when all user-declared functions
 * are available.
 */

public class UnboundFunctionLibrary implements FunctionLibrary {

    private List<UserFunctionResolvable> unboundFunctionReferences = new ArrayList<UserFunctionResolvable>(20);
    private List<StaticContext> correspondingStaticContext = new ArrayList<StaticContext>(20);
    private boolean resolving = false;

    /**
     * Create an UnboundFunctionLibrary
     */

    public UnboundFunctionLibrary() {
    }

    /**
     * Identify a (namespace-prefixed) function appearing in the expression. This
     * method is called by the XQuery parser to resolve function calls found within
     * the query.
     * <p>Note that a function call may appear earlier in the query than the definition
     * of the function to which it is bound. Unlike XSLT, we cannot search forwards to
     * find the function definition. Binding of function calls is therefore a two-stage
     * process; at the time the function call is parsed, we simply register it as
     * pending; subsequently at the end of query parsing all the pending function
     * calls are resolved. Another consequence of this is that we cannot tell at the time
     * a function call is parsed whether it is a call to an internal (XSLT or XQuery)
     * function or to an extension function written in Java.
     *
     * @return an Expression representing the function call. This will normally be
     *         a FunctionCall, but it may be rewritten as some other expression.
     * @throws net.sf.saxon.trans.XPathException
     *          if the function call is invalid, either because it is
     *          an unprefixed call to a non-system function, or because it is calling a system
     *          function that is available in XSLT only. A prefixed function call that cannot
     *          be recognized at this stage is assumed to be a forwards reference, and is bound
     *          later when bindUnboundFunctionCalls() is called.
     */

    /*@Nullable*/
    public Expression bind(SymbolicName.F functionName, /*@NotNull*/  Expression[] arguments, StaticContext env) throws XPathException {
        if (resolving) {
            return null;
        }
        UserFunctionCall ufc = new UserFunctionCall();
        ufc.setFunctionName(functionName.getComponentName());
        ufc.setArguments(arguments);
        unboundFunctionReferences.add(ufc);
        correspondingStaticContext.add(env);
        return ufc;
    }

//#ifdefined HOF

    /**
     * Test whether a function with a given name and arity is available; if so, return a function
     * item that can be dynamically called.
     * <p/>
     * <p>This supports the function-lookup() function in XPath 3.0.</p>
     *
     * @param functionName  the qualified name of the function being called
     * @param staticContext the static context to be used by the function, in the event that
     *                      it is a system function with dependencies on the static context
     * @return if a function of this name and arity is available for calling, then a corresponding
     *         function item; or null if the function does not exist
     * @throws net.sf.saxon.trans.XPathException
     *          in the event of certain errors, for example attempting to get a function
     *          that is private
     */
    public Function getFunctionItem(SymbolicName.F functionName, StaticContext staticContext) throws XPathException {
        if (resolving) {
            return null;
        }
        XQueryFunctionLibrary.UnresolvedCallable uc = new XQueryFunctionLibrary.UnresolvedCallable(functionName);
        unboundFunctionReferences.add(uc);
        correspondingStaticContext.add(null);
        CallableFunction fi = new CallableFunction(functionName, uc, AnyFunctionType.getInstance());
        //uc.setFunctionItem(fi);
        return fi;
    }
//#endif


    /**
     * Test whether a function with a given name and arity is available
     * <p>This supports the function-available() function in XSLT.</p>
     *
     * @param functionName the qualified name of the function being called
     * @return true if a function of this name and arity is available for calling
     */
    public boolean isAvailable(SymbolicName.F functionName) {
        return false;  // function-available() is not used in XQuery
    }

    /**
     * Bind function calls that could not be bound when first encountered. These
     * will either be forwards references to functions declared later in the query,
     * or errors. This method is for internal use.
     *
     * @param lib    A library containing all the XQuery functions that have been declared;
     *               the method searches this library for this missing function call
     * @param config The Saxon configuration
     * @throws XPathException if a function call refers to a function that has
     *                        not been declared
     */

    public void bindUnboundFunctionReferences(/*@NotNull*/ XQueryFunctionBinder lib, /*@NotNull*/ Configuration config) throws XPathException {
        resolving = true;
        for (int i = 0; i < unboundFunctionReferences.size(); i++) {
            UserFunctionResolvable ref = unboundFunctionReferences.get(i);
            if (ref instanceof UserFunctionCall) {
                UserFunctionCall ufc = (UserFunctionCall) ref;
                QueryModule importingModule = (QueryModule) correspondingStaticContext.get(i);
                if (importingModule == null) {
                    // means we must have already been here
                    continue;
                }
                correspondingStaticContext.set(i, null);    // for garbage collection purposes
                // The original UserFunctionCall is effectively a dummy: we weren't able to find a function
                // definition at the time. So we try again.
                final StructuredQName q = ufc.getFunctionName();
                final int arity = ufc.getArity();

                XQueryFunction fd = lib.getDeclaration(q, arity);
                if (fd != null) {
                    fd.registerReference(ufc);
                    ufc.setStaticType(fd.getResultType());
                    // Check that the result type and all the argument types are in the static context of the
                    // calling module
                    importingModule.checkImportedFunctionSignature(fd);
                } else {
                    String msg = "Cannot find a " + arity +
                            "-argument function named " + q.getClarkName() + "()";
                    if (!config.getBooleanProperty(FeatureKeys.ALLOW_EXTERNAL_FUNCTIONS)) {
                        msg += ". Note: external function calls have been disabled";
                    } else {
                        String supplementary = XPathParser.getMissingFunctionExplanation(q, config);
                        if (supplementary != null) {
                            msg += ". " + supplementary;
                        }
                    }
                    XPathException err = new XPathException(msg, "XPST0017", ufc.getLocation());
                    err.setIsStaticError(true);
                    throw err;
                }
            } else if (ref instanceof XQueryFunctionLibrary.UnresolvedCallable) {
                XQueryFunctionLibrary.UnresolvedCallable uc = (XQueryFunctionLibrary.UnresolvedCallable) ref;
                final StructuredQName q = uc.getFunctionName();
                final int arity = uc.getArity();

                XQueryFunction fd = lib.getDeclaration(q, arity);
                if (fd != null) {
                    fd.registerReference(uc);
                    //uc.setStaticType(fd.getResultType());
                    // Check that the result type and all the argument types are in the static context of the
                    // calling module
                    //importingModule.checkImportedFunctionSignature(fd);
                } else {
                    String msg = "Cannot find a " + arity +
                            "-argument function named " + q.getClarkName() + "()";
                    if (!config.getBooleanProperty(FeatureKeys.ALLOW_EXTERNAL_FUNCTIONS)) {
                        msg += ". Note: external function calls have been disabled";
                    }
                    XPathException err = new XPathException(msg);
                    err.setErrorCode("XPST0017");
                    err.setIsStaticError(true);
                    throw err;
                }
            }
        }
    }

    /**
     * This method creates a copy of a FunctionLibrary: if the original FunctionLibrary allows
     * new functions to be added, then additions to this copy will not affect the original, or
     * vice versa.
     *
     * @return a copy of this function library. This must be an instance of the original class.
     */

    /*@NotNull*/
    public FunctionLibrary copy() {
        UnboundFunctionLibrary qfl = new UnboundFunctionLibrary();
        qfl.unboundFunctionReferences = new ArrayList<UserFunctionResolvable>(unboundFunctionReferences);
        qfl.correspondingStaticContext = new ArrayList<StaticContext>(correspondingStaticContext);
        qfl.resolving = resolving;
        return qfl;
    }

}

