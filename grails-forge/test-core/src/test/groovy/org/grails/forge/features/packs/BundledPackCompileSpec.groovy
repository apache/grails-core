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

package org.grails.forge.features.packs

import java.nio.file.Files

import org.apache.grails.forge.core.pack.PackFeature
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.BuildTask
import org.gradle.testkit.runner.TaskOutcome
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.OperatingSystem
import org.grails.forge.application.Project
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.feature.Feature
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.FileSystemOutputHandler
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.GormImpl
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.Options
import org.grails.forge.options.ServletImpl
import org.grails.forge.util.NameUtils
import org.grails.forge.utils.CommandSpec
import spock.lang.Shared

/**
 * Compiles what the bundled feature packs generate. The golden files of the packs pin the text of
 * what each one writes; nothing in them says that the text is an application that compiles.
 *
 * <p>Building one application for each pack would take hours, so the packs are put together: a few
 * applications are worked out such that every pack is in at least one of them and every type of
 * application is one of them, and each is compiled once. They are worked out from what the generator itself says about its features, and not from a
 * list kept here, so a new pack is compiled without an edit to this specification, and a pack that
 * no application can have fails it by name.</p>
 *
 * <p>An application is put together by asking the generator, which is the judge of what goes
 * together: an option or a feature is added when the generator takes the selection and the
 * application then has more packs that no earlier application had. Exclusive groups, required
 * features, the types of application a pack is offered for and the options a pack is the default of
 * are thereby those of the generator. The packs a user cannot select, such as the servlet containers,
 * are reached through the options and defaults that bring them in.</p>
 *
 * <p>Only the compile tasks run, so nothing a feature needs at run time is started: no database, no
 * container, no browser. What the application runs with is resolved all the same, by the catalog of its
 * commands that the build writes after the classes, which says so when it does not resolve: an application
 * whose runtime class path does not, such as one with two versions of a module that are both required,
 * fails here as well, unless a pack of it is named in {@link #RUNTIME_NOT_RESOLVED} with the reason. The JSON and markup views of an application are compiled with it, since the
 * Gradle plugins of the views compile them before the tests are compiled. The Gradle build cache, which a generated application turns on, is switched
 * off for these builds, since a class file taken from the cache would say nothing about the sources.</p>
 */
class BundledPackCompileSpec extends CommandSpec {

    /**
     * The packs no compiled application has, each with the reason. A pack named here is not selected;
     * the specification fails when one of them is no pack any more or is compiled after all.
     */
    static final Map<String, String> NOT_COMPILED = [:]

    /**
     * The packs that give an application a runtime class path that is known not to resolve, each with
     * the reason. An application with one of them is compiled all the same; the specification fails when
     * its runtime class path resolves, so that the pack is taken out of here once that is corrected.
     */
    static final Map<String, String> RUNTIME_NOT_RESOLVED = [:]

    private static final Project PROJECT = NameUtils.parse('example.grails.foo')

    private static final JdkVersion JDK = JdkVersion.DEFAULT_OPTION

    private static final OperatingSystem OPERATING_SYSTEM = OperatingSystem.LINUX

    private static final List<String> INTEGRATION_TEST_SOURCES = ['src/integration-test/groovy', 'src/integration-test/java']

    @Shared
    File userHome

    @Shared
    Set<String> packs

    @Shared
    List<Compiled> applications = []

    /** The packs that are in no application, each with what the generator said when it was asked for alone. */
    @Shared
    Map<String, String> inNoApplication = [:]

    void setupSpec() {
        // one Gradle user home for all the applications: the dependencies are downloaded once and the
        // daemon is reused. CommandSpec removes its own after each feature method
        userHome = Files.createTempDirectory("${tempDirectoryPrefix}TestKit").toFile()
        useGradleUserHome(userHome)

        long started = System.currentTimeMillis()
        packs = new TreeSet<String>(applicationContext.getBeansOfType(PackFeature).values()*.name)
        plan()
        println("[packs-compile] ${packs.size()} packs in ${applications.size()} applications, " +
                "worked out in ${System.currentTimeMillis() - started} ms")
        applications.each { Compiled application ->
            println("[packs-compile] planned ${application}: ${application.features.findAll { String name -> name in packs }.join(' ')}")
        }
    }

    void cleanupSpec() {
        userHome?.deleteDir()
    }

