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

import java.nio.charset.StandardCharsets

import groovy.transform.CompileStatic

import tools.jackson.core.JacksonException

import org.springframework.http.HttpInputMessage
import org.springframework.http.HttpOutputMessage
import org.springframework.http.MediaType
import org.springframework.http.converter.AbstractHttpMessageConverter
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.util.StreamUtils

import grails.web.render.NamedJsonRenderer
import org.grails.plugins.web.rest.render.JacksonWriteFailures

/**
 * Writes a response with a configuration registered with the {@code NamedJsonConfigurationRegistry}, and the projection
 * of the response, as a message converter, so that Spring MVC's response body handler writes it with the application's
 * {@code ResponseBodyAdvice} beans, as it writes any other JSON {@code respond} writes. It writes a value only.
 *
 * @since 9.0
 */
@CompileStatic
class NamedJsonConfigurationConverter extends AbstractHttpMessageConverter<Object> {

    private final NamedJsonRenderer renderer

    private final String name

    private final List<String> includes

    private final List<String> excludes

    /**
     * @param renderer the renderer of the registered configurations
     * @param name the name of the configuration
     * @param includes the properties to include, or {@code null} for all
     * @param excludes the properties to leave out, or {@code null} for none
     */
    NamedJsonConfigurationConverter(NamedJsonRenderer renderer, String name, List<String> includes, List<String> excludes) {
        super(MediaType.APPLICATION_JSON, new MediaType('application', '*+json'), new MediaType('text', 'json'))
        this.renderer = renderer
        this.name = name
        this.includes = includes
        this.excludes = excludes
    }

    @Override
    protected boolean supports(Class<?> type) {
        true
    }

    @Override
    boolean canRead(Class<?> type, MediaType mediaType) {
        false
    }

    @Override
    protected Object readInternal(Class<? extends Object> type, HttpInputMessage message) {
        throw new HttpMessageNotReadableException("A named JSON configuration [${name}] writes values only", message)
    }

    @Override
    protected void writeInternal(Object value, HttpOutputMessage message) throws IOException {
        Writer writer = new OutputStreamWriter(StreamUtils.nonClosing(message.body), StandardCharsets.UTF_8)
        try {
            renderer.render(name, value, writer, includes, excludes)
            writer.flush()
        }
        catch (JacksonException | IOException e) {
            // as Spring's Jackson converters report it, also a failure of the response to take what it writes
            throw JacksonWriteFailures.reported(e)
        }
    }
}
