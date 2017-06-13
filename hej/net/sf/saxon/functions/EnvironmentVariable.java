////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.EnvironmentVariableResolver;
import net.sf.saxon.lib.FeatureKeys;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.ZeroOrOne;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.StringValue;

/**
 * Implement the XPath 3.0 fn:environment-variable() function
 */

public class EnvironmentVariable extends SystemFunction {


    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as SequenceIterators
     * @return the result of the evaluation, in the form of a SequenceIterator
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    public ZeroOrOne<StringValue> call(XPathContext context, Sequence[] arguments) throws XPathException {

        return new ZeroOrOne<StringValue>(
                getVariable((StringValue) arguments[0].head(), context));
    }

    private static StringValue getVariable(StringValue environVar, XPathContext context) throws XPathException {
        EnvironmentVariableResolver resolver = (EnvironmentVariableResolver) context.getConfiguration().getConfigurationProperty(
                FeatureKeys.ENVIRONMENT_VARIABLE_RESOLVER);
        String environVarName = environVar.getStringValue();
        String environValue;
        try {
            environValue = resolver.getEnvironmentVariable(environVarName);
            if (environValue == null) {
                return null;
            }
        } catch (SecurityException e) {
            environValue = "";
        } catch (NullPointerException e) {
            environValue = "";
        }

        return new StringValue(environValue);
    }

}

//Copyright (c) 2010-2012 Saxonica Limited.