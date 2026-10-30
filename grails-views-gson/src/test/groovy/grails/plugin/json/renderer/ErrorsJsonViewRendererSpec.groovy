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
package grails.plugin.json.renderer

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.Errors
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.servlet.View

import grails.converters.JSON
import grails.core.DefaultGrailsApplication
import grails.plugin.json.view.mvc.JsonViewResolver
import grails.util.GrailsWebMockUtil
import grails.web.mime.MimeType
import org.grails.plugins.web.rest.render.DefaultRendererRegistry
import org.grails.plugins.web.rest.render.ServletRenderContext
import org.grails.plugins.web.rest.render.SpringMessageConverters
import org.grails.web.converters.configuration.ConvertersConfigurationHolder
import org.grails.web.converters.configuration.ConvertersConfigurationInitializer
import org.grails.web.converters.marshaller.json.ValidationErrorsMarshaller
import spock.lang.Specification

/**
 * Validation errors are rendered by an errors template, and otherwise as without JSON views, never by the object template,
 * which forge's JSON views feature and the REST API profile generate.
 */
class ErrorsJsonViewRendererSpec extends Specification {

    void setup() {
        new ConvertersConfigurationInitializer(grailsApplication: new DefaultGrailsApplication()).initialize()
        // as the converters plugin registers it in an application
        JSON.registerObjectMarshaller(new ValidationErrorsMarshaller())
    }

    void cleanup() {
        ConvertersConfigurationHolder.clear()
        RequestContextHolder.resetRequestAttributes()
    }

    void "without an errors template, validation errors are written as without JSON views, with Spring JSON #springJson"() {
        given: 'only the object template'
        def renderer = errorsRenderer(springJson, ['/object/_object'])
        def webRequest = bookRequest()

        when:
        renderer.render(invalid(), new ServletRenderContext(webRequest))

        then:
        webRequest.response.status == 422
        webRequest.response.contentType.startsWith(contentType)
        webRequest.response.contentAsString != 'null'
        webRequest.response.contentAsString.contains('title')

        where:
        springJson || contentType
        true       || 'application/problem+json'
        false      || 'application/json'
    }

    void 'without an errors template, validation errors are written in the encoding of the registry, as without JSON views'() {
        given:
        def renderer = errorsRenderer(false, ['/object/_object'], 'ISO-8859-1')
        def webRequest = bookRequest()

        when:
        renderer.render(invalid(), new ServletRenderContext(webRequest))

        then:
        webRequest.response.contentType == 'application/json;charset=ISO-8859-1'
    }

    void 'an errors template renders validation errors, the controller one ahead of #{other}'() {
        given:
        def renderer = errorsRenderer(true, templates)
        def webRequest = bookRequest()

        when:
        renderer.render(invalid(), new ServletRenderContext(webRequest))

        then:
        webRequest.response.contentAsString == rendered

        where:
        other              | templates                                                        || rendered
        'the object one'   | ['/object/_object', '/errors/_errors']                           || '/errors/_errors'
        'the errors one'   | ['/object/_object', '/errors/_errors', '/book/_errors']          || '/book/_errors'
    }

    private static ErrorsJsonViewRenderer errorsRenderer(boolean springJson, List<String> templates, String encoding = 'UTF-8') {
        TemplatesResolver.TEMPLATES.set(templates)
        def registry = new DefaultRendererRegistry(useSpringJson: springJson, encoding: encoding)
        registry.springMessageConverters = new SpringMessageConverters()
        registry.springMessageConverters.setConverters([new JacksonJsonHttpMessageConverter()])
        registry.initialize()
        def resolver = new TemplatesResolver()
        resolver.rendererRegistry = registry
        resolver.initialize()
        (ErrorsJsonViewRenderer) registry.findContainerRenderer(MimeType.JSON, Errors, new ErrorsBook())
    }

    private static Object bookRequest() {
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        webRequest.controllerName = 'book'
        webRequest
    }

    private static Errors invalid() {
        def book = new ErrorsBook(title: '')
        def errors = new BeanPropertyBindingResult(book, 'errorsBook')
        errors.rejectValue('title', 'blank', 'must not be blank')
        errors
    }
}

/**
 * Resolves the templates a feature lists, each as a view that writes its name; read from a thread local, since the
 * object template is resolved while the resolver is constructed.
 */
class TemplatesResolver extends JsonViewResolver {

    static final ThreadLocal<List<String>> TEMPLATES = ThreadLocal.withInitial { [] }

    @Override
    View resolveView(String viewName, Locale locale) {
        TEMPLATES.get().contains(viewName) ? new NameView(viewName) : null
    }
}

class NameView implements View {

    private final String name

    NameView(String name) {
        this.name = name
    }

    @Override
    void render(Map<String, ?> model, HttpServletRequest request, HttpServletResponse response) {
        // as the object template generated applications have writes validation errors, which it is not given as object
        response.writer.write(name == '/object/_object' ? 'null' : name)
    }
}

class ErrorsBook {
    String title
}
