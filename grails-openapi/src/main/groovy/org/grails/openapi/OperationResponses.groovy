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

import io.swagger.v3.oas.models.headers.Header
import io.swagger.v3.oas.models.media.ArraySchema
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.media.XML
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses

import grails.core.GrailsControllerClass

/**
 * The responses an operation is described with: those a RestfulController action answers with,
 * or success, and a miss for an operation addressing a resource.
 */
@CompileStatic
class OperationResponses {

    private static final String DEFAULT_RESPONSE_CODE = '200'
    private static final String NOT_FOUND_RESPONSE_CODE = '404'
    private static final String UNPROCESSABLE_RESPONSE_CODE = '422'
    private static final String LOCATION_HEADER = 'Location'

    private final ComponentSchemas schemas
    private final ValidationErrorsContent validationErrors

    OperationResponses(ComponentSchemas schemas, ValidationErrorsContent validationErrors) {
        this.schemas = schemas
        this.validationErrors = validationErrors
    }

    /**
     * The responses a RestfulController action gives: save answers CREATED, delete answers
     * NO_CONTENT with no body, an action addressed by an identifier can miss, and an action that
     * validates what it binds can answer with the validation errors.
     *
     * @param locates whether a save answers with where the created resource is, as a
     * RestfulController does
     */
    ApiResponses restful(GrailsControllerClass controller, Class<?> resourceType, String actionName, boolean takesId,
                         Map<String, Boolean> mediaTypes, boolean locates) {
        ApiResponses responses = new ApiResponses()

        ApiResponse success = new ApiResponse().description('Success')
        if (RestfulControllerActions.hasResponseBody(actionName)) {
            Schema<?> resource = schemas.reference(resourceType)
            if (resource != null) {
                // A list is rendered in XML as a list element holding an element for each.
                Schema<?> schema = RestfulControllerActions.isCollection(actionName)
                        ? new ArraySchema().items(resource).xml(new XML().name('list').wrapped(true))
                        : resource
                success.setContent(MediaTypes.content(schema, mediaTypes))
            }
        }
        if (locates && RestfulControllerActions.locates(actionName)) {
            success.addHeaderObject(LOCATION_HEADER, new Header()
                    .description('The URL of the created resource')
                    .schema(new StringSchema().format('uri')))
        }
        responses.addApiResponse(RestfulControllerActions.successCode(actionName), success)

        if (takesId) {
            responses.addApiResponse(NOT_FOUND_RESPONSE_CODE, new ApiResponse().description('Not Found'))
        }
        Content errors = RestfulControllerActions.validates(actionName) ? validationErrors.content(controller, mediaTypes) : null
        if (errors != null) {
            responses.addApiResponse(UNPROCESSABLE_RESPONSE_CODE, new ApiResponse()
                    .description('Validation failed')
                    .content(errors))
        }
        responses
    }

    /**
     * The responses of an action nothing more is known of: success, and a miss where it addresses
     * a resource.
     */
    static ApiResponses plain(boolean addressesResource) {
        ApiResponses responses = new ApiResponses()
                .addApiResponse(DEFAULT_RESPONSE_CODE, new ApiResponse().description('Success'))
        if (addressesResource) {
            responses.addApiResponse(NOT_FOUND_RESPONSE_CODE, new ApiResponse().description('Not Found'))
        }
        responses
    }
}
