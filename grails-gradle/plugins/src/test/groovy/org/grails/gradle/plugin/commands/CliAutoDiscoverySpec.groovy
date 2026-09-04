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

/**
 * Verifies the discovery/wiring half of the cli split (the companion publishing half is covered by
 * {@link CliCompanionPublishingSpec}): auto-provisioning of the cli tier onto {@code grailsCli},
 * the guarantee that the tier is compile-visible but never leaks onto the runtime classpath, and
 * the {@code cliAutoProvision} opt-out.
 *
 * <p>The critical scenario is a companion advertised by a command-bearing plugin developed in the
 * <em>same build</em> as the consuming app. That companion must be wired as a project dependency
 * requesting the plugin's {@code cli} feature capability — not as an unversioned external module
 * coordinate, which would send Gradle to a repository for a jar the sibling project has not
 * published.</p>
 */
class CliAutoDiscoverySpec extends GradleSpecification {

    def "a companion advertised by a same-build project dependency is wired to its cli capability, not an external module"() {
        given: 'an app that depends on an in-build command-bearing plugin'
        setupTestResourceProject('cli-companion-autodiscovery')
        executeTask(':app:prepareDigitBearingPlugin')

        when: 'the app resolves its grailsCli dependencies'
        def result = executeTask(':app:inspectGrailsCli', [
                ':app:inspectGrailsCliLegacy',
                ':app:inspectClasspathWiring',
                '-PgrailsLegacyCommandSupport=true'
        ])

        then: 'the companion is a project dependency requesting the plugin cli capability'
        result.output.contains('GRAILSCLI_DEP: project path=:my-plugin capabilities=[org.example.test:my-plugin-cli]')

        and: 'it is NOT an external module coordinate (the bug this regression pins)'
        !result.output.contains('GRAILSCLI_DEP: module org.example.test:my-plugin-cli')

        and: 'digit-bearing composite-build artifacts use their resolved producer versions'
        result.output.contains('GRAILSCLI_DEP: module org.example.test:my-plugin-2fa-one-cli:1')
        result.output.contains('GRAILSCLI_DEP: module org.example.test:my-plugin-2fa-snapshot-cli:1-SNAPSHOT')
        result.output.contains('GRAILSCLI_DEP: module org.example.test:my-plugin-2fa-timestamp-cli:1-20260101.123456-1')
        result.output.contains("GRAILSCLI_DEP: module org.example.test:my-plugin-2fa-unspecified-cli:${PROJECT_VERSION}")

        and: 'the command contract and runner are auto-provisioned at the current Grails version'
        result.output.contains("GRAILSCLI_DEP: module org.apache.grails:grails-core-cli:${PROJECT_VERSION}")
        result.output.contains("GRAILSCLI_DEP: module org.apache.grails:grails-console:${PROJECT_VERSION}")

        and: 'the opt-in legacy bridge is execution-only and versioned to the current Grails version'
        result.output.contains("GRAILSCLILEGACY_DEP: module org.apache.grails:grails-core-cli-legacy:${PROJECT_VERSION}")
        !result.output.contains('GRAILSCLI_DEP: module org.apache.grails:grails-core-cli-legacy')
        result.output.contains('CLASSPATH_EXTENDS_GRAILSCLILEGACY=true')
        result.output.contains('COMPILE_EXTENDS_GRAILSCLILEGACY=false')
        result.output.contains('COMPILE_HAS_GRAILSCLILEGACY=false')
        result.output.contains('TEST_COMPILE_HAS_GRAILSCLILEGACY=false')
        result.output.contains('INTEGRATION_TEST_COMPILE_HAS_GRAILSCLILEGACY=false')
        result.output.contains('TEST_RUNTIME_HAS_GRAILSCLILEGACY=true')
        result.output.contains('INTEGRATION_TEST_RUNTIME_HAS_GRAILSCLILEGACY=true')
    }

