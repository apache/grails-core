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
import org.grails.forge.cli.CommandLineCompleter
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import org.grails.forge.util.VersionInfo
import org.jline.reader.Candidate
import org.jline.reader.impl.DefaultParser
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
                                      'requestedFeatures', 'excludedFeatures', 'repositories', 'repositoriesNotNamed', 'features', 'command', 'notes']
        document.generator == [name: 'grails-forge', version: VersionInfo.version]
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

        when: 'asked again'
        String first = out.toString()
        out.reset()
        run('project', 'show', '--json')

        then:
        out.toString() == first
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
        lines.findAll { it.startsWith('| ') }.size() == 4 + ofRepositories().size()
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

        when: 'the application is to be generated again'
        err.reset()
        exit = run('project', 'regenerate', '--diff')

        then: 'which is refused as adding a feature to it is'
        exit == 1
        err.toString().contains('The application has the feature acme-audit of a feature pack that is not part of Grails; a feature cannot yet be added to such an application')
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
                                   'give it to the command with --feature-pack-dir'] + ofRepositories()
        document.features.find { it.name == 'acme-audit' } == [name: 'acme-audit', source: 'pack']
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
        err.toString().contains('There is no state of a generated project here: .grails/project.yml is missing')
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

    void "an application that another version generated is shown with a note, and is generated again by that version only"() {
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
        out.toString().readLines().contains("| The application was generated by version 9.0.0-M1 of the command line, and this is ${VersionInfo.version}: the command is for that version".toString())
        out.toString().readLines()[-1] == 'grails-forge-cli create-app com.example.shop'

        when:
        exit = run('project', 'regenerate', '--diff')

        then:
        exit == 1
        err.toString().contains("The application was generated by grails-forge 9.0.0-M1 for Grails ${VersionInfo.grailsVersion}, and this is grails-forge ${VersionInfo.version} " +
                "for Grails ${VersionInfo.grailsVersion}")
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

    void "the commands are shown by help, described for a tool and completed"() {
        when:
        int exit = ForgeCli.execute(beanContext, 'help')

        then:
        exit == 0
        out.toString().contains('*  project show')
        out.toString().contains('*  project regenerate --output DIRECTORY')
        out.toString() =~ /(?m)^  project +Works with the record of how the application in the current directory was\s/

        when:
        out.reset()
        exit = ForgeCli.execute(beanContext, 'help', '--json')
        Map project = ((List<Map>) ((Map) new JsonSlurper().parseText(out.toString())).commands).find { it.name == 'project' }

        then:
        exit == 0
        project.project == false
        project.commands*.name == ['show', 'regenerate']
        project.commands.every { it.project == true && it.origin == 'built-in' && it.description }
        project.commands[0].options*.names.contains(['--json'])
        project.commands[1].options*.names.containsAll([['-o', '--output'], ['--diff'], ['-f', '--force']])
        project.commands[1].options.find { '--output' in it.names }.label == 'DIRECTORY'

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

        and:
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
        err.toString().contains('No command specified: project show says how the application was generated, and project regenerate generates it again')
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
