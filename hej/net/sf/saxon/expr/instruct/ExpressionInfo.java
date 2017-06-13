////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trace.InstructionInfo;
import net.sf.saxon.trace.LocationKind;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Details about an instruction, used when reporting errors and when tracing
 */

public final class ExpressionInfo implements InstructionInfo {

    private Expression expr;

    public ExpressionInfo(Expression expr) {
        this.expr = expr;
    }

    /**
     * Get the construct type
     */
    public int getConstructType() {
        return LocationKind.XPATH_EXPRESSION;
    }

    /**
     * Get the URI of the module containing the instruction
     *
     * @return the module's URI, or null indicating unknown
     */

    /*@Nullable*/
    public String getSystemId() {
        return expr.getLocation().getSystemId();
    }

    /**
     * Get the line number of the instruction within its module
     *
     * @return the line number
     */

    public int getLineNumber() {
        return expr.getLocation().getLineNumber();
    }

    /**
     * Get an immutable copy of this Location object. By default Location objects may be mutable, so they
     * should not be saved for later use. The result of this operation holds the same location information,
     * but in an immutable form.
     */
    public Location saveLocation() {
        return this;
    }

    /**
     * Get a name identifying the object of the expression, for example a function name, template name,
     * variable name, key name, element name, etc. This is used only where the name is known statically.
     *
     * @return the name of the object, or null to indicate that it has no name
     */

    /*@Nullable*/
    public StructuredQName getObjectName() {
        return null;
    }


    public Object getProperty(String name) {
        return null;
    }

    /**
     * Get an iterator over all the properties available. The values returned by the iterator
     * will be of type String, and each string can be supplied as input to the getProperty()
     * method to retrieve the value of the property.
     *
     * @return an iterator over the names of the properties
     */

    public Iterator<String> getProperties() {
        List<String> list = Collections.emptyList();
        return list.iterator();
    }

    /**
     * Get the public ID of the module containing the instruction. This method
     * is provided to satisfy the SourceLocator interface. However, the public ID is
     * not maintained by Saxon, and the method always returns null
     *
     * @return null
     */

    public String getPublicId() {
        return null;
    }

    /**
     * Get the column number identifying the position of the instruction.
     *
     * @return -1 if column number is not known
     */

    public int getColumnNumber() {
        return expr.getLocation().getColumnNumber();
    }

}

