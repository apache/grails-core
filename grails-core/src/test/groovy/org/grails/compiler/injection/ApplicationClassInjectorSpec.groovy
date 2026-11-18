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

import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll
import spock.util.environment.RestoreSystemProperties

import grails.util.BuildSettings

@RestoreSystemProperties
class ApplicationClassInjectorSpec extends Specification {

    @TempDir
    File tempDir

    void setup() {
        // compiling runs the global transformation, which would otherwise pick up trait injectors another
        // spec left registered
        TraitInjectionUtils.@traitInjectors = []
    }

    void cleanup() {
        TraitInjectionUtils.@traitInjectors = null
    }

    @Unroll
    def "EXCLUDED_AUTO_CONFIGURE_CLASSES contains expected entry #className"(String className) {
        expect:
        ApplicationClassInjector.EXCLUDED_AUTO_CONFIGURE_CLASSES.contains(className)

        where:
        className << [
            'org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration',
            'org.springframework.boot.reactor.autoconfigure.ReactorAutoConfiguration',
            'org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration'
        ]
    }

    @Unroll
    def "CONDITIONAL_EXCLUSIONS contains expected entry #expected.excludeClass"(Map<String, String> expected) {
        when:
        def exclusion = ApplicationClassInjector.CONDITIONAL_EXCLUSIONS.find {
            it.excludeClass == expected.excludeClass
        }

        then:
        exclusion != null
        exclusion.pluginClass == expected.pluginClass
        exclusion.systemProperty == expected.systemProperty

        where:
        expected << [
            [
                    excludeClass: 'org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration',
                    pluginClass: 'org.grails.plugins.databasemigration.DatabaseMigrationGrailsPlugin',
                    systemProperty: 'grails.autoconfigure.exclude.liquibase'
            ]
        ]
    }

    def "system property defaults to true (exclusion enabled)"() {
        given:
        def prop = 'grails.autoconfigure.exclude.liquibase'

        when:
        System.clearProperty(prop)

        then:
        Boolean.parseBoolean(System.getProperty(prop, 'true'))
    }

    def "system property set to false disables exclusion"() {
        given:
        def prop = 'grails.autoconfigure.exclude.liquibase'

        when:
        System.setProperty(prop, 'false')

        then:
        !Boolean.parseBoolean(System.getProperty(prop, 'true'))
    }

    def "shouldInject returns false for null URL"() {
        given:
        def injector = new ApplicationClassInjector()

        expect:
        !injector.shouldInject(null)
    }

    def "artefact types contains Application"() {
        given:
        def injector = new ApplicationClassInjector()

        expect:
        injector.artefactTypes.contains('Application')
    }

    def "a dynamically compiled application class records its name as the main class"() {
        given: "an application class that is not statically compiled, where a generated project puts it"
        def targetDir = new File(tempDir, 'build/classes/groovy/main')
        compile(new File(tempDir, 'grails-app/init/Application.groovy'), '''
            class Application extends grails.boot.config.GrailsAutoConfiguration {
            }
        ''', targetDir)
        System.clearProperty(BuildSettings.MAIN_CLASS_NAME)

        when: "the class is initialized"
        def loader = new URLClassLoader([targetDir.toURI().toURL()] as URL[], getClass().classLoader)
        Class.forName('Application', true, loader)

        then: "its name is recorded"
        System.getProperty(BuildSettings.MAIN_CLASS_NAME) == 'Application'
    }

    def "the statement recording the main class is not dispatched through a metaclass"() {
        given: "an application class that is not statically compiled"
        def targetDir = new File(tempDir, 'build/classes/groovy/main')
        compile(new File(tempDir, 'grails-app/init/Application.groovy'), '''
            class Application extends grails.boot.config.GrailsAutoConfiguration {
            }
        ''', targetDir)

        when: "its static initializer is read"
        def calls = StaticInitializerCalls.of(new File(targetDir, 'Application.class').bytes)

        then: "System.setProperty is called directly"
        calls.contains('INVOKESTATIC java/lang/System.setProperty')

        and: "nothing is dispatched dynamically, which a native image could not resolve before any metadata is registered"
        !calls.any { it.startsWith('INVOKEDYNAMIC') }
    }

    private static void compile(File sourceFile, String source, File targetDirectory) {
        sourceFile.parentFile.mkdirs()
        sourceFile.text = source
        def compilationUnit = new CompilationUnit(new CompilerConfiguration(targetDirectory: targetDirectory))
        compilationUnit.addSource(sourceFile)
        compilationUnit.compile()
    }
}
