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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.introspect.BeanPropertyDefinition;
import tools.jackson.databind.jsontype.TypeSerializer;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.bean.BeanSerializerBase;
import tools.jackson.databind.ser.impl.PropertySerializerMap;
import tools.jackson.databind.ser.std.StdContainerSerializer;
import tools.jackson.databind.util.ClassUtil;

import org.grails.datastore.mapping.model.PersistentEntity;

/**
 * Writes the references to associated domain class instances: an object of the class name, when it is included, and the
 * id, named as the referenced class names them for the mapper, by its naming strategy, {@code @JsonProperty} and
 * {@code @JsonNaming}. An associated instance of a class with a serializer of its own is written with it.
 *
 * @since 9.0
 */
final class DomainReferences {

    private final Map<Class<?>, String> idNames = new ConcurrentHashMap<>();

    private final Map<Class<?>, String> classNames = new ConcurrentHashMap<>();

    private final Map<Class<?>, Boolean> ownSerializers = new ConcurrentHashMap<>();

    /**
     * Writes an associated instance that is not written in full: a reference to it, or what the rendering writes in its
     * place, unless its class has a serializer of its own, such as one with a {@code @JsonValue}, one annotated
     * {@code @JsonSerialize} or one a module has a serializer for, which writes it as Jackson writes the value of the
     * property, or an element of it, with what the property is annotated with, such as {@code @JsonFormat}, and the
     * type id the type it is declared with asks for.
     */
    private void writeAssociated(Object associated, PersistentEntity entity, JsonGenerator generator,
            SerializationContext context, DomainClassRendering rendering, ReferenceSerializer serializers,
            TypeSerializer typeSerializer) {
        if (associated == null) {
            generator.writeNull();
            return;
        }
        if (rendering.writeReference(associated, entity.getIdentity(), entity)) {
            return;
        }
        if (hasSerializerOfItsOwn(associated, entity, context)) {
            Object written = writtenInstead(associated, rendering);
            if (written == null) {
                // a proxy of an instance that no longer exists
                generator.writeNull();
            }
            else if (typeSerializer != null) {
                serializers.valueSerializer(written, context).serializeWithType(written, generator, context, typeSerializer);
            }
            else {
                serializers.valueSerializer(written, context).serialize(written, generator, context);
            }
            return;
        }
        writeReference(associated, entity, generator, context, rendering);
    }

    /**
     * Whether an associated instance that is not written in full is empty, as the serializer of its class, if it has one
     * of its own, has it, for a property that leaves out an empty value: a reference, or what the rendering writes in its
     * place, never is.
     */
    private boolean isEmpty(Object associated, PersistentEntity entity, SerializationContext context,
            DomainClassRendering rendering, ReferenceSerializer serializers) {
        if (rendering.writesReferences() || !hasSerializerOfItsOwn(associated, entity, context)) {
            return false;
        }
        Object written = writtenInstead(associated, rendering);
        return written == null || serializers.valueSerializer(written, context).isEmpty(context, written);
    }

    private boolean hasSerializerOfItsOwn(Object associated, PersistentEntity entity, SerializationContext context) {
        return hasSerializerOfItsOwn(DomainClassRendering.isProxyClass(associated.getClass()) ?
                entity.getJavaClass() : associated.getClass(), context);
    }

    /** The instance a proxy stands for, which is {@code null} when it no longer exists, or else the instance itself. */
    private static Object writtenInstead(Object associated, DomainClassRendering rendering) {
        return DomainClassRendering.isProxyClass(associated.getClass()) ? rendering.unwrap(associated) : associated;
    }

    /**
     * Writes a reference to an instance, such as one reached again while it is being written, or what the rendering
     * writes in its place.
     */
    void write(Object reference, PersistentEntity entity, JsonGenerator generator, SerializationContext context,
            DomainClassRendering rendering) {
        if (reference == null) {
            generator.writeNull();
            return;
        }
        if (rendering.writeReference(reference, entity.getIdentity(), entity)) {
            return;
        }
        writeReference(reference, entity, generator, context, rendering);
    }

