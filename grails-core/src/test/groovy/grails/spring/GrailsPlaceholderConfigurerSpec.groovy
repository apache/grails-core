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
package grails.spring

import grails.util.Holders
import grails.core.DefaultGrailsApplication
import org.grails.config.NavigableMapPropertySource
import org.grails.config.yaml.YamlPropertySourceLoader
import org.grails.spring.context.support.GrailsPlaceholderConfigurer
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.support.BeanDefinitionBuilder
import org.springframework.boot.convert.ApplicationConversionService
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer
import org.springframework.core.env.Environment
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.MutablePropertySources
import org.springframework.core.env.PropertySource
import org.springframework.core.env.SystemEnvironmentPropertySource
import org.springframework.core.io.ByteArrayResource
import spock.lang.Issue
import spock.lang.Specification

/**
 * @author Graeme Rocher
 */
class GrailsPlaceholderConfigurerSpec extends Specification {

    void cleanup() {
        Holders.setConfig(null)
    }

    void "Test that property placeholder configuration works for simple properties in map syntax"() {
        when:"A bean is defined with a placeholder"
            def application = new DefaultGrailsApplication()
            application.config.foo = [bar: "test"]
            def bb = new BeanBuilder()
            bb.beans {
                addBeanFactoryPostProcessor(new GrailsPlaceholderConfigurer('${', application.config.toProperties()))
                testBean(TestBean) {
                    name = '${foo.bar}'
                }
            }
            def applicationContext = bb.createApplicationContext()
            def bean = applicationContext.getBean(TestBean)
        then:"The placeholder is replaced"
            bean.name == "test"

    }

    void "Test that property placeholder configuration works for simple properties in dot syntax"() {
        when:"A bean is defined with a placeholder"
        def application = new DefaultGrailsApplication()
        application.config.foo.bar="test"
        def bb = new BeanBuilder()
        bb.beans {
            addBeanFactoryPostProcessor(new GrailsPlaceholderConfigurer('${', application.config.toProperties()))
            testBean(TestBean) {
                name = '${foo.bar}'
            }
        }
        def applicationContext = bb.createApplicationContext()
        def bean = applicationContext.getBean(TestBean)
        then:"The placeholder is replaced"
        bean.name == "test"

    }

    @Issue('GRAILS-9490')
    void "Test that property placeholder configuration doesn't throw an error if invalid placeholders are configured"() {
        when:"A bean is defined with a placeholder"
        def application = new DefaultGrailsApplication()
        application.config.bar = [foo: "test"]
        application.config.more = [stuff: 'another ${place.holder}']
        def bb = new BeanBuilder()
        bb.beans {
            addBeanFactoryPostProcessor(new GrailsPlaceholderConfigurer('${', application.config.toProperties()))
            testBean(TestBean) {
                name = '${foo.bar}'
            }
        }
        def applicationContext = bb.createApplicationContext()
        def bean = applicationContext.getBean(TestBean)
        then:"The placeholder is replaced"
        bean.name == '${foo.bar}'

    }

    void "Test that property placeholder configuration works for simple properties with a custom placeholder prefix"() {
        when:"A bean is defined with a placeholder"
        def application = new DefaultGrailsApplication()
        application.config.foo = [bar: "test"]
        application.config['grails.spring.placeholder.prefix']='£{'
        application.setConfig(application.config)
        def bb = new BeanBuilder()
        bb.beans {
            addBeanFactoryPostProcessor(new GrailsPlaceholderConfigurer('£{', application.config.toProperties()))
            testBean(TestBean) {
                name = '£{foo.bar}'
            }
        }
        def applicationContext = bb.createApplicationContext()
        def bean = applicationContext.getBean(TestBean)
        then:"The placeholder is replaced"
        bean.name == "test"

    }

