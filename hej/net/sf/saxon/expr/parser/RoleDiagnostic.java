////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.parser;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.UType;

/**
 * A RoleDiagnostic (formerly RoleLocator) identifies the role in which an expression is used,
 * for example as the third argument of the concat() function. This information is stored in an
 * ItemChecker or CardinalityChecker so that good diagnostics can be
 * achieved when run-time type errors are detected.
 */
public class RoleDiagnostic {

    private int kind;
    private String operation;
    private int operand;
    private String errorCode = "XPTY0004";  // default error code for type errors

    public static final int FUNCTION = 0;
    public static final int BINARY_EXPR = 1;
    public static final int TYPE_OP = 2;
    public static final int VARIABLE = 3;
    public static final int INSTRUCTION = 4;
    public static final int FUNCTION_RESULT = 5;
    public static final int ORDER_BY = 6;
    public static final int TEMPLATE_RESULT = 7;
    public static final int PARAM = 8;
    public static final int UNARY_EXPR = 9;
    public static final int UPDATING_EXPR = 10;
    public static final int GROUPING_KEY = 11;
    public static final int EVALUATE_RESULT = 12;
    public static final int CONTEXT_ITEM = 13;
    public static final int AXIS_STEP = 14;
    public static final int OPTION = 15;
    public static final int CHARACTER_MAP_EXPANSION = 16;
    public static final int DOCUMENT_ORDER = 17;
    public static final int MAP_CONSTRUCTOR = 18;


    /**
     * Create information about the role of a subexpression within its parent expression
     *
     * @param kind      the kind of parent expression, e.g. a function call or a variable reference
     * @param operation the name of the object in the parent expression, e.g. a function name or
     *                  instruction name. A QName is provided in display form, prefix:local.
     *                  For a string, the special format element/attribute is recognized, for example xsl:for-each/select,
     *                  to identify the role of an XPath expression in a stylesheet.
     * @param operand   Ordinal position of this subexpression, e.g. the position of an argument in
     *                  a function call
     */



    public RoleDiagnostic(int kind, String operation, int operand) {
        this.kind = kind;
        this.operation = operation;
        this.operand = operand;
    }

    /**
     * Set the error code to be produced if a type error is detected
     *
     * @param code The error code
     */

    public void setErrorCode(/*@Nullable*/ String code) {
        if (code != null) {
            this.errorCode = code;
        }
    }

    /**
     * Get the error code to be produced if a type error is detected
     *
     * @return code The error code
     */

    public String getErrorCode() {
        return errorCode;
    }

    /**
     * Ask whether the error code represents a type error
     *
     * @return true if the error is treated as a type error
     */

    public boolean isTypeError() {
        return !errorCode.startsWith("FORG") && !errorCode.equals("XPDY0050");
    }

    /**
     * Construct and return the error message indicating a type error
     *
     * @return the constructed error message
     */
    public String getMessage() {
        String name = operation;

        switch (kind) {
            case FUNCTION:
                if (name.equals("saxon:call") || name.equals("saxon:apply")) {
                    if (operand == 0) {
                        return "target of dynamic function call";
                    } else {
                        return ordinal(operand) + " argument of dynamic function call";
                    }
                } else {
                    return ordinal(operand + 1) + " argument of " +
                            (name.isEmpty() ? "anonymous function" : name + "()");
                }
            case BINARY_EXPR:
                return ordinal(operand + 1) + " operand of '" + name + '\'';
            case UNARY_EXPR:
                return "operand of '-'";
            case TYPE_OP:
                return "value in '" + name + "' expression";
            case VARIABLE:
                if (name.equals("saxon:context-item")) {
                    return "context item";
                } else {
                    return "value of variable $" + name;
                }
            case INSTRUCTION:
                int slash = name.indexOf('/');
                String attributeName = "";
                if (slash >= 0) {
                    attributeName = name.substring(slash + 1);
                    name = name.substring(0, slash);
                }
                return '@' + attributeName + " attribute of " + (name.equals("LRE") ? "a literal result element" : name);
            case FUNCTION_RESULT:
                if (name.isEmpty()) {
                    return "result of anonymous function";
                } else {
                    return "result of call to " + name;
                }
            case TEMPLATE_RESULT:
                return "result of template " + name;
            case ORDER_BY:
                return ordinal(operand + 1) + " sort key";
            case PARAM:
                return "value of parameter $" + name;
            case UPDATING_EXPR:
                return "value of " + ordinal(operand + 1) + " operand of " + name + " expression";
            case GROUPING_KEY:
                return "value of the grouping key";
            case EVALUATE_RESULT:
                return "result of the expression {" + name + "} evaluated by xsl:evaluate";
            case CONTEXT_ITEM:
                return "the context item";
            case AXIS_STEP:
                return "the context item for the " + AxisInfo.axisName[operand] + " axis";
            case OPTION:
                return "the value of the " + name + " option";
            case CHARACTER_MAP_EXPANSION:
                return "the substitute value for character '" + name + "' in the character map";
            case DOCUMENT_ORDER:
                return "document-order sorter";
            case MAP_CONSTRUCTOR:
                return "xsl:map sequence constructor";
            default:
                return "";
        }
    }

