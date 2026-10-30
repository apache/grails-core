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
package org.grails.web.json;

import java.util.Iterator;
import java.util.Set;
import java.util.function.Predicate;

import com.fasterxml.jackson.annotation.JsonFormat;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.TokenStreamContext;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import tools.jackson.databind.jsontype.TypeSerializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.PropertyWriter;
import tools.jackson.databind.ser.ValueSerializerModifier;
import tools.jackson.databind.ser.impl.PropertySerializerMap;
import tools.jackson.databind.ser.std.DelegatingSerializer;
import tools.jackson.databind.ser.std.StdContainerSerializer;
import tools.jackson.databind.type.ArrayType;
import tools.jackson.databind.type.CollectionLikeType;
import tools.jackson.databind.type.CollectionType;
import tools.jackson.databind.type.MapLikeType;
import tools.jackson.databind.type.MapType;
import tools.jackson.databind.util.ClassUtil;
import tools.jackson.databind.util.NameTransformer;

/**
 * Wraps every serializer of the mapper that a {@link JsonMapperSupport} writes with, so that a value nested in a value
 * the mapper writes, such as a property that a custom serializer writes with {@link JsonGenerator#writePOJO(Object)},
 * is offered to the nested value writer the outer value is written with. {@code grails.converters.JSON} renders such a
 * value as it renders any other value. A value the writer declines, and any value written without a nested value
 * writer, is written by the serializer it wraps.
 */
final class NestedValueSerializer extends DelegatingSerializer {

    /** The attribute of the serialization context that holds the {@link Frame} of the value being written. */
    private static final String FRAME = NestedValueSerializer.class.getName() + ".frame";

    /** The attribute of the serialization context that holds the {@link HandedBack} value the nested value writer writes. */
    private static final String HANDED_BACK = NestedValueSerializer.class.getName() + ".handedBack";

    /** The property the values are written for, or {@code null} for values that are not the values of a property. */
    private final BeanProperty property;

    /** The property's serializers of the instances written in the place of a proxy, found once for each class. */
    private volatile PropertySerializerMap standInSerializers = PropertySerializerMap.emptyForProperties();

    NestedValueSerializer(ValueSerializer<?> delegatee) {
        this(delegatee, null);
    }

    private NestedValueSerializer(ValueSerializer<?> delegatee, BeanProperty property) {
        super(delegatee);
        this.property = property;
    }

    /**
     * @return a module that wraps every value serializer of a mapper
     */
    static SimpleModule module() {
        return new SimpleModule("grails-nested-values").setSerializerModifier(new Modifier());
    }

    /**
     * Writes a value with the generator's mapper, offering the values nested in it to a nested value writer.
     *
     * @param generator the generator
     * @param value the value
     * @param nestedValueWriter writes a nested value and returns {@code true}, or returns {@code false} for the mapper to
     *        write it
     */
    static void write(JsonGenerator generator, Object value, Predicate<Object> nestedValueWriter) {
        // The frame belongs to the write: its context is the one every serializer of this value is given
        SerializationContext context = context(generator);
        Object previous = context.getAttribute(FRAME);
        Object handedBack = context.getAttribute(HANDED_BACK);
        Frame frame = new Frame(generator, value, nestedValueWriter);
        context.setAttribute(FRAME, frame);
        context.setAttribute(HANDED_BACK, null);
        try {
            if (handedBack instanceof HandedBack nested && nested.generator == generator && nested.value == value) {
                // A nested value the nested value writer writes with the mapper again is written by the serializer of the
                // property it is the value of, so that what the property is annotated with applies to it
                frame.rootWritten = true;
                nested.write(context);
            }
            else if (handedBack instanceof HandedBack nested && nested.generator == generator && value != null &&
                    nested.standIn == value) {
                // The instance it writes in the place of a proxy is written as the mapper writes the instance a proxy stands
                // for: by the property's serializer for its class, whose wrappers take it as the value being written
                nested.writeStandIn(context);
            }
            else {
                generator.writePOJO(value);
            }
        }
        finally {
            context.setAttribute(FRAME, previous);
            context.setAttribute(HANDED_BACK, handedBack);
        }
    }

