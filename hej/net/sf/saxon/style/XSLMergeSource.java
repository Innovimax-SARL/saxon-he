////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.accum.Accumulator;
import net.sf.saxon.expr.accum.AccumulatorRegistry;
import net.sf.saxon.expr.sort.MergeInstr;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.AttributeCollection;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.NameChecker;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.Whitespace;

import java.util.HashSet;
import java.util.Set;

/**
 * Implements the xsl:merge-source element available in XSLT 3.0 as a child of xsl:merge.
 * <p/>
 * March 2012: xsl:merge-source and xsl:merge-input combined into a single element
 */

public class XSLMergeSource extends StyleElement {


    private Expression forEachItem;
    private Expression forEachSource;
    private Expression select;
    private boolean sortBeforeMerge = false;
    private int mergeKeyCount = 0;
    private String sourceName;
    private int validationAction = Validation.STRIP;
    private SchemaType schemaType = null;
    private boolean streamable = false;
    private Set<Accumulator> accumulators = new HashSet<Accumulator>();


    /**
     * Determine whether this node is an instruction.
     *
     * @return true - it is an instruction
     */

    public boolean isInstruction() {
        return false;
    }

    /**
     * Determine whether this type of element is allowed to contain a sequence constructor
     *
     * @return false: no, it may not contain a sequence constructor
     */

    public boolean mayContainSequenceConstructor() {
        return false;
    }

    /**
     * Get the for-each-item expression, if it exists
     *
     * @return the for-each-item expression, if defined, or null otherwise
     */

    public Expression getForEachItem() {
        return forEachItem;
    }

    /**
     * Get the for-each-stream expression, if it exists
     *
     * @return the for-each-stream expression, if defined, or null otherwise
     */

    public Expression getForEachSource() {
        return forEachSource;
    }

    /**
     * Get the select expression
     *
     * @return the select expression. Never null.
     */

    public Expression getSelect() {
        return select;
    }

    /**
     * Ask whether the sort-before-merge option is set
     *
     * @return true if the input sequence is to be sorted before merging
     */
    public boolean isSortBeforeMerge() {
        return sortBeforeMerge;
    }

    /**
     * Get the name of the merge source, or null if not specified
     * @return the value of the @name attribute, or null if the attribute was absent
     */

    public String getSourceName() {
        return sourceName;
    }

    /**
     * Get the value of the validation attribute, if present
     * @return the value of the validation attribute
     */

    public int getValidationAction() {
        return validationAction;
    }

    /**
     * Get the value of the type attribute, if present
     * @return the value of the type attribute
     */

    public SchemaType getSchemaTypeAttribute() {
        return schemaType;
    }

    public MergeInstr.MergeSource makeMergeSource(MergeInstr mi, Expression select) {
        MergeInstr.MergeSource ms = new MergeInstr.MergeSource(mi);
        if (forEachItem != null) {
            ms.initForEachItem(mi, forEachItem);
        }
        if (forEachSource != null) {
            ms.initForEachStream(mi, forEachSource);
        }
        if (select != null) {
            this.select = select;
            ms.initRowSelect(mi, select);
        }
        ms.baseURI = getBaseURI();
        ms.sourceName = sourceName;
        ms.validation = validationAction;
        ms.schemaType = schemaType;
        ms.streamable = streamable;
        ms.accumulators = accumulators;
        return ms;
    }

    @Override
    public Expression compile(Compilation exec, ComponentDeclaration decl)
            throws XPathException {
        return null;
    }

