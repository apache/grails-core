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

import java.util.function.Supplier

import groovy.json.JsonSlurper

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonFilter
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIdentityInfo
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonUnwrapped
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.annotation.JsonView
import com.fasterxml.jackson.annotation.ObjectIdGenerators
import spock.lang.Specification
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.BeanDescription
import tools.jackson.databind.DatabindException
import tools.jackson.databind.JavaType
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.SerializationConfig
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.ValueSerializer
import tools.jackson.databind.annotation.JsonNaming
import tools.jackson.databind.annotation.JsonSerialize
import tools.jackson.databind.exc.InvalidDefinitionException
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule
import tools.jackson.databind.ser.BeanPropertyWriter
import tools.jackson.databind.ser.ValueSerializerModifier
import tools.jackson.databind.ser.std.DelegatingSerializer
import tools.jackson.databind.ser.std.SimpleBeanPropertyFilter
import tools.jackson.databind.ser.std.SimpleFilterProvider
import tools.jackson.databind.ser.std.StdSerializer
import tools.jackson.databind.ser.std.ToStringSerializer
import tools.jackson.databind.util.StdConverter

import org.springframework.context.ApplicationContext
import org.springframework.context.support.GenericApplicationContext

import grails.converters.JSON
import grails.converters.json.NamedJsonConfigurationRegistry
import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.core.support.proxy.DefaultProxyHandler
import grails.core.support.proxy.EntityProxyHandler
import grails.core.support.proxy.ProxyHandler
import grails.persistence.Entity
import org.grails.core.lifecycle.ShutdownOperations
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.hibernate.proxy.HibernateProxy
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.proxy.EntityProxy
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.web.converters.configuration.ConvertersConfigurationHolder
import org.grails.web.converters.configuration.ConvertersConfigurationInitializer
import org.grails.web.converters.marshaller.json.DomainClassMarshaller

/**
 * A JsonMapper with a {@link DomainClassJacksonModule} renders domain class instances as {@code grails.converters.JSON}
 * renders them, and the JSON converter renders them with the same serializer.
 */
class DomainClassJacksonModuleSpec extends Specification {

    GrailsApplication grailsApplication

    void setup() {
        grailsApplication = domainApplication()
        new ConvertersConfigurationInitializer(grailsApplication: grailsApplication).initialize()
    }

    void cleanup() {
        ConvertersConfigurationHolder.clear()
    }

    void "a JsonMapper with the module renders a domain class instance as the JSON converter does"() {
        given:
        def volume = volume()

        expect: 'associated instances as references of their id, including those of a map association'
        mapper().writeValueAsString(volume) ==
                '{"id":1,"title":"Grails","shelf":{"id":3},"writers":[{"id":1},{"id":2}],"writersByName":{"a":{"id":1},"b":{"id":2}}}'
        new JSON(volume).toString() == mapper().writeValueAsString(volume)
    }

    void "a map association with a null key fails as Jackson fails to write a map with one"() {
        given:
        def volume = volume()
        volume.writersByName = [(null): volume.writers[0]]

        when:
        mapper().writeValueAsString(volume)

        then:
        DatabindException e = thrown()
        e.originalMessage == jacksonNullKeyFailure()
    }

    void "a map association is written in the order of its keys where the mapper orders map entries by them"() {
        given:
        def volume = volume()
        volume.writersByName = [b: volume.writers[1], a: volume.writers[0]]
        def ordered = JsonMapper.builder().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .addModule(new DomainClassJacksonModule(ConvertersConfigurationInitializer.jsonDomainClassRendering(
                        grailsApplication, new DefaultProxyHandler()))).build()

        expect:
        ordered.writeValueAsString(volume).endsWith('"writersByName":{"a":{"id":1},"b":{"id":2}}}')
        mapper().writeValueAsString(volume).endsWith('"writersByName":{"b":{"id":2},"a":{"id":1}}}')
    }

    void "the version and the class name are written when they are included"() {
        given:
        def mapper = mapper(new DomainClassRendering(grailsApplication, new DefaultProxyHandler(), true, true, false))
        def shelf = new Shelf(name: 'top')
        shelf.id = 3
        shelf.version = 5
        def volume = new Volume(title: 'Grails', shelf: shelf)
        volume.id = 1

        expect:
        mapper.writeValueAsString(shelf) == "{\"class\":\"${Shelf.name}\",\"id\":3,\"version\":5,\"name\":\"top\"}"
        mapper.writeValueAsString(volume).startsWith(
                "{\"class\":\"${Volume.name}\",\"id\":1,\"title\":\"Grails\",\"shelf\":{\"class\":\"${Shelf.name}\",\"id\":3}")
    }

    void "rendering deep, associated instances are written in full, and one already being written as a reference"() {
        given:
        def mapper = mapper(new DomainClassRendering(grailsApplication, new DefaultProxyHandler(), false, false, true))
        def first = new Partner(name: 'first')
        first.id = 1
        def second = new Partner(name: 'second', partner: first)
        second.id = 2
        first.partner = second

        expect:
        mapper.writeValueAsString(first) == '{"id":1,"name":"first","partner":{"id":2,"name":"second","partner":{"id":1}}}'
    }

    void "an instance reached again is written as the circular reference behaviour selects, whichever mapper writes it"() {
        given:
        grailsApplication.config.setAt('grails.converters.json.default.deep', true)
        grailsApplication.config.setAt('grails.converters.json.circular.reference.behaviour', behaviour)
        ConvertersConfigurationHolder.clear()
        new ConvertersConfigurationInitializer(grailsApplication: grailsApplication).initialize()
        def first = new Partner(name: 'first')
        first.id = 1
        def second = new Partner(name: 'second', partner: first)
        second.id = 2
        first.partner = second

        expect:
        mapper().writeValueAsString(first) == '{"id":1,"name":"first","partner":{"id":2,"name":"second","partner":' + repeated + '}}'

        and: 'so does the JSON converter, which orders the keys of a reference as Grails 8 did'
        new JSON(first).toString() == '{"id":1,"name":"first","partner":{"id":2,"name":"second","partner":' + repeated + '}}'

        where:
        behaviour     || repeated
        'DEFAULT'     || '{"_ref":"..","class":"' + Partner.name + '"}'
        'PATH'        || '{"ref":"root","class":"' + Partner.name + '"}'
        'INSERT_NULL' || 'null'
        'IGNORE'      || 'null'
    }

    void "a rendering that decides by the names properties are written with is asked about the class name, the id and the version too"() {
        given: 'a rendering that leaves out the class name and the version by the names they are written with'
        def rendering = new DomainClassRendering(grailsApplication, new DefaultProxyHandler(), true, true, false) {
            @Override
            boolean includes(Object object, String property, String written) {
                !(written in ['class', 'version'])
            }
        }
        def shelf = new Shelf(name: 'top')
        shelf.id = 3
        shelf.version = 5

        expect:
        mapper(rendering).writeValueAsString(shelf) == '{"id":3,"name":"top"}'
    }

    void "a proxy is written as the instance it proxies, and a reference to one from its identifier, without loading it"() {
        given:
        def proxyHandler = new WriterProxyHandler()
        def mapper = mapper(new DomainClassRendering(grailsApplication, proxyHandler, false, false, false))
        def writer = new Writer(name: 'a')
        writer.id = 1
        def volume = new Volume(title: 'Grails', writers: [new WriterProxy(proxyId: 9)])
        volume.id = 1

        expect:
        mapper.writeValueAsString(new WriterProxy(target: writer, proxyId: 1)) == '{"id":1,"name":"a"}'
        mapper.writeValueAsString(volume) == '{"id":1,"title":"Grails","shelf":null,"writers":[{"id":9}],"writersByName":null}'
    }

    void "the includes and excludes the JSON converter is given for a proxy apply to the instance it writes in its place, with #description"() {
        given:
        def context = new GenericApplicationContext()
        context.beanFactory.registerSingleton('proxyHandler', proxyHandler)
        context.refresh()
        ConvertersConfigurationHolder.clear()
        new ConvertersConfigurationInitializer(grailsApplication: grailsApplication, applicationContext: context).initialize()
        def writer = new Writer(name: 'a')
        writer.id = 1

        when:
        def excluding = new JSON(new WriterProxy(target: writer, proxyId: 1))
        excluding.setExcludes(['name'])
        def including = new JSON(new WriterProxy(target: writer, proxyId: 1))
        including.setIncludes(['name'])

        then:
        excluding.toString() == '{"id":1}'
        including.toString() == '{"name":"a"}'

        cleanup:
        context.close()

        where:
        description                                       | proxyHandler
        'a proxy handler that unwraps it'                 | new WriterProxyHandler()
        'a proxy handler that leaves a GORM proxy as it is' | new DefaultProxyHandler()
    }

    void "the includes and excludes the JSON converter is given for a Hibernate proxy that the proxy handler leaves as it is apply to the instance it writes in its place"() {
        given:
        def writer = new Writer(name: 'a')
        writer.id = 1
        def json = new JSON(new LazyWriterProxy(target: writer))
        json.setExcludes(['name'])

        expect:
        json.toString() == '{"id":1}'
    }

    void "the JSON converter writes the same text each time it renders, where it gave the includes of a proxy to the class it stands for"() {
        given: 'a writer, then a proxy of another, whose class has excludes'
        def other = new Writer(name: 'b')
        other.id = 2
        def writer = new Writer(name: 'a')
        writer.id = 1
        def json = new JSON([other, new WriterProxy(target: writer, proxyId: 1)])
        json.setExcludes(WriterProxy, ['name'])

        expect: 'the excludes apply from the proxy on, in each render'
        json.toString() == '[{"id":2,"name":"b"},{"id":1}]'
        json.toString() == '[{"id":2,"name":"b"},{"id":1}]'
    }

    void "the includes of a proxy written in full as an association apply to the instance it stands for, not to the instance being written around it, #description"() {
        given:
        grailsApplication.config.setAt('grails.converters.json.default.deep', true)
        ConvertersConfigurationHolder.clear()
        new ConvertersConfigurationInitializer(grailsApplication: grailsApplication).initialize()
        def second = type.getDeclaredConstructor().newInstance()
        second.name = 'b'
        second.tail = 'y'
        second.id = 2
        def first = type.getDeclaredConstructor().newInstance()
        first.name = 'a'
        first.tail = 'x'
        first.id = 1
        first.next = proxyType.getDeclaredConstructor().newInstance()
        first.next.target = second
        first.next.proxyId = 2
        def json = new JSON(first)
        json.setExcludes(proxyType, ['tail'])

        expect: 'the first chain is written whole, its tail after the proxy too'
        json.toString() == written

        where:
        description            | type       | proxyType       || written
        'without a type id'    | Chain      | ChainProxy      || '{"id":1,"name":"a","next":{"id":2,"name":"b","next":null},"tail":"x"}'
        'with the type id the association asks for' | TypedChain | TypedChainProxy ||
                '{"id":1,"name":"a","next":{"kind":"TypedChain","id":2,"name":"b","next":null},"tail":"x"}'
    }

