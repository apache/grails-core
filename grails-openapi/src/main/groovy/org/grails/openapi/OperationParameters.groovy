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

import java.lang.reflect.Parameter as ReflectedParameter

import groovy.transform.CompileStatic

import io.swagger.v3.core.util.PrimitiveType
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.media.IntegerSchema
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.parameters.Parameter

import grails.web.http.HttpHeaders
import grails.web.mapping.UrlMapping
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity

/**
 * The parameters of an operation: the variables of its path, the request parameters its action
 * binds by name or through a command object, the paging of a listing, and the version asked for.
 */
@CompileStatic
class OperationParameters {

    private static final String ID = 'id'
    private static final String ID_SUFFIX = 'Id'

    private final Collection<MappingContext> mappingContexts
    private final ComponentSchemas schemas

    OperationParameters(Collection<MappingContext> mappingContexts, ComponentSchemas schemas) {
        this.mappingContexts = mappingContexts
        this.schemas = schemas
    }

    /**
     * Every variable the path declares needs a parameter, and nothing else does.
     */
    void addPathParameters(Operation operation, UrlMapping mapping, List<String> pathNames, Class<?> controllerType,
                           String actionName, Class<?> resourceType) {
        for (String name : pathNames) {
            operation.addParametersItem(new Parameter()
                    .name(name)
                    .in('path')
                    .required(true)
                    .schema(pathParameterSchema(mapping, name, controllerType, actionName, resourceType)))
        }
    }

    /**
     * The parameters of a simple type an action declares are bound from the request by name.
     */
    void addRequestParameters(Operation operation, Class<?> controllerType, String actionName, List<String> pathNames) {
        for (ReflectedParameter reflected : ActionAnnotations.requestParameters(controllerType, actionName)) {
            String name = ActionAnnotations.requestParameterName(reflected)
            if (name in pathNames) {
                continue
            }
            Schema<?> schema = PrimitiveType.createProperty(reflected.type) ?: new StringSchema()
            addIfAbsent(operation, queryParameter(name, null, schema))
        }
    }

    /**
     * A command object an action binds on a request without a body is bound from the request
     * parameters, so each property it binds is a query parameter, sent under the name Grails binds
     * it by, whatever name the command object is described with.
     */
    void addCommandParameters(Operation operation, Class<?> controllerType, String actionName, List<String> pathNames) {
        Class<?> commandType = ActionAnnotations.commandObjectType(controllerType, actionName)
        Schema<?> command = commandType != null ? schemas.inline(commandType) : null
        PropertyNames names = command?.properties ? PropertyNames.of(commandType) : null
        ((Map<String, Schema>) command?.properties)?.each { String described, Schema property ->
            String name = names.nameOf(described) ?: described
            if (name in pathNames || property.readOnly || !isParameterValue(property)) {
                return
            }
            Parameter parameter = queryParameter(name, property.description, property)
            if (described in (command.required ?: [])) {
                parameter.setRequired(true)
            }
            addIfAbsent(operation, parameter)
        }
    }

    /**
     * The paging and sorting a listing accepts. RestfulController passes the request parameters
     * to GORM, so a listing answers to them whether or not a mapping mentions them.
     */
    static void addPagingParameters(Operation operation) {
        IntegerSchema max = new IntegerSchema()
        max.setMinimum(BigDecimal.ZERO)
        max.setMaximum(BigDecimal.valueOf(RestfulControllerActions.MAX_RESULTS))
        max.setDefault(RestfulControllerActions.DEFAULT_MAX)
        addIfAbsent(operation, queryParameter('max',
                "The most results to return, at most ${RestfulControllerActions.MAX_RESULTS}".toString(), max))

        IntegerSchema offset = new IntegerSchema()
        offset.setMinimum(BigDecimal.ZERO)
        addIfAbsent(operation, queryParameter('offset', 'The result to start from', offset))
        addIfAbsent(operation, queryParameter('sort', 'The property to sort by', new StringSchema()))

        StringSchema order = new StringSchema()
        order.setEnum(['asc', 'desc'])
        addIfAbsent(operation, queryParameter('order', 'The direction to sort in', order))
    }

    /**
     * The version an operation answers, asked for with the {@code Accept-Version} header, and
     * required of any version but the one answered where none is asked for.
     */
    static void addVersionParameter(Operation operation, String version, boolean required) {
        StringSchema schema = new StringSchema()
        schema.setEnum([version])
        operation.addParametersItem(new Parameter()
                .name(HttpHeaders.ACCEPT_VERSION)
                .in('header')
                .required(required)
                .description(required
                        ? "Asks for version ${version} of the API".toString()
                        : "Asks for version ${version} of the API, the version answered where none is asked for".toString())
                .schema(schema))
    }

    /**
     * A path variable has the type of the identifier it names - the identifier of the resource an
     * operation addresses, or that of another resource a nested mapping names it by, such as
     * {@code bookId} - or else of the action parameter bound from it, and the pattern and values the
     * mapping constrains it to.
     */
    private Schema<?> pathParameterSchema(UrlMapping mapping, String name, Class<?> controllerType, String actionName,
                                          Class<?> resourceType) {
        Schema<?> schema = identifierSchema(name, resourceType)
        if (schema == null) {
            ReflectedParameter declared = ActionAnnotations.requestParameters(controllerType, actionName)
                    .find { ReflectedParameter it -> ActionAnnotations.requestParameterName(it) == name }
            schema = declared != null ? PrimitiveType.createProperty(declared.type) : null
        }
        schema = schema ?: new StringSchema()
        UrlMappingPaths.constrain(schema, mapping, name)
        schema
    }

    private Schema<?> identifierSchema(String name, Class<?> resourceType) {
        PersistentEntity entity = null
        if (name == ID) {
            entity = GrailsModelConverter.entityFor(resourceType)
        }
        else if (name.endsWith(ID_SUFFIX) && name.length() > ID_SUFFIX.length()) {
            entity = entityNamed(name.substring(0, name.length() - ID_SUFFIX.length()))
        }
        entity != null ? GrailsModelConverter.identifierSchema(entity) : null
    }

    private PersistentEntity entityNamed(String propertyName) {
        for (MappingContext context : mappingContexts) {
            PersistentEntity entity = context.persistentEntities.find { PersistentEntity it ->
                it.decapitalizedName == propertyName
            }
            if (entity != null) {
                return entity
            }
        }
        null
    }

    /**
     * A value a request parameter can carry: not an object, nor a list of objects.
     */
    private static boolean isParameterValue(Schema<?> schema) {
        if (isObject(schema) || [schema.allOf, schema.oneOf, schema.anyOf].any { List<Schema> composed ->
            composed?.any { Schema member -> isObject(member) }
        }) {
            // An object, or one of an object and something else, such as a nullable reference.
            return false
        }
        typeOf(schema) != 'array' || (schema.items != null && !schema.items.$ref && typeOf(schema.items) != 'object')
    }

    private static boolean isObject(Schema<?> schema) {
        schema.$ref || typeOf(schema) == 'object'
    }

    private static String typeOf(Schema<?> schema) {
        schema.type ?: schema.types?.find { String type -> type != 'null' }
    }

    private static void addIfAbsent(Operation operation, Parameter parameter) {
        if (!operation.parameters?.any { Parameter existing -> existing.name == parameter.name }) {
            operation.addParametersItem(parameter)
        }
    }

    private static Parameter queryParameter(String name, String description, Schema<?> schema) {
        Parameter parameter = new Parameter()
        parameter.setName(name)
        parameter.setIn('query')
        parameter.setDescription(description)
        parameter.setSchema(schema)
        parameter
    }
}
