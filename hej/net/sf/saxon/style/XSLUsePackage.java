////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Component;
import net.sf.saxon.expr.instruct.GlobalContextRequirement;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.trans.*;
import net.sf.saxon.trans.rules.RuleManager;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.Whitespace;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Handler for xsl:use-package elements in stylesheet.
 */
public class XSLUsePackage extends StyleElement {

    private String nameAtt = null;
    private PackageVersionRanges versionRanges = null;
    private StylesheetPackage usedPackage;
    private List<ComponentAcceptor> acceptors = null;

    /**
     * Bind to the package to which this xsl:use-package element refers.
     */

    public void findUsedPackage(CompilerInfo info) throws XPathException {
        if (usedPackage == null) {
            if (nameAtt == null) {
                nameAtt = Whitespace.trim(getAttributeValue("", "name"));
            }
            usedPackage = info.getPackageLibrary().findPackage(nameAtt, getPackageVersionRanges()).loadedPackage;
            if (usedPackage == null) {
                compileError("Package " + getAttributeValue("name") + " could not be found", "XTSE3000");
                // For error recovery, create an empty package
                usedPackage = getConfiguration().makeStylesheetPackage();
            }
            GlobalContextRequirement gcr = usedPackage.getContextItemRequirements();
            if (gcr != null && !gcr.isMayBeOmitted()) {
                compileError("Package " + getAttributeValue("name") +
                                     " requires a global context item, so it cannot be used as a library package", "XTTE0590");
            }
            usedPackage.setRootPackage(false);
        }
    }


    /**
     * Get the package to which this xsl:use-package element refers. Assumes that findPackage()
     * has already been called.
     * @return the package that is referenced.
     */

    public StylesheetPackage getUsedPackage() {
        return usedPackage;
    }

    /**
     * Get the ranges of package versions this use-package directive will accept.
     *
     * <p>This will involve processing the attributes once to derive any ranges declared (and the name of the required package).
     * If no range is defined, the catchall '*' is assumed. </p>
     *
     * @return  the ranges of versions of the named package that this declaration will accept
     * @throws XPathException
     */

    public PackageVersionRanges getPackageVersionRanges() throws XPathException {
        if (versionRanges == null) {
            try {
                prepareAttributes();
            } catch (XPathException e) {
                versionRanges = new PackageVersionRanges("*");
            }
        }
        return versionRanges;
    }


    @Override
    protected void prepareAttributes() throws XPathException {
        AttributeCollection atts = getAttributeList();
        String ranges = "*";
        for (int a = 0; a < atts.getLength(); a++) {
            String f = atts.getQName(a);
            if (f.equals("name")) {
                nameAtt = Whitespace.trim(atts.getValue(a));
            } else if (f.equals("package-version")) {
                ranges = Whitespace.trim(atts.getValue(a)).replaceAll("\\\\", "");
            } else {
                checkUnknownAttribute(atts.getNodeName(a));
            }
        }
        versionRanges = new PackageVersionRanges(ranges);
    }

    @Override
    public boolean isDeclaration() {
        return true;
    }

    public void validate(ComponentDeclaration decl) throws XPathException {
        AxisIterator kids = iterateAxis(AxisInfo.CHILD);
        NodeInfo child;
        while ((child = kids.next()) != null) {
            if (child.getNodeKind() == Type.TEXT) {
                compileError("Character content is not allowed as a child of xsl:use-package");
            } else if (child instanceof XSLAccept || child instanceof XSLOverride) {
                //ok
            } else {
                compileError("Child element " + Err.wrap(child.getDisplayName(), Err.ELEMENT) +
                        " is not allowed as a child of xsl:use-package", "XTSE0010");
            }
        }
    }

    @Override
    public void postValidate() throws XPathException {
        AxisIterator kids = iterateAxis(AxisInfo.CHILD);
        NodeInfo curr;
        while ((curr = kids.next()) != null) {
            if (curr instanceof XSLOverride || curr instanceof XSLAccept) {
                ((StyleElement) curr).postValidate();
            }
        }
    }

    /**
     * Get the child xsl:accept elements
     * @return the list of child xsl:accept elements
     */

    public List<ComponentAcceptor> getAcceptors() {
        if (this.acceptors == null) {
            acceptors = new ArrayList<ComponentAcceptor>();
            AxisIterator useKids = iterateAxis(AxisInfo.CHILD);
            NodeInfo decl;
            while ((decl = useKids.next()) != null) {
                if (decl instanceof ComponentAcceptor) {
                    acceptors.add((ComponentAcceptor) decl);
                }
            }
        }
        return acceptors;
    } ;

    /**
     * Process all the xsl:override declarations in the xsl:use-package, adding the overriding named components
     * to the list of top-level declarations
     * @param module the top-level stylesheet module of this package
     * @param topLevel the list of declarations in this package (to which this method appends)
     * @param overrides set of named components for which this xsl:use-package provides an override
     * (which this method populates).
     * @throws XPathException in the event of an error.
     */

