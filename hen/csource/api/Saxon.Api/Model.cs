using System;
using System.IO;
using System.Xml;
using System.Collections;
using JConfiguration = net.sf.saxon.Configuration;
using JNamePool = net.sf.saxon.om.NamePool;
using JAtomicValue = net.sf.saxon.value.AtomicValue;
using JFunction = net.sf.saxon.om.Function;
using JItem = net.sf.saxon.om.Item;
using JZeroOrOne = net.sf.saxon.om.ZeroOrOne;
using JOne = net.sf.saxon.om.One;
using JEmptySequence = net.sf.saxon.value.EmptySequence;
using JSequenceExtent = net.sf.saxon.value.SequenceExtent;
using JConversionResult = net.sf.saxon.type.ConversionResult;
using JValidationFailure = net.sf.saxon.type.ValidationFailure;
using JSequenceIterator = net.sf.saxon.om.SequenceIterator;
using JStandardNames = net.sf.saxon.om.StandardNames;
using JStructuredQName = net.sf.saxon.om.StructuredQName;
using JXPathContext = net.sf.saxon.expr.XPathContext;
using JDotNetReceiver = net.sf.saxon.dotnet.DotNetReceiver;
using JDotNetObjectValue = net.sf.saxon.dotnet.DotNetObjectValue;
using JBigDecimal = java.math.BigDecimal;
using JArrayList = java.util.ArrayList;
using JCharSequence = java.lang.CharSequence;
using JSequence = net.sf.saxon.om.Sequence;
using JNodeInfo = net.sf.saxon.om.NodeInfo;
using JAxisInfo = net.sf.saxon.om.AxisInfo;
using JInscopeNamespaceResolver = net.sf.saxon.om.InscopeNamespaceResolver;
using JNameChecker = net.sf.saxon.om.NameChecker;
using JSingletonIterator = net.sf.saxon.tree.iter.SingletonIterator;
using JQNameValue = net.sf.saxon.value.QNameValue;
using JStringValue = net.sf.saxon.value.StringValue;
using JInt64Value = net.sf.saxon.value.Int64Value;
using JBigDecimalValue = net.sf.saxon.value.BigDecimalValue;
using JFloatValue = net.sf.saxon.value.FloatValue;
using JDoubleValue = net.sf.saxon.value.DoubleValue;
using JBooleanValue = net.sf.saxon.value.BooleanValue;
using JAnyURIValue = net.sf.saxon.value.AnyURIValue;
using JNumericValue = net.sf.saxon.value.NumericValue;
using JStringToDouble11 = net.sf.saxon.value.StringToDouble11;
using JIntegerValue = net.sf.saxon.value.IntegerValue;
using JNameTest = net.sf.saxon.pattern.NameTest;
using JAtomicType = net.sf.saxon.type.AtomicType;
using JSchemaType = net.sf.saxon.type.SchemaType;
using JType = net.sf.saxon.type.Type;
using JStringToDouble = net.sf.saxon.type.StringToDouble;
using JSequenceTool = net.sf.saxon.om.SequenceTool;
using JExplicitLocation = net.sf.saxon.expr.parser.ExplicitLocation;
using JHashTrieMap = net.sf.saxon.ma.map.HashTrieMap;
using JMapItem = net.sf.saxon.ma.map.MapItem;
using JArrayItem = net.sf.saxon.ma.arrays.ArrayItem;
using JKeyValuePair = net.sf.saxon.ma.map.KeyValuePair;
using JSimpleArrayItem = net.sf.saxon.ma.arrays.SimpleArrayItem;
using JDecimalValue = net.sf.saxon.value.DecimalValue;
using System.Collections.Generic;

namespace Saxon.Api
{

    /// <summary>
    /// A value in the XDM data model. A value is a sequence of zero or more
    /// items, each item being either an atomic value or a node.
    /// </summary>
    /// <remarks>
    /// <para>An <c>XdmValue</c> is immutable.</para>
    /// <para>A sequence consisting of a single item <i>may</i> be represented
    /// as an instance of <c>XdmItem</c>, which is a subtype of <c>XdmValue</c>. However,
    /// there is no guarantee that all single-item sequences will be instances of
    /// <c>XdmItem</c>: if you want to ensure this, use the <c>Simplify</c> property.</para>
    /// <para>There are various ways of creating an <c>XdmValue</c>. To create an atomic
    /// value, use one of the constructors on <c>XdmAtomicValue</c> (which is a subtype of <c>XdmValue</c>).
    /// To construct an <c>XdmNode</c> (another subtype) by parsing an XML document, or by wrapping a DOM document,
    /// use a <c>DocumentBuilder</c>. To create a sequence of values, use the <c>Append</c>
    /// method on this class to form a list from individual items or sublists.</para>
    /// <para>An <c>dmValue</c> is also returned as the result of evaluating a query
    /// using the XQuery and XPath interfaces.</para>
    /// <para>The subtype <c>XdmEmptySequence</c> represents an empty sequence: an
    /// <c>XdmValue</c> of length zero. Again, there is no guarantee that every empty sequence
    /// will be represented as an instance of <c>XdmEmptySequence</c>, unless you use
    /// the <c>Simplify</c> property.</para>
    /// </remarks>

    [Serializable]
    public class XdmValue : IEnumerable
    {

        internal JSequence value;

        // Internal constructor

        internal XdmValue() { }

        /// <summary>
        /// Create a value from a collection of items
        /// </summary>
        /// <param name="items">An enumerable collection providing the items to make up the sequence. Every
        /// member of this collection must be an instance of <c>XdmItem</c>
        /// </param>

        public XdmValue(IEnumerable items)
        {
            JArrayList list = new JArrayList();
            foreach (XdmItem c in items)
            {
                list.add((JItem)c.Unwrap());
            }
            value = new JSequenceExtent(list);
        }

        /// <summary>
        /// Create a new XdmValue by concatenating the sequences of items in this XdmValue and another XdmValue
        /// </summary>
        /// <remarks>
        /// Neither of the input XdmValue objects is modified by this operation
        /// </remarks>
        /// <param name="otherValue">
        /// The other XdmValue, whose items are to be appended to the items from this XdmValue
        /// </param>
        
        public XdmValue Append(XdmValue otherValue) {
            JArrayList list = new JArrayList();
            foreach (XdmItem item in this) {
                list.add(item.Unwrap());
            }
            foreach (XdmItem item in otherValue) {
                list.add(item.Unwrap());
            }
            return XdmValue.Wrap(new JSequenceExtent(list));
        }


        /// <summary>
        /// Create an XdmValue from an underlying Saxon Sequence object.
        /// This method is provided for the benefit of applications that need to mix
        /// use of the Saxon .NET API with direct use of the underlying objects
        /// and methods offered by the Java implementation.
        /// </summary>
        /// <param name="value">An object representing an XDM value in the
        /// underlying Saxon implementation. If the parameter is null,
        /// the method returns null.</param>
        /// <returns>An XdmValue that wraps the underlying Saxon value
        /// representation.</returns>

        public static XdmValue Wrap(JSequence value)
        {
            if (value == null) {
                return XdmEmptySequence.INSTANCE;
            }
            net.sf.saxon.om.GroundedValue gv;
            try
            {
                gv = JSequenceTool.toGroundedValue(value);
            }
            catch (Exception e) {
                throw new DynamicError(e.Message);
            }
            XdmValue result;
            if (gv.getLength() == 0)
            {

                return XdmEmptySequence.INSTANCE;
            } else if (gv.getLength() == 1) {
                JItem first = gv.head();
                if (first is JAtomicValue)
                {
                    result = new XdmAtomicValue();
                    result.value = (JAtomicValue)first;
                    return result;
                }
                else if (first is JNodeInfo)
                {
                    result = new XdmNode();
                    result.value = (JNodeInfo)first;
                    return result;
                }
                else if (first is JZeroOrOne)
                {
                    return Wrap(((JZeroOrOne)value).head());
                }
                else if (first is JOne)
                {
                    return Wrap(((JOne)value).head());
                }
                else if (first is JMapItem)
                {
                    result = new XdmMap();
                    result.value = (JMapItem)first;
                    return result;
                }
                else if (first is JArrayItem)
                {
                    result = new XdmArray();
                    result.value = (JArrayItem)first;
                    return result;
                }
                else if (first is JFunction)
                {
                    result = new XdmFunctionItem();
                    result.value = (JFunction)first;
                    return result;
                }
                else {
                    result = new XdmValue();
                    result.value = first;
                    return result;
                }

            } 
            else
            {
                result = new XdmValue();
                result.value = gv;
                return result;
            }
            
        }

        /// <summary>
        /// Make an XDM value from a .Net object. The supplied object may be any of the following
        /// An  instance of XdmValue (for example an XdmAtomicValue, XdmMap, XdmArray or XdmNode),
        /// an instance of IDictionary which is wrapped as an XdmMap,
        /// an instance and array which is wrapped as an XdmArray.
        /// </summary>
        /// <param name="o">The supplied object</param>
        /// <returns>the result of conversion if successful</returns>
        public static XdmValue MakeValue(object o) {
           
            if (o == null)
            {
                return null;
            }
            if (o is JSequence)
            {
                return XdmValue.Wrap((JSequence)o);
            }
            else if (o is XdmValue)
            {
                return (XdmValue)o;
            }
            else if (o is IDictionary)
            {
                return XdmMap.MakeMap((IDictionary)o);
            }
            else if (o.GetType().IsArray) {
                return XdmArray.MakeArray((object[])o);
            } else if (o is IEnumerable) {
                return XdmValue.MakeSequence((IEnumerable)o);
            }

            else
            {
                return XdmAtomicValue.MakeAtomicValue(o);

            }

        }

        private static XdmValue MakeSequence(IEnumerable o)
        {
            JArrayList list = new JArrayList();

            if (o is string)
            {
                return XdmAtomicValue.MakeAtomicValue((object)o);
            }
            foreach (object oi in o)
            {
                XdmValue v = XdmValue.MakeValue(oi);
                if (v is XdmItem)
                {
                    list.add((JItem)v.Unwrap());
                }
                else {
                    list.add(new XdmArray(v).Unwrap());
                }

            }
            JSequence value =  new JSequenceExtent(list);
            return XdmValue.Wrap(value);
        }


        /// <summary>
        /// Extract the underlying Saxon Sequence object from an XdmValue.
        /// This method is provided for the benefit of applications that need to mix
        /// use of the Saxon .NET API with direct use of the underlying objects
        /// and methods offered by the Java implementation.
        /// </summary>
        /// <returns>An object representing the XDM value in the
        /// underlying Saxon implementation.</returns>


