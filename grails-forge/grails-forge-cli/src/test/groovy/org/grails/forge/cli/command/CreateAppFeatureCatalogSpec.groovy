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

import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileAttribute
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
import org.apache.grails.forge.cli.pack.PackCatalogProbe
import org.apache.grails.forge.cli.pack.PackCatalogs
import org.apache.grails.forge.core.bom.BomVersions
import org.grails.forge.application.ContextFactory
import org.grails.forge.application.WebAvailableFeatures
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForkedCli
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
    Duration within = Duration.ofSeconds(30)
    boolean answered = true
    boolean pluginIndex = false
    String chosenFor = null
    // what GRAILS_PLUGIN_INDEX names: nothing, whatever the build gives the specifications
    String indexVariable = null

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out))
        System.setErr(new PrintStream(err))
        repository = Files.createTempDirectory('grailsforgerepository').toFile()
        work = Files.createTempDirectory('grailsforgecatalog').toFile()
        copies = work.toPath().resolve('.grails').resolve('forge').resolve('catalogs')
        address = repository.toPath().toAbsolutePath().normalize().toUri().toString()
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        repository.deleteDir()
        work.deleteDir()
    }

    // the directory of the copies as the command makes it: for the user alone, where the file system has modes
    private void ownCopies() {
        boolean posix = FileSystems.default.supportedFileAttributeViews().contains('posix')
        Files.createDirectories(copies, (posix ? [PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString('rwx------'))] : []) as FileAttribute<?>[])
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
        out.toString().contains('    the SHA-256 is the one that same catalog gives for the jar: the jar is the one the catalog means, which nobody else has checked')
        out.toString().contains('    the jar was not checked: the repository gave no checksum for it')
        out.toString().contains('Application created')
        !out.toString().contains('--feature-pack-sha256')

        and: 'which plugin the catalog made of the name, and where it is looked for, is said before the plugin is asked for'
        String before = "The feature acme-audit is taken as ${COORDINATES}, which the feature catalog ${catalog.toPath().toUri()} (sha256 ${sha256(catalog)}) " +
                "names for it, with the SHA-256 ${sha256(jar)}. Asking ${address}"
        out.toString().contains(before)
        out.toString().indexOf(before) < out.toString().indexOf('Feature pack acme-audit from')

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
        byte[] document = json([plugin(PLUGIN, ['2.3.0': pack(jar)])]).getBytes('UTF-8')
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
        byte[] document = json([plugin(PLUGIN, ['2.3.0': pack(jar)])]).getBytes('UTF-8')
        answers[HOSTED] = { new Reply(200, null, new ByteArrayInputStream(document)) }

        when:
        int exit = run('--list-features', '--features', 'acme-audit', '--feature-catalog', HOSTED, '--feature-pack-repository', address)

        then:
        exit == 0
        [copies, copies.parent, copies.parent.parent].every { PosixFilePermissions.toString(Files.getPosixFilePermissions(it)) == 'rwx------' }
        Files.list(copies).withCloseable { it.toList() }.every { PosixFilePermissions.toString(Files.getPosixFilePermissions(it)) == 'rw-------' }
    }

    void "a catalog that cannot be read is answered by an older copy with a warning, and by none with --refresh"() {
        given:
        File jar = publish(COORDINATES)
        byte[] document = json([plugin(PLUGIN, ['2.3.0': pack(jar)])]).getBytes('UTF-8')
        ownCopies()
        Path copy = copies.resolve(digest(HOSTED.getBytes('UTF-8')) + '.json')
        Files.write(copy, document)
        Files.setLastModifiedTime(copy, FileTime.from(Instant.parse('2026-01-02T03:04:05Z')))
        answers[HOSTED] = { new Reply(503, null, null) }

        when:
        int exit = run('--list-features', '--features', 'acme-audit', '--feature-catalog', HOSTED, '--feature-pack-repository', address)

        then:
        exit == 0
        out.toString().contains("The feature catalog ${HOSTED} could not be read (HTTP status 503): the copy of it that was read on 2026-01-02T03:04:05Z, ")
        out.toString() =~ /2026-01-02T03:04:05Z, \d+ days old, is used/
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
        byte[] document = json([plugin(PLUGIN, ['2.3.0': pack(jar)])]).getBytes('UTF-8')

        when: 'there is no copy'
        int exit = run('--list-features', '--features', 'acme-audit', '--feature-catalog', HOSTED, '--feature-pack-repository', address, '--offline')

        then:
        exit == 1
        err.toString().contains("The feature catalog ${HOSTED} could not be read: not asked, since --offline makes no request to the network, " +
                'and there is no copy of the catalog on this computer')
        asked.isEmpty()

        when:
        ownCopies()
        Path copy = copies.resolve(digest(HOSTED.getBytes('UTF-8')) + '.json')
        Files.write(copy, document)
        Files.setLastModifiedTime(copy, FileTime.from(Instant.parse('2026-01-02T03:04:05Z')))
        exit = run('--list-features', '--features', 'acme-audit', '--feature-catalog', HOSTED, '--feature-pack-repository', address, '--offline')

        then:
        exit == 0
        out.toString().contains("Feature pack acme-audit from ${COORDINATES} (${address})")
        out.toString() =~ 'The feature catalog ' + java.util.regex.Pattern.quote(HOSTED) + / is read from its copy of 2026-01-02T03:04:05Z, \d+ days old: --offline asks nothing of the network/
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
        byte[] document = json([plugin(PLUGIN, ['2.3.0': pack(jar)])]).getBytes('UTF-8')
        ownCopies()
        Path copy = copies.resolve(digest(HOSTED.getBytes('UTF-8')) + '.json')
        Files.write(copy, '[[[[[[[[[[[[[[[['.getBytes('UTF-8'))
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
        pluginIndex = true

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
        exit == 2
        asked*.toString() == [PackCatalogs.PLUGIN_INDEX]
        err.toString().readLines()[0] == 'The requested feature does not exist: acme-audit'
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
        ['2.3': [:], '2.3.0': [:], '2.3.0.0': [:]]                                                || '2.3.0.0'
        ['2.3.0.0': [:], '2.3.0': [:], '2.3': [:]]                                                || '2.3.0.0'
        ['2.03.0': [:], '2.3.0': [:]]                                                             || '2.3.0'
        ['2.3.0': [:], '2.03.0': [:]]                                                             || '2.3.0'
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
        'of a plugin deprecated with a reason' | ['2.3.0': [:]]                                           | [deprecated: 'use another'] || "${PLUGIN} is deprecated (use another), and is used only by its coordinates, such as ${PLUGIN}:2.3.0"
        'as a snapshot'           | ['2.4.0-SNAPSHOT': [:]]                                               | [:]                         || "${PLUGIN} has it in no release, and a version that is none is used only by its coordinates, such as ${PLUGIN}:2.4.0-SNAPSHOT"
        'for another Grails'      | ['2.3.0': [compatibility: [grails: '[7.0.0,8.0.0)', forge: RANGE]]]   | [:]                         || "${PLUGIN} has it in no version for Grails"
        'for another application' | ['2.3.0': [applicationTypes: ['plugin']]]                             | [:]                         || "${PLUGIN} has it for applications of type plugin, and this is web"
        // a pack of a later format is not read at all, and its name is not known: PluginDiscoveryContractSpec
        'for a later template API' | ['2.3.0': [templateApi: 2]]                                          | [:]                         || "${PLUGIN} has it in no version this Forge reads the pack of (2.3.0 needs another Forge)"
    }

    void "a deprecated plugin and a snapshot are used by their coordinates, for which no catalog is read"() {
        given:
        setting = null
        pluginIndex = true
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
                "${catalog.toPath().toUri()} (which records no feature pack) either")

        when:
        exit = run('--list-catalog-features', '--feature-catalog', catalog.toURI().toString())

        then:
        exit == 0
        out.toString().contains('the catalog records no feature pack')
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
        'nested too deep'              | { (('[' * 100000) + (']' * 100000)).bytes }                                      || "it is not a catalog: it is nested deeper than ${PackCatalog.MAX_DEPTH}"
        'nothing but open brackets'    | { ('[' * 1000000).bytes }                                                        || 'it is not a catalog: it is nested deeper than'
        'of more values than allowed'  | { ('[{"x": [' + ('0,' * PackCatalog.MAX_VALUES) + '0]}]').bytes }                || "it is not a catalog: it has more than ${PackCatalog.MAX_VALUES} values"
        'not JSON'                     | { '<html>@|red not found|@\u001b[31m</html>'.bytes }                             || 'it is not a catalog: it is not JSON'
        'YAML'                         | { 'name: Cache\ncoords: org.apache.grails:grails-cache\n'.bytes }               || 'it is not a catalog: it is not JSON'
        'not UTF-8'                    | { [0x5b, 0x22, 0xff, 0xfe, 0x22, 0x5d] as byte[] }                               || 'it is not written in UTF-8'
        'cut off'                      | { '[{"coords": "com.acme:acme'.bytes }                                           || 'it is not a catalog: a text is not closed'
        'followed by more'             | { '[] {}'.bytes }                                                                || 'it is not a catalog: there is something after the document'
        'a text'                       | { '"catalog"'.bytes }                                                            || 'it is not a catalog: the document is not an array of plugins, as the plugin index is'
        'an object with the plugins'   | { '{"format": 1, "plugins": []}'.bytes }                                         || 'it is not a catalog: the document is not an array of plugins, as the plugin index is'
        'with a key twice'             | { '[{"name": "a", "name": "b"}]'.bytes }                                         || 'it is not a catalog: an object has one key twice'
        'with a number too long'       | { ('[{"x": 1.' + ('3' * PackCatalog.MAX_NUMBER) + '}]').bytes }                   || "it is not a catalog: a number has more than ${PackCatalog.MAX_NUMBER} characters"
        'with a number that is none'   | { '[{"x": 01}]'.bytes }                                                          || 'it is not a catalog: it is not JSON'
        'with half a number'           | { '[{"x": 1e}]'.bytes }                                                          || 'it is not a catalog: it is not JSON'
        'with a key too long'          | { ('[{"' + ('k' * 201) + '": 1}]').bytes }                                        || 'it is not a catalog: an object has a key of more than 200 characters'
        'of too many plugins'          | { ('[' + (['{}'] * (PackCatalog.MAX_PLUGINS + 1)).join(',') + ']').bytes }        || "it is not a catalog: it has more than the ${PackCatalog.MAX_PLUGINS} plugins a catalog can have"
        'with a control character'     | { '[{"x": "a\u0007b"}]'.bytes }                                                  || 'it is not a catalog: a text has a control character in it'
    }

    void "a catalog that is a directory, or is not there, is not read"() {
        when:
        int exit = run('shop', '--features', 'acme-audit', '--feature-catalog', new File(work, 'missing.json').toURI().toString())

        then:
        exit == 1
        err.toString().contains('could not be read: not found')
    }

    @Unroll
    void "a plugin of the catalog with #what is left out, or that one version of it, and the others are still found"() {
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
                "${catalog.toPath().toUri()} (1 ${of} left out for what the catalog says of it; the first: plugin 1: ${problem}")
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
        what                                  | change                                                                         || problem | of
        'coordinates that are none'           | { it.coords = 'com.acme' }                                                     || 'coords is not group:artifact of at most 120 characters, but com.acme' | 'plugin'
        'coordinates that are markup'         | { it.coords = '@|red x|@:\u001b[31my' }                                        || 'coords is not group:artifact of at most 120 characters, but @ |red x|@:?[31my' | 'plugin'
        'coordinates that go up a directory'  | { it.coords = 'com.acme:../../etc' }                                           || 'coords is not group:artifact of at most 120 characters, but com.acme:../../etc' | 'plugin'
        'coordinates of three parts'          | { it.coords = COORDINATES }                                                    || "coords is not group:artifact of at most 120 characters, but ${COORDINATES}" | 'plugin'
        'coordinates that are a number'       | { it.coords = 7 }                                                              || 'coords is not a text' | 'plugin'
        'no coordinates'                      | { it.remove('coords') }                                                        || 'coords is not a text' | 'plugin'
        'a version that is a range'           | { it.versions[0].version = '[2.0,3.0)' }                                       || "${PLUGIN}: version [2.0,3.0) is not one version of a plugin" | 'version'
        'a version that is the latest'        | { it.versions[0].version = 'latest.release' }                                  || "${PLUGIN}: version latest.release is not one version of a plugin" | 'version'
        'a version that is markup'            | { it.versions[0].version = '@|red 1|@' }                                       || "${PLUGIN}: version @ |red 1|@ is not one version of a plugin" | 'version'
        'a version twice'                     | { it.versions << it.versions[0] }                                              || "${PLUGIN}: has the version 2.3.0 more than once" | 'plugin'
        'a version that is a text'            | { it.versions << 'x' }                                                         || "${PLUGIN}: has a version that is not an object" | 'plugin'
        'a pack that is a text'               | { it.versions[0].pack = 'yes' }                                                || "${PLUGIN}: the pack of version 2.3.0 is not an object" | 'version'
        'a name that is none'                 | { it.versions[0].pack.name = 'Acme Audit' }                                    || "${PLUGIN}: the pack of version 2.3.0: name is not the name of a feature, but Acme Audit" | 'version'
        'a name that is markup'               | { it.versions[0].pack.name = '@|red x|@\u0007' }                               || "${PLUGIN}: the pack of version 2.3.0: name is not the name of a feature, but @ |red x|@?" | 'version'
        'a name that is a list'               | { it.versions[0].pack.name = ['acme-audit'] }                                  || "${PLUGIN}: the pack of version 2.3.0: name is not a text" | 'version'
        'no digest'                           | { it.versions[0].pack.remove('sha256') }                                       || "${PLUGIN}: the pack of version 2.3.0: sha256 is not a text" | 'version'
        'a digest that is none'               | { it.versions[0].pack.sha256 = 'z' * 64 }                                      || "${PLUGIN}: the pack of version 2.3.0: sha256 is not a SHA-256, which is 64 hexadecimal digits" | 'version'
        'a format that is a text'             | { it.versions[0].pack.format = '1' }                                           || "${PLUGIN}: the pack of version 2.3.0: format is not a whole number from 1" | 'version'
        'a template API that is a fraction'   | { it.versions[0].pack.templateApi = 1.5 }                                      || "${PLUGIN}: the pack of version 2.3.0: templateApi is not a whole number from 1" | 'version'
        'no compatibility'                    | { it.versions[0].pack.remove('compatibility') }                                || "${PLUGIN}: the pack of version 2.3.0: compatibility is not an object" | 'version'
        'a range that is none'                | { it.versions[0].pack.compatibility.grails = '@|red 9|@' }                     || "${PLUGIN}: the pack of version 2.3.0: compatibility.grails is not a range of versions" | 'version'
        'coordinates longer than a plugin has' | { it.coords = "com.${'a' * 120}:acme" }                                       || 'coords is not group:artifact of at most 120 characters, but com.aaaa' | 'plugin'
        'coordinates longer than a text'      | { it.coords = "com.${'a' * 5000}:acme" }                                       || 'coords has more than 4096 characters' | 'plugin'
        'a version longer than a version has' | { it.versions[0].version = '1.' + ('0' * 60) }                                 || "${PLUGIN}: has a version of more than 60 characters" | 'version'
        'more versions than a plugin has'     | { (1..PackCatalog.MAX_VERSIONS).each { n -> it.versions << [version: "1.0.${n}"] } } || "${PLUGIN}: has more than the ${PackCatalog.MAX_VERSIONS} versions a plugin can have" | 'plugin'
        'a repository with a password'        | { it['maven-repo'] = 'https://user:secret@repo.acme.invalid/maven' }           || "${PLUGIN}: maven-repo is not the https address of a repository that a catalog can name" | 'plugin'
        'a repository that is a directory'    | { it['maven-repo'] = 'file:/tmp/repository' }                                  || "${PLUGIN}: maven-repo is not the https address of a repository that a catalog can name" | 'plugin'
        'a repository without TLS'            | { it['maven-repo'] = 'http://repo.acme.invalid/maven' }                        || "${PLUGIN}: maven-repo is not the https address of a repository that a catalog can name" | 'plugin'
        'a repository of this computer'       | { it.versions[0]['maven-repo'] = 'https://localhost:8443/maven' }              || "${PLUGIN}: version 2.3.0: maven-repo is not the https address of a repository that a catalog can name" | 'version'
        'coordinates of a version that are none' | { it.versions[0].coords = 'com.acme:audit:2.3.0' }                           || "${PLUGIN}: the coords of version 2.3.0 is not group:artifact of at most 120 characters, but com.acme:audit:2.3.0" | 'version'
        'a version that is said to be there by a text' | { it.versions[0].artifactAvailable = 'no' }                            || "${PLUGIN}: has a version whose artifactAvailable is neither true nor false" | 'version'
        'a repository of a private network'   | { it['maven-repo'] = 'https://192.168.1.10/maven' }                            || "${PLUGIN}: maven-repo is not the https address of a repository that a catalog can name" | 'plugin'
        'a repository with a token'           | { it['maven-repo'] = 'https://repo.acme.invalid/maven?token=secret' }          || "${PLUGIN}: maven-repo is not the https address of a repository that a catalog can name" | 'plugin'
        'deprecated as a number'              | { it.deprecated = 1 }                                                          || "${PLUGIN}: deprecated is neither true, false nor a text" | 'plugin'
        'a description that is a list'        | { it.desc = ['x'] }                                                            || "${PLUGIN}: desc is not a text" | 'plugin'
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
                plugin(PLUGIN, ['2.3.0': pack(jar), '2.2.0': pack(jar)], [desc: "Audits @|red everything|@\u001b[31m\nand more " + ('x' * 3000), docs: 'javascript:alert(1)', name: 'A' * 5000]),
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
        lines.contains('  1 more is not offered: of a deprecated plugin, without a release, not in the version org.apache.grails:grails-bom manages it at, or not for this application, Grails ' +
                "${org.grails.forge.util.VersionInfo.grailsVersion} and Forge ${org.grails.forge.util.VersionInfo.version}".toString())
        lines.contains('  1 more has the name of a feature of the framework, which is the one that is selected by that name')
        lines.contains('  1 version is left out for what the catalog says of it:')
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
        out.toString().contains('No feature catalog is read: --feature-catalog, GRAILS_FORGE_CATALOG or GRAILS_PLUGIN_INDEX names one')
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
        out.toString().contains('    the SHA-256 is the one that same catalog gives for the jar')

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

    void "the repository a catalog names is asked after those the user has, Maven Central among them, and not when repositories are given"() {
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
        asked*.toString().take(2) == ["${PackArtifacts.MAVEN_CENTRAL}${path}", "https://repo.acme.invalid/maven/${path}"]
        out.toString().contains("Asking ${empty.toPath().toUri()}, ${PackArtifacts.MAVEN_CENTRAL}, and then https://repo.acme.invalid/maven/, the repository the catalog names")

        when: 'the repository of the catalog does not have it'
        asked.clear()
        answers.clear()
        exit = run('--list-features', '--features', 'acme-audit', '--feature-catalog', catalog.toURI().toString())

        then: 'it is asked last, after Maven Central'
        exit == 1
        asked*.toString() == ["${PackArtifacts.MAVEN_CENTRAL}${path}", "https://repo.acme.invalid/maven/${path}"]
        err.toString().contains("${empty.toPath().toUri()}: not found; ${PackArtifacts.MAVEN_CENTRAL}: not found; https://repo.acme.invalid/maven/: not found")

        and: 'what the catalog made of the name was said although nothing came of it'
        out.toString().contains("The feature acme-audit is taken as ${COORDINATES}, which the feature catalog")

        when: 'the repositories of the user have Maven Central first among them'
        asked.clear()
        environment = "mavenCentral();https://corp.invalid/repo;${empty.absolutePath}"
        exit = run('--list-features', '--features', 'acme-audit', '--feature-catalog', catalog.toURI().toString())

        then: 'the repository of the catalog is still the last'
        exit == 1
        asked*.host == ['repo.maven.apache.org', 'corp.invalid', 'repo.acme.invalid']

        when: 'repositories are given, with the environment as it was'
        environment = empty.absolutePath
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

    @Unroll
    void "a name that is not known is what it was before there were catalogs when the catalog nobody named cannot be read: #reason"() {
        given: 'the plugin index of Grails is the catalog, and it does not answer'
        setting = null
        pluginIndex = true
        answers[PackCatalogs.PLUGIN_INDEX] = answer

        when:
        int exit = run('shop', '--features', 'h2,hibernate-validtor')

        then: 'the refusal of a feature that does not exist, with the usage, and a line about the catalog'
        exit == 2
        List<String> said = err.toString().readLines()
        said[0] == 'The requested feature does not exist: hibernate-validtor'
        said[1] == "The feature catalog ${PackCatalogs.PLUGIN_INDEX} could not be read: ${reason}, so hibernate-validtor was not looked for there"
        said[2].startsWith('Usage: create-app')
        !new File(dir, 'shop').exists()
        asked*.toString() == [PackCatalogs.PLUGIN_INDEX]

        and: 'that it could not be read is kept beside the copies, for the user alone'
        Path marker = copies.resolve(digest(PackCatalogs.PLUGIN_INDEX.getBytes('UTF-8')) + '.unread')
        Files.size(marker) == 0
        org.grails.forge.application.OperatingSystem.WINDOWS == null || !Files.getFileStore(marker).supportsFileAttributeView('posix') ||
                PosixFilePermissions.toString(Files.getPosixFilePermissions(marker)) == 'rw-------'

        when: 'another name is mistyped the same day'
        asked.clear()
        exit = run('shop', '--features', 'postgress')

        then: 'the catalog is not asked for again'
        exit == 2
        asked.isEmpty()
        err.toString().readLines()[0] == 'The requested feature does not exist: postgress'
        err.toString().readLines()[1] ==~ 'The feature catalog ' + java.util.regex.Pattern.quote(PackCatalogs.PLUGIN_INDEX) +
                / could not be read: the last attempt, at \d{4}-\d\d-\d\dT\d\d:\d\d:\d\dZ, failed, and it is not asked for again within a day; / +
                /--refresh asks for it, so postgress was not looked for there/

        when: 'the catalog is asked for anew'
        exit = run('shop', '--features', 'postgress', '--refresh')

        then:
        exit == 2
        asked*.toString() == [PackCatalogs.PLUGIN_INDEX]
        err.toString().readLines()[1] == "The feature catalog ${PackCatalogs.PLUGIN_INDEX} could not be read: ${reason}, so postgress was not looked for there"

        when: 'the day is over'
        asked.clear()
        Files.setLastModifiedTime(marker, FileTime.from(Instant.now() - PackCatalogs.TIME_TO_LIVE - Duration.ofMinutes(1)))
        exit = run('shop', '--features', 'postgress')

        then:
        exit == 2
        asked*.toString() == [PackCatalogs.PLUGIN_INDEX]

        where:
        reason                                 | answer
        'not found'                            | { new Reply(404, null, null) }
        'timeout'                              | { throw new java.net.http.HttpTimeoutException('request timed out') }
        'TLS failure (SSLHandshakeException)'  | { throw new javax.net.ssl.SSLHandshakeException('no trusted certificate, says the other side') }
        'HTTP status 500'                      | { new Reply(500, null, null) }
        'it is not a catalog: it is not JSON'  | { new Reply(200, null, new ByteArrayInputStream('<html>'.bytes)) }
    }

    void "the catalog nobody named is waited for a few seconds and not as long as a jar"() {
        given:
        setting = null
        pluginIndex = true
        within = Duration.ofSeconds(2)
        answers[PackCatalogs.PLUGIN_INDEX] = {
            Thread.sleep(120000)
            new Reply(404, null, null)
        }

        when:
        long started = System.nanoTime()
        int exit = run('shop', '--features', 'hibernate-validtor')

        then:
        exit == 2
        Duration.ofNanos(System.nanoTime() - started) < Duration.ofSeconds(60)
        err.toString().readLines()[0] == 'The requested feature does not exist: hibernate-validtor'
        err.toString().readLines()[1] == "The feature catalog ${PackCatalogs.PLUGIN_INDEX} could not be read: timeout, so hibernate-validtor was not looked for there"
        PackCatalogs.DEFAULT_WITHIN <= Duration.ofSeconds(10)
    }

    @Unroll
    void "a catalog that was named and cannot be read fails the command: #reason"() {
        given:
        answers[HOSTED] = answer

        when: 'it is named by the option'
        int exit = run('shop', '--features', 'acme-audit', '--feature-catalog', HOSTED)

        then:
        exit == 1
        err.toString().contains("acme-audit is not of Grails or of a feature pack that was given, and was looked for in a feature catalog. " +
                "The feature catalog ${HOSTED} could not be read: ${reason}.")
        !err.toString().contains('Usage')
        !err.toString().contains('the other side')
        !new File(dir, 'shop').exists()

        and: 'what is remembered of the catalog nobody named is not remembered of this one'
        !Files.exists(copies) || Files.list(copies).withCloseable { it.toList() }.isEmpty()

        when: 'it is named by the environment'
        setting = HOSTED
        asked.clear()
        exit = run('shop', '--features', 'acme-audit')

        then:
        exit == 1
        asked*.toString() == [HOSTED]
        err.toString().contains("The feature catalog ${HOSTED} could not be read: ${reason}.")

        where:
        reason                                 | answer
        'not found'                            | { new Reply(404, null, null) }
        'timeout'                              | { throw new java.net.http.HttpTimeoutException('request timed out') }
        'TLS failure (SSLHandshakeException)'  | { throw new javax.net.ssl.SSLHandshakeException('no trusted certificate, says the other side') }
    }

    void "with --offline and no copy of the catalog nobody named, a name that is not known does not exist, and nothing is asked"() {
        given:
        setting = null
        pluginIndex = true

        when:
        int exit = run('shop', '--features', 'hibernate-validtor', '--offline')

        then:
        exit == 2
        err.toString().readLines()[0] == 'The requested feature does not exist: hibernate-validtor'
        err.toString().readLines()[1] == "The feature catalog ${PackCatalogs.PLUGIN_INDEX} could not be read: not asked, since --offline makes no request to " +
                'the network, and there is no copy of the catalog on this computer, so hibernate-validtor was not looked for there'
        asked.isEmpty()

        and: 'that says nothing about whether the catalog can be read'
        !Files.exists(copies) || Files.list(copies).withCloseable { it.toList() }.isEmpty()
    }

    void "a name is found in the catalog nobody named, and the plugin index of today has none"() {
        given:
        setting = null
        pluginIndex = true
        File jar = publish(COORDINATES)
        byte[] document = json([plugin(PLUGIN, ['2.3.0': pack(jar)])]).getBytes('UTF-8')
        answers[PackCatalogs.PLUGIN_INDEX] = { new Reply(200, null, new ByteArrayInputStream(document)) }

        when:
        int exit = run('com.example.shop', '--features', 'acme-audit', '--feature-pack-repository', address)

        then:
        exit == 0
        asked*.toString() == [PackCatalogs.PLUGIN_INDEX]
        out.toString().contains("    the coordinates are those the feature catalog ${PackCatalogs.PLUGIN_INDEX} (sha256 ${digest(document)}) has for the feature acme-audit")
        new File(dir, 'shop/.grails/forge.lock.yml').text.contains("  catalog: ${PackCatalogs.PLUGIN_INDEX}\n  catalogSha256: ${digest(document)}\n")

        when: 'the index is as it is published today'
        Files.list(copies).withCloseable { it.toList() }.each { Files.delete(it) }
        answers[PackCatalogs.PLUGIN_INDEX] = { new Reply(200, null, new ByteArrayInputStream('[{"name": "Cache", "coords": "org.apache.grails:grails-cache"}]'.bytes)) }
        exit = run('other', '--features', 'acme-audit')

        then:
        exit == 2
        err.toString().readLines()[0] == 'The requested feature does not exist: acme-audit. acme-audit was not found in the feature catalog ' +
                "${PackCatalogs.PLUGIN_INDEX} (which records no feature pack) either"

        when: 'the list of the catalog nobody named is asked for, and it cannot be read'
        Files.list(copies).withCloseable { it.toList() }.each { Files.delete(it) }
        answers[PackCatalogs.PLUGIN_INDEX] = { new Reply(500, null, null) }
        exit = run('--list-catalog-features')

        then: 'that is what was asked for, and it failed'
        exit == 1
        err.toString().contains("The feature catalog ${PackCatalogs.PLUGIN_INDEX} could not be read: HTTP status 500")
    }

    void "of a plugin the BOM of the application manages, the version of the BOM is the one that is taken, and no other"() {
        given: 'a plugin the BOM of Grails manages'
        String managed = 'org.apache.grails:grails-cache'
        String version = BomVersions.of('grails-bom').versionOf(managed)
        File ofTheBom = publish("${managed}:${version}")
        File later = publish("${managed}:99.0.0")
        File both = catalog([plugin(managed, [(version): pack(ofTheBom), '99.0.0': pack(later)])], 'both.json')
        File onlyLater = catalog([plugin(managed, ['99.0.0': pack(later), '98.0.0': pack(later)])], 'later.json')
        File unreadable = catalog([plugin(managed, [(version): pack(ofTheBom, [templateApi: 2]), '99.0.0': pack(later)])], 'unreadable.json')
        File laterFormat = catalog([plugin(managed, [(version): pack(ofTheBom, [format: 2]), '99.0.0': pack(later)])], 'later-format.json')
        File otherGroup = catalog([plugin('org.other:grails-cache', ['99.0.0': pack(publish('org.other:grails-cache:99.0.0')), '1.0.0': pack(later)])], 'other.json')
        File underAnother = catalog([plugin('org.evil:wrapper', ['0.0.1': pack(publish("${managed}:0.0.1")), '0.0.2': pack(later)]).tap {
            it.versions.each { it.coords = managed }
        }], 'under-another.json')

        expect:
        version != null

        when: 'the catalog has a pack for the version of the BOM and for a later one'
        int exit = run('--list-features', '--features', 'acme-audit', '--feature-catalog', both.toURI().toString(), '--feature-pack-repository', address)

        then: 'the version of the BOM is taken, also when it is no release'
        exit == 0
        out.toString().contains("Feature pack acme-audit from ${managed}:${version} (${address}), sha256 ${sha256(ofTheBom)}")

        when: 'the catalog has none for that version'
        exit = run('shop', '--features', 'acme-audit', '--feature-catalog', onlyLater.toURI().toString(), '--feature-pack-repository', address)

        then: 'no other version is chosen, and the command names the BOM and says how another version is insisted on'
        exit == 2
        err.toString().contains("The feature acme-audit is not taken from the feature catalog: org.apache.grails:grails-bom, which the application gets, manages " +
                "${managed} at ${version}, and the catalog has no pack for ${version} (it has one for 99.0.0, 98.0.0); another version is used only by its " +
                "coordinates, such as ${managed}:99.0.0")
        !new File(dir, 'shop').exists()

        when: 'the pack of that version is not one this Forge reads'
        exit = run('shop', '--features', 'acme-audit', '--feature-catalog', unreadable.toURI().toString(), '--feature-pack-repository', address)

        then:
        exit == 2
        err.toString().contains("org.apache.grails:grails-bom, which the application gets, manages ${managed} at ${version}, and the pack of ${version} is not one this Forge reads")

        when: 'the pack of that version has a format a later Forge reads'
        exit = run('shop', '--features', 'acme-audit', '--feature-catalog', laterFormat.toURI().toString(), '--feature-pack-repository', address)

        then: 'it is not read, and no other version is chosen'
        exit == 2
        err.toString().contains("org.apache.grails:grails-bom, which the application gets, manages ${managed} at ${version}, and the catalog has no pack for ${version} " +
                '(it has one for 99.0.0)')

        when: 'the coordinates are given'
        exit = run('--list-features', '--features', "${managed}:99.0.0", '--feature-pack-repository', address)

        then:
        exit == 0
        out.toString().contains("Feature pack acme-audit from ${managed}:99.0.0 (${address})")

        when: 'a plugin of another group has the name of the artifact'
        exit = run('--list-features', '--features', 'acme-audit', '--feature-catalog', otherGroup.toURI().toString(), '--feature-pack-repository', address)

        then: 'the BOM does not manage it, and its latest release is taken'
        exit == 0
        out.toString().contains("Feature pack acme-audit from org.other:grails-cache:99.0.0 (${address})")

        when: 'a plugin of another name has versions that are the artifact the BOM manages'
        exit = run('shop', '--features', 'acme-audit', '--feature-catalog', underAnother.toURI().toString(), '--feature-pack-repository', address)

        then: 'the rule is that of what would be fetched'
        exit == 2
        err.toString().contains("org.apache.grails:grails-bom, which the application gets, manages ${managed} at ${version}, and the catalog has no pack for ${version}")

        when: 'the catalog is listed'
        exit = run('--list-catalog-features', '--feature-catalog', both.toURI().toString(), '--feature-catalog', onlyLater.toURI().toString())

        then: 'with the version that would be taken'
        exit == 0
        out.toString().readLines().any { it.startsWith("  acme-audit  ${managed}:${version}") }
    }

    @Unroll
    void "the versions are those of the BOM the application gets: #bom for #options"() {
        given: 'an artifact only the BOM for Hibernate 7 manages, and one both manage'
        String ofHibernate7 = 'org.hibernate.orm:hibernate-envers'
        String ofBoth = 'org.apache.grails:grails-cache'
        File jar = publish("${ofHibernate7}:99.0.0")
        File cache = publish("${ofBoth}:99.0.0", pack(CreateAppFeaturePackSpec.DESCRIPTOR.replace('name: acme-audit', 'name: acme-cache')))
        File catalog = catalog([plugin(ofHibernate7, ['99.0.0': pack(jar)]), plugin(ofBoth, ['99.0.0': pack(cache, [name: 'acme-cache'])]),
                                plugin('com.acme:unmanaged', ['99.0.0': pack(publish('com.acme:unmanaged:99.0.0', pack(CreateAppFeaturePackSpec.DESCRIPTOR
                                        .replace('name: acme-audit', 'name: acme-free'))), [name: 'acme-free'])])])

        expect: 'what the tables of this build say'
        BomVersions.of('grails-bom').versionOf(ofHibernate7) == null
        BomVersions.of('grails-hibernate7-bom').versionOf(ofHibernate7) != null
        BomVersions.of('grails-bom').versionOf(ofBoth) == BomVersions.of('grails-hibernate7-bom').versionOf(ofBoth)

        when: 'the artifact one BOM manages and the other does not'
        int exit = run((['--list-features', '--features', 'acme-audit', '--feature-catalog', catalog.toURI().toString(), '--feature-pack-repository', address] + options) as String[])

        then:
        exit == (managed ? 2 : 0)
        managed || out.toString().contains("Feature pack acme-audit from ${ofHibernate7}:99.0.0 (${address})")
        !managed || err.toString().readLines()[0].startsWith("The feature acme-audit is not taken from the feature catalog: org.apache.grails:${bom}, which the application gets, " +
                "manages ${ofHibernate7} at ${BomVersions.of(bom).versionOf(ofHibernate7)}, and the catalog has no pack for")

        when: 'the artifact both manage'
        exit = run((['--list-features', '--features', 'acme-cache', '--feature-catalog', catalog.toURI().toString(), '--feature-pack-repository', address] + options) as String[])

        then:
        exit == 2
        err.toString().readLines()[0].startsWith("The feature acme-cache is not taken from the feature catalog: org.apache.grails:${bom}, which the application gets, manages ${ofBoth} at ")

        when: 'an artifact neither manages'
        exit = run((['--list-features', '--features', 'acme-free', '--feature-catalog', catalog.toURI().toString(), '--feature-pack-repository', address] + options) as String[])

        then: 'its latest release is taken'
        exit == 0
        out.toString().contains("Feature pack acme-free from com.acme:unmanaged:99.0.0 (${address})")

        where:
        bom                     | options                         | managed
        'grails-bom'            | []                              | false
        'grails-bom'            | ['--data', 'hibernate5']        | false
        'grails-hibernate7-bom' | ['--data', 'hibernate7']        | true
    }

    @Unroll
    void "a name is not made a version of for an application with #bom, whose versions this Forge does not have"() {
        given:
        File jar = publish(COORDINATES)
        File catalog = catalog([plugin(PLUGIN, ['2.3.0': pack(jar)])])

        when:
        int exit = run((['shop', '--features', features, '--feature-catalog', catalog.toURI().toString(), '--feature-pack-repository', address] + options) as String[])

        then: 'the BOM could have the plugin in another version than any that would be chosen here'
        exit == 2
        err.toString().readLines()[0] == "A feature is not taken from a feature catalog for an application with org.apache.grails:${bom}: this Forge does not have the " +
                'versions that BOM manages (it has those of org.apache.grails:grails-bom and org.apache.grails:grails-hibernate7-bom), and so cannot tell which version of ' +
                'the plugin the application would have. Give the plugin by its coordinates, group:artifact:version'
        !new File(dir, 'shop').exists()

        when: 'the plugin is given by its coordinates'
        exit = run((['--list-features', '--features', features.replace('acme-audit', COORDINATES), '--feature-pack-repository', address] + options) as String[])

        then:
        exit == 0
        out.toString().contains("Feature pack acme-audit from ${COORDINATES} (${address})")

        where:
        bom                               | features                      | options
        'grails-micronaut-bom'            | 'grails-micronaut,acme-audit' | []
        'grails-hibernate7-micronaut-bom' | 'acme-audit,grails-micronaut' | ['--data', 'hibernate7']
    }

    void "a pack that a catalog gave for a name is held to the BOM it was chosen for when the features give the application another"() {
        given: 'versions of a BOM that differ from those the application will get, as a feature that another one requires would bring about'
        File jar = publish(COORDINATES)
        File catalog = catalog([plugin(PLUGIN, ['2.3.0': pack(jar)])])
        chosenFor = 'grails-hibernate7-bom'

        when:
        int exit = run('com.example.shop', '--features', 'acme-audit', '--feature-catalog', catalog.toURI().toString(), '--feature-pack-repository', address)

        then:
        exit == 2
        err.toString().readLines()[0].startsWith('The features that were found by their names in a feature catalog were chosen for an application with ' +
                'org.apache.grails:grails-hibernate7-bom, and with all its features the application gets org.apache.grails:grails-bom')
        !new File(dir, 'shop').exists()
    }

    void "a request these specifications do not answer themselves fails the specification, and is not made"() {
        given: 'the command as it is made outside these specifications, and the plugin index of Grails as its catalog'
        setting = null
        pluginIndex = true
        answered = false

        when:
        run('shop', '--features', 'hibernate-validtor')

        then:
        AssertionError e = thrown()
        e.message == "A request was made to ${PackCatalogs.PLUGIN_INDEX}, and the specifications of the Forge CLI ask nothing of another computer"

        when: 'a jar is asked for'
        new PackArtifacts().resolve(PackArtifacts.coordinates(COORDINATES), [PackArtifacts.repository('https://repository.invalid/maven')], null)

        then:
        AssertionError ofTheJar = thrown()
        ofTheJar.message.startsWith('A request was made to https://repository.invalid/maven/com/acme/acme-audit-grails/2.3.0/acme-audit-grails-2.3.0.jar')
    }

    void "without a catalog that is named, a name that is not known is what it was before there were catalogs"() {
        given: 'the command as it is, with nothing named'
        setting = null
        pluginIndex = PackCatalogs.PLUGIN_INDEX_UNNAMED

        expect: 'the plugin index of Grails is not read unless it is named'
        !PackCatalogs.PLUGIN_INDEX_UNNAMED

        when:
        int exit = run('shop', '--features', 'h2,hibernate-validtor')

        then: 'the refusal of before, and nothing else'
        exit == 2
        err.toString().readLines()[0] == 'The requested feature does not exist: hibernate-validtor'
        err.toString().readLines()[1].startsWith('Usage: create-app')
        !err.toString().contains('catalog could not')
        asked.isEmpty()

        and: 'nothing is written under the home of the user'
        !Files.exists(work.toPath().resolve('.grails'))

        when:
        exit = run('--list-catalog-features')

        then:
        exit == 0
        out.toString().contains('No feature catalog is read: --feature-catalog, GRAILS_FORGE_CATALOG or GRAILS_PLUGIN_INDEX names one')
        asked.isEmpty()
    }

    void "numbers of any size and form a catalog has are checked to be numbers and are not made numbers of"() {
        given:
        File jar = publish(COORDINATES)
        File catalog = new File(work, 'numbers.json')
        catalog.setText('[{"x": 1e99999999999, "y": [-0.0E-7, 1E+99999999999, 1.333333333333333333333333333333333333333, -1e-99999999999, 0, -0]}, ' +
                json(plugin(PLUGIN, ['2.3.0': pack(jar)], [stars: 1.0e400])).replace('1.0E+400', '1.0e400') + ']', 'UTF-8')

        when:
        int exit = run('--list-features', '--features', 'acme-audit', '--feature-catalog', catalog.toURI().toString(), '--feature-pack-repository', address)

        then:
        exit == 0
        out.toString().contains("Feature pack acme-audit from ${COORDINATES} (${address})")
        !err.toString().contains('exponent')

        when: 'such a number is where a whole one is read'
        catalog.setText(catalog.getText('UTF-8').replace('"templateApi": 1', '"templateApi": 1e99999999999'), 'UTF-8')
        exit = run('shop', '--features', 'acme-audit', '--feature-catalog', catalog.toURI().toString(), '--feature-pack-repository', address)

        then: 'that version is left out'
        exit == 2
        err.toString().readLines()[0].contains("plugin 2: ${PLUGIN}: the pack of version 2.3.0: templateApi is not a whole number from 1")
    }

    void "a catalog of the most a catalog can be is read in a small memory, and what is not read of it is not kept"() {
        given: 'documents as large as a catalog can be: one of plugins with packs, one of what is not read'
        File jar = publish(COORDINATES)
        String digest = sha256(jar)
        StringBuilder withPacks = new StringBuilder('[')
        int plugins = 0
        while (withPacks.length() < PackCatalog.MAX_BYTES - 4000 && plugins < PackCatalog.MAX_PLUGINS && (plugins + 1) * 140 < PackCatalog.MAX_VALUES) {
            withPacks.append(plugins++ == 0 ? '' : ',').append('{"coords": "com.acme:p').append(plugins).append('", "versions": [')
            (1..12).each { int version ->
                withPacks.append(version == 1 ? '' : ',').append('{"version": "1.').append(version).append('", "pack": {"format": 1, "kind": "feature", "name": "f')
                        .append(plugins).append('", "templateApi": 1, "compatibility": {"grails": "[9.0.0,10.0.0)", "forge": "[9.0.0,10.0.0)"}, "sha256": "')
                        .append(digest).append('"}}')
            }
            withPacks.append(']}')
        }
        withPacks.append(']')
        File packed = new File(work, 'packed.json')
        packed.setText(withPacks.toString(), 'UTF-8')
        File unread = new File(work, 'unread.json')
        String filler = '"' + ('x' * 2000) + '"'
        unread.setText('[' + json(plugin(PLUGIN, ['2.3.0': pack(jar)])) + ', {"other": [' +
                ([filler] * ((PackCatalog.MAX_BYTES - 4000).intdiv(filler.length() + 1))).join(',') + ']}]', 'UTF-8')

        expect:
        packed.length() > PackCatalog.MAX_BYTES.intdiv(2)
        packed.length() <= PackCatalog.MAX_BYTES
        unread.length() > PackCatalog.MAX_BYTES - 8000
        unread.length() <= PackCatalog.MAX_BYTES

        when: 'each is read in a virtual machine with 128 MiB'
        // what reading a catalog needs, and no more: the classes of this module and of the generator, and Groovy
        String classPath = [PackCatalogProbe, PackCatalog, org.apache.grails.forge.core.pack.PackVersionRange, GroovyObject]
                .collect { Class type -> new File(type.protectionDomain.codeSource.location.toURI()).path }.unique().join(File.pathSeparator)
        List<String> results = [packed, unread].collect { File document ->
            Process process = new ProcessBuilder(new File(System.getProperty('java.home'), 'bin/java').path, '-Xmx128m', '-cp', classPath,
                    PackCatalogProbe.name, document.toURI().toString()).redirectErrorStream(true).start()
            String said = process.inputStream.getText('UTF-8')
            assert process.waitFor(120, java.util.concurrent.TimeUnit.SECONDS)
            "${process.exitValue()} ${said.trim()}".toString()
        }

        then:
        results[0].startsWith("0 plugins=${plugins} versions=${plugins * 12} ")
        results[1].startsWith('0 plugins=1 versions=1 ')
        originalOut.println "heap used after reading a catalog of the most plugins with packs, and one of what is not read: ${results}"
    }

    void "a pack cannot name its own artifact: the artifact is that of its version, which the list and the command show"() {
        given: 'a catalog that gives a pack coordinates, which no pack has'
        File jar = publish(COORDINATES)
        publish('org.evil:evil-thing:6.6.6')
        File catalog = catalog([plugin(PLUGIN, ['2.3.0': pack(jar, [coords: 'org.evil:evil-thing:6.6.6', 'maven-repo': 'https://evil.invalid/maven'])])])

        when:
        int exit = run('--list-features', '--features', "${PLUGIN}#acme-audit", '--feature-catalog', catalog.toURI().toString(), '--feature-pack-repository', address)

        then: 'they are not read, and the jar is that of the plugin'
        exit == 0
        out.toString().contains("Feature pack acme-audit from ${COORDINATES} (${address}), sha256 ${sha256(jar)}")
        !out.toString().contains('evil')
    }

    void "a version that the index has under other coordinates and another repository is taken from there, and the command says so before it asks"() {
        given: 'a plugin whose earlier versions were published under another name, as the plugin index has it for many'
        File jar = publish('org.grails.plugins:mail:4.0.2')
        Map entry = plugin('org.grails.plugins:grails-mail', ['4.0.2': pack(jar)], ['maven-repo': 'https://repo1.maven.org/maven2'])
        entry.versions[0].coords = 'org.grails.plugins:mail'
        entry.versions[0]['maven-repo'] = 'https://repo.grails.org/plugins'
        File catalog = catalog([entry])
        String path = 'org/grails/plugins/mail/4.0.2/mail-4.0.2.jar'
        answers["https://repo.grails.org/plugins/${path}".toString()] = { new Reply(200, null, new ByteArrayInputStream(jar.bytes)) }

        when: 'the repositories are those the user has'
        int exit = run('com.example.shop', '--features', 'acme-audit', '--feature-catalog', catalog.toURI().toString())

        then: 'the line that comes before anything is asked names the plugin, the coordinates that are fetched and the repository of the catalog'
        exit == 0
        String before = "The feature acme-audit is taken as org.grails.plugins:mail:4.0.2, the coordinates the catalog gives for version 4.0.2 of the plugin " +
                "org.grails.plugins:grails-mail, which the feature catalog ${catalog.toPath().toUri()} (sha256 ${sha256(catalog)}) names for it, with the SHA-256 " +
                "${sha256(jar)}. Asking ${PackArtifacts.MAVEN_CENTRAL}, and then https://repo.grails.org/plugins/, the repository the catalog names"
        out.toString().contains(before)
        out.toString().indexOf(before) < out.toString().indexOf('Feature pack acme-audit from org.grails.plugins:mail:4.0.2 (https://repo.grails.org/plugins/)')
        asked*.toString().take(2) == ["${PackArtifacts.MAVEN_CENTRAL}${path}", "https://repo.grails.org/plugins/${path}"]

        and: 'the state of the project has the coordinates that were fetched'
        new File(dir, 'shop/.grails/project.yml').text.contains("requestedFeatures:\n- name: acme-audit\n  coordinates: org.grails.plugins:mail\n  version: 4.0.2\n")
        new File(dir, 'shop/.grails/forge.lock.yml').text.contains("- name: acme-audit\n  source: pack\n  origin: https://repo.grails.org/plugins/\n" +
                "  coordinates: org.grails.plugins:mail\n  version: 4.0.2\n  sha256: ${sha256(jar)}\n")

        when: 'the full name is that of the plugin the catalog lists'
        asked.clear()
        exit = run('--list-features', '--features', 'org.grails.plugins:grails-mail#acme-audit', '--feature-catalog', catalog.toURI().toString(),
                '--feature-pack-repository', address)

        then: 'and not that of the coordinates of the version'
        exit == 0
        out.toString().contains("Feature pack acme-audit from org.grails.plugins:mail:4.0.2 (${address})")
        asked.isEmpty()

        when:
        exit = run('shop2', '--features', 'org.grails.plugins:mail#acme-audit', '--feature-catalog', catalog.toURI().toString(), '--feature-pack-repository', address)

        then:
        exit == 2
        err.toString().readLines()[0].startsWith('The requested feature does not exist: org.grails.plugins:mail#acme-audit')

        when: 'the list of the catalog is asked for'
        exit = run('--list-catalog-features', '--feature-catalog', catalog.toURI().toString())

        then: 'it shows what would be fetched and which plugin that is'
        exit == 0
        out.toString().readLines().any { it.startsWith('  acme-audit  org.grails.plugins:mail:4.0.2 of org.grails.plugins:grails-mail') }
    }

    @Unroll
    void "a pack is for a Grails that both its own range and the range of the descriptor of the plugin have: #grailsVersion"() {
        given:
        File jar = publish(COORDINATES)
        Map entry = plugin(PLUGIN, ['2.3.0': pack(jar)])
        entry.versions[0].grailsVersion = grailsVersion
        File catalog = catalog([entry])

        when:
        int exit = run(((offered ? ['--list-features'] : ['shop']) + ['--features', 'acme-audit', '--feature-catalog', catalog.toURI().toString(),
                                                                         '--feature-pack-repository', address]) as String[])

        then:
        exit == (offered ? 0 : 2)
        offered || err.toString().readLines()[0].contains(said)
        !offered || out.toString().contains("Feature pack acme-audit from ${COORDINATES}")

        where: 'the forms the plugin index has, and what is none'
        grailsVersion             | offered | said
        '7.0.0 > *'               | true    | null
        '7.0.0  > *'              | true    | null
        '2.4 >= *'                | true    | null
        '3.1.* > *'               | true    | null
        '9.0.0-SNAPSHOT > *'      | true    | null
        '7.0.0.BUILD-SNAPSHOT > *' | true   | null
        '9.0.0.RC1 > *'           | true    | null
        '3 > *'                   | true    | null
        '7.0.0 > 9.9.9'           | true    | null
        '7.0.0 > 9.*'             | true    | null
        '5.3.0 > 6.2.3'           | false   | "${PLUGIN} has it in no version for Grails ${org.grails.forge.util.VersionInfo.grailsVersion} and Forge ${org.grails.forge.util.VersionInfo.version} " +
                "(2.3.0 is for Grails ${RANGE}, which the descriptor of the plugin gives as 5.3.0 > 6.2.3 and Forge ${RANGE})"
        '10.0.0 > *'              | false   | 'which the descriptor of the plugin gives as 10.0.0 > *'
        '9.1 > *'                 | false   | 'which the descriptor of the plugin gives as 9.1 > *'
        'any'                     | false   | "1 version left out for what the catalog says of it; the first: plugin 1: ${PLUGIN}: version 2.3.0 is left out: its grailsVersion is not a range " +
                'of the form 7.0.0 > *, but any'
        '7.0.0 < *'               | false   | 'its grailsVersion is not a range of the form 7.0.0 > *, but 7.0.0 < *'
        '@|red 7|@ > *'           | false   | 'its grailsVersion is not a range of the form 7.0.0 > *, but @ |red 7|@ > *'
    }

    void "one version with a range that is none is left out, and the other versions of the plugin are still found"() {
        given:
        File jar = publish(COORDINATES)
        Map entry = plugin(PLUGIN, ['2.4.0': pack(publish("${PLUGIN}:2.4.0")), '2.3.0': pack(jar)])
        entry.versions[0].grailsVersion = 'whatever the descriptor said'
        entry.versions[1].grailsVersion = '7.0.0 > *'
        entry.versions << [version: '2.2.0', grailsVersion: ['not', 'read'], date: 7]
        File catalog = catalog([entry])

        when:
        int exit = run('--list-features', '--features', 'acme-audit', '--feature-catalog', catalog.toURI().toString(), '--feature-pack-repository', address)

        then:
        exit == 0
        out.toString().contains("Feature pack acme-audit from ${COORDINATES} (${address})")

        when:
        exit = run('--list-catalog-features', '--feature-catalog', catalog.toURI().toString())

        then:
        exit == 0
        out.toString().contains('  1 version is left out for what the catalog says of it:')
        out.toString().contains("    plugin 1: ${PLUGIN}: version 2.4.0 is left out: its grailsVersion is not a range of the form 7.0.0 > *, but whatever the descriptor said")
    }

    void "a version the index says has no artifact is not offered, whatever pack it has"() {
        given:
        File jar = publish(COORDINATES)
        Map entry = plugin(PLUGIN, ['2.4.0': pack(publish("${PLUGIN}:2.4.0")), '2.3.0': pack(jar)])
        entry.versions[0].artifactAvailable = false
        entry.versions[1].artifactAvailable = true
        File catalog = catalog([entry])

        when:
        int exit = run('--list-features', '--features', 'acme-audit', '--feature-catalog', catalog.toURI().toString(), '--feature-pack-repository', address)

        then:
        exit == 0
        out.toString().contains("Feature pack acme-audit from ${COORDINATES} (${address})")
    }

    void "a name that is a feature of the framework for another type of application is never looked for in a catalog"() {
        given: 'a feature the framework has, and not for a REST API'
        ProjectGenerator generator = beanContext.getBean(ProjectGenerator)
        String other = generator.availableFeatures(org.grails.forge.application.ApplicationType.WEB, []).allFeatures.toList()*.name
                .find { !generator.availableFeatures(org.grails.forge.application.ApplicationType.REST_API, []).findFeature(it, true).isPresent() }
        assert other != null
        CreateRestApiCommand command = new CreateRestApiCommand(beanContext.getBean(org.grails.forge.application.RestApiAvailableFeatures),
                beanContext.getBean(ContextFactory), generator) {

            @Override
            protected PackArtifacts createPackArtifacts() {
                return new PackArtifacts({ URI asking -> throw new AssertionError("asked ${asking}") } as Transport)
            }

            @Override
            protected String featureCatalogSetting() {
                return 'https://catalog.invalid/never.json'
            }
        }

        when:
        int exit = new CommandLine(command, new GrailsPicocliFactory(beanContext)).execute('shop', '--features', other, '--feature-pack-repository', address)

        then: 'the refusal of the framework, and no request'
        exit == 2
        err.toString().readLines()[0] == "The requested feature does not exist: ${other}"
        !new File(dir, 'shop').exists()
    }

    @Unroll
    void "the first catalog that lists a plugin speaks for it, also when it has it #how, and the list agrees"() {
        given:
        File jar = publish(COORDINATES)
        File other = publish('org.other:audit:1.0.0', pack(CreateAppFeaturePackSpec.DESCRIPTOR.replace('name: acme-audit', 'name: other-audit')))
        File withoutPack = new File(work, 'without-pack.json')
        withoutPack.setText(json([[coords: PLUGIN, versions: [[version: '2.3.0']]]]), 'UTF-8')
        File withoutName = catalog([plugin(PLUGIN, ['2.3.0': pack(jar, [name: 'acme-other'])])], 'without-name.json')
        File leftOut = catalog([plugin(PLUGIN, ['2.3.0': pack(jar, [sha256: 'none'])])], 'left-out.json')
        File second = catalog([plugin(PLUGIN, ['2.3.0': pack(jar)]), plugin('org.other:audit', ['1.0.0': pack(other, [name: 'other-audit'])])], 'second.json')
        File first = [withoutPack, withoutName, leftOut][which]

        when:
        int exit = run('shop', '--features', 'acme-audit', '--feature-catalog', first.toURI().toString(), '--feature-catalog', second.toURI().toString(),
                '--feature-pack-repository', address)

        then: 'what the second catalog says of the plugin is not looked at'
        exit == 2
        err.toString().readLines()[0] == "The feature acme-audit is not taken from the feature catalog: the feature catalog ${first.toPath().toUri()} is the first that " +
                "lists ${PLUGIN} and has no feature acme-audit for it, and what a later catalog says of that plugin is not looked at"
        !new File(dir, 'shop').exists()

        when: 'the full name is given'
        exit = run('shop', '--features', "${PLUGIN}#acme-audit", '--feature-catalog', first.toURI().toString(), '--feature-catalog', second.toURI().toString(),
                '--feature-pack-repository', address)

        then:
        exit == 2
        err.toString().readLines()[0].contains("is the first that lists ${PLUGIN}")

        when: 'the catalogs are listed'
        exit = run('--list-catalog-features', '--feature-catalog', first.toURI().toString(), '--feature-catalog', second.toURI().toString())

        then: 'the plugin is not offered by the second, and another plugin of the second is'
        exit == 0
        !out.toString().readLines().any { it.startsWith('  acme-audit ') }
        out.toString().readLines().any { it.startsWith('  other-audit  org.other:audit:1.0.0') }
        out.toString().contains('  1 plugin is not looked at: a catalog that is named before this one lists it')

        when: 'a plugin only the second has is asked for'
        exit = run('--list-features', '--features', 'other-audit', '--feature-catalog', first.toURI().toString(), '--feature-catalog', second.toURI().toString(),
                '--feature-pack-repository', address)

        then:
        exit == 0
        out.toString().contains("Feature pack other-audit from org.other:audit:1.0.0 (${address})")

        where:
        which | how
        0     | 'without a pack'
        1     | 'without the name'
        2     | 'with a pack that is left out'
    }

    void "a message that is made of what a catalog says is one line of a length that has an end"() {
        given: 'as many plugins as can have one name, with coordinates as long as a plugin has'
        File jar = publish(COORDINATES)
        List plugins = (1..300).collect { int number -> plugin("com.${'a' * 100}:p${number}", ['1.0.0': pack(jar)]) }
        File many = catalog(plugins, 'many.json')
        File reasons = catalog((1..300).collect { int number -> plugin("com.${'a' * 100}:p${number}", [('1.0.0-' + ('b' * 50)): pack(jar)]) }, 'reasons.json')

        when:
        int exit = run('shop', '--features', 'acme-audit', '--feature-catalog', many.toURI().toString())

        then:
        exit == 2
        err.toString().readLines()[0].startsWith('More than one plugin of the feature catalog has a feature acme-audit: name the one that is meant as com.aaa')
        err.toString().readLines()[0].endsWith('(and 297 more)')
        err.toString().readLines()[0].length() < 600

        when:
        exit = run('shop', '--features', 'acme-audit', '--feature-catalog', reasons.toURI().toString())

        then:
        exit == 2
        err.toString().readLines()[0].startsWith('The feature acme-audit is not taken from the feature catalog: com.aaa')
        err.toString().readLines()[0].length() <= PackCatalogs.MAX_MESSAGE + 3

        when: 'more names than a line names are not found'
        exit = run('shop', '--features', (1..40).collect { "nothing-${'c' * 60}-${it}" }.join(','), '--feature-catalog', many.toURI().toString())

        then:
        exit == 2
        err.toString().readLines()[0].length() < 6000
        err.toString().readLines()[0].contains(' and 37 more were not found in the feature catalog')
    }

    @Unroll
    void "the feature of one pack that is asked for as #features is taken once, and is one feature"() {
        given:
        File jar = publish(COORDINATES)
        File catalog = catalog([plugin(PLUGIN, ['2.3.0': pack(jar)])])

        when:
        int exit = run('com.example.shop', '--features', features, '--feature-catalog', catalog.toURI().toString(), '--feature-pack-repository', address)

        then:
        exit == 0
        out.toString().count('Feature pack acme-audit from') == 1
        !err.toString().contains('both have the name')
        new File(dir, 'shop/.grails/project.yml').text.count('- name: acme-audit\n') == 1
        new File(dir, 'shop/build.gradle').text.count('com.acme:acme-audit-grails:2.3.0') == 1

        where:
        features << ["acme-audit,${PLUGIN}#acme-audit", "${PLUGIN}#acme-audit,acme-audit", "${COORDINATES},${PLUGIN}#acme-audit", "${PLUGIN}#acme-audit,${COORDINATES},acme-audit",
                     "acme-audit,${COORDINATES}", 'acme-audit,acme-audit']
    }

    @IgnoreIf({ os.windows })
    @Unroll
    void "a copy is read only from directories that are the user's own: #what"() {
        given: 'a copy someone put where the copies are, which names another jar than the catalog does'
        File jar = publish(COORDINATES)
        File planted = publish("${PLUGIN}:6.6.6")
        byte[] document = json([plugin(PLUGIN, ['2.3.0': pack(jar)])]).getBytes('UTF-8')
        byte[] hostile = json([plugin(PLUGIN, ['6.6.6': pack(planted)])]).getBytes('UTF-8')
        answers[HOSTED] = { new Reply(200, null, new ByteArrayInputStream(document)) }
        Path elsewhere = Files.createDirectories(work.toPath().resolve('elsewhere'))
        Path directory = arrange.call(copies, elsewhere) as Path
        Path copy = directory.resolve(digest(HOSTED.getBytes('UTF-8')) + '.json')
        Files.write(copy, hostile)

        when:
        int exit = run('--list-features', '--features', 'acme-audit', '--feature-catalog', HOSTED, '--feature-pack-repository', address)

        then: 'the catalog is asked for, the copy is not read, and none is written'
        exit == 0
        asked*.toString() == [HOSTED]
        out.toString().contains("Feature pack acme-audit from ${COORDINATES} (${address})")
        out.toString().contains('No copy of a feature catalog is read or kept: ')
        out.toString().contains(' is not a directory of yours that nobody else can write to')
        out.toString().count('No copy of a feature catalog') == 1
        Files.readAllBytes(copy) == hostile

        cleanup:
        [copies, copies?.parent, copies?.parent?.parent].each { Path each ->
            if (each != null && Files.isDirectory(each, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                Files.setPosixFilePermissions(each, PosixFilePermissions.fromString('rwx------'))
            }
        }

        where:
        what                                         | arrange
        'the directory of the copies is a link'      | { Path at, Path other -> Files.createDirectories(at.parent); Files.createSymbolicLink(at, other); other }
        'the directory above it is a link'           | { Path at, Path other -> Files.createDirectories(at.parent.parent); Files.createSymbolicLink(at.parent, other); Files.createDirectories(other.resolve('catalogs')) }
        'anybody can write to it'                    | { Path at, Path other -> Files.createDirectories(at); Files.setPosixFilePermissions(at, PosixFilePermissions.fromString('rwxrwxrwx')); at }
        'its group can write to the one above'       | { Path at, Path other -> Files.createDirectories(at); Files.setPosixFilePermissions(at.parent, PosixFilePermissions.fromString('rwxrwx---')); at }
        'anybody can write to .grails'               | { Path at, Path other -> Files.createDirectories(at); Files.setPosixFilePermissions(at.parent.parent, PosixFilePermissions.fromString('rwxr-xrwx')); at }
    }

    void "what a command that was ended while it wrote a copy left behind is removed, and what is being written is not"() {
        given:
        File jar = publish(COORDINATES)
        byte[] document = json([plugin(PLUGIN, ['2.3.0': pack(jar)])]).getBytes('UTF-8')
        answers[HOSTED] = { new Reply(200, null, new ByteArrayInputStream(document)) }
        ownCopies()
        Path old = Files.write(copies.resolve('catalog123.tmp'), 'half'.bytes)
        Files.setLastModifiedTime(old, FileTime.from(Instant.now() - Duration.ofHours(2)))
        Path fresh = Files.write(copies.resolve('catalog456.tmp'), 'half'.bytes)
        Path another = Files.write(copies.resolve('notes.txt'), 'mine'.bytes)
        Files.setLastModifiedTime(another, FileTime.from(Instant.now() - Duration.ofHours(2)))

        when:
        int exit = run('--list-features', '--features', 'acme-audit', '--feature-catalog', HOSTED, '--feature-pack-repository', address)

        then:
        exit == 0
        !Files.exists(old)
        Files.exists(fresh)
        Files.exists(another)
        Files.list(copies).withCloseable { it.toList() }*.fileName*.toString().sort() ==
                ['catalog456.tmp', digest(HOSTED.getBytes('UTF-8')) + '.json', 'notes.txt'].sort()
    }

    @IgnoreIf({ os.windows })
    void "with --offline a copy in a directory others can write to is not read, and the command says that, and how to make it one that is"() {
        given: 'a copy of the catalog, under a .grails that the group of the user can write to, as a umask of 002 leaves it'
        File jar = publish(COORDINATES)
        byte[] document = json([plugin(PLUGIN, ['2.3.0': pack(jar)])]).getBytes('UTF-8')
        ownCopies()
        Files.write(copies.resolve(digest(HOSTED.getBytes('UTF-8')) + '.json'), document)
        Files.setPosixFilePermissions(copies.parent.parent, PosixFilePermissions.fromString('rwxrwxr-x'))

        when:
        int exit = run('--list-features', '--features', 'acme-audit', '--feature-catalog', HOSTED, '--feature-pack-repository', address, '--offline')

        then:
        exit == 1
        err.toString().contains("The feature catalog ${HOSTED} could not be read: not asked, since --offline makes no request to the network, " +
                "and no copy of a catalog is read on this computer, whether there is one or not: ${copies.parent.parent} is not a directory of yours that nobody else can write to: it can be written to by its group or by others, " +
                'so what is in it need not be a copy this command made; chmod go-w on it makes it yours alone')
        !err.toString().contains('there is no copy of the catalog')
        asked.isEmpty()

        when: 'the directory is made the user\'s alone'
        Files.setPosixFilePermissions(copies.parent.parent, PosixFilePermissions.fromString('rwx------'))
        exit = run('--list-features', '--features', 'acme-audit', '--feature-catalog', HOSTED, '--feature-pack-repository', address, '--offline')

        then:
        exit == 0
        out.toString().contains("Feature pack acme-audit from ${COORDINATES} (${address})")
    }

    @Unroll
    void "a plugin is deprecated by true and by a reason, and not by #value"() {
        given:
        File jar = publish(COORDINATES)
        byte[] document = json([plugin(PLUGIN, ['2.3.0': pack(jar)], [deprecated: value])]).getBytes('UTF-8')

        expect:
        PackCatalog.parse(document, URI.create('https://catalog.invalid/index.json')).plugins*.deprecated == [deprecated]

        where: 'the reason, which is empty where the index gives none, and null for a plugin that is not deprecated'
        value         | deprecated
        true          | ''
        'use another' | 'use another'
        'falsely'     | 'falsely'
        false         | null
        'false'       | null
        'False'       | null
        ' false '     | null
        ''            | null
    }

    void "a plugin whose only pack is after the versions that are read is named as one that was not read to its end"() {
        given: 'a plugin with more versions than are read, the pack in one after them'
        File jar = publish(COORDINATES)
        Map<String, Object> entry = plugin(PLUGIN, [:])
        entry.versions = (1..(PackCatalog.MAX_VERSIONS + 5)).collect { int number ->
            number == PackCatalog.MAX_VERSIONS + 3 ? [version: "1.0.${number}".toString(), pack: pack(jar)] : [version: "1.0.${number}".toString()]
        }
        byte[] document = json([entry, plugin('org.other:other-grails', ['1.0.0': pack(jar, [name: 'other'])])]).getBytes('UTF-8')

        when:
        PackCatalog catalog = PackCatalog.parse(document, URI.create('https://catalog.invalid/index.json'))

        then:
        catalog.plugins*.coordinates == ['org.other:other-grails']
        catalog.problems == ["plugin 1: ${PLUGIN} has more than the ${PackCatalog.MAX_VERSIONS} versions a plugin can have, and none of the first of them has a pack: " +
                'whether a later one has was not read']
    }

    @IgnoreIf({ os.windows || !ForkedCli.available() })
    void "the CLI as a program, with no catalog named by the command or the environment, asks no computer for one"() {
        given: 'whatever the program would ask of the network comes here'
        ServerSocket listener = new ServerSocket(0, 50, InetAddress.loopbackAddress)
        List<String> connections = [].asSynchronized()
        Thread accepting = new Thread({
            try {
                while (true) {
                    listener.accept().withCloseable { Socket socket -> connections << socket.remoteSocketAddress.toString() }
                }
            } catch (IOException ignored) {
                // closed when the feature ends
            }
        })
        accepting.daemon = true
        accepting.start()
        List<String> jvm = ["-Duser.home=${work.path}".toString(), '-Dhttp.proxyHost=127.0.0.1', "-Dhttp.proxyPort=${listener.localPort}".toString(),
                            '-Dhttps.proxyHost=127.0.0.1', "-Dhttps.proxyPort=${listener.localPort}".toString(), '-Dhttp.nonProxyHosts=',
                            '-DsocksProxyHost=127.0.0.1', "-DsocksProxyPort=${listener.localPort}".toString()]

        expect: 'what this build is made with'
        !PackCatalogs.PLUGIN_INDEX_UNNAMED
        PackCatalogs.addresses([], null, PackCatalogs.PLUGIN_INDEX_UNNAMED).isEmpty()

        when: 'the features of catalogs are asked for, and an application with a feature that is none of Grails'
        List<String> said = [['create-app', '--list-catalog-features'], ['create-app', 'shop', '--features', 'acme-audit']].collect { List<String> line ->
            ProcessBuilder builder = ForkedCli.builder(dir, jvm, line as String[]).redirectErrorStream(true)
            // the build names no catalog for the specifications; here nothing at all does, as for a user
            builder.environment().remove('GRAILS_FORGE_CATALOG')
            builder.environment().remove('GRAILS_PLUGIN_INDEX')
            Process program = builder.start()
            String text = program.inputStream.getText('UTF-8')
            assert program.waitFor(300, java.util.concurrent.TimeUnit.SECONDS)
            "${program.exitValue()} ${text}".toString()
        }

        then:
        said[0].startsWith('0 ')
        said[0].contains('No feature catalog is read: --feature-catalog, GRAILS_FORGE_CATALOG or GRAILS_PLUGIN_INDEX names one')
        !said[1].startsWith('0 ')
        said[1].contains('acme-audit')
        !new File(dir, 'shop').exists()
        connections.isEmpty()
        !Files.exists(copies)

        cleanup:
        listener?.close()
    }

    private int run(String... args) {
        String catalogs = setting
        String index = indexVariable
        String repositories = environment
        Path home = work.toPath()
        boolean defaultIndex = pluginIndex
        String otherBom = chosenFor
        Duration waited = within
        boolean fake = answered
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
            protected String pluginIndexSetting() {
                return index
            }

            @Override
            protected Path featureCatalogHome() {
                return home
            }

            @Override
            protected boolean readsPluginIndexUnnamed() {
                return defaultIndex
            }

            @Override
            protected String bomOfTheSelection() {
                return otherBom ?: super.bomOfTheSelection()
            }

            @Override
            protected PackArtifacts createPackArtifacts() {
                return fake ? new PackArtifacts(transport) : new PackArtifacts()
            }

            @Override
            protected Duration defaultCatalogWithin() {
                return waited
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
        catalog.setText(json(plugins), 'UTF-8')
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
