////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.testdriver;

public enum Spec {

    XP20("2.0", "XPath2.0", "XP", "XP20"),
    XP30("3.0", "XPath3.0", "XP", "XP30"),
    XP31("3.1", "XPath3.1", "XP", "XP31"),
    XQ10("1.0", "XQuery1.0", "XQ", "XQ10"),
    XQ30("3.0", "XQuery3.0", "XQ", "XQ30"),
    XQ31("3.1", "XQuery3.1", "XQ", "XQ31"),
    XT10("1.0", "XSLT1.0", "XT", "XT10"),
    XT20("2.0", "XSLT2.0", "XT", "XT20"),
    XT30("3.0", "XSLT3.0", "XT", "XT30");


    public final String version;
    public final String fullName;
    public final String shortSpecName;
    public final String specAndVersion;

    Spec(String l, String na, String sn, String sv) {
        version = l;
        fullName = na;
        shortSpecName = sn;
        specAndVersion = sv;
    }

    public int getNumericVersion() {
        return Integer.parseInt(version.replaceAll("\\.",""));
    }
}
