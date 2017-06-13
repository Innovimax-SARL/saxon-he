////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.Configuration;
import net.sf.saxon.Controller;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.SchemaURIResolver;
import net.sf.saxon.lib.UnfailingErrorListener;
import net.sf.saxon.om.Item;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.URIResolver;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

/**
 * A PipelineConfiguration sets options that apply to all the operations in a pipeline.
 * Unlike the global Configuration, these options are always local to a process.
 */

public class PipelineConfiguration {

    /*@NotNull*/ private Configuration config;
    private URIResolver uriResolver;
    private SchemaURIResolver schemaURIResolver;
    private Controller controller;
    private Stack<Item> currentApplyStack;
    private ParseOptions parseOptions;
    private int hostLanguage = Configuration.XSLT;
    private Map<String, Object> components;
    private boolean locationIsCodeLocation = false;

    /**
     * Create a PipelineConfiguration. Note: the normal way to create
     * a PipelineConfiguration is via the factory methods in the Controller and
     * Configuration classes
     *
     * @param config the Saxon configuration
     * @see Configuration#makePipelineConfiguration
     * @see Controller#makePipelineConfiguration
     */

    public PipelineConfiguration(/*@NotNull*/ Configuration config) {
        this.config = config;
        parseOptions = new ParseOptions();
    }

    /**
     * Create a PipelineConfiguration as a copy of an existing
     * PipelineConfiguration
     *
     * @param p the existing PipelineConfiguration
     */

    public PipelineConfiguration(PipelineConfiguration p) {
        config = p.config;
        uriResolver = p.uriResolver;
        schemaURIResolver = p.schemaURIResolver;
        controller = p.controller;
        parseOptions = new ParseOptions(p.parseOptions);
        hostLanguage = p.hostLanguage;
        if (p.components != null) {
            components = new HashMap<String, Object>(p.components);
        }
    }

    /**
     * Get the Saxon Configuration object
     *
     * @return the Saxon Configuration
     */

    /*@NotNull*/
    public Configuration getConfiguration() {
        return config;
    }

    /**
     * Set the Saxon Configuration object
     *
     * @param config the Saxon Configuration
     */

    public void setConfiguration(/*@NotNull*/ Configuration config) {
        this.config = config;
    }

    /**
     * Get the ErrorListener set as a property of this pipeline
     *
     * @return the ErrorListener; null if none has been set.
     */

    public UnfailingErrorListener getLocalErrorListener() {
        return parseOptions.getErrorListener();
    }

    /**
     * Get an ErrorListener for reporting errors in processing this pipeline; this
     * will be the ErrorListener set locally in the PipelineConfiguration if there is one,
     * or the ErrorListener from the Configuration otherwise.
     *
     * @return the ErrorListener to be used; never null
     */

    public UnfailingErrorListener getErrorListener() {
        UnfailingErrorListener listener = parseOptions.getErrorListener();
        if (listener == null) {
            listener = config.getErrorListener();
        }
        return listener;
    }

    /**
     * Set the ErrorListener used for reporting errors in processing this pipeline
     *
     * @param errorListener the ErrorListener
     */

    public void setErrorListener(ErrorListener errorListener) {
        parseOptions.setErrorListener(errorListener);
    }

    /**
     * Get the URIResolver used for processing URIs encountered on this pipeline
     *
     * @return the URIResolver
     */

    public URIResolver getURIResolver() {
        return uriResolver;
    }

    /**
     * Set the URIResolver used for processing URIs encountered on this pipeline
     *
     * @param uriResolver the URIResolver
     */

    public void setURIResolver(URIResolver uriResolver) {
        this.uriResolver = uriResolver;
    }

    /**
     * Get the user-defined SchemaURIResolver for resolving URIs used in "import schema"
     * declarations; returns null if none has been explicitly set.
     *
     * @return the SchemaURIResolver
     */

    public SchemaURIResolver getSchemaURIResolver() {
        return schemaURIResolver;
    }

    /**
     * Set the document parsing and building options to be used on this pipeline
     *
     * @param options the options to be used
     */

    public void setParseOptions(ParseOptions options) {
        parseOptions = options;
    }

    /**
     * Get the document parsing and building options to be used on this pipeline
     * return the options to be used
     *
     * @return the parser options for this pipeline
     */

    public ParseOptions getParseOptions() {
        return parseOptions;
    }

    /**
     * Say whether xsi:schemaLocation and xsi:noNamespaceSchemaLocation attributes
     * should be recognized while validating an instance document
     *
     * @param recognize true if these attributes should be recognized
     */

