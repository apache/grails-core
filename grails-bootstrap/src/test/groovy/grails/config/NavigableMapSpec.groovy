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

    def "navigate stops at a value that is not a map, and reads a list by index"() {
        given:
        NavigableMap output = new NavigableMap()
        output.merge([app: [name: 'demo', ports: [8080, 8443], rows: [[name: 'first']], codes: ['0': 'zero']]], true)

        expect: 'a path that ends on the value answers with it'
        output.navigate('app', 'name') == 'demo'
        output.navigate('app', 'ports') == [8080, 8443]

        and: 'a path that continues past a scalar is absent rather than the value'
        output.navigate('app', 'name', 'deeper') == null
        new CodeGenConfig(output).navigate(String, 'app', 'name', 'deeper') == null

        and: 'a whole number after a list names its element, as in Spring Boot, and so does an index after its name'
        output.navigate('app', 'ports', '0') == 8080
        output.navigate('app', 'ports[1]') == 8443
        output.navigate('app', 'rows', '0', 'name') == 'first'

        and: 'any other segment after a list is absent'
        output.navigate('app', 'ports', '2') == null
        output.navigate('app', 'ports', '-1') == null
        output.navigate('app', 'ports', '+1') == null
        output.navigate('app', 'ports', '01') == null
        output.navigate('app', 'ports[01]') == null
        output.navigate('app', 'ports', 'first') == null
        output.navigate('app', 'ports', '0', 'deeper') == null

        and: 'a map with a key that is a number reads it as before'
        output.navigate('app', 'codes', '0') == 'zero'
    }

    def "a dotted key inside a nested map is merged under the path of that map"() {
        given:
        NavigableMap output = new NavigableMap()

        when: 'a nested map holds a dotted key that, alone, names another property'
        output.merge([a: [b: 'root'], x: ['a.b': 'nested']], true)

        then: 'the other property keeps its value, read as a flat key and through the nested maps'
        output['a.b'] == 'root'
        output.navigate('a', 'b') == 'root'

        and: 'the dotted key is read under the path of the map that holds it'
        output['x.a.b'] == 'nested'
        output.navigate('x', 'a', 'b') == 'nested'
    }

    def "a dotted key inside a nested map holding #description writes the flat keys of its full path only"() {
        given:
        NavigableMap output = new NavigableMap()

        when:
        output.merge(input, true)

        then:
        output.keySet() == rootKeys as Set

        where:
        description             | input                          | rootKeys
        'a map'                 | [x: ['a.b': [c: 'v']]]         | ['x', 'x.a.b', 'x.a.b.c']
        'a map with a dot'      | [x: ['a.b': ['c.d': 'v']]]     | ['x', 'x.a.b', 'x.a.b.c.d']
        'a value, two dots in'  | [x: ['a.b.c': 'v']]            | ['x', 'x.a.b.c']
        'a value'               | [a: [c: '1'], x: ['a.b': 'v']] | ['a', 'a.c', 'x', 'x.a.b']
    }

    def "the flat key of a map under a dotted key inside a nested map is the map the nested maps hold"() {
        given:
        NavigableMap output = new NavigableMap()

        when:
        output.merge([x: ['a.b': [c: 'v']]], true)

        then:
        output['x.a.b'].is(output.navigate('x', 'a', 'b'))
        output.navigate('x', 'a', 'b', 'c') == 'v'
    }

    def "a dotted key inside a nested map writes no key at the root"() {
        given:
        NavigableMap output = new NavigableMap()

        when:
        output.merge([logging: [level: ['org.springframework': 'DEBUG']]], true)

        then:
        output['logging.level.org.springframework'] == 'DEBUG'
        !output.containsKey('org.springframework')
        !output.keySet().contains('org.springframework')
    }
}
