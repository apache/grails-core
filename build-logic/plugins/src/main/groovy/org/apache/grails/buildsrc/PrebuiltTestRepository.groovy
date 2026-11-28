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

import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException

import groovy.transform.CompileStatic

import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.initialization.Settings
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.provider.ValueSourceSpec

/**
 * The repository of the tests ({@code build/local-maven}) as a build uses it when it is told that the
 * framework is already published there and is not to be built: {@code -PprebuiltTestRepository}.
 *
 * <p>The build of Forge includes the framework build, compiles its own modules against the projects of
 * that build, and has the specifications that build generated applications depend on the publish of every
 * module of it. With the property it does neither: the framework build is not included, the modules of
 * Forge resolve the framework from the repository as the generated applications do, and the specifications
 * are given the repository as it is. No module of the framework is compiled.</p>
 *
 * <p>The value of the property is {@code true} (or nothing) for {@code build/local-maven} of the checkout,
 * or the path of a repository, which a workflow uses for one it downloaded; a path that is not absolute is
 * taken from the directory Gradle was started in. {@code false} is as if the property were not given.</p>
 *
 * <p>What the property gives up is that the jars follow the sources, so the repository is checked before
 * anything is configured, and the build fails when the repository</p>
 * <ul>
 * <li>is not there, or holds no {@code grails-bom} or no Grails Gradle plugin of the version of the build
 * (the message names the versions it does hold);</li>
 * <li>has no record of a complete publish of the framework and of the Grails Gradle plugins
 * ({@link TestRepositoryMarker}), or the record is of another version;</li>
 * <li>holds a framework whose Grails plugin modules were compiled with the other kind of call site than
 * the build asks for with {@code -PgrailsIndy}: those are different jars;</li>
 * <li>was published from other sources than are checked out now ({@link FrameworkSources}): the message
 * names the directories that changed and when the repository was published.</li>
 * </ul>
 *
 * <p>The last is a failure and not a warning because a warning in the output of a build that then passes
 * is not read, and tests that pass against jars that are not the sources are worse than no tests. It does
 * not get in the way of the work the property is for: a change in Forge is no change of the framework, so
 * whoever works on Forge alone publishes once and is never asked again; whoever changes the framework has
 * to publish again in any case for the change to be tested. {@code -PallowStaleTestRepository=true} turns
 * it into a warning, which every test task that uses the repository repeats, for the one who knows that
 * the change does not matter. Where Git cannot say what the sources are (a source release), there is
 * nothing to compare and the build says so.</p>
 *
 * <p>The check is a value source, so with the configuration cache it is made again on every build, and a
 * repository that has changed, or sources that have, discard the cached configuration.</p>
 */
@CompileStatic
class PrebuiltTestRepository {

    /** The Gradle property that asks for the repository to be used as it is. */
    static final String PROPERTY = 'prebuiltTestRepository'

    /** Set to {@code true} (as a Gradle property) to be warned of a repository that is older than the sources, not stopped. */
    static final String ALLOW_STALE_PROPERTY = 'allowStaleTestRepository'

    /** The name of the extra property of the root project that holds this object, or {@code null} without the property. */
    static final String EXTRA_PROPERTY = 'prebuiltTestRepository'

    private static final Logger LOGGER = Logging.getLogger(PrebuiltTestRepository)
    private static final String FAILURE = 'FAILURE '
    private static final String STALE = 'STALE '
    private static final String NOTE = 'NOTE '
    private static final String PUBLISH = 'Publish it from the top directory of the checkout with ' +
            '\'./gradlew publishAllPublicationsToTestCaseMavenRepoRepository\' (with -PgrailsIndy=false when the build that uses it is run with it) ' +
            'and \'(cd grails-gradle && ./gradlew publishAllPublicationsToTestCaseMavenRepoRepository)\''
    private static final List<List<String>> MARKER_ARTIFACTS = [
            ['org.apache.grails', 'grails-bom', '.pom', TestRepositoryMarker.FRAMEWORK],
            ['org.apache.grails', 'grails-gradle-plugins', '.jar', TestRepositoryMarker.GRADLE_PLUGINS],
    ]

