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
package org.grails.forge.features.openapi

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.OperatingSystem
import org.grails.forge.utils.CommandSpec

class OpenApiBuildSpec extends CommandSpec {

    void 'an app with openapi compiles its assets, without the Swagger UI webjar Closure Compiler cannot parse'() {
        given:
        generateProject(OperatingSystem.LINUX, ['openapi'], ApplicationType.WEB)

        when:
        BuildResult result = executeGradle('assetCompile')

        then:
        result.task(':assetCompile').outcome == TaskOutcome.SUCCESS
    }

    @Override
    String getTempDirectoryPrefix() {
        'testopenapi'
    }
}
