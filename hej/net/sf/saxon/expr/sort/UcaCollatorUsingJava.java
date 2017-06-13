////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.lib.SubstringMatcher;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.StringConverter;
import net.sf.saxon.type.ValidationFailure;
import net.sf.saxon.value.AnyURIValue;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.CollationElementIterator;
import java.text.CollationKey;
import java.text.Collator;
import java.text.RuleBasedCollator;
import java.util.*;


/**
 * This class implements (an approximation to) the UCA Collation Algorithm
 * relying solely on the built-in Java support (that is, without using the
 * ICU library). This provides a fallback implementation for Saxon-HE, and
 * it is used only when the collation URI does not include the option
 * fallback=no.
 */
public class UcaCollatorUsingJava implements SubstringMatcher {

    private String uri;
    private RuleBasedCollator uca;
    private Strength strengthLevel;


    private static String keywords[] = {"fallback", "lang", "version", "strength",
            "alternate", "backwards", "normalization", "maxVariable",
            "caseLevel", "caseFirst", "numeric", "reorder"}; //, "hiraganaQuaternary"
    private static Set<String> keys = new HashSet<String>(Arrays.asList(keywords));


    public UcaCollatorUsingJava(String uri) throws XPathException {
        this.uri = uri;
        uca = (RuleBasedCollator) RuleBasedCollator.getInstance();
        setProps(parseProps(uri));
    }

    public RuleBasedCollator getRuleBasedCollator() {
        return uca;
    }

    /**
     * Return the locales supported in this version of ICU
     * Note that with Java 6 this method has been known to throw an array index exception from RuleBasedCollator,
     * With Java 7 this performs correctly and getAvailableLocales() does not appear to be
     * invoked from any of the UCA or numbering support.
     *
     * @return The language tags and display names separated by semicolon
     */
//    public static String[] getLocales() {
//        ArrayList<String> locales = new ArrayList<String>();
//        for (Locale l : RuleBasedCollator.getAvailableLocales()) {
//            locales.add(ICULibrary.getLanguageTag(l) + ";" + l.getDisplayName());
//        }
//        return locales.toArray(new String[locales.size()]);
//    }

    private void error(String field, String allowed) throws XPathException {
        error("value of " + field + " must be " + allowed);
    }

    private void error(String field, String allowed, String requested) throws XPathException {
        error("value of " + field + " must be " + allowed + ", requested was:" + requested);
    }

    private void error(String message) throws XPathException {
        throw new XPathException("Error in UCA Collation URI " + uri + ": " + message, "FOCH0002");
    }

    public int compare(String source, String target) {
//        System.err.println("UCA.compare(" + source + "," + target + ")" + show() + " -> " + uca.compare(source, target));
        return uca.compare(source, target);
    }

//    public String show() {
//        if (uca != null) {
//            return " UCAversion=" + uca.getUCAVersion()
//                    + " Version=" + uca.getVersion()
//                    + " lang=" + uca.get
//                    + " french=" + uca.isFrenchCollation()
//                    + " lowerFirst=" + uca.isLowerCaseFirst()
//                    + " upperFirst=" + uca.isUpperCaseFirst()
//                    + " strength=" + uca.getStrength();
//        } else {
//            return "No RuleBasedCollator initialised";
//        }
//    }

    public CollationKey getJavaCollationKey(String source) {
        return uca.getCollationKey(source);
    }

    @Override
    public int hashCode() {
        return uca.hashCode();
    }

