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

import org.gradle.testkit.runner.BuildResult

import org.apache.grails.gradle.model.cli.ToolDescriptor

/**
 * A tool of a companion of a plugin gets a Gradle task of its own, named after the name the tool has by
 * the name of its class: the suffix {@code Tool} is taken off, or the suffix {@code Command} when the
 * class does not end with {@code Tool}, so that a class renamed from {@code DbmUpdateCommand} to
 * {@code DbmUpdateTool} keeps its task {@code dbmUpdate} and its name {@code dbm-update}.
 */
class ToolTaskNamesSpec extends CommandProjectSpecification {

    def "a tool registered by a companion has a task named after the name of the tool, whichever suffix its class has"() {
        given: 'a companion that registers tools under the key of the tool contract and under the key of Grails 8'
        setupCommandProject('''
            dependencies {
                grailsCli 'org.example:tools-cli:1.0'
            }
        ''')
        publish('org.example', 'tools-cli', '1.0', [(CLI_FACTORIES):
                "${ToolDescriptor.FACTORIES_KEY_TOOL}=com.example.DbmUpdateTool,com.example.ToolTool,com.example.UrlMappingsReportTool\n" +
                "${COMMAND_KEY}=com.example.HelloWorldCommand,com.example.S2QuickstartCommand\n"])

        when:
        BuildResult update = executeTask('dbmUpdate', ['--arg=changelog.groovy'])
        BuildResult tool = executeTask('tool')
        BuildResult hello = executeTask('helloWorld')
        BuildResult quickstart = executeTask('s2Quickstart', ['-Pargs=com.example User Role'])

        then: 'each task runs its tool by the name the tool has'
        arguments(update) == ['dbm-update', 'changelog.groovy', 'example.Application']
        arguments(tool) == ['tool', 'example.Application']
        arguments(hello) == ['hello-world', 'example.Application']
        arguments(quickstart) == ['s2-quickstart', 'com.example', 'User', 'Role', 'example.Application']

        when:
        BuildResult tasks = executeTask('tasks', ['--group=Grails'])

        then: 'no task carries the suffix of the class'
        List<String> names = tasks.output.readLines().findAll { String line -> line.contains(' - ') }.collect { String line -> line.substring(0, line.indexOf(' - ')) }
        names.containsAll(['dbmUpdate', 'tool', 'urlMappingsReport', 'helloWorld', 's2Quickstart'])
        names.intersect(['dbmUpdateTool', 'toolTool', 'urlMappingsReportTool', 'helloWorldCommand', 's2QuickstartCommand']).isEmpty()

        and: 'the description names the tool'
        tasks.output.contains('dbmUpdate - Runs the tool dbm-update')
    }
}
