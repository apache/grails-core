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

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

/**
 * Unit tests for {@link TestPhasesGradlePlugin}, covering how a phase's {@code sourceFolderName}
 * reaches the source set it creates.
 *
 * @since 8.0
 */
class TestPhasesGradlePluginSpec extends Specification {

    @TempDir
    File projectDir

    private Project project

    private NamedDomainObjectContainer<TestPhase> testPhases

    def setup() {
        project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        project.pluginManager.apply('groovy')
        project.pluginManager.apply(TestPhasesGradlePlugin)
        testPhases = project.extensions.getByName(TestPhasesGradlePlugin.EXTENSION_NAME)
                as NamedDomainObjectContainer<TestPhase>
    }

    /** Canonical paths: the temp dir arrives as /var/... but Gradle resolves it to /private/var/... */
    private Set<String> groovySourceDirsOf(String phaseName) {
        SourceSetContainer sourceSets = project.extensions.getByType(SourceSetContainer)
        SourceSet phaseSourceSet = sourceSets.getByName(phaseName)
        phaseSourceSet.groovy.srcDirs.collect { File it -> it.canonicalPath }.toSet()
    }

    private String pathOf(String relative) {
        new File(projectDir, relative).canonicalPath
    }

    void 'a phase derives its source folder from its name by default'() {
        given:
        new File(projectDir, 'src/functional-test/groovy').mkdirs()

        when:
        TestPhase phase = project.objects.newInstance(TestPhase, 'functionalTest')
        testPhases.add(phase)

        then:
        groovySourceDirsOf('functionalTest').contains(pathOf('src/functional-test/groovy'))
    }

    void 'a sourceFolderName set before the phase is added overrides the derived default'() {
        given: 'a folder that the name-derived convention would never point at'
        new File(projectDir, 'src/custom-cli-phase/groovy').mkdirs()

        when:
        TestPhase phase = project.objects.newInstance(TestPhase, 'integrationTestCli')
        phase.sourceFolderName.set('src/custom-cli-phase')
        testPhases.add(phase)

        then: 'the override is what the source set is built from'
        groovySourceDirsOf('integrationTestCli').contains(pathOf('src/custom-cli-phase/groovy'))

        and: 'the derived default is not used'
        !groovySourceDirsOf('integrationTestCli').contains(pathOf('src/integration-test-cli/groovy'))
    }

    void 'a sourceFolderName set from a create action arrives too late to be read'() {
        given: 'the container fires configureEach during add(), before any create action runs'
        new File(projectDir, 'src/custom-cli-phase/groovy').mkdirs()
        new File(projectDir, 'src/integration-test-cli/groovy').mkdirs()

        when: 'the override is expressed the way create(name, action) invites'
        testPhases.create('integrationTestCli') { TestPhase phase ->
            phase.sourceFolderName.set('src/custom-cli-phase')
        }

        then: 'the property holds the override afterwards, so the mistake is invisible from the outside'
        testPhases.getByName('integrationTestCli').sourceFolderName.get() == 'src/custom-cli-phase'

        and: 'but the source set was already built from the derived default - hence add(), not create()'
        groovySourceDirsOf('integrationTestCli').contains(pathOf('src/integration-test-cli/groovy'))
        !groovySourceDirsOf('integrationTestCli').contains(pathOf('src/custom-cli-phase/groovy'))
    }
}
