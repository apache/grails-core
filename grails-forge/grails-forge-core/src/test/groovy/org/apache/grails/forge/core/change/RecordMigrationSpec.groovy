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
import org.apache.grails.forge.core.pack.PackDescriptor
import org.apache.grails.forge.core.state.GenerationOptions
import org.apache.grails.forge.core.state.ProjectLock
import org.apache.grails.forge.core.state.ProjectLock.LockedRepository
import org.apache.grails.forge.core.state.ProjectState
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.OperatingSystem
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.GormImpl
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.ServletImpl
import org.grails.forge.util.VersionInfo
import spock.lang.Unroll

/**
 * Moving the record of a generated application to this generator: which records are moved, what the
 * baseline becomes, and why a record is not moved, as the codes a tool reads.
 */
class RecordMigrationSpec extends AbstractFeatureAdditionSpec {

    static final String EARLIER = '1.0.0'

    @Unroll
    void "the record of #generator #version for Grails #grails, of the build #build, is moved to this generator: #movable"() {
        expect:
        RecordMigration.isMovable(lock(generator, version, grails, build)) == movable

        where:
        generator      | version                  | grails                        | build     || movable
        'grails-forge' | EARLIER                  | EARLIER                       | null      || true
        'grails-forge' | EARLIER                  | VersionInfo.grailsVersion     | 'ab' * 32 || true
        'grails-forge' | later()                  | later()                       | null      || false
        'grails-forge' | EARLIER                  | later()                       | null      || false
        'grails-forge' | 'nightly'                | VersionInfo.grailsVersion     | null      || false
        'acme-forge'   | EARLIER                  | EARLIER                       | null      || false
        'grails-forge' | VersionInfo.version      | VersionInfo.grailsVersion     | null      || false
        'grails-forge' | VersionInfo.version      | VersionInfo.grailsVersion     | VersionInfo.generatorSha256 || false
    }

    void "the record of another build of this version is moved when both name their builds"() {
        expect:
        RecordMigration.isMovable(lock('grails-forge', VersionInfo.version, VersionInfo.grailsVersion, 'ab' * 32)) == (VersionInfo.generatorSha256 != null)
    }

    void "files the earlier generator wrote and this one does not, and those it did not write, are taken out of the baseline and put into it, and no file of the application is written"() {
        given: 'an application whose earlier generator wrote three files this one does not'
        Path project = create(leavingFilesOut(), 'app', [])
        writtenByEarlier(project)
        Map<String, String> before = tree(project)
        ProjectGenerator now = generator { GeneratorContext context ->
            context.addTemplate('extra', new Fixed('extra.txt', 'new\n'.bytes, false, false))
        }

        when:
        RecordMigration migration = addition(now).migrate(project, OperatingSystem.LINUX, [])

        then: 'what would change is said, and nothing is written'
        migration.outcome == RecordMigration.Outcome.MOVED
        migration.recordedVersion == EARLIER
        migration.added == ['extra.txt']
        migration.removed == ['kept/stays.txt', 'old/only/gone.bin', 'only-without.txt']
        migration.changed == []
        tree(project) == before

        when:
        migration.apply()
        ProjectState state = ProjectState.read(project)

        then: 'the files of the application are as they were, and the record is that of this generator'
        tree(project).findAll { !it.key.startsWith('.grails') } == before.findAll { !it.key.startsWith('.grails') }
        state.verifyBaseline(project)
        state.lock.generatorVersion == VersionInfo.version
        state.baseline.find('extra.txt') != null
        state.baseline.find('only-without.txt') == null
        !Files.exists(project.resolve('.grails/baseline/files/old'))
        Files.exists(project.resolve('old/only/gone.bin'))
        journals().isEmpty()

        and: 'the application is generated again by this generator from the record'
        addition(now).regenerate(project, OperatingSystem.LINUX).paths.contains('extra.txt')
    }

    void "the record this generator wrote is not moved: there is nothing to move, and nothing is written"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        Map<String, String> before = tree(project)

        when:
        RecordMigration migration = addition(generator).migrate(project, OperatingSystem.LINUX, [])
        migration.apply()

