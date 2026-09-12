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

import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.context.support.StaticMessageSource
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.ProblemDetail
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.Errors
import org.springframework.validation.FieldError
import org.springframework.validation.ObjectError

import grails.core.DefaultGrailsApplication
import grails.rest.render.errors.ValidationProblemDetailFactory
import grails.util.GrailsWebMockUtil
import grails.web.render.NamedJsonRenderer
import org.grails.plugins.web.rest.render.ServletRenderContext
import org.grails.web.converters.configuration.ConvertersConfigurationHolder
import org.grails.web.converters.configuration.ConvertersConfigurationInitializer
import org.grails.web.converters.exceptions.ConverterException

import spock.lang.Specification

import static java.nio.charset.StandardCharsets.UTF_8

class DefaultJsonRendererSpec extends Specification {

    void setup() {
        new ConvertersConfigurationInitializer(grailsApplication: new DefaultGrailsApplication()).initialize()
    }

    void cleanup() {
        ConvertersConfigurationHolder.clear()
    }

    void 'selects the first MVC converter that can write the negotiated type'() {
        given:
        def first = Mock(HttpMessageConverter)
        def selected = Mock(HttpMessageConverter)
        def renderer = new DefaultJsonRenderer<Map>(Map)
        renderer.useSpringJson = true
        renderer.springHttpMessageConverters = [first, selected]
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
        def converter = Mock(HttpMessageConverter)
        def renderer = new DefaultJsonRenderer<Map>(Map)
        renderer.useSpringJson = true
        renderer.springHttpMessageConverters = [converter]
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()

        when:
        renderer.render([title: 'Legacy'], new ServletRenderContext(webRequest))

        then:
        1 * converter.canWrite(LinkedHashMap, MediaType.APPLICATION_JSON) >> false
        0 * converter.write(_, _, _)
        webRequest.response.contentAsString == '{"title":"Legacy"}'
    }

    void 'per-response projections retain the legacy converter path'() {
        given:
        def converter = Mock(HttpMessageConverter)
        def renderer = new DefaultJsonRenderer<ProjectionBody>(ProjectionBody)
        renderer.useSpringJson = true
        renderer.springHttpMessageConverters = [converter]
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()

        when:
        renderer.render(new ProjectionBody(title: 'Included', hidden: true),
                new ServletRenderContext(webRequest, [includes: ['title']]))

        then:
        0 * converter._
        webRequest.response.contentAsString == '{"title":"Included"}'
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

    void 'validation errors render as problem JSON through Spring conversion by default'() {
        given:
        def converter = Mock(HttpMessageConverter)
        def renderer = new DefaultJsonRenderer<Errors>(Errors)
        renderer.useSpringJson = true
        renderer.springHttpMessageConverters = [converter]
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

    void 'the problem content type survives a response committed while writing'() {
        given: "a converter whose write commits the response, as a large body would"
        def converter = Mock(HttpMessageConverter)
        def renderer = new DefaultJsonRenderer<Errors>(Errors)
        renderer.useSpringJson = true
        renderer.springHttpMessageConverters = [converter]
        def errors = new BeanPropertyBindingResult(new Object(), 'book')
        errors.addError(new ObjectError('book', ['book.invalid'] as String[], null, 'Book is invalid'))
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()

        when:
        renderer.render(errors, new ServletRenderContext(webRequest))

        then:
        1 * converter.canWrite(ProblemDetail, MediaType.APPLICATION_PROBLEM_JSON) >> true
        1 * converter.write(_, new MediaType(MediaType.APPLICATION_PROBLEM_JSON, UTF_8), _) >> { arguments ->
            arguments[2].body.write('{"status":422}'.bytes)
        }

        and: "the content type was set before the body was written, so it is not lost"
        webRequest.response.contentType == 'application/problem+json;charset=UTF-8'
    }

    void 'the negotiated content type is restored when no converter can write the problem'() {
        given:
        def converter = Mock(HttpMessageConverter)
        def renderer = new DefaultJsonRenderer<Errors>(Errors)
        renderer.useSpringJson = true
        renderer.springHttpMessageConverters = [converter]
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
        def converter = Mock(HttpMessageConverter)
        def renderer = new DefaultJsonRenderer<Object>(Object)
        renderer.useSpringJson = true
        renderer.encoding = 'ISO-8859-1'
        renderer.springHttpMessageConverters = [converter]
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
        def converter = Mock(HttpMessageConverter)
        def renderer = new DefaultJsonRenderer<Errors>(Errors)
        renderer.useSpringJson = true
        renderer.springHttpMessageConverters = [converter]
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
        def converter = Mock(HttpMessageConverter)
        def renderer = new DefaultJsonRenderer<Errors>(Errors)
        renderer.useSpringJson = true
        renderer.springHttpMessageConverters = [converter]
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
}

class ProjectionBody {
    String title
    boolean hidden
}
