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

import java.lang.annotation.Annotation

import groovy.transform.CompileStatic

import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.PropertyName
import com.fasterxml.jackson.databind.SerializationConfig
import com.fasterxml.jackson.databind.type.TypeFactory
import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.jackson.ModelResolver
import io.swagger.v3.core.jackson.TypeNameResolver
import io.swagger.v3.core.util.AnnotationsUtils
import io.swagger.v3.core.util.Json
import io.swagger.v3.core.util.PrimitiveType
import io.swagger.v3.core.util.ReflectionUtils
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema as SchemaAnnotation

/**
 * Keeps each schema name in a document to one thing.
 *
 * <p>swagger-core names a class by its simple name, so two classes sharing a simple name in
 * different packages would be described by one schema, and a class sharing the name of a schema
 * the document already has or derives, such as the validation errors, would stand in for it.</p>
 *
 * <p>A name more than one class claims is taken by none of them: each is named by the package it
 * is declared in as well, whichever is described first, so the names do not depend on the order
 * the document is described in. A reserved name keeps its schema, and a class claiming it is named
 * by its package.</p>
 *
 * <p>While a document is described, the first to claim a name holds it and a later claimant is
 * given its qualified name. {@link #renames} then says which names to move once the document is
 * complete.</p>
 */
@CompileStatic
class SchemaNames {

    private static final String RESERVED = 'reserved:'

    /**
     * The claimant holding each name: the type of a class, or the marker of a reserved name.
     */
    private final Map<String, Object> holders = [:]

    private final Map<JavaType, String> qualifiedNames = [:]

    /**
     * The names claimed by a class and something else.
     */
    private final Set<String> shared = new LinkedHashSet<>()

    /**
     * The name a schema was given while a class held the name reserved for it.
     */
    private final Map<String, String> displaced = [:]

    /**
     * Whether swagger-core describes the type as a schema of its own, which is named, rather than
     * inline. An enum is described inline unless it is described as a schema of its own, by
     * {@code @Schema(enumAsRef = true)} or swagger-core's configuration.
     */
    static boolean isNamed(AnnotatedType annotatedType, JavaType type) {
        if (type.enumType) {
            return isEnumNamed(annotatedType, type)
        }
        !type.containerType && !type.primitive && !type.javaLangObject && !ReflectionUtils.isSystemType(type)
                && PrimitiveType.fromType(type) == null
    }

    /**
     * Whether swagger-core describes the type as itself, rather than another in its place: the
     * implementation or the primitive type a {@code @Schema} annotation names, or the value a
     * {@code @JsonValue} accessor returns, which is resolved under the name the type was given. An
     * enum is described as itself whatever it is serialized as, its values read from its
     * {@code @JsonValue} accessor where it has one.
     */
    static boolean isDescribedAsItself(AnnotatedType annotatedType, JavaType type) {
        if (annotatedType.skipOverride) {
            return true
        }
        SchemaAnnotation declared = schemaAnnotation(annotatedType, type)
        if (declared != null && (declared.implementation() != Void || isPrimitiveOverride(declared, type))) {
            return false
        }
        if (type.enumType) {
            return true
        }
        !SERIALIZED_AS_VALUE.get(type.rawClass)
    }

