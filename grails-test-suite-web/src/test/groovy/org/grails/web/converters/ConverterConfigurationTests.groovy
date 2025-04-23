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

import grails.converters.JSON
import grails.converters.XML
import grails.persistence.Entity
import grails.testing.gorm.DomainUnitTest
import grails.testing.web.GrailsWebUnitTest
import org.grails.web.converters.marshaller.json.DomainClassMarshaller as JsonClassMarshaller
import org.grails.web.converters.marshaller.xml.DomainClassMarshaller as XmlClassMarshaller
import org.grails.web.converters.marshaller.ClosureObjectMarshaller
import spock.lang.Specification

/**
 * Tests for the customizable Converter Configuration.
 *
 * @author Siegfried Puchbauer
 */
class ConverterConfigurationTests extends Specification implements DomainUnitTest<ConverterBook>, GrailsWebUnitTest {

    void testCustomClosureMarshallerRegistration() {
        given:
        JSON.registerObjectMarshaller(ConverterBook) {
            [id: it.id,
             title: it.title,
             foo: 'bar'
            ]
        }

        when:
        def book = new ConverterBook()
        book.id = 4711
        book.title = "The Definitive Guide to Grails"

        then:
        (book as JSON).toString() == """{"id":4711,"title":"The Definitive Guide to Grails","foo":"bar"}"""
    }

    void testDefaultConverterConfigurationObjectMarshallerRegistration() {
        given:
        JSON.registerObjectMarshaller(java.sql.Date) { it.toString() }

        JSON.registerObjectMarshaller(java.sql.Time) { it.toString() }

        when:
        def objA = new java.sql.Date(System.currentTimeMillis())
        def objB = new java.sql.Time(System.currentTimeMillis())

        then:
        ([a:objA] as JSON).toString() == """{"a":"${objA}"}""".toString()
        ([b:objB] as JSON).toString() == """{"b":"${objB}"}""".toString()
    }

    void testMarshallerRegistrationOrder() {

        given:
        JSON.registerObjectMarshaller(Date) { "FAIL" }

        JSON.registerObjectMarshaller(Date) { "SUCCESS" }

        expect:
        ([d: new Date()] as JSON).toString() == """{"d":"SUCCESS"}"""
    }

    void testMarshallerPriority() {
        given:
        def om1 = new ClosureObjectMarshaller(ConverterWidget, { "SUCCESS" })
        def om2 = new ClosureObjectMarshaller(ConverterWidget, { "FAIL" })

        JSON.registerObjectMarshaller(om1, 5)
        JSON.registerObjectMarshaller(om2, 3)

        expect:
        ([d: new ConverterWidget()] as JSON).toString() == """{"d":"SUCCESS"}"""
    }

    void testNamedConfigurations() {
        given:
        JSON.registerObjectMarshaller(Date) { "DEFAULT" }

        JSON.createNamedConfig("test-config") { cfg ->
            cfg.registerObjectMarshaller(Date) { "TEST" }
        }

        when:
        def obj = [d: new Date()]

        String initial = (obj as JSON).toString()
        String named
        JSON.use("test-config") {
            named = (obj as JSON).toString()
        }

        then:
        initial == """{"d":"DEFAULT"}"""
        named == """{"d":"TEST"}"""
        (obj as JSON).toString() == """{"d":"DEFAULT"}"""
    }

    void testDomainWithVersionConfiguration() {
        given:
        JSON.createNamedConfig("with-version") {
            it.registerObjectMarshaller(new JsonClassMarshaller(true, grailsApplication))
        }

        XML.createNamedConfig("with-version") {
            it.registerObjectMarshaller(new XmlClassMarshaller(true, grailsApplication))
        }

        when:
        String withVersionJson
        String withVersionXml
        JSON.use("with-version") {
            withVersionJson = (createBook() as JSON).toString()
        }
        XML.use("with-version") {
            withVersionXml = (createBook() as XML).toString()
        }

        then:
        withVersionJson == """{"id":4711,"version":0,"title":"The Definitive Guide to Grails","author":"Graeme Rocher"}"""
        withVersionXml == """<?xml version="1.0" encoding="UTF-8"?><converterBook id="4711" version="0"><title>The Definitive Guide to Grails</title><author>Graeme Rocher</author></converterBook>"""
    }

//    @Test
//    void testPrettyPrintConfiguration() {
//
//        JSON.createNamedConfig("pretty-print") { cfg -> cfg.prettyPrint = true }
//        XML.createNamedConfig("pretty-print") { cfg -> cfg.prettyPrint = true }
//
//def prettyJSON = """{
//  "class": "org.grails.web.converters.ConverterBook",
//  "id": 4711,
//  "author": "Graeme Rocher",
//  "title": "The Definitive Guide to Grails"
//}"""
//
//        JSON.use("pretty-print") {
//            assertEquals(prettyJSON.replaceAll('[\r\n]', ''), (createBook() as JSON).toString().replaceAll('[\r\n]', ''))
//        }
//
//        def prettyXML = """<?xml version="1.0" encoding="UTF-8"?>
//<converterBook id="4711">
//  <author>
//    Graeme Rocher
//  </author>
//  <title>
//    The Definitive Guide to Grails
//  </title>
//</converterBook>"""
//        XML.use("pretty-print") {
//            assertEquals(prettyXML.replaceAll('[\r\n]', ''), (createBook() as XML).toString().replaceAll('[\r\n]', '').trim())
//        }
//    }

    protected createBook() {
        def book = new ConverterBook()
        book.id = 4711
        book.version = 0
        book.title = "The Definitive Guide to Grails"
        book.author = "Graeme Rocher"
        book
    }
}

@Entity
class ConverterBook {
    String title
    String author
}

class ConverterWidget{}
