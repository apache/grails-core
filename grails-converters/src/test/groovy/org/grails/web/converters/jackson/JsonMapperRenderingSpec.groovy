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
package org.grails.web.converters.jackson

import java.math.RoundingMode
import java.sql.Time
import java.time.Month
import java.time.ZoneId
import java.time.temporal.ChronoUnit

import com.fasterxml.jackson.annotation.JsonValue
import spock.lang.Shared
import spock.lang.Specification
import tools.jackson.databind.json.JsonMapper

import org.springframework.context.ApplicationContext
import org.springframework.context.support.GenericApplicationContext

import grails.converters.JSON
import grails.core.DefaultGrailsApplication
import grails.core.support.proxy.DefaultProxyHandler
import grails.core.support.proxy.ProxyHandler
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.grails.web.converters.configuration.ConvertersConfigurationHolder
import org.grails.web.converters.configuration.ConvertersConfigurationInitializer
import org.grails.web.converters.configuration.ObjectMarshallerRegisterer
import org.grails.web.converters.exceptions.ConverterException
import org.grails.web.converters.marshaller.ClosureObjectMarshaller
import org.grails.web.converters.marshaller.ObjectMarshaller

/**
 * {@code grails.converters.JSON} writes JSON with the application's Jackson {@code JsonMapper}: a value renders as the
 * mapper renders it, while Grails marshallers keep rendering domain classes, beans, records, enums and containers.
 */
class JsonMapperRenderingSpec extends Specification {

    @Shared
    JsonMapper jackson = JsonMapper.builder().build()

    void setup() {
        initialize()
    }

    void cleanup() {
        ConvertersConfigurationHolder.clear()
    }

    void "a #description renders as the JsonMapper renders it"() {
        given:
        def map = [value: value]

        expect:
        new JSON(map).toString() == jackson.writeValueAsString(map)

        where:
        value << DateTimeValues.all() + otherValues()
        description = value instanceof Map ? "${value.keySet().first().class.simpleName} map key" : value.class.simpleName
    }

    void "strings are escaped for an HTML script element, as in Grails 8, wherever they are written"() {
        expect: 'keys, values, and values the mapper writes, such as a @JsonValue'
        new JSON(['</b>': '</script>', lines: 'x\u2028y\u2029z', html: new Html('<i>a</i>')]).toString() ==
                '{"<\\u002fb>":"<\\u002fscript>","lines":"x\\u2028y\\u2029z","html":"<i>a<\\u002fi>"}'
    }

    void "pretty printed JSON is indented by the JsonMapper's default pretty printer"() {
        given:
        def map = [a: 1, b: [1, 2], c: [d: 'e'], empty: []]

        expect:
        new JSON(map).toString(true) == jackson.writerWithDefaultPrettyPrinter().writeValueAsString(map)
    }

    void "enums render by name, as in Grails 8, and a Month as its number"() {
        expect:
        new JSON([role: Role.HEAD, unit: ChronoUnit.SECONDS, labelled: Labelled.ONE, month: Month.MAY]).toString() ==
                '{"role":"HEAD","unit":"SECONDS","labelled":"ONE","month":5}'
    }

    void "a single #value.class.simpleName renders as a JSON value"() {
        expect:
        new JSON(value).toString() == expected

        where:
        value        || expected
        'text'       || '"text"'
        42           || '42'
        Month.MAY    || '5'
        Role.HEAD    || '"HEAD"'
        new Date(0L) || '"1970-01-01T00:00:00.000Z"'
    }

    void "the JsonMapper from the application context is used, so spring.jackson settings apply"() {
        given: 'a mapper configured as spring.jackson.time-zone would configure it'
        def context = applicationContext {
            it.registerBean(JsonMapper, { JsonMapper.builder().defaultTimeZone(TimeZone.getTimeZone('America/New_York')).build() })
        }
        initialize([:], context)

        expect:
        new JSON([date: new Date(1759909726407L)]).toString() == '{"date":"2025-10-08T03:48:46.407-04:00"}'

        cleanup:
        context.close()
    }

