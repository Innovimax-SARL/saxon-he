////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.map;

import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.value.AtomicValue;

/**
 * A key and a corresponding value to be held in a Map.
 */

public class KeyValuePair {
    public AtomicValue key;
    public Sequence value;

    public KeyValuePair(AtomicValue key, Sequence value) {
        this.key = key;
        this.value = value;
        if (SequenceTool.isUnrepeatable(value)) {
            throw new IllegalArgumentException();
        }
    }
}

// Copyright (c) 2010 Saxonica Limited.
