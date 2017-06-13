////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans.packages;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.ProxyReceiver;
import net.sf.saxon.event.Sender;
import net.sf.saxon.event.Sink;
import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;

import javax.xml.transform.stream.StreamSource;
import java.io.File;

/**
 * The PIGrabber class is a Receiver that looks at an incoming stylesheet document
 * and extracts the package name and version from the root element; parsing is then
 * abandoned.
 *
 * @author Michael H. Kay
 */

public class PackageInspector extends ProxyReceiver {

    private String packageName;
    private String packageVersion;
    private int elementCount = 0;

    private PackageInspector(PipelineConfiguration pipe) {
        super(new Sink(pipe));
    }

    /**
     * Abort the parse when the first start element tag is found
     */

    public void startElement(NodeName namecode, SchemaType typecode, Location location, int properties)
            throws XPathException {
        if (elementCount++ >= 1) {
            // abort the parse when the second start element tag is found
            throw new XPathException("#start#");
        }
    }

    /**
     * Notify an attribute. Attributes are notified after the startElement event, and before any
     * children. Namespaces and attributes may be intermingled.
     *
     * @param name   The name of the attribute
     * @param typeCode   The type of the attribute
     * @param value
     * @param locationId
     * @param properties Bit significant value. The following bits are defined:
     *                   <dd>DISABLE_ESCAPING</dd>    <dt>Disable escaping for this attribute</dt>
     *                   <dd>NO_SPECIAL_CHARACTERS</dd>      <dt>Attribute value contains no special characters</dt>  @throws IllegalStateException: attempt to output an attribute when there is no open element
     */
    @Override
    public void attribute(NodeName name, SimpleType typeCode, CharSequence value, Location locationId, int properties) throws XPathException {
        if (name.getLocalPart().equals("name")) {
            packageName = value.toString();
        } else if (name.getLocalPart().equals("package-version") || name.getLocalPart().equals("packageVersion")) {
            packageVersion = value.toString();
        }
    }

    private VersionedPackageName getNameAndVersion() {
        try {
            return new VersionedPackageName(packageName, packageVersion);
        } catch (XPathException e) {
            return null;
        }
    }

    public static PackageDetails getPackageDetails(File top, Configuration config) {
        PackageInspector inspector = new PackageInspector(config.makePipelineConfiguration());
        try {
            Sender.send(new StreamSource(top), inspector, new ParseOptions());
        } catch (XPathException e) {
            // early exit is expected
        }
        VersionedPackageName vp = inspector.getNameAndVersion();
        if (vp == null) {
            return null;
        } else {
            PackageDetails details = new PackageDetails();
            details.nameAndVersion = vp;
            details.sourceLocation = new StreamSource(top);
            return details;
        }
    }
}