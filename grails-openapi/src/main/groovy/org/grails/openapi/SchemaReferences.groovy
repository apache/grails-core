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

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.callbacks.Callback
import io.swagger.v3.oas.models.headers.Header
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.Encoding
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.parameters.RequestBody
import io.swagger.v3.oas.models.responses.ApiResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Moves schemas to new names, and every reference to them with them, across a whole document.
 */
@CompileStatic
class SchemaReferences {

    private static final Logger LOG = LoggerFactory.getLogger(SchemaReferences)

    private static final String REFERENCE_PREFIX = Components.COMPONENTS_SCHEMAS_REF

    private final Map<String, String> renames
    private final Set<Schema> visited = Collections.newSetFromMap(new IdentityHashMap<Schema, Boolean>())

    private SchemaReferences(Map<String, String> renames) {
        this.renames = renames
    }

    /**
     * @param renames the new name of each schema to move, applied all at once
     */
    static void rename(OpenAPI openApi, Map<String, String> renames) {
        if (renames) {
            new SchemaReferences(distinct(renames, openApi.components?.schemas?.keySet())).renameIn(openApi)
        }
    }

    /**
     * A schema is only moved to a name nothing else holds or moves to, so no schema replaces another.
     */
    private static Map<String, String> distinct(Map<String, String> renames, Set<String> existing) {
        Map<String, String> moves = [:]
        Set<String> taken = new HashSet<String>(existing ?: Collections.<String> emptySet())
        taken.removeAll(renames.keySet())
        renames.each { String from, String to ->
            if (taken.add(to)) {
                moves[from] = to
            }
            else {
                LOG.warn('Not renaming the schema [{}] to [{}], which another schema holds', from, to)
            }
        }
        moves
    }

    private void renameIn(OpenAPI openApi) {
        Components components = openApi.components
        if (components?.schemas) {
            Map<String, Schema> schemas = new LinkedHashMap<>()
            components.schemas.each { String name, Schema schema -> schemas[renames[name] ?: name] = schema }
            components.setSchemas(schemas)
            schemas.values().each { visit(it) }
        }
        components?.responses?.values()?.each { visit(it) }
        components?.parameters?.values()?.each { visit(it) }
        components?.requestBodies?.values()?.each { visit(it) }
        components?.headers?.values()?.each { visit(it) }
        components?.callbacks?.values()?.each { visit(it) }
        components?.pathItems?.values()?.each { visit(it) }
        openApi.paths?.values()?.each { visit(it) }
        openApi.webhooks?.values()?.each { visit(it) }
    }

    private void visit(PathItem item) {
        item?.parameters?.each { visit(it) }
        item?.readOperations()?.each { Operation operation ->
            operation.parameters?.each { visit(it) }
            visit(operation.requestBody)
            operation.responses?.values()?.each { visit(it) }
            operation.callbacks?.values()?.each { visit(it) }
        }
    }

    private void visit(Callback callback) {
        callback?.values()?.each { visit(it) }
    }

    private void visit(Parameter parameter) {
        visit(parameter?.schema)
        visit(parameter?.content)
    }

    private void visit(RequestBody body) {
        visit(body?.content)
    }

    private void visit(ApiResponse response) {
        visit(response?.content)
        response?.headers?.values()?.each { visit(it) }
    }

    private void visit(Header header) {
        visit(header?.schema)
        visit(header?.content)
    }

    private void visit(Content content) {
        content?.values()?.each { MediaType mediaType ->
            visit(mediaType.schema)
            mediaType.encoding?.values()?.each { Encoding encoding -> encoding.headers?.values()?.each { visit(it) } }
        }
    }

    private void visit(Schema schema) {
        if (schema == null || !visited.add(schema)) {
            return
        }
        String name = schema.$ref?.startsWith(REFERENCE_PREFIX) ? schema.$ref.substring(REFERENCE_PREFIX.length()) : null
        if (name != null && renames.containsKey(name)) {
            schema.set$ref(REFERENCE_PREFIX + renames[name])
        }
        schema.properties?.values()?.each { visit(it) }
        schema.patternProperties?.values()?.each { visit(it) }
        schema.dependentSchemas?.values()?.each { visit(it) }
        schema.allOf?.each { visit(it) }
        schema.anyOf?.each { visit(it) }
        schema.oneOf?.each { visit(it) }
        schema.prefixItems?.each { visit(it) }
        for (Schema child : [schema.items, schema.not, schema.contains, schema.propertyNames, schema.contentSchema,
                             schema.unevaluatedProperties, schema.unevaluatedItems, schema.additionalItems,
                             schema.getIf(), schema.getThen(), schema.getElse()]) {
            visit(child)
        }
        if (schema.additionalProperties instanceof Schema) {
            visit((Schema) schema.additionalProperties)
        }
        schema.discriminator?.mapping?.entrySet()?.each { Map.Entry<String, String> entry ->
            String target = entry.value?.startsWith(REFERENCE_PREFIX) ? entry.value.substring(REFERENCE_PREFIX.length()) : null
            if (target != null && renames.containsKey(target)) {
                entry.setValue(REFERENCE_PREFIX + renames[target])
            }
        }
    }
}
