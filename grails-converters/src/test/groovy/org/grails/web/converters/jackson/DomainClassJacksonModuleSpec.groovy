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
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonUnwrapped
import com.fasterxml.jackson.annotation.JsonValue
import spock.lang.Specification
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.annotation.JsonNaming
import tools.jackson.databind.annotation.JsonSerialize
import tools.jackson.databind.exc.InvalidDefinitionException
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.ser.std.StdSerializer

import org.springframework.context.ApplicationContext

import grails.converters.JSON
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

    void "properties annotated @JsonIgnore or write-only are not written, and those of @JsonIgnoreProperties only by the mapper"() {
        given: 'GORM adds @JsonIgnoreProperties to every domain class, naming properties such as the version'
        def member = new Member(name: 'a', password: 'secret', pin: '1234', token: 'abc')
        member.id = 1
        member.version = 2

        expect:
        mapper().writeValueAsString(member) == '{"id":1,"name":"a"}'
        mapper(new DomainClassRendering(grailsApplication, new DefaultProxyHandler(), true, false, false))
                .writeValueAsString(member) == '{"id":1,"version":2,"name":"a"}'
        new JSON(member).toString() == '{"id":1,"name":"a","token":"abc"}'
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

        and: 'the JSON converter writes the property names as they are'
        new JSON(titled).toString() == '{"id":1,"bookTitle":"G","shelf":{"id":3},"subTitle":"s"}'
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

    private JsonMapper mapper(DomainClassRendering rendering = ConvertersConfigurationInitializer.jsonDomainClassRendering(grailsApplication, new DefaultProxyHandler())) {
        JsonMapper.builder().addModule(new DomainClassJacksonModule(rendering)).build()
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
                Titled, Kebab, Kennel, Pair)
        grailsApplication.initialise()
        def mappingContext = new KeyValueMappingContext('json')
        mappingContext.addPersistentEntities(Volume, Shelf, Writer, Partner, Label, Member, Animal, Dog, Code, Titled, Kebab,
                Kennel, Pair)
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
