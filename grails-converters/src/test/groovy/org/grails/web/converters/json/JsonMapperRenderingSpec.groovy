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

import groovy.json.JsonSlurper

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIdentityInfo
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonUnwrapped
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.annotation.ObjectIdGenerators
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Timeout
import tools.jackson.core.JsonGenerator
import tools.jackson.core.StreamWriteConstraints
import tools.jackson.core.json.JsonFactory
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.cfg.ContextAttributes
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule
import tools.jackson.databind.ser.std.StdSerializer
import tools.jackson.databind.ser.std.ToStringSerializer

import org.springframework.context.ApplicationContext
import org.springframework.context.support.GenericApplicationContext
import org.springframework.validation.BeanPropertyBindingResult

import grails.converters.JSON
import grails.persistence.PersistenceMethod
import grails.web.controllers.ControllerMethod
import grails.core.DefaultGrailsApplication
import grails.core.support.proxy.DefaultProxyHandler
import grails.core.support.proxy.ProxyHandler
import org.grails.buffer.StreamCharBuffer
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.grails.web.converters.configuration.ConvertersConfigurationHolder
import org.grails.web.converters.configuration.ConvertersConfigurationInitializer
import org.grails.web.converters.configuration.ObjectMarshallerRegisterer
import org.grails.web.converters.exceptions.ConverterException
import org.grails.web.converters.jackson.GrailsJsonMapperCustomizer
import org.grails.web.converters.marshaller.ClosureObjectMarshaller
import org.grails.web.converters.marshaller.ObjectMarshaller
import org.grails.web.converters.marshaller.json.JsonMapperValueMarshaller
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

    void "a GString and a StreamCharBuffer are escaped as a String is, wherever they are written, with #description"() {
        given:
        initialize(config, mapper ? applicationContext {
            it.registerBean(JsonMapper, {
                def builder = JsonMapper.builder()
                new GrailsJsonMapperCustomizer().customize(builder)
                builder.build()
            })
        } : null)
        def buffer = new StreamCharBuffer()
        buffer.writer.write('</b>')

        expect: 'with the HTML end tag escaped as in Grails 8'
        new JSON("</a>${1}").toString() == '"<\\u002fa>1"'
        new JSON([gstring: "</a>${1}", buffer: buffer, list: ["</c>${2}"]]).toString() ==
                '{"gstring":"<\\u002fa>1","buffer":"<\\u002fb>","list":["<\\u002fc>2"]}'

        where:
        description                                                            | config                                  | mapper
        "the application's mapper, to which Grails adds a GString serializer" | [:]                                     | true
        'the default mapper'                                                   | [:]                                     | false
        'the legacy converter'                                                 | ['grails.converters.json.legacy': true] | false
    }

    void "a String, a GString and a StreamCharBuffer write a control character alike, with #description"() {
        given:
        initialize(config, mapper ? applicationContext {
            it.registerBean(JsonMapper, {
                def builder = JsonMapper.builder()
                new GrailsJsonMapperCustomizer().customize(builder)
                builder.build()
            })
        } : null)
        def buffer = new StreamCharBuffer()
        buffer.writer.write('a\u001Bb')

        expect:
        new JSON([string: 'a\u001Bb', gstring: "a\u001B${'b'}", buffer: buffer]).toString() ==
                "{\"string\":\"a${escaped}b\",\"gstring\":\"a${escaped}b\",\"buffer\":\"a${escaped}b\"}"

        where:
        description                                                            | config                                  | mapper || escaped
        "the application's mapper, to which Grails adds a GString serializer" | [:]                                     | true   || '\\u001B'
        'the default mapper'                                                   | [:]                                     | false  || '\\u001B'
        'the legacy converter, as Grails 8 wrote it'                          | ['grails.converters.json.legacy': true] | false  || '\\u001b'
    }

    void "with several mappers and none primary, the converter writes with Spring Boot's, as respond and binding do"() {
        given: "another mapper registered first, so that the first mapper found is not Boot's"
        initialize([:], applicationContext {
            it.registerBean('pluginMapper', JsonMapper, {
                JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE).build()
            })
            it.registerBean('jacksonJsonMapper', JsonMapper, {
                JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build()
            })
        })

        expect:
        new JSON(new NamedMapperBean(firstName: 'Ada')).toString() == '{"first_name":"Ada"}'
    }

    void "pretty printed JSON is indented by the JsonMapper's default pretty printer"() {
        given:
        def map = [a: 1, b: [1, 2], c: [d: 'e'], empty: []]

        expect:
        new JSON(map).toString(true) == jackson.writerWithDefaultPrettyPrinter().writeValueAsString(map)
    }

    void "a property's @JsonFormat shape applies, as the JsonMapper applies it"() {
        given:
        def bean = new ShapedValues(count: 5L, born: new Date(0))

        expect:
        new JSON(bean).toString() == '{"born":"1970-01-01","count":"5"}'
        new JSON(bean).toString() == jackson.writeValueAsString(bean)
    }

    void "a @JsonUnwrapped property is written into the enclosing object, as the JsonMapper writes it"() {
        given:
        def holder = new UnwrapHolder(address: new UnwrapAddress(street: 'Main'), note: 'n')

        expect:
        new JSON(holder).toString() == '{"street":"Main","note":"n"}'
        new JSON(holder).toString() == jackson.writeValueAsString(holder)
    }

    void "a @JsonUnwrapped property whose serializer does not unwrap renders as the converter renders it"() {
        given: 'a value Jackson writes under the name of the property, as its serializer cannot unwrap it'
        JSON.registerObjectMarshaller(Role) { Role role -> role.name().toLowerCase() }
        def holder = new UnwrapRoleHolder(role: Role.HEAD, note: 'n')

        expect:
        jackson.writeValueAsString(holder) == '{"note":"n","role":"HEAD"}'
        new JSON(holder).toString() == '{"note":"n","role":"head"}'
    }

    void "a value a marshaller writes in the place of a property's value is written as itself, without the property's annotations"() {
        given: 'marshallers that write a record and a bean in the place of the value they are registered for'
        JSON.registerObjectMarshaller(Login) { Login login -> new LoginView(login.user()) }
        JSON.registerObjectMarshaller(Profile) { Profile profile -> new ProfileView(user: profile.user) }

        expect: 'whatever the class of the value the marshaller writes'
        new JSON(new LoginHolder(login: new Login('ada', 's3cret'))).toString() == '{"login":{"user":"ada"}}'
        new JSON(new ProfileHolder(profile: new Profile(user: 'ada'))).toString() == '{"profile":{"user":"ada"}}'
    }

    void "the annotations of a property apply to the bean, collection or map it holds, as the JsonMapper applies them"() {
        given:
        def account = new Account(user: 'ada', secret: 's3cret')
        def holder = new AccountHolder(account: account, accounts: [account], accountsByName: [ada: account])

        expect:
        new JSON(holder).toString() ==
                '{"account":{"user":"ada"},"accounts":[{"user":"ada"}],"accountsByName":{"ada":{"user":"ada"}}}'
        new JSON(holder).toString() == jackson.writeValueAsString(holder)
    }

    void "an enum written with a type id renders by name, with the type id the JsonMapper writes"() {
        given:
        def holder = new TypedValueHolder(value: Status.ACTIVE)

        expect: 'the type id the mapper writes, with the name rather than the @JsonValue'
        jackson.writeValueAsString(holder) == '{"value":["org.grails.web.converters.json.Status","A"]}'
        new JSON(holder).toString() == '{"value":["org.grails.web.converters.json.Status","ACTIVE"]}'
    }

    void "the default attributes of the application's JsonMapper reach its serializers"() {
        given:
        def context = applicationContext {
            it.registerBean(JsonMapper, {
                JsonMapper.builder()
                        .defaultAttributes(ContextAttributes.getEmpty().withSharedAttribute('who', 'app'))
                        .addModule(new SimpleModule().addSerializer(Echo, new EchoSerializer()))
                        .build()
            })
        }
        initialize([:], context)

        expect:
        new JSON([echo: new Echo()]).toString() == '{"echo":"app"}'

        cleanup:
        context.close()
    }

    void "a value nested deeper than the thread's stack lets the converter write fails with a ConverterException, with legacy #legacy"() {
        given: 'a mapper without a nesting limit, which the converter writes with where legacy is false, so that only the stack limits the depth'
        def context = applicationContext {
            it.registerBean(JsonMapper, {
                JsonMapper.builder(JsonFactory.builder()
                        .streamWriteConstraints(StreamWriteConstraints.builder().maxNestingDepth(Integer.MAX_VALUE).build())
                        .build()).build()
            })
        }
        initialize(['grails.converters.json.legacy': legacy], context)

        when: 'deeper than any thread stack holds'
        new JSON(nested(100_000)).render(new StringWriter())

        then:
        ConverterException e = thrown()
        e.cause instanceof StackOverflowError

        cleanup:
        context.close()

        where:
        legacy << [false, true]
    }

    void "without an application JsonMapper, a value nested deeper than Jackson's default limit fails as the mapper fails to write it"() {
        given: 'no mapper in the application context, as in a unit test other than a web test'
        initialize()
        Throwable failure = null
        // a stack deep enough that only the limit stops the write
        def thread = new Thread(null, {
            try {
                new JSON(nested(600)).render(new StringWriter())
            }
            catch (Throwable e) {
                failure = e
            }
        } as Runnable, 'deep', 64L * 1024 * 1024)

        when:
        thread.start()
        thread.join()

        then: 'as the default mapper of Jackson fails, 500 objects and arrays deep'
        failure instanceof ConverterException
        Throwable cause = failure
        while (cause.cause) {
            cause = cause.cause
        }
        cause.message.startsWith('Document nesting depth (501) exceeds the maximum allowed (500')
    }

    void "a value nested deeper than the mapper's limit fails with a ConverterException, as the mapper fails to write it"() {
        given: 'a mapper limited to 10 levels, as spring.jackson.factory.constraints.write.max-nesting-depth sets it'
        def context = applicationContext {
            it.registerBean(JsonMapper, {
                JsonMapper.builder(JsonFactory.builder()
                        .streamWriteConstraints(StreamWriteConstraints.builder().maxNestingDepth(10).build())
                        .build()).build()
            })
        }
        initialize([:], context)

        expect: 'ten objects deep'
        new JSON(nested(9)).toString() == '{"child":' * 9 + '{}' + '}' * 9

        when: 'eleven'
        new JSON(nested(10)).render(new StringWriter())

        then:
        ConverterException e = thrown()
        Throwable cause = e
        while (cause.cause) {
            cause = cause.cause
        }
        cause.message.startsWith('Document nesting depth (11) exceeds the maximum allowed (10')

        cleanup:
        context.close()
    }

    void "the methods Grails adds to a bean as persistence or controller methods are not written"() {
        expect:
        new JSON(new MarkedMethods(title: 'Grails')).toString() == '{"title":"Grails"}'
    }

    void "with the IGNORE circular reference behaviour, a value reached again is left out of a list, and a property is null"() {
        given:
        initialize(['grails.converters.json.circular.reference.behaviour': 'IGNORE'])
        def first = new Node(name: 'first')
        first.children = [new Node(name: 'second', children: [first])]
        def lone = new Node(name: 'lone')
        lone.parent = lone
        def row = new RowNode(name: 'lone')
        row.parent = row
        def keyed = new KeyedNode(name: 'keyed')
        keyed.byName = [self: keyed]

        when:
        def list = new JsonSlurper().parseText(new JSON(first).toString())
        def property = new JsonSlurper().parseText(new JSON(lone).toString())

        then: 'as the mapper respond writes with does'
        list.children[0].children == []
        property.containsKey('parent')
        property.parent == null

        and: 'a property of a bean written as an array keeps its position'
        new JSON(row).toString() == '["lone",null]'

        and: 'a value of a map is null, as a property is'
        new JsonSlurper().parseText(new JSON(keyed).toString()).byName == [self: null]
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

    void "a #description is written with the serializer a module of the application gives its type, wherever it is written"() {
        given:
        def context = applicationContext {
            it.registerBean(JsonMapper, {
                JsonMapper.builder().addModule(new SimpleModule().addSerializer(Long, ToStringSerializer.instance)).build()
            })
        }
        initialize([:], context)
        JSON.registerObjectMarshaller(new ObjectMarshaller<JSON>() {
            boolean supports(Object object) { object instanceof PropertyIdHolder }

            void marshalObject(Object object, JSON json) {
                json.writer.object()
                json.property('id', ((PropertyIdHolder) object).id)
                json.writer.endObject()
            }
        })
        JSON.registerObjectMarshaller(BuiltIdHolder) { BuiltIdHolder holder, JSON json -> json.build { id holder.id } }

        expect:
        new JSON(value).toString() == written

        cleanup:
        context.close()

        where:
        description                                | value                        || written
        'number on its own'                        | 5L                           || '"5"'
        'number in a map'                          | [id: 5L]                     || '{"id":"5"}'
        'number a marshaller writes as a property' | new PropertyIdHolder(id: 5L) || '{"id":"5"}'
        'number a marshaller builds'               | new BuiltIdHolder(id: 5L)    || '{"id":"5"}'
        'number of a type the module leaves alone' | 5                            || '5'
    }

    void "with legacy, a number is written as Grails 8 wrote it, whatever serializer a module of the application gives its type"() {
        given:
        def context = applicationContext {
            it.registerBean(JsonMapper, {
                JsonMapper.builder().addModule(new SimpleModule().addSerializer(Long, ToStringSerializer.instance)).build()
            })
        }
        initialize(['grails.converters.json.legacy': true], context)

        expect:
        new JSON(5L).toString() == '5'
        new JSON([id: 5L]).toString() == '{"id":5}'

        cleanup:
        context.close()
    }

    void "a #description is wrapped in an object named after its class where the mapper wraps root values, as the mapper wraps it"() {
        given:
        def mapper = JsonMapper.builder().enable(SerializationFeature.WRAP_ROOT_VALUE).build()
        def context = applicationContext { it.registerBean(JsonMapper, { mapper }) }
        initialize([:], context)

        expect:
        new JSON(value).toString() == mapper.writeValueAsString(value)
        new JSON(value).toString().startsWith("{\"${rootName}\":")

        cleanup:
        context.close()

        where:
        description | value                                  || rootName
        'bean'      | new NamedMapperBean(firstName: 'Ada')  || 'NamedMapperBean'
        'map'       | [a: 1]                                 || 'LinkedHashMap'
        'list'      | [1, 2]                                 || 'ArrayList'
        'string'    | 'x'                                    || 'String'
    }

    void "with the PATH circular reference behaviour, a reference in a wrapped root value has the path of the value it repeats"() {
        given:
        def context = applicationContext {
            it.registerBean(JsonMapper, { JsonMapper.builder().enable(SerializationFeature.WRAP_ROOT_VALUE).build() })
        }
        initialize(['grails.converters.json.circular.reference.behaviour': 'PATH'], context)
        def parent = new Node(name: 'parent')
        def child = new Node(name: 'child', parent: parent)
        child.children = [child]
        parent.children = [child]

        when:
        def json = JSON.parse(new JSON(parent).toString()).Node

        then: 'below the object the root value is wrapped in'
        json.children[0].parent == [ref: 'root.Node', class: Node.name]
        json.children[0].children[0] == [ref: 'root.Node.children[0]', class: Node.name]

        cleanup:
        context.close()
    }

    void "a root value whose proxy handler gives null is written as null, whether or not the mapper wraps root values"() {
        given:
        def context = applicationContext {
            it.registerBean(JsonMapper, { JsonMapper.builder().enable(SerializationFeature.WRAP_ROOT_VALUE).build() })
            it.registerBean(ProxyHandler, { [unwrapIfProxy: { Object o -> null }, isProxy: { Object o -> true },
                                              isInitialized: { Object o -> false }] as ProxyHandler })
        }
        initialize([:], context)

        expect:
        new JSON(new Node(name: 'unloaded')).toString() == 'null'

        cleanup:
        context.close()
    }

    void "with legacy, the value is not wrapped where the application's mapper wraps root values, as in Grails 8"() {
        given:
        def context = applicationContext {
            it.registerBean(JsonMapper, { JsonMapper.builder().enable(SerializationFeature.WRAP_ROOT_VALUE).build() })
        }
        initialize(['grails.converters.json.legacy': true], context)

        expect:
        new JSON([a: 1]).toString() == '{"a":1}'

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

    void "a bean with @JsonIdentityInfo reached again renders as the mapper's object id, not as a circular reference"() {
        given:
        def first = new IdentifiedNode(name: 'first')
        first.next = new IdentifiedNode(name: 'second', next: first)

        expect:
        new JSON(first).toString() == '{"@id":1,"name":"first","next":{"@id":2,"name":"second","next":1}}'
    }

    @Timeout(10)
    void "a bean with @JsonIdentityInfo that a registered marshaller writes is a circular reference when reached again"() {
        given:
        JSON.registerObjectMarshaller(IdentifiedNode) { IdentifiedNode node -> [name: node.name, next: node.next] }
        def first = new IdentifiedNode(name: 'first')
        first.next = new IdentifiedNode(name: 'second', next: first)

        when:
        def json = JSON.parse(new JSON(first).toString())

        then: 'the mapper never writes the bean, so it has no object id to write'
        json.name == 'first'
        json.next.name == 'second'
        json.next.next._ref
        json.next.next['class'] == IdentifiedNode.name
    }

    @Timeout(10)
    void "a bean with @JsonIdentityInfo that a subclass of the mapper's marshaller writes itself is a circular reference when reached again"() {
        given:
        JSON.registerObjectMarshaller(new JsonMapperValueMarshaller() {
            @Override
            boolean supports(Object object) {
                object instanceof IdentifiedNode
            }

            @Override
            void marshalObject(Object object, JSON converter) {
                IdentifiedNode node = (IdentifiedNode) object
                converter.convertAnother([name: node.name, next: node.next])
            }
        })
        def first = new IdentifiedNode(name: 'first')
        first.next = new IdentifiedNode(name: 'second', next: first)

        when:
        def json = JSON.parse(new JSON(first).toString())

        then:
        json.name == 'first'
        json.next.name == 'second'
        json.next.next._ref
        json.next.next['class'] == IdentifiedNode.name
    }

    void "a subclass of the mapper's marshaller that writes a single value itself writes it wherever it is nested"() {
        given:
        def id = UUID.fromString('0f8fad5b-d9cb-469f-a165-70867728950e')
        JSON.registerObjectMarshaller(new JsonMapperValueMarshaller() {
            @Override
            boolean supports(Object object) {
                object instanceof UUID
            }

            @Override
            void marshalObject(Object object, JSON converter) {
                converter.convertAnother(object.toString().toUpperCase())
            }
        })

        expect:
        new JSON(id).toString() == '"0F8FAD5B-D9CB-469F-A165-70867728950E"'
        new JSON([id: id, ids: [id]]).toString() ==
                '{"id":"0F8FAD5B-D9CB-469F-A165-70867728950E","ids":["0F8FAD5B-D9CB-469F-A165-70867728950E"]}'
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

    void "validation errors render without the values that were rejected, as the mapper writes them"() {
        given:
        def errors = new BeanPropertyBindingResult(new Holder(name: 'secret'), 'holder')
        errors.rejectValue('name', 'invalid', 'Name is invalid')

        when:
        def written = new JsonSlurper().parseText(new JSON(errors).toString())

        then:
        written.errors.size() == 1
        written.errors[0].keySet() as List == ['object', 'field', 'codes', 'message']
        written.errors[0].object == 'holder'
        written.errors[0].field == 'name'
        written.errors[0].message == 'Name is invalid'
        !new JSON(errors).toString().contains('secret')
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

    void "an application serializer takes precedence over the converter's own, as it does in Grails responses"() {
        given:
        def context = applicationContext {
            it.registerBean(JsonMapper, {
                JsonMapper.builder().addModule(new SimpleModule().addSerializer(BeanPropertyBindingResult, new MapperErrorsSerializer())).build()
            })
        }
        initialize([:], context)
        def errors = new BeanPropertyBindingResult(new Object(), 'test')
        errors.reject('failed', 'Error happening on test object.')

        expect:
        new JSON(errors).toString() == '"written by the mapper"'
        new GrailsJsonMapperCustomizer().forGrails(context.getBean(JsonMapper)).writeValueAsString(errors) == '"written by the mapper"'

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

    void "a record renders as Jackson renders it, with the annotations on its components"() {
        expect:
        new JSON([person: new Person('Ada', 'x')]).toString() == '{"person":{"first_name":"Ada"}}'
    }

    void "a map with a null key fails, as it does for Spring Boot, where the Grails 8 converter left the entry out"() {
        when:
        new JSON([(null): 1, a: 2]).toString()

        then:
        def e = thrown(RuntimeException)
        e.message.contains('Null key for a Map not allowed in JSON')
    }

    void "a collection that contains itself renders as an empty collection where it repeats, as before"() {
        given:
        List list = []
        list << list
        Map map = [:]
        map.self = map

        expect:
        new JSON(list).toString() == '[[]]'
        new JSON(map).toString() == '{"self":{}}'
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

    void "a bean renders as Jackson renders it, with the annotations on its properties"() {
        expect:
        new JSON(new AnnotatedBean(firstName: 'Ada', secret: 's')).toString() == '{"first_name":"Ada"}'
    }

    void "the includes and excludes of the converter decide which properties of a bean are written"() {
        given:
        def json = new JSON(new Holder(name: 'Fred', role: Role.ADMIN, unit: ChronoUnit.DAYS))
        json.setExcludes(Holder, ['created', 'amount', 'unit'])

        expect:
        json.toString() == '{"name":"Fred","role":"ADMIN"}'
    }

    void "the includes and excludes of the converter name a property by its name or by the name the JsonMapper writes, here #names"() {
        given:
        def json = new JSON(new RenamedBean(name: 'Ada', count: 2))
        if (includes != null) {
            json.setIncludes(RenamedBean, includes)
        }
        if (excludes != null) {
            json.setExcludes(RenamedBean, excludes)
        }

        expect:
        json.toString() == written

        where:
        includes          | excludes  || written
        ['name']          | null      || '{"label":"Ada"}'
        ['label']         | null      || '{"label":"Ada"}'
        ['label']         | ['name']  || '{}'
        null              | ['name']  || '{"count":2}'
        null              | ['label'] || '{"count":2}'

        names = "includes ${includes} and excludes ${excludes}"
    }

    void "empty includes of the converter write none of the properties of a bean, as in Grails 8"() {
        given:
        def json = new JSON(new Holder(name: 'Fred', role: Role.ADMIN))
        json.setIncludes(Holder, [])

        expect:
        json.toString() == '{}'
    }

    void "the includes and excludes of the converter apply to the entries of a @JsonAnyGetter, as to the property it is"() {
        expect:
        written(['secrets'], null) == '{"name":"a"}'
        written(null, ['name']) == '{"name":"a"}'
        written(null, []) == '{}'
        written(null, ['secrets']) == '{"token":"t"}'
    }

    private static String written(List<String> excludes, List<String> includes) {
        def json = new JSON(new AnyGetterHolder(name: 'a', secrets: [token: 't']))
        if (excludes != null) {
            json.setExcludes(AnyGetterHolder, excludes)
        }
        if (includes != null) {
            json.setIncludes(AnyGetterHolder, includes)
        }
        json.toString()
    }

    void "a value the mapper writes with a type id is written with it, rather than by a marshaller that cannot write one"() {
        given: 'a value of a type with a type id, which writes a string with its @JsonValue'
        def typed = new TypedPayload(shape: new Circle(name: 'round'))

        expect: 'as the mapper writes it'
        JsonMapper.builder().build().writeValueAsString(typed) == '{"shape":["Circle","round"]}'
        new JSON(typed).toString() == '{"shape":["Circle","round"]}'
    }

    void "a property's @JsonTypeInfo writes the type ids of the elements of the #description it holds, as the JsonMapper writes them"() {
        expect:
        jackson.writeValueAsString(holder) == written
        new JSON(holder).toString() == written

        where:
        description  | holder                                                || written
        'list'       | new TypedContents(list: [new Tile(side: 2)])           || '{"list":[{"kind":"tile","side":2}]}'
        'array'      | new TypedContents(array: [new Tile(side: 2)] as Object[]) || '{"array":[{"kind":"tile","side":2}]}'
        'map'        | new TypedContents(map: [a: new Tile(side: 2)])         || '{"map":{"a":{"kind":"tile","side":2}}}'
    }

    void "beans and map entries are rendered by the mapper"() {
        given:
        def bean = new Holder(name: 'Fred', created: new Date(1790305200000L), role: Role.ADMIN, unit: ChronoUnit.DAYS,
                amount: new BigDecimal('10.50'))

        expect:
        new JSON(bean).toString() ==
                '{"amount":10.50,"created":"2026-09-25T03:00:00.000Z","name":"Fred","role":"ADMIN","unit":"DAYS"}'
        new JSON([entry: new AbstractMap.SimpleEntry('k', 1)]).toString() == '{"entry":{"k":1}}'
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

    void "grails.converters.json.date set to javascript fails, since the JsonMapper cannot parse the JavaScript dates back"() {
        when:
        initialize('grails.converters.json.date': 'javascript')

        then:
        IllegalStateException e = thrown()
        e.message.startsWith('grails.converters.json.date: javascript writes dates as JavaScript new Date(...) expressions')
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

    void "with the DEFAULT circular reference behaviour, a bean that references itself renders as a reference to the object that holds the reference"() {
        given:
        initialize('grails.converters.json.circular.reference.behaviour': 'DEFAULT')
        def only = new Node(name: 'only')
        only.parent = only

        expect:
        new JSON(only).toString() == '{"children":null,"name":"only","parent":{"_ref":".","class":"' + Node.name + '"}}'
    }

    void "with the #behaviour circular reference behaviour, a reference has its keys in the converter's order, whatever order the JsonMapper writes map entries in"() {
        given: 'a mapper that sorts the entries of a map by key'
        def context = new GenericApplicationContext()
        context.registerBean(JsonMapper, { JsonMapper.builder().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build() })
        context.registerBean(ProxyHandler, { new DefaultProxyHandler() })
        context.refresh()
        initialize(['grails.converters.json.circular.reference.behaviour': behaviour], context)
        def only = new Node(name: 'only')
        only.parent = only

        expect:
        new JSON(only).toString() == '{"children":null,"name":"only","parent":' + reference + '}'

        cleanup:
        context.close()

        where:
        behaviour | reference
        'PATH'    | '{"ref":"root","class":"' + Node.name + '"}'
        'DEFAULT' | '{"_ref":".","class":"' + Node.name + '"}'
    }

    void "with the DEFAULT circular reference behaviour, a reference steps up the JSON objects and arrays to the value the object is written as, for #description, as respond does"() {
        given:
        initialize('grails.converters.json.circular.reference.behaviour': 'DEFAULT')

        expect:
        new JSON(value).toString() == written

        where:
        description                                       | value              || written
        'a bean that references itself through an Optional' | optionalCycle()  ||
                '{"name":"a","next":{"_ref":".","class":"' + OptionalLink.name + '"}}'
        'a bean with a wrapper type id that references itself' | wrappedCycle(false) ||
                '{"WrappedLink":{"name":"a","next":{"_ref":"..","class":"' + WrappedLink.name + '"}}}'
        'two beans with wrapper type ids'                 | wrappedCycle(true) ||
                '{"WrappedLink":{"name":"a","next":{"WrappedLink":{"name":"b","next":{"_ref":"../../..","class":"' + WrappedLink.name + '"}}}}}'
        'a bean with an array wrapper type id in a list'  | arrayWrappedCycle() ||
                '{"items":[["WrappedItem",{"name":"i","owner":{"_ref":"../../..","class":"' + WrappedItemOwner.name + '"}}]],"name":"o"}'
    }

    private static OptionalLink optionalCycle() {
        def link = new OptionalLink(name: 'a')
        link.next = Optional.of(link)
        link
    }

    private static WrappedLink wrappedCycle(boolean pair) {
        def link = new WrappedLink(name: 'a')
        link.next = pair ? new WrappedLink(name: 'b', next: link) : link
        link
    }

    private static WrappedItemOwner arrayWrappedCycle() {
        def owner = new WrappedItemOwner(name: 'o')
        owner.items = [new WrappedItem(name: 'i', owner: owner)]
        owner
    }

    void "an object equal to one being written is written in full, as the JsonMapper writes it, not as a reference, with the #behaviour behaviour"() {
        given:
        initialize('grails.converters.json.circular.reference.behaviour': behaviour)
        def outer = new EqualLink(name: 'a', next: new EqualLink(name: 'a'))

        expect:
        new JSON(outer).toString() == '{"name":"a","next":{"name":"a","next":null}}'

        where:
        behaviour << ['DEFAULT', 'PATH', 'INSERT_NULL', 'IGNORE', 'EXCEPTION']
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

    /**
     * Initializes the converters to render JSON through the JsonMapper ({@code grails.converters.json.legacy: false}),
     * unless the configuration says otherwise.
     */
    private void initialize(Map<String, Object> config = [:], ApplicationContext applicationContext = null) {
        ConvertersConfigurationHolder.clear()
        def grailsApplication = new DefaultGrailsApplication()
        (['grails.converters.json.legacy': false] + config).each { key, value -> grailsApplication.config.setAt(key, value) }
        grailsApplication.initialise()
        def mappingContext = new KeyValueMappingContext('json')
        grailsApplication.setApplicationContext(Stub(ApplicationContext) {
            getBean('grailsDomainClassMappingContext', MappingContext) >> mappingContext
        })
        grailsApplication.setMappingContext(mappingContext)
        new ConvertersConfigurationInitializer(grailsApplication: grailsApplication, applicationContext: applicationContext).initialize()
    }

    private static Map nested(int levels) {
        Map root = [:]
        Map current = root
        levels.times {
            Map child = [:]
            current.child = child
            current = child
        }
        root
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
                'say "hi"', 'a\\b', 'a/b', 'tab\tnew\nline\u0001', 'esc\u001Bso\u000Eus\u001Fvt\u000B', 'café 中', 'x\u0085y', 42,
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

class TypedValueHolder {
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
    Object value
}

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

class ShapedValues {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Long count
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = 'yyyy-MM-dd', timezone = 'UTC')
    Date born
}

class UnwrapAddress {
    String street
}

record Login(String user, String password) {}

record LoginView(String user) {}

class LoginHolder {
    @JsonIgnoreProperties('user')
    Login login
}

class Profile {
    String user
}

class ProfileView {
    String user
}

class ProfileHolder {
    @JsonIgnoreProperties('user')
    Profile profile
}

class UnwrapHolder {
    @JsonUnwrapped
    UnwrapAddress address
    String note
}

class UnwrapRoleHolder {
    @JsonUnwrapped
    Role role
    String note
}

class Account {
    String user
    String secret
}

@JsonPropertyOrder(['account', 'accounts', 'accountsByName'])
class AccountHolder {
    @JsonIgnoreProperties(['secret'])
    Account account
    @JsonIgnoreProperties(['secret'])
    List<Account> accounts
    @JsonIgnoreProperties(['secret'])
    Map<String, Account> accountsByName
}

class Echo { }

/** Writes the attribute a write carries. */
class EchoSerializer extends StdSerializer<Echo> {

    EchoSerializer() {
        super(Echo)
    }

    @Override
    void serialize(Echo value, JsonGenerator generator, SerializationContext context) {
        generator.writeString(String.valueOf(context.getAttribute('who')))
    }
}

class MarkedMethods {
    String title

    @PersistenceMethod
    List<String> getDirtyPropertyNames() {
        ['title']
    }

    @ControllerMethod
    String getActionName() {
        'show'
    }
}

class AnnotatedBean {
    @JsonProperty('first_name')
    String firstName
    @JsonIgnore
    String secret
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String nickname
}

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

class KeyedNode {
    String name
    Map<String, KeyedNode> byName
}

@JsonFormat(shape = JsonFormat.Shape.ARRAY)
@JsonPropertyOrder(['name', 'parent'])
class RowNode {
    String name
    RowNode parent
}

@JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator)
class IdentifiedNode {
    String name
    IdentifiedNode next
}

class Secret {
    String value
}

class Wrapper {
    Object inner
    List<Object> others = []
}

class PropertyIdHolder {
    Long id
}

class BuiltIdHolder {
    Long id
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

class AnyGetterHolder {
    String name
    @JsonAnyGetter
    Map<String, String> secrets
}

class TypedPayload {
    TypedShape shape
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
class TypedShape {
}

class Circle extends TypedShape {
    String name

    @JsonValue
    String jsonValue() { name }
}

@JsonInclude(JsonInclude.Include.NON_NULL)
class TypedContents {
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = 'kind')
    @JsonSubTypes([@JsonSubTypes.Type(value = Tile, name = 'tile')])
    List<Object> list

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = 'kind')
    @JsonSubTypes([@JsonSubTypes.Type(value = Tile, name = 'tile')])
    Object[] array

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = 'kind')
    @JsonSubTypes([@JsonSubTypes.Type(value = Tile, name = 'tile')])
    Map<String, Object> map
}

class Tile {
    Integer side
}

class RenamedBean {
    @JsonProperty('label')
    String name
    Integer count
}

class OptionalLink {
    String name
    Optional<OptionalLink> next
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_OBJECT)
class WrappedLink {
    String name
    WrappedLink next
}

class WrappedItemOwner {
    String name
    List<WrappedItem> items
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_ARRAY)
class WrappedItem {
    String name
    WrappedItemOwner owner
}

class EqualLink {
    String name
    EqualLink next

    @Override
    boolean equals(Object other) {
        other instanceof EqualLink && other.name == name
    }

    @Override
    int hashCode() {
        name.hashCode()
    }
}

class NamedMapperBean {
    String firstName
}
