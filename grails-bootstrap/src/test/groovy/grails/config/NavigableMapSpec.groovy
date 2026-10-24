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
package grails.config

import org.grails.config.CodeGenConfig
import org.grails.config.NavigableMap
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.environment.RestoreSystemProperties

@RestoreSystemProperties
class NavigableMapSpec extends Specification {

    void 'merge preserves profile metadata as ordinary map values'() {
        given:
        System.setProperty('spring.profiles.active', 'beta')
        def input = [
                spring: [profiles: [active: 'alpha', include: ['shared']], config: [activate: ['on-profile': 'alpha']]],
                sample: [message: 'original']
        ]
        def output = new NavigableMap()

        when:
        output.merge(input, true)

        then:
        output.get('sample.message') == 'original'
        output.get('spring.profiles.active') == 'alpha'
        output.get('spring.profiles.include') == ['shared']
        output.get('spring.config.activate.on-profile') == 'alpha'
    }

    @Unroll
    def "merge navigable map for #input "(Map input) {

        when:
        Map output = new NavigableMap()
        output.merge(input)

        then:
        output['js'] == 'text/javascript'
        output['json'] == ['application/json', 'text/json']
        output['xml'] == ['application/hal+xml', 'text/xml', 'application/xml']
        output['grails.cors.mappings[/api/**]'] == 'default'

        where:
        input << [
                [js: 'text/javascript', json: ['application/json', 'text/json'], xml: ['application/hal+xml', 'text/xml', 'application/xml'], 'grails.cors.mappings[/api/**]': 'default'],
                [js: 'text/javascript', 'json[0]': 'application/json', 'json[1]': 'text/json', 'xml[0]': 'application/hal+xml', 'xml[1]':'text/xml', 'xml[2]':'application/xml', 'grails.cors.mappings[/api/**]': 'default'],
                ]
    }

    def "multiple subscript entries are collapse to a list of maps"() {
        given:
        Map input = [
                'rabbitmq.connections[0].name': 'main',
                'rabbitmq.connections[0].host': '1109201498',
                'rabbitmq.connections[0].host2': '635494740',
                'rabbitmq.connections[0].username': 'guest',
                'rabbitmq.connections[0].password': 'guest',
        ]

        when:
        Map output = new NavigableMap()
        output.merge(input)

        then:
        output.keySet() as List<String> == ['rabbitmq.connections']
        output['rabbitmq.connections'] instanceof List
        output['rabbitmq.connections'].size() == 1
        output['rabbitmq.connections'][0].name == 'main'
        output['rabbitmq.connections'][0].host == '1109201498'
        output['rabbitmq.connections'][0].host2 == '635494740'
        output['rabbitmq.connections'][0].username == 'guest'
        output['rabbitmq.connections'][0].password == 'guest'
    }

    def "multiple subscript entries are collapse to a map of maps"() {
        given:
        Map input = [
                'rabbitmq.connections[foo].name': 'main',
                'rabbitmq.connections[foo].host': '1109201498',
                'rabbitmq.connections[foo].host2': '635494740',
                'rabbitmq.connections[foo].username': 'guest',
                'rabbitmq.connections[foo].password': 'guest',
        ]

        when:
        Map output = new NavigableMap()
        output.merge(input)

        then:
        output.keySet() as List<String> == ['rabbitmq.connections']
        output['rabbitmq.connections'] instanceof Map
        output['rabbitmq.connections'].size() == 1
        output['rabbitmq.connections']['foo'].name == 'main'
        output['rabbitmq.connections']['foo'].host == '1109201498'
        output['rabbitmq.connections']['foo'].host2 == '635494740'
        output['rabbitmq.connections']['foo'].username == 'guest'
        output['rabbitmq.connections']['foo'].password == 'guest'
    }

    def "consecutive subscripts are collapsed to nested lists"() {
        given:
        Map input = [
                'groups[0][0]': 'a',
                'groups[0][1]': 'b',
                'groups[1][0]': 'c',
                'deep[0][0][0]': 'x',
        ]

        when:
        Map output = new NavigableMap()
        output.merge(input)

        then:
        output['groups'] == [['a', 'b'], ['c']]
        output['deep'] == [[['x']]]

        and: "each element stays readable under its indexed name"
        output['groups[0][1]'] == 'b'
        output['deep[0][0][0]'] == 'x'
    }

