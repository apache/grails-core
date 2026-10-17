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
package org.grails.web.converters.json

import org.springframework.context.support.GenericApplicationContext
import org.springframework.mock.web.MockHttpServletRequest
import spock.lang.Specification
import tools.jackson.core.json.JsonReadFeature
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper

import grails.converters.JSON
import grails.core.DefaultGrailsApplication
import grails.core.support.proxy.DefaultProxyHandler
import grails.core.support.proxy.ProxyHandler
import org.grails.web.converters.configuration.ConvertersConfigurationHolder
import org.grails.web.converters.configuration.ConvertersConfigurationInitializer
import org.grails.web.converters.exceptions.ConverterException
import org.grails.web.json.JSONArray
import org.grails.web.json.JSONObject

/**
 * With {@code grails.converters.json.legacy} set to {@code false}, {@code JSON.parse}, and so {@code request.JSON}, parse
 * with the application's Jackson mapper: what its parser accepts is what its parser features allow, and what comes out is
 * the {@code JSONObject}s and {@code JSONArray}s, and the number types, that Grails' own parser returns.
 */
class JsonMapperParsingSpec extends Specification {

    void setup() {
        use(JsonMapper.builder().build())
    }

    void cleanup() {
        ConvertersConfigurationHolder.clear()
    }

    void "JSON.parse accepts JSON"() {
        expect:
        JSON.parse('{"a":"x","b":[1,2.5,true,false,null],"c":{"d":"e"}}') ==
                [a: 'x', b: [1, 2.5d, true, false, null], c: [d: 'e']]
        JSON.parse('[1,"two",{"three":3}]') == [1, 'two', [three: 3]]
    }

    void "JSON.parse rejects #syntax, which Grails' own parser accepts"() {
        when:
        JSON.parse(text)

        then:
        thrown(ConverterException)

        where:
        syntax                          | text
        'single quotes'                 | "{'x':'a'}"
        '// comments'                   | '{"x":1 // note\n}'
        '/* */ comments'                | '{"x":1 /* note */}'
        'an unquoted string value'      | '{"x":hello}'
        'an unquoted name'              | '{x:1}'
        'a trailing comma in an object' | '{"x":1,}'
        'a trailing comma in an array'  | '[1,]'
        'a JavaScript date'             | '{"x":new Date(0)}'
        'an unterminated object'        | '{"x":1'
        'no value at all'               | ''
        'text after the value'          | '{"x":1} garbage'
        'a second value'                | '{"x":1}{"y":2}'
    }

    void "JSON.parse accepts text after the value when the mapper does not fail on trailing tokens"() {
        given:
        use(JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build())

        expect:
        JSON.parse('{"x":1} garbage') == [x: 1]
    }

    void "JSON.parse accepts the syntax the mapper's parser is configured to accept"() {
        given:
        use(JsonMapper.builder()
                .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
                .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
                .enable(JsonReadFeature.ALLOW_UNQUOTED_PROPERTY_NAMES)
                .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
                .build())

        expect:
        JSON.parse("{'x':1, y:'a' // note\n,}") == [x: 1, y: 'a']
    }

    void "JSON.parse reads NaN and Infinity as doubles when the mapper's parser accepts them"() {
        given:
        use(JsonMapper.builder().enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS).build())

        when:
        def parsed = JSON.parse('{"nan":NaN,"infinity":Infinity,"negative":-Infinity}')

        then:
        Double.isNaN(parsed.nan as double)
        parsed.infinity == Double.POSITIVE_INFINITY
        parsed.negative == Double.NEGATIVE_INFINITY
    }

    void "JSON.parse reads #number as a #type.simpleName"() {
        when:
        def value = JSON.parse("{\"n\":${number}}").n

        then:
        value.getClass() == type
        value.toString() == text

        where:
        number                    || type       | text
        '42'                      || Integer    | '42'
        '9223372036854775807'     || Long       | '9223372036854775807'
        '9223372036854775808'     || BigInteger | '9223372036854775808'
        '1.5'                     || Double     | '1.5'
        '0.123456789012345678901' || BigDecimal | '0.123456789012345678901'
        '1.00'                    || BigDecimal | '1.00'
        '1e3'                     || BigDecimal | '1E+3'
        '1e400'                   || BigDecimal | '1E+400'
        '-1e400'                  || BigDecimal | '-1E+400'
        '1.5e1'                   || Integer    | '15'
        '1E0'                     || Integer    | '1'
        '-0'                      || Integer    | '0'
    }

    void "JSON.parse reads #number as Grails' own parser does"() {
        expect: "new JSONObject(String) always parses with Grails' parser"
        JSON.parse("{\"n\":${number}}").n == new JSONObject("{\"n\":${number}}").n
        JSON.parse("{\"n\":${number}}").n.getClass() == new JSONObject("{\"n\":${number}}").n.getClass()

        where:
        number << ['42', '-7', '9223372036854775807', '9223372036854775808', '1.5', '0.123456789012345678901', '1.00',
                   '1e3', '1e400', '-1e400', '1e-400', '1.5e1', '1E0', '2.5E-3', '-0']
    }

    void "JSON.parse returns Grails' Map and List containers, with Java nulls"() {
        when:
        def json = JSON.parse('{"list":[1,{"nested":null}],"value":null}')

        then:
        json instanceof JSONObject
        json instanceof Map
        json.list instanceof JSONArray
        json.list instanceof List
        json.list[1] instanceof JSONObject
        json.list[1].nested == null
        json.value == null
        json.containsKey('value')
    }

    void "request.JSON is parsed by the mapper too"() {
        given:
        def request = new MockHttpServletRequest()
        request.contentType = 'application/json'
        request.content = '{"name":"Ada","tags":["a","b"]}'.bytes

        expect:
        JSON.parse(request) == [name: 'Ada', tags: ['a', 'b']]
        JSON.parse(request).is(JSON.parse(request))
    }

    void "a request with an unacceptable body fails with a ConverterException"() {
        given:
        def request = new MockHttpServletRequest()
        request.content = "{'name':'Ada'}".bytes

        when:
        JSON.parse(request)

        then:
        thrown(ConverterException)
    }

    /** Initializes the converters as an application with the mapper and {@code grails.converters.json.legacy: false}. */
    private static void use(JsonMapper mapper) {
        ConvertersConfigurationHolder.clear()
        def grailsApplication = new DefaultGrailsApplication()
        grailsApplication.config.setAt('grails.converters.json.legacy', false)
        grailsApplication.initialise()
        def applicationContext = new GenericApplicationContext()
        applicationContext.registerBean(ProxyHandler, { new DefaultProxyHandler() })
        applicationContext.registerBean(JsonMapper, { mapper })
        applicationContext.refresh()
        new ConvertersConfigurationInitializer(grailsApplication: grailsApplication, applicationContext: applicationContext)
                .initialize()
    }
}
