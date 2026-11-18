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

import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.springframework.mock.web.MockHttpServletRequest
import spock.lang.Specification

import grails.converters.JSON

/**
 * The converters' extensions without the grails-xml module, which this module's tests do not include.
 */
class ConvertersModuleExtensionSpec extends Specification {

    void 'request.JSON parses the JSON body in a class compiled with @CompileStatic'() {
        given:
        def reader = new GroovyClassLoader(getClass().classLoader).parseClass('''
            import groovy.transform.CompileStatic
            import jakarta.servlet.http.HttpServletRequest

            @CompileStatic
            class StaticJsonReader {
                static Object read(HttpServletRequest request) {
                    request.JSON
                }
            }
        ''')

        expect:
        reader.read(jsonRequest()).title == 'The Stand'
    }

    void 'a value converts to JSON with as'() {
        expect:
        ([title: 'The Stand'] as JSON) instanceof JSON
        ([[title: 'The Stand']] as Object[]) as JSON instanceof JSON
    }

    void 'request.XML does not compile with @CompileStatic without the XML module'() {
        when:
        new GroovyClassLoader(getClass().classLoader).parseClass('''
            import groovy.transform.CompileStatic
            import jakarta.servlet.http.HttpServletRequest

            @CompileStatic
            class StaticXmlReader {
                static Object read(HttpServletRequest request) {
                    request.XML
                }
            }
        ''')

        then:
        MultipleCompilationErrorsException e = thrown()
        e.message.contains('No such property: XML')
    }

    void 'the method Grails 8 compiled request.XML into names the XML module it requires'() {
        when:
        Grails8CompiledReader.xml(new MockHttpServletRequest())

        then:
        IllegalStateException e = thrown()
        e.message.contains('org.apache.grails:grails-xml')
    }

    void 'the methods Grails 8 compiled request.JSON and as JSON into still convert'() {
        expect:
        Grails8CompiledReader.json(jsonRequest()).title == 'The Stand'
        Grails8CompiledReader.convert([title: 'The Stand']) instanceof JSON
    }

    private static MockHttpServletRequest jsonRequest() {
        def request = new MockHttpServletRequest()
        request.contentType = 'application/json'
        request.content = '{"title":"The Stand"}'.getBytes('UTF-8')
        request
    }
}

/** Calls the methods Grails 8's static compiler called for request.XML, request.JSON and as JSON. */
@CompileStatic
@SuppressWarnings('deprecation')
class Grails8CompiledReader {

    static Object xml(HttpServletRequest request) {
        ConvertersExtension.getXML(request)
    }

    static Object json(HttpServletRequest request) {
        ConvertersExtension.getJSON(request)
    }

    static JSON convert(Object value) {
        ConvertersExtension.asType(value, JSON)
    }
}
