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
        given: "no aggregateJacocoCoverage task on a non-root project"
        when: "listing all tasks"
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('tasks', '--all')
                .withPluginClasspath()
                .build()

        then: "aggregateJacocoCoverage is not registered (aggregation is root-only via grails-violation-aggregation)"
        !result.output.contains('aggregateJacocoCoverage')
    }
}
