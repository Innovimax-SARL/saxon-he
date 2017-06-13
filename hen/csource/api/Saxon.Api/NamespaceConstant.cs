using System;
using System.Collections.Generic;
using System.Text;
using JNamespaceConstant = net.sf.saxon.lib.NamespaceConstant;

namespace Saxon.Api
{

	/// <summary> 
	/// Holds a set of constants representing known namespaces.
	/// </summary>
    public class NamespaceConstant
    {

		/// <summary>
		/// A URI representing the null namespace (actually, an empty string)
		/// </summary>

	public static readonly String NULL = "";

		/// <summary>
		/// Fixed namespace name for XML: "http://www.w3.org/XML/1998/namespace".
		/// </summary>
    public static readonly String XML = "http://www.w3.org/XML/1998/namespace";

		/// <summary>
		/// Fixed namespace name for XSLT: "http://www.w3.org/1999/XSL/Transform"
		/// </summary>
    public static readonly String XSLT = "http://www.w3.org/1999/XSL/Transform";

		/// <summary>
		/// Fixed namespace name for SAXON: "http://saxon.sf.net/"
		/// </summary>
    public static readonly String SAXON = "http://saxon.sf.net/";

		/// <summary>
		/// Fixed namespace name for the export of a Saxon stylesheet package
		/// </summary>
    public static readonly String SAXON_XSLT_EXPORT = "http://ns.saxonica.com/xslt/export";

		/// <summary>
		/// Namespace name for XML Schema: "http://www.w3.org/2001/XMLSchema"
		/// </summary>
    public static readonly String SCHEMA = "http://www.w3.org/2001/XMLSchema";

		/// <summary>
		/// XML-schema-defined namespace for use in instance documents ("xsi")
		/// </summary>
    public static readonly String SCHEMA_INSTANCE = "http://www.w3.org/2001/XMLSchema-instance";

		/// <summary>
		/// Namespace defined in XSD 1.1 for schema versioning
		/// </summary>
    public static readonly String SCHEMA_VERSIONING = "http://www.w3.org/2007/XMLSchema-versioning";

		/// <summary>
		/// Fixed namespace name for SAXON SQL extension: "http://saxon.sf.net/sql"
		/// </summary>
    public static readonly String SQL = "http://saxon.sf.net/sql";

		/// <summary>
		/// Fixed namespace name for EXSLT/Common: "http://exslt.org/common"
		/// </summary>
    public static readonly String EXSLT_COMMON = "http://exslt.org/common";

		/// <summary>
		/// Fixed namespace name for EXSLT/math: "http://exslt.org/math"
		/// </summary>
    public static readonly String EXSLT_MATH = "http://exslt.org/math";

		/// <summary>
		/// Fixed namespace name for EXSLT/sets: "http://exslt.org/sets"
		/// </summary>
    public static readonly String EXSLT_SETS = "http://exslt.org/sets";

		/// <summary>
		/// Fixed namespace name for EXSLT/date: "http://exslt.org/dates-and-times"
		/// </summary>
    public static readonly String EXSLT_DATES_AND_TIMES = "http://exslt.org/dates-and-times";

		/// <summary>
		/// Fixed namespace name for EXSLT/random: "http://exslt.org/random"
		/// </summary>
    public static readonly String EXSLT_RANDOM = "http://exslt.org/random";

		/// <summary>
		/// The standard namespace for functions and operators
		/// </summary>
    public static readonly String FN = "http://www.w3.org/2005/xpath-functions";

		/// <summary>
		/// The standard namespace for XQuery output declarations
		/// </summary>
    public static readonly String OUTPUT = "http://www.w3.org/2010/xslt-xquery-serialization";


		/// <summary>
		/// The standard namespace for system error codes
		/// </summary>
    public static readonly String ERR = "http://www.w3.org/2005/xqt-errors";

		/// <summary>
		/// Predefined XQuery namespace for local functions
		/// </summary>
    public static readonly String LOCAL = "http://www.w3.org/2005/xquery-local-functions";
    
		/// <summary>
		/// Math namespace for the XPath 3.0 math functions
		/// </summary>
    public static readonly String MATH = "http://www.w3.org/2005/xpath-functions/math";

		/// <summary>
		/// Namespace URI for XPath 3.0 functions associated with maps
		/// </summary>
    public readonly static String MAP_FUNCTIONS = "http://www.w3.org/2005/xpath-functions/map";

		/// <summary>
		/// Namespace URI for XPath 3.0 functions associated with arrays
		/// </summary>
    public readonly static String ARRAY_FUNCTIONS = "http://www.w3.org/2005/xpath-functions/array";

		/// <summary>
		/// The XHTML namespace http://www.w3.org/1999/xhtml
		/// </summary>
    public static readonly String XHTML = "http://www.w3.org/1999/xhtml";

		/// <summary>
		/// The SVG namespace
		/// </summary>
    public static readonly String SVG = "http://www.w3.org/2000/svg";

