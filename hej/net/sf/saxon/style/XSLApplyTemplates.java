////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.instruct.ApplyTemplates;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.expr.sort.SortExpression;
import net.sf.saxon.expr.sort.SortKeyDefinitionList;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.AnyNodeTest;
import net.sf.saxon.pattern.NameTest;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.Mode;
import net.sf.saxon.trans.SymbolicName;
import net.sf.saxon.trans.rules.RuleManager;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.Whitespace;

import java.util.HashMap;


/**
 * An xsl:apply-templates element in the stylesheet
 */

public class XSLApplyTemplates extends StyleElement {

    /*@Nullable*/ private Expression select;
    private StructuredQName modeName;   // null if no name specified or if conventional values such as #current used
    private boolean useCurrentMode = false;
    private boolean useTailRecursion = false;
    private boolean defaultedSelectExpression = true;
    private Mode mode;
    private String modeAttribute;

    /**
     * Determine whether this node is an instruction.
     *
     * @return true - it is an instruction
     */

    public boolean isInstruction() {
        return true;
    }


    public void prepareAttributes() throws XPathException {

        AttributeCollection atts = getAttributeList();

        String selectAtt;

        for (int a = 0; a < atts.getLength(); a++) {
            String f = atts.getQName(a);
            if (f.equals("mode")) {
                modeAttribute = Whitespace.trim(atts.getValue(a));
            } else if (f.equals("select")) {
                selectAtt = atts.getValue(a);
                select = makeExpression(selectAtt, a);
                defaultedSelectExpression = false;
            } else {
                checkUnknownAttribute(atts.getNodeName(a));
            }
        }

        if (modeAttribute != null) {
            if (modeAttribute.equals("#current")) {
                useCurrentMode = true;
            } else if (modeAttribute.equals("#unnamed")) {
                modeName = Mode.UNNAMED_MODE_NAME;
            } else if (modeAttribute.equals("#default")) {
                // do nothing;
            } else {
                try {
                    modeName = makeQName(modeAttribute);
                } catch (NamespaceException err) {
                    compileError(err.getMessage(), "XTSE0280");
                    modeName = null;
                } catch (XPathException err) {
                    compileError("Mode name " + Err.wrap(modeAttribute) + " is not a valid QName",
                            err.getErrorCodeQName());
                    modeName = null;
                }
            }
        }
    }

    public void validate(ComponentDeclaration decl) throws XPathException {

        // get the Mode object
        if (useCurrentMode) {
            // give a warning if we're not inside an xsl:template
            if (iterateAxis(AxisInfo.ANCESTOR, new NameTest(Type.ELEMENT, StandardNames.XSL_TEMPLATE, getNamePool())).next() == null) {
                issueWarning("Specifying mode=\"#current\" when not inside an xsl:template serves no useful purpose", this);
            }
        } else {
            PrincipalStylesheetModule psm = getPrincipalStylesheetModule();
            if (modeName == null) {
                // XSLT 3.0 allows a default mode to be specified on a containing element
                modeName = getDefaultMode();
                if ((modeName == null || modeName.equals(Mode.UNNAMED_MODE_NAME)) &&
                        psm.isDeclaredModes() && !psm.getRuleManager().isUnnamedModeExplicit()) {
                    compileError("The unnamed mode must be explicitly declared in an xsl:mode declaration", "XTSE3085");
                }
            } else if (modeName.equals(Mode.UNNAMED_MODE_NAME) && psm.isDeclaredModes() && !psm.getRuleManager().isUnnamedModeExplicit()) {
                compileError("The #unnamed mode must be explicitly declared in an xsl:mode declaration", "XTSE3085");
            }

            SymbolicName sName = new SymbolicName(StandardNames.XSL_MODE, modeName);
            StylesheetPackage containingPackage = decl.getSourceElement().getContainingPackage();
            HashMap<SymbolicName, Component> componentIndex = containingPackage.getComponentIndex();
            // see if there is a mode with this name in a used package
            Component existing = componentIndex.get(sName);
                if (existing != null) {
                    mode = (Mode)existing.getActor();
                }

            if (mode == null) {
                if (psm.isDeclaredModes()) {
                    compileError("Mode name " + modeName.getDisplayName() + " must be explicitly declared in an xsl:mode declaration", "XTSE3085");
                }
                mode = psm.getRuleManager().obtainMode(modeName, true);
            }
        }

        // handle sorting if requested

        AxisIterator kids = iterateAxis(AxisInfo.CHILD);
        NodeInfo child;
        while ((child = kids.next()) != null) {
            if (child.getNodeKind() == Type.TEXT) {
                // with xml:space=preserve, white space nodes may still be there
                if (!Whitespace.isWhite(child.getStringValueCS())) {
                    compileError("No character data is allowed within xsl:apply-templates", "XTSE0010");
                }
            } else if (!(child instanceof XSLSort || child instanceof XSLWithParam)){
                compileError("Invalid element " + Err.wrap(child.getDisplayName(), Err.ELEMENT) +
                                     " within xsl:apply-templates", "XTSE0010");
            }
        }

        if (select == null) {
            Expression here = new ContextItemExpression();
            RoleDiagnostic role =
                    new RoleDiagnostic(RoleDiagnostic.CONTEXT_ITEM, "", 0);
            role.setErrorCode("XTTE0510");
            here = new ItemChecker(here, AnyNodeTest.getInstance(), role);
            select = new SimpleStepExpression(here, new AxisExpression(AxisInfo.CHILD, null));
            //select = new AxisExpression(AxisInfo.CHILD, null);
            select.setLocation(allocateLocation());
            select.setRetainedStaticContext(makeRetainedStaticContext());
        }

        select = typeCheck("select", select);

    }

    /**
     * Mark tail-recursive calls on templates and functions.
     * For most instructions, this does nothing.
     */

    public boolean markTailCalls() {
        useTailRecursion = true;
        return true;
    }


    public Expression compile(Compilation compilation, ComponentDeclaration decl) throws XPathException {
        SortKeyDefinitionList sortKeys = makeSortKeys(compilation, decl);
        if (sortKeys != null) {
            useTailRecursion = false;
        }
        assert select != null;
        Expression sortedSequence = select;
        if (sortKeys != null) {
            sortedSequence = new SortExpression(select, sortKeys);
        }
        compileSequenceConstructor(compilation, decl, true);
        RuleManager rm = compilation.getPrincipalStylesheetModule().getRuleManager();
        ApplyTemplates app = new ApplyTemplates(
                sortedSequence,
                useCurrentMode,
                useTailRecursion,
                defaultedSelectExpression,
                isWithinDeclaredStreamableConstruct(),
                mode,
                rm);
        app.setActualParams(getWithParamInstructions(app, compilation, decl, false));
        app.setTunnelParams(getWithParamInstructions(app, compilation, decl, true));
        return app;
    }

}

