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
package grails.converters.json

import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Function

import groovy.json.JsonSlurper

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonUnwrapped
import tools.jackson.core.JacksonException
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.DatabindException
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ValueSerializer
import tools.jackson.databind.cfg.ContextAttributes
import tools.jackson.databind.exc.InvalidDefinitionException
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule
import tools.jackson.databind.node.JsonNodeFactory
import tools.jackson.databind.node.ObjectNode

import grails.core.DefaultGrailsApplication
import grails.persistence.Entity
import grails.core.support.proxy.DefaultProxyHandler
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.core.lifecycle.ShutdownOperations
import org.grails.web.converters.jackson.DomainClassJacksonModule
import org.grails.web.converters.jackson.DomainClassRendering
import org.grails.web.converters.jackson.GrailsJsonMapperCustomizer
import spock.lang.Specification

class NamedJsonConfigurationRegistrySpec extends Specification {

    void "a registry is not created without a mapper, rather than failing on its first write"() {
        when:
        new NamedJsonConfigurationRegistry((JsonMapper) null)

        then:
        NullPointerException e = thrown()
        e.message == 'jsonMapper'
    }

    void 'mapper resolution retries absence and caches the first successful lookup'() {
        given:
        int lookups = 0
        JsonMapper available = null
        def registry = new NamedJsonConfigurationRegistry({ -> lookups++; available })
        registry.register('deep') { }

        when:
        registry.writer('deep')

        then:
        thrown(IllegalStateException)
        lookups == 1

        when:
        available = JsonMapper.builder().build()
        String named = registry.writeValueAsString('deep', [ok: true])
        String unnamed = registry.writeValueAsString(null, [ok: true])

        then:
        named == '{"ok":true}'
        unnamed == named
        lookups == 2
    }

    void 'concurrent first writes resolve the mapper without serializing bean lookups'() {
        given:
        def mapper = JsonMapper.builder().build()
        def lookups = new AtomicInteger()
        def barrier = new CyclicBarrier(2)
        def registry = new NamedJsonConfigurationRegistry({ ->
            lookups.incrementAndGet()
            barrier.await(5, TimeUnit.SECONDS)
            mapper
        })
        registry.register('deep') { }
        def executor = Executors.newFixedThreadPool(2)

        when:
        def writes = (1..2).collect {
            executor.submit({ -> registry.writeValueAsString('deep', [ok: true]) } as Callable<String>)
        }
        def results = writes.collect { it.get(10, TimeUnit.SECONDS) }

        then:
        results == ['{"ok":true}', '{"ok":true}']
        registry.writeValueAsString(null, [ok: true]) == '{"ok":true}'
        lookups.get() == 2

        cleanup:
        executor.shutdownNow()
    }

    void 'named serializers are isolated and support direct string and writer output'() {
        given:
        JsonMapper mapper = JsonMapper.builder().build()
        def registry = new NamedJsonConfigurationRegistry(mapper)
        boolean containedBefore = registry.contains('custom')
        registry.register('custom') {
            it.serializer(NamedJsonValue, new NamedJsonValueSerializer())
        }
        def value = new NamedJsonValue(name: 'Grails')
        def output = new StringWriter()

        when:
        String named = registry.writeValueAsString('custom', value)
        registry.writeValue('custom', output, value)

        then:
        !containedBefore
        registry.contains('custom')
        named == '{"configured":"GRAILS"}'
        output.toString() == named
        mapper.writeValueAsString(value) == '{"name":"Grails"}'
    }

    void 'a write to a response writer leaves it open and unflushed, so that one failing part-way leaves the response uncommitted'() {
        given: "a writer that records what the registry does with it, as a response's writer commits the response"
        def registry = new NamedJsonConfigurationRegistry(JsonMapper.builder().build())
        def calls = []
        def output = new StringWriter() {
            @Override
            void flush() {
                calls << 'flush'
            }

            @Override
            void close() {
                calls << 'close'
            }
        }

        when:
        registry.writeValue(configuration, output, new NamedJsonFailing(title: 'Grails'), includes, null)

        then:
        thrown(JacksonException)
        calls.isEmpty()

        when: 'a write that succeeds'
        registry.writeValue(configuration, output, new NamedJsonValue(name: 'Grails'), includes, null)

        then:
        calls.isEmpty()
        output.toString().endsWith('{"name":"Grails"}')

        where:
        configuration | includes
        null          | null
        'deep'        | null
        'deep'        | ['title', 'name', 'failing']
    }

