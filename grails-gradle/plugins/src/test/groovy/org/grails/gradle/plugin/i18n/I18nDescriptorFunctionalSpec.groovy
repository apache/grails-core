/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.grails.gradle.plugin.i18n

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner

import org.grails.gradle.plugin.core.GradleSpecification

/**
 * Exercises the i18n descriptor through the plugins an application or plugin author actually applies,
 * rather than by invoking the task directly: automatic registration, application-versus-plugin
 * dispatch, plugin-name derivation from the {@code *GrailsPlugin.groovy} descriptor, the
 * {@code grails { i18n { } }} block, and inclusion in {@code processResources}.
 */
class I18nDescriptorFunctionalSpec extends GradleSpecification {

    private Properties descriptorFrom(GradleRunner runner) {
        File descriptor = new File(runner.projectDir,
                "build/resources/main/${GenerateI18nDescriptorTask.DESCRIPTOR_PATH}")
        assert descriptor.exists(): "No descriptor generated at ${descriptor}"
        Properties properties = new Properties()
        descriptor.withInputStream { properties.load(it) }
        properties
    }

    void 'an application project generates its descriptor into processResources output'() {
        given:
        GradleRunner runner = setupTestResourceProject('i18n-descriptor-app')

        when: 'no task is registered by hand — the Grails application plugin wires it'
        BuildResult result = executeTask('processResources')

        then:
        assertTaskSuccess('generateI18nDescriptor', result)

        and:
        Properties descriptor = descriptorFrom(runner)
        descriptor.'artifact.type' == 'application'
        descriptor.'artifact.name' == 'i18n-descriptor-app'
        descriptor.basenames == 'api_errors,messages'
        descriptor.locales == 'de,pt_BR'
    }

    void 'a plugin project records the hyphenated Grails plugin name, not the project name'() {
        given: 'a descriptor class named SpringSecurityDemoGrailsPlugin in a project named otherwise'
        GradleRunner runner = setupTestResourceProject('i18n-descriptor-plugin')

        when:
        BuildResult result = executeTask('processResources')

        then:
        assertTaskSuccess('generateI18nDescriptor', result)

        and: 'the runtime plugin name is what links the descriptor to the discovered plugin'
        Properties descriptor = descriptorFrom(runner)
        descriptor.'artifact.type' == 'plugin'
        descriptor.'artifact.name' == 'spring-security-demo'

        and: 'a plugin may ship several bundles inside its own namespace'
        descriptor.basenames == 'spring-security-demo,spring-security-demo-validation'
        descriptor.locales == 'fr'
    }

    void 'a plugin bundle outside the plugin namespace fails the build'() {
        given: 'the plugin ships messages.properties, which would shadow the application\'s own'
        GradleRunner runner = setupTestResourceProject('i18n-descriptor-plugin-collision')

        when:
        BuildResult result = runner.withArguments('processResources', '--stacktrace').buildAndFail()

        then:
        result.output.contains('ships message bundles outside its own namespace')
    }

    void 'declared base names override the inference the file names would produce'() {
        given: "api.properties beside api_errors.properties would otherwise read as a mistyped locale"
        GradleRunner runner = setupTestResourceProject('i18n-descriptor-declared')

        when:
        BuildResult result = executeTask('processResources')

        then:
        assertTaskSuccess('generateI18nDescriptor', result)

        and:
        Properties descriptor = descriptorFrom(runner)
        descriptor.basenames == 'api,api_errors'
        descriptor.locales == ''
    }

    void 'the descriptor is regenerated when a bundle is removed'() {
        given:
        GradleRunner runner = setupTestResourceProject('i18n-descriptor-app')
        executeTask('processResources')

        when: 'the only locale-independent bundle for a base name disappears'
        new File(runner.projectDir, 'grails-app/i18n/api_errors.properties').delete()
        BuildResult result = executeTask('processResources')

        then: 'the stale base name does not survive in the descriptor'
        assertTaskSuccess('generateI18nDescriptor', result)
        descriptorFrom(runner).basenames == 'messages'
    }
}
