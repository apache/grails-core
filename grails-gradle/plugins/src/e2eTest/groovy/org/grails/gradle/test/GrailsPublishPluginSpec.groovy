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

package org.grails.gradle.test

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure
import spock.lang.PendingFeature

import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile

class GrailsPublishPluginSpec extends GradleSpecification {
    List<File> toCleanup = []

    def cleanup() {
        for (File file : toCleanup) {
            try {
                file.deleteDir()
            }
            catch(ignored) {
            }
        }
    }

    def "gradle config works when not publishing - snapshot - maven publish - legacy-apply - multi-project-no-subproject-build-gradle-publish-all"() {
        given:
        GradleRunner runner = setupTestResourceProject('legacy-apply', 'multi-project-no-subproject-build-gradle-publish-all')

        when:
        def result = executeTask("assemble", ["--info"], runner)

        then:
        assertTaskSuccess("assemble", result)
        assertBuildSuccess(result, ["compileJava", "compileGroovy", "processResources", "classes", "jar", "groovydoc", "javadoc", "javadocJar", "sourcesJar"])

        !result.output.contains("does not have a version defined. Using the gradle property `projectVersion` to assume version is ")
        !result.output.contains("Environment Variable `GRAILS_PUBLISH_RELEASE` detected - using variable instead of project version.")
        result.output.contains("Project subproject1 will be a snapshot.")
        !result.output.contains("Project subproject1 will be a release.")
        result.output.contains("Project subproject2 will be a snapshot.")
        !result.output.contains("Project subproject2 will be a release.")
    }

    def "gradle config works when not publishing - snapshot - maven publish - legacy-apply - multi-project-no-subproject-build-gradle-publish-per-project"() {
        given:
        GradleRunner runner = setupTestResourceProject('legacy-apply', 'multi-project-no-subproject-build-gradle-publish-per-project')

        when:
        def result = executeTask("assemble", runner)

        then:
        assertTaskSuccess("assemble", result)
        assertBuildSuccess(result, ["compileJava", "compileGroovy", "processResources", "classes", "jar", "groovydoc", "javadoc", "javadocJar", "sourcesJar"])

        !result.output.contains("does not have a version defined. Using the gradle property `projectVersion` to assume version is ")
    }

    def "gradle config works when not publishing - snapshot - maven publish - legacy-apply - multi-project-parent-child-setup-per-project-parent-published"() {
        given:
        GradleRunner runner = setupTestResourceProject('legacy-apply', 'multi-project-parent-child-setup-per-project-parent-published')

        when:
        def result = executeTask("assemble", runner)

        then:
        assertTaskSuccess("assemble", result)
        assertBuildSuccess(result, ["compileJava", "compileGroovy", "processResources", "classes", "jar", "groovydoc", "javadoc", "javadocJar", "sourcesJar"])

        !result.output.contains("does not have a version defined. Using the gradle property `projectVersion` to assume version is ")
    }

    def "gradle config works when not publishing - snapshot - maven publish - legacy-apply - child-project-with-unrelated-parent - eval parent"() {
        given:
        GradleRunner runner = setupTestResourceProject('legacy-apply', 'child-project-with-unrelated-parent')

        when:
        def result = executeTask("assemble", runner)

        then:
        assertTaskSuccess("assemble", result)
        assertBuildSuccess(result, ["compileJava", "compileGroovy", "processResources", "classes", "jar", "groovydoc", "javadoc", "javadocJar", "sourcesJar"])

        !result.output.contains("does not have a version defined. Using the gradle property `projectVersion` to assume version is ")
    }

    def "gradle config works when not publishing - snapshot - maven publish - legacy-apply - child-project-with-unrelated-parent - eval child"() {
        given:
        GradleRunner runner = setupTestResourceProject('legacy-apply', 'child-project-with-unrelated-parent', 'otherProject')

        when:
        def result = executeTask("assemble", runner)

        then:
        assertTaskSuccess("assemble", result)
        assertBuildSuccess(result, ["compileJava", "compileGroovy", "processResources", "classes", "jar", "groovydoc", "javadoc", "javadocJar", "sourcesJar"])

        !result.output.contains("does not have a version defined. Using the gradle property `projectVersion` to assume version is ")
    }

    def "gradle config works when not publishing - snapshot - maven publish - legacy-apply - multi-project-parent-child-setup-per-project-child-published"() {
        given:
        GradleRunner runner = setupTestResourceProject('legacy-apply', 'multi-project-parent-child-setup-per-project-child-published')

        when:
        def result = executeTask("assemble", runner)

        then:
        assertTaskSuccess("assemble", result)
        assertBuildSuccess(result, ["compileJava", "compileGroovy", "processResources", "classes", "jar", "groovydoc", "javadoc", "javadocJar", "sourcesJar"])

        !result.output.contains("does not have a version defined. Using the gradle property `projectVersion` to assume version is ")
    }

    def "gradle config works when not publishing - snapshot - maven publish - legacy-apply - multi-project-with-subproject-gradle"() {
        given:
        GradleRunner runner = setupTestResourceProject('legacy-apply', 'multi-project-with-subproject-gradle')

        when:
        def result = executeTask("assemble", runner)

        then:
        assertTaskSuccess("assemble", result)
        assertBuildSuccess(result, ["compileJava", "compileGroovy", "processResources", "classes", "jar", "groovydoc", "javadoc", "javadocJar", "sourcesJar"])

        !result.output.contains("does not have a version defined. Using the gradle property `projectVersion` to assume version is ")
    }

    def "gradle config works when not publishing - snapshot - maven publish - plugins-block - multi-project-parent-child-setup-per-project-parent-published"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugins-block', 'multi-project-parent-child-setup-per-project-parent-published')

        when:
        def result = executeTask("assemble", runner)

        then:
        assertTaskSuccess("assemble", result)
        assertBuildSuccess(result, ["compileJava", "compileGroovy", "processResources", "classes", "jar", "groovydoc", "javadoc", "javadocJar", "sourcesJar"])

