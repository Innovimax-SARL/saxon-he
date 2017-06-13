////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.FunctionItemType;
import net.sf.saxon.type.SpecificFunctionType;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;

import java.util.Arrays;

/**
 * Implementation of the fn:concat() function
 */


public class Concat extends SystemFunction {

    @Override
    protected Sequence resultIfEmpty(int arg) {
        return null;
    }

    /**
     * Get the roles of the arguments, for the purposes of streaming
     *
     * @return an array of OperandRole objects, one for each argument
     */
    public OperandRole[] getOperandRoles() {
        OperandRole[] roles = new OperandRole[getArity()];
        OperandRole operandRole = new OperandRole(0, OperandUsage.ABSORPTION);
        for (int i = 0; i < getArity(); i++) {
            roles[i] = operandRole;
        }
        return roles;
    }



    /**
     * Get the item type of the function item
     *
     * @return the function item's type
     */
    @Override
    public FunctionItemType getFunctionItemType() {
        SequenceType[] argTypes = new SequenceType[getArity()];
        Arrays.fill(argTypes, SequenceType.OPTIONAL_ATOMIC);
        return new SpecificFunctionType(argTypes, SequenceType.SINGLE_STRING);
    }

    /**
     * Allow the function to create an optimized call based on the values of the actual arguments
     *
     * @param visitor     the expression visitor
     * @param contextInfo information about the context item
     * @param arguments   the supplied arguments to the function call. Note: modifying the contents
     *                    of this array should not be attempted, it is likely to have no effect.
     * @return either a function call on this function, or an expression that delivers
     * the same result, or null indicating that no optimization has taken place
     * @throws XPathException if an error is detected
     */
    @Override
    public Expression makeOptimizedFunctionCall(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo, final Expression... arguments) throws XPathException {
        return new SystemFunctionCall.Optimized(this, arguments) {
            @Override
            public CharSequence evaluateAsString(XPathContext context) throws XPathException {
                FastStringBuffer buffer = new FastStringBuffer(256);
                for (Operand o: operands()) {
                    Item it = o.getChildExpression().evaluateItem(context);
                    if (it != null) {
                        buffer.append(it.getStringValueCS());
                    }
                }
                return buffer;
            }

            @Override
            public Item evaluateItem(XPathContext context) throws XPathException {
                return new StringValue(evaluateAsString(context));
            }
        };

    }


    public StringValue call(XPathContext context, Sequence[] arguments) throws XPathException {
        FastStringBuffer fsb = new FastStringBuffer(FastStringBuffer.C64);
        for (Sequence arg : arguments) {
            Item item = arg.head();
            if (item != null) {
                fsb.append(item.getStringValueCS());
            }
        }
        return new StringValue(fsb);
    }


    /**
     * Get the required type of the nth argument
     */

    public SequenceType getRequiredType(int arg) {
        return getDetails().argumentTypes[0];
        // concat() is a special case
    }

    public String getCompilerName() {
        return "ConcatCompiler";
    }



}

