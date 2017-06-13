////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.NamespaceBinding;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.UnfailingIterator;
import net.sf.saxon.tree.util.NamespaceIterator;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.value.StringValue;

import java.util.Iterator;

/**
 * This class implements the XPath 2.0 function fn:in-scope-prefixes()
 */

public class InScopePrefixes extends SystemFunction {

    /**
     * Given an element node, return an iterator over the namespace
     * prefixes, taking into account special rules for the XML namespace and the default namespace
     *
     * @param element  an element
     * @return an iterator over the prefixes.
     */


    private UnfailingIterator iteratePrefixes(final NodeInfo element)  {
        final Iterator<NamespaceBinding> iter = NamespaceIterator.iterateNamespaces(element);
        return new UnfailingIterator() {
            private int position = 0;

            public int getProperties() {
                return 0;
            }

            public StringValue next() {
                if (position == 0) {
                    position++;
                    return new StringValue("xml");
                } else if (iter.hasNext()) {
                    String prefix = iter.next().getPrefix();
                    StringValue current;
                    if (prefix.isEmpty()) {
                        current = StringValue.EMPTY_STRING;
                    } else {
                        current = new StringValue(prefix, BuiltInAtomicType.NCNAME);
                    }
                    position++;
                    return current;
                } else {
                    position = -1;
                    return null;
                }
            }

            public void close() {
            }
        };

    }


    /**
     * Dynamic function call of in-scope-prefixes
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as Sequences.
     *                  <p>Generally it is advisable, if calling iterate() to process a supplied sequence, to
     *                  call it only once; if the value is required more than once, it should first be converted
     *                  to a {@link net.sf.saxon.om.GroundedValue} by calling the utility methd
     *                  SequenceTool.toGroundedValue().</p>
     *                  <p>If the expected value is a single item, the item should be obtained by calling
     *                  Sequence.head(): it cannot be assumed that the item will be passed as an instance of
     *                  {@link net.sf.saxon.om.Item} or {@link net.sf.saxon.value.AtomicValue}.</p>
     *                  <p>It is the caller's responsibility to perform any type conversions required
     *                  to convert arguments to the type expected by the callee. An exception is where
     *                  this Callable is explicitly an argument-converting wrapper around the original
     *                  Callable.</p>
     * @return the sequence of prefixes
     * @throws XPathException
     */

    public Sequence call(final XPathContext context, Sequence[] arguments) throws XPathException {
        final NodeInfo element = (NodeInfo) arguments[0].head();
        return SequenceTool.toLazySequence(iteratePrefixes(element));
    }

}

