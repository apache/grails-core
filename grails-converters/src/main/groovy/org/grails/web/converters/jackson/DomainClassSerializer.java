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
package org.grails.web.converters.jackson;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.JacksonSerializable;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.jsontype.TypeSerializer;
import tools.jackson.databind.ser.std.StdSerializer;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

import org.grails.core.exceptions.GrailsConfigurationException;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.ManyToOne;
import org.grails.datastore.mapping.model.types.OneToOne;
import org.grails.datastore.mapping.reflect.NameUtils;
import org.grails.datastore.mapping.reflect.ReflectionUtils;

/**
 * Serializes domain class instances as {@code grails.converters.JSON} renders them: an object of the id, the version
 * and the class name when they are included, and the persistent properties. An associated domain class instance is a
 * reference of its id, such as {@code {"id":1}}, or, when rendering deep, the instance in full. Embedded instances and
 * collections of basic values are written in full.
 *
 * <p>Property values are written with {@link JsonGenerator#writePOJO(Object)}, so a mapper writes them as it writes any
 * other value, and a converter renders them with its marshallers. When rendering deep, an instance that is already
 * being written, further up, is written as a reference.
 *
 * <p>A property annotated with {@code @JsonIgnore}, or with {@code @JsonProperty(access = WRITE_ONLY)}, on its field
 * or getter, is not written. The {@link DomainClassRendering} decides about the others.
 *
 * @since 9.0
 */
public final class DomainClassSerializer extends StdSerializer<Object> {

    private static final String RENDERING_INSTANCES = DomainClassSerializer.class.getName() + ".instances";

