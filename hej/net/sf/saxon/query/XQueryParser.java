////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.query;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.flwor.*;
import net.sf.saxon.expr.instruct.*;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.expr.sort.GenericAtomicComparer;
import net.sf.saxon.expr.sort.SortKeyDefinition;
import net.sf.saxon.functions.*;
import net.sf.saxon.functions.registry.BuiltInFunctionSet;
import net.sf.saxon.functions.registry.ConstructorFunctionLibrary;
import net.sf.saxon.lib.*;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.AnyNodeTest;
import net.sf.saxon.pattern.QNameTest;
import net.sf.saxon.pattern.UnionQNameTest;
import net.sf.saxon.serialize.CharacterMap;
import net.sf.saxon.serialize.CharacterMapIndex;
import net.sf.saxon.serialize.SerializationParamsHandler;
import net.sf.saxon.serialize.charcode.UTF16CharacterSet;
import net.sf.saxon.style.AttributeValueTemplate;
import net.sf.saxon.trace.LocationKind;
import net.sf.saxon.trans.DecimalFormatManager;
import net.sf.saxon.trans.DecimalSymbols;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.tree.util.NamespaceResolverWithDefault;
import net.sf.saxon.type.*;
import net.sf.saxon.value.*;
import net.sf.saxon.z.IntHashSet;
import net.sf.saxon.z.IntPredicate;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * This class defines extensions to the XPath parser to handle the additional
 * syntax supported in XQuery
 */
public class XQueryParser extends XPathParser {

    public final static String XQUERY10 = "1.0";
    public final static String XQUERY30 = "3.0";
    public final static String XQUERY31 = "3.1";

    private boolean memoFunction = false;
    private boolean disableCycleChecks = false;
    private boolean streaming = false;
    //protected String queryVersion = null;
    private int errorCount = 0;
    /*@Nullable*/ private XPathException firstError = null;

    /*@NotNull*/ protected Executable executable;

    private boolean foundCopyNamespaces = false;
    private boolean foundBoundarySpaceDeclaration = false;
    private boolean foundOrderingDeclaration = false;
    private boolean foundEmptyOrderingDeclaration = false;
    private boolean foundDefaultCollation = false;
    private boolean foundConstructionDeclaration = false;
    private boolean foundDefaultFunctionNamespace = false;
    private boolean foundDefaultElementNamespace = false;
    private boolean foundBaseURIDeclaration = false;
    private boolean foundContextItemDeclaration = false;
    private boolean foundDefaultDecimalFormat = false;
    private boolean preambleProcessed = false;

    /*@NotNull*/ public Set importedModules = new HashSet(5);
    /*@NotNull*/ List<String> namespacesToBeSealed = new ArrayList<String>(10);
    /*@NotNull*/ List<Import> schemaImports = new ArrayList<Import>(5);
    /*@NotNull*/ List<Import> moduleImports = new ArrayList<Import>(5);

    private Set<StructuredQName> outputPropertiesSeen = new HashSet<StructuredQName>(4);


    /**
     * Constructor for internal use: this class should be instantiated via the QueryModule
     */

    public XQueryParser() {
        //queryVersion = XQUERY31;
        setLanguage(XQUERY, 31);
    }

    /**
     * Create a new parser of the same kind
     *
     * @return a new parser of the same kind as this one
     */

    public XQueryParser newParser() {
        XQueryParser qp = new XQueryParser();
        qp.setLanguage(language, 31);
        qp.setParserExtension(parserExtension);
        return qp;
    }

    /**
     * Create an XQueryExpression
     *
     * @param query      the source text of the query
     * @param mainModule the static context of the query
     * @param config     the Saxon configuration
     * @return the compiled XQuery expression
     * @throws XPathException if the expression contains static errors
     */

    /*@NotNull*/
    public XQueryExpression makeXQueryExpression(
            /*@NotNull*/ String query,
            /*@NotNull*/ QueryModule mainModule,
            /*@NotNull*/ Configuration config) throws XPathException {
        try {
            setLanguage(XQUERY, 31);
            if (config.getXMLVersion() == Configuration.XML10) {
                query = normalizeLineEndings10(query);
            } else {
                query = normalizeLineEndings11(query);
            }
            Executable exec = mainModule.getExecutable();
            if (exec == null) {
                exec = new Executable(config);
                exec.setHostLanguage(Configuration.XQUERY);
                exec.setTopLevelPackage(mainModule.getPackageData());
                setExecutable(exec);
                //mainModule.setExecutable(exec);
            }

//            setDefaultContainer(new SimpleContainer(mainModule.getPackageData()));

            Properties outputProps = new Properties(config.getDefaultSerializationProperties());
            if (outputProps.getProperty(OutputKeys.METHOD) == null) {
                outputProps.setProperty(OutputKeys.METHOD, "xml");
            }
            exec.setDefaultOutputProperties(outputProps);

            //exec.setLocationMap(new LocationMap());
            FunctionLibraryList libList = new FunctionLibraryList();
            libList.addFunctionLibrary(new ExecutableFunctionLibrary(config));
            exec.setFunctionLibrary(libList);
            // this will be changed later
            setExecutable(exec);

            setCodeInjector(mainModule.getCodeInjector());

            Expression exp = parseQuery(query, 0, Token.EOF, mainModule);
            if (streaming) {
                env.getConfiguration().checkLicensedFeature(Configuration.LicenseFeature.ENTERPRISE_XQUERY, "streaming", -1);
            }

            exec.fixupQueryModules(mainModule);

            // Check for cyclic dependencies among the modules

//            if (!disableCycleChecks) {
//                Iterator miter = exec.getQueryLibraryModules();
//                while (miter.hasNext()) {
//                    QueryModule module = (QueryModule)miter.next();
//                    module.lookForModuleCycles(new Stack(), 1);
//                }
//            }

            // Make the XQueryExpression object

            XQueryExpression queryExp = config.makeXQueryExpression(exp, mainModule, streaming);

            // Make the function library that's available at run-time (e.g. for saxon:evaluate() and function-lookup()).
            // This includes all user-defined functions regardless of which module they are in

            FunctionLibrary userlib = exec.getFunctionLibrary();
            FunctionLibraryList lib = new FunctionLibraryList();
            lib.addFunctionLibrary(mainModule.getBuiltInFunctionSet());
            lib.addFunctionLibrary(config.getBuiltInExtensionLibraryList());
            lib.addFunctionLibrary(new ConstructorFunctionLibrary(config));
            lib.addFunctionLibrary(config.getIntegratedFunctionLibrary());
            lib.addFunctionLibrary(mainModule.getGlobalFunctionLibrary());
            config.addExtensionBinders(lib);
            lib.addFunctionLibrary(userlib);
            exec.setFunctionLibrary(lib);

            return queryExp;
        } catch (XPathException e) {
            if (!e.hasBeenReported()) {
                reportError(e);
            }
            throw e;
        }
    }

    /**
     * Get the permitted set of standard functions in this environment
     *
     * @return a code indicating which system library functions are supported in this version of the language
     */

    public int getPermittedFunctions() {
        return BuiltInFunctionSet.CORE | BuiltInFunctionSet.XPATH30;
    }

    /**
     * Check that an expression is streamable
     * @param exp the expression to be checked
     * @param contextInfo information about the context item
     * @throws XPathException if the expression is not streamable
     */

    public void checkStreamability(Expression exp, ContextItemStaticInfo contextInfo) throws XPathException {
        throw new XPathException("Streaming requires Saxon-EE");
    }

    /**
     * Normalize line endings in the source query, according to the XML 1.1 rules.
     *
     * @param in the input query
     * @return the query with line endings normalized
     */

    private static String normalizeLineEndings11(/*@NotNull*/ String in) {
        if (in.indexOf((char) 0xd) < 0 && in.indexOf((char) 0x85) < 0 && in.indexOf((char) 0x2028) < 0) {
            return in;
        }
        FastStringBuffer sb = new FastStringBuffer(in.length());
        for (int i = 0; i < in.length(); i++) {
            char ch = in.charAt(i);
            switch (ch) {
                case 0x85:
                case 0x2028:
                    sb.append((char) 0xa);
                    break;
                case 0xd:
                    if (i < in.length() - 1 && (in.charAt(i + 1) == (char) 0xa || in.charAt(i + 1) == (char) 0x85)) {
                        sb.append((char) 0xa);
                        i++;
                    } else {
                        sb.append((char) 0xa);
                    }
                    break;
                default:
                    sb.append(ch);
            }
        }
        return sb.toString();
    }

    /**
     * Normalize line endings in the source query, according to the XML 1.0 rules.
     *
     * @param in the input query
     * @return the query text with line endings normalized
     */

    private static String normalizeLineEndings10(/*@NotNull*/ String in) {
        if (in.indexOf((char) 0xd) < 0) {
            return in;
        }
        FastStringBuffer sb = new FastStringBuffer(in.length());
        for (int i = 0; i < in.length(); i++) {
            char ch = in.charAt(i);
            switch (ch) {
                case 0xd:
                    if (i < in.length() - 1 && in.charAt(i + 1) == (char) 0xa) {
                        sb.append((char) 0xa);
                        i++;
                    } else {
                        sb.append((char) 0xa);
                    }
                    break;
                default:
                    sb.append(ch);
            }
        }
        return sb.toString();
    }


    /**
     * Get the executable containing this expression.
     *
     * @return the executable
     */

    /*@NotNull*/
    public Executable getExecutable() {
        return executable;
    }

    /**
     * Set the executable used for this query expression
     *
     * @param exec the executable
     */

    public void setExecutable(/*@NotNull*/ Executable exec) {
        executable = exec;
    }

    /**
     * Disable checks for certain kinds of cycle. This is equivalent to
     * <p><code>declare option saxon:allow-cycles "true"</code></p>
     *
     * @param disable true if checks for import cycles are to be suppressed, that is,
     *                if cycles should be allowed
     * @deprecated in 9.8, the XQUery 3.0+ rules for cycles are always used, and the
     * old 1.0 cycle checks never happen.
     */

    public void setDisableCycleChecks(boolean disable) {
        disableCycleChecks = disable;
    }

    /**
     * Callback to tailor the tokenizer
     */

    protected void customizeTokenizer(Tokenizer t) {
        t.isXQuery = true;
    }


    /**
     * Say whether the query should be compiled and evaluated to use streaming.
     * This affects subsequent calls on the parseQuery() method. This option requires
     * Saxon-EE.
     * @param option if true, the compiler will attempt to compile a query to be
     * capable of executing in streaming mode. If the query cannot be streamed,
     * a compile-time exception is reported. In streaming mode, the source
     * document is supplied as a stream, and no tree is built in memory. The default
     * is false.
     * @since 9.6
     */

    public void setStreaming(boolean option) {
        streaming = option;
    }

    /**
     * Ask whether the streaming option has been set, that is, whether
     * subsequent calls on parseQuery() will compile queries to be capable
     * of executing in streaming mode.
     * @return true if the streaming option has been set.
     * @since 9.6
     */

    public boolean isStreaming() {
        return streaming;
    }



    /**
     * Parse a top-level Query.
     * Prolog? Expression
     *
     * @param queryString The text of the query
     * @param start       Offset of the start of the query
     * @param terminator  Token expected to follow the query (usually Token.EOF)
     * @param env         The static context
     * @return the Expression object that results from parsing
     * @throws net.sf.saxon.trans.XPathException
     *          if the expression contains a syntax error
     */

    /*@NotNull*/
    private Expression parseQuery(String queryString,
                                  int start,
                                  int terminator,
                                  /*@NotNull*/ QueryModule env) throws XPathException {
        this.env = env;
        charChecker = env.getConfiguration().getValidCharacterChecker();
//        if (defaultContainer == null) {
//            defaultContainer = new TemporaryContainer(env.getConfiguration(), env.getLocationMap(), 1);
//        }
        language = XQUERY;
        t = new Tokenizer();
        t.languageLevel = 31;
        t.isXQuery = true;
        try {
            t.tokenize(queryString, start, -1);
        } catch (XPathException err) {
            grumble(err.getMessage());
        }
        parseVersionDeclaration();
        t.allowSaxonExtensions = env.getConfiguration().getBooleanProperty(FeatureKeys.ALLOW_SYNTAX_EXTENSIONS);

        QNameParser qp = new QNameParser(env.getLiveNamespaceResolver());
        qp.setAcceptEQName(true);
        qp.setUnescaper(new Unescaper(env.getConfiguration().getValidCharacterChecker()));
        setQNameParser(qp);

        parseProlog();
        processPreamble();

        Expression exp = parseExpression();

        // Diagnostic code - show the expression before any optimizations
//        ExpressionPresenter ep = ExpressionPresenter.make(env.getConfiguration());
//        exp.explain(ep);
//        ep.close();
        // End of diagnostic code

        if (t.currentToken != terminator) {
            grumble("Unexpected token " + currentTokenDisplay() + " beyond end of query");
        }
        setLocation(exp);
        ExpressionTool.setDeepRetainedStaticContext(exp, env.makeRetainedStaticContext());
        if (errorCount == 0) {
            return exp;
        } else {
            XPathException err = new XPathException("One or more static errors were reported during query analysis");
            err.setHasBeenReported(true);
            err.setErrorCodeQName(firstError.getErrorCodeQName());   // largely for the XQTS test driver
            throw err;
        }
    }

    /**
     * Parse a library module.
     * Prolog? Expression
     *
     * @param queryString The text of the library module.
     * @param env         The static context. The result of parsing
     *                    a library module is that the static context is populated with a set of function
     *                    declarations and variable declarations. Each library module must have its own
     *                    static context objext.
     * @throws XPathException if the expression contains a syntax error
     */

    public final void parseLibraryModule(String queryString, /*@NotNull*/ QueryModule env)
            throws XPathException {
        this.env = env;
        final Configuration config = env.getConfiguration();
        charChecker = config.getValidCharacterChecker();
        if (config.getXMLVersion() == Configuration.XML10) {
            queryString = normalizeLineEndings10(queryString);
        } else {
            queryString = normalizeLineEndings11(queryString);
        }
        Executable exec = env.getExecutable();
        if (exec == null) {
            throw new IllegalStateException("Query library module has no associated Executable");
        }
        executable = exec;
//        defaultContainer = new SimpleContainer(env.getPackageData());
        t = new Tokenizer();
        t.languageLevel = 31;
        t.isXQuery = true;
        QNameParser qp = new QNameParser(env.getLiveNamespaceResolver());
        qp.setAcceptEQName(true);
        qp.setUnescaper(new Unescaper(config.getValidCharacterChecker()));
        setQNameParser(qp);
        try {
            t.tokenize(queryString, 0, -1);
        } catch (XPathException err) {
            grumble(err.getMessage());
        }
        parseVersionDeclaration();
        //t.setAllowExpandedQNameSyntax("3.0".equals(queryVersion));
        parseModuleDeclaration();
        parseProlog();
        processPreamble();
        if (t.currentToken != Token.EOF) {
            grumble("Unrecognized content found after the variable and function declarations in a library module");
        }
        if (errorCount != 0) {
            XPathException err = new XPathException("Static errors were reported in the imported library module");
            err.setErrorCodeQName(firstError.getErrorCodeQName());
            throw err;
        }
    }


    private void reportError(/*@NotNull*/ XPathException exception) throws XPathException {
        errorCount++;
        if (firstError == null) {
            firstError = exception;
        }
        ((QueryModule) env).reportStaticError(exception);
        throw exception;
    }

    private static Pattern encNamePattern = Pattern.compile("^[A-Za-z]([A-Za-z0-9._\\x2D])*$");

    /**
     * Parse the version declaration if present.
     *
     * @throws XPathException in the event of a syntax error.
     */
    private void parseVersionDeclaration() throws XPathException {
        if (t.currentToken == Token.XQUERY_VERSION) {
            nextToken();
            expect(Token.STRING_LITERAL);
            String queryVersion = unescape(t.currentTokenValue).toString();
            String[] allowedVersions = new String[]{"1.0", "3.0", "3.1"};
            if (Arrays.binarySearch(allowedVersions, queryVersion) < 0) {
//            if (!queryVersion.trim().matches("[0-9]+\\.[0-9]+")) {
                grumble("Invalid XQuery version " + queryVersion, "XQST0031");
            }
//            if (XQUERY10.equals(queryVersion)) {
//                // no action
//            } else if (XQUERY30.equals(queryVersion) || "1.1".equals(queryVersion)) {
//                queryVersion = XQUERY30;
//                if (((QueryModule) env).getLanguageVersion() != 30 && ((QueryModule) env).getLanguageVersion() != 31) {
//                    grumble("XQuery 3.0 was not enabled when invoking Saxon", "XQST0031");
//                    queryVersion = XQUERY10;
//                }
//            } else if(XQUERY31.equals(queryVersion)) {
//                queryVersion = XQUERY31;
//                if (((QueryModule) env).getLanguageVersion() != 31) {
//                    grumble("XQuery 3.1 was not enabled when invoking Saxon", "XQST0031");
//                    queryVersion = XQUERY30;
//                }
//            } else {
//                grumble("Unsupported XQuery version " + queryVersion, "XQST0031");
//                queryVersion = XQUERY10;
//            }
            nextToken();
            if ("encoding".equals(t.currentTokenValue)) {
                nextToken();
                expect(Token.STRING_LITERAL);
                if (!encNamePattern.matcher(unescape(t.currentTokenValue)).matches()) {
                    grumble("Encoding name contains invalid characters", "XQST0087");
                }
                // we ignore the encoding now: it was handled earlier, while decoding the byte stream
                nextToken();
            }
            expect(Token.SEMICOLON);
            nextToken();
        } else {
//            if (((QueryModule) env).getLanguageVersion()== 30) {
//                queryVersion = XQUERY30;
//            } else if (((QueryModule) env).getLanguageVersion() == 31) {
//                queryVersion = XQUERY31;
//            } else {
//                queryVersion = XQUERY10;
//            }
            if (t.currentToken == Token.XQUERY_ENCODING) {
                nextToken();
                expect(Token.STRING_LITERAL);
                if (!encNamePattern.matcher(t.currentTokenValue).matches()) {
                    grumble("Encoding name contains invalid characters", "XQST0087");
                }
                // we ignore the encoding now: it was handled earlier, while decoding the byte stream
                nextToken();
                expect(Token.SEMICOLON);
                nextToken();
            }
        }
    }

    /**
     * In a library module, parse the module declaration
     * Syntax: <"module" "namespace"> prefix "=" uri ";"
     *
     * @throws XPathException in the event of a syntax error.
     */

    private void parseModuleDeclaration() throws XPathException {
        expect(Token.MODULE_NAMESPACE);
        nextToken();
        expect(Token.NAME);
        String prefix = t.currentTokenValue;
        nextToken();
        expect(Token.EQUALS);
        nextToken();
        expect(Token.STRING_LITERAL);
        String uri = uriLiteral(t.currentTokenValue);
        checkProhibitedPrefixes(prefix, uri);
        if (uri.isEmpty()) {
            grumble("Module namespace cannot be \"\"", "XQST0088");
            uri = "http://saxon.fallback.namespace/";   // for error recovery
        }
        nextToken();
        expect(Token.SEMICOLON);
        nextToken();
        try {
            ((QueryModule) env).setModuleNamespace(uri);
            ((QueryModule) env).declarePrologNamespace(prefix, uri);
            executable.addQueryLibraryModule((QueryModule) env);
        } catch (XPathException err) {
            err.setLocator(makeLocation());
            reportError(err);
        }
    }

    /**
     * Parse the query prolog. This method, and its subordinate methods which handle
     * individual declarations in the prolog, cause the static context to be updated
     * with relevant context information. On exit, t.currentToken is the first token
     * that is not recognized as being part of the prolog.
     *
     * @throws XPathException in the event of a syntax error.
     */

