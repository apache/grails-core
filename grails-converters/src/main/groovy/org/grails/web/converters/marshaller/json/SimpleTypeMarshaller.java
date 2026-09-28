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
package org.grails.web.converters.marshaller.json;

import java.util.function.Function;

import grails.converters.JSON;
import org.grails.web.converters.exceptions.ConverterException;
import org.grails.web.converters.marshaller.ObjectMarshaller;
import org.grails.web.json.JSONException;

/**
 * A JSON {@link ObjectMarshaller} for a type whose JSON representation is a single {@link String} or
 * {@link Number} value computed directly from the object, such as {@code toString()} or a single
 * accessor. Registered once per type in {@code ConvertersConfigurationInitializer} in place of a
 * dedicated marshaller class.
 *
 * @param <T> the supported type
 * @since 8.0
 */
public final class SimpleTypeMarshaller<T> implements ObjectMarshaller<JSON> {

    private final Class<T> type;

    private final Function<T, Object> valueExtractor;

    /**
     * @param type the type this marshaller supports
     * @param valueExtractor computes the JSON value (a {@link String} or a {@link Number}) from an
     *        instance of {@code type}
     */
    public SimpleTypeMarshaller(Class<T> type, Function<T, Object> valueExtractor) {
        this.type = type;
        this.valueExtractor = valueExtractor;
    }

    public boolean supports(Object object) {
        return type.isInstance(object);
    }

    public void marshalObject(Object object, JSON converter) throws ConverterException {
        try {
            converter.getWriter().value(valueExtractor.apply(type.cast(object)));
        }
        catch (JSONException e) {
            throw new ConverterException(e);
        }
    }

}
