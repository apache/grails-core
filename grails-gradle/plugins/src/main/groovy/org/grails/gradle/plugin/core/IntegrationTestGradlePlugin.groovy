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

import groovy.transform.CompileStatic

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Gradle plugin for adding separate src/integration-test folder to hold integration tests.
 *
 * <p>This plugin applies {@link TestPhasesGradlePlugin} and registers the default
 * {@code integrationTest} phase. Additional test phases can be added via the
 * {@code testPhases} extension.</p>
 *
 * <p>Adds integrationTestImplementation and integrationTestRuntimeOnly configurations
 * that extend from testCompileClasspath and testRuntimeClasspath.</p>
 *
 */
@CompileStatic
class IntegrationTestGradlePlugin implements Plugin<Project> {

    @Deprecated(forRemoval = true, since = '7.1')
    static final String INTEGRATION_TEST_IMPLEMENTATION_CONFIGURATION_NAME = 'integrationTestImplementation'

    @Deprecated(forRemoval = true, since = '7.1')
    static final String INTEGRATION_TEST_RUNTIME_ONLY_CONFIGURATION_NAME = 'integrationTestRuntimeOnly'

    static final String INTEGRATION_TEST_SOURCE_SET_NAME = 'integrationTest'

    @Deprecated(forRemoval = true, since = '7.1')
    static final String INTEGRATION_TEST_TASK_NAME = 'integrationTest'

    @Deprecated(forRemoval = true, since = '7.1')
    static final String MERGE_TEST_REPORTS_TASK_NAME = 'mergeTestReports'

    @Deprecated(forRemoval = true, since = '7.1')
    static final String GRAILS_INTEGRATION_TEST_INDICATOR = 'is.grails.integration.test'

    boolean ideaIntegration = true

    String sourceFolderName = 'src/integration-test'

    @Override
    void apply(Project project) {
        project.pluginManager.apply(TestPhasesGradlePlugin)

        NamedDomainObjectContainer<TestPhase> testPhases = (NamedDomainObjectContainer<TestPhase>) project.extensions.getByName(TestPhasesGradlePlugin.EXTENSION_NAME)
        testPhases.create(INTEGRATION_TEST_SOURCE_SET_NAME) { TestPhase phase ->
            phase.sourceFolderName.set(sourceFolderName)
            phase.ideaIntegration.set(ideaIntegration)
        }
    }
}
