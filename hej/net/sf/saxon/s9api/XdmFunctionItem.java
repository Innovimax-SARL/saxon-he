////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ExplicitLocation;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.om.Function;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.FunctionItemType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.SequenceExtent;

/**
 * The class XdmFunctionItem represents a function item
 */

public class XdmFunctionItem extends XdmItem {

    protected XdmFunctionItem() {}

    public XdmFunctionItem(Function fi) {
        super(fi);
    }

    /**
     * Get the name of the function
     *
     * @return the function name, as a QName, or null for an anonymous inline function item
     */

    public QName getName() {
        Function fi = (Function) getUnderlyingValue();
        StructuredQName sq = fi.getFunctionName();
        return sq == null ? null : new QName(sq);
    }

    /**
     * Get the arity of the function
     *
     * @return the arity of the function, that is, the number of arguments in the function's signature
     */

    public int getArity() {
        Function fi = (Function) getUnderlyingValue();
        return fi.getArity();
    }

    /**
     * Determine whether the item is an atomic value
     *
     * @return false, the item is not an atomic value, it is a function item
     */

    @Override
    public boolean isAtomicValue() {
        return false;
    }

    /**
     * Get a system function. This can be any function defined in XPath 3.1 functions and operators,
     * including functions in the math, map, and array namespaces. It can also be a Saxon extension
     * function, provided a licensed Processor is used.
     * @return the requested function, or null if there is no such function. Note that some functions
     * (those with particular context dependencies) may be unsuitable for dynamic calling.
     * @throws SaxonApiException if dynamic function calls are not permitted by this Saxon Configuration
     */

    public static XdmFunctionItem getSystemFunction(Processor processor, QName name, int arity) throws SaxonApiException {
        try {
            Configuration config = processor.getUnderlyingConfiguration();
            Function f = config.getSystemFunction(name.getStructuredQName(), arity);
            return f==null ? null : new XdmFunctionItem(f);
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Call the function
     *
     * @param arguments the values to be supplied as arguments to the function. The "function
     *                  conversion rules" will be applied to convert the arguments to the required
     *                  type when necessary.
     * @param processor the s9api Processor
     * @return the result of calling the function
     */

    public XdmValue call(Processor processor, XdmValue... arguments) throws SaxonApiException {
        if (arguments.length != getArity()) {
            throw new SaxonApiException("Supplied " + arguments.length + " arguments, required " + getArity());
        }
        try {
            Function fi = (Function) getUnderlyingValue();
            FunctionItemType type = fi.getFunctionItemType();
            Sequence[] argVals = new Sequence[arguments.length];
            TypeHierarchy th = processor.getUnderlyingConfiguration().getTypeHierarchy();
            for (int i = 0; i < arguments.length; i++) {
                net.sf.saxon.value.SequenceType required = type.getArgumentTypes()[i];
                Sequence val = arguments[i].getUnderlyingValue();
                if (!required.matches(val, th)) {
                    RoleDiagnostic role = new RoleDiagnostic(RoleDiagnostic.FUNCTION, "", i);
                    val = th.applyFunctionConversionRules(val, required, role, ExplicitLocation.UNKNOWN_LOCATION);
                }
                argVals[i] = val;
            }
            XPathContext context = processor.getUnderlyingConfiguration().getConversionContext();
            Sequence result = fi.call(context, argVals);
            if (!fi.isTrustedResultType()) {
                net.sf.saxon.value.SequenceType required = type.getResultType();
                if (!required.matches(result, th)) {
                    RoleDiagnostic role = new RoleDiagnostic(RoleDiagnostic.FUNCTION_RESULT, "", 0);
                    result = th.applyFunctionConversionRules(result, required, role, ExplicitLocation.UNKNOWN_LOCATION);
                }
            }
            Sequence se = SequenceExtent.makeSequenceExtent(result.iterate());
            return XdmValue.wrap(se);
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        }
    }

}