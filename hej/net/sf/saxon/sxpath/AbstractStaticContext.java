////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.sxpath;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.EarlyEvaluationContext;
import net.sf.saxon.expr.PackageData;
import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ExplicitLocation;
import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.expr.parser.RetainedStaticContext;
import net.sf.saxon.functions.FunctionLibrary;
import net.sf.saxon.functions.FunctionLibraryList;
import net.sf.saxon.functions.registry.ConstructorFunctionLibrary;
import net.sf.saxon.functions.registry.XPath20FunctionSet;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.DecimalFormatManager;
import net.sf.saxon.trans.KeyManager;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;

import java.util.HashMap;
import java.util.Map;

/**
 * An abstract and configurable implementation of the StaticContext interface,
 * which defines the static context of an XPath expression.
 * <p/>
 * <p>This class implements those parts of the functionality of a static context
 * that tend to be common to most implementations: simple-valued properties such
 * as base URI and default element namespace; availability of the standard
 * function library; and support for collations.</p>
 */

public abstract class AbstractStaticContext implements StaticContext {

    private String baseURI = null;
    private Configuration config;
    private PackageData packageData;
    private Location containingLocation = ExplicitLocation.UNKNOWN_LOCATION;
    private String defaultCollationName;
    private FunctionLibraryList libraryList = new FunctionLibraryList();
    private String defaultFunctionNamespace = NamespaceConstant.FN;
    private String defaultElementNamespace = NamespaceConstant.NULL;
    private boolean backwardsCompatible = false;
    private int xpathLanguageLevel = 31;
    protected boolean usingDefaultFunctionLibrary;
    private Map<StructuredQName, ItemType> typeAliases = new HashMap<StructuredQName, ItemType>();

    /**
     * Set the Configuration. This is protected so it can be used only by subclasses;
     * the configuration will normally be set at construction time
     *
     * @param config the configuration
     */

    protected void setConfiguration(Configuration config) {
        this.config = config;
        this.defaultCollationName = config.getDefaultCollationName();
    }

    /**
     * Get the system configuration
     */

    public Configuration getConfiguration() {
        return config;
    }

    /**
     * Set data about the unit of compilation (XQuery module, XSLT package)
     *
     * @param packageData the package data
     */
    public void setPackageData(PackageData packageData) {
        this.packageData = packageData;
    }

    /**
     * Get data about the unit of compilation (XQuery module, XSLT package) to which this
     * container belongs
     */
    public PackageData getPackageData() {
        return packageData;
    }

    /**
     * Say whether this static context is schema-aware
     *
     * @param aware true if this static context is schema-aware
     */

    public void setSchemaAware(boolean aware) {
        getPackageData().setSchemaAware(aware);
    }

    /**
     * Construct a RetainedStaticContext, which extracts information from this StaticContext
     * to provide the subset of static context information that is potentially needed
     * during expression evaluation
     *
     * @return a RetainedStaticContext object: either a newly created one, or one that is
     * reused from a previous invocation.
     */
    public RetainedStaticContext makeRetainedStaticContext() {
        return new RetainedStaticContext(this);
    }


    /**
     * Initialize the default function library for XPath.
     * This can be overridden using setFunctionLibrary().
     */

    protected final void setDefaultFunctionLibrary() {
        FunctionLibraryList lib = new FunctionLibraryList();
        lib.addFunctionLibrary(config.getXPath31FunctionSet());
        lib.addFunctionLibrary(getConfiguration().getBuiltInExtensionLibraryList());
        lib.addFunctionLibrary(new ConstructorFunctionLibrary(getConfiguration()));
        lib.addFunctionLibrary(config.getIntegratedFunctionLibrary());
        config.addExtensionBinders(lib);
        setFunctionLibrary(lib);
    }

    public final void setDefaultFunctionLibrary(int version) {
        if (version == 20) {
            FunctionLibraryList lib = new FunctionLibraryList();
            lib.addFunctionLibrary(XPath20FunctionSet.getInstance());
            lib.addFunctionLibrary(getConfiguration().getBuiltInExtensionLibraryList());
            lib.addFunctionLibrary(new ConstructorFunctionLibrary(getConfiguration()));
            lib.addFunctionLibrary(config.getIntegratedFunctionLibrary());
            config.addExtensionBinders(lib);
            setFunctionLibrary(lib);
        } else {
            setDefaultFunctionLibrary();
        }
    }
    /**
     * Add a function library to the list of function libraries
     *
     * @param library the function library to be added
     */

