////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.ContextItemExpression;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.NumberSequenceFormatter;
import net.sf.saxon.expr.StringLiteral;
import net.sf.saxon.expr.instruct.NumberInstruction;
import net.sf.saxon.expr.instruct.ValueOf;
import net.sf.saxon.expr.number.NumberFormatter;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.om.AttributeCollection;
import net.sf.saxon.pattern.Pattern;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.Whitespace;

/**
 * An xsl:number element in the stylesheet. <br>
 */

public class XSLNumber extends StyleElement {



    private int level;
    /*@Nullable*/ private Pattern count = null;
    private Pattern from = null;
    private Expression select = null;
    private Expression value = null;
    private Expression format = null;
    private Expression groupSize = null;
    private Expression groupSeparator = null;
    private Expression letterValue = null;
    private Expression lang = null;
    private Expression ordinal = null;
    private Expression startAt = null;
    private NumberFormatter formatter = null;

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

        String selectAtt = null;
        String valueAtt = null;
        String countAtt = null;
        String fromAtt = null;
        String levelAtt = null;
        String formatAtt = null;
        String gsizeAtt = null;
        String gsepAtt = null;
        String langAtt = null;
        String letterValueAtt = null;
        String ordinalAtt = null;
        String startAtAtt = null;

        for (int a = 0; a < atts.getLength(); a++) {
            String f = atts.getQName(a);
            if (f.equals("select")) {
                selectAtt = atts.getValue(a);
                select = makeExpression(selectAtt, a);
            } else if (f.equals("value")) {
                valueAtt = atts.getValue(a);
                value = makeExpression(valueAtt, a);
            } else if (f.equals("count")) {
                countAtt = atts.getValue(a);
            } else if (f.equals("from")) {
                fromAtt = atts.getValue(a);
            } else if (f.equals("level")) {
                levelAtt = Whitespace.trim(atts.getValue(a));
            } else if (f.equals("format")) {
                formatAtt = atts.getValue(a);
                format = makeAttributeValueTemplate(formatAtt, a);
            } else if (f.equals("lang")) {
                langAtt = atts.getValue(a);
                lang = makeAttributeValueTemplate(langAtt, a);
            } else if (f.equals("letter-value")) {
                letterValueAtt = Whitespace.trim(atts.getValue(a));
                letterValue = makeAttributeValueTemplate(letterValueAtt, a);
            } else if (f.equals("grouping-size")) {
                gsizeAtt = Whitespace.trim(atts.getValue(a));
            } else if (f.equals("grouping-separator")) {
                gsepAtt = atts.getValue(a);
            } else if (f.equals("ordinal")) {
                ordinalAtt = atts.getValue(a);
                ordinal = makeAttributeValueTemplate(ordinalAtt, a);
            } else if (f.equals("start-at")) {
                startAtAtt = atts.getValue(a);
                startAt = makeAttributeValueTemplate(startAtAtt, a);
            } else {
                checkUnknownAttribute(atts.getNodeName(a));
            }
        }


        if (valueAtt != null) {
            if (selectAtt != null) {
                compileError("The select attribute and value attribute must not both be present", "XTSE0975");
            }
            if (countAtt != null) {
                compileError("The count attribute and value attribute must not both be present", "XTSE0975");
            }
            if (fromAtt != null) {
                compileError("The from attribute and value attribute must not both be present", "XTSE0975");
            }
            if (levelAtt != null) {
                compileError("The level attribute and value attribute must not both be present", "XTSE0975");
            }
        }

        if (countAtt != null) {
            count = makePattern(countAtt, "count");
        }

        if (fromAtt != null) {
            from = makePattern(fromAtt, "from");
        }

        if (levelAtt == null) {
            level = NumberInstruction.SINGLE;
        } else if (levelAtt.equals("single")) {
            level = NumberInstruction.SINGLE;
        } else if (levelAtt.equals("multiple")) {
            level = NumberInstruction.MULTI;
        } else if (levelAtt.equals("any")) {
            level = NumberInstruction.ANY;
        } else {
            invalidAttribute("level", "single|any|multiple");
        }

        if (level == NumberInstruction.SINGLE && from == null && count == null) {
            level = NumberInstruction.SIMPLE;
        }

        if (formatAtt != null) {
            if (format instanceof StringLiteral) {
                formatter = new NumberFormatter();
                formatter.prepare(((StringLiteral) format).getStringValue());
            }
            // else we'll need to allocate the formatter at run-time
        } else {
            formatter = new NumberFormatter();
            formatter.prepare("1");
        }

        if (gsepAtt != null && gsizeAtt != null) {
            // the spec says that if only one is specified, it is ignored
            groupSize = makeAttributeValueTemplate(gsizeAtt, getAttributeList().getIndex("", "group-size"));
            groupSeparator = makeAttributeValueTemplate(gsepAtt, getAttributeList().getIndex("", "group-separator"));
        }

        if (startAtAtt != null) {
            if (startAtAtt.indexOf('{') < 0 && !startAtAtt.matches("-?[0-9]+(\\s+-?[0-9]+)*")) {
                compileErrorInAttribute("Invalid format for start-at attribute", "XTSE0020", "start-at");
            }
        } else {
            startAt = new StringLiteral("1");
        }

    }

    public void validate(ComponentDeclaration decl) throws XPathException {
        checkEmpty();

        select = typeCheck("select", select);
        value = typeCheck("value", value);
        format = typeCheck("format", format);
        groupSize = typeCheck("group-size", groupSize);
        groupSeparator = typeCheck("group-separator", groupSeparator);
        letterValue = typeCheck("letter-value", letterValue);
        ordinal = typeCheck("ordinal", ordinal);
        lang = typeCheck("lang", lang);
        from = typeCheck("from", from);
        count = typeCheck("count", count);
        startAt = typeCheck("start-at", startAt);

        String errorCode = "XTTE1000";
        if (value == null && select == null) {
            errorCode = "XTTE0990";
            ContextItemExpression implicitSelect = new ContextItemExpression();
            implicitSelect.setLocation(allocateLocation());
            implicitSelect.setErrorCodeForUndefinedContext(errorCode);
            select = implicitSelect;
        }

        if (select != null) {
            try {
                RoleDiagnostic role =
                        new RoleDiagnostic(RoleDiagnostic.INSTRUCTION, "xsl:number/select", 0);
                role.setErrorCode(errorCode);
                select = getConfiguration().getTypeChecker(false).staticTypeCheck(select,
                        SequenceType.SINGLE_NODE,
                                                                                  role, makeExpressionVisitor());
            } catch (XPathException err) {
                compileError(err);
            }
        }
    }

    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        boolean valueSpecified = value != null;
        if (value == null) {
            value = new NumberInstruction(
                    select,
                    level,
                    count,
                    from);
            value.setLocation(allocateLocation());
        }
        NumberSequenceFormatter numFormatter = new NumberSequenceFormatter(
                value,
                format,
                groupSize,
                groupSeparator,
                letterValue,
                ordinal,
                startAt,
                lang,
                formatter,
                xPath10ModeIsEnabled() && valueSpecified);
        numFormatter.setLocation(allocateLocation());
        ValueOf inst = new ValueOf(numFormatter, false, false);
        inst.setLocation(allocateLocation());
        inst.setIsNumberingInstruction();
        return inst;
    }

}