////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.functions.registry.BuiltInFunctionSet;
import net.sf.saxon.lib.FeatureKeys;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AtomicValue;

/**
 * Implement the fn:doc() function - a simplified form of the Document function
 */

public class Doc extends SystemFunction implements Callable {

    private ParseOptions parseOptions;

    /**
     * Get the parsing options set via setParseOptions()
     * @return the parsing options
     */

    public ParseOptions getParseOptions() {
        return parseOptions;
    }

    /**
     * Set options to be used for the parsing operation. Defaults to the parsing options set in the Configuration
     * @param parseOptions the parsing options to be used. Currently only affects the behaviour of the sendDocument()
     * method (used in streamed merging)
     */

    public void setParseOptions(ParseOptions parseOptions) {
        this.parseOptions = parseOptions;
    }

    public int getCardinality(Expression[] arguments) {
        return arguments[0].getCardinality() & ~StaticProperty.ALLOWS_MANY;
    }

    @Override
    public Expression makeFunctionCall(Expression... arguments) {
        Expression expr = maybePreEvaluate(this, arguments);
        return expr == null ? super.makeFunctionCall(arguments) : expr;
    }

    public static Expression maybePreEvaluate(final SystemFunction sf, final Expression[] arguments) {
           if (arguments.length > 1 ||
                           !sf.getRetainedStaticContext().getConfiguration().getBooleanProperty(FeatureKeys.PRE_EVALUATE_DOC_FUNCTION)) {
               sf.getDetails().properties = sf.getDetails().properties | BuiltInFunctionSet.LATE;
               return null;

           } else {
               // allow early evaluation
               return new SystemFunctionCall(sf, arguments) {
                   @Override
                   public Expression preEvaluate(ExpressionVisitor visitor) throws XPathException {
                       Configuration config = visitor.getConfiguration();
                       try {
                           GroundedValue firstArg = ((Literal)getArg(0)).getValue();
                           if (firstArg.getLength() == 0) {
                               return null;
                           } else if (firstArg.getLength() > 1) {
                               return this;
                           }
                           String href = firstArg.head().getStringValue();
                           if (href.indexOf('#') >= 0) {
                               return this;
                           }
                           NodeInfo item = DocumentFn.preLoadDoc(href, sf.getStaticBaseUriString(), config, getLocation());
                           if (item != null) {
                               return Literal.makeLiteral(SequenceTool.toGroundedValue(item));
                           }
                       } catch (Exception err) {
                           // ignore the exception and try again at run-time
                           return this;
                       }
                       return this;
                   }
               };

           }

       }



    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as SequenceIterators
     * @return the result of the evaluation, in the form of a SequenceIterator
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    public ZeroOrOne<NodeInfo> call(XPathContext context, Sequence[] arguments) throws XPathException {
        AtomicValue hrefVal = (AtomicValue) arguments[0].head();
        if (hrefVal == null) {
            return ZeroOrOne.empty();
        }
        String href = hrefVal.getStringValue();
        PackageData packageData = getRetainedStaticContext().getPackageData();
        NodeInfo item = DocumentFn.makeDoc(href, getRetainedStaticContext().getStaticBaseUriString(), packageData, getParseOptions(), context, null, false);
        if (item == null) {
            // we failed to read the document
            throw new XPathException("Failed to load document " + href, "FODC0002", context);
        }
        if (parseOptions != null) {
            context.getController().getAccumulatorManager().setApplicableAccumulators(
                    item.getTreeInfo(), parseOptions.getApplicableAccumulators()
            );
        }
        return new ZeroOrOne<NodeInfo>(item);
    }

    /**
     * Get the static properties of this expression (other than its type). The result is
     * bit-signficant. These properties are used for optimizations. In general, if
     * property bit is set, it is true, but if it is unset, the value is unknown.
     * @param arguments
     */

    public int getSpecialProperties(Expression[] arguments) {
        return StaticProperty.ORDERED_NODESET |
                StaticProperty.PEER_NODESET |
                StaticProperty.NON_CREATIVE |
                StaticProperty.SINGLE_DOCUMENT_NODESET;
        // Declaring it as a peer node-set expression avoids sorting of expressions such as
        // doc(XXX)/a/b/c
        // The doc() function might appear to be creative: but it isn't, because multiple calls
        // with the same arguments will produce identical results.
    }


}

