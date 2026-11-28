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
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import groovy.json.JsonSlurper
import org.apache.grails.forge.cli.pack.PackArtifacts
import org.apache.grails.forge.cli.pack.PackArtifacts.Reply
import org.apache.grails.forge.cli.pack.PackArtifacts.Transport
import org.apache.grails.forge.core.state.ProjectManifest
import org.apache.grails.forge.core.state.ProjectState
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.cli.CliRunner
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.GrailsPicocliFactory
import picocli.CommandLine

/**
 * The feature of a feature pack that is not part of Grails, added to an application that does not
 * have it: by the directory of the pack, by the coordinates of its plugin, whose jar is taken from a
 * repository, and by its name in a feature catalog. The record then names the pack as that of an
 * application created with it does, and the next feature is added with the pack given again. Every
 * repository here is a directory, and nothing is asked of the network.
 */
class FeatureAddPackSpec extends CommandSpec implements CommandFixture {

    static final String PLUGIN = 'com.acme:acme-audit-grails'
    static final String COORDINATES = PLUGIN + ':2.3.0'
    static final String RANGE = '[9.0.0,10.0.0)'

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()
    File work
    File home
    String previousHome
    File repository
    String address
    File app
    List<URI> asked = []
    // what the repositories of the network of this specification answer, by address: nothing, unless it is put here
    Map<String, byte[]> answers = [:]

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out))
        System.setErr(new PrintStream(err))
        work = Files.createTempDirectory('grailsforgeaddpack').toFile()
        repository = new File(work, 'repository')
        repository.mkdirs()
        address = repository.toPath().toAbsolutePath().normalize().toUri().toString()
        // the journal of a change is kept in the home directory of whoever runs the command
        home = new File(work, 'home')
        home.mkdirs()
        previousHome = System.getProperty('user.home')
        System.setProperty('user.home', home.absolutePath)
        app = new File(work, 'app')
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        System.setProperty('user.home', previousHome)
        work.deleteDir()
    }

    void "the pack of a directory is added, the application is the one created with it, and the next feature is added with the pack given again"() {
        given: 'an application, and the same one created with the pack'
        File pack = pack(new File(work, 'pack'), 'kept as it is')
        File with = new File(work, 'with')
        assert create(app) == 0
        assert create(with, '--features', 'acme-audit', '--feature-pack-dir', pack.absolutePath) == 0
        Map<String, String> before = tree(app)

        when: 'what adding it comes to is asked for'
        int exit = add(app, 'acme-audit', '--feature-pack-dir', pack.absolutePath, '--dry-run')

        then: 'where the pack comes from and what it adds is said, and nothing is written'
        exit == 0
        out.toString().contains("Feature pack acme-audit from ${pack.absolutePath}")
        out.toString().contains('To add the feature acme-audit')
        out.toString() =~ /add +grails-app\/conf\/com\/example\/audit\.txt/
        tree(app) == before

        when:
        exit = add(app, 'acme-audit', '--feature-pack-dir', pack.absolutePath)

        then: 'the pack is said before the files are, and what the later commands need afterwards'
        exit == 0
        err.toString().isEmpty()
        out.toString().indexOf('Feature pack acme-audit from') < out.toString().indexOf('Adding the feature acme-audit')
        out.toString().contains('Feature acme-audit added')
        out.toString().contains('The feature acme-audit is that of a feature pack that is not part of Grails: feature add and project regenerate work for the ' +
                'application only with that very pack')

        and: 'the application and its record are those of the one that was created with the pack'
        tree(app) == tree(with)
        new File(app, '.grails/forge.lock.yml').text =~ /- name: acme-audit\n  source: pack\n  contentSha256: [0-9a-f]{64}\n/
        new File(app, '.grails/project.yml').text.contains('requestedFeatures:\n- name: acme-audit\n')
        new File(app, 'build.gradle').text.contains('implementation "com.acme:acme-audit-grails:2.3.0"')
        ProjectState.read(app.toPath()).verifyBaseline(app.toPath())

        when: 'it is added again'
        Map<String, String> added = tree(app)
        exit = add(app, 'acme-audit', '--feature-pack-dir', pack.absolutePath)

        then: 'there is nothing to do'
        exit == 0
        out.toString().contains('The application already has the feature acme-audit')
        out.toString().contains('Nothing was changed')
        tree(app) == added

        when: 'a feature of Grails is added without the pack'
        exit = add(app, 'grails-quartz')

        then: 'the application is generated again only with it'
        exit == 1
        err.toString().contains('The application has the feature of feature packs that are not part of Grails, and that pack was not given')
        tree(app) == added

        when: 'and with it'
        exit = add(app, 'grails-quartz', '--feature-pack-dir', pack.absolutePath)

        then:
        exit == 0
        new File(app, 'build.gradle').text.contains('quartz')
        ProjectState.read(app.toPath()).lock.featureNames.containsAll(['acme-audit', 'grails-quartz'])

        and: 'the commands that show and compare the application take it as one that was created with the pack'
        regenerate(app, '--diff', '--feature-pack-dir', pack.absolutePath) == 0
        list(app, '--feature-pack-dir', pack.absolutePath) == 0
        out.toString() =~ /asked for\s+acme-audit/
    }

    void "the plugin that is named by its coordinates is taken from a repository, recorded by its coordinates and its SHA-256, and fetched again for the next feature"() {
        given:
        File jar = publish(COORDINATES, 'kept as it is')
        File with = new File(work, 'with')
        assert create(app) == 0
        assert create(with, '--features', COORDINATES, '--feature-pack-repository', address) == 0
        Map<String, String> before = tree(app)

        when: 'the jar is to have another SHA-256 than it has'
        int exit = add(app, COORDINATES, '--feature-pack-repository', address, '--feature-pack-sha256', "${COORDINATES}=${'ab' * 32}")

        then: 'it is refused before it is opened, and nothing is written'
        exit == 1
        err.toString().contains('ab' * 32)
        tree(app) == before

        when: 'nothing is asked of the network, and the repository is one of it'
        exit = add(app, COORDINATES, '--feature-pack-repository', 'https://repo.acme.invalid/maven', '--offline')

        then:
        exit == 1
        err.toString().contains('--offline')
        asked.isEmpty()
        tree(app) == before

        when: 'nothing is asked of the network, and the repository is a directory'
        exit = add(app, COORDINATES, '--feature-pack-repository', address, '--offline', '--feature-pack-sha256', "${COORDINATES}=${sha256(jar)}")

        then:
        done(exit)
        asked.isEmpty()
        out.toString().contains("Feature pack acme-audit from ${COORDINATES} (${address}), sha256 ${sha256(jar)}")
        out.toString().contains('    the jar has the SHA-256 that was asked for with --feature-pack-sha256')
        out.toString().contains('Feature acme-audit added')

        and: 'the record is that of the application that was created with the plugin'
        tree(app) == tree(with)
        new File(app, '.grails/project.yml').text.contains("requestedFeatures:\n- name: acme-audit\n  coordinates: ${PLUGIN}\n  version: 2.3.0\n")
        new File(app, '.grails/forge.lock.yml').text.contains("coordinates: ${PLUGIN}\n  version: 2.3.0\n  sha256: ${sha256(jar)}\n")

        when: 'the plugin is named again'
        Map<String, String> added = tree(app)
        exit = add(app, COORDINATES, '--feature-pack-repository', address)

        then: 'it is the feature the application has, whose jar is fetched again as for every command that generates the application'
        done(exit)
        out.toString().contains("The feature pack acme-audit of the application is fetched again as ${COORDINATES}")
        out.toString().contains('The application already has the feature acme-audit')
        out.toString().contains('Nothing was changed')
        tree(app) == added

        when: 'another version of the plugin is named'
        publish(PLUGIN + ':2.4.0', 'another')
        exit = add(app, PLUGIN + ':2.4.0', '--feature-pack-repository', address)

        then: 'the pack the application has is not replaced by a command'
        exit == 1
        err.toString().contains("The application has the feature acme-audit already, of the feature pack of the jar ${COORDINATES} with the SHA-256 ${sha256(jar)}")
        tree(app) == added

        when: 'a feature of Grails is added'
        exit = add(app, 'grails-quartz', '--feature-pack-repository', address)

        then: 'the jar is fetched again by what the record has of it'
        exit == 0
        out.toString().contains("The feature pack acme-audit of the application is fetched again as ${COORDINATES}, with the SHA-256 ${sha256(jar)} its record has")
        ProjectState.read(app.toPath()).lock.featureNames.containsAll(['acme-audit', 'grails-quartz'])
    }

    void "a name is looked up in a feature catalog, as the create commands look it up, and the record names the catalog"() {
        given:
        File jar = publish(COORDINATES, 'kept as it is')
        File catalog = new File(work, 'catalog.json')
        catalog.setText('[{"coords": "' + PLUGIN + '", "owner": "Acme", "licenses": ["Apache-2.0"], "versions": [{"version": "2.3.0", "date": "2026-10-04T18:46:18Z", ' +
                '"pack": {"format": 1, "kind": "feature", "name": "acme-audit", "templateApi": 1, "compatibility": {"grails": "' + RANGE + '", "forge": "' + RANGE + '"}, ' +
                '"sha256": "' + sha256(jar) + '"}}]}]', 'UTF-8')
        assert create(app) == 0
        Map<String, String> before = tree(app)

        when: 'no catalog is named'
        int exit = add(app, 'acme-audit')

        then: 'the name is no feature'
        exit == 2
        err.toString().contains('acme-audit')
        tree(app) == before

        when:
        exit = add(app, 'acme-audit', '--feature-catalog', catalog.toURI().toString(), '--feature-pack-repository', address)

        then: 'which plugin the catalog made of the name is said before the plugin is asked for'
        done(exit)
        String taken = "The feature acme-audit is taken as ${COORDINATES}, which the feature catalog ${catalog.toPath().toUri()} (sha256 ${sha256(catalog)}) " +
                "names for it, with the SHA-256 ${sha256(jar)}. Asking ${address}"
        out.toString().contains(taken)
        out.toString().indexOf(taken) < out.toString().indexOf('Feature pack acme-audit from')
        out.toString().contains("    the coordinates are those the feature catalog ${catalog.toPath().toUri()} (sha256 ${sha256(catalog)}) has for the feature acme-audit")
        out.toString().contains('Feature acme-audit added')

        and:
        new File(app, '.grails/project.yml').text.contains("requestedFeatures:\n- name: acme-audit\n  coordinates: ${PLUGIN}\n  version: 2.3.0\n")
        new File(app, '.grails/forge.lock.yml').text.contains("catalogSha256: ${sha256(catalog)}\n")
        new File(app, 'grails-app/conf/com/example/audit.txt').text == 'kept as it is'

        when: 'the full name of the feature is given, of the plugin the application has it of'
        Map<String, String> added = tree(app)
        exit = add(app, "${PLUGIN}#acme-audit", '--feature-pack-repository', address)

        then:
        done(exit)
        out.toString().contains('The application already has the feature acme-audit')
        tree(app) == added
    }

    void "a file of the pack that the application has otherwise is a conflict, and nothing is written, the record among it"() {
        given:
        File pack = pack(new File(work, 'pack'), 'kept as it is')
        assert create(app) == 0
        File own = new File(app, 'grails-app/conf/com/example/audit.txt')
        own.parentFile.mkdirs()
        own.text = 'of the application\n'
        Map<String, String> before = tree(app)

        when:
        int exit = add(app, 'acme-audit', '--feature-pack-dir', pack.absolutePath)

        then:
        exit == 1
        out.toString() =~ /conflict +grails-app\/conf\/com\/example\/audit\.txt: the feature adds this file, and the project has another file of its name/
        err.toString().contains('The feature was not added')
        tree(app) == before
        !new File(app, '.grails/forge.lock.yml').text.contains('acme-audit')
    }

    void "a preset is not added, and neither is a pack whose feature is not among those that are named"() {
        given:
        File pack = pack(new File(work, 'pack'), 'kept as it is')
        File preset = new File(work, 'preset')
        preset.mkdirs()
        new File(preset, 'feature.yml').setText("format: 1\nkind: preset\nname: acme-service\ntemplateApi: 1\ncompatibility:\n  grails: '${RANGE}'\n  forge: '${RANGE}'\n" +
                'title: Acme Service\ndescription: How Acme makes a service\napplicationType: web\n', 'UTF-8')
        assert create(app) == 0
        Map<String, String> before = tree(app)

        when:
        int exit = add(app, 'acme-service', '--feature-pack-dir', preset.absolutePath)

        then:
        exit == 2
        err.toString().contains('acme-service is a preset, and a preset is not added to an application: an application is made from a preset when it is created, with --preset')
        tree(app) == before

        when: 'a pack is given, and a feature of Grails is named'
        exit = add(app, 'grails-quartz', '--feature-pack-dir', pack.absolutePath)

        then:
        exit == 1
        err.toString().contains('The feature pack acme-audit was given, and its feature is not among the features that are added')
        tree(app) == before
    }

    void "what a pack requires comes with it, and a pack that conflicts with a feature the application has is refused, as when an application is created"() {
        given:
        File requiring = pack(new File(work, 'requiring'), 'kept as it is')
        new File(requiring, 'feature.yml') << "requires: ['cache']\n"
        File conflicting = pack(new File(work, 'conflicting'), 'kept as it is')
        new File(conflicting, 'feature.yml') << "conflicts: ['h2']\n"
        assert create(app) == 0
        Map<String, String> before = tree(app)

        when: 'the pack does not go with a feature the application has'
        int exit = add(app, 'acme-audit', '--feature-pack-dir', conflicting.absolutePath)

        then:
        exit == 2
        err.toString().contains('h2')
        tree(app) == before

        when:
        exit = add(app, 'acme-audit', '--feature-pack-dir', requiring.absolutePath)

        then: 'the feature of Grails it requires is added with it, and said'
        done(exit)
        out.toString().contains('Adding the feature acme-audit, and with it cache')
        ProjectState.read(app.toPath()).lock.featureNames.containsAll(['acme-audit', 'cache'])
        ProjectState.read(app.toPath()).manifest.requestedFeatures*.name == ['acme-audit']
        new File(app, 'build.gradle').text.contains('org.apache.grails:grails-cache')
    }

    void "the options of a pack that is added are given with --feature-option, recorded, and used again when the application is generated again"() {
        given: 'a pack with a parameter that is a choice, and a template that writes it'
        File pack = new File(work, 'pack')
        new File(pack, 'templates').mkdirs()
        new File(pack, 'feature.yml').setText(FeatureOptionSpec.DESCRIPTOR, 'UTF-8')
        new File(pack, 'templates/audit.properties.gsp').setText(FeatureOptionSpec.TEMPLATE, 'UTF-8')
        File with = new File(work, 'with')
        assert create(app) == 0
        assert create(with, '--features', 'acme-audit', '--feature-pack-dir', pack.absolutePath, '--feature-option', 'acme-audit.store=collection') == 0
        Map<String, String> before = tree(app)

        when: 'a value that is none of the choices'
        int exit = add(app, 'acme-audit', '--feature-pack-dir', pack.absolutePath, '--feature-option', 'acme-audit.store=nowhere')

        then:
        exit == 2
        err.toString().contains('nowhere')
        tree(app) == before

        when: 'a value that is not the default'
        exit = add(app, 'acme-audit', '--feature-pack-dir', pack.absolutePath, '--feature-option', 'acme-audit.store=collection')

        then: 'the template has it, the record has it, and the application is the one that was created with the pack and the option'
        done(exit)
        new File(app, 'grails-app/conf/audit.properties').text == 'audit.store=collection\naudit.retention=90\n'
        new File(app, '.grails/project.yml').text.contains('featureOptions:')
        new File(app, '.grails/project.yml').text.contains('store: collection')
        ProjectState.read(app.toPath()).manifest.options.featureOptions == ['acme-audit': [store: 'collection']]
        tree(app) == tree(with)

        and: 'the application is generated again as it is, from its record and the pack'
        regenerate(app, '--diff', '--feature-pack-dir', pack.absolutePath) == 0

        when: 'a feature of Grails is added later'
        exit = add(app, 'grails-quartz', '--feature-pack-dir', pack.absolutePath)

        then: 'the pack is used again with the option of the record'
        done(exit)
        new File(app, 'grails-app/conf/audit.properties').text == 'audit.store=collection\naudit.retention=90\n'
        ProjectState.read(app.toPath()).manifest.options.featureOptions == ['acme-audit': [store: 'collection']]
        regenerate(app, '--diff', '--feature-pack-dir', pack.absolutePath) == 0
    }

    void "what the pack that is added says is to be done next is said after the feature is added, and is in the document"() {
        given:
        File pack = pack(new File(work, 'pack'), 'kept as it is')
        new File(pack, 'feature.yml') << PackInstructionsSpec.INSTRUCTIONS
        assert create(app) == 0

        when:
        int exit = add(app, 'acme-audit', '--feature-pack-dir', pack.absolutePath, '--dry-run', '--json')
        Map document = new JsonSlurper().parseText(out.toString()) as Map

        then:
        exit == 0
        (document.instructions as List<Map>)*.feature == ['acme-audit']
        ((document.instructions as List<Map>)[0].text as String).startsWith('Run `./gradlew dbmGormDiff`')

        when:
        exit = add(app, 'acme-audit', '--feature-pack-dir', pack.absolutePath)
        List<String> lines = out.toString().readLines()

        then:
        done(exit)
        lines.indexOf('| The feature acme-audit says what to do next:') > lines.findIndexOf { String line -> line.startsWith('| Feature acme-audit added') }
        lines.contains('    Run `./gradlew dbmGormDiff` to generate the migration of the audit tables.')
    }

    void "the jar of a plugin that was fetched is kept, and added to another application without a request; --refresh fetches it again"() {
        given: 'a repository of the network that has the jar'
        File jar = publish(COORDINATES, 'kept as it is')
        String remote = 'https://repo.acme.example/maven/'
        answers.put("${remote}com/acme/acme-audit-grails/2.3.0/acme-audit-grails-2.3.0.jar".toString(), jar.bytes)
        File other = new File(work, 'other')
        File third = new File(work, 'third')
        assert create(app) == 0
        assert create(other) == 0
        assert create(third) == 0

        when:
        int exit = add(app, COORDINATES, '--feature-pack-repository', remote)

        then:
        done(exit)
        asked*.toString().contains("${remote}com/acme/acme-audit-grails/2.3.0/acme-audit-grails-2.3.0.jar".toString())

        when: 'it is added to another application, with the SHA-256 it has to have'
        asked.clear()
        exit = add(other, COORDINATES, '--feature-pack-repository', remote, '--feature-pack-sha256', "${COORDINATES}=${sha256(jar)}")

        then: 'the copy that is kept is the jar, and nothing is asked for'
        done(exit)
        asked.isEmpty()
        new File(other, '.grails/forge.lock.yml').text.contains("sha256: ${sha256(jar)}\n")

        when: 'and to a third with --refresh, where the repository has it no more'
        answers.clear()
        Map<String, String> before = tree(third)
        exit = add(third, COORDINATES, '--feature-pack-repository', remote, '--feature-pack-sha256', "${COORDINATES}=${sha256(jar)}", '--refresh')

        then: 'the repository is asked, and the feature is not added'
        exit == 1
        !asked.isEmpty()
        tree(third) == before
    }

    void "feature diff shows what feature add of the plugin that is named by its coordinates would change, and writes nothing"() {
        given:
        publish(COORDINATES, 'kept as it is')
        assert create(app) == 0
        Map<String, String> before = tree(app)

        when:
        int exit = diff(app, COORDINATES, '--feature-pack-repository', address)

        then:
        exit == 0
        out.toString().contains('+    implementation "com.acme:acme-audit-grails:2.3.0"')
        out.toString().contains('+kept as it is')
        tree(app) == before
    }

    void "what adding the feature of a plugin comes to is one JSON document, with where the pack comes from among its notes"() {
        given:
        File jar = publish(COORDINATES, 'kept as it is')
        assert create(app) == 0
        Map<String, String> before = tree(app)

        when:
        int exit = add(app, COORDINATES, '--feature-pack-repository', address, '--dry-run', '--json')
        Map document = new JsonSlurper().parseText(out.toString()) as Map

        then:
        exit == 0
        document.outcome == 'changes'
        document.requested == ['acme-audit']
        (document.notes as List<String>).any { String note -> note.startsWith("Feature pack acme-audit from ${COORDINATES}") && note.endsWith("sha256 ${sha256(jar)}") }
        (document.files as List<Map>).any { Map file -> file.path == 'grails-app/conf/com/example/audit.txt' && file.action == 'add' }
        tree(app) == before
    }

    void "the feature of a pack is added to an application of Grails 8 that project migrate gave its record"() {
        given:
        File pack = pack(new File(work, 'pack'), 'kept as it is')
        app.mkdirs()
        EarlierApplication.copy(EarlierApplication.GRAILS_8_FORGE, app)
        EarlierApplication.upgrade(app)
        assert inDirectory(app) { CliRunner.run(ProjectMigrateCommand, beanContext) } == 0
        String build = new File(app, 'build.gradle').text

        when:
        int exit = add(app, 'acme-audit', '--feature-pack-dir', pack.absolutePath)
        ProjectState state = ProjectState.read(app.toPath())

        then: 'the lines of the application are kept, and the record is still the one that was written for it'
        exit == 0
        new File(app, 'build.gradle').text.contains('implementation "com.acme:acme-audit-grails:2.3.0"')
        new File(app, 'build.gradle').readLines().containsAll(build.readLines())
        new File(app, 'grails-app/conf/com/example/audit.txt').text == 'kept as it is'
        state.manifest.generationState == ProjectManifest.ADOPTED
        state.lock.featureNames.contains('acme-audit')
        state.verifyBaseline(app.toPath())
    }

    // a command that ended as it should, or what it said
    private boolean done(int exit) {
        assert exit == 0: "${out}\n${err}"
        return true
    }

    private int create(File directory, String... arguments) {
        directory.mkdirs()
        return inDirectory(directory) { CliRunner.run(CreateAppCommand, beanContext, (['com.example.shop', '--inplace'] + arguments.toList()) as String[]) }
    }

    // with repositories of the network that have nothing: GRAILS_REPO_URL names none, and what is asked of the network is kept
    private int add(File directory, String... arguments) {
        Transport transport = transport()
        File catalogHome = home
        FeatureAddCommand command = new FeatureAddCommand(beanContext.getBean(ProjectGenerator)) {

            @Override
            protected PackArtifacts createPackArtifacts() {
                // the jars that are fetched are kept under the home of the test, as the command keeps them under that of the user
                return new PackArtifacts(transport).keepingJarsUnder(catalogHome.toPath())
            }

            @Override
            protected String grailsRepoUrl() {
                return null
            }

            @Override
            protected String featureCatalogSetting() {
                return null
            }

            @Override
            protected String pluginIndexSetting() {
                return null
            }

            @Override
            protected Path featureCatalogHome() {
                return catalogHome.toPath()
            }
        }
        return inDirectory(directory) { new CommandLine(command, new GrailsPicocliFactory(beanContext)).execute(arguments) }
    }

    private Transport transport() {
        return { URI asking ->
            asked << asking
            byte[] answer = answers.get(asking.toString())
            return answer == null ? new Reply(404, null, null) : new Reply(200, null, new ByteArrayInputStream(answer))
        } as Transport
    }

    // feature diff with the repositories of this specification
    private int diff(File directory, String... arguments) {
        Transport transport = transport()
        FeatureDiffCommand command = new FeatureDiffCommand(beanContext.getBean(ProjectGenerator)) {

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

    private int regenerate(File directory, String... arguments) {
        return inDirectory(directory) { CliRunner.run(ProjectRegenerateCommand, beanContext, arguments) }
    }

    private int list(File directory, String... arguments) {
        return inDirectory(directory) { CliRunner.run(FeatureListCommand, beanContext, arguments) }
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

    private static File pack(File directory, String content) {
        new File(directory, 'templates').mkdirs()
        new File(directory, 'feature.yml').setText(CreateAppFeaturePackSpec.DESCRIPTOR, 'UTF-8')
        new File(directory, 'templates/audit.txt').setText(content, 'UTF-8')
        return directory
    }

    private File publish(String coordinates, String content) {
        List<String> parts = coordinates.split(':').toList()
        File directory = new File(repository, "${parts[0].replace('.', '/')}/${parts[1]}/${parts[2]}")
        directory.mkdirs()
        File jar = new File(directory, "${parts[1]}-${parts[2]}.jar")
        new ZipOutputStream(new FileOutputStream(jar)).withCloseable { ZipOutputStream zip ->
            ['META-INF/grails-forge/feature.yml'        : CreateAppFeaturePackSpec.DESCRIPTOR,
             'META-INF/grails-forge/templates/audit.txt': content].each { String path, String text ->
                zip.putNextEntry(new ZipEntry(path))
                zip.write(text.getBytes('UTF-8'))
                zip.closeEntry()
            }
        }
        return jar
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
