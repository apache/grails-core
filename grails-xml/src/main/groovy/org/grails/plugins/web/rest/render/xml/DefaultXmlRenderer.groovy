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

import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.validation.Errors

import grails.config.Settings
import grails.converters.XML
import grails.rest.render.RenderContext
import grails.rest.render.Renderer
import grails.rest.render.RendererRegistry
import grails.util.GrailsWebUtil
import grails.web.mime.MimeType
import org.grails.core.artefact.DomainClassArtefactHandler
import org.grails.plugins.web.rest.render.html.DefaultHtmlRenderer
import org.grails.plugins.web.rest.render.ProblemDetailResponse
import org.grails.plugins.web.rest.render.ServletRenderContext
import org.grails.plugins.web.rest.render.SpringMessageConverters
import org.grails.plugins.web.rest.render.SpringResponseBody
import org.grails.web.gsp.io.GrailsConventionGroovyPageLocator

/**
 * Default renderer for XML responses
 *
 * @author Graeme Rocher
 * @since 2.3
 */
@Slf4j
@CompileStatic
class DefaultXmlRenderer<T> implements Renderer<T> {

    @Value('${' + Settings.WEB_RENDERING_XML_SPRING + ':' + Settings.WEB_RENDERING_XML_SPRING_DEFAULT + '}')
    boolean useSpringXml = Boolean.parseBoolean(Settings.WEB_RENDERING_XML_SPRING_DEFAULT)

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

    // the registry an autowired controller is given, where the application has several
    @Autowired(required = false)
    @Qualifier('rendererRegistry')
    private ObjectProvider<RendererRegistry> namedRendererRegistryProvider

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

    private final AtomicBoolean problemFallbackReported = new AtomicBoolean()

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
            // the application's only or primary registry, or else the one respond writes with
            resolvedRendererRegistry = rendererRegistryProvider?.getIfUnique() ?: namedRendererRegistryProvider?.getIfAvailable()
        }
        return resolvedRendererRegistry
    }

    private List<HttpMessageConverter<?>> resolveSpringHttpMessageConverters() {
        return configuredSpringHttpMessageConverters() ?: []
    }

    /** The converters this renderer is given, or else Spring MVC's, or {@code null} where it has neither. */
    private List<HttpMessageConverter<?>> configuredSpringHttpMessageConverters() {
        return springHttpMessageConvertersSupplier != null ? springHttpMessageConvertersSupplier.get() :
                springMessageConvertersProvider?.getIfAvailable()?.converters
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

        // A problem is rendered only by the view respond names, as in any other format
        def viewName = object instanceof ProblemDetail ? context.arguments?.view?.toString() :
                (context.viewName ?: context.actionName)
        final view = viewName && !(object instanceof Errors) ?
                resolveGroovyPageLocator()?.findViewForFormat(context.controllerName, viewName, mimeType.extension) : null
        if (view) {
            // if a view is provided, we use the HTML renderer to return an appropriate model to the view; one an
            // application registers for problems renders their HTML page rather than the view respond names
            RendererRegistry registry = resolveRendererRegistry()
            Renderer htmlRenderer = object instanceof ProblemDetail ? null : registry?.findRenderer(MimeType.HTML, object)
            if (htmlRenderer == null) {
                htmlRenderer = DefaultHtmlRenderer.forFormatView(targetType, registry, encoding)
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
     * Writes a problem as Spring MVC writes one a handler method returns, whatever
     * {@code grails.web.rendering.xml.spring} is set to: as {@code application/problem+xml} where a message converter
     * writes it in XML, and otherwise as {@code application/problem+json}, as Spring MVC answers a client that asks for a
     * problem in XML without {@code jackson-dataformat-xml}. Where no converter writes it, the legacy converter writes it
     * as a bean, with the status it carries.
     */
    private void renderProblem(ProblemDetail problem, RenderContext context) {
        if (context instanceof ServletRenderContext) {
            ServletRenderContext servletContext = (ServletRenderContext) context
            Integer status = ProblemDetailResponse.requestedStatus(context)
            List<HttpMessageConverter<?>> converters = configuredSpringHttpMessageConverters()
            if (ProblemDetailResponse.write(problem, servletContext, status, [MediaType.APPLICATION_PROBLEM_XML], converters) ||
                    ProblemDetailResponse.write(problem, servletContext, status, [MediaType.APPLICATION_PROBLEM_JSON], converters)) {
                return
            }
        }
        if (problemFallbackReported.compareAndSet(false, true)) {
            log.warn('No Spring message converter writes a ProblemDetail, so the legacy XML converter writes it as a bean.')
        }
        ProblemDetailResponse.prepare(problem, context)
        renderXml(new XML(problem), context)
    }

    /**
     * Subclasses should override to customize XML response rendering. A problem is written as Spring MVC writes it,
     * whatever the renderer's own settings, unless a subclass renders it itself.
     *
     * @param object
     * @param context
     */
    protected void renderXml(Object object, RenderContext context) {
        if (object instanceof ProblemDetail) {
            renderProblem((ProblemDetail) object, context)
            return
        }
        HttpMessageConverter<Object> springConverter = findSpringConverter(object, context)
        if (springConverter != null) {
            // as Spring MVC writes the value a @ResponseBody handler method returns, with the application's advice
            SpringResponseBody.write(object, (ServletRenderContext) context, springConverter,
                    MediaType.parseMediaType((context.acceptMimeType ?: MimeType.XML).name))
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
        // the legacy converter applies includes, also empty ones, which include none of the properties, and excludes
        if (!useSpringXml || !(context instanceof ServletRenderContext) || namedConfiguration ||
                getEffectiveIncludes(context) != null || getEffectiveExcludes(context)) {
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
