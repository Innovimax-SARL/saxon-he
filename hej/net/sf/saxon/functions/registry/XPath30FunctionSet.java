////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions.registry;

import net.sf.saxon.functions.*;
import net.sf.saxon.pattern.AnyNodeTest;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.NumericType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.StringValue;

/**
 * Function signatures (and pointers to implementations) of the functions defined in XPath 3.0 without the
 * Higher-Order-Functions feature
 */

public class XPath30FunctionSet extends BuiltInFunctionSet {

    private static XPath30FunctionSet THE_INSTANCE = new XPath30FunctionSet();

    public static XPath30FunctionSet getInstance() {
        return THE_INSTANCE;
    }

    private XPath30FunctionSet() {
        init();
    }

    private void init() {

        importFunctionSet(XPath20FunctionSet.getInstance());

        register("analyze-string", 2, RegexFunctionSansFlags.class, NodeKindTest.ELEMENT,
                 ONE, XPATH30, LATE | NEW)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null);

        register("analyze-string", 3, AnalyzeStringFn.class, NodeKindTest.ELEMENT,
                 ONE, XPATH30, LATE | NEW)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null)
                .arg(2, BuiltInAtomicType.STRING, ONE, null);

        register("available-environment-variables", 0, AvailableEnvironmentVariables.class, BuiltInAtomicType.STRING,
                 STAR, XPATH30 | USE_WHEN, LATE);

        register("data", 0, ContextItemAccessorFunction.class, BuiltInAtomicType.ANY_ATOMIC, STAR, XPATH30, CITEM | LATE);

        register("document-uri", 0, ContextItemAccessorFunction.class,
                 BuiltInAtomicType.ANY_URI, OPT, XPATH30, CITEM | LATE);

        register("element-with-id", 1, SuperId.ElementWithId.class, NodeKindTest.ELEMENT, STAR, CORE, CITEM | LATE | UO)
                .arg(0, BuiltInAtomicType.STRING, STAR, EMPTY);

        register("element-with-id", 2, SuperId.ElementWithId.class, NodeKindTest.ELEMENT, STAR, CORE, UO)
                .arg(0, BuiltInAtomicType.STRING, STAR, EMPTY)
                .arg(1, Type.NODE_TYPE, ONE, null);

        register("environment-variable", 1, EnvironmentVariable.class, BuiltInAtomicType.STRING,
                 OPT, XPATH30 | USE_WHEN, LATE)
                .arg(0, BuiltInAtomicType.STRING, ONE, null);

        register("format-date", 2, FormatDate.class, BuiltInAtomicType.STRING,
                 OPT, XSLT | XPATH30, 0)
                .arg(0, BuiltInAtomicType.DATE, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null);

        register("format-date", 5, FormatDate.class, BuiltInAtomicType.STRING,
                 OPT, XSLT | XPATH30, 0)
                .arg(0, BuiltInAtomicType.DATE, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null)
                .arg(2, BuiltInAtomicType.STRING, OPT, null)
                .arg(3, BuiltInAtomicType.STRING, OPT, null)
                .arg(4, BuiltInAtomicType.STRING, OPT, null);

        register("format-dateTime", 2, FormatDate.class, BuiltInAtomicType.STRING,
                 OPT, XSLT | XPATH30, 0)
                .arg(0, BuiltInAtomicType.DATE_TIME, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null);

        register("format-dateTime", 5, FormatDate.class, BuiltInAtomicType.STRING,
                 OPT, XSLT | XPATH30, 0)
                .arg(0, BuiltInAtomicType.DATE_TIME, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null)
                .arg(2, BuiltInAtomicType.STRING, OPT, null)
                .arg(3, BuiltInAtomicType.STRING, OPT, null)
                .arg(4, BuiltInAtomicType.STRING, OPT, null);

        register("format-integer", 2, FormatInteger.class, AnyItemType.getInstance(), ONE, XPATH30, 0)
                .arg(0, BuiltInAtomicType.INTEGER, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null);

        register("format-integer", 3, FormatInteger.class, AnyItemType.getInstance(), ONE, XPATH30, 0)
                .arg(0, BuiltInAtomicType.INTEGER, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null)
                .arg(2, BuiltInAtomicType.STRING, OPT, null);

        register("format-number", 2, FormatNumber.class, BuiltInAtomicType.STRING, ONE, XSLT | XPATH30, LATE)
                .arg(0, NumericType.getInstance(), OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null);

        register("format-number", 3, FormatNumber.class, BuiltInAtomicType.STRING, ONE, XSLT | XPATH30, NS | LATE)
                .arg(0, NumericType.getInstance(), OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null)
                .arg(2, BuiltInAtomicType.STRING, OPT, null);

        register("format-time", 2, FormatDate.class, BuiltInAtomicType.STRING,
                 OPT, XSLT | XPATH30, 0)
                .arg(0, BuiltInAtomicType.TIME, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null);

        register("format-time", 5, FormatDate.class, BuiltInAtomicType.STRING,
                 OPT, XSLT | XPATH30, 0)
                .arg(0, BuiltInAtomicType.TIME, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null)
                .arg(2, BuiltInAtomicType.STRING, OPT, null)
                .arg(3, BuiltInAtomicType.STRING, OPT, null)
                .arg(4, BuiltInAtomicType.STRING, OPT, null);

        register("generate-id", 0, ContextItemAccessorFunction.class, BuiltInAtomicType.STRING, ONE, XSLT | XPATH30, CITEM | LATE);

        register("generate-id", 1, GenerateId_1.class, BuiltInAtomicType.STRING, ONE, XSLT | XPATH30, 0)
                .arg(0, Type.NODE_TYPE, OPT | INS, StringValue.EMPTY_STRING);

        register("has-children", 0, ContextItemAccessorFunction.class, BuiltInAtomicType.BOOLEAN,
                 ONE, XPATH30, CITEM | LATE);

        register("has-children", 1, HasChildren_1.class, BuiltInAtomicType.BOOLEAN,
                 OPT, XPATH30, 0)
                .arg(0, AnyNodeTest.getInstance(), OPT | INS, null);

        register("head", 1, HeadFn.class, AnyItemType.getInstance(),
                 OPT, XPATH30, FILTER)
                .arg(0, AnyItemType.getInstance(), STAR | TRA, null);

        register("innermost", 1, Innermost.class, AnyNodeTest.getInstance(),
                 STAR, XPATH30, 0)
                .arg(0, AnyNodeTest.getInstance(), STAR | NAV, null);

        register("nilled", 0, ContextItemAccessorFunction.class, BuiltInAtomicType.BOOLEAN, OPT, XPATH30, CITEM | LATE);

        register("node-name", 0, ContextItemAccessorFunction.class, BuiltInAtomicType.QNAME, OPT, XPATH30, CITEM | LATE);

        register("outermost", 1, Outermost.class, AnyNodeTest.getInstance(), STAR, XPATH30, AS_ARG0 | FILTER)
                .arg(0, AnyNodeTest.getInstance(), STAR | TRA, null);

        register("parse-xml", 1, ParseXml.class, NodeKindTest.DOCUMENT, ONE, XPATH30, LATE | NEW)
                .arg(0, BuiltInAtomicType.STRING, ONE, null);

        register("parse-xml-fragment", 1, ParseXmlFragment.class, NodeKindTest.DOCUMENT, ONE, XPATH30, LATE | NEW)
                .arg(0, BuiltInAtomicType.STRING, ONE, null);

        register("path", 0, ContextItemAccessorFunction.class, BuiltInAtomicType.STRING, OPT, XPATH30, CITEM | LATE);

        register("path", 1, Path_1.class, BuiltInAtomicType.STRING, OPT, XPATH30, 0)
                .arg(0, AnyNodeTest.getInstance(), OPT | NAV, null);

        register("round", 2, Round.class, NumericType.getInstance(), OPT, XPATH30, AS_PRIM_ARG0)
                .arg(0, NumericType.getInstance(), OPT, EMPTY)
                .arg(1, BuiltInAtomicType.INTEGER, ONE, null);

        register("serialize", 1, Serialize.class, BuiltInAtomicType.STRING, ONE, XPATH30, 0)
                .arg(0, AnyItemType.getInstance(), STAR, null);

