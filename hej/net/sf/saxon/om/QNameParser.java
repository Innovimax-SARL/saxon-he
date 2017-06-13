////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.query.XQueryParser;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Whitespace;

/**
 * Parser to handle QNames in either lexical QName or EQName syntax, including resolving any prefix against
 * a URIResolver. The parser can be instantiated with various options to control the returned error code,
 * the handling of defaults, etc.
 */
public class QNameParser {

    private NamespaceResolver resolver;
    private boolean acceptEQName = false;
    private String defaultNamespace = null;
    private String errorOnBadSyntax = "XPST0003";
    private String errorOnUnresolvedPrefix = "XPST0081";
    private XQueryParser.Unescaper unescaper = null;

    public QNameParser(NamespaceResolver resolver) {
        this.resolver = resolver;
    }

    public void setNamespaceResolver(NamespaceResolver resolver) {
        this.resolver = resolver;
    }

    public void setAcceptEQName(boolean acceptEQName) {
        this.acceptEQName = acceptEQName;
    }

    public void setUnescaper(XQueryParser.Unescaper unescaper) {
        this.unescaper = unescaper;
    }

    public void setDefaultNamespace(String defaultNamespace) {
        this.defaultNamespace = defaultNamespace;
    }

    public void setErrorOnBadSyntax(String code) {
        errorOnBadSyntax = code;
    }

    public void setErrorOnUnresolvedPrefix(String code) {
        errorOnUnresolvedPrefix = code;
    }

    /**
     * Make a structured QName from a lexical QName, using a supplied NamespaceResolver to
     * resolve the prefix. The local part of the QName is checked for validity; the prefix is
     * not checked, on the grounds that an invalid prefix will fail to resolve to a URI.
     *
     * @param lexicalName the QName as a lexical name (prefix:local)
     * @return the StructuredQName object corresponding to this lexical QName
     * @throws net.sf.saxon.trans.XPathException if the namespace prefix is not in scope or if the value is lexically
     *                        invalid. Error code FONS0004 is set if the namespace prefix has not been declared; error
     *                        code FOCA0002 is set if the name is lexically invalid. These may need to be
     *                        changed on return depending on the caller's requirements.
     */

    public StructuredQName parse(CharSequence lexicalName) throws XPathException {
        lexicalName = Whitespace.trimWhitespace(lexicalName);
        if (acceptEQName && lexicalName.length() >= 4 && lexicalName.charAt(0) == 'Q' && lexicalName.charAt(1) == '{') {
            String name = lexicalName.toString();
            int endBrace = name.indexOf('}');
            if (endBrace < 0) {
                throw new XPathException("Invalid EQName: closing brace not found", errorOnBadSyntax);
            } else if (endBrace == name.length() - 1) {
                throw new XPathException("Invalid EQName: local part is missing", errorOnBadSyntax);
            }
            String uri = name.substring(2, endBrace).toString();
            //String uri = Whitespace.collapseWhitespace(name.substring(2, endBrace)).toString();
            if (unescaper != null && uri.contains("&")) {
                uri = unescaper.unescape(uri).toString();
            }
            if (uri.equals(NamespaceConstant.XMLNS)) {
                throw new XPathException("The string '" + NamespaceConstant.XMLNS + "' cannot be used as a namespace URI", "XQST0070");
            }
            String local = name.substring(endBrace + 1);
            checkLocalName(local);
            return new StructuredQName("", uri, local);
        }
        try {
            String[] parts = NameChecker.getQNameParts(lexicalName);
            checkLocalName(parts[1]);
            if (parts[0].isEmpty()) {
                return new StructuredQName("", defaultNamespace, parts[1]);
            }
            String uri = resolver.getURIForPrefix(parts[0], false);
            if (uri == null) {
                throw new XPathException("Namespace prefix '" + parts[0] + "' has not been declared", errorOnUnresolvedPrefix);
            }
            return new StructuredQName(parts[0], uri, parts[1]);
        } catch (QNameException e) {
            throw new XPathException(e.getMessage(), errorOnBadSyntax);
        }
    }

    private void checkLocalName(String local) throws XPathException {
        if (!NameChecker.isValidNCName(local)) {
            throw new XPathException("Invalid EQName: local part is not a valid NCName", errorOnBadSyntax);
        }
    }

}

