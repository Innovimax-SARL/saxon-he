////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.Configuration;
import net.sf.saxon.Controller;
import net.sf.saxon.event.*;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.AnyNodeTest;
import net.sf.saxon.pattern.ContentTypeTest;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.tree.wrapper.VirtualCopy;
import net.sf.saxon.tree.wrapper.VirtualUntypedCopy;
import net.sf.saxon.type.*;
import net.sf.saxon.value.SequenceType;

import java.net.URI;
import java.net.URISyntaxException;


/**
 * An xsl:copy-of element in the stylesheet.
 */

public class CopyOf extends Instruction implements ValidatingInstruction {

    private Operand selectOp;
    private boolean copyNamespaces;
    private boolean copyAccumulators;
    private int validation;
    private SchemaType schemaType;
    private boolean requireDocumentOrElement = false;
    private boolean rejectDuplicateAttributes;
    //private boolean readOnce = false;
    private boolean validating;
    private boolean copyLineNumbers = true;
    private boolean copyForUpdate = false;
    private boolean isSchemaAware = true;

    /**
     * Create an xsl:copy-of instruction (also used in XQuery for implicit copying)
     *
     * @param select                    expression that selects the nodes to be copied
     * @param copyNamespaces            true if namespaces are to be copied
     * @param validation                validation mode for the result tree
     * @param schemaType                schema type for validating the result tree
     * @param rejectDuplicateAttributes true if duplicate attributes are to be rejected (XQuery). False
     *                                  if duplicates are handled by discarding all but the first (XSLT).
     */

    public CopyOf(Expression select,
                  boolean copyNamespaces,
                  int validation,
                  SchemaType schemaType,
                  boolean rejectDuplicateAttributes) {
        selectOp = new Operand(this, select, OperandRole.SINGLE_ATOMIC);
        this.copyNamespaces = copyNamespaces;
        this.validation = validation;
        this.schemaType = schemaType;
        validating = schemaType != null || validation != Validation.PRESERVE;
        this.rejectDuplicateAttributes = rejectDuplicateAttributes;
    }

    public Expression getSelect() {
        return selectOp.getChildExpression();
    }

    /**
     * Set the select expression
     *
     * @param select the new select expression
     */

    public void setSelect(Expression select) {
        selectOp.setChildExpression(select);
    }

    @Override
    public Iterable<Operand> operands() {
        return selectOp;
    }

    /**
     * Get the validation mode
     *
     * @return the validation mode
     */

    public int getValidationAction() {
        return validation;
    }

    /**
     * Test if the instruction is doing validation
     *
     * @return true if it is
     */

    public boolean isValidating() {
        return validating;
    }

    /**
     * Get the schema type to be used for validation
     *
     * @return the schema type, or null if not validating against a type
     */

    public SchemaType getSchemaType() {
        return schemaType;
    }

    /**
     * Set the "is schema aware" property
     *
     * @param schemaAware true if schema awareness is enabled
     */

    public void setSchemaAware(boolean schemaAware) {
        this.isSchemaAware = schemaAware;
    }

    /**
     * Set whether line numbers are to be copied from the source to the result.
     * Default is false.
     *
     * @param copy true if line numbers are to be copied
     */

    public void setCopyLineNumbers(boolean copy) {
        copyLineNumbers = copy;
    }

    /**
     * Determine whether this instruction creates new nodes.
     * The result depends on the type of the select expression.
     */

    public final boolean mayCreateNewNodes() {
        return !getSelect().getItemType().isPlainType();
    }

    /**
     * Get the name of this instruction, for diagnostics and tracing
     */

    public int getInstructionNameCode() {
        return StandardNames.XSL_COPY_OF;
    }

    /**
     * For XQuery, the operand (select) must be a single element or document node.
     *
     * @param requireDocumentOrElement true if the argument must be a single element or document node
     */
    public void setRequireDocumentOrElement(boolean requireDocumentOrElement) {
        this.requireDocumentOrElement = requireDocumentOrElement;
    }

