////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.Configuration;
import net.sf.saxon.Version;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.instruct.Actor;
import net.sf.saxon.expr.instruct.ApplyTemplates;
import net.sf.saxon.expr.instruct.CallTemplate;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.expr.sort.CodepointCollator;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.om.AttributeCollection;
import net.sf.saxon.om.NamespaceException;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.pattern.NodeTestPattern;
import net.sf.saxon.pattern.Pattern;
import net.sf.saxon.trans.*;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ErrorType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.Whitespace;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Handler for xsl:key elements in stylesheet. <br>
 */

public class XSLKey extends StyleElement implements StylesheetComponent {

    private Pattern match;
    private Expression use;
    private String collationName;
    private StructuredQName keyName;
    SlotManager stackFrameMap;
    private boolean rangeKey;
    private boolean composite = false;


    /**
     * Get the corresponding Actor object that results from the compilation of this
     * StylesheetComponent
     *
     * @return the compiled ComponentCode
     * @throws UnsupportedOperationException for second-class components such as keys that support outwards references
     *                                       but not inwards references
     */
    public Actor getActor() {
        throw new UnsupportedOperationException();
    }

    public SymbolicName getSymbolicName() {
        return null;
    }

    public void checkCompatibility(Component component) throws XPathException {
        // no action: keys cannot be overridden
    }

    @Override
    public boolean isDeclaration() {
        return true;
    }

    /**
     * Determine whether this type of element is allowed to contain a sequence constructor
     *
     * @return true: yes, it may contain a sequence constructor
     */

    public boolean mayContainSequenceConstructor() {
        return true;
    }

    /**
     * Get the Procedure object that looks after any local variables declared in the content constructor
     */

    public SlotManager getSlotManager() {
        return stackFrameMap;
    }

    public void prepareAttributes() throws XPathException {

        String nameAtt = null;
        String matchAtt = null;
        String useAtt = null;

        AttributeCollection atts = getAttributeList();

        for (int a = 0; a < atts.getLength(); a++) {
            String uri = atts.getURI(a);
            String local = atts.getLocalName(a);
            if ("".equals(uri)) {
                if (local.equals("name")) {
                    nameAtt = Whitespace.trim(atts.getValue(a));
                } else if (local.equals("use")) {
                    useAtt = atts.getValue(a);
                    use = makeExpression(useAtt, a);
                } else if (local.equals("match")) {
                    matchAtt = atts.getValue(a);
                } else if (local.equals("collation")) {
                    collationName = Whitespace.trim(atts.getValue(a));
                } else if (local.equals("composite")) {
                    composite = processBooleanAttribute("composite", atts.getValue(a));
                } else {
                    checkUnknownAttribute(atts.getNodeName(a));
                }
            } else if (local.equals("range-key") && uri.equals(NamespaceConstant.SAXON)) {
                String rangeKeyAtt = Whitespace.trim(atts.getValue(a));
                if ("yes".equals(rangeKeyAtt)) {
                    rangeKey = true;
                } else if ("no".equals(rangeKeyAtt)) {
                    rangeKey = false;
                } else {
                    compileError("saxon:range-key must be 'yes' or 'no'", "XTSE0020");
                }
            } else {
                checkUnknownAttribute(atts.getNodeName(a));
            }
        }

        if (nameAtt == null) {
            reportAbsence("name");
            nameAtt = "_dummy_key_name";
        }
        try {
            keyName = makeQName(nameAtt);
            setObjectName(keyName);
        } catch (NamespaceException err) {
            compileErrorInAttribute(err.getMessage(), "XTSE0280", "name");
        } catch (XPathException err) {
            compileErrorInAttribute(err.getMessage(), err.getErrorCodeLocalPart(), "name");
        }

        if (matchAtt == null) {
            reportAbsence("match");
            matchAtt = "*";
        }
        match = makePattern(matchAtt, "match");
        if (match == null) {
            // error has been reported
            match = new NodeTestPattern(ErrorType.getInstance());
        }

    }

