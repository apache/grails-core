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

import java.util.function.Predicate

import groovy.transform.CompileStatic

import io.swagger.v3.core.util.Json
import io.swagger.v3.core.util.Json31
import io.swagger.v3.core.util.Yaml
import io.swagger.v3.core.util.Yaml31
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.Paths
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.tags.Tag
import org.springframework.core.io.Resource
import org.springframework.core.io.ResourceLoader

/**
 * The document a description starts from, which describes the API as a whole and what the
 * application does not map itself.
 */
@CompileStatic
class BaseDocument {

    /**
     * Reads a YAML or JSON document.
     *
     * @return the document, or {@code null} where none is configured
     * @throws IllegalStateException where the document configured does not exist
     */
    static OpenAPI read(String location, ResourceLoader resourceLoader, boolean openapi31) {
        if (!location) {
            return null
        }
        Resource resource = resourceLoader.getResource(location)
        if (!resource.exists()) {
            throw new IllegalStateException("The OpenAPI base document [${location}] does not exist".toString())
        }
        String text = resource.inputStream.withCloseable { InputStream input -> input.getText('UTF-8') }
        boolean json = text.trim().startsWith('{')
        Class<OpenAPI> type = OpenAPI
        if (openapi31) {
            return json ? Json31.mapper().readValue(text, type) : Yaml31.mapper().readValue(text, type)
        }
        json ? Json.mapper().readValue(text, type) : Yaml.mapper().readValue(text, type)
    }

    /**
     * Starts a document from the base document: its information, servers, security and external
     * documentation are used as they are, and its tags, extensions, components, and the paths the
     * document selects, are kept beside what is derived.
     */
    static void merge(OpenAPI base, OpenAPI openApi, Paths paths, Components components, Predicate<String> selectsPath) {
        if (base.info != null) {
            openApi.setInfo(base.info)
        }
        if (base.servers) {
            openApi.setServers(base.servers)
        }
        if (base.security) {
            openApi.setSecurity(base.security)
        }
        if (base.externalDocs != null) {
            openApi.setExternalDocs(base.externalDocs)
        }
        base.tags?.each { Tag tag ->
            if (!openApi.tags?.any { Tag existing -> existing.name == tag.name }) {
                openApi.addTagsItem(tag)
            }
        }
        base.extensions?.each { String name, Object value -> openApi.addExtension(name, value) }
        // A path the base document declares belongs to the documents whose paths select it.
        base.paths?.each { String path, PathItem item ->
            if (!paths.containsKey(path) && selectsPath.test(path)) {
                paths.addPathItem(path, item)
            }
        }
        Components declared = base.components
        if (declared != null) {
            declared.schemas?.each { String name, Schema schema -> components.addSchemas(name, schema) }
            declared.securitySchemes?.each { name, scheme -> components.addSecuritySchemes(name, scheme) }
            declared.responses?.each { name, response -> components.addResponses(name, response) }
            declared.parameters?.each { name, parameter -> components.addParameters(name, parameter) }
            declared.examples?.each { name, example -> components.addExamples(name, example) }
            declared.requestBodies?.each { name, body -> components.addRequestBodies(name, body) }
            declared.headers?.each { name, header -> components.addHeaders(name, header) }
            declared.links?.each { name, link -> components.addLinks(name, link) }
            declared.callbacks?.each { name, callback -> components.addCallbacks(name, callback) }
        }
    }
}
