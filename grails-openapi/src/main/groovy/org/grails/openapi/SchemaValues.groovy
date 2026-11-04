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
package org.grails.openapi

import groovy.transform.CompileStatic

import io.swagger.v3.oas.models.media.Schema

/**
 * The values a schema lists and suggests - its {@code enum}, {@code default}, {@code example} and
 * {@code const} - written as the type the schema describes.
 *
 * <p>An annotation declares each of them as a string, such as
 * {@code @Schema(allowableValues = ['1', '2'], defaultValue = '1')}. swagger-core converts them for
 * a property of an OpenAPI 3.0 document, whose schema it creates of the property's type, but leaves
 * most of them strings in a 3.1 document, whose schema holds its type as a set of types, and in a
 * schema an annotation declares by its type alone, in either version. A string is none of the
 * values of an integer, a number or a boolean, so such a schema allowed nothing it listed.</p>
 */
@CompileStatic
class SchemaValues {

    private static final String NULL_TYPE = 'null'

    private SchemaValues() {
    }

    /**
     * Converts the values of a schema that describes one type, besides {@code null}, to that type:
     * an integer, a number or a boolean. A value that is not one of that type is left as it is.
     */
    static void typeValues(Schema schema) {
        String type = singleType(schema)
        if (!(type in ['integer', 'number', 'boolean'])) {
            return
        }
        if (schema.enum) {
            ((Schema<Object>) schema).setEnum(schema.enum.collect { Object value -> convert(value, type) })
        }
        if (schema.getDefault() instanceof CharSequence) {
            schema.setDefault(convert(schema.getDefault(), type))
        }
        if (schema.example instanceof CharSequence) {
            schema.setExample(convert(schema.example, type))
        }
        if (schema.getConst() instanceof CharSequence) {
            schema.setConst(convert(schema.getConst(), type))
        }
    }

    private static String singleType(Schema schema) {
        Collection<String> types = schema.types ? schema.types.findAll { String it -> it != NULL_TYPE }
                : (schema.type ? [schema.type] : Collections.<String> emptyList())
        types.size() == 1 ? types.first() : null
    }

    /**
     * A value as swagger-core converts it for a schema it creates of the type: an integer as the
     * narrowest of {@code Integer} and {@code Long} holding it, a number as a {@code BigDecimal}.
     */
    private static Object convert(Object value, String type) {
        if (!(value instanceof CharSequence)) {
            return value
        }
        String text = value.toString().trim()
        try {
            switch (type) {
                case 'integer':
                    BigInteger integer = new BigInteger(text)
                    if (integer.bitLength() < Integer.SIZE) {
                        return integer.intValue()
                    }
                    return integer.bitLength() < Long.SIZE ? integer.longValue() : integer
                case 'number':
                    return new BigDecimal(text)
                default:
                    return text.equalsIgnoreCase('true') || text.equalsIgnoreCase('false') ? Boolean.valueOf(text) : value
            }
        }
        catch (NumberFormatException ignored) {
            return value
        }
    }
}
