////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


package net.sf.saxon.functions;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ExplicitLocation;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AtomicType;
import net.sf.saxon.type.Converter;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;

import java.util.*;

/**
 * This class implements the rules for options parameters, as used in functions such as parse-json, serialize,
 * json-to-XML, map:merge. It provides a convenient way of ensuring that the options parameter conventions
 * are enforced.
 */
public class OptionsParameter {

    private Map<String, SequenceType> allowedOptions = new HashMap<String, SequenceType>(8);
    private Map<String, Sequence> defaultValues = new HashMap<String, Sequence>(8);
    private Set<String> requiredOptions = new HashSet<String>(4);
    private Map<String, Set<String>> allowedValues = new HashMap<String, Set<String>>(8);
    private String errorCodeForDisallowedValue;
    private String errorCodeForAbsentValue = "SXJE9999";
    private boolean allowCastFromString = false;

    public OptionsParameter() {};

    /**
     * Register a permitted option keyword, and the associated type of value, without defining
     * a default value
     * @param name the option keyword
     * @param type the required type
     */
    public void addAllowedOption(String name, SequenceType type) {
        allowedOptions.put(name, type);
    }

    /**
     * Register a required option keyword, and the associated type of value, where omitting
     * the value is an error
     *
     * @param name the option keyword
     * @param type the required type
     */
    public void addRequiredOption(String name, SequenceType type) {
        allowedOptions.put(name, type);
        requiredOptions.add(name);
    }

    /**
     * Register a permitted option keyword, and the associated type of value, with a default value
     *
     * @param name the option keyword
     * @param type the required type
     * @param defaultValue the default value if the option is not specified; or null
     *                                 if no default is defined
     */
    public void addAllowedOption(String name, SequenceType type, Sequence defaultValue) {
        allowedOptions.put(name, type);
        if (defaultValue != null) {
            defaultValues.put(name, defaultValue);
        }
    }

    /**
     * Enumerate the permitted values for an option keyword
     * @param name the option keyword
     * @param errorCode the error to be reported if the supplied value is not one of those permitted
     * @param values the permitted values
     */

    public void setAllowedValues(String name, String errorCode, String... values) {
        HashSet<String> valueSet = new HashSet<String>(Arrays.asList(values));
        allowedValues.put(name, valueSet);
        errorCodeForDisallowedValue = errorCode;
    }

    /**
     * Process an XPath map containing the supplied values. Options that are recognized are copied
     * into the result map, after validation and expansion of defaults. Unrecognized options are
     * ignored, in accordance with the option parameter conventions
     * @param supplied the supplied options as an XPath map object
     * @param context the dynamic evaluation context
     * @return the validated options as a Java map
     * @throws XPathException if any supplied options are invalid
     */

    public Map<String, Sequence> processSuppliedOptions(MapItem supplied, XPathContext context) throws XPathException {
        Map<String, Sequence> result = new HashMap<String, Sequence>();
        TypeHierarchy th = context.getConfiguration().getTypeHierarchy();

        for (String req : requiredOptions) {
            if (supplied.get(new StringValue(req)) == null) {
                throw new XPathException("No value supplied for required option: " + req, errorCodeForAbsentValue);
            }
        }

        for (Map.Entry<String, SequenceType> allowed : allowedOptions.entrySet()) {
            String key = allowed.getKey();
            SequenceType required = allowed.getValue();
            Sequence actual = supplied.get(new StringValue(key));
            if (actual != null) {
                if (!required.matches(actual, th)) {
                    boolean ok = false;
                    if (actual instanceof StringValue && allowCastFromString
                            && required.getPrimaryType() instanceof AtomicType) {
                        try {ConversionRules rules = context.getConfiguration().getConversionRules();
                            actual = Converter.convert((StringValue)actual, (AtomicType)required.getPrimaryType(), rules);
                            ok = true;
                        } catch (XPathException err) {
                            ok = false;
                        }
                    }
                    if (!ok) {
                        RoleDiagnostic role = new RoleDiagnostic(RoleDiagnostic.OPTION, key, 0);
                        role.setErrorCode("XPTY0004");
                        actual = th.applyFunctionConversionRules(
                                actual, required, role, ExplicitLocation.UNKNOWN_LOCATION);
                    }
                }
                actual = SequenceTool.toGroundedValue(actual).reduce();
                Set<String> permitted = allowedValues.get(key);
                if (permitted != null) {
                    if (!(actual instanceof AtomicValue) || !permitted.contains(((AtomicValue)actual).getStringValue())) {
                        String message = "Invalid option " + key + "=" + Err.depictSequence(actual) + ". Valid values are:";
                        int i = 0;
                        for (String v : permitted) {
                            message += (i++ == 0 ? " ": ", ") + v;
                        }
                        throw new XPathException(message, errorCodeForDisallowedValue);
                    }
                }
                result.put(key, actual);
            } else {
                Sequence def = defaultValues.get(key);
                if (def != null) {
                    result.put(key, def);
                }
            }
        }

        return result;

    }

    /**
     * Get a Java map containing the default values of registered options
     * @return a map containing the default values
     */

    public Map<String, Sequence> getDefaultOptions()  {
        Map<String, Sequence> result = new HashMap<String, Sequence>();
        for (Map.Entry<String, Sequence> entry : defaultValues.entrySet()) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    public String getErrorCodeForAbsentValue() {
        return errorCodeForAbsentValue;
    }

    public void setErrorCodeForAbsentValue(String errorCodeForAbsentValue) {
        this.errorCodeForAbsentValue = errorCodeForAbsentValue;
    }


    public boolean isAllowCastFromString() {
        return allowCastFromString;
    }

    public void setAllowCastFromString(boolean allowCastFromString) {
        this.allowCastFromString = allowCastFromString;
    }


}

