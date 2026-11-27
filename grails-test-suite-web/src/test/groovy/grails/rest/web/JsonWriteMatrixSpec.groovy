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
package grails.rest.web

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonFilter
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIdentityInfo
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.annotation.JsonRawValue
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import com.fasterxml.jackson.annotation.JsonUnwrapped
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.annotation.JsonView
import com.fasterxml.jackson.annotation.ObjectIdGenerators
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import spock.lang.Specification
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.JacksonSerializable
import tools.jackson.databind.JsonNode
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ValueSerializer
import tools.jackson.databind.annotation.JsonNaming
import tools.jackson.databind.annotation.JsonSerialize
import tools.jackson.databind.ObjectWriter
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule
import tools.jackson.databind.jsontype.TypeSerializer
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.NullNode
import tools.jackson.databind.node.ObjectNode
import tools.jackson.databind.ser.std.SimpleBeanPropertyFilter
import tools.jackson.databind.ser.std.SimpleFilterProvider

import grails.artefact.Artefact
import grails.converters.JSON
import grails.converters.json.NamedJsonConfigurationRegistry
import grails.persistence.Entity
import org.grails.datastore.mapping.proxy.EntityProxy
import grails.testing.gorm.DataTest
import grails.testing.web.controllers.ControllerUnitTest

/**
 * Writes a value of each Jackson feature through each way an application writes JSON, with and without includes and
 * excludes, and compares what each writes with what Spring Boot's mapper writes: the same JSON, less the properties the
 * includes and excludes leave out of the instances of the value's class, or of its elements' classes. Spring Boot's
 * mapper writes domain class instances with the module {@code grails.converters.json.domain.jackson.enabled} gives it,
 * as the JSON converter writes them. Each spec that extends it implements {@code ControllerUnitTest}, which finds the
 * controller class in the generic type of the spec's own declaration.
 */
abstract class JsonWriteMatrix extends Specification {

    static final String RESPOND = 'respond'
    static final String RESPOND_NAMED = 'respond with a named configuration'
    static final String RENDER = 'render json:'
    static final String RENDER_NAMED = 'render json: with a named configuration'
    static final String CONVERTER = 'the JSON converter'
    static final String REGISTRY = 'the named configuration registry'
    static final String RESPOND_VIEWED = 'respond with a configuration that has a view'
    static final String RENDER_VIEWED = 'render json: with a configuration that has a view'
    static final String REGISTRY_VIEWED = 'the registry with a configuration that has a view'
    static final String RESPOND_SERIALIZED = 'respond with a configuration that has serializers'
    static final String RENDER_SERIALIZED = 'render json: with a configuration that has serializers'
    static final String REGISTRY_SERIALIZED = 'the registry with a configuration that has serializers'

    static final List<String> REGISTRY_PATHS = [REGISTRY, REGISTRY_VIEWED, REGISTRY_SERIALIZED]

    /** The classes the configuration with serializers has a serializer of its own for. */
    static final List<Class<?>> SERIALIZED_CLASSES = [MatrixPlain, MatrixAuthor]

    static final String SINGLE = 'a value'
    static final String LISTED = 'a list of two'
    static final String NESTED = 'a property of a bean'

    @Configuration
    static class MatrixFilterConfiguration {

        @Bean
        JsonMapperBuilderCustomizer matrixFilterCustomizer() {
            { JsonMapper.Builder builder ->
                builder.filterProvider(new SimpleFilterProvider()
                        .addFilter('matrix', SimpleBeanPropertyFilter.serializeAllExcept('hidden')))
            } as JsonMapperBuilderCustomizer
        }
    }

    /**
     * @return the ways of writing JSON the configuration of the spec writes with
     */
    abstract List<String> paths()

    /**
     * @return whether the way of writing JSON applies includes and excludes to the elements of a list
     */
    boolean projectsElements(String path) {
        true
    }

    /**
     * @return whether the way of writing JSON is the JSON converter's, which writes an enum by its name
     */
    boolean writesWithConverter(String path) {
        path == CONVERTER
    }

    void setup() {
        NamedJsonConfigurationRegistry registry = applicationContext.getBean(NamedJsonConfigurationRegistry)
        if (!registry.contains('matrix')) {
            registry.register('matrix') { }
            registry.register('matrixViewed') { it.view(MatrixPublicView) }
            registry.register('matrixSerialized') {
                it.serializer(MatrixPlain, new MatrixPlainSerializer())
                it.serializer(MatrixAuthor, new MatrixAuthorSerializer())
            }
        }
    }

