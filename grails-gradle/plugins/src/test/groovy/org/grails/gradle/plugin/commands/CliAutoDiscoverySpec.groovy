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
 * Verifies the discovery/wiring half of the companions of plugins (the publishing half is covered by
 * {@link CliCompanionPublishingSpec} and {@link TwoCompanionsSpec}): auto-provisioning of the
 * companions {@code -cli} onto {@code grailsCli} and {@code -cli-standalone} onto
 * {@code grailsCliStandalone}, the guarantee that neither scope is on the compile or the runtime
 * class path of the application, and the {@code cliAutoProvision} opt-out.
 *
 * <p>The critical scenario is a companion advertised by a tool-bearing plugin developed in the
 * <em>same build</em> as the consuming app. That companion must be wired as a project dependency
 * requesting the plugin's feature capability — not as an unversioned external module coordinate,
 * which would send Gradle to a repository for a jar the sibling project has not published.</p>
 */
class CliAutoDiscoverySpec extends GradleSpecification {

    def "a companion advertised by a same-build project dependency is wired to its capability, not an external module"() {
        given: 'an app that depends on an in-build tool-bearing plugin'
        setupTestResourceProject('cli-companion-autodiscovery')
        executeTask(':app:prepareDigitBearingPlugin')

        when: 'the app resolves its grailsCli and grailsCliStandalone dependencies'
        def result = executeTask(':app:inspectGrailsCli', [':app:inspectGrailsCliStandalone'])

        then: 'each companion is a project dependency requesting the capability of its feature, in the scope of its kind'
        result.output.contains('GRAILSCLI_DEP: project path=:my-plugin capabilities=[org.example.test:my-plugin-cli]')
        result.output.contains('GRAILSCLISTANDALONE_DEP: project path=:my-plugin capabilities=[org.example.test:my-plugin-cli-standalone]')

        and: 'neither is an external module coordinate (the bug this regression pins), nor in the scope of the other kind'
        !result.output.contains('GRAILSCLI_DEP: module org.example.test:my-plugin-cli')
        !result.output.contains('GRAILSCLISTANDALONE_DEP: module org.example.test:my-plugin-cli-standalone')
        !result.output.contains('GRAILSCLI_DEP: project path=:my-plugin capabilities=[org.example.test:my-plugin-cli-standalone]')
        !result.output.contains('GRAILSCLISTANDALONE_DEP: project path=:my-plugin capabilities=[org.example.test:my-plugin-cli]')

        and: 'digit-bearing composite-build artifacts use their resolved producer versions'
        result.output.contains('GRAILSCLI_DEP: module org.example.test:my-plugin-2fa-one-cli:1')
        result.output.contains('GRAILSCLI_DEP: module org.example.test:my-plugin-2fa-snapshot-cli:1-SNAPSHOT')
        result.output.contains('GRAILSCLI_DEP: module org.example.test:my-plugin-2fa-timestamp-cli:1-20260101.123456-1')
        result.output.contains("GRAILSCLI_DEP: module org.example.test:my-plugin-2fa-unspecified-cli:${PROJECT_VERSION}")

        and: 'a jar that advertises a -cli-standalone companion has it in grailsCliStandalone, at its version; the others advertise none'
        result.output.contains('GRAILSCLISTANDALONE_DEP: module org.example.test:my-plugin-2fa-one-cli-standalone:1')
        result.output.readLines().count { it.startsWith('GRAILSCLISTANDALONE_DEP: module org.example.test:my-plugin-2fa') } == 1

        and: 'the tool API and the runner are auto-provisioned at the current Grails version, and the tool API alone for standalone tools'
        result.output.contains("GRAILSCLI_DEP: module org.apache.grails:grails-core-cli:${PROJECT_VERSION}")
        result.output.contains("GRAILSCLI_DEP: module org.apache.grails:grails-console:${PROJECT_VERSION}")
        result.output.contains("GRAILSCLISTANDALONE_DEP: module org.apache.grails:grails-core-cli:${PROJECT_VERSION}")
        !result.output.contains('GRAILSCLISTANDALONE_DEP: module org.apache.grails:grails-console')

        and: 'nothing of the bridge for the commands of Grails 7'
        !result.output.contains('grails-core-cli-legacy')
    }

    def "building an in-build library does not invalidate the configuration cache entry that discovered the companions"() {
        given: 'an app that also depends on a library of the same build without a companion'
        setupTestResourceProject('cli-companion-autodiscovery')
        executeTask(':app:prepareDigitBearingPlugin')

        when: 'the app discovers its companions, the library jar is built, and the app discovers them again'
        def stored = executeTask(':app:inspectGrailsCli')
        executeTask(':plain-library:jar')
        def reused = executeTask(':app:inspectGrailsCli')

        then: 'the library jar was never read during discovery, so building it leaves the entry reusable'
        stored.output.contains('Configuration cache entry stored')
        reused.output.contains('Configuration cache entry reused')
        reused.output.contains('GRAILSCLI_DEP: project path=:my-plugin capabilities=[org.example.test:my-plugin-cli]')
    }

