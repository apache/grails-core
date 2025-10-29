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
package org.grails.plugins.testing

import spock.lang.Specification

import java.nio.charset.StandardCharsets

/**
 * Test case for {@link org.grails.plugins.testing.GrailsMockHttpServletRequest}.
 */
class GrailsMockHttpServletRequestTests extends Specification {

    static final String TEST_XML_CONTENT = """\
    <root>
      <item qty="10">
        <name>Orange</name>
        <type>Fruit</type>
      </item>
      <item qty="6">
        <name>Apple</name>
        <type>Fruit</type>
      </item>
      <item qty="2">
        <name>Chair</name>
        <type>Furniture</type>
      </item>
    </root>
    """.stripIndent()

    static final String TEST_JSON_CONTENT = """\
    [
        { qty: 10, name: "Orange", type: "Fruit" },
        { qty: 6, name: "Apple", type: "Fruit" },
        { qty: 2, name: "Chair", type: "Furniture" }
    ]
    """.stripIndent()

    void testGetXML() {

        given:
        def request = new GrailsMockHttpServletRequest()

        when:
        request.method = 'POST'
        request.contentType = 'application/xml; charset=UTF-8'
        request.content = TEST_XML_CONTENT.getBytes('UTF-8')

        then:
        verifyXmlResult(request.XML)
    }

    void testGetXMLMultipleCalls() {

        given:
        def request = new GrailsMockHttpServletRequest()

        when:
        request.method = 'POST'
        request.contentType = "application/xml; charset=UTF-8"
        request.content = TEST_XML_CONTENT.getBytes(StandardCharsets.UTF_8)

        then:
        verifyXmlResult request.XML
        // Try again.
        verifyXmlResult request.XML
        // And one more time.
        verifyXmlResult request.XML
    }

    void testGetXMLNoContent() {

        given:
        def request = new GrailsMockHttpServletRequest()

        when:
        request.method = 'POST'

        and:
        request.XML

        then:
        def e = thrown(Exception)
        e.message == 'Error parsing XML'
    }

    void testGetXMLContentNotXml() {

        given:
        def content = """\
        First line
        Second line
        """.stripIndent()
        def request = new GrailsMockHttpServletRequest()

        when:
        request.method = 'POST'
        request.contentType = 'text/plain; charset=UTF-8'
        request.content = content.getBytes('UTF-8')

        and:
        request.XML

        then:
        def e = thrown(Exception)
        e.message == 'Error parsing XML'
    }

    void testGetJSON() {

        given:
        def request = new GrailsMockHttpServletRequest()

        when:
        request.contentType = 'text/json; charset=UTF-8'
        request.content = TEST_JSON_CONTENT.getBytes('UTF-8')

        then:
        verifyJsonResult(request.JSON)
    }

    void testGetJSONMultipleCalls() {

        given:
        def request = new GrailsMockHttpServletRequest()

        when:
        request.contentType = 'text/json; charset=UTF-8'
        request.content = TEST_JSON_CONTENT.getBytes('UTF-8')

        then:
        verifyJsonResult(request.JSON)
        // Try again.
        verifyJsonResult(request.JSON)
        // And one more time.
        verifyJsonResult(request.JSON)
    }

    void testGetJSONNoContent() {

        given:
        def request = new GrailsMockHttpServletRequest()

        expect:
        0 == (Integer) request.JSON.size()
    }

    void testGetJSONContentNotJson() {

        given:
        def content = """\
        First line
        Second line
        """.stripIndent()
        def request = new GrailsMockHttpServletRequest()

        when:
        request.contentType = 'text/plain; charset=UTF-8'
        request.content = content.getBytes('UTF-8')

        then: 'should not contain JSON'
        request.JSON.isEmpty()
    }

    void verifyXmlResult(xml) {
        3 == (int) xml.item.size()
        'Apple' == xml.item[1].name.text() as String
        '2' ==  xml.item[2].@qty.text() as String
    }

    void verifyJsonResult(json) {
        3 == json.size() as int
        'Apple' == json[1].name as String
        2 == json[2].qty as int
    }
}
