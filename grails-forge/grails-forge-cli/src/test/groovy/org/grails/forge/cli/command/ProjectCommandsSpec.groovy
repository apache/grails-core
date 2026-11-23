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
import java.nio.file.LinkOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import groovy.json.JsonSlurper
import org.apache.grails.forge.core.state.ProjectLock
import org.apache.grails.forge.core.state.ProjectState
import org.apache.grails.forge.core.state.StateLimits
import org.grails.forge.cli.CommandLineCompleter
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import org.grails.forge.util.VersionInfo
import org.jline.reader.Candidate
import org.jline.reader.impl.DefaultParser
import spock.lang.IgnoreIf
import spock.lang.Unroll

/**
 * {@code project show} and {@code project regenerate}: what the record of an application says of how
 * it was generated, and the application generated again from it.
 */
class ProjectCommandsSpec extends CommandSpec {

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()

    /** Where an application is created or written a second time, to compare with */
    File other

    File home
    String previousHome

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out))
        System.setErr(new PrintStream(err))
        other = Files.createTempDirectory('grailsforgeother').toFile()
        // where a change of a project that was interrupted is looked for
        home = Files.createTempDirectory('grailsforgehome').toFile()
        previousHome = System.getProperty('user.home')
        System.setProperty('user.home', home.absolutePath)
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        System.setProperty('user.home', previousHome)
        other.deleteDir()
        home.deleteDir()
    }

    @Unroll
    void "the command line project show prints creates the application of #command #options again, with the same record"() {
        given:
        create(dir, [command, 'com.example.shop'] + options)

        when:
        int exit = run('project', 'show')
        List<String> printed = words(out.toString().readLines().last())

        then:
        exit == 0
        err.toString().isEmpty()
        printed == ['grails-forge-cli', command == 'create-webapp' ? 'create-app' : command, 'com.example.shop'] + expected

        when: 'the printed command line is run in another directory'
        int again = create(other, printed.drop(1))

        then: 'the two applications are the same, the record under .grails among their files'
        again == 0
        tree(new File(other, 'shop')) == tree(new File(dir, 'shop'))
        tree(new File(dir, 'shop')).keySet().containsAll(['.grails/project.yml', '.grails/forge.lock.yml', '.grails/baseline/index.yml'])

        where:
        command             | options                                                                                 || expected
        'create-app'        | []                                                                                      || []
        'create-app'        | ['--jdk', '25', '--servlet', 'undertow', '--data', 'mongodb', '--reloading', 'none',
                               '--features', 'grails-quartz,asciidoctor', '--exclude-features', 'mockito,scaffolding'] || ['--jdk', '25', '--servlet', 'undertow', '--data', 'mongodb', '--reloading', 'none', '--features', 'asciidoctor,grails-quartz', '--exclude-features', 'mockito,scaffolding']
        'create-app'        | ['--servlet', 'tomcat', '--data', 'hibernate7', '--features', 'grails-web-console']      || ['--data', 'hibernate7', '--features', 'grails-web-console']
        'create-webapp'     | ['-s', 'jetty', '-f', 'cache']                                                           || ['--servlet', 'jetty', '--features', 'cache']
        'create-restapi'    | ['--exclude-features', 'mockito', '--features', 'openapi']                              || ['--features', 'openapi', '--exclude-features', 'mockito']
        'create-plugin'     | ['--features', 'asciidoctor']                                                           || ['--servlet', 'none', '--features', 'asciidoctor']
        'create-web-plugin' | ['--jdk', '25', '--features', 'grails-quartz']                                          || ['--jdk', '25', '--features', 'grails-quartz']
        'create-rest-api-plugin' | ['--data', 'hibernate7', '--features', 'asciidoctor', '--exclude-features', 'mockito'] || ['--data', 'hibernate7', '--features', 'asciidoctor', '--exclude-features', 'mockito']
    }

    void "project show says what the record has, the features in the order they are applied, and the command line last"() {
        given:
        create(dir, ['create-app', 'com.example.shop', '--data', 'hibernate7', '--features', 'grails-quartz', '--exclude-features', 'mockito'])
        ProjectState state = ProjectState.read(new File(dir, 'shop').toPath())

        when:
        int exit = run('project', 'show')
        List<String> lines = out.toString().readLines()

        then:
        exit == 0
        err.toString().isEmpty()
        lines.first() == '| How the application shop was generated, from its record in .grails'
        lines.containsAll(['    type          web', '    name          shop', '    package       com.example', "    Grails        ${VersionInfo.grailsVersion}".toString(),
                           "    generated by  grails-forge ${VersionInfo.version}".toString(), '    state         tracked', '    jdk           21',
                           '    servlet       tomcat', '    data          hibernate7', '    reloading     devtools', '    asked for     grails-quartz',
                           '    left out      mockito', "| The ${state.lock.features.size()} features it has, in the order they are applied".toString()])
        lines.findAll { it ==~ /    \S+ \((framework|bundled-pack)\)/ }.collect { it.trim().split(' ').first() } == state.lock.featureNames
        lines.contains('| The application is created again with:')
        lines[-1] == 'grails-forge-cli create-app com.example.shop --data hibernate7 --features grails-quartz --exclude-features mockito'

        when: 'from a directory of the application'
        out.reset()
        System.setProperty('user.dir', new File(dir, 'shop/grails-app/conf').absolutePath)
        exit = ForgeCli.execute(beanContext, 'project', 'show')

        then: 'the same is said'
        exit == 0
        out.toString().readLines() == lines
    }

    void "project show --json is one document with the record and the command, its keys in one order"() {
        given:
        create(dir, ['create-app', 'com.example.shop', '--jdk', '25', '--features', 'grails-quartz', '--exclude-features', 'mockito'])
        ProjectState state = ProjectState.read(new File(dir, 'shop').toPath())

        when:
        int exit = run('project', 'show', '--json')
        Map document = (Map) new JsonSlurper().parseText(out.toString())

        then:
        exit == 0
        err.toString().isEmpty()
        out.toString().readLines().size() == 1
        out.toString().startsWith('{"format":1,"applicationType":"web","name":"shop","defaultPackage":"com.example","grailsVersion":')
        document.keySet() as List == ['format', 'applicationType', 'name', 'defaultPackage', 'grailsVersion', 'generator', 'templateApi', 'generationState', 'options',
                                      'requestedFeatures', 'excludedFeatures', 'repositories', 'repositoriesNotNamed', 'features', 'command', 'notes',
                                      'effectiveOptions', 'addedFeatures']
        document.generator == [name: 'grails-forge', version: VersionInfo.version, sha256: VersionInfo.generatorSha256]
        document.generationState == 'tracked'
        document.options == [jdk: 25, servlet: 'tomcat', data: 'hibernate5', reloading: 'devtools']
        document.requestedFeatures == [[name: 'grails-quartz']]
        document.excludedFeatures == ['mockito']
        document.repositories == state.lock.repositories.collect { it.url == null ? [kind: it.kind] : [kind: it.kind, url: it.url] }
        document.repositoriesNotNamed == 0
        document.features*.name == state.lock.featureNames
        document.features.every { it.keySet() as List == ['name', 'source'] }
        document.command == [name: 'create-app', arguments: ['com.example.shop', '--jdk', '25', '--features', 'grails-quartz', '--exclude-features', 'mockito'],
                             line: 'grails-forge-cli create-app com.example.shop --jdk 25 --features grails-quartz --exclude-features mockito']
        document.notes == ofRepositories()

        and: 'every option with its value, the default of this command line, and how the two compare'
        document.format == 1
        document.effectiveOptions == [
                [name: 'application-type', value: 'web', status: 'recorded'],
                [name: 'name', value: 'shop', status: 'recorded'],
                [name: 'package', value: 'com.example', default: 'shop', status: 'differs'],
                [name: 'grails-version', value: VersionInfo.grailsVersion, status: 'recorded'],
                [name: 'jdk', option: '--jdk', value: 25, default: 21, status: 'differs'],
                [name: 'servlet', option: '--servlet', value: 'tomcat', default: 'tomcat', status: 'default'],
                [name: 'data', option: '--data', value: 'hibernate5', default: 'hibernate5', status: 'default'],
                [name: 'reloading', option: '--reloading', value: 'devtools', default: 'devtools', status: 'default'],
                [name: 'subproject', option: '--subproject', value: false, default: false, status: 'default'],
                [name: 'features', option: '--features', value: ['grails-quartz'], default: [], status: 'differs'],
                [name: 'exclude-features', option: '--exclude-features', value: ['mockito'], default: [], status: 'differs'],
                [name: 'feature-pack-dir', option: '--feature-pack-dir', value: [], default: [], status: 'default'],
                [name: 'feature-pack-repository', option: '--feature-pack-repository', value: [], default: [], status: 'default'],
                [name: 'feature-pack-sha256', option: '--feature-pack-sha256', value: [], default: [], status: 'default'],
                [name: 'feature-catalog', option: '--feature-catalog', value: [], default: [], status: 'default'],
                [name: 'preset', option: '--preset', value: [], default: [], status: 'default'],
                [name: 'test-framework', value: 'spock', default: 'spock', status: 'not-recorded'],
                [name: 'build-tool', value: 'gradle', default: 'gradle', status: 'not-recorded']]
        document.addedFeatures == state.lock.featureNames - 'grails-quartz'
        !document.addedFeatures.isEmpty()

        when: 'asked again'
        String first = out.toString()
        out.reset()
        run('project', 'show', '--json')

        then:
        out.toString() == first
    }

    void "project show lists every option with its value, the ones that were not given too, and says which are the default of this command line"() {
        given:
        create(dir, ['create-app', 'com.example.shop', '--features', 'grails-quartz'])
        ProjectState state = ProjectState.read(new File(dir, 'shop').toPath())

        when:
        int exit = run('project', 'show')
        List<String> lines = out.toString().readLines()
        int from = lines.indexOf("| Every option of create-app with the value the application was generated with, and whether that is the default of this version " +
                "(${VersionInfo.version}) of the command line".toString())
        List<List<String>> rows = lines.subList(from + 1, from + 19).collect { String line -> line.trim().split(/ {2,}/).toList() }

        then:
        exit == 0
        err.toString().isEmpty()
        from > 0
        rows == [['application type', 'web'],
                 ['name', 'shop'],
                 ['package', 'com.example', '(differs from the current default: shop)'],
                 ['Grails version', VersionInfo.grailsVersion],
                 ['--jdk', '21', '(default)'],
                 ['--servlet', 'tomcat', '(default)'],
                 ['--data', 'hibernate5', '(default)'],
                 ['--reloading', 'devtools', '(default)'],
                 ['--subproject', 'no', '(default)'],
                 ['--features', 'grails-quartz', '(differs from the current default: (none))'],
                 ['--exclude-features', '(none)', '(default)'],
                 ['--feature-pack-dir', '(none)', '(default)'],
                 ['--feature-pack-repository', '(none)', '(default)'],
                 ['--feature-pack-sha256', '(none)', '(default)'],
                 ['--feature-catalog', '(none)', '(default)'],
                 ['--preset', '(none)', '(default)'],
                 ['test framework', 'spock', '(not recorded: the only one this version generates)'],
                 ['build tool', 'gradle', '(not recorded: the only one this version generates)']]

        and: 'the features that were asked for apart from those that came with them'
        lines[from + 19] == "| Of the ${state.lock.features.size()} features, 1 was asked for (--features above) and ${state.lock.features.size() - 1} came with the " +
                'application type, with an option or with another feature:'
        lines[from + 20].trim().split(', ').toList() == state.lock.featureNames - 'grails-quartz'

        and: 'the command line is the short one, and last'
        lines[-1] == 'grails-forge-cli create-app com.example.shop --features grails-quartz'
    }

    void "an option a create command has that project show knows nothing of is shown as one the record does not have"() {
        given:
        create(dir, ['create-app', 'com.example.shop'])
        ProjectState state = ProjectState.read(new File(dir, 'shop').toPath())

        when: 'the options of a create command of a later version, one of them new and one that says nothing of the application'
        List<ProjectShowCommand.Setting> settings = ProjectShowCommand.settings(state, ['--jdk', '--zeta', '--alpha-option', '--offline'])

        then: 'the known one has its value, the new ones follow in the order of their names without one, and the one that says nothing is left out'
        settings*.name == ['application-type', 'name', 'package', 'grails-version', 'jdk', 'alpha-option', 'zeta', 'test-framework', 'build-tool']
        settings.find { it.name == 'zeta' }.toMap() == [name: 'zeta', option: '--zeta', status: 'not-recorded']
        settings.find { it.name == 'zeta' }.remark() == 'not recorded'
        settings.find { it.name == 'zeta' }.label() == '--zeta'
    }

    void "an option of a create command is shown by project show, or is one that says nothing of the application"() {
        given:
        create(dir, [command, 'com.example.shop'])
        List<String> options = ForgeCli.createCommandLine(beanContext, true).getSubcommands().get(command).getCommandSpec().options().collect { ProjectShowCommand.nameOf(it.names().toList()) }

        when:
        run('project', 'show', '--json')
        Map document = (Map) new JsonSlurper().parseText(out.toString())

        then: 'a new option is added to one of the two lists of the command, with what the record has for it'
        options.findAll { !(it in ProjectShowCommand.SHOWN_OPTIONS) && !(it in ProjectShowCommand.OPTIONS_NOT_OF_THE_APPLICATION) } == []
        ProjectShowCommand.SHOWN_OPTIONS.every { it in options }
        ProjectShowCommand.SHOWN_OPTIONS.intersect(ProjectShowCommand.OPTIONS_NOT_OF_THE_APPLICATION).isEmpty()

        and: 'each of them is in the document, none without a status'
        document.effectiveOptions.findAll { it.option != null }*.option == ProjectShowCommand.SHOWN_OPTIONS
        document.effectiveOptions.every { it.status in ['default', 'differs', 'recorded', 'not-recorded'] }

        where:
        command << ['create-app', 'create-restapi', 'create-web-plugin', 'create-plugin']
    }

    void "a value that is not the default of this command line is said to differ and is on the command line, whoever chose it"() {
        given: 'a record with another Grails Data implementation than a create command of this version takes: that of an application created when it was the default'
        create(dir, ['create-app', 'com.example.shop', '--data', 'hibernate7'])

        when:
        int exit = run('project', 'show')
        List<String> lines = out.toString().readLines()

        then:
        exit == 0
        lines.any { it ==~ / +--data +hibernate7 +\(differs from the current default: hibernate5\)/ }
        lines.any { it ==~ / +--servlet +tomcat +\(default\)/ }
        lines[-1] == 'grails-forge-cli create-app com.example.shop --data hibernate7'

        when:
        out.reset()
        run('project', 'show', '--json')
        Map document = (Map) new JsonSlurper().parseText(out.toString())

        then:
        document.effectiveOptions.find { it.name == 'data' } == [name: 'data', option: '--data', value: 'hibernate7', default: 'hibernate5', status: 'differs']
        document.command.arguments == ['com.example.shop', '--data', 'hibernate7']

        when: 'the printed command line is run in another directory'
        int again = create(other, words(lines[-1]).drop(1))

        then: 'the application is the one that was generated, not one of the defaults of today'
        again == 0
        tree(new File(other, 'shop')) == tree(new File(dir, 'shop'))
    }

    void "a record that lacks an option is shown with the default of this command line for it, marked as not recorded"() {
        given: 'a record as a build that did not write the option left it'
        create(dir, ['create-app', 'com.example.shop', '--servlet', 'jetty'])
        File project = new File(dir, 'shop')
        withoutOption(project, 'reloading')

        when:
        int exit = run('project', 'show')
        List<String> lines = out.toString().readLines()

        then:
        exit == 0
        err.toString().isEmpty()
        lines.any { it ==~ / +--reloading +devtools +\(not recorded: the current default\)/ }
        lines.any { it ==~ / +--servlet +jetty +\(differs from the current default: tomcat\)/ }
        lines.contains('| The record does not have the option --reloading: the command takes the default of this version of the command line for it, ' +
                'which need not be what the application was generated with')
        lines[-1] == 'grails-forge-cli create-app com.example.shop --servlet jetty'

        when:
        out.reset()
        exit = run('project', 'show', '--json')
        Map document = (Map) new JsonSlurper().parseText(out.toString())

        then:
        exit == 0
        document.effectiveOptions.find { it.name == 'reloading' } == [name: 'reloading', option: '--reloading', value: 'devtools', default: 'devtools', status: 'not-recorded']
        document.options == [jdk: 21, servlet: 'jetty', data: 'hibernate5', reloading: 'devtools']
        document.notes.first().startsWith('The record does not have the option --reloading')

        when: 'what changes the application reads the record as before'
        err.reset()
        exit = run('project', 'regenerate', '--diff')

        then:
        exit == 1
        err.toString().contains('.grails/project.yml: options.reloading')
    }

    void "the plugin of a feature pack is named by its coordinates with the SHA-256 of its jar, and the command line gives the same application"() {
        given:
        File repository = new File(other, 'repository')
        File jar = publish(repository, 'com.acme:acme-audit-grails:2.3.0')
        String address = repository.toPath().toAbsolutePath().normalize().toUri().toString()
        create(dir, ['create-app', 'com.example.shop', '--features', 'h2,com.acme:acme-audit-grails:2.3.0', '--feature-pack-repository', address])
        out.reset()

        when:
        int exit = run('project', 'show')
        List<String> lines = out.toString().readLines()

        then:
        exit == 0
        lines.contains('    asked for     acme-audit (com.acme:acme-audit-grails:2.3.0), h2')
        lines.contains("    acme-audit (pack, com.acme:acme-audit-grails:2.3.0, sha256 ${sha(jar.bytes)})".toString())
        lines.contains('| The record does not name the repository com.acme:acme-audit-grails:2.3.0 was taken from: give it to the command with --feature-pack-repository, ' +
                'unless it is one of those the command asks anyway')
        lines.contains('| The feature acme-audit is that of a feature pack that is not part of Grails: feature add and project regenerate work for the application ' +
                'only with that very pack, which its record names by its SHA-256 (the jar of a plugin is fetched again by its coordinates; the pack of a directory is ' +
                'given again with --feature-pack-dir)')
        lines.findAll { it.startsWith('| ') }.size() == 7 + ofRepositories().size()
        words(lines[-1]) == ['grails-forge-cli', 'create-app', 'com.example.shop', '--features', 'com.acme:acme-audit-grails:2.3.0,h2',
                             '--feature-pack-sha256', "com.acme:acme-audit-grails:2.3.0=${sha(jar.bytes)}".toString()]

        and: 'the directory the jar came from is nowhere in what is said'
        !out.toString().contains(other.name)

        when: 'the command line is run with the repository, in another directory'
        File again = new File(other, 'again')
        again.mkdirs()
        int created = create(again, words(lines[-1]).drop(1) + ['--feature-pack-repository', address])

        then:
        created == 0
        tree(new File(again, 'shop')).findAll { it.key.startsWith('.grails/') } == tree(new File(dir, 'shop')).findAll { it.key.startsWith('.grails/') }

        when: 'the application is generated again, with the jar of its record from the repository'
        err.reset()
        out.reset()
        exit = run('project', 'regenerate', '--diff', '--feature-pack-repository', address)

        then: 'it is the application as it was generated'
        exit == 0
        out.toString().contains("The feature pack acme-audit of the application is fetched again as com.acme:acme-audit-grails:2.3.0, with the SHA-256 ${sha(jar.bytes)} its record has")
    }

    void "a feature pack that was given as a directory is said to be needed with the command line"() {
        given:
        File pack = new File(other, 'pack')
        new File(pack, 'templates').mkdirs()
        new File(pack, 'feature.yml').setText(DESCRIPTOR, 'UTF-8')
        new File(pack, 'templates/audit.txt').setText('kept as it is', 'UTF-8')
        create(dir, ['create-app', 'com.example.shop', '--features', 'acme-audit', '--feature-pack-dir', pack.absolutePath])
        out.reset()

        when:
        int exit = run('project', 'show', '--json')
        Map document = (Map) new JsonSlurper().parseText(out.toString())

        then:
        exit == 0
        document.command.arguments == ['com.example.shop', '--features', 'acme-audit']
        document.notes == ['The feature acme-audit is that of a feature pack given as a directory or a jar, which the record does not name: ' +
                                   'give it to the command with --feature-pack-dir',
                           'The feature acme-audit is that of a feature pack that is not part of Grails: feature add and project regenerate work for the ' +
                                   'application only with that very pack, which its record names by its SHA-256 (the jar of a plugin is fetched again by its ' +
                                   'coordinates; the pack of a directory is given again with --feature-pack-dir)'] + ofRepositories()
        document.features.find { it.name == 'acme-audit' }.subMap(['name', 'source']) == [name: 'acme-audit', source: 'pack']
        document.features.find { it.name == 'acme-audit' }.contentSha256 ==~ /[0-9a-f]{64}/
        document.effectiveOptions.find { it.name == 'feature-pack-dir' } == [name: 'feature-pack-dir', option: '--feature-pack-dir', default: [], status: 'not-recorded']
        !out.toString().contains(other.name)
    }

    void "an argument a shell would act on is put between single quotes, and what a terminal acts on is not written"() {
        expect:
        ProjectShowCommand.commandLine('grails', arguments) == line
        words(line) == read

        where:
        arguments                               || line                                            | read
        ['create-app', 'com.example.shop']      || 'grails create-app com.example.shop'            | ['grails', 'create-app', 'com.example.shop']
        ['a b', '$(rm -rf x)', '`id`', 'x;y']   || "grails 'a b' '\$(rm -rf x)' '`id`' 'x;y'"      | ['grails', 'a b', '$(rm -rf x)', '`id`', 'x;y']
        ["it's", '"q"', 'a\\b', '*', '~', '']   || "grails 'it'\\''s' '\"q\"' 'a\\b' '*' '~' ''"   | ['grails', "it's", '"q"', 'a\\b', '*', '~', '']
        ['a\nb', 'c\u001b[31md', '@|red x|@']   || "grails 'a?b' 'c?[31md' '@|red x|@'"            | ['grails', 'a?b', 'c?[31md', '@|red x|@']
    }

    @Unroll
    void "#command outside a Grails project says that there is none"() {
        when:
        int exit = ForgeCli.execute(beanContext, command as String[])

        then:
        exit == 1
        err.toString().readLines() == ["| Error ${command.take(2).join(' ')} is a command of a Grails project; there is none in ${dir.canonicalPath} or above it".toString()]
        out.toString().isEmpty()
        dir.list() as List == []

        where:
        command << [['project', 'show'], ['project', 'show', '--json'], ['project', 'regenerate', '--diff'], ['project', 'regenerate', '--output', 'again']]
    }

    @Unroll
    void "#command in a project without the record of a generated application says that it is missing"() {
        given:
        new File(dir, 'grails-app').mkdirs()
        new File(dir, 'build.gradle').text = '// an application of another making\n'

        when:
        int exit = ForgeCli.execute(beanContext, command as String[])

        then:
        exit == 1
        err.toString().contains('There is no state of a generated project here: .grails/project.yml is missing. For an application that was not generated ' +
                'by this version, grails-forge-cli project migrate writes the record')
        out.toString().isEmpty()
        !new File(dir, 'again').exists()

        where:
        command << [['project', 'show'], ['project', 'regenerate', '--diff'], ['project', 'regenerate', '--output', 'again']]
    }

    @Unroll
    void "an application whose record #what is not shown and not generated again"() {
        given:
        create(dir, ['create-app', 'com.example.shop'])
        File project = new File(dir, 'shop')
        change.call(project)
        Map<String, String> before = tree(project)
        out.reset()

        when:
        int shown = run('project', 'show')

        then:
        shown == (show == null ? 0 : 1)
        show == null || err.toString().contains(show)

        when:
        err.reset()
        int diff = run('project', 'regenerate', '--diff')
        String said = err.toString()
        err.reset()
        int written = run('project', 'regenerate', '--output', new File(other, 'again').absolutePath)

        then: 'in the words feature add has for it'
        diff == 1
        written == 1
        said.contains(regenerate)
        err.toString().contains(regenerate)
        !new File(other, 'again').exists()
        tree(project) == before

        where:
        what                               | change                                                                                   || show                                                                         | regenerate
        'has a lock that was edited'       | { File it -> File lock = new File(it, '.grails/forge.lock.yml'); lock.text = lock.text.replace('grails-web', 'grails-wex') } || '.grails/forge.lock.yml' | '.grails/forge.lock.yml'
        'has no lock'                      | { File it -> new File(it, '.grails/forge.lock.yml').delete() }                           || 'The state of the project is not complete: .grails/forge.lock.yml is missing' | 'The state of the project is not complete: .grails/forge.lock.yml is missing'
        'has a manifest that was edited'   | { File it -> new File(it, '.grails/project.yml') << 'requestedFeatures: []\n' }          || '.grails/project.yml'                                                        | '.grails/project.yml'
        'lacks a file of the baseline'     | { File it -> new File(it, '.grails/baseline/files/build.gradle').delete() }              || null                                                                         | '.grails/baseline/files/build.gradle is missing from the baseline'
        'has a baseline that was edited'   | { File it -> new File(it, '.grails/baseline/files/build.gradle') << '// edited\n' }      || null                                                                         | '.grails/baseline/files/build.gradle is not what the generator wrote'
        'has another file in its baseline' | { File it -> new File(it, '.grails/baseline/files/extra.txt').text = 'x' }               || null                                                                         | '.grails/baseline/files/extra.txt is not a file of the baseline index'
    }

    void "an application that another version generated is shown with a note, and is generated again once project migrate moved its record"() {
        given:
        create(dir, ['create-app', 'com.example.shop'])
        File project = new File(dir, 'shop')
        ProjectLock lock = ProjectState.read(project.toPath()).lock
        new File(project, '.grails/forge.lock.yml').bytes = new ProjectLock(lock.manifestSha256, lock.generatorName, '9.0.0-M1', lock.grailsVersion, lock.templateApi,
                lock.applicationType, lock.options, lock.lineEndings, lock.repositories, lock.repositoriesNotNamed, lock.features, lock.parameters,
                lock.baselineIndexSha256).toBytes()
        out.reset()

        when:
        int exit = run('project', 'show')

        then:
        exit == 0
        out.toString().readLines().contains(("| The application was generated by version 9.0.0-M1 of the command line, and this is ${VersionInfo.version}: the command is " +
                'for that version; grails-forge-cli project migrate moves the record to this version, from the choices it has, and changes no file of the application ' +
                'outside .grails').toString())
        out.toString().readLines()[-1] == 'grails-forge-cli create-app com.example.shop'

        when:
        exit = run('project', 'regenerate', '--diff')

        then:
        exit == 1
        err.toString().contains("The application was generated by grails-forge 9.0.0-M1 for Grails ${VersionInfo.grailsVersion}, and this is grails-forge ${VersionInfo.version} " +
                "for Grails ${VersionInfo.grailsVersion}")
        err.toString().contains('Nothing was changed. grails-forge-cli project migrate moves the record to this version')
    }

    void "project regenerate --output writes the application as it was generated, whatever was done to its files since"() {
        given:
        List<String> options = ['create-app', 'com.example.shop', '--servlet', 'jetty', '--features', 'grails-quartz', '--exclude-features', 'mockito']
        create(dir, options)
        create(other, options)
        File project = new File(dir, 'shop')
        new File(project, 'build.gradle') << '// ours\n'
        new File(project, 'grails-app/conf/application.yml').delete()
        new File(project, 'src/main/groovy/com/example').mkdirs()
        new File(project, 'src/main/groovy/com/example/Mine.groovy').text = 'package com.example\n\nclass Mine {}\n'
        Map<String, String> before = tree(project)
        File output = new File(dir.canonicalFile, 'again')
        out.reset()

        when: 'the directory is named as it is typed, from a directory of the application'
        System.setProperty('user.dir', new File(project, 'grails-app').absolutePath)
        int exit = ForgeCli.execute(beanContext, 'project', 'regenerate', '--output', '../../again')

        then:
        exit == 0
        err.toString().isEmpty()
        out.toString().readLines() == ["| Application generated again at ${output.canonicalPath}: ${tree(output).count { !it.value.startsWith('directory') }} files, " +
                                               'and its record under .grails is that of the project']

        and: 'every file, with its mode, is that of the application as it was created, the record among them'
        tree(output) == tree(new File(other, 'shop'))

        and: 'the project is as it was'
        tree(project) == before
    }

    void "a directory that has files is written to with --force only, and the directory of the project never"() {
        given:
        create(dir, ['create-app', 'com.example.shop'])
        create(other, ['create-app', 'com.example.shop'])
        File project = new File(dir, 'shop')
        File output = new File(dir.canonicalFile, 'again')
        output.mkdirs()
        Map<String, String> before = tree(project)

        expect: 'an empty directory is written to'
        run('project', 'regenerate', '--output', output.absolutePath) == 0
        tree(output) == tree(new File(other, 'shop'))

        when: 'it has files now'
        new File(output, 'build.gradle').text = '// changed\n'
        new File(output, 'mine.txt').text = 'mine'
        Map<String, String> has = tree(output)
        int exit = run('project', 'regenerate', '--output', output.absolutePath)

        then:
        exit == 1
        err.toString().readLines() == ["| Error ${output.canonicalPath} already exists and is not empty. Run again with -f to overwrite.".toString()]
        tree(output) == has

        when:
        err.reset()
        exit = run('project', 'regenerate', '--output', output.absolutePath, '--force')

        then: 'the files of the application are replaced, and what else is there stays'
        exit == 0
        err.toString().isEmpty()
        tree(output).findAll { it.key != 'mine.txt' } == tree(new File(other, 'shop'))
        new File(output, 'mine.txt').text == 'mine'

        when: 'the record there has a file the project has not'
        new File(output, '.grails/baseline/files/extra.txt').text = 'x'
        exit = run('project', 'regenerate', '--output', output.absolutePath, '-f')

        then: 'the first file that differs is named'
        exit == 1
        err.toString().readLines() == ["| Error The application was written to ${output.canonicalPath}, and its record is not that of the project: " +
                                               '.grails/baseline/files/extra.txt is a file of the directory that was written to only']

        when:
        err.reset()
        exit = run('project', 'regenerate', '--output', target, '--force')

        then:
        exit == 1
        err.toString().contains('is the directory of the project or one above it: the application is generated again into another directory, and the project is not changed')
        tree(project) == before

        where:
        target << ['.', '..', 'grails-app/..']
    }

    void "a file that is in the way of the directory, and a link in the directory, are refused"() {
        given:
        create(dir, ['create-app', 'com.example.shop'])
        File file = new File(dir.canonicalFile, 'file.txt')
        file.text = 'a file'
        File output = new File(dir.canonicalFile, 'again')
        File elsewhere = new File(other, 'elsewhere')
        elsewhere.mkdirs()
        output.mkdirs()
        Files.createSymbolicLink(new File(output, 'grails-app').toPath(), elsewhere.toPath())

        when:
        int exit = run('project', 'regenerate', '--output', file.absolutePath, '--force')

        then:
        exit == 1
        err.toString().readLines() == ["| Error ${file.canonicalPath} is not a directory, so the application is not written there".toString()]
        file.text == 'a file'

        when:
        err.reset()
        exit = run('project', 'regenerate', '--output', output.absolutePath, '--force')

        then: 'nothing is written, through the link or beside it'
        exit == 1
        err.toString().contains('grails-app is a link, and a file of the project is not written through a link')
        output.list() as List == ['grails-app']
        elsewhere.list() as List == []
    }

    @Unroll
    void "project regenerate #arguments is a mistake in the command line"() {
        given:
        create(dir, ['create-app', 'com.example.shop'])

        when:
        int exit = run((['project', 'regenerate'] + arguments) as String[])

        then:
        exit == 2
        err.toString().contains(message)
        !new File(dir, 'again').exists()

        where:
        arguments                                  || message
        []                                         || 'Give --output with the directory to write the application to, or --diff'
        ['--diff', '--output', '../again']         || 'Give --output with the directory to write the application to, or --diff'
        ['--diff', '--force']                      || '--force is for --output: --diff writes nothing'
    }

    void "project regenerate --diff names the files that were added, removed and changed, and none of those the application ignores"() {
        given:
        create(dir, ['create-app', 'com.example.shop', '--features', 'grails-quartz'])
        File project = new File(dir, 'shop')
        out.reset()

        expect: 'an application as it was generated has none'
        run('project', 'regenerate', '--diff') == 0
        out.toString().readLines() == ['| Every file of the application is as it was generated, and it has no others']
        err.toString().isEmpty()

        when:
        new File(project, 'build.gradle') << '// ours\n'
        new File(project, 'grails-app/conf/application.yml').delete()
        new File(project, 'gradlew').delete()
        new File(project, 'src/main/groovy/com/example').mkdirs()
        new File(project, 'src/main/groovy/com/example/Mine.groovy').text = 'package com.example\n\nclass Mine {}\n'
        new File(project, 'src/main/groovy/com/example/build').mkdirs()
        new File(project, 'src/main/groovy/com/example/build/Kept.groovy').text = 'package com.example.build\n'
        new File(project, 'notes.txt').text = 'notes'
        // what the .gitignore of the application leaves out, and the repository
        ['build/libs/shop.jar', '.gradle/8/x.bin', '.idea/workspace.xml', 'shop.iml', 'out/production/x.class', 'sub/build/y.class', '.git/config', '.git/objects/ab/cd',
         '.grails/cache/commands.json', '.grails/run/pid', '.grails/history', '.DS_Store', 'grails-app/.DS_Store', 'dist/x', 'target/y'].each { String path ->
            File file = new File(project, path)
            file.parentFile.mkdirs()
            file.text = 'x'
        }
        // a rule the application has since does not count: its file is one that changed
        new File(project, '.gitignore') << 'notes.txt\n'
        Map<String, String> before = tree(project)
        out.reset()
        int exit = run('project', 'regenerate', '--diff')

        then:
        exit == 1
        err.toString().isEmpty()
        out.toString().readLines() == ['changed  .gitignore',
                                       'changed  build.gradle',
                                       'removed  gradlew',
                                       'removed  grails-app/conf/application.yml',
                                       'added    notes.txt',
                                       'added    src/main/groovy/com/example/Mine.groovy',
                                       'added    src/main/groovy/com/example/build/Kept.groovy',
                                       '| 7 files are not as the application was generated: 3 added, 2 removed, 2 changed']

        and: 'nothing was written'
        tree(project) == before
    }

    void "a clone with the line endings of Windows, and a file of the record that was changed, are told apart"() {
        given:
        create(dir, ['create-app', 'com.example.shop'])
        File project = new File(dir, 'shop')
        ProjectState state = ProjectState.read(project.toPath())
        for (String path : state.baseline.files.findAll { !it.binary }*.path) {
            File file = new File(project, path)
            file.bytes = file.getText('UTF-8').replace('\n', '\r\n').getBytes('UTF-8')
        }
        out.reset()

        expect: 'the text files are as generated with either line ending'
        run('project', 'regenerate', '--diff') == 0

        when: 'a file beside the baseline, which is no file of the application, is another'
        new File(project, '.grails/.gitattributes') << '# ours\n'
        out.reset()
        int exit = run('project', 'regenerate', '--diff')

        then:
        exit == 1
        out.toString().readLines().first() == 'changed  .grails/.gitattributes'

        when: 'and the application is written again'
        exit = run('project', 'regenerate', '--output', new File(other, 'again').canonicalPath)

        then: 'its record is not that of the project, and the file is named'
        exit == 1
        err.toString().readLines() == ["| Error The application was written to ${new File(other, 'again').canonicalPath}, and its record is not that of the project: " +
                                               '.grails/.gitattributes differs']
    }

    @IgnoreIf({ os.windows || System.getProperty('user.name') == 'root' })
    void "when a file cannot be written the command names it and ends with 1, and the project is as it was"() {
        given: 'a directory of the application, where it is written to, that takes a new file by its mode and lets nobody look into it'
        create(dir, ['create-app', 'com.example.shop'])
        File project = new File(dir, 'shop')
        Map<String, String> before = tree(project)
        File output = new File(dir.canonicalFile, 'again')
        File closed = new File(output, 'grails-app/conf')
        closed.mkdirs()
        Files.setPosixFilePermissions(closed.toPath(), PosixFilePermissions.fromString('-w-------'))

        when:
        int exit = run('project', 'regenerate', '--output', output.absolutePath, '--force')
        Files.setPosixFilePermissions(closed.toPath(), PosixFilePermissions.fromString('rwxr-xr-x'))
        Map<String, String> left = tree(output)
        def said = err.toString().trim() =~ /^\| Error Cannot write grails-app\/conf\/[a-z.-]+: .+\. The application at (.+) is not complete: (\d+) of (\d+) files were written before it$/

        then: 'one line names the file, and says that what the directory has is not the application'
        exit == 1
        out.toString().isEmpty()
        err.toString().readLines().size() == 1
        said.matches()
        said.group(1) == output.canonicalPath

        and: 'what was written before it is there, the record among it, and nothing of the directory that could not be written to'
        (said.group(2) as int) == left.count { !it.value.startsWith('directory') }
        (said.group(2) as int) < (said.group(3) as int)
        (said.group(3) as int) == before.count { !it.value.startsWith('directory') }
        left.containsKey('.grails/project.yml')
        closed.list() as List == []

        and: 'the project is as it was'
        tree(project) == before

        cleanup:
        Files.setPosixFilePermissions(new File(dir.canonicalFile, 'again/grails-app/conf').toPath(), PosixFilePermissions.fromString('rwxr-xr-x'))
    }

    void "a file the record of the project has and the application that was written has not is named"() {
        given:
        create(dir, ['create-app', 'com.example.shop'])
        File project = new File(dir, 'shop')
        new File(project, '.grails/baseline/extra.txt').text = 'x'
        File output = new File(other, 'again')

        when:
        int exit = run('project', 'regenerate', '--output', output.canonicalPath)

        then:
        exit == 1
        err.toString().readLines() == ["| Error The application was written to ${output.canonicalPath}, and its record is not that of the project: " +
                                               '.grails/baseline/extra.txt is a file of the project only']
    }

    @IgnoreIf({ os.windows || System.getProperty('user.name') == 'root' })
    void "project regenerate --diff names a directory of the project it cannot read, and names no file as added, removed or changed"() {
        given:
        create(dir, ['create-app', 'com.example.shop'])
        File project = new File(dir, 'shop')
        File closed = new File(project, 'src/main/groovy/closed')
        closed.mkdirs()
        new File(closed, 'Mine.groovy').text = 'class Mine {}\n'
        Files.setPosixFilePermissions(closed.toPath(), PosixFilePermissions.fromString('---------'))
        out.reset()

        when:
        int exit = run('project', 'regenerate', '--diff')

        then:
        exit == 1
        out.toString().isEmpty()
        err.toString().readLines() == ['| Error The files of the project cannot be read: src/main/groovy/closed cannot be opened']

        when: 'with -x, where it failed is said after it'
        err.reset()
        exit = run('project', 'regenerate', '--diff', '-x')

        then:
        exit == 1
        err.toString().readLines().first() == '| Error The files of the project cannot be read: src/main/groovy/closed cannot be opened'
        err.toString().contains('AccessDeniedException')
        err.toString().contains('\tat ')

        cleanup:
        Files.setPosixFilePermissions(new File(dir, 'shop/src/main/groovy/closed').toPath(), PosixFilePermissions.fromString('rwxr-xr-x'))
    }

    void "with -x a record that cannot be used is said with where it failed, by both commands"() {
        given:
        create(dir, ['create-app', 'com.example.shop'])
        new File(dir, 'shop/.grails/forge.lock.yml').delete()

        when:
        int exit = run((command + ['-x']) as String[])

        then:
        exit == 1
        err.toString().readLines().first().startsWith('| Error The state of the project is not complete: .grails/forge.lock.yml is missing')
        err.toString().contains('ProjectStateException')
        err.toString().contains('\tat ')

        when: 'and without it, in one line'
        err.reset()
        exit = run(command as String[])

        then:
        exit == 1
        err.toString().readLines().size() == 1
        err.toString().startsWith('| Error The state of the project is not complete: .grails/forge.lock.yml is missing')

        where:
        command << [['project', 'show'], ['project', 'regenerate', '--diff']]
    }

    void "what a .gitignore leaves out: a name anywhere, a path from the root, a directory with all it has, and what a later line takes back"() {
        given:
        ProjectRegenerateCommand.IgnoreRules rules = new ProjectRegenerateCommand.IgnoreRules(
                '# a comment\n\nbuild/\r\n.idea\n*.iml  \n/dist/\ndocs/**/draft-?.txt\n**/generated\nlogs/**\n!logs/keep.txt\n!\n/\n[Bb]in/\nwith\\\\slash\n' + ('x' * 1001) + '\n')

        expect: 'a file in a directory that is left out is left out with it, however deep'
        rules.ignoresWithin(path) == ignored

        where:
        path                                 || ignored
        'build/libs/shop.jar'                || true
        'sub/build/classes/A.class'          || true
        'build'                              || false
        'build.gradle'                       || false
        '.idea/workspace.xml'                || true
        'grails-app/.idea'                   || true
        'shop.iml'                           || true
        'sub/shop.iml'                       || true
        'dist/shop.zip'                      || true
        'sub/dist/shop.zip'                  || false
        'docs/draft-1.txt'                   || true
        'docs/a/b/draft-2.txt'               || true
        'docs/a/draft-10.txt'                || false
        'src/generated/A.groovy'             || true
        'generated'                          || true
        'logs/app.log'                       || true
        'logs/keep.txt'                      || false
        'bin/tool'                           || false
        'Bin/tool'                           || false
        'x' * 1001                           || false
        'src/main/groovy/A.groovy'           || false
    }

    void "a file that a later line of a .gitignore takes back is one again where its directory is not left out"() {
        given:
        ProjectRegenerateCommand.IgnoreRules rules = new ProjectRegenerateCommand.IgnoreRules('*.log\n!keep.log\nout/\n')

        expect:
        rules.ignoresWithin('a.log')
        !rules.ignoresWithin('keep.log')
        !rules.ignoresWithin('sub/keep.log')
        rules.ignoresWithin('out/keep.log')
        rules.ignores('out', true)
        !rules.ignores('out', false)
    }

    void "the options of an application that is one project of a build, and the repositories its lock names, are shown and are on the command line"() {
        given:
        File repository = new File(other, 'repository')
        File jar = publish(repository, 'com.acme:acme-audit-grails:2.3.0')
        String address = repository.toPath().toAbsolutePath().normalize().toUri().toString()
        new File(dir, 'settings.gradle').text = "rootProject.name = 'build'\n"
        assert create(dir, ['create-app', 'com.example.shop', '--subproject', '--features', 'com.acme:acme-audit-grails:2.3.0', '--feature-pack-repository', address]) == 0
        File project = new File(dir, 'shop')
        ProjectLock lock = ProjectState.read(project.toPath()).lock
        // the lock as it is when the plugin came from a repository at an address, and the build was generated with more repositories than a lock names
        List<ProjectLock.LockedRepository> repositories = (1..StateLimits.MAX_REPOSITORIES).collect { new ProjectLock.LockedRepository(ProjectLock.LockedRepository.REMOTE, "https://repo${it}.example.org/maven".toString()) }
        repositories[1] = new ProjectLock.LockedRepository(ProjectLock.LockedRepository.MAVEN_LOCAL)
        new File(project, '.grails/forge.lock.yml').bytes = new ProjectLock(lock.manifestSha256, lock.generatorName, lock.generatorVersion, lock.grailsVersion, lock.templateApi,
                lock.applicationType, lock.options, lock.lineEndings, repositories, 3,
                lock.features.collect { ProjectLock.LockedFeature each ->
                    each.coordinates == null ? each : new ProjectLock.LockedFeature(each.name, each.source, 'https://plugins.example.org/maven', each.coordinates, each.version, each.sha256)
                }, lock.parameters, lock.baselineIndexSha256).toBytes()
        out.reset()

        when:
        int exit = run('project', 'show')
        List<String> lines = out.toString().readLines()

        then:
        exit == 0
        lines.contains('    repository    remote https://repo1.example.org/maven')
        lines.contains('    repository    maven-local')
        lines.contains('    repository    and 3 more')
        lines.contains("    acme-audit (pack, com.acme:acme-audit-grails:2.3.0, sha256 ${sha(jar.bytes)}, from https://plugins.example.org/maven)".toString())
        lines.any { it ==~ / +--subproject +yes +\(differs from the current default: no\)/ }
        lines.any { it ==~ / +--feature-pack-repository +https:\/\/plugins\.example\.org\/maven +\(differs from the current default: \(none\)\)/ }
        !lines.any { it.contains('does not name the repository') }
        lines.contains('| The application was generated with the repositories of the environment variable GRAILS_REPO_URL, which are written into its build: ' +
                'set the variable to the same repositories for the command')
        words(lines[-1]) == ['grails-forge-cli', 'create-app', 'com.example.shop', '--subproject', '--features', 'com.acme:acme-audit-grails:2.3.0',
                             '--feature-pack-repository', 'https://plugins.example.org/maven',
                             '--feature-pack-sha256', "com.acme:acme-audit-grails:2.3.0=${sha(jar.bytes)}".toString()]

        when:
        out.reset()
        exit = run('project', 'show', '--json')
        Map document = (Map) new JsonSlurper().parseText(out.toString())

        then:
        exit == 0
        document.repositories.size() == StateLimits.MAX_REPOSITORIES
        document.repositories[0] == [kind: 'remote', url: 'https://repo1.example.org/maven']
        document.repositories[1] == [kind: 'maven-local']
        document.repositoriesNotNamed == 3
        document.features.find { it.name == 'acme-audit' }.origin == 'https://plugins.example.org/maven'
        document.effectiveOptions.find { it.name == 'subproject' } == [name: 'subproject', option: '--subproject', value: true, default: false, status: 'differs']
        document.effectiveOptions.find { it.name == 'feature-pack-repository' }.value == ['https://plugins.example.org/maven']
    }

    void "the commands are shown by help, described for a tool and completed"() {
        when:
        int exit = ForgeCli.execute(beanContext, 'help')

        then:
        exit == 0
        out.toString().contains('*  project show')
        out.toString().contains('*  project regenerate --output DIRECTORY')
        out.toString().contains('*  project migrate')
        out.toString() =~ /(?m)^  project +Works with the record of how the application in the current directory was\s/

        when:
        out.reset()
        exit = ForgeCli.execute(beanContext, 'help', '--json')
        Map project = ((List<Map>) ((Map) new JsonSlurper().parseText(out.toString())).commands).find { it.name == 'project' }

        then:
        exit == 0
        project.project == false
        project.commands*.name == ['show', 'regenerate', 'migrate']
        project.commands.every { it.project == true && it.origin == 'built-in' && it.description }
        project.commands[0].options*.names.contains(['--json'])
        project.commands[1].options*.names.containsAll([['-o', '--output'], ['--diff'], ['-f', '--force']])
        project.commands[1].options.find { '--output' in it.names }.label == 'DIRECTORY'
        project.commands[2].options*.names.containsAll([['--type'], ['--features'], ['--exclude-features'], ['--jdk', '--java-version'], ['-s', '--servlet'],
                                                        ['-d', '--data', '-g', '--gorm'], ['-r', '--reloading'], ['--dry-run'], ['--force']])

        when:
        out.reset()
        exit = ForgeCli.execute(beanContext, 'help', 'project', 'regenerate')
        String help = out.toString()
        out.reset()
        int option = ForgeCli.execute(beanContext, 'project', 'regenerate', '--help')

        then:
        exit == 0
        option == 0
        help == out.toString()
        help.startsWith('Usage: grails-forge-cli project regenerate ')
        help.contains('--diff')

        when:
        out.reset()
        exit = ForgeCli.execute(beanContext, 'help', 'project', 'show')

        then:
        exit == 0
        out.toString().startsWith('Usage: grails-forge-cli project show ')

        when:
        out.reset()
        exit = ForgeCli.execute(beanContext, 'help', 'project', 'migrate')

        then:
        exit == 0
        out.toString().startsWith('Usage: grails-forge-cli project migrate ')

        and:
        complete('project m').contains('migrate')
        complete('project migrate --f').containsAll(['--features', '--force'])
        complete('pro').contains('project')
        complete('project s').contains('show')
        complete('project re').contains('regenerate')
        complete('project regenerate --o').containsAll(['--output', '--diff', '--force'])
        complete('project show --j').contains('--json')
    }

    void "project without a command says which commands there are"() {
        when:
        int exit = ForgeCli.execute(beanContext, 'project')

        then:
        exit == 2
        err.toString().contains('No command specified: project show says how the application was generated, and project regenerate generates it again; ' +
                'project migrate writes the record of an application that has none')
    }

    private static final String DESCRIPTOR = 'format: 1\nkind: feature\ntemplateApi: 1\nname: acme-audit\n' +
            "compatibility:\n  grails: '[9.0.0,10.0.0)'\n  forge: '[9.0.0,10.0.0)'\n" +
            "files:\n  - from: audit.txt\n    to: 'grails-app/conf/audit.txt'\n"

    // a create command, in a directory of its own
    private int create(File directory, List<String> arguments) {
        String current = System.getProperty('user.dir')
        System.setProperty('user.dir', directory.absolutePath)
        try {
            return ForgeCli.execute(beanContext, arguments as String[])
        } finally {
            System.setProperty('user.dir', current)
            // what a create command says is not what is looked at
            out.reset()
        }
    }

    // takes an option out of the manifest and the lock, and seals the lock again, so that the two belong together as before
    private static void withoutOption(File project, String key) {
        File manifest = new File(project, '.grails/project.yml')
        File lock = new File(project, '.grails/forge.lock.yml')
        String before = sha(manifest.bytes)
        manifest.setText(manifest.getText('UTF-8').readLines().findAll { String line -> !line.startsWith("  ${key}:") }.join('\n') + '\n', 'UTF-8')
        String text = lock.getText('UTF-8')
        assert text.contains(before)
        String sealed = text.substring(0, text.indexOf('seal:\n')).readLines().findAll { String line -> !line.startsWith("  ${key}:") }.join('\n') + '\n'
        sealed = sealed.replace(before, sha(manifest.bytes))
        lock.setText(sealed + "seal:\n  sha256: ${sha(sealed.getBytes('UTF-8'))}\n", 'UTF-8')
    }

    // the note for the repositories the build of Forge gives its tests in GRAILS_REPO_URL, when it does
    private List<String> ofRepositories() {
        return ProjectState.read(new File(dir, 'shop').toPath()).lock.repositories.isEmpty() ? [] :
                ['The application was generated with the repositories of the environment variable GRAILS_REPO_URL, which are written into its build: ' +
                         'set the variable to the same repositories for the command']
    }

    // a command in the application that was created in the directory of the spec
    private int run(String... arguments) {
        String current = System.getProperty('user.dir')
        System.setProperty('user.dir', new File(dir, 'shop').absolutePath)
        try {
            return ForgeCli.execute(beanContext, arguments)
        } finally {
            System.setProperty('user.dir', current)
        }
    }

    private List<String> complete(String line) {
        List<Candidate> candidates = []
        new CommandLineCompleter(ForgeCli.createCommandLine(beanContext, true).getCommandSpec())
                .complete(null, new DefaultParser().parse(line, line.length()), candidates)
        return candidates*.value()
    }

    /**
     * The words a POSIX shell reads a line as, for a line that has nothing but words and single
     * quotes: outside the quotes a backslash takes the next character as it is.
     */
    private static List<String> words(String line) {
        List<String> words = []
        StringBuilder word = null
        boolean quoted = false
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i)
            if (quoted) {
                if (c == (char) "'") {
                    quoted = false
                } else {
                    word.append(c)
                }
            } else if (c == (char) "'") {
                quoted = true
                word = word ?: new StringBuilder()
            } else if (c == (char) '\\') {
                word = word ?: new StringBuilder()
                word.append(line.charAt(++i))
            } else if (c == (char) ' ') {
                if (word != null) {
                    words << word.toString()
                    word = null
                }
            } else {
                word = word ?: new StringBuilder()
                word.append(c)
            }
        }
        if (word != null) {
            words << word.toString()
        }
        return words
    }

    private static File publish(File repository, String coordinates) {
        List<String> parts = coordinates.split(':').toList()
        File directory = new File(repository, "${parts[0].replace('.', '/')}/${parts[1]}/${parts[2]}")
        directory.mkdirs()
        File jar = new File(directory, "${parts[1]}-${parts[2]}.jar")
        new ZipOutputStream(new FileOutputStream(jar)).withCloseable { ZipOutputStream zip ->
            ['META-INF/grails-forge/feature.yml': DESCRIPTOR, 'META-INF/grails-forge/templates/audit.txt': 'kept as it is'].each { String path, String text ->
                zip.putNextEntry(new ZipEntry(path))
                zip.write(text.getBytes('UTF-8'))
                zip.closeEntry()
            }
        }
        return jar
    }

    /** Everything of a project: every directory with its mode, and every file with the digest of its content and its mode, a link as a link. */
    private static Map<String, String> tree(File project) {
        Map<String, String> entries = new TreeMap<>()
        boolean posix = project.toPath().fileSystem.supportedFileAttributeViews().contains('posix')
        Files.walk(project.toPath()).withCloseable { stream ->
            stream.each { java.nio.file.Path path ->
                String name = project.toPath().relativize(path).join('/')
                if (name.isEmpty()) {
                    return
                }
                String mode = posix ? PosixFilePermissions.toString(Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS)) : ''
                if (Files.isSymbolicLink(path)) {
                    entries.put(name, "link to ${Files.readSymbolicLink(path)}".toString())
                } else if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    entries.put(name, "directory ${mode}".toString())
                } else {
                    entries.put(name, "${sha(Files.readAllBytes(path))} ${mode}".toString())
                }
            }
        }
        return entries
    }

    private static String sha(byte[] bytes) {
        return MessageDigest.getInstance('SHA-256').digest(bytes).encodeHex().toString()
    }
}
