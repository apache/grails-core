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

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.annotation.JsonView
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.module.SimpleModule
import tools.jackson.databind.ser.std.ToStringSerializer

import groovy.json.JsonSlurper

import grails.converters.json.NamedJsonConfigurationRegistry
import grails.core.DefaultGrailsApplication
import grails.core.support.proxy.DefaultProxyHandler
import grails.core.support.proxy.EntityProxyHandler
import org.grails.datastore.mapping.proxy.EntityProxy
import grails.core.support.proxy.ProxyHandler
import grails.persistence.Entity
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.validation.BeanPropertyBindingResult

import tools.jackson.databind.json.JsonMapper

import org.grails.config.PropertySourcesConfig
import org.grails.core.exceptions.GrailsConfigurationException
import org.grails.datastore.mapping.model.MappingContext

import spock.lang.Specification

class GrailsJsonMapperCustomizerSpec extends Specification {

    void 'only the Grails mapper receives the validation errors serializer'() {
        given:
        def errors = new BeanPropertyBindingResult(new JsonCommand(), 'command')
        errors.rejectValue('name', 'blank', 'must not be blank')
        def context = new AnnotationConfigApplicationContext()
        context.registerBean(GrailsJsonMapperCustomizer)
        context.register(JacksonAutoConfiguration)
        context.refresh()

        expect: "the same entry shape the RFC 9457 problem uses, so the two cannot drift apart"
        def bootMapper = context.getBean(JsonMapper)
        def mapper = context.getBean(GrailsJsonMapperCustomizer).forGrails(bootMapper)
        def entry = mapper.readValue(mapper.writeValueAsString(errors), Map).errors.first()
        entry.object == 'command'
        entry.field == 'name'
        entry.codes.contains('blank')
        entry.message == 'must not be blank'

        and: "the submitted value is never exposed on this path"
        !entry.containsKey('rejectedValue')

        when: 'the shared mapper uses ordinary bean serialization, including the cyclic model'
        bootMapper.writeValueAsString(errors)

        then: 'Grails has not installed its Errors serializer on the shared mapper'
        thrown(tools.jackson.core.exc.StreamConstraintsException)

        cleanup:
        context.close()
    }

    void 'Grails response mapper uses persistent metadata and the configured identity policy for domain objects'() {
        given:
        def mapper = domainMapper(true, true)
        def author = new JacksonAuthor(name: 'Douglas').tap { id = 2 }
        def book = new JacksonBook(title: 'Mostly Harmless', authors: [author], authorsByName: [douglas: author]).tap {
            id = 1
            version = 3
        }

        expect:
        mapper.readValue(mapper.writeValueAsString(book), Map) == [
                class: JacksonBook.name,
                id: 1,
                version: 3,
                title: 'Mostly Harmless',
                authors: [[class: JacksonAuthor.name, id: 2]],
                authorsByName: [douglas: [class: JacksonAuthor.name, id: 2]],
        ]
    }

    void 'an unsaved domain instance omits its null identity and version, as the legacy marshaller does'() {
        given:
        def mapper = domainMapper(true, false)

        expect:
        mapper.writeValueAsString(new JacksonBook(title: 'Unsaved')) ==
                '{"title":"Unsaved","authors":null,"authorsByName":null}'
    }

    void 'Grails response mapper unwraps domain proxies before reading persistent properties'() {
        given:
        def target = new JacksonBook(title: 'Unwrapped').tap { id = 1 }
        def proxy = new JacksonBookProxy(target: target)
        def proxyHandler = Stub(ProxyHandler) {
            isProxy(proxy) >> true
            unwrapIfProxy(_ as Object) >> { arguments -> arguments[0].is(proxy) ? target : arguments[0] }
        }
        def mapper = domainMapper(false, false, proxyHandler)

        expect:
        mapper.readValue(mapper.writeValueAsString(proxy), Map) == [
                id: 1, title: 'Unwrapped', authors: null, authorsByName: null,
        ]
    }

