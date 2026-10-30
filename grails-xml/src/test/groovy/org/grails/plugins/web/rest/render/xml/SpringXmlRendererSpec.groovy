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

import groovy.xml.XmlSlurper

import org.springframework.http.converter.xml.JacksonXmlHttpMessageConverter
import org.springframework.http.converter.StringHttpMessageConverter
import org.springframework.http.MediaType
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.HttpMessageNotWritableException
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext

import grails.rest.render.RenderContext
import grails.web.mime.MimeType
import grails.core.DefaultGrailsApplication
import grails.persistence.Entity
import org.grails.plugins.web.rest.render.ServletRenderContext
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.converters.configuration.ConvertersConfigurationHolder
import org.grails.web.converters.configuration.XmlConvertersConfigurationInitializer

import org.springframework.validation.BeanPropertyBindingResult
import org.grails.web.gsp.io.GrailsConventionGroovyPageLocator
import grails.rest.render.xml.XmlRenderer

import spock.lang.Specification

import static java.nio.charset.StandardCharsets.UTF_8
import spock.lang.Unroll

class SpringXmlRendererSpec extends Specification {

    void setup() {
        new XmlConvertersConfigurationInitializer().tap {
            grailsApplication = new DefaultGrailsApplication()
            initialize()
        }
    }

    void cleanup() {
        ConvertersConfigurationHolder.clear()
    }

    @Unroll
    void 'ordinary beans are written through the Spring XML message converter for #acceptedMimeType'() {
        given:
        def renderer = new DefaultXmlRenderer<XmlGreeting>(XmlGreeting)
        renderer.useSpringXml = true
        renderer.springHttpMessageConvertersSupplier = { [new JacksonXmlHttpMessageConverter()] }
        def response = new MockHttpServletResponse()
        def webRequest = new GrailsWebRequest(new MockHttpServletRequest(), response, new MockServletContext())
        def context = new FixedMimeServletRenderContext(webRequest, acceptedMimeType)

        when:
        renderer.render(new XmlGreeting(message: 'hello'), context)

        then:
        new XmlSlurper().parseText(response.contentAsString).message.text() == 'hello'

        where:
        acceptedMimeType << [
                MimeType.XML,
                MimeType.TEXT_XML,
                new MimeType('application/vnd.grails.test+xml', 'xml'),
        ]
    }

    void 'the first capable MVC converter is selected in configured order'() {
        given:
        def first = Mock(HttpMessageConverter)
        def second = Mock(HttpMessageConverter)
        def renderer = new DefaultXmlRenderer<XmlGreeting>(XmlGreeting)
        renderer.useSpringXml = true
        renderer.springHttpMessageConvertersSupplier = { [first, second] }
        def response = new MockHttpServletResponse()
        def webRequest = new GrailsWebRequest(new MockHttpServletRequest(), response, new MockServletContext())
        def context = new FixedMimeServletRenderContext(webRequest, MimeType.XML)

        when:
        renderer.render(new XmlGreeting(message: 'hello'), context)

        then: 'and Spring MVC writes with it, as it writes the value of a handler method'
        1 * first.canWrite(XmlGreeting, MediaType.APPLICATION_XML) >> false
        2 * second.canWrite(XmlGreeting, MediaType.APPLICATION_XML) >> true
        1 * second.getSupportedMediaTypes(XmlGreeting) >> [MediaType.APPLICATION_XML]
        1 * second.write(_, MediaType.APPLICATION_XML, _)
        0 * _
    }

    void 'a write that fails part-way leaves the response uncommitted, so that the error page applies'() {
        given:
        def converter = Mock(HttpMessageConverter) {
            canWrite(XmlGreeting, MediaType.APPLICATION_XML) >> true
            getSupportedMediaTypes(XmlGreeting) >> [MediaType.APPLICATION_XML]
            write(_, _, _) >> { arguments ->
                arguments[2].body.write('<XmlGreeting><message>'.bytes)
                throw new HttpMessageNotWritableException('failed part-way')
            }
        }
        def renderer = new DefaultXmlRenderer<XmlGreeting>(XmlGreeting)
        renderer.useSpringXml = true
        renderer.springHttpMessageConvertersSupplier = { [converter] }
        def response = new MockHttpServletResponse()
        def webRequest = new GrailsWebRequest(new MockHttpServletRequest(), response, new MockServletContext())

        when:
        renderer.render(new XmlGreeting(message: 'hello'), new FixedMimeServletRenderContext(webRequest, MimeType.XML))

        then:
        thrown(HttpMessageNotWritableException)
        !response.committed
    }

    void 'a generic string converter does not strip the legacy XML string element'() {
        given:
        def renderer = new DefaultXmlRenderer<String>(String)
        renderer.useSpringXml = true
        renderer.springHttpMessageConvertersSupplier = { [new StringHttpMessageConverter()] }
        def response = new MockHttpServletResponse()
        def request = new GrailsWebRequest(new MockHttpServletRequest(), response, new MockServletContext())

        when:
        renderer.render('ok', new FixedMimeServletRenderContext(request, MimeType.XML))

        then:
        def xml = new XmlSlurper().parseText(response.contentAsString)
        xml.name() == 'string'
        xml.text() == 'ok'
    }