    public void setUseXsiSchemaLocation(boolean recognize) {
        parseOptions.setUseXsiSchemaLocation(recognize);
    }

    /**
     * Say whether validation errors encountered on this pipeline should be treated as fatal
     * or as recoverable.
     * <p>Note this is a shortcut for <code>getParseOptions().setContinueAfterValidationErrors()</code>, retained
     * for backwards compatibility.</p>
     *
     * @param recover set to true if validation errors are to be treated as recoverable. If this option is set to true,
     *                such errors will be reported to the ErrorListener using the error() method, and validation will continue.
     *                If it is set to false, errors will be reported using the fatalError() method, and validation will
     *                be abandoned.  The default depends on the circumstances: typically during standalone instance validation
     *                the default is true, but during XSLT and XQuery processing it is false.
     */

    public void setRecoverFromValidationErrors(boolean recover) {
        parseOptions.setContinueAfterValidationErrors(recover);
    }

    /**
     * Ask if this pipeline recovers from validation errors
     * <p>Note this is a shortcut for <code>getParseOptions().isContinueAfterValidationErrors()</code>, retained
     * for backwards compatibility.</p>
     *
     * @return true if validation errors on this pipeline are treated as recoverable; false if they are treated
     *         as fatal
     */

    public boolean isRecoverFromValidationErrors() {
        return parseOptions.isContinueAfterValidationErrors();
    }

    /**
     * Set a user-defined SchemaURIResolver for resolving URIs used in "import schema"
     * declarations.
     *
     * @param resolver the SchemaURIResolver
     */

    public void setSchemaURIResolver(SchemaURIResolver resolver) {
        schemaURIResolver = resolver;
    }

    /**
     * Get the controller associated with this pipelineConfiguration
     *
     * @return the controller if it is known; otherwise null.
     */

    public Controller getController() {
        return controller;
    }

    /**
     * Set the context item. Used only for diagnostics, to indicate
     * what part of the source document is being processed. Maintained
     * only by XSLT instructions that change the context, e.g. for-each,
     * apply-templates, iterate, and for-each-group
     *
     * @param item the context item
     */

    public void pushCurrentAppliedItem(Item item) {
        if (currentApplyStack == null) {
            currentApplyStack = new Stack<Item>();
        }
        currentApplyStack.push(item);
    }


    public void popCurrentAppliedItem() {
        currentApplyStack.pop();
    }

    public Item peekCurrentAppliedItem() {
        if (currentApplyStack != null && currentApplyStack.size() > 0) {
            return currentApplyStack.peek();
        } else {
            return null;
        }
    }

    public Stack<Item> getAppliedItemStack() {
        return currentApplyStack;
    }

    /**
     * Set the Controller associated with this pipelineConfiguration
     *
     * @param controller the Controller
     */

    public void setController(Controller controller) {
        this.controller = controller;
    }

    /**
     * Get the host language in use
     *
     * @return for example {@link Configuration#XSLT} or {@link Configuration#XQUERY}
     */

    public int getHostLanguage() {
        return hostLanguage;
    }

    /**
     * Set the host language in use
     *
     * @param language for example {@link Configuration#XSLT} or {@link Configuration#XQUERY}
     */

    public void setHostLanguage(int language) {
        hostLanguage = language;
    }

    /**
     * Set whether attribute defaults defined in a schema or DTD are to be expanded or not
     * (by default, fixed and default attribute values are expanded, that is, they are inserted
     * into the document during validation as if they were present in the instance being validated)
     *
     * @param expand true if defaults are to be expanded, false if not
     */

    public void setExpandAttributeDefaults(boolean expand) {
        parseOptions.setExpandAttributeDefaults(expand);
    }

    /**
     * Set a named component of the pipeline
     *
     * @param name  string the component name
     * @param value the component value
     */

    public void setComponent(String name, Object value) {
        if (components == null) {
            components = new HashMap<String, Object>();
        }
        components.put(name, value);
    }

    /**
     * Get a named component of the pipeline
     *
     * @param name string the component name
     * @return the component value, or null if absent
     */

    public Object getComponent(String name) {
        if (components == null) {
            return null;
        } else {
            return components.get(name);
        }
    }

    public boolean isLocationIsCodeLocation() {
        return locationIsCodeLocation;
    }

    public void setLocationIsCodeLocation(boolean locationIsCodeLocation) {
        this.locationIsCodeLocation = locationIsCodeLocation;
    }

}

