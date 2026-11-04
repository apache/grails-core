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
import spock.lang.Issue;
import spock.lang.Specification

class ConfigMapSpec extends Specification {

    def "should support merging ConfigObject maps"() {
        given:
        NavigableMap configMap = new NavigableMap()
        def config = new ConfigSlurper().parse('''
foo {
    bar = "good"
}
test.another = true
''')

        when:"a config object is merged"
        configMap.merge(config)

        then:"The merge is correct"
        configMap.size() == 4
        configMap['test'] instanceof NavigableMap
        configMap['test.another']  == true
    }

    def "should support merge correctly"() {
        given:
        NavigableMap configMap = new NavigableMap()
        when:
        configMap.merge(['foo.bar':'good1', bar:[foo:'good2']], true)
        then:
        configMap.foo.bar == 'good1'
        configMap.getProperty('foo.bar') == 'good1'
        configMap.bar.foo == 'good2'
        configMap.getProperty('bar.foo') == 'good2'

        when:
        configMap.merge(['foo.two':'good3', bar:[two:'good4']], true)
        configMap.merge(['grails.codegen.defaultPackage':"test"])
        configMap.merge([grails:[codegen:[defaultPackage:"test"]]])
        configMap.merge(['grails.codegen':[defaultPackage:"test"]], true)

        then:
        configMap.size() == 9
        configMap.containsKey('grails.codegen.defaultPackage')
        configMap.getProperty('grails.codegen.defaultPackage') == 'test'
        configMap.grails.codegen.defaultPackage == 'test'
        configMap.foo.bar == 'good1'
        configMap.getProperty('foo.bar') == 'good1'
        configMap.bar.foo == 'good2'
        configMap.getProperty('bar.foo') == 'good2'

        configMap.foo.two == 'good3'
        configMap.getProperty('foo.two') == 'good3'
        configMap.bar.two == 'good4'
        configMap.getProperty('bar.two') == 'good4'

    }
    def "should support flattening keys - map syntax"() {
        given:
        NavigableMap configMap = new NavigableMap()
        when:
        configMap.a = [b: [c: 1, d: 2]]
        then:
        configMap.toFlatConfig() == ['a.b.c': 1, 'a.b.d': 2]
    }
    def "should support flattening keys - dot syntax"() {
        given:
        NavigableMap configMap = new NavigableMap()
        when:
        configMap.a.b.c = 1
        configMap.a.b.d = 2
        then:
        configMap.toFlatConfig() == ['a.b.c': 1, 'a.b.d': 2]
    }

    @Issue('#9146')
    def "should support hashCode() - map syntax"() {
        given:
        NavigableMap configMap = new NavigableMap()
        when:
        configMap.a = [b: [c: 1, d: 2]]
        then:"hasCode() doesn't cause a Stack Overflow error"
        configMap.hashCode() == configMap.hashCode()
    }

    @Issue('#9146')
    def "should support hashCode() - dot syntax"() {
        given:
        NavigableMap configMap = new NavigableMap()
        when:
        configMap.a.b.c = 1
        configMap.a.b.d = 2
        then:"hasCode() doesn't cause a Stack Overflow error"
        configMap.hashCode() == configMap.hashCode()
    }

    def "should support flattening list values - map syntax"() {
        given:
        NavigableMap configMap = new NavigableMap()
        when:
        configMap.a = [b: [c: [1, 2, 3], d: 2]]
        then:
        configMap.toFlatConfig() ==
                ['a.b.c': [1, 2, 3],
                 'a.b.c[0]': 1,
                 'a.b.c[1]': 2,
                 'a.b.c[2]': 3,
                 'a.b.d': 2]
    }

    def "should support flattening list values - dot syntax"() {
        given:
        NavigableMap configMap = new NavigableMap()
        when:
        configMap.a.b.c = [1, 2, 3]
        configMap.a.b.d = 2
        then:
        configMap.toFlatConfig() ==
                ['a.b.c': [1, 2, 3],
                 'a.b.c[0]': 1,
                 'a.b.c[1]': 2,
                 'a.b.c[2]': 3,
                 'a.b.d': 2]
    }
    
    def "should support flattening to properties - map syntax"() {
        given:
        NavigableMap configMap = new NavigableMap()
        when:
        configMap.a = [b: [c: [1, 2, 3], d: 2]]
        then:
        configMap.toProperties() ==
                ['a.b.c': '1,2,3',
                 'a.b.c[0]': '1',
                 'a.b.c[1]': '2',
                 'a.b.c[2]': '3',
                 'a.b.d': '2']
    }

