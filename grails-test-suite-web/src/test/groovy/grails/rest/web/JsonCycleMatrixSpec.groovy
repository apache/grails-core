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

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.annotation.JsonTypeInfo
import spock.lang.Specification
import tools.jackson.databind.json.JsonMapper

import grails.converters.JSON
import grails.converters.json.NamedJsonConfigurationRegistry
import grails.testing.web.controllers.ControllerUnitTest
import org.grails.web.converters.Converter.CircularReferenceBehaviour

/**
 * Writes beans that reach an object again while it is being written, through each way an application writes JSON, with
 * each circular reference behaviour, and compares what each writes with what the Grails 8 JSON converter wrote, which a
 * spec with {@code grails.converters.json.legacy} set writes. Each spec that extends it implements
 * {@code ControllerUnitTest}, which finds the controller class in the generic type of the spec's own declaration, and
 * selects the behaviour in its {@code doWithConfig}.
 */
abstract class JsonCycleMatrix extends Specification {

    static final String RESPOND = 'respond'
    static final String RESPOND_NAMED = 'respond with a named configuration'
    static final String RENDER = 'render json:'
    static final String RENDER_NAMED = 'render json: with a named configuration'
    static final String CONVERTER = 'the JSON converter'
    static final String REGISTRY = 'the named configuration registry'

    static final List<String> NON_LEGACY_PATHS = [RESPOND, RESPOND_NAMED, RENDER, RENDER_NAMED, CONVERTER, REGISTRY]

    abstract CircularReferenceBehaviour behaviour()

    /**
     * @return whether the JSON converter of the spec is the legacy one, which renders as Grails 8 did
     */
    abstract boolean legacy()

    static Closure configured(boolean legacy, CircularReferenceBehaviour behaviour) {
        { config ->
            config['grails.web.rendering.json.spring'] = true
            config['grails.converters.json.legacy'] = legacy
            config['grails.converters.json.circular.reference.behaviour'] = behaviour.name()
        }
    }

    void setup() {
        NamedJsonConfigurationRegistry registry = applicationContext.getBean(NamedJsonConfigurationRegistry)
        if (!registry.contains('matrix')) {
            registry.register('matrix') { }
        }
    }

    void '#fixture written through #path with the #behaviourName circular reference behaviour is written as Grails 8 wrote it'() {
        given:
        String expected = fixture.expected[behaviour()]

        when:
        String written = attempt(path, fixture.instance.call())

        then: 'nothing, where the behaviour fails the write'
        expected == null ? written.startsWith('threw ') : equalJson(written, expected)

        where:
        [fixture, path] << rows()
        behaviourName = behaviour().name()
    }

    List<List<Object>> rows() {
        List<List<Object>> rows = []
        for (CycleFixture fixture : CycleFixture.ALL) {
            if (legacy()) {
                if (fixture.legacy) {
                    rows << [fixture, CONVERTER]
                }
            }
            else {
                NON_LEGACY_PATHS.each { rows << [fixture, it] }
            }
        }
        rows
    }

    /** The same JSON, in the same order, whatever the spacing and the escapes of the text. */
    private boolean equalJson(String written, String expected) {
        if (written.startsWith('threw ')) {
            return false
        }
        JsonMapper mapper = JsonMapper.builder().build()
        mapper.readTree(written).toString() == mapper.readTree(expected).toString()
    }

    private String attempt(String path, Object value) {
        try {
            write(path, value)
        }
        catch (Throwable failure) {
            "threw ${failure}".toString()
        }
    }

    private String write(String path, Object value) {
        if (path == CONVERTER) {
            return new JSON(value).toString()
        }
        if (path == REGISTRY) {
            return applicationContext.getBean(NamedJsonConfigurationRegistry).writeValueAsString('matrix', value)
        }
        response.reset()
        response.format = 'json'
        switch (path) {
            case RESPOND:
                controller.respond(value)
                break
            case RESPOND_NAMED:
                controller.respond(value, [jsonConfiguration: 'matrix'])
                break
            case RENDER:
                controller.render(json: value)
                break
            case RENDER_NAMED:
                controller.render(json: value, jsonConfiguration: 'matrix')
                break
            default:
                throw new IllegalArgumentException(path)
        }
        response.contentAsString
    }
}