    def "a companion with a customized artifactId is discovered and resolves through its advertised capability"() {
        given: 'an app depending on a plugin whose companion coordinates are customized'
        setupTestResourceProject('cli-companion-autodiscovery')

        when: 'the app resolves its grailsCli and grailsCliStandalone dependencies'
        def result = executeTask(':app:inspectGrailsCli', [':app:inspectCliClasspath', ':app:inspectGrailsCliStandalone', ':app:inspectStandaloneClasspath'])

        then: 'the companions are discovered under the advertised (customized) capabilities'
        result.output.contains('GRAILSCLI_DEP: project path=:renamed-plugin capabilities=[org.example.test:acme-cli-tools]')
        result.output.contains('GRAILSCLISTANDALONE_DEP: project path=:renamed-plugin capabilities=[org.example.test:acme-files]')

        and: 'the companion jars actually resolve from the sibling projects variants'
        result.output.contains('CLI_ARTIFACT: my-plugin-cli-1.0.0.jar')
        result.output.contains('CLI_ARTIFACT: acme-cli-tools-1.0.0.jar')
        result.output.contains('STANDALONE_ARTIFACT: my-plugin-cli-standalone-1.0.0.jar')
        result.output.contains('STANDALONE_ARTIFACT: acme-files-1.0.0.jar')

        and: 'the class path a standalone tool is started with has no companion -cli and no jar of a plugin'
        !result.output.readLines().any { it.startsWith('STANDALONE_ARTIFACT:') && (it.contains('-cli-1') || it.contains('acme-cli-tools') || it.contains('my-plugin-1')) }

        and: 'grailsCliClasspath, which the build runs tools with, has both kinds'
        result.output.contains('CLI_ARTIFACT: my-plugin-cli-standalone-1.0.0.jar')
        result.output.contains('CLI_ARTIFACT: acme-files-1.0.0.jar')
    }

    def "the scopes of the tools are on neither the compile nor the runtime class path of the application"() {
        given:
        setupTestResourceProject('cli-companion-autodiscovery')

        when:
        def result = executeTask(':app:inspectClasspathWiring', [':app:inspectRuntimeClasspath'])

        then: 'neither scope extends the main class paths'
        result.output.contains('COMPILE_EXTENDS_GRAILSCLI=false')
        result.output.contains('COMPILE_EXTENDS_GRAILSCLISTANDALONE=false')
        result.output.contains('RUNTIME_EXTENDS_GRAILSCLI=false')
        result.output.contains('RUNTIME_EXTENDS_GRAILSCLISTANDALONE=false')

        and: 'the tests and src/cli have both'
        ['TEST_COMPILE', 'TEST_RUNTIME', 'INTEGRATION_TEST_COMPILE', 'INTEGRATION_TEST_RUNTIME', 'CLI_COMPILE', 'CLI_RUNTIME', 'CLASSPATH'].every { String label ->
            result.output.contains("${label}_EXTENDS_GRAILSCLI=true") && result.output.contains("${label}_EXTENDS_GRAILSCLISTANDALONE=true")
        }

        and: 'src/standalone and the class path of standalone tools have grailsCliStandalone alone'
        ['STANDALONE_COMPILE', 'STANDALONE_RUNTIME', 'STANDALONE_CLASSPATH'].every { String label ->
            result.output.contains("${label}_EXTENDS_GRAILSCLI=false") && result.output.contains("${label}_EXTENDS_GRAILSCLISTANDALONE=true")
        }

        and: 'no companion jar reaches the resolved runtime classpath'
        !result.output.readLines().any { it.startsWith('RUNTIME_ARTIFACT:') && (it.contains('-cli') || it.contains('acme-')) }
    }

    def "cliAutoProvision = false disables auto-provisioning of both scopes"() {
        given:
        setupTestResourceProject('cli-companion-autodiscovery')

        when: 'the master switch is off'
        def result = executeTask(':app:inspectGrailsCli', [':app:inspectGrailsCliStandalone', '-PgrailsCliAutoProvision=false'])

        then: 'nothing is provisioned — neither the companions nor the tool API and runner'
        !result.output.contains('GRAILSCLI_DEP:')
        !result.output.contains('GRAILSCLISTANDALONE_DEP:')
    }

    def "configuring the cli wiring emits no Gradle deprecation warnings"() {
        given: 'a web application, which applies the cli plugin'
        setupTestResourceProject('cli-deprecation-free')

        when: 'every task is configured with deprecation warnings treated as failures'
        def result = executeTask('tasks', ['--all', '--warning-mode=fail'])

        then: 'the build succeeds without a deprecation warning'
        !result.output.contains('has been deprecated')
    }
}
