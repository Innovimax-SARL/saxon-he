////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.sql;

import net.sf.saxon.Controller;
import net.sf.saxon.event.ReceiverOptions;
import net.sf.saxon.event.SequenceOutputter;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.SimpleExpression;
import net.sf.saxon.expr.StringLiteral;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.om.*;
import net.sf.saxon.style.Compilation;
import net.sf.saxon.style.ComponentDeclaration;
import net.sf.saxon.style.ExtensionInstruction;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.Untyped;
import net.sf.saxon.value.ObjectValue;
import net.sf.saxon.value.StringValue;

import java.sql.*;
import java.util.HashSet;
import java.util.Set;

/**
 * An sql:query element in the stylesheet.
 * <p/>
 * For example:
 * <pre>
 *   &lt;sql:query column="{$column}" table="{$table}" where="{$where}"
 *                 xsl:extension-element-prefixes="sql"/ &gt;
 *
 * </pre>
 * (result with HTML-table-output) <BR>
 * <pre>
 *   &lt;sql:query column="{$column}" table="{$table}" where="{$where}"
 *                 row-tag="TR" column-tag="TD"
 *                 separatorType="tag"
 *                 xsl:extension-element-prefixes="sql"/ &gt;
 * </pre>
 *
 * @author claudio.thomas@unix-ag.org (based on Michael Kay's SQLInsert.java)
 */

public class SQLQuery extends ExtensionInstruction {

    Expression connection;
    /**
     * selected column(s) to query
     */
    Expression column;
    /**
     * the table(s) to query in
     */
    Expression table;
    /**
     * conditions of query (can be omitted)
     */
    Expression where;

    String rowTag;
    /**
     * name of element to hold the rows
     */
    String colTag;
    /**
     * name of element to hold the columns
     */

    boolean disable = false;    // true means disable-output-escaping="yes"

    public void prepareAttributes() throws XPathException {
        // Attributes for SQL-statement
        String dbCol = getAttributeValue("", "column");
        if (dbCol == null) {
            reportAbsence("column");
        }
        column = makeAttributeValueTemplate(dbCol, getAttributeList().getIndex("", "column"));

        String dbTab = getAttributeValue("", "table");
        if (dbTab == null) {
            reportAbsence("table");
            dbTab = "saxon-dummy-table";
        }
        table = makeAttributeValueTemplate(dbTab, getAttributeList().getIndex("", "table"));

        String dbWhere = getAttributeValue("", "where");
        if (dbWhere == null) {
            where = new StringLiteral(StringValue.EMPTY_STRING);
        } else {
            where = makeAttributeValueTemplate(dbWhere, getAttributeList().getIndex("", "where"));
        }

        String connectAtt = getAttributeValue("", "connection");
        if (connectAtt == null) {
            reportAbsence("connection");
        } else {
            connection = makeExpression(connectAtt, getAttributeList().getIndex("", "connection"));
        }

        // Atributes for row & column element names

        rowTag = getAttributeValue("", "row-tag");
        if (rowTag == null) {
            rowTag = "#auto";
        }
        if (!"#auto".equals(rowTag) && !NameChecker.isValidNCName(rowTag)) {
            compileError("row-tag must not contain a colon");
        }

        colTag = getAttributeValue("", "column-tag");
        if (colTag == null) {
            colTag = "#auto";
        }
        if (!"#auto".equals(colTag) && !NameChecker.isValidNCName(colTag)) {
            compileError("column-tag must be a valid NCName");
        }
        // Attribute output-escaping
        String disableAtt = getAttributeValue("", "disable-output-escaping");
        if (disableAtt != null) {
            if (disableAtt.equals("yes")) {
                disable = true;
            } else if (disableAtt.equals("no")) {
                disable = false;
            } else {
                compileError("disable-output-escaping attribute must be either yes or no");
            }
        }

    }

