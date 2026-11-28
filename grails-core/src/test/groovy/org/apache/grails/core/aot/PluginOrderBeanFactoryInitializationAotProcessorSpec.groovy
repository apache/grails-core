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
package org.apache.grails.core.aot

import java.nio.file.Files
import java.nio.file.Path

import org.springframework.aot.generate.DefaultGenerationContext
import org.springframework.aot.generate.GeneratedFiles.Kind
import org.springframework.aot.generate.InMemoryGeneratedFiles
import org.springframework.aot.generate.NameGenerator
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates
import org.springframework.beans.factory.aot.BeanFactoryInitializationCode
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.env.StandardEnvironment
import org.springframework.javapoet.ClassName
import spock.lang.Specification
import spock.lang.TempDir
import spock.util.environment.RestoreSystemProperties

import grails.core.DefaultGrailsApplication
import grails.plugins.DefaultGrailsPluginManager
import grails.plugins.GrailsPluginManager
import grails.plugins.exceptions.PluginException
import org.apache.grails.core.plugins.DefaultPluginDiscovery

/**
 * Covers the plugin order an ahead-of-time build records, and the check an application running on the
 * generated code makes against it.
 */
@RestoreSystemProperties
class PluginOrderBeanFactoryInitializationAotProcessorSpec extends Specification {

    @TempDir
    Path temp

    GenericApplicationContext context = new GenericApplicationContext()

    void cleanup() {
        context.close()
    }

    void 'the build records the plugins in plugin order, and carries the record into an image'() {
        given:
        def gcl = new GroovyClassLoader()
        context.beanFactory.registerSingleton(GrailsPluginManager.BEAN_NAME, manager(gcl, pluginClasses(gcl)))
        def generation = new DefaultGenerationContext(
                new NameGenerator(ClassName.get('com.example', 'Order')), new InMemoryGeneratedFiles())

        when:
        new PluginOrderBeanFactoryInitializationAotProcessor().processAheadOfTime(context.beanFactory)
                .applyTo(generation, Mock(BeanFactoryInitializationCode))

        then:
        def files = ((InMemoryGeneratedFiles) generation.generatedFiles).getGeneratedFiles(Kind.RESOURCE)
        files[PluginOrderBeanFactoryInitializationAotProcessor.PLUGIN_ORDER_PATH].inputStream.text ==
                'aotOrderZzz\naotOrderAaa\n'
        RuntimeHintsPredicates.resource().forResource(PluginOrderBeanFactoryInitializationAotProcessor.PLUGIN_ORDER_PATH)
                .test(generation.runtimeHints)
    }

    void 'without a plugin manager the build records nothing'() {
        expect:
        new PluginOrderBeanFactoryInitializationAotProcessor().processAheadOfTime(context.beanFactory) == null
    }

    void 'an application running on generated code loads its plugins when they are in the recorded order'() {
        given:
        System.setProperty('spring.aot.enabled', 'true')
        def loader = recording('aotOrderZzz\naotOrderAaa\n')

        when:
        def manager = manager(loader, pluginClasses(loader))

        then:
        manager.allPlugins*.name == ['aotOrderZzz', 'aotOrderAaa']
    }

    void 'an application running on generated code with other plugins than its build fails, naming both orders'() {
        given:
        System.setProperty('spring.aot.enabled', 'true')
        def loader = recording('aotOrderAaa\nsomethingElse\n')

        when:
        manager(loader, pluginClasses(loader))

        then:
        PluginException e = thrown()
        e.message.contains('[aotOrderZzz, aotOrderAaa]')
        e.message.contains('[aotOrderAaa, somethingElse]')
    }

    void 'the recorded order is not consulted when the application does not run on generated code'() {
        given:
        System.clearProperty('spring.aot.enabled')
        def loader = recording('somethingElse\n')

        when:
        def manager = manager(loader, pluginClasses(loader))

        then:
        manager.allPlugins*.name == ['aotOrderZzz', 'aotOrderAaa']
    }

    private GroovyClassLoader recording(String order) {
        Path file = temp.resolve(PluginOrderBeanFactoryInitializationAotProcessor.PLUGIN_ORDER_PATH)
        Files.createDirectories(file.parent)
        Files.writeString(file, order)
        def loader = new GroovyClassLoader(getClass().classLoader)
        loader.addURL(temp.toUri().toURL())
        loader
    }

    private static List<Class<?>> pluginClasses(GroovyClassLoader loader) {
        [loader.parseClass("@grails.plugins.metadata.GrailsPluginDescriptor(after = ['aotOrderZzz']) class AotOrderAaaGrailsPlugin extends grails.plugins.Plugin { }"),
         loader.parseClass('@grails.plugins.metadata.GrailsPluginDescriptor class AotOrderZzzGrailsPlugin extends grails.plugins.Plugin { }')]
    }

    private static DefaultGrailsPluginManager manager(ClassLoader loader, List<Class<?>> pluginClasses) {
        def discovery = new DefaultPluginDiscovery(pluginClasses as Class<?>[])
        discovery.loadPluginsFromClasspath = false
        discovery.init(new StandardEnvironment())
        def manager = new DefaultGrailsPluginManager(new DefaultGrailsApplication([] as Class[], loader), discovery)
        manager.loadPlugins()
        manager
    }
}
