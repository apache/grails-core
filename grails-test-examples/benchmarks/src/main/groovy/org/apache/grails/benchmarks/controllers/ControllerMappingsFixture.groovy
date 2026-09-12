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
package org.apache.grails.benchmarks.controllers

import org.springframework.context.ApplicationContext

import grails.core.GrailsApplication
import grails.web.mapping.UrlMapping
import org.grails.web.mapping.DefaultUrlMappingEvaluator
import org.grails.web.mapping.DefaultUrlMappingsHolder
import org.grails.web.mapping.mvc.GrailsControllerUrlMappings

/**
 * Mapping sets for {@code ControllerMappingCollectionBenchmark}, wrapped in the
 * {@code GrailsControllerUrlMappings} that {@code UrlMappingsHandlerMapping} consults per request.
 */
class ControllerMappingsFixture {

    /**
     * A mapping set sized and shaped like a mid-size application's {@code UrlMappings.groovy}: a
     * handful of static URLs, five REST resource blocks, some multi-token dynamic URLs, a couple of
     * method-scoped mappings, the catch-all default mapping, and the error mappings.
     */
    static GrailsControllerUrlMappings createApplicationMappings(GrailsApplication grailsApplication,
            ApplicationContext applicationContext) {
        // These MUST be double-quoted GStrings, exactly as a real UrlMappings.groovy is written.
        // The DSL captures variables by letting its delegate resolve $category and friends; with
        // single quotes they stay literal text, every mapping becomes a fixed path, and match()
        // silently returns null so the benchmark measures failed lookups.
        createMappings(grailsApplication, applicationContext) {
            '/'(controller: 'application', action: 'index')
            '/login'(controller: 'auth', action: 'login')
            '/logout'(controller: 'auth', action: 'logout')
            '/health'(controller: 'health', action: 'index')

            '/api/books'(resources: 'book')
            '/api/authors'(resources: 'author')
            '/api/publishers'(resources: 'publisher')
            '/api/orders'(resources: 'order')
            '/api/customers'(resources: 'customer')

            "/store/$category/$subcategory?"(controller: 'store', action: 'browse')
            "/blog/$year/$month?/$day?/$slug?"(controller: 'blog', action: 'show')
            "/report/$id/download"(controller: 'report', action: 'download')

            get '/search'(controller: 'search', action: 'index')
            post '/feedback'(controller: 'feedback', action: 'save')

            "/$controller/$action?/$id?(.$format)?"()

            '500'(view: '/error')
            '404'(view: '/notFound')
        }
    }

    /**
     * A mapping set in which several patterns deliberately overlap on the same URI, so that
     * {@code matchAll} returns a multi-element candidate array. The application set above produces
     * one or two candidates for a typical URI; this one shows how the per-candidate work in
     * {@code collectControllerMappings} scales, which a two-candidate measurement alone cannot.
     */
    static GrailsControllerUrlMappings createOverlappingMappings(GrailsApplication grailsApplication,
            ApplicationContext applicationContext) {
        createMappings(grailsApplication, applicationContext) {
            '/api/books'(resources: 'book')

            "/api/books/$id"(controller: 'book', action: 'show')
            "/api/$section/$id"(controller: 'book', action: 'show')
            "/$controller/$action?/$id?(.$format)?"()
        }
    }

    private static GrailsControllerUrlMappings createMappings(GrailsApplication grailsApplication,
            ApplicationContext applicationContext, Closure<?> mappings) {
        DefaultUrlMappingEvaluator evaluator = new DefaultUrlMappingEvaluator(applicationContext)
        List<UrlMapping> evaluated = evaluator.evaluateMappings(mappings)
        new GrailsControllerUrlMappings(grailsApplication, new DefaultUrlMappingsHolder(evaluated))
    }
}
