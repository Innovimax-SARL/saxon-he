////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;

/**
 * A Valve is a general-purpose component for use in a pipeline of receivers. It selects an alternative
 * destination for the pipeline events based on the namespace of the first startElement event.
 *
 * There is a primary destination which is selected initially. If the namespace of the first element has
 * a given value, then subsequent output is sent to an alternative destination.
 * */

public class Valve extends ProxyReceiver {

    private boolean started = false;
    private String testNamespace;
    private Receiver alternativeReceiver;

    public Valve(String testNamespace, Receiver primary, Receiver secondary) {
        super(primary);
        this.testNamespace = testNamespace;
        this.alternativeReceiver = secondary;
    }

    @Override
    public void startElement(NodeName elemName, SchemaType typeCode, Location location, int properties) throws XPathException {
        if (!started) {
            started = true;
            if (elemName.getURI().equals(testNamespace)) {
                alternativeReceiver.open();
                alternativeReceiver.startDocument(0);
                try {
                    getUnderlyingReceiver().close();
                } catch (XPathException err) {
                    // ignore the failure
                }
                setUnderlyingReceiver(alternativeReceiver);
            }
        }
        super.startElement(elemName, typeCode, location, properties);
    }

    public boolean wasDiverted() {
        return getUnderlyingReceiver() == alternativeReceiver;
    }
}

