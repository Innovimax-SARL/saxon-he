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
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.*;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.NamespaceIterator;
import net.sf.saxon.type.*;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.SequenceType;

import java.util.Iterator;


/**
 * Handler for xsl:copy elements in stylesheet. This only handles copying of the context item. An xsl:copy
 * with a select attribute is handled by wrapping the instruction in an xsl:for-each.
 */

public class Copy extends ElementCreator {

    private boolean copyNamespaces;
    private ItemType selectItemType = AnyItemType.getInstance();
    private ItemType resultItemType;

    /**
     * Create a shallow copy instruction
     *
     * param select            selects the node (or other item) to be copied. Never null.
     * param selectSpecified   true if the select attribute of xsl:copy was specified explicitly (in which
     *                          case the context for evaluating the body will change)
     * @param copyNamespaces    true if namespace nodes are to be copied when copying an element
     * @param inheritNamespaces true if child elements are to inherit the namespace nodes of their parent
     * @param schemaType        the Schema type against which the content is to be validated
     * @param validation        the schema validation mode
     */

    public Copy(boolean copyNamespaces,
                boolean inheritNamespaces,
                SchemaType schemaType,
                int validation) {
        this.copyNamespaces = copyNamespaces;
        this.inheritNamespacesToChildren = inheritNamespaces;
        setValidationAction(validation, schemaType);
        preservingTypes = schemaType == null && validation == Validation.PRESERVE;
    }

    /**
     * Say whether namespace nodes are to be copied (in the case of an element)
     * @param copy set to true if namespace nodes are to be copied
     */

    public void setCopyNamespaces(boolean copy) {
        copyNamespaces = copy;
    }

    /**
     * Ask whether namespace nodes are to be copied (in the case of an element)
     *
     * @return true if all in-scope namespaces are to be copied
     */

    public boolean isCopyNamespaces() {
        return copyNamespaces;
    }

    /**
     * Simplify an expression. This performs any static optimization (by rewriting the expression
     * as a different expression). The default implementation does nothing.
     *
     *
     *
     * @throws net.sf.saxon.trans.XPathException
     *          if an error is discovered during expression rewriting
     */

    /*@NotNull*/
    public Expression simplify() throws XPathException {
        preservingTypes |= !getPackageData().isSchemaAware();
        return super.simplify();
    }


    /*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {

        typeCheckChildren(visitor, contextInfo);

        selectItemType = contextInfo.getItemType();

        ItemType selectItemType = contextInfo.getItemType();  //select.getItemType();
        if (selectItemType == ErrorType.getInstance()) {
            XPathException err = new XPathException("No context item supplied for xsl:copy", "XTTE0945");
            err.setIsTypeError(true);
            err.setLocation(getLocation());
            throw err;
        }

        if (selectItemType instanceof NodeTest) {
            switch (selectItemType.getPrimitiveType()) {
                // For elements and attributes, assume the type annotation will change
                case Type.ELEMENT:
                    this.resultItemType = NodeKindTest.ELEMENT;
                    break;
                case Type.ATTRIBUTE:
                    this.resultItemType = NodeKindTest.ATTRIBUTE;
                    break;
                case Type.DOCUMENT:
                    this.resultItemType = NodeKindTest.DOCUMENT;
                    break;
                default:
                    this.resultItemType = selectItemType;
            }
        } else {
            this.resultItemType = selectItemType;
        }

        checkContentSequence(visitor.getStaticContext());
        return this;
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings
     */

    /*@NotNull*/
    public Expression copy(RebindingMap rebindings) {
        Copy copy = new Copy(
                copyNamespaces, inheritNamespacesToChildren, getSchemaType(), getValidationAction());
        ExpressionTool.copyLocationInfo(this, copy);
        copy.setContentExpression(getContentExpression().copy(rebindings));
        copy.resultItemType = resultItemType;
        return copy;
    }

    /**
     * Determine which aspects of the context the expression depends on. The result is
     * a bitwise-or'ed value composed from constants such as XPathContext.VARIABLES and
     * XPathContext.CURRENT_NODE. The default implementation combines the intrinsic
     * dependencies of this expression with the dependencies of the subexpressions,
     * computed recursively. This is overridden for expressions such as FilterExpression
     * where a subexpression's dependencies are not necessarily inherited by the parent
     * expression.
     *
     * @return a set of bit-significant flags identifying the dependencies of
     *         the expression
     */

    public int getIntrinsicDependencies() {
        return StaticProperty.DEPENDS_ON_CONTEXT_ITEM;
    }

