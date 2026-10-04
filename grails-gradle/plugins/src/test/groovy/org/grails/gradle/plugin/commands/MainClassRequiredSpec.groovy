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

import org.grails.gradle.plugin.core.GradleSpecification

class MainClassRequiredSpec extends GradleSpecification {

    def "the #taskName task explains that it requires an application class"() {
        given: 'a plugin without an Application class'
            def runner = setupTestResourceProject('main-class-required')

        when: 'running a task that runs against the application'
            def result = runner.withArguments(taskName, '--stacktrace').buildAndFail()

        then: 'the failure says what is missing instead of reporting a provider without a value'
            result.output.contains("The '${taskName}' task requires an application class with a main method, but none was found.")
            !result.output.contains('Cannot query the value of this provider because it has no value available')

        where:
            taskName << ['runCommand', 'runScript', 'console', 'shell']
    }
}
