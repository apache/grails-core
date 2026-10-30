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
package org.grails.plugins.web.rest.render.atom

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.persistence.Entity
import grails.rest.render.atom.AtomCollectionRenderer
import grails.rest.render.atom.AtomRenderer
import grails.rest.render.hal.HalXmlRenderer
import grails.spring.BeanBuilder
import grails.util.GrailsWebMockUtil
import grails.util.GrailsWebUtil
import grails.web.CamelCaseUrlConverter
import grails.web.mapping.LinkGenerator
import org.grails.config.PropertySourcesConfig
import org.grails.core.lifecycle.ShutdownOperations
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.plugins.web.mime.MimeTypesConfiguration
import org.grails.plugins.web.rest.render.ServletRenderContext
import org.grails.support.MockApplicationContext
import org.grails.web.converters.configuration.ConvertersConfigurationHolder
import org.grails.web.converters.configuration.XmlConvertersConfigurationInitializer
import org.grails.web.mapping.DefaultLinkGenerator
import org.grails.web.mapping.DefaultUrlMappingEvaluator
import org.grails.web.mapping.DefaultUrlMappingsHolder
import org.grails.web.mime.DefaultMimeUtility
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.springframework.context.support.GenericApplicationContext
import org.springframework.context.support.StaticMessageSource
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.support.GenericWebApplicationContext
import org.springframework.web.util.WebUtils
import spock.lang.Specification

class AtomRendererSpec extends Specification {

    void setup() {
        ShutdownOperations.runOperations()
        def initializer = new XmlConvertersConfigurationInitializer()
        initializer.grailsApplication = new DefaultGrailsApplication()
        initializer.initialize()
    }

    void cleanup() {
        ConvertersConfigurationHolder.clear()
        RequestContextHolder.resetRequestAttributes()
        ShutdownOperations.runOperations()
    }

    void 'Test rendering a single domain object as an Atom feed'() {
        given:
        def renderer = configureRenderer(new AtomRenderer(AtomBook))
        def webRequest = boundMimeTypeRequest()
        def response = webRequest.response as MockHttpServletResponse
        def book = new AtomBook(title: 'Grails & Groovy')
        book.id = 1L
        book.dateCreated = new Date(1383908400000L)
        book.lastUpdated = new Date(1383994800000L)

        when:
        renderer.render(book, new ServletRenderContext(webRequest))

        then:
        response.contentType == GrailsWebUtil.getContentType(AtomRenderer.MIME_TYPE.name, GrailsWebUtil.DEFAULT_ENCODING)
        response.contentAsString.startsWith('<?xml version="1.0" encoding="UTF-8"?>')
        response.contentAsString.contains('<feed xmlns="http://www.w3.org/2005/Atom">')
        response.contentAsString.contains('<title>Grails &amp; Groovy</title>')
        response.contentAsString.contains('<published>')
        response.contentAsString.contains('<updated>')
        response.contentAsString.contains('<id>tag:localhost,')
        response.contentAsString.contains(':1</id>')
        response.contentAsString.contains('<link rel="self" href="http://localhost/books/1" hreflang="en" title="Books" type="application/atom+xml" />')
        response.contentAsString.contains('<link rel="alternate" href="http://localhost/books/1" hreflang="en" title="Books" />')
        response.contentAsString.endsWith('</feed>')
    }