    @Override
    protected void prepareAttributes() throws XPathException {
        AttributeCollection atts = getAttributeList();

        String selectAtt = null;
        String forEachItemAtt = null;
        String forEachSourceAtt = null;
        String validationAtt = null;
        String typeAtt = null;
        String streamableAtt = null;
        String useAccumulatorsAtt = null;

        for (int a = 0; a < atts.getLength(); a++) {
            String f = atts.getQName(a);
            if (f.equals("for-each-item")) {
                forEachItemAtt = atts.getValue(a);
                forEachItem = makeExpression(forEachItemAtt, a);
            } else if (f.equals("for-each-stream") || f.equals("for-each-source")) {
                // renamed 2016-09 - retain synonym for now
                forEachSourceAtt = atts.getValue(a);
                forEachSource = makeExpression(forEachSourceAtt, a);
            } else if (f.equals("select")) {
                selectAtt = atts.getValue(a);
                select = makeExpression(selectAtt, a);
            } else if (f.equals("sort-before-merge")) {
                sortBeforeMerge = processBooleanAttribute("sort-before-merge", atts.getValue(a));
            } else if (f.equals("name")) {
                String nameAtt = Whitespace.trim(atts.getValue(a));
                if (NameChecker.isValidNCName(nameAtt)) {
                    sourceName = nameAtt;
                } else {
                    compileError("xsl:merge-source/@name (" + nameAtt + ") is not a valid NCName", "XTSE0020");
                }
            } else if (f.equals("validation")) {
                validationAtt = Whitespace.trim(atts.getValue(a));
            } else if (f.equals("type")) {
                typeAtt = Whitespace.trim(atts.getValue(a));
            } else if (f.equals("streamable")) {
                streamableAtt = atts.getValue(a);
            } else if (f.equals("use-accumulators")) {
                useAccumulatorsAtt = Whitespace.trim(atts.getValue(a));
            } else {
                checkUnknownAttribute(atts.getNodeName(a));
            }
        }

        if (sourceName == null) {
            sourceName = "saxon-merge-source-" + hashCode();
        }

        if (forEachItemAtt != null) {
            if (forEachSourceAtt != null) {
                compileError("The for-each-item and for-each-source attributes must not both be present", "XTSE3195");
            }
        }

        if (selectAtt == null) {
            reportAbsence("select");
        }

        if (validationAtt == null) {
            validationAction = getDefaultValidation();
        } else {
            validationAction = validateValidationAttribute(validationAtt);
        }
        if (typeAtt != null) {
            if (!isSchemaAware()) {
                compileError("The @type attribute is available only with a schema-aware XSLT processor", "XTSE1660");
            }
            schemaType = getSchemaType(typeAtt);
            validationAction = Validation.BY_TYPE;
        }

        if (typeAtt != null && validationAtt != null) {
            compileError("The @validation and @type attributes are mutually exclusive", "XTSE1505");
        }

        if ((typeAtt != null || validationAtt != null) && forEachSourceAtt == null) {
            compileError("The @type and @validation attributes can be used only when @for-each-stream is specified", "XTSE0020");
        }

        if (streamableAtt != null) {
            streamable = processStreamableAtt(streamableAtt);
            if (streamable && forEachSource == null) {
                compileError("Streaming on xsl:merge-source is possible only when @for-each-source is used", "XTSE3195");
            }
        } else if (forEachSource != null) {
            streamable = true;
        }

        if (useAccumulatorsAtt == null) {
            useAccumulatorsAtt = "";
        }

        AccumulatorRegistry registry = getPrincipalStylesheetModule().getStylesheetPackage().getAccumulatorRegistry();
        accumulators = registry.getUsedAccumulators(useAccumulatorsAtt, this);

    }

    public void validate(ComponentDeclaration decl) throws XPathException {

        forEachItem = typeCheck("for-each-item", forEachItem);
        forEachSource = typeCheck("for-each-source", forEachSource);
        select = typeCheck("select", select);

        AxisIterator kids = iterateAxis(AxisInfo.CHILD);
        NodeInfo child;
        while ((child = kids.next()) != null) {
            if (child instanceof XSLMergeKey) {
                mergeKeyCount++;
            } else if (child.getNodeKind() == Type.TEXT) {
                // with xml:space=preserve, white space nodes may still be there
                if (!Whitespace.isWhite(child.getStringValueCS())) {
                    compileError("No character data is allowed within xsl:merge-source", "XTSE0010");
                }
            } else if (child instanceof StyleElement) {
                ((StyleElement) child).compileError("No children other than xsl:merge-key are allowed within xsl:merge-source", "XTSE0010");
            }
        }

        if (mergeKeyCount == 0) {
            compileError("xsl:merge-source must have exactly at least one xsl:merge-key child element", "XTSE0010");
        }

    }

}