    /**
     * Records that the nested value writer writes, in the place of the nested value it was handed, the instance that value
     * stands for, such as the instance a proxy proxies, so that the instance is written as the mapper writes it in the
     * place of the proxy.
     *
     * @param generator the generator
     * @param value the nested value the nested value writer was handed
     * @param instance the instance it writes in its place
     */
    static void standIn(JsonGenerator generator, Object value, Object instance) {
        if (generator.objectWriteContext() instanceof SerializationContext context &&
                context.getAttribute(HANDED_BACK) instanceof HandedBack nested && nested.generator == generator &&
                nested.value == value) {
            context.setAttribute(HANDED_BACK, nested.withStandIn(instance));
        }
    }

    private static SerializationContext context(JsonGenerator generator) {
        if (generator.objectWriteContext() instanceof SerializationContext context) {
            return context;
        }
        throw new IllegalArgumentException("The generator was not created by a mapper's writer");
    }

    /**
     * The location, relative to the value being written with a nested value writer, of the nested value the generator
     * is about to write, such as {@code .inner} or {@code [0]}.
     *
     * @param generator the generator
     * @return the location, or an empty string when nothing is being written with a nested value writer
     */
    static String nestedPath(JsonGenerator generator) {
        Frame frame = generator.objectWriteContext() instanceof SerializationContext context ?
                (Frame) context.getAttribute(FRAME) : null;
        if (frame == null || frame.generator != generator) {
            return "";
        }
        StringBuilder path = new StringBuilder();
        boolean upcoming = true;
        for (TokenStreamContext context = generator.streamWriteContext(); context != null && context != frame.start;
                context = context.getParent()) {
            if (context.inObject()) {
                path.insert(0, "." + context.currentName());
            }
            else if (context.inArray()) {
                // the innermost array has not counted the value it is about to be given yet
                path.insert(0, "[" + (context.getEntryCount() - (upcoming ? 0 : 1)) + "]");
            }
            upcoming = false;
        }
        return path.toString();
    }

    @Override
    protected ValueSerializer<Object> newDelegatingInstance(ValueSerializer<?> newDelegatee) {
        return new NestedValueSerializer(newDelegatee, property);
    }

    /** The property is kept, so that the instance a proxy in it stands for is written with the serializer of the property. */
    @Override
    public ValueSerializer<?> createContextual(SerializationContext context, BeanProperty property) {
        ValueSerializer<?> contextual = context.handleSecondaryContextualization(_delegatee, property);
        return contextual == _delegatee && property == this.property ? this : new NestedValueSerializer(contextual, property);
    }

    /**
     * An unwrapped value is written into the object that encloses it, as the property's serializer writes it: the nested
     * value writer writes a complete value, which cannot be written there. A serializer that cannot unwrap its value
     * writes it under the name of the property, as a complete value, so it stays wrapped.
     */
    @Override
    public ValueSerializer<Object> unwrappingSerializer(NameTransformer unwrapper) {
        ValueSerializer<Object> unwrapping = _delegatee.unwrappingSerializer(unwrapper);
        return unwrapping == _delegatee ? this : unwrapping;
    }

