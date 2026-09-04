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

package org.grails.plugins

import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer

import grails.config.ConfigProperties
import grails.config.Settings
import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication

import org.grails.spring.context.support.GrailsPlaceholderConfigurer

import spock.lang.Issue
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

class CoreAutoConfigurationSpec extends Specification {

    private GrailsApplication grailsApplication = new DefaultGrailsApplication()

    private ApplicationContextRunner contextRunner() {
        // Both classLoader and grailsConfigProperties are derived from the GrailsApplication,
        // which the core plugin itself contributes at runtime rather than this auto-configuration.
        new ApplicationContextRunner()
                .withInitializer(registerSingleton('grailsApplication', grailsApplication))
                .withConfiguration(AutoConfigurations.of(CoreAutoConfiguration, PropertyPlaceholderAutoConfiguration))
    }

    private static ApplicationContextInitializer<ConfigurableApplicationContext> registerSingleton(String name, Object instance) {
        return { ConfigurableApplicationContext context ->
            context.beanFactory.registerSingleton(name, instance)
        } as ApplicationContextInitializer<ConfigurableApplicationContext>
    }

    // Placeholders are substituted by BeanDefinitionVisitor over registered bean definitions, so the
    // probe has to be a real definition carrying a property value - a supplier-registered bean has
    // no definition-level string for the configurer to visit.
    private static ApplicationContextInitializer<ConfigurableApplicationContext> registerProbe(String placeholder) {
        return { ConfigurableApplicationContext context ->
            RootBeanDefinition definition = new RootBeanDefinition(PlaceholderProbe)
            definition.propertyValues.add('name', placeholder)
            ((BeanDefinitionRegistry) context.beanFactory).registerBeanDefinition('placeholderProbe', definition)
        } as ApplicationContextInitializer<ConfigurableApplicationContext>
    }

    void 'the core beans register'() {
        expect:
        contextRunner().run { context ->
            assert context.getBean('classLoader') instanceof ClassLoader
            assert context.getBean('grailsConfigProperties') instanceof ConfigProperties
            assert context.getBean('propertySourcesPlaceholderConfigurer') instanceof GrailsPlaceholderConfigurer
        }
    }

    void 'the classLoader bean exposes the GrailsApplication class loader'() {
        expect:
        contextRunner().run { context ->
            assert context.getBean('classLoader').is(grailsApplication.classLoader)
        }
    }

    void 'the classLoader bean is primary, so it wins over another ClassLoader candidate'() {
        given:
        ClassLoader competing = new URLClassLoader(new URL[0])

        expect:
        contextRunner()
                .withInitializer(registerSingleton('someOtherClassLoader', competing))
                .run { context ->
                    assert context.getBeanNamesForType(ClassLoader).length == 2
                    assert context.getBean(ClassLoader).is(grailsApplication.classLoader)
                }
    }

    void 'grailsConfigProperties reads through to the application config'() {
        given:
        grailsApplication.config.foo = [bar: 'test']

        expect:
        contextRunner().run { context ->
            assert context.getBean('grailsConfigProperties', ConfigProperties).getProperty('foo.bar') == 'test'
        }
    }

    void "the Grails placeholder configurer replaces Boot's, which orders after it and backs off"() {
        expect:
        contextRunner().run { context ->
            def configurers = context.getBeansOfType(PropertySourcesPlaceholderConfigurer)
            assert configurers.size() == 1
            assert configurers.values().first() instanceof GrailsPlaceholderConfigurer
        }
    }

    void 'bean definition placeholders are resolved with the default prefix'() {
        expect:
        contextRunner()
                .withInitializer(registerProbe('${foo.bar}'))
                .withPropertyValues('foo.bar=test')
                .run { context ->
                    assert context.getBean(PlaceholderProbe).name == 'test'
                }
    }

    void 'an unresolvable placeholder is left in place rather than failing the context'() {
        expect:
        contextRunner()
                .withInitializer(registerProbe('${no.such.property}'))
                .run { context ->
                    assert context.getBean(PlaceholderProbe).name == '${no.such.property}'
                }
    }

    void 'a configured placeholder prefix is applied to the configurer'() {
        expect:
        contextRunner()
                .withInitializer(registerProbe('@{foo.bar}'))
                .withPropertyValues("${Settings.SPRING_PLACEHOLDER_PREFIX}=@{", 'foo.bar=test')
                .run { context ->
                    assert context.getBean(PlaceholderProbe).name == 'test'
                }
    }

    @Issue('GRAILS-10130')
    @RestoreSystemProperties
    void 'a system property is resolved in a bean definition placeholder'() {
        given:
        System.setProperty('foo.bar', 'test')

        expect:
        contextRunner()
                .withInitializer(registerProbe('${foo.bar}'))
                .run { context ->
                    assert context.getBean(PlaceholderProbe).name == 'test'
                }
    }

    void 'a configured placeholder prefix displaces the default one'() {
        expect:
        contextRunner()
                .withInitializer(registerProbe('${foo.bar}'))
                .withPropertyValues("${Settings.SPRING_PLACEHOLDER_PREFIX}=@{", 'foo.bar=test')
                .run { context ->
                    assert context.getBean(PlaceholderProbe).name == '${foo.bar}'
                }
    }

    static class PlaceholderProbe {

        String name

    }

}