    void "a class's own includes and excludes are the only ones that apply to it, where a proxy of it has others"() {
        given:
        def writer = new Writer(name: 'a')
        writer.id = 1
        def json = new JSON(new WriterProxy(target: writer, proxyId: 1))
        json.setExcludes(['name'])
        json.setIncludes(Writer, ['id', 'name'])

        expect:
        json.toString() == '{"id":1,"name":"a"}'
    }

    void "the JSON converter has no includes or excludes for no class, and is told of a stand-in before it renders without failing"() {
        given:
        def writer = new Writer(name: 'a')
        writer.id = 1
        def proxy = new WriterProxy(target: writer, proxyId: 1)
        def json = new JSON(proxy)
        json.setExcludes(['name'])

        when:
        json.standIn(proxy, writer)

        then:
        json.getIncludes(null) == null
        json.getExcludes(null) == null
        json.toString() == '{"id":1}'
    }

    void "the includes of a proxy apply where a DomainClassMarshaller unwraps it itself, with legacy #legacy"() {
        given: 'a marshaller for the proxy class that a DomainClassMarshaller with a proxy handler of its own writes'
        grailsApplication.config.setAt('grails.converters.json.legacy', legacy)
        ConvertersConfigurationHolder.clear()
        new ConvertersConfigurationInitializer(grailsApplication: grailsApplication).initialize()
        def marshaller = new DomainClassMarshaller(false, new WriterProxyHandler(), grailsApplication)
        JSON.registerObjectMarshaller(WriterProxy) { Object proxy, JSON converter -> marshaller.marshalObject(proxy, converter) }
        def writer = new Writer(name: 'a')
        writer.id = 1
        def json = new JSON(new WriterProxy(target: writer, proxyId: 1))
        json.setExcludes(['name'])

        expect:
        json.toString() == '{"id":1}'

        cleanup:
        grailsApplication.config.setAt('grails.converters.json.legacy', false)

        where:
        legacy << [false, true]
    }

    void "a GORM proxy is unwrapped by itself when the application's proxy handler does not unwrap it, as on MongoDB"() {
        given: 'only Hibernate registers a proxy handler; other datastores leave the default one, which unwraps nothing'
        def mapper = mapper(new DomainClassRendering(grailsApplication, new DefaultProxyHandler(), false, false, false))
        def writer = new Writer(name: 'a')
        writer.id = 1
        def volume = new Volume(title: 'Grails', writers: [new WriterProxy(proxyId: 9)])
        volume.id = 1

        expect: 'the proxy is written as its target, and a reference to one from its key, without loading it'
        mapper.writeValueAsString(new WriterProxy(target: writer, proxyId: 1)) == '{"id":1,"name":"a"}'
        mapper.writeValueAsString(volume) == '{"id":1,"title":"Grails","shelf":null,"writers":[{"id":9}],"writersByName":null}'

        and: 'the JSON converter, which renders through the same serializer, writes them alike'
        new JSON(new WriterProxy(target: writer, proxyId: 1)).toString() == '{"id":1,"name":"a"}'
        new JSON(volume).toString() == '{"id":1,"title":"Grails","shelf":null,"writers":[{"id":9}],"writersByName":null}'
    }

    void "a GORM proxy of an instance that no longer exists is written as null"() {
        given: 'a proxy that loads no target, as GORM gives one whose row was deleted'
        def rendering = new DomainClassRendering(grailsApplication, new DefaultProxyHandler(), false, false, false)
        def deep = mapper(new DomainClassRendering(grailsApplication, new DefaultProxyHandler(), false, false, true))
        def shelf = new Shelf(name: 'top')
        shelf.id = 3
        def library = new Library(name: 'main', shelf: shelf, writers: [new WriterProxy(proxyId: 9)])
        library.id = 7
        def kennel = new Kennel(resident: new DogProxy(proxyId: 2))
        kennel.id = 5
        def registry = new NamedJsonConfigurationRegistry(mapper(rendering))
        def projected = new StringWriter()

        expect:
        mapper(rendering).writeValueAsString(new WriterProxy(proxyId: 9)) == 'null'
        deep.writeValueAsString(library) == '{"id":7,"name":"main","shelf":{"id":3},"writers":[null]}'
        new JSON(new WriterProxy(proxyId: 9)).toString() == 'null'

        and: 'in an association whose type writes a type id'
        deep.writeValueAsString(kennel) == '{"id":5,"resident":null}'

        when: 'the response names the properties to write, in an application that has reported nothing yet'
        ShutdownOperations.runOperations()
        String log = stderrWhile { registry.writeValue(null, projected, new WriterProxy(proxyId: 9), ['name'], null) }

        then: 'nothing is left to project'
        projected.toString() == 'null'
        !log.contains('were not applied')
    }

    void "a proxy of an instance that no longer exists is empty, as null is, where a property leaves out empty values"() {
        given:
        def mapper = JsonMapper.builder()
                .changeDefaultPropertyInclusion { it.withValueInclusion(JsonInclude.Include.NON_EMPTY) }
                .addModule(new DomainClassJacksonModule(new DomainClassRendering(grailsApplication, new DefaultProxyHandler(),
                        false, false, false)))
                .build()
        def writer = new Writer(name: 'a')
        writer.id = 1

        expect:
        mapper.writeValueAsString(new WriterHolder(writer: new WriterProxy(proxyId: 9))) == '{}'
        mapper.writeValueAsString(new WriterHolder(writer: new WriterProxy(target: writer, proxyId: 1))) ==
                '{"writer":{"id":1,"name":"a"}}'
    }

    void "properties annotated @JsonIgnore or write-only are not written, and those @JsonIgnoreProperties names are not"() {
        given: 'GORM adds @JsonIgnoreProperties to every domain class, naming properties such as the version'
        def member = new Member(name: 'a', password: 'secret', pin: '1234', token: 'abc')
        member.id = 1
        member.version = 2

        expect:
        mapper().writeValueAsString(member) == '{"id":1,"name":"a"}'
        mapper(new DomainClassRendering(grailsApplication, new DefaultProxyHandler(), true, false, false))
                .writeValueAsString(member) == '{"id":1,"version":2,"name":"a"}'

        and: 'the JSON converter leaves them out as well'
        new JSON(member).toString() == '{"id":1,"name":"a"}'
    }

    void "the id is written as Jackson writes any property: in its views, by its own serializer, unless the class leaves it out"() {
        given:
        def viewed = new ViewedId(name: 'a')
        viewed.id = 1
        def stringId = new StringId(name: 'a')
        stringId.id = 1
        def hiddenId = new HiddenId(name: 'a')
        hiddenId.id = 1

        expect:
        mapper().writerWithView(OutsideView).writeValueAsString(viewed) == '{"name":"a"}'
        mapper().writerWithView(IdView).writeValueAsString(viewed) == '{"id":1}'
        mapper().writeValueAsString(stringId) == '{"id":"1","name":"a"}'
        mapper().writeValueAsString(hiddenId) == '{"name":"a"}'

        and: 'the JSON converter writes them alike'
        new JSON(stringId).toString() == '{"id":"1","name":"a"}'
        new JSON(hiddenId).toString() == '{"name":"a"}'
    }

    void "a property the domain class ignores is left out, whatever name the #name naming strategy gives it"() {
        given: 'properties a class names in @JsonIgnoreProperties, as GORM names the tenant id of every domain class'
        def mapper = JsonMapper.builder().propertyNamingStrategy(strategy)
                .addModule(new DomainClassJacksonModule(ConvertersConfigurationInitializer.jsonDomainClassRendering(
                        grailsApplication, new DefaultProxyHandler()))).build()
        def scoped = new TenantScoped(name: 'a', tenantId: 't1')
        scoped.id = 1
        def hidden = new HiddenId(name: 'a')
        hidden.id = 1

        expect:
        mapper.writeValueAsString(scoped) == scopedJson
        mapper.writeValueAsString(hidden) == hiddenJson

        where:
        name               | strategy                                  | scopedJson            | hiddenJson
        'SNAKE_CASE'       | PropertyNamingStrategies.SNAKE_CASE       | '{"id":1,"name":"a"}' | '{"name":"a"}'
        'UPPER_CAMEL_CASE' | PropertyNamingStrategies.UPPER_CAMEL_CASE | '{"Id":1,"Name":"a"}' | '{"Name":"a"}'
    }

    void "the version and the class name are written in the view the class names for its properties, as the id is"() {
        given:
        def mapper = mapper(new DomainClassRendering(grailsApplication, new DefaultProxyHandler(), true, true, false))
        def viewed = new ViewedClass(name: 'a')
        viewed.id = 1
        viewed.version = 2

        expect:
        mapper.writerWithView(OutsideView).writeValueAsString(viewed) ==
                "{\"class\":\"${ViewedClass.name}\",\"id\":1,\"version\":2,\"name\":\"a\"}"
        mapper.writerWithView(IdView).writeValueAsString(viewed) == '{}'
    }

    void "a mix-in applies to the id"() {
        given:
        def writer = new Writer(name: 'a')
        writer.id = 1
        def rendering = ConvertersConfigurationInitializer.jsonDomainClassRendering(grailsApplication, new DefaultProxyHandler())

        expect:
        JsonMapper.builder().addModule(new DomainClassJacksonModule(rendering)).addMixIn(Writer, IgnoredIdMixin).build()
                .writeValueAsString(writer) == '{"name":"a"}'
        JsonMapper.builder().addModule(new DomainClassJacksonModule(rendering)).addMixIn(Writer, IgnoredIdPropertiesMixin).build()
                .writeValueAsString(writer) == '{"name":"a"}'
    }

