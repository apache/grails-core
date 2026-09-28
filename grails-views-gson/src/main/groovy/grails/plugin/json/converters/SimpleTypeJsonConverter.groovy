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

package grails.plugin.json.converters

import java.util.function.Function

import groovy.json.JsonGenerator
import groovy.transform.CompileStatic

/**
 * A {@link JsonGenerator.Converter} for a type whose JSON representation is a single value computed
 * directly from the object, such as {@code toString()} or a single accessor. Registered once per type
 * in {@code JsonViewTemplateEngine} in place of a dedicated converter class.
 *
 * @since 8.0
 */
@CompileStatic
class SimpleTypeJsonConverter implements JsonGenerator.Converter {

    private final Class<?> type
    private final Function<Object, Object> valueExtractor

    /**
     * @param type the type this converter handles, including its subtypes
     * @param valueExtractor computes the JSON value from an instance of {@code type}
     */
    SimpleTypeJsonConverter(Class<?> type, Function<Object, Object> valueExtractor) {
        this.type = type
        this.valueExtractor = valueExtractor
    }

    @Override
    boolean handles(Class<?> type) {
        this.type.isAssignableFrom(type)
    }

    @Override
    Object convert(Object value, String key) {
        valueExtractor.apply(value)
    }

}
