////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.Controller;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.instruct.InstructionDetails;
import net.sf.saxon.expr.parser.ExplicitLocation;
import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.lib.Logger;
import net.sf.saxon.lib.TraceListener;
import net.sf.saxon.ma.arrays.ArrayItem;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.om.*;
import net.sf.saxon.trace.LocationKind;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.ObjectValue;
import net.sf.saxon.value.SequenceExtent;

import javax.xml.transform.SourceLocator;

/**
 * This class supports the XPath 2.0 function trace().
 * The value is traced to the registered output stream (defaulting to System.err),
 * unless a TraceListener is in use, in which case the information is sent to the TraceListener
 */


public class Trace extends SystemFunction {

    Location location = ExplicitLocation.UNKNOWN_LOCATION;

    /**
     * Get the static properties of this expression (other than its type). The result is
     * bit-significant. These properties are used for optimizations. In general, if
     * property bit is set, it is true, but if it is unset, the value is unknown.
     * @param arguments the actual arguments
     */

    public int getSpecialProperties(Expression[] arguments) {
        return arguments[0].getSpecialProperties();
    }

    public int getCardinality(Expression[] arguments) {
        return arguments[0].getCardinality();
    }

    public void notifyListener(String label, Sequence val, SourceLocator loc, XPathContext context) {
        InstructionDetails info = new InstructionDetails();
        info.setConstructType(LocationKind.TRACE_CALL);
        info.setLineNumber(loc.getLineNumber());
        info.setSystemId(loc.getSystemId());
        info.setProperty("label", label);
        info.setProperty("value", val);
        TraceListener listener = context.getController().getTraceListener();
        listener.enter(info, context);
        listener.leave(info);
    }

    @Override
    public Expression makeFunctionCall(Expression... arguments) {
        // Fix bug 2597
        Expression e = super.makeFunctionCall(arguments);
        location = e.getLocation();
        return e;
    }

    public static void traceItem(/*@Nullable*/ Item val, String label, Logger out) {
        if (val == null) {
            out.info(label);
        } else {
            if (val instanceof NodeInfo) {
                out.info(label + ": " + Type.displayTypeName(val) + ": "
                    + Navigator.getPath((NodeInfo) val));
            } else if (val instanceof AtomicValue || val instanceof ObjectValue) {
                out.info(label + ": " + Type.displayTypeName(val) + ": "
                        + val.getStringValue());
            } else if (val instanceof ArrayItem || val instanceof MapItem) {
                out.info(label + ": " + val.toString());
            } else if (val instanceof Function) {
                StructuredQName name = ((Function)val).getFunctionName();
                out.info(label + ": function " + (name==null ? "(anon)" : name.getDisplayName()) + "#" + ((Function)val).getArity());
            }
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
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        Controller controller = context.getController();
        String label = arguments.length == 1 ? "*" : arguments[1].head().getStringValue();
        if (controller.isTracing()) {
            Sequence value = SequenceExtent.makeSequenceExtent(arguments[0].iterate());
            notifyListener(label, value, location, context);
            return value;
        } else {
            Logger out = controller.getTraceFunctionDestination();
            if (out == null) {
                return arguments[0];
            } else {
                return SequenceTool.toLazySequence(new TracingIterator(arguments[0].iterate(),
                        label, out));
            }
        }
    }

    /**
     * Tracing Iterator class
     */

    private class TracingIterator implements SequenceIterator {

        private SequenceIterator base;
        private String label;
        private Logger out;
        private boolean empty = true;
        private int position = 0;


        public TracingIterator(SequenceIterator base, String label, Logger out) {
            this.base = base;
            this.label = label;
            this.out = out;
        }

        public Item next() throws XPathException {
            Item n = base.next();
            position++;
            if (n == null) {
                if (empty) {
                    traceItem(null, label + ": empty sequence", out);
                }
            } else {
                traceItem(n, label + " [" + position + ']', out);
                empty = false;
            }
            return n;
        }

        public void close() {
            base.close();
        }

        /**
         * Get properties of this iterator, as a bit-significant integer.
         *
         * @return the properties of this iterator. This will be some combination of
         *         properties such as {@link #GROUNDED}, {@link #LAST_POSITION_FINDER},
         *         and {@link #LOOKAHEAD}. It is always
         *         acceptable to return the value zero, indicating that there are no known special properties.
         *         It is acceptable for the properties of the iterator to change depending on its state.
         */

        public int getProperties() {
            return 0;
        }
    }

    public String getStreamerName() {
        return "Trace";
    }

}