    private void parseProlog() throws XPathException {
        //boolean allowSetters = true;
        boolean allowModuleDecl = true;
        boolean allowDeclarations = true;

        while (true) {
            try {
                if (t.currentToken == Token.MODULE_NAMESPACE) {
                    String uri = ((QueryModule) env).getModuleNamespace();
                    if (uri == null) {
                        grumble("Module declaration must not be used in a main module");
                    } else {
                        grumble("Module declaration appears more than once");
                    }
                    if (!allowModuleDecl) {
                        grumble("Module declaration must precede other declarations in the query prolog");
                    }
                }
                allowModuleDecl = false;
                switch (t.currentToken) {
                    case Token.DECLARE_NAMESPACE:
                        if (!allowDeclarations) {
                            grumble("Namespace declarations cannot follow variables, functions, or options");
                        }
                        //allowSetters = false;
                        parseNamespaceDeclaration();
                        break;
                    case Token.DECLARE_ANNOTATED:
                        // we have read "declare %"
                        processPreamble();
                        if (allowDeclarations) {
                            sealNamespaces(namespacesToBeSealed, env.getConfiguration());
                            allowDeclarations = false;
                        }
                        nextToken();
                        expect(Token.PERCENT);
                        AnnotationList annotationList = parseAnnotationsList();
                        if (isKeyword("function")) {
                            annotationList.check(env.getConfiguration(), "DF");
                            parseFunctionDeclaration(annotationList);
                        } else if (isKeyword("variable")) {
                            annotationList.check(env.getConfiguration(), "DV");
                            parseVariableDeclaration(annotationList);
                        } else {
                            grumble("Annotations can appear only in 'declare variable' and 'declare function'");
                        }
                        break;
                    case Token.DECLARE_DEFAULT:
                        nextToken();
                        expect(Token.NAME);
                        if (t.currentTokenValue.equals("element")) {
                            if (!allowDeclarations) {
                                grumble("Namespace declarations cannot follow variables, functions, or options");
                            }
                            //allowSetters = false;
                            parseDefaultElementNamespace();
                        } else if (t.currentTokenValue.equals("function")) {
                            if (!allowDeclarations) {
                                grumble("Namespace declarations cannot follow variables, functions, or options");
                            }
                            //allowSetters = false;
                            parseDefaultFunctionNamespace();
                        } else if (t.currentTokenValue.equals("collation")) {
                            if (!allowDeclarations) {
                                grumble("Collation declarations must appear earlier in the prolog");
                            }
                            parseDefaultCollation();
                        } else if (t.currentTokenValue.equals("order")) {
                            if (!allowDeclarations) {
                                grumble("Order declarations must appear earlier in the prolog");
                            }
                            parseDefaultOrder();
                        } else if (t.currentTokenValue.equals("decimal-format")) {
                            nextToken();
                            parseDefaultDecimalFormat();
                        } else {
                            grumble("After 'declare default', expected 'element', 'function', or 'collation'");
                        }
                        break;
                    case Token.DECLARE_BOUNDARY_SPACE:
                        if (!allowDeclarations) {
                            grumble("'declare boundary-space' must appear earlier in the query prolog");
                        }
                        parseBoundarySpaceDeclaration();
                        break;
                    case Token.DECLARE_ORDERING:
                        if (!allowDeclarations) {
                            grumble("'declare ordering' must appear earlier in the query prolog");
                        }
                        parseOrderingDeclaration();
                        break;
                    case Token.DECLARE_COPY_NAMESPACES:
                        if (!allowDeclarations) {
                            grumble("'declare copy-namespaces' must appear earlier in the query prolog");
                        }
                        parseCopyNamespacesDeclaration();
                        break;
                    case Token.DECLARE_BASEURI:
                        if (!allowDeclarations) {
                            grumble("'declare base-uri' must appear earlier in the query prolog");
                        }
                        parseBaseURIDeclaration();
                        break;
                    case Token.DECLARE_DECIMAL_FORMAT:
                        if (!allowDeclarations) {
                            grumble("'declare decimal-format' must appear earlier in the query prolog");
                        }
                        parseDecimalFormatDeclaration();
                        break;
                    case Token.IMPORT_SCHEMA:
                        //allowSetters = false;
                        if (!allowDeclarations) {
                            grumble("Import schema must appear earlier in the prolog");
                        }
                        parseSchemaImport();
                        break;
                    case Token.IMPORT_MODULE:
                        //allowSetters = false;
                        if (!allowDeclarations) {
                            grumble("Import module must appear earlier in the prolog");
                        }
                        parseModuleImport();
                        break;
                    case Token.DECLARE_VARIABLE:
                        //allowSetters = false;
                        if (allowDeclarations) {
                            sealNamespaces(namespacesToBeSealed, env.getConfiguration());
                            allowDeclarations = false;
                        }
                        processPreamble();
                        parseVariableDeclaration(AnnotationList.EMPTY);
                        break;
                    case Token.DECLARE_CONTEXT:
                        //allowSetters = false;
                        if (allowDeclarations) {
                            sealNamespaces(namespacesToBeSealed, env.getConfiguration());
                            allowDeclarations = false;
                        }
                        processPreamble();
                        parseContextItemDeclaration();
                        break;
                    case Token.DECLARE_FUNCTION:
                        if (allowDeclarations) {
                            sealNamespaces(namespacesToBeSealed, env.getConfiguration());
                            allowDeclarations = false;
                        }
                        processPreamble();
                        parseFunctionDeclaration(AnnotationList.EMPTY);
                        break;
                    case Token.DECLARE_UPDATING:
                        nextToken();
                        if (!isKeyword("function")) {
                            grumble("expected 'function' after 'declare updating");
                        }
                        if (allowDeclarations) {
                            sealNamespaces(namespacesToBeSealed, env.getConfiguration());
                            allowDeclarations = false;
                        }
                        processPreamble();
                        parserExtension.parseUpdatingFunctionDeclaration(this);
                        break;
                    case Token.DECLARE_OPTION:
                        if (allowDeclarations) {
                            sealNamespaces(namespacesToBeSealed, env.getConfiguration());
                            allowDeclarations = false;
                        }
                        parseOptionDeclaration();
                        break;
                    case Token.DECLARE_TYPE:
                        checkSyntaxExtensions("declare type");
                        if (allowDeclarations) {
                            sealNamespaces(namespacesToBeSealed, env.getConfiguration());
                            allowDeclarations = false;
                        }
                        parseTypeAliasDeclaration();
                        break;
                    case Token.DECLARE_CONSTRUCTION:
                        if (!allowDeclarations) {
                            grumble("'declare construction' must appear earlier in the query prolog");
                        }
                        parseConstructionDeclaration();
                        break;
                    case Token.DECLARE_REVALIDATION:
                        if (!allowDeclarations) {
                            grumble("'declare revalidation' must appear earlier in the query prolog");
                        }
                        parserExtension.parseRevalidationDeclaration(this);
                        break;
                    case Token.EOF:
                        String uri = ((QueryModule) env).getModuleNamespace();
                        if (uri == null) {
                            grumble("The main module must contain a query expression after any declarations in the prolog");
                        } else {
                            return;
                        }
                        break;
                    default:
                        return;
                }
                expect(Token.SEMICOLON);
                nextToken();
            } catch (XPathException err) {
                if (err.getLocator() == null) {
                    err.setLocator(makeLocation());
                }
                if (!err.hasBeenReported()) {
                    errorCount++;
                    if (firstError == null) {
                        firstError = err;
                    }
                    ((QueryModule) env).reportStaticError(err);
                }
                // we've reported an error, attempt to recover by skipping to the
                // next semicolon
                while (t.currentToken != Token.SEMICOLON) {
                    nextToken();
                    if (t.currentToken == Token.EOF) {
                        return;
                    } else if (t.currentToken == Token.RCURLY) {
                        t.lookAhead();
                    } else if (t.currentToken == Token.TAG) {
                        parsePseudoXML(true);
                    }
                }
                nextToken();
            }
        }
    }

    /**
     * Parse the annotations that can appear in a variable or function declaration
     * @return the annotations as a list
     * @throws XPathException in the event of a syntax error
     */

    protected AnnotationList parseAnnotationsList() throws XPathException {
        // we have read "declare" and have seen "%" as lookahead
        ArrayList<Annotation> annotations = new ArrayList<Annotation>();
        int options = 0;
        while (true) {
            t.setState(Tokenizer.BARE_NAME_STATE);
            nextToken();
            expect(Token.NAME);
            t.setState(Tokenizer.DEFAULT_STATE);
            StructuredQName qName;
            String uri;
            if (t.currentTokenValue.indexOf(':') < 0) {
                uri = NamespaceConstant.XQUERY;
                qName = new StructuredQName("", uri, t.currentTokenValue);
            } else {
                qName = makeStructuredQName(t.currentTokenValue, "");
                uri = qName.getURI();
            }
            Annotation annotation = new Annotation(qName);
            if (uri.equals(NamespaceConstant.XQUERY)) {
                if (qName.equals(Annotation.PRIVATE) || qName.equals(Annotation.PUBLIC) ||
                        qName.equals(Annotation.UPDATING) || qName.equals(Annotation.SIMPLE)) {
                    // OK
                } else {
                    grumble("Unrecognized variable or function annotation " + qName.getDisplayName(), "XQST0045");
                }
                annotation.addAnnotationParameter(new Int64Value(options));
            } else if (isReservedInQuery(uri)) {
                grumble("The annotation " + t.currentTokenValue + " is in a reserved namespace", "XQST0045");
            } else if (uri.equals("")) {
                grumble("The annotation " + t.currentTokenValue + " is in no namespace", "XQST0045");
            } else {
                // no action - ignore namespaced annotations
            }
            nextToken();
            if (t.currentToken == Token.LPAR) {
                nextToken();
                if (t.currentToken == Token.RPAR) {
                    grumble("Annotation parameter list cannot be empty");
                }
                while (true) {
                    // nextToken();
                    Literal arg = null;
                    switch (t.currentToken) {
                        case Token.STRING_LITERAL:
                            arg = (Literal)parseStringLiteral(false);
                            break;

                        case Token.NUMBER:
                            arg = (Literal)parseNumericLiteral(false);
                            break;

                        default:
                            grumble("Annotation parameter must be a literal");
                            return null;
                    }
                    GroundedValue val = arg.getValue();
                    if (val instanceof StringValue || val instanceof NumericValue) {
                        annotation.addAnnotationParameter((AtomicValue) val);
                    } else {
                        grumble("Annotation parameter must be a string or number");
                    }

                    if (t.currentToken == Token.RPAR) {
                        nextToken();
                        break;
                    }
                    expect(Token.COMMA);
                    nextToken();
                }
            }
            annotations.add(annotation);
            if (t.currentToken != Token.PERCENT) {
                return new AnnotationList(annotations);
            }
        }
    }


    private void sealNamespaces(/*@NotNull*/ List namespacesToBeSealed, /*@NotNull*/ Configuration config) {
        for (Object aNamespacesToBeSealed : namespacesToBeSealed) {
            String ns = (String) aNamespacesToBeSealed;
            config.sealNamespace(ns);
        }
    }

    /**
     * Method called once the setters have been read to do tidying up that can't be done until we've got
     * to the end
     *
     * @throws XPathException if parsing fails
     */

    private void processPreamble() throws XPathException {
        if (preambleProcessed) {
            return;
        }
        preambleProcessed = true;
        if (foundDefaultCollation) {
            String collationName = env.getDefaultCollationName();
            URI collationURI;
            try {
                collationURI = new URI(collationName);
                if (!collationURI.isAbsolute()) {
                    URI base = new URI(env.getStaticBaseURI());
                    collationURI = base.resolve(collationURI);
                    collationName = collationURI.toString();
                }
            } catch (URISyntaxException err) {
                grumble("Default collation name '" + collationName + "' is not a valid URI", "XQST0046");
                collationName = NamespaceConstant.CODEPOINT_COLLATION_URI;
            }
            if (env.getConfiguration().getCollation(collationName) == null) {
                grumble("Default collation name '" + collationName + "' is not a recognized collation", "XQST0038");
                collationName = NamespaceConstant.CODEPOINT_COLLATION_URI;
            }
            ((QueryModule) env).setDefaultCollationName(collationName);
        }
        for (Import imp : schemaImports) {
            try {
                applySchemaImport(imp);
            } catch (XPathException err) {
                if (!err.hasBeenReported()) {
                    throw err;
                }
            }
        }
        for (Import imp : moduleImports) {
            // Check that this import would not create a cycle involving a change of namespace
//            if (!disableCycleChecks) {
//                if (!imp.namespaceURI.equals(((QueryModule)env).getModuleNamespace())) {
//                    QueryModule parent = (QueryModule)env;
//                    if (!parent.mayImportModule(imp.namespaceURI)) {
//                        XPathException err = new XPathException(
//                                "A module cannot import itself directly or indirectly, unless all modules in the cycle are in the same namespace");
//                        err.setErrorCode("XQST0073");
//                        err.setIsStaticError(true);
//                        throw err;
//                    }
//                }
//            }
            try {
                applyModuleImport(imp);
            } catch (XPathException err) {
                if (!err.hasBeenReported()) {
                    throw err;
                }
            }
        }
    }

    private void parseDefaultCollation() throws XPathException {
        // <"default" "collation"> StringLiteral
        if (foundDefaultCollation) {
            grumble("default collation appears more than once", "XQST0038");
        }
        foundDefaultCollation = true;
        nextToken();
        expect(Token.STRING_LITERAL);
        String uri = uriLiteral(t.currentTokenValue);
        ((QueryModule) env).setDefaultCollationName(uri);
        nextToken();
    }

    /**
     * parse "declare default order empty (least|greatest)"
     *
     * @throws XPathException if parsing fails
     */
    private void parseDefaultOrder() throws XPathException {
        if (foundEmptyOrderingDeclaration) {
            grumble("empty ordering declaration appears more than once", "XQST0069");
        }
        foundEmptyOrderingDeclaration = true;
        nextToken();
        if (!isKeyword("empty")) {
            grumble("After 'declare default order', expected keyword 'empty'");
        }
        nextToken();
        if (isKeyword("least")) {
            ((QueryModule) env).setEmptyLeast(true);
        } else if (isKeyword("greatest")) {
            ((QueryModule) env).setEmptyLeast(false);
        } else {
            grumble("After 'declare default order empty', expected keyword 'least' or 'greatest'");
        }
        nextToken();
    }

    /**
     * Parse the "declare xmlspace" declaration.
     * Syntax: <"declare" "boundary-space"> ("preserve" | "strip")
     *
     * @throws XPathException if a static error is encountered
     */

    private void parseBoundarySpaceDeclaration() throws XPathException {
        if (foundBoundarySpaceDeclaration) {
            grumble("'declare boundary-space' appears more than once", "XQST0068");
        }
        foundBoundarySpaceDeclaration = true;
        nextToken();
        expect(Token.NAME);
        if ("preserve".equals(t.currentTokenValue)) {
            ((QueryModule) env).setPreserveBoundarySpace(true);
        } else if ("strip".equals(t.currentTokenValue)) {
            ((QueryModule) env).setPreserveBoundarySpace(false);
        } else {
            grumble("boundary-space must be 'preserve' or 'strip'");
        }
        nextToken();
    }

    /**
     * Parse the "declare ordering" declaration.
     * Syntax: <"declare" "ordering"> ("ordered" | "unordered")
     *
     * @throws XPathException if parsing fails
     */

    private void parseOrderingDeclaration() throws XPathException {
        if (foundOrderingDeclaration) {
            grumble("ordering mode declaration appears more than once", "XQST0065");
        }
        foundOrderingDeclaration = true;
        nextToken();
        expect(Token.NAME);
        if ("ordered".equals(t.currentTokenValue)) {
            // no action
        } else if ("unordered".equals(t.currentTokenValue)) {
            // no action
        } else {
            grumble("ordering mode must be 'ordered' or 'unordered'");
        }
        nextToken();
    }

    /**
     * Parse the "declare copy-namespaces" declaration.
     * Syntax: <"declare" "copy-namespaces"> ("preserve" | "no-preserve") "," ("inherit" | "no-inherit")
     *
     * @throws XPathException if a static error is encountered
     */

    private void parseCopyNamespacesDeclaration() throws XPathException {
        if (foundCopyNamespaces) {
            grumble("declare copy-namespaces appears more than once", "XQST0055");
        }
        foundCopyNamespaces = true;
        nextToken();
        expect(Token.NAME);
        if ("preserve".equals(t.currentTokenValue)) {
            ((QueryModule) env).setPreserveNamespaces(true);
        } else if ("no-preserve".equals(t.currentTokenValue)) {
            ((QueryModule) env).setPreserveNamespaces(false);
        } else {
            grumble("copy-namespaces must be followed by 'preserve' or 'no-preserve'");
        }
        nextToken();
        expect(Token.COMMA);
        nextToken();
        expect(Token.NAME);
        if ("inherit".equals(t.currentTokenValue)) {
            ((QueryModule) env).setInheritNamespaces(true);
        } else if ("no-inherit".equals(t.currentTokenValue)) {
            ((QueryModule) env).setInheritNamespaces(false);
        } else {
            grumble("After the comma in the copy-namespaces declaration, expected 'inherit' or 'no-inherit'");
        }
        nextToken();
    }


    /**
     * Parse the "declare construction" declaration.
     * Syntax: <"declare" "construction"> ("preserve" | "strip")
     *
     * @throws XPathException if parsing fails
     */

    private void parseConstructionDeclaration() throws XPathException {
        if (foundConstructionDeclaration) {
            grumble("declare construction appears more than once", "XQST0067");
        }
        foundConstructionDeclaration = true;
        nextToken();
        expect(Token.NAME);
        int val;
        if ("preserve".equals(t.currentTokenValue)) {
            val = Validation.PRESERVE;
//            if (!env.getExecutable().isSchemaAware()) {
//                grumble("construction mode preserve is allowed only with a schema-aware query");
//            }
        } else if ("strip".equals(t.currentTokenValue)) {
            val = Validation.STRIP;
        } else {
            grumble("construction mode must be 'preserve' or 'strip'");
            val = Validation.STRIP;
        }
        ((QueryModule) env).setConstructionMode(val);
        nextToken();
    }

    /**
     * Parse the "declare revalidation" declaration.
     * Syntax: not allowed unless XQuery update is in use
     *
     * @throws XPathException if the syntax is incorrect, or is not allowed in this XQuery processor
     */

    protected void parseRevalidationDeclaration() throws XPathException {
        grumble("declare revalidation is allowed only in XQuery Update");
    }

    /**
     * Parse (and process) the schema import declaration.
     * SchemaImport ::=	"import" "schema" SchemaPrefix? URILiteral ("at" URILiteral ("," URILiteral)*)?
     * SchemaPrefix ::=	("namespace" NCName "=") | ("default" "element" "namespace")
     *
     * @throws XPathException if parsing fails
     */

    private void parseSchemaImport() throws XPathException {
        ensureSchemaAware("import schema");
        Import sImport = new Import();
        String prefix = null;
        sImport.namespaceURI = null;
        sImport.locationURIs = new ArrayList<String>(5);
        nextToken();
        if (isKeyword("namespace")) {
            t.setState(Tokenizer.DEFAULT_STATE);
            nextToken();
            expect(Token.NAME);
            prefix = t.currentTokenValue;
            nextToken();
            expect(Token.EQUALS);
            nextToken();
        } else if (isKeyword("default")) {
            nextToken();
            if (!isKeyword("element")) {
                grumble("In 'import schema', expected 'element namespace'");
            }
            nextToken();
            if (!isKeyword("namespace")) {
                grumble("In 'import schema', expected keyword 'namespace'");
            }
            nextToken();
            prefix = "";
        }
        if (t.currentToken == Token.STRING_LITERAL) {
            String uri = uriLiteral(t.currentTokenValue);
            checkProhibitedPrefixes(prefix, uri);
            sImport.namespaceURI = uri;
            nextToken();
            if (isKeyword("at")) {
                nextToken();
                expect(Token.STRING_LITERAL);
                sImport.locationURIs.add(uriLiteral(t.currentTokenValue));
                nextToken();
                while (t.currentToken == Token.COMMA) {
                    nextToken();
                    expect(Token.STRING_LITERAL);
                    sImport.locationURIs.add(uriLiteral(t.currentTokenValue));
                    nextToken();
                }
            } else if (t.currentToken != Token.SEMICOLON) {
                grumble("After the target namespace URI, expected 'at' or ';'");
            }
        } else {
            grumble("After 'import schema', expected 'namespace', 'default', or a string-literal");
        }
        if (prefix != null) {
            try {
                if (prefix.isEmpty()) {
                    ((QueryModule) env).setDefaultElementNamespace(sImport.namespaceURI);
                } else {
                    if (sImport.namespaceURI == null || "".equals(sImport.namespaceURI)) {
                        grumble("A prefix cannot be bound to the null namespace", "XQST0057");
                    }
                    ((QueryModule) env).declarePrologNamespace(prefix, sImport.namespaceURI);
                }
            } catch (XPathException err) {
                err.setLocator(makeLocation());
                reportError(err);
            }
        }
        for (Object schemaImport : schemaImports) {
            Import imp = (Import) schemaImport;
            if (imp.namespaceURI.equals(sImport.namespaceURI)) {
                grumble("Schema namespace '" + sImport.namespaceURI + "' is imported more than once", "XQST0058");
                break;
            }
        }

        schemaImports.add(sImport);

    }

    protected void ensureSchemaAware(String featureName) throws XPathException {
        if (!env.getConfiguration().isLicensedFeature(Configuration.LicenseFeature.ENTERPRISE_XQUERY)) {
            throw new XPathException("This Saxon version and license does not allow use of '" + featureName + "'", "XQST0009");
        }
        env.getConfiguration().checkLicensedFeature(Configuration.LicenseFeature.ENTERPRISE_XQUERY, featureName, -1);
        getExecutable().setSchemaAware(true);
        getStaticContext().getPackageData().setSchemaAware(true);
    }

    private void applySchemaImport(/*@NotNull*/ Import sImport) throws XPathException {

        // Do the importing

        Configuration config = env.getConfiguration();
        synchronized (config) {
            if (!config.isSchemaAvailable(sImport.namespaceURI)) {
                if (!sImport.locationURIs.isEmpty()) {
                    try {
                        PipelineConfiguration pipe = config.makePipelineConfiguration();
                        config.readMultipleSchemas(pipe, env.getStaticBaseURI(), sImport.locationURIs, sImport.namespaceURI);
                        namespacesToBeSealed.add(sImport.namespaceURI);
                    } catch (SchemaException err) {
                        grumble("Error in schema " + sImport.namespaceURI + ": " + err.getMessage(), "XQST0059");
                    }
                } else if (sImport.namespaceURI.equals(NamespaceConstant.XML) ||
                        sImport.namespaceURI.equals(NamespaceConstant.FN)||
                        sImport.namespaceURI.equals(NamespaceConstant.SCHEMA_INSTANCE)) {
                    config.addSchemaForBuiltInNamespace(sImport.namespaceURI);
                } else {
                    grumble("Unable to locate requested schema " + sImport.namespaceURI, "XQST0059");
                }
            }
            ((QueryModule) env).addImportedSchema(sImport.namespaceURI, env.getStaticBaseURI(), sImport.locationURIs);
        }
    }

    /**
     * Parse (and expand) the module import declaration.
     * Syntax: <"import" "module" ("namespace" NCName "=")? uri ("at" uri ("," uri)*)? ";"
     *
     * @throws net.sf.saxon.trans.XPathException
     *          if a static error is encountered
     */

    private void parseModuleImport() throws XPathException {
        QueryModule thisModule = (QueryModule) env;
        Import mImport = new Import();
        String prefix = null;
        mImport.namespaceURI = null;
        mImport.locationURIs = new ArrayList<String>(5);
        nextToken();
        if (t.currentToken == Token.NAME && t.currentTokenValue.equals("namespace")) {
            t.setState(Tokenizer.DEFAULT_STATE);
            nextToken();
            expect(Token.NAME);
            prefix = t.currentTokenValue;
            nextToken();
            expect(Token.EQUALS);
            nextToken();
        }
        if (t.currentToken == Token.STRING_LITERAL) {
            String uri = uriLiteral(t.currentTokenValue);
            checkProhibitedPrefixes(prefix, uri);
            mImport.namespaceURI = uri;
            if (mImport.namespaceURI.isEmpty()) {
                grumble("Imported module namespace cannot be \"\"", "XQST0088");
                mImport.namespaceURI = "http://saxon.fallback.namespace/line" + t.getLineNumber();   // for error recovery
            }
            if (importedModules.contains(mImport.namespaceURI)) {
                grumble("Two 'import module' declarations specify the same module namespace", "XQST0047");
            }
            importedModules.add(mImport.namespaceURI);
            ((QueryModule) env).addImportedNamespace(mImport.namespaceURI);
            nextToken();
            if (isKeyword("at")) {
                do {
                    nextToken();
                    expect(Token.STRING_LITERAL);
                    mImport.locationURIs.add(uriLiteral(t.currentTokenValue));
                    nextToken();
                } while (t.currentToken == Token.COMMA);
            }
        } else {
            grumble("After 'import module', expected 'namespace' or a string-literal");
        }
        if (prefix != null) {
            try {
                if (mImport.namespaceURI.equals(thisModule.getModuleNamespace()) &&
                        mImport.namespaceURI.equals(thisModule.checkURIForPrefix(prefix))) {
                    // then do nothing: a duplicate declaration in this situation is not an error
                } else {
                    thisModule.declarePrologNamespace(prefix, mImport.namespaceURI);
                }
            } catch (XPathException err) {
                err.setLocator(makeLocation());
                reportError(err);
            }
        }

//        // Check that this import would not create a cycle involving a change of namespace
//        if (!disableCycleChecks) {
//            if (!mImport.namespaceURI.equals(((QueryModule)env).getModuleNamespace())) {
//                QueryModule parent = (QueryModule)env;
//                if (!parent.mayImport(mImport.namespaceURI)) {
//                    StaticError err = new StaticError("A module cannot import itself directly or indirectly, unless all modules in the cycle are in the same namespace");
//                    err.setErrorCode("XQST0073");
//                    throw err;
//                }
//            }
//        }

        moduleImports.add(mImport);
    }

