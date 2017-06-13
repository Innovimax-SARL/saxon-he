////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.ma.arrays.ArrayItem;
import net.sf.saxon.ma.arrays.ArrayItemType;
import net.sf.saxon.ma.map.KeyValuePair;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.ma.map.MapType;
import net.sf.saxon.om.Function;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.query.AnnotationList;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.value.SequenceType;

/**
 * An instance of this class represents a specific function item type, for example
 * function(xs:int) as xs:boolean
 */
public class SpecificFunctionType extends AnyFunctionType {

    private SequenceType[] argTypes;
    private SequenceType resultType;
    private AnnotationList annotations;

    public SpecificFunctionType(SequenceType[] argTypes, SequenceType resultType) {
        this.argTypes = argTypes;
        this.resultType = resultType;
        this.annotations = AnnotationList.EMPTY;
    }

    public SpecificFunctionType(SequenceType[] argTypes, SequenceType resultType, AnnotationList annotations) {
        this.argTypes = argTypes;
        this.resultType = resultType;
        this.annotations = annotations;
    }

    /**
     * Get the arity (number of arguments) of this function type
     *
     * @return the number of argument types in the function signature
     */

    public int getArity() {
        return argTypes.length;
    }

    /**
     * Get the argument types
     *
     * @return the list of argument types
     */

    public SequenceType[] getArgumentTypes() {
        return argTypes;
    }

    /**
     * Get the result type
     *
     * @return the result type
     */

    public SequenceType getResultType() {
        return resultType;
    }

    /**
     * Get the list of annotation assertions defined on this function item type.
     *
     * @return the list of annotation assertions, or an empty list if there are none
     */

    @Override
    public AnnotationList getAnnotationAssertions() {
        return annotations;
    }

    /**
     * Produce a representation of this type name for use in error messages.
     *
     * @return a string representation of the type, in notation resembling but not necessarily
     *         identical to XPath syntax
     */
    public String toString() {
        FastStringBuffer sb = new FastStringBuffer(100);
        sb.append("(function(");
        for (int i = 0; i < argTypes.length; i++) {
            sb.append(argTypes[i].toString());
            if (i < argTypes.length - 1) {
                sb.append(", ");
            }
        }
        sb.append(") as ");
        sb.append(resultType.toString());
        sb.append(')');
        return sb.toString();
    }

    /**
     * Test whether this function type equals another function type
     */

    public boolean equals(Object other) {
        if (other instanceof SpecificFunctionType) {
            SpecificFunctionType f2 = (SpecificFunctionType) other;
            if (!resultType.equals(f2.resultType)) {
                return false;
            }
            if (argTypes.length != f2.argTypes.length) {
                return false;
            }
            for (int i = 0; i < argTypes.length; i++) {
                if (!argTypes[i].equals(f2.argTypes[i])) {
                    return false;
                }
            }
            // Compare the annotations
            if (!getAnnotationAssertions().equals(f2.getAnnotationAssertions())) {
                return false;
            }
            return true;
        }
        return false;
    }

    /**
     * Returns a hash code value for the object.
     */
    @Override
    public int hashCode() {
        int h = resultType.hashCode() ^ argTypes.length;
        for (SequenceType argType : argTypes) {
            h ^= argType.hashCode();
        }
        return h;
    }

    /**
     * Determine the relationship of one function item type to another. This method is only concerned
     * with the type signatures of the two function item types, and not with their annotation assertions.
     *
     * @return for example {@link TypeHierarchy#SUBSUMES}, {@link TypeHierarchy#SAME_TYPE}
     */