    void 'an I/O failure of the writer is the IOException the write declares, for a value of #characters characters'() {
        given:
        def registry = new NamedJsonConfigurationRegistry(JsonMapper.builder().build())
        def output = new Writer() {
            @Override
            void write(char[] buffer, int offset, int length) {
                throw new IOException('broken pipe')
            }

            @Override
            void flush() {
            }

            @Override
            void close() {
            }
        }

        when: 'written while the value is, or once it is complete, where it fits in the buffer'
        registry.writeValue('deep', output, new NamedJsonValue(name: 'G' * characters))

        then: "with Jackson's exception suppressed"
        IOException e = thrown()
        e.message == 'broken pipe'
        e.suppressed.every { it instanceof JacksonException }
        e.suppressed.size() == 1

        where:
        characters << [60000, 6]
    }

    void 'a value that fails while the writer is broken is reported as Jackson reports it, with the failure of the writer suppressed'() {
        given: 'a writer that fails when Jackson writes to it what it has buffered, once the value has failed'
        def registry = new NamedJsonConfigurationRegistry(JsonMapper.builder().build())
        def output = new Writer() {
            @Override
            void write(char[] buffer, int offset, int length) {
                throw new IOException('broken pipe')
            }

            @Override
            void flush() {
            }

            @Override
            void close() {
            }
        }

        when:
        registry.writeValue('deep', output, new NamedJsonFailing(title: 'Grails'))

        then:
        DatabindException e = thrown()
        e.cause instanceof IllegalStateException
        e.path*.propertyName == ['failing']
        e.suppressed*.cause*.message == ['broken pipe']
    }

    void 'an I/O failure of the value written is reported as Jackson reports it, with its path, rather than as a failure of the writer'() {
        given:
        def registry = new NamedJsonConfigurationRegistry(JsonMapper.builder().build())
        def output = new StringWriter()

        when:
        registry.writeValue('deep', output, new NamedJsonUnreadable(name: 'Grails'))

        then:
        DatabindException e = thrown()
        e.cause instanceof FileNotFoundException
        e.path*.propertyName == ['content']
    }

    void 'a property a bean unwraps with the name of one of its own is rejected, as the JsonMapper rejects it'() {
        given:
        def plain = JsonMapper.builder().build()
        def registry = new NamedJsonConfigurationRegistry(plain)

        when:
        plain.writeValueAsString(new NamedJsonUnwrapping(id: 5, inner: new NamedJsonInner(id: 1, name: 'a')))

        then:
        thrown(InvalidDefinitionException)

        when:
        registry.writeValueAsString(null, new NamedJsonUnwrapping(id: 5, inner: new NamedJsonInner(id: 1, name: 'a')))

        then:
        thrown(InvalidDefinitionException)

        expect: 'a property it unwraps without a conflict is written into it'
        registry.writeValueAsString(null, new NamedJsonPrefixedUnwrapping(id: 5, inner: new NamedJsonInner(id: 1, name: 'a'))) ==
                '{"id":5,"inner_id":1,"inner_name":"a"}'
    }

    void 'a configuration cannot render domain class instances in ways of their own, which only the JSON converter does'() {
        given:
        def registry = new NamedJsonConfigurationRegistry(JsonMapper.builder().build())

        when:
        registry.register('own') {
            it.attribute(DomainClassJacksonModule.RENDERINGS, { Object instance -> null } as Function<Object, DomainClassRendering>)
        }

        then:
        IllegalArgumentException e = thrown()
        e.message.contains(DomainClassJacksonModule.RENDERINGS)
    }

    void 'unknown configurations fail clearly'() {
        given:
        def registry = new NamedJsonConfigurationRegistry(JsonMapper.builder().build())

        when:
        registry.writeValueAsString('missing', [:])

        then:
        def error = thrown(IllegalArgumentException)
        error.message == 'Named JSON configuration [missing] is not registered.'
    }

