////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.instruct.*;
import net.sf.saxon.expr.parser.ExplicitLocation;
import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.*;
import net.sf.saxon.trace.LocationKind;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.linked.DocumentImpl;
import net.sf.saxon.tree.linked.LinkedTreeBuilder;
import net.sf.saxon.tree.util.NamespaceIterator;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Untyped;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;


/**
 * This class represents a literal result element in the style sheet
 * (typically an HTML element to be output). <br>
 * It is also used to represent unknown top-level elements, which are ignored.
 */

public class LiteralResultElement extends StyleElement {

    private NodeName resultNodeName;
    private NodeName[] attributeNames;
    private Expression[] attributeValues;
    private int numberOfAttributes;
    private boolean toplevel;
    private List<NamespaceBinding> namespaceCodes = new ArrayList<NamespaceBinding>();
    private StructuredQName[] attributeSets;
    /*@Nullable*/ private SchemaType schemaType = null;
    private int validation = Validation.STRIP;
    private boolean inheritNamespaces = true;

    /**
     * Determine whether this type of element is allowed to contain a sequence constructor
     *
     * @return true: yes, it may contain a sequence constructor
     */

    public boolean mayContainSequenceConstructor() {
        return true;
    }

    /**
     * Specify that this is an instruction
     */

    public boolean isInstruction() {
        return true;
    }

    /**
     * Process the attribute list
     */

    public void prepareAttributes() throws XPathException {

        // Process the values of all attributes. At this stage we deal with attribute
        // values (especially AVTs), but we do not apply namespace aliasing to the
        // attribute names.

        AttributeCollection atts = getAttributeList();
        int num = atts.getLength();

        if (num == 0) {
            numberOfAttributes = 0;
        } else {
            NamePool namePool = getNamePool();
            attributeNames = new NodeName[num];
            attributeValues = new Expression[num];
            numberOfAttributes = 0;

            for (int i = 0; i < num; i++) {

                int fp = atts.getFingerprint(i);
                String attURI = namePool.getURI(fp);

                if (attURI.equals(NamespaceConstant.XSLT)) {

                    if (fp == StandardNames.XSL_USE_ATTRIBUTE_SETS) {
                        // deal with this later
                    } else if (fp == StandardNames.XSL_DEFAULT_COLLATION) {
                        // already dealt with
                    } else if (fp == StandardNames.XSL_DEFAULT_MODE) {
                        // already dealt with
                    } else if (fp == StandardNames.XSL_DEFAULT_VALIDATION) {
                        // already dealt with
                    } else if (fp == StandardNames.XSL_EXTENSION_ELEMENT_PREFIXES) {
                        // already dealt with
                    } else if (fp == StandardNames.XSL_EXCLUDE_RESULT_PREFIXES) {
                        // already dealt with
                    } else if (fp == StandardNames.XSL_EXPAND_TEXT) {
                        // already dealt with
                    } else if (fp == StandardNames.XSL_VERSION) {
                        // already dealt with
                    } else if (fp == StandardNames.XSL_XPATH_DEFAULT_NAMESPACE) {
                        // already dealt with
                    } else if (fp == StandardNames.XSL_TYPE) {
                        // deal with this later
                    } else if (fp == StandardNames.XSL_USE_WHEN) {
                        // already dealt with
                    } else if (fp == StandardNames.XSL_VALIDATION) {
                        // deal with this later
                    } else if (fp == StandardNames.XSL_INHERIT_NAMESPACES) {
                        inheritNamespaces = processBooleanAttribute("xsl:inherit-namespaces", atts.getValue(i));
                    } else if (forwardsCompatibleModeIsEnabled()) {
                        // the attribute is ignored
                    } else {
                        compileError("Unknown XSLT attribute " + atts.getNodeName(i).getDisplayName(), "XTSE0805");
                    }
                } else {
                    attributeNames[numberOfAttributes] = new FingerprintedQName(atts.getPrefix(i), atts.getURI(i), atts.getLocalName(i), fp);
                    Expression exp = makeAttributeValueTemplate(atts.getValue(i), i);
                    attributeValues[numberOfAttributes] = exp;
                    numberOfAttributes++;
                }
            }

            // now shorten the arrays if necessary. This is necessary if there are [xsl:]-prefixed
            // attributes that weren't copied into the arrays.

            if (numberOfAttributes < attributeNames.length) {
                attributeNames = Arrays.copyOf(attributeNames, numberOfAttributes);
                attributeValues = Arrays.copyOf(attributeValues, numberOfAttributes);
            }
        }
        resultNodeName = getNodeName();

    }

