////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.json;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.StringToDouble;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.value.Whitespace;

import java.util.Map;

/**
 * Parser for JSON, which notifies parsing events to a JsonHandler
 */
public class JsonParser {

    public static final int ESCAPE = 1;
    public static final int ALLOW_ANY_TOP_LEVEL = 2;
    public static final int LIBERAL = 4;
    public static final int VALIDATE = 8;
    public static final int DEBUG = 16;
    public static final int DUPLICATES_RETAINED = 32;
    public static final int DUPLICATES_LAST = 64;
    public static final int DUPLICATES_FIRST = 128;
    public static final int DUPLICATES_REJECTED = 256;

    public static final int DUPLICATES_SPECIFIED = DUPLICATES_FIRST | DUPLICATES_LAST | DUPLICATES_RETAINED | DUPLICATES_REJECTED;

    private static final String ERR_GRAMMAR = "FOJS0001";
    private static final String ERR_DUPLICATE = "FOJS0003";
    private static final String ERR_SCHEMA = "FOJS0004";
    private static final String ERR_OPTIONS = "FOJS0005";

    /**
     * Create a JSON parser
     */

    public JsonParser() {
    }

    /**
     * Parse the JSON string according to supplied options
     *
     *
     * @param input   JSON input string
     * @param flags options for the conversion as a map of xs:string : value pairs
     * @param handler event handler to which parsing events are notified
     * @param context XPath evaluation context
     * @throws XPathException if the syntax of the input is incorrect
     */
    public void parse(String input, int flags, JsonHandler handler, XPathContext context) throws XPathException {
        if (input.isEmpty()) {
            invalidJSON("An empty string is not valid JSON", ERR_GRAMMAR);
        }

        JsonTokenizer t = new JsonTokenizer(input);
        t.next();

        parseConstruct(handler, t, flags, context);

        if (t.next() != JsonTokenizer.EOF) {
            invalidJSON("Unexpected token beyond end of JSON input", ERR_GRAMMAR);
        }

    }


    public static int getFlags(Map<String, Sequence> options, XPathContext context, Boolean allowValidate) throws XPathException {
        int flags = 0;
        BooleanValue debug = (BooleanValue) options.get("debug");
        if (debug != null && debug.getBooleanValue()) {
            flags |= DEBUG;
        }

        boolean escape = ((BooleanValue) options.get("escape")).getBooleanValue();
        if (escape) {
            flags |= ESCAPE;
            if (options.get("fallback") != null) {
                throw new XPathException("Cannot specify a fallback function when escape=true", "FOJS0005");
            }
        }

        if (((BooleanValue) options.get("liberal")).getBooleanValue()) {
            flags |= LIBERAL;
            flags |= ALLOW_ANY_TOP_LEVEL;
        }

        boolean validate = false;
        if (allowValidate) {
            validate = ((BooleanValue) options.get("validate")).getBooleanValue();
            if (validate) {
                if (!context.getController().getExecutable().isSchemaAware()) {
                    error("Requiring validation on non-schema-aware processor", ERR_SCHEMA);
                }
                flags |= VALIDATE;
            }
        }

        if (options.containsKey("duplicates")) {
            String duplicates = ((StringValue) options.get("duplicates")).getStringValue();
            if ("reject".equals(duplicates)) {
                flags |= DUPLICATES_REJECTED;
            } else if ("use-last".equals(duplicates)) {
                flags |= DUPLICATES_LAST;
            } else if ("use-first".equals(duplicates)) {
                flags |= DUPLICATES_FIRST;
            } else if ("retain".equals(duplicates)) {
                flags |= DUPLICATES_RETAINED;
            } else {
                error("Invalid value for 'duplicates' option", ERR_OPTIONS);
            }
            if (validate && "retain".equals(duplicates)) {
                error("The options validate:true and duplicates:retain cannot be used together", ERR_OPTIONS);
            }
        }
        return flags;
    }

    /**
     * Parse a JSON construct (top-level or nested)
     *
     * @param handler   the handler to generate the result
     * @param tokenizer the tokenizer, positioned at the first token of the construct to be read
     * @param flags     parsing options
     * @param context   XPath evaluation context
     * @throws net.sf.saxon.trans.XPathException if a dynamic error occurs (for example, invalid JSON input)
     */

