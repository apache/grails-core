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
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
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
        problem                        | content      || message
        'does not exist'               | null         || 'does not exist'
        'is not JSON'                  | 'not json'   || 'is not JSON'
        'is not an array of strings'   | '["a", 1]'   || 'must hold a JSON array of strings'
        'is an object'                 | '{"a": "b"}' || 'must hold a JSON array of strings'
    }

    private Project project(Map<String, String> properties) {
        Project project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        properties.each { String name, String value -> project.extensions.extraProperties.set(name, value) }
        project
    }
}
