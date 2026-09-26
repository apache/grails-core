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

import java.lang.annotation.Annotation
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Array
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Parameter

import groovy.transform.CompileStatic

import io.swagger.v3.core.util.AnnotationsUtils
import io.swagger.v3.core.util.ParameterProcessor
import io.swagger.v3.oas.annotations.ExternalDocumentation as ExternalDocumentationAnnotation
import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.Operation as OperationAnnotation
import io.swagger.v3.oas.annotations.Parameter as ParameterAnnotation
import io.swagger.v3.oas.annotations.media.Schema as SchemaAnnotation
import io.swagger.v3.oas.annotations.parameters.RequestBody as RequestBodyAnnotation
import io.swagger.v3.oas.annotations.responses.ApiResponse as ApiResponseAnnotation
import io.swagger.v3.oas.annotations.security.SecurityRequirement as SecurityRequirementAnnotation
import io.swagger.v3.oas.annotations.tags.Tag as TagAnnotation
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.ExternalDocumentation
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.SpecVersion
import io.swagger.v3.oas.models.headers.Header
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.parameters.Parameter as ParameterModel
import io.swagger.v3.oas.models.parameters.RequestBody
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import io.swagger.v3.oas.models.security.SecurityRequirement

import grails.web.RequestParameter

/**
 * Reads the OpenAPI annotations an application declares on a controller and its actions, so that
 * what is derived from the URL mappings can be corrected or enriched.
 *
 * <p>The annotations are converted by swagger-core itself, so they mean what they mean on a
 * Spring or JAX-RS endpoint: a schema, an array schema, a media type, an example or a header is
 * described exactly as declared, and a type an annotation names is added to the components.</p>
 */
@CompileStatic
class ActionAnnotations {

    static final String DEFAULT_MEDIA_TYPE = 'application/json'

