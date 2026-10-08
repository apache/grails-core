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

import grails.util.Environment
import org.grails.config.yaml.YamlPropertySourceLoader
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration
import org.springframework.core.ResolvableType
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.env.SystemEnvironmentPropertySource
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

@RestoreSystemProperties
class YamlPropertySourceLoaderSpec extends Specification {

    def setup() {
        System.setProperty(Environment.KEY, Environment.DEVELOPMENT.name)
        Environment.reset()
    }

    def "ensure the config for environment is merged with single environment block"() {
        given: "A PropertySourcesConfig instance"
        def propertySource = new YamlPropertySourceLoader()
        Resource resource = new FileSystemResource(getClass().getClassLoader().getResource("foo-plugin-environments.yml").getFile())

        when:
        def yamlPropertiesSource = propertySource.load('foo-plugin-environments.yml', resource, Arrays.asList("dataSource", "hibernate"))
        def config = new PropertySourcesConfig(yamlPropertiesSource.first())

        then: "The config to be accessible with the merged env values"
        config.one == 2
        config.two == 3
        config.three.four == 45
        !config.four.five
        config.getProperty('one', String) == '2'
        config.getProperty('three.four', String) == '45'
        config.getProperty('three', String) == null
        config.get('three.four') == 45
        config.getProperty('three.four') == '45'
        config.getProperty('three.four', Date) == null
        config.empty.value == 'development'
        !config.dataSource
        !config.getProperty('dataSource')
        !config.get('dataSource')
    }

    def "ensure the config for environment is merged with single environment block with parseFlatMap false"() {
        given: "A PropertySourcesConfig instance"
        def propertySource = new YamlPropertySourceLoader()
        Resource resource = new FileSystemResource(getClass().getClassLoader().getResource("foo-plugin-environments.yml").getFile())

        when:
        def yamlPropertiesSource = propertySource.load('foo-plugin-environments.yml', resource, Arrays.asList("dataSource", "hibernate"))
        def config = new PropertySourcesConfig(yamlPropertiesSource.first())

        then: "These will not be navigable due to false parseFlatKeys"
        config.one == 2
        config.two == 3
        !config.four.five
        config.getProperty('one', String) == '2'
        config.getProperty('three.four', String) == '45'
        config.getProperty('three', String) == null
        config.get('three.four') == 45
        config.getProperty('three.four') == '45'
        config.getProperty('three.four', Date) == null
        !config.dataSource
        !config.getProperty('dataSource')
        !config.get('dataSource')
    }

    def "ensure the config for environment is merged with multiple environment block"() {
        given: "A PropertySourcesConfig instance"
        def propertySource = new YamlPropertySourceLoader()
        Resource resource = new FileSystemResource(getClass().getClassLoader().getResource("foo-plugin-multiple-environments.yml").getFile())

        when:
        def yamlPropertiesSource = propertySource.load('foo-plugin-multiple-environments.yml', resource, Arrays.asList("dataSource", "hibernate"))
        def config = new PropertySourcesConfig(yamlPropertiesSource.first())

        then: "The config to be accessible with the merged env values"
        config.one == -2
        config.two == 3
        config.three.four == 45
        config.four.five == 45
        config.getProperty('one', String) == '-2'
        config.getProperty('three.four', String) == '45'
        config.getProperty('three', String) == null
        config.get('three.four') == 45
        config.get('four.five') == 45
        config.getProperty('three.four') == '45'
        config.getProperty('three.four', Date) == null
        config.getProperty('four.five') == '45'
        config.empty.value == 'development'
        !config.dataSource
        !config.getProperty('dataSource')
        !config.get('dataSource')
    }
    def "binds a list of objects to configuration properties element by element"() {
        given:
        def environment = environment(ITEMS_YAML)

        when:
        ItemsProperties bound = Binder.get(environment).bind('app', Bindable.of(ItemsProperties)).get()

        then: "each element is bound as the type the list declares"
        bound.items*.getClass() == [Item, Item]
        bound.items*.name == ['one', 'two']
        bound.items[0].paths == '/a/**'

        and: "a list inside an element is bound too"
        bound.items[1].tags == ['x', 'y']

        and: "a list of plain values is bound as before"
        bound.names == ['p', 'q']
    }

    def "presents a list of objects to the environment under the names Spring Boot binds"() {
        when:
        def environment = environment(ITEMS_YAML)

        then:
        environment.getProperty('app.items[0].name') == 'one'
        environment.getProperty('app.items[1].tags[1]') == 'y'
        environment.getProperty('app.items') == null
        !environment.containsProperty('app.items')

        and: "a list of plain values is also presented element by element"
        environment.getProperty('app.names[0]') == 'p'
        environment.getProperty('app.names[1]') == 'q'
        !environment.containsProperty('app.names')
    }

