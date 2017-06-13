////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.map;

import net.sf.saxon.type.SpecificFunctionType;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.om.Item;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.*;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.SequenceType;

/**
 * An instance of this class represents a specific map item type, for example
 * map(x:integer, element(employee))
 */
public class MapType extends AnyFunctionType {

    public final static MapType ANY_MAP_TYPE = new MapType(BuiltInAtomicType.ANY_ATOMIC, SequenceType.ANY_SEQUENCE);

    // The type of a map with no entries. It's handled specially in some static type inferencing rules
    public final static MapType EMPTY_MAP_TYPE = new MapType(BuiltInAtomicType.ANY_ATOMIC, SequenceType.ANY_SEQUENCE, true);

    /**
     * A type that allows a sequence of zero or one map items
     */
    public static final SequenceType OPTIONAL_MAP_ITEM =
            SequenceType.makeSequenceType(ANY_MAP_TYPE, StaticProperty.ALLOWS_ZERO_OR_ONE);

    private AtomicType keyType;
    private SequenceType valueType;
    private boolean mustBeEmpty;

    public MapType(AtomicType keyType, SequenceType valueType) {
        this.keyType = keyType;
        this.valueType = valueType;
        this.mustBeEmpty = false;
    }

    public MapType(AtomicType keyType, SequenceType valueType, boolean mustBeEmpty) {
        this.keyType = keyType;
        this.valueType = valueType;
        this.mustBeEmpty = mustBeEmpty;
    }

    /**
     * Get the type of the keys
     * @return the type to which all keys must conform
     */

    public AtomicType getKeyType() {
        return keyType;
    }

    /**
     * Get the type of the indexed values
     * @return the type to which all associated values must conform
     */

    public SequenceType getValueType() {
        return valueType;
    }

    /**
     * Ask whether this function item type is a map type. In this case function coercion (to the map type)
     * will never succeed.
     *
     * @return true if this FunctionItemType is a map type
     */
    @Override
    public boolean isMapType() {
        return true;
    }

    /**
     * Ask whether this function item type is an array type. In this case function coercion (to the array type)
     * will never succeed.
     *
     * @return true if this FunctionItemType is an array type
     */
    @Override
    public boolean isArrayType() {
        return false;
    }

    /**
     * Test whether a given item conforms to this type
     *
     * @param item The item to be tested
     * @param th
     * @return true if the item is an instance of this type; false otherwise
     */
    @Override
    public boolean matches(Item item, TypeHierarchy th) {
        if (!(item instanceof MapItem)){
            return false;
        }
        if (((MapItem) item).isEmpty()) {
            return true;
        } else if (mustBeEmpty) {
            return false;
        }
        if (this == ANY_MAP_TYPE) {
            return true;
        } else {
            return ((MapItem)item).conforms(keyType, valueType, th);
        }
    }

    /**
     * Get the arity (number of arguments) of this function type
     *
     * @return the number of argument types in the function signature
     */

    public int getArity() {
        return 1;
    }

    /**
     * Get the argument types of this map, viewed as a function
     *
     * @return the list of argument types of this map, viewed as a function
     */

    public SequenceType[] getArgumentTypes() {
        // regardless of the key type, a function call on this map can supply any atomic value
        return new SequenceType[]{SequenceType.makeSequenceType(BuiltInAtomicType.ANY_ATOMIC, StaticProperty.EXACTLY_ONE)};
    }

    /**
     * Get the result type of this map, viewed as a function
     *
     * @return the result type of this map, viewed as a function
     */

    public SequenceType getResultType() {
        // a function call on this map can always return ()
        if (Cardinality.allowsZero(valueType.getCardinality())) {
            return valueType;
        } else {
            return SequenceType.makeSequenceType(
                    valueType.getPrimaryType(), Cardinality.union(valueType.getCardinality(), StaticProperty.ALLOWS_ZERO));
        }
    }

    /**
     * Produce a representation of this type name for use in error messages.
     *
     * @return a string representation of the type, in notation resembling but not necessarily
     *         identical to XPath syntax
     */
    public String toString() {
        if (this == ANY_MAP_TYPE) {
            return "map(*)";
        } else if (this == EMPTY_MAP_TYPE) {
            return "map(\u00B0)";
        } else {
            FastStringBuffer sb = new FastStringBuffer(100);
            sb.append("map(");
            sb.append(keyType.toString());
            sb.append(", ");
            sb.append(valueType.toString());
            sb.append(")");
            return sb.toString();
        }
    }