    protected final void addFunctionLibrary(FunctionLibrary library) {
        libraryList.addFunctionLibrary(library);
    }

    /**
     * Get the host language (XSLT, XQuery, XPath) used to implement the code in this container
     *
     * @return the value {@link net.sf.saxon.Configuration#XPATH}
     */

    public int getHostLanguage() {
        return Configuration.XPATH;
    }

    /**
     * Construct a dynamic context for early evaluation of constant subexpressions
     */

    public XPathContext makeEarlyEvaluationContext() {
        return new EarlyEvaluationContext(getConfiguration());
    }


    public Location getContainingLocation() {
        return containingLocation;
    }

    /**
     * Set the containing location, which represents the location of the outermost expression using this
     * static context (typically, subexpressions will have a nested location that refers to this outer
     * containing location)
     *
     * @param location the location map to be used
     */

    public void setContainingLocation(Location location) {
        containingLocation = location;
    }

    /**
     * Set the base URI in the static context
     *
     * @param baseURI the base URI of the expression; the value null is allowed to indicate that the base URI is not available.
     */

    public void setBaseURI(String baseURI) {
        this.baseURI = baseURI;
    }

    /**
     * Get the Base URI, for resolving any relative URI's used
     * in the expression. Used by the document() function, resolve-uri(), etc.
     *
     * @return "" if no base URI has been set
     */

    public String getStaticBaseURI() {
        return baseURI == null ? "" : baseURI;
    }

    /**
     * Get the function library containing all the in-scope functions available in this static
     * context. This method is called by the XPath parser when binding a function call in the
     * XPath expression to an implementation of the function.
     */

    public FunctionLibrary getFunctionLibrary() {
        return libraryList;
    }

    /**
     * Set the function library to be used
     *
     * @param lib the function library
     */

    public void setFunctionLibrary(FunctionLibraryList lib) {
        libraryList = lib;
        usingDefaultFunctionLibrary = false;
    }

    /**
     * Declare a named collation
     *
     * @param name       The name of the collation (technically, a URI)
     * @param comparator The StringCollator used to implement the collating sequence
     * @param isDefault  True if this is to be used as the default collation
     * @deprecated since 9.6. All collations are now registered at the level of the
     * Configuration. If this method is called, the effect is (a) the supplied collation
     * is registered with the configuration, and (b) if isDefault=true, the collation
     * becomes the default collation for this static context
     */

    public void declareCollation(String name, StringCollator comparator, boolean isDefault) {
        getConfiguration().registerCollation(name, comparator);
        if (isDefault) {
            defaultCollationName = name;
        }
    }

    /**
     * Set the name of the default collation for this static context.
     * @param collationName the name of the default collation
     */

    public void setDefaultCollationName(String collationName) {
        defaultCollationName = collationName;
    }

    /**
     * Get the name of the default collation.
     *
     * @return the name of the default collation; or the name of the codepoint collation
     *         if no default collation has been defined
     */

    public String getDefaultCollationName() {
        return defaultCollationName;
    }

    /**
     * Issue a compile-time warning. This method is used during XPath expression compilation to
     * output warning conditions. The default implementation writes the message to the
     * error listener registered with the Configuration.
     */

    public void issueWarning(String s, Location locator) {
        XPathException ex = new XPathException(s);
        ex.setLocation(locator);
        config.getErrorListener().warning(ex);
    }

    /**
     * Get the system ID of the container of the expression. Used to construct error messages.
     *
     * @return "" always
     */

    public String getSystemId() {
        return "";
    }


    /**
     * Get the default namespace URI for elements and types
     * Return NamespaceConstant.NULL (that is, the zero-length string) for the non-namespace
     *
     * @return the default namespace for elements and type
     */

    public String getDefaultElementNamespace() {
        return defaultElementNamespace;
    }

    /**
     * Set the default namespace for elements and types
     *
     * @param uri the namespace to be used for unprefixed element and type names.
     *            The value "" (or NamespaceConstant.NULL) represents the non-namespace
     */

    public void setDefaultElementNamespace(String uri) {
        defaultElementNamespace = uri;
    }

