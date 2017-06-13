////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.testdriver;

import net.sf.saxon.s9api.*;
import net.sf.saxon.value.DateTimeValue;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.*;

public abstract class TestReport {
    protected TestDriver testDriver;
    protected Spec spec;
    protected XMLStreamWriter results;
    protected Writer writer;

    public TestReport(TestDriver testDriver, Spec sp) {
        this.testDriver = testDriver;
        this.spec = sp;
    }

    public void createWriter(Processor processor) throws IOException, SaxonApiException {
        Writer resultWriter = new BufferedWriter(new FileWriter(new File(testDriver.getResultsDir() + "/results"
                + "_" + spec.fullName + "_" + testDriver.getProductVersion() + ".xml")));
        Serializer serializer = processor.newSerializer(resultWriter);
        serializer.setOutputProperty(Serializer.Property.METHOD, "xml");
        serializer.setOutputProperty(Serializer.Property.INDENT, "yes");
        if (!processor.getSaxonEdition().equals("HE")) {
            serializer.setOutputProperty(Serializer.Property.SAXON_LINE_LENGTH, "120");
        }
        this.writer = resultWriter;
        this.results = serializer.getXMLStreamWriter();
    }

    public abstract void writeResultFilePreamble(Processor processor, XdmNode catalog) throws IOException, SaxonApiException, XMLStreamException;

    public abstract String getReportNamespace();

    public void writeResultFilePostamble() {
        try {
            this.results.writeEndElement();
            this.results.close();
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void startTestSetElement(XdmNode funcSetNode) {
        try {
            results.writeStartElement(getReportNamespace(), "test-set");
            results.writeAttribute("name", funcSetNode.getAttributeValue(new QName("name")));
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }
    }

    public void endElement() {
        try {
            results.writeEndElement();
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }
    }

    public void writeTestcaseElement(String name, String part, String result, String comment) {
        try {
            this.results.writeEmptyElement(getReportNamespace(), "test-case");
            this.results.writeAttribute("name", name);
            this.results.writeAttribute("part", part);
            this.results.writeAttribute("result", result);
            if (comment != null) {
                this.results.writeAttribute("comment", comment);
            }
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }
    }

    public void writeTestcaseElement(String name, String result, String comment) {
        try {
            this.results.writeEmptyElement(getReportNamespace(), "test-case");
            this.results.writeAttribute("name", name);
            this.results.writeAttribute("result", result);
            if (comment != null) {
                this.results.writeAttribute("comment", comment);
            }
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getDate() {
        return DateTimeValue.getCurrentDateTime(null).getStringValue().substring(0, 10);
    }
    public static String getTime() {
        return DateTimeValue.getCurrentDateTime(null).getStringValue().substring(11);
    }


}