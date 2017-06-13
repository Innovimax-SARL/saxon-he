////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.sql;

import net.sf.saxon.expr.*;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.style.Compilation;
import net.sf.saxon.style.ComponentDeclaration;
import net.sf.saxon.style.ExtensionInstruction;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.ObjectValue;
import net.sf.saxon.value.Whitespace;

import java.sql.Connection;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * An sql:insert element in the stylesheet.
 */

public class SQLInsert extends ExtensionInstruction {

    Expression connection;
    String table;

    public void prepareAttributes() throws XPathException {

        table = getAttributeList().getValue("", "table");
        // TODO: allow table to be an AVT
        if (table == null) {
            reportAbsence("table");
        }
        table = SQLConnect.quoteSqlName(table);

        String connectAtt = getAttributeList().getValue("", "connection");
        if (connectAtt == null) {
            reportAbsence("connection");
        } else {
            connection = makeExpression(connectAtt, getAttributeList().getIndex("", "connection"));
        }
    }

    public void validate(ComponentDeclaration decl) throws XPathException {
        super.validate(decl);
        connection = typeCheck("connection", connection);
        AxisIterator kids = iterateAxis(AxisInfo.CHILD);
        NodeInfo curr;
        while ((curr = kids.next()) != null) {
            if (curr instanceof SQLColumn) {
                // OK
            } else if (curr.getNodeKind() == Type.TEXT && Whitespace.isWhite(curr.getStringValueCS())) {
                // OK
            } else {
                compileError("Only sql:column is allowed as a child of sql:insert", "XTSE0010");
            }
        }

    }

    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {

        // Collect names of columns to be added

        StringBuffer statement = new StringBuffer(120);
        statement.append("INSERT INTO " + table + " (");

        AxisIterator kids = iterateAxis(AxisInfo.CHILD);
        NodeInfo child;
        int cols = 0;
        while ((child = kids.next()) != null) {
            if (child instanceof SQLColumn) {
                if (cols++ > 0) {
                    statement.append(',');
                }
                String colname = ((SQLColumn) child).getColumnName();
                statement.append(colname);
            }
        }
        statement.append(") VALUES (");

        // Add "?" marks for the variable parameters

        for (int i = 0; i < cols; i++) {
            if (i != 0) {
                statement.append(',');
            }
            statement.append('?');
        }

        statement.append(')');

        return new InsertInstruction(connection, statement.toString(), getColumnInstructions(exec, decl));
    }

    /*@NotNull*/
    public List<Expression> getColumnInstructions(Compilation exec, ComponentDeclaration decl) throws XPathException {
        List<Expression> list = new ArrayList<Expression>(10);

        AxisIterator kids = iterateAxis(AxisInfo.CHILD);
        NodeInfo child;
        while ((child = kids.next()) != null) {
            if (child instanceof SQLColumn) {
                list.add(((SQLColumn) child).compile(exec, decl));
            }
        }

        return list;
    }

    private static class InsertInstruction extends SimpleExpression {

        public static final int CONNECTION = 0;
        public static final int FIRST_COLUMN = 1;
        String statement;

        public InsertInstruction(Expression connection, String statement, List<Expression> columnInstructions) {
            Expression[] sub = new Expression[columnInstructions.size() + 1];
            sub[CONNECTION] = connection;
            for (int i = 0; i < columnInstructions.size(); i++) {
                sub[i + FIRST_COLUMN] = columnInstructions.get(i);
            }
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
            return "sql:insert";
        }

//        public Expression promote(PromotionOffer offer) throws XPathException {
//            if (offer.action != PromotionOffer.FOCUS_INDEPENDENT && offer.action != PromotionOffer.EXTRACT_GLOBAL_VARIABLES) {
//                // TODO: See bug 1971291, test case sql002. Suppressing the promotion of sql:column instructions is a rather
//                // crude solution to the bug; a better approach would be to allow them to be promoted, and avoid the assumption
//                // that the arguments at run-time will always be SQLColumn instructions. Also, it would be nice to get
//                // rid of the type hierarchy abuse: SQLColumn should not really extend GeneralVariable. See also similar
//                // logic in SQLUpdate.
//                return super.promote(offer);
//            } else {
//                return this;
//            }
//        }

        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {

            // Prepare the SQL statement (only do this once)

            Item conn = arguments[CONNECTION].head();
            if (!(conn instanceof ObjectValue && ((ObjectValue) conn).getObject() instanceof Connection)) {
                dynamicError("Value of connection expression is not a JDBC Connection", SaxonErrorCode.SXSQ0001, context);
            }
            Connection connection = (Connection) ((ObjectValue) conn).getObject();
            PreparedStatement ps = null;

            try {
                ps = connection.prepareStatement(statement);
                ParameterMetaData metaData = ps.getParameterMetaData();

                // Add the actual column values to be inserted

                int i = 1;
                for (int c = FIRST_COLUMN; c < arguments.length; c++) {
                    AtomicValue v = (AtomicValue) arguments[c].head();
                    String parameterClassName = null;
                    try {
                        parameterClassName = metaData.getParameterClassName(c);
                    }catch(SQLException ex) {
                        parameterClassName = "java.lang.String";
                    }
                    Object value;
                    if (parameterClassName.equals("java.lang.String")) {
                        value = v.getStringValue();
                    } else if (parameterClassName.equals("java.sql.Date")) {
                        value = java.sql.Date.valueOf(v.getStringValue());
                    } else {
                        try {
                            Class targetClass = Class.forName(parameterClassName);
                            PJConverter converter = PJConverter.allocate(context.getConfiguration(), v.getPrimitiveType(), StaticProperty.ALLOWS_ONE, targetClass);
                            value = converter.convert(v, targetClass, context);
                        } catch (ClassNotFoundException err) {
                            throw new XPathException("xsl:insert - cannot convert value to required class " + parameterClassName);
                        }
                    }

                    ps.setObject(i++, value);

                }

                ps.executeUpdate();
                if (!connection.getAutoCommit()) {
                    connection.commit();
                }

            } catch (SQLException ex) {
                dynamicError("SQL INSERT failed: " + ex.getMessage(), SaxonErrorCode.SXSQ0004, context);
            } finally {
                if (ps != null) {
                    try {
                        ps.close();
                    } catch (SQLException ignore) {
                    }
                }
            }

            return EmptySequence.getInstance();
        }

    }


}

