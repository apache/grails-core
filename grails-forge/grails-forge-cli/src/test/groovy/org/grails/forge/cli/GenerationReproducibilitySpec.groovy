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
package org.grails.forge.cli

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

/**
 * The same command lines write the same bytes, whoever runs them and where. {@link GenerationProbe}
 * runs one list of command lines in three virtual machines that differ in everything a generated
 * application must not depend on: the locale (English, Turkish, whose upper and lower case of
 * {@code i} are other letters, and German), the time zone, the default character set, the line
 * separator, the home directory, and the name and the depth of the directory. Every file of every
 * application, with its mode, and the record of how it was generated ({@code .grails}) have to be the
 * same in all three, and so does what the commands that list something write.
 *
 * <p>The features of an application are a set: the order their names are given in, at creation or to
 * one {@code feature add}, changes no file and nothing in the record, and features that are added one
 * after another leave what adding them at once leaves.</p>
 */
@IgnoreIf({ !ForkedCli.available() })
class GenerationReproducibilitySpec extends Specification {

    private static final List<String> NAMES = ['plain', 'turkish', 'german']
    private static final boolean WINDOWS = System.getProperty('os.name').toLowerCase(Locale.ROOT).contains('win')

    @Shared
    @TempDir
    Path directory

    @Shared
    Map<String, Path> roots = [:]

    @Shared
    Map<String, Map<String, String>> trees = [:]

    void setupSpec() {
        // different names, and different depths
        roots.plain = Files.createDirectories(directory.resolve('a'))
        roots.turkish = Files.createDirectories(directory.resolve('In A Directory/with spaces/and.dots/I'))
        roots.german = Files.createDirectories(directory.resolve('x/y/z/UPPER/lower/9'))
        Map<String, List<String>> options = [
                plain  : ['-Duser.language=en', '-Duser.country=US', '-Duser.timezone=UTC', '-Dfile.encoding=UTF-8'],
                turkish: ['-Duser.language=tr', '-Duser.country=TR', '-Duser.timezone=Pacific/Kiritimati', '-Dfile.encoding=ISO-8859-9'],
                german : ['-Duser.language=de', '-Duser.country=DE', '-Duser.timezone=America/St_Johns', '-Dfile.encoding=US-ASCII']
        ]
        if (!WINDOWS) {
            // on Windows a line ends with both characters already, and a command line cannot hold either
            options.turkish << '-Dline.separator=\r\n'
            options.german << '-Dline.separator=\r'
        }
        NAMES.each { String name ->
            Path home = Files.createDirectories(roots[name].resolve('home'))
            File log = roots[name].resolve('probe.log').toFile()
            ProcessBuilder builder = probe(roots[name], options[name] + ["-Duser.home=${home}".toString()])
            builder.redirectErrorStream(true).redirectOutput(log)
            Map<String, String> environment = builder.environment()
            // nothing of whoever runs the specification: no fixed time, no repositories of their own, no catalogs
            ['SOURCE_DATE_EPOCH', 'GRAILS_REPO_URL', 'JAVA_TOOL_OPTIONS', 'COLORTERM'].each { String variable -> environment.remove(variable) }
            environment.put('GRAILS_FORGE_CATALOG', 'none')
            environment.put('GRAILS_PLUGIN_INDEX', 'none')
            environment.put('TZ', name == 'plain' ? 'UTC' : name == 'turkish' ? 'Asia/Tokyo' : 'America/Los_Angeles')
            environment.put('LANG', name == 'plain' ? 'C' : name == 'turkish' ? 'tr_TR.ISO8859-9' : 'de_DE.UTF-8')
            environment.put('LC_ALL', environment.get('LANG'))
            Process process = builder.start()
            try {
                boolean ended = process.waitFor(10, TimeUnit.MINUTES)
                assert ended && process.exitValue() == 0: "The probe ${name} did not run to its end:\n${log.getText('UTF-8')}"
            } finally {
                if (process.alive) {
                    process.destroyForcibly().waitFor(SpecWaits.BOUND.toMillis(), TimeUnit.MILLISECONDS)
                }
            }
        }
        NAMES.each { String name -> trees[name] = tree(roots[name].resolve('projects')) }
    }

