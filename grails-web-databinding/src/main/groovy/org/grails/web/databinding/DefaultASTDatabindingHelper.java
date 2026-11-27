/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.web.databinding;

import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import groovy.lang.Delegate;
import groovy.transform.Generated;
import groovy.transform.Internal;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.ListExpression;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.runtime.MetaClassHelper;
import org.codehaus.groovy.transform.trait.Traits;

import org.springframework.validation.Errors;

import grails.util.CollectionUtils;
import grails.util.GrailsNameUtils;
import org.grails.compiler.injection.GrailsASTUtils;

public class DefaultASTDatabindingHelper implements ASTDatabindingHelper {
    public static final String CONSTRAINTS_FIELD_NAME = "constraints";
    public static final String BINDABLE_CONSTRAINT_NAME = "bindable";

    public static final String DEFAULT_DATABINDING_WHITELIST = "$defaultDatabindingWhiteList";
    public static final String LEGACY_DATABINDING_WHITELIST = "$legacyDatabindingWhiteList";
    public static final String NO_BINDABLE_PROPERTIES = "$_NO_BINDABLE_PROPERTIES_$";
    /** Node metadata keys of the names computed for a class of the compilation unit. */
    private static final String WHITE_LIST_PROPERTY_NAMES = DefaultASTDatabindingHelper.class.getName() + ".whiteListPropertyNames";
    private static final String LEGACY_WHITE_LIST_PROPERTY_NAMES = DefaultASTDatabindingHelper.class.getName() + ".legacyWhiteListPropertyNames";
    private static final String EXPLICITLY_BINDABLE_SPECIAL_PROPERTY_NAMES = DefaultASTDatabindingHelper.class.getName() + ".explicitlyBindableSpecialPropertyNames";
    private static final String WARNED = DefaultASTDatabindingHelper.class.getName() + ".warned";
    private static final String TRANSIENTS_FIELD_NAME = "transients";
    private static final ClassNode GENERATED_WHITE_LIST_TYPE = ClassHelper.make(GeneratedDatabindingWhiteList.class);
    private static final ClassNode INTERNAL_TYPE = ClassHelper.make(Internal.class);
    private static final ClassNode GENERATED_TYPE = ClassHelper.make(Generated.class);
    private static final ClassNode DELEGATE_TYPE = ClassHelper.make(Delegate.class);
    /** By name: grails-validation is not on this module's compile classpath. */
    private static final ClassNode VALIDATEABLE_TYPE = ClassHelper.make("grails.validation.Validateable");
    private static final ClassNode ERRORS_TYPE = ClassHelper.make(Errors.class);

    @SuppressWarnings("serial")
    private static final List<ClassNode> SIMPLE_TYPES = new ArrayList<>() {
        {
            add(new ClassNode(Boolean.class));
            add(new ClassNode(Boolean.TYPE));
            add(new ClassNode(Byte.class));
            add(new ClassNode(Byte.TYPE));
            add(new ClassNode(Character.class));
            add(new ClassNode(Character.TYPE));
            add(new ClassNode(Short.class));
            add(new ClassNode(Short.TYPE));
            add(new ClassNode(Integer.class));
            add(new ClassNode(Integer.TYPE));
            add(new ClassNode(Long.class));
            add(new ClassNode(Long.TYPE));
            add(new ClassNode(Float.class));
            add(new ClassNode(Float.TYPE));
            add(new ClassNode(Double.class));
            add(new ClassNode(Double.TYPE));
            add(new ClassNode(BigInteger.class));
            add(new ClassNode(BigDecimal.class));
            add(new ClassNode(String.class));
            add(new ClassNode(URL.class));
        }
    };

    private static final Set<String> DOMAIN_CLASS_PROPERTIES_TO_EXCLUDE_BY_DEFAULT = CollectionUtils.newSet("id", "version", "dateCreated", "lastUpdated");

    public void injectDatabindingCode(final SourceUnit source, final GeneratorContext context, final ClassNode classNode) {
        if (!classNode.isPrimaryClassNode()) {
            warnAboutCompiledClassWithoutWhiteLists(source, classNode);
            return;
        }
        addDefaultDatabindingWhitelistFields(source, classNode, false);
    }

    /**
     * A class that is not in this compilation unit, as the parameter type of an action is when a plugin or another
     * module compiled it, cannot be given white lists here: fields added to its class node never reach its
     * bytecode. It is left as it was compiled. When it has binding rules but no white lists, so that the binder
     * binds it without restriction, a warning, once per class, says to recompile it.
     */
    private static void warnAboutCompiledClassWithoutWhiteLists(final SourceUnit sourceUnit, final ClassNode classNode) {
        if (sourceUnit == null || hasWhiteListField(classNode) || !classNode.getAnnotations(GENERATED_WHITE_LIST_TYPE).isEmpty() ||
                !hasBindingRules(classNode) || classNode.getNodeMetaData(WARNED) != null) {
            return;
        }
        classNode.putNodeMetaData(WARNED, Boolean.TRUE);
        final List<ClassNode> classes = sourceUnit.getAST().getClasses();
        final ASTNode location = classes.isEmpty() ? classNode : classes.get(0);
        GrailsASTUtils.warning(sourceUnit, location, "[" + classNode.getName() + "] has binding rules but was compiled without a data " +
                "binding white list, so it is bound without restriction. Recompile it with a Grails that generates the white list " +
                "and with grails-web-databinding on its compile classpath.");
    }

