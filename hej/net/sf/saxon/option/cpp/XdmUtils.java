////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2014 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.cpp;

import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.s9api.*;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.BuiltInType;
import net.sf.saxon.type.Type;

import java.util.ArrayList;
import java.util.List;

/**
 * * This utility class consists generic functions for use with XDM data model.
 * For the use of Saxon on C++
 */
public class XdmUtils {


    public XdmUtils() {

    }

    public static QName getTypeName(String typeStr) {

        int fp = StandardNames.getFingerprint(NamespaceConstant.SCHEMA, typeStr);

        BuiltInAtomicType typei = (BuiltInAtomicType) BuiltInType.getSchemaType(fp);
        StructuredQName sname = typei.getTypeName();
        return new QName(sname.getPrefix(), sname.getURI(), sname.getLocalPart());

    }

    public static String getStringValue(XdmValue value) {
        return value.toString();
    }

    /**
     * Attempt to Downcast XdmItem to XdmNode. If this is not possible then return null
     *
     */
    public static XdmNode castToXdmNode(XdmItem item){
        if(item instanceof XdmNode) {
            return (XdmNode)item;
        }
        return null;
    }

    public static String getPrimitiveTypeName(XdmAtomicValue value){
        return getEQName(value.getPrimitiveTypeName());
    }



    /**
     * Attempt to Downcast XdmItem to XdmAtomicValue. If this is not possible then return null
     *
     */
    public static XdmAtomicValue castToXdmAtomicValue(XdmItem item){
        if(item.isAtomicValue()) {
            return (XdmAtomicValue)item;
        }
        return null;
    }

    /**
     * Attempt to Downcast XdmItem to XdmFunctionItem. If this is not possible then return null
     *
     */
    public static XdmFunctionItem castToXdmFunctionItem(XdmItem item){
        if(item instanceof XdmFunctionItem) {
            return (XdmFunctionItem)item;
        }
        return null;
    }

    /**
     * Convert the nodeKind enum type to a integer
     *
     * @param nodeKind - XdmNodeKind type
     */
    public static int convertNodeKindType(XdmNodeKind nodeKind) {
        switch (nodeKind) {
            case DOCUMENT:
                return Type.DOCUMENT;
            case ELEMENT:
                return Type.ELEMENT;
            case ATTRIBUTE:
                return Type.ATTRIBUTE;
            case TEXT:
                return Type.TEXT;
            case COMMENT:
                return Type.COMMENT;
            case PROCESSING_INSTRUCTION:
                return Type.PROCESSING_INSTRUCTION;
            case NAMESPACE:
                return Type.NAMESPACE;
        }

        return 0;

    }

  /*
   * The expanded name, as a string using the notation devised by EQName.
   * If the name is in a namespace, the resulting string takes the form <code>{uri}local</code>.
   * Otherwise, the value is the local part of the name.
   * @param qname
   *
   */
    public static String getEQName(QName qname) {
        String uri = qname.getNamespaceURI();
        if (uri.length() == 0) {
            return qname.getLocalName();
        } else {
            return "Q{" + uri + "}" + qname.getLocalName();
        }
    }

    /**
     * Get the name of the node, as a EQName
     *
     * @param node
     * @return the name of the node. In the case of unnamed nodes (for example, text and comment nodes)
     *         return null.
     */
    public static String getNodeName(XdmNode node) {
          QName qname = node.getNodeName();
          if(qname == null) {
              return null;
          }
          return XdmUtils.getEQName(qname);

    }

    public XdmNode[] getChildNodes(){
        return null;
    }


    /**
     * Get the string value of a named attribute of the element passed
     *
     * @param  node the element in question
     * @param eqname the name of the required attribute
     * @return null if this node is not an element, or if this element has no
     *         attribute with the specified name. Otherwise return the string value of the
     *         selected attribute node.
     */
    public static String getAttributeValue(XdmNode node, String eqname){

        QName name = QName.fromEQName(eqname);
        if (SaxonCAPI.debug) {
          System.err.println("EQName= "+eqname);
          System.err.println("Java-Call Att-name="+name.getClarkName() + " Value="+node.getAttributeValue(name));
        }
        return node.getAttributeValue(name);
    }

    public static XdmNode[] getAttributeNodes(XdmNode node){
       List<XdmNode> children = new ArrayList<XdmNode>();
        XdmSequenceIterator iter = node.axisIterator(Axis.ATTRIBUTE);
        while(iter.hasNext()){
            children.add((XdmNode) iter.next());
        }
        if(children.size() == 0 ){
            return null;
        }
        return children.toArray(new XdmNode[0]);
    }

    public static XdmNode[] getChildren(XdmNode node){
        List<XdmNode> children = new ArrayList<XdmNode>();
        XdmSequenceIterator iter = node.axisIterator(Axis.CHILD);
        while(iter.hasNext()){
            children.add((XdmNode)iter.next());
        }
        if(children.size() == 0 ){
            return null;
        }
        return children.toArray(new XdmNode[0]);
    }

    public static int getChildCount(XdmNode node) {
        int num = 0;
        XdmSequenceIterator iter = node.axisIterator(Axis.CHILD);
        while(iter.hasNext()){
            iter.next();
            num++;
        }

        return num;
    }

    public static int getAttributeCount(XdmNode node) {
        int num = 0;
        XdmSequenceIterator iter = node.axisIterator(Axis.ATTRIBUTE);
        while(iter.hasNext()){
            iter.next();
            num++;
        }

        return num;
    }

}
