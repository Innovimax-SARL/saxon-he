////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2012 Michael Froh.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.trie;

import java.util.Iterator;
import java.util.NoSuchElementException;

public abstract class ImmutableList<T> implements Iterable<T> {
    public abstract T head();

    public abstract ImmutableList<T> tail();

    public abstract boolean isEmpty();

    public final int size() {
        ImmutableList<T> input = this;
        int size = 0;
        while (!input.isEmpty()) {
            size++;
            input = input.tail();
        }
        return size;
    }


    public ImmutableList<T> prepend(T element) {
        return new NonEmptyList<T>(element, this);
    }

    public static <T> ImmutableList<T> nil() {
        return EMPTY_LIST;
    }

    public boolean equals(Object o) {
        if (!(o instanceof ImmutableList)) {
            return false;
        }
        if (o == this) {
            return true;
        }
        Iterator<T> thisIter = this.iterator();
        Iterator<T> otherIter = ((ImmutableList) o).iterator();
        while (thisIter.hasNext() && otherIter.hasNext()) {
            if (!thisIter.next().equals(otherIter.next())) {
                return false;
            }
        }
        return thisIter.hasNext() == otherIter.hasNext();
    }

    public Iterator<T> iterator() {
        return new Iterator<T>() {
            private ImmutableList<T> list = ImmutableList.this;

            public boolean hasNext() {
                return !list.isEmpty();
            }

            public T next() {
                T element = list.head();
                list = list.tail();
                return element;
            }

            public void remove() {
                throw new RuntimeException("Cannot remove from immutable list");
            }
        };
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (T elem : this) {
            if (!first) {
                builder.append(", ");
            }
            builder.append(elem);
            first = false;
        }
        builder.append(']');
        return builder.toString();
    }

    private static final EmptyList EMPTY_LIST = new EmptyList();

    private static class EmptyList extends ImmutableList {
        @Override
        public Object head() {
            throw new NoSuchElementException("head() called on empty list");
        }

        @Override
        public ImmutableList tail() {
            throw new NoSuchElementException("head() called on empty list");
        }

        @Override
        public boolean isEmpty() {
            return true;
        }
    }

    private static class NonEmptyList<T> extends ImmutableList<T> {
        private final T element;
        private final ImmutableList<T> tail;

        private NonEmptyList(final T element, final ImmutableList<T> tail) {
            this.element = element;
            this.tail = tail;
        }

        @Override
        public T head() {
            return element;
        }

        @Override
        public ImmutableList<T> tail() {
            return tail;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }
    }
}
