////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.json;

import net.sf.saxon.event.DocumentValidator;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.StartTagBuffer;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ExplicitLocation;
import net.sf.saxon.functions.OptionsParameter;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.*;

import java.util.Map;

/**
 * Implement the XML to JSON conversion as a built-in function - fn:xml-to-json()
 */
public class XMLToJsonFn extends SystemFunction {

    public static OptionsParameter makeOptionsParameter() {
        OptionsParameter xmlToJsonOptions = new OptionsParameter();
        xmlToJsonOptions.addAllowedOption("indent", SequenceType.SINGLE_BOOLEAN, BooleanValue.FALSE);
        return xmlToJsonOptions;
    }

    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        NodeInfo xml = (NodeInfo) arguments[0].head();
        if (xml == null) {
            return EmptySequence.getInstance();
        }

        boolean indent = false;
        if (getArity() > 1) {
            MapItem suppliedOptions = (MapItem) arguments[1].head();
            Map<String, Sequence> options = getDetails().optionDetails.processSuppliedOptions(suppliedOptions, context);
            indent = ((BooleanValue)options.get("indent").head()).getBooleanValue();
        }

        PipelineConfiguration pipe = context.getController().makePipelineConfiguration();
        JsonReceiver receiver = new JsonReceiver(pipe);
        receiver.setIndenting(indent);
        Receiver r = receiver;
        if (xml.getNodeKind() == Type.DOCUMENT) {
            r = new DocumentValidator(r, "FOJS0006");
        }
        StartTagBuffer stb = new StartTagBuffer(r);
        pipe.setComponent(StartTagBuffer.class.getName(), stb);
        stb.setPipelineConfiguration(pipe);
        stb.open();
        xml.copy(stb, NodeInfo.NO_NAMESPACES, ExplicitLocation.UNKNOWN_LOCATION);
        stb.close();
        return new StringValue(receiver.getJsonString());
    }

    public String getStreamerName() {
        return "XmlToJsonFn";
    }

}
