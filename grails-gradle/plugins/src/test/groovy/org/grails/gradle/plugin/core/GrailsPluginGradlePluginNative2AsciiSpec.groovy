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

import org.gradle.testkit.runner.GradleRunner

/**
 * The resource processing of a plugin project reads the grails extension when the task is configured, so
 * the extension must not be read before the build script has run. The plugin once realized every task
 * while it was applied, and {@code native2ascii = false} in the build script was read too late.
 */
class GrailsPluginGradlePluginNative2AsciiSpec extends GradleSpecification {

    def "native2ascii = false keeps a plugin's resource bundles as written"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugin-native2ascii-off')

        when:
        def result = executeTask('processResources')

        then:
        assertTaskSuccess('processResources', result)
        new File(runner.projectDir, 'build/resources/main/templates/messages_ru.properties')
                .getText('UTF-8')
                .contains('welcome.title=Добро пожаловать в Grails')
    }
}
