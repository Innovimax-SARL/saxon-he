////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.ma.arrays.ArrayItem;
import net.sf.saxon.ma.arrays.SimpleArrayItem;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * An array in the XDM data model. An array is a list of zero or more members, each of which
 * is an arbitrary XDM value. The array itself is an XDM item.
 * <p/>
 * <p>An XdmArray is immutable.</p>
 * <p/>
 *
 * @since 9.8
 */

public class XdmArray extends XdmFunctionItem {

    /**
     * Create an empty XdmArray
     */

    public XdmArray() {
        setValue(SimpleArrayItem.EMPTY_ARRAY);
    }

    /**
     * Create an XdmArray that wraps a supplied ArrayItem
     * @param array the ArrayItem to be encapsulated
     */

    public XdmArray(ArrayItem array) {
        super(array);
    }

    /**
     * Create an XdmArray supplying the members as an array of XdmValue objects
     *
     * @param members an array of XdmValue objects. Note that subsequent changes to the array will have no effect
     *                on the XdmValue.
     */

    public XdmArray(XdmValue[] members) {
        List<Sequence> values = new ArrayList<Sequence>();
        for (XdmValue member : members) {
            values.add(member.getUnderlyingValue());
        }
        setValue(new SimpleArrayItem(values));
    }

    /**
     * Create an XdmArray supplying the members as a collection of XdmValue objects
     *
     * @param members a sequence of XdmValue objects. Note that if this is supplied as a list or similar
     *                collection, subsequent changes to the list/collection will have no effect on the XdmValue.
     *                Note that the argument can be a single XdmValue representing a sequence, in which case the
     *                constructed array will have one member for each item in the supplied sequence.
     */

    public XdmArray(Iterable<? extends XdmValue> members) {
        List<Sequence> values = new ArrayList<Sequence>();
        for (XdmValue member : members) {
            values.add(member.getUnderlyingValue());
        }
        setValue(new SimpleArrayItem(values));
    }

    /**
     * Get the number of members in the array
     *
     * @return the number of members in the array. (Note that the {@link #size()} method returns 1 (one),
     * because an XDM array is an item.)
     */

    public int arrayLength() {
        return getUnderlyingValue().arrayLength();
    }

    /**
     * Get the n'th member in the array, counting from zero.
     *
     * @param n the member that is required, counting the first member in the array as member zero
     * @return the n'th member in the sequence making up the array, counting from zero
     * @throws IndexOutOfBoundsException  if n is less than zero or greater than or equal to the number
     *                                    of members in the array
     */

    public XdmValue get(int n) throws IndexOutOfBoundsException {
        try {
            Sequence member = getUnderlyingValue().get(n);
            return XdmValue.wrap(member);
        } catch (XPathException e) {
            throw new IndexOutOfBoundsException();
        }
    }

    /**
     * Create a new array in which one member is replaced with a new value.
     *
     * @param n the position of the member that is to be replaced, counting the first member
     *          in the array as member zero
     * @param value the new value for this member
     * @return a new array, the same length as the original, with one member replaced
     * by a new value
     * @throws IndexOutOfBoundsException if n is less than zero or greater than or equal to the number
     *                                   of members in the array
     */

    public XdmArray put(int n, XdmValue value) throws IndexOutOfBoundsException {
        try {
            Sequence member = value.getUnderlyingValue();
            return (XdmArray)XdmValue.wrap(getUnderlyingValue().put(n, member));
        } catch (XPathException e) {
            throw new IndexOutOfBoundsException();
        }
    }

    /**
     * Get the members of the array in the form of a list.
     * @return a list of the members of this array.
     */
    public List<XdmValue> asList() {
        SimpleArrayItem val = (SimpleArrayItem)getUnderlyingValue();
        Iterator<Sequence> members = val.iterator();
        List<XdmValue> result = new ArrayList<XdmValue>(val.getLength());
        while (members.hasNext()) {
            result.add(XdmValue.wrap(members.next()));
        }
        return result;
    }

    /**
     * Get the underlying implementation object representing the value. This method allows
     * access to lower-level Saxon functionality, including classes and methods that offer
     * no guarantee of stability across releases.
     *
     * @return the underlying implementation object representing the value
     */
    @Override
    public ArrayItem getUnderlyingValue() {
        return (ArrayItem)super.getUnderlyingValue();
    }

    /**
     * Make an XDM array from a Java array. Each member of the supplied array
     * is converted to a single member in the result array using the method
     * {@link XdmValue#makeValue(Object)}
     *
     * @return the result of the conversion if successful
     * @throws IllegalArgumentException if conversion is not possible
     */

    public static XdmArray makeArray(Object[] input) throws IllegalArgumentException {
        List<XdmValue> result = new ArrayList<XdmValue>(input.length);
        for (Object o : input) {
            result.add(XdmValue.makeValue(o));
        }
        return new XdmArray(result);
    }

    /**
     * Make an XdmArray whose members are xs:boolean values
     * @param input the input array of booleans
     * @return an XdmArray whose members are xs:boolean values corresponding one-to-one with the input
     */

    public static XdmArray makeArray(boolean[] input) {
        List<XdmValue> result = new ArrayList<XdmValue>(input.length);
        for (boolean o : input) {
            result.add(new XdmAtomicValue(o));
        }
        return new XdmArray(result);
    }

    /**
     * Make an XdmArray whose members are xs:long values
     *
     * @param input the input array of integers
     * @return an XdmArray whose members are xs:integer values corresponding one-to-one with the input
     */

    public static XdmArray makeArray(long[] input) {
        List<XdmValue> result = new ArrayList<XdmValue>(input.length);
        for (long o : input) {
            result.add(new XdmAtomicValue(o));
        }
        return new XdmArray(result);
    }

    /**
     * Make an XdmArray whose members are xs:integer values
     *
     * @param input the input array of integers
     * @return an XdmArray whose members are xs:integer values corresponding one-to-one with the input
     */

    public static XdmArray makeArray(int[] input) {
        List<XdmValue> result = new ArrayList<XdmValue>(input.length);
        for (int o : input) {
            result.add(new XdmAtomicValue(o));
        }
        return new XdmArray(result);
    }

    /**
     * Make an XdmArray whose members are xs:integer values
     *
     * @param input the input array of integers
     * @return an XdmArray whose members are xs:integer values corresponding one-to-one with the input
     */

    public static XdmArray makeArray(short[] input) {
        List<XdmValue> result = new ArrayList<XdmValue>(input.length);
        for (short o : input) {
            result.add(new XdmAtomicValue(o));
        }
        return new XdmArray(result);
    }

    /**
     * Make an XdmArray whose members are xs:integer values
     *
     * @param input the input array of integers
     * @return an XdmArray whose members are xs:integer values corresponding one-to-one with the input
     */

    public static XdmArray makeArray(byte[] input) {
        List<XdmValue> result = new ArrayList<XdmValue>(input.length);
        for (byte o : input) {
            result.add(new XdmAtomicValue(o));
        }
        return new XdmArray(result);
    }

}

