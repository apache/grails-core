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
import java.util.concurrent.atomic.AtomicBoolean

import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.context.support.StaticMessageSource
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.HttpMessageNotWritableException
import org.springframework.http.converter.ByteArrayHttpMessageConverter
import org.springframework.http.converter.StringHttpMessageConverter
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.PropertyNamingStrategies
import org.grails.web.converters.jackson.GrailsJsonMapperCustomizer
import org.springframework.http.ProblemDetail
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.Errors
import org.springframework.validation.FieldError
import org.springframework.validation.ObjectError
import org.springframework.web.context.request.RequestContextHolder

import grails.converters.JSON
import grails.core.DefaultGrailsApplication
import grails.rest.render.RenderContext
import grails.rest.render.errors.ValidationProblemDetailFactory
import grails.rest.render.json.JsonCollectionRenderer
import grails.util.GrailsWebMockUtil
import grails.web.render.NamedJsonRenderer
import org.grails.plugins.web.rest.render.ServletRenderContext
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.converters.configuration.ConvertersConfigurationHolder
import org.grails.web.converters.configuration.ConvertersConfigurationInitializer
import org.grails.web.converters.exceptions.ConverterException

import spock.lang.Specification

import static java.nio.charset.StandardCharsets.UTF_8

class DefaultJsonRendererSpec extends Specification {

    void 'an explicit ProblemDetail controls the status content type and instance for status #status'() {
        given:
        def renderer = new DefaultJsonRenderer<Object>(Object)
        renderer.springHttpMessageConvertersSupplier = { [new JacksonJsonHttpMessageConverter()] }
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        def context = new ServletRenderContext(webRequest)
        context.resourcePath = '/books'

        when:
        renderer.render(ProblemDetail.forStatus(status), context)

        then:
        webRequest.response.status == status
        webRequest.response.contentType.startsWith('application/problem+json')
        webRequest.response.contentAsString.contains('"instance":"/books"')

        where: 'a problem may carry a status that has no HttpStatus constant'
        status << [422, 499, 299, 599]
    }

    void 'the hooks of the legacy JSON path are deprecated for removal in Grails 11'() {
        expect:
        [DefaultJsonRenderer.getDeclaredField('namedConfiguration').getAnnotation(Deprecated),
         DefaultJsonRenderer.getDeclaredMethod('renderJson', JSON, RenderContext).getAnnotation(Deprecated),
         JsonCollectionRenderer.getDeclaredMethod('renderJson', JSON, RenderContext).getAnnotation(Deprecated)].every {
            it.forRemoval() && it.since() == '9.0'
        }
    }

    void 'text JSON uses Jackson and preserves source converter prefixes'() {
        given:
        def source = new JacksonJsonHttpMessageConverter()
        source.setPrefixJson(true)
        def renderer = new DefaultJsonRenderer<Object>(Object)
        renderer.useSpringJson = true
        renderer.grailsJsonMapperCustomizer = new GrailsJsonMapperCustomizer()
        renderer.springHttpMessageConvertersSupplier = { [source] }
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        def context = new ServletRenderContext(webRequest) {
            @Override
            grails.web.mime.MimeType getAcceptMimeType() { grails.web.mime.MimeType.TEXT_JSON }
        }

        when:
        renderer.render([65, 66] as byte[], context)

        then:
        webRequest.response.contentAsString == ")]}'\u002c \"QUI=\""
        webRequest.response.contentType.startsWith('text/json')
        !renderer.legacyFallbackReported.get()
    }

    void 'Spring JSON without MVC converters falls back to the legacy converter'() {
        given:
        def renderer = new DefaultJsonRenderer<Object>(Object)
        renderer.useSpringJson = true
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()

        when:
        String log = stderrWhile { renderer.render([title: 'Grails'], new ServletRenderContext(webRequest)) }

        then:
        webRequest.response.contentAsString == '{"title":"Grails"}'
        renderer.legacyFallbackReported.get()
        log.contains('Spring JSON rendering is enabled but no MVC message converters are available; using legacy JSON.')
    }

    void 'the missing converters warning is reported once for the renderers that share its flag'() {
        given:
        def shared = new java.util.concurrent.atomic.AtomicBoolean()
        def renderers = (1..2).collect {
            new DefaultJsonRenderer<Object>(Object).tap {
                useSpringJson = true
                missingConvertersReported = shared
            }
        }

        when:
        String log = stderrWhile {
            renderers.each { it.render([title: 'Grails'], new ServletRenderContext(GrailsWebMockUtil.bindMockWebRequest())) }
        }

        then:
        log.readLines().count { it.contains('no MVC message converters are available') } == 1
    }