    /**
     * @return the named configuration the way of writing JSON names, or {@code null}
     */
    static String configurationOf(String path) {
        switch (path) {
            case [RESPOND_NAMED, RENDER_NAMED, REGISTRY]:
                return 'matrix'
            case [RESPOND_VIEWED, RENDER_VIEWED, REGISTRY_VIEWED]:
                return 'matrixViewed'
            case [RESPOND_SERIALIZED, RENDER_SERIALIZED, REGISTRY_SERIALIZED]:
                return 'matrixSerialized'
            default:
                return null
        }
    }

    /**
     * @return what Spring Boot's mapper writes with what the configuration of the way of writing JSON adds to it
     */
    private ObjectWriter oracle(String path) {
        JsonMapper mapper = applicationContext.getBean(JsonMapper)
        switch (configurationOf(path)) {
            case 'matrixViewed':
                return mapper.writerWithView(MatrixPublicView)
            case 'matrixSerialized':
                return mapper.rebuild().addModule(new SimpleModule()
                        .addSerializer(MatrixPlain, new MatrixPlainSerializer())
                        .addSerializer(MatrixAuthor, new MatrixAuthorSerializer())).build().writer()
            default:
                return mapper.writer()
        }
    }

    /**
     * @return whether the configuration of the way of writing JSON has a serializer of its own for instances of a class
     */
    private static boolean serializedByConfiguration(String path, Class<?> type) {
        configurationOf(path) == 'matrixSerialized' && SERIALIZED_CLASSES.any { it.isAssignableFrom(type) }
    }

    void '#fixture written as #container through #path is what Spring Boot writes, less what includes and excludes leave out'() {
        expect:
        mismatches(fixture, container, path) == []

        where:
        [fixture, container, path] << combinations()
    }

    void "Spring Boot's mapper writes a domain class instance as the JSON converter does, as the domain rows compare with"() {
        expect:
        applicationContext.getBean(JsonMapper).writeValueAsString(MatrixBook.sample()) ==
                '{"id":1,"title":"Grails","pages":2,"author":{"id":7},"reviewers":[{"id":7}]}'

        and: 'a view leaves out what it does not name, and a serializer of an associated class writes it in its place'
        oracle(REGISTRY_VIEWED).writeValueAsString(new MatrixViewed()) == '{"email":"ada@example.com"}'
        oracle(REGISTRY_SERIALIZED).writeValueAsString(MatrixBook.sample()) ==
                '{"id":1,"title":"Grails","pages":2,"author":"author:Ada","reviewers":["author:Ada"]}'
    }

    List<List<Object>> combinations() {
        List<List<Object>> rows = []
        for (MatrixFixture fixture : MatrixFixture.ALL) {
            for (String container : fixture.containers) {
                for (String path : paths()) {
                    rows << [fixture, container, path]
                }
            }
        }
        rows
    }

    private List<String> mismatches(MatrixFixture fixture, String container, String path) {
        JsonMapper mapper = applicationContext.getBean(JsonMapper)
        List<String> found = []
        for (Map<String, Object> options : fixture.options(container)) {
            if (path in REGISTRY_PATHS && options) {
                continue
            }
            String expected = fixture.expected(oracle(path), mapper, container, options, projectsElements(path),
                    writesWithConverter(path)) { Class<?> type -> serializedByConfiguration(path, type) }
            String written
            try {
                written = mapper.readTree(write(path, fixture.value(container), options)).toString()
            }
            catch (Throwable failure) {
                found << "${options}: threw ${failure}".toString()
                continue
            }
            if (written != expected) {
                found << "${options}: wrote ${written}, not ${expected}".toString()
            }
        }
        found
    }

