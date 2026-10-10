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
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpOutputMessage
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.AbstractJacksonHttpMessageConverter
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.validation.Errors

import tools.jackson.core.JsonGenerator
import tools.jackson.databind.JacksonSerializable
import tools.jackson.databind.ObjectWriter
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.jsontype.TypeSerializer

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

    final Class<T> targetType
    MimeType[] mimeTypes = [MimeType.JSON, MimeType.TEXT_JSON] as MimeType[]

    @Value('${grails.converters.encoding:UTF-8}')
    String encoding = GrailsWebUtil.DEFAULT_ENCODING

    @Autowired(required = false)
    GrailsConventionGroovyPageLocator groovyPageLocator

    @Autowired(required = false)
    RendererRegistry rendererRegistry

    String namedConfiguration
    HttpStatus errorsHttpStatus = HttpStatus.UNPROCESSABLE_CONTENT

    /**
     * Whether responses are written by Spring's message converters rather than the legacy
     * {@link JSON} converter. Grails 9 defaults to the legacy path; Grails 10 opts into Spring
     * by default and Grails 11 removes the legacy response path.
     */
    Boolean useSpringJson = false

    /**
     * Shared by the renderers of one registry, so that falling back to the legacy converter is
     * reported once rather than for every response.
     */
    AtomicBoolean legacyFallbackReported = new AtomicBoolean()
    private final AtomicBoolean missingConvertersReported = new AtomicBoolean()
    List<HttpMessageConverter<?>> springHttpMessageConverters = []

    /**
     * Resolved when a response is written rather than when this renderer is built, so that
     * obtaining the converters cannot force MVC initialization during bean creation.
     */
    Supplier<List<HttpMessageConverter<?>>> springHttpMessageConvertersSupplier
    ValidationProblemDetailFactory validationProblemDetailFactory = new ValidationProblemDetailFactory()
    NamedJsonRenderer namedJsonRenderer
    GrailsJsonMapperCustomizer grailsJsonMapperCustomizer

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
        if (view && !(object instanceof Errors) && !(object instanceof ProblemDetail)) {
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
        if (selectedConfiguration) {
            if (namedJsonRenderer == null || !namedJsonRenderer.contains(selectedConfiguration)) {
                throw new IllegalArgumentException("Named JSON configuration [$selectedConfiguration] is not registered.")
            }
            namedJsonRenderer.render(selectedConfiguration, object, context.writer,
                    context.includes, context.excludes)
            return
        }
        if (object instanceof ProblemDetail || canUseSpringConverter(context)) {
            Object springValue = object
            if (object instanceof Errors) {
                springValue = validationProblemDetailFactory.create((Errors) object, errorsHttpStatus)
            }
            MediaType mediaType = springValue instanceof ProblemDetail ?
                    MediaType.parseMediaType(PROBLEM_JSON.name) :
                    MediaType.parseMediaType(resolveMimeType(context).name)
            // Set the content type before writing: once the writer flushes, the response is
            // committed and a later content type change is silently discarded.
            if (springValue instanceof ProblemDetail) {
                ProblemDetail problem = (ProblemDetail) springValue
                context.setStatus(HttpStatus.valueOf(problem.status))
                if (problem.instance == null && context.resourcePath) {
                    problem.instance = URI.create(context.resourcePath)
                }
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

        if (legacyFallbackReported.compareAndSet(false, true)) {
            log.warn('Legacy respond() JSON rendering is deprecated and will be removed in Grails 11. ' +
                    'Migrate to Jackson serializers and set grails.web.rendering.json.spring=true. ' +
                    'Spring JSON becomes the default in Grails 10.')
        }
        JSON converter
        String legacyConfiguration = selectedConfiguration ?: namedConfiguration
        if (legacyConfiguration) {
            JSON.use(legacyConfiguration) {
                converter = new JSON(object)
            }
        } else {
            converter = new JSON(object)
        }
        renderJson(converter, context)
    }

    private List<HttpMessageConverter<?>> resolveSpringHttpMessageConverters() {
        List<HttpMessageConverter<?>> supplied = springHttpMessageConvertersSupplier?.get()
        return supplied ?: springHttpMessageConverters
    }

    private boolean canUseSpringConverter(RenderContext context) {
        if (useSpringJson == Boolean.TRUE && !resolveSpringHttpMessageConverters() &&
                missingConvertersReported.compareAndSet(false, true)) {
            log.warn('Spring JSON rendering is enabled but no MVC message converters are available; using legacy JSON.')
        }
        return useSpringJson == Boolean.TRUE && resolveSpringHttpMessageConverters() && !namedConfiguration
    }

    private boolean renderWithSpringConverter(Object object, MediaType mediaType, RenderContext context) {
        if (mediaType.type == 'text' && mediaType.subtype == 'json') {
            mediaType = MediaType.APPLICATION_JSON
        }
        Class<?> objectType = object?.getClass() ?: Object
        HttpMessageConverter<Object> converter = (HttpMessageConverter<Object>) resolveSpringHttpMessageConverters().find {
            HttpMessageConverter<?> candidate -> candidate.canWrite(objectType, mediaType) &&
                    candidate.getSupportedMediaTypes(objectType).any { MediaType supported ->
                        supported.subtype == 'json' || supported.subtype.endsWith('+json')
                    }
        }
        if (converter == null) {
            return false
        }

        // Only adapt the standard converter. Application converter subclasses and per-type
        // mappers retain their own serialization contract.
        boolean grailsMapper = grailsJsonMapperCustomizer != null &&
                converter.getClass() == JacksonJsonHttpMessageConverter &&
                !((JacksonJsonHttpMessageConverter) converter).getMappersForType(objectType)
        if ((context.includes || context.excludes) && !grailsMapper) {
            // Only the Grails mapper applies a projection; the legacy converter applies it otherwise.
            return false
        }
        JsonProjection projection = null
        if (grailsMapper) {
            projection = JsonProjection.of(object, context.includes, context.excludes)
            // Keep the source converter itself: its prefix, media types and charset are application
            // configuration. Only the value's JSON representation uses the isolated Grails mapper.
            object = new GrailsJsonValue(object, grailsJsonMapperCustomizer.forGrails(
                    ((JacksonJsonHttpMessageConverter) converter).mapper), projection)
        }
        // Jackson only writes UTF encodings. Use UTF-8 for the intermediate byte stream;
        // the servlet writer still applies the configured response encoding.
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
        projection?.reportUnapplied()
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

    private static final class GrailsJsonValue implements JacksonSerializable {
        private final Object value
        private final JsonMapper mapper
        private final JsonProjection projection

        GrailsJsonValue(Object value, JsonMapper mapper, JsonProjection projection) {
            this.value = value
            this.mapper = mapper
            this.projection = projection
        }

        @Override
        void serialize(JsonGenerator generator, SerializationContext context) {
            ObjectWriter writer = mapper.writer()
            if (projection != null) {
                writer = writer.withAttribute(JsonProjection.ATTRIBUTE, projection)
            }
            writer.writeValue(generator, value)
        }

        @Override
        void serializeWithType(JsonGenerator generator, SerializationContext context, TypeSerializer typeSerializer) {
            serialize(generator, context)
        }
    }
}
