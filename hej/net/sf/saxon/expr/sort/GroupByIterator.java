////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import com.saxonica.ee.stream.ManualGroupIterator;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.LastPositionFinder;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.ListIterator;
import net.sf.saxon.tree.iter.LookaheadIterator;
import net.sf.saxon.value.AtomicValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * A GroupByIterator iterates over a sequence of groups defined by
 * xsl:for-each-group group-by="x". The groups are returned in
 * order of first appearance. Note that an item can appear in several groups;
 * indeed, an item may be the leading item of more than one group, which means
 * that knowing the leading item is not enough to know the current group.
 * <p/>
 * <p>The GroupByIterator acts as a SequenceIterator, where successive calls of
 * next() return the leading item of each group in turn. The current item of
 * the iterator is therefore the leading item of the current group. To get access
 * to all the members of the current group, the method iterateCurrentGroup() is used;
 * this underpins the current-group() function in XSLT. The grouping key for the
 * current group is available via the getCurrentGroupingKey() method.</p>
 */

public class GroupByIterator implements GroupIterator, LastPositionFinder, LookaheadIterator {

    // The implementation of group-by is not pipelined. All the items in the population
    // are read at the start, their grouping keys are calculated, and the groups are formed
    // in memory as a hash table indexed by the grouping key. This hash table is then
    // flattened into three parallel lists: a list of groups (each group being represented
    // as a list of items in population order), a list of grouping keys, and a list of
    // the initial items of the groups.

    private SequenceIterator population;
    protected Expression keyExpression;
    private StringCollator collator;
    private XPathContext keyContext;
    private int position = 0;

    // Main data structure holds one entry for each group. The entry is also an ArrayList,
    // which contains the Items that are members of the group, in population order.
    // The groups are arranged in order of first appearance within the population.
    protected List<List<Item>> groups = new ArrayList<List<Item>>(40);

    // This parallel structure identifies the grouping key for each group. The list
    // corresponds one-to-one with the list of groups.
    protected List<AtomicSequence> groupKeys = new ArrayList<AtomicSequence>(40);

    // An AtomicComparer is used to do the comparisons of individual atomic values
    //protected AtomicComparer comparer;

    protected boolean composite;

    /**
     * Create a GroupByIterator
     *
     * @param population    iterator over the population to be grouped
     * @param keyExpression the expression used to calculate the grouping key
     * @param keyContext    dynamic context for calculating the grouping key
     * @param collator      Collation to be used for comparing grouping keys
     * @param composite     true if grouping keys are to be treated as composite keys
     * @throws XPathException if an error occurs
     */

    public GroupByIterator(SequenceIterator population, Expression keyExpression,
                           XPathContext keyContext, StringCollator collator, boolean composite)
            throws XPathException {
        this.population = population;
        this.keyExpression = keyExpression;
        this.keyContext = keyContext;
        this.collator = collator;
        this.composite = composite;
        if (composite) {
            buildIndexedGroupsComposite();
        } else {
            buildIndexedGroups();
        }
    }

    public GroupByIterator() {
    }

    /**
     * Build the grouping table forming groups of items with equal keys.
     * This form of grouping allows a member of the population to be present in zero
     * or more groups, one for each value of the grouping key.
     *
     * @throws XPathException if any error occurs
     */

    private void buildIndexedGroups() throws XPathException {
        HashMap<AtomicMatchKey, List<Item>> index = new HashMap<AtomicMatchKey, List<Item>>(40);
        XPathContext c2 = keyContext.newMinorContext();
        FocusIterator focus = new FocusTrackingIterator(population);
        c2.setCurrentIterator(focus);
        int implicitTimezone = c2.getImplicitTimezone();
        Item item;
        while ((item = focus.next()) != null) {
            SequenceIterator keys = keyExpression.iterate(c2);
            boolean firstKey = true;
            while (true) {
                AtomicValue key = (AtomicValue) keys.next();
                if (key == null) {
                    break;
                }
                AtomicMatchKey comparisonKey;
                if (key.isNaN()) {
                    comparisonKey = AtomicValue.NaN_MATCH_KEY;
                } else {
                    comparisonKey = key.getXPathComparable(false, collator, implicitTimezone);
                }
                List<Item> g = index.get(comparisonKey);
                if (g == null) {
                    List<Item> newGroup = new ArrayList<Item>(20);
                    newGroup.add(item);
                    groups.add(newGroup);
                    groupKeys.add(key);
                    index.put(comparisonKey, newGroup);
                } else {
                    if (firstKey) {
                        g.add(item);
                    } else {
                        // if this is not the first key value for this item, we
                        // check whether the item is already in this group before
                        // adding it again. If it is in this group, then we know
                        // it will be at the end.
                        if (g.get(g.size() - 1) != item) {
                            g.add(item);
                        }
                    }
                }
                firstKey = false;
            }

        }
    }