    void 'null denotes the default configuration'() {
        expect:
        new NamedJsonConfigurationRegistry(JsonMapper.builder().build()).contains(null)
    }

    void 'registered configurations reject changes rather than silently retaining a cached writer'() {
        given:
        def registry = new NamedJsonConfigurationRegistry(JsonMapper.builder().build())
        NamedJsonConfiguration captured
        registry.register('fixed') { captured = it }
        registry.writer('fixed')

        when:
        mutation.call(captured)

        then:
        thrown(IllegalStateException)

        where:
        mutation << [
                { it.serializer(NamedJsonValue, new NamedJsonValueSerializer()) },
                { it.view(String) },
                { it.attribute('key', 'value') }
        ]
    }

    void 'a projection applies to the properties of #description'() {
        given: 'a registry over a plain mapper, which adds projection support itself'
        def registry = new NamedJsonConfigurationRegistry(JsonMapper.builder().build())
        registry.register('safe') { }
        def output = new StringWriter()

        when:
        registry.writeValue('safe', output, value, includes, excludes)

        then:
        new JsonSlurper().parseText(output.toString()) == expected

        where:
        description                | value                                                           | includes   | excludes       || expected
        'a bean'                   | new NamedJsonCredentials(user: 'a', password: 'b')              | null       | ['password']   || [user: 'a']
        'each bean of a list'      | [new NamedJsonCredentials(user: 'a', password: 'b')]            | null       | ['password']   || [[user: 'a']]
        'each bean of an array'    | [new NamedJsonCredentials(user: 'a', password: 'b')] as Object[] | ['user']  | null           || [[user: 'a']]
        'a bean by its bean name'  | new NamedJsonRenamed(displayName: 'Ada', secret: 's')           | null       | ['displayName'] || [secret: 's']
        'a bean by its JSON name'  | new NamedJsonRenamed(displayName: 'Ada', secret: 's')           | null       | ['display']    || [secret: 's']
    }

    void 'a projection applies to the type written, not to other beans it contains'() {
        given:
        def registry = new NamedJsonConfigurationRegistry(JsonMapper.builder().build())
        def output = new StringWriter()
        def account = new NamedJsonAccount(name: 'main', credentials: new NamedJsonCredentials(user: 'a', password: 'b'))

        when: 'as with the legacy converter, the projection names properties of the written type'
        registry.writeValue(null, output, account, ['name', 'credentials'], ['password'])

        then:
        new JsonSlurper().parseText(output.toString()) == [name: 'main', credentials: [user: 'a', password: 'b']]
    }

    void 'a projection applies to the entries of a @JsonAnyGetter by the name of the property it is, or its own'() {
        given:
        def registry = new NamedJsonConfigurationRegistry(JsonMapper.builder().build())
        def secrets = new NamedJsonAnyGetterProperty(name: 'a', secrets: [token: 't'])
        def extras = new NamedJsonAnyGetterMethod(name: 'a')

        expect:
        projected(registry, secrets, null, ['secrets']) == '{"name":"a"}'
        projected(registry, secrets, ['name'], null) == '{"name":"a"}'
        projected(registry, secrets, [], null) == '{}'
        projected(registry, secrets, ['secrets'], null) == '{"token":"t"}'
        projected(registry, extras, ['name'], null) == '{"name":"a"}'
        projected(registry, extras, null, ['name']) == '{"extra":"v"}'
    }

    void 'a projection applies to the entries of a @JsonAnyGetter that are an ObjectNode, as to those of a Map'() {
        given:
        def registry = new NamedJsonConfigurationRegistry(JsonMapper.builder().build())
        def node = new NamedJsonAnyGetterNode(name: 'a')

        expect:
        projected(registry, node, null, null) == '{"name":"a","extra":"v"}'
        projected(registry, node, ['name'], null) == '{"name":"a"}'
        projected(registry, node, null, ['name']) == '{"extra":"v"}'
    }

    private static String projected(NamedJsonConfigurationRegistry registry, Object value, List<String> includes,
                                     List<String> excludes) {
        def output = new StringWriter()
        registry.writeValue(null, output, value, includes, excludes)
        output.toString()
    }