        then:
        migration.nothingToMove
        migration.outcome in [RecordMigration.Outcome.SAME_BUILD, RecordMigration.Outcome.SAME_FILES]
        tree(project) == before
    }

    @Unroll
    void "a record #what is not moved, with the reason #reason"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [FEATURE])
        change.call(project)
        Map<String, String> before = tree(project)

        when:
        addition(generator).migrate(project, OperatingSystem.LINUX, [])

        then:
        ProjectChangeException e = thrown()
        e.reason == reason
        e.message.contains(message)
        e.message.endsWith('Nothing was changed')
        tree(project) == before

        where:
        what                                          | change                                                     || reason                                  | message
        'of a later version'                          | { Path it -> rewriteLock(it, later(), false) }       || ProjectChangeException.OTHER_GENERATOR  | 'a record is moved to a later version, and not to an earlier one'
        'generated with other repositories'           | { Path it -> rewriteLock(it, EARLIER, true) }        || ProjectChangeException.RECORD_DIFFERS   | 'Run the command with GRAILS_REPO_URL as it was when the application was generated'
        'that names a feature this version has not'   | { Path it -> renameFeature(it); writtenByEarlier(it) }     || ProjectChangeException.NOT_REGENERABLE  | 'The record names the feature acme-scheduler'
    }

    void "a project that is half changed by an interrupted change is not moved, with the reason interrupted-change"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        writtenByEarlier(project)
        try {
            addition(changingIcon()).migrate(project, OperatingSystem.LINUX, []).apply { String path ->
                if (path == ProjectState.BASELINE_INDEX) {
                    throw new Stopped()
                }
            }
        } catch (Stopped ignored) {
        }

        when:
        addition(generator).migrate(project, OperatingSystem.LINUX, [])

        then:
        ProjectChangeException e = thrown()
        e.reason == ProjectChangeException.INTERRUPTED_CHANGE

        when: 'the change is undone'
        ProjectRecovery recovery = addition(generator).recover(project)

        then:
        recovery.undone
        addition(generator).migrate(project, OperatingSystem.LINUX, []).outcome == RecordMigration.Outcome.MOVED
    }

    private static String later() {
        return "${Integer.parseInt(VersionInfo.version.takeWhile { it != '.' }) + 1}.0.0".toString()
    }

    private static ProjectLock lock(String generator, String version, String grails, String build) {
        return new ProjectLock('ab' * 32, generator, version, build, grails, PackDescriptor.TEMPLATE_API, ApplicationType.WEB,
                new GenerationOptions(JdkVersion.DEFAULT_OPTION, ServletImpl.DEFAULT_OPTION, GormImpl.DEFAULT_OPTION, DevelopmentReloading.DEFAULT_OPTION),
                ProjectLock.LINE_ENDINGS_OF_TEMPLATES, [], 0, [], [:], 'cd' * 32)
    }

    // the record as an earlier version of the generator wrote it: its version in the lock, and the seal of the lock
    private static void writtenByEarlier(Path project) {
        rewriteLock(project, EARLIER, false)
    }

    private static void rewriteLock(Path project, String version, boolean otherRepositories) {
        File lock = project.resolve(ProjectState.LOCK).toFile()
        String body = lock.text.replaceFirst(/(?m)^(generator:\n  name: grails-forge\n  version: )[^\n]*\n/, '$1' + version + '\n')
        if (otherRepositories) {
            ProjectLock read = ProjectState.read(project).lock
            Files.write(project.resolve(ProjectState.LOCK), new ProjectLock(read.manifestSha256, read.generatorName, version, read.generatorSha256, read.grailsVersion,
                    read.templateApi, read.applicationType, read.options, read.lineEndings, [new LockedRepository(LockedRepository.REMOTE, 'https://repo.example.org/maven')],
                    0, read.features, read.parameters, read.baselineIndexSha256).toBytes())
            return
        }
        if (version != VersionInfo.version && version != EARLIER) {
            // a later version of Grails, in the manifest as well, with the digest the lock has of it
            File manifest = project.resolve(ProjectState.MANIFEST).toFile()
            manifest.text = manifest.text.replace("grailsVersion: ${VersionInfo.grailsVersion}\n", "grailsVersion: ${version}\n")
            body = body.replace("\ngrailsVersion: ${VersionInfo.grailsVersion}\n", "\ngrailsVersion: ${version}\n")
                    .replaceFirst('manifest:\n  sha256: [0-9a-f]{64}', "manifest:\n  sha256: ${sha(manifest.bytes)}")
        }
        body = body.substring(0, body.lastIndexOf('seal:\n'))
        lock.text = body + "seal:\n  sha256: ${sha(body.bytes)}\n"
        assert ProjectState.read(project).lock.generatorVersion == version
    }

    // the feature of the record as an earlier version that named it otherwise wrote it, in the manifest and the lock
    private static void renameFeature(Path project) {
        File manifest = project.resolve(ProjectState.MANIFEST).toFile()
        File lock = project.resolve(ProjectState.LOCK).toFile()
        manifest.text = manifest.text.replace("- name: ${FEATURE}\n", '- name: acme-scheduler\n')
        String body = lock.text.replace("- name: ${FEATURE}\n", '- name: acme-scheduler\n')
                .replaceFirst('manifest:\n  sha256: [0-9a-f]{64}', "manifest:\n  sha256: ${sha(manifest.bytes)}")
        body = body.substring(0, body.lastIndexOf('seal:\n'))
        lock.text = body + "seal:\n  sha256: ${sha(body.bytes)}\n"
    }
}
