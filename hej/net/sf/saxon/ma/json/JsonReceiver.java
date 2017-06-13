////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.json;

import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.StartTagBuffer;
import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.NamespaceBindingSet;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;
import net.sf.saxon.type.StringConverter;
import net.sf.saxon.value.DoubleValue;
import net.sf.saxon.value.StringToDouble11;
import net.sf.saxon.value.Whitespace;
import net.sf.saxon.z.IntPredicate;

import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

/**
 * A Receiver which receives a stream of XML events using the vocabulary defined for the XML representation
 * of JSON in XSLT 3.0, and which generates the corresponding JSON text as a string
 */


public class JsonReceiver implements Receiver {

    private PipelineConfiguration pipe;
    private FastStringBuffer output;
    private FastStringBuffer textBuffer = new FastStringBuffer(128);
    private Stack<NodeName> stack = new Stack<NodeName>();
    private boolean atStart = true;
    private StartTagBuffer startTagBuffer;
    private boolean indenting = false;
    private boolean escaped = false;
    private Stack<Set<String>> keyChecker = new Stack<Set<String>>();

    private static final String ERR_INPUT = "FOJS0006";

    public JsonReceiver(PipelineConfiguration pipe) {
        setPipelineConfiguration(pipe);
    }

    public void setPipelineConfiguration(PipelineConfiguration pipe) {
        this.pipe = pipe;
        startTagBuffer = (StartTagBuffer) pipe.getComponent(StartTagBuffer.class.getName());
    }

    public PipelineConfiguration getPipelineConfiguration() {
        return pipe;
    }

    public void setSystemId(String systemId) {
        // no action
    }

    public void setIndenting(boolean indenting) {
        this.indenting = indenting;
    }

    public boolean isIndenting() {
        return indenting;
    }

    public void open() throws XPathException {
        output = new FastStringBuffer(2048);
    }

    public void startDocument(int properties) throws XPathException {
        if (output == null) {
            output = new FastStringBuffer(2048);
        }
    }

    public void endDocument() throws XPathException {
        // no action
    }

    public void setUnparsedEntity(String name, String systemID, String publicID) throws XPathException {
        // no action
    }

    public void startElement(NodeName elemName, SchemaType typeCode, Location location, int properties) throws XPathException {
        String parent = stack.empty() ? null : stack.peek().getLocalPart();
        boolean inMap = "map".equals(parent);
        stack.push(elemName);
        //started.push(false);
        if (!elemName.hasURI(NamespaceConstant.FN)) {
            throw new XPathException("xml-to-json: element found in wrong namespace: " +
                                             elemName.getStructuredQName().getEQName(), ERR_INPUT);
        }
        if (!atStart) {
            output.append(',');
            if (indenting) {
                indent(stack.size());
            }
        }
        if (inMap) {
            if (startTagBuffer == null) {
                startTagBuffer = (StartTagBuffer) pipe.getComponent(StartTagBuffer.class.getName());
            }
            if (startTagBuffer == null) {
                throw new IllegalStateException();
            }
            String key = startTagBuffer.getAttribute("", "key");
            if (key == null) {
                throw new XPathException("xml-to-json: Child elements of <map> must have a key attribute", ERR_INPUT);
            }
            String keyEscaped = startTagBuffer.getAttribute("", "escaped-key");
            boolean alreadyEscaped = false;
            if (keyEscaped != null) {
                try {
                    alreadyEscaped = StringConverter.STRING_TO_BOOLEAN.convertString(keyEscaped).asAtomic().effectiveBooleanValue();
                } catch (XPathException e) {
                    throw new XPathException("xml-to-json: Value of escaped-key attribute '" + Err.wrap(keyEscaped) +
                                                     "' is not a valid xs:boolean", ERR_INPUT);
                }
            }
            key = (alreadyEscaped ? handleEscapedString(key) : escape(key, false, new ControlChar())).toString();

            String normalizedKey = alreadyEscaped ? unescape(key) : key;
            boolean added = keyChecker.peek().add(normalizedKey);
            if (!added) {
                throw new XPathException("xml-to-json: duplicate key value " + Err.wrap(key), ERR_INPUT);
            }

            output.append('"');
            output.append(key);
            output.append('"');
            output.append(indenting ? " : " : ":");
        }
        String local = elemName.getLocalPart();
        escaped = false;
        if (local.equals("array")) {
            if (indenting) {
                indent(stack.size());
                output.append("[ ");
            } else {
                output.append('[');
            }
            atStart = true;
        } else if (local.equals("map")) {
            if (indenting) {
                indent(stack.size());
                output.append("{ ");
            } else {
                output.append('{');
            }
            atStart = true;
            keyChecker.push(new HashSet<String>());
        } else if (local.equals("null")) {
            checkParent(local, parent);
            output.append("null");
            atStart = false;
        } else if (local.equals("string")) {
            if (startTagBuffer == null) {
                startTagBuffer = (StartTagBuffer) pipe.getComponent(StartTagBuffer.class.getName());
            }
            String escapeAtt = startTagBuffer.getAttribute("", "escaped");
            if (escapeAtt != null) {
                try {
                    escaped = StringConverter.STRING_TO_BOOLEAN.convertString(escapeAtt).asAtomic().effectiveBooleanValue();
                } catch (XPathException e) {
                    throw new XPathException("xml-to-json: value of escaped attribute (" +
                                                     escapeAtt + ") is not a valid xs:boolean", ERR_INPUT);
                }
            }
            checkParent(local, parent);
            atStart = false;
        } else if (local.equals("boolean") || local.equals("number")) {
            checkParent(local, parent);
            atStart = false;
        } else {
            throw new XPathException("xml-to-json: unknown element <" + local + ">", ERR_INPUT);
        }
        textBuffer.setLength(0);
    }

