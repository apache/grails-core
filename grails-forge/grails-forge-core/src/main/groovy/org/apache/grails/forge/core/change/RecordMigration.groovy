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

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.apache.grails.forge.core.change.ProjectTransaction.Write
import org.apache.grails.forge.core.pack.PackDescriptor
import org.apache.grails.forge.core.state.ProjectLock
import org.grails.forge.util.VersionInfo
import java.nio.file.Path
import java.util.function.Consumer
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * What moving the record of an application to this generator comes to, worked out before anything
 * is written: see {@link FeatureAddition#migrate}. The record of a generated application names the
 * generator that wrote it; another version, or another build of this version that generates other
 * files, writes another baseline for the same choices. Moving the record writes the baseline, its
 * index, the lock and the manifest this generator writes for the choices the record has, and no other
 * file: the files of the application stay as they are, and where they are not what this generator
 * makes of the choices, a feature that is added later takes them for changes of the application.
 *
 * @since 9.0
 */
@CompileStatic
final class RecordMigration {

    /**
     * What moving the record comes to.
     */
    static enum Outcome {

        /** This very build of the generator wrote the record: there is nothing to move */
        SAME_BUILD,

        /** Another build of this version wrote the record, and this one writes the same record but for the build that wrote it: there is nothing to move */
        SAME_FILES,

        /** Another generator wrote the record, and it is moved to this one */
        MOVED
    }

    // a release, up to four numbers with dots between them, and what follows it
    private static final Pattern VERSION = Pattern.compile(/(\d{1,9}(?:\.\d{1,9}){0,3})(?:[-.]([A-Za-z][A-Za-z0-9.+-]*))?/)
    private static final Pattern MILESTONE = Pattern.compile(/(?i)(?:m|milestone)\.?(\d{1,9})/)
    private static final Pattern CANDIDATE = Pattern.compile(/(?i)(?:rc|cr)\.?(\d{1,9})/)
    private static final Pattern SNAPSHOT = Pattern.compile(/(?i)snapshot/)

    final Outcome outcome

    /** The version of the generator that wrote the record */
    final String recordedVersion

    /** The version of Grails the record is of */
    final String recordedGrailsVersion

    /** The identity of the build that wrote the record, or {@code null} when the record has none */
    final String recordedSha256

    /** The paths of the files this generator writes for the choices of the record and the record has no copy of, sorted */
    final List<String> added

    /** The paths of the files the record has a copy of and this generator does not write for its choices, sorted */
    final List<String> removed

    /** The paths of the files this generator writes otherwise than the record has them: their content, their kind or whether they can be executed, sorted */
    final List<String> changed

    private final Path project
    private final Path home
    private final List<Write> writes

    @PackageScope
    RecordMigration(Outcome outcome, ProjectLock recorded, Path project, Path home, List<String> added, List<String> removed, List<String> changed, List<Write> writes) {
        this.outcome = outcome
        this.recordedVersion = recorded.generatorVersion
        this.recordedGrailsVersion = recorded.grailsVersion
        this.recordedSha256 = recorded.generatorSha256
        this.project = project
        this.home = home
        this.added = Collections.unmodifiableList(new ArrayList<String>(added).sort())
        this.removed = Collections.unmodifiableList(new ArrayList<String>(removed).sort())
        this.changed = Collections.unmodifiableList(new ArrayList<String>(changed).sort())
        this.writes = Collections.unmodifiableList(new ArrayList<Write>(writes))
    }

    /**
     * @return whether the record is that of this generator already, so that nothing is written
     */
    boolean isNothingToMove() {
        return outcome != Outcome.MOVED
    }

    /**
     * Writes the baseline, its index, the lock and the manifest this generator writes for the choices
     * of the record, all of them or none, with the lock of the project and a journal by which a change
     * that is interrupted is undone, as a feature is added. Nothing is written when there is nothing
     * to move.
     *
     * @throws ProjectChangeException when the files could not be written, or one of them is not what
     * it was when the move was worked out; the message says whether the record is as it was
     */
    void apply() {
        apply { String path -> }
    }

    /**
     * @param written told the path of each file once it is written. What it throws is not handled:
     * the change then stays where it is, as it does when the program ends at that point, and is
     * undone by {@link FeatureAddition#recover(Path)}
     * @see #apply()
     */
    void apply(Consumer<String> written) {
        if (isNothingToMove()) {
            return
        }
        ProjectTransaction.run(project, home, writes, written)
    }

    /**
     * Whether this generator moves the record a lock is of to itself: a record that Forge of an
     * earlier version wrote, and one that another build of this version wrote, as both name their
     * builds. The record of this build is not moved, since it is this generator's already, and
     * neither is that of a later version or of a generator that is not Forge.
     *
     * @param lock the lock of a record
     * @return whether {@code project migrate} moves the record to this generator
     */
    static boolean isMovable(ProjectLock lock) {
        return notMovable(lock) == null && (!isThisVersion(lock) || isOtherBuild(lock))
    }

    /**
     * @param lock the lock of a record
     * @return whether this generator wrote the record: this version, and this build of it as far as
     * the lock and this build name their builds
     */
    static boolean isThisGenerator(ProjectLock lock) {
        return isThisVersion(lock) && !isOtherBuild(lock)
    }

    /**
     * @return whether this version of the generator wrote the lock, whichever build of it
     */
    @PackageScope
    static boolean isThisVersion(ProjectLock lock) {
        return lock.generatorName == ProjectLock.GENERATOR && lock.generatorVersion == VersionInfo.getVersion() && lock.grailsVersion == VersionInfo.getGrailsVersion() &&
                lock.templateApi == PackDescriptor.TEMPLATE_API
    }

    /**
     * @return whether both the lock and this generator name their build, and the builds are not the same
     */
    @PackageScope
    static boolean isOtherBuild(ProjectLock lock) {
        String running = VersionInfo.getGeneratorSha256()
        return lock.generatorSha256 != null && running != null && lock.generatorSha256 != running
    }

    /**
     * Why the record a lock is of is not moved to this generator, in one sentence that ends without a
     * full stop; {@code null} when it is moved, or is this generator's already.
     */
    @PackageScope
    static String notMovable(ProjectLock lock) {
        String running = VersionInfo.getVersion()
        String runningGrails = VersionInfo.getGrailsVersion()
        String of = "The record was written by ${lock.generatorName} ${lock.generatorVersion} for Grails ${lock.grailsVersion}, and this is ${ProjectLock.GENERATOR} " +
                "${running} for Grails ${runningGrails}"
        if (lock.generatorName != ProjectLock.GENERATOR) {
            return "${of}: a record that another generator than ${ProjectLock.GENERATOR} wrote is not moved to it. Use the generator that wrote the record"
        }
        Integer version = compared(lock.generatorVersion, running)
        Integer grails = compared(lock.grailsVersion, runningGrails)
        if (version == null || grails == null) {
            return "${of}: which of the two versions is the later one cannot be told, and a record is moved to a later version only. Use the command line of " +
                    "Grails ${lock.generatorVersion}"
        }
        if (version > 0 || grails > 0) {
            return "${of}, which is earlier: a record is moved to a later version, and not to an earlier one. Use the command line of Grails ${lock.generatorVersion}, " +
                    'which wrote the record'
        }
        return null
    }

    /**
     * Compares two versions of Grails as Gradle and Maven order them: by their numbers first, a number
     * that is left out being zero, and then a milestone before a release candidate, a release
     * candidate before a snapshot and a snapshot before the release, each by its number. What follows
     * the numbers that is none of these comes before all of them, in the order of its text.
     *
     * @return less than zero, zero or more than zero as the first is the earlier, the same or the
     * later; {@code null} when one of them is not a version
     */
    @PackageScope
    static Integer compared(String one, String other) {
        Matcher first = VERSION.matcher(one ?: '')
        Matcher second = VERSION.matcher(other ?: '')
        if (!first.matches() || !second.matches()) {
            return null
        }
        List<Integer> ours = first.group(1).split(/\./).collect { String each -> Integer.parseInt(each) }
        List<Integer> theirs = second.group(1).split(/\./).collect { String each -> Integer.parseInt(each) }
        for (int index = 0; index < Math.max(ours.size(), theirs.size()); index++) {
            int difference = Integer.compare(index < ours.size() ? ours[index] : 0, index < theirs.size() ? theirs[index] : 0)
            if (difference != 0) {
                return difference
            }
        }
        String qualifier = first.group(2) ?: ''
        String otherQualifier = second.group(2) ?: ''
        int rank = Integer.compare(rank(qualifier), rank(otherQualifier))
        if (rank != 0) {
            return rank
        }
        int number = Integer.compare(number(qualifier), number(otherQualifier))
        return number != 0 ? number : qualifier.compareToIgnoreCase(otherQualifier)
    }

    // a release last, then a snapshot, a release candidate and a milestone, and whatever else first
    private static int rank(String qualifier) {
        if (qualifier.isEmpty()) {
            return 4
        }
        if (SNAPSHOT.matcher(qualifier).matches()) {
            return 3
        }
        if (CANDIDATE.matcher(qualifier).matches()) {
            return 2
        }
        return MILESTONE.matcher(qualifier).matches() ? 1 : 0
    }

    private static int number(String qualifier) {
        Matcher candidate = CANDIDATE.matcher(qualifier)
        if (candidate.matches()) {
            return Integer.parseInt(candidate.group(1))
        }
        Matcher milestone = MILESTONE.matcher(qualifier)
        return milestone.matches() ? Integer.parseInt(milestone.group(1)) : 0
    }
}
