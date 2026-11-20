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
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.apache.grails.forge.cli.pack.PackArtifacts
import org.apache.grails.forge.cli.pack.PackArtifacts.Reply
import org.apache.grails.forge.cli.pack.PackArtifacts.Transport
import org.apache.grails.forge.cli.pack.PackCatalog
import org.apache.grails.forge.cli.pack.PackCatalogs
import org.grails.forge.application.ContextFactory
import org.grails.forge.application.WebAvailableFeatures
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.GrailsPicocliFactory
import picocli.CommandLine
import spock.lang.IgnoreIf
import spock.lang.Unroll

/**
 * The feature of a plugin that is selected by its name, which a feature catalog has the plugin and the
 * version for. Every catalog and repository here is a file, a directory, or an answer this
 * specification gives itself: nothing is asked of the network, and a request no feature expects fails
 * the feature.
 */
class CreateAppFeatureCatalogSpec extends CommandSpec implements CommandFixture {

    static final String PLUGIN = 'com.acme:acme-audit-grails'
    static final String COORDINATES = PLUGIN + ':2.3.0'
    static final String HOSTED = 'https://catalog.invalid/index.json'
    static final String RANGE = '[9.0.0,10.0.0)'

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()
    File repository
    File work
    String address
    Path copies
    String setting = 'none'
    String environment = null
    List<URI> asked = []
    Map<String, Closure<Reply>> answers = [:]

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out))
        System.setErr(new PrintStream(err))
        repository = Files.createTempDirectory('grailsforgerepository').toFile()
        work = Files.createTempDirectory('grailsforgecatalog').toFile()
        copies = work.toPath().resolve('copies')
        address = repository.toPath().toAbsolutePath().normalize().toUri().toString()
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        repository.deleteDir()
        work.deleteDir()
    }

    void "an application is created with the feature a catalog has a plugin for, as it is with the coordinates of the plugin"() {
        given:
        File jar = publish(COORDINATES)
        File catalog = catalog([plugin(PLUGIN, ['2.3.0': pack(jar)])])

        when: 'the feature is selected by its name, next to a feature of the framework'
        int exit = run('com.example.shop', '--features', 'h2,acme-audit', '--feature-catalog', catalog.toURI().toString(),
                '--feature-pack-repository', address)

        then:
        exit == 0
        out.toString().contains("Feature pack acme-audit from ${COORDINATES} (${address}), sha256 ${sha256(jar)}")
        out.toString().contains("    the coordinates are those the feature catalog ${catalog.toPath().toUri()} (sha256 ${sha256(catalog)}) has for the feature acme-audit")
        out.toString().contains('    the jar has the SHA-256 the catalog records for it')
        out.toString().contains('    the jar was not checked: the repository gave no checksum for it')
        out.toString().contains('Application created')
        !out.toString().contains('--feature-pack-sha256')

        and: 'the application has what the pack gives it'
        new File(dir, 'shop/build.gradle').text.contains('implementation "com.acme:acme-audit-grails:2.3.0"')
        new File(dir, 'shop/grails-app/conf/com/example/audit.txt').text == 'kept as it is'

        and: 'the manifest says which plugin and version the name was, and the lock which jar and which catalog that was'
        new File(dir, 'shop/.grails/project.yml').text.contains("requestedFeatures:\n- name: acme-audit\n  coordinates: ${PLUGIN}\n  version: 2.3.0\n")
        String lock = new File(dir, 'shop/.grails/forge.lock.yml').text
        lock.contains("- name: acme-audit\n  source: pack\n  coordinates: ${PLUGIN}\n  version: 2.3.0\n  sha256: ${sha256(jar)}\n  catalogSha256: ${sha256(catalog)}\n")

        and: 'a catalog that is a file of this computer is not named in what is committed'
        !lock.contains('catalog:')
        !lock.contains(work.name)

        and: 'nothing was asked of the network'
        asked.isEmpty()
    }

    void "a catalog of an https address is named in the lock, kept as a copy only its owner reads, and not asked for again within a day"() {
        given:
        File jar = publish(COORDINATES)
        byte[] document = json([format: 1, plugins: [plugin(PLUGIN, ['2.3.0': pack(jar)])]]).getBytes('UTF-8')
        answers[HOSTED] = { new Reply(200, null, new ByteArrayInputStream(document)) }

        when:
        int exit = run('com.example.shop', '--features', 'acme-audit', '--feature-catalog', HOSTED, '--feature-pack-repository', address)

        then:
        exit == 0
        asked*.toString() == [HOSTED]
        out.toString().contains("    the coordinates are those the feature catalog ${HOSTED} (sha256 ${digest(document)}) has for the feature acme-audit")
        new File(dir, 'shop/.grails/forge.lock.yml').text.contains("  sha256: ${sha256(jar)}\n  catalog: ${HOSTED}\n  catalogSha256: ${digest(document)}\n")

        and: 'the copy is the bytes that were read'
        List<Path> kept = Files.list(copies).withCloseable { it.toList() }
        kept.size() == 1
        kept[0].fileName.toString() == digest(HOSTED.getBytes('UTF-8')) + '.json'
        Files.readAllBytes(kept[0]) == document

        when: 'the name is looked up again'
        asked.clear()
        exit = run('--list-features', '--features', 'acme-audit', '--feature-catalog', HOSTED, '--feature-pack-repository', address)

        then: 'the copy answers'
        exit == 0
        asked.isEmpty()
        out.toString().contains("Feature pack acme-audit from ${COORDINATES}")

        when: 'the catalog is asked for anew'
        exit = run('--list-features', '--features', 'acme-audit', '--feature-catalog', HOSTED, '--feature-pack-repository', address, '--refresh')

        then:
        exit == 0
        asked*.toString() == [HOSTED]

        when: 'the copy is older than a day'
        asked.clear()
        Files.setLastModifiedTime(kept[0], FileTime.from(Instant.now() - PackCatalogs.TIME_TO_LIVE - Duration.ofMinutes(1)))
        exit = run('--list-features', '--features', 'acme-audit', '--feature-catalog', HOSTED, '--feature-pack-repository', address)

        then: 'the catalog is asked for, and the copy is the new one'
        exit == 0
        asked*.toString() == [HOSTED]
        Duration.between(Files.getLastModifiedTime(kept[0]).toInstant(), Instant.now()) < Duration.ofHours(1)
    }

    @IgnoreIf({ os.windows })
    void "the copies of the catalogs are in a directory and in files only the user has access to"() {
        given:
        File jar = publish(COORDINATES)
        byte[] document = json([format: 1, plugins: [plugin(PLUGIN, ['2.3.0': pack(jar)])]]).getBytes('UTF-8')
        answers[HOSTED] = { new Reply(200, null, new ByteArrayInputStream(document)) }

        when:
        int exit = run('--list-features', '--features', 'acme-audit', '--feature-catalog', HOSTED, '--feature-pack-repository', address)

        then:
        exit == 0
        PosixFilePermissions.toString(Files.getPosixFilePermissions(copies)) == 'rwx------'
        Files.list(copies).withCloseable { it.toList() }.every { PosixFilePermissions.toString(Files.getPosixFilePermissions(it)) == 'rw-------' }
    }

    void "a catalog that cannot be read is answered by an older copy with a warning, and by none with --refresh"() {
        given:
        File jar = publish(COORDINATES)
        byte[] document = json([format: 1, plugins: [plugin(PLUGIN, ['2.3.0': pack(jar)])]]).getBytes('UTF-8')
        Files.createDirectories(copies)
        Path copy = copies.resolve(digest(HOSTED.getBytes('UTF-8')) + '.json')
        Files.write(copy, document)
        Files.setLastModifiedTime(copy, FileTime.from(Instant.parse('2026-01-02T03:04:05Z')))
        answers[HOSTED] = { new Reply(503, null, null) }

        when:
        int exit = run('--list-features', '--features', 'acme-audit', '--feature-catalog', HOSTED, '--feature-pack-repository', address)

        then:
        exit == 0
        out.toString().contains("The feature catalog ${HOSTED} could not be read (HTTP status 503): the copy of it that was read on 2026-01-02T03:04:05Z is used")
        out.toString().contains("Feature pack acme-audit from ${COORDINATES}")

        when:
        exit = run('--list-features', '--features', 'acme-audit', '--feature-catalog', HOSTED, '--feature-pack-repository', address, '--refresh')

        then:
        exit == 1
        err.toString().contains("acme-audit is not of Grails or of a feature pack that was given, and was looked for in a feature catalog. " +
                "The feature catalog ${HOSTED} could not be read: HTTP status 503")
    }

    void "with --offline a catalog is read from its copy however old, and nothing is asked of the network"() {
        given:
        File jar = publish(COORDINATES)
        byte[] document = json([format: 1, plugins: [plugin(PLUGIN, ['2.3.0': pack(jar)])]]).getBytes('UTF-8')

        when: 'there is no copy'
        int exit = run('--list-features', '--features', 'acme-audit', '--feature-catalog', HOSTED, '--feature-pack-repository', address, '--offline')

        then:
        exit == 1
        err.toString().contains("The feature catalog ${HOSTED} could not be read: not asked, since --offline makes no request to the network, " +
                'and there is no copy of the catalog on this computer')
        asked.isEmpty()

        when:
        Files.createDirectories(copies)
        Path copy = copies.resolve(digest(HOSTED.getBytes('UTF-8')) + '.json')
        Files.write(copy, document)
        Files.setLastModifiedTime(copy, FileTime.from(Instant.parse('2026-01-02T03:04:05Z')))
        exit = run('--list-features', '--features', 'acme-audit', '--feature-catalog', HOSTED, '--feature-pack-repository', address, '--offline')

        then:
        exit == 0
        out.toString().contains("Feature pack acme-audit from ${COORDINATES} (${address})")
        asked.isEmpty()

        when: 'the jar is only in a repository of the network'
        exit = run('--list-features', '--features', 'acme-audit', '--feature-catalog', HOSTED, '--feature-pack-repository', 'https://repository.invalid/maven',
                '--offline')

        then:
        exit == 1
        err.toString().contains("The feature pack of ${COORDINATES} was not taken from a repository. https://repository.invalid/maven/: not asked, " +
                'since --offline makes no request to the network')
        asked.isEmpty()

        when:
        exit = run('--list-features', '--offline', '--refresh')

        then:
        exit == 2
        err.toString().contains('--refresh reads a feature catalog anew and --offline asks nothing of the network: give one of them')
    }

    void "a copy that is no catalog is not used, and is replaced"() {
        given:
        File jar = publish(COORDINATES)
        byte[] document = json([format: 1, plugins: [plugin(PLUGIN, ['2.3.0': pack(jar)])]]).getBytes('UTF-8')
        Files.createDirectories(copies)
        Path copy = copies.resolve(digest(HOSTED.getBytes('UTF-8')) + '.json')
        Files.write(copy, '{"format": 1, "plugins": [[[[[[[[[[[[[[[['.getBytes('UTF-8'))
        answers[HOSTED] = { new Reply(200, null, new ByteArrayInputStream(document)) }

        when:
        int exit = run('--list-features', '--features', 'acme-audit', '--feature-catalog', HOSTED, '--feature-pack-repository', address)

        then:
        exit == 0
        asked*.toString() == [HOSTED]
        Files.readAllBytes(copy) == document
    }

    void "no catalog is read for an application of features of the framework, or for what is no name of a feature"() {
        given: 'the plugin index of Grails is the catalog, and every request fails the feature'
        setting = null

        when:
        int exit = run('com.example.shop', '--features', 'h2')

        then:
        exit == 0
        asked.isEmpty()
        !Files.exists(copies)

        when: 'coordinates, and names no pack can have'
        File jar = publish(COORDINATES)
        exit = run('--list-features', '--features', "${COORDINATES},h2,acme-audit", '--feature-pack-repository', address)

        then: 'the name is that of the pack that was given by its coordinates'
        exit == 0
        asked.isEmpty()
        out.toString().contains("Feature pack acme-audit from ${COORDINATES} (${address}), sha256 ${sha256(jar)}")

        when:
        exit = run('other', '--features', 'Not_A_Name')

        then:
        exit == 2
        err.toString().contains('The requested feature does not exist: Not_A_Name')
        asked.isEmpty()

        when: 'a name nothing here knows is looked for in the plugin index'
        exit = run('other', '--features', 'acme-audit')

        then: 'which this specification does not answer'
        exit == 1
        asked*.toString() == [PackCatalogs.DEFAULT]
        err.toString().contains('acme-audit is not of Grails or of a feature pack that was given, and was looked for in a feature catalog. ' +
                "The feature catalog ${PackCatalogs.DEFAULT} could not be read: not found. A plugin can be given by its coordinates, " +
                'group:artifact:version, and --feature-catalog names the catalog to read, or none')
        !new File(dir, 'other').exists()
    }

    void "the catalogs of the environment are read when the command names none, and none is no catalog"() {
        given:
        File jar = publish(COORDINATES)
        File catalog = catalog([plugin(PLUGIN, ['2.3.0': pack(jar)])])
        setting = " ${catalog.toURI()} ; "

        when:
        int exit = run('--list-features', '--features', 'acme-audit', '--feature-pack-repository', address)

        then:
        exit == 0
        out.toString().contains("Feature pack acme-audit from ${COORDINATES}")

        when:
        exit = run('shop', '--features', 'acme-audit', '--feature-pack-repository', address, '--feature-catalog', 'none')

        then: 'the name is one there is not, as it was before there were catalogs'
        exit == 2
        err.toString().contains('The requested feature does not exist: acme-audit')
        !err.toString().readLines().first().contains('catalog')
        asked.isEmpty()
    }

    @Unroll
    void "the catalog address #given is a mistake in the command line"() {
        when:
        int exit = run('shop', '--features', 'h2', '--feature-catalog', given)

        then:
        exit == 2
        err.toString().contains(message)
        !err.toString().contains('secret')
        !new File(dir, 'shop').exists()
        asked.isEmpty()

        where:
        given                                      || message
        'http://catalog.invalid/index.json'        || "'http://catalog.invalid/index.json' is not a feature catalog that is read: the address has to be https, or file: for a file"
        'https://user:secret@catalog.invalid/i.json' || 'is not a feature catalog that is read: the address has a user or password, and credentials are not sent'
        'https://catalog.invalid/i.json?token=secret' || 'is not a feature catalog that is read: the address cannot have a query or a fragment'
        'index.json'                               || 'is not a feature catalog that is read: the address has to be https, or file: for a file'
        'https://2130706433/index.json'            || 'its host or port is not one a request can be made to'
    }

    void "none is not given with the address of a catalog"() {
        when:
        int exit = run('shop', '--features', 'h2', '--feature-catalog', 'none', '--feature-catalog', HOSTED)

        then:
        exit == 2
        err.toString().contains('none is given for no feature catalog, and cannot be given with the address of one')
    }

    @Unroll
    void "of the versions #versions the catalog has, #expected is taken"() {
        given:
        Map<String, Map> packs = [:]
        versions.each { String version, Map overrides ->
            packs.put(version, pack(publish("${PLUGIN}:${version}"), overrides))
        }
        File catalog = catalog([plugin(PLUGIN, packs)])

        when:
        int exit = run('--list-features', '--features', 'acme-audit', '--feature-catalog', catalog.toURI().toString(), '--feature-pack-repository', address)

        then:
        exit == 0
        out.toString().contains("Feature pack acme-audit from ${PLUGIN}:${expected} (${address})")

        where:
        versions                                                                                   || expected
        ['2.3.0': [:], '2.10.0': [:], '2.9.1': [:]]                                                || '2.10.0'
        ['2.10.0': [:], '2.3': [:], '2.3.0.1': [:]]                                                || '2.10.0'
        ['3.0.0': [compatibility: [grails: '[10.0.0,11.0.0)', forge: RANGE]], '2.3.0': [:]]        || '2.3.0'
        ['3.0.0': [compatibility: [grails: RANGE, forge: '[10.0.0,)']], '2.3.0': [:]]              || '2.3.0'
        ['3.0.0-SNAPSHOT': [:], '3.0.0-RC1': [:], '3.0.0.M1': [:], '2.3.0': [:]]                   || '2.3.0'
        ['3.0.0': [format: 2], '2.4.0': [templateApi: 2], '2.3.0': [:]]                            || '2.3.0'
        ['3.0.0': [applicationTypes: ['plugin', 'rest-api']], '2.3.0': [applicationTypes: ['web']]] || '2.3.0'
        ['3.0.0': [kind: 'preset'], '2.3.0': [:]]                                                  || '2.3.0'
        ['3.0.0': [name: 'acme-other'], '2.3.0': [:]]                                              || '2.3.0'
    }

    @Unroll
    void "a feature the catalog has only #how is not taken by its name, and the command says how it is used"() {
        given:
        Map<String, Map> packs = [:]
        versions.each { String version, Map overrides ->
            packs.put(version, pack(publish("${PLUGIN}:${version}"), overrides))
        }
        File catalog = catalog([plugin(PLUGIN, packs, more)])

        when:
        int exit = run('shop', '--features', 'acme-audit', '--feature-catalog', catalog.toURI().toString(), '--feature-pack-repository', address)

        then: 'what was asked for cannot be had as it was asked for'
        exit == 2
        err.toString().contains("The feature acme-audit is not taken from the feature catalog: ${message}")
        !new File(dir, 'shop').exists()

        where:
        how                       | versions                                                              | more                        || message
        'of a deprecated plugin'  | ['2.3.0': [:], '2.4.0': [:]]                                          | [deprecated: true]          || "${PLUGIN} is deprecated, and is used only by its coordinates, such as ${PLUGIN}:2.4.0"
        'of a plugin deprecated with a reason' | ['2.3.0': [:]]                                           | [deprecated: 'use another'] || "${PLUGIN} is deprecated, and is used only by its coordinates, such as ${PLUGIN}:2.3.0"
        'as a snapshot'           | ['2.4.0-SNAPSHOT': [:]]                                               | [:]                         || "${PLUGIN} has it in no release, and a version that is none is used only by its coordinates, such as ${PLUGIN}:2.4.0-SNAPSHOT"
        'for another Grails'      | ['2.3.0': [compatibility: [grails: '[7.0.0,8.0.0)', forge: RANGE]]]   | [:]                         || "${PLUGIN} has it in no version for Grails"
        'for another application' | ['2.3.0': [applicationTypes: ['plugin']]]                             | [:]                         || "${PLUGIN} has it for applications of type plugin, and this is web"
        'in a later format'       | ['2.3.0': [format: 2]]                                                | [:]                         || "${PLUGIN} has it in no version this Forge reads the pack of (2.3.0 needs another Forge)"
    }

    void "a deprecated plugin and a snapshot are used by their coordinates, for which no catalog is read"() {
        given:
        setting = null
        publish("${PLUGIN}:2.4.0-SNAPSHOT")

        when:
        int exit = run('--list-features', '--features', "${PLUGIN}:2.4.0-SNAPSHOT", '--feature-pack-repository', address)

        then:
        exit == 0
        out.toString().contains("Feature pack acme-audit from ${PLUGIN}:2.4.0-SNAPSHOT (${address})")
        asked.isEmpty()
    }

    void "a name two plugins of the catalog have is refused, and each of them is selected by its full name"() {
        given:
        File jar = publish(COORDINATES)
        File other = publish('org.other:audit:1.0.0')
        File catalog = catalog([plugin(PLUGIN, ['2.3.0': pack(jar)]), plugin('org.other:audit', ['1.0.0': pack(other)])])

        when:
        int exit = run('shop', '--features', 'acme-audit', '--feature-catalog', catalog.toURI().toString(), '--feature-pack-repository', address)

        then:
        exit == 2
        err.toString().contains('More than one plugin of the feature catalog has a feature acme-audit: name the one that is meant as ' +
                "${PLUGIN}#acme-audit or org.other:audit#acme-audit")
        !new File(dir, 'shop').exists()

        when:
        exit = run('--list-features', '--features', 'org.other:audit#acme-audit,org.other:audit#acme-audit', '--feature-catalog', catalog.toURI().toString(),
                '--feature-pack-repository', address)

        then:
        exit == 0
        out.toString().contains("Feature pack acme-audit from org.other:audit:1.0.0 (${address}), sha256 ${sha256(other)}")
        !out.toString().contains("from ${COORDINATES}")
    }

    void "the catalog that is named first speaks for a plugin two catalogs have"() {
        given:
        File jar = publish(COORDINATES)
        File later = publish("${PLUGIN}:2.4.0")
        File first = catalog([plugin(PLUGIN, ['2.3.0': pack(jar)])], 'first.json')
        File second = catalog([plugin(PLUGIN, ['2.4.0': pack(later)])], 'second.json')

        when:
        int exit = run('--list-features', '--features', 'acme-audit', '--feature-catalog', first.toURI().toString(),
                '--feature-catalog', second.toURI().toString(), '--feature-pack-repository', address)

        then:
        exit == 0
        out.toString().contains("Feature pack acme-audit from ${COORDINATES} (${address})")
    }

    void "a name of the framework is the feature of the framework, whatever a catalog has under it"() {
        given:
        File jar = publish(COORDINATES)
        File shadow = publish('org.other:h2-pack:1.0.0', pack(CreateAppFeaturePackSpec.DESCRIPTOR.replace('name: acme-audit', 'name: h2')))
        File catalog = catalog([plugin(PLUGIN, ['2.3.0': pack(jar)]), plugin('org.other:h2-pack', ['1.0.0': pack(shadow, [name: 'h2'])])])

        when: 'the name alone is asked for'
        int exit = run('--list-features', '--features', 'h2', '--feature-catalog', HOSTED)

        then: 'no catalog is read for it'
        exit == 0
        asked.isEmpty()

        when: 'a catalog is read for another name'
        exit = run('com.example.shop', '--features', 'h2,acme-audit', '--feature-catalog', catalog.toURI().toString(), '--feature-pack-repository', address)

        then: 'the command says whose feature h2 is, and the plugin is not taken'
        exit == 0
        out.toString().contains('The feature h2 is that of Grails. The feature catalog has a feature of that name for org.other:h2-pack, which is not used')
        !out.toString().contains('org.other:h2-pack:1.0.0')
        !new File(dir, 'shop/build.gradle').text.contains('h2-pack')

        when: 'the plugin is asked for by its full name'
        exit = run('other', '--features', 'org.other:h2-pack#h2', '--feature-catalog', catalog.toURI().toString(), '--feature-pack-repository', address)

        then: 'its pack is refused, as it is by its coordinates'
        exit == 1
        err.toString().contains('has the name of a feature of the framework, h2')
        !new File(dir, 'other').exists()
    }

    void "a name no catalog has does not exist, and the command says where it was looked for"() {
        given:
        File jar = publish(COORDINATES)
        File catalog = catalog([plugin(PLUGIN, ['2.3.0': pack(jar)])])

        when:
        int exit = run('shop', '--features', 'acme-other,com.acme:nothing#acme-audit', '--feature-catalog', catalog.toURI().toString())

        then:
        exit == 2
        err.toString().contains('The requested features do not exist: acme-other, com.acme:nothing#acme-audit. acme-other, com.acme:nothing#acme-audit ' +
                "were not found in the feature catalog ${catalog.toPath().toUri()} either")
        !new File(dir, 'shop').exists()
    }

    void "the plugin index as it is published today records no feature pack, and the command says so"() {
        given:
        File catalog = new File(work, 'today.json')
        catalog.setText(json([[name: 'Cache', desc: 'Caching', coords: 'org.apache.grails:grails-cache', 'maven-repo': 'https://repo1.maven.org/maven2',
                               versions: [[version: '8.0.0', date: '2026-10-04T18:46:18Z', grailsVersion: '8.0.0-SNAPSHOT > *']]]]), 'UTF-8')

        when:
        int exit = run('shop', '--features', 'grails-cache-pack', '--feature-catalog', catalog.toURI().toString())

        then:
        exit == 2
        err.toString().contains('The requested feature does not exist: grails-cache-pack. grails-cache-pack was not found in the feature catalog ' +
                "${catalog.toPath().toUri()} (the plugin index without a format, which records no feature pack) either")

        when:
        exit = run('--list-catalog-features', '--feature-catalog', catalog.toURI().toString())

        then:
        exit == 0
        out.toString().contains('the catalog is the plugin index without a format, which records no feature pack')
    }

    @Unroll
    void "a catalog that is #what is refused as a whole: the command line was right, and nothing is created"() {
        given:
        File catalog = new File(work, 'hostile.json')
        catalog.bytes = content.call() as byte[]

        when:
        int exit = run('shop', '--features', 'acme-audit', '--feature-catalog', catalog.toURI().toString(), '--feature-pack-repository', address)

        then:
        exit == 1
        String said = err.toString()
        said.contains("acme-audit is not of Grails or of a feature pack that was given, and was looked for in a feature catalog. The feature catalog " +
                "${catalog.toPath().toUri()} could not be read: ${message}")
        !said.contains('Exception')
        !said.contains('Usage')
        said.readLines().findAll { it }.size() == 1
        said.length() < 1200
        !new File(dir, 'shop').exists()

        where:
        what                           | content                                                                          || message
        'larger than a catalog can be' | { new byte[PackCatalog.MAX_BYTES + 1] }                                          || "the catalog has more than the ${PackCatalog.MAX_BYTES} bytes it can have"
        'nested too deep'              | { ('{"format":1,"plugins":' + ('[' * 100000) + (']' * 100000) + '}').bytes }      || "it is not a catalog: it is nested deeper than ${PackCatalog.MAX_DEPTH}"
        'nothing but open brackets'    | { ('[' * 5000000).bytes }                                                        || 'it is not a catalog: it is nested deeper than'
        'of more values than allowed'  | { ('[' + ('0,' * PackCatalog.MAX_VALUES) + '0]').bytes }                         || "it is not a catalog: it has more than ${PackCatalog.MAX_VALUES} values"
        'not JSON'                     | { '<html>@|red not found|@\u001b[31m</html>'.bytes }                             || 'it is not a catalog: it is not JSON'
        'YAML'                         | { 'format: 1\nplugins: []\n'.bytes }                                             || 'it is not a catalog: it is not JSON'
        'not UTF-8'                    | { [0x7b, 0x22, 0xff, 0xfe, 0x22, 0x7d] as byte[] }                               || 'it is not written in UTF-8'
        'cut off'                      | { '{"format": 1, "plugins": [{"coords": "com.acme:acme'.bytes }                  || 'it is not a catalog: a text is not closed'
        'followed by more'             | { '{"format": 1, "plugins": []} {}'.bytes }                                      || 'it is not a catalog: there is something after the document'
        'a text'                       | { '"catalog"'.bytes }                                                            || 'it is not a catalog: the document is neither an object nor an array'
        'without a format'             | { '{"plugins": []}'.bytes }                                                      || 'it is not a catalog: it has no format, which is a whole number'
        'of a format that is a text'   | { '{"format": "1", "plugins": []}'.bytes }                                       || 'it is not a catalog: it has no format, which is a whole number'
        'of a later format'            | { '{"format": 2, "plugins": []}'.bytes }                                         || 'it is of format 2, and this Forge reads format 1'
        'without plugins'              | { '{"format": 1, "plugins": {"a": 1}}'.bytes }                                   || 'it is not a catalog: it has no plugins, which is an array'
        'with a key twice'             | { '{"format": 1, "format": 1, "plugins": []}'.bytes }                            || 'it is not a catalog: an object has one key twice'
        'with a text too long'         | { ('{"format": 1, "plugins": [], "x": "' + ('x' * (PackCatalog.MAX_TEXT + 1)) + '"}').bytes } || "it is not a catalog: a text has more than ${PackCatalog.MAX_TEXT} characters"
        'with a control character'     | { '{"format": 1, "plugins": [], "x": "a\u0007b"}'.bytes }                        || 'it is not a catalog: a text has a control character in it'
    }

    void "a catalog that is a directory, or is not there, is not read"() {
        when:
        int exit = run('shop', '--features', 'acme-audit', '--feature-catalog', new File(work, 'missing.json').toURI().toString())

        then:
        exit == 1
        err.toString().contains('could not be read: not found')
    }

    @Unroll
    void "a plugin of the catalog with #what is left out, and the others are still found"() {
        given:
        File jar = publish(COORDINATES)
        File good = publish('org.good:good-audit:1.0.0', pack(CreateAppFeaturePackSpec.DESCRIPTOR.replace('name: acme-audit', 'name: good-audit')))
        Map bad = plugin(PLUGIN, ['2.3.0': pack(jar)])
        change.call(bad)
        File catalog = catalog([bad, plugin('org.good:good-audit', ['1.0.0': pack(good, [name: 'good-audit'])])])

        when: 'the feature of the plugin is asked for'
        int exit = run('shop', '--features', 'acme-audit', '--feature-catalog', catalog.toURI().toString(), '--feature-pack-repository', address)

        then: 'it does not exist, and one line says what was left out and why'
        exit == 2
        String said = err.toString()
        said.contains('The requested feature does not exist: acme-audit. acme-audit was not found in the feature catalog ' +
                "${catalog.toPath().toUri()} (1 plugin left out for what the catalog says of it; the first: plugin 1: ${problem}")
        !said.contains('@|')
        !said.contains('secret')
        !said.contains('\u001b')
        said.readLines().first().length() < 900
        !new File(dir, 'shop').exists()

        when: 'the feature of the other plugin is asked for'
        exit = run('--list-features', '--features', 'good-audit', '--feature-catalog', catalog.toURI().toString(), '--feature-pack-repository', address)

        then:
        exit == 0
        out.toString().contains("Feature pack good-audit from org.good:good-audit:1.0.0 (${address})")

        where:
        what                                  | change                                                                         || problem
        'coordinates that are none'           | { it.coords = 'com.acme' }                                                     || 'coords is not group:artifact, but com.acme'
        'coordinates that are markup'         | { it.coords = '@|red x|@:\u001b[31my' }                                        || 'coords is not group:artifact, but @ |red x|@:?[31my'
        'coordinates that go up a directory'  | { it.coords = 'com.acme:../../etc' }                                           || 'coords is not group:artifact, but com.acme:../../etc'
        'coordinates of three parts'          | { it.coords = COORDINATES }                                                    || "coords is not group:artifact, but ${COORDINATES}"
        'coordinates that are a number'       | { it.coords = 7 }                                                              || 'coords is not a text'
        'no coordinates'                      | { it.remove('coords') }                                                        || 'coords is not a text'
        'a version that is a range'           | { it.versions[0].version = '[2.0,3.0)' }                                       || "${PLUGIN}: version [2.0,3.0) is not one version of a plugin"
        'a version that is the latest'        | { it.versions[0].version = 'latest.release' }                                  || "${PLUGIN}: version latest.release is not one version of a plugin"
        'a version that is markup'            | { it.versions[0].version = '@|red 1|@' }                                       || "${PLUGIN}: version @ |red 1|@ is not one version of a plugin"
        'a version twice'                     | { it.versions << it.versions[0] }                                              || "${PLUGIN}: has the version 2.3.0 more than once"
        'a version that is a text'            | { it.versions << 'x' }                                                         || "${PLUGIN}: has a version that is not an object"
        'a pack that is a text'               | { it.versions[0].pack = 'yes' }                                                || "${PLUGIN}: the pack of version 2.3.0 is not an object"
        'a name that is none'                 | { it.versions[0].pack.name = 'Acme Audit' }                                    || "${PLUGIN}: the pack of version 2.3.0: name is not the name of a feature, but Acme Audit"
        'a name that is markup'               | { it.versions[0].pack.name = '@|red x|@\u0007' }                               || "${PLUGIN}: the pack of version 2.3.0: name is not the name of a feature, but @ |red x|@?"
        'a name that is a list'               | { it.versions[0].pack.name = ['acme-audit'] }                                  || "${PLUGIN}: the pack of version 2.3.0: name is not a text"
        'a kind there is not'                 | { it.versions[0].pack.kind = 'profile' }                                       || "${PLUGIN}: the pack of version 2.3.0: kind is neither feature nor preset"
        'no digest'                           | { it.versions[0].pack.remove('sha256') }                                       || "${PLUGIN}: the pack of version 2.3.0: sha256 is not a text"
        'a digest that is none'               | { it.versions[0].pack.sha256 = 'z' * 64 }                                      || "${PLUGIN}: the pack of version 2.3.0: sha256 is not a SHA-256, which is 64 hexadecimal digits"
        'a format that is a text'             | { it.versions[0].pack.format = '1' }                                           || "${PLUGIN}: the pack of version 2.3.0: format is not a whole number from 1"
        'a template API that is a fraction'   | { it.versions[0].pack.templateApi = 1.5 }                                      || "${PLUGIN}: the pack of version 2.3.0: templateApi is not a whole number from 1"
        'no compatibility'                    | { it.versions[0].pack.remove('compatibility') }                                || "${PLUGIN}: the pack of version 2.3.0: compatibility is not an object"
        'a range that is none'                | { it.versions[0].pack.compatibility.grails = '@|red 9|@' }                     || "${PLUGIN}: the pack of version 2.3.0: compatibility.grails is not a range of versions"
        'a type of application there is not'  | { it.versions[0].pack.applicationTypes = ['web', 'desktop'] }                  || "${PLUGIN}: the pack of version 2.3.0: applicationTypes is not an array of web, rest-api, web-plugin, plugin"
        'an artifact that is none'            | { it.versions[0].pack.coords = 'com.acme:acme-audit-grails:[1,2)' }            || "${PLUGIN}: the pack of version 2.3.0: coords is not one version of a plugin"
        'a repository with a password'        | { it['maven-repo'] = 'https://user:secret@repo.acme.invalid/maven' }           || "${PLUGIN}: maven-repo is not the https address of a repository that a catalog can name"
        'a repository that is a directory'    | { it['maven-repo'] = 'file:/tmp/repository' }                                  || "${PLUGIN}: maven-repo is not the https address of a repository that a catalog can name"
        'a repository without TLS'            | { it['maven-repo'] = 'http://repo.acme.invalid/maven' }                        || "${PLUGIN}: maven-repo is not the https address of a repository that a catalog can name"
        'a repository of this computer'       | { it.versions[0].pack['maven-repo'] = 'https://localhost:8443/maven' }         || "${PLUGIN}: the pack of version 2.3.0: maven-repo is not the https address of a repository that a catalog can name"
        'a repository of a private network'   | { it['maven-repo'] = 'https://192.168.1.10/maven' }                            || "${PLUGIN}: maven-repo is not the https address of a repository that a catalog can name"
        'a repository with a token'           | { it['maven-repo'] = 'https://repo.acme.invalid/maven?token=secret' }          || "${PLUGIN}: maven-repo is not the https address of a repository that a catalog can name"
        'deprecated as a number'              | { it.deprecated = 1 }                                                          || "${PLUGIN}: deprecated is neither true, false nor a text"
        'a description that is a list'        | { it.desc = ['x'] }                                                            || "${PLUGIN}: desc is not a text"
    }

    void "a plugin that is in the catalog twice is there once"() {
        given:
        File jar = publish(COORDINATES)
        File later = publish("${PLUGIN}:2.4.0")
        File catalog = catalog([plugin(PLUGIN, ['2.3.0': pack(jar)]), plugin(PLUGIN, ['2.4.0': pack(later)])])

        when:
        int exit = run('--list-features', '--features', 'acme-audit', '--feature-catalog', catalog.toURI().toString(), '--feature-pack-repository', address)

        then:
        exit == 0
        out.toString().contains("Feature pack acme-audit from ${COORDINATES} (${address})")
    }

    void "what a catalog says of a plugin for people is listed as one line that is no markup, and plugins without a pack are not looked at"() {
        given:
        File jar = publish(COORDINATES)
        File old = publish('org.old:old-audit:1.0.0', pack(CreateAppFeaturePackSpec.DESCRIPTOR.replace('name: acme-audit', 'name: old-audit')))
        File shadow = publish('org.other:h2-pack:1.0.0')
        File catalog = catalog([
                plugin(PLUGIN, ['2.3.0': pack(jar), '2.2.0': pack(jar)], [name: 'Acme', desc: "Audits @|red everything|@\u001b[31m\nand more " + ('x' * 5000), docs: 'javascript:alert(1)']),
                plugin('org.old:old-audit', ['1.0.0': pack(old, [name: 'old-audit'])], [deprecated: true]),
                plugin('org.other:h2-pack', ['1.0.0': pack(shadow, [name: 'h2'])]),
                [coords: '@|red not looked at|@', versions: [[version: '\u0007']], name: 7],
                'not a plugin',
                plugin('org.bad:bad', ['1.0.0': pack(jar, [sha256: 'none'])]),
        ])

        when:
        int exit = run('--list-catalog-features', '--feature-catalog', catalog.toURI().toString())

        then:
        exit == 0
        List<String> lines = out.toString().readLines()
        lines.contains("Feature catalog ${catalog.toPath().toUri()}, sha256 ${sha256(catalog)}".toString())
        lines.any { it.startsWith("  acme-audit  ${COORDINATES}  Audits @ |red everything|@ [31m and more xxx") && it.endsWith('...') && it.length() < 300 }
        lines.contains('  1 more is not offered: of a deprecated plugin, without a release, or not for this application, Grails ' +
                "${org.grails.forge.util.VersionInfo.grailsVersion} and Forge ${org.grails.forge.util.VersionInfo.version}".toString())
        lines.contains('  1 more has the name of a feature of the framework, which is the one that is selected by that name')
        lines.contains('  1 plugin is left out for what the catalog says of it:')
        lines.contains('    plugin 6: org.bad:bad: the pack of version 1.0.0: sha256 is not a SHA-256, which is 64 hexadecimal digits')
        !out.toString().contains('old-audit')
        !out.toString().contains('\u001b[31m')
        !out.toString().contains('not looked at')
        asked.isEmpty()
    }

    void "no catalog is listed when none is what was said for it"() {
        when:
        int exit = run('--list-catalog-features')

        then:
        exit == 0
        out.toString().contains('No feature catalog is read')
    }

    void "a jar that is not the one the catalog records is refused before it is opened, and nothing is created"() {
        given:
        File jar = publish(COORDINATES)
        File catalog = catalog([plugin(PLUGIN, ['2.3.0': pack(jar, [sha256: 'AB' * 32])])])

        when:
        int exit = run('shop', '--features', 'acme-audit', '--feature-catalog', catalog.toURI().toString(), '--feature-pack-repository', address)

        then:
        exit == 1
        err.toString().contains("The feature pack of ${COORDINATES} was not taken from a repository. ${address}: checksum mismatch: the sha256 of " +
                "acme-audit-grails-2.3.0.jar is ${sha256(jar)}, and ${'ab' * 32} was asked for")
        !new File(dir, 'shop').exists()
    }

    void "a jar whose pack is not the feature the catalog says it has is refused"() {
        given:
        File jar = publish(COORDINATES, pack(CreateAppFeaturePackSpec.DESCRIPTOR.replace('name: acme-audit', 'name: acme-other')))
        File catalog = catalog([plugin(PLUGIN, ['2.3.0': pack(jar)])])

        when:
        int exit = run('shop', '--features', 'acme-audit', '--feature-catalog', catalog.toURI().toString(), '--feature-pack-repository', address)

        then:
        exit == 1
        err.toString().contains("The feature catalog ${catalog.toPath().toUri()} says that ${COORDINATES} has the feature acme-audit, and the jar has the feature pack acme-other")
        !new File(dir, 'shop').exists()
    }

    void "a digest that is asked for has to be the one the catalog records, and has to be of something that is selected"() {
        given:
        File jar = publish(COORDINATES)
        File catalog = catalog([plugin(PLUGIN, ['2.3.0': pack(jar)])])

        when:
        int exit = run('--list-features', '--features', 'acme-audit', '--feature-catalog', catalog.toURI().toString(), '--feature-pack-repository', address,
                '--feature-pack-sha256', "${COORDINATES}=${sha256(jar)}")

        then:
        exit == 0
        out.toString().contains('    the jar has the SHA-256 the catalog records for it')

        when:
        exit = run('shop', '--features', 'acme-audit', '--feature-catalog', catalog.toURI().toString(), '--feature-pack-repository', address,
                '--feature-pack-sha256', "${COORDINATES}=${'cd' * 32}")

        then:
        exit == 1
        err.toString().contains("records the SHA-256 ${sha256(jar)} for ${COORDINATES}, and ${'cd' * 32} was asked for with --feature-pack-sha256")

        when:
        exit = run('shop', '--features', 'acme-audit', '--feature-catalog', catalog.toURI().toString(), '--feature-pack-repository', address,
                '--feature-pack-sha256', "com.acme:other:1.0=${'cd' * 32}")

        then:
        exit == 2
        err.toString().contains('--feature-pack-sha256 names com.acme:other:1.0, which is not among the features asked for with --features')
        !new File(dir, 'shop').exists()
    }

    void "the repository a catalog names is asked after those of the environment and before Maven Central, and not when repositories are given"() {
        given:
        File jar = publish(COORDINATES)
        File catalog = catalog([plugin(PLUGIN, ['2.3.0': pack(jar)], ['maven-repo': 'https://repo.acme.invalid/maven'])])
        String path = 'com/acme/acme-audit-grails/2.3.0/acme-audit-grails-2.3.0.jar'
        File empty = Files.createDirectory(work.toPath().resolve('empty')).toFile()
        environment = empty.absolutePath
        answers["https://repo.acme.invalid/maven/${path}".toString()] = { new Reply(200, null, new ByteArrayInputStream(jar.bytes)) }

        when:
        int exit = run('--list-features', '--features', 'acme-audit', '--feature-catalog', catalog.toURI().toString())

        then:
        exit == 0
        out.toString().contains("Feature pack acme-audit from ${COORDINATES} (https://repo.acme.invalid/maven/), sha256 ${sha256(jar)}")
        asked*.toString().first() == "https://repo.acme.invalid/maven/${path}"
        asked.every { it.host == 'repo.acme.invalid' }

        when: 'the repository of the catalog does not have it'
        asked.clear()
        answers.clear()
        exit = run('--list-features', '--features', 'acme-audit', '--feature-catalog', catalog.toURI().toString())

        then: 'Maven Central is asked last'
        exit == 1
        asked*.toString() == ["https://repo.acme.invalid/maven/${path}", "${PackArtifacts.MAVEN_CENTRAL}${path}"]
        err.toString().contains("${empty.toPath().toUri()}: not found; https://repo.acme.invalid/maven/: not found; ${PackArtifacts.MAVEN_CENTRAL}: not found")

        when: 'repositories are given'
        asked.clear()
        exit = run('--list-features', '--features', 'acme-audit', '--feature-catalog', catalog.toURI().toString(), '--feature-pack-repository', empty.toURI().toString())

        then: 'only they are asked'
        exit == 1
        asked.isEmpty()
    }

    void "the name the plugin index has for Maven Central is Maven Central"() {
        given:
        File jar = publish(COORDINATES)
        File catalog = catalog([plugin(PLUGIN, ['2.3.0': pack(jar)], ['maven-repo': 'https://repo1.maven.org/maven2'])])

        when:
        int exit = run('--list-features', '--features', 'acme-audit', '--feature-catalog', catalog.toURI().toString())

        then:
        exit == 1
        asked*.toString() == ["${PackArtifacts.MAVEN_CENTRAL}com/acme/acme-audit-grails/2.3.0/acme-audit-grails-2.3.0.jar"]
    }

    private int run(String... args) {
        String catalogs = setting
        String repositories = environment
        Path keptIn = copies
        Transport transport = { URI asking ->
            asked << asking
            Closure<Reply> answer = answers[asking.toString()]
            return answer == null ? new Reply(404, null, null) : answer.call()
        } as Transport
        CreateAppCommand command = new CreateAppCommand(beanContext.getBean(WebAvailableFeatures), beanContext.getBean(ContextFactory),
                beanContext.getBean(ProjectGenerator)) {

            @Override
            protected String featureCatalogSetting() {
                return catalogs
            }

            @Override
            protected Path featureCatalogCopies() {
                return keptIn
            }

            @Override
            protected PackArtifacts createPackArtifacts() {
                return new PackArtifacts(transport)
            }

            @Override
            protected String grailsRepoUrl() {
                return repositories
            }
        }
        out.reset()
        err.reset()
        return new CommandLine(command, new GrailsPicocliFactory(beanContext)).execute(args)
    }

    private static Map<String, Object> plugin(String coordinates, Map<String, Map> packs, Map more = [:]) {
        return ([coords: coordinates, owner: 'Acme', licenses: ['Apache-2.0'],
                 versions: packs.collect { String version, Map pack -> [version: version, date: '2026-10-04T18:46:18Z', pack: pack] }] + more) as Map<String, Object>
    }

    private static Map<String, Object> pack(File jar, Map overrides = [:]) {
        return ([format: 1, kind: 'feature', name: 'acme-audit', templateApi: 1, compatibility: [grails: RANGE, forge: RANGE], sha256: sha256(jar)] +
                overrides) as Map<String, Object>
    }

    private File catalog(List plugins, String name = 'catalog.json') {
        File catalog = new File(work, name)
        catalog.setText(json([format: 1, generated: '2026-11-19T00:00:00Z', plugins: plugins]), 'UTF-8')
        return catalog
    }

    private static String json(Object value) {
        if (value instanceof Map) {
            return '{' + value.collect { key, each -> json(key.toString()) + ': ' + json(each) }.join(', ') + '}'
        }
        if (value instanceof List) {
            return '[' + value.collect { json(it) }.join(', ') + ']'
        }
        if (value instanceof CharSequence) {
            StringBuilder text = new StringBuilder('"')
            for (char c : value.toString().toCharArray()) {
                text.append(c == ('"' as char) || c == ('\\' as char) ? '\\' + c : c < (' ' as char) ? String.format('\\u%04x', (int) c) : String.valueOf(c))
            }
            return text.append('"').toString()
        }
        return String.valueOf(value)
    }

    private static Map<String, String> pack(String descriptor) {
        return ['META-INF/grails-forge/feature.yml': descriptor, 'META-INF/grails-forge/templates/audit.txt': 'kept as it is']
    }

    private File publish(String coordinates, Map<String, String> entries = pack(CreateAppFeaturePackSpec.DESCRIPTOR)) {
        List<String> parts = coordinates.split(':').toList()
        File directory = new File(repository, "${parts[0].replace('.', '/')}/${parts[1]}/${parts[2]}")
        directory.mkdirs()
        File jar = new File(directory, "${parts[1]}-${parts[2]}.jar")
        new ZipOutputStream(new FileOutputStream(jar)).withCloseable { ZipOutputStream zip ->
            // so that each jar has a digest of its own
            zip.putNextEntry(new ZipEntry('META-INF/coordinates.txt'))
            zip.write(coordinates.getBytes('UTF-8'))
            zip.closeEntry()
            entries.each { String path, String text ->
                zip.putNextEntry(new ZipEntry(path))
                zip.write(text.getBytes('UTF-8'))
                zip.closeEntry()
            }
        }
        return jar
    }

    private static String sha256(File file) {
        return digest(file.bytes)
    }

    private static String digest(byte[] bytes) {
        return MessageDigest.getInstance('SHA-256').digest(bytes).encodeHex().toString()
    }
}
