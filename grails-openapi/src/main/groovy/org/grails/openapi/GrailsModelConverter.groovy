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
package org.grails.openapi

import java.beans.PropertyDescriptor
import java.lang.reflect.Method
import java.lang.reflect.Type

import groovy.transform.CompileStatic

import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.type.TypeFactory
import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverter
import io.swagger.v3.core.converter.ModelConverterContext
import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.core.util.PrimitiveType
import io.swagger.v3.oas.annotations.media.Schema as SchemaAnnotation
import io.swagger.v3.oas.annotations.media.Schema.AccessMode
import io.swagger.v3.oas.models.SpecVersion
import io.swagger.v3.oas.models.media.ArraySchema
import io.swagger.v3.oas.models.media.ComposedSchema
import io.swagger.v3.oas.models.media.IntegerSchema
import io.swagger.v3.oas.models.media.JsonSchema
import io.swagger.v3.oas.models.media.ObjectSchema
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.media.XML
import org.codehaus.groovy.runtime.InvokerHelper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.BeanUtils
import org.springframework.core.ResolvableType
import org.springframework.util.ClassUtils
import org.springframework.validation.Errors
import org.springframework.validation.Validator

import grails.gorm.validation.Constrained
import grails.util.GrailsNameUtils
import grails.gorm.validation.ConstrainedEntity
import grails.gorm.validation.ConstrainedProperty
import grails.validation.Validateable
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.Basic
import org.grails.datastore.mapping.model.types.Embedded
import org.grails.datastore.mapping.model.types.EmbeddedCollection
import org.grails.datastore.mapping.model.types.ToMany
import org.grails.web.databinding.BindingIncludeLists

/**
 * Teaches swagger-core what a Groovy class, a command object and a GORM entity look like, so the
 * schema of such a type is right wherever it is resolved: as a resource, as a command object, or
 * as the implementation an annotation names.
 *
 * <ul>
 *   <li>The {@code metaClass} every Groovy object has, and the {@code errors} a validateable type
 *   has, are not properties of the resource and are left out.</li>
 *   <li>The declared constraints become the matching schema keywords.</li>
 *   <li>The identifier and version of an entity are read only, because the server assigns them.</li>
 *   <li>An association to another entity is described by the identifier that Grails renders and
 *   binds for it, rather than by the whole associated resource.</li>
 * </ul>
 *
 * <p>The converter is registered with swagger-core once, and describes a type so only where a
 * Grails description is resolving it, which supplies the GORM metadata it uses through
 * {@link #withMappingContexts}. Anywhere else, as springdoc resolves the types of its own
 * endpoints, which Jackson renders rather than Grails, it passes the type on as it is, and records,
 * while springdoc builds a document, the class it resolves under each name. A declaration that
 * cannot be read, such as constraints that fail to evaluate, is left out, and logged; each is read
 * before the schema is changed.</p>
 */
@CompileStatic
class GrailsModelConverter implements ModelConverter {

    static final GrailsModelConverter INSTANCE = new GrailsModelConverter()

    /**
     * The name Grails renders and binds an identifier by, whatever the identity is named.
     */
    private static final String IDENTITY = 'id'

    private static final Logger LOG = LoggerFactory.getLogger(GrailsModelConverter)

    private static final String EMAIL_FORMAT = 'email'
    private static final String URI_FORMAT = 'uri'
    private static final String INT64_FORMAT = 'int64'
    private static final String NULL_TYPE = 'null'
    private static final String REFERENCE_PREFIX = '#/components/schemas/'

    private static final ThreadLocal<Collection<MappingContext>> MAPPING_CONTEXTS = new ThreadLocal<>()

    private static final ThreadLocal<Boolean> INCLUDE_VERSION = new ThreadLocal<>()

    private static final String DATABINDING_WHITELIST = '$defaultDatabindingWhiteList'

    private static final List<Class<?>> FILE_TYPES = ['org.springframework.web.multipart.MultipartFile',
                                                      'jakarta.servlet.http.Part'].findAll { String name ->
        ClassUtils.isPresent(name, GrailsModelConverter.classLoader)
    }.collect { String name -> ClassUtils.resolveClassName(name, GrailsModelConverter.classLoader) }.asImmutable()

    private static final ThreadLocal<SchemaNames> SCHEMA_NAMES = new ThreadLocal<>()