    void "a mix-in applies to the version, which GORM leaves out of every domain class for the rendering to decide"() {
        given:
        def versioned = new GormVersioned(name: 'top')
        versioned.id = 3
        versioned.version = 5
        def rendering = new DomainClassRendering(grailsApplication, new DefaultProxyHandler(), true, false, false)

        expect:
        JsonMapper.builder().addModule(new DomainClassJacksonModule(rendering)).build()
                .writeValueAsString(versioned) == '{"id":3,"version":5,"name":"top"}'
        JsonMapper.builder().addModule(new DomainClassJacksonModule(rendering)).addMixIn(GormVersioned, IgnoredVersionMixin)
                .build().writeValueAsString(versioned) == '{"id":3,"name":"top"}'
        JsonMapper.builder().addModule(new DomainClassJacksonModule(rendering))
                .addMixIn(GormVersioned, IgnoredVersionPropertiesMixin).build().writeValueAsString(versioned) == '{"id":3,"name":"top"}'

        and: 'a mix-in of an interface of the class, or of every class, as Jackson applies them'
        JsonMapper.builder().addModule(new DomainClassJacksonModule(rendering)).addMixIn(Catalogued, IgnoredVersionMixin)
                .build().writeValueAsString(versioned) == '{"id":3,"name":"top"}'
        JsonMapper.builder().addModule(new DomainClassJacksonModule(rendering)).addMixIn(Object, IgnoredVersionPropertiesMixin)
                .build().writeValueAsString(versioned) == '{"id":3,"name":"top"}'
    }

    void "rendering deep, the annotations of an association apply to the instances it writes"() {
        given:
        def shelf = new Shelf(name: 'top')
        shelf.id = 3
        def first = new Writer(name: 'a')
        first.id = 1
        def library = new Library(name: 'main', shelf: shelf, writers: [first])
        library.id = 7

        def proxied = new Library(name: 'main', shelf: shelf, writers: [new WriterProxy(target: first, proxyId: 1)])
        proxied.id = 8
        def deep = mapper(new DomainClassRendering(grailsApplication, new DefaultProxyHandler(), false, false, true))

        expect:
        deep.writeValueAsString(library) == '{"id":7,"name":"main","shelf":{"id":3},"writers":[{"id":1}]}'

        and: 'to a proxy of an instance in the association as well'
        deep.writeValueAsString(proxied) == '{"id":8,"name":"main","shelf":{"id":3},"writers":[{"id":1}]}'

        and: 'and when the JSON converter renders deep'
        JSON.use('deep') { new JSON(library).toString() } == '{"id":7,"name":"main","shelf":{"id":3},"writers":[{"id":1}]}'
    }

    void "a domain class with @JsonIdentityInfo writes a repeated instance as Jackson's object id"() {
        given:
        def first = new Identified(name: 'first')
        first.id = 1
        def second = new Identified(name: 'second', partner: first)
        second.id = 2
        first.partner = second

        expect:
        mapper(new DomainClassRendering(grailsApplication, new DefaultProxyHandler(), false, false, true))
                .writeValueAsString(first) == '{"@id":1,"id":1,"name":"first","partner":{"@id":2,"id":2,"name":"second","partner":1}}'

        and: 'so does the JSON converter'
        JSON.use('deep') { new JSON(first).toString() } ==
                '{"@id":1,"id":1,"name":"first","partner":{"@id":2,"id":2,"name":"second","partner":1}}'
    }

    void "@JsonPropertyOrder orders the persistent properties, after the id, and @JsonFilter filters them"() {
        given:
        def ordered = new Ordered(alpha: 'a', beta: 'b', gamma: 'c')
        ordered.id = 1
        def filtered = new Filtered(name: 'a', secret: 's')
        filtered.id = 2
        def filters = new SimpleFilterProvider().addFilter('domain', SimpleBeanPropertyFilter.serializeAllExcept('secret'))

        expect:
        mapper().writeValueAsString(ordered) == '{"id":1,"gamma":"c","alpha":"a","beta":"b"}'
        mapper().writer(filters).writeValueAsString(filtered) == '{"id":2,"name":"a"}'
    }

    void "a domain class with its own @JsonSerialize serializer keeps it"() {
        given:
        def label = new Label(text: 'x')
        label.id = 1

        expect:
        mapper().writeValueAsString(label) == '"label:x"'
    }

    void "a domain class with @JsonTypeInfo is written with its type id, itself and through a property of its superclass"() {
        given:
        def dog = new Dog(name: 'Rex')
        dog.id = 2

        expect:
        mapper().writeValueAsString(dog) == '{"@type":"Dog","id":2,"name":"Rex"}'
        mapper().writeValueAsString(new Pet(animal: dog)) == '{"animal":{"@type":"Dog","id":2,"name":"Rex"}}'
    }

    void "rendering deep, the reference to an instance already being written is offered to the rendering first"() {
        given:
        def first = new Partner(name: 'first')
        first.id = 1
        def second = new Partner(name: 'second', partner: first)
        second.id = 2
        first.partner = second
        def offered = []
        def rendering = new DomainClassRendering(grailsApplication, new DefaultProxyHandler(), false, false, true) {
            @Override
            boolean writeReference(Object reference, PersistentProperty idProperty, PersistentEntity entity) {
                offered << reference.id
                false
            }
        }

        expect:
        mapper(rendering).writeValueAsString(first) == '{"id":1,"name":"first","partner":{"id":2,"name":"second","partner":{"id":1}}}'
        offered == [1L]
    }

    void "a #description with a type id in a @JsonUnwrapped property fails, as Jackson's own unwrapping does"() {
        given:
        def dog = new Dog(name: 'Rex')
        dog.id = 2
        def lenient = JsonMapper.builder()
                .disable(SerializationFeature.FAIL_ON_UNWRAPPED_TYPE_IDENTIFIERS)
                .addModule(new DomainClassJacksonModule(ConvertersConfigurationInitializer.jsonDomainClassRendering(grailsApplication, new DefaultProxyHandler())))
                .build()

        when:
        mapper().writeValueAsString(new UnwrappedDog(dog: value(dog), note: 'n'))

        then:
        thrown(InvalidDefinitionException)

        expect: 'without the type id, when the feature is disabled'
        lenient.writeValueAsString(new UnwrappedDog(dog: value(dog), note: 'n')) == '{"id":2,"name":"Rex","note":"n"}'

        where:
        description       | value
        'domain instance' | { Dog instance -> instance }
        'proxy'           | { Dog instance -> new DogProxy(target: instance, proxyId: 2) }
    }

    void "references are named as the instance itself, by the mapper's naming strategy"() {
        given:
        def mapper = { boolean deep ->
            JsonMapper.builder()
                    .propertyNamingStrategy(PropertyNamingStrategies.UPPER_CAMEL_CASE)
                    .addModule(new DomainClassJacksonModule(new DomainClassRendering(grailsApplication, new DefaultProxyHandler(), false, false, deep)))
                    .build()
        }
        def first = new Partner(name: 'first')
        first.id = 1
        def second = new Partner(name: 'second', partner: first)
        second.id = 2
        first.partner = second

        expect: 'through an association, and rendering deep, through a cycle'
        mapper(false).writeValueAsString(volume()) ==
                '{"Id":1,"Title":"Grails","Shelf":{"Id":3},"Writers":[{"Id":1},{"Id":2}],"WritersByName":{"a":{"Id":1},"b":{"Id":2}}}'
        mapper(true).writeValueAsString(first) == '{"Id":1,"Name":"first","Partner":{"Id":2,"Name":"second","Partner":{"Id":1}}}'
    }

    void "a module that gives the serializer of a domain class a filter id, or a serializer of its own, keeps it tracking circular references"() {
        given: 'a module registered before the domain class module, so that its modifier is given the tracking serializer'
        def first = new Partner(name: 'first')
        first.id = 1
        def second = new Partner(name: 'second', partner: first)
        second.id = 2
        first.partner = second
        def deep = new DomainClassRendering(grailsApplication, new DefaultProxyHandler(), false, false, true)
        def mapper = { Closure<ValueSerializer<?>> change ->
            def application = new SimpleModule('application').setSerializerModifier(new ValueSerializerModifier() {
                @Override
                ValueSerializer<?> modifySerializer(SerializationConfig config, BeanDescription.Supplier description,
                        ValueSerializer<?> serializer) {
                    description.get().beanClass == Partner ? change(serializer) : serializer
                }
            })
            JsonMapper.builder().addModule(application).addModule(new DomainClassJacksonModule(deep)).build()
        }
        def filters = new SimpleFilterProvider().addFilter('partners', SimpleBeanPropertyFilter.serializeAllExcept('name'))

        expect: 'a filter id applies, and the instance reached again is a reference to it'
        mapper { it.withFilterId('partners') }.writer(filters).writeValueAsString(first) ==
                '{"id":1,"partner":{"id":2,"partner":{"id":1}}}'

        and: 'so does a serializer of its own'
        mapper { it.replaceDelegatee(new PartnerLabelSerializer()) }.writeValueAsString(first) ==
                '{"label":"first","partner":{"label":"second","partner":{"id":1}}}'
    }

    void "the shape a property's @JsonFormat asks for applies to a domain instance, which is still tracked"() {
        given:
        def first = new Partner(name: 'first')
        first.id = 1
        def second = new Partner(name: 'second', partner: first)
        second.id = 2
        first.partner = second
        def deep = mapper(new DomainClassRendering(grailsApplication, new DefaultProxyHandler(), false, false, true))

        expect: 'the instance is an array of its properties, and the instance reached again is a reference to it'
        deep.writeValueAsString(new ShapedPartnerHolder(partner: first)) ==
                '{"partner":[1,"first",{"id":2,"name":"second","partner":{"id":1}}]}'
    }

    void "an instance written as an array has a position for each property its rendering writes, whichever writes it"() {
        given:
        def positioned = new Positioned(name: 'first')
        positioned.id = 1
        positioned.version = 5

        expect: 'the id and the name, without positions for a class name and a version the rendering does not write'
        mapper().writeValueAsString(positioned) == '[1,"first"]'
        new JSON(positioned).toString() == '[1,"first"]'

        when: 'the rendering writes the version'
        JSON.registerObjectMarshaller(new DomainClassMarshaller(true, grailsApplication))

        then:
        mapper(new DomainClassRendering(grailsApplication, new DefaultProxyHandler(), true, false, false))
                .writeValueAsString(positioned) == '[1,5,"first"]'
        new JSON(positioned).toString() == '[1,5,"first"]'

        when: 'the converter excludes the version its rendering writes'
        def json = new JSON(positioned)
        json.setExcludes(Positioned, ['version'])

        then: 'its position is kept, as Jackson keeps that of a property it leaves out'
        json.toString() == '[1,null,"first"]'
    }

    void "only the persistent properties of a domain class are written, without those of a @JsonAnyGetter, as in Grails 8"() {
        given: 'a domain class with a @JsonAnyGetter'
        def extended = new Extended(name: 'a')
        extended.id = 1

        expect:
        mapper().writeValueAsString(extended) == '{"id":1,"name":"a"}'
        new JSON(extended).toString() == '{"id":1,"name":"a"}'

        and: 'as for a bean, unless it is a domain class'
        JsonMapper.builder().build().writeValueAsString(new ExtendedBean(name: 'a')) == '{"name":"a","extra":"value"}'
    }

