////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trace;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.CheckSumFilter;
import net.sf.saxon.event.NamespaceReducer;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.parser.ExplicitLocation;
import net.sf.saxon.expr.parser.RetainedStaticContext;
import net.sf.saxon.lib.Logger;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.SaxonOutputKeys;
import net.sf.saxon.om.*;
import net.sf.saxon.serialize.charcode.UTF16CharacterSet;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.type.Untyped;
import net.sf.saxon.value.Whitespace;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.stream.StreamResult;
import java.util.*;

/**
 * This class handles the display of an abstract expression tree in an XML format
 * with some slight resemblance to XQueryX
 */
public class ExpressionPresenter {

    private Configuration config;
    private Receiver receiver;
    private int depth = 0;
    private boolean inStartTag = false;
    private String nextRole = null;
    private Stack<Expression> expressionStack = new Stack<Expression>();
    private Stack<String> nameStack = new Stack<String>();
    private String defaultNamespace;
    private Map<String, String> options = new HashMap<String, String>();
    private boolean relocatable = false;

    /**
     * Make an uncommitted ExpressionPresenter. This must be followed by a call on init()
     */

    public ExpressionPresenter() {}

    /**
     * Make an ExpressionPresenter that writes indented output to the standard error output
     * destination of the Configuration
     *
     * @param config the Saxon configuration
     */

    public ExpressionPresenter(/*@NotNull*/ Configuration config) {
        this(config, config.getLogger());
    }

    /**
     * Make an ExpressionPresenter that writes indented output to a specified output stream
     *
     * @param config the Saxon configuration
     * @param out    the output destination
     */

    public ExpressionPresenter(Configuration config, StreamResult out) {
        this(config, out, false);
    }

    /**
     * Make an ExpressionPresenter that writes indented output to a specified output stream,
     * with checksumming
     *
     * @param config   the Saxon configuration
     * @param out      the output destination
     * @param checksum true if a checksum is to be written at the end of the file
     */

    public ExpressionPresenter(Configuration config, StreamResult out, boolean checksum) {
        init(config, out, checksum);
    }

    /**
     * Make an ExpressionPresenter that writes indented output to a specified output stream,
     * with checksumming
     *
     * @param config   the Saxon configuration
     * @param out      the output destination
     * @param checksum true if a checksum is to be written at the end of the file
     */

    public void init(Configuration config, StreamResult out, boolean checksum) {
        Properties props = makeDefaultProperties(config);
        if (config.getXMLVersion() == Configuration.XML11) {
            if ("JS".equals(getOption("target"))) {
                config.getLogger().warning("For target=JS, the SEF file will use XML 1.0, which disallows control characters");
            } else {
                props.setProperty(OutputKeys.VERSION, "1.1");
            }
        }
        try {
            receiver = config.getSerializerFactory().getReceiver(
                    out,
                    config.makePipelineConfiguration(),
                    props);
            receiver = new NamespaceReducer(receiver);
            if (checksum) {
                receiver = new CheckSumFilter(receiver);
            }
        } catch (XPathException err) {
            err.printStackTrace();
            throw new InternalError(err.getMessage());
        }
        this.config = config;
        try {
            receiver.open();
            receiver.startDocument(0);
        } catch (XPathException err) {
            err.printStackTrace();
            throw new InternalError(err.getMessage());
        }
    }


    /**
     * Make an ExpressionPresenter that writes indented output to a specified output stream
     *
     * @param config the Saxon configuration
     * @param out    the output stream
     */

    public ExpressionPresenter(Configuration config, Logger out) {
        this(config, out.asStreamResult());
    }

    /**
     * Make an ExpressionPresenter for a given Configuration using a user-supplied Receiver
     * to accept the output
     *
     * @param config   the Configuration
     * @param receiver the user-supplied Receiver
     */

    public ExpressionPresenter(Configuration config, /*@NotNull*/ Receiver receiver) {
        this.config = config;
        this.receiver = receiver;
        try {
            receiver.open();
            receiver.startDocument(0);
        } catch (XPathException err) {
            err.printStackTrace();
            throw new InternalError(err.getMessage());
        }
    }

    /**
     * Set the default namespace, used for subsequent calls on startElement.
     * Must be consistent throughout the whole document
     *
     * @param namespace the default namespace
     */

