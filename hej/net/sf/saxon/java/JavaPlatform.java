////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.java;

import com.saxonica.ee.bytecode.util.GeneratedClassLoader;
import net.sf.saxon.Configuration;
import net.sf.saxon.Platform;
import net.sf.saxon.dom.DOMEnvelope;
import net.sf.saxon.dom.DOMObjectModel;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.expr.parser.RetainedStaticContext;
import net.sf.saxon.expr.sort.*;
import net.sf.saxon.functions.FunctionLibraryList;
import net.sf.saxon.lib.ModuleURIResolver;
import net.sf.saxon.lib.StandardModuleURIResolver;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.om.NamespaceResolver;
import net.sf.saxon.regex.ARegularExpression;
import net.sf.saxon.regex.JavaRegularExpression;
import net.sf.saxon.regex.RegularExpression;
import net.sf.saxon.resource.StandardCollectionFinder;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ExternalObjectType;
import net.sf.saxon.xpath.JAXPXPathStaticContext;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.stream.StreamSource;
import java.text.CollationKey;
import java.text.Collator;
import java.util.*;

/**
 * Implementation of the Platform class containing methods specific to the Java platform
 * (as distinct from .NET). This is a singleton class with no instance data.
 */
public class JavaPlatform implements Platform {

    /**
     * The constructor is called during the static initialization of the Configuration
     */

    public JavaPlatform() {
    }


    /**
     *  Checks if the supplied static context is an instance of the JAXP static context.
     *  On Java we create namespace information from the JAXP XPath static context.
     *  On the .NET platform we do nothing.
     *  @param retainedStaticContext
     *  @param sc
     *  @return boolean
     *  @since 9.7.0.5
     *
     */

    public boolean JAXPStaticContextCheck(RetainedStaticContext retainedStaticContext, StaticContext sc){
         if(sc instanceof JAXPXPathStaticContext && !(((JAXPXPathStaticContext) sc).getNamespaceContext() instanceof NamespaceResolver)){
             setNamespacesFromJAXP(retainedStaticContext, (JAXPXPathStaticContext) sc);
             return true;
         }
        return false;
    }

    /**
     * Create namespace information from the JAXP XPath static context. This
     * case needs special treatment because the JAXP static context holds namespace information
     * using a NamespaceContext object, which (absurdly) offers no way to iterate over all the
     * contained namespaces.
     *
     * @param retainedStaticContext
     * @param sc JAXP static context
     */

    private void setNamespacesFromJAXP(RetainedStaticContext retainedStaticContext, JAXPXPathStaticContext sc) {
        final NamespaceContext nc = sc.getNamespaceContext();
        retainedStaticContext.setNamespaces(new Map<String, String>() {
            public void clear() { }

            public int size() {
                throw new UnsupportedOperationException();
            }

            public boolean isEmpty() {
                throw new UnsupportedOperationException();
            }

            public boolean containsKey(Object key) {
                return nc.getNamespaceURI(key.toString()) != null;
            }

            public boolean containsValue(Object value) {
                throw new UnsupportedOperationException();
            }

            public String get(Object key) {
                return nc.getNamespaceURI(key.toString());
            }

            public String put(String key, String value) {
                throw new UnsupportedOperationException();
            }

            public String remove(Object key) {
                throw new UnsupportedOperationException();
            }

            public void putAll(Map<? extends String, ? extends String> m) {
                throw new UnsupportedOperationException();
            }

            public Set<String> keySet() {
                throw new UnsupportedOperationException();
            }

            public Collection<String> values() {
                throw new UnsupportedOperationException();
            }

            public Set<Entry<String, String>> entrySet() {
                throw new UnsupportedOperationException();
            }
        });

    }

    /**
     * Perform platform-specific initialization of the configuration
     */

    public void initialize(Configuration config) {
        config.registerExternalObjectModel(DOMEnvelope.getInstance());
        config.registerExternalObjectModel(DOMObjectModel.getInstance());
        config.setCollectionFinder(new StandardCollectionFinder());
    }

    /**
     * Return true if this is the Java platform
     */

    public boolean isJava() {
        return true;
    }

    /**
     * Return true if this is the .NET platform
     */

    public boolean isDotNet() {
        return false;
    }

    /**
     * Get the platform version
     */

    public String getPlatformVersion() {
        return "Java version " + System.getProperty("java.version");
    }

    /**
     * Get a suffix letter to add to the Saxon version number to identify the platform
     */

    public String getPlatformSuffix() {
        return "J";
    }


