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
package org.apache.grails.forge.core.change

import java.nio.file.Files
import java.nio.file.Path
import org.apache.grails.forge.core.state.AdoptedProjectRecord
import org.apache.grails.forge.core.state.GenerationOptions
import org.apache.grails.forge.core.state.ProjectLock.LockedFeature
import org.apache.grails.forge.core.state.ProjectManifest
import org.apache.grails.forge.core.state.ProjectState
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.OperatingSystem
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.feature.FeatureSelectionException
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.GormImpl
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.ServletImpl

/**
 * The record of an application that was there before it had one, written with the baseline the
 * generator makes of what the application is taken to be: what it has, that a feature is added
 * against it with the files of the application kept, and that it stays such a record.
 */
class RecordAdoptionSpec extends AbstractFeatureAdditionSpec {

    static final GenerationOptions OPTIONS = new GenerationOptions(JdkVersion.DEFAULT_OPTION, ServletImpl.TOMCAT, GormImpl.HIBERNATE5, DevelopmentReloading.DEVTOOLS)

    void "the record has the baseline the generator writes for the choices, says that it was adopted, and no file of the application is written"() {
        given: 'an application as the generator makes it, with a line of its own, and without its record'
        ProjectGenerator generator = beanContext.getBean(ProjectGenerator)
        Path generated = create(generator, 'generated', [])
        Path project = withoutRecord(generated, 'app')
        Path build = project.resolve('build.gradle')
        Files.writeString(build, Files.readString(build) + '\n// a line of the application\n')
        Files.delete(project.resolve('grails-app/i18n/messages.properties'))
        Map<String, String> before = tree(project)

        when:
        RecordAdoption adoption = addition(generator).adopt(project, ApplicationType.WEB, 'shop', 'com.acme', OPTIONS, [], [], [], OperatingSystem.LINUX, [])

        then: 'nothing is written by working it out'
        tree(project) == before
        adoption.record.manifest.adopted
        adoption.record.manifest.hasBaseline()
        !adoption.record.manifest.tracked
        adoption.otherwise == ['build.gradle']
        adoption.absent == ['grails-app/i18n/messages.properties']
        adoption.asGenerated.size() == adoption.record.baseline.files.size() - 2
        adoption.written.last() == ProjectState.MANIFEST

        when:
        adoption.apply()
        ProjectState state = ProjectState.read(project)
        state.verifyBaseline(project)

        then: 'the record is the one of the generated application but for what says that it was adopted, and the application is as it was'
        state.manifest.generationState == ProjectManifest.ADOPTED
        Files.readString(project.resolve(ProjectState.MANIFEST)) ==
                Files.readString(generated.resolve(ProjectState.MANIFEST)).replace('generationState: tracked\n', 'generationState: adopted\n')
        Files.readAllBytes(project.resolve(ProjectState.BASELINE_INDEX)) == Files.readAllBytes(generated.resolve(ProjectState.BASELINE_INDEX))
        state.lock.featureNames == ProjectState.read(generated).lock.featureNames
        differing(tree(project), before).every { String path -> path.startsWith('.grails') }
        left(project).isEmpty()

        when: 'a feature is added'
        ChangePlan plan = planOf(generator, project)
        plan.apply()
        ProjectState after = ProjectState.read(project)

        then: 'the line of the application is kept, the file it removed stays away, and the record is still an adopted one'
        plan.conflicts.isEmpty()
        Files.readString(build).contains('quartz')
        Files.readString(build).endsWith('\n// a line of the application\n')
        !Files.exists(project.resolve('grails-app/i18n/messages.properties'))
        after.manifest.adopted
        after.manifest.requestedFeatures*.name == [FEATURE]
        after.lock.featureNames.contains(FEATURE)
        after.verifyBaseline(project)

        and: 'it is generated again as its record says, and held to the baseline'
        addition(generator).regenerate(project, OperatingSystem.LINUX).state.manifest.adopted

        and: 'the record of this build is that of this version already'
        addition(generator).migrate(project, OperatingSystem.LINUX, []).outcome == RecordMigration.Outcome.SAME_BUILD
    }