    void "the entries of a persistent property annotated @JsonAnyGetter are written in its place, whatever name a naming strategy gives it"() {
        given:
        def attributed = new Attributed(name: 'a', customAttributes: [colour: 'red', size: 'L'])
        attributed.id = 1
        def snakeCase = JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .addModule(new DomainClassJacksonModule(ConvertersConfigurationInitializer.jsonDomainClassRendering(
                        grailsApplication, new DefaultProxyHandler())))
                .build()

        expect: 'as Jackson writes those of a bean'
        mapper().writeValueAsString(attributed) == '{"id":1,"name":"a","colour":"red","size":"L","note":"n"}'
        snakeCase.writeValueAsString(attributed) == '{"id":1,"name":"a","colour":"red","size":"L","note":"n"}'
        new JSON(attributed).toString() == '{"id":1,"name":"a","colour":"red","size":"L","note":"n"}'

        and: 'none, when there are none'
        mapper().writeValueAsString(new Attributed(name: 'b', note: 'n')) == '{"name":"b","note":"n"}'

        when: 'the property is excluded'
        def json = new JSON(attributed)
        json.setExcludes(Attributed, ['customAttributes'])

        then:
        json.toString() == '{"id":1,"name":"a","note":"n"}'
    }

    void "a persistent property annotated @JsonAnyGetter is left out where the class ignores it, and written as a property where it is disabled"() {
        given:
        def ignored = new IgnoredAttributes(name: 'a', extras: [k: 'v'])
        ignored.id = 1
        def disabled = new DisabledAttributes(name: 'a', extras: [k: 'v'])
        disabled.id = 2

        expect:
        mapper().writeValueAsString(ignored) == '{"id":1,"name":"a"}'
        mapper().writeValueAsString(disabled) == '{"id":2,"name":"a","extras":{"k":"v"}}'
    }

    void "a @JsonFilter filters the entries of a persistent property annotated @JsonAnyGetter one by one, as for a bean"() {
        given:
        def filtered = new FilteredAttributes(name: 'a', attributes: [colour: 'red', size: 'L'])
        filtered.id = 1
        def filters = new SimpleFilterProvider().addFilter('attributes',
                SimpleBeanPropertyFilter.filterOutAllExcept('id', 'name', 'colour'))

        expect:
        JsonMapper.builder().build().writer(filters).writeValueAsString(
                new FilteredAttributesBean(name: 'a', attributes: [colour: 'red', size: 'L'])) == '{"name":"a","colour":"red"}'
        mapper().writer(filters).writeValueAsString(filtered) == '{"id":1,"name":"a","colour":"red"}'
    }

    void "the entries of a persistent property annotated @JsonAnyGetter are written in the views the property is written in"() {
        given:
        def attributed = new Attributed(name: 'a', customAttributes: [colour: 'red'])
        attributed.id = 1
        def viewed = new ViewedAttributes(name: 'b', attributes: [size: 'L'])
        viewed.id = 2
        def byDefault = JsonMapper.builder().enable(MapperFeature.DEFAULT_VIEW_INCLUSION)
                .addModule(new DomainClassJacksonModule(ConvertersConfigurationInitializer.jsonDomainClassRendering(
                        grailsApplication, new DefaultProxyHandler())))
                .build()

        expect: 'in the views it names'
        mapper().writerWithView(Everyone).writeValueAsString(attributed) == '{"colour":"red"}'
        mapper().writerWithView(OutsideView).writeValueAsString(attributed) == '{}'

        and: 'in every view, when it names none and the mapper writes such properties in every view'
        byDefault.writerWithView(OutsideView).writeValueAsString(viewed) == '{"id":2,"size":"L"}'
        mapper().writerWithView(OutsideView).writeValueAsString(viewed) == '{}'
    }

    void "an associated instance whose class has a serializer of its own is written with it, rather than as a reference"() {
        given: 'associations to a class with a @JsonValue, to one with a @JsonSerialize serializer, to one with a ' +
                '@JsonSerialize converter, and to one without'
        def shelf = new Shelf(name: 'top')
        shelf.id = 3
        def summarized = new Summarized(name: 's')
        summarized.id = 4
        def coded = new Coded(code: new Code(code: 'X1'), label: new Label(text: 'x'), summarized: summarized, shelf: shelf)
        coded.id = 1

        expect: 'as the classes are written anywhere else'
        mapper().writeValueAsString(summarized) == '{"summary":"summary of s"}'
        mapper().writeValueAsString(coded) ==
                '{"id":1,"code":"X1","label":"label:x","summarized":{"summary":"summary of s"},"shelf":{"id":3}}'
        new JSON(coded).toString() == mapper().writeValueAsString(coded)

        when: 'the association is a proxy'
        coded.code = new CodeProxy(target: new Code(code: 'X2'), proxyId: 2)

        then: 'the instance it stands for'
        mapper().writeValueAsString(coded) ==
                '{"id":1,"code":"X2","label":"label:x","summarized":{"summary":"summary of s"},"shelf":{"id":3}}'

        when: 'the association is a proxy of an instance that no longer exists'
        coded.code = new CodeProxy(target: null, proxyId: 2)

        then:
        mapper().writeValueAsString(coded) ==
                '{"id":1,"code":null,"label":"label:x","summarized":{"summary":"summary of s"},"shelf":{"id":3}}'
    }

    void "an associated instance written with a serializer of its own is written as Jackson writes the value of the property"() {
        given: 'associations whose properties are annotated for the shape and the inclusion of what their classes write'
        def rated = new Rated(rating: rating(5), ratings: [rating(6), rating(7)], code: new Code(code: ''))
        rated.id = 1
        def bean = new RatedBean(rating: rated.rating, ratings: rated.ratings, code: rated.code)

        expect: 'as for the properties of any bean'
        JsonMapper.builder().build().writeValueAsString(bean) == '{"rating":"5","ratings":["6","7"]}'
        mapper().writeValueAsString(rated) == '{"id":1,"rating":"5","ratings":["6","7"]}'
        new JSON(rated).toString() == mapper().writeValueAsString(rated)

        when: 'what its serializer writes is not empty'
        rated.code = new Code(code: 'X1')

        then:
        mapper().writeValueAsString(rated) == '{"id":1,"rating":"5","ratings":["6","7"],"code":"X1"}'

        when: 'it is a proxy of an instance that no longer exists'
        rated.code = new CodeProxy(target: null, proxyId: 3)

        then:
        mapper().writeValueAsString(rated) == '{"id":1,"rating":"5","ratings":["6","7"]}'
    }

    void "an associated instance written in full is left out where its property leaves out an empty value, as a reference is"() {
        given:
        def rated = new Rated(rating: rating(5), ratings: [rating(6), rating(7)], code: new Code(code: ''))
        rated.id = 1
        def coded = new Coded(code: new CodeProxy(target: null, proxyId: 2), label: new Label(text: 'x'))
        coded.id = 1
        def deep = new DomainClassRendering(grailsApplication, new DefaultProxyHandler(), false, false, true)
        def nonEmpty = JsonMapper.builder()
                .changeDefaultPropertyInclusion { it.withValueInclusion(JsonInclude.Include.NON_EMPTY) }
                .addModule(new DomainClassJacksonModule(deep)).build()

        expect: 'an instance its serializer finds empty'
        mapper(deep).writeValueAsString(rated) == '{"id":1,"rating":"5","ratings":["6","7"]}'

        and: 'a proxy of an instance that no longer exists'
        nonEmpty.writeValueAsString(coded) == '{"id":1,"label":"label:x"}'
    }

    void "an associated instance written with a serializer of its own has the type id its declared type asks for, as a reference has none"() {
        given:
        def cat = new Cat(name: 'Tom')
        cat.id = 4
        def kennel = new Kennel(resident: cat)
        kennel.id = 6
        def shelter = new Shelter(residents: [cat])
        shelter.id = 7

        expect: 'as Jackson writes it for the property of any bean'
        mapper().writeValueAsString(new Pet(animal: cat)) == '{"animal":["Cat","Tom"]}'
        mapper().writeValueAsString(kennel) == '{"id":6,"resident":["Cat","Tom"]}'
        mapper().writeValueAsString(shelter) == '{"id":7,"residents":[["Cat","Tom"]]}'
        mapper().writeValueAsString(new Zoo(byName: [tom: cat]).tap { id = 8 }) == '{"id":8,"byName":{"tom":["Cat","Tom"]}}'

        and: 'by the JSON converter'
        new JSON(kennel).toString() == mapper().writeValueAsString(kennel)
        new JSON(shelter).toString() == mapper().writeValueAsString(shelter)
    }

    void "the serializer an associated instance with a serializer of its own is written with is found once for each class and property"() {
        given:
        def tally = new Tally(single: counted('a'), counts: [counted('b'), counted('c'), counted('d')])
        tally.id = 1
        def mapper = mapper()
        CountedSerializer.CONTEXTUALIZED.set(0)

        when:
        def first = mapper.writeValueAsString(tally)
        def second = mapper.writeValueAsString(tally)

        then:
        first == '{"id":1,"single":"a","counts":["b","c","d"]}'
        second == first
        CountedSerializer.CONTEXTUALIZED.get() == 2
    }

    private static Counted counted(String name) {
        def counted = new Counted(name: name)
        counted.id = name.charAt(0) as long
        counted
    }

    void "the instances of a to-many association written with a serializer of their own have the type ids the property asks for"() {
        given: 'an association that names the type ids of its elements, of a class that names none'
        def parrot = new Parrot(name: 'Polly')
        parrot.id = 3
        def aviary = new Aviary(birds: [parrot])
        aviary.id = 1

        expect: 'as Jackson writes them for the property of any bean'
        mapper().writeValueAsString(new AviaryBean(birds: [parrot])) == '{"birds":[["Parrot","Polly"]]}'
        mapper().writeValueAsString(aviary) == '{"id":1,"birds":[["Parrot","Polly"]]}'
    }

    void "the instances of a to-many association written in full have the type ids the property asks for"() {
        given: 'an association that names the type ids of its elements, of a class that names none'
        def pet = new KennelPet(name: 'Rex')
        pet.id = 2
        def kennel = new TypedKennel(pets: [pet])
        kennel.id = 1

        expect: 'as Jackson writes them for the property of any bean'
        mapper(new DomainClassRendering(grailsApplication, new DefaultProxyHandler(), false, false, true))
                .writeValueAsString(kennel) == '{"id":1,"pets":[{"kind":"KennelPet","id":2,"name":"Rex"}]}'
    }

