////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.map;

import net.sf.saxon.expr.OperandRole;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.sort.AtomicComparer;
import net.sf.saxon.expr.sort.AtomicMatchKey;
import net.sf.saxon.functions.DeepEqual;
import net.sf.saxon.ma.trie.ImmutableHashTrieMap;
import net.sf.saxon.ma.trie.ImmutableMap;
import net.sf.saxon.ma.trie.Tuple2;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.query.AnnotationList;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AtomicIterator;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.*;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.SequenceType;

import java.util.Iterator;

/**
 * An immutable map. This is the Saxon 9.6 implementation, which uses a hash trie
 */
public class HashTrieMap extends AbstractItem implements MapItem, GroundedValue {

    public final static SequenceType SINGLE_MAP_TYPE = SequenceType.makeSequenceType(
            MapType.ANY_MAP_TYPE, StaticProperty.EXACTLY_ONE);

    // The underlying trie holds key-value pairs, but these do not correspond directly
    // to the key value pairs in the XDM map. Instead, the key in the trie is an AtomicMatchKey
    // based on the XDM key, which allows equality matching to differ from the Java-level
    // equals method (to take account of collations, etc). The value in the trie is
    // actually a tuple holding both the real key, and the value.

    private ImmutableMap<AtomicMatchKey, KeyValuePair> imap;

    // The following values are maintained incrementally when
    // entries are added to the map. They are not changed when entries are removed,
    // so the actual type may be narrower than these values suggest. The purpose of
    // keeping this information is to try and avoid dynamic checking of the map
    // contents against a required type wherever possible. We therefore have
    // a guarantee that all entries in the map conform to the types maintained here;
    // but there is no guarantee that they do not also conform to some more specific
    // type.

    // The set of UTypes of keys in the map
    private UType keyUType = UType.VOID;

    // The set of UTypes of items in the values in the map
    private UType valueUType = UType.VOID;

    // The atomic type of all keys, if they are homogeneous; otherwise null
    private AtomicType keyAtomicType = ErrorType.getInstance();

    // The item type of all items in values, if they are homogeneous; otherwise null
    private ItemType valueItemType = ErrorType.getInstance();

    // The "envelope" cardinality of values in the map (0:1, 0:0, 1:1, 0:many, 1:many)
    private int valueCardinality = 0;

    // The number of entries in the map; -1 if unknown
    private int entries = -1;

    /**
     * Create an empty map
     */

    public HashTrieMap() {
        this.imap = ImmutableHashTrieMap.empty();
        this.entries = 0;
    }


    /**
     * Create a singleton map with a single key and value
     * @param key the key value
     * @param value the associated value
     * @param context dynamic evaluation context. Gives access to type information.
     * @return a singleton map
     * @throws XPathException if map mixes timezoned and timezoneless values
     */

    public static HashTrieMap singleton(AtomicValue key, Sequence value, XPathContext context) throws XPathException {
        return new HashTrieMap().addEntry(key, value);
    }

    /**
     * Create a map whose contents are a copy of an existing immutable map
     * @param imap the map to be copied
     */

    public HashTrieMap(ImmutableMap<AtomicMatchKey, KeyValuePair> imap) {
        this.imap = imap;
        entries = -1;
    }

    /**
     * Create a map whose entries are copies of the entries in an existing MapItem
     *
     * @param map       the existing map to be copied
     * @return the new map
     */

    public static HashTrieMap copy(MapItem map) {
        if (map instanceof HashTrieMap) {
            return (HashTrieMap)map;
        }
        HashTrieMap m2 = new HashTrieMap();
        for (KeyValuePair pair : map) {
            m2 = m2.addEntry(pair.key, pair.value);
        }
        return m2;
    }

    /**
     * Ask whether this function item is an array
     *
     * @return false (it is not an array)
     */
    public boolean isArray() {
        return false;
    }

