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
package org.grails.config

import org.grails.config.yaml.YamlPropertySourceLoader
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.MutablePropertySources
import org.springframework.core.env.PropertySource
import org.springframework.core.io.ByteArrayResource
import spock.lang.Ignore
import spock.lang.Issue
import spock.lang.Specification

import jakarta.persistence.FlushModeType

/**
 * @author graemerocher
 */
class PropertySourcesConfigSpec extends Specification {

    void "Test that PropertySourcesConfig works as expected"() {
        given:"A PropertySourcesConfig instance"
            def propertySource = new MapPropertySource("foo", [one:1, two:2, 'flush.mode': 'commit', 'three.four': 34, 'empty.value':null])
            def propertySources = new MutablePropertySources()
            propertySources.addLast(propertySource)
            def config = new PropertySourcesConfig(propertySources)

        expect:"The config to be accessible"
            config.one == 1
            config.two == 2
            config.three.four == 34
            !config.four.five
            config.getProperty('one', String) == '1'
            config.getProperty('three.four', String) == '34'
            config.getProperty('three', String) == null
            config.get('three.four') == 34
            config.getProperty('three.four') == '34'
            config.getProperty('three.four', Date) == null
            config.getProperty('flush.mode', FlushModeType) == FlushModeType.COMMIT
            !config.empty.value
    }

    @Issue("https://github.com/apache/grails-spring-security-core/issues/724")
    void "Test accessing a NavigableMap property as Map class"() {
        given:
        def source = new NavigableMap()
        source.merge(["grails": ["plugin": ["springsecurity": ["userLookup": ["usernamePropertyName": "username"]]]]])
        def propertySource = new MapPropertySource("springsecurity", source)
        def propertySources = new MutablePropertySources()
        propertySources.addLast(propertySource)
        def config = new PropertySourcesConfig(propertySources)

        expect:
        config.getProperty("grails.plugin.springsecurity", ConfigObject.class)
    }

    void "Test accessing a NavigableMap with nested NavigableMap property as Map class"() {
        given:
        def source = new NavigableMap()
        def chainMap = new NavigableMap()
        chainMap.merge(["chainMap" :[
                [pattern: '/assets/**', filters: 'none'],
                [pattern: '/**/js/**', filters: 'none'],
                [pattern: '/**/css/**', filters: 'none'],
                [pattern: '/**/images/**', filters: 'none'],
                [pattern: '/**/favicon.ico', filters: 'none'],
                [pattern: '/**', filters: 'JOINED_FILTERS']
        ]])
        source.merge(["grails": ["plugin": ["springsecurity": ["filterChain": chainMap, "userLookup": ["usernamePropertyName": "username"]]]]])
        def propertySource = new MapPropertySource("springsecurity", source)
        def propertySources = new MutablePropertySources()
        propertySources.addLast(propertySource)
        def config = new PropertySourcesConfig(propertySources)

        when:
        def securityConfig = config.getProperty("grails.plugin.springsecurity", ConfigObject.class)

        then:
        !(securityConfig.userLookup instanceof NavigableMap)
        !(securityConfig.filterChain.chainMap instanceof NavigableMap)
    }

    /*

      We need to settle on whether the following is a bug or not.
      There are some tests in ConfigMapSpec that indirectly assert some
      behavior that I think would be inconsistent with making the following
      test pass.

     */
    private static class ChangingPropertySource extends PropertySource<Object> {

        int reads

        ChangingPropertySource() {
            super('changing', new Object())
        }

        @Override
        Object getProperty(String name) {
            name == 'counter' ? "value-${++reads}".toString() : null
        }

        int readCount() {
            reads
        }
    }

    @Ignore("Cannot set property 'bar' on null object")
    @Issue('apache/grails-core#10188')
    void 'test replacing nested property values'() {
        given: 'a PropertySourcesConfig'
        def cfg = new PropertySourcesConfig()

        when: 'a nested property is assigned a value'
        cfg.foo.bar = ['one': '1']

        and: 'later is assigned a different value'
        cfg.foo.bar = ['two': '2']

        then: 'the second value is not merged with the first value'
        cfg.foo.bar == ['two': '2']
    }