    public void setDefaultNamespace(String namespace) {
        defaultNamespace = namespace;
    }

    /**
     * Set an option (a keyword=value pair, where both are strings)
     *
     * @param key   the option name
     * @param value the option value
     */

    public void setOption(String key, String value) {
        options.put(key, value);
    }

    /**
     * Get the value of an option
     *
     * @param key the option name
     * @return the option value, or null if the option has not been set
     */

    public String getOption(String key) {
        return options.get(key);
    }

    /**
     * Ask whether the package can be deployed to a different location, with a different base URI
     *
     * @return if true then static-base-uri() represents the deployed location of the package,
     * rather than its compile time location
     */

    public boolean isRelocatable() {
        return relocatable;
    }

    /**
     * Say whether the package can be deployed to a different location, with a different base URI
     *
     * @param relocatable if true then static-base-uri() represents the deployed location of the package,
     *                    rather than its compile time location
     */

    public void setRelocatable(boolean relocatable) {
        this.relocatable = relocatable;
    }


    /**
     * Make a receiver, using default output properties, with serialized output going
     * to a specified OutputStream
     *
     * @param config the Configuration
     * @param out    the OutputStream
     * @return a Receiver that directs serialized output to this output stream
     * @throws XPathException
     */

    /*@Nullable*/
    public static Receiver defaultDestination(/*@NotNull*/ Configuration config, Logger out) throws XPathException {
        Properties props = makeDefaultProperties(config);
        return config.getSerializerFactory().getReceiver(
                out.asStreamResult(),
                config.makePipelineConfiguration(),
                props);
    }


    /**
     * Make a Properties object containing defaulted serialization attributes for the expression tree
     *
     * @return a default set of properties
     */

    /*@NotNull*/
    public static Properties makeDefaultProperties(Configuration config) {
        Properties props = new Properties();
        props.setProperty(OutputKeys.METHOD, "xml");
        props.setProperty(OutputKeys.INDENT, "yes");
        props.setProperty(SaxonOutputKeys.INDENT_SPACES, "1");
        props.setProperty(SaxonOutputKeys.LINE_LENGTH, "4096");
        props.setProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        props.setProperty(OutputKeys.ENCODING, "utf-8");
        props.setProperty(OutputKeys.VERSION, "1.0");
        return props;
    }

    /**
     * Start an element representing an expression in the expression tree
     *
     * @param name the name of the element
     * @param expr the expression represented
     * @return the depth of the tree before this element: for diagnostics, this can be compared
     * with the value returned by endElement
     */

    public int startElement(String name, Expression expr) {
        Expression parent = expressionStack.isEmpty() ? null : expressionStack.peek();
        expressionStack.push(expr);
        nameStack.push("*" + name);
        int n = _startElement(name);
        if (parent == null || expr.getRetainedStaticContext() != parent.getRetainedStaticContext()) {
            if (expr.getRetainedStaticContext() == null) {
                throw new AssertionError("Export failure: no retained static context on " + expr.toShortString());
            } else {
                emitRetainedStaticContext(expr.getRetainedStaticContext(), parent == null ? null : parent.getRetainedStaticContext());
            }
        }
        String mod = expr.getLocation().getSystemId();
        if (mod != null && parent != null && (parent.getLocation().getSystemId() == null || !parent.getLocation().getSystemId().equals(mod))) {
            emitAttribute("module", truncatedModuleName(mod));
        }
        int lineNr = expr.getLocation().getLineNumber();
        if (parent == null || (parent.getLocation().getLineNumber() != lineNr && lineNr != -1)) {
            emitAttribute("line", lineNr + "");
        }
        return n;
    }

    private String truncatedModuleName(String module) {
        if (!relocatable) {
            return module;
        } else {
            // If not exporting the base URI, cut the filename used for diagnostic location of errors down to its last component
            String parts[] = module.split("/");
            for (int p = parts.length - 1; p >= 0; p--) {
                if (!parts[p].isEmpty()) {
                    return parts[p];
                }
            }
            return module;
        }
    }

