////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;


import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.SequenceType;

/**
 * Interface representing a union type. This may be either a built-in union type (of which there is only one,
 * namely ErrorType), or a user-defined union type.
 */

public interface UnionType extends SimpleType, ItemType {

    /**
     * Ask whether the union contains a list type among its member types
     *
     * @return true of one of the member types is a list type
     */

    boolean containsListType() throws MissingComponentException;

    /**
     * Get the "plain" types in the transitive membership. Plain types are atomic types and union types that
     * are defined directly in terms of other plain types, without adding any restriction facets.
     *
     * @return the atomic types and plain union types in the transitive membership of the union type.
     */

    Iterable<PlainType> getPlainMemberTypes() throws MissingComponentException;

    /**
     * Get the result type of a cast operation to this union type, as a sequence type.
     *
     * @return the result type of casting, as precisely as possible. For example, if all the member types of
     *         the union are derived from the same primitive type, this will return that primitive type.
     */

    SequenceType getResultTypeOfCast();

    /**
     * Validate an atomic value, which is known to be an instance of one of the member types of the
     * union, against any facets (pattern facets or enumeration facets) defined at the level of the
     * union itself.
     * @param value the Atomic Value to be checked. This must be an instance of a member type of the
     *              union
     * @param rules the ConversionRules for the Configuration
     * @return a ValidationFailure if the value is not valid; null if it is valid.
     */

    ValidationFailure checkAgainstFacets(AtomicValue value, ConversionRules rules);

}

