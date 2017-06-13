////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import net.sf.saxon.Configuration;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.value.ObjectValue;

/**
 * This class represents the type of an external Java object returned by
 * an extension function, or supplied as an external variable/parameter.
 */

public class JavaExternalObjectType extends ExternalObjectType implements ItemType {

    public static JavaExternalObjectType EXTERNAL_OBJECT_TYPE = new JavaExternalObjectType(Object.class);

    private Class javaClass;

    /**
     * Create an external object type.
     *
     * @param javaClass the Java class to which this type corresponds
     */

    public JavaExternalObjectType(Class javaClass) {
        this.javaClass = javaClass;
    }

    /**
     * Create an external object type.
     *
     * @param javaClass the Java class to which this type corresponds
     * @param config    the Saxon configuration. This argument is no longer used (since 9.5).
     */

    public JavaExternalObjectType(/*@NotNull*/ Class javaClass, /*@NotNull*/ Configuration config) {
        this(javaClass);
    }

    /**
     * Get the corresponding {@link net.sf.saxon.type.UType}. A UType is a union of primitive item
     * types.
     *
     * @return the smallest UType that subsumes this item type
     */
    public UType getUType() {
        return UType.EXTENSION;
    }

    /**
     * Get the local name of this type.
     *
     * @return the fully qualified name of the Java class.
     */

    /*@Nullable*/
    public String getName() {
        return javaClass.getName();
    }

    /**
     * Get the target namespace of this type. The is always NamespaceConstant.JAVA_TYPE.
     *
     * @return the target namespace of this type definition.
     */

    /*@Nullable*/
    public String getTargetNamespace() {
        return NamespaceConstant.JAVA_TYPE;
    }

    /**
     * Return true if this is an external object type, that is, a Saxon-defined type for external
     * Java or .NET objects
     *
     * @return true (always)
     */

    public boolean isExternalType() {
        return true;
    }

    /**
     * Get the name of this type as a StructuredQName, unless the type is anonymous, in which case
     * return null
     *
     * @return the name of the atomic type, or null if the type is anonymous.
     */

    /*@Nullable*/
    public StructuredQName getTypeName() {
        return classNameToQName(javaClass.getName());
    }

    /**
     * Get the primitive item type corresponding to this item type.
     *
     * @return EXTERNAL_OBJECT_TYPE, the ExternalObjectType that encapsulates
     *         the Java type Object.class.
     */

    /*@NotNull*/
    public ItemType getPrimitiveItemType() {
        return EXTERNAL_OBJECT_TYPE;
    }

    /**
     * Get the primitive type corresponding to this item type. For item(),
     * this is Type.ITEM. For node(), it is Type.NODE. For specific node kinds,
     * it is the value representing the node kind, for example Type.ELEMENT.
     * For anyAtomicValue it is Type.ATOMIC. For numeric it is Type.NUMBER.
     * For other atomic types it is the primitive type as defined in XML Schema,
     * except that INTEGER is considered to be a primitive type.
     */

    public int getPrimitiveType() {
        return StandardNames.XS_ANY_ATOMIC_TYPE;
    }

    /**
     * Get the item type of the atomic values that will be produced when an item
     * of this type is atomized
     *
     * @return BuiltInAtomicType.STRING, because atomization returns the result of toString()
     */

    public AtomicType getAtomizedItemType() {
        return BuiltInAtomicType.STRING;
    }

    /**
     * Ask whether values of this type are atomizable
     *
     * @return true, because (since Saxon 9.5) atomization will always return the result of toString()
     */

    public boolean isAtomizable() {
        return true;
    }

    /**
     * Get the relationship of this external object type to another external object type
     *
     * @param other the other external object type
     * @return the relationship of this external object type to another external object type,
     *         as one of the constants in class {@link TypeHierarchy}, for example {@link TypeHierarchy#SUBSUMES}
     */

