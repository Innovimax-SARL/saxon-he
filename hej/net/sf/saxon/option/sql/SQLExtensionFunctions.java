////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.sql;

import net.sf.saxon.event.SequenceOutputter;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ExplicitLocation;
import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.ma.map.HashTrieMap;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.ma.map.MapType;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.FunctionItemType;
import net.sf.saxon.type.SpecificFunctionType;
import net.sf.saxon.type.Untyped;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.ObjectValue;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;

import java.io.PrintWriter;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;


public class SQLExtensionFunctions {

    // Dropped from the 9.7 release

    public static final String NAMESPACE = "http://saxon.sf.net/sql";

    // sql:connect($database as xs:string,$driver as xs:string,
    //             $user as xs:string, $password as xs:string, $auto-commit as xs:string)
    // as item()
    public static ObjectValue<Connection> connect(XPathContext context,
                                                  One<StringValue> database,
                                                  One<StringValue> driver,
                                                  One<StringValue> user,
                                                  One<StringValue> password,
                                                  One<StringValue> autoCommit) throws XPathException {
        // Establish the JDBC connection

        Connection connection = null;      // JDBC Database Connection

        String dbString = database.head().getStringValue();
        String dbDriverString = driver.head().getStringValue();
        String userString = user.head().getStringValue();
        String pwdString = password.head().getStringValue();
        String autoCommitString = autoCommit.head().getStringValue();

        try {
            // the following hack is necessary to load JDBC drivers
            Class.forName(dbDriverString);
        } catch (ClassNotFoundException e) {
            XPathException err = new XPathException("Failed to load JDBC driver " + dbDriverString, e);
            err.setXPathContext(context);
            err.setErrorCode(SaxonErrorCode.SXSQ0003);
            //err.setLocation(getLocation());
            throw err;
        }
        try {
            DriverManager.setLogWriter(new PrintWriter(System.err));
            connection = DriverManager.getConnection(dbString, userString, pwdString);
        } catch (SQLException ex) {
            XPathException err = new XPathException("JDBC Connection Failure", ex);
            err.setXPathContext(context);
            err.setErrorCode(SaxonErrorCode.SXSQ0003);
            //err.setLocation(getLocation());
            throw err;
        }

        try {
            if (autoCommitString.length() > 0) {
                connection.setAutoCommit("yes".equals(autoCommitString));
            }
        } catch (SQLException e) {
            XPathException err = new XPathException("Failed to set autoCommit on JDBC connection " + dbDriverString, e);
            err.setXPathContext(context);
            err.setErrorCode(SaxonErrorCode.SXSQ0003);
            //err.setLocation(getLocation());
            throw err;
        }

        return new ObjectValue<Connection>(connection);

    }

    /**
     * Forms an SQL query from subparts
     *
     * @param table   Database table to select from
     * @param columns columns to choose ("*" means all)
     * @param where   selection criteria
     * @return Completed SQL query string
     * @throws XPathException
     */
    public static StringValue queryString(One<StringValue> table,
                                          OneOrMore<Item> columns,
                                          ZeroOrOne<StringValue> where) throws XPathException {
        String dbCol = "";
        SequenceIterator i = columns.iterate();
        Item item;
        boolean first = true;
        while ((item = i.next()) != null) {
            if (!first) {
                dbCol += ",";
            }
            first = false;
            dbCol += item.getStringValue();
        }
        String dbTab = table.head().getStringValue();
        String dbWhere = (where.head() != null) ? where.head().getStringValue() : null;

        StringBuffer statement = new StringBuffer();
        statement.append("SELECT ").append(dbCol).append(" FROM ").append(dbTab);
        if (dbWhere != null && !dbWhere.equals("")) {
            statement.append(" WHERE ").append(dbWhere);
        }
        return new StringValue(statement);
    }

