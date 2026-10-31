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
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.MutablePropertySources
import org.springframework.core.env.PropertySource
import org.springframework.core.env.StandardEnvironment
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

        and: "a key that runs past a scalar is absent, where Grails 8 answered with the scalar"
        config.getProperty('plain.nested.value.deeper') == null
        !config.containsProperty('plain.nested.value.deeper')
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

        and: "a key that names a child of any of them, other than an element of the list, is absent rather than the value"
        config.getProperty('server.url.port') == null
        config.getProperty('server.url.port', String, 'none') == 'none'
        config.getProperty('server.ports.first') == null
        config.getProperty('grails.gorm.default.constraints.nullable') == null

        and: "containsProperty agrees"
        !config.containsProperty('server.url.port')
        !config.containsProperty('server.ports.first')
        !config.containsProperty('grails.gorm.default.constraints.nullable')
    }

    void 'a whole number after a list names its element, as Spring Boot reads it, from #description'() {
        given:
        def config = new PropertySourcesConfig(source)

        expect: "the element, by a whole-number segment or by an index after the name"
        config.getProperty('server.ports.0', Integer) == 8080
        config.getProperty('server.ports.1', Integer) == 8443
        config.getProperty('server.ports[1]', Integer) == 8443
        config.containsProperty('server.ports.0')
        config.navigate('server', 'ports', '1') == 8443

        and: "a list of maps reads into its elements"
        config.getProperty('app.rows.0.name') == 'first'
        config.getProperty('app.rows[1].name') == 'second'

        and: "an index past the end, or a segment that is not a whole number, is absent"
        config.getProperty('server.ports.2') == null
        !config.containsProperty('server.ports.2')
        config.getProperty('server.ports.-1') == null
        config.getProperty('server.ports.01') == null
        config.getProperty('server.ports[01]') == null
        config.getProperty('server.ports.0.deeper') == null

        where:
        description                        | source
        'a list held whole, as from a map' | [server: [ports: [8080, 8443]], app: [rows: [[name: 'first'], [name: 'second']]]]
        'application.yml'                  | new YamlPropertySourceLoader().load('application.yml', new ByteArrayResource("server:\n  ports:\n    - 8080\n    - 8443\napp:\n  rows:\n    - name: first\n    - name: second\n".bytes)).first()
    }

    void 'a whole number after a list gives the element the Spring environment gives'() {
        given: "application.yml in a config and in an environment with configuration property sources attached"
        def source = new YamlPropertySourceLoader().load('application.yml', new ByteArrayResource("server:\n  ports:\n    - 8080\n    - 8443\n".bytes)).first()
        def environment = new StandardEnvironment()
        environment.propertySources.addFirst(source)
        ConfigurationPropertySources.attach(environment)
        def config = new PropertySourcesConfig(source)

        expect:
        environment.getProperty('server.ports.0', Integer) == 8080
        config.getProperty('server.ports.0', Integer) == 8080
        environment.getProperty('server.ports.2') == null
        config.getProperty('server.ports.2') == null

        and: "a number with a leading zero is not an index"
        environment.getProperty('server.ports.01') == null
        config.getProperty('server.ports.01') == null
    }

    void 'a full key stored with #method wins over the element of the list it names: #key'() {
        given:
        def config = new PropertySourcesConfig([server: [ports: [8080, 8443]]])

        when:
        write(config, key)

        then: "the stored value, not the element"
        config.getProperty(key, Integer) == 9000
        config.get(key) == 9000

        and: "the other element still reads from the list"
        config.getProperty('server.ports[0]', Integer) == 8080
        config.getProperty('server.ports.0', Integer) == 8080

        where:
        method   | key               | write
        'put'    | 'server.ports[1]' | { PropertySourcesConfig c, String k -> c.put(k, 9000) }
        'putAll' | 'server.ports[1]' | { PropertySourcesConfig c, String k -> c.putAll([(k): 9000]) }
        'setAt'  | 'server.ports[1]' | { PropertySourcesConfig c, String k -> c[k] = 9000 }
        'put'    | 'server.ports.1'  | { PropertySourcesConfig c, String k -> c.put(k, 9000) }
        'putAll' | 'server.ports.1'  | { PropertySourcesConfig c, String k -> c.putAll([(k): 9000]) }
        'setAt'  | 'server.ports.1'  | { PropertySourcesConfig c, String k -> c[k] = 9000 }
    }

    void 'a map with a key that is a whole number reads it as before'() {
        given:
        def config = new PropertySourcesConfig([codes: ['0': 'zero', '1': 'one']])

        expect:
        config.getProperty('codes.0') == 'zero'
        config.navigate('codes', '1') == 'one'
    }

    void 'a typed read with a default below a value that is not a map gets the default'() {
        given: "a parent set to a boolean as a shorthand for its block"
        def config = new PropertySourcesConfig([x: [y: false]])

        expect: "the default, where Grails 8 answered with the parent"
        config.getProperty('x.y.enabled', Boolean, true)
        config.getProperty('x.y', Boolean) == false
    }

    void 'a key below a value that is not a map is answered by a property source of lower precedence that sets it'() {
        given: "a property source that sets a.b to a value, and one of lower precedence that sets a.b.c"
        def higher = new MapPropertySource('higher', ['a.b': 'x'])
        def lower = new MapPropertySource('lower', ['a.b.c': 'y'])
        def sources = new MutablePropertySources()
        sources.addLast(higher)
        sources.addLast(lower)
        def config = new PropertySourcesConfig(sources)

        and: "a Spring environment over the same two sources"
        def environment = new StandardEnvironment()
        environment.propertySources.addLast(higher)
        environment.propertySources.addLast(lower)

        expect: "getProperty answers with the deeper key, where Grails 8 answered with a.b, as the environment does"
        config.getProperty('a.b.c') == 'y'
        config.containsProperty('a.b.c')
        environment.getProperty('a.b.c') == 'y'

        and: "navigate stops at the value"
        config.navigate('a', 'b', 'c') == null
    }

    void 'a dotted key inside a nested map is exported under its full path and overwrites no other property'() {
        given: "a map holding a property and, in a nested map, a dotted key that alone names the same property"
        def config = new PropertySourcesConfig([a: [b: 'root'], x: ['a.b': 'nested'], logging: [level: ['org.springframework': 'DEBUG']]])

        expect: "each property is read and exported under its own name"
        config['a.b'] == 'root'
        config.getProperty('a.b') == 'root'
        config.getProperty('x.a.b') == 'nested'
        config.getProperty('logging.level.org.springframework') == 'DEBUG'
        config.toProperties().getProperty('a.b') == 'root'
        config.toProperties().getProperty('x.a.b') == 'nested'
        config.toProperties().getProperty('logging.level.org.springframework') == 'DEBUG'

        and: "no property is written under the dotted key alone"
        config.getProperty('org.springframework') == null
        !config.toProperties().containsKey('org.springframework')
    }

    void 'a dotted key inside a nested map writes no property that the nested maps do not hold'() {
        given: "a map with no b of its own, and a nested map holding a dotted key that alone names a.b"
        def config = new PropertySourcesConfig([a: [c: '1'], x: ['a.b': 'nested']])

        expect: "a.b is absent, and the dotted key is read under its full path"
        config.getProperty('a.b') == null
        !config.toProperties().containsKey('a.b')
        config.navigate('a', 'b') == null
        config.getProperty('x.a.b') == 'nested'
        config.getProperty('a.c') == '1'
    }

    void 'a placeholder in a value merged #description is resolved alike for the flat key and the nested entry'() {
        given: "a property source that answers a new value each time it is read, and a section the config already holds"
        def counter = new CountingPropertySource()
        def config = new PropertySourcesConfig(counter)
        config.merge([app: [name: 'demo']])

        when: "a value holding a placeholder is merged into the section"
        config.merge(merged)

        then: "the dotted key, the nested map and the export all read one resolved value"
        String resolved = config.navigate('app', 'url')
        resolved ==~ /http:\/\/host\d+/
        config.toProperties().getProperty('app.url') == resolved
        config['app.url'] == resolved
        config.getProperty('app.url') == resolved

        where:
        description       | merged
        'in a nested map' | [app: [url: 'http://${host}']]
        'as a dotted key' | ['app.url': 'http://${host}']
    }

    void 'a placeholder in a value merged in a nested map is resolved once'() {
        given:
        def counter = new CountingPropertySource()
        def config = new PropertySourcesConfig(counter)

        when:
        config.merge([app: [url: 'http://${host}']])

        then:
        config['app.url'] == 'http://host1'
        config.navigate('app', 'url') == 'http://host1'
        counter.readCount() == 1
    }

    void 'a placeholder in a dotted key inside a nested map is read alike for the flat key and the nested entry'() {
        given: "a property source that answers a new value each time it is read"
        def config = new PropertySourcesConfig(new CountingPropertySource())

        when: "a nested map holding a dotted key with a placeholder is merged"
        config.merge([grails: ['gorm.url': 'http://${host}']])

        then: "the dotted key, the nested map and the export all read one resolved value"
        String resolved = config.navigate('grails', 'gorm', 'url')
        resolved ==~ /http:\/\/host\d+/
        config['grails.gorm.url'] == resolved
        config.getProperty('grails.gorm.url') == resolved
        config.toProperties().getProperty('grails.gorm.url') == resolved

        and: "no property is written under the dotted key alone"
        config.getProperty('gorm.url') == null
        !config.containsKey('gorm.url')
    }

    static class CountingPropertySource extends PropertySource<Object> {

        private int reads

        CountingPropertySource() {
            super('counting', new Object())
        }

        @Override
        Object getProperty(String name) {
            name == 'host' ? "host${++reads}".toString() : null
        }

        int readCount() {
            reads
        }
    }
}
