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
package org.grails.plugins.web.rest.render.xml

import java.nio.charset.Charset
import java.util.function.Supplier

import groovy.transform.CompileStatic

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpOutputMessage
import org.springframework.http.MediaType
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.validation.Errors

import grails.converters.XML
import grails.rest.render.RenderContext
import grails.rest.render.Renderer
import grails.rest.render.RendererRegistry
import grails.util.GrailsWebUtil
import grails.web.mime.MimeType
import org.grails.plugins.web.rest.render.html.DefaultHtmlRenderer
import org.grails.plugins.web.rest.render.WriterOutputStream
import org.grails.web.gsp.io.GrailsConventionGroovyPageLocator

/**
 * Default renderer for XML responses
 *
 * @author Graeme Rocher
 * @since 2.3
 */
@CompileStatic
class DefaultXmlRenderer<T> implements Renderer<T> {

    final Class<T> targetType
    MimeType[] mimeTypes = [MimeType.XML, MimeType.TEXT_XML] as MimeType[]

    @Value('${grails.converters.encoding:UTF-8}')
    String encoding = GrailsWebUtil.DEFAULT_ENCODING

    @Autowired(required = false)
    GrailsConventionGroovyPageLocator groovyPageLocator

    @Autowired(required = false)
    RendererRegistry rendererRegistry

    List<HttpMessageConverter<?>> springHttpMessageConverters = []

    /**
     * Resolved when a response is written rather than when this renderer is built, so that
     * obtaining the converters cannot force MVC initialization during bean creation.
     */
    Supplier<List<HttpMessageConverter<?>>> springHttpMessageConvertersSupplier

    private List<HttpMessageConverter<?>> resolveSpringHttpMessageConverters() {
        List<HttpMessageConverter<?>> supplied = springHttpMessageConvertersSupplier?.get()
        return supplied ?: springHttpMessageConverters
    }

    String namedConfiguration

    DefaultXmlRenderer(Class<T> targetType) {
        this.targetType = targetType
    }

    DefaultXmlRenderer(Class<T> targetType, MimeType...mimeTypes) {
        this.targetType = targetType
        this.mimeTypes = mimeTypes
    }

    DefaultXmlRenderer(Class<T> targetType, GrailsConventionGroovyPageLocator groovyPageLocator) {
        this.targetType = targetType
        this.groovyPageLocator = groovyPageLocator
    }

    DefaultXmlRenderer(Class<T> targetType, GrailsConventionGroovyPageLocator groovyPageLocator, RendererRegistry rendererRegistry) {
        this.targetType = targetType
        this.groovyPageLocator = groovyPageLocator
        this.rendererRegistry = rendererRegistry
    }

    @Override
    void render(Object object, RenderContext context) {
        final mimeType = context.acceptMimeType ?: MimeType.XML
        context.setContentType(GrailsWebUtil.getContentType(mimeType.name, encoding))

        def viewName = context.viewName ?: context.actionName
        final view = groovyPageLocator?.findViewForFormat(context.controllerName, viewName, mimeType.extension)
        if (view) {
            // if a view is provided, we use the HTML renderer to return an appropriate model to the view
            Renderer htmlRenderer = rendererRegistry?.findRenderer(MimeType.HTML, object)
            if (htmlRenderer == null) {
                htmlRenderer = new DefaultHtmlRenderer(targetType)
                htmlRenderer.encoding = encoding
            }
            htmlRenderer.render((Object) object, context)
        } else {
            if (object instanceof Errors) {
                context.setStatus(HttpStatus.UNPROCESSABLE_ENTITY)
            }
            renderXml(object, context)
        }

    }

    /**
     * Subclasses should override to customize XML response rendering
     *
     * @param object
     * @param context
     */
    protected void renderXml(Object object, RenderContext context) {
        HttpMessageConverter<Object> springConverter = findSpringConverter(object, context)
        if (springConverter != null) {
            renderWithSpringConverter(springConverter, object, context)
            return
        }

        XML converter

        if (namedConfiguration) {
            XML.use(namedConfiguration) {
                converter = object as XML
            }
        } else {
            converter = object as XML
        }
        renderXml(converter, context)
    }

    private HttpMessageConverter<Object> findSpringConverter(Object object, RenderContext context) {
        if (!resolveSpringHttpMessageConverters() || namedConfiguration || context.includes || context.excludes) {
            return null
        }
        if (object == null || object instanceof Errors || object instanceof Map || object instanceof Collection ||
                object.getClass().isArray()) {
            return null
        }
        MediaType mediaType = MediaType.parseMediaType((context.acceptMimeType ?: MimeType.XML).name)
        return (HttpMessageConverter<Object>) resolveSpringHttpMessageConverters().find { HttpMessageConverter<?> converter ->
            converter.canWrite(object.getClass(), mediaType)
        }
    }

    private void renderWithSpringConverter(
            HttpMessageConverter<Object> converter, Object object, RenderContext context) {
        // Write in the configured encoding rather than the converter's default so the bytes it
        // produces and the characters decoded back out agree, and stream them through instead of
        // holding the whole response in memory.
        Charset charset = Charset.forName(encoding)
        MediaType contentType = new MediaType(
                MediaType.parseMediaType((context.acceptMimeType ?: MimeType.XML).name), charset)
        WriterOutputStream.writeThrough(context.writer, charset) { OutputStream body ->
            HttpOutputMessage message = new HttpOutputMessage() {
                private final HttpHeaders headers = new HttpHeaders()

                @Override
                OutputStream getBody() {
                    return body
                }

                @Override
                HttpHeaders getHeaders() {
                    return headers
                }
            }
            converter.write(object, contentType, message)
        }
    }

    /**
     * Subclasses should override to customize XML response rendering
     *
     * @param object
     * @param context
     */
    protected void renderXml(XML converter, RenderContext context) {
        converter.setExcludes(context.excludes)
        converter.setIncludes(context.includes)
        converter.render(context.getWriter())
    }
}
