////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trace;

import net.sf.saxon.Configuration;
import net.sf.saxon.Controller;
import net.sf.saxon.PreparedStylesheet;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.StreamWriterToReceiver;
import net.sf.saxon.event.TransformerReceiver;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.FunctionCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.instruct.GlobalParameterSet;
import net.sf.saxon.expr.instruct.TraceExpression;
import net.sf.saxon.lib.Logger;
import net.sf.saxon.lib.StandardLogger;
import net.sf.saxon.lib.TraceListener;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.style.Compilation;
import net.sf.saxon.trans.CompilerInfo;
import net.sf.saxon.trans.XPathException;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Properties;
import java.util.Stack;

/**
 * A trace listener that records timing information for templates and functions, outputting this
 * information as an HTML report to a specified destination when the transformation completes.
 */

public class TimingTraceListener implements TraceListener {

    private int repeat = 0;

    private static class InstructionDetails {
        public InstructionInfo instruct;
        public long gross;
        public long net;
        public long count;
    }


    Logger out = new StandardLogger();
    private long t_total;
    /*@NotNull*/ private Stack<InstructionDetails> instructs = new Stack();
    /*@NotNull*/ HashMap<InstructionInfo, InstructionDetails> instructMap = new HashMap<InstructionInfo, InstructionDetails>();
    /*@Nullable*/ protected Configuration config = null;

    private InstructionInfo instructStack[] = new InstructionInfo[1500];
    private int stackDepth = 0;
    private int lang = Configuration.XSLT;

    /**
     * Set the PrintStream to which the output will be written.
     *
     * @param stream the PrintStream to be used for output. By default, the output is written
     *               to System.err.
     * @throws XPathException
     * @throws XMLStreamException
     */

    public void setOutputDestination(Logger stream) {
        out = stream;
    }

    /**
     * Called at start
     */

    public void open(/*@NotNull*/ Controller controller) {
        config = controller.getConfiguration();
        lang = controller.getExecutable().getHostLanguage();
        t_total = System.nanoTime();
    }

    /**
     * Called at end. This method builds the XML out and analyzed html output
     */

    public void close() {
        t_total = System.nanoTime() - t_total;
        repeat++;
        try {
            PreparedStylesheet sheet = this.getStyleSheet();
            Controller controller = sheet.newController();

            Properties props = new Properties();
            props.setProperty("method", "html");
            props.setProperty("indent", "yes");
            controller.setTraceListener(null);
            TransformerReceiver tr = new TransformerReceiver(controller);
            controller.initializeController(new GlobalParameterSet());
            tr.open();
            Receiver result = config.getSerializerFactory().getReceiver(out.asStreamResult(), controller.makePipelineConfiguration(), props);
            tr.setDestination(result);
            XMLStreamWriter writer = new StreamWriterToReceiver(tr);
            writer.writeStartDocument();

            writer.writeStartElement("trace");
            writer.writeAttribute("t-total", Double.toString((double) t_total / 1000000));
            for (InstructionDetails ins : instructMap.values()) {
                writer.writeStartElement("fn");
                String name = "UNKNOWN";
                if (ins.instruct.getObjectName() != null) {
                    name = ins.instruct.getObjectName().getDisplayName();
                    writer.writeAttribute("name", name);
                } else {
                    if (ins.instruct.getProperty("name") != null) {
                        name = ins.instruct.getProperty("name").toString();
                        writer.writeAttribute("name", name);
                    }
                }
                if (ins.instruct.getProperty("match") != null) {
                    name = ins.instruct.getProperty("match").toString();
                    writer.writeAttribute("match", name);
                }
                if (ins.instruct.getProperty("mode") != null) {
                    name = ins.instruct.getProperty("mode").toString();
                    writer.writeAttribute("mode", name);
                }
                writer.writeAttribute("construct", (ins.instruct.getConstructType() == StandardNames.XSL_FUNCTION ? "function" : (ins.instruct.getConstructType() == StandardNames.XSL_VARIABLE ? "variable" : "template")));
                String file = ins.instruct.getSystemId();
                if (file != null) {
                    /*if (file.length() > 15) {
                        file = "*" + file.substring(file.length() - 14);
                    }
                    writer.writeAttribute("file", "\"" + file + "\"");*/
                    writer.writeAttribute("file", file);
                }
                writer.writeAttribute("count", Long.toString(ins.count/ repeat)) ;
                writer.writeAttribute("t-sum-net", Double.toString((double) ins.net / repeat/ 1000000));
                writer.writeAttribute("t-avg-net", Double.toString((ins.net / (double) ins.count) / 1000000));
                writer.writeAttribute("t-sum", Double.toString((double) ins.gross / repeat/ 1000000));
                writer.writeAttribute("t-avg", Double.toString((ins.gross / (double) ins.count) / 1000000));
                writer.writeAttribute("line", Long.toString(ins.instruct.getLineNumber()));
                writer.writeEndElement();
            }
            writer.writeEndElement();
            writer.writeEndDocument();
            writer.close();
        } catch (XPathException e) {
            System.err.println("Unable to transform timing profile information: " + e.getMessage());
        } catch (TransformerException e) {
            System.err.println("Unable to render timing profile information: " + e.getMessage());
        } catch (XMLStreamException e) {
            System.err.println("Unable to generate timing profile information: " + e.getMessage());
        }
    }

