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

/**
 * The class paths of the tools of an application have the versions the application manages: the
 * platforms and the dependency constraints it declares on its main configurations, so a dependency
 * of {@code grailsCli} or {@code grailsCliStandalone} is written without a version, as every other
 * dependency of the application is. They do not have the dependencies of the application: a
 * standalone tool is still started without them.
 */
class ToolClasspathPlatformsSpec extends CommandProjectSpecification {

    private static final String CLASSPATHS = '''
        tasks.register('toolClasspaths') {
            def classpaths = [
                    GRAILS_CLI_CLASSPATH           : configurations.grailsCliClasspath,
                    GRAILS_CLI_STANDALONE_CLASSPATH: configurations.grailsCliStandaloneClasspath,
                    CLI                            : configurations.cliCompileClasspath,
                    CLI_RUN                        : configurations.cliRuntimeClasspath,
                    STANDALONE                     : configurations.standaloneCompileClasspath,
                    STANDALONE_RUN                 : configurations.standaloneRuntimeClasspath,
                    STANDALONE_SOURCE_SET_RUN      : sourceSets.standalone.runtimeClasspath,
                    RUNTIME                        : configurations.runtimeClasspath,
            ]
            doLast {
                classpaths.each { String name, FileCollection files ->
                    println name + '=' + files.files.findAll { it.isFile() }.collect { it.name }.sort().join(',')
                }
            }
        }
    '''

    private static final List<String> CLASSPATHS_OF_TOOLS = ['GRAILS_CLI_CLASSPATH', 'GRAILS_CLI_STANDALONE_CLASSPATH', 'CLI', 'CLI_RUN',
                                                             'STANDALONE', 'STANDALONE_RUN', 'STANDALONE_SOURCE_SET_RUN']

    private static final List<String> STANDALONE_CLASSPATHS = ['GRAILS_CLI_STANDALONE_CLASSPATH', 'STANDALONE', 'STANDALONE_RUN', 'STANDALONE_SOURCE_SET_RUN']

    def "the tools of an application that declares #declared on implementation resolve their dependencies without a version, and have none of the application"() {
        given: 'an application that manages its versions with a platform and a constraint on its main configurations'
        setupCommandProject(CLASSPATHS + """
            grails {
                bom = ${bom}
            }
            dependencies {
                implementation platform('${platform}')
                implementation 'org.example:of-the-application'
                constraints {
                    implementation 'org.example:constrained:2.0'
                }

                grailsCli 'org.example:only-for-tools'
                grailsCli 'org.example:constrained'
                grailsCliStandalone 'org.example:only-for-standalone-tools'
            }
        """)
        publishPlatform(platform)
        publish('org.example', 'of-the-application', '1.0', [:])
        publish('org.example', 'only-for-tools', '1.1', [:])
        publish('org.example', 'only-for-standalone-tools', '1.2', [:])
        publish('org.example', 'constrained', '2.0', [:])

        when:
        Map<String, List<String>> paths = paths(executeTask('toolClasspaths'))

        then: 'the scope of the tools the build runs has the versions of the platform and of the constraint'
        ['GRAILS_CLI_CLASSPATH', 'CLI', 'CLI_RUN'].every { String name ->
            paths[name].containsAll(['only-for-tools-1.1.jar', 'constrained-2.0.jar', 'only-for-standalone-tools-1.2.jar'])
        }

        and: 'the scope of the standalone tools has the version of the platform, and nothing of grailsCli'
        STANDALONE_CLASSPATHS.every { String name ->
            paths[name].contains('only-for-standalone-tools-1.2.jar') && !paths[name].any { it.startsWith('only-for-tools') || it.startsWith('constrained') }
        }

        and: 'no class path of the tools has a dependency of the application: only its versions'
        CLASSPATHS_OF_TOOLS.every { String name -> !paths[name].any { it.startsWith('of-the-application') } }

        and: 'the application has its dependency, and nothing only tools have'
        paths.RUNTIME.contains('of-the-application-1.0.jar')
        !paths.RUNTIME.any { it.startsWith('only-for-') || it.startsWith('constrained') }

        where:
        declared                     | bom            | platform
        'a platform of its own'      | 'null'         | 'org.example:application-platform:1.0'
        'the Grails BOM'             | "'grails-bom'" | "org.apache.grails:grails-bom:${GRAILS_VERSION}"
    }

    def "the catalog of the tools of such an application is complete"() {
        given:
        setupCommandProject('''
            dependencies {
                implementation platform('org.example:application-platform:1.0')
                grailsCli 'org.example:only-for-tools'
                grailsCliStandalone 'org.example:only-for-standalone-tools'
            }
        ''')
        publishPlatform('org.example:application-platform:1.0')
        publish('org.example', 'only-for-tools', '1.1', [:])
        publish('org.example', 'only-for-standalone-tools', '1.2', [:])

        when:
        executeTask('grailsToolCatalog')
        Map catalog = new JsonSlurper().parse(new File(projectDir, '.grails/cache/tool-catalog.json')) as Map

        then:
        catalog.complete == true
        !catalog.diagnostics
    }

    // a POM-only module that manages the versions of the libraries of the features, as a BOM does
    private void publishPlatform(String coordinates) {
        List<String> parts = coordinates.tokenize(':')
        String group = parts[0]
        String artifact = parts[1]
        String version = parts[2]
        File directory = new File(projectDir, "maven-repo/${group.replace('.', '/')}/${artifact}/${version}")
        directory.mkdirs()
        String managed = [['of-the-application', '1.0'], ['only-for-tools', '1.1'], ['only-for-standalone-tools', '1.2']].collect { List<String> library ->
            "<dependency><groupId>org.example</groupId><artifactId>${library[0]}</artifactId><version>${library[1]}</version></dependency>\n"
        }.join('')
        new File(directory, "${artifact}-${version}.pom").text = '<?xml version="1.0" encoding="UTF-8"?>\n' +
                '<project xmlns="http://maven.apache.org/POM/4.0.0">\n' +
                "<modelVersion>4.0.0</modelVersion><groupId>${group}</groupId><artifactId>${artifact}</artifactId><version>${version}</version>\n" +
                '<packaging>pom</packaging>\n' +
                "<dependencyManagement><dependencies>\n${managed}</dependencies></dependencyManagement>\n" +
                '</project>\n'
    }

    private static Map<String, List<String>> paths(BuildResult result) {
        (CLASSPATHS_OF_TOOLS + ['RUNTIME']).collectEntries { String name ->
            String line = result.output.readLines().find { it.startsWith("${name}=") }
            [(name): line.substring(name.length() + 1).split(',').findAll().toList()]
        }
    }
}