    void 'a projection applies to one domain write'() {
        given:
        def registry = new NamedJsonConfigurationRegistry(domainMapper(false, false))
        def book = new JacksonBook(title: 'Filtered').tap {
            id = 1
            version = 3
        }
        def output = new StringWriter()

        when:
        registry.writeValue(null, output, book, ['id', 'title'], ['title'])

        then:
        output.toString() == '{"id":1}'

        and: 'the next write is not projected'
        new JsonSlurper().parseText(registry.writeValueAsString(null, book)) == [
                id: 1, title: 'Filtered', authors: null, authorsByName: null,
        ]
    }

    void 'a projection of a proxied domain object applies to its target'() {
        given:
        def target = new JacksonBook(title: 'Unwrapped').tap { id = 1 }
        def proxy = new JacksonBookProxy(target: target)
        def proxyHandler = Stub(ProxyHandler) {
            unwrapIfProxy(_ as Object) >> { arguments -> arguments[0].is(proxy) ? target : arguments[0] }
        }
        def registry = new NamedJsonConfigurationRegistry(domainMapper(false, false, proxyHandler))
        def output = new StringWriter()

        when:
        registry.writeValue(null, output, proxy, ['title'], null)

        then:
        output.toString() == '{"title":"Unwrapped"}'
    }

    void 'the mapper builds before GORM is initialized and picks up entities afterwards'() {
        given: "a real application, whose mapping context proxy fails on any lookup until GORM runs"
        def application = new DefaultGrailsApplication(JacksonBook)
        application.initialise()

        when: "auto-configuration builds the mapper ahead of GORM"
        def builder = JsonMapper.builder()
        def customizer = new GrailsJsonMapperCustomizer(application, new DefaultProxyHandler())
        customizer.customize(builder)
        def mapper = customizer.forGrails(builder.build())

        then: "building does not fail"
        noExceptionThrown()

        when: "GORM finishes and a domain object is written"
        def mappingContext = new KeyValueMappingContext('jackson')
        mappingContext.addPersistentEntities(JacksonBook)
        application.mappingContext = mappingContext
        def written = mapper.readValue(mapper.writeValueAsString(new JacksonBook(title: 'Later').tap { id = 7 }), Map)

        then: "the persistent metadata is used, resolved on first write rather than at build time"
        written.id == 7
        written.title == 'Later'
    }

    void 'a type written before GORM is ready still uses the domain serializer afterwards'() {
        given: "a real application, whose getMappingContext hands out a proxy that fails on use"
        def application = new DefaultGrailsApplication(JacksonBook, JacksonAuthor)
        application.initialise()
        application.config.setAt('grails.converters.domain.include.class', true)
        def builder = JsonMapper.builder()
        def customizer = new GrailsJsonMapperCustomizer(application, new DefaultProxyHandler())
        customizer.customize(builder)
        def mapper = customizer.forGrails(builder.build())
        def book = new JacksonBook(title: 'Cached').tap { id = 9 }

        when: "the type is written once before GORM has initialized"
        mapper.writeValueAsString(book)

        then: "writing is refused rather than silently producing a bean-shaped document, because GORM is not ready"
        def e = thrown(Exception)
        causes(e).any { it instanceof GrailsConfigurationException }

        and: "a null check could not have detected this, since the proxy is not null"
        application.mappingContext != null

        when: "GORM initializes and the same class is written again"
        def mappingContext = new KeyValueMappingContext('jackson')
        mappingContext.addPersistentEntities(JacksonBook, JacksonAuthor)
        application.mappingContext = mappingContext
        def after = mapper.readValue(mapper.writeValueAsString(book), Map)

        then: "the Grails domain serializer is used, not a bean serializer cached on the first write"
        after.class == JacksonBook.name
        after.id == 9
        after.title == 'Cached'
    }

    private static List<Throwable> causes(Throwable throwable) {
        List<Throwable> causes = []
        for (Throwable cause = throwable; cause != null && !causes.contains(cause); cause = cause.cause) {
            causes << cause
        }
        causes
    }

