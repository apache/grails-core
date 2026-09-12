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

import grails.converters.JSON
import grails.rest.render.RenderContext
import grails.rest.render.Renderer
import grails.rest.render.RendererRegistry
import grails.rest.render.errors.ValidationProblemDetailFactory
import grails.util.GrailsWebUtil
import grails.web.mime.MimeType
import grails.web.render.NamedJsonRenderer
import org.grails.plugins.web.rest.render.WriterOutputStream
import org.grails.plugins.web.rest.render.html.DefaultHtmlRenderer
import org.grails.web.gsp.io.GrailsConventionGroovyPageLocator

/**
 * Default renderer for JSON
 *
 * @author Graeme Rocher
 * @since 2.3
 */
@CompileStatic
class DefaultJsonRenderer<T> implements Renderer<T> {

    static final MimeType PROBLEM_JSON = new MimeType('application/problem+json', 'json')

    final Class<T> targetType
    MimeType[] mimeTypes = [MimeType.JSON, MimeType.TEXT_JSON] as MimeType[]

    @Value('${grails.converters.encoding:UTF-8}')
    String encoding = GrailsWebUtil.DEFAULT_ENCODING

    @Autowired(required = false)
    GrailsConventionGroovyPageLocator groovyPageLocator

    @Autowired(required = false)
    RendererRegistry rendererRegistry

    String namedConfiguration
    HttpStatus errorsHttpStatus = HttpStatus.UNPROCESSABLE_ENTITY
    boolean useSpringJson
    List<HttpMessageConverter<?>> springHttpMessageConverters = []

    /**
     * Resolved when a response is written rather than when this renderer is built, so that
     * obtaining the converters cannot force MVC initialization during bean creation.
     */
    Supplier<List<HttpMessageConverter<?>>> springHttpMessageConvertersSupplier
    ValidationProblemDetailFactory validationProblemDetailFactory = new ValidationProblemDetailFactory()
    NamedJsonRenderer namedJsonRenderer

    DefaultJsonRenderer(Class<T> targetType) {
        this.targetType = targetType
    }

    DefaultJsonRenderer(Class<T> targetType, MimeType...mimeTypes) {
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
    void render(Object object, RenderContext context) {
        final mimeType = resolveMimeType(context)
        context.setContentType(GrailsWebUtil.getContentType(mimeType.name, encoding))
        def viewName = context.viewName ?: context.actionName
        final view = groovyPageLocator?.findViewForFormat(context.controllerName, viewName, mimeType.extension)
        if (view && !(object instanceof Errors)) {
            // if a view is provided, we use the HTML renderer to return an appropriate model to the view
            Renderer htmlRenderer = rendererRegistry?.findRenderer(MimeType.HTML, object)
            if (htmlRenderer == null) {
                htmlRenderer = new DefaultHtmlRenderer(targetType)
                htmlRenderer.encoding = encoding
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
        String selectedConfiguration = context.arguments?.get('jsonConfiguration')?.toString()
        if (selectedConfiguration && namedJsonRenderer?.contains(selectedConfiguration)) {
            namedJsonRenderer.render(selectedConfiguration, object, context.writer,
                    context.includes, context.excludes)
            return
        }
        if (!selectedConfiguration && canUseSpringConverter(context)) {
            Object springValue = object instanceof Errors ?
                    validationProblemDetailFactory.create((Errors) object, errorsHttpStatus) : object
            MediaType mediaType = object instanceof Errors ?
                    MediaType.parseMediaType(PROBLEM_JSON.name) :
                    MediaType.parseMediaType(resolveMimeType(context).name)
            // Set the content type before writing: once the writer flushes, the response is
            // committed and a later content type change is silently discarded.
            if (object instanceof Errors) {
                context.setContentType(GrailsWebUtil.getContentType(PROBLEM_JSON.name, encoding))
            }
            if (renderWithSpringConverter(springValue, mediaType, context)) {
                return
            }
            if (object instanceof Errors) {
                // No converter could write the problem; restore the negotiated type for the
                // legacy converter path below.
                context.setContentType(GrailsWebUtil.getContentType(resolveMimeType(context).name, encoding))
            }
        }

        JSON converter
        String legacyConfiguration = selectedConfiguration ?: namedConfiguration
        if (legacyConfiguration) {
            JSON.use(legacyConfiguration) {
                converter = object as JSON
            }
        } else {
            converter = object as JSON
        }
        renderJson(converter, context)
    }

    private List<HttpMessageConverter<?>> resolveSpringHttpMessageConverters() {
        List<HttpMessageConverter<?>> supplied = springHttpMessageConvertersSupplier?.get()
        return supplied ?: springHttpMessageConverters
    }

    private boolean canUseSpringConverter(RenderContext context) {
        return useSpringJson && resolveSpringHttpMessageConverters() && !namedConfiguration &&
                !context.includes && !context.excludes
    }

    private boolean renderWithSpringConverter(Object object, MediaType mediaType, RenderContext context) {
        Class<?> objectType = object?.getClass() ?: Object
        HttpMessageConverter<Object> converter = (HttpMessageConverter<Object>) resolveSpringHttpMessageConverters().find {
            HttpMessageConverter<?> candidate -> candidate.canWrite(objectType, mediaType)
        }
        if (converter == null) {
            return false
        }

        // Write in the configured encoding rather than the converter's default so that the bytes it
        // produces and the characters decoded back out agree. A media type carries no charset here,
        // so Jackson would otherwise emit UTF-8 and any other configured encoding would mis-decode.
        Charset charset = Charset.forName(encoding)
        MediaType contentType = new MediaType(mediaType, charset)
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
        return true
    }

    private MimeType resolveMimeType(RenderContext context) {
        MimeType mimeType = context.acceptMimeType
        return mimeType == null || mimeType == MimeType.ALL ? MimeType.JSON : mimeType
    }

    protected void renderJson(JSON converter, RenderContext context) {
        converter.setExcludes(context.excludes)
        converter.setIncludes(context.includes)
        converter.render(context.getWriter())
    }
}