    void "a feature that was found is asked for by name only where the choices do not bring it"() {
        given:
        ProjectGenerator generator = beanContext.getBean(ProjectGenerator)
        Path project = withoutRecord(create(generator, 'generated', [FEATURE]), 'app')
        List<String> byDefault = ProjectState.read(directory.resolve('generated')).lock.featureNames - [FEATURE]

        when: 'every feature the application has is given as found'
        RecordAdoption adoption = addition(generator).adopt(project, ApplicationType.WEB, 'shop', 'com.acme', OPTIONS, [], byDefault + [FEATURE], [], OperatingSystem.LINUX, [])

        then: 'only the one the type and the options do not bring is asked for, and every file is as generated'
        adoption.record.manifest.requestedFeatures*.name == [FEATURE]
        adoption.otherwise.isEmpty()
        adoption.absent.isEmpty()
    }

    void "a record that was written is written anew, with the copies the new one does not have removed"() {
        given:
        ProjectGenerator generator = beanContext.getBean(ProjectGenerator)
        Path project = withoutRecord(create(generator, 'generated', []), 'app')
        addition(generator).adopt(project, ApplicationType.WEB, 'shop', 'com.acme', OPTIONS, [FEATURE], [], [], OperatingSystem.LINUX, []).apply()

        when: 'as an application of another type, without the feature'
        addition(generator).adopt(project, ApplicationType.REST_API, 'shop', 'com.acme', OPTIONS, [], [], [], OperatingSystem.LINUX, []).apply()
        ProjectState state = ProjectState.read(project)

        then: 'the baseline is the one of the new choices and nothing else'
        state.manifest.applicationType == ApplicationType.REST_API
        state.manifest.adopted
        !state.lock.featureNames.contains(FEATURE)
        state.verifyBaseline(project)

        when: 'over a record without a baseline'
        Path other = withoutRecord(directory.resolve('generated'), 'other')
        AdoptedProjectRecord.files(ApplicationType.WEB, 'shop', 'com.acme', OPTIONS, [], [], [new LockedFeature('cache', LockedFeature.BUNDLED_PACK)]).each { String path, byte[] content ->
            Files.createDirectories(other.resolve(path).parent)
            Files.write(other.resolve(path), content)
        }
        addition(generator).adopt(other, ApplicationType.WEB, 'shop', 'com.acme', OPTIONS, [], [], [], OperatingSystem.LINUX, []).apply()

        then:
        ProjectState.read(other).manifest.adopted
        ProjectState.read(other).verifyBaseline(other)
    }

    void "the record of a generated application is not written over, and choices no application is created with are refused"() {
        given:
        ProjectGenerator generator = beanContext.getBean(ProjectGenerator)
        Path generated = create(generator, 'generated', [])
        Map<String, String> before = tree(generated)

        when:
        addition(generator).adopt(generated, ApplicationType.WEB, 'shop', 'com.acme', OPTIONS, [], [], [], OperatingSystem.LINUX, [])

        then:
        ProjectChangeException e = thrown()
        e.reason == ProjectChangeException.GENERATED_RECORD
        tree(generated) == before

        when: 'a feature there is not'
        addition(generator).adopt(withoutRecord(generated, 'app'), ApplicationType.WEB, 'shop', 'com.acme', OPTIONS, ['no-such-feature'], [], [], OperatingSystem.LINUX, [])

        then:
        thrown(FeatureSelectionException)
    }

    void "what the application has otherwise in the lines a feature changes is a conflict, and nothing is written"() {
        given: 'an application whose dependencies are not those the generator writes'
        ProjectGenerator generator = beanContext.getBean(ProjectGenerator)
        Path project = withoutRecord(create(generator, 'generated', []), 'app')
        Path build = project.resolve('build.gradle')
        String text = Files.readString(build)
        int from = text.indexOf('\ndependencies {\n')
        Files.writeString(build, text.substring(0, from) + '\ndependencies {\n    implementation "org.apache.grails:grails-core"\n}\n')
        addition(generator).adopt(project, ApplicationType.WEB, 'shop', 'com.acme', OPTIONS, [], [], [], OperatingSystem.LINUX, []).apply()
        Map<String, String> before = tree(project)

        when:
        ChangePlan plan = planOf(generator, project)

        then:
        plan.conflicts*.path == ['build.gradle']
        tree(project) == before
    }

    // the files of an application without the record of how it was generated
    private Path withoutRecord(Path generated, String into) {
        Path project = copy(generated, into)
        project.resolve('.grails').toFile().deleteDir()
        return project
    }
}
