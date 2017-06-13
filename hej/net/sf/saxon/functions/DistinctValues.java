////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AtomicValue;

import java.util.HashSet;

/**
 * The XPath 2.0 distinct-values() function, with the collation argument already known
 */

public class DistinctValues extends CollatingFunctionFixed {


    public String getStreamerName() {
        return "DistinctValues";
    }


    /**
     * Iterator class to return the distinct values in a sequence
     */

    public static class DistinctIterator implements SequenceIterator {

        private SequenceIterator base;
        private StringCollator collator;
        private XPathContext context;
        private HashSet<Object> lookup = new HashSet<Object>(40);

        /**
         * Create an iterator over the distinct values in a sequence
         *
         * @param base     the input sequence. This must return atomic values only.
         * @param collator The comparer used to obtain comparison keys from each value;
         *                 these comparison keys are themselves compared using equals().
         * @param context the XPath dynamic context
         */

        public DistinctIterator(SequenceIterator base, StringCollator collator, XPathContext context) {
            this.base = base;
            this.collator = collator;
            this.context = context;
        }

        /**
         * Get the next item in the sequence. <BR>
         *
         * @return the next item, or null if there are no more items.
         * @throws net.sf.saxon.trans.XPathException
         *          if an error occurs retrieving the next item
         */

        public AtomicValue next() throws XPathException {
            int implicitTimezone = context.getImplicitTimezone();
            while (true) {
                AtomicValue nextBase = (AtomicValue) base.next();
                if (nextBase == null) {
                    return null;
                }
                Object key;
                if (nextBase.isNaN()) {
                    key = DistinctValues.class;
                } else {
                    key = nextBase.getXPathComparable(false, collator, implicitTimezone);
                }
                if (lookup.add(key)) {
                    // returns true if newly added (if not, keep looking)
                    return nextBase;
                }
            }
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

    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        StringCollator collator = getStringCollator();
        return SequenceTool.toLazySequence(new DistinctIterator(arguments[0].iterate(), collator, context));
    }

}

