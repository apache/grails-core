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
package org.grails.plugins.web.rest.render.json

import grails.converters.JSON
import grails.rest.render.RenderContext
import grails.rest.render.Renderer
import grails.rest.render.RendererRegistry
import grails.util.GrailsWebUtil
import grails.web.mime.MimeType
import groovy.transform.CompileStatic
import org.grails.plugins.web.rest.render.html.DefaultHtmlRenderer
import org.grails.web.gsp.io.GrailsConventionGroovyPageLocator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.validation.Errors

/**
 * Default renderer for JSON
 *
 * @author Graeme Rocher
 * @since 2.3
 */
@CompileStatic
class DefaultJsonRenderer<T> implements Renderer<T> {

    final Class<T> targetType
    MimeType[] mimeTypes = [MimeType.JSON, MimeType.TEXT_JSON] as MimeType[]
    String encoding = GrailsWebUtil.DEFAULT_ENCODING

    @Autowired(required = false)
    GrailsConventionGroovyPageLocator groovyPageLocator

    @Autowired(required = false)
    RendererRegistry rendererRegistry

    String namedConfiguration
    HttpStatus errorsHttpStatus = HttpStatus.UNPROCESSABLE_ENTITY

    DefaultJsonRenderer(Class<T> targetType) {
        this.targetType = targetType
    }

    DefaultJsonRenderer(Class<T> targetType, MimeType... mimeTypes) {
        this.targetType = targetType
        this.mimeTypes = mimeTypes
    }

    DefaultJsonRenderer(Class<T> targetType, GrailsConventionGroovyPageLocator groovyPageLocator) {
        this.targetType = targetType
        this.groovyPageLocator = groovyPageLocator
    }

    DefaultJsonRenderer(Class<T> targetType, GrailsConventionGroovyPageLocator groovyPageLocator, RendererRegistry rendererRegistry) {
        this.targetType = targetType
        this.groovyPageLocator = groovyPageLocator
        this.rendererRegistry = rendererRegistry
    }

    @Override
    void render(T object, RenderContext context) {
        final mimeType = context.acceptMimeType ?: MimeType.JSON
        context.setContentType(GrailsWebUtil.getContentType(mimeType.name, encoding))
        def viewName = context.viewName ?: context.actionName
        final view = groovyPageLocator?.findViewForFormat(context.controllerName, viewName, mimeType.extension)
        if (view && !(object instanceof Errors)) {
            // if a view is provided, we use the HTML renderer to return an appropriate model to the view
            Renderer htmlRenderer = rendererRegistry?.findRenderer(MimeType.HTML, object)
            if (htmlRenderer == null) {
                htmlRenderer = new DefaultHtmlRenderer(targetType)
            }
            htmlRenderer.render((Object) object, context)
        } else {
            if (object instanceof Errors) {

                context.setStatus(errorsHttpStatus)
            }
            renderJson(object, context)
        }
    }

    /**
     * Subclasses should override to customize JSON response rendering
     *
     * @param object
     * @param context
     */
    protected void renderJson(T object, RenderContext context) {
        JSON converter
        if (namedConfiguration) {
            JSON.use(namedConfiguration) {
                converter = object as JSON
            }
        } else {
            converter = object as JSON
        }
        renderJson(converter, context)
    }

    protected void renderJson(JSON converter, RenderContext context) {
        converter.setExcludes(context.excludes)
        converter.setIncludes(context.includes)
        converter.render(context.getWriter())
    }
}
