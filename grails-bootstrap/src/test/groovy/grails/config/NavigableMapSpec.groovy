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

    def "an indexed key with a property after the index builds the list in the nested map, written #description"() {
        given:
        NavigableMap output = new NavigableMap()

        when:
        output.merge(input, true)

        then: 'the nested map holds the list, so navigation reads it'
        output.navigate(path as String[]) == [[name: 'row', port: '8080']]

        where:
        description                  | input                                                                 | path
        'as a dotted key'            | ['a.rows[0].name': 'row', 'a.rows[0].port': '8080']                   | ['a', 'rows']
        'as a dotted key two deep'   | ['a.b.rows[0].name': 'row', 'a.b.rows[0].port': '8080']               | ['a', 'b', 'rows']
    }

    def "a map written by an indexed key with a property after the index keeps its entries when a dotted key is merged after it"() {
        given:
        NavigableMap output = new NavigableMap()

        when:
        output.merge(['a.mappings[foo].name': 'x'], true)
        output.merge(['a.mappings.bar': 'y'], true)

        then:
        output.navigate('a', 'mappings') == [foo: [name: 'x'], bar: 'y']
    }

    def "a list replaced after an indexed key wrote it gets no old element back from a later indexed key, replaced #description"() {
        given:
        NavigableMap output = new NavigableMap()

        when:
        output.merge(['a.rows[0].name': 'old'], true)
        output.merge(replacement, true)
        output.merge(['a.rows[0].port': '8080'], true)

        then:
        output.navigate('a', 'rows') == [[name: 'new', port: '8080']]
        output.get('a.rows') == [[name: 'new', port: '8080']]

        where:
        description       | replacement
        'as a dotted key' | ['a.rows': [[name: 'new']]]
        'in a nested map' | [a: [rows: [[name: 'new']]]]
    }

    def "an indexed key written into a map inside an element keeps the other entries of that map"() {
        given:
        NavigableMap output = new NavigableMap()

        when:
        output.merge(['a.rows': [[db: [url: 'jdbc:new', user: 'me']]]], true)
        output.merge(['a.rows[0].db.port': '8080'], true)

        then:
        output.navigate('a', 'rows')[0].db == [url: 'jdbc:new', user: 'me', port: '8080']

        and: 'the flat keys of the element and of the map inside it hold the maps the list holds'
        output.get('a.rows[0]').is(output.navigate('a', 'rows')[0])
        output.get('a.rows[0].db').is(output.navigate('a', 'rows')[0].db)
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

    def "a dotted property after a map subscript is written into the nested map the subscript names, written #description"() {
        given:
        NavigableMap output = new NavigableMap()

        when:
        output.merge(written, true)
        output.merge(['a[b].c.d': 'new'], true)

        then: 'the nested map and its flat key hold the new value'
        output.navigate('a', 'b', 'c', 'd') == 'new'
        output.get('a.b.c.d') == 'new'

        and: 'the flattened config holds it once, under the bracketed name, and the old value nowhere'
        output.toFlatConfig().get('a[b].c.d') == 'new'
        !output.toFlatConfig().containsKey('a.b.c.d')
        !output.toFlatConfig().containsValue('old')

        where:
        description          | written
        'after a dotted key' | ['a.b.c.d': 'old']
        'after a nested map' | [a: [b: [c: [d: 'old']]]]
    }

    def "a field of a map key in brackets written again holds the last value written, #key, written #description"() {
        given:
        NavigableMap output = new NavigableMap()

        when:
        output.merge([("a.mappings[${key}].maxAge".toString()): '10', ("a.mappings[${key}].allowCredentials".toString()): 'true'], true)
        output.merge([("a.mappings[${key}].maxAge".toString()): value], true)

        then: 'the held map, the flat key of the map and the flat key of the field all hold the last value'
        output.navigate('a', 'mappings')[key].maxAge == value
        output.navigate('a', 'mappings')[key].allowCredentials == 'true'
        output.get('a.mappings')[key].maxAge == value
        output.get("a.mappings[${key}].maxAge".toString()) == value

        and: 'the properties hold the last value under every key of the field, and none when it is null'
        output.toProperties().findAll { it.key.toString().endsWith('maxAge') }.values().toSet() == (value == null ? [] : [value]).toSet()

        where:
        [key, description, value] << [['/api/**', '/api/v1.0/**'], [['as a value', '20'], ['as null', null]]].combinations().collect { String key, List row ->
            [key, row[0], row[1]]
        }
    }

    def "a field of a map key in brackets written as null after the map was #description holds the null, #key"() {
        given:
        NavigableMap output = new NavigableMap()

        when:
        output.merge([("a.mappings[${key}].maxAge".toString()): '10'], true)
        output.merge(replacement, true)
        output.merge([("a.mappings[${key}].maxAge".toString()): null, ("a.mappings[${key}].allowCredentials".toString()): 'true'], true)

        then: 'the held map holds the null and the field written beside it, and not the value written before the replacement'
        output.navigate('a', 'mappings')[key] == [maxAge: null, allowCredentials: 'true']
        output.get('a.mappings')[key] == [maxAge: null, allowCredentials: 'true']
        output.get("a.mappings[${key}].maxAge".toString()) == null
        !output.toProperties().keySet().any { it.toString().endsWith('maxAge') }

        where:
        [key, description, replacement] << [['/api/**', '/api/v1.0/**'], ['replaced with a value', 'cleared']].combinations().collect { String key, String description ->
            [key, description, description == 'cleared' ? ['a.mappings': null] : ['a.mappings': [(key): 'one']]]
        }
    }

    def "an indexed key with a property after the index writes a map over a value of a list that is not a map"() {
        given:
        NavigableMap output = new NavigableMap()

        when:
        output.merge(['a.rows': ['one', 'two']], true)
        output.merge(['a.rows[0].name': 'first', 'a.rows[1].name': 'second'], true)

        then:
        output.navigate('a', 'rows') == [[name: 'first'], [name: 'second']]
        output.navigate('a', 'rows')[1].name == 'second'
    }

    def "an element rebuilt after the list was #description holds only the fields written since"() {
        given:
        NavigableMap output = new NavigableMap()

        when:
        output.merge(first, true)
        output.merge(replacement, true)
        writes.each { Map write -> output.merge(write, true) }

        then:
        output.navigate('a', 'rows') == expected
        output.get('a.rows') == expected

        where:
        description              | first                         | replacement                | writes                                                      | expected
        'replaced with values'   | ['a.rows[1].obsolete': 'old'] | ['a.rows': ['one', 'two']] | [['a.rows[0].name': 'first'], ['a.rows[1].name': 'second']] | [[name: 'first'], [name: 'second']]
        'replaced, then written' | ['a.rows[1].obsolete': 'old'] | ['a.rows': ['one', 'two']] | [['a.rows[1].name': 'second']]                              | ['one', [name: 'second']]
        'cleared'                | ['a.rows[1].obsolete': 'old'] | ['a.rows': null]           | [['a.rows[0].port': '8080'], ['a.rows[1].name': 'second']]  | [[port: '8080'], [name: 'second']]
        'cleared, same element'  | ['a.rows[0].name': 'old']     | ['a.rows': null]           | [['a.rows[0].port': '8080']]                                | [[port: '8080']]
    }

    def "a map written by subscript and then replaced holds only the fields written since in its entry"() {
        given:
        NavigableMap output = new NavigableMap()

        when:
        output.merge(['a.routes[x].obsolete': 'old'], true)
        output.merge(['a.routes': [x: 'one']], true)
        output.merge(['a.routes[x].name': 'second'], true)

        then:
        output.navigate('a', 'routes') == [x: [name: 'second']]
        output.get('a.routes') == [x: [name: 'second']]
    }
}