    public StructuredQName getKeyName() {
        //We use null to mean "not yet evaluated"
        try {
            if (getObjectName() == null) {
                // allow for forwards references
                String nameAtt = getAttributeValue("", "name");
                if (nameAtt != null) {
                    setObjectName(makeQName(nameAtt));
                }
            }
            return getObjectName();
        } catch (NamespaceException err) {
            return null;          // the errors will be picked up later
        } catch (XPathException err) {
            return null;
        }
    }

    public void validate(ComponentDeclaration decl) throws XPathException {

        Configuration config = getConfiguration();
        stackFrameMap = config.makeSlotManager();
        checkTopLevel("XTSE0010", false);
        if (use != null) {
            // the value can be supplied as a content constructor in place of a use expression
            if (hasChildNodes()) {
                compileError("An xsl:key element with a @use attribute must be empty", "XTSE1205");
            }
            try {
                RoleDiagnostic role =
                        new RoleDiagnostic(RoleDiagnostic.INSTRUCTION, "xsl:key/use", 0);
                //role.setSourceLocator(new ExpressionLocation(this));
                use = config.getTypeChecker(false).staticTypeCheck(
                        use,
                        SequenceType.ATOMIC_SEQUENCE,
                        role, makeExpressionVisitor());
            } catch (XPathException err) {
                compileError(err);
            }
        } else {
            if (!hasChildNodes()) {
                compileError("An xsl:key element must either have a @use attribute or have content", "XTSE1205");
            }
        }
        use = typeCheck("use", use);
        match = typeCheck("match", match);

        // Do a further check that the use expression makes sense in the context of the match pattern
        if (use != null) {
            use = use.typeCheck(makeExpressionVisitor(), config.makeContextItemStaticInfo(match.getItemType(), false));
        }

        if (collationName != null) {
            URI collationURI;
            try {
                collationURI = new URI(collationName);
                if (!collationURI.isAbsolute()) {
                    URI base = new URI(getBaseURI());
                    collationURI = base.resolve(collationURI);
                    collationName = collationURI.toString();
                }
            } catch (URISyntaxException err) {
                compileError("Collation name '" + collationName + "' is not a valid URI");
                //collationName = NamespaceConstant.CODEPOINT_COLLATION_URI;
            }
        } else {
            collationName = getDefaultCollationName();
        }

    }

    private static class ContainsGlobalVariable implements ExpressionTool.ExpressionPredicate {
        public boolean matches(Expression e) {
            return e instanceof GlobalVariableReference ||
                    e instanceof UserFunctionCall ||
                    e instanceof CallTemplate ||
                    e instanceof ApplyTemplates;
        }
    }

    private static ContainsGlobalVariable containsGlobalVariable = new ContainsGlobalVariable();

    public void index(ComponentDeclaration decl, PrincipalStylesheetModule top) throws XPathException {
        StructuredQName keyName = getKeyName();
        if (keyName != null) {
            top.getKeyManager().preRegisterKeyDefinition(keyName);
        }
    }