    private static final ThreadLocal<Boolean> TYPES_ONLY = ThreadLocal.withInitial { false }

    /**
     * The class resolved under each name outside a Grails description while springdoc builds a
     * document on the thread.
     */
    private static final ThreadLocal<Map<String, Class<?>>> RESOLVED_ELSEWHERE = new ThreadLocal<>()

    private static final ThreadLocal<Deque<Class<?>>> RESOLVING = ThreadLocal.<Deque<Class<?>>> withInitial {
        (Deque<Class<?>>) new ArrayDeque<Class<?>>()
    }

    /**
     * Registers the converter with both of swagger-core's converter lists, once.
     */
    static synchronized void register() {
        for (boolean openapi31 : [false, true]) {
            ModelConverters converters = ModelConverters.getInstance(openapi31)
            if (!converters.converters.any { it instanceof GrailsModelConverter }) {
                converters.addConverter(INSTANCE)
            }
        }
    }

    /**
     * Resolves with the entities of the given mapping contexts known, so their constraints,
     * identifiers and associations are described.
     */
    static <T> T withMappingContexts(Collection<MappingContext> mappingContexts, Closure<T> work) {
        withMappingContexts(mappingContexts, false, work)
    }

    /**
     * @param includeVersion whether Grails renders the version of an entity, which it does not
     * unless {@code grails.converters.domain.include.version} is set
     */
    static <T> T withMappingContexts(Collection<MappingContext> mappingContexts, boolean includeVersion, Closure<T> work) {
        Collection<MappingContext> previous = MAPPING_CONTEXTS.get()
        Boolean previousIncludeVersion = INCLUDE_VERSION.get()
        MAPPING_CONTEXTS.set(mappingContexts)
        INCLUDE_VERSION.set(includeVersion)
        try {
            return work.call()
        }
        finally {
            MAPPING_CONTEXTS.set(previous)
            INCLUDE_VERSION.set(previousIncludeVersion)
        }
    }

    /**
     * Resolves with every class named apart from the others sharing its name, through the names
     * of the document being described.
     */
    static <T> T withSchemaNames(SchemaNames names, Closure<T> work) {
        SchemaNames previous = SCHEMA_NAMES.get()
        SCHEMA_NAMES.set(names)
        try {
            return work.call()
        }
        finally {
            SCHEMA_NAMES.set(previous)
        }
    }

    /**
     * Resolves the types a description reaches without reading what Grails declares of them, as a
     * build processing the application ahead of time does, while the application is not running.
     */
    static <T> T withTypesOnly(Closure<T> work) {
        Boolean previous = TYPES_ONLY.get()
        TYPES_ONLY.set(true)
        try {
            return work.call()
        }
        finally {
            TYPES_ONLY.set(previous)
        }
    }

    /**
     * @return the entity mapped for the type, if a mapping context in scope maps it
     */
    static PersistentEntity entityFor(Class<?> type) {
        if (type == null) {
            return null
        }
        for (MappingContext context : (MAPPING_CONTEXTS.get() ?: Collections.<MappingContext> emptyList())) {
            PersistentEntity entity = context.getPersistentEntity(type.name)
            if (entity != null) {
                return entity
            }
        }
        null
    }

    /**
     * Whether a type has a property a file is bound to, so it is bound from a multipart request.
     */
    static boolean hasFileProperty(Class<?> type) {
        if (type == null) {
            return false
        }
        BeanUtils.getPropertyDescriptors(type).any { PropertyDescriptor property -> isFile(property.propertyType) }
    }

    /**
     * An uploaded file: a {@code MultipartFile} or a servlet {@code Part}.
     */
    private static boolean isFile(Class<?> type) {
        type != null && FILE_TYPES.any { Class<?> fileType -> fileType.isAssignableFrom(type) }
    }

    /**
     * Starts recording the class resolved under each name outside a Grails description on this
     * thread, as springdoc starts building a document, before it resolves the types of its own
     * endpoints. What an earlier document recorded is dropped.
     */
    static void recordResolvedNames() {
        RESOLVED_ELSEWHERE.set([:])
    }

    /**
     * Stops recording, as the Grails description of a document springdoc builds starts.
     *
     * @return the class resolved under each name outside a Grails description since recording
     * started on this thread, which is none where it was not started
     */
    static Map<String, Class<?>> takeResolvedNames() {
        Map<String, Class<?>> recorded = RESOLVED_ELSEWHERE.get()
        RESOLVED_ELSEWHERE.remove()
        recorded ?: Collections.<String, Class<?>> emptyMap()
    }

