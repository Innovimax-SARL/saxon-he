////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.arrays;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.*;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.SequenceType;

/**
 * An instance of this class represents a specific array item type, for example
 * function(xs:int) as xs:boolean
 */
public class ArrayItemType extends AnyFunctionType {

    public final static ArrayItemType ANY_ARRAY_TYPE = new ArrayItemType(SequenceType.ANY_SEQUENCE);

    private SequenceType memberType;

    public ArrayItemType(SequenceType memberType) {
        this.memberType = memberType;
    }

    /**
     * Get the type of the members of the array
     * @return the type to which all members of the array must conform
     */

    public SequenceType getMemberType() {
        return memberType;
    }

    /**
     * Ask whether this function item type is a map type. In this case function coercion (to the map type)
     * will never succeed.
     *
     * @return true if this FunctionItemType is a map type
     */
    @Override
    public boolean isMapType() {
        return false;
    }

    /**
     * Ask whether this function item type is an array type. In this case function coercion (to the array type)
     * will never succeed.
     *
     * @return true if this FunctionItemType is an array type
     */
    @Override
    public boolean isArrayType() {
        return true;
    }

    /**
     * Ask whether values of this type are atomizable
     *
     * @return true unless it is known that these items will be elements with element-only
     *         content, in which case return false
     */
    @Override
    public boolean isAtomizable() {
        return true;
    }

    /**
     * Get the item type of the atomic values that will be produced when an item
     * of this type is atomized
     *
     * @return the item type of the atomic values that will be produced when an item
     *         of this type is atomized
     */
    @Override
    public PlainType getAtomizedItemType() {
        return memberType.getPrimaryType().getAtomizedItemType();
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
     * Get the argument types of this array, viewed as a function
     *
     * @return the list of argument types of this array, viewed as a function
     */

    public SequenceType[] getArgumentTypes() {
        // regardless of the key type, a function call on this map can supply any atomic value
        return new SequenceType[]{BuiltInAtomicType.INTEGER.one()};
    }

    /**
     * Test whether a given item conforms to this type
     *
     * @param item The item to be tested
     * @param th
     * @return true if the item is an instance of this type; false otherwise
     */
    @Override
    public boolean matches(Item item, TypeHierarchy th) throws XPathException{
        if (!(item instanceof ArrayItem)) {
            return false;
        }
        if (this == ANY_ARRAY_TYPE) {
            return true;
        } else {
            for (Sequence s : (ArrayItem) item){
                if (!memberType.matches(s, th)){
                    return false;
                }
            }
            return  true;
        }
    }

    /**
     * Get the result type of this array, viewed as a function
     *
     * @return the result type of this array, viewed as a function
     */

    public SequenceType getResultType() {
        return memberType;
    }

    /**
     * Produce a representation of this type name for use in error messages.
     *
     * @return a string representation of the type, in notation resembling but not necessarily
     *         identical to XPath syntax
     */
    public String toString() {
        if (this.equals(ANY_ARRAY_TYPE)) {
            return "array(*)";
        } else {
            FastStringBuffer sb = new FastStringBuffer(100);
            sb.append("array(");
            sb.append(memberType.toString());
            sb.append(")");
            return sb.toString();
        }
    }

    /**
     * Test whether this array type equals another array type
     */

    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other instanceof ArrayItemType) {
            ArrayItemType f2 = (ArrayItemType) other;
            return memberType.equals(f2.memberType);
        }
        return false;
    }

    /**
     * Returns a hash code value for the object.
     */
    @Override
    public int hashCode() {
        return memberType.hashCode();
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
        } else if (other == ArrayItemType.ANY_ARRAY_TYPE) {
            return TypeHierarchy.SUBSUMED_BY;
        } else if (other.isMapType()){
            return TypeHierarchy.DISJOINT;
        } else if (other instanceof ArrayItemType) {
            ArrayItemType f2 = (ArrayItemType) other;
            return th.sequenceTypeRelationship(memberType, f2.memberType);

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
        //#ifdefined HOF                                                          ~
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
        memberType.getPrimaryType().visitNamedSchemaComponents(visitor);
    }

    /**
     * Generate Javascript code to test whether an item conforms to this item type
     *
     * @param knownToBe a type that this item is known to conform to
     * @param targetVersion
     * @return a Javascript instruction or sequence of instructions, which can be used as the body
     * of a Javascript function, and which returns a boolean indication whether the value of the
     * variable "item" is an instance of this item type.
     * @throws XPathException if JS code cannot be generated for this item type, for example because
     *                        the test is schema-aware.
     */
    @Override
    public String generateJavaScriptItemTypeTest(ItemType knownToBe, int targetVersion) throws XPathException {
        if (this == ANY_ARRAY_TYPE) {
            return "return SaxonJS.U.isArray(item)";
        }
        FastStringBuffer fsb = new FastStringBuffer(256);
        fsb.append("function v(item) {" + memberType.getPrimaryType().generateJavaScriptItemTypeTest(AnyItemType.getInstance(), targetVersion) + "};");
        fsb.append(Cardinality.generateJavaScriptChecker(memberType.getCardinality()));
        fsb.append("return SaxonJS.U.isArray(item) && " +
                           "SaxonJS.U.ForArray(item.value).every(function(seq){return c(seq.length) && SaxonJS.U.ForArray(seq).every(v)});");
        return fsb.toString();
    }
}

// Copyright (c) 2015-2016 Saxonica Limited.