    def "should support flattening to properties - dot syntax"() {
        given:
        NavigableMap configMap = new NavigableMap()
        when:
        configMap.a.b.c = [1, 2, 3]
        configMap.a.b.d = 2
        then:
        configMap.toProperties() ==
                ['a.b.c': '1,2,3',
                 'a.b.c[0]': '1',
                 'a.b.c[1]': '2',
                 'a.b.c[2]': '3',
                 'a.b.d': '2']
    }
    
    def "should support cloning - map syntax"() {
        given:
        NavigableMap configMap = new NavigableMap()
        configMap.a = [b: [c: [1, 2, 3], d: 2]]
        when: 
        NavigableMap cloned = configMap.clone()
        then:
        cloned.toFlatConfig() ==
                ['a.b.c': [1, 2, 3],
                 'a.b.c[0]': 1,
                 'a.b.c[1]': 2,
                 'a.b.c[2]': 3,
                 'a.b.d': 2]
        !cloned.is(configMap)
        cloned == configMap
    }

    def "should support cloning - dot syntax"() {
        given:
        NavigableMap configMap = new NavigableMap()
        configMap.a.b.c = [1, 2, 3]
        configMap.a.b.d = 2
        when:
        NavigableMap cloned = configMap.clone()
        then:
        cloned.toFlatConfig() ==
                ['a.b.c': [1, 2, 3],
                 'a.b.c[0]': 1,
                 'a.b.c[1]': 2,
                 'a.b.c[2]': 3,
                 'a.b.d': 2]
        !cloned.is(configMap)
        cloned == configMap
    }

    def "an entry of a map written by map subscripts is flattened once, by its bracketed key, #description"() {
        given:
        NavigableMap configMap = new NavigableMap()
        merges.each { Map merged -> configMap.merge(merged, true) }

        when:
        NavigableMap flattened = from.call(configMap)

        then:
        flattened.toFlatConfig().keySet() == keys as Set
        flattened.toProperties().stringPropertyNames() == keys as Set

        where:
        description                          | merges                                                      | from                                        | keys
        'after a dotted key in the same map' | [['app.routes.default': 'z'], ['app.routes[/api/**]': 'x']] | { NavigableMap map -> map }                 | ['app.routes.default', 'app.routes[/api/**]']
        'after another subscript'            | [['app.tenants[acme].routes[/api/**]': 'x']]                | { NavigableMap map -> map }                 | ['app.tenants[acme].routes[/api/**]']
        'from a nested map'                  | [['app.routes[/api/**]': 'x']]                              | { NavigableMap map -> map.navigate('app') } | ['routes[/api/**]']
        'from a copy'                        | [['app.routes[/api/**]': 'x']]                              | { NavigableMap map -> map.clone() }         | ['app.routes[/api/**]']
    }

    def "an entry written in brackets is flattened once when every field is held in brackets, #description"() {
        given:
        NavigableMap configMap = new NavigableMap()
        configMap.merge(merged, true)

        expect:
        configMap.toFlatConfig().keySet() == keys as Set
        configMap.toProperties().stringPropertyNames() == keys as Set

        where:
        description                     | merged                                                                                                                     | keys
        'a map written in brackets'     | ['app.routes[api]': [target: 'http://localhost', enabled: 'true']]                                                         | ['app.routes[api].target', 'app.routes[api].enabled']
        'lists written below a bracket' | ['grails.cors.mappings[/api/**].allowedOrigins[0]': 'https://a', 'grails.cors.mappings[/api/**].allowedMethods[0]': 'GET'] | ['grails.cors.mappings[/api/**].allowedOrigins', 'grails.cors.mappings[/api/**].allowedOrigins[0]', 'grails.cors.mappings[/api/**].allowedMethods', 'grails.cors.mappings[/api/**].allowedMethods[0]']
    }

