/*
 * Copyright 2012-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.rest.web

import grails.artefact.Artefact
import grails.core.support.proxy.ProxyHandler
import grails.persistence.Entity
import grails.testing.gorm.DomainUnitTest
import grails.testing.web.controllers.ControllerUnitTest
import grails.web.mime.MimeType
import org.grails.web.util.GrailsApplicationAttributes
import org.springframework.web.servlet.ModelAndView
import spock.lang.Issue
import spock.lang.Specification

class RespondMethodSpec extends Specification implements ControllerUnitTest<BookController>, DomainUnitTest<Book> {

    Closure doWithConfig() {{ config ->
        // unit tests in real applications will not need to do
        // this because the real Config.groovy will be loaded
        config['grails.mime.types'] = [html      : ['text/html', 'application/xhtml+xml'],
                                    xml          : ['text/xml', 'application/xml'],
                                    text         : 'text/plain',
                                    js           : 'text/javascript',
                                    rss          : 'application/rss+xml',
                                    atom         : 'application/atom+xml',
                                    css          : 'text/css',
                                    csv          : 'text/csv',
                                    all          : '*/*',
                                    json         : ['application/json', 'text/json'],
                                    form         : 'application/x-www-form-urlencoded',
                                    multipartForm: 'multipart/form-data']
    }}

    void "Test that the respond method produces the correct model for a domain instance and no specific content type"() {
        given:"A book instance"
            def book = new Book(title: "The Stand").save(flush:true)

        when:"The respond method is used to render a response"
            webRequest.actionName = 'show'
            controller.show(book)
            def modelAndView = webRequest.request.getAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW)

        then:"A modelAndView and view is produced"
            modelAndView != null
            modelAndView instanceof ModelAndView
            modelAndView.model.book == book
            modelAndView.viewName == 'show'
    }

    void "Test that the respond method produces XML for a domain instance and a content type of XML"() {
        given:"A book instance"
            def book = new Book(title: "The Stand").save(flush:true)

        when:"The respond method is used to render a response"
            response.format = 'xml'

            def result = controller.show(book)

        then:"A modelAndView and view is produced"
            result == null
            response.contentType == 'text/xml;charset=UTF-8'
            response.xml.title.text() == 'The Stand'
    }

    void "Test that the respond method produces XML for a list of domains and a content type of XML"() {
        given:"A book instance"
            def book = new Book(title: "The Stand").save(flush:true)

        when:"The respond method is used to render a response"
            response.format = 'xml'
            def result = controller.index()

        then:"A modelAndView and view is produced"
            result == null
            response.contentType == 'text/xml;charset=UTF-8'
    }

    void "Test that the respond method produces errors XML for a domain instance that has errors and a content type of XML"() {
        given:"A book instance"
            def book = new Book(title: "")
            book.validate()

        when:"The respond method is used to render a response"
            response.format = 'xml'

            def result = controller.show(book)

        then:"A modelAndView and view is produced"
            result == null
            response.contentType == 'text/xml;charset=UTF-8'
            response.xml.error.message.text() == 'Property [title] of class [class grails.rest.web.Book] cannot be null'
    }

    void "Test that the respond method produces JSON for a domain instance and a content type of JSON"() {
        given:"A book instance"
            def book = new Book(title: "The Stand").save(flush:true)

        when:"The respond method is used to render a response"
            response.format = 'json'

        def result = controller.show(book)

        then:"A modelAndView and view is produced"
            result == null
            response.contentType == 'application/json;charset=UTF-8'
            response.json.title == 'The Stand'
    }

    void "Test that the respond method produces a 406 for a format not supported"() {
        given:"A book instance"
            def book = new Book(title: "The Stand").save(flush:true)

        when:"The respond method is used to render a response"
            response.format = 'xml'

            def result = controller.showWithFormats(book.id)

        then:"A 406 status is set"
            response.status == 406
    }

    void "Test that the respond method produces JSON for an action that specifies explicit formats"() {
        given:"A book instance"
            def book = new Book(title: "The Stand").save(flush:true)

        when:"The respond method is used to render a response"
            response.format = 'json'

            def result = controller.showWithFormats(book.id)

        then:"A modelAndView and view is produced"
            result == null
            response.contentType == 'application/json;charset=UTF-8'
            response.json.title == 'The Stand'
    }

    void "Test that the respond method produces the correct model for a domain instance and content type is HTML"() {
        given:"A book instance"
        def book = new Book(title: "The Stand").save(flush:true)

        when:"The respond method is used to render a response"
        webRequest.actionName = 'showWithModel'
        controller.showWithModel(book)
        def modelAndView = webRequest.request.getAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW)

        then:"A modelAndView and view is produced"
        modelAndView != null
        modelAndView instanceof ModelAndView
        modelAndView.model == [book: book, extra: true]
        modelAndView.viewName == 'showWithModel'
    }

    void "Test that the respond method produces errors HTML for a domain instance that has errors and a content type of HTML"() {
        given:"A book instance"
        def book = new Book(title: "")
        book.validate()

        when:"The respond method is used to render a response"
        webRequest.actionName = 'showWithModel'
        controller.showWithModel(book)
        def modelAndView = webRequest.request.getAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW)

        then:"A modelAndView and view is produced"
        modelAndView != null
        modelAndView instanceof ModelAndView
        modelAndView.model == [book: book, extra: true]
        modelAndView.viewName == 'showWithModel'
    }

    void "Test that proxyHandler is used for unwrapping wrapped model"() {
        given:"A book instance"
        def book = new Book(title: "")
        book.validate()
        controller.proxyHandler = new TestProxyHandler()
        when:"The respond method is used to render a response"
        webRequest.actionName = 'showWithModel'
        controller.respond(new BookProxy(book: book), model: [extra: true])
        def modelAndView = webRequest.request.getAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW)

        then:"A modelAndView and view is produced"
        modelAndView != null
        modelAndView instanceof ModelAndView
        modelAndView.model == [book: book, extra: true]
        modelAndView.viewName == 'showWithModel'
    }

    void "Test that proxyHandler is used for unwrapping proxy collections"() {
        given:"A book instance"
        def book = new Book(title: "")
        book.validate()

        def renderer=applicationContext.getBean('rendererRegistry').findRenderer(MimeType.HTML, [])
        renderer.proxyHandler = new TestProxyHandler()
        
        when:"The respond method is used to render a response"
        webRequest.actionName = 'showWithModel'
        controller.respond([new BookProxy(book: book)], model: [extra: true])
        def modelAndView = webRequest.request.getAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW)

        then:"A modelAndView and view is produced"
        modelAndView != null
        modelAndView instanceof ModelAndView
        modelAndView.model.containsKey('bookList')
        modelAndView.model.extra == true
        modelAndView.viewName == 'showWithModel'
    }

    @Issue(['apache/grails-core#610', 'apache/grails-core#611'])
    void 'Test respond with a single Map argument'() {
        when:
        response.format = 'json'
        controller.respondWithMap()

        then:
        response.json.name == 'Jeff'
    }

    @Issue(['apache/grails-core#610', 'apache/grails-core#611'])
    void 'Test respond with a Map argument and named arguments'() {
        when:
        response.format = 'json'
        controller.respondWithMapAndNamedArguments()

        then:
        response.json.name == 'Jeff'
        response.status == 201
    }
}

@Artefact("Controller")
class BookController {
    def show(Book b) {
        respond b
    }

    def showWithModel(Book b) {
        respond b, model: [extra: true]
    }

    def index() {
        respond Book.list()
    }

    def showWithFormats(Long id) {
        respond Book.get(id), formats:['json', 'html']
    }

    def respondWithMap() {
        respond([name: 'Jeff'])
    }

    def respondWithMapAndNamedArguments() {
        respond([name: 'Jeff'], status: 201)
    }
}
@Entity
class Book {
    String title

    static constraints = {
        title blank:false
    }
}

class BookProxy {
    Book book
}

class TestProxyHandler implements ProxyHandler {
    @Override
    public boolean isProxy(Object o) {
        false
    }

    @Override
    public Object unwrapIfProxy(Object instance) {
        if(instance instanceof BookProxy)
            return instance.book
        instance
    }

    @Override
    public boolean isInitialized(Object o) {
        true
    }

    @Override
    public void initialize(Object o) {
        
    }

    @Override
    public boolean isInitialized(Object obj, String associationName) {
        true
    }
}
