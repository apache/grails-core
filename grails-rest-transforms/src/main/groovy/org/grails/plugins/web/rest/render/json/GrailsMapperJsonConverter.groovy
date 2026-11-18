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
package org.grails.plugins.web.rest.render.json

import groovy.transform.CompileStatic

import tools.jackson.databind.JavaType
import tools.jackson.databind.ObjectWriter
import tools.jackson.databind.json.JsonMapper

import org.springframework.http.MediaType
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter

import org.grails.web.converters.jackson.GrailsJsonMapperCustomizer
import org.grails.web.converters.jackson.JsonProjection

/**
 * Spring's Jackson JSON converter with the Grails mapper, which writes a response as the application's converter writes
 * it, with the types it writes and its prefix, and with the projection of the response, and in {@code text/json} too,
 * which the Grails {@code json} format has.
 *
 * @since 9.0
 */
@CompileStatic
class GrailsMapperJsonConverter extends JacksonJsonHttpMessageConverter {

    private static final MediaType TEXT_JSON = new MediaType('text', 'json')

    private final JsonProjection projection

    /**
     * @param source the application's converter
     * @param mapper the Grails mapper derived from that converter's
     * @param prefix what that converter writes before a value
     * @param projection the projection of the response, or {@code null}
     */
    GrailsMapperJsonConverter(JacksonJsonHttpMessageConverter source, JsonMapper mapper, String prefix, JsonProjection projection) {
        super(mapper)
        List<MediaType> types = new ArrayList<MediaType>(source.supportedMediaTypes)
        if (!types.any { MediaType type -> type.includes(TEXT_JSON) }) {
            types.add(TEXT_JSON)
        }
        setSupportedMediaTypes(types)
        if (prefix) {
            setJsonPrefix(prefix)
        }
        this.projection = projection
    }

    /** The writer of one write, which has the projection and finds a repeated object wherever the write writes it. */
    @Override
    protected ObjectWriter customizeWriter(ObjectWriter writer, JavaType javaType, MediaType contentType) {
        ObjectWriter written = GrailsJsonMapperCustomizer.forOneWrite(writer)
        projection == null ? written : written.withAttribute(JsonProjection.ATTRIBUTE, projection)
    }
}
