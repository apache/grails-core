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

import java.io.Writer;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.stream.BaseStream;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.ser.SerializationContextExt;
import tools.jackson.databind.ser.bean.BeanSerializerBase;
import tools.jackson.databind.ser.impl.UnsupportedTypeSerializer;
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
 * @since 9.0
 */
public final class JsonMapperSupport {

    private final JsonMapper mapper;

    private final ClassValue<Boolean> rendersValue = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            if (isContainer(type)) {
                return false;
            }
            try {
                ValueSerializer<?> serializer = serializationContext().findValueSerializer(type);
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
    }

    /**
     * @return the mapper JSON is written with
     */
    public JsonMapper getMapper() {
        return mapper;
    }

    /**
     * @param out the writer to write JSON to
     * @param prettyPrint whether to indent the JSON with the mapper's default pretty printer
     * @return a generator writing to {@code out}
     */
    public JsonGenerator createGenerator(Writer out, boolean prettyPrint) {
        ObjectWriter writer = prettyPrint ? mapper.writerWithDefaultPrettyPrinter() : mapper.writer();
        return writer.createGenerator(new HtmlSafeJsonWriter(out));
    }

    /**
     * Whether the mapper writes values of the type itself, with a dedicated serializer for a single value. Beans,
     * records, enums other than those with their own serializer (such as {@code Month}), {@code Optional} and other
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
     * Writes a value with the mapper, as a value inside the JSON document the generator is writing.
     *
     * @param generator a generator created by {@link #createGenerator(Writer, boolean)}
     * @param value the value
     */
    public void writeValue(JsonGenerator generator, Object value) {
        generator.writePOJO(value);
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
        SerializationContextExt context = serializationContext();
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

    private SerializationContextExt serializationContext() {
        return mapper._serializationContext();
    }

    private static boolean isContainer(Class<?> type) {
        return Iterable.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type) ||
                Map.Entry.class.isAssignableFrom(type) || Iterator.class.isAssignableFrom(type) ||
                Enumeration.class.isAssignableFrom(type) || BaseStream.class.isAssignableFrom(type) ||
                (type.isArray() && !type.getComponentType().isPrimitive());
    }
}
