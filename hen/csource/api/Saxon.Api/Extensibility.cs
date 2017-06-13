using System;
using System.Collections.Generic;
using System.Text;

using JConfiguration = net.sf.saxon.Configuration;
using JStaticContext = net.sf.saxon.expr.StaticContext;
using JXPathException = net.sf.saxon.trans.XPathException;
using JXPathContext = net.sf.saxon.expr.XPathContext;
using JExtensionFunctionDefinition = net.sf.saxon.lib.ExtensionFunctionDefinition;
using JExtensionFunctionCall = net.sf.saxon.lib.ExtensionFunctionCall;
using JStructuredQName = net.sf.saxon.om.StructuredQName;
using JSequenceIterator = net.sf.saxon.om.SequenceIterator;
using JSequenceType = net.sf.saxon.value.SequenceType;
using JSequence = net.sf.saxon.om.Sequence;
using JExpression = net.sf.saxon.expr.Expression;

namespace Saxon.Api
{

    /// <summary>
    /// The class <c>StaticContext</c> provides information about the static context of an expression
    /// </summary>

    public class StaticContext {

        private JStaticContext env;

        internal StaticContext(JStaticContext jsc) {
            env = jsc;
        }


        /// <summary>
        /// The URI of the module where an expression appears, suitable for use in diagnostics
        /// </summary>
        /// 
        public Uri ModuleUri {
            get {
                return new Uri(env.getSystemId());
            }
        }


        /// <summary>
        /// The static base URI of the expression. Often the same as the URI of the containing module,
        /// but not necessarily so, for example in a stylesheet that uses external XML entities or the
        /// xml:base attribute
        /// </summary>
        /// 
        public Uri BaseUri {
            get {
				return new Uri(env.getStaticBaseURI());
            }
        }

        /// <summary>
        /// Resolve an in-scope namespace prefix to obtain the corresponding namespace URI. If the prefix
        /// is a zero-length string, the default namespace for elements and types is returned.
        /// </summary>
        /// <param name="Prefix">The namespace prefix</param>
        /// <returns>The corresponding namespace URI if there is one, or null otherwise</returns>
        /// 
        public String GetNamespaceForPrefix(string Prefix) {
            if (Prefix == "") {
                return env.getDefaultElementNamespace();
            }
            try {
				return env.getNamespaceResolver().getURIForPrefix(Prefix, false);
            } catch (JXPathException) {
                return null;
            }
        }

        /// <summary>
        /// The <c>Processor</c> that was used to create the query or stylesheet from which this extension
        /// function was invoked.
        /// </summary>
        /// <remarks>
        /// <para>This property is useful if the extension function wishes to create new nodes (the <code>Processor</code>
        /// can be used to obtain a <code>DocumentBuilder</code>), or to execute XPath expressions or queries.</para>
        /// <para>There may be circumstances in which the <c>Processor</c> is not available, in which case this method
        /// may return null, or may return a different <c>Processor</c>. This will happen only if low-level interfaces
        /// have been used to cause a <c>Configuration</c> to be shared between several <c>Processor</c> instances,
        /// or between a <c>Processor</c> and other applications.</para>
        /// </remarks>

        public Processor Processor
        {
            get
            {
                JConfiguration config = env.getConfiguration();
                Object p = config.getProcessor();
                if (p is Processor)
                {
                    return (Processor)p;
                }
                else
                {
                    return null;
                }

            }
        }

        /// <summary>
        /// The underlying object in the Saxon implementation, an instance of class
        /// <code>net.sf.saxon.expr.StaticContext</code>
        /// </summary>
        /// <remarks>
        /// <para>This property provides access to internal methods in the Saxon engine that are
        /// not specifically exposed in the .NET API. In general these methods should be
        /// considered to be less stable than the classes in the Saxon.Api namespace.</para> 
        /// <para>The internal methods follow
        /// Java naming conventions rather than .NET conventions.</para>
        /// <para>Information about the returned object (and the objects it provides access to)
        /// is included in the Saxon JavaDoc docmentation, available 
        /// <link href="http://www.saxonica.com/documentation/javadoc/index.html">online</link>.
        /// </para>
        /// </remarks>

        public JStaticContext Implementation
        {
            get { return env; }
        }
    }

    /// <summary>
    /// The class <c>DynamicContext</c> provides information about the dynamic context of an expression
    /// </summary>
    /// 
    public class DynamicContext {

        internal JXPathContext context;