    void 'a mapping defect surfaces instead of falling back to bean serialization'() {
        given: "a mapping context that fails for a reason other than GORM not being ready"
        def application = new DefaultGrailsApplication(JacksonBook) {
            @Override
            MappingContext getMappingContext() {
                throw new IllegalStateException('broken mapping')
            }
        }
        application.initialise()
        def builder = JsonMapper.builder()
        def customizer = new GrailsJsonMapperCustomizer(application, new DefaultProxyHandler())
        customizer.customize(builder)
        def mapper = customizer.forGrails(builder.build())

        when:
        mapper.writeValueAsString(new JacksonBook(title: 'Broken').tap { id = 3 })

        then: "the defect is not swallowed into ordinary bean serialization"
        def e = thrown(Exception)
        (e.message ?: e.cause?.message).contains('broken mapping')
    }

    void 'Boot JsonMapper writes nested and root GStrings as JSON strings'() {
        given:
        def builder = JsonMapper.builder()
        new GrailsJsonMapperCustomizer().customize(builder)
        def mapper = builder.build()
        def title = 'Grails'

        expect:
        mapper.writeValueAsString("Saved ${title}") == '"Saved Grails"'
        mapper.writeValueAsString([message: "Saved ${title}"]) == '{"message":"Saved Grails"}'
    }

    void 'the Grails mapper keeps the mix-ins, naming, inclusion and views of the mapper it derives from'() {
        given:
        def mapping = new KeyValueMappingContext('derived')
        mapping.addPersistentEntities(AnnotatedJacksonBook)
        def app = new DefaultGrailsApplication(AnnotatedJacksonBook)
        app.initialise()
        app.mappingContext = mapping
        def builder = JsonMapper.builder().addMixIn(AnnotatedJacksonBook, AnnotatedJacksonBookMixin)
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .changeDefaultPropertyInclusion { it.withValueInclusion(JsonInclude.Include.NON_NULL) }
        def customizer = new GrailsJsonMapperCustomizer(app, new DefaultProxyHandler())
        customizer.customize(builder)
        def mapper = customizer.forGrails(builder.build())
        def book = new AnnotatedJacksonBook(secret: 'private', title: 'Grails', firstName: 'Ada',
                published: new Date(0), internal: 'hidden').tap { id = 4 }

        expect: 'the domain class is written as Grails writes it, with what the mapper applies to any bean'
        mapper.readValue(mapper.writerWithView(PublicView).writeValueAsString(book), Map) == [
                id: 4, book_title: 'Grails', first_name: 'Ada', published: '1970']
    }

    void 'application domain serializers keep precedence in the Grails mapper'() {
        given:
        def application = new DefaultGrailsApplication(JacksonBook)
        application.initialise()
        def source = JsonMapper.builder().addModule(new SimpleModule('application-json')
                .addSerializer(JacksonBook, ToStringSerializer.instance)).build()
        def book = new JacksonBook(title: 'Custom').tap { id = 3 }
        def mapper = new GrailsJsonMapperCustomizer(application).forGrails(source)

        expect:
        mapper.writeValueAsString(book) == source.writeValueAsString(book)
    }

    void 'the properties a @JsonValue accessor leaves out of the bean it gives are left out, as the mapper leaves them out'() {
        given:
        def application = new DefaultGrailsApplication()
        application.initialise()
        def source = JsonMapper.builder().build()
        def mapper = new GrailsJsonMapperCustomizer(application).forGrails(source)
        def holder = new JacksonValueHolder(account: new JacksonAccount(user: 'ada', secret: 's3cret'))

        expect:
        mapper.writeValueAsString(holder) == '{"user":"ada"}'
        mapper.writeValueAsString(holder) == source.writeValueAsString(holder)
    }

    private JsonMapper domainMapper(boolean includeVersion, boolean includeClass, ProxyHandler proxyHandler = null) {
        def mappingContext = new KeyValueMappingContext('jackson')
        mappingContext.addPersistentEntities(JacksonBook, JacksonAuthor)
        def application = new DefaultGrailsApplication(JacksonBook, JacksonAuthor)
        application.initialise()
        application.mappingContext = mappingContext
        application.config.setAt('grails.converters.domain.include.version', includeVersion)
        application.config.setAt('grails.converters.domain.include.class', includeClass)
        def builder = JsonMapper.builder()
        def customizer = new GrailsJsonMapperCustomizer(application, proxyHandler ?: new DefaultProxyHandler())
        customizer.customize(builder)
        customizer.forGrails(builder.build())
    }

