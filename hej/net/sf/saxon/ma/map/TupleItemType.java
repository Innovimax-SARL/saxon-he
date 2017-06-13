////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.map;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.*;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An instance of this class represents a specific tuple item type, for example
 * tuple(x: xs:double, y: element(employee)).
 *
 * Tuple types are a Saxon extension introduced in Saxon 9.8. The syntax for constructing
 * a tuple type requires Saxon-PE or higher, but the supporting code is included in
 * Saxon-HE for convenience.
 */
public class TupleItemType extends AnyFunctionType {

    private Map<String, SequenceType> fields = new HashMap<String, SequenceType>();

    public TupleItemType(List<String> names, List<SequenceType> types) {
        for (int i=0; i<names.size(); i++) {
            fields.put(names.get(i), types.get(i));
        }
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
     * Get the type of a given field
     * @param field the name of the field
     * @return the type of the field if it is defined, or null otherwise
     */

    public SequenceType getFieldType(String field) {
        return fields.get(field);
    }

    /**
     * Test whether a given item conforms to this type
     *
     * @param item The item to be tested
     * @param th type hierarchy data
     * @return true if the item is an instance of this type; false otherwise
     */
    @Override
    public boolean matches(Item item, TypeHierarchy th) throws XPathException {
        if (!(item instanceof MapItem)) {
            return false;
        }
        MapItem map = (MapItem)item;
        for (Map.Entry<String, SequenceType> field : fields.entrySet()) {
            Sequence val = map.get(new StringValue(field.getKey()));
            if (val == null) {
                val = EmptySequence.getInstance();
            }
            if (!field.getValue().matches(val, th)) {
                return false;
            }
        }
        return true;
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
        return SequenceType.ANY_SEQUENCE;
    }

    /**
     * Produce a representation of this type name for use in error messages.
     *
     * @return a string representation of the type, in notation resembling but not necessarily
     * identical to XPath syntax
     */
    public String toString() {
        FastStringBuffer sb = new FastStringBuffer(100);
        sb.append("tuple(");
        boolean first = true;
        for (Map.Entry<String, SequenceType> field : fields.entrySet()) {
            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }
            sb.append(field.getKey());
            sb.append(": ");
            sb.append(field.getValue().toString());
        }
        sb.append(")");
        return sb.toString();
    }

    /**
     * Test whether this function type equals another function type
     */

    public boolean equals(Object other) {
        return this == other || other instanceof TupleItemType && fields.equals(((TupleItemType) other).fields);
    }

    /**
     * Returns a hash code value for the object.
     */
    @Override
    public int hashCode() {
        return fields.hashCode();
    }

    /**
     * Determine the relationship of one function item type to another
     *
     * @return for example {@link TypeHierarchy#SUBSUMES}, {@link TypeHierarchy#SAME_TYPE}
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
        } else if (other instanceof MapType) {
            MapType f2 = (MapType) other;
            int keyRel = th.relationship(BuiltInAtomicType.STRING, f2.getKeyType());
            if (keyRel == TypeHierarchy.DISJOINT) {
                return TypeHierarchy.DISJOINT;
            }
            // Let's keep the analysis simple...
            return TypeHierarchy.OVERLAPS;
        } else {
            int rel = TypeHierarchy.DISJOINT;
            rel = new SpecificFunctionType(getArgumentTypes(), getResultType()).relationship(other, th);
            return rel;
        }
    }

    @Override
    public Expression makeFunctionSequenceCoercer(Expression exp, RoleDiagnostic role)
            throws XPathException {
        Expression result = exp;
        result = new SpecificFunctionType(getArgumentTypes(), getResultType()).makeFunctionSequenceCoercer(exp, role);
        return result;
    }

    /**
     * Visit all the schema components used in this ItemType definition
     *
     * @param visitor the visitor class to be called when each component is visited
     */

    public void visitNamedSchemaComponents(SchemaComponentVisitor visitor) throws XPathException {
        for (SequenceType t : fields.values()) {
            t.getPrimaryType().visitNamedSchemaComponents(visitor);
        }
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
        throw new UnsupportedOperationException();
    }
}

// Copyright (c) 2011-2016 Saxonica Limited.