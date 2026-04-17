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

class GrailsCodeStylePluginJacocoSpec extends Specification {
    @TempDir
    Path testProjectDir
    
    File buildFile

    def setup() {
        buildFile = testProjectDir.resolve('build.gradle').toFile()
        buildFile << """
            plugins {
                id 'groovy'
                id 'org.apache.grails.gradle.grails-code-style'
            }
            
            repositories {
                mavenCentral()
            }
        """
        
        testProjectDir.resolve('settings.gradle').toFile().text = "include 'app-module'"
    }

    def "test jacoco aggregation markdown table generation"() {
        given: "a project structure with jacoco report"
        testProjectDir.resolve('build/reports/codestyle').toFile().mkdirs()
        def moduleDir = testProjectDir.resolve('app-module')
        moduleDir.toFile().mkdirs()
        
        def jacocoDir = moduleDir.resolve('build/reports/jacoco/test').toFile()
        jacocoDir.mkdirs()
        
        def csvReport = new File(jacocoDir, 'jacocoTestReport.csv')
        csvReport.text = """GROUP,PACKAGE,CLASS,INSTRUCTION_MISSED,INSTRUCTION_COVERED,BRANCH_MISSED,BRANCH_COVERED,LINE_MISSED,LINE_COVERED,COMPLEXITY_MISSED,COMPLEXITY_COVERED,METHOD_MISSED,METHOD_COVERED
app-module,com.example,AppClass,1,9,0,0,0,1,0,1,0,1
app-module,com.example,AppClass._closure1,1,0,0,0,0,1,0,1,0,1
app-module,com.example,AppClass.InnerClass,1,9,0,0,0,1,0,1,0,1
app-module,com.example,VeryLongClassNameThatMightCauseIssuesInMarkdownTableGenerationIfNotHandledCorrectly,50,50,0,0,0,1,0,1,0,1
"""

        when: "running aggregateStyleViolations with jacoco enabled"
        def runner = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('aggregateStyleViolations', '-Pgrails.codestyle.enabled.jacoco=true', '-x', 'checkstyleMain', '-x', 'codenarcMain', '--stacktrace', '--info')
                .withPluginClasspath()
        def result = runner.build()
        println result.output

        then: "task finished successfully"
        result.task(':aggregateStyleViolations').outcome == org.gradle.testkit.runner.TaskOutcome.SUCCESS

        and: "jacoco report is generated"
        testProjectDir.resolve('JACOCO_COVERAGE_VIOLATIONS.md').toFile().exists()
        
        def jacocoMd = testProjectDir.resolve('JACOCO_COVERAGE_VIOLATIONS.md').toFile().text
        println "Generated JaCoCo Markdown:\n${jacocoMd}"
        
        jacocoMd.contains('## Module: app-module')
        jacocoMd.contains('| com.example.AppClass | JaCoCo | LowCoverage | 0 | Instructions covered: 90.00% |')
        jacocoMd.contains('| com.example.VeryLongClassNameThatMightCauseIssuesInMarkdownTableGenerationIfNotHandledCorrectly | JaCoCo | LowCoverage | 0 | Instructions covered: 50.00% |')
        
        // Closures should be filtered
        !jacocoMd.contains('AppClass._closure1')
        
        // Standard inner classes might remain if they don't contain $ or new or _closure
        // (JaCoCo CSV format uses . for inner classes)
        jacocoMd.contains('AppClass.InnerClass')
        
        // Check for table header alignment row
        jacocoMd.contains('| :--- | :--- | :--- | :--- | :--- |')
    }
}
