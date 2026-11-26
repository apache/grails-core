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

import groovy.json.JsonOutput

import org.gradle.testkit.runner.BuildResult

/**
 * {@code runTool} runs a tool of the project or of one of its plugins by its name, with the class path
 * of the application, of both source sets of tools and of {@code grailsCliClasspath}; {@code runCommand},
 * its deprecated other name, does the same after a warning. Both take the arguments of the tool in the
 * three ways: {@code --arg}, {@code --args-file} and {@code -Pargs}. The class that runs a tool is a
 * stand-in that says what it was started with.
 */
class RunToolTaskSpec extends CommandProjectSpecification {

    def "#task takes the arguments of the tool in each of the three ways#warned"() {
        given:
        setupCommandProject()
        projectFile('arguments.json', JsonOutput.toJson(['report', 'from a file', '']))

        when:
        BuildResult options = executeTask(task, ['--arg=report', '--arg=a b'])
        BuildResult file = executeTask(task, ['--args-file=arguments.json'])
        BuildResult property = executeTask(task, ['-Pargs=report "a b" c'])

        then:
        arguments(options) == ['report', 'a b', 'example.Application']
        arguments(file) == ['report', 'from a file', '', 'example.Application']
        arguments(property) == ['report', 'a b', 'c', 'example.Application']

        and:
        [options, file, property].every { BuildResult result -> result.output.contains('runCommand is deprecated: use runTool') == deprecated }

        where:
        task         | deprecated
        'runTool'    | false
        'runCommand' | true

        warned = deprecated ? ', after a line that says it is deprecated' : ''
    }

    def "#task runs with the classes of the application, of src/cli and of src/standalone, and with both scopes of tools"() {
        given:
        setupCommandProject('''
            dependencies {
                grailsCli 'org.example:only-for-tools:1.0'
                grailsCliStandalone 'org.example:only-for-standalone-tools:1.0'
            }
        ''')
        publish('org.example', 'only-for-tools', '1.0', [:])
        publish('org.example', 'only-for-standalone-tools', '1.0', [:])
        projectFile('src/cli/groovy/example/ReportTool.groovy', 'package example\n\nclass ReportTool {\n\n    Application application\n    FilesHelper helper\n}\n')
        projectFile('src/standalone/resources/templates/report.txt', 'a template')
        projectFile('src/standalone/java/example/FilesHelper.java', 'package example;\n\npublic class FilesHelper {\n}\n')

        when:
        BuildResult result = executeTask(task, ['--arg=report'])
        List<String> classpath = result.output.readLines().findAll { it.startsWith('CLASSPATH_ENTRY=') }.collect { it.substring('CLASSPATH_ENTRY='.length()) }
        List<String> inProject = inProject(classpath)

        then: 'the tool runs with the output of the three source sets'
        arguments(result) == ['report', 'example.Application']
        inProject.containsAll(['build/classes/groovy/main', 'build/classes/groovy/cli', 'build/classes/java/standalone', 'build/resources/standalone'])

        and: 'and with what tools depend on, of both kinds'
        classpath.collect { new File(it).name }.containsAll(['only-for-tools-1.0.jar', 'only-for-standalone-tools-1.0.jar', 'grails-core-cli-0.0.1-test.jar',
                                                            'grails-console-0.0.1-test.jar'])

        where:
        task << ['runTool', 'runCommand']
    }

    def "runTool and runCommand are tasks of the group of the tools, and runCommand says that it is deprecated"() {
        given:
        setupCommandProject()

        when:
        BuildResult result = executeTask('tasks', ['--group=Grails'])

        then:
        result.output.contains('runTool - Runs a tool of the project or of one of its plugins: the first argument is the name of the tool (--arg=<name>)')
        result.output.contains('runCommand - Deprecated: use runTool')
    }

    // the entries of a class path that are in the directory of the project, as paths in it
    private List<String> inProject(List<String> classpath) {
        List<String> roots = [projectDir.canonicalPath, projectDir.absolutePath].unique()
        classpath.collect { String entry ->
            String root = roots.find { String each -> entry.startsWith(each + File.separator) }
            root == null ? null : entry.substring(root.length() + 1).replace(File.separatorChar, '/' as char)
        }.findAll { it != null }
    }
}
