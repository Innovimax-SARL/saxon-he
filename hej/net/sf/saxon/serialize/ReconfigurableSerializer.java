////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import net.sf.saxon.event.ProxyReceiver;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.lib.SerializerFactory;
import net.sf.saxon.trans.XPathException;

import javax.xml.transform.Result;
import java.util.Properties;

/**
 * This class by default acts as a pass-through Receiver, acting as the gateway to a serialization
 * pipeline. But it is capable of dynamic reconfiguration to construct a different serialization
 * pipeline if required. This capability is invoked when xsl:result-document sets serialization
 * parameters dynamically.
 */

public class ReconfigurableSerializer extends ProxyReceiver {

    private Result destination;
    private Properties apiDefinedProperties;

    /**
     * Create a reconfigurable serializer (that is, a gateway to a dynamically-constructed serialization
     * pipeline)
     * @param next the next receiver to receiver events
     * @param apiDefinedProperties the output properties explicitly declared on the Serializer object.
     *                             These are defined to take precedence over any properties defined
     *                             within the stylesheet.
     * @param destination the output destination.
     */

    public ReconfigurableSerializer(Receiver next, Properties apiDefinedProperties, Result destination) {
        super(next);
        this.destination = destination;
        this.apiDefinedProperties = apiDefinedProperties;
    }

    /**
     * Create a new serialization pipeline and redirect future output to that pipeline.
     * @param outputProperties serialization properties, typically the properties specified dynamically
     *                         in an xsl:result-document instruction, together with properties specified
     *                         statically in the named or unnamed output format that it references.
     * @param charMapIndex character map index
     * @throws XPathException
     */

    public void reconfigure(Properties outputProperties, CharacterMapIndex charMapIndex) throws XPathException {
        SerializerFactory sf = getConfiguration().getSerializerFactory();
        Properties combinedProps = new Properties(outputProperties);
        for (String s : apiDefinedProperties.stringPropertyNames()) {
            combinedProps.setProperty(s, apiDefinedProperties.getProperty(s));
        }
        Receiver r = sf.getReceiver(destination, getPipelineConfiguration(), combinedProps, charMapIndex);
        setUnderlyingReceiver(r);
    }
}