    public void compileDeclaration(Compilation compilation, ComponentDeclaration decl) throws XPathException {
        StaticContext env = getStaticContext();
        Configuration config = env.getConfiguration();
        StringCollator collator = null;
        if (collationName != null) {
            collator = findCollation(collationName, getBaseURI());
            if (collator == null) {
                compileError("The collation name " + Err.wrap(collationName, Err.URI) + " is not recognized", "XTSE1210");
                collator = CodepointCollator.getInstance();
            }
            if (collator instanceof CodepointCollator) {
                // if the user explicitly asks for the codepoint collation, treat it as if they hadn't asked
                collator = null;
                collationName = null;

            } else if (!Version.platform.canReturnCollationKeys(collator)) {
                compileError("The collation used for xsl:key must be capable of generating collation keys", "XTSE1210");
            }
        }

        if (use == null) {
            Expression body = compileSequenceConstructor(compilation, decl, true);

            try {
                use = Atomizer.makeAtomizer(body);
                use = use.simplify();
            } catch (XPathException e) {
                compileError(e);
            }

            try {
                RoleDiagnostic role =
                        new RoleDiagnostic(RoleDiagnostic.INSTRUCTION, "xsl:key/use", 0);
                //role.setSourceLocator(new ExpressionLocation(this));
                use = config.getTypeChecker(false).staticTypeCheck(
                        use,
                        SequenceType.OPTIONAL_ATOMIC,
                        role, makeExpressionVisitor());
                // Do a further check that the use expression makes sense in the context of the match pattern
                assert match != null;
                use = use.typeCheck(makeExpressionVisitor(), config.makeContextItemStaticInfo(match.getItemType(), false));


            } catch (XPathException err) {
                compileError(err);
            }
        }

        ExpressionVisitor visitor = makeExpressionVisitor();
        ContextItemStaticInfo contextItemType = getConfiguration().makeContextItemStaticInfo(match.getItemType(), false);
        use = use.optimize(visitor, contextItemType);
        ItemType useItemType = use.getItemType();
        if (useItemType == ErrorType.getInstance()) {
            useItemType = BuiltInAtomicType.STRING; // corner case, prevents crashing
        }
        BuiltInAtomicType useType = (BuiltInAtomicType) useItemType.getPrimitiveItemType();
        if (xPath10ModeIsEnabled()) {
            if (!useType.equals(BuiltInAtomicType.STRING) && !useType.equals(BuiltInAtomicType.UNTYPED_ATOMIC)) {
                use = new AtomicSequenceConverter(use, BuiltInAtomicType.STRING);
                ((AtomicSequenceConverter) use).allocateConverter(config, false);
                useType = BuiltInAtomicType.STRING;
            }
        }
        allocateLocalSlots(use);
        // first slot in pattern is reserved for current()
        int nextFree = 0;
        if ((match.getDependencies() & StaticProperty.DEPENDS_ON_CURRENT_ITEM) != 0) {
            nextFree = 1;
        }
        int slots = match.allocateSlots(stackFrameMap, nextFree);

        // If either the match pattern or the use expression references a global variable or parameter,
        // or a call on a function or template that might reference one, then
        // the key indexes cannot be reused across multiple transformations. See Saxon bug 1968.

        boolean sensitive =
                ExpressionTool.contains(use, false, containsGlobalVariable) ||
                ExpressionTool.contains(match, false, containsGlobalVariable);


        KeyManager km = getCompilation().getPrincipalStylesheetModule().getKeyManager();
        SymbolicName symbolicName = new SymbolicName(StandardNames.XSL_KEY, keyName);
        KeyDefinition keydef = new KeyDefinition(symbolicName, match, use, collationName, collator);
        keydef.setPackageData(getCompilation().getPackageData());
        keydef.setRangeKey(rangeKey);
        keydef.setIndexedItemType(useType);
        keydef.setStackFrameMap(stackFrameMap);
        keydef.setLocation(getSystemId(), getLineNumber());
        keydef.setBackwardsCompatible(xPath10ModeIsEnabled());
        keydef.setComposite(composite);
        keydef.makeDeclaringComponent(Visibility.PRIVATE, getContainingPackage());
        try {
            km.addKeyDefinition(keyName, keydef, !sensitive, compilation.getConfiguration());
        } catch (XPathException err) {
            compileError(err);
        }
    }

    /**
     * Optimize the stylesheet construct
     *
     * @param declaration this xsl:key declaration
     */

    public void optimize(ComponentDeclaration declaration) throws XPathException {
        // already done earlier
    }


    /**
     * Generate byte code if appropriate
     *
     * @param opt the optimizer
     * @throws net.sf.saxon.trans.XPathException
     *          if bytecode generation fails
     */
    public void generateByteCode(Optimizer opt) {}
}