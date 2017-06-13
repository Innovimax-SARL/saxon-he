////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.expr.UserFunctionCall;
import net.sf.saxon.expr.instruct.UserFunction;
import net.sf.saxon.om.Function;
import net.sf.saxon.trans.SymbolicName;
import net.sf.saxon.trans.XPathException;

import java.util.HashMap;
import java.util.Iterator;

/**
 * An ExecutableFunctionLibrary is a function library that contains definitions of functions for use at
 * run-time. Normally functions are bound at compile-time; however there are various situations in which
 * the information is needed dynamically, for example (a) to support the XSLT function-available() call
 * (in the pathological case where the argument is not known statically), (b) to allow functions to be
 * called from saxon:evaluate(), (c) to allow functions to be called from a debugging breakpoint.
 * <p/>
 */

public class ExecutableFunctionLibrary implements FunctionLibrary {

    private transient Configuration config;
    private HashMap<SymbolicName, UserFunction> functions = new HashMap<SymbolicName, UserFunction>(20);
    // The key of the hash table is a String that combines the QName of the function with the arity.

    /**
     * Create the ExecutableFunctionLibrary
     *
     * @param config the Saxon configuration
     */

    public ExecutableFunctionLibrary(Configuration config) {
        this.config = config;
    }

    /**
     * Register a function with the function library
     *
     * @param fn the function to be registered
     */

    public void addFunction(UserFunction fn) {
        functions.put(fn.getSymbolicName(), fn);
    }

    /**
     * Bind a function, given the URI and local parts of the function name,
     * and the list of expressions supplied as arguments. This method is called at compile
     * time.
     *
     * @param functionName The name of the function to be called
     * @param staticArgs   The expressions supplied statically in the function call. The intention is
     *                     that the static type of the arguments (obtainable via getItemType() and getCardinality() may
     *                     be used as part of the binding algorithm.
     * @param env          the static evaluation context
     * @return An object representing the extension function to be called, if one is found;
     *         null if no extension function was found matching the required name and arity.
     * @throws net.sf.saxon.trans.XPathException
     *          if a function is found with the required name and arity, but
     *          the implementation of the function cannot be loaded or used; or if an error occurs
     *          while searching for the function; or if this function library "owns" the namespace containing
     *          the function call, but no function was found.
     */

    public Expression bind(SymbolicName.F functionName, Expression[] staticArgs, StaticContext env)
            throws XPathException {
        UserFunction fn = functions.get(functionName);
        if (fn == null) {
            return null;
        }
        UserFunctionCall fc = new UserFunctionCall();
        fc.setFunctionName(functionName.getComponentName());
        fc.setArguments(staticArgs);
        fc.setFunction(fn);
        fc.setStaticType(fn.getResultType());
        return fc;
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
        UserFunction fn = functions.get(functionName);
        if (fn != null && fn.isUpdating()) {
            throw new XPathException("Cannot bind a function item to an updating function");
        }
        return fn;
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
        return functions.get(functionName) != null;
    }

    /**
     * This method creates a copy of a FunctionLibrary: if the original FunctionLibrary allows
     * new functions to be added, then additions to this copy will not affect the original, or
     * vice versa.
     *
     * @return a copy of this function library. This must be an instance of the original class.
     */

    public FunctionLibrary copy() {
        ExecutableFunctionLibrary efl = new ExecutableFunctionLibrary(config);
        efl.functions = new HashMap<SymbolicName, UserFunction>(functions);
        return efl;
    }

    /**
     * Iterate over all the functions defined in this function library. The objects
     * returned by the iterator are of class {@link UserFunction}
     *
     * @return an iterator delivering the {@link UserFunction} objects representing
     *         the user-defined functions in a stylesheet or query
     */

    public Iterator<UserFunction> iterateFunctions() {
        return functions.values().iterator();
    }

}