    @Override
    Schema resolve(AnnotatedType annotatedType, ModelConverterContext context, Iterator<ModelConverter> chain) {
        if (MAPPING_CONTEXTS.get() == null && !TYPES_ONLY.get()) {
            // Resolved outside a Grails description, as springdoc resolves the types of its own
            // endpoints, which Jackson renders rather than Grails.
            recordResolvedName(annotatedType)
            return chain.hasNext() ? chain.next().resolve(annotatedType, context, chain) : null
        }
        resolveDescribed(annotatedType, context, chain)
    }

    private static void recordResolvedName(AnnotatedType annotatedType) {
        Map<String, Class<?>> recorded = RESOLVED_ELSEWHERE.get()
        Class<?> type = recorded != null ? rawClass(annotatedType.type) : null
        JavaType javaType = type != null ? javaType(annotatedType.type) : null
        if (javaType != null && SchemaNames.isDescribedAsItself(annotatedType, javaType)
                && SchemaNames.isNamed(annotatedType, javaType)) {
            recorded.putIfAbsent(annotatedType.name ?: SchemaNames.naturalName(annotatedType, javaType), type)
        }
    }

    private Schema resolveDescribed(AnnotatedType annotatedType, ModelConverterContext context, Iterator<ModelConverter> chain) {
        Class<?> type = rawClass(annotatedType.type)
        if (isFile(type)) {
            // A file is sent as the binary part of a multipart request.
            return new StringSchema().format('binary')
        }

        if (annotatedType.propertyName != null) {
            if (type != null && (MetaClass.isAssignableFrom(type) || Errors.isAssignableFrom(type))) {
                return null
            }
            Schema reference = associationReference(annotatedType.propertyName)
            if (reference != null) {
                return reference
            }
        }

        if (!chain.hasNext()) {
            return null
        }
        JavaType javaType = javaType(annotatedType.type)
        if (type == null || javaType == null || !SchemaNames.isDescribedAsItself(annotatedType, javaType)) {
            // Another type is described in its place, and named and described as itself.
            return chain.next().resolve(annotatedType, context, chain)
        }
        nameApart(annotatedType)

        Deque<Class<?>> resolving = RESOLVING.get()
        resolving.push(type)
        Schema resolved
        try {
            resolved = chain.next().resolve(annotatedType, context, chain)
        }
        finally {
            resolving.pop()
        }

        Schema model = modelOf(resolved, context)
        if (model?.properties != null && !TYPES_ONLY.get()) {
            try {
                describe(type, model)
            }
            catch (RuntimeException | LinkageError e) {
                // The schema swagger-core resolved stands without what Grails declares of the
                // type, rather than failing the document.
                LOG.warn("Could not describe what Grails declares of [${type.name}], such as its constraints", e)
            }
        }
        resolved
    }

    /**
     * Names a class that shares its name with another apart from it, before swagger-core names the
     * class and refers to it.
     */
    private static void nameApart(AnnotatedType annotatedType) {
        SchemaNames names = SCHEMA_NAMES.get()
        if (names == null || annotatedType.name) {
            return
        }
        JavaType type = javaType(annotatedType.type)
        if (type == null || !SchemaNames.isNamed(annotatedType, type)) {
            return
        }
        String natural = SchemaNames.naturalName(annotatedType, type)
        String name = names.claim(type, natural)
        if (name != natural) {
            annotatedType.setName(name)
        }
    }

    /**
     * A property the enclosing entity maps as an association to another entity is described by
     * the associated identifier, which is what Grails renders for it and accepts to bind it.
     */
    private static Schema associationReference(String propertyName) {
        Class<?> owner = RESOLVING.get().peek()
        PersistentEntity entity = entityFor(owner)
        if (entity == null) {
            return null
        }
        PersistentProperty property = entity.getPropertyByName(propertyName)
        if (!(property instanceof Association) || property instanceof Embedded || property instanceof EmbeddedCollection) {
            return null
        }
        Association association = (Association) property
        PersistentEntity associated = association.associatedEntity
        if (associated == null) {
            return null
        }

        // Grails renders and binds the associated identifier as id, whatever the identity is named:
        // in XML as an attribute of the association's element, and a to-many association as an
        // element holding one for each, named for the associated class.
        Schema reference = new ObjectSchema()
                .addProperty(IDENTITY, identifierSchema(associated).xml(new XML().attribute(true)))
                .description("The identifier of the associated ${associated.javaClass.simpleName}".toString())
        reference.addRequiredItem(IDENTITY)

        if (association instanceof ToMany) {
            reference.setXml(new XML().name(GrailsNameUtils.getPropertyName(associated.javaClass)))
            return new ArraySchema().items(reference).xml(new XML().wrapped(true))
        }
        reference
    }