    /**
     * Test whether this expression requires a document or element node
     *
     * @return true if this expression requires the value of the argument to be a document or element node,
     *         false if there is no such requirement
     */

    public boolean isDocumentOrElementRequired() {
        return requireDocumentOrElement;
    }

    /**
     * Set whether this instruction is creating a copy for the purpose of updating (XQuery transform expression)
     *
     * @param forUpdate true if this copy is being created to support an update
     */

    public void setCopyForUpdate(boolean forUpdate) {
        copyForUpdate = forUpdate;
    }

    /**
     * Ask whether this instruction is creating a copy for the purpose of updating (XQuery transform expression)
     *
     * @return true if this copy is being created to support an update
     */

    public boolean isCopyForUpdate() {
        return copyForUpdate;
    }

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is provided. This implementation provides both iterate() and
     * process() methods natively.
     */

    public int getImplementationMethod() {
        return ITERATE_METHOD | PROCESS_METHOD | WATCH_METHOD;
    }

    /**
     * Ask whether namespaces are to be copied or not
     *
     * @return true if namespaces are to be copied (the default)
     */

    public boolean isCopyNamespaces() {
        return copyNamespaces;
    }

    /**
     * Say whether accumulator values should be copied from the source document
     * @param copy true if values should be copied
     */

    public void setCopyAccumulators(boolean copy) {
        copyAccumulators = copy;
    }

    /**
     * Ask whether accumulator values should be copied from the source document
     * @return true if values should be copied
     */

    public boolean isCopyAccumulators() {
        return copyAccumulators;
    }


    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings information about variables whose bindings need to be replaced
     */

    /*@NotNull*/
    public Expression copy(RebindingMap rebindings) {
        CopyOf c = new CopyOf(getSelect().copy(rebindings), copyNamespaces, validation, schemaType, rejectDuplicateAttributes);
        c.setCopyForUpdate(copyForUpdate);
        c.setCopyLineNumbers(copyLineNumbers);
        //c.setReadOnce(readOnce);
        c.isSchemaAware = isSchemaAware;
        c.setCopyAccumulators(copyAccumulators);
        return c;
    }

