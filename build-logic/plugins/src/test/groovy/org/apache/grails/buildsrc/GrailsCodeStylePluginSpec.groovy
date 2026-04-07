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
import spock.lang.Specification
import spock.lang.TempDir
import java.nio.file.Path

class GrailsCodeStylePluginSpec extends Specification {
    @TempDir
    Path testProjectDir
    
    File buildFile
    File groovyFile

    def setup() {
        buildFile = testProjectDir.resolve('build.gradle').toFile()
        buildFile << """
            plugins {
                id 'groovy'
                id 'org.apache.grails.gradle.grails-code-style'
            }
            
            // Minimal configuration for the plugin
            repositories {
                mavenCentral()
            }
        """
        
        testProjectDir.resolve('src/main/groovy').toFile().mkdirs()
        groovyFile = testProjectDir.resolve('src/main/groovy/Test.groovy').toFile()
    }

    def "test aggregateStyleViolations generates jacoco report"() {
        given: "a module with a jacoco csv report"
        def jacocoDir = testProjectDir.resolve('build/reports/jacoco/test').toFile()
        jacocoDir.mkdirs()
        
        // Create an empty reports directory to satisfy task input validation
        testProjectDir.resolve('build/reports/codestyle').toFile().mkdirs()
        
        def csvReport = new File(jacocoDir, 'jacocoTestReport.csv')
        csvReport.text = """GROUP,PACKAGE,CLASS,INSTRUCTION_MISSED,INSTRUCTION_COVERED,BRANCH_MISSED,BRANCH_COVERED,LINE_MISSED,LINE_COVERED,COMPLEXITY_MISSED,COMPLEXITY_COVERED,METHOD_MISSED,METHOD_COVERED
test-module,com.example,TestClass,1,9,0,0,0,1,0,1,0,1
"""
        
        buildFile = testProjectDir.resolve('settings.gradle').toFile()
        buildFile.text = "include 'test-module'"
        def moduleBuildFile = testProjectDir.resolve('test-module/build.gradle').toFile()
        moduleBuildFile.parentFile.mkdirs()
        moduleBuildFile.text = """
            plugins {
                id 'groovy'
                id 'org.apache.grails.gradle.grails-code-style'
            }
        """

        when: "running aggregateStyleViolations"
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('aggregateStyleViolations', '--stacktrace')
                .withPluginClasspath()
                .build()

        then: "task finished successfully"
        result.task(':aggregateStyleViolations').outcome == org.gradle.testkit.runner.TaskOutcome.SUCCESS

        and: "JACOCO_COVERAGE_VIOLATIONS.md exists and contains the coverage data"
        def report = testProjectDir.resolve('JACOCO_COVERAGE_VIOLATIONS.md').toFile()
        report.exists()
        report.text.contains('## Module: test-module')
        report.text.contains('| com.example.TestClass | 90.00% |')
    }
}
