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
package org.grails.forge.cli.command

import org.grails.forge.cli.CliRunner
import spock.lang.Unroll

/**
 * The feature {@code grails-publish} through the command line: selected when a plugin is created, added
 * to a plugin that is there, and refused for an application that is no plugin.
 */
class FeatureAddGrailsPublishSpec extends AbstractFeatureAddCommandSpec {

    static final List<String> INSTRUCTIONS = [
            '| The feature grails-publish says what to do next:',
            '    Fill in the TODO values of the grailsPublish block of build.gradle before you publish the plugin.',
            '    The version of gradle.properties is a release, and publishing a release needs a signing key:',
            '    give the plugin a version that ends with -SNAPSHOT while you develop it.',
            '    README.md says how a snapshot and a release are published.',
    ]

    @Unroll
    void "grails-publish is added to what #command.simpleName made as if it had been created with it, and says what to do next"() {
        given:
        create(dir, command, [])
        create(with, command, ['--features', 'grails-publish'])

        when:
        int exit = add('grails-publish')
        List<String> lines = out.toString().readLines()

        then:
        exit == 0
        err.toString().isEmpty()
        out.toString().contains('Feature grails-publish added. Commit the changes together with the directory .grails')

        and: 'every file is the file of the plugin that was created with the feature'
        tree(dir) == tree(with)
        new File(dir, 'build.gradle').text.contains('apply plugin: "org.apache.grails.gradle.grails-publish"')
        new File(dir, 'build.gradle').text.contains('grailsPublish {')
        new File(dir, 'README.md').text.contains('## Publishing the plugin')

        and: 'the instructions are the last thing the command says'
        lines.takeRight(INSTRUCTIONS.size()) == INSTRUCTIONS

        where:
        command << [CreatePluginCommand, CreateWebPluginCommand, CreateRestApiPluginCommand]
    }

    void "create-plugin with grails-publish shows the instructions of the feature after everything else it says"() {
        when:
        int exit = createSaying(CreatePluginCommand)
        List<String> lines = out.toString().readLines()

        then:
        exit == 0
        lines.any { it.startsWith('| Application created at ') }
        lines.takeRight(INSTRUCTIONS.size()) == INSTRUCTIONS
    }

    @Unroll
    void "#command.simpleName refuses grails-publish, which is a feature of plugins, and writes nothing"() {
        when:
        int exit = createSaying(command)

        then:
        exit == 2
        err.toString().contains('The requested feature does not exist: grails-publish')
        !new File(dir, 'build.gradle').exists()

        where:
        command << [CreateAppCommand, CreateRestApiCommand]
    }

    @Unroll
    void "feature add refuses grails-publish in what #command.simpleName made and changes nothing"() {
        given:
        create(dir, command, [])
        Map<String, String> before = tree(dir)

        when:
        int exit = add('grails-publish')

        then:
        exit == 2
        (out.toString() + err.toString()).contains('The requested feature does not exist: grails-publish')
        tree(dir) == before

        where:
        command << [CreateAppCommand, CreateRestApiCommand]
    }

    // a create command with the feature in the directory of the spec, whose output is kept
    private int createSaying(Class command) {
        return CliRunner.run(command, beanContext, ['com.example.shop', '--inplace', '--features', 'grails-publish'] as String[])
    }
}
