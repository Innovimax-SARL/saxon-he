////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.parser;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.LocalBinding;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.query.AnnotationList;
import net.sf.saxon.query.XQueryParser;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.z.IntSet;

import java.util.ArrayList;

/**
 * Dummy Parser extension for syntax in XPath that is accepted only in Saxon-PE and -EE. Primarily, this means
 * XPath 3.0 syntax associated with higher-order functions.
 */
public class ParserExtension {

    public ParserExtension() {
    }

    private void needHof(XPathParser p, String what) throws XPathException {
        p.grumble(what + " require support for higher-order-functions, which needs Saxon-PE or higher");
    }

    private void needExtension(XPathParser p, String what) throws XPathException {
        p.grumble(what + " require support for Saxon extensions, available in Saxon-PE or higher");
    }

    private void needUpdate(XPathParser p, String what) throws XPathException {
        p.grumble(what + " requires support for XQuery Update, available in Saxon-EE or higher");
    }

    /**
     * Parse a literal function item (function#arity). On entry, the function name and
     * the '#' token have already been read
     *
     * @param p the parser
     * @return an expression representing the function value
     * @throws XPathException in the event of a syntax error
     */

    public Expression parseNamedFunctionReference(XPathParser p) throws XPathException {
        needHof(p, "Named function references");
        return null;
    }

    /**
     * Parse the item type used for function items (XQuery 3.0 higher order functions)
     * Syntax (changed by WG decision on 2009-09-22):
     * function '(' '*' ') |
     * function '(' (SeqType (',' SeqType)*)? ')' 'as' SeqType
     * For backwards compatibility with Saxon 9.2 we allow the "*" to be omitted for the time being
     * The "function(" has already been read
     * @param annotations the list of annotation assertions for this function item type
     */

    public ItemType parseFunctionItemType(XPathParser p, AnnotationList annotations) throws XPathException {
        needHof(p, "Function item types");
        return null;
    }

    /**
     * Parse an ItemType within a SequenceType
     *
     * @return the ItemType after parsing
     * @throws XPathException if a static error is found
     */

    public ItemType parseExtendedItemType(XPathParser p) throws XPathException {
        Tokenizer t = p.getTokenizer();
        if (t.currentToken == Token.TILDE) {
            needExtension(p, "Type aliases");
        } else if (t.currentToken == Token.NODEKIND && t.currentTokenValue.equals("tuple")) {
            // Saxon 9.8 extension: tuple types
            needExtension(p, "Tuple types");
        } else if (t.currentToken == Token.NODEKIND && t.currentTokenValue.equals("union")) {
            // Saxon 9.8 extension: union types
            needExtension(p, "Inline union types");
        }
        return null;
    }


    /**
     * Parse a function argument. The special marker "?" is allowed and causes "null"
     * to be returned
     */

    public Expression  makeArgumentPlaceMarker(XPathParser p) throws XPathException {
        needHof(p, "Argument place-holders");
        return null;
    }

    /**
     * Parse an inline function
     * "function" "(" ParamList? ")" ("as" SequenceType)? EnclosedExpr
     * On entry, "function (" has already been read
     *
     * @throws XPathException if a syntax error is found
     */

    protected Expression parseInlineFunction(XPathParser p, AnnotationList annotations) throws XPathException {
        needHof(p, "Inline functions");
        return null;
    }

    public Expression parseSimpleInlineFunction(XPathParser p) throws XPathException {
        needExtension(p, "Simple inline functions");
        return null;
    }

    /**
     * Process a function call in which one or more of the argument positions are
     * represented as "?" placemarkers (indicating partial application or currying)
     *
     * @param offset       offset in the query source of the start of the expression
     * @param name         the function call (as if there were no currying)
     * @param args         the arguments (with EmptySequence in the placemarker positions)
     * @param placeMarkers the positions of the placemarkers    @return the curried function
     * @throws XPathException
     */

    public Expression makeCurriedFunction(
            XPathParser parser, int offset,
            StructuredQName name, Expression[] args, IntSet placeMarkers) throws XPathException {
        needHof(parser, "Partially applied functions");
        return null;
    }

    /**
     * When a variable reference occurs within an inline function, it might be a reference to a variable declared
     * outside the inline function (which needs to become part of the closure. This code looks for such an outer
     * variable
     *
     * @param qName               the name of the variable
     * @return a binding for the variable; this will typically be a binding to a newly added parameter
     * for the innermost function in which the variable reference appears. As a side effect, all the inline
     * functions between the declaration of the variable and its use will have this variable as an additional
     * parameter, each one bound to the corresponding parameter in the containing function.
     */

    public LocalBinding findOuterRangeVariable(XPathParser p, StructuredQName qName) {
        return null;
    }



    public Expression createDynamicCurriedFunction(XPathParser p, Expression functionItem, ArrayList<Expression> args, IntSet placeMarkers) throws XPathException {
        needHof(p, "Partial function application");
        return null;
    }

    /**
     * Parse a type alias declaration. Allowed only in Saxon-PE and higher
     *
     * @throws XPathException if parsing fails
     */

    public void parseTypeAliasDeclaration(XQueryParser p) throws XPathException {
        needExtension(p, "Type alias declarations");
    }

    /**
     * Parse the "declare revalidation" declaration.
     * Syntax: not allowed unless XQuery update is in use
     *
     * @throws XPathException if the syntax is incorrect, or is not allowed in this XQuery processor
     */

    public void parseRevalidationDeclaration(XQueryParser p) throws XPathException {
        needUpdate(p, "A revalidation declaration");
    }

    /**
     * Parse an updating function declaration (allowed in XQuery Update only)
     *
     * @throws XPathException if parsing fails or if updating functions are not allowed
     */

    public void parseUpdatingFunctionDeclaration(XQueryParser p) throws XPathException {
        needUpdate(p, "An updating function");
    }

    protected Expression parseExtendedExprSingle(XPathParser p) throws XPathException {
        return null;
    }


}
