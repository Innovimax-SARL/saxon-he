////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;


import net.sf.saxon.Configuration;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.number.*;
import net.sf.saxon.lib.Numberer;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.regex.ARegularExpression;
import net.sf.saxon.regex.charclass.Categories;
import net.sf.saxon.regex.RegularExpression;
import net.sf.saxon.regex.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.value.IntegerValue;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.z.IntHashSet;
import net.sf.saxon.z.IntPredicate;
import net.sf.saxon.z.IntSet;

import java.util.ArrayList;
import java.util.List;

public class FormatInteger extends SystemFunction {

    //TODO - initialise Numberer and prepare the picture at compile time if possible

    private static RegularExpression badHashPattern;
    private static RegularExpression modifierPattern;
    private static RegularExpression decimalDigitPattern;

    public static final String preface = "In the picture string for format-integer, ";

    static {
        try {
            badHashPattern = new ARegularExpression("((\\d+|\\w+)#+.*)|(#+[^\\d]+)", "", "XP20", null);
            modifierPattern = new ARegularExpression("([co](\\(.*\\))?)?[at]?", "", "XP20", null);
            decimalDigitPattern = new ARegularExpression("^((\\p{Nd}|#|[^\\p{N}\\p{L}])+?)$", "", "XP20", null);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as SequenceIterators
     * @return the result of the evaluation, in the form of a SequenceIterator
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    public StringValue call(XPathContext context, Sequence[] arguments) throws XPathException {
        return formatInteger(
                (IntegerValue) arguments[0].head(),
                (StringValue) arguments[1].head(),
                arguments.length == 2 ? null : (StringValue) arguments[2].head(),
                context);
    }

    private StringValue formatInteger(IntegerValue num, StringValue picture, /*@Nullable*/ StringValue language, XPathContext context) throws XPathException {
        Configuration config = context.getConfiguration();

        if (num == null) {
            return StringValue.EMPTY_STRING;
        }

        String languageVal;
        if (language != null) {
            languageVal = language.getStringValue();
        } else {
            //default language
            languageVal = config.getDefaultLanguage();
        }

        boolean hasSign = false;
        if (num.signum() == -1) {
            hasSign = true;
        }
        num = (IntegerValue) num.abs();
        String pic = picture.getStringValue();
        if (pic.isEmpty()) {
            throw new XPathException(preface + "the picture cannot be empty", "FODF1310");
        }

        String primaryToken;
        String modifier;
        String parenthetical;

        int lastSemicolon = pic.lastIndexOf(';');
        if (lastSemicolon >= 0) {
            primaryToken = pic.substring(0, lastSemicolon);
            if (primaryToken.isEmpty()) {
                throw new XPathException(preface + "the primary format token cannot be empty", "FODF1310");
            }
            modifier = lastSemicolon < pic.length() - 1 ? pic.substring(lastSemicolon + 1) : "";
            if (!modifierPattern.matches(modifier)) {
                throw new XPathException(preface + "the modifier is invalid", "FODF1310");
            }
        } else {
            primaryToken = pic;
            modifier = "";
        }

        //boolean cardinal = modifier.startsWith("c");
        boolean ordinal = modifier.startsWith("o");
        //boolean traditional = modifier.endsWith("t");
        boolean alphabetic = modifier.endsWith("a");

        int leftParen = modifier.indexOf('(');
        int rightParen = modifier.lastIndexOf(')');

        parenthetical = leftParen < 0 ? "" : modifier.substring(leftParen + 1, rightParen);

        String letterValue = alphabetic ? "alphabetic" : "traditional";
        String ordinalValue = ordinal ? "".equals(parenthetical) ? "yes" : parenthetical : "";

        Numberer numb = config.makeNumberer(languageVal, null);

        UnicodeString primary = UnicodeString.makeUnicodeString(primaryToken);
        IntPredicate isDecimalDigit = Categories.getCategory("Nd");
        boolean isDecimalDigitPattern = false;
        for (int i = 0; i < primary.uLength(); i++) {
            if (isDecimalDigit.matches(primary.uCharAt(i))) {
                isDecimalDigitPattern = true;
                break;
            }
        }
        if (isDecimalDigitPattern) {
            if (!decimalDigitPattern.matches(primaryToken)) {
                throw new XPathException(
                        preface + "the primary format token contains a decimal digit but does not " +
                            "meet the rules for a decimal digit pattern", "FODF1310");
            }
            NumericGroupFormatter picGroupFormat = getPicSeparators(primaryToken);
            UnicodeString adjustedPicture = picGroupFormat.getAdjustedPicture();
            String str = numb.format(num.longValue(), adjustedPicture, picGroupFormat, letterValue, ordinalValue);
            return hasSign ? new StringValue("-" + str) : new StringValue(str);
        } else {
            UnicodeString token = UnicodeString.makeUnicodeString(primaryToken);
            String str = numb.format(num.longValue(), token, null, letterValue, ordinalValue);
            return hasSign ? new StringValue("-" + str) : new StringValue(str);
        }
    }

    /**
     * Get the picture separators and filter
     * them out of the picture. Has side effect of creating a simplified picture, which
     * it makes available as the getAdjustedPicture() property of the returned NumericGroupFormatter.
     *
     * @param pic the formatting picture, after stripping off any modifiers
     * @return a NumericGroupFormatter that implements the formatting defined in the picture
     * @throws net.sf.saxon.trans.XPathException
     *          if the picture is invalid
     */
    public static NumericGroupFormatter getPicSeparators(String pic) throws XPathException {

        UnicodeString picExpanded = UnicodeString.makeUnicodeString(pic);
        IntSet groupingPositions = new IntHashSet(5);
        List<Integer> separatorList = new ArrayList<Integer>();
        int groupingPosition = 0; // number of digits to the right of a grouping separator
        int firstGroupingPos = 0; // number of digits to the right of the first grouping separator
        int lastGroupingPos = 0;
        boolean regularCheck = true;
        int zeroDigit = -1;

        if (badHashPattern.matches(pic)) {
            throw new XPathException(preface + "the picture is not valid (it uses '#' where disallowed)", "FODF1310");
        }

        for (int i = picExpanded.uLength() - 1; i >= 0; i--) {

            final int codePoint = picExpanded.uCharAt(i);
            switch (Character.getType(codePoint)) {

                case Character.DECIMAL_DIGIT_NUMBER:

                    if (zeroDigit == -1) {
                        zeroDigit = Alphanumeric.getDigitFamily(codePoint);
                    } else {
                        if (zeroDigit != Alphanumeric.getDigitFamily(codePoint)) {
                            throw new XPathException(
                                    preface + "the picture mixes digits from different digit families", "FODF1310");
                        }
                    }
                    groupingPosition++;
                    break;

                case Character.LETTER_NUMBER:
                case Character.OTHER_NUMBER:
                case Character.UPPERCASE_LETTER:
                case Character.LOWERCASE_LETTER:
                case Character.MODIFIER_LETTER:
                case Character.OTHER_LETTER:
                    break;

                default:
                    if (i == picExpanded.uLength() - 1) {
                        throw new XPathException(preface + "the picture cannot end with a separator", "FODF1310");
                    }
                    if (codePoint == '#') {
                        groupingPosition++;
                        if (i != 0) {
                            switch (Character.getType(picExpanded.uCharAt(i - 1))) {
                                case Character.DECIMAL_DIGIT_NUMBER:
                                case Character.LETTER_NUMBER:
                                case Character.OTHER_NUMBER:
                                case Character.UPPERCASE_LETTER:
                                case Character.LOWERCASE_LETTER:
                                case Character.MODIFIER_LETTER:
                                case Character.OTHER_LETTER:
                                    throw new XPathException(
                                            preface + "the picture cannot contain alphanumeric character(s) before character '#'", "FODF1310");
                            }
                        }
//
                        break;
                    } else {
                        boolean added = groupingPositions.add(groupingPosition);
                        if (!added) {
                            throw new XPathException(preface + "the picture contains consecutive separators", "FODF1310");
                        }
                        separatorList.add(codePoint);

                        if (groupingPositions.size() == 1) {
                            firstGroupingPos = groupingPosition;
                        } else {

                            if (groupingPosition != firstGroupingPos * groupingPositions.size()) {
                                regularCheck = false;
                            }
                            if (separatorList.get(0) != codePoint) {
                                regularCheck = false;
                            }

                        }

                        if (i == 0) {
                            throw new XPathException(preface + "the picture cannot begin with a separator", "FODF1310");
                        }
                        lastGroupingPos = groupingPosition;
                    }
            }
        }
        if (regularCheck && groupingPositions.size() >= 1) {
            if (picExpanded.uLength() - lastGroupingPos - groupingPositions.size() > firstGroupingPos) {
                regularCheck = false;
            }
        }

        UnicodeString adjustedPic = extractSeparators(picExpanded, groupingPositions);
        if (groupingPositions.size() == 0) {
            return new RegularGroupFormatter(0, "", adjustedPic);
        }

        if (regularCheck) {
            if (separatorList.isEmpty()) {
                return new RegularGroupFormatter(0, "", adjustedPic);
            } else {
                FastStringBuffer sb = new FastStringBuffer(4);
                sb.appendWideChar(separatorList.get(0));
                return new RegularGroupFormatter(firstGroupingPos, sb.toString(), adjustedPic);
            }
        } else {
            return new IrregularGroupFormatter(groupingPositions, separatorList, adjustedPic);
        }
    }

    /**
     * Return a string in which characters at given positions have been removed
     *
     * @param arr              the string to be filtered
     * @param excludePositions the positions to be filtered out from the string, counted as positions from the end
     * @return the items from the original array that are not filtered out
     */
    private static UnicodeString extractSeparators(UnicodeString arr, IntSet excludePositions) {

        FastStringBuffer fsb = new FastStringBuffer(arr.uLength());
        for (int i = 0; i < arr.uLength(); i++) {
            if (NumberFormatter.isLetterOrDigit(arr.uCharAt(i))) {
                fsb.appendWideChar(arr.uCharAt(i));
            }
        }
        return UnicodeString.makeUnicodeString(fsb);
    }

}

//Copyright (c) 2010 Saxonica Limited.