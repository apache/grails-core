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
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class GrailsDependencyValidatorPluginSpec extends Specification {

    private static Project rootWithBoms() {
        Project root = ProjectBuilder.builder().withName('root').build()
        ProjectBuilder.builder().withName('grails-bom').withParent(root).build()
        ProjectBuilder.builder().withName('grails-micronaut-bom').withParent(root).build()
        ProjectBuilder.builder().withName('grails-hibernate7-bom').withParent(root).build()
        root
    }

    private static void addBomPlatform(Project project, String configuration, String bomPath) {
        project.configurations.maybeCreate(configuration)
        project.dependencies.add(configuration,
                project.dependencies.platform(project.dependencies.project(path: bomPath)))
    }

    void "detectBomPath ignores the documentation configuration when a variant BOM is used elsewhere"() {
        given: "a project that selects grails-micronaut-bom but inherits grails-bom on the shared documentation config"
        Project root = rootWithBoms()
        Project project = ProjectBuilder.builder().withName('grails-micronaut').withParent(root).build()
        addBomPlatform(project, 'api', ':grails-micronaut-bom')
        addBomPlatform(project, 'documentation', ':grails-bom')

        expect: "the variant BOM wins and no conflict is reported"
        GrailsDependencyValidatorPlugin.detectBomPath(project) == ':grails-micronaut-bom'
    }

    void "detectBomPath returns the single declared BOM"() {
        given: "a default-variant project with grails-bom on both a real config and the documentation config"
        Project root = rootWithBoms()
        Project project = ProjectBuilder.builder().withName('grails-core').withParent(root).build()
        addBomPlatform(project, 'implementation', ':grails-bom')
        addBomPlatform(project, 'documentation', ':grails-bom')

        expect:
        GrailsDependencyValidatorPlugin.detectBomPath(project) == ':grails-bom'
    }

    void "detectBomPath returns null when no Grails BOM is declared"() {
        given:
        Project root = rootWithBoms()
        Project project = ProjectBuilder.builder().withName('plain').withParent(root).build()
        project.configurations.maybeCreate('implementation')

        expect:
        GrailsDependencyValidatorPlugin.detectBomPath(project) == null
    }

    void "detectBomPath returns null when only the documentation tooling configuration declares a BOM"() {
        given: "a project whose sole BOM is the doc-tooling grails-bom on the documentation config"
        Project root = rootWithBoms()
        Project project = ProjectBuilder.builder().withName('docs-only').withParent(root).build()
        addBomPlatform(project, 'documentation', ':grails-bom')

        expect: "the doc-tooling BOM is ignored, so no project BOM is detected"
        GrailsDependencyValidatorPlugin.detectBomPath(project) == null
    }

    void "detectBomPath fails when two distinct Grails BOMs are declared on real configurations"() {
        given: "a genuine misconfiguration layering two variant BOMs on real dependency configurations"
        Project root = rootWithBoms()
        Project project = ProjectBuilder.builder().withName('misconfigured').withParent(root).build()
        addBomPlatform(project, 'api', ':grails-micronaut-bom')
        addBomPlatform(project, 'implementation', ':grails-hibernate7-bom')

        when:
        GrailsDependencyValidatorPlugin.detectBomPath(project)

        then:
        GradleException e = thrown(GradleException)
        e.message.contains('declares more than one Grails BOM')
    }
}
