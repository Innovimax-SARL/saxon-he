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
import net.sf.saxon.expr.accum.Accumulator;
import net.sf.saxon.expr.accum.AccumulatorRegistry;
import net.sf.saxon.expr.instruct.ForEach;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.functions.registry.VendorFunctionSetHE;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.ma.map.HashTrieMap;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.value.QNameValue;
import net.sf.saxon.value.SequenceExtent;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.value.Whitespace;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Handler for xsl:source-document element in XSLT 3.0 stylesheet. <br>
 */

public class XSLSourceDocument extends StyleElement {

    private Expression href = null;
    private Set<Accumulator> accumulators = new HashSet<Accumulator>();
    private boolean streaming = true;
    private ParseOptions parseOptions;


    /**
     * Determine whether this node is an instruction.
     *
     * @return true - it is an instruction
     */

    public boolean isInstruction() {
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

    protected boolean isWithinDeclaredStreamableConstruct() {
        return true;
    }


    public void prepareAttributes() throws XPathException {

        parseOptions = new ParseOptions(getConfiguration().getParseOptions());

        AttributeCollection atts = getAttributeList();

        String hrefAtt = null;
        String validationAtt = null;
        String typeAtt = null;
        String useAccumulatorsAtt = null;
        String streamableAtt = null;

        for (int a = 0; a < atts.getLength(); a++) {
            String f = atts.getQName(a);
            if (f.equals("href")) {
                hrefAtt = atts.getValue(a);
                href = makeAttributeValueTemplate(hrefAtt, a);
            } else if (f.equals("validation")) {
                validationAtt = Whitespace.trim(atts.getValue(a));
            } else if (f.equals("type")) {
                typeAtt = Whitespace.trim(atts.getValue(a));
            } else if (f.equals("use-accumulators")) {
                useAccumulatorsAtt = Whitespace.trim(atts.getValue(a));
            } else if (f.equals("streamable")) {
                streamableAtt = Whitespace.trim(atts.getValue(a));
            } else if (NamespaceConstant.SAXON.equals(atts.getURI(a))) {
                String local = atts.getLocalName(a);
                if (local.equals("dtd-validation")) {
                    parseOptions.setDTDValidationMode(processBooleanAttribute(f, atts.getValue(a)) ? Validation.STRICT : Validation.SKIP);
                } else if (local.equals("expand-attribute-defaults")) {
                    parseOptions.setExpandAttributeDefaults(processBooleanAttribute(f, atts.getValue(a)));
                } else if (local.equals("line-numbering")) {
                    parseOptions.setLineNumbering(processBooleanAttribute(f, atts.getValue(a)));
                } else if (local.equals("xinclude")) {
                    parseOptions.setXIncludeAware(processBooleanAttribute(f, atts.getValue(a)));
//                } else if (local.equals("tree-model")) {
//                    List<TreeModel> models = getConfiguration().getExternalObjectModels()
//                    parseOptions.setModel(processBooleanAttribute(f, atts.getValue(a)));
                } else if (local.equals("validation-params")) {
                   // TODO
                } else if (local.equals("strip-space")) {
                    String value = Whitespace.normalizeWhitespace(atts.getValue(a)).toString();
                    if (value.equals("#all")) {
                        parseOptions.setSpaceStrippingRule(AllElementsSpaceStrippingRule.getInstance());
                    } else if (value.equals("#none")) {
                        parseOptions.setSpaceStrippingRule(NoElementsSpaceStrippingRule.getInstance());
                    } else if (value.equals("#ignorable")) {
                        parseOptions.setSpaceStrippingRule(IgnorableSpaceStrippingRule.getInstance());
                    } else {
                        // ???
                    }
                } else {
                    checkUnknownAttribute(atts.getNodeName(a));
                }
            } else {
                checkUnknownAttribute(atts.getNodeName(a));
            }
        }

        if (hrefAtt == null) {
            reportAbsence("href");
        }

        if (validationAtt != null) {
            int validation = validateValidationAttribute(validationAtt);
            parseOptions.setSchemaValidationMode(validation);
        }
        
        if (typeAtt != null) {
            if (!isSchemaAware()) {
                compileError("The @type attribute is available only with a schema-aware XSLT processor", "XTSE1660");
            }
            parseOptions.setSchemaValidationMode(Validation.BY_TYPE);
            parseOptions.setTopLevelType(getSchemaType(typeAtt));
        }

        if (typeAtt != null && validationAtt != null) {
            compileError("The @validation and @type attributes are mutually exclusive", "XTSE1505");
        }

        if (streamableAtt != null) {
            streaming = processBooleanAttribute("streamable", streamableAtt);
        } else {
            streaming = getLocalPart().equals("stream"); // retained for compatibility with XSLT 3.0 draft spec
        }

        if (useAccumulatorsAtt == null) {
            useAccumulatorsAtt = "";
        }

        AccumulatorRegistry registry = getPrincipalStylesheetModule().getStylesheetPackage().getAccumulatorRegistry();
        accumulators = ((AccumulatorRegistry) registry).getUsedAccumulators(useAccumulatorsAtt, this);

    }


    public void validate(ComponentDeclaration decl) throws XPathException {
        //checkParamComesFirst(false);
        href = typeCheck("select", href);
        if (!hasChildNodes()) {
            compileWarning("An empty xsl:source-document instruction has no effect", SaxonErrorCode.SXWN9009);
        }
    }

    /*@Nullable*/
    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        Configuration config = getConfiguration();
        parseOptions.setSpaceStrippingRule(getPackageData().getSpaceStrippingRule());
        parseOptions.setApplicableAccumulators(accumulators);
        Expression action = compileSequenceConstructor(exec, decl, false);
        if (action == null) {
            // body of xsl:source-document is empty: it's a no-op.
            return Literal.makeEmptySequence();
        }
        try {
            ExpressionVisitor visitor = makeExpressionVisitor();
            action = action.simplify();
            action = action.typeCheck(visitor, config.makeContextItemStaticInfo(NodeKindTest.DOCUMENT, false));

            if (streaming) {
                return config.makeStreamInstruction(
                        href, action, parseOptions, null, allocateLocation(),
                        makeRetainedStaticContext());
            }

            // make a non-streaming implementation of the instruction
            //Expression docCall = SystemFunction.makeCall("doc", makeRetainedStaticContext(), href);
            HashTrieMap options = new HashTrieMap();
            int validation = parseOptions.getSchemaValidationMode();
            if (validation == Validation.DEFAULT) {
                validation = Validation.STRIP;
            }
            options = options.addEntry(
                    StringValue.makeStringValue("validation"),
                    StringValue.makeStringValue(Validation.toString(validation)));
            if (parseOptions.getTopLevelType() != null) {
                options = options.addEntry(
                        StringValue.makeStringValue("type"),
                        new QNameValue(parseOptions.getTopLevelType().getStructuredQName(), BuiltInAtomicType.QNAME));
            }
            if (parseOptions.getSpaceStrippingRule() != null) {
                SpaceStrippingRule rule = parseOptions.getSpaceStrippingRule();
                String str = null;
                if (rule == AllElementsSpaceStrippingRule.getInstance()) {
                    str = "all";
                } else if (rule == NoElementsSpaceStrippingRule.getInstance()) {
                    str = "none";
                } else if (rule instanceof SelectedElementsSpaceStrippingRule) {
                    str = "package-defined";
                }
                options = options.addEntry(
                        StringValue.makeStringValue("strip-space"),
                        StringValue.makeStringValue(str));
            }
            if (accumulators != null) {
                List<QNameValue> list = new ArrayList<QNameValue>();
                for (Accumulator acc : accumulators) {
                    list.add(new QNameValue(acc.getAccumulatorName(), BuiltInAtomicType.QNAME));
                }
                options = options.addEntry(StringValue.makeStringValue("accumulators"),
                                           SequenceExtent.makeSequenceExtent(list));
            }
            SystemFunction docFn = VendorFunctionSetHE.getInstance().makeFunction("doc", 2);
            docFn.setRetainedStaticContext(makeRetainedStaticContext());
            Expression docCall = docFn.makeFunctionCall(href, Literal.makeLiteral(options));
            docCall.setRetainedStaticContext(makeRetainedStaticContext());
            docCall.setLocation(allocateLocation());
            return new ForEach(docCall, action);

        } catch (XPathException err) {
            compileError(err);
            return null;
        }
    }

}