    /**
     * Called when an instruction in the stylesheet gets processed
     */

    public void enter(/*@NotNull*/ InstructionInfo instruction, XPathContext context) {
        int loc = instruction.getConstructType();
        if (loc == StandardNames.XSL_FUNCTION || loc == StandardNames.XSL_TEMPLATE || loc == StandardNames.XSL_VARIABLE) {
            long start = System.nanoTime();
            InstructionDetails instructDetails = new InstructionDetails();
            instructDetails.instruct = instruction;
            instructDetails.gross = start;
            instructs.add(instructDetails);

            instructStack[stackDepth++] = instruction;
        }
    }

    /**
     * Called after an instruction of the stylesheet got processed
     */

    public void leave(/*@NotNull*/ InstructionInfo instruction) {
        int loc = instruction.getConstructType();
        if (loc == StandardNames.XSL_FUNCTION || loc == StandardNames.XSL_TEMPLATE || loc == StandardNames.XSL_VARIABLE) {
            InstructionDetails instruct = instructs.peek();
            long duration = System.nanoTime() - instruct.gross;
            long net = duration - instruct.net;
            instruct.net = net;
            instruct.gross = duration;
            InstructionDetails foundInstructDetails = instructMap.get(instruction);
            if (foundInstructDetails == null) {
                instruct.count = 1;
                instructMap.put(instruction, instruct);
                stackDepth--;
            } else {
                foundInstructDetails.count = foundInstructDetails.count + 1;
                boolean inStack = false;
                for (int i = 0; i < stackDepth - 1; i++) {
                    if (instructStack[i] == instruction) {
                        inStack = true;
                        break;
                    }
                }
                stackDepth--;
                if (!inStack) {
                    foundInstructDetails.gross = foundInstructDetails.gross + instruct.gross;
                }
                foundInstructDetails.net = foundInstructDetails.net + instruct.net;
            }
            instructs.pop();
            if (instructs.size() > 0) {
                InstructionDetails parentInstruct = instructs.peek();
                parentInstruct.net = parentInstruct.net + duration;
            }
        }
    }

    /**
     * Called when an item becomes current
     */

    public void startCurrentItem(Item item) {
    }

    /**
     * Called after a node of the source tree got processed
     */

    public void endCurrentItem(Item item) {
    }

