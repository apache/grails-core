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

package org.grails.forge.feature.github.workflows

import org.grails.forge.BeanContextSpec
import org.grails.forge.application.ApplicationType
import org.grails.forge.feature.github.workflows.plain.PlainGithubWorkflowFeature
import org.grails.forge.fixture.CommandOutputFixture
import org.grails.forge.options.BuildTool
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.Options
import org.grails.forge.options.TestFramework
import spock.lang.Unroll

class PlainGithubWorkflowSpec extends BeanContextSpec implements CommandOutputFixture {

    @Unroll
    void 'test github workflow is created for #buildTool'(BuildTool buildTool, String workflowName) {
        when:
        def output = generate(ApplicationType.WEB,
                new Options(TestFramework.SPOCK),
                [PlainGithubWorkflowFeature.NAME])
        def workflow = output[".github/workflows/${workflowName}"]

        then:
        workflow
        workflow.contains("name: Java CI")

        where:
        buildTool | workflowName
        BuildTool.GRADLE | "gradle.yml"
    }

    @Unroll
    void 'test github gradle workflow java version for #version'(JdkVersion version) {
        when:
        def output = generate(ApplicationType.WEB,
                new Options(TestFramework.JUNIT, version),
                [PlainGithubWorkflowFeature.NAME])
        def workflow = output['.github/workflows/gradle.yml']

        then:
        workflow
        workflow.contains("java-version: ${version.majorVersion()}")

        where:
        version << JdkVersion.values()
    }
}