    private String write(String path, Object value, Map<String, Object> options) {
        if (path == CONVERTER) {
            JSON converter = new JSON(value)
            for (Class<?> type : MatrixFixture.classesOf(value)) {
                if (options.includes != null) {
                    converter.setIncludes(type, options.includes as List<String>)
                }
                if (options.excludes != null) {
                    converter.setExcludes(type, options.excludes as List<String>)
                }
            }
            return converter.toString()
        }
        String configuration = configurationOf(path)
        if (path in REGISTRY_PATHS) {
            return applicationContext.getBean(NamedJsonConfigurationRegistry).writeValueAsString(configuration, value)
        }
        Map<String, Object> arguments = configuration ? options + [jsonConfiguration: configuration] : options
        response.reset()
        response.format = 'json'
        switch (path) {
            case [RESPOND, RESPOND_NAMED, RESPOND_VIEWED, RESPOND_SERIALIZED]:
                controller.respond(value, arguments)
                break
            case [RENDER, RENDER_NAMED, RENDER_VIEWED, RENDER_SERIALIZED]:
                controller.render([json: value] + arguments)
                break
            default:
                throw new IllegalArgumentException(path)
        }
        response.contentAsString
    }
}

/**
 * With Spring MVC writing {@code respond}, and the JSON converter not legacy, every way of writing JSON.
 */
class JsonWriteMatrixSpec extends JsonWriteMatrix implements ControllerUnitTest<JsonMatrixController>, DataTest {

    Closure doWithConfig() {
        { config ->
            config['grails.web.rendering.json.spring'] = true
            config['grails.converters.json.legacy'] = false
            config['grails.converters.json.domain.jackson.enabled'] = true
        }
    }

    Class<?>[] getDomainClassesToMock() {
        MatrixFixture.DOMAIN_CLASSES
    }

    List<String> paths() {
        [RESPOND, RESPOND_NAMED, RENDER, RENDER_NAMED, CONVERTER, REGISTRY, RESPOND_VIEWED, RENDER_VIEWED, REGISTRY_VIEWED,
         RESPOND_SERIALIZED, RENDER_SERIALIZED, REGISTRY_SERIALIZED]
    }
}

/**
 * With the JSON converter, not legacy, writing {@code respond}, which applies includes and excludes to the class of the
 * value only, so not to the elements of a list, as the respond reference documents, unless {@code respond} names a
 * configuration, which the Grails mapper writes with.
 */
class ConverterRespondJsonWriteMatrixSpec extends JsonWriteMatrix implements ControllerUnitTest<JsonMatrixController>, DataTest {

    Closure doWithConfig() {
        { config ->
            config['grails.converters.json.legacy'] = false
            config['grails.converters.json.domain.jackson.enabled'] = true
        }
    }

    Class<?>[] getDomainClassesToMock() {
        MatrixFixture.DOMAIN_CLASSES
    }

    List<String> paths() {
        [RESPOND, RESPOND_NAMED, RESPOND_VIEWED, RESPOND_SERIALIZED]
    }

    @Override
    boolean projectsElements(String path) {
        path != RESPOND
    }

    @Override
    boolean writesWithConverter(String path) {
        path == RESPOND
    }
}

/**
 * A value to write, and what the projection of each class it writes instances of names.
 */
class MatrixFixture {

    static final Class<?>[] DOMAIN_CLASSES = [MatrixAuthor, MatrixBook, MatrixRenamedBook, MatrixSnakeBook, MatrixAttributedBook,
            MatrixTypedBook, MatrixIgnoringBook, MatrixIncludingBook, MatrixCodedBook, MatrixViewedBook, MatrixDatedBook,
            MatrixIdentifiedBook, MatrixShelf, MatrixVolume] as Class<?>[]