    /**
     * Prepare Stylesheet to render the analyzed XML data out.
     * This method can be overridden in a subclass to produce the output in a different format.
     */
    /*@NotNull*/
    public PreparedStylesheet getStyleSheet() throws XPathException {
        String process = this.lang == Configuration.XSLT ? "Stylesheet" : "Query";
        String templateOr = this.lang == Configuration.XSLT ? "template, " : "";
        String templatesAnd = this.lang == Configuration.XSLT ? "templates and " : "";
        String xsl = "<?xml version='1.0' encoding='UTF-8'?>" +
                "<xsl:stylesheet xmlns:xsl='http://www.w3.org/1999/XSL/Transform' " +
                "xmlns:xs='http://www.w3.org/2001/XMLSchema' exclude-result-prefixes='xs' version='2.0'>" +
                "<xsl:template match='*'>" +
                "<html>" +
                "<head>" +
                "<title>Analysis of " + process + " Execution Time</title>" +
                "</head>" +
                "<body>" +
                "<h1>Analysis of " + process + " Execution Time</h1>" +
                "<p>Total time: <xsl:value-of select='format-number(@t-total, \"#0.000\")'/> milliseconds</p>" +
                "<h2>Time spent in each " + templateOr + "function or global variable:</h2>" +
                "<p>The table below is ordered by the total net time spent in the " + templateOr +
                "   function or global variable. Gross time means the time including called " + templatesAnd + "functions (recursive calls only count from the original entry);" +
                "  net time means time excluding time spent in called " + templatesAnd + "functions.</p>" +
                "<table border='border' cellpadding='10'>" +
                "   <thead>" +
                "      <tr>" +
                "         <th>file</th>" +
                "        <th>line</th>" +
                "       <th>instruction</th>" +
                "      <th>count</th>" +
                "     <th>average time (gross/ms)</th>" +
                "    <th>total time (gross/ms)</th>" +
                "   <th>average time (net/ms)</th>" +
                "  <th>total time (net/ms)</th>" +
                "</tr>" +
                "</thead>" +
                "<tbody>" +
                "   <xsl:for-each select='fn'>" +
                /*"<xsl:sort select='@file'/>" +
            "<xsl:sort select='@line'/>" +
            "<xsl:sort select='@name'/>" +
            "<xsl:sort select='@match'/>" +*/
                "  <xsl:sort select='number(@t-sum-net)' order='descending'/>" +
                "      <tr>" +
                "         <td>" +
                "            \"<xsl:value-of select='if(string-length(@file) gt 15) then \"*\" else (),substring(@file,string-length(@file) - 15)' separator=''/>\"" +
                "       </td>" +
                "      <td>" +
                "         <xsl:value-of select='@line'/>" +
                "    </td>" +
                "   <td>" +
                "      <xsl:value-of select='@construct, @name, @match'/>" +
                " </td>" +
                "<td align='right'>" +
                "    <xsl:value-of select=\"format-number(@count,',##0')\"/>" +
                "</td>" +
                "    <td align='right'>" +
                "       <xsl:value-of select=\"format-number(@t-avg, '#0.000')\"/>" +
                "  </td>" +
                " <td align='right'>" +
                "    <xsl:value-of select=\"format-number(@t-sum, ',##0.000')\"/>" +
                "</td>" +
                " <td align='right'>" +
                "    <xsl:value-of select=\"format-number(@t-avg-net, '#0.000')\"/>" +
                "</td>" +
                "<td align='right'>" +
                "   <xsl:value-of select=\"format-number(@t-sum-net, ',##0.000')\"/>" +
                " </td>" +
                "</tr>" +
                " </xsl:for-each>" +
                "</tbody>" +
                "</table>" +
                "</body>" +
                "</html>" +
                "</xsl:template>" +
                "</xsl:stylesheet>";

        Source styleSource = new StreamSource(new StringReader(xsl));
        CompilerInfo compilerInfo = config.getDefaultXsltCompilerInfo();
        compilerInfo.setCodeInjector(null);
        return Compilation.compileSingletonPackage(config, compilerInfo, styleSource);

    }

}

