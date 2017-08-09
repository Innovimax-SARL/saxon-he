////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Singleton class representing the class xs:numeric as defined in XPath 3.1: a union type
 * whose members are xs:double, xs:decimal, and xs:float
 */
public class NumericType implements UnionType, PlainType {

    private final static NumericType THE_INSTANCE = new NumericType();

    public static NumericType getInstance() {
        return THE_INSTANCE;
    }

    private static List<PlainType> members;

    private NumericType() {}

    public StructuredQName getTypeName() {
        return new StructuredQName("xs", NamespaceConstant.SCHEMA, "numeric");
    }

    /**
     * Ask whether the union contains a list type among its member types
     *
     * @return true of one of the member types is a list type
     */
    public boolean containsListType() {
        return false;
    }

    /**
     * Get the "plain" types in the transitive membership. Plain types are atomic types and union types that
     * are defined directly in terms of other plain types, without adding any restriction facets.
     *
     * @return the atomic types and plain union types in the transitive membership of the union type.
     */
    public Iterable<PlainType> getPlainMemberTypes() {
        if (members == null) {
            synchronized(NumericType.class) {
                members = new ArrayList<PlainType>();
                members.add(BuiltInAtomicType.DOUBLE);
                members.add(BuiltInAtomicType.FLOAT);
                members.add(BuiltInAtomicType.DECIMAL);
            }
        }
        return members;
    }

    /**
     * Get the result type of a cast operation to this union type, as a sequence type.
     *
     * @return the result type of casting, as precisely as possible. For example, if all the member types of
     *         the union are derived from the same primitive type, this will return that primitive type.
     */
    public SequenceType getResultTypeOfCast() {
        return SequenceType.ATOMIC_SEQUENCE;
    }

