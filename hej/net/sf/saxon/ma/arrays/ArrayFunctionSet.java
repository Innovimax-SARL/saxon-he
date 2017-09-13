////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.arrays;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.functions.registry.BuiltInFunctionSet;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.SpecificFunctionType;
import net.sf.saxon.value.*;
import net.sf.saxon.z.IntHashSet;
import net.sf.saxon.z.IntSet;

import java.util.ArrayList;
import java.util.List;

/**
 * Function signatures (and pointers to implementations) of the functions defined in XPath 2.0
 */

public class ArrayFunctionSet extends BuiltInFunctionSet {

    public static ArrayFunctionSet THE_INSTANCE = new ArrayFunctionSet();

    public ArrayFunctionSet() {
        init();
    }

    public static ArrayFunctionSet getInstance() {
        return THE_INSTANCE;
    }

    private void init() {


        register("append", 2, ArrayAppend.class, ArrayItemType.ANY_ARRAY_TYPE, ONE, 0, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null)
                .arg(1, AnyItemType.getInstance(), STAR | NAV, null);

        ItemType filterFunctionType = new SpecificFunctionType(
                new SequenceType[]{SequenceType.ANY_SEQUENCE},
                SequenceType.SINGLE_BOOLEAN);

        register("filter", 2, ArrayFilter.class, ArrayItemType.ANY_ARRAY_TYPE, ONE, 0, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null)
                .arg(1, filterFunctionType, ONE | INS, null);

        register("flatten", 1, ArrayFlatten.class, AnyItemType.getInstance(), STAR, 0, 0)
                .arg(0, AnyItemType.getInstance(), STAR | ABS, null);

        ItemType foldFunctionType = new SpecificFunctionType(
                new SequenceType[]{SequenceType.ANY_SEQUENCE, SequenceType.ANY_SEQUENCE},
                SequenceType.ANY_SEQUENCE);

        register("fold-left", 3, ArrayFoldLeft.class, AnyItemType.getInstance(), STAR, 0, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null)
                .arg(1, AnyItemType.getInstance(), STAR | NAV, null)
                .arg(2, foldFunctionType, ONE | INS, null);

        register("fold-right", 3, ArrayFoldRight.class, AnyItemType.getInstance(), STAR, 0, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null)
                .arg(1, AnyItemType.getInstance(), STAR | NAV, null)
                .arg(2, foldFunctionType, ONE | INS, null);

        ItemType forEachFunctionType = new SpecificFunctionType(
                new SequenceType[]{SequenceType.ANY_SEQUENCE},
                SequenceType.ANY_SEQUENCE);

        register("for-each", 2, ArrayForEach.class, AnyItemType.getInstance(), STAR, 0, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null)
                .arg(1, forEachFunctionType, ONE | INS, null);

        register("for-each-pair", 3, ArrayForEachPair.class, AnyItemType.getInstance(), STAR, 0, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null)
                .arg(1, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null)
                .arg(2, foldFunctionType, ONE | INS, null);

        register("get", 2, ArrayGet.class, AnyItemType.getInstance(), STAR, 0, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null)
                .arg(1, BuiltInAtomicType.INTEGER, ONE | ABS, null);

        register("head", 1, ArrayHead.class, AnyItemType.getInstance(), STAR, 0, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null);

        register("insert-before", 3, ArrayInsertBefore.class, ArrayItemType.ANY_ARRAY_TYPE, ONE, 0, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null)
                .arg(1, BuiltInAtomicType.INTEGER, STAR | ABS, null)
                .arg(2, AnyItemType.getInstance(), STAR | NAV, null);

        register("join", 1, ArrayJoin.class, ArrayItemType.ANY_ARRAY_TYPE, ONE, 0, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, STAR | INS, null);

        register("put", 3, ArrayPut.class, ArrayItemType.ANY_ARRAY_TYPE, ONE, 0, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null)
                .arg(1, BuiltInAtomicType.INTEGER, STAR | INS, null)
                .arg(2, AnyItemType.getInstance(), STAR | NAV, null);

        register("remove", 2, ArrayRemove.class, ArrayItemType.ANY_ARRAY_TYPE, ONE, 0, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null)
                .arg(1, BuiltInAtomicType.INTEGER, STAR | ABS, null);

        register("reverse", 1, ArrayReverse.class, ArrayItemType.ANY_ARRAY_TYPE, ONE, 0, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null);

        register("size", 1, ArraySize.class, BuiltInAtomicType.INTEGER, ONE, 0, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null);

        ItemType sortFunctionType = new SpecificFunctionType(
                new SequenceType[]{SequenceType.ANY_SEQUENCE},
                SequenceType.ATOMIC_SEQUENCE);

        register("sort", 1, ArraySort.class, ArrayItemType.ANY_ARRAY_TYPE, ONE, 0, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null);

        register("sort", 2, ArraySort.class, ArrayItemType.ANY_ARRAY_TYPE, ONE, 0, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null)
                .arg(1, BuiltInAtomicType.STRING, OPT | ABS, null);

        register("sort", 3, ArraySort.class, ArrayItemType.ANY_ARRAY_TYPE, ONE, 0, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null)
                .arg(1, BuiltInAtomicType.STRING, OPT | ABS, null)
                .arg(2, sortFunctionType, ONE | INS, null);

        register("subarray", 2, ArraySubarray.class, ArrayItemType.ANY_ARRAY_TYPE, ONE, 0, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null)
                .arg(1, BuiltInAtomicType.INTEGER, ONE | ABS, null);

        register("subarray", 3, ArraySubarray.class, ArrayItemType.ANY_ARRAY_TYPE, ONE, 0, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null)
                .arg(1, BuiltInAtomicType.INTEGER, ONE | ABS, null)
                .arg(2, BuiltInAtomicType.INTEGER, ONE | ABS, null);

        register("tail", 1, ArrayTail.class, ArrayItemType.ANY_ARRAY_TYPE, ONE, 0, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null);

        register("_to-sequence", 1, ArrayToSequence.class, AnyItemType.getInstance(), STAR, 0, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null);

        register("_from-sequence", 1, ArrayFromSequence.class, ArrayItemType.ANY_ARRAY_TYPE, ONE, 0, 0)
                .arg(0, AnyItemType.getInstance(), STAR | INS, null);


    }

