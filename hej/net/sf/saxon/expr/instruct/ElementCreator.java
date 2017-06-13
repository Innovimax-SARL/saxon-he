////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.Controller;
import net.sf.saxon.event.*;
import net.sf.saxon.expr.*;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NamespaceBinding;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.Cardinality;


/**
 * An instruction that creates an element node. There are two subtypes, FixedElement
 * for use where the name is known statically, and Element where it is computed
 * dynamically. To allow use in both XSLT and XQuery, the class acts both as an
 * Instruction and as an Expression.
 */

public abstract class ElementCreator extends ParentNodeConstructor {


    /**
     * The inheritNamespacesToChildren flag indicates that the namespace nodes on the element created by this instruction
     * are to be inherited (copied) on the children of this element. That is, if this flag is false, the child
     * elements must carry a namespace undeclaration for all the namespaces on the parent, unless they are
     * redeclared in some way.
     */

    protected boolean inheritNamespacesToChildren = true;

    /**
     * The inheritNamespacesFromParent flag indicates that this element should inherit the namespaces of its
     * parent element in the result tree. That is, if this flag is false, this element must carry a namespace
     * undeclaration for all the namespaces on its parent, unless they are redeclared in some way.
     */

    protected boolean inheritNamespacesFromParent = true;

    /**
     * Construct an ElementCreator. Exists for the benefit of subclasses.
     */

    public ElementCreator() {
    }

    /**
     * Get the item type of the value returned by this instruction
     *
     * @return the item type
     */

    /*@NotNull*/
    public ItemType getItemType() {
        return NodeKindTest.ELEMENT;
    }

    @Override
    public int getCardinality() {
        return StaticProperty.EXACTLY_ONE;
    }

    /**
     * Say whether this element causes its children to inherit namespaces
     * @param inherit true if namespaces are to be inherited by the children
     */

    public void setInheritNamespacesToChildren(boolean inherit) {
        inheritNamespacesToChildren = inherit;
    }

    /**
     * Ask whether the inherit namespaces flag is set
     *
     * @return true if namespaces constructed on this parent element are to be inherited by its children
     */

    public boolean isInheritNamespacesToChildren() {
        return inheritNamespacesToChildren;
    }

    /**
     * Say whether this element causes inherits namespaces from its parent. True except in XQuery where
     * one direct element constructor forms the immediate content of another (see W3C bug 22334)
     *
     * @param inherit true if namespaces are to be inherited from the parent
     */

    public void setInheritNamespacesFromParent(boolean inherit) {
        inheritNamespacesFromParent = inherit;
    }

    /**
     * Ask whether this element inherits namespaces from its parent. True except in XQuery where
     * one direct element constructor forms the immediate content of another (see W3C bug 22334)
     *
     * @return true if this child element inherits namespaces from its parent element
     */

    public boolean isInheritNamespacesFromParent() {
        return inheritNamespacesFromParent;
    }

    /**
     * Get the static properties of this expression (other than its type). The result is
     * bit-signficant. These properties are used for optimizations. In general, if
     * property bit is set, it is true, but if it is unset, the value is unknown.
     *
     * @return a set of flags indicating static properties of this expression
     */

    public int computeSpecialProperties() {
        int p = super.computeSpecialProperties() |
                StaticProperty.SINGLE_DOCUMENT_NODESET;
        if (getValidationAction() == Validation.STRIP) {
            p |= StaticProperty.ALL_NODES_UNTYPED;
        }
        return p;
    }

    /**
     * Suppress validation on contained element constructors, on the grounds that the parent element
     * is already performing validation. The default implementation does nothing.
     */

    public void suppressValidation(int parentValidationMode) {
        if (getValidationAction() == parentValidationMode && getSchemaType() == null) {
            // TODO: is this safe? e.g. if the child has validation=strict but matches a skip wildcard in the parent
            setValidationAction(Validation.PRESERVE, null);
        }
    }

