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

/**
 * The test phases of a project in the catalog of its commands.
 */
class ToolCatalogTestPhasesSpec extends Specification {

    def "a catalog that was given no phases says nothing of them, and one that was given none lists none"() {
        when:
        ToolCatalogBuilder builder = new ToolCatalogBuilder()

        then:
        !builder.build().containsKey('testPhases')

        when:
        builder.testPhases([:])

        then:
        builder.build().testPhases == []
        builder.toJson().endsWith('"tools":[],"testPhases":[]}\n')
    }

    def "the phases are listed in the order they are given in, and one that cannot be a switch or whose task has an unusual name is not"() {
        given:
        ToolCatalogBuilder builder = new ToolCatalogBuilder()
        Map<String, String> phases = [unit: 'test', integration: 'integrationTest', 'end-to-end': 'endToEndTest', smoke: 'smoke']
        phases.putAll(['api_check': 'api_check', 'Upper': 'Upper', '': 'empty', '-x': 'x', 'a--b': 'ab', ('x' * 65): 'long', 'odd': 'a task', 'esc': 'a\u001b[31m',
                       'dollar': 'a$b', 'at': 'a@b', 'longTask': ('t' * 65)])

        when:
        builder.testPhases(phases)

        then:
        builder.build().testPhases == [[name: 'unit', task: 'test'], [name: 'integration', task: 'integrationTest'],
                                       [name: 'end-to-end', task: 'endToEndTest'], [name: 'smoke', task: 'smoke']]
        builder.build().complete == true
    }

    def "no more phases are listed than a catalog lists"() {
        given:
        ToolCatalogBuilder builder = new ToolCatalogBuilder()

        when:
        builder.testPhases((1..100).collectEntries { int number -> ["phase-${number}".toString(), "phase${number}Test".toString()] })

        then:
        builder.build().testPhases.size() == ToolCatalogBuilder.MAX_TEST_PHASES
        builder.build().testPhases.first() == [name: 'phase-1', task: 'phase1Test']
    }
}