    @Override
    public String getNamespace() {
        return NamespaceConstant.ARRAY_FUNCTIONS;
    }

    @Override
    public String getConventionalPrefix() {
        return "array";
    }


    /**
     * Implementation of the extension function array:append(array, item()*) => array
     */
    public static class ArrayAppend extends SystemFunction {

        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            ArrayItem array = (ArrayItem) arguments[0].head();
            assert array != null;
            Sequence other = arguments[1];
            List<Sequence> list = new ArrayList<Sequence>(1);
            list.add(other);
            SimpleArrayItem otherArray = new SimpleArrayItem(list);
            return array.concat(otherArray);
        }

    }

    /**
     * Implementation of the extension function array:filter(array, function) => array
     */
    public static class ArrayFilter extends SystemFunction {

        @Override
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            ArrayItem array = (ArrayItem) arguments[0].head();
            assert array != null;
            Function fn = (Function) arguments[1].head();
            List<Sequence> list = new ArrayList<Sequence>(1);
            int i;
            for (i=0; i < array.arrayLength(); i++) {
                if (((BooleanValue) dynamicCall(fn, context, new Sequence[]{array.get(i)}).head()).getBooleanValue()) {
                    list.add(array.get(i));
                }
            }
            return new SimpleArrayItem(list);
        }
    }

    /**
     * Implementation of the extension function array:flatten => item()*
     */
    public static class ArrayFlatten extends SystemFunction {

        private void flatten(Sequence arg, List<Item> out) throws XPathException {
            SequenceIterator iter = arg.iterate();
            Item item;
            while ((item = iter.next()) != null) {
                if (item instanceof ArrayItem) {
                    for (Sequence member : (ArrayItem)item) {
                        flatten(member, out);
                    }
                } else {
                    out.add(item);
                }
            }
        }

        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            List<Item> out = new ArrayList<Item>();
            flatten(arguments[0], out);
            return SequenceExtent.makeSequenceExtent(out);
        }
    }

    /**
     * Implementation of the extension function array:fold-left(array, item()*, function) => array
     */
    public static class ArrayFoldLeft extends SystemFunction {

        @Override
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            ArrayItem array = (ArrayItem) arguments[0].head();
            assert array != null;
            int arraySize = array.arrayLength();
            Sequence zero = arguments[1].head();
            Function fn = (Function) arguments[2].head();
            int i;
            for (i=0; i < arraySize; i++) {
                zero = dynamicCall(fn, context, new Sequence[]{zero, array.get(i)});
            }
            return zero;
        }
    }

    /**
     * Implementation of the extension function array:fold-left(array, item()*, function) => array
     */
    public static class ArrayFoldRight extends SystemFunction {

        @Override
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            ArrayItem array = (ArrayItem) arguments[0].head();
            assert array != null;
            Sequence zero = arguments[1].head();
            Function fn = (Function) arguments[2].head();
            int i;
            for (i = array.arrayLength() - 1; i >= 0; i--) {
                zero = dynamicCall(fn, context, new Sequence[]{array.get(i), zero});
            }
            return zero;
        }
    }

    /**
     * Implementation of the extension function array:for-each(array, function) => array
     */
    public static class ArrayForEach extends SystemFunction {

        @Override
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            ArrayItem array = (ArrayItem) arguments[0].head();
            assert array != null;
            Function fn = (Function) arguments[1].head();
            List<Sequence> list = new ArrayList<Sequence>(1);
            int i;
            for (i=0; i < array.arrayLength(); i++) {
                list.add(dynamicCall(fn, context, new Sequence[]{array.get(i)}));
            }
            return new SimpleArrayItem(list);
        }

    }

    /**
     * Implementation of the extension function array:for-each-pair(array, array, function) => array
     */
    public static class ArrayForEachPair extends SystemFunction {

        @Override
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            ArrayItem array1 = (ArrayItem) arguments[0].head();
            assert array1 != null;
            ArrayItem array2 = (ArrayItem) arguments[1].head();
            assert array2 != null;
            Function fn = (Function) arguments[2].head();
            List<Sequence> list = new ArrayList<Sequence>(1);
            int i;
            for (i=0; i < array1.arrayLength() && i < array2.arrayLength(); i++) {
                list.add(dynamicCall(fn, context, new Sequence[]{array1.get(i), array2.get(i)}));
            }
            return new SimpleArrayItem(list);
        }
    }

    /**
     * Implementation of the extension function array:get(array, xs:integer) => item()*
     */
    public static class ArrayGet extends SystemFunction {

        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            ArrayItem array = (ArrayItem) arguments[0].head();
            IntegerValue index = (IntegerValue) arguments[1].head();
            return array.get((int)index.longValue() - 1);
        }

    }

    /**
     * Implementation of the extension function array:head(array) => item()*
     */
    public static class ArrayHead extends SystemFunction {

        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            ArrayItem array = (ArrayItem) arguments[0].head();
            assert array != null;
            if (array.arrayLength() == 0){
                throw new XPathException("Argument to array:head is an empty array","FOAY0001");
            }
            return array.get(0);
        }

    }

    /**
     * Implementation of the extension function array:insert-before(array, xs:integer, item()*) => array
     */
    public static class ArrayInsertBefore extends SystemFunction {


        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            ArrayItem array = (ArrayItem) arguments[0].head();
            assert array != null;
            int index = (int)((IntegerValue) arguments[1].head()).longValue() - 1;
            if (index < 0 || index > array.arrayLength()){
                throw new XPathException("Specified position is not in range","FOAY0001");
            }
            Sequence newMember = arguments[2];
            List<Sequence> list = new ArrayList<Sequence>(1);
            int i;
            for (i=0; i < index; i++) {
                list.add(array.get(i));
            }
            list.add(newMember);
            for (i = index; i < array.arrayLength(); i++) {
                list.add(array.get(i));
            }
            return new SimpleArrayItem(list);
        }

    }

    /**
     * Implementation of the extension function array:join(arrays) => array
     */
    public static class ArrayJoin extends SystemFunction {

        @Override
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            SequenceIterator iterator = arguments[0].iterate();
            ArrayItem array = SimpleArrayItem.EMPTY_ARRAY;
            ArrayItem nextArray;
            while ((nextArray = (ArrayItem) iterator.next()) != null) {
                array = array.concat(nextArray);
            }
            return array;
        }

    }

    /**
     * Implementation of the extension function array:put(arrays, index, newValue) => array
     */
    public static class ArrayPut extends SystemFunction {

        @Override
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            ArrayItem array = (ArrayItem) arguments[0].head();
            int index = (int) ((IntegerValue) arguments[1].head()).longValue();
            GroundedValue newVal = SequenceExtent.makeSequenceExtent(arguments[2].iterate());
            return array.put(index-1, newVal);
        }

    }

    /**
     * Implementation of the extension function array:remove(array, xs:integer) => array
     */
    public static class ArrayRemove extends SystemFunction {


        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            ArrayItem array = (ArrayItem) arguments[0].head();
            if (arguments[1] instanceof IntegerValue) {
                int index = (int) ((IntegerValue) arguments[1]).longValue() - 1;
                if (index < 0 || index >= array.arrayLength()) {
                    throw new XPathException("Position " + index + " is not in range", "FOAY0001");
                }
                return array.remove(index);
            }
            SequenceIterator iter = arguments[1].iterate();
            IntegerValue pos;
            IntSet positions = new IntHashSet();
            while ((pos = (IntegerValue) iter.next()) != null) {
                int index = (int) pos.longValue() - 1;
                if (index < 0 || index >= array.arrayLength()) {
                    throw new XPathException("Position " + index + " is not in range", "FOAY0001");
                }
                positions.add(index);
            }
            return array.removeSeveral(positions);
        }

    }

    /**
     * Implementation of the extension function array:reverse(array, xs:integer, xs:integer) => array
     */
    public static class ArrayReverse extends SystemFunction {

        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            ArrayItem array = (ArrayItem) arguments[0].head();
            assert array != null;
            List<Sequence> list = new ArrayList<Sequence>(1);
            int i;
            for (i=0; i < array.arrayLength(); i++) {
                list.add(array.get(array.arrayLength()-i-1));
            }
            return new SimpleArrayItem(list);
        }

    }

    /**
     * Implementation of the extension function array:size(array) => integer
     */
    public static class ArraySize extends SystemFunction {

        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            ArrayItem array = (ArrayItem) arguments[0].head();
            assert array != null;
            return new Int64Value(array.arrayLength());
        }

    }

    /**
     * Implementation of the extension function array:subarray(array, xs:integer, xs:integer) => array
     */
    public static class ArraySubarray extends SystemFunction {


        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            ArrayItem array = (ArrayItem) arguments[0].head();
            assert array != null;
            int start = (int) ((IntegerValue) arguments[1].head()).longValue();
            int length;
            if (arguments.length == 3) {
                length = (int) ((IntegerValue) arguments[2].head()).longValue();
            }
            else {
                length = array.arrayLength() - start + 1;
            }
            if (start < 1) {
                throw new XPathException("Start position is less than one","FOAY0001");
            }
            if (start > array.arrayLength() + 1) {
                throw new XPathException("Start position is out of bounds","FOAY0001");
            }
            if (length < 0) {
                throw new XPathException("Specified length of subarray is less than zero","FOAY0002");
            }
            if (start + length > array.arrayLength() + 1) {
                throw new XPathException("Specified length of subarray is too great for start position given","FOAY0001");
            }
            List<Sequence> list = new ArrayList<Sequence>(1);
            int i;
            for (i=0; i < length; i++) {
                list.add(array.get(start - 1 + i));
            }
            return new SimpleArrayItem(list);
        }

    }

    /**
     * Implementation of the extension function array:tail(array) => item()*
     */
    public static class ArrayTail extends SystemFunction {

        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            ArrayItem array = (ArrayItem) arguments[0].head();
            assert array != null;
            if (array.arrayLength() < 1){
                throw new XPathException("Argument to array:tail is an empty array","FOAY0001");
            }
            return array.remove(0);
        }
    }

    /**
     * Implementation of the extension function array:_to-sequence(array) => item()* which
     * is used internally for the implementation of array?*
     */

    public static class ArrayToSequence extends SystemFunction {
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            ArrayItem array = (ArrayItem) arguments[0].head();
            List<GroundedValue> results = new ArrayList<GroundedValue>();
            for (int i = 0; i < array.arrayLength(); i++) {
                results.add(SequenceTool.toGroundedValue(array.get(i)));
            }
            assert array != null;
            return new Chain(results);
        }
    }

    /**
     * Implementation of the extension function array:_from-sequence(item()*) => array(*) which
     * is used internally for the implementation of array{} and of the saxon:array extension
     */

    public static class ArrayFromSequence extends SystemFunction {
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            return SimpleArrayItem.makeSimpleArrayItem(arguments[0].iterate());
        }
    }
}
