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

import java.nio.file.Path

import org.springframework.aot.generate.DefaultGenerationContext
import org.springframework.aot.generate.GeneratedFiles.Kind
import org.springframework.aot.generate.InMemoryGeneratedFiles
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates
import org.springframework.beans.factory.aot.BeanFactoryInitializationCode
import org.springframework.context.support.GenericApplicationContext
import org.springframework.javapoet.ClassName
import spock.lang.Specification
import spock.lang.TempDir

import grails.plugins.GrailsPlugin
import grails.plugins.GrailsPluginManager
import grails.plugins.exceptions.PluginException
import org.apache.grails.core.plugins.PluginClassFixtures
import org.apache.grails.core.plugins.PluginUtils
import org.springframework.aot.generate.NameGenerator

/**
 * Covers each plugin's configuration reaching an application running on generated code: a plugin's
 * {@code plugin.yml} or {@code plugin.groovy} sits beside its descriptor, under
 * {@code META-INF/grails/plugins/<name>/}, and an image carries it only when asked.
 */
class PluginConfigurationBeanFactoryInitializationAotProcessorSpec extends Specification {

    @TempDir
    Path temp

    GenericApplicationContext context = new GenericApplicationContext()

    List<URLClassLoader> loaders = []

    void cleanup() {
        context.close()
        loaders*.close()
    }

    /** A plugin class in a root of its own, as each plugin's JAR is, with the namespaced configuration given, if any. */
    private Class<?> plugin(String className, String pluginName, String configFileName = null, String configuration = null) {
        Path root = temp.resolve(className)
        PluginClassFixtures.compilePlugin(root, className)
        if (configFileName) {
            PluginClassFixtures.write(root, PluginUtils.getPluginConfigPath(pluginName, configFileName), configuration)
        }
        URLClassLoader loader = PluginClassFixtures.loaderOver(root)
        loaders << loader
        loader.loadClass(className)
    }

    private void loaded(Map<String, Class<?>> pluginClassesByName) {
        GrailsPlugin[] plugins = pluginClassesByName.collect { String name, Class<?> pluginClass ->
            Stub(GrailsPlugin) {
                getName() >> name
                getPluginClass() >> pluginClass
            }
        } as GrailsPlugin[]
        context.beanFactory.registerSingleton(GrailsPluginManager.BEAN_NAME, Stub(GrailsPluginManager) {
            getAllPlugins() >> plugins
        })
    }

    private DefaultGenerationContext generate() {
        def generation = new DefaultGenerationContext(
                new NameGenerator(ClassName.get('com.example', 'Configuration')), new InMemoryGeneratedFiles())
        new PluginConfigurationBeanFactoryInitializationAotProcessor().processAheadOfTime(context.beanFactory)
                .applyTo(generation, Mock(BeanFactoryInitializationCode))
        generation
    }

    void 'each plugin configuration is carried into an image at its namespaced path, without a copy'() {
        given: 'two plugins with a plugin.yml each, in their own roots'
            loaded(alpha: plugin('fixture.alpha.AlphaGrailsPlugin', 'alpha', 'plugin.yml', 'alpha: one'),
                    beta: plugin('fixture.beta.BetaGrailsPlugin', 'beta', 'plugin.yml', 'beta: two'))

        when:
            def generation = generate()

        then: 'each namespaced path is registered'
            RuntimeHintsPredicates.resource().forResource(PluginUtils.getPluginConfigPath('alpha', 'plugin.yml')).test(generation.runtimeHints)
            RuntimeHintsPredicates.resource().forResource(PluginUtils.getPluginConfigPath('beta', 'plugin.yml')).test(generation.runtimeHints)

        and: 'nothing is generated, since the paths are already distinct'
            ((InMemoryGeneratedFiles) generation.generatedFiles).getGeneratedFiles(Kind.RESOURCE).isEmpty()
    }

    void 'a configuration written as Groovy is carried under its own name'() {
        given:
            loaded(scripted: plugin('fixture.groovy.ScriptedGrailsPlugin', 'scripted', 'plugin.groovy', 'scripted = true'))

        expect: 'the name is how its format is chosen'
            RuntimeHintsPredicates.resource().forResource(PluginUtils.getPluginConfigPath('scripted', 'plugin.groovy'))
                    .test(generate().runtimeHints)
    }

    void 'plugins without configuration contribute nothing'() {
        given:
            loaded(gamma: plugin('fixture.gamma.GammaGrailsPlugin', 'gamma'))

        expect:
            new PluginConfigurationBeanFactoryInitializationAotProcessor().processAheadOfTime(context.beanFactory) == null
    }

    void 'a plugin with both namespaced plugin.yml and plugin.groovy fails AOT processing'() {
        given: 'a plugin whose namespace contains both plugin.yml and plugin.groovy'
            Class<?> pluginClass = plugin('fixture.dual.DualGrailsPlugin', 'dual', 'plugin.yml', 'dual: true')
            Path root = temp.resolve('fixture.dual.DualGrailsPlugin')
            PluginClassFixtures.write(root, PluginUtils.getPluginConfigPath('dual', PluginUtils.PLUGIN_GROOVY_CONFIG), 'dual = true')
            loaded(dual: pluginClass)

        when:
            new PluginConfigurationBeanFactoryInitializationAotProcessor().processAheadOfTime(context.beanFactory)

        then:
            def e = thrown(PluginException)
            e.message.contains('[dual]')
    }

    void 'a context that is not a Grails application contributes nothing'() {
        expect: 'a plain Spring application being generated has no plugins to ask'
            new PluginConfigurationBeanFactoryInitializationAotProcessor().processAheadOfTime(context.beanFactory) == null
    }
}