    void 'a dotted key walks plain nested maps and skips empty segments'() {
        given: "a plain map value that was put without flattened keys"
        def config = new PropertySourcesConfig()
        config.put('plain', [nested: [value: 'found']])

        expect: "each segment of the key is resolved against the map it reaches"
        config.getProperty('plain.nested.value') == 'found'
        config.getProperty('plain.nested', Map) == [value: 'found']
        config.getProperty('plain.missing.value') == null

        and: "empty segments are ignored as they always were"
        config.getProperty('plain..nested.value') == 'found'
        config.getProperty('.plain.nested.value.') == 'found'
        config.getProperty('...') == null
    }

    void 'the flat key is read before the nested maps are walked'() {
        given: "a merged value, which exists as a flat key and as a nested entry"
        def config = new PropertySourcesConfig()
        config.merge([a: [b: 'merged']])

        expect: "every read agrees"
        config.getProperty('a.b') == 'merged'
        config['a.b'] == 'merged'
        config.a.b == 'merged'

        when: "the flat key is written directly"
        config.put('a.b', 'flat')

        then: "getProperty answers with it, as config['a.b'] does"
        config.getProperty('a.b') == 'flat'
        config['a.b'] == 'flat'

        when: "the nested entry is written directly"
        config.a.put('b', 'nested')

        then: "getProperty keeps the flat key, while dot navigation reads the nested entry"
        config.getProperty('a.b') == 'flat'
        config.a.b == 'nested'

        when: "the nested entry is assigned by subscript, which writes both"
        config.a['b'] = 'subscript'

        then: "every read agrees"
        config.getProperty('a.b') == 'subscript'
        config['a.b'] == 'subscript'
        config.a.b == 'subscript'

        when: "the value is assigned through dot notation, which writes both"
        config.a.b = 'assigned'

        then: "every read agrees again"
        config.getProperty('a.b') == 'assigned'
        config['a.b'] == 'assigned'
        config.a.b == 'assigned'
    }

    void 'a block merged over a scalar is read as the block'() {
        given: "a scalar merged first and a block under it merged second, as a higher-precedence source does"
        def config = new PropertySourcesConfig()
        config.merge(['server.url': 'x'])
        config.merge(['server.url.port': 'y'])

        expect: "every read sees the block"
        config.getProperty('server.url', Map) == [port: 'y']
        config.navigate('server', 'url') == [port: 'y']
        config.getProperty('server.url.port') == 'y'

        and: "a String read of the block is the default, as it always was"
        config.getProperty('server.url') == null
        config.getProperty('server.url', String, 'none') == 'none'
    }

    void 'a dotted key inside a nested map does not overwrite the root key it resembles'() {
        given: "a root key and a nested map whose dotted key spells the same path"
        def config = new PropertySourcesConfig()
        config.merge([a: [b: 'root'], x: ['a.b': 'nested']])

        expect: "each key answers with its own value"
        config.getProperty('a.b') == 'root'
        config.navigate('a', 'b') == 'root'
        config.getProperty('x.a.b') == 'nested'
        config.navigate('x', 'a', 'b') == 'nested'
    }

    void 'a flat key that a merge left behind is read rather than the scalar that replaced its parent'() {
        given: "a nested block merged first and a scalar merged over its parent, as a higher-precedence source does"
        def config = new PropertySourcesConfig()
        config.merge(['server.url.port': 'y', 'server.url': 'x'])

        expect: "the parent is the scalar"
        config.getProperty('server.url') == 'x'

        and: "the child answers with the flat key the first merge wrote"
        config.getProperty('server.url.port') == 'y'
    }

    void 'a dotted key written directly is read even when its parent is a scalar'() {
        given: "a scalar and a dotted key below it, both written directly"
        def config = new PropertySourcesConfig()
        config.put('a', 'scalar')
        config.put('a.b', 1)

        expect: "each answers with what was written"
        config.getProperty('a') == 'scalar'
        config.getProperty('a.b', Integer) == 1
    }

    void 'a placeholder in a nested value is resolved in the flat key as well as in the nested entry'() {
        given: "a yaml value that refers to another"
        def yaml = '''
name: Hello
hello:
    evaluatedName: '${name}, world'
'''
        def yamlSource = new YamlPropertySourceLoader().load('application.yml', new ByteArrayResource(yaml.bytes, 'test.yml'), null)
        def config = new PropertySourcesConfig(yamlSource.first())

        expect: "every read sees the resolved value"
        config.getProperty('hello.evaluatedName') == 'Hello, world'
        config['hello.evaluatedName'] == 'Hello, world'
        config.hello.evaluatedName == 'Hello, world'
    }

