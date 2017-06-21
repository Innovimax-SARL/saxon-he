using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using Saxon.Api;

namespace TestRunner
{

public class Xslt30ResultsDocument : ResultsDocument {

    public Xslt30ResultsDocument(string rdir, Spec sp)
         : base(rdir, sp){
    }

    public override void writeResultFilePreamble(Processor processor, XdmNode catalog) {
        createWriter(processor);
        String today = DateTime.Now.ToString();
        //XdmNode outermost = (XdmNode)catalog.EnumerateAxis(XdmAxis.Child, new QName(FOTestSuiteDriver.CNS, "catalog")).Current;
        results.WriteStartElement("test-suite-result", "http://www.w3.org/2012/11/xslt30-test-results");
        results.WriteStartElement("submission");
        results.WriteAttributeString("anonymous", "false");

        results.WriteStartElement("created");
        results.WriteAttributeString("by", "Michael Kay");
        results.WriteAttributeString("email", "mike@saxonica.com");
        results.WriteAttributeString("organization", "Saxonica");
        results.WriteAttributeString("on", today);
        results.WriteEndElement();

        results.WriteStartElement("test-run");
		results.WriteAttributeString("testsuiteVersion", "3.0.1");
        results.WriteAttributeString("date-run", today);
        results.WriteEndElement();

        results.WriteStartElement("notes");
        results.WriteEndElement(); // notes

        results.WriteEndElement(); // submission

        results.WriteStartElement("product");

        results.WriteAttributeString("vendor", "Saxonica");
        results.WriteAttributeString("name", "Saxon-EEN");
        results.WriteAttributeString("version", processor.ProductVersion);
        results.WriteAttributeString("released", "false");
        results.WriteAttributeString("open-source", "false");
        results.WriteAttributeString("language", ((SpecAttr)spec.GetAttr()).svname);
        results.WriteEndElement(); //product

    }

}
}

