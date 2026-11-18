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
package org.apache.grails.converters.internal.json

import java.util.function.Function

import groovy.transform.CompileStatic

import grails.converters.JSON
import org.grails.web.converters.exceptions.ConverterException
import org.grails.web.converters.marshaller.ObjectMarshaller
import org.grails.web.json.JSONException

/**
 * A JSON {@link ObjectMarshaller} for the values of a type that each render as a single JSON string or number.
 *
 * @param <T> the type of the values
 * @since 8.0
 */
@CompileStatic
class SimpleTypeMarshaller<T> implements ObjectMarshaller<JSON> {

    private final Class<T> type
    private final Function<? super T, ?> jsonValue

    /**
     * @param type the type of the values, including its subtypes
     * @param jsonValue the string or number that a value renders as
     */
    SimpleTypeMarshaller(Class<T> type, Function<? super T, ?> jsonValue) {
        this.type = type
        this.jsonValue = jsonValue
    }

    @Override
    boolean supports(Object object) {
        type.isInstance(object)
    }

    @Override
    void marshalObject(Object object, JSON converter) throws ConverterException {
        try {
            converter.writer.value(jsonValue.apply(type.cast(object)))
        }
        catch (JSONException e) {
            throw new ConverterException(e)
        }
    }
}
