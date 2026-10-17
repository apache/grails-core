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
import java.nio.charset.StandardCharsets
import java.util.function.Supplier

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

import tools.jackson.databind.ObjectMapper

import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpOutputMessage
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.AbstractJacksonHttpMessageConverter
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.http.converter.json.ProblemDetailJacksonMixin
import org.springframework.validation.Errors

import grails.converters.XML
import grails.rest.render.RenderContext
import grails.rest.render.Renderer
import grails.rest.render.RendererRegistry
import grails.util.GrailsWebUtil
import grails.web.mime.MimeType
import org.grails.core.artefact.DomainClassArtefactHandler
import org.grails.plugins.web.rest.render.html.DefaultHtmlRenderer
import org.grails.plugins.web.rest.render.ProblemDetailResponse
import org.grails.plugins.web.rest.render.SpringMessageConverters
import org.grails.plugins.web.rest.render.WriterOutputStream
import org.grails.web.gsp.io.GrailsConventionGroovyPageLocator
import org.grails.web.json.JsonMapperSupport

/**
 * Default renderer for XML responses
 *
 * @author Graeme Rocher
 * @since 2.3
 */
@CompileStatic
class DefaultXmlRenderer<T> implements Renderer<T> {

    @Value('${grails.web.rendering.xml.spring:false}')
    boolean useSpringXml = false

    final Class<T> targetType
    MimeType[] mimeTypes = [MimeType.XML, MimeType.TEXT_XML] as MimeType[]

    @Value('${grails.converters.encoding:UTF-8}')
    String encoding = GrailsWebUtil.DEFAULT_ENCODING

    GrailsConventionGroovyPageLocator groovyPageLocator

    // Resolving the locator while collecting MIME type providers creates a bean cycle.
    @Autowired(required = false)
    ObjectProvider<GrailsConventionGroovyPageLocator> groovyPageLocatorProvider

    RendererRegistry rendererRegistry

    @Autowired(required = false)
    ObjectProvider<RendererRegistry> rendererRegistryProvider

    /**
     * Resolved when a response is written rather than when this renderer is built, so that
     * obtaining the converters cannot force MVC initialization during bean creation.
     */
    Supplier<List<HttpMessageConverter<?>>> springHttpMessageConvertersSupplier

    /** Lets an XML renderer bean, which the plugin does not configure, reach MVC's converters. */
    @Autowired(required = false)
    ObjectProvider<SpringMessageConverters> springMessageConvertersProvider

    private volatile GrailsConventionGroovyPageLocator resolvedGroovyPageLocator
    private volatile RendererRegistry resolvedRendererRegistry

    private volatile Tuple2<ObjectMapper, ObjectMapper> problemMapper

    private GrailsConventionGroovyPageLocator resolveGroovyPageLocator() {
        if (groovyPageLocator != null) {
            return groovyPageLocator
        }
        // Only a locator that was found is kept: one registered after the first response is still found
        if (resolvedGroovyPageLocator == null) {
            resolvedGroovyPageLocator = groovyPageLocatorProvider?.getIfAvailable()
        }
        return resolvedGroovyPageLocator
    }

    private RendererRegistry resolveRendererRegistry() {
        if (rendererRegistry != null) {
            return rendererRegistry
        }
        if (resolvedRendererRegistry == null) {
            resolvedRendererRegistry = rendererRegistryProvider?.getIfAvailable()
        }
        return resolvedRendererRegistry
    }

    private List<HttpMessageConverter<?>> resolveSpringHttpMessageConverters() {
        List<HttpMessageConverter<?>> supplied = springHttpMessageConvertersSupplier?.get() ?:
                springMessageConvertersProvider?.getIfAvailable()?.converters
        return supplied ?: []
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
        final view = object instanceof Errors || object instanceof ProblemDetail ? null :
                resolveGroovyPageLocator()?.findViewForFormat(context.controllerName, viewName, mimeType.extension)
        if (view) {
            // if a view is provided, we use the HTML renderer to return an appropriate model to the view
            RendererRegistry registry = resolveRendererRegistry()
            Renderer htmlRenderer = registry?.findRenderer(MimeType.HTML, object)
            if (htmlRenderer == null) {
                htmlRenderer = new DefaultHtmlRenderer(targetType)
                htmlRenderer.encoding = encoding
            }
            htmlRenderer.render((Object) object, context)
        } else if (object instanceof ProblemDetail) {
            renderProblem((ProblemDetail) object, context)
        } else {
            if (object instanceof Errors) {
                context.setStatus(HttpStatus.UNPROCESSABLE_ENTITY)
            }
            renderXml(object, context)
        }

    }

