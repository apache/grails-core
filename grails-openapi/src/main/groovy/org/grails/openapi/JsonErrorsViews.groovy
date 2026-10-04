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
package org.grails.openapi

import groovy.transform.CompileStatic

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import org.springframework.validation.Errors
import org.springframework.web.servlet.View

import grails.core.GrailsControllerClass
import grails.plugin.json.view.mvc.JsonViewResolver

/**
 * The errors views of JSON views, only loaded where JSON views is present.
 *
 * <p>JSON views renders the errors of a controller with its own {@code _errors} view where it has
 * one, and otherwise with the view for {@code Errors}, falling back to the view for any object.
 * It renders only JSON; the converters render the errors in any other format.</p>
 */
@CompileStatic
class JsonErrorsViews extends ErrorsViews {

    private static final Logger LOG = LoggerFactory.getLogger(JsonErrorsViews)

    private final Collection<JsonViewResolver> resolvers
    private ErrorsRendering rendering
    private final Map<Class<?>, Boolean> ownViews = [:]

    JsonErrorsViews(Collection<JsonViewResolver> resolvers) {
        this.resolvers = resolvers
    }

    static ErrorsViews of(ApplicationContext context) {
        Collection<JsonViewResolver> resolvers
        try {
            resolvers = context.getBeansOfType(JsonViewResolver).values()
        }
        catch (Exception | LinkageError e) {
            LOG.debug('Could not look up the JSON view resolvers', e)
            return NONE
        }
        resolvers ? new JsonErrorsViews(resolvers) : NONE
    }

    @Override
    ErrorsRendering rendering() {
        if (rendering == null) {
            ErrorsRendering found = ErrorsRendering.CONVERTERS
            for (JsonViewResolver resolver : resolvers) {
                View view = resolve(Errors.name) { resolver.resolveView(Errors, Locale.ENGLISH) }
                if (view != null) {
                    found = view.is(resolver.objectView) ? ErrorsRendering.OBJECT_VIEW : ErrorsRendering.ERRORS_VIEW
                    break
                }
            }
            rendering = found
        }
        rendering
    }

    @Override
    boolean hasOwnView(GrailsControllerClass controller) {
        Boolean own = ownViews[controller.clazz]
        if (own == null) {
            String path = "${controller.namespace ? '/' + controller.namespace : ''}/${controller.logicalPropertyName}/_errors"
            own = resolvers.any { JsonViewResolver resolver -> resolve(path) { resolver.resolveView(path, Locale.ENGLISH) } != null }
            ownViews[controller.clazz] = own
        }
        own
    }

    /**
     * A view that fails to resolve, such as a template that does not compile or cannot be read, is
     * no view.
     */
    private static View resolve(String view, Closure<View> resolution) {
        try {
            return resolution.call()
        }
        catch (Exception | LinkageError e) {
            LOG.debug("Could not look up the JSON view for ${view}", e)
            return null
        }
    }
}
