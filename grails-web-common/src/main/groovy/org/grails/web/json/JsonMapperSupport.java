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

import java.io.Writer;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.BaseStream;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.PrettyPrinter;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import tools.jackson.databind.ser.bean.BeanSerializerBase;
import tools.jackson.databind.ser.impl.UnsupportedTypeSerializer;
import tools.jackson.databind.ser.jackson.JsonValueSerializer;
import tools.jackson.databind.ser.jdk.EnumSerializer;
import tools.jackson.databind.ser.std.ReferenceTypeSerializer;
import tools.jackson.databind.ser.std.StdContainerSerializer;
import tools.jackson.databind.ser.std.ToEmptyObjectSerializer;
import tools.jackson.databind.util.TokenBuffer;

/**
 * The Jackson {@link JsonMapper} that {@code grails.converters.JSON} writes through: Spring Boot's auto-configured
 * mapper in an application, so that {@code spring.jackson.*} settings apply, or a default mapper otherwise.
 *
 * <p>The mapper writes the JSON token stream, and writes every value that it has a dedicated serializer for, such as
 * dates and times, {@code Month}, {@code UUID}, {@code Locale}, {@code byte[]}, types with a {@code @JsonValue} and
 * the types that Jackson modules registered with the application serialize. Grails marshallers still render domain
 * classes, beans, records, enums, collections, maps, arrays and {@code Optional}, so the values they contain are
 * rendered as any other value is. The JSON text is written through {@link HtmlSafeJsonWriter}.
 *
 * <p>JSON is written with a copy of the mapper whose serializers offer the values nested in a value they write, such
 * as a property that a module's serializer writes, to the nested value writer the value is written with
 * ({@link #writeValue(JsonGenerator, Object, Predicate)}), so that Grails marshallers render those values as well.
 *
 * @since 9.0
 */
public final class JsonMapperSupport {

    /**
     * A default {@code JsonMapper}, for JSON written outside an application that provides one, such as in a unit test.
     */
    public static final JsonMapperSupport DEFAULT = new JsonMapperSupport(JsonMapper.builder().build());

    private final JsonMapper mapper;

    private final JsonMapper writingMapper;