    public void emitRetainedStaticContext(RetainedStaticContext sc, RetainedStaticContext parentSC) {
        try {
            if (!relocatable && sc.getStaticBaseUri() != null && (parentSC == null || !sc.getStaticBaseUri().equals(parentSC.getStaticBaseUri()))) {
                emitAttribute("baseUri", sc.getStaticBaseUriString());
            }
            if (!sc.getDefaultCollationName().equals(NamespaceConstant.CODEPOINT_COLLATION_URI) &&
                    (parentSC == null || !sc.getDefaultCollationName().equals(parentSC.getDefaultCollationName()))) {
                emitAttribute("defaultCollation", sc.getDefaultCollationName());
            }
            if (!sc.getDefaultElementNamespace().isEmpty() &&
                    (parentSC == null || !sc.getDefaultElementNamespace().equals(parentSC.getDefaultElementNamespace()))) {
                emitAttribute("defaultElementNS", sc.getDefaultElementNamespace());
            }
            if (!NamespaceConstant.FN.equals(sc.getDefaultFunctionNamespace())) {
                emitAttribute("defaultFunctionNS", sc.getDefaultFunctionNamespace());
            }
            if (parentSC == null || !sc.declaresSameNamespaces(parentSC)) {
                FastStringBuffer fsb = new FastStringBuffer(FastStringBuffer.C256);
                for (Iterator<String> iter = sc.iteratePrefixes(); iter.hasNext(); ) {
                    String p = iter.next();
                    fsb.append(p);
                    fsb.append("=");
                    String uri = sc.getURIForPrefix(p, true);
                    if (Whitespace.containsWhitespace(uri)) {
                        throw new XPathException("Cannot export a stylesheet if namespaces contain whitespace: '" + uri + "'");
                    }
                    if (uri.equals(NamespaceConstant.getUriForConventionalPrefix(p))) {
                        uri = "~";
                    }
                    fsb.append(uri);
                    fsb.append(" ");
                }
                emitAttribute("ns", Whitespace.trim(fsb));
            }
        } catch (XPathException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Start an element
     *
     * @param name the name of the element
     * @return the depth of the tree before this element: for diagnostics, this can be compared
     * with the value returned by endElement
     */

    public int startElement(String name) {
        nameStack.push(name);
        return _startElement(name);
    }

    private int _startElement(String name) {
        //System.err.println("start " + name + " at " + new XPathException("").getStackTrace().length);
        try {
            if (inStartTag) {
                receiver.startContent();
                inStartTag = false;
            }
            NodeName nodeName;
            if (defaultNamespace == null) {
                nodeName = new NoNamespaceName(name);
            } else {
                nodeName = new FingerprintedQName("", defaultNamespace, name);
            }
            receiver.startElement(nodeName, Untyped.getInstance(), ExplicitLocation.UNKNOWN_LOCATION, 0);
            if (nextRole != null) {
                emitAttribute("role", nextRole);
                nextRole = null;
            }
        } catch (XPathException err) {
            err.printStackTrace();
            throw new InternalError(err.getMessage());
        }
        inStartTag = true;
        return depth++;
    }

    /**
     * Set the role of the next element to be output
     *
     * @param role the value of the role output to be used
     */

    public void setChildRole(String role) {
        nextRole = role;
    }

    /**
     * Output an attribute node
     *
     * @param name  the name of the attribute
     * @param value the value of the attribute
     */

    public void emitAttribute(String name, String value) {
        if (value != null) {
            if (name.equals("module")) {
                value = truncatedModuleName(value);
            }
            try {
                receiver.attribute(new NoNamespaceName(name), BuiltInAtomicType.UNTYPED_ATOMIC, value, ExplicitLocation.UNKNOWN_LOCATION, 0);
            } catch (XPathException err) {
                err.printStackTrace();
                throw new InternalError(err.getMessage());
            }
        }
    }

    /**
     * Output a QName-valued attribute node
     *
     * @param name  the name of the attribute
     * @param value the value of the attribute
     */

    public void emitAttribute(String name, StructuredQName value) {
        try {
            if (value.getURI().isEmpty()) {
                receiver.attribute(new NoNamespaceName(name), BuiltInAtomicType.UNTYPED_ATOMIC, value.getLocalPart(), ExplicitLocation.UNKNOWN_LOCATION, 0);
            } else if (value.getPrefix().isEmpty()) {
                String prefix = config.getNamePool().suggestPrefixForURI(value.getURI());
                if (prefix == null) {
                    prefix = allocatePrefix(value.getURI());
                }
                receiver.namespace(new NamespaceBinding(prefix, value.getURI()), 0);
                receiver.attribute(new NoNamespaceName(name),
                                   BuiltInAtomicType.UNTYPED_ATOMIC, prefix + ":" + value.getLocalPart(), ExplicitLocation.UNKNOWN_LOCATION, 0);
            } else {
                receiver.namespace(new NamespaceBinding(value.getPrefix(), value.getURI()), 0);
                receiver.attribute(new NoNamespaceName(name),
                                   BuiltInAtomicType.UNTYPED_ATOMIC, value.getDisplayName(), ExplicitLocation.UNKNOWN_LOCATION, 0);
            }
        } catch (XPathException err) {
            err.printStackTrace();
            throw new InternalError(err.getMessage());
        }
    }

    private String allocatePrefix(String uri) {
        if (uri.equals(NamespaceConstant.SCHEMA)) {
            return "xs";
        } else if (uri.equals(NamespaceConstant.FN)) {
            return "fn";
        } else if (uri.equals(NamespaceConstant.SAXON)) {
            return "saxon";
        } else {
            int c = uri.lastIndexOf('/');
            if (c == uri.length() - 1) {
                c = uri.substring(0, uri.length() - 1).lastIndexOf('/');
            }
            if (c >= 0 && c + 3 < uri.length()) {
                String prefix = uri.substring(c + 1, c + 3);
                if (NameChecker.isValidNCName(prefix)) {
                    return prefix;
                }
            }
            return "p";
        }
    }

    /**
     * Output a namespace declaration
     *
     * @param prefix the namespace prefix
     * @param uri    the namespace URI
     */

    public void namespace(String prefix, String uri) {
        try {
            receiver.namespace(new NamespaceBinding(prefix, uri), 0);
        } catch (XPathException e) {
            e.printStackTrace();
            throw new InternalError(e.getMessage());
        }
    }

    /**
     * End an element in the expression tree
     *
     * @return the depth of the tree after ending this element. For diagnostics, this can be compared with the
     * value returned by startElement()
     */

    public int endElement() {
        //System.err.println("end at " + new XPathException("").getStackTrace().length);
        try {
            if (inStartTag) {
                receiver.startContent();
                inStartTag = false;
            }
            receiver.endElement();
        } catch (XPathException err) {
            err.printStackTrace();
            throw new InternalError(err.getMessage());
        }
        String name = nameStack.pop();
        if (name.startsWith("*")) {
            expressionStack.pop();
        }
        return --depth;
    }

    /**
     * Start a child element in the output
     *
     * @param name the name of the child element
     */

    public void startSubsidiaryElement(String name) {
        startElement(name);
    }

    /**
     * End a child element in the output
     */

    public void endSubsidiaryElement() {
        endElement();
    }

    /**
     * Close the output
     */

    public void close() {
        try {
            if (receiver instanceof CheckSumFilter) {
                int c = ((CheckSumFilter) receiver).getChecksum();
                receiver.processingInstruction(CheckSumFilter.SIGMA, Integer.toHexString(c), ExplicitLocation.UNKNOWN_LOCATION, 0);
            }
            receiver.endDocument();
            receiver.close();
        } catch (XPathException err) {
            err.printStackTrace();
            throw new InternalError(err.getMessage());
        }
    }


    /**
     * Get the Saxon configuration
     *
     * @return the Saxon configuration
     */

    public Configuration getConfiguration() {
        return config;
    }

    /**
     * Get the name pool
     *
     * @return the name pool
     */

    public NamePool getNamePool() {
        return config.getNamePool();
    }

    /**
     * Get the type hierarchy cache
     *
     * @return the type hierarchy cache
     */

    public TypeHierarchy getTypeHierarchy() {
        return config.getTypeHierarchy();
    }

    /**
     * Static method to escape a string using Javascript escaping conventions
     * @param in the string to be escaped
     */

    public static String jsEscape(String in) {
        FastStringBuffer out = new FastStringBuffer(in.length());
        for (int i = 0; i < in.length(); i++) {
            char c = in.charAt(i);
            switch (c) {
                case '\'':
                    out.append("\\\'");
                    break;
                case '"':
                    out.append("\\\"");
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
                case '\\':
                    out.append("\\\\");
                    break;
                default:
                    if (c < 32 || (c > 127 && c < 160) || c > UTF16CharacterSet.SURROGATE1_MIN) {
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
        return out.toString();
    }
}

