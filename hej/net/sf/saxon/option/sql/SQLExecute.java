////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.sql;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.SimpleExpression;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.style.Compilation;
import net.sf.saxon.style.ComponentDeclaration;
import net.sf.saxon.style.ExtensionInstruction;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.ObjectValue;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;


/**
 * An sql:execute element in the stylesheet.
 * <p/>
 * For example:
 * <pre>
 *   &lt;sql:execute statement="{$statement}"
 *                 xsl:extension-element-prefixes="sql"/ &gt;
 *
 * </pre>
 *
 * @author Michael Kay
 */

public class SQLExecute extends ExtensionInstruction {

    Expression connection;
    Expression statement;


    public void prepareAttributes() throws XPathException {
        // Attributes for SQL-statement
        String statementAtt = getAttributeValue("", "statement");
        if (statementAtt == null) {
            reportAbsence("statement");
        }
        statement = makeAttributeValueTemplate(statementAtt, getAttributeList().getIndex("", "statement"));

        String connectAtt = getAttributeValue("", "connection");
        if (connectAtt == null) {
            reportAbsence("connection");
        } else {
            connection = makeExpression(connectAtt, getAttributeList().getIndex("", "connection"));
        }

    }

    public void validate(ComponentDeclaration decl) throws XPathException {
        super.validate(decl);
        statement = typeCheck("statement", statement);
        connection = typeCheck("connection", connection);
    }

    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        return new SqlStatementInstruction(connection, statement);
    }

    private static class SqlStatementInstruction extends SimpleExpression {

        public static final int CONNECTION = 0;
        public static final int STATEMENT = 1;

        public SqlStatementInstruction(Expression connection,
                                       Expression statement) {
            Expression[] sub = {connection, statement};
            setArguments(sub);
        }

        /**
         * A subclass must provide one of the methods evaluateItem(), iterate(), or process().
         * This method indicates which of the three is provided.
         */

        public int getImplementationMethod() {
            return Expression.PROCESS_METHOD;
        }

        /*@NotNull*/
        public String getExpressionType() {
            return "sql:statement";
        }

        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            Item conn = arguments[CONNECTION].head();
            if (!(conn instanceof ObjectValue && ((ObjectValue) conn).getObject() instanceof Connection)) {
                XPathException de = new XPathException("Value of connection expression is not a JDBC Connection");
                de.setXPathContext(context);
                throw de;
            }
            Connection connection = (Connection) ((ObjectValue) conn).getObject();

            String statementText = arguments[STATEMENT].head().getStringValue();

            try {
                if ("COMMIT WORK".equals(statementText)) {
                    connection.commit();
                } else if ("ROLLBACK WORK".equals(statementText)) {
                    connection.rollback();
                } else {
                    Statement s = connection.createStatement();
                    s.execute(statementText);
                }

            } catch (SQLException ex) {
                XPathException de = new XPathException("(SQL) " + ex.getMessage());
                de.setXPathContext(context);
                throw de;
            }

            return EmptySequence.getInstance();
        }
    }
}

