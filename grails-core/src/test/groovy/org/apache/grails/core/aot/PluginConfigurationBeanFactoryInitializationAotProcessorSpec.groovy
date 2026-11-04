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

import org.springframework.aot.generate.ClassNameGenerator
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
import org.apache.grails.core.plugins.PluginClassFixtures
import org.apache.grails.core.plugins.PluginUtils

/**
 * Covers each plugin's configuration reaching an application running on generated code, where every class is
 * at the same root and a plugin's {@code plugin.yml} cannot be found relative to its class.
 *
 * <p>An image built without these lost every plugin's configuration.</p>
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

    /** A plugin class in a root of its own, as each plugin's JAR is, with the configuration given, if any. */
    private Class<?> plugin(String className, String configFileName = null, String configuration = null) {
        Path root = temp.resolve(className)
        PluginClassFixtures.compilePlugin(root, className)
        if (configFileName) {
            PluginClassFixtures.write(root, configFileName, configuration)
        }
        URLClassLoader loader = PluginClassFixtures.loaderOver(root)
        loaders << loader
        loader.loadClass(className)
    }

    private void loaded(Class<?>... pluginClasses) {
        GrailsPlugin[] plugins = pluginClasses.collect { Class<?> pluginClass ->
            Stub(GrailsPlugin) {
                getPluginClass() >> pluginClass
            }
        } as GrailsPlugin[]
        context.beanFactory.registerSingleton(GrailsPluginManager.BEAN_NAME, Stub(GrailsPluginManager) {
            getAllPlugins() >> plugins
        })
    }

    private DefaultGenerationContext generate() {
        def generation = new DefaultGenerationContext(
                new ClassNameGenerator(ClassName.get('com.example', 'Configuration')), new InMemoryGeneratedFiles())
        new PluginConfigurationBeanFactoryInitializationAotProcessor().processAheadOfTime(context.beanFactory)
                .applyTo(generation, Mock(BeanFactoryInitializationCode))
        generation
    }

    private static String generated(DefaultGenerationContext generation, String path) {
        ((InMemoryGeneratedFiles) generation.generatedFiles).getGeneratedFileContent(Kind.RESOURCE, path)
    }

    void 'each plugin configuration is copied under the name of its plugin class'() {
        given: 'two plugins with a plugin.yml each, at the same path in their own roots'
            loaded(plugin('fixture.alpha.AlphaGrailsPlugin', 'plugin.yml', 'alpha: one'),
                    plugin('fixture.beta.BetaGrailsPlugin', 'plugin.yml', 'beta: two'))
            String alpha = PluginUtils.getGeneratedConfigurationPath('fixture.alpha.AlphaGrailsPlugin', 'plugin.yml')
            String beta = PluginUtils.getGeneratedConfigurationPath('fixture.beta.BetaGrailsPlugin', 'plugin.yml')

        when:
            def generation = generate()

        then: 'each is written where only its own plugin looks for it'
            generated(generation, alpha) == 'alpha: one'
            generated(generation, beta) == 'beta: two'

        and: 'carried into an image'
            RuntimeHintsPredicates.resource().forResource(alpha).test(generation.runtimeHints)
            RuntimeHintsPredicates.resource().forResource(beta).test(generation.runtimeHints)
    }

    void 'a configuration written as Groovy keeps its name'() {
        given:
            loaded(plugin('fixture.groovy.ScriptedGrailsPlugin', 'plugin.groovy', 'scripted = true'))

        expect: 'the name is how its format is chosen'
            generated(generate(),
                    PluginUtils.getGeneratedConfigurationPath('fixture.groovy.ScriptedGrailsPlugin', 'plugin.groovy')) ==
                    'scripted = true'
    }

    void 'where every class is at one root each plugin still finds its own configuration'() {
        given: 'two configured plugins and one without configuration, copied while the roots still exist'
            loaded(plugin('fixture.alpha.AlphaGrailsPlugin', 'plugin.yml', 'alpha: one'),
                    plugin('fixture.beta.BetaGrailsPlugin', 'plugin.yml', 'beta: two'),
                    plugin('fixture.gamma.GammaGrailsPlugin'))
            def generation = generate()

        and: 'an image of them: their classes and the copies at a single root, and neither plugin.yml'
            Path image = temp.resolve('image')
            ['fixture.alpha.AlphaGrailsPlugin', 'fixture.beta.BetaGrailsPlugin', 'fixture.gamma.GammaGrailsPlugin'].each {
                PluginClassFixtures.compilePlugin(image, it)
            }
            ((InMemoryGeneratedFiles) generation.generatedFiles).getGeneratedFiles(Kind.RESOURCE).each { path, content ->
                PluginClassFixtures.write(image, path, content.inputStream.text)
            }
            URLClassLoader loader = PluginClassFixtures.loaderOver(image)
            loaders << loader

        expect:
            PluginUtils.readPluginConfiguration(loader.loadClass('fixture.alpha.AlphaGrailsPlugin')).inputStream.text == 'alpha: one'
            PluginUtils.readPluginConfiguration(loader.loadClass('fixture.beta.BetaGrailsPlugin')).inputStream.text == 'beta: two'
            PluginUtils.readPluginConfiguration(loader.loadClass('fixture.gamma.GammaGrailsPlugin')) == null
    }

    void 'plugins without configuration contribute nothing'() {
        given:
            loaded(plugin('fixture.gamma.GammaGrailsPlugin'))

        expect:
            new PluginConfigurationBeanFactoryInitializationAotProcessor().processAheadOfTime(context.beanFactory) == null
    }

    void 'a context that is not a Grails application contributes nothing'() {
        expect: 'a plain Spring application being generated has no plugins to ask'
            new PluginConfigurationBeanFactoryInitializationAotProcessor().processAheadOfTime(context.beanFactory) == null
    }
}
