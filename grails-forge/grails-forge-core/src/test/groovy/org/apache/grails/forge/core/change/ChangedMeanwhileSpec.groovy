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

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import org.apache.grails.forge.core.pack.Pack
import org.apache.grails.forge.core.state.ProjectState
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.OperatingSystem
import org.grails.forge.application.Project
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.feature.AvailableFeatures
import org.grails.forge.io.OutputHandler
import spock.lang.Unroll

/**
 * A project that another command changes while a change of it is worked out: what is written is
 * held to the record the change was worked out from, so that the change is refused and the project
 * is what the other command made of it. The other command is a step of the generator here, which
 * runs at a known point between the reading of the record and the writing.
 */
class ChangedMeanwhileSpec extends AbstractFeatureAdditionSpec {

    static final String EARLIER = '1.0.0'
    static final String PREPARED = ' was changed while the change of the project was being prepared; nothing of the project was changed'

    void "a record that gets a feature while it is moved to this generator is not moved, and stays the record with the feature"() {
        given: 'an application of an earlier version, and the same application with a feature added to it'
        ProjectGenerator earlier = generator()
        Path project = create(earlier, 'app', [])
        Path withFeature = copy(project, 'other')
        planOf(earlier, withFeature).apply()
        writtenByEarlier(project)
        writtenByEarlier(withFeature)

        and: 'a generator of this version that writes one file more, during whose work the feature is added to the application'
        boolean added = false
        ProjectGenerator now = generator { GeneratorContext context ->
            if (!added) {
                added = true
                put(withFeature, project)
            }
            context.addTemplate('extra', new Fixed('extra.txt', 'new\n'.bytes, false, false))
        }

        when:
        RecordMigration migration = addition(now).migrate(project, OperatingSystem.LINUX, [])

        then: 'what the move comes to was worked out from the record without the feature'
        added
        migration.outcome == RecordMigration.Outcome.MOVED
        migration.added == ['extra.txt']

        when:
        migration.apply()

        then: 'and is refused, since that record is not the one the project has'
        ProjectChangeException e = thrown()
        e.message.endsWith(PREPARED)

        and: 'the project is the one with the feature, whose record reads and whose baseline is what its index says'
        tree(project) == tree(withFeature)
        ProjectState state = ProjectState.read(project)
        state.verifyBaseline(project)
        state.manifest.requestedFeatures*.name == [FEATURE]
        state.lock.generatorVersion == EARLIER
        journals().isEmpty()

        when: 'the record is moved again, now from what the project has'
        RecordMigration again = addition(now).migrate(project, OperatingSystem.LINUX, [])
        again.apply()
        ProjectState moved = ProjectState.read(project)

        then:
        moved.verifyBaseline(project)
        moved.manifest.requestedFeatures*.name == [FEATURE]
        moved.baseline.find('extra.txt') != null
    }

    @Unroll
    void "a project that gets a feature while another is added to it does not get the other, whenever the first was added: #moment"() {
        given: 'an application, and the same application with a feature added to it'
        ProjectGenerator plain = generator()
        Path project = create(plain, 'app', [])
        Path withFeature = copy(project, 'other')
        planOf(plain, withFeature).apply()

        and: 'a generator during whose work that feature is added to the application'
        boolean added = false
        Closure other = {
            if (!added) {
                added = true
                put(withFeature, project)
            }
        }
        ProjectGenerator generator = new Meanwhile(real: plain, listing: listing ? other : { }, generating: listing ? { } : other)

        when:
        ChangePlan plan = planOf(generator, project, ['cache'])

        then: 'the plan is that of the record without the first feature'
        added
        plan.added == ['cache']

        when:
        plan.apply()

        then: 'and is not written: the record is another, and so can be the files the two features both change'
        Exception e = thrown()
        plan.conflicts.isEmpty() ? e instanceof ProjectChangeException && e.message.endsWith(PREPARED) : e instanceof IllegalStateException
        tree(project) == tree(withFeature)
        ProjectState.read(project).manifest.requestedFeatures*.name == [FEATURE]

        when: 'added again, to what the project has'
        planOf(plain, project, ['cache']).apply()
        ProjectState state = ProjectState.read(project)

        then:
        state.verifyBaseline(project)
        state.manifest.requestedFeatures*.name.sort() == ['cache', FEATURE]

        where:
        moment                                                         | listing
        'after the record was read and before it was read for digests' | true
        'while the application was generated'                          | false
    }

    // what the other command leaves of a project: every file it has, in the place of the one that is there
    private static void put(Path from, Path to) {
        Files.walkFileTree(from, new SimpleFileVisitor<Path>() {

            @Override
            FileVisitResult preVisitDirectory(Path each, BasicFileAttributes attributes) {
                Files.createDirectories(to.resolve(from.relativize(each).toString()))
                return FileVisitResult.CONTINUE
            }

            @Override
            FileVisitResult visitFile(Path each, BasicFileAttributes attributes) {
                Files.copy(each, to.resolve(from.relativize(each).toString()), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
                return FileVisitResult.CONTINUE
            }
        })
    }

    // the record as an earlier version of the generator wrote it: its version in the lock, and the seal of the lock
    private static void writtenByEarlier(Path project) {
        File lock = project.resolve(ProjectState.LOCK).toFile()
        String body = lock.text.replaceFirst(/(?m)^(generator:\n  name: grails-forge\n  version: )[^\n]*\n/, '$1' + EARLIER + '\n')
        body = body.substring(0, body.lastIndexOf('seal:\n'))
        lock.text = body + "seal:\n  sha256: ${sha(body.bytes)}\n"
        assert ProjectState.read(project).lock.generatorVersion == EARLIER
    }

    static class Meanwhile implements ProjectGenerator {

        @Delegate
        ProjectGenerator real
        Closure listing
        Closure generating

        @Override
        AvailableFeatures availableFeatures(ApplicationType applicationType, Collection<Pack> packs) {
            AvailableFeatures available = real.availableFeatures(applicationType, packs)
            listing.call()
            return available
        }

        @Override
        void generate(ApplicationType applicationType, Project project, OutputHandler outputHandler, GeneratorContext generatorContext) throws Exception {
            generating.call()
            real.generate(applicationType, project, outputHandler, generatorContext)
        }
    }
}
