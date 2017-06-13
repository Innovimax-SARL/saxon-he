////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.SequenceReceiver;
import net.sf.saxon.ma.arrays.ArrayItem;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.ObjectValue;
import net.sf.saxon.value.SequenceExtent;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * An value in the XDM data model. A value is a sequence of zero or more items,
 * each item being either an atomic value or a node.
 * <p/>
 * <p>An XdmValue is immutable.</p>
 * <p/>
 * <p>A sequence consisting of a single item may be represented as an instance of {@link XdmItem},
 * which is a subtype of XdmValue. However, there is no guarantee that a sequence of length one
 * will always be an instance of XdmItem.</p>
 * <p/>
 * <p>Similarly, a zero-length sequence may be represented as an instance of {@link XdmEmptySequence},
 * but there is no guarantee that every sequence of length zero will always be an instance of
 * XdmEmptySequence.</p>
 *
 * @since 9.0
 */

public class XdmValue implements Iterable<XdmItem> {

    private Sequence value;    // this must be a materialized value

    protected XdmValue() {
        // must be followed by setValue()
    }

    /**
     * Create an XdmValue as a sequence of XdmItem objects
     *
     * @param items a sequence of XdmItem objects. Note that if this is supplied as a list or similar
     *              collection, subsequent changes to the list/collection will have no effect on the XdmValue.
     * @since 9.0.0.4
     */

    public XdmValue(Iterable<? extends XdmItem> items) {
        List<Item> values = new ArrayList<Item>();
        for (XdmItem item : items) {
            values.add(item.getUnderlyingValue());
        }
        value = new SequenceExtent(values);
    }

    /**
     * Create an XdmValue containing the items returned by an XdmSequenceIterator
     *
     * @param iterator the iterator that supplies the values
     * @throws SaxonApiException if an error occurs reading values from the supplied iterator
     * @since 9.6
     */

    public XdmValue(XdmSequenceIterator iterator) throws SaxonApiException {
        try {
            List<Item> values = new ArrayList<Item>();
            while (iterator.hasNext()) {
                values.add(iterator.next().getUnderlyingValue());
            }
            value = new SequenceExtent(values);
        } catch (SaxonApiUncheckedException e) {
            throw new SaxonApiException(e.getCause());
        }
    }

    protected XdmValue(Sequence value) {
        setValue(value);
    }

    protected void setValue(Sequence value) {
        this.value = value;
    }

    /**
     * Create an XdmValue that wraps an existing Saxon Sequence
     *
     * @param value the supplied Sequence (which may be a singleton Item),
     * @return an XdmValue corresponding to the supplied Sequence. If the
     *         supplied value is null, an empty sequence is returned. If the supplied
     *         value is an atomic value, the result will be an instance of XdmAtomicValue.
     *         If the supplied value is a node, the result will be an instance of XdmNode.
     *         If the supplied value is a map, the result will be an instance of XdmMap.
     *         If the supplied value is an array, the result will be an instance of XdmArray.
     *         If the supplied value is a function item, the result will be an instance of
     *         XdmFunctionItem.
     * @throws SaxonApiUncheckedException if the supplied Sequence is not yet fully evaluated, and evaluation
     *                                    of the underlying expression fails with a dynamic error.
     * @since 9.5 (previously a protected method)
     */

    public static XdmValue wrap(Sequence value) {
        if (value == null) {
            return XdmEmptySequence.getInstance();
        }
        GroundedValue gv;
        try {
            gv = SequenceTool.toGroundedValue(value);
        } catch (XPathException e) {
            throw new SaxonApiUncheckedException(e);
        }
        if (gv.getLength() == 0) {
            return XdmEmptySequence.getInstance();
        } else if (gv.getLength() == 1) {
            Item first = gv.head();
            if (first instanceof NodeInfo) {
                return new XdmNode((NodeInfo) first);
            } else if (first instanceof AtomicValue) {
                return new XdmAtomicValue((AtomicValue) first);
            } else if (first instanceof MapItem) {
                return new XdmMap((MapItem)first);
            } else if (first instanceof ArrayItem) {
                return new XdmArray((ArrayItem)first);
            } else if (first instanceof Function) {
                return new XdmFunctionItem((Function) first);
            } else {
                return new XdmExternalObject(((ObjectValue)first).getObject());
            }
        } else {
            return new XdmValue(gv);
        }
    }

    /**
     * Create a new XdmValue by concatenating the contents of this XdmValue and another
     * XdmValue into a single sequence. The two input XdmValue objects are unaffected by this operation.
     * <p>Note: creating a sequence of N values by successive calls on this method
     * takes time proportional to N-squared.</p>
     *
     * @param otherValue the value to be appended
     * @return a new XdmValue containing the concatenation of the two input XdmValue objects
     * @since 9.2
     */

    public XdmValue append(XdmValue otherValue) {
        List<Item> values = new ArrayList<Item>();
        for (XdmItem item : this) {
            values.add(item.getUnderlyingValue());
        }
        for (XdmItem item : otherValue) {
            values.add(item.getUnderlyingValue());
        }
        return new XdmValue(new SequenceExtent(values));
    }

    /**
     * Get the number of items in the sequence
     *
     * @return the number of items in the value, considered as a sequence. Note that for arrays
     * and maps, the answer will be 1 (one) since arrays and maps are items.
     * @throws SaxonApiUncheckedException if the value is lazily evaluated and the delayed
     *                                    evaluation fails with a dynamic error.
     */

    public int size() {
        try {
            return SequenceTool.getLength(value);
        } catch (XPathException err) {
            throw new SaxonApiUncheckedException(err);
        }
    }

