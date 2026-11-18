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
package org.grails.web.converters

import groovy.transform.CompileStatic

import jakarta.servlet.http.HttpServletRequest

import org.springframework.mock.web.MockHttpServletRequest
import spock.lang.Specification

/**
 * {@code request.XML} on a servlet request is the extension method the XML module's extension module descriptor
 * registers.
 */
class XmlConvertersExtensionSpec extends Specification {

    void 'request.XML parses the XML body of a servlet request through the extension the XML module registers'() {
        given: "Spring's mock request, which, unlike a Grails unit test's, has no XML property of its own"
        def request = new MockHttpServletRequest()
        request.contentType = 'application/xml'
        request.content = '<book><title>The Stand</title></book>'.getBytes('UTF-8')

        expect:
        request.XML.title.text() == 'The Stand'
    }

    void "request['XML'] is the parsed body too, not a request attribute"() {
        given:
        def request = new MockHttpServletRequest()
        request.contentType = 'application/xml'
        request.content = '<book><title>The Stand</title></book>'.getBytes('UTF-8')

        expect:
        request['XML'].title.text() == 'The Stand'
    }

    void 'request.XML parses the XML body in a class compiled with @CompileStatic'() {
        given:
        def reader = new GroovyClassLoader(getClass().classLoader).parseClass('''
            import groovy.transform.CompileStatic
            import jakarta.servlet.http.HttpServletRequest

            @CompileStatic
            class StaticXmlReader {
                static Object read(HttpServletRequest request) {
                    request.XML
                }
            }
        ''')

        expect:
        reader.read(xmlRequest()).title.text() == 'The Stand'
    }

    void 'a library compiled against Grails 8 reads request.XML and request.JSON without being recompiled'() {
        given:
        def json = new MockHttpServletRequest()
        json.contentType = 'application/json'
        json.content = '{"title":"The Stand"}'.getBytes('UTF-8')

        expect:
        Grails8CompiledXmlReader.xml(xmlRequest()).title.text() == 'The Stand'
        Grails8CompiledXmlReader.json(json).title == 'The Stand'
    }

    private static MockHttpServletRequest xmlRequest() {
        def request = new MockHttpServletRequest()
        request.contentType = 'application/xml'
        request.content = '<book><title>The Stand</title></book>'.getBytes('UTF-8')
        request
    }
}

/** Calls the methods Grails 8's static compiler called for request.XML and request.JSON. */
@CompileStatic
@SuppressWarnings('deprecation')
class Grails8CompiledXmlReader {

    static Object xml(HttpServletRequest request) {
        ConvertersExtension.getXML(request)
    }

    static Object json(HttpServletRequest request) {
        ConvertersExtension.getJSON(request)
    }
}
