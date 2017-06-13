////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.map;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.functions.InsertBefore;
import net.sf.saxon.functions.OptionsParameter;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.functions.registry.BuiltInFunctionSet;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.ma.arrays.ArrayItem;
import net.sf.saxon.ma.arrays.ArrayItemType;
import net.sf.saxon.ma.arrays.SimpleArrayItem;
import net.sf.saxon.om.*;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Function signatures (and pointers to implementations) of the functions defined in XPath 2.0
 */

public class MapFunctionSet extends BuiltInFunctionSet {

    public static MapFunctionSet THE_INSTANCE = new MapFunctionSet();

    public MapFunctionSet() {
        init();
    }

    public static MapFunctionSet getInstance() {
        return THE_INSTANCE;
    }

    private void init() {

        register("merge", 1, MapMerge.class, MapType.ANY_MAP_TYPE, ONE, 0, 0)
                .arg(0, MapType.ANY_MAP_TYPE, STAR | INS, null);

        OptionsParameter mergeOptionDetails = new OptionsParameter();
        mergeOptionDetails.addAllowedOption("duplicates", SequenceType.SINGLE_STRING, new StringValue("use-first"));
        // TODO: at some stage, drop duplicates=unspecified (retained for WD compatibility)
        mergeOptionDetails.setAllowedValues("duplicates", "FOJS0005", "use-first", "use-last", "combine", "reject", "unspecified", "use-any");
        mergeOptionDetails.addAllowedOption("duplicates-error-code", SequenceType.SINGLE_STRING, new StringValue("FOJS0003"));

        register("merge", 2, MapMerge.class, MapType.ANY_MAP_TYPE, ONE, 0, 0)
                .arg(0, MapType.ANY_MAP_TYPE, STAR, null)
                .arg(1, MapType.ANY_MAP_TYPE, STAR, null)
                .optionDetails(mergeOptionDetails);

        register("entry", 2, MapEntry.class, MapType.ANY_MAP_TYPE, ONE, 0, 0)
                .arg(0, BuiltInAtomicType.ANY_ATOMIC, ONE | ABS, null)
                .arg(1, AnyItemType.getInstance(), STAR | NAV, null);

        register("find", 2, MapFind.class, ArrayItemType.getInstance(), ONE, 0, 0)
                .arg(0, AnyItemType.getInstance(), STAR | INS, null)
                .arg(1, BuiltInAtomicType.ANY_ATOMIC, ONE | ABS, null);

        register("get", 2, MapGet.class, AnyItemType.getInstance(), STAR, 0, 0)
                .arg(0, MapType.ANY_MAP_TYPE, ONE | INS, null)
                .arg(1, BuiltInAtomicType.ANY_ATOMIC, ONE | ABS, null);

        register("put", 3, MapPut.class, MapType.ANY_MAP_TYPE, ONE, 0, 0)
                .arg(0, MapType.ANY_MAP_TYPE, ONE | INS, null)
                .arg(1, BuiltInAtomicType.ANY_ATOMIC, ONE | ABS, null)
                .arg(2, AnyItemType.getInstance(), STAR | NAV, null);

        register("contains", 2, MapContains.class, BuiltInAtomicType.BOOLEAN, ONE, 0, 0)
                .arg(0, MapType.ANY_MAP_TYPE, ONE | INS, null)
                .arg(1, BuiltInAtomicType.ANY_ATOMIC, ONE | ABS, null);

        register("remove", 2, MapRemove.class, MapType.ANY_MAP_TYPE, ONE, 0, 0)
                .arg(0, MapType.ANY_MAP_TYPE, ONE | INS, null)
                .arg(1, BuiltInAtomicType.ANY_ATOMIC, STAR | ABS, null);

        register("keys", 1, MapKeys.class, BuiltInAtomicType.ANY_ATOMIC, STAR, 0, 0)
                .arg(0, MapType.ANY_MAP_TYPE, ONE | INS, null);

        register("size", 1, MapSize.class, BuiltInAtomicType.INTEGER, ONE, 0, 0)
                .arg(0, MapType.ANY_MAP_TYPE, ONE | INS, null);

        ItemType actionType = new SpecificFunctionType(
                new SequenceType[]{SequenceType.SINGLE_ATOMIC, SequenceType.ANY_SEQUENCE},
                SequenceType.ANY_SEQUENCE);

        register("for-each", 2, MapForEach.class, AnyItemType.getInstance(), STAR, 0, 0)
                .arg(0, MapType.ANY_MAP_TYPE, ONE | INS, null)
                .arg(1, actionType, ONE | INS, null);

        register("untyped-contains", 2, MapUntypedContains.class, BuiltInAtomicType.BOOLEAN, ONE, 0, 0)
                .arg(0, MapType.ANY_MAP_TYPE, ONE | INS, null)
                .arg(1, BuiltInAtomicType.ANY_ATOMIC, ONE | ABS, null);


    }