    void 'legacy XML conversion is retained for compatibility shapes'() {
        given:
        def mvcConverter = Mock(HttpMessageConverter)
        def renderer = new DefaultXmlRenderer<Map>(Map)
        renderer.useSpringXml = true
        renderer.springHttpMessageConvertersSupplier = { [mvcConverter] }
        def response = new MockHttpServletResponse()
        def webRequest = new GrailsWebRequest(new MockHttpServletRequest(), response, new MockServletContext())
        def context = new FixedMimeServletRenderContext(webRequest, MimeType.XML)

        when:
        renderer.render([message: 'hello'], context)

        then:
        0 * mvcConverter._
        new XmlSlurper().parseText(response.contentAsString).entry.text() == 'hello'
    }

    void 'domain responses keep legacy marshalling even with Spring XML enabled'() {
        given:
        grails.converters.XML.registerObjectMarshaller(XmlDomainGreeting) { value -> [legacy: value.message] }
        def converter = Mock(HttpMessageConverter)
        def renderer = new DefaultXmlRenderer<XmlDomainGreeting>(XmlDomainGreeting)
        renderer.useSpringXml = true
        renderer.springHttpMessageConvertersSupplier = { [converter] }
        def response = new MockHttpServletResponse()
        def request = new GrailsWebRequest(new MockHttpServletRequest(), response, new MockServletContext())

        when:
        renderer.render(new XmlDomainGreeting(message: 'hello'), new FixedMimeServletRenderContext(request, MimeType.XML))

        then:
        0 * converter._
        new XmlSlurper().parseText(response.contentAsString).entry.find { it.@key == 'legacy' }.text() == 'hello'
    }

    void 'XML a Spring converter writes is UTF-8, as Spring MVC writes it, whatever grails.converters.encoding says'() {
        given:
        def renderer = new DefaultXmlRenderer<XmlGreeting>(XmlGreeting)
        renderer.useSpringXml = true
        renderer.encoding = 'ISO-8859-1'
        renderer.springHttpMessageConvertersSupplier = { [new JacksonXmlHttpMessageConverter()] }
        def response = new MockHttpServletResponse()
        def request = new GrailsWebRequest(new MockHttpServletRequest(), response, new MockServletContext())

        when:
        renderer.render(new XmlGreeting(message: 'café'), new FixedMimeServletRenderContext(request, MimeType.XML))

        then:
        response.contentType == MediaType.APPLICATION_XML_VALUE
        new XmlSlurper().parseText(new String(response.contentAsByteArray, UTF_8)).message.text() == 'café'
    }

    void 'the excludes of an XmlRenderer keep a response on the legacy converter with Spring XML enabled'() {
        given:
        def converter = Mock(HttpMessageConverter)
        def renderer = new XmlRenderer<XmlCredentials>(XmlCredentials)
        renderer.excludes = ['password']
        renderer.useSpringXml = true
        renderer.springHttpMessageConvertersSupplier = { [converter] }
        def response = new MockHttpServletResponse()
        def request = new GrailsWebRequest(new MockHttpServletRequest(), response, new MockServletContext())

        when:
        renderer.render(new XmlCredentials(user: 'ada', password: 's3cret'),
                new FixedMimeServletRenderContext(request, MimeType.XML))

        then:
        0 * converter._
        response.contentAsString.contains('ada')
        !response.contentAsString.contains('s3cret')
    }

    void 'validation errors are written as XML where an action view exists'() {
        given:
        def locator = Mock(GrailsConventionGroovyPageLocator)
        def renderer = new DefaultXmlRenderer<Object>(Object, locator) {
            @Override
            protected void renderXml(Object object, RenderContext context) {
                context.writer.write('<errors><error/></errors>')
            }
        }
        def response = new MockHttpServletResponse()
        def request = new GrailsWebRequest(new MockHttpServletRequest(), response, new MockServletContext())
        request.controllerName = 'greeting'
        request.actionName = 'save'
        def errors = new BeanPropertyBindingResult(new XmlGreeting(message: 'x'), 'greeting')
        errors.rejectValue('message', 'invalid', 'Message is invalid')

        when: 'the action has a name, by which the renderer would look its view up'
        renderer.render(errors, new FixedMimeServletRenderContext(request, MimeType.XML))

        then: 'no view is looked up for validation errors'
        0 * locator._
        response.status == 422
        new XmlSlurper().parseText(response.contentAsString).error.size() == 1
    }

    void 'adding Jackson XML does not bypass registered XML marshallers by default'() {
        given:
        grails.converters.XML.registerObjectMarshaller(XmlGreeting) { value -> [custom: value.message] }
        def renderer = new DefaultXmlRenderer<XmlGreeting>(XmlGreeting)
        renderer.springHttpMessageConvertersSupplier = { [new JacksonXmlHttpMessageConverter()] }
        def response = new MockHttpServletResponse()
        def request = new GrailsWebRequest(new MockHttpServletRequest(), response, new MockServletContext())

        when:
        renderer.render(new XmlGreeting(message: 'customized'), new FixedMimeServletRenderContext(request, MimeType.XML))

        then:
        response.contentAsString.contains('customized')
        !response.contentAsString.contains('<XmlGreeting>')
    }
}

class XmlGreeting {
    String message
}

class XmlCredentials {
    String user
    String password
}

@Entity
class XmlDomainGreeting {
    String message
}

class FixedMimeServletRenderContext extends ServletRenderContext {
    private final MimeType mimeType

    FixedMimeServletRenderContext(GrailsWebRequest webRequest, MimeType mimeType) {
        super(webRequest)
        this.mimeType = mimeType
    }

    @Override
    MimeType getAcceptMimeType() {
        mimeType
    }
}
