////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;

import net.sf.saxon.Configuration;
import net.sf.saxon.Controller;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.RetainedStaticContext;
import net.sf.saxon.functions.IntegratedFunctionLibrary;
import net.sf.saxon.functions.IsIdRef;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.MultipleNodeKindTest;
import net.sf.saxon.pattern.NodeTestPattern;
import net.sf.saxon.pattern.Pattern;
import net.sf.saxon.pattern.BasePatternWithPredicate;
import net.sf.saxon.sxpath.IndependentContext;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.tree.iter.ListIterator;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.Converter;
import net.sf.saxon.type.UType;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.DoubleValue;
import net.sf.saxon.value.NumericValue;
import net.sf.saxon.z.IntHashMap;

import java.lang.ref.WeakReference;
import java.util.*;


/**
 * KeyManager manages the set of key definitions in a stylesheet, and the indexes
 * associated with these key definitions. It handles xsl:sort-key as well as xsl:key
 * definitions.
 * <p/>
 * <p>The memory management in this class is subtle, with extensive use of weak references.
 * The idea is that an index should continue to exist in memory so long as both the compiled
 * stylesheet and the source document exist in memory: if either is removed, the index should
 * go too. The document itself holds no reference to the index. The compiled stylesheet (which
 * owns the KeyManager) holds a weak reference to the index. The index, of course, holds strong
 * references to the nodes in the document. The Controller holds a strong reference to the
 * list of indexes used for each document, so that indexes remain in memory for the duration
 * of a transformation even if the documents themselves are garbage collected.</p>
 * <p/>
 * <p>Potentially there is a need for more than one index for a given key name, depending
 * on the primitive type of the value provided to the key() function. An index is built
 * corresponding to the type of the requested value; if subsequently the key() function is
 * called with the same name and a different type of value, then a new index is built.</p>
 * <p/>
 * <p>For XSLT-defined keys, equality matching follows the rules of the eq operator, which means
 * that untypedAtomic values are treated as strings. In backwards compatibility mode, <i>all</i>
 * values are converted to strings.</p>
 * <p/>
 * <p>This class is also used for internal indexes constructed (a) to support the idref() function,
 * and (b) (in Saxon-EE only) to support filter expressions of the form /a/b/c[d=e], where the
 * path expression being filtered must be a single-document context-free path rooted at a document node,
 * where exactly one of d and e must be dependent on the focus, and where certain other conditions apply
 * such as the filter predicate not being positional. The operator in this case may be either "=" or "eq".
 * If it is "eq", then the semantics are very similar to xsl:key indexes, except that use of non-comparable
 * types gives an error rather than a non-match. If the operator is "=", however, then the rules for
 * handling untypedAtomic values are different: these must be converted to the type of the other operand.
 * In this situation the following rules apply. Assume that the predicate is [use=value], where use is
 * dependent on the focus (the indexed value), and value is the sought value.</p>
 * <p/>
 * <ul>
 * <li>If value is a type other than untypedAtomic, say T, then we build an index for type T, in which any
 * untypedAtomic values that arise in evaluating "use" are converted to type T. A conversion failure results
 * in an error. A value of a type that is not comparable to T also results in an error.</li>
 * <li>If value is untypedAtomic, then we build an index for every type actually encountered in evaluating
 * the use expression (treating untypedAtomic as string), and then search each of these indexes. (Note that
 * it is not an error if the use expression returns a mixture of say numbers and dates, provided that the
 * sought value is untypedAtomic).</li>
 * </ul>
 *
 * @author Michael H. Kay
 */

public class KeyManager {

    private PackageData packageData;

    // a dummy index, always empty, to act as a marker
    private final static KeyIndex underConstruction = new KeyIndex(false);

    private HashMap<StructuredQName, KeyDefinitionSet> keyDefinitions;
    // one entry for each named key; the entry contains
    // a KeyDefinitionSet holding the key definitions with that name
    private transient WeakHashMap<TreeInfo, WeakReference<IntHashMap<KeyIndex>>> docIndexes;
    // one entry for each document that is in memory;
    // the entry contains a HashMap mapping the fingerprint of the key name plus the primitive item type
    // to the HashMap that is the actual index of key/value pairs.

    /**
     * Create a KeyManager and initialise variables
     *
     * @param config the Saxon configuration
     * @param pack the package in which these keys are available
     */

