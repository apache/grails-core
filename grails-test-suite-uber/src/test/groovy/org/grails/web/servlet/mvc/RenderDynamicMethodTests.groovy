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
package org.grails.web.servlet.mvc

import grails.testing.web.controllers.ControllerUnitTest
import org.grails.web.util.WebUtils
import spock.lang.Specification
import grails.artefact.Artefact
import org.grails.buffer.FastStringWriter

class RenderDynamicMethodTests extends Specification implements ControllerUnitTest<RenderDynamicMethodTestController>  {

    void testRenderTextWithLayout() {
        when:
        controller.renderTextWithLayout()

        then:
        response.contentType == response.contentType
        response.contentAsString == response.contentAsString
        request.getAttribute(WebUtils.LAYOUT_ATTRIBUTE) == "bar"
    }

    void testRenderView() {
        when:
        controller.renderView()

        then:
        controller.modelAndView.viewName == controller.modelAndView.viewName
    }

    void testRenderText() {
        when:
        controller.renderText()

        then:
        response.contentType == response.contentType
        response.contentAsString == response.contentAsString
    }

    void testRenderStreamCharBuffer() {
        when:
        controller.renderStreamCharBuffer()

        then:
        response.contentType == response.contentType
        response.contentAsString == response.contentAsString
    }

    void testRenderGString() {
        when:
        controller.renderGString()

        then:
        response.contentType == response.contentType
        response.contentAsString == response.contentAsString
    }

    void testRenderTextWithContentType() {
        when:
        controller.renderTextWithContentType()

        then:
        response.contentType == response.contentType
        response.contentAsString == response.contentAsString
    }

    void testRenderTextWithContentTypeAndCharset() {
        when:
        controller.renderTextWithContentTypeAndCharset()

        then:
        response.contentType == response.contentType
        response.contentAsString == response.contentAsString
    }

    void testRenderXml() {
        when:
        controller.renderXml()

        then:
        response.contentType == response.contentType
        response.contentAsString == response.contentAsString
    }

    void testRenderNonAsciiXml() {
        when:
        controller.renderNonAsciiXml()

        then:
        response.contentType == response.contentType
        response.contentAsString == response.contentAsString
    }

    void testRenderJSON() {
        when:
        controller.renderJSON()

        then:
        response.contentType == response.contentType
        response.contentAsString == response.contentAsString
    }

    void testStatusAndText() {
        when:
        controller.renderStatusAndText()

        then:
        response.contentAsString == response.contentAsString
        response.status == response.status
    }

    void testStatusOnly() {
        when:
        controller.renderStatusOnly()

        then:
        response.contentAsString == response.contentAsString
        response.status == response.status
    }

    void testRenderFile() {
        when:
        controller.renderFile()

        then:
        response.contentAsString == response.contentAsString
        response.contentType == response.contentType
    }
}

@Artefact('Controller')
class RenderDynamicMethodTestController {
    def renderText = {
        render "text"
    }

    def renderStreamCharBuffer = {
        def writer = new FastStringWriter()
        writer.write("text")
        render writer.buffer
    }

    def renderTextWithLayout = {
        render text:"foo", layout:"bar"
    }

    def renderGString = {
        render "${'te' + 'xt'}"
    }

    def renderTextWithContentType = {
        render(text:"<foo>bar</foo>",contentType:"text/xml", encoding:"utf-16")
    }

    def renderTextWithContentTypeAndCharset = {
        render(text:"<foo>bar</foo>",contentType:"text/xml;charset=utf-16")
    }

    def renderXml = {
        render(contentType:"text/xml") {
            foo {
                bar("hello")
            }
        }
    }

    def renderNonAsciiXml = {
        render(contentType:"text/xml;charset=utf-8") {
            foo {
                bar("hello öäåÖÄÅ")
            }
        }
    }

    def renderJSON = {
        render(contentType:"application/json") {
            foo( [ "hello" ] ) {
                bar it
            }
        }
    }
    def renderView ={
        render(view:'foo')
    }
    def renderXmlView = {
        render(view:'foo', contentType:'text/xml')
    }
    def renderXmlUtf16View = {
        render(view:'foo', contentType:'text/xml', encoding:'utf-16')
    }
    def renderStatusAndText = {
        render(status: 503, text: 'five oh three')
    }
    def renderStatusOnly = {
        render(status: 404)
    }
    def renderFile = {
        render file:'foo'.bytes, contentType: 'text/plain'
    }
}