    void 'Test rendering a collection of domain objects as Atom entries'() {
        given:
        def renderer = configureRenderer(new AtomCollectionRenderer(AtomBook))
        def webRequest = boundMimeTypeRequest()
        def response = webRequest.response as MockHttpServletResponse
        def books = [
            new AtomBook(title: 'Grails'),
            new AtomBook(title: 'Groovy')
        ]
        books[0].id = 1L
        books[1].id = 2L

        when:
        renderer.render(books, new ServletRenderContext(webRequest))

        then:
        renderer.componentType == AtomBook
        response.contentType == GrailsWebUtil.getContentType(AtomRenderer.MIME_TYPE.name, GrailsWebUtil.DEFAULT_ENCODING)
        response.contentAsString.contains('<feed xmlns="http://www.w3.org/2005/Atom"><title>Books</title><id>tag:localhost:/books</id>')
        response.contentAsString.count('<entry>') == 2
        response.contentAsString.contains('<entry><id>tag:localhost:1</id>')
        response.contentAsString.contains('<entry><id>tag:localhost:2</id>')
        response.contentAsString.contains('rel="self" href="http://localhost/books/1"')
        response.contentAsString.contains('rel="self" href="http://localhost/books/2"')
        response.contentAsString.contains('<title>Grails</title>')
        response.contentAsString.contains('<title>Groovy</title>')
        !response.contentAsString.contains('<published>')
        !response.contentAsString.contains('<updated>')
        response.contentAsString.endsWith('</entry></feed>')
    }

    void 'Test rendering an empty Atom collection'() {
        given:
        def renderer = configureRenderer(new AtomCollectionRenderer(AtomBook))
        def webRequest = boundMimeTypeRequest()
        def response = webRequest.response as MockHttpServletResponse

        when:
        renderer.render([], new ServletRenderContext(webRequest))

        then:
        response.contentType == GrailsWebUtil.getContentType(AtomRenderer.MIME_TYPE.name, GrailsWebUtil.DEFAULT_ENCODING)
        response.contentAsString.contains('<feed xmlns="http://www.w3.org/2005/Atom"><title>Books</title><id>tag:localhost:/books</id>')
        response.contentAsString.contains('rel="self" href="http://localhost/books"')
        response.contentAsString.contains('rel="alternate" href="http://localhost/books"')
        !response.contentAsString.contains('<entry>')
        response.contentAsString.endsWith('</feed>')
    }

    void 'Test Atom rejects non domain objects for #collection'() {
        given:
        def renderer = configureRenderer(collection ? new AtomCollectionRenderer(AtomNote) : new AtomRenderer(AtomNote))
        def webRequest = boundMimeTypeRequest()
        def note = new AtomNote(title: 'Draft')

        when:
        renderer.render(collection ? [note] : note, new ServletRenderContext(webRequest))

        then:
        def error = thrown(IllegalArgumentException)
        error.message.contains("The AtomRenderer can only be used with domain classes that specify 'dateCreated' and 'lastUpdated' properties")

        where:
        collection << [false, true]
    }

    void 'Test Atom rejects domain objects without lastUpdated'() {
        given:
        def renderer = configureRenderer(new AtomRenderer(AtomAuthor))
        def webRequest = boundMimeTypeRequest()

        when:
        renderer.render(new AtomAuthor(name: 'Graeme'), new ServletRenderContext(webRequest))

        then:
        def error = thrown(IllegalArgumentException)
        error.message.contains("The AtomRenderer can only be used with domain classes that specify 'dateCreated' and 'lastUpdated' properties")
    }

    void 'Test Atom tag ids derive from the resource URI with #description'() {
        expect:
        AtomRenderer.generateIdForURI(url, dateCreated, id) == expected

        where:
        description              | url                          | dateCreated                                            | id   || expected
        'a resource path'        | 'http://localhost/books'     | null                                                   | null || 'tag:localhost:/books'
        'an explicit identifier' | 'https://localhost/books/1'  | null                                                   | 1L   || 'tag:localhost:1'
        'a creation date'        | 'http://localhost/books/1'   | new GregorianCalendar(2013, Calendar.NOVEMBER, 8).time | 1L   || 'tag:localhost,2013-11-08:1'
        'a fragment'             | 'http://example.org/books#1' | null                                                   | null || 'tag:example.org:/books/1'
        'no path'                | 'localhost'                  | null                                                   | null || 'tag:localhost'
    }

    void 'Atom rendering warns once per application that it is scheduled for removal in Grails 11'() {
        given:
        def renderer = configureRenderer(new AtomRenderer(AtomBook))
        def book = new AtomBook(title: 'Grails')
        book.id = 1L
        book.dateCreated = new Date(1383908400000L)
        book.lastUpdated = new Date(1383994800000L)

        when:
        String log = stderrWhile {
            2.times { renderer.render(book, new ServletRenderContext(boundMimeTypeRequest())) }
        }

        then:
        log.readLines().count { it.contains('Atom rendering is scheduled for removal in Grails 11.') } == 1
    }

