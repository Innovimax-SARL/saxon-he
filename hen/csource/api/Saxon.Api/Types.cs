using System;
using System.Collections.Generic;
using System.Text;
using System.Xml;
using JType = net.sf.saxon.type.Type;
using JItemType = net.sf.saxon.type.ItemType;
using JAnyItemType = net.sf.saxon.type.AnyItemType;
using JNodeTest = net.sf.saxon.pattern.NodeTest;
using JAnyNodeType = net.sf.saxon.pattern.AnyNodeTest;
using JAnyFunctionType = net.sf.saxon.type.AnyFunctionType;
using JErrorType = net.sf.saxon.type.ErrorType;
using JFunctionItemType = net.sf.saxon.type.FunctionItemType;
using JNodeKindTest = net.sf.saxon.pattern.NodeKindTest;
using JAtomicType = net.sf.saxon.type.AtomicType;
using JBuiltInAtomicType = net.sf.saxon.type.BuiltInAtomicType;
using JBuiltInType = net.sf.saxon.type.BuiltInType;
using JSequenceType = net.sf.saxon.value.SequenceType;
using JStandardNames = net.sf.saxon.om.StandardNames;
using JStructuredQName = net.sf.saxon.om.StructuredQName;
using JCardinality = net.sf.saxon.value.Cardinality;
using JStaticProperty = net.sf.saxon.expr.StaticProperty;

namespace Saxon.Api
{
    /// <summary>
    /// Abstract class representing an item type. This may be the generic item type <c>item()</c>,
    /// an atomic type, the generic node type <code>node()</code>, a specific node kind such as
    /// <c>element()</c> or <c>text()</c>, or the generic function type <code>function()</code>.
    /// </summary>
    /// <remarks>
    /// More specific node types (such as <c>element(E)</c> or <c>schema-element(E)</c> cannot currently
    /// be instantiated in this API.
    /// </remarks>

    public abstract class XdmItemType
    {
        protected JItemType type;

        internal static XdmItemType MakeXdmItemType(JItemType type)
        {
            if (type.isAtomicType())
            {
                return new XdmAtomicType(((JAtomicType)type));
            }
            else if (type is JErrorType)
            {
                return XdmAnyNodeType.Instance;  // TODO: need to represent this properly
            }
            else if (type is JNodeTest)
            {
                if (type is JAnyNodeType)
                {
                    return XdmAnyNodeType.Instance;
                }
                else
                {
                    int kind = ((JNodeTest)type).getPrimitiveType();
                    Console.WriteLine("Kind " + kind);
                    return XdmNodeKind.ForNodeKindTest((JNodeKindTest)JNodeKindTest.makeNodeKindTest(kind));
                }
            }
            else if (type is JAnyItemType)
            {
                return XdmAnyItemType.Instance;
            }
            else if (type is JFunctionItemType)
            {
                return XdmAnyFunctionType.Instance;
            }
            else
            {
                return null;
            }
        }

        internal JItemType Unwrap() {
            return type;
        }
    }

        /// <summary>
        /// Singleton class representing the item type item(), which matches any item.
        /// </summary>

        public class XdmAnyItemType : XdmItemType
        {

            /// <summary>
            /// The singleton instance of this class: an <c>XdmItemType</c> corresponding to the
            /// item type <c>item()</c>, which matches any item.
            /// </summary>

            public static XdmAnyItemType Instance = new XdmAnyItemType();

            internal XdmAnyItemType()
            {
                this.type =  JAnyItemType.getInstance();
            }
        }


        /// <summary>
        /// Singleton class representing the item type node(), which matches any node.
        /// </summary>

        public class XdmAnyNodeType : XdmItemType
        {

            /// <summary>
            /// The singleton instance of this class: an <c>XdmItemType</c> corresponding to the
            /// item type <c>node()</c>, which matches any node.
            /// </summary>

            public static XdmAnyNodeType Instance = new XdmAnyNodeType();

            internal XdmAnyNodeType()
            {
                this.type = JAnyNodeType.getInstance();
            }
        }


        /// <summary>
        /// Singleton class representing the item type function(), which matches any function item.
        /// </summary>

        public class XdmAnyFunctionType : XdmItemType
        {

            /// <summary>
            /// The singleton instance of this class: an <c>XdmItemType</c> corresponding to the
            /// item type <c>function()</c>, which matches any function item.
            /// </summary>

            public static XdmAnyFunctionType Instance = new XdmAnyFunctionType();