    static final List<MatrixFixture> ALL = [
            of('a bean', { new MatrixPlain() }, [(MatrixPlain): [name: ['name'], count: ['count']]]),
            of('a renamed property', { new MatrixRenamed() },
                    [(MatrixRenamed): [name: ['label'], count: ['count']]], aliases: [label: 'name']),
            of('an ordered bean', { new MatrixOrdered() }, [(MatrixOrdered): [count: ['count'], name: ['name']]]),
            of('an ignored property', { new MatrixIgnored() }, [(MatrixIgnored): [name: ['name'], secret: []]]),
            of('properties the class ignores', { new MatrixIgnoredProperties() },
                    [(MatrixIgnoredProperties): [name: ['name'], secret: []]]),
            of('a field any-getter', { new MatrixAnyField() },
                    [(MatrixAnyField): [name: ['name'], attributes: ['colour', 'size']]]),
            of('a method any-getter', { new MatrixAnyMethod() }, [(MatrixAnyMethod): [name: ['name'], extras: ['colour']]]),
            of('an unwrapped property', { new MatrixUnwrapped() },
                    [(MatrixUnwrapped): [name: ['name'], address: ['street', 'city']]]),
            of('an unwrapped property with a prefix', { new MatrixUnwrappedPrefix() },
                    [(MatrixUnwrappedPrefix): [name: ['name'], address: ['home_street', 'home_city']]]),
            of('a class type id', { new MatrixCircle() }, [(MatrixCircle): [label: ['label'], radius: ['radius']]]),
            of('a property type id', { new MatrixPropertyTyped() },
                    [(MatrixPropertyTyped): [name: ['name'], payload: ['payload']]]),
            of('element type ids of a list', { new MatrixTypedElements() },
                    [(MatrixTypedElements): [name: ['name'], items: ['items']]]),
            of('element type ids of an array', { new MatrixTypedArray() },
                    [(MatrixTypedArray): [name: ['name'], items: ['items']]]),
            of('value type ids of a map', { new MatrixTypedMap() }, [(MatrixTypedMap): [name: ['name'], items: ['items']]]),
            of('a @JsonValue string', { new MatrixValue() }, [:]),
            of('a @JsonValue object', { new MatrixValueObject() }, [:]),
            of('a class serializer', { new MatrixSerialized() }, [:]),
            of('a JacksonSerializable', { new MatrixSerializable() }, [:]),
            of('a property serializer', { new MatrixPropertySerialized() },
                    [(MatrixPropertySerialized): [name: ['name'], title: ['title']]]),
            of('inclusion rules', { new MatrixIncluded() },
                    [(MatrixIncluded): [name: ['name'], nick: ['nick'], tags: ['tags'], count: ['count']]]),
            of('formats', { new MatrixFormatted() }, [(MatrixFormatted): [count: ['count'], day: ['day']]]),
            of('an array-shaped bean', { new MatrixArrayShaped() }, [(MatrixArrayShaped): [name: [], count: []]]),
            of('a record', { new MatrixRecord('Ada', 2) }, [(MatrixRecord): [name: ['name'], count: ['count']]]),
            of('an identity id', { new MatrixIdentified() }, [(MatrixIdentified): [name: ['name'], count: ['count']]]),
            of('a view property', { new MatrixViewed() }, [(MatrixViewed): [name: ['name'], email: ['email']]]),
            of('a read-only property', { new MatrixComputed() }, [(MatrixComputed): [name: ['name'], greeting: ['greeting']]]),
            of('enums', { new MatrixEnums() }, [(MatrixEnums): [colour: ['colour'], size: ['size'], level: ['level']]],
                    converted: { ObjectNode node ->
                        // the converter writes an enum by its name, so that it binds back
                        [colour: 'RED', size: 'SMALL'].each { String key, String name ->
                            if (node.has(key)) {
                                node.put(key, name)
                            }
                        }
                    }),
            of('containers', { new MatrixContainers() },
                    [(MatrixContainers): [data: ['data'], nick: ['nick'], numbers: ['numbers'], addresses: ['addresses']]]),
            of('a nested bean', { new MatrixNested() }, [(MatrixNested): [name: ['name'], inner: ['inner']]]),
            of('a raw value', { new MatrixRaw() }, [(MatrixRaw): [name: ['name'], raw: ['raw']]]),
            of('a naming strategy', { new MatrixSnake() }, [(MatrixSnake): [firstName: ['first_name'], loginCount: ['login_count']]],
                    aliases: [first_name: 'firstName', login_count: 'loginCount']),
            of('a filtered bean', { new MatrixFiltered() }, [(MatrixFiltered): [name: ['name'], hidden: []]]),
            of('a GString', { new MatrixGString() }, [(MatrixGString): [name: ['name'], greeting: ['greeting']]]),
            of('elements of two classes', { [new MatrixPlain(), new MatrixChild()] },
                    [(MatrixPlain): [name: ['name'], count: ['count']], (MatrixChild): [name: ['name'], count: ['count'], extra: ['extra']]],
                    containers: [JsonWriteMatrix.SINGLE, JsonWriteMatrix.NESTED]),
            of('a domain class', { MatrixBook.sample() },
                    [(MatrixBook): [id: ['id'], title: ['title'], pages: ['pages'], author: ['author'], reviewers: ['reviewers']]]),
            of('a renamed domain property', { withId(new MatrixRenamedBook(title: 'Grails', pages: 2)) },
                    [(MatrixRenamedBook): [id: ['id'], title: ['label'], pages: ['pages']]], aliases: [label: 'title']),
            of('a domain naming strategy', { withId(new MatrixSnakeBook(bookTitle: 'Grails', pageCount: 2)) },
                    [(MatrixSnakeBook): [id: ['id'], bookTitle: ['book_title'], pageCount: ['page_count']]],
                    aliases: [book_title: 'bookTitle', page_count: 'pageCount']),
            of('a domain any-getter', { withId(new MatrixAttributedBook(title: 'Grails', attributes: [colour: 'red'])) },
                    [(MatrixAttributedBook): [id: ['id'], title: ['title'], attributes: ['colour']]]),
            of('a domain type id', { withId(new MatrixTypedBook(title: 'Grails')) },
                    [(MatrixTypedBook): [id: ['id'], title: ['title']]]),
            of('an ignored domain property', { withId(new MatrixIgnoringBook(title: 'Grails', secret: 's')) },
                    [(MatrixIgnoringBook): [id: ['id'], title: ['title'], secret: []]]),
            of('domain inclusion rules', { withId(new MatrixIncludingBook(title: 'Grails')) },
                    [(MatrixIncludingBook): [id: ['id'], title: ['title'], nick: ['nick']]]),
            of('a domain @JsonValue', { withId(new MatrixCodedBook(code: 'B1')) }, [:]),
            of('a domain view property', { withId(new MatrixViewedBook(title: 'Grails', email: 'a@example.com')) },
                    [(MatrixViewedBook): [id: ['id'], title: ['title'], email: ['email']]]),
            of('a domain format', { withId(new MatrixDatedBook(title: 'Grails', published: new Date(0))) },
                    [(MatrixDatedBook): [id: ['id'], title: ['title'], published: ['published']]]),
            of('a domain identity id', { withId(new MatrixIdentifiedBook(title: 'Grails')) },
                    [(MatrixIdentifiedBook): [id: ['id'], title: ['title']]]),
            of('a domain class whose association is a proxy that is not loaded', {
                withId(new MatrixBook(title: 'Grails', pages: 2, author: proxiedAuthor(), reviewers: []))
            }, [(MatrixBook): [id: ['id'], title: ['title'], pages: ['pages'], author: ['author'], reviewers: ['reviewers']]]),
            of('a proxy of a domain class that is not loaded', { proxiedAuthor() },
                    [(MatrixAuthor): [id: ['id'], name: ['name']]]),
            of('domain associations whose property gives their type ids', { MatrixShelf.sample() },
                    [(MatrixShelf): [id: ['id'], name: ['name'], featured: ['featured'], volumes: ['volumes']]])
    ]

