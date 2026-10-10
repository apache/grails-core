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

import java.io.IOException;
import java.io.Writer;
import java.util.function.Predicate;

/**
 * A value for a {@link JSONWriter} to write with a {@link JsonMapperSupport}'s mapper. A {@code JSONWriter} with the
 * same mapper streams it to its generator, and offers the values nested in it to the value's nested value writer (see
 * {@link JsonMapperSupport#writeValue(tools.jackson.core.JsonGenerator, Object, Predicate)}). Anywhere else, it is
 * written as any {@link JSONElement} is: the JSON text the mapper writes for the value.
 *
 * @since 9.0
 */
public final class JsonMapperValue implements JSONElement {

    private final Object value;

    private final JsonMapperSupport jsonMapper;

    private final Predicate<Object> nestedValueWriter;

    /**
     * @param value the value to write
     * @param jsonMapper the mapper to write it with
     */
    public JsonMapperValue(Object value, JsonMapperSupport jsonMapper) {
        this(value, jsonMapper, nested -> false);
    }

    /**
     * @param value the value to write
     * @param jsonMapper the mapper to write it with
     * @param nestedValueWriter writes a value nested in the value, as one complete JSON value, and returns {@code true},
     *        or returns {@code false} for the mapper to write it
     */
    public JsonMapperValue(Object value, JsonMapperSupport jsonMapper, Predicate<Object> nestedValueWriter) {
        this.value = value;
        this.jsonMapper = jsonMapper;
        this.nestedValueWriter = nestedValueWriter;
    }

    /**
     * @return the value to write
     */
    public Object getValue() {
        return value;
    }

    JsonMapperSupport getJsonMapperSupport() {
        return jsonMapper;
    }

    Predicate<Object> getNestedValueWriter() {
        return nestedValueWriter;
    }

    @Override
    public Writer writeTo(Writer out) throws IOException {
        out.write(toString());
        return out;
    }

    @Override
    public String toString() {
        return jsonMapper.writeValueAsString(value);
    }
}
