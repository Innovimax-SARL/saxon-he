////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.instruct.GlobalVariable;
import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.expr.parser.RetainedStaticContext;
import net.sf.saxon.functions.FunctionLibrary;
import net.sf.saxon.functions.FunctionLibraryList;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.functions.registry.VendorFunctionSetHE;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NameTest;
import net.sf.saxon.trans.*;
import net.sf.saxon.tree.AttributeLocation;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;

import java.util.Set;

/**
 * An ExpressionContext represents the context for an XPath expression written
 * in the stylesheet.
 */

public class ExpressionContext implements XSLTStaticContext {

    private StyleElement element;
    private StructuredQName attributeName;
    private Location containingLocation = null;
    private NamespaceResolver namespaceResolver = null;
    private RetainedStaticContext retainedStaticContext = null;


    /**
     * Create a static context for XPath expressions in an XSLT stylesheet
     *
     * @param styleElement  the element on which the XPath expression appears
     * @param attributeName the name of the attribute in which the XPath expression appears; or null
     *                      if the expression appears in a text node
     */

    public ExpressionContext(StyleElement styleElement, StructuredQName attributeName) {
        element = styleElement;
        this.attributeName = attributeName;
    }

    /**
     * Get the system configuration
     */

    public Configuration getConfiguration() {
        return element.getConfiguration();
    }

    public StylesheetPackage getPackageData() {
        return element.getPackageData();
    }

    /**
     * Ask whether expressions compiled under this static context are schema-aware.
     * They must be schema-aware if the expression is to handle typed (validated) nodes
     *
     * @return true if expressions are schema-aware
     */
    public boolean isSchemaAware() {
        return element.isSchemaAware();
    }

    /**
     * Construct a dynamic context for early evaluation of constant subexpressions
     */

    public XPathContext makeEarlyEvaluationContext() {
        return new EarlyEvaluationContext(getConfiguration());
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
        if (retainedStaticContext == null) {
            if (element.changesRetainedStaticContext() || !(element.getParent() instanceof StyleElement)) {
                retainedStaticContext = new RetainedStaticContext(this);
            } else {
                retainedStaticContext = ((StyleElement) element.getParent()).getStaticContext().makeRetainedStaticContext();
            }
        }
        return retainedStaticContext;
    }

    /**
     * Get the containing location of an XPath expression within the stylesheet
     *
     * @return a Location that identifies the containing element, together with the name of the
     * attribute holding the XPath expression in question.
     */

    public Location getContainingLocation() {
        if (containingLocation == null) {
            containingLocation = new AttributeLocation(element, attributeName);
        }
        return containingLocation;
    }

    /**
     * Issue a compile-time warning
     */

    public void issueWarning(String s, Location locator) {
        element.issueWarning(new XPathException(s, SaxonErrorCode.SXWN9000, locator));
    }

    /**
     * Get the System ID of the entity containing the expression (used for diagnostics)
     */

    public String getSystemId() {
        return element.getSystemId();
    }

    /**
     * Get the Base URI of the element containing the expression, for resolving any
     * relative URI's used in the expression.
     * Used by the document() function.
     */

    public String getStaticBaseURI() {
        return element.getBaseURI();
    }

    /**
     * Get a copy of the NamespaceResolver suitable for saving in the executable code
     *
     * @return a NamespaceResolver
     */


