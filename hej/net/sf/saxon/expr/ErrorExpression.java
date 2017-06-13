////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;


/**
 * Error expression: this expression is generated when the supplied expression cannot be
 * parsed, and the containing element enables forwards-compatible processing. It defers
 * the generation of an error message until an attempt is made to evaluate the expression
 */

public class ErrorExpression extends Expression {

    private XPathException exception;
    private String message;
    private String errorCode;
    private boolean isTypeError;

    /**
     * This constructor is never executed, but it is used in the expression parser
     * as a dummy so that the Java compiler recognizes parsing methods as always returning
     * a non-null result.
     */
    public ErrorExpression() {
        this("Unspecified error", "XXXX9999", false);
    }

    /**
     * Create an ErrorExpression, which if evaluated, generates a dynamic error
     * @param message the error message
     * @param errorCode the error code
     * @param isTypeError true if this is a type error
     */

    public ErrorExpression(String message, String errorCode, boolean isTypeError) {
        this.message = message;
        this.errorCode = errorCode;
        this.isTypeError = isTypeError;
    }

    /**
     * Constructor taking an exception. Creating exceptions is expensive, so this
     * constructor should be used only if the exception object already exists.
     *
     * @param exception the error to be thrown when this expression is evaluated
     */

    public ErrorExpression(XPathException exception) {
        this.exception = exception;
        this.message = exception.getMessage();
        this.errorCode = exception.getErrorCodeLocalPart();
    }

    /**
     * Get the wrapped exception
     *
     * @return the exception to be thrown when the expression is evaluated
     */

    public XPathException getException() {
        return exception;
    }

    public boolean isTypeError() {
        return exception == null ? isTypeError : exception.isTypeError();
    }

    public String getMessage() {
        return exception == null ? message : exception.getMessage();
    }

    public String getErrorCodeLocalPart() {
        return exception == null ? errorCode : exception.getErrorCodeLocalPart();
    }

    /**
     * Type-check the expression.
     */

    /*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        return this;
    }

    /*@NotNull*/
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        return this;
    }

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is provided directly. The other methods will always be available
     * indirectly, using an implementation that relies on one of the other methods.
     *
     * @return the implementation method, for example {@link #ITERATE_METHOD} or {@link #EVALUATE_METHOD} or
     * {@link #PROCESS_METHOD}
     */
    @Override
    public int getImplementationMethod() {
        return EVALUATE_METHOD | ITERATE_METHOD;
    }

    /**
     * Evaluate the expression. This always throws the exception registered when the expression
     * was first parsed.
     */

    public Item evaluateItem(XPathContext context) throws XPathException {
        if (exception != null) {
            // copy the exception for thread-safety, because we want to add context information
            XPathException err = new XPathException(exception.getMessage());
            err.setLocation(getLocation());
            err.setErrorCodeQName(exception.getErrorCodeQName());
            err.setXPathContext(context);
            err.setIsTypeError(exception.isTypeError());
            throw err;
        } else {
            XPathException err = new XPathException(message);
            err.setLocation(getLocation());
            err.setErrorCode(errorCode);
            err.setXPathContext(context);
            err.setIsTypeError(isTypeError);
            throw err;
        }
    }

    /**
     * Iterate over the expression. This always throws the exception registered when the expression
     * was first parsed.
     */

    /*@NotNull*/
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        evaluateItem(context);
        return null;    // to fool the compiler
    }

    /**
     * Determine the data type of the expression, if possible
     *
     * @return Type.ITEM (meaning not known in advance)
     */

    /*@NotNull*/
    public ItemType getItemType() {
        return AnyItemType.getInstance();
    }

    /**
     * Determine the static cardinality
     */

    public int computeCardinality() {
        return StaticProperty.ALLOWS_ZERO_OR_MORE;
        // we return a liberal value, so that we never get a type error reported
        // statically
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings
     */

    /*@NotNull*/
    public Expression copy(RebindingMap rebindings) {
        if (exception != null) {
            ErrorExpression e2 = new ErrorExpression(exception);
            ExpressionTool.copyLocationInfo(this, e2);
            return e2;
        } else {
            ErrorExpression e2 = new ErrorExpression(message, errorCode, isTypeError);
            ExpressionTool.copyLocationInfo(this, e2);
            return e2;
        }
    }

    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     *
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     * The name will always be in the form of a lexical XML QName, and should match the name used
     * in export() output displaying the expression.
     */
    @Override
    public String getExpressionName() {
        return "errorExpr";
    }

    @Override
    public String toString() {
        return "error(\"" + message + "\")";
    }

    @Override
    public String toShortString() {
        return toString();
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    public void export(ExpressionPresenter destination) throws XPathException {
        destination.startElement("error", this);
        destination.emitAttribute("message", exception == null ? message : exception.getMessage());
        destination.emitAttribute("code", exception == null ? errorCode : exception.getErrorCodeLocalPart());
        destination.emitAttribute("isTypeErr", isTypeError?"0":"1");
        destination.endElement();
    }

}