        public JSequence Unwrap()
        {
            return value;
        }

        /// <summary>
        /// Get the sequence of items in the form of an <c>IList</c>
        /// </summary>
        /// <returns>
        /// The list of items making up this value. Each item in the list
        /// will be an object of type <c>XdmItem</c>
        /// </returns>        

        public IList GetList()
        {
            if (value == null)
            {
                return new ArrayList();
            }
            else if (value is JItem)
            {
                ArrayList list = new ArrayList(1);
                list.Add((XdmItem)XdmValue.Wrap(value));
                return list;
            }
            else
            {
                ArrayList list = new ArrayList();
                JSequenceIterator iter = value.iterate();
                while (true)
                {
                    JItem jitem = iter.next();
                    if (jitem == null)
                    {
                        break;
                    }
                    list.Add((XdmItem)XdmValue.Wrap(jitem));
                }
                return list;
            }
        }

        /// <summary>
        /// Get the sequence of items in the form of an <c>IXdmEnumerator</c>
        /// </summary>
        /// <returns>
        /// An enumeration over the list of items making up this value. Each item in the list
        /// will be an object of type <c>XdmItem</c>
        /// </returns>    

        public IEnumerator GetEnumerator()
        {
            if (value == null)
            {
                return EmptyEnumerator.INSTANCE;
            }
            else if (value is JItem)
            {
                return new SequenceEnumerator(JSingletonIterator.makeIterator((JItem)value));
            }
            else
            {
                return new SequenceEnumerator(value.iterate());
            }
        }

        /// <summary>
        /// Get the number of items in the sequence
        /// </summary>
        /// <returns>
        /// The number of items in the sequence. Note that for a single item (including
        /// a map or an array) this always returns 1 (one).
        /// </returns> 

        public int Count
        {
            get
            {
                if (value == null)
                {
                    return 0;
                }
                else if (value is JItem)
                {
                    return 1;
                }
                else
                {
                    return JSequenceTool.toGroundedValue(value).getLength();
                }
            }
        }

        /// <summary>
        /// Simplify a value: that is, reduce it to the simplest possible form.
        /// If the sequence is empty, the result will be an instance of <c>XdmEmptySequence</c>.
        /// If the sequence is a single node, the result will be an instance of <c>XdmNode</c>;
        /// if it is a single atomic value, it will be an instance of <c>XdmAtomicValue</c>.
        /// </summary>

        public XdmValue Simplify
        {
            get
            {
                switch (JSequenceTool.toGroundedValue(value).getLength())
                {
                    case 0:
                        if (this is XdmEmptySequence)
                        {
                            return this;
                        }
                        return XdmEmptySequence.INSTANCE;

                    case 1:
                        if (this is XdmItem)
                        {
                            return this;
                        }
                        return XdmValue.Wrap(value);

                    default:
                        return this;
                }
            }
        }

    }

    /// <summary inherits="XdmValue">
    /// The class <c>XdmItem</c> represents an item in a sequence, as defined
    /// by the XDM data model. An item is either an atomic value or a node.
    /// </summary>
    /// <remarks>
    /// <para>An item is a member of a sequence, but it can also be considered as
    /// a sequence (of length one) in its own right. <c>XdmItem</c> is a subtype
    /// of <c>XdmValue</c> because every Item in the XDM data model is also a
    /// value.</para>
    /// <para>It cannot be assumed that every sequence of length one will be 
    /// represented by an <c>XdmItem</c>. It is quite possible for an <c>XdmValue</c>
    /// that is not an <c>XdmItem</c> to hold a singleton sequence.</para>
    /// </remarks> 

    [Serializable]
    public abstract class XdmItem : XdmValue
    {

        /// <summary>
        /// Determine whether the item is an atomic value
        /// </summary>
        /// <returns>
        /// true if the item is an atomic value, false if it is a Node
        /// </returns>

        public abstract bool IsAtomic();


        /// <summary>
        /// Get the string value of the item. For a node, this gets the string
        /// value of the node. For an atomic value, it has the same effect as casting the value to a string.
        /// In all cases the result is the same as applying  the XPath string() function.
        /// For atomc values, the result is the same as the result of calling toString. This
        /// is not the case for nodes, where toString returns an XML serialization of the node.
        /// </summary>
        /// <returns>The result of converting the item to a string</returns>
        public String GetStringValue() {
            return ((JItem)value).getStringValue();
        }

    }


    /// <summary>
    /// The class XdmExternalObject represents an XDM item that wraps an external .NET object.
    /// As such, it is outside the scope of the XDM specification (but permitted as an extension).
    /// 
    /// </summary>
    [Serializable]
    public class XdmExternalObjectValue : XdmItem
    {

        /// <summary>
        /// Constructor to create an XdmExternalObject that wraps a supplied .Net object
        /// </summary>
        /// <param name="o">the supplied .NET object</param>
        public XdmExternalObjectValue(object o) {
            value = new net.sf.saxon.value.ObjectValue(o);
        }

        /// <summary>
        /// Determine whether the item is an atomic value
        /// </summary>
        /// <returns>
        /// false (the item is not an atomic value)
        /// </returns>
        public override bool IsAtomic()
        {
            return false;
        }

        /// <summary>
        /// Get the wrapped .NET object
        /// </summary>
        /// <returns>the wrapped object</returns>
        public object GetExternalObject() {
            return ((net.sf.saxon.value.ObjectValue)value).getObject();
        }

        /// <summary>
        /// Get the result of converting the external value to a string.
        /// </summary>
        /// <returns>the result of applying ToString() to the wrapped external object</returns>
        public override string ToString() {
            return GetExternalObject().ToString();
        }
    }

    /// <summary inherits="XdmItem">
    /// The class <c>XdmAtomicValue</c> represents an item in an XPath 2.0 sequence
    /// that is an atomic value. The value may belong to any of the 19 primitive types
    /// defined in XML Schema, or to a type derived from these primitive types, or to 
    /// the XPath 2.0 type <c>xs:untypedAtomic</c>
    /// </summary>
    /// <remarks>
    /// Note that there is no guarantee that every <c>XdmValue</c> comprising a single
    /// atomic value will be an instance of this class. To force this, use the <c>Simplify</c>
    /// property of the <c>XdmValue</c>.
    /// </remarks>

    [Serializable]
    public class XdmAtomicValue : XdmItem
    {

        //internal JAtomicValue atomicValue;

        internal XdmAtomicValue() { }

        /// <summary>
        /// Determine whether the item is an atomic value
        /// </summary>
        /// <returns>
        /// true (the item is an atomic value)
        /// </returns>

        public override bool IsAtomic()
        {
            return true;
        }

        /// <summary>
        /// Construct an atomic value of type <c>xs:string</c>
        /// </summary>
        /// <param name="str">The string value</param>

        public XdmAtomicValue(String str)
        {
            this.value = new JStringValue(str);
        }

        /// <summary>
        /// Construct an atomic value of type <c>xs:integer</c>
        /// </summary>
        /// <param name="i">The integer value</param>

        public XdmAtomicValue(long i)
        {
            this.value = new JInt64Value(i);
        }

        /// <summary>
        /// Construct an atomic value of type <c>xs:integer</c>
        /// </summary>
        /// <param name="i">The integer value</param>
        public XdmAtomicValue(byte i)
        {
            this.value = new JInt64Value(i);
        }

        /// <summary>
        /// Construct an atomic value of type <c>xs:decimal</c>
        /// </summary>
        /// <param name="d">The decimal value</param>

        public XdmAtomicValue(decimal d)
        {
            this.value = new JBigDecimalValue(new JBigDecimal(d.ToString()));
        }

        /// <summary>
        /// Construct an atomic value of type <c>xs:float</c>
        /// </summary>
        /// <param name="f">The float value</param>        

        public XdmAtomicValue(float f)
        {
            this.value = new JFloatValue(f);
        }

        /// <summary>
        /// Construct an atomic value of type <c>xs:double</c>
        /// </summary>
        /// <param name="d">The double value</param>

        public XdmAtomicValue(double d)
        {
            this.value = new JDoubleValue(d);
        }

        /// <summary>
        /// Construct an atomic value of type <c>xs:boolean</c>
        /// </summary>
        /// <param name="b">The boolean value</param>

        public XdmAtomicValue(bool b)
        {
            this.value = JBooleanValue.get(b);
        }

        /// <summary>
        /// Construct an atomic value of type <c>xs:anyURI</c>
        /// </summary>
        /// <param name="u">The uri value</param>

        public XdmAtomicValue(Uri u)
        {
            this.value = new JAnyURIValue(u.ToString());
        }

        /// <summary>
        /// Construct an atomic value of type <c>xs:QName</c>
        /// </summary>
        /// <param name="q">The QName value</param>                

        public XdmAtomicValue(QName q)
        {
            this.value = new JQNameValue(
                q.Prefix, q.Uri, q.LocalName);
        }

        /// <summary>
        /// Construct an atomic value of a given built-in or user-defined type
        /// </summary>
        /// <example>
        ///   <code>AtomicValue("abcd", QName.XDT_UNTYPED_ATOMIC)</code>
        ///   <para>creates an untyped atomic value containing the string "abcd"</para>
        /// </example>
        /// <param name="lexicalForm">The string representation of the value (any value that is acceptable
        /// in the lexical space, as defined by XML Schema Part 2). Whitespace normalization as defined by
        /// the target type will be applied to the value.</param>
        /// <param name="type">The QName giving the name of the target type. This must be an atomic
        /// type, and it must not be a type that is namespace-sensitive (QName, NOTATION, or types derived
        /// from these). If the type is a user-defined type then its definition must be present
        /// in the schema cache maintained by the <c>SchemaManager</c>.</param> 
        /// <param name="processor">The <c>Processor</c> object. This is needed for looking up user-defined
        /// types, and also because some conversions are context-sensitive, for example they depend on the
        /// implicit timezone or the choice of XML 1.0 versus XML 1.1 for validating names.</param>
        /// <exception name="ArgumentException">Thrown if the type is unknown or unsuitable, or if the supplied string is not
        /// a valid lexical representation of a value of the given type.</exception>