    private void parseConstruct(JsonHandler handler, JsonTokenizer tokenizer, int flags, XPathContext context) throws XPathException {
        boolean debug = (flags & DEBUG) != 0;
        if (debug) {
            System.err.println("token:" + tokenizer.currentToken + " :" + tokenizer.currentTokenValue);
        }
        switch (tokenizer.currentToken) {
            case JsonTokenizer.LCURLY:
                parseObject(handler, tokenizer, flags, context);
                break;

            case JsonTokenizer.LSQB:
                parseArray(handler, tokenizer, flags, context);
                break;

            case JsonTokenizer.NUMERIC_LITERAL:
                double d = parseNumericLiteral(tokenizer.currentTokenValue.toString(), flags);
                handler.writeNumeric(tokenizer.currentTokenValue.toString(), d);
                break;

            case JsonTokenizer.TRUE:
                handler.writeBoolean(true);
                break;

            case JsonTokenizer.FALSE:
                handler.writeBoolean(false);
                break;

            case JsonTokenizer.NULL:
                handler.writeNull();
                break;

            case JsonTokenizer.STRING_LITERAL:
                String literal = tokenizer.currentTokenValue.toString();
                handler.writeString(unescape(literal, flags, ERR_GRAMMAR));
                break;

            default:
                invalidJSON("Unexpected symbol: " + tokenizer.currentTokenValue, ERR_GRAMMAR);
        }
    }

    /**
     * Parse a JSON object (or map), i.e. construct delimited by curly braces
     *
     * @param handler   the handler to generate the result
     * @param tokenizer the tokenizer, positioned at the object to be read
     * @param flags     parsing options as a set of flags
     * @param context   XPath evaluation context
     * @throws net.sf.saxon.trans.XPathException if a dynamic error occurs (such as invalid JSON input)
     */

    private void parseObject(JsonHandler handler, JsonTokenizer tokenizer, int flags, XPathContext context) throws XPathException {
        boolean liberal = (flags & LIBERAL) != 0;
        handler.startMap();
        int tok = tokenizer.next();
        while (tok != JsonTokenizer.RCURLY) {
            if (tok != JsonTokenizer.STRING_LITERAL) {
                invalidJSON("Property name must be a string literal", ERR_GRAMMAR);
            }
            String key = tokenizer.currentTokenValue.toString();
            key = unescape(key, flags, ERR_GRAMMAR);
            String reEscaped = handler.reEscape(key);
            tok = tokenizer.next();
            if (tok != JsonTokenizer.COLON) {
                invalidJSON("Missing colon after \"" + Err.wrap(key) + "\"", ERR_GRAMMAR);
            }
            tokenizer.next();
            boolean duplicate = handler.setKey(key, reEscaped);
            if (duplicate && ((flags & DUPLICATES_REJECTED) != 0)) {
                invalidJSON("Duplicate key value \"" + Err.wrap(key) + "\"", ERR_DUPLICATE);
            }
            if (!duplicate || ((flags & (DUPLICATES_LAST|DUPLICATES_RETAINED)) != 0)) {
                parseConstruct(handler, tokenizer, flags, context);
            } else {
                // retain first: parse the duplicate value but discard it
                JsonHandler h2 = new JsonHandler();
                h2.setContext(context);
                parseConstruct(h2, tokenizer, flags, context);
            }
            tok = tokenizer.next();
            if (tok == JsonTokenizer.COMMA) {
                tok = tokenizer.next();
                if (tok == JsonTokenizer.RCURLY) {
                    if (liberal) {
                        break;  // tolerate the trailing comma
                    } else {
                        invalidJSON("Trailing comma after entry in object", ERR_GRAMMAR);
                    }
                }
            } else if (tok == JsonTokenizer.RCURLY) {
                break;
            } else {
                invalidJSON("Unexpected token after value of \"" + Err.wrap(key) + "\" property", ERR_GRAMMAR);
            }
        }
        handler.endMap();
    }

    /**
     * Parse a JSON array, i.e. construct delimited by square brackets
     *
     * @param handler   the handler to generate the result
     * @param tokenizer the tokenizer, positioned at the object to be read
     * @param flags     parsing options
     * @param context   XPath evaluation context
     * @throws net.sf.saxon.trans.XPathException if a dynamic error occurs (such as invalid JSON input)
     */

