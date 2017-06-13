////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ExplicitLocation;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NameTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.QNameValue;
import net.sf.saxon.value.SequenceExtent;
import net.sf.saxon.om.ZeroOrOne;
import net.sf.saxon.value.StringValue;

/**
 * Implement XPath function fn:error()
 */

public class Error extends SystemFunction implements Callable {

    @Override
    public int getSpecialProperties(Expression[] arguments) {
        return super.getSpecialProperties(arguments) & ~StaticProperty.NON_CREATIVE;
    }

    /**
     * Determine whether this is a vacuous expression as defined in the XQuery update specification
     *
     * @return true if this expression is vacuous
     */

    public boolean isVacuousExpression() {
        return true;
    }

//    /**
//     * Evaluation of the expression always throws an error
//     */
//
//    /*@Nullable*/
//    public Item evaluateItem(XPathContext context) throws XPathException {
//        int len = getArity();
//
//        switch (len) {
//            case 0:
//                return error(context, null, null, null);
//            case 1:
//                // Note: error#1 does not allow the first argument to be an empty sequence. So we take
//                // care to raise XPTY0004 in this case. But we still report a generic error message, rather
//                // than complaining specifically about the missing error code
//                QNameValue arg0 = (QNameValue) getArg(0).evaluateItem(context);
//                if (arg0 == null) {
//                    arg0 = new QNameValue("err", NamespaceConstant.ERR, "XPTY0004");
//                }
//                return error(context, arg0, null, null);
//            case 2:
//                return error(context, (QNameValue) getArg(0).evaluateItem(context), (StringValue) getArg(1).evaluateItem(context), null);
//            case 3:
//                return error(context, (QNameValue) getArg(0).evaluateItem(context), (StringValue) getArg(1).evaluateItem(context), getArg(2).iterate(context));
//            default:
//                return null;
//        }
//    }
//

//    /**
//     * Evaluate an updating expression, adding the results to a Pending Update List.
//     * The default implementation of this method, which is used for non-updating expressions,
//     * throws an UnsupportedOperationException
//     *
//     * @param context the XPath dynamic evaluation context
//     * @param pul     the pending update list to which the results should be written
//     */
//
//    public void evaluatePendingUpdates(XPathContext context, PendingUpdateList pul) throws XPathException {
//        evaluateItem(context);
//    }

    public Item error(
            XPathContext context,
            /*@Nullable*/ QNameValue errorCode,
            /*@Nullable*/ StringValue desc,
            /*@Nullable*/ SequenceIterator errObject) throws XPathException {
        QNameValue qname = null;
        if (getArity() > 0) {
            qname = errorCode;
        }
        if (qname == null) {
            qname = new QNameValue("err", NamespaceConstant.ERR,
                    getArity() == 1 ? "FOTY0004" : "FOER0000",
                    BuiltInAtomicType.QNAME, false);
        }
        String description;
        if (getArity() > 1) {
            description = desc == null ? "" : desc.getStringValue();
        } else {
            description = "Error signalled by application call on error()";
        }
        XPathException e = new XPathException(description);
        e.setErrorCodeQName(qname.getStructuredQName());
        e.setXPathContext(context);
        if (getArity() > 2 && errObject != null) {
            Sequence errorObject = SequenceExtent.makeSequenceExtent(errObject);
            if (errorObject instanceof ZeroOrOne) {
                Item root = ((ZeroOrOne) errorObject).head();
                if ((root instanceof NodeInfo) && ((NodeInfo) root).getNodeKind() == Type.DOCUMENT) {
                    AxisIterator iter = ((NodeInfo) root).iterateAxis(AxisInfo.CHILD,
                            new NameTest(Type.ELEMENT, "", "error", context.getConfiguration().getNamePool()));
                    NodeInfo errorElement = iter.next();
                    if (errorElement != null) {
                        String module = errorElement.getAttributeValue("", "module");
                        String lineVal = errorElement.getAttributeValue("", "line");
                        int line = lineVal == null ? -1 : Integer.parseInt(lineVal);
                        String columnVal = errorElement.getAttributeValue("", "column");
                        int col = columnVal == null ? -1 : Integer.parseInt(columnVal);
                        ExplicitLocation locator = new ExplicitLocation(module, line, col);
                        e.setLocator(locator);
                    }
                }
            }
            e.setErrorObject(errorObject);
        }
        throw e;
    }


    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        int len = arguments.length;

        switch (len) {
            case 0:
                return error(context, null, null, null);
            case 1:
                // Note: in XPath 3.1 first arg may be an empty sequence, and then error code is FOER0000.
                // Previously in XPath 3.0 error#1 does not allow the first argument to be an empty sequence. So we took
                // care to raise XPTY0004 in this case. But we still report a generic error message, rather
                // than complaining specifically about the missing error code
                QNameValue arg0 = (QNameValue)arguments[0].head();
                if (arg0 == null) {
                    arg0 = new QNameValue("err", NamespaceConstant.ERR, "FOER0000");
                }
                return error(context, arg0, null, null);
            case 2:
                return error(context, (QNameValue) arguments[0].head(), (StringValue) arguments[1].head(), null);
            case 3:
                return error(context, (QNameValue) arguments[0].head(), (StringValue) arguments[1].head(), arguments[2].iterate());
            default:
                return null;
        }
    }
}