    void "a rendering writes the references itself when it overrides writeReference, unless it says otherwise"() {
        expect:
        !new DomainClassRendering(grailsApplication, new DefaultProxyHandler(), false, false, false).writesReferences()
        new WritingRendering(grailsApplication).writesReferences()
    }

    void "a DomainClassMarshaller that writes references itself writes the reference to an instance whose class has a serializer of its own"() {
        given:
        JSON.registerObjectMarshaller(new DomainClassMarshaller(false, grailsApplication) {
            @Override
            protected void asShortObject(Object refObj, JSON json, PersistentProperty idProperty, PersistentEntity referencedDomainClass) {
                json.writer.value("${referencedDomainClass.javaClass.simpleName}:${refObj.id}".toString())
            }
        })
        def code = new Code(code: '')
        code.id = 2
        def rated = new Rated(rating: rating(5), code: code)
        rated.id = 1

        expect: 'which is never empty'
        new JSON(rated).toString() == '{"id":1,"rating":"Rating:5","ratings":null,"code":"Code:2"}'
    }

    void "the instances of a to-many association whose class a module has a serializer for are written with it"() {
        given: 'a module with a serializer of writers, which take precedence over the bean serializer of the domain class'
        def mapper = JsonMapper.builder()
                .addModule(new SimpleModule().addSerializer(Writer, new WriterNameSerializer()))
                .addModule(new DomainClassJacksonModule(ConvertersConfigurationInitializer.jsonDomainClassRendering(
                        grailsApplication, new DefaultProxyHandler())))
                .build()

        expect:
        mapper.writeValueAsString(volume()) ==
                '{"id":1,"title":"Grails","shelf":{"id":3},"writers":["a","b"],"writersByName":{"a":"a","b":"b"}}'
    }

    void "a domain class with a @JsonValue is written by Jackson"() {
        given:
        def code = new Code(code: 'X1')
        code.id = 1

        expect:
        mapper().writeValueAsString(code) == '"X1"'
    }

    void "a class name or a version the rendering does not write does not conflict with a property of a bean the instance is unwrapped into"() {
        given: 'a bean with a version of its own, into which a versioned domain class instance is unwrapped'
        def writer = new Writer(name: 'a')
        writer.id = 1
        def envelope = new VersionedEnvelope(version: 'v1', writer: writer)

        expect: "the mapper's rendering writes neither, as Spring Boot's mapper and a response's have it by default"
        mapper().writeValueAsString(envelope) == '{"version":"v1","id":1,"name":"a"}'

        and: 'the JSON converter, whose renderings may write either for each instance it writes'
        new JSON(envelope).toString() == '{"version":"v1","id":1,"name":"a"}'

        when: 'the rendering writes the version'
        mapper(new DomainClassRendering(grailsApplication, new DefaultProxyHandler(), true, false, false))
                .writeValueAsString(envelope)

        then: 'the names conflict, as Jackson reports it for any bean'
        thrown(InvalidDefinitionException)
    }

    void "a property a domain class unwraps with the name of one of its own is rejected by the mapper, as for any bean, and written twice by the JSON converter"() {
        given: 'a domain class with an embedded component it unwraps, which has a property named as one of its own'
        def located = new Located(name: 'library', address: new Address(name: 'main', city: 'Paris'))
        located.id = 1

        when:
        mapper().writeValueAsString(located)

        then: "the names conflict, as Jackson reports it for any bean, since the mapper's rendering decides what may be written"
        thrown(InvalidDefinitionException)

        when: 'the JSON converter, whose renderings may write the class name and the version of each instance it writes'
        String json = new JSON(located).toString()

        then: 'it does not check the names, and writes both'
        json == '{"id":1,"name":"library","city":"Paris","name":"main"}'
    }

    void "an associated instance of a domain class the mapper rejects is written as a reference, which needs none of its properties"() {
        given: 'an association to a domain class that unwraps a property with the name of one of its own'
        def located = new Located(name: 'library', address: new Address(name: 'main', city: 'Paris'))
        located.id = 1
        def branch = new Branch(name: 'east', location: located)
        branch.id = 2
        def mapper = mapper()

        expect: 'for every write, though Jackson tries to build the serializer it rejects again for each'
        mapper.writeValueAsString(branch) == '{"id":2,"name":"east","location":{"id":1}}'
        mapper.writeValueAsString(branch) == '{"id":2,"name":"east","location":{"id":1}}'
        new JSON(branch).toString() == '{"id":2,"name":"east","location":{"id":1}}'
    }

    void "Jackson is asked once for the serializer of an associated domain class it cannot build"() {
        given: 'a module that counts the serializers built of the class'
        def located = new Located(name: 'library', address: new Address(name: 'main', city: 'Paris'))
        located.id = 1
        def branch = new Branch(name: 'east', location: located)
        branch.id = 2
        def built = new java.util.concurrent.atomic.AtomicInteger()
        def counting = new SimpleModule('counting-locations').setSerializerModifier(new ValueSerializerModifier() {
            @Override
            ValueSerializer<?> modifySerializer(SerializationConfig config, BeanDescription.Supplier beanDescription,
                                                ValueSerializer<?> serializer) {
                if (beanDescription.beanClass == Located) {
                    built.incrementAndGet()
                }
                serializer
            }
        })
        def mapper = JsonMapper.builder().addModule(new DomainClassJacksonModule(
                ConvertersConfigurationInitializer.jsonDomainClassRendering(grailsApplication, new DefaultProxyHandler())))
                .addModule(counting).build()

        when:
        3.times { mapper.writeValueAsString(branch) }

        then: 'though Jackson keeps no serializer it could not build'
        built.get() == 1
    }

    void "an associated instance of a domain class whose serializer fails to resolve is written as a reference too"() {
        given: 'a module that fails to build the serializer of the enum of a property, which Jackson reports as a mapping problem'
        def badge = new Badge(label: 'gold', colour: BadgeColour.GOLD)
        badge.id = 2
        def badged = new Badged(badge: badge)
        badged.id = 1
        def failing = new SimpleModule('failing-colours').setSerializerModifier(new ValueSerializerModifier() {
            @Override
            ValueSerializer<?> modifyEnumSerializer(SerializationConfig config, JavaType valueType,
                                                    BeanDescription.Supplier beanDescription, ValueSerializer<?> serializer) {
                if (valueType.rawClass == BadgeColour) {
                    throw new IllegalArgumentException('no serializer for colours')
                }
                serializer
            }
        })
        def mapper = JsonMapper.builder().addModule(new DomainClassJacksonModule(
                ConvertersConfigurationInitializer.jsonDomainClassRendering(grailsApplication, new DefaultProxyHandler())))
                .addModule(failing).build()

        when:
        mapper.writeValueAsString(badge)

        then: 'the class cannot be written'
        DatabindException e = thrown()
        !(e instanceof InvalidDefinitionException)

        expect: 'a reference to an instance of it can'
        mapper.writeValueAsString(badged) == '{"id":1,"badge":{"id":2}}'
    }

    void "an association after a @JsonUnwrapped property is written with the rendering of the instance that has it"() {
        given: 'a DomainClassMarshaller that writes the ids with a prefix'
        JSON.registerObjectMarshaller(new DomainClassMarshaller(false, grailsApplication) {
            @Override
            protected Object extractValue(Object domainObject, PersistentProperty property) {
                def value = super.extractValue(domainObject, property)
                property.name == 'id' ? "id-${value}".toString() : value
            }
        })
        def shelf = new Shelf(name: 'top')
        shelf.id = 3
        def filed = new Filed(title: 'Grails', label: new FileLabel(code: 'g1'), shelf: shelf)
        filed.id = 1

        expect: 'the reference after the unwrapped label, as the reference of an instance without one'
        new JSON(filed).toString() == '{"id":"id-1","title":"Grails","code":"g1","shelf":{"id":"id-3"}}'
    }

    void "a domain instance in a @JsonUnwrapped property is written into the enclosing object"() {
        given:
        def writer = new Writer(name: 'a')
        writer.id = 1

        expect: 'in the place of the property, in the order the mapper writes the properties of the enclosing bean'
        mapper().writeValueAsString(new Unwrapped(writer: writer, prefixed: writer, note: 'n')) ==
                '{"note":"n","w_id":1,"w_name":"a","id":1,"name":"a"}'

        and: 'so does the JSON converter'
        new JSON(new Unwrapped(writer: writer, prefixed: writer, note: 'n')).toString() ==
                '{"note":"n","w_id":1,"w_name":"a","id":1,"name":"a"}'
    }

    void "what a property is annotated with applies to the instance a Hibernate proxy in it stands for, whichever part of the JSON converter unwraps it, with the #behaviour circular reference behaviour"() {
        given: 'a proxy with a lazy initializer and a proxy handler that unwraps it, as Hibernate has'
        grailsApplication.config.setAt('grails.converters.json.circular.reference.behaviour', behaviour)
        ConvertersConfigurationHolder.clear()
        def initializer = new ConvertersConfigurationInitializer() {
            @Override
            protected ProxyHandler getProxyHandler() {
                new LazyProxyHandler()
            }
        }
        initializer.grailsApplication = grailsApplication
        initializer.initialize()
        def proxy = { long id ->
            def writer = new Writer(name: 'a')
            writer.id = id
            new LazyWriterProxy(target: writer)
        }
        def holder = new IgnoringWriterHolder(writer: proxy(1), writers: [proxy(2)])
        def dog = new Dog(name: 'Rex')
        dog.id = 3
        def pet = new IgnoringPet(animal: new LazyAnimalProxy(target: dog))
        def mapper = mapper(new DomainClassRendering(grailsApplication, new LazyProxyHandler(), false, false, false))

        expect:
        mapper.writeValueAsString(holder) == '{"writer":{"name":"a"},"writers":[{"name":"a"}]}'
        new JSON(holder).toString() == '{"writer":{"name":"a"},"writers":[{"name":"a"}]}'

        and: 'a proxy narrowed to a subclass, with the type id of the subclass'
        mapper.writeValueAsString(pet) == '{"animal":{"@type":"Dog","name":"Rex"}}'
        new JSON(pet).toString() == '{"animal":{"@type":"Dog","name":"Rex"}}'

        when: 'the converter has a proxy handler that does not unwrap it, so that its proxy marshaller does'
        ConvertersConfigurationHolder.clear()
        new ConvertersConfigurationInitializer(grailsApplication: grailsApplication).initialize()

        then:
        new JSON(holder).toString() == '{"writer":{"name":"a"},"writers":[{"name":"a"}]}'
        new JSON(pet).toString() == '{"animal":{"@type":"Dog","name":"Rex"}}'

        where:
        behaviour << ['DEFAULT', 'PATH']
    }