        public XdmAtomicValue(String lexicalForm, QName type, Processor processor)
        {
			JConfiguration jconfig = processor.Implementation;
            int fp = jconfig.getNamePool().getFingerprint(type.Uri, type.LocalName);
            if (fp == -1)
            {
                throw new ArgumentException("Unknown name " + type);
            }
			JSchemaType st = jconfig.getSchemaType(new JStructuredQName("", type.Uri.ToString(), type.LocalName));
            if (st == null)
            {
                throw new ArgumentException("Unknown type " + type);
            }
            if (!(st is JAtomicType))
            {
                throw new ArgumentException("Specified type " + type + " is not atomic");
            }
            if (((JAtomicType)st).isNamespaceSensitive())
            {
                throw new ArgumentException("Specified type " + type + " is namespace-sensitive");
            }
			JConversionResult result = ((JAtomicType)st).getStringConverter(jconfig.getConversionRules()).convertString((JCharSequence)lexicalForm);
 
            if (result is JValidationFailure)
            {
                throw new ArgumentException(((JValidationFailure)result).getMessage());
            }
            this.value = (JAtomicValue)result;
        }

        /// <summary>
        /// Create an atomic value that wraps an external object. Such values can be used
        /// in conjunction with extension functions.
        /// </summary>
        /// <remarks>
        /// <para>This method should not be used to create simple atomic values representing strings,
        /// numbers, booleans, and so on. For that purpose, use the relevant constructor.
        /// Wrapped external objects are used only when calling .NET native code external
        /// to a query or stylesheet.</para>
        /// <para>In releases prior to 9.2, this method also existed with the alternative spelling
        /// <code>wrapExternalObject</code> (lower-case "w"). This was retained for backwards compatibility,
        /// but caused problems for Visual Basic users, where it is not permitted to have two methods whose
        /// names differ only in case. Any applications using <code>wrapExternalObject</code> must
        /// therefore be changed to use <code>WrapExternalObject</code>. Apologies for the inconvenience.</para>
        /// </remarks>
        /// <param name="external">The object to be wrapped.</param>
        /// <returns>The wrapped object</returns>

        public static XdmAtomicValue WrapExternalObject(object external)
        {
            return (XdmAtomicValue)XdmValue.Wrap(new JDotNetObjectValue(external));
        }

        public static XdmAtomicValue MakeAtomicValue(object value)
        {
            if (value is JAtomicValue) {
                return (XdmAtomicValue)XdmValue.Wrap((JAtomicValue)value);
            } else if (value is Boolean) {
                return new XdmAtomicValue((Boolean)value);
            } else if (value is int) {
                return new XdmAtomicValue((int)value);
            } else if (value is long) {
                return new XdmAtomicValue((long)value);
            } else if (value is short) {
                return new XdmAtomicValue((short)value);
            } else if (value is Char) {
                return new XdmAtomicValue((Char)value);
            } else if (value is Byte) {
                return new XdmAtomicValue((Byte)value);
            } else if (value is String) {
                return new XdmAtomicValue((String)value);
            } else if (value is Double) {
                return new XdmAtomicValue((Double)value);
            } else if (value is decimal) {
                return new XdmAtomicValue((decimal)value);
            } else if (value is long) {
                return new XdmAtomicValue((long)value);
            } else if (value is Uri) {
                return new XdmAtomicValue((Uri)value);
            } else if (value is QName) {
                return new XdmAtomicValue((QName)value);
            } if (value is XdmAtomicValue) {
                return (XdmAtomicValue)value;
            } else
            {
                throw new ArgumentException(value.ToString());
            }
        }


        /// <summary>
        /// Get the value converted to a boolean using the XPath casting rules
        /// </summary>
        /// <returns>the result of converting to a boolean (Note: this is not the same as the
        /// effective boolean value).</returns> 

        public bool GetBooleanValue()
        {
            JAtomicValue av = (JAtomicValue)this.value;
            if (av is JBooleanValue) {
                return ((JBooleanValue)av).getBooleanValue();
            } else if (av is JNumericValue) {
                return !av.isNaN() && ((JNumericValue)av).signum() != 0;
            } else if (av is JStringValue) {
                String s = av.getStringValue().Trim();
                return "1".Equals(s) || "true".Equals(s);
            } else {
                throw new ArgumentException("Cannot cast item to a boolean");
            }
        }


        /// <summary>
        /// Get the value converted to a boolean using the XPath casting rules
        /// </summary>
        /// <returns>the result of converting to an integer</returns>

        public long GetLongValue()
        {
            JAtomicValue av = (JAtomicValue)this.value;
            if (av is JBooleanValue) {
                return ((JBooleanValue)av).getBooleanValue() ? 0L : 1L;
            } else if (av is JNumericValue) {
            try {
                return ((JNumericValue)av).longValue();
            } catch (Exception) {
                throw new ArgumentException("Cannot cast item to an integer");
            }
            } else if (av is JStringValue) {
                JStringToDouble converter = JStringToDouble.getInstance();
                return (long)converter.stringToNumber(av.getStringValueCS());
            } else {
                throw new ArgumentException("Cannot cast item to an integer");
            }
        }


        /// <summary>
        /// Get the value converted to a double using the XPath casting rules.
        /// <p>If the value is a string, the XSD 1.1 rules are used, which means that the string
        /// "+INF" is recognised.</p>
        /// </summary>
        /// <returns>the result of converting to a double</returns>

        public double GetDoubleValue()
        {
            JAtomicValue av = (JAtomicValue)this.value;
            if (av is JBooleanValue) {
                return ((JBooleanValue)av).getBooleanValue() ? 0.0 : 1.0;
            } else if (av is JNumericValue) {
                return ((JNumericValue)av).getDoubleValue();
            } else if (av is JStringValue) {
            try {
                JStringToDouble converter = JStringToDouble11.getInstance();
                return converter.stringToNumber(av.getStringValueCS());
            } catch (Exception e) {
                throw new ArgumentException(e.Message);
            }
            } else {
                throw new ArgumentException("Cannot cast item to a double");
            }
        }


         /// <summary>
        /// Get the value converted to a decimal using the XPath casting rules
        /// </summary>
        /// <returns>return the result of converting to a decimal</returns>

        public Decimal GetDecimalValue() 
        {
            JAtomicValue av = (JAtomicValue)this.value;
            if (av is JBooleanValue) {
                return ((JBooleanValue)av).getBooleanValue() ? 0  : 1;
            } else if (av is JNumericValue) {
                try {
                    return Convert.ToDecimal(((JNumericValue)av).getDecimalValue().toString());
                } catch (Exception) {
                    throw new ArgumentException("Cannot cast item to a decimal");
                }   
            } else if (av is JStringValue) {
                return Convert.ToDecimal(av.getStringValueCS().toString());
            } else {
                throw new ArgumentException("Cannot cast item to a decimal");
            }
        }



        /// <summary>
        /// Convert the atomic value to a string
        /// </summary>
        /// <returns>The value converted to a string, according to the rules
        /// of the XPath 2.0 cast operator</returns>        

        public override String ToString()
        {
            return ((JAtomicValue)value).getStringValue();
        }
        
        /// <summary>
        /// Compare two atomic values for equality
        /// </summary>
        /// <returns>The result of the equality comparison, using the rules of the
        /// op:is-same-key() comparison used for comparing key values in maps</returns>
        
        public override Boolean Equals(object other)
        {
            if (other is XdmAtomicValue) {
                return ((JAtomicValue)value).asMapKey().Equals(((JAtomicValue)((XdmAtomicValue)other).value).asMapKey());

            } else {
                return false;
            }
        }
        
        /// <summary>
        /// Get a hash code to support equality comparison
        /// </summary>
        /// <returns>A suitable hash code</returns>
        
        public override int GetHashCode()
        {
            return ((JAtomicValue)value).asMapKey().GetHashCode();
        }         

        /// <summary>
        /// Get the name of the value's XDM type
        /// </summary>
        /// <param name="processor">The <code>Processor</code> object. 
        ///This parameter is no longer required</param>
        /// <returns>The type of the value, as a QName.


        public QName GetTypeName(Processor processor)
        {
            JStructuredQName sqname = ((JAtomicValue)value).getItemType().getStructuredQName();
            return new QName(sqname.getPrefix(),
                             sqname.getURI(),
                             sqname.getLocalPart());
        }

        /// <summary>
        /// Get the name of the primitive type of the value
        /// </summary>
        /// <returns>The primitive type of the value, as a QName. This will be the name of
        /// one of the primitive types defined in XML Schema Part 2, or the XPath-defined
        /// type <c>xs:untypedAtomic</c>. For the purposes of this method, <c>xs:integer</c> is considered
        /// to be a primitive type.
        /// </returns>


        public QName GetPrimitiveTypeName()
        {
            int fp = ((JAtomicValue)value).getItemType().getPrimitiveType();
            return new QName(JStandardNames.getPrefix(fp),
                             JStandardNames.getURI(fp),
                             JStandardNames.getLocalName(fp));
        }

        /// <summary>Get the value as a CLI object of the nearest equivalent type.</summary>
        /// <remarks>
        /// <para>The return type is as follows:</para>
        /// <para>xs:string - String</para>
        /// <para>xs:integer - Long</para>
        /// <para>xs:decimal - Decimal</para>
        /// <para>xs:double - Double</para>
        /// <para>xs:float - Float</para>
        /// <para>xs:boolean - Bool</para>
        /// <para>xs:QName - QName</para>
        /// <para>xs:anyURI - Uri</para>
        /// <para>xs:untypedAtomic - String</para>
        /// <para>wrapped external object - the original external object</para>
        /// <para>Other types - currently String, but this may change in the future</para>
        /// </remarks>
        /// <returns>The value converted to the most appropriate CLI type</returns>

        public Object Value
        {
            get
            {
                if (value is JIntegerValue)
                {
                    return ((JIntegerValue)value).longValue();
                }
                else if (value is JDoubleValue)
                {
                    return ((JDoubleValue)value).getDoubleValue();
                }
                else if (value is JFloatValue)
                {
                    return ((JFloatValue)value).getFloatValue();
                }
                else if (value is JDecimalValue)
                {
                    return Decimal.Parse(((JDecimalValue)value).getStringValue());
                }
                else if (value is JBooleanValue)
                {
                    return ((JBooleanValue)value).getBooleanValue();
                }
                else if (value is JAnyURIValue)
                {
                    return new Uri(((JAnyURIValue)value).getStringValue());
                }
                else if (value is JQNameValue)
                {
                    return new QName((JQNameValue)value);
                }
                else if (value is JDotNetObjectValue)
                {
                    return ((JDotNetObjectValue)value).getObject();
                }
                else
                {
                    return ((JAtomicValue)value).getStringValue();
                }
            }
        }


    }

