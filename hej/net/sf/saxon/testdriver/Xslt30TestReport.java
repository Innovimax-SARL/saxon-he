////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.testdriver;


import net.sf.saxon.Version;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;

public class Xslt30TestReport extends TestReport {

    public static final String NS = "http://www.w3.org/2012/11/xslt30-test-results";

    public Xslt30TestReport(TestDriver testDriver, Spec sp) {
        super(testDriver, sp);
    }

    public void writeResultFilePreamble(Processor processor, XdmNode catalog)
            throws IOException, SaxonApiException, XMLStreamException {
        createWriter(processor);
        results.writeStartElement(NS, "test-suite-result");
        results.writeDefaultNamespace(NS);
        results.writeStartElement(NS, "implementation");
        results.writeAttribute("name", testDriver.getProductEdition());
        results.writeAttribute("version", Version.getProductVersion());
        results.writeAttribute("anonymous-result-column", "false");
        results.writeEmptyElement(NS, "organization");
        results.writeAttribute("name", "http://www.saxonica.com/");
        results.writeAttribute("anonymous", "false");
        results.writeEmptyElement(NS, "submitter");
        results.writeAttribute("name", "Michael Kay");
        results.writeAttribute("email", "mike@saxonica.com");
        //outputDiscretionaryItems();
        results.writeEmptyElement(NS, "configuration");
        results.writeAttribute("timeRun", getTime());
        results.writeAttribute("lang", testDriver.lang);
        results.writeAttribute("bytecode", testDriver.isByteCode()?"on":"off");


        results.writeEndElement(); //implementation
        results.writeEmptyElement(NS, "test-run");
        results.writeAttribute("dateRun", getDate());
        results.writeAttribute("testsuiteVersion", "3.0.1");



    }

    @Override
    public String getReportNamespace() {
        return NS;
    }
}

