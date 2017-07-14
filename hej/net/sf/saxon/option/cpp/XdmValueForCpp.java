////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2014 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.cpp;

import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.s9api.*;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.BuiltInType;
import net.sf.saxon.type.Converter;
import net.sf.saxon.type.ValidationException;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.IntegerValue;
import net.sf.saxon.value.StringValue;

/**
 * * This class is to use with Saxon on C++
 */
public class XdmValueForCpp {
    private BuiltInAtomicType type;
    private XdmValue value;
    private static QName errorCode = null;


    public XdmValueForCpp() {
        value = XdmEmptySequence.getInstance();
        type = (BuiltInAtomicType) BuiltInType.getSchemaType(StandardNames.XS_ERROR);
    }

    public String getErrorCode() {
        if (errorCode != null) {
            errorCode.toString();
        }
        return "";
    }


    public XdmValueForCpp(String typeStr, String valueStr) throws Exception {

        int fp = StandardNames.getFingerprint(NamespaceConstant.SCHEMA, typeStr);

        type = (BuiltInAtomicType) BuiltInType.getSchemaType(fp);
        ConversionRules rules = new ConversionRules();
        Converter converter = rules.getConverter(BuiltInAtomicType.STRING, type);

        value = XdmValue.wrap(converter.convert(new StringValue(valueStr)).asAtomic());


    }


    public XdmValueForCpp(boolean b) {
        value = XdmValue.wrap(BooleanValue.get(b));
        type = BuiltInAtomicType.BOOLEAN;
    }

    public XdmValueForCpp(int i) throws SaxonApiException {
        try {
            value = XdmValue.wrap(IntegerValue.makeIntegerValue(i).asAtomic());
            type = BuiltInAtomicType.INT;
        } catch (ValidationException e) {
            throw new SaxonApiException(e);
        }
    }

    public XdmValueForCpp(XdmNode node) {
        value = XdmValue.wrap(node.getUnderlyingValue());
        type = null;
    }


    public static XdmItem[] makeArrayFromXdmValue(XdmValue value) {
        XdmItem[] items = new XdmItem[value.size()];

        for(int i=0;i<value.size();i++) {
            XdmItem item = value.itemAt(i);
            items[i] = item;
        }
        return items;

    }

    public static Processor getProcessor(Object node){
        return (Processor) ((XdmNode)node).getUnderlyingNode().getConfiguration().getProcessor();
    }


    public XdmValue getXdmValue() {
        return value;
    }

    public BuiltInAtomicType getAtomicType() {
        return type;
    }

    public QName getTypeName() {
        StructuredQName sname = type.getTypeName();
        return new QName(sname.getPrefix(), sname.getURI(), sname.getLocalPart());
    }

    public static QName getTypeName(String typeStr) {

        int fp = StandardNames.getFingerprint(NamespaceConstant.SCHEMA, typeStr);

        BuiltInAtomicType typei = (BuiltInAtomicType) BuiltInType.getSchemaType(fp);
        StructuredQName sname = typei.getTypeName();
        return new QName(sname.getPrefix(), sname.getURI(), sname.getLocalPart());

    }

    public String getStringValue() {
        return value.toString();
    }
}