    final String description
    final Closure<?> instance

    /**
     * The properties of each class the fixture writes instances of, in the order they are written, with the JSON names
     * each is written as. A class a serializer of its own writes has none.
     */
    final Map<Class<?>, Map<String, List<String>>> properties

    /** The JSON names a projection may name a property by besides its bean name. */
    final Map<String, String> aliases

    final List<String> containers

    /** Changes what Spring Boot writes for an instance into what the JSON converter writes for it, or {@code null}. */
    final Closure<?> converted

    private MatrixFixture(String description, Closure<?> instance, Map<Class<?>, Map<String, List<String>>> properties,
            Map<String, String> aliases, List<String> containers, Closure<?> converted) {
        this.description = description
        this.instance = instance
        this.properties = properties
        this.aliases = aliases
        this.containers = containers
        this.converted = converted
    }

    static MatrixFixture of(Map<String, Object> extras = [:], String description, Closure<?> instance,
            Map<Class<?>, Map<String, List<String>>> properties) {
        new MatrixFixture(description, instance, properties, (extras.aliases ?: [:]) as Map<String, String>,
                (extras.containers ?: [JsonWriteMatrix.SINGLE, JsonWriteMatrix.LISTED, JsonWriteMatrix.NESTED]) as List<String>,
                extras.converted as Closure<?>)
    }

    /** A proxy, not loaded, of an author that is saved, so that each call gives an equal proxy of the same author. */
    private static MatrixAuthor proxiedAuthor() {
        MatrixAuthor author = MatrixAuthor.findByName('Proxied') ?: new MatrixAuthor(name: 'Proxied').save(flush: true, failOnError: true)
        MatrixAuthor.withSession { it.clear() }
        MatrixAuthor proxy = MatrixAuthor.load(author.id)
        assert proxy instanceof EntityProxy && !proxy.initialized
        proxy
    }