    /// <summary inherits="XdmItem">
    /// The class <c>XdmFunctionItem</c> represents an item in an XPath 3.0 sequence
    /// that represents a function.
    /// </summary>
    /// <remarks>
    /// <para>Note that there is no guarantee that every <c>XdmValue</c> comprising a single
    /// function item will be an instance of this class. To force this, use the <c>Simplify</c>
    /// property of the <c>XdmValue</c>.</para>
    /// <para>At present the only way of creating an instance of this class is as the result of
    /// an XPath or XQuery expression that returns a function item. Note that this feature requires
    /// XPath 3.0 or XQuery 3.0 to be enabled, which in turn requires use of Saxon-EE.</para>
    /// </remarks>

    [Serializable]
    public class XdmFunctionItem : XdmItem
    {
        /// <summary>
        /// The name of the function, as a QName. The result will be null if the function is anonymous.
        /// </summary>
        
        public QName FunctionName
        {
            get
            {
                return QName.FromStructuredQName(((JFunction)value).getFunctionName());
            }
        }

        /// <summary>
        /// The arity of the function, that is, the number of arguments it expects
        /// </summary>

        public int Arity
        {
            get
            {
                return ((JFunction)value).getArity();
            }
        }

        /// <summary>
        /// Determine whether the item is an atomic value
        /// </summary>
        /// <returns>
        /// false (a function item is not an atomic value)
        /// </returns>

        public override bool IsAtomic()
        {
            return false;
        }


        /// <summary>
        /// Invoke the function
        /// </summary>
        /// <param name="arguments">The arguments to the function</param>
        /// <param name="processor">The Saxon processor, used to provide context information</param>
        /// <returns>The result of calling the function</returns>
        /// 
        public XdmValue invoke(XdmValue[] arguments, Processor processor)
        {
            JSequence[] args = new JSequence[arguments.Length];
            for (int i = 0; i < arguments.Length; i++)
            {
                args[i] = arguments[i].Unwrap();
            }
			JXPathContext context = processor.Implementation.getConversionContext();
            JSequence result = ((JFunction)value).call(context, args);
            return XdmValue.Wrap(result);
        }
    }

    /// <summary inherits="XdmFunctionItem">
    /// The class <c>XdmArray</c> represents an array item in an XPath 3.1 sequence.
    /// An array in the XDM data model. An array is a list of zero or more members, each of which
    /// is an arbitrary XDM value.The array itself is an XDM item.
    /// </summary>
    [Serializable]
    public class XdmArray : XdmFunctionItem
    {


	///<summary> Constructor to create an empty XdmArray</summary>
        public XdmArray() {
            this.value = JSimpleArrayItem.EMPTY_ARRAY;
        }

        public XdmArray(XdmValue value) {
            int length = value.Count;
            JArrayList list = new JArrayList(length);
            foreach (XdmItem item in value.GetList())
            {
                list.add(item.Unwrap());
            }
            this.value = new JSimpleArrayItem(list);
        }


       
        ///<summary> Create an XdmArray supplying the members as an array of XdmValue objects</summary>
        /// <param name="members"> Members an array of XdmValue objects. Note that subsequent changes 
        /// to the array will have no effect on the XdmValue.</param>
     

        public XdmArray(XdmValue [] members) {
            JArrayList list = new JArrayList(members.Length);
            for (int i =0; i< members.Length;i++) {
                list.add(members[i].Unwrap());
            }
            this.value = new JSimpleArrayItem(list);
        }

        internal XdmArray(JArrayList list)
        {
            this.value = new JSimpleArrayItem(list);
        }

        internal XdmArray(JArrayItem array) {
            this.value = array;
        }


        /// <summary>Create an XdmArray supplying the members as a collection of XdmValue objects</summary>
        /// <param name="members"> members a sequence of XdmValue objects. Note that if this is supplied as 
        /// a list or similar collection, subsequent changes to the list/collection will have no effect on 
        /// the XdmValue.</param>
        /// <remarks>Note that the argument can be a single XdmValue representing a sequence, in which case the
        ///  constructed array will have one member for each item in the supplied sequence.</remarks>
    
        public XdmArray(List<XdmValue> members) {
            JArrayList list = new JArrayList(members.Count);
            for (int i = 0; i < members.Count; i++)
            {
                list.add(members[i].Unwrap());
            }
            this.value = new JSimpleArrayItem(list);
        }

        /// <summary>
        /// Get the number of members in the array
        /// </summary>
        /// <returns>the number of members in the array.</returns> 
        /// <remarks>(Note that the {@link #size()} method returns 1 (one),
        /// because an XDM array is an item.)</remarks>
        public int ArrayLength() {
            return ((JArrayItem)value).arrayLength();
        }

        /// <summary>
        /// Get the n'th member in the array, counting from zero.
        /// </summary>
        /// <param name="n">the member that is required, counting the first member in the array as member zero</param>
        /// <returns>the n'th member in the sequence making up the array, counting from zero</returns>
        public XdmValue Get(int n) {
            try {
                JSequence member = ((JArrayItem)value).get(n);
                return XdmValue.Wrap(member);
            }
            catch (Exception) {
                throw new IndexOutOfRangeException();
            }
        }


        /// <summary>
        /// Create a new array in which one member is replaced with a new value.
        /// </summary>
        /// <param name="n">the position of the member that is to be replaced, counting the first member
        /// in the array as member zero</param>
        /// <param name="value"></param>
        /// <returns></returns>
        public XdmArray Put(int n, XdmValue valuei) {
            try {
                JSequence member = valuei.Unwrap();
                return (XdmArray)XdmValue.Wrap(((JArrayItem)this.value).put(n, member));
            } catch (Exception) {
                throw new IndexOutOfRangeException();
            }
        }


        /// <summary>
        /// Get the members of the array in the form of a list.
        /// </summary>
        /// <returns>A list of the members of this array.</returns>
        public List<XdmValue> AsList() {

            JSimpleArrayItem val = (JSimpleArrayItem)value;
            java.util.Iterator  iter = val.iterator();
            List<XdmValue> result = new List<XdmValue>(val.getLength());
            while (iter.hasNext()) {
                result.Add(XdmValue.Wrap((JSequence)iter.next()));

            }
            return result;
        }

        /// <summary>
        /// Make an XDM array from an object array. Each member of the supplied array
        /// is converted to a single member in the result array using the method
        /// {@link XdmValue#MakeValue(Object)}        
        /// </summary>
        /// <param name="o">the array of objects</param>
        /// <returns>the result of the conversion if successful</returns>
        public static XdmArray MakeArray(object[] o)
        {
            JArrayList list = new JArrayList(o.Length);
            for (int i = 0; i <o.Length; i++)
            {
                list.add(XdmValue.MakeValue(o[i]).Unwrap());
            }
            return new XdmArray(list);
        }

        /// <summary>
        /// Make an XdmArray whose members are xs:boolean values       
        /// </summary>
        /// <param name="o">input the input array of booleans</param>
        /// <returns>an XdmArray whose members are xs:boolean values corresponding one-to-one with the input</returns>
        public static XdmArray MakeArray(bool[] o)
        {
            JArrayList list = new JArrayList(o.Length);
            for (int i = 0; i < o.Length; i++)
            {
                list.add(new XdmAtomicValue(o[i]).Unwrap());
            }
            return new XdmArray(list);
        }


        /// <summary>
        /// Make an XdmArray whose members are xs:long values      
        /// </summary>
        /// <param name="o">input the input array of integers</param>
        /// <returns>an XdmArray whose members are xs:integer values corresponding one-to-one with the input</returns>
        public static XdmArray MakeArray(long[] o)
        {
            JArrayList list = new JArrayList(o.Length);
            for (int i = 0; i < o.Length; i++)
            {
                list.add(new XdmAtomicValue(o[i]).Unwrap());
            }
            return new XdmArray(list);
        }


        /// <summary>
        /// Make an XdmArray whose members are xs:integer values      
        /// </summary>
        /// <param name="o">input the input array of integers</param>
        /// <returns>an XdmArray whose members are xs:integer values corresponding one-to-one with the input</returns>
        public static XdmArray MakeArray(int[] o)
        {
            JArrayList list = new JArrayList(o.Length);
            for (int i = 0; i < o.Length; i++)
            {
                list.add(new XdmAtomicValue(o[i]).Unwrap());
            }
            return new XdmArray(list);
        }

        /// <summary>
        /// Make an XdmArray whose members are xs:integer values      
        /// </summary>
        /// <param name="o">input the input array of integers</param>
        /// <returns>an XdmArray whose members are xs:integer values corresponding one-to-one with the input</returns>
        public static XdmArray MakeArray(byte[] o)
        {
            JArrayList list = new JArrayList(o.Length);
            for (int i = 0; i < o.Length; i++)
            {
                list.add(new XdmAtomicValue(o[i]).Unwrap());
            }
            return new XdmArray(list);
        }
    }

    /// <summary inherits="XdmFunctionItem">
    /// The class <c>XdmMap</c> represents a map item in an XPath 3.1 sequence.
    /// A map in the XDM data model. A map is a list of zero or more entries, each of which
    /// is a pair comprising a key(which is an atomic value) and a value(which is an arbitrary value).
    /// The map itself is an XDM item
    /// </summary>


    [Serializable]
    public class XdmMap :  XdmFunctionItem
    {
  

        /// <summary>
        /// Create an empty XdmMap
        /// </summary>
        public XdmMap() { this.value = new JHashTrieMap(); }

        internal XdmMap(JHashTrieMap map) { this.value = map; }

        
        /*public XdmMap(Dictionary<XdmAtomicValue, T >  map ) where T : XdmValue  {
            JHashTrieMap val = new JHashTrieMap();
            foreach(KeyValuePair<XdmAtomicValue, T> entry in map) {
                val.initialPut((JAtomicValue)entry.Key.Unwrap(), entry.Value.Unwrap());
            }
            this.value = val;
        }*/

        /// <summary>
        /// Get the number of entries in the map
        /// </summary>
        /// <remarks>the number of entries in the map. (Note that the {@link #size()} method returns 1 (one),
        /// because an XDM map is an item.)</remarks>
        public int Size {
            get {
                return ((JMapItem)value).size();
            }

        }


        /// <summary>
        /// Is empty check on the XdmMap
        /// </summary>
        /// <returns>Returns <code>true</code> if this map contains no key-value mappings.</returns>
        public bool IsEmpty() {
            return ((JMapItem)value).isEmpty();

        }