    void 'a cycle back to a domain object being written follows circular reference behaviour #behaviour'() {
        given: 'an embedded value whose nested bean points back at its owner'
        def person = new JacksonPerson(name: 'Ada').tap { id = 1 }
        person.address = new JacksonAddress(street: 'Main', geo: new JacksonGeo(owner: person))

        expect:
        def mapper = cycleMapper(behaviour)
        def written = mapper.readValue(mapper.writeValueAsString(root ? [person] : person), Object)
        (root ? written[0] : written).address.geo.owner == owner

        and: 'a property, whose name is written, is written whatever the behaviour'
        (root ? written[0] : written).address.geo.containsKey('owner')

        where:
        behaviour     | root  || owner
        'DEFAULT'     | false || [_ref: '../..', class: JacksonPerson.name]
        'DEFAULT'     | true  || [_ref: '../..', class: JacksonPerson.name]
        'PATH'        | false || [ref: 'root', class: JacksonPerson.name]
        'PATH'        | true  || [ref: 'root[0]', class: JacksonPerson.name]
        'INSERT_NULL' | false || null
        'IGNORE'      | false || null
    }

    void 'a cycle back to a domain object fails when the behaviour is EXCEPTION'() {
        given:
        def person = new JacksonPerson(name: 'Ada').tap { id = 1 }
        person.address = new JacksonAddress(street: 'Main', geo: new JacksonGeo(owner: person))

        when:
        cycleMapper('EXCEPTION').writeValueAsString(person)

        then:
        def e = thrown(tools.jackson.databind.DatabindException)
        e.message.contains("Circular Reference detected: class ${JacksonPerson.name}")
    }

    void 'a domain object repeated outside its own value is written in full each time'() {
        given:
        def person = new JacksonPerson(name: 'Ada').tap { id = 1 }

        expect:
        def mapper = cycleMapper('DEFAULT')
        mapper.readValue(mapper.writeValueAsString([person, person]), List) ==
                [[id: 1, name: 'Ada', address: null], [id: 1, name: 'Ada', address: null]]
    }

    private static JsonMapper cycleMapper(String behaviour) {
        def mappingContext = new KeyValueMappingContext('jackson')
        mappingContext.addPersistentEntities(JacksonPerson)
        def application = new DefaultGrailsApplication(JacksonPerson)
        application.initialise()
        application.mappingContext = mappingContext
        application.config.setAt('grails.converters.json.circular.reference.behaviour', behaviour)
        new GrailsJsonMapperCustomizer(application, new DefaultProxyHandler()).forGrails(JsonMapper.builder().build())
    }

    void 'a cycle made only of beans follows circular reference behaviour #behaviour'() {
        given:
        def first = new JacksonNode(name: 'first')
        first.next = new JacksonNode(name: 'second', next: first)

        expect:
        def mapper = beanCycleMapper(behaviour)
        def written = mapper.readValue(mapper.writeValueAsString(root ? [first] : first), Object)
        (root ? written[0] : written).next.next == repeated

        and: 'a property, whose name is written, is written whatever the behaviour'
        (root ? written[0] : written).next.containsKey('next')

        where:
        behaviour     | root  || repeated
        'DEFAULT'     | false || [_ref: '..', class: JacksonNode.name]
        'DEFAULT'     | true  || [_ref: '..', class: JacksonNode.name]
        'PATH'        | false || [ref: 'root', class: JacksonNode.name]
        'PATH'        | true  || [ref: 'root[0]', class: JacksonNode.name]
        'INSERT_NULL' | false || null
        'IGNORE'      | false || null
    }

    void 'a cycle reached as an element of a list is #outcome when the behaviour is #behaviour'() {
        given:
        def first = new JacksonListNode(name: 'first')
        first.children = [new JacksonListNode(name: 'second', children: [first])]

        expect:
        def mapper = beanCycleMapper(behaviour)
        mapper.readValue(mapper.writeValueAsString(first), Map).children[0].children == elements

        where:
        behaviour     || elements | outcome
        'IGNORE'      || []       | 'left out'
        'INSERT_NULL' || [null]   | 'written as null'
    }

