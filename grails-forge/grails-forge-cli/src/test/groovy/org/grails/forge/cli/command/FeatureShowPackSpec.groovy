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
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.cli.CliRunner
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import org.grails.forge.cli.GrailsPicocliFactory
import picocli.CommandLine
import spock.lang.Unroll

/**
 * {@code feature show} and {@code feature find} of the feature of a feature pack the application does
 * not have yet, which is named as {@code feature add} takes it: by the directory of its pack, by the
 * coordinates of its plugin, whose jar is taken from a repository and kept, and by its name in a
 * feature catalog. What they say of it is what {@code feature add} would do with it. The repository of
 * the network is an answer this specification gives itself and counts the requests of; every other
 * repository and every catalog is a file.
 */
class FeatureShowPackSpec extends CommandSpec {

    static final String PLUGIN = 'com.acme:acme-audit-grails'
    static final String COORDINATES = PLUGIN + ':2.3.0'
    static final String RANGE = '[9.0.0,10.0.0)'
    static final String REMOTE = 'https://repository.invalid/maven/'
    static final String JAR = REMOTE + 'com/acme/acme-audit-grails/2.3.0/acme-audit-grails-2.3.0.jar'
    static final String DESCRIPTOR = FeatureOptionSpec.DESCRIPTOR + PackInstructionsSpec.INSTRUCTIONS + '''\
        artefacts:
          - name: audit-report
            description: Creates a report of audit events
            template: artefacts/AuditReport.groovy
            to: src/main/groovy/{packagePath}/{className}AuditReport.groovy
        '''.stripIndent()
    static final Map<String, String> TEMPLATES = ['audit.properties.gsp'        : FeatureOptionSpec.TEMPLATE,
                                                  'artefacts/AuditReport.groovy': PackArtefactCommandsSpec.REPORT]
    static final String PRESET_REFUSED = 'which is not a feature: an application is made from a preset when it is created, with --preset, ' +
            'and a preset is not added to one that is there'

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()
    File work
    File home
    File jar
    String repository
    String catalog
    List<URI> asked = []
    Map<String, byte[]> answers = [:]

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(err, true))
        work = Files.createTempDirectory('grailsforgeshowpack').toFile().canonicalFile
        home = new File(work, 'home')
        home.mkdirs()
        jar = publish(COORDINATES, DESCRIPTOR, TEMPLATES)
        repository = new File(work, 'repository').toPath().toUri().toString()
        File catalogFile = new File(work, 'catalog.json')
        catalogFile.setText('[{"coords": "' + PLUGIN + '", "name": "Acme Audit", "desc": "Keeps a trail of what was changed", "owner": "Acme", "licenses": ["Apache-2.0"], ' +
                '"versions": [{"version": "2.3.0", "date": "2026-10-04T18:46:18Z", ' +
                '"pack": {"format": 1, "kind": "feature", "name": "acme-audit", "templateApi": 1, "compatibility": {"grails": "' + RANGE + '", "forge": "' + RANGE + '"}, ' +
                '"sha256": "' + sha256(jar) + '"}}]}]', 'UTF-8')
        catalog = catalogFile.toURI().toString()
        assert CliRunner.run(CreateAppCommand, beanContext, 'com.example.shop', '--inplace') == 0
        reset()
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        work.deleteDir()
    }

    void "feature show says of the feature of a pack the application does not have what feature add would do with it, by the directory of the pack"() {
        given:
        File pack = directoryPack()
        Map<String, String> before = tree()

        when:
        int exit = show('acme-audit', '--feature-pack-dir', pack.absolutePath)
        List<String> lines = out.toString().readLines()

        then: 'the feature, that the application does not have it, and where its pack is'
        exit == 0
        err.toString().isEmpty()
        lines[0] == 'Feature: acme-audit (Acme Audit Trail)'
        lines.contains('* In this application: no')
        lines.any { it.startsWith('* Feature pack: acme-audit, of ') && it.contains(pack.name) && it.endsWith('The application does not have it: feature add takes it as it was taken here') }

        and: 'the options it takes, the commands it gives and what its pack says to do'
        lines.contains('* Options, each given as --feature-option acme-audit.NAME=VALUE:')
        lines.contains('    store=table|collection (default table)  Where audit events are kept')
        lines.contains('    retention=text (default 90)')
        lines.contains('    console=true|false (default false)')
        lines.contains('* Commands it gives the command line of an application that has it:')
        lines.contains('    create-audit-report  Creates a report of audit events')
        lines.contains('* What its feature pack says to do once it is added:')
        lines.contains('    Run `./gradlew dbmGormDiff` to generate the migration of the audit tables.')

        and: 'what feature add would change in this application'
        lines.any { it.startsWith('* In this application feature add would change ') }
        lines.contains('    added    grails-app/conf/audit.properties')
        lines.any { it.startsWith('             + ') && it.contains('com.acme:acme-audit-grails:2.3.0') }
        !lines.any { it.startsWith('* feature add does not add it') }

        and:
        tree() == before

        when: 'as a document, beside what feature add --dry-run --json says of the same'
        reset()
        exit = show('acme-audit', '--feature-pack-dir', pack.absolutePath, '--json')
        Map shown = ((Map) new JsonSlurper().parseText(out.toString())).feature as Map
        reset()
        assert ForgeCli.execute(beanContext, 'feature', 'add', 'acme-audit', '--feature-pack-dir', pack.absolutePath, '--dry-run', '--json') == 0
        Map added = new JsonSlurper().parseText(out.toString()) as Map

        then:
        exit == 0
        shown.inApplication == false
        shown.addable == true
        shown.notAddedBecause == null
        shown.contributionTo == 'application'
        ((Map) shown.featurePack).name == 'acme-audit'
        ((Map) shown.featurePack).inApplication == false
        ((Map) shown.featurePack).contentSha256 ==~ /[0-9a-f]{64}/
        ((Map) shown.featurePack).coordinates == null
        ((List<Map>) shown.options).collect { [it.name, it.type, it.default, it.choices, it.option] } == [
                ['store', 'choice', 'table', ['table', 'collection'], '--feature-option acme-audit.store=VALUE'],
                ['retention', 'string', '90', null, '--feature-option acme-audit.retention=VALUE'],
                ['console', 'boolean', false, null, '--feature-option acme-audit.console=VALUE']]
        ((List<Map>) shown.options)[0].description == 'Where audit events are kept'
        shown.commands == [[name: 'create-audit-report', description: 'Creates a report of audit events']]
        shown.instructions == ['Run `./gradlew dbmGormDiff` to generate the migration of the audit tables.', 'Then commit grails-app/migrations.']

        and: 'the files are those feature add would add and change'
        ((List<Map>) shown.files)*.path as Set == ((List<Map>) added.files).findAll { !((String) it.path).startsWith('.grails/') }*.path as Set
    }

    void "the jar of a plugin that is named by its coordinates is held to its SHA-256 and kept, and shown again without a request"() {
        given: 'a repository of the network that has the jar'
        answers[JAR] = jar.bytes
        answers[JAR + '.sha256'] = sha256(jar).bytes
        Map<String, String> before = tree()

        when:
        int exit = show(COORDINATES, '--feature-pack-repository', REMOTE, '--feature-pack-sha256', "${COORDINATES}=${sha256(jar)}")
        List<String> lines = out.toString().readLines()

        then: 'the jar was fetched, and the feature is said with its plugin, the digest of the jar and how it was held to it'
        exit == 0
        asked*.toString().contains(JAR)
        lines[0] == 'Feature: acme-audit (Acme Audit Trail)'
        lines.contains("* Feature pack: acme-audit, of the plugin ${COORDINATES}, sha256 ${sha256(jar)}. The application does not have it: feature add takes it as it was taken here".toString())
        lines.contains('    the jar has the SHA-256 that was asked for with --feature-pack-sha256')
        lines.contains('    added    grails-app/conf/audit.properties')
        Files.isRegularFile(home.toPath().resolve(".grails/forge/packs/${sha256(jar)}.jar"))
        tree() == before

        when: 'it is shown again, as a document'
        asked.clear()
        reset()
        exit = show(COORDINATES, '--feature-pack-repository', REMOTE, '--feature-pack-sha256', "${COORDINATES}=${sha256(jar)}", '--json')
        Map pack = ((Map) ((Map) new JsonSlurper().parseText(out.toString())).feature).featurePack as Map

        then: 'no repository is asked: the jar is the copy that is kept'
        exit == 0
        asked.isEmpty()
        pack.coordinates == COORDINATES
        pack.sha256 == sha256(jar)
        pack.repository == REMOTE
        pack.inApplication == false
        ((List<String>) pack.provenance).any { it.startsWith('the jar is the copy that is kept on this computer') }

        when: 'another SHA-256 is asked for, which no kept jar has'
        reset()
        exit = show(COORDINATES, '--feature-pack-repository', REMOTE, '--feature-pack-sha256', "${COORDINATES}=${'ab' * 32}")

        then: 'the jar of the repository is refused before it is opened, as feature add refuses it'
        exit == 1
        out.toString().isEmpty()
        err.toString().contains("checksum mismatch: the sha256 of acme-audit-grails-2.3.0.jar is ${sha256(jar)}, and ${'ab' * 32} was asked for")
        tree() == before
    }

    @Unroll
    void "feature show takes #what from a feature catalog, as feature add does"() {
        when:
        int exit = show(named, '--feature-catalog', catalog, '--feature-pack-repository', repository, '--json')
        Map document = new JsonSlurper().parseText(out.toString()) as Map
        Map pack = ((Map) document.feature).featurePack as Map

        then: 'which plugin the catalog made of the name is said, and the pack names the catalog'
        exit == 0
        ((List<String>) document.notes).any { it.startsWith("The feature acme-audit is taken as ${COORDINATES}, which the feature catalog") }
        ((Map) document.feature).name == 'acme-audit'
        ((Map) document.feature).addable == true
        pack.coordinates == COORDINATES
        pack.sha256 == sha256(jar)
        pack.catalog != null
        pack.catalogSha256 ==~ /[0-9a-f]{64}/
        ((List<String>) pack.provenance).any { it.startsWith('the coordinates are those the feature catalog') }

        where:
        what            | named
        'a name'        | 'acme-audit'
        'a full name'   | PLUGIN + '#acme-audit'
    }

    void "a name no catalog has is no feature, and where it was looked for is said as feature add says it"() {
        when:
        int exit = show('acme-other', '--feature-catalog', catalog, '--feature-pack-repository', repository)
        String said = err.toString()
        reset()
        int added = ForgeCli.execute(beanContext, 'feature', 'add', 'acme-other', '--feature-catalog', catalog, '--feature-pack-repository', repository, '--dry-run')

        then:
        exit == 1
        added == 2
        said.contains('There is no feature acme-other for the application shop (web).')
        said.contains('catalog.json')
        err.toString().contains('catalog.json')
    }

    void "a preset is not shown as a feature: it is refused with what feature add says of it"() {
        given:
        publish('com.acme:acme-preset:1.0.0', CreatePresetSpec.PRESET, ['TEAM.md.gsp': CreatePresetSpec.TEMPLATE])

        when:
        int exit = show('com.acme:acme-preset:1.0.0', '--feature-pack-repository', repository)
        String said = err.toString()
        reset()
        int added = ForgeCli.execute(beanContext, 'feature', 'add', 'com.acme:acme-preset:1.0.0', '--feature-pack-repository', repository, '--dry-run')

        then:
        exit == 2
        added == 2
        out.toString().isEmpty()
        said.contains("com.acme:acme-preset:1.0.0 has the preset acme-service, ${PRESET_REFUSED}")
        err.toString().contains("com.acme:acme-preset:1.0.0 has the preset acme-service, ${PRESET_REFUSED}")
    }

    @Unroll
    void "#where feature show says what the feature of a plugin changes in a new application of the type, with the option it was given"() {
        given:
        if (outside) {
            System.setProperty('user.dir', work.absolutePath)
        }

        when:
        int exit = show(([COORDINATES, '--feature-pack-repository', repository, '--feature-option', 'acme-audit.store=collection'] + type) as String[])
        List<String> lines = out.toString().readLines()

        then:
        exit == 0
        lines[0] == 'Feature: acme-audit (Acme Audit Trail)'
        !lines.any { it.startsWith('* In this application') }
        lines.any { it.startsWith("* Feature pack: acme-audit, of the plugin ${COORDINATES}, sha256 ${sha256(jar)}") && !it.contains('does not have it') }
        lines.contains('    store=table|collection (default table)  Where audit events are kept')
        lines.any { it.startsWith("* In a new application of type ${named} it changes ") }
        lines.contains('    added    grails-app/conf/audit.properties')

        where:
        where               | outside | type                 | named
        'With --type'       | false   | ['--type', 'rest-api'] | 'rest-api'
        'Outside a project' | true    | []                   | 'web'
    }

    @Unroll
    void "#args is a mistake in the command line of feature show, as it is for feature add"() {
        expect:
        show(([COORDINATES, '--feature-pack-repository', repository] + args) as String[]) == 2
        err.toString().contains(message)
        out.toString().isEmpty()

        where:
        args                                                    | message
        ['--feature-option', 'store']                           | '--feature-option'
        ['--feature-pack-sha256', "${COORDINATES}=nothing"]     | 'does not end with a SHA-256'
        ['--refresh', '--offline']                              | '--refresh reads a feature catalog anew and --offline asks nothing of the network'
    }

    void "an option the feature does not have is why feature add does not add it"() {
        when:
        int exit = show(COORDINATES, '--feature-pack-repository', repository, '--feature-option', 'acme-audit.colour=blue', '--json')
        Map feature = ((Map) new JsonSlurper().parseText(out.toString())).feature as Map

        then:
        exit == 0
        feature.addable == false
        ((String) feature.notAddedBecause).contains('colour')
    }

    void "feature find looks through the features of a pack that is given and of a feature catalog, also where the application does not have them"() {
        given:
        File pack = directoryPack()

        when: 'the pack of a directory'
        int exit = find('acme', '--feature-pack-dir', pack.absolutePath)

        then:
        exit == 0
        err.toString().isEmpty()
        out.toString().contains('1 feature of the application shop (web) has acme in the name, the title, the description or the category:')
        out.toString().readLines().any { it.startsWith('    acme-audit ') && !it.contains('the application has it') }

        when: 'a catalog, as a document'
        reset()
        exit = find('trail', '--feature-catalog', catalog, '--json')
        Map document = new JsonSlurper().parseText(out.toString()) as Map

        then: 'the feature is found by what the catalog says of its plugin, with the plugin that would be taken, and no jar is asked for'
        exit == 0
        document.features == []
        document.catalogFeatures == [[name: 'acme-audit', coordinates: COORDINATES, title: 'Acme Audit', description: 'Keeps a trail of what was changed',
                                      catalog: new File(work, 'catalog.json').toPath().toUri().toString()]]
        asked.isEmpty()

        when: 'and as text'
        reset()
        exit = find('acme', '--feature-catalog', catalog)

        then:
        exit == 0
        out.toString().contains('1 feature of a feature catalog has acme in the name, or in the title or the description of the plugin:')
        out.toString().readLines().any { it.startsWith('    acme-audit ') && it.contains(COORDINATES) }
        out.toString().contains('feature show FEATURE')

        when: 'with the pack given as well, the feature is one of those that can be had, and is not said twice'
        reset()
        exit = find('acme', '--feature-catalog', catalog, '--feature-pack-dir', pack.absolutePath, '--json')
        document = new JsonSlurper().parseText(out.toString()) as Map

        then:
        exit == 0
        ((List<Map>) document.features)*.name == ['acme-audit']
        document.catalogFeatures == []

        when: 'nothing has the text'
        reset()
        exit = find('nothing-of-the-kind', '--feature-catalog', catalog)

        then:
        exit == 0
        out.toString().contains('No feature of the application shop (web) has nothing-of-the-kind')
    }

    @Unroll
    void "feature find with #args is a mistake in the command line"() {
        expect:
        find((['acme'] + args) as String[]) == 2
        err.toString().contains(message)

        where:
        args                                                              | message
        ['--feature-option', 'acme-audit.store=collection']               | '--feature-option gives a feature that is shown or added an option'
        ['--feature-pack-sha256', "${COORDINATES}=${'ab' * 32}"]           | "--feature-pack-sha256 names ${COORDINATES}, which is not among the features"
    }

    void "feature show, feature find and feature diff take the options feature add takes for a pack, by one declaration"() {
        when:
        int exit = ForgeCli.execute(beanContext, 'help', '--json')
        Map feature = ((List<Map>) ((Map) new JsonSlurper().parseText(out.toString())).commands).find { it.name == 'feature' }
        Map<String, Map> commands = ((List<Map>) feature.commands).collectEntries { [it.name, it] }
        Map<String, Map> ofAdd = options(commands.add)
        List<String> ofPacks = ['--feature-pack-dir', '--feature-pack-repository', '--feature-pack-sha256', '--feature-catalog', '--refresh', '--offline', '--feature-option']

        then:
        exit == 0
        ofAdd.keySet().containsAll(ofPacks)
        ['show', 'find', 'diff'].every { String name ->
            Map<String, Map> ofCommand = options(commands.get(name))
            ofPacks.every { String option -> ofCommand.get(option) != null && ofCommand.get(option) == ofAdd.get(option) }
        }

        and: 'every option of feature add but those that say what is done with the plan and --replace is one of feature show'
        (ofAdd.keySet() - options(commands.get('show')).keySet()) == ['--dry-run', '--diff', '--replace'] as Set
    }

    private int show(String... arguments) {
        Transport transport = transport()
        File kept = home
        FeatureShowCommand command = new FeatureShowCommand(beanContext.getBean(ProjectGenerator)) {

            @Override
            protected PackArtifacts createPackArtifacts() {
                return new PackArtifacts(transport).keepingJarsUnder(kept.toPath())
            }

            @Override
            protected String grailsRepoUrl() {
                return null
            }
        }
        return new CommandLine(command, new GrailsPicocliFactory(beanContext)).execute(arguments)
    }

    private int find(String... arguments) {
        Transport transport = transport()
        File kept = home
        FeatureFindCommand command = new FeatureFindCommand(beanContext.getBean(ProjectGenerator)) {

            @Override
            protected PackArtifacts createPackArtifacts() {
                return new PackArtifacts(transport).keepingJarsUnder(kept.toPath())
            }

            @Override
            protected String grailsRepoUrl() {
                return null
            }
        }
        return new CommandLine(command, new GrailsPicocliFactory(beanContext)).execute(arguments)
    }

    private Transport transport() {
        return { URI asking ->
            asked << asking
            byte[] answer = answers.get(asking.toString())
            return answer == null ? new Reply(404, null, null) : new Reply(200, null, new ByteArrayInputStream(answer))
        } as Transport
    }

    private File directoryPack() {
        File directory = new File(work, 'pack')
        TEMPLATES.each { String path, String text ->
            File template = new File(directory, "templates/${path}")
            template.parentFile.mkdirs()
            template.setText(text, 'UTF-8')
        }
        new File(directory, 'feature.yml').setText(DESCRIPTOR, 'UTF-8')
        return directory
    }

    private File publish(String coordinates, String descriptor, Map<String, String> templates) {
        List<String> parts = coordinates.split(':').toList()
        File directory = new File(work, "repository/${parts[0].replace('.', '/')}/${parts[1]}/${parts[2]}")
        directory.mkdirs()
        File published = new File(directory, "${parts[1]}-${parts[2]}.jar")
        Map<String, String> entries = ['META-INF/grails-forge/feature.yml': descriptor] +
                templates.collectEntries { String path, String text -> ["META-INF/grails-forge/templates/${path}".toString(), text] }
        new ZipOutputStream(new FileOutputStream(published)).withCloseable { ZipOutputStream zip ->
            entries.each { String path, String text ->
                zip.putNextEntry(new ZipEntry(path))
                zip.write(text.getBytes('UTF-8'))
                zip.closeEntry()
            }
        }
        return published
    }

    private static String sha256(File file) {
        return MessageDigest.getInstance('SHA-256').digest(file.bytes).encodeHex().toString()
    }

    // the options of a command of help --json, by the first of their long names
    private static Map<String, Map> options(Map command) {
        return ((List<Map>) command.options).collectEntries { Map option -> [((List<String>) option.names).find { it.startsWith('--') }, option] }
    }

    private void reset() {
        out.reset()
        err.reset()
    }

    private Map<String, String> tree() {
        Map<String, String> entries = new TreeMap<>()
        Files.walk(dir.toPath()).withCloseable { stream ->
            stream.each { Path path ->
                String name = dir.toPath().relativize(path).join('/')
                if (!name.startsWith('.grails/transactions')) {
                    entries.put(name, Files.isDirectory(path) ? 'directory' : MessageDigest.getInstance('SHA-256').digest(Files.readAllBytes(path)).encodeHex().toString())
                }
            }
        }
        return entries
    }
}