        /// <summary>
        ///  Create a new map containing an additional (key, value) pair.
        /// If there is an existing entry with the same key, it is removed
        /// </summary>
        /// <param name="key">a new map containing the additional entry. The original map is unchanged.</param>
        /// <param name="value"></param>
        /// <returns></returns>
        public XdmMap Put(XdmAtomicValue key, XdmValue value)
        {
            XdmMap map2 = new XdmMap();
            map2.value = ((JMapItem)this.value).addEntry((JAtomicValue)key.Unwrap(), value.Unwrap());
            return map2;
        }


        /// <summary>
        /// Create a new map in which the entry for a given key has been removed.
        /// If there is no entry with the same key, the new map has the same content as the old(it may or may not
        /// be the same .NET object)
        /// </summary>
        /// <param name="key">The key to which entry to be removed</param>
        /// <returns>a map without the specified entry. The original map is unchanged.</returns>
        public XdmMap Remove(XdmAtomicValue key)
        {
            XdmMap map2 = new XdmMap();
            map2.value = ((JMapItem)this.value).remove((JAtomicValue)key.Unwrap());
            return map2;
        }


        /// <summary>
        ///  Return a corresponding .NET Dictionary collection of keys and values.
        /// </summary>
        /// <returns>a mutable Dictionary from atomic values to (sequence) values, containing the
        /// same entries as this map</returns>
        public Dictionary<XdmAtomicValue, XdmValue> AsDictionary() {
            Dictionary<XdmAtomicValue, XdmValue> map = new Dictionary<XdmAtomicValue, XdmValue>();
            JMapItem jmap = (JMapItem)value;
            java.util.Iterator iter = jmap.iterator();
            JKeyValuePair pair = null;
            while (iter.hasNext()) {
                pair = (JKeyValuePair)iter.next();
                map.Add((XdmAtomicValue)XdmValue.Wrap(pair.key), XdmValue.Wrap(pair.value));
            }
            return map;
        }


        /// <summary>
        /// Get the keys present in the map in the form of a set.
        /// </summary>
        /// <returns>a set of the keys present in this map, in arbitrary order.</returns>
        public HashSet <XdmAtomicValue>KeySet()
        {
            HashSet<XdmAtomicValue> result = new HashSet<XdmAtomicValue>();
            JMapItem jmap = (JMapItem)value;
            net.sf.saxon.tree.iter.AtomicIterator iter = jmap.keys();
            JAtomicValue key = null;
            while ((key = iter.next()) != null)
            {
                result.Add((XdmAtomicValue)XdmValue.Wrap(key));
            }
            return result;
        }


        /// <summary>
        /// Returns <code>true</code> if this map contains a mapping for the specified
        /// key. More formally, returns <code>true</code> if and only if
        /// this map contains a mapping for a key<code>k</code> such that
        /// <code>(key==null ? k==null : key.Equals(k))</code>.  (There can be
        /// at most one such mapping.)
        /// </summary>
        /// <param name="key">key key whose presence in this map is to be tested</param>
        /// <returns><tt>true</tt> if this map contains a mapping for the specified key</returns>
        public bool ContainsKey(object key) {
            JAtomicValue k = (JAtomicValue)((XdmAtomicValue)key).value;
            return ((JMapItem)value).get(k)!= null;
        }


        /// <summary>
        ///  Returns the value to which the specified key is mapped,
        /// or {@code null} if this map contains no mapping for the key.
        /// </summary>
        /// <param name="key"> key the key whose associated value is to be returned. If this is
        ///            not an XdmAtomicValue, the method attempts to construct an
        ///            XdmAtomicValue using the method {@link XdmAtomicValue#MakeAtomicValue(Object)};
        /// it is therefore possible to pass a simple key such as a string or integer.</param>
        /// <returns>the value to which the specified key is mapped, or
        /// {@code null} if this map contains no mapping for the key</returns>
        public XdmValue Get(object key)
        {
            if (key == null) {
                throw new ArgumentNullException();
            }
            if (!(key is XdmAtomicValue)) {
                try
                {
                    key = XdmAtomicValue.MakeAtomicValue(key);
                }
                catch (Exception ex) {
                    throw new InvalidCastException(ex.ToString());
                }
            }
            JAtomicValue k = (JAtomicValue)((XdmAtomicValue)key).value;
            JSequence v = ((JMapItem)value).get(k);
            return v==null ? null : XdmValue.Wrap(v);
        }

        /// <summary>
        /// Returns a {@link Collection} view of the values contained in this map.
        /// The collection is backed by the map, so changes to the map are
        /// reflected in the collection, and vice-versa.If the map is
        /// modified while an iteration over the collection is in progress
        /// (except through the iterator's own <code>remove</code> operation),
        /// the results of the iteration are undefined.The collection
        /// supports element removal, which removes the corresponding
        /// mapping from the map, via the<code> Iterator.remove</code>,
        /// <code>Collection.remove</code>, <code>removeAll</code>,
        /// <code>retainAll</code> and <code>clear</code> operations.  It does not
        /// support the <code>add</code> or <code>addAll</code> operations.
        /// </summary>
        /// <returns>A collection view of the values contained in this map</returns>
        public ICollection Values() {
            List<XdmValue> result = new List<XdmValue>();

            JMapItem jmap = (JMapItem)value;
            java.util.Iterator iter = jmap.iterator();
            JKeyValuePair pair = null;
            while ((pair = (JKeyValuePair)iter.next()) != null)
            {
                result.Add((XdmAtomicValue)XdmValue.Wrap(pair.value));
            }

            return result;
        }


        /// <summary>
        /// Returns a {@link Set} view of the mappings contained in this map.
        /// </summary>
        /// <returns>a set view of the mappings contained in this map</returns>
        public HashSet<DictionaryEntry> EntrySet() {
            HashSet<DictionaryEntry> result = new HashSet<DictionaryEntry>();
            JMapItem jmap = (JMapItem)value;
            java.util.Iterator iter = jmap.iterator();
            JKeyValuePair pair = null;
            while ((pair = (JKeyValuePair)iter.next()) != null)
            {
                result.Add(new DictionaryEntry(pair.key, pair.value));
            }
            return result;
        }


        /// <summary>
        /// Static factory method to construct an XDM map by converting each entry
        /// in a supplied generic collection of key/value pairs; <code>IDictionary</code>.The keys in the 
        /// Dictionary must be convertible to XDM atomic values using the 
        /// {@link XdmAtomicValue#MakeAtomicValue(Object)} method. The associated values 
        /// must be convertible to XDM sequences
        /// using the {@link XdmValue#MakeValue(Object)} method.
        /// </summary>
        /// <param name="input">input the supplied map</param>
        /// <returns>the resulting XdmMap</returns>
        public static XdmMap MakeMap(IDictionary input) {
            JHashTrieMap result = new JHashTrieMap();
            XdmAtomicValue key;
            XdmValue value;
            
            foreach (object keyi in input.Keys)
            {
                key = XdmAtomicValue.MakeAtomicValue(keyi);
                value = XdmValue.MakeValue(input[keyi]);
                result.initialPut((JAtomicValue)key.Unwrap(), value.Unwrap());
            }
           
            return new XdmMap(result);
        }

        


    }
    
    /// <summary inherits="XdmItem">
    /// The class <c>XdmNode</c> represents a Node in the XDM Data Model. A Node
    /// is an <c>XdmItem</c>, and is therefore an <c>XdmValue</c> in its own right, and may also participate
    /// as one item within a sequence value.
    /// </summary>
    /// <remarks>
    /// <para>An <c>XdmNode</c> is implemented as a wrapper around an object
    /// of type <c>net.sf.saxon.NodeInfo</c>. Because this is a key interface
    /// within Saxon, it is exposed via this API, even though it is a Java
    /// interface that is not part of the API proper.</para>
    /// <para>The <c>XdmNode</c> interface exposes basic properties of the node, such
    /// as its name, its string value, and its typed value. Navigation to other nodes
    /// is supported through a single method, <c>EnumerateAxis</c>, which allows
    /// other nodes to be retrieved by following any of the XPath axes.</para>
    /// </remarks>

    [Serializable]
    public class XdmNode : XdmItem
    {

        /// <summary>
        /// Determine whether the item is an atomic value
        /// </summary>
        /// <returns>
        /// false (the item is not an atomic value)
        /// </returns>

        public override bool IsAtomic()
        {
            return false;
        }

        /// <summary>
        /// The name of the node, as a <c>QName</c>. Returns null in the case of unnamed nodes.
        /// </summary>

        public QName NodeName
        {
            get
            {
                JNodeInfo node = (JNodeInfo)value;
                String local = node.getLocalPart();
                if (local == "")
                {
                    return null;
                }
                String prefix = node.getPrefix();
                String uri = node.getURI();
                return new QName(prefix, uri, local);
            }
        }

        /// <summary>
        /// The kind of node, as an instance of <c>System.Xml.XmlNodeType</c>.
        /// </summary>
        /// <remarks>For a namespace node in the XDM model, the value XmlNodeType.None 
        /// is returned.
        /// </remarks>

        public XmlNodeType NodeKind
        {
            get
            {
                JNodeInfo node = (JNodeInfo)value;
                int kind = node.getNodeKind();
                switch (kind)
                {
                    case JType.DOCUMENT:
                        return XmlNodeType.Document;
                    case JType.ELEMENT:
                        return XmlNodeType.Element;
                    case JType.ATTRIBUTE:
                        return XmlNodeType.Attribute;
                    case JType.TEXT:
                        return XmlNodeType.Text;
                    case JType.COMMENT:
                        return XmlNodeType.Comment;
                    case JType.PROCESSING_INSTRUCTION:
                        return XmlNodeType.ProcessingInstruction;
                    case JType.NAMESPACE:
                        return XmlNodeType.None;
                    default:
                        throw new ArgumentException("Unknown node kind");
                }
            }
        }

        /// <summary>
        /// Get the line number of the node in a source document. For a document constructed using the document
        /// builder, this is available only if the line numbering option was set wehn the document was built (and
        /// then only for element nodes). If the line number is not available, the value -1 is returned.
        /// Line numbers will typically be as reported by a SAX parser; this means that the line number for an element
        /// node is the line number containing the closing ">" of the start tag.
        /// </summary>
         
        public int LineNumber {
            get { return ((JNodeInfo)value).getLineNumber(); }
        }


        /// <summary>
        /// Get the column number of the node in a source document. For a document constructed using the document
        /// builder, this is available only if the line numbering option was set wehn the document was built (and
        /// then only for element nodes). If the column number is not available, the value -1 is returned.
        /// Line numbers will typically be as reported by a SAX parser; this means that the column number for an element
        /// node is the column number containing the closing ">" of the start tag.
        /// </summary>
        public int ColumnNumber
        {
            get { return ((JNodeInfo)value).getColumnNumber(); }
        }

