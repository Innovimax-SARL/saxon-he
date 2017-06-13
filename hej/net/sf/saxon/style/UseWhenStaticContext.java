////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.expr.parser.RetainedStaticContext;
import net.sf.saxon.functions.FunctionLibrary;
import net.sf.saxon.functions.FunctionLibraryList;
import net.sf.saxon.functions.registry.ConstructorFunctionLibrary;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.sxpath.AbstractStaticContext;
import net.sf.saxon.trans.DecimalFormatManager;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;

import java.util.Collections;
import java.util.Set;

/**
 * This class implements the static context used for evaluating use-when and other
 * static expressions in XSLT 3.0
 * A new instance of this class is created for each use-when expression encountered; there are
 * therefore no issues with reusability. The class provides a Container for the expression as well
 * as the static context information; the Executable contains the single XPath expression only, and
 * is created for the purpose.
 */

public class UseWhenStaticContext extends AbstractStaticContext implements XSLTStaticContext {

    private NamespaceResolver namespaceContext;
    private FunctionLibrary functionLibrary;
    private Compilation compilation;

    /**
     * Create a static context for evaluating use-when expressions
     *
     * @param compilation      the package compilation episode
     * @param namespaceContext the namespace context in which the use-when expression appears
     */

    public UseWhenStaticContext(Compilation compilation, NamespaceResolver namespaceContext) {
        Configuration config = compilation.getConfiguration();
        setConfiguration(config);
        this.compilation = compilation;
        setPackageData(compilation.getPackageData());
        this.namespaceContext = namespaceContext;
        setXPathLanguageLevel(31);

        FunctionLibraryList lib = new FunctionLibraryList();
        lib.addFunctionLibrary(config.getUseWhenFunctionSet());
        lib.addFunctionLibrary(getConfiguration().getBuiltInExtensionLibraryList());
        lib.addFunctionLibrary(new ConstructorFunctionLibrary(getConfiguration()));
        lib.addFunctionLibrary(config.getIntegratedFunctionLibrary());
        config.addExtensionBinders(lib);
        functionLibrary = lib;
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


    public Compilation getCompilation() {
        return compilation;
    }

    /**
     * Issue a compile-time warning
     */

    public void issueWarning(String s, Location locator) {
        XPathException err = new XPathException(s);
        err.setLocator(locator);
        getConfiguration().getErrorListener().warning(err);
    }

    /**
     * Get the System ID of the container of the expression. This is the containing
     * entity (file) and is therefore useful for diagnostics. Use getBaseURI() to get
     * the base URI, which may be different.
     */

    public String getSystemId() {
        return getStaticBaseURI();
    }

    /**
     * Bind a variable used in this element to its declaration
     *
     * @param qName the name of the variable
     * @return an expression representing the variable reference, This will often be
     *         a {@link net.sf.saxon.expr.VariableReference}, suitably initialized to refer to the corresponding variable declaration,
     *         but in general it can be any expression which returns the variable's value when evaluated. In this version of the method,
     *         the value of the variable is known statically, so the returned expression is a literal containing the variable's value.
     */

    public Expression bindVariable(StructuredQName qName) throws XPathException {
        GroundedValue val = compilation.getStaticVariable(qName);
        if (val != null) {
            return Literal.makeLiteral(val);
        } else {
            XPathException err = new XPathException
                    ("Variables (other than XSLT 3.0 static variables) cannot be used in a static expression: " +
                            qName.getDisplayName());
            err.setErrorCode("XPST0008");
            err.setIsStaticError(true);
            throw err;
        }
    }

    /**
     * Get the function library containing all the in-scope functions available in this static
     * context
     */

    public FunctionLibrary getFunctionLibrary() {
        return functionLibrary;
    }

    /**
     * Get a named collation.
     *
     * @param name The name of the required collation. Supply null to get the default collation.
     * @return the collation; or null if the required collation is not found.
     */

//    /*@Nullable*/ public StringCollator getCollation(String name) {
//        return null;
//    }

    /**
     * Get the name of the default collation.
     *
     * @return the name of the default collation; or the name of the codepoint collation
     *         if no default collation has been defined
     */

    public String getDefaultCollationName() {
        return NamespaceConstant.CODEPOINT_COLLATION_URI;
    }

    /**
     * Get the default function namespace
     */

    public String getDefaultFunctionNamespace() {
        return NamespaceConstant.FN;
    }

    /**
     * Determine whether Backwards Compatible Mode is used
     */

    public boolean isInBackwardsCompatibleMode() {
        return false;
    }

    /**
     * Determine whether a Schema for a given target namespace has been imported. Note that the
     * in-scope element declarations, attribute declarations and schema types are the types registered
     * with the (schema-aware) configuration, provided that their namespace URI is registered
     * in the static context as being an imported schema namespace. (A consequence of this is that
     * within a Configuration, there can only be one schema for any given namespace, including the
     * null namespace).
     */

    public boolean isImportedSchema(String namespace) {
        return false;
    }

    /**
     * Get the set of imported schemas
     *
     * @return a Set, the set of URIs representing the names of imported schemas
     */

    public Set<String> getImportedSchemaNamespaces() {
        return Collections.emptySet();
    }

    /**
     * Get a namespace resolver to resolve the namespaces declared in this static context.
     *
     * @return a namespace resolver.
     */

    public NamespaceResolver getNamespaceResolver() {
        return namespaceContext;
    }

    /**
     * Get a DecimalFormatManager to resolve the names of decimal formats used in calls
     * to the format-number() function.
     *
     * @return the decimal format manager for this static context, or null if named decimal
     *         formats are not supported in this environment.
     */

    public DecimalFormatManager getDecimalFormatManager() {
        return null;
    }

    /**
     * Determine if an extension element is available
     *
     * @throws net.sf.saxon.trans.XPathException
     *          if the name is invalid or the prefix is not declared
     */

    public boolean isElementAvailable(String qname) throws XPathException {
        try {
            String[] parts = NameChecker.getQNameParts(qname);
            String uri;
            if (parts[0].isEmpty()) {
                uri = getDefaultElementNamespace();
            } else {
                uri = namespaceContext.getURIForPrefix(parts[0], false);
            }
            StyleNodeFactory factory = getConfiguration().makeStyleNodeFactory(compilation);
            return factory.isElementAvailable(uri, parts[1], true);
        } catch (QNameException e) {
            XPathException err = new XPathException("Invalid element name. " + e.getMessage());
            err.setErrorCode("XTDE1440");
            throw err;
        }
    }

    public int getColumnNumber() {
        return 0;
    }

    public String getPublicId() {
        return null;
    }

    public int getLineNumber() {
        return -1;
    }

    /**
     * Get type alias. This is a Saxon extension. A type alias is a QName which can
     * be used as a shorthand for an itemtype, using the syntax ~typename anywhere that
     * an item type is permitted.
     *
     * @param typeName the name of the type alias
     * @return the corresponding item type, if the name is recognised; otherwise null.
     * This implementation always returns null (type aliases cannot be used in XSLT static
     * expressions).
     */
    @Override
    public ItemType resolveTypeAlias(StructuredQName typeName) {
        return null;
    }

}