    /**
     * Ask whether this function item is a map
     *
     * @return true (it is a map)
     */
    public boolean isMap() {
        return true;
    }

    /**
     * Get the function annotations (as defined in XQuery). Returns an empty
     * list if there are no function annotations.
     *
     * @return the function annotations
     */

    @Override
    public AnnotationList getAnnotations() {
        return AnnotationList.EMPTY;
    }

    /**
     * Atomize the item.
     *
     * @return the result of atomization
     * @throws XPathException if atomization is not allowed for this kind of item
     */
    public AtomicSequence atomize() throws XPathException {
        throw new XPathException("Maps cannot be atomized", "FOTY0013");
    }

    /**
     * After adding an entry to the map, update the cached type information
     * @param key the new key
     * @param val the new associated value
     * @param wasEmpty true if the map was empty before adding these values
     */

    private void updateTypeInformation(AtomicValue key, Sequence val, boolean wasEmpty) {
        if (wasEmpty) {
            keyUType = key.getUType();
            valueUType = SequenceTool.getUType(val);
            keyAtomicType = key.getItemType();
            valueItemType = getItemTypeOfSequence(val);
            valueCardinality = SequenceTool.getCardinality(val);
        } else {
            keyUType = keyUType.union(key.getUType());
            valueUType = valueUType.union(SequenceTool.getUType(val));
            valueCardinality = Cardinality.union(valueCardinality, SequenceTool.getCardinality(val));
            if (key.getItemType() != keyAtomicType) {
                keyAtomicType = null;
            }
            if (!isKnownToConform(val, valueItemType)) {
                valueItemType = null;
            }
        }
    }

    /**
     * Ask whether all the items in a sequence are known to conform to a given item type
     * @param value the sequence
     * @param itemType the given item type
     * @return true if all the items conform; false if not, or if the information cannot
     * be efficiently determined
     */

    private static boolean isKnownToConform(Sequence value, ItemType itemType) {
        // Problem is we don't have access to a TypeHierarchy object...
        if (itemType == AnyItemType.getInstance()) {
            return true;
        }
        try {
            SequenceIterator iter = value.iterate();
            Item item;
            while ((item = iter.next()) != null) {
                if (item instanceof AtomicValue) {
                    if (itemType instanceof AtomicType) {
                        if (!Type.isSubType(((AtomicValue)item).getItemType(), (AtomicType)itemType)) {
                            return false;
                        }
                    } else {
                        return false;
                    }
                } else if (item instanceof NodeInfo) {
                    if (itemType instanceof NodeTest) {
                        if (!((NodeTest)itemType).matchesNode((NodeInfo)item)) {
                            return false;
                        }
                    } else {
                        return false;
                    }
                } else {
                    // functions, maps, arrays: give up (this is only an optimization)
                    return false;
                }
            }
            return true;
        } catch (XPathException e) {
            return false;
        }
    }

    /**
     * Get an item type to which all the values in a sequence are known to conform
     * @param val the sequence
     * @return the type of the first item in the sequence, provided that all subsequent
     * values in the sequence are known to conform to this type; otherwise item().
     */

    private ItemType getItemTypeOfSequence(Sequence val) {
        try {
            Item first = val.head();
            if (first == null) {
                return AnyItemType.getInstance();
            } else {
                ItemType type;
                if (first instanceof AtomicValue) {
                    type = ((AtomicValue)first).getItemType();
                } else if (first instanceof NodeInfo) {
                    type = NodeKindTest.makeNodeKindTest(((NodeInfo)first).getNodeKind());
                } else {
                    type = AnyFunctionType.getInstance();
                }
                if (isKnownToConform(val, type)) {
                    return type;
                } else {
                    return AnyItemType.getInstance();
                }
            }
        } catch (XPathException e) {
            return AnyItemType.getInstance();
        }
    }

    /**
     * Get the size of the map
     */