    def "consecutive subscripts followed by a property are collapsed to a map in a nested list"() {
        given:
        Map input = [
                'items[0][1].name': 'n',
                'items[0][1].host': 'h',
                'routes[api][0]': '/a',
                'routes[api][1]': '/b',
        ]

        when:
        Map output = new NavigableMap()
        output.merge(input)

        then:
        output['items'] == [[null, [name: 'n', host: 'h']]]
        output['routes'] == [api: ['/a', '/b']]
    }

    def "dotted keys with consecutive subscripts are collapsed to nested lists"() {
        given:
        Map input = [
                'app.groups[0][0]': 'a',
                'app.groups[0][1]': 'b',
                'app.groups[1][0]': 'c',
        ]

        when:
        NavigableMap output = new NavigableMap()
        output.merge(input, true)

        then:
        output['app.groups'] == [['a', 'b'], ['c']]
        output['app.groups[1][0]'] == 'c'
        output.navigate('app', 'groups') == [['a', 'b'], ['c']]
    }

    def "a dotted key inside a nested map is flattened under its parent"() {
        given:
        NavigableMap output = new NavigableMap()

        when:
        output.merge([a: [b: 'root'], x: ['a.b': 'nested']], true)

        then: 'the dotted key is written under its parent in both representations'
        output['x.a.b'] == 'nested'
        output.navigate('x', 'a', 'b') == 'nested'

        and: 'the root key it resembles keeps its value in both representations'
        output['a.b'] == 'root'
        output.navigate('a', 'b') == 'root'
    }

    def "a block merged over a scalar replaces it in both representations"() {
        given:
        NavigableMap output = new NavigableMap()
        output.merge(['server.url': 'x'], true)

        when:
        output.merge(['server.url.port': 'y'], true)

        then: 'the flat key holds the block, as the nested entry does'
        output['server.url'] == [port: 'y']
        output.navigate('server', 'url') == [port: 'y']
        output['server.url.port'] == 'y'
    }

    def "subscript assignment on a nested map writes both representations"() {
        given:
        NavigableMap output = new NavigableMap()
        output.merge([a: [b: 'merged']], true)

        when:
        output.a['b'] = 'assigned'

        then:
        output['a.b'] == 'assigned'
        output.navigate('a', 'b') == 'assigned'
    }

    def "a nested subscript merge keeps the flat alias of the list on the same list"() {
        given:
        NavigableMap output = new NavigableMap()
        output.merge(['app.items[0]': 'old'], true)

        when:
        output.merge([app: ['items[0]': 'new']], true)

        then:
        output['app.items'] == ['new']
        output.navigate('app', 'items') == ['new']
        output['app.items[0]'] == 'new'
    }

    def "a nested dotted merge over a scalar moves the flat alias to the new block"() {
        given:
        NavigableMap output = new NavigableMap()
        output.merge(['x.a': 'old'], true)

        when:
        output.merge([x: ['a.b': 'new']], true)

        then: 'the root alias holds the block, as the nested entry does'
        output['x.a'] == [b: 'new']
        output.navigate('x', 'a') == [b: 'new']
        output['x.a.b'] == 'new'
    }

    def "a nested dotted merge writes the flat alias of every block it creates at the root"() {
        given:
        NavigableMap output = new NavigableMap()
        output.merge([x: [y: 1]], true)

        when:
        output.merge([x: ['a.b.c': 'new']], true)

        then:
        output['x.a'] == [b: [c: 'new']]
        output['x.a.b'] == [c: 'new']
        output['x.a.b.c'] == 'new'
        output.navigate('x', 'a', 'b') == [c: 'new']
    }

    def "a block recreated after a scalar replaced it moves the flat alias to the new block"() {
        given:
        NavigableMap output = new NavigableMap()
        output.merge(['a.b.c': 1], true)
        output.merge([a: 'scalar'], true)

        when:
        output.merge(['a.b.d': 2], true)

        then: 'the root alias holds the recreated block, as the nested entry does'
        output['a.b'] == [d: 2]
        output.navigate('a', 'b') == [d: 2]
        output['a.b.d'] == 2
    }

    def "a property write below an existing block writes the flat alias of the new block at the root"() {
        given:
        NavigableMap output = new NavigableMap()
        output.merge([x: [y: 1]], true)

        when:
        output.x.a.b = 'new'

        then:
        output['x.a'] == [b: 'new']
        output['x.a.b'] == 'new'
        output.navigate('x', 'a') == [b: 'new']
    }