    /**
     * Validate that this node is OK
     *
     * @param decl
     */

    public void validate(ComponentDeclaration decl) throws XPathException {

        toplevel = (getParent() instanceof XSLStylesheet);

        resultNodeName = getNodeName();

        String elementURI = getURI();

        if (toplevel) {
            // A top-level element can never be a "real" literal result element,
            // but this class gets used for unknown elements found at the top level

            if (elementURI.isEmpty()) {
                compileError("Top level elements must have a non-null namespace URI", "XTSE0130");
            }
        } else {

            // Build the list of output namespace nodes. Note we no longer optimize this list.
            // See comments in the 9.1 source code for some history of this decision.

            Iterator<NamespaceBinding> inscope = NamespaceIterator.iterateNamespaces(this);
            while (inscope.hasNext()) {
                namespaceCodes.add(inscope.next());
            }

            // Spec bug 5857: if there is no other binding for the default namespace, add an undeclaration
//            String defaultNamespace = getURIForPrefix("", true);
//            if (defaultNamespace.length()==0) {
//                namespaceCodes.add(NamespaceBinding.DEFAULT_UNDECLARATION);
//            }

            // apply any aliases required to create the list of output namespaces

            PrincipalStylesheetModule sheet = getPrincipalStylesheetModule();

            if (sheet.hasNamespaceAliases()) {
                for (int i = 0; i < namespaceCodes.size(); i++) {
                    // System.err.println("Examining namespace " + namespaceCodes[i]);
                    String suri = namespaceCodes.get(i).getURI();
                    NamespaceBinding ncode = sheet.getNamespaceAlias(suri);
                    if (ncode != null && !ncode.getURI().equals(suri)) {
                        // apply the namespace alias. Change in 7.3: use the prefix associated
                        // with the new namespace, not the old prefix.
                        namespaceCodes.set(i, ncode);
                    }
                }

                // determine if there is an alias for the namespace of the element name

                NamespaceBinding elementAlias = sheet.getNamespaceAlias(elementURI);
                if (elementAlias != null && !elementAlias.getURI().equals(elementURI)) {
                    resultNodeName = new FingerprintedQName(elementAlias.getPrefix(),
                            elementAlias.getURI(),
                            getLocalPart());
                }
            }

            // deal with special attributes

            String useAttSets = getAttributeValue(NamespaceConstant.XSLT, "use-attribute-sets");
            if (useAttSets != null) {
                attributeSets = getUsedAttributeSets(useAttSets);
            }

            validation = getDefaultValidation();
            String type = getAttributeValue(NamespaceConstant.XSLT, "type");
            if (type != null) {
                if (!isSchemaAware()) {
                    compileError("The xsl:type attribute is available only with a schema-aware XSLT processor", "XTSE1660");
                }
                schemaType = getSchemaType(type);
                validation = Validation.BY_TYPE;
            }

            String validate = getAttributeValue(NamespaceConstant.XSLT, "validation");
            if (validate != null) {
                validation = validateValidationAttribute(validate);
                if (schemaType != null) {
                    compileError("The attributes xsl:type and xsl:validation are mutually exclusive", "XTSE1505");
                }
            }

            // establish the names to be used for all the output attributes;
            // also type-check the AVT expressions

            if (numberOfAttributes > 0) {

                for (int i = 0; i < numberOfAttributes; i++) {

                    NodeName anameCode = attributeNames[i];
                    NodeName alias = anameCode;
                    String attURI = anameCode.getURI();

                    if (attURI.length() != 0) {    // attribute has a namespace prefix
                        NamespaceBinding newBinding = sheet.getNamespaceAlias(attURI);
                        if (newBinding != null && !newBinding.getURI().equals(attURI)) {
                            alias = new FingerprintedQName(
                                    newBinding.getPrefix(),
                                    newBinding.getURI(),
                                    getAttributeList().getLocalName(i));
                        }
                    }

                    attributeNames[i] = alias;
                    attributeValues[i] = typeCheck(alias.getDisplayName(), attributeValues[i]);
                }
            }

            // remove any namespaces that are on the exclude-result-prefixes list.
            // The namespace is excluded even if it is the namespace of the element or an attribute,
            // though in that case namespace fixup will reinstate it.

            for (int n = namespaceCodes.size() - 1; n >= 0; n--) {
                String uri = namespaceCodes.get(n).getURI();
                if (isExcludedNamespace(uri) && !sheet.isAliasResultNamespace(uri)) {
                    namespaceCodes.remove(n);
                }
            }
        }
    }