class DefaultCycleMatrixSpec extends JsonCycleMatrix implements ControllerUnitTest<JsonMatrixController> {
    Closure doWithConfig() { configured(false, behaviour()) }
    CircularReferenceBehaviour behaviour() { CircularReferenceBehaviour.DEFAULT }
    boolean legacy() { false }
}

class ExceptionCycleMatrixSpec extends JsonCycleMatrix implements ControllerUnitTest<JsonMatrixController> {
    Closure doWithConfig() { configured(false, behaviour()) }
    CircularReferenceBehaviour behaviour() { CircularReferenceBehaviour.EXCEPTION }
    boolean legacy() { false }
}

class InsertNullCycleMatrixSpec extends JsonCycleMatrix implements ControllerUnitTest<JsonMatrixController> {
    Closure doWithConfig() { configured(false, behaviour()) }
    CircularReferenceBehaviour behaviour() { CircularReferenceBehaviour.INSERT_NULL }
    boolean legacy() { false }
}

class IgnoreCycleMatrixSpec extends JsonCycleMatrix implements ControllerUnitTest<JsonMatrixController> {
    Closure doWithConfig() { configured(false, behaviour()) }
    CircularReferenceBehaviour behaviour() { CircularReferenceBehaviour.IGNORE }
    boolean legacy() { false }
}

class PathCycleMatrixSpec extends JsonCycleMatrix implements ControllerUnitTest<JsonMatrixController> {
    Closure doWithConfig() { configured(false, behaviour()) }
    CircularReferenceBehaviour behaviour() { CircularReferenceBehaviour.PATH }
    boolean legacy() { false }
}

class LegacyDefaultCycleMatrixSpec extends JsonCycleMatrix implements ControllerUnitTest<JsonMatrixController> {
    Closure doWithConfig() { configured(true, behaviour()) }
    CircularReferenceBehaviour behaviour() { CircularReferenceBehaviour.DEFAULT }
    boolean legacy() { true }
}

class LegacyExceptionCycleMatrixSpec extends JsonCycleMatrix implements ControllerUnitTest<JsonMatrixController> {
    Closure doWithConfig() { configured(true, behaviour()) }
    CircularReferenceBehaviour behaviour() { CircularReferenceBehaviour.EXCEPTION }
    boolean legacy() { true }
}

class LegacyInsertNullCycleMatrixSpec extends JsonCycleMatrix implements ControllerUnitTest<JsonMatrixController> {
    Closure doWithConfig() { configured(true, behaviour()) }
    CircularReferenceBehaviour behaviour() { CircularReferenceBehaviour.INSERT_NULL }
    boolean legacy() { true }
}

class LegacyPathCycleMatrixSpec extends JsonCycleMatrix implements ControllerUnitTest<JsonMatrixController> {
    Closure doWithConfig() { configured(true, behaviour()) }
    CircularReferenceBehaviour behaviour() { CircularReferenceBehaviour.PATH }
    boolean legacy() { true }
}

/**
 * A value that reaches an object again while it is being written, and what each circular reference behaviour writes for
 * it, or {@code null} where the behaviour fails the write.
 */
class CycleFixture {

