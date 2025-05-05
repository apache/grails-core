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

import org.grails.forge.application.ApplicationType
import org.grails.forge.application.OperatingSystem
import org.grails.forge.utils.CommandSpec

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
        final String output = executeGradle("build -x iT").getOutput()

        then:
        output.contains('BUILD SUCCESSFUL')
    }

    void "test create-app contains i18n files"() {
        given:
        generateProject(OperatingSystem.MACOS_ARCH64)

        expect:
        new File(dir, "grails-app/i18n").exists()
    }

    void "test create-app creates a correct Application.groovy"() {
        given:
        generateProject(OperatingSystem.MACOS_ARCH64, [], ApplicationType.DEFAULT_OPTION)
        def applicationClassSourceFile = new File(dir, 'grails-app/init/example/grails/Application.groovy')

        expect:
        applicationClassSourceFile.exists()
        applicationClassSourceFile.text == '''\
        package example.grails
        
        import grails.boot.GrailsApp
        import grails.boot.config.GrailsAutoConfiguration
        import groovy.transform.CompileStatic
        
        @CompileStatic
        class Application extends GrailsAutoConfiguration {
            static void main(String[] args) {
                GrailsApp.run(Application, args)
            }
        }
        '''.stripIndent(8)
    }

    void "test create-app web-plugin creates a correct Application.groovy"() {
        given:
        generateProject(OperatingSystem.MACOS_ARCH64, [], ApplicationType.WEB_PLUGIN)

        def applicationClassSourceFile = new File(dir, 'grails-app/init/example/grails/Application.groovy')

        expect:
        applicationClassSourceFile.exists()
        applicationClassSourceFile.text == '''\
        package example.grails
        
        import grails.boot.GrailsApp
        import grails.boot.config.GrailsAutoConfiguration
        import grails.plugins.metadata.PluginSource
        import groovy.transform.CompileStatic
        
        @PluginSource
        @CompileStatic
        class Application extends GrailsAutoConfiguration {
            static void main(String[] args) {
                GrailsApp.run(Application, args)
            }
        }
        '''.stripIndent(8)
    }

    @Override
    String getTempDirectoryPrefix() {
        return "test-app"
    }
}
