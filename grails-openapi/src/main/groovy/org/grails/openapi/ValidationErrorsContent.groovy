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
import io.swagger.v3.oas.models.media.ArraySchema
import io.swagger.v3.oas.models.media.ComposedSchema
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.IntegerSchema
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.ObjectSchema
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.media.XML

import grails.core.GrailsControllerClass

/**
 * The validation errors a request that cannot be bound is answered with, described once as a
 * schema of the document's own.
 *
 * <p>The converters render the errors, in JSON and XML, and answer with 422. Where the application
 * renders with JSON views, a view renders them in JSON instead: the errors view an application is
 * generated with, which answers with 422, a controller's own errors view, which answers as it sets,
 * or, without either, the view for any object, which answers with success. A schema the base
 * document declares under the name describes the errors in every media type, in place of these.</p>
 */
@CompileStatic
class ValidationErrorsContent {

    private final Components components
    private final ErrorsViews views
    private final String schemaName
    private final boolean declared

    /**
     * @param views the views that render the errors in JSON
     * @param schemaName the name the errors are described under
     * @param declared whether the base document declares the errors, under that name
     */
    ValidationErrorsContent(Components components, ErrorsViews views, String schemaName, boolean declared) {
        this.components = components
        this.views = views
        this.schemaName = schemaName
        this.declared = declared
    }

    /**
     * The errors a failed validation answers with, in each media type it answers with 422 in.
     *
     * @return the content, or {@code null} where the errors are answered with 422 in none of them
     */
    Content content(GrailsControllerClass controller, Map<String, Boolean> mediaTypes) {
        Content content = new Content()
        mediaTypes.each { String mediaType, Boolean shaped ->
            if (!shaped) {
                content.addMediaType(mediaType, new MediaType())
            }
            else if (declared) {
                content.addMediaType(mediaType, new MediaType().schema(ComponentSchemas.referenceTo(schemaName)))
            }
            else if (mediaType in MediaTypes.JSON_MEDIA_TYPES) {
                addJson(content, mediaType, controller)
            }
            else {
                // The converters render the errors in any other format, which the schema describes
                // unless it describes the errors view.
                content.addMediaType(mediaType, new MediaType().schema(views.rendering() == ErrorsRendering.ERRORS_VIEW
                        ? converterErrors() : reference()))
            }
        }
        content.isEmpty() ? null : content
    }

    private void addJson(Content content, String mediaType, GrailsControllerClass controller) {
        if (controller != null && views.hasOwnView(controller)) {
            // Only the application knows the shape of the controller's own view.
            content.addMediaType(mediaType, new MediaType())
        }
        else if (views.rendering() != ErrorsRendering.OBJECT_VIEW) {
            content.addMediaType(mediaType, new MediaType().schema(reference()))
        }
    }

    private Schema<?> reference() {
        if (!components.schemas?.containsKey(schemaName)) {
            components.addSchemas(schemaName, views.rendering() == ErrorsRendering.ERRORS_VIEW
                    ? viewErrors() : converterErrors())
        }
        ComponentSchemas.referenceTo(schemaName)
    }

    /**
     * The errors the converters render: in JSON an object listing them, and in XML an
     * {@code errors} element holding an {@code error} element for each, naming the object and the
     * field in its attributes.
     */
    private static Schema<?> converterErrors() {
        Schema<?> error = new ObjectSchema()
                .addProperty('object', new StringSchema().description('The name of the object that failed validation')
                        .xml(new XML().attribute(true)))
                .addProperty('field', new StringSchema().description('The property that failed validation')
                        .xml(new XML().attribute(true)))
                .addProperty('rejected-value', new Schema<>().description('The value that was rejected'))
                .addProperty('message', new StringSchema().description('Why the value was rejected'))
        error.setRequired(['object', 'message'])
        error.setXml(new XML().name('error'))
        Schema<?> errors = new ObjectSchema()
                .description('The validation errors of a request that could not be bound')
                .addProperty('errors', new ArraySchema().items(error).xml(new XML().wrapped(false)))
        errors.setRequired(['errors'])
        errors.setXml(new XML().name('errors'))
        errors
    }

    /**
     * The errors the errors view of an application with JSON views renders, as the view an
     * application is generated with renders them: one error on its own, or several embedded.
     */
    private static Schema<?> viewErrors() {
        Schema<?> self = new ObjectSchema()
                .addProperty('href', new StringSchema().format('uri').description('The URL the request was made to'))
        Schema<?> error = new ObjectSchema()
                .addProperty('message', new StringSchema().description('Why the request could not be bound'))
                .addProperty('path', new StringSchema().description('The path the request was made to'))
                .addProperty('_links', new ObjectSchema().addProperty('self', self))
        error.setRequired(['message'])
        Schema<?> several = new ObjectSchema()
                .addProperty('total', new IntegerSchema().description('The number of errors'))
                .addProperty('_embedded', new ObjectSchema().addProperty('errors', new ArraySchema().items(error)))
        several.setRequired(['total', '_embedded'])
        Schema<?> errors = new ComposedSchema()
        errors.setDescription('The validation errors of a request that could not be bound')
        errors.setOneOf([error, several])
        errors
    }
}