    /**
     * Get a parser by instantiating the SAXParserFactory
     *
     * @return the parser (XMLReader)
     */

    public XMLReader loadParser() {
        XMLReader parser;
        try {
            parser = SAXParserFactory.newInstance().newSAXParser().getXMLReader();
        } catch (ParserConfigurationException err) {
            throw new TransformerFactoryConfigurationError(err);
        } catch (SAXException err) {
            throw new TransformerFactoryConfigurationError(err);
        }
        return parser;
    }

    /**
     * Convert a StreamSource to either a SAXSource or a PullSource, depending on the native
     * parser of the selected platform
     *
     * @param pipe          the pipeline configuration
     * @param input         the supplied StreamSource
     * @param validation    indicates whether schema validation is required
     * @param dtdValidation indicates whether DTD validation is required
     * @param stripspace    indicates whether whitespace text nodes should be stripped
     * @return the PullSource or SAXSource, initialized with a suitable parser, or the original
     *         input Source, if now special handling is required or possible. This implementation
     *         always returns the original input unchanged.
     */

    public Source getParserSource(PipelineConfiguration pipe, StreamSource input, int validation,
                                  boolean dtdValidation, int stripspace) {
        return input;
    }

    /**
     * Obtain a collation with a given set of properties. The set of properties is extensible
     * and variable across platforms. Common properties with example values include lang=ed-GB,
     * strength=primary, case-order=upper-first, ignore-modifiers=yes, alphanumeric=yes.
     * Properties that are not supported are generally ignored; however some errors, such as
     * failing to load a requested class, are fatal.
     *
     * @param config the configuration object
     * @param props  the desired properties of the collation
     * @param uri    the collation URI
     * @return a collation with these properties
     * @throws XPathException if a fatal error occurs
     */

    /*@Nullable*/
    public StringCollator makeCollation(Configuration config, Properties props, String uri) throws XPathException {
        return JavaCollationFactory.makeCollation(config, uri, props);
    }

    /**
     * Given a collation, determine whether it is capable of returning collation keys.
     * The essential property of collation keys
     * is that if two values are equal under the collation, then the collation keys are
     * equal under the equals() method.
     *
     * @param collation the collation, provided as a Comparator
     * @return true if this collation can supply collation keys
     */

    public boolean canReturnCollationKeys(StringCollator collation) {
        return (collation instanceof CodepointCollator) ||
                ((collation instanceof SimpleCollation) &&
                        (((SimpleCollation) collation).getComparator() instanceof Collator));
    }

    /**
     * Given a collation, get a collation key. The essential property of collation keys
     * is that if two values are equal under the collation, then the collation keys are
     * compare correctly under the equals() method.
     *
     * @throws ClassCastException if the collation is not one that is capable of supplying
     *                            collation keys (this should have been checked in advance)
     */

    public AtomicMatchKey getCollationKey(SimpleCollation namedCollation, String value) {
        CollationKey ck = ((Collator) namedCollation.getComparator()).getCollationKey(value);
        return new CollationMatchKey(ck);
    }

    /**
     * No ICU features
     */

    public boolean hasICUCollator() {
        return false;
    }

    public boolean hasICUNumberer() {
        return false;
    }

    /**
     * If available, make a collation using the ICU-J Library
     *
     * @param uri    the collation URI (which will always be a UCA collation URI as defined in XSLT 3.0)
     * @param config the Saxon configuration
     * @return the collation, or null if not available
     * @throws XPathException if the URI is malformed in some way
     */

    public StringCollator makeUcaCollator(String uri, Configuration config) throws XPathException {
        return new UcaCollatorUsingJava(uri);
    }

    /**
     * Compile a regular expression
     *
     *
     * @param config
     * @param regex        the regular expression as a string
     * @param flags        the value of the flags attribute
     * @param hostLanguage one of "XSD10", "XSD11", "XP20" or "XP30". Also allow combinations, e.g. "XP20/XSD11".
     * @param warnings     if supplied, may capture warnings from the regular expression compiler
     * @return the compiled regular expression
     * @throws net.sf.saxon.trans.XPathException
     *          if the regular expression or the flags are invalid
     */
    public RegularExpression compileRegularExpression(Configuration config, CharSequence regex, String flags, String hostLanguage, List<String> warnings) throws XPathException {
        // recognize "!" as a flag to mean: use native Java regex syntax
        if (flags.contains("!")) {
            // undocumented
            return new JavaRegularExpression(regex, flags.replace("!", ""));
        } else {
            // recognize implementation-defined flags following a semicolon in the flags string
            boolean useJava = false;
            boolean useSaxon = false;
            int semi = flags.indexOf(';');
            if (semi >= 0) {
                useJava = flags.indexOf('j', semi) >= 0;
                useSaxon = flags.indexOf('s', semi) >= 0;
                flags = flags.substring(0, semi);
            }
            if ("J".equals(config.getDefaultRegexEngine()) && !useSaxon) {
                useJava = true;
            }
            if (useJava) {
                return new JavaRegularExpression(regex, flags);
            } else {
                return new ARegularExpression(regex, flags, hostLanguage, warnings);
            }
        }
    }