    /**
     * Set the default function namespace
     *
     * @param uri the namespace to be used for unprefixed function names.
     *            The value "" (or NamespaceConstant.NULL) represents the non-namespace
     */

    public void setDefaultFunctionNamespace(String uri) {
        defaultFunctionNamespace = uri;
    }

    /**
     * Get the default function namespace.
     * The value "" (or NamespaceConstant.NULL) represents the non-namespace
     *
     * @return the default namesapce for functions
     */

    public String getDefaultFunctionNamespace() {
        return defaultFunctionNamespace;
    }

    /**
     * Set the XPath language level supported.
     * The current levels supported are 20 (=2.0) and 31 (=3.1). The default is 3.1.
     *
     * @param level the XPath language level
     * @since 9.3. From 9.8 this only affects the XPath syntax that is accepted;
     * it does not affect the function library that is available, which must be
     * set separately using {@link #setFunctionLibrary(FunctionLibraryList)}
     */

    public void setXPathLanguageLevel(int level) {
        xpathLanguageLevel = level;
    }

    /**
     * Get the XPath language level supported, as an integer value.
     * The current levels supported are 20 ("2.0"), and 31 ("3.1"). The default is "3.1".
     *
     * @return the XPath language level
     * @since 9.3
     */

    public int getXPathVersion() {
        return xpathLanguageLevel;
    }

    /**
     * Set XPath 1.0 backwards compatibility mode on or off
     *
     * @param option true if XPath 1.0 compatibility mode is to be set to true;
     *               otherwise false
     */

    public void setBackwardsCompatibilityMode(boolean option) {
        backwardsCompatible = option;
    }

    /**
     * Determine whether Backwards Compatible Mode is used
     *
     * @return true if XPath 1.0 compatibility mode is to be set to true;
     *         otherwise false
     */

    public boolean isInBackwardsCompatibleMode() {
        return backwardsCompatible;
    }

    /**
     * Set the DecimalFormatManager used to resolve the names of decimal formats used in calls
     * to the format-number() function.
     *
     * @param manager the decimal format manager for this static context, or null if no named decimal
     *                formats are available in this environment.
     */

    public void setDecimalFormatManager(DecimalFormatManager manager) {
        getPackageData().setDecimalFormatManager(manager);
    }

    /**
     * Get the required type of the context item. If no type has been explicitly declared for the context
     * item, an instance of AnyItemType (representing the type item()) is returned.
     *
     * @return the required type of the context item
     * @since 9.3
     */

    public ItemType getRequiredContextItemType() {
        return AnyItemType.getInstance();
    }

    /**
     * Get a DecimalFormatManager to resolve the names of decimal formats used in calls
     * to the format-number() function.
     *
     * @return the decimal format manager for this static context; a newly created empty
     *         DecimalFormatManager if none has been supplied
     * @since 9.2
     */

    public DecimalFormatManager getDecimalFormatManager() {
        DecimalFormatManager manager = getPackageData().getDecimalFormatManager();
        if (manager == null) {
            manager = new DecimalFormatManager(Configuration.XPATH, xpathLanguageLevel);
            getPackageData().setDecimalFormatManager(manager);
        }
        return manager;
    }

    /**
     * Get the KeyManager, containing definitions of keys available for use.
     *
     * @return the KeyManager. This is used to resolve key names, both explicit calls
     *         on key() used in XSLT, and system-generated calls on key() which may
     *         also appear in XQuery and XPath
     */
    public KeyManager getKeyManager() {
        return getPackageData().getKeyManager();
    }

    /**
     * Register an alias for an ItemType. This is a Saxon extension. If {@code typename}
     * has been registered as an alias for, say, map{xs:string, xs:integer*}, then
     * the syntax {@code ~typename} is accepted anywhere this ItemType would
     * be accepted.
     */

    public void setTypeAlias(StructuredQName name, ItemType type) {
        typeAliases.put(name, type);
    }

    /**
     * Get type alias. This is a Saxon extension. A type alias is a QName which can
     * be used as a shorthand for an itemtype, using the syntax ~typename anywhere that
     * an item type is permitted.
     *
     * @param typeName the name of the type alias
     * @return the corresponding item type, if the name is recognised; otherwise null.
     */

    @Override
    public ItemType resolveTypeAlias(StructuredQName typeName) {
        return typeAliases.get(typeName);
    }
}

