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
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import groovy.json.JsonSlurper
import org.apache.grails.forge.cli.pack.PackArtifacts
import org.apache.grails.forge.cli.pack.PackArtifacts.Reply
import org.apache.grails.forge.cli.pack.PackArtifacts.Transport
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.cli.CliRunner
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.GrailsPicocliFactory
import picocli.CommandLine

/**
 * An application with the feature of a feature pack that is not part of Grails, from its creation to a
 * feature that is added to it in a clone of it elsewhere: the pack is given again, held to the record
 * of the application by its digest, and the application is generated again with it. The pack of a
 * directory is held to the SHA-256 of its content, the jar of a plugin to the coordinates and the
 * SHA-256 of the jar. Every repository here is a directory.
 */
class ExternalPackJourneySpec extends CommandSpec implements CommandFixture {

    static final String PLUGIN = 'com.acme:acme-audit-grails'
    static final String COORDINATES = PLUGIN + ':2.3.0'
    static final String BASE_PLUGIN = 'com.acme:acme-base-grails'
    static final String BASE = BASE_PLUGIN + ':1.4.0'
    static final String BASE_DESCRIPTOR = 'format: 1\nkind: feature\ntemplateApi: 1\nname: acme-base\n' +
            "compatibility:\n  grails: '[9.0.0,10.0.0)'\n  forge: '[9.0.0,10.0.0)'\n" +
            'description: The base of the Acme plugins\n' +
            'dependencies:\n  - scope: implementation\n    coordinates: com.acme:acme-base-grails\n    version: 1.4.0\n' +
            'files:\n  - from: audit.txt\n    to: grails-app/conf/base.txt\n'

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()
    File work
    File home
    String previousHome
    File repository
    String address
    List<URI> asked = []

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out))
        System.setErr(new PrintStream(err))
        work = Files.createTempDirectory('grailsforgejourney').toFile()
        repository = new File(work, 'repository')
        repository.mkdirs()
        address = repository.toPath().toAbsolutePath().normalize().toUri().toString()
        // the journal of a change is kept in the home directory of whoever runs the command
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

    void "the pack of a directory is recorded by the digest of its content, and a clone gets a feature with the same pack elsewhere"() {
        given: 'a pack, and an application created with it'
        File original = pack(new File(work, 'pack'), 'kept as it is')
        File app = new File(work, 'app')

        when:
        int exit = create(app, '--features', 'acme-audit', '--feature-pack-dir', original.absolutePath)

        then: 'what the later commands need is said once the application is created'
        exit == 0
        out.toString().contains('The feature acme-audit is that of a feature pack that is not part of Grails: feature add and project regenerate work for the ' +
                'application only with that very pack, which its record names by its SHA-256 (the jar of a plugin is fetched again by its coordinates; ' +
                'the pack of a directory is given again with --feature-pack-dir)')

        and: 'the record has the digest of the content of the pack, and no path of this computer'
        String lock = new File(app, '.grails/forge.lock.yml').text
        String content = (lock =~ /- name: acme-audit\n  source: pack\n  contentSha256: ([0-9a-f]{64})\n/)[0][1]
        !lock.contains(work.name)

        when: 'the features that can be added are listed without the pack'
        exit = list(app)
        List<String> lines = out.toString().readLines()

        then: 'what the application has is listed, and the list says which pack feature add and the list need, and how it is given'
        exit == 0
        lines[0] ==~ /\| The application shop \(web\) has \d+ features, as its record in \.grails says:/
        lines[1] ==~ /\s+asked for\s+acme-audit/
        out.toString().replaceAll(/\s+/, ' ').contains('| The features feature add can add to it are listed with the feature packs it was generated with, ' +
                'which feature add needs too: give them to feature list as to feature add. The application has the feature of feature packs that are not part of ' +
                'Grails, and that pack was not given: ')
        out.toString().replaceAll(/\s+/, ' ').contains("acme-audit, a feature pack that was given as a directory or a file whose content has the SHA-256 ${content}")
        out.toString().contains('a pack of a directory is given with --feature-pack-dir')
        !out.toString().contains('cannot yet be added')
        !out.toString().contains('can be added to it with')

        when: 'with the pack'
        exit = list(app, '--feature-pack-dir', original.absolutePath)
        lines = out.toString().readLines()
        int addable = lines.findIndexOf { it.contains('can be added to it with: ') && it.endsWith(' feature add FEATURE, given the feature packs of the application as here') }

        then: 'they are listed as feature add adds them'
        exit == 0
        addable > 0
        lines.drop(addable).any { it.startsWith('    grails-quartz ') }
        lines.drop(addable).any { it.startsWith('    cache ') }
        !lines.drop(addable).any { it.startsWith('    acme-audit ') }

        when: 'with a pack of that name and another content'
        exit = list(app, '--feature-pack-dir', pack(new File(work, 'listed-changed'), 'changed by someone').absolutePath)

        then: 'it is refused, as feature add refuses it'
        exit == 1
        err.toString().contains('The feature pack that was given is not the one the application was generated with')

        when: 'the application is cloned elsewhere, and so is the pack, and a feature of Grails is added there'
        File clone = copy(app, new File(work, 'elsewhere/clone'))
        File packOfTheClone = copy(original, new File(work, 'elsewhere/pack'))
        exit = add(clone, 'grails-quartz', '--feature-pack-dir', packOfTheClone.absolutePath)

        then: 'the application has both, and still has the pack by the same digest'
        exit == 0
        new File(clone, 'build.gradle').text.contains('quartz')
        new File(clone, 'grails-app/conf/com/example/audit.txt').text == 'kept as it is'
        new File(clone, '.grails/forge.lock.yml').text.contains("- name: acme-audit\n  source: pack\n  contentSha256: ${content}\n")
        new File(clone, '.grails/project.yml').text.contains('- name: grails-quartz')

        and: 'is generated again with the same pack, as it is now'
        regenerate(clone, '--output', new File(work, 'regenerated').absolutePath, '--feature-pack-dir', packOfTheClone.absolutePath) == 0
        new File(work, 'regenerated/build.gradle').text == new File(clone, 'build.gradle').text
        new File(work, 'regenerated/grails-app/conf/com/example/audit.txt').text == 'kept as it is'

        and: 'the application it was cloned from is what it was generated as'
        regenerate(app, '--diff', '--feature-pack-dir', original.absolutePath) == 0

        when: 'without the pack'
        Map<String, String> before = tree(clone)
        exit = add(clone, 'cache')

        then: 'nothing is added, and what to give is said'
        exit == 1
        err.toString().contains('The application has the feature of feature packs that are not part of Grails, and that pack was not given: such an application is ' +
                'generated again, to add a feature to it or to compare it, only with the very packs it was generated with. acme-audit, a feature pack that was given as ' +
                "a directory or a file whose content has the SHA-256 ${content}. Nothing was changed.")
        tree(clone) == before

        when: 'with a pack of the same name and another content'
        File changed = pack(new File(work, 'changed'), 'changed by someone')
        exit = add(clone, 'cache', '--feature-pack-dir', changed.absolutePath)

        then:
        exit == 1
        err.toString().contains('The feature pack that was given is not the one the application was generated with, so it is not generated again with it: acme-audit is ' +
                "a feature pack whose content has the SHA-256 ${content}, and the content of the one that was given, ${changed.toPath().toAbsolutePath().normalize()}, has ")
        tree(clone) == before

        when: 'with a pack the application does not have'
        File other = pack(new File(work, 'other'), 'kept as it is', 'other-audit')
        exit = add(clone, 'cache', '--feature-pack-dir', packOfTheClone.absolutePath, '--feature-pack-dir', other.absolutePath)

        then:
        exit == 1
        err.toString().contains('The feature pack other-audit was given, and the application has no feature of it')
        tree(clone) == before

        when: 'and regenerate does not compare without it'
        exit = regenerate(clone, '--diff')

        then:
        exit == 1
        err.toString().contains('and that pack was not given')
    }

    void "the jar of a plugin is fetched again by the coordinates and the digest of the record, also without the network"() {
        given:
        File jar = publish(COORDINATES, 'kept as it is')
        File app = new File(work, 'app')

        when:
        int exit = create(app, '--features', COORDINATES, '--feature-pack-repository', address)

        then:
        exit == 0
        out.toString().contains('The feature acme-audit is that of a feature pack that is not part of Grails')
        new File(app, '.grails/forge.lock.yml').text.contains("- name: acme-audit\n  source: pack\n  coordinates: ${PLUGIN}\n  version: 2.3.0\n  sha256: ${sha256(jar)}\n")

        when: 'the features that can be added are listed in a clone, with the repository'
        File clone = copy(app, new File(work, 'clone'))
        exit = list(clone, '--feature-pack-repository', address)
        List<String> lines = out.toString().readLines()
        int addable = lines.findIndexOf { it.contains('can be added to it with: ') && it.endsWith(' feature add FEATURE, given the feature packs of the application as here') }

        then: 'the jar is fetched again, as feature add fetches it, and they are listed'
        exit == 0
        out.toString().contains("The feature pack acme-audit of the application is fetched again as ${COORDINATES}, with the SHA-256 ${sha256(jar)} its record has. " +
                "Asking ${address}")
        addable > 0
        lines.drop(addable).any { it.startsWith('    grails-quartz ') }

        when: 'no repository has it'
        exit = listWithoutRepositories(clone)

        then: 'what the application has is listed, and the list says which jar it needs'
        exit == 0
        out.toString().startsWith("| The feature pack acme-audit of the application is fetched again as ${COORDINATES}")
        out.toString().contains('\n| The application shop (web) has ')
        out.toString().replaceAll(/\s+/, ' ').contains('| The features feature add can add to it are listed with the feature packs it was generated with, ' +
                'which feature add needs too: give them to feature list as to feature add. The feature pack acme-audit of the application could not be had again: ')
        out.toString().contains(COORDINATES)
        !out.toString().contains('can be added to it with')

        when: 'a feature is added in the clone, with the repository'
        exit = add(clone, 'grails-quartz', '--feature-pack-repository', address)

        then: 'the jar is fetched again, and said to be'
        exit == 0
        out.toString().contains("The feature pack acme-audit of the application is fetched again as ${COORDINATES}, with the SHA-256 ${sha256(jar)} its record has. " +
                "Asking ${address}")
        new File(clone, 'build.gradle').text.contains('quartz')
        new File(clone, '.grails/forge.lock.yml').text.contains("coordinates: ${PLUGIN}\n  version: 2.3.0\n  sha256: ${sha256(jar)}\n")

        when: 'without the network, with the jar of the record kept as a file'
        File kept = new File(work, 'kept.jar')
        Files.copy(jar.toPath(), kept.toPath())
        File offline = copy(app, new File(work, 'offline'))
        exit = add(offline, 'grails-quartz', '--offline', '--feature-pack-jar', kept.absolutePath)

        then:
        exit == 0
        !out.toString().contains('is fetched again')
        new File(offline, 'build.gradle').text.contains('quartz')

        when: 'without the network, from a repository that is a directory'
        File fromDirectory = copy(app, new File(work, 'from-directory'))
        exit = add(fromDirectory, 'grails-quartz', '--offline', '--feature-pack-repository', address)

        then:
        exit == 0

        when: 'the repository has another jar under those coordinates'
        publish(COORDINATES, 'changed by someone')
        File again = copy(app, new File(work, 'again'))
        Map<String, String> before = tree(again)
        exit = add(again, 'grails-quartz', '--feature-pack-repository', address)

        then: 'it is not taken'
        exit == 1
        err.toString().contains(COORDINATES)
        err.toString().contains(sha256(jar))
        tree(again) == before

        when: 'no repository has it'
        File nowhere = copy(app, new File(work, 'nowhere'))
        exit = addWithoutRepositories(nowhere, 'grails-quartz')

        then:
        exit == 1
        err.toString().contains(COORDINATES)
        asked.every { URI each -> each.toString().contains('com/acme/acme-audit-grails/2.3.0') }
        !new File(nowhere, 'build.gradle').text.contains('quartz')
    }

    void "a pack requires and conflicts with the feature of the pack of a plugin by its full name, when the application is created and when a feature is added"() {
        given: 'the pack of a plugin, and packs of directories that name its feature by its full name'
        publish(BASE, 'the base', BASE_DESCRIPTOR)
        File audit = pack(new File(work, 'audit'), 'kept as it is', 'acme-audit', "requires: ['${BASE_PLUGIN}#acme-base']\n")
        File other = pack(new File(work, 'other'), 'kept as it is', 'acme-other', "conflicts: ['${BASE_PLUGIN}#acme-base']\n")

        when: 'the plugin is not given'
        File without = new File(work, 'without')
        int exit = create(without, '--features', 'acme-audit', '--feature-pack-dir', audit.absolutePath)

        then: 'nothing is created, and the message names the plugin'
        exit == 2
        err.toString().contains("The feature acme-audit requires the feature acme-base of the plugin ${BASE_PLUGIN} (${BASE_PLUGIN}#acme-base), " +
                'and no feature pack of that plugin with that feature was given for this type of application')
        !new File(without, 'build.gradle').exists()

        when: 'it is given by its coordinates'
        File app = new File(work, 'app')
        exit = create(app, '--features', "acme-audit,${BASE}", '--feature-pack-dir', audit.absolutePath, '--feature-pack-repository', address)

        then: 'the feature of its pack is the one that was required'
        exit == 0
        new File(app, 'build.gradle').text.contains('implementation "com.acme:acme-base-grails:1.4.0"')
        new File(app, 'grails-app/conf/base.txt').text == 'the base'
        new File(app, '.grails/forge.lock.yml').text.contains("- name: acme-base\n  source: pack\n  coordinates: ${BASE_PLUGIN}\n  version: 1.4.0\n")

        when: 'a pack that conflicts with it is selected with it'
        File conflicting = new File(work, 'conflicting')
        exit = create(conflicting, '--features', "acme-other,${BASE}", '--feature-pack-dir', other.absolutePath, '--feature-pack-repository', address)

        then: 'nothing is created'
        exit == 2
        err.toString().contains('The feature acme-other cannot be used with the feature acme-base')
        !new File(conflicting, 'build.gradle').exists()

        when: 'a feature is added to the application, with its packs'
        exit = add(app, 'grails-quartz', '--feature-pack-dir', audit.absolutePath, '--feature-pack-repository', address)

        then: 'it is generated again with the feature the full name names, and gets the feature'
        exit == 0
        new File(app, 'build.gradle').text.contains('quartz')
        new File(app, 'build.gradle').text.contains('implementation "com.acme:acme-base-grails:1.4.0"')

        when: 'the features that can be added are listed with its packs'
        exit = list(app, '--feature-pack-dir', audit.absolutePath, '--feature-pack-repository', address)

        then:
        exit == 0
        out.toString().readLines().any { it.startsWith('    cache ') }
    }

    void "project show names the digest of the pack, and says what the later commands need, in its document too"() {
        given:
        File original = pack(new File(work, 'pack'), 'kept as it is')
        File app = new File(work, 'app')
        assert create(app, '--features', 'acme-audit', '--feature-pack-dir', original.absolutePath) == 0
        String content = (new File(app, '.grails/forge.lock.yml').text =~ /contentSha256: ([0-9a-f]{64})/)[0][1]

        when:
        int exit = show(app, '--json')
        Map document = new JsonSlurper().parseText(out.toString().readLines().find { it.startsWith('{') }) as Map

        then:
        exit == 0
        document.features.find { it.name == 'acme-audit' } == [name: 'acme-audit', source: 'pack', contentSha256: content]
        document.notes.any { String note -> note.startsWith('The feature acme-audit is that of a feature pack that is not part of Grails: feature add and project regenerate') }
    }

    private int create(File directory, String... arguments) {
        directory.mkdirs()
        String current = System.getProperty('user.dir')
        System.setProperty('user.dir', directory.absolutePath)
        try {
            out.reset()
            err.reset()
            return CliRunner.run(CreateAppCommand, beanContext, (['com.example.shop', '--inplace'] + arguments.toList()) as String[])
        } finally {
            System.setProperty('user.dir', current)
        }
    }

    private int add(File directory, String... arguments) {
        return inDirectory(directory) { CliRunner.run(FeatureAddCommand, beanContext, arguments) }
    }

    private int regenerate(File directory, String... arguments) {
        return inDirectory(directory) { CliRunner.run(ProjectRegenerateCommand, beanContext, arguments) }
    }

    private int show(File directory, String... arguments) {
        return inDirectory(directory) { CliRunner.run(ProjectShowCommand, beanContext, arguments) }
    }

    private int list(File directory, String... arguments) {
        return inDirectory(directory) { CliRunner.run(FeatureListCommand, beanContext, arguments) }
    }

    // feature list as addWithoutRepositories runs feature add
    private int listWithoutRepositories(File directory, String... arguments) {
        Transport transport = { URI asking ->
            asked << asking
            return new Reply(404, null, null)
        } as Transport
        FeatureListCommand command = new FeatureListCommand(beanContext.getBean(ProjectGenerator)) {

            @Override
            protected PackArtifacts createPackArtifacts() {
                return new PackArtifacts(transport)
            }

            @Override
            protected String grailsRepoUrl() {
                return null
            }
        }
        return inDirectory(directory) { new CommandLine(command, new GrailsPicocliFactory(beanContext)).execute(arguments) }
    }

    // with repositories that have nothing: GRAILS_REPO_URL names none, and Maven Central and the repository of the record are answers of this specification
    private int addWithoutRepositories(File directory, String... arguments) {
        Transport transport = { URI asking ->
            asked << asking
            return new Reply(404, null, null)
        } as Transport
        FeatureAddCommand command = new FeatureAddCommand(beanContext.getBean(ProjectGenerator)) {

            @Override
            protected PackArtifacts createPackArtifacts() {
                return new PackArtifacts(transport)
            }

            @Override
            protected String grailsRepoUrl() {
                return null
            }
        }
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

    private static File pack(File directory, String content, String name = 'acme-audit', String more = '') {
        new File(directory, 'templates').mkdirs()
        new File(directory, 'feature.yml').setText(CreateAppFeaturePackSpec.DESCRIPTOR.replace('name: acme-audit', "name: ${name}") + more, 'UTF-8')
        new File(directory, 'templates/audit.txt').setText(content, 'UTF-8')
        return directory
    }

    private File publish(String coordinates, String content, String descriptor = CreateAppFeaturePackSpec.DESCRIPTOR) {
        List<String> parts = coordinates.split(':').toList()
        File directory = new File(repository, "${parts[0].replace('.', '/')}/${parts[1]}/${parts[2]}")
        directory.mkdirs()
        File jar = new File(directory, "${parts[1]}-${parts[2]}.jar")
        new ZipOutputStream(new FileOutputStream(jar)).withCloseable { ZipOutputStream zip ->
            ['META-INF/grails-forge/feature.yml'        : descriptor,
             'META-INF/grails-forge/templates/audit.txt': content].each { String path, String text ->
                zip.putNextEntry(new ZipEntry(path))
                zip.write(text.getBytes('UTF-8'))
                zip.closeEntry()
            }
        }
        return jar
    }

    // a copy of a directory, as a clone of it elsewhere has it
    private static File copy(File from, File to) {
        Path source = from.toPath()
        Files.walk(source).withCloseable { stream ->
            stream.each { Path path ->
                Path target = to.toPath().resolve(source.relativize(path).toString())
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target)
                } else {
                    Files.copy(path, target, StandardCopyOption.COPY_ATTRIBUTES)
                }
            }
        }
        return to
    }

    private static Map<String, String> tree(File project) {
        Map<String, String> entries = new TreeMap<>()
        Files.walk(project.toPath()).withCloseable { stream ->
            stream.each { Path path ->
                String name = project.toPath().relativize(path).join('/')
                if (!name.startsWith('.grails/transactions') && Files.isRegularFile(path)) {
                    entries.put(name, MessageDigest.getInstance('SHA-256').digest(Files.readAllBytes(path)).encodeHex().toString())
                }
            }
        }
        return entries
    }

    private static String sha256(File file) {
        return MessageDigest.getInstance('SHA-256').digest(file.bytes).encodeHex().toString()
    }
}