    void 'a ProblemDetail with no MVC converters names why the legacy converter wrote it'() {
        given:
        def renderer = new DefaultJsonRenderer<Object>(Object)
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()

        when:
        String log = stderrWhile {
            renderer.render(org.springframework.http.ProblemDetail.forStatus(409), new ServletRenderContext(webRequest))
        }

        then:
        log.contains("A ProblemDetail is written through Spring's message converters, but no Spring message converter writes")
        log.contains('so the legacy JSON converter writes it as a bean')
    }

    void 'a ProblemDetail the legacy converter writes and a legacy response are each reported, whichever comes first'() {
        given:
        def renderer = new DefaultJsonRenderer<Object>(Object)
        def values = [[title: 'Grails'], ProblemDetail.forStatus(409)]

        when:
        String log = stderrWhile {
            (problemFirst ? values.reverse() : values).each { value ->
                renderer.render(value, new ServletRenderContext(GrailsWebMockUtil.bindMockWebRequest()))
            }
        }

        then:
        log.contains(LEGACY_RESPOND_WARNING)
        log.contains('so the legacy JSON converter writes it as a bean')

        where:
        problemFirst << [false, true]
    }

    void 'with Spring JSON on, a legacy fallback says why rather than asking for the setting again'() {
        given: 'an application converter, which cannot apply a projection'
        def converter = Mock(HttpMessageConverter) {
            canWrite(ProjectionBody, MediaType.APPLICATION_JSON) >> true
            getSupportedMediaTypes(_ as Class) >> [MediaType.APPLICATION_JSON]
        }
        def renderer = new DefaultJsonRenderer<ProjectionBody>(ProjectionBody)
        renderer.useSpringJson = true
        renderer.grailsJsonMapperCustomizer = new GrailsJsonMapperCustomizer()
        renderer.springHttpMessageConvertersSupplier = { [converter] }
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()

        when:
        String log = stderrWhile {
            renderer.render(new ProjectionBody(title: 'Included', hidden: true),
                    new ServletRenderContext(webRequest, [includes: ['title']]))
        }

        then:
        log.contains('Spring JSON rendering is enabled, but [' + ProjectionBody.name + '] is written by the legacy JSON converter')
        log.contains('includes and excludes need the Grails JSON mapper')
        !log.contains('set grails.web.rendering.json.spring=true')
    }

    void 'unknown named JSON configurations have the same error as render json'() {
        given:
        def renderer = new DefaultJsonRenderer<Object>(Object)
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()

        when:
        renderer.render([ok: true], new ServletRenderContext(webRequest, [jsonConfiguration: 'missing']))

        then:
        def error = thrown(IllegalArgumentException)
        error.message == 'Named JSON configuration [missing] is not registered.'
    }

    void setup() {
        new ConvertersConfigurationInitializer(grailsApplication: new DefaultGrailsApplication()).initialize()
    }

    void cleanup() {
        ConvertersConfigurationHolder.clear()
        RequestContextHolder.resetRequestAttributes()
    }

    void 'legacy rendering writes a JSON string with configuration #configuration'() {
        given:
        def renderer = new DefaultJsonRenderer<Object>(Object)
        renderer.namedConfiguration = configuration
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()

        when:
        renderer.render('ok', new ServletRenderContext(webRequest))

        then:
        webRequest.response.contentAsString == '"ok"'

        where:
        configuration << [null, 'deep']
    }

    void 'real Spring converters serialize #value as JSON instead of raw content'() {
        given:
        def builder = JsonMapper.builder()
        new GrailsJsonMapperCustomizer().customize(builder)
        def renderer = new DefaultJsonRenderer<Object>(Object)
        renderer.useSpringJson = true
        renderer.springHttpMessageConvertersSupplier = { [new ByteArrayHttpMessageConverter(),
                new StringHttpMessageConverter(), new JacksonJsonHttpMessageConverter(builder)] }
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()

        when:
        renderer.render(value, new ServletRenderContext(webRequest))

        then:
        webRequest.response.contentAsString == expected

        where:
        value                           | expected
        'ok'                            | '"ok"'
        "Saved ${'Grails'}"              | '"Saved Grails"'
        [message: "Saved ${'Grails'}"]   | '{"message":"Saved Grails"}'
        [65, 66] as byte[]               | '"QUI="'
    }