    def "a companion with a customized artifactId is discovered and resolves through its advertised capability"() {
        given: 'an app depending on a plugin whose companion coordinate is customized'
        setupTestResourceProject('cli-companion-autodiscovery')

        when: 'the app resolves its grailsCli dependencies'
        def result = executeTask(':app:inspectGrailsCli', [':app:inspectCliClasspath'])

        then: 'the companion is discovered under the advertised (customized) capability'
        result.output.contains('GRAILSCLI_DEP: project path=:renamed-plugin capabilities=[org.example.test:acme-cli-tools]')

        and: 'both companion jars actually resolve from the sibling projects cli variants'
        result.output.contains('CLI_ARTIFACT: my-plugin-cli-1.0.0.jar')
        result.output.contains('CLI_ARTIFACT: acme-cli-tools-1.0.0.jar')
    }

    def "the cli tier is compile-visible but never leaks onto the runtime classpath"() {
        given:
        setupTestResourceProject('cli-companion-autodiscovery')

        when:
        def result = executeTask(':app:inspectClasspathWiring', [
                ':app:inspectRuntimeClasspath',
                '-PgrailsLegacyCommandSupport=true'
        ])

        then: 'grailsCli extends the compile classpath but not the runtime classpath'
        result.output.contains('COMPILE_EXTENDS_GRAILSCLI=true')
        result.output.contains('RUNTIME_EXTENDS_GRAILSCLI=false')
        result.output.contains('COMPILE_EXTENDS_GRAILSCLILEGACY=false')
        result.output.contains('CLASSPATH_EXTENDS_GRAILSCLILEGACY=true')
        result.output.contains('COMPILE_HAS_GRAILSCLILEGACY=false')
        result.output.contains('TEST_COMPILE_HAS_GRAILSCLILEGACY=false')
        result.output.contains('INTEGRATION_TEST_COMPILE_HAS_GRAILSCLILEGACY=false')
        result.output.contains('TEST_RUNTIME_HAS_GRAILSCLILEGACY=true')
        result.output.contains('INTEGRATION_TEST_RUNTIME_HAS_GRAILSCLILEGACY=true')

        and: 'no companion (-cli) jar reaches the resolved runtime classpath'
        !result.output.readLines().any { it.startsWith('RUNTIME_ARTIFACT:') && it.contains('-cli') }
    }

    def "cliAutoProvision = false disables auto-provisioning of the whole cli tier"() {
        given:
        setupTestResourceProject('cli-companion-autodiscovery')

        when: 'the master switch is off even if the legacy bridge is explicitly opted in'
        def result = executeTask(':app:inspectGrailsCli', [
                ':app:inspectGrailsCliLegacy',
                ':app:inspectClasspathWiring',
                '-PgrailsCliAutoProvision=false',
                '-PgrailsLegacyCommandSupport=true'
        ])

        then: 'nothing is provisioned onto grailsCli — neither the companion nor the contract/runner'
        !result.output.contains('GRAILSCLI_DEP:')
        !result.output.contains('GRAILSCLILEGACY_DEP:')
        result.output.contains('COMPILE_HAS_GRAILSCLILEGACY=false')
        result.output.contains('TEST_COMPILE_HAS_GRAILSCLILEGACY=false')
        result.output.contains('INTEGRATION_TEST_COMPILE_HAS_GRAILSCLILEGACY=false')
        result.output.contains('TEST_RUNTIME_HAS_GRAILSCLILEGACY=false')
        result.output.contains('INTEGRATION_TEST_RUNTIME_HAS_GRAILSCLILEGACY=false')
    }

