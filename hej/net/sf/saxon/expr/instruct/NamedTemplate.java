////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trace.LocationKind;
import net.sf.saxon.trans.SymbolicName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.SequenceType;

import java.util.ArrayList;
import java.util.List;

/**
 * The runtime object corresponding to a named xsl:template element in the stylesheet.
 * <p/>
 * Note that the Template object no longer has precedence information associated with it; this is now
 * only in the Rule object that references this Template. This allows two rules to share the same template,
 * with different precedences. This occurs when a stylesheet module is imported more than once, from different
 * places, with different import precedences.
 *
 * <p>From Saxon 9.7, the NamedTemplate and TemplateRule objects are separated. A NamedTemplate represents
 * a template with a name attribute; a TemplateRule is a template with a match attribute. If an xsl:template
 * declaration has both attributes, two objects are created.</p>
 */

public class NamedTemplate extends Actor {

    // TODO: change the calling mechanism for named templates to use positional parameters
    // in the same way as functions. For templates that have both a match and a name attribute,
    // create a match template as a wrapper around the named template, resulting in separate
    // NamedTemplate and MatchTemplate classes. For named templates, perhaps compile into function
    // calls directly, the only difference being that context is retained.

    // The body of the template is represented by an expression,
    // which is responsible for any type checking that's needed.

    private StructuredQName templateName;
    private boolean hasRequiredParams;
    private boolean bodyIsTailCallReturner;
    private SequenceType requiredType;
    private ItemType requiredContextItemType = AnyItemType.getInstance();
    private boolean mayOmitContextItem = true;
    private boolean absentFocus = false;
    private List<LocalParam> localParams = new ArrayList<LocalParam>(4);

    /**
     * Create a named template
     */

    public NamedTemplate() {}

    /**
     * Initialize the template
     *
     * @param templateName the name of the template (if any)
     *                     performed by apply-imports
     */

    public void setTemplateName(StructuredQName templateName) {
        this.templateName = templateName;
    }

    /**
     * Set the required context item type. Used when there is an xsl:context-item child element
     *
     * @param type          the required context item type
     * @param mayBeOmitted  true if the context item may be absent
     * @param absentFocus true if the context item is treated as absent even if supplied (use=absent)
     */

    public void setContextItemRequirements(ItemType type, boolean mayBeOmitted, boolean absentFocus) {
        requiredContextItemType = type;
        mayOmitContextItem = mayBeOmitted;
        this.absentFocus = absentFocus;
    }

    public int getComponentKind() {
        return StandardNames.XSL_TEMPLATE;
    }

    public SymbolicName getSymbolicName() {
        if (getTemplateName() == null) {
            return null;
        } else {
            return new SymbolicName(StandardNames.XSL_TEMPLATE, getTemplateName());
        }
    }

    /**
     * Set the expression that forms the body of the template
     *
     * @param body the body of the template
     */

    public void setBody(Expression body) {
        super.setBody(body);
        bodyIsTailCallReturner = (body instanceof TailCallReturner);
    }

    /**
     * Get the name of the template (if it is named)
     *
     * @return the template name, or null if unnamed
     */

    public StructuredQName getTemplateName() {
        return templateName;
    }

    /**
     * Get a name identifying the object of the expression, for example a function name, template name,
     * variable name, key name, element name, etc. This is used only where the name is known statically.
     */

    public StructuredQName getObjectName() {
        return templateName;
    }

    public void resetLocalParams() {
        localParams.clear();
    }

    public void addLocalParam(LocalParam param) {
        localParams.add(param);
    }

    /**
     * Set whether this template has one or more required parameters
     *
     * @param has true if the template has at least one required parameter
     */

    public void setHasRequiredParams(boolean has) {
        hasRequiredParams = has;
    }

    /**
     * Ask whether this template has one or more required parameters
     *
     * @return true if this template has at least one required parameter
     */

    public boolean hasRequiredParams() {
        return hasRequiredParams;
    }

    /**
     * Set the required type to be returned by this template
     *
     * @param type the required type as defined in the "as" attribute on the xsl:template element
     */

    public void setRequiredType(SequenceType type) {
        requiredType = type;
    }

    /**
     * Get the required type to be returned by this template
     *
     * @return the required type as defined in the "as" attribute on the xsl:template element
     */

