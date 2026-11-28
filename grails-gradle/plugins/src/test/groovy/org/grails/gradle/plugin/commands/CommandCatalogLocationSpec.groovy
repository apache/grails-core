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
package org.grails.gradle.plugin.commands

import groovy.json.JsonSlurper

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome

import org.apache.grails.gradle.model.cli.ToolCatalogBuilder

/**
 * Where the catalog of the commands of a project is written: in the directory of the project, where
 * the command line reads it without running the build, whatever the build directory of the project is.
 * A catalog that an earlier build left in the build directory is not written again, and not read by
 * the command line, which reads {@link ToolCatalogBuilder#LOCATION} alone.
 */
class CommandCatalogLocationSpec extends CommandProjectSpecification {

    private static final String CATALOG = '.grails/cache/tool-catalog.json'
    private static final String STALE = '{"format":1,"complete":true,"diagnostics":[],"tools":[{"name":"stale","origin":"application","target":"application","registration":"descriptor"}]}\n'

    def "the catalog is where the command line reads it, in the directory of the project"() {
        expect:
        CATALOG == ToolCatalogBuilder.LOCATION
        GrailsToolCatalogTask.CATALOG_LOCATION == ToolCatalogBuilder.LOCATION
    }

    def "a project whose build directory is moved in #dsl has its catalog in the directory of the project, and a catalog of an earlier build is left as it is"() {
        given:
        setupCommandProject(moved)
        if (kotlin) {
            asKotlinBuild()
        }
        File old = projectFile('build/grails/tool-catalog.json', STALE)

        when:
        BuildResult result = executeTask('grailsToolCatalog')

        then:
        result.task(':grailsToolCatalog').outcome == TaskOutcome.SUCCESS
        catalog(projectDir).tools*.name == ['config-report']

        and: 'nothing of the catalog is in the build directory, which the build did write to'
        new File(projectDir, buildDirectory).isDirectory()
        !new File(projectDir, "${buildDirectory}/grails/tool-catalog.json").exists()

        and: 'the catalog an earlier build wrote to the build directory it had then is not written again'
        old.text == STALE

        and: 'the directory of the catalog keeps itself out of a commit'
        new File(projectDir, '.grails/cache/.gitignore').text == '*\n'

        where:
        dsl          | kotlin | buildDirectory | moved
        'Groovy'     | false  | 'out'          | "layout.buildDirectory = layout.projectDirectory.dir('out')\n"
        'Kotlin'     | true   | 'target'       | ''
    }

    def "a project whose build directory is outside of it has its catalog in its own directory"() {
        given:
        setupCommandProject('''
            layout.buildDirectory = layout.projectDirectory.dir('../elsewhere/command-catalog')
        ''')

        when:
        executeTask('grailsToolCatalog')

        then:
        catalog(projectDir).tools*.name == ['config-report']
        new File(projectDir, '../elsewhere/command-catalog').isDirectory()
        !new File(projectDir, '../elsewhere/command-catalog/grails').exists()
    }

    def "each project of a build has a catalog of its own, in its own directory, whatever the build directories are"() {
        given: 'a second application in the build, and the build directories of both below the one of the root'
        setupCommandProject('''
            layout.buildDirectory = layout.projectDirectory.dir('out')
        ''')
        new File(projectDir, 'settings.gradle') << "\ninclude 'second'\n"
        projectFile('second/build.gradle', '''
            plugins {
                id 'org.apache.grails.gradle.grails-app'
            }
            grails {
                bom = null
            }
            layout.buildDirectory = rootProject.layout.buildDirectory.dir('second')
            repositories {
                maven {
                    url = uri(rootProject.layout.projectDirectory.dir('maven-repo'))
                }
            }
            dependencies {
                implementation localGroovy()
            }
        ''')
        projectFile("second/src/main/resources/${CLI_FACTORIES}", registrations('second.SecondCommand'))
        projectFile("second/src/main/resources/${descriptorPath('second.SecondCommand')}", descriptor('second.SecondCommand', 'second', 'Of the second application'))

        when:
        BuildResult result = executeTask('grailsToolCatalog')

        then:
        result.task(':grailsToolCatalog').outcome == TaskOutcome.SUCCESS
        result.task(':second:grailsToolCatalog').outcome == TaskOutcome.SUCCESS
        catalog(projectDir).tools*.name == ['config-report']
        catalog(new File(projectDir, 'second')).tools*.name == ['config-report', 'second']
        new File(projectDir, 'out/second').isDirectory()
        !new File(projectDir, 'out/second/grails').exists()
    }

    def "a catalog that is up to date is not written again where it is, and one that is gone is written again"() {
        given:
        setupCommandProject('''
            layout.buildDirectory = layout.projectDirectory.dir('out')
        ''')
        executeTask('grailsToolCatalog')

        when:
        BuildResult again = executeTask('grailsToolCatalog')

        then:
        again.task(':grailsToolCatalog').outcome == TaskOutcome.UP_TO_DATE

        when: 'the build directory is deleted, as clean does: the catalog is not in it'
        new File(projectDir, 'out').deleteDir()
        BuildResult afterClean = executeTask('grailsToolCatalog')

        then:
        afterClean.task(':grailsToolCatalog').outcome == TaskOutcome.UP_TO_DATE
        catalog(projectDir).tools*.name == ['config-report']

        when: 'the catalog is deleted'
        new File(projectDir, CATALOG).delete()
        BuildResult written = executeTask('grailsToolCatalog')

        then:
        written.task(':grailsToolCatalog').outcome == TaskOutcome.SUCCESS
        catalog(projectDir).tools*.name == ['config-report']
    }

    // the build script of the project in the Kotlin DSL, with its build directory moved to target
    private void asKotlinBuild() {
        new File(projectDir, 'build.gradle').delete()
        projectFile('build.gradle.kts', '''
            plugins {
                id("org.apache.grails.gradle.grails-app")
            }

            grails {
                bom.set(null as String?)
            }

            repositories {
                maven {
                    url = uri(layout.projectDirectory.dir("maven-repo"))
                }
            }

            dependencies {
                implementation(localGroovy())
            }

            layout.buildDirectory.set(layout.projectDirectory.dir("target"))

            apply(from = "commands.gradle")
        '''.stripIndent())
    }

    private static Map catalog(File project) {
        (Map) new JsonSlurper().parseText(new File(project, CATALOG).getText('US-ASCII'))
    }
}
