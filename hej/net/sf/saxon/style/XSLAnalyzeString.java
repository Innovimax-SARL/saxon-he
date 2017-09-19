////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.Version;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StringLiteral;
import net.sf.saxon.expr.instruct.AnalyzeString;
import net.sf.saxon.om.AttributeCollection;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.regex.RegularExpression;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;

import java.util.ArrayList;
import java.util.List;

/**
 * An xsl:analyze-string elements in the stylesheet. New at XSLT 2.0<BR>
 */

public class XSLAnalyzeString extends StyleElement {

    /*@Nullable*/ private Expression select;
    private Expression regex;
    private Expression flags;
    private StyleElement matching;
    private StyleElement nonMatching;
    private RegularExpression pattern;

    /**
     * Determine whether this node is an instruction.
     *
     * @return true - it is an instruction
     */

    public boolean isInstruction() {
        return true;
    }

    /**
     * Determine whether this type of element is allowed to contain an xsl:fallback
     * instruction
     */

    public boolean mayContainFallback() {
        return true;
    }


    public void prepareAttributes() throws XPathException {
        String selectAtt = null;
        String regexAtt = null;
        String flagsAtt = null;

        AttributeCollection atts = getAttributeList();

        for (int a = 0; a < atts.getLength(); a++) {
            String f = atts.getQName(a);
            if (f.equals("regex")) {
                regexAtt = atts.getValue(a);
                regex = makeAttributeValueTemplate(regexAtt, a);

            } else if (f.equals("select")) {
                selectAtt = atts.getValue(a);
                select = makeExpression(selectAtt, a);
            } else if (f.equals("flags")) {
                flagsAtt = atts.getValue(a); // not trimmed, see bugzilla 4315
                flags = makeAttributeValueTemplate(flagsAtt, a);
            } else {
                checkUnknownAttribute(atts.getNodeName(a));
            }
        }

        if (selectAtt == null) {
            reportAbsence("select");
            select = makeExpression(".", -1);    // for error recovery
        }

        if (regexAtt == null) {
            reportAbsence("regex");
            regex = makeAttributeValueTemplate("xxx", -1);  // for error recovery
        }

        if (flagsAtt == null) {
            flagsAtt = "";
            flags = makeAttributeValueTemplate("", -1);
        }


        if (regex instanceof StringLiteral && flags instanceof StringLiteral) {
            try {
                final String regex = ((StringLiteral) this.regex).getStringValue();
                final String flagstr = ((StringLiteral) flags).getStringValue();

                List<String> warnings = new ArrayList<String>();
                pattern = Version.platform.compileRegularExpression(
                        getConfiguration(), regex, flagstr, getEffectiveVersion() >= 30 ? "XP30" : "XP20", warnings);
                for (String w : warnings) {
                    issueWarning(w, this);
                }
            } catch (XPathException err) {
                if ("FORX0001".equals(err.getErrorCodeLocalPart())) {
                    invalidFlags("Error in regular expression flags: " + err.getMessage());
                } else {
                    invalidRegex("Error in regular expression: " + err.getMessage());
                }
            }
        }

    }

    private void invalidRegex(String message) throws XPathException {
        compileErrorInAttribute(message, "XTDE1140", "regex");
        // prevent it being reported more than once
        pattern = Version.platform.compileRegularExpression(getConfiguration(), "x", "", "XP20", null);
    }

    private void invalidFlags(String message) throws XPathException {
        compileErrorInAttribute(message, "XTDE1145", "flags");
        // prevent it being reported more than once
        pattern = Version.platform.compileRegularExpression(getConfiguration(), "x", "", "XP20", null);
    }

    public void validate(ComponentDeclaration decl) throws XPathException {
        //checkWithinTemplate();

        AxisIterator kids = iterateAxis(AxisInfo.CHILD);
        boolean foundFallback = false;
        while (true) {
            NodeInfo curr = kids.next();
            if (curr == null) {
                break;
            }
            if (curr instanceof XSLFallback) {
                foundFallback = true;
            } else if (curr instanceof XSLMatchingSubstring) {
                boolean b = curr.getLocalPart().equals("matching-substring");
                if (b) {
                    if (matching != null || nonMatching != null || foundFallback) {
                        compileError("xsl:matching-substring element must come first", "XTSE0010");
                    }
                    matching = (StyleElement) curr;
                } else {
                    if (nonMatching != null || foundFallback) {
                        compileError("xsl:non-matching-substring cannot appear here", "XTSE0010");
                    }
                    nonMatching = (StyleElement) curr;
                }
            } else {
                compileError("Only xsl:matching-substring and xsl:non-matching-substring are allowed here", "XTSE0010");
            }
        }

        if (matching == null && nonMatching == null) {
            compileError("At least one xsl:matching-substring or xsl:non-matching-substring element must be present",
                    "XTSE1130");
        }

        select = typeCheck("select", select);
        regex = typeCheck("regex", regex);
        flags = typeCheck("flags", flags);

    }

    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        Expression matchingBlock = null;
        if (matching != null) {
            matchingBlock = matching.compileSequenceConstructor(exec, decl, false);
        }

        Expression nonMatchingBlock = null;
        if (nonMatching != null) {
            nonMatchingBlock = nonMatching.compileSequenceConstructor(exec, decl, false);
        }

        try {
            return new AnalyzeString(select,
                    regex,
                    flags,
                    matchingBlock == null ? null : matchingBlock.simplify(),
                    nonMatchingBlock == null ? null : nonMatchingBlock.simplify(),
                    pattern);
        } catch (XPathException e) {
            compileError(e);
            return null;
        }
    }


}