    public SequenceType getRequiredType() {
        if (requiredType == null) {
            return SequenceType.ANY_SEQUENCE;
        } else {
            return requiredType;
        }
    }

    public ItemType getRequiredContextItemType() {
        return requiredContextItemType;
    }

    public boolean isMayOmitContextItem() {
        return mayOmitContextItem;
    }

    public boolean isAbsentFocus() {
        return absentFocus;
    }


    public List<LocalParam> getLocalParams() {
        return localParams;
    }


    /**
     * Get the local parameter with a given parameter id
     *
     * @param id the parameter id
     * @return the local parameter with this id if found, otherwise null
     */

    /*@Nullable*/
    public LocalParam getLocalParam(StructuredQName id) {
        List<LocalParam> params = getLocalParams();
        for (LocalParam lp : params) {
            if (lp.getVariableQName().equals(id)) {
                return lp;
            }
        }
        return null;
    }

    /**
     * Expand the template. Called when the template is invoked using xsl:call-template.
     * Invoking a template by this method does not change the current template.
     *
     * @param context the XPath dynamic context
     * @return null if the template exited normally; but if it was a tail call, details of the call
     * that hasn't been made yet and needs to be made by the caller
     * @throws net.sf.saxon.trans.XPathException if a dynamic error occurs while evaluating
     *                                           the template
     */

    public TailCall expand(XPathContext context) throws XPathException {
        Item contextItem = context.getContextItem();
        if (contextItem == null) {
            if (!mayOmitContextItem) {
                XPathException err =
                        new XPathException("The template requires a context item, but none has been supplied", "XTTE3090");
                err.setLocation(getLocation());
                err.setIsTypeError(true);
                throw err;
            }
        } else {
            TypeHierarchy th = context.getConfiguration().getTypeHierarchy();
            if (requiredContextItemType != AnyItemType.getInstance() &&
                    !requiredContextItemType.matches(contextItem, th)) {
                XPathException err = new XPathException("The template requires a context item of type " + requiredContextItemType +
                        ", but the supplied context item has type " +
                        Type.getItemType(contextItem, context.getConfiguration().getTypeHierarchy()), "XTTE0590");
                err.setLocation(getLocation());
                err.setIsTypeError(true);
                throw err;
            }
            if (absentFocus) {
                context = context.newMinorContext();
                context.setCurrentIterator(null);
            }
        }
        if (bodyIsTailCallReturner) {
            return ((TailCallReturner) body).processLeavingTail(context);
        } else if (body != null) {
            body.process(context);
        }
        return null;
    }


    /**
     * Get the type of construct. This will either be the fingerprint of a standard XSLT instruction name
     * (values in {@link net.sf.saxon.om.StandardNames}: all less than 1024)
     * or it will be a constant in class {@link LocationKind}.
     */

    public int getConstructType() {
        return LocationKind.TEMPLATE;
    }

    /**
     * Output diagnostic explanation to an ExpressionPresenter
     */

    public void export(ExpressionPresenter presenter) throws XPathException {
        presenter.startElement("template");
        presenter.emitAttribute("name", getTemplateName().getEQName());
        explainProperties(presenter);

        presenter.emitAttribute("slots", "" + getStackFrameMap().getNumberOfVariables());

        if (getBody() != null) {
            presenter.setChildRole("body");
            getBody().export(presenter);
        }
        presenter.endElement();
    }

    public void explainProperties(ExpressionPresenter presenter) throws XPathException {
        if (getRequiredContextItemType() != AnyItemType.getInstance()) {
            presenter.emitAttribute("cxt", getRequiredContextItemType().toString());
            if ("JS".equals(presenter.getOption("target"))) {
                try {
                    presenter.emitAttribute("jsTest", getRequiredContextItemType().generateJavaScriptItemTypeTest(AnyItemType.getInstance()));
                } catch (XPathException e) {
                    e.maybeSetLocation(getLocation());
                    throw e;
                }
            }
        }

        String flags = "";
        if (mayOmitContextItem) {
            flags = "o";
        }
        if (!absentFocus) {
            flags += "s";
        }
        presenter.emitAttribute("flags", flags);
        if (getRequiredType() != SequenceType.ANY_SEQUENCE) {
            presenter.emitAttribute("as", getRequiredType().toString());
        }
        presenter.emitAttribute("line", getLineNumber() + "");
        presenter.emitAttribute("module", getSystemId());
    }


}