    void "a default JsonMapper is used when the application context has several and none is primary"() {
        given:
        def context = applicationContext {
            it.registerBean('first', JsonMapper, { JsonMapper.builder().defaultTimeZone(TimeZone.getTimeZone('Asia/Tokyo')).build() })
            it.registerBean('second', JsonMapper, { JsonMapper.builder().defaultTimeZone(TimeZone.getTimeZone('Asia/Tokyo')).build() })
        }
        initialize([:], context)

        expect:
        new JSON([date: new Date(1759909726407L)]).toString() == '{"date":"2025-10-08T07:48:46.407Z"}'

        cleanup:
        context.close()
    }

    void "a marshaller registered with JSON.registerObjectMarshaller takes precedence, also inside records and Optionals"() {
        given:
        JSON.registerObjectMarshaller(Date) { Date date -> date.time }
        def date = new Date(1759909726407L)

        expect:
        new JSON([date: date, optional: Optional.of(date), record: new Dated(date)]).toString() ==
                '{"date":1759909726407,"optional":1759909726407,"record":{"when":1759909726407}}'
    }

    void "a marshaller an ObjectMarshallerRegisterer registers takes precedence"() {
        given:
        def context = applicationContext {
            it.registerBean(ObjectMarshallerRegisterer, {
                new ObjectMarshallerRegisterer(converterClass: JSON,
                        marshaller: new ClosureObjectMarshaller<JSON>(Date, { Date date -> date.time }))
            })
        }
        initialize([:], context)

        expect:
        new JSON([date: new Date(1759909726407L)]).toString() == '{"date":1759909726407}'

        cleanup:
        context.close()
    }

    void "beans, map entries and domain-like objects are rendered by their marshallers"() {
        given:
        def bean = new Holder(name: 'Fred', created: new Date(1790305200000L), role: Role.ADMIN, unit: ChronoUnit.DAYS,
                amount: new BigDecimal('10.50'))

        expect:
        new JSON(bean).toString() ==
                '{"amount":10.50,"created":"2026-09-25T03:00:00.000Z","name":"Fred","role":"ADMIN","unit":"DAYS"}'
        new JSON([entry: new AbstractMap.SimpleEntry('k', 1)]).toString() == '{"entry":{"key":"k","value":1}}'
    }

    void "a value a marshaller writes directly to the writer is quoted, as JSONWriter quotes it"() {
        given:
        JSON.registerObjectMarshaller(new ObjectMarshaller<JSON>() {
            boolean supports(Object object) { object instanceof Identified }
            void marshalObject(Object object, JSON json) {
                json.writer.object().key('id').value(new Identifier('5f1d</a>')).endObject()
            }
        })

        expect:
        new JSON(new Identified()).toString() == '{"id":"id-5f1d<\\u002fa>"}'
    }

    void "a JsonMapper failure is reported as a ConverterException"() {
        when:
        new JSON([value: new Failing()]).render(new StringWriter())

        then:
        thrown(ConverterException)
    }

    void "with grails.converters.json.date set to javascript, Date values render as JavaScript dates"() {
        given:
        initialize('grails.converters.json.date': 'javascript')

        expect:
        new JSON([date: new Date(0L), time: new Time(0L), month: Month.MAY]).toString() ==
                '{"date":new Date(0),"time":new Date(0),"month":5}'
    }

    void "the JSON builder writes through the JsonMapper"() {
        given:
        def json = new JSON()
        def out = new StringWriter()
        json.writer = new JacksonJSONWriter(ConvertersConfigurationHolder.getJsonMapper().createGenerator(out, false),
                ConvertersConfigurationHolder.getJsonMapper())

        when:
        json.build {
            keyed([(new Date(1759909726407L)): 'date', name: 'string'])
        }
        json.writer.generator.flush()

        then:
        out.toString() == '{"keyed":{"2025-10-08T07:48:46.407Z":"date","name":"string"}}'
    }

