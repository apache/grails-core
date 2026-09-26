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

import grails.converters.JSON;
import org.grails.web.converters.configuration.ConvertersConfigurationHolder;
import org.grails.web.converters.exceptions.ConverterException;
import org.grails.web.converters.marshaller.ObjectMarshaller;
import org.grails.web.json.JSONException;

/**
 * Renders a value with the {@link JsonMapperSupport JsonMapper} that {@link JSON} writes through, as Spring Boot
 * renders it, when the mapper has a dedicated serializer for a single value of its type: dates and times,
 * {@code Month}, {@code UUID}, {@code Locale}, {@code byte[]}, types with a {@code @JsonValue} and the types that
 * Jackson modules registered with the application serialize (see {@link JsonMapperSupport#rendersValue(Class)}).
 *
 * <p>It is registered ahead of the enum, record, {@code Optional}, collection, map, domain class and bean marshallers,
 * so those never see such a value, and behind any marshaller an application registers, which takes precedence for the
 * types it supports.
 *
 * @since 9.0
 */
public class JsonMapperValueMarshaller implements ObjectMarshaller<JSON> {

    public boolean supports(Object object) {
        return ConvertersConfigurationHolder.getJsonMapper().rendersValue(object.getClass());
    }

    public void marshalObject(Object object, JSON converter) throws ConverterException {
        try {
            converter.getWriter().value(new JsonMapperValue(object, converter.getJsonMapper()));
        }
        catch (JSONException e) {
            throw new ConverterException(e);
        }
    }
}