    public void validate(ComponentDeclaration decl) throws XPathException {
        super.validate(decl);
        column = typeCheck("column", column);
        table = typeCheck("table", table);
        where = typeCheck("where", where);
        connection = typeCheck("connection", connection);
    }

    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        return new QueryInstruction(connection,
                column, table, where,
                rowTag, colTag, disable);
    }

    private static class QueryInstruction extends SimpleExpression {

        public static final int CONNECTION = 0;
        public static final int COLUMN = 1;
        public static final int TABLE = 2;
        public static final int WHERE = 3;
        String rowTag;
        String colTag;
        int options;

        public QueryInstruction(Expression connection,
                                Expression column,
                                Expression table,
                                Expression where,
                                String rowTag,
                                String colTag,
                                boolean disable) {
            Expression[] sub = {connection, column, table, where};
            setArguments(sub);
            this.rowTag = rowTag;
            this.colTag = colTag;
            this.options = (disable ? ReceiverOptions.DISABLE_ESCAPING : 0);
        }

        private QueryInstruction(){};

        /**
         * A subclass must provide one of the methods evaluateItem(), iterate(), or process().
         * This method indicates which of the three is provided.
         */

        public int getImplementationMethod() {
            return Expression.PROCESS_METHOD;
        }

        public String getExpressionType() {
            return "sql:query";
        }

        public Expression copy(RebindingMap rebindings) {
            QueryInstruction qi2 = new QueryInstruction();
            qi2.copyOperandsFrom(this);
            qi2.rowTag = rowTag;
            qi2.colTag = colTag;
            qi2.options = options;
            return qi2;
        }

        public Sequence call(XPathContext context, Sequence[] arguments /*@NotNull*/) throws XPathException {
            // Prepare the SQL statement (only do this once)

            Controller controller = context.getController();
            Item conn = arguments[CONNECTION].head();
            if (!(conn instanceof ObjectValue && ((ObjectValue) conn).getObject() instanceof Connection)) {
                XPathException de = new XPathException("Value of connection expression is not a JDBC Connection");
                de.setXPathContext(context);
                throw de;
            }
            Connection connection = (Connection) ((ObjectValue) conn).getObject();

            String dbCol = arguments[COLUMN].head().getStringValue();
            String dbTab = arguments[TABLE].head().getStringValue();
            String dbWhere = arguments[WHERE].head().getStringValue();


            Set<String> validNames = new HashSet<String>();
            NodeName rowCode;
            if (rowTag.equals("#auto")) {
                if (NameChecker.isValidNCName(dbTab)) {
                    rowCode = new NoNamespaceName(dbTab);
                } else {
                    rowCode = new NoNamespaceName("row");
                }
            } else {
                rowCode = new NoNamespaceName(rowTag);
            }
            NodeName colCode = ("#auto".equals(colTag) ? null : new NoNamespaceName(colTag));

            PreparedStatement ps = null;
            ResultSet rs = null;
            XPathException de = null;

            try {
                StringBuffer statement = new StringBuffer();
                statement.append("SELECT ").append(dbCol).append(" FROM ").append(dbTab);
                if (!dbWhere.equals("")) {
                    statement.append(" WHERE ").append(dbWhere);
                }
                //System.err.println("-> SQL: " + statement.toString());

                // -- Prepare the SQL statement
                ps = connection.prepareStatement(statement.toString());
                controller.setUserData(this.getLocation(), "sql:statement", ps);

                // -- Execute Statement
                rs = ps.executeQuery();

                // -- Get the column names
                ResultSetMetaData metaData = rs.getMetaData();

                // -- Print out Result
                SequenceOutputter out = context.getController().allocateSequenceOutputter(50);
                String result = "";
                int icol = rs.getMetaData().getColumnCount();
                while (rs.next()) {                            // next row
                    //System.out.print("<- SQL : "+ rowStart);
                    out.startElement(rowCode, Untyped.getInstance(), getLocation(), 0);
                    for (int col = 1; col <= icol; col++) {     // next column
                        // Read result from RS only once, because
                        // of JDBC-Specifications
                        result = rs.getString(col);
                        NodeName colName = colCode;
                        String sqlName = null;
                        boolean nameOK = false;
                        if (colName == null) {
                            sqlName = metaData.getColumnName(col);
                            if (validNames.contains(sqlName)) {
                                nameOK = true;
                            } else if (NameChecker.isValidNCName(sqlName)) {
                                nameOK = true;
                                validNames.add(sqlName);
                            }
                            if (nameOK) {
                                colName = new NoNamespaceName(sqlName);
                            } else {
                                colName = new NoNamespaceName("col");
                            }
                        } else {
                            nameOK = true;
                        }
                        out.startElement(colName, Untyped.getInstance(), getLocation(), 0);
                        if (sqlName != null && !nameOK) {
                            out.attribute(new NoNamespaceName("name"), BuiltInAtomicType.UNTYPED_ATOMIC, sqlName, getLocation(), 0);
                        }
                        if (result != null) {
                            out.characters(result, getLocation(), options);
                        }
                        out.endElement();
                    }
                    //System.out.println(rowEnd);
                    out.endElement();
                }
                //rs.close();

                if (!connection.getAutoCommit()) {
                    connection.commit();
                }
                return SequenceTool.toLazySequence(out.iterate());

            } catch (SQLException ex) {
                de = new XPathException("(SQL) " + ex.getMessage());
                de.setXPathContext(context);
                throw de;
            } finally {
                boolean wasDEThrown = (de != null);
                if (rs != null) {
                    try {
                        rs.close();
                    } catch (SQLException ex) {
                        de = new XPathException("(SQL) " + ex.getMessage());
                        de.setXPathContext(context);
                    }
                }
                if (ps != null) {
                    try {
                        ps.close();
                    } catch (SQLException ex) {
                        de = new XPathException("(SQL) " + ex.getMessage());
                        de.setXPathContext(context);
                    }
                }
                if (!wasDEThrown && de != null) {
                    throw de; // test so we don't lose the real exception
                }
            }
        }
    }
}

