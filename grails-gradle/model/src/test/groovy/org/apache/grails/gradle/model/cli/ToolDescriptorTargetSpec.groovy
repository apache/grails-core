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
class ToolDescriptorTargetSpec extends Specification {

    private static final String CLASS = 'demo.TailCommand'

    @TempDir
    File dir

    @Unroll
    def "a descriptor written for #title is read as it was written"() {
        when:
        String json = ToolDescriptor.describe(CLASS, 'tail', 'Follows a file', target, startsApplication, [])
        ToolDescriptor.Described read = ToolDescriptor.read(CLASS, json.getBytes('US-ASCII'))

        then:
        json == '{"format":1,"class":"demo.TailCommand","name":"tail","description":"Follows a file",' + members + '}\n'
        read.name == 'tail'
        read.description == 'Follows a file'
        read.target == target
        read.startsApplication == starts
        read.deprecated == []

        where:
        title                                                 | target        | startsApplication || members                                             | starts
        'a tool that runs in the application'                 | 'application' | true              || '"target":"application","startsApplication":true'  | true
        'a tool the build runs without the application'       | 'application' | false             || '"target":"application","startsApplication":false' | false
        'a tool that runs as a process of its own'            | 'standalone'  | false             || '"target":"standalone"'                            | false
        'such a tool, which never starts the application'     | 'standalone'  | true              || '"target":"standalone"'                            | false
    }

    def "a descriptor that names no target, and one written without one, is of a command that runs with the application"() {
        expect:
        ToolDescriptor.read(CLASS, '{"format":1,"class":"demo.TailCommand","name":"tail"}'.getBytes('US-ASCII')).target == 'application'
        ToolDescriptor.read(CLASS, '{"format":1,"class":"demo.TailCommand","name":"tail"}'.getBytes('US-ASCII')).startsApplication
        ToolDescriptor.describe(CLASS, 'tail', null) == ToolDescriptor.describe(CLASS, 'tail', null, 'application', true, null)
        ToolDescriptor.toJson(CLASS, 'tail', null) == ToolDescriptor.toJson(CLASS, 'tail', null, 'application', true, [])
    }

    @Unroll
    def "no descriptor is written for the target #target, which no build runs"() {
        expect:
        ToolDescriptor.describe(CLASS, 'tail', 'Follows a file', target, true, []) == null

        where:
        target << ['console', 'session', 'gradle', 'nowhere', null]
    }

    @Unroll
    def "a descriptor for the target #target is refused: #reason"() {
        when:
        ToolDescriptor.read(CLASS, ('{"format":1,"class":"demo.TailCommand","name":"tail","target":' + target + '}').getBytes('US-ASCII'))

        then:
        ToolDescriptor.Refused refused = thrown()
        refused.message.startsWith(reason)

        where:
        target           | reason
        '"console"'      | 'is for the execution target console, which this version of Grails cannot run'
        '"session"'      | 'is for the execution target session, which this version of Grails cannot run'
        '"nowhere"'      | 'has an execution target that does not exist'
        '"gradle"'       | 'has an execution target that does not exist'
        '7'              | 'has an execution target that does not exist'
        '["gradle"]'     | 'has an execution target that does not exist'
    }

    def "the catalog of a build lists each tool with the target its descriptor names, whether it starts the application and what is deprecated about it"() {
        given:
        File jar = new File(dir, 'element')
        new File(jar, 'META-INF').mkdirs()
        new File(jar, 'META-INF/grails-cli.factories').text =
                'org.apache.grails.core.cli.GrailsTool=demo.TailCommand,demo.ReportCommand,demo.RenderCommand\n' +
                'org.apache.grails.core.cli.ApplicationCommand=demo.ReportCommand,demo.PlainCommand\n'
        descriptor(jar, 'demo.TailCommand', ToolDescriptor.describe('demo.TailCommand', 'tail', 'Follows a file', 'application', false, ['type']))
        descriptor(jar, 'demo.RenderCommand', ToolDescriptor.describe('demo.RenderCommand', 'render', 'Renders a file', 'standalone', false, ['folder', 'type']))
        descriptor(jar, 'demo.ReportCommand', ToolDescriptor.describe('demo.ReportCommand', 'report', 'Writes it'))

        when:
        ToolCatalogBuilder builder = new ToolCatalogBuilder()
        builder.add('org.example:element:1.0', jar)
        Map<String, Object> catalog = builder.build()

        then: 'a command without a descriptor is listed as one that runs with the application'
        catalog.tools == [
                [name: 'plain', aliases: [], description: '', origin: 'org.example:element:1.0', target: 'application', startsApplication: true, registration: 'factories'],
                [name: 'render', aliases: [], description: 'Renders a file', origin: 'org.example:element:1.0', target: 'standalone', startsApplication: false, registration: 'descriptor', deprecated: ['type', 'folder']],
                [name: 'report', aliases: [], description: 'Writes it', origin: 'org.example:element:1.0', target: 'application', startsApplication: true, registration: 'descriptor'],
                [name: 'tail', aliases: [], description: 'Follows a file', origin: 'org.example:element:1.0', target: 'application', startsApplication: false, registration: 'descriptor', deprecated: ['type']],
        ]
        catalog.complete == true
        builder.toJson().contains('"name":"tail","aliases":[],"description":"Follows a file","origin":"org.example:element:1.0","target":"application","startsApplication":false,"registration":"descriptor","deprecated":["type"]}')
    }

    private static void descriptor(File element, String className, String content) {
        File file = new File(element, ToolDescriptor.location(className))
        file.parentFile.mkdirs()
        file.text = content
    }
}
