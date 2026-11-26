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

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties
import spock.lang.TempDir

class CommandArgumentsSpec extends Specification {

    @TempDir
    File projectDir

    void 'without arguments a command gets none'() {
        expect:
        GrailsCliGradlePlugin.commandArguments(project([:])) == []
    }

    void 'the args property is split as a shell would split it'() {
        expect:
        GrailsCliGradlePlugin.commandArguments(project(args: 'url-mappings-report "two words" \'say "hi"\'')) ==
                ['url-mappings-report', 'two words', 'say "hi"']
    }

    void 'the arguments of the args file are passed on unchanged, after those of the args property'() {
        given: 'arguments a command line could not carry without quoting'
        List<String> arguments = ['two words', 'it\'s "x" y', '', '%PATH% & | <>', '--format', 'json']
        new File(projectDir, 'arguments.json').text = JsonOutput.toJson(arguments)

        expect:
        GrailsCliGradlePlugin.commandArguments(project(args: 'url-mappings-report', argsFile: 'arguments.json')) ==
                ['url-mappings-report'] + arguments
    }

    void 'an args file that #problem is reported'() {
        given:
        if (content != null) {
            new File(projectDir, 'arguments.json').text = content
        }

        when:
        GrailsCliGradlePlugin.commandArguments(project(argsFile: 'arguments.json'))

        then:
        GradleException e = thrown()
        e.message.contains(message)

        where:
        problem                        | content          || message
        'does not exist'               | null             || 'does not exist'
        'is not JSON'                  | 'not json'       || 'is not JSON'
        'is empty'                     | ''               || 'is not JSON: it is empty'
        'is blank'                     | ' \n '           || 'is not JSON: it is empty'
        'has text after its array'     | '["a"] trailing' || 'is not JSON: text follows the end of its array'
        'holds a second array'         | '["a"] ["b"]'    || 'is not JSON: text follows the end of its array'
        'is not an array of strings'   | '["a", 1]'       || 'must hold a JSON array of strings'
        'is an object'                 | '{"a": "b"}'     || 'must hold a JSON array of strings'
    }

    void 'brackets and quotes inside the strings of an args file do not end its array'() {
        given:
        List<String> arguments = [']', '[', '"]', '\\', '\\"', '} {']
        new File(projectDir, 'arguments.json').text = JsonOutput.toJson(arguments) + '\n'

        expect:
        GrailsCliGradlePlugin.commandArguments(project(argsFile: 'arguments.json')) == arguments
    }

    void 'an args file given by its absolute path, as the Grails Forge CLI gives it, is read'() {
        given:
        File elsewhere = new File(projectDir, 'elsewhere/arguments.json')
        elsewhere.parentFile.mkdirs()
        elsewhere.text = JsonOutput.toJson(['greet', 'Alice'])

        expect:
        GrailsCliGradlePlugin.commandArguments(project(argsFile: elsewhere.absolutePath)) == ['greet', 'Alice']
    }

    void 'the system properties among the arguments are set on the command JVM when it runs, and stay among the arguments'() {
        given: 'arguments with properties in every form the command line parser reads'
        List<String> given = ['dbm-update', '-Dgrails.env=test', '-Dquery=a=b', ' -Dtrimmed=yes ', '"-Dquoted=yes"',
                              '-D', '-Dnovalue', 'first']
        new File(projectDir, 'arguments.json').text = JsonOutput.toJson(given)
        Project project = project(argsFile: 'arguments.json')
        JavaExec task = project.tasks.register('commandTask', JavaExec).get()
        task.systemProperty('grails.env', 'development')

        when:
        List<Object> arguments = GrailsCliGradlePlugin.commandTaskArguments(task, project)

        then: 'the file is handed to the command by its path, so its arguments are not on the command line of the JVM'
        arguments == []
        task.systemProperties[GrailsCliGradlePlugin.ARGS_FILE_SYSTEM_PROPERTY] == project.file('arguments.json').absolutePath

        and: 'nothing else is set while the build is configured, where a setting for every JavaExec task could replace it'
        task.systemProperties.findAll { it.key != GrailsCliGradlePlugin.ARGS_FILE_SYSTEM_PROPERTY } == ['grails.env': 'development']

        when: 'the task starts'
        task.actions.first().execute(task)

        then:
        task.systemProperties.findAll { it.key != GrailsCliGradlePlugin.ARGS_FILE_SYSTEM_PROPERTY } ==
                ['grails.env': 'test', query: 'a=b', trimmed: 'yes', quoted: 'yes']
        task.onlyIf.isSatisfiedBy(task)
    }

    void 'the arguments of the args property are passed as they are, before the args file'() {
        given:
        new File(projectDir, 'arguments.json').text = JsonOutput.toJson(['--password', 'secret'])
        Project project = project(args: 'dbm-update --verbose', argsFile: 'arguments.json')
        JavaExec task = project.tasks.register('commandTask', JavaExec).get()

        expect:
        GrailsCliGradlePlugin.commandTaskArguments(task, project) == ['dbm-update', '--verbose']
        task.systemProperties[GrailsCliGradlePlugin.ARGS_FILE_SYSTEM_PROPERTY] == project.file('arguments.json').absolutePath
    }