    void "a module's wrapper around the serializer of a domain class writes the instance a proxy stands for, in the JSON converter"() {
        given: "the application's JsonMapper, whose module wraps the serializer of Writer, and Hibernate's proxy handler"
        def application = new SimpleModule('application').setSerializerModifier(new ValueSerializerModifier() {
            @Override
            ValueSerializer<?> modifySerializer(SerializationConfig config, BeanDescription.Supplier description,
                    ValueSerializer<?> serializer) {
                description.get().beanClass == Writer ? new ApplicationWrapper(serializer) : serializer
            }
        })
        def context = new GenericApplicationContext()
        context.registerBean(JsonMapper, { JsonMapper.builder().addModule(application).build() } as Supplier<JsonMapper>)
        context.registerBean(ProxyHandler, { new LazyProxyHandler() } as Supplier<ProxyHandler>)
        context.refresh()
        ConvertersConfigurationHolder.clear()
        new ConvertersConfigurationInitializer(grailsApplication: grailsApplication, applicationContext: context).initialize()
        def writer = new Writer(name: 'a')
        writer.id = 1

        expect:
        new JSON(new IgnoringWriterHolder(writer: new LazyWriterProxy(target: writer), writers: [])).toString() ==
                '{"writer":{"name":"a"},"writers":[]}'

        cleanup:
        context.close()
    }

    void "a proxy in a @JsonUnwrapped property is written into the enclosing object, as the instance it proxies is"() {
        given:
        def writer = new Writer(name: 'a')
        writer.id = 1
        def proxy = { -> new WriterProxy(target: writer, proxyId: 1) }

        expect:
        mapper().writeValueAsString(new Unwrapped(writer: proxy(), prefixed: proxy(), note: 'n')) ==
                '{"note":"n","w_id":1,"w_name":"a","id":1,"name":"a"}'

        and: 'a proxy of an instance that no longer exists is left out, as a null value is'
        mapper().writeValueAsString(new Unwrapped(writer: new WriterProxy(proxyId: 9), note: 'n')) == '{"note":"n"}'

        and: 'so does the JSON converter'
        new JSON(new Unwrapped(writer: proxy(), prefixed: proxy(), note: 'n')).toString() ==
                '{"note":"n","w_id":1,"w_name":"a","id":1,"name":"a"}'
    }

    void "a proxy in a @JsonUnwrapped property of an instance that cannot be unwrapped is written under the name of the property"() {
        given: 'a domain class with a @JsonValue, which Jackson does not unwrap'
        def code = new Code(code: 'X1')
        code.id = 1

        expect:
        mapper().writeValueAsString(new UnwrappedCode(code: code, note: 'n')) == '{"code":"X1","note":"n"}'
        mapper().writeValueAsString(new UnwrappedCode(code: new CodeProxy(target: code, proxyId: 1), note: 'n')) ==
                '{"code":"X1","note":"n"}'

        and: 'so does the JSON converter'
        new JSON(new UnwrappedCode(code: new CodeProxy(target: code, proxyId: 1), note: 'n')).toString() ==
                '{"code":"X1","note":"n"}'
    }

    void "@JsonInclude, @JsonView and @JsonFormat on a property apply, as they do to any bean"() {
        given:
        def annotated = new Annotated(name: 'a', nickname: '', hidden: 'h', born: new Date(0))
        annotated.id = 1
        def mapper = mapper()

        expect: 'an empty nickname is left out, and a date is formatted as the property asks'
        mapper.writeValueAsString(annotated) == '{"id":1,"name":"a","hidden":"h","born":"1970"}'

        and: 'a view writes the properties in it, as it does for any bean when default view inclusion is off'
        mapper.writerWithView(Everyone).writeValueAsString(annotated) == '{"hidden":"h"}'

        and: 'the JSON converter applies them as well'
        new JSON(annotated).toString() == '{"id":1,"name":"a","hidden":"h","born":"1970"}'
    }

    void "the mapper's naming strategy, @JsonNaming and @JsonProperty name the properties the mapper writes"() {
        given:
        def snakeCase = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .addModule(new DomainClassJacksonModule(ConvertersConfigurationInitializer.jsonDomainClassRendering(grailsApplication, new DefaultProxyHandler())))
                .build()
        def shelf = new Shelf(name: 'top')
        shelf.id = 3
        def titled = new Titled(bookTitle: 'G', shelf: shelf, subTitle: 's')
        titled.id = 1
        def kebab = new Kebab(bookTitle: 'K')
        kebab.id = 4

        expect:
        snakeCase.writeValueAsString(titled) == '{"id":1,"book_title":"G","shelf":{"id":3},"subtitle":"s"}'
        mapper().writeValueAsString(kebab) == '{"id":4,"book-title":"K"}'

        and: 'the JSON converter writes the names Jackson gives, apart from a naming strategy of the mapper it uses'
        new JSON(titled).toString() == '{"id":1,"bookTitle":"G","shelf":{"id":3},"subtitle":"s"}'
    }

    void "the includes and excludes of the JSON converter name a property of a domain class by its name or by the name it is written with, here #names"() {
        given:
        def instance = type == Titled ? new Titled(bookTitle: 'G', subTitle: 's') : new RenamedId(name: 'n')
        instance.id = 1
        def json = new JSON(instance)
        if (includes != null) {
            json.setIncludes(type, includes)
        }
        if (excludes != null) {
            json.setExcludes(type, excludes)
        }

        expect:
        json.toString() == written

        where:
        type      | includes                 | excludes     || written
        Titled    | ['subTitle']             | null         || '{"subtitle":"s"}'
        Titled    | ['subtitle']             | null         || '{"subtitle":"s"}'
        Titled    | ['id', 'subtitle']       | ['subTitle'] || '{"id":1}'
        Titled    | null                     | ['subTitle'] || '{"id":1,"bookTitle":"G","shelf":null}'
        Titled    | null                     | ['subtitle'] || '{"id":1,"bookTitle":"G","shelf":null}'
        RenamedId | ['identifier']           | null         || '{"identifier":1}'
        RenamedId | ['id']                   | null         || '{"identifier":1}'
        RenamedId | null                     | ['identifier'] || '{"name":"n"}'

        names = "${type.simpleName} with includes ${includes} and excludes ${excludes}"
    }