    private void checkParent(String child, String parent) throws XPathException {
        if ("null".equals(parent) || "string".equals(parent) || "number".equals(parent) || "boolean".equals(parent)) {
            throw new XPathException("xml-to-json: A " + Err.wrap(child, Err.ELEMENT) +
                                             " element cannot appear as a child of " + Err.wrap(parent, Err.ELEMENT), ERR_INPUT);
        }
    }

    public void namespace(NamespaceBindingSet namespaceBindings, int properties) throws XPathException {
        // no action
    }

    public void attribute(NodeName attName, SimpleType typeCode, CharSequence value, Location locationId, int properties) throws XPathException {
        if (attName.hasURI("") && (attName.getLocalPart().equals("key") || attName.getLocalPart().equals("escaped-key"))) {
            boolean inMap = stack.size() == 1 || stack.get(stack.size() - 2).getLocalPart().equals("map");
            if (!inMap) {
                throw new XPathException(
                        "xml-to-json: The " + attName.getLocalPart() +
                                " attribute is allowed only on elements within a map", ERR_INPUT);
            }
        } else if (attName.hasURI("") && attName.getLocalPart().equals("escaped")) {
            boolean allowed = stack.size() == 1 ||
                    (stack.size() > 1 && stack.peek().getLocalPart().equals("string"));
            // See bugs 29917 and 30077: at the top level, the escaped attribute is ignored
            // whatever element it appears on
            if (!allowed) {
                throw new XPathException(
                        "xml-to-json: The escaped attribute is allowed only on the <string> element",
                        ERR_INPUT);
            }
        } else if (attName.hasURI("") || attName.hasURI(NamespaceConstant.FN)) {
            throw new XPathException("xml-to-json: Disallowed attribute in input: " + attName.getDisplayName(), ERR_INPUT);
        }
        // Attributes in other namespaces are ignored
    }

    public void startContent() throws XPathException {
        // no action
    }

    public void endElement() throws XPathException {
        NodeName name = stack.pop();
        String local = name.getLocalPart();
        if (local.equals("boolean")) {
            try {
                boolean b = StringConverter.STRING_TO_BOOLEAN.convertString(textBuffer).asAtomic().effectiveBooleanValue();
                output.append(b ? "true" : "false");
            } catch (XPathException e) {
                throw new XPathException("xml-to-json: Value of <boolean> element is not a valid xs:boolean", ERR_INPUT);
            }
        } else if (local.equals("number")) {
            try {
                double d = StringToDouble11.getInstance().stringToNumber(textBuffer);
                if (Double.isNaN(d) || Double.isInfinite(d)) {
                    throw new XPathException("xml-to-json: Infinity and NaN are not allowed", ERR_INPUT);
                }
                output.append(new DoubleValue(d).getStringValueCS());
            } catch (NumberFormatException e) {
                throw new XPathException("xml-to-json: Invalid number: " + textBuffer, ERR_INPUT);
            }
        } else if (local.equals("string")) {
            output.append('"');
            String str = textBuffer.toString();
            if (escaped) {
                output.append(handleEscapedString(str));
            } else {
                output.append(escape(str, false, new ControlChar()));
            }
            output.append('"');
        } else if (!Whitespace.isWhite(textBuffer)) {
            throw new XPathException("xml-to-json: Element " + name.getDisplayName() + " must have no text content", ERR_INPUT);
        }
        textBuffer.setLength(0);
        escaped = false;
        if (local.equals("array")) {
            output.append(indenting ? " ]" : "]");
        } else if (local.equals("map")) {
            keyChecker.pop();
            output.append(indenting ? " }" : "}");
        }
        atStart = false;
    }

