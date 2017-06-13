////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.query;


import net.sf.saxon.Configuration;
import net.sf.saxon.lib.FunctionAnnotationHandler;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;

import java.util.*;

/**
 * A list of function or variable annotations, or of annotation assertions
 */

public class AnnotationList implements Iterable<Annotation> {


    private List<Annotation> list;

    private static List<Annotation> EMPTY_BASE_LIST = Collections.emptyList();
    public static AnnotationList EMPTY = new AnnotationList(EMPTY_BASE_LIST);

    public AnnotationList(List<Annotation> list) {
        this.list = list;
    }

    public static AnnotationList singleton(Annotation ann) {
        return new AnnotationList(Collections.singletonList(ann));
    }


    /**
     * Check an annotation list for internal consistency (e.g. rules that %public and %private cannot coexist)
     * @param where the context where the list appears: one of "DF" (declare function), "DV" (declare variable),
     *              "IF" (inline function declaration), "FT" (function test)
     * @throws XPathException if the annotation list is not internally consistent
     */

    public void check(Configuration config, String where) throws XPathException {
        Map<String, List<Annotation>> map = groupByNamespace();
        for (Map.Entry<String, List<Annotation>> entry : map.entrySet()) {
            FunctionAnnotationHandler handler = config.getFunctionAnnotationHandler(entry.getKey());
            if (handler != null) {
                handler.check(new AnnotationList(entry.getValue()), where);
            }
        }
    }

    private Map<String, List<Annotation>> groupByNamespace() {
        Map<String, List<Annotation>> result = new HashMap<String, List<Annotation>>();
        for (Annotation ann : list) {
            String ns = ann.getAnnotationQName().getURI();
            if (result.containsKey(ns)) {
                result.get(ns).add(ann);
            } else {
                List<Annotation> list = new ArrayList<Annotation>();
                list.add(ann);
                result.put(ns, list);
            }
        }
        return result;
    }

    public AnnotationList filterByNamespace(String ns) {
        List<Annotation> out = new ArrayList<Annotation>();
        for (Annotation ann : list) {
            if (ann.getAnnotationQName().hasURI(ns)) {
                out.add(ann);
            }
        }
        return new AnnotationList(out);
    }


    /**
     * Returns an iterator over a set of elements of type T.
     *
     * @return an Iterator.
     */
    @Override
    public Iterator<Annotation> iterator() {
        return list.iterator();
    }

    public boolean isEmpty() {
        return list.isEmpty();
    }

    public int size() {
        return list.size();
    }

    public Annotation get(int i) {
        return list.get(i);
    }

    /**
     * Ask whether a list of annotations contains an annotation with a given name
     *
     * @param name           the given name
     * @return true if one or more annotations with the given name are present in the list
     */

    public boolean includes(StructuredQName name) {
        for (Annotation a : list) {
            if (a.getAnnotationQName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Ask whether a list of annotations contains an annotation with a given local name
     *
     * @param localName the given local name
     * @return true if one or more annotations with the given name are present in the list
     */

    public boolean includes(String localName) {
        for (Annotation a : list) {
            if (a.getAnnotationQName().getLocalPart().equals(localName)) {
                return true;
            }
        }
        return false;
    }

    public boolean equals(Object other) {
        // treat the annotation list as ordered
        return other instanceof AnnotationList && list.equals(((AnnotationList)other).list);
    }

    public int hashCode() {
        return list.hashCode();
    }


}
