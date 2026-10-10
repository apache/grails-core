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
package org.grails.web.converters.json.legacy

import java.math.RoundingMode
import java.time.ZoneId

import spock.lang.Specification
import tools.jackson.core.JsonGenerator
import tools.jackson.core.StreamWriteFeature
import tools.jackson.core.json.JsonWriteFeature
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule
import tools.jackson.databind.ser.std.StdSerializer

import org.springframework.context.ApplicationContext
import org.springframework.context.support.GenericApplicationContext

import grails.converters.JSON
import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.core.support.proxy.DefaultProxyHandler
import grails.core.support.proxy.ProxyHandler
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.grails.web.converters.configuration.ConvertersConfigurationHolder
import org.grails.web.converters.configuration.ConvertersConfigurationInitializer
import org.grails.web.json.DateTimeValues
import org.grails.web.json.PrettyPrintJSONWriter

/**
 * With {@code grails.converters.json.legacy}, the JSON converter renders JSON as Grails 8 rendered it. The expected
 * text was rendered by Grails 8.0.x (232bb34006), with the same values, in UTC.
 */
class Grails8JsonRenderingSpec extends Specification {

    private static final Map<String, Object> LEGACY = ['grails.converters.json.legacy': true]

    TimeZone zone

    GrailsApplication grailsApplication