    /**
     * Check statically whether the content of the element creates attributes or namespaces
     * after creating any child nodes
     *
     * @param env the static context
     * @throws XPathException
     */

    protected void checkContentSequence(StaticContext env) throws XPathException {
        if (getContentExpression() instanceof Block) {
            // TODO: why no checking if it isn't a Block? cf DocumentInstr which does such checking
            Operand[] components = ((Block) getContentExpression()).getOperanda();
            boolean foundChild = false;
            boolean foundPossibleChild = false;
            for (Operand o : components) {
                 Expression component = o.getChildExpression();
                ItemType it = component.getItemType();
                if (it instanceof NodeTest) {
                    boolean maybeEmpty = Cardinality.allowsZero(component.getCardinality());
                    UType possibleNodeKinds = it.getUType();
                    if (possibleNodeKinds.overlaps(UType.TEXT)) {
                        // the text node might turn out to be zero-length. If that's a possibility,
                        // then we only issue a warning. Also, we need to completely ignore a known
                        // zero-length text node, which is included to prevent space-separation
                        // in an XQuery construct like <a>{@x}{@y}</b>
                        if (component instanceof ValueOf &&
                                ((ValueOf) component).getSelect() instanceof StringLiteral) {
                            String value = ((StringLiteral) ((ValueOf) component).getSelect()).getStringValue();
                            if (value.isEmpty()) {
                                // continue;  // not an error
                            } else {
                                foundChild = true;
                            }
                        } else {
                            foundPossibleChild = true;
                        }
                    } else if (!possibleNodeKinds.overlaps(UType.CHILD_NODE_KINDS)) {
                        if (maybeEmpty) {
                            foundPossibleChild = true;
                        } else {
                            foundChild = true;
                        }
                    } else if (foundChild && possibleNodeKinds == UType.ATTRIBUTE && !maybeEmpty) {
                        XPathException de = new XPathException(
                                "Cannot create an attribute node after creating a child of the containing element");
                        de.setErrorCode(isXSLT() ? "XTDE0410" : "XQTY0024");
                        de.setLocator(component.getLocation());
                        throw de;
                    } else if (foundChild && possibleNodeKinds == UType.NAMESPACE && !maybeEmpty) {
                        XPathException de = new XPathException(
                                "Cannot create a namespace node after creating a child of the containing element");
                        de.setErrorCode(isXSLT() ? "XTDE0410" : "XQTY0024");
                        de.setLocator(component.getLocation());
                        throw de;
                    } else if ((foundChild || foundPossibleChild) && possibleNodeKinds == UType.ATTRIBUTE) {
                        env.issueWarning(
                                "Creating an attribute here will fail if previous instructions create any children",
                                component.getLocation());
                    } else if ((foundChild || foundPossibleChild) && possibleNodeKinds == UType.NAMESPACE) {
                        env.issueWarning(
                                "Creating a namespace node here will fail if previous instructions create any children",
                                component.getLocation());
                    }
                }
            }

        }
    }

    /**
     * Determine (at run-time) the name code of the element being constructed
     *
     * @param context    the XPath dynamic evaluation context
     * @param copiedNode for the benefit of xsl:copy, the node being copied; otherwise null
     * @return the integer name code representing the element name
     * @throws XPathException if a failure occurs
     */

    public abstract NodeName getElementName(XPathContext context, /*@Nullable*/ NodeInfo copiedNode) throws XPathException;

    /**
     * Get the base URI for the element being constructed
     *
     * @param context    the XPath dynamic evaluation context
     * @param copiedNode the node being copied (for xsl:copy), otherwise null
     * @return the base URI of the constructed element
     */

    public abstract String getNewBaseURI(XPathContext context, NodeInfo copiedNode);