    public NamespaceResolver getNamespaceResolver() {
        if (namespaceResolver == null) {
            namespaceResolver = element.makeNamespaceContext();
        }
        return namespaceResolver;
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
     * @return the decimal format manager for this static context, or null if named decimal
     * formats are not supported in this environment.
     */

    public DecimalFormatManager getDecimalFormatManager() {
        return element.getCompilation().getPrincipalStylesheetModule().getDecimalFormatManager();
    }

    /**
     * Bind a variable to an object that can be used to refer to it
     *
     * @param qName the name of the variable
     * @return a VariableDeclaration object that can be used to identify it in the Bindery,
     * @throws XPathException if the variable has not been declared
     */

    public Expression bindVariable(StructuredQName qName) throws XPathException {
        SourceBinding sourceBinding = element.bindVariable(qName);
        if (sourceBinding == null) {
            if (qName.hasURI(NamespaceConstant.XSLT) && qName.getLocalPart().equals("original")) {
                element.getXslOriginal(StandardNames.XSL_VARIABLE);
                return new GlobalVariableReference(qName);
            }
            // it might have been declared in an imported package or query
            SymbolicName sn = new SymbolicName(StandardNames.XSL_VARIABLE, qName);
            Component comp = element.getCompilation().getPrincipalStylesheetModule().getComponent(sn);
            if (comp != null && comp.getDeclaringPackage() != element.getContainingPackage()) { // test variable-0118
                GlobalVariable var = (GlobalVariable) comp.getActor();
                GlobalVariableReference vref = new GlobalVariableReference(var);
                vref.setStaticType(var.getRequiredType(), null, 0);
                return vref;
            }
            // it might be an implicit error variable in try/catch
            if (getXPathVersion() >= 30 && qName.hasURI(NamespaceConstant.ERR)) {
                AxisIterator catchers = element.iterateAxis(AxisInfo.ANCESTOR_OR_SELF,
                                                            new NameTest(Type.ELEMENT, StandardNames.XSL_CATCH, element.getNamePool()));
                StyleElement catcher = (StyleElement) catchers.next();
                if (catcher != null) {
                    for (StructuredQName errorVariable : StandardNames.errorVariables) {
                        if (errorVariable.getLocalPart().equals(qName.getLocalPart())) {
                            SystemFunction f = VendorFunctionSetHE.getInstance().makeFunction("dynamic-error-info", 1);
                            return f.makeFunctionCall(new StringLiteral(qName.getLocalPart()));
                        }
                    }
                }
            }

            XPathException err = new XPathException("Variable " + qName.getDisplayName() +
                                                            " has not been declared (or its declaration is not in scope)");
            err.setErrorCode("XPST0008");
            err.setIsStaticError(true);
            throw err;
        }

        VariableReference var;
        if (sourceBinding.hasProperty(SourceBinding.IMPLICITLY_DECLARED)) {
            // Used for the $value variable in xsl:accumulator-rule
            SuppliedParameterReference supRef = new SuppliedParameterReference(0);
            supRef.setSuppliedType(sourceBinding.getDeclaredType());
            return supRef;
        }
        if (sourceBinding.hasProperty(SourceBinding.GLOBAL)) {
            var = new GlobalVariableReference(qName);
            //SourceBinding binding = ((XSLGlobalVariable) sourceBinding.getSourceElement()).sourceBinding;
            GlobalVariable compiledVar = ((XSLGlobalVariable) sourceBinding.getSourceElement()).getCompiledVariable();
            if (compiledVar != null && element.getCompilation().getCompilerInfo().isJustInTimeCompilation()) {
                var.fixup(compiledVar);
                var.setStaticType(compiledVar.getRequiredType(), sourceBinding.getConstantValue(), 0);
            } else {
                sourceBinding.registerReference(var);
            }
            return var;
        } else {
            var = new LocalVariableReference(qName);
            sourceBinding.registerReference(var);
            return var;
        }

    }

    /**
     * Get the function library containing all the in-scope functions available in this static
     * context
     */

    public FunctionLibrary getFunctionLibrary() {
        FunctionLibrary lib = element.getContainingPackage().getFunctionLibrary();
        StyleElement containingOverride = element.findAncestorElement(StandardNames.XSL_OVERRIDE);
        if (containingOverride != null) {
            // Within xsl:override, recognize the function name xsl:original
            FunctionLibraryList libList = new FunctionLibraryList();
            libList.addFunctionLibrary(lib);
            containingOverride.addXSLOverrideFunctionLibrary(libList);
            return libList;
        }
        return lib;
    }

    /**
     * Determine if an extension element is available
     *
     * @throws XPathException if the name is invalid or the prefix is not declared
     */

    public boolean isElementAvailable(String qname) throws XPathException {
        try {
            String[] parts = NameChecker.getQNameParts(qname);
            String uri;
            if (parts[0].isEmpty()) {
                uri = getDefaultElementNamespace();
            } else {
                uri = element.getURIForPrefix(parts[0], false);
            }
            return element.getCompilation().getStyleNodeFactory(true).isElementAvailable(uri, parts[1], true);
        } catch (QNameException e) {
            XPathException err = new XPathException("Invalid element name. " + e.getMessage());
            err.setErrorCode("XTDE1440");
            throw err;
        }
    }

    /**
     * Get the default collation. Return null if no default collation has been defined
     */

    public String getDefaultCollationName() {
        return element.getDefaultCollationName();
    }

    /**
     * Get the default XPath namespace for elements and types
     * Return NamespaceConstant.NULL for the non-namespace
     */

    public String getDefaultElementNamespace() {
        return element.getDefaultXPathNamespace();
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
        return element.xPath10ModeIsEnabled();
    }

    /**
     * Get the XPath language level supported, as an integer.
     * From Saxon 9.8 this is always 3.1 (returned as 31)
     *
     * @return the XPath language level; from Saxon 9.8 the return value will be 31.
     * @since 9.3
     */

    public int getXPathVersion() {
        return 31;
    }

    /**
     * Test whether a schema has been imported for a given namespace
     *
     * @param namespace the target namespace of the required schema
     * @return true if a schema for this namespace has been imported
     */

    public boolean isImportedSchema(String namespace) {
        //if (Configuration.USE_PACKAGE_BINDING) {
        return element.getPrincipalStylesheetModule().isImportedSchema(namespace);
        //} else {
        //    return getConfiguration().
        //}
    }

    /**
     * Get the set of imported schemas
     *
     * @return a Set, the set of URIs representing the names of imported schemas
     */

    public Set<String> getImportedSchemaNamespaces() {
        return element.getPrincipalStylesheetModule().getImportedSchemaTable();
    }

    /**
     * Get the KeyManager, containing definitions of keys available for use.
     *
     * @return the KeyManager. This is used to resolve key names, both explicit calls
     * on key() used in XSLT, and system-generated calls on key() which may
     * also appear in XQuery and XPath
     */
    public KeyManager getKeyManager() {
        return element.getCompilation().getPrincipalStylesheetModule().getKeyManager();
    }

    /**
     * Get the stylesheet element containing this XPath expression
     *
     * @return the element in the tree representation of the source stylesheet
     */

    public StyleElement getStyleElement() {
        return element;
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
        return getPackageData().obtainTypeAliasManager().getItemType(typeName);
    }


}