    public void applyModuleImport(/*@NotNull*/ Import mImport) throws XPathException {
        List<QueryModule> existingModules;

        // resolve the location URIs against the base URI
        for (int i = 0; i < mImport.locationURIs.size(); i++) {
            try {
                String uri = mImport.locationURIs.get(i);
                URI abs = ResolveURI.makeAbsolute(uri, env.getStaticBaseURI());
                mImport.locationURIs.set(i, abs.toString());
            } catch (URISyntaxException e) {
                grumble("Invalid URI " + mImport.locationURIs.get(i) + ": " + e.getMessage());
            }
        }

        // See if the URI is that of a separately-compiled query library
        QueryLibrary lib = ((QueryModule) env).getUserQueryContext().getCompiledLibrary(mImport.namespaceURI);
        if (lib != null) {
            executable.addQueryLibraryModule(lib);
            existingModules = new ArrayList<QueryModule>();
            existingModules.add(lib);
            lib.link((QueryModule) env);

        } else if (!env.getConfiguration().getBooleanProperty(FeatureKeys.XQUERY_MULTIPLE_MODULE_IMPORTS)) {
            // Unless this configuration option is set, if we already know a module with the right module URI, then we
            // use it irrespective of its location URI.
            List<QueryModule> list = executable.getQueryLibraryModules(mImport.namespaceURI);
            if (list != null && !list.isEmpty()) {
                return;
            }
        } else {

            for (int h = mImport.locationURIs.size() - 1; h >= 0; h--) {
                if (executable.isQueryLocationHintProcessed(mImport.locationURIs.get(h))) {
                    mImport.locationURIs.remove(h);
                }
            }

        }

        // If there are no location URIs left, and we already know a module with the right module URI.

        if (mImport.locationURIs.isEmpty()) {
            List<QueryModule> list = executable.getQueryLibraryModules(mImport.namespaceURI);
            if (list != null && !list.isEmpty()) {
                return;
            }
        }

        // Call the module URI resolver to find the remaining modules

        ModuleURIResolver resolver = ((QueryModule) env).getUserQueryContext().getModuleURIResolver();

        String[] hints = new String[mImport.locationURIs.size()];
        for (int h = 0; h < hints.length; h++) {
            hints[h] = mImport.locationURIs.get(h);
        }
        StreamSource[] sources = null;
        if (resolver != null) {
            try {
                sources = resolver.resolve(mImport.namespaceURI, env.getStaticBaseURI(), hints);
            } catch (XPathException err) {
                grumble("Failed to resolve URI of imported module: " + err.getMessage(), "XQST0059");
            }
        }
        if (sources == null) {
            if (hints.length == 0) {
                grumble("Cannot locate module for namespace " + mImport.namespaceURI, "XQST0059");
            }
            resolver = env.getConfiguration().getStandardModuleURIResolver();
            sources = resolver.resolve(mImport.namespaceURI, env.getStaticBaseURI(), hints);
        }

        for (String hint : mImport.locationURIs) {
            executable.addQueryLocationHintProcessed(hint);
        }

        for (int m = 0; m < sources.length; m++) {
            StreamSource ss = sources[m];
            String baseURI = ss.getSystemId();
            if (baseURI == null) {
                if (m < hints.length) {
                    baseURI = hints[m];
                    ss.setSystemId(hints[m]);
                } else {
                    grumble("No base URI available for imported module", "XQST0059");
                }
            }
            // Although the module hadn't been loaded when we started, it might have been loaded since, as
            // a result of a reference from another imported module. Note, we are careful here to use URI.equals()
            // rather that String.equals() to compare URIs, as this gives a slightly more intelligent comparison,
            // for example the scheme name is case-independent, and file:///x/y/z matches file:/x/y/z.
            // TODO: use similar logic when loading schema modules
            // TODO: despite the comment above, we are comparing URIs as strings
            existingModules = executable.getQueryLibraryModules(mImport.namespaceURI);
            boolean loaded = false;
            if (existingModules != null && m < hints.length) {
                for (QueryModule existingModule : existingModules) {
                    URI uri = existingModule.getLocationURI();
                    if (uri != null && uri.toString().equals(mImport.locationURIs.get(m))) {
                        loaded = true;
                        break;
                    }
                }
            }
            if (loaded) {
                break;
            }

            try {
                String queryText = QueryReader.readSourceQuery(ss, charChecker);
                try {
                    if (ss.getInputStream() != null) {
                        ss.getInputStream().close();
                    } else if (ss.getReader() != null) {
                        ss.getReader().close();
                    }
                } catch (IOException e) {
                    throw new XPathException("Failure while closing file for imported query module");
                }
                QueryModule.makeQueryModule(
                        baseURI, executable, (QueryModule) env, queryText, mImport.namespaceURI
                );
            } catch (XPathException err) {
                err.maybeSetLocation(makeLocation());
                reportError(err);
            }
        }
    }

    /**
     * Parse the Base URI declaration.
     * Syntax: <"declare" "base-uri"> uri-literal
     *
     * @throws XPathException if a static error is found
     */

    private void parseBaseURIDeclaration() throws XPathException {
        if (foundBaseURIDeclaration) {
            grumble("Base URI Declaration may only appear once", "XQST0032");
        }
        foundBaseURIDeclaration = true;
        nextToken();
        expect(Token.STRING_LITERAL);
        String uri = uriLiteral(t.currentTokenValue);
        try {
            // if the supplied URI is relative, try to resolve it
            URI baseURI = new URI(uri);
            if (!baseURI.isAbsolute()) {
                String oldBase = env.getStaticBaseURI();
                uri = ResolveURI.makeAbsolute(uri, oldBase).toString();
            }
            ((QueryModule) env).setBaseURI(uri);
        } catch (URISyntaxException err) {
            // The spec says this "is not intrinsically an error", but can cause a failure later
            ((QueryModule) env).setBaseURI(uri);
        }
        nextToken();
    }


    /**
     * Parse a named decimal format declaration.
     * "declare" "decimal-format" QName (property "=" string-literal)*
     *
     * @throws XPathException
     */

    private void parseDecimalFormatDeclaration() throws XPathException {
        nextToken();
        expect(Token.NAME);
        StructuredQName formatName = makeStructuredQName(t.currentTokenValue, "");
        if (env.getDecimalFormatManager().getNamedDecimalFormat(formatName) != null) {
            grumble("Duplicate declaration of decimal-format " + formatName.getDisplayName(), "XQST0111");
        }
        nextToken();
        parseDecimalFormatProperties(formatName);
    }

    /**
     * Parse a default decimal format declaration
     * "declare" "default" "decimal-format" (property "=" string-literal)*
     *
     * @throws XPathException
     */

    private void parseDefaultDecimalFormat() throws XPathException {
        if (foundDefaultDecimalFormat) {
            grumble("Duplicate declaration of default decimal-format", "XQST0111");
        }
        foundDefaultDecimalFormat = true;
        parseDecimalFormatProperties(null);
    }

    private void parseDecimalFormatProperties(/*@Nullable*/ StructuredQName formatName) throws XPathException {
        int outerOffset = t.currentTokenStartOffset;
        DecimalFormatManager dfm = env.getDecimalFormatManager();
        DecimalSymbols dfs = formatName == null ? dfm.getDefaultDecimalFormat() : dfm.obtainNamedDecimalFormat(formatName);
        dfs.setHostLanguage(Configuration.XQUERY, 31);
        Set<String> propertyNames = new HashSet<String>(10);
        while (t.currentToken != Token.SEMICOLON) {
            int offset = t.currentTokenStartOffset;
            String propertyName = t.currentTokenValue;
            if (propertyNames.contains(propertyName)) {
                grumble("Property name " + propertyName + " is defined more than once", "XQST0114", offset);
            }
            nextToken();
            expect(Token.EQUALS);
            nextToken();
            expect(Token.STRING_LITERAL);
            String propertyValue = unescape(t.currentTokenValue).toString();
            nextToken();
            propertyNames.add(propertyName);
            if (propertyName.equals("decimal-separator")) {
                dfs.setDecimalSeparator(propertyValue);
            } else if (propertyName.equals("grouping-separator")) {
                dfs.setGroupingSeparator(propertyValue);
            } else if (propertyName.equals("infinity")) {
                dfs.setInfinity(propertyValue);
            } else if (propertyName.equals("minus-sign")) {
                dfs.setMinusSign(propertyValue);
            } else if (propertyName.equals("NaN")) {
                dfs.setNaN(propertyValue);
            } else if (propertyName.equals("percent")) {
                dfs.setPercent(propertyValue);
            } else if (propertyName.equals("per-mille")) {
                dfs.setPerMille(propertyValue);
            } else if (propertyName.equals("zero-digit")) {
                try {
                    dfs.setZeroDigit(propertyValue);
                } catch (XPathException err) {
                    err.setErrorCode("XQST0097");
                    throw err;
                }
            } else if (propertyName.equals("digit")) {
                dfs.setDigit(propertyValue);
            } else if (propertyName.equals("pattern-separator")) {
                dfs.setPatternSeparator(propertyValue);
            } else if (propertyName.equals("exponent-separator")) {
                dfs.setExponentSeparator(propertyValue);
            } else {
                grumble("Unknown decimal-format property: " + propertyName, "XPST0003", offset);
            }
        }


        try {
            dfs.checkConsistency(formatName);
        } catch (XPathException err) {
            grumble(err.getMessage(), "XQST0098", outerOffset);
        }

    }

    /**
     * Parse the "default function namespace" declaration.
     * Syntax: <"declare" "default" "function" "namespace"> StringLiteral
     *
     * @throws XPathException to indicate a syntax error
     */

    private void parseDefaultFunctionNamespace() throws XPathException {
        if (foundDefaultFunctionNamespace) {
            grumble("default function namespace appears more than once", "XQST0066");
        }
        foundDefaultFunctionNamespace = true;
        nextToken();
        expect(Token.NAME);
        if (!"namespace".equals(t.currentTokenValue)) {
            grumble("After 'declare default function', expected 'namespace'");
        }
        nextToken();
        expect(Token.STRING_LITERAL);
        String uri = uriLiteral(t.currentTokenValue);
        if (uri.equals(NamespaceConstant.XML) || uri.equals(NamespaceConstant.XMLNS)) {
            grumble("Reserved namespace used as default element/type namespace", "XQST0070");
        }
        ((QueryModule) env).setDefaultFunctionNamespace(uri);
        nextToken();
    }

    /**
     * Parse the "default element namespace" declaration.
     * Syntax: <"declare" "default" "element" "namespace"> StringLiteral
     *
     * @throws XPathException to indicate a syntax error
     */

    private void parseDefaultElementNamespace() throws XPathException {
        if (foundDefaultElementNamespace) {
            grumble("default element namespace appears more than once", "XQST0066");
        }
        foundDefaultElementNamespace = true;
        nextToken();
        expect(Token.NAME);
        if (!"namespace".equals(t.currentTokenValue)) {
            grumble("After 'declare default element', expected 'namespace'");
        }
        nextToken();
        expect(Token.STRING_LITERAL);
        String uri = uriLiteral(t.currentTokenValue);
        if (uri.equals(NamespaceConstant.XML) || uri.equals(NamespaceConstant.XMLNS)) {
            grumble("Reserved namespace used as default element/type namespace", "XQST0070");
        }
        ((QueryModule) env).setDefaultElementNamespace(uri);
        nextToken();
    }

    /**
     * Parse a namespace declaration in the Prolog.
     * Syntax: <"declare" "namespace"> NCName "=" StringLiteral
     *
     * @throws XPathException if parsing fails or a static error is found
     */

    private void parseNamespaceDeclaration() throws XPathException {
        nextToken();
        expect(Token.NAME);
        String prefix = t.currentTokenValue;
        if (!NameChecker.isValidNCName(prefix)) {
            grumble("Invalid namespace prefix " + Err.wrap(prefix));
        }
        nextToken();
        expect(Token.EQUALS);
        nextToken();
        expect(Token.STRING_LITERAL);
        String uri = uriLiteral(t.currentTokenValue);
        checkProhibitedPrefixes(prefix, uri);
        if ("xml".equals(prefix)) {
            // disallowed here even if bound to the correct namespace - erratum XQ.E19
            grumble("Namespace prefix 'xml' cannot be declared", "XQST0070");
        }
        try {
            ((QueryModule) env).declarePrologNamespace(prefix, uri);
        } catch (XPathException err) {
            err.setLocator(makeLocation());
            reportError(err);
        }
        nextToken();
    }

    /**
     * Check that a namespace declaration does not use a prohibited prefix or URI (xml or xmlns)
     *
     * @param prefix the prefix to be tested
     * @param uri    the URI being declared
     * @throws XPathException if the prefix is prohibited
     */

    private void checkProhibitedPrefixes(/*@Nullable*/ String prefix, /*@Nullable*/ String uri) throws XPathException {
        if (prefix != null && prefix.length() > 0 && !NameChecker.isValidNCName(prefix)) {
            grumble("The namespace prefix " + Err.wrap(prefix) + " is not a valid NCName");
        }
        if (prefix == null) {
            prefix = "";
        }
        if (uri == null) {
            uri = "";
        }
        if ("xmlns".equals(prefix)) {
            grumble("The namespace prefix 'xmlns' cannot be redeclared", "XQST0070");
        }
        if (uri.equals(NamespaceConstant.XMLNS)) {
            grumble("The xmlns namespace URI is reserved", "XQST0070");
        }
        if (uri.equals(NamespaceConstant.XML) && !prefix.equals("xml")) {
            grumble("The XML namespace cannot be bound to any prefix other than 'xml'", "XQST0070");
        }
        if (prefix.equals("xml") && !uri.equals(NamespaceConstant.XML)) {
            grumble("The prefix 'xml' cannot be bound to any namespace other than " + NamespaceConstant.XML, "XQST0070");
        }
    }

    /**
     * Parse a global variable definition.
     * <"declare" "variable" "$"> VarName TypeDeclaration?
     * ((":=" ExprSingle ) | "external")
     * XQuery 3.0 allows "external := ExprSingle"
     *
     * @param annotations derived from any %-annotations present in XQuery 3.0
     * @throws XPathException if a static error is found
     */

    private void parseVariableDeclaration(AnnotationList annotations) throws XPathException {
        int offset = t.currentTokenStartOffset;
        GlobalVariable var = new GlobalVariable();
        var.setPackageData(env.getPackageData());
        var.setLineNumber(t.getLineNumber() + 1);
        var.setSystemId(env.getSystemId());
        if (annotations != null) {
            var.setPrivate(annotations.includes(Annotation.PRIVATE));
        }
        nextToken();
        expect(Token.DOLLAR);
        t.setState(Tokenizer.BARE_NAME_STATE);
        nextToken();
        expect(Token.NAME);
        String varName = t.currentTokenValue;
        StructuredQName varQName = makeStructuredQName(t.currentTokenValue, "");
        var.setVariableQName(varQName);

        String uri = varQName.getURI();
        String moduleURI = ((QueryModule) env).getModuleNamespace();
        if (moduleURI != null && !moduleURI.equals(uri)) {
            grumble("A variable declared in a library module must be in the module namespace", "XQST0048", offset);
        }

        nextToken();
        SequenceType requiredType = SequenceType.ANY_SEQUENCE;
        if (t.currentToken == Token.AS) {
            t.setState(Tokenizer.SEQUENCE_TYPE_STATE);
            nextToken();
            requiredType = parseSequenceType();
        }
        var.setRequiredType(requiredType);

        if (t.currentToken == Token.ASSIGN) {
            t.setState(Tokenizer.DEFAULT_STATE);
            nextToken();
            Expression exp = parseExprSingle();
            var.setSelectExpression(makeTracer(offset, exp, StandardNames.XSL_VARIABLE, varQName));
        } else if (t.currentToken == Token.NAME) {
            if ("external".equals(t.currentTokenValue)) {
                GlobalParam par = new GlobalParam();
                par.setPackageData(((QueryModule) env).getPackageData());
                //par.setExecutable(var.getExecutable());
                par.setLineNumber(var.getLineNumber());
                par.setSystemId(var.getSystemId());
                par.setVariableQName(var.getVariableQName());
                par.setRequiredType(var.getRequiredType());
                var = par;
                nextToken();
                if (t.currentToken == Token.ASSIGN) {
                    t.setState(Tokenizer.DEFAULT_STATE);
                    nextToken();
                    Expression exp = parseExprSingle();
                    var.setSelectExpression(makeTracer(offset, exp, StandardNames.XSL_VARIABLE, varQName));
                }

            } else {
                grumble("Variable must either be initialized or be declared as external");
            }
        } else {
            grumble("Expected ':=' or 'external' in variable declaration");
        }

        QueryModule qenv = (QueryModule) env;
        RetainedStaticContext rsc = env.makeRetainedStaticContext();
        var.setRetainedStaticContext(rsc);
        if (var.getBody() != null) {
            ExpressionTool.setDeepRetainedStaticContext(var.getBody(), rsc);
        }
        if (qenv.getModuleNamespace() != null &&
                !uri.equals(qenv.getModuleNamespace())) {
            grumble("Variable " + Err.wrap(varName, Err.VARIABLE) + " is not defined in the module namespace");
        }
        try {
            qenv.declareVariable(var);
        } catch (XPathException e) {
            grumble(e.getMessage(), e.getErrorCodeQName(), -1);
        }
    }


    /**
     * Parse a context item declaration.
     * "declare" "context" "item"  TypeDeclaration?
     * ((":=" ExprSingle ) | ("external" (":=" ExprSingle ))
     *
     * @throws XPathException
     */

    private void parseContextItemDeclaration() throws XPathException {
        int offset = t.currentTokenStartOffset;
        nextToken();
        if (!isKeyword("item")) {
            grumble("After 'declare context', expected 'item'");
        }
        if (foundContextItemDeclaration) {
            grumble("More than one context item declaration found", "XQST0099", offset);
        }
        foundContextItemDeclaration = true;

        GlobalContextRequirement req = new GlobalContextRequirement();
        req.setAbsentFocus(false);

        t.setState(Tokenizer.BARE_NAME_STATE);

        nextToken();
        ItemType requiredType = AnyItemType.getInstance();
        if (t.currentToken == Token.AS) {
            t.setState(Tokenizer.SEQUENCE_TYPE_STATE);
            nextToken();
            requiredType = parseItemType();
        }
        req.addRequiredItemType(requiredType);

        if (t.currentToken == Token.ASSIGN) {
            if (!((QueryModule) env).isMainModule()) {
                grumble("The context item must not be initialized in a library module", "XQST0113");
            }
            t.setState(Tokenizer.DEFAULT_STATE);
            nextToken();
            Expression exp = parseExprSingle();
            exp.setRetainedStaticContext(env.makeRetainedStaticContext());
            RoleDiagnostic role = new RoleDiagnostic(RoleDiagnostic.CONTEXT_ITEM, "context item declaration", 0);
            exp = CardinalityChecker.makeCardinalityChecker(exp, StaticProperty.EXACTLY_ONE, role);
            ExpressionVisitor visitor = ExpressionVisitor.make(env);
            exp = exp.simplify();
            ContextItemStaticInfo info = env.getConfiguration().makeContextItemStaticInfo(AnyItemType.getInstance(), true);
            exp.setRetainedStaticContext(env.makeRetainedStaticContext());
            exp = exp.typeCheck(visitor, info);
            req.setDefaultValue(exp);
        } else if (t.currentToken == Token.NAME && "external".equals(t.currentTokenValue)) {
            req.setAbsentFocus(false);
            nextToken();
            if (t.currentToken == Token.ASSIGN) {
                if (!((QueryModule) env).isMainModule()) {
                    grumble("The context item must not be initialized in a library module", "XQST0113");
                }
                t.setState(Tokenizer.DEFAULT_STATE);
                nextToken();
                Expression exp = parseExprSingle();
                RoleDiagnostic role = new RoleDiagnostic(RoleDiagnostic.CONTEXT_ITEM, "context item declaration", 0);
                exp = CardinalityChecker.makeCardinalityChecker(exp, StaticProperty.EXACTLY_ONE, role);
                exp.setRetainedStaticContext(env.makeRetainedStaticContext());
                req.setDefaultValue(exp);
            }
        } else {
            grumble("Expected ':=' or 'external' in context item declaration");
        }

        Executable exec = getExecutable();
        if (exec.getGlobalContextRequirement() != null) {
            // the context item is already declared in another module. Compare the required types
            GlobalContextRequirement gcr = exec.getGlobalContextRequirement();
            if (gcr.getDefaultValue() == null && req.getDefaultValue() != null) {
                gcr.setDefaultValue(req.getDefaultValue());
            }
            for (ItemType otherType : gcr.getRequiredItemTypes()) {
                if (otherType != AnyItemType.getInstance()) {
                    TypeHierarchy th = env.getConfiguration().getTypeHierarchy();
                    int rel = th.relationship(requiredType, otherType);
                    if (rel == TypeHierarchy.DISJOINT) {
                        // the two types are incompatible: fail now
                        grumble("Different modules specify incompatible requirements for the type of the initial context item", "XPTY0004");
                    }
                }
            }
            gcr.addRequiredItemType(requiredType);
        } else {
            exec.setGlobalContextRequirement(req);
        }
    }

    /**
     * Parse a function declaration.
     * <p>Syntax:<br/>
     * <"declare" "function"> QName "(" ParamList? ")" ("as" SequenceType)?
     * (EnclosedExpr | "external")
     * </p>
     * <p>On entry, the "declare function" has already been recognized</p>
     *
     * @param annotations the list of annotations that have been encountered for this function declaration
     * @throws XPathException if a syntax error is found
     */

