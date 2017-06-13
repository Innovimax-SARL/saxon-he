////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.OperandRole;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.sort.AtomicComparer;
import net.sf.saxon.query.AnnotationList;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.UnfailingIterator;
import net.sf.saxon.type.FunctionItemType;

/**
 * XDM 3.0 introduces a third kind of item, beyond nodes and atomic values: the function. Functions
 * implement this interface.
 */

public interface Function extends Item, Callable, GroundedValue {

    /**
     * Get an iterator over all the items in the sequence
     *
     * @return an iterator over all the items
     */

    UnfailingIterator iterate();

    /**
     * Ask whether this function item is a map
     * @return true if this function item is a map, otherwise false
     */

    boolean isMap();

    /**
     * Ask whether this function item is an array
     * @return true if this function item is an array, otherwise false
     */

    boolean isArray();

    /**
     * Get the item type of the function item
     *
     * @return the function item's type
     */

    FunctionItemType getFunctionItemType();

    /**
     * Get the name of the function, or null if it is anonymous
     *
     * @return the function name, or null for an anonymous inline function
     */

    StructuredQName getFunctionName();

    /**
     * Get the arity of the function
     *
     * @return the number of arguments in the function signature
     */

    int getArity();

    /**
     * Get the roles of the arguments, for the purposes of streaming
     * @return an array of OperandRole objects, one for each argument
     */

    OperandRole[] getOperandRoles();

    /**
     * Get the function annotations (as defined in XQuery). Returns an empty
     * list if there are no function annotations.
     * @return the function annotations
     */

    AnnotationList getAnnotations();

    /**
     * Invoke the function
     *
     * @param context the XPath dynamic evaluation context
     * @param args    the actual arguments to be supplied
     * @return the result of invoking the function
     * @throws XPathException if a dynamic error occurs within the function
     */

    Sequence call(XPathContext context, Sequence[] args) throws XPathException;

    /**
     * Test whether this FunctionItem is deep-equal to another function item,
     * under the rules of the deep-equal function
     *
     * @param other    the other function item
     * @param context  the dynamic evaluation context
     * @param comparer the object to perform the comparison
     * @param flags    options for how the comparison is performed
     * @return true if the two function items are deep-equal
     * @throws XPathException if the comparison cannot be performed
     */

    boolean deepEquals(Function other, XPathContext context, AtomicComparer comparer, int flags) throws XPathException;

    /**
     * Get a description of this function for use in error messages. For named functions, the description
     * is the function name (as a lexical QName). For others, it might be, for example, "inline function",
     * or "partially-applied ends-with function".
     * @return a description of the function for use in error messages
     */

    String getDescription();

    /**
     * Output information about this function item to the diagnostic explain() output
     */

    void export(ExpressionPresenter out) throws XPathException;


    /**
     * Check that result type is of the trusted system functions to return a result of the correct type
     */

    boolean isTrustedResultType();
}