        /// <summary>
        /// The typed value of the node, as an instance of <c>XdmValue</c>.
        /// </summary>
        /// <exception>
        /// A DynamicError is thrown if the node has no typed value, as will be the case for
        /// an element with element-only content.
        /// </exception>

        public XdmValue TypedValue
        {
            get { return XdmValue.Wrap(((JNodeInfo)value).atomize()); }
        }

        /// <summary>
        /// Unwraps the underlying XmlNode object from the XdmValue.
        /// If the method does not wrap a XmlNode then a null is returned
        /// </summary>
        /// <returns>The underlying XmlNode</returns>
        public XmlNode getUnderlyingXmlNode()
        {

            if (value is net.sf.saxon.dotnet.DotNetNodeWrapper)
            {

                return (XmlNode)((net.sf.saxon.dotnet.DotNetNodeWrapper)value).getRealNode();
            }
            return null;
        }

        /// <summary>
        /// The string value of the node.
        /// </summary>

        public String StringValue
        {
            get { return ((JNodeInfo)value).getStringValue(); }
        }

        /// <summary>
        /// Get the parent of this node.
        /// </summary>
        /// <remarks>
        /// Returns either a document node, and element node, or null in the case where
        /// this node has no parent. 
        /// </remarks>

        public XdmNode Parent
        {
            get {
                JNodeInfo parent = ((JNodeInfo)value).getParent();
                return (parent == null ? null : (XdmNode)XdmValue.Wrap(parent)); 
            }
        }

        /// <summary>
        /// Get the root of the tree containing this node.
        /// </summary>
        /// <remarks>
        /// Returns the root of the tree containing this node (which might be this node itself).
        /// </remarks>

        public XdmNode Root
        {
            get
            {
                XdmNode parent = Parent;
                if (parent == null)
                {
                    return this;
                }
                else
                {
                    return parent.Root;
                }
            }
        }

        /// <summary>
        /// Get a the string value of a named attribute of this element. 
        /// </summary>
        /// <remarks>
        /// Returns null if this node is not an element, or if this element has no
        /// attribute with the specified name.
        /// </remarks>
        /// <param name="name">The name of the attribute whose value is required</param>

        public String GetAttributeValue(QName name)
        {
            return ((JNodeInfo)value).getAttributeValue(name.Uri, name.LocalName);
        }

        /// <summary>
        /// Get an enumerator that supplies all the nodes on one of the XPath
        /// axes, starting with this node.
        /// </summary>
        /// <param name="axis">
        /// The axis to be navigated, for example <c>XdmAxis.Child</c> for the child AxisInfo.
        /// </param>
        /// <remarks>
        /// The nodes are returned in axis order: that is, document order for a forwards
        /// axis, reverse document order for a reverse AxisInfo.
        /// </remarks>

        public IEnumerator EnumerateAxis(XdmAxis axis)
        {
            return new SequenceEnumerator(((JNodeInfo)value).iterateAxis(GetAxisNumber(axis)));
        }

        /// <summary>
        /// Get an enumerator that selects all the nodes on one of the XPath
        /// axes, provided they have a given name. The nodes selected are those of the principal
        /// node kind (elements for most axes, attributes for the attribute axis, namespace nodes
        /// for the namespace axis) whose name matches the name given in the second argument.
        /// </summary>
        /// <param name="axis">
        /// The axis to be navigated, for example <c>XdmAxis.Child</c> for the child AxisInfo.
        /// </param>
        /// <param name="nodeName">
        /// The name of the required nodes, for example <c>new QName("", "item")</c> to select
        /// nodes with local name "item", in no namespace.
        /// </param>
        /// <remarks>
        /// The nodes are returned in axis order: that is, document order for a forwards
        /// axis, reverse document order for a reverse AxisInfo.
        /// </remarks>

        public IEnumerator EnumerateAxis(XdmAxis axis, QName nodeName)
        {
            int kind;
            switch (axis)
            {
                case XdmAxis.Attribute:
                    kind = net.sf.saxon.type.Type.ATTRIBUTE;
                    break;
                case XdmAxis.Namespace:
                    kind = net.sf.saxon.type.Type.NAMESPACE;
                    break;
                default:
                    kind = net.sf.saxon.type.Type.ELEMENT;
                    break;
            }
            JNamePool pool = ((JNodeInfo)value).getConfiguration().getNamePool();
            JNameTest test = new JNameTest(kind, nodeName.Uri, nodeName.LocalName, pool);
            return new SequenceEnumerator(((JNodeInfo)value).iterateAxis(GetAxisNumber(axis), test));
        }

        private static byte GetAxisNumber(XdmAxis axis)
        {
            switch (axis)
            {
                case XdmAxis.Ancestor: return JAxisInfo.ANCESTOR;
                case XdmAxis.AncestorOrSelf: return JAxisInfo.ANCESTOR_OR_SELF;
                case XdmAxis.Attribute: return JAxisInfo.ATTRIBUTE;
                case XdmAxis.Child: return JAxisInfo.CHILD;
                case XdmAxis.Descendant: return JAxisInfo.DESCENDANT;
                case XdmAxis.DescendantOrSelf: return JAxisInfo.DESCENDANT_OR_SELF;
                case XdmAxis.Following: return JAxisInfo.FOLLOWING;
                case XdmAxis.FollowingSibling: return JAxisInfo.FOLLOWING_SIBLING;
                case XdmAxis.Namespace: return JAxisInfo.NAMESPACE;
                case XdmAxis.Parent: return JAxisInfo.PARENT;
                case XdmAxis.Preceding: return JAxisInfo.PRECEDING;
                case XdmAxis.PrecedingSibling: return JAxisInfo.PRECEDING_SIBLING;
                case XdmAxis.Self: return JAxisInfo.SELF;
            }
            return 0;
        }

        /// <summary>
        /// The Base URI of the node.
        /// </summary>

        public Uri BaseUri
        {
            get { 
				string baseUriStr = ((JNodeInfo)value).getBaseURI();
				if (baseUriStr == null || baseUriStr.Equals("")) {
					return null;
				}
				return new Uri(baseUriStr); 
			}
        }

        /// <summary>
        /// The Document URI of the node.
        /// </summary>

        public Uri DocumentUri
        {
            get
            {
                String s = ((JNodeInfo)value).getSystemId();
                if (s == null || s.Length == 0)
                {
                    return null;
                }
                return new Uri(s);
            }
        }

        /// <summary>
        /// Send the node (that is, the subtree rooted at this node) to an <c>XmlWriter</c>
        /// </summary>
        /// <remarks>
        /// Note that a <c>XmlWriter</c> can only handle a well-formed XML document. This method
        /// will therefore signal an exception if the node is a document node with no children, or with
        /// more than one element child.
        /// </remarks>
        /// <param name="writer">
        /// The <c>XmlWriter</c> to which the node is to be written
        /// </param>

        public void WriteTo(XmlWriter writer)
        {
            JNodeInfo node = ((JNodeInfo)value);
            JDotNetReceiver receiver = new JDotNetReceiver(writer);
            receiver.setPipelineConfiguration(node.getConfiguration().makePipelineConfiguration());
            receiver.open();
			node.copy(receiver, net.sf.saxon.om.CopyOptions.ALL_NAMESPACES, JExplicitLocation.UNKNOWN_LOCATION);
            receiver.close();
        }

        /// <summary>
        /// Return a serialization of this node as lexical XML
        /// </summary>
        /// <remarks>
        /// <para>In the case of an element node, the result will be a well-formed
        /// XML document serialized as defined in the W3C XSLT/XQuery serialization specification,
        /// using options method="xml", indent="yes", omit-xml-declaration="yes".</para>
        /// <para>In the case of a document node, the result will be a well-formed
        /// XML document provided that the document node contains exactly one element child,
        /// and no text node children. In other cases it will be a well-formed external
        /// general parsed entity.</para>
        /// <para>In the case of an attribute node, the output is a string in the form
        /// <c>name="value"</c>. The name will use the original namespace prefix.</para>
        /// <para>Other nodes, such as text nodes, comments, and processing instructions, are
        /// represented as they would appear in lexical XML.</para>
        /// </remarks>

        public String OuterXml
        {
            get
            {
                JNodeInfo node = ((JNodeInfo)value);

                if (node.getNodeKind() == JType.ATTRIBUTE)
                {
                    String val = node.getStringValue().Replace("\"", "&quot;");
                    val = val.Replace("<", "&lt;");
                    val = val.Replace("&", "&amp;");
                    return node.getDisplayName() + "=\"" + val + '"';
                } else if (node.getNodeKind() == JType.NAMESPACE) {
                    String val = node.getStringValue().Replace("\"", "&quot;");
                    val = val.Replace("<", "&lt;");
                    val = val.Replace("&", "&amp;");
                    String name = node.getDisplayName();
                    name = (name.Equals("") ? "xmlns" : "xmlns:" + name);
                    return name + "=\"" + val + '"';
                }

                Serializer serializer = new Serializer();
                serializer.SetOutputProperty(Serializer.METHOD, "xml");
                serializer.SetOutputProperty(Serializer.INDENT, "yes");
                serializer.SetOutputProperty(Serializer.OMIT_XML_DECLARATION, "yes");

                StringWriter sw = new StringWriter();
                serializer.SetOutputWriter(sw);
				node.copy(serializer.GetReceiver(node.getConfiguration()), net.sf.saxon.om.CopyOptions.ALL_NAMESPACES, JExplicitLocation.UNKNOWN_LOCATION);
                return sw.ToString();
            }
        }

        /// <summary>
        /// Two instances of XdmNode are equal if they represent the same node. That is, the Equals()
        /// method returns the same result as the XPath "is" operator.
        /// </summary>
        /// <param name="obj">The object node to be compared</param>
         
        public override bool Equals(object obj)
        {
            return obj is XdmNode && ((JNodeInfo)value).equals((JNodeInfo)((XdmNode)obj).value);
        }

        /// <summary>
        /// The hashCode of a node reflects the equality relationship: if two XdmNode instances
        /// represent the same node, then they have the same hashCode
        /// </summary>

        public override int GetHashCode()
        {
            return ((JNodeInfo)value).hashCode();
        }

        /// <summary>
        /// Return a string representation of the node.
        /// </summary>
        /// <remarks>
        /// This currently returns the same as the <c>OuterXml</c> property.
        /// To get the string value as defined in XPath, use the <c>StringValue</c> property.
        /// </remarks>