    /*@NotNull*/
    public ItemType getItemType() {
        ItemType in = getSelect().getItemType();
        if (!isSchemaAware) {
            return in;
        }
        Configuration config = getConfiguration();
        if (schemaType != null) {
            TypeHierarchy th = config.getTypeHierarchy();
            int e = th.relationship(in, NodeKindTest.ELEMENT);
            if (e == TypeHierarchy.SAME_TYPE || e == TypeHierarchy.SUBSUMED_BY) {
                return new ContentTypeTest(Type.ELEMENT, schemaType, config, false);
            }
            int a = th.relationship(in, NodeKindTest.ATTRIBUTE);
            if (a == TypeHierarchy.SAME_TYPE || a == TypeHierarchy.SUBSUMED_BY) {
                return new ContentTypeTest(Type.ATTRIBUTE, schemaType, config, false);
            }
        } else {
            switch (validation) {
                case Validation.PRESERVE:
                    return in;
                case Validation.STRIP: {
                    TypeHierarchy th = config.getTypeHierarchy();
                    int e = th.relationship(in, NodeKindTest.ELEMENT);
                    if (e == TypeHierarchy.SAME_TYPE || e == TypeHierarchy.SUBSUMED_BY) {
                        return new ContentTypeTest(Type.ELEMENT, Untyped.getInstance(), config, false);
                    }
                    int a = th.relationship(in, NodeKindTest.ATTRIBUTE);
                    if (a == TypeHierarchy.SAME_TYPE || a == TypeHierarchy.SUBSUMED_BY) {
                        return new ContentTypeTest(Type.ATTRIBUTE, BuiltInAtomicType.UNTYPED_ATOMIC, config, false);
                    }
                    if (e != TypeHierarchy.DISJOINT || a != TypeHierarchy.DISJOINT) {
                        // it might be an element or attribute
                        return in instanceof NodeTest ? AnyNodeTest.getInstance() : AnyItemType.getInstance();
                    } else {
                        // it can't be an element or attribute, so stripping type annotations can't affect it
                        return in;
                    }
                }
                case Validation.STRICT:
                case Validation.LAX:
                    if (in instanceof NodeTest) {
                        TypeHierarchy th = config.getTypeHierarchy();
                        int fp = ((NodeTest) in).getFingerprint();
                        if (fp != -1) {
                            int e = th.relationship(in, NodeKindTest.ELEMENT);
                            if (e == TypeHierarchy.SAME_TYPE || e == TypeHierarchy.SUBSUMED_BY) {
                                SchemaDeclaration elem = config.getElementDeclaration(fp);
                                if (elem != null) {
                                    try {
                                        return new ContentTypeTest(Type.ELEMENT, elem.getType(), config, false);
                                    } catch (MissingComponentException e1) {
                                        return new ContentTypeTest(Type.ELEMENT, AnyType.getInstance(), config, false);
                                    }
                                } else {
                                    // Although there is no element declaration now, there might be one at run-time
                                    return new ContentTypeTest(Type.ELEMENT, AnyType.getInstance(), config, false);
                                }
                            }
                            int a = th.relationship(in, NodeKindTest.ATTRIBUTE);
                            if (a == TypeHierarchy.SAME_TYPE || a == TypeHierarchy.SUBSUMED_BY) {
                                SchemaDeclaration attr = config.getElementDeclaration(fp);
                                if (attr != null) {
                                    try {
                                        return new ContentTypeTest(Type.ATTRIBUTE, attr.getType(), config, false);
                                    } catch (MissingComponentException e1) {
                                        return new ContentTypeTest(Type.ATTRIBUTE, AnySimpleType.getInstance(), config, false);
                                    }
                                } else {
                                    // Although there is no attribute declaration now, there might be one at run-time
                                    return new ContentTypeTest(Type.ATTRIBUTE, AnySimpleType.getInstance(), config, false);
                                }
                            }
                        } else {
                            int e = th.relationship(in, NodeKindTest.ELEMENT);
                            if (e == TypeHierarchy.SAME_TYPE || e == TypeHierarchy.SUBSUMED_BY) {
                                return NodeKindTest.ELEMENT;
                            }
                            int a = th.relationship(in, NodeKindTest.ATTRIBUTE);
                            if (a == TypeHierarchy.SAME_TYPE || a == TypeHierarchy.SUBSUMED_BY) {
                                return NodeKindTest.ATTRIBUTE;
                            }
                        }
                        return AnyNodeTest.getInstance();
                    } else if (in instanceof AtomicType) {
                        return in;
                    } else {
                        return AnyItemType.getInstance();
                    }
            }
        }
        return getSelect().getItemType();
    }

    public int getCardinality() {
        return getSelect().getCardinality();
    }

    public int getDependencies() {
        return getSelect().getDependencies();
    }