    void 'Jackson bytes decode correctly with a non UTF response encoding'() {
        given:
        def renderer = new DefaultJsonRenderer<Object>(Object)
        renderer.useSpringJson = true
        renderer.encoding = 'ISO-8859-1'
        renderer.springHttpMessageConvertersSupplier = { [new JacksonJsonHttpMessageConverter()] }
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()

        when:
        renderer.render([title: 'café'], new ServletRenderContext(webRequest))

        then:
        webRequest.response.contentAsString == '{"title":"café"}'
        webRequest.response.characterEncoding == 'ISO-8859-1'
    }

    void 'repeated Grails conversion keeps each source converter mapper configuration'() {
        given:
        def first = new JacksonJsonHttpMessageConverter(JsonMapper.builder().build())
        def second = new JacksonJsonHttpMessageConverter(JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.UPPER_CAMEL_CASE).build())
        def renderer = new DefaultJsonRenderer<Object>(Object)
        renderer.useSpringJson = true
        renderer.grailsJsonMapperCustomizer = new GrailsJsonMapperCustomizer()
        expect:
        [first, second, first, second].every { source ->
            def webRequest = GrailsWebMockUtil.bindMockWebRequest()
            renderer.springHttpMessageConvertersSupplier = { [source] }
            renderer.render(new ProjectionBody(title: 'Grails', hidden: true), new ServletRenderContext(webRequest))
            webRequest.response.contentAsString.contains(source.is(first) ? '"title":"Grails"' : '"Title":"Grails"')
        }
    }

    void 'selects the first MVC converter that can write the negotiated type'() {
        given:
        def first = Mock(HttpMessageConverter) {
            getSupportedMediaTypes(_ as Class) >> [MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON]
        }
        def selected = Mock(HttpMessageConverter) {
            getSupportedMediaTypes(_ as Class) >> [MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON]
        }
        def renderer = new DefaultJsonRenderer<Map>(Map)
        renderer.useSpringJson = true
        renderer.springHttpMessageConvertersSupplier = { [first, selected] }
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()

        when:
        renderer.render([title: 'Spring'], new ServletRenderContext(webRequest))

        then:
        1 * first.canWrite(LinkedHashMap, MediaType.APPLICATION_JSON) >> false
        1 * selected.canWrite(LinkedHashMap, MediaType.APPLICATION_JSON) >> true
        1 * selected.write(_, new MediaType(MediaType.APPLICATION_JSON, UTF_8), _) >> { arguments ->
            arguments[2].body.write('{"title":"MVC"}'.bytes)
        }
        webRequest.response.contentAsString == '{"title":"MVC"}'
    }

    void 'falls back to the legacy converter when MVC cannot write the response'() {
        given:
        def converter = Mock(HttpMessageConverter) {
            getSupportedMediaTypes(_ as Class) >> [MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON]
        }
        def renderer = new DefaultJsonRenderer<Map>(Map)
        renderer.useSpringJson = true
        renderer.springHttpMessageConvertersSupplier = { [converter] }
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()

        when:
        renderer.render([title: 'Legacy'], new ServletRenderContext(webRequest))

        then:
        1 * converter.canWrite(LinkedHashMap, MediaType.APPLICATION_JSON) >> false
        0 * converter.write(_, _, _)
        webRequest.response.contentAsString == '{"title":"Legacy"}'
    }

    void 'a projection the selected converter cannot apply keeps the legacy converter path'() {
        given: 'an application converter, which Grails does not adapt to its mapper'
        def converter = Mock(HttpMessageConverter) {
            canWrite(ProjectionBody, MediaType.APPLICATION_JSON) >> true
            getSupportedMediaTypes(_ as Class) >> [MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON]
        }
        def renderer = new DefaultJsonRenderer<ProjectionBody>(ProjectionBody)
        renderer.useSpringJson = true
        renderer.grailsJsonMapperCustomizer = new GrailsJsonMapperCustomizer()
        renderer.springHttpMessageConvertersSupplier = { [converter] }
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()

        when:
        renderer.render(new ProjectionBody(title: 'Included', hidden: true),
                new ServletRenderContext(webRequest, [includes: ['title']]))

        then:
        0 * converter.write(_, _, _)
        webRequest.response.contentAsString == '{"title":"Included"}'
    }

    void 'a per-response projection is applied by the Grails mapper on the Spring path'() {
        given:
        def renderer = new DefaultJsonRenderer<ProjectionBody>(ProjectionBody)
        renderer.useSpringJson = true
        renderer.grailsJsonMapperCustomizer = new GrailsJsonMapperCustomizer()
        renderer.springHttpMessageConvertersSupplier = { [new JacksonJsonHttpMessageConverter()] }
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()

        when:
        renderer.render(new ProjectionBody(title: 'Included', hidden: true),
                new ServletRenderContext(webRequest, [excludes: ['hidden']]))

        then:
        webRequest.response.contentAsString == '{"title":"Included"}'
        !renderer.legacyFallbackReported.get()
    }

    void 'respond selects a registered named JSON configuration'() {
        given:
        def namedRenderer = Mock(NamedJsonRenderer)
        def renderer = new DefaultJsonRenderer<Map>(Map)
        renderer.namedJsonRenderer = namedRenderer
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()

        when:
        renderer.render([title: 'Named'], new ServletRenderContext(webRequest, [jsonConfiguration: 'deep']))

        then:
        1 * namedRenderer.contains('deep') >> true
        1 * namedRenderer.render('deep', [title: 'Named'], _, null, null) >> { arguments ->
            arguments[2].write('{"configured":true}')
        }
        webRequest.response.contentAsString == '{"configured":true}'
    }

    void 'with Spring JSON on, validation errors render as problem JSON through Spring conversion'() {
        given:
        def converter = Mock(HttpMessageConverter) {
            getSupportedMediaTypes(_ as Class) >> [MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON]
        }
        def renderer = new DefaultJsonRenderer<Errors>(Errors)
        renderer.useSpringJson = true
        renderer.springHttpMessageConvertersSupplier = { [converter] }
        def errors = new BeanPropertyBindingResult(new Object(), 'book')
        errors.addError(new FieldError('book', 'title', 'secret', false,
                ['book.title.blank'] as String[], null, 'Title must not be blank'))
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()

        when:
        renderer.render(errors, new ServletRenderContext(webRequest))

        then:
        1 * converter.canWrite(ProblemDetail, MediaType.APPLICATION_PROBLEM_JSON) >> true
        1 * converter.write({ ProblemDetail problem ->
            problem.status == 422 && !problem.properties.errors.first().containsKey('rejectedValue')
        }, new MediaType(MediaType.APPLICATION_PROBLEM_JSON, UTF_8), _) >> { arguments ->
            arguments[2].body.write('{"status":422,"title":"Validation failed"}'.bytes)
        }
        webRequest.response.status == 422
        webRequest.response.contentType == 'application/problem+json;charset=UTF-8'
        webRequest.response.contentAsString == '{"status":422,"title":"Validation failed"}'
    }

    void 'the problem content type is set before the body is written, which can commit the response'() {
        given:
        def converter = Mock(HttpMessageConverter) {
            getSupportedMediaTypes(_ as Class) >> [MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON]
        }
        def renderer = new DefaultJsonRenderer<Errors>(Errors)
        renderer.useSpringJson = true
        renderer.springHttpMessageConvertersSupplier = { [converter] }
        def errors = new BeanPropertyBindingResult(new Object(), 'book')
        errors.addError(new ObjectError('book', ['book.invalid'] as String[], null, 'Book is invalid'))
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        String contentTypeWhenWritten = null

        when:
        renderer.render(errors, new ServletRenderContext(webRequest))

        then:
        1 * converter.canWrite(ProblemDetail, MediaType.APPLICATION_PROBLEM_JSON) >> true
        1 * converter.write(_, new MediaType(MediaType.APPLICATION_PROBLEM_JSON, UTF_8), _) >> { arguments ->
            contentTypeWhenWritten = webRequest.response.contentType
            arguments[2].body.write('{"status":422}'.bytes)
        }
        contentTypeWhenWritten == 'application/problem+json;charset=UTF-8'
    }

    void 'a write that fails part-way leaves the response uncommitted, so that the error page applies'() {
        given:
        def converter = Mock(HttpMessageConverter) {
            getSupportedMediaTypes(_ as Class) >> [MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON]
            canWrite(ProblemDetail, MediaType.APPLICATION_PROBLEM_JSON) >> true
            write(_, _, _) >> { arguments ->
                arguments[2].body.write('{"status":'.bytes)
                throw new HttpMessageNotWritableException('failed part-way')
            }
        }
        def renderer = new DefaultJsonRenderer<Object>(Object)
        renderer.springHttpMessageConvertersSupplier = { [converter] }
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()

        when:
        renderer.render(ProblemDetail.forStatus(409), new ServletRenderContext(webRequest))

        then:
        thrown(HttpMessageNotWritableException)
        !webRequest.response.committed
    }

    void 'the negotiated content type is restored when no converter can write the problem'() {
        given:
        def converter = Mock(HttpMessageConverter) {
            getSupportedMediaTypes(_ as Class) >> [MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON]
        }
        def renderer = new DefaultJsonRenderer<Errors>(Errors)
        renderer.useSpringJson = true
        renderer.springHttpMessageConvertersSupplier = { [converter] }
        def errors = new BeanPropertyBindingResult(new Object(), 'book')
        errors.addError(new ObjectError('book', ['book.invalid'] as String[], null, 'Book is invalid'))
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()

        when:
        try {
            renderer.render(errors, new ServletRenderContext(webRequest))
        }
        catch (ConverterException ignored) {
            // The legacy fallback needs the errors marshaller, which this slice does not
            // configure. The content type has already been restored by the time it runs.
        }

        then:
        1 * converter.canWrite(ProblemDetail, MediaType.APPLICATION_PROBLEM_JSON) >> false
        0 * converter.write(_, _, _)

        and: "the legacy converter path reports ordinary JSON, not problem JSON"
        webRequest.response.contentType == 'application/json;charset=UTF-8'
    }

    void 'a projection reaches the named configuration rather than being dropped'() {
        given:
        def namedRenderer = Mock(NamedJsonRenderer)
        def renderer = new DefaultJsonRenderer<Map>(Map)
        renderer.namedJsonRenderer = namedRenderer
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()

        when: "a response asks for both a named configuration and a projection"
        renderer.render([title: 'Named'], new ServletRenderContext(webRequest,
                [jsonConfiguration: 'deep', includes: ['title'], excludes: ['hidden']]))

        then: "the projection is passed on, not discarded"
        1 * namedRenderer.contains('deep') >> true
        1 * namedRenderer.render('deep', [title: 'Named'], _, ['title'], ['hidden']) >> { arguments ->
            arguments[2].write('{"title":"Named"}')
        }
    }

    void 'a non UTF-8 encoding round-trips through the Spring converter'() {
        given: "a renderer configured with a non UTF-8 encoding"
        def converter = Mock(HttpMessageConverter) {
            getSupportedMediaTypes(_ as Class) >> [MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON]
        }
        def renderer = new DefaultJsonRenderer<Object>(Object)
        renderer.useSpringJson = true
        renderer.encoding = 'ISO-8859-1'
        renderer.springHttpMessageConvertersSupplier = { [converter] }
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()

        when:
        renderer.render([title: 'Cafe\u0301'], new ServletRenderContext(webRequest))

        then: "the configured charset is handed to the converter"
        1 * converter.canWrite(_, MediaType.APPLICATION_JSON) >> true
        1 * converter.write(_, { MediaType mediaType ->
            mediaType.charset == Charset.forName('ISO-8859-1')
        }, _) >> { arguments ->
            // a converter honours the charset on the media type it is given
            arguments[2].body.write('{"title":"caf\u00e9"}'.getBytes(arguments[1].charset))
        }

        and: "the bytes it produced decode back to the same characters"
        webRequest.response.contentAsString == '{"title":"caf\u00e9"}'
    }

    void 'the problem body carries a resolved message, not a raw template'() {
        given: "a constraint-style error whose default message still has argument placeholders"
        LocaleContextHolder.locale = Locale.ENGLISH
        def converter = Mock(HttpMessageConverter) {
            getSupportedMediaTypes(_ as Class) >> [MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON]
        }
        def renderer = new DefaultJsonRenderer<Errors>(Errors)
        renderer.useSpringJson = true
        renderer.springHttpMessageConvertersSupplier = { [converter] }
        renderer.validationProblemDetailFactory =
                new ValidationProblemDetailFactory(false, new StaticMessageSource())
        def errors = new BeanPropertyBindingResult(new Object(), 'book')
        errors.addError(new FieldError('book', 'title', null, false,
                ['book.title.nullable'] as String[], ['title'] as Object[],
                'Property [{0}] cannot be null'))
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()

        when:
        renderer.render(errors, new ServletRenderContext(webRequest))

        then:
        1 * converter.canWrite(ProblemDetail, MediaType.APPLICATION_PROBLEM_JSON) >> true
        1 * converter.write({ ProblemDetail problem ->
            problem.properties.errors.first().message == 'Property [title] cannot be null'
        }, _, _) >> { arguments -> arguments[2].body.write('{}'.bytes) }

        cleanup:
        LocaleContextHolder.resetLocaleContext()
    }

    void 'the problem body reports the same status the response is sent with'() {
        given: "a renderer configured to answer validation failures with 400"
        def converter = Mock(HttpMessageConverter) {
            getSupportedMediaTypes(_ as Class) >> [MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON]
        }
        def renderer = new DefaultJsonRenderer<Errors>(Errors)
        renderer.useSpringJson = true
        renderer.springHttpMessageConvertersSupplier = { [converter] }
        renderer.errorsHttpStatus = HttpStatus.BAD_REQUEST
        def errors = new BeanPropertyBindingResult(new Object(), 'book')
        errors.addError(new ObjectError('book', ['book.invalid'] as String[], null, 'Book is invalid'))
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()

        when:
        renderer.render(errors, new ServletRenderContext(webRequest))

        then:
        1 * converter.canWrite(ProblemDetail, MediaType.APPLICATION_PROBLEM_JSON) >> true
        1 * converter.write({ ProblemDetail problem -> problem.status == 400 },
                new MediaType(MediaType.APPLICATION_PROBLEM_JSON, UTF_8), _) >> { arguments ->
            arguments[2].body.write('{"status":400}'.bytes)
        }
        webRequest.response.status == 400
    }

    void 'without a Spring JSON setting the legacy converter writes the response and warns'() {
        given:
        def renderer = new DefaultJsonRenderer<Object>(Object)
        renderer.springHttpMessageConvertersSupplier = { [new JacksonJsonHttpMessageConverter()] }

        when: 'two responses are written'
        String log = stderrWhile {
            2.times { renderer.render([65, 66] as byte[], new ServletRenderContext(GrailsWebMockUtil.bindMockWebRequest())) }
        }

        then:
        GrailsWebRequest.lookup().response.contentAsString == '[65,66]'
        renderer.legacyFallbackReported.get()

        and: 'the deprecation is reported once, naming the release that changes the default and the one that removes it'
        log.readLines().count { it.contains(LEGACY_RESPOND_WARNING) } == 1
        log.contains('Migrate to Jackson serializers and set grails.web.rendering.json.spring=true. ' +
                'Spring JSON becomes the default in Grails 10.')
    }

    void 'without a Spring JSON setting a registered JSON marshaller keeps the legacy converter'() {
        given:
        JSON.registerObjectMarshaller(ProjectionBody) { ProjectionBody body -> [legacy: body.title] }
        def reported = new AtomicBoolean()
        def renderers = (1..2).collect {
            new DefaultJsonRenderer<Object>(Object).tap {
                springHttpMessageConvertersSupplier = { [new JacksonJsonHttpMessageConverter()] }
                legacyFallbackReported = reported
            }
        }

        expect: 'both renderers apply the marshaller and share the report of the fallback'
        renderers.every { renderer ->
            def webRequest = GrailsWebMockUtil.bindMockWebRequest()
            renderer.render(new ProjectionBody(title: 'Grails'), new ServletRenderContext(webRequest))
            webRequest.response.contentAsString == '{"legacy":"Grails"}'
        }
        reported.get()
    }

    void 'Spring JSON setting #setting decides over a registered JSON marshaller'() {
        given:
        JSON.registerObjectMarshaller(ProjectionBody) { ProjectionBody body -> [legacy: body.title] }
        def renderer = new DefaultJsonRenderer<Object>(Object)
        renderer.useSpringJson = setting
        renderer.springHttpMessageConvertersSupplier = { [new JacksonJsonHttpMessageConverter()] }
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()

        when:
        renderer.render(new ProjectionBody(title: 'Grails'), new ServletRenderContext(webRequest))

        then:
        webRequest.response.contentAsString.contains(expected)
        renderer.legacyFallbackReported.get() == !setting

        where:
        setting | expected
        true    | '"title":"Grails"'
        false   | '"legacy":"Grails"'
    }

    private static final String LEGACY_RESPOND_WARNING =
            'Legacy respond() JSON rendering is deprecated and will be removed in Grails 11.'

    private static String stderrWhile(Closure<?> work) {
        PrintStream original = System.err
        def buffer = new ByteArrayOutputStream()
        System.setErr(new PrintStream(buffer, true))
        try {
            work.call()
        }
        finally {
            System.setErr(original)
        }
        buffer.toString()
    }
}

class ProjectionBody {
    String title
    boolean hidden
}
