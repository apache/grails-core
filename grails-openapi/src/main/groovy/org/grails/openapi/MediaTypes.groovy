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

import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.Schema
import org.springframework.context.ApplicationContext

import grails.core.GrailsControllerClass
import grails.web.mime.MimeType

/**
 * The media types an action responds in and binds a body from, as the formats its controller
 * declares map to them.
 */
@CompileStatic
class MediaTypes {

    static final String DEFAULT_MEDIA_TYPE = ActionAnnotations.DEFAULT_MEDIA_TYPE

    /**
     * The media types the JSON renderers answer, and so the ones JSON views renders in.
     */
    static final List<String> JSON_MEDIA_TYPES = [MimeType.JSON.name, MimeType.TEXT_JSON.name].asImmutable()

    private static final String MULTIPART_MEDIA_TYPE = 'multipart/form-data'

    /**
     * The formats that carry the shape the document describes, rather than a view, a form or a
     * HAL document.
     */
    private static final Set<String> DATA_FORMATS = ['json', 'xml'].toSet().asImmutable()

    private final ApplicationContext context
    private final ControllerCatalog controllers
    private Map<String, String> formatMediaTypes

    MediaTypes(ApplicationContext context, ControllerCatalog controllers) {
        this.context = context
        this.controllers = controllers
    }

    /**
     * The media types an action responds in: those of the formats its controller declares in
     * {@code responseFormats}, for the action or for every action, or JSON where it declares none.
     * Each says whether it carries the shape the document describes, which a data format, JSON or
     * XML, does and a view, a form or a HAL document does not.
     */
    Map<String, Boolean> responseMediaTypes(GrailsControllerClass controller, String actionName) {
        Map<String, Boolean> mediaTypes = [:]
        for (String format : controllers.responseFormats(controller, actionName) ?: []) {
            String mediaType = formatMediaTypes()[format]
            if (mediaType != null && !mediaTypes.containsKey(mediaType)) {
                mediaTypes[mediaType] = format in DATA_FORMATS
            }
        }
        mediaTypes ?: [(DEFAULT_MEDIA_TYPE): true] as Map<String, Boolean>
    }

    /**
     * The media types an action binds a body from: {@code multipart/form-data} where what it binds
     * has a file, and otherwise those of the data formats it responds in, or JSON.
     */
    List<String> bodyMediaTypes(GrailsControllerClass controller, Class<?> controllerType, String actionName) {
        Class<?> bound = ActionAnnotations.commandObjectType(controllerType, actionName)
                ?: (controllers.isResourceController(controller) ? controllers.resourceType(controller) : null)
        if (GrailsModelConverter.hasFileProperty(bound)) {
            return [MULTIPART_MEDIA_TYPE]
        }
        List<String> data = responseMediaTypes(controller, actionName).findAll { String type, Boolean shaped -> shaped }
                .keySet().toList()
        data ?: [DEFAULT_MEDIA_TYPE]
    }

    /**
     * The content of a response or body in each media type, with the schema in those that carry
     * the shape.
     */
    static Content content(Schema<?> schema, Map<String, Boolean> mediaTypes) {
        Content content = new Content()
        mediaTypes.each { String mediaType, Boolean shaped ->
            content.addMediaType(mediaType, shaped ? new MediaType().schema(schema) : new MediaType())
        }
        content
    }

    /**
     * The media type Grails maps each format to: the first configured for it.
     */
    private Map<String, String> formatMediaTypes() {
        if (formatMediaTypes == null) {
            formatMediaTypes = [:]
            for (MimeType mimeType : configuredMimeTypes()) {
                if (mimeType.extension && !formatMediaTypes.containsKey(mimeType.extension)) {
                    formatMediaTypes[mimeType.extension] = mimeType.name
                }
            }
        }
        formatMediaTypes
    }

    private MimeType[] configuredMimeTypes() {
        try {
            if (context?.containsBean(MimeType.BEAN_NAME)) {
                return context.getBean(MimeType.BEAN_NAME, MimeType[])
            }
        }
        catch (RuntimeException ignored) {
            // An application without the configured types describes with the defaults.
        }
        MimeType.createDefaults()
    }
}
