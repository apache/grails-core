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

import java.math.RoundingMode
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import java.sql.Time
import java.time.Month
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Timeout
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule
import tools.jackson.databind.ser.std.StdSerializer

import org.springframework.context.ApplicationContext
import org.springframework.context.support.GenericApplicationContext
import org.springframework.validation.BeanPropertyBindingResult

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
import org.grails.web.converters.marshaller.json.ValidationErrorsMarshaller
import org.grails.web.json.DateTimeValues
import org.grails.web.json.JSONWriter

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

    void "an enum constant with a body renders by name, as every enum"() {
        expect:
        new JSON([status: BodyStatus.ACTIVE, shape: Shape.CIRCLE]).toString() == '{"status":"ACTIVE","shape":"CIRCLE"}'
    }

    void "a Number a module serializer writes renders as the mapper renders it, also inside a value the mapper writes"() {
        given:
        def context = applicationContext {
            it.registerBean(JsonMapper, {
                JsonMapper.builder().addModule(new SimpleModule()
                        .addSerializer(Money, new MoneySerializer())
                        .addSerializer(Wrapper, new WrapperSerializer())).build()
            })
        }
        initialize([:], context)

        expect:
        new JSON([money: new Money(5), wrapper: new Wrapper(inner: new Money(6))]).toString() ==
                '{"money":"$5","wrapper":{"inner":"$6","others":[]}}'
        new JSON(new Money(7)).toString() == '"$7"'

        cleanup:
        context.close()
    }

    void "an enum with a @JsonValue renders by name, as every enum, so that it binds back"() {
        expect:
        new JSON([status: Status.ACTIVE]).toString() == '{"status":"ACTIVE"}'
        new JSON(Status.ACTIVE).toString() == '"ACTIVE"'
    }

    void "a JsonMapper that indents its output does not indent JSON that is not pretty printed"() {
        given: 'a mapper configured as spring.jackson.serialization.indent-output would configure it'
        def mapper = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build()
        def context = applicationContext { it.registerBean(JsonMapper, { mapper }) }
        initialize([:], context)
        def map = [a: 1, b: [1, 2]]

        expect:
        new JSON(map).toString() == '{"a":1,"b":[1,2]}'
        new JSON(map).toString(false) == '{"a":1,"b":[1,2]}'
        new JSON(map).toString(true) == mapper.writerWithDefaultPrettyPrinter().writeValueAsString(map)

        cleanup:
        context.close()
    }

    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void "a Number the mapper has no dedicated writer for is written once, inside a value a module serializer writes"() {
        given: 'a marshaller that writes an AtomicInteger to the writer itself, and a module serializer that writes one'
        def context = moduleContext()
        initialize([:], context)
        JSON.registerObjectMarshaller(new ObjectMarshaller<JSON>() {
            boolean supports(Object object) { object instanceof AtomicInteger }

            void marshalObject(Object object, JSON json) { json.writer.value((Number) object) }
        })

        expect:
        new JSON([wrapper: new Wrapper(inner: new AtomicInteger(5)), number: new AtomicInteger(6)]).toString() ==
                '{"wrapper":{"inner":5,"others":[]},"number":6}'

        cleanup:
        context.close()
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

        expect: 'in values and in map keys'
        new JSON([date: new Date(1759909726407L), keyed: [(new Date(1759909726407L)): 'date']]).toString() ==
                '{"date":"2025-10-08T03:48:46.407-04:00","keyed":{"2025-10-08T03:48:46.407-04:00":"date"}}'

        cleanup:
        context.close()
    }

    void "a type a Jackson module serializes renders with its serializer, and the values it writes as the converter renders them"() {
        given: 'a JsonMapper with a module serializer for Wrapper, and a marshaller registered for Secret'
        def context = moduleContext()
        initialize([:], context)
        JSON.registerObjectMarshaller(Secret) { Secret secret -> '***' }
        def wrapper = new Wrapper(inner: new Secret(value: 'hidden'))

        expect: 'the marshaller renders the Secret the module serializer writes'
        new JSON([wrapper: wrapper, secret: new Secret(value: 'hidden')]).toString() ==
                '{"wrapper":{"inner":"***","others":[]},"secret":"***"}'

        and: 'the application JsonMapper itself is not changed'
        context.getBean(JsonMapper).writeValueAsString(wrapper) == '{"inner":{"value":"hidden"},"others":[]}'

        when: 'a marshaller is registered for Wrapper as well'
        JSON.registerObjectMarshaller(Wrapper) { Wrapper it -> [secret: it.inner] }

        then: 'it takes precedence over the module serializer'
        new JSON([wrapper: wrapper]).toString() == '{"wrapper":{"secret":"***"}}'

        cleanup:
        context.close()
    }

    void "every value a module serializer writes renders as the converter renders it, and values of the mapper's types as the mapper does"() {
        given:
        def context = moduleContext()
        initialize([:], context)
        JSON.registerObjectMarshaller(Secret) { Secret secret -> '***' }
        def wrapper = new Wrapper(inner: Labelled.ONE,
                others: [new Secret(value: 'hidden'), [role: Role.HEAD, labelled: Labelled.ONE], new Date(0L), null, new Point(1, 2)])

        expect: 'enums by name, a map by the map marshaller, a date by the mapper'
        new JSON([wrapper: wrapper]).toString() ==
                '{"wrapper":{"inner":"ONE","others":["***",{"role":"HEAD","labelled":"ONE"},"1970-01-01T00:00:00.000Z",null,{"x":1,"y":2}]}}'

        cleanup:
        context.close()
    }

    void "with the #behaviour circular reference behaviour, a cycle through a module serializer renders as #description"() {
        given: 'a cycle nested in a value that a module serializer writes'
        def context = moduleContext()
        initialize(['grails.converters.json.circular.reference.behaviour': behaviour], context)
        def parent = new Node(name: 'parent')
        parent.children = [new Node(name: 'child', parent: parent)]

        expect:
        JSON.parse(new JSON([count: 1, wrapper: new Wrapper(others: [parent])]).toString()).wrapper.others[0].children[0].parent == expected

        cleanup:
        context.close()

        where:
        behaviour     | description            || expected
        'DEFAULT'     | 'a relative reference' || [_ref: '../..', class: Node.name]
        'PATH'        | 'a path from the root' || [ref: 'root.wrapper.others[0]', class: Node.name]
        'INSERT_NULL' | 'null'                 || null
    }

    void "with the PATH circular reference behaviour, a reference through a module serializer has the path of the value it writes"() {
        given:
        def context = moduleContext()
        initialize(['grails.converters.json.circular.reference.behaviour': 'PATH'], context)
        def parent = new Node(name: 'parent')
        parent.children = [new Node(name: 'child', parent: parent)]

        when:
        def json = JSON.parse(new JSON([wrapper: new Wrapper(inner: parent, others: ['first', parent])]).toString()).wrapper

        then: 'a reference below a named value, and below a value after another in an array'
        json.inner.children[0].parent == [ref: 'root.wrapper.inner', class: Node.name]
        json.others[1].children[0].parent == [ref: 'root.wrapper.others[1]', class: Node.name]

        cleanup:
        context.close()
    }

    void "a failure to render a value a module serializer writes is reported as the converter reports it"() {
        given:
        def context = moduleContext()
        initialize(['grails.converters.json.circular.reference.behaviour': 'EXCEPTION'], context)
        def parent = new Node(name: 'parent')
        parent.children = [new Node(name: 'child', parent: parent)]

        when:
        new JSON(new Wrapper(inner: parent)).render(new StringWriter())

        then:
        def e = thrown(ConverterException)
        e.message == "Circular Reference detected: class ${Node.name}"

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

    void "a registered marshaller keeps the Grails 8 rendering of a type, as the upgrade guide shows"() {
        given:
        JSON.registerObjectMarshaller(Month) { Month month -> month.name() }
        JSON.registerObjectMarshaller(Locale) { Locale locale -> locale.toString() }
        JSON.registerObjectMarshaller(byte[]) { byte[] bytes -> bytes as List }

        expect:
        new JSON([month: Month.MAY, locale: Locale.forLanguageTag('zh-Hant-TW'), bytes: [1, 2, 3] as byte[]]).toString() ==
                '{"month":"MAY","locale":"zh_TW_#Hant","bytes":[1,2,3]}'
    }

    void "a marshaller registered with an explicit negative priority comes after the default marshallers"() {
        given: 'default marshallers have the priorities -1, -2, and so on'
        def context = applicationContext {
            it.registerBean(ObjectMarshallerRegisterer, {
                new ObjectMarshallerRegisterer(converterClass: JSON, priority: -100,
                        marshaller: new ClosureObjectMarshaller<JSON>(Date, { Date date -> date.time }))
            })
        }
        initialize([:], context)

        expect: 'the mapper renders the date'
        new JSON([date: new Date(1759909726407L)]).toString() == '{"date":"2025-10-08T07:48:46.407Z"}'

        cleanup:
        context.close()
    }

    void "the validation errors marshaller takes precedence over a module serializer for Errors"() {
        given: 'the errors marshaller registered as the converters plugin registers it'
        def context = applicationContext {
            it.registerBean(JsonMapper, {
                JsonMapper.builder().addModule(new SimpleModule().addSerializer(BeanPropertyBindingResult, new MapperErrorsSerializer())).build()
            })
            it.registerBean(ObjectMarshallerRegisterer, {
                new ObjectMarshallerRegisterer(converterClass: JSON, marshaller: new ValidationErrorsMarshaller())
            })
        }
        initialize([:], context)
        def errors = new BeanPropertyBindingResult(new Object(), 'test')
        errors.reject('failed', 'Error happening on test object.')

        expect:
        new JSON(errors).toString() == '{"errors":[{"object":"test","message":"Error happening on test object."}]}'

        cleanup:
        context.close()
    }

    void "a Throwable is rendered by the bean marshallers, as in Grails 8"() {
        given:
        def error = new IllegalStateException('boom')
        def rendered = new JSON([error: error]).toString()
        initialize(['grails.converters.json.legacy': true])

        expect:
        rendered.contains('"message":"boom"')
        rendered == new JSON([error: error]).toString()
    }

    void "a record renders as an object of its components, without the Jackson annotations on them"() {
        expect:
        new JSON([person: new Person('Ada', 'x')]).toString() == '{"person":{"firstName":"Ada","ssn":"x"}}'
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
        json.writer = new JSONWriter(out)

        when:
        json.build {
            keyed([(new Date(1759909726407L)): 'date', name: 'string'])
        }

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

    private static GenericApplicationContext moduleContext() {
        applicationContext {
            it.registerBean(JsonMapper, {
                JsonMapper.builder().addModule(new SimpleModule().addSerializer(Wrapper, new WrapperSerializer())).build()
            })
        }
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
                [1, 'a', null], [a: [b: [1, [c: 2]]]], [(1): 'one'], new Point(1, 2), new Html('plain'),
                ['a', 'b'] as char[], new File('/tmp/a.txt'), Paths.get('/tmp/a.txt'), Pattern.compile('a+b'),
                StandardCharsets.UTF_8, InetAddress.getByAddress('h', [127, 0, 0, 1] as byte[]),
                ByteBuffer.wrap([1, 2, 3] as byte[]), [(String): 'class key'],
                [(OffsetDateTime.parse('2025-10-08T04:48:46.407-03:00')): 'keeps its offset'],
                JsonMapper.builder().build().readTree('{"a":1,"b":[true,null]}')
        ]
    }
}

