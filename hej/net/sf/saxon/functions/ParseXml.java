////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.Configuration;
import net.sf.saxon.Controller;
import net.sf.saxon.event.Builder;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.Sender;
import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.PackageData;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.om.IgnorableSpaceStrippingRule;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.TreeModel;
import net.sf.saxon.style.StylesheetPackage;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.TinyDocumentImpl;
import net.sf.saxon.value.ObjectValue;
import net.sf.saxon.value.SequenceExtent;
import net.sf.saxon.value.StringValue;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

public class ParseXml extends SystemFunction implements Callable {

    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as SequenceIterators
     * @return the result of the evaluation, in the form of a SequenceIterator
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    public NodeInfo call(XPathContext context, Sequence[] arguments) throws XPathException {
        return evalParseXml(
                (StringValue) arguments[0].head(),
                context);
    }

    private NodeInfo evalParseXml(StringValue inputArg, XPathContext context) throws XPathException {
        String baseURI = getRetainedStaticContext().getStaticBaseUriString();

        RetentiveErrorHandler errorHandler = new RetentiveErrorHandler();
        try {
            Controller controller = context.getController();
            if (controller == null) {
                throw new XPathException("parse-xml() function is not available in this environment");
            }
            Configuration config = controller.getConfiguration();

            StringReader sr = new StringReader(inputArg.getStringValue());
            InputSource is = new InputSource(sr);
            is.setSystemId(baseURI);
            Source source = new SAXSource(is);
            source.setSystemId(baseURI);

            Builder b = TreeModel.TINY_TREE.makeBuilder(controller.makePipelineConfiguration());
            Receiver s = b;
            ParseOptions options = new ParseOptions(config.getParseOptions());
            PackageData pd = getRetainedStaticContext().getPackageData();
            if (pd instanceof StylesheetPackage) {
                options.setSpaceStrippingRule(((StylesheetPackage) pd).getSpaceStrippingRule());
                if (((StylesheetPackage)pd).isStripsTypeAnnotations()) {
                    s = config.getAnnotationStripper(s);
                }
            } else {
                options.setSpaceStrippingRule(IgnorableSpaceStrippingRule.getInstance());
            }
            options.setErrorHandler(errorHandler);

            s.setPipelineConfiguration(b.getPipelineConfiguration());

            Sender.send(source, s, options);

            TinyDocumentImpl node = (TinyDocumentImpl) b.getCurrentRoot();
            node.setBaseURI(baseURI);
            node.getTreeInfo().setUserData("saxon:document-uri", "");
            b.reset();
            return node;
        } catch (XPathException err) {
            XPathException xe = new XPathException("First argument to parse-xml() is not a well-formed and namespace-well-formed XML document. XML parser reported: " +
                    err.getMessage(), "FODC0006");
            errorHandler.captureRetainedErrors(xe);
            xe.maybeSetContext(context);
            throw xe;
        }
    }

    public static class RetentiveErrorHandler implements ErrorHandler {

        public List<SAXParseException> errors = new ArrayList<SAXParseException>();
        public boolean failed = false;

        public void error(SAXParseException exception) throws SAXException {
            errors.add(exception);
        }

        public void warning(SAXParseException exception) throws SAXException {
            // no action
        }

        public void fatalError(SAXParseException exception) throws SAXException {
            errors.add(exception);
            failed = true;
        }

        public void captureRetainedErrors(XPathException xe) {
            List<SAXParseException> retainedErrors = errors;
            if (!retainedErrors.isEmpty()) {
                List<ObjectValue<SAXParseException>> wrappedErrors = new ArrayList<ObjectValue<SAXParseException>>();
                for (SAXParseException e : retainedErrors) {
                    wrappedErrors.add(new ObjectValue<SAXParseException>(e));
                }
                xe.setErrorObject(SequenceExtent.makeSequenceExtent(wrappedErrors));
            }
        }
    }
}

//Copyright (c) 2010-2016 Saxonica Limited.
