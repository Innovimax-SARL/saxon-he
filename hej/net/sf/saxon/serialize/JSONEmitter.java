////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.SequenceWriter;
import net.sf.saxon.lib.SaxonOutputKeys;
import net.sf.saxon.ma.arrays.ArrayItem;
import net.sf.saxon.ma.json.JsonReceiver;
import net.sf.saxon.ma.map.KeyValuePair;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.om.*;
import net.sf.saxon.query.QueryResult;
import net.sf.saxon.serialize.charcode.CharacterSet;
import net.sf.saxon.serialize.codenorm.Normalizer;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.NumericValue;
import net.sf.saxon.z.IntPredicate;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * This class implements the JSON serialization method defined in XSLT+XQuery Serialization 3.1.
 *
 * @author Michael H. Kay
 */

public class JSONEmitter extends SequenceWriter {

    private ExpandedStreamResult result;
    private boolean allowDuplicateKeys = false;
    private String nodeOutputMethod = "xml";
    private int count = 0;
    
    private Writer writer;
    private Normalizer normalizer;
    private CharacterMap characterMap;
    private Properties outputProperties;
    private CharacterSet characterSet;
    private boolean indent;

    private boolean unfailing = false;
    
    public JSONEmitter(PipelineConfiguration pipe, StreamResult result, Properties outputProperties) throws XPathException {
        super(pipe);
        setOutputProperties(outputProperties);
        this.result = new ExpandedStreamResult(pipe.getConfiguration(), result, outputProperties);
    }

    /**
     * Set output properties
     *
     * @param details the output serialization properties
     * @throws net.sf.saxon.trans.XPathException if an error occurs finding the encoding property
     */

    public void setOutputProperties(Properties details) throws XPathException {
        this.outputProperties = details;
        if ("yes".equals(details.getProperty(SaxonOutputKeys.ALLOW_DUPLICATE_NAMES))) {
            allowDuplicateKeys = true;
        }
        if ("yes".equals(details.getProperty(OutputKeys.INDENT))) {
            indent = true;
        }
        if ("yes".equals(details.getProperty(SaxonOutputKeys.UNFAILING))) {
            unfailing = true;
            allowDuplicateKeys = true;
        }
        String jnom = details.getProperty(SaxonOutputKeys.JSON_NODE_OUTPUT_METHOD);
        if (jnom != null) {
            nodeOutputMethod = jnom;
        }
    }

    /**
     * Get the output properties
     * @return the properties that were set using setOutputProperties
     */

    public Properties getOutputProperties() {
        return outputProperties;
    }

    /**
     * Set the Unicode normalizer to be used for normalizing strings.
     * @param normalizer the normalizer to be used
     */

    public void setNormalizer(Normalizer normalizer) {
        this.normalizer = normalizer;
    }

    /**
     * Set the CharacterMap to be used, if any
     * @param map the character map
     */

    public void setCharacterMap(CharacterMap map) {
        this.characterMap = map;
    }

    /**
     * Append an arbitrary item (node or atomic value) to the output
     *
     * @param item           the item to be appended
     * @throws net.sf.saxon.trans.XPathException if the operation fails
     */
    @Override
    public void write(Item item) throws XPathException {
        if (count++ > 0) {
            if (unfailing) {
                emit(",");
            } else {
                throw new XPathException("JSON output method cannot handle a sequence of more than one item", "SERE0023");
            }
        }
        writeItem(item, 0);
    }

