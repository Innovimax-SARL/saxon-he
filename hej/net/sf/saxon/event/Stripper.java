////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.FingerprintedQName;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.om.SpaceStrippingRule;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.rules.Rule;
import net.sf.saxon.trans.rules.RuleTarget;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ComplexType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;
import net.sf.saxon.type.Untyped;
import net.sf.saxon.value.Whitespace;

import java.util.Arrays;


/**
 * The RuleBasedStripper class performs whitespace stripping according to the rules of
 * the xsl:strip-space and xsl:preserve-space instructions.
 * It maintains details of which elements need to be stripped.
 * The code is written to act as a SAX-like filter to do the stripping.
 *
 * @author Michael H. Kay
 */


public class Stripper extends ProxyReceiver {

    public final static StripRuleTarget STRIP = new StripRuleTarget() {
    };
    public final static StripRuleTarget PRESERVE = new StripRuleTarget() {
    };
    protected SpaceStrippingRule rule;

    public Stripper(/*@NotNull*/ SpaceStrippingRule rule, /*@NotNull*/ Receiver next) {
        super(next);
        assert rule != null;
        this.rule = rule;
    }

    // stripStack is used to hold information used while stripping nodes. We avoid allocating
    // space on the tree itself to keep the size of nodes down. Each entry on the stack is two
    // booleans, one indicates the current value of xml-space is "preserve", the other indicates
    // that we are in a space-preserving element.

    // We implement our own stack to avoid the overhead of allocating objects. The two booleans
    // are held as the leas-significant bits of a byte.

    private byte[] stripStack = new byte[100];
    private int top = 0;

    /**
     * Get a clean copy of this stripper. The new copy shares the same PipelineConfiguration
     * as the original, but the underlying receiver (that is, the destination for post-stripping
     * events) is changed.
     *
     * @param next the next receiver in the pipeline for the new Stripper
     * @return a dublicate of this Stripper, with the output sent to "next".
     */

    public Stripper getAnother(Receiver next) {
        return new Stripper(rule, next);
    }

    /**
     * Decide whether an element is in the set of white-space preserving element types
     *
     * @param name Identifies the name of the element whose whitespace is (or is not) to
     *             be preserved
     * @param type The schema type of this element
     * @return ALWAYS_PRESERVE if the element is in the set of white-space preserving
     *         element types, ALWAYS_STRIP if the element is to be stripped regardless of the
     *         xml:space setting, and STRIP_DEFAULT otherwise
     * @throws XPathException if the rules are ambiguous and ambiguities are to be
     *                        reported as errors
     */

    private int isSpacePreserving(NodeName name, SchemaType type) throws XPathException {
        return rule.isSpacePreserving(name, type);
    }

    public static final byte ALWAYS_PRESERVE = 0x01;    // whitespace always preserved (e.g. xsl:text)
    public static final byte ALWAYS_STRIP = 0x02;       // whitespace always stripped (e.g. xsl:choose)
    public static final byte STRIP_DEFAULT = 0x00;      // no special action
    public static final byte PRESERVE_PARENT = 0x04;    // parent element specifies xml:space="preserve"
    public static final byte SIMPLE_CONTENT = 0x08;     // type annotation indicates simple typed content
    public static final byte ASSERTIONS_EXIST = 0x10;   // XSD 1.1 assertions are in scope


    /**
     * Callback interface for SAX: not for application use
     */

    public void open() throws XPathException {
        // System.err.println("Stripper#startDocument()");
        top = 0;
        stripStack[top] = ALWAYS_PRESERVE;             // {xml:preserve = false, preserve this element = true}
        super.open();
    }

    public void startElement(NodeName elemName, SchemaType type, Location location, int properties) throws XPathException {
        // System.err.println("startElement " + nameCode);
        nextReceiver.startElement(elemName, type, location, properties);

        byte preserveParent = stripStack[top];
        byte preserve = (byte) (preserveParent & (PRESERVE_PARENT | ASSERTIONS_EXIST));

        int elementStrip = isSpacePreserving(elemName, type);
        if (elementStrip == ALWAYS_PRESERVE) {
            preserve |= ALWAYS_PRESERVE;
        } else if (elementStrip == ALWAYS_STRIP) {
            preserve |= ALWAYS_STRIP;
        }
        if (type != Untyped.getInstance()) {
            if (preserve == 0) {
                // if the element has simple content, whitespace stripping is disabled
                if (type.isSimpleType() || ((ComplexType) type).isSimpleContent()) {
                    preserve |= SIMPLE_CONTENT;
                }
            }
            if (type instanceof ComplexType && ((ComplexType) type).hasAssertions()) {
                preserve |= ASSERTIONS_EXIST;
            }
        }

        // put "preserve" value on top of stack

        top++;
        if (top >= stripStack.length) {
            stripStack = Arrays.copyOf(stripStack, top * 2);
        }
        stripStack[top] = preserve;
    }

    public void attribute(NodeName nameCode, SimpleType typeCode, CharSequence value, Location locationId, int properties)
            throws XPathException {

        // test for xml:space="preserve" | "default"

        if (nameCode.equals(XML_SPACE)) {
            if (Whitespace.normalizeWhitespace(value).equals("preserve")) {
                stripStack[top] |= PRESERVE_PARENT;
            } else {
                stripStack[top] &= ~PRESERVE_PARENT;
            }
        }
        nextReceiver.attribute(nameCode, typeCode, value, locationId, properties);
    }

    private static NodeName XML_SPACE = new FingerprintedQName("xml", NamespaceConstant.XML, "space", StandardNames.XML_SPACE);

    /**
     * Handle an end-of-element event
     */

    public void endElement() throws XPathException {
        nextReceiver.endElement();
        top--;
    }

    /**
     * Handle a text node
     */

    public void characters(CharSequence chars, Location locationId, int properties) throws XPathException {
        // assume adjacent chunks of text are already concatenated

        if (((((stripStack[top] & (ALWAYS_PRESERVE | PRESERVE_PARENT | SIMPLE_CONTENT | ASSERTIONS_EXIST)) != 0) &&
                (stripStack[top] & ALWAYS_STRIP) == 0)
                || !Whitespace.isWhite(chars))
                && chars.length() > 0) {
            nextReceiver.characters(chars, locationId, properties);
        }
    }

    /**
     * Ask whether this Receiver (or the downstream pipeline) makes any use of the type annotations
     * supplied on element and attribute events
     *
     * @return true if the Receiver makes any use of this information. If false, the caller
     *         may supply untyped nodes instead of supplying the type annotation
     */

    public boolean usesTypeAnnotations() {
        return true;
    }

    public static class StripRuleTarget implements RuleTarget {
        public void export(ExpressionPresenter presenter) throws XPathException {
            // no-op
        }

        public void registerRule(Rule rule) {
            // no action
        }
    }
}

