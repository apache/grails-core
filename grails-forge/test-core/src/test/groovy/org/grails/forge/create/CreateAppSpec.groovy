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

package org.grails.forge.create

import org.gradle.testkit.runner.TaskOutcome
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.OperatingSystem
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.JdkVersion
import org.grails.forge.utils.CommandSpec
import spock.lang.Unroll

class CreateAppSpec extends CommandSpec {

    void "test basic create-app build task"() {
        given:
        generateProject(OperatingSystem.MACOS_ARCH64)

        when:
        /*
            Temporarily disable the integrationTest task.
            -----------------------------------------------

            There is a problem with running the integrationTest task here.
            It is failing with org.openqa.selenium.SessionNotCreatedException.

            This problem was probably masked previously by the fact that the Geb/Selenium
            dependencies were not being included for OperatingSystem.MACOS_ARCH64.

            As of commit 8675723e62df6d136d7af48d5c75d7728cbef871 the Geb/Selenium
            dependencies are included for OperatingSystem.MACOS_ARCH64 and this
            causes the integrationTest task to fail.
        */
        final String output = executeGradle("build -x integrationTest").getOutput()

        then:
        output.contains('BUILD SUCCESSFUL')
    }

    void "test create-app contains i18n files"() {
        given:
        generateProject(OperatingSystem.MACOS_ARCH64)

        expect:
        new File(dir, "grails-app/i18n").exists()
    }

    @Unroll
    void "test create-app #applicationType creates a correct Application.groovy"() {
        given:
        generateProject(OperatingSystem.MACOS_ARCH64, [], applicationType)
        def applicationClassSourceFile = new File(dir, 'grails-app/init/example/grails/Application.groovy')

        expect:
        applicationClassSourceFile.exists()
        applicationClassSourceFile.text == applicationSource.stripIndent(8)

        where:
        applicationType            | applicationSource
        ApplicationType.WEB        | '''\
        package example.grails
        
        import groovy.transform.CompileStatic

        import org.springframework.context.annotation.ComponentScan

        import grails.boot.GrailsApp
        import grails.boot.config.GrailsAutoConfiguration
        
        @CompileStatic
        @ComponentScan(value = 'example.grails')
        class Application extends GrailsAutoConfiguration {
            static void main(String[] args) {
                GrailsApp.run(Application, args)
            }
        }
        '''
        ApplicationType.REST_API   | '''\
        package example.grails

        import groovy.transform.CompileStatic

        import org.springframework.context.annotation.ComponentScan

        import grails.boot.GrailsApp
        import grails.boot.config.GrailsAutoConfiguration

        @CompileStatic
        @ComponentScan(value = 'example.grails')
        class Application extends GrailsAutoConfiguration {
            static void main(String[] args) {
                GrailsApp.run(Application, args)
            }
        }
        '''
        ApplicationType.PLUGIN     | '''\
        package example.grails

        import groovy.transform.CompileStatic

        import org.springframework.context.annotation.ComponentScan

        import grails.boot.GrailsApp
        import grails.boot.config.GrailsAutoConfiguration
        import grails.plugins.metadata.PluginSource

        @PluginSource
        @CompileStatic
        @ComponentScan(value = 'example.grails')
        class Application extends GrailsAutoConfiguration {
            static void main(String[] args) {
                GrailsApp.run(Application, args)
            }
        }
        '''
        ApplicationType.WEB_PLUGIN | '''\
        package example.grails
        
        import groovy.transform.CompileStatic

        import org.springframework.context.annotation.ComponentScan

        import grails.boot.GrailsApp
        import grails.boot.config.GrailsAutoConfiguration
        import grails.plugins.metadata.PluginSource
        
        @PluginSource
        @CompileStatic
        @ComponentScan(value = 'example.grails')
        class Application extends GrailsAutoConfiguration {
            static void main(String[] args) {
                GrailsApp.run(Application, args)
            }
        }
        '''
    }

    void "test generated application scans standard Spring components"() {
        given:
        generateProject(OperatingSystem.MACOS_ARCH64)
        File componentSourceFile = new File(dir, 'src/main/groovy/example/grails/components/ScannedComponent.groovy')
        componentSourceFile.parentFile.mkdirs()
        componentSourceFile.text = '''\
            package example.grails.components

            import org.springframework.stereotype.Component

            @Component
            class ScannedComponent {
                String value() {
                    'scanned'
                }
            }
        '''.stripIndent()
        File componentSpecFile = new File(dir, 'src/test/groovy/example/grails/ComponentScanningSpec.groovy')
        componentSpecFile.parentFile.mkdirs()
        componentSpecFile.text = '''\
            package example.grails

            import example.grails.components.ScannedComponent
            import grails.testing.mixin.integration.Integration
            import org.springframework.beans.factory.annotation.Autowired
            import spock.lang.Specification

            @Integration
            class ComponentScanningSpec extends Specification {

                @Autowired
                ScannedComponent scannedComponent

                void "standard Spring component is injected"() {
                    expect:
                    scannedComponent.value() == 'scanned'
                }
            }
        '''.stripIndent()

        when:
        def result = executeGradle('test', '--tests', 'example.grails.ComponentScanningSpec')

        then:
        result.task(':test').outcome == TaskOutcome.SUCCESS
    }

    void "test create-app with micronaut feature"() {
        given:
        // Micronaut features require JDK 25+ because micronaut-core's ScopedValues
        // references java.lang.ScopedValue.CallableOp (JEP 506, finalized in JDK 25).
        generateProject(OperatingSystem.MACOS_ARCH64, ['grails-micronaut'], ApplicationType.WEB,
                DevelopmentReloading.DEFAULT_OPTION, JdkVersion.JDK_25)

        def gradleProperties = new File(dir, 'gradle.properties')
        def gradleBuildFile = new File(dir, 'build.gradle')

        expect:
        gradleProperties.exists()
        gradleProperties.text.contains('micronautPlatformVersion=4.10.16')
        gradleBuildFile.exists()
        gradleBuildFile.text.contains('implementation "org.apache.grails:grails-micronaut"')
    }

    @Override
    String getTempDirectoryPrefix() {
        return "test-app"
    }
}