        public override String ToString()
        {
            return OuterXml;
        }

        /// <summary>
        /// Escape hatch to the underlying class in the Java implementation
        /// </summary>

        public JNodeInfo Implementation
        {
            get { return ((JNodeInfo)value); }
        }


    }


    /// <summary inherits="XdmValue">
    /// The class <c>XdmEmptySequence</c> represents an empty sequence in the XDM Data Model.
    /// </summary>
    /// <remarks>
    /// <para>An empty sequence <i>may</i> also be represented by an <c>XdmValue</c> whose length
    /// happens to be zero. Applications should therefore not test to see whether an object
    /// is an instance of this class in order to decide whether it is empty.</para>
    /// <para>In interfaces that expect an <c>XdmItem</c>, an empty sequence is represented
    /// by a CLI <c>null</c> value.</para> 
    /// </remarks>

    [Serializable]
    public sealed class XdmEmptySequence : XdmValue
    {

        ///<summary>The singular instance of this class</summary>

        public static XdmEmptySequence INSTANCE = new XdmEmptySequence();

        private XdmEmptySequence()
        {
            this.value = JEmptySequence.getInstance();
        }
    }


    /// <summary>
    /// The QName class represents an instance of xs:QName, as defined in the XPath 2.0
    /// data model. Internally, it has three components, a namespace URI, a local name, and
    /// a prefix. The prefix is intended to be used only when converting the value back to 
    /// a string.
    /// </summary>
    /// <remarks>
    /// Note that a QName is not itself an <c>XdmItem</c> in this model; however it can
    /// be wrapped in an XdmItem.
    /// </remarks>    

    [Serializable]
    public sealed class QName
    {

        private JStructuredQName sqName;
        //private String prefix;
        //private String uri;
        //private String local;
        //int hashcode = -1;      // evaluated lazily


        private static String XS = NamespaceConstant.SCHEMA;

        /// <summary>QName constant for the name xs:string</summary>
        public static readonly QName XS_STRING = new QName(XS, "xs:string");

        /// <summary>QName constant for the name xs:integer</summary>
        public static readonly QName XS_INTEGER = new QName(XS, "xs:integer");

        /// <summary>QName constant for the name xs:double</summary>
        public static readonly QName XS_DOUBLE = new QName(XS, "xs:double");

        /// <summary>QName constant for the name xs:float</summary>
        public static readonly QName XS_FLOAT = new QName(XS, "xs:float");

        /// <summary>QName constant for the name xs:decimal</summary>
        public static readonly QName XS_DECIMAL = new QName(XS, "xs:decimal");

        /// <summary>QName constant for the name xs:boolean</summary>
        public static readonly QName XS_BOOLEAN = new QName(XS, "xs:boolean");

        /// <summary>QName constant for the name xs:anyURI</summary>
        public static readonly QName XS_ANYURI = new QName(XS, "xs:anyURI");

        /// <summary>QName constant for the name xs:QName</summary>
        public static readonly QName XS_QNAME = new QName(XS, "xs:QName");

        /// <summary>QName constant for the name xs:untypedAtomic</summary>
        public static readonly QName XS_UNTYPED_ATOMIC = new QName(XS, "xs:untypedAtomic");

        /// <summary>QName constant for the name xs:untypedAtomic (for backwards compatibility)</summary>
        public static readonly QName XDT_UNTYPED_ATOMIC = new QName(XS, "xs:untypedAtomic");

        /// <summary>
        /// Construct a QName representing a name in no namespace
        /// </summary>
        /// <remarks>
        /// This constructor does not check that the components of the QName are
        /// lexically valid.
        /// </remarks>
        /// <param name="local">The local part of the name
        /// </param>

        public QName(String local)
        {
            // TODO: check for validity
            int colon = local.IndexOf(':');
            if (colon < 0)
            {
                sqName = new JStructuredQName("", "", local);
            }
            else {
                
                    throw new ArgumentException("Local name contains a colon");
                }             
        }

        /// <summary>
        /// Construct a QName using a namespace URI and a lexical representation.
        /// The lexical representation may be a local name on its own, or it may 
        /// be in the form <c>prefix:local-name</c>
        /// </summary>
        /// <remarks>
        /// This constructor does not check that the components of the QName are
        /// lexically valid.
        /// </remarks>
        /// <param name="uri">The namespace URI. Use either the string "" or null
        /// for names that are not in any namespace.
        /// </param>
        /// <param name="lexical">Either the local part of the name, or the prefix
        /// and local part in the format <c>prefix:local</c>
        /// </param>

        public QName(String uri, String lexical)
        {
            // TODO: check for validity
            uri = (uri == null ? "" : uri);
            int colon = lexical.IndexOf(':');
            if (colon < 0)
            {
                sqName = new JStructuredQName("", uri, lexical);
            }
            else
            {

                string prefix = lexical.Substring(0, colon);
                string local = lexical.Substring(colon + 1);
                sqName = new JStructuredQName(prefix, uri, local);
            }
        }

        /// <summary>
        /// Construct a QName using a namespace prefix, a namespace URI, and a local name
        /// (in that order).
        /// </summary>
        /// <remarks>
        /// This constructor does not check that the components of the QName are
        /// lexically valid.
        /// </remarks>
        /// <param name="prefix">The prefix of the name. Use either the string ""
        /// or null for names that have no prefix (that is, they are in the default
        /// namespace)</param>
        /// <param name="uri">The namespace URI. Use either the string "" or null
        /// for names that are not in any namespace.
        /// </param>
        /// <param name="local">The local part of the name</param>

        public QName(String prefix, String uri, String local)
        {
            sqName = new JStructuredQName(prefix, uri, local);
        }

        /// <summary>
        /// Construct a QName from a lexical QName, supplying an element node whose
        /// in-scope namespaces are to be used to resolve any prefix contained in the QName.
        /// </summary>
        /// <remarks>
        /// <para>This constructor checks that the components of the QName are
        /// lexically valid.</para>
        /// <para>If the lexical QName has no prefix, the name is considered to be in the
        /// default namespace, as defined by <c>xmlns="..."</c>.</para>
        /// <para>If the prefix of the lexical QName is not in scope, returns null.</para>
        /// </remarks>
        /// <param name="lexicalQName">The lexical QName, in the form <code>prefix:local</code>
        /// or simply <c>local</c>.</param>
        /// <param name="element">The element node whose in-scope namespaces are to be used
        /// to resolve the prefix part of the lexical QName.</param>
        /// <exception cref="ArgumentException">If the prefix of the lexical QName is not in scope</exception>
        /// <exception cref="ArgumentException">If the lexical QName is invalid 
        /// (for example, if it contains invalid characters)</exception>
        /// 

        public QName(String lexicalQName, XdmNode element)
        {
            try
            {
                JNodeInfo node = (JNodeInfo)element.value;
				sqName = JStructuredQName.fromLexicalQName(lexicalQName, true, true, new JInscopeNamespaceResolver(node));
				
            }
            catch (net.sf.saxon.trans.XPathException err)
            {
                throw new ArgumentException(err.getMessage());
            }
        }

        /// <summary>
        /// Construct a <c>QName</c> from an <c>XmlQualifiedName</c> (as defined in the
        /// <c>System.Xml</c> package).
        /// </summary>
        /// <remarks>
        /// Note that an <c>XmlQualifiedName</c> does not contain any prefix, so the result
        /// will always have a prefix of ""
        /// </remarks>
        /// <param name="qualifiedName">The XmlQualifiedName</param>

        public QName(XmlQualifiedName qualifiedName)
        {
            string uri = qualifiedName.Namespace;
            string local = qualifiedName.Name;
            string prefix = String.Empty;
            sqName = new JStructuredQName(prefix, uri, prefix);
        }

        //  internal constructor from a QNameValue

        internal QName(JQNameValue q)
        {
            sqName = new JStructuredQName(q.getPrefix(), q.getNamespaceURI(), q.getLocalName());
        }

        /// <summary>
        /// Factory method to construct a QName from a string containing the expanded
        /// QName in Clark notation, that is, <c>{uri}local</c>
        /// </summary>
        /// <remarks>
        /// The prefix part of the <c>QName</c> will be set to an empty string.
        /// </remarks>
        /// <param name="expandedName">The URI in Clark notation: <c>{uri}local</c> if the
        /// name is in a namespace, or simply <c>local</c> if not.</param> 

        public static QName FromClarkName(String expandedName)
        {
            String namespaceURI;
            String localName;
            if (expandedName[0] == '{')
            {
                int closeBrace = expandedName.IndexOf('}');
                if (closeBrace < 0)
                {
                    throw new ArgumentException("No closing '}' in Clark name");
                }
                namespaceURI = expandedName.Substring(1, closeBrace - 1);
                if (closeBrace == expandedName.Length)
                {
                    throw new ArgumentException("Missing local part in Clark name");
                }
                localName = expandedName.Substring(closeBrace + 1);
            }
            else
            {
                namespaceURI = "";
                localName = expandedName;
            }

            return new QName("", namespaceURI, localName);
        }


       /// <summary>
       ///Factory method to construct a QName from a string containing the expanded
       ///QName in EQName notation, that is, <c>Q{uri}local</c>
       /// </summary>
       /// <remarks>
       ///The prefix part of the <c>QName</c> will be set to an empty string.
       /// </remarks>
       /// <param name="expandedName">The URI in EQName notation: <c>{uri}local</c> if the
       /// name is in a namespace. For a name in no namespace, either of the
       /// forms <c>Q{}local</c> or simply <c>local</c> are accepted.</param>
       ///<returns> the QName corresponding to the supplied name in EQName notation. This will always
       ///have an empty prefix.</returns>
       
        public static QName FromEQName(String expandedName)
        {
            String namespaceURI;
            String localName;
            if (expandedName[0] == 'Q' && expandedName[1] == '{')
            {
                int closeBrace = expandedName.IndexOf('}');
                if (closeBrace < 0)
                {
                    throw new ArgumentException("No closing '}' in EQName");
                }
                namespaceURI = expandedName.Substring(2, closeBrace);
                if (closeBrace == expandedName.Length)
                {
                    throw new ArgumentException("Missing local part in EQName");
                }
                localName = expandedName.Substring(closeBrace + 1);
            }
            else
            {
                namespaceURI = "";
                localName = expandedName;
            }

            return new QName("", namespaceURI, localName);
        }

        // internal method: Factory method to construct a QName from Saxon's internal <c>StructuredQName</c>
        // representation.

        internal static QName FromStructuredQName(JStructuredQName sqn) {
            return new QName(sqn.getPrefix(), sqn.getURI(), sqn.getLocalPart());
        }