    private void writeReference(Object reference, PersistentEntity entity, JsonGenerator generator,
            SerializationContext context, DomainClassRendering rendering) {
        Object id = rendering.referenceId(reference, entity.getIdentity());
        generator.writeStartObject();
        if (rendering.isIncludeClass()) {
            generator.writeStringProperty(name(classNames, context, entity.getJavaClass(), "class"), entity.getName());
        }
        if (id != null) {
            generator.writeName(name(idNames, context, entity.getJavaClass(), "id"));
            // DomainClassMarshaller wrote the id of a reference with the converter's writer, which quotes the string of
            // an id that is not a number, such as a MongoDB ObjectId; the id of an instance itself is written as any
            // other value, by the mapper, as the converter rendered it in Grails 8 as well
            if (id instanceof Number || id instanceof Boolean) {
                context.writeValue(generator, id);
            }
            else {
                generator.writeString(id.toString());
            }
        }
        generator.writeEndObject();
    }

    /**
     * Whether Jackson writes the instances of a domain class with a serializer other than its bean serializer, inside
     * the serializers modules wrap it with. A class whose serializer Jackson cannot build, such as one that unwraps a
     * property with the name of one of its own, or one a module fails to build a serializer of a property of, has none: its instances are referenced, which needs none of their
     * properties, rather than failing the write of every instance that references one.
     */
    private boolean hasSerializerOfItsOwn(Class<?> type, SerializationContext context) {
        // Jackson does not keep a serializer it cannot build, and would try again for every reference
        return ownSerializers.computeIfAbsent(type, t -> {
            try {
                return findsSerializerOfItsOwn(t, context);
            }
            catch (DatabindException e) {
                // a definition Jackson rejects, or a mapping problem, such as a module failing to build a serializer of
                // one of its properties
                return false;
            }
        });
    }

    private static boolean findsSerializerOfItsOwn(Class<?> type, SerializationContext context) {
        ValueSerializer<?> serializer = context.findValueSerializer(type);
        // a bean serializer of another class, such as the one a @JsonSerialize converter converts the instances to,
        // is not that of the domain class
        while (!(serializer instanceof BeanSerializerBase) || !serializer.handledType().isAssignableFrom(type)) {
            ValueSerializer<?> delegatee = serializer.getDelegatee();
            if (delegatee == null || delegatee == serializer) {
                return true;
            }
            serializer = delegatee;
        }
        return false;
    }

    private static String name(Map<Class<?>, String> cache, SerializationContext context, Class<?> type, String property) {
        return cache.computeIfAbsent(type, t -> {
            for (BeanPropertyDefinition definition : context.introspectBeanDescription(context.constructType(t)).findProperties()) {
                if (property.equals(definition.getInternalName())) {
                    return definition.getName();
                }
            }
            return property;
        });
    }

    /**
     * Writes an association as references, or in full when the rendering is deep, with the property of the association,
     * so that what it is annotated with, such as {@code @JsonIgnoreProperties}, applies to the instances, as it does to
     * any bean.
     */
    ValueSerializer<Object> serializer(PersistentEntity associated, boolean toMany, DomainRendering rendering,
            BeanProperty property) {
        return new ReferenceSerializer(this, associated, toMany, rendering, property);
    }

    private static final class ReferenceSerializer extends ValueSerializer<Object> {

        private final DomainReferences references;

        private final PersistentEntity associated;

        private final boolean toMany;

        private final DomainRendering rendering;

        private final BeanProperty property;

        private TypeSerializer elementTypes;

        private volatile boolean elementTypesFound;

        private volatile PropertySerializerMap valueSerializers = PropertySerializerMap.emptyForProperties();

        private volatile ValueSerializer<Object> containerSerializer;

        private ReferenceSerializer(DomainReferences references, PersistentEntity associated, boolean toMany,
                DomainRendering rendering, BeanProperty property) {
            this.references = references;
            this.associated = associated;
            this.toMany = toMany;
            this.rendering = rendering;
            this.property = property;
        }

        @Override
        public ValueSerializer<?> createContextual(SerializationContext context, BeanProperty property) {
            return property == this.property ? this : new ReferenceSerializer(references, associated, toMany, rendering, property);
        }