    /**
     * Validate the children of this node, recursively. Overridden for top-level
     * data elements.
     *
     * @param decl
     * @param excludeStylesheet
     */

    protected void validateChildren(ComponentDeclaration decl, boolean excludeStylesheet) throws XPathException {
        if (!toplevel) {
            super.validateChildren(decl, excludeStylesheet);
        }
    }

    /**
     * Compile code to process the literal result element at runtime
     */

    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        // top level elements in the stylesheet are ignored
        if (toplevel) {
            return null;
        }

        NamespaceBinding[] bindings = namespaceCodes.toArray(new NamespaceBinding[namespaceCodes.size()]);
        FixedElement inst = new FixedElement(
                resultNodeName,
                bindings,
                inheritNamespaces,
                true, schemaType,
                validation);

        Expression content = compileSequenceConstructor(exec, decl, true);

        if (numberOfAttributes > 0) {
            for (int i = attributeNames.length - 1; i >= 0; i--) {
                FixedAttribute att = new FixedAttribute(
                        attributeNames[i],
                        Validation.STRIP,
                        null);
                att.setRetainedStaticContext(makeRetainedStaticContext());
                att.setSelect(attributeValues[i]);
                att.setLocation(allocateLocation());
                Expression exp = att;
                if (getConfiguration().isCompileWithTracing()) {
                    TraceExpression trace = new TraceExpression(exp);
                    trace.setNamespaceResolver(getNamespaceResolver());
                    trace.setConstructType(LocationKind.LITERAL_RESULT_ATTRIBUTE);
                    trace.setLocation(allocateLocation());
                    trace.setObjectName(attributeNames[i].getStructuredQName());
                    exp = trace;
                }

                if (content == null) {
                    content = exp;
                } else {
                    content = Block.makeBlock(exp, content);
                    content.setLocation(allocateLocation());
                }
            }
        }

        if (attributeSets != null) {
            Expression use = UseAttributeSet.makeUseAttributeSets(attributeSets, this);
            if (content == null) {
                content = use;
            } else {
                content = Block.makeBlock(use, content);
                content.setLocation(allocateLocation());
            }
        }

