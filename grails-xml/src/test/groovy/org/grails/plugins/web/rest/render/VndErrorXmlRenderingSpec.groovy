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
package org.grails.plugins.web.rest.render

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.persistence.Entity
import grails.rest.render.errors.VndErrorXmlRenderer
import grails.util.GrailsWebMockUtil
import grails.util.GrailsWebUtil
import grails.web.CamelCaseUrlConverter
import grails.web.mapping.LinkGenerator
import grails.web.mapping.UrlMappingsHolder
import grails.web.mime.MimeType
import org.grails.spring.GrailsApplicationContext
import org.grails.support.MockApplicationContext
import org.grails.web.mapping.DefaultLinkGenerator
import org.grails.web.mapping.DefaultUrlMappingEvaluator
import org.grails.web.mapping.DefaultUrlMappingsHolder
import org.springframework.context.support.StaticMessageSource
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockServletContext
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.Errors
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.support.GenericWebApplicationContext
import org.grails.core.lifecycle.ShutdownOperations
import spock.lang.Specification

/**
 *
 * @author Graeme Rocher
 */
class VndErrorXmlRenderingSpec extends Specification {

    void cleanup() {
        RequestContextHolder.resetRequestAttributes()
    }

    void "Test VND error rendering in XML" () {
        given:"A registry with VND error registered"
            def registry = new DefaultRendererRegistry()
            final vndRenderer = new VndErrorXmlRenderer()
            final messageSource = new StaticMessageSource()
            final request = GrailsWebMockUtil.bindMockWebRequest()
            final response = request.response
            messageSource.addMessage("title.invalid", request.locale, "Bad Title")
            vndRenderer.messageSource = messageSource
            vndRenderer.linkGenerator = getLinkGenerator {
                "/books"(resources:"book")
            }
            registry.addRenderer(vndRenderer)


        when:"A renderer is looked up"
            final book = new Book()
            book.id = 1
            def error = new BeanPropertyBindingResult(book, Book.name)
            error.rejectValue("title", "title.invalid")
            final renderer = registry.findContainerRenderer(MimeType.XML, Errors, book)

        then:"It is a VND error renderer"
            renderer instanceof VndErrorXmlRenderer


        when:"The renderer renders an error"
            renderer.render(error, new ServletRenderContext(request))

        then:"The response is correct"
            response.status == HttpStatus.UNPROCESSABLE_ENTITY.value()
            response.contentType == GrailsWebUtil.getContentType(VndErrorXmlRenderer.MIME_TYPE.name, GrailsWebUtil.DEFAULT_ENCODING)
            response.contentAsString == '<?xml version="1.0" encoding="UTF-8"?><errors xml:lang="en"><error logref="book.title.invalid.1"><message>Bad Title</message><link rel="resource" href="http://localhost/books/1" /></error></errors>'

    }

    void "Vnd.Error XML rendering warns once per application that it is scheduled for removal in Grails 11"() {
        given:
        ShutdownOperations.runOperations()
        def vndRenderer = new VndErrorXmlRenderer()
        def messageSource = new StaticMessageSource()
        messageSource.addMessage("title.invalid", Locale.ENGLISH, "Bad Title")
        vndRenderer.messageSource = messageSource
        vndRenderer.linkGenerator = getLinkGenerator {
            "/books"(resources:"book")
        }
        def book = new Book()
        book.id = 1
        def error = new BeanPropertyBindingResult(book, Book.name)
        error.rejectValue("title", "title.invalid")

        when:
        String log = stderrWhile {
            2.times { vndRenderer.render(error, new ServletRenderContext(GrailsWebMockUtil.bindMockWebRequest())) }
        }

        then:
        log.readLines().count { it.contains('Vnd.Error XML rendering is scheduled for removal in Grails 11.') } == 1

        cleanup:
        ShutdownOperations.runOperations()
    }

    LinkGenerator getLinkGenerator(Closure mappings) {
        def generator = new DefaultLinkGenerator("http://localhost", null)
        generator.grailsUrlConverter = new CamelCaseUrlConverter()
        generator.urlMappingsHolder = getUrlMappingsHolder mappings
        return generator;
    }
    UrlMappingsHolder getUrlMappingsHolder(Closure mappings) {
        def ctx = new MockApplicationContext()
        ctx.registerMockBean(GrailsApplication.APPLICATION_ID, new DefaultGrailsApplication())
        def evaluator = new DefaultUrlMappingEvaluator(ctx)
        def allMappings = evaluator.evaluateMappings mappings
        return new DefaultUrlMappingsHolder(allMappings)
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

@Entity
class Book {
    Long id
    String title
}