    /**
     * Gives a class that got no white list fields when the artefacts of its compilation unit were processed them,
     * with a record of the names in them: a base class that is neither a domain class nor an action parameter, a
     * command object compiled apart from its controller, or a class that only inherits its binding rules. Called
     * after canonicalization, so the setters that transformations such as {@code @Delegate} add to the class are in
     * the lists. If a subclass of the compilation unit already computed the names it inherits from this class,
     * those names are kept, so that the class and its subclass agree.
     */
    void addDefaultDatabindingWhitelistFields(final SourceUnit sourceUnit, final ClassNode classNode) {
        if (hasWhiteListField(classNode)) {
            recordDeclaredWhiteLists(classNode);
            return;
        }
        addDefaultDatabindingWhitelistFields(sourceUnit, classNode, true);
    }

    /**
     * Records the white lists a class declares itself, so that a subclass compiled against the compiled class reads
     * them as it reads generated lists. The lists are the class's own, written by its author or standing in for ones
     * an earlier Grails generated, and are left as they are. A list declared other than as a list of names is not
     * recorded, and a subclass compiled against the class is then warned about, as it is about a class an earlier
     * Grails compiled. A subclass of the compilation unit may have read the declared lists into the class's
     * metadata already; the record is written all the same.
     */
    private void recordDeclaredWhiteLists(final ClassNode classNode) {
        if (!classNode.getAnnotations(GENERATED_WHITE_LIST_TYPE).isEmpty()) {
            return;
        }
        if (readDeclaredWhiteLists(classNode)) {
            recordWhiteLists(classNode);
        }
    }

    private void addDefaultDatabindingWhitelistFields(final SourceUnit sourceUnit, final ClassNode classNode, final boolean keepComputedNames) {
        if (classNode.getDeclaredField(DEFAULT_DATABINDING_WHITELIST) == null) {
            Set<String> propertyNamesToIncludeInWhiteList = keepComputedNames ? classNode.getNodeMetaData(WHITE_LIST_PROPERTY_NAMES) : null;
            if (propertyNamesToIncludeInWhiteList == null) {
                propertyNamesToIncludeInWhiteList = getPropertyNamesToIncludeInWhiteList(sourceUnit, classNode);
            }
            addWhitelistField(classNode, DEFAULT_DATABINDING_WHITELIST, propertyNamesToIncludeInWhiteList, true);
        }

        if (classNode.getDeclaredField(LEGACY_DATABINDING_WHITELIST) == null) {
            Set<String> legacyPropertyNamesToIncludeInWhiteList = keepComputedNames ? classNode.getNodeMetaData(LEGACY_WHITE_LIST_PROPERTY_NAMES) : null;
            if (legacyPropertyNamesToIncludeInWhiteList == null) {
                legacyPropertyNamesToIncludeInWhiteList = getLegacyPropertyNamesToIncludeInWhiteList(sourceUnit, classNode);
            }
            addWhitelistField(classNode, LEGACY_DATABINDING_WHITELIST, legacyPropertyNamesToIncludeInWhiteList, true);
        }
        recordWhiteLists(classNode);
    }

