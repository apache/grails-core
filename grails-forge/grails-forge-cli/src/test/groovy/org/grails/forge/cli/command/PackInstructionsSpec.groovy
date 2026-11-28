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
package org.grails.forge.cli.command

import java.nio.file.Files
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Stream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import groovy.json.JsonSlurper
import org.apache.grails.forge.core.pack.Pack
import org.apache.grails.forge.core.pack.PackDescriptorParser
import org.apache.grails.forge.core.pack.PackFeature
import org.apache.grails.forge.core.pack.PackPages
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.cli.CliRunner
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.GrailsPicocliFactory
import org.grails.forge.feature.AvailableFeatures
import org.grails.forge.feature.Feature
import picocli.CommandLine

/**
 * The {@code instructions} of a feature pack are shown once the application has the feature: by the
 * create commands and by {@code feature add}, after everything else the command says, as text.
 */
class PackInstructionsSpec extends CommandSpec implements CommandFixture {

    static final String INSTRUCTIONS = 'instructions: |\n  Run `./gradlew dbmGormDiff` to generate the migration of the audit tables.\n  Then commit grails-app/migrations.\n'

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()
    File work
    File home
    String previousHome

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out))
        System.setErr(new PrintStream(err))
        work = Files.createTempDirectory('grailsforgeinstructions').toFile()
        home = new File(work, 'home')
        home.mkdirs()
        previousHome = System.getProperty('user.home')
        System.setProperty('user.home', home.absolutePath)
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        System.setProperty('user.home', previousHome)
        work.deleteDir()
    }

    void "a create command shows the instructions of the pack of a directory after everything else it says"() {
        given:
        File pack = pack(new File(work, 'pack'), INSTRUCTIONS)

        when:
        int exit = create(new File(work, 'app'), '--features', 'acme-audit', '--feature-pack-dir', pack.absolutePath)
        List<String> lines = out.toString().readLines()
        int created = lines.findIndexOf { it.startsWith('| Application created at ') }
        int said = lines.indexOf('| The feature acme-audit says what to do next:')

        then:
        exit == 0
        created >= 0
        said > created
        lines[said + 1] == '    Run `./gradlew dbmGormDiff` to generate the migration of the audit tables.'
        lines[said + 2] == '    Then commit grails-app/migrations.'
        lines.size() == said + 3

        and: 'the instructions are no part of the application'
        !new File(work, 'app/README.md').text.contains('dbmGormDiff')
    }

    void "a create command shows the instructions of the pack of the jar of a plugin"() {
        given:
        File repository = new File(work, 'repository')
        publish(repository, 'com.acme:acme-audit-grails:2.3.0', CreateAppFeaturePackSpec.DESCRIPTOR + INSTRUCTIONS)

        when:
        int exit = create(new File(work, 'app'), '--features', 'com.acme:acme-audit-grails:2.3.0',
                '--feature-pack-repository', repository.toPath().toAbsolutePath().normalize().toUri().toString())
        List<String> lines = out.toString().readLines()
        int said = lines.indexOf('| The feature acme-audit says what to do next:')

        then:
        exit == 0
        said > lines.findIndexOf { it.startsWith('| Application created at ') }
        lines[said + 1] == '    Run `./gradlew dbmGormDiff` to generate the migration of the audit tables.'
    }

    void "an application without instructions is told nothing more"() {
        given:
        File pack = pack(new File(work, 'pack'), '')

        when:
        int exit = create(new File(work, 'app'), '--features', 'acme-audit', '--feature-pack-dir', pack.absolutePath)

        then:
        exit == 0
        !out.toString().contains('says what to do next')
    }

    void "long instructions are cut: #lineCount lines of #length characters"() {
        given:
        String text = 'instructions: |\n' + (1..lineCount).collect { "  ${it} ${'x' * length}\n" }.join('')
        File pack = pack(new File(work, 'pack'), text)

        when:
        int exit = create(new File(work, 'app'), '--features', 'acme-audit', '--feature-pack-dir', pack.absolutePath)
        List<String> lines = out.toString().readLines()
        int said = lines.indexOf('| The feature acme-audit says what to do next:')
        List<String> shown = lines.drop(said + 1)

        then:
        exit == 0
        shown.size() == expectedLines
        shown.every { it.length() <= 4 + 200 + 3 }
        shown.last() == last

        where:
        lineCount | length || expectedLines | last
        30        | 10     || 30            | '    30 xxxxxxxxxx'
        35        | 10     || 31            | '    ... and 5 more lines, which the descriptor of the feature has'
        31        | 10     || 31            | '    ... and 1 more line, which the descriptor of the feature has'
        1         | 300    || 1             | '    1 ' + 'x' * 198 + '...'
    }

    void "instructions with #what are refused with the descriptor, and nothing of them reaches the terminal"() {
        given:
        File pack = pack(new File(work, 'pack'), "instructions: \"Run this${character}now\"\n")

        when:
        int exit = create(new File(work, 'app'), '--features', 'acme-audit', '--feature-pack-dir', pack.absolutePath)

        then:
        exit == 2
        err.toString().contains('instructions: ')
        !out.toString().contains('says what to do next')
        !(out.toString() + err.toString()).contains(raw)
        !new File(work, 'app/build.gradle').exists()

        where:
        what                      | character    | raw
        'an escape of a terminal' | '\\e[31m'    | '\u001b'
        'a bell'                  | '\\a'        | '\u0007'
        'markup of the command'   | ' @|red x|@ ' | '\u001b'
    }

    void "feature add shows the instructions of the features it added, after it added them"() {
        given:
        File app = new File(work, 'app')
        create(app)

        when: 'what adding the feature would do, for a tool'
        int exit = add(app, 'grails-quartz', '--dry-run', '--json')
        Map document = new JsonSlurper().parseText(out.toString()) as Map

        then: 'the document has the instructions as a member, and nothing is shown beside it'
        exit == 0
        out.toString().readLines().size() == 1
        document.keySet().toList().last() == 'instructions'
        document.instructions == [[feature: 'grails-quartz', text: 'Declare a job with `grails create-job`.\nJobs start with the application.']]

        when: 'what it would do, for a person'
        exit = add(app, 'grails-quartz', '--dry-run')

        then: 'nothing was added, so nothing is to be done next'
        exit == 0
        !out.toString().contains('says what to do next')

        when:
        exit = add(app, 'grails-quartz')
        List<String> lines = out.toString().readLines()
        int added = lines.findIndexOf { it.startsWith('| Feature grails-quartz added') }

        then:
        exit == 0
        added >= 0
        lines.drop(added + 1) == ['| The feature grails-quartz says what to do next:',
                                  '    Declare a job with `grails create-job`.',
                                  '    Jobs start with the application.']

        when: 'the application has the feature already'
        exit = add(app, 'grails-quartz')

        then:
        exit == 0
        !out.toString().contains('says what to do next')
    }

    void "the document of feature add has no instructions for features that have none"() {
        given:
        File app = new File(work, 'app')
        create(app)

        when:
        int exit = inDirectory(app) { CliRunner.run(FeatureAddCommand, beanContext, 'grails-quartz', '--dry-run', '--json') }

        then:
        exit == 0
        (new JsonSlurper().parseText(out.toString()) as Map).instructions == []
    }

    private int create(File directory, String... arguments) {
        directory.mkdirs()
        return inDirectory(directory) { CliRunner.run(CreateAppCommand, beanContext, (['com.example.shop', '--inplace'] + arguments.toList()) as String[]) }
    }

    // feature add of a generator whose feature grails-quartz is the one of Grails with instructions: no feature of Grails has any yet
    private int add(File directory, String... arguments) {
        ProjectGenerator real = beanContext.getBean(ProjectGenerator)
        String descriptor = getClass().getResource('/META-INF/grails-forge-bundled/grails-quartz/feature.yml').getText('UTF-8') +
                '\ninstructions: |\n  Declare a job with `grails create-job`.\n  Jobs start with the application.\n'
        Pack pack = new Pack(new PackDescriptorParser().parseOfForge(descriptor, 'the specification'), 'the specification', [:], true)
        Feature instructing = PackFeature.of(pack, { String name -> Optional.<Feature>empty() } as Function<String, Optional<Feature>>,
                { null } as Supplier<PackPages>)
        FeatureAddCommand command = new FeatureAddCommand(new Instructing(real: real, instructing: instructing))
        return inDirectory(directory) { new CommandLine(command, new GrailsPicocliFactory(beanContext)).execute(arguments) }
    }

    private int inDirectory(File directory, Closure<Integer> run) {
        String current = System.getProperty('user.dir')
        System.setProperty('user.dir', directory.absolutePath)
        try {
            out.reset()
            err.reset()
            return run.call()
        } finally {
            System.setProperty('user.dir', current)
        }
    }

    private static File pack(File directory, String more) {
        new File(directory, 'templates').mkdirs()
        new File(directory, 'feature.yml').setText(CreateAppFeaturePackSpec.DESCRIPTOR + more, 'UTF-8')
        new File(directory, 'templates/audit.txt').setText('audit', 'UTF-8')
        return directory
    }

    private static File publish(File repository, String coordinates, String descriptor) {
        List<String> parts = coordinates.split(':').toList()
        File directory = new File(repository, "${parts[0].replace('.', '/')}/${parts[1]}/${parts[2]}")
        directory.mkdirs()
        File jar = new File(directory, "${parts[1]}-${parts[2]}.jar")
        new ZipOutputStream(new FileOutputStream(jar)).withCloseable { ZipOutputStream zip ->
            ['META-INF/grails-forge/feature.yml'        : descriptor,
             'META-INF/grails-forge/templates/audit.txt': 'audit'].each { String path, String text ->
                zip.putNextEntry(new ZipEntry(path))
                zip.write(text.getBytes('UTF-8'))
                zip.closeEntry()
            }
        }
        return jar
    }

    /** A generator whose list of features has one feature in the place of the one of its name. */
    static class Instructing implements ProjectGenerator {

        @Delegate
        ProjectGenerator real

        Feature instructing

        @Override
        AvailableFeatures availableFeatures(ApplicationType applicationType, Collection<Pack> packs) {
            AvailableFeatures all = real.availableFeatures(applicationType, packs)
            Feature other = instructing
            return new AvailableFeatures() {

                @Override
                Optional<Feature> findFeature(String name) {
                    return name == other.getName() ? Optional.of(other) : all.findFeature(name)
                }

                @Override
                Optional<Feature> findFeature(String name, boolean ignoreVisibility) {
                    return name == other.getName() ? Optional.of(other) : all.findFeature(name, ignoreVisibility)
                }

                @Override
                Stream<Feature> getFeatures() {
                    return all.getFeatures()
                }

                @Override
                Stream<Feature> getAllFeatures() {
                    return all.getAllFeatures()
                }

                @Override
                Iterator<String> iterator() {
                    return all.iterator()
                }
            }
        }
    }
}
