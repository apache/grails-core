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
package org.grails.gradle.plugin.core

import org.gradle.testkit.runner.BuildResult

/**
 * Covers how {@code grails { indy }} reaches an application's Groovy compile tasks: not at all when
 * the application leaves it unset, so the Groovy compiler keeps its own default, and on every task
 * when the application sets it.
 *
 * @since 9.0
 */
class GrailsIndySpec extends GradleSpecification {

    void 'an application that does not set indy compiles with the Groovy compiler default'() {
        given:
            setupTestResourceProject('indy-settings')

        when:
            BuildResult result = executeTask('inspectIndy')

        then: 'no compile task is given an indy option, so Groovy compiles invokedynamic call sites'
            result.output.contains('compileGroovy=unset')
            result.output.contains('compileTestGroovy=unset')
    }

    void 'the compiled bytecode uses #callSites call sites when indy is #setting'() {
        given:
            setupTestResourceProject('indy-settings')

        when:
            BuildResult result = executeTask('inspectBytecode', arguments)

        then: 'what the compiler produced, not what the task was told'
            result.output.contains("CALL_SITES=${callSites}")

        where:
            setting  | arguments               | callSites
            'unset'  | []                      | 'indy'
            'true'   | ['-PfixtureIndy=true']  | 'indy'
            'false'  | ['-PfixtureIndy=false'] | 'classic'
    }

    void 'indy = #indy is applied to every Groovy compile task'() {
        given:
            setupTestResourceProject('indy-settings')

        when:
            BuildResult result = executeTask('inspectIndy', ["-PfixtureIndy=${indy}" as String])

        then:
            result.output.contains("compileGroovy=${indy}")
            result.output.contains("compileTestGroovy=${indy}")

        where:
            indy << [false, true]
    }
}
