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

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.TempDir

class GrailsBomPluginSpec extends Specification {

    @TempDir
    Path testProjectDir

    def setup() {
        testProjectDir.resolve('settings.gradle').toFile().text = ''
        testProjectDir.resolve('.asf.yaml').toFile().text = ''
        testProjectDir.resolve('gradle.properties').toFile().text = 'projectVersion=1.2.3\n'
        testProjectDir.resolve('dependencies.gradle').toFile().text = ''
        testProjectDir.resolve('build.gradle').toFile().text = '''
            plugins {
                id 'org.apache.grails.buildsrc.bom-conventions'
            }

            ext.combinedPlatforms = [:]
            ext.combinedDependencies = [:]
            ext.combinedVersions = [:]

            tasks.register('verifyBomConventions') {
                doLast {
                    assert plugins.hasPlugin('java-platform')
                    assert project.group == 'org.apache.grails'
                    assert project.version.toString() == '1.2.3'
                    assert project.configurations.bomDependencies.canBeResolved
                    assert project.configurations.bomDependencies.extendsFrom.contains(project.configurations.api)
                    assert project.tasks.findByName('extractConstraints')
                    assert project.tasks.findByName('validateNoSnapshotDependencies')
                    assert project.ext.pomCustomization instanceof Closure
                }
            }

            tasks.register('writeCustomizedPom') {
                doLast {
                    def root = new Node(null, 'project')
                    root.appendNode('properties')
                    def dependencies = root.appendNode('dependencyManagement').appendNode('dependencies')
                    addDependency(dependencies, 'org.apache.grails', 'grails-gradle-plugins')
                    addDependency(dependencies, 'org.apache.grails.gradle', 'grails-gradle-common')

                    project.ext.pomCustomization.call(new Expando(asNode: { root }))

                    def writer = new StringWriter()
                    new groovy.xml.XmlNodePrinter(new PrintWriter(writer)).print(root)
                    def pomFile = file("$buildDir/customized-pom.xml")
                    pomFile.parentFile.mkdirs()
                    pomFile.text = writer.toString()
                }
            }

            tasks.register('generateMetadataFileForMavenPublication')
            tasks.register('generatePomFileForMavenPublication')

            void addDependency(Node dependencies, String groupId, String artifactId) {
                def dependency = dependencies.appendNode('dependency')
                dependency.appendNode('groupId', groupId)
                dependency.appendNode('artifactId', artifactId)
                dependency.appendNode('version', project.version.toString())
                dependency.appendNode('scope', 'compile')
            }
        '''
    }

    def "plugin wires shared BOM conventions"() {
        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('verifyBomConventions', '--stacktrace')
                .withPluginClasspath()
                .build()

        then:
        result.task(':verifyBomConventions').outcome == TaskOutcome.SUCCESS
    }

    def "pom customization registers Gradle build projects with their configured groups"() {
        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('writeCustomizedPom', '--stacktrace')
                .withPluginClasspath()
                .build()

        then:
        result.task(':writeCustomizedPom').outcome == TaskOutcome.SUCCESS

        and:
        def pom = testProjectDir.resolve('build/customized-pom.xml').toFile().text
        def xml = new groovy.xml.XmlSlurper().parseText(pom)
        xml.properties.'grails-gradle-plugins.version'.text().trim() == '1.2.3'
        xml.properties.'grails-gradle-common.version'.text().trim() == '1.2.3'
        xml.dependencyManagement.dependencies.dependency.find {
            it.groupId.text().trim() == 'org.apache.grails' &&
                    it.artifactId.text().trim() == 'grails-gradle-plugins'
        }.version.text().trim() == '${grails-gradle-plugins.version}'
        xml.dependencyManagement.dependencies.dependency.find {
            it.groupId.text().trim() == 'org.apache.grails.gradle' &&
                    it.artifactId.text().trim() == 'grails-gradle-common'
        }.version.text().trim() == '${grails-gradle-common.version}'
    }

    def "release snapshot validation wires lazily to publication tasks"() {
        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('generateMetadataFileForMavenPublication', '--stacktrace')
                .withEnvironment([
                        'GRAILS_PUBLISH_RELEASE'          : 'true',
                        'NEXUS_PUBLISH_STAGING_PROFILE_ID': 'test'
                ])
                .withPluginClasspath()
                .build()

        then:
        result.task(':validateNoSnapshotDependencies').outcome == TaskOutcome.SUCCESS
        result.task(':generateMetadataFileForMavenPublication').outcome == TaskOutcome.SUCCESS
    }
}
