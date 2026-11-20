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

import io.swagger.v3.oas.annotations.tags.Tag as TagAnnotation
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.Paths
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.tags.Tag
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import grails.core.GrailsControllerClass

/**
 * Completes a document once every operation is described: each operation gets an identifier of
 * its own, the tags the operations use are described, and nothing refers to a schema the document
 * does not have.
 */
@CompileStatic
class DocumentCompletion {

    private static final Logger LOG = LoggerFactory.getLogger('grails.openapi.GrailsOpenApiGenerator')

    /**
     * An identifier must be unique across the document. The same action reached through more
     * than one mapping derives the same identifier, so the later one is qualified by the path it is
     * reached at, which depends on the path rather than on the order the mappings were read.
     */
    static void disambiguateOperationIds(Paths paths) {
        Set<String> used = [] as Set
        paths.each { String path, PathItem item ->
            item.readOperationsMap().each { PathItem.HttpMethod method, Operation operation ->
                String id = operation.operationId
                if (id == null || used.add(id)) {
                    return
                }
                String qualified = "${id}_${pathDiscriminator(path)}".toString()
                int suffix = 2
                while (!used.add(qualified)) {
                    qualified = "${id}_${pathDiscriminator(path)}_${suffix++}".toString()
                }
                operation.setOperationId(qualified)
            }
        }
    }

    /**
     * Describes the tags the documented controllers declare, so a grouping carries its description
     * rather than only its name.
     */
    static void registerTags(OpenAPI openApi, Paths paths, Collection<GrailsControllerClass> controllers) {
        Set<String> used = paths.values().collectMany { PathItem item ->
            item.readOperations().collectMany { Operation operation -> operation.tags ?: [] }
        } as Set<String>
        for (GrailsControllerClass controller : controllers) {
            DocumentParts.describe("the tags of [${controller.fullName}]".toString()) {
                registerTags(openApi, controller, used)
            }
        }
    }

    /**
     * Removes a reference to a schema that is not in the document. A class that could not be
     * described leaves the operations that referred to it pointing at nothing, and a reference that
     * does not resolve is worse for a code generator than an operation described without a shape.
     */
    static void dropUnresolvedReferences(Paths paths, Components components) {
        Set<String> defined = components.schemas?.keySet() ?: [] as Set<String>
        paths.each { String path, PathItem item ->
            item.readOperations().each { Operation operation ->
                operation.responses?.values()?.each { ApiResponse response ->
                    if (dropUnresolved(response.content, defined, path) && response.content.values().every { it.schema == null }) {
                        response.setContent(null)
                    }
                }
                if (dropUnresolved(operation.requestBody?.content, defined, path)) {
                    operation.setRequestBody(null)
                }
            }
        }
    }

    private static void registerTags(OpenAPI openApi, GrailsControllerClass controller, Set<String> used) {
        if (ActionAnnotations.isHidden(controller.clazz)) {
            return
        }
        for (TagAnnotation declared : ActionAnnotations.declaredTags(controller.clazz)) {
            if (!(declared.name() in used) || openApi.tags?.any { Tag it -> it.name == declared.name() }) {
                continue
            }
            Tag tag = new Tag().name(declared.name())
            if (declared.description()) {
                tag.setDescription(declared.description())
            }
            openApi.addTagsItem(tag)
        }
    }

    private static boolean dropUnresolved(Content content, Set<String> defined, String path) {
        if (content == null) {
            return false
        }
        boolean emptied = false
        content.each { String mediaTypeName, MediaType mediaType ->
            if (!resolves(mediaType.schema, defined)) {
                LOG.warn('Describing {} without a schema: the type it referred to could not be described', path)
                mediaType.setSchema(null)
                emptied = true
            }
        }
        emptied
    }

    private static boolean resolves(Schema<?> schema, Set<String> defined) {
        if (schema == null) {
            return true
        }
        String ref = schema.$ref ?: schema.items?.$ref
        ref == null || !ref.startsWith(ComponentSchemas.REFERENCE_PREFIX)
                || defined.contains(ref.substring(ComponentSchemas.REFERENCE_PREFIX.length()))
    }

    private static String pathDiscriminator(String path) {
        String cleaned = path.replaceAll(/[^A-Za-z0-9]+/, '_')
        cleaned.startsWith('_') ? cleaned.substring(1) : cleaned
    }
}