    /**
     * A serializer that does not write the shape a property's {@code @JsonFormat} asks for gives none, so that Jackson finds
     * one that does, as it does for its own serializers.
     */
    @Override
    public ValueSerializer<?> withFormatOverrides(SerializationConfig config, JsonFormat.Value formatOverrides) {
        ValueSerializer<?> overridden = _delegatee.withFormatOverrides(config, formatOverrides);
        return overridden == null ? null : _newIfChanged(overridden);
    }

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializationContext ctxt) {
        if (!writtenAsNested(value, gen, ctxt, null)) {
            _delegatee.serialize(value, gen, ctxt);
        }
    }

    @Override
    public void serializeWithType(Object value, JsonGenerator gen, SerializationContext ctxt, TypeSerializer typeSer) {
        if (!writtenAsNested(value, gen, ctxt, typeSer)) {
            _delegatee.serializeWithType(value, gen, ctxt, typeSer);
        }
    }

    private boolean writtenAsNested(Object value, JsonGenerator generator, SerializationContext context,
            TypeSerializer typeSerializer) {
        Frame frame = (Frame) context.getAttribute(FRAME);
        if (frame == null || frame.generator != generator) {
            return false;
        }
        if (!frame.rootWritten && value == frame.root) {
            frame.rootWritten = true;
            return false;
        }
        Object previous = context.getAttribute(HANDED_BACK);
        context.setAttribute(HANDED_BACK, new HandedBack(generator, value, _delegatee, typeSerializer, this, null));
        try {
            return frame.nestedValueWriter.test(value);
        }
        finally {
            context.setAttribute(HANDED_BACK, previous);
        }
    }

    private ValueSerializer<Object> standInSerializer(Class<?> type, SerializationContext context) {
        PropertySerializerMap found = standInSerializers;
        ValueSerializer<Object> serializer = found.serializerFor(type);
        if (serializer == null) {
            serializer = context.findPrimaryPropertySerializer(type, property);
            standInSerializers = found.newWith(type, serializer);
        }
        return serializer;
    }

    /**
     * A nested value handed to the nested value writer, with the serializer of the property it is the value of, the
     * nested value serializer that handed it, and the instance the nested value writer writes in its place, if it stands
     * for one.
     */
    private record HandedBack(JsonGenerator generator, Object value, ValueSerializer<Object> serializer,
            TypeSerializer typeSerializer, NestedValueSerializer handedBy, Object standIn) {

        HandedBack withStandIn(Object instance) {
            return new HandedBack(generator, value, serializer, typeSerializer, handedBy, instance);
        }

        void write(SerializationContext context) {
            write(serializer, value, context);
        }

        void writeStandIn(SerializationContext context) {
            write(handedBy.standInSerializer(standIn.getClass(), context), standIn, context);
        }

        private void write(ValueSerializer<Object> written, Object instance, SerializationContext context) {
            if (typeSerializer == null) {
                written.serialize(instance, generator, context);
            }
            else {
                written.serializeWithType(instance, generator, context, typeSerializer);
            }
        }
    }

    private static final class Frame {

        private final JsonGenerator generator;

        private final TokenStreamContext start;

        private final Object root;

        private final Predicate<Object> nestedValueWriter;

        private boolean rootWritten;

        private Frame(JsonGenerator generator, Object root, Predicate<Object> nestedValueWriter) {
            this.generator = generator;
            this.start = generator.streamWriteContext();
            this.root = root;
            this.nestedValueWriter = nestedValueWriter;
        }
    }

    private static final class Modifier extends ValueSerializerModifier {

        private static final long serialVersionUID = 1L;

        /** The types whose values Jackson writes as natural JSON values, without a type id of their own. */
        private static final Set<Class<?>> NATURAL_TYPES = Set.of(String.class, Integer.class, Boolean.class, Double.class,
                int.class, boolean.class, double.class);

        @Override
        public ValueSerializer<?> modifySerializer(SerializationConfig config, BeanDescription.Supplier beanDesc,
                ValueSerializer<?> serializer) {
            // Jackson's own serializer of a natural JSON type is left as it is: the nested value writer writes such a value
            // as the mapper does, and Jackson writes the type id of a @JsonValue that gives one only with that serializer
            if (NATURAL_TYPES.contains(beanDesc.getBeanClass()) && ClassUtil.isJacksonStdImpl(serializer)) {
                return serializer;
            }
            return wrap(serializer);
        }

        @Override
        public ValueSerializer<?> modifyArraySerializer(SerializationConfig config, ArrayType valueType,
                BeanDescription.Supplier beanDesc, ValueSerializer<?> serializer) {
            return wrap(serializer);
        }

        @Override
        public ValueSerializer<?> modifyCollectionSerializer(SerializationConfig config, CollectionType valueType,
                BeanDescription.Supplier beanDesc, ValueSerializer<?> serializer) {
            return wrap(serializer);
        }

        @Override
        public ValueSerializer<?> modifyCollectionLikeSerializer(SerializationConfig config,
                CollectionLikeType valueType, BeanDescription.Supplier beanDesc, ValueSerializer<?> serializer) {
            return wrap(serializer);
        }

        @Override
        public ValueSerializer<?> modifyMapSerializer(SerializationConfig config, MapType valueType,
                BeanDescription.Supplier beanDesc, ValueSerializer<?> serializer) {
            return wrap(serializer);
        }

        @Override
        public ValueSerializer<?> modifyMapLikeSerializer(SerializationConfig config, MapLikeType valueType,
                BeanDescription.Supplier beanDesc, ValueSerializer<?> serializer) {
            return wrap(serializer);
        }

        @Override
        public ValueSerializer<?> modifyEnumSerializer(SerializationConfig config, JavaType valueType,
                BeanDescription.Supplier beanDesc, ValueSerializer<?> serializer) {
            return wrap(serializer);
        }

        private static ValueSerializer<?> wrap(ValueSerializer<?> serializer) {
            return serializer instanceof NestedValueSerializer || serializer instanceof Container ? serializer :
                    Container.of(new NestedValueSerializer(serializer));
        }
    }

    /**
     * The {@link NestedValueSerializer} of a collection, an array or a map, which Jackson takes for the container
     * serializer it wraps: Jackson gives the serializer of a property's value the type serializer that the property's
     * {@code @JsonTypeInfo} asks for the elements only when that serializer is a {@link StdContainerSerializer}, and the
     * elements would otherwise be written without their type ids.
     */
    private static final class Container extends StdContainerSerializer<Object> {

        private final NestedValueSerializer nested;

        private final StdContainerSerializer<?> container;

        private Container(NestedValueSerializer nested, StdContainerSerializer<?> container) {
            super(container);
            this.nested = nested;
            this.container = container;
        }

        /**
         * @return the serializer, as a {@code Container} if it is a {@link NestedValueSerializer} of a container serializer
         */
        static ValueSerializer<?> of(ValueSerializer<?> serializer) {
            return serializer instanceof NestedValueSerializer nested &&
                    nested._delegatee instanceof StdContainerSerializer<?> container ? new Container(nested, container) :
                    serializer;
        }

        private ValueSerializer<?> rewrapped(ValueSerializer<?> serializer) {
            return serializer == nested ? this : of(serializer);
        }

        @Override
        protected StdContainerSerializer<?> _withValueTypeSerializer(TypeSerializer valueTypeSerializer) {
            StdContainerSerializer<?> typed = container.withValueTypeSerializer(valueTypeSerializer);
            return typed == container ? this :
                    new Container((NestedValueSerializer) nested.replaceDelegatee(typed), typed);
        }

        @Override
        public JavaType getContentType() {
            return container.getContentType();
        }

        @Override
        public ValueSerializer<?> getContentSerializer() {
            return container.getContentSerializer();
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        @Override
        public boolean hasSingleElement(Object value) {
            return ((StdContainerSerializer) container).hasSingleElement(value);
        }

        @Override
        public boolean isEmpty(SerializationContext context, Object value) {
            return nested.isEmpty(context, value);
        }

        @Override
        public void serialize(Object value, JsonGenerator generator, SerializationContext context) {
            nested.serialize(value, generator, context);
        }

        @Override
        public void serializeWithType(Object value, JsonGenerator generator, SerializationContext context,
                TypeSerializer typeSerializer) {
            nested.serializeWithType(value, generator, context, typeSerializer);
        }

        @Override
        public void resolve(SerializationContext context) {
            nested.resolve(context);
        }

        @Override
        public ValueSerializer<?> createContextual(SerializationContext context, BeanProperty property) {
            return rewrapped(nested.createContextual(context, property));
        }

        @SuppressWarnings("unchecked")
        @Override
        public ValueSerializer<Object> unwrappingSerializer(NameTransformer unwrapper) {
            return (ValueSerializer<Object>) rewrapped(nested.unwrappingSerializer(unwrapper));
        }

        @SuppressWarnings("unchecked")
        @Override
        public ValueSerializer<Object> replaceDelegatee(ValueSerializer<?> delegatee) {
            return (ValueSerializer<Object>) rewrapped(nested.replaceDelegatee(delegatee));
        }

        @Override
        public ValueSerializer<?> withFilterId(Object filterId) {
            return rewrapped(nested.withFilterId(filterId));
        }

        @Override
        public ValueSerializer<?> withIgnoredProperties(Set<String> ignoredProperties) {
            return rewrapped(nested.withIgnoredProperties(ignoredProperties));
        }

        @Override
        public ValueSerializer<?> withFormatOverrides(SerializationConfig config, JsonFormat.Value formatOverrides) {
            return rewrapped(nested.withFormatOverrides(config, formatOverrides));
        }

        @Override
        public Class<?> handledType() {
            return nested.handledType();
        }

        @Override
        public boolean usesObjectId() {
            return nested.usesObjectId();
        }

        @Override
        public boolean isUnwrappingSerializer() {
            return nested.isUnwrappingSerializer();
        }

        @Override
        public ValueSerializer<?> getDelegatee() {
            return nested.getDelegatee();
        }

        @Override
        public Iterator<PropertyWriter> properties() {
            return nested.properties();
        }

        @Override
        public void acceptJsonFormatVisitor(JsonFormatVisitorWrapper visitor, JavaType type) {
            nested.acceptJsonFormatVisitor(visitor, type);
        }
    }
}