        internal DynamicContext(JXPathContext context) {
            this.context = context;
        }

        /// <summary>
        /// The context item. May be null if no context item is defined
        /// </summary>
        /// 
        public XdmItem ContextItem {
            get {
                return (XdmItem)XdmItem.Wrap(context.getContextItem());
            }
        }

        /// <summary>
        /// The context position (equivalent to the XPath position() function).
        /// </summary>
        /// <remarks>Calling this method throws an exception if the context item is undefined.</remarks>
        /// 
        public int ContextPosition {
            get {
                return context.getCurrentIterator().position();
            }
        }

        /// <summary>
        /// The context size (equivalent to the XPath last() function).
        /// </summary>
        /// <remarks>Calling this method throws an exception if the context item is undefined.</remarks>
        /// 
        public int ContextSize {
            get {
                return context.getLast();
            }
        }

        /// <summary>
        /// The underlying object in the Saxon implementation, an instance of class
        /// <code>net.sf.saxon.expr.XPathContext</code>
        /// </summary>
        /// <remarks>
        /// <para>This property provides access to internal methods in the Saxon engine that are
        /// not specifically exposed in the .NET API. In general these methods should be
        /// considered to be less stable than the classes in the Saxon.Api namespace.</para> 
        /// <para>The internal methods follow
        /// Java naming conventions rather than .NET conventions.</para>
        /// <para>Information about the returned object (and the objects it provides access to)
        /// is included in the Saxon JavaDoc docmentation, available 
        /// <link href="http://www.saxonica.com/documentation/javadoc/index.html">online</link>.
        /// </para>
        /// </remarks>

        public JXPathContext Implementation
        {
            get { return context; }
        }        

    }

    /// <summary>
    /// <para>Abstract superclass for user-written extension functions. An extension function may be implemented as a subclass
    /// of this class, with appropriate implementations of the defined methods.</para>
    /// <para>More precisely, a subclass of <c>ExtensionFunctionDefinition</c> identifies a family of extension functions
    /// with the same (namespace-qualified) name but potentially having different arity (number of arguments).</para>
    /// </summary>
    /// <remarks>
    /// <para>A user-defined extension function is typically implemented using a pair of classes: a class that extends 
    /// <code>ExtensionFunctionDefinition</code>, whose purpose is to define the properties of the extension function
    /// (in particular, its signature -- the types of its arguments and result); and a class that extends
    /// <code>ExtensionFunctionCall</code>, whose purpose is to perform the actual evaluation.</para> 
    /// <para>The <code>ExtensionFunctionDefinition</code> is immutable and will normally have a singleton instance
    /// for each subclass; this singleton instance is registered with the <code>Processor</code> to associate the
    /// name of the extension function with its definition.</para>
    /// <para>The <code>ExtensionFunctionCall</code> has one instance for each call on the extension function appearing
    /// in the source code of a stylesheet or query; this instance is created when Saxon calls the method <code>MakeFunctionCall</code>
    /// provided by the <code>ExtensionFunctionDefinition</code> object. The instance of <code>ExtensionFunctionCall</code>
    /// holds information about the static context of the function call, and its <code>Call</code> method is called
    /// (by Saxon) to evaluate the extension function at run-time.</para>
    /// </remarks>

    public abstract class ExtensionFunctionDefinition
    {
        /// <summary>
        /// Read-only property returning the name of the extension function, as a QName.
        /// </summary>
        /// <remarks>
        /// A getter for this property must be implemented in every subclass.
        /// </remarks>

        public abstract QName FunctionName {get;}

        /// <summary>
        /// Read-only property giving the minimum number of arguments in a call to this extension function.
        /// </summary>
        /// <remarks>
        /// A getter for this property must be implemented in every subclass.
        /// </remarks>

        public abstract int MinimumNumberOfArguments {get;}

        /// <summary>
        /// Read-only property giving the maximum number of arguments in a call to this extension function.
        /// </summary>
        /// <remarks>
        /// A getter for this property must be implemented in every subclass.
        /// </remarks>
        
        public abstract int MaximumNumberOfArguments {get;}

        /// <summary>
        /// Read-only property giving the required types of the arguments to this extension function. 
        /// If the number of items in the array is less than the maximum number of arguments, 
        /// then the last entry in the returned ArgumentTypes is assumed to apply to all the rest; 
        /// if the returned array is empty, then all arguments are assumed to be of type <c>item()*</c>
        /// </summary>
        /// <remarks>
        /// A getter for this property must be implemented in every subclass.
        /// </remarks>