    /**
     * The schema of the identifier an entity is addressed and associated by: an integer of the
     * width it is declared with, a UUID, or a string for any other type.
     */
    static Schema identifierSchema(PersistentEntity entity) {
        Class<?> identifierType = entity.identity?.type
        Schema schema = identifierType != null ? PrimitiveType.createProperty(identifierType) : null
        schema ?: new StringSchema()
    }

    private static Schema modelOf(Schema resolved, ModelConverterContext context) {
        if (resolved?.$ref) {
            String name = resolved.$ref.startsWith(REFERENCE_PREFIX)
                    ? resolved.$ref.substring(REFERENCE_PREFIX.length())
                    : resolved.$ref
            return context.definedModels?.get(name)
        }
        resolved
    }

    private static void describe(Class<?> type, Schema model) {
        PersistentEntity entity = entityFor(type)
        boolean validateable = Validateable.isAssignableFrom(type)
        PropertyNames propertyNames = PropertyNames.of(type)
        if (entity == null && !validateable && !declaresBindableProperties(type)) {
            // Grails renders it by the names of its properties, and declares nothing else of it.
            declareReferenceSiblings(model, propertyNames.applyTo(model), propertyNames)
            return
        }

        // What Grails declares of the type is read before the schema is changed, each declaration
        // on its own, so one that cannot be read is left out without leaving the schema half changed.
        Map<String, Constrained> constraints = read("the constraints of [${type.name}]") {
            entity != null ? entityConstraints(entity) : (validateable ? validateableConstraints(type) : null)
        } ?: Collections.<String, Constrained> emptyMap()
        Set<String> readOnly = entity == null ? readOnlyConstrained(type, constraints.keySet()) : Collections.<String> emptySet()
        List<String> bindable = read("what data binding binds of [${type.name}]") {
            BindingIncludeLists.propertyNames(type)
        }
        Set<String> beanProperties = BeanUtils.getPropertyDescriptors(type)*.name.toSet()

        Map<String, String> names = propertyNames.applyTo(model)
        declareReferenceSiblings(model, names, propertyNames)
        if (model.xml == null) {
            // Grails renders a type in XML as an element named for its class.
            model.setXml(new XML().name(GrailsNameUtils.getPropertyName(type)))
        }
        String versionName = null
        if (entity != null) {
            versionName = entity.versioned ? entity.version?.name : null
            describeEntity(entity, model, names, versionName)
        }
        readOnly.each { String name -> describable(model, names[name] ?: name)?.setReadOnly(true) }
        describeCollectionsInXml(type, model, names, entity)
        applyConstraints(model, constraints.findAll { String name, Constrained constrained -> !(name in readOnly) },
                versionName, names)
        if (bindable != null) {
            markUnbound(model, names, bindable, beanProperties)
        }
    }

    /**
     * Grails renders a collection of values in XML as an element holding one for each, named for
     * the class of the value, such as {@code <lines><string>a</string></lines>}. A to-many
     * association is described so where it is referred to, and a value described as a schema of its
     * own is named for its class there.
     */
    private static void describeCollectionsInXml(Class<?> type, Schema model, Map<String, String> names,
                                                 PersistentEntity entity) {
        ((Map<String, Schema>) model.properties)?.each { String described, Schema property ->
            if (property.xml != null || !(property.type == 'array' || property.types?.contains('array'))) {
                return
            }
            property.setXml(new XML().wrapped(true))
            Class<?> element = elementType(type, propertyNamed(names, described), entity)
            if (element != null && property.items != null && !property.items.$ref) {
                property.items.setXml(new XML().name(GrailsNameUtils.getPropertyName(element)))
            }
        }
    }