    /**
     * Test whether this function type equals another function type
     */

    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other instanceof MapType) {
            MapType f2 = (MapType) other;
            return keyType.equals(f2.keyType) && valueType.equals(f2.valueType) && mustBeEmpty == f2.mustBeEmpty;
        }
        return false;
    }

    /**
     * Returns a hash code value for the object.
     */
    @Override
    public int hashCode() {
        return keyType.hashCode() ^ valueType.hashCode();
    }

    /**
     * Determine the relationship of one function item type to another
     *
     * @return for example {@link net.sf.saxon.type.TypeHierarchy#SUBSUMES}, {@link net.sf.saxon.type.TypeHierarchy#SAME_TYPE}
     */

    public int relationship(FunctionItemType other, TypeHierarchy th) {
        if (other == AnyFunctionType.getInstance()) {
            return TypeHierarchy.SUBSUMED_BY;
        } else if (equals(other)) {
            return TypeHierarchy.SAME_TYPE;
        } else if (other == MapType.ANY_MAP_TYPE) {
            return TypeHierarchy.SUBSUMED_BY;
        } else if (other.isArrayType()) {
            return TypeHierarchy.DISJOINT;
        } else if (other instanceof TupleItemType) {
            return TypeHierarchy.inverseRelationship(other.relationship(this, th));
        } else if (other instanceof MapType) {
            MapType f2 = (MapType) other;
            int keyRel = th.relationship(keyType, f2.keyType);
            if (keyRel == TypeHierarchy.DISJOINT) {
                return TypeHierarchy.DISJOINT;
            }
            int valueRel = th.sequenceTypeRelationship(valueType, f2.valueType);

            if (keyRel == TypeHierarchy.DISJOINT || valueRel == TypeHierarchy.DISJOINT) {
                return TypeHierarchy.DISJOINT;
            }
            if (keyRel == valueRel) {
                return keyRel;
            }
            if (keyRel == TypeHierarchy.SAME_TYPE && valueRel == TypeHierarchy.SAME_TYPE) {
                return TypeHierarchy.SAME_TYPE;
            }
            if ((keyRel == TypeHierarchy.SAME_TYPE || keyRel == TypeHierarchy.SUBSUMES) &&
                    (valueRel == TypeHierarchy.SAME_TYPE || valueRel == TypeHierarchy.SUBSUMES)) {
                return TypeHierarchy.SUBSUMES;
            }
            if ((keyRel == TypeHierarchy.SAME_TYPE || keyRel == TypeHierarchy.SUBSUMED_BY) &&
                    (valueRel == TypeHierarchy.SAME_TYPE || valueRel == TypeHierarchy.SUBSUMED_BY)) {
                return TypeHierarchy.SUBSUMED_BY;
            }
            return TypeHierarchy.OVERLAPS;
        } else {
            int rel = TypeHierarchy.DISJOINT;
            //#ifdefined HOF
            rel = new SpecificFunctionType(getArgumentTypes(), getResultType()).relationship(other, th);
            //#endif
            return rel;
        }
    }

    @Override
    public Expression makeFunctionSequenceCoercer(Expression exp, RoleDiagnostic role)
            throws XPathException {
        Expression result = exp;
        //#ifdefined HOF
        result = new SpecificFunctionType(getArgumentTypes(), getResultType()).makeFunctionSequenceCoercer(exp, role);
        //#endif
        return result;
    }

    /**
     * Visit all the schema components used in this ItemType definition
     *
     * @param visitor the visitor class to be called when each component is visited
     */

    public void visitNamedSchemaComponents(SchemaComponentVisitor visitor) throws XPathException {
        keyType.visitNamedSchemaComponents(visitor);
        valueType.getPrimaryType().visitNamedSchemaComponents(visitor);
    }

    /**
     * Generate Javascript code to test whether an item conforms to this item type
     *
     * @param knownToBe a type that this item is known to conform to
     * @return a Javascript instruction or sequence of instructions, which can be used as the body
     * of a Javascript function, and which returns a boolean indication whether the value of the
     * variable "item" is an instance of this item type.
     * @throws XPathException if JS code cannot be generated for this item type, for example because
     *                        the test is schema-aware.
     */
    @Override
    public String generateJavaScriptItemTypeTest(ItemType knownToBe) throws XPathException {
        if (this == ANY_MAP_TYPE) {
            return "return SaxonJS.U.isMap(item)";
        }
        FastStringBuffer fsb = new FastStringBuffer(256);
        fsb.append("function k(item) {" + keyType.generateJavaScriptItemTypeTest(BuiltInAtomicType.ANY_ATOMIC) + "};");
        fsb.append("function v(item) {" + valueType.getPrimaryType().generateJavaScriptItemTypeTest(AnyItemType.getInstance()) + "};");
        int card = valueType.getCardinality();
        fsb.append(Cardinality.generateJavaScriptChecker(card));
        fsb.append("return SaxonJS.U.isMap(item) && item.conforms(k, v, c);");
        return fsb.toString();
    }
}

// Copyright (c) 2011-2016 Saxonica Limited.