    /**
     * Set the properties for the UCA collation
     *
     * @param props the set of properties parsed from the UCA parameters
     * @throws XPathException
     */
    public void setProps(Properties props) throws XPathException {
        boolean fallbackError = false;
        String fallback = props.getProperty("fallback");
        if (fallback != null) {
            if (fallback.equals("yes")) {
            } else if (fallback.equals("no")) {
                error("fallback=no is not supported in Saxon-HE");
            } else {
                error("fallback", "yes|no");
            }
        }

        String lang = props.getProperty("lang");
        if (lang != null && lang.length() != 0) {
            ValidationFailure vf = StringConverter.STRING_TO_LANGUAGE.validate(lang);
            if (vf != null) {
                error("lang", "a valid language code");
            }
            String language, country = "", variant = "";
            String parts[] = lang.split("-");
            language = parts[0];
            if (parts.length > 1) {
                country = parts[1];
            }
            if (parts.length > 2) {
                variant = parts[2];
            }
            Locale loc = new Locale(language, country, variant);
            uca = (RuleBasedCollator) Collator.getInstance(loc);
        }

        String strength = props.getProperty("strength");
        if (strength != null) {
            if (strength.equals("primary") || strength.equals("1")) {
                setStrength(java.text.Collator.PRIMARY);
            } else if (strength.equals("secondary") || strength.equals("2")) {
                setStrength(java.text.Collator.SECONDARY);
            } else if (strength.equals("tertiary") || strength.equals("3")) {
                setStrength(java.text.Collator.TERTIARY);
            } else if (strength.equals("quaternary") || strength.equals("4")) {
                setStrength(java.text.Collator.IDENTICAL); // fallback to nearest supported option
            } else if (strength.equals("identical") || strength.equals("5")) {
                setStrength(java.text.Collator.IDENTICAL);
            }
        }

        String normalization = props.getProperty("normalization");
        if (normalization != null) {
            if (normalization.equals("yes")) {
                uca.setDecomposition(java.text.Collator.CANONICAL_DECOMPOSITION);
            } else if (normalization.equals("no")) {
                uca.setDecomposition(java.text.Collator.NO_DECOMPOSITION);
            }
        }
    }


    private Properties parseProps(String uri) throws XPathException {
        URI uuri;
        try {
            uuri = new URI(uri);
        } catch (URISyntaxException err) {
            throw new XPathException(err);
        }
        ArrayList<String> unknownKeys = new ArrayList<String>();
        Properties props = new Properties();
        String query = AnyURIValue.decode(uuri.getRawQuery());
        if (query != null && query.length() > 0) {
            for (String s : query.split(";")) {
                String tokens[] = s.split("=");
                if (!keys.contains(tokens[0])) {
                    unknownKeys.add(tokens[0]);
                }
                props.setProperty(tokens[0], tokens[1]);
            }
        }
        String fallback = props.getProperty("fallback");
        if (fallback != null && fallback.equals("no") && !unknownKeys.isEmpty()) {
            String message = unknownKeys.size() > 1 ? "unknown parameters:" : "unknown parameter:";
            for (String u : unknownKeys) {
                message += u + " ";
            }
            error(message);
        }
        return props;
    }

    public void setStrength(int newStrength) {
        uca.setStrength(newStrength);
    }

    public int getStrength() {
        return uca.getStrength();
    }

    //public ULocale getLocale() {
//        return uca.getLocale(ULocale.ACTUAL_LOCALE);
//    }

    @Override
    public boolean comparesEqual(CharSequence s1, CharSequence s2) {
        return uca.compare(s1, s2) == 0;
    }

    @Override
    public String getCollationURI() {
        return uri;
    }

    @Override
    public int compareStrings(CharSequence o1, CharSequence o2) {
        return uca.compare(o1, o2);
    }

    @Override
    public AtomicMatchKey getCollationKey(CharSequence s) {
        CollationKey ck = uca.getCollationKey(s.toString());
        return new CollationMatchKey(ck);
    }


    /**
     * Test whether one string contains another, according to the rules
     * of the XPath contains() function
     *
     * @param s1 the containing string
     * @param s2 the contained string
     * @return true iff s1 contains s2
     */

    public boolean contains(String s1, String s2) {
        RuleBasedCollator collator = getRuleBasedCollator();
        CollationElementIterator iter1 = collator.getCollationElementIterator(s1);
        CollationElementIterator iter2 = collator.getCollationElementIterator(s2);
        return collationContains(iter1, iter2, null, false);
    }