    private static final ClassValue<Set<String>> IGNORED_PROPERTIES = new ClassValue<>() {
        @Override
        protected Set<String> computeValue(Class<?> type) {
            Set<String> ignored = new HashSet<>();
            for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
                for (Field field : current.getDeclaredFields()) {
                    if (ignoredForSerialization(field)) {
                        ignored.add(field.getName());
                    }
                }
                for (Method method : current.getDeclaredMethods()) {
                    if (ReflectionUtils.isGetter(method.getName(), method.getParameterTypes()) &&
                            ignoredForSerialization(method)) {
                        ignored.add(NameUtils.getPropertyNameForGetterOrSetter(method.getName()));
                    }
                }
            }
            return Set.copyOf(ignored);
        }
    };

    private final DomainClassRendering rendering;

    /**
     * @param rendering how to render domain class instances
     */
    public DomainClassSerializer(DomainClassRendering rendering) {
        super(Object.class);
        this.rendering = rendering;
    }

    /**
     * @return how this serializer renders domain class instances
     */
    public DomainClassRendering getRendering() {
        return rendering;
    }

    /**
     * A value that a mapper writes as a domain class instance rendered in a particular way, whether or not the mapper
     * has a {@link DomainClassJacksonModule}.
     *
     * @param object a domain class instance
     * @param rendering how to render it
     * @return the value to write
     */
    public static Object value(Object object, DomainClassRendering rendering) {
        return new JacksonSerializable.Base() {
            @Override
            public void serialize(JsonGenerator generator, SerializationContext context) {
                write(object, generator, context, rendering);
            }

            @Override
            public void serializeWithType(JsonGenerator generator, SerializationContext context,
                    TypeSerializer typeSerializer) {
                serialize(generator, context);
            }
        };
    }

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializationContext ctxt) {
        Object object = rendering.unwrap(value);
        Set<Object> instances = renderingInstances(ctxt);
        if (!instances.add(object)) {
            writeReference(object, entity(object, rendering), gen, rendering);
            return;
        }
        try {
            write(object, gen, ctxt, rendering);
        }
        finally {
            instances.remove(object);
        }
    }

    private static void write(Object value, JsonGenerator gen, SerializationContext ctxt,
            DomainClassRendering rendering) {
        Object object = rendering.unwrap(value);
        PersistentEntity entity = entity(object, rendering);
        Set<String> ignored = IGNORED_PROPERTIES.get(object.getClass());
        gen.writeStartObject(object);
        if (rendering.isIncludeClass() && rendering.includes(object, "class")) {
            gen.writeStringProperty("class", object.getClass().getName());
        }
        // a composite key has no identity, and is not written
        PersistentProperty identity = entity.getIdentity();
        if (identity != null && !ignored.contains(identity.getName()) && rendering.includes(object, identity.getName())) {
            Object id = rendering.propertyValue(object, identity);
            if (id != null) {
                gen.writeName(identity.getName());
                gen.writePOJO(id);
            }
        }
        if (rendering.isIncludeVersion() && !ignored.contains(GormProperties.VERSION) &&
                rendering.includes(object, GormProperties.VERSION)) {
            Object version = rendering.propertyValue(object, entity.getVersion());
            if (version != null) {
                gen.writeName(GormProperties.VERSION);
                gen.writePOJO(version);
            }
        }
        BeanWrapper bean = new BeanWrapperImpl(object);
        for (PersistentProperty property : entity.getPersistentProperties()) {
            if (property.equals(entity.getVersion()) || ignored.contains(property.getName()) ||
                    !rendering.includes(object, property.getName())) {
                continue;
            }
            gen.writeName(property.getName());
            Object propertyValue = bean.getPropertyValue(property.getName());
            if (property instanceof Association<?> association && propertyValue != null) {
                writeAssociation(association, propertyValue, gen, ctxt, rendering);
            }
            else {
                gen.writePOJO(propertyValue);
            }
        }
        gen.writeEndObject();
    }

    private static void writeAssociation(Association<?> association, Object value, JsonGenerator gen,
            SerializationContext ctxt, DomainClassRendering rendering) {
        PersistentEntity associated = association.getAssociatedEntity();
        if (rendering.isDeep()) {
            gen.writePOJO(copied(rendering.unwrap(value)));
        }
        else if (associated == null || association.isEmbedded() || association.getType().isEnum()) {
            gen.writePOJO(value);
        }
        else if (association instanceof OneToOne || association instanceof ManyToOne) {
            writeReference(value, associated, gen, rendering);
        }
        else if (value instanceof Collection<?> references) {
            gen.writeStartArray();
            for (Object reference : references) {
                writeReference(reference, associated, gen, rendering);
            }
            gen.writeEndArray();
        }
        else if (value instanceof Map<?, ?> references) {
            gen.writeStartObject();
            for (Map.Entry<?, ?> entry : references.entrySet()) {
                writeKey(entry.getKey(), gen, ctxt);
                writeReference(entry.getValue(), associated, gen, rendering);
            }
            gen.writeEndObject();
        }
        else {
            gen.writePOJO(value);
        }
    }

    private static void writeReference(Object reference, PersistentEntity entity, JsonGenerator gen,
            DomainClassRendering rendering) {
        if (reference == null) {
            gen.writeNull();
            return;
        }
        PersistentProperty identity = entity.getIdentity();
        if (rendering.writeReference(reference, identity, entity)) {
            return;
        }
        Object id = rendering.referenceId(reference, identity);
        gen.writeStartObject();
        if (rendering.isIncludeClass()) {
            gen.writeStringProperty("class", entity.getName());
        }
        if (id != null) {
            gen.writeName("id");
            // an id that is not a number, such as a MongoDB ObjectId, is written as its string
            if (id instanceof Number || id instanceof Boolean) {
                gen.writePOJO(id);
            }
            else {
                gen.writeString(id.toString());
            }
        }
        gen.writeEndObject();
    }

    private static void writeKey(Object key, JsonGenerator gen, SerializationContext ctxt) {
        if (key == null) {
            gen.writeName("null");
        }
        else if (key instanceof String name) {
            gen.writeName(name);
        }
        else {
            ctxt.findKeySerializer(key.getClass(), null).serialize(key, gen, ctxt);
        }
    }

    private static boolean ignoredForSerialization(AnnotatedElement element) {
        JsonIgnore ignore = element.getAnnotation(JsonIgnore.class);
        if (ignore != null && ignore.value()) {
            return true;
        }
        JsonProperty property = element.getAnnotation(JsonProperty.class);
        return property != null && property.access() == JsonProperty.Access.WRITE_ONLY;
    }

    private static PersistentEntity entity(Object object, DomainClassRendering rendering) {
        PersistentEntity entity = rendering.findEntity(object);
        if (entity == null) {
            throw new GrailsConfigurationException("Could not retrieve the respective entity for domain " +
                    object.getClass().getName() + " in the mapping context API");
        }
        return entity;
    }

    /**
     * A copy of an association's collection, which loads it and keeps its order.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Object copied(Object value) {
        if (value instanceof SortedMap map) {
            return new TreeMap(map);
        }
        if (value instanceof SortedSet set) {
            return new TreeSet(set);
        }
        if (value instanceof Set set) {
            return new LinkedHashSet(set);
        }
        if (value instanceof Map map) {
            return new LinkedHashMap(map);
        }
        if (value instanceof Collection collection) {
            return new ArrayList(collection);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Set<Object> renderingInstances(SerializationContext ctxt) {
        Set<Object> instances = (Set<Object>) ctxt.getAttribute(RENDERING_INSTANCES);
        if (instances == null) {
            instances = Collections.newSetFromMap(new IdentityHashMap<>());
            ctxt.setAttribute(RENDERING_INSTANCES, instances);
        }
        return instances;
    }
}