    /** The class of an instance, which for a proxy is the domain class it stands for. */
    static Class<?> classOf(Object instance) {
        instance instanceof EntityProxy ? instance.getClass().superclass : instance.getClass()
    }

    private static Object withId(Object instance) {
        instance['id'] = 1L
        instance
    }

    static Set<Class<?>> classesOf(Object value) {
        value instanceof Collection ? value.collect { classOf(it) } as LinkedHashSet<Class<?>> : [classOf(value)] as LinkedHashSet<Class<?>>
    }

    @Override
    String toString() {
        description
    }

    Object value(String container) {
        switch (container) {
            case JsonWriteMatrix.LISTED:
                return [instance.call(), instance.call()]
            case JsonWriteMatrix.NESTED:
                return new MatrixHolder(value: instance.call())
            default:
                return instance.call()
        }
    }

    /**
     * @return no includes or excludes, then excluding and including each property by each name, then including none
     */
    List<Map<String, Object>> options(String container) {
        if (container == JsonWriteMatrix.NESTED) {
            return [[:]]
        }
        Set<String> names = new LinkedHashSet<>()
        properties.values().each { names.addAll(it.keySet()) }
        names.addAll(aliases.keySet())
        if (names.empty) {
            names << 'name'
        }
        List<Map<String, Object>> options = [[:]]
        names.each { String name ->
            options << [excludes: [name]]
            options << [includes: [name]]
        }
        options << [includes: []]
        options
    }

    /**
     * @param writer writes what the way of writing JSON is compared with
     * @param projectsElements whether includes and excludes apply to the elements of a list
     * @param byConverter whether the JSON converter writes it
     * @param serializedByConfiguration whether the configuration has a serializer of its own for instances of a class,
     *        which includes and excludes do not reach
     */
    String expected(ObjectWriter writer, JsonMapper reader, String container, Map<String, Object> options,
            boolean projectsElements, boolean byConverter, Closure<Boolean> serializedByConfiguration) {
        Object value = value(container)
        JsonNode tree = reader.readTree(writer.writeValueAsString(value))
        boolean projected = options && (projectsElements || !(value instanceof List))
        instances(value, tree).each { Object instance, JsonNode node ->
            if (byConverter && converted != null && node instanceof ObjectNode) {
                converted.call(node)
            }
            if (projected && !serializedByConfiguration.call(classOf(instance))) {
                project(node, classOf(instance), options)
            }
        }
        tree.toString()
    }

    /**
     * The instances of the fixture in a value, each with the JSON it is written as; none where it is not written, as the
     * value of a property a view leaves out.
     */
    private static Map<Object, JsonNode> instances(Object value, JsonNode tree) {
        Map<Object, JsonNode> found = new IdentityHashMap<>()
        if (tree == null) {
            return found
        }
        if (value instanceof MatrixHolder) {
            found.putAll(instances(value.value, tree.get('value')))
        }
        else if (value instanceof List) {
            value.eachWithIndex { Object element, int index -> found.put(element, tree.get(index)) }
        }
        else {
            found.put(value, tree)
        }
        found
    }

    private void project(JsonNode node, Class<?> type, Map<String, Object> options) {
        Map<String, List<String>> written = properties[type]
        if (written == null) {
            return
        }
        written.keySet().eachWithIndex { String property, int index ->
            if (!kept(property, options)) {
                if (node instanceof ArrayNode) {
                    node.set(index, NullNode.instance)
                }
                else {
                    written[property].each { (node as ObjectNode).remove(it) }
                }
            }
        }
    }

    private boolean kept(String property, Map<String, Object> options) {
        List<String> names = [property] + aliases.findAll { it.value == property }*.key
        List<String> includes = options.includes as List<String>
        List<String> excludes = options.excludes as List<String>
        !names.any { excludes?.contains(it) } && (includes == null || names.any { includes.contains(it) })
    }
}

@Artefact('Controller')
class JsonMatrixController { }

class MatrixHolder {
    Object value
}

class MatrixPlain {
    String name = 'Ada'
    Integer count = 2
}

class MatrixChild extends MatrixPlain {
    String extra = 'x'
}

class MatrixRenamed {
    @JsonProperty('label')
    String name = 'Ada'
    Integer count = 2
}

@JsonPropertyOrder(['count', 'name'])
class MatrixOrdered {
    String name = 'Ada'
    Integer count = 2
}

