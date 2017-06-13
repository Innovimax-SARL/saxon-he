////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions.registry;

import net.sf.saxon.functions.*;
import net.sf.saxon.ma.json.XMLToJsonFn;
import net.sf.saxon.ma.map.MapType;
import net.sf.saxon.pattern.AnyNodeTest;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.Type;

/**
 * Function signatures (and pointers to implementations) of the functions defined in XSLT 3.0.
 * This includes the functions defined in XSLT 2.0 and XPath 3.1 by reference. It does not
 * include higher-order functions, and it does not include functions in the math/map/array
 * namespaces.
 */

public class XSLT30FunctionSet extends BuiltInFunctionSet {

    private static XSLT30FunctionSet THE_INSTANCE = new XSLT30FunctionSet();

    public static XSLT30FunctionSet getInstance() {
        return THE_INSTANCE;
    }

    private XSLT30FunctionSet() {
        init();
    }

    private void init() {

        importFunctionSet(XPath31FunctionSet.getInstance());
        importFunctionSet(XSLT20FunctionSet.getInstance());

        register("accumulator-before", 1, AccumulatorFn.AccumulatorBefore.class, AnyItemType.getInstance(),
                 STAR, XSLT30, LATE | CITEM)
                .arg(0, BuiltInAtomicType.STRING, ONE, null);

        register("accumulator-after", 1, AccumulatorFn.AccumulatorAfter.class, AnyItemType.getInstance(),
                                  STAR, XSLT30, LATE | CITEM)
                .arg(0, BuiltInAtomicType.STRING, ONE, null);

        register("available-system-properties", 0, AvailableSystemProperties.class, BuiltInAtomicType.QNAME,
                 STAR, XSLT30 | USE_WHEN, LATE);

        register("copy-of", 0, CopyOfFn.class, AnyItemType.getInstance(),
                 STAR, XPATH30, NEW);

        register("copy-of", 1, CopyOfFn.class, AnyItemType.getInstance(),
                 STAR, XPATH30, NEW)
                .arg(0, AnyItemType.getInstance(), STAR | ABS, EMPTY);

        register("current-merge-group", 0, CurrentMergeGroup.class, AnyItemType.getInstance(),
                 STAR, XSLT30, LATE);

        register("current-merge-group", 1, CurrentMergeGroup.class, AnyItemType.getInstance(),
                 STAR, XSLT30, LATE)
                .arg(0, BuiltInAtomicType.STRING, ONE, null);

        register("current-merge-key", 0, CurrentMergeKey.class, BuiltInAtomicType.ANY_ATOMIC,
                 STAR, XSLT30, LATE);

        register("current-output-uri", 0, CurrentOutputUri.class, BuiltInAtomicType.ANY_URI, OPT, XSLT30, LATE);

        register("snapshot", 0, ContextItemAccessorFunction.class, AnyItemType.getInstance(), STAR, XPATH30, CITEM | LATE | NEW);

        register("snapshot", 1, SnapshotFn.class, AnyNodeTest.getInstance(),
                 STAR, XPATH30, NEW)
                .arg(0, AnyItemType.getInstance(), STAR | ABS, EMPTY);

        register("stream-available", 1, StreamAvailable.class, BuiltInAtomicType.BOOLEAN,
                 ONE, XSLT30, LATE)
                .arg(0, BuiltInAtomicType.STRING, OPT, null);

        register("unparsed-entity-uri", 2, UnparsedEntity.UnparsedEntityUri.class, BuiltInAtomicType.ANY_URI, ONE, XSLT30, 0)
                .arg(0, BuiltInAtomicType.STRING, ONE, null)
                .arg(1, Type.NODE_TYPE, ONE, null);

        register("unparsed-entity-public-id", 2, UnparsedEntity.UnparsedEntityPublicId.class, BuiltInAtomicType.STRING, ONE, XSLT30, 0)
                .arg(0, BuiltInAtomicType.STRING, ONE, null)
                .arg(1, Type.NODE_TYPE, ONE, null);

        register("xml-to-json", 1, XMLToJsonFn.class, AnyItemType.getInstance(),
                 OPT, XPATH30, LATE)
                .arg(0, AnyNodeTest.getInstance(), OPT | ABS, null);

        register("xml-to-json", 2, XMLToJsonFn.class, AnyItemType.getInstance(),
                 OPT, XPATH30, LATE)
                .arg(0, AnyNodeTest.getInstance(), OPT | ABS, null)
                .arg(1, MapType.ANY_MAP_TYPE, ONE, null)
                .optionDetails(XMLToJsonFn.makeOptionsParameter());

    }


}

