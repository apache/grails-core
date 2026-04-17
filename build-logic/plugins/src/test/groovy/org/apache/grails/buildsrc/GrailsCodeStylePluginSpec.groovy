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

    def "test codeStyle and aggregation tasks including jacoco"() {
        given: "a project structure with violations and jacoco report"
        testProjectDir.resolve('build/reports/codestyle/checkstyle').toFile().mkdirs()
        testProjectDir.resolve('build/reports/codestyle/codenarc').toFile().mkdirs()
        def jacocoDir = testProjectDir.resolve('build/reports/jacoco/test').toFile()
        jacocoDir.mkdirs()
        
        buildFile = testProjectDir.resolve('settings.gradle').toFile()
        buildFile.text = "include 'app-module'"
        def moduleDir = testProjectDir.resolve('app-module')
        def moduleBuildFile = moduleDir.resolve('build.gradle').toFile()
        moduleBuildFile.parentFile.mkdirs()
        moduleBuildFile.text = """
            plugins {
                id 'groovy'
                id 'jacoco'
                id 'org.apache.grails.gradle.grails-code-style'
            }
            repositories {
                mavenCentral()
            }
            dependencies {
                implementation 'org.apache.groovy:groovy:4.0.11'
            }
        """
        def sourceFile = moduleDir.resolve('src/main/groovy/com/example/AppClass.groovy')
        sourceFile.toFile().parentFile.mkdirs()
        sourceFile.toFile().text = "package com.example\nclass AppClass {}"

        def checkstyleReport = testProjectDir.resolve('build/reports/codestyle/checkstyle/app-module-checkstyleMain.xml').toFile()
        checkstyleReport.parentFile.mkdirs()
        checkstyleReport.text = """<?xml version="1.0" encoding="UTF-8"?>
<checkstyle version="10.0">
<file name="${sourceFile.toFile().absolutePath}">
<error line="1" column="1" severity="error" message="Missing a Javadoc comment." source="com.puppycrawl.tools.checkstyle.checks.javadoc.JavadocPackageCheck"/>
</file>
</checkstyle>
"""
        def codenarcReport = testProjectDir.resolve('build/reports/codestyle/codenarc/app-module-codenarcMain.xml').toFile()
        codenarcReport.parentFile.mkdirs()
        codenarcReport.text = """<?xml version="1.0" encoding="UTF-8"?>
<CodeNarc version="3.1.0">
<Package name="com.example">
<File name="AppClass.groovy">
<Violation ruleName="EmptyClass" priority="2" lineNumber="1">
<Message>The class is empty</Message>
</Violation>
</File>
</Package>
</CodeNarc>
"""
        def csvReport = new File(jacocoDir, 'jacocoTestReport.csv')
        csvReport.text = """GROUP,PACKAGE,CLASS,INSTRUCTION_MISSED,INSTRUCTION_COVERED,BRANCH_MISSED,BRANCH_COVERED,LINE_MISSED,LINE_COVERED,COMPLEXITY_MISSED,COMPLEXITY_COVERED,METHOD_MISSED,METHOD_COVERED
app-module,com.example,AppClass,1,9,0,0,0,1,0,1,0,1
app-module,org.grails.orm.hibernate.support.hibernate7,FilteredClass,1,9,0,0,0,1,0,1,0,1
app-module,com.example,FullCoverageClass,0,10,0,0,0,1,0,1,0,1
"""

        when: "running aggregateStyleViolations with jacoco enabled"
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('aggregateStyleViolations', '-Pgrails.codestyle.enabled.jacoco=true', '-x', 'checkstyleMain', '-x', 'codenarcMain', '--stacktrace')
                .withPluginClasspath()
                .build()

        then: "task finished successfully"
        result.task(':aggregateStyleViolations').outcome == org.gradle.testkit.runner.TaskOutcome.SUCCESS

        and: "violation reports are generated"
        testProjectDir.resolve('CHECKSTYLE_VIOLATIONS.md').toFile().exists()
        testProjectDir.resolve('CODENARC_VIOLATIONS.md').toFile().exists()
        testProjectDir.resolve('JACOCO_COVERAGE_VIOLATIONS.md').toFile().exists()
        
        def checkstyleMd = testProjectDir.resolve('CHECKSTYLE_VIOLATIONS.md').toFile().text
        checkstyleMd.contains('## Module: app-module')
        checkstyleMd.contains('| com.example.AppClass | Checkstyle | JavadocPackageCheck | 1 | Missing a Javadoc comment. |')

        def codenarcMd = testProjectDir.resolve('CODENARC_VIOLATIONS.md').toFile().text
        codenarcMd.contains('## Module: app-module')
        codenarcMd.contains('| com.example.AppClass | CodeNarc | EmptyClass | 1 | The class is empty |')

        def jacocoMd = testProjectDir.resolve('JACOCO_COVERAGE_VIOLATIONS.md').toFile().text
        jacocoMd.contains('## Module: app-module')
        jacocoMd.contains('| com.example.AppClass | 90.00% |')
        !jacocoMd.contains('FilteredClass')
        !jacocoMd.contains('FullCoverageClass')
    }

    def "test codenarcFix task fixes violations"() {
        given: "a file with violations"
        groovyFile.text = """package org.test

class Test{
    def map = [key:"value"]
    def str = "unnecessary gstring"
    def semi = "semicolon";
    def lines = 1


    def other = 2
}
"""
        when: "running codenarcFix"
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('codenarcFix', '--stacktrace')
                .withPluginClasspath()
                .build()

        then: "task finished successfully"
        result.task(':codenarcFix').outcome == org.gradle.testkit.runner.TaskOutcome.SUCCESS

        and: "violations are fixed"
        def fixedContent = groovyFile.text
        fixedContent.contains('class Test {') // SpaceBeforeOpeningBrace
        fixedContent.contains('class Test {\n\n    def map') 
        fixedContent.contains("[key: 'value']") // SpaceAroundMapEntryColon and UnnecessaryGString
        fixedContent.contains("'unnecessary gstring'") // UnnecessaryGString
        fixedContent.contains("def semi = 'semicolon'") // UnnecessarySemicolon
        !fixedContent.contains(";")
        fixedContent.count('\n\n') == 3 // ConsecutiveBlankLines
    }

    def "test codenarcFix task does not break strings with single quotes"() {
        given: "a file with double quoted strings containing single quotes"
        groovyFile.text = """package org.test

class Test {
    def s1 = "it's a test"
    def s2 = "format 'yyyy-MM-dd'"
    def s3 = "contains \\"double\\" and 'single' quotes"
}
"""
        when: "running codenarcFix"
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('codenarcFix', '--stacktrace')
                .withPluginClasspath()
                .build()

        then: "task finished successfully"
        result.task(':codenarcFix').outcome == org.gradle.testkit.runner.TaskOutcome.SUCCESS

        and: "strings with single quotes are NOT changed to single quotes (which would break them)"
        def content = groovyFile.text
        content.contains('def s1 = "it\'s a test"')
        content.contains('def s2 = "format \'yyyy-MM-dd\'"')
        // s3 has escaped double quotes, so it should also remain double quoted
        content.contains('def s3 = "contains \\"double\\" and \'single\' quotes"')
    }

    def "test codenarcFix task does not break escaped double quotes in double quotes"() {
        given: "a file with escaped double quotes"
        groovyFile.text = """package org.test

class Test {
    def s = "\\"\\\$it\\""
}
"""
        when: "running codenarcFix"
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('codenarcFix', '--stacktrace')
                .withPluginClasspath()
                .build()

        then: "task finished successfully"
        result.task(':codenarcFix').outcome == org.gradle.testkit.runner.TaskOutcome.SUCCESS

        and: "escaped quotes are NOT broken"
        groovyFile.text.contains('"\\"\\$it\\""')
    }
}
