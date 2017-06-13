////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.expr.parser.RetainedStaticContext;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.style.StylesheetPackage;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trace.InstructionInfo;
import net.sf.saxon.trans.SymbolicName;
import net.sf.saxon.trans.Visibility;
import net.sf.saxon.trans.XPathException;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * This object represents the compiled form of a user-written function, template, attribute-set, etc
 * (the source can be either an XSLT stylesheet function or an XQuery function).
 * <p/>
 * <p>It is assumed that type-checking, of both the arguments and the results,
 * has been handled at compile time. That is, the expression supplied as the body
 * of the function must be wrapped in code to check or convert the result to the
 * required type, and calls on the function must be wrapped at compile time to check or
 * convert the supplied arguments.
 */

public abstract class Actor implements InstructionInfo, ExpressionOwner {

    protected Expression body;
    private String systemId;
    private int lineNumber;
    private SlotManager stackFrameMap;
    private PackageData packageData;
    private Component declaringComponent;
    private Visibility declaredVisibility;
    private RetainedStaticContext retainedStaticContext;

    // TODO: there is redundancy here between packageData, retainedStaticContext.packageData, and declaringComponent.declaringPackage

    public Actor() {
    }

    /**
     * Get the symbolic name of the component
     *
     * @return the symbolic name
     */

    public abstract SymbolicName getSymbolicName();

    /**
     * Set basic data about the unit of compilation (XQuery module, XSLT package) to which this
     * procedure belongs
     *
     * @param packageData information about the containing package
     */

    public void setPackageData(PackageData packageData) {
        this.packageData = packageData;
    }

    /**
     * Get basic data about the unit of compilation (XQuery module, XSLT package) to which this
     * container belongs
     */
    public PackageData getPackageData() {
        return packageData;
    }

    public Component makeDeclaringComponent(Visibility visibility, StylesheetPackage declaringPackage) {
        if (declaringComponent == null) {
            declaringComponent = Component.makeComponent(this, visibility, declaringPackage, declaringPackage);
        }
        return declaringComponent;
    }

    public Component getDeclaringComponent() {
        return declaringComponent;
    }

    public void setDeclaringComponent(Component comp) {
        declaringComponent = comp;
    }

    /**
     * Allocate slot numbers to all the external component references in this component
     *
     * @param pack the containing package
     */

    public void allocateAllBindingSlots(StylesheetPackage pack) {
        if (getBody() != null && getDeclaringComponent().getDeclaringPackage() == pack) {
            allocateBindingSlotsRecursive(pack, this, getBody(), getDeclaringComponent().getComponentBindings());
        }
    }

    public static void allocateBindingSlotsRecursive(StylesheetPackage pack, Actor p, Expression exp, List<ComponentBinding> bindings) {
        if (exp instanceof ComponentInvocation) {
            p.processComponentReference(pack, (ComponentInvocation) exp, bindings);
        }
        for (Operand o : exp.operands()) {
            allocateBindingSlotsRecursive(pack, p, o.getChildExpression(), bindings);
        }
    }

    /**
     * Process a component reference from this component to some other component. Specifically, allocate
     * a slot number to the component reference, and add the reference to the supplied binding vector
     *
     * @param pack       the containing package
     * @param invocation the instruction or expression representing the external reference, for example
     *                   a call-template instruction
     * @param bindings   the supplied binding vector, containing all outward references
     *                   from this component; this list is modified by the call
     */

    private void processComponentReference(StylesheetPackage pack, ComponentInvocation invocation, List<ComponentBinding> bindings) {
        SymbolicName name = invocation.getSymbolicName();
        if (name == null) {
            // there is no target component, e.g. with apply-templates mode="#current"
            return;
        }

        Component target = pack.getComponent(name);
        if (target == null) {
            target = pack.getHiddenComponent(name);
        }
        if (target == null && name.getComponentName().hasURI(NamespaceConstant.XSLT) &&
                name.getComponentName().getLocalPart().equals("original")) {
            target = pack.getOverriddenComponent(getSymbolicName());
        }
        if (target == null) {
            throw new AssertionError("Target of component reference " + name + " is undefined");
        }
        if (invocation.getBindingSlot() >= 0) {
            throw new AssertionError("**** Component reference " + name + " is already bound");
        }
        int slot = bindings.size();
        ComponentBinding cb = new ComponentBinding(name, target);
        bindings.add(cb);
        invocation.setBindingSlot(slot);
    }

    public void setBody(Expression body) {
        this.body = body;
        if (body != null) {
            body.setParentExpression(null);
        }
    }

    public final Expression getBody() {
        return body;
    }

    public void setStackFrameMap(SlotManager map) {
        stackFrameMap = map;
    }

    public SlotManager getStackFrameMap() {
        return stackFrameMap;
    }

    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    public Location getLocation() {
        return this;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getSystemId() {
        return systemId;
    }

    public int getColumnNumber() {
        return -1;
    }

    /*@Nullable*/
    public String getPublicId() {
        return null;
    }

    public Location saveLocation() {
        return this;
    }

    public void setRetainedStaticContext(RetainedStaticContext rsc) {
        this.retainedStaticContext = rsc;
    }

    public RetainedStaticContext getRetainedStaticContext() {
        return retainedStaticContext;
    }

    public Object getProperty(String name) {
        return null;
    }

    /**
     * Set the visibility of the component as defined using its actual @visibility attribute
     *
     * @param visibility the actual declared visibility; null if the visibility attribute is absent
     */

    public void setDeclaredVisibility(Visibility visibility) {
        this.declaredVisibility = visibility;
    }

    /**
     * Get the visibility of the component as defined using its actual @visibility attribute
     *
     * @return the actual declared visibility; null if the visibility attribute is absent
     */

    public Visibility getDeclaredVisibility() {
        return declaredVisibility;
    }


    /**
     * Get an iterator over all the properties available. The values returned by the iterator
     * will be of type String, and each string can be supplied as input to the getProperty()
     * method to retrieve the value of the property. The iterator may return properties whose
     * value is null.
     */

    public Iterator<String> getProperties() {
        final List<String> list = Collections.emptyList();
        return list.iterator();
    }

    /**
     * Get the kind of component that this represents, using integer constants such as
     * {@link net.sf.saxon.om.StandardNames#XSL_FUNCTION}
     */

    public abstract int getComponentKind();

    /**
     * Export expression structure. The abstract expression tree
     * is written to the supplied outputstream.
     *
     * @param presenter the expression presenter used to generate the XML representation of the structure
     */

    public abstract void export(ExpressionPresenter presenter) throws XPathException;

    public boolean isExportable() {
        return true;
    }

    public void setChildExpression(Expression expr) {
        setBody(expr);
    }
}


