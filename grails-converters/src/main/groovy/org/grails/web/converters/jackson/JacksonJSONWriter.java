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

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Date;

import groovy.lang.Writable;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;

import org.grails.web.json.JSONElement;
import org.grails.web.json.JSONException;
import org.grails.web.json.JSONWriter;

/**
 * A {@link JSONWriter} that writes through a Jackson {@link JsonGenerator}, so that object marshallers written against
 * {@code JSONWriter} keep working while Jackson writes the JSON: string escaping, numbers and pretty printing are the
 * mapper's, as in Spring Boot.
 *
 * <p>It keeps the state of {@code JSONWriter} (the {@link JSONWriter#mode}, nesting and comma) exactly as that class
 * does, so the same calls are valid and invalid, and a {@code PathCapturingJSONWriterWrapper} can wrap it. Values
 * passed to {@link #value(Object)} are written as {@code JSONWriter} writes them, a {@link JsonMapperValue} by the
 * mapper. A single value, outside any object or array, is also accepted, as JSON allows.
 *
 * @since 9.0
 */
public class JacksonJSONWriter extends JSONWriter {

    private final JsonGenerator generator;

    private final JsonMapperSupport jsonMapper;

    /**
     * @param generator the generator to write to
     * @param jsonMapper the mapper to write {@link JsonMapperValue}s with
     */
    public JacksonJSONWriter(JsonGenerator generator, JsonMapperSupport jsonMapper) {
        super(null);
        this.generator = generator;
        this.jsonMapper = jsonMapper;
    }

    /**
     * @return the generator this writer writes to
     */
    public JsonGenerator getGenerator() {
        return generator;
    }

    @Override
    public JSONWriter array() {
        if (mode == Mode.INIT || mode == Mode.OBJECT || mode == Mode.ARRAY) {
            push(Mode.ARRAY);
            generate(generator::writeStartArray);
            comma = false;
            return this;
        }
        throw new JSONException("Misplaced array: expected mode of INIT, OBJECT or ARRAY but was " + mode);
    }

    @Override
    public JSONWriter endArray() {
        return end(Mode.ARRAY, ']');
    }

    @Override
    public JSONWriter object() {
        if (mode == Mode.INIT) {
            mode = Mode.OBJECT;
        }
        if (mode == Mode.OBJECT || mode == Mode.ARRAY) {
            generate(generator::writeStartObject);
            if (mode == Mode.OBJECT) {
                mode = Mode.KEY;
            }
            push(Mode.KEY);
            comma = false;
            return this;
        }
        throw new JSONException("Misplaced object: expected mode of INIT, OBJECT or ARRAY but was " + mode);
    }

    @Override
    public JSONWriter endObject() {
        return end(Mode.KEY, '}');
    }

    @Override
    protected JSONWriter end(Mode m, char c) {
        if (mode != m) {
            throw new JSONException(m == Mode.OBJECT ? "Misplaced endObject." : "Misplaced endArray.");
        }
        pop(m);
        generate(c == ']' ? generator::writeEndArray : generator::writeEndObject);
        comma = true;
        return this;
    }

    @Override
    public JSONWriter key(String s) {
        if (s == null) {
            throw new JSONException("Null key.");
        }
        if (mode == Mode.KEY) {
            generate(() -> generator.writeName(s));
            comma = false;
            mode = Mode.OBJECT;
            return this;
        }
        throw new JSONException("Misplaced key: expected mode of KEY but was " + mode);
    }

    @Override
    public JSONWriter value(boolean b) {
        return write(() -> generator.writeBoolean(b), b);
    }

    @Override
    public JSONWriter value(double d) {
        return write(() -> generator.writeNumber(d), d);
    }

    @Override
    public JSONWriter value(long l) {
        return write(() -> generator.writeNumber(l), l);
    }

    @Override
    public JSONWriter value(Number number) {
        return number != null ? write(() -> writeNumber(number), number) : valueNull();
    }

    @Override
    public JSONWriter valueNull() {
        return write(generator::writeNull, null);
    }