    /**
     * Get the name of this instruction for diagnostic and tracing purposes
     */

    public int getInstructionNameCode() {
        return StandardNames.XSL_COPY;
    }

    /**
     * Get the immediate sub-expressions of this expression, with information about the relationship
     * of each expression to its parent expression. Default implementation
     * returns a zero-length array, appropriate for an expression that has no
     * sub-expressions.
     *
     * @return an iterator containing the sub-expressions of this expression
     */
    @Override
    public Iterable<Operand> operands() {
        return contentOp;
    }


    /**
     * Get the item type of the result of this instruction.
     *
     * @return The context item type.
     */

    /*@NotNull*/
    public ItemType getItemType() {
        if (resultItemType != null) {
            return resultItemType;
        } else {
            TypeHierarchy th = getConfiguration().getTypeHierarchy();
            resultItemType = computeItemType(th);
            return resultItemType;
        }
    }

    private ItemType computeItemType(TypeHierarchy th) {
        ItemType selectItemType = this.selectItemType; //select.getItemType();
        if (!getPackageData().isSchemaAware()) {
            return selectItemType;
        }
        // The rest of the code handles the complications of schema-awareness
        Configuration config = th.getConfiguration();
        if (getSchemaType() != null) {
            int e = th.relationship(selectItemType, NodeKindTest.ELEMENT);
            if (e == TypeHierarchy.SAME_TYPE || e == TypeHierarchy.SUBSUMED_BY) {
                return new ContentTypeTest(Type.ELEMENT, getSchemaType(), config, false);
            }
            int a = th.relationship(selectItemType, NodeKindTest.ATTRIBUTE);
            if (a == TypeHierarchy.SAME_TYPE || a == TypeHierarchy.SUBSUMED_BY) {
                return new ContentTypeTest(Type.ATTRIBUTE, getSchemaType(), config, false);
            }
            return AnyNodeTest.getInstance();
        } else {
            switch (getValidationAction()) {
                case Validation.PRESERVE:
                    return selectItemType;
                case Validation.STRIP: {
                    int e = th.relationship(selectItemType, NodeKindTest.ELEMENT);
                    if (e == TypeHierarchy.SAME_TYPE || e == TypeHierarchy.SUBSUMED_BY) {
                        return new ContentTypeTest(Type.ELEMENT, Untyped.getInstance(), config, false);
                    }
                    int a = th.relationship(selectItemType, NodeKindTest.ATTRIBUTE);
                    if (a == TypeHierarchy.SAME_TYPE || a == TypeHierarchy.SUBSUMED_BY) {
                        return new ContentTypeTest(Type.ATTRIBUTE, BuiltInAtomicType.UNTYPED_ATOMIC, config, false);
                    }
                    if (e != TypeHierarchy.DISJOINT || a != TypeHierarchy.DISJOINT) {
                        // it might be an element or attribute
                        return AnyNodeTest.getInstance();
                    } else {
                        // it can't be an element or attribute, so stripping type annotations can't affect it
                        return selectItemType;
                    }
                }
                case Validation.STRICT:
                case Validation.LAX:
                    if (selectItemType instanceof NodeTest) {
                        int fp = ((NodeTest) selectItemType).getFingerprint();
                        if (fp != -1) {
                            int e = th.relationship(selectItemType, NodeKindTest.ELEMENT);
                            if (e == TypeHierarchy.SAME_TYPE || e == TypeHierarchy.SUBSUMED_BY) {
                                SchemaDeclaration elem = config.getElementDeclaration(fp);
                                if (elem != null) {
                                    try {
                                        return new ContentTypeTest(Type.ELEMENT, elem.getType(), config, false);
                                    } catch (MissingComponentException e1) {
                                        return new ContentTypeTest(Type.ELEMENT, AnyType.getInstance(), config, false);
                                    }
                                } else {
                                    // No element declaration now, but there might be one at run-time
                                    return new ContentTypeTest(Type.ELEMENT, AnyType.getInstance(), config, false);
                                }
                            }
                            int a = th.relationship(selectItemType, NodeKindTest.ATTRIBUTE);
                            if (a == TypeHierarchy.SAME_TYPE || a == TypeHierarchy.SUBSUMED_BY) {
                                SchemaDeclaration attr = config.getElementDeclaration(fp);
                                if (attr != null) {
                                    try {
                                        return new ContentTypeTest(Type.ATTRIBUTE, attr.getType(), config, false);
                                    } catch (MissingComponentException e1) {
                                        return new ContentTypeTest(Type.ATTRIBUTE, AnySimpleType.getInstance(), config, false);
                                    }
                                } else {
                                    // No attribute declaration now, but there might be one at run-time
                                    return new ContentTypeTest(Type.ATTRIBUTE, AnySimpleType.getInstance(), config, false);
                                }
                            }
                        } else {
                            int e = th.relationship(selectItemType, NodeKindTest.ELEMENT);
                            if (e == TypeHierarchy.SAME_TYPE || e == TypeHierarchy.SUBSUMED_BY) {
                                return NodeKindTest.ELEMENT;
                            }
                            int a = th.relationship(selectItemType, NodeKindTest.ATTRIBUTE);
                            if (a == TypeHierarchy.SAME_TYPE || a == TypeHierarchy.SUBSUMED_BY) {
                                return NodeKindTest.ATTRIBUTE;
                            }
                        }
                        return AnyNodeTest.getInstance();
                    } else if (selectItemType instanceof AtomicType) {
                        return selectItemType;
                    } else {
                        return AnyItemType.getInstance();
                    }
                default:
                    throw new IllegalStateException();
            }
        }
    }

