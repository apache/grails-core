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

import groovy.json.JsonSlurper

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
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.SerializationConfig
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.annotation.JsonNaming
import tools.jackson.databind.annotation.JsonSerialize
import tools.jackson.databind.exc.InvalidDefinitionException
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule
import tools.jackson.databind.ser.BeanPropertyWriter
import tools.jackson.databind.ser.ValueSerializerModifier
import tools.jackson.databind.ser.std.SimpleBeanPropertyFilter
import tools.jackson.databind.ser.std.SimpleFilterProvider
import tools.jackson.databind.ser.std.StdSerializer
import tools.jackson.databind.ser.std.ToStringSerializer

import org.springframework.context.ApplicationContext

import grails.converters.JSON
import grails.converters.json.NamedJsonConfigurationRegistry
import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.core.support.proxy.DefaultProxyHandler
import grails.core.support.proxy.EntityProxyHandler
import grails.persistence.Entity
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
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

        and: 'so does the JSON converter, whose references order their keys as a map does'
        new JsonSlurper().parseText(new JSON(first).toString()) ==
                new JsonSlurper().parseText('{"id":1,"name":"first","partner":{"id":2,"name":"second","partner":' + repeated + '}}')

        where:
        behaviour     || repeated
        'DEFAULT'     || '{"_ref":"..","class":"' + Partner.name + '"}'
        'PATH'        || '{"class":"' + Partner.name + '","ref":"root"}'
        'INSERT_NULL' || 'null'
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

        when: 'the response names the properties to write'
        String log = stderrWhile { registry.writeValue(null, projected, new WriterProxy(proxyId: 9), ['name'], null) }

        then: 'nothing is left to project'
        projected.toString() == 'null'
        !log.contains('were not applied')
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

    void "a domain instance with a type id in a @JsonUnwrapped property fails, as Jackson's own unwrapping does"() {
        given:
        def dog = new Dog(name: 'Rex')
        dog.id = 2
        def lenient = JsonMapper.builder()
                .disable(SerializationFeature.FAIL_ON_UNWRAPPED_TYPE_IDENTIFIERS)
                .addModule(new DomainClassJacksonModule(ConvertersConfigurationInitializer.jsonDomainClassRendering(grailsApplication, new DefaultProxyHandler())))
                .build()

        when:
        mapper().writeValueAsString(new UnwrappedDog(dog: dog, note: 'n'))

        then:
        thrown(InvalidDefinitionException)

        expect: 'without the type id, when the feature is disabled'
        lenient.writeValueAsString(new UnwrappedDog(dog: dog, note: 'n')) == '{"id":2,"name":"Rex","note":"n"}'
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

    void "a domain class with a @JsonValue is written by Jackson"() {
        given:
        def code = new Code(code: 'X1')
        code.id = 1

        expect:
        mapper().writeValueAsString(code) == '"X1"'
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
                Identified, Ordered, Filtered)
        // the JSON converter renders through the JsonMapper, and so with the serializer
        grailsApplication.config.setAt('grails.converters.json.legacy', false)
        grailsApplication.initialise()
        def mappingContext = new KeyValueMappingContext('json')
        mappingContext.addPersistentEntities(Volume, Shelf, Writer, Partner, Label, Member, Animal, Dog, Code, Titled, Kebab,
                Kennel, Shelter, Pair, Annotated, ViewedId, ViewedClass, GormVersioned, StringId, HiddenId, PublicId, Library, Identified,
                Ordered, Filtered)
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

class Pet {
    Animal animal
}

@Entity
class Kennel {
    Animal resident
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

class UnwrappedDog {
    @JsonUnwrapped
    Dog dog
    String note
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

class DogProxy extends Dog implements EntityProxy<Dog> {
    Dog target
    Long proxyId

    void initialize() {}

    boolean isInitialized() { target != null }

    Serializable getProxyKey() { proxyId }
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