    /**
     * The class of the values a collection property holds: the one GORM maps it with, or the one
     * its type declares.
     */
    private static Class<?> elementType(Class<?> type, String name, PersistentEntity entity) {
        PersistentProperty persistent = entity?.getPropertyByName(name)
        if (persistent instanceof Basic && ((Basic) persistent).componentType != null) {
            return ((Basic) persistent).componentType
        }
        Method getter = BeanUtils.getPropertyDescriptor(type, name)?.readMethod
        if (getter == null) {
            return null
        }
        ResolvableType declared = ResolvableType.forMethodReturnType(getter)
        declared.array ? declared.componentType.resolve() : declared.asCollection().resolveGeneric(0)
    }

    /**
     * Reads a declaration of a type, logging rather than throwing where it cannot be read.
     */
    private static <T> T read(String what, Closure<T> declaration) {
        try {
            return declaration.call()
        }
        catch (RuntimeException | LinkageError e) {
            LOG.warn("Could not read ${what}; it is described without them", e)
            return null
        }
    }

    /**
     * The name in the type of a property the document describes under the given name.
     */
    private static String propertyNamed(Map<String, String> names, String described) {
        names.find { String name, String describedAs -> describedAs == described }?.key ?: described
    }

    private static Schema property(Schema model, Map<String, String> names, String name) {
        (Schema) model.properties?.get(names[name] ?: name)
    }

    /**
     * A property data binding does not bind - one the server assigns, such as {@code dateCreated},
     * one constrained {@code bindable: false}, or a transient - is not something a client sends,
     * so it is read only.
     */
    private static void markUnbound(Schema model, Map<String, String> names, List<String> bindable,
                                    Set<String> beanProperties) {
        if (!model.properties) {
            return
        }
        new ArrayList<String>(((Map<String, Schema>) model.properties).keySet()).each { String described ->
            String name = propertyNamed(names, described)
            if (!(name in bindable) && name in beanProperties) {
                describable(model, described).setReadOnly(true)
            }
        }
    }

    /**
     * The schema a property is described by: its own, or, where it is a reference in an OpenAPI
     * 3.0 document, which ignores anything beside a {@code $ref}, one holding all of the reference,
     * which describes the property from then on.
     */
    private static Schema describable(Schema model, String described) {
        Schema property = (Schema) model.properties?.get(described)
        if (property?.$ref && model.specVersion != SpecVersion.V31) {
            ComposedSchema reference = new ComposedSchema()
            reference.setAllOf([property])
            model.properties[described] = reference
            return reference
        }
        property
    }

    /**
     * OpenAPI 3.0 ignores anything beside a {@code $ref}, so swagger-core leaves out of a property
     * described by one what its {@code @Schema} says of the property. It is said of all of the
     * reference instead.
     */
    private static void declareReferenceSiblings(Schema model, Map<String, String> names, PropertyNames propertyNames) {
        if (model.specVersion == SpecVersion.V31 || !model.properties) {
            return
        }
        new ArrayList<String>(((Map<String, Schema>) model.properties).keySet()).each { String described ->
            SchemaAnnotation declared = ((Schema) model.properties[described]).$ref
                    ? propertyNames.declaredSchema(propertyNamed(names, described)) : null
            AccessMode access = declared?.accessMode()
            boolean readOnly = access == AccessMode.READ_ONLY
            boolean writeOnly = access == AccessMode.WRITE_ONLY
            if (declared == null || !(declared.description() || declared.deprecated() || readOnly || writeOnly)) {
                return
            }
            Schema property = describable(model, described)
            if (declared.description()) {
                property.setDescription(declared.description())
            }
            if (declared.deprecated()) {
                property.setDeprecated(true)
            }
            if (readOnly) {
                property.setReadOnly(true)
            }
            if (writeOnly) {
                property.setWriteOnly(true)
            }
        }
    }

    /**
     * Grails declares the properties it binds on a command object an action takes.
     */
    private static boolean declaresBindableProperties(Class<?> type) {
        try {
            type.getField(DATABINDING_WHITELIST)
            return true
        }
        catch (NoSuchFieldException | SecurityException ignored) {
            return false
        }
    }

    /**
     * A command property that can only be read - one with a getter and nothing to bind it to - is
     * not something a client sends, so it is read only and its constraints are not required of a
     * request.
     */
    private static Set<String> readOnlyConstrained(Class<?> type, Collection<String> constrained) {
        constrained.findAll { String name -> !isWritable(type, name) }.toSet()
    }

