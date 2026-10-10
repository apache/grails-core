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
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonToken;
import tools.jackson.core.type.WritableTypeId;
import tools.jackson.databind.JacksonSerializable;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.introspect.BeanPropertyDefinition;
import tools.jackson.databind.jsontype.TypeSerializer;
import tools.jackson.databind.ser.std.StdSerializer;
import tools.jackson.databind.util.NameTransformer;

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
 * <p>Written by a mapper, rather than by the JSON converter, an instance's property names are those Jackson gives the
 * class's properties, so the mapper's naming strategy, {@code @JsonNaming} and {@code @JsonProperty} names apply. The
 * properties are written in the converter's order, whether or not the mapper sorts properties. The serializer writes
 * the type id of a class with {@code @JsonTypeInfo}, and can write an instance's properties into an enclosing object,
 * for a property annotated with {@code @JsonUnwrapped}.
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

    private final NameTransformer unwrapper;

    private final Map<Class<?>, Names> namesByType = new ConcurrentHashMap<>();

    /**
     * @param rendering how to render domain class instances
     */
    public DomainClassSerializer(DomainClassRendering rendering) {
        this(rendering, null);
    }

    private DomainClassSerializer(DomainClassRendering rendering, NameTransformer unwrapper) {
        super(Object.class);
        this.rendering = rendering;
        this.unwrapper = unwrapper;
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
    public ValueSerializer<Object> unwrappingSerializer(NameTransformer nameTransformer) {
        return new DomainClassSerializer(rendering,
                unwrapper == null ? nameTransformer : NameTransformer.chainedTransformer(nameTransformer, unwrapper));
    }

    @Override
    public boolean isUnwrappingSerializer() {
        return unwrapper != null;
    }

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializationContext ctxt) {
        Object object = rendering.unwrap(value);
        Naming naming = naming(ctxt);
        Set<Object> instances = renderingInstances(ctxt);
        if (!instances.add(object)) {
            // rendering deep, an instance already being written further up is a reference to it
            if (unwrapper == null) {
                writeReference(object, entity(object, rendering), gen, rendering, naming);
            }
            else {
                writeReferenceProperties(object, entity(object, rendering), gen, rendering, naming, unwrapper);
            }
            return;
        }
        try {
            if (unwrapper == null) {
                gen.writeStartObject(object);
            }
            writeProperties(object, gen, ctxt, rendering, naming, unwrapper);
            if (unwrapper == null) {
                gen.writeEndObject();
            }
        }
        finally {
            instances.remove(object);
        }
    }

    @Override
    public void serializeWithType(Object value, JsonGenerator gen, SerializationContext ctxt, TypeSerializer typeSer) {
        if (unwrapper != null) {
            // as Jackson's UnwrappingBeanSerializer: properties written into an enclosing object have no place for a type id
            if (ctxt.isEnabled(SerializationFeature.FAIL_ON_UNWRAPPED_TYPE_IDENTIFIERS)) {
                ctxt.reportBadDefinition(value.getClass(), "Unwrapped property requires use of type information: cannot " +
                        "serialize without disabling `SerializationFeature.FAIL_ON_UNWRAPPED_TYPE_IDENTIFIERS`");
            }
            serialize(value, gen, ctxt);
            return;
        }
        Object object = rendering.unwrap(value);
        PersistentEntity entity = entity(object, rendering);
        Naming naming = naming(ctxt);
        Set<Object> instances = renderingInstances(ctxt);
        boolean reference = !instances.add(object);
        if (reference && rendering.writeReference(object, entity.getIdentity(), entity)) {
            return;
        }
        WritableTypeId typeId = typeSer.writeTypePrefix(gen, ctxt, typeSer.typeId(object, JsonToken.START_OBJECT));
        if (reference) {
            writeReferenceProperties(object, entity, gen, rendering, naming, null);
        }
        else {
            try {
                writeProperties(object, gen, ctxt, rendering, naming, null);
            }
            finally {
                instances.remove(object);
            }
        }
        typeSer.writeTypeSuffix(gen, ctxt, typeId);
    }

    private static void write(Object value, JsonGenerator gen, SerializationContext ctxt,
            DomainClassRendering rendering) {
        Object object = rendering.unwrap(value);
        gen.writeStartObject(object);
        writeProperties(object, gen, ctxt, rendering, Naming.AS_THEY_ARE, null);
        gen.writeEndObject();
    }

    private static void writeProperties(Object object, JsonGenerator gen, SerializationContext ctxt,
            DomainClassRendering rendering, Naming naming, NameTransformer unwrapper) {
        PersistentEntity entity = entity(object, rendering);
        Names names = naming.of(object.getClass());
        Set<String> ignored = IGNORED_PROPERTIES.get(object.getClass());
        List<WrittenProperty> fields = new ArrayList<>();
        if (rendering.isIncludeClass() && rendering.includes(object, "class")) {
            fields.add(new WrittenProperty("class", object.getClass().getName(), null, true));
        }
        // a composite key has no identity, and is not written
        PersistentProperty identity = entity.getIdentity();
        if (identity != null && !ignored.contains(identity.getName()) && rendering.includes(object, identity.getName())) {
            Object id = rendering.propertyValue(object, identity);
            if (id != null) {
                fields.add(new WrittenProperty(identity.getName(), id, null, false));
            }
        }
        if (rendering.isIncludeVersion() && !ignored.contains(GormProperties.VERSION) &&
                rendering.includes(object, GormProperties.VERSION)) {
            Object version = rendering.propertyValue(object, entity.getVersion());
            if (version != null) {
                fields.add(new WrittenProperty(GormProperties.VERSION, version, null, false));
            }
        }
        BeanWrapper bean = new BeanWrapperImpl(object);
        for (PersistentProperty property : entity.getPersistentProperties()) {
            if (property.equals(entity.getVersion()) || ignored.contains(property.getName()) ||
                    !rendering.includes(object, property.getName())) {
                continue;
            }
            fields.add(new WrittenProperty(property.getName(), bean.getPropertyValue(property.getName()),
                    property instanceof Association<?> association ? association : null, false));
        }
        for (WrittenProperty field : fields) {
            gen.writeName(transformed(names.name(field.name()), unwrapper));
            if (field.association() != null && field.value() != null) {
                writeAssociation(field.association(), field.value(), gen, ctxt, rendering, naming);
            }
            else if (field.className()) {
                gen.writeString((String) field.value());
            }
            else {
                gen.writePOJO(field.value());
            }
        }
    }

    /**
     * The names the mapper writes the properties of instances and references with: those Jackson gives each class's
     * properties.
     */
    private Naming naming(SerializationContext ctxt) {
        return type -> namesByType.computeIfAbsent(type, t -> {
            Map<String, String> external = new HashMap<>();
            for (BeanPropertyDefinition property : ctxt.introspectBeanDescription(ctxt.constructType(t)).findProperties()) {
                external.put(property.getInternalName(), property.getName());
            }
            return new Names(external);
        });
    }

    private static String transformed(String name, NameTransformer unwrapper) {
        return unwrapper != null ? unwrapper.transform(name) : name;
    }

    private static void writeAssociation(Association<?> association, Object value, JsonGenerator gen,
            SerializationContext ctxt, DomainClassRendering rendering, Naming naming) {
        PersistentEntity associated = association.getAssociatedEntity();
        if (rendering.isDeep()) {
            gen.writePOJO(copied(rendering.unwrap(value)));
        }
        else if (associated == null || association.isEmbedded() || association.getType().isEnum()) {
            gen.writePOJO(value);
        }
        else if (association instanceof OneToOne || association instanceof ManyToOne) {
            writeReference(value, associated, gen, rendering, naming);
        }
        else if (value instanceof Collection<?> references) {
            gen.writeStartArray();
            for (Object reference : references) {
                writeReference(reference, associated, gen, rendering, naming);
            }
            gen.writeEndArray();
        }
        else if (value instanceof Map<?, ?> references) {
            gen.writeStartObject();
            for (Map.Entry<?, ?> entry : references.entrySet()) {
                writeKey(entry.getKey(), gen, ctxt);
                writeReference(entry.getValue(), associated, gen, rendering, naming);
            }
            gen.writeEndObject();
        }
        else {
            gen.writePOJO(value);
        }
    }

    private static void writeReference(Object reference, PersistentEntity entity, JsonGenerator gen,
            DomainClassRendering rendering, Naming naming) {
        if (reference == null) {
            gen.writeNull();
            return;
        }
        if (rendering.writeReference(reference, entity.getIdentity(), entity)) {
            return;
        }
        gen.writeStartObject();
        writeReferenceProperties(reference, entity, gen, rendering, naming, null);
        gen.writeEndObject();
    }

    /**
     * Writes the class name and id of a reference, named as the referenced domain class names them.
     */
    private static void writeReferenceProperties(Object reference, PersistentEntity entity, JsonGenerator gen,
            DomainClassRendering rendering, Naming naming, NameTransformer unwrapper) {
        Names names = naming.of(entity.getJavaClass());
        Object id = rendering.referenceId(reference, entity.getIdentity());
        if (rendering.isIncludeClass()) {
            gen.writeStringProperty(transformed(names.name("class"), unwrapper), entity.getName());
        }
        if (id != null) {
            gen.writeName(transformed(names.name("id"), unwrapper));
            // DomainClassMarshaller wrote the id of a reference with the converter's writer, which quotes the string of
            // an id that is not a number, such as a MongoDB ObjectId; the id of an instance itself is written as any
            // other value, by the mapper, as the converter rendered it in Grails 8 as well
            if (id instanceof Number || id instanceof Boolean) {
                gen.writePOJO(id);
            }
            else {
                gen.writeString(id.toString());
            }
        }
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

    /**
     * A property of an instance to write: its name, its value, its association, if it is one, and whether it is the
     * class name, which is written as it is.
     */
    private record WrittenProperty(String name, Object value, Association<?> association, boolean className) {
    }

    /**
     * Finds the names to write the properties of a class with.
     */
    @FunctionalInterface
    private interface Naming {

        /**
         * The property names as they are, as the JSON converter writes them.
         */
        Naming AS_THEY_ARE = type -> Names.AS_THEY_ARE;

        Names of(Class<?> type);
    }

    /**
     * The names to write a class's properties with: those Jackson gives them, or the property names as they are.
     */
    private static final class Names {

        private static final Names AS_THEY_ARE = new Names(Map.of());

        private final Map<String, String> external;

        private Names(Map<String, String> external) {
            this.external = external;
        }

        private String name(String property) {
            return external.getOrDefault(property, property);
        }
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