    /*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        typeCheckChildren(visitor, contextInfo);
        if (isDocumentOrElementRequired()) {
            // this implies the expression is actually an XQuery validate{} expression, hence the error messages
            RoleDiagnostic role = new RoleDiagnostic(RoleDiagnostic.TYPE_OP, "validate", 0);
            role.setErrorCode("XQTY0030");
            Configuration config = visitor.getConfiguration();
            setSelect(config.getTypeChecker(false).staticTypeCheck(
                    getSelect(), SequenceType.SINGLE_NODE, role, visitor));

            TypeHierarchy th = config.getTypeHierarchy();
            ItemType t = getSelect().getItemType();
            if (th.isSubType(t, NodeKindTest.ATTRIBUTE)) {
                throw new XPathException("validate{} expression cannot be applied to an attribute", "XQTY0030");
            }
            if (th.isSubType(t, NodeKindTest.TEXT)) {
                throw new XPathException("validate{} expression cannot be applied to a text node", "XQTY0030");
            }
            if (th.isSubType(t, NodeKindTest.COMMENT)) {
                throw new XPathException("validate{} expression cannot be applied to a comment node", "XQTY0030");
            }
            if (th.isSubType(t, NodeKindTest.PROCESSING_INSTRUCTION)) {
                throw new XPathException("validate{} expression cannot be applied to a processing instruction node", "XQTY0030");
            }
            if (th.isSubType(t, NodeKindTest.NAMESPACE)) {
                throw new XPathException("validate{} expression cannot be applied to a namespace node", "XQTY0030");
            }
        }
        return this;
    }

    /*@NotNull*/
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
//        if (readOnce) {
//            Expression optcopy;
//            if (!(getSelect() instanceof DocumentSorter)) {
//                optcopy = getConfiguration().obtainOptimizer().optimizeCopy(visitor, contextItemType, getSelect());
//                if (optcopy != null) {
//                    optcopy = optcopy.typeCheck(visitor, contextItemType);
//                    ExpressionTool.copyLocationInfo(this, optcopy);
//                    return optcopy;
//                }
//            }
//            selectOp.optimize(visitor, contextItemType);
//            optcopy = getConfiguration().obtainOptimizer().optimizeCopy(visitor, contextItemType, getSelect());
//            if (optcopy != null) {
//                ExpressionTool.copyLocationInfo(this, optcopy);
//                return optcopy;
//            }
//        } else {
            selectOp.optimize(visitor, contextItemType);
//        }

        if (Literal.isEmptySequence(getSelect())) {
            return getSelect();
        }
        adoptChildExpression(getSelect());
        if (getSelect().getItemType().isPlainType()) {
            return getSelect();
        }
        return this;
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("copyOf", this);
        if (validation != Validation.SKIP) {
            out.emitAttribute("validation", Validation.toString(validation));
        }
        if (schemaType != null) {
            out.emitAttribute("type", schemaType.getStructuredQName());
        }

