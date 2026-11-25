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

    void "a file of the baseline that the application does not have is left out of what a feature changes, and the feature is added without it"() {
        given: 'an application without the README this version generates, and its record'
        ProjectGenerator generator = beanContext.getBean(ProjectGenerator)
        Path generated = create(generator, 'generated', [])
        Path project = withoutRecord(generated, 'app')
        Files.delete(project.resolve('README.md'))
        addition(generator).adopt(project, ApplicationType.WEB, 'shop', 'com.acme', OPTIONS, [], [], [], OperatingSystem.LINUX, []).apply()

        when:
        ChangePlan plan = planOf(generator, project)
        ChangePlan.PlannedFile readme = plan.files.find { ChangePlan.PlannedFile file -> file.path == 'README.md' }

        then: 'the file is no conflict: it is left out, with what the feature would have added to it and where its copy is'
        plan.conflicts.isEmpty()
        readme.action == ChangePlan.Action.LEAVE
        readme.reason.startsWith('the application does not have this file')
        readme.details.any { String line -> line.startsWith('the feature would have added ') }
        readme.details.any { String line -> line.contains('.grails/baseline/files/README.md') }
        plan.diff().any { String line -> line.startsWith('left out: README.md') }

        when:
        plan.apply()
        ProjectState state = ProjectState.read(project)

        then: 'the feature is added, the file is not written, and its copy in the baseline is the one of the generator with the feature'
        !Files.exists(project.resolve('README.md'))
        state.lock.featureNames.contains(FEATURE)
        Files.readString(project.resolve('build.gradle')).contains('quartz')
        Files.readString(project.resolve('.grails/baseline/files/README.md')).toLowerCase().contains('quartz')
        state.verifyBaseline(project)
        addition(generator).regenerate(project, OperatingSystem.LINUX).state.manifest.adopted

        when: 'the same file is taken out of an application that was generated'
        Files.delete(generated.resolve('README.md'))

        then: 'that is a change of the application, and a conflict as before'
        planOf(generator, generated).conflicts*.path == ['README.md']
    }

    void "a dependency a feature declares is put at the end of the dependencies block of a build that has them in an order of its own"() {
        given: 'an application whose dependencies are not those the generator writes'
        ProjectGenerator generator = beanContext.getBean(ProjectGenerator)
        Path generated = create(generator, 'generated', [])
        Path project = withoutRecord(generated, 'app')
        Path build = project.resolve('build.gradle')
        String text = Files.readString(build)
        int from = text.indexOf('\ndependencies {\n')
        int to = text.indexOf('\n}\n', from)
        String own = text.substring(0, from) + '\ndependencies {\n    runtimeOnly "com.h2database:h2"\n    implementation "org.apache.grails:grails-core"\n}\n' + text.substring(to + 3)
        Files.writeString(build, own)
        addition(generator).adopt(project, ApplicationType.WEB, 'shop', 'com.acme', OPTIONS, [], [], [], OperatingSystem.LINUX, []).apply()

        when:
        ChangePlan plan = planOf(generator, project)
        ChangePlan.PlannedFile planned = plan.files.find { ChangePlan.PlannedFile file -> file.path == 'build.gradle' }

        then: 'the line is placed, and that is said'
        plan.conflicts.isEmpty()
        planned.action == ChangePlan.Action.MERGE
        planned.details.any { String line -> line.contains('1 dependency at the end of the dependencies block') }

        when:
        plan.apply()

        then: 'every line of the application is where it was, and the dependency is the last of the block'
        Files.readString(build) == own.replace('    implementation "org.apache.grails:grails-core"\n}\n',
                '    implementation "org.apache.grails:grails-core"\n    implementation "org.apache.grails:grails-quartz"\n}\n')

        when: 'the same build in an application that was generated, where the place of a line is the one the generator gave it'
        Path tracked = copy(generated, 'tracked')
        Files.writeString(tracked.resolve('build.gradle'), own)

        then: 'is a conflict as before'
        planOf(generator, tracked).conflicts*.path == ['build.gradle']
    }

    void "a Gradle plugin a feature applies is put after the last plugin the build applies, and its dependencies at the end of theirs"() {
        given: 'an application that applies its plugins below its group, in another order'
        ProjectGenerator generator = beanContext.getBean(ProjectGenerator)
        Path project = withoutRecord(create(generator, 'generated', []), 'app')
        Path build = project.resolve('build.gradle')
        List<String> lines = Files.readAllLines(build)
        List<String> applied = lines.findAll { String line -> line.startsWith('apply plugin:') }
        lines.removeAll(applied)
        lines.addAll(lines.findIndexOf { String line -> line.startsWith('group') } + 1, [''] + applied.reverse())
        String own = lines.join('\n') + '\n'
        Files.writeString(build, own)
        addition(generator).adopt(project, ApplicationType.WEB, 'shop', 'com.acme', OPTIONS, [], [], [], OperatingSystem.LINUX, []).apply()

        when:
        ChangePlan plan = planOf(generator, project, ['views-json'])
        ChangePlan.PlannedFile planned = plan.files.find { ChangePlan.PlannedFile file -> file.path == 'build.gradle' }

        then:
        plan.conflicts.isEmpty()
        planned.details.any { String line -> line.contains('1 apply line after the last line of the file that applies a plugin') }

        when:
        plan.apply()
        List<String> after = Files.readAllLines(build)

        then: 'every line of the build is where it was, and the plugin follows the last one that was applied'
        after.findAll { String line -> own.readLines().contains(line) } == own.readLines()
        after[after.indexOf(applied.first()) + 1] == 'apply plugin: "org.apache.grails.gradle.grails-gson"'
    }

    void "a plugin a feature names by its id goes to the end of the plugins block, or into one of its own after the buildscript block"() {
        given: 'an application whose plugins block has a line of its own, and one that has no plugins block'
        ProjectGenerator generator = beanContext.getBean(ProjectGenerator)
        Path generated = create(generator, 'generated', [])
        Path project = withoutRecord(generated, 'app')
        Path build = project.resolve('build.gradle')
        String text = Files.readString(build)
        int from = text.indexOf('\nplugins {\n')
        int to = text.indexOf('\n}\n', from) + 3
        String block = text.substring(from, to)
        // every line of the block is the project's own, so that no line of the generated block is there to put the plugin next to
        Files.writeString(build, text.replace(block, '\nplugins {\n    id("war")\n    id("jacoco")\n}\n'))
        Path without = withoutRecord(generated, 'without')
        Files.writeString(without.resolve('build.gradle'), text.replace(block, '\n'))
        addition(generator).adopt(project, ApplicationType.WEB, 'shop', 'com.acme', OPTIONS, [], [], [], OperatingSystem.LINUX, []).apply()
        addition(generator).adopt(without, ApplicationType.WEB, 'shop', 'com.acme', OPTIONS, [], [], [], OperatingSystem.LINUX, []).apply()

        when:
        ChangePlan plan = planOf(generator, project, ['asciidoctor'])
        plan.apply()

        then: 'it is the last of the block'
        plan.conflicts.isEmpty()
        Files.readString(build).contains('    id("jacoco")\n    id "org.asciidoctor.jvm.convert" version "4.0.5"\n}\n')
        Files.readString(build).contains('\napply from: "gradle/asciidoc.gradle"\n')

        when:
        plan = planOf(generator, without, ['asciidoctor'])
        plan.apply()
        List<String> after = Files.readAllLines(without.resolve('build.gradle'))

        then: 'a block of its own follows the buildscript block, where Gradle takes one'
        plan.conflicts.isEmpty()
        plan.files.find { ChangePlan.PlannedFile file -> file.path == 'build.gradle' }.details.any { String line ->
            line.contains('1 plugin in a plugins block of its own after the buildscript block, where Gradle takes one')
        }
        after.indexOf('plugins {') > after.indexOf('buildscript {')
        after[after.indexOf('plugins {') + 1] == '    id "org.asciidoctor.jvm.convert" version "4.0.5"'
        after[after.indexOf('plugins {') + 2] == '}'
        after.indexOf('plugins {') < after.findIndexOf { String line -> line.startsWith('apply plugin:') }
    }

    void "a dependency a feature takes the place of is taken out where the build has its very line, and is a conflict where it has not"() {
        given: 'an application with its dependencies in an order of its own'
        ProjectGenerator generator = beanContext.getBean(ProjectGenerator)
        Path generated = create(generator, 'generated', [])
        Path project = withoutRecord(generated, 'app')
        Path build = project.resolve('build.gradle')
        List<String> lines = Files.readAllLines(build)
        int from = lines.indexOf('dependencies {')
        int to = (from..<lines.size()).find { int index -> lines[index] == '}' }
        lines = lines.subList(0, from + 1) + lines.subList(from + 1, to).reverse() + lines.subList(to, lines.size())
        Files.writeString(build, lines.join('\n') + '\n')
        Path other = withoutRecord(generated, 'other')
        Files.writeString(other.resolve('build.gradle'), (lines.join('\n') + '\n').replace('runtimeOnly "com.h2database:h2"', 'runtimeOnly "com.h2database:h2:2.3.232"'))
        addition(generator).adopt(project, ApplicationType.WEB, 'shop', 'com.acme', OPTIONS, [], [], [], OperatingSystem.LINUX, []).apply()
        addition(generator).adopt(other, ApplicationType.WEB, 'shop', 'com.acme', OPTIONS, [], [], [], OperatingSystem.LINUX, []).apply()

        when:
        ChangePlan plan = planOf(generator, project, ['postgres'])
        plan.apply()
        List<String> after = Files.readAllLines(build)

        then: 'the driver of the default is gone, and the one of the feature is the last of the block'
        plan.conflicts.isEmpty()
        !after.contains('    runtimeOnly "com.h2database:h2"')
        after.contains('    runtimeOnly "org.postgresql:postgresql"')
        (lines - ['    runtimeOnly "com.h2database:h2"']).every { String line -> after.contains(line) }

        expect: 'a line of the default that is written otherwise is not taken for it'
        planOf(generator, other, ['postgres']).conflicts*.path == ['build.gradle']
    }

    void "a build whose dependencies block cannot be told is a conflict, and nothing is written"() {
        given: 'two dependencies blocks'
        ProjectGenerator generator = beanContext.getBean(ProjectGenerator)
        Path project = withoutRecord(create(generator, 'generated', []), 'app')
        Path build = project.resolve('build.gradle')
        String text = Files.readString(build)
        int from = text.indexOf('\ndependencies {\n')
        Files.writeString(build, text.substring(0, from) + '\ndependencies {\n    implementation "org.apache.grails:grails-core"\n}\n\ndependencies {\n}\n')
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
