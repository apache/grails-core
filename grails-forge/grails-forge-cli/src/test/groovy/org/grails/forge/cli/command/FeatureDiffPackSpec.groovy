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
import org.grails.forge.cli.CliRunner
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import spock.lang.Unroll

/**
 * {@code feature diff} of the feature of a feature pack the application does not have yet, with the
 * options {@code feature add} takes for such a pack: the SHA-256 its jar has to have, a feature
 * catalog its name is looked up in, and the options of the feature. With each, the diff is line for
 * line the one {@code feature add --diff} shows with the same options, and the two commands end with
 * the same exit code. Every repository and catalog here is a file, and nothing is asked of the network.
 */
class FeatureDiffPackSpec extends CommandSpec {

    static final String PLUGIN = 'com.acme:acme-audit-grails'
    static final String COORDINATES = PLUGIN + ':2.3.0'
    static final String RANGE = '[9.0.0,10.0.0)'
    // what feature add takes that is of what it does with the plan, and not of what is added
    static final List<String> OF_ADDING = ['--dry-run', '--diff', '--json']

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()
    File work
    File jar
    String repository
    String catalog

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(err, true))
        work = Files.createTempDirectory('grailsforgediffpack').toFile().canonicalFile
        File directory = new File(work, 'repository/com/acme/acme-audit-grails/2.3.0')
        directory.mkdirs()
        jar = new File(directory, 'acme-audit-grails-2.3.0.jar')
        new ZipOutputStream(new FileOutputStream(jar)).withCloseable { ZipOutputStream zip ->
            ['META-INF/grails-forge/feature.yml'                   : FeatureOptionSpec.DESCRIPTOR,
             'META-INF/grails-forge/templates/audit.properties.gsp': FeatureOptionSpec.TEMPLATE].each { String path, String text ->
                zip.putNextEntry(new ZipEntry(path))
                zip.write(text.getBytes('UTF-8'))
                zip.closeEntry()
            }
        }
        repository = new File(work, 'repository').toPath().toUri().toString()
        File catalogFile = new File(work, 'catalog.json')
        catalogFile.setText('[{"coords": "' + PLUGIN + '", "owner": "Acme", "licenses": ["Apache-2.0"], "versions": [{"version": "2.3.0", "date": "2026-10-04T18:46:18Z", ' +
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

    @Unroll
    void "feature diff of a pack that is added by #by writes the diff feature add --diff shows with the same options, and changes nothing"() {
        given:
        Map<String, String> before = tree()
        List<String> arguments = asked.call(this)

        when:
        int exit = run((['feature', 'diff'] + arguments) as String[])
        List<String> diff = out.toString().readLines()
        String notes = err.toString()
        reset()
        int add = run((['feature', 'add'] + arguments + ['--diff']) as String[])
        List<String> shown = out.toString().readLines()

        then: 'both can add the feature'
        exit == 0
        add == 0
        shown.last() == '| Nothing was changed'

        and: 'the very lines, with the file of the pack as the options of the feature make it'
        int from = shown.findIndexOf { it.startsWith('--- ') }
        from > 0
        diff == shown.subList(from, shown.size() - 1)
        diff.first().startsWith('--- ')
        diff.contains('+    implementation "com.acme:acme-audit-grails:2.3.0"')
        diff.contains("+audit.store=${store}".toString())
        diff.contains("+audit.retention=${retention}".toString())

        and: 'which plugin a catalog made of a name is said by both, by feature diff where the diff is not'
        notes.contains(said)
        shown.take(from).join('\n').contains(said)

        and:
        tree() == before

        where:
        by                                        | asked                                                                                               | store        | retention | said
        'its coordinates and its SHA-256'         | { it.byCoordinates(['--feature-pack-sha256', "${COORDINATES}=${sha256(it.jar)}".toString()]) }       | 'table'      | '90'      | ''
        'its name in a feature catalog'           | { ['acme-audit', '--feature-catalog', it.catalog, '--feature-pack-repository', it.repository] }      | 'table'      | '90'      | "The feature acme-audit is taken as ${COORDINATES}, which the feature catalog"
        'its name in a catalog that is read anew' | { ['acme-audit', '--feature-catalog', it.catalog, '--refresh', '--feature-pack-repository', it.repository] } | 'table' | '90'      | "The feature acme-audit is taken as ${COORDINATES}, which the feature catalog"
        'its coordinates with an option'          | { it.byCoordinates(['--feature-option', 'acme-audit.store=collection']) }                           | 'collection' | '90'      | ''
        'its coordinates with two options'        | { it.byCoordinates(['--feature-option=acme-audit.store=collection', '--feature-option', 'acme-audit.retention=30']) } | 'collection' | '30' | ''
        'the directory of its pack with an option' | { it.byDirectory(['--feature-option', 'acme-audit.store=collection']) } | 'collection' | '90' | ''
        'a catalog, a SHA-256 and an option'      | { ['acme-audit', '--feature-catalog', it.catalog, '--feature-pack-repository', it.repository, '--feature-pack-sha256', "${COORDINATES}=${sha256(it.jar)}".toString(), '--feature-option', 'acme-audit.retention=7'] } | 'table' | '7' | "The feature acme-audit is taken as ${COORDINATES}, which the feature catalog"
    }

    @Unroll
    void "feature diff ends as feature add --diff does for #what, and neither changes anything"() {
        given:
        Map<String, String> before = tree()
        List<String> arguments = asked.call(this)

        when:
        int exit = run((['feature', 'diff'] + arguments) as String[])
        String said = err.toString()
        String written = out.toString()
        reset()
        int add = run((['feature', 'add'] + arguments + ['--diff']) as String[])

        then:
        exit == expected
        add == expected
        written.isEmpty()
        said.contains(message)
        err.toString().contains(message)
        tree() == before

        where:
        what                                          | asked                                                                                         | expected | message
        'a jar that has another SHA-256'              | { it.byCoordinates(['--feature-pack-sha256', "${COORDINATES}=${'ab' * 32}".toString()]) }      | 1        | "checksum mismatch: the sha256 of acme-audit-grails-2.3.0.jar is"
        'a SHA-256 that is none'                      | { it.byCoordinates(['--feature-pack-sha256', "${COORDINATES}=nothing".toString()]) }           | 2        | 'does not end with a SHA-256'
        'an option that is not written as one'        | { it.byCoordinates(['--feature-option', 'store']) }                                           | 2        | '--feature-option'
        'an option the feature does not have'         | { it.byCoordinates(['--feature-option', 'acme-audit.colour=blue']) }                          | 2        | 'colour'
        'a value an option cannot have'               | { it.byCoordinates(['--feature-option', 'acme-audit.store=tape']) }                           | 2        | 'tape'
        'a name no catalog has'                       | { ['acme-other', '--feature-catalog', it.catalog, '--feature-pack-repository', it.repository] } | 2        | 'acme-other'
        'a catalog that is no file'                   | { ['acme-audit', '--feature-catalog', new File(it.work, 'none.json').toURI().toString()] }     | 1        | 'none.json'
    }

    void "the options of a feature that is added are those of the application of the current directory: #where they are a mistake in the command line"() {
        given:
        Map<String, String> before = tree()
        if (outside) {
            System.setProperty('user.dir', work.absolutePath)
        }

        when:
        int exit = run((['feature', 'diff', 'grails-quartz'] + type + options) as String[])

        then:
        exit == 2
        out.toString().isEmpty()
        err.toString().contains("${named} for a feature that is added to the application of the current directory. ${where}, feature diff shows a new application")
        tree() == before

        where:
        where               | outside | type                  | options                                                                   | named
        'With --type'       | false   | ['--type', 'web']     | ['--feature-option', 'acme-audit.store=collection']                       | '--feature-option is an option'
        'With --type'       | false   | ['--type=rest-api']   | ['--refresh', '--feature-catalog', 'none']                                | '--feature-catalog, --refresh are options'
        'Outside a project' | true    | []                    | ['--feature-pack-sha256', "${COORDINATES}=${'ab' * 32}".toString()]        | '--feature-pack-sha256 is an option'
    }

    void "feature diff takes every option feature add takes for what is added, by one declaration"() {
        when:
        int exit = run('help', '--json')
        Map feature = ((List<Map>) ((Map) new JsonSlurper().parseText(out.toString())).commands).find { it.name == 'feature' }
        Map<String, Map> commands = ((List<Map>) feature.commands).collectEntries { [it.name, it] }
        Map<String, Map> ofAdd = options(commands.add)
        Map<String, Map> ofDiff = options(commands.diff)

        then: 'every option of feature add but those that say what is done with the plan is one of feature diff'
        exit == 0
        (ofAdd.keySet() - ofDiff.keySet()) == OF_ADDING as Set
        ofDiff.keySet().containsAll(['--feature-pack-sha256', '--feature-catalog', '--refresh', '--feature-option', '--feature-pack-dir', '--feature-pack-repository',
                                     '--offline', '--replace'])

        and: 'and the options of what is added are described in the same words'
        ['--feature-pack-sha256', '--feature-catalog', '--refresh', '--feature-option', '--feature-pack-dir', '--feature-pack-repository', '--offline'].every { String name ->
            ofAdd.get(name) == ofDiff.get(name)
        }
    }

    List<String> byCoordinates(List<String> more) {
        return [COORDINATES, '--feature-pack-repository', repository] + more
    }

    // the pack as a directory, by which its feature is named
    List<String> byDirectory(List<String> more) {
        File pack = new File(work, 'pack')
        new File(pack, 'templates').mkdirs()
        new File(pack, 'feature.yml').setText(FeatureOptionSpec.DESCRIPTOR, 'UTF-8')
        new File(pack, 'templates/audit.properties.gsp').setText(FeatureOptionSpec.TEMPLATE, 'UTF-8')
        return ['acme-audit', '--feature-pack-dir', pack.absolutePath] + more
    }

    static String sha256(File file) {
        return MessageDigest.getInstance('SHA-256').digest(file.bytes).encodeHex().toString()
    }

    // the options of a command of help --json, by the first of their names
    private static Map<String, Map> options(Map command) {
        return ((List<Map>) command.options).collectEntries { Map option -> [((List<String>) option.names).find { it.startsWith('--') }, option] }
    }

    private int run(String... args) {
        return ForgeCli.execute(beanContext, args)
    }

    private void reset() {
        out.reset()
        err.reset()
    }

    private Map<String, String> tree() {
        Map<String, String> entries = new TreeMap<>()
        Files.walk(dir.toPath()).withCloseable { stream ->
            stream.each { Path path ->
                entries.put(dir.toPath().relativize(path).join('/'), Files.isDirectory(path) ? 'directory' :
                        MessageDigest.getInstance('SHA-256').digest(Files.readAllBytes(path)).encodeHex().toString())
            }
        }
        return entries
    }
}