//        register("serialize", 2, Serialize.class, BuiltInAtomicType.STRING, ONE, XPATH30, 0)
//                .arg(0, AnyItemType.getInstance(), STAR, null)
//                .arg(1, NodeKindTest.ELEMENT, OPT, null);

        register("sort", 1, Sort_1.class, AnyItemType.getInstance(), STAR, XPATH31, 0)
                .arg(0, AnyItemType.getInstance(), STAR, null);

        register("string-join", 1, StringJoin.class, BuiltInAtomicType.STRING, ONE, XPATH30, 0)
                .arg(0, BuiltInAtomicType.ANY_ATOMIC, STAR, StringValue.EMPTY_STRING);

//        register("string-join", 2, StringJoin.class, BuiltInAtomicType.STRING, ONE, CORE, 0)
//                .arg(0, BuiltInAtomicType.ANY_ATOMIC, STAR, StringValue.EMPTY_STRING)
//                .arg(1, BuiltInAtomicType.STRING, ONE, null);

        register("tail", 1, TailFn.class, AnyItemType.getInstance(), STAR, XPATH30, AS_ARG0 | FILTER)
                .arg(0, AnyItemType.getInstance(), STAR | TRA, null);

        register("unparsed-text", 1, UnparsedText.class,
                 BuiltInAtomicType.STRING, OPT, XSLT | XPATH30, BASE | LATE)
                .arg(0, BuiltInAtomicType.STRING, OPT, null);

        register("unparsed-text", 2, UnparsedText.class,
                 BuiltInAtomicType.STRING, OPT, XSLT | XPATH30, BASE | LATE)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null);

        register("unparsed-text-available", 1, UnparsedTextAvailable.class,
                 BuiltInAtomicType.BOOLEAN, ONE, XSLT | XPATH30, BASE | LATE)
                .arg(0, BuiltInAtomicType.STRING, ONE, null);

        register("unparsed-text-available", 2, UnparsedTextAvailable.class,
                 BuiltInAtomicType.BOOLEAN, ONE, XSLT | XPATH30, BASE | LATE)
                .arg(0, BuiltInAtomicType.STRING, ONE, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null);

        register("unparsed-text-lines", 1, UnparsedTextLines.class, BuiltInAtomicType.STRING, STAR, XPATH30, BASE | LATE)
                .arg(0, BuiltInAtomicType.STRING, OPT, null);

        register("unparsed-text-lines", 2, UnparsedTextLines.class, BuiltInAtomicType.STRING, STAR, XPATH30, BASE | LATE)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null);

        register("uri-collection", 0, UriCollection.class, BuiltInAtomicType.ANY_URI, STAR, XPATH30, LATE);

        register("uri-collection", 1, UriCollection.class, BuiltInAtomicType.ANY_URI, STAR, XPATH30, LATE)
                .arg(0, BuiltInAtomicType.STRING, OPT, null);


    }


}
