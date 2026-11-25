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
package org.apache.grails.forge.cli.health

import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.console.EarlierGrailsProject
import org.apache.grails.forge.cli.console.ProjectEvidence
import org.apache.grails.forge.core.change.RecordMigration
import org.apache.grails.forge.core.state.ProjectLock
import org.apache.grails.forge.core.state.ProjectManifest
import org.apache.grails.forge.core.state.ProjectState
import org.apache.grails.forge.core.state.ProjectStateException
import org.grails.forge.util.VersionInfo

/**
 * {@code record}: the record of the project under {@code .grails}, which the commands that create
 * its classes, {@code feature add} and {@code project regenerate} read: whether there is one, whether
 * it reads, whether its baseline is what its index says, whether it is the record of this version,
 * whether it is of the version of Grails the project is on, and whether a change of the project was
 * interrupted. A project that an earlier version of Forge generated has {@code grails-forge-cli.yml}
 * instead, which the commands read as well.
 *
 * @since 9.0
 */
@CompileStatic
final class RecordCheck extends HealthCheck {

    private static final String TRANSACTIONS = '.grails/transactions'
    // the lock that a change takes on the project, which is there between changes as well
    private static final String TRANSACTION_LOCK = 'lock'
    private static final int MAX_NAMED = 5

    @Override
    String getName() {
        return 'record'
    }

    @Override
    List<HealthFinding> check(HealthContext context) {
        if (!context.hasRecordFiles()) {
            return withoutRecord(context)
        }
        List<HealthFinding> findings = []
        ProjectState selection = context.selection
        boolean movable = selection != null && selection.manifest.hasBaseline() && RecordMigration.isMovable(selection.lock)
        String migrate = context.toType('project migrate')
        if (movable) {
            // moving the record writes its baseline anew, so what else is wrong with it is for this version to say afterwards
            ProjectLock lock = selection.lock
            boolean otherBuild = lock.generatorVersion == VersionInfo.getVersion()
            findings << found('earlier-generator', HealthLevel.WARNING, (otherBuild ? "The record was written by another build of version ${lock.generatorVersion} of " +
                    'the command line than this one' : "The record was written by version ${lock.generatorVersion} of the command line, and this is ${VersionInfo.getVersion()}") +
                    ': feature add and project regenerate refuse it until it is moved', "${migrate} moves it to this version, and changes no file of the project outside .grails",
                    migrate, [ProjectState.LOCK], NOTE_RECORD)
        } else if (context.recordFailure != null) {
            findings << found('unreadable', HealthLevel.WARNING, "The record of the project does not read: ${context.recordFailure}",
                    'Do as the message says; a record that was changed by hand is restored from version control (git restore --source=HEAD -- .grails)', null,
                    [ProjectState.MANIFEST, ProjectState.LOCK, ProjectState.BASELINE_INDEX], NOTE_RECORD)
        } else if (context.record.manifest.hasBaseline()) {
            try {
                context.record.verifyBaseline(context.directory)
            } catch (ProjectStateException e) {
                findings << found('baseline', HealthLevel.WARNING, "The baseline of the record is not what its index says: ${e.getMessage()}",
                        'Restore .grails from version control (git restore --source=HEAD -- .grails): feature add and project regenerate refuse a baseline that was changed',
                        null, [ProjectState.BASELINE], NOTE_RECORD)
            }
        }
        HealthFinding drift = versionDrift(context, selection, movable)
        if (drift != null) {
            findings << drift
        }
        List<String> interrupted = interrupted(context.directory)
        if (!interrupted.isEmpty()) {
            findings << found('interrupted', HealthLevel.WARNING, "A change of the project did not come to its end: ${TRANSACTIONS} has ${interrupted.join(', ')}",
                    'The next feature add, without --dry-run, undoes the change or completes it, and says what it did', null, [TRANSACTIONS], NOTE_RECORD)
        }
        if (!findings.isEmpty()) {
            return findings
        }
        ProjectManifest manifest = context.record.manifest
        return [ok(manifest.tracked ? 'The record under .grails reads, its baseline is what its index says, and it is the record of this version' :
                manifest.adopted ? 'The record under .grails, which project migrate wrote with the baseline of what the application is taken to be (generationState ' +
                        "${ProjectManifest.ADOPTED}), reads, its baseline is what its index says, and it is the record of this version" :
                        'The record under .grails, which project migrate wrote without a baseline (generationState ' +
                                "${ProjectManifest.UNTRACKED}), reads; feature add adds no feature until ${context.toType('project migrate --force')} writes it with one",
                [ProjectState.MANIFEST])]
    }