    /*@NotNull*/
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        Expression exp = super.optimize(visitor, contextItemType);
        if (exp == this) {
            if (resultItemType == null) {
                resultItemType = computeItemType(visitor.getConfiguration().getTypeHierarchy());
            }
            if (visitor.isOptimizeForStreaming()) {
                UType type = contextItemType.getItemType().getUType();
                if (!type.intersection(MultipleNodeKindTest.LEAF.getUType()).equals(UType.VOID)) {
                    Expression copyOf = new CopyOf(
                            new ContextItemExpression(), false, getValidationAction(), getSchemaType(), false);
                    NodeTest leafTest = new MultipleNodeKindTest(type.intersection(MultipleNodeKindTest.LEAF.getUType()));
                    Expression[] conditions = new Expression[]{
                            new InstanceOfExpression(
                                    new ContextItemExpression(),
                                    SequenceType.makeSequenceType(leafTest, StaticProperty.EXACTLY_ONE)),
                            Literal.makeLiteral(BooleanValue.TRUE)};
                    Expression[] actions = new Expression[]{copyOf, this};
                    Choose choose = new Choose(conditions, actions);
                    ExpressionTool.copyLocationInfo(this, choose);
                    return choose;
                }
            }
        }
        return exp;
    }

    /**
     * Callback from ElementCreator when constructing an element
     *
     * @param context    XPath dynamic evaluation context
     * @param copiedNode the node being copied
     * @return the namecode of the element to be constructed
     * @throws XPathException
     */

    public NodeName getElementName(XPathContext context, NodeInfo copiedNode) throws XPathException {
        return NameOfNode.makeName(copiedNode);
    }

    /**
     * Get the base URI of a copied element node (the base URI is retained in the new copy)
     *
     * @param context    XPath dynamic evaluation context
     * @param copiedNode the node being copied (for xsl:copy), otherwise null
     * @return the base URI
     */

    public String getNewBaseURI(XPathContext context, NodeInfo copiedNode) {
        return copiedNode.getBaseURI();
    }

    /**
     * Callback to output namespace nodes for the new element.
     *
     * @param context    The execution context
     * @param receiver   the Receiver where the namespace nodes are to be written
     * @param nodeName    the element name
     * @param copiedNode  the node being copied (for xsl:copy), otherwise null
     * @throws XPathException
     */

    public void outputNamespaceNodes(XPathContext context, Receiver receiver, NodeName nodeName, NodeInfo copiedNode)
            throws XPathException {
        if (copyNamespaces) {
            NamespaceIterator.sendNamespaces(copiedNode, receiver);
        } else {
            // Always output the namespace of the element name itself
            receiver.namespace(nodeName.getNamespaceBinding(), 0);
        }
    }

    /**
     * Callback to get a list of the intrinsic namespaces that need to be generated for the element.
     * The result is an array of namespace codes, the codes either occupy the whole array or are
     * terminated by a -1 entry. A result of null is equivalent to a zero-length array.
     */

    public NamespaceBinding[] getActiveNamespaces() throws XPathException {
        if (copyNamespaces) {
            // we should have disabled lazy construction, so this shouldn't be called.
            throw new UnsupportedOperationException();
        } else {
            return null;
        }
    }


    public TailCall processLeavingTail(XPathContext context) throws XPathException {
        Controller controller = context.getController();
        SequenceReceiver out = context.getReceiver();
        Item item = context.getContextItem();//select.evaluateItem(context);
        if (item == null) {
            return null;
        }

        if (!(item instanceof NodeInfo)) {
            out.append(item, getLocation(), NodeInfo.ALL_NAMESPACES);
            return null;
        }
        NodeInfo source = (NodeInfo) item;
        //out.getPipelineConfiguration().setBaseURI(source.getBaseURI());

        // Processing depends on the node kind.

        switch (source.getNodeKind()) {

            case Type.ELEMENT:
                // use the generic code for creating new elements
                return super.processLeavingTail(context, (NodeInfo) item);

            case Type.ATTRIBUTE:
                if (getSchemaType() instanceof ComplexType) {
                    dynamicError("Cannot copy an attribute when the type requested for validation is a complex type", "XTTE1535", context);
                }
                try {
                    CopyOf.copyAttribute(source, (SimpleType) getSchemaType(), getValidationAction(), this, context, false);
                } catch (NoOpenStartTagException err) {
                    err.setXPathContext(context);
                    throw dynamicError(getLocation(), err, context);
                }
                break;

            case Type.TEXT:
                CharSequence tval = source.getStringValueCS();
                out.characters(tval, getLocation(), 0);
                break;

            case Type.PROCESSING_INSTRUCTION:
                CharSequence pval = source.getStringValueCS();
                out.processingInstruction(source.getDisplayName(), pval, getLocation(), 0);
                break;

            case Type.COMMENT:
                CharSequence cval = source.getStringValueCS();
                out.comment(cval, getLocation(), 0);
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

            case Type.DOCUMENT:
                SequenceReceiver saved = out;
                boolean pop = false;
                if (!preservingTypes) {
                    ParseOptions options = new ParseOptions(getValidationOptions());
                    options.setSpaceStrippingRule(NoElementsSpaceStrippingRule.getInstance());
                    controller.getConfiguration().prepareValidationReporting(context, options);
                    Receiver val = controller.getConfiguration().
                        getDocumentValidator(out, source.getBaseURI(), options, getLocation());
                    if (val != out) {
                        SequenceReceiver sr = new TreeReceiver(val);
                        sr.setPipelineConfiguration(out.getPipelineConfiguration());
                        context.setReceiver(sr);
                        pop = true;
                        out = sr;
                    }
                }
                out.startDocument(0);
                copyUnparsedEntities(source, out);
                getContentExpression().process(context);
                out.endDocument();
                if (pop) {
                    context.setReceiver(saved);
                }
                break;

            default:
                throw new IllegalArgumentException("Unknown node kind " + source.getNodeKind());

        }
        return null;
    }

    public static void copyUnparsedEntities(NodeInfo source, SequenceReceiver out) throws XPathException {
        Iterator<String> unparsedEntities = source.getTreeInfo().getUnparsedEntityNames();
        while (unparsedEntities.hasNext()) {
            String n = unparsedEntities.next();
            String[] details = source.getTreeInfo().getUnparsedEntity(n);
            out.setUnparsedEntity(n, details[0], details[1]);
        }
    }

    /**
     * Evaluate as an expression. We rely on the fact that when these instructions
     * are generated by XQuery, there will always be a valueExpression to evaluate
     * the content
     */

    public Item evaluateItem(XPathContext context) throws XPathException {
        Controller controller = context.getController();
        SequenceReceiver saved = context.getReceiver();
        SequenceOutputter seq = controller.allocateSequenceOutputter(1);
        seq.getPipelineConfiguration().setHostLanguage(getPackageData().getHostLanguage());
        seq.getPipelineConfiguration().setLocationIsCodeLocation(true);
        context.setReceiver(seq);
        process(context);
        seq.close();
        context.setReceiver(saved);
        Item item = seq.getFirstItem();
        seq.reset();
        return item;
    }

    /**
     * Get the (partial) name of a class that supports streaming of this kind of expression
     *
     * @return the partial name of a class that can be instantiated to provide streaming support in Saxon-EE,
     * or null if there is no such class
     */
    @Override
    public String getStreamerName() {
        return "Copy";
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("copy", this);
        exportValidationAndType(out);
        String flags = "";
        if (copyNamespaces) {
            flags = "c";
        }
        if (inheritNamespacesToChildren) {
            flags += "i";
        }
        if (inheritNamespacesFromParent) {
            flags += "n";
        }
        if (isLocal()) {
            flags += "l";
        }
        out.emitAttribute("flags", flags);
        out.setChildRole("content");
        getContentExpression().export(out);
        out.endElement();
    }


}