    /**
     * Whether a class is bound by rules that must outlive its compilation: it declares constraints or transients, it
     * is a command object, which implements {@code grails.validation.Validateable}, or it extends a class that does
     * or that already has white lists or a record of them. Such a class gets white list fields even when nothing
     * in its compilation unit binds it: for the binder, and for a subclass compiled against it. An instance
     * property named {@code constraints} or {@code transients} is not a rule.
     */
    static boolean hasBindingRules(final ClassNode classNode) {
        if (classNode.implementsInterface(VALIDATEABLE_TYPE) || declaresBindingRules(classNode)) {
            return true;
        }
        for (ClassNode node = classNode.getSuperClass(); node != null && !node.equals(ClassHelper.OBJECT_TYPE); node = node.getSuperClass()) {
            if (declaresBindingRules(node) || hasWhiteListField(node) || !node.getAnnotations(GENERATED_WHITE_LIST_TYPE).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static boolean declaresBindingRules(final ClassNode classNode) {
        return hasStaticField(classNode, CONSTRAINTS_FIELD_NAME) || hasStaticField(classNode, TRANSIENTS_FIELD_NAME);
    }

    static boolean hasWhiteListField(final ClassNode classNode) {
        return classNode.getDeclaredField(DEFAULT_DATABINDING_WHITELIST) != null || classNode.getDeclaredField(LEGACY_DATABINDING_WHITELIST) != null;
    }

    private static boolean hasStaticField(final ClassNode classNode, final String fieldName) {
        final FieldNode fieldNode = classNode.getDeclaredField(fieldName);
        return fieldNode != null && fieldNode.isStatic();
    }

    /**
     * Reads the white lists a class of this compilation unit declares itself, as a class that writes its own
     * {@code $defaultDatabindingWhiteList} does, into the class's node metadata, and says whether it declares any.
     * The lists are the author's own, so a subclass inherits them as it would inherit the lists Grails generates. A
     * class that writes only {@code $defaultDatabindingWhiteList} writes the one list Grails 7 has, which is the
     * legacy list; the special properties it names are then all that is known of its deny-by-default list, as for
     * a class Grails 7 compiled. A list declared other than as a list of names, and the field of a compiled class,
     * whose initial value is not in its bytecode, are not read.
     */
    private static boolean readDeclaredWhiteLists(final ClassNode classNode) {
        final Set<String> defaultNames = getDeclaredWhiteListNames(classNode, DEFAULT_DATABINDING_WHITELIST);
        final Set<String> legacyNames = getDeclaredWhiteListNames(classNode, LEGACY_DATABINDING_WHITELIST);
        if (defaultNames == null && legacyNames == null) {
            return false;
        }
        final Set<String> legacy = legacyNames != null ? legacyNames : defaultNames;
        final Set<String> denyByDefault = legacyNames != null && defaultNames != null ? defaultNames : specialPropertyNamesIn(legacy);
        classNode.putNodeMetaData(LEGACY_WHITE_LIST_PROPERTY_NAMES, legacy);
        classNode.putNodeMetaData(WHITE_LIST_PROPERTY_NAMES, denyByDefault);
        classNode.putNodeMetaData(EXPLICITLY_BINDABLE_SPECIAL_PROPERTY_NAMES, specialPropertyNamesIn(denyByDefault));
        return true;
    }

    /** The property names in a white list field the class declares with a list of names, without the patterns. */
    private static Set<String> getDeclaredWhiteListNames(final ClassNode classNode, final String fieldName) {
        final FieldNode fieldNode = classNode.getDeclaredField(fieldName);
        if (fieldNode == null || !(fieldNode.getInitialValueExpression() instanceof ListExpression)) {
            return null;
        }
        final Set<String> names = new HashSet<>();
        for (Expression expression : ((ListExpression) fieldNode.getInitialValueExpression()).getExpressions()) {
            if (!(expression instanceof ConstantExpression)) {
                return null;
            }
            final String name = String.valueOf(((ConstantExpression) expression).getValue());
            if (!name.endsWith("_*") && !name.endsWith(".*") && !NO_BINDABLE_PROPERTIES.equals(name)) {
                names.add(name);
            }
        }
        return names;
    }

    private static Set<String> specialPropertyNamesIn(final Set<String> names) {
        final Set<String> specialPropertyNames = new HashSet<>(names);
        specialPropertyNames.retainAll(DOMAIN_CLASS_PROPERTIES_TO_EXCLUDE_BY_DEFAULT);
        return specialPropertyNames;
    }

    /**
     * Records the names computed for the class on it, so that a subclass compiled against the compiled class reads
     * them instead of recomputing them from bytecode. Nothing is recorded for a class that declares a white list
     * field itself, because its names were not computed.
     */
    private void recordWhiteLists(final ClassNode classNode) {
        final Set<String> propertyNames = classNode.getNodeMetaData(WHITE_LIST_PROPERTY_NAMES);
        final Set<String> legacyPropertyNames = classNode.getNodeMetaData(LEGACY_WHITE_LIST_PROPERTY_NAMES);
        final Set<String> explicitlyBindableSpecialPropertyNames = classNode.getNodeMetaData(EXPLICITLY_BINDABLE_SPECIAL_PROPERTY_NAMES);
        if (propertyNames == null || legacyPropertyNames == null || explicitlyBindableSpecialPropertyNames == null ||
                !classNode.getAnnotations(GENERATED_WHITE_LIST_TYPE).isEmpty()) {
            return;
        }
        final AnnotationNode annotation = new AnnotationNode(GENERATED_WHITE_LIST_TYPE);
        annotation.setMember("value", namesExpression(legacyPropertyNames));
        annotation.setMember("denyByDefault", namesExpression(propertyNames));
        annotation.setMember("explicitlyBindable", namesExpression(explicitlyBindableSpecialPropertyNames));
        classNode.addAnnotation(annotation);
    }

    private static ListExpression namesExpression(final Set<String> propertyNames) {
        final ListExpression names = new ListExpression();
        for (String propertyName : new TreeSet<>(propertyNames)) {
            names.addExpression(new ConstantExpression(propertyName));
        }
        return names;
    }

    /**
     * The names a member of a record holds; none when the member was not recorded.
     */
    private static Set<String> getRecordedNames(final AnnotationNode record, final String member) {
        final Set<String> names = new HashSet<>();
        final Expression value = record.getMember(member);
        if (value == null) {
            return names;
        }
        final List<Expression> expressions = value instanceof ListExpression ? ((ListExpression) value).getExpressions() : List.of(value);
        for (Expression expression : expressions) {
            if (expression instanceof ConstantExpression) {
                names.add(String.valueOf(((ConstantExpression) expression).getValue()));
            }
        }
        return names;
    }

    /**
     * Reads the record on a compiled parent, in another module, in a plugin or in an earlier build, into the
     * parent's node metadata, all of it at once, and says whether there was one. The bytecode of a compiled parent
     * has no constraints closure and no transients list, so recomputing from it would make what the parent declared
     * transient or {@code bindable: false} bindable in the subclass. The legacy names are {@code value}. The
     * deny-by-default names are {@code denyByDefault} together with the special properties {@code explicitlyBindable}
     * holds, which an explicit {@code bindable: true} puts in that list too: a parent compiled by Grails 7 recorded
     * no {@code denyByDefault}, and those special properties are all its record tells of that list. A parent in
     * this compilation unit has no record to read. A parent with binding rules but no record is recomputed from
     * its bytecode with a warning, once per parent, that names the class being compiled. A parent without rules,
     * as a Java class or a plain base class is, is recomputed from its bytecode as it always was.
     *
     * @param compiledClassNode the class of the compilation unit whose white lists are being computed, of which
     *                          the parent may be a distant ancestor
     */
    private static boolean readRecordOfParentClass(final SourceUnit sourceUnit, final ClassNode compiledClassNode, final ClassNode parentClassNode) {
        if (parentClassNode.isPrimaryClassNode()) {
            return false;
        }
        final List<AnnotationNode> annotations = parentClassNode.getAnnotations(GENERATED_WHITE_LIST_TYPE);
        if (annotations.isEmpty()) {
            if (sourceUnit != null && (hasWhiteListField(parentClassNode) || hasBindingRules(parentClassNode)) &&
                    parentClassNode.getNodeMetaData(WARNED) == null) {
                parentClassNode.putNodeMetaData(WARNED, Boolean.TRUE);
                GrailsASTUtils.warning(sourceUnit, compiledClassNode, "The data binding white list of [" + compiledClassNode.getName() +
                        "] is derived from the bytecode of [" + parentClassNode.getName() + "], which was compiled without " +
                        "a record of its own white list: properties the parent declares transient or bindable: false may " +
                        "be bindable. Recompile the parent with a Grails that records the white list and with " +
                        "grails-web-databinding on its compile classpath.");
            }
            return false;
        }
        final AnnotationNode record = annotations.get(0);
        final Set<String> explicitlyBindable = getRecordedNames(record, "explicitlyBindable");
        final Set<String> denyByDefault = getRecordedNames(record, "denyByDefault");
        denyByDefault.addAll(explicitlyBindable);
        parentClassNode.putNodeMetaData(LEGACY_WHITE_LIST_PROPERTY_NAMES, getRecordedNames(record, "value"));
        parentClassNode.putNodeMetaData(WHITE_LIST_PROPERTY_NAMES, denyByDefault);
        parentClassNode.putNodeMetaData(EXPLICITLY_BINDABLE_SPECIAL_PROPERTY_NAMES, explicitlyBindable);
        return true;
    }

    /**
     * Whether a method of a compiled class is plumbing Groovy added when it compiled the class, as
     * {@code setMetaClass} is, rather than a setter the class declares or gets from a transformation such as
     * {@code @Delegate}. Groovy marks both generated, so the plumbing is told by what it is: a synthetic bridge,
     * a method marked internal, or a method of {@link groovy.lang.GroovyObject}.
     */
    private static boolean isGroovyPlumbing(final MethodNode methodNode) {
        return methodNode.isSynthetic() ||
                !methodNode.getAnnotations(INTERNAL_TYPE).isEmpty() ||
                ClassHelper.GROOVY_OBJECT_TYPE.getDeclaredMethod(methodNode.getName(), methodNode.getParameters()) != null;
    }

    /**
     * The prefixes of the fields the traits of a class compose into it. A trait's field is added to the class under
     * the trait's name, as {@code grails_validation_Validateable__errors} is, and is the trait's own: it is not a
     * property of the class, and is never bound. A property a trait declares stays bindable through the setter the
     * trait adds to the class.
     */
    private static Set<String> getTraitFieldPrefixes(final ClassNode classNode) {
        final Set<String> prefixes = new HashSet<>();
        for (ClassNode node = classNode; node != null; node = node.getSuperClass()) {
            for (ClassNode interfaceNode : node.getAllInterfaces()) {
                if (Traits.isTrait(interfaceNode)) {
                    // the name Groovy's TraitComposer gives the field, Traits.remappedFieldName, which is not public
                    prefixes.add(interfaceNode.getName().replace('.', '_') + "__");
                }
            }
        }
        return prefixes;
    }

    private static boolean isTraitField(final FieldNode fieldNode, final Set<String> traitFieldPrefixes) {
        for (String prefix : traitFieldPrefixes) {
            if (fieldNode.getName().startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a type holds the validation result of an object, as the {@code errors} that {@code Validateable} and
     * GORM give a class does. It records what binding and validation found wrong with the input, and is never
     * bound from the input itself.
     */
    private static boolean isValidationResult(final ClassNode type) {
        if (type == null) {
            return false;
        }
        try {
            return type.equals(ERRORS_TYPE) || type.implementsInterface(ERRORS_TYPE);
        } catch (NoClassDefFoundError e) {
            // a supertype of a compiled property type is not on the compile classpath; the binder refuses a
            // validation result at runtime whatever the white list says
            return false;
        }
    }

    private void addWhitelistField(final ClassNode classNode, final String fieldName, final Set<String> propertyNamesToIncludeInWhiteList, final boolean denyWhenEmpty) {
        final ListExpression listExpression = new ListExpression();
        if (propertyNamesToIncludeInWhiteList.size() > 0) {
            for (String propertyName : propertyNamesToIncludeInWhiteList) {
                listExpression.addExpression(new ConstantExpression(propertyName));

                final FieldNode declaredField = getDeclaredFieldInInheritanceHierarchy(classNode, propertyName);
                boolean isSimpleType = false;
                if (declaredField != null) {
                    final ClassNode type = declaredField.getType();
                    if (type != null) {
                        isSimpleType = SIMPLE_TYPES.contains(type);
                    }
                }
                if (!isSimpleType) {
                    listExpression.addExpression(new ConstantExpression(propertyName + "_*"));
                    listExpression.addExpression(new ConstantExpression(propertyName + ".*"));
                }
            }
        } else if (denyWhenEmpty) {
            listExpression.addExpression(new ConstantExpression(NO_BINDABLE_PROPERTIES));
        }

        classNode.addField(fieldName,
                Modifier.STATIC | Modifier.PUBLIC | Modifier.FINAL, new ClassNode(List.class),
                listExpression);
    }

    private FieldNode getDeclaredFieldInInheritanceHierarchy(final ClassNode classNode, String propertyName) {
        FieldNode fieldNode = classNode.getDeclaredField(propertyName);
        if (fieldNode == null) {
            if (!classNode.getSuperClass().equals(new ClassNode(Object.class))) {
                return getDeclaredFieldInInheritanceHierarchy(classNode.getSuperClass(), propertyName);
            }
        }
        return fieldNode;
    }

    /**
     * The deny-by-default names a subclass inherits from its parent: those computed for the parent in this
     * compilation unit, those recorded on the parent when it was compiled, or else those recomputed from its bytecode.
     */
    private Set<String> getPropertyNamesToIncludeInWhiteListForParentClass(final SourceUnit sourceUnit, final ClassNode compiledClassNode, final ClassNode parentClassNode) {
        Set<String> propertyNames = parentClassNode.getNodeMetaData(WHITE_LIST_PROPERTY_NAMES);
        if (propertyNames == null && (readRecordOfParentClass(sourceUnit, compiledClassNode, parentClassNode) ||
                readDeclaredWhiteLists(parentClassNode))) {
            propertyNames = parentClassNode.getNodeMetaData(WHITE_LIST_PROPERTY_NAMES);
        }
        if (propertyNames == null) {
            propertyNames = getPropertyNamesToIncludeInWhiteList(sourceUnit, parentClassNode, compiledClassNode);
        }
        return propertyNames;
    }

    private Set<String> getExplicitlyBindableSpecialPropertyNamesForParentClass(final SourceUnit sourceUnit, final ClassNode compiledClassNode, final ClassNode parentClassNode) {
        Set<String> explicitlyBindable = parentClassNode.getNodeMetaData(EXPLICITLY_BINDABLE_SPECIAL_PROPERTY_NAMES);
        if (explicitlyBindable == null && (readRecordOfParentClass(sourceUnit, compiledClassNode, parentClassNode) ||
                readDeclaredWhiteLists(parentClassNode))) {
            explicitlyBindable = parentClassNode.getNodeMetaData(EXPLICITLY_BINDABLE_SPECIAL_PROPERTY_NAMES);
        }
        if (explicitlyBindable == null) {
            getPropertyNamesToIncludeInWhiteList(sourceUnit, parentClassNode, compiledClassNode);
            explicitlyBindable = parentClassNode.getNodeMetaData(EXPLICITLY_BINDABLE_SPECIAL_PROPERTY_NAMES);
        }
        return explicitlyBindable != null ? explicitlyBindable : new HashSet<>();
    }

    private Set<String> getPropertyNamesToIncludeInWhiteList(final SourceUnit sourceUnit, final ClassNode classNode) {
        return getPropertyNamesToIncludeInWhiteList(sourceUnit, classNode, classNode);
    }

    /**
     * @param compiledClassNode the class of the compilation unit the names serve: {@code classNode} itself, or a
     *                          subclass of it when {@code classNode} is a compiled parent recomputed from bytecode
     */
    private Set<String> getPropertyNamesToIncludeInWhiteList(final SourceUnit sourceUnit, final ClassNode classNode, final ClassNode compiledClassNode) {
        final Set<String> propertyNamesToIncludeInWhiteList = new HashSet<>();
        final Set<String> unbindablePropertyNames = new HashSet<>();
        final Set<String> bindablePropertyNames = new HashSet<>();

        // Special properties (id/version/dateCreated/lastUpdated) made bindable via an explicit constraint
        // somewhere in the class hierarchy. These are tracked separately so the inherited-whitelist filter
        // below can tell an explicit bindable: true from a special property that merely landed in a non-domain
        // parent's whitelist by default (for example id/version injected onto a @DirtyCheck base class).
        final Set<String> explicitlyBindableSpecialPropertyNames = new HashSet<>();
        final boolean isDomainClass = GrailsASTUtils.isDomainClass(classNode, sourceUnit);
        if (!classNode.getSuperClass().equals(new ClassNode(Object.class))) {
            final Set<String> parentClassPropertyNames = getPropertyNamesToIncludeInWhiteListForParentClass(sourceUnit, compiledClassNode, classNode.getSuperClass());
            final Set<String> parentExplicitlyBindableSpecialPropertyNames = getExplicitlyBindableSpecialPropertyNamesForParentClass(sourceUnit, compiledClassNode, classNode.getSuperClass());
            explicitlyBindableSpecialPropertyNames.addAll(parentExplicitlyBindableSpecialPropertyNames);
            for (final String parentPropertyName : parentClassPropertyNames) {
                // A domain class never binds its special properties (id/version/dateCreated/lastUpdated) by
                // default, so don't inherit them from a non-domain parent such as a @DirtyCheck base class.
                // An explicit bindable: true constraint declared up the hierarchy is honoured, however.
                if (isDomainClass && DOMAIN_CLASS_PROPERTIES_TO_EXCLUDE_BY_DEFAULT.contains(parentPropertyName) &&
                        !parentExplicitlyBindableSpecialPropertyNames.contains(parentPropertyName)) {
                    continue;
                }
                bindablePropertyNames.add(parentPropertyName);
            }
        }

        final FieldNode constraintsFieldNode = classNode.getDeclaredField(CONSTRAINTS_FIELD_NAME);
        if (constraintsFieldNode != null && constraintsFieldNode.hasInitialExpression()) {
            final Expression constraintsInitialExpression = constraintsFieldNode.getInitialExpression();
            if (constraintsInitialExpression instanceof ClosureExpression) {

                final Map<String, Map<String, Expression>> constraintsInfo = GrailsASTUtils.getConstraintMetadata((ClosureExpression) constraintsInitialExpression);

                for (Entry<String, Map<String, Expression>> constraintConfig : constraintsInfo.entrySet()) {
                    final String propertyName = constraintConfig.getKey();
                    final Map<String, Expression> mapEntryExpressions = constraintConfig.getValue();
                    for (Entry<String, Expression> entry : mapEntryExpressions.entrySet()) {
                        final String constraintName = entry.getKey();
                        if (BINDABLE_CONSTRAINT_NAME.equals(constraintName)) {
                            final Expression valueExpression = entry.getValue();
                            Boolean bindableValue = null;
                            if (valueExpression instanceof ConstantExpression) {
                                final Object constantValue = ((ConstantExpression) valueExpression).getValue();
                                if (constantValue instanceof Boolean) {
                                    bindableValue = (Boolean) constantValue;
                                }
                            }
                            if (bindableValue != null) {
                                if (Boolean.TRUE.equals(bindableValue)) {
                                    unbindablePropertyNames.remove(propertyName);
                                    bindablePropertyNames.add(propertyName);
                                    if (DOMAIN_CLASS_PROPERTIES_TO_EXCLUDE_BY_DEFAULT.contains(propertyName)) {
                                        explicitlyBindableSpecialPropertyNames.add(propertyName);
                                    }
                                } else {
                                    bindablePropertyNames.remove(propertyName);
                                    unbindablePropertyNames.add(propertyName);
                                    explicitlyBindableSpecialPropertyNames.remove(propertyName);
                                }
                            } else {
                                GrailsASTUtils.warning(sourceUnit, valueExpression, "The bindable constraint for property [" +
                                        propertyName + "] in class [" + classNode.getName() +
                                        "] has a value which is not a boolean literal and will be ignored.");
                            }
                        }
                    }
                }
            }
        }

        propertyNamesToIncludeInWhiteList.addAll(bindablePropertyNames);
        classNode.putNodeMetaData(WHITE_LIST_PROPERTY_NAMES, propertyNamesToIncludeInWhiteList);
        classNode.putNodeMetaData(EXPLICITLY_BINDABLE_SPECIAL_PROPERTY_NAMES, explicitlyBindableSpecialPropertyNames);
        return propertyNamesToIncludeInWhiteList;
    }

    /**
     * The legacy names a subclass inherits from its parent: those computed for the parent in this compilation unit,
     * those recorded on the parent when it was compiled, or else those recomputed from its bytecode.
     */
    private Set<String> getLegacyPropertyNamesToIncludeInWhiteListForParentClass(final SourceUnit sourceUnit, final ClassNode compiledClassNode, final ClassNode parentClassNode) {
        Set<String> propertyNames = parentClassNode.getNodeMetaData(LEGACY_WHITE_LIST_PROPERTY_NAMES);
        if (propertyNames == null && (readRecordOfParentClass(sourceUnit, compiledClassNode, parentClassNode) ||
                readDeclaredWhiteLists(parentClassNode))) {
            propertyNames = parentClassNode.getNodeMetaData(LEGACY_WHITE_LIST_PROPERTY_NAMES);
        }
        if (propertyNames == null) {
            propertyNames = getLegacyPropertyNamesToIncludeInWhiteList(sourceUnit, parentClassNode, compiledClassNode);
        }
        return propertyNames;
    }

    private Set<String> getLegacyPropertyNamesToIncludeInWhiteList(final SourceUnit sourceUnit, final ClassNode classNode) {
        return getLegacyPropertyNamesToIncludeInWhiteList(sourceUnit, classNode, classNode);
    }

    /**
     * @param compiledClassNode the class of the compilation unit the names serve: {@code classNode} itself, or a
     *                          subclass of it when {@code classNode} is a compiled parent recomputed from bytecode
     */
    private Set<String> getLegacyPropertyNamesToIncludeInWhiteList(final SourceUnit sourceUnit, final ClassNode classNode, final ClassNode compiledClassNode) {
        final Set<String> propertyNamesToIncludeInWhiteList = new HashSet<>();
        final Set<String> unbindablePropertyNames = new HashSet<>();
        final Set<String> bindablePropertyNames = new HashSet<>();

        final Set<String> explicitlyBindableSpecialPropertyNames = new HashSet<>();
        final boolean isDomainClass = GrailsASTUtils.isDomainClass(classNode, sourceUnit);
        if (!classNode.getSuperClass().equals(new ClassNode(Object.class))) {
            final Set<String> parentClassPropertyNames = getLegacyPropertyNamesToIncludeInWhiteListForParentClass(sourceUnit, compiledClassNode, classNode.getSuperClass());
            final Set<String> parentExplicitlyBindableSpecialPropertyNames = getExplicitlyBindableSpecialPropertyNamesForParentClass(sourceUnit, compiledClassNode, classNode.getSuperClass());
            explicitlyBindableSpecialPropertyNames.addAll(parentExplicitlyBindableSpecialPropertyNames);
            for (final String parentPropertyName : parentClassPropertyNames) {
                if (isDomainClass && DOMAIN_CLASS_PROPERTIES_TO_EXCLUDE_BY_DEFAULT.contains(parentPropertyName) &&
                        !parentExplicitlyBindableSpecialPropertyNames.contains(parentPropertyName)) {
                    continue;
                }
                bindablePropertyNames.add(parentPropertyName);
            }
        }

        final FieldNode constraintsFieldNode = classNode.getDeclaredField(CONSTRAINTS_FIELD_NAME);
        if (constraintsFieldNode != null && constraintsFieldNode.hasInitialExpression()) {
            final Expression constraintsInitialExpression = constraintsFieldNode.getInitialExpression();
            if (constraintsInitialExpression instanceof ClosureExpression) {

                final Map<String, Map<String, Expression>> constraintsInfo = GrailsASTUtils.getConstraintMetadata((ClosureExpression) constraintsInitialExpression);

                for (Entry<String, Map<String, Expression>> constraintConfig : constraintsInfo.entrySet()) {
                    final String propertyName = constraintConfig.getKey();
                    final Map<String, Expression> mapEntryExpressions = constraintConfig.getValue();
                    for (Entry<String, Expression> entry : mapEntryExpressions.entrySet()) {
                        final String constraintName = entry.getKey();
                        if (BINDABLE_CONSTRAINT_NAME.equals(constraintName)) {
                            final Expression valueExpression = entry.getValue();
                            Boolean bindableValue = null;
                            if (valueExpression instanceof ConstantExpression) {
                                final Object constantValue = ((ConstantExpression) valueExpression).getValue();
                                if (constantValue instanceof Boolean) {
                                    bindableValue = (Boolean) constantValue;
                                }
                            }
                            if (bindableValue != null) {
                                if (Boolean.TRUE.equals(bindableValue)) {
                                    unbindablePropertyNames.remove(propertyName);
                                    bindablePropertyNames.add(propertyName);
                                    if (DOMAIN_CLASS_PROPERTIES_TO_EXCLUDE_BY_DEFAULT.contains(propertyName)) {
                                        explicitlyBindableSpecialPropertyNames.add(propertyName);
                                    }
                                } else {
                                    bindablePropertyNames.remove(propertyName);
                                    unbindablePropertyNames.add(propertyName);
                                    explicitlyBindableSpecialPropertyNames.remove(propertyName);
                                }
                            } else {
                                GrailsASTUtils.warning(sourceUnit, valueExpression, "The bindable constraint for property [" +
                                        propertyName + "] in class [" + classNode.getName() +
                                        "] has a value which is not a boolean literal and will be ignored.");
                            }
                        }
                    }
                }
            }
        }

        final Set<String> fieldsInTransientsList = getPropertyNamesExpressedInTransientsList(classNode);
        final Set<String> traitFieldPrefixes = getTraitFieldPrefixes(classNode);

        propertyNamesToIncludeInWhiteList.addAll(bindablePropertyNames);
        final List<FieldNode> fields = classNode.getFields();
        for (FieldNode fieldNode : fields) {
            final String fieldName = fieldNode.getName();
            if ((!unbindablePropertyNames.contains(fieldName)) && !isTraitField(fieldNode, traitFieldPrefixes) && !isValidationResult(fieldNode.getType()) &&
                    (bindablePropertyNames.contains(fieldName) || shouldFieldBeInWhiteList(fieldNode, fieldsInTransientsList, isDomainClass))) {
                propertyNamesToIncludeInWhiteList.add(fieldName);
            }
        }

        final Map<String, MethodNode> declaredMethodsMap = classNode.getDeclaredMethodsMap();
        for (Entry<String, MethodNode> methodEntry : declaredMethodsMap.entrySet()) {
            final MethodNode value = methodEntry.getValue();
            if (classNode.equals(value.getDeclaringClass()) && (classNode.isPrimaryClassNode() || !isGroovyPlumbing(value))) {
                Parameter[] parameters = value.getParameters();
                if (parameters != null && parameters.length == 1) {
                    final String methodName = value.getName();
                    if (methodName.startsWith("set")) {
                        final Parameter parameter = parameters[0];
                        final ClassNode paramType = parameter.getType();
                        if (!paramType.equals(new ClassNode(Object.class)) && !isValidationResult(paramType)) {
                            final String restOfMethodName = methodName.substring(3);
                            final String propertyName = GrailsNameUtils.getPropertyName(restOfMethodName);
                            if (!unbindablePropertyNames.contains(propertyName) &&
                                    (!isDomainClass || !DOMAIN_CLASS_PROPERTIES_TO_EXCLUDE_BY_DEFAULT.contains(propertyName)) &&
                                    (bindablePropertyNames.contains(propertyName) ||
                                            setsBindableProperty(sourceUnit, classNode, compiledClassNode, value, propertyName, fieldsInTransientsList, isDomainClass))) {
                                propertyNamesToIncludeInWhiteList.add(propertyName);
                            }
                        }
                    }
                }
            }
        }
        classNode.putNodeMetaData(LEGACY_WHITE_LIST_PROPERTY_NAMES, propertyNamesToIncludeInWhiteList);
        Map<String, ClassNode> allAssociationMap = GrailsASTUtils.getAllAssociationMap(classNode);
        for (String associationName : allAssociationMap.keySet()) {
            if (!propertyNamesToIncludeInWhiteList.contains(associationName) && !unbindablePropertyNames.contains(associationName)) {
                propertyNamesToIncludeInWhiteList.add(associationName);
            }
        }
        return propertyNamesToIncludeInWhiteList;
    }

    /**
     * Whether a setter sets a property that is bindable. A setter written by hand is one the class declares, and
     * binds. A setter Groovy generated for a field of the class, which from bytecode is there beside the field,
     * follows the field: when the class is compiled with its subclass, that setter does not exist yet and the field
     * alone decides. A setter {@code @Delegate} adds for a property of the delegate follows the delegate's own white
     * list: what the delegate keeps out of binding stays out when it is reached through the delegating class.
     */
    private boolean setsBindableProperty(final SourceUnit sourceUnit, final ClassNode classNode, final ClassNode compiledClassNode,
            final MethodNode setter, final String propertyName, final Set<String> fieldsInTransientsList, final boolean isDomainClass) {
        if (setter.getAnnotations(GENERATED_TYPE).isEmpty()) {
            return true;
        }
        final FieldNode fieldNode = classNode.getDeclaredField(propertyName);
        if (fieldNode != null) {
            return classNode.isPrimaryClassNode() || shouldFieldBeInWhiteList(fieldNode, fieldsInTransientsList, isDomainClass);
        }
        final ClassNode delegateType = getDelegateTypeDeclaring(classNode, propertyName);
        if (delegateType != null) {
            return getLegacyPropertyNamesToIncludeInWhiteListForParentClass(sourceUnit, compiledClassNode, delegateType).contains(propertyName);
        }
        return true;
    }

    /**
     * The type of the first field of the class that {@code @Delegate} marks and that has the property, as a field
     * or a setter of its own or of a parent; {@code null} when no delegate of the class has it.
     */
    private static ClassNode getDelegateTypeDeclaring(final ClassNode classNode, final String propertyName) {
        final String setterName = "set" + MetaClassHelper.capitalize(propertyName);
        for (FieldNode fieldNode : classNode.getFields()) {
            if (fieldNode.getAnnotations(DELEGATE_TYPE).isEmpty()) {
                continue;
            }
            final ClassNode delegateType = fieldNode.getType();
            if (delegateType.getField(propertyName) != null || delegateType.getProperty(propertyName) != null ||
                    !delegateType.getMethods(setterName).isEmpty()) {
                return delegateType;
            }
        }
        return null;
    }

    private boolean shouldFieldBeInWhiteList(final FieldNode fieldNode, final Set<String> fieldsInTransientsList, final boolean isDomainClass) {
        boolean shouldInclude = true;
        final int modifiers = fieldNode.getModifiers();
        final String fieldName = fieldNode.getName();
        if ((modifiers & Modifier.STATIC) != 0 ||
                (modifiers & Modifier.TRANSIENT) != 0 ||
                fieldsInTransientsList.contains(fieldName) ||
                (fieldNode.getType().equals(new ClassNode(Object.class)) && !fieldNode.getType().isUsingGenerics())) {
            shouldInclude = false;
        } else if (isDomainClass) {
            if (DOMAIN_CLASS_PROPERTIES_TO_EXCLUDE_BY_DEFAULT.contains(fieldName)) {
                shouldInclude = false;
            }
        }
        return shouldInclude;
    }

    private Set<String> getPropertyNamesExpressedInTransientsList(final ClassNode classNode) {
        final Set<String> transientFields = new HashSet<>();
        final FieldNode transientsField = classNode.getField(TRANSIENTS_FIELD_NAME);
        if (transientsField != null && transientsField.isStatic()) {
            final Expression initialValueExpression = transientsField.getInitialValueExpression();
            if (initialValueExpression instanceof ListExpression) {
                final ListExpression le = (ListExpression) initialValueExpression;
                final List<Expression> expressions = le.getExpressions();
                for (Expression expr : expressions) {
                    if (expr instanceof ConstantExpression) {
                        final ConstantExpression ce = (ConstantExpression) expr;
                        final Object contantValue = ce.getValue();
                        if (contantValue instanceof String) {
                            transientFields.add((String) contantValue);
                        }
                    }
                }
            }
        }
        return transientFields;
    }

}