    /**
     * Get the n'th item in the value, counting from zero.
     *
     * @param n the item that is required, counting the first item in the sequence as item zero
     * @return the n'th item in the sequence making up the value, counting from zero
     * @throws IndexOutOfBoundsException  if n is less than zero or greater than or equal to the number
     *                                    of items in the value
     * @throws SaxonApiUncheckedException if the value is lazily evaluated and the delayed
     *                                    evaluation fails with a dynamic error.
     */

    public XdmItem itemAt(int n) throws IndexOutOfBoundsException, SaxonApiUncheckedException {
        if (n < 0 || n >= size()) {
            throw new IndexOutOfBoundsException("" + n);
        }
        try {
            Item item = SequenceTool.itemAt(value, n);
            return (XdmItem) XdmItem.wrap(item);
        } catch (XPathException e) {
            throw new SaxonApiUncheckedException(e);
        }
    }

    /**
     * Get an iterator over the items in this value.
     *
     * @return an Iterator over the items in this value.
     * @throws SaxonApiUncheckedException if the value is lazily evaluated and the delayed
     *                                    evaluation fails with a dynamic error.
     */
    public XdmSequenceIterator iterator() throws SaxonApiUncheckedException {
        try {
            Sequence v = getUnderlyingValue();
            return new XdmSequenceIterator(v.iterate());
        } catch (XPathException e) {
            throw new SaxonApiUncheckedException(e);
        }
    }

    /**
     * Get the underlying implementation object representing the value. This method allows
     * access to lower-level Saxon functionality, including classes and methods that offer
     * no guarantee of stability across releases.
     *
     * @return the underlying implementation object representing the value
     */

    public Sequence getUnderlyingValue() {
        return value;
    }

    /**
     * Create a string representation of the value. The is the result of serializing
     * the value using the adaptive serialization method.
     * @return a string representation of the value
     */

    public String toString() {
        try {
            Configuration config = null;
            SequenceIterator iter = value.iterate();
            Item item;
            while ((item = iter.next()) != null) {
                if (item instanceof NodeInfo) {
                    config = ((NodeInfo)item).getConfiguration();
                    break;
                }
            }
            if (config == null) {
                config = Configuration.newConfiguration();
            }
            StringWriter writer = new StringWriter();
            StreamResult result = new StreamResult(writer);
            PipelineConfiguration pipe = config.makePipelineConfiguration();
            Properties properties = new Properties();
            properties.setProperty(OutputKeys.METHOD, "adaptive");
            properties.setProperty(OutputKeys.INDENT, "true");
            properties.setProperty(OutputKeys.OMIT_XML_DECLARATION, "true");
            SequenceReceiver r = config.getSerializerFactory().getReceiver(result, pipe, properties);
            iter = value.iterate();
            while ((item = iter.next()) != null) {
                r.append(item);
            }
            return writer.toString();
        } catch (XPathException e) {
            return super.toString();
        }

    }

    /**
     * Make an XDM sequence from a Java Iterable. Each value delivered by the iterable
     * is first converted to an XDM value using the {@link #makeValue(Object)} method;
     * if the result is anything other than a single XDM item, it is then wrapped in an
     * {@link XdmArray}.
     * @return the result of the conversion if successful
     * @throws IllegalArgumentException if conversion is not possible
     */

    public static XdmValue makeSequence(Iterable list) throws IllegalArgumentException {
        List<Item> result = new ArrayList<Item>();
        for (Object o : list) {
            XdmValue v = XdmValue.makeValue(o);
            if (v instanceof XdmItem) {
                result.add((Item)v.getUnderlyingValue());
            } else {
                result.add(new XdmArray(v).getUnderlyingValue());
            }
        }
        return XdmValue.wrap(SequenceExtent.makeSequenceExtent(result));
    }

    /**
     * Make an XDM value from a Java object. The supplied object may be any of the following:
     * <ul>
     * <li>An instance of {@link XdmValue} (for example, an
     * {@link XdmNode} or {@link XdmAtomicValue} or {@link XdmArray} or {@link XdmMap}),
     * which is returned unchanged</li>
     * <li>An instance of {@link net.sf.saxon.om.Sequence} (for example, a
     * {@link net.sf.saxon.om.NodeInfo} or {@link net.sf.saxon.value.AtomicValue}),
     * which is wrapped as an {@code XdmValue}</li>
     * <li>An instance of {@link java.util.Map}, which is converted to an XDM Map using
     * the {@link XdmMap#makeMap} method</li>
     * <li>A Java array, of any type, which is converted to an XDM Array using the
     * {@link XdmArray#makeArray} method</li>
     * <li>A Java collection, more specifically {@link java.lang.Iterable}, which is converted to an XDM sequence
     * using the {@link XdmValue#makeSequence(Iterable)} method</li>
     * <li>Any object that is convertible to an XDM atomic value using the
     * method {@link XdmAtomicValue#makeAtomicValue(Object)}</li>
     * </ul>
     *
     * @return the result of the conversion if successful
     * @throws IllegalArgumentException if conversion is not possible
     */

    public static XdmValue makeValue(Object o) throws IllegalArgumentException {
        if (o instanceof Sequence) {
            return XdmValue.wrap((Sequence) o);
        } else if (o instanceof XdmValue) {
            return (XdmValue) o;
        } else if (o instanceof Map) {
            return XdmMap.makeMap((Map) o);
        } else if (o instanceof Object[]) {
            return XdmArray.makeArray((Object[]) o);
        } else if (o instanceof Iterable) {
            return XdmValue.makeSequence((Iterable) o);
        } else {
            return XdmAtomicValue.makeAtomicValue(o);
        }
    }


}