    // sql:query($conn as item(), $table as xs:string, $columns as xs:string+)
    // as element()*
    public static Sequence query(XPathContext context,
                                 Item conn,
                                 One<StringValue> table,
                                 OneOrMore<Item> columns) throws XPathException {
        return query(context, conn, table, columns, ZeroOrOne.<StringValue>empty());
    }

    // sql:query($conn as item(), $table as xs:string, $columns as xs:string+, $where as xs:string?)
    // as element()*
    public static Sequence query(XPathContext context,
                                 Item conn,
                                 One<StringValue> table,
                                 OneOrMore<Item> columns,
                                 ZeroOrOne<StringValue> where) throws XPathException {
        return query(context, conn, One.string(queryString(table, columns, where).getStringValue()));
    }

    // sql:query($conn as item(), $query as xs:string)
    // as element()*
    public static Sequence query(XPathContext context,
                                 Item conn,
                                 One<StringValue> query) throws XPathException {

        Connection connection = getConnection(context, conn);

        int options = 0;

        Set<String> validNames = new HashSet<String>();
        NodeName rowCode = new NoNamespaceName("row");
        NodeName colCode = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        XPathException de = null;
        Location loc = ExplicitLocation.UNKNOWN_LOCATION;
        try {
            // -- Prepare the SQL statement
            ps = connection.prepareStatement(query.getStringValue());
            //controller.setUserData(this, "sql:statement", ps);

            // -- Execute Statement
            rs = ps.executeQuery();

            // -- Get the column names
            ResultSetMetaData metaData = rs.getMetaData();

            // -- Print out Result
            SequenceOutputter out = context.getController().allocateSequenceOutputter(50);
            String result = "";
            int icol = rs.getMetaData().getColumnCount();
            while (rs.next()) {                            // next row
                out.startElement(rowCode, Untyped.getInstance(), loc, 0);
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
                    out.startElement(colName, Untyped.getInstance(), loc, 0);
                    if (sqlName != null && !nameOK) {
                        out.attribute(new NoNamespaceName("name"), BuiltInAtomicType.UNTYPED_ATOMIC, sqlName, loc, 0);
                    }
                    if (result != null) {
                        out.characters(result, loc, options);
                    }
                    out.endElement();
                }
                out.endElement();
            }

            if (!connection.getAutoCommit()) {
                connection.commit();
            }
            return SequenceTool.toLazySequence(out.iterate());

        } catch (SQLException ex) {
            de = new XPathException("(SQL) " + ex.getMessage());
            de.setXPathContext(context);
            throw de;
        } finally {
            boolean wasDEThrown = de != null;
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

    // sql:close($conn as item())
    // as ()
    public static Sequence close(XPathContext context,
                                 Item conn) throws XPathException {
        return closeLocal(context, getConnection(context, conn));
    }

    public static Sequence closeLocal(XPathContext context,
                                      Connection connection) throws XPathException {
        try {
            connection.close();
        } catch (SQLException ex) {
            XPathException de = new XPathException("(SQL) Failed to close connection: " + ex.getMessage());
            de.setErrorCode(SaxonErrorCode.SXSQ0002);
            de.setXPathContext(context);
            throw de;
        }
        return EmptyAtomicSequence.getInstance();
    }

    // sql:queryMap($conn as item(), $table as xs:string, $columns as xs:string+, $where as xs:string?)
    // as map()*
    public static ArrayList<MapItem> queryMap(XPathContext context,
                                              Item conn,
                                              One<StringValue> table,
                                              OneOrMore<Item> columns,
                                              ZeroOrOne<StringValue> where) throws XPathException {
        return queryMap(context, conn, One.string(queryString(table, columns, where).getStringValue()));
    }

    // sql:queryMap($conn as item(), $query as xs:string)
    // as map()*
    public static ArrayList<MapItem> queryMap(XPathContext context,
                                              Item conn,
                                              One<StringValue> query) throws XPathException {

        return queryMapLocal(context, getConnection(context, conn), query);
    }

    public static ArrayList<MapItem> queryMapLocal(XPathContext context,
                                                   Connection connection,
                                                   One<StringValue> query) throws XPathException {

        PreparedStatement ps = null;
        ResultSet rs = null;
        XPathException de = null;

        ArrayList<MapItem> result = new ArrayList<MapItem>();

        try {
            // -- Prepare the SQL statement
            ps = connection.prepareStatement(query.getStringValue());
            // -- Execute Statement
            rs = ps.executeQuery();

            // -- Get the column names
            ResultSetMetaData metaData = rs.getMetaData();

            int icol = rs.getMetaData().getColumnCount();
            while (rs.next()) {                            // next row
                HashTrieMap map = new HashTrieMap();
                for (int col = 1; col <= icol; col++) {     // next column
                    // Read result from RS only once, because
                    // of JDBC-Specifications
                    map = map.addEntry(new StringValue(metaData.getColumnName(col)),
                            new StringValue(rs.getString(col)));
                }
                result.add(map);
            }

            if (!connection.getAutoCommit()) {
                connection.commit();
            }
            return result;
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

    private static Connection getConnection(XPathContext context, Item conn) throws XPathException {
        if (!(conn instanceof ObjectValue && ((ObjectValue) conn).getObject() instanceof Connection)) {
            XPathException de = new XPathException("Value of connection expression is not a JDBC Connection");
            de.setXPathContext(context);
            throw de;
        }
        return (Connection) ((ObjectValue) conn).getObject();
    }


    public static MapItem connectMap(XPathContext context,
                                     One<StringValue> database,
                                     One<StringValue> driver,
                                     One<StringValue> user,
                                     One<StringValue> password,
                                     One<StringValue> autoCommit) throws XPathException {
        ObjectValue<Connection> connection = connect(context, database, driver, user, password, autoCommit);
        HashTrieMap map = new HashTrieMap();
        SystemFunction q = new QueryFunction(connection.getObject());
        map = map.addEntry(new StringValue("query", BuiltInAtomicType.STRING), q);
        SystemFunction c = new CloseFunction(connection.getObject());
        map = map.addEntry(new StringValue("close", BuiltInAtomicType.STRING), c);
        return map;
    }

    private static class QueryFunction extends SystemFunction {
        Connection connection;

        QueryFunction(Connection conn) {
            connection = conn;
            //setOperanda(new OperandArray(null, new Expression[1]));
        }
        @Override
        public StructuredQName getFunctionName() {
            return new StructuredQName("", null, "SQLquery");
        }
        public FunctionItemType getFunctionItemType() {
            return MapType.ANY_MAP_TYPE;
        }


        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            AtomicValue sv;
            if (getArity() == 0) {
                final Item contextItem = context.getContextItem();
                if (contextItem == null) {
                    throw new XPathException("The context item for sql:query() is not set", "XPDY0002", context);
                }
                sv = StringValue.makeStringValue(contextItem.getStringValueCS());
            } else {
                sv = (AtomicValue) arguments[0].head();
            }
            if (sv == null) {
                throw new XPathException("The query for sql:query() must not be empty", "XPDY0002", context);
                //return null;
            }
            String s = sv.getStringValue();
            ArrayList<MapItem> a = queryMapLocal(context, connection, One.string(s));
            Item[] array = a.toArray(new Item[1]);
            return new ZeroOrMore(array);
        }
    }

    private static FunctionItemType nullFunctionType = new SpecificFunctionType(
            new SequenceType[0],
            SequenceType.EMPTY_SEQUENCE);

    private static class CloseFunction extends SystemFunction {
        Connection connection;

        CloseFunction(Connection conn) {
            connection = conn;
            //setOperanda(new OperandArray(null, new Expression[0]));
        }
        @Override
        public StructuredQName getFunctionName() {
            return new StructuredQName("", null, "SQLclose");
        }
        public FunctionItemType getFunctionItemType() {
            return nullFunctionType;
        }


        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            return closeLocal(context, connection);
        }
    }
}

