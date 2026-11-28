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
package org.apache.grails.core.plugins

import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

import spock.lang.Specification
import spock.lang.TempDir

import grails.plugins.exceptions.PluginException
import grails.util.Environment

import static org.apache.grails.core.plugins.PluginClassFixtures.compile
import static org.apache.grails.core.plugins.PluginClassFixtures.write

class PluginVerificationSpec extends Specification {

    static final List<String> ENVIRONMENTS = ['development', 'test', 'production']

    @TempDir
    Path root

    URLClassLoader loader

    void cleanup() {
        loader?.close()
    }

    private void plugin(String name, String attributes = '') {
        String className = "${name.capitalize()}VerifiedGrailsPlugin"
        compile(root, "${className}.groovy", """
            package verified
            @grails.plugins.metadata.GrailsPluginDescriptor(name = '${name}'${attributes ? ', ' + attributes : ''})
            class ${className} extends grails.plugins.Plugin { }
        """)
    }

    /** The fixture's class path, whose own resources come first, as an application's do on its class path. */
    private ClassLoader classPath() {
        loader = new URLClassLoader([root.toUri().toURL()] as URL[], PluginClassFixtures.classLoader) {
            @Override
            URL getResource(String name) {
                findResource(name) ?: super.getResource(name)
            }
        }
    }

    void 'returns the plugin order the application starts with in each environment'() {
        given: 'a plugin that comes after another, and one that only development loads'
        plugin('first')
        plugin('second', "after = ['first']")
        plugin('devOnly', "environmentsIncludes = ['development']")

        when:
        Map<String, List<String>> orders = PluginVerification.verify(classPath(), ENVIRONMENTS)

        then:
        orders.keySet().toList() == ENVIRONMENTS
        orders.values().every { it.indexOf('first') < it.indexOf('second') && it.indexOf('core') < it.indexOf('first') }
        orders.development.contains('devOnly')
        !orders.test.contains('devOnly')
        !orders.production.contains('devOnly')
    }

    void 'fails once, naming every environment, on a dependency no environment meets'() {
        given:
        plugin('needy', "dependsOn = ['absentDependency']")

        when:
        PluginVerification.verify(classPath(), ENVIRONMENTS)

        then:
        PluginException e = thrown()
        e.message.startsWith('In the development, test, production environments: ')
        e.message.contains('Grails plug-in [needy]')
        e.message.contains('dependency [absentDependency] with required version [*] is missing')
        e.message.count('Grails plug-in [needy]') == 1
        e.message.contains('name it in after instead of dependsOn')
    }

    void 'names only the environment whose plugins stop the application'() {
        given: 'a dependency that only development loads'
        plugin('devTools', "environmentsIncludes = ['development']")
        plugin('needsDevTools', "dependsOn = ['devTools']")

        when:
        PluginVerification.verify(classPath(), ENVIRONMENTS)

        then: 'each environment is named with its own reason'
        PluginException e = thrown()
        e.message.startsWith('In the test environment: ')
        e.message.contains('dependency [devTools] does not support the [test] environment')
        e.message.contains('In the production environment: ')
        e.message.contains('dependency [devTools] does not support the [production] environment')
        !e.message.contains('development')
    }

    void 'leaves out the plugins the application configuration excludes, in the environments it excludes them'() {
        given: 'a plugin whose dependency is missing, which production excludes'
        plugin('needy', "dependsOn = ['absentDependency']")
        write(root, 'application.yml', '''
            environments:
                production:
                    grails:
                        plugin:
                            excludes: needy
        '''.stripIndent())

        when:
        PluginVerification.verify(classPath(), ENVIRONMENTS)

        then: 'production starts, and the others report the missing dependency'
        PluginException e = thrown()
        e.message.startsWith('In the development, test environments: ')
        e.message.contains('[absentDependency]')

        when: 'every environment excludes it, in application.groovy'
        Files.delete(root.resolve('application.yml'))
        write(root, 'application.groovy', "grails.plugin.excludes = ['needy']\n")
        loader.close()
        Map<String, List<String>> orders = PluginVerification.verify(classPath(), ENVIRONMENTS)

        then:
        orders.values().every { !it.contains('needy') }
    }

    void 'fails on a circular plugin order'() {
        given:
        plugin('chicken', "after = ['egg']")
        plugin('egg', "after = ['chicken']")

        when:
        PluginVerification.verify(classPath(), ['production'])

        then:
        PluginException e = thrown()
        e.message.startsWith('In the production environment: ')
        e.message.contains('chicken')
        e.message.contains('egg')
    }

    void 'fails on a plugin built for an earlier Grails'() {
        given:
        File jar = root.resolve('old-plugin-1.0.jar').toFile()
        new JarOutputStream(new FileOutputStream(jar)).withCloseable { out ->
            out.putNextEntry(new ZipEntry('META-INF/grails-plugin.xml'))
            out << '<plugin name="old"/>'.getBytes('UTF-8')
            out.closeEntry()
        }
        loader = new URLClassLoader([root.toUri().toURL(), jar.toURI().toURL()] as URL[], PluginClassFixtures.classLoader)

        when:
        PluginVerification.verify(loader, ['production'])

        then:
        PluginException e = thrown()
        e.message.contains('built for an earlier Grails')
        e.message.contains('old-plugin-1.0.jar')
    }

    void 'leaves the environment and class loader of the thread as they were'() {
        given:
        plugin('plain')
        String environment = System.getProperty(Environment.KEY)
        ClassLoader contextLoader = Thread.currentThread().contextClassLoader

        when:
        PluginVerification.verify(classPath(), ENVIRONMENTS)

        then:
        System.getProperty(Environment.KEY) == environment
        Thread.currentThread().contextClassLoader.is(contextLoader)
    }

    void 'writes the order of each environment to the report, or why the plugins stop the application'() {
        given:
        Path report = root.resolve('report.txt')
        Path failure = root.resolve('failure.txt')
        plugin('first')
        ClassLoader contextLoader = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = classPath()

        when:
        runMain(report, failure)

        then:
        Files.readAllLines(report)*.replaceAll(':.*', '') == ENVIRONMENTS
        Files.readString(report).contains('first')
        !Files.exists(failure)

        when:
        plugin('needy', "dependsOn = ['absentDependency']")
        loader.close()
        Thread.currentThread().contextClassLoader = classPath()
        runMain(report, failure)

        then:
        Files.readString(failure).contains('dependency [absentDependency] with required version [*] is missing')
        Files.readString(failure).startsWith('In the development, test, production environments: ')

        cleanup:
        Thread.currentThread().contextClassLoader = contextLoader
    }

    private static void runMain(Path report, Path failure) {
        PluginVerification.main([report.toString(), failure.toString()] + ENVIRONMENTS as String[])
    }
}