    private void parseArray(JsonHandler handler, JsonTokenizer tokenizer, int flags, XPathContext context) throws XPathException {
        boolean liberal = (flags & LIBERAL) != 0;
        handler.startArray();
        int tok = tokenizer.next();
        if (tok == JsonTokenizer.RSQB) {
            handler.endArray();
            return;
        }
        while (true) {
            parseConstruct(handler, tokenizer, flags, context);
            tok = tokenizer.next();
            if (tok == JsonTokenizer.COMMA) {
                tok = tokenizer.next();
                if (tok == JsonTokenizer.RSQB) {
                    if (liberal) {
                        break;// tolerate the trailing comma
                    } else {
                        invalidJSON("Trailing comma after entry in array", ERR_GRAMMAR);
                    }
                }
            } else if (tok == JsonTokenizer.RSQB) {
                break;
            } else {
                invalidJSON("Unexpected token after entry in array", ERR_GRAMMAR);
            }
        }
        handler.endArray();
    }

    /**
     * Parse a JSON numeric literal,
     *
     * @param token the numeric literal to be parsed and converted
     * @param flags parsing options
     * @return the result of parsing and conversion to XDM
     * @throws net.sf.saxon.trans.XPathException if a dynamic error occurs (such as invalid JSON input)
     */

    private double parseNumericLiteral(String token, int flags) throws XPathException {
        try {
            if ((flags & LIBERAL) == 0) {
                // extra checks on the number disabled by choosing spec="liberal"
                if (token.startsWith("+")) {
                    invalidJSON("Leading + sign not allowed: " + token, ERR_GRAMMAR);
                } else {
                    String t = token;
                    if (t.startsWith("-")) {
                        t = t.substring(1);
                    }
                    if (t.startsWith("0") &&
                        !(t.equals("0") || t.startsWith("0.") || t.startsWith("0e") || t.startsWith("0E"))) {
                        invalidJSON("Redundant leading zeroes not allowed: " + token, ERR_GRAMMAR);
                    }
                    if (t.endsWith(".") || t.contains(".e") || t.contains(".E")) {
                        invalidJSON("Empty fractional part not allowed", ERR_GRAMMAR);
                    }
                    if (t.startsWith(".")) {
                        invalidJSON("Empty integer part not allowed", ERR_GRAMMAR);
                    }
                }
            }
            return StringToDouble.getInstance().stringToNumber(token);
        } catch (NumberFormatException e) {
            invalidJSON("Invalid numeric literal: " + e.getMessage(), ERR_GRAMMAR);
            return Double.NaN;
        }
    }

    /**
     * Unescape a JSON string literal,
     *
     * @param literal the string literal to be processed
     * @param flags   parsing options
     * @param errorCode Error code
     * @return the result of parsing and conversion to XDM
     * @throws net.sf.saxon.trans.XPathException if a dynamic error occurs (such as invalid JSON input)
     */

    public static String unescape(String literal, int flags, String errorCode) throws XPathException {
        if (literal.indexOf('\\') < 0) {
            return literal;
        }
        boolean liberal = (flags & LIBERAL) != 0;
        FastStringBuffer buffer = new FastStringBuffer(literal.length());
        for (int i = 0; i < literal.length(); i++) {
            char c = literal.charAt(i);
            if (c == '\\') {
                if (i++ == literal.length() - 1) {
                    throw new XPathException("Invalid JSON escape: String " + Err.wrap(literal) + " ends in backslash", errorCode);
                }
                switch (literal.charAt(i)) {
                    case '"':
                        buffer.append('"');
                        break;
                    case '\\':
                        buffer.append('\\');
                        break;
                    case '/':
                        buffer.append('/');
                        break;
                    case 'b':
                        buffer.append('\b');
                        break;
                    case 'f':
                        buffer.append('\f');
                        break;
                    case 'n':
                        buffer.append('\n');
                        break;
                    case 'r':
                        buffer.append('\r');
                        break;
                    case 't':
                        buffer.append('\t');
                        break;
                    case 'u':
                        try {
                            String hex = literal.substring(i + 1, i + 5);
                            int code = Integer.parseInt(hex, 16);
                            buffer.append((char) code);
                            i += 4;
                        } catch (Exception e) {
                            if (liberal) {
                                buffer.append("\\u");
                            } else {
                                throw new XPathException("Invalid JSON escape: \\u must be followed by four hex characters", errorCode);
                            }
                        }
                        break;
                    default:
                        if (liberal) {
                            buffer.append(c);
                        } else {
                            throw new XPathException("Unknown escape sequence \\" + literal.charAt(i), errorCode);
                        }
                }
            } else {
                buffer.append(c);
            }
        }
        return buffer.toString();
    }

    /**
     * Throw an error
     *
     * @param message the error message
     * @param code    the error code to be used
     * @throws net.sf.saxon.trans.XPathException always
     */