    void 'each XML format warns for itself, so Atom warns once HAL XML has warned'() {
        given:
        def hal = configureRenderer(new HalXmlRenderer(AtomBook))
        def atom = configureRenderer(new AtomRenderer(AtomBook))
        def book = new AtomBook(title: 'Grails')
        book.id = 1L
        book.dateCreated = new Date(1383908400000L)
        book.lastUpdated = new Date(1383994800000L)

        when:
        String log = stderrWhile {
            hal.render(book, new ServletRenderContext(boundMimeTypeRequest()))
            atom.render(book, new ServletRenderContext(boundMimeTypeRequest()))
        }

        then:
        log.readLines().count { it.contains('HAL XML rendering is scheduled for removal in Grails 11.') } == 1
        log.readLines().count { it.contains('Atom rendering is scheduled for removal in Grails 11.') } == 1
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

    private <R extends HalXmlRenderer> R configureRenderer(R renderer) {
        def mappingContext = new KeyValueMappingContext('')
        mappingContext.addPersistentEntity(AtomBook)
        mappingContext.addPersistentEntity(AtomAuthor)
        renderer.mappingContext = mappingContext
        def messages = new StaticMessageSource()
        messages.addMessage('resource.atomBook.href.title', Locale.ENGLISH, 'Books')
        messages.addMessage('resource.books.href.title', Locale.ENGLISH, 'Books')
        renderer.messageSource = messages
        renderer.linkGenerator = getLinkGenerator()
        renderer.prettyPrint = false
        renderer
    }

    private LinkGenerator getLinkGenerator() {
        def ctx = new MockApplicationContext()
        ctx.registerMockBean(GrailsApplication.APPLICATION_ID, new DefaultGrailsApplication())
        def evaluator = new DefaultUrlMappingEvaluator(ctx)
        def mappings = evaluator.evaluateMappings {
            '/books'(resources: 'atomBook')
        }
        def generator = new DefaultLinkGenerator('http://localhost', null)
        generator.grailsUrlConverter = new CamelCaseUrlConverter()
        generator.urlMappingsHolder = new DefaultUrlMappingsHolder(mappings)
        generator
    }

    private GrailsWebRequest boundMimeTypeRequest() {
        def servletContext = new MockServletContext()
        def ctx = new GenericWebApplicationContext(servletContext)
        servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, ctx)
        def application = new DefaultGrailsApplication()
        application.config = new PropertySourcesConfig([
            'grails.mime.use.accept.header': true,
            'grails.mime.types': [atom: 'application/atom+xml']
        ])
        def mainContext = new GenericApplicationContext()
        mainContext.refresh()
        application.setApplicationContext(mainContext)
        def bb = new BeanBuilder()
        bb.beans {
            grailsApplication = application
            mimeConfiguration(MimeTypesConfiguration, application, [])
        }
        def mimeConfiguration = bb.createApplicationContext().getBean(MimeTypesConfiguration)
        ctx.beanFactory.registerSingleton('mimeUtility', new DefaultMimeUtility(mimeConfiguration.mimeTypes()))
        ctx.beanFactory.registerSingleton(GrailsApplication.APPLICATION_ID, application)
        ctx.refresh()
        def webRequest = GrailsWebMockUtil.bindMockWebRequest(ctx)
        (webRequest.request as MockHttpServletRequest).addHeader('ACCEPT', 'application/atom+xml')
        webRequest.request.setAttribute(WebUtils.FORWARD_REQUEST_URI_ATTRIBUTE, '/books')
        webRequest
    }
}

@Entity
class AtomBook {
    String title
    Date dateCreated
    Date lastUpdated
}

@Entity
class AtomAuthor {
    String name
}

class AtomNote {
    String title
}