    public int relationship(FunctionItemType other, TypeHierarchy th) {
        if (other == AnyFunctionType.getInstance()) {
            return TypeHierarchy.SUBSUMED_BY;
        } else if (equals(other)) {
            return TypeHierarchy.SAME_TYPE;
        } else if (other instanceof ArrayItemType || other instanceof MapType) {
            int rrel = other.relationship(this, th);
            switch (rrel) {
                case TypeHierarchy.SUBSUMES:
                    // A function can never substitute for an array or map type
                    return TypeHierarchy.DISJOINT;
                case TypeHierarchy.SUBSUMED_BY:
                    return TypeHierarchy.SUBSUMES;
                default:
                    return rrel;
            }
        } else {
            if (argTypes.length != other.getArgumentTypes().length) {
                return TypeHierarchy.DISJOINT;
            }
            boolean wider = false;
            boolean narrower = false;
            for (int i = 0; i < argTypes.length; i++) {
                int argRel = th.sequenceTypeRelationship(argTypes[i], other.getArgumentTypes()[i]);
                switch (argRel) {
                    case TypeHierarchy.DISJOINT:
                        return TypeHierarchy.DISJOINT;
                    case TypeHierarchy.SUBSUMES:
                        narrower = true;
                        break;
                    case TypeHierarchy.SUBSUMED_BY:
                        wider = true;
                        break;
                    case TypeHierarchy.OVERLAPS:
                        wider = true;
                        narrower = true;
                        break;
                    case TypeHierarchy.SAME_TYPE:
                    default:
                        break;
                }
            }

            int resRel = th.sequenceTypeRelationship(resultType, other.getResultType());
            switch (resRel) {
                case TypeHierarchy.DISJOINT:
                    return TypeHierarchy.DISJOINT;
                case TypeHierarchy.SUBSUMES:
                    wider = true;
                    break;
                case TypeHierarchy.SUBSUMED_BY:
                    narrower = true;
                    break;
                case TypeHierarchy.OVERLAPS:
                    wider = true;
                    narrower = true;
                    break;
                case TypeHierarchy.SAME_TYPE:
                default:
                    break;
            }

            if (wider) {
                if (narrower) {
                    return TypeHierarchy.OVERLAPS;
                } else {
                    return TypeHierarchy.SUBSUMES;
                }
            } else {
                if (narrower) {
                    return TypeHierarchy.SUBSUMED_BY;
                } else {
                    return TypeHierarchy.SAME_TYPE;
                }
            }
        }
    }

    /**
     * Test whether a given item conforms to this type
     *
     *
     * @param item The item to be tested
     * @param th the type hierarchy cache
     * @return true if the item is an instance of this type; false otherwise
     */
    @Override
    public boolean matches(Item item, TypeHierarchy th) {
        if (!(item instanceof Function)) {
            return false;
        }


        if (item instanceof MapItem) {
            // Bug 2938: Essentially a map is an instance of function(X) as Y
            // if (a) X is a subtype of xs:anyAtomicType, and (b) all the values in the map are instances of Y
            if (getArity() == 1 &&
                    argTypes[0].getCardinality() == StaticProperty.EXACTLY_ONE &&
                    argTypes[0].getPrimaryType().isPlainType()) {
                for (KeyValuePair pair : (MapItem) item) {
                    try {
                        if (!resultType.matches(pair.value, th)) {
                            return false;
                        }
                    } catch (XPathException e) {
                        return false;
                    }
                }
                return true;
            } else {
                return false;
            }
        }

        if (item instanceof ArrayItem) {
            // Bug 2938: Essentially a array is an instance of function(X) as Y
            // if (a) X is a subtype of xs:integer, and (b) all the values in the array are instances of Y
            if (getArity() == 1 &&
                    argTypes[0].getCardinality() == StaticProperty.EXACTLY_ONE &&
                    argTypes[0].getPrimaryType().isPlainType()) {
                int rel = th.relationship(argTypes[0].getPrimaryType(), BuiltInAtomicType.INTEGER);
                if (!(rel == TypeHierarchy.SAME_TYPE || rel == TypeHierarchy.SUBSUMED_BY)) {
                    return false;
                }
                for (Sequence member : (ArrayItem) item) {
                    try {
                        if (!resultType.matches(member, th)) {
                            return false;
                        }
                    } catch (XPathException e) {
                        return false;
                    }
                }
                return true;
            } else {
                return false;
            }
        }

        int rel = th.relationship(((Function) item).getFunctionItemType(), this);
        return rel == TypeHierarchy.SAME_TYPE || rel == TypeHierarchy.SUBSUMED_BY;
    }

    public Expression makeFunctionSequenceCoercer(Expression exp, RoleDiagnostic role)
            throws XPathException {
        return exp.getConfiguration().makeFunctionSequenceCoercer(this, exp, role);
    }

    /**
     * Visit all the schema components used in this ItemType definition
     *
     * @param visitor the visitor class to be called when each component is visited
     */

    public void visitNamedSchemaComponents(SchemaComponentVisitor visitor) throws XPathException {
        resultType.getPrimaryType().visitNamedSchemaComponents(visitor);
        for (SequenceType argType : argTypes) {
            argType.getPrimaryType().visitNamedSchemaComponents(visitor);
        }
    }

    /**
     * Generate Javascript code to test whether an item conforms to this item type
     *
     * @return a Javascript instruction or sequence of instructions, which can be used as the body
     * of a Javascript function, and which returns a boolean indication whether the value of the
     * variable "item" is an instance of this item type.
     * @throws XPathException if JS code cannot be generated for this item type, for example because
     *                        the test is schema-aware.
     * @param knownToBe
     */
    @Override
    public String generateJavaScriptItemTypeTest(ItemType knownToBe) throws XPathException {
        throw new XPathException("Cannot generate JS code for function type tests", SaxonErrorCode.SXJS0001);
    }
}
