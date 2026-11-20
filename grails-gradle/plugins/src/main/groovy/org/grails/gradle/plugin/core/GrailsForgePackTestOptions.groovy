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

import java.time.Duration
import java.util.regex.Pattern

import javax.inject.Inject

import groovy.transform.CompileStatic

import org.gradle.api.GradleException
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

/**
 * How the Forge feature pack of a plugin is tested by {@code forgePackTest}, configured through the
 * nested {@code grails { forgePackTest { } }} block:
 *
 * <pre><code>grails {
 *     forgePackTest {
 *         fixture('web')
 *         fixture('mongo') {
 *             applicationType = 'rest-api'
 *             options = [data: 'mongodb']
 *         }
 *         tasks = ['assemble']
 *     }
 * }</code></pre>
 *
 * @since 9.0
 */
@CompileStatic
class GrailsForgePackTestOptions {

    private static final Pattern FIXTURE_NAME = Pattern.compile(/[a-z][a-z0-9]*(-[a-z0-9]+)*/)
    private static final Pattern WORD = Pattern.compile(/[^\s\p{Cc}\p{Cf}\p{Zl}\p{Zp},=]+/)
    private static final int MAX_FIXTURE_NAME = 60
    private static final int DEFAULT_TIMEOUT_MINUTES = 30
    private static final List<String> OPTIONS = ['data', 'servlet', 'reloading', 'jdk'].asImmutable()

    /**
     * The applications to generate and build. Without any, there is one for each type of application
     * the descriptor of the pack names, with the options every application has by default.
     */
    final NamedDomainObjectContainer<ForgePackFixture> fixtures

    /**
     * The tasks to run in each generated application. By default {@code assemble}, which compiles
     * the application and packages it, and runs none of its tests.
     */
    final ListProperty<String> tasks

    /** Further arguments for the Gradle of each generated application, such as {@code --stacktrace}. */
    final ListProperty<String> arguments

    /**
     * The repositories the generated applications resolve from before the ones every generated
     * application has: addresses, paths of directories, {@code mavenLocal()} or {@code mavenCentral()}.
     * Each is written into the build files of the applications as it is, a user and a password in an
     * address included. Without a value, which is the default, they are the Maven repositories the
     * build script of the plugin declares, those left out whose address has a user or a password or
     * is not {@code https}; the task says which.
     */
    final ListProperty<String> repositories

    /**
     * Whether the generated applications also resolve from the local Maven repository, before every
     * other repository. Not by default.
     */
    final Property<Boolean> mavenLocal

    /**
     * Whether the generated applications are built with {@code --offline}. Not by default, unless the
     * build of the plugin is itself run with {@code --offline}.
     */
    final Property<Boolean> offline

    /**
     * How long the build of one generated application can take before it is stopped and the fixture
     * is one that could not be built. By default 30 minutes.
     */
    final Property<Duration> fixtureTimeout

    @Inject
    GrailsForgePackTestOptions(ObjectFactory objects) {
        this.fixtures = objects.domainObjectContainer(ForgePackFixture)
        this.fixtures.configureEach { ForgePackFixture fixture -> fixture.applicationType.convention('web') }
        this.tasks = objects.listProperty(String).convention(['assemble'])
        this.arguments = objects.listProperty(String).convention([])
        this.repositories = objects.listProperty(String).convention((Iterable<String>) null)
        this.mavenLocal = objects.property(Boolean).convention(false)
        this.offline = objects.property(Boolean).convention(false)
        this.fixtureTimeout = objects.property(Duration).convention(Duration.ofMinutes(DEFAULT_TIMEOUT_MINUTES))
    }

    /**
     * Adds a fixture, or configures the one of the name.
     *
     * @param name the name of the fixture
     * @param configureClosure a closure applied to the {@link ForgePackFixture}
     */
    void fixture(String name, @DelegatesTo(value = ForgePackFixture, strategy = Closure.DELEGATE_FIRST) Closure<?> configureClosure = null) {
        ForgePackFixture fixture = fixtures.maybeCreate(name)
        if (configureClosure != null) {
            configureClosure.delegate = fixture
            configureClosure.resolveStrategy = Closure.DELEGATE_FIRST
            configureClosure.call()
        }
    }

    /**
     * The fixtures as Forge reads them, each on a line: its name, its type of application, its
     * options and its features.
     *
     * @throws GradleException for a fixture Forge could not read from a line
     */
    List<String> fixtureLines() {
        return fixtures.collect { ForgePackFixture fixture -> line(fixture) }
    }

    private static String line(ForgePackFixture fixture) {
        String name = fixture.name
        if (name.length() > MAX_FIXTURE_NAME || !FIXTURE_NAME.matcher(name).matches()) {
            throw new GradleException('The name of a fixture of forgePackTest is lower case letters, digits and single hyphens, starts with ' +
                    "a letter and has at most ${MAX_FIXTURE_NAME} characters: '${name}' is not")
        }
        List<String> words = [name, word(name, 'applicationType', fixture.applicationType.get())]
        fixture.options.get().each { String option, String value ->
            if (!(option in OPTIONS)) {
                throw new GradleException("The fixture ${name} of forgePackTest has the option '${option}': a fixture has ${OPTIONS.join(', ')}")
            }
            words << "${option}=${word(name, option, value)}".toString()
        }
        List<String> features = fixture.features.get().collect { String feature -> word(name, 'features', feature) }
        if (features) {
            words << "features=${features.join(',')}".toString()
        }
        return words.join(' ')
    }

    // a line is split at its spaces, and the features at their commas
    private static String word(String fixture, String of, String value) {
        if (value == null || !WORD.matcher(value).matches()) {
            throw new GradleException("The fixture ${fixture} of forgePackTest has a value for ${of} that is empty or has a space, a comma, " +
                    'an equals sign or a control character')
        }
        return value
    }
}