enum Role { HEAD, DISPATCHER, ADMIN }

enum BodyStatus {
    ACTIVE('A') {
        @Override
        String describe() { 'active' }
    }

    final String code

    BodyStatus(String code) { this.code = code }

    @JsonValue
    String getCode() { code }

    String describe() { '' }
}

enum Shape {
    CIRCLE {
        @Override
        int corners() { 0 }
    }

    abstract int corners()
}

class Money extends Number {

    final long amount

    Money(long amount) { this.amount = amount }

    int intValue() { (int) amount }

    long longValue() { amount }

    float floatValue() { amount }

    double doubleValue() { amount }

    @Override
    String toString() { "Money($amount)" }
}

class MoneySerializer extends StdSerializer<Money> {

    MoneySerializer() {
        super(Money)
    }

    @Override
    void serialize(Money money, JsonGenerator generator, SerializationContext context) {
        generator.writeString('$' + money.amount)
    }
}

enum Status {
    ACTIVE('A')

    final String code

    Status(String code) { this.code = code }

    @JsonValue
    String getCode() { code }
}

enum Labelled {
    ONE

    @Override
    String toString() { 'one!' }
}

record Point(int x, int y) {}

record Dated(Date when) {}

record Person(@JsonProperty('first_name') String firstName, @JsonIgnore String ssn) {}

class MapperErrorsSerializer extends StdSerializer<BeanPropertyBindingResult> {

    MapperErrorsSerializer() {
        super(BeanPropertyBindingResult)
    }

    @Override
    void serialize(BeanPropertyBindingResult errors, JsonGenerator generator, SerializationContext context) {
        generator.writeString('written by the mapper')
    }
}

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

class Secret {
    String value
}

class Wrapper {
    Object inner
    List<Object> others = []
}

class WrapperSerializer extends StdSerializer<Wrapper> {

    WrapperSerializer() {
        super(Wrapper)
    }

    @Override
    void serialize(Wrapper wrapper, JsonGenerator generator, SerializationContext context) {
        generator.writeStartObject()
        generator.writeName('inner')
        generator.writePOJO(wrapper.inner)
        generator.writeName('others')
        generator.writeStartArray()
        wrapper.others.each { generator.writePOJO(it) }
        generator.writeEndArray()
        generator.writeEndObject()
    }
}
