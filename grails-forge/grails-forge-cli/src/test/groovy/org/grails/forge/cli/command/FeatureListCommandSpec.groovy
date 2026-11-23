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
import java.security.MessageDigest
import org.grails.forge.cli.CliRunner
import org.grails.forge.cli.CommandLineCompleter
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import org.jline.reader.Candidate
import org.jline.reader.LineReader
import org.jline.reader.LineReaderBuilder
import org.jline.reader.Parser
import org.jline.reader.impl.DefaultParser
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import spock.lang.Unroll

/**
 * {@code feature list}: the features of the application in the current directory, as its record says
 * them, the features {@code feature add} adds to it and why it does not add the others; and the names
 * of features completed for {@code feature add}. What the list says of a feature is what
 * {@code feature add} does with it.
 */
class FeatureListCommandSpec extends CommandSpec {

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()
    File home
    String previousHome

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(err, true))
        // the journal of a change, which feature add looks for, is kept in the home directory of whoever runs it
        home = Files.createTempDirectory('grailsforgehome').toFile()
        previousHome = System.getProperty('user.home')
        System.setProperty('user.home', home.absolutePath)
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        System.setProperty('user.home', previousHome)
        home.deleteDir()
    }

    void "outside a project feature list says that it is a command of a project, and where the features of a new application are listed"() {
        when:
        int exit = ForgeCli.execute(beanContext, 'feature', 'list')

        then:
        exit == 1
        out.toString().isEmpty()
        err.toString().contains('feature list is a command of a Grails project; there is none in')
        err.toString().contains('The features an application is created with are listed by: grails-forge-cli create-app --list-features')
    }

    void "in an application feature list says what it has, what feature add adds to it, and why it adds none of the others"() {
        given:
        create(['--features', 'postgres,grails-quartz', '--exclude-features', 'mockito', '--data', 'hibernate5'])
        Map<String, String> before = tree()

        when:
        int exit = ForgeCli.execute(beanContext, 'feature', 'list')
        List<String> lines = out.toString().readLines()

        then:
        exit == 0
        err.toString().isEmpty()

        and: 'what the application has, and which of it was asked for by name'
        lines[0] ==~ /\| The application shop \(web\) has \d+ features, as its record in \.grails says:/
        entry(lines, 'asked for').substring('asked for'.length() + 1).split(', ') as Set == ['postgres', 'grails-quartz'] as Set
        entry(lines, 'with them').contains('asset-pipeline-grails')
        !entry(lines, 'with them').contains('postgres')

        and: 'what can be added, with what it takes the place of'
        int addable = lines.findIndexOf { it.contains('can be added to it with: grails-forge-cli feature add FEATURE') }
        int refused = lines.findIndexOf { it ==~ /\| \d+ features? of Grails for this type of application cannot be added to it:/ }
        addable > 0 && refused > addable
        entry(lines.subList(addable, refused), 'cache') != null
        entry(lines.subList(addable, refused), 'mysql').contains('with --replace only: it takes the place of postgres, which was asked for by name (group database-driver)')

        and: 'what cannot be added, each with the reason'
        entry(lines.subList(refused, lines.size()), 'mockito') != null
        entry(lines.subList(refused, lines.size()), 'gorm-hibernate7').contains('Adding a feature does not change the options the application was created with')

        and: 'what the application has is not among either'
        entry(lines.subList(addable, lines.size()), 'grails-quartz') == null
        entry(lines.subList(addable, lines.size()), 'postgres') == null

        and: 'nothing was written'
        tree() == before
    }

    @Unroll
    void "what feature list says of #feature is what feature add does with it: #verdict"() {
        given:
        create(['--features', 'postgres', '--exclude-features', 'mockito'])
        ForgeCli.execute(beanContext, 'feature', 'list')
        List<String> lines = out.toString().readLines()
        int refused = lines.findIndexOf { it.contains('cannot be added to it:') }
        boolean listedAddable = refused < 0 || lines.subList(0, refused).any { it.startsWith("    ${feature} ") }
        out.reset()
        err.reset()

        when:
        int exit = CliRunner.run(FeatureAddCommand, beanContext, ([feature, '--dry-run'] + options) as String[])

        then:
        listedAddable == (verdict == 'added')
        (exit == 0) == (verdict == 'added')

        where:
        feature                | options       | verdict
        'cache'                | []            | 'added'
        'grails-web-console'   | []            | 'added'
        'mysql'                | ['--replace'] | 'added'
        'mockito'              | []            | 'refused'
        'gorm-hibernate7'      | []            | 'refused'
        'gorm-mongodb'         | []            | 'added'
    }

    void "an application whose record feature add does not take: what it has is listed, and the list says why nothing can be added"() {
        given: 'an application whose record project migrate wrote'
        create([])
        new File(dir, '.grails').deleteDir()
        assert ForgeCli.execute(beanContext, 'project', 'migrate') == 0
        out.reset()

        when:
        int exit = ForgeCli.execute(beanContext, 'feature', 'list')
        String said = out.toString()

        then:
        exit == 0
        said.startsWith('| The application ')
        said.contains('| No feature is added to this application with feature add: the record was written by project migrate for an application that was there')
        !said.contains('can be added to it')
    }

    void "a feature pack the application was not generated with is refused, as feature add refuses it"() {
        given:
        create([])
        File pack = new File(home, 'pack')
        new File(pack, 'templates').mkdirs()
        new File(pack, 'feature.yml').setText(CreateAppFeaturePackSpec.DESCRIPTOR, 'UTF-8')
        new File(pack, 'templates/audit.txt').setText('kept as it is', 'UTF-8')

        when:
        int exit = ForgeCli.execute(beanContext, 'feature', 'list', '--feature-pack-dir', pack.absolutePath)

        then:
        exit == 1
        err.toString().contains('The feature pack acme-audit was given, and the application has no feature of it')
        !out.toString().contains('can be added to it')
    }

    void "feature add completes the names of the features the application does not have, and outside a project those of every type of application"() {
        given:
        Terminal terminal = TerminalBuilder.builder().type(Terminal.TYPE_DUMB).streams(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream()).build()

        when: 'outside a project'
        List<String> outside = complete(terminal, 'feature add ')

        then:
        outside.containsAll(['cache', 'grails-quartz', 'postgres', 'mockito'])

        when: 'in an application that has some of them'
        create(['--features', 'grails-quartz'])
        List<String> inside = complete(terminal, 'feature add ')

        then:
        inside.contains('cache')
        inside.contains('postgres')
        !inside.contains('grails-quartz')
        !inside.contains('asset-pipeline-grails')

        cleanup:
        terminal?.close()
    }

    private void create(List<String> arguments) {
        assert CliRunner.run(CreateAppCommand, beanContext, (['com.example.shop', '--inplace'] + arguments) as String[]) == 0
        out.reset()
        err.reset()
    }

    // what the list has of a feature: its line and the lines below it that belong to it, or null when it has none
    private static String entry(List<String> lines, String name) {
        int at = lines.findIndexOf { it.startsWith("    ${name} ") }
        if (at < 0) {
            return null
        }
        List<String> entry = [lines[at].trim()]
        for (int i = at + 1; i < lines.size() && lines[i].startsWith('    ') && lines[i].charAt(4) == (' ' as char); i++) {
            entry << lines[i]
        }
        return entry.join('\n').replaceAll(/\s+/, ' ')
    }

    private List<String> complete(Terminal terminal, String typed) {
        LineReader reader = LineReaderBuilder.builder().terminal(terminal).build()
        List<Candidate> candidates = []
        new CommandLineCompleter(ForgeCli.createCommandLine(beanContext, true).commandSpec)
                // as the prompt asks on the TAB key: a word that is begun after the last space
                .complete(reader, new DefaultParser().parse(typed, typed.length(), Parser.ParseContext.COMPLETE), candidates)
        return candidates*.value()
    }

    private Map<String, String> tree() {
        Map<String, String> entries = new TreeMap<>()
        Files.walk(dir.toPath()).withCloseable { stream ->
            stream.each { java.nio.file.Path path ->
                entries.put(dir.toPath().relativize(path).join('/'), Files.isDirectory(path) ? 'directory' :
                        MessageDigest.getInstance('SHA-256').digest(Files.readAllBytes(path)).encodeHex().toString())
            }
        }
        return entries
    }
}