    /** The repository. */
    final File directory

    /** What every test task that uses the repository says before it runs, or {@code null}. */
    final String warning

    final String version

    private PrebuiltTestRepository(File directory, String warning, String version) {
        this.directory = directory
        this.warning = warning
        this.version = version
    }

    /**
     * Reads the property for a build, checks the repository it names, and leaves the result for the
     * build scripts in the extra property {@link #EXTRA_PROPERTY} of the root project.
     *
     * @param settings the settings of the build
     * @return the repository, or {@code null} when the property does not ask for it
     * @throws GradleException when the repository is not one the build can use
     */
    static PrebuiltTestRepository of(Settings settings) {
        PrebuiltTestRepository repository = read(settings)
        settings.gradle.rootProject { Project root ->
            root.extensions.extraProperties.set(EXTRA_PROPERTY, repository)
        }
        return repository
    }

    private static PrebuiltTestRepository read(Settings settings) {
        String asked = settings.providers.gradleProperty(PROPERTY).orNull?.trim()
        if (asked == null || asked.equalsIgnoreCase('false')) {
            return null
        }
        Directory sources = GradleUtils.findAsfRootDir(settings.layout.rootDirectory)
        if (sources == null) {
            throw new GradleException("-P${PROPERTY} needs the checkout of Grails this build is part of, and ${settings.rootDir} is in none")
        }
        File directory
        if (asked.isEmpty() || asked.equalsIgnoreCase('true')) {
            directory = sources.dir('build/local-maven').asFile.canonicalFile
        } else {
            File named = new File(asked)
            directory = (named.absolute ? named : new File(settings.startParameter.currentDir, asked)).canonicalFile
        }
        boolean classic = settings.providers.gradleProperty('grailsIndy').map { String value -> !value.trim().toBoolean() }.getOrElse(false)
        boolean allowStale = settings.providers.gradleProperty(ALLOW_STALE_PROPERTY).map { String value -> value.trim().toBoolean() }.getOrElse(false)
        String version = settings.providers.gradleProperty('projectVersion').orNull
        if (version == null) {
            Properties properties = new Properties()
            properties.load(new StringReader(settings.providers.fileContents(sources.file('gradle.properties')).asText.get()))
            version = properties.getProperty('projectVersion')
        }
        List<String> findings = settings.providers.of(Check) { ValueSourceSpec<CheckParameters> spec ->
            spec.parameters.repository.set(directory.absolutePath)
            spec.parameters.sources.set(sources.asFile.absolutePath)
            spec.parameters.classic.set(classic)
            if (version != null) {
                spec.parameters.version.set(version)
            }
        }.get().readLines()

        Instant now = Instant.now()
        List<String> failures = findings.findAll { String finding -> finding.startsWith(FAILURE) }.collect { String finding -> withAges(finding.substring(FAILURE.length()), now) }
        List<String> stale = findings.findAll { String finding -> finding.startsWith(STALE) }.collect { String finding -> withAges(finding.substring(STALE.length()), now) }
        List<String> notes = findings.findAll { String finding -> finding.startsWith(NOTE) }.collect { String finding -> withAges(finding.substring(NOTE.length()), now) }
        String name = "The repository of the tests that -P${PROPERTY} names, ${directory},"
        if (failures) {
            throw new GradleException("${name} cannot be used. ${failures.join(' ')} ${PUBLISH}, or run without -P${PROPERTY} to have this build publish it.")
        }
        if (stale && !allowStale) {
            throw new GradleException("${name} is older than the sources. ${stale.join(' ')} ${PUBLISH}; run without -P${PROPERTY} to have this build publish it; " +
                    "or pass -P${ALLOW_STALE_PROPERTY}=true to test against the jars as they are.")
        }
        if (stale) {
            notes << "${name} is older than the sources, and -P${ALLOW_STALE_PROPERTY}=true has it used as it is. ${stale.join(' ')}".toString()
        }
        notes.each { String message ->
            LOGGER.warn('WARNING: {}', message)
        }
        return new PrebuiltTestRepository(directory, notes ? notes.join(' ') : null, version)
    }