    /**
     * Callback to output namespace nodes for the new element. This method is responsible
     * for ensuring that a namespace node is always generated for the namespace of the element
     * name itself.
     *
     * @param context    The execution context
     * @param receiver   the Receiver where the namespace nodes are to be written
     * @param nameCode   the name code of the element being created
     * @param copiedNode the node being copied (for xsl:copy) or null otherwise
     * @throws XPathException if a dynamic error occurs
     */

    public abstract void outputNamespaceNodes(
            XPathContext context, Receiver receiver, NodeName nameCode, /*@Nullable*/ NodeInfo copiedNode)
            throws XPathException;

    /**
     * Callback to get a list of the intrinsic namespaces that need to be generated for the element.
     *
     * @return the set of namespace bindings.
     * @throws net.sf.saxon.trans.XPathException
     *          if an error occurs
     */

    public NamespaceBinding[] getActiveNamespaces() throws XPathException {
        return null;
    }

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is prefered. For instructions this is the process() method.
     */

    public int getImplementationMethod() {
        return Expression.PROCESS_METHOD | Expression.EVALUATE_METHOD;
    }

    /**
     * Evaluate the instruction to produce a new element node. This method is typically used when there is
     * a parent element or document in a result tree, to which the new element is added.
     *
     * @param context XPath dynamic evaluation context
     * @return null (this instruction never returns a tail call)
     * @throws XPathException
     */
    public TailCall processLeavingTail(XPathContext context)
            throws XPathException {
        return processLeavingTail(context, null);
    }

    /**
     * Evaluate the instruction to produce a new element node. This method is typically used when there is
     * a parent element or document in a result tree, to which the new element is added.
     *
     * @param context    XPath dynamic evaluation context
     * @param copiedNode null except in the case of xsl:copy, when it is the node being copied; otherwise null
     * @return null (this instruction never returns a tail call)
     * @throws XPathException if a dynamic error occurs
     */
    public final TailCall processLeavingTail(XPathContext context, /*@Nullable*/ NodeInfo copiedNode)
            throws XPathException {

        try {

            NodeName elemName = getElementName(context, copiedNode);
            SchemaType typeCode = getValidationAction() == Validation.PRESERVE ? AnyType.getInstance() : Untyped.getInstance();

            SequenceReceiver out = context.getReceiver();
            SequenceReceiver saved = out;
            boolean pop = false;
            Receiver elemOut = out;
            if (!preservingTypes) {
                ParseOptions options = new ParseOptions(getValidationOptions());
                options.setTopLevelElement(elemName.getStructuredQName());
                context.getConfiguration().prepareValidationReporting(context, options);
                Receiver validator = context.getConfiguration().getElementValidator(out, options, getLocation());

                if (validator != out) {
                    out = new TreeReceiver(validator);
                    context.setReceiver(out);
                    pop = true;
                }
                elemOut = out;
            }

            if (elemOut.getSystemId() == null) {
                elemOut.setSystemId(getNewBaseURI(context, copiedNode));
            }
            int properties = inheritNamespacesToChildren ? 0 : ReceiverOptions.DISINHERIT_NAMESPACES;
            if (!inheritNamespacesFromParent) {
                properties |= ReceiverOptions.REFUSE_NAMESPACES;
            }

            elemOut.startElement(elemName, typeCode, getLocation(), properties);

            // output the required namespace nodes via a callback

            outputNamespaceNodes(context, elemOut, elemName, copiedNode);

            // process subordinate instructions to generate attributes and content
            getContentExpression().process(context);

            // output the element end tag (which will fail if validation fails)
            elemOut.endElement();

            if (pop) {
                context.setReceiver(saved);
            }
            return null;

        } catch (XPathException e) {
            e.maybeSetLocation(getLocation());
            e.maybeSetContext(context);
            throw e;
        }
    }

    /**
     * Evaluate the constructor, returning the constructed element node. If lazy construction
     * mode is in effect, then an UnconstructedParent object is returned instead.
     */

    public Item evaluateItem(XPathContext context) throws XPathException {
        return constructElement(context, null);
        // TODO: recover from validation errors that might have occurred
    }