        public abstract XdmSequenceType[] ArgumentTypes {get;}

        /// <summary>
        /// Method returning the declared type of the return value from the function. The type of the return
        /// value may be known more precisely if the types of the arguments are known (for example, some functions
        /// return a value that is the same type as the first argument. The method is therefore called supplying the
        /// static types of the actual arguments present in the call.
        /// </summary>
        /// <remarks>
        /// This method must be implemented in every subclass.
        /// </remarks>
        /// <param name="ArgumentTypes">
        /// The static types of the arguments present in the function call
        /// </param>
        /// <returns>
        /// An <c>XdmSequenceType</c> representing the declared return type of the extension function
        /// </returns>

        public abstract XdmSequenceType ResultType(XdmSequenceType[] ArgumentTypes);

        /// <summary>
        /// This property may return true for a subclass if it guarantees that the returned result of the function
        /// will always be of the declared return type: setting this to true by-passes the run-time checking of the type
        /// of the value, together with code that would otherwise perform atomization, numeric type promotion, and similar
        /// conversions. If the value is set to true and the value is not of the correct type, the effect is unpredictable
        /// and probably disastrous.
        /// </summary>
        /// <remarks>
        /// The default value of this property is <c>false</c>. A getter for this property may be implemented in a subclass
        /// to return a different value.
        /// </remarks>

        public virtual Boolean TrustResultType {
            get{return false;}
        }

        /// <summary>
        /// This property must return true for a subclass if the evaluation of the function makes use of the context
        /// item, position, or size from the dynamic context. It should also return true (despite the property name)
        /// if the function makes use of parts of the static context that vary from one part of the query or stylesheet
        /// to another. Setting the property to true inhibits certain Saxon optimizations, such as extracting the call
        /// from a loop, or moving it into a global variable.
        /// </summary>
        /// <remarks>
        /// The default value of this property is <c>false</c>. A getter for this property may be implemented in a subclass
        /// to return a different value.
        /// </remarks>

        public virtual Boolean DependsOnFocus {
            get{return false;}
        }

        /// <summary>
        /// This property should return true for a subclass if the evaluation of the function has side-effects.
        /// Saxon never guarantees the result of calling functions with side-effects, but if this property is set,
        /// then certain aggressive optimizations will be avoided, making it more likely that the function behaves
        /// as expected.
        /// </summary>
        /// <remarks>
        /// The default value of this property is <c>false</c>. A getter for this property may be implemented in a subclass
        /// to return a different value.
        /// </remarks>

        public virtual Boolean HasSideEffects {
            get{return false;}
        }

        /// <summary>
        /// Factory method to create an <c>ExtensionFunctionCall</c> object, representing a specific function call in the XSLT or XQuery
        /// source code. Saxon will call this method once it has identified that a specific call relates to this extension
        /// function.
        /// </summary>
        /// <remarks>
        /// This method must be implemented in every subclass. The implementation should normally instantiate the relevant subclass
        /// of <code>ExtensionFunctionCall</code>, and return the new instance.
        /// </remarks>
        /// <returns>
        /// An instance of the appropriate implementation of <code>ExtensionFunctionCall</code>
        /// </returns>

        public abstract ExtensionFunctionCall MakeFunctionCall();
    }

    /// <summary>
    /// <para>An instance of this class will be created by the compiler for each function call to this extension function
    /// that is found in the source code. The class is always instantiated by calling the method <c>MakeFunctionCall()</c>
    /// of the corresponding <c>ExtensionFunctionDefinition</c>. 
    /// The implementation may therefore retain information about the static context of the
    /// call. Once compiled, however, the instance object must be immutable.</para>
    /// </summary>

    public abstract class ExtensionFunctionCall {

        /// <summary>
        /// Method called by the compiler (at compile time) to provide information about the static context of the
        /// function call. The implementation may retain this information for use at run-time, if the result of the
        /// function depends on information in the static context.
        /// </summary>
        /// <remarks>
        /// For efficiency, the implementation should only retain copies of the information that it actually needs. It
        /// is not a good idea to hold a reference to the static context itself, since that can result in a great deal of
        /// compile-time information being locked into memory during run-time execution.
        /// </remarks>
        /// <param name="context">Information about the static context in which the function is called</param>

        public virtual void SupplyStaticContext(StaticContext context)
        {
            // default: no action
        }