    def "the Grails config still reads a list of objects as a list"() {
        when:
        def config = new PropertySourcesConfig(load(ITEMS_YAML))

        then:
        config.getProperty('app.items', List)*.name == ['one', 'two']
        config.getProperty('app.names', List) == ['p', 'q']
    }

    def "resolves placeholders in YAML scalar lists bound to configuration properties with environment #variables"() {
        given:
        def source = load('''\
            app:
              allowedOrigins:
                - https://static.example.com
                - "${EXAMPLE_ALLOWED_ORIGIN:https://default.example.com}"
              ports: [8080, "${EXAMPLE_PORT:9090}"]
              flags: [true, "${EXAMPLE_FLAG:false}"]
              groups: [["${EXAMPLE_GROUP:primary}"], [secondary]]
              empty: []
              blanks: [first, null, "", last]
            '''.stripIndent())
        def runner = new ApplicationContextRunner()
                .withUserConfiguration(ScalarListConfiguration)
                .withInitializer { context ->
                    context.environment.propertySources.addFirst(source)
                    context.environment.propertySources.addFirst(new SystemEnvironmentPropertySource('testEnvironment', variables))
                }

        expect:
        runner.run { context ->
            assert !context.startupFailure
            def bound = context.getBean(ScalarListProperties)
            assert bound.allowedOrigins == ['https://static.example.com', origin]
            assert bound.ports == [8080, port]
            assert bound.flags == [true, flag]
            assert bound.groups == [[group], ['secondary']]
            assert bound.empty == []
            assert bound.blanks == ['first', '', '', 'last']
        }

        where:
        variables                                                                                                               | origin                        | port | flag  | group
        [:]                                                                                                                     | 'https://default.example.com' | 9090 | false | 'primary'
        [EXAMPLE_ALLOWED_ORIGIN: 'https://custom.example.com', EXAMPLE_PORT: '7070', EXAMPLE_FLAG: 'true', EXAMPLE_GROUP: 'custom'] | 'https://custom.example.com'  | 7070 | true  | 'custom'
    }

    def "presents lists nested in lists under consecutive indexes"() {
        given:
        def yaml = '''\
            app:
              groups: [[a, b], [c]]
              deep: [[[x]]]
              nested: [[{name: n}]]
            '''.stripIndent()
        def source = load(yaml)
        def environment = environment(yaml)
        def binder = Binder.get(environment)
        def listOfLists = { Class type ->
            Bindable.of(ResolvableType.forClassWithGenerics(List, ResolvableType.forClassWithGenerics(List, type)))
        }

        expect: "each element is named with one index per level"
        source.getPropertyNames().findAll { String name -> name.startsWith('app.') } as Set == [
                'app.groups[0][0]', 'app.groups[0][1]', 'app.groups[1][0]',
                'app.deep[0][0][0]',
                'app.nested[0][0].name',
        ] as Set
        environment.getProperty('app.groups[0][1]') == 'b'
        environment.getProperty('app.deep[0][0][0]') == 'x'
        environment.getProperty('app.nested[0][0].name') == 'n'

        and: "Spring Boot binds the nested lists"
        binder.bind('app.groups', listOfLists(String)).get() == [['a', 'b'], ['c']]
        binder.bind('app.nested', listOfLists(Item)).get()*.getAt(0)*.name == ['n']

        and: "the Grails config reads the nested lists as lists"
        def config = new PropertySourcesConfig(source)
        config.getProperty('app.groups', List) == [['a', 'b'], ['c']]
        config.getProperty('app.deep', List) == [[['x']]]
    }

    def "a higher priority scalar list replaces a lower priority list when binding"() {
        given:
        def environment = environment('app.names: [first, second]')
        environment.propertySources.addFirst(load('app.names: ["${EXAMPLE_NAME:replacement}"]', 'override'))

        expect:
        Binder.get(environment).bind('app.names', Bindable.listOf(String)).get() == ['replacement']
    }

    private static final String ITEMS_YAML = '''\
        app:
          items:
            - name: one
              paths: /a/**
            - name: two
              tags: [x, y]
          names: [p, q]
        '''.stripIndent()

    private static NavigableMapPropertySource load(String yaml, String name = 'test') {
        (NavigableMapPropertySource) new YamlPropertySourceLoader().load(name, new ByteArrayResource(yaml.bytes)).first()
    }

    private static StandardEnvironment environment(String yaml) {
        def environment = new StandardEnvironment()
        environment.propertySources.addFirst(load(yaml))
        ConfigurationPropertySources.attach(environment)
        environment
    }

    static class ItemsProperties {
        List<Item> items = []
        List<String> names = []
    }

    static class Item {
        String name
        String paths
        List<String> tags
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ScalarListProperties)
    static class ScalarListConfiguration {
    }

    @ConfigurationProperties('app')
    static class ScalarListProperties {
        List<String> allowedOrigins
        List<Integer> ports
        List<Boolean> flags
        List<List<String>> groups
        List<String> empty = ['default']
        List<String> blanks
    }
}
