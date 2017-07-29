////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pull;

import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.ReceiverOptions;
import net.sf.saxon.event.SequenceReceiver;
import net.sf.saxon.expr.parser.ExplicitLocation;
import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.om.AttributeCollection;
import net.sf.saxon.om.NamespaceBinding;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.Orphan;
import net.sf.saxon.type.SimpleType;
import net.sf.saxon.type.Type;

import java.util.List;

/**
 * PullPushTee is a pass-through filter class that links one PullProvider to another PullProvider
 * in a pipeline, copying all events that are read into a push pipeline, supplied in the form
 * of a Receiver.
 * <p/>
 * <p>This class can be used to insert a schema validator into a pull pipeline, since Saxon's schema
 * validation is push-based. It could also be used to insert a serializer into the pipeline, allowing
 * the XML document being "pulled" to be displayed for diagnostic purposes.</p>
 */

public class PullPushTee extends PullFilter {

    private Receiver branch;
    boolean previousAtomic = false;

    /**
     * Create a PullPushTee
     *
     * @param base   the PullProvider to which requests are to be passed
     * @param branch the Receiver to which all events are to be copied, as "push" events.
     *               This Receiver must already be open before use
     */

    public PullPushTee(/*@NotNull*/ PullProvider base, Receiver branch) throws XPathException {
        super(base);
        this.branch = branch;
    }

    /**
     * Get the Receiver to which events are being tee'd.
     *
     * @return the Receiver
     */

    public Receiver getReceiver() {
        return branch;
    }

    /**
     * Get the next event. This implementation gets the next event from the underlying PullProvider,
     * copies it to the branch Receiver, and then returns the event to the caller.
     *
     * @return an integer code indicating the type of event. The code
     *         {@link #END_OF_INPUT} is returned at the end of the sequence.
     */

    public int next() throws XPathException {
        currentEvent = super.next();
        copyEvent(currentEvent);
        return currentEvent;
    }


    /**
     * Copy a pull event to a Receiver
     *
     * @param event the pull event to be copied
     */

    private void copyEvent(int event) throws XPathException {
        PullProvider in = getUnderlyingProvider();
        Location loc = in.getSourceLocator();
        if (loc == null) {
            loc = ExplicitLocation.UNKNOWN_LOCATION;
        }
        Receiver out = branch;
        switch (event) {
            case START_DOCUMENT:
                out.startDocument(0);
                break;

            case START_ELEMENT:
                out.startElement(in.getNodeName(), in.getSchemaType(), loc, 0);

                NamespaceBinding[] decl = in.getNamespaceDeclarations();
                for (NamespaceBinding aDecl : decl) {
                    if (aDecl == null) {
                        break;
                    }
                    out.namespace(aDecl, 0);
                }

                AttributeCollection atts = in.getAttributes();
                for (int i = 0; i < atts.getLength(); i++) {
                    out.attribute(atts.getNodeName(i), atts.getTypeAnnotation(i),
                            atts.getValue(i), loc, atts.getProperties(i));
                }

                out.startContent();
                break;

            case TEXT:

                out.characters(in.getStringValue(), loc, ReceiverOptions.WHOLE_TEXT_NODE);
                break;

            case COMMENT:

                out.comment(in.getStringValue(), loc, 0);
                break;

            case PROCESSING_INSTRUCTION:

                out.processingInstruction(
                        in.getNodeName().getLocalPart(),
                        in.getStringValue(), loc, 0);
                break;

            case END_ELEMENT:

                out.endElement();
                break;

            case END_DOCUMENT:
                List entities = in.getUnparsedEntities();
                if (entities != null) {
                    for (Object entity : entities) {
                        UnparsedEntity ue = (UnparsedEntity) entity;
                        out.setUnparsedEntity(ue.getName(), ue.getSystemId(), ue.getPublicId());
                    }
                }
                out.endDocument();
                break;

            case END_OF_INPUT:
                in.close();
                break;

            case ATOMIC_VALUE:
                if (out instanceof SequenceReceiver) {
                    ((SequenceReceiver) out).append(super.getAtomicValue(), loc, 0);
                    break;
                } else {
                    if (previousAtomic) {
                        out.characters(" ", loc, 0);
                    }
                    CharSequence chars = in.getStringValue();
                    out.characters(chars, loc, 0);
                    break;
                }

            case ATTRIBUTE:
                if (out instanceof SequenceReceiver) {
                    Orphan o = new Orphan(in.getPipelineConfiguration().getConfiguration());
                    o.setNodeName(getNodeName());
                    o.setNodeKind(Type.ATTRIBUTE);
                    o.setStringValue(getStringValue());
                    ((SequenceReceiver) out).append(o, loc, 0);
                    break;
                } else {
                    out.attribute(getNodeName(),
                            (SimpleType) in.getSchemaType(), getStringValue(), loc, 0);
                    break;
                }

            case NAMESPACE:
                if (out instanceof SequenceReceiver) {
                    Orphan o = new Orphan(in.getPipelineConfiguration().getConfiguration());
                    o.setNodeName(getNodeName());
                    o.setNodeKind(Type.NAMESPACE);
                    o.setStringValue(getStringValue());
                    ((SequenceReceiver) out).append(o, loc, 0);
                    break;
                } else {
                    out.namespace(new NamespaceBinding(getNodeName().getPrefix(), getNodeName().getURI()), 0);
                    break;
                }

            default:
                throw new UnsupportedOperationException("" + event);

        }
        previousAtomic = event == ATOMIC_VALUE;
    }
}

