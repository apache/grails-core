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

import groovy.transform.CompileStatic
import groovy.xml.XmlSlurper
import grails.converters.XML
import grails.core.DefaultGrailsApplication
import grails.persistence.Entity
import grails.testing.gorm.DomainUnitTest
import grails.util.GrailsWebUtil
import grails.validation.ValidationErrors
import grails.web.mime.MimeType
import groovy.xml.slurpersupport.GPathResult
import org.grails.plugins.web.rest.render.ServletRenderContext
import org.grails.web.converters.configuration.ConvertersConfigurationHolder
import org.grails.web.converters.configuration.ConvertersConfigurationInitializer
import org.grails.web.converters.marshaller.xml.ValidationErrorsMarshaller
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import spock.lang.PendingFeature
import spock.lang.Specification

/**
 * @author Graeme Rocher
 */
class DefaultXmlRendererSpec extends Specification implements DomainUnitTest<XmlBook> {

    void setup() {
        final initializer = new ConvertersConfigurationInitializer()
        initializer.grailsApplication = new DefaultGrailsApplication()
        initializer.initialize()
        XML.registerObjectMarshaller(new ValidationErrorsMarshaller())
    }

    void cleanup() {
        GroovySystem.metaClassRegistry.removeMetaClass(ValidationErrors)
        ConvertersConfigurationHolder.clear()
    }

    @PendingFeature(reason = 'java.lang.IllegalAccessException: class org.grails.web.converters.marshaller.xml.GenericJavaBeanMarshaller cannot access a member of class org.grails.datastore.mapping.model.MappingFactory$1 with modifiers "public"')
    void 'Test that XML renderer writes XML to the response for a domain instance'() {
        when: 'A domain instance is rendered'
            def renderer = new DefaultXmlRenderer(XmlBook)
            def response = new MockHttpServletResponse()
            def webRequest = new GrailsWebRequest(new MockHttpServletRequest(), response, new MockServletContext())
            webRequest.actionName = 'test'
            def renderContext = new ServletRenderContext(webRequest) {
                @Override
                MimeType getAcceptMimeType() {
                    MimeType.TEXT_XML
                }
            }
            def book = new XmlBook(title: 'The Stand')
            renderer.render(book, renderContext)

        then: 'The model and view are populated correctly'
            response.contentType == GrailsWebUtil.getContentType('text/xml', GrailsWebUtil.DEFAULT_ENCODING)
            response.status == 200

        when: 'The XML is parsed'
            def xml = new XmlSlurper().parseText(response.contentAsString)

        then: 'It is correct'
        (xml['title'] as GPathResult).text() == 'The Stand'
     }

    void 'Test that XML renderer sets a model and view correctly for an Error instance'() {
        when: 'A domain instance is rendered'
            def renderer = new DefaultXmlRenderer(XmlBook)
            def response = new MockHttpServletResponse()
            def webRequest = new GrailsWebRequest(new MockHttpServletRequest(), response, new MockServletContext())
            webRequest.actionName = 'test'
            def renderContext = new ServletRenderContext(webRequest) {
                @Override
                MimeType getAcceptMimeType() {
                    MimeType.TEXT_XML
                }
            }
            def book = new XmlBook(title: '')
            def errors = new ValidationErrors(book)
            book.errors = errors
            errors.rejectValue('title', 'title.blank.error')
            renderer.render(book.errors,renderContext)

        then: 'The model and view are populated correctly'
            response.contentType == GrailsWebUtil.getContentType('text/xml', GrailsWebUtil.DEFAULT_ENCODING)
            response.status == 422

        when: 'The XML is parsed'
            def text = response.contentAsString
            def xml = new XmlSlurper().parseText(text)

        then: 'It is correct'
            (xml['error']['@field'] as GPathResult).text() == 'title'
    }
}

@Entity
class XmlBook {

    String title

    @SuppressWarnings('unused')
    static constraints = {
        title(blank: false)
    }
}
