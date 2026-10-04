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

import java.nio.file.Path

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.TempDir

/**
 * Runs {@code validateDependencyVersions} on a library whose direct dependency upgrades a version
 * its {@code grails-bom} manages.
 */
class ValidateDependencyVersionsTaskSpec extends Specification {

    @TempDir
    Path testProjectDir

    def setup() {
        ['1.0', '1.1'].each { String version ->
            File pom = testProjectDir.resolve("repo/test/lib-a/${version}/lib-a-${version}.pom").toFile()
            pom.parentFile.mkdirs()
            pom.text = """<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <groupId>test</groupId>
    <artifactId>lib-a</artifactId>
    <version>${version}</version>
</project>
"""
        }
        testProjectDir.resolve('settings.gradle').toFile().text = """
rootProject.name = 'root'
include 'grails-bom', 'lib'
"""
        write('grails-bom/build.gradle', """
plugins {
    id 'java-platform'
}

dependencies {
    constraints {
        api 'test:lib-a:1.0'
    }
}
""")
    }

    def "fails when a dependency resolves to a version other than the one the BOM manages"() {
        given:
        writeLibrary()

        when:
        BuildResult result = runner([]).buildAndFail()

        then:
        result.output.contains("Dependency version validation failed for project 'lib'")
        result.output.contains('test:lib-a - resolved 1.1, expected 1.0')
    }

    def "skipDependencyValidation opts the project out: #optOut"() {
        given:
        writeLibrary(buildScript)

        when:
        BuildResult result = runner(arguments).build()

        then:
        result.task(':lib:validateDependencyVersions').outcome == TaskOutcome.SUCCESS

        where:
        optOut                                  | arguments                             | buildScript
        '-PskipDependencyValidation'            | ['-PskipDependencyValidation']        | ''
        '-PskipDependencyValidation=true'       | ['-PskipDependencyValidation=true']   | ''
        'ext.skipDependencyValidation = true'   | []                                    | 'ext.skipDependencyValidation = true'
    }

    def "still validates with #arguments"() {
        given:
        writeLibrary()

        when:
        BuildResult result = runner(arguments).buildAndFail()

        then:
        result.output.contains('test:lib-a - resolved 1.1, expected 1.0')

        where: "an explicit false, or the opt-out that belongs to validateBomProperties"
        arguments << [['-PskipDependencyValidation=false'], ['-PskipBomPropertyValidation']]
    }

    private GradleRunner runner(List<String> arguments) {
        GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments([':lib:validateDependencyVersions', '--stacktrace'] + arguments)
                .withPluginClasspath()
    }

    private void writeLibrary(String extra = '') {
        write('lib/build.gradle', """
plugins {
    id 'java'
    id 'org.apache.grails.buildsrc.dependency-validator'
}

repositories {
    maven { url = rootProject.uri('repo') }
}

dependencies {
    implementation platform(project(':grails-bom'))
    implementation 'test:lib-a:1.1'
}

${extra}
""")
    }

    private void write(String path, String content) {
        File file = testProjectDir.resolve(path).toFile()
        file.parentFile.mkdirs()
        file.text = content
    }
}