    /**
     * Test whether one string ends with another, according to the rules
     * of the XPath ends-with() function
     *
     * @param s1 the containing string
     * @param s2 the contained string
     * @return true iff s1 ends with s2
     */

    public boolean endsWith(String s1, String s2) {
        RuleBasedCollator collator = getRuleBasedCollator();
        CollationElementIterator iter1 = collator.getCollationElementIterator(s1);
        CollationElementIterator iter2 = collator.getCollationElementIterator(s2);
        return collationContains(iter1, iter2, null, true);
    }

    /**
     * Test whether one string starts with another, according to the rules
     * of the XPath starts-with() function
     *
     * @param s1 the containing string
     * @param s2 the contained string
     * @return true iff s1 starts with s2
     */

    public boolean startsWith(String s1, String s2) {
        RuleBasedCollator collator = getRuleBasedCollator();
        CollationElementIterator iter1 = collator.getCollationElementIterator(s1);
        CollationElementIterator iter2 = collator.getCollationElementIterator(s2);
        return collationStartsWith(iter1, iter2);
    }

    /**
     * Return the part of a string after a given substring, according to the rules
     * of the XPath substring-after() function
     *
     * @param s1 the containing string
     * @param s2 the contained string
     * @return the part of s1 that follows the first occurrence of s2
     */

    public String substringAfter(String s1, String s2) {
        RuleBasedCollator collator = getRuleBasedCollator();
        CollationElementIterator iter1 = collator.getCollationElementIterator(s1);
        CollationElementIterator iter2 = collator.getCollationElementIterator(s2);
        int[] ia = new int[2];
        boolean ba = collationContains(iter1, iter2, ia, false);
        if (ba) {
            return s1.substring(ia[1]);
        } else {
            return "";
        }
    }

    /**
     * Return the part of a string before a given substring, according to the rules
     * of the XPath substring-before() function
     *
     * @param s1 the containing string
     * @param s2 the contained string
     * @return the part of s1 that precedes the first occurrence of s2
     */

    public String substringBefore(String s1, String s2) {
        RuleBasedCollator collator = getRuleBasedCollator();
        CollationElementIterator iter1 = collator.getCollationElementIterator(s1);
        CollationElementIterator iter2 = collator.getCollationElementIterator(s2);
        int[] ib = new int[2];
        boolean bb = collationContains(iter1, iter2, ib, false);
        if (bb) {
            return s1.substring(0, ib[0]);
        } else {
            return "";
        }
    }

    /**
     * Determine whether one string starts with another, under the terms of a given
     * collating sequence.
     *
     * @param s0 iterator over the collation elements of the containing string
     * @param s1 iterator over the collation elements of the contained string
     * @return true if the first string starts with the second
     */

    private boolean collationStartsWith(CollationElementIterator s0,
                                        CollationElementIterator s1) {
        makeStrengthObject();
        while (true) {
            int e0, e1;
            e1 = s1.next();
            if (e1 == CollationElementIterator.NULLORDER) {
                return true;
            }
            e0 = s0.next();
            if (e0 == CollationElementIterator.NULLORDER) {
                return false;
            }
            if (strengthLevel.compare(e0, e1) != 0) {
                return false;
            }
        }
    }

    private String show(int ce) {
        return "" + CollationElementIterator.primaryOrder(ce) + "/" +
                CollationElementIterator.secondaryOrder(ce) + "/" +
                CollationElementIterator.tertiaryOrder(ce);
    }

    private void makeStrengthObject() {
        if (strengthLevel == null) {
            switch (getStrength()) {
                case com.ibm.icu.text.Collator.PRIMARY:
                    strengthLevel = new Primary();
                    break;
                case com.ibm.icu.text.Collator.SECONDARY:
                    strengthLevel = new Secondary();
                    break;
                case com.ibm.icu.text.Collator.TERTIARY:
                    strengthLevel = new Tertiary();
                    break;
                default:
                    strengthLevel = new Identical();
                    break;
            }
        }
    }

