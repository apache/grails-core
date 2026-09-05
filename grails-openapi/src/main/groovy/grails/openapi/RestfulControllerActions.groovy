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
package grails.openapi

import groovy.transform.CompileStatic

/**
 * The shape of the actions {@link grails.rest.RestfulController} declares, used to describe a
 * controller reached through the default {@code "/$controller/$action?/$id?"} mapping rather than
 * through a mapping that names it.
 *
 * @author Scott Murphy Heiberg
 * @since 8.0
 */
@CompileStatic
class RestfulControllerActions {

    /**
     * Whether an action addresses a single resource, and so takes the optional id segment.
     */
    private static final Set<String> ID_ACTIONS = ['show', 'edit', 'update', 'patch', 'delete'].toSet().asImmutable()

    /**
     * The method each action answers when the controller does not declare otherwise. Mirrors the
     * {@code allowedMethods} RestfulController declares; a subclass that overrides it is read
     * directly instead.
     */
    private static final String DEFAULT_SUCCESS_CODE = '200'

    private static final Set<String> COLLECTION_ACTIONS = ['index'].toSet().asImmutable()

    private static final Set<String> VALIDATING_ACTIONS = ['save', 'update', 'patch'].toSet().asImmutable()

    private static final Map<String, String> SUCCESS_CODES = [
            save: '201',
            delete: '204',
    ].asImmutable()

    private static final Map<String, String> DEFAULT_METHODS = [
            save: 'POST',
            update: 'PUT',
            patch: 'PATCH',
            delete: 'DELETE',
    ].asImmutable()

    /**
     * The status a successful action responds with. RestfulController answers CREATED from save
     * and NO_CONTENT from delete rather than OK.
     */
    static String successCode(String actionName) {
        SUCCESS_CODES.getOrDefault(actionName, DEFAULT_SUCCESS_CODE)
    }

    /**
     * Whether the successful response carries the resource. Delete renders no content.
     */
    static boolean hasResponseBody(String actionName) {
        actionName != 'delete'
    }

    /**
     * Whether the action responds with a collection of the resource rather than one of them.
     */
    static boolean isCollection(String actionName) {
        actionName in COLLECTION_ACTIONS
    }

    /**
     * Whether the action validates what it binds, and so can answer with the validation errors.
     * Patch delegates to update, so all three do.
     */
    static boolean validates(String actionName) {
        actionName in VALIDATING_ACTIONS
    }

    /**
     * The maximum a listing returns, whatever a larger {@code max} asks for.
     */
    static final int MAX_RESULTS = 100

    /**
     * The default page size when none is asked for.
     */
    static final int DEFAULT_MAX = 10

    /**
     * Whether the action reads the paging and sorting parameters GORM binds from the query string.
     * Only the listing does; the remaining actions address one resource.
     */
    static boolean paginates(String actionName) {
        actionName in COLLECTION_ACTIONS
    }

    static boolean takesId(String actionName) {
        actionName in ID_ACTIONS
    }

    /**
     * @param actionName the action to describe
     * @param allowedMethods the controller's declared {@code allowedMethods}, if any
     * @return the HTTP method the action answers, defaulting to GET
     */
    static String httpMethod(String actionName, Object allowedMethods) {
        Object declared = (allowedMethods instanceof Map) ? ((Map) allowedMethods).get(actionName) : null
        if (declared instanceof CharSequence) {
            return declared.toString().toUpperCase(Locale.ENGLISH)
        }
        if (declared instanceof Collection && declared) {
            // A single OpenAPI operation cannot describe several methods, so the first declared
            // method is documented: RestfulController lists the RESTful one first.
            return ((Collection) declared).first().toString().toUpperCase(Locale.ENGLISH)
        }
        DEFAULT_METHODS.getOrDefault(actionName, 'GET')
    }
}