    /**
     * Writes a value as {@code JSONWriter} does: a {@link JsonMapperValue} with the mapper, {@code null} (and
     * {@code JSONObject.NULL}), numbers and booleans as JSON literals, a {@code Date} as a JavaScript
     * {@code new Date(...)} (for the javascript JSON date format), a {@link JSONElement} as the JSON it writes, a
     * {@code String} as a JSON string, and any other value as the quoted, JSON encoded text {@code JSONWriter} writes
     * for it, such as a {@code StreamCharBuffer} or the {@code toString()} of an identifier.
     */
    @Override
    public JSONWriter value(Object o) {
        if (o == null || o.equals(null)) {
            return valueNull();
        }
        if (o instanceof JsonMapperValue mapperValue) {
            return write(() -> jsonMapper.writeValue(generator, mapperValue.getValue()), o);
        }
        if (o instanceof Number number) {
            return value(number);
        }
        if (o instanceof Boolean b) {
            return value(b.booleanValue());
        }
        if (o instanceof Date date) {
            return write(() -> generator.writeRawValue("new Date(" + date.getTime() + ")"), o);
        }
        if (o instanceof JSONElement element) {
            return write(() -> generator.writeRawValue(element.toString()), o);
        }
        if (o.getClass() == String.class || o.getClass() == StringBuilder.class || o.getClass() == StringBuffer.class) {
            return write(() -> generator.writeString(o.toString()), o);
        }
        String quoted = quoted(o);
        return write(() -> generator.writeRawValue(quoted), o);
    }

    /**
     * Writes raw JSON text as a value, as {@code JSONWriter} appends a value's text.
     */
    @Override
    protected JSONWriter append(String s) {
        if (s == null) {
            throw new JSONException("Null pointer");
        }
        return write(() -> generator.writeRawValue(s), s);
    }

    @Override
    protected JSONWriter append(Writable writableValue) {
        StringWriter text = new StringWriter();
        try {
            writableValue.writeTo(text);
        }
        catch (IOException e) {
            throw new JSONException(e);
        }
        return write(() -> generator.writeRawValue(text.toString()), writableValue);
    }

    @Override
    protected void comma() {
        // the generator writes separators itself
    }

    private JSONWriter write(Runnable writeValue, Object value) {
        if (mode == Mode.INIT) {
            generate(writeValue);
            mode = Mode.DONE;
            return this;
        }
        if (mode == Mode.OBJECT || mode == Mode.ARRAY) {
            generate(writeValue);
            if (mode == Mode.OBJECT) {
                mode = Mode.KEY;
            }
            comma = true;
            return this;
        }
        throw new JSONException("Value out of sequence: expected mode to be OBJECT or ARRAY when writing '" + value + "' but was " + mode);
    }

    private static void generate(Runnable write) {
        try {
            write.run();
        }
        catch (JacksonException e) {
            throw new JSONException(e);
        }
    }

    /**
     * @return the quoted, JSON encoded text a {@code JSONWriter} writes for a value
     */
    private static String quoted(Object value) {
        StringWriter text = new StringWriter();
        new JSONWriter(text).array().value(value).endArray();
        String array = text.toString();
        return array.substring(1, array.length() - 1);
    }

    private void writeNumber(Number number) {
        if (number instanceof Integer || number instanceof Short || number instanceof Byte) {
            generator.writeNumber(number.intValue());
        }
        else if (number instanceof Long) {
            generator.writeNumber(number.longValue());
        }
        else if (number instanceof Double) {
            generator.writeNumber(number.doubleValue());
        }
        else if (number instanceof Float) {
            generator.writeNumber(number.floatValue());
        }
        else if (number instanceof BigDecimal bigDecimal) {
            generator.writeNumber(bigDecimal);
        }
        else if (number instanceof BigInteger bigInteger) {
            generator.writeNumber(bigInteger);
        }
        else {
            jsonMapper.writeValue(generator, number);
        }
    }
}
