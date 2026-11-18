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

class LegacyCommandTaskDiscoverySpec extends GradleSpecification {

    def "a legacy runtime command does not register a named task while runCommand remains available"() {
        given: 'a Grails 7-style command plugin and a consuming application'
        setupTestResourceProject('legacy-command-discovery')

        when: 'the producer jar containing the legacy factories registration is built'
        def jarResult = executeTask(':legacy-command-plugin:jar')

        and: 'the consuming application inspects its registered tasks'
        def tasksResult = executeTask(':app:inspectLegacyCommandTask')

        then: 'the runtime jar is available for legacy discovery'
        assertTaskSuccess('jar', jarResult)

        and: 'no configuration-time named task is invented from runtimeClasspath'
        tasksResult.output.contains('LEGACY_COMMAND_TASK_PRESENT=false')

        and: 'the generic runCommand task remains available'
        tasksResult.output.contains('RUN_COMMAND_TASK_PRESENT=true')
    }
}
