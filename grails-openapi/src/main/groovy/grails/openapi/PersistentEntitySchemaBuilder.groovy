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
package grails.openapi

import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

import groovy.transform.CompileStatic

import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.core.converter.ResolvedSchema
import io.swagger.v3.oas.annotations.media.Schema as SchemaAnnotation
import io.swagger.v3.oas.models.SpecVersion
import io.swagger.v3.oas.models.media.IntegerSchema
import io.swagger.v3.oas.models.media.Schema
import org.codehaus.groovy.runtime.InvokerHelper
import org.springframework.validation.Validator

import grails.gorm.validation.Constrained
import grails.gorm.validation.ConstrainedEntity
import grails.gorm.validation.ConstrainedProperty
import grails.validation.Validateable
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association

/**
 * Builds OpenAPI schemas for Grails domain classes.
 *
 * <p>The type is resolved through swagger-core, so a {@code @Schema} annotation on a domain class or
 * one of its properties is honored and associated classes are resolved without this class walking
 * them. The declared GORM constraints are then applied over that result, which swagger-core cannot
 * see: {@code nullable: false} becomes a required member, {@code maxSize} becomes {@code maxLength},
 * {@code inList} becomes an enumeration, {@code matches} becomes a pattern, and {@code email} or
 * {@code url} becomes a format.</p>
 *
 * <p>The identifier and version are marked {@code readOnly}, because the server assigns them.</p>
 *
 * @author Scott Murphy Heiberg
 * @since 8.0
 */
@CompileStatic
class PersistentEntitySchemaBuilder {

    private static final String EMAIL_FORMAT = 'email'
    private static final String URI_FORMAT = 'uri'
    private static final String INT64_FORMAT = 'int64'

    /**
     * @param entity the GORM entity to describe
     * @param mappingContext supplies the entity validator that carries the declared constraints
     * @return every schema the entity resolves to, keyed by schema name, including the classes it
     * is associated with
     */
    Map<String, Schema> build(PersistentEntity entity, MappingContext mappingContext = null,
                              SpecVersion specVersion = SpecVersion.V30) {
        ResolvedSchema resolved = converters(specVersion).readAllAsResolvedSchema(entity.javaClass)
        Map<String, Schema> schemas = [:]
        if (resolved?.referencedSchemas) {
            schemas.putAll(resolved.referencedSchemas)
        }
        else if (resolved?.schema) {
            schemas[schemaName(entity)] = resolved.schema
        }

        schemas.each { String name, Schema schema ->
            PersistentEntity described = entityNamed(name, mappingContext)
            if (described != null) {
                applyGormMetadata(schema, described, mappingContext)
            }
        }
        schemas
    }

    /**
     * Describes a command object, which is not a persistent entity but declares constraints the
     * same way, so a request body is described by what the action actually binds.
     *
     * @param commandType the command object an action takes
     * @return every schema the command resolves to, keyed by schema name
     */
    Map<String, Schema> buildCommand(Class<?> commandType, SpecVersion specVersion = SpecVersion.V30) {
        ResolvedSchema resolved = converters(specVersion).readAllAsResolvedSchema(commandType)
        Map<String, Schema> resolvedSchemas = [:]
        if (resolved?.referencedSchemas) {
            resolvedSchemas.putAll(resolved.referencedSchemas)
        }
        else if (resolved?.schema) {
            resolvedSchemas[schemaName(commandType)] = resolved.schema
        }

        // Validateable contributes errors, and every Groovy object contributes metaClass. Left in,
        // each drags its whole object graph into the document, so the walk follows the fields the
        // commands declare rather than everything swagger-core resolved. A nested command is
        // pruned and constrained the same way the outer one is.
        Map<String, Schema> schemas = [:]
        Set<Class<?>> visited = []
        Deque<Class<?>> pending = new ArrayDeque<>([commandType])

        while (!pending.isEmpty()) {
            Class<?> type = pending.poll()
            if (!visited.add(type)) {
                continue
            }

            Schema schema = resolvedSchemas[schemaName(type)]
            if (schema == null) {
                continue
            }

            retainDeclaredProperties(schema, type)
            applyDeclaredConstraints(schema, constraintsFor(type))
            schemas[schemaName(type)] = schema

            declaredPropertyTypes(type, schema.properties?.keySet() ?: [] as Set).each { Class<?> nested ->
                if (resolvedSchemas.containsKey(schemaName(nested))) {
                    pending.add(nested)
                }
            }
        }
        schemas
    }

