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

package org.grails.web.mime

import grails.converters.JSON
import grails.converters.XML
import grails.persistence.Entity
import grails.testing.gorm.DomainUnitTest
import grails.testing.web.controllers.ControllerUnitTest
import grails.web.Controller
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class ContentFormatControllerTests extends Specification implements ControllerUnitTest<ContentController>, DomainUnitTest<Gizmo> {

    Closure doWithConfig() {{ c ->
        c['grails.mime.use.accept.header'] = true
        c['grails.mime.types'] = [html: ['text/html', 'application/xhtml+xml'],
                               xml : ['text/xml', 'application/xml'],
                               text: 'text/plain',
                               js  : 'text/javascript',
                               rss : 'application/rss+xml',
                               atom: 'application/atom+xml',
                               css : 'text/css',
                               cvs : 'text/csv',
                               all : '*/*',
                               json: 'application/json'
        ]
    }}

    void testFormatWithRenderAsXML() {
        when:
        request.setParameter "format", "xml"
        controller.testWithFormatRenderAs()

        then:
        '''<?xml version="1.0" encoding="UTF-8"?><gizmo><name>iPod</name></gizmo>''' == response.contentAsString
    }

    void testFormatWithRenderAsJSON() {
        when:
        request.setParameter "format", "json"
        controller.testWithFormatRenderAs()

        then:
        """{"name":"iPod"}""".toString() == response.contentAsString
    }

    void testAllFormat() {
        when:
        request.addHeader "Accept", "*/*"
        controller.testFormat()

        then:
        "all" == response.contentAsString
    }

    void testWithFormatAndAll() {
        when:
        request.addHeader "Accept", "*/*"

        then:
        "all" == response.format

        when:
        controller.testWithFormat()

        then:
        "<html></html>" == response.contentAsString
        "html" == response.format
    }

    void testWithFormatAndAll2() {
        when:
        request.addHeader "Accept", "*/*"

        then:
        "all" == response.format

        when:
        controller.testWithFormatAndModel()

        then:
        "alert('hello')" == response.contentAsString
        "js" == response.format
    }

    void testDefaultFormat() {
        when:
        controller.testFormat()

        then:
        "all" == response.contentAsString
    }


    void testWithContentTypeAndAcceptHeader() {
        when:
        // should favour content type header
        request.addHeader "Accept", "text/javascript, text/html, application/xml, text/xml, */*"
        request.addHeader "Content-Type", "text/html"

        controller.testFormat()

        then:
        "js" == response.contentAsString
    }

    void testFirefox2AcceptHeader() {
        when:
        request.addHeader "Accept", "text/xml,application/xml,application/xhtml+xml,text/html;q=0.9,text/plain;q=0.8,image/png,*/*;q=0.5"
        controller.testFormat()

        then:
        "html" == response.contentAsString
    }

    void testFirefox3AcceptHeader() {
        when:
        request.addHeader "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        controller.testFormat()

        then:
        "html" == response.contentAsString
    }

    void testFirefox2AcceptHeaderWithFormatOrdering() {
        when:
        request.addHeader "Accept", "text/xml,application/xml,application/xhtml+xml,text/html;q=0.9,text/plain;q=0.8,image/png,*/*;q=0.5"
        controller.testWithFormatAndEqualQualityGrading()

        then:
        "<html></html>" == response.contentAsString
        "html" == request.format
    }

    void testPrototypeFormat() {
        when:
        request.addHeader "Accept", "text/javascript, text/html, application/xml, text/xml, */*"
        controller.testFormat()

        then:
        "js" == response.contentAsString
    }

    void testOverrideWithRequestParameter() {
        when:
        request.addHeader "Accept", "text/javascript, text/html, application/xml, text/xml, */*"
        request.setParameter "format", "xml"
        controller.testFormat()

        then:
        "xml" == response.contentAsString
    }

    void testOverrideWithControllerParameter() {
        when:
        request.addHeader "Accept", "text/javascript, text/html, application/xml, text/xml, */*"

        params.format = "xml"
        controller.testFormat()

        then:
        "xml" == response.contentAsString
    }

    void testWithFormatAndDefaults() {
        when:
        controller.testWithFormat()

        then:
        "<html></html>" == response.contentAsString
    }

    void testPrototypeWithFormat() {
        when:
        request.addHeader "Accept", "text/javascript, text/html, application/xml, text/xml, */*"
        controller.testWithFormat()

        then:
        "alert('hello')" == response.contentAsString
    }

    void testWithFormatParameterOverride() {
        when:
        request.setParameter "format", "js"
        webRequest.controllerName = 'content'
        controller.testWithFormat()

        then:
        "alert('hello')" == response.contentAsString
    }

    void testWithFormatAndModel() {
        when:
        request.addHeader "Accept", "text/html"
        def model = controller.testWithFormatAndModel()

        then:
        'world' == model?.hello
    }

    void testWithFormatZeroArgs() {
        when:
        request.addHeader "Accept", "text/javascript, text/html, application/xml, text/xml, */*"
        webRequest.controllerName = 'content'
        controller.testWithFormatZeroArgs()

        then:
        "html" == request.format
    }

}

@Controller
class ContentController {
    def testFormat() {
        render response.format
    }

    def testWithFormat() {
        withFormat {
            html { render "<html></html>" }
            js { render "alert('hello')" }
        }
    }

    def testWithFormatAndEqualQualityGrading() {
        withFormat {
            html { render "<html></html>" }
            xml { render(contentType:"text/xml",text: "<xml></xml>") }
        }
    }

    def testWithFormatAndModel() {
        withFormat {
            js { render "alert('hello')" }
            html hello:'world'
        }
    }

    def testWithFormatZeroArgs() {
        withFormat {
            html()
            xml()
        }
    }

    def testWithFormatRenderAs() {
        def gizmos = Gizmo.get()
        withFormat {
            html {
                render "<html>hello</html>"
            }
            xml {
                render gizmos as XML
            }
            json {
                render gizmos as JSON
            }
        }
    }
}

@Entity
class Gizmo {
    Long id
    Long version
    String name

    static get() {
        new Gizmo(name:"iPod")
    }
}