    void setup() {
        zone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone('UTC'))
        grailsApplication = new DefaultGrailsApplication()
        grailsApplication.config.setAt('grails.converters.json.legacy', true)
        grailsApplication.initialise()
        def mappingContext = new KeyValueMappingContext('json')
        grailsApplication.setApplicationContext(Stub(ApplicationContext) {
            getBean('grailsDomainClassMappingContext', MappingContext) >> mappingContext
        })
        grailsApplication.setMappingContext(mappingContext)
        new ConvertersConfigurationInitializer(grailsApplication: grailsApplication).initialize()
    }

    void cleanup() {
        ConvertersConfigurationHolder.clear()
        TimeZone.setDefault(zone)
    }

    void "every value renders as Grails 8 rendered it"() {
        when:
        def rendered = (DateTimeValues.all() + other()).collect { new JSON([value: it]).toString() }

        then:
        rendered == GRAILS_8_VALUES
    }

    void "toString(true) indents as Grails 8 indented it"() {
        expect:
        pretty().collect { new JSON(it).toString(true) } == GRAILS_8_TO_STRING_PRETTY
    }

    void "rendering with pretty printing configured indents as Grails 8 indented it"() {
        when:
        def rendered = pretty().collect {
            def json = new JSON(it)
            json.prettyPrint = true
            def out = new StringWriter()
            json.render(out)
            out.toString()
        }

        then: 'with the platform line separator'
        rendered == GRAILS_8_RENDER_PRETTY.collect { it.replace('\n', PrettyPrintJSONWriter.NEWLINE) }
    }

    void "domain class instances render as Grails 8 rendered them"() {
        expect: 'associations, embedded values, proxies, includes and excludes, deep, the version and the class name'
        Grails8DomainRendering.renderAll(LEGACY) == GRAILS_8_DOMAIN
    }

    void "the application's JsonMapper does not change the text"() {
        given: 'a mapper that indents, writes numbers as strings, plain BigDecimals, dates in New York and a Cover itself'
        def context = new GenericApplicationContext()
        context.registerBean(JsonMapper, { configuredMapper() })
        context.registerBean(ProxyHandler, { new DefaultProxyHandler() })
        context.refresh()
        new ConvertersConfigurationInitializer(grailsApplication: grailsApplication, applicationContext: context).initialize()

        expect:
        (DateTimeValues.all() + other()).collect { new JSON([value: it]).toString() } == GRAILS_8_VALUES
        pretty().collect { new JSON(it).toString(true) } == GRAILS_8_TO_STRING_PRETTY

        and: 'a Code, which has a @JsonValue, and a Cover, which the mapper has a serializer for, render as beans'
        Grails8DomainRendering.renderAll(LEGACY, [(JsonMapper): configuredMapper()]) == GRAILS_8_DOMAIN

        cleanup:
        context.close()
    }

    void "toString(false) indents when pretty printing is configured, as Grails 8 ignored the argument"() {
        given:
        grailsApplication.config.setAt('grails.converters.json.pretty.print', true)
        new ConvertersConfigurationInitializer(grailsApplication: grailsApplication).initialize()

        expect:
        pretty().collect { new JSON(it).toString(false) } ==
                GRAILS_8_RENDER_PRETTY.collect { it.replace('\n', PrettyPrintJSONWriter.NEWLINE) }
        pretty().collect { new JSON(it).toString(true) } == GRAILS_8_TO_STRING_PRETTY
    }

    private static JsonMapper configuredMapper() {
        JsonMapper.builder()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .enable(JsonWriteFeature.WRITE_NUMBERS_AS_STRINGS)
                .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
                .defaultTimeZone(TimeZone.getTimeZone('America/New_York'))
                .addModule(new SimpleModule().addSerializer(Cover, new CoverSerializer()))
                .build()
    }

    private static List<Object> other() {
        [
                'say "hi"', 'a\\b', 'a/b', 'tab\tnew\nline\u0001', 'caf\u00e9 \u4e2d', 'x\u0085y', '</script>', 'x\u2028y', 42,
                9007199254740993L, 1.0d, 2.50d, 1.0e20d, 1.5f, new BigDecimal('1.10'), new BigDecimal('1E+3'),
                new BigInteger('123456789012345678901234567890'), true, 'c' as char,
                [1, 2, 3] as byte[], [1, 2] as int[], UUID.fromString('123e4567-e89b-12d3-a456-426614174000'),
                new URL('https://grails.apache.org/a?b=c'), new URI('https://grails.apache.org/a?b=c'), Locale.US,
                Locale.forLanguageTag('zh-Hant-TW'), Currency.getInstance('USD'), String, Role.DISPATCHER,
                Optional.of('x'), Optional.empty(), new StringBuilder('sb'),
                BigDecimal.ONE.divide(new BigDecimal(3), 5, RoundingMode.HALF_UP), ZoneId.of('Europe/Paris'),
                [1, 'a', null], [a: [b: [1, [c: 2]]]], [(1): 'one'], new Point(1, 2), new Html('plain'),
                Labelled.ONE, new Holder(name: 'Fred', created: new Date(1790305200000L), role: Role.ADMIN, amount: new BigDecimal('10.50'))
        ]
    }

    private static List<Object> pretty() {
        [
                [a: 1, b: [1, 2], c: [d: 'e'], empty: [], emptyMap: [:], list: [[x: 1], [2, 3]], text: '</b>'],
                [1, [2, 3], [a: 1], []],
                [:],
                []
        ]
    }

    private static final List<String> GRAILS_8_VALUES = [
            '{"value":"2025-10-08T07:48:46.407Z"}',
            '{"value":"2026-09-25T03:00:00.000Z"}',
            '{"value":"1969-12-31T23:59:59.999Z"}',
            '{"value":"0999-05-27T00:00:00.000Z"}',
            '{"value":"+12345-01-01T00:00:00.000Z"}',
            '{"value":"+0000-01-01T00:00:00.000Z"}',
            '{"value":"-0043-03-15T00:00:00.000Z"}',
            '{"value":"2025-10-08T07:48:46.407Z"}',
            '{"value":"2025-10-08T07:48:46.407Z"}',
            '{"value":"2026-09-25T00:00:00.000Z"}',
            '{"value":"07:48:46"}',
            '{"value":"03:00:00"}',
            '{"value":"2025-10-08T07:48:46.407Z"}',
            '{"value":"2026-09-25T03:00:00.000Z"}',
            '{"value":"2025-10-08T07:48:46.407Z"}',
            '{"value":"2025-10-08T00:00:00.000Z"}',
            '{"value":"2025-10-08T07:48:46.407254Z"}',
            '{"value":"2026-09-25T03:00:00Z"}',
            '{"value":"1970-01-01T00:00:00Z"}',
            '{"value":"+12345-01-01T00:00:00Z"}',
            '{"value":"2026-09-25"}',
            '{"value":"+12345-01-01"}',
            '{"value":"01:48:46.407254"}',
            '{"value":"03:00:00"}',
            '{"value":"03:00:00.5"}',
            '{"value":"2025-10-08T01:48:46.407254"}',
            '{"value":"2026-09-25T03:00:00"}',
            '{"value":"2025-10-08T01:48:46.407254-06:00"}',
            '{"value":"2026-09-25T03:00:00Z"}',
            '{"value":"03:00:00-03:00"}',
            '{"value":"03:00:00.5+05:30"}',
            '{"value":"2026-09-25T00:00:00-03:00"}',
            '{"value":"2026-09-25T03:00:00Z"}',
            '{"value":"2026-09-25T03:00:00Z"}',
            '{"value":2026}',
            '{"value":-44}',
            '{"value":"2026-09"}',
            '{"value":"-0001-12"}',
            '{"value":"--02-29"}',
            '{"value":"JANUARY"}',
            '{"value":"DECEMBER"}',
            '{"value":"FRIDAY"}',
            '{"value":"PT1H30M0.25S"}',
            '{"value":"PT0S"}',
            '{"value":"PT-1M-30S"}',
            '{"value":"P1Y2M3D"}',
            '{"value":"P0D"}',
            '{"value":"America/Sao_Paulo"}',
            '{"value":"GMT+02:00"}',
            '{"value":"-03:00"}',
            '{"value":"Z"}',
            '{"value":"America/Sao_Paulo"}',
            '{"value":"GMT+02:00"}',
            '{"value":"Custom/Zone"}',
            '{"value":"P1DT2H"}',
            '{"value":{"2025-10-08T07:48:46.407Z":"value"}}',
            '{"value":{"2025-10-08T07:48:46.407Z":"value"}}',
            '{"value":{"2025-10-08T07:48:46.407Z":"value"}}',
            '{"value":{"1970-01-01T03:00:00.000Z":"value"}}',
            '{"value":{"2025-10-08T07:48:46.407Z":"value"}}',
            '{"value":{"2025-10-08T04:48:46.407254-03:00":"value"}}',
            '{"value":{"2026-09-25T03:00:00Z":"value"}}',
            '{"value":{"2025-10-08T07:48:46.407254Z":"value"}}',
            '{"value":{"2026-09-25":"value"}}',
            '{"value":{"2026-09-25T03:00":"value"}}',
            '{"value":{"03:00":"value"}}',
            '{"value":{"2026-09-25T00:00-03:00":"value"}}',
            '{"value":{"PT1H30M":"value"}}',
            '{"value":{"2026":"value"}}',
            '{"value":{"America/Sao_Paulo":"value"}}',
            '{"value":"say \\"hi\\""}',
            '{"value":"a\\\\b"}',
            '{"value":"a/b"}',
            '{"value":"tab\\tnew\\nline\\u0001"}',
            '{"value":"caf\u00e9 \u4e2d"}',
            '{"value":"x\u0085y"}',
            '{"value":"<\\u002fscript>"}',
            '{"value":"x\\u2028y"}',
            '{"value":42}',
            '{"value":9007199254740993}',
            '{"value":1.0}',
            '{"value":2.5}',
            '{"value":1.0E20}',
            '{"value":1.5}',
            '{"value":1.10}',
            '{"value":1E+3}',
            '{"value":123456789012345678901234567890}',
            '{"value":true}',
            '{"value":{}}',
            '{"value":[1,2,3]}',
            '{"value":[1,2]}',
            '{"value":{"leastSignificantBits":-6605018797301088256,"mostSignificantBits":1314564453825188563}}',
            '{"value":"https://grails.apache.org/a?b=c"}',
            '{"value":{"absolute":true,"authority":"grails.apache.org","fragment":null,"host":"grails.apache.org","opaque":false,"path":"/a","port":-1,"query":"b=c","rawAuthority":"grails.apache.org","rawFragment":null,"rawPath":"/a","rawQuery":"b=c","rawSchemeSpecificPart":"//grails.apache.org/a?b=c","rawUserInfo":null,"scheme":"https","schemeSpecificPart":"//grails.apache.org/a?b=c","userInfo":null}}',
            '{"value":"en_US"}',
            '{"value":"zh_TW_#Hant"}',
            '{"value":"USD"}',
            '{"value":"java.lang.String"}',
            '{"value":"DISPATCHER"}',
            '{"value":{"empty":false,"present":true}}',
            '{"value":{"empty":true,"present":false}}',
            '{"value":"sb"}',
            '{"value":0.33333}',
            '{"value":"Europe/Paris"}',
            '{"value":[1,"a",null]}',
            '{"value":{"a":{"b":[1,{"c":2}]}}}',
            '{"value":{"1":"one"}}',
            '{"value":{"x":1,"y":2}}',
            '{"value":{"value":"plain"}}',
            '{"value":"ONE"}',
            '{"value":{"amount":10.50,"created":"2026-09-25T03:00:00.000Z","name":"Fred","role":"ADMIN"}}'
    ]

    /**
     * The JSON of each {@link Grails8DomainRendering} scenario, rendered by Grails 8.0.x (232bb34006), in UTC.
     */
    private static final List<String> GRAILS_8_DOMAIN = [
            '{"id":1,"title":"The Dispossessed","published":"2026-09-25T03:00:00.000Z","role":"HEAD","price":12.50,"author":{"id":7},"editor":{"id":8},"translator":null,"chapters":[{"id":1},{"id":2}],"chaptersByName":{"one":{"id":1}},"cover":{"colour":"blue","pages":387}}',
            '{"class":"org.grails.web.converters.json.legacy.Novel","id":1,"version":3,"title":"The Dispossessed","published":"2026-09-25T03:00:00.000Z","role":"HEAD","price":12.50,"author":{"class":"org.grails.web.converters.json.legacy.Author","id":7},"editor":{"class":"org.grails.web.converters.json.legacy.Author","id":8},"translator":null,"chapters":[{"class":"org.grails.web.converters.json.legacy.Chapter","id":1},{"class":"org.grails.web.converters.json.legacy.Chapter","id":2}],"chaptersByName":{"one":{"class":"org.grails.web.converters.json.legacy.Chapter","id":1}},"cover":{"colour":"blue","pages":387}}',
            '{"id":1,"title":"The Dispossessed","published":"2026-09-25T03:00:00.000Z","role":"HEAD","price":12.50,"author":{"id":7,"name":"Ursula"},"editor":{"id":8,"name":"Virginia"},"translator":null,"chapters":[{"id":1,"heading":"One"},{"id":2,"heading":"Two"}],"chaptersByName":{"one":{"id":1,"heading":"One"}},"cover":{"colour":"blue","pages":387}}',
            '{"id":1,"title":"The Dispossessed","published":"2026-09-25T03:00:00.000Z","role":"HEAD","price":12.50,"author":{"id":7,"name":"Ursula"},"editor":{"id":8,"name":"Virginia"},"translator":null,"chapters":[{"id":1,"heading":"One"},{"id":2,"heading":"Two"}],"chaptersByName":{"one":{"id":1,"heading":"One"}},"cover":{"colour":"blue","pages":387}}',
            '{"title":"The Dispossessed","author":{"id":7},"chapters":[{"id":1},{"id":2}]}',
            '{"id":1,"title":"The Dispossessed","published":"2026-09-25T03:00:00.000Z","role":"HEAD","author":{"id":7},"editor":{"id":8},"translator":null,"chaptersByName":{"one":{"id":1}}}',
            '{"title":"Unsaved","published":null,"role":null,"price":null,"author":null,"editor":null,"translator":null,"chapters":null,"chaptersByName":null,"cover":null}',
            '{"id":1,"title":"The Dispossessed","published":"2026-09-25T03:00:00.000Z","role":"HEAD","price":12.50,"author":{"id":7},"editor":{"id":8},"translator":null,"chapters":[{"id":1},{"id":2}],"chaptersByName":{"one":{"id":1}},"cover":{"colour":"blue","pages":387}}',
            '[{"id":1,"title":"The Dispossessed","published":"2026-09-25T03:00:00.000Z","role":"HEAD","price":12.50,"author":{"id":7},"editor":{"id":8},"translator":null,"chapters":[{"id":1},{"id":2}],"chaptersByName":{"one":{"id":1}},"cover":{"colour":"blue","pages":387}},{"name":"Unsaved"}]',
            '{"class java.lang.String":"class","1970-01-01T00:00:00.000Z":"date","code:k":"code"}',
            '{"code":{"code":"k"},"cover":{"colour":"red","pages":12}}'
    ]

    private static final List<String> GRAILS_8_TO_STRING_PRETTY = [
            '{\n   "a": 1,\n   "b": [\n      1,\n      2\n   ],\n   "c": {"d": "e"},\n   "emptyMap": {},\n   "text": "<\\/b>",\n   "list": [\n      {"x": 1},\n      [\n         2,\n         3\n      ]\n   ],\n   "empty": []\n}',
            '[\n   1,\n   [\n      2,\n      3\n   ],\n   {"a": 1},\n   []\n]',
            '{}',
            '[]'
    ]

    private static final List<String> GRAILS_8_RENDER_PRETTY = [
            '{\n  "a": 1,\n  "b": \n  [\n    1,\n    2\n  ],\n  "c": {\n    "d": "e"\n  },\n  "empty": \n  [\n  ],\n  "emptyMap": {\n  },\n  "list": \n  [\n    {\n      "x": 1\n    },\n    [\n      2,\n      3\n    ]\n  ],\n  "text": "<\\u002fb>"\n}',
            '\n[\n  1,\n  [\n    2,\n    3\n  ],\n  {\n    "a": 1\n  },\n  [\n  ]\n]',
            '{\n}',
            '\n[\n]'
    ]
}

enum Role { HEAD, DISPATCHER, ADMIN }

enum Labelled {
    ONE

    @Override
    String toString() { 'one!' }
}

record Point(int x, int y) {}

class Html {
    final String value

    Html(String value) { this.value = value }

    String value() { value }
}

class CoverSerializer extends StdSerializer<Cover> {

    CoverSerializer() {
        super(Cover)
    }

    @Override
    void serialize(Cover cover, JsonGenerator generator, SerializationContext context) {
        generator.writeString("cover:${cover.colour}")
    }
}

class Holder {
    String name
    Date created
    Role role
    BigDecimal amount
}