    /**
     * The types the command's described fields refer to, including the element type of a
     * collection, so a nested command is described rather than referred to and left undefined.
     */
    /**
     * Collects the classes a generic signature names, at any depth: a
     * {@code List<List<Command>>} names its element type two levels in.
     */
    private static void collectTypeArguments(Type type, Set<Class<?>> types) {
        if (!(type instanceof ParameterizedType)) {
            return
        }
        ((ParameterizedType) type).actualTypeArguments.each { Type argument ->
            if (argument instanceof Class) {
                types << (Class<?>) argument
            }
            else {
                collectTypeArguments(argument, types)
            }
        }
    }

    private static Set<Class<?>> declaredPropertyTypes(Class<?> commandType, Set<String> described) {
        Set<Class<?>> types = [] as Set
        for (Class<?> type = commandType; type != null && type != Object; type = type.superclass) {
            type.declaredFields.each { Field field ->
                // Only a field the schema still describes is followed. A trait contributes fields
                // under a mangled name, which pruning already dropped, and following those would
                // reintroduce exactly what pruning removed.
                if (field.synthetic || Modifier.isStatic(field.modifiers) || !described.contains(field.name)) {
                    return
                }
                types << field.type
                collectTypeArguments(field.genericType, types)
            }
        }
        types
    }

    private static void retainDeclaredProperties(Schema schema, Class<?> commandType) {
        Map<String, Schema> properties = schema.properties
        if (properties == null) {
            return
        }
        Set<String> declared = declaredPropertyNames(commandType)
        properties.keySet().retainAll(declared)
        schema.required?.retainAll(declared)
    }

    private static Set<String> declaredPropertyNames(Class<?> commandType) {
        Set<String> names = [] as Set
        for (Class<?> type = commandType; type != null && type != Object; type = type.superclass) {
            type.declaredFields.each { Field field ->
                if (!field.synthetic && !Modifier.isStatic(field.modifiers) && !field.name.startsWith('$')) {
                    names << field.name
                }
            }
        }
        names
    }

    /**
     * Keeps only the schemas the retained properties still refer to, so pruning a property also
     * removes what it alone pulled in.
     */
    private static void retainReferenced(Map<String, Schema> keep, Map<String, Schema> available) {
        Deque<Schema> pending = new ArrayDeque<>(keep.values())
        while (!pending.isEmpty()) {
            Schema current = pending.poll()
            for (String name : referencedNames(current)) {
                if (!keep.containsKey(name) && available.containsKey(name)) {
                    Schema referenced = available[name]
                    keep[name] = referenced
                    pending.add(referenced)
                }
            }
        }
    }

    private static List<String> referencedNames(Schema schema) {
        List<String> names = []
        collectReference(schema.$ref, names)
        collectReference(schema.items?.$ref, names)
        schema.properties?.values()?.each { Schema property ->
            collectReference(property.$ref, names)
            collectReference(property.items?.$ref, names)
        }
        names
    }

    private static void collectReference(String ref, List<String> names) {
        if (ref) {
            names << ref.substring(ref.lastIndexOf('/') + 1)
        }
    }

    /**
     * A Validateable command object exposes its constraints statically. A command object that is
     * not Validateable simply carries none.
     */
    private static Map<String, Constrained> constraintsFor(Class<?> commandType) {
        if (!Validateable.isAssignableFrom(commandType)) {
            return Collections.<String, Constrained> emptyMap()
        }
        Map<String, Constrained> declared = (Map<String, Constrained>) InvokerHelper.invokeStaticMethod(
                commandType, 'getConstraintsMap', null)
        declared ?: Collections.<String, Constrained> emptyMap()
    }

    private static void applyDeclaredConstraints(Schema schema, Map<String, Constrained> constraints) {
        Map<String, Schema> properties = schema.properties
        if (properties == null) {
            return
        }
        constraints.each { String name, Constrained constrained ->
            Schema propertySchema = properties[name]
            if (propertySchema == null) {
                return
            }
            applyConstraints(propertySchema, constrained)
            if (!constrained.nullable && !isRequired(schema, name)) {
                schema.addRequiredItem(name)
            }
        }
    }

    /**
     * swagger-core keeps a separate converter list per specification version, so the schemas are
     * resolved with the one that matches the document being described. Resolving 3.0 shapes into a
     * 3.1 document would emit the wrong form for nullability, bounds and examples.
     */
    private static ModelConverters converters(SpecVersion specVersion) {
        ModelConverters.getInstance(specVersion == SpecVersion.V31)
    }

    /**
     * The schema name a domain class is registered and referenced under.
     */
    static String schemaName(PersistentEntity entity) {
        schemaName(entity.javaClass)
    }

