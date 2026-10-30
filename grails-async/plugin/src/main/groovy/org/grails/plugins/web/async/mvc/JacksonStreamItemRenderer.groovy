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
package org.grails.plugins.web.async.mvc

import groovy.transform.CompileStatic
import tools.jackson.databind.json.JsonMapper

import org.springframework.beans.factory.BeanRegistry
import org.springframework.core.Ordered

import grails.async.web.StreamItemRenderer

/**
 * Renders any item that is not text as JSON, with the application's {@link JsonMapper} when it has one.
 * It is the last renderer consulted, after every renderer a plugin or the application registers.
 *
 * @since 8.1
 */
@CompileStatic
class JacksonStreamItemRenderer implements StreamItemRenderer, Ordered {

    private final JsonMapper jsonMapper

    JacksonStreamItemRenderer(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper
    }

    /**
     * Creates the renderer around the context's {@code JsonMapper} bean, or a default mapper when the
     * context has none or more than one.
     */
    static JacksonStreamItemRenderer create(BeanRegistry.SupplierContext context) {
        // A context with several mappers and no primary one streams with a default mapper rather than failing to start
        JsonMapper jsonMapper = context.beanProvider(JsonMapper).getIfUnique { -> JsonMapper.builder().build() }
        new JacksonStreamItemRenderer(jsonMapper)
    }

    @Override
    String render(Object item) {
        if (item == null || item instanceof CharSequence) {
            return null
        }
        jsonMapper.writeValueAsString(item)
    }

    @Override
    int getOrder() {
        Ordered.LOWEST_PRECEDENCE
    }

}