    public int size() {
        if (entries >= 0) {
            return entries;
        }
        int count = 0;
        //noinspection UnusedDeclaration
        for (KeyValuePair entry: this) {
            count++;
        }
        return entries = count;
    }

    /**
     * Ask whether the map is empty
     *
     * @return true if and only if the size of the map is zero
     */
    public boolean isEmpty() {
        return entries == 0 || !imap.iterator().hasNext();
    }

    /**
     * Ask whether the map conforms to a given map type
     *
     * @param requiredKeyType   the required keyType
     * @param requiredValueType the required valueType
     * @param th        the type hierarchy cache for the configuration
     * @return true if the map conforms to the required type
     */
    @Override
    public boolean conforms(AtomicType requiredKeyType, SequenceType requiredValueType, TypeHierarchy th) {
        if (isEmpty()) {
            return true;
        }
        if (keyAtomicType == requiredKeyType && valueItemType == requiredValueType.getPrimaryType() &&
                Cardinality.subsumes(requiredValueType.getCardinality(), valueCardinality)) {
            return true;
        }
        boolean needFullCheck = false;
        if (requiredKeyType != BuiltInAtomicType.ANY_ATOMIC) {
            ItemType upperBoundKeyType = keyUType.toItemType();
            int rel = th.relationship(requiredKeyType, upperBoundKeyType);
            if (rel == TypeHierarchy.SAME_TYPE || rel == TypeHierarchy.SUBSUMES) {
                // The key type is matched
            } else if (rel == TypeHierarchy.DISJOINT) {
                return false;
            } else {
                needFullCheck = true;
            }
        }
        ItemType requiredValueItemType = requiredValueType.getPrimaryType();
        if (requiredValueItemType != BuiltInAtomicType.ANY_ATOMIC) {
            ItemType upperBoundValueType = valueUType.toItemType();
            int rel = th.relationship(requiredValueItemType, upperBoundValueType);
            if (rel == TypeHierarchy.SAME_TYPE || rel == TypeHierarchy.SUBSUMES) {
                // The value type is matched
            } else if (rel == TypeHierarchy.DISJOINT) {
                return false;
            } else {
                needFullCheck = true;
            }
        }
        int requiredValueCard = requiredValueType.getCardinality();
        if (!Cardinality.subsumes(requiredValueCard, valueCardinality)) {
            needFullCheck = true;
        }

        if (needFullCheck) {
            // we need to test the entries individually
            AtomicIterator keyIter = keys();
            AtomicValue key;
            while ((key = keyIter.next()) != null) {
                if (!requiredKeyType.matches(key, th)) {
                    return false;
                }
                Sequence val = get(key);
                try {
                    if (!requiredValueType.matches(val, th)) {
                        return false;
                    }
                } catch (XPathException e) {
                    throw new AssertionError(e); // cannot happen with a grounded sequence
                }
            }
        }

        return true;

    }

    /**
     * Get the type of the map. This method is used largely for diagnostics, to report
     * the type of a map when it differs from the required type. This method has the side-effect
     * of updating the internal type information held within the map.
     *
     * @param th The type hierarchy cache for the configuration
     * @return the type of this map
     */
    @Override
    public MapType getItemType(TypeHierarchy th) {
        AtomicType keyType = null;
        ItemType valueType = null;
        int valueCard = 0;
        // we need to test the entries individually
        AtomicIterator keyIter = keys();
        AtomicValue key;
        while ((key = keyIter.next()) != null) {
            Sequence val = get(key);
            if (keyType == null) {
                keyType = key.getItemType();
                valueType = SequenceTool.getItemType(val, th);
                valueCard = SequenceTool.getCardinality(val);
            } else {
                keyType = (AtomicType)Type.getCommonSuperType(keyType, key.getItemType(), th);
                valueType = Type.getCommonSuperType(valueType, SequenceTool.getItemType(val, th), th);
                valueCard = Cardinality.union(valueCard, SequenceTool.getCardinality(val));
            }
        }
        if (keyType == null) {
            // implies the map is empty
            this.keyUType = UType.VOID;
            this.valueUType = UType.VOID;
            this.valueCardinality = 0;
            return MapType.ANY_MAP_TYPE;
        } else {
            this.keyUType = keyType.getUType();
            this.valueUType = valueType.getUType();
            this.valueCardinality = valueCard;
            return new MapType(keyType, SequenceType.makeSequenceType(valueType, valueCard));
        }
    }

