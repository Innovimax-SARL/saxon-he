////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.map;

import net.sf.saxon.om.Function;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.tree.iter.AtomicIterator;
import net.sf.saxon.type.AtomicType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.type.UType;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.SequenceType;

import java.util.Iterator;

/**
 * Interface supported by different implementations of an XDM map item
 */
public interface MapItem extends Function, Iterable<KeyValuePair> {

    /**
     * Get an entry from the Map
     * @param key     the value of the key
     * @return the value associated with the given key, or null if the key is not present in the map
     */

    Sequence get(AtomicValue key);

    /**
     * Get the size of the map
     *
     * @return the number of keys/entries present in this map
     */

    int size();

    /**
     * Ask whether the map is empty
     *
     * @return true if and only if the size of the map is zero
     */

    boolean isEmpty();

    /**
     * Get the set of all key values in the map.
     *
     * @return a set containing all the key values present in the map, in unpredictable order
     */

    AtomicIterator keys();

    /**
     * Get the set of all key-value pairs in the map
     * @return an iterator over the key-value pairs
     */

    Iterator<KeyValuePair> iterator();

    /**
     * Create a new map containing the existing entries in the map plus an additional entry,
     * without modifying the original. If there is already an entry with the specified key,
     * this entry is replaced by the new entry.
     *
     * @param key   the key of the new entry
     * @param value the value associated with the new entry
     * @return the new map containing the additional entry
     */

    MapItem addEntry(AtomicValue key, Sequence value);

    /**
     * Remove an entry from the map
     *
     *
     * @param key     the key of the entry to be removed
     * @return a new map in which the requested entry has been removed; or this map
     *         unchanged if the specified key was not present
     */

    MapItem remove(AtomicValue key);

    /**
     * Ask whether the map conforms to a given map type
     * @param keyType the required keyType
     * @param valueType the required valueType
     * @param th the type hierarchy cache for the configuration
     * @return true if the map conforms to the required type
     */
    boolean conforms(AtomicType keyType, SequenceType valueType, TypeHierarchy th);

    /**
     * Get the type of the map. This method is used largely for diagnostics, to report
     * the type of a map when it differs from the required type.
     * @return the type of this map
     */

    MapType getItemType(TypeHierarchy th);

    /**
     * Get the lowest common item type of the keys in the map
     *
     * @return the most specific type to which all the keys belong. If the map is
     *         empty, return UType.VOID
     */

    UType getKeyUType();

}

// Copyright (c) 2011-2016 Saxonica Limited.