    void 'with the IGNORE behaviour, a cycle reached as a value of a map is null, as a property is'() {
        given:
        def keyed = new JacksonKeyedNode(name: 'keyed')
        keyed.byName = [self: keyed]

        expect:
        beanCycleMapper('IGNORE').writeValueAsString(keyed) == '{"byName":{"self":null},"name":"keyed"}'
    }

    void 'with the IGNORE behaviour, a cycle reached as a property of a bean written as an array is null, so that the positions are kept'() {
        given:
        def lone = new JacksonRowNode(name: 'lone')
        lone.parent = lone

        expect:
        beanCycleMapper('IGNORE').writeValueAsString(lone) == '["lone",null]'
    }

    void 'a bean that refers to itself fails when the behaviour is EXCEPTION'() {
        given:
        def node = new JacksonNode(name: 'self')
        node.next = node

        when:
        beanCycleMapper('EXCEPTION').writeValueAsString(node)

        then:
        def e = thrown(tools.jackson.databind.DatabindException)
        e.message.contains("Circular Reference detected: class ${JacksonNode.name}")
    }

    void 'a bean repeated outside its own value is written in full each time'() {
        given:
        def shared = new JacksonNode(name: 'shared')
        def mapper = beanCycleMapper('DEFAULT')

        expect:
        mapper.readValue(mapper.writeValueAsString([shared, shared]), List) ==
                [[name: 'shared', next: null], [name: 'shared', next: null]]
    }

    void 'the circular reference behaviour is read once for a write, however many objects it writes'() {
        given:
        def config = new BehaviourReadsConfig()
        config.setAt('grails.converters.json.circular.reference.behaviour', 'PATH')
        def application = new DefaultGrailsApplication()
        application.initialise()
        application.config = config
        def mapper = new GrailsJsonMapperCustomizer(application, new DefaultProxyHandler()).forGrails(JsonMapper.builder().build())
        def nodes = (1..10).collect { new JacksonNode(name: "node $it").tap { next = it } } +
                (1..10).collect { new JacksonKeyedNode(name: "keyed $it").tap { byName = [self: it] } }
        mapper.writeValueAsString(nodes)
        config.behaviourReads = 0

        when: 'a write of objects of several classes, each reached again, once the mapper has its serializers'
        mapper.writeValueAsString(nodes)

        then:
        config.behaviourReads == 1
    }

    void 'with the DEFAULT behaviour, a bean that references itself is a reference to the object that holds the reference'() {
        given:
        def only = new JacksonNode(name: 'only')
        only.next = only

        expect: 'as ".." from the second of two beans that reference each other is the first'
        beanCycleMapper('DEFAULT').writeValueAsString(only) ==
                '{"name":"only","next":{"_ref":".","class":"' + JacksonNode.name + '"}}'
    }

    private static JsonMapper beanCycleMapper(String behaviour) {
        def application = new DefaultGrailsApplication()
        application.initialise()
        application.config.setAt('grails.converters.json.circular.reference.behaviour', behaviour)
        new GrailsJsonMapperCustomizer(application, new DefaultProxyHandler()).forGrails(JsonMapper.builder().build())
    }

    void 'the Grails mapper honours the Jackson annotations and naming strategy of a domain class'() {
        given:
        def mapping = new KeyValueMappingContext('annotated')
        mapping.addPersistentEntities(AnnotatedJacksonBook)
        def app = new DefaultGrailsApplication(AnnotatedJacksonBook)
        app.initialise()
        app.mappingContext = mapping
        def source = JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build()
        def mapper = new GrailsJsonMapperCustomizer(app, new DefaultProxyHandler()).forGrails(source)
        def book = new AnnotatedJacksonBook(secret: 'private', title: 'Grails', firstName: 'Ada').tap { id = 4 }

        when:
        def written = mapper.readValue(mapper.writeValueAsString(book), Map)

        then: 'a @JsonIgnore property is left out, a persistent property is named as Jackson names it'
        !written.containsKey('secret')
        written.book_title == 'Grails'
        written.first_name == 'Ada'
        written.id == 4
    }