    /**
     * Get the lowest common item type of the keys in the map
     *
     * @return the most specific type to which all the keys belong. If the map is
     *         empty, return UType.VOID (the type with no instances)
     */
    public UType getKeyUType() {
        return keyUType;
    }

    /**
     * Get the roles of the arguments, for the purposes of streaming
     *
     * @return an array of OperandRole objects, one for each argument
     */
    public OperandRole[] getOperandRoles() {
        return new OperandRole[]{OperandRole.SINGLE_ATOMIC};
    }

    /**
     * Create a new map containing the existing entries in the map plus an additional entry,
     * without modifying the original. If there is already an entry with the specified key,
     * this entry is replaced by the new entry.
     *
     * @param key     the key of the new entry
     * @param value   the value associated with the new entry
     * @return the new map containing the additional entry
     */

    public HashTrieMap addEntry(AtomicValue key, Sequence value) {
        boolean empty = isEmpty();
        ImmutableMap<AtomicMatchKey, KeyValuePair> imap2 = imap.put(makeKey(key), new KeyValuePair(key, value));
        HashTrieMap t2 = new HashTrieMap(imap2);
        t2.valueCardinality = this.valueCardinality;
        t2.keyUType = keyUType;
        t2.valueUType = valueUType;
        t2.keyAtomicType = keyAtomicType;
        t2.valueItemType = valueItemType;
        t2.updateTypeInformation(key, value, empty);
        return t2;
    }

    /**
     * Add a new entry to this map. Since the map is supposed to be immutable, this method
     * must only be called while initially populating the map, and must not be called if
     * anyone else might already be using the map.
     *
     * @param key     the key of the new entry. Any existing entry with this key is replaced.
     * @param value   the value associated with the new entry
     * @return true if an existing entry with the same key was replaced
     */

    public boolean initialPut(AtomicValue key, Sequence value) {
        boolean empty = isEmpty();
        boolean exists = get(key) != null;
        imap = imap.put(makeKey(key), new KeyValuePair(key, value));
        updateTypeInformation(key, value, empty);
        entries = -1;
        return exists;
    }

    private AtomicMatchKey makeKey(AtomicValue key) {
        return key.asMapKey();
    }


    /**
     * Remove an entry from the map
     *
     * @param key     the key of the entry to be removed
     * @return a new map in which the requested entry has been removed; or this map
     *         unchanged if the specified key was not present
     */

    public HashTrieMap remove(AtomicValue key) {
        ImmutableMap<AtomicMatchKey, KeyValuePair> m2 = imap.remove(makeKey(key));
        if (m2 == imap) {
            // The key is not present; the map is unchanged
            return this;
        }
        HashTrieMap result = new HashTrieMap(m2);
        result.keyUType = keyUType;
        result.valueUType = valueUType;
        result.valueCardinality = valueCardinality;
        result.entries = entries-1;
        return result;
    }

    /**
     * Get an entry from the Map
     *
     * @param key     the value of the key
     * @return the value associated with the given key, or null if the key is not present in the map
     */

    public Sequence get(AtomicValue key)  {
        KeyValuePair o = imap.get(makeKey(key));
        return o==null ? null : o.value;
    }

    /**
     * Get an key/value pair from the Map
     *
     * @param key     the value of the key
     * @return the key-value-pair associated with the given key, or null if the key is not present in the map
     */

    public KeyValuePair getKeyValuePair(AtomicValue key) {
        return imap.get(makeKey(key));
    }