    void "a placeholder naming a list of plain values resolves to the elements of the list"() {
        given:
        def runner = contextRunner(load('app.names: [first, second]'))
                .withBean(ListValues)
                .withInitializer { context ->
                    context.registerBeanDefinition('testBean', BeanDefinitionBuilder.genericBeanDefinition(TestBean)
                            .addPropertyValue('name', '${app.names}')
                            .beanDefinition)
                }

        expect:
        runner.run { context ->
            assert !context.startupFailure
            def values = context.getBean(ListValues)
            assert values.list == ['first', 'second']
            assert values.array == ['first', 'second'] as String[]
            assert values.text == 'first,second'
            assert context.getBean(TestBean).name == 'first,second'
        }
    }

    void "a placeholder naming a list by a camelCase key resolves to the elements of the list"() {
        given:
        def runner = contextRunner(load('grails.cors.allowedOrigins: [https://a.example.com, https://b.example.com]'))
                .withBean(CorsValues)

        expect:
        runner.run { context ->
            assert !context.startupFailure
            def values = context.getBean(CorsValues)
            assert values.allowedOrigins == ['https://a.example.com', 'https://b.example.com']
            assert values.text == 'https://a.example.com,https://b.example.com'
        }
    }

    void "a placeholder names a list in any form binding matches: #placeholder reads #key"() {
        given:
        def runner = contextRunner(load("${key}: [first, second]"))
                .withInitializer { context ->
                    context.registerBeanDefinition('testBean', BeanDefinitionBuilder.genericBeanDefinition(TestBean)
                            .addPropertyValue('name', '${' + placeholder + '}')
                            .beanDefinition)
                }

        expect:
        runner.run { context ->
            assert !context.startupFailure
            assert context.getBean(TestBean).name == 'first,second'
        }

        where:
        key                   | placeholder
        'app.allowedOrigins'  | 'app.allowed-origins'
        'app.allowed-origins' | 'app.allowedOrigins'
        'app.allowedOrigins'  | 'app.allowedorigins'
    }

    void "the placeholders in the elements of a list are resolved with environment #variables"() {
        given:
        def runner = contextRunner(load('app.origins: [https://static.example.com, "${EXAMPLE_ORIGIN:https://default.example.com}"]'))
                .withInitializer { context ->
                    context.environment.propertySources.addFirst(new SystemEnvironmentPropertySource('testEnvironment', variables))
                }
                .withBean(OriginValues)

        expect:
        runner.run { context ->
            assert !context.startupFailure
            assert context.getBean(OriginValues).origins == ['https://static.example.com', origin]
        }

        where:
        variables                                         | origin
        [:]                                               | 'https://default.example.com'
        [EXAMPLE_ORIGIN: 'https://custom.example.com']   | 'https://custom.example.com'
    }

    void "a placeholder naming a list reads it from the source of the highest priority that sets it"() {
        given:
        def runner = contextRunner(load('app.names: [first, second]'))
                .withInitializer { context ->
                    context.environment.propertySources.addFirst(load('app.names: [replacement]', 'override'))
                }
                .withBean(ListValues)

        expect:
        runner.run { context ->
            assert !context.startupFailure
            assert context.getBean(ListValues).list == ['replacement']
        }
    }

    void "a value set under the name of a list, in any property source, is used instead of the list"() {
        given:
        def runner = contextRunner(load('app.names: [first, second]'))
                .withInitializer { context ->
                    context.environment.propertySources.addLast(new MapPropertySource('lower', ['app.names': 'single']))
                }
                .withBean(ListValues)

        expect:
        runner.run { context ->
            assert !context.startupFailure
            assert context.getBean(ListValues).text == 'single'
        }
    }

    void "a placeholder naming a list of objects is left as written, or resolves to its default"() {
        given:
        def runner = contextRunner(load('app.items: [{name: first}]')).withBean(ObjectListValues)

        expect:
        runner.run { context ->
            assert !context.startupFailure
            def values = context.getBean(ObjectListValues)
            assert values.items == '${app.items}'
            assert values.itemsOrDefault == 'none'
        }
    }

