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
package grails.aot

import groovy.lang.GroovyClassLoader

import org.springframework.aot.generate.ClassNameGenerator
import org.springframework.aot.generate.DefaultGenerationContext
import org.springframework.aot.generate.GeneratedFiles
import org.springframework.aot.generate.InMemoryGeneratedFiles
import org.springframework.context.aot.ApplicationContextAotGenerator
import org.springframework.context.support.GenericApplicationContext
import org.springframework.javapoet.ClassName

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.plugins.DefaultGrailsPluginManager
import grails.plugins.GrailsPluginManager
import org.apache.grails.core.plugins.DefaultPluginDiscovery
import org.apache.grails.core.plugins.PluginDiscovery
import org.grails.spring.DefaultRuntimeSpringConfiguration
import org.spockframework.runtime.SpockAssertionError
import spock.lang.PendingFeature
import spock.lang.Specification

class GrailsAotSmokeSpec extends Specification {

    void 'Spring AOT generates an initializer for a minimal Grails context'() {
        given: 'a non-refreshed context with the minimal Grails application bean'
            def context = new GenericApplicationContext()
            context.registerBean(DefaultGrailsApplication)

        when: 'Spring processes the context through its public AOT API'
            ClassName initializer = new ApplicationContextAotGenerator().processAheadOfTime(context, generationContext())

        then: 'an initializer entry point is generated'
            initializer != null
            initializer.simpleName().contains('ApplicationContextInitializer')

        cleanup:
            context.close()
    }

    @PendingFeature(exceptions = [SpockAssertionError], reason = 'Blocker: Grails discovers artefact classes at runtime, and no AOT contribution currently records that runtime artefact registry as generated source.')
    void 'Spring AOT records a dynamically discovered Grails artefact'() {
        given: 'an artefact discovered from a runtime Groovy class loader'
            def classLoader = new GroovyClassLoader()
            Class<?> dynamicArtefact = classLoader.parseClass('''
                package grails.aot.dynamic
                class AotDynamicController { }
            ''')
            def application = new DefaultGrailsApplication([dynamicArtefact] as Class<?>[], classLoader)
            def context = new GenericApplicationContext()
            context.beanFactory.registerSingleton(GrailsApplication.APPLICATION_ID, application)
            def generationContext = generationContext()

        when: 'Grails initializes its runtime artefact registry before Spring processes the context through its public AOT API'
            application.initialise()
            if (!application.allArtefacts.contains(dynamicArtefact)) {
                throw new IllegalStateException('Grails artefact registry did not contain the dynamically loaded controller before AOT processing')
            }
            new ApplicationContextAotGenerator().processAheadOfTime(context, generationContext)
            generationContext.writeGeneratedContent()

        then: 'the generated source preserves the runtime-discovered artefact type'
            generatedSource(generationContext).contains(dynamicArtefact.name)

        cleanup:
            context.close()
            classLoader.close()
    }

    @PendingFeature(exceptions = [SpockAssertionError], reason = 'Blocker: plugin doWithSpring closures are evaluated from runtime Groovy classes, with no AOT contribution that converts their bean definitions into build-time generated source.')
    void 'Spring AOT records a dynamically loaded plugin doWithSpring bean'() {
        given: 'a plugin class loaded at runtime with a Groovy bean-definition closure'
            def classLoader = new GroovyClassLoader()
            Class<?> dynamicPlugin = classLoader.parseClass('''
                class AotDynamicGrailsPlugin {
                    def version = '1.0'
                    def doWithSpring = {
                        dynamicPluginBean(Object)
                    }
                }
            ''')
            def application = new DefaultGrailsApplication([] as Class<?>[], classLoader)
            def context = new GenericApplicationContext()
            def runtimeContext = new GenericApplicationContext()
            application.mainContext = context
            def discovery = new DefaultPluginDiscovery([dynamicPlugin] as Class<?>[])
            discovery.loadPluginsFromClasspath = false
            discovery.init(context.environment)
            def pluginManager = new DefaultGrailsPluginManager(application, discovery)
            pluginManager.loadPlugins()
            context.beanFactory.registerSingleton(PluginDiscovery.BEAN_NAME, discovery)
            context.beanFactory.registerSingleton(GrailsPluginManager.BEAN_NAME, pluginManager)
            def generationContext = generationContext()

        when: 'the runtime plugin configuration phase registers its DSL bean before Spring processes the context through its public AOT API'
            def springConfiguration = new DefaultRuntimeSpringConfiguration()
            pluginManager.doRuntimeConfiguration(springConfiguration)
            springConfiguration.registerBeansWithContext(runtimeContext)
            if (!runtimeContext.beanFactory.containsBeanDefinition('dynamicPluginBean')) {
                throw new IllegalStateException('Plugin doWithSpring did not register dynamicPluginBean before AOT processing')
            }
            new ApplicationContextAotGenerator().processAheadOfTime(context, generationContext)
            generationContext.writeGeneratedContent()

        then: 'the generated source preserves the dynamically registered plugin bean definition'
            generatedSource(generationContext).contains('dynamicPluginBean')

        cleanup:
            context.close()
            runtimeContext.close()
            classLoader.close()
    }

    private static DefaultGenerationContext generationContext() {
        new DefaultGenerationContext(
                new ClassNameGenerator(ClassName.get(GrailsAotSmokeSpec)),
                new InMemoryGeneratedFiles())
    }

    private static String generatedSource(DefaultGenerationContext generationContext) {
        generationContext.generatedFiles.getGeneratedFiles(GeneratedFiles.Kind.SOURCE).values().collect {
            it.inputStream.text
        }.join('\n')
    }
}