    void 'a placeholder in a merged value is resolved once for both representations'() {
        given: "a property source whose value changes on every read, as random.uuid does"
        def changing = new ChangingPropertySource()
        def propertySources = new MutablePropertySources()
        propertySources.addLast(changing)
        def config = new PropertySourcesConfig(propertySources)

        when: "a value with a placeholder is merged"
        config.merge([auth: [secret: '${counter}']])

        then: "the flat key and the nested entry hold the same resolved value"
        config.getProperty('auth.secret') == 'value-1'
        config.getProperty('auth', Map).secret == 'value-1'
        config['auth.secret'] == 'value-1'

        and: "the placeholder was resolved once"
        changing.readCount() == 1
    }

    void 'an indexed list merged under its parent is read as the updated list'() {
        given: "a list written by index, then an element of it merged under the parent"
        def config = new PropertySourcesConfig()
        config.merge(['app.items[0]': 'old'])
        config.merge([app: ['items[0]': 'new']])

        expect: "the flat key and the nested list agree"
        config.getProperty('app.items', List) == ['new']
        config.navigate('app', 'items') == ['new']
    }

    void 'a nested dotted merge over a scalar is read as the block'() {
        given: "a scalar merged first and a block merged over it second through a dotted key inside a nested map"
        def config = new PropertySourcesConfig()
        config.merge(['x.a': 'old'])
        config.merge([x: ['a.b': 'new']])

        expect: "every read sees the block"
        config.getProperty('x.a', Map) == [b: 'new']
        config.navigate('x', 'a') == [b: 'new']
        config.getProperty('x.a.b') == 'new'

        and: "a String read of the block is the default"
        config.getProperty('x.a') == null
    }

    void 'a block recreated after a scalar replaced it is read as the new block'() {
        given: "a block, a scalar merged over it, and a block merged under the scalar again"
        def config = new PropertySourcesConfig()
        config.merge(['a.b.c': 1])
        config.merge([a: 'scalar'])
        config.merge(['a.b.d': 2])

        expect: "every read sees the recreated block"
        config.getProperty('a.b', Map) == [d: 2]
        config.navigate('a', 'b') == [d: 2]
        config.getProperty('a.b.d', Integer) == 2
    }

    void 'setting a nested property to null leaves its siblings readable'() {
        given: "two blocks under one parent"
        def config = new PropertySourcesConfig()
        config.merge(['a.b.c': 1, 'a.e.f': 3])

        when: "one of them is set to null"
        config.a.e = null

        then: "it is gone through every read"
        config.getProperty('a.e', Map) == null
        !config.containsProperty('a.e.f')
        config.getProperty('a', Map) == [b: [c: 1]]

        and: "its sibling is still there through every read"
        config['a.b.c'] == 1
        config.getProperty('a.b.c', Integer) == 1
        config.getProperty('a.b', Map) == [c: 1]
    }

    void 'a dotted key inside a nested map leaves no copy of itself in the parent'() {
        given: "a dotted key inside a nested map, then the same key merged flat"
        def config = new PropertySourcesConfig()
        config.merge([a: ['b.c': 'nested']])
        config.merge(['a.b.c': 'flat'])

        expect: "the parent holds the nested entry only, and every read agrees"
        config.getProperty('a', Map) == [b: [c: 'flat']]
        config.getProperty('a.b.c') == 'flat'
        config.navigate('a', 'b', 'c') == 'flat'
    }

    void 'a dotted key that runs past a value that is not a map is absent'() {
        given: "a scalar, a list and a closure, the value shapes application.yml and application.groovy produce"
        def constraints = { nullable true }
        def config = new PropertySourcesConfig([
                server: [url: 'http://localhost', ports: [8080, 8443]],
                grails: [gorm: ['default': [constraints: constraints]]]
        ])

        expect: "each value resolves itself"
        config.getProperty('server.url') == 'http://localhost'
        config.getProperty('server.ports', List) == [8080, 8443]
        config.getProperty('grails.gorm.default.constraints', Closure).is(constraints)

        and: "a key that names a child of any of them is absent rather than the value"
        config.getProperty('server.url.port') == null
        config.getProperty('server.url.port', String, 'none') == 'none'
        config.getProperty('server.ports.0') == null
        config.getProperty('grails.gorm.default.constraints.nullable') == null

        and: "containsProperty agrees"
        !config.containsProperty('server.url.port')
        !config.containsProperty('server.ports.0')
        !config.containsProperty('grails.gorm.default.constraints.nullable')
    }
}