    /**
     * Determine whether one string contains another, under the terms of a given
     * collating sequence. If matchAtEnd=true, the match must be at the end of the first
     * string.
     *
     * @param s0         iterator over the collation elements of the containing string
     * @param s1         iterator over the collation elements of the contained string
     * @param offsets    may be null, but if it is supplied, it must be an array of two
     *                   integers which, if the function returns true, will contain the start position of the
     *                   first matching substring, and the offset of the first character after the first
     *                   matching substring. This is not available for matchAtEnd=true
     * @param matchAtEnd true if the match is required to be at the end of the string
     * @return true if the first string contains the second
     */

    private boolean collationContains(CollationElementIterator s0,
                                      CollationElementIterator s1,
                                      /*@Nullable*/ int[] offsets,
                                      boolean matchAtEnd) {
        makeStrengthObject();
        int e0, e1;
        e1 = s1.next();
        if (e1 == CollationElementIterator.NULLORDER) {
            return true;
        }
        e0 = CollationElementIterator.NULLORDER;
        while (true) {
            // scan the first string to find a matching character
            while (strengthLevel.compare(e0, e1) != 0) {
                e0 = s0.next();
                if (e0 == CollationElementIterator.NULLORDER) {
                    // hit the end, no match
                    return false;
                }
            }
            // matched first character, note the position of the possible match
            int start = s0.getOffset();
            if (collationStartsWith(s0, s1)) {
                if (matchAtEnd) {
                    e0 = s0.next();
                    if (e0 == CollationElementIterator.NULLORDER) {
                        // the match is at the end
                        return true;
                    }
                    // else ignore this match and keep looking
                } else {
                    if (offsets != null) {
                        offsets[0] = start - 1;
                        offsets[1] = s0.getOffset();
                    }
                    return true;
                }
            }
            // reset the position and try again
            s0.setOffset(start);

            // workaround for a difference between JDK 1.4.0 and JDK 1.4.1
            if (s0.getOffset() != start) {
                // JDK 1.4.0 takes this path
                s0.next();
            }
            s1.reset();
            e0 = -1;
            e1 = s1.next();
            // loop round to try again
        }
    }


    /**
     * Compare two integers
     *
     * @param a an integer
     * @param b another integer
     * @return -1, 0, +1
     */

    private static int intCompare(int a, int b) {
        return Integer.valueOf(a).compareTo(b);
        // TODO: in JDK 7, this can be Integer.compare(a, b)
    }


    public interface Strength {
        int compare(int ce1, int ce2);
    }

    public class Primary implements Strength {
        @Override
        public int compare(int ce1, int ce2) {
            return intCompare(CollationElementIterator.primaryOrder(ce1), CollationElementIterator.primaryOrder(ce2));
        }

    }

    public class Secondary implements Strength {
        @Override
        public int compare(int ce1, int ce2) {
            int c1 = intCompare(CollationElementIterator.primaryOrder(ce1), CollationElementIterator.primaryOrder(ce2));
            if (c1 == 0) {
                return intCompare(CollationElementIterator.secondaryOrder(ce1), CollationElementIterator.secondaryOrder(ce2));
            } else {
                return c1;
            }
        }

    }

    public class Tertiary implements Strength {
        @Override
        public int compare(int ce1, int ce2) {
            int c1 = intCompare(CollationElementIterator.primaryOrder(ce1), CollationElementIterator.primaryOrder(ce2));
            if (c1 == 0) {
                int c2 = intCompare(CollationElementIterator.secondaryOrder(ce1), CollationElementIterator.secondaryOrder(ce2));
                if (c2 == 0) {
                    return intCompare(CollationElementIterator.tertiaryOrder(ce1), CollationElementIterator.tertiaryOrder(ce2));
                } else {
                    return c2;
                }
            } else {
                return c1;
            }
        }

    }

    public class Identical implements Strength {
        @Override
        public int compare(int ce1, int ce2) {
            return intCompare(ce1, ce2);
        }

    }


}