    private List<HealthFinding> withoutRecord(HealthContext context) {
        ProjectEvidence evidence = context.evidence
        String migrate = context.toType('project migrate')
        if (evidence.earlierRecord != null && evidence.earlierRecordRefused != null) {
            return [found('unreadable', HealthLevel.WARNING, "The project has no record under .grails, and ${evidence.earlierRecordRefused}",
                    "${migrate} writes the record of this version from the rest of the project; commit .grails", migrate, [evidence.earlierRecord], NOTE_RECORD)]
        }
        if (evidence.earlierRecord != null) {
            return [ok("The project has ${evidence.earlierRecord}, the record of an earlier version, which the commands that create its classes read; " +
                    "${migrate} writes the record of this version from it", [evidence.earlierRecord])]
        }
        return [found('missing', HealthLevel.WARNING, "The project has no record (${ProjectState.MANIFEST} or grails-forge-cli.yml): the commands that create its " +
                'classes are not there', "${migrate} writes it from the files of the project; commit .grails", migrate, [ProjectState.MANIFEST], NOTE_RECORD)]
    }

    // the record is of another version of Grails than the project; in a project of an earlier major version the check of the version says it
    private HealthFinding versionDrift(HealthContext context, ProjectState selection, boolean movable) {
        String project = context.projectVersion
        Integer major = Versions.major(project)
        if (selection == null || movable || project == null || major == null || major < EarlierGrailsProject.FIRST_MAJOR || context.onEarlierMajor ||
                project == selection.manifest.grailsVersion) {
            return null
        }
        String recorded = selection.manifest.grailsVersion
        String message = "The project is on Grails ${project} (gradle.properties), and the record is of Grails ${recorded}"
        if (!selection.manifest.hasBaseline()) {
            String again = context.toType('project migrate --force')
            return found('version-drift', HealthLevel.WARNING, message, "${again}, with the name and the options the record was written with, writes it again for this version",
                    again, ['gradle.properties', ProjectState.MANIFEST], NOTE_RECORD)
        }
        return found('version-drift', HealthLevel.WARNING, "${message}: the record is moved only by the command line of the version of the project",
                './grailsw update-wrapper, and then ./grailsw project migrate, which moves the record to the version of the project and changes no file outside .grails',
                './grailsw project migrate', ['gradle.properties', ProjectState.MANIFEST], NOTE_RECORD)
    }

    // what is left under .grails/transactions of a change that did not end, by its names
    private static List<String> interrupted(Path project) {
        Path directory = project.resolve(TRANSACTIONS)
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return []
        }
        List<String> names = []
        int more = 0
        try {
            Files.newDirectoryStream(directory).withCloseable { DirectoryStream<Path> stream ->
                for (Path each : stream) {
                    String name = each.getFileName().toString()
                    if (name == TRANSACTION_LOCK) {
                        continue
                    }
                    if (names.size() < MAX_NAMED) {
                        names << name
                    } else {
                        more++
                    }
                }
            }
        } catch (IOException ignored) {
            return ['a directory that cannot be read']
        }
        names.sort()
        if (more > 0) {
            names << "${more} more".toString()
        }
        return names
    }
}