    /**
     * Construct the part of the message giving the required item type
     *
     * @param requiredItemType the item type required by the context of a particular expression
     * @return a message of the form "Required item type of X is Y"
     */

    public String composeRequiredMessage(ItemType requiredItemType) {
        return "Required item type of " + getMessage() +
                " is " + requiredItemType.toString();
    }

    /**
     * Construct a full error message
     *
     * @param requiredItemType the item type required by the context of a particular expression
     * @param suppliedItemType the item type inferred by static analysis of an expression
     * @return a message of the form "Required item type of A is R; supplied value has item type S"
     */

    public String composeErrorMessage(ItemType requiredItemType, ItemType suppliedItemType) {
        return "Required item type of " + getMessage() +
                " is " + requiredItemType.toString() +
                "; supplied value has item type " +
                suppliedItemType.toString();
    }

    /**
     * Construct a full error message
     *
     * @param requiredItemType the item type required by the context of a particular expression
     * @param supplied the supplied expression
     * @param suppliedItemType the item type inferred by static analysis of an expression
     * @return a message of the form "Required item type of A is R; supplied value has item type S"
     */

    public String composeErrorMessage(ItemType requiredItemType, Expression supplied, ItemType suppliedItemType) {
        return "Required item type of " + getMessage() +
                " is " + requiredItemType.toString() +
                "; supplied value (" + supplied.toShortString() + ") has item type " +
                suppliedItemType.toString();
    }

    /**
     * Construct a full error message
     *
     * @param requiredItemType the item type required by the context of a particular expression
     * @param suppliedItemType the item type inferred by static analysis of an expression
     * @return a message of the form "Required item type of A is R; supplied value has item type S"
     */

    public String composeErrorMessage(ItemType requiredItemType, UType suppliedItemType) {
        return "Required item type of " + getMessage() +
                " is " + requiredItemType.toString() +
                "; supplied value has item type " +
                suppliedItemType.toString();
    }

    /**
     * Save as a string, for use when serializing the expression tree
     * @return a string representation of the object
     */

    public String save() {
        FastStringBuffer fsb = new FastStringBuffer(FastStringBuffer.C256);
        fsb.append(kind + "|");
        fsb.append(operand + "|");
        fsb.append(errorCode.equals("XPTY0004") ? "" : errorCode);
        fsb.append("|");
        fsb.append(operation);
        return fsb.toString();
    }

    /**
     * Reconstruct from a saved string
     * @param in the saved string representation of the RoleDiagnostic
     */

    public static RoleDiagnostic reconstruct(String in) {
        int v = in.indexOf('|');
        int kind = Integer.parseInt(in.substring(0, v));
        int w = in.indexOf('|', v+1);
        int operand = Integer.parseInt(in.substring(v+1, w));
        int x = in.indexOf('|', w+1);
        String errorCode = in.substring(w+1, x);
        String operation = in.substring(x+1);
        RoleDiagnostic cd = new RoleDiagnostic(kind, operation, operand);
        if (!errorCode.isEmpty()) {
            cd.setErrorCode(errorCode);
        }
        return cd;
    }

    /**
     * Get the ordinal representation of a number (used to identify which argument of a function
     * is in error)
     *
     * @param n the cardinal number
     * @return the ordinal representation
     */
    public static String ordinal(int n) {
        switch (n) {
            case 1:
                return "first";
            case 2:
                return "second";
            case 3:
                return "third";
            default:
                // we can live with 21th, 22th... How many functions have >20 arguments?
                return n + "th";
        }
    }


}