            internal XdmAnyFunctionType()
            {
                this.type = JAnyFunctionType.getInstance();
            }
        }

        /// <summary>
        /// An instance of class <c>XdmAtomicType</c> represents a specific atomic type, for example
        /// <c>xs:double</c>, <c>xs:integer</c>, or <c>xs:anyAtomicType</c>. This may be either a built-in
        /// atomic type or a type defined in a user-written schema.
        /// </summary>
        /// <remarks>
        /// To get an <c>XdmAtomicType</c> instance representing a built-in atomic type, use one of the predefined instances
        /// of the subclass <c>XdmBuiltInAtomicType</c>. To get an <c>XdmAtomicType</c> instance representing a user-defined
        /// atomic type (defined in a schema), use the method <c>GetAtomicType</c> defined on the <c>SchemaManager</c> class.
        /// </remarks>

        public class XdmAtomicType : XdmItemType
        {

            internal XdmAtomicType(JAtomicType type)
            {
                this.type = type;
            }

            /// <summary>
            /// Get an XdmAtomicType object representing a built-in atomic type with a given name
            /// </summary>
            /// <param name="name">The name of the required built-in atomic type</param>
            /// <returns>An XdmAtomicType object representing the built-in atomic type with the supplied name.
            /// Returns null if there is no built-in atomic type with this name.
            /// It is undefined whether two requests for the same built-in type will return the same object.</returns>

            public static XdmAtomicType BuiltInAtomicType(QName name)
            {
                int fingerprint = JStandardNames.getFingerprint(name.Uri, name.LocalName);
                if (fingerprint == -1)
                {
                    return null;
                }
                JAtomicType jat = (JAtomicType)JBuiltInType.getSchemaType(fingerprint);
                if (jat == null)
                {
                    return null;
                }
                return new XdmAtomicType((JAtomicType)JBuiltInType.getSchemaType(fingerprint));
            }

            /// <summary>
            /// The name of the atomic type, or null if the type is anonymous
            /// </summary>

            public QName Name
            {
                get
                {
                    JStructuredQName jQName = ((JAtomicType)type).getTypeName();
                    if (jQName == null)
                    {
                        return null;
                    }
                    return new QName(jQName.getPrefix(), jQName.getURI(), jQName.getLocalPart());
                }
            }
        }

        /// <summary>
        /// Instances of <c>XdmNodeKind</c> represent the item types denoted in XPath as <c>document-node()</c>,
        /// <c>element()</c>, <c>attribute()</c>, <c>text()</c>, and so on. These are all represented by singular named instances.
        /// </summary>

        public class XdmNodeKind : XdmItemType
        {

            internal XdmNodeKind(JNodeKindTest test)
            {
                type = test;
            }

            /// <summary>
            /// The item type <c>document-node()</c>
            /// </summary>

            public static XdmNodeKind Document = new XdmNodeKind(JNodeKindTest.DOCUMENT);

            /// <summary>
            /// The item type <c>element()</c>
            /// </summary>

            public static XdmNodeKind Element = new XdmNodeKind(JNodeKindTest.ELEMENT);

            /// <summary>
            /// The item type <c>attribute()</c>
            /// </summary>

            public static XdmNodeKind Attribute = new XdmNodeKind(JNodeKindTest.ATTRIBUTE);

            /// <summary>
            /// The item type <c>text()</c>
            /// </summary>

            public static XdmNodeKind Text = new XdmNodeKind(JNodeKindTest.TEXT);

            /// <summary>
            /// The item type <c>comment()</c>
            /// </summary>

            public static XdmNodeKind Comment = new XdmNodeKind(JNodeKindTest.COMMENT);

            /// <summary>
            /// The item type <c>processing-instruction()</c>
            /// </summary>

            public static XdmNodeKind ProcessingInstruction = new XdmNodeKind(JNodeKindTest.PROCESSING_INSTRUCTION);

            /// <summary>
            /// The item type <c>namespace-node()</c>
            /// </summary>

            public static XdmNodeKind Namespace = new XdmNodeKind(JNodeKindTest.NAMESPACE);

            internal static XdmNodeKind ForNodeKindTest(JNodeKindTest test)
            {
                int kind = test.getPrimitiveType();
                switch (kind)
                {
                    case JType.DOCUMENT:
                        return Document;
                    case JType.ELEMENT:
                        return Element;
                    case JType.ATTRIBUTE:
                        return Attribute;
                    case JType.TEXT:
                        return Text;
                    case JType.COMMENT:
                        return Comment;
                    case JType.PROCESSING_INSTRUCTION:
                        return ProcessingInstruction;
                    case JType.NAMESPACE:
                        return Namespace;
                    default:
                        throw new ArgumentException("Unknown node kind");
                }
            }

