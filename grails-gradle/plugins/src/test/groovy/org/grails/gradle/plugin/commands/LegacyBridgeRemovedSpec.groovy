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
 * The bridge that ran the commands of Grails 7 is gone. A build that still asks for it fails while it is
 * configured, with a line that says so, rather than going on without the commands it expects; a plugin
 * jar compiled for Grails 7 has none of its commands listed, and the catalog and the build name it.
 */
class LegacyBridgeRemovedSpec extends CommandProjectSpecification {

    private static final String REMOVED = 'legacyCommandSupport was removed in Grails 9: commands of Grails 7 are not run; see the upgrade notes'

    def "a build that sets grails { legacyCommandSupport = true } fails with the reason"() {
        given:
        setupCommandProject('''
            grails {
                legacyCommandSupport = true
            }
        ''')

        when:
        BuildResult result = runner.withArguments('help').buildAndFail()

        then:
        result.output.contains(REMOVED)
    }

    def "a build that asks for the bridge with the project property grailsLegacyCommandSupport=#value #outcome"() {
        given:
        setupCommandProject()

        when:
        BuildResult result = value == 'true' ?
                runner.withArguments('help', "-PgrailsLegacyCommandSupport=${value}").buildAndFail() :
                runner.withArguments('help', "-PgrailsLegacyCommandSupport=${value}").build()

        then:
        result.output.contains(REMOVED) == (value == 'true')

        where:
        value   | outcome
        'true'  | 'fails with the reason'
        'false' | 'is built as it is'
    }

    def "a build that asks for the bridge in gradle.properties fails with the reason"() {
        given:
        setupCommandProject()
        new File(projectDir, 'gradle.properties') << '\ngrailsLegacyCommandSupport=true\n'

        when:
        BuildResult result = runner.withArguments('help').buildAndFail()

        then:
        result.output.contains(REMOVED)
    }

    def "grails { legacyCommandSupport = false }, which is what Grails 9 does, is taken as it is"() {
        given:
        setupCommandProject('''
            grails {
                legacyCommandSupport = false
            }
        ''')

        expect:
        executeTask('help')
    }

    def "a build that declares a dependency of the bridge's configuration grailsCliLegacy fails while it is configured"() {
        given:
        setupCommandProject('''
            dependencies {
                grailsCliLegacy 'org.apache.grails:grails-core-cli-legacy:0.0.1-test'
            }
        ''')

        when:
        BuildResult result = runner.withArguments('help').buildAndFail()

        then:
        result.output.contains('grailsCliLegacy')
    }

    def "a plugin jar compiled for Grails 7 has none of its commands listed, also beside the jar of the bridge, and the catalog and the build name it"() {
        given: 'a plugin of Grails 7, and the jar of the bridge as a project of Grails 8 had it'
        setupCommandProject('''
            dependencies {
                implementation 'org.example:old-plugin:7.0.0'
                grailsCli 'org.apache.grails:grails-core-cli-legacy:0.0.1-test'
            }
        ''')
        publish('org.example', 'old-plugin', '7.0.0', [
                'META-INF/grails.factories': 'grails.dev.commands.ApplicationCommand=org.example.legacy.HelloLegacyAppCommand\n',
        ])
        publish('org.apache.grails', 'grails-core-cli-legacy', GRAILS_VERSION, [
                (CLI_FACTORIES): 'org.apache.grails.core.cli.ApplicationCommandProvider=org.apache.grails.core.cli.compat.LegacyApplicationCommandProvider\n',
                'org/apache/grails/core/cli/compat/LegacyApplicationCommandProvider.class': 'not a class',
        ])

        when:
        BuildResult result = executeTask('grailsToolCatalog')
        Map catalog = (Map) new JsonSlurper().parse(new File(projectDir, '.grails/cache/tool-catalog.json'))

        then: 'the commands of the plugin are not listed, and nothing is registered as legacy'
        catalog.tools*.name == ['config-report']
        !catalog.tools.any { it.registration == 'legacy' }

        and: 'the catalog says why, and is not complete'
        catalog.complete == false
        catalog.diagnostics == ['org.example:old-plugin:7.0.0 has commands of Grails 7, which Grails 9 does not run: use a version of the plugin for Grails 9.']

        and: 'the build says it as a warning'
        result.output.contains('org.example:old-plugin:7.0.0 has commands of Grails 7, which Grails 9 does not run: use a version of the plugin for Grails 9.')
    }
}
