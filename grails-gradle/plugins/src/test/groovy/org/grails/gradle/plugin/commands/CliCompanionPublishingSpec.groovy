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

import groovy.json.JsonSlurper

import org.grails.gradle.plugin.core.GradleSpecification

/**
 * Verifies that cli-tier dependencies declared with a capability
 * ({@code project(':owner') { capabilities { requireCapability('g:owner-cli') } }}) are published
 * as plain dependencies on the companion coordinate ({@code owner-cli}) in both the pom and the
 * Gradle Module Metadata, and that the published artifacts resolve from a Maven repository.
 *
 * A published capability request cannot be resolved against the published component tree: the
 * owner's cli variant and its {@code available-at} redirect target both provide the capability,
 * so consumers fail with a capability self-conflict (the grails-forge generated-app failure).
 */
class CliCompanionPublishingSpec extends GradleSpecification {

    def "cli capability dependencies are published as companion coordinates and resolve externally"() {
        given: 'a build with a cli-owning module, a plugin consuming its cli tier, and a cli-tier library'
        def runner = setupTestResourceProject('cli-companion-publishing')
        def repoDir = new File(runner.projectDir, 'build/test-repo/org/example/test')

        when: 'all publications are published to a maven repository'
        executeTask('publishAllPublicationsToTestCaseMavenRepoRepository')

        then: 'the companion metadata of the plugin records the plain owner-cli coordinate'
        def pluginCliDependencies = variantDependencies(repoDir, 'plugin-a-cli')
        pluginCliDependencies.every { it.requestedCapabilities == null }
        pluginCliDependencies.any { it.group == 'org.example.test' && it.module == 'owner-cli' }

        and: 'the main metadata of the cli-tier library records the plain owner-cli coordinate'
        def libraryDependencies = variantDependencies(repoDir, 'console-lib')
        libraryDependencies.every { it.requestedCapabilities == null }
        libraryDependencies.any { it.group == 'org.example.test' && it.module == 'owner-cli' }

        and: 'the poms list the companion coordinate'
        latestArtifact(repoDir, 'plugin-a-cli', '.pom').text.contains('<artifactId>owner-cli</artifactId>')
        latestArtifact(repoDir, 'console-lib', '.pom').text.contains('<artifactId>owner-cli</artifactId>')

        and: 'a customized companion coordinate is published plainly too: the consumer metadata records acme-tools without capabilities'
        def pluginBCliDependencies = variantDependencies(repoDir, 'plugin-b-cli')
        pluginBCliDependencies.every { it.requestedCapabilities == null }
        pluginBCliDependencies.any { it.group == 'org.example.test' && it.module == 'acme-tools' }
        latestArtifact(repoDir, 'plugin-b-cli', '.pom').text.contains('<artifactId>acme-tools</artifactId>')

        when: 'an external consumer resolves the published modules from the repository'
        def resolveResult = executeTask(':resolver:resolveCliTier')

        then: 'the owners, their companions, and all consumers resolve without a capability conflict'
        resolveResult.output.contains('RESOLVED: owner-1.0.0-SNAPSHOT.jar')
        resolveResult.output.contains('RESOLVED: owner-cli-1.0.0-SNAPSHOT.jar')
        resolveResult.output.contains('RESOLVED: plugin-a-cli-1.0.0-SNAPSHOT.jar')
        resolveResult.output.contains('RESOLVED: console-lib-1.0.0-SNAPSHOT.jar')

        and: 'the customized companion coordinate resolves externally as well'
        resolveResult.output.contains('RESOLVED: plugin-b-cli-1.0.0-SNAPSHOT.jar')
        resolveResult.output.contains('RESOLVED: acme-tools-1.0.0-SNAPSHOT.jar')
    }

    private static List<Map<String, Object>> variantDependencies(File repoDir, String artifactId) {
        def metadata = new JsonSlurper().parse(latestArtifact(repoDir, artifactId, '.module'), 'UTF-8')
        List<Map<String, Object>> dependencies = (List<Map<String, Object>>) metadata.variants
                .collectMany { variant -> variant.dependencies ?: [] }
        assert dependencies : "no variant dependencies found in the ${artifactId} module metadata"
        dependencies
    }

    private static File latestArtifact(File repoDir, String artifactId, String extension) {
        File versionDir = new File(repoDir, "${artifactId}/1.0.0-SNAPSHOT")
        File[] files = versionDir.listFiles({ File file -> file.name.endsWith(extension) } as FileFilter)
        assert files : "no ${extension} files found under ${versionDir}"
        files.sort { it.name }.last()
    }
}