    /**
     * What is wrong with a repository for a build, one finding to a line, and nothing when it can be used.
     *
     * @param repository the repository
     * @param version the version the build is for
     * @param classic whether the build asks for a framework compiled with classic call sites
     * @param current the sources as they are now, or {@code null} when they are not known
     */
    static String check(File repository, String version, boolean classic, FrameworkSources current) {
        if (!repository.isDirectory()) {
            return "${FAILURE}There is no such directory."
        }
        List<String> findings = []
        for (List<String> artifact : MARKER_ARTIFACTS) {
            File module = new File(repository, "${artifact[0].replace('.', '/')}/${artifact[1]}")
            File[] files = new File(module, version).listFiles()
            if (!files?.any { File file -> file.name.endsWith(artifact[2]) }) {
                List<String> held = (module.listFiles()?.findAll { File file -> file.isDirectory() }*.name ?: [] as List<String>).sort()
                String at = publishedAt(repository, artifact[3])
                findings << "${FAILURE}It holds no ${artifact[0]}:${artifact[1]} of ${version}, the version this build is for: ${held ? "it holds ${held.join(', ')}" : 'it holds none at all'}${at ? ", published${at}" : ''}.".toString()
            }
        }
        if (findings) {
            return findings.join('\n')
        }
        for (String build : [TestRepositoryMarker.FRAMEWORK, TestRepositoryMarker.GRADLE_PLUGINS]) {
            Map<String, String> record = TestRepositoryMarker.read(repository, build)
            if (record == null) {
                findings << ("${FAILURE}It has no record of a complete publish of ${build} (${TestRepositoryMarker.DIRECTORY}/${build}.properties): " +
                        'it was published before that record was written, by the publish of single modules, or by a publish that failed.').toString()
                continue
            }
            String published = "${build} was published to it${publishedAt(repository, build)}"
            if (record.get('format') != '2' || !(record.get(TestRepositoryMarker.CALL_SITES) in [TestRepositoryMarker.INDY, TestRepositoryMarker.CLASSIC]) ||
                    !(record.get(TestRepositoryMarker.FINGERPRINT) ==~ /[0-9a-f]{64}|unknown/) || !record.get('javaVersion')?.isInteger()) {
                findings << "${FAILURE}${published} with an incomplete or unsupported record; publish it again.".toString()
                continue
            }
            if (record.get('javaVersion').toInteger() > JavaVersion.current().majorVersion.toInteger()) {
                findings << "${FAILURE}${published} for Java ${record.get('javaVersion')}, newer than this build's Java ${JavaVersion.current()}.".toString()
                continue
            }
            if (record.get(TestRepositoryMarker.VERSION) != version) {
                findings << "${FAILURE}${published} as ${record.get(TestRepositoryMarker.VERSION)}, and this build is for ${version}.".toString()
                continue
            }
            if (build == TestRepositoryMarker.FRAMEWORK) {
                boolean publishedClassic = record.get(TestRepositoryMarker.CALL_SITES) == TestRepositoryMarker.CLASSIC
                if (publishedClassic != classic) {
                    findings << ("${FAILURE}${published} ${publishedClassic ? 'with -PgrailsIndy=false, so with classic call sites in its Grails plugin modules' : 'with invokedynamic call sites'}, " +
                            "and this build is run ${classic ? 'with -PgrailsIndy=false, so for classic call sites' : 'without -PgrailsIndy=false'}: those are different jars.").toString()
                    continue
                }
            }
            if (build == TestRepositoryMarker.FRAMEWORK) {
                List<String> missing = TestRepositoryMarker.FRAMEWORK_OUTPUTS.findAll { String path -> !TestRepositoryMarker.frameworkOutput(repository, path).isFile() }
                if (missing) {
                    findings << ("${FAILURE}${published} without what a build that does not build the framework takes from it besides its modules " +
                            "(${TestRepositoryMarker.FRAMEWORK_OUTPUTS_DIRECTORY}): ${missing.join(', ')}.").toString()
                    continue
                }
            }
            String fingerprint = record.get(TestRepositoryMarker.FINGERPRINT)
            if (current == null || fingerprint == TestRepositoryMarker.UNKNOWN) {
                findings << "${NOTE}${published}, and whether the sources of the framework have changed since cannot be told: ${current == null ? 'Git does not say what the sources are now' : 'Git did not say what they were then'}.".toString()
            } else if (fingerprint != current.fingerprint) {
                findings << "${STALE}${published}${from(TestRepositoryMarker.readSources(repository, build), current)}".toString()
            }
        }
        return findings.join('\n')
    }