    private static final String[] NO_MEDIA_TYPES = new String[0]
    private static final String[] DEFAULT_MEDIA_TYPES = [DEFAULT_MEDIA_TYPE] as String[]

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
        actionMethods(controllerClass, actionName).any { Method method ->
            method.isAnnotationPresent(Hidden) || method.getAnnotation(OperationAnnotation)?.hidden()
        }
    }

    /**
     * @return whether the action declares {@code @Operation} or its controller declares
     * {@code @Tag}, which is what an application limiting the document to what it annotated opts in
     */
    static boolean isAnnotated(Class<?> controllerClass, String actionName) {
        declaredTags(controllerClass) || actionMethods(controllerClass, actionName).any { Method method ->
            method.isAnnotationPresent(OperationAnnotation)
        }
    }

    /**
     * The tags a controller declares, which group its operations and can describe the group.
     */
    static List<TagAnnotation> declaredTags(Class<?> controllerClass) {
        controllerClass == null
                ? Collections.<TagAnnotation> emptyList()
                : repeatable(controllerClass, TagAnnotation).findAll { TagAnnotation tag -> tag.name() }.toList()
    }

    /**
     * @return whether the action declares its request body, which then replaces the derived one
     */
    static boolean declaresRequestBody(Class<?> controllerClass, String actionName) {
        actionMethods(controllerClass, actionName).any { Method method ->
            method.isAnnotationPresent(RequestBodyAnnotation) || hasRequestBody(method.getAnnotation(OperationAnnotation))
        }
    }

    /**
     * Applies the declared annotations over the operation derived for the action. The controller's
     * annotations apply first, so an action can refine what its controller declares, and a value
     * that is not declared is left as derived.
     */
    static void apply(Operation operation, Class<?> controllerClass, String actionName,
                      Components components, boolean openapi31) {
        if (controllerClass == null) {
            return
        }
        repeatable(controllerClass, ApiResponseAnnotation).each { ApiResponseAnnotation declared ->
            applyResponse(operation, declared, components, openapi31)
        }
        applySecurity(operation, repeatable(controllerClass, SecurityRequirementAnnotation))

        for (Method method : actionMethods(controllerClass, actionName)) {
            applyOperation(operation, method.getAnnotation(OperationAnnotation), components, openapi31)
            repeatable(method, ApiResponseAnnotation).each { ApiResponseAnnotation declared ->
                applyResponse(operation, declared, components, openapi31)
            }
            repeatable(method, ParameterAnnotation).each { ParameterAnnotation declared ->
                applyParameter(operation, declared, null, components, openapi31)
            }
            for (Parameter parameter : method.parameters) {
                ParameterAnnotation declared = parameter.getAnnotation(ParameterAnnotation)
                if (declared != null) {
                    applyParameter(operation, declared, parameter, components, openapi31)
                }
            }
            applyRequestBody(operation, method.getAnnotation(RequestBodyAnnotation), components, openapi31)
            applySecurity(operation, repeatable(method, SecurityRequirementAnnotation))
        }
        if (openapi31) {
            declareTypes(operation)
        }
    }

    /**
     * swagger-core reads the type an annotation declares into the type of a 3.1 schema, which a 3.1
     * document is written from the types of, so without them the type is not written at all.
     */
    private static void declareTypes(Operation operation) {
        operation.responses?.values()?.each { ApiResponse response ->
            declareTypes(response.content)
            response.headers?.values()?.each { Header header -> declareTypes(header.schema) }
        }
        declareTypes(operation.requestBody?.content)
        operation.parameters?.each { ParameterModel parameter ->
            declareTypes(parameter.schema)
            declareTypes(parameter.content)
        }
    }

    private static void declareTypes(Content content) {
        content?.values()?.each { MediaType mediaType -> declareTypes(mediaType.schema) }
    }

    private static void declareTypes(Schema schema) {
        if (schema == null) {
            return
        }
        if (schema.specVersion == SpecVersion.V31 && schema.type && !schema.types) {
            schema.setTypes([schema.type] as Set<String>)
        }
        declareTypes(schema.items)
        ((Map<String, Schema>) schema.properties)?.values()?.each { Schema property -> declareTypes(property) }
        [schema.allOf, schema.anyOf, schema.oneOf].each { List<Schema> schemas -> schemas?.each { declareTypes(it) } }
        if (schema.additionalProperties instanceof Schema) {
            declareTypes((Schema) schema.additionalProperties)
        }
    }

    /**
     * The command object an action binds, if it takes one.
     *
     * <p>Follows the rule the controller transform applies: a parameter of a simple type is bound
     * from the request parameters, an {@code Object}, an interface or an abstract class is not bound
     * at all, and any other type is bound as a command object.</p>
     *
     * @return the command object type, or {@code null} when the action takes none
     */
    static Class<?> commandObjectType(Class<?> controllerClass, String actionName) {
        Method action = actionMethod(controllerClass, actionName)
        action?.parameterTypes?.find { Class<?> type -> isCommandObject(type) }
    }

    /**
     * The request parameters an action binds by name: the parameters of a simple type. Their names
     * are only known where the application is compiled to keep them.
     */
    static List<Parameter> requestParameters(Class<?> controllerClass, String actionName) {
        Method action = actionMethod(controllerClass, actionName)
        (action?.parameters ?: new Parameter[0]).findAll { Parameter parameter ->
            (parameter.namePresent || parameter.getAnnotation(RequestParameter) != null) && isSimple(parameter.type)
        }.toList()
    }

    /**
     * The name of the request parameter an action parameter is bound from: the one
     * {@code @RequestParameter} names, or its own.
     */
    static String requestParameterName(Parameter parameter) {
        parameter.getAnnotation(RequestParameter)?.value() ?: parameter.name
    }

    /**
     * Whether the controller transform binds a parameter of the type from the request parameters by
     * name: a primitive, a primitive wrapper, {@code String}, or {@code Serializable} - the type a
     * domain identifier is declared as.
     */
    private static boolean isSimple(Class<?> type) {
        type.primitive || type in [Integer, Float, Long, Double, Short, Boolean, Byte, Character, String, Serializable]
    }

    private static boolean isCommandObject(Class<?> type) {
        if (type == null || type.array || isSimple(type) || type == Object) {
            return false
        }
        !type.interface && !Modifier.isAbstract(type.modifiers)
    }

    private static boolean hasRequestBody(OperationAnnotation declared) {
        declared != null && (declared.requestBody().content() || declared.requestBody().description())
    }

    private static void applyOperation(Operation operation, OperationAnnotation declared,
                                       Components components, boolean openapi31) {
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
        ExternalDocumentation externalDocs = externalDocumentation(declared.externalDocs())
        if (externalDocs != null) {
            operation.setExternalDocs(externalDocs)
        }
        declared.parameters().each { ParameterAnnotation parameter ->
            applyParameter(operation, parameter, null, components, openapi31)
        }
        declared.responses().each { ApiResponseAnnotation response ->
            applyResponse(operation, response, components, openapi31)
        }
        if (hasRequestBody(declared)) {
            applyRequestBody(operation, declared.requestBody(), components, openapi31)
        }
        applySecurity(operation, declared.security().toList())
    }

    private static void applyResponse(Operation operation, ApiResponseAnnotation declared,
                                      Components components, boolean openapi31) {
        String code = declared.responseCode() ?: 'default'
        ApiResponses responses = operation.responses ?: new ApiResponses()
        ApiResponse response = responses.get(code) ?: new ApiResponse()

        if (declared.ref()) {
            response = new ApiResponse().$ref(declared.ref())
        }
        else {
            if (declared.description()) {
                response.setDescription(declared.description())
            }
            // An action's return type is not declared, so a response body can only be described
            // by the annotation. Where one is declared it replaces what was derived.
            Content content = AnnotationsUtils.getContent(declared.content(), NO_MEDIA_TYPES, DEFAULT_MEDIA_TYPES,
                    null, components, null, openapi31).orElse(null)
            if (content) {
                response.setContent(content)
            }
            Map<String, Header> headers = AnnotationsUtils.getHeaders(declared.headers(), components, null, openapi31)
                    .orElse(null)
            if (headers) {
                response.setHeaders(headers)
            }
            if (response.description == null) {
                response.setDescription(code)
            }
        }

        responses.addApiResponse(code, response)
        operation.setResponses(responses)
    }

    /**
     * A parameter the operation already describes, such as a path variable, is refined by what
     * is declared; any other declared parameter is added, which is how a query parameter or a
     * header an action reads is described.
     */
    private static void applyParameter(Operation operation, ParameterAnnotation declared, Parameter reflected,
                                       Components components, boolean openapi31) {
        String name = declared.name() ?: (reflected != null ? requestParameterName(reflected) : null)
        if (!name) {
            return
        }
        ParameterModel existing = operation.parameters?.find { ParameterModel candidate ->
            candidate.name == name && (!declared.in().toString() || candidate.in == declared.in().toString())
        }
        if (declared.hidden()) {
            if (existing != null) {
                operation.parameters.remove(existing)
            }
            return
        }

        ParameterModel parameter = existing ?: new ParameterModel().name(name).in(parameterLocation(declared))
        Schema derived = existing?.schema
        List<Annotation> annotations = [(Annotation) declared]
        ParameterProcessor.applyAnnotations(parameter, reflected?.parameterizedType ?: declaredType(declared), annotations,
                components, NO_MEDIA_TYPES, DEFAULT_MEDIA_TYPES, null, openapi31)
        if (derived != null && (reflected == null || reflected.type == Object)) {
            keepDerivedType(parameter, derived, declared)
        }
        if (parameter.in == null) {
            parameter.setIn(parameterLocation(declared))
        }
        if (parameter.in == 'path') {
            parameter.setRequired(true)
        }
        if (existing == null) {
            operation.addParametersItem(parameter)
        }
    }

    /**
     * A parameter declared without a type describes it as a string, so one that refines a
     * parameter the operation already describes, such as the identifier of the resource, keeps
     * the type it was described with.
     */
    private static void keepDerivedType(ParameterModel parameter, Schema derived, ParameterAnnotation declared) {
        if (declared.content() || AnnotationsUtils.hasArrayAnnotation(declared.array())) {
            return
        }
        SchemaAnnotation schema = declared.schema()
        if (!AnnotationsUtils.hasSchemaAnnotation(schema)) {
            parameter.setSchema(derived)
            return
        }
        Schema described = parameter.schema
        if (described == null || schema.implementation() != Void || schema.type() || schema.types() || schema.ref()) {
            return
        }
        described.setType(derived.type)
        described.setTypes(derived.types)
        if (!schema.format()) {
            described.setFormat(derived.format)
        }
    }

    /**
     * The type a parameter declared on the method rather than on a method parameter is described
     * as: the implementation its schema names, or a string.
     */
    private static Class<?> declaredType(ParameterAnnotation declared) {
        Class<?> implementation = declared.schema().implementation()
        if (implementation == null || implementation == Void) {
            implementation = declared.array().schema().implementation()
            if (implementation != null && implementation != Void) {
                return Array.newInstance(implementation, 0).getClass()
            }
            return String
        }
        implementation
    }

    private static String parameterLocation(ParameterAnnotation declared) {
        declared.in().toString() ?: 'query'
    }

    private static void applyRequestBody(Operation operation, RequestBodyAnnotation declared,
                                         Components components, boolean openapi31) {
        if (declared == null) {
            return
        }
        RequestBody requestBody = new RequestBody()
        if (declared.description()) {
            requestBody.setDescription(declared.description())
        }
        if (declared.required()) {
            requestBody.setRequired(true)
        }
        if (declared.ref()) {
            requestBody.set$ref(declared.ref())
        }
        Content content = AnnotationsUtils.getContent(declared.content(), NO_MEDIA_TYPES, DEFAULT_MEDIA_TYPES,
                null, components, null, openapi31).orElse(null)
        if (content) {
            requestBody.setContent(content)
        }
        operation.setRequestBody(requestBody)
    }

    private static void applySecurity(Operation operation, Collection<SecurityRequirementAnnotation> declared) {
        declared?.each { SecurityRequirementAnnotation requirement ->
            if (!requirement.name()) {
                return
            }
            SecurityRequirement model = new SecurityRequirement().addList(requirement.name(), requirement.scopes().toList())
            if (!operation.security?.contains(model)) {
                operation.addSecurityItem(model)
            }
        }
    }

    private static ExternalDocumentation externalDocumentation(ExternalDocumentationAnnotation declared) {
        if (declared == null || !declared.url()) {
            return null
        }
        new ExternalDocumentation().url(declared.url()).description(declared.description() ?: null)
    }

    /**
     * The annotations of a repeatable type an element declares, whether declared once or several
     * times.
     */
    private static <A extends Annotation> List<A> repeatable(AnnotatedElement element, Class<A> type) {
        element == null ? Collections.<A> emptyList() : Arrays.asList(element.getAnnotationsByType(type))
    }

    /**
     * The method an action is declared as. Grails compiles an action that takes parameters into a
     * second method without them, which binds the parameters and calls the declared one, so the
     * declared one is the one that takes them.
     *
     * @return the declared method, or {@code null} where the controller has no method of that name
     */
    static Method actionMethod(Class<?> controllerClass, String actionName) {
        actionMethods(controllerClass, actionName).max { Method method -> method.parameterCount }
    }

    /**
     * The methods an action is declared as, in the most derived class declaring it: an action a
     * controller overrides, such as a {@code RestfulController} one, is declared by the override,
     * whatever parameters the one it overrides takes. Grails compiles an action into more than one
     * method where it takes parameters, so every method of that name the class declares is consulted.
     */
    private static List<Method> actionMethods(Class<?> controllerClass, String actionName) {
        if (controllerClass == null || !actionName) {
            return Collections.<Method> emptyList()
        }
        for (Class<?> type = controllerClass; type != null && type != Object; type = type.superclass) {
            List<Method> declared = type.declaredMethods.findAll { Method it ->
                it.name == actionName && Modifier.isPublic(it.modifiers) && !it.synthetic
            }.toList()
            if (declared) {
                return declared
            }
        }
        Collections.<Method> emptyList()
    }
}
