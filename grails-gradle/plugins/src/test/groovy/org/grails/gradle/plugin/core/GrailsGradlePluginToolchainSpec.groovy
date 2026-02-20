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

/**
 * Tests that {@link GrailsGradlePlugin} propagates the project's Java toolchain
 * to JavaExec tasks via {@code javaLauncher.convention()}.
 *
 * <p>Without the fix, JavaExec tasks spawned by Grails (dbm-* migration
 * commands, console, shell, application context commands) use the JDK
 * running Gradle instead of the project's configured toolchain.</p>
 *
 * @since 7.0.8
 * @see GrailsGradlePlugin#configureToolchainForForkTasks
 */
class GrailsGradlePluginToolchainSpec extends GradleSpecification {

    // ----------------------------------------------------------------
    // Toolchain propagation
    // ----------------------------------------------------------------

    def "JavaExec tasks inherit project toolchain"() {
        given:
        setupTestResourceProject('toolchain-javaexec')

        when:
        def result = executeTask('checkToolchain')

        then:
        result.output.contains("TOOLCHAIN_VERSION=${CURRENT_JDK}")
    }

    def "Test tasks inherit project toolchain"() {
        given:
        setupTestResourceProject('toolchain-test')

        when:
        def result = executeTask('checkTestToolchain')

        then:
        result.output.contains("TEST_TOOLCHAIN_VERSION=${CURRENT_JDK}")
    }

    def "ApplicationContextCommandTask inherits toolchain via grails-web plugin"() {
        given:
        setupTestResourceProject('toolchain-command')

        when:
        def result = executeTask('checkCommandToolchain')

        then:
        result.output.contains("CMD_TOOLCHAIN_VERSION=${CURRENT_JDK}")
    }

    def "convention allows individual task override via set()"() {
        given:
        setupTestResourceProject('toolchain-override')

        when:
        def result = executeTask('checkOverride')

        then:
        result.output.contains("OVERRIDE_VERSION=${CURRENT_JDK}")
    }

    // ----------------------------------------------------------------
    // Backwards compatibility (no toolchain configured)
    // ----------------------------------------------------------------

    def "JavaExec tasks work without errors when no toolchain configured"() {
        given:
        setupTestResourceProject('no-toolchain-javaexec')

        when:
        def result = executeTask('checkToolchain')

        then:
        result.output.contains('HAS_LAUNCHER=')
    }

    def "GrailsWebGradlePlugin works without errors when no toolchain configured"() {
        given:
        setupTestResourceProject('no-toolchain-web')

        when:
        def result = executeTask('checkNoError')

        then:
        result.output.contains('WEB_PLUGIN_OK=true')
    }

    // ----------------------------------------------------------------
    // Fork settings preservation
    // ----------------------------------------------------------------

    def "configureForkSettings applies system properties and default heap sizes"() {
        given:
        setupTestResourceProject('fork-settings-defaults')

        when:
        def result = executeTask('inspectSysProps')

        then:
        result.output.contains('HAS_ENV=true')
        result.output.contains('MIN_HEAP=768m')
        result.output.contains('MAX_HEAP=768m')
    }

    def "custom heap sizes are not overridden by fork settings"() {
        given:
        setupTestResourceProject('fork-settings-custom')

        when:
        def result = executeTask('inspectHeap')

        then:
        result.output.contains('MIN_HEAP=512m')
        result.output.contains('MAX_HEAP=2g')
    }
}
