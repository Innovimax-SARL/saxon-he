////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.dotnet;

import cli.Microsoft.Win32.Registry;
import cli.Microsoft.Win32.RegistryKey;
import cli.System.Environment;
import cli.System.Xml.*;
import com.saxonica.ee.bytecode.util.GeneratedClassLoader;
import net.sf.saxon.Configuration;
import net.sf.saxon.Platform;
import net.sf.saxon.Version;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.expr.parser.RetainedStaticContext;
import net.sf.saxon.expr.sort.AtomicMatchKey;
import net.sf.saxon.expr.sort.CodepointCollator;
import net.sf.saxon.expr.sort.SimpleCollation;
import net.sf.saxon.lib.*;
import net.sf.saxon.pull.PullProvider;
import net.sf.saxon.pull.PullSource;
import net.sf.saxon.regex.ARegularExpression;
import net.sf.saxon.regex.JavaRegularExpression;
import net.sf.saxon.regex.RegularExpression;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ExternalObjectType;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.stream.StreamSource;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.spi.CharsetProvider;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Implementation of the Platform interface containing methods appropriate to the .NET platform
 */

public class DotNetPlatform implements Platform {

    /**
     * Create a link to the extended character sets in charsets.jar. This makes these accessible for
     * loading.
     */

    private static CharsetProvider provider = new sun.nio.cs.ext.ExtendedCharsets();

    public DotNetPlatform() {
    }

    /**
     * Perform platform-specific initialization of the configuration
     */

    public void initialize(Configuration config) {
        config.setURIResolver(new DotNetURIResolver(new XmlUrlResolver()));
        //config.setModuleURIResolver(new DotNetStandardModuleURIResolver(new XmlUrlResolver()));
        //config.setCollectionURIResolver(new DotNetCollectionURIResolver());
    }


    /**
     * Return true if this is the Java platform
     */

    public boolean isJava() {
        return false;
    }

    /**
     * Return true if this is the .NET platform
     */

    public boolean isDotNet() {
        return true;
    }

    /**
     * Get the platform version
     */

    public String getPlatformVersion() {
        return ".NET " + Environment.get_Version().ToString() +
                " on " + Environment.get_OSVersion().ToString();
    }

    /**
     * Get a suffix letter to add to the Saxon version number to identify the platform
     */

    public String getPlatformSuffix() {
        return "N";
    }

    /**
     * Get a parser by instantiating the SAXParserFactory
     *
     * @return the parser (XMLReader)
     */

    /**
     * No ICU features
     */

    public boolean hasICUCollator() {
        return false;
    }

