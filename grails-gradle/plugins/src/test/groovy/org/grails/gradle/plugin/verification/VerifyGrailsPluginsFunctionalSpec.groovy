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
package org.grails.gradle.plugin.verification

import org.gradle.testkit.runner.TaskOutcome

import org.grails.gradle.plugin.core.GradleSpecification

/**
 * Tests how {@code verifyGrailsPlugins} runs the check of the project's Grails. The fixture's class path has a
 * class of the same name in its place, which reports what it was given; the check itself is tested in
 * grails-core and, against published Grails, in the end-to-end tests.
 */
class VerifyGrailsPluginsFunctionalSpec extends GradleSpecification {

    def "check runs the check of the project's class path for development, test and production, and writes the report"() {
        given:
        def runner = setupTestResourceProject('plugin-verification')

        when:
        def result = executeTask('check')

        then:
        result.task(':verifyGrailsPlugins').outcome == TaskOutcome.SUCCESS
        new File(runner.projectDir, 'build/grails/plugin-order.txt').text == 'development,test,production\n'

        when: 'nothing changed'
        result = executeTask('check')

        then:
        result.task(':verifyGrailsPlugins').outcome == TaskOutcome.UP_TO_DATE
    }

    def "the environments checked can be set"() {
        given:
        def runner = setupTestResourceProject('plugin-verification')
        new File(runner.projectDir, 'build.gradle') << '''
            tasks.named('verifyGrailsPlugins') { environments = ['production', 'staging'] }
        '''.stripIndent()

        when:
        executeTask('verifyGrailsPlugins')

        then:
        new File(runner.projectDir, 'build/grails/plugin-order.txt').text == 'production,staging\n'
    }

    def "the build fails with the reason the plugins would stop the application"() {
        given:
        def runner = setupTestResourceProject('plugin-verification')
        new File(runner.projectDir, 'src/main/resources').mkdirs()
        new File(runner.projectDir, 'src/main/resources/fail.txt').text = 'fail'

        when:
        def result = runner.withArguments('check', '--stacktrace').buildAndFail()

        then:
        result.task(':verifyGrailsPlugins').outcome == TaskOutcome.FAILED
        result.output.contains('The Grails plugins of this project would stop its application from starting:')
        result.output.contains('Grails plug-in [needy] cannot be loaded in [development, test, production]')
    }

    def "a directory a view compiler registers into the output without naming its producer is not read"() {
        given: 'a task that runs after the classes, as a view compiler does, writing a directory of the output'
        def runner = setupTestResourceProject('plugin-verification')
        new File(runner.projectDir, 'build.gradle') << '''
            def views = layout.buildDirectory.dir('view-classes')
            sourceSets.main.output.dir(views)
            def compileViews = tasks.register('compileViews') {
                dependsOn('classes')
                outputs.dir(views)
                doLast { views.get().file('views.properties').asFile.text = 'view=compiled\\n' }
            }
            tasks.named('jar') { dependsOn(compileViews) }
            tasks.named('check') { dependsOn(compileViews) }
        '''.stripIndent()

        when:
        def result = executeTask('check')

        then: 'Gradle reports no implicit dependency'
        result.task(':compileViews').outcome == TaskOutcome.SUCCESS
        result.task(':verifyGrailsPlugins').outcome == TaskOutcome.SUCCESS
        !result.output.contains('implicit dependency')
    }

    def "every Grails application and plugin build verifies its plugins"() {
        given:
        setupTestResourceProject(project)

        when:
        def result = executeTask('help', ['--task', 'verifyGrailsPlugins'])

        then:
        result.output.contains('Checks that the Grails plugins of the project would let its application start.')

        where:
        project << ['plugin-descriptor-compilation', 'gsp-compile-classpath-grails']
    }
}
