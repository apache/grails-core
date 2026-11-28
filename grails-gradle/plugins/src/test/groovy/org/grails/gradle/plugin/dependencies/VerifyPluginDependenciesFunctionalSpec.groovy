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
package org.grails.gradle.plugin.dependencies

import org.gradle.testkit.runner.TaskOutcome

import org.grails.gradle.plugin.core.GradleSpecification

/**
 * Tests that {@code check} verifies that a dependency of the project provides every plugin a Grails plugin
 * of it {@code dependsOn}.
 */
class VerifyPluginDependenciesFunctionalSpec extends GradleSpecification {

    def "check passes when a dependency of the project or the project itself provides every plugin depended on"() {
        given: 'a plugin depending on one in a dependency jar and on another of its own project, and after an absent one'
        setupTestResourceProject('plugin-dependencies')

        when:
        def result = executeTask(':consumer:check')

        then:
        result.task(':consumer:verifyPluginDependencies').outcome == TaskOutcome.SUCCESS
    }

    def "check fails, naming the plugin and the dependency, when no dependency provides a plugin depended on"() {
        given:
        def runner = setupTestResourceProject('plugin-dependencies')
        new File(runner.projectDir, 'consumer/src/main/resources/META-INF/grails/plugins/consumer/plugin.properties') <<
                'dependsOn.notOnTheClassPath=*\n'

        when:
        def result = runner.withArguments(':consumer:check', '--stacktrace').buildAndFail()

        then:
        result.task(':consumer:verifyPluginDependencies').outcome == TaskOutcome.FAILED
        result.output.contains('Grails plugin [consumer] depends on [notOnTheClassPath], but no dependency of ' +
                'project :consumer provides it (none of its runtime class path has ' +
                'META-INF/grails/plugins/notOnTheClassPath/plugin.properties)')
        result.output.contains('name it in after instead of dependsOn')
        !result.output.contains('depends on [providedPlugin]')
    }

    def "a plugin descriptor in a resource directory a task generates is verified once that task has run"() {
        given: 'a task that generates a resource directory holding a descriptor with an unmet dependency'
        def runner = setupTestResourceProject('plugin-dependencies')
        new File(runner.projectDir, 'consumer/build.gradle') << '''
            def generated = layout.buildDirectory.dir('generated-descriptor')
            def generate = tasks.register('generateDescriptor') {
                outputs.dir(generated)
                doLast {
                    def file = generated.get().file('META-INF/grails/plugins/generated/plugin.properties').asFile
                    file.parentFile.mkdirs()
                    file.text = 'format.version=1\\nname=generated\\nclass=example.GeneratedGrailsPlugin\\ndependsOn.notOnTheClassPath=*\\n'
                }
            }
            sourceSets.main.resources.srcDir(generated)
            tasks.named('processResources') { dependsOn(generate) }
        '''.stripIndent()

        when:
        def result = runner.withArguments(':consumer:check', '--stacktrace').buildAndFail()

        then: 'the task runs after the generator, so Gradle reports no implicit dependency and the descriptor is read'
        result.task(':consumer:generateDescriptor').outcome == TaskOutcome.SUCCESS
        result.task(':consumer:verifyPluginDependencies').outcome == TaskOutcome.FAILED
        !result.output.contains('implicit dependency')
        result.output.contains('Grails plugin [generated] depends on [notOnTheClassPath]')
    }

    def "a directory a view compiler registers into the output without naming its producer is not read"() {
        given: 'a task that runs after the classes, as a view compiler does, writing a directory of the output'
        def runner = setupTestResourceProject('plugin-dependencies')
        new File(runner.projectDir, 'consumer/build.gradle') << '''
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
        def result = executeTask(':consumer:check')

        then: 'the verification reads only the classes and resources, so Gradle reports no implicit dependency'
        result.task(':consumer:compileViews').outcome == TaskOutcome.SUCCESS
        result.task(':consumer:verifyPluginDependencies').outcome == TaskOutcome.SUCCESS
        !result.output.contains('implicit dependency')
    }

    def "a project without Grails plugins has nothing to verify"() {
        given:
        def runner = setupTestResourceProject('plugin-dependencies')
        new File(runner.projectDir, 'consumer/src/main/resources/META-INF').deleteDir()

        when:
        def result = executeTask(':consumer:check')

        then:
        result.task(':consumer:verifyPluginDependencies').outcome == TaskOutcome.NO_SOURCE
    }

    def "the Grails plugin Gradle plugin verifies the plugin dependencies of its project"() {
        given:
        setupTestResourceProject('plugin-descriptor-compilation')

        when:
        def result = executeTask('tasks', ['--all'])

        then:
        result.output.contains('verifyPluginDependencies')
    }
}