            /// <summary>
            /// Get the item type representing the node kind of a supplied node
            /// </summary>
            /// <param name="node">The node whose node kind is required</param>
            /// <returns>The relevant node kind</returns>

            public static XdmNodeKind ForNode(XdmNode node)
            {
                return ForNodeType(node.NodeKind);
            }

            /// <summary>
            /// Get the item type corresponding to an <c>XmlNodeType</c> as defined in the System.Xml package
            /// </summary>
            /// <param name="type">The <c>XmlNodeType</c> to be converted</param>
            /// <returns>The corresponding <c>XdmNodeKind</c></returns>

            public static XdmNodeKind ForNodeType(XmlNodeType type)
            {
                switch (type)
                {
                    case XmlNodeType.Document:
                        return Document;
                    case XmlNodeType.Element:
                        return Element;
                    case XmlNodeType.Attribute:
                        return Attribute;
                    case XmlNodeType.Text:
                        return Text;
                    case XmlNodeType.Comment:
                        return Comment;
                    case XmlNodeType.ProcessingInstruction:
                        return ProcessingInstruction;
                    default:
                        throw new ArgumentException("Unknown node kind");
                }
            }
        }

        /// <summary>
        /// An instance of class <c>XdmSequenceType</c> represents a sequence type, that is, the combination
        /// of an item type and an occurrence indicator.
        /// </summary>

        public class XdmSequenceType
        {

            internal XdmItemType itemType;
            internal int occurrences;
            public const char ZERO_OR_MORE='*';
            public const char ONE_OR_MORE = '+';
            public const char ZERO_OR_ONE = '?';
            public const char ZERO = 'º'; //xBA
            public const char ONE = ' ';

            /// <summary>
            /// Create an XdmSequenceType corresponding to a given XdmItemType and occurrence indicator
            /// </summary>
            /// <param name="itemType">The item type</param>
            /// <param name="occurrenceIndicator">The occurrence indicator, one of '?' (zero-or-one), 
            /// '*' (zero-or-more), '+' (one-or-more), ' ' (a single space) (exactly one),
            /// or 'º' (masculine ordinal indicator, xBA) (exactly zero). The type empty-sequence()
            /// can be represented by an occurrence indicator of 'º' with any item type.</param>

            public XdmSequenceType(XdmItemType itemType, char occurrenceIndicator)
            {
                int occ;
                switch (occurrenceIndicator)
                {
                    case ZERO_OR_MORE:
                        occ = JStaticProperty.ALLOWS_ZERO_OR_MORE;
                        break;
                    case ONE_OR_MORE:
                        occ = JStaticProperty.ALLOWS_ONE_OR_MORE;
                        break;
                    case ZERO_OR_ONE:
                        occ = JStaticProperty.ALLOWS_ZERO_OR_ONE;
                        break;
                    case ONE:
                        occ = JStaticProperty.EXACTLY_ONE;
                        break;
                    case ZERO:
                        occ = JStaticProperty.ALLOWS_ZERO;
                        break;
                    default:
                        throw new ArgumentException("Unknown occurrence indicator");
                }
                this.itemType = itemType;
                this.occurrences = occ;
            }

            internal static XdmSequenceType FromSequenceType(JSequenceType seqType)
            {
                XdmItemType itemType = XdmItemType.MakeXdmItemType(seqType.getPrimaryType());
                char occ;
                if (seqType.getCardinality() == JStaticProperty.ALLOWS_ZERO_OR_MORE)
                {
                    occ = ZERO_OR_MORE;
                }
                else if (seqType.getCardinality() == JStaticProperty.ALLOWS_ONE_OR_MORE)
                {
                    occ = ONE_OR_MORE;
                }
                else if (seqType.getCardinality() == JStaticProperty.ALLOWS_ZERO_OR_ONE)
                {
                    occ = ZERO_OR_ONE;
                }
                else if (seqType.getCardinality() == JStaticProperty.ALLOWS_ZERO)
                {
                    occ = ZERO;
                }
                else
                {
                    occ = ONE;
                }
                return new XdmSequenceType(itemType, occ);
            }

            internal JSequenceType ToSequenceType() {
                JItemType jit = itemType.Unwrap();
                return JSequenceType.makeSequenceType(jit, occurrences);
            }
 
                
        }


}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2016 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