    void "the properties are written in the converter's order, whether or not the mapper sorts properties"() {
        given: 'Jackson 3 sorts the properties of beans alphabetically by default'
        def unsorted = JsonMapper.builder()
                .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .addModule(new DomainClassJacksonModule(ConvertersConfigurationInitializer.jsonDomainClassRendering(grailsApplication, new DefaultProxyHandler())))
                .build()
        def expected = '{"id":1,"title":"Grails","shelf":{"id":3},"writers":[{"id":1},{"id":2}],"writersByName":{"a":{"id":1},"b":{"id":2}}}'

        expect:
        JsonMapper.builder().build().isEnabled(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
        mapper().writeValueAsString(volume()) == expected
        unsorted.writeValueAsString(volume()) == expected
    }

    void "a subclass of a domain class that is not one is written as a bean, and rendered as one by the converter"() {
        given:
        def subclass = new VolumeSubclass(title: 'Sub', extra: 'e')

        when:
        def mapped = mapper().writeValueAsString(subclass)
        def rendered = new JSON(subclass).toString()

        then:
        mapped.contains('"extra":"e"')
        rendered.contains('"extra":"e"')
    }

    void "an instance of a domain subclass is written as its own domain class, also through a property of its superclass"() {
        given:
        def dog = new Dog(name: 'Rex')
        dog.id = 2
        def kennel = new Kennel(resident: dog)
        kennel.id = 5

        expect: 'as a reference, or in full when rendering deep'
        mapper().writeValueAsString(kennel) == '{"id":5,"resident":{"id":2}}'
        mapper(new DomainClassRendering(grailsApplication, new DefaultProxyHandler(), false, false, true))
                .writeValueAsString(kennel) == '{"id":5,"resident":{"@type":"Dog","id":2,"name":"Rex"}}'
    }

    void "rendering deep, the instances of an association declared with a type that writes type ids are written with theirs"() {
        given:
        def dog = new Dog(name: 'Rex')
        dog.id = 2
        def shelter = new Shelter(residents: [dog])
        shelter.id = 9

        expect:
        mapper(new DomainClassRendering(grailsApplication, new DefaultProxyHandler(), false, false, true))
                .writeValueAsString(shelter) == '{"id":9,"residents":[{"@type":"Dog","id":2,"name":"Rex"}]}'
        JSON.use('deep') { new JSON(shelter).toString() } == '{"id":9,"residents":[{"@type":"Dog","id":2,"name":"Rex"}]}'
    }

    void "the registry's deep configuration, and any that asks for it, writes associated instances in full"() {
        given:
        def registry = new NamedJsonConfigurationRegistry(
                new GrailsJsonMapperCustomizer(grailsApplication, new DefaultProxyHandler()).forGrails(JsonMapper.builder().build()))
        registry.register('catalog') { it.deep() }
        def full = '{"id":1,"title":"Grails","shelf":{"id":3,"name":"top"},"writers":[{"id":1,"name":"a"},{"id":2,"name":"b"}],' +
                '"writersByName":{"a":{"id":1,"name":"a"},"b":{"id":2,"name":"b"}}}'

        expect:
        registry.writeValueAsString('deep', volume()) == full
        registry.writeValueAsString('catalog', volume()) == full

        and: 'as references, otherwise'
        registry.writeValueAsString(null, volume()) == mapper().writeValueAsString(volume())
    }

    void "JSON.use('deep') renders associated instances in full with the converter"() {
        expect:
        JSON.use('deep') { new JSON(volume()).toString() } ==
                '{"id":1,"title":"Grails","shelf":{"id":3,"name":"top"},"writers":[{"id":1,"name":"a"},{"id":2,"name":"b"}],"writersByName":{"a":{"id":1,"name":"a"},"b":{"id":2,"name":"b"}}}'
    }

    void "a domain class with a composite key is written without an id"() {
        given:
        def pair = new Pair(first: 'a', second: 'b')

        expect:
        new JSON(pair).toString() == '{"first":"a","second":"b"}'
        mapper().writeValueAsString(pair) == '{"first":"a","second":"b"}'
    }

    void "the JSON converter writes the includes and excludes of the converter"() {
        given:
        def json = new JSON(volume())
        json.setExcludes(Volume, ['writers', 'writersByName'])

        expect:
        json.toString() == '{"id":1,"title":"Grails","shelf":{"id":3}}'
    }

    void "a marshaller for domain classes says how the converter renders an instance by being a DomainClassRenderer"() {
        given: 'a marshaller that renders the version and the class name, for the instances it writes'
        JSON.registerObjectMarshaller(new DomainClassMarshaller(false, grailsApplication) {
            @Override
            DomainClassRendering getRendering(JSON json, Class<?> type) {
                new DomainClassRendering(grailsApplication, new DefaultProxyHandler(), true, true, false)
            }
        })
        def shelf = new Shelf(name: 'top')
        shelf.id = 3
        shelf.version = 5

        expect:
        new JSON(shelf).toString() == "{\"class\":\"${Shelf.name}\",\"id\":3,\"version\":5,\"name\":\"top\"}"
    }

    void "a DomainClassMarshaller that writes references itself still does"() {
        given:
        JSON.registerObjectMarshaller(new DomainClassMarshaller(false, grailsApplication) {
            @Override
            protected void asShortObject(Object refObj, JSON json, PersistentProperty idProperty, PersistentEntity referencedDomainClass) {
                json.writer.value("${referencedDomainClass.javaClass.simpleName}:${refObj.id}".toString())
            }
        })

        expect:
        new JSON(volume()).toString() ==
                '{"id":1,"title":"Grails","shelf":"Shelf:3","writers":["Writer:1","Writer:2"],"writersByName":{"a":"Writer:1","b":"Writer:2"}}'
    }

    void "the id of an instance is the value a DomainClassMarshaller extracts, as are the ids of the instances it references"() {
        given:
        JSON.registerObjectMarshaller(new DomainClassMarshaller(false, grailsApplication) {
            @Override
            protected Object extractValue(Object domainObject, PersistentProperty property) {
                def value = super.extractValue(domainObject, property)
                property.name == 'id' ? "id-${value}".toString() : value
            }
        })

        expect:
        new JSON(volume()).toString() == '{"id":"id-1","title":"Grails","shelf":{"id":"id-3"},' +
                '"writers":[{"id":"id-1"},{"id":"id-2"}],"writersByName":{"a":{"id":"id-1"},"b":{"id":"id-2"}}}'
    }

    void "an unsaved instance is written without an id, unless its rendering gives it one"() {
        given:
        def unsaved = new Shelf(name: 'top')

        expect:
        mapper().writeValueAsString(unsaved) == '{"name":"top"}'
        new JSON(unsaved).toString() == '{"name":"top"}'

        when:
        JSON.registerObjectMarshaller(new DomainClassMarshaller(false, grailsApplication) {
            @Override
            protected Object extractValue(Object domainObject, PersistentProperty property) {
                def value = super.extractValue(domainObject, property)
                property.name == 'id' && value == null ? 'unsaved' : value
            }
        })

        then:
        new JSON(unsaved).toString() == '{"id":"unsaved","name":"top"}'
    }

    void "a value the rendering gives in place of the id is written with the serializer and inclusion of the id"() {
        given: 'an id written by its own serializer, and left out when empty'
        JSON.registerObjectMarshaller(new DomainClassMarshaller(false, grailsApplication) {
            @Override
            protected Object extractValue(Object domainObject, PersistentProperty property) {
                def value = super.extractValue(domainObject, property)
                property.name == 'id' ? (value == 1L ? 101L : '') : value
            }
        })
        def first = new PublicId(name: 'a')
        first.id = 1
        def second = new PublicId(name: 'b')
        second.id = 2

        expect:
        new JSON(first).toString() == '{"id":"101","name":"a"}'
        new JSON(second).toString() == '{"name":"b"}'
    }

    void "the id is written by the writer another module gives it"() {
        given: "an application's module, which the mapper consults ahead of the domain class module registered before it"
        def writer = new Writer(name: 'a')
        writer.id = 1
        def module = new SimpleModule('prefixed-ids').setSerializerModifier(new PrefixedIds())

        expect:
        JsonMapper.builder().addModule(new DomainClassJacksonModule(
                ConvertersConfigurationInitializer.jsonDomainClassRendering(grailsApplication, new DefaultProxyHandler())))
                .addModule(module).build().writeValueAsString(writer) == '{"id":"w1","name":"a"}'
    }

    /** The failure Jackson reports for a map with a null key. */
    private static String jacksonNullKeyFailure() {
        try {
            JsonMapper.builder().build().writeValueAsString([(null): 'a'])
        }
        catch (DatabindException e) {
            return e.originalMessage
        }
        throw new AssertionError('Jackson wrote a map with a null key')
    }

    private JsonMapper mapper(DomainClassRendering rendering = ConvertersConfigurationInitializer.jsonDomainClassRendering(grailsApplication, new DefaultProxyHandler())) {
        JsonMapper.builder().addModule(new DomainClassJacksonModule(rendering)).build()
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

    private static Rating rating(int score) {
        def rating = new Rating(score: score)
        rating.id = score
        rating
    }

    private static Volume volume() {
        def first = new Writer(name: 'a')
        first.id = 1
        def second = new Writer(name: 'b')
        second.id = 2
        def shelf = new Shelf(name: 'top')
        shelf.id = 3
        def volume = new Volume(title: 'Grails', shelf: shelf, writers: [first, second], writersByName: [a: first, b: second])
        volume.id = 1
        volume
    }

    private GrailsApplication domainApplication() {
        def grailsApplication = new DefaultGrailsApplication(Volume, Shelf, Writer, Partner, Label, Member, Animal, Dog, Code,
                Titled, Kebab, Kennel, Shelter, Pair, Annotated, ViewedId, ViewedClass, GormVersioned, StringId, HiddenId, PublicId, Library,
                Identified, Ordered, Filtered, Located, Filed, TenantScoped, Positioned, Extended, Coded, Branch, Summarized, Rated, Rating, Cat, Attributed, ViewedAttributes, FilteredAttributes, Badge, Badged, Creature, Parrot, Aviary, Counted, Tally, IgnoredAttributes, DisabledAttributes, Zoo, RenamedId, TypedKennel, KennelPet, Chain, TypedChain)
        // the JSON converter renders through the JsonMapper, and so with the serializer
        grailsApplication.config.setAt('grails.converters.json.legacy', false)
        grailsApplication.initialise()
        def mappingContext = new KeyValueMappingContext('json')
        mappingContext.addPersistentEntities(Volume, Shelf, Writer, Partner, Label, Member, Animal, Dog, Code, Titled, Kebab,
                Kennel, Shelter, Pair, Annotated, ViewedId, ViewedClass, GormVersioned, StringId, HiddenId, PublicId, Library, Identified,
                Ordered, Filtered, Located, Filed, TenantScoped, Positioned, Extended, Coded, Branch, Summarized, Rated, Rating, Cat, Attributed, ViewedAttributes, FilteredAttributes, Badge, Badged, Creature, Parrot, Aviary, Counted, Tally, IgnoredAttributes, DisabledAttributes, Zoo, RenamedId, TypedKennel, KennelPet, Chain, TypedChain)
        grailsApplication.setApplicationContext(Stub(ApplicationContext) {
            getBean('grailsDomainClassMappingContext', MappingContext) >> mappingContext
        })
        grailsApplication.setMappingContext(mappingContext)
        grailsApplication
    }
}

@Entity
class Volume {
    static hasMany = [writers: Writer, writersByName: Writer]
    String title
    Shelf shelf
    List writers
    Map writersByName
}

@Entity
class Shelf {
    String name
}

@Entity
class Writer {
    String name
}

@Entity
class Partner {
    String name
    Partner partner
}

@Entity
@JsonIgnoreProperties(['token'])
class Member {
    String name
    @JsonIgnore
    String password
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    String pin
    String token
}

class OutsideView { }

class IdView { }

@Entity
class ViewedId {
    @JsonView(IdView)
    Long id
    @JsonView(OutsideView)
    String name
}

@Entity
@JsonView(OutsideView)
class ViewedClass {
    String name
}

@Entity
class StringId {
    @JsonSerialize(using = ToStringSerializer)
    Long id
    String name
}

@Entity
@JsonIgnoreProperties(['id'])
class HiddenId {
    String name
}

@Entity
@JsonIgnoreProperties(['tenantId'])
class TenantScoped {
    String name
    String tenantId
}

@Entity
class PublicId {
    @JsonSerialize(using = ToStringSerializer)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    long id
    String name
}

class PrefixedIds extends ValueSerializerModifier {

    @Override
    List<BeanPropertyWriter> changeProperties(SerializationConfig config, BeanDescription.Supplier description,
            List<BeanPropertyWriter> writers) {
        writers.collect { it.name == 'id' ? new PrefixedIdWriter(it) : it }
    }
}

class PrefixedIdWriter extends BeanPropertyWriter {

    PrefixedIdWriter(BeanPropertyWriter writer) {
        super(writer)
    }

    @Override
    void serializeAsProperty(Object bean, JsonGenerator generator, SerializationContext context) {
        generator.writeStringProperty(name, "w${get(bean)}".toString())
    }
}

abstract class IgnoredIdMixin {
    @JsonIgnore
    abstract Long getId()
}

@JsonIgnoreProperties(['id'])
abstract class IgnoredIdPropertiesMixin { }

interface Catalogued { }

// GORM names the version in the @JsonIgnoreProperties of every domain class
@Entity
@JsonIgnoreProperties(['version'])
class GormVersioned implements Catalogued {
    String name
}

abstract class IgnoredVersionMixin {
    @JsonIgnore
    abstract Long getVersion()
}

@JsonIgnoreProperties(['version'])
abstract class IgnoredVersionPropertiesMixin { }

@Entity
class Library {
    static hasMany = [writers: Writer]
    String name
    @JsonIgnoreProperties(['name'])
    Shelf shelf
    @JsonIgnoreProperties(['name'])
    List writers
}

@Entity
@JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator)
class Identified {
    String name
    Identified partner
}

@Entity
@JsonPropertyOrder(['gamma', 'alpha'])
class Ordered {
    String alpha
    String beta
    String gamma
}

@Entity
@JsonFilter('domain')
class Filtered {
    String name
    String secret
}

@Entity
@JsonSerialize(using = LabelSerializer)
class Label {
    String text
}

class LabelSerializer extends StdSerializer<Label> {

    LabelSerializer() {
        super(Label)
    }

    @Override
    void serialize(Label label, JsonGenerator generator, SerializationContext context) {
        generator.writeString("label:${label.text}")
    }
}

@Entity
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
class Animal {
}

@Entity
class Dog extends Animal {
    String name
}

@Entity
@JsonSerialize(using = CountedSerializer)
class Counted {
    String name
}

class CountedSerializer extends StdSerializer<Counted> {

