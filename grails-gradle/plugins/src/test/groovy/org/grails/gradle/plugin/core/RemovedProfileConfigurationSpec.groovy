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
package org.grails.gradle.plugin.core

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Unroll

/**
 * Grails 9 has no profiles. The {@code profile} configuration of an application or a plugin project is
 * there to refuse a dependency with a message that says what to do, where Gradle would only say that it
 * found no method {@code profile()}.
 */
class RemovedProfileConfigurationSpec extends GradleSpecification {

    @Unroll
    def "a profile dependency declared #how fails the build of #kind and says to remove it"() {
        given:
        GradleRunner runner = setupTestResourceProject(fixture)
        new File(runner.projectDir, 'build.gradle') << declaration

        when:
        BuildResult result = runner.withArguments('help', '--stacktrace').buildAndFail()

        then: 'the one message names the dependency, the project and the upgrade note'
        result.output.contains(
                "Grails 9 has no profiles: remove the 'profile' dependency '${notation}' from the build of " +
                "root project '${fixture}'. Nothing reads it, and the profiles are not published for Grails 9. " +
                'See "The Grails Shell and the Profiles Are Not Published, and a Build Cannot Declare a Profile" ' +
                'in the Grails 9 upgrade notes: https://grails.apache.org/docs/latest/guide/upgrading.html')

        and: 'Gradle names the line of the build script that declares it'
        result.output.contains("build.gradle' line: ")

        and: 'not what Gradle says of a configuration that is not there'
        !result.output.contains('Could not find method profile()')
        !result.output.contains("Configuration with name 'profile' not found")

        and: 'nothing was looked for in a repository'
        !result.output.contains('Could not resolve')

        where:
        kind             | fixture                  | how                        | notation                                    | declaration
        'an application' | 'removed-profile-app'    | 'in the dependencies block' | 'org.apache.grails.profiles:web'            | "\ndependencies {\n    profile 'org.apache.grails.profiles:web'\n}\n"
        'an application' | 'removed-profile-app'    | 'with dependencies.add'     | 'org.apache.grails.profiles:rest-api:7.0.0' | "\ndependencies.add('profile', 'org.apache.grails.profiles:rest-api:7.0.0')\n"
        'an application' | 'removed-profile-app'    | 'through a provider'        | 'org.example:own-profile:1.0'               | "\ndependencies.addProvider('profile', providers.provider { 'org.example:own-profile:1.0' })\n"
        'a plugin'       | 'removed-profile-plugin' | 'in the dependencies block' | 'org.apache.grails.profiles:web-plugin'     | "\ndependencies {\n    profile 'org.apache.grails.profiles:web-plugin'\n}\n"
        'a plugin'       | 'removed-profile-plugin' | 'with dependencies.add'     | 'org.apache.grails.profiles:plugin:7.0.0'   | "\ndependencies.add('profile', 'org.apache.grails.profiles:plugin:7.0.0')\n"
    }

    @Unroll
    def "#kind without a profile dependency builds with the configuration cache"() {
        given:
        GradleRunner runner = setupTestResourceProject(fixture)
        new File(runner.projectDir, 'build.gradle') << '''
            tasks.register('inspectProfile') {
                def profile = configurations.profile
                def report = providers.provider {
                    "PROFILE resolvable=${profile.canBeResolved} consumable=${profile.canBeConsumed} " +
                            "dependencies=${profile.incoming.dependencies.collect { it.name }}"
                }
                doLast {
                    println report.get()
                }
            }
        '''.stripIndent()

        when: 'the archive is built from a clean build, storing and then reusing the configuration cache entry'
        BuildResult stored = executeTask('clean', ['jar', 'inspectProfile'])
        BuildResult reused = executeTask('clean', ['jar', 'inspectProfile'])

        then:
        stored.output.contains('Configuration cache entry stored')
        reused.output.contains('Configuration cache entry reused')
        assertTaskSuccess('jar', stored)
        assertTaskSuccess('jar', reused)

        and: 'the configuration is not one to resolve or to consume, and nothing is put in it'
        stored.output.contains('PROFILE resolvable=false consumable=false dependencies=[]')
        reused.output.contains('PROFILE resolvable=false consumable=false dependencies=[]')

        where:
        kind             | fixture
        'an application' | 'removed-profile-app'
        'a plugin'       | 'removed-profile-plugin'
    }

    def "a profile configuration the build made before the plugin was applied is left as it is"() {
        given:
        GradleRunner runner = setupTestResourceProject('removed-profile-app')
        new File(runner.projectDir, 'build.gradle').text = '''
            plugins {
                id 'org.apache.grails.gradle.grails-app' apply false
            }

            configurations {
                profile
            }

            apply plugin: 'org.apache.grails.gradle.grails-app'

            grails {
                bom = null
                cliAutoProvision = false
            }

            dependencies {
                implementation localGroovy()
                profile localGroovy()
            }

            tasks.register('inspectProfile') {
                def declared = configurations.profile.dependencies.size()
                doLast {
                    println "PROFILE declared=${declared}"
                }
            }
        '''.stripIndent()

        when:
        BuildResult result = executeTask('inspectProfile')

        then:
        result.output.contains('PROFILE declared=1')
    }
}
