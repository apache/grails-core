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
package org.grails.forge.feature.github.workflows.plain

import groovy.transform.CompileStatic
import org.springframework.stereotype.Component
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.feature.github.workflows.GitHubWorkflowFeature
import org.grails.forge.template.GspTemplate
import org.grails.forge.template.GspView

@Component
@CompileStatic
class PlainGithubWorkflowFeature extends GitHubWorkflowFeature {

    private static final String NAME = 'github-workflow-java-ci'

    @Override
    String getName() {
        return NAME
    }

    @Override
    String getTitle() {
        return 'GitHub Actions CI Workflow'
    }

    @Override
    String getDescription() {
        return 'Add a GitHub Actions workflow that builds and tests the project.'
    }

    @Override
    void apply(GeneratorContext generatorContext) {
        final String workflowFilePath = getWorkflowFilePath()
        generatorContext.addTemplate('javaWorkflow', new GspTemplate(workflowFilePath,
                GspView.of('/forge/feature/github/workflows/plain/templates/plainGithubWorkflow.gsp', [project: generatorContext.getProject(), jdkVersion: generatorContext.getJdkVersion()])))
    }

    protected String getWorkflowFileName() {
        return 'gradle.yml'
    }
}
