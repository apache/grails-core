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
package org.grails.plugins.web.rest.render.html

import grails.persistence.Entity
import grails.validation.ValidationErrors
import grails.web.mime.MimeType
import org.grails.web.util.GrailsApplicationAttributes
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.plugins.web.rest.render.ServletRenderContext
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.web.servlet.ModelAndView
import spock.lang.Specification

/**
 * @author Graeme Rocher
 */

class HtmlRendererSpec extends Specification {


    void "Test that HTML renderer sets a model and view correctly for a domain instance"() {
        when:"A domain instance is rendered"
            def renderer = new DefaultHtmlRenderer(Book)
            final webRequest = new GrailsWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockServletContext())
            webRequest.actionName = "test"
            def renderContext = new ServletRenderContext(webRequest) {
                @Override
                MimeType getAcceptMimeType() {
                    MimeType.HTML
                }
            }
            final book = new Book(title: "The Stand")
            renderer.render(book,renderContext)

            ModelAndView modelAndView = webRequest.currentRequest.getAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW)
        then:"The model and view are populated correctly"
            modelAndView
            modelAndView.viewName == 'test'
            modelAndView.model == [book:book]
    }

    void "Test that HTML renderer sets a model and view correctly for a domain instance and custom view and model"() {
        when:"A domain instance is rendered"
            def renderer = new DefaultHtmlRenderer(Book)
            final webRequest = new GrailsWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockServletContext())
            webRequest.actionName = "test"
            def renderContext = new ServletRenderContext(webRequest, [model:[foo:'bar'],view:"foo"]) {
                @Override
                MimeType getAcceptMimeType() {
                    MimeType.HTML
                }
            }
            final book = new Book(title: "The Stand")
            renderer.render(book,renderContext)

            ModelAndView modelAndView = webRequest.currentRequest.getAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW)
        then:"The model and view are populated correctly"
            modelAndView
            modelAndView.viewName == 'foo'
            modelAndView.model == [foo: "bar", book:book]
    }

    void "Test that HTML renderer sets the correct model for a list of domains"() {
        when:"A domain instance is rendered"
            def renderer = new DefaultHtmlRenderer(Book)
            final webRequest = new GrailsWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockServletContext())
            webRequest.actionName = "test"
            def renderContext = new ServletRenderContext(webRequest){
                @Override
                MimeType getAcceptMimeType() {
                    MimeType.HTML
                }
            }
            final books = [new Book(title: "The Stand")]
            renderer.render(books,renderContext)

            ModelAndView modelAndView = webRequest.currentRequest.getAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW)
        then:"The model and view are populated correctly"
            modelAndView
            modelAndView.viewName == 'test'
            modelAndView.model == [bookList:books]
    }

    void "Test that HTML renderer sets the correct model for an error"() {
        when:"A domain instance is rendered"
            def renderer = new DefaultHtmlRenderer(Book)
            final webRequest = new GrailsWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockServletContext())
            webRequest.actionName = "test"
            def renderContext = new ServletRenderContext(webRequest){
                @Override
                MimeType getAcceptMimeType() {
                    MimeType.HTML
                }
            }
            final book = new Book(title: "The Stand")
            final errors = new ValidationErrors(book)
            book.errors = errors
            errors.rejectValue("title", "title.blank.error")

            renderer.render(book.errors,renderContext)

        ModelAndView modelAndView = webRequest.currentRequest.getAttribute(GrailsApplicationAttributes.MODEL_AND_VIEW)
            then:"The model and view are populated correctly"
            modelAndView
            modelAndView.viewName == 'test'
            modelAndView.model == [book:book]
        }
}
@Entity
class Book {
    String title
    static constraints = {
        title blank:false
    }
}
