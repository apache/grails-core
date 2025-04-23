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

package org.grails.gorm.graphql.types.scalars.coercing.jsr310

import graphql.language.StringValue
import graphql.schema.Coercing
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import groovy.transform.CompileStatic

import java.time.format.DateTimeParseException

/**
 * Base class for Java 8 date types to extend for GraphQL coercion
 *
 * @param <T> The JSR310 class
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
abstract class Jsr310Coercion<T> implements Coercing<T, T> {

    protected List<String> formats

    Jsr310Coercion(List<String> dateFormats) {
        this.formats = dateFormats
    }

    @Override
    T serialize(Object input) {
        convertValue(input).orElseThrow {
            throw new CoercingSerializeException("Could not convert ${input.class.name} to the required date type")
        }
    }

    @Override
    T parseValue(Object input) {
        convertValue(input).orElseThrow {
            throw new CoercingParseValueException("Could not convert ${input.class.name} to the required date type")
        }
    }

    @Override
    T parseLiteral(Object input) {
        if (input instanceof StringValue) {
            convert(((StringValue) input).value).orElse(null)
        }
        else {
            null
        }
    }

    abstract T parse(String value, String format)

    abstract Class getTypeClass()

    protected Optional<T> convertValue(Object value) {
        if (typeClass.isAssignableFrom(value.class)) {
            Optional.of((T) value)
        }
        else if (value instanceof String) {
            convert((String) value)
        }
        else {
            Optional.empty()
        }
    }

    protected Optional<T> convert(String value) {
        T dateValue
        Exception firstException
        formats.each { String format ->
            if (dateValue == null) {
                try {
                    dateValue = parse(value, format)
                } catch (DateTimeParseException e) {
                    firstException = firstException ?: e
                }
            }
        }

        if (dateValue == null) {
            Optional.empty()
        } else {
            Optional.of(dateValue)
        }
    }
}