    void "the virtual machines differ in what an application must not depend on"() {
        expect:
        environment('plain') == [locale: 'en-US', timezone: 'UTC', charset: 'UTF-8', lineSeparator: WINDOWS ? '0d0a' : '0a']
        environment('turkish') == [locale: 'tr-TR', timezone: 'Pacific/Kiritimati', charset: 'ISO-8859-9', lineSeparator: '0d0a']
        environment('german') == [locale: 'de-DE', timezone: 'America/St_Johns', charset: 'US-ASCII', lineSeparator: WINDOWS ? '0d0a' : '0d']
    }

    void "every command line succeeds, in every virtual machine"() {
        expect:
        NAMES.each { String name ->
            List<String> exits = roots[name].resolve('exits.txt').toFile().readLines('UTF-8')
            // with what a command that failed wrote, which is why it failed
            Map<String, String> failed = exits.findAll { String line -> !line.endsWith('=0') }.collectEntries { String line ->
                [line, roots[name].resolve("output/${line.substring(0, line.indexOf('='))}.txt").toFile().getText('UTF-8')]
            }
            assert failed == [:]
            assert exits.size() == GenerationProbe.SCENARIOS.values().sum { List<List<String>> commands -> commands.count { it[1] != 'FORGET' } }
        }
    }

    @Unroll
    void "every file and the record of every application are the same bytes with the #other locale, time zone, character set, line separator and directory"() {
        expect:
        trees.plain.size() > 1000
        differing(trees.plain, trees[other]) == []

        where:
        other << ['turkish', 'german']
    }

    @Unroll
    void "what the commands write to the output is the same with the #other locale, time zone, character set, line separator and directory"() {
        given:
        Map<String, String> plain = output('plain')
        Map<String, String> others = output(other)

        expect:
        plain.keySet() == others.keySet()
        plain.findAll { String name, String text -> others[name] != text }.keySet().toList() == []

        where:
        other << ['turkish', 'german']
    }

    @Unroll
    void "#one and #other are the same application: the order names are given in changes no file and nothing in the record"() {
        expect:
        Map<String, String> first = below(trees.plain, one)
        first.size() > 50
        differing(first, below(trees.plain, other)) == []

        where:
        one                 | other
        'features'          | 'features-reordered'
        'options'           | 'options-reordered'
        'packs'             | 'packs-reordered'
        'added-at-once'     | 'added-reordered'
        'added-at-once'     | 'added-in-sequence'
        'added-at-once'     | 'created-with-added'
        'default'           | 'webapp'
    }

    void "an application that is generated again from its record is the application"() {
        given:
        Map<String, String> all = below(trees.plain, 'regenerated')
        Map<String, String> first = all.findAll { it.key.startsWith('shop/') }.collectEntries { [it.key.substring(5), it.value] }
        Map<String, String> again = all.findAll { it.key.startsWith('again/') }.collectEntries { [it.key.substring(6), it.value] }

        expect:
        first.size() > 50
        differing(first, again) == []
    }

    void "the features of two packs that write to the same places are applied by name, whichever pack is given first"() {
        given:
        Path shop = roots.plain.resolve('projects/packs/shop')
        String build = shop.resolve('build.gradle').toFile().getText('UTF-8')
        String configuration = shop.resolve('grails-app/conf/application.yml').toFile().getText('UTF-8')
        String lock = shop.resolve('.grails/forge.lock.yml').toFile().getText('UTF-8')

        expect: 'acme-first before acme-second, which is the order of their names and not that of what they write, wherever lines are added to one place'
        build.contains('    id "com.acme.zeta" version "1.2.3"\n    id "com.acme.alpha" version "1.2.3"\n')
        build.contains('acme {\n    zeta = true\n    alpha = true\n}\n')
        shop.resolve('gradle.properties').toFile().getText('UTF-8').contains('acmeZeta=1\nacmeAlpha=1\n')
        shop.resolve('README.md').toFile().getText('UTF-8').contains('Acme first is here.\n\nAcme second is here.\n')

        and: 'the dependencies are sorted, and one that both declare is there once'
        build.contains('    implementation "com.acme:acme-first:1.2.3"\n    implementation "com.acme:acme-second:1.2.3"\n')
        build.count('com.acme:acme-testing:1.2.3') == 1

        and: 'the settings are sorted by key'
        configuration.contains('acme:\n  alpha: true\n  shared:\n    first: 1\n    second: 1\n  zeta: true\n')

        and: 'an external template renders its model and non-ASCII text as UTF-8'
        String model = shop.resolve('src/main/resources/acme/first-model.txt').toFile().getText('UTF-8')
        model.startsWith('Shop|com/example|acme-first,acme-second,')
        model.endsWith('|café\n')

        and: 'the record names no directory of the computer the application was generated on'
        lock.indexOf('- name: acme-first\n') < lock.indexOf('- name: acme-second\n')
        !lock.contains(roots.plain.toString())
        !lock.contains('packs/')
    }

