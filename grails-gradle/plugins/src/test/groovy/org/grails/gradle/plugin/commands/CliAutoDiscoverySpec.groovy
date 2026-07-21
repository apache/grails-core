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

        when: 'the app resolves its grailsCli dependencies'
        def result = executeTask(':app:inspectGrailsCli')

        then: 'the companion is a project dependency requesting the plugin cli capability'
        result.output.contains('GRAILSCLI_DEP: project path=:my-plugin capabilities=[org.example.test:my-plugin-cli]')

        and: 'it is NOT an external module coordinate (the bug this regression pins)'
        !result.output.contains('GRAILSCLI_DEP: module org.example.test:my-plugin-cli')

        and: 'the command contract and runner are auto-provisioned as well'
        result.output.contains('GRAILSCLI_DEP: module org.apache.grails:grails-core-cli')
        result.output.contains('GRAILSCLI_DEP: module org.apache.grails:grails-console')
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
        def result = executeTask(':app:inspectClasspathWiring', [':app:inspectRuntimeClasspath'])

        then: 'grailsCli extends the compile classpath but not the runtime classpath'
        result.output.contains('COMPILE_EXTENDS_GRAILSCLI=true')
        result.output.contains('RUNTIME_EXTENDS_GRAILSCLI=false')

        and: 'no companion (-cli) jar reaches the resolved runtime classpath'
        !result.output.readLines().any { it.startsWith('RUNTIME_ARTIFACT:') && it.contains('-cli') }
    }

    def "cliAutoProvision = false disables auto-provisioning of the whole cli tier"() {
        given:
        setupTestResourceProject('cli-companion-autodiscovery')

        when:
        def result = executeTask(':app:inspectGrailsCli', ['-PgrailsCliAutoProvision=false'])

        then: 'nothing is provisioned onto grailsCli — neither the companion nor the contract/runner'
        !result.output.contains('GRAILSCLI_DEP:')
    }
}
