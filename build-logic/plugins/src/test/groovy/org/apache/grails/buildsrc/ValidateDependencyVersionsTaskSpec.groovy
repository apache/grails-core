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
 * its {@code grails-bom} manages, on one whose dependency requests a version range, and on ones
 * whose graph cannot be resolved in full.
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
        // lib-b requires lib-a as a range, the shape ehcache gives jaxb-runtime
        File rangePom = testProjectDir.resolve('repo/test/lib-b/1.0/lib-b-1.0.pom').toFile()
        rangePom.parentFile.mkdirs()
        rangePom.text = """<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <groupId>test</groupId>
    <artifactId>lib-b</artifactId>
    <version>1.0</version>
    <dependencies>
        <dependency>
            <groupId>test</groupId>
            <artifactId>lib-a</artifactId>
            <version>[1,2)</version>
            <scope>runtime</scope>
        </dependency>
    </dependencies>
</project>
"""
        // lib-c constrains lib-a to a range through Gradle module metadata, the shape hibernate-core
        // gives log4j-core; the POM marker makes Gradle read the .module file
        write('repo/test/lib-c/1.0/lib-c-1.0.pom', """<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <!-- do_not_remove: published-with-gradle-metadata -->
    <modelVersion>4.0.0</modelVersion>
    <groupId>test</groupId>
    <artifactId>lib-c</artifactId>
    <version>1.0</version>
</project>
""")
        write('repo/test/lib-c/1.0/lib-c-1.0.module', """{
  "formatVersion": "1.1",
  "component": { "group": "test", "module": "lib-c", "version": "1.0" },
  "variants": [
    {
      "name": "apiElements",
      "attributes": { "org.gradle.category": "library", "org.gradle.libraryelements": "jar", "org.gradle.usage": "java-api" },
      "dependencyConstraints": [ { "group": "test", "module": "lib-a", "version": { "strictly": "[1,2)", "requires": "[1,2)" } } ]
    },
    {
      "name": "runtimeElements",
      "attributes": { "org.gradle.category": "library", "org.gradle.libraryelements": "jar", "org.gradle.usage": "java-runtime" },
      "dependencyConstraints": [ { "group": "test", "module": "lib-a", "version": { "strictly": "[1,2)", "requires": "[1,2)" } } ]
    }
  ]
}
""")
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

    def "fails when a transitive dependency is requested with a dynamic version"() {
        given: "a dependency whose own dependency is a range, although the BOM decides the version"
        writeLibrary('', "implementation 'test:lib-b:1.0'")

        when:
        BuildResult result = runner([]).buildAndFail()

        then:
        result.output.contains("Dependency version validation failed for project 'lib'")
        result.output.contains('requested with a dynamic version')
        result.output.contains('test:lib-b:1.0 -> test:lib-a:[1,2)')
    }

    def "fails when the build script itself requests a dynamic version"() {
        given:
        writeLibrary('', "implementation 'test:lib-a:1.+'")

        when:
        BuildResult result = runner([]).buildAndFail()

        then:
        result.output.contains("project ':lib'${BomDependencyGraph.BUILD_SCRIPT_MARKER} -> test:lib-a:1.+")
        result.output.contains('pin the version in that build script')
    }

    def "fails when a transitive dependency constraint requests a dynamic version"() {
        given: "a module whose published constraint on lib-a is a range, which no exclusion can remove"
        writeLibrary('', """
    implementation 'test:lib-c:1.0'
    implementation 'test:lib-a'
""")

        when:
        BuildResult result = runner([]).buildAndFail()

        then:
        result.output.contains("test:lib-c:1.0${BomDependencyGraph.CONSTRAINT_MARKER} -> test:lib-a:[1,2)")
        result.output.contains('component metadata rule')
    }

    def "allowedDynamicVersions tolerates a listed request"() {
        given: "the range also wins the version, which the override exemption covers"
        writeLibrary("""
ext.allowedDynamicVersions = ['test:lib-a']
ext.allowedBomOverrides = ['test:lib-a']
""", "implementation 'test:lib-b:1.0'")

        when:
        BuildResult result = runner([]).build()

        then:
        result.task(':lib:validateDependencyVersions').outcome == TaskOutcome.SUCCESS
    }

    def "passes when the dynamic dependency is excluded and the BOM-managed version declared in its place"() {
        given:
        writeLibrary('', """
    implementation 'test:lib-b:1.0', {
        exclude group: 'test', module: 'lib-a'
    }
    runtimeOnly 'test:lib-a'
""")

        when:
        BuildResult result = runner([]).build()

        then:
        result.task(':lib:validateDependencyVersions').outcome == TaskOutcome.SUCCESS
    }

    def "reports a dynamic version and a BOM mismatch in one failure"() {
        given: "a direct upgrade past the BOM and a transitive range in the same graph"
        writeLibrary('', """
    implementation 'test:lib-a:1.1'
    implementation 'test:lib-b:1.0'
""")

        when:
        BuildResult result = runner([]).buildAndFail()

        then: "the second report is not held back until the first is fixed"
        result.output.contains('test:lib-b:1.0 -> test:lib-a:[1,2)')
        result.output.contains('test:lib-a - resolved 1.1, expected 1.0')
    }

    def "warns about a dependency it could not resolve on every run, including a configuration cache hit"() {
        given: "a module no repository has, so nothing below it can be checked"
        writeLibrary('', "implementation 'test:lib-missing:1.0'")
        String warning = "Could not resolve test:lib-missing:1.0 in configuration 'compileClasspath' of project ':lib'"

        when: "the run that computes the task inputs and stores the entry"
        BuildResult stored = runner(['--configuration-cache']).build()

        and: "the run that reuses the entry and never computes them"
        BuildResult reused = runner(['--configuration-cache']).build()

        then:
        stored.output.contains('Configuration cache entry stored.')
        stored.task(':lib:validateDependencyVersions').outcome == TaskOutcome.SUCCESS
        stored.output.contains(warning)
        stored.output.contains('Could not find test:lib-missing:1.0')

        and: "the warning is logged by the task, so the reused run shows it too"
        reused.output.contains('Reusing configuration cache.')
        reused.task(':lib:validateDependencyVersions').outcome == TaskOutcome.SUCCESS
        reused.output.contains(warning)
    }

    def "warns about a configuration it could not resolve instead of passing silently"() {
        given: "a resolve hook that fails before the graph is built, so the configuration has no graph at all"
        writeLibrary("""
configurations.configureEach {
    incoming.beforeResolve { throw new GradleException('repository metadata unavailable') }
}
""")

        when:
        BuildResult result = runner([]).build()

        then:
        result.task(':lib:validateDependencyVersions').outcome == TaskOutcome.SUCCESS
        result.output.contains("Skipped configuration 'compileClasspath' of project ':lib' because it failed to resolve: repository metadata unavailable")
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

    private void writeLibrary(String extra = '', String dependencies = "implementation 'test:lib-a:1.1'") {
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
    ${dependencies}
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