    @Override
    public String getNamespace() {
        return NamespaceConstant.MAP_FUNCTIONS;
    }

    @Override
    public String getConventionalPrefix() {
        return "map";
    }


    /**
     * Implementation of the XPath 3.1 function map:contains(Map, key) => boolean
     */
    public static class MapContains extends SystemFunction {

        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            MapItem map = (MapItem) arguments[0].head();
            AtomicValue key = (AtomicValue) arguments[1].head();
            return BooleanValue.get(map.get(key) != null);
        }

    }

    /**
     * Implementation of the XPath 3.1 function map:get(Map, key) => value
     */
    public static class MapGet extends SystemFunction {

        String pendingWarning = null;

        /**
         * Get the return type, given knowledge of the actual arguments
         *
         * @param args the actual arguments supplied
         * @return the best available item type that the function will return
         */
        @Override
        public ItemType getResultItemType(Expression[] args) {
            ItemType mapType = args[0].getItemType();
            if (mapType instanceof TupleItemType && args[1] instanceof StringLiteral) {
                String key = ((StringLiteral) args[1]).getStringValue();
                TupleItemType tit = (TupleItemType) mapType;
                SequenceType valueType = tit.getFieldType(key);
                if (valueType == null) {
                    warning("Field " + key + " is not defined in tuple type");
                    return AnyItemType.getInstance();
                } else {
                    return valueType.getPrimaryType();
                }
            } else if (mapType instanceof MapType) {
                return ((MapType)mapType).getValueType().getPrimaryType();
            } else {
                return super.getResultItemType(args);
            }
        }

        /**
         * Get the cardinality, given knowledge of the actual arguments
         *
         * @param args the actual arguments supplied
         * @return the most precise available cardinality that the function will return
         */
        @Override
        public int getCardinality(Expression[] args) {
            ItemType mapType = args[0].getItemType();
            if (mapType instanceof TupleItemType && args[1] instanceof StringLiteral) {
                String key = ((StringLiteral) args[1]).getStringValue();
                TupleItemType tit = (TupleItemType) mapType;
                SequenceType valueType = tit.getFieldType(key);
                if (valueType == null) {
                    warning("Field " + key + " is not defined in tuple type");
                    return StaticProperty.ALLOWS_MANY;
                } else {
                    return valueType.getCardinality();
                }
            } else if (mapType instanceof MapType) {
                return Cardinality.union(
                        ((MapType) mapType).getValueType().getCardinality(),
                        StaticProperty.ALLOWS_ZERO);
            } else {
                return super.getCardinality(args);
            }
        }

        /**
         * Allow the function to create an optimized call based on the values of the actual arguments
         *
         * @param visitor     the expression visitor
         * @param contextInfo information about the context item
         * @param arguments   the supplied arguments to the function call. Note: modifying the contents
         *                    of this array should not be attempted, it is likely to have no effect.
         * @return either a function call on this function, or an expression that delivers
         * the same result, or null indicating that no optimization has taken place
         * @throws XPathException if an error is detected
         */
        @Override
        public Expression makeOptimizedFunctionCall(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo, Expression... arguments) throws XPathException {
            if (pendingWarning != null && !pendingWarning.equals("DONE")) {
                visitor.issueWarning(pendingWarning, arguments[0].getLocation());
                pendingWarning = "DONE";
            }
            return null;
        }

        private void warning(String message) {
            if (!"DONE".equals(pendingWarning)) {
                pendingWarning = message;
            }
        }

        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            MapItem map = (MapItem) arguments[0].head();
            assert map != null;
            AtomicValue key = (AtomicValue) arguments[1].head();
            Sequence value = map.get(key);
            if (value == null) {
                return EmptySequence.getInstance();
            } else {
                return value;
            }
        }

    }

    /**
     * Implementation of the XPath 3.1 function map:find(item()*, key) => array
     */
    public static class MapFind extends SystemFunction {

        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            List<Sequence> result = new ArrayList<Sequence>();
            AtomicValue key = (AtomicValue) arguments[1].head();
            processSequence(arguments[0], key, result);
            return new SimpleArrayItem(result);
        }

        private void processSequence(Sequence in, AtomicValue key, List<Sequence> result) throws XPathException {
            SequenceIterator iter = in.iterate();
            Item item;
            while ((item = iter.next()) != null) {
                if (item instanceof ArrayItem) {
                    for (Sequence sequence : (ArrayItem) item) {
                        processSequence(sequence, key, result);
                    }
                } else if (item instanceof MapItem) {
                    Sequence value = ((MapItem)item).get(key);
                    if (value != null) {
                        result.add(value);
                    }
                    for (KeyValuePair entry : (MapItem)item) {
                        processSequence(entry.value, key, result);
                    }
                }
            }
        }

    }

    /**
     * Implementation of the extension function map:entry(key, value) => Map
     */
    public static class MapEntry extends SystemFunction {

        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            AtomicValue key = (AtomicValue) arguments[0].head();
            assert key != null;
            Sequence value = SequenceExtent.makeSequenceExtent(arguments[1].iterate());
            return HashTrieMap.singleton(key, value, context);
        }

        /**
         * Get the return type, given knowledge of the actual arguments
         *
         * @param args the actual arguments supplied
         * @return the best available item type that the function will return
         */
        @Override
        public ItemType getResultItemType(Expression[] args) {
            PlainType ku = args[0].getItemType().getAtomizedItemType();
            AtomicType ka;
            if (ku instanceof AtomicType) {
                ka = (AtomicType)ku;
            } else {
                ka = ku.getPrimitiveItemType();
            }
            return new MapType(ka,
                               SequenceType.makeSequenceType(args[1].getItemType(), args[1].getCardinality()));
        }

        public String getStreamerName() {
            return "MapEntry";
        }

    }

    /**
     * Implementation of the extension function map:for-each(Map, Function) => item()*
     */
    public static class MapForEach extends SystemFunction {

        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            MapItem map = (MapItem) arguments[0].head();
            Function fn = (Function) arguments[1].head();
            List<GroundedValue> results = new ArrayList<GroundedValue>();
            for (KeyValuePair pair : map) {
                Sequence seq = fn.call(context, new Sequence[]{pair.key, pair.value});
                results.add(SequenceTool.toGroundedValue(seq));
            }
            return new Chain(results);
        }
    }

    /**
     * Implementation of the extension function map:keys(Map) => atomicValue*
     */
    public static class MapKeys extends SystemFunction {

        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            MapItem map = (MapItem) arguments[0].head();
            assert map != null;
            return SequenceTool.toLazySequence(map.keys());
        }
    }

    /**
     * Implementation of the extension function map:merge() => Map
     * From 9.8, map:merge is also used to implement map constructors in XPath and the xsl:map
     * instruction in XSLT. For this purpose it accepts an additional option to define the error
     * code to be used to signal duplicates.
     */
    public static class MapMerge extends SystemFunction {

        private String duplicates = "use-first";
        private String duplicatesErrorCode = "FOJS0003";

        /**
         * Allow the function to create an optimized call based on the values of the actual arguments
         *
         * @param visitor     the expression visitor
         * @param contextInfo information about the context item
         * @param arguments   the supplied arguments to the function call. Note: modifying the contents
         *                    of this array should not be attempted, it is likely to have no effect.
         * @return either a function call on this function, or an expression that delivers
         * the same result, or null indicating that no optimization has taken place
         * @throws XPathException if an error is detected
         */
        @Override
        public Expression makeOptimizedFunctionCall(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo, Expression... arguments) throws XPathException {
            if (arguments.length == 2 && arguments[1] instanceof Literal) {
                MapItem options = (MapItem) ((Literal)arguments[1]).getValue().head();
                Map<String, Sequence> values = getDetails().optionDetails.processSuppliedOptions(
                        options, visitor.getStaticContext().makeEarlyEvaluationContext());
                String duplicates = ((StringValue) values.get("duplicates")).getStringValue();
                String duplicatesErrorCode = ((StringValue) values.get("duplicates-error-code")).getStringValue();
                MapMerge mm2 = (MapMerge)MapFunctionSet.getInstance().makeFunction("merge", 1);
                mm2.duplicates = duplicates;
                mm2.duplicatesErrorCode = duplicatesErrorCode;
                return mm2.makeFunctionCall(arguments[0]);
            }
            return super.makeOptimizedFunctionCall(visitor, contextInfo, arguments);
        }

        /**
         * Get the return type, given knowledge of the actual arguments
         *
         * @param args the actual arguments supplied
         * @return the best available item type that the function will return
         */
        @Override
        public ItemType getResultItemType(Expression[] args) {
            ItemType it = args[0].getItemType();
            return it == ErrorType.getInstance() ? MapType.EMPTY_MAP_TYPE : it;
        }

        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {

            String duplicates = this.duplicates;
            String duplicatesErrorCode = this.duplicatesErrorCode;
            if (arguments.length > 1) {
                MapItem options = (MapItem)arguments[1].head();
                Map<String, Sequence> values = getDetails().optionDetails.processSuppliedOptions(options, context);
                duplicates = ((StringValue)values.get("duplicates")).getStringValue();
                duplicatesErrorCode = ((StringValue)values.get("duplicates-error-code")).getStringValue();
            }

            SequenceIterator iter = arguments[0].iterate();
            MapItem baseMap = (MapItem) iter.next();
            if (baseMap == null) {
                return new HashTrieMap();
            } else {
                if (!(baseMap instanceof HashTrieMap)) {
                    baseMap = HashTrieMap.copy(baseMap);
                }
                MapItem next;
                while ((next = (MapItem) iter.next()) != null) {
                    for (KeyValuePair pair : next) {
                        Sequence existing = baseMap.get(pair.key);
                        if (existing != null) {
                            if (duplicates.equals("use-first") || duplicates.equals("unspecified") || duplicates.equals("use-any")) {
                                // no action
                            } else if (duplicates.equals("use-last")) {
                                baseMap = ((HashTrieMap) baseMap).addEntry(pair.key, pair.value);
                            } else if (duplicates.equals("combine")) {
                                InsertBefore.InsertIterator combinedIter =
                                        new InsertBefore.InsertIterator(pair.value.iterate(), existing.iterate(), 1);
                                Sequence combinedValue = SequenceExtent.makeSequenceExtent(combinedIter);
                                baseMap = ((HashTrieMap) baseMap).addEntry(pair.key, combinedValue);
                            } else {
                                throw new XPathException("Duplicate key in constructed map: " +
                                                                 Err.wrap(pair.key.getStringValueCS()), duplicatesErrorCode);
                            }
                        } else {
                            baseMap = ((HashTrieMap) baseMap).addEntry(pair.key, pair.value);
                        }
                    }
                }
                return baseMap;
            }

        }

        public String getStreamerName() {
            return "NewMap";
        }

        /**
         * Export any implicit arguments held in optimized form within the SystemFunction call
         *
         * @param out the export destination
         */
        @Override
        public void exportAdditionalArguments(SystemFunctionCall call, ExpressionPresenter out) throws XPathException {
            if (call.getArity() == 1) {
                HashTrieMap options = new HashTrieMap();
                options.initialPut(new StringValue("duplicates"), new StringValue(duplicates));
                options.initialPut(new StringValue("duplicates-error-code"), new StringValue(duplicatesErrorCode));
                Literal.exportValue(options, out);
            }
        }
    }

    /**
     * Implementation of the extension function map:put() => Map
     */

    public static class MapPut extends SystemFunction {

        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {

            MapItem baseMap = (MapItem) arguments[0].head();

            if (!(baseMap instanceof HashTrieMap)) {
                baseMap = HashTrieMap.copy(baseMap);
            }

            AtomicValue key = (AtomicValue) arguments[1].head();
            Sequence value = SequenceTool.makeRepeatable(arguments[2]);
            KeyValuePair pair = new KeyValuePair(key, value);
            return ((HashTrieMap) baseMap).addEntry(pair.key, pair.value);
        }
    }

    /**
     * Implementation of the XPath 3.1 function map:remove(Map, key) => value
     */
    public static class MapRemove extends SystemFunction {

        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            MapItem map = (MapItem) arguments[0].head();
            SequenceIterator iter = arguments[1].iterate();
            AtomicValue key;
            while ((key = (AtomicValue) iter.next()) != null) {
                map = map.remove(key);
            }
            return map;
        }

    }

    /**
     * Implementation of the extension function map:size(map) => integer
     */
    public static class MapSize extends SystemFunction {

        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            MapItem map = (MapItem) arguments[0].head();
            return new Int64Value(map.size());
        }
    }

}
