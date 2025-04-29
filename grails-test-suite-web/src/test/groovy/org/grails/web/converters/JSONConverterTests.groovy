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

import grails.testing.gorm.DomainUnitTest
import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Specification

import grails.artefact.Artefact
import grails.converters.JSON
import grails.persistence.Entity

import org.grails.web.servlet.mvc.HibernateProxy
import org.grails.web.servlet.mvc.LazyInitializer
import org.grails.buffer.StreamCharBuffer

/**
 * Tests for the JSON converter.
 *
 * @author Graeme Rocher
 * @since 0.4
 */
class JSONConverterTests extends Specification implements ControllerUnitTest<JSONConverterController>, DomainUnitTest<Book> {

    void testNullJSONValues() {
        when:
        controller.testNullValues()

        then:
        '{}' == response.contentAsString
    }

    void testJSONConverter() {
        when:
        controller.test()
        def json = response.json

        then:
        json.title == "The Stand"
        json.author == "Stephen King"
        json.size() == 2
    }

    void testConvertErrors() {
        when:
        controller.testErrors()
        def json = response.json
        def titleError = json.errors.find { it.field == 'title' }
        def authorError = json.errors.find { it.field == 'author' }

        then:
        titleError.message == "Property [title] of class [class ${Book.name}] cannot be null".toString()
        authorError.message == "Property [author] of class [class ${Book.name}] cannot be null".toString()
    }


    void testProxiedDomainClassWithJSONConverter() {
        given:
        def obj = new Book()
        obj.title = "The Stand"
        obj.author = "Stephen King"

        def hibernateInitializer = [getImplementation:{obj}] as LazyInitializer
        def proxy = [getHibernateLazyInitializer:{hibernateInitializer}] as HibernateProxy

        when:
        params.b = proxy
        controller.testProxy()
        def json = response.json

        then:
        json.title == "The Stand"
        json.author == "Stephen King"
        json.size() == 2
    }

    void testJSONEnumConverting() {
        when:
        def enumInstance = Role.HEAD
        params.e = enumInstance
        controller.testEnum()
        def json = response.json

        then:
        json.enumType == "org.grails.web.converters.Role"
        json.name == "HEAD"
        json.size() == 2
    }

    // GRAILS-11513
    void testStringsWithQuotes() {
        when:
        def json = [quotedString: "I contain a \"Quote\"!", nonquotedString: "I don't!"] as JSON

        then:
        json.toString() == '{"quotedString":"I contain a \\"Quote\\"!","nonquotedString":"I don\'t!"}'
    }

    void testGStringsWithQuotes() {
        when:
        def json = [quotedString: "I contain a \"${'Quote'}\"!", nonquotedString: "I ${'don'}'t!"] as JSON
        then:
        json.toString() == '{"quotedString":"I contain a \\"Quote\\"!","nonquotedString":"I don\'t!"}'
    }

    void testStreamCharBufferWithQuotes() {
        when:
        def quotedBuffer = new StreamCharBuffer()
        quotedBuffer.writer << "I contain a \"Quote\"!"
        def nonquotedBuffer = new StreamCharBuffer()
        nonquotedBuffer.writer << "I don't!"
        def json = [quotedString: quotedBuffer, nonquotedString: nonquotedBuffer] as JSON

        then:
        json.toString() == '{"quotedString":"I contain a \\"Quote\\"!","nonquotedString":"I don\'t!"}'
    }

    void testObjectWithQuotes() {
        when:
        def json = [quotedString: new CustomCharSequence("I contain a \"Quote\"!"), nonquotedString: new CustomCharSequence("I don't!")] as JSON

        then:
        json.toString() == '{"quotedString":"I contain a \\"Quote\\"!","nonquotedString":"I don\'t!"}'
    }

    // GRAILS-11515
    void testJsonMultilineSerialization() {
        when:
        String multiLine = "first line \n second line"
        def object = [ line: multiLine ]
        def result = object as JSON

        then:
        result.toString() == '{"line":"first line \\n second line"}'
    }

    // GRAILS-11530
    void testMoreStringsWithQuotes() {
        when:
        def str = 'Hi, this is my "test"'
        def json = new grails.converters.JSON([a:str])

        then:
        json.toString() == '{"a":"Hi, this is my \\"test\\""}'
    }

    // GRAILS-11517
    void testMoreStringsWithQuotes2() {
        expect:
        '{"key":"<a href=\\"#\\" class=\\"link\\">link<\\u002fa>"}' == (['key': '<a href="#" class="link">link</a>'] as JSON).toString()
    }

    // GRAILS-10393
    void testJavaClassDoesntRenderClassProperty() {
        expect:
        '{"age":86,"name":"Sally"}' == (new Author("Sally", 86) as JSON).toString()
    }
}

enum Role { HEAD, DISPATCHER, ADMIN }

@Artefact("Controller")
class JSONConverterController {
    def test = {
       def b = new Book(title:'The Stand', author:'Stephen King')
       render b as JSON
    }

    def testProxy = {
       render params.b as JSON
    }

    def testErrors = {
        def b = new Book()
        b.validate()
        render b.errors as JSON
    }

   def testEnum = {
       render params.e as JSON
   }

    def testNullValues = {
        def descriptors = [:]
        descriptors.put(null,null)
        render descriptors as JSON
    }
}

@Entity
class Book {
   Long id
   Long version
   String title
   String author
}

class CustomCharSequence implements CharSequence {
    String source

    CustomCharSequence(String source) {
        this.source = source
    }

    @Override
    public int length() {
        source.length()
    }

    @Override
    public char charAt(int index) {
        source.charAt(index)
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        source.subSequence(start, end)
    }

    @Override
    public String toString() {
        source
    }
}