    public void parseFunctionDeclaration(AnnotationList annotations) throws XPathException {

        if (annotations.includes(SAXON_MEMO_FUNCTION)) {
            if (env.getConfiguration().getEditionCode().equals("HE")) {
                warning("saxon:memo-function option is ignored under Saxon-HE");
            } else {
                memoFunction = true;
            }
        }

        // the next token should be the < QNAME "("> pair
        int offset = t.currentTokenStartOffset;
        t.setState(Tokenizer.DEFAULT_STATE);
        nextToken();
        expect(Token.FUNCTION);

        String uri;
        StructuredQName qName;
        if (t.currentTokenValue.indexOf(':') < 0) {
            uri = env.getDefaultFunctionNamespace();
            qName = new StructuredQName("", uri, t.currentTokenValue);
        } else {
            qName = makeStructuredQName(t.currentTokenValue, "");
            uri = qName.getURI();
        }

        if (uri.isEmpty()) {
            grumble("The function must be in a namespace", "XQST0060");
        }

        String moduleURI = ((QueryModule) env).getModuleNamespace();
        if (moduleURI != null && !moduleURI.equals(uri)) {
            grumble("A function in a library module must be in the module namespace", "XQST0048");
        }

        if (isReservedInQuery(uri)) {
            grumble("The function name " + t.currentTokenValue + " is in a reserved namespace", "XQST0045");
        }

        XQueryFunction func = new XQueryFunction();
        func.setFunctionName(qName);
        func.setResultType(SequenceType.ANY_SEQUENCE);
        func.setBody(null);
        Location loc = makeNestedLocation(env.getContainingLocation(), t.getLineNumber(offset), t.getColumnNumber(offset), null);
        func.setLocation(loc);
        func.setStaticContext((QueryModule) env);
        func.setMemoFunction(memoFunction);
        if (annotations != null) {
            func.setUpdating(annotations.includes(Annotation.UPDATING));
            func.setAnnotations(annotations);
        }

        nextToken();
        HashSet<StructuredQName> paramNames = new HashSet<StructuredQName>(8);
        if (t.currentToken != Token.RPAR) {
            while (true) {
                //     ParamList   ::=     Param ("," Param)*
                //     Param       ::=     "$" VarName  TypeDeclaration?
                expect(Token.DOLLAR);
                nextToken();
                expect(Token.NAME);
                StructuredQName argQName = makeStructuredQName(t.currentTokenValue, "");
                if (paramNames.contains(argQName)) {
                    grumble("Duplicate parameter name " + Err.wrap(t.currentTokenValue, Err.VARIABLE), "XQST0039");
                }
                paramNames.add(argQName);
                SequenceType paramType = SequenceType.ANY_SEQUENCE;
                nextToken();
                if (t.currentToken == Token.AS) {
                    nextToken();
                    paramType = parseSequenceType();
                }

                UserFunctionParameter arg = new UserFunctionParameter();
                arg.setRequiredType(paramType);
                arg.setVariableQName(argQName);
                func.addArgument(arg);
                declareRangeVariable(arg);
                if (t.currentToken == Token.RPAR) {
                    break;
                } else if (t.currentToken == Token.COMMA) {
                    nextToken();
                } else {
                    grumble("Expected ',' or ')' after function argument, found '" +
                            Token.tokens[t.currentToken] + '\'');
                }
            }
        }
        t.setState(Tokenizer.BARE_NAME_STATE);
        nextToken();
        if (t.currentToken == Token.AS) {
            if (func.isUpdating()) {
                grumble("Cannot specify a return type for an updating function", "XUST0028");
            }
            t.setState(Tokenizer.SEQUENCE_TYPE_STATE);
            nextToken();
            func.setResultType(parseSequenceType());
        }
        if (isKeyword("external")) {
            grumble("Saxon does not allow external functions to be declared", "XPST0017");
            // TODO: allow this, provided the function is available (and take note of "nondeterministic")
        } else {
            expect(Token.LCURLY);
            t.setState(Tokenizer.DEFAULT_STATE);
            nextToken();
            if (t.currentToken == Token.RCURLY) {
                Expression body = Literal.makeEmptySequence();
                body.setRetainedStaticContext(env.makeRetainedStaticContext());
                setLocation(body);
                func.setBody(body);
            } else {
                Expression body = parseExpression();
                func.setBody(body);
                ExpressionTool.setDeepRetainedStaticContext(body, env.makeRetainedStaticContext());
            }
            expect(Token.RCURLY);
            lookAhead();  // must be done manually after an RCURLY
        }
        UserFunctionParameter[] params = func.getParameterDefinitions();
        //noinspection UnusedDeclaration
        for (UserFunctionParameter param : params) {
            undeclareRangeVariable();
        }
        t.setState(Tokenizer.DEFAULT_STATE);
        nextToken();

        QueryModule qenv = (QueryModule) env;

        try {
            qenv.declareFunction(func);
        } catch (XPathException e) {
            grumble(e.getMessage(), e.getErrorCodeQName(), -1);
        }
        memoFunction = false;
    }

    public static StructuredQName SAXON_MEMO_FUNCTION = new StructuredQName("saxon", NamespaceConstant.SAXON, "memo-function");


    /**
     * Parse a type alias declaration. Allowed only in Saxon-PE and higher
     * @throws XPathException if parsing fails
     */

    protected void parseTypeAliasDeclaration() throws XPathException {
        parserExtension.parseTypeAliasDeclaration(this);
    }

    /**
     * Parse an option declaration.
     * <p>Syntax:<br/>
     * <"declare" "option">  QName "string-literal"
     * </p>
     * <p>On entry, the "declare option" has already been recognized</p>
     *
     * @throws XPathException if a syntax error is found
     */

    private void parseOptionDeclaration() throws XPathException {
        nextToken();
        expect(Token.NAME);
        String defaultUri = NamespaceConstant.XQUERY;
        StructuredQName varName = makeStructuredQName(t.currentTokenValue, defaultUri);
        String uri = varName.getURI();

        if (uri.isEmpty()) {
            grumble("The QName identifying an option declaration must be prefixed", "XPST0081");
            return;
        }

        nextToken();
        expect(Token.STRING_LITERAL);
        //String value = URILiteral(t.currentTokenValue).trim();
        String value = unescape(t.currentTokenValue).toString();

        if (uri.equals(NamespaceConstant.OUTPUT)) {
            parseOutputDeclaration(varName, value);
        } else if (uri.equals(NamespaceConstant.SAXON)) {
            String localName = varName.getLocalPart();
            if (localName.equals("output")) {
                setOutputProperty(value);
            } else if (localName.equals("memo-function")) {
                value = value.trim();
                if (value.equals("true")) {
                    memoFunction = true;
                    if (env.getConfiguration().getEditionCode().equals("HE")) {
                        warning("saxon:memo-function option is ignored under Saxon-HE");
                    }
                } else if (value.equals("false")) {
                    memoFunction = false;
                } else {
                    warning("Value of saxon:memo-function must be 'true' or 'false'");
                }
            } else if (localName.equals("allow-cycles")) {
                warning("Value of saxon:allow-cycles is ignored");
            } else {
                warning("Unknown Saxon option declaration: " + varName.getDisplayName());
            }
        }
        nextToken();
    }

    protected void parseOutputDeclaration(StructuredQName varName, String value) throws XPathException {

        if (!((QueryModule) env).isMainModule()) {
            grumble("Output declarations must not appear in a library module", "XQST0108");
        }
        String localName = varName.getLocalPart();
        if (outputPropertiesSeen.contains(varName)) {
            grumble("Duplicate output declaration (" + varName + ")", "XQST0110");
        }
        outputPropertiesSeen.add(varName);
        if (localName.equals("parameter-document")) {
            Source source;
            try {
                source = env.getConfiguration().getURIResolver().resolve(value, env.getStaticBaseURI());
            } catch (TransformerException e) {
                throw XPathException.makeXPathException(e);
            }
            ParseOptions options = new ParseOptions();
            options.setSchemaValidationMode(Validation.LAX);
            TreeInfo doc = env.getConfiguration().buildDocumentTree(source);
            SerializationParamsHandler ph = new SerializationParamsHandler();
            ph.setSerializationParams(doc.getRootNode());
            // TODO: handle override semantics
            Properties baseProps = ph.getSerializationProperties();
            Properties props = getExecutable().getDefaultOutputProperties();
            for (String prop : baseProps.stringPropertyNames()) {
                props.setProperty(prop, baseProps.getProperty(prop));
            }
            CharacterMap characterMap = ph.getCharacterMap();
            if (characterMap != null) {
                CharacterMapIndex index = new CharacterMapIndex();
                index.putCharacterMap(characterMap.getName(), characterMap);
                getExecutable().setCharacterMapIndex(index);
                props.setProperty(SaxonOutputKeys.USE_CHARACTER_MAPS, characterMap.getName().getClarkName());
            }
        } else if (localName.equals("use-character-maps")) {
            grumble("Output declaration use-character-maps cannot appear except in a parameter file", "XQST0109");
        } else {
            Properties props = getExecutable().getDefaultOutputProperties();
            try {
                ResultDocument.setSerializationProperty(props,
                        "", localName,
                        value,
                        env.getNamespaceResolver(),
                        false,
                        env.getConfiguration());
            } catch (XPathException e) {
                throw e;
            }
        }
    }

    /**
     * Handle a saxon:output option declaration. Format:
     * declare option saxon:output "indent = yes"
     *
     * @param property a property name=value pair. The name is the name of a serialization
     *                 property, potentially as a prefixed QName; the value is the value of the property. A warning
     *                 is output for unrecognized properties or values
     */

    private void setOutputProperty(/*@NotNull*/ String property) {
        int equals = property.indexOf("=");
        if (equals < 0) {
            badOutputProperty("no equals sign");
        } else if (equals == 0) {
            badOutputProperty("starts with '=");
        }
        String keyword = Whitespace.trim(property.substring(0, equals));
        String value = equals == property.length() - 1 ? "" : Whitespace.trim(property.substring(equals + 1));

        Properties props = getExecutable().getDefaultOutputProperties();
        try {
            assert keyword != null;
            StructuredQName name = makeStructuredQName(keyword, "");
            String lname = name.getLocalPart();
            String uri = name.getURI();
            ResultDocument.setSerializationProperty(props,
                uri, lname,
                value,
                env.getNamespaceResolver(),
                false,
                env.getConfiguration());
        } catch (XPathException e) {
            badOutputProperty(e.getMessage());
        }
    }

    private void badOutputProperty(String s) {
        try {
            warning("Invalid serialization property (" + s + ")");
        } catch (XPathException staticError) {
            //
        }
    }

    /**
     * Parse a FLWOR expression. This replaces the XPath "for" expression.
     * Full syntax:
     * <p/>
     * [41] FLWORExpr ::=  (ForClause  | LetClause)+
     * WhereClause? OrderByClause?
     * "return" ExprSingle
     * [42] ForClause ::=  <"for" "$"> VarName TypeDeclaration? PositionalVar? "in" ExprSingle
     * ("," "$" VarName TypeDeclaration? PositionalVar? "in" ExprSingle)*
     * [43] PositionalVar  ::= "at" "$" VarName
     * [44] LetClause ::= <"let" "$"> VarName TypeDeclaration? ":=" ExprSingle
     * ("," "$" VarName TypeDeclaration? ":=" ExprSingle)*
     * [45] WhereClause  ::= "where" Expr
     * [46] OrderByClause ::= (<"order" "by"> | <"stable" "order" "by">) OrderSpecList
     * [47] OrderSpecList ::= OrderSpec  ("," OrderSpec)*
     * [48] OrderSpec     ::=     ExprSingle  OrderModifier
     * [49] OrderModifier ::= ("ascending" | "descending")?
     * (<"empty" "greatest"> | <"empty" "least">)?
     * ("collation" StringLiteral)?
     * </p>
     *
     * @return the resulting subexpression
     * @throws XPathException if any error is encountered
     */

    /*@Nullable*/
    protected Expression parseFLWORExpression() throws XPathException {
        FLWORExpression flwor = new FLWORExpression();
        int exprOffset = t.currentTokenStartOffset;
        List<Clause> clauseList = new ArrayList<Clause>(4);
        boolean foundOrderBy = false;
        boolean foundWhere = false;
        while (true) {
            int offset = t.currentTokenStartOffset;
            if (t.currentToken == Token.FOR) {
                parseForClause(flwor, clauseList);
            } else if (t.currentToken == Token.LET) {
                parseLetClause(flwor, clauseList);
            } else if (t.currentToken == Token.COUNT) {
                parseCountClause(clauseList);
            } else if (t.currentToken == Token.GROUP_BY) {
                parseGroupByClause(flwor, clauseList);
            } else if (t.currentToken == Token.FOR_TUMBLING || t.currentToken == Token.FOR_SLIDING) {
                parseWindowClause(flwor, clauseList);
            } else if (t.currentToken == Token.WHERE || isKeyword("where")) {
                nextToken();
                Expression condition = parseExprSingle();
                WhereClause clause = new WhereClause(flwor, condition);
                clause.setRepeated(containsLoopingClause(clauseList));
                clauseList.add(clause);
                foundWhere = true;
            } else if (isKeyword("stable") || isKeyword("order")) {
                // we read the "stable" keyword but ignore it; Saxon ordering is always stable
                if (isKeyword("stable")) {
                    nextToken();
                    if (!isKeyword("order")) {
                        grumble("'stable' must be followed by 'order by'");
                    }
                }
                foundOrderBy = true;
                TupleExpression tupleExpression = new TupleExpression();
                List<LocalVariableReference> vars = new ArrayList<LocalVariableReference>();
                for (Clause c : clauseList) {
                    for (LocalVariableBinding b : c.getRangeVariables()) {
                        vars.add(new LocalVariableReference(b));
                    }
                }
                tupleExpression.setVariables(vars);
                List sortSpecList;
                t.setState(Tokenizer.BARE_NAME_STATE);
                nextToken();
                if (!isKeyword("by")) {
                    grumble("'order' must be followed by 'by'");
                }
                t.setState(Tokenizer.DEFAULT_STATE);
                nextToken();
                sortSpecList = parseSortDefinition();
                SortKeyDefinition[] keys = new SortKeyDefinition[sortSpecList.size()];
                for (int i = 0; i < keys.length; i++) {
                    SortSpec spec = (SortSpec) sortSpecList.get(i);
                    SortKeyDefinition key = new SortKeyDefinition();
                    key.setSortKey(((SortSpec) sortSpecList.get(i)).sortKey, false);
                    key.setOrder(new StringLiteral(spec.ascending ? "ascending" : "descending"));
                    key.setEmptyLeast(spec.emptyLeast);

                    if (spec.collation != null) {
                        final StringCollator comparator = env.getConfiguration().getCollation(spec.collation);
                        if (comparator == null) {
                            grumble("Unknown collation '" + spec.collation + '\'', "XQST0076");
                        }
                        key.setCollation(comparator);
                    }
                    keys[i] = key;
                }
                OrderByClause clause = new OrderByClause(flwor, keys, tupleExpression);
                clause.setRepeated(containsLoopingClause(clauseList));
                clauseList.add(clause);
            } else {
                break;
            }
            setLocation(clauseList.get(clauseList.size() - 1), offset);
        }

        int returnOffset = t.currentTokenStartOffset;
        expect(Token.RETURN);
        t.setState(Tokenizer.DEFAULT_STATE);
        nextToken();
        Expression returnExpression = parseExprSingle();
        returnExpression = makeTracer(returnOffset, returnExpression, LocationKind.RETURN_EXPRESSION, null);

        // undeclare all the range variables

        for (int i = clauseList.size() - 1; i >= 0; i--) {
            Clause clause = clauseList.get(i);
            for (int n = 0; n < clause.getRangeVariables().length; n++) {
                undeclareRangeVariable();
            }
        }

        if (codeInjector != null) {
            List<Clause> expandedList = new ArrayList<Clause>(clauseList.size() * 2);
            expandedList.add(clauseList.get(0));
            for (int i = 1; i < clauseList.size(); i++) {
                Clause extra = codeInjector.injectClause(
                        clauseList.get(i - 1),
                        env
                );
                if (extra != null) {
                    expandedList.add(extra);
                }
                expandedList.add(clauseList.get(i));
            }
            Clause extra = codeInjector.injectClause(
                    clauseList.get(clauseList.size() - 1), env);
            if (extra != null) {
                expandedList.add(extra);
            }
            clauseList = expandedList;
        }

        flwor.init(clauseList, returnExpression);
        setLocation(flwor, exprOffset);
        return flwor;

    }

    /**
     * Make a LetExpression. This returns an ordinary LetExpression if tracing is off, and an EagerLetExpression
     * if tracing is on. This is so that trace events occur in an order that the user can follow.
     *
     * @return the constructed "let" expression
     */

    /*@NotNull*/
    protected LetExpression makeLetExpression() {
        if (env.getConfiguration().isCompileWithTracing()) {
            return new EagerLetExpression();
        } else {
            return new LetExpression();
        }
    }