    def "legacyCommandSupport defaults to false and leaves only the modern CLI tier"() {
        given:
        setupTestResourceProject('cli-companion-autodiscovery')

        when:
        def result = executeTask(':app:inspectGrailsCli', [
                ':app:inspectGrailsCliLegacy',
                ':app:inspectClasspathWiring'
        ])

        then: 'the modern CLI tier is still auto-provisioned'
        result.output.contains("GRAILSCLI_DEP: module org.apache.grails:grails-core-cli:${PROJECT_VERSION}")
        result.output.contains("GRAILSCLI_DEP: module org.apache.grails:grails-console:${PROJECT_VERSION}")
        result.output.contains('GRAILSCLI_DEP: project path=:my-plugin capabilities=[org.example.test:my-plugin-cli]')

        and: 'the legacy bridge is not provisioned by default'
        !result.output.contains('GRAILSCLILEGACY_DEP:')
        result.output.contains('COMPILE_HAS_GRAILSCLILEGACY=false')
        result.output.contains('TEST_COMPILE_HAS_GRAILSCLILEGACY=false')
        result.output.contains('INTEGRATION_TEST_COMPILE_HAS_GRAILSCLILEGACY=false')
        result.output.contains('TEST_RUNTIME_HAS_GRAILSCLILEGACY=false')
        result.output.contains('INTEGRATION_TEST_RUNTIME_HAS_GRAILSCLILEGACY=false')
    }

    def "legacyCommandSupport = false disables only the Grails 7 command bridge"() {
        given:
        setupTestResourceProject('cli-companion-autodiscovery')

        when:
        def result = executeTask(':app:inspectGrailsCli', [
                ':app:inspectGrailsCliLegacy',
                ':app:inspectClasspathWiring',
                '-PgrailsLegacyCommandSupport=false'
        ])

        then: 'the modern CLI tier is still auto-provisioned'
        result.output.contains("GRAILSCLI_DEP: module org.apache.grails:grails-core-cli:${PROJECT_VERSION}")
        result.output.contains("GRAILSCLI_DEP: module org.apache.grails:grails-console:${PROJECT_VERSION}")
        result.output.contains('GRAILSCLI_DEP: project path=:my-plugin capabilities=[org.example.test:my-plugin-cli]')

        and: 'the explicit false property keeps the legacy bridge off'
        !result.output.contains('GRAILSCLILEGACY_DEP:')
        result.output.contains('COMPILE_HAS_GRAILSCLILEGACY=false')
        result.output.contains('TEST_COMPILE_HAS_GRAILSCLILEGACY=false')
        result.output.contains('INTEGRATION_TEST_COMPILE_HAS_GRAILSCLILEGACY=false')
        result.output.contains('TEST_RUNTIME_HAS_GRAILSCLILEGACY=false')
        result.output.contains('INTEGRATION_TEST_RUNTIME_HAS_GRAILSCLILEGACY=false')
    }

    def "legacyCommandSupport = true provisions only the Grails 7 command bridge"() {
        given:
        setupTestResourceProject('cli-companion-autodiscovery')

        when:
        def result = executeTask(':app:inspectGrailsCli', [
                ':app:inspectGrailsCliLegacy',
                ':app:inspectClasspathWiring',
                '-PgrailsLegacyCommandSupport=true'
        ])

        then: 'the modern CLI tier remains auto-provisioned'
        result.output.contains("GRAILSCLI_DEP: module org.apache.grails:grails-core-cli:${PROJECT_VERSION}")
        result.output.contains("GRAILSCLI_DEP: module org.apache.grails:grails-console:${PROJECT_VERSION}")
        result.output.contains('GRAILSCLI_DEP: project path=:my-plugin capabilities=[org.example.test:my-plugin-cli]')

        and: 'the legacy bridge is provisioned only when opted in'
        result.output.contains("GRAILSCLILEGACY_DEP: module org.apache.grails:grails-core-cli-legacy:${PROJECT_VERSION}")
        !result.output.contains('GRAILSCLI_DEP: module org.apache.grails:grails-core-cli-legacy')
        result.output.contains('TEST_RUNTIME_HAS_GRAILSCLILEGACY=true')
        result.output.contains('INTEGRATION_TEST_RUNTIME_HAS_GRAILSCLILEGACY=true')
    }
}
