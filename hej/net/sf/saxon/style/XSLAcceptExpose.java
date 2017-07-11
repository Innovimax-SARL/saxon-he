////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.om.*;
import net.sf.saxon.pattern.*;
import net.sf.saxon.trans.ComponentTest;
import net.sf.saxon.trans.Visibility;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.Whitespace;

import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * Handler for xsl:accept and xsl:expose elements in stylesheet.
 */
public abstract class XSLAcceptExpose extends StyleElement {

    private Set<ComponentTest> explicitComponentTests = new HashSet<ComponentTest>();
    private Set<ComponentTest> wildcardComponentTests = new HashSet<ComponentTest>();
    private Visibility visibility;

    public Visibility getVisibility() {
        return visibility;
    }

    public Set<ComponentTest> getExplicitComponentTests() throws XPathException {
        prepareAttributes();
        return explicitComponentTests;
    }

    public Set<ComponentTest> getWildcardComponentTests() throws XPathException {
        prepareAttributes();
        return wildcardComponentTests;
    }

    @Override
    protected void prepareAttributes() throws XPathException {

        if (visibility != null) {
            return;
        }

        String componentAtt = null;
        String namesAtt = null;
        String visibilityAtt = null;

        AttributeCollection atts = getAttributeList();
        for (int a = 0; a < atts.getLength(); a++) {
            String f = atts.getQName(a);
            if (f.equals("names")) {
                namesAtt = Whitespace.trim(atts.getValue(a));
            } else if (f.equals("component")) {
                componentAtt = Whitespace.trim(atts.getValue(a));
            } else if (f.equals("visibility")) {
                visibilityAtt = Whitespace.trim(atts.getValue(a));
            } else {
                checkUnknownAttribute(atts.getNodeName(a));
            }
        }

        if (visibilityAtt == null) {
            reportAbsence("visibility");
            visibility = Visibility.PRIVATE;
        } else {
            visibility = interpretVisibilityValue(visibilityAtt, this instanceof XSLAccept ? "ha" : "");
            if (visibility == null) {
                visibility = Visibility.PRIVATE; // fall back in case of errors
            }
        }


        int componentTypeCode = StandardNames.XSL_FUNCTION;
        if (componentAtt == null) {
            reportAbsence("component");
        } else {
            String local = Whitespace.trim(componentAtt);
            if (local.equals("function")) {
                componentTypeCode = StandardNames.XSL_FUNCTION;
            } else if (local.equals("template")) {
                componentTypeCode = StandardNames.XSL_TEMPLATE;
            } else if (local.equals("variable")) {
                componentTypeCode = StandardNames.XSL_VARIABLE;
            } else if (local.equals("attribute-set")) {
                componentTypeCode = StandardNames.XSL_ATTRIBUTE_SET;
            } else if (local.equals("mode")) {
                componentTypeCode = StandardNames.XSL_MODE;
            } else if (local.equals("*")) {
                // spec change bug 29478
                componentTypeCode = -1;
            } else {
                compileError("The component type is not one of the allowed names (function, template, variable, attribute-set, or mode)",
                             "XTSE0020");
                return;
            }
        }

        if (namesAtt == null) {
            reportAbsence("names");
            namesAtt = "";
        }
        StringTokenizer st = new StringTokenizer(namesAtt, " \t\r\n");
        while (st.hasMoreTokens()) {
            final String tok = st.nextToken();
            QNameTest test;
            int hash = tok.lastIndexOf('#');
            if (hash > 0 && tok.indexOf('}', hash) < 0) {   // ignore any '#' within a namespace URI of an EQName
                if (componentTypeCode == -1) {
                    compileError("When component='*' is specified, all names must be wildcards",
                                 this instanceof XSLAccept ? "XTSE3032" : "XTSE3022");
                } else {
                    StructuredQName name;
                    try {
                        name = makeQName(tok.substring(0, hash));
                    } catch (NamespaceException e) {
                        compileError("Invalid QName in names attribute: " + tok + " (" + e.getMessage() + ")");
                        name = NameOfNode.makeName(this).getStructuredQName(); // error recovery
                    }
                    test = new NameTest(Type.ELEMENT, name.getURI(), name.getLocalPart(), getNamePool());
                    int arity = 0;
                    try {
                        arity = Integer.parseInt(tok.substring(hash + 1));
                    } catch (Exception err) {
                        compileError("Malformed function arity in '" + tok + "'");
                    }
                    explicitComponentTests.add(new ComponentTest(componentTypeCode, test, arity));
                }

            } else if (tok.equals("*")) {
                test = AnyNodeTest.getInstance();
                addWildCardTest(componentTypeCode, test);
            } else if (tok.endsWith(":*")) {
                if (tok.length() == 2) {
                    compileError("No prefix before ':*'");
                }
                String prefix = tok.substring(0, tok.length() - 2);
                final String uri = getURIForPrefix(prefix, false);
                test = new NamespaceTest(getNamePool(), Type.ELEMENT, uri);
                addWildCardTest(componentTypeCode, test);
            } else if (tok.startsWith("Q{") && tok.endsWith("}*")) {
                final String uri = tok.substring(2, tok.length() - 2);
                test = new NamespaceTest(getNamePool(), Type.ELEMENT, uri);
                wildcardComponentTests.add(new ComponentTest(componentTypeCode, test, -1));
            } else if (tok.startsWith("*:")) {
                if (tok.length() == 2) {
                    compileError("No local name after '*:'");
                }
                final String localname = tok.substring(2);
                test = new LocalNameTest(getNamePool(), Type.ELEMENT, localname);
                addWildCardTest(componentTypeCode, test);

            } else {
                if (componentTypeCode == -1) {
                    compileError("When component='*' is specified, all names must be wildcards",
                                 this instanceof XSLAccept ? "XTSE3032" : "XTSE3022");
                } else {
                    StructuredQName name;
                    try {
                        name = makeQName(tok);
                    } catch (NamespaceException e) {
                        compileError("Invalid QName in names attribute: " + tok + " (" + e.getMessage() + ")");
                        name = NameOfNode.makeName(this).getStructuredQName(); // error recovery
                    }
                    test = new NameTest(Type.ELEMENT, name.getURI(), name.getLocalPart(), getNamePool());
                    explicitComponentTests.add(new ComponentTest(componentTypeCode, test, -1));
                }
            }

        }
    }

    private void addWildCardTest(int componentTypeCode, QNameTest test) {
        if (componentTypeCode == -1) {
            wildcardComponentTests.add(new ComponentTest(StandardNames.XSL_FUNCTION, test, -1));
            wildcardComponentTests.add(new ComponentTest(StandardNames.XSL_TEMPLATE, test, -1));
            wildcardComponentTests.add(new ComponentTest(StandardNames.XSL_VARIABLE, test, -1));
            wildcardComponentTests.add(new ComponentTest(StandardNames.XSL_ATTRIBUTE_SET, test, -1));
            wildcardComponentTests.add(new ComponentTest(StandardNames.XSL_MODE, test, -1));
        } else {
            wildcardComponentTests.add(new ComponentTest(componentTypeCode, test, -1));
        }
    }




}