    private static boolean isWritable(Class<?> type, String name) {
        String setter = 'set' + name.capitalize()
        type.methods.any { Method method -> method.name == setter && method.parameterCount == 1 }
    }

    /**
     * Grails renders an entity as its identifier, its persistent properties and, where it is
     * configured to, its version. Anything else swagger-core finds - a transient, a getter with
     * nothing persisted behind it, a public field, or the accessor GORM adds for the foreign key of
     * an association - is not part of it.
     */
    private static void describeEntity(PersistentEntity entity, Schema model, Map<String, String> names,
                                       String versionName) {
        String identityName = entity.identity?.name
        ((Map<String, Schema>) model.properties)?.keySet()?.removeAll { String described ->
            String name = names.find { String candidate, String describedAs -> describedAs == described }?.key
            name == null || (name != identityName && (name == versionName || entity.getPropertyByName(name) == null))
        }

        markReadOnly(model, names[identityName] ?: identityName)
        if (identityName && identityName != IDENTITY) {
            // Grails renders the identifier in XML as the id attribute, whatever it is named.
            ((Schema) model.properties[names[identityName] ?: identityName]).xml.setName(IDENTITY)
        }
        if (INCLUDE_VERSION.get()) {
            markReadOnly(model, names[versionName] ?: versionName)
        }
    }

    /**
     * GORM adds the version through a transform swagger-core does not see, so a property the
     * server assigns is added where it is missing rather than only flagged. Grails renders it in XML
     * as an attribute.
     */
    private static void markReadOnly(Schema model, String propertyName) {
        if (!propertyName) {
            return
        }
        Schema property = (Schema) model.properties[propertyName]
        if (property == null) {
            property = new IntegerSchema().format(INT64_FORMAT)
            model.addProperty(propertyName, property)
        }
        property.setReadOnly(true)
        property.setXml(new XML().attribute(true))
    }

    private static Map<String, Constrained> entityConstraints(PersistentEntity entity) {
        Validator validator = entity.mappingContext?.getEntityValidator(entity)
        if (validator instanceof ConstrainedEntity) {
            Map<String, ConstrainedProperty> declared = ((ConstrainedEntity) validator).constrainedProperties
            return declared ? new LinkedHashMap<String, Constrained>(declared) : Collections.<String, Constrained> emptyMap()
        }
        Collections.<String, Constrained> emptyMap()
    }

    private static Map<String, Constrained> validateableConstraints(Class<?> type) {
        Map<String, Constrained> declared = (Map<String, Constrained>) InvokerHelper.invokeStaticMethod(
                type, 'getConstraintsMap', null)
        declared ?: Collections.<String, Constrained> emptyMap()
    }

    private static void applyConstraints(Schema model, Map<String, Constrained> constraints, String versionName,
                                         Map<String, String> names) {
        constraints.each { String name, Constrained constrained ->
            Schema property = property(model, names, name)
            if (property == null) {
                return
            }
            applyConstraints(property, constrained)
            String described = names[name] ?: name
            if (constrained.nullable && property.$ref) {
                if (model.specVersion == SpecVersion.V31) {
                    model.properties[described] = nullableReference(property)
                }
                else {
                    describable(model, described).setNullable(true)
                }
            }
            if (!constrained.nullable && name != versionName && !model.required?.contains(described)) {
                model.addRequiredItem(described)
            }
        }
    }

    /**
     * A reference is never null in itself, so a nullable property described by one is the
     * referenced schema or null: in OpenAPI 3.1 one of the reference and a null type. In 3.0 it is
     * all of the reference, nullable.
     */
    private static Schema nullableReference(Schema reference) {
        Schema nullable = new JsonSchema()
        nullable.setOneOf([reference, new JsonSchema().types([NULL_TYPE] as Set<String>)])
        nullable.setDescription(reference.description)
        nullable.setReadOnly(reference.readOnly)
        nullable.setWriteOnly(reference.writeOnly)
        nullable.setDeprecated(reference.deprecated)
        reference.setDescription(null)
        reference.setReadOnly(null)
        reference.setWriteOnly(null)
        reference.setDeprecated(null)
        nullable
    }