    void "a configurer given its own property sources reads the environment only when it is one of them: #description"() {
        given:
        def runner = new ApplicationContextRunner()
                .withInitializer { context ->
                    context.environment.propertySources.addFirst(load('app.names: [first, second]\napp.scalar: single'))
                    context.beanFactory.conversionService = ApplicationConversionService.sharedInstance
                    def sources = new MutablePropertySources()
                    if (unrelatedSource) {
                        sources.addLast(new MapPropertySource('unrelated', ['other.name': 'other']))
                    }
                    if (environmentSource) {
                        sources.addLast(environmentPropertySource(context.environment))
                    }
                    // Spring adds the environment and the local properties only to a configurer given no sources
                    context.registerBeanDefinition('grailsPlaceholderConfigurer', BeanDefinitionBuilder.genericBeanDefinition(GrailsPlaceholderConfigurer)
                            .addPropertyValue('propertySources', sources)
                            .beanDefinition)
                }
                .withBean(FallbackValues)

        expect:
        runner.run { context ->
            assert !context.startupFailure
            def values = context.getBean(FallbackValues)
            assert values.names == names
            assert values.scalar == scalar
        }

        where:
        description                   | unrelatedSource | environmentSource || names          | scalar
        'no sources'                  | false           | false             || 'fallback'     | 'fallback'
        'an unrelated source'         | true            | false             || 'fallback'     | 'fallback'
        'the environment among them'  | true            | true              || 'first,second' | 'single'
    }

    void "the environment still presents a list by its indexed names only"() {
        given:
        def runner = contextRunner(load('app.names: [first, second]')).withBean(ListValues)

        expect:
        runner.run { context ->
            assert !context.startupFailure
            assert context.getBean(ListValues).list == ['first', 'second']
            assert context.environment.getProperty('app.names') == null
            assert !context.environment.containsProperty('app.names')
            assert context.environment.getProperty('app.names[1]') == 'second'
        }
    }

    private static ApplicationContextRunner contextRunner(NavigableMapPropertySource configuration) {
        new ApplicationContextRunner()
                .withInitializer { context ->
                    context.environment.propertySources.addFirst(configuration)
                    // as SpringApplication sets it, so that a comma-separated value converts to a List or an array
                    context.beanFactory.conversionService = ApplicationConversionService.sharedInstance
                }
                .withUserConfiguration(PlaceholderConfiguration)
    }

    private static NavigableMapPropertySource load(String yaml, String name = 'test') {
        (NavigableMapPropertySource) new YamlPropertySourceLoader().load(name, new ByteArrayResource(yaml.bytes)).first()
    }

    // the environment as PropertySourcesPlaceholderConfigurer adds it to the sources it builds itself
    private static PropertySource<Environment> environmentPropertySource(Environment environment) {
        new PropertySource<Environment>(PropertySourcesPlaceholderConfigurer.ENVIRONMENT_PROPERTIES_PROPERTY_SOURCE_NAME, environment) {
            @Override
            Object getProperty(String name) {
                environment.getProperty(name)
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class PlaceholderConfiguration {

        // a static bean method, as CoreGrailsPlugin registers it, because the configurer is a BeanFactoryPostProcessor
        @Bean
        static GrailsPlaceholderConfigurer grailsPlaceholderConfigurer() {
            new GrailsPlaceholderConfigurer()
        }
    }

    static class ListValues {

        @Value('${app.names}')
        List<String> list

        @Value('${app.names}')
        String[] array

        @Value('${app.names}')
        String text
    }

    static class OriginValues {

        @Value('${app.origins}')
        List<String> origins
    }

    static class CorsValues {

        @Value('${grails.cors.allowedOrigins}')
        List<String> allowedOrigins

        @Value('${grails.cors.allowedOrigins}')
        String text
    }

    static class ObjectListValues {

        @Value('${app.items}')
        String items

        @Value('${app.items:none}')
        String itemsOrDefault
    }

    static class FallbackValues {

        @Value('${app.names:fallback}')
        String names

        @Value('${app.scalar:fallback}')
        String scalar
    }
}
class TestBean {
    String name
}
