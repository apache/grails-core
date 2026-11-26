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
package org.grails.forge.create

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.grails.forge.application.ApplicationType
import org.grails.forge.utils.CommandSpec
import spock.lang.Unroll

/**
 * What each {@code create-} command generates is a project its own Gradle build configures and
 * assembles, as it is generated and before anything is added to it: every type of application there
 * is, so that a type whose fresh project does not build is seen here. A REST API plugin is generated
 * without a {@code grails-app/views} directory, which the Gradle plugin of the JSON views once refused.
 */
class GeneratedTypesAssembleSpec extends CommandSpec {

    @Unroll
    void 'a freshly generated #type.name project configures and assembles'() {
        given:
        generateProjectWithDefaults(type)

        when:
        BuildResult assembled = executeGradle('assemble')

        then:
        assembled.task(':assemble').outcome in [TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE]
        assembled.output.contains('BUILD SUCCESSFUL')
        new File(dir, 'build/libs').list().any { it.endsWith('.jar') || it.endsWith('.war') }

        where:
        type << ApplicationType.values().toList()
    }

    void 'the types that are assembled here are the five there are'() {
        expect:
        ApplicationType.values()*.name.toSorted() == ['plugin', 'rest_api', 'rest_api_plugin', 'web', 'web_plugin']
    }

    @Override
    String getTempDirectoryPrefix() {
        'testgeneratedtypes'
    }
}