        FastStringBuffer fsb = new FastStringBuffer(FastStringBuffer.C16);
        if (requireDocumentOrElement) {
            fsb.append('p');
        }
        if (rejectDuplicateAttributes) {
            fsb.append('a');
        }
//        if (readOnce) {
//            fsb.append('o');
//        }
        if (validating) {
            fsb.append('v');
        }
        if (copyLineNumbers) {
            fsb.append('l');
        }
        if (copyForUpdate) {
            fsb.append('u');
        }
        if (isSchemaAware) {
            fsb.append('s');
        }
        if (copyNamespaces) {
            fsb.append('c');
        }
        if (copyAccumulators) {
            fsb.append('m');
        }
        if (!fsb.isEmpty()) {
            out.emitAttribute("flags", fsb.toString());
        }
        getSelect().export(out);
        out.endElement();
    }

    /**
     * Get the (partial) name of a class that supports streaming of this kind of expression
     *
     * @return the partial name of a class that can be instantiated to provide streaming support in Saxon-EE,
     * or null if there is no such class
     */
    @Override
    public String getStreamerName() {
        return "CopyOf";
    }

    /**
     * Add a representation of this expression to a PathMap. The PathMap captures a map of the nodes visited
     * by an expression in a source tree.
     * <p/>
     * <p>The default implementation of this method assumes that an expression does no navigation other than
     * the navigation done by evaluating its subexpressions, and that the subexpressions are evaluated in the
     * same context as the containing expression. The method must be overridden for any expression
     * where these assumptions do not hold. For example, implementations exist for AxisExpression, ParentExpression,
     * and RootExpression (because they perform navigation), and for the doc(), document(), and collection()
     * functions because they create a new navigation root. Implementations also exist for PathExpression and
     * FilterExpression because they have subexpressions that are evaluated in a different context from the
     * calling expression.</p>
     *
     * @param pathMap        the PathMap to which the expression should be added
     * @param pathMapNodeSet the PathMapNodeSet to which the paths embodied in this expression should be added
     * @return the pathMapNodeSet representing the points in the source document that are both reachable by this
     *         expression, and that represent possible results of this expression. For an expression that does
     *         navigation, it represents the end of the arc in the path map that describes the navigation route. For other
     *         expressions, it is the same as the input pathMapNode.
     */

    public PathMap.PathMapNodeSet addToPathMap(PathMap pathMap, PathMap.PathMapNodeSet pathMapNodeSet) {
        PathMap.PathMapNodeSet result = super.addToPathMap(pathMap, pathMapNodeSet);
        result.setReturnable(false);
        TypeHierarchy th = getConfiguration().getTypeHierarchy();
        ItemType type = getItemType();
        if (th.relationship(type, NodeKindTest.ELEMENT) != TypeHierarchy.DISJOINT ||
                th.relationship(type, NodeKindTest.DOCUMENT) != TypeHierarchy.DISJOINT) {
            result.addDescendants();
        }
        return new PathMap.PathMapNodeSet(pathMap.makeNewRoot(this));
    }

    /**
     * Process this xsl:copy-of instruction
     *
     * @param context the dynamic context for the transformation
     * @return null - this implementation of the method never returns a TailCall
     */

    public TailCall processLeavingTail(XPathContext context) throws XPathException {

        Controller controller = context.getController();
        SequenceReceiver out = context.getReceiver();
        boolean copyBaseURI = out.getSystemId() == null;
        // if the copy is being attached to an existing parent, it inherits the base URI of the parent

        int copyOptions = CopyOptions.TYPE_ANNOTATIONS;
        if (copyNamespaces) {
            copyOptions |= CopyOptions.ALL_NAMESPACES;
        }
        if (copyAccumulators) {
            copyOptions |= CopyOptions.ACCUMULATORS;
        }
        if (copyForUpdate) {
            copyOptions |= CopyOptions.FOR_UPDATE;
        }
        //int whichNamespaces = (copyNamespaces ? NodeInfo.ALL_NAMESPACES : NodeInfo.NO_NAMESPACES);

        SequenceIterator iter = getSelect().iterate(context);
        Item item;
        while ((item = iter.next()) != null) {
            if (item instanceof NodeInfo) {
                NodeInfo source = (NodeInfo) item;
                int kind = source.getNodeKind();
                if (requireDocumentOrElement &&
                        !(kind == Type.ELEMENT || kind == Type.DOCUMENT)) {
                    XPathException e = new XPathException("Operand of validate expression must be a document or element node");
                    e.setXPathContext(context);
                    e.setErrorCode("XQTY0030");
                    throw e;
                }
                final Configuration config = controller.getConfiguration();
                switch (kind) {

                    case Type.ELEMENT: {
                        Receiver eval = out;
                        if (validating) {
                            ParseOptions options = new ParseOptions();
                            options.setSchemaValidationMode(validation);
                            SchemaType type = schemaType;
                            if (type == null) {
                                // Bug 3062
                                String xsitype = source.getAttributeValue(NamespaceConstant.SCHEMA_INSTANCE, "type");
                                if (xsitype != null) {
                                    StructuredQName typeName;
                                    try {
                                        typeName = StructuredQName.fromLexicalQName(
                                                xsitype,
                                                true,
                                                false,
                                                new InscopeNamespaceResolver(source));
                                    } catch (XPathException e) {
                                        throw new XPathException("Invalid QName in xsi:type attribute of element being validated: " + xsitype, "XTTE1510");
                                    }
                                    type = config.getSchemaType(typeName);
                                    if (type == null) {
                                        throw new XPathException("Unknown xsi:type in element being validated: " + xsitype, "XTTE1510");
                                    }
                                }
                            }
                            options.setTopLevelType(type);
                            options.setTopLevelElement(NameOfNode.makeName(source).getStructuredQName());
                            options.setErrorListener(context.getErrorListener());
                            config.prepareValidationReporting(context, options);
                            eval = config.getElementValidator(out, options, getLocation());
                        }
                        if (copyBaseURI) {
                            eval.setSystemId(computeNewBaseUri(source, getStaticBaseURIString()));
                        }

                        Receiver savedReceiver = null;
                        PipelineConfiguration savedPipe = null;
                        if (copyLineNumbers) {
                            savedReceiver = eval;
                            PipelineConfiguration pipe = eval.getPipelineConfiguration();
                            savedPipe = new PipelineConfiguration(pipe);
                            LocationCopier copier = new LocationCopier(false);
                            pipe.setComponent(CopyInformee.class.getName(), copier);
                        }
                        source.copy(eval, copyOptions, getLocation());
                        if (copyLineNumbers) {
                            eval = savedReceiver;
                            assert eval != null;
                            eval.setPipelineConfiguration(savedPipe);
                        }
                        break;
                    }
                    case Type.ATTRIBUTE:
                        if (schemaType != null && schemaType.isComplexType()) {
                            XPathException e = new XPathException("When copying an attribute with schema validation, the requested type must not be a complex type");
                            e.setLocation(getLocation());
                            e.setXPathContext(context);
                            e.setErrorCode("XTTE1535");
                            throw dynamicError(getLocation(), e, context);
                        }
                        try {
                            copyAttribute(source, (SimpleType) schemaType, validation, this, context, rejectDuplicateAttributes);
                        } catch (NoOpenStartTagException err) {
                            XPathException e = new XPathException(err.getMessage());
                            e.setLocation(getLocation());
                            e.setXPathContext(context);
                            e.setErrorCodeQName(err.getErrorCodeQName());
                            throw dynamicError(getLocation(), e, context);
                        }
                        break;
                    case Type.TEXT:
                        out.characters(source.getStringValueCS(), getLocation(), 0);
                        break;

                    case Type.PROCESSING_INSTRUCTION:
                        if (copyBaseURI) {
                            out.setSystemId(source.getBaseURI());
                        }
                        out.processingInstruction(source.getDisplayName(), source.getStringValueCS(), getLocation(), 0);
                        break;

                    case Type.COMMENT:
                        out.comment(source.getStringValueCS(), getLocation(), 0);
                        break;

                    case Type.NAMESPACE:
                        try {
                            source.copy(out, 0, getLocation());
                        } catch (NoOpenStartTagException err) {
                            XPathException e = new XPathException(err.getMessage());
                            e.setXPathContext(context);
                            e.setErrorCodeQName(err.getErrorCodeQName());
                            throw dynamicError(getLocation(), e, context);
                        }
                        break;

                    case Type.DOCUMENT: {
                        ParseOptions options = new ParseOptions();
                        options.setSchemaValidationMode(validation);
                        options.setSpaceStrippingRule(NoElementsSpaceStrippingRule.getInstance());
                        options.setTopLevelType(schemaType);
                        options.setErrorListener(context.getErrorListener());
                        config.prepareValidationReporting(context, options);
                        Receiver val = config.getDocumentValidator(out, source.getBaseURI(), options, getLocation());
                        //val.setPipelineConfiguration(out.getPipelineConfiguration());
                        if (copyBaseURI) {
                            val.setSystemId(source.getBaseURI());
                        }
                        Receiver savedReceiver = null;
                        PipelineConfiguration savedPipe = null;
                        if (copyLineNumbers) {
                            savedReceiver = val;
                            savedPipe = new PipelineConfiguration(val.getPipelineConfiguration());
                            LocationCopier copier = new LocationCopier(true);
                            val.getPipelineConfiguration().setComponent(CopyInformee.class.getName(), copier);

                        }
                        source.copy(val, copyOptions, getLocation());
                        if (copyLineNumbers) {
                            val = savedReceiver;
                            assert val != null;
                            val.setPipelineConfiguration(savedPipe);
                        }
//                        if (val != out) {
//                            See bug 2403
//                            val.close(); // needed to flush out unresolved IDREF values when validating: test copy-5021
//                        }
                        break;
                    }
                    default:
                        throw new IllegalArgumentException("Unknown node kind " + source.getNodeKind());
                }

            } else {
                out.append(item, getLocation(), NodeInfo.ALL_NAMESPACES);
            }
        }
        return null;
    }

    public static String computeNewBaseUri(NodeInfo source, String staticBaseURI) {
        // These rules are the rules for xsl:copy-of instruction in XSLT. The same code is used to support the
        // validate{} expression in XQuery. XQuery says nothing about the base URI of a node that results
        // from a validate{} expression, so until it does, we might as well use the same logic.
        String newBaseUri;
        String xmlBase = source.getAttributeValue(NamespaceConstant.XML, "base");
        if (xmlBase != null) {
            try {
                URI xmlBaseUri = new URI(xmlBase);
                if (xmlBaseUri.isAbsolute()) {
                    newBaseUri = xmlBase;
                } else if (staticBaseURI != null) {
                    URI sbu = new URI(staticBaseURI);
                    URI abs = sbu.resolve(xmlBaseUri);
                    newBaseUri = abs.toString();
                } else {
                    newBaseUri = source.getBaseURI();
                }
            } catch (URISyntaxException err) {
                newBaseUri = source.getBaseURI();
            }
        } else {
            newBaseUri = source.getBaseURI();
        }
        return newBaseUri;
    }

    /**
     * Method shared by xsl:copy and xsl:copy-of to copy an attribute node
     *
     * @param source           the node to be copied
     * @param schemaType       the simple type against which the value is to be validated, if any
     * @param validation       one of preserve, strip, strict, lax
     * @param instruction      the calling instruction, used for diagnostics
     * @param context          the dynamic context
     * @param rejectDuplicates true if duplicate attributes with the same name are disallowed (XQuery)
     * @throws XPathException if a failure occurs
     */

    static void copyAttribute(NodeInfo source,
                              SimpleType schemaType,
                              int validation,
                              Instruction instruction,
                              XPathContext context,
                              boolean rejectDuplicates)
            throws XPathException {
        int opt = 0;
        if (rejectDuplicates) {
            opt |= ReceiverOptions.REJECT_DUPLICATES;
        }
        CharSequence value = source.getStringValueCS();
        SimpleType annotation = validateAttribute(source, schemaType, validation, context);
        try {
            context.getReceiver().attribute(NameOfNode.makeName(source), annotation, value, instruction.getLocation(), opt);
        } catch (XPathException e) {
            e.maybeSetContext(context);
            e.maybeSetLocation(instruction.getLocation());
            if (instruction.getPackageData().getHostLanguage() == Configuration.XQUERY && e.getErrorCodeLocalPart().equals("XTTE0950")) {
                e.setErrorCode("XQTY0086");
            }
            throw e;
        }
    }

    /**
     * Validate an attribute node and return the type annotation to be used
     *
     * @param source           the node to be copied
     * @param schemaType       the simple type against which the value is to be validated, if any
     * @param validation       one of preserve, strip, strict, lax
     * @param context          the dynamic context
     * @throws XPathException if the attribute is not valid
     * @return the type annotation to be used for the attribute
     */


    public static SimpleType validateAttribute(
            NodeInfo source, SimpleType schemaType, int validation, XPathContext context) throws XPathException {
        CharSequence value = source.getStringValueCS();
        SimpleType annotation = BuiltInAtomicType.UNTYPED_ATOMIC;
        if (schemaType != null) {
            if (schemaType.isNamespaceSensitive()) {
                XPathException err = new XPathException("Cannot create a parentless attribute whose " +
                        "type is namespace-sensitive (such as xs:QName)");
                err.setErrorCode("XTTE1545");
                throw err;
            }
            ValidationFailure err = schemaType.validateContent(
                    value, DummyNamespaceResolver.getInstance(), context.getConfiguration().getConversionRules());
            if (err != null) {
                err.setMessage("Attribute being copied does not match the required type. " +
                        err.getMessage());
                throw err.makeException();
            }
            annotation = schemaType;
        } else if (validation == Validation.STRICT || validation == Validation.LAX) {
            try {
                annotation = context.getConfiguration().validateAttribute(NameOfNode.makeName(source).getStructuredQName(), value, validation);
            } catch (ValidationException e) {
                XPathException err = XPathException.makeXPathException(e);
                err.setErrorCodeQName(e.getErrorCodeQName());
                err.setIsTypeError(true);
                throw err;
            }

        } else if (validation == Validation.PRESERVE) {
            annotation = (SimpleType) source.getSchemaType();
            if (!annotation.equals(BuiltInAtomicType.UNTYPED_ATOMIC) && annotation.isNamespaceSensitive()) {
                XPathException err = new XPathException("Cannot preserve type annotation when copying an attribute with namespace-sensitive content");
                err.setErrorCode(context.getController().getExecutable().getHostLanguage() == Configuration.XSLT ? "XTTE0950" : "XQTY0086");
                err.setIsTypeError(true);
                throw err;
            }
        }
        return annotation;
    }

    /*@NotNull*/
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        final Controller controller = context.getController();
        assert controller != null;
        if (schemaType == null && copyNamespaces && !copyForUpdate) {
            if (validation == Validation.PRESERVE) {
                // create a virtual copy of the underlying nodes
                ItemMappingFunction copier = new ItemMappingFunction() {
                    public Item mapItem(Item item) {
                        if (item instanceof NodeInfo) {
                            VirtualCopy vc = VirtualCopy.makeVirtualCopy((NodeInfo) item);
                            vc.getTreeInfo().setCopyAccumulators(copyAccumulators);
                            if (((NodeInfo) item).getNodeKind() == Type.ELEMENT) {
                                vc.setSystemId(computeNewBaseUri((NodeInfo) item, getStaticBaseURIString()));
                            }
                            return vc;
                        } else {
                            return item;
                        }
                    }
                };
                return new ItemMappingIterator(getSelect().iterate(context), copier, true);
            } else if (validation == Validation.STRIP) {
                // create a virtual copy of the underlying nodes
                ItemMappingFunction copier = new ItemMappingFunction() {
                    public Item mapItem(Item item) {
                        if (!(item instanceof NodeInfo)) {
                            return item;
                        }
                        VirtualCopy vc = VirtualUntypedCopy.makeVirtualUntypedTree((NodeInfo) item, (NodeInfo) item);
                        vc.getTreeInfo().setCopyAccumulators(copyAccumulators);
                        if (((NodeInfo) item).getNodeKind() == Type.ELEMENT) {
                            vc.setSystemId(computeNewBaseUri((NodeInfo) item, getStaticBaseURIString()));
                        }
                        return vc;
                    }
                };
                return new ItemMappingIterator(getSelect().iterate(context), copier, true);
            }
        }
        SequenceReceiver saved = context.getReceiver();
        PipelineConfiguration pipe = controller.makePipelineConfiguration();
        pipe.setLocationIsCodeLocation(true);
        SequenceOutputter out = new SequenceOutputter(pipe);
        pipe.setHostLanguage(getPackageData().getHostLanguage());
        context.setReceiver(out);
        try {
            process(context);
        } catch (XPathException err) {
            err.maybeSetLocation(getLocation());
            err.maybeSetContext(context);
            throw err;
        }
        context.setReceiver(saved);
        return out.getSequence().iterate();
    }



}