    private void writeItem(Item item, int level) throws XPathException {
        try {
            if (item instanceof NumericValue) {
                if (((NumericValue)item).isNaN()) {
                    if (unfailing) {
                        emit("NaN");
                    } else {
                        throw new XPathException("JSON has no way of representing NaN", "SERE0020");
                    }
                } else if (Double.isInfinite(((NumericValue)item).getDoubleValue())) {
                    if (unfailing) {
                        emit(((NumericValue) item).getDoubleValue() < 0 ? "-INF" : "INF");
                    } else {
                        throw new XPathException("JSON has no way of representing Infinity", "SERE0020");
                    }
                } else {
                    emit(item.getStringValue());
                }
            } else if (item instanceof BooleanValue) {
                emit(item.getStringValue());
            } else if (item instanceof AtomicValue) {
                emit('"');
                emit(escape(item.getStringValue()));
                emit('"');
            } else if (item instanceof MapItem) {
                Set<String> keys = null;
                if (!allowDuplicateKeys) {
                    keys = new HashSet<String>();
                }
                emitOpen('{', level);
                boolean first = true;
                for (KeyValuePair pair : (MapItem) item) {
                    if (first) {
                        first = false;
                    } else {
                        emitComma(level);
                    }
                    emit('"');
                    String stringKey = pair.key.getStringValue();
                    if (!allowDuplicateKeys && !keys.add(stringKey)) {
                        throw new XPathException("Key value \"" + stringKey + "\" occurs more than once in JSON map", "SERE0022");
                    }
                    emit(escape(stringKey));
                    emit('"');
                    emit(':');
                    writeSequence(SequenceTool.toGroundedValue(pair.value), level+1);
                }
                emitClose('}', level);
            } else if (item instanceof ArrayItem) {
                emitOpen('[', level);
                boolean first = true;
                for (Sequence member : (ArrayItem) item) {
                    if (first) {
                        first = false;
                    } else {
                        emitComma(level);
                    }
                    writeSequence(SequenceTool.toGroundedValue(member), level+1);
                }
                emitClose(']', level);
            } else if (item instanceof NodeInfo) {
                emit('"');
                String s = serializeNode((NodeInfo) item);
                emit(escape(s));
                emit('"');
            } else if (unfailing) {
                emit('"');
                String s = item.getStringValue();
                emit(escape(s));
                emit('"');
            } else {
                throw new XPathException("JSON output method cannot handle an item of type " + item.getClass(), "SERE0021");
            }
        } catch (IOException err) {
            throw new XPathException("Failure writing to " + getSystemId(), err);
        }
    }

    private void emitOpen(char bracket, int level) throws XPathException {
        if (indent) {
            emit(' ');
        }
        emit(bracket);
        if (indent) {
            emit('\n');
            for (int i=0; i<2*(level+1); i++) {
                emit(' ');
            }
        }
    }

    private void emitClose(char bracket, int level) throws XPathException {
        if (indent) {
            emit('\n');
            for (int i = 0; i <= 2 * level; i++) {
                emit(' ');
            }
        }
        emit(bracket);
    }

    private void emitComma(int level) throws XPathException {
        emit(',');
        if (indent) {
            emit('\n');
            for (int i = 0; i < 2 * (level+1); i++) {
                emit(' ');
            }
        }
    }

    private CharSequence escape(CharSequence cs) throws XPathException {
        if (characterMap != null) {
            FastStringBuffer out  = new FastStringBuffer(cs.length());
            cs = characterMap.map(cs, true);
            String s = cs.toString();
            int prev = 0;
            while (true) {
                int start = s.indexOf(0, prev);
                if (start >= 0) {
                    out.append(simpleEscape(s.substring(prev, start)));
                    int end = s.indexOf(0, start+1);
                    out.append(s.substring(start+1, end));
                    prev = end+1;
                } else {
                    out.append(simpleEscape(s.substring(prev)));
                    return out;
                }
            }
        } else {
            return simpleEscape(cs);
        }
    }

    private CharSequence simpleEscape(CharSequence cs) throws XPathException {
        if (normalizer != null) {
            cs = normalizer.normalize(cs);
        }
        return JsonReceiver.escape(cs, false, new IntPredicate() {
            public boolean matches(int c) {
                return c < 31 || (c >= 127 && c <= 159) || !characterSet.inCharset(c);
            }
        });
    }

    private String serializeNode(NodeInfo node) throws XPathException {
        StringWriter sw = new StringWriter();
        Properties props = new Properties();
        props.setProperty("method", nodeOutputMethod);
        props.setProperty("indent", "no");
        props.setProperty("omit-xml-declaration", "yes");
        QueryResult.serialize(node, new StreamResult(sw), props);
        return sw.toString().trim();
    }
    
    private void emit(CharSequence s) throws XPathException {
        if (writer == null) {
            writer = result.obtainWriter();
            characterSet = result.getCharacterSet();
        }
        try {
            writer.append(s);
        } catch (IOException e) {
            throw new XPathException(e);
        }
    }

    private void emit(char c) throws XPathException {
        emit(c + "");
    }

    private void writeSequence(GroundedValue seq, int level) throws XPathException, IOException {
        int len = seq.getLength();
        if (len == 0) {
            emit("null");
        } else if (len == 1) {
            writeItem(seq.head(), level);
        } else {
            throw new XPathException("JSON serialization: cannot handle a sequence of length " + len, "SERE0023");
        }
    }

    /**
     * End of the document.
     */
    @Override
    public void close() throws XPathException {
        if (count==0) {
            emit("null");
        }
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                // no action
            }
        }
        super.close();
    }
}