    /**
     * Whether a class is serialized as the value of a {@code @JsonValue} accessor, which is read
     * once for each class rather than for each time it is resolved.
     */
    private static final ClassValue<Boolean> SERIALIZED_AS_VALUE = new ClassValue<Boolean>() {

        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                SerializationConfig config = Json.mapper().serializationConfig
                return config.introspect(config.constructType(type)).findJsonValueAccessor() != null
            }
            catch (RuntimeException ignored) {
                return false
            }
        }
    }

    private static boolean isPrimitiveOverride(SchemaAnnotation declared, JavaType type) {
        String declaredType = declared.type()
        declaredType && declaredType != 'object' && (PrimitiveType.fromTypeAndFormat(declaredType, declared.format()) != null
                || PrimitiveType.fromType(type) != null || PrimitiveType.fromName(declaredType) != null)
    }

    private static SchemaAnnotation schemaAnnotation(AnnotatedType annotatedType, JavaType type) {
        Annotation merged = AnnotationsUtils.mergeSchemaAnnotations(annotatedType.ctxAnnotations, type)
        merged instanceof ArraySchema ? ((ArraySchema) merged).schema() : (SchemaAnnotation) merged
    }

    private static boolean isEnumNamed(AnnotatedType annotatedType, JavaType type) {
        if (ModelResolver.enumsAsRef || annotatedType.resolveEnumAsRef) {
            return true
        }
        SchemaAnnotation declared = schemaAnnotation(annotatedType, type)
        declared != null && declared.enumAsRef()
    }

    /**
     * The name swagger-core gives the type: the name a {@code @Schema} annotation declares, the
     * root name Jackson declares, or the simple name.
     */
    static String naturalName(AnnotatedType annotatedType, JavaType type) {
        if (!annotatedType.skipSchemaName) {
            Annotation declared = annotatedType.ctxAnnotations?.find { Annotation it -> it instanceof SchemaAnnotation }
            String name = declared != null ? ((SchemaAnnotation) declared).name() : null
            if (name) {
                return name
            }
        }
        SerializationConfig config = Json.mapper().serializationConfig
        PropertyName root = config.annotationIntrospector?.findRootName(config.introspectClassAnnotations(type).classInfo)
        root?.hasSimpleName() ? root.simpleName : TypeNameResolver.std.nameForType(type)
    }

    /**
     * @return the name to describe the class under while the document is described
     */
    String claim(JavaType type, String natural) {
        Object holder = holders.putIfAbsent(natural, type)
        if (holder == null || holder == type) {
            return natural
        }
        shared << natural
        qualifiedName(type, natural)
    }

    /**
     * Reserves a name for a schema that is not resolved from a class: one the document already
     * has, or one it derives.
     *
     * @return the name to describe it under while the document is described
     */
    String reserve(String name) {
        String marker = RESERVED + name
        Object holder = holders.putIfAbsent(name, marker)
        if (holder == null || holder == marker) {
            return name
        }
        shared << name
        displaced.computeIfAbsent(name) { String taken -> unclaimed(taken + '_', marker) }
    }

    /**
     * Keeps a name the document already has for the class its schema was resolved from, as springdoc
     * resolves a class one of its endpoints returns, so the class is described by that schema,
     * under its name, rather than apart from it.
     *
     * @param resolvedFrom the class, or {@code null} where it is not known
     */
    String reserve(String name, Class<?> resolvedFrom) {
        resolvedFrom != null ? claim(TypeFactory.defaultInstance().constructType(resolvedFrom), name) : reserve(name)
    }

    /**
     * Claims the name a reserved schema moves to once the document is complete, such as a patch
     * schema following the schema it is a patch of, apart from any class that holds it.
     *
     * @return the name to move the schema to
     */
    String move(String reserved, String target) {
        unclaimed(target, RESERVED + reserved)
    }

    /**
     * The names to move once the document is complete: each class holding a name something else
     * also claims moves to its qualified name, and each schema a class kept from its reserved name
     * moves to it.
     */
    Map<String, String> renames() {
        Map<String, String> renames = [:]
        for (String name : shared) {
            Object holder = holders[name]
            if (holder instanceof JavaType) {
                renames[name] = qualifiedName((JavaType) holder, name)
            }
        }
        displaced.each { String name, String temporary -> renames[temporary] = name }
        renames
    }

    /**
     * The name qualified by the package the class is declared in, and the class it is nested in.
     */
    private String qualifiedName(JavaType type, String natural) {
        String known = qualifiedNames[type]
        if (known != null) {
            return known
        }
        Class<?> declared = type.rawClass
        String enclosing = declared.name.substring(0, declared.name.length() - declared.simpleName.length())
        String qualified = unclaimed(enclosing.replace('$', '.') + natural, type)
        qualifiedNames[type] = qualified
        qualified
    }

    private String unclaimed(String name, Object claimant) {
        String candidate = name
        int suffix = 2
        Object holder
        while ((holder = holders.putIfAbsent(candidate, claimant)) != null && holder != claimant) {
            candidate = name + suffix++
        }
        candidate
    }
}