    void 'every bundled feature pack is in an application that is compiled'() {
        expect: 'the packs are read from the generator'
        !packs.isEmpty()

        and: 'no pack is left out without a reason'
        assert inNoApplication.isEmpty(): 'These bundled feature packs are in none of the applications that are compiled, ' +
                'so nothing shows that what they generate compiles. Make the pack selectable in some application, ' +
                'or name it in NOT_COMPILED with the reason:\n' +
                inNoApplication.collect { String name, String refusal -> "  ${name}: ${refusal}" }.join('\n')

        and: 'what is left out is a pack, and is left out, and so is what is known not to resolve'
        (NOT_COMPILED.keySet() - packs).isEmpty()
        (RUNTIME_NOT_RESOLVED.keySet() - packs).isEmpty()
        NOT_COMPILED.keySet().findAll { String name -> applications.any { Compiled application -> name in application.features } }.isEmpty()
    }

    void 'the application #application compiles'(Compiled application) {
        given:
        long started = System.currentTimeMillis()
        applicationContext.getBean(ProjectGenerator).generate(application.type, PROJECT, application.options, OPERATING_SYSTEM,
                application.selected, new FileSystemOutputHandler(dir, ConsoleOutput.NOOP), ConsoleOutput.NOOP)
        List<String> tasks = ['compileGroovy', 'compileTestGroovy']
        if (INTEGRATION_TEST_SOURCES.any { String sources -> new File(dir, sources).exists() }) {
            tasks << 'compileIntegrationTestGroovy'
        }

        when:
        BuildResult result = executeGradle((tasks + ['--no-build-cache']) as String[])
        println("[packs-compile] compiled ${application} with ${tasks.join(' ')} " +
                "in ${(System.currentTimeMillis() - started).intdiv(1000)} s")

        then: 'the sources were compiled in this build, not taken from a cache'
        result.task(':compileGroovy').outcome == TaskOutcome.SUCCESS
        tasks.every { String task -> result.task(":${task}").outcome in [TaskOutcome.SUCCESS, TaskOutcome.NO_SOURCE] }
        result.tasks.every { BuildTask task -> task.outcome != TaskOutcome.FROM_CACHE }

        and: 'what the application runs with resolves, unless a pack is known to keep it from it: the catalog of its commands, which a build that compiles it writes, names a class path that does not'
        String catalog = new File(dir, '.grails/cache/tool-catalog.json').getText('US-ASCII')
        catalog.contains('runtimeClasspath cannot be resolved completely ') == application.features.any { String name -> RUNTIME_NOT_RESOLVED.containsKey(name) }
        !catalog.contains('grailsCliClasspath cannot be resolved completely ')

        where:
        application << applications
    }

    @Override
    String getTempDirectoryPrefix() {
        'packs-compile'
    }

    private void plan() {
        Set<String> left = new TreeSet<String>(packs - NOT_COMPILED.keySet())
        while (!left.isEmpty()) {
            Compiled best = null
            for (ApplicationType type : ApplicationType.values()) {
                Compiled grown = grow(new Compiled(type: type, options: defaultOptions()), left, null)
                if (grown != null && gain(grown, left) > (best == null ? 0 : gain(best, left))) {
                    best = grown
                }
            }
            if (best == null) {
                // nothing more is reached from the defaults: look for one application that has one of the packs
                best = left.findResult { String pack -> having(pack, left) }
            }
            if (best == null) {
                break
            }
            best.first = best.features.findAll { String name -> name in left }
            applications << best
            left.removeAll(best.features)
        }
        for (String pack : left) {
            inNoApplication[pack] = refusalOf(pack)
        }
        // a plugin is built with another Gradle plugin and a pack may write other files for it: each type of
        // application that none of the applications above is gets one, with as many of the packs as it can have
        for (ApplicationType type : ApplicationType.values()) {
            if (!applications.any { Compiled application -> application.type == type }) {
                Compiled grown = grow(new Compiled(type: type, options: defaultOptions()), new TreeSet<String>(packs - NOT_COMPILED.keySet()), null)
                if (grown != null) {
                    applications << grown
                }
            }
        }
    }

    /**
     * Adds to an application, for as long as something can be added: another value of an option or one
     * more feature, whenever the generator takes it and the application then has more of the packs that
     * are left.
     */
    private Compiled grow(Compiled from, Set<String> left, String kept) {
        Compiled current = resolved(from)
        if (current == null || (kept != null && !(kept in current.features))) {
            return null
        }
        boolean changed = true
        while (changed) {
            changed = false
            List<Compiled> tried = []
            for (Options options : otherOptions(current.options)) {
                tried << new Compiled(type: current.type, options: options, selected: current.selected)
            }
            for (String name : selectable(current.type)) {
                if (name in left && !(name in current.features)) {
                    tried << new Compiled(type: current.type, options: current.options, selected: current.selected + name)
                }
            }
            for (Compiled candidate : tried) {
                Compiled taken = resolved(candidate)
                if (taken != null && gain(taken, left) > gain(current, left) && (kept == null || kept in taken.features)) {
                    // the other candidates were made from what the application was: start over from what it is now
                    current = taken
                    changed = true
                    break
                }
            }
        }
        return current
    }