    def "a dotted name is flattened as before, unless every bracketed name it spells holds the same value, #description"() {
        given:
        NavigableMap configMap = new NavigableMap()

        when:
        writes.each { Closure write -> write.call(configMap) }

        then:
        configMap.toProperties() == exported
        configMap.toFlatConfig() == exported

        where:
        description                                                     | exported                                                                 | writes
        'a field merged after a map written in brackets'                | ['routes.api.target': 'http://localhost', 'routes[api].enabled': 'true'] | [{ NavigableMap m -> m.merge(['routes[api]': [target: 'http://localhost'], 'routes[api].enabled': 'true'], true) }]
        'a dotted key merged after a null in brackets'                  | ['app.routes.k': 'new']                                                  | [{ NavigableMap m -> m.merge(['app.routes[k]': null], true) }, { NavigableMap m -> m.merge(['app.routes.k': 'new'], true) }]
        'a dotted key merged after a value in brackets'                 | ['app.routes.k': 'new', 'app.routes[k]': 'old']                          | [{ NavigableMap m -> m.merge(['app.routes[k]': 'old'], true) }, { NavigableMap m -> m.merge(['app.routes.k': 'new'], true) }]
        'a dotted key put after a key in brackets'                      | ['app.routes.k': 'new', 'app.routes[k]': 'old']                          | [{ NavigableMap m -> m.merge(['app.routes[k]': 'old'], true) }, { NavigableMap m -> m.put('app.routes.k', 'new') }]
        'a key in brackets merged after a dotted key'                   | ['app.routes[k]': 'new']                                                 | [{ NavigableMap m -> m.merge(['app.routes.k': 'old'], true) }, { NavigableMap m -> m.merge(['app.routes[k]': 'new'], true) }]
        'a map in brackets merged after a dotted key below it'          | ['a[b].c': 'new']                                                        | [{ NavigableMap m -> m.merge(['a.b.c': 'old'], true) }, { NavigableMap m -> m.merge(['a[b].c': 'new'], true) }]
        'a dotted key merged after two keys in brackets that differ'    | ['a.b.c': 'new', 'a.b[c]': 'old', 'a[b.c]': 'new']                       | [{ NavigableMap m -> m.merge(['a[b.c]': 'new'], true) }, { NavigableMap m -> m.merge(['a.b[c]': 'old'], true) }, { NavigableMap m -> m.merge(['a.b.c': 'new'], true) }]
        'a key with a dot in brackets'                                  | ['a[b.c]': 'x']                                                          | [{ NavigableMap m -> m.merge(['a[b.c]': 'x'], true) }]
        'a key with a dot in brackets beside the nested path it spells' | ['a.b.c': 'x', 'a[b.c]': 'x']                                            | [{ NavigableMap m -> m.merge(['a.b.c': 'x'], true) }, { NavigableMap m -> m.merge(['a[b.c]': 'x'], true) }]
        'a key with a dot in brackets merged after a dotted key put'    | ['a.b.c': 'x', 'a[b.c]': 'x']                                            | [{ NavigableMap m -> m.put('a.b.c', 'x') }, { NavigableMap m -> m.merge(['a[b.c]': 'x'], true) }]
        'a dotted key put after a key with a dot in brackets'           | ['a.b.c': 'x', 'a[b.c]': 'x']                                            | [{ NavigableMap m -> m.merge(['a[b.c]': 'x'], true) }, { NavigableMap m -> m.put('a.b.c', 'x') }]
        'a key with a dot in brackets whose first part holds a scalar'  | ['routes[/v1]': 'one', 'routes[/v1.0/**]': 'two']                        | [{ NavigableMap m -> m.merge(['routes[/v1]': 'one'], true) }, { NavigableMap m -> m.merge(['routes[/v1.0/**]': 'two'], true) }]
        'a key with a dot in brackets whose first part holds a map'     | ['routes./v1.0/**': 'two', 'routes[/v1.0/**]': 'two']                    | [{ NavigableMap m -> m.merge(['routes./v1.0/**': 'two'], true) }, { NavigableMap m -> m.merge(['routes[/v1.0/**]': 'two'], true) }]
        'a key with a dot in brackets whose dotted start holds a map'   | ['a.b.c': 'x', 'a[b.c]': 'x']                                            | [{ NavigableMap m -> m.put('a.b', [c: 'x']) }, { NavigableMap m -> m.merge(['a[b.c]': 'x'], true) }]
        'a map put at the dotted start of a key with a dot in brackets' | ['a.b.c': 'x', 'a[b.c]': 'x']                                            | [{ NavigableMap m -> m.merge(['a[b.c]': 'x'], true) }, { NavigableMap m -> m.put('a.b', [c: 'x']) }]
    }
}