    void 'a projection a serializer of its own bypasses is reported, once for each type'() {
        given: 'an application started afresh, which has reported nothing'
        ShutdownOperations.runOperations()
        def registry = new NamedJsonConfigurationRegistry(JsonMapper.builder().build())
        registry.register('custom') { it.serializer(NamedJsonValue, new NamedJsonValueSerializer()) }
        def output = new StringWriter()

        when:
        String log = stderrWhile {
            registry.writeValue('custom', output, new NamedJsonValue(name: 'secret'), null, ['configured'])
            registry.writeValue('custom', new StringWriter(), new NamedJsonValue(name: 'secret'), null, ['configured'])
        }

        then:
        output.toString() == '{"configured":"SECRET"}'
        log.count("JSON includes/excludes were not applied to [class ${NamedJsonValue.name}]") == 1
    }

    void 'a projection a serializer of its own bypasses is reported again after the application shuts down'() {
        given:
        ShutdownOperations.runOperations()
        def registry = new NamedJsonConfigurationRegistry(JsonMapper.builder().build())
        registry.register('custom') { it.serializer(NamedJsonValue, new NamedJsonValueSerializer()) }

        when:
        String log = stderrWhile {
            registry.writeValue('custom', new StringWriter(), new NamedJsonValue(name: 'secret'), null, ['configured'])
            ShutdownOperations.runOperations()
            registry.writeValue('custom', new StringWriter(), new NamedJsonValue(name: 'secret'), null, ['configured'])
        }

        then:
        log.count("JSON includes/excludes were not applied to [class ${NamedJsonValue.name}]") == 2
    }

    void 'the elements of an Iterable that is not a Collection are written, not consumed by the projection'() {
        given: 'an iterable that can only be iterated once, in an application started afresh'
        ShutdownOperations.runOperations()
        def registry = new NamedJsonConfigurationRegistry(JsonMapper.builder().build())
        Iterator<NamedJsonCredentials> elements = [new NamedJsonCredentials(user: 'a', password: 'b')].iterator()
        Iterable<NamedJsonCredentials> once = { -> elements } as Iterable<NamedJsonCredentials>
        def output = new StringWriter()

        when:
        String log = stderrWhile {
            registry.writeValue(null, output, once, null, ['password'])
            registry.writeValue(null, new StringWriter(), once, null, ['password'])
        }

        then:
        new JsonSlurper().parseText(output.toString()) == [[user: 'a', password: 'b']]
        log.count('were not applied to the elements of a') == 1
        log.contains('which is not a Collection')
    }

    void 'a projection of a value without properties fails before anything is written'() {
        given:
        def registry = new NamedJsonConfigurationRegistry(JsonMapper.builder().build())
        registry.register('safe') { }
        def output = new StringWriter()

        when:
        registry.writeValue('safe', output, value, null, ['password'])

        then:
        thrown(IllegalArgumentException)
        output.toString().empty

        where:
        value << [[password: 'secret'], 'secret', [[password: 'secret']], [['secret']]]
    }

    void "a configuration's attributes are added to the mapper's own"() {
        given:
        def mapper = JsonMapper.builder()
                .defaultAttributes(ContextAttributes.getEmpty().withSharedAttribute('who', 'app'))
                .addModule(new SimpleModule().addSerializer(NamedJsonEcho, new NamedJsonEchoSerializer()))
                .build()
        def registry = new NamedJsonConfigurationRegistry(mapper)
        registry.register('attributed') { it.attribute('what', 'named') }

        expect:
        registry.writeValueAsString('attributed', new NamedJsonEcho()) == '"app/named"'
    }

    void 'a configuration may have any name, also that of a module the registry writes with'() {
        given:
        def registry = new NamedJsonConfigurationRegistry(JsonMapper.builder().build())
        registry.register('projection') { }
        def output = new StringWriter()

        when:
        registry.writeValue('projection', output, new NamedJsonCredentials(user: 'a', password: 'b'), null, ['password'])

        then:
        new JsonSlurper().parseText(output.toString()) == [user: 'a']
    }

