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
import org.gradle.testkit.runner.TaskOutcome

/**
 * The test phases of an application in the catalog {@code grailsCommandCatalog} writes, from which
 * {@code grails test-app} knows the switches of the phases: the {@code Test} tasks of the project,
 * by the names they are selected with.
 */
class TestPhaseCatalogSpec extends CommandProjectSpecification {

    private static final String CATALOG = '.grails/cache/command-catalog.json'

    def "the catalog lists the phases of the project in their order, each with its task, and no task whose name cannot be a switch"() {
        given:
        setupCommandProject('''
            testPhases {
                create('functionalTest')
                create('endToEndTest')
            }
            // tasks that are no phase of the Grails Gradle plugin: a name without the ending, two whose
            // phase another task is, and one that cannot be typed as a switch
            tasks.register('smoke', Test)
            tasks.register('functional', Test)
            tasks.register('unitTest', Test)
            tasks.register('api_check', Test)
            tasks.configureEach {
                if (it.name in ['functionalTest', 'endToEndTest', 'smoke', 'functional', 'unitTest', 'api_check']) {
                    throw new GradleException("The task ${it.name} was created to list the phases")
                }
            }
        ''')

        when:
        BuildResult result = executeTask('grailsCommandCatalog')

        then:
        result.task(':grailsCommandCatalog').outcome == TaskOutcome.SUCCESS
        catalog().testPhases == [
                [name: 'unit', task: 'test'],
                [name: 'integration', task: 'integrationTest'],
                [name: 'end-to-end', task: 'endToEndTest'],
                [name: 'functional', task: 'functionalTest'],
                [name: 'smoke', task: 'smoke'],
                // not the phase unit, which the task test is: the phase of its own name
                [name: 'unit-test', task: 'unitTest'],
        ]
    }

    def "a phase that is added to the build is in the catalog the next time it is written, with the stored configuration where nothing else changed"() {
        given:
        setupCommandProject('''
            if (providers.gradleProperty('functional').present) {
                testPhases {
                    create('functionalTest')
                }
            }
        ''')

        when:
        executeTask('grailsCommandCatalog')

        then:
        catalog().testPhases*.name == ['unit', 'integration']

        when:
        BuildResult result = executeTask('grailsCommandCatalog', ['-Pfunctional=true'])

        then:
        result.task(':grailsCommandCatalog').outcome == TaskOutcome.SUCCESS
        catalog().testPhases*.name == ['unit', 'integration', 'functional']

        when:
        result = executeTask('grailsCommandCatalog', ['-Pfunctional=true'])

        then:
        result.output.contains('Configuration cache entry reused')
        result.task(':grailsCommandCatalog').outcome == TaskOutcome.UP_TO_DATE
    }

    private Map catalog() {
        (Map) new JsonSlurper().parseText(new File(projectDir, CATALOG).getText('US-ASCII'))
    }
}
