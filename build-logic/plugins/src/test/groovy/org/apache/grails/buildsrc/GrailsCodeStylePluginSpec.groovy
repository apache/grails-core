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
        // Note: ClassStartsWithBlankLine regex in the plugin is:
        // content.replaceAll(/(class\s+[^{]+\{\n)([ \t]*[^ \s\n\/])/, '$1\n$2')
        // In our case, it should match "class Test{\n    def map" and insert a newline
        fixedContent.contains('class Test {\n\n    def map') 
        fixedContent.contains('[key: \'value\']') // SpaceAroundMapEntryColon and UnnecessaryGString
        fixedContent.contains("'unnecessary gstring'") // UnnecessaryGString
        fixedContent.contains('def semi = \'semicolon\'\n') // UnnecessarySemicolon
        fixedContent.count('\n\n') == 3 // ConsecutiveBlankLines (3 reduced to 2), plus others
    }
}