    /**
     * Get the set of all key values in the map
     * @return an iterator over the keys, in undefined order
     */

    public AtomicIterator keys() {
        return new AtomicIterator() {

            int pos = 0;
            Iterator<Tuple2<AtomicMatchKey, KeyValuePair>> base = imap.iterator();

            public AtomicValue next() {
                if (base.hasNext()) {
                    AtomicValue curr = base.next()._2.key;
                    pos++;
                    return curr;
                } else {
                    pos = -1;
                    return null;
                }
            }

            public void close() {
            }

            public int getProperties() {
                return 0;
            }
        };

    }

    /**
     * Get the set of all key-value pairs in the map
     *
     * @return an iterator over the key-value pairs
     */
    public Iterator<KeyValuePair> iterator() {
        return new Iterator<KeyValuePair>() {

            Iterator<Tuple2<AtomicMatchKey, KeyValuePair>> base = imap.iterator();

            public boolean hasNext() {
                return base.hasNext();
            }

            public KeyValuePair next() {
                return base.next()._2;
            }

            public void remove() {
                base.remove();
            }
        };

    }

    /**
     * Get the item type of this item as a function item. Note that this returns the generic function
     * type for maps, not a type related to this specific map.
     *
     * @return the function item's type
     */
    public FunctionItemType getFunctionItemType(/*@Nullable*/) {
        //return new MapType(getKeyType(), getValueType());
        return MapType.ANY_MAP_TYPE;
    }

    /**
     * Get the name of the function, or null if it is anonymous
     *
     * @return the function name, or null for an anonymous inline function
     */
    public StructuredQName getFunctionName() {
        return null;
    }

    /**
     * Get a description of this function for use in error messages. For named functions, the description
     * is the function name (as a lexical QName). For others, it might be, for example, "inline function",
     * or "partially-applied ends-with function".
     *
     * @return a description of the function for use in error messages
     */
    public String getDescription() {
        return "map";
    }

    /**
     * Get the arity of the function
     *
     * @return the number of arguments in the function signature
     */
    public int getArity() {
        return 1;
    }

    /**
     * Invoke the function
     *
     * @param context the XPath dynamic evaluation context
     * @param args    the actual arguments to be supplied
     * @return the result of invoking the function
     * @throws net.sf.saxon.trans.XPathException if an error occurs evaluating
     * the supplied argument
     *
     */
    public Sequence call(XPathContext context, Sequence[] args) throws XPathException {
        AtomicValue key = (AtomicValue) args[0].head();
        Sequence value = get(key);
        if (value == null) {
            return EmptySequence.getInstance();
        } else {
            return value;
        }
    }

    /**
     * Get the value of the item as a string. For nodes, this is the string value of the
     * node as defined in the XPath 2.0 data model, except that all nodes are treated as being
     * untyped: it is not an error to get the string value of a node with a complex type.
     * For atomic values, the method returns the result of casting the atomic value to a string.
     * <p/>
     * If the calling code can handle any CharSequence, the method {@link #getStringValueCS} should
     * be used. If the caller requires a string, this method is preferred.
     *
     * @return the string value of the item
     * @throws UnsupportedOperationException if the item is a function item (an unchecked exception
     *                                       is used here to avoid introducing exception handling to a large number of paths where it is not
     *                                       needed)
     * @see #getStringValueCS
     * @since 8.4
     */
    public String getStringValue() {
        throw new UnsupportedOperationException("A map has no string value");
    }