    /** The first application, over every type and every value of the options, that has the pack. */
    private Compiled having(String pack, Set<String> left) {
        for (ApplicationType type : ApplicationType.values()) {
            List<String> selected = pack in selectable(type) ? [pack] : []
            for (Options options : everyOptions()) {
                Compiled grown = grow(new Compiled(type: type, options: options, selected: selected), left, pack)
                if (grown != null) {
                    return grown
                }
            }
        }
        return null
    }

    private static int gain(Compiled application, Set<String> left) {
        application.features.count { String name -> name in left } as int
    }

    /** The visible packs of a type of application: what a user can select. */
    private List<String> selectable(ApplicationType type) {
        applicationContext.getBean(ProjectGenerator).availableFeatures(type, [])
                .getFeatures()
                .map { Feature feature -> feature.name }
                .filter { String name -> name in packs && !(name in NOT_COMPILED.keySet()) }
                .sorted()
                .toList()
    }

    /** The application as the generator makes it, or {@code null} when the generator refuses the selection. */
    private Compiled resolved(Compiled asked) {
        try {
            GeneratorContext context = applicationContext.getBean(ProjectGenerator).createGeneratorContext(asked.type, PROJECT,
                    asked.options, OPERATING_SYSTEM, asked.selected, ConsoleOutput.NOOP)
            // some refusals come only when the features are applied
            context.applyFeatures()
            return new Compiled(type: asked.type, options: asked.options, selected: asked.selected,
                    features: new ArrayList<String>(context.features))
        } catch (IllegalArgumentException ignored) {
            return null
        }
    }

    private String refusalOf(String pack) {
        ApplicationType type = ApplicationType.values().find { ApplicationType each -> pack in selectable(each) }
        if (type == null) {
            return 'it is visible for no type of application, and no option or default of any type brings it in'
        }
        try {
            applicationContext.getBean(ProjectGenerator).createGeneratorContext(type, PROJECT, defaultOptions(), OPERATING_SYSTEM,
                    [pack], ConsoleOutput.NOOP).applyFeatures()
            return "the generator takes it for a ${type.name} application, and the application then does not have it"
        } catch (IllegalArgumentException e) {
            return "selected for a ${type.name} application: ${e.message}"
        }
    }

    private static Options defaultOptions() {
        new Options(DevelopmentReloading.DEFAULT_OPTION, GormImpl.DEFAULT_OPTION, ServletImpl.DEFAULT_OPTION, JDK)
    }

    /** The options that differ from the given ones in one value. */
    private static List<Options> otherOptions(Options options) {
        List<Options> others = []
        others.addAll(GormImpl.values().findAll { GormImpl each -> each != options.gormImpl }
                .collect { GormImpl each -> options.withGormImpl(each) })
        others.addAll(ServletImpl.values().findAll { ServletImpl each -> each != options.servletImpl }
                .collect { ServletImpl each -> options.withServletImpl(each) })
        others.addAll(DevelopmentReloading.values().findAll { DevelopmentReloading each -> each != options.developmentReloading }
                .collect { DevelopmentReloading each -> options.withDevelopmentReloading(each) })
        return others
    }

    private static List<Options> everyOptions() {
        List<Options> every = []
        for (GormImpl gorm : GormImpl.values()) {
            for (ServletImpl servlet : ServletImpl.values()) {
                for (DevelopmentReloading reloading : DevelopmentReloading.values()) {
                    every << new Options(reloading, gorm, servlet, JDK)
                }
            }
        }
        return every
    }

    /** An application that is compiled: what is asked of the generator, and the features it then has. */
    static class Compiled {

        ApplicationType type
        Options options
        List<String> selected = []
        List<String> features = []
        /** The packs this application is the first to have. */
        List<String> first = []

        @Override
        String toString() {
            String options = [options.gormImpl, options.servletImpl, options.developmentReloading]*.name().join(', ').toLowerCase(Locale.ENGLISH)
            return "${type.name} (${options}) with ${selected.join(', ') ?: 'no selected feature'}"
        }
    }
}
