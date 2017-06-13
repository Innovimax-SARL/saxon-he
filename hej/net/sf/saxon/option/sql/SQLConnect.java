////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.sql;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.style.Compilation;
import net.sf.saxon.style.ComponentDeclaration;
import net.sf.saxon.style.ExtensionInstruction;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.ObjectValue;
import net.sf.saxon.value.StringValue;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.regex.Pattern;

/**
 * An sql:connect element in the stylesheet.
 */

public class SQLConnect extends ExtensionInstruction {

    Expression database;
    Expression driver;
    Expression user;
    Expression password;
    Expression autoCommit = Literal.makeEmptySequence();

    public boolean mayContainSequenceConstructor() {
        return false;
    }

    public void prepareAttributes() throws XPathException {

        // Get mandatory database attribute

        String dbAtt = getAttributeValue("", "database");
        if (dbAtt == null) {
            reportAbsence("database");
            dbAtt = ""; // for error recovery
        }
        database = makeAttributeValueTemplate(dbAtt, getAttributeList().getIndex("", "database"));

        // Get driver attribute

        String dbDriver = getAttributeValue("", "driver");
        if (dbDriver == null) {
            if (dbAtt.length() > 9 && dbAtt.substring(0, 9).equals("jdbc:odbc")) {
                dbDriver = "sun.jdbc.odbc.JdbcOdbcDriver";
            } else {
                reportAbsence("driver");
            }
        }
        driver = makeAttributeValueTemplate(dbDriver, getAttributeList().getIndex("", "driver"));


        // Get and expand user attribute, which defaults to empty string

        String userAtt = getAttributeValue("", "user");
        if (userAtt == null) {
            user = new StringLiteral(StringValue.EMPTY_STRING);
        } else {
            user = makeAttributeValueTemplate(userAtt, getAttributeList().getIndex("", "user"));
        }

        // Get and expand password attribute, which defaults to empty string

        String pwdAtt = getAttributeValue("", "password");
        if (pwdAtt == null) {
            password = new StringLiteral(StringValue.EMPTY_STRING);
        } else {
            password = makeAttributeValueTemplate(pwdAtt, getAttributeList().getIndex("", "password"));
        }

        // Get auto-commit attribute if specified

        String autoCommitAtt = getAttributeValue("", "auto-commit");
        if (autoCommitAtt != null) {
            autoCommit = makeAttributeValueTemplate(autoCommitAtt, getAttributeList().getIndex("", "auto-commit"));
        }
    }

    public void validate(ComponentDeclaration decl) throws XPathException {
        super.validate(decl);
        getConfiguration().checkLicensedFeature(Configuration.LicenseFeature.PROFESSIONAL_EDITION,
                                                "sql:connect",
                                                getPackageData().getLocalLicenseId());
        database = typeCheck("database", database);
        driver = typeCheck("driver", driver);
        user = typeCheck("user", user);
        password = typeCheck("password", password);
        autoCommit = typeCheck("auto-commit", autoCommit);
    }

    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        return new ConnectInstruction(database, driver, user, password, autoCommit);
    }

    public static class ConnectInstruction extends SimpleExpression {

        public static final int DATABASE = 0;
        public static final int DRIVER = 1;
        public static final int USER = 2;
        public static final int PASSWORD = 3;
        public static final int AUTOCOMMIT = 4;

        private ConnectInstruction() {
            // provided for the benefit of copy()
        }

        public ConnectInstruction(Expression database,
                                  Expression driver, Expression user, Expression password, Expression autoCommit) {

            Expression[] subs = {database, driver, user, password, autoCommit};
            setArguments(subs);
        }

        /**
         * A subclass must provide one of the methods evaluateItem(), iterate(), or process().
         * This method indicates which of the three is provided.
         */

        public int getImplementationMethod() {
            return Expression.EVALUATE_METHOD;
        }

        public int computeCardinality() {
            return StaticProperty.EXACTLY_ONE;
        }

        public String getExpressionType() {
            return "sql:connect";
        }

        public ConnectInstruction copy(RebindingMap rebindings) {
            return (ConnectInstruction)new ConnectInstruction().copyOperandsFrom(this);
        }

        public ObjectValue<Connection> call(XPathContext context, Sequence[] arguments) throws XPathException {

            // Establish the JDBC connection

            Connection connection = null;      // JDBC Database Connection

            String dbString = str(arguments[DATABASE]);
            String dbDriverString = str(arguments[DRIVER]);
            String userString = str(arguments[USER]);
            String pwdString = str(arguments[PASSWORD]);
            String autoCommitString = str(arguments[AUTOCOMMIT]);

            try {
                // the following hack is necessary to load JDBC drivers
                Class.forName(dbDriverString);
            } catch (ClassNotFoundException e) {
                XPathException err = new XPathException("Failed to load JDBC driver " + dbDriverString, e);
                err.setXPathContext(context);
                err.setErrorCode(SaxonErrorCode.SXSQ0003);
                err.setLocation(getLocation());
                throw err;
            }
            try {
                if (context.getConfiguration().isTiming()) {
                    DriverManager.setLogWriter(new PrintWriter(System.err));
                }
                connection = DriverManager.getConnection(dbString, userString, pwdString);
            } catch (SQLException ex) {
                XPathException err = new XPathException("JDBC Connection Failure", ex);
                err.setXPathContext(context);
                err.setErrorCode(SaxonErrorCode.SXSQ0003);
                err.setLocation(getLocation());
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
                err.setLocation(getLocation());
                throw err;
            }

            return new ObjectValue<Connection>(connection);

        }

        private String str(/*@NotNull*/ Sequence iterator) throws XPathException {
            Item item = iterator.head();
            return item == null ? "" : item.getStringValue();
        }
    }

    /**
     * Utility method to quote a SQL table or column name if it needs quoting.
     *
     * @param name the supplied name
     * @return the supplied name, enclosed in double quotes if it does not satisfy the pattern [A-Za-z_][A-Za-z0-9_]*,
     *         with any double quotes replaced by two double quotes
     */

    public static String quoteSqlName(String name) {
        // TODO: allow an embedded double-quote to be escaped as two double-quotes
        if (namePattern.matcher(name).matches()) {
            return name;
        }
        return "\"" + name + "\"";
    }

    private static Pattern namePattern = Pattern.compile("\"[^\"]+\"|[A-Za-z_][A-Za-z0-9_]*");
}

