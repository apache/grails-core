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

import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpOutputMessage
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.validation.Errors

import tools.jackson.core.JacksonException
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.JacksonSerializable
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.jsontype.TypeSerializer

import grails.converters.JSON
import grails.rest.render.RenderContext
import grails.rest.render.Renderer
import grails.rest.render.RendererRegistry
import grails.rest.render.errors.ValidationProblemDetailFactory
import grails.util.GrailsWebUtil
import grails.web.mime.MimeType
import grails.web.render.NamedJsonRenderer
import org.grails.plugins.web.rest.render.JacksonWriteFailures
import org.grails.plugins.web.rest.render.ProblemDetailResponse
import org.grails.plugins.web.rest.render.ServletRenderContext
import org.grails.plugins.web.rest.render.SpringResponseBody
import org.grails.plugins.web.rest.render.html.DefaultHtmlRenderer
import org.grails.web.converters.jackson.GrailsJsonMapperCustomizer
import org.grails.web.converters.jackson.JsonProjection
import org.grails.web.gsp.io.GrailsConventionGroovyPageLocator

/**
 * Default renderer for JSON
 *
 * @author Graeme Rocher
 * @since 2.3
 */
@Slf4j
@CompileStatic
class DefaultJsonRenderer<T> implements Renderer<T> {

    static final MimeType PROBLEM_JSON = new MimeType('application/problem+json', 'json')

    private static final ValidationProblemDetailFactory DEFAULT_PROBLEM_DETAIL_FACTORY = new ValidationProblemDetailFactory()

    /** How many types, each with a reason, the legacy converter is reported for while Spring JSON rendering is enabled. */
    static final int MAX_REPORTED_FALLBACKS = 100

    /** The prefix of each converter, which a converter that is no longer used does not keep. */
    private static final Map<JacksonJsonHttpMessageConverter, String> PREFIXES =
            Collections.synchronizedMap(new WeakHashMap<JacksonJsonHttpMessageConverter, String>())

    final Class<T> targetType
    MimeType[] mimeTypes = [MimeType.JSON, MimeType.TEXT_JSON] as MimeType[]

    @Value('${grails.converters.encoding:UTF-8}')
    String encoding = GrailsWebUtil.DEFAULT_ENCODING

    @Autowired(required = false)
    GrailsConventionGroovyPageLocator groovyPageLocator

    /**
     * Finds the GSP locator when a response is written, for a renderer built before the locator, as the registry's own
     * are built in a unit test.
     */
    @Autowired(required = false)
    ObjectProvider<GrailsConventionGroovyPageLocator> groovyPageLocatorProvider

    @Autowired(required = false)
    RendererRegistry rendererRegistry

    /**
     * The legacy {@link JSON} named configuration the renderer writes with.
     *
     * @deprecated Scheduled for removal in Grails 11, with the legacy JSON converter's named configurations. Register
     * a configuration with {@link grails.converters.json.NamedJsonConfigurationRegistry} and select it with the
     * {@code jsonConfiguration} argument of {@code respond}.
     */
    @Deprecated(since = '9.0', forRemoval = true)
    String namedConfiguration
    HttpStatus errorsHttpStatus = HttpStatus.UNPROCESSABLE_CONTENT

    /**
     * Whether responses are written by Spring's message converters rather than the legacy
     * {@link JSON} converter. Left unset, the renderer registry applies
     * {@code grails.web.rendering.json.spring}; unset outside a registry means the legacy path.
     * Grails 9 defaults to the legacy path; Grails 10 opts into Spring by default and Grails 11
     * removes the legacy response path.
     */
    Boolean useSpringJson

    /**
     * Shared by the renderers of one registry, so that falling back to the legacy converter is
     * reported once rather than for every response.
     */
    AtomicBoolean legacyFallbackReported = new AtomicBoolean()

    /** Shared by the renderers of one registry, like {@link #legacyFallbackReported}. */
    AtomicBoolean missingConvertersReported = new AtomicBoolean()

    /**
     * Shared by the renderers of one registry: each type, with the reason, that the legacy converter writes while Spring
     * JSON rendering is enabled is reported once, up to {@link #MAX_REPORTED_FALLBACKS} of them.
     */
    Set<String> springFallbacksReported = ConcurrentHashMap.newKeySet()

    /**
     * Shared by the renderers of one registry, like {@link #legacyFallbackReported}: a problem the legacy converter
     * writes is reported once, whether or not a legacy response was.
     */
    AtomicBoolean problemFallbackReported = new AtomicBoolean()