    public void gatherNamedOverrides(PrincipalStylesheetModule module,
                                     List<ComponentDeclaration> topLevel,
                                     Set<SymbolicName> overrides)
            throws XPathException {
        if (usedPackage == null) {
            return; // error already reported
        }
        AxisIterator kids = iterateAxis(AxisInfo.CHILD, NodeKindTest.ELEMENT);
        NodeInfo override;
        while ((override = kids.next()) != null) {
            if (override instanceof XSLOverride) {
                AxisIterator overridings = override.iterateAxis(AxisInfo.CHILD, NodeKindTest.ELEMENT);
                NodeInfo overridingDeclaration;
                while ((overridingDeclaration = overridings.next()) != null) {
                    if (overridingDeclaration instanceof StylesheetComponent) {
                        ComponentDeclaration decl = new ComponentDeclaration(module, (StyleElement) overridingDeclaration);
                        topLevel.add(decl);
                        SymbolicName name = ((StylesheetComponent) overridingDeclaration).getSymbolicName();
                        if (name != null) {
                            overrides.add(name);
                        }
                    }
                }
            }
        }
    }

    /**
     * Process all the xsl:override declarations in the xsl:use-package, adding the overriding template rules
     * to the list of top-level declarations
     *
     * @param module    the top-level stylesheet module of this package (the using package)
     * @param overrides set of named components for which this xsl:use-package provides an override
     * (which this method populates). If the xsl:override contains any template rules, then the named
     * mode will be included in this list, but the individual template rules will not be added to
     * the top-level list.
     * @throws XPathException in the event of an error.
     */

    public void gatherRuleOverrides(PrincipalStylesheetModule module,
                                    List<ComponentAcceptor> acceptors, Set<SymbolicName> overrides)
            throws XPathException {
        StylesheetPackage thisPackage = module.getStylesheetPackage();
        RuleManager ruleManager = module.getRuleManager();
        AxisIterator kids = iterateAxis(AxisInfo.CHILD, NodeKindTest.ELEMENT);
        Set<SymbolicName> overriddenModes = new HashSet<SymbolicName>();

        // Process all template rules within xsl:override elements
        NodeInfo override;
        while ((override = kids.next()) != null) {
            if (override instanceof XSLOverride) {
                AxisIterator overridings = override.iterateAxis(AxisInfo.CHILD, NodeKindTest.ELEMENT);
                NodeInfo overridingDeclaration;
                while ((overridingDeclaration = overridings.next()) != null) {
                    //if (overridingDeclaration instanceof StylesheetComponent) {
                        if (overridingDeclaration instanceof XSLTemplate && overridingDeclaration.getAttributeValue("", "match") != null) {
                            StructuredQName[] modeNames = ((XSLTemplate)overridingDeclaration).getModeNames();
                            for (StructuredQName modeName : modeNames) {
                                SymbolicName symbolicName = new SymbolicName(StandardNames.XSL_MODE, modeName);
                                overrides.add(symbolicName);
                                Mode newBaseMode = ruleManager.obtainMode(modeName, false);
                                if (newBaseMode == null) {
                                    overriddenModes.add(symbolicName);
                                    Component usedComponent = usedPackage.getComponent(symbolicName);
                                    if (usedComponent == null) {
                                        ((StyleElement) overridingDeclaration).compileError(
                                            "Mode " + modeName.getDisplayName() + " is not defined in the used package");
                                        continue;
                                    }
                                    Mode usedMode = (Mode)usedComponent.getActor();
                                    if (usedComponent.getVisibility() != Visibility.PUBLIC) {
                                        ((StyleElement) overridingDeclaration).compileError(
                                            "Cannot override template rules in mode " + modeName.getDisplayName() +
                                                ", because the mode is not public", "XTSE3060");
                                        continue;
                                    }
                                    SimpleMode overridingMode = new SimpleMode(modeName);
                                    CompoundMode newCompoundMode = new CompoundMode(usedMode, overridingMode);
                                    ruleManager.registerMode(newCompoundMode);
                                    Component newModeComponent =
                                        newCompoundMode.makeDeclaringComponent(Visibility.PUBLIC, thisPackage);
                                    for (ComponentAcceptor acceptor : acceptors) {
                                        acceptor.acceptComponent(newModeComponent);
                                    }
                                    if (newModeComponent.getVisibility() != Visibility.PUBLIC) {
                                        ((StyleElement) overridingDeclaration).compileError(
                                            "Cannot override template rules in mode " + modeName.getDisplayName() +
                                                ", because the mode (as defined in xsl:accept) is not public", "XTSE3060");
                                        continue;
                                    }
                                    thisPackage.getComponentIndex().put(symbolicName, newModeComponent);
                                    //compoundMode.setBaseComponent(baseComponent);
                                    //base.allocateAllBindingSlots(module.getStylesheetPackage());
                                    newCompoundMode.allocateAllBindingSlots(thisPackage);
                                }
                            }
                        }
                    //}
                }
            }
        }

        // Now process all public/final modes in the used package that have not been overridden by new template rules

        RuleManager usedPackageRuleManager = usedPackage.getRuleManager();
        if (usedPackageRuleManager != null) {
            for (Mode m : usedPackageRuleManager.getAllNamedModes()) {
                SymbolicName sn = m.getSymbolicName();
                if (!overriddenModes.contains(sn)) {
                    Component c = thisPackage.getComponent(sn);
                    if (c != null && c.getVisibility() != Visibility.PRIVATE) {
                        ruleManager.registerMode((Mode) c.getActor());
                    }
                }
            }
        }
    }


}

