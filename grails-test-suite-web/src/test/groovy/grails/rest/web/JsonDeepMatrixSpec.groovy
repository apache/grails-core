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

import spock.lang.Specification
import tools.jackson.databind.json.JsonMapper

import grails.converters.JSON
import org.grails.datastore.mapping.proxy.EntityProxy
import grails.converters.json.NamedJsonConfigurationRegistry
import grails.persistence.Entity
import grails.testing.gorm.DataTest
import grails.testing.web.controllers.ControllerUnitTest

/**
 * Writes domain class instances whose associations are written in full through each way an application writes JSON,
 * and compares what each writes with what the Grails 8 JSON converter wrote, which a spec with
 * {@code grails.converters.json.legacy} set writes: the same properties, in the order Jackson writes those of a domain
 * class, which every way of writing JSON writes alike. Each spec that extends it implements {@code ControllerUnitTest},
 * which finds the controller class in the generic type of the spec's own declaration.
 */
abstract class JsonDeepMatrix extends Specification {

    static final String RESPOND = 'respond'
    static final String RESPOND_NAMED = 'respond with a named configuration'
    static final String RENDER = 'render json:'
    static final String RENDER_NAMED = 'render json: with a named configuration'
    static final String CONVERTER = 'the JSON converter'
    static final String REGISTRY = 'the named configuration registry'

    static final Class<?>[] DOMAIN_CLASSES = [MatrixAuthor, MatrixBook, DeepNode, MatrixShelf, MatrixVolume] as Class<?>[]

    abstract List<String> paths()

    /**
     * @return the name of the configuration that writes associations in full, or {@code null} where every one does
     */
    abstract String deepConfiguration()

    /**
     * @return whether the JSON converter of the spec is the legacy one, which renders as Grails 8 did
     */
    boolean legacy() {
        false
    }

    static Closure configured(boolean legacy, boolean defaultDeep) {
        { config ->
            config['grails.web.rendering.json.spring'] = true
            config['grails.converters.json.legacy'] = legacy
            config['grails.converters.json.default.deep'] = defaultDeep
        }
    }

    void setup() {
        NamedJsonConfigurationRegistry registry = applicationContext.getBean(NamedJsonConfigurationRegistry)
        if (!registry.contains('matrix')) {
            registry.register('matrix') { }
        }
    }

    void '#fixture written in full through #path is written as Grails 8 wrote it'() {
        given:
        Object value = fixture.instance.call()
        String expected = fixture.expected.call(value)

        when:
        String written = write(path, value)

        then: 'Grails 8 ordered the properties of a domain class otherwise'
        legacy() ? sameObjects(written, expected) : canonical(written) == canonical(expected)

        where:
        [fixture, path] << [DeepFixture.ALL.findAll { !legacy() || it.legacy }, paths()].combinations()
    }

    private static String canonical(String json) {
        JsonMapper.builder().build().readTree(json).toString()
    }

    private static boolean sameObjects(String written, String expected) {
        JsonMapper mapper = JsonMapper.builder().build()
        mapper.readTree(written) == mapper.readTree(expected)
    }

    private String write(String path, Object value) {
        String named = deepConfiguration() ?: 'matrix'
        if (path == CONVERTER) {
            return deepConfiguration() ? JSON.use('deep') { new JSON(value).toString() } : new JSON(value).toString()
        }
        if (path == REGISTRY) {
            return applicationContext.getBean(NamedJsonConfigurationRegistry).writeValueAsString(named, value)
        }
        response.reset()
        response.format = 'json'
        switch (path) {
            case RESPOND:
                controller.respond(value, deepConfiguration() ? [jsonConfiguration: named] : [:])
                break
            case RESPOND_NAMED:
                controller.respond(value, [jsonConfiguration: named])
                break
            case RENDER:
                controller.render(deepConfiguration() ? [json: value, jsonConfiguration: named] : [json: value])
                break
            case RENDER_NAMED:
                controller.render(json: value, jsonConfiguration: named)
                break
            default:
                throw new IllegalArgumentException(path)
        }
        response.contentAsString
    }
}

/**
 * With {@code grails.converters.json.default.deep}, which writes associations in full wherever JSON is written.
 */
class DefaultDeepMatrixSpec extends JsonDeepMatrix implements ControllerUnitTest<JsonMatrixController>, DataTest {
    Closure doWithConfig() { configured(false, true) }
    Class<?>[] getDomainClassesToMock() { DOMAIN_CLASSES }
    List<String> paths() { [RESPOND, RESPOND_NAMED, RENDER, RENDER_NAMED, CONVERTER, REGISTRY] }
    String deepConfiguration() { null }
}