    /**
     * Resolved when a response is written rather than when this renderer is built, so that
     * obtaining the converters cannot force MVC initialization during bean creation.
     */
    Supplier<List<HttpMessageConverter<?>>> springHttpMessageConvertersSupplier
    /** Left unset, the registry's factory is used, or a default one outside a registry. */
    ValidationProblemDetailFactory validationProblemDetailFactory
    NamedJsonRenderer namedJsonRenderer
    GrailsJsonMapperCustomizer grailsJsonMapperCustomizer

    private volatile GrailsConventionGroovyPageLocator resolvedGroovyPageLocator

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
        // A problem is rendered only by the view respond names, as in any other format
        def viewName = object instanceof ProblemDetail ? context.arguments?.view?.toString() :
                (context.viewName ?: context.actionName)
        final view = viewName && !(object instanceof Errors) ?
                resolveGroovyPageLocator()?.findViewForFormat(context.controllerName, viewName, mimeType.extension) : null
        if (view) {
            // a name that is wrong is found where a view writes the response, as where this renderer writes it
            requireRegisteredConfiguration(context)
            // if a view is provided, we use the HTML renderer to return an appropriate model to the view; one an
            // application registers for problems renders their HTML page rather than the view respond names
            Renderer htmlRenderer = object instanceof ProblemDetail ? null : rendererRegistry?.findRenderer(MimeType.HTML, object)
            if (htmlRenderer == null) {
                htmlRenderer = DefaultHtmlRenderer.forFormatView(targetType, rendererRegistry, encoding)
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
     * The named JSON configuration {@code respond} selects, which must be registered: also for a problem, which Spring's
     * message converters write whatever the configuration, and for a view, so that a name that is wrong is found
     * whatever writes the response.
     */
    private String requireRegisteredConfiguration(RenderContext context) {
        String selectedConfiguration = context.arguments?.get('jsonConfiguration')?.toString()
        if (selectedConfiguration && (namedJsonRenderer == null || !namedJsonRenderer.contains(selectedConfiguration))) {
            throw new IllegalArgumentException("Named JSON configuration [$selectedConfiguration] is not registered.")
        }
        selectedConfiguration
    }

    /**
     * Subclasses should override to customize JSON response rendering. A problem is written as Spring MVC writes it,
     * whatever the renderer's own settings, unless a subclass renders it itself.
     *
     * @param object
     * @param context
     */
    protected void renderJson(T object, RenderContext context) {
        String selectedConfiguration = requireRegisteredConfiguration(context)
        // validation errors are written as the problem they make, as a problem is
        if (selectedConfiguration && !(object instanceof ProblemDetail || object instanceof Errors)) {
            if (context instanceof ServletRenderContext) {
                // written by Spring MVC, with the application's response body advice, as any other JSON respond writes
                SpringResponseBody.write(object, (ServletRenderContext) context, new NamedJsonConfigurationConverter(
                        namedJsonRenderer, selectedConfiguration, getEffectiveIncludes(context), getEffectiveExcludes(context)),
                        MediaType.parseMediaType(resolveMimeType(context).name))
                return
            }
            try {
                namedJsonRenderer.render(selectedConfiguration, object, context.writer,
                        getEffectiveIncludes(context), getEffectiveExcludes(context))
            }
            catch (JacksonException | IOException e) {
                // as respond reports it without a configuration, as Spring MVC does
                throw JacksonWriteFailures.reported(e)
            }
            return
        }
        String springFallbackReason = null
        if (object instanceof ProblemDetail || (object instanceof Errors && canUseSpringConverter(context))) {
            // validation errors are written as the problem they make, with the status it carries
            ProblemDetail problem = object instanceof Errors ?
                    (validationProblemDetailFactory ?: DEFAULT_PROBLEM_DETAIL_FACTORY).create((Errors) object, errorsHttpStatus) :
                    (ProblemDetail) object
            Integer status = object instanceof Errors ? null : ProblemDetailResponse.requestedStatus(context)
            if (context instanceof ServletRenderContext &&
                    ProblemDetailResponse.write(problem, (ServletRenderContext) context, status, [MediaType.APPLICATION_PROBLEM_JSON],
                            springHttpMessageConvertersSupplier?.get())) {
                return
            }
            if (object instanceof ProblemDetail) {
                // the legacy converter writes it as a bean, in the response Spring MVC would have sent
                ProblemDetailResponse.prepare((ProblemDetail) object, context)
                context.setContentType(GrailsWebUtil.getContentType(PROBLEM_JSON.name, encoding))
            }
            springFallbackReason = "no Spring message converter writes [${problem.getClass().name}] as ${MediaType.APPLICATION_PROBLEM_JSON}"
        }
        else if (canUseSpringConverter(context)) {
            springFallbackReason = writeWithSpringConverter(object, MediaType.parseMediaType(resolveMimeType(context).name), context)
            if (springFallbackReason == null) {
                return
            }
        }

        reportLegacyFallback(object, springFallbackReason)
        JSON converter
        // A jsonConfiguration argument names a configuration of the JSON mapper, which the legacy converter does not have
        if (namedConfiguration) {
            JSON.use(namedConfiguration) {
                converter = new JSON(object)
            }
        } else {
            converter = new JSON(object)
        }
        renderJson(converter, context)
    }

    private void reportLegacyFallback(Object object, String springFallbackReason) {
        if (object instanceof ProblemDetail && springFallbackReason != null) {
            if (problemFallbackReported.compareAndSet(false, true)) {
                log.warn("A ProblemDetail is written through Spring's message converters, but ${springFallbackReason}, " +
                        'so the legacy JSON converter writes it as a bean.')
            }
            return
        }
        boolean first = legacyFallbackReported.compareAndSet(false, true)
        if (useSpringJson == Boolean.TRUE) {
            if (springFallbackReason != null || namedConfiguration) {
                // each type the legacy converter writes, for each reason, so that another one is not missed
                String type = object?.getClass()?.name
                String reason = springFallbackReason ?: "this renderer uses the legacy named configuration [${namedConfiguration}]"
                if (reportsSpringFallback("${type}: ${reason}")) {
                    log.warn("Spring JSON rendering is enabled, but [${type}] is written by the legacy JSON converter, " +
                            "which is deprecated and will be removed in Grails 11, because ${reason}.")
                }
            }
            return
        }
        if (!first) {
            return
        }
        log.warn('Legacy respond() JSON rendering is deprecated and will be removed in Grails 11. ' +
                'Migrate to Jackson serializers and set grails.web.rendering.json.spring=true. ' +
                'Spring JSON becomes the default in Grails 10.')
    }

    /** Whether a fallback is reported: the first time for it, until {@link #MAX_REPORTED_FALLBACKS} have been. */
    private boolean reportsSpringFallback(String fallback) {
        if (springFallbacksReported.size() >= MAX_REPORTED_FALLBACKS || !springFallbacksReported.add(fallback)) {
            return false
        }
        if (springFallbacksReported.size() == MAX_REPORTED_FALLBACKS) {
            log.warn("The legacy JSON converter has written ${MAX_REPORTED_FALLBACKS} types while Spring JSON rendering is " +
                    'enabled; further types are not reported.')
        }
        return true
    }

    /**
     * The includes the projection and the legacy converter apply. A subclass that carries its own
     * includes returns them here, so both response paths honour them.
     */
    protected List<String> getEffectiveIncludes(RenderContext context) {
        return context.includes
    }

    /** The excludes the projection and the legacy converter apply; see {@link #getEffectiveIncludes}. */
    protected List<String> getEffectiveExcludes(RenderContext context) {
        return context.excludes
    }

    private List<HttpMessageConverter<?>> resolveSpringHttpMessageConverters() {
        return springHttpMessageConvertersSupplier?.get() ?: []
    }

    private boolean canUseSpringConverter(RenderContext context) {
        if (useSpringJson == Boolean.TRUE && !resolveSpringHttpMessageConverters() &&
                missingConvertersReported.compareAndSet(false, true)) {
            log.warn('Spring JSON rendering is enabled but no MVC message converters are available; using legacy JSON.')
        }
        return useSpringJson == Boolean.TRUE && resolveSpringHttpMessageConverters() && !namedConfiguration
    }

    /**
     * Writes a value as Spring MVC writes the value a {@code @ResponseBody} handler method returns, with its response
     * body handler and the application's {@code ResponseBodyAdvice} beans: with the first converter that writes it in
     * JSON, or, where that is Spring's standard Jackson converter, with a converter that writes it with the Grails mapper
     * derived from that converter's, in its types and with its prefix, and with the projection of the response.
     *
     * @return {@code null} once the value is written, otherwise why the Spring converters could not write it
     */
    private String writeWithSpringConverter(Object object, MediaType mediaType, RenderContext context) {
        if (!(context instanceof ServletRenderContext)) {
            return 'Spring MVC writes a response to a servlet request'
        }
        MediaType converterType = mediaType.type == 'text' && mediaType.subtype == 'json' ? MediaType.APPLICATION_JSON : mediaType
        Class<?> objectType = object?.getClass() ?: Object
        HttpMessageConverter<Object> converter = (HttpMessageConverter<Object>) resolveSpringHttpMessageConverters().find {
            HttpMessageConverter<?> candidate -> candidate.canWrite(objectType, converterType) &&
                    candidate.getSupportedMediaTypes(objectType).any { MediaType supported ->
                        supported.subtype == 'json' || supported.subtype.endsWith('+json')
                    }
        }
        if (converter == null) {
            return "no Spring message converter writes [${objectType.name}] as ${converterType}"
        }
        List<String> includes = getEffectiveIncludes(context)
        List<String> excludes = getEffectiveExcludes(context)

        // Only adapt the standard converter. Application converter subclasses and per-type
        // mappers retain their own serialization contract.
        boolean grailsMapper = grailsJsonMapperCustomizer != null &&
                converter.getClass() == JacksonJsonHttpMessageConverter &&
                !((JacksonJsonHttpMessageConverter) converter).getMappersForType(objectType)
        if ((includes != null || excludes) && !grailsMapper) {
            // Only the Grails mapper applies a projection; the legacy converter applies it otherwise.
            return 'includes and excludes need the Grails JSON mapper, which the application\'s message converter replaces'
        }
        if (grailsMapper) {
            JacksonJsonHttpMessageConverter source = (JacksonJsonHttpMessageConverter) converter
            JsonProjection projection = JsonProjection.of(object, includes, excludes)
            GrailsMapperJsonConverter grails = new GrailsMapperJsonConverter(source,
                    grailsJsonMapperCustomizer.forGrails(source.mapper),
                    prefixOf(source, new MediaType(converterType, StandardCharsets.UTF_8)), projection)
            SpringResponseBody.write(object, (ServletRenderContext) context, grails, mediaType)
            projection?.reportUnapplied()
            return null
        }
        SpringResponseBody.write(object, (ServletRenderContext) context, converter, converterType)
        return null
    }

    /**
     * What a converter writes before a value, such as a JSON prefix, which it has no accessor for, read once for each
     * converter, as its prefix is configuration: what it writes for a marker, less the JSON its mapper writes for the
     * marker, which includes the wrapper of a root value.
     */
    private static String prefixOf(JacksonJsonHttpMessageConverter converter, MediaType contentType) {
        return PREFIXES.computeIfAbsent(converter) { JacksonJsonHttpMessageConverter probed -> probePrefix(probed, contentType) }
    }

    private static String probePrefix(JacksonJsonHttpMessageConverter converter, MediaType contentType) {
        ByteArrayOutputStream written = new ByteArrayOutputStream()
        converter.write(PrefixMarker.INSTANCE, contentType, outputMessage(written))
        String output = written.toString(StandardCharsets.UTF_8)
        String marker = converter.mapper.writeValueAsString(PrefixMarker.INSTANCE)
        if (!output.endsWith(marker)) {
            throw new IllegalStateException("[${converter.getClass().name}] writes [${output}] for a value its mapper " +
                    "writes as [${marker}].")
        }
        return output.substring(0, output.length() - marker.length())
    }

    private static HttpOutputMessage outputMessage(OutputStream body) {
        return new HttpOutputMessage() {
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
    }

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

    private MimeType resolveMimeType(RenderContext context) {
        MimeType mimeType = context.acceptMimeType
        return mimeType == null || mimeType == MimeType.ALL ? MimeType.JSON : mimeType
    }

    /**
     * Writes a response with the legacy {@link JSON} converter.
     *
     * @deprecated Scheduled for removal in Grails 11, with the legacy {@code respond()} JSON path. Write the value with a
     * Jackson serializer, which {@code grails.web.rendering.json.spring} uses.
     */
    @Deprecated(since = '9.0', forRemoval = true)
    protected void renderJson(JSON converter, RenderContext context) {
        converter.setExcludes(getEffectiveExcludes(context))
        converter.setIncludes(getEffectiveIncludes(context))
        converter.render(context.getWriter())
    }

    /** A value a converter writes as {@code null}, whatever it writes around it. */
    private static final class PrefixMarker implements JacksonSerializable {

        static final PrefixMarker INSTANCE = new PrefixMarker()

        @Override
        void serialize(JsonGenerator generator, SerializationContext context) {
            generator.writeNull()
        }

        @Override
        void serializeWithType(JsonGenerator generator, SerializationContext context, TypeSerializer typeSerializer) {
            generator.writeNull()
        }
    }
}
