////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import net.sf.saxon.om.Item;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.SequenceType;


/**
 * ItemType is an interface that allows testing of whether an Item conforms to an
 * expected type. ItemType represents the types in the type hierarchy in the XPath model,
 * as distinct from the schema model: an item type is either item() (matches everything),
 * a node type (matches nodes), an atomic type (matches atomic values), or empty()
 * (matches nothing). Atomic types, represented by the class AtomicType, are also
 * instances of SimpleType in the schema type hierarchy. Node Types, represented by
 * the class NodeTest, are also Patterns as used in XSLT.
 * <p/>
 * <p>Saxon assumes that apart from {@link AnyItemType} (which corresponds to <code>item()</item>
 * and matches anything), every ItemType will be either an {@link AtomicType}, a {@link net.sf.saxon.pattern.NodeTest},
 * or a {@link FunctionItemType}. User-defined implementations of ItemType must therefore extend one of those
 * three classes/interfaces.</p>
 *
 * @see AtomicType
 * @see net.sf.saxon.pattern.NodeTest
 * @see FunctionItemType
 */

public interface ItemType {

    /**
     * Determine whether this item type is an atomic type
     *
     * @return true if this is ANY_ATOMIC_TYPE or a subtype thereof
     */

    boolean isAtomicType();

    /**
     * Determine whether this item type is a plain type (that is, whether it can ONLY match
     * atomic values)
     *
     * @return true if this is ANY_ATOMIC_TYPE or a subtype thereof, or a
     *         "plain" union type (that is, unions of atomic types that impose no further restrictions).
     *         Return false if this is a union type whose member types are not all known.
     */

    boolean isPlainType();

    /**
     * Test whether a given item conforms to this type
     *
     *
     * @param item    The item to be tested
     * @param th      The type hierarchy cache. Currently used only when matching function items.
     * @return true if the item is an instance of this type; false otherwise
     */

    boolean matches(Item item, TypeHierarchy th) throws XPathException;

    /**
     * Get the primitive item type corresponding to this item type. For item(),
     * this is Type.ITEM. For node(), it is Type.NODE. For specific node kinds,
     * it is the value representing the node kind, for example Type.ELEMENT.
     * For anyAtomicValue and union types it is Type.ATOMIC_VALUE. For numeric it is Type.NUMBER.
     * For other atomic types it is the primitive type as defined in XML Schema,
     * except that integer, xs:dayTimeDuration, and xs:yearMonthDuration
     * are considered to be primitive types.
     *
     * @return the corresponding primitive type
     */

    /*@NotNull*/
    ItemType getPrimitiveItemType();

    /**
     * Get the primitive type corresponding to this item type. For item(),
     * this is Type.ITEM. For node(), it is Type.NODE. For specific node kinds,
     * it is the value representing the node kind, for example Type.ELEMENT.
     * For anyAtomicValue it is BuiltInAtomicType.ANY_ATOMIC. For numeric it is Type.NUMBER.
     * For other atomic types it is the primitive type as defined in XML Schema,
     * except that INTEGER is considered to be a primitive type.
     *
     * @return the integer fingerprint of the corresponding primitive type
     */

    int getPrimitiveType();

    /**
     * Get the corresponding {@link UType}. A UType is a union of primitive item
     * types.
     * @return the smallest UType that subsumes this item type
     */

    UType getUType();

    /**
     * Get the item type of the atomic values that will be produced when an item
     * of this type is atomized
     *
     * @return the best available item type of the atomic values that will be produced when an item
     *         of this type is atomized, or null if it is known that atomization will throw an error.
     */

    PlainType getAtomizedItemType();

    /**
     * Ask whether values of this type are atomizable
     *
     * @return true unless it is known that these items will be elements with element-only
     *         content, or function items, in which case return false
     */

    boolean isAtomizable();

    /**
     * Visit all the schema components used in this ItemType definition
     *
     * @param visitor the visitor class to be called when each component is visited
     * @throws net.sf.saxon.trans.XPathException
     *          if an error occurs
     */

    void visitNamedSchemaComponents(SchemaComponentVisitor visitor) throws XPathException;

    /**
     * Generate Javascript code to test whether an item conforms to this item type
     * @param knownToBe An item type that the supplied item is known to conform to; the generated code
     *                  can assume that the item is an instance of this type.
     * @param targetVersion The version of Saxon-JS for which code is being generated. Currently either 1 or 2.
     * @return a Javascript instruction or sequence of instructions, which can be used as the body
     * of a Javascript function, and which returns a boolean indication whether the value of the
     * variable "item" is an instance of this item type.
     * @throws XPathException if JS code cannot be generated for this item type, for example because
     *                        the test is schema-aware.
     *
     */

    String generateJavaScriptItemTypeTest(ItemType knownToBe, int targetVersion) throws XPathException;


    /**
     * Generate Javascript code to convert a supplied Javascript value to this item type,
     * if conversion is possible, or throw an error otherwise.
     * @param errorCode the error to be thrown if conversion is not possible
     * @param targetVersion the version of Saxon-JS for which code is being generated
     * @return a Javascript instruction or sequence of instructions, which can be used as the body
     * of a Javascript function, and which returns the result of conversion to this type, or throws
     * an error if conversion is not possible. The variable "val" will hold the supplied Javascript
     * value.
     */

    String generateJavaScriptItemTypeAcceptor(String errorCode, int targetVersion) throws XPathException;

    /**
     * Extension of the ItemType interface implemented by some item types, to provide
     * a cache of SequenceType objects based on this item type, with different
     * occurrence indicators.
     */

    interface WithSequenceTypeCache extends ItemType {
        /**
         * Get a sequence type representing exactly one instance of this atomic type
         *
         * @return a sequence type representing exactly one instance of this atomic type
         * @since 9.8.0.2
         */

        SequenceType one();
        /**
         * Get a sequence type representing zero or one instances of this atomic type
         *
         * @return a sequence type representing zero or one instances of this atomic type
         * @since 9.8.0.2
         */

        SequenceType zeroOrOne();

        /**
         * Get a sequence type representing one or more instances of this atomic type
         *
         * @return a sequence type representing one or more instances of this atomic type
         * @since 9.8.0.2
         */

        SequenceType oneOrMore();

        /**
         * Get a sequence type representing one or more instances of this atomic type
         *
         * @return a sequence type representing one or more instances of this atomic type
         * @since 9.8.0.2
         */

        SequenceType zeroOrMore();

    }

}

