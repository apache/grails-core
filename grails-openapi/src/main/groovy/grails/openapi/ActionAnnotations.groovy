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

import java.lang.reflect.Method

import groovy.transform.CompileStatic

import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.parameters.Parameter as ParameterModel
import io.swagger.v3.oas.models.media.ArraySchema
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses as ApiResponsesModel

/**
 * Reads the OpenAPI annotations an application declares on a controller action, so that what this
 * module derives from the URL mappings can be corrected or enriched without writing a controller
 * springdoc can scan.
 *
 * @author Scott Murphy Heiberg
 * @since 8.0
 */
@CompileStatic
class ActionAnnotations {

    private static final String DEFAULT_MEDIA_TYPE = 'application/json'

    /**
     * @return whether the controller as a whole is withheld from the document
     */
    static boolean isHidden(Class<?> controllerClass) {
        controllerClass != null && controllerClass.isAnnotationPresent(Hidden)
    }

    /**
     * @return whether the action is withheld from the document, either through {@code @Hidden} or
     * through {@code @Operation(hidden = true)}
     */
    static boolean isHidden(Class<?> controllerClass, String actionName) {
        for (Method method : actionMethods(controllerClass, actionName)) {
            if (method.isAnnotationPresent(Hidden)) {
                return true
            }
            io.swagger.v3.oas.annotations.Operation declared =
                    method.getAnnotation(io.swagger.v3.oas.annotations.Operation)
            if (declared != null && declared.hidden()) {
                return true
            }
        }
        false
    }

    /**
     * Applies the declared annotations over the operation this module built. A value the
     * application did not set is left as derived.
     */
    static void apply(Operation operation, Class<?> controllerClass, String actionName) {
        applyParameters(operation, controllerClass, actionName)
        for (Method method : actionMethods(controllerClass, actionName)) {
            applyOperation(operation, method.getAnnotation(io.swagger.v3.oas.annotations.Operation))
            applyResponses(operation, method.getAnnotation(ApiResponses))
            applyResponse(operation, method.getAnnotation(io.swagger.v3.oas.annotations.responses.ApiResponse))
        }
    }

    private static void applyOperation(Operation operation, io.swagger.v3.oas.annotations.Operation declared) {
        if (declared == null) {
            return
        }
        if (declared.summary()) {
            operation.setSummary(declared.summary())
        }
        if (declared.description()) {
            operation.setDescription(declared.description())
        }
        if (declared.operationId()) {
            operation.setOperationId(declared.operationId())
        }
        if (declared.tags()) {
            operation.setTags(declared.tags().toList())
        }
        if (declared.deprecated()) {
            operation.setDeprecated(true)
        }
    }

    private static void applyResponses(Operation operation, ApiResponses declared) {
        if (declared == null) {
            return
        }
        declared.value().each { applyResponse(operation, it) }
    }

    private static void applyResponse(Operation operation, io.swagger.v3.oas.annotations.responses.ApiResponse declared) {
        if (declared == null || !declared.responseCode()) {
            return
        }
        ApiResponsesModel responses = operation.responses ?: new ApiResponsesModel()
        ApiResponse response = responses.get(declared.responseCode()) ?: new ApiResponse()
        if (declared.description()) {
            response.setDescription(declared.description())
        }

        // An action's return type is not declared, so a response schema can only come from the
        // annotation. Where one is given it replaces what the resource convention supplied.
        Content content = declaredContent(declared.content())
        if (content != null) {
            response.setContent(content)
        }

        responses.addApiResponse(declared.responseCode(), response)
        operation.setResponses(responses)
    }

    /**
     * The tag a controller declares, which groups its operations and can describe the group. An
     * application that declares none is tagged with the controller name.
     */
    static io.swagger.v3.oas.annotations.tags.Tag declaredTag(Class<?> controllerClass) {
        controllerClass?.getAnnotation(io.swagger.v3.oas.annotations.tags.Tag)
    }

    /**
     * Applies the descriptions an application declares for the parameters of an action. A Grails
     * action does not declare its parameters, so they are declared on the method itself.
     */
    static void applyParameters(Operation operation, Class<?> controllerClass, String actionName) {
        if (!operation.parameters) {
            return
        }
        for (Method method : actionMethods(controllerClass, actionName)) {
            method.getAnnotationsByType(io.swagger.v3.oas.annotations.Parameter).each { declared ->
                ParameterModel described = operation.parameters.find { it.name == declared.name() }
                if (described == null) {
                    return
                }
                if (declared.description()) {
                    described.setDescription(declared.description())
                }
                if (declared.example()) {
                    described.setExample(declared.example())
                }
            }
        }
    }

