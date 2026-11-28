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
import org.grails.forge.utils.CommandSpec

/**
 * The Grails environment the tests of a generated application run in when a spec builds it: the one
 * they have when somebody runs the build from a command line, which is test, and not the environment
 * of the JVM of the spec, which is that of Forge. The Grails Gradle plugin gives the tests the
 * environment the build was asked for with {@code -Dgrails.env}, and TestKit hands the system
 * properties of the JVM that asks to the build, so a spec that built an application as it is ran its
 * tests in production.
 */
class GeneratedBuildEnvironmentSpec extends CommandSpec {

    void "the unit tests and the integration tests of a generated application run in the test environment, though this JVM has that of Forge"() {
        given: 'an application whose tests write down the environment they run in'
        generateProjectWithDefaults(ApplicationType.REST_API)
        probe('test')
        probe('integration-test')

        expect: 'Forge runs here, as production'
        System.getProperty('grails.env') == 'production'

        when: 'the build runs them, as it is'
        executeGradle('test', '--tests', 'example.grails.EnvironmentOfTestSpec',
                'integrationTest', '--tests', 'example.grails.EnvironmentOfIntegrationTestSpec')

        then: 'both ran as from a command line'
        written('test') == 'TEST test'
        written('integration-test') == 'TEST test'

        and: 'and this JVM has its environment back'
        System.getProperty('grails.env') == 'production'

        when: 'an environment is asked for, as on a command line'
        executeGradle('integrationTest', '--tests', 'example.grails.EnvironmentOfIntegrationTestSpec', '-Dgrails.env=development')

        then: 'the tests have it'
        written('integration-test') == 'DEVELOPMENT development'
        System.getProperty('grails.env') == 'production'
    }

    // a specification of the application, in one of its test source sets, that writes down its environment
    private void probe(String sourceSet) {
        String name = "EnvironmentOf${sourceSet.split('-')*.capitalize().join('')}Spec"
        File spec = new File(dir, "src/${sourceSet}/groovy/example/grails/${name}.groovy")
        spec.parentFile.mkdirs()
        spec.text = """\
            package example.grails

            import grails.util.Environment
            import spock.lang.Specification

            class ${name} extends Specification {

                void "the environment is written down"() {
                    when:
                    new File('build/environment-of-${sourceSet}.txt').text = Environment.current.name() + ' ' + System.getProperty('grails.env')

                    then:
                    noExceptionThrown()
                }
            }
            """.stripIndent()
    }

    private String written(String sourceSet) {
        new File(dir, "build/environment-of-${sourceSet}.txt").text
    }

    @Override
    String getTempDirectoryPrefix() {
        'generatedenvironment'
    }
}