class MatrixIgnored {
    String name = 'Ada'
    @JsonIgnore
    String secret = 's3cret'
}

@JsonIgnoreProperties(['secret'])
class MatrixIgnoredProperties {
    String name = 'Ada'
    String secret = 's3cret'
}

class MatrixAnyField {
    String name = 'Ada'
    @JsonAnyGetter
    Map<String, Object> attributes = [colour: 'red', size: 'L']
}

class MatrixAnyMethod {
    String name = 'Ada'

    @JsonAnyGetter
    Map<String, Object> getExtras() {
        [colour: 'red']
    }
}

class MatrixAddress {
    String street = 'Main'
    String city = 'Springfield'
}

class MatrixUnwrapped {
    String name = 'Ada'
    @JsonUnwrapped
    MatrixAddress address = new MatrixAddress()
}

class MatrixUnwrappedPrefix {
    String name = 'Ada'
    @JsonUnwrapped(prefix = 'home_')
    MatrixAddress address = new MatrixAddress()
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = 'kind')
@JsonSubTypes([@JsonSubTypes.Type(value = MatrixCircle, name = 'circle')])
abstract class MatrixShape {
    String label = 'round'
}

class MatrixCircle extends MatrixShape {
    Integer radius = 3
}

class MatrixPropertyTyped {
    String name = 'Ada'
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = 'kind')
    @JsonSubTypes([@JsonSubTypes.Type(value = MatrixAddress, name = 'address')])
    Object payload = new MatrixAddress()
}

class MatrixTypedElements {
    String name = 'Ada'
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = 'kind')
    @JsonSubTypes([@JsonSubTypes.Type(value = MatrixAddress, name = 'address')])
    List<Object> items = [new MatrixAddress()]
}

class MatrixTypedArray {
    String name = 'Ada'
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = 'kind')
    @JsonSubTypes([@JsonSubTypes.Type(value = MatrixAddress, name = 'address')])
    Object[] items = [new MatrixAddress()] as Object[]
}

class MatrixTypedMap {
    String name = 'Ada'
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = 'kind')
    @JsonSubTypes([@JsonSubTypes.Type(value = MatrixAddress, name = 'address')])
    Map<String, Object> items = [home: new MatrixAddress()]
}

class MatrixValue {
    String code = 'A1'

    @JsonValue
    String value() {
        code
    }
}

class MatrixValueObject {
    @JsonValue
    Map<String, Object> value() {
        [code: 'A1']
    }
}

@JsonSerialize(using = MatrixNameSerializer)
class MatrixSerialized {
    String name = 'Ada'
}

class MatrixNameSerializer extends ValueSerializer<MatrixSerialized> {
    @Override
    void serialize(MatrixSerialized value, JsonGenerator generator, SerializationContext context) {
        generator.writeStartObject()
        generator.writeStringProperty('written', value.name.toUpperCase())
        generator.writeEndObject()
    }
}

class MatrixSerializable extends JacksonSerializable.Base {
    String name = 'Ada'

    @Override
    void serialize(JsonGenerator generator, SerializationContext context) {
        generator.writeStartObject()
        generator.writeStringProperty('serialized', name)
        generator.writeEndObject()
    }

    @Override
    void serializeWithType(JsonGenerator generator, SerializationContext context, TypeSerializer typeSerializer) {
        serialize(generator, context)
    }
}

class MatrixPropertySerialized {
    String name = 'Ada'
    @JsonSerialize(using = MatrixUpperSerializer)
    String title = 'grails'
}

class MatrixUpperSerializer extends ValueSerializer<String> {
    @Override
    void serialize(String value, JsonGenerator generator, SerializationContext context) {
        generator.writeString(value.toUpperCase())
    }
}

class MatrixIncluded {
    String name = 'Ada'
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String nick
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    List<String> tags = []
    Integer count = 2
}

class MatrixFormatted {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Integer count = 2
    @JsonFormat(pattern = 'yyyy-MM-dd', timezone = 'UTC')
    Date day = new Date(0)
}

@JsonFormat(shape = JsonFormat.Shape.ARRAY)
@JsonPropertyOrder(['name', 'count'])
class MatrixArrayShaped {
    String name = 'Ada'
    Integer count = 2
}

record MatrixRecord(String name, Integer count) { }

@JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator, property = '@id')
class MatrixIdentified {
    String name = 'Ada'
    Integer count = 2
}

class MatrixPublicView { }

