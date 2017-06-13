////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.jdom;

import net.sf.saxon.Query;
import net.sf.saxon.trans.XPathException;
import org.jdom.Document;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;

import javax.xml.transform.sax.SAXSource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Variant of command line net.sf.saxon.Transform do build the source document
 * in JDOM and then proceed with the transformation. This class is provided largely for
 * testing purposes.
 */

public class JDOMQuery extends Query {

    /*@NotNull*/
    public List preprocess(List sources) throws XPathException {
        try {
            ArrayList jdomSources = new ArrayList(sources.size());
            for (Object source : sources) {
                SAXSource ss = (SAXSource) source;
                SAXBuilder builder = new SAXBuilder();
                Document doc = builder.build(ss.getInputSource());
                doc.setBaseURI(((SAXSource) source).getSystemId());
                JDOMDocumentWrapper jdom = new JDOMDocumentWrapper(doc, getConfiguration());
                jdomSources.add(jdom);
            }
            return jdomSources;
        } catch (JDOMException e) {
            throw new XPathException(e);
        } catch (IOException e) {
            throw new XPathException(e);
        }
    }

    public static void main(String[] args) {
        new JDOMQuery().doQuery(args, "JDOMQuery");
    }
}

