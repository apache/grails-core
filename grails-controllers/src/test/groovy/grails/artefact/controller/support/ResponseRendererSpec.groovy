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
package grails.artefact.controller.support

import grails.util.GrailsWebMockUtil
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.servlet.mvc.ParameterCreationListener
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.context.request.RequestContextHolder
import spock.lang.Specification

class ResponseRendererSpec extends Specification {

    void cleanup() {
        RequestContextHolder.setRequestAttributes(null)
    }

    void 'rendering an object uses text plain for inspect output'() {
        given:
        GrailsWebRequest webRequest = bindWebRequest()
        def renderer = new TestResponseRenderer()

        when:
        renderer.render(new InspectableResponseValue('<script>alert(1)</script>'))

        then:
        webRequest.response.contentType == 'text/plain;charset=utf-8'
        webRequest.response.contentAsString == '<script>alert(1)</script>'
    }

    void 'rendering an unrecognized map uses text plain for inspect output'() {
        given:
        GrailsWebRequest webRequest = bindWebRequest()
        def renderer = new TestResponseRenderer()

        when:
        renderer.render([unsafe: '<script>alert(1)</script>'])

        then:
        webRequest.response.contentType == 'text/plain;charset=utf-8'
        webRequest.response.contentAsString == "['unsafe':'<script>alert(1)</script>']"
    }

    void 'file renders are attachments by default without a file name'() {
        given:
        GrailsWebRequest webRequest = bindWebRequest()
        def renderer = new TestResponseRenderer()

        when:
        renderer.render(file: '<svg/>'.bytes, contentType: 'image/svg+xml')

        then:
        webRequest.response.getHeader('Content-Disposition') == 'attachment'
        webRequest.response.contentAsByteArray == '<svg/>'.bytes
    }

    void 'file renders use the resolved file name for attachment disposition'() {
        given:
        GrailsWebRequest webRequest = bindWebRequest()
        File file = File.createTempFile('grails-render-', '.txt')
        file.text = 'download body'
        def renderer = new TestResponseRenderer()

        when:
        renderer.render(file: file, contentType: 'text/plain')

        then:
        webRequest.response.getHeader('Content-Disposition') == "attachment;filename=\"${file.name}\""
        webRequest.response.contentAsString == 'download body'

        cleanup:
        file.delete()
    }

    void 'file renders escape unsafe attachment file names'() {
        given:
        GrailsWebRequest webRequest = bindWebRequest()
        def renderer = new TestResponseRenderer()

        when:
        renderer.render(file: 'download body'.bytes, contentType: 'text/plain', fileName: 'a"b\\c\r\n.txt')

        then:
        webRequest.response.getHeader('Content-Disposition') == 'attachment;filename="a\\"b\\\\c__.txt"'
        webRequest.response.contentAsString == 'download body'
    }

    void 'file renders may explicitly opt into inline disposition'() {
        given:
        GrailsWebRequest webRequest = bindWebRequest()
        def renderer = new TestResponseRenderer()

        when:
        renderer.render(file: '<svg/>'.bytes, contentType: 'image/svg+xml', inline: true)

        then:
        webRequest.response.getHeader('Content-Disposition') == null
    }

    void 'file renders preserve an existing content disposition header'() {
        given:
        GrailsWebRequest webRequest = bindWebRequest()
        webRequest.response.setHeader('Content-Disposition', 'inline')
        def renderer = new TestResponseRenderer()

        when:
        renderer.render(file: '<svg/>'.bytes, contentType: 'image/svg+xml')

        then:
        webRequest.response.getHeader('Content-Disposition') == 'inline'
    }

    private GrailsWebRequest bindWebRequest() {
        WebApplicationContext applicationContext = Mock(WebApplicationContext)
        applicationContext.getBeansOfType(ParameterCreationListener) >> [:]
        GrailsWebMockUtil.bindMockWebRequest(
                applicationContext,
                new MockHttpServletRequest(),
                new MockHttpServletResponse())
    }
}

class TestResponseRenderer implements ResponseRenderer {
}

class InspectableResponseValue {

    private final String inspectedValue

    InspectableResponseValue(String inspectedValue) {
        this.inspectedValue = inspectedValue
    }

    @Override
    String toString() {
        inspectedValue
    }
}
