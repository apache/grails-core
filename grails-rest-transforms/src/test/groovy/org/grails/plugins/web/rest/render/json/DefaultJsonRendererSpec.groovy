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

import groovy.json.JsonSlurper

import jakarta.servlet.ServletOutputStream
import jakarta.servlet.WriteListener

import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.context.support.StaticMessageSource
import org.springframework.core.ResolvableType
import org.springframework.http.HttpOutputMessage
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.HttpMessageNotWritableException
import org.springframework.http.converter.ByteArrayHttpMessageConverter
import org.springframework.http.converter.StringHttpMessageConverter
import org.springframework.http.converter.HttpMessageConversionException
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.mock.http.MockHttpOutputMessage
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import com.fasterxml.jackson.annotation.JsonUnwrapped
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule
import tools.jackson.databind.ser.std.StdSerializer
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.SerializationFeature
import org.grails.core.lifecycle.ShutdownOperations
import org.grails.web.converters.jackson.JacksonNamedJsonRenderer
import org.grails.web.converters.jackson.GrailsJsonMapperCustomizer
import org.springframework.http.ProblemDetail
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.Errors
import org.springframework.validation.FieldError
import org.springframework.validation.ObjectError
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.servlet.ModelAndView

import grails.converters.json.NamedJsonConfigurationRegistry
import grails.converters.JSON
import grails.core.DefaultGrailsApplication
import grails.rest.render.RenderContext
import grails.rest.render.Renderer
import grails.rest.render.RendererRegistry
import grails.rest.render.errors.ValidationProblemDetailFactory
import grails.rest.render.json.JsonCollectionRenderer
import grails.util.GrailsWebMockUtil
import grails.web.render.NamedJsonRenderer
import org.grails.plugins.web.rest.render.DefaultRendererRegistry
import org.grails.plugins.web.rest.render.ServletRenderContext
import org.grails.gsp.io.GroovyPageScriptSource
import org.grails.web.gsp.io.GrailsConventionGroovyPageLocator
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.GrailsApplicationAttributes
import org.grails.web.converters.configuration.ConvertersConfigurationHolder
import org.grails.web.converters.configuration.ConvertersConfigurationInitializer
import org.grails.web.converters.marshaller.json.ValidationErrorsMarshaller

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

    void 'a status argument of #argument decides the status of a problem response, as the status of a ResponseEntity does in Spring MVC'() {
        given:
        def renderer = new DefaultJsonRenderer<Object>(Object)
        renderer.springHttpMessageConvertersSupplier = { [new JacksonJsonHttpMessageConverter()] }
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()

        when:
        String log = stderrWhile {
            renderer.render(ProblemDetail.forStatus(409), new ServletRenderContext(webRequest, [status: argument]))
        }

        then: 'the problem keeps its own status, and Spring MVC reports the mismatch, as for a ResponseEntity'
        webRequest.response.status == 400
        webRequest.response.contentAsString.contains('"status":409')
        log.contains("but its status doesn't match the ProblemDetail status: 409")

        where:
        argument << [400, HttpStatus.BAD_REQUEST, '400']
    }

    void 'a status argument that matches the problem is not reported'() {
        given:
        def renderer = new DefaultJsonRenderer<Object>(Object)
        renderer.springHttpMessageConvertersSupplier = { [new JacksonJsonHttpMessageConverter()] }
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()

        when:
        String log = stderrWhile {
            renderer.render(ProblemDetail.forStatus(409), new ServletRenderContext(webRequest, [status: 409]))
        }

        then:
        webRequest.response.status == 409
        !log.contains("doesn't match the ProblemDetail status")
    }

    void 'a status argument does not change the status of the problem built for validation errors'() {
        given:
        def renderer = new DefaultJsonRenderer<Errors>(Errors)
        renderer.useSpringJson = true
        renderer.springHttpMessageConvertersSupplier = { [new JacksonJsonHttpMessageConverter()] }
        def errors = new BeanPropertyBindingResult(new Object(), 'book')
        errors.addError(new ObjectError('book', ['book.invalid'] as String[], null, 'Book is invalid'))
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()

        when:
        String log = stderrWhile {
            renderer.render(errors, new ServletRenderContext(webRequest, [status: 400]))
        }

        then:
        webRequest.response.status == 422
        webRequest.response.contentAsString.contains('"status":422')
        !log.contains("doesn't match the ProblemDetail status")
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

    void 'the prefix of a converter is read once for the converter, as the configuration it is'() {
        given:
        def source = new JacksonJsonHttpMessageConverter()
        source.jsonPrefix = 'first:'
        def renderer = new DefaultJsonRenderer<Object>(Object)
        renderer.useSpringJson = true
        renderer.grailsJsonMapperCustomizer = new GrailsJsonMapperCustomizer()
        renderer.springHttpMessageConvertersSupplier = { [source] }

        when: 'a value is written, and the converter is configured again before the next'
        def first = GrailsWebMockUtil.bindMockWebRequest()
        renderer.render([title: 'Grails'], new ServletRenderContext(first))
        source.jsonPrefix = 'second:'
        def second = GrailsWebMockUtil.bindMockWebRequest()
        renderer.render([title: 'Grails'], new ServletRenderContext(second))

        then: 'both are written with the prefix it had when the first was written'
        first.response.contentAsString == 'first:{"title":"Grails"}'
        second.response.contentAsString == 'first:{"title":"Grails"}'

        when: 'another converter writes it'
        def other = new JacksonJsonHttpMessageConverter()
        other.jsonPrefix = 'other:'
        renderer.springHttpMessageConvertersSupplier = { [other] }
        def third = GrailsWebMockUtil.bindMockWebRequest()
        renderer.render([title: 'Grails'], new ServletRenderContext(third))

        then: 'with its own prefix'
        third.response.contentAsString == 'other:{"title":"Grails"}'
    }

    void 'a source mapper that wraps root values names the wrapper after the value, after the prefix, as Spring MVC writes it'() {
        given:
        def mapper = JsonMapper.builder().enable(SerializationFeature.WRAP_ROOT_VALUE).build()
        def source = new JacksonJsonHttpMessageConverter(mapper)
        source.setPrefixJson(true)
        def renderer = new DefaultJsonRenderer<Object>(Object)
        renderer.useSpringJson = true
        renderer.grailsJsonMapperCustomizer = new GrailsJsonMapperCustomizer()
        renderer.springHttpMessageConvertersSupplier = { [source] }
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        def spring = new MockHttpOutputMessage()
        source.write(new ProjectionBody(title: 'Grails', hidden: true), new MediaType(MediaType.APPLICATION_JSON, UTF_8), spring)

        when:
        renderer.render(new ProjectionBody(title: 'Grails', hidden: true), new ServletRenderContext(webRequest))

        then:
        webRequest.response.contentAsString == spring.bodyAsString
        spring.bodyAsString.contains('{"ProjectionBody":')
    }

    void 'an application converter writes the value as it writes it, rather than the Grails mapper, with #description'() {
        given:
        def renderer = new DefaultJsonRenderer<Object>(Object)
        renderer.useSpringJson = true
        renderer.grailsJsonMapperCustomizer = new GrailsJsonMapperCustomizer()
        renderer.springHttpMessageConvertersSupplier = { [converter] }
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        def spring = new MockHttpOutputMessage()
        converter.write(new ProjectionBody(title: 'Grails', hidden: true), new MediaType(MediaType.APPLICATION_JSON, UTF_8), spring)

        when:
        renderer.render(new ProjectionBody(title: 'Grails', hidden: true), new ServletRenderContext(webRequest))

        then:
        webRequest.response.contentAsString == spring.bodyAsString
        spring.bodyAsString == written

        where:
        description                                 | converter                   | written
        'a subclass that writes a suffix'           | new SuffixedJsonConverter() | '{"hidden":true,"title":"Grails"};'
        'a mapper registered for the class written' | upperCamelCaseConverter()   | '{"Hidden":true,"Title":"Grails"}'
    }

    void 'a response writer that fails as the prefix of the converter is written fails as Spring MVC reports it'() {
        given:
        def source = new JacksonJsonHttpMessageConverter()
        source.setPrefixJson(true)
        def renderer = grailsMapperRenderer(source)
        def context = new ServletRenderContext(brokenPipeRequest())

        when:
        renderer.render(new ProjectionBody(title: 'Grails', hidden: true), context)

        then:
        HttpMessageNotWritableException e = thrown()
        e.message == 'Could not write JSON: Broken pipe'
        e.mostSpecificCause instanceof IOException
    }

    void 'a type definition error of the Grails mapper is reported as Spring MVC reports it, leaving the response uncommitted'() {
        given: 'a bean whose @JsonUnwrapped property writes a name the bean writes too, which Jackson rejects'
        def renderer = grailsMapperRenderer(new JacksonJsonHttpMessageConverter())
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()

        when:
        renderer.render(new ClashingBody(id: 5, inner: new ClashingInner(id: 1)), new ServletRenderContext(webRequest))

        then: 'as a conversion error, which is not a failure to write'
        HttpMessageConversionException e = thrown()
        e.class == HttpMessageConversionException
        e.message.startsWith('Type definition error: ')
        !webRequest.response.committed
    }

    void 'the Grails mapper writes JSON in UTF-8, as Spring MVC writes it, whatever the encoding of the converters'() {
        given:
        def renderer = grailsMapperRenderer(new JacksonJsonHttpMessageConverter())
        renderer.encoding = 'ISO-8859-1'
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()

        when:
        renderer.render(new ProjectionBody(title: 'caf\u00e9'), new ServletRenderContext(webRequest))

        then:
        webRequest.response.contentType == 'application/json'
        webRequest.response.contentAsByteArray == '{"hidden":false,"title":"caf\u00e9"}'.getBytes(UTF_8)
    }

    void 'a Jackson 2 converter writes JSON in UTF-8, as Spring MVC writes it, whatever the encoding of the converters'() {
        given:
        def renderer = new DefaultJsonRenderer<Object>(Object)
        renderer.useSpringJson = true
        renderer.springHttpMessageConvertersSupplier = { [new MappingJackson2HttpMessageConverter()] }
        renderer.encoding = 'ISO-8859-1'
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()

        when:
        renderer.render(new AccentBody(title: 'caf\u00e9'), new ServletRenderContext(webRequest))

        then:
        webRequest.response.contentType == 'application/json'
        webRequest.response.contentAsByteArray == '{"title":"caf\u00e9"}'.getBytes(UTF_8)
    }

    void 'a projection a serializer of its own bypasses on the Spring path is reported'() {
        given: 'an application started afresh, which has reported nothing'
        ShutdownOperations.runOperations()

        and: 'a mapper with a serializer of the projected type, which writes it without its properties'
        def mapper = JsonMapper.builder().addModule(new SimpleModule().addSerializer(ProjectionBody, new TitleOnlySerializer())).build()
        def renderer = grailsMapperRenderer(new JacksonJsonHttpMessageConverter(mapper))
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()

        when:
        String log = stderrWhile {
            renderer.render(new ProjectionBody(title: 'Grails', hidden: true),
                    new ServletRenderContext(webRequest, [excludes: ['title']]))
        }

        then:
        webRequest.response.contentAsString == '"Grails"'
        log.contains("JSON includes/excludes were not applied to [class ${ProjectionBody.name}]")
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

    void 'a ProblemDetail with no MVC converters names why the legacy converter wrote it, once'() {
        given:
        def renderer = new DefaultJsonRenderer<Object>(Object)

        when:
        String log = stderrWhile {
            2.times {
                renderer.render(ProblemDetail.forStatus(409), new ServletRenderContext(GrailsWebMockUtil.bindMockWebRequest()))
            }
        }

        then:
        log.contains("A ProblemDetail is written through Spring's message converters, but no Spring message converter writes")
        log.readLines().count { it.contains('so the legacy JSON converter writes it as a bean') } == 1
    }

    void 'a ProblemDetail is rendered by the format view respond names, with the status it carries, and by no other'() {
        given: 'a JSON view of the action, and one that respond names'
        def locator = Stub(GrailsConventionGroovyPageLocator) {
            findViewForFormat('book', 'show', 'json') >> Stub(GroovyPageScriptSource)
            findViewForFormat('book', 'conflict', 'json') >> Stub(GroovyPageScriptSource)
        }
        def renderer = new DefaultJsonRenderer<Object>(Object, locator)
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        webRequest.controllerName = 'book'
        webRequest.actionName = 'show'
        def problem = ProblemDetail.forStatus(409)

        when:
        renderer.render(problem, new ServletRenderContext(webRequest, [view: 'conflict']))
        ModelAndView modelAndView = webRequest.currentRequest.getAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW)

        then:
        modelAndView.viewName == 'conflict'
        modelAndView.model == [problemDetail: problem]
        webRequest.response.status == 409

        when: 'respond names no view'
        webRequest = GrailsWebMockUtil.bindMockWebRequest()
        webRequest.controllerName = 'book'
        webRequest.actionName = 'show'
        renderer.render(ProblemDetail.forStatus(409), new ServletRenderContext(webRequest))

        then: 'the view of the action does not render the problem'
        webRequest.currentRequest.getAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW) == null
        webRequest.response.contentAsString.contains('"status":409')
    }

    void 'the JSON view respond names renders a ProblemDetail, rather than the HTML renderer an application registers for problems'() {
        given:
        def locator = Stub(GrailsConventionGroovyPageLocator) {
            findViewForFormat('book', 'conflict', 'json') >> Stub(GroovyPageScriptSource)
        }
        def page = Stub(Renderer) {
            render(_, _) >> { Object problem, RenderContext context -> context.viewName = '/errors/problem' }
        }
        def registry = Stub(RendererRegistry) {
            findRenderer(grails.web.mime.MimeType.HTML, _) >> page
        }
        def renderer = new DefaultJsonRenderer<Object>(Object, locator, registry)
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        webRequest.controllerName = 'book'
        webRequest.actionName = 'show'
        def problem = ProblemDetail.forStatus(409)

        when:
        renderer.render(problem, new ServletRenderContext(webRequest, [view: 'conflict']))
        ModelAndView modelAndView = webRequest.currentRequest.getAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW)

        then:
        modelAndView.viewName == 'conflict'
        modelAndView.model == [problemDetail: problem]
        webRequest.response.status == 409
    }

    void 'the JSON view respond names gets a ProblemDetail under the model name its HTML view gets it'() {
        given: 'a registry that names the model of a view with the domain suffix'
        def locator = Stub(GrailsConventionGroovyPageLocator) {
            findViewForFormat('book', 'conflict', 'json') >> Stub(GroovyPageScriptSource)
        }
        def registry = new DefaultRendererRegistry(modelSuffix: 'Instance')
        registry.initialize()
        def renderer = new DefaultJsonRenderer<Object>(Object, locator, registry)
        def problem = ProblemDetail.forStatus(409)
        def html = GrailsWebMockUtil.bindMockWebRequest()
        html.controllerName = 'book'

        when: 'the HTML view'
        registry.findRenderer(grails.web.mime.MimeType.HTML, problem).render(problem,
                new ServletRenderContext(html, [view: 'conflict']))

        then:
        html.currentRequest.getAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW).model == [problemDetailInstance: problem]

        when: 'the JSON view'
        def json = GrailsWebMockUtil.bindMockWebRequest()
        json.controllerName = 'book'
        renderer.render(problem, new ServletRenderContext(json, [view: 'conflict']))

        then:
        json.currentRequest.getAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW).model == [problemDetailInstance: problem]
    }

    void 'a named JSON configuration does not apply to a ProblemDetail the legacy converter writes'() {
        given: 'a configuration of the JSON mapper, which the legacy converter does not have'
        def renderer = new DefaultJsonRenderer<Object>(Object)
        renderer.namedJsonRenderer = Stub(NamedJsonRenderer) {
            contains('catalog') >> true
        }
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()

        when:
        stderrWhile {
            renderer.render(ProblemDetail.forStatus(409),
                    new ServletRenderContext(webRequest, [jsonConfiguration: 'catalog']))
        }

        then:
        noExceptionThrown()
        webRequest.response.status == 409
        webRequest.response.contentAsString.contains('"status":409')
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

    void 'with Spring JSON on, each type the legacy converter writes is reported once, for each reason'() {
        given: 'an application converter, which cannot apply a projection'
        def converter = Mock(HttpMessageConverter) {
            canWrite(_, MediaType.APPLICATION_JSON) >> true
            getSupportedMediaTypes(_ as Class) >> [MediaType.APPLICATION_JSON]
        }
        def renderers = (1..2).collect {
            new DefaultJsonRenderer<Object>(Object).tap {
                useSpringJson = true
                grailsJsonMapperCustomizer = new GrailsJsonMapperCustomizer()
                springHttpMessageConvertersSupplier = { [converter] }
            }
        }
        // as the renderers of one registry share it
        renderers[1].springFallbacksReported = renderers[0].springFallbacksReported

        when: 'each renderer writes both types'
        String log = stderrWhile {
            renderers.each { renderer ->
                [new ProjectionBody(title: 'Grails'), new AccentBody(title: 'Grails')].each { value ->
                    renderer.render(value, new ServletRenderContext(GrailsWebMockUtil.bindMockWebRequest(), [includes: ['title']]))
                }
            }
        }

        then:
        log.readLines().count { it.contains("but [${ProjectionBody.name}] is written by the legacy JSON converter") } == 1
        log.readLines().count { it.contains("but [${AccentBody.name}] is written by the legacy JSON converter") } == 1
    }

    void 'with Spring JSON on, no more types are reported once as many as the limit have been'() {
        given:
        def converter = Mock(HttpMessageConverter) {
            canWrite(_, MediaType.APPLICATION_JSON) >> true
            getSupportedMediaTypes(_ as Class) >> [MediaType.APPLICATION_JSON]
        }
        def renderer = new DefaultJsonRenderer<Object>(Object)
        renderer.useSpringJson = true
        renderer.grailsJsonMapperCustomizer = new GrailsJsonMapperCustomizer()
        renderer.springHttpMessageConvertersSupplier = { [converter] }
        (1..<DefaultJsonRenderer.MAX_REPORTED_FALLBACKS).each { renderer.springFallbacksReported.add("reported ${it}".toString()) }

        when:
        String log = stderrWhile {
            [new ProjectionBody(title: 'Grails'), new AccentBody(title: 'Grails')].each { value ->
                renderer.render(value, new ServletRenderContext(GrailsWebMockUtil.bindMockWebRequest(), [includes: ['title']]))
            }
        }

        then: 'the last type the limit allows, and that no more are reported'
        log.contains("but [${ProjectionBody.name}] is written by the legacy JSON converter")
        log.contains("has written ${DefaultJsonRenderer.MAX_REPORTED_FALLBACKS} types while Spring JSON rendering is enabled")
        !log.contains("but [${AccentBody.name}] is written by the legacy JSON converter")
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

    void 'an application converter writes a problem whatever the includes, which do not apply to a problem'() {
        given:
        def converter = Mock(HttpMessageConverter) {
            canWrite(ProblemDetail, _) >> true
            getSupportedMediaTypes(_ as Class) >> [MediaType.APPLICATION_PROBLEM_JSON]
        }
        def renderer = new DefaultJsonRenderer<ProblemDetail>(ProblemDetail)
        renderer.useSpringJson = true
        renderer.grailsJsonMapperCustomizer = new GrailsJsonMapperCustomizer()
        renderer.springHttpMessageConvertersSupplier = { [converter] }
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()

        when:
        renderer.render(ProblemDetail.forStatus(409), new ServletRenderContext(webRequest, [includes: ['title']]))

        then:
        1 * converter.write(_ as ProblemDetail, _, _)
    }

    void 'empty includes are applied by the legacy converter in the place of an application converter, which writes none of the properties'() {
        given:
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
        renderer.render(new ProjectionBody(title: 'Included', hidden: true), new ServletRenderContext(webRequest, [includes: []]))

        then:
        0 * converter.write(_, _, _)
        webRequest.response.contentAsString == '{}'
    }

    void 'unknown named JSON configurations have the same error as render json, for #description'() {
        given:
        def renderer = new DefaultJsonRenderer<Object>(Object)
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()

        when:
        renderer.render(value, new ServletRenderContext(webRequest, [jsonConfiguration: 'missing']))

        then:
        def error = thrown(IllegalArgumentException)
        error.message == 'Named JSON configuration [missing] is not registered.'

        where: 'also for a problem, which no configuration writes'
        description | value
        'a value'   | [ok: true]
        'a problem' | ProblemDetail.forStatus(409)
    }

    void 'an unknown named JSON configuration fails where a JSON view renders the response too, for #description'() {
        given: 'a JSON view of the action, and one respond names'
        def locator = Stub(GrailsConventionGroovyPageLocator) {
            findViewForFormat('book', 'show', 'json') >> Stub(GroovyPageScriptSource)
            findViewForFormat('book', 'conflict', 'json') >> Stub(GroovyPageScriptSource)
        }
        def renderer = new DefaultJsonRenderer<Object>(Object, locator)
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        webRequest.controllerName = 'book'
        webRequest.actionName = 'show'

        when:
        renderer.render(value, new ServletRenderContext(webRequest, [jsonConfiguration: 'missing'] + arguments))

        then:
        def error = thrown(IllegalArgumentException)
        error.message == 'Named JSON configuration [missing] is not registered.'
        webRequest.currentRequest.getAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW) == null

        where:
        description                          | value                         | arguments
        'a value the action view renders'    | [ok: true]                    | [:]
        'a problem the view respond names'   | ProblemDetail.forStatus(409)  | [view: 'conflict']
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

    void 'Jackson bytes decode correctly with a non UTF encoding of the converters'() {
        given:
        def renderer = new DefaultJsonRenderer<Object>(Object)
        renderer.useSpringJson = true
        renderer.encoding = 'ISO-8859-1'
        renderer.springHttpMessageConvertersSupplier = { [new JacksonJsonHttpMessageConverter()] }
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()

        when:
        renderer.render([title: 'café'], new ServletRenderContext(webRequest))

        then: 'in UTF-8, which the response says it is in'
        webRequest.response.contentAsString == '{"title":"café"}'
        webRequest.response.characterEncoding == 'UTF-8'
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
        (1.._) * selected.canWrite(LinkedHashMap, MediaType.APPLICATION_JSON) >> true
        1 * selected.write(_, MediaType.APPLICATION_JSON, _) >> { arguments ->
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

    void 'a write with a named configuration that fails is reported as Spring MVC reports it, for #description'() {
        given:
        def registry = new NamedJsonConfigurationRegistry(JsonMapper.builder().build())
        registry.register('plain') { }
        def renderer = new DefaultJsonRenderer<Object>(Object)
        renderer.namedJsonRenderer = new JacksonNamedJsonRenderer(registry)
        def webRequest = failingWriter ? brokenPipeRequest() : GrailsWebMockUtil.bindMockWebRequest()
        def context = new ServletRenderContext(webRequest, [jsonConfiguration: 'plain'])

        when:
        renderer.render(value, context)

        then:
        HttpMessageConversionException e = thrown()
        e.class == type
        e.message.startsWith(message)

        where:
        description               | value                                                   | failingWriter || type                            | message
        'a writer that fails'     | new ProjectionBody(title: 'Grails')                     | true          || HttpMessageNotWritableException | 'Could not write JSON: Broken pipe'
        'a type definition error' | new ClashingBody(id: 5, inner: new ClashingInner(id: 1)) | false         || HttpMessageConversionException  | 'Type definition error: '
    }

    /** A request whose response fails to take what is written to it, as a client that went away. */
    private static GrailsWebRequest brokenPipeRequest() {
        def response = new MockHttpServletResponse() {
            @Override
            ServletOutputStream getOutputStream() {
                new ServletOutputStream() {
                    @Override
                    boolean isReady() {
                        true
                    }

                    @Override
                    void setWriteListener(WriteListener listener) {
                    }

                    @Override
                    void write(int b) throws IOException {
                        throw new IOException('Broken pipe')
                    }

                    @Override
                    void write(byte[] bytes, int offset, int length) throws IOException {
                        throw new IOException('Broken pipe')
                    }
                }
            }
        }
        GrailsWebMockUtil.bindMockWebRequest(new MockServletContext(), new MockHttpServletRequest(), response)
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
        def renderer = new DefaultJsonRenderer<Errors>(Errors)
        renderer.useSpringJson = true
        renderer.springHttpMessageConvertersSupplier = { [new JacksonJsonHttpMessageConverter()] }
        def errors = new BeanPropertyBindingResult(new Object(), 'book')
        errors.addError(new FieldError('book', 'title', 'secret', false,
                ['book.title.blank'] as String[], null, 'Title must not be blank'))
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()

        when:
        renderer.render(errors, new ServletRenderContext(webRequest))
        def json = new JsonSlurper().parseText(webRequest.response.contentAsString)

        then: 'Spring MVC writes the problem the errors make, without the rejected value'
        webRequest.response.status == 422
        webRequest.response.contentType.startsWith('application/problem+json')
        json.status == 422
        json.title == 'Validation failed'
        json.errors.size() == 1
        !json.errors.first().containsKey('rejectedValue')
    }

    void 'the problem content type is set before the body is written, which can commit the response'() {
        given:
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        String contentTypeWhenWritten = null
        def converter = new JacksonJsonHttpMessageConverter() {
            @Override
            protected void writeInternal(Object object, ResolvableType type, HttpOutputMessage message, Map<String, Object> hints) {
                message.body
                contentTypeWhenWritten = webRequest.response.contentType
                super.writeInternal(object, type, message, hints)
            }
        }
        def renderer = new DefaultJsonRenderer<Errors>(Errors)
        renderer.useSpringJson = true
        renderer.springHttpMessageConvertersSupplier = { [converter] }
        def errors = new BeanPropertyBindingResult(new Object(), 'book')
        errors.addError(new ObjectError('book', ['book.invalid'] as String[], null, 'Book is invalid'))

        when:
        renderer.render(errors, new ServletRenderContext(webRequest))

        then:
        contentTypeWhenWritten.startsWith('application/problem+json')
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
        // as the converters plugin registers it
        JSON.registerObjectMarshaller(new ValidationErrorsMarshaller())

        when:
        renderer.render(errors, new ServletRenderContext(webRequest))

        then:
        1 * converter.canWrite(ProblemDetail, MediaType.APPLICATION_PROBLEM_JSON) >> false
        0 * converter.write(_, _, _)

        and: "the legacy converter path writes the errors as ordinary JSON, not problem JSON"
        webRequest.response.contentType == 'application/json;charset=UTF-8'
        new JsonSlurper().parseText(webRequest.response.contentAsString) == [errors: [[object: 'book', message: 'Book is invalid']]]
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

    void 'an application converter is given the type Spring MVC selects, whatever the encoding of the converters'() {
        given:
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

        then:
        (1.._) * converter.canWrite(_, MediaType.APPLICATION_JSON) >> true
        1 * converter.write(_, MediaType.APPLICATION_JSON, _) >> { arguments ->
            arguments[2].body.write('{"title":"caf\u00e9"}'.getBytes(UTF_8))
        }
        webRequest.response.contentAsByteArray == '{"title":"caf\u00e9"}'.getBytes(UTF_8)
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
        (1.._) * converter.canWrite(ProblemDetail, MediaType.APPLICATION_PROBLEM_JSON) >> true
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
        (1.._) * converter.canWrite(ProblemDetail, MediaType.APPLICATION_PROBLEM_JSON) >> true
        1 * converter.write({ ProblemDetail problem -> problem.status == 400 },
                MediaType.APPLICATION_PROBLEM_JSON, _) >> { arguments ->
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

    private static DefaultJsonRenderer<Object> grailsMapperRenderer(JacksonJsonHttpMessageConverter converter) {
        def renderer = new DefaultJsonRenderer<Object>(Object)
        renderer.useSpringJson = true
        renderer.grailsJsonMapperCustomizer = new GrailsJsonMapperCustomizer()
        renderer.springHttpMessageConvertersSupplier = { [converter] }
        renderer
    }

    private static JacksonJsonHttpMessageConverter upperCamelCaseConverter() {
        def converter = new JacksonJsonHttpMessageConverter()
        converter.registerMappersForType(ProjectionBody) { Map<MediaType, JsonMapper> mappers ->
            mappers.put(MediaType.APPLICATION_JSON,
                    JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.UPPER_CAMEL_CASE).build())
        }
        converter
    }

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

class ClashingBody {
    Integer id
    @JsonUnwrapped
    ClashingInner inner
}

class ClashingInner {
    Integer id
}

class TitleOnlySerializer extends StdSerializer<ProjectionBody> {

    TitleOnlySerializer() {
        super(ProjectionBody)
    }

    @Override
    void serialize(ProjectionBody body, JsonGenerator generator, SerializationContext context) {
        generator.writeString(body.title)
    }
}

class SuffixedJsonConverter extends JacksonJsonHttpMessageConverter {
    @Override
    protected void writeSuffix(JsonGenerator generator, Object object) {
        generator.writeRaw(';')
    }
}

class AccentBody {
    String title
}