    /**
     * Add the platform-specific function libraries to a function library list. This version
     * of the method does nothing
     *
     * @param list         the function library list that is to be extended
     * @param config       the Configuration
     * @param hostLanguage the host language, for example Configuration.XQUERY
     */

    public void addFunctionLibraries(FunctionLibraryList list, Configuration config, int hostLanguage) {
        // do nothing
    }

    public ExternalObjectType getExternalObjectType(Configuration config, String uri, String localName) {
        throw new UnsupportedOperationException("getExternalObjectType for Java");
    }

    /**
     * Return the name of the directory in which the software is installed (if available)
     *
     * @param edition
     * @return the name of the directory in which Saxon is installed, if available, or null otherwise
     */

    public String getInstallationDirectory(String edition, Configuration config) {
        try {
            return System.getenv("SAXON_HOME");
        } catch (SecurityException e) {
            return null;
        }
    }

    /**
     * Register all the external object models that are provided as standard
     * with the relevant edition of Saxon for this Configuration
     *
     * @since 9.3
     */

    public void registerAllBuiltInObjectModels(Configuration config) {
        // No action for Saxon-HE
    }

    /**
     * Set the default XML parser to be loaded by the SAXParserFactory on this platform.
     * Needed because the Apache catalog resolver uses the SAXParserFactory to instantiate
     * a parser, and if not customized this causes a failure on the .NET platform.
     *
     * @since 9.4
     */

    public void setDefaultSAXParserFactory(Configuration config) {
        // No action for Saxon on Java
    }

    public ModuleURIResolver makeStandardModuleURIResolver(Configuration config) {
        return new StandardModuleURIResolver(config);
    }



    /**
     * Return the class loader required to load the bytecode generated classes
     * @param config           The saxon configuration
     * @param thisClass        The class object generated
     * @return the class loader object
     * @since 9.6.0.3
     */
    public ClassLoader makeClassLoader(Configuration config, Class thisClass){
        ClassLoader myclassLoader = thisClass.getClassLoader();
        ClassLoader parentClassLoader = config.getDynamicLoader().getClassLoader();

        if (parentClassLoader == null) {
            parentClassLoader = Thread.currentThread().getContextClassLoader();
        }
        if (parentClassLoader == null) {
            parentClassLoader = thisClass.getClassLoader();
        }
//#if BYTECODE==true
        myclassLoader = new MyClassLoader(parentClassLoader);
//#endif

         return myclassLoader;

    }

    //#if BYTECODE==true
    /**
     * MyClassLoader. Implements the GeneratedClassLoader which keeps a map
     * of bytecode generated classes.
     *
     *  @since 9.6.0.3
    */
    public static class MyClassLoader extends ClassLoader implements GeneratedClassLoader {


        Map<String, Class> classMap = new Hashtable<String, Class>();

        public MyClassLoader(ClassLoader parentClassLoader) {

            super(parentClassLoader);

        }


        public void registerClass(String name, byte[] classFile) {

            if (!classMap.containsKey(name)) {
                Class classi = defineClass(name, classFile, 0, classFile.length);
                classMap.put(name, classi);
            }
        }


        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {

            if (classMap.containsKey(name)) {
                return classMap.get(name);

            } else {
                return super.findClass(name);
            }
        }


    }


    /* public static Class loadClassZZ(String className, byte[] b) {
    //override classDefine (as it is protected) and define the class.
    Class clazz = null;
    try {
      ClassLoader loader = ClassLoader.getSystemClassLoader();
      Class cls = Class.forName("java.lang.ClassLoader");
      java.lang.reflect.Method method =
        cls.getDeclaredMethod("defineClass", new Class[] { String.class, byte[].class, int.class, int.class });

      // protected method invocaton
      method.setAccessible(true);
      try {
        Object[] args = new Object[] { className, b, new Integer(0), new Integer(b.length)};

        clazz = (Class) method.invoke(loader, args);

      } finally {
        method.setAccessible(false);
      }
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
    return clazz;
  }  */
//#endif

}

