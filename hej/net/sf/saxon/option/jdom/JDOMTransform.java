////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.jdom;

import net.sf.saxon.Transform;
import net.sf.saxon.trans.XPathException;
import org.jdom.Document;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.xml.sax.InputSource;

import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Variant of command line net.sf.saxon.Transform do build the source document
 * in JDOM and then proceed with the transformation. This class is provided largely for
 * testing purposes.
 */

public class JDOMTransform extends Transform {

    /*@NotNull*/
    public List<Source> preprocess(List<Source> sources) throws XPathException {
        try {
            ArrayList<Source> jdomSources = new ArrayList<Source>(sources.size());
            for (Source src : sources) {
                InputSource is;
                if (src instanceof SAXSource) {
                    SAXSource ss = (SAXSource) src;
                    is = ss.getInputSource();
                } else if (src instanceof StreamSource) {
                    StreamSource ss = (StreamSource) src;
                    if (ss.getInputStream() != null) {
                        is = new InputSource(ss.getInputStream());
                    } else if (ss.getReader() != null) {
                        is = new InputSource(ss.getReader());
                    } else {
                        is = new InputSource(ss.getSystemId());
                    }
                } else {
                    throw new IllegalArgumentException("Unknown kind of source");
                }
                is.setSystemId(src.getSystemId());
                SAXBuilder builder = new SAXBuilder();
                Document doc = builder.build(is);
                doc.setBaseURI(is.getSystemId());
                JDOMDocumentWrapper jdom = new JDOMDocumentWrapper(doc, getConfiguration());
                jdomSources.add(jdom.getRootNode());
            }
            return jdomSources;
        } catch (JDOMException e) {
            throw new XPathException(e);
        } catch (IOException e) {
            throw new XPathException(e);
        }
    }

    public static void main(String[] args) {
        new JDOMTransform().doTransform(args, "JDOMTransform");
    }
}