        /// <summary>
        /// A subclass must implement this method if it retains any local data at the instance level. On some occasions
        /// (for example, when XSLT or XQuery code is inlined), Saxon will make a copy of an <c>ExtensionFunction</c> object.
        /// It will then call this method on the old object, supplying the new object as the value of the argument, and the
        /// method must copy all local data items from the old object to the new.
        /// </summary>
        /// <param name="destination">The new extension function object. This will always be an instance of the same
        /// class as the existing object.</param>

        public virtual void CopyLocalData(ExtensionFunctionCall destination) { }

        /// <summary>
        /// Method called at run time to evaluate the function.
        /// </summary>
        /// <param name="arguments">The values of the arguments to the function, supplied as iterators over XPath
        /// sequence values.</param>
        /// <param name="context">The dynamic context for evaluation of the function. This provides access
        /// to the context item, position, and size, and if required to internal data maintained by the Saxon
        /// engine.</param>
        /// <returns>An iterator over a sequence, representing the result of the extension function.
        /// Note that Saxon does not guarantee to read this sequence to completion, so calls on the iterator
        /// must have no side-effects. In rare circumstances (for example, when <code>last()</code> is
        /// used) Saxon may clone the returned iterator by calling its <c>GetAnother()</c> method, 
        /// allowing the function results to be read more than once.</returns>

        public abstract IXdmEnumerator Call(IXdmEnumerator[] arguments, DynamicContext context);
    }

    internal class WrappedExtensionFunctionDefinition : JExtensionFunctionDefinition
    {
        ExtensionFunctionDefinition definition;

        public WrappedExtensionFunctionDefinition(ExtensionFunctionDefinition definition)
        {
            this.definition = definition;
        }

        public override JStructuredQName getFunctionQName()
        {
            return definition.FunctionName.ToStructuredQName();
        }

        public override int getMinimumNumberOfArguments()
        {
            return definition.MinimumNumberOfArguments;
        }

        public override int getMaximumNumberOfArguments()
        {
            return definition.MaximumNumberOfArguments;
        }

        public override JSequenceType[] getArgumentTypes()
        {
            XdmSequenceType[] dt = definition.ArgumentTypes;
            JSequenceType[] jt = new JSequenceType[dt.Length];
            for (int i = 0; i < dt.Length; i++)
            {
                jt[i] = dt[i].ToSequenceType();
            }
            return jt;
        }

        public override JSequenceType getResultType(JSequenceType[] argumentTypes)
        {
            XdmSequenceType[] dt = new XdmSequenceType[argumentTypes.Length];
            for (int i = 0; i < dt.Length; i++)
            {
                dt[i] = XdmSequenceType.FromSequenceType(argumentTypes[i]);
            }

            XdmSequenceType rt = definition.ResultType(dt);
            return rt.ToSequenceType();
        }

        public override Boolean trustResultType()
        {
            return definition.TrustResultType;
        }

        public override Boolean dependsOnFocus()
        {
            return definition.DependsOnFocus;
        }

        public override Boolean hasSideEffects()
        {
            return definition.HasSideEffects;
        }

        public override JExtensionFunctionCall makeCallExpression()
        {
            return new WrappedExtensionFunctionCall(definition.MakeFunctionCall());
        }

    }

    internal class WrappedExtensionFunctionCall : JExtensionFunctionCall {

        ExtensionFunctionCall functionCall;

        public WrappedExtensionFunctionCall(ExtensionFunctionCall call)
        {
            this.functionCall = call;
        }
        
        public override void supplyStaticContext(JStaticContext context, int locationId, JExpression[] arguments)
        {
            StaticContext sc = new StaticContext(context);
            functionCall.SupplyStaticContext(sc);
        }

        public override void copyLocalData(JExtensionFunctionCall destination)
        {
            functionCall.CopyLocalData(((WrappedExtensionFunctionCall)destination).functionCall);
        }

        public override JSequence call(JXPathContext context, JSequence [] argument)
        {
            SequenceEnumerator[] na = new SequenceEnumerator[argument.Length];
            for (int i = 0; i < na.Length; i++)
            {
                na[i] = new SequenceEnumerator(argument[i].iterate());
            }
            DynamicContext dc = new DynamicContext(context);
            IXdmEnumerator result = functionCall.Call(na, dc);
            return new net.sf.saxon.om.LazySequence(new DotNetSequenceIterator(result));
        }
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2016 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