        /// <summary>
        /// Register a QName with the <c>Processor</c>. This makes comparison faster
        /// when the QName is compared with others that are also registered with the <c>Processor</c>.
        /// Depreacted method.
        /// </summary>
        /// <remarks>
        /// A given <c>QName</c> object can only be registered with one <c>Processor</c>.
        /// </remarks>
        /// <param name="processor">The Processor in which the name is to be registered.</param>
        [System.Obsolete("This method is no longer in use")]
        public void Register(Processor processor)
        {}

        /// <summary>
        /// Validate the QName against the XML 1.0 or XML 1.1 rules for valid names.
        /// </summary>
        /// <param name="processor">The Processor in which the name is to be validated.
        /// This determines whether the XML 1.0 or XML 1.1 rules for forming names are used.</param>
        /// <returns>true if the name is valid, false if not</returns>

        public bool IsValid(Processor processor)
        {
            if(this.Prefix != String.Empty)
            {
                if (!JNameChecker.isValidNCName(Prefix))
                {
                    return false;
                }
            }
            if (!JNameChecker.isValidNCName(this.LocalName))
            {
                return false;
            }
            return true;
        }

        /// <summary>The prefix of the QName. This plays no role in operations such as comparison
        /// of QNames for equality, but is retained (as specified in XPath) so that a string representation
        /// can be reconstructed.
        /// </summary>
        /// <remarks>
        /// Returns the zero-length string in the case of a QName that has no prefix.
        /// </remarks>

        public String Prefix
        {
            get { return sqName.getPrefix(); }
        }

        /// <summary>The namespace URI of the QName. Returns "" (the zero-length string) if the
        /// QName is not in a namespace.
        /// </summary>

        public String Uri
        {
            get { return sqName.getURI(); }
        }

        /// <summary>The local part of the QName</summary>

        public String LocalName
        {
            get { return sqName.getLocalPart(); }
        }

        /// <summary>The expanded name, as a string using the notation devised by James Clark.
        /// If the name is in a namespace, the resulting string takes the form <c>{uri}local</c>.
        /// Otherwise, the value is the local part of the name.
        /// </summary>

        public String ClarkName
        {
            get
            {
                string uri = Uri;
                if (uri.Equals(""))
                {
                    return LocalName;
                }
                else
                {
                    return "{" + uri + "}" + LocalName;
                }
            }
        }

        /// <summary>The expanded name in EQName format that is <c>Q{uri}local</c>. A no namespace name is returned as <c>Q{}local</c>.
        /// </summary>
        public String EQName
        {
            get
            {
                return "Q{" + Uri + "}" + LocalName;
            }
        }

        /// <summary>
        /// Convert the value to a string. The resulting string is the lexical form of the QName,
        /// using the original prefix if there was one.
        /// </summary>

        public override String ToString()
        {

            if (Prefix.Equals(""))
            {
                return LocalName;
            }
            else
            {
				return Prefix + ":" + LocalName;
            }
        }

        /// <summary>
        /// Get a hash code for the QName, to support equality matching. This supports the
        /// semantics of equality, which considers only the namespace URI and local name, and
        /// not the prefix.
        /// </summary>
        /// <remarks>
        /// The algorithm for allocating a hash code does not depend on registering the QName 
        /// with the <c>Processor</c>.
        /// </remarks>

        public override int GetHashCode()
        {
            return sqName.hashCode();
        }

        /// <summary>
        /// Test whether two QNames are equal. This supports the
        /// semantics of equality, which considers only the namespace URI and local name, and
        /// not the prefix.
        /// </summary>
        /// <remarks>
        /// The result of the function does not depend on registering the QName 
        /// with the <c>Processor</c>, but is computed more quickly if the QNames have
        /// both been registered
        /// </remarks>
        /// <param name="other">The value to be compared with this QName. If this value is not a QName, the
        /// result is always false. Otherwise, it is true if the namespace URI and local name both match.</param>

        public override bool Equals(Object other)
        {
            if (!(other is QName))
            {
                return false;
            }
            return sqName.equals(((QName)other).sqName);
        }

        /// <summary>
        /// Convert the value to an <c>XmlQualifiedName</c> (as defined in the
        /// <c>System.Xml</c> package)
        /// </summary>
        /// <remarks>
        /// Note that this loses the prefix.
        /// </remarks>

        public XmlQualifiedName ToXmlQualifiedName()
        {
            return new XmlQualifiedName(LocalName, Uri);
        }

		// internal method: Convert to a net.sf.saxon.value.QNameValue

        internal JQNameValue ToQNameValue()
        {
            return new JQNameValue(sqName.getPrefix(), sqName.getURI(), sqName.getLocalPart(), null);
        }

        internal JStructuredQName ToStructuredQName()
        {
            return new JStructuredQName(Prefix, Uri, LocalName);
        }




    }

    /// <summary>
    /// This class represents an enumeration of the values in an XPath
    /// sequence. It implements the IEnumerator interface, and the objects
    /// returned are always instances of <c>XdmItem</c>. In addition to the
    /// methods defined by <c>IEnumerator</c>, an additional method <c>GetAnother</c>
    /// must be implemented: this provides a new iterator over the same sequence
    /// of items, positioned at the start of the sequence.
    /// </summary>
    /// <remarks>
    /// Because the underlying value can be evaluated lazily, it is possible
    /// for exceptions to occur as the sequence is being read.
    /// </remarks>

    public interface IXdmEnumerator : IEnumerator
    {

        /// <summary>
        /// Create another <c>XdmEnumerator</c> over the same sequence of values, positioned at the start
        /// of the sequence, with no change to this <c>XdmEnumerator</c>.
        /// </summary>
        /// <returns>
        /// A new XdmEnumerator over the same sequence of XDM items, positioned at the start of the sequence.
        /// </returns>

        /**public**/ IXdmEnumerator GetAnother();
    }

    /// <summary>
    /// This class is an implementation of <c>IXdmEnumerator</c> that wraps
    /// a (Java) SequenceIterator.
    /// </summary>
    /// <remarks>
    /// Because the underlying value can be evaluated lazily, it is possible
    /// for exceptions to occur as the sequence is being read.
    /// </remarks>

    [Serializable]
    internal class SequenceEnumerator : IXdmEnumerator
    {

        private JSequenceIterator iter;
		private JItem current;

        internal SequenceEnumerator(JSequenceIterator iter)
        {
            this.iter = iter;
			current = null;
        }

        /// <summary>Return the current item in the sequence</summary>
        /// <returns>An object which will always be an instance of <c>XdmItem</c></returns>

        public object Current
        {
			get {
                if (current == null) return null;
                return XdmValue.Wrap(current); }
        }

        /// <summary>Move to the next item in the sequence</summary>
        /// <returns>true if there are more items in the sequence</returns>

        public bool MoveNext()
        {
			JItem nextItem = iter.next ();
			current = nextItem;
			return (nextItem != null);
        }

        /// <summary>Deprecated. Reset the enumeration so that the next call of
        /// <c>MoveNext</c> will position the enumeration at the
        /// first item in the sequence</summary>
        [System.Obsolete("MethodAccessException no longer used")]
        public void Reset()
        {
            
        }

        /// <summary>
        /// Deprecated. Create another XdmEnumerator over the same sequence of values, positioned at the start
        /// of the sequence, with no change to this XdmEnumerator.
        /// </summary>
        /// <returns>
        /// A new XdmEnumerator over the same sequence of XDM items, positioned at the start of the sequence.
        /// </returns>
        [System.Obsolete("MethodAccessException no longer used")]
        public IXdmEnumerator GetAnother()
        {
            throw new NotImplementedException();
        }
    }

    /// <summary>
    /// Implementation of the (Java) interface SequenceIterator that wraps
    /// a (.NET) IXdmEnumerator
    /// </summary>

    internal class DotNetSequenceIterator : JSequenceIterator
    {
        // TODO: catch errors and throw an XPathException if necessary.

        IXdmEnumerator iter;
        int pos = 0;

        public DotNetSequenceIterator(IXdmEnumerator iter)
        {
            this.iter = iter;
        }

        public JItem next()
        {
            if (pos < 0)
            {
                return null;
            }
            bool more = iter.MoveNext();
            if (more)
            {
                pos++;
                XdmItem i = (XdmItem)iter.Current;
                return (JItem)i.Unwrap();
            }
            else
            {
                pos = -1;
                return null;
            }

        }

        public JItem current()
        {
            if (pos < 0)
            {
                return null;
            }
            XdmItem i = (XdmItem)iter.Current;
            return (JItem)i.Unwrap();
        }

        public int position()
        {
            return pos;
        }

        public void close()
        {
        }

        public JSequenceIterator getAnother()
        {
            return new DotNetSequenceIterator(iter.GetAnother());
        }

        public int getProperties()
        {
            return 0;
        }
    }

    /// <summary>
    /// Enumeration identifying the thirteen XPath axes
    /// </summary>

    public enum XdmAxis
    {
        /// <summary>The XPath ancestor axis</summary> 
        Ancestor,
        /// <summary>The XPath ancestor-or-self axis</summary> 
        AncestorOrSelf,
        /// <summary>The XPath attribute axis</summary> 
        Attribute,
        /// <summary>The XPath child axis</summary> 
        Child,
        /// <summary>The XPath descendant axis</summary> 
        Descendant,
        /// <summary>The XPath descandant-or-self axis</summary> 
        DescendantOrSelf,
        /// <summary>The XPath following axis</summary> 
        Following,
        /// <summary>The XPath following-sibling axis</summary> 
        FollowingSibling,
        /// <summary>The XPath namespace axis</summary> 
        Namespace,
        /// <summary>The XPath parent axis</summary> 
        Parent,
        /// <summary>The XPath preceding axis</summary> 
        Preceding,
        /// <summary>The XPath preceding-sibling axis</summary> 
        PrecedingSibling,
        /// <summary>The XPath self axis</summary> 
        Self
    }

    /// <summary>
    /// An implementation of <code>IXdmEnumerator</code> that iterates over an empty sequence.
    /// </summary>

    public class EmptyEnumerator : IXdmEnumerator
    {

        public static EmptyEnumerator INSTANCE = new EmptyEnumerator();

        private EmptyEnumerator() { }

        public void Reset() { }

        public object Current
        {
            get { throw new InvalidOperationException("Collection is empty."); }
        }

        public bool MoveNext()
        {
            return false;
        }

        public IXdmEnumerator GetAnother()
        {
            return this;
        }
    }


}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2016 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////