    static final List<CycleFixture> ALL = [
            new CycleFixture('two beans that reference each other', {
                def a = new CycleNode(name: 'a')
                a.next = new CycleNode(name: 'b', next: a)
                a
            }, [
                    (CircularReferenceBehaviour.DEFAULT): '{"name":"a","next":{"name":"b","next":' + ref('..', CycleNode) + '}}',
                    (CircularReferenceBehaviour.EXCEPTION): null,
                    (CircularReferenceBehaviour.INSERT_NULL): '{"name":"a","next":{"name":"b","next":null}}',
                    (CircularReferenceBehaviour.IGNORE): '{"name":"a","next":{"name":"b","next":null}}',
                    (CircularReferenceBehaviour.PATH): '{"name":"a","next":{"name":"b","next":' + path('root', CycleNode) + '}}'
            ]),
            new CycleFixture('a bean that references itself', {
                def a = new CycleNode(name: 'a')
                a.next = a
                a
            }, [
                    (CircularReferenceBehaviour.DEFAULT): '{"name":"a","next":' + ref('.', CycleNode) + '}',
                    (CircularReferenceBehaviour.EXCEPTION): null,
                    (CircularReferenceBehaviour.INSERT_NULL): '{"name":"a","next":null}',
                    (CircularReferenceBehaviour.IGNORE): '{"name":"a","next":null}',
                    (CircularReferenceBehaviour.PATH): '{"name":"a","next":' + path('root', CycleNode) + '}'
            ]),
            new CycleFixture('a bean in a list of the bean that holds it', {
                def owner = new CycleOwner(name: 'o')
                owner.items = [new CycleItem(name: 'i', owner: owner)]
                owner
            }, [
                    (CircularReferenceBehaviour.DEFAULT): '{"items":[{"name":"i","owner":' + ref('../..', CycleOwner) + '}],"name":"o"}',
                    (CircularReferenceBehaviour.EXCEPTION): null,
                    (CircularReferenceBehaviour.INSERT_NULL): '{"items":[{"name":"i","owner":null}],"name":"o"}',
                    (CircularReferenceBehaviour.IGNORE): '{"items":[{"name":"i","owner":null}],"name":"o"}',
                    (CircularReferenceBehaviour.PATH): '{"items":[{"name":"i","owner":' + path('root', CycleOwner) + '}],"name":"o"}'
            ]),
            new CycleFixture('a bean in a map of the bean that holds it', {
                def keeper = new CycleKeeper(name: 'k')
                keeper.byName = [x: new CycleKept(name: 'x', keeper: keeper)]
                keeper
            }, [
                    (CircularReferenceBehaviour.DEFAULT): '{"byName":{"x":{"keeper":' + ref('../..', CycleKeeper) + ',"name":"x"}},"name":"k"}',
                    (CircularReferenceBehaviour.EXCEPTION): null,
                    (CircularReferenceBehaviour.INSERT_NULL): '{"byName":{"x":{"keeper":null,"name":"x"}},"name":"k"}',
                    (CircularReferenceBehaviour.IGNORE): '{"byName":{"x":{"keeper":null,"name":"x"}},"name":"k"}',
                    (CircularReferenceBehaviour.PATH): '{"byName":{"x":{"keeper":' + path('root', CycleKeeper) + ',"name":"x"}},"name":"k"}'
            ]),
            new CycleFixture('a bean that is an element of its own list', {
                def group = new CycleGroup(name: 'g')
                group.members = [group, new CycleNode(name: 'x')]
                group
            }, [
                    (CircularReferenceBehaviour.DEFAULT): '{"members":[' + ref('..', CycleGroup) + ',{"name":"x","next":null}],"name":"g"}',
                    (CircularReferenceBehaviour.EXCEPTION): null,
                    (CircularReferenceBehaviour.INSERT_NULL): '{"members":[null,{"name":"x","next":null}],"name":"g"}',
                    (CircularReferenceBehaviour.IGNORE): '{"members":[{"name":"x","next":null}],"name":"g"}',
                    (CircularReferenceBehaviour.PATH): '{"members":[' + path('root', CycleGroup) + ',{"name":"x","next":null}],"name":"g"}'
            ]),
            new CycleFixture('a list of two beans that reference each other', {
                def a = new CycleNode(name: 'a')
                def b = new CycleNode(name: 'b', next: a)
                a.next = b
                [a, b]
            }, [
                    (CircularReferenceBehaviour.DEFAULT): '[{"name":"a","next":{"name":"b","next":' + ref('..', CycleNode) + '}},' +
                            '{"name":"b","next":{"name":"a","next":' + ref('..', CycleNode) + '}}]',
                    (CircularReferenceBehaviour.EXCEPTION): null,
                    (CircularReferenceBehaviour.INSERT_NULL): '[{"name":"a","next":{"name":"b","next":null}},{"name":"b","next":{"name":"a","next":null}}]',
                    (CircularReferenceBehaviour.IGNORE): '[{"name":"a","next":{"name":"b","next":null}},{"name":"b","next":{"name":"a","next":null}}]',
                    (CircularReferenceBehaviour.PATH): '[{"name":"a","next":{"name":"b","next":' + path('root[0]', CycleNode) + '}},' +
                            '{"name":"b","next":{"name":"a","next":' + path('root[1]', CycleNode) + '}}]'
            ]),
            new CycleFixture('a bean twice in a list, which is not a cycle', {
                def a = new CycleNode(name: 'a')
                [a, a]
            }, CircularReferenceBehaviour.values().collectEntries { [(it): '[{"name":"a","next":null},{"name":"a","next":null}]'] }),
            new CycleFixture('an array-shaped bean that references itself', {
                def row = new CycleRow(name: 'r')
                row.parent = row
                row
            }, [
                    (CircularReferenceBehaviour.DEFAULT): '["r",' + ref('.', CycleRow) + ']',
                    (CircularReferenceBehaviour.EXCEPTION): null,
                    (CircularReferenceBehaviour.INSERT_NULL): '["r",null]',
                    (CircularReferenceBehaviour.IGNORE): '["r",null]',
                    (CircularReferenceBehaviour.PATH): '["r",' + path('root', CycleRow) + ']'
            ], false),
            new CycleFixture('a bean that references itself through an Optional', {
                def node = new CycleOptionalNode(name: 'a')
                node.next = Optional.of(node)
                node
            }, [
                    (CircularReferenceBehaviour.DEFAULT): '{"name":"a","next":' + ref('.', CycleOptionalNode) + '}',
                    (CircularReferenceBehaviour.EXCEPTION): null,
                    (CircularReferenceBehaviour.INSERT_NULL): '{"name":"a","next":null}',
                    (CircularReferenceBehaviour.IGNORE): '{"name":"a","next":null}',
                    (CircularReferenceBehaviour.PATH): '{"name":"a","next":' + path('root', CycleOptionalNode) + '}'
            ], false),
            new CycleFixture('a bean with a wrapper type id that references itself', {
                def node = new CycleWrappedNode(name: 'a')
                node.next = node
                node
            }, [
                    (CircularReferenceBehaviour.DEFAULT): '{"CycleWrappedNode":{"name":"a","next":' + ref('..', CycleWrappedNode) + '}}',
                    (CircularReferenceBehaviour.EXCEPTION): null,
                    (CircularReferenceBehaviour.INSERT_NULL): '{"CycleWrappedNode":{"name":"a","next":null}}',
                    (CircularReferenceBehaviour.IGNORE): '{"CycleWrappedNode":{"name":"a","next":null}}',
                    (CircularReferenceBehaviour.PATH): '{"CycleWrappedNode":{"name":"a","next":' + path('root', CycleWrappedNode) + '}}'
            ], false),
            new CycleFixture('two beans with wrapper type ids that reference each other', {
                def node = new CycleWrappedNode(name: 'a')
                node.next = new CycleWrappedNode(name: 'b', next: node)
                node
            }, [
                    (CircularReferenceBehaviour.DEFAULT): '{"CycleWrappedNode":{"name":"a","next":{"CycleWrappedNode":{"name":"b","next":' + ref('../../..', CycleWrappedNode) + '}}}}',
                    (CircularReferenceBehaviour.EXCEPTION): null,
                    (CircularReferenceBehaviour.INSERT_NULL): '{"CycleWrappedNode":{"name":"a","next":{"CycleWrappedNode":{"name":"b","next":null}}}}',
                    (CircularReferenceBehaviour.IGNORE): '{"CycleWrappedNode":{"name":"a","next":{"CycleWrappedNode":{"name":"b","next":null}}}}',
                    (CircularReferenceBehaviour.PATH): '{"CycleWrappedNode":{"name":"a","next":{"CycleWrappedNode":{"name":"b","next":' + path('root', CycleWrappedNode) + '}}}}'
            ], false),
            new CycleFixture('a bean with an array wrapper type id in a list of the bean that holds it', {
                def owner = new CycleArrayWrappedOwner(name: 'o')
                owner.items = [new CycleArrayWrappedItem(name: 'i', owner: owner)]
                owner
            }, [
                    (CircularReferenceBehaviour.DEFAULT): '{"items":[["CycleArrayWrappedItem",{"name":"i","owner":' + ref('../../..', CycleArrayWrappedOwner) + '}]],"name":"o"}',
                    (CircularReferenceBehaviour.EXCEPTION): null,
                    (CircularReferenceBehaviour.INSERT_NULL): '{"items":[["CycleArrayWrappedItem",{"name":"i","owner":null}]],"name":"o"}',
                    (CircularReferenceBehaviour.IGNORE): '{"items":[["CycleArrayWrappedItem",{"name":"i","owner":null}]],"name":"o"}',
                    (CircularReferenceBehaviour.PATH): '{"items":[["CycleArrayWrappedItem",{"name":"i","owner":' + path('root', CycleArrayWrappedOwner) + '}]],"name":"o"}'
            ], false),
            new CycleFixture('a bean holding another that is equal to it, which is not a cycle', {
                new CycleEqualNode(name: 'a', next: new CycleEqualNode(name: 'a'))
            }, CircularReferenceBehaviour.values().collectEntries {
                [(it): '{"name":"a","next":{"name":"a","next":null}}']
            }, false)
    ]