    static final java.util.concurrent.atomic.AtomicInteger CONTEXTUALIZED = new java.util.concurrent.atomic.AtomicInteger()

    CountedSerializer() {
        super(Counted)
    }

    @Override
    ValueSerializer<?> createContextual(SerializationContext context, tools.jackson.databind.BeanProperty property) {
        if (property != null) {
            CONTEXTUALIZED.incrementAndGet()
        }
        this
    }

    @Override
    void serialize(Counted counted, JsonGenerator generator, SerializationContext context) {
        generator.writeString(counted.name)
    }
}

@Entity
class Tally {
    static hasMany = [counts: Counted]
    Counted single
    List<Counted> counts
}

@Entity
class Chain {
    String name
    Chain next
    String tail
}

class ChainProxy extends Chain implements EntityProxy<Chain> {
    Chain target
    Long proxyId

    void initialize() {}

    boolean isInitialized() { target != null }

    Serializable getProxyKey() { proxyId }
}

@Entity
class TypedChain {
    String name
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = 'kind')
    TypedChain next
    String tail
}

class TypedChainProxy extends TypedChain implements EntityProxy<TypedChain> {
    TypedChain target
    Long proxyId

    void initialize() {}

    boolean isInitialized() { target != null }

    Serializable getProxyKey() { proxyId }
}

@Entity
class KennelPet {
    String name
}

@Entity
class TypedKennel {
    static hasMany = [pets: KennelPet]
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = 'kind')
    List<KennelPet> pets
}

@Entity
class Creature {
}

@Entity
class Parrot extends Creature {
    String name

    @JsonValue
    String jsonValue() { name }
}

@Entity
class Aviary {
    static hasMany = [birds: Creature]
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_ARRAY)
    List<Creature> birds
}

class AviaryBean {
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_ARRAY)
    List<Creature> birds
}

@Entity
class Cat extends Animal {
    String name

    @JsonValue
    String jsonValue() { name }
}

class Pet {
    Animal animal
}

@Entity
class Kennel {
    Animal resident
}

class WriterHolder {
    Writer writer
}

@Entity
class Shelter {
    static hasMany = [residents: Animal]
    List<Animal> residents
}

@Entity
class Code {
    String code

    @JsonValue
    String jsonValue() { code }
}

class IgnoringWriterHolder {
    @JsonIgnoreProperties('id')
    Writer writer
    @JsonIgnoreProperties('id')
    List<Writer> writers
}

class PartnerLabelSerializer extends StdSerializer<Partner> {

    PartnerLabelSerializer() {
        super(Partner)
    }

    @Override
    void serialize(Partner partner, JsonGenerator generator, SerializationContext context) {
        generator.writeStartObject()
        generator.writeStringProperty('label', partner.name)
        generator.writeName('partner')
        context.writeValue(generator, partner.partner)
        generator.writeEndObject()
    }
}

@Entity
class Coded {
    Code code
    Label label
    Summarized summarized
    Shelf shelf
}

@Entity
class Rated {
    static hasMany = [ratings: Rating]
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Rating rating
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    List<Rating> ratings
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    Code code
}

class RatedBean {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Rating rating
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    List<Rating> ratings
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    Code code
}

@Entity
class Rating {
    Integer score

    @JsonValue
    Integer value() { score }
}

@Entity
@JsonSerialize(converter = SummaryConverter)
class Summarized {
    String name
}

class Summary {
    String summary
}

class SummaryConverter extends StdConverter<Summarized, Summary> {

    @Override
    Summary convert(Summarized summarized) {
        new Summary(summary: "summary of ${summarized.name}")
    }
}

class WritingRendering extends DomainClassRendering {

    WritingRendering(GrailsApplication grailsApplication) {
        super(grailsApplication, new DefaultProxyHandler(), false, false, false)
    }

    @Override
    boolean writeReference(Object reference, PersistentProperty idProperty, PersistentEntity entity) {
        true
    }
}

class WriterNameSerializer extends StdSerializer<Writer> {

    WriterNameSerializer() {
        super(Writer)
    }

    @Override
    void serialize(Writer writer, JsonGenerator generator, SerializationContext context) {
        generator.writeString(writer.name)
    }
}

@Entity
class Extended {
    String name

    @JsonAnyGetter
    Map<String, Object> getExtras() {
        [extra: 'value']
    }
}

@Entity
class Attributed {
    String name
    @JsonAnyGetter
    @JsonView(Everyone)
    Map<String, String> customAttributes
    String note = 'n'
}

@Entity
@JsonIgnoreProperties('extras')
class IgnoredAttributes {
    String name
    @JsonAnyGetter
    Map<String, String> extras
}

@Entity
class DisabledAttributes {
    String name
    @JsonAnyGetter(enabled = false)
    Map<String, String> extras
}

@Entity
class Zoo {
    static hasMany = [byName: Animal]
    Map<String, Animal> byName
}

@Entity
@JsonFilter('attributes')
class FilteredAttributes {
    String name
    @JsonAnyGetter
    Map<String, String> attributes
}

@JsonFilter('attributes')
class FilteredAttributesBean {
    String name
    @JsonAnyGetter
    Map<String, String> attributes
}

@Entity
class ViewedAttributes {
    @JsonView(Everyone)
    String name
    @JsonAnyGetter
    Map<String, String> attributes
}

class ExtendedBean {
    String name

    @JsonAnyGetter
    Map<String, Object> getExtras() {
        [extra: 'value']
    }
}

@Entity
@JsonFormat(shape = JsonFormat.Shape.ARRAY)
class Positioned {
    String name
}

class ShapedPartnerHolder {
    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    Partner partner
}

class UnwrappedCode {
    @JsonUnwrapped
    Code code
    String note
}

class CodeProxy extends Code implements EntityProxy<Code> {
    Code target
    Long proxyId

    void initialize() {}

    boolean isInitialized() { target != null }

    Serializable getProxyKey() { proxyId }
}

class UnwrappedDog {
    @JsonUnwrapped
    Dog dog
    String note
}

class VersionedEnvelope {
    String version
    @JsonUnwrapped
    Writer writer
}

@Entity
class Located {
    static embedded = ['address']
    String name
    @JsonUnwrapped
    Address address
}

class Address {
    String name
    String city
}

@Entity
class Badge {
    String label
    BadgeColour colour
}

enum BadgeColour {
    GOLD
}

@Entity
class Badged {
    Badge badge
}

@Entity
class Branch {
    String name
    Located location
}

@Entity
class Filed {
    static embedded = ['label']
    String title
    @JsonUnwrapped
    FileLabel label
    Shelf shelf
}

class FileLabel {
    String code
}

class Unwrapped {
    @JsonUnwrapped
    Writer writer
    @JsonUnwrapped(prefix = 'w_')
    Writer prefixed
    String note
}

class Everyone { }

@Entity
class Annotated {
    String name
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String nickname
    @JsonView(Everyone)
    String hidden
    @JsonFormat(pattern = 'yyyy', timezone = 'UTC')
    Date born
}

@Entity
class RenamedId {
    @JsonProperty('identifier')
    Long id
    String name
}

@Entity
class Titled {
    String bookTitle
    Shelf shelf
    @JsonProperty('subtitle')
    String subTitle
}

@Entity
@JsonNaming(PropertyNamingStrategies.KebabCaseStrategy)
class Kebab {
    String bookTitle
}

@Entity
class Pair implements Serializable {
    String first
    String second

    static mapping = {
        id composite: ['first', 'second']
    }
}

class VolumeSubclass extends Volume {
    String extra
}

class WriterProxy extends Writer implements EntityProxy<Writer> {
    Writer target
    Long proxyId

    void initialize() {}

    boolean isInitialized() { target != null }

    Serializable getProxyKey() { proxyId }
}

/** A proxy as Hibernate gives one: its class extends the class it proxies, and it has a lazy initializer. */
class LazyWriterProxy extends Writer implements HibernateProxy {
    Writer target

    LazyInitializer getHibernateLazyInitializer() {
        new LazyInitializer(implementation: target)
    }
}

class LazyInitializer {
    Object implementation
}

class DogProxy extends Dog implements EntityProxy<Dog> {
    Dog target
    Long proxyId

    void initialize() {}

    boolean isInitialized() { target != null }

    Serializable getProxyKey() { proxyId }
}

/** A proxy of an Animal that stands for a Dog, as Hibernate gives one for an association to Animal. */
class LazyAnimalProxy extends Animal implements HibernateProxy {
    Animal target

    LazyInitializer getHibernateLazyInitializer() {
        new LazyInitializer(implementation: target)
    }
}

class IgnoringPet {
    @JsonIgnoreProperties('id')
    Animal animal
}

/** A proxy handler that unwraps the proxies Hibernate gives, as that of Hibernate does. */
class LazyProxyHandler implements EntityProxyHandler {

    boolean isProxy(Object o) { o instanceof HibernateProxy }

    Object unwrapIfProxy(Object instance) { isProxy(instance) ? instance.hibernateLazyInitializer.implementation : instance }

    boolean isInitialized(Object o) { true }

    void initialize(Object o) {}

    boolean isInitialized(Object obj, String associationName) { true }

    Object getProxyIdentifier(Object o) { isProxy(o) ? unwrapIfProxy(o).id : null }

    Class<?> getProxiedClass(Object o) { isProxy(o) ? o.getClass().superclass : o.getClass() }
}

/** A module's wrapper around the serializer of a domain class, as a library may add one. */
class ApplicationWrapper extends DelegatingSerializer {

    ApplicationWrapper(ValueSerializer<?> delegatee) {
        super(delegatee)
    }

    @Override
    protected ValueSerializer<Object> newDelegatingInstance(ValueSerializer<?> delegatee) {
        new ApplicationWrapper(delegatee)
    }
}

class WriterProxyHandler implements EntityProxyHandler {

    boolean isProxy(Object o) { o instanceof WriterProxy }

    Object unwrapIfProxy(Object instance) {
        if (instance instanceof WriterProxy) {
            assert instance.target != null: 'an uninitialized proxy was loaded'
            return instance.target
        }
        instance
    }

    boolean isInitialized(Object o) { !(o instanceof WriterProxy) || o.target != null }

    void initialize(Object o) {}

    boolean isInitialized(Object obj, String associationName) { true }

    Object getProxyIdentifier(Object o) { o instanceof WriterProxy ? o.proxyId : null }

    Class<?> getProxiedClass(Object o) { o instanceof WriterProxy ? Writer : o.getClass() }
}
