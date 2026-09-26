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
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.core.env.StandardEnvironment
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

        and: "a list of plain values is presented as it is"
        environment.getProperty('app.names', List) == ['p', 'q']
    }

    def "the Grails config still reads a list of objects as a list"() {
        when:
        def config = new PropertySourcesConfig(load(ITEMS_YAML))

        then:
        config.getProperty('app.items', List)*.name == ['one', 'two']
        config.getProperty('app.names', List) == ['p', 'q']
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

    private static NavigableMapPropertySource load(String yaml) {
        (NavigableMapPropertySource) new YamlPropertySourceLoader().load('test', new ByteArrayResource(yaml.bytes)).first()
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
}
