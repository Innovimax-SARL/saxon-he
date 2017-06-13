////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.flwor;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.PathMap;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.om.NamespaceResolver;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;

/**
 * A "trace" clause in a FLWOR expression, added by a TraceCodeInjector for diagnostic
 * tracing, debugging, profiling or similar purposes.
 */
public class TraceClause extends Clause {

    private Clause target;
    private NamespaceResolver nsResolver;

    /**
     * Create a traceClause
     *
     * @param target    the clause whose evaluation is being traced
     */

    public TraceClause(Clause target, NamespaceResolver nsResolver) {
        this.target = target;
        this.nsResolver = nsResolver;
    }

    /**
     * Get the namespace bindings from the static context of the clause
     *
     * @return a namespace resolver that reflects the in scope namespaces of the clause
     */

    public NamespaceResolver getNamespaceResolver() {
        return nsResolver;
    }

    /**
     * Set the namespace bindings from the static context of the clause
     *
     * @param nsResolver a namespace resolver that reflects the in scope namespaces of the clause
     */

    public void setNamespaceResolver(NamespaceResolver nsResolver) {
        this.nsResolver = nsResolver;
    }


    @Override
    public int getClauseKey() {
        return TRACE;
    }

    public TraceClause copy(FLWORExpression flwor, RebindingMap rebindings) {
        return new TraceClause(target, nsResolver);
    }

    /**
     * Get a tuple stream that implements the functionality of this clause, taking its
     * input from another tuple stream which this clause modifies
     *
     * @param base    the input tuple stream
     * @param context the dynamic evaluation context
     * @return the output tuple stream
     */
    @Override
    public TuplePull getPullStream(TuplePull base, XPathContext context) {
        return new TraceClausePull(base, this, target);
    }

    /**
     * Get a push-mode tuple stream that implements the functionality of this clause, supplying its
     * output to another tuple stream
     *
     * @param destination the output tuple stream
     * @param context     the dynamic evaluation context
     * @return the push tuple stream that implements the functionality of this clause of the FLWOR
     *         expression
     */
    @Override
    public TuplePush getPushStream(TuplePush destination, XPathContext context) {
        return new TraceClausePush(destination, this, target);
    }

    /**
     * Process the subexpressions of this clause
     *
     * @param processor the expression processor used to process the subexpressions
     */
    @Override
    public void processOperands(OperandProcessor processor) throws XPathException {
    }

    @Override
    public void addToPathMap(PathMap pathMap, PathMap.PathMapNodeSet pathMapNodeSet) {
        // no action
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     *
     * @param out the expression presenter used to display the structure
     */
    @Override
    public void explain(ExpressionPresenter out) throws XPathException {
        out.startElement("trace");
        out.endElement();
    }

    public String toString() {
        return "trace";
    }
}