    def "setting a nested property to null removes its flat keys and no others"() {
        given:
        NavigableMap output = new NavigableMap()
        output.merge(['a.b.c': 1, 'a.e.f': 3, 'a.e.g[0]': 4], true)

        when:
        output.a.e = null

        then: 'the flat keys of the removed property are gone'
        !output.containsKey('a.e')
        !output.containsKey('a.e.f')
        !output.containsKey('a.e.g')
        !output.containsKey('a.e.g[0]')
        output.navigate('a', 'e') == null

        and: 'those of its siblings stay'
        output['a.b'] == [c: 1]
        output['a.b.c'] == 1
        output.navigate('a', 'b', 'c') == 1
    }

    def "setting a root property to null removes the flat keys below it"() {
        given:
        NavigableMap output = new NavigableMap()
        output.merge(['a.b.c': 1, 'a.e': 2, 'x.y': 3], true)

        when:
        output.a = null

        then:
        !output.containsKey('a')
        !output.containsKey('a.b')
        !output.containsKey('a.b.c')
        !output.containsKey('a.e')
        output['x.y'] == 3
        output.navigate('x', 'y') == 3
    }

    def "a merge into a clone of an empty map leaves the original empty"() {
        given:
        NavigableMap original = new NavigableMap()
        NavigableMap copy = original.clone()

        when:
        copy.merge(['new.block.value': 1], true)

        then:
        original.isEmpty()
        copy['new.block'] == [value: 1]
        copy.navigate('new', 'block') == [value: 1]
    }

    def "a clone is a configuration of its own"() {
        given:
        NavigableMap original = new NavigableMap()
        original.merge([a: [b: [c: 1]]], true)

        when:
        NavigableMap copy = original.clone()

        then: 'the copy holds the same entries'
        copy == original
        !copy.is(original)

        when: 'the copy is written through every path'
        copy.a.b.d = 2
        copy.merge(['a.e.f': 3], true)
        copy.put('g', 4)

        then: 'the copy sees the writes in both representations, on one map'
        copy['a.b'] == [c: 1, d: 2]
        copy['a.b'].is(copy.navigate('a', 'b'))
        copy['a.e'] == [f: 3]
        copy.navigate('a', 'e') == [f: 3]
        copy['g'] == 4

        and: 'the original sees none of them'
        original['a.b'] == [c: 1]
        original.navigate('a', 'b') == [c: 1]
        !original.containsKey('a.b.d')
        !original.containsKey('a.e')
        !original.containsKey('g')
        original.navigate('a', 'e') == null
    }

    def "a clone copies the lists and the list elements of indexed keys"() {
        given:
        NavigableMap original = new NavigableMap()
        original.merge(['a.items[0]': 'first', 'a.rows[0].name': 'row'], true)

        when:
        NavigableMap copy = original.clone()
        copy.merge(['a.items[0]': 'changed', 'a.items[1]': 'added', 'a.rows[0].name': 'renamed'], true)

        then: 'the copy reads the writes through both representations, on one list'
        copy['a.items'] == ['changed', 'added']
        copy['a.items'].is(copy.navigate('a', 'items'))
        copy['a.rows'] == [[name: 'renamed']]

        and: 'the original keeps its lists'
        original['a.items'] == ['first']
        original.navigate('a', 'items') == ['first']
        original['a.items[0]'] == 'first'
        original['a.rows'] == [[name: 'row']]
    }

    def "a dotted key inside a nested map writes the root flat key and the nested entry only"() {
        given:
        NavigableMap output = new NavigableMap()

        when:
        output.merge([a: ['b.c': 'nested']], true)
        output.merge(['a.b.c': 'flat'], true)

        then: 'the sub-map holds the nested entry and no copy of the dotted key'
        output.navigate('a') == [b: [c: 'flat']]
        output['a.b.c'] == 'flat'
        output['a.b'] == [c: 'flat']
    }

    def "navigate stops at a value that is not a map"() {
        given:
        NavigableMap output = new NavigableMap()
        output.merge([app: [name: 'demo', ports: [8080, 8443]]], true)

        expect: 'a path that ends on the value answers with it'
        output.navigate('app', 'name') == 'demo'
        output.navigate('app', 'ports') == [8080, 8443]

        and: 'a path that continues past it is absent rather than the value'
        output.navigate('app', 'name', 'deeper') == null
        output.navigate('app', 'ports', '0') == null
        new CodeGenConfig(output).navigate(String, 'app', 'name', 'deeper') == null
    }
}