/**
 * With the configuration named {@code deep}, which respond, render json: and the registry select by its name, and the
 * JSON converter's configuration of the same name, which {@code JSON.use} selects.
 */
class NamedDeepMatrixSpec extends JsonDeepMatrix implements ControllerUnitTest<JsonMatrixController>, DataTest {
    Closure doWithConfig() { configured(false, false) }
    Class<?>[] getDomainClassesToMock() { DOMAIN_CLASSES }
    List<String> paths() { [RESPOND, RENDER, CONVERTER, REGISTRY] }
    String deepConfiguration() { 'deep' }
}

class LegacyDefaultDeepMatrixSpec extends JsonDeepMatrix implements ControllerUnitTest<JsonMatrixController>, DataTest {
    Closure doWithConfig() { configured(true, true) }
    Class<?>[] getDomainClassesToMock() { DOMAIN_CLASSES }
    List<String> paths() { [CONVERTER] }
    String deepConfiguration() { null }
    boolean legacy() { true }
}

class LegacyNamedDeepMatrixSpec extends JsonDeepMatrix implements ControllerUnitTest<JsonMatrixController>, DataTest {
    Closure doWithConfig() { configured(true, false) }
    Class<?>[] getDomainClassesToMock() { DOMAIN_CLASSES }
    List<String> paths() { [CONVERTER] }
    String deepConfiguration() { 'deep' }
    boolean legacy() { true }
}

/**
 * Domain class instances, and what each is written as with its associations in full, given the instance.
 */
class DeepFixture {

    static final List<DeepFixture> ALL = [
            new DeepFixture('a domain class with a to-one and a to-many association', { MatrixBook.sample() }, { MatrixBook book ->
                '{"id":1,"title":"Grails","pages":2,"author":{"id":7,"name":"Ada"},"reviewers":[{"id":7,"name":"Ada"}]}'
            }),
            new DeepFixture('a domain class whose association is a proxy that is not loaded', {
                def author = new MatrixAuthor(name: 'Ada').save(flush: true, failOnError: true)
                MatrixAuthor.withSession { it.clear() }
                def proxy = MatrixAuthor.load(author.id)
                assert proxy instanceof EntityProxy && !proxy.initialized
                def book = new MatrixBook(title: 'Grails', pages: 2, author: proxy, reviewers: [])
                book.id = 1
                book
            }, { MatrixBook book ->
                '{"id":1,"title":"Grails","pages":2,"author":{"id":' + book.author.id + ',"name":"Ada"},"reviewers":[]}'
            }),
            new DeepFixture('two domain class instances that reference each other', {
                def first = new DeepNode(name: 'a')
                first.id = 1
                def second = new DeepNode(name: 'b', parent: first)
                second.id = 2
                first.parent = second
                first
            }, { DeepNode node ->
                '{"id":1,"name":"a","parent":{"id":2,"name":"b","parent":{"_ref":"..","class":"' + DeepNode.name + '"}}}'
            }),
            new DeepFixture('a domain class instance that references itself', {
                def only = new DeepNode(name: 'a')
                only.id = 1
                only.parent = only
                only
            }, { DeepNode node ->
                '{"id":1,"name":"a","parent":{"_ref":".","class":"' + DeepNode.name + '"}}'
            }),
            new DeepFixture('a list of domain class instances that share an association', {
                def author = new MatrixAuthor(name: 'Ada')
                author.id = 7
                def first = new MatrixBook(title: 'A', pages: 1, author: author, reviewers: [])
                first.id = 1
                def second = new MatrixBook(title: 'B', pages: 2, author: author, reviewers: [])
                second.id = 2
                [first, second]
            }, { List<MatrixBook> books ->
                '[{"id":1,"title":"A","pages":1,"author":{"id":7,"name":"Ada"},"reviewers":[]},' +
                        '{"id":2,"title":"B","pages":2,"author":{"id":7,"name":"Ada"},"reviewers":[]}]'
            }),
            new DeepFixture('domain associations whose property gives their type ids', { MatrixShelf.sample() }, { MatrixShelf shelf ->
                '{"id":1,"name":"top","featured":{"kind":"volume","id":2,"title":"Grails"},' +
                        '"volumes":[{"kind":"volume","id":2,"title":"Grails"}]}'
            }, false)
    ]

    final String description
    final Closure<?> instance
    final Closure<String> expected

    /** Whether Grails 8 could write it, which it could not for what only Jackson knows, such as a type id. */
    final boolean legacy

    DeepFixture(String description, Closure<?> instance, Closure<String> expected, boolean legacy = true) {
        this.description = description
        this.instance = instance
        this.expected = expected
        this.legacy = legacy
    }

    @Override
    String toString() {
        description
    }
}

@Entity
class DeepNode {
    String name
    DeepNode parent
}