    void "with the #behaviour circular reference behaviour, a cycle renders as #description"() {
        given: 'a cycle below the root, after a number, with a null in between'
        initialize('grails.converters.json.circular.reference.behaviour': behaviour)
        def parent = new Node(name: 'parent')
        parent.children = [new Node(name: 'child', parent: parent)]

        expect:
        JSON.parse(new JSON([count: 1, nodes: [parent]]).toString()).nodes[0].children[0].parent == expected

        where:
        behaviour     | description                 || expected
        'DEFAULT'     | 'a relative reference'      || [_ref: '../..', class: Node.name]
        'PATH'        | 'a path from the root'      || [ref: 'root.nodes[0]', class: Node.name]
        'INSERT_NULL' | 'null'                      || null
    }

    void "with the PATH circular reference behaviour, a single value renders"() {
        given:
        initialize('grails.converters.json.circular.reference.behaviour': 'PATH')

        expect:
        new JSON('text').toString() == '"text"'
        new JSON(null).toString() == 'null'
    }

    void "with the EXCEPTION circular reference behaviour, a cycle fails to render"() {
        given:
        initialize('grails.converters.json.circular.reference.behaviour': 'EXCEPTION')
        def parent = new Node(name: 'parent')
        parent.children = [new Node(name: 'child', parent: parent)]

        when:
        new JSON(parent).render(new StringWriter())

        then:
        thrown(ConverterException)
    }

    private void initialize(Map<String, Object> config = [:], ApplicationContext applicationContext = null) {
        ConvertersConfigurationHolder.clear()
        def grailsApplication = new DefaultGrailsApplication()
        config.each { key, value -> grailsApplication.config.setAt(key, value) }
        grailsApplication.initialise()
        def mappingContext = new KeyValueMappingContext('json')
        grailsApplication.setApplicationContext(Stub(ApplicationContext) {
            getBean('grailsDomainClassMappingContext', MappingContext) >> mappingContext
        })
        grailsApplication.setMappingContext(mappingContext)
        new ConvertersConfigurationInitializer(grailsApplication: grailsApplication, applicationContext: applicationContext).initialize()
    }

    private static GenericApplicationContext applicationContext(Closure registrations) {
        def context = new GenericApplicationContext()
        context.registerBean(ProxyHandler, { new DefaultProxyHandler() })
        registrations.call(context)
        context.refresh()
        context
    }

    private static List<Object> otherValues() {
        [
                'say "hi"', 'a\\b', 'a/b', 'tab\tnew\nline\u0001', 'café 中', 'x\u0085y', 42,
                9007199254740993L, 1.0d, 2.50d, 1.0e20d, 1.5f, new BigDecimal('1.10'), new BigDecimal('1E+3'),
                new BigInteger('123456789012345678901234567890'), Double.NaN, true, 'c' as char,
                [1, 2, 3] as byte[], [1, 2] as int[], UUID.fromString('123e4567-e89b-12d3-a456-426614174000'),
                new URL('https://grails.apache.org/a?b=c'), new URI('https://grails.apache.org/a?b=c'), Locale.US,
                Locale.forLanguageTag('zh-Hant-TW'), Currency.getInstance('USD'), String, Role.DISPATCHER,
                Optional.of('x'), Optional.empty(), new StringBuilder('sb'),
                BigDecimal.ONE.divide(new BigDecimal(3), 5, RoundingMode.HALF_UP), ZoneId.of('Europe/Paris'),
                [1, 'a', null], [a: [b: [1, [c: 2]]]], [(1): 'one'], new Point(1, 2), new Html('plain')
        ]
    }
}

enum Role { HEAD, DISPATCHER, ADMIN }

enum Labelled {
    ONE

    @Override
    String toString() { 'one!' }
}

record Point(int x, int y) {}

record Dated(Date when) {}

class Html {
    final String value

    Html(String value) { this.value = value }

    @JsonValue
    String value() { value }
}

class Failing {
    @JsonValue
    String value() { throw new IllegalStateException('fails') }
}

class Identifier {
    final String value

    Identifier(String value) { this.value = value }

    @Override
    String toString() { "id-$value" }
}

class Identified {
}

class Holder {
    String name
    Date created
    Role role
    ChronoUnit unit
    BigDecimal amount
}

class Node {
    String name
    Node parent
    List<Node> children
}