    /**
     * Writes a problem as Spring MVC does, as {@code application/problem+xml} through its message converters, whatever
     * {@code grails.web.rendering.xml.spring} is set to. Where none can write it, as without
     * {@code jackson-dataformat-xml}, it is written in the format Spring's converter writes, with the extension members
     * converted by the mapper of Spring's JSON converter, as the problem written in JSON has them.
     */
    private void renderProblem(ProblemDetail problem, RenderContext context) {
        ProblemDetailResponse.prepare(problem, context)
        MediaType problemXml = MediaType.APPLICATION_PROBLEM_XML
        context.setContentType(GrailsWebUtil.getContentType(problemXml.toString(), encoding))
        List<HttpMessageConverter<?>> converters = resolveSpringHttpMessageConverters()
        HttpMessageConverter<Object> converter = (HttpMessageConverter<Object>) converters.find {
            HttpMessageConverter<?> candidate -> candidate.canWrite(ProblemDetail, problemXml) &&
                    candidate.getSupportedMediaTypes(ProblemDetail).any { MediaType supported ->
                        supported.subtype == 'xml' || supported.subtype.endsWith('+xml')
                    }
        }
        if (converter != null) {
            renderWithSpringConverter(converter, problem, problemXml, context)
            return
        }
        ProblemXml.write(problem, context.writer, problemMapper(converters))
    }

    /**
     * The mapper of Spring's JSON converter, with Spring's mix-in for problems, derived once for the mapper it derives
     * from.
     */
    @CompileDynamic
    private ObjectMapper problemMapper(List<HttpMessageConverter<?>> converters) {
        def json = (JacksonJsonHttpMessageConverter) converters.find { it instanceof JacksonJsonHttpMessageConverter }
        ObjectMapper source = json?.mapper ?: JsonMapperSupport.DEFAULT.mapper
        Tuple2<ObjectMapper, ObjectMapper> derived = problemMapper
        if (derived == null || !derived.v1.is(source)) {
            derived = new Tuple2<ObjectMapper, ObjectMapper>(source,
                    source.rebuild().addMixIn(ProblemDetail, ProblemDetailJacksonMixin).build())
            problemMapper = derived
        }
        derived.v2
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
            renderWithSpringConverter(springConverter, object,
                    MediaType.parseMediaType((context.acceptMimeType ?: MimeType.XML).name), context)
            return
        }

        XML converter

        if (namedConfiguration) {
            XML.use(namedConfiguration) {
                converter = new XML(object)
            }
        } else {
            converter = new XML(object)
        }
        renderXml(converter, context)
    }

    /**
     * The includes the legacy converter applies, and whose presence keeps a response on it. A
     * subclass that carries its own includes returns them here.
     */
    protected List<String> getEffectiveIncludes(RenderContext context) {
        return context.includes
    }

    /** The excludes the legacy converter applies; see {@link #getEffectiveIncludes}. */
    protected List<String> getEffectiveExcludes(RenderContext context) {
        return context.excludes
    }

    private HttpMessageConverter<Object> findSpringConverter(Object object, RenderContext context) {
        if (!useSpringXml || namedConfiguration || getEffectiveIncludes(context) || getEffectiveExcludes(context)) {
            return null
        }
        List<HttpMessageConverter<?>> converters = resolveSpringHttpMessageConverters()
        if (!converters) {
            return null
        }
        if (object == null || object instanceof Errors || object instanceof Map || object instanceof Collection ||
                object.getClass().isArray() || DomainClassArtefactHandler.isDomainClass(object.getClass(), true)) {
            return null
        }
        MediaType mediaType = MediaType.parseMediaType((context.acceptMimeType ?: MimeType.XML).name)
        return (HttpMessageConverter<Object>) converters.find { HttpMessageConverter<?> converter ->
            converter.canWrite(object.getClass(), mediaType) &&
                    converter.getSupportedMediaTypes(object.getClass()).any { MediaType supported ->
                        supported.subtype == 'xml' || supported.subtype.endsWith('+xml')
                    }
        }
    }

    private void renderWithSpringConverter(
            HttpMessageConverter<Object> converter, Object object, MediaType mediaType, RenderContext context) {
        // Jackson only writes UTF encodings; the servlet writer applies the response encoding.
        Charset charset = converter instanceof AbstractJacksonHttpMessageConverter ?
                StandardCharsets.UTF_8 : Charset.forName(encoding)
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
    }

    /**
     * Subclasses should override to customize XML response rendering
     *
     * @param object
     * @param context
     */
    protected void renderXml(XML converter, RenderContext context) {
        converter.setExcludes(getEffectiveExcludes(context))
        converter.setIncludes(getEffectiveIncludes(context))
        converter.render(context.getWriter())
    }
}
