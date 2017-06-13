////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.ItemChecker;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.om.Function;
import net.sf.saxon.om.Item;
import net.sf.saxon.query.Annotation;
import net.sf.saxon.query.AnnotationList;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.SequenceType;

import java.util.Collections;
import java.util.List;

/**
 * An ItemType representing the type function(*). Subtypes represent function items with more specific
 * type signatures.
 * <p/>
 * <p>Note that although this class has a singleton instance representing the type <code>function(*)</code>,
 * there are also likely to be instances of subclasses representing more specific function types.</p>
 */
public class AnyFunctionType implements FunctionItemType {

    /*@NotNull*/ public final static AnyFunctionType ANY_FUNCTION = new AnyFunctionType();

    /**
     * Get the singular instance of this type (Note however that subtypes of this type
     * may have any number of instances)
     *
     * @return the singular instance of this type
     */

    /*@NotNull*/
    public static AnyFunctionType getInstance() {
        return ANY_FUNCTION;
    }

    /**
     * Get the corresponding {@link net.sf.saxon.type.UType}. A UType is a union of primitive item
     * types.
     *
     * @return the smallest UType that subsumes this item type
     */
    public UType getUType() {
        return UType.FUNCTION;
    }

    /**
     * Determine whether this item type is an atomic type
     *
     * @return true if this is ANY_ATOMIC_TYPE or a subtype thereof
     */
    public boolean isAtomicType() {
        return false;
    }

    /**
     * Determine whether this item type is atomic (that is, whether it can ONLY match
     * atomic values)
     *
     * @return true if this is ANY_ATOMIC_TYPE or a subtype thereof
     */

    public boolean isPlainType() {
        return false;
    }

    /**
     * Ask whether this function item type is a map type. In this case function coercion (to the map type)
     * will never succeed.
     *
     * @return true if this FunctionItemType is a map type
     */
    public boolean isMapType() {
        return false;
    }

    /**
     * Ask whether this function item type is an array type. In this case function coercion (to the array type)
     * will never succeed.
     *
     * @return true if this FunctionItemType is an array type
     */

    public boolean isArrayType() {
        return false;
    }

    /**
     * Get the argument types of the function
     *
     * @return the argument types, as an array of SequenceTypes, or null if this is the generic function
     *         type function(*)
     */
    /*@Nullable*/
    public SequenceType[] getArgumentTypes() {
        return null;
    }

    /**
     * Get the list of annotation assertions defined on this function item type.
     * @return the list of annotation assertions, or an empty list if there are none
     */

    public AnnotationList getAnnotationAssertions() {
        return AnnotationList.EMPTY;
    }

    /**
     * Test whether a given item conforms to this type
     *
     *
     *
     * @param item    The item to be tested
     * @param th
     * @return true if the item is an instance of this type; false otherwise
     */
    public boolean matches(Item item, TypeHierarchy th) throws XPathException {
        return item instanceof Function;
    }

    /**
     * Get the primitive item type corresponding to this item type. For item(),
     * this is Type.ITEM. For node(), it is Type.NODE. For specific node kinds,
     * it is the value representing the node kind, for example Type.ELEMENT.
     * For anyAtomicValue it is Type.ATOMIC_VALUE. For numeric it is Type.NUMBER.
     * For other atomic types it is the primitive type as defined in XML Schema,
     * except that integer, xs:dayTimeDuration, and xs:yearMonthDuration
     * are considered to be primitive types. For function items it is the singular
     * instance FunctionItemType.getInstance().
     */

    /*@NotNull*/
    public final ItemType getPrimitiveItemType() {
        return ANY_FUNCTION;
    }

    /**
     * Get the primitive type corresponding to this item type. For item(),
     * this is Type.ITEM. For node(), it is Type.NODE. For specific node kinds,
     * it is the value representing the node kind, for example Type.ELEMENT.
     * For anyAtomicValue it is Type.ATOMIC_VALUE. For numeric it is Type.NUMBER.
     * For other atomic types it is the primitive type as defined in XML Schema,
     * except that INTEGER is considered to be a primitive type.
     */

    public final int getPrimitiveType() {
        return Type.FUNCTION;
    }

    /**
     * Produce a representation of this type name for use in error messages.
     *
     * @return a string representation of the type, in notation resembling but not necessarily
     *         identical to XPath syntax
     */

    public String toString() {
        return "function(*)";
    }

    /**
     * Get the item type of the atomic values that will be produced when an item
     * of this type is atomized
     *
     * @return the item type of the atomic values that will be produced when an item
     *         of this type is atomized
     */

    /*@NotNull*/
    public PlainType getAtomizedItemType() {
        return null;
    }

    /**
     * Ask whether values of this type are atomizable
     *
     * @return true unless it is known that these items will be elements with element-only
     *         content, in which case return false
     */

    public boolean isAtomizable() {
        return false;
    }

    /**
     * Determine the relationship of one function item type to another
     *
     * @return for example {@link TypeHierarchy#SUBSUMES}, {@link TypeHierarchy#SAME_TYPE}
     */

    public int relationship(FunctionItemType other, TypeHierarchy th) {
        if (other == this) {
            return TypeHierarchy.SAME_TYPE;
        } else {
            return TypeHierarchy.SUBSUMES;
        }
    }

    /**
     * Create an expression whose effect is to apply function coercion to coerce a function from this type to another type
     *
     * @param exp     the expression that delivers the supplied sequence of function items (the ones in need of coercion)
     * @param role    information for use in diagnostics
     * @return the sequence of coerced functions, each on a function that calls the corresponding original function
     * after checking the parameters
     */

    public Expression makeFunctionSequenceCoercer(Expression exp, RoleDiagnostic role)
            throws XPathException {
        return new ItemChecker(exp, this, role);
    }

    /**
     * Visit all the schema components used in this ItemType definition
     *
     * @param visitor the visitor class to be called when each component is visited
     */

    public void visitNamedSchemaComponents(SchemaComponentVisitor visitor) throws XPathException {
        // no action
    }

    /**
     * Get the result type
     *
     * @return the result type
     */

    public SequenceType getResultType() {
        return SequenceType.ANY_SEQUENCE;
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
        return "return SaxonJS.U.isMap(item) || SaxonJS.U.isArray(item);";
    }

    /**
     * Generate Javascript code to convert a supplied Javascript value to this item type,
     * if conversion is possible, or throw an error otherwise.
     *
     * @param errorCode the error to be thrown if conversion is not possible
     * @return a Javascript instruction or sequence of instructions, which can be used as the body
     * of a Javascript function, and which returns the result of conversion to this type, or throws
     * an error if conversion is not possible. The variable "val" will hold the supplied Javascript
     * value.
     */
    public String generateJavaScriptItemTypeAcceptor(String errorCode) throws XPathException {
        return "if (typeof val == 'object') {return val;} else {throw SaxonJS.XError('Cannot convert supplied JS value to a map or array');}";
    }
}