    private static String ref(String levels, Class<?> type) {
        '{"_ref":"' + levels + '","class":"' + type.name + '"}'
    }

    private static String path(String path, Class<?> type) {
        '{"ref":"' + path + '","class":"' + type.name + '"}'
    }

    final String description
    final Closure<?> instance
    final Map<CircularReferenceBehaviour, String> expected

    /** Whether Grails 8 could write it, which it could not for a shape only Jackson knows. */
    final boolean legacy

    CycleFixture(String description, Closure<?> instance, Map<CircularReferenceBehaviour, String> expected,
            boolean legacy = true) {
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

class CycleNode {
    String name
    CycleNode next
}

class CycleOwner {
    String name
    List<CycleItem> items
}

class CycleGroup {
    String name
    List<Object> members
}

class CycleItem {
    String name
    CycleOwner owner
}

class CycleKeeper {
    String name
    Map<String, CycleKept> byName
}

class CycleKept {
    String name
    CycleKeeper keeper
}

@JsonFormat(shape = JsonFormat.Shape.ARRAY)
@JsonPropertyOrder(['name', 'parent'])
class CycleRow {
    String name
    CycleRow parent
}

class CycleOptionalNode {
    String name
    Optional<CycleOptionalNode> next
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_OBJECT)
class CycleWrappedNode {
    String name
    CycleWrappedNode next
}

class CycleArrayWrappedOwner {
    String name
    List<CycleArrayWrappedItem> items
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_ARRAY)
class CycleArrayWrappedItem {
    String name
    CycleArrayWrappedOwner owner
}

class CycleEqualNode {
    String name
    CycleEqualNode next

    @Override
    boolean equals(Object other) {
        other instanceof CycleEqualNode && other.name == name
    }

    @Override
    int hashCode() {
        name.hashCode()
    }
}