    void 'the writer for a configuration is derived once and reused'() {
        given:
        def registry = new NamedJsonConfigurationRegistry(JsonMapper.builder().build())
        registry.register('deep') { it.attribute('depth', 'deep') }

        expect: "rebuilding a mapper per response is expensive, so the writer is cached"
        registry.writer('deep').is(registry.writer('deep'))
    }

    void 'a re-registered configuration derives a new writer'() {
        given:
        def registry = new NamedJsonConfigurationRegistry(JsonMapper.builder().build())
        registry.register('deep') { it.attribute('depth', 'shallow') }
        def first = registry.writer('deep')

        when:
        registry.register('deep') { it.attribute('depth', 'deep') }

        then:
        !registry.writer('deep').is(first)
    }


    void 'a projection applies on top of the named configuration'() {
        given: "a domain-style value written through a named configuration"
        def mappingContext = new KeyValueMappingContext('named')
        mappingContext.addPersistentEntities(NamedJsonBook)
        def application = new DefaultGrailsApplication(NamedJsonBook)
        application.mappingContext = mappingContext
        def builder = JsonMapper.builder()
        def customizer = new GrailsJsonMapperCustomizer(application, new DefaultProxyHandler())
        customizer.customize(builder)
        def registry = new NamedJsonConfigurationRegistry(customizer.forGrails(builder.build()))
        registry.register('deep') { it.attribute('depth', 'deep') }
        def book = new NamedJsonBook(title: 'Projected').tap { id = 4 }

        when: "the response also asks for a projection"
        def writer = new StringWriter()
        registry.writeValue('deep', writer, book, ['title'], null)

        then: "selecting a configuration does not discard it"
        writer.toString() == '{"title":"Projected"}'
    }


    private static String stderrWhile(Closure<?> work) {
        PrintStream original = System.err
        def buffer = new ByteArrayOutputStream()
        System.setErr(new PrintStream(buffer, true))
        try {
            work.call()
        }
        finally {
            System.setErr(original)
        }
        buffer.toString()
    }
}

class NamedJsonUnwrapping {
    Long id
    @JsonUnwrapped
    NamedJsonInner inner
}

class NamedJsonPrefixedUnwrapping {
    Long id
    @JsonUnwrapped(prefix = 'inner_')
    NamedJsonInner inner
}

class NamedJsonInner {
    Long id
    String name
}

class NamedJsonFailing {
    String title

    String getFailing() {
        throw new IllegalStateException('the value cannot be read')
    }
}

class NamedJsonUnreadable {
    String name

    String getContent() throws IOException {
        throw new FileNotFoundException('content.txt')
    }
}

class NamedJsonValue {
    String name
}

class NamedJsonCredentials {
    String user
    String password
}

class NamedJsonAnyGetterProperty {
    String name
    @JsonAnyGetter
    Map<String, String> secrets
}

class NamedJsonAnyGetterNode {
    String name

    @JsonAnyGetter
    ObjectNode getExtras() {
        JsonNodeFactory.instance.objectNode().put('extra', 'v')
    }
}

class NamedJsonAnyGetterMethod {
    String name

    @JsonAnyGetter
    Map<String, Object> getExtras() {
        [extra: 'v']
    }
}

class NamedJsonAccount {
    String name
    NamedJsonCredentials credentials
}

class NamedJsonRenamed {
    @JsonProperty('display')
    String displayName
    String secret
}

class NamedJsonEcho { }

/** Writes the attributes a write carries. */
class NamedJsonEchoSerializer extends ValueSerializer<NamedJsonEcho> {

    @Override
    void serialize(NamedJsonEcho value, JsonGenerator generator, SerializationContext context) {
        generator.writeString("${context.getAttribute('who')}/${context.getAttribute('what')}".toString())
    }
}

class NamedJsonValueSerializer extends ValueSerializer<NamedJsonValue> {

    @Override
    void serialize(NamedJsonValue value, JsonGenerator generator, SerializationContext context) throws JacksonException {
        generator.writeStartObject()
        generator.writeStringProperty('configured', value.name.toUpperCase(Locale.ROOT))
        generator.writeEndObject()
    }
}

@Entity
class NamedJsonBook {
    Long id
    String title
}
