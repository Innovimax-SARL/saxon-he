////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans.packages;

import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.style.StylesheetPackage;

import javax.xml.transform.Source;
import java.util.Map;

/**
 * Information about a package held in a package library; the package may or may not be loaded in memory
 */
public class PackageDetails {
    public VersionedPackageName nameAndVersion;
    public String baseName;
    public String shortName;
    public StylesheetPackage loadedPackage;
    public Source sourceLocation;
    public Source exportLocation;
    public Integer priority;
    public Map<StructuredQName, Sequence> staticParams;
    public Thread beingProcessed;
}

