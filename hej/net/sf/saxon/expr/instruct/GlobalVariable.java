////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.Configuration;
import net.sf.saxon.Controller;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.query.XQueryFunction;
import net.sf.saxon.query.XQueryFunctionLibrary;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trace.InstructionInfo;
import net.sf.saxon.trace.LocationKind;
import net.sf.saxon.trans.SymbolicName;
import net.sf.saxon.trans.Visibility;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.ManualIterator;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.IntegerValue;
import net.sf.saxon.value.SequenceExtent;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.SingletonClosure;
import net.sf.saxon.value.Cardinality;

import java.util.*;

/**
 * A compiled global variable in a stylesheet or query. <br>
 */

public class GlobalVariable extends Actor
    implements Binding, net.sf.saxon.query.Declaration, InstructionInfo, ContextOriginator {

    protected List<BindingReference> references = new ArrayList<BindingReference>(10);
    // Note that variableReferences on this list might be dormant;
    // that is, they might be disconnected from the live expression tree.
    // References are maintained in XQuery but not in XSLT (where they are handled at the level of the
    // XSLSourceBinding object)

    private Expression select;
    private StructuredQName variableQName;
    private SequenceType requiredType;

    private boolean indexed;
    private boolean isPrivate = false;
    private boolean isAssignable = false;
    private GlobalVariable originalVariable;
    private int binderySlotNumber;
    private boolean isRequiredParam;

    /**
     * Create a global variable
     */

    public GlobalVariable() {
    }

    /**
     * Initialize the properties of the variable
     *
     * @param select the expression to which the variable is bound
     * @param qName  the name of the variable
     */

    public void init(Expression select, StructuredQName qName) {
        this.select = select;
        variableQName = qName;
        setBody(select);
    }

    /**
     * Get the symbolic name of the component
     *
     * @return the symbolic name
     */
    @Override
    public SymbolicName getSymbolicName() {
        return new SymbolicName(StandardNames.XSL_VARIABLE, variableQName);
    }

    /**
     * Set the expression to which this variable is bound
     *
     * @param select the initializing expression. May be null in the case of a global parameter with no initializer.
     */

    public void setSelectExpression(Expression select) {
        this.select = select;
        setBody(select);
    }

    /**
     * Get the expression to which this variable is bound
     *
     * @return the initializing expression
     */

    public Expression getSelectExpression() {
        return select;
    }

    /**
     * Set the required type of this variable
     *
     * @param required the required type
     */

    public void setRequiredType(SequenceType required) {
        requiredType = required;
    }

    /**
     * Get the required type of this variable
     *
     * @return the required type
     */

    public SequenceType getRequiredType() {
        return requiredType;
    }

    /**
     * Get the Configuration to which this Container belongs
     *
     * @return the Configuration
     */
    private Configuration getConfiguration() {
        return getPackageData().getConfiguration();
    }

    /**
     * Say that this (XQuery) variable is a copy of some originally declared variable. It's a
     * separate variable when imported into another module, but it retains the link
     * to the original.
     *
     * @param var the variable in the imported module from which this variable is derived
     */

    public void setOriginalVariable(GlobalVariable var) {
        originalVariable = var;
    }

    /**
     * Get the original declaration of this variable
     *
     * @return the variable in the imported module from which this variable is derived
     */

    public GlobalVariable getOriginalVariable() {
        return originalVariable;
    }

    /**
     * Get the original declaration of this variable, or its original declaration, transitively
     *
     * @return the real variable declaration in some transitively imported module from which this variable is
     *         ultimately derived
     */

    public GlobalVariable getUltimateOriginalVariable() {
        if (originalVariable == null) {
            return this;
        } else {
            return originalVariable.getUltimateOriginalVariable();
        }
    }

    /**
     * Say whether this variable is unused. Normally, unused variables are not
     * compiled. However, in XSLT with debugging enabled (that is, with compileWithTracing
     * set), dummy unused variables are created in respect of global variables that are
     * declared but never referenced. These variables are included in the list
     * of variables known to the Executable, but they are never evaluated, and do
     * not have slot numbers allocated in the bindery.
     * @param unused set to true if this global variable is to be marked as unused.
     */

    public void setUnused(boolean unused) {
        this.binderySlotNumber = -9234;
    }

    /**
     * Ask whether this variable is unused. Normally, unused variables are not
     * compiled. However, in XSLT with debugging enabled (that is, with compileWithTracing
     * set), dummy unused variables are created in respect of global variables that are
     * declared but never referenced. These variables are included in the list
     * of variables known to the Executable, but they are never evaluated, and do
     * not have slot numbers allocated in the bindery.
     * @return true if this global variable is marked as unused.
     */

    public boolean isUnused() {
        return this.binderySlotNumber == -9234;
    }

    /**
     * Ask whether this global variable is private
     *
     * @return true if this variable is private
     */

    public boolean isPrivate() {
        return isPrivate;
    }

    /**
     * Say whether this global variable is private
     *
     * @param b true if this variable is external
     */
    public void setPrivate(boolean b) {
        isPrivate = b;
    }

    /**
     * Indicate whether this variable is assignable using saxon:assign
     *
     * @param assignable true if this variable is assignable
     */

    public void setAssignable(boolean assignable) {
        isAssignable = assignable;
    }

    /**
     * Test whether it is permitted to assign to the variable using the saxon:assign
     * extension element. This will only be true if the extra attribute saxon:assignable="yes"
     * is present.
     */

    public final boolean isAssignable() {
        return isAssignable;
    }


    /**
     * Get the type of construct. This will either be the fingerprint of a standard XSLT instruction name
     * (values in {@link net.sf.saxon.om.StandardNames}: all less than 1024)
     * or it will be a constant in class {@link LocationKind}.
     *
     * @return an integer identifying the kind of construct
     */
    public int getConstructType() {
        return StandardNames.XSL_VARIABLE;
    }

    /**
     * Get a name identifying the object of the expression, for example a function name, template name,
     * variable name, key name, element name, etc. This is used only where the name is known statically.
     *
     * @return the QName of the object declared or manipulated by this instruction or expression
     */
    public StructuredQName getObjectName() {
        return getVariableQName();
    }

    /**
     * Get the value of a particular property of the instruction. Properties
     * of XSLT instructions are generally known by the name of the stylesheet attribute
     * that defines them.
     *
     * @param name The name of the required property
     * @return The value of the requested property, or null if the property is not available
     */
    public Object getProperty(String name) {
        return null;
    }

    /**
     * Get an iterator over all the properties available. The values returned by the iterator
     * will be of type String, and each string can be supplied as input to the getProperty()
     * method to retrieve the value of the property. The iterator may return properties whose
     * value is null.
     *
     * @return an iterator over the properties.
     */
    public Iterator<String> getProperties() {
        List<String> list = Collections.emptyList();
        return list.iterator();
    }

    /**
     * Get the host language (XSLT, XQuery, XPath) used to implement the code in this container
     *
     * @return typically {@link net.sf.saxon.Configuration#XSLT} or {@link net.sf.saxon.Configuration#XQUERY}
     */

    public int getHostLanguage() {
        return getPackageData().getHostLanguage();
    }

    /**
     * Mark this as an indexed variable, to allow fast searching
     */

    public void setIndexedVariable() {
        indexed = true;
    }

    /**
     * Ask whether this is an indexed variable
     *
     * @return true if this variable is indexed
     */

    public boolean isIndexedVariable() {
        return indexed;
    }

    /**
     * The expression that initializes a global variable may itself use local variables.
     * In this case a stack frame needs to be allocated while evaluating the global variable
     *
     * @param map The stack frame map for local variables used while evaluating this global
     *            variable.
     */

    public void setContainsLocals(SlotManager map) {
        setStackFrameMap(map);
    }

    /**
     * Is this a global variable?
     *
     * @return true (yes, it is a global variable)
     */

    public boolean isGlobal() {
        return true;
    }

    /**
     * Register a variable reference that refers to this global variable
     *
     * @param ref the variable reference
     */
    public void registerReference(BindingReference ref) {
        references.add(ref);
    }

    /**
     * Iterate over the references to this variable
     *
     * @return an iterator over the references: returns objects of class {@link VariableReference}
     */

    public Iterator iterateReferences() {
        return references.iterator();
    }

    /**
     * Get the slot number allocated to this variable in the Bindery
     *
     * @return the slot number, that is the position allocated to the variable within the Bindery
     */

    public int getBinderySlotNumber() {
        return binderySlotNumber;
    }

    /**
     * Set the slot number of this variable in the Bindery
     *
     * @param s the slot number, that is, the position allocated to this variable within the Bindery
     */

    public void setBinderySlotNumber(int s) {
        if (!isUnused()) {
            binderySlotNumber = s;
        }
    }

    /**
     * Indicate that this variable represents a required parameter
     *
     * @param requiredParam true if this is a required parameter
     */

    public void setRequiredParam(boolean requiredParam) {
        this.isRequiredParam = requiredParam;
    }

    /**
     * Ask whether this variable represents a required parameter
     *
     * @return true if this variable represents a required parameter
     */

    public boolean isRequiredParam() {
        return this.isRequiredParam;
    }

    /**
     * Create a compiled representation of this global variable
     *
     * @param exec the executable
     * @param slot the slot number allocated to this variable
     * @throws XPathException if compile-time errors are found.
     */

    public void compile(/*@NotNull*/ Executable exec, int slot) throws XPathException {
        // Not used in XSLT
//        if (references.isEmpty()) {
//            return;
//        }
        TypeHierarchy th = getConfiguration().getTypeHierarchy();

        setBinderySlotNumber(slot);
        if (this instanceof GlobalParam) {
            setRequiredParam(select == null);
        }
        SequenceType type = getRequiredType();
        for (BindingReference ref : references) {
            ref.fixup(this);
            GroundedValue constantValue = null;
            int properties = 0;
            Expression select = getSelectExpression();
            if (select instanceof Literal && !(this instanceof GlobalParam)) {
                // we can't rely on the constant value because it hasn't yet been type-checked,
                // which could change it (eg by numeric promotion). Rather than attempt all the type-checking
                // now, we do a quick check. See test bug64
                int relation = th.relationship(select.getItemType(), type.getPrimaryType());
                if (relation == TypeHierarchy.SAME_TYPE || relation == TypeHierarchy.SUBSUMED_BY) {
                    constantValue = ((Literal) select).getValue();
                    type = SequenceType.makeSequenceType(SequenceTool.getItemType(constantValue, th), SequenceTool.getCardinality(constantValue));
                }
            }
            if (select != null) {
                properties = select.getSpecialProperties();
            }
            properties |= StaticProperty.NON_CREATIVE;
            // a variable reference is non-creative even if its initializer is creative
            ref.setStaticType(type, constantValue, properties);
        }
        //exec.registerGlobalVariable(this);
        if (isRequiredParam()) {
            exec.registerGlobalParameter((GlobalParam) this);
        }
    }


    /**
     * Type check the compiled representation of this global variable
     *
     * @param visitor an expression visitor
     * @throws XPathException if compile-time errors are found.
     */

    public void typeCheck(/*@NotNull*/ ExpressionVisitor visitor) throws XPathException {
        Expression value = getSelectExpression();
        if (value != null) {
            value.checkForUpdatingSubexpressions();
            if (value.isUpdatingExpression()) {
                throw new XPathException(
                        "Initializing expression for global variable must not be an updating expression", "XUST0001");
            }
            RoleDiagnostic role = new RoleDiagnostic(
                    RoleDiagnostic.VARIABLE, getVariableQName().getDisplayName(), 0);
            ContextItemStaticInfo cit = getConfiguration().makeContextItemStaticInfo(AnyItemType.getInstance(), true);
            Expression value2 = TypeChecker.strictTypeCheck(
                    value.simplify().typeCheck(visitor, cit),
                    getRequiredType(), role, visitor.getStaticContext());
            value2 = value2.optimize(visitor, cit);
            setSelectExpression(value2);
            // the value expression may declare local variables
            SlotManager map = getConfiguration().makeSlotManager();
            int slots = ExpressionTool.allocateSlots(value2, 0, map);
            if (slots > 0) {
                setContainsLocals(map);
            }

            if (getRequiredType() == SequenceType.ANY_SEQUENCE && !(this instanceof GlobalParam)) {
                // no type was declared; try to deduce a type from the value
                try {
                    final ItemType itemType = value.getItemType();
                    final int cardinality = value.getCardinality();
                    setRequiredType(SequenceType.makeSequenceType(itemType, cardinality));
                    GroundedValue constantValue = null;
                    if (value2 instanceof Literal) {
                        constantValue = ((Literal) value2).getValue();
                    }
                    for (BindingReference reference : references) {
                        if (reference instanceof VariableReference) {
                            ((VariableReference) reference).refineVariableType(
                                    itemType, cardinality, constantValue, value.getSpecialProperties());
                        }
                    }
                } catch (Exception err) {
                    // exceptions can happen because references to variables and functions are still unbound
                }
            }


        }
    }


    /**
     * Check for cycles in this variable definition
     *
     * @param referees              the calls leading up to this one; it's an error if this variable is on the
     *                              stack, because that means it calls itself directly or indirectly. The stack may contain
     *                              variable definitions (GlobalVariable objects) and user-defined functions (UserFunction objects).
     *                              It will never contain the same object more than once.
     * @param globalFunctionLibrary the library containing all global functions
     * @throws net.sf.saxon.trans.XPathException
     *          if cycles are found
     */

    public void lookForCycles(Stack<Object> referees, XQueryFunctionLibrary globalFunctionLibrary) throws XPathException {
        if (referees.contains(this)) {
            int s = referees.indexOf(this);
            referees.push(this);
            String message = "Circular definition of global variable: $" + getVariableQName().getDisplayName();

            for (int i = s; i < referees.size() - 1; i++) {
                if (i != s) {
                    message += ", which";
                }
                if (referees.get(i + 1) instanceof GlobalVariable) {
                    GlobalVariable next = (GlobalVariable) referees.get(i + 1);
                    message += " uses $" + next.getVariableQName().getDisplayName();
                } else if (referees.get(i + 1) instanceof XQueryFunction) {
                    XQueryFunction next = (XQueryFunction) referees.get(i + 1);
                    message += " calls " + next.getFunctionName().getDisplayName() +
                            "#" + next.getNumberOfArguments() + "()";
                }
            }
            message += '.';
            XPathException err = new XPathException(message);
            String errorCode;
            if (getPackageData().getHostLanguage() == Configuration.XSLT) {
                errorCode = "XTDE0640";
            } else {
                errorCode = "XQDY0054";
            }
            err.setErrorCode(errorCode);
            err.setIsStaticError(true);
            err.setLocation(getLocation());
            throw err;
        }
        if (select != null) {
            referees.push(this);
            List<Binding> list = new ArrayList<Binding>(10);
            ExpressionTool.gatherReferencedVariables(select, list);
            for (Binding b : list) {
                if (b instanceof GlobalVariable) {
                    ((GlobalVariable) b).lookForCycles(referees, globalFunctionLibrary);
                }
            }
            List<SymbolicName> flist = new ArrayList<SymbolicName>();
            ExpressionTool.gatherCalledFunctionNames(select, flist);
            for (SymbolicName s : flist) {
                XQueryFunction f = globalFunctionLibrary.getDeclarationByKey(s);
                if (!referees.contains(f)) {
                    // recursive function calls are allowed
                    lookForFunctionCycles(f, referees, globalFunctionLibrary);
                }
            }
            referees.pop();
        }
    }

    /**
     * Look for cyclic variable references that go via one or more function calls
     *
     * @param f                     a used-defined function
     * @param referees              a list of variables and functions that refer directly or indirectly to this variable
     * @param globalFunctionLibrary the library containing all global functions
     * @throws XPathException if an error occurs during evaluation
     */

    private static void lookForFunctionCycles(
            XQueryFunction f, Stack<Object> referees, XQueryFunctionLibrary globalFunctionLibrary) throws XPathException {
        Expression body = f.getBody();
        referees.push(f);
        List<Binding> list = new ArrayList<Binding>(10);
        ExpressionTool.gatherReferencedVariables(body, list);
        for (Binding b : list) {
            if (b instanceof GlobalVariable) {
                ((GlobalVariable) b).lookForCycles(referees, globalFunctionLibrary);
            }
        }
        List<SymbolicName> flist = new ArrayList<SymbolicName>();
        ExpressionTool.gatherCalledFunctionNames(body, flist);
        for (SymbolicName s : flist) {
            XQueryFunction qf = globalFunctionLibrary.getDeclarationByKey(s);
            if (!referees.contains(qf)) {
                // recursive function calls are allowed
                lookForFunctionCycles(qf, referees, globalFunctionLibrary);
            }
        }
        referees.pop();
    }

    /**
     * Evaluate the variable. That is,
     * get the value of the select expression if present or the content
     * of the element otherwise, either as a tree or as a sequence
     * @param context the dynamic evaluation context
     * @param target the component representing the variable to be evaluated
     * @return the value of the variable
     * @throws XPathException if a dynamic error occurs during evaluation
     */

    public Sequence getSelectValue(XPathContext context, Component target) throws XPathException {
        if (select == null) {
            throw new AssertionError("*** No select expression for global variable $" +
                    getVariableQName().getDisplayName() + "!!");
        } else if (select instanceof Literal) {
            // fast path for constant global variables
            return ((Literal)select).getValue();
        } else {
            try {
                XPathContextMajor c2 = context.newCleanContext();
                c2.setOrigin(this);
                if (target == null || target.getDeclaringPackage().isRootPackage()) {
                    ManualIterator mi = new ManualIterator(context.getController().getGlobalContextItem());
                    c2.setCurrentIterator(mi);
                } else {
                    c2.setCurrentIterator(null);
                }
                if (getStackFrameMap() != null) {
                    c2.openStackFrame(getStackFrameMap());
                }
                c2.setCurrentComponent(target);
                int savedOutputState = c2.getTemporaryOutputState();
                c2.setTemporaryOutputState(StandardNames.XSL_VARIABLE);
                c2.setCurrentOutputUri(null);
                Sequence result;
                if (indexed) {
                    result = c2.getConfiguration().makeSequenceExtent(select, FilterExpression.FILTERED, c2);
                } else {
                    result = SequenceExtent.makeSequenceExtent(select.iterate(c2));
                }
                c2.setTemporaryOutputState(savedOutputState);
                return result;
            } catch (XPathException e) {
                if (!getVariableQName().hasURI(NamespaceConstant.SAXON_GENERATED_VARIABLE)) {
                    e.setIsGlobalError(true);
                }
                throw e;
            }
        }
    }

    /**
     * Evaluate the variable
     */

    public Sequence evaluateVariable(XPathContext context) throws XPathException {
        final Controller controller = context.getController();
        assert controller != null;
        final Bindery b = controller.getBindery(getPackageData());

        final Sequence v = b.getGlobalVariable(getBinderySlotNumber());

        if (v != null) {
            return v;
        } else {
            return actuallyEvaluate(context, null);
        }
    }

    /**
     * Evaluate the variable
     */

    public Sequence evaluateVariable(XPathContext context, Component target) throws XPathException {
        final Controller controller = context.getController();
        assert controller != null;
        Bindery b = controller.getBindery(getPackageData());
        if (b == null) {
            // This is to handle those paths that haven't properly adjusted to multiple binderies...
            throw new AssertionError();
            //b = controller.getTopLevelBindery();
        }
        final Sequence v = b.getGlobalVariable(getBinderySlotNumber());

        if (v != null) {
            return v;
        } else {
            return actuallyEvaluate(context, target);
        }
    }

    /**
     * Evaluate the global variable, and save its value for use in subsequent references.
     *
     * @param context the XPath dynamic context
     * @param target the component representing this variable (in the context of a package where it is used)
     * @return the value of the variable
     * @throws XPathException if evaluation fails
     */

    protected Sequence actuallyEvaluate(XPathContext context, Component target) throws XPathException {
        final Controller controller = context.getController();
        assert controller != null;
        final Bindery b = controller.getBindery(getPackageData());
        //System.err.println("EVAL GV " + this + " " + getVariableQName().getDisplayName() + " in slot " + getBinderySlotNumber());

        try {
            // This is the first reference to a global variable; try to evaluate it now.
            // But first check for circular dependencies.
            setDependencies(this, context);

            // Set a flag to indicate that the variable is being evaluated. This is designed to prevent
            // (where possible) the same global variable being evaluated several times in different threads
            boolean go = b.setExecuting(this);
            if (!go) {
                // some other thread has evaluated the variable while we were waiting
                return b.getGlobalVariable(getBinderySlotNumber());
            }

            Sequence value = getSelectValue(context, target);
            if (indexed) {
                value = controller.getConfiguration().obtainOptimizer().makeIndexedValue(value.iterate());
            }
            return b.saveGlobalVariableValue(this, value);

        } catch (XPathException err) {
            b.setNotExecuting(this);
            if (err instanceof XPathException.Circularity) {
                String errorCode;
                if (getPackageData().getHostLanguage() == Configuration.XSLT) {
                    errorCode = "XTDE0640";
                } else {
                    errorCode = "XQDY0054";
                }
                err.setErrorCode(errorCode);
                err.setXPathContext(context);
                // Detect it more quickly the next time (in a pattern, the error is recoverable)
                SingletonClosure closure = new SingletonClosure(new ErrorExpression(err), context);
                b.setGlobalVariable(this, closure);
                err.setLocation(getLocation());
                throw err;
            } else {
                throw err;
            }
        }
    }

    /**
     * Get the variable that is immediately dependent on this one, and register the dependency, so
     * that circularities can be detected across threads. This relies on the fact that when the initialiser
     * for variable X contains a reference to variable Y, then when Y is evaluated, a stack frame will be found
     * on the context stack representing the evaluation of X. We don't set a dependency from X to Y if the value
     * of Y was already available in the Bindery; it's not needed, because in this case we know that evaluation
     * of Y is unproblematic, and can't lead to any circularities.
     *  @param var     the global variable or parameter being evaluated
     * @param context the dynamic evaluation context
     */

    protected static void setDependencies(GlobalVariable var, XPathContext context) throws XPathException {
        Controller controller = context.getController();
        if (!(context instanceof XPathContextMajor)) {
            context = getMajorCaller(context);
        }
        while (context != null) {
            do {
                ContextOriginator origin = ((XPathContextMajor) context).getOrigin();
                if (origin instanceof GlobalVariable) {
                    controller.registerGlobalVariableDependency((GlobalVariable) origin, var);
                    return;
                }
                context = getMajorCaller(context);
            } while (context != null);
        }
    }

    private static XPathContextMajor getMajorCaller(XPathContext context) {
        XPathContext caller = context.getCaller();
        while (!(caller == null || caller instanceof XPathContextMajor)) {
            caller = caller.getCaller();
        }
        return (XPathContextMajor) caller;
    }

    /**
     * If the variable is bound to an integer, get the minimum and maximum possible values.
     * Return null if unknown or not applicable
     *
     * @return a pair of integers containing the minimum and maximum values for the integer value;
     *         or null if the value is not an integer or the range is unknown
     */
    public IntegerValue[] getIntegerBoundsForVariable() {
        return select==null ? null : select.getIntegerBounds();
    }

    /**
     * If this is a local variable held on the local stack frame, return the corresponding slot number.
     * In other cases, return -1.
     *
     * @return the slot number on the local stack frame
     */
    public int getLocalSlotNumber() {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    /**
     * Set the name of the variable
     *
     * @param s the name of the variable (a QName)
     */

    public void setVariableQName(StructuredQName s) {
        variableQName = s;
    }

    /**
     * Get the name of the variable
     *
     * @return the name of the variable, as a structured QName
     */
    public StructuredQName getVariableQName() {
        return variableQName;
    }

    /**
     * Register a variable reference that refers to the variable bound in this expression
     *
     * @param ref
     * @param isLoopingReference - true if the reference occurs within a loop, such as the predicate
     *                           of a filter expression
     */
    public void addReference(VariableReference ref, boolean isLoopingReference) {
        // No action
    }

    /**
     * Get the kind of component that this represents, using integer constants such as
     * {@link net.sf.saxon.om.StandardNames#XSL_FUNCTION}
     */
    @Override
    public int getComponentKind() {
        return StandardNames.XSL_VARIABLE;
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied outputstream.
     *
     * @param presenter the expression presenter used to display the structure
     */
    @Override
    public void export(ExpressionPresenter presenter) throws XPathException {
        presenter.startElement(this instanceof GlobalParam ? "globalParam" : "globalVariable");
        presenter.emitAttribute("name", getVariableQName().getEQName());
        presenter.emitAttribute("type", getRequiredType().toString());
        presenter.emitAttribute("line", getLineNumber() + "");
        presenter.emitAttribute("module", getSystemId());
        if (getStackFrameMap() != null) {
            presenter.emitAttribute("slots", getStackFrameMap().getNumberOfVariables() + "");
        }
        if (getDeclaringComponent() != null) {
            Visibility vis = getDeclaringComponent().getVisibility();
            if (vis != null) {
                presenter.emitAttribute("visibility", vis.toString());
            }
        }
        String flags = getFlags();
        if (!flags.isEmpty()) {
            presenter.emitAttribute("flags", flags);
        }

        if ("JS".equals(presenter.getOption("target"))) {
            int targetVersion = presenter.getIntOption("targetVersion", 1);
            presenter.emitAttribute("jsAcceptor", getRequiredType().getPrimaryType().generateJavaScriptItemTypeAcceptor("XTTE0590", targetVersion));
            presenter.emitAttribute("jsCardCheck", Cardinality.generateJavaScriptChecker(getRequiredType().getCardinality()));
        }

        if (getSelectExpression() != null) {
            getSelectExpression().export(presenter);
        }
        presenter.endElement();
    }

    protected String getFlags() {
        String flags = "";
        if (isAssignable) {
            flags += "a";
        }
        if (indexed) {
            flags += "x";
        }
        if (isRequiredParam) {
            flags += "r";
        }
        return flags;
    }
}