    /**
     * Get the string value of the item as a CharSequence. This is in some cases more efficient than
     * the version of the method that returns a String. The method satisfies the rule that
     * <code>X.getStringValueCS().toString()</code> returns a string that is equal to
     * <code>X.getStringValue()</code>.
     * <p/>
     * Note that two CharSequence values of different types should not be compared using equals(), and
     * for the same reason they should not be used as a key in a hash table.
     * <p/>
     * If the calling code can handle any CharSequence, this method should
     * be used. If the caller requires a string, the {@link #getStringValue} method is preferred.
     *
     * @return the string value of the item
     * @throws UnsupportedOperationException if the item is a function item (an unchecked exception
     *                                       is used here to avoid introducing exception handling to a large number of paths where it is not
     *                                       needed)
     * @see #getStringValue
     * @since 8.4
     */
    public CharSequence getStringValueCS() {
        throw new UnsupportedOperationException("A map has no string value");
    }

    /**
     * Get the typed value of the item.
     * <p/>
     * For a node, this is the typed value as defined in the XPath 2.0 data model. Since a node
     * may have a list-valued data type, the typed value is in general a sequence, and it is returned
     * in the form of a SequenceIterator.
     * <p/>
     * If the node has not been validated against a schema, the typed value
     * will be the same as the string value, either as an instance of xs:string or as an instance
     * of xs:untypedAtomic, depending on the node kind.
     * <p/>
     * For an atomic value, this method returns an iterator over a singleton sequence containing
     * the atomic value itself.
     *
     * @return an iterator over the items in the typed value of the node or atomic value. The
     *         items returned by this iterator will always be atomic values.
     * @throws net.sf.saxon.trans.XPathException
     *          where no typed value is available, for example in the case of
     *          an element with complex content
     * @since 8.4
     */
    public SequenceIterator getTypedValue() throws XPathException {
        throw new XPathException("A map has no typed value");
    }

    /**
     * Test whether this FunctionItem is deep-equal to another function item,
     * under the rules of the deep-equal function
     *
     * @param other the other function item
     */
    public boolean deepEquals(Function other, XPathContext context, AtomicComparer comparer, int flags) throws XPathException {
        if (other instanceof MapItem &&
                ((MapItem) other).size() == size()) {
            AtomicIterator keys = keys();
            AtomicValue key;
            while ((key = keys.next()) != null) {
                Sequence thisValue = get(key);
                Sequence otherValue = ((MapItem) other).get(key);
                if (otherValue == null) {
                    return false;
                }
                if (!DeepEqual.deepEqual(otherValue.iterate(),
                    thisValue.iterate(), comparer, context, flags)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /*@Nullable*/
    public MapItem itemAt(int n) {
        return n == 0 ? this : null;
    }

    public boolean effectiveBooleanValue() throws XPathException {
        throw new XPathException("A map item has no effective boolean value");
    }

    /**
     * Returns a string representation of the object.
     * @return a string representation of the object.
     */
    @Override
    public String toString() {
        FastStringBuffer buffer = new FastStringBuffer(256);
        buffer.append("map{");
        for (KeyValuePair pair : this) {
            if (buffer.length() > 4) {
                buffer.append(",");
            }
            buffer.append(pair.key.toString());
            buffer.append(":");
            buffer.append(pair.value.toString());
        }
        buffer.append("}");
        return buffer.toString();
    }

    /**
     * Output information about this function item to the diagnostic explain() output
     */
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("map");
        out.emitAttribute("size", size() + "");
        out.endElement();
    }

    public boolean isTrustedResultType() {
        return true;
    }

    public void diagnosticDump() {
        System.err.println("Map details:");
        Iterator<Tuple2<AtomicMatchKey, KeyValuePair>> iter = imap.iterator();
        while (iter.hasNext()) {
            Tuple2<AtomicMatchKey, KeyValuePair> entry = iter.next();
            AtomicMatchKey k1 = entry._1;
            AtomicValue k2 = entry._2.key;
            Sequence v = entry._2.value;
            System.err.println(k1.getClass() + " " + k1 +
                                       " #:" + k1.hashCode() +
                                       " = (" + k2.getClass() + " " + k2 + " : " + v + ")");
        }
    }
}

// Copyright (c) 2017 Saxonica Limited.