    // ", from commit 1234567 with 2 files changed, and these have changed since: a, b."
    private static String from(FrameworkSources then, FrameworkSources now) {
        if (then == null) {
            return ', and the sources of the framework have changed since.'
        }
        List<String> changed = now.changedSince(then)
        String named = changed.take(8).join(', ') + (changed.size() > 8 ? " and ${changed.size() - 8} more" : '')
        String commit = then.commit ? ", from commit ${then.commit.take(10)}${then.uncommitted ? " with ${then.uncommitted} file${then.uncommitted == 1 ? '' : 's'} changed and not committed" : ''}" : ''
        return "${commit}, and the sources of the framework have changed since (now commit ${now.commit.take(10)}${now.uncommitted ? ", ${now.uncommitted} file${now.uncommitted == 1 ? '' : 's'} not committed" : ''}), in: ${named}."
    }

    // " at <instant>", in a form that withAges(String, Instant) completes with how long ago that was
    private static String publishedAt(File repository, String build) {
        String published = TestRepositoryMarker.read(repository, build)?.get(TestRepositoryMarker.PUBLISHED)
        return published ? " @${published}@" : ''
    }

    // The findings are a value the configuration cache compares between builds, so they name instants and
    // not how long ago those were; that is put in when a finding is said.
    private static String withAges(String finding, Instant now) {
        return finding.replaceAll(/@([^@ ]+)@/) { List<String> match ->
            try {
                return "${age(Duration.between(Instant.parse(match[1]), now))} ago (${match[1]})".toString()
            } catch (DateTimeParseException ignored) {
                return "at ${match[1]}".toString()
            }
        }
    }

    static String age(Duration duration) {
        long minutes = Math.max(0L, duration.toMinutes())
        if (minutes < 60) {
            return "${minutes} minute${minutes == 1 ? '' : 's'}"
        }
        if (minutes < 48 * 60) {
            long hours = minutes.intdiv(60)
            return "${hours} hour${hours == 1 ? '' : 's'}"
        }
        return "${minutes.intdiv(24 * 60)} days"
    }

    /** What {@link Check} is asked about. */
    interface CheckParameters extends ValueSourceParameters {

        /** The path of the repository. */
        Property<String> getRepository()

        /** The path of the top directory of the checkout. */
        Property<String> getSources()

        /** The version of the build, when it is not the one {@code gradle.properties} of the checkout gives. */
        Property<String> getVersion()

        /** Whether the build is run with {@code -PgrailsIndy=false}. */
        Property<Boolean> getClassic()
    }

    /**
     * The findings about a repository, which the configuration cache has made again on every build: it
     * reads the repository and asks Git for the state of the sources.
     */
    @CompileStatic
    abstract static class Check implements ValueSource<String, CheckParameters> {

        @Override
        String obtain() {
            File sources = new File(parameters.sources.get())
            String version = parameters.version.orNull
            if (version == null) {
                Properties properties = new Properties()
                new File(sources, 'gradle.properties').withInputStream { InputStream stream -> properties.load(stream) }
                version = properties.getProperty('projectVersion')
            }
            return check(new File(parameters.repository.get()), version, parameters.classic.get(), FrameworkSources.of(sources))
        }
    }
}
