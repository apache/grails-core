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

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonView
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.module.SimpleModule
import tools.jackson.databind.ser.std.ToStringSerializer

import groovy.json.JsonSlurper

import grails.converters.json.NamedJsonConfigurationRegistry
import grails.core.DefaultGrailsApplication
import grails.core.support.proxy.DefaultProxyHandler
import grails.core.support.proxy.ProxyHandler
import grails.persistence.Entity
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.validation.BeanPropertyBindingResult

import tools.jackson.databind.json.JsonMapper

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
        application.config.setAt('grails.converters.domain.include.class', true)
        def builder = JsonMapper.builder()
        def customizer = new GrailsJsonMapperCustomizer(application, new DefaultProxyHandler())
        customizer.customize(builder)
        def mapper = customizer.forGrails(builder.build())
        def book = new JacksonBook(title: 'Cached').tap { id = 9 }

        when: "the type is written once before GORM has initialized"
        mapper.writeValueAsString(book)

        then: "writing is refused rather than silently producing a bean-shaped document"
        thrown(Exception)

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

    void 'a mapping defect surfaces instead of falling back to bean serialization'() {
        given: "a mapping context that fails for a reason other than GORM not being ready"
        def application = new DefaultGrailsApplication(JacksonBook) {
            @Override
            MappingContext getMappingContext() {
                throw new IllegalStateException('broken mapping')
            }
        }
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

    void 'Grails domain compatibility does not change Jackson annotations on the shared mapper'() {
        given:
        def builder = JsonMapper.builder().addMixIn(AnnotatedJacksonBook, AnnotatedJacksonBookMixin)
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .changeDefaultPropertyInclusion { it.withValueInclusion(JsonInclude.Include.NON_NULL) }
        def customizer = new GrailsJsonMapperCustomizer()
        customizer.customize(builder)
        def mapper = builder.build()
        customizer.forGrails(mapper)
        def book = new AnnotatedJacksonBook(secret: 'private', title: 'Grails', firstName: 'Ada',
                published: new Date(0), internal: 'hidden')

        expect:
        mapper.readValue(mapper.writerWithView(PublicView).writeValueAsString(book), Map) == [
                book_title: 'Grails', first_name: 'Ada', published: '1970', summary: 'Grails by Ada']
    }

    void 'application domain serializers keep precedence in the Grails mapper'() {
        given:
        def application = new DefaultGrailsApplication(JacksonBook)
        def source = JsonMapper.builder().addModule(new SimpleModule('application-json')
                .addSerializer(JacksonBook, ToStringSerializer.instance)).build()
        def book = new JacksonBook(title: 'Custom').tap { id = 3 }
        def mapper = new GrailsJsonMapperCustomizer(application).forGrails(source)

        expect:
        mapper.writeValueAsString(book) == source.writeValueAsString(book)
    }

    private JsonMapper domainMapper(boolean includeVersion, boolean includeClass, ProxyHandler proxyHandler = null) {
        def mappingContext = new KeyValueMappingContext('jackson')
        mappingContext.addPersistentEntities(JacksonBook, JacksonAuthor)
        def application = new DefaultGrailsApplication(JacksonBook, JacksonAuthor)
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
        application.mappingContext = mappingContext
        application.config.setAt('grails.converters.json.circular.reference.behaviour', behaviour)
        new GrailsJsonMapperCustomizer(application, new DefaultProxyHandler()).forGrails(JsonMapper.builder().build())
    }

    void 'a narrowed proxy renders the unwrapped subclass properties and class name'() {
        given:
        def target = new SpecialJacksonBook(title: 'Subclass', edition: 'second').tap { id = 5 }
        def proxy = new JacksonBookProxy(target: target)
        def mapping = new KeyValueMappingContext('polymorphic')
        mapping.addPersistentEntities(JacksonBook, SpecialJacksonBook, JacksonAuthor)
        def app = new DefaultGrailsApplication(JacksonBook, SpecialJacksonBook, JacksonAuthor)
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
class SpecialJacksonBook extends JacksonBook {
    String edition
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

class JacksonBookProxy extends JacksonBook {
    JacksonBook target
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