    /**
     * Build the grouping table forming groups of items with equal keys.
     * This form of grouping allows the grouping key to be a sequence of atomic values.
     *
     * @throws XPathException if any error occurs
     */

    private void buildIndexedGroupsComposite() throws XPathException {
        HashMap<List<AtomicMatchKey>, List<Item>> index = new HashMap<List<AtomicMatchKey>, List<Item>>(40);
        XPathContext c2 = keyContext.newMinorContext();
        FocusIterator focus = new FocusTrackingIterator(population);
        c2.setCurrentIterator(focus);
        int implicitTimezone = c2.getImplicitTimezone();
        Item item;
        while ((item = focus.next()) != null) {
            SequenceIterator keys = keyExpression.iterate(c2);
            List<AtomicMatchKey> ckList = new ArrayList<AtomicMatchKey>();
            List<AtomicValue> compositeKey = new ArrayList<AtomicValue>();
            while (true) {
                AtomicValue key = (AtomicValue) keys.next();
                if (key == null) {
                    break;
                }
                compositeKey.add(key);
                AtomicMatchKey comparisonKey;
                if (key.isNaN()) {
                    comparisonKey = AtomicValue.NaN_MATCH_KEY;
                } else {
                    comparisonKey = key.getXPathComparable(false, collator, implicitTimezone);
                }
                ckList.add(comparisonKey);
            }

            List<Item> g = index.get(ckList);
            if (g == null) {
                List<Item> newGroup = new ArrayList<Item>(20);
                newGroup.add(item);
                groups.add(newGroup);
                groupKeys.add(new AtomicArray(compositeKey));
                index.put(ckList, newGroup);
            } else {
                g.add(item);
            }
        }
    }


    /**
     * Get the value of the grouping key for the current group
     *
     * @return the grouping key
     */

    public synchronized AtomicSequence getCurrentGroupingKey() {
        AtomicSequence val = groupKeys.get(position - 1);
        if (val == null) {
            return EmptyAtomicSequence.getInstance();
        } else {
            return val;
        }
    }

    /**
     * Get an iterator over the items in the current group
     *
     * @return the iterator
     */

    public SequenceIterator iterateCurrentGroup() {
        return new ListIterator(groups.get(position - 1));
    }

    /**
     * Get the contents of the current group as a java List
     *
     * @return the contents of the current group
     */

    public List getCurrentGroup() {
        return groups.get(position - 1);
    }

    public boolean hasNext() {
        return position < groups.size();
    }

    /*@Nullable*/
    public Item next() throws XPathException {
        if (position >= 0 && position < groups.size()) {
            position++;
            return current();
        } else {
            position = -1;
            return null;
        }
    }

    private Item current() {
        if (position < 1) {
            return null;
        }
        // return the initial item of the current group
        return groups.get(position - 1).get(0);
    }

    public void close() {
    }

    /**
     * Get properties of this iterator, as a bit-significant integer.
     *
     * @return the properties of this iterator. This will be some combination of
     *         properties such as {@link #GROUNDED}, {@link #LAST_POSITION_FINDER},
     *         and {@link #LOOKAHEAD}. It is always
     *         acceptable to return the value zero, indicating that there are no known special properties.
     *         It is acceptable for the properties of the iterator to change depending on its state.
     */

    public int getProperties() {
        return LAST_POSITION_FINDER | LOOKAHEAD;
    }

    /**
     * Get the last position (that is, the number of groups)
     */

    public int getLength() throws XPathException {
        return groups.size();
    }

//#if EE==true

    public ManualGroupIterator getSnapShot(XPathContext context) throws XPathException {
        return new ManualGroupByIterator();
    }

    public class ManualGroupByIterator extends ManualGroupIterator {

        List<Item> currentGroup = groups.get(position - 1);
        AtomicSequence currentGroupingKey = groupKeys.get(position - 1);

        public ManualGroupByIterator() {
            super(groups.get(position - 1).get(0), position);
            setLastPositionFinder(new LastPositionFinder() {
                public int getLength() throws XPathException {
                    return groups.size();
                }
            });
        }

        public SequenceIterator iterateCurrentGroup() throws XPathException {
            return new ListIterator(currentGroup);
        }

        public AtomicSequence getCurrentGroupingKey() {
            return currentGroupingKey;
        }


        @Override
        public int getLength() throws XPathException {
            return groups.size();
        }


    }
//#endif


}

