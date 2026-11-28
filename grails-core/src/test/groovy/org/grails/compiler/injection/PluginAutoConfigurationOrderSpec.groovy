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
package org.grails.compiler.injection

import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Specification
import spock.lang.TempDir

import org.springframework.boot.autoconfigure.AutoConfiguration

import org.apache.grails.core.plugins.PluginUtils

/**
 * The compiler orders the auto-configurations that plugins' beans blocks compile to (their siblings) as the
 * plugins themselves are ordered, and publishes each plugin's sibling in its descriptor.
 */
class PluginAutoConfigurationOrderSpec extends Specification {

    @TempDir
    File project

    void 'a sibling comes after the siblings of the plugins its plugin depends on or comes after, and before those it comes before'() {
        given:
        File output = new File(project, 'classes')

        when:
        compile(output, [], [
                Alpha  : plugin('Alpha', ''),
                Beta   : plugin('Beta', "dependsOn = ['alpha']"),
                Gamma  : plugin('Gamma', "before = ['alpha'], after = ['absent']"),
                Delta  : '''@grails.plugins.metadata.GrailsPluginDescriptor(after = ['alpha'])
                            class DeltaGrailsPlugin extends grails.plugins.Plugin { }''',
                Epsilon: plugin('Epsilon', "after = ['delta', 'beta']")
        ])
        def loader = loader(output)

        then: 'each sibling is ordered against the others as the plugins are'
        order(loader, 'AlphaAutoConfiguration') == [after: [], before: []]
        order(loader, 'BetaAutoConfiguration') == [after: ['AlphaAutoConfiguration'], before: []]
        order(loader, 'GammaAutoConfiguration') == [after: [], before: ['AlphaAutoConfiguration']]

        and: 'a plugin without a sibling is looked through to the siblings of the plugins it comes after'
        order(loader, 'EpsilonAutoConfiguration') == [after: ['AlphaAutoConfiguration', 'BetaAutoConfiguration'], before: []]

        and: 'the descriptor names a plugin\'s sibling, and nothing for a plugin without one'
        definitions(loader).collectEntries { [(it.key): it.value.autoConfiguration] } ==
                [alpha: 'AlphaAutoConfiguration', beta: 'BetaAutoConfiguration', gamma: 'GammaAutoConfiguration',
                 delta: null, epsilon: 'EpsilonAutoConfiguration']

        cleanup:
        loader?.close()
    }

    void 'a plugin compiled later finds the sibling of a plugin on its class path through that plugin\'s descriptor'() {
        given:
        File library = new File(project, 'library')
        compile(library, [], [Alpha: plugin('Alpha', '')])
        File output = new File(project, 'classes')

        when:
        compile(output, [library], [Beta: plugin('Beta', "after = ['alpha']"), Gamma: plugin('Gamma', "before = ['alpha']")])
        def loader = loader(library, output)

        then:
        order(loader, 'BetaAutoConfiguration').after == ['AlphaAutoConfiguration']
        order(loader, 'GammaAutoConfiguration').before == ['AlphaAutoConfiguration']

        cleanup:
        loader?.close()
    }

    void 'an ordering against an auto-configuration that is not a Grails plugin\'s is kept beside the generated one'() {
        given:
        File output = new File(project, 'classes')

        when:
        compile(output, [], [
                Alpha: plugin('Alpha', ''),
                Beta : plugin('Beta', "after = ['alpha']",
                        '(after = org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration, ' +
                                "beforeName = 'org.example.SomethingAutoConfiguration')")
        ])
        def loader = loader(output)
        def annotation = loader.loadClass('BetaAutoConfiguration').getAnnotation(AutoConfiguration)

        then:
        annotation.after()*.name == ['org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration']
        annotation.afterName() as List == ['AlphaAutoConfiguration']
        annotation.beforeName() as List == ['org.example.SomethingAutoConfiguration']

        cleanup:
        loader?.close()
    }

    void 'a hand-written ordering on a plugin that names another Grails plugin\'s sibling fails the compilation: #description'() {
        given:
        File library = new File(project, 'library')
        compile(library, [], [Alpha: plugin('Alpha', '')])

        when:
        compile(new File(project, 'classes'), [library], [Beta: source])

        then:
        MultipleCompilationErrorsException e = thrown()
        e.message.contains('AlphaAutoConfiguration, the auto-configuration of the Grails plugin alpha')
        e.message.contains('use after/before on @GrailsPluginDescriptor')

        where:
        description                       | source
        '@AutoConfiguration(afterName)'   | plugin('Beta', '', "(afterName = 'AlphaAutoConfiguration')")
        '@AutoConfiguration(beforeName)'  | plugin('Beta', '', "(beforeName = ['AlphaAutoConfiguration'])")
        '@AutoConfigureAfter(name)'       | plugin('Beta', '', '', "@org.springframework.boot.autoconfigure.AutoConfigureAfter(name = 'AlphaAutoConfiguration')")
        '@AutoConfigureBefore(name)'      | plugin('Beta', '', '', "@org.springframework.boot.autoconfigure.AutoConfigureBefore(name = 'AlphaAutoConfiguration')")
    }

    private static String plugin(String name, String descriptor, String autoConfiguration = '', String extra = '') {
        """
            ${extra}
            @org.springframework.boot.autoconfigure.AutoConfiguration${autoConfiguration}
            @grails.plugins.metadata.GrailsPluginDescriptor(${descriptor})
            class ${name}GrailsPlugin extends grails.plugins.Plugin {
                def beans = {
                    bean('${name.toLowerCase()}Greeting', String) { 'hello' }
                }
            }
        """
    }

    private void compile(File output, List<File> classpath, Map<String, String> plugins) {
        File sources = new File(output.parentFile, output.name + '-src/src/main/groovy')
        sources.mkdirs()
        List<File> files = plugins.collect { String name, String text ->
            File file = new File(sources, "${name}GrailsPlugin.groovy")
            file.text = text
            file
        }
        def configuration = new CompilerConfiguration(targetDirectory: output)
        configuration.classpathList = classpath*.path
        def unit = new CompilationUnit(configuration)
        unit.addSources(files as File[])
        unit.compile()
    }

    private static URLClassLoader loader(File... directories) {
        new URLClassLoader(directories*.toURI()*.toURL() as URL[], PluginAutoConfigurationOrderSpec.classLoader)
    }

    private static Map<String, List<String>> order(ClassLoader loader, String sibling) {
        def annotation = loader.loadClass(sibling).getAnnotation(AutoConfiguration)
        [after: annotation.afterName() as List, before: annotation.beforeName() as List]
    }

    private static Map<String, grails.plugins.metadata.PluginDefinition> definitions(ClassLoader loader) {
        def scanned = new URLClassLoader(((URLClassLoader) loader).URLs, (ClassLoader) null)
        try {
            PluginUtils.scanPluginDescriptorResources(scanned).values().collectEntries { [(it.pluginName): it] }
        } finally {
            scanned.close()
        }
    }
}