class MatrixViewed {
    String name = 'Ada'
    @JsonView(MatrixPublicView)
    String email = 'ada@example.com'
}

class MatrixComputed {
    String name = 'Ada'

    String getGreeting() {
        "Hello ${name}"
    }
}

enum MatrixColour {
    RED

    @JsonValue
    String lower() {
        name().toLowerCase()
    }
}

enum MatrixSize {
    @JsonProperty('s')
    SMALL
}

enum MatrixLevel {
    HIGH
}

class MatrixEnums {
    MatrixColour colour = MatrixColour.RED
    MatrixSize size = MatrixSize.SMALL
    MatrixLevel level = MatrixLevel.HIGH
}

class MatrixContainers {
    Map<String, Object> data = [a: 1, b: [2, 3]]
    Optional<String> nick = Optional.of('ada')
    int[] numbers = [1, 2] as int[]
    List<MatrixAddress> addresses = [new MatrixAddress()]
}

class MatrixNested {
    String name = 'Ada'
    MatrixPlain inner = new MatrixPlain()
}

class MatrixRaw {
    String name = 'Ada'
    @JsonRawValue
    String raw = '{"a":1}'
}

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy)
class MatrixSnake {
    String firstName = 'Ada'
    Integer loginCount = 2
}

@JsonFilter('matrix')
class MatrixFiltered {
    String name = 'Ada'
    String hidden = 'h'
}

class MatrixGString {
    String name = 'Ada'
    Object greeting = "Hello ${'Ada'}"
}

@Entity
class MatrixAuthor {
    String name
}

@Entity
class MatrixBook {
    static hasMany = [reviewers: MatrixAuthor]
    String title
    Integer pages
    MatrixAuthor author
    List<MatrixAuthor> reviewers

    static MatrixBook sample() {
        def author = new MatrixAuthor(name: 'Ada')
        author.id = 7
        def book = new MatrixBook(title: 'Grails', pages: 2, author: author, reviewers: [author])
        book.id = 1
        book
    }
}

@Entity
class MatrixRenamedBook {
    @JsonProperty('label')
    String title
    Integer pages
}

@Entity
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy)
class MatrixSnakeBook {
    String bookTitle
    Integer pageCount
}

@Entity
class MatrixAttributedBook {
    String title
    @JsonAnyGetter
    Map<String, String> attributes
}

@Entity
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = 'kind')
@JsonTypeName('typed')
class MatrixTypedBook {
    String title
}

@Entity
class MatrixIgnoringBook {
    String title
    @JsonIgnore
    String secret
}

@Entity
class MatrixIncludingBook {
    String title
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String nick
}

@Entity
class MatrixCodedBook {
    String code

    @JsonValue
    String value() {
        code
    }
}

@Entity
class MatrixViewedBook {
    String title
    @JsonView(MatrixPublicView)
    String email
}

@Entity
class MatrixDatedBook {
    String title
    @JsonFormat(pattern = 'yyyy-MM-dd', timezone = 'UTC')
    Date published
}

@Entity
@JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator, property = '@id')
class MatrixIdentifiedBook {
    String title
}

class MatrixPlainSerializer extends ValueSerializer<MatrixPlain> {
    @Override
    void serialize(MatrixPlain value, JsonGenerator generator, SerializationContext context) {
        generator.writeStartObject()
        generator.writeStringProperty('plain', value.name)
        generator.writeEndObject()
    }
}

class MatrixAuthorSerializer extends ValueSerializer<MatrixAuthor> {
    @Override
    void serialize(MatrixAuthor value, JsonGenerator generator, SerializationContext context) {
        generator.writeString("author:${value.name}".toString())
    }
}

@Entity
class MatrixVolume {
    String title
}

@Entity
class MatrixShelf {
    static hasMany = [volumes: MatrixVolume]
    String name
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = 'kind')
    @JsonSubTypes([@JsonSubTypes.Type(value = MatrixVolume, name = 'volume')])
    MatrixVolume featured
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = 'kind')
    @JsonSubTypes([@JsonSubTypes.Type(value = MatrixVolume, name = 'volume')])
    List<MatrixVolume> volumes

    static MatrixShelf sample() {
        def volume = new MatrixVolume(title: 'Grails')
        volume.id = 2
        def shelf = new MatrixShelf(name: 'top', featured: volume, volumes: [volume])
        shelf.id = 1
        shelf
    }
}
