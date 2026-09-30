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

import org.springframework.context.ApplicationContext
import org.springframework.util.ClassUtils

import grails.core.GrailsControllerClass

/**
 * The views that render the validation errors in JSON, looked up the way JSON views looks them up
 * when it renders them. Without JSON views the converters render them.
 */
@CompileStatic
abstract class ErrorsViews {

    private static final String JSON_VIEW_RESOLVER = 'grails.plugin.json.view.mvc.JsonViewResolver'

    /**
     * The errors are rendered by the converters, with no view.
     */
    static final ErrorsViews NONE = new ErrorsViews() {

        @Override
        ErrorsRendering rendering() {
            ErrorsRendering.CONVERTERS
        }

        @Override
        boolean hasOwnView(GrailsControllerClass controller) {
            false
        }
    }

    /**
     * The views the application renders the errors with: those of JSON views where the
     * application uses them.
     */
    static ErrorsViews of(ApplicationContext context) {
        if (context == null || !ClassUtils.isPresent(JSON_VIEW_RESOLVER, ErrorsViews.classLoader)) {
            return NONE
        }
        try {
            return JsonErrorsViews.of(context)
        }
        catch (LinkageError ignored) {
            // JSON views is present without what it needs, so it renders nothing.
            return NONE
        }
    }

    /**
     * What renders the errors of a controller without an errors view of its own.
     */
    abstract ErrorsRendering rendering()

    /**
     * Whether the controller has an errors view of its own, which JSON views prefers.
     */
    abstract boolean hasOwnView(GrailsControllerClass controller)
}