        @Override
        public void serialize(Object value, JsonGenerator generator, SerializationContext context) {
            DomainClassRendering current = rendering.ofOwner(context, generator);
            if (deep(current, context)) {
                Object unwrapped = current.unwrap(value);
                if (unwrapped == null) {
                    // a proxy of an instance that no longer exists
                    generator.writeNull();
                    return;
                }
                standIn(context, value, unwrapped);
                Object written = DomainWrites.copied(unwrapped);
                serializer(written, context).serialize(written, generator, context);
            }
            else if (!toMany) {
                references.writeAssociated(value, associated, generator, context, current, this, null);
            }
            else if (value instanceof Collection<?> collection) {
                generator.writeStartArray();
                TypeSerializer elementTypes = elementTypeSerializer(context);
                for (Object reference : collection) {
                    references.writeAssociated(reference, associated, generator, context, current, this, elementTypes);
                }
                generator.writeEndArray();
            }
            else if (value instanceof Map<?, ?> map) {
                generator.writeStartObject();
                TypeSerializer elementTypes = elementTypeSerializer(context);
                for (Map.Entry<?, ?> entry : ordered(map, context).entrySet()) {
                    writeKey(entry.getKey(), generator, context);
                    references.writeAssociated(entry.getValue(), associated, generator, context, current, this,
                            elementTypes);
                }
                generator.writeEndObject();
            }
            else {
                context.writeValue(generator, value);
            }
        }

        @Override
        public void serializeWithType(Object value, JsonGenerator generator, SerializationContext context,
                TypeSerializer typeSerializer) {
            DomainClassRendering current = rendering.ofOwner(context, generator);
            if (deep(current, context) && !toMany) {
                // In full, an instance is written with its type id; a reference carries none
                Object unwrapped = current.unwrap(value);
                if (unwrapped == null) {
                    generator.writeNull();
                    return;
                }
                standIn(context, value, unwrapped);
                Object written = DomainWrites.copied(unwrapped);
                valueSerializer(written, context).serializeWithType(written, generator, context, typeSerializer);
            }
            else if (!toMany) {
                references.writeAssociated(value, associated, generator, context, current, this, typeSerializer);
            }
            else {
                serialize(value, generator, context);
            }
        }

        /**
         * The type ids of the instances of a to-many association that are written with a serializer of their own, as
         * the property asks for them, with its {@code @JsonTypeInfo}, or else the type its elements are declared with.
         */
        private TypeSerializer elementTypeSerializer(SerializationContext context) {
            if (!elementTypesFound) {
                elementTypes = findElementTypeSerializer(context);
                elementTypesFound = true;
            }
            return elementTypes;
        }

        private TypeSerializer findElementTypeSerializer(SerializationContext context) {
            if (property == null || property.getType().getContentType() == null) {
                return null;
            }
            TypeSerializer named = elementTypeSerializerNamedByProperty();
            return named != null ? named : context.findTypeSerializer(property.getType().getContentType());
        }

        /**
         * The type serializer the property's {@code @JsonTypeInfo} names for its elements, which Jackson gives the type the
         * property is written as, or {@code null}.
         */
        private TypeSerializer elementTypeSerializerNamedByProperty() {
            if (property instanceof BeanPropertyWriter writer && writer.getSerializationType() != null &&
                    writer.getSerializationType().getContentType() != null &&
                    writer.getSerializationType().getContentType().getTypeHandler() instanceof TypeSerializer named) {
                return named;
            }
            return null;
        }

        /** Tells the writer of a proxy that is written in full as the instance it proxies, as the proxy serializer does. */
        @SuppressWarnings("unchecked")
        private static void standIn(SerializationContext context, Object value, Object unwrapped) {
            if (unwrapped != value && context.getAttribute(DomainClassJacksonModule.STAND_INS) instanceof BiConsumer<?, ?> standIns) {
                ((BiConsumer<Object, Object>) standIns).accept(value, unwrapped);
            }
        }

        /** Whether associated instances are written in full: for every write, or for this one. */
        private static boolean deep(DomainClassRendering rendering, SerializationContext context) {
            return rendering.isDeep() || Boolean.TRUE.equals(context.getAttribute(DomainClassJacksonModule.DEEP));
        }

        /**
         * The serializer of an association written in full: of the type it is declared with, for a collection or map, so
         * that its instances are written with the type ids their declared type, or the property's {@code @JsonTypeInfo},
         * asks for, as Jackson writes them.
         */
        private ValueSerializer<Object> serializer(Object written, SerializationContext context) {
            if (toMany && property != null && property.getType().getRawClass().isInstance(written)) {
                ValueSerializer<Object> found = containerSerializer;
                if (found == null) {
                    found = findContainerSerializer(context);
                    containerSerializer = found;
                }
                return found;
            }
            return valueSerializer(written, context);
        }