    public int getRelationship(/*@NotNull*/ JavaExternalObjectType other) {
        Class j2 = other.javaClass;
        if (javaClass.equals(j2)) {
            return TypeHierarchy.SAME_TYPE;
        } else if (javaClass.isAssignableFrom(j2)) {
            return TypeHierarchy.SUBSUMES;
        } else if (j2.isAssignableFrom(javaClass)) {
            return TypeHierarchy.SUBSUMED_BY;
        } else if (javaClass.isInterface() || j2.isInterface()) {
            return TypeHierarchy.OVERLAPS; // there may be an overlap, we play safe
        } else {
            return TypeHierarchy.DISJOINT;
        }
    }

    /*@NotNull*/
    public String getDescription() {
        return getDisplayName();
    }

    /**
     * Test whether this item type is an atomic type
     *
     * @return false, this is not considered to be an atomic type
     */

    public boolean isAtomicType() {
        return false;
    }

    /**
     * Get the Java class to which this external object type corresponds
     *
     * @return the corresponding Java class
     */

    public Class getJavaClass() {
        return javaClass;
    }

    /**
     * Test whether a given item conforms to this type
     *
     *
     *
     * @param item    The item to be tested
     * @param th
     * @return true if the item is an instance of this type; false otherwise
     */
    public boolean matches(/*@NotNull*/ Item item, /*@NotNull*/TypeHierarchy th) {
        if (item instanceof ObjectValue) {
            Object obj = ((ObjectValue) item).getObject();
            return javaClass.isAssignableFrom(obj.getClass());
        }
        return false;
    }


    /*@NotNull*/
    public String toString() {
        return classNameToQName(javaClass.getName()).getEQName();
    }

    /*@NotNull*/
    public String getDisplayName() {
        return "java-type:" + javaClass.getName();
    }

    /**
     * Determine the default priority of this item type when used on its own as a Pattern
     *
     * @return the default priority
     */
    public double getDefaultPriority() {
        return 0;
    }

    /**
     * Visit all the schema components used in this ItemType definition
     *
     * @param visitor the visitor class to be called when each component is visited
     * @throws net.sf.saxon.trans.XPathException
     *          if an error occurs
     */
    public void visitNamedSchemaComponents(SchemaComponentVisitor visitor) throws XPathException {
        // no action
    }

    /**
     * Returns a hash code value for the object.
     */

    public int hashCode() {
        return javaClass.hashCode();
    }

    /**
     * Test whether two ExternalObjectType objects represent the same type
     *
     * @param obj the other ExternalObjectType
     * @return true if the two objects represent the same type
     */

    public boolean equals(/*@NotNull*/ Object obj) {
        return obj instanceof JavaExternalObjectType && javaClass == ((JavaExternalObjectType) obj).javaClass;
    }

    /**
     * Static method to convert a Java class name to an XPath local name. This involves the
     * following substitutions: "$" is replaced by "-", and "[" is replaced by "_-".
     */

    public static String classNameToLocalName(String className) {
        return className.replace('$', '-').replace("[", "_-");
    }

    /**
     * Static method to convert an XPath local name to a Java class name. This involves the
     * following substitutions: "-" is replaced by "$", and leading "_-" pairs are replaced by "[".
     */

    public static String localNameToClassName(String className) {
        FastStringBuffer fsb = new FastStringBuffer(className.length());
        boolean atStart = true;
        for (int i=0; i<className.length(); i++) {
            char c = className.charAt(i);
            if (atStart) {
                if (c == '_' && i+1 < className.length()  && className.charAt(i+1) == '-') {
                    fsb.append('[');
                    i++;
                } else {
                    atStart = false;
                    fsb.append(c == '-' ? '$' : c);
                }
            } else {
                fsb.append(c == '-' ? '$' : c);
            }
        }
        return fsb.toString();
    }

    /**
     * Static method to get the QName corresponding to a Java class name
     */

    public static StructuredQName classNameToQName(String className) {
        return new StructuredQName("jt", NamespaceConstant.JAVA_TYPE, classNameToLocalName(className));
    }


}