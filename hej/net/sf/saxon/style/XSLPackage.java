////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.trans.packages.VersionedPackageName;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.linked.NodeImpl;
import net.sf.saxon.tree.util.AttributeCollectionImpl;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.Whitespace;

/**
 * Handler for xsl:package elements. Explicit xsl:package elements are not permitted in Saxon-HE, but
 * implicit packages are created, so the class is present in HE. The top-level module of a stylesheet/package
 * will always be represented by an XSLPackage object, but if the original name was xsl:stylesheet or xsl:transform
 * then this original name will be present as the name of the element.
 */
public class XSLPackage extends XSLModuleRoot {

    private String nameAtt = null;
    private PackageVersion packageVersion = null;
    private boolean declaredModes = true;
    private boolean prepared = false;

    /**
     * Initialise a new ElementImpl with an element name
     *
     * @param elemName       Integer representing the element name, with namespaces resolved
     * @param elementType    the schema type of the element node
     * @param atts           The attribute list: always null
     * @param parent         The parent node
     * @param sequenceNumber Integer identifying this element within the document
     */
    @Override
    public void initialise(NodeName elemName, SchemaType elementType, AttributeCollectionImpl atts, NodeInfo parent, int sequenceNumber) {
        super.initialise(elemName, elementType, atts, parent, sequenceNumber);
        declaredModes = getLocalPart().equals("package");
    }

    /**
     * Get the name of the package (the value of its @name attribute)
     * @return the name of the package, or null if the @name attribute is omitted
     */

    public String getName() {
        if (nameAtt == null) {
            try {
                prepareAttributes();
            } catch (XPathException e) {
                nameAtt = "default";
            }
        }
        return nameAtt;
    }

    /**
     * Get the requested XSLT version (the value of the @version attribute)
     * @return the value of the @version attribute, times ten as an integer
     */

    public int getVersion() {
        if (version == -1) {
            try {
                prepareAttributes();
            } catch (XPathException e) {
                version = 30;
            }
        }
        return version;
    }

    public VersionedPackageName getNameAndVersion() {
        return new VersionedPackageName(getName(), getPackageVersion());
    }

    /**
     * Get the package version (the value of the @package-version attribute)
     * @return the value of the @package-version attribute, defaulting to "1.0"
     */
    public PackageVersion getPackageVersion() {
        if (packageVersion == null) {
            try {
                prepareAttributes();
            } catch (XPathException e) {
                packageVersion = PackageVersion.ONE;
            }
        }
        return packageVersion;
    }



    @Override
    protected void prepareAttributes() throws XPathException {
        if (prepared) {
            // already done
            return;
        }
        prepared = true;

        String inputTypeAnnotationsAtt = null;
        String packageVersionAtt = null;
        AttributeCollection atts = getAttributeList();

        for (int a = 0; a < atts.getLength(); a++) {
            String f = atts.getQName(a);
            if (f.equals("name") && getLocalPart().equals("package")) {
                nameAtt = Whitespace.trim(atts.getValue(a));
            } else if (f.equals("id")) {
                // no action
            } else if (f.equals("version")) {
                if (version == -1) {
                    processVersionAttribute("");
                }
            } else if (f.equals("package-version") && getLocalPart().equals("package")) {
                packageVersionAtt = Whitespace.trim(atts.getValue(a));

            } else if (f.equals("declared-modes") && getLocalPart().equals("package")) {
                declaredModes = processBooleanAttribute("declared-modes", atts.getValue(a));
//            } else if (f.equals("default-validation")) {
//                // already handled
//                String val = Whitespace.trim(atts.getValue(a));
//                defaultValidation = Validation.getCode(val);
//                if (defaultValidation == Validation.INVALID ||
//                    defaultValidation == Validation.STRICT || defaultValidation == Validation.LAX) {
//                    compileError("Invalid value for default-validation attribute. " +
//                        "Permitted values are (preserve, strip)", "XTSE0020");
//                } else if (!isSchemaAware() && defaultValidation != Validation.STRIP) {
//                    defaultValidation = Validation.STRIP;
//                    compileError("default-validation='" + val + "' requires a schema-aware processor",
//                            "XTSE1660");
//                }
            } else if (f.equals("input-type-annotations")) {
                inputTypeAnnotationsAtt = atts.getValue(a);
            } else {
                checkUnknownAttribute(atts.getNodeName(a));
            }
        }

        if (packageVersionAtt == null) {
            packageVersion = PackageVersion.ONE;
        } else {
            try {
                packageVersion = new PackageVersion(packageVersionAtt);
            } catch (XPathException ex) {
                compileErrorInAttribute(ex.getMessage(), ex.getErrorCodeLocalPart(), "package-version");
            }
        }

        if (version == -1) {
            version = 30;
            reportAbsence("version");
        }
        if (inputTypeAnnotationsAtt != null) {
            if (inputTypeAnnotationsAtt.equals("strip")) {
                //setInputTypeAnnotations(ANNOTATION_STRIP);
            } else if (inputTypeAnnotationsAtt.equals("preserve")) {
                //setInputTypeAnnotations(ANNOTATION_PRESERVE);
            } else if (inputTypeAnnotationsAtt.equals("unspecified")) {
                //
            } else {
                compileError("Invalid value for input-type-annotations attribute. " +
                        "Permitted values are (strip, preserve, unspecified)", "XTSE0020");
            }
        }

    }

    /**
     * Determine whether forwards-compatible mode is enabled for this element
     *
     * @return true if forwards-compatible mode is enabled
     */

//    public boolean forwardsCompatibleModeIsEnabled() {
//        return false;
//    }

    /**
     * Ask whether it is required that modes be explicitly declared
     *
     * @return true if modes referenced within this package must be explicitly declared
     */
    @Override
    public boolean isDeclaredModes() {
        if (nameAtt == null) {
            try {
                prepareAttributes();
            } catch (XPathException e) {
                // it will be reported later
                nameAtt = null;
            }
        }
        return declaredModes;
    }

    /**
     * Recursive walk through the stylesheet to validate all nodes
     * @param decl not used
     * @throws XPathException if invalid
     */


    public void validate(ComponentDeclaration decl) throws XPathException {

        AxisIterator kids = iterateAxis(AxisInfo.CHILD);
        NodeImpl child;
        while ((child = (NodeImpl)kids.next()) != null) {
            int fp = child.getFingerprint();
            if (child.getNodeKind() == Type.TEXT ||
                    (child instanceof StyleElement && ((StyleElement) child).isDeclaration()) ||
                    child instanceof DataElement ) {
                // all is well
            } else if (getLocalPart().equals("package") &&
                    (fp == StandardNames.XSL_USE_PACKAGE || fp == StandardNames.XSL_EXPOSE)) {
                // all is well
            } else if (!NamespaceConstant.XSLT.equals(child.getURI()) && !"".equals(child.getURI())) {
                // elements in other namespaces are allowed and ignored
            } else if (child instanceof AbsentExtensionElement && ((StyleElement) child).forwardsCompatibleModeIsEnabled()) {
                // this is OK: an unknown XSLT element is allowed in forwards compatibility mode
            } else if (NamespaceConstant.XSLT.equals(child.getURI())) {
                ((StyleElement) child).compileError("Element " + child.getDisplayName() +
                        " must not appear directly within " + getDisplayName(), "XTSE0010");
            } else {
                ((StyleElement) child).compileError("Element " + child.getDisplayName() +
                        " must not appear directly within " + getDisplayName() +
                        " because it is not in a namespace", "XTSE0130");
            }
        }

    }



}