        /**
         * The serializer of the collection or map of a to-many association, given the type serializer the property names
         * for its elements, as Jackson gives it the serializer of any other property of a container type.
         */
        @SuppressWarnings("unchecked")
        private ValueSerializer<Object> findContainerSerializer(SerializationContext context) {
            ValueSerializer<Object> serializer = context.findPrimaryPropertySerializer(property.getType(), property);
            TypeSerializer named = elementTypeSerializerNamedByProperty();
            if (named != null && serializer instanceof StdContainerSerializer<?> container) {
                return (ValueSerializer<Object>) container.withValueTypeSerializer(named);
            }
            return serializer;
        }

        /**
         * The serializer Jackson writes an instance with as the value of the property, or as an element of it, found once
         * for each class, as Jackson's own writers find it.
         */
        private ValueSerializer<Object> valueSerializer(Object written, SerializationContext context) {
            PropertySerializerMap found = valueSerializers;
            ValueSerializer<Object> serializer = found.serializerFor(written.getClass());
            if (serializer == null) {
                PropertySerializerMap.SerializerAndMapResult result = toMany ?
                        found.findAndAddSecondarySerializer(written.getClass(), context, property) :
                        found.findAndAddPrimarySerializer(context.constructType(written.getClass()), context, property);
                valueSerializers = result.map;
                serializer = result.serializer;
            }
            return serializer;
        }

        @Override
        public boolean isEmpty(SerializationContext context, Object value) {
            if (value instanceof Collection<?> collection) {
                return collection.isEmpty();
            }
            if (value instanceof Map<?, ?> map) {
                return map.isEmpty();
            }
            if (value == null) {
                return true;
            }
            if (toMany) {
                return false;
            }
            // Jackson asks before it writes the name of the property, inside the object of the instance that has it
            JsonGenerator generator = context.getGenerator();
            DomainClassRendering current = generator != null ? rendering.ofOwner(context, generator) : rendering.get();
            if (deep(current, context)) {
                // in full, as the serializer it is written with has it; a proxy of an instance that no longer exists is
                // written as null
                Object unwrapped = current.unwrap(value);
                if (unwrapped == null) {
                    return true;
                }
                Object written = DomainWrites.copied(unwrapped);
                return valueSerializer(written, context).isEmpty(context, written);
            }
            return references.isEmpty(value, associated, context, current, this);
        }

        /**
         * The entries of a map association in the order Jackson writes those of a map: by their keys where
         * {@code ORDER_MAP_ENTRIES_BY_KEYS} is enabled and the keys are comparable, with an entry of a {@code null} key
         * first, and otherwise as the map has them, or failing as Jackson fails for keys that are not comparable where
         * {@code FAIL_ON_ORDER_MAP_BY_INCOMPARABLE_KEY} is enabled.
         */
        private static Map<?, ?> ordered(Map<?, ?> map, SerializationContext context) {
            if (!context.isEnabled(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS) || map instanceof SortedMap<?, ?> ||
                    map.isEmpty()) {
                return map;
            }
            Object firstKey = map.keySet().iterator().next();
            if (!(firstKey instanceof Comparable<?>)) {
                if (context.isEnabled(SerializationFeature.FAIL_ON_ORDER_MAP_BY_INCOMPARABLE_KEY)) {
                    context.reportBadDefinition(firstKey == null ? Object.class : firstKey.getClass(),
                            "Cannot order Map entries by key of incomparable type " + ClassUtil.classNameOf(firstKey) +
                                    ", consider disabling `SerializationFeature.FAIL_ON_ORDER_MAP_BY_INCOMPARABLE_KEY` to " +
                                    "simply skip sorting");
                }
                return map;
            }
            Map<Object, Object> ordered = new LinkedHashMap<>();
            if (map.containsKey(null)) {
                ordered.put(null, map.get(null));
            }
            TreeMap<Object, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    sorted.put(entry.getKey(), entry.getValue());
                }
            }
            ordered.putAll(sorted);
            return ordered;
        }

        /** Writes a key as Jackson writes the key of a map: a {@code null} one with its null key serializer, which fails by default. */
        private static void writeKey(Object key, JsonGenerator generator, SerializationContext context) {
            if (key == null) {
                context.findNullKeySerializer(context.constructType(Object.class), null).serialize(null, generator, context);
            }
            else if (key instanceof String name) {
                generator.writeName(name);
            }
            else {
                context.findKeySerializer(key.getClass(), null).serialize(key, generator, context);
            }
        }
    }
}