    void 'the Grails mapper replaces the domain module of the mapper it derives from'() {
        given: 'a mapper whose own domain module would reference a cycle by id'
        def person = new JacksonPerson(name: 'Ada').tap { id = 1 }
        person.address = new JacksonAddress(street: 'Main', geo: new JacksonGeo(owner: person))
        def mapping = new KeyValueMappingContext('replace')
        mapping.addPersistentEntities(JacksonPerson)
        def app = new DefaultGrailsApplication(JacksonPerson)
        app.initialise()
        app.mappingContext = mapping
        app.config.setAt('grails.converters.json.circular.reference.behaviour', 'PATH')
        def rendering = new DomainClassRendering(app, new DefaultProxyHandler(), false, false, false)
        def source = JsonMapper.builder().addModule(new DomainClassJacksonModule(rendering)).build()
        def grails = new GrailsJsonMapperCustomizer(app, new DefaultProxyHandler()).forGrails(source)

        expect: 'the source mapper writes a reference of the id, the Grails mapper what the response setting selects'
        source.readValue(source.writeValueAsString(person), Map).address.geo.owner == [id: 1]
        grails.readValue(grails.writeValueAsString(person), Map).address.geo.owner == [ref: 'root', class: JacksonPerson.name]
    }

    void 'an association reference to a proxy that knows its identifier does not initialize the proxy'() {
        given:
        def mapping = new KeyValueMappingContext('references')
        mapping.addPersistentEntities(JacksonReview, JacksonAuthor)
        def app = new DefaultGrailsApplication(JacksonReview, JacksonAuthor)
        app.initialise()
        app.mappingContext = mapping
        def proxyHandler = new UnwrapCountingProxyHandler()
        def mapper = new GrailsJsonMapperCustomizer(app, proxyHandler).forGrails(JsonMapper.builder().build())
        def review = new JacksonReview(text: 'Fine', author: new JacksonAuthorProxy().tap { id = 7 }).tap { id = 1 }

        expect: 'the reference is the identifier alone, and a detached proxy would have thrown on unwrapping'
        new JsonSlurper().parseText(mapper.writeValueAsString(review)) == [id: 1, text: 'Fine', author: [id: 7]]
        proxyHandler.unwraps == 0
    }

    void 'a projection applies to a narrowed proxy inside a collection'() {
        given:
        def target = new SpecialJacksonBook(title: 'Subclass', edition: 'second').tap { id = 5 }
        def proxy = new JacksonBookProxy(target: target)
        def plain = new JacksonBook(title: 'Plain').tap { id = 1 }
        def mapping = new KeyValueMappingContext('narrowed')
        mapping.addPersistentEntities(JacksonBook, SpecialJacksonBook, JacksonAuthor)
        def app = new DefaultGrailsApplication(JacksonBook, SpecialJacksonBook, JacksonAuthor)
        app.initialise()
        app.mappingContext = mapping
        def proxyHandler = Stub(ProxyHandler) {
            unwrapIfProxy(_) >> { args -> args[0].is(proxy) ? target : args[0] }
        }
        def registry = new NamedJsonConfigurationRegistry(
                new GrailsJsonMapperCustomizer(app, proxyHandler).forGrails(JsonMapper.builder().build()))
        def output = new StringWriter()

        when:
        registry.writeValue(null, output, [plain, proxy], null, ['title'])

        then: 'the excluded property is left out of the proxy as well as of the plain instance'
        def written = new JsonSlurper().parseText(output.toString())
        written.every { !it.containsKey('title') }
        written[1].edition == 'second'
    }

    void 'a narrowed proxy renders the unwrapped subclass properties and class name'() {
        given:
        def target = new SpecialJacksonBook(title: 'Subclass', edition: 'second').tap { id = 5 }
        def proxy = new JacksonBookProxy(target: target)
        def mapping = new KeyValueMappingContext('polymorphic')
        mapping.addPersistentEntities(JacksonBook, SpecialJacksonBook, JacksonAuthor)
        def app = new DefaultGrailsApplication(JacksonBook, SpecialJacksonBook, JacksonAuthor)
        app.initialise()
        app.mappingContext = mapping
        app.config.setAt('grails.converters.domain.include.class', true)
        def proxyHandler = Stub(ProxyHandler) {
            unwrapIfProxy(_) >> { args -> args[0].is(proxy) ? target : args[0] }
        }
        def mapper = new GrailsJsonMapperCustomizer(app, proxyHandler).forGrails(JsonMapper.builder().build())

        expect:
        def result = mapper.readValue(mapper.writeValueAsString([pet: proxy]), Map).pet
        result.class == SpecialJacksonBook.name
        result.edition == 'second'
        result.title == 'Subclass'
    }
}

