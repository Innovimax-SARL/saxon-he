////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.SequenceWriter;
import net.sf.saxon.functions.FormatNumber;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.SaxonOutputKeys;
import net.sf.saxon.ma.arrays.ArrayItem;
import net.sf.saxon.ma.map.KeyValuePair;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.om.*;
import net.sf.saxon.query.QueryResult;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.DoubleValue;
import net.sf.saxon.value.QualifiedNameValue;

import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Properties;

/**
 * This class implements the Adaptive serialization method defined in XSLT+XQuery Serialization 3.1.
 */

public class AdaptiveEmitter extends SequenceWriter {

    private Writer writer;
    private Properties outputProperties;
    private String itemSeparator = "\n";
    private boolean started = false;

    public AdaptiveEmitter(PipelineConfiguration pipe, Writer writer) {
        super(pipe);
        this.writer = writer;
    }

    public void setOutputProperties(Properties props) {
        outputProperties = props;
        if (props.getProperty(SaxonOutputKeys.ITEM_SEPARATOR) != null) {
            itemSeparator = props.getProperty(SaxonOutputKeys.ITEM_SEPARATOR);
        }
    }

    public Properties getOutputProperties() {
        return outputProperties;
    }

    private void emit(CharSequence s) throws XPathException {
        try {
            writer.append(s);
        } catch (IOException e) {
            throw new XPathException(e);
        }
    }

    /**
     * Abstract method to be supplied by subclasses: output one item in the sequence.
     *
     * @param item the item to be written to the sequence
     * @throws XPathException if any failure occurs while writing the item
     */
    @Override
    public void write(Item item) throws XPathException {
        if (started) {
            emit(itemSeparator);
        } else {
            started = true;
        }
        serializeItem(item);
    }

    private void serializeItem(Item item) throws XPathException {
        if (item instanceof AtomicValue) {
            emit(serializeAtomicValue((AtomicValue) item));
        } else if (item instanceof NodeInfo) {
            serializeNode((NodeInfo) item);
        } else if (item instanceof MapItem) {
            serializeMap((MapItem) item);
        } else if (item instanceof ArrayItem) {
            serializeArray((ArrayItem) item);
        } else if (item instanceof Function) {
            serializeFunction((Function) item);
        }
    }

    private String serializeAtomicValue(AtomicValue value) throws XPathException {
        switch(value.getPrimitiveType().getFingerprint()) {
            case StandardNames.XS_STRING:
            case StandardNames.XS_ANY_URI:
            case StandardNames.XS_UNTYPED_ATOMIC: {
                String s = value.getStringValue();
                if (s.contains("\"")) {
                    s = s.replace("\"", "\"\"");
                }
                return "\"" + s + "\"";
            }
            case StandardNames.XS_BOOLEAN:
                return value.effectiveBooleanValue() ? "true()" : "false()";

            case StandardNames.XS_DECIMAL:
            case StandardNames.XS_INTEGER:
                return value.getStringValue();

            case StandardNames.XS_DOUBLE:
                return FormatNumber.formatExponential((DoubleValue)value);

            case StandardNames.XS_FLOAT:
            case StandardNames.XS_DURATION:
            case StandardNames.XS_DATE_TIME:
            case StandardNames.XS_DATE:
            case StandardNames.XS_TIME:
            case StandardNames.XS_G_YEAR_MONTH:
            case StandardNames.XS_G_MONTH:
            case StandardNames.XS_G_MONTH_DAY:
            case StandardNames.XS_G_YEAR:
            case StandardNames.XS_G_DAY:
            case StandardNames.XS_HEX_BINARY:
            case StandardNames.XS_BASE64_BINARY:
                return value.getPrimitiveType().getDisplayName() + "(\"" + value.getStringValue() + "\")";

            case StandardNames.XS_DAY_TIME_DURATION:
            case StandardNames.XS_YEAR_MONTH_DURATION:
                return "xs:duration(\"" + value.getStringValue() + "\")";

            case StandardNames.XS_QNAME:
            case StandardNames.XS_NOTATION:
                return ((QualifiedNameValue)value).getStructuredQName().getEQName();
            default:
                return "***";
        }
    }

    private void serializeFunction(Function fn) throws XPathException {
        StructuredQName fname = fn.getFunctionName();
        if (fname == null || fname.hasURI(NamespaceConstant.ANONYMOUS)) {
            emit("(anonymous-function)");
        } else if (fname.hasURI(NamespaceConstant.FN)) {
            emit("fn:" + fname.getLocalPart());
        } else if (fname.hasURI(NamespaceConstant.MATH)) {
            emit("math:" + fname.getLocalPart());
        } else if (fname.hasURI(NamespaceConstant.MAP_FUNCTIONS)) {
            emit("map:" + fname.getLocalPart());
        } else if (fname.hasURI(NamespaceConstant.ARRAY_FUNCTIONS)) {
            emit("array:" + fname.getLocalPart());
        } else if (fname.hasURI(NamespaceConstant.SCHEMA)) {
            emit("xs:" + fname.getLocalPart());
        } else {
            emit(fname.getEQName());
        }
        emit("#" + fn.getArity());
    }

    private void serializeNode(NodeInfo node) throws XPathException {
        switch (node.getNodeKind()) {
            case Type.ATTRIBUTE:
                emit(node.getDisplayName());
                emit("=\"");
                emit(node.getStringValueCS());
                emit("\"");
                break;
            case Type.NAMESPACE:
                emit(node.getLocalPart().isEmpty() ? "xmlns" : "xmlns:" + node.getLocalPart());
                emit("=\"");
                emit(node.getStringValueCS());
                emit("\"");
                break;
            default:
                StringWriter sw = new StringWriter();
                Properties props = new Properties(outputProperties);
                String nodeMethod = outputProperties.getProperty(SaxonOutputKeys.JSON_NODE_OUTPUT_METHOD);
                if (nodeMethod == null) {
                    nodeMethod = "xml";
                }
                props.setProperty("method", nodeMethod);
                props.setProperty("indent", "no");
                props.setProperty("omit-xml-declaration", "yes");
                props.setProperty(SaxonOutputKeys.UNFAILING, "yes");
                QueryResult.serialize(node, new StreamResult(sw), props);
                emit(sw.toString().trim());
        }
    }

    private void serializeArray(ArrayItem array) throws XPathException {
        emit("[");
        boolean first = true;
        for (Sequence seq: array) {
            if (first) {
                first = false;
            } else {
                emit(",");
            }
            outputInternalSequence(seq);
        }
        emit("]");
    }

    private void serializeMap(MapItem map) throws XPathException {
        emit("map{");
        boolean first = true;
        for (KeyValuePair pair : map) {
            if (first) {
                first = false;
            } else {
                emit(",");
            }
            serializeItem(pair.key);
            emit(":");
            Sequence value = pair.value;
            outputInternalSequence(value);
        }
        emit("}");
    }

    private void outputInternalSequence(Sequence value) throws XPathException {
        boolean first = true;
        Item it;
        SequenceIterator iter = value.iterate();
        boolean omitParens = value instanceof GroundedValue && ((GroundedValue)value).getLength() == 1;
        if (!omitParens) {
            emit("(");
        }
        while ((it = iter.next()) != null) {
            if (!first) {
                emit(",");
            }
            first = false;
            serializeItem(it);
        }
        if (!omitParens) {
            emit(")");
        }
    }

    @Override
    public void close() throws XPathException {
        super.close();
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                throw new XPathException(e);
            }
        }
    }
}