    private final ClassValue<Boolean> rendersValue = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            if (isContainer(type)) {
                return false;
            }
            try {
                ValueSerializer<?> serializer = serializationContext().findValueSerializer(type);
                if (Enum.class.isAssignableFrom(type) && serializer instanceof JsonValueSerializer) {
                    // an enum renders by name(), so that it binds back, even when it has a @JsonValue
                    return false;
                }
                return !(serializer instanceof BeanSerializerBase || serializer instanceof ToEmptyObjectSerializer ||
                        serializer instanceof EnumSerializer || serializer instanceof ReferenceTypeSerializer ||
                        serializer instanceof StdContainerSerializer || serializer instanceof UnsupportedTypeSerializer);
            }
            catch (RuntimeException | LinkageError e) {
                // a type Jackson cannot build a serializer for is left to the Grails marshallers
                return false;
            }
        }
    };

    /**
     * @param mapper the mapper to write JSON with
     */
    public JsonMapperSupport(JsonMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper cannot be null");
        this.writingMapper = mapper.rebuild().addModule(NestedValueSerializer.module()).build();
    }

    /**
     * @return the mapper JSON is written with, whose configuration and modules apply
     */
    public JsonMapper getMapper() {
        return mapper;
    }

    /**
     * Creates a generator that writes JSON to {@code out}, escaped for an HTML {@code <script>} element. The generator
     * never flushes or closes {@code out}: whoever owns it does.
     *
     * @param out the writer to write JSON to
     * @param prettyPrint whether to indent the JSON with the mapper's default pretty printer; when {@code false}, the
     *        JSON is not indented, even if the mapper enables {@code SerializationFeature.INDENT_OUTPUT}
     * @return a generator writing to {@code out}
     */
    public JsonGenerator createGenerator(Writer out, boolean prettyPrint) {
        // not indented unless asked to be, even when the mapper indents its output (spring.jackson.serialization.indent-output)
        ObjectWriter writer = (prettyPrint ? writingMapper.writerWithDefaultPrettyPrinter() :
                writingMapper.writer().without(SerializationFeature.INDENT_OUTPUT))
                .without(StreamWriteFeature.AUTO_CLOSE_TARGET)
                .without(StreamWriteFeature.FLUSH_PASSED_TO_STREAM);
        return writer.createGenerator(new HtmlSafeJsonWriter(out));
    }

    /**
     * Creates a generator that writes JSON to {@code out}, escaped for an HTML {@code <script>} element and indented
     * by a pretty printer. The generator never flushes or closes {@code out}: whoever owns it does.
     *
     * @param out the writer to write JSON to
     * @param prettyPrinter the pretty printer to indent the JSON with
     * @return a generator writing to {@code out}
     */
    public JsonGenerator createGenerator(Writer out, PrettyPrinter prettyPrinter) {
        ObjectWriter writer = writingMapper.writer().with(prettyPrinter)
                .without(StreamWriteFeature.AUTO_CLOSE_TARGET)
                .without(StreamWriteFeature.FLUSH_PASSED_TO_STREAM);
        return writer.createGenerator(new HtmlSafeJsonWriter(out));
    }

    /**
     * Whether the mapper writes values of the type itself, with a dedicated serializer for a single value. Beans,
     * records, enums other than those with their own serializer (such as {@code Month}), including an enum with a
     * {@code @JsonValue}, {@code Optional} and other
     * reference types, collections, maps, iterables, streams and object arrays are left to Grails marshallers, so that
     * the values they contain are rendered as any other value is.
     *
     * @param type a value type
     * @return whether the mapper writes values of the type
     */
    public boolean rendersValue(Class<?> type) {
        return rendersValue.get(type);
    }

    /**
     * Writes a value with the mapper, as a value inside the JSON document the generator is writing. Inside a value
     * written with {@link #writeValue(JsonGenerator, Object, Predicate)}, the value takes part in that nested write: it
     * is offered to the nested value writer first, as any value nested in that value is.
     *
     * @param generator a generator created by {@link #createGenerator(Writer, boolean)}
     * @param value the value
     */
    public void writeValue(JsonGenerator generator, Object value) {
        generator.writePOJO(value);
    }

    /**
     * Writes a value with the mapper, as a value inside the JSON document the generator is writing, and offers each
     * value nested in it, such as a property that a module's serializer writes with
     * {@link JsonGenerator#writePOJO(Object)}, to a nested value writer first. The writer either writes the nested value
     * to the generator, as one complete JSON value, and returns {@code true}, or returns {@code false} for the mapper to
     * write it.
     *
     * @param generator a generator created by {@link #createGenerator(Writer, boolean)}
     * @param value the value
     * @param nestedValueWriter the nested value writer
     */
    public void writeValue(JsonGenerator generator, Object value, Predicate<Object> nestedValueWriter) {
        NestedValueSerializer.write(generator, value, nestedValueWriter);
    }

    /**
     * @param value a value
     * @return the JSON the mapper writes for the value
     */
    public String writeValueAsString(Object value) {
        return mapper.writeValueAsString(value);
    }

    /**
     * The JSON object key the mapper writes for a map key: a {@code String} key as it is, and any other key as its key
     * serializer writes it, such as a {@code Date} key in the mapper's date format.
     *
     * @param key a non-null map key
     * @return the JSON object key
     */
    public String formatKey(Object key) {
        if (key instanceof String string) {
            return string;
        }
        SerializationContext context = serializationContext();
        ValueSerializer<Object> keySerializer = context.findKeySerializer(key.getClass(), null);
        try (TokenBuffer buffer = TokenBuffer.forGeneration()) {
            buffer.writeStartObject();
            keySerializer.serialize(key, buffer, context);
            buffer.writeEndObject();
            try (JsonParser parser = buffer.asParser()) {
                parser.nextToken();
                if (parser.nextToken() == JsonToken.PROPERTY_NAME) {
                    return parser.currentName();
                }
            }
        }
        return key.toString();
    }

    /**
     * A serialization context with the mapper's configuration, from its public API: the mapper passes one to a format
     * visitor before it visits a type.
     */
    private SerializationContext serializationContext() {
        JsonFormatVisitorWrapper visitor = new JsonFormatVisitorWrapper.Base();
        mapper.acceptJsonFormatVisitor(String.class, visitor);
        return visitor.getContext();
    }

    /**
     * A container is left to the Grails marshallers before its serializer is looked at. An {@code Iterable} is not: Jackson
     * writes a collection or other iterable with a container serializer, and a bean-like one with a bean serializer, which
     * are left to the Grails marshallers as well, while it writes a {@code Path} or a {@code JsonNode} as a value.
     */
    private static boolean isContainer(Class<?> type) {
        return Map.class.isAssignableFrom(type) ||
                Map.Entry.class.isAssignableFrom(type) || Iterator.class.isAssignableFrom(type) ||
                Enumeration.class.isAssignableFrom(type) || BaseStream.class.isAssignableFrom(type) ||
                (type.isArray() && !type.getComponentType().isPrimitive());
    }
}
