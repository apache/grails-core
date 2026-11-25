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
package org.apache.grails.gradle.model.cli

import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

/**
 * The execution target a descriptor names: a command that runs with the application, or one that
 * runs without it, and how a build lists each.
 */
class CommandDescriptorTargetSpec extends Specification {

    private static final String CLASS = 'demo.TailCommand'

    @TempDir
    File dir

    @Unroll
    def "a descriptor written for the target #target is read with it"() {
        when:
        String json = CommandDescriptor.describe(CLASS, 'tail', 'Follows a file', target)
        CommandDescriptor.Described read = CommandDescriptor.read(CLASS, json.getBytes('US-ASCII'))

        then:
        json == '{"format":1,"class":"demo.TailCommand","name":"tail","description":"Follows a file","target":"' + target + '"}\n'
        read.name == 'tail'
        read.description == 'Follows a file'
        read.target == target

        where:
        target << ['application', 'gradle']
    }

    def "a descriptor that names no target, and one written without one, is of a command that runs with the application"() {
        expect:
        CommandDescriptor.read(CLASS, '{"format":1,"class":"demo.TailCommand","name":"tail"}'.getBytes('US-ASCII')).target == 'application'
        CommandDescriptor.describe(CLASS, 'tail', null) == CommandDescriptor.describe(CLASS, 'tail', null, 'application')
        CommandDescriptor.toJson(CLASS, 'tail', null) == CommandDescriptor.toJson(CLASS, 'tail', null, 'application')
    }

    @Unroll
    def "no descriptor is written for the target #target, which no build runs"() {
        expect:
        CommandDescriptor.describe(CLASS, 'tail', 'Follows a file', target) == null

        where:
        target << ['console', 'standalone', 'session', 'nowhere', null]
    }

    @Unroll
    def "a descriptor for the target #target is refused: #reason"() {
        when:
        CommandDescriptor.read(CLASS, ('{"format":1,"class":"demo.TailCommand","name":"tail","target":' + target + '}').getBytes('US-ASCII'))

        then:
        CommandDescriptor.Refused refused = thrown()
        refused.message.startsWith(reason)

        where:
        target           | reason
        '"console"'      | 'is for the execution target console, which this version of Grails cannot run'
        '"standalone"'   | 'is for the execution target standalone, which this version of Grails cannot run'
        '"session"'      | 'is for the execution target session, which this version of Grails cannot run'
        '"nowhere"'      | 'has an execution target that does not exist'
        '7'              | 'has an execution target that does not exist'
        '["gradle"]'     | 'has an execution target that does not exist'
    }

    def "the catalog of a build lists each command with the target its descriptor names"() {
        given:
        File jar = new File(dir, 'element')
        new File(jar, 'META-INF').mkdirs()
        new File(jar, 'META-INF/grails-cli.factories').text =
                'org.apache.grails.core.cli.ApplicationCommand=demo.TailCommand,demo.ReportCommand,demo.PlainCommand\n'
        descriptor(jar, 'demo.TailCommand', CommandDescriptor.describe('demo.TailCommand', 'tail', 'Follows a file', 'gradle'))
        descriptor(jar, 'demo.ReportCommand', CommandDescriptor.describe('demo.ReportCommand', 'report', 'Writes it'))

        when:
        CommandCatalogBuilder builder = new CommandCatalogBuilder()
        builder.add('org.example:element:1.0', jar)
        Map<String, Object> catalog = builder.build()

        then: 'a command without a descriptor is listed as one that runs with the application'
        catalog.commands == [
                [name: 'plain', aliases: [], description: '', origin: 'org.example:element:1.0', target: 'application', registration: 'factories'],
                [name: 'report', aliases: [], description: 'Writes it', origin: 'org.example:element:1.0', target: 'application', registration: 'descriptor'],
                [name: 'tail', aliases: [], description: 'Follows a file', origin: 'org.example:element:1.0', target: 'gradle', registration: 'descriptor'],
        ]
        catalog.complete == true
        builder.toJson().contains('"name":"tail","aliases":[],"description":"Follows a file","origin":"org.example:element:1.0","target":"gradle"')
    }

    private static void descriptor(File element, String className, String content) {
        File file = new File(element, CommandDescriptor.location(className))
        file.parentFile.mkdirs()
        file.text = content
    }
}
