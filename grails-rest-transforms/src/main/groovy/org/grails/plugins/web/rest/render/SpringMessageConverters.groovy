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
package org.grails.plugins.web.rest.render

import groovy.transform.CompileStatic

import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter

/**
 * Resolves the message converters Spring MVC ends up configured with at response time.
 *
 * <p>Renderers need the same converter list, in the same order, that the handler adapter uses.
 * Injecting the adapter to read them forces the whole MVC infrastructure to be created from a
 * renderer bean, which risks circular dependencies and defeats lazy startup. A provider delays
 * that lookup until a response is written and works without deprecated MVC callbacks.</p>
 *
 * @since 9.0
 */
@CompileStatic
class SpringMessageConverters {

    @Autowired(required = false)
    @Qualifier('requestMappingHandlerAdapter')
    ObjectProvider<RequestMappingHandlerAdapter> handlerAdapter

    private volatile List<HttpMessageConverter<?>> converters = List.of()

    void setConverters(List<HttpMessageConverter<?>> converters) {
        // Lightweight web test slices have no MVC handler adapter. They supply their converters
        // here; production always uses the handler adapter's final list when it is available.
        this.converters = Collections.unmodifiableList(converters)
    }

    /**
     * @return the converters MVC is configured with, or an empty list before initialization; read
     * when a response is written so that every configurer's contribution is included
     */
    List<HttpMessageConverter<?>> getConverters() {
        RequestMappingHandlerAdapter adapter = handlerAdapter?.getIfAvailable()
        return adapter == null ? converters : Collections.unmodifiableList(adapter.messageConverters)
    }
}
