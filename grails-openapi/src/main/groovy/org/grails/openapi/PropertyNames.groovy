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

import groovy.transform.CompileStatic

import com.fasterxml.jackson.databind.BeanDescription
import com.fasterxml.jackson.databind.SerializationConfig
import com.fasterxml.jackson.databind.introspect.AnnotatedMember
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition
import io.swagger.v3.core.util.Json
import io.swagger.v3.oas.annotations.media.Schema as SchemaAnnotation
import io.swagger.v3.oas.models.media.Schema
import org.springframework.beans.BeanUtils

/**
 * The names the properties of a type Grails renders and binds are described under.
 *
 * <p>swagger-core names a property as Jackson does, which is not always the name Grails renders and
 * binds it by: Jackson describes {@code getISBN()} as {@code isbn}, where Grails uses {@code ISBN},
 * and {@code @JsonProperty} renames a property Grails' converters, JSON views and data binding still
 * render and bind by its own name. A property is described by the name Grails uses, unless its
 * description is renamed on purpose, with {@code @Schema(name)}.</p>
 */
@CompileStatic
class PropertyNames {

    /**
     * The name swagger-core describes each property under, by the name Grails uses.
     */
    private final Map<String, String> swaggerNames

    /**
     * The properties whose description is renamed on purpose.
     */
    private final Set<String> renamed

    /**
     * The {@code @Schema} each property declares, by the name Grails uses.
     */
    private final Map<String, SchemaAnnotation> declared

    private PropertyNames(Map<String, String> swaggerNames, Set<String> renamed, Map<String, SchemaAnnotation> declared) {
        this.swaggerNames = swaggerNames
        this.renamed = renamed
        this.declared = declared
    }

    /**
     * The names of a type's properties, as Jackson, which swagger-core describes them with, reads
     * them. A type Jackson cannot introspect is described by the names swagger-core gives.
     */
    static PropertyNames of(Class<?> type) {
        Map<String, String> swaggerNames = [:]
        Set<String> renamed = [] as Set
        Map<String, SchemaAnnotation> declared = [:]
        List<BeanPropertyDefinition> properties
        try {
            SerializationConfig config = Json.mapper().serializationConfig
            BeanDescription description = config.introspect(config.constructType(type))
            properties = description.findProperties()
        }
        catch (RuntimeException | LinkageError ignored) {
            return new PropertyNames(swaggerNames, renamed, declared)
        }
        for (BeanPropertyDefinition property : properties) {
            try {
                String name = beanName(property)
                if (name) {
                    String schemaName = schemaName(property)
                    swaggerNames[name] = schemaName ?: property.name
                    if (schemaName) {
                        renamed << name
                    }
                    SchemaAnnotation schema = schemaAnnotation(property)
                    if (schema != null) {
                        declared[name] = schema
                    }
                }
            }
            catch (RuntimeException ignored) {
                // Jackson refuses a property with conflicting accessors, such as Groovy's metaClass.
            }
        }
        new PropertyNames(swaggerNames, renamed, declared)
    }

    /**
     * Describes each property of a model swagger-core resolved under the name Grails uses for it,
     * unless it is renamed on purpose.
     *
     * @return the name each property is now described under, by the name Grails uses, for every
     * property that is one of the type's
     */
    Map<String, String> applyTo(Schema model) {
        Map<String, String> described = [:]
        Map<String, Schema> properties = (Map<String, Schema>) model.properties
        if (!properties) {
            return described
        }
        // A name kept as it is - renamed on purpose, or no property's - holds its place, and a
        // property takes the name Grails uses only where nothing else is described by it.
        Set<String> taken = properties.keySet().findAll { String key ->
            String name = nameOf(key)
            name == null || name in renamed || name == key
        }.toSet()
        Map<String, Schema> named = new LinkedHashMap<>()
        List<String> required = model.required != null ? new ArrayList<String>(model.required) : null
        properties.each { String key, Schema property ->
            String name = nameOf(key)
            String describedAs = key
            if (name != null && !(name in renamed) && name != key && !(name in taken)) {
                describedAs = name
                taken << name
            }
            named[describedAs] = property
            if (name != null) {
                described[name] = describedAs
            }
            if (describedAs != key && required != null && required.contains(key)) {
                required[required.indexOf(key)] = describedAs
            }
        }
        model.setProperties(named)
        if (required != null) {
            model.setRequired(required)
        }
        described
    }

    /**
     * @return the name Grails uses for the property swagger-core describes under the given name,
     * or {@code null} where it is not one of the type's
     */
    String nameOf(String swaggerName) {
        swaggerNames.find { String name, String describedAs -> describedAs == swaggerName }?.key
                ?: (swaggerNames.containsKey(swaggerName) ? swaggerName : null)
    }

    private static String beanName(BeanPropertyDefinition property) {
        if (property.field != null) {
            return property.field.name
        }
        AnnotatedMethod accessor = property.getter ?: property.setter
        PropertyDescriptor descriptor = accessor != null ? BeanUtils.findPropertyForMethod(accessor.annotated) : null
        descriptor?.name ?: property.internalName
    }

    /**
     * @return the {@code @Schema} a property declares, by the name Grails uses for it, or
     * {@code null} where it declares none
     */
    SchemaAnnotation declaredSchema(String name) {
        declared[name]
    }

    private static String schemaName(BeanPropertyDefinition property) {
        for (AnnotatedMember member : [property.getter, property.field, property.setter]) {
            String name = member?.getAnnotation(SchemaAnnotation)?.name()
            if (name) {
                return name
            }
        }
        null
    }

    /**
     * The {@code @Schema} of a property: the getter's, the field's, where Groovy puts the
     * annotation of a property, or the setter's.
     */
    private static SchemaAnnotation schemaAnnotation(BeanPropertyDefinition property) {
        for (AnnotatedMember member : [property.getter, property.field, property.setter]) {
            SchemaAnnotation schema = member?.getAnnotation(SchemaAnnotation)
            if (schema != null) {
                return schema
            }
        }
        null
    }
}
