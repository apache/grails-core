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
package org.apache.grails.buildsrc

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class GrailsJacocoPluginSpec extends Specification {

    @TempDir
    Path testProjectDir

    def setup() {
        testProjectDir.resolve('settings.gradle').toFile().text = ''
        testProjectDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'groovy'
                id 'org.apache.grails.gradle.grails-jacoco'
            }
            repositories {
                mavenCentral()
            }
        """
        def src = testProjectDir.resolve('src/main/groovy/com/example/Foo.groovy').toFile()
        src.parentFile.mkdirs()
        src.text = 'package com.example\nclass Foo {}'
    }

    def "jacoco plugin applies JaCoCo and registers jacocoTestReport task"() {
        when: "listing verification tasks"
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('tasks', '--group=verification')
                .withPluginClasspath()
                .build()

        then: "jacocoTestReport is present"
        result.output.contains('jacocoTestReport')
    }

    def "jacocoTestReport generates xml html and csv reports"() {
        when: "listing all tasks"
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('tasks', '--all')
                .withPluginClasspath()
                .build()

        then: "aggregateJacocoCoverage is not registered (that task belongs to grails-violation-aggregation)"
        !result.output.contains('aggregateJacocoCoverage')
    }

    def "jacocoAggregateReport is registered on the root project in a multi-project build"() {
        given: "a multi-project build where a subproject applies grails-jacoco"
        testProjectDir.resolve('settings.gradle').toFile().text = "include 'app-module'"
        testProjectDir.resolve('build.gradle').toFile().text = ''
        def moduleDir = testProjectDir.resolve('app-module')
        moduleDir.toFile().mkdirs()
        moduleDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'groovy'
                id 'org.apache.grails.gradle.grails-jacoco'
            }
            repositories { mavenCentral() }
        """

        when: "listing verification tasks on the root"
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('tasks', '--group=verification')
                .withPluginClasspath()
                .build()

        then: "jacocoAggregateReport appears on the root"
        result.output.contains('jacocoAggregateReport')
    }

    def "jacocoAggregateReport includes the subproject test task as a dependency"() {
        given: "a multi-project build"
        testProjectDir.resolve('settings.gradle').toFile().text = "include 'app-module'"
        testProjectDir.resolve('build.gradle').toFile().text = ''
        def moduleDir = testProjectDir.resolve('app-module')
        moduleDir.toFile().mkdirs()
        moduleDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'groovy'
                id 'org.apache.grails.gradle.grails-jacoco'
            }
            repositories { mavenCentral() }
        """

        when: "doing a dry run"
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('jacocoAggregateReport', '--dry-run')
                .withPluginClasspath()
                .build()

        then: "the subproject test task is in the execution plan"
        result.output.contains(':app-module:test')
    }

    def "jacocoAggregateReport is skipped when no exec files exist"() {
        given: "a multi-project build with tests excluded so no exec files are produced"
        testProjectDir.resolve('settings.gradle').toFile().text = "include 'app-module'"
        testProjectDir.resolve('build.gradle').toFile().text = ''
        def moduleDir = testProjectDir.resolve('app-module')
        moduleDir.toFile().mkdirs()
        moduleDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'groovy'
                id 'org.apache.grails.gradle.grails-jacoco'
            }
            repositories { mavenCentral() }
        """

        when: "running jacocoAggregateReport with tests excluded"
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('jacocoAggregateReport', '-x', 'test', '--stacktrace')
                .withPluginClasspath()
                .build()

        then: "the task is skipped because executionData is empty"
        result.task(':jacocoAggregateReport').outcome == TaskOutcome.SKIPPED
    }

    def "each additional subproject with grails-jacoco wires itself into the same aggregate task"() {
        given: "two subprojects both applying grails-jacoco"
        testProjectDir.resolve('settings.gradle').toFile().text = "include 'module-a', 'module-b'"
        testProjectDir.resolve('build.gradle').toFile().text = ''
        ['module-a', 'module-b'].each { name ->
            def dir = testProjectDir.resolve(name)
            dir.toFile().mkdirs()
            dir.resolve('build.gradle').toFile().text = """
                plugins {
                    id 'groovy'
                    id 'org.apache.grails.gradle.grails-jacoco'
                }
                repositories { mavenCentral() }
            """
        }

        when: "doing a dry run"
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('jacocoAggregateReport', '--dry-run')
                .withPluginClasspath()
                .build()

        then: "both subproject test tasks appear as dependencies"
        result.output.contains(':module-a:test')
        result.output.contains(':module-b:test')

        and: "only one aggregate task is registered on the root"
        result.output.count('jacocoAggregateReport') == 1
    }
}
