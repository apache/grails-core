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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.BaseStream;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.PrettyPrinter;
import tools.jackson.core.StreamWriteConstraints;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.core.io.CharacterEscapes;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
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
 * The Jackson {@link JsonMapper} that {@code grails.converters.JSON} writes through. With
 * {@code grails.converters.json.legacy}, the default, it is {@link #DEFAULT}, which writes the JSON token stream as
 * Grails 8 wrote it, while the converter's marshallers write every value, whatever the {@code spring.jackson.*}
 * settings say. With the setting {@code false} it is a copy of the application's mapper with the converter's modules,
 * so that {@code spring.jackson.*} settings apply, or of a default mapper where the application has none.
 *
 * <p>Without the legacy setting, the mapper writes the JSON token stream, and the values the converter hands it: every value that it has a dedicated
 * serializer for, such as dates and times, {@code Month}, {@code UUID}, {@code Locale}, {@code byte[]}, types with a
 * {@code @JsonValue} and the types that Jackson modules registered with the application serialize, and, rendering as
 * Spring Boot does, beans, records, collections, maps, arrays and {@code Optional} values, whose nested values the
 * converter renders as any other value. The JSON text is written through {@link HtmlSafeJsonWriter}.
 *
 * <p>JSON is written with a copy of the mapper whose serializers offer the values nested in a value they write, such
 * as a property that a module's serializer writes, to the nested value writer the value is written with
 * ({@link #writeValue(JsonGenerator, Object, Predicate)}), so that Grails marshallers render those values as well.
 *
 * @since 9.0
 */
public final class JsonMapperSupport {

    /**
     * The {@code JsonMapper} of JSON written as Grails 8 wrote it, by the converter with
     * {@code grails.converters.json.legacy} and by a {@code JSONWriter} created without a mapper, which escapes control
     * characters as Grails 8 escaped them and writes values nested as deep as Grails 8 wrote them, which Jackson otherwise
     * limits.
     */
    public static final JsonMapperSupport DEFAULT = new JsonMapperSupport(JsonMapper.builder(JsonFactory.builder()
            .streamWriteConstraints(StreamWriteConstraints.builder().maxNestingDepth(Integer.MAX_VALUE).build())
            .build()).build(), LegacyCharacterEscapes.INSTANCE);

    private final JsonMapper mapper;

    private final JsonMapper writingMapper;

    private final Map<Object, Object> attributes;

    private final ClassValue<Boolean> rendersValue;

    private final ClassValue<Boolean> writesObjectIds;

    private final ClassValue<Boolean> customSerializer;

    // the escapes the generators write with, or null for the mapper's
    private final CharacterEscapes characterEscapes;

    /**
     * @param mapper the mapper to write JSON with
     */
    public JsonMapperSupport(JsonMapper mapper) {
        this(mapper, null);
    }

    private JsonMapperSupport(JsonMapper mapper, CharacterEscapes characterEscapes) {
        this.mapper = Objects.requireNonNull(mapper, "mapper cannot be null");
        this.writingMapper = mapper.rebuild().addModule(NestedValueSerializer.module()).build();
        this.attributes = Map.of();
        this.rendersValue = rendersValue();
        this.writesObjectIds = writesObjectIds();
        this.customSerializer = customSerializer();
        this.characterEscapes = characterEscapes;
    }

    private ClassValue<Boolean> customSerializer() {
        return new ClassValue<>() {
            @Override
            protected Boolean computeValue(Class<?> type) {
                try {
                    // looked up as a value written on its own is, so that the configured format of the type counts
                    return innermost(serializationContext(mapper).findTypedValueSerializer(type, true)).getClass() !=
                            innermost(serializationContext(JsonMapper.shared()).findTypedValueSerializer(type, true)).getClass();
                }
                catch (RuntimeException | LinkageError e) {
                    return false;
                }
            }
        };
    }

    private ClassValue<Boolean> writesObjectIds() {
        return new ClassValue<>() {
            @Override
            protected Boolean computeValue(Class<?> type) {
                try {
                    return serializationContext(mapper).findValueSerializer(type).usesObjectId();
                }
                catch (RuntimeException | LinkageError e) {
                    return false;
                }
            }
        };
    }

    private ClassValue<Boolean> rendersValue() {
        return new ClassValue<>() {
            @Override
            protected Boolean computeValue(Class<?> type) {
                if (isContainer(type)) {
                    return false;
                }
                try {
                    ValueSerializer<?> serializer = innermost(serializationContext(mapper).findValueSerializer(type));
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
    }

    /** The serializer another one wraps, such as a bean serializer that tracks cycles, which decides what it writes. */
    private static ValueSerializer<?> innermost(ValueSerializer<?> serializer) {
        ValueSerializer<?> current = serializer;
        for (ValueSerializer<?> delegatee = current.getDelegatee(); delegatee != null && delegatee != current;
                delegatee = current.getDelegatee()) {
            current = delegatee;
        }
        return current;
    }

    private JsonMapperSupport(JsonMapperSupport base, Map<Object, Object> attributes) {
        this.mapper = base.mapper;
        this.writingMapper = base.writingMapper;
        this.attributes = attributes;
        this.rendersValue = base.rendersValue;
        this.writesObjectIds = base.writesObjectIds;
        this.customSerializer = base.customSerializer;
        this.characterEscapes = base.characterEscapes;
    }

    /**
     * @param key the key of a serialization context attribute
     * @param value its value, which serializers read with {@code SerializationContext#getAttribute}
     * @return a support that writes with the same mapper, whose generators carry the attribute in addition to this
     *         one's, so that a serializer can read what the writer knows about the write
     */
    public JsonMapperSupport withAttribute(Object key, Object value) {
        Map<Object, Object> combined = new HashMap<>(attributes);
        combined.put(key, value);
        return new JsonMapperSupport(this, Map.copyOf(combined));
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
        ObjectWriter writer = withAttributes(prettyPrint ? writingMapper.writerWithDefaultPrettyPrinter() :
                writingMapper.writer().without(SerializationFeature.INDENT_OUTPUT))
                .without(StreamWriteFeature.AUTO_CLOSE_TARGET)
                .without(StreamWriteFeature.FLUSH_PASSED_TO_STREAM);
        return withEscapes(writer).createGenerator(new HtmlSafeJsonWriter(out));
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
        ObjectWriter writer = withAttributes(writingMapper.writer().with(prettyPrinter))
                .without(StreamWriteFeature.AUTO_CLOSE_TARGET)
                .without(StreamWriteFeature.FLUSH_PASSED_TO_STREAM);
        return withEscapes(writer).createGenerator(new HtmlSafeJsonWriter(out));
    }

    private ObjectWriter withEscapes(ObjectWriter writer) {
        return characterEscapes == null ? writer : writer.with(characterEscapes);
    }

    /** The writer with this support's attributes added to those the mapper gives every write, which they leave in place. */
    private ObjectWriter withAttributes(ObjectWriter writer) {
        ObjectWriter attributed = writer;
        for (Map.Entry<Object, Object> attribute : attributes.entrySet()) {
            attributed = attributed.withAttribute(attribute.getKey(), attribute.getValue());
        }
        return attributed;
    }

    /**
     * Whether the mapper writes values of the type itself, with a dedicated serializer for a single value. Beans,
     * records, {@code Optional} and other reference types, collections, maps, iterables, streams and object arrays are
     * left to Grails marshallers, so that the values they contain are rendered as any other value is. So is an enum the
     * mapper writes with Jackson's enum serializer or by its {@code @JsonValue}, so that it renders by name. The mapper
     * writes any other enum itself: one with a serializer of its own, such as {@code Month}, and, on the mapper
     * {@code grails.converters.JSON} writes through without {@code grails.converters.json.legacy}, which writes enums by
     * name, every enum.
     *
     * @param type a value type
     * @return whether the mapper writes values of the type
     */
    public boolean rendersValue(Class<?> type) {
        return rendersValue.get(type);
    }

    /**
     * Whether the mapper writes values of the type with object ids, as for a class annotated {@code @JsonIdentityInfo}:
     * it writes the id of a value it has written already in the document, including a value reached again while it is
     * being written.
     *
     * @param type a value type
     * @return whether the mapper writes object ids for values of the type
     * @since 9.0
     */
    public boolean writesObjectIds(Class<?> type) {
        return writesObjectIds.get(type);
    }

    /**
     * Whether the mapper writes values of the type with a serializer other than the one a default {@code JsonMapper}
     * writes them with, such as a {@code ToStringSerializer} a module of the application registers for {@code Long}.
     *
     * @param type a value type
     * @return whether the mapper has a serializer of its own for the type
     * @since 9.0
     */
    public boolean hasCustomSerializer(Class<?> type) {
        return customSerializer.get(type);
    }

    /**
     * The name of the object the mapper wraps a value it writes on its own in, as Spring Boot's
     * {@code spring.jackson.serialization.wrap-root-value} has it wrap the body of a response: the value's
     * {@code @JsonRootName}, or the simple name of its class.
     *
     * @param type the class of the value
     * @return the name, or {@code null} where the mapper does not wrap root values
     * @since 9.0
     */
    public String rootName(Class<?> type) {
        if (!mapper.serializationConfig().isEnabled(SerializationFeature.WRAP_ROOT_VALUE)) {
            return null;
        }
        return serializationContext(mapper).findRootName(type).getSimpleName();
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
     * Parses JSON text with the mapper's own parser, so that the parser features the mapper is configured with decide what
     * is accepted, such as comments or single quotes. Objects and arrays become the {@link JSONObject}s and
     * {@link JSONArray}s that {@code grails.converters.JSON#parse} has always returned, with the number types Grails' own
     * parser gives: an {@code Integer}, a {@code Long} or a {@code BigInteger} for a number written with as many digits
     * after the decimal point as its exponent moves, such as {@code 15} or {@code 1.5e1}; a {@code Double} for any other
     * number written with the digits {@code Double.toString} writes for it, such as {@code 2.5}; and otherwise a
     * {@code BigDecimal}, such as {@code 1.00}, {@code 1e3} or {@code 1e400}. NaN and Infinity, which a mapper may
     * accept, are {@code Double}s.
     *
     * <p>Text after the value is rejected, as the mapper rejects it when it reads a value, unless the mapper disables
     * {@code DeserializationFeature.FAIL_ON_TRAILING_TOKENS}.
     *
     * @param text the JSON text
     * @return the value: a {@code JSONObject}, a {@code JSONArray}, a {@code String}, a number, a {@code Boolean} or
     *         {@code null}
     * @throws JSONException if the text is not JSON the mapper's parser accepts
     * @since 9.0
     */
    public Object parse(String text) throws JSONException {
        try (JsonParser parser = mapper.createParser(text)) {
            JsonToken token = parser.nextToken();
            if (token == null) {
                throw new JSONException("A JSON text must have a value");
            }
            Object value = readValue(parser, token);
            if (mapper.isEnabled(DeserializationFeature.FAIL_ON_TRAILING_TOKENS) && parser.nextToken() != null) {
                throw new JSONException("Unexpected " + parser.currentToken() + " after the JSON value");
            }
            return value;
        }
        catch (JacksonException e) {
            throw new JSONException(e);
        }
    }

    private static Object readValue(JsonParser parser, JsonToken token) {
        switch (token) {
            case START_OBJECT -> {
                Map<String, Object> properties = new LinkedHashMap<>();
                for (JsonToken next = parser.nextToken(); next == JsonToken.PROPERTY_NAME; next = parser.nextToken()) {
                    String name = parser.currentName();
                    properties.put(name, readValue(parser, parser.nextToken()));
                }
                return new JSONObject(properties);
            }
            case START_ARRAY -> {
                List<Object> elements = new ArrayList<>();
                for (JsonToken next = parser.nextToken(); next != JsonToken.END_ARRAY; next = parser.nextToken()) {
                    if (next == null) {
                        throw new JSONException("A JSON array must end with ']'");
                    }
                    elements.add(readValue(parser, next));
                }
                return new JSONArray(elements);
            }
            case VALUE_STRING -> {
                return parser.getString();
            }
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> {
                // NaN and Infinity, which the mapper may accept, have no decimal value
                return parser.isNaN() ? parser.getDoubleValue() : JSONTokener.number(parser.getDecimalValue());
            }
            case VALUE_TRUE -> {
                return Boolean.TRUE;
            }
            case VALUE_FALSE -> {
                return Boolean.FALSE;
            }
            case VALUE_NULL -> {
                return null;
            }
            default -> throw new JSONException("Unexpected " + token + " in a JSON text");
        }
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
        SerializationContext context = serializationContext(mapper);
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
     * A serialization context with a mapper's configuration, from its public API: the mapper passes one to a format
     * visitor before it visits a type.
     */
    private static SerializationContext serializationContext(JsonMapper mapper) {
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
