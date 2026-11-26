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
import groovy.json.JsonSlurper

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome

/**
 * The options plugins and the application declare for {@code run-app}, as {@code grailsToolCatalog}
 * lists them in the catalog of the commands of the project. No jar of these projects has a class in
 * it: the declarations are read, and nothing of a plugin is run.
 */
class CommandOptionCatalogSpec extends CommandProjectSpecification {

    private static final String CATALOG = '.grails/cache/tool-catalog.json'
    private static final String OPTIONS = 'META-INF/grails-command-options.json'
    private static final Map TAIL = [command: 'run-app', name: 'query-tail', description: 'Capture every statement GORM runs.', property: 'grails.gorm.queryTail.enabled']

    def "the catalog lists the options of a plugin jar, of a companion and of the application, and says what it does not use"() {
        given:
        setupCommandProject('''
            dependencies {
                implementation 'org.example:data:1.0'
                implementation 'org.example:tenants:1.0'
                grailsCli 'org.example:data-cli:1.0'
                grailsCli 'org.example:other-cli:1.0'
                grailsCli 'org.hostile:options-cli:1.0'
            }
        ''')
        publish('org.example', 'data', '1.0', [:])
        publish('org.example', 'data-cli', '1.0', [(OPTIONS): options([TAIL])])
        publish('org.example', 'tenants', '1.0', [(OPTIONS): options([
                [command: 'run-app', name: 'tenant', description: 'The tenant the application runs as.', type: 'string', property: 'demo.tenant'],
                [command: 'run-app', name: 'clash', description: 'Of the tenants.', property: 'demo.tenants.clash']])])
        publish('org.example', 'other-cli', '1.0', [(OPTIONS): options([[command: 'run-app', name: 'clash', description: 'Of the other.', property: 'demo.other.clash']])])
        publish('org.hostile', 'options-cli', '1.0', [(OPTIONS): options([
                [command: 'run-app', name: 'agent', description: 'Attaches something.', property: 'java.agent.path'],
                [command: 'run-app', name: 'profile', description: 'Another profile.', property: 'spring.profiles.active', value: 'production'],
                [command: 'run-app', name: 'more -Dx=y', description: 'More than a name.', property: 'demo.more'],
                [command: 'stop-app', name: 'force', description: 'Of another command.', property: 'demo.force']])])
        projectFile("src/main/resources/${OPTIONS}", options([[command: 'run-app', name: 'trace', description: 'Log every request.', property: 'logging.level.web', value: 'TRACE']]))

        when:
        BuildResult result = executeTask('grailsToolCatalog')
        Map catalog = readCatalog()

        then:
        result.task(':grailsToolCatalog').outcome == TaskOutcome.SUCCESS
        catalog.commandOptions == [
                [command: 'run-app', name: 'query-tail', description: 'Capture every statement GORM runs.', type: 'boolean', property: 'grails.gorm.queryTail.enabled',
                 value: 'true', origin: 'org.example:data-cli:1.0'],
                [command: 'run-app', name: 'tenant', description: 'The tenant the application runs as.', type: 'string', property: 'demo.tenant', origin: 'org.example:tenants:1.0'],
                [command: 'run-app', name: 'trace', description: 'Log every request.', type: 'boolean', property: 'logging.level.web', value: 'TRACE', origin: 'application'],
        ]

        and: 'what is not used is said in the catalog and by the task, with nothing of a declaration that is refused'
        catalog.diagnostics.size() == 5
        catalog.diagnostics.count { String line -> line.startsWith('Option ') && line.contains('org.hostile:options-cli:1.0') } == 4
        catalog.diagnostics.contains('The option run-app --clash is not used because more than one declares it, and not the same: org.example:other-cli:1.0, org.example:tenants:1.0.')
        !catalogText().contains('java.agent')
        !catalogText().contains('spring.profiles')
        !catalogText().contains('-Dx=y')
        result.output.contains('The option run-app --clash is not used because more than one declares it')

        and: 'what is said of options leaves what the catalog says of the commands as it is'
        catalog.complete == true
        catalog.tools*.name == ['config-report']

        when: 'nothing changed'
        BuildResult again = executeTask('grailsToolCatalog')

        then:
        again.task(':grailsToolCatalog').outcome == TaskOutcome.UP_TO_DATE

        when: 'the application declares the name two plugins have'
        projectFile("src/main/resources/${OPTIONS}", options([[command: 'run-app', name: 'clash', description: 'Of the application.', property: 'demo.own.clash']]))
        BuildResult changed = executeTask('grailsToolCatalog')
        catalog = readCatalog()

        then: 'the catalog follows the resource of the application, and what the application declares holds'
        changed.task(':grailsToolCatalog').outcome == TaskOutcome.SUCCESS
        catalog.commandOptions*.name == ['clash', 'query-tail', 'tenant']
        catalog.commandOptions[0] == [command: 'run-app', name: 'clash', description: 'Of the application.', type: 'boolean', property: 'demo.own.clash', value: 'true', origin: 'application']
        catalog.diagnostics.contains('The option run-app --clash is as the application declares it: what org.example:other-cli:1.0, org.example:tenants:1.0 declares for that name is not used.')
    }

    def "a project without a declared option has a catalog without the member, and one in grails-app/conf is the application's"() {
        given:
        setupCommandProject()

        when:
        executeTask('grailsToolCatalog')

        then:
        !readCatalog().containsKey('commandOptions')

        when:
        projectFile("grails-app/conf/${OPTIONS}", options([TAIL]))
        executeTask('grailsToolCatalog')

        then:
        readCatalog().commandOptions*.origin == ['application']
        readCatalog().commandOptions*.name == ['query-tail']
    }

    private static String options(List<Map> declared) {
        JsonOutput.toJson([format: 1, options: declared])
    }

    private String catalogText() {
        new File(projectDir, CATALOG).getText('US-ASCII')
    }

    private Map readCatalog() {
        (Map) new JsonSlurper().parseText(catalogText())
    }
}
