////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.sql;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.SimpleExpression;
import net.sf.saxon.expr.StringLiteral;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.style.Compilation;
import net.sf.saxon.style.ComponentDeclaration;
import net.sf.saxon.style.ExtensionInstruction;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.ObjectValue;
import net.sf.saxon.value.StringValue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * An sql:delete element in the stylesheet.
 *
 * @author Mathias Payer <mathias.payer@nebelwelt.net>
 * @author Michael Kay
 *         <p/>
 *         For example:
 *         <pre>
 *                   &lt;sql:delete connection="$connection" table="table-name" where="{$where}"
 *                                 xsl:extension-element-prefixes="sql" /&gt;
 *
 *                 </pre>
 */

public class SQLDelete extends ExtensionInstruction {

    Expression connection;
    String table;
    Expression where;

    public void prepareAttributes() throws XPathException {

        table = getAttributeList().getValue("", "table");
        if (table == null) {
            reportAbsence("table");
            table = "saxon-error-table";
        }
        table = SQLConnect.quoteSqlName(table);

        String dbWhere = getAttributeList().getValue("", "where");
        if (dbWhere == null) {
            where = new StringLiteral(StringValue.EMPTY_STRING);
        } else {
            where = makeAttributeValueTemplate(dbWhere, getAttributeList().getIndex("", "where"));
        }


        String connectAtt = getAttributeList().getValue("", "connection");
        if (connectAtt == null) {
            reportAbsence("connection");
        } else {
            connection = makeExpression(connectAtt, getAttributeList().getIndex("", "connection"));
        }
    }

    public void validate(ComponentDeclaration decl) throws XPathException {
        super.validate(decl);
        where = typeCheck("where", where);
        connection = typeCheck("connection", connection);
    }

    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        return new DeleteInstruction(connection, "DELETE FROM " + table, where);
    }

    private static class DeleteInstruction extends SimpleExpression {

        private static final long serialVersionUID = -4234440812734827279L;
        public static final int CONNECTION = 0;
        public static final int WHERE = 1;
        String statement;

        public DeleteInstruction(Expression connection, String statement, Expression where) {
            Expression[] sub = new Expression[2];
            sub[CONNECTION] = connection;
            sub[WHERE] = where;
            this.statement = statement;
            setArguments(sub);
        }

        /**
         * A subclass must provide one of the methods evaluateItem(), iterate(), or process().
         * This method indicates which of the three is provided.
         */

        public int getImplementationMethod() {
            return Expression.EVALUATE_METHOD;
        }

        public String getExpressionType() {
            return "sql:delete";
        }

        /*@Nullable*/
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            // Prepare the SQL statement (only do this once)

            Item conn = arguments[CONNECTION].head();
            if (!(conn instanceof ObjectValue && ((ObjectValue) conn).getObject() instanceof Connection)) {
                dynamicError("Value of connection expression is not a JDBC Connection", SaxonErrorCode.SXSQ0001, context);
            }
            Connection connection = (Connection) ((ObjectValue) conn).getObject();
            PreparedStatement ps = null;

            String dbWhere = arguments[WHERE].head().getStringValue();
            String localstmt = statement;

            if (!dbWhere.equals("")) {
                localstmt += " WHERE " + dbWhere;
            }

            try {
                ps = connection.prepareStatement(localstmt);

                ps.executeUpdate();
                if (!connection.getAutoCommit()) {
                    connection.commit();
                }

            } catch (SQLException ex) {
                dynamicError("SQL DELETE failed: " + ex.getMessage(), SaxonErrorCode.SXSQ0004, context);
            } finally {
                if (ps != null) {
                    try {
                        ps.close();
                    } catch (SQLException ignore) {
                    }
                }
            }
            return new EmptyAtomicSequence();
            //return null;
        }

    }


}

