////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.lib.SaxonOutputKeys;
import net.sf.saxon.ma.json.JsonReceiver;
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
import java.io.Writer;
import java.util.Properties;

/**
 * This class implements the JSON serialization method defined in XSLT+XQuery Serialization 3.1.
 * At present it contains the back-end functionality of JSONEmitter; the intent is to modify
 * JSONEmitter to invoke this class.
 *
 * @author Michael H. Kay
 */

public class JSONWriter  {

    private ExpandedStreamResult result;

    private Writer writer;
    private Normalizer normalizer;
    private CharacterMap characterMap;
    private Properties outputProperties;
    private CharacterSet characterSet;
    private boolean indent;
    private boolean first = true;
    private boolean afterKey = false;
    private int level;

    private boolean unfailing = false;

    public JSONWriter(PipelineConfiguration pipe, StreamResult result, Properties outputProperties) throws XPathException {
        setOutputProperties(outputProperties);
        this.result = new ExpandedStreamResult(pipe.getConfiguration(), result, outputProperties);
    }

    /**
     * Set output properties
     *
     * @param details the output serialization properties
     * @throws XPathException if an error occurs finding the encoding property
     */

    public void setOutputProperties(Properties details) throws XPathException {
        this.outputProperties = details;
        if ("yes".equals(details.getProperty(OutputKeys.INDENT))) {
            indent = true;
        }
        if ("yes".equals(details.getProperty(SaxonOutputKeys.UNFAILING))) {
            unfailing = true;
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

    public void writeKey(String key) throws XPathException {
        conditionalComma();
        emit('"');
        emit(escape(key));
        emit("\":");
        afterKey = true;
    }

    /**
     * Append an arbitrary item (node or atomic value) to the output
     *
     * @param item           the atomic value to be appended, or null to append "null"
     * @throws XPathException if the operation fails
     */

    public void writeAtomicValue(AtomicValue item) throws XPathException {
        conditionalComma();
        if (item == null) {
            emit("null");
        } else if (item instanceof NumericValue) {
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
        } else {
            emit('"');
            emit(escape(item.getStringValue()));
            emit('"');
        }
    }

    public void startArray() throws XPathException {
        emitOpen('[', level++);
    }

    public void endArray() throws XPathException {
        emitClose(']', level--);
    }

    public void startMap() throws XPathException {
        emitOpen('{', level++);
    }

    public void endMap() throws XPathException {
        emitClose('}', level--);
    }

    private void emitOpen(char bracket, int level) throws XPathException {
        conditionalComma();
        if (indent) {
            emit(' ');
        }
        emit(bracket);
        first = true;
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
        first = false;
    }

    private void conditionalComma() throws XPathException {
        if (first) {
            first = false;
        } else if (!afterKey) {
            emit(',');
        }
        afterKey = false;
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



    /**
     * End of the document.
     */

    public void close() throws XPathException {
        if (first) {
            emit("null");
        }
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                // no action
            }
        }
    }
}

