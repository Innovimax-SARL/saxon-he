////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AtomicIterator;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.EmptySequence;

import java.util.Collections;
import java.util.Iterator;

/**
 * An implementation of AtomicSequence that contains no items.
 */

public class EmptyAtomicSequence implements AtomicSequence {

    private static EmptyAtomicSequence THE_INSTANCE = new EmptyAtomicSequence();

    public static EmptyAtomicSequence getInstance() {
        return THE_INSTANCE;
    }

    public AtomicValue head() {
        return null;
    }

    public AtomicIterator iterate() {
        return EmptyIterator.OfAtomic.THE_INSTANCE;
    }

    public AtomicValue itemAt(int n) {
        return null;
    }

    public int getLength() {
        return 0;
    }

    /**
     * Get the canonical lexical representation as defined in XML Schema. This is not always the same
     * as the result of casting to a string according to the XPath rules.
     *
     * @return the canonical lexical representation if defined in XML Schema; otherwise, the result
     *         of casting to string according to the XPath 2.0 rules
     */
    public CharSequence getCanonicalLexicalRepresentation() {
        return "";
    }

    /**
     * Get a Comparable value that implements the XML Schema ordering comparison semantics for this value.
     * The default implementation is written to compare sequences of atomic values.
     * This method is overridden for AtomicValue and its subclasses.
     * <p/>
     * <p>In the case of data types that are partially ordered, the returned Comparable extends the standard
     * semantics of the compareTo() method by returning the value {@link net.sf.saxon.om.SequenceTool#INDETERMINATE_ORDERING} when there
     * is no defined order relationship between two given values.</p>
     *
     * @return a Comparable that follows XML Schema comparison rules
     */
    public Comparable getSchemaComparable() {
        return null;
    }

    public CharSequence getStringValueCS() {
        return "";
    }

    public String getStringValue() {
        return "";
    }

    /**
     * Get a subsequence of the value
     *
     * @param start  the index of the first item to be included in the result, counting from zero.
     *               A negative value is taken as zero. If the value is beyond the end of the sequence, an empty
     *               sequence is returned
     * @param length the number of items to be included in the result. Specify Integer.MAX_VALUE to
     *               get the subsequence up to the end of the base sequence. If the value is negative, an empty sequence
     *               is returned. If the value goes off the end of the sequence, the result returns items up to the end
     *               of the sequence
     * @return the required subsequence. If min is
     */
    public GroundedValue subsequence(int start, int length) {
        return this;
    }

    public boolean effectiveBooleanValue() throws XPathException {
        return false;
    }

    /**
     * Reduce the sequence to its simplest form. If the value is an empty sequence, the result will be
     * EmptySequence.getInstance(). If the value is a single atomic value, the result will be an instance
     * of AtomicValue. If the value is a single item of any other kind, the result will be an instance
     * of SingletonItem. Otherwise, the result will typically be unchanged.
     *
     * @return the simplified sequence
     */
    public GroundedValue reduce() {
        return EmptySequence.getInstance();
    }

    /**
     * Return a Java iterator over the atomic sequence.
     * @return an Iterator.
     */

    public Iterator<AtomicValue> iterator() {
        return Collections.<AtomicValue>emptyList().iterator();
    }
}