    public KeyManager(Configuration config, PackageData pack) {
        packageData = pack;
        keyDefinitions = new HashMap<StructuredQName, KeyDefinitionSet>(10);
        docIndexes = new WeakHashMap<TreeInfo, WeakReference<IntHashMap<KeyIndex>>>(10);
        // Create a key definition for the idref() function
        registerIdrefKey(config);
    }

    /**
     * An internal key definition is used to support the idref() function. The key definition
     * is equivalent to xsl:key match="element(*, xs:IDREF) | element(*, IDREFS) |
     * attribute(*, xs:IDREF) | attribute(*, IDREFS)" use="tokenize(string(.))". This method creates this
     * key definition.
     *
     * @param config The configuration. This is needed because the patterns that are
     *               generated need access to schema information.
     */

    private void registerIdrefKey(Configuration config) {
        BasePatternWithPredicate pp = new BasePatternWithPredicate(
            new NodeTestPattern(new MultipleNodeKindTest(UType.ELEMENT_OR_ATTRIBUTE)),
            IntegratedFunctionLibrary.makeFunctionCall(new IsIdRef(), new Expression[]{})
        );
        try {
            IndependentContext sc = new IndependentContext(config);
            sc.setPackageData(packageData);
            sc.setXPathLanguageLevel(31);
            RetainedStaticContext rsc = new RetainedStaticContext(sc);
            Expression sf = SystemFunction.makeCall("string", rsc, new ContextItemExpression());
            Expression use = SystemFunction.makeCall("tokenize", rsc, sf);    // Use the new tokenize#1
            final StructuredQName qName = StandardNames.getStructuredQName(StandardNames.XS_IDREFS);
            SymbolicName symbolicName = new SymbolicName(StandardNames.XSL_KEY, qName);
            KeyDefinition key = new KeyDefinition(symbolicName, pp, use, null, null);
            key.setPackageData(packageData);
            key.setIndexedItemType(BuiltInAtomicType.STRING);
            addKeyDefinition(qName, key, true, config);
        } catch (XPathException err) {
            throw new AssertionError(err); // shouldn't happen
        }
    }

    /**
     * Pre-register a key definition. This simply registers that a key with a given name exists,
     * without providing any details.
     *
     * @param keyName the name of the key to be pre-registered
     */

    public void preRegisterKeyDefinition(StructuredQName keyName) {
        KeyDefinitionSet keySet = keyDefinitions.get(keyName);
        if (keySet == null) {
            keySet = new KeyDefinitionSet(keyName, keyDefinitions.size());
            keyDefinitions.put(keyName, keySet);
        }
    }

    /**
     * Register a key definition. Note that multiple key definitions with the same name are
     * allowed
     *
     *
     * @param keyName Structured QName representing the name of the key
     * @param keydef  The details of the key's definition
     * @param reusable Set to true if indexes using this key definition can be used across multiple transformations, false if
     * the indexes need to be rebuilt for each transformation. Indexes are not reusable if the key definition contains references
     * to global variables or parameters, or calls used-defined functions or templates that might contain such references.
     * @param config  The configuration
     * @throws XPathException if this key definition is inconsistent with existing key definitions having the same name
     */

    public void addKeyDefinition(StructuredQName keyName, KeyDefinition keydef, boolean reusable, Configuration config) throws XPathException {
        KeyDefinitionSet keySet = keyDefinitions.get(keyName);
        if (keySet == null) {
            keySet = new KeyDefinitionSet(keyName, keyDefinitions.size());
            keyDefinitions.put(keyName, keySet);
        }
        keySet.addKeyDefinition(keydef);

        if (!reusable) {
            keySet.setReusable(false);
        }

        boolean backwardsCompatible = keySet.isBackwardsCompatible();

        if (backwardsCompatible) {
            // In backwards compatibility mode, convert all the use-expression results to sequences of strings
            List<KeyDefinition> v = keySet.getKeyDefinitions();
            for (KeyDefinition kd : v) {
                kd.setBackwardsCompatible(true);
                if (!kd.getBody().getItemType().equals(BuiltInAtomicType.STRING)) {
                    Expression exp = new AtomicSequenceConverter(kd.getBody(), BuiltInAtomicType.STRING);
                    ((AtomicSequenceConverter) exp).allocateConverter(config, false);
                    kd.setBody(exp);
                }
            }
        }

    }

    /**
     * Get all the key definitions that match a particular name
     *
     * @param qName The name of the required key
     * @return The set of key definitions of the named key if there are any, or null otherwise.
     */

