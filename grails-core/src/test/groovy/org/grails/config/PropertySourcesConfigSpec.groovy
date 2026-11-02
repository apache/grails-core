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
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
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

    void 'a placeholder in a list element from application.yml is resolved as the environment resolves it'() {
        given: "application.yml with a placeholder in a list of values and in a list of maps"
        def yml = '''\
app:
  origins:
    - https://a.example.com
    - '${EXAMPLE_ORIGIN:https://default.example.com}'
  rows:
    - url: '${EXAMPLE_ORIGIN:https://default.example.com}'
      name: first
      db:
        host: '${EXAMPLE_ORIGIN:https://default.example.com}'
      tags:
        - '${EXAMPLE_ORIGIN:https://default.example.com}'
'''
        def source = new YamlPropertySourceLoader().load('application.yml', new ByteArrayResource(yml.bytes)).first()
        def environment = new StandardEnvironment()
        environment.propertySources.addFirst(source)
        ConfigurationPropertySources.attach(environment)
        def config = new PropertySourcesConfig(environment.propertySources)

        expect: "the environment resolves each element"
        environment.getProperty('app.origins[1]') == 'https://default.example.com'
        environment.getProperty('app.rows[0].url') == 'https://default.example.com'

        and: "the config answers the same, by index and in the whole list"
        config.getProperty('app.origins[1]') == 'https://default.example.com'
        config.getProperty('app.origins', List) == ['https://a.example.com', 'https://default.example.com']
        config.navigate('app', 'origins') == ['https://a.example.com', 'https://default.example.com']
        config.getProperty('app.rows[0].url') == 'https://default.example.com'
        config.navigate('app', 'rows')[0].url == 'https://default.example.com'
        config.navigate('app', 'rows')[0].name == 'first'

        and: "so does a map or a list inside an element"
        environment.getProperty('app.rows[0].db.host') == 'https://default.example.com'
        config.getProperty('app.rows[0].db.host') == 'https://default.example.com'
        config.navigate('app', 'rows')[0].db.host == 'https://default.example.com'
        config.navigate('app', 'rows')[0].tags == ['https://default.example.com']
    }

    void 'a placeholder in a list held whole is resolved'() {
        given: "a list held whole, as application.groovy gives it, of values, of maps and of lists"
        def map = new NavigableMap()
        map.merge([app: [origins: ['https://a.example.com', '${EXAMPLE_ORIGIN:https://default.example.com}'],
                         rows   : [[url: '${EXAMPLE_ORIGIN:https://default.example.com}', port: 8080]],
                         groups : [['${EXAMPLE_ORIGIN:https://default.example.com}']]]], true)
        def config = new PropertySourcesConfig(new NavigableMapPropertySource('application.groovy', map))

        expect:
        config.getProperty('app.origins', List) == ['https://a.example.com', 'https://default.example.com']
        config.navigate('app', 'origins') == ['https://a.example.com', 'https://default.example.com']
        config.navigate('app', 'rows') == [[url: 'https://default.example.com', port: 8080]]
        config.navigate('app', 'groups') == [['https://default.example.com']]

        and: "the property source keeps the list as written"
        map.navigate('app', 'origins') == ['https://a.example.com', '${EXAMPLE_ORIGIN:https://default.example.com}']
    }

    void 'a list element follows the value its placeholder names when the config is refreshed, and #description keeps the placeholder'() {
        given: "a list with a placeholder, and a property source holding the value it names"
        def values = [exampleHost: 'first']
        def propertySources = new MutablePropertySources()
        propertySources.addFirst(new MapPropertySource('values', values))
        propertySources.addLast(source)
        def config = new PropertySourcesConfig(propertySources)

        expect:
        config.getProperty('app.origins', List) == ['http://first']

        when: "the value changes and the config is refreshed"
        values.exampleHost = 'second'
        config.refresh()

        then: "the list element follows it, in the whole list and by navigation"
        config.getProperty('app.origins', List) == ['http://second']
        config.navigate('app', 'origins') == ['http://second']

        and: "the property source still holds the placeholder"
        source.getProperty('app.origins') == ['http://${exampleHost}']

        where:
        description                    | source
        'application.yml'              | new YamlPropertySourceLoader().load('application.yml', new ByteArrayResource('app:\n  origins:\n    - http://${exampleHost}\n'.bytes)).first()
        'application.groovy'           | new NavigableMapPropertySource('application.groovy', new NavigableMap().tap { merge([app: [origins: ['http://${exampleHost}']]], true) })
        'an ordinary property source'  | new MapPropertySource('ordinary', ['app.origins': ['http://${exampleHost}']])
    }

    void 'a list from #description is held as a copy, so an indexed write from a source of higher precedence leaves the source as it is'() {
        given:
        def propertySources = new MutablePropertySources()
        propertySources.addFirst(new MapPropertySource('higher', ['app.origins[1]': 'https://c.example.com']))
        propertySources.addLast(source)

        when:
        def config = new PropertySourcesConfig(propertySources)

        then: "the config holds the written element under each name of the list"
        config.getProperty('app.origins', List) == ['https://a.example.com', 'https://c.example.com']
        config['app.origins'] == ['https://a.example.com', 'https://c.example.com']
        config.navigate('app', 'origins') == ['https://a.example.com', 'https://c.example.com']

        and: "a list of the same kind, while the source keeps its own"
        config.navigate('app', 'origins').getClass() == source.getProperty('app.origins').getClass()
        source.getProperty('app.origins') == ['https://a.example.com', 'https://b.example.com']

        when: "the source of higher precedence is removed and the config is built again"
        propertySources.remove('higher')
        config = new PropertySourcesConfig(propertySources)

        then:
        config.getProperty('app.origins', List) == ['https://a.example.com', 'https://b.example.com']

        where:
        description                   | source
        'application.yml'             | new YamlPropertySourceLoader().load('application.yml', new ByteArrayResource('app:\n  origins:\n    - https://a.example.com\n    - https://b.example.com\n'.bytes)).first()
        'application.groovy'          | new NavigableMapPropertySource('application.groovy', new NavigableMap().tap { merge([app: [origins: new LinkedList(['https://a.example.com', 'https://b.example.com'])]], true) })
        'an ordinary property source' | new MapPropertySource('ordinary', ['app.origins': new LinkedList(['https://a.example.com', 'https://b.example.com'])])
    }

    void 'a list without a placeholder is held as given'() {
        given:
        def origins = ['https://a.example.com', 8080]
        def config = new PropertySourcesConfig([:])

        when:
        config.merge([app: [origins: origins]])

        then:
        config.navigate('app', 'origins').is(origins)
    }

    void 'a placeholder in a list element merged in a nested map is resolved once, alike for the flat key and the nested entry'() {
        given:
        def counter = new CountingPropertySource()
        def config = new PropertySourcesConfig(counter)

        when:
        config.merge([app: [hosts: ['http://${host}']]])

        then:
        config.navigate('app', 'hosts') == ['http://host1']
        config['app.hosts'] == ['http://host1']
        config.getProperty('app.hosts', List) == ['http://host1']
        counter.readCount() == 1
    }

    void 'a placeholder in a list element merged #description is resolved once, alike for every read'() {
        given:
        def counter = new CountingPropertySource()
        def config = new PropertySourcesConfig(counter)

        when:
        config.merge(merged)

        then:
        config.navigate('app', 'hosts') == ['http://host1']
        config['app.hosts'] == ['http://host1']
        config.navigate('app', 'hosts[0]') == indexed
        counter.readCount() == 1

        where:
        description         | merged                             | indexed
        'as a dotted key'   | ['app.hosts': ['http://${host}']]  | null
        'as an indexed key' | ['app.hosts[0]': 'http://${host}'] | 'http://host1'
    }

    void 'a placeholder in a list element of application.yml is resolved once, alike for every read'() {
        given: "application.yml, read through Grails' YAML loader, and a property source that answers a new value each time it is read"
        def yml = '''\
app:
  hosts:
    - 'http://${host}'
  rows:
    - url: 'http://${host}'
      tags:
        - 'http://${host}'
      routes:
        '[x]': 'http://${host}'
'''
        def counter = new CountingPropertySource()
        def propertySources = new MutablePropertySources()
        propertySources.addFirst(new YamlPropertySourceLoader().load('application.yml', new ByteArrayResource(yml.bytes)).first())
        propertySources.addLast(counter)
        def config = new PropertySourcesConfig(propertySources)

        expect: "each element is read once"
        counter.readCount() == 4

        and: "a list element reads the same by index, in the whole list, by navigation and in the export"
        String host = config.getProperty('app.hosts[0]')
        host ==~ /http:\/\/host\d/
        config['app.hosts[0]'] == host
        config['app.hosts'] == [host]
        config.getProperty('app.hosts', List) == [host]
        config.navigate('app', 'hosts') == [host]
        config.toProperties().getProperty('app.hosts[0]') == host

        and: "so do a value and a list element inside an element"
        String url = config.getProperty('app.rows[0].url')
        config.navigate('app', 'rows')[0].url == url
        config['app.rows'][0].url == url
        config.toProperties().getProperty('app.rows[0].url') == url
        String tag = config.getProperty('app.rows[0].tags[0]')
        config.navigate('app', 'rows')[0].tags == [tag]
        config['app.rows'][0].tags == [tag]
        config.toProperties().getProperty('app.rows[0].tags[0]') == tag

        and: "so does a key in brackets in a map inside an element, which the element holds under both of its names"
        String route = config.getProperty('app.rows[0].routes[x]')
        config.getProperty('app.rows[0].routes.x') == route
        config.getProperty('app.rows', List)[0].routes.x == route
        config.navigate('app', 'rows')[0].routes.x == route
        config.toProperties().getProperty('app.rows[0].routes[x]') == route

        and: "the export gives the key in brackets once, by its bracketed name"
        !config.toProperties().containsKey('app.rows[0].routes.x')

        and: "each element is resolved on its own"
        [host, url, tag, route].toSet().size() == 4
    }

    void 'a value of an ordinary property source is resolved once, so an escaped placeholder is kept as text, as the environment keeps it'() {
        given: "a property source the environment resolves as it reads it, with an escaped placeholder in a value, a list, an indexed key and a ConfigObject"
        def section = new ConfigObject()
        section.url = 'http://${exampleHost}'
        section.hosts = ['\\${exampleHost}', '${exampleHost}']
        def environment = new StandardEnvironment()
        environment.propertySources.addFirst(new MapPropertySource('ordinary', [
                exampleHost   : 'secret',
                'app.text'    : '\\${exampleHost}',
                'app.list'    : ['\\${exampleHost}', '${exampleHost}'],
                'app.hosts[0]': '\\${exampleHost}',
                'app.section' : section]))
        def config = new PropertySourcesConfig(environment.propertySources)

        expect: "the environment keeps the escaped placeholder as text"
        environment.getProperty('app.text') == '${exampleHost}'
        environment.getProperty('app.hosts[0]') == '${exampleHost}'

        and: "so does the config, in a value, in a list and under an indexed key"
        config.getProperty('app.text') == '${exampleHost}'
        config.getProperty('app.list', List) == ['${exampleHost}', 'secret']
        config.navigate('app', 'list') == ['${exampleHost}', 'secret']
        config.getProperty('app.hosts[0]') == '${exampleHost}'
        config.navigate('app', 'hosts') == ['${exampleHost}']

        and: "and in a ConfigObject, whose placeholders are resolved"
        config.getProperty('app.section.url') == 'http://secret'
        config.navigate('app', 'section', 'hosts') == ['${exampleHost}', 'secret']
    }

    void 'a map written by map subscripts is exported by its bracketed keys only, which Spring Boot binds as written'() {
        given: "a map written by map subscripts, as application.yml writes '[/api/**]'"
        def config = new PropertySourcesConfig(new MapPropertySource('yaml', [
                'app.routes[/api/**]': 'x', 'app.routes[/v1.0/**]': 'y', 'app.routes[k]': 'z',
                'app.cors[/admin/**].allowedOrigins[0]': 'https://a.example.com',
        ]))

        when:
        Properties exported = config.toProperties()

        then: "no entry is exported a second time by a dotted name"
        exported.stringPropertyNames().findAll { it.startsWith('app.routes') } as Set == ['app.routes[/api/**]', 'app.routes[/v1.0/**]', 'app.routes[k]'] as Set
        !exported.stringPropertyNames().any { it.startsWith('app.cors./') }

        and: "Spring Boot binds the exported map with the keys the file wrote"
        def published = new StandardEnvironment()
        published.propertySources.addFirst(new MapPropertySource('published', exported as Map<String, Object>))
        Binder.get(published).bind('app.routes', Bindable.mapOf(String, String)).get() == ['/api/**': 'x', '/v1.0/**': 'y', k: 'z']
        Binder.get(published).bind('app.cors', Bindable.mapOf(String, Object)).get().keySet() == ['/admin/**'] as Set
    }

    void 'a map put as a value is exported by the dotted names of its entries'() {
        given:
        def config = new PropertySourcesConfig()
        config.put('plain', [nested: 'value'])

        expect:
        config.toProperties().getProperty('plain.nested') == 'value'
    }

    void 'a map written by map subscripts is exported once whatever precedes it in application.yml, #description'() {
        given: "application.yml as Grails loads it, which keeps the order of the file"
        def config = new PropertySourcesConfig(new YamlPropertySourceLoader().load('application.yml', new ByteArrayResource(yaml.bytes)).first())

        when:
        def published = new StandardEnvironment()
        published.propertySources.addFirst(new MapPropertySource('published', config.toProperties() as Map<String, Object>))

        then:
        !config.toProperties().stringPropertyNames().any { it.contains('./') }
        Binder.get(published).bind(prefix, Bindable.mapOf(String, Object)).get() == bound

        where:
        description                          | yaml                                                                         | prefix        | bound
        'a plain key before a bracketed one' | "app:\n  routes:\n    default: z\n    '[/api/**]': x\n"                       | 'app.routes'  | [default: 'z', '/api/**': 'x']
        'a subscript after a subscript'      | "app:\n  tenants:\n    '[acme]':\n      routes:\n        '[/api/**]': x\n" | 'app.tenants' | [acme: [routes: ['/api/**': 'x']]]
    }

    void 'Spring Boot binds every field of an entry written in brackets that a later merge changed, #description'() {
        given:
        def config = new PropertySourcesConfig()
        merges.each { Map merged -> config.merge(merged) }

        when:
        def published = new StandardEnvironment()
        published.propertySources.addFirst(new MapPropertySource('published', config.toProperties() as Map<String, Object>))

        then:
        Binder.get(published).bind(prefix, Bindable.mapOf(String, Object)).get() == bound

        where:
        description                                      | merges                                                                         | prefix       | bound
        'a field merged after a map written in brackets' | [['routes[api]': [target: 'http://localhost'], 'routes[api].enabled': 'true']] | 'routes'     | [api: [target: 'http://localhost', enabled: 'true']]
        'a dotted key merged after a null in brackets'   | [['app.routes[k]': null], ['app.routes.k': 'new']]                             | 'app.routes' | [k: 'new']
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
