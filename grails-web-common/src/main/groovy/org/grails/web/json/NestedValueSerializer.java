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

/**
 * Wraps every serializer of the mapper that a {@link JsonMapperSupport} writes with, so that a value nested in a value
 * the mapper writes, such as a property that a custom serializer writes with {@link JsonGenerator#writePOJO(Object)},
 * is offered to the nested value writer the outer value is written with. {@code grails.converters.JSON} renders such a
 * value as it renders any other value. A value the writer declines, and any value written without a nested value
 * writer, is written by the serializer it wraps.
 */
final class NestedValueSerializer extends DelegatingSerializer {

    private static final ThreadLocal<Frame> FRAME = new ThreadLocal<>();

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
        Frame previous = FRAME.get();
        FRAME.set(new Frame(generator, value, nestedValueWriter));
        try {
            generator.writePOJO(value);
        }
        finally {
            if (previous != null) {
                FRAME.set(previous);
            }
            else {
                FRAME.remove();
            }
        }
    }

    /**
     * The location, relative to the value being written with a nested value writer, of the nested value the generator
     * is about to write, such as {@code .inner} or {@code [0]}.
     *
     * @param generator the generator
     * @return the location, or an empty string when nothing is being written with a nested value writer
     */
    static String nestedPath(JsonGenerator generator) {
        Frame frame = FRAME.get();
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

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializationContext ctxt) {
        if (!writtenAsNested(value, gen)) {
            _delegatee.serialize(value, gen, ctxt);
        }
    }

    @Override
    public void serializeWithType(Object value, JsonGenerator gen, SerializationContext ctxt, TypeSerializer typeSer) {
        if (!writtenAsNested(value, gen)) {
            _delegatee.serializeWithType(value, gen, ctxt, typeSer);
        }
    }

    private static boolean writtenAsNested(Object value, JsonGenerator generator) {
        Frame frame = FRAME.get();
        if (frame == null || frame.generator != generator) {
            return false;
        }
        if (!frame.rootWritten && value == frame.root) {
            frame.rootWritten = true;
            return false;
        }
        return frame.nestedValueWriter.test(value);
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