    public KeyDefinitionSet getKeyDefinitionSet(StructuredQName qName) {
        return keyDefinitions.get(qName);
    }

    /**
     * Look for a key definition that matches a proposed new key
     * @param finder matches/selects the nodes to be indexed
     * @param use computes the value on which the nodes are indexed
     * @param collationName collation to be used
     * @return a KeyDefinitionSet containing a key with the required characteristics if there
     * is one, or null otherwise
     */

    public KeyDefinitionSet findKeyDefinition(Pattern finder, Expression use, String collationName) {
        for (KeyDefinitionSet keySet : keyDefinitions.values()) {
            if (keySet.getKeyDefinitions().size() == 1) {
                for (KeyDefinition keyDef : keySet.getKeyDefinitions()) {
                    if (keyDef.getMatch().equals(finder) &&
                            keyDef.getUse().equals(use) &&
                            keyDef.getCollationName().equals(collationName)) {
                        return keySet;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Build the index for a particular document for a named key
     *
     *
     * @param keySet         The set of key definitions with this name
     * @param doc            The source document in question
     * @param context        The dynamic context
     * @return the index in question, as a Map mapping a key value onto a List of nodes
     * @throws XPathException if a dynamic error is encountered
     */

    private synchronized KeyIndex buildIndex(KeyDefinitionSet keySet,
                                             TreeInfo doc,
                                             XPathContext context) throws XPathException {

        KeyIndex index = new KeyIndex(keySet.isRangeKey());
        index.buildIndex(keySet, doc, context);
        return index;
    }


    /**
     * Get the nodes with a given key value
     *
     * @param keySet      The set of key definitions identified by the key name used in the call to the key() function
     * @param doc         The source document in question
     * @param soughtValue The required key value
     * @param context     The dynamic context, needed only the first time when the key is being built
     * @return an iteration of the selected nodes, always in document order with no duplicates
     * @throws XPathException if a dynamic error is encountered
     */

    public SequenceIterator selectByKey(
            KeyDefinitionSet keySet,
            TreeInfo doc,
            AtomicValue soughtValue,
            XPathContext context) throws XPathException {

        if (soughtValue == null) {
            return EmptyIterator.OfNodes.THE_INSTANCE;
        }

        if (keySet.isBackwardsCompatible()) {
            // if backwards compatibility is in force, treat all values as strings
            final ConversionRules rules = context.getConfiguration().getConversionRules();
            soughtValue = Converter.convert(soughtValue, BuiltInAtomicType.STRING, rules).asAtomic();
        } else {
            // If the key value is numeric, promote it to a double
            // Note: this could result in two decimals comparing equal because they convert to the same double

            BuiltInAtomicType itemType = soughtValue.getPrimitiveType();
            if (itemType.equals(BuiltInAtomicType.INTEGER) ||
                    itemType.equals(BuiltInAtomicType.DECIMAL) ||
                    itemType.equals(BuiltInAtomicType.FLOAT)) {
                soughtValue = new DoubleValue(((NumericValue) soughtValue).getDoubleValue());
            }
        }

        // No special action needed for anyURI to string promotion (it just seems to work: tests idky44, 45)

        KeyIndex index = obtainIndex(keySet, doc, context);

        List<NodeInfo> nodes = index.get(soughtValue);
        if (nodes == null) {
            return EmptyIterator.emptyIterator();
        } else {
            return new ListIterator(nodes);
        }

    }

    /**
     * Get the nodes with a given composite key value
     *
     * @param keySet      The set of key definitions identified by the key name used in the call to the key() function
     * @param doc         The source document in question
     * @param soughtValue The required key value
     * @param context     The dynamic context, needed only the first time when the key is being built
     * @return an iteration of the selected nodes, always in document order with no duplicates
     * @throws XPathException if a dynamic error is encountered
     */

    public SequenceIterator selectByCompositeKey(
            KeyDefinitionSet keySet,
            TreeInfo doc,
            SequenceIterator soughtValue,
            XPathContext context) throws XPathException {


        KeyIndex index = obtainIndex(keySet, doc, context);

        List<NodeInfo> nodes = index.getComposite(soughtValue);
        if (nodes == null) {
            return EmptyIterator.emptyIterator();
        } else {
            return new ListIterator(nodes);
        }

    }

    /**
     * Get the index supporting a particular key definition for a particular document. The index is created
     * if it does not already exist.
     *
     * @param keySet the set of xsl:key definitions making up this key
     * @param doc the document to which the index applies
     * @param context the dynamic evaluation context
     * @return the relevant index
     * @throws XPathException if any failure occurs
     */

    public KeyIndex obtainIndex(KeyDefinitionSet keySet, TreeInfo doc, XPathContext context) throws XPathException {
        if (keySet.isReusable()) {
            return obtainSharedIndex(keySet, doc, context);
        } else {
            return obtainLocalIndex(keySet, doc, context);
        }
    }


    private KeyIndex obtainSharedIndex(KeyDefinitionSet keySet, TreeInfo doc, XPathContext context) throws XPathException {
        KeyIndex index;
        int keySetNumber = keySet.getKeySetNumber();
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (doc) {
            // Need to synchronize to prevent two threads that use the same stylesheet indexing the same source
            // document simultaneously. We could synchronize on either the key definition or the document
            // (ideally we would use the combination of the two), but the document is less likely to cause
            // unnecessary contention: it's more likely that an index definition applies to large numbers of
            // documents than that a document has large numbers of indexes.
            index = getSharedIndex(doc, keySetNumber);
            if (index == underConstruction) {
                // index is under construction
                XPathException de = new XPathException("Key definition is circular");
                de.setXPathContext(context);
                de.setErrorCode("XTDE0640");
                throw de;
            }

            // If the index does not yet exist, then create it.
            if (index == null) {
                // Mark the index as being under construction, in case the definition is circular
                putSharedIndex(doc, keySetNumber, underConstruction, context);
                index = buildIndex(keySet, doc, context);
                putSharedIndex(doc, keySetNumber, index, context);
            }
        }
        return index;
    }

    private KeyIndex obtainLocalIndex(KeyDefinitionSet keySet, TreeInfo doc, XPathContext context) throws XPathException {
        KeyIndex index;
        int keySetNumber = keySet.getKeySetNumber();
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (doc) {
            // Need to synchronize to prevent two threads that use the same stylesheet indexing the same source
            // document simultaneously. We could synchronize on either the key definition or the document
            // (ideally we would use the combination of the two), but the document is less likely to cause
            // unnecessary contention: it's more likely that an index definition applies to large numbers of
            // documents than that a document has large numbers of indexes.
            index = getLocalIndex(doc, keySetNumber, context);
            if (index == underConstruction) {
                // index is under construction
                XPathException de = new XPathException("Key definition is circular");
                de.setXPathContext(context);
                de.setErrorCode("XTDE0640");
                throw de;
            }

            // If the index does not yet exist, then create it.
            if (index == null) {
                // Mark the index as being under construction, in case the definition is circular
                putLocalIndex(doc, keySetNumber, underConstruction, context);
                index = buildIndex(keySet, doc, context);
                putLocalIndex(doc, keySetNumber, index, context);
            }
        }
        return index;
    }



    /**
     * Save the index associated with a particular key, a particular item type,
     * and a particular document. This
     * needs to be done in such a way that the index is discarded by the garbage collector
     * if the document is discarded. We therefore use a WeakHashMap indexed on the DocumentInfo,
     * which returns HashMap giving the index for each key fingerprint. This index is itself another
     * HashMap.
     * The methods need to be synchronized because several concurrent transformations (which share
     * the same KeyManager) may be creating indexes for the same or different documents at the same
     * time.
     *
     * @param doc            the document being indexed
     * @param keyFingerprint represents the name of the key definition
     * @param index          the index being saved
     * @param context        the dynamic evaluation context
     */

    private synchronized void putSharedIndex(TreeInfo doc, int keyFingerprint, KeyIndex index, XPathContext context) {
        if (docIndexes == null) {
            // it's transient, so it will be null when reloading a compiled stylesheet
            docIndexes = new WeakHashMap<TreeInfo, WeakReference<IntHashMap<KeyIndex>>>(10);
        }
        WeakReference<IntHashMap<KeyIndex>> indexRef = docIndexes.get(doc);
        IntHashMap<KeyIndex> indexList;
        if (indexRef == null || indexRef.get() == null) {
            indexList = new IntHashMap<KeyIndex>(10);
            // Ensure there is a firm reference to the indexList for the duration of a transformation
            // But for keys associated with temporary trees, or documents that have been discarded from
            // the document pool, keep the reference within the document node itself.
            Controller controller = context.getController();
            if (controller.getDocumentPool().contains(doc)) {
                context.getController().setUserData(doc, "saxon:key-index-list", indexList);
            } else {
                doc.setUserData("saxon:key-index-list", indexList);
            }
            docIndexes.put(doc, new WeakReference<IntHashMap<KeyIndex>>(indexList));
        } else {
            indexList = indexRef.get();
        }
        indexList.put(keyFingerprint, index);
    }

    /**
     * Save the index associated with a particular key, a particular item type,
     * and a particular document. This version of the method is used for indexes that are
     * not reusable across transformations, because the key depends on transformation-specific
     * data such as global variables or parameters.
     * The method still need to be synchronized because several threads within a transformation
     * may be creating indexes for the same or different documents at the same
     * time.
     *
     * @param doc            the document being indexed
     * @param keyFingerprint represents the name of the key definition
     * @param index          the index being saved
     * @param context        the dynamic evaluation context
     */

    private synchronized void putLocalIndex(TreeInfo doc, int keyFingerprint, KeyIndex index, XPathContext context) {
        Controller controller = context.getController();
        IntHashMap<KeyIndex> docIndexes =
                (IntHashMap<KeyIndex>)controller.getUserData(doc, "saxon:unshared-key-index-list");
        if (docIndexes == null) {
            docIndexes = new IntHashMap<KeyIndex>();
            controller.setUserData(doc, "saxon:unshared-key-index-list", docIndexes);
        }
        docIndexes.put(keyFingerprint, index);
    }


    /**
     * Get the shared index associated with a particular key, a particular source document,
     * and a particular primitive item type
     *
     *
     * @param doc            the document whose index is required
     * @param keyFingerprint the name of the key definition
     * @return either an index (as a HashMap), or the dummy map "under construction", or null
     */

    private synchronized KeyIndex getSharedIndex(TreeInfo doc, int keyFingerprint) {
        if (docIndexes == null) {
            // it's transient, so it will be null when reloading a compiled stylesheet
            docIndexes = new WeakHashMap<TreeInfo, WeakReference<IntHashMap<KeyIndex>>>(10);
        }
        WeakReference<IntHashMap<KeyIndex>> ref = docIndexes.get(doc);
        if (ref == null) {
            return null;
        }
        IntHashMap<KeyIndex> indexList = ref.get();
        if (indexList == null) {
            return null;
        }
        return indexList.get(keyFingerprint);
    }

    /**
     * Get the non-shared index associated with a particular key, a particular source document,
     * and a particular primitive item type
     *
     *
     * @param doc            the document whose index is required
     * @param keyFingerprint the name of the key definition
     * @param context        the dynamic evaluation context
     * @return either an index (as a HashMap), or the dummy map "under construction", or null
     */

    private synchronized KeyIndex getLocalIndex(TreeInfo doc, int keyFingerprint, XPathContext context) {
        Controller controller = context.getController();
        IntHashMap<KeyIndex> docIndexes =
                (IntHashMap<KeyIndex>)controller.getUserData(doc, "saxon:unshared-key-index-list");
        if (docIndexes == null) {
            return null;
        }
        return docIndexes.get(keyFingerprint);
    }


    /**
     * Clear all the indexes for a given document. This is currently done whenever updates
     * are applied to the document, because updates can potentially invalidate the indexes.
     *
     * @param doc the document whose indexes are to be invalidated
     */

    public synchronized void clearDocumentIndexes(TreeInfo doc) {
        docIndexes.remove(doc);
    }

    /**
     * Get all the key definition sets
     * @return a set containing all the key definition sets
     */

    public Collection<KeyDefinitionSet> getAllKeyDefinitionSets() {
        return keyDefinitions.values();
    }

    /**
     * Get the number of distinctly-named key definitions
     *
     * @return the number of key definition sets (where the key definitions in one set share the same name)
     */

    public int getNumberOfKeyDefinitions() {
        return keyDefinitions.size();
    }

    /**
     * Diagnostic output explaining the keys
     *
     * @param out the expression presenter that will display the information
     */

    public void exportKeys(ExpressionPresenter out, Map<Component, Integer> componentIdMap) throws XPathException {
        for (Map.Entry<StructuredQName, KeyDefinitionSet> e : keyDefinitions.entrySet()) {
            boolean reusable = e.getValue().isReusable();
            List<KeyDefinition> list = e.getValue().getKeyDefinitions();
            for (KeyDefinition kd : list) {
                if (!kd.getObjectName().equals(StandardNames.getStructuredQName(StandardNames.XS_IDREFS))) {
                    kd.export(out, reusable, componentIdMap);
                }
            }
        }
    }
}

