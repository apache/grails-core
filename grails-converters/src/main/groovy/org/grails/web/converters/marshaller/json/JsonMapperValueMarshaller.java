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

import java.lang.reflect.Method;

import org.springframework.util.ReflectionUtils;

import grails.converters.JSON;
import org.grails.web.converters.exceptions.ConverterException;
import org.grails.web.converters.marshaller.ObjectMarshaller;
import org.grails.web.json.JSONException;
import org.grails.web.json.JsonMapperValue;

/**
 * Renders a value with the {@link org.grails.web.json.JsonMapperSupport JsonMapper} that {@link JSON} writes through, as
 * Spring Boot renders it: a single value, such as a date or time, a {@code UUID} or a type with a {@code @JsonValue}, a
 * collection, a map, an array, a record, an {@code Optional}, an enum or a bean, with the rules of
 * {@link org.grails.web.converters.jackson.ConverterJsonModule}.
 *
 * <p>It is registered last, after {@code ProxyUnwrappingMarshaller} and the domain class marshaller, so those never lose
 * a value to it, and every value no marshaller before it supports is written by the mapper. Any marshaller an
 * application registers takes precedence for the types it supports.
 *
 * <p>A value nested in a value the mapper writes, such as a property of a bean, an element of a collection or a property
 * that a Jackson module's serializer writes with {@code JsonGenerator#writePOJO}, is rendered by the converter as any
 * other value is, so marshallers apply to it. Only the single values the mapper writes anyway are left to it.
 *
 * @since 9.0
 */
public class JsonMapperValueMarshaller implements ObjectMarshaller<JSON> {

    private static final ClassValue<Boolean> OVERRIDES_MARSHAL_OBJECT = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            Method marshalObject = ReflectionUtils.findMethod(type, "marshalObject", Object.class, JSON.class);
            return marshalObject != null && marshalObject.getDeclaringClass() != JsonMapperValueMarshaller.class;
        }
    };

    public boolean supports(Object object) {
        return true;
    }

    public void marshalObject(Object object, JSON converter) throws ConverterException {
        write(object, converter);
    }

    /**
     * @param marshaller the marshaller of a value
     * @return whether it writes the value with the converter's mapper, as this marshaller and the domain class
     *         marshaller do, unless a subclass writes it itself
     */
    public static boolean writesWithMapper(ObjectMarshaller<JSON> marshaller) {
        return marshaller instanceof JsonMapperValueMarshaller && !OVERRIDES_MARSHAL_OBJECT.get(marshaller.getClass()) ||
                marshaller instanceof DomainClassMarshaller domainClassMarshaller && domainClassMarshaller.writesWithMapper();
    }

    /**
     * Writes a value with the converter's mapper, and renders the values nested in it with the converter.
     */
    static void write(Object value, JSON converter) throws ConverterException {
        try {
            converter.getWriter().value(new JsonMapperValue(value, converter.getJsonMapperSupport(),
                    nested -> writeNested(nested, converter)));
        }
        catch (JSONException e) {
            throw converterException(e);
        }
    }

    private static boolean writeNested(Object nested, JSON converter) {
        // A single value is left to the mapper, but any other goes back through the converter, which keeps track of the
        // objects it is rendering, so that one that contains itself is found
        if (writesWithMapper(converter.lookupObjectMarshaller(nested)) &&
                converter.getJsonMapperSupport().rendersValue(nested.getClass())) {
            return false;
        }
        converter.getWriter().writeNested(() -> converter.convertAnother(nested));
        return true;
    }

    /**
     * A failure to render a nested value reaches here wrapped by the serializers it was nested in; it is reported as
     * the converter reported it.
     */
    private static ConverterException converterException(JSONException e) {
        for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof ConverterException converterException) {
                return converterException;
            }
        }
        return new ConverterException(e);
    }
}