@Entity
class JacksonReview {
    Long id
    Long version
    String text
    JacksonAuthor author
}

class JacksonAuthorProxy extends JacksonAuthor {
}

/** Stands in for a lazy-loading handler: a proxy knows its identifier but cannot be initialized. */
class UnwrapCountingProxyHandler extends DefaultProxyHandler implements EntityProxyHandler {
    int unwraps

    @Override
    boolean isProxy(Object o) {
        o instanceof JacksonAuthorProxy
    }

    @Override
    Object unwrapIfProxy(Object o) {
        if (o instanceof JacksonAuthorProxy) {
            unwraps++
            throw new IllegalStateException('could not initialize proxy - no Session')
        }
        o
    }

    @Override
    Object getProxyIdentifier(Object o) {
        ((JacksonAuthorProxy) o).id
    }

    @Override
    Class<?> getProxiedClass(Object o) {
        JacksonAuthor
    }
}

@Entity
class SpecialJacksonBook extends JacksonBook {
    String edition
}

class JacksonNode {
    String name
    JacksonNode next
}

class BehaviourReadsConfig extends PropertySourcesConfig {

    int behaviourReads

    @Override
    <T> T getProperty(String key, Class<T> targetType, T defaultValue, List<T> allowedValues) {
        if (key == 'grails.converters.json.circular.reference.behaviour') {
            behaviourReads++
        }
        super.getProperty(key, targetType, defaultValue, allowedValues)
    }
}

class JacksonKeyedNode {
    String name
    Map<String, JacksonKeyedNode> byName
}

@JsonFormat(shape = JsonFormat.Shape.ARRAY)
@JsonPropertyOrder(['name', 'parent'])
class JacksonRowNode {
    String name
    JacksonRowNode parent
}

class JacksonListNode {
    String name
    List<JacksonListNode> children
}

class JsonCommand {
    String name
}

@Entity
class JacksonBook {
    static hasMany = [authors: JacksonAuthor, authorsByName: JacksonAuthor]

    Long id
    Long version
    String title
    List<JacksonAuthor> authors
    Map<String, JacksonAuthor> authorsByName
}

@Entity
class JacksonPerson {
    static embedded = ['address']

    Long id
    Long version
    String name
    JacksonAddress address
}

class JacksonAddress {
    String street
    JacksonGeo geo
}

class JacksonGeo {
    JacksonPerson owner
}

@Entity
class JacksonAuthor {
    Long id
    Long version
    String name
}

class JacksonBookProxy extends JacksonBook implements EntityProxy<JacksonBook> {
    JacksonBook target

    void initialize() {}

    boolean isInitialized() { target != null }

    Serializable getProxyKey() { id }
}

class PublicView { }
class InternalView extends PublicView { }

@Entity
@JsonView(PublicView)
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(['errors', 'dirty', 'dirtyPropertyNames', 'dirty_property_names', 'attached', 'version'])
class AnnotatedJacksonBook {
    @JsonIgnore
    String secret
    @JsonProperty('book_title')
    String title
    String firstName
    @JsonFormat(pattern = 'yyyy', timezone = 'UTC')
    Date published
    @JsonView(InternalView)
    String internal
    String missing
    String mixinHidden = 'private'
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String emptyText = ''

    String getSummary() { "$title by $firstName" }
}

abstract class AnnotatedJacksonBookMixin {
    @JsonIgnore
    abstract String getMixinHidden()
}

class JacksonAccount {
    String user
    String secret
}

class JacksonValueHolder {
    JacksonAccount account

    @JsonValue
    @JsonIgnoreProperties(['secret'])
    JacksonAccount getAccount() {
        account
    }
}
