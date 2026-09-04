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
package org.apache.grails.buildsrc

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class SbomPluginSpec extends Specification {

    private static final def LOGGER = Logging.getLogger(SbomPluginSpec)
    private static final String HIBERNATE_COMMONS = 'pkg:maven/org.hibernate.common/hibernate-commons-annotations@5.1.2.Final?type=jar'

    private static List lgplChoice() {
        [[license: [id: 'LGPL-2.1-only']]]
    }

    static class FakeCliArtifactExtension {
        final Property<String> artifactId
        FakeCliArtifactExtension(Property<String> artifactId) { this.artifactId = artifactId }
    }

    private static Project projectWithVersion(String version) {
        Project project = ProjectBuilder.builder().withName('grails-core').build()
        project.extensions.extraProperties.set('projectVersion', version)
        project
    }

    private static void addCliArtifactExtension(Project project, String artifactId) {
        Property<String> prop = project.objects.property(String)
        prop.set(artifactId)
        project.extensions.add('cliArtifact', new FakeCliArtifactExtension(prop))
    }

    void "sbomOutputLocationFor names the file <artifactId>-<version>-sbom.json"() {
        given:
        Project project = projectWithVersion('8.0.0')

        expect:
        SbomPlugin.sbomOutputLocationFor(project, project.provider { 'grails-core' }).get().asFile.name ==
                'grails-core-8.0.0-sbom.json'
    }

    void "cliCompanionArtifactId honours a custom cli artifactId"() {
        given: "a cli-artifact extension configured with a customised companion coordinate"
        Project project = projectWithVersion('8.0.0')
        addCliArtifactExtension(project, 'grails-core-custom-cli')

        expect:
        SbomPlugin.cliCompanionArtifactId(project).get() == 'grails-core-custom-cli'
    }

    void "a custom cli artifactId flows through to the cli sbom file name"() {
        given: "a customised companion coordinate"
        Project project = projectWithVersion('8.0.0')
        addCliArtifactExtension(project, 'grails-core-custom-cli')

        when: "the cli sbom output location is derived from that coordinate"
        def location = SbomPlugin.sbomOutputLocationFor(project, SbomPlugin.cliCompanionArtifactId(project))

        then: "the sbom file name tracks the custom name, not the <project>-cli default"
        location.get().asFile.name == 'grails-core-custom-cli-8.0.0-sbom.json'
    }

    void "a category-X license is permitted when exempted for the sbom component (cli companion)"() {
        expect: "the hibernate 5 LGPL exemption keyed to the dbmigration cli companion is honoured"
        SbomPlugin.pickLicense(LOGGER, 'grails-data-hibernate5-dbmigration',
                'grails-data-hibernate5-dbmigration-cli', HIBERNATE_COMMONS, lgplChoice()) != null
    }

    void "an unexempted category-X license fails naming the sbom component, not just the project"() {
        when: "the same LGPL dependency appears in an sbom component with no matching exemption"
        SbomPlugin.pickLicense(LOGGER, 'grails-data-hibernate5-dbmigration',
                'grails-data-hibernate5-dbmigration', HIBERNATE_COMMONS, lgplChoice())

        then: "the failure identifies the offending sbom component"
        GradleException e = thrown(GradleException)
        e.message.contains('grails-data-hibernate5-dbmigration')
        e.message.contains('LGPL-2.1-only')
    }
}