    private static CharSequence handleEscapedString(String str) throws XPathException {
        // check that escape sequences are valid
        unescape(str);
        FastStringBuffer out = new FastStringBuffer(str.length() * 2);
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '"' && (i == 0 || str.charAt(i - 1) != '\\')) {
                out.append("\\\"");
            } else if (c < 32 || (c >= 127 && c < 160)) {
                if (c == '\b') {
                    out.append("\\b");
                } else if (c == '\f') {
                    out.append("\\f");
                } else if (c == '\n') {
                    out.append("\\n");
                } else if (c == '\r') {
                    out.append("\\r");
                } else if (c == '\t') {
                    out.append("\\t");
                } else {
                    out.append("\\u");
                    String hex = Integer.toHexString(c).toUpperCase();
                    while (hex.length() < 4) {
                        hex = "0" + hex;
                    }
                    out.append(hex);
                }
            } else if (c == '/') {
                out.append("\\/");
            } else {
                out.append(c);
            }
        }
        return out;
    }

    /**
     * Escape a string using backslash escape sequences as defined in JSON
     *
     * @param in         the input string
     * @param forXml     true if the output is for the json-to-xml functino
     * @param hexEscapes a predicate identifying characters that should be output as hex escapes using \ u XXXX notation.
     * @return the escaped string
     */

    public static CharSequence escape(CharSequence in, boolean forXml, IntPredicate hexEscapes) throws XPathException {
        FastStringBuffer out = new FastStringBuffer(in.length());
        for (int i = 0; i < in.length(); i++) {
            char c = in.charAt(i);
            switch (c) {
                case '"':
                    out.append(forXml ? "\"" : "\\\"");
                    break;
                case '\b':
                    out.append("\\b");
                    break;
                case '\f':
                    out.append("\\f");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                case '/':
                    out.append(forXml ? "/" : "\\/");  // spec bug 29665, saxon bug 2849
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                default:
                    if (hexEscapes.matches(c)) {
                        out.append("\\u");
                        String hex = Integer.toHexString(c).toUpperCase();
                        while (hex.length() < 4) {
                            hex = "0" + hex;
                        }
                        out.append(hex);
                    } else {
                        out.append(c);
                    }
            }
        }
        return out;
    }

    private static class ControlChar implements IntPredicate {
        public boolean matches(int c) {
            return c < 31 || (c >= 127 && c <= 159);
        }
    }

    public void characters(CharSequence chars, Location locationId, int properties) throws XPathException {
        textBuffer.append(chars);
    }

    public void processingInstruction(String name, CharSequence data, Location locationId, int properties) throws XPathException {
        // no action
    }

    public void comment(CharSequence content, Location locationId, int properties) throws XPathException {
        // no action
    }

    public void close() throws XPathException {
        // no action
    }

    public boolean usesTypeAnnotations() {
        return false;
    }

    public String getSystemId() {
        return null;
    }

    /**
     * On completion, get the assembled JSON string
     *
     * @return the JSON string representing the supplied XML content.
     */

    public String getJsonString() {
        return output.toString();
    }

    /**
     * Add indentation whitespace to the buffer
     *
     * @param depth the level of indentation
     */

    private void indent(int depth) {
        output.append('\n');
        for (int i = 0; i < depth; i++) {
            output.append("  ");
        }
    }

    /**
     * Unescape a JSON string literal,
     *
     * @param literal the string literal to be processed
     * @return the result of expanding escape sequences
     * @throws net.sf.saxon.trans.XPathException if the input contains invalid escape sequences
     */

    private static String unescape(String literal) throws XPathException {
        if (literal.indexOf('\\') < 0) {
            return literal;
        }
        FastStringBuffer buffer = new FastStringBuffer(literal.length());
        for (int i = 0; i < literal.length(); i++) {
            char c = literal.charAt(i);
            if (c == '\\') {
                if (i++ == literal.length() - 1) {
                    throw new XPathException("String '" + Err.wrap(literal) + "' ends in backslash ", "FOJS0007");
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
                            throw new XPathException("Invalid hex escape sequence in string '" + Err.wrap(literal) + "'", "FOJS0007");
                        }
                        break;
                    default:
                        throw new XPathException("Unknown escape sequence \\" + literal.charAt(i), "FOJS0007");
                }
            } else {
                buffer.append(c);
            }
        }
        return buffer.toString();
    }
}

// Copyright (c) 2017 Saxonica Limited. All rights reserved