        !result.output.contains("Project subproject2 does not have a version defined. Using the gradle property `projectVersion` to assume version is 0.0.1-SNAPSHOT.")
        !result.output.contains("Project subproject1 does not have a version defined. Using the gradle property `projectVersion` to assume version is 0.0.1-SNAPSHOT.")
    }

    def "gradle config works when not publishing - snapshot - maven publish - plugins-block - child-project-with-unrelated-parent - eval parent"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugins-block', 'child-project-with-unrelated-parent')

        when:
        def result = executeTask("assemble", runner)

        then:
        assertTaskSuccess("assemble", result)
        assertBuildSuccess(result, ["compileJava", "compileGroovy", "processResources", "classes", "jar", "groovydoc", "javadoc", "javadocJar", "sourcesJar"])

        !result.output.contains("Project subproject2 does not have a version defined. Using the gradle property `projectVersion` to assume version is 0.0.1-SNAPSHOT.")
        !result.output.contains("Project subproject1 does not have a version defined. Using the gradle property `projectVersion` to assume version is 0.0.1-SNAPSHOT.")
    }

    def "gradle config works when not publishing - snapshot - maven publish - plugins-block - child-project-with-unrelated-parent - eval child"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugins-block', 'child-project-with-unrelated-parent', 'otherProject')

        when:
        def result = executeTask("assemble", runner)

        then:
        assertTaskSuccess("assemble", result)
        assertBuildSuccess(result, ["compileJava", "compileGroovy", "processResources", "classes", "jar", "groovydoc", "javadoc", "javadocJar", "sourcesJar"])

        !result.output.contains("Project subproject2 does not have a version defined. Using the gradle property `projectVersion` to assume version is 0.0.1-SNAPSHOT.")
        !result.output.contains("Project subproject1 does not have a version defined. Using the gradle property `projectVersion` to assume version is 0.0.1-SNAPSHOT.")
    }

    def "gradle config works when not publishing - snapshot - maven publish - plugins-block - multi-project-parent-child-setup-per-project-child-published"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugins-block', 'multi-project-parent-child-setup-per-project-child-published')

        when:
        def result = executeTask("assemble", runner)

        then:
        assertTaskSuccess("assemble", result)
        assertBuildSuccess(result, ["compileJava", "compileGroovy", "processResources", "classes", "jar", "groovydoc", "javadoc", "javadocJar", "sourcesJar"])

        !result.output.contains("does not have a version defined. Using the gradle property `projectVersion` to assume version is ")
    }

    def "gradle config works when not publishing - snapshot - maven publish - plugins-block - multi-project-with-subproject-gradle"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugins-block', 'multi-project-with-subproject-gradle')

        when:
        def result = executeTask("assemble", runner)

        then:
        assertTaskSuccess("assemble", result)
        assertBuildSuccess(result, ["compileJava", "compileGroovy", "processResources", "classes", "jar", "groovydoc", "javadoc", "javadocJar", "sourcesJar"])

        !result.output.contains("does not have a version defined. Using the gradle property `projectVersion` to assume version is ")
    }

    def "gradle config works when not publishing - milestone - maven publish - legacy-apply - multi-project-no-subproject-build-gradle-publish-all"() {
        given:
        GradleRunner runner = setupTestResourceProject('legacy-apply', 'multi-project-no-subproject-build-gradle-publish-all')

        setGradleProperty(
                "projectVersion",
                "0.0.1-M1",
                runner
        )

        when:
        def result = executeTask("assemble", runner)

        then:
        assertTaskSuccess("assemble", result)
        assertBuildSuccess(result, ["compileJava", "compileGroovy", "processResources", "classes", "jar", "groovydoc", "javadoc", "javadocJar", "sourcesJar"])

        !result.output.contains("does not have a version defined. Using the gradle property `projectVersion` to assume version is ")
    }

    def "gradle config works when not publishing - milestone - maven publish - legacy-apply - multi-project-no-subproject-build-gradle-publish-per-project"() {
        given:
        GradleRunner runner = setupTestResourceProject('legacy-apply', 'multi-project-no-subproject-build-gradle-publish-per-project')

        setGradleProperty(
                "projectVersion",
                "0.0.1-M1",
                runner
        )

        when:
        def result = executeTask("assemble", runner)

        then:
        assertTaskSuccess("assemble", result)
        assertBuildSuccess(result, ["compileJava", "compileGroovy", "processResources", "classes", "jar", "groovydoc", "javadoc", "javadocJar", "sourcesJar"])

        !result.output.contains("does not have a version defined. Using the gradle property `projectVersion` to assume version is ")
    }

    def "gradle config works when not publishing - milestone - maven publish - legacy-apply - multi-project-parent-child-setup-per-project-parent-published"() {
        given:
        GradleRunner runner = setupTestResourceProject('legacy-apply', 'multi-project-parent-child-setup-per-project-parent-published')

        setGradleProperty(
                "projectVersion",
                "0.0.1-M1",
                runner
        )

        when:
        def result = executeTask("assemble", runner)

        then:
        assertTaskSuccess("assemble", result)
        assertBuildSuccess(result, ["compileJava", "compileGroovy", "processResources", "classes", "jar", "groovydoc", "javadoc", "javadocJar", "sourcesJar"])

        !result.output.contains("does not have a version defined. Using the gradle property `projectVersion` to assume version is ")
    }

    def "gradle config works when not publishing - milestone - maven publish - legacy-apply - child-project-with-unrelated-parent - eval parent"() {
        given:
        GradleRunner runner = setupTestResourceProject('legacy-apply', 'child-project-with-unrelated-parent')

        setGradleProperty(
                "projectVersion",
                "0.0.1-M1",
                runner
        )

        when:
        def result = executeTask("assemble", runner)

        then:
        assertTaskSuccess("assemble", result)
        assertBuildSuccess(result, ["compileJava", "compileGroovy", "processResources", "classes", "jar", "groovydoc", "javadoc", "javadocJar", "sourcesJar"])

        !result.output.contains("does not have a version defined. Using the gradle property `projectVersion` to assume version is ")
    }

    def "gradle config works when not publishing - milestone - maven publish - legacy-apply - child-project-with-unrelated-parent - eval child"() {
        given:
        GradleRunner runner = setupTestResourceProject('legacy-apply', 'child-project-with-unrelated-parent', 'otherProject')

        setGradleProperty(
                "projectVersion",
                "0.0.1-M1",
                runner
        )

        when:
        def result = executeTask("assemble", runner)

        then:
        assertTaskSuccess("assemble", result)
        assertBuildSuccess(result, ["compileJava", "compileGroovy", "processResources", "classes", "jar", "groovydoc", "javadoc", "javadocJar", "sourcesJar"])

        !result.output.contains("does not have a version defined. Using the gradle property `projectVersion` to assume version is ")
    }

    @PendingFeature(reason = "Failed to apply plugin class 'io.github.gradlenexus.publishplugin.NexusPublishPlugin'")
    def "gradle config works when not publishing - milestone - maven publish - legacy-apply - multi-project-parent-child-setup-per-project-child-published"() {
        given:
        GradleRunner runner = setupTestResourceProject('legacy-apply', 'multi-project-parent-child-setup-per-project-child-published')

        setGradleProperty(
                "projectVersion",
                "0.0.1-M1",
                runner
        )

        when:
        def result = executeTask("assemble", runner)

        then:
        assertTaskSuccess("assemble", result)
        assertBuildSuccess(result, ["compileJava", "compileGroovy", "processResources", "classes", "jar", "groovydoc", "javadoc", "javadocJar", "sourcesJar"])

        !result.output.contains("does not have a version defined. Using the gradle property `projectVersion` to assume version is ")
    }

    @PendingFeature(reason = "Failed to apply plugin class 'io.github.gradlenexus.publishplugin.NexusPublishPlugin'")
    def "gradle config works when not publishing - milestone - maven publish - legacy-apply - multi-project-with-subproject-gradle"() {
        given:
        GradleRunner runner = setupTestResourceProject('legacy-apply', 'multi-project-with-subproject-gradle')

        setGradleProperty(
                "projectVersion",
                "0.0.1-M1",
                runner
        )

        when:
        def result = executeTask("assemble", runner)

        then:
        assertTaskSuccess("assemble", result)
        assertBuildSuccess(result, ["compileJava", "compileGroovy", "processResources", "classes", "jar", "groovydoc", "javadoc", "javadocJar", "sourcesJar"])

        !result.output.contains("does not have a version defined. Using the gradle property `projectVersion` to assume version is ")
    }

    def "gradle config works when not publishing - milestone - maven publish - plugins-block - multi-project-parent-child-setup-per-project-parent-published"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugins-block', 'multi-project-parent-child-setup-per-project-parent-published')

        setGradleProperty(
                "projectVersion",
                "0.0.1-M1",
                runner
        )

        when:
        def result = executeTask("assemble", runner)

        then:
        assertTaskSuccess("assemble", result)
        assertBuildSuccess(result, ["compileJava", "compileGroovy", "processResources", "classes", "jar", "groovydoc", "javadoc", "javadocJar", "sourcesJar"])

        result.output.contains("Project subproject1 does not have a version defined. Using the gradle property `projectVersion` to assume version is 0.0.1-M1.")
    }

    def "gradle config works when not publishing - milestone - maven publish - plugins-block - child-project-with-unrelated-parent - eval parent"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugins-block', 'child-project-with-unrelated-parent')

        setGradleProperty(
                "projectVersion",
                "0.0.1-M1",
                runner
        )

        when:
        def result = executeTask("assemble", runner)

        then:
        assertTaskSuccess("assemble", result)
        assertBuildSuccess(result, ["compileJava", "compileGroovy", "processResources", "classes", "jar", "groovydoc", "javadoc", "javadocJar", "sourcesJar"])

        result.output.contains("Project subproject1 does not have a version defined. Using the gradle property `projectVersion` to assume version is 0.0.1-M1.")
    }

    def "gradle config works when not publishing - milestone - maven publish - plugins-block - child-project-with-unrelated-parent - eval child"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugins-block', 'child-project-with-unrelated-parent', 'otherProject')

        setGradleProperty(
                "projectVersion",
                "0.0.1-M1",
                runner
        )

        when:
        def result = executeTask("assemble", runner)

        then:
        assertTaskSuccess("assemble", result)
        assertBuildSuccess(result, ["compileJava", "compileGroovy", "processResources", "classes", "jar", "groovydoc", "javadoc", "javadocJar", "sourcesJar"])

        result.output.contains("Project subproject1 does not have a version defined. Using the gradle property `projectVersion` to assume version is 0.0.1-M1.")
    }

    @PendingFeature(reason = "Failed to apply plugin class 'io.github.gradlenexus.publishplugin.NexusPublishPlugin'")
    def "gradle config works when not publishing - milestone - maven publish - plugins-block - multi-project-parent-child-setup-per-project-child-published"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugins-block', 'multi-project-parent-child-setup-per-project-child-published')

        setGradleProperty(
                "projectVersion",
                "0.0.1-M1",
                runner
        )

        when:
        def result = executeTask("assemble", runner)

        then:
        assertTaskSuccess("assemble", result)
        assertBuildSuccess(result, ["compileJava", "compileGroovy", "processResources", "classes", "jar", "groovydoc", "javadoc", "javadocJar", "sourcesJar"])

        !result.output.contains("does not have a version defined. Using the gradle property `projectVersion` to assume version is ")
    }

    @PendingFeature(reason = "Failed to apply plugin class 'io.github.gradlenexus.publishplugin.NexusPublishPlugin'")
    def "gradle config works when not publishing - milestone - maven publish - plugins-block - multi-project-with-subproject-gradle"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugins-block', 'multi-project-with-subproject-gradle')

        setGradleProperty(
                "projectVersion",
                "0.0.1-M1",
                runner
        )

        when:
        def result = executeTask("assemble", runner)

        then:
        assertTaskSuccess("assemble", result)
        assertBuildSuccess(result, ["compileJava", "compileGroovy", "processResources", "classes", "jar", "groovydoc", "javadoc", "javadocJar", "sourcesJar"])

        !result.output.contains("does not have a version defined. Using the gradle property `projectVersion` to assume version is ")
    }

    @PendingFeature(reason = "lazy eval and maven publish local cannot be distinguished between normal publish")
    def "project fails on maven publish without url"() {
        given:
        Path projectDir = createProjectDir("invalid-sources")

        GradleRunner runner = setupProject(projectDir)

        projectDir.resolve("settings.gradle").toFile().text = """
            rootProject.name = 'invalid-sources'
        """

        Path sourceDirectory = projectDir.resolve("src").resolve("main").resolve("groovy")
        sourceDirectory.toFile().mkdirs()

        sourceDirectory.resolve("Example.groovy").toFile().text = """
            class Example {
                String name
            }
        """

        projectDir.resolve('build.gradle').toFile().text = """
            buildscript {
                repositories {
                    maven { url "\${System.getenv('LOCAL_MAVEN_PATH')}\" }
                    maven { url = 'https://repo.grails.org/grails/core' }
                    maven { url = 'https://repository.apache.org/content/repositories/snapshots' }
                }
                dependencies {
                    classpath "org.apache.grails:grails-gradle-plugins:\$grailsGradlePluginVersion"
                }
            }
            
            version "0.0.1-SNAPSHOT"
            group "org.grails.example"

            apply plugin: 'java-library'
            apply plugin: 'groovy'
        
            repositories {
                maven { url = 'https://repo.grails.org/grails/core' }
                maven { url = 'https://repository.apache.org/content/repositories/snapshots' }
            }

            dependencies {
                implementation "org.apache.groovy:groovy:\$groovyVersion"
            }
        
            apply plugin: 'org.apache.grails.gradle.grails-publish'
            grailsPublish {
                githubSlug = 'apache/grails-cores'
                license {
                    name = 'Apache-2.0'
                }
                title = 'Grails Gradle Plugin - Example Project'
                desc = 'A testing project for the grails gradle plugin'
                developers = [
                        jdaugherty: 'James Daugherty',
                ]
            }
        """

        when:
        def assembleResult = executeTask("assemble", runner)

        then:
        assertTaskSuccess("assemble", assembleResult)
        assertBuildSuccess(assembleResult, ["compileJava", "compileGroovy", "processResources", "classes", "jar", "groovydoc", "javadoc", "javadocJar", "sourcesJar"])

        when:
        runner = addEnvironmentVariable("MAVEN_PUBLISH_USERNAME", "publishUser", runner)
        runner = addEnvironmentVariable("MAVEN_PUBLISH_PASSWORD", "publishPassword", runner)

        executeTask("publish", runner)

        then:
        UnexpectedBuildFailure bf = thrown(UnexpectedBuildFailure)
        bf.buildResult.output.contains("Could not locate a project property of `mavenPublishUrl` or an environment variable of `MAVEN_PUBLISH_URL`. A URL is required for maven publishing.")
    }

    def "project without java plugin fails grailsPublish apply"() {
        given:
        Path projectDir = createProjectDir("invalid-sources")

        GradleRunner runner = setupProject(projectDir)

        projectDir.resolve("settings.gradle").toFile().text = """
            rootProject.name = 'invalid-sources'
        """

        projectDir.resolve('build.gradle').toFile().text = """
            buildscript {
                repositories {
                    maven { url "\${System.getenv('LOCAL_MAVEN_PATH')}\" }
                    maven { url = 'https://repo.grails.org/grails/core' }
                    maven { url = 'https://repository.apache.org/content/repositories/snapshots' }
                }
                dependencies {
                    classpath "org.apache.grails:grails-gradle-plugins:\$grailsGradlePluginVersion"
                }
            }
            
            version "0.0.1"
            
            apply plugin: 'org.apache.grails.gradle.grails-publish'
        """

        when:
        executeTask("assemble", runner)

        then:
        UnexpectedBuildFailure bf = thrown(UnexpectedBuildFailure)
        bf.buildResult.output.contains("Grails Publish Plugin requires the Java Plugin to be applied to the project.")
    }

    def "project without sources fails grailsPublish apply"() {
        given:
        Path projectDir = createProjectDir("invalid-sources")

        GradleRunner runner = setupProject(projectDir)

        projectDir.resolve("settings.gradle").toFile().text = """
            rootProject.name = 'invalid-sources'
        """

        projectDir.resolve('build.gradle').toFile().text = """
            buildscript {
                repositories {
                    maven { url "\${System.getenv('LOCAL_MAVEN_PATH')}\" }
                    maven { url = 'https://repo.grails.org/grails/core' }                    
                    maven { url = 'https://repository.apache.org/content/repositories/snapshots' }
                }
                dependencies {
                    classpath "org.apache.grails:grails-gradle-plugins:\$grailsGradlePluginVersion"
                }
            }
            
            version "0.0.1"
            
            apply plugin: 'java'
            apply plugin: 'org.apache.grails.gradle.grails-publish'
        """

        when:
        executeTask("assemble", runner)

        then:
        UnexpectedBuildFailure bf = thrown(UnexpectedBuildFailure)
        bf.buildResult.output.contains("Cannot apply Grails Publish Plugin. Project invalid-sources does not have anything to publish.")
    }

    def "project with environment variable based snapshot or release detection - is snapshot"() {
        given:
        GradleRunner runner = setupTestResourceProject('legacy-apply', 'multi-project-no-subproject-build-gradle-publish-all')

        runner = setGradleProperty("projectVersion", "0.0.1-M1", runner)
        runner = addEnvironmentVariable("GRAILS_PUBLISH_RELEASE", "false", runner)

        when:
        def result = executeTask("assemble", ["--info"], runner)

        then:
        assertTaskSuccess("assemble", result)
        assertBuildSuccess(result, ["compileJava", "compileGroovy", "processResources", "classes", "jar", "groovydoc", "javadoc", "javadocJar", "sourcesJar"])

        !result.output.contains("does not have a version defined. Using the gradle property `projectVersion` to assume version is ")
        result.output.contains("Environment Variable `GRAILS_PUBLISH_RELEASE` detected - using variable instead of project version.")
        result.output.contains("Project subproject1 will be a snapshot.")
        result.output.contains("Project subproject2 will be a snapshot.")
    }

    def "project with environment variable based snapshot or release detection - is release"() {
        given:
        GradleRunner runner = setupTestResourceProject('legacy-apply', 'multi-project-no-subproject-build-gradle-publish-all')

        runner = setGradleProperty("projectVersion", "0.0.1-SNAPSHOT", runner)
        runner = addEnvironmentVariable("GRAILS_PUBLISH_RELEASE", "true", runner)

        when:
        def result = executeTask("assemble", ["--info"], runner)

        then:
        assertTaskSuccess("assemble", result)
        assertBuildSuccess(result, ["compileJava", "compileGroovy", "processResources", "classes", "jar", "groovydoc", "javadoc", "javadocJar", "sourcesJar"])

        !result.output.contains("does not have a version defined. Using the gradle property `projectVersion` to assume version is ")
        result.output.contains("Environment Variable `GRAILS_PUBLISH_RELEASE` detected - using variable instead of project version.")
        result.output.contains("Project subproject1 will be a release.")
        result.output.contains("Project subproject2 will be a release.")
    }

    def "source artifact test - simple project"() {
        given:
        File tempDir = File.createTempDir("simple-project")
        toCleanup << tempDir

        and:
        GradleRunner runner = setupTestResourceProject('other-artifacts', 'simple-project')

        runner = setGradleProperty("projectVersion", "0.0.1-SNAPSHOT", runner)
        runner = setGradleProperty("mavenPublishUrl", tempDir.toPath().toAbsolutePath().toString(), runner)
        runner = addEnvironmentVariable("GRAILS_PUBLISH_RELEASE", "false", runner)

        when:
        def result = executeTask("publish", ["--info"], runner)

        then:
        assertTaskSuccess("sourcesJar", result)
        assertTaskSuccess("javadocJar", result)
        assertTaskSuccess("groovydoc", result)
        assertBuildSuccess(result, ["compileJava", "compileGroovy", "processResources", "classes", "jar", "groovydoc", "javadoc", "javadocJar", "sourcesJar", "grailsPublishValidation", "requireMavenPublishUrl", "generateMetadataFileForMavenPublication", "generatePomFileForMavenPublication", "publishMavenPublicationToMavenLocal", "publishToMavenLocal"])

        !result.output.contains("does not have a version defined. Using the gradle property `projectVersion` to assume version is ")
        result.output.contains("Environment Variable `GRAILS_PUBLISH_RELEASE` detected - using variable instead of project version.")

        and:
        Path artifactDir = tempDir.toPath().resolve("org/grails/example/simple-project/0.0.1-SNAPSHOT")
        Files.exists(artifactDir)
        File[] artifacts = artifactDir.toFile().listFiles()

        /*
        simple-project-0.0.1-20250212.043425-1.pom.sha1
        simple-project-0.0.1-20250212.043425-1.pom.md5
        simple-project-0.0.1-20250212.043425-1-javadoc.jar.sha1
        simple-project-0.0.1-20250212.043425-1.module.md5
        simple-project-0.0.1-20250212.043425-1-sources.jar.md5
        simple-project-0.0.1-20250212.043425-1.jar.md5
        maven-metadata.xml
        simple-project-0.0.1-20250212.043425-1.module
        simple-project-0.0.1-20250212.043425-1.pom
        simple-project-0.0.1-20250212.043425-1.jar.sha1
        simple-project-0.0.1-20250212.043425-1-javadoc.jar
        simple-project-0.0.1-20250212.043425-1-sources.jar
        simple-project-0.0.1-20250212.043425-1.jar
        simple-project-0.0.1-20250212.043425-1.module.sha1
        simple-project-0.0.1-20250212.043425-1-sources.jar.sha1
        maven-metadata.xml.md5
        simple-project-0.0.1-20250212.043425-1-javadoc.jar.md5
        maven-metadata.xml.sha1
         */

        File javadocJar = artifacts.find{ it.name.endsWith("javadoc.jar") }
        javadocJar
        findJarFileEntry("DefaultPackage/TestJava.html", javadocJar)
        findJarFileEntry("another/TestOtherJava.html", javadocJar)
        findJarFileEntry("org/grails/example/MyProject.html", javadocJar)

        File sourcesJar = artifacts.find{ it.name.endsWith("sources.jar") }
        sourcesJar
        findJarFileEntry("TestJava.java", sourcesJar)
        findJarFileEntry("org/grails/example/MyProject.groovy", sourcesJar)

        File classesJar = artifacts.find{ it.name.endsWith(".jar") && !it.name.endsWith("javadoc.jar") && !it.name.endsWith("sources.jar") }
        classesJar
        findJarFileEntry("TestJava.class", classesJar)
        findJarFileEntry("org/grails/example/MyProject.class", classesJar)
    }

    boolean findJarFileEntry(String path, File file) {
        try (JarFile jarFile = new JarFile(file)) {
            return jarFile.getEntry(path) != null
        }
    }

    def "source artifact test - java already configured"() {
        given:
        File tempDir = File.createTempDir("java-already-configured")
        toCleanup << tempDir

        and:
        GradleRunner runner = setupTestResourceProject('other-artifacts', 'java-already-configured')

        runner = setGradleProperty("projectVersion", "0.0.1-SNAPSHOT", runner)
        runner = setGradleProperty("mavenPublishUrl", tempDir.toPath().toAbsolutePath().toString(), runner)
        runner = addEnvironmentVariable("GRAILS_PUBLISH_RELEASE", "false", runner)

        when:
        def result = executeTask("publish", ["--info"], runner)

        then:
        assertTaskSuccess("sourcesJar", result)
        assertTaskSuccess("javadocJar", result)
        assertTaskSuccess("groovydoc", result)
        assertBuildSuccess(result, ["compileJava", "compileGroovy", "processResources", "classes", "jar", "groovydoc", "javadoc", "javadocJar", "sourcesJar", "grailsPublishValidation", "requireMavenPublishUrl", "generateMetadataFileForMavenPublication", "generatePomFileForMavenPublication", "publishMavenPublicationToMavenLocal", "publishToMavenLocal"])

        !result.output.contains("does not have a version defined. Using the gradle property `projectVersion` to assume version is ")
        result.output.contains("Environment Variable `GRAILS_PUBLISH_RELEASE` detected - using variable instead of project version.")

        and:
        Path artifactDir = tempDir.toPath().resolve("org/grails/example/java-already-configured/0.0.1-SNAPSHOT")
        Files.exists(artifactDir)
        File[] artifacts = artifactDir.toFile().listFiles()

        File javadocJar = artifacts.find{ it.name.endsWith("javadoc.jar") }
        javadocJar
        findJarFileEntry("DefaultPackage/TestJava.html", javadocJar)
        findJarFileEntry("another/TestOtherJava.html", javadocJar)
        findJarFileEntry("org/grails/example/MyProject.html", javadocJar)

        File sourcesJar = artifacts.find{ it.name.endsWith("sources.jar") }
        sourcesJar
        findJarFileEntry("TestJava.java", sourcesJar)
        findJarFileEntry("org/grails/example/MyProject.groovy", sourcesJar)

        File classesJar = artifacts.find{ it.name.endsWith(".jar") && !it.name.endsWith("javadoc.jar") && !it.name.endsWith("sources.jar") }
        classesJar
        findJarFileEntry("TestJava.class", classesJar)
        findJarFileEntry("org/grails/example/MyProject.class", classesJar)
    }

    def "source artifact test - groovydoc disabled"() {
        given:
        File tempDir = File.createTempDir("groovy-doc-disabled")
        toCleanup << tempDir

        and:
        GradleRunner runner = setupTestResourceProject('other-artifacts', 'groovy-doc-disabled')

        runner = setGradleProperty("projectVersion", "0.0.1-SNAPSHOT", runner)
        runner = setGradleProperty("mavenPublishUrl", tempDir.toPath().toAbsolutePath().toString(), runner)
        runner = addEnvironmentVariable("GRAILS_PUBLISH_RELEASE", "false", runner)

        when:
        executeTask("publish", ["--info"], runner)

        then:
        Exception e = thrown(Exception)
        e.message.contains("Groovydoc task is disabled. Please enable it to ensure javadoc can be published correctly with the Grails Publish Plugin.")
    }

    def "source artifact test - java only project"() {
        given:
        File tempDir = File.createTempDir("java-only-project")
        toCleanup << tempDir

        and:
        GradleRunner runner = setupTestResourceProject('other-artifacts', 'java-only-project')

        runner = setGradleProperty("projectVersion", "0.0.1-SNAPSHOT", runner)
        runner = setGradleProperty("mavenPublishUrl", tempDir.toPath().toAbsolutePath().toString(), runner)
        runner = addEnvironmentVariable("GRAILS_PUBLISH_RELEASE", "false", runner)

        when:
        def result = executeTask("publish", ["--info"], runner)

        then:
        assertTaskSuccess("sourcesJar", result)
        assertTaskSuccess("javadocJar", result)
        assertBuildSuccess(result, ["compileJava", "processResources", "classes", "jar", "javadoc", "javadocJar", "sourcesJar", "grailsPublishValidation", "requireMavenPublishUrl", "generateMetadataFileForMavenPublication", "generatePomFileForMavenPublication", "publishMavenPublicationToMavenLocal", "publishToMavenLocal"])

        !result.output.contains("does not have a version defined. Using the gradle property `projectVersion` to assume version is ")
        result.output.contains("Environment Variable `GRAILS_PUBLISH_RELEASE` detected - using variable instead of project version.")

        and:
        Path artifactDir = tempDir.toPath().resolve("org/grails/example/java-only-project/0.0.1-SNAPSHOT")
        Files.exists(artifactDir)
        File[] artifacts = artifactDir.toFile().listFiles()

        File javadocJar = artifacts.find{ it.name.endsWith("javadoc.jar") }
        javadocJar
        findJarFileEntry("TestJava.html", javadocJar) // TODO: Unfortunately, groovydoc seems to always put this under DefaultPackage, while javadoc does not
        findJarFileEntry("another/TestOtherJava.html", javadocJar)

        File sourcesJar = artifacts.find{ it.name.endsWith("sources.jar") }
        sourcesJar
        findJarFileEntry("TestJava.java", sourcesJar)

        File classesJar = artifacts.find{ it.name.endsWith(".jar") && !it.name.endsWith("javadoc.jar") && !it.name.endsWith("sources.jar") }
        classesJar
        findJarFileEntry("TestJava.class", classesJar)
    }

    def "source artifact test - non-groovy-java-sources are published"() {
        given:
        File tempDir = File.createTempDir("non-groovy-java-sources-included")
        toCleanup << tempDir

        and:
        GradleRunner runner = setupTestResourceProject('other-artifacts', 'non-groovy-java-sources-included')

        runner = setGradleProperty("projectVersion", "0.0.1-SNAPSHOT", runner)
        runner = setGradleProperty("mavenPublishUrl", tempDir.toPath().toAbsolutePath().toString(), runner)
        runner = addEnvironmentVariable("GRAILS_PUBLISH_RELEASE", "false", runner)

        when:
        def result = executeTask("publish", ["--info"], runner)

        then:
        assertTaskSuccess("sourcesJar", result)
        assertTaskSuccess("javadocJar", result)
        assertBuildSuccess(result, ["compileJava", "processResources", "classes", "jar", "javadoc", "javadocJar", "sourcesJar", "grailsPublishValidation", "requireMavenPublishUrl", "generateMetadataFileForMavenPublication", "generatePomFileForMavenPublication", "publishMavenPublicationToMavenLocal", "publishToMavenLocal"])

        !result.output.contains("does not have a version defined. Using the gradle property `projectVersion` to assume version is ")
        result.output.contains("Environment Variable `GRAILS_PUBLISH_RELEASE` detected - using variable instead of project version.")

        and:
        System.out.println(tempDir.list())
        Path artifactDir = tempDir.toPath().resolve("org/grails/example/non-groovy-java-sources-included/0.0.1-SNAPSHOT")
        Files.exists(artifactDir)
        File[] artifacts = artifactDir.toFile().listFiles()

        File javadocJar = artifacts.find{ it.name.endsWith("javadoc.jar") }
        javadocJar
        findJarFileEntry("DefaultPackage/TestJava.html", javadocJar)
        findJarFileEntry("another/TestOtherJava.html", javadocJar)

        File sourcesJar = artifacts.find{ it.name.endsWith("sources.jar") }
        sourcesJar
        findJarFileEntry("TestJava.java", sourcesJar)
        findJarFileEntry("Testing.txt", sourcesJar)

        File classesJar = artifacts.find{ it.name.endsWith(".jar") && !it.name.endsWith("javadoc.jar") && !it.name.endsWith("sources.jar") }
        classesJar
        findJarFileEntry("TestJava.class", classesJar)
    }

    def "source artifact test - multi project plugins configured in child"() {
        given:
        File tempDir = File.createTempDir("multi-project-plugins-applied-child")
        toCleanup << tempDir

        and:
        GradleRunner runner = setupTestResourceProject('other-artifacts', 'multi-project-plugins-applied-child')

        runner = setGradleProperty("projectVersion", "0.0.1-SNAPSHOT", runner)
        runner = setGradleProperty("mavenPublishUrl", tempDir.toPath().toAbsolutePath().toString(), runner)
        runner = addEnvironmentVariable("GRAILS_PUBLISH_RELEASE", "false", runner)

        when:
        def result = executeTask("publish", ["--info"], runner)

        then:
        assertTaskSuccess("sourcesJar", result)
        assertTaskSuccess("javadocJar", result)
        assertBuildSuccess(result, ["compileJava", "processResources", "classes", "jar", "javadoc", "javadocJar", "sourcesJar", "grailsPublishValidation", "requireMavenPublishUrl", "generateMetadataFileForMavenPublication", "generatePomFileForMavenPublication", "publishMavenPublicationToMavenLocal", "publishToMavenLocal"])

        !result.output.contains("does not have a version defined. Using the gradle property `projectVersion` to assume version is ")
        result.output.contains("Environment Variable `GRAILS_PUBLISH_RELEASE` detected - using variable instead of project version.")

        and:
        Path subproject2Path = tempDir.toPath().resolve("org/grails/example/subproject2/0.0.1-SNAPSHOT")
        !Files.exists(subproject2Path)

        and:
        Path artifactDir = tempDir.toPath().resolve("org/grails/example/subproject1/0.0.1-SNAPSHOT")
        Files.exists(artifactDir)
        File[] artifacts = artifactDir.toFile().listFiles()

        File javadocJar = artifacts.find{ it.name.endsWith("javadoc.jar") }
        javadocJar
        findJarFileEntry("DefaultPackage/TestJava.html", javadocJar)
        findJarFileEntry("another/TestOtherJava.html", javadocJar)
        findJarFileEntry("org/grails/example/SubProject1.html", javadocJar)
        !findJarFileEntry("org/grails/example/SubProject2.html", javadocJar)

        File sourcesJar = artifacts.find{ it.name.endsWith("sources.jar") }
        sourcesJar
        findJarFileEntry("TestJava.java", sourcesJar)
        findJarFileEntry("org/grails/example/SubProject1.groovy", sourcesJar)
        !findJarFileEntry("org/grails/example/SubProject2.groovy", sourcesJar)

        File classesJar = artifacts.find{ it.name.endsWith(".jar") && !it.name.endsWith("javadoc.jar") && !it.name.endsWith("sources.jar") }
        classesJar
        findJarFileEntry("TestJava.class", classesJar)
        findJarFileEntry("org/grails/example/SubProject1.class", classesJar)
        !findJarFileEntry("org/grails/example/SubProject2.class", classesJar)
    }

    def "source artifact test - multi project plugins configured in parent"() {
        given:
        File tempDir = File.createTempDir("multi-project-plugins-applied-parent")
        toCleanup << tempDir

        and:
        GradleRunner runner = setupTestResourceProject('other-artifacts', 'multi-project-plugins-applied-parent')

        runner = setGradleProperty("projectVersion", "0.0.1-SNAPSHOT", runner)
        runner = setGradleProperty("mavenPublishUrl", tempDir.toPath().toAbsolutePath().toString(), runner)
        runner = addEnvironmentVariable("GRAILS_PUBLISH_RELEASE", "false", runner)

        when:
        def result = executeTask("publish", ["--info"], runner)

        then:
        assertTaskSuccess("sourcesJar", result)
        assertTaskSuccess("javadocJar", result)
        assertBuildSuccess(result, ["compileJava", "processResources", "classes", "jar", "javadoc", "javadocJar", "sourcesJar", "grailsPublishValidation", "requireMavenPublishUrl", "generateMetadataFileForMavenPublication", "generatePomFileForMavenPublication", "publishMavenPublicationToMavenLocal", "publishToMavenLocal"])

        !result.output.contains("does not have a version defined. Using the gradle property `projectVersion` to assume version is ")
        result.output.contains("Environment Variable `GRAILS_PUBLISH_RELEASE` detected - using variable instead of project version.")

        and:
        Path subproject2Path = tempDir.toPath().resolve("org/grails/example/subproject2/0.0.1-SNAPSHOT")
        !Files.exists(subproject2Path)

        and:
        Path artifactDir = tempDir.toPath().resolve("org/grails/example/subproject1/0.0.1-SNAPSHOT")
        Files.exists(artifactDir)
        File[] artifacts = artifactDir.toFile().listFiles()

        File javadocJar = artifacts.find{ it.name.endsWith("javadoc.jar") }
        javadocJar
        findJarFileEntry("DefaultPackage/TestJava.html", javadocJar)
        findJarFileEntry("another/TestOtherJava.html", javadocJar)
        findJarFileEntry("org/grails/example/SubProject1.html", javadocJar)
        !findJarFileEntry("org/grails/example/SubProject2.html", javadocJar)

        File sourcesJar = artifacts.find{ it.name.endsWith("sources.jar") }
        sourcesJar
        findJarFileEntry("TestJava.java", sourcesJar)
        findJarFileEntry("org/grails/example/SubProject1.groovy", sourcesJar)
        !findJarFileEntry("org/grails/example/SubProject2.groovy", sourcesJar)

        File classesJar = artifacts.find{ it.name.endsWith(".jar") && !it.name.endsWith("javadoc.jar") && !it.name.endsWith("sources.jar") }
        classesJar
        findJarFileEntry("TestJava.class", classesJar)
        findJarFileEntry("org/grails/example/SubProject1.class", classesJar)
        !findJarFileEntry("org/grails/example/SubProject2.class", classesJar)
    }

    def "source artifact test - explicit jar creation without gradle assistance"() {
        given:
        File tempDir = File.createTempDir("explicit-jar-creation-without-gradle-assistance")
        toCleanup << tempDir

        and:
        GradleRunner runner = setupTestResourceProject('other-artifacts', 'explicit-jar-creation-without-gradle-assistance')

        runner = setGradleProperty("projectVersion", "0.0.1-SNAPSHOT", runner)
        runner = setGradleProperty("mavenPublishUrl", tempDir.toPath().toAbsolutePath().toString(), runner)
        runner = addEnvironmentVariable("GRAILS_PUBLISH_RELEASE", "false", runner)

        when:
        def result = executeTask("publish", ["--info"], runner)

        then:
        assertTaskSuccess("sourcesJar", result)
        assertTaskSuccess("javadocJar", result)
        assertTaskSuccess("groovydoc", result)
        assertBuildSuccess(result, ["compileJava", "compileGroovy", "processResources", "classes", "jar", "groovydoc", "javadoc", "javadocJar", "sourcesJar", "grailsPublishValidation", "requireMavenPublishUrl", "generateMetadataFileForMavenPublication", "generatePomFileForMavenPublication", "publishMavenPublicationToMavenLocal", "publishToMavenLocal"])

        !result.output.contains("does not have a version defined. Using the gradle property `projectVersion` to assume version is ")
        result.output.contains("Environment Variable `GRAILS_PUBLISH_RELEASE` detected - using variable instead of project version.")

        and:
        Path artifactDir = tempDir.toPath().resolve("org/grails/example/explicit-jar-creation-without-gradle-assistance/0.0.1-SNAPSHOT")
        Files.exists(artifactDir)
        File[] artifacts = artifactDir.toFile().listFiles()

        File javadocJar = artifacts.find{ it.name.endsWith("javadoc.jar") }
        javadocJar
        findJarFileEntry("DefaultPackage/TestJava.html", javadocJar)
        findJarFileEntry("another/TestOtherJava.html", javadocJar)
        findJarFileEntry("org/grails/example/MyProject.html", javadocJar)

        File sourcesJar = artifacts.find{ it.name.endsWith("sources.jar") }
        sourcesJar
        findJarFileEntry("TestJava.java", sourcesJar)
        findJarFileEntry("org/grails/example/MyProject.groovy", sourcesJar)

        File classesJar = artifacts.find{ it.name.endsWith(".jar") && !it.name.endsWith("javadoc.jar") && !it.name.endsWith("sources.jar") }
        classesJar
        findJarFileEntry("TestJava.class", classesJar)
        findJarFileEntry("org/grails/example/MyProject.class", classesJar)
    }

    def "source artifact test - groovy only project"() {
        given:
        File tempDir = File.createTempDir("groovy-only-project")
        toCleanup << tempDir

        and:
        GradleRunner runner = setupTestResourceProject('other-artifacts', 'groovy-only-project')

        runner = setGradleProperty("projectVersion", "0.0.1-SNAPSHOT", runner)
        runner = setGradleProperty("mavenPublishUrl", tempDir.toPath().toAbsolutePath().toString(), runner)
        runner = addEnvironmentVariable("GRAILS_PUBLISH_RELEASE", "false", runner)

        when:
        def result = executeTask("publish", ["--info"], runner)

        then:
        assertTaskSuccess("sourcesJar", result)
        assertTaskSuccess("javadocJar", result)
        assertTaskSuccess("groovydoc", result)
        assertBuildSuccess(result, ["compileJava", "compileGroovy", "processResources", "classes", "jar", "groovydoc", "javadoc", "javadocJar", "sourcesJar", "grailsPublishValidation", "requireMavenPublishUrl", "generateMetadataFileForMavenPublication", "generatePomFileForMavenPublication", "publishMavenPublicationToMavenLocal", "publishToMavenLocal"])

        !result.output.contains("does not have a version defined. Using the gradle property `projectVersion` to assume version is ")
        result.output.contains("Environment Variable `GRAILS_PUBLISH_RELEASE` detected - using variable instead of project version.")

        and:
        Path artifactDir = tempDir.toPath().resolve("org/grails/example/groovy-only-project/0.0.1-SNAPSHOT")
        Files.exists(artifactDir)
        File[] artifacts = artifactDir.toFile().listFiles()

        File javadocJar = artifacts.find{ it.name.endsWith("javadoc.jar") }
        javadocJar
        findJarFileEntry("org/grails/example/MyProject.html", javadocJar)

        File sourcesJar = artifacts.find{ it.name.endsWith("sources.jar") }
        sourcesJar
        findJarFileEntry("org/grails/example/MyProject.groovy", sourcesJar)

        File classesJar = artifacts.find{ it.name.endsWith(".jar") && !it.name.endsWith("javadoc.jar") && !it.name.endsWith("sources.jar") }
        classesJar
        findJarFileEntry("org/grails/example/MyProject.class", classesJar)
    }
}
