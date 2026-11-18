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

import org.springframework.aot.AotDetector
import org.springframework.aot.generate.ClassNameGenerator
import org.springframework.aot.generate.DefaultGenerationContext
import org.springframework.aot.generate.InMemoryGeneratedFiles
import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.support.BeanRegistryAdapter
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.context.annotation.AnnotationConfigUtils
import org.springframework.context.aot.ApplicationContextAotGenerator
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.SpringProperties
import org.springframework.javapoet.ClassName

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.plugins.DefaultGrailsPluginManager
import grails.plugins.GrailsPlugin
import grails.plugins.GrailsPluginManager
import org.apache.grails.core.plugins.DefaultPluginDiscovery
import spock.lang.Specification

/**
 * Covers the core plugin's behaviour under Spring's ahead-of-time processing: the bean definitions
 * it contributes must be expressible as generated code, and the configuration class post-processor
 * it registers must stand down where the context already has its bean definitions generated.
 */
class CoreGrailsPluginAotSpec extends Specification {

    GenericApplicationContext context = new GenericApplicationContext()

    void cleanup() {
        SpringProperties.setProperty(AotDetector.AOT_ENABLED, null)
        context.close()
    }

    /**
     * Mirrors the registrar phase of {@code GrailsApplicationPostProcessor}: every enabled plugin's
     * {@link BeanRegistrar} applied against the registry through the same adapter the runtime uses.
     */
    private void applyCorePluginRegistrar() {
        GrailsApplication application = new DefaultGrailsApplication()
        application.applicationContext = context
        application.initialise()

        def discovery = new DefaultPluginDiscovery([CoreGrailsPlugin] as Class<?>[])
        discovery.loadPluginsFromClasspath = false
        discovery.init(context.environment)

        GrailsPluginManager pluginManager = new DefaultGrailsPluginManager(application, discovery)
        context.beanFactory.registerSingleton(GrailsApplication.APPLICATION_ID, application)
        context.beanFactory.registerSingleton(GrailsPluginManager.BEAN_NAME, pluginManager)
        pluginManager.loadPlugins()

        for (GrailsPlugin plugin : pluginManager.allPlugins) {
            BeanRegistrar registrar = plugin.beanRegistrar
            if (registrar != null) {
                new BeanRegistryAdapter(context, context, context.environment, registrar.getClass())
                        .register(registrar)
            }
        }
    }

    void 'the configuration class post-processor is registered when generated artifacts are not in use'() {
        given:
            SpringProperties.setProperty(AotDetector.AOT_ENABLED, 'false')

        when:
            applyCorePluginRegistrar()

        then: 'plugin-contributed @Configuration beans still need parsing at runtime'
            context.containsBeanDefinition('grailsConfigurationClassPostProcessor')
    }

    void 'the configuration class post-processor is withheld when generated artifacts are in use'() {
        given: 'the flag an AOT-optimized application is started with'
            SpringProperties.setProperty(AotDetector.AOT_ENABLED, 'true')

        when:
            applyCorePluginRegistrar()

        then: 'the configuration classes were parsed at build time, so parsing them again would ' +
                'collide with the definitions already generated'
            !context.containsBeanDefinition('grailsConfigurationClassPostProcessor')
    }

    void 'a second configuration class post-processor is not added to a context that has one'() {
        given: 'the processor Spring registers as part of setting up annotation configuration'
            SpringProperties.setProperty(AotDetector.AOT_ENABLED, 'false')
            AnnotationConfigUtils.registerAnnotationConfigProcessors(context)

        when:
            applyCorePluginRegistrar()

        then: 'the one already there sees the plugin definitions, which are registered ahead of it; ' +
                'a second parses the same registry again and, while code is being generated, writes ' +
                'out the same import-aware post-processor a second time'
            !context.containsBeanDefinition('grailsConfigurationClassPostProcessor')
            context.containsBeanDefinition(AnnotationConfigUtils.CONFIGURATION_ANNOTATION_PROCESSOR_BEAN_NAME)
    }

    void "a datastore's own proxy handler is not replaced"() {
        given: 'what a GORM implementation declares from doWithSpring, which runs earlier'
            SpringProperties.setProperty(AotDetector.AOT_ENABLED, 'false')
            context.registerBeanDefinition('proxyHandler', new RootBeanDefinition(String))

        when:
            applyCorePluginRegistrar()

        then: 'it knows how to unwrap that datastore\'s proxies, and this one does not'
            context.getBeanDefinition('proxyHandler').beanClassName == String.name
    }

    void 'the proxy handler is registered where no datastore declared one'() {
        given:
            SpringProperties.setProperty(AotDetector.AOT_ENABLED, 'false')

        when:
            applyCorePluginRegistrar()

        then:
            context.containsBeanDefinition('proxyHandler')
    }

    void 'the core plugin bean definitions can be generated ahead of time'() {
        given:
            SpringProperties.setProperty(AotDetector.AOT_ENABLED, 'false')
            applyCorePluginRegistrar()

        and:
            def generationContext = new DefaultGenerationContext(
                    new ClassNameGenerator(ClassName.get('org.apache.grails.common.aot.test', 'CoreAotTest')),
                    new InMemoryGeneratedFiles())

        when: 'the context is processed exactly as the processAot build task processes it'
            new ApplicationContextAotGenerator().processAheadOfTime(context, generationContext)

        then: 'no definition holds a value the generator cannot express as code -- a live instance ' +
                'passed as a constructor argument or property value would fail here'
            noExceptionThrown()
    }

    void 'a definition holding a live instance fails generation'() {
        given:
            SpringProperties.setProperty(AotDetector.AOT_ENABLED, 'false')
            applyCorePluginRegistrar()

        and: 'the shape this plugin must avoid: an already-constructed object as a constructor argument'
            def definition = new GenericBeanDefinition()
            definition.beanClass = StringBuilder
            definition.constructorArgumentValues.addIndexedArgumentValue(0, new DefaultGrailsApplication())
            context.registerBeanDefinition('holdsALiveInstance', definition)

        and:
            def generationContext = new DefaultGenerationContext(
                    new ClassNameGenerator(ClassName.get('org.apache.grails.common.aot.test', 'LiveInstanceAotTest')),
                    new InMemoryGeneratedFiles())

        when:
            new ApplicationContextAotGenerator().processAheadOfTime(context, generationContext)

        then: 'proving the preceding check is capable of failing'
            Exception e = thrown()
            e.message.contains('holdsALiveInstance')
    }
}