    private static void applyConstraints(Schema schema, Constrained constrained) {
        if (constrained.inList && !schema.enum) {
            Schema<Object> target = (Schema<Object>) schema
            constrained.inList.each { target.addEnumItemObject(it) }
        }

        // The string constraints throw rather than return null when read from a property of
        // another type, so they are consulted only where the property is a string. The test is
        // the one the constraint itself applies - the property type, not the schema, which
        // describes a date, a UUID and a byte array as a string too.
        Class<?> type = constrained instanceof ConstrainedProperty ? ((ConstrainedProperty) constrained).propertyType : null
        if (type != null && CharSequence.isAssignableFrom(type)) {
            applyStringConstraints(schema, constrained)
        }
        else if (type != null && (Number.isAssignableFrom(type) || type.primitive)) {
            applyNumericConstraints(schema, constrained)
        }
        else if (type != null && (Collection.isAssignableFrom(type) || type.array)) {
            applyCollectionConstraints(schema, constrained)
        }

        if (constrained.nullable && !schema.$ref) {
            allowNull(schema)
        }
    }

    /**
     * A nullable property is rendered as null where it has no value, and bound from null. OpenAPI
     * 3.0 says so with {@code nullable}, and 3.1 with a {@code null} type; each version ignores the
     * other's, so both are set. A value a list constrains must be listed to be valid, null included.
     */
    private static void allowNull(Schema schema) {
        schema.setNullable(true)
        Set<String> types = schema.types ? new LinkedHashSet<String>(schema.types)
                : (schema.type ? new LinkedHashSet<String>([schema.type]) : null)
        if (types != null) {
            types << NULL_TYPE
            schema.setTypes(types)
        }
        if (schema.enum && !schema.enum.contains(null)) {
            ((Schema<Object>) schema).addEnumItemObject(null)
        }
    }

    private static void applyCollectionConstraints(Schema schema, Constrained constrained) {
        Integer maxSize = constrained.maxSize != null ? constrained.maxSize
                : (constrained.size != null ? (Integer) constrained.size.to : null)
        Integer minSize = constrained.minSize != null ? constrained.minSize
                : (constrained.size != null ? (Integer) constrained.size.from : null)
        if (maxSize != null) {
            schema.setMaxItems(maxSize)
        }
        if (minSize != null) {
            schema.setMinItems(minSize)
        }
    }

    private static void applyStringConstraints(Schema schema, Constrained constrained) {
        // Grails requires the whole value to match, where a schema pattern matches any part of it.
        if (constrained.matches) {
            schema.setPattern("^(?:${constrained.matches})\$".toString())
        }
        if (constrained.email) {
            schema.setFormat(EMAIL_FORMAT)
        }
        else if (constrained.url) {
            schema.setFormat(URI_FORMAT)
        }

        // A zero bound is falsy in Groovy, so the presence of each value is tested rather than
        // its truth: maxSize: 0 and min: 0 are real constraints.
        Integer maxSize = constrained.maxSize != null ? constrained.maxSize
                : (constrained.size != null ? (Integer) constrained.size.to : null)
        Integer minSize = constrained.minSize != null ? constrained.minSize
                : (constrained.size != null ? (Integer) constrained.size.from : null)
        if (maxSize != null) {
            schema.setMaxLength(maxSize)
        }
        if (minSize != null) {
            schema.setMinLength(minSize)
        }
        // A blank value is rejected, and the shortest value that is not blank is one character.
        if (!constrained.blank && (schema.minLength == null || schema.minLength < 1)) {
            schema.setMinLength(1)
        }
    }

    private static void applyNumericConstraints(Schema schema, Constrained constrained) {
        Comparable min = constrained.min != null ? constrained.min
                : (constrained.range != null ? (Comparable) constrained.range.from : null)
        Comparable max = constrained.max != null ? constrained.max
                : (constrained.range != null ? (Comparable) constrained.range.to : null)
        if (min instanceof Number) {
            schema.setMinimum(toBigDecimal((Number) min))
        }
        if (max instanceof Number) {
            schema.setMaximum(toBigDecimal((Number) max))
        }
    }

    private static BigDecimal toBigDecimal(Number value) {
        value instanceof BigDecimal ? (BigDecimal) value : new BigDecimal(value.toString())
    }

    private static Class<?> rawClass(Type type) {
        javaType(type)?.rawClass
    }

    private static JavaType javaType(Type type) {
        if (type == null) {
            return null
        }
        try {
            return TypeFactory.defaultInstance().constructType(type)
        }
        catch (IllegalArgumentException ignored) {
            return null
        }
    }
}