    public boolean hasICUNumberer() {
        return false;
    }
    public XMLReader loadParser() {
        XMLReader parser;
        try {
            parser = SAXParserFactory.newInstance("org.apache.xerces.jaxp.SAXParserFactoryImpl", getClass().getClassLoader())
                    .newSAXParser().getXMLReader();
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
     * @param validation    indicates whether schema validation is required, adn in what mode
     * @param dtdValidation true if DTD validation is required
     * @param stripspace    defines the requird whitespace handling
     * @return the PullSource or SAXSource, initialized with a suitable parser, or the original
     *         input Source, if now special handling is required or possible. May also return an AugmentedSource
     *         that wraps one of these.
     */

    public Source getParserSource(PipelineConfiguration pipe, StreamSource input, int validation, boolean dtdValidation,
                                  int stripspace) {
        Configuration config = pipe.getConfiguration();
        boolean preferJaxp = (Boolean) config.getConfigurationProperty(FeatureKeys.PREFER_JAXP_PARSER);
        InputStream is = input.getInputStream();
        if (is != null) {
            if (is instanceof DotNetInputStream && !preferJaxp) {
                XmlReader parser = new XmlTextReader(input.getSystemId(),
                        ((DotNetInputStream) is).getUnderlyingStream());
                ((XmlTextReader) parser).set_WhitespaceHandling(WhitespaceHandling.wrap(WhitespaceHandling.All));
                ((XmlTextReader) parser).set_Normalization(true);
                if (pipe.getURIResolver() instanceof DotNetURIResolver) {
                    ((XmlTextReader) parser).set_XmlResolver(
                            ((DotNetURIResolver) pipe.getURIResolver()).getXmlResolver());
                }

                // Always need a validating parser, because that's the only way to get entity references expanded
                parser = new XmlValidatingReader(parser);
                if (dtdValidation) {
                    ((XmlValidatingReader) parser).set_ValidationType(ValidationType.wrap(ValidationType.DTD));
                } else {
                    ((XmlValidatingReader) parser).set_ValidationType(ValidationType.wrap(ValidationType.None));
                }
                PullProvider provider = new DotNetPullProvider(parser);
                //provider = new PullTracer(provider);
                PullSource ps = new PullSource(provider);
                //System.err.println("Using PullSource(stream)");
                ps.setSystemId(input.getSystemId());
                if (validation == Validation.DEFAULT) {
                    return ps;
                } else {
                    AugmentedSource as = AugmentedSource.makeAugmentedSource(ps);
                    as.setSchemaValidationMode(validation);
                    return as;
                }
            } else {
                return input;
            }
        }
        Reader reader = input.getReader();
        if (reader != null) {
            if (reader instanceof DotNetReader && !preferJaxp) {
                XmlReader parser = new XmlTextReader(input.getSystemId(),
                        ((DotNetReader) reader).getUnderlyingTextReader());
                ((XmlTextReader) parser).set_Normalization(true);
                ((XmlTextReader) parser).set_WhitespaceHandling(WhitespaceHandling.wrap(WhitespaceHandling.All));
                if (pipe.getURIResolver() instanceof DotNetURIResolver) {
                    ((XmlTextReader) parser).set_XmlResolver(
                            ((DotNetURIResolver) pipe.getURIResolver()).getXmlResolver());
                }

                // Always need a validating parser, because that's the only way to get entity references expanded
                parser = new XmlValidatingReader(parser);
                if (dtdValidation) {
                    ((XmlValidatingReader) parser).set_ValidationType(ValidationType.wrap(ValidationType.DTD));
                } else {
                    ((XmlValidatingReader) parser).set_ValidationType(ValidationType.wrap(ValidationType.None));
                }
                PullSource ps = new PullSource(new DotNetPullProvider(parser));
                //System.err.println("Using PullSource(reader)");
                ps.setSystemId(input.getSystemId());
                if (validation == Validation.DEFAULT) {
                    return ps;
                } else {
                    AugmentedSource as = AugmentedSource.makeAugmentedSource(ps);
                    as.setSchemaValidationMode(validation);
                    return as;
                }
            } else {
                return input;
            }
        }
        String uri = input.getSystemId();
        if (uri != null) {
            try {
                Source r = pipe.getURIResolver().resolve(uri, null);
                if (r == null) {
                    return input;
                } else if (r instanceof AugmentedSource) {
                    Source r2 = ((AugmentedSource) r).getContainedSource();
                    if (r2 instanceof StreamSource) {
                        r2 = getParserSource(pipe, (StreamSource) r2, validation, dtdValidation, stripspace);
                        return r2;
                    } else {
                        return r2;
                    }
                } else if (r instanceof StreamSource && r != input) {
                    Source r2 = getParserSource(pipe, (StreamSource) r, validation, dtdValidation, stripspace);
                    AugmentedSource as = AugmentedSource.makeAugmentedSource(r2);
                    as.setPleaseCloseAfterUse(true);
                    return as;
                } else {
                    return r;
                }
            } catch (TransformerException err) {
                return input;
            }
        }
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

    public StringCollator makeCollation(Configuration config, Properties props, String uri) throws XPathException {
        return DotNetCollationFactory.makeCollation(config, uri, props);
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
        return collation instanceof DotNetComparator ||
                collation instanceof CodepointCollator;
    }

    /**
     * Given a collation, get a collation key. The essential property of collation keys
     * is that if two values are equal under the collation, then the collation keys are
     * equal under the equals() method.
     *
     * @throws ClassCastException if the collation is not one that is capable of supplying
     *                            collation keys (this should have been checked in advance)
     */

    public AtomicMatchKey getCollationKey(SimpleCollation namedCollation, String value) {
        DotNetComparator c = (DotNetComparator) namedCollation.getComparator();
        return c.getCollationKey(value);
    }

    /**
     * If available, make a collation using the ICU-J Library
     * @param uri the collation URI (which will always be a UCA collation URI as defined in XSLT 3.0)
     * @param config the Saxon configuration
     * @return the collation, or null if not available
     * @throws XPathException if the URI is malformed in some way
     */

    public StringCollator makeUcaCollator(String uri, Configuration config) throws XPathException {
        return null;
    }

    /**
     * Compile a regular expression
     *
     *
     * @param config
     * @param regex        the regular expression as a string
     * @param flags        the value of the flags attribute
     * @param hostLanguage one of "XSD10", "XSD11", XP20" or "XP30"
     * @param warnings
     * @return the compiled regular expression
     * @throws net.sf.saxon.trans.XPathException
     *          if the regular expression or the flags are invalid
     */
    public RegularExpression compileRegularExpression(Configuration config, CharSequence regex, String flags, String hostLanguage, List<String> warnings) throws XPathException {
        // recognize implementation-defined flags following a semicolon in the flags string
        boolean useJava = false;
        boolean useDotNet = false;
        boolean useSaxon = false;
        int semi = flags.indexOf(';');
        if (semi >= 0) {
            useJava = flags.indexOf('j', semi) >= 0;
            useDotNet = flags.indexOf('n', semi) >= 0;
            useSaxon = flags.indexOf('s', semi) >= 0;
            flags = flags.substring(0, semi);
        }
        if (!useJava && !useDotNet && !useSaxon) {
            String def = config.getDefaultRegexEngine();
            if ("N".equals(def)) {
                useDotNet = true;
            } else if ("J".equals(def)) {
                useJava = true;
            }
        }
        if (useJava) {
            return new JavaRegularExpression(regex, flags);
        } else if (useDotNet) {
            return new DotNetRegularExpression(regex, flags);
        } else {
            return new ARegularExpression(regex, flags, hostLanguage, warnings);
        }
    }

    /**
     * Get a SchemaType representing a wrapped external (.NET) object
     *
     * @param config    the Saxon Configuration
     * @param uri       the namespace URI of the schema type
     * @param localName the local name of the schema type
     * @return the SchemaType object representing this type
     */


    public ExternalObjectType getExternalObjectType(Configuration config, String uri, String localName) {
        if (uri.equals(NamespaceConstant.DOT_NET_TYPE)) {
            return new DotNetExternalObjectType(cli.System.Type.GetType(localName), config);
        } else {
            throw new IllegalArgumentException("Type is not in .NET namespace");
        }
    }

    /**
     * Return the name of the directory in which the software is installed (if available)
     *
     * @param edition the Saxon edition, for example "EE" for enterprise edition
     * @param config  the Saxon configuration
     * @return the name of the directory in which Saxon is installed, if available, or null otherwise
     */

    /*@Nullable*/
    public String getInstallationDirectory(String edition, Configuration config) {
        RegistryKey[] bases = {Registry.LocalMachine, Registry.CurrentUser};
        // See Saxon bug 3426425.
        String[] paths = {"Software\\Saxonica\\Saxon", "Software\\Wow6432Node\\Saxonica\\Saxon"};
        for (RegistryKey base : bases) {
            for (String path : paths) {
                if (base != null) {
                    RegistryKey regKey = base.OpenSubKey(path + edition + "-N\\Settings", false);
                    if (regKey != null) {
                        if (config.isTiming()) {
                            config.getStandardErrorOutput().println("Found registry key at " + regKey.toString());
                        }
                        String installPath = (String) regKey.GetValue("InstallPath");
                        if (config.isTiming()) {
                            config.getStandardErrorOutput().println("Software installation path: " + installPath);
                        }
                        return installPath;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Register all the external object models that are provided as standard
     * with the relevant edition of Saxon for this Configuration
     *
     * @since 9.3
     */

    public void registerAllBuiltInObjectModels(Configuration config) {
        // No action for Saxon on .NET
    }

    /**
     * Set the default XML parser to be loaded by the SAXParserFactory on this platform.
     * Needed because the Apache catalog resolver uses the SAXParserFactory to instantiate
     * a parser, and if not customized this causes a failure on the .NET platform.
     *
     * @since 9.4
     */

    public void setDefaultSAXParserFactory(Configuration config) {
        String editionCode = "he";
//#if PE==true
        editionCode = "pe";
//#endif
//#if EE==true
        editionCode = "ee";
//#endif
        System.setProperty("javax.xml.parsers.SAXParserFactory", "org.apache.xerces.jaxp.SAXParserFactoryImpl, saxon9"+editionCode+", Version="+ Version.getProductVersion()+", Culture=neutral, PublicKeyToken=e1fdd002d5083fe6");
    }

    public boolean JAXPStaticContextCheck(RetainedStaticContext retainedStaticContext, StaticContext sc) {
        return false;
    }

    public ModuleURIResolver makeStandardModuleURIResolver(Configuration config) {
        return new DotNetStandardModuleURIResolver(new XmlUrlResolver());
    }

    //#if EE==true
    /**
     * Return the class loader required to load the bytecode generated classes
     * @param config           The saxon configuration
     * @param thisClass        The class object generated
     * @return the class loader object
     * @since 9.6.0.3
     */
    public ClassLoader makeClassLoader(Configuration config, Class thisClass){
        ClassLoader parentClassLoader = config.getDynamicLoader().getClassLoader();
        if (parentClassLoader == null) {
            parentClassLoader = thisClass.getClassLoader();
        }

        if (parentClassLoader == null) {
            parentClassLoader = Thread.currentThread().getContextClassLoader();
        }
         return new MyClassLoader(parentClassLoader);

    }


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
//#endif


}