    void 'an args file that is not JSON is reported without what it holds, which can be a password'() {
        given:
        new File(projectDir, 'arguments.json').text = '["dbm-update", "--password", "s3cr3t" oops]'

        when:
        GrailsCliGradlePlugin.commandArguments(project(argsFile: 'arguments.json'))

        then:
        GradleException e = thrown()
        e.message.contains('is not JSON')
        !e.message.contains('s3cr3t')
        e.cause == null
    }

    void 'a command task of a project the grailsCommandProject property does not name does not read the args file'() {
        given: 'an args file that is not in this project, as one relative to another project of the build'
        Project project = project(grailsCommandProject: new File(projectDir, 'other').absolutePath, argsFile: 'arguments.json')
        JavaExec task = project.tasks.register('commandTask', JavaExec).get()

        when:
        List<Object> arguments = GrailsCliGradlePlugin.commandTaskArguments(task, project)

        then:
        noExceptionThrown()
        arguments == []
        !task.onlyIf.isSatisfiedBy(task)
        !task.systemProperties.containsKey(GrailsCliGradlePlugin.ARGS_FILE_SYSTEM_PROPERTY)
    }

    void 'arguments the command line parser refuses set no system property'() {
        given:
        new File(projectDir, 'arguments.json').text = JsonOutput.toJson(['report', '-Dgrails.env=test', '--dev mode'])
        Project project = project(argsFile: 'arguments.json')
        JavaExec task = project.tasks.register('commandTask', JavaExec).get()

        int actions = task.actions.size()

        when:
        GrailsCliGradlePlugin.commandTaskArguments(task, project)

        then: 'no action is added to set any'
        task.actions.size() == actions
    }

    @RestoreSystemProperties
    void 'configuring a command task with #given leaves the environment of the build alone'() {
        given:
        System.setProperty('grails.env', 'development')
        new File(projectDir, 'arguments.json').text = JsonOutput.toJson(given)
        Project project = project(argsFile: 'arguments.json')
        JavaExec task = project.tasks.register('commandTask', JavaExec).get()

        when:
        GrailsCliGradlePlugin.commandTaskArguments(task, project)
        task.actions.first().execute(task)

        then: 'the JVM that configures the build keeps its environment'
        System.getProperty('grails.env') == 'development'

        and: 'the command JVM gets the one it names'
        task.systemProperties.findAll { it.key != GrailsCliGradlePlugin.ARGS_FILE_SYSTEM_PROPERTY } == properties

        where:
        given                                   || properties
        ['dbm-update', '-Dgrails.env=test']     || ['grails.env': 'test']
        ['test', 'dbm-update', '-Dother=value'] || [other: 'value']
    }

    void 'a command task of a project the grailsCommandProject property does not name depends on nothing'() {
        given:
        Project project = project(grailsCommandProject: new File(projectDir, 'other').absolutePath)
        JavaExec task = project.tasks.register('commandTask', JavaExec).get()
        task.dependsOn('classes')
        task.classpath = project.files('classes')

        when:
        GrailsCliGradlePlugin.commandTaskArguments(task, project)

        then: 'nothing is compiled or resolved for it'
        task.dependsOn.empty
        task.classpath.empty
        !task.onlyIf.isSatisfiedBy(task)
    }

    void 'a relative grailsCommandProject is the directory below the root of the build'() {
        given:
        Project root = ProjectBuilder.builder().withProjectDir(projectDir).build()
        File appDir = new File(projectDir, 'apps/app')
        appDir.mkdirs()
        Project app = ProjectBuilder.builder().withName('app').withProjectDir(appDir).withParent(root).build()
        app.extensions.extraProperties.set('grailsCommandProject', named)
        JavaExec task = app.tasks.register('commandTask', JavaExec).get()

        when:
        GrailsCliGradlePlugin.commandTaskArguments(task, app)

        then:
        task.onlyIf.isSatisfiedBy(task) == runs

        where:
        named        || runs
        'apps/app'   || true
        'apps/other' || false
        'app'        || false
    }

    void 'a command task runs only in the project the grailsCommandProject property names: #named'() {
        given:
        Project project = project(grailsCommandProject: new File(projectDir, named).absolutePath)
        JavaExec task = project.tasks.register('commandTask', JavaExec).get()

        when:
        GrailsCliGradlePlugin.commandTaskArguments(task, project)

        then:
        task.onlyIf.isSatisfiedBy(task) == runs

        where:
        named   || runs
        ''      || true
        'other' || false
    }

    private Project project(Map<String, String> properties) {
        Project project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        properties.each { String name, String value -> project.extensions.extraProperties.set(name, value) }
        project
    }
}