        if (content == null) {
            content = Literal.makeEmptySequence();
        }
        inst.setContentExpression(content);
        inst.setRetainedStaticContext(makeRetainedStaticContext());
        return inst;
    }

    /**
     * Make a top-level literal result element into a stylesheet. This implements
     * the "Simplified Stylesheet" facility.
     * @param topLevel true if this is the top level module of a stylesheet; false if it is included or imported
     * @return the reconstructed stylesheet with an xsl:stylesheet and xsl:template element added
     * @throws XPathException if anything goes wrong
     */

    public DocumentImpl makeStylesheet(boolean topLevel) throws XPathException {

        // the implementation grafts the LRE node onto a containing xsl:template and
        // xsl:stylesheet

        StyleNodeFactory nodeFactory = getCompilation().getStyleNodeFactory(topLevel);
        if (!isInScopeNamespace(NamespaceConstant.XSLT)) {
            String message;
            if (getLocalPart().equals("stylesheet") || getLocalPart().equals("transform")) {
                message = "Namespace for stylesheet element should be " + NamespaceConstant.XSLT;
            } else {
                message = "The supplied file does not appear to be a stylesheet";
            }
            XPathException err = new XPathException(message);
            err.setLocation(allocateLocation());
            err.setErrorCode("XTSE0150");
            err.setIsStaticError(true);
            //noinspection EmptyCatchBlock
            compileError(err);
            throw err;

        }

        // check there is an xsl:version attribute (it's mandatory), and copy
        // it to the new xsl:stylesheet element

        String version = getAttributeValue(NamespaceConstant.XSLT, "version");
        if (version == null) {
            XPathException err = new XPathException("Simplified stylesheet: xsl:version attribute is missing");
            err.setErrorCode("XTSE0150");
            err.setIsStaticError(true);
            err.setLocation(allocateLocation());
            //noinspection EmptyCatchBlock
            compileError(err);
            throw err;
        }

        try {
            DocumentImpl oldRoot = (DocumentImpl) getRoot();
            LinkedTreeBuilder builder = new LinkedTreeBuilder(getConfiguration().makePipelineConfiguration());
            builder.setNodeFactory(nodeFactory);
            builder.setSystemId(this.getSystemId());

            builder.open();
            builder.startDocument(0);

            int st = StandardNames.XSL_STYLESHEET;
            final Location loc = ExplicitLocation.UNKNOWN_LOCATION;
            builder.startElement(new CodedName(st, "xsl", getNamePool()), Untyped.getInstance(), loc, 0);
            builder.namespace(new NamespaceBinding("xsl", NamespaceConstant.XSLT), 0);
            builder.attribute(new NoNamespaceName("version"), BuiltInAtomicType.UNTYPED_ATOMIC, version, loc, 0);
            builder.startContent();

            int te = StandardNames.XSL_TEMPLATE;
            builder.startElement(new CodedName(te, "xsl", getNamePool()), Untyped.getInstance(), loc, 0);
            builder.attribute(new NoNamespaceName("match"), BuiltInAtomicType.UNTYPED_ATOMIC, "/", loc, 0);
            builder.startContent();

            builder.graftElement(this);

            builder.endElement();
            builder.endElement();
            builder.endDocument();
            builder.close();

            DocumentImpl newRoot = (DocumentImpl) builder.getCurrentRoot();
            newRoot.graftLocationMap(oldRoot);
            return newRoot;
        } catch (XPathException err) {
            //TransformerConfigurationException e = new TransformerConfigurationException(err);
            err.setLocation(allocateLocation());
            throw err;
        }

    }

    /**
     * Get the type of construct. This will be a constant in
     * class {@link LocationKind}. This method is part of the
     * {@link net.sf.saxon.trace.InstructionInfo} interface
     */

    public int getConstructType() {
        return LocationKind.LITERAL_RESULT_ELEMENT;
    }

    /**
     * Get a name identifying the object of the expression, for example a function name, template name,
     * variable name, key name, element name, etc. This is used only where the name is known statically.
     * If there is no name, the value will be -1.
     *
     * @return the name of the literal result element
     */

    public StructuredQName getObjectName() {
        return new StructuredQName(getPrefix(), getURI(), getLocalPart());
    }

    /**
     * Get the value of a particular property of the instruction. This is part of the
     * {@link net.sf.saxon.trace.InstructionInfo} interface for run-time tracing and debugging. The properties
     * available include all the attributes of the source instruction (named by the attribute name):
     * these are all provided as string values.
     *
     * @param name The name of the required property
     * @return The value of the requested property, or null if the property is not available
     */

    public Object getProperty(String name) {
        if (name.equals("name")) {
            return getDisplayName();
        }
        return null;
    }

}