    protected static boolean containsLoopingClause(List<Clause> clauseList) {
        for (Clause c : clauseList) {
            if (FLWORExpression.isLoopingClause(c)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parse a ForClause.
     * <p/>
     * [42] ForClause ::=  "for" ForBinding ("," ForBinding)*
     * [42a] ForBinding ::= "$" VarName TypeDeclaration? ("allowing" "empty")? PositionalVar? "in" ExprSingle
     * </p>
     *
     * @param clauseList - the components of the parsed ForClause are appended to the
     *                   supplied list
     * @throws XPathException if parsing fails
     */
    private void parseForClause(FLWORExpression flwor, List<Clause> clauseList) throws XPathException {
        boolean first = true;
        do {
            ForClause clause = new ForClause();
            clause.setRepeated(!first || containsLoopingClause(clauseList));
            setLocation(clause, t.currentTokenStartOffset);
            if (first) {
                //clause.offset = t.currentTokenStartOffset;
            }
            clauseList.add(clause);
            nextToken();
            if (first) {
                first = false;
            } else {
                //clause.offset = t.currentTokenStartOffset;
            }
            expect(Token.DOLLAR);
            nextToken();
            expect(Token.NAME);
            StructuredQName varQName = makeStructuredQName(t.currentTokenValue, "");
            SequenceType type = SequenceType.SINGLE_ITEM;
            nextToken();

            boolean explicitType = false;
            if (t.currentToken == Token.AS) {
                explicitType = true;
                nextToken();
                type = parseSequenceType();
            }

            boolean allowingEmpty = false;
            if (isKeyword("allowing")) {
                allowingEmpty = true;
                clause.setAllowingEmpty(true);
                if (!explicitType) {
                    type = SequenceType.OPTIONAL_ITEM;
                }
                nextToken();
                if (!isKeyword("empty")) {
                    grumble("After 'allowing', expected 'empty'");
                }
                nextToken();
            }

            if (explicitType && !allowingEmpty && type.getCardinality() != StaticProperty.EXACTLY_ONE) {
                warning("Occurrence indicator on singleton range variable has no effect");
                type = SequenceType.makeSequenceType(type.getPrimaryType(), StaticProperty.EXACTLY_ONE);
            }

            LocalVariableBinding binding = new LocalVariableBinding(varQName, type);
            clause.setRangeVariable(binding);

            if (isKeyword("at")) {
                nextToken();
                expect(Token.DOLLAR);
                nextToken();
                expect(Token.NAME);
                StructuredQName posQName = makeStructuredQName(t.currentTokenValue, "");
                if (!scanOnly && posQName.equals(varQName)) {
                    grumble("The two variables declared in a single 'for' clause must have different names", "XQST0089");
                }
                LocalVariableBinding pos = new LocalVariableBinding(posQName, SequenceType.SINGLE_INTEGER);
                clause.setPositionVariable(pos);
                nextToken();
            }
            expect(Token.IN);
            nextToken();
            clause.initSequence(flwor, parseExprSingle());
            declareRangeVariable(clause.getRangeVariable());
            if (clause.getPositionVariable() != null) {
                declareRangeVariable(clause.getPositionVariable());
            }
            if (allowingEmpty) {
                checkForClauseAllowingEmpty(flwor, clause);
            }
        } while (t.currentToken == Token.COMMA);
    }

    /**
     * Check a ForClause for an "outer for"
     *
     * @throws net.sf.saxon.trans.XPathException
     *          if invalid
     */

    private void checkForClauseAllowingEmpty(FLWORExpression flwor, ForClause clause) throws XPathException {
        if (!allowXPath30Syntax) {
            grumble("The 'allowing empty' option requires XQuery 3.0");
        }
        SequenceType type = clause.getRangeVariable().getRequiredType();
        if (!Cardinality.allowsZero(type.getCardinality())) {
            warning("When 'allowing empty' is specified, the occurrence indicator on the range variable type should be '?'");
        }
    }

    /**
     * Parse a LetClause.
     * <p/>
     * [44] LetClause ::= <"let" "$"> VarName TypeDeclaration? ":=" ExprSingle
     * ("," "$" VarName TypeDeclaration? ":=" ExprSingle)*
     * </p>
     *
     * @param clauseList - the components of the parsed LetClause are appended to the
     *                   supplied list
     * @throws XPathException if a static error is found
     */
    private void parseLetClause(FLWORExpression flwor, List<Clause> clauseList) throws XPathException {
        boolean first = true;
        do {
            LetClause clause = new LetClause();
            setLocation(clause, t.currentTokenStartOffset);
            clause.setRepeated(containsLoopingClause(clauseList));
            if (first) {
                //clause.offset = t.currentTokenStartOffset;
            }
            clauseList.add(clause);
            nextToken();
            if (first) {
                first = false;
            } else {
                //clause.offset = t.currentTokenStartOffset;
            }
            expect(Token.DOLLAR);
            nextToken();
            expect(Token.NAME);
            String var = t.currentTokenValue;

            StructuredQName varQName = makeStructuredQName(var, "");
            SequenceType type = SequenceType.ANY_SEQUENCE;
            nextToken();
            if (t.currentToken == Token.AS) {
                nextToken();
                type = parseSequenceType();
            }
            LocalVariableBinding v = new LocalVariableBinding(varQName, type);

            expect(Token.ASSIGN);
            nextToken();
            clause.initSequence(flwor, parseExprSingle());
            clause.setRangeVariable(v);
            declareRangeVariable(v);
        } while (t.currentToken == Token.COMMA);
    }

    /**
     * Parse a CountClause.
     * <p/>
     * [44] CountClause ::= <"count" "$"> VarName
     * </p>
     *
     * @param clauseList - the components of the parsed CountClause are appended to the
     *                   supplied list
     * @throws XPathException in the event of a syntax error
     */
    private void parseCountClause(List<Clause> clauseList) throws XPathException {
        do {
            CountClause clause = new CountClause();
            setLocation(clause, t.currentTokenStartOffset);
            clause.setRepeated(containsLoopingClause(clauseList));
            clauseList.add(clause);
            nextToken();
            expect(Token.DOLLAR);
            nextToken();
            expect(Token.NAME);
            String var = t.currentTokenValue;

            StructuredQName varQName = makeStructuredQName(var, "");
            SequenceType type = SequenceType.ANY_SEQUENCE;
            nextToken();
            LocalVariableBinding v = new LocalVariableBinding(varQName, type);
            clause.setRangeVariable(v);
            declareRangeVariable(v);
        } while (t.currentToken == Token.COMMA);
    }

    /**
     * Parse a Group By clause.
     * Handles the full XQuery 3.0 syntax:
     * "group by" ($varname ["collation" URILiteral]) [,...]
     * @param clauseList the list of clauses of the FLWOR expression, to which this clause is added
     * @throws XPathException if there is a syntax error
     *
     */
    private void parseGroupByClause(FLWORExpression flwor, List<Clause> clauseList) throws XPathException {
        GroupByClause clause = new GroupByClause(env.getConfiguration());
        setLocation(clause, t.currentTokenStartOffset);
        clause.setRepeated(containsLoopingClause(clauseList));
        List<StructuredQName> variableNames = new ArrayList<StructuredQName>();
        List<String> collations = new ArrayList<String>();
        nextToken();
        while (true) {
            StructuredQName varQName = readVariableName();

            if (t.currentToken == Token.ASSIGN) {
                LetClause letClause = new LetClause();

                clauseList.add(letClause);
                nextToken();

                SequenceType type = SequenceType.ANY_SEQUENCE;
                if (t.currentToken == Token.AS) {
                    nextToken();
                    type = parseSequenceType();
                }
                LocalVariableBinding v = new LocalVariableBinding(varQName, type);

                letClause.initSequence(flwor, parseExprSingle());
                letClause.setRangeVariable(v);
                declareRangeVariable(v);
            }
            variableNames.add(varQName);
            if (isKeyword("collation")) {
                nextToken();
                expect(Token.STRING_LITERAL);
                collations.add(t.currentTokenValue);
                nextToken();
            } else {
                collations.add(env.getDefaultCollationName());
            }
            if (t.currentToken == Token.COMMA) {
                nextToken();
            } else {
                break;
            }
        }
        // Each of the variable names acts both as a variable reference (for a variable in the pre-grouping stream)
        // and a variable declaration (for a variable in the post-grouping stream).
        TupleExpression groupingTupleExpr = new TupleExpression();
        TupleExpression retainedTupleExpr = new TupleExpression();
        List<LocalVariableReference> groupingRefs = new ArrayList<LocalVariableReference>();
        List<LocalVariableReference> retainedRefs = new ArrayList<LocalVariableReference>();
        List<LocalVariableBinding> groupedBindings = new ArrayList<LocalVariableBinding>();
        for (StructuredQName q : variableNames) {
            boolean found = false;
            search:
            for (int i = clauseList.size() - 1; i >= 0; i--) {
                for (LocalVariableBinding b : clauseList.get(i).getRangeVariables()) {
                    if (q.equals(b.getVariableQName())) {
                        groupedBindings.add(b);
                        groupingRefs.add(new LocalVariableReference(b));
                        found = true;
                        break search;
                    }
                }
            }
            if (!found) {
                grumble("The grouping variable " + q.getDisplayName() + " must be the name of a variable bound earlier in the FLWOR expression",
                        "XQST0094");
            }
        }
        groupingTupleExpr.setVariables(groupingRefs);
        clause.initGroupingTupleExpression(flwor, groupingTupleExpr);

        List<LocalVariableBinding> ungroupedBindings = new ArrayList<LocalVariableBinding>();
        for (int i = clauseList.size() - 1; i >= 0; i--) {
            for (LocalVariableBinding b : clauseList.get(i).getRangeVariables()) {
                if (!groupedBindings.contains(b)) {
                    ungroupedBindings.add(b);
                    retainedRefs.add(new LocalVariableReference(b));
                }
            }
        }

        retainedTupleExpr.setVariables(retainedRefs);
        clause.initRetainedTupleExpression(flwor, retainedTupleExpr);

        LocalVariableBinding[] bindings = new LocalVariableBinding[groupedBindings.size() + ungroupedBindings.size()];
        int k = 0;

        for (LocalVariableBinding b : groupedBindings) {
            bindings[k] = new LocalVariableBinding(b.getVariableQName(), b.getRequiredType());
            //declareRangeVariable(bindings[k]);
            k++;
        }

        for (LocalVariableBinding b : ungroupedBindings) {
            ItemType itemType = b.getRequiredType().getPrimaryType();
            bindings[k] = new LocalVariableBinding(b.getVariableQName(),
                    SequenceType.makeSequenceType(itemType, StaticProperty.ALLOWS_ZERO_OR_MORE));
            //declareRangeVariable(bindings[k]);
            k++;
        }

        for (int z = groupedBindings.size(); z < bindings.length; z++) {
            declareRangeVariable(bindings[z]);
        }
        for (int z = 0; z < groupedBindings.size(); z++) {
            declareRangeVariable(bindings[z]);
        }

        clause.setVariableBindings(bindings);
        GenericAtomicComparer[] comparers = new GenericAtomicComparer[collations.size()];
        XPathContext context = env.makeEarlyEvaluationContext();
        for (int i = 0; i < comparers.length; i++) {
            StringCollator coll = env.getConfiguration().getCollation(collations.get(i));
            comparers[i] = (GenericAtomicComparer) GenericAtomicComparer.makeAtomicComparer(
                    BuiltInAtomicType.ANY_ATOMIC, BuiltInAtomicType.ANY_ATOMIC, coll, context);
        }
        clause.setComparers(comparers);
        clauseList.add(clause);
    }

    private StructuredQName readVariableName() throws XPathException {
        expect(Token.DOLLAR);
        nextToken();
        expect(Token.NAME);
        String name = t.currentTokenValue;
        nextToken();
        return makeStructuredQName(name, "");
    }

    /**
     * Parse a tumbling or sliding window clause.
     * @param clauseList the list of clauses of the FLWOR expression, to which this clause is aded
     * @throws XPathException if there is a syntax error
     *
     */
    private void parseWindowClause(FLWORExpression flwor, List<Clause> clauseList) throws XPathException {
        WindowClause clause = new WindowClause();
        setLocation(clause, t.currentTokenStartOffset);
        clause.setRepeated(containsLoopingClause(clauseList));
        clause.setIsSlidingWindow(t.currentToken == Token.FOR_SLIDING);
        nextToken();
        if (!isKeyword("window")) {
            grumble("after 'sliding' or 'tumbling', expected 'window', but found " + currentTokenDisplay());
        }
        nextToken();
        StructuredQName windowVarName = readVariableName();
        SequenceType windowType = SequenceType.ANY_SEQUENCE;
        if (t.currentToken == Token.AS) {
            nextToken();
            windowType = parseSequenceType();
        }

        LocalVariableBinding windowVar = new LocalVariableBinding(windowVarName, windowType);
        clause.setVariableBinding(WindowClause.WINDOW_VAR, windowVar);

        // We can't assume that all the items in the input sequence belong to the item type of the windows: test case SlidingWindowExpr507
        SequenceType windowItemTypeMandatory = SequenceType.SINGLE_ITEM;
        SequenceType windowItemTypeOptional = SequenceType.OPTIONAL_ITEM;

        expect(Token.IN);
        nextToken();
        clause.initSequence(flwor, parseExprSingle());
        if (!isKeyword("start")) {
            grumble("in window clause, expected 'start', but found " + currentTokenDisplay());
        }
        t.setState(Tokenizer.BARE_NAME_STATE);
        nextToken();
        if (t.currentToken == Token.DOLLAR) {
            LocalVariableBinding startItemVar = new LocalVariableBinding(readVariableName(), windowItemTypeMandatory);
            clause.setVariableBinding(WindowClause.START_ITEM, startItemVar);
            declareRangeVariable(startItemVar);
        }
        if (isKeyword("at")) {
            nextToken();
            LocalVariableBinding startPositionVar = new LocalVariableBinding(readVariableName(), SequenceType.SINGLE_INTEGER);
            clause.setVariableBinding(WindowClause.START_ITEM_POSITION, startPositionVar);
            declareRangeVariable(startPositionVar);
        }
        if (isKeyword("previous")) {
            nextToken();
            LocalVariableBinding startPreviousItemVar = new LocalVariableBinding(readVariableName(), windowItemTypeOptional);
            clause.setVariableBinding(WindowClause.START_PREVIOUS_ITEM, startPreviousItemVar);
            declareRangeVariable(startPreviousItemVar);
        }
        if (isKeyword("next")) {
            nextToken();
            LocalVariableBinding startNextItemVar = new LocalVariableBinding(readVariableName(), windowItemTypeOptional);
            clause.setVariableBinding(WindowClause.START_NEXT_ITEM, startNextItemVar);
            declareRangeVariable(startNextItemVar);
        }
        if (!isKeyword("when")) {
            grumble("Expected 'when' condition for window start, but found " + currentTokenDisplay());
        }
        t.setState(Tokenizer.DEFAULT_STATE);
        nextToken();
        clause.initStartCondition(flwor, parseExprSingle());
        if (isKeyword("only")) {
            clause.setIncludeUnclosedWindows(false);
            nextToken();
        }
        if (isKeyword("end")) {
            t.setState(Tokenizer.BARE_NAME_STATE);
            nextToken();

            if (t.currentToken == Token.DOLLAR) {
                LocalVariableBinding endItemVar = new LocalVariableBinding(readVariableName(), windowItemTypeMandatory);
                clause.setVariableBinding(WindowClause.END_ITEM, endItemVar);
                declareRangeVariable(endItemVar);
            }
            if (isKeyword("at")) {
                nextToken();
                LocalVariableBinding endPositionVar = new LocalVariableBinding(readVariableName(), SequenceType.SINGLE_INTEGER);
                clause.setVariableBinding(WindowClause.END_ITEM_POSITION, endPositionVar);
                declareRangeVariable(endPositionVar);
            }
            if (isKeyword("previous")) {
                nextToken();
                LocalVariableBinding endPreviousItemVar = new LocalVariableBinding(readVariableName(), windowItemTypeOptional);
                clause.setVariableBinding(WindowClause.END_PREVIOUS_ITEM, endPreviousItemVar);
                declareRangeVariable(endPreviousItemVar);
            }
            if (isKeyword("next")) {
                nextToken();
                LocalVariableBinding endNextItemVar = new LocalVariableBinding(readVariableName(), windowItemTypeOptional);
                clause.setVariableBinding(WindowClause.END_NEXT_ITEM, endNextItemVar);
                declareRangeVariable(endNextItemVar);
            }
            if (!isKeyword("when")) {
                grumble("Expected 'when' condition for window end, but found " + currentTokenDisplay());
            }
            t.setState(Tokenizer.DEFAULT_STATE);
            nextToken();
            clause.initEndCondition(flwor, parseExprSingle());
        } else {
            // no "end" condition found
            if (clause.isSlidingWindow()) {
                grumble("A sliding window requires an end condition");
            }
        }
        declareRangeVariable(windowVar);
        clauseList.add(clause);
    }


    /**
     * Make a string-join expression that concatenates the string-values of items in
     * a sequence with intervening spaces. This may be simplified later as a result
     * of type-checking.
     *
     * @param exp the base expression, evaluating to a sequence
     * @param env the static context
     * @return a call on string-join to create a string containing the
     *         representations of the items in the sequence separated by spaces.
     */

    /*@Nullable*/
    public static Expression makeStringJoin(Expression exp, /*@NotNull*/ StaticContext env) throws XPathException {

        exp = Atomizer.makeAtomizer(exp);
        ItemType t = exp.getItemType();
        if (!t.equals(BuiltInAtomicType.STRING) && !t.equals(BuiltInAtomicType.UNTYPED_ATOMIC)) {
            exp = new AtomicSequenceConverter(exp, BuiltInAtomicType.STRING);
            ((AtomicSequenceConverter) exp).allocateConverter(env.getConfiguration(), false);
        }

        if (exp.getCardinality() == StaticProperty.EXACTLY_ONE) {
            return exp;
        } else {
            RetainedStaticContext rsc = new RetainedStaticContext(env);
            Expression fn = SystemFunction.makeCall("string-join", rsc, exp, new StringLiteral(StringValue.SINGLE_SPACE));
            ExpressionTool.copyLocationInfo(exp, fn);
            return fn;
        }
    }

    /**
     * Parse the "order by" clause.
     * [46] OrderByClause ::= (<"order" "by"> | <"stable" "order" "by">) OrderSpecList
     * [47] OrderSpecList ::= OrderSpec  ("," OrderSpec)*
     * [48] OrderSpec     ::=     ExprSingle  OrderModifier
     * [49] OrderModifier ::= ("ascending" | "descending")?
     * (<"empty" "greatest"> | <"empty" "least">)?
     * ("collation" StringLiteral)?
     *
     * @return a list of sort specifications (SortSpec), one per sort key
     * @throws XPathException if parsing fails
     */
    /*@NotNull*/
    private List parseSortDefinition() throws XPathException {
        List<SortSpec> sortSpecList = new ArrayList<SortSpec>(5);
        while (true) {
            SortSpec sortSpec = new SortSpec();
            sortSpec.sortKey = parseExprSingle();
            sortSpec.ascending = true;
            sortSpec.emptyLeast = ((QueryModule) env).isEmptyLeast();
            sortSpec.collation = env.getDefaultCollationName();
            //t.setState(t.BARE_NAME_STATE);
            if (isKeyword("ascending")) {
                nextToken();
            } else if (isKeyword("descending")) {
                sortSpec.ascending = false;
                nextToken();
            }
            if (isKeyword("empty")) {
                nextToken();
                if (isKeyword("greatest")) {
                    sortSpec.emptyLeast = false;
                    nextToken();
                } else if (isKeyword("least")) {
                    sortSpec.emptyLeast = true;
                    nextToken();
                } else {
                    grumble("'empty' must be followed by 'greatest' or 'least'");
                }
            }
            if (isKeyword("collation")) {
                sortSpec.collation = readCollationName();
            }
            sortSpecList.add(sortSpec);
            if (t.currentToken == Token.COMMA) {
                nextToken();
            } else {
                break;
            }
        }
        return sortSpecList;
    }

    protected String readCollationName() throws XPathException {
        nextToken();
        expect(Token.STRING_LITERAL);
        String collationName = uriLiteral(t.currentTokenValue);
        URI collationURI;
        try {
            collationURI = new URI(collationName);
            if (!collationURI.isAbsolute()) {
                URI base = new URI(env.getStaticBaseURI());
                collationURI = base.resolve(collationURI);
                collationName = collationURI.toString();
            }
        } catch (URISyntaxException err) {
            grumble("Collation name '" + collationName + "' is not a valid URI", "XQST0046");
            collationName = NamespaceConstant.CODEPOINT_COLLATION_URI;
        }
        nextToken();
        return collationName;
    }

    private static class SortSpec {
        /*@Nullable*/ public Expression sortKey;
        public boolean ascending;
        public boolean emptyLeast;
        public String collation;
    }

    /**
     * Parse a Typeswitch Expression.
     * This construct is XQuery-only.
     * TypeswitchExpr   ::=
     * "typeswitch" "(" Expr ")"
     * CaseClause+
     * "default" ("$" VarName)? "return" ExprSingle
     * CaseClause   ::=
     * "case" ("$" VarName "as")? SequenceType "return" ExprSingle
     *
     * @throws XPathException if parsing fails
     */

    /*@NotNull*/
    protected Expression parseTypeswitchExpression() throws XPathException {

        // On entry, the "(" has already been read
        int offset = t.currentTokenStartOffset;
        nextToken();
        Expression operand = parseExpression();
        List<List<SequenceType>> types = new ArrayList<List<SequenceType>>(10);
        List<Expression> actions = new ArrayList<Expression>(10);
        expect(Token.RPAR);
        nextToken();

        // The code generated takes the form:
        //    let $zzz := operand return
        //    if ($zzz instance of t1) then action1
        //    else if ($zzz instance of t2) then action2
        //    else default-action
        //
        // If a variable is declared in a case clause or default clause,
        // then "action-n" takes the form
        //    let $v as type := $zzz return action-n

        // we were generating "let $v as type := $zzz return action-n" but this gives a compile time error if
        // there's a case clause that specifies an impossible type.

        LetExpression outerLet = makeLetExpression();
        outerLet.setRequiredType(SequenceType.ANY_SEQUENCE);
        outerLet.setVariableQName(new StructuredQName("zz", NamespaceConstant.SAXON, "zz_typeswitchVar"));
        outerLet.setSequence(operand);

        while (t.currentToken == Token.CASE) {
            int caseOffset = t.currentTokenStartOffset;
            List<SequenceType> typeList;
            Expression action;
            nextToken();
            if (t.currentToken == Token.DOLLAR) {
                nextToken();
                expect(Token.NAME);
                final String var = t.currentTokenValue;
                final StructuredQName varQName = makeStructuredQName(var, "");
                nextToken();
                expect(Token.AS);
                nextToken();
                typeList = parseSequenceTypeList();
                action = makeTracer(caseOffset,
                        parseTypeswitchReturnClause(varQName, outerLet),
                        LocationKind.CASE_EXPRESSION,
                        varQName);
                if (action instanceof TraceExpression) {
                    ((TraceExpression) action).setProperty("type", typeList.get(0).toString());
                }

            } else {
                typeList = parseSequenceTypeList();
                action = makeTracer(caseOffset, parseExprSingle(), LocationKind.CASE_EXPRESSION, null);
                if (action instanceof TraceExpression) {
                    ((TraceExpression) action).setProperty("type", typeList.get(0).toString());
                }
            }
            types.add(typeList);
            actions.add(action);
        }
        if (types.isEmpty()) {
            grumble("At least one case clause is required in a typeswitch");
        }
        expect(Token.DEFAULT);
        final int defaultOffset = t.currentTokenStartOffset;
        nextToken();
        Expression defaultAction;
        if (t.currentToken == Token.DOLLAR) {
            nextToken();
            expect(Token.NAME);
            final String var = t.currentTokenValue;
            final StructuredQName varQName = makeStructuredQName(var, "");
            nextToken();
            expect(Token.RETURN);
            nextToken();
            defaultAction = makeTracer(defaultOffset,
                    parseTypeswitchReturnClause(varQName, outerLet),
                    LocationKind.DEFAULT_EXPRESSION,
                    varQName);
        } else {
            t.treatCurrentAsOperator();
            expect(Token.RETURN);
            nextToken();
            defaultAction = makeTracer(defaultOffset, parseExprSingle(), LocationKind.DEFAULT_EXPRESSION, null);
        }

        Expression lastAction = defaultAction;
        // Note, the ragged "choose" later gets flattened into a single-level choose, saving stack space
        for (int i = types.size() - 1; i >= 0; i--) {
            final LocalVariableReference var = new LocalVariableReference(outerLet);
            setLocation(var);
            Expression ioe = new InstanceOfExpression(var, types.get(i).get(0));
            for (int j = 1; j < types.get(i).size(); j++) {
                ioe = new OrExpression(ioe, new InstanceOfExpression(var.copy(new RebindingMap()), types.get(i).get(j)));
            }
            setLocation(ioe);
            final Expression ife = Choose.makeConditional(ioe, actions.get(i), lastAction);
            setLocation(ife);
            lastAction = ife;
        }
        outerLet.setAction(lastAction);
        return makeTracer(offset, outerLet, LocationKind.TYPESWITCH_EXPRESSION, null);
    }

    /*@NotNull*/
    private List<SequenceType> parseSequenceTypeList() throws XPathException {
        List<SequenceType> typeList = new ArrayList<SequenceType>();
        while (true) {
            SequenceType type = parseSequenceType();
            typeList.add(type);
            t.treatCurrentAsOperator();
            if (t.currentToken == Token.UNION) {
                nextToken();
            } else {
                break;
            }
        }
        expect(Token.RETURN);
        nextToken();
        return typeList;
    }

    /*@NotNull*/
    private Expression parseTypeswitchReturnClause(StructuredQName varQName, LetExpression outerLet)
            throws XPathException {
        Expression action;
//        t.treatCurrentAsOperator();
//        expect(Token.RETURN);
//        nextToken();

        LetExpression innerLet = makeLetExpression();
        innerLet.setRequiredType(SequenceType.ANY_SEQUENCE);
        innerLet.setVariableQName(varQName);
        innerLet.setSequence(new LocalVariableReference(outerLet));

        declareRangeVariable(innerLet);
        action = parseExprSingle();
        undeclareRangeVariable();

        innerLet.setAction(action);
        return innerLet;
//        if (Literal.isEmptySequence(action)) {
//            // The purpose of simplifying this now is that () is allowed in a branch even in XQuery Update when
//            // other branches of the typeswitch are updating.
//            return action;
//        } else {
//            return innerLet;
//        }
    }


    /**
     * Parse a Switch Expression.
     * This construct is XQuery-3.0-only.
     * SwitchExpr ::= "switch" "(" Expr ")" SwitchCaseClause+ "default" "return" ExprSingle
     * SwitchCaseClause ::= ("case" ExprSingle)+ "return" ExprSingle
     */

    /*@NotNull*/
    protected Expression parseSwitchExpression() throws XPathException {

        // On entry, the "(" has already been read
        int offset = t.currentTokenStartOffset;
        nextToken();
        Expression operand = parseExpression();
        expect(Token.RPAR);
        nextToken();

        List<Expression> conditions = new ArrayList<Expression>(10);
        List<Expression> actions = new ArrayList<Expression>(10);

        // The code generated takes the form:
        //    let $zzz := zero-or-one(atomize(operand)) return
        //    choose
        //      when ($zzz eq t1) then action1
        //      when ($zzz eq t2) then action2
        //      when (true) default-action
        //
        // We rely on the optimizer to convert this to a SwitchExpression in the case where all the case clauses
        // are literal constants.

        LetExpression outerLet = makeLetExpression();
        outerLet.setRequiredType(SequenceType.OPTIONAL_ATOMIC);
        outerLet.setVariableQName(new StructuredQName("zz", NamespaceConstant.SAXON, "zz_switchVar"));
        outerLet.setSequence(Atomizer.makeAtomizer(operand));

        while (true) {
            //int caseOffset = t.currentTokenStartOffset;
            List<Expression> caseExpressions = new ArrayList<Expression>(4);

            expect(Token.CASE);
            do {
                nextToken();
                Expression c = parseExprSingle();
                caseExpressions.add(c);
            } while (t.currentToken == Token.CASE);

            expect(Token.RETURN);
            nextToken();

            Expression action = parseExprSingle();
            for (int i = 0; i < caseExpressions.size(); i++) {
                EquivalenceComparison vc = new EquivalenceComparison(
                        new LocalVariableReference(outerLet),
                        Token.FEQ,
                        caseExpressions.get(i));
                if (i == 0) {
                    conditions.add(vc);
                    actions.add(action);
                } else {
                    OrExpression orExpr = new OrExpression(conditions.remove(conditions.size() - 1), vc);
                    conditions.add(orExpr);
                }
                //actions.add((i==0 ? action : action.copy()));
            }

            if (t.currentToken != Token.CASE) {
                break;
            }
        }

        expect(Token.DEFAULT);
        nextToken();
        expect(Token.RETURN);
        nextToken();
        Expression defaultExpr = parseExprSingle();
        conditions.add(Literal.makeLiteral(BooleanValue.TRUE));
        actions.add(defaultExpr);

        Choose choice = new Choose(
                conditions.toArray(new Expression[conditions.size()]),
                actions.toArray(new Expression[conditions.size()]));
        outerLet.setAction(choice);
        return makeTracer(offset, outerLet, LocationKind.SWITCH_EXPRESSION, null);
    }


    /**
     * Parse a Validate Expression.
     * This construct is XQuery-only. The syntax allows:
     * validate mode? { Expr }
     * mode ::= "strict" | "lax"
     *
     * @throws XPathException if parsing fails
     */

    /*@NotNull*/
    protected Expression parseValidateExpression() throws XPathException {
        int offset = t.currentTokenStartOffset;
        int mode = Validation.STRICT;
        boolean foundCurly = false;
        SchemaType requiredType = null;
        ensureSchemaAware("validate expression");
        switch (t.currentToken) {
            case Token.VALIDATE_STRICT:
                mode = Validation.STRICT;
                nextToken();
                break;
            case Token.VALIDATE_LAX:
                mode = Validation.LAX;
                nextToken();
                break;
            case Token.VALIDATE_TYPE:
//                if (XQUERY10.equals(queryVersion)) {
//                    grumble("validate-as-type requires XQuery 3.0");
//                }
                mode = Validation.BY_TYPE;
                nextToken();
                expect(Token.KEYWORD_CURLY);
                if (!NameChecker.isQName(t.currentTokenValue)) {
                    grumble("Schema type name expected after 'validate type");
                }
                requiredType = env.getConfiguration().getSchemaType(
                    makeStructuredQName(t.currentTokenValue, env.getDefaultElementNamespace()));
                if (requiredType == null) {
                    grumble("Unknown schema type " + t.currentTokenValue, "XQST0104");
                }
                foundCurly = true;
                break;
            case Token.KEYWORD_CURLY:
                if (t.currentTokenValue.equals("validate")) {
                    mode = Validation.STRICT;
                } else {
                    throw new AssertionError("shouldn't be parsing a validate expression");
                }
                foundCurly = true;
        }

        if (!foundCurly) {
            expect(Token.LCURLY);
        }
        nextToken();

        Expression exp = parseExpression();
        if (exp instanceof ParentNodeConstructor) {
            ((ParentNodeConstructor) exp).setValidationAction(mode, (mode == Validation.BY_TYPE ? requiredType : null));
        } else {
            // the expression must return a single element or document node. The type-
            // checking machinery can't handle a union type, so we just check that it's
            // a node for now. Because we are reusing XSLT copy-of code, we need
            // an ad-hoc check that the node is of the right kind.

            // below code moved to XQuery-specific path in CopyOf
//            try {
//                RoleLocator role = new RoleLocator(RoleLocator.TYPE_OP, "validate", 0);
//                role.setErrorCode("XQTY0030");
//                setLocation(exp);
//                exp = config.getTypeChecker().staticTypeCheck(exp,
//                        SequenceType.SINGLE_NODE,
//                        false,
//                        role, ExpressionVisitor.make(env, getExecutable()));
//            } catch (XPathException err) {
//                grumble(err.getMessage(), err.getErrorCodeQName(), -1);
//            }
            exp = new CopyOf(exp, true, mode, requiredType, true);
            setLocation(exp);
            ((CopyOf) exp).setRequireDocumentOrElement(true);
        }

        expect(Token.RCURLY);
        t.lookAhead();      // always done manually after an RCURLY
        nextToken();
        return makeTracer(offset, exp, LocationKind.VALIDATE_EXPRESSION, null);
    }

    /**
     * Parse an Extension Expression.
     * Syntax: "(#" QName arbitrary-text "#)")+ "{" expr? "}"
     *
     * @throws XPathException if parsing fails
     */

    /*@NotNull*/
    protected Expression parseExtensionExpression() throws XPathException {
        SchemaType requiredType = null;
        CharSequence trimmed = Whitespace.removeLeadingWhitespace(t.currentTokenValue);
        int c = 0;
        int len = trimmed.length();
        while (c < len && " \t\r\n".indexOf(trimmed.charAt(c)) < 0) {
            c++;
        }
        String qname = trimmed.subSequence(0, c).toString();
        String pragmaContents = "";
        while (c < len && " \t\r\n".indexOf(trimmed.charAt(c)) >= 0) {
            c++;
        }
        if (c < len) {
            pragmaContents = trimmed.subSequence(c, len).toString();
        }

        boolean validateType = false;
        boolean streaming = false;
        String uri;
        String localName;
        if (qname.startsWith("Q{")) {
            StructuredQName sq = parseExtendedQName(qname);
            uri = sq.getURI();
            localName = sq.getLocalPart();
        } else if (!NameChecker.isQName(qname)) {
            grumble("First token in pragma must be a valid QName, terminated by whitespace");
            return new ErrorExpression();
        } else {
            StructuredQName name = makeStructuredQName(qname, "");
            uri = name.getURI();
            localName = name.getLocalPart();
        }
        if (uri.equals(NamespaceConstant.SAXON)) {
            if (localName.equals("validate-type")) {
                if (!env.getConfiguration().isLicensedFeature(Configuration.LicenseFeature.ENTERPRISE_XQUERY)) {
                    warning("Ignoring saxon:validate-type. To use this feature " +
                            "you need the Saxon-EE processor from http://www.saxonica.com/");
                } else {
                    String typeName = Whitespace.trim(pragmaContents);
                    if (!NameChecker.isQName(typeName)) {
                        grumble("Schema type name expected in saxon:validate-type pragma: found " + Err.wrap(typeName));
                    }
                    assert typeName != null;
                    requiredType = env.getConfiguration().getSchemaType(
                        makeStructuredQName(typeName, env.getDefaultElementNamespace()));
                    if (requiredType == null) {
                        grumble("Unknown schema type " + typeName);
                    }
                    validateType = true;
                }
            } else if (localName.equals("stream")) {
                if (!env.getConfiguration().isLicensedFeature(Configuration.LicenseFeature.ENTERPRISE_XQUERY)) {
                    warning("Ignoring saxon:stream. To use this feature " +
                            "you need the Saxon-EE processor from http://www.saxonica.com/");
                } else {
                    streaming = true;
                }
            } else {
                warning("Ignored pragma " + qname + " (unrecognized Saxon pragma)");
            }
        }

        nextToken();
        Expression expr;
        if (t.currentToken == Token.PRAGMA) {
            expr = parseExtensionExpression();
        } else {
            expect(Token.LCURLY);
            nextToken();
            if (t.currentToken == Token.RCURLY) {
                t.lookAhead();      // always done manually after an RCURLY
                nextToken();
                grumble("Unrecognized pragma, with no fallback expression", "XQST0079");
            }
            expr = parseExpression();
            expect(Token.RCURLY);
            t.lookAhead();      // always done manually after an RCURLY
            nextToken();
        }
        if (validateType) {
            if (expr instanceof ParentNodeConstructor) {
                ((ParentNodeConstructor) expr).setValidationAction(Validation.BY_TYPE, requiredType);
                return expr;
            } else if (expr instanceof AttributeCreator) {
                if (!(requiredType instanceof SimpleType)) {
                    grumble("The type used for validating an attribute must be a simple type");
                }

                //noinspection ConstantConditions
                ((AttributeCreator) expr).setSchemaType((SimpleType) requiredType);
                ((AttributeCreator) expr).setValidationAction(Validation.BY_TYPE);
                return expr;
            } else {
                CopyOf copy = new CopyOf(expr, true, Validation.BY_TYPE, requiredType, true);
                copy.setLocation(makeLocation());
                return copy;
            }
        } else if (streaming) {
            return makeSaxonStreamCall(expr);

        } else {
            return expr;
        }
    }

    /**
     * Make a call on saxon:stream - real implementation is in Saxon-EE. This dummy implementation
     * returns the supplied expression unchanged, resulting in a non-streaming implementation.
     * @param exp the argument to the call (the expression to be evaluated in streaming mode)
     * @return the call, modified to use streaming; or the original expression is streaming is not supported.
     * @throws XPathException in the event of failures
     */

    protected Expression makeSaxonStreamCall(Expression exp) throws XPathException {
        return exp;
    }

    /**
     * Convert an EQName in format Q{uri}local to a StructuredQName. We have already established
     * that the string starts with "Q{"
     *
     *
     * @param in      the string to be checked/parsed
     * @return the expanded QName
     * @throws XPathException if the input syntax is incorrect
     */

    /*@NotNull*/
    private static StructuredQName parseExtendedQName(/*@NotNull*/ String in) throws XPathException {
        if (in.length() < 4) {
            invalidExtendedQName(in, "too short");
        }
        int end = in.indexOf('}', 1);
        if (end < 0) {
            invalidExtendedQName(in, "no closing '}'");
        }
        if (end + 1 >= in.length()) {
            invalidExtendedQName(in, "no local name after URI");
        }
        String uri = in.substring(2, end);
        String localName = in.substring(end + 2);
        if (!NameChecker.isValidNCName(localName)) {
            invalidExtendedQName(in, "invalid local name after colon");
        }
        return new StructuredQName("", uri, localName);
    }

    private static void invalidExtendedQName(String in, String msg) throws XPathException {
        throw new XPathException("Invalid EQName " + in + " - " + msg, "XPST0003");
    }

    /**
     * Parse a node constructor. This is allowed only in XQuery. This method handles
     * both the XML-like "direct" constructors, and the XQuery-based "computed"
     * constructors.
     *
     * @return an Expression for evaluating the parsed constructor
     * @throws XPathException in the event of a syntax error.
     */

    /*@NotNull*/
    protected Expression parseConstructor() throws XPathException {
        int offset = t.currentTokenStartOffset;
        switch (t.currentToken) {
            case Token.TAG:
                Expression tag = parsePseudoXML(false);
                lookAhead();
                t.setState(Tokenizer.OPERATOR_STATE);
                nextToken();
                return tag;
            case Token.KEYWORD_CURLY:
                String nodeKind = t.currentTokenValue;
                if (nodeKind.equals("validate")) {
                    grumble("A validate expression is not allowed within a path expression");

                    //if (nodeKind.equals("validate")) {
                    // this allows a validate{} expression to appear as an operand of '/', which the grammar does not allow
                    // return parseValidateExpression();
                } else if (nodeKind.equals("ordered") || nodeKind.equals("unordered")) {
                    // these are currently no-ops in Saxon
                    nextToken();
                    Expression content;
                    if (t.currentToken == Token.RCURLY && allowXPath31Syntax) {
                        content = Literal.makeEmptySequence();
                    } else {
                        content = parseExpression();
                    }
                    expect(Token.RCURLY);
                    lookAhead();  // must be done manually after an RCURLY
                    nextToken();
                    return content;
                } else if (nodeKind.equals("document")) {
                    return parseDocumentConstructor(offset);

                } else if ("element".equals(nodeKind)) {
                    return parseComputedElementConstructor(offset);

                } else if ("attribute".equals(nodeKind)) {
                    return parseComputedAttributeConstructor(offset);

                } else if ("text".equals(nodeKind)) {
                    return parseTextNodeConstructor(offset);

                } else if ("comment".equals(nodeKind)) {
                    return parseCommentConstructor(offset);

                } else if ("processing-instruction".equals(nodeKind)) {
                    return parseProcessingInstructionConstructor(offset);

                } else if ("namespace".equals(nodeKind)) {
                    return parseNamespaceConstructor(offset);

                } else {
                    grumble("Unrecognized node constructor " + t.currentTokenValue + "{}");

                }
            case Token.ELEMENT_QNAME:
                return parseNamedElementConstructor(offset);

            case Token.ATTRIBUTE_QNAME:
                return parseNamedAttributeConstructor(offset);

            case Token.NAMESPACE_QNAME:
                return parseNamedNamespaceConstructor(offset);

            case Token.PI_QNAME:
                return parseNamedProcessingInstructionConstructor(offset);
        }
        return new ErrorExpression();
    }

    /**
     * Parse document constructor: document {...}
     *
     * @param offset the location in the source query
     * @return the document constructor instruction
     * @throws XPathException if parsing fails
     */

    /*@NotNull*/
    private Expression parseDocumentConstructor(int offset) throws XPathException {
        nextToken();
        Expression content = parseExpression();
        expect(Token.RCURLY);
        lookAhead();  // must be done manually after an RCURLY
        nextToken();
        DocumentInstr doc = new DocumentInstr(false, null);
        if (!((QueryModule) env).isPreserveNamespaces()) {
            content = new CopyOf(content, false, Validation.PRESERVE, null, true);
        }
        doc.setValidationAction(((QueryModule) env).getConstructionMode(), null);
        doc.setContentExpression(content);
        setLocation(doc, offset);
        return doc;
    }

    /**
     * Parse an element constructor of the form
     * element {expr} {expr}
     *
     * @param offset location of the expression in the source query
     * @return the compiled instruction
     * @throws XPathException if a static error is found
     */

    /*@NotNull*/
    private Expression parseComputedElementConstructor(int offset) throws XPathException {
        nextToken();
        // get the expression that yields the element name
        Expression name = parseExpression();
        expect(Token.RCURLY);
        lookAhead();  // must be done manually after an RCURLY
        nextToken();
        expect(Token.LCURLY);
        t.setState(Tokenizer.DEFAULT_STATE);
        nextToken();
        Expression content = null;
        if (t.currentToken != Token.RCURLY) {
            // get the expression that yields the element content
            content = parseExpression();
            // if the child expression creates another element,
            // suppress validation, as the parent already takes care of it
            if (content instanceof ElementCreator && ((ElementCreator) content).getSchemaType() == null) {
                // TODO: does this cover all cases? E.g. the child element might match a wildcard in the parent declaration.
                ((ElementCreator) content).setValidationAction(Validation.PRESERVE, null);
            }
            expect(Token.RCURLY);
        }
        lookAhead();  // done manually after an RCURLY
        nextToken();

        Instruction inst;
        if (name instanceof Literal) {
            GroundedValue vName = ((Literal) name).getValue();
            // if element name is supplied as a literal, treat it like a direct element constructor
            NodeName elemName;
            if (vName instanceof StringValue && !(vName instanceof AnyURIValue)) {
                String lex = ((StringValue) vName).getStringValue();
                try {
                    elemName = makeNodeName(lex, true);
                    elemName.obtainFingerprint(env.getConfiguration().getNamePool());
                } catch (XPathException staticError) {
                    String code = staticError.getErrorCodeLocalPart();
                    if ("XPST0008".equals(code) || "XPST0081".equals(code)) {
                        staticError.setErrorCode("XQDY0074");
                    } else if ("XPST0003".equals(code)) {
                        //staticError.setErrorCode("XQDY0074");
                        grumble("Invalid QName in element constructor: " + lex, "XQDY0074", offset);
                        return new ErrorExpression();
                    }
                    staticError.setLocator(makeLocation());
                    staticError.setIsStaticError(false);
                    return new ErrorExpression(staticError);
                } catch (QNameException qerr) {
                    grumble("Invalid QName in element constructor: " + lex, "XQDY0074", offset);
                    return new ErrorExpression();
                }
            } else if (vName instanceof QualifiedNameValue) {
                String uri = ((QualifiedNameValue) vName).getNamespaceURI();
                elemName = new FingerprintedQName("", uri, ((QualifiedNameValue) vName).getLocalName());
                elemName.obtainFingerprint(env.getConfiguration().getNamePool());
            } else {
                grumble("Element name must be either a string or a QName", "XPTY0004", offset);
                return new ErrorExpression();
            }
            inst = new FixedElement(elemName,
                    ((QueryModule) env).getActiveNamespaceCodes(),
                    ((QueryModule) env).isInheritNamespaces(),
                    true, null,
                    ((QueryModule) env).getConstructionMode());
            if (content == null) {
                content = Literal.makeEmptySequence();
            }
            if (!((QueryModule) env).isPreserveNamespaces()) {
                content = new CopyOf(content, false, Validation.PRESERVE, null, true);
            }
            ((FixedElement) inst).setContentExpression(content);
            setLocation(inst, offset);
            //makeContentConstructor(content, (InstructionWithChildren) inst, offset);
            return makeTracer(offset, inst, LocationKind.LITERAL_RESULT_ELEMENT, elemName.getStructuredQName());
        } else {
            // it really is a computed element constructor: save the namespace context
            NamespaceResolver ns = new NamespaceResolverWithDefault(
                    env.getNamespaceResolver(),
                    env.getDefaultElementNamespace());
            inst = new ComputedElement(name, null, null,
                    ((QueryModule) env).getConstructionMode(),
                    ((QueryModule) env).isInheritNamespaces(),
                    true);
            setLocation(inst);
            if (content == null) {
                content = Literal.makeEmptySequence();
            }
            if (!((QueryModule) env).isPreserveNamespaces()) {
                content = new CopyOf(content, false, Validation.PRESERVE, null, true);
            }
            ((ComputedElement) inst).setContentExpression(content);
            setLocation(inst, offset);
            //makeContentConstructor(content, (InstructionWithChildren) inst, offset);
            return makeTracer(offset, inst, StandardNames.XSL_ELEMENT, null);
        }
    }

    /**
     * Parse an element constructor of the form
     * element qname { expr }
     *
     * @param offset the position in the source query
     * @return the compiled instruction
     * @throws XPathException if parsing fails
     */

    private Expression parseNamedElementConstructor(int offset) throws XPathException {
        NodeName nodeName = null;
        try {
            nodeName = makeNodeName(t.currentTokenValue, true);
        } catch (QNameException e) {
            grumble("Invalid QName in element constructor", "XPST0003");
        }
        Expression content = null;
        nextToken();
        if (t.currentToken != Token.RCURLY) {
            content = parseExpression();
            expect(Token.RCURLY);
        }
        lookAhead();  // after an RCURLY
        nextToken();
        FixedElement el2 = new FixedElement(nodeName,
                ((QueryModule) env).getActiveNamespaceCodes(),
                ((QueryModule) env).isInheritNamespaces(),
                true, null,
                ((QueryModule) env).getConstructionMode());
        setLocation(el2, offset);
        if (content == null) {
            content = Literal.makeEmptySequence();
        }
        if (!((QueryModule) env).isPreserveNamespaces()) {
            content = new CopyOf(content, false, Validation.PRESERVE, null, true);
        }
        el2.setContentExpression(content);
        return makeTracer(offset, el2, LocationKind.LITERAL_RESULT_ELEMENT, nodeName.getStructuredQName());
    }

    /**
     * Parse an attribute constructor of the form
     * attribute {expr} {expr}
     *
     * @param offset position of the expression in the input
     * @return the compiled instruction
     * @throws XPathException if a static error is encountered
     */

    /*@NotNull*/
    private Expression parseComputedAttributeConstructor(int offset) throws XPathException {
        nextToken();
        Expression name = parseExpression();
        expect(Token.RCURLY);
        lookAhead();  // must be done manually after an RCURLY
        nextToken();
        expect(Token.LCURLY);
        t.setState(Tokenizer.DEFAULT_STATE);
        nextToken();
        Expression content = null;
        if (t.currentToken != Token.RCURLY) {
            content = parseExpression();
            expect(Token.RCURLY);
        }
        lookAhead();  // after an RCURLY
        nextToken();
        if (name instanceof Literal) {
            GroundedValue vName = ((Literal) name).getValue();
            if (vName instanceof StringValue && !(vName instanceof AnyURIValue)) {
                String lex = ((StringValue) vName).getStringValue();
                if (lex.equals("xmlns") || lex.startsWith("xmlns:")) {
                    grumble("Cannot create a namespace using an attribute constructor", "XQDY0044", offset);
                }
                NodeName attributeName;
                try {
                    attributeName = makeNodeName(lex, false);
                } catch (XPathException staticError) {
                    String code = staticError.getErrorCodeLocalPart();
                    staticError.setLocator(makeLocation());
                    if ("XPST0008".equals(code) || "XPST0081".equals(code)) {
                        staticError.setErrorCode("XQDY0074");
                    } else if ("XPST0003".equals(code)) {
                        grumble("Invalid QName in attribute constructor: " + lex, "XQDY0074", offset);
                        return new ErrorExpression();
                    }
                    throw staticError;
                } catch (QNameException err) {
                    grumble("Invalid QName in attribute constructor: " + lex, "XQDY0074", offset);
                    return new ErrorExpression();
                }
                FixedAttribute fatt = new FixedAttribute(attributeName,
                        Validation.STRIP,
                        null);
                fatt.setRejectDuplicates();
                makeSimpleContent(content, fatt, offset);
                return makeTracer(offset, fatt, StandardNames.XSL_ATTRIBUTE, null);
            } else if (vName instanceof QNameValue) {
                QNameValue qnv = (QNameValue) vName;
                NodeName attributeName = new FingerprintedQName(
                        qnv.getPrefix(), qnv.getNamespaceURI(), qnv.getLocalName());
                attributeName.obtainFingerprint(env.getConfiguration().getNamePool());
                FixedAttribute fatt = new FixedAttribute(attributeName,
                        Validation.STRIP,
                        null);
                fatt.setRejectDuplicates();
                makeSimpleContent(content, fatt, offset);
                return makeTracer(offset, fatt, StandardNames.XSL_ATTRIBUTE, null);
            }
        }
        ComputedAttribute att = new ComputedAttribute(name,
                null,
                env.getNamespaceResolver(),
                Validation.STRIP,
                null,
                true);
        att.setRejectDuplicates();
        makeSimpleContent(content, att, offset);
        return makeTracer(offset, att, StandardNames.XSL_ATTRIBUTE, null);
    }

    /**
     * Parse an attribute constructor of the form
     * attribute name {expr}
     *
     * @param offset position of the expression in the source
     * @return the parsed expression
     * @throws XPathException if a static error is found
     */

    private Expression parseNamedAttributeConstructor(int offset) throws XPathException {
        String warning = null;
        if (t.currentTokenValue.equals("xmlns") || t.currentTokenValue.startsWith("xmlns:")) {
            warning = "Cannot create a namespace declaration using an attribute constructor";
        }
        NodeName attributeName;
        try {
            attributeName = makeNodeName(t.currentTokenValue, false);
        } catch (QNameException e) {
            throw new XPathException(e);
        }
        if (!attributeName.getURI().equals("") && attributeName.getPrefix().equals("")) {
            // This must be because the name was given as Q{uri}local. Invent a prefix.
            attributeName = new FingerprintedQName("_", attributeName.getURI(), attributeName.getLocalPart());
        }
        Expression attContent = null;
        nextToken();
        if (t.currentToken != Token.RCURLY) {
            attContent = parseExpression();
            expect(Token.RCURLY);
        }
        lookAhead();  // after an RCURLY
        nextToken();
        if (warning == null) {
            FixedAttribute att2 = new FixedAttribute(attributeName,
                    Validation.STRIP,
                    null);
            att2.setRejectDuplicates();
            att2.setRetainedStaticContext(env.makeRetainedStaticContext());
            makeSimpleContent(attContent, att2, offset);
            return makeTracer(offset, att2, LocationKind.LITERAL_RESULT_ATTRIBUTE, attributeName.getStructuredQName());
        } else {
            warning(warning);
            return new ErrorExpression(warning, "XQDY0044", false);
        }
    }

    private Expression parseTextNodeConstructor(int offset) throws XPathException {
        nextToken();
        Expression value;
        if (t.currentToken == Token.RCURLY && allowXPath31Syntax) {
            value = Literal.makeEmptySequence();
        } else {
            value = parseExpression();
        }
        expect(Token.RCURLY);
        lookAhead(); // after an RCURLY
        nextToken();
        Expression select = stringify(value, true, env);
        ValueOf vof = new ValueOf(select, false, true);
        setLocation(vof, offset);
        return makeTracer(offset, vof, StandardNames.XSL_TEXT, null);
    }

    private Expression parseCommentConstructor(int offset) throws XPathException {
        nextToken();
        Expression value;
        if (t.currentToken == Token.RCURLY && allowXPath31Syntax) {
            value = Literal.makeEmptySequence();
        } else {
            value = parseExpression();
        }
        expect(Token.RCURLY);
        lookAhead(); // after an RCURLY
        nextToken();
        Comment com = new Comment();
        makeSimpleContent(value, com, offset);
        return makeTracer(offset, com, StandardNames.XSL_COMMENT, null);
    }

    /**
     * Parse a processing instruction constructor of the form
     * processing-instruction {expr} {expr}
     *
     * @param offset the position of the expression in the source query
     * @return the compiled instruction
     * @throws XPathException if parsing fails
     */

    private Expression parseProcessingInstructionConstructor(int offset) throws XPathException {
        nextToken();
        Expression name = parseExpression();
        expect(Token.RCURLY);
        lookAhead();  // must be done manually after an RCURLY
        nextToken();
        expect(Token.LCURLY);
        t.setState(Tokenizer.DEFAULT_STATE);
        nextToken();
        Expression content = null;
        if (t.currentToken != Token.RCURLY) {
            content = parseExpression();
            expect(Token.RCURLY);
        }
        lookAhead();  // after an RCURLY
        nextToken();
        ProcessingInstruction pi = new ProcessingInstruction(name);
        makeSimpleContent(content, pi, offset);
        return makeTracer(offset, pi, StandardNames.XSL_PROCESSING_INSTRUCTION, null);
    }

    /**
     * Parse a processing instruction constructor of the form
     * processing-instruction name { expr }
     *
     * @param offset position of the expression in the source
     * @return the parsed expression
     * @throws XPathException if a static error is found
     */

    private Expression parseNamedProcessingInstructionConstructor(int offset) throws XPathException {
        String target = t.currentTokenValue;
        String warning = null;
        if (target.equalsIgnoreCase("xml")) {
            warning = "A processing instruction must not be named 'xml' in any combination of upper and lower case";
        }
        if (!NameChecker.isValidNCName(target)) {
            grumble("Invalid processing instruction name " + Err.wrap(target));
        }
        Expression piName = new StringLiteral(target);
        Expression piContent = null;
        nextToken();
        if (t.currentToken != Token.RCURLY) {
            piContent = parseExpression();
            expect(Token.RCURLY);
        }
        lookAhead();  // after an RCURLY
        nextToken();
        if (warning == null) {
            ProcessingInstruction pi2 = new ProcessingInstruction(piName);
            makeSimpleContent(piContent, pi2, offset);
            return makeTracer(offset, pi2, StandardNames.XSL_PROCESSING_INSTRUCTION, null);
        } else {
            warning(warning);
            return new ErrorExpression(warning, "XQDY0064", false);
        }
    }

    /**
     * Parse a Try/Catch Expression.
     * This construct is XQuery-3.0 only. The syntax allows:
     * try { Expr } catch NameTest ('|' NameTest)* { Expr }
     * We don't currently implement the CatchVars
     */

    protected Expression parseTryCatchExpression() throws XPathException {
        if (!allowXPath30Syntax) {
            grumble("try/catch requires XQuery 3.0");
        }
        int offset = t.currentTokenStartOffset;
        nextToken();
        Expression tryExpr = parseExpression();
        TryCatch tryCatch = new TryCatch(tryExpr);
        setLocation(tryCatch, offset);
        expect(Token.RCURLY);
        lookAhead();  // must be done manually after an RCURLY
        nextToken();
        boolean foundOneCatch = false;
        List<QNameTest> tests = new ArrayList<QNameTest>();
        while (isKeyword("catch")) {
            tests.clear();
            foundOneCatch = true;
            boolean seenCurly = false;
            do {
                nextToken();
                String tokv = t.currentTokenValue;
                switch (t.currentToken) {
                    case Token.NAME:
                        nextToken();
                        tests.add(makeNameTest(Type.ELEMENT, tokv, false));
                        break;

                    case Token.KEYWORD_CURLY:
                        nextToken();
                        tests.add(makeNameTest(Type.ELEMENT, tokv, false));
                        seenCurly = true;
                        break;

                    case Token.PREFIX:
                        nextToken();
                        tests.add(makeNamespaceTest(Type.ELEMENT, tokv));
                        break;

                    case Token.SUFFIX:
                        nextToken();
                        tokv = t.currentTokenValue;
                        if (t.currentToken == Token.NAME) {
                            // OK
                        } else if (t.currentToken == Token.KEYWORD_CURLY) {
                            // OK
                            seenCurly = true;
                        } else {
                            grumble("Expected name after '*:'");
                        }
                        nextToken();
                        tests.add(makeLocalNameTest(Type.ELEMENT, tokv));
                        break;

                    case Token.STAR:
                    case Token.MULT:
                        nextToken();
                        tests.add(AnyNodeTest.getInstance());
                        break;

                    default:
                        grumble("Unrecognized name test");
                        return null;
                }
            }
            while (t.currentToken == Token.UNION && !t.currentTokenValue.equals("union"));    // must be "|" not "union"!
            if (!seenCurly) {
                expect(Token.LCURLY);
                nextToken();
            }
            QNameTest test;
            if (tests.size() == 1) {
                test = tests.get(0);
            } else {
                test = new UnionQNameTest(tests);
            }
            catchDepth++;
            Expression catchExpr = parseExpression();
            tryCatch.addCatchExpression(test, catchExpr);
            expect(Token.RCURLY);
            lookAhead();  // must be done manually after an RCURLY
            nextToken();
            catchDepth--;
        }
        if (!foundOneCatch) {
            grumble("After try{}, expected 'catch'");
        }

        return tryCatch;
    }


    /**
     * Parse a computed namespace constructor of the form
     * namespace {expr}{expr}
     *
     * @param offset the location of the expression in the query source
     * @return the compiled Namespace instruction
     * @throws XPathException in the event of a syntax error
     */

    /*@NotNull*/
    private Expression parseNamespaceConstructor(int offset) throws XPathException {
        if (!allowXPath30Syntax) {
            grumble("Namespace node constructors require XQuery 3.0");
        }
        nextToken();
        Expression nameExpr = parseExpression();
        expect(Token.RCURLY);
        lookAhead();  // must be done manually after an RCURLY
        nextToken();
        expect(Token.LCURLY);
        t.setState(Tokenizer.DEFAULT_STATE);
        nextToken();
        Expression content = null;
        if (t.currentToken != Token.RCURLY) {
            content = parseExpression();
            expect(Token.RCURLY);
        }
        lookAhead();  // after an RCURLY
        nextToken();

        NamespaceConstructor instr = new NamespaceConstructor(nameExpr);
        setLocation(instr);
        makeSimpleContent(content, instr, offset);
        return makeTracer(offset, instr, StandardNames.XSL_NAMESPACE, null);
    }

    /**
     * Parse a namespace node constructor of the form
     * namespace name { expr }
     *
     * @param offset the location of the expression in the query source
     * @return the compiled instruction
     * @throws XPathException in the event of a syntax error
     */

    /*@NotNull*/
    private Expression parseNamedNamespaceConstructor(int offset) throws XPathException {
        if (!allowXPath30Syntax) {
            grumble("Namespace node constructors require XQuery 3.0");
        }
        String target = t.currentTokenValue;
        if (!NameChecker.isValidNCName(target)) {
            grumble("Invalid namespace prefix " + Err.wrap(target));
        }
        Expression nsName = new StringLiteral(target);
        Expression nsContent = null;
        nextToken();
        if (t.currentToken != Token.RCURLY) {
            nsContent = parseExpression();
            expect(Token.RCURLY);
        }
        lookAhead();  // after an RCURLY
        nextToken();
        NamespaceConstructor instr = new NamespaceConstructor(nsName);
        makeSimpleContent(nsContent, instr, offset);
        return makeTracer(offset, instr, StandardNames.XSL_NAMESPACE, null);
    }


    /**
     * Make the instructions for the children of a node with simple content (attribute, text, PI, etc)
     *
     * @param content the expression making up the simple content
     * @param inst    the skeletal instruction for creating the node
     * @param offset  the character position of this construct within the source query
     * @throws net.sf.saxon.trans.XPathException
     *          if a static error is encountered
     */

    protected void makeSimpleContent(/*@Nullable*/ Expression content, /*@NotNull*/ SimpleNodeConstructor inst, int offset) throws XPathException {
        try {
            if (content == null) {
                inst.setSelect(new StringLiteral(StringValue.EMPTY_STRING));
            } else {
                inst.setSelect(stringify(content, false, env));
            }
            setLocation(inst, offset);
        } catch (XPathException e) {
            grumble(e.getMessage());
        }
    }

    /**
     * Make a sequence of instructions as the children of an element-construction instruction.
     * The idea here is to convert an XPath expression that "pulls" the content into a sequence
     * of XSLT-style instructions that push nodes directly onto the subtree, thus reducing the
     * need for copying of intermediate nodes.
     * @param content The content of the element as an expression
     * @param inst The element-construction instruction (Element or FixedElement)
     * @param offset the character position in the query
     */
//    private void makeContentConstructor(Expression content, InstructionWithChildren inst, int offset) {
//        if (content == null) {
//            inst.setChildren(null);
//        } else if (content instanceof AppendExpression) {
//            List instructions = new ArrayList(10);
//            convertAppendExpression((AppendExpression) content, instructions);
//            inst.setChildren((Expression[]) instructions.toArray(new Expression[instructions.size()]));
//        } else {
//            Expression children[] = {content};
//            inst.setChildren(children);
//        }
//        setLocation(inst, offset);
//    }

    /**
     * Parse pseudo-XML syntax in direct element constructors, comments, CDATA, etc.
     * This is handled by reading single characters from the Tokenizer until the
     * end of the tag (or an enclosed expression) is enountered.
     * This method is also used to read an end tag. Because an end tag is not an
     * expression, the method in this case returns a StringValue containing the
     * contents of the end tag.
     *
     * @param allowEndTag true if the context allows an End Tag to appear here
     * @return an Expression representing the result of parsing the constructor.
     *         If an end tag was read, its contents will be returned as a StringValue.
     * @throws XPathException if parsing fails
     */

    /*@NotNull*/
    private Expression parsePseudoXML(boolean allowEndTag) throws XPathException {
        try {
            Expression exp;
            int offset = t.inputOffset;
            // we're reading raw characters, so we don't want the currentTokenStartOffset
            char c = t.nextChar();
            switch (c) {
                case '!':
                    c = t.nextChar();
                    if (c == '-') {
                        exp = parseCommentConstructor();
                    } else if (c == '[') {
                        grumble("A CDATA section is allowed only in element content");
                        return null;
                        // if CDATA were allowed here, we would have already read it
                    } else {
                        grumble("Expected '--' or '[CDATA[' after '<!'");
                        return null;
                    }
                    break;
                case '?':
                    exp = parsePIConstructor();
                    break;
                case '/':
                    if (allowEndTag) {
                        FastStringBuffer sb = new FastStringBuffer(FastStringBuffer.C16);
                        while (true) {
                            c = t.nextChar();
                            if (c == '>') {
                                break;
                            }
                            sb.append(c);
                        }
                        return new StringLiteral(sb.toString());
                    }
                    grumble("Unmatched XML end tag");
                    return new ErrorExpression();
                default:
                    t.unreadChar();
                    exp = parseDirectElementConstructor(allowEndTag);
            }
            setLocation(exp, offset);
            return exp;
        } catch (StringIndexOutOfBoundsException e) {
            grumble("End of input encountered while parsing direct constructor");
            return new ErrorExpression();
        }
    }

    /**
     * Parse a direct element constructor
     *
     * @param isNested true if this constructor is nested directly as part of the content of another
     *                 element constructor. This has the effect that the child element is not copied, which means
     *                 that namespace inheritance (which only happens during copying) has no effect
     * @return the expression representing the constructor
     * @throws XPathException if a syntax error is found
     * @throws StringIndexOutOfBoundsException
     *                        if the end of input is encountered prematurely
     */

    private Expression parseDirectElementConstructor(boolean isNested) throws XPathException, StringIndexOutOfBoundsException {
        NamePool pool = env.getConfiguration().getNamePool();
        boolean changesContext = false;
        int offset = t.inputOffset - 1;
        // we're reading raw characters, so we don't want the currentTokenStartOffset
        char c;
        FastStringBuffer buff = new FastStringBuffer(FastStringBuffer.C64);
        int namespaceCount = 0;
        while (true) {
            c = t.nextChar();
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == '/' || c == '>') {
                break;
            }
            buff.append(c);
        }
        String elname = buff.toString();
        if (elname.isEmpty()) {
            grumble("Expected element name after '<'");
        }
        //Used LinkedHashMap because it is friendly to retain the order of attributes.
        LinkedHashMap<String, AttributeDetails> attributes = new LinkedHashMap<String, AttributeDetails>(10);
        while (true) {
            // loop through the attributes
            // We must process namespace declaration attributes first;
            // their scope applies to all preceding attribute names and values.
            // But finding the delimiting quote of an attribute value requires the
            // XPath expressions to be parsed, because they may contain nested quotes.
            // So we parse in "scanOnly" mode, which ignores any undeclared namespace
            // prefixes, use the result of this parse to determine the length of the
            // attribute value, save the value, and reparse it when all the namespace
            // declarations have been dealt with.
            c = skipSpaces(c);
            if (c == '/' || c == '>') {
                break;
            }
            int attOffset = t.inputOffset - 1;
            buff.setLength(0);
            // read the attribute name
            while (true) {
                buff.append(c);
                c = t.nextChar();
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == '=') {
                    break;
                }
            }
            String attName = buff.toString();
            if (!NameChecker.isQName(attName)) {
                grumble("Invalid attribute name " + Err.wrap(attName, Err.ATTRIBUTE));
            }
            c = skipSpaces(c);
            expectChar(c, '=');
            c = t.nextChar();
            c = skipSpaces(c);
            char delim = c;
            boolean isNamespace = "xmlns".equals(attName) || attName.startsWith("xmlns:");
            int end;
            if (isNamespace) {
                end = makeNamespaceContent(t.input, t.inputOffset, delim);
                changesContext = true;
            } else {
                Expression avt;
                try {
                    avt = makeAttributeContent(t.input, t.inputOffset, delim, true);
                } catch (XPathException err) {
                    if (!err.hasBeenReported()) {
                        grumble(err.getMessage());
                    }
                    throw err;
                }

                // by convention, this returns the end position when called with scanOnly set
                end = (int) ((Int64Value) ((Literal) avt).getValue()).longValue();

            }
            // save the value with its surrounding quotes
            String val = t.input.substring(t.inputOffset - 1, end + 1);
            // and without
            String rval = t.input.substring(t.inputOffset, end);

            // account for any newlines found in the value
            // (note, subexpressions between curlies will have been parsed using a different tokenizer)
            String tail = val;
            int pos;
            while ((pos = tail.indexOf('\n')) >= 0) {
                t.incrementLineNumber(t.inputOffset - 1 + pos);
                tail = tail.substring(pos + 1);
            }
            t.inputOffset = end + 1;

            if (isNamespace) {
                // Processing follows the resolution of bug 5083: doubled curly braces represent single
                // curly braces, single curly braces are not allowed.
                FastStringBuffer sb = new FastStringBuffer(rval.length());
                boolean prevDelim = false;
                boolean prevOpenCurly = false;
                boolean prevCloseCurly = false;
                for (int i = 0; i < rval.length(); i++) {
                    char n = rval.charAt(i);
                    if (n == delim) {
                        prevDelim = !prevDelim;
                        if (prevDelim) {
                            continue;
                        }
                    }
                    if (n == '{') {
                        prevOpenCurly = !prevOpenCurly;
                        if (prevOpenCurly) {
                            continue;
                        }
                    } else if (prevOpenCurly) {
                        grumble("Namespace must not contain an unescaped opening brace", "XQST0022");
                    }
                    if (n == '}') {
                        prevCloseCurly = !prevCloseCurly;
                        if (prevCloseCurly) {
                            continue;
                        }
                    } else if (prevCloseCurly) {
                        grumble("Namespace must not contain an unescaped closing brace", "XPST0003");
                    }
                    sb.append(n);
                }
                if (prevOpenCurly) {
                    grumble("Namespace must not contain an unescaped opening brace", "XQST0022");
                }
                if (prevCloseCurly) {
                    grumble("Namespace must not contain an unescaped closing brace", "XPST0003");
                }
                rval = sb.toString();
                String uri = uriLiteral(rval);
                if (!StandardURIChecker.getInstance().isValidURI(uri)) {
                    grumble("Namespace must be a valid URI value", "XQST0046");
                }
                String prefix;
                if ("xmlns".equals(attName)) {
                    prefix = "";
                    if (uri.equals(NamespaceConstant.XML)) {
                        grumble("Cannot have the XML namespace as the default namespace", "XQST0070");
                    }
                } else {
                    prefix = attName.substring(6);
                    if (prefix.equals("xml") && !uri.equals(NamespaceConstant.XML)) {
                        grumble("Cannot bind the prefix 'xml' to a namespace other than the XML namespace", "XQST0070");
                    } else if (uri.equals(NamespaceConstant.XML) && !prefix.equals("xml")) {
                        grumble("Cannot bind a prefix other than 'xml' to the XML namespace", "XQST0070");
                    } else if (prefix.equals("xmlns")) {
                        grumble("Cannot use xmlns as a namespace prefix", "XQST0070");
                    }

                    if (uri.isEmpty()) {
                        if (env.getConfiguration().getXMLVersion() == Configuration.XML10) {
                            grumble("Namespace URI must not be empty", "XQST0085");
                        }
                    }
                }
                namespaceCount++;
                ((QueryModule) env).declareActiveNamespace(prefix, uri);
            }
            if (attributes.get(attName) != null) {
                if (isNamespace) {
                    grumble("Duplicate namespace declaration " + attName, "XQST0071", attOffset);
                } else {
                    grumble("Duplicate attribute name " + attName, "XQST0040", attOffset);
                }
            }
//            if (attName.equals("xml:id") && !NameChecker.isValidNCName(rval)) {
//                grumble("Value of xml:id must be a valid NCName", "XQST0082");
//            }
            AttributeDetails a = new AttributeDetails();
            a.value = val;
            a.startOffset = attOffset;
            attributes.put(attName, a);

            // on return, the current character is the closing quote
            c = t.nextChar();
            if (!(c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == '/' || c == '>')) {
                grumble("There must be whitespace after every attribute except the last");
            }
        }
        StructuredQName qName = null;
        if (scanOnly) {
            qName = StandardNames.getStructuredQName(StandardNames.XSL_ELEMENT);  // any name will do
        } else {
            try {
                String[] parts = NameChecker.getQNameParts(elname);
                String namespace = ((QueryModule) env).checkURIForPrefix(parts[0]);
                if (namespace == null) {
                    grumble("Undeclared prefix in element name " + Err.wrap(elname, Err.ELEMENT), "XPST0081", offset);
                }
                qName = new StructuredQName(parts[0], namespace, parts[1]);
            } catch (QNameException e) {
                grumble("Invalid element name " + Err.wrap(elname, Err.ELEMENT), "XPST0003", offset);
            }
        }
        int validationMode = ((QueryModule) env).getConstructionMode();
        FingerprintedQName fqn = new FingerprintedQName(
            qName.getPrefix(), qName.getURI(), qName.getLocalPart(), pool.allocateFingerprint(qName.getURI(), qName.getLocalPart()));
        FixedElement elInst = new FixedElement(fqn,
                ((QueryModule) env).getActiveNamespaceCodes(),
                ((QueryModule) env).isInheritNamespaces(),
                !isNested,
                null,
                validationMode);

        setLocation(elInst, offset);

        List<Expression> contents = new ArrayList<Expression>(10);

        IntHashSet attFingerprints = new IntHashSet(attributes.size());
        // we've checked for duplicate lexical QNames, but not for duplicate expanded-QNames
        for (Map.Entry<String, AttributeDetails> entry : attributes.entrySet()) {
            String attName = entry.getKey();
            AttributeDetails a = entry.getValue();
            String attValue = a.value;
            int attOffset = a.startOffset;

            if ("xmlns".equals(attName) || attName.startsWith("xmlns:")) {
                // do nothing
            } else if (scanOnly) {
                // This means we are prescanning an attribute constructor, and we found a nested attribute
                // constructor, which we have prescanned; we now don't want to re-process the nested attribute
                // constructor because it might depend on things like variables declared in the containing
                // attribute constructor, and in any case we're going to come back to it again later.
                // See test qxmp180
            } else {
                NodeName attributeName = null;
                String attNamespace;
                try {
                    String[] parts = NameChecker.getQNameParts(attName);
                    if (parts[0].isEmpty()) {
                        // attributes don't use the default namespace
                        attNamespace = "";
                    } else {
                        attNamespace = ((QueryModule) env).checkURIForPrefix(parts[0]);
                    }
                    if (attNamespace == null) {
                        grumble("Undeclared prefix in attribute name " +
                                Err.wrap(attName, Err.ATTRIBUTE), "XPST0081", attOffset);
                    }
                    attributeName = new FingerprintedQName(parts[0], attNamespace, parts[1]);
                    int key = attributeName.obtainFingerprint(pool);
                    if (attFingerprints.contains(key)) {
                        grumble("Duplicate expanded attribute name " + attName, "XQST0040", attOffset);
                    }
                    attFingerprints.add(key);
                } catch (QNameException e) {
                    grumble("Invalid attribute name " + Err.wrap(attName, Err.ATTRIBUTE), "XPST0003", attOffset);
                }

                FixedAttribute attInst =
                        new FixedAttribute(attributeName, Validation.STRIP, null);

                setLocation(attInst);
                Expression select;
                try {
                    select = makeAttributeContent(attValue, 1, attValue.charAt(0), false);
                } catch (XPathException err) {
                    err.setIsStaticError(true);
                    throw err;
                }
                attInst.setRetainedStaticContext(env.makeRetainedStaticContext());
                attInst.setSelect(select);
                attInst.setRejectDuplicates();
                setLocation(attInst);
                contents.add(makeTracer(attOffset, attInst, LocationKind.LITERAL_RESULT_ATTRIBUTE,
                        attributeName.getStructuredQName()));
            }
        }
        if (c == '/') {
            // empty element tag
            expectChar(t.nextChar(), '>');
        } else {
            readElementContent(elname, contents);
        }

        Expression[] elk = new Expression[contents.size()];
        for (int i = 0; i < contents.size(); i++) {
            // if the child expression creates another element,
            // suppress validation, as the parent already takes care of it
            if (validationMode != Validation.STRIP) {
                contents.get(i).suppressValidation(validationMode);
            }
            elk[i] = contents.get(i);
        }
        Block block = new Block(elk);
        if (changesContext) {
            block.setRetainedStaticContext(env.makeRetainedStaticContext());
        }
        elInst.setContentExpression(block);

        // reset the in-scope namespaces to what they were before

        for (int n = 0; n < namespaceCount; n++) {
            ((QueryModule) env).undeclareNamespace();
        }

        return makeTracer(offset, elInst, LocationKind.LITERAL_RESULT_ELEMENT, qName);
    }

    /**
     * Parse the content of an attribute in a direct element constructor. This may contain nested expressions
     * within curly braces. A particular problem is that the namespaces used in the expression may not yet be
     * known. This means we need the ability to parse in "scanOnly" mode, where undeclared namespace prefixes
     * are ignored.
     * <p/>
     * The code is based on the XSLT code in {@link AttributeValueTemplate#make}: the main difference is that
     * character entities and built-in entity references need to be recognized and expanded. Also, whitespace
     * needs to be normalized, mimicking the action of an XML parser
     *
     * @param avt        the content of the attribute as written, including variable portions enclosed in curly braces
     * @param start      the character position in the attribute value where parsing should start
     * @param terminator a character that is to be taken as marking the end of the expression
     * @param scanOnly   if the purpose of this parse is simply to locate the end of the attribute value, and not
     *                   to report any semantic errors.
     * @return the expression that will evaluate the content of the attribute
     * @throws XPathException if parsing fails
     */

    /*@Nullable*/
    private Expression makeAttributeContent(/*@NotNull*/ String avt,
                                            int start,
                                            char terminator,
                                            boolean scanOnly) throws XPathException {

        Location loc = makeLocation();
        List<Expression> components = new ArrayList<Expression>(10);

        int i0, i1, i2, i8, i9, len, last;
        last = start;
        len = avt.length();
        while (last < len) {
            i2 = avt.indexOf(terminator, last);
            if (i2 < 0) {
                XPathException e = new XPathException("Attribute constructor is not properly terminated");
                e.setIsStaticError(true);
                throw e;
            }

            i0 = avt.indexOf("{", last);
            i1 = avt.indexOf("{{", last);
            i8 = avt.indexOf("}", last);
            i9 = avt.indexOf("}}", last);

            if ((i0 < 0 || i2 < i0) && (i8 < 0 || i2 < i8)) {   // found end of string
                addStringComponent(components, avt, last, i2);

                // look for doubled quotes, and skip them (for now)
                if (i2 + 1 < avt.length() && avt.charAt(i2 + 1) == terminator) {
                    components.add(new StringLiteral(terminator + ""));
                    last = i2 + 2;
                    //continue;
                } else {
                    last = i2;
                    break;
                }
            } else if (i8 >= 0 && (i0 < 0 || i8 < i0)) {             // found a "}"
                if (i8 != i9) {                        // a "}" that isn't a "}}"
                    XPathException e = new XPathException(
                            "Closing curly brace in attribute value template \"" + avt + "\" must be doubled");
                    e.setIsStaticError(true);
                    throw e;
                }
                addStringComponent(components, avt, last, i8 + 1);
                last = i8 + 2;
            } else if (i1 >= 0 && i1 == i0) {              // found a doubled "{{"
                addStringComponent(components, avt, last, i1 + 1);
                last = i1 + 2;
            } else if (i0 >= 0) {                        // found a single "{"
                if (i0 > last) {
                    addStringComponent(components, avt, last, i0);
                }
                Expression exp;
                XPathParser parser = newParser();
                ((XQueryParser) parser).executable = executable;
                parser.setAllowAbsentExpression(allowXPath31Syntax);
                parser.setScanOnly(scanOnly);
                parser.setRangeVariableStack(rangeVariables);
                parser.setCatchDepth(catchDepth);
                exp = parser.parse(avt, i0 + 1, Token.RCURLY, env);
                if (!scanOnly) {
                    exp = exp.simplify();
                }
                last = parser.getTokenizer().currentTokenStartOffset + 1;
                components.add(makeStringJoin(exp, env));

            } else {
                throw new IllegalStateException("Internal error parsing direct attribute constructor");
            }
        }

        // if this is simply a prescan, return the position of the end of the
        // AVT, so we can parse it properly later

        if (scanOnly) {
            return Literal.makeLiteral(Int64Value.makeIntegerValue(last));
        }

        // is it empty?

        if (components.isEmpty()) {
            return new StringLiteral(StringValue.EMPTY_STRING);
        }

        // is it a single component?

        if (components.size() == 1) {
            return components.get(0);
        }

        // otherwise, return an expression that concatenates the components


        Expression[] args = new Expression[components.size()];
        components.toArray(args);
        RetainedStaticContext rsc = new RetainedStaticContext(env);
        Expression fn = SystemFunction.makeCall("concat", rsc, args);
        fn.setLocation(loc);
        return fn;
        //return visitor.simplify(fn);

    }

    private void addStringComponent(/*@NotNull*/ List<Expression> components, /*@NotNull*/ String avt, int start, int end)
            throws XPathException {
        // analyze fixed text within the value of a direct attribute constructor.
        if (start < end) {
            FastStringBuffer sb = new FastStringBuffer(end - start);
            for (int i = start; i < end; i++) {
                char c = avt.charAt(i);
                switch (c) {
                    case '&': {
                        int semic = avt.indexOf(';', i);
                        if (semic < 0) {
                            grumble("No closing ';' found for entity or character reference");
                        } else {
                            String entity = avt.substring(i + 1, semic);
                            sb.append(new Unescaper(env.getConfiguration().getValidCharacterChecker()).analyzeEntityReference(entity));
                            i = semic;
                        }
                        break;
                    }
                    case '<':
                        grumble("The < character must not appear in attribute content");
                        break;
                    case '\n':
                    case '\t':
                        sb.append(' ');
                        break;
                    case '\r':
                        sb.append(' ');
                        if (i + 1 < end && avt.charAt(i + 1) == '\n') {
                            i++;
                        }
                        break;
                    default:
                        sb.append(c);

                }
            }
            components.add(new StringLiteral(sb.toString()));
        }
    }

    /**
     * Parse the content of an namespace declaration attribute in a direct element constructor. This is simpler
     * than an ordinary attribute because it does not contain nested expressions in curly braces. (But see bug 5083).
     *
     * @param avt        the content of the attribute as written, including variable portions enclosed in curly braces
     * @param start      the character position in the attribute value where parsing should start
     * @param terminator a character that is to be taken as marking the end of the expression
     * @return the position of the end of the URI value
     * @throws XPathException if parsing fails
     */

    private int makeNamespaceContent(/*@NotNull*/ String avt, int start, char terminator) throws XPathException {

        int i2, len, last;
        last = start;
        len = avt.length();
        while (last < len) {
            i2 = avt.indexOf(terminator, last);
            if (i2 < 0) {
                XPathException e = new XPathException("Namespace declaration is not properly terminated");
                e.setIsStaticError(true);
                throw e;
            }

            // look for doubled quotes, and skip them (for now)
            if (i2 + 1 < avt.length() && avt.charAt(i2 + 1) == terminator) {
                last = i2 + 2;
                //continue;
            } else {
                last = i2;
                break;
            }
        }

        // return the position of the end of the literal
        return last;

    }

    /**
     * Read the content of a direct element constructor
     *
     * @param startTag   the element start tag
     * @param components an empty list, to which the expressions comprising the element contents are added
     * @throws XPathException if any static errors are detected
     */
    private void readElementContent(String startTag, /*@NotNull*/ List<Expression> components) throws XPathException {
        try {
            boolean afterEnclosedExpr = false;
            while (true) {
                // read all the components of the element value
                FastStringBuffer text = new FastStringBuffer(FastStringBuffer.C64);
                char c;
                boolean containsEntities = false;
                while (true) {
                    c = t.nextChar();
                    if (c == '<') {
                        // See if we've got a CDATA section
                        if (t.nextChar() == '!') {
                            if (t.nextChar() == '[') {
                                readCDATASection(text);
                                containsEntities = true;
                                continue;
                            } else {
                                t.unreadChar();
                                t.unreadChar();
                            }
                        } else {
                            t.unreadChar();
                        }
                        break;
                    } else if (c == '&') {
                        text.append(readEntityReference());
                        containsEntities = true;
                    } else if (c == '}') {
                        c = t.nextChar();
                        if (c != '}') {
                            grumble("'}' must be written as '}}' within element content");
                        }
                        text.append(c);
                    } else if (c == '{') {
                        c = t.nextChar();
                        if (c != '{') {
                            c = '{';
                            break;
                        }
                        text.append(c);
                    } else {
                        if (!charChecker.matches(c) && !UTF16CharacterSet.isSurrogate(c)) {
                            grumble("Character code " + c + " is not a valid XML character");
                        }
                        text.append(c);
                    }
                }
                if (text.length() > 0 &&
                        (containsEntities |
                                ((QueryModule) env).isPreserveBoundarySpace() ||
                                !Whitespace.isWhite(text))) {
                    ValueOf inst = new ValueOf(new StringLiteral(new StringValue(text.condense())), false, false);
                    setLocation(inst);
                    components.add(inst);
                    afterEnclosedExpr = false;
                }
                if (c == '<') {
                    Expression exp = parsePseudoXML(true);
                    // An end tag can appear here, and is returned as a string value
                    if (exp instanceof StringLiteral) {
                        String endTag = ((StringLiteral) exp).getStringValue();
                        if (Whitespace.isWhitespace(endTag.charAt(0))) {
                            grumble("End tag contains whitespace before the name");
                        }
                        endTag = Whitespace.trim(endTag);
                        if (endTag.equals(startTag)) {
                            return;
                        } else {
                            grumble("End tag </" + endTag +
                                    "> does not match start tag <" + startTag + '>', "XQST0118");
                            // error code allocated by spec bug 11609
                        }
                    } else {
                        components.add(exp);
                    }
                } else {
                    // we read an '{' indicating an enclosed expression
                    if (afterEnclosedExpr) {
                        Expression previousComponent = components.get(components.size() - 1);
                        boolean previousComponentIsNodeTest = true;
                        UType previousItemType = previousComponent.getStaticUType(UType.ANY);
                        previousComponentIsNodeTest = UType.ANY_NODE.subsumes(previousItemType);
                        if (!previousComponentIsNodeTest) {
                            // Add a zero-length text node, to prevent {"a"}{"b"} generating an intervening space
                            // See tests (qxmp132, qxmp261)
                            ValueOf inst = new ValueOf(new StringLiteral(StringValue.EMPTY_STRING), false, false);
                            setLocation(inst);
                            components.add(inst);
                        }
                    }
                    t.unreadChar();
                    t.setState(Tokenizer.DEFAULT_STATE);
                    lookAhead();
                    nextToken();
                    if (t.currentToken == Token.RCURLY && allowXPath31Syntax) {
                        components.add(Literal.makeEmptySequence());
                    } else {
                        Expression exp = parseExpression();
                        if (!((QueryModule) env).isPreserveNamespaces()) {
                            exp = new CopyOf(exp, false, Validation.PRESERVE, null, true);
                        }
                        components.add(exp);
                        expect(Token.RCURLY);
                    }
                    afterEnclosedExpr = true;
                }
            }
        } catch (StringIndexOutOfBoundsException err) {
            grumble("No closing end tag found for direct element constructor");
        }
    }

    /*@Nullable*/
    private Expression parsePIConstructor() throws XPathException {
        try {
            FastStringBuffer pi = new FastStringBuffer(FastStringBuffer.C64);
            int firstSpace = -1;
            while (!pi.toString().endsWith("?>")) {
                char c = t.nextChar();
                if (firstSpace < 0 && " \t\r\n".indexOf(c) >= 0) {
                    firstSpace = pi.length();
                }
                pi.append(c);
            }
            pi.setLength(pi.length() - 2);

            String target;
            String data = "";
            if (firstSpace < 0) {
                // there is no data part
                target = pi.toString();
            } else {
                // trim leading space from the data part, but not trailing space
                target = pi.toString().substring(0, firstSpace);
                firstSpace++;
                while (firstSpace < pi.length() && " \t\r\n".indexOf(pi.charAt(firstSpace)) >= 0) {
                    firstSpace++;
                }
                data = pi.toString().substring(firstSpace);
            }

            if (!NameChecker.isValidNCName(target)) {
                grumble("Invalid processing instruction name " + Err.wrap(target));
            }

            if (target.equalsIgnoreCase("xml")) {
                grumble("A processing instruction must not be named 'xml' in any combination of upper and lower case");
            }

            ProcessingInstruction instruction =
                    new ProcessingInstruction(new StringLiteral(target));
            instruction.setSelect(new StringLiteral(data));
            setLocation(instruction);
            return instruction;
        } catch (StringIndexOutOfBoundsException err) {
            grumble("No closing '?>' found for processing instruction");
            return null;
        }
    }

    private void readCDATASection(/*@NotNull*/ FastStringBuffer cdata) throws XPathException {
        try {
            char c;
            // CDATA section
            c = t.nextChar();
            expectChar(c, 'C');
            c = t.nextChar();
            expectChar(c, 'D');
            c = t.nextChar();
            expectChar(c, 'A');
            c = t.nextChar();
            expectChar(c, 'T');
            c = t.nextChar();
            expectChar(c, 'A');
            c = t.nextChar();
            expectChar(c, '[');
            while (!cdata.toString().endsWith("]]>")) {
                cdata.append(t.nextChar());
            }
            cdata.setLength(cdata.length() - 3);
        } catch (StringIndexOutOfBoundsException err) {
            grumble("No closing ']]>' found for CDATA section");
        }
    }

    /*@Nullable*/
    private Expression parseCommentConstructor() throws XPathException {
        try {
            char c = t.nextChar();
            // XML-like comment
            expectChar(c, '-');
            FastStringBuffer comment = new FastStringBuffer(FastStringBuffer.C256);
            while (!comment.toString().endsWith("--")) {
                comment.append(t.nextChar());
            }
            if (t.nextChar() != '>') {
                grumble("'--' is not permitted in an XML comment");
            }
            CharSequence commentText = comment.subSequence(0, comment.length() - 2);
            Comment instruction = new Comment();
            instruction.setSelect(new StringLiteral(new StringValue(commentText)));
            setLocation(instruction);
            return instruction;
        } catch (StringIndexOutOfBoundsException err) {
            grumble("No closing '-->' found for comment constructor");
            return null;
        }
    }

    /**
     * Convert an expression so it generates a space-separated sequence of strings
     *
     * @param exp           the expression that calculates the content
     * @param noNodeIfEmpty if true, no node is produced when the value of the content
     *                      expression is an empty sequence. If false, the effect of supplying an empty sequence
     *                      is that a node is created whose string-value is a zero-length string. Set to true for
     *                      text node constructors, false for other kinds of node.
     * @param env        the static context
     * @return an expression that computes the content and converts the result to a character string
     * @throws XPathException if parsing fails
     */

    public static Expression stringify(Expression exp, boolean noNodeIfEmpty, StaticContext env) throws XPathException {
        // Compare with XSLLeafNodeConstructor.makeSimpleContentConstructor
        // Fast path if given a string literal
        if (exp instanceof StringLiteral) {
            return exp;
        }
        if (exp.getRetainedStaticContext() == null) {
            exp.setRetainedStaticContext(env.makeRetainedStaticContext());
        }
        // Atomize the result
        exp = Atomizer.makeAtomizer(exp);
        // Convert each atomic value to a string
        exp = new AtomicSequenceConverter(exp, BuiltInAtomicType.STRING);
        //((AtomicSequenceConverter) exp).allocateConverter(config, false);
        // Join the resulting strings with a separator
        exp = SystemFunction.makeCall("string-join", exp.getRetainedStaticContext(), exp, new StringLiteral(StringValue.SINGLE_SPACE));
        assert exp != null;
        if (noNodeIfEmpty) {
            ((StringJoin) ((SystemFunctionCall) exp).getTargetFunction()).setReturnEmptyIfEmpty(true);
        }
        // All that's left for the instruction to do is to construct the right kind of node
        return exp;
    }

    /**
     * Method to make a string literal from a token identified as a string
     * literal. This is trivial in XPath, but in XQuery the method is overridden
     * to identify pseudo-XML character and entity references
     *
     * @param token the string as written (or as returned by the tokenizer)
     * @return The string value of the string literal, after dereferencing entity and
     *         character references
     * @throws XPathException if parsing fails
     */

    @Override
    /*@NotNull*/
    protected Literal makeStringLiteral(/*@NotNull*/ String token) throws XPathException {
        StringLiteral lit;
        if (token.indexOf('&') == -1) {
            lit = new StringLiteral(token);
        } else {
            CharSequence sb = unescape(token);
            lit = new StringLiteral(StringValue.makeStringValue(sb));
        }
        setLocation(lit);
        return lit;
    }

    /**
     * Unescape character references and built-in entity references in a string
     *
     * @param token the input string, which may include XML-style character references or built-in
     *              entity references
     * @return the string with character references and built-in entity references replaced by their expansion
     * @throws XPathException if a malformed character or entity reference is found
     */

    /*@NotNull*/
    protected CharSequence unescape(/*@NotNull*/ String token) throws XPathException {
        return new Unescaper(env.getConfiguration().getValidCharacterChecker()).unescape(token);
    }

    public static class Unescaper {

        private IntPredicate characterChecker;

        public Unescaper(IntPredicate characterChecker) {
            this.characterChecker = characterChecker;
        }
        public CharSequence unescape(String token) throws XPathException {
            FastStringBuffer sb = new FastStringBuffer(token.length());
            for (int i = 0; i < token.length(); i++) {
                char c = token.charAt(i);
                if (c == '&') {
                    int semic = token.indexOf(';', i);
                    if (semic < 0) {
                        throw new XPathException("No closing ';' found for entity or character reference", "XPST0003");
                    } else {
                        String entity = token.substring(i + 1, semic);
                        sb.append(analyzeEntityReference(entity));
                        i = semic;
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb;
        }


        /*@Nullable*/
        private String analyzeEntityReference(/*@NotNull*/ String entity) throws XPathException {
            if ("lt".equals(entity)) {
                return "<";
            } else if ("gt".equals(entity)) {
                return ">";
            } else if ("amp".equals(entity)) {
                return "&";
            } else if ("quot".equals(entity)) {
                return "\"";
            } else if ("apos".equals(entity)) {
                return "'";
            } else if (entity.length() < 2 || entity.charAt(0) != '#') {
                throw new XPathException("invalid character reference &" + entity + ';', "XPST0003");
            } else {
                //entity = entity.toLowerCase();
                return parseCharacterReference(entity);
            }
        }

        /*@Nullable*/
        private String parseCharacterReference(/*@NotNull*/ String entity) throws XPathException {
            int value = 0;
            if (entity.charAt(1) == 'x') {
                if (entity.length() < 3) {
                    throw new XPathException("No hex digits in hexadecimal character reference", "XPST0003");
                }
                entity = entity.toLowerCase();
                for (int i = 2; i < entity.length(); i++) {
                    int digit = "0123456789abcdef".indexOf(entity.charAt(i));
                    if (digit < 0) {
                        throw new XPathException("Invalid hex digit '" + entity.charAt(i) + "' in character reference", "XPST0003");
                    }
                    value = (value * 16) + digit;
                    if (value > UTF16CharacterSet.NONBMP_MAX) {
                        throw new XPathException("Character reference exceeds Unicode codepoint limit", "XQST0090");
                    }
                }
            } else {
                for (int i = 1; i < entity.length(); i++) {
                    int digit = "0123456789".indexOf(entity.charAt(i));
                    if (digit < 0) {
                        throw new XPathException("Invalid digit '" + entity.charAt(i) + "' in decimal character reference", "XPST0003");
                    }
                    value = (value * 10) + digit;
                    if (value > UTF16CharacterSet.NONBMP_MAX) {
                        throw new XPathException("Character reference exceeds Unicode codepoint limit", "XQST0090");
                    }
                }
            }

            net.sf.saxon.z.IntPredicate nc = characterChecker;
            if (!nc.matches(value)) {
                throw new XPathException("Invalid XML character reference x"
                                + Integer.toHexString(value), "XQST0090");
            }
            // following code borrowed from AElfred
            // Check for surrogates: 00000000 0000xxxx yyyyyyyy zzzzzzzz
            //  (1101|10xx|xxyy|yyyy + 1101|11yy|zzzz|zzzz:
            if (value <= 0x0000ffff) {
                // no surrogates needed
                return "" + (char) value;
            } else if (value <= 0x0010ffff) {
                value -= 0x10000;
                // > 16 bits, surrogate needed
                return "" + (char) (0xd800 | (value >> 10))
                        + (char) (0xdc00 | (value & 0x0003ff));
            } else {
                // too big for surrogate
                throw new XPathException("Character reference x" + Integer.toHexString(value) + " is too large", "XQST0090");
            }
        }

    }

    /**
     * Read a pseudo-XML character reference or entity reference.
     *
     * @return The character represented by the character or entity reference. Note
     *         that this is a string rather than a char because a char only accommodates characters
     *         up to 65535.
     * @throws XPathException if the character or entity reference is not well-formed
     */

    /*@Nullable*/
    private String readEntityReference() throws XPathException {
        try {
            FastStringBuffer sb = new FastStringBuffer(FastStringBuffer.C64);
            while (true) {
                char c = t.nextChar();
                if (c == ';') {
                    break;
                }
                sb.append(c);
            }
            String entity = sb.toString();
            return new Unescaper(env.getConfiguration().getValidCharacterChecker()).analyzeEntityReference(entity);
        } catch (StringIndexOutOfBoundsException err) {
            grumble("No closing ';' found for entity or character reference");
        }
        return null;     // to keep the Java compiler happy
    }

    /**
     * Parse a string template: introduced in XQuery 3.1
     */

    @Override
    public Expression parseStringTemplate(boolean complete) throws XPathException {
        int offset = t.currentTokenStartOffset;
        if (!allowXPath31Syntax) {
            throw new XPathException("String constructor expressions require XQuery 3.1");
        }
        if (complete) {
            Expression result = new StringLiteral(t.currentTokenValue);
            setLocation(result, offset);
            t.next();
            return result;
        } else {
            List<Expression> components = new ArrayList<Expression>();
            components.add(new StringLiteral(t.currentTokenValue));
            t.next();
            while (true) {
                if (t.currentToken != Token.STRING_TEMPLATE_FINAL && t.currentToken != Token.STRING_TEMPLATE_INTERMEDIATE) {
                    Expression enclosed = parseExpression();
                    Expression stringJoin = SystemFunction.makeCall(
                        "string-join", env.makeRetainedStaticContext(), enclosed, new StringLiteral(" "));
                    components.add(stringJoin);
                }
                if (t.currentToken == Token.STRING_TEMPLATE_FINAL) {
                    components.add(new StringLiteral(t.currentTokenValue));
                    t.next();
                    Expression[] args = components.toArray(new Expression[components.size()]);
                    Expression result = SystemFunction.makeCall("concat", env.makeRetainedStaticContext(), args);
                    setLocation(result, offset);
                    return result;
                } else if (t.currentToken == Token.STRING_TEMPLATE_INTERMEDIATE) {
                    components.add(new StringLiteral(t.currentTokenValue));
                    t.next();
                } else {
                    grumble("Expected '}`' after enclosed expression in string template");
                }
            }
        }
    }

    /**
     * Handle a URI literal. This is whitespace-normalized as well as being unescaped
     *
     * @param in the string as written
     * @return the URI after unescaping of entity and character references
     *         followed by whitespace normalization
     * @throws net.sf.saxon.trans.XPathException
     *          if an error is found while unescaping the URI
     */

    public String uriLiteral(/*@NotNull*/ String in) throws XPathException {
        return Whitespace.applyWhitespaceNormalization(Whitespace.COLLAPSE, unescape(in)).toString();
    }

    /**
     * Lookahead one token, catching any exception thrown by the tokenizer. This
     * method is only called from the query parser when switching from character-at-a-time
     * mode to tokenizing mode
     *
     * @throws XPathException if parsing fails
     */

    protected void lookAhead() throws XPathException {
        try {
            t.lookAhead();
        } catch (XPathException err) {
            grumble(err.getMessage());
        }
    }

    @Override
    protected boolean atStartOfRelativePath() {
        return t.currentToken == Token.TAG || super.atStartOfRelativePath();
        // "<" after "/" is recognized in XQuery but not in XPath.
    }

    @Override
    protected void testPermittedAxis(byte axis, String errorCode) throws XPathException {
        if (axis == AxisInfo.NAMESPACE && language == XQUERY) {
            grumble("The namespace axis is not available in XQuery", errorCode);
        }
    }

    /**
     * Skip whitespace.
     *
     * @param c the current character
     * @return the first character after any whitespace
     * @throws StringIndexOutOfBoundsException
     *          if the end of input is encountered
     */

    private char skipSpaces(char c) throws StringIndexOutOfBoundsException {
        while (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
            c = t.nextChar();
        }
        return c;
    }

    /**
     * Test whether the current character is the expected character.
     *
     * @param actual   The character that was read
     * @param expected The character that was expected
     * @throws XPathException if they are different
     */

    private void expectChar(char actual, char expected) throws XPathException {
        if (actual != expected) {
            grumble("Expected '" + expected + "', found '" + actual + '\'');
        }
    }

    /**
     * Get the current language (XPath or XQuery)
     */

    /*@NotNull*/
    protected String getLanguage() {
        return "XQuery";
    }

    private static class AttributeDetails {
        String value;
        int startOffset;
    }

    private static class Import {
        /*@Nullable*/ String namespaceURI;
        List<String> locationURIs;
    }
}