    /**
     * swagger-core registers a type under the name its {@code @Schema} declares, so the name is
     * read the same way here. Otherwise the schema would be registered under one name and referred
     * to by another, and the GORM overlay would never find it.
     */
    static String schemaName(Class<?> type) {
        SchemaAnnotation declared = type.getAnnotation(SchemaAnnotation)
        declared?.name() ?: type.simpleName
    }

    static String referencePath(String schemaName) {
        "#/components/schemas/${schemaName}".toString()
    }

    private static PersistentEntity entityNamed(String name, MappingContext mappingContext) {
        mappingContext?.persistentEntities?.find { PersistentEntity candidate -> schemaName(candidate) == name }
    }

    /**
     * Applies what GORM knows and swagger-core does not.
     */
    private static void applyGormMetadata(Schema schema, PersistentEntity entity, MappingContext mappingContext) {
        Map<String, Schema> properties = schema.properties
        if (properties == null) {
            return
        }

        // swagger-core surfaces the foreign key accessor GORM adds alongside a to-one association;
        // the association itself already describes the relationship.
        for (Association association : entity.associations) {
            properties.remove("${association.name}Id".toString())
        }

        markReadOnly(properties, entity.identity?.name, schema)
        if (entity.versioned) {
            markReadOnly(properties, entity.version?.name, schema)
        }

        Map<String, ConstrainedProperty> constraints = constraintsFor(entity, mappingContext)
        String versionName = entity.versioned ? entity.version?.name : null

        for (PersistentProperty property : entity.persistentProperties) {
            Constrained constrained = constraints[property.name]
            Schema propertySchema = properties[property.name]
            if (constrained == null || propertySchema == null) {
                continue
            }

            applyConstraints(propertySchema, constrained)

            if (!constrained.nullable && property.name != versionName && !isRequired(schema, property.name)) {
                schema.addRequiredItem(property.name)
            }
        }
    }

    /**
     * A property the server assigns. GORM adds the version through a transform that swagger-core
     * does not see, so it is described here rather than only flagged.
     */
    private static void markReadOnly(Map<String, Schema> properties, String propertyName, Schema owner) {
        if (!propertyName) {
            return
        }
        Schema property = properties[propertyName]
        if (property == null) {
            property = new IntegerSchema().format(INT64_FORMAT)
            owner.addProperty(propertyName, property)
        }
        property.setReadOnly(true)
    }

    private static boolean isRequired(Schema schema, String propertyName) {
        schema.required?.contains(propertyName)
    }

    private static Map<String, ConstrainedProperty> constraintsFor(PersistentEntity entity, MappingContext mappingContext) {
        if (mappingContext == null) {
            return Collections.<String, ConstrainedProperty> emptyMap()
        }
        Validator validator = mappingContext.getEntityValidator(entity)
        validator instanceof ConstrainedEntity
                ? ((ConstrainedEntity) validator).constrainedProperties ?: Collections.<String, ConstrainedProperty> emptyMap()
                : Collections.<String, ConstrainedProperty> emptyMap()
    }

    private static void applyConstraints(Schema schema, Constrained constrained) {
        if (constrained.inList) {
            Schema<Object> enumTarget = (Schema<Object>) schema
            constrained.inList.each { enumTarget.addEnumItemObject(it) }
        }

        // The String-only constraints throw rather than return null when read from a property of
        // another type, so they are consulted only where the constrained property is a string. The
        // test is the one the constraint itself applies - the property type, not the schema, which
        // describes a date, a UUID and a byte array as a string too.
        if (isStringProperty(constrained)) {
            applyStringConstraints(schema, constrained)
        }
        else if (isNumericProperty(constrained)) {
            applyNumericConstraints(schema, constrained)
        }
    }

    /**
     * Mirrors {@code DefaultConstrainedProperty.isNotValidStringType}, which is what decides whether
     * reading a string constraint throws.
     */
    private static boolean isStringProperty(Constrained constrained) {
        Class<?> type = propertyType(constrained)
        type != null && CharSequence.isAssignableFrom(type)
    }

    private static boolean isNumericProperty(Constrained constrained) {
        Class<?> type = propertyType(constrained)
        type != null && (Number.isAssignableFrom(type) || type.primitive)
    }

    private static Class<?> propertyType(Constrained constrained) {
        constrained instanceof ConstrainedProperty ? ((ConstrainedProperty) constrained).propertyType : null
    }

    private static void applyStringConstraints(Schema schema, Constrained constrained) {
        if (constrained.matches) {
            schema.setPattern(constrained.matches)
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
}