		/// <summary>
		/// The MathML namespace
		/// </summary>
    public static readonly String MATHML = "http://www.w3.org/1998/Math/MathML";

		/// <summary>
		/// The XMLNS namespace (used in DOM)
		/// </summary>
    public static readonly String XMLNS = "http://www.w3.org/2000/xmlns/";

		/// <summary>
		/// The XLink namespace
		/// </summary>
    public static readonly String XLINK = "http://www.w3.org/1999/xlink";

		/// <summary>
		/// The xquery-option namespace for the XQuery 3.0 feature names
		/// </summary>
    public static readonly String XQUERY_OPTIONS = "http://www.w3.org/2011/xquery-options";

		/// <summary>
		/// The xquery namespace for the XQuery 3.0 declare option
		/// </summary>
    public static readonly String XQUERY = "http://www.w3.org/2012/xquery";

		/// <summary>
		/// Namespace for types representing external Java objects
		/// </summary>
    public static readonly String JAVA_TYPE = "http://saxon.sf.net/java-type";

		/// <summary>
		/// Namespace for types representing external .NET objects
		/// </summary>
    public static readonly String DOT_NET_TYPE = "http://saxon.sf.net/clitype";    

		/// <summary>
		/// Namespace for names allocated to anonymous types. This exists so that
		/// a name fingerprint can be allocated for use as a type annotation.
		/// </summary>
    public static readonly String ANONYMOUS = "http://ns.saxonica.com/anonymous-type";

		/// <summary>
		/// Namespace for the Saxon serialization of the schema component model
		/// </summary>
    public static readonly String SCM = "http://ns.saxonica.com/schema-component-model";

		/// <summary>
		/// URI identifying the Saxon object model for use in the JAXP 1.3 XPath API
		/// </summary>
    public static readonly String OBJECT_MODEL_SAXON = "http://saxon.sf.net/jaxp/xpath/om";

		/// <summary>
		/// URI identifying the Unicode codepoint collation
		/// </summary>
    public static readonly String CODEPOINT_COLLATION_URI = "http://www.w3.org/2005/xpath-functions/collation/codepoint";

		/// <summary>
		/// URI identifying the HML5 ascii-case-blind collation
		/// </summary>
    public static readonly String HTML5_CASE_BLIND_COLLATION_URI = "http://www.w3.org/2005/xpath-functions/collation/html5-ascii-case-insensitive";

		/// <summary>
		/// URI for the names of generated global variables
		/// </summary>
        public static readonly String SAXON_GENERATED_GLOBAL = SAXON + "generated-global-variable";

		/// <summary>
		/// URI for the Saxon configuration file
		/// </summary>
    public static readonly String SAXON_CONFIGURATION = "http://saxon.sf.net/ns/configuration";

		/// <summary>
		/// URI for the EXPath zip module
		/// </summary>
    public static readonly String EXPATH_ZIP = "http://expath.org/ns/zip";


		/// <summary>
		/// Determine whether a namespace is a reserved namespace
		/// </summary>
		/// <returns><c>true</c>, if this namespace URI is a reserved namespace, <c>false</c> otherwise.</returns>
		/// <param name="uri">the namespace URI to be tested</param>
        public static bool isReserved(/*@Nullable*/ String uri) {

            return JNamespaceConstant.isReserved(uri);    
        }

		/// <summary>
		/// Determine whether a namespace is a reserved namespace
		/// </summary>
		/// <returns><c>true</c>, if this namespace URI is reserved in XQuery, <c>false</c> otherwise.</returns>
		/// <param name="uri">the namespace URI to be tested</param>
        public static bool isReservedInQuery(String uri)
        {
            return JNamespaceConstant.isReservedInQuery(uri);
        }

		/// <summary>
		/// Determine whether a namespace is a reserved namespace in XQuery 3.0
		/// </summary>
		/// <returns><c>true</c>, if this namespace URI is reserved in XQuery 3.0, <c>false</c> otherwise.</returns>
		/// <param name="uri">the namespace URI to be tested</param>
        public static bool isReservedInQuery30(String uri) {
            return JNamespaceConstant.isReservedInQuery30(uri);
        }

		/// <summary>
		/// Determine whether a namespace is a reserved namespace in XQuery 3.1
		/// </summary>
		/// <returns><c>true</c>, if this namespace URI is reserved in XQuery 3.1, <c>false</c> otherwise.</returns>
		/// <param name="uri">the namespace URI to be tested</param>
        public static bool isReservedInQuery31(String uri)
        {
            return JNamespaceConstant.isReservedInQuery31(uri);
        }

		/// <summary>
		/// Find a similar namespace to one that is a possible mis-spelling
		/// </summary>
		/// <returns>the correct spelling of the namespace</returns>
		/// <param name="candidate">the possibly mis-spelt namespace</param>
        public static String findSimilarNamespace(String candidate) {
            return JNamespaceConstant.findSimilarNamespace(candidate);
        }
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2016 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