    /**
     * Resolves the schema an application declares for a response. Only the implementation type is
     * read; anything else the annotation can express is left to springdoc.
     */
    private static Content declaredContent(io.swagger.v3.oas.annotations.media.Content[] declared) {
        if (!declared) {
            return null
        }
        Content content = new Content()
        boolean described = false

        for (io.swagger.v3.oas.annotations.media.Content each : declared) {
            // A collection response is declared through array = @ArraySchema(schema = ...), which
            // carries the element type; a single one through schema = @Schema(...).
            Class<?> itemType = declaredType(each.array()?.schema())
            Class<?> implementation = declaredType(each.schema())

            Schema<?> schema
            if (itemType != null) {
                schema = new ArraySchema().items(reference(itemType))
            }
            else if (implementation != null) {
                schema = reference(implementation)
            }
            else {
                continue
            }

            String mediaType = each.mediaType() ?: DEFAULT_MEDIA_TYPE
            content.addMediaType(mediaType, new MediaType().schema(schema))
            described = true
        }
        described ? content : null
    }

    private static Class<?> declaredType(io.swagger.v3.oas.annotations.media.Schema declared) {
        Class<?> implementation = declared?.implementation()
        implementation == null || implementation == Void ? null : implementation
    }

    private static Schema<?> reference(Class<?> type) {
        new Schema<>().$ref(PersistentEntitySchemaBuilder.referencePath(PersistentEntitySchemaBuilder.schemaName(type)))
    }

    /**
     * The types an application declared a response schema for, so their schemas are registered.
     */
    static List<Class<?>> declaredResponseTypes(Class<?> controllerClass, String actionName) {
        List<Class<?>> types = []
        for (Method method : actionMethods(controllerClass, actionName)) {
            collectResponseTypes(method.getAnnotation(ApiResponses)?.value(), types)
            io.swagger.v3.oas.annotations.responses.ApiResponse single =
                    method.getAnnotation(io.swagger.v3.oas.annotations.responses.ApiResponse)
            if (single != null) {
                collectResponseTypes([single] as io.swagger.v3.oas.annotations.responses.ApiResponse[], types)
            }
        }
        types
    }

    private static void collectResponseTypes(io.swagger.v3.oas.annotations.responses.ApiResponse[] declared,
                                             List<Class<?>> types) {
        declared?.each { io.swagger.v3.oas.annotations.responses.ApiResponse response ->
            response.content()?.each { io.swagger.v3.oas.annotations.media.Content each ->
                Class<?> implementation = declaredType(each.schema())
                if (implementation != null) {
                    types << implementation
                }
                Class<?> itemType = declaredType(each.array()?.schema())
                if (itemType != null) {
                    types << itemType
                }
            }
        }
    }

    /**
     * The command object an action binds, if it takes one.
     *
     * <p>Mirrors the rule the controller transform applies: a parameter is data bound as a command
     * object unless its declared type is a primitive, a primitive wrapper, {@code String},
     * {@code Serializable} - the type a domain identifier is declared as - or {@code Object}.</p>
     *
     * @return the command object type, or {@code null} when the action takes none
     */
    static Class<?> commandObjectType(Class<?> controllerClass, String actionName) {
        for (Method method : actionMethods(controllerClass, actionName)) {
            for (Class<?> parameterType : method.parameterTypes) {
                if (isCommandObject(parameterType)) {
                    return parameterType
                }
            }
        }
        null
    }

    private static boolean isCommandObject(Class<?> type) {
        if (type == null || type.primitive || type.array) {
            return false
        }
        !(type in [Integer, Float, Long, Double, Short, Boolean, Byte, Character,
                   String, Serializable, Object])
    }

    /**
     * Grails compiles an action into more than one method where it takes a command object, so every
     * method of that name is consulted rather than only the first found.
     */
    private static List<Method> actionMethods(Class<?> controllerClass, String actionName) {
        if (controllerClass == null || !actionName) {
            return Collections.<Method> emptyList()
        }
        controllerClass.methods.findAll { Method it -> it.name == actionName }.toList()
    }
}