    /**
     * Determine whether this item type is a plain type (that is, whether it can ONLY match
     * atomic values)
     *
     * @return true if this is ANY_ATOMIC_TYPE or a subtype thereof, or a
     *         "plain" union type (that is, unions of atomic types that impose no further restrictions)
     */
    public boolean isPlainType() {
        return true;
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
    public boolean matches(Item item, TypeHierarchy th) {
        return item instanceof NumericValue;
    }

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
    public AtomicType getPrimitiveItemType() {
        return BuiltInAtomicType.ANY_ATOMIC;
    }

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
    public int getPrimitiveType() {
        return BuiltInAtomicType.ANY_ATOMIC.getFingerprint();
    }

    /**
     * Get the corresponding {@link net.sf.saxon.type.UType}. A UType is a union of primitive item
     * types.
     *
     * @return the smallest UType that subsumes this item type
     */
    public UType getUType() {
        return UType.NUMERIC;
    }

    /**
     * Get the item type of the atomic values that will be produced when an item
     * of this type is atomized
     *
     * @return the best available item type of the atomic values that will be produced when an item
     *         of this type is atomized, or null if it is known that atomization will throw an error.
     */
    public PlainType getAtomizedItemType() {
        return this;
    }

    /**
     * Ask whether values of this type are atomizable
     *
     * @return true unless it is known that these items will be elements with element-only
     *         content, or function items, in which case return false
     */
    public boolean isAtomizable() {
        return true;
    }

    /**
     * Visit all the schema components used in this ItemType definition
     *
     * @param visitor the visitor class to be called when each component is visited
     * @throws net.sf.saxon.trans.XPathException
     *          if an error occurs
     */
    public void visitNamedSchemaComponents(SchemaComponentVisitor visitor) throws XPathException {
        // no action
    }

    /**
     * Test whether this Simple Type is an atomic type
     *
     * @return true if this is an atomic type
     */
    public boolean isAtomicType() {
        return false;
    }

    /**
     * Test whether this Simple Type is a list type
     *
     * @return true if this is a list type
     */
    public boolean isListType() {
        return false;
    }

    /**
     * Test whether this Simple Type is a union type
     *
     * @return true if this is a union type
     */
    public boolean isUnionType() {
        return true;
    }

    /**
     * Return true if this is an external object type, that is, a Saxon-defined type for external
     * Java or .NET objects
     *
     * @return true if this is an external type
     */
    public boolean isExternalType() {
        return false;
    }

    /**
     * Determine whether this is a built-in type or a user-defined type
     *
     * @return true if this is a built-in type
     */
    public boolean isBuiltInType() {
        return true;
    }

    /**
     * Get the built-in type from which this type is derived by restriction
     *
     * @return the built-in type from which this type is derived by restriction. This will not necessarily
     *         be a primitive type.
     */
    public SchemaType getBuiltInBaseType() {
        return AnySimpleType.getInstance();
    }

    /**
     * Get the typed value corresponding to a given string value, assuming it is
     * valid against this type
     *
     * @param value    the string value
     * @param resolver a namespace resolver used to resolve any namespace prefixes appearing
     *                 in the content of values. Can supply null, in which case any namespace-sensitive content
     *                 will be rejected.
     * @param rules    the conversion rules from the configuration
     * @return an iterator over the atomic sequence comprising the typed value. The objects
     *         returned by this SequenceIterator will all be of type {@link net.sf.saxon.value.AtomicValue},
     *         The next() method on the iterator throws no checked exceptions, although it is not actually
     *         declared as an UnfailingIterator.
     * @throws net.sf.saxon.type.ValidationException
     *          if the supplied value is not in the lexical space of the data type
     */
    public AtomicSequence getTypedValue(CharSequence value, NamespaceResolver resolver, ConversionRules rules) throws ValidationException {
        try {
            double d = StringToDouble.getInstance().stringToNumber(value);
            return new DoubleValue(d);
        } catch (NumberFormatException e) {
            throw new ValidationFailure(e.getMessage()).makeException();
        }
    }

    /**
     * Check whether a given input string is valid according to this SimpleType
     *
     * @param value      the input string to be checked
     * @param nsResolver a namespace resolver used to resolve namespace prefixes if the type
     *                   is namespace sensitive. The value supplied may be null; in this case any namespace-sensitive
     *                   content will throw an UnsupportedOperationException.
     * @param rules      the conversion rules from the configuration
     * @return null if validation succeeds; or return a ValidationFailure describing the validation failure
     *         if validation fails. Note that the exception is returned rather than being thrown.
     * @throws UnsupportedOperationException if the type is namespace-sensitive and no namespace
     *                                       resolver is supplied
     */
    public ValidationFailure validateContent(CharSequence value, NamespaceResolver nsResolver, ConversionRules rules) {
        try {
            StringToDouble.getInstance().stringToNumber(value);
            return null;
        } catch (NumberFormatException e) {
            return new ValidationFailure(e.getMessage());
        }
    }

    /**
     * Validate an atomic value, which is known to be an instance of one of the member types of the
     * union, against any facets (pattern facets or enumeration facets) defined at the level of the
     * union itself.
     *
     * @param value the Atomic Value to be checked. This must be an instance of a member type of the
     *              union
     * @param rules the ConversionRules for the Configuration
     * @return a ValidationFailure if the value is not valid; null if it is valid.
     */
    @Override
    public ValidationFailure checkAgainstFacets(AtomicValue value, ConversionRules rules) {
        return null;
    }

    /**
     * Test whether this type is namespace sensitive, that is, if a namespace context is needed
     * to translate between the lexical space and the value space. This is true for types derived
     * from, or containing, QNames and NOTATIONs
     *
     * @return true if the type is namespace-sensitive
     */
    public boolean isNamespaceSensitive() {
        return false;
    }

    /**
     * Determine how values of this simple type are whitespace-normalized.
     *
     * @return one of {@link net.sf.saxon.value.Whitespace#PRESERVE}, {@link net.sf.saxon.value.Whitespace#COLLAPSE},
     *         {@link net.sf.saxon.value.Whitespace#REPLACE}.
     */
    public int getWhitespaceAction() {
        return Whitespace.COLLAPSE;
    }

    /**
     * Apply any pre-lexical facets, other than whitespace. At the moment the only such
     * facet is saxon:preprocess
     *
     * @param input the value to be preprocessed
     * @return the value after preprocessing
     * @throws net.sf.saxon.type.ValidationException
     *          if preprocessing detects that the value is invalid
     */
    public CharSequence preprocess(CharSequence input) throws ValidationException {
        return input;
    }

    /**
     * Reverse any pre-lexical facets, other than whitespace. At the moment the only such
     * facet is saxon:preprocess. This is called when converting a value of this type to
     * a string
     *
     * @param input the value to be postprocessed: this is the "ordinary" result of converting
     *              the value to a string
     * @return the value after postprocessing
     * @throws net.sf.saxon.type.ValidationException
     *          if postprocessing detects that the value is invalid
     */
    public CharSequence postprocess(CharSequence input) throws ValidationException {
        return input;
    }

    /**
     * Get the local name of this type
     *
     * @return the local name of this type definition, if it has one. Return null in the case of an
     *         anonymous type.
     */
    public String getName() {
        return "numeric";
    }

    /**
     * Get the target namespace of this type
     *
     * @return the target namespace of this type definition, if it has one. Return null in the case
     *         of an anonymous type, and in the case of a global type defined in a no-namespace schema.
     */
    public String getTargetNamespace() {
        return NamespaceConstant.SCHEMA;
    }

    /**
     * Get the fingerprint of the name of this type
     *
     * @return the fingerprint. Returns an invented fingerprint for an anonymous type.
     */
    public int getFingerprint() {
        return StandardNames.XS_NUMERIC;
    }

    /**
     * Get the display name of the type: that is, a lexical QName with an arbitrary prefix
     *
     * @return a lexical QName identifying the type. In the case of an anonymous type, an internally-generated
     *         name is returned
     */
    public String getDisplayName() {
        return "xs:numeric";
    }

    /**
     * Get the name of this type as an EQName, that is, a string in the format Q{uri}local.
     *
     * @return an EQName identifying the type. In the case of an anonymous type, an internally-generated
     *         name is returned
     */
    public String getEQName() {
        return "Q(" + NamespaceConstant.SCHEMA + "}numeric";
    }

    /**
     * Get the name of the type as a StructuredQName
     *
     * @return a StructuredQName identifying the type.  In the case of an anonymous type, an internally-generated
     * name is returned
     */
    public StructuredQName getStructuredQName() {
        return new StructuredQName("xs", NamespaceConstant.SCHEMA, "numeric");
    }

    /**
     * Test whether this SchemaType is a complex type
     *
     * @return true if this SchemaType is a complex type
     */
    public boolean isComplexType() {
        return false;
    }

    /**
     * Test whether this SchemaType is a simple type
     *
     * @return true if this SchemaType is a simple type
     */
    public boolean isSimpleType() {
        return true;
    }

    /**
     * Test whether this is an anonymous type
     *
     * @return true if this SchemaType is an anonymous type
     */
    public boolean isAnonymousType() {
        return false;
    }

    /**
     * Returns the value of the 'block' attribute for this type, as a bit-significant
     * integer with fields such as {@link net.sf.saxon.type.SchemaType#DERIVATION_LIST} and {@link net.sf.saxon.type.SchemaType#DERIVATION_EXTENSION}.
     * This corresponds to the property "prohibited substitutions" in the schema component model.
     *
     * @return the value of the 'block' attribute for this type
     */
    public int getBlock() {
        return 0;
    }

    /**
     * Returns the base type that this type inherits from. This method can be used to get the
     * base type of a type that is known to be valid.
     * If this type is a Simpletype that is a built in primitive type then null is returned.
     *
     * @return the base type, or null if this is xs:anyType (the root of the type hierarchy)
     */
    public SchemaType getBaseType() {
        return AnySimpleType.getInstance();
    }

    /**
     * Gets the integer code of the derivation method used to derive this type from its
     * parent. Returns zero for primitive types.
     *
     * @return a numeric code representing the derivation method, for example {@link net.sf.saxon.type.SchemaType#DERIVATION_RESTRICTION}
     */
    public int getDerivationMethod() {
        return SchemaType.DERIVATION_RESTRICTION;
    }

    /**
     * Get the types of derivation that are not permitted, by virtue of the "final" property.
     *
     * @return the types of derivation that are not permitted, as a bit-significant integer
     *         containing bits such as {@link net.sf.saxon.type.SchemaType#DERIVATION_EXTENSION}
     */
    public int getFinalProhibitions() {
        return 0;
    }

    /**
     * Determines whether derivation (of a particular kind)
     * from this type is allowed, based on the "final" property
     *
     * @param derivation the kind of derivation, for example {@link net.sf.saxon.type.SchemaType#DERIVATION_LIST}
     * @return true if this kind of derivation is allowed
     */
    public boolean allowsDerivation(int derivation) {
        return true;
    }

    /**
     * Analyze an XPath expression to see whether the expression is capable of delivering a value of this
     * type. This method is called during static analysis of a query or stylesheet to give compile-time
     * warnings when "impossible" paths are used.
     *
     * @param expression the expression that delivers the content
     * @param kind       the node kind whose content is being delivered: {@link Type#ELEMENT},
     *                   {@link Type#ATTRIBUTE}, or {@link Type#DOCUMENT}
     * @throws net.sf.saxon.trans.XPathException
     *          if the expression will never deliver a value of the correct type
     */
    public void analyzeContentExpression(Expression expression, int kind) throws XPathException {
        // TODO
    }

    /**
     * Get the typed value of a node that is annotated with this schema type.
     *
     * @param node the node whose typed value is required
     * @return the typed value.
     * @throws net.sf.saxon.trans.XPathException
     *          if the node cannot be atomized, for example if this is a complex type
     *          with element-only content
     * @since 8.5. Changed in 9.5 to return the new type AtomicSequence
     */
    public AtomicSequence atomize(NodeInfo node) throws XPathException {
        throw new UnsupportedOperationException(); // nodes are never annotated with a union type
    }

    /**
     * Test whether this is the same type as another type. They are considered to be the same type
     * if they are derived from the same type definition in the original XML representation (which
     * can happen when there are multiple includes of the same file)
     *
     * @param other the other type
     * @return true if this is the same type as other
     */
    public boolean isSameType(SchemaType other) {
        return other instanceof NumericType;
    }

    /**
     * Get a description of this type for use in error messages. This is the same as the display name
     * in the case of named types; for anonymous types it identifies the type by its position in a source
     * schema document.
     *
     * @return text identifing the type, for use in a phrase such as "the type XXXX".
     */
    public String getDescription() {
        return "xs:numeric";
    }

    /**
     * Check that this type is validly derived from a given type, following the rules for the Schema Component
     * Constraint "Is Type Derivation OK (Simple)" (3.14.6) or "Is Type Derivation OK (Complex)" (3.4.6) as
     * appropriate.
     *
     * @param base  the base type; the algorithm tests whether derivation from this type is permitted
     * @param block the derivations that are blocked by the relevant element declaration
     * @throws net.sf.saxon.type.SchemaException
     *          if the derivation is not allowed
     */
    public void checkTypeDerivationIsOK(SchemaType base, int block) throws SchemaException {
        // TODO
    }

    /**
     * Get the URI of the schema document where the type was originally defined.
     *
     * @return the URI of the schema document. Returns null if the information is unknown or if this
     *         is a built-in type
     */
    public String getSystemId() {
        return null;
    }

    /**
     * Ask whether this type is an ID type. This is defined to be any simple type
     * who typed value may contain atomic values of type xs:ID: that is, it includes types derived
     * from ID by restriction, list, or union. Note that for a node to be treated
     * as an ID in XSD 1.0, its typed value must be a *single* atomic value of type ID; the type of the
     * node, however, can still allow a list. But in XSD 1.1, a list of IDs is permitted
     *
     * @return true if this type is an ID type
     */
    public boolean isIdType() {
        return false;
    }

    /**
     * Ask whether this type is an IDREF or IDREFS type. This is defined to be any simple type
     * who typed value may contain atomic values of type xs:IDREF: that is, it includes types derived
     * from IDREF or IDREFS by restriction, list, or union
     *
     * @return true if this type is an IDREF type
     */
    public boolean isIdRefType() {
        return false;
    }

    /**
     * Get the validation status of this component.
     *
     * @return one of the values {@link #UNVALIDATED}, {@link #VALIDATING},
     *         {@link #VALIDATED}, {@link #INVALID}, {@link #INCOMPLETE}
     */
    public int getValidationStatus() {
        return VALIDATED;
    }

    /**
     * Get the redefinition level. This is zero for a component that has not been redefined;
     * for a redefinition of a level-0 component, it is 1; for a redefinition of a level-N
     * component, it is N+1. This concept is used to support the notion of "pervasive" redefinition:
     * if a component is redefined at several levels, the top level wins, but it is an error to have
     * two versions of the component at the same redefinition level.
     *
     * @return the redefinition level
     */
    public int getRedefinitionLevel() {
        return 0;
    }

    /**
     * Get the schema component in the form of a function item. This allows schema information
     * to be made visible to XSLT or XQuery code. The function makes available the contents of the
     * schema component as defined in the XSD specification. The function takes a string as argument
     * representing a property name, and returns the corresponding property of the schema component.
     * There is also a property "class" which returns the kind of schema component, for example
     * "Attribute Declaration".
     *
     * @return the schema component represented as a function from property names to property values.
     */
    public Function getComponentAsFunction() {
        return null;  // TODO
    }

    @Override
    public String toString() {
        return "xs:numeric";
    }

    /**
     * Generate Javascript code to test whether an item conforms to this item type
     *
     * @return a Javascript instruction or sequence of instructions, which can be used as the body
     * of a Javascript function, and which returns a boolean indication whether the value of the
     * variable "item" is an instance of this item type.
     * @param knownToBe
     * @param targetVersion
     */
    @Override
    public String generateJavaScriptItemTypeTest(ItemType knownToBe, int targetVersion) {
        if (targetVersion == 1) {
            return "return SaxonJS.U.Atomic.numeric.matches(item);";
        } else {
            return "return SaxonJS.U.isNumeric(item);";
        }
    }

    /**
     * Generate Javascript code to convert a supplied Javascript value to this item type,
     * if conversion is possible, or throw an error otherwise.
     *
     * @param errorCode the error to be thrown if conversion is not possible
     * @param targetVersion
     * @return a Javascript instruction or sequence of instructions, which can be used as the body
     * of a Javascript function, and which returns the result of conversion to this type, or throws
     * an error if conversion is not possible. The variable "val" will hold the supplied Javascript
     * value.
     */
    public String generateJavaScriptItemTypeAcceptor(String errorCode, int targetVersion) throws XPathException {
        return BuiltInAtomicType.DOUBLE.generateJavaScriptItemTypeAcceptor(errorCode, targetVersion);
    }
}

