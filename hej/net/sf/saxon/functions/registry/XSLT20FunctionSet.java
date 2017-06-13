////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions.registry;

import net.sf.saxon.functions.*;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.NumericType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.StringValue;

/**
 * Function signatures (and pointers to implementations) of the functions defined in XPath 2.0
 */

public class XSLT20FunctionSet extends BuiltInFunctionSet {

    public static XSLT20FunctionSet THE_INSTANCE = new XSLT20FunctionSet();

    public static XSLT20FunctionSet getInstance() {
        return THE_INSTANCE;
    }

    public XSLT20FunctionSet() {
        init();
    }

    private void init() {

        importFunctionSet(XPath20FunctionSet.getInstance());

        register("current", 0, Current.class, Type.ITEM_TYPE, ONE, XSLT, LATE);

        register("current-group", 0, CurrentGroup.class, Type.ITEM_TYPE, STAR, XSLT, LATE);

        register("current-grouping-key", 0, CurrentGroupingKey.class, BuiltInAtomicType.ANY_ATOMIC, STAR, XSLT, LATE);

        register("document", 1, DocumentFn.class, Type.NODE_TYPE, STAR, XSLT, BASE | LATE | UO)
                .arg(0, Type.ITEM_TYPE, STAR, null);

        register("document", 2, DocumentFn.class, Type.NODE_TYPE, STAR, XSLT, BASE | LATE | UO)
                .arg(0, Type.ITEM_TYPE, STAR, null)
                .arg(1, Type.NODE_TYPE, ONE, null);

        register("element-available", 1, ElementAvailable.class, BuiltInAtomicType.BOOLEAN, ONE, XSLT | USE_WHEN, NS)
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

        register("function-available", 1, FunctionAvailable.class, BuiltInAtomicType.BOOLEAN, ONE, XSLT | USE_WHEN, NS | LATE)
                .arg(0, BuiltInAtomicType.STRING, ONE, null);

        register("function-available", 2, FunctionAvailable.class, BuiltInAtomicType.BOOLEAN, ONE, XSLT | USE_WHEN, NS | LATE)
                .arg(0, BuiltInAtomicType.STRING, ONE, null)
                .arg(1, BuiltInAtomicType.INTEGER, ONE, null);

        register("generate-id", 0, ContextItemAccessorFunction.class, BuiltInAtomicType.STRING, ONE, XSLT | XPATH30, CITEM | LATE);

        register("generate-id", 1, GenerateId_1.class, BuiltInAtomicType.STRING, ONE, XSLT | XPATH30, 0)
                .arg(0, Type.NODE_TYPE, OPT | INS, StringValue.EMPTY_STRING);

        register("key", 2, KeyFn.class, Type.NODE_TYPE, STAR, XSLT, CDOC | NS | LATE)
                .arg(0, BuiltInAtomicType.STRING, ONE, null)
                .arg(1, BuiltInAtomicType.ANY_ATOMIC, STAR, EMPTY);

        register("key", 3, KeyFn.class, Type.NODE_TYPE, STAR, XSLT, NS | LATE)
                .arg(0, BuiltInAtomicType.STRING, ONE, null)
                .arg(1, BuiltInAtomicType.ANY_ATOMIC, STAR, EMPTY)
                .arg(2, Type.NODE_TYPE, ONE, null);

        register("regex-group", 1, RegexGroup.class, BuiltInAtomicType.STRING, ONE, XSLT, LATE | SIDE)
                .arg(0, BuiltInAtomicType.INTEGER, ONE, null);
                // Mark it as having side-effects to prevent loop-lifting

        register("system-property", 1, SystemProperty.class, BuiltInAtomicType.STRING, ONE, XSLT | USE_WHEN, NS | LATE)
                .arg(0, BuiltInAtomicType.STRING, ONE, null);

        register("type-available", 1, TypeAvailable.class, BuiltInAtomicType.BOOLEAN, ONE, XSLT | USE_WHEN, NS)
                .arg(0, BuiltInAtomicType.STRING, ONE, null);

        register("unparsed-entity-uri", 1, UnparsedEntity.UnparsedEntityUri.class, BuiltInAtomicType.ANY_URI, ONE, XSLT, CDOC | LATE)
                .arg(0, BuiltInAtomicType.STRING, ONE, null);

        register("unparsed-entity-public-id", 1, UnparsedEntity.UnparsedEntityPublicId.class, BuiltInAtomicType.STRING, ONE, XSLT, CDOC | LATE)
                .arg(0, BuiltInAtomicType.STRING, ONE, null);

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

    }

}
