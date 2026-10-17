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

import java.util.function.Predicate;

import com.fasterxml.jackson.annotation.JsonFormat;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.TokenStreamContext;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.jsontype.TypeSerializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.ValueSerializerModifier;
import tools.jackson.databind.ser.std.DelegatingSerializer;
import tools.jackson.databind.type.ArrayType;
import tools.jackson.databind.type.CollectionLikeType;
import tools.jackson.databind.type.CollectionType;
import tools.jackson.databind.type.MapLikeType;
import tools.jackson.databind.type.MapType;
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

    NestedValueSerializer(ValueSerializer<?> delegatee) {
        super(delegatee);
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
            if (handedBack instanceof HandedBack nested && nested.value == value && nested.generator == generator) {
                // A nested value the nested value writer writes with the mapper again is written by the serializer of the
                // property it is the value of, so that what the property is annotated with applies to it
                frame.rootWritten = true;
                nested.write(context);
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
        return new NestedValueSerializer(newDelegatee);
    }

    /**
     * An unwrapped value is written into the object that encloses it, as the property's serializer writes it: the nested
     * value writer writes a complete value, which cannot be written there.
     */
    @Override
    public ValueSerializer<Object> unwrappingSerializer(NameTransformer unwrapper) {
        return _delegatee.unwrappingSerializer(unwrapper);
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
        context.setAttribute(HANDED_BACK, new HandedBack(generator, value, _delegatee, typeSerializer));
        try {
            return frame.nestedValueWriter.test(value);
        }
        finally {
            context.setAttribute(HANDED_BACK, previous);
        }
    }

    /** A nested value handed to the nested value writer, with the serializer of the property it is the value of. */
    private record HandedBack(JsonGenerator generator, Object value, ValueSerializer<Object> serializer,
            TypeSerializer typeSerializer) {

        void write(SerializationContext context) {
            if (typeSerializer == null) {
                serializer.serialize(value, generator, context);
            }
            else {
                serializer.serializeWithType(value, generator, context, typeSerializer);
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

        @Override
        public ValueSerializer<?> modifySerializer(SerializationConfig config, BeanDescription.Supplier beanDesc,
                ValueSerializer<?> serializer) {
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
            return serializer instanceof NestedValueSerializer ? serializer : new NestedValueSerializer(serializer);
        }
    }
}