    /**
     * Construct the element node as a free-standing (parentless) node in a tiny tree
     *
     * @param context    XPath dynamic evaluation context
     * @param copiedNode for the benefit of xsl:copy, the node being copied
     * @return the constructed element node
     * @throws XPathException if a dynamic error occurs
     */
    private NodeInfo constructElement(XPathContext context, /*@Nullable*/ NodeInfo copiedNode) throws XPathException {
        try {
            Controller controller = context.getController();
            SequenceReceiver saved = context.getReceiver();
            SequenceOutputter seq = controller.allocateSequenceOutputter(1);
            seq.getPipelineConfiguration().setHostLanguage(getPackageData().getHostLanguage());
            seq.getPipelineConfiguration().setLocationIsCodeLocation(true);

            NodeName elemName = getElementName(context, copiedNode);
            SchemaType typeCode = getValidationAction() == Validation.PRESERVE ? AnyType.getInstance() : Untyped.getInstance();

            SequenceReceiver ini = seq;
            if (!preservingTypes) {
                ParseOptions options = new ParseOptions(getValidationOptions());
                options.setTopLevelElement(elemName.getStructuredQName());
                controller.getConfiguration().prepareValidationReporting(context, options);
                Receiver validator = controller.getConfiguration().getElementValidator(ini, options, getLocation());

                if (ini.getSystemId() == null) {
                    ini.setSystemId(getNewBaseURI(context, copiedNode));
                }
                if (validator == ini) {
                    context.setReceiver(ini);
                } else {
                    TreeReceiver tr = new TreeReceiver(validator);
                    tr.setPipelineConfiguration(seq.getPipelineConfiguration());
                    context.setReceiver(tr);
                    ini = tr;
                }
            } else {
                context.setReceiver(ini);
                if (ini.getSystemId() == null) {
                    ini.setSystemId(getNewBaseURI(context, copiedNode));
                }
            }

            ini.open();
            int properties = inheritNamespacesToChildren ? 0 : ReceiverOptions.DISINHERIT_NAMESPACES;
            if (!inheritNamespacesFromParent) {
                properties |= ReceiverOptions.REFUSE_NAMESPACES;
            }
            ini.startElement(elemName, typeCode, getLocation(), properties);

            // output the namespace nodes for the new element
            outputNamespaceNodes(context, ini, elemName, null);

            getContentExpression().process(context);

            ini.endElement();
            ini.close();
            context.setReceiver(saved);

            // the constructed element is the first and only item in the sequence
            NodeInfo result = (NodeInfo) seq.popLastItem();
            seq.reset();
            return result;

        } catch (XPathException err) {
            err.maybeSetLocation(getLocation());
            err.maybeSetContext(context);
            throw err;
        }
    }

    protected void exportValidationAndType(ExpressionPresenter out) {
        if (getValidationAction() != Validation.SKIP && getValidationAction() != Validation.BY_TYPE) {
            out.emitAttribute("validation", Validation.toString(getValidationAction()));
        }
        if (getValidationAction() == Validation.BY_TYPE) {
            SchemaType type = getSchemaType();
            if (type != null) {
                out.emitAttribute("type", type.getStructuredQName());
            }
        }
    }

    protected String getInheritanceFlags() {
        String flags = "";
        if (!inheritNamespacesFromParent) {
            flags += "P";
        }
        if (!inheritNamespacesToChildren) {
            flags += "C";
        }
        return flags;
    }

    public void setInheritanceFlags(String flags) {
        inheritNamespacesFromParent = !flags.contains("P");
        inheritNamespacesToChildren = !flags.contains("C");
    }

    /**
     * Get the (partial) name of a class that supports streaming of this kind of expression
     *
     * @return the partial name of a class that can be instantiated to provide streaming support in Saxon-EE,
     * or null if there is no such class
     */
    @Override
    public String getStreamerName() {
        return "ElementCreator";
    }
}

