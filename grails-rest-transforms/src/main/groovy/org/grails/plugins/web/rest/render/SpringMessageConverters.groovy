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

import org.springframework.http.converter.HttpMessageConverter
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Captures the message converters Spring MVC ends up configured with.
 *
 * <p>Renderers need the same converter list, in the same order, that the handler adapter uses.
 * Injecting the adapter to read them forces the whole MVC infrastructure to be created from a
 * renderer bean, which risks circular dependencies and defeats lazy startup. Spring calls
 * {@link #extendMessageConverters} once with the final list instead, after every
 * {@code WebMvcConfigurer} has contributed, so the ordering applications configure is preserved.</p>
 *
 * @since 8.0
 */
@CompileStatic
class SpringMessageConverters implements WebMvcConfigurer {

    private volatile List<HttpMessageConverter<?>> converters = List.of()

    @Override
    void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        // Keeps the list itself rather than a copy. Spring invokes each WebMvcConfigurer in turn
        // and installs this same instance on the handler adapter, so a configurer ordered after
        // this one can still add, remove or reorder converters. Copying here would freeze a list
        // that is not yet final, leaving Grails rendering with a different set from Spring MVC.
        // Wrapped, not copied: the wrapper still sees whatever later configurers do to the
        // underlying list, while stopping a caller mutating Spring MVC's converters through here.
        this.converters = Collections.unmodifiableList(converters)
    }

    /**
     * @return the converters MVC is configured with, or an empty list before initialization; read
     * when a response is written so that every configurer's contribution is included
     */
    List<HttpMessageConverter<?>> getConverters() {
        return converters
    }
}