    void "no file of a record names the computer, the user or the time it was written at"() {
        expect:
        NAMES.each { String name ->
            String root = roots[name].toRealPath().toString()
            String user = System.getProperty('user.name')
            Files.walk(roots[name].resolve('projects')).withCloseable { stream ->
                stream.filter { Path path -> Files.isRegularFile(path) && roots[name].resolve('projects').relativize(path).join('/').contains('/.grails/') &&
                        !roots[name].resolve('projects').relativize(path).join('/').contains('/.grails/baseline/files/') }.each { Path path ->
                    String text = new String(Files.readAllBytes(path), StandardCharsets.ISO_8859_1)
                    assert !text.contains(root): path
                    assert !text.contains('\r'): path
                    assert !text.contains('\\'): path
                    assert user.length() < 4 || !text.contains(user): path
                    assert !(text =~ /20\d\d-\d\d-\d\d/).find(): path
                }
            }
        }
    }

    private Map<String, String> environment(String name) {
        return roots[name].resolve('environment.txt').toFile().readLines('UTF-8').collectEntries { String line ->
            int at = line.indexOf('=')
            [line.substring(0, at), line.substring(at + 1)]
        }
    }

    // what each command line wrote, with the directory of the virtual machine taken out of it and its lines ended one way:
    // a command writes its lines to the output as the platform ends them
    private Map<String, String> output(String name) {
        String root = roots[name].toRealPath().toString()
        Map<String, String> texts = new TreeMap<>()
        roots[name].resolve('output').toFile().listFiles().each { File file ->
            texts.put(file.name, file.getText('UTF-8').replace(root, 'ROOT').replace('\r\n', '\n').replace('\r', '\n'))
        }
        return texts
    }

    private static Map<String, String> below(Map<String, String> tree, String scenario) {
        return tree.findAll { it.key.startsWith(scenario + '/') }.collectEntries { [it.key.substring(scenario.length() + 1), it.value] } as Map<String, String>
    }

    private static List<String> differing(Map<String, String> one, Map<String, String> other) {
        return new ArrayList<String>(one.keySet() + other.keySet()).findAll { one.get(it) != other.get(it) }.sort()
    }

    /**
     * Everything below a directory: every directory with its mode, and every file with the digest of
     * its content and its mode. The directory of the changes of an application is left out: an
     * application has it once a feature was added to it and not before.
     */
    private static Map<String, String> tree(Path root) {
        Map<String, String> entries = new TreeMap<>()
        boolean posix = root.fileSystem.supportedFileAttributeViews().contains('posix')
        Files.walk(root).withCloseable { stream ->
            stream.each { Path path ->
                String name = root.relativize(path).join('/')
                if (name.isEmpty() || name.endsWith('/.grails/transactions') || name.endsWith('/.grails/transactions/lock')) {
                    return
                }
                String mode = posix ? PosixFilePermissions.toString(Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS)) : ''
                if (Files.isSymbolicLink(path)) {
                    entries.put(name, "link to ${Files.readSymbolicLink(path)}".toString())
                } else if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    entries.put(name, "directory ${mode}".toString())
                } else {
                    entries.put(name, "${MessageDigest.getInstance('SHA-256').digest(Files.readAllBytes(path)).encodeHex()} ${mode}".toString())
                }
            }
        }
        return entries
    }

    // GenerationProbe in a virtual machine of its own, as ForkedCli starts the CLI
    private static ProcessBuilder probe(Path root, List<String> jvm) {
        List<String> command = [new File(System.getProperty('java.home'), 'bin/java').path, '-Xmx512m']
        command.addAll(jvm)
        File classPath = File.createTempFile('grails-cli-class-path', '.txt')
        classPath.deleteOnExit()
        classPath.setText('-cp\n"' + System.getProperty('java.class.path').replace('\\', '\\\\') + '"\n', 'UTF-8')
        command.add('@' + classPath.path)
        command.add(GenerationProbe.name)
        command.add(root.toString())
        ProcessBuilder builder = new ProcessBuilder(command).directory(root.toFile())
        builder.environment().remove('CLASSPATH')
        return builder
    }
}
