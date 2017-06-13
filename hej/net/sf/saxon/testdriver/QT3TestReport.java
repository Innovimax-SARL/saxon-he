////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.testdriver;


import net.sf.saxon.Version;
import net.sf.saxon.s9api.*;
import net.sf.saxon.value.DateTimeValue;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.util.Map;

public class QT3TestReport extends TestReport {

    public QT3TestReport(TestDriver testDriver, Spec sp) {
        super(testDriver, sp);
    }

    public void writeResultFilePreamble(Processor processor, XdmNode catalog)
            throws IOException, SaxonApiException, XMLStreamException {
        String ns = getReportNamespace();
        createWriter(processor);
        String today = DateTimeValue.getCurrentDateTime(null).toDateValue().getStringValue().substring(0, 10);
        XdmNode outermost;
        XdmSequenceIterator iter = catalog.axisIterator(Axis.CHILD, new QName(QT3TestDriverHE.CNS, "catalog"));
        if (iter.hasNext()) {
            outermost = (XdmNode)iter.next();
        } else {
            throw new SaxonApiException("Outermost element of catalog file must be Q{" + QT3TestDriverHE.CNS + "}catalog");
        }
        results.writeStartElement(ns, "test-suite-result");
        results.writeDefaultNamespace(ns);
        results.writeStartElement(ns, "submission");
        results.writeAttribute("anonymous", "false");

        results.writeStartElement(ns, "created");
        results.writeAttribute("by", "Michael Kay");
        results.writeAttribute("email", "mike@saxonica.com");
        results.writeAttribute("organization", "Saxonica");
        results.writeAttribute("on", today);
        results.writeEndElement();

        results.writeStartElement(ns, "test-run");
        results.writeAttribute("test-suite-version", outermost.getAttributeValue(new QName("", "version")));
        results.writeAttribute("date-run", today);
        results.writeEndElement();

        results.writeStartElement(ns, "notes");
        results.writeEndElement(); // notes

        results.writeEndElement(); // submission

        results.writeStartElement(ns, "product");

        results.writeAttribute("vendor", "Saxonica");
        results.writeAttribute("name", testDriver.getProductEdition());
        results.writeAttribute("version", Version.getProductVersion());
        results.writeAttribute("released", "false");
        results.writeAttribute("open-source", "false");
        results.writeAttribute("language", spec.specAndVersion);
        results.writeAttribute("bytecode", testDriver.isByteCode()?"on":"off");


        Map<String, QT3TestDriverHE.Dependency> dependencyMap = ((QT3TestDriverHE) testDriver).getDependencyMap();
        if (!dependencyMap.isEmpty()) {
            for (Map.Entry<String, QT3TestDriverHE.Dependency> entry : dependencyMap.entrySet()) {
                QT3TestDriverHE.Dependency dep = entry.getValue();
                if (!"spec".equals(dep.dType)) {
                    results.writeStartElement(ns, "dependency");
                    results.writeAttribute("type", dep.dType);
                    results.writeAttribute("value", entry.getKey());
                    results.writeAttribute("satisfied", Boolean.toString(dep.satisfied));
                    results.writeEndElement(); //dependency
                }
            }
        }

        results.writeEndElement(); //product

    }

    @Override
    public String getReportNamespace() {
        return QT3TestDriverHE.RNS;
    }
}