    private static void error(String message, String code)
            throws XPathException {
        throw new XPathException(message, code);
    }

    /**
     * Throw an error
     *
     * @param message the error message
     * @param code    the error code to be used
     * @throws net.sf.saxon.trans.XPathException always
     */

    private void invalidJSON(String message, String code)
            throws XPathException {
        error("Invalid JSON input: " + message, code);
    }


    /**
     * Inner class to do the tokenization
     */

    private class JsonTokenizer {

        private String input;
        private int position;
        public int currentToken;
        public FastStringBuffer currentTokenValue = new FastStringBuffer(FastStringBuffer.C64);

        private final static int LSQB = 1;
        private final static int RSQB = 2;
        public static final int LCURLY = 3;
        public static final int RCURLY = 4;
        public static final int STRING_LITERAL = 5;
        public static final int NUMERIC_LITERAL = 6;
        public static final int TRUE = 7;
        public static final int FALSE = 8;
        public static final int NULL = 9;
        public static final int COLON = 10;
        public static final int COMMA = 11;
        public static final int EOF = 999;

        public JsonTokenizer(String input) {
            this.input = input;
            this.position = 0;
            // Ignore a leading BOM
            if (input.length() > 0 && input.charAt(0) == 65279) {
                position++;
            }
        }

        public int next() throws XPathException {
            currentToken = readToken();
            return currentToken;
        }

        private int readToken() throws XPathException {
            if (position >= input.length()) {
                return EOF;
            }
            while (Whitespace.isWhitespace(input.charAt(position))) {
                if (++position >= input.length()) {
                    return EOF;
                }
            }
            char ch = input.charAt(position++);
            switch (ch) {
                case '[':
                    return LSQB;
                case '{':
                    return LCURLY;
                case ']':
                    return RSQB;
                case '}':
                    return RCURLY;
                case '"':
                    currentTokenValue.setLength(0);
                    boolean afterBackslash = false;
                    while (true) {
                        char c = input.charAt(position++);
                        if (c < 32) {
                            invalidJSON("Unescaped control character (x" + Integer.toHexString(c) + ")", ERR_GRAMMAR);
                        }
                        if (afterBackslash && c == 'u') {
                            try {
                                String hex = input.substring(position, position + 4);
                                //noinspection ResultOfMethodCallIgnored
                                Integer.parseInt(hex, 16);
                            } catch (Exception e) {
                                invalidJSON("\\u must be followed by four hex characters", ERR_GRAMMAR);
                            }
                        }
                        if (c == '"' && !afterBackslash) {
                            break;
                        } else {
                            currentTokenValue.append(c);
                            afterBackslash = c == '\\' && !afterBackslash;
                        }
                        if (position >= input.length()) {
                            invalidJSON("Unclosed quotes in string literal", ERR_GRAMMAR);
                        }
                    }
                    return STRING_LITERAL;
                case ':':
                    return COLON;
                case ',':
                    return COMMA;
                case '-':
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    currentTokenValue.setLength(0);
                    currentTokenValue.append(ch);
                    if (position < input.length()) {   // We could be in ECMA mode when there is a single digit
                        while (true) {
                            char c = input.charAt(position);
                            if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') {
                                currentTokenValue.append(c);
                                if (++position >= input.length()) {
                                    break;
                                }
                            } else {
                                break;
                            }
                        }
                    }
                    return NUMERIC_LITERAL;
                case 't':
                case 'f':
                case 'n':
                    currentTokenValue.setLength(0);
                    currentTokenValue.append(ch);
                    while (true) {
                        char c = input.charAt(position);
                        if (c >= 'a' && c <= 'z') {
                            currentTokenValue.append(c);
                            if (++position >= input.length()) {
                                break;
                            }
                        } else {
                            break;
                        }
                    }
                    String val = currentTokenValue.toString();
                    if (val.equals("true")) {
                        return TRUE;
                    } else if (val.equals("false")) {
                        return FALSE;
                    } else if (val.equals("null")) {
                        return NULL;
                    } else {
                        error("Unknown constant " + currentTokenValue, ERR_GRAMMAR);
                    }
                default:
                    char c = input.charAt(--position);
                    invalidJSON("Unexpected character '" + c + "' (\\u" +
                            Integer.toHexString(c) + ") at position " + position, ERR_GRAMMAR);
                    return -1;
            }
        }
    }

}

// Copyright (c) 2017 Saxonica Limited.
