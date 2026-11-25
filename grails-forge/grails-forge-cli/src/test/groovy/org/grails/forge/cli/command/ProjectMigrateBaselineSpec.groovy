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

import groovy.json.JsonSlurper
import org.apache.grails.forge.core.change.EarlierGeneratedFiles
import org.apache.grails.forge.core.state.ProjectManifest
import org.apache.grails.forge.core.state.ProjectState
import org.grails.forge.cli.CliRunner
import org.grails.forge.cli.ForgeCli
import org.grails.forge.util.VersionInfo
import spock.lang.Unroll

/**
 * {@code project migrate} for an application that Grails 7 or 8 created, see {@link EarlierApplication}:
 * the record it writes has the baseline this version generates for what the application is taken to
 * be, and from then on a feature is added to the application as to one this version generated. What
 * the application has otherwise than that baseline is kept, and where a feature changes the same
 * lines nothing is written.
 */
class ProjectMigrateBaselineSpec extends AbstractFeatureAddCommandSpec {

    static final String WRITTEN = '| The record was written, with its baseline under .grails/baseline: .grails/project.yml, .grails/forge.lock.yml and ' +
            '.grails/baseline/index.yml. No other file of the project was changed. Commit the directory .grails'

    @Unroll
    void "an application that #created gets a record with the baseline of what it is taken to be, and no other file of it is touched"() {
        given:
        EarlierApplication.copy(fixture, dir)
        EarlierApplication.upgrade(dir)
        Map<String, String> before = tree(dir)

        when:
        int exit = migrate()
        List<String> said = out.toString().readLines()
        ProjectState state = ProjectState.read(dir.toPath())

        then: 'what was found is said, and how the application compares with what this version generates for it'
        exit == 0
        err.toString().isEmpty()
        said.any { it ==~ / +type +web +\(.*\)/ }
        said.any { it ==~ / +data +hibernate5 +\(.*\)/ }
        said.any { it ==~ / +reloading +devtools +\(.*\)/ }
        String baseline = said.find { it.startsWith('| The baseline is the ') }
        baseline ==~ (/\| The baseline is the / + state.baseline.files.size() + / files this version generates for an application of the type, the options and the features above / +
                /\(/ + state.lock.features.size() + / features with those of the type; \S+ project show lists them\)\. The application has \d+ of them as generated, / +
                /\d+ otherwise and \d+ not at all: what it has otherwise is taken for a change of yours/)
        said.contains(WRITTEN)

        and: 'the record says that it was written for an application that was there, and its baseline is what this build generates'
        state.manifest.generationState == ProjectManifest.ADOPTED
        !state.manifest.tracked
        state.manifest.grailsVersion == VersionInfo.grailsVersion
        state.lock.generatorSha256 == VersionInfo.generatorSha256
        state.baseline.files*.path.containsAll(['build.gradle', 'grails-app/conf/application.yml'])
        state.verifyBaseline(dir.toPath())
        // a feature that was found and that every application of the type has stays a default, which another feature of its group replaces
        !state.manifest.requestedFeatures*.name.contains('h2')
        state.lock.featureNames.contains('h2')

        and: 'nothing but the record was written'
        Map<String, String> after = tree(dir)
        after.keySet().findAll { String path -> after.get(path) != before.get(path) }.every { String path -> path == '.grails' || path.startsWith('.grails/') }
        before.keySet().every { String path -> after.containsKey(path) }
        new File(dir, '.grails/.gitignore').text == ProjectState.IGNORE_CONTENT

        when: 'the record is shown'
        reset()
        exit = show()

        then:
        exit == 0
        out.toString().readLines().any { it ==~ / +state +adopted/ }
        out.toString().contains('The record was written for an application that was there, by project migrate, and not when the application was generated ' +
                '(its state is adopted): it says what the application is taken to be, and its baseline is what this version generates for that.')
        out.toString().contains('feature add adds a feature against the baseline and takes what the application has otherwise for a change of yours')

        when: 'and the project is checked'
        reset()
        health('-v')

        then:
        out.toString().contains('The record under .grails, which project migrate wrote with the baseline of what the application is taken to be (generationState adopted), ' +
                'reads, its baseline is what its index says, and it is the record of this version')

        when: 'a feature that only adds a file is added'
        reset()
        exit = add('github-workflow-java-ci')

        then: 'as to any application'
        exit == 0
        out.toString() =~ /add +\.github\/workflows\/gradle\.yml/
        new File(dir, '.github/workflows/gradle.yml').isFile()
        ProjectState.read(dir.toPath()).manifest.generationState == ProjectManifest.ADOPTED
        ProjectState.read(dir.toPath()).manifest.requestedFeatures*.name.contains('github-workflow-java-ci')
        ProjectState.read(dir.toPath()).verifyBaseline(dir.toPath())

        when: 'the command is run again, with nothing that says otherwise what the application is'
        reset()
        exit = migrate()

        then: 'the record is that of this version, and how to write it anew is said'
        exit == 0
        out.toString().contains('it is that of this version already, and there is nothing to move. Nothing was written. The record was written for an application ' +
                'that was there, with the baseline of what it is taken to be:')
        out.toString().contains('project migrate --force reads the files again and writes it anew')

        where:
        fixture                           | created
        EarlierApplication.GRAILS_8_SHELL | 'the Grails shell of Grails 8.0.0 created'
        EarlierApplication.GRAILS_8_FORGE | 'Grails 8.0.0 generated'
        EarlierApplication.GRAILS_7_FORGE | 'Grails 7.0.9 generated'
    }

    void "features that change the build and the configuration are added to an application of Grails 8, with every line of the application kept"() {
        given:
        EarlierApplication.copy(EarlierApplication.GRAILS_8_FORGE, dir)
        EarlierApplication.upgrade(dir)
        assert migrate() == 0
        List<String> build = new File(dir, 'build.gradle').readLines()
        List<String> configuration = new File(dir, 'grails-app/conf/application.yml').readLines()
        reset()

        when: 'a feature that takes the place of the default database driver'
        int exit = add('postgres')

        then: 'the lines of the driver that was found are replaced, and the others are those the application had'
        exit == 0
        err.toString().isEmpty()
        out.toString().contains('postgres replaces h2, the default of the group database-driver')
        out.toString() =~ /merge +build\.gradle \(your changes are kept\)/
        out.toString() =~ /merge +grails-app\/conf\/application\.yml \(your changes are kept\)/
        List<String> added = new File(dir, 'build.gradle').readLines()
        added.contains('    runtimeOnly "org.postgresql:postgresql"')
        !added.contains('    runtimeOnly "com.h2database:h2"')
        added.containsAll(build - ['    runtimeOnly "com.h2database:h2"'])
        new File(dir, 'grails-app/conf/application.yml').text.contains('org.postgresql.Driver')
        new File(dir, 'grails-app/conf/application.yml').readLines().containsAll(configuration.takeWhile { String line -> line != 'dataSource:' })
        new File(dir, 'src/test/resources/application-test.yml').isFile()

        and: 'the record is the adopted one with the feature, and its baseline reads'
        ProjectState state = ProjectState.read(dir.toPath())
        state.manifest.generationState == ProjectManifest.ADOPTED
        state.manifest.requestedFeatures*.name == ['postgres']
        state.lock.featureNames.contains('postgres')
        !state.lock.featureNames.contains('h2')
        state.verifyBaseline(dir.toPath())

        when: 'a feature that adds to the build and writes files'
        reset()
        exit = add('asciidoctor')

        then:
        exit == 0
        out.toString() =~ /merge +build\.gradle \(your changes are kept\)/
        new File(dir, 'gradle/asciidoc.gradle').isFile()
        new File(dir, 'build.gradle').readLines().containsAll(added)

        when: 'it is added again'
        Map<String, String> before = tree(dir)
        reset()
        exit = add('asciidoctor')

        then:
        exit == 0
        out.toString().contains('The application already has the feature asciidoctor')
        tree(dir) == before
    }

    void "a feature whose lines have no place in a file that is laid out otherwise is added all the same, and the record is written anew with what the application has"() {
        given: 'an application of Grails 8, whose README has other sections than this version writes'
        EarlierApplication.copy(EarlierApplication.GRAILS_8_FORGE, dir)
        EarlierApplication.upgrade(dir)
        assert migrate() == 0
        assert ProjectState.read(dir.toPath()).lock.featureNames.contains('mockito')
        List<String> build = new File(dir, 'build.gradle').readLines()
        List<String> readme = new File(dir, 'README.md').readLines()
        reset()

        when:
        int exit = add('cache')

        then: 'the lines of the README are put at its end, which is said, and the other files are merged'
        done(exit)
        out.toString() =~ /merge +README\.md \(your changes are kept\)/
        out.toString().contains('this file is not laid out as this version writes it: the lines the feature adds were put at the end of it')
        out.toString() =~ /merge +build\.gradle \(your changes are kept\)/
        out.toString() =~ /merge +grails-app\/conf\/application\.yml \(your changes are kept\)/
        new File(dir, 'build.gradle').readLines().contains('    implementation "org.apache.grails:grails-cache"')
        new File(dir, 'build.gradle').readLines().containsAll(build)
        new File(dir, 'grails-app/conf/application.yml').readLines()*.trim().containsAll(['cache:', 'enabled: true', 'clearAtStartup: false'])
        new File(dir, 'README.md').readLines().take(readme.size()) == readme
        new File(dir, 'README.md').readLines().drop(readme.size()).findAll { String line -> !line.isEmpty() }[0] == '## Feature cache documentation'
        ProjectState.read(dir.toPath()).verifyBaseline(dir.toPath())

        when: 'a feature that adds an entry to a list of the build, whose last entry the application has without the comma this version writes'
        reset()
        exit = add('openapi')

        then: 'the entry is put at the end of the list of the application, which gets the comma that sets it apart'
        done(exit)
        out.toString().contains('1 entry at the end of the list excludes of assets')
        new File(dir, 'build.gradle').text.contains("            'webjars/bootstrap-icons/**',\n            'webjars/swagger-ui/**'\n    ]\n")

        when: 'a class of the application has a line of its own where a feature writes'
        File bootStrap = new File(dir, 'grails-app/init/com/example/BootStrap.groovy')
        bootStrap.text = bootStrap.text.replace('    def init = {\n', '    def init = {\n        println "ours"\n')
        Map<String, String> before = tree(dir)
        reset()
        exit = add('grails-spring-security')

        then: 'that is a conflict, and nothing is written, the record among it'
        exit == 1
        out.toString() =~ /conflict +grails-app\/init\/com\/example\/BootStrap\.groovy: /
        err.toString().contains('The feature was not added: 1 file cannot be written without losing what the application has. Nothing was changed.')
        tree(dir) == before

        when: 'what it comes to is asked for as a document'
        reset()
        exit = add('grails-spring-security', '--dry-run', '--json')
        Map document = new JsonSlurper().parseText(out.toString()) as Map

        then:
        exit == 1
        document.outcome == 'conflicts'
        (document.files as List<Map>).find { Map file -> file.path == 'grails-app/init/com/example/BootStrap.groovy' }.action == 'conflict'
        tree(dir) == before

        when: 'the record is written anew without a feature the application does not have'
        reset()
        exit = migrate('--force', '--exclude-features', 'mockito')
        ProjectState state = ProjectState.read(dir.toPath())

        then: 'the feature that was added is kept, as one the record names, and no file of the application is touched'
        exit == 0
        out.toString().contains('| Left out with --exclude-features: mockito')
        out.toString().contains('    cache (the record, .grails/project.yml)')
        out.toString().contains(WRITTEN)
        state.manifest.generationState == ProjectManifest.ADOPTED
        state.manifest.excludedFeatures*.name == ['mockito']
        state.manifest.requestedFeatures*.name == ['cache', 'openapi']
        !state.lock.featureNames.contains('mockito')
        state.verifyBaseline(dir.toPath())
        tree(dir).findAll { String path, String content -> !path.startsWith('.grails') } == before.findAll { String path, String content -> !path.startsWith('.grails') }
    }

    void "an application the Grails shell created gets a feature without the file it does not have, with its dependencies and its settings where they can be put"() {
        given:
        EarlierApplication.copy(EarlierApplication.GRAILS_8_SHELL, dir)
        EarlierApplication.upgrade(dir)
        assert migrate() == 0
        String build = new File(dir, 'build.gradle').text
        String configuration = new File(dir, 'grails-app/conf/application.yml').text
        reset()

        when: 'a feature that adds a dependency, settings and a section of the README'
        int exit = add('cache')

        then: 'the README the application does not have is left out, and what would have been added to it is said'
        done(exit)
        out.toString() =~ /leave +README\.md: the application does not have this file, which this version generates for it and the feature changes: it is left out/
        out.toString().contains('## Feature cache documentation')
        out.toString().contains('the file as this version generates it with the feature is kept as .grails/baseline/files/README.md: copy it from there to have it')
        !new File(dir, 'README.md').exists()
        new File(dir, '.grails/baseline/files/README.md').text.contains('## Feature cache documentation')

        and: 'the dependency is the last of the dependencies, which are in the order they were in'
        out.toString().contains('1 dependency at the end of the dependencies block')
        new File(dir, 'build.gradle').text == build.replace('    testImplementation "org.apache.grails:grails-testing-support-web"\n}\n',
                '    testImplementation "org.apache.grails:grails-testing-support-web"\n    implementation "org.apache.grails:grails-cache"\n}\n')

        and: 'the settings are a document of their own at the end of a configuration that is written as several'
        out.toString().contains('what the feature sets, which the file does not set, put into a document of its own at the end of it')
        new File(dir, 'grails-app/conf/application.yml').text.startsWith(configuration)
        new File(dir, 'grails-app/conf/application.yml').text.substring(configuration.length()).readLines()*.trim().toSet() ==
                ['---', 'grails:', 'cache:', 'enabled: true', 'clearAtStartup: false', 'cacheManager: GrailsConcurrentMapCacheManager'].toSet()
        ProjectState.read(dir.toPath()).lock.featureNames.contains('cache')
        ProjectState.read(dir.toPath()).verifyBaseline(dir.toPath())

        when: 'a feature that brings a Gradle plugin by its id and a script of its own'
        String withCache = new File(dir, 'build.gradle').text
        reset()
        exit = add('asciidoctor')

        then: 'the plugin gets a plugins block after the buildscript block, which the build did not have, and the script is applied after its plugins'
        done(exit)
        out.toString().contains('1 plugin in a plugins block of its own after the buildscript block, where Gradle takes one')
        out.toString().contains('1 apply line after the last line of the file that applies a plugin')
        new File(dir, 'build.gradle').text == withCache.replace('}\n\nversion = "0.1"', '}\n\nplugins {\n    id "org.asciidoctor.jvm.convert" version "4.0.5"\n}\n\nversion = "0.1"')
                .replace('apply plugin:"org.apache.grails.gradle.grails-gsp"\n', 'apply plugin:"org.apache.grails.gradle.grails-gsp"\napply from: "gradle/asciidoc.gradle"\n')

        when: 'a feature that applies a Gradle plugin'
        String withDocs = new File(dir, 'build.gradle').text
        reset()
        exit = add('views-json')

        then: 'the plugin is applied after the last line that applies one'
        done(exit)
        new File(dir, 'build.gradle').text.contains('apply from: "gradle/asciidoc.gradle"\napply plugin: "org.apache.grails.gradle.grails-gson"\n')
        new File(dir, 'build.gradle').readLines().containsAll(withDocs.readLines())

        when: 'a feature that takes the place of the database driver, whose settings the configuration has in documents and an indentation of its own'
        String settings = new File(dir, 'grails-app/conf/application.yml').text
        reset()
        exit = add('postgres')

        then: 'each value is changed where it is, and nothing else of the file'
        done(exit)
        out.toString().contains('5 values the feature changes changed where the file has them, each as it was generated')
        new File(dir, 'grails-app/conf/application.yml').text == settings.replace('driverClassName: org.h2.Driver', 'driverClassName: org.postgresql.Driver')
                .replace('username: sa', 'username: postgres')
                .replace('url: jdbc:h2:mem:devDb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE', 'url: jdbc:postgresql://localhost:5432/devDb?tcpKeepAlive=true')
                .replace('url: jdbc:h2:mem:testDb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE', 'url: jdbc:postgresql://localhost:5432/testDb?tcpKeepAlive=true')
                .replace('url: jdbc:h2:./prodDb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE', 'url: jdbc:postgresql://localhost:5432/postgres?tcpKeepAlive=true')
        !new File(dir, 'build.gradle').text.contains('com.h2database:h2')

        when: 'a feature that changes the BootStrap class, which is as the Grails shell wrote it'
        reset()
        exit = add('grails-spring-security')

        then: 'the class becomes the one this version generates with the feature'
        done(exit)
        out.toString() =~ /update +grails-app\/init\/com\/example\/BootStrap\.groovy/
        out.toString().contains('this file is as an earlier version of Grails generated it, with nothing of yours in it')
        new File(dir, 'grails-app/init/com/example/BootStrap.groovy').text.contains('springSecurityService')
        Map<String, String> before = tree(dir)

        when: 'the files that are not as this version generates them are asked for'
        reset()
        exit = regenerate('--diff')

        then: 'they are all the application has otherwise than a new one, and that is said'
        exit == 1
        out.toString().readLines().contains('changed  build.gradle')
        out.toString().readLines().contains('removed  README.md')
        out.toString() =~ /\| \d+ files are not as this version generates an application of what this one is taken to be: /
        out.toString().contains('the files named are all the application has otherwise than a new one of the same type, options and features, what an earlier version of Grails ' +
                'generated otherwise among it, and not only what was changed in it')
        tree(dir) == before
    }

    @Unroll
    void "#feature is #outcome an application that #created: #conflicts in the way, #left left out"() {
        given:
        EarlierApplication.copy(fixture, dir)
        EarlierApplication.upgrade(dir)
        assert migrate() == 0
        Map<String, String> before = tree(dir)
        reset()

        when:
        int exit = add(feature, '--dry-run', '--json')
        Map document = new JsonSlurper().parseText(out.toString()) as Map
        List<Map> files = document.files as List<Map>

        then:
        document.outcome == (conflicts.isEmpty() ? 'changes' : 'conflicts')
        exit == (conflicts.isEmpty() ? 0 : 1)
        files.findAll { Map file -> file.action == 'conflict' }*.path == conflicts
        files.findAll { Map file -> file.action == 'leave' }*.path == left
        tree(dir) == before

        where:
        fixture                           | feature                   || conflicts                                              | left
        EarlierApplication.GRAILS_8_SHELL | 'cache'                   || []                                                     | ['README.md']
        EarlierApplication.GRAILS_8_SHELL | 'grails-quartz'           || []                                                     | ['README.md']
        EarlierApplication.GRAILS_8_SHELL | 'postgres'                || []                                                     | ['README.md']
        EarlierApplication.GRAILS_8_SHELL | 'github-workflow-java-ci' || []                                                     | []
        EarlierApplication.GRAILS_8_SHELL | 'asciidoctor'             || []                                                     | []
        EarlierApplication.GRAILS_8_SHELL | 'views-json'              || []                                                     | ['README.md']
        EarlierApplication.GRAILS_8_SHELL | 'testcontainers'          || []                                                     | ['README.md']
        EarlierApplication.GRAILS_8_SHELL | 'grails-spring-security'  || []                                                     | ['README.md']
        EarlierApplication.GRAILS_8_SHELL | 'mysql'                   || []                                                     | ['README.md']
        EarlierApplication.GRAILS_8_SHELL | 'openapi'                 || []                                                     | ['README.md']
        EarlierApplication.GRAILS_8_FORGE | 'cache'                   || []                                                     | []
        EarlierApplication.GRAILS_8_FORGE | 'grails-quartz'           || []                                                     | []
        EarlierApplication.GRAILS_8_FORGE | 'postgres'                || []                                                     | []
        EarlierApplication.GRAILS_8_FORGE | 'github-workflow-java-ci' || []                                                     | []
        EarlierApplication.GRAILS_8_FORGE | 'asciidoctor'             || []                                                     | []
        EarlierApplication.GRAILS_8_FORGE | 'views-json'              || []                                                     | []
        EarlierApplication.GRAILS_8_FORGE | 'testcontainers'          || []                                                     | []
        EarlierApplication.GRAILS_8_FORGE | 'grails-spring-security'  || []                                                     | []
        EarlierApplication.GRAILS_8_FORGE | 'mysql'                   || []                                                     | []
        EarlierApplication.GRAILS_8_FORGE | 'openapi'                 || []                                                     | []
        EarlierApplication.GRAILS_7_FORGE | 'cache'                   || []                                                     | []
        EarlierApplication.GRAILS_7_FORGE | 'grails-quartz'           || []                                                     | []
        EarlierApplication.GRAILS_7_FORGE | 'postgres'                || []                                                     | []
        EarlierApplication.GRAILS_7_FORGE | 'github-workflow-java-ci' || []                                                     | []
        EarlierApplication.GRAILS_7_FORGE | 'asciidoctor'             || []                                                     | []
        EarlierApplication.GRAILS_7_FORGE | 'views-json'              || []                                                     | []
        EarlierApplication.GRAILS_7_FORGE | 'testcontainers'          || []                                                     | []
        EarlierApplication.GRAILS_7_FORGE | 'grails-spring-security'  || []                                                     | []
        EarlierApplication.GRAILS_7_FORGE | 'mysql'                   || []                                                     | []
        EarlierApplication.GRAILS_7_FORGE | 'openapi'                 || []                                                     | []
        outcome = conflicts.isEmpty() ? 'added to' : 'not added to'
        created = fixture == EarlierApplication.GRAILS_8_SHELL ? 'the Grails shell of Grails 8.0.0 created' :
                fixture == EarlierApplication.GRAILS_8_FORGE ? 'Grails 8.0.0 generated' : 'Grails 7.0.9 generated'
    }

    @Unroll
    void "the BootStrap class of an application that #created is one an earlier version is known to have generated"() {
        given:
        EarlierApplication.copy(fixture, dir)
        File bootStrap = new File(dir, 'grails-app/init/com/example/BootStrap.groovy')

        expect: 'whatever the package and the ends of the lines'
        EarlierGeneratedFiles.isAsGenerated('grails-app/init/com/example/BootStrap.groovy', bootStrap.bytes)
        EarlierGeneratedFiles.isAsGenerated('grails-app/init/org/acme/shop/BootStrap.groovy', bootStrap.text.replace('package com.example', 'package org.acme.shop').replace('\n', '\r\n').bytes)

        and: 'and with a line of anyone else in it, it is not'
        !EarlierGeneratedFiles.isAsGenerated('grails-app/init/com/example/BootStrap.groovy', bootStrap.text.replace('def init = {', 'def init = { // ours').bytes)
        !EarlierGeneratedFiles.isAsGenerated('grails-app/conf/BootStrap.groovy', bootStrap.bytes)

        where:
        fixture                           | created
        EarlierApplication.GRAILS_8_SHELL | 'the Grails shell of Grails 8.0.0 created'
        EarlierApplication.GRAILS_8_FORGE | 'Grails 8.0.0 generated'
        EarlierApplication.GRAILS_7_FORGE | 'Grails 7.0.9 generated'
    }

    void "a project that is still on Grails 8 gets its record, and a feature is added once it is on Grails 9"() {
        given:
        EarlierApplication.copy(EarlierApplication.GRAILS_8_FORGE, dir)

        when:
        int exit = migrate()

        then:
        exit == 0
        out.toString().contains('The project is on Grails 8.0.0 (gradle.properties): the record is that of an application of Grails 9')
        ProjectState.read(dir.toPath()).manifest.generationState == ProjectManifest.ADOPTED

        when: 'the command is run as it is typed, which looks at the version of the project first'
        Map<String, String> before = tree(dir)
        reset()
        exit = ForgeCli.execute(beanContext, 'feature', 'add', 'github-workflow-java-ci')

        then:
        exit == 1
        err.toString().contains('This project is on Grails 8.0.0 (gradle.properties)')
        tree(dir) == before

        when:
        EarlierApplication.upgrade(dir)
        reset()
        exit = ForgeCli.execute(beanContext, 'feature', 'add', 'github-workflow-java-ci')

        then:
        exit == 0
        new File(dir, '.github/workflows/gradle.yml').isFile()
    }

    void "where the type of the application was assumed the record has no baseline, feature add says how to get one, and the command writes it once it is told"() {
        given: 'a plugin whose build does not say whether it has controllers'
        EarlierApplication.pluginOfAssumedType(dir)

        when:
        int exit = migrate()
        ProjectState state = ProjectState.read(dir.toPath())

        then:
        exit == 0
        out.toString().contains('| The record is written without a baseline (generationState untracked), and')
        out.toString().contains('adds no feature to the application until it has one: the type of the application was assumed (the Gradle plugin grails-plugin of build.gradle')
        out.toString().contains('say it with --type, and run')
        state.manifest.generationState == ProjectManifest.UNTRACKED
        state.baseline.files.isEmpty()

        when:
        Map<String, String> before = tree(dir)
        reset()
        exit = add('github-workflow-java-ci')

        then:
        exit == 1
        err.toString().contains('project migrate --force writes the record with a baseline once it is told what it could not work out, which it names')
        tree(dir) == before

        when:
        reset()
        exit = migrate('--force', '--type', 'plugin')

        then:
        exit == 0
        out.toString().contains(WRITTEN)
        ProjectState.read(dir.toPath()).manifest.generationState == ProjectManifest.ADOPTED
        ProjectState.read(dir.toPath()).verifyBaseline(dir.toPath())

        when:
        reset()
        exit = add('github-workflow-java-ci')

        then:
        exit == 0
        new File(dir, '.github/workflows/gradle.yml').isFile()

        when: 'the command is to write the record anew, and is not told the type it assumed'
        before = tree(dir)
        reset()
        exit = migrate('--force')

        then: 'the record with its baseline is not replaced by one without'
        exit == 1
        err.toString().contains('The record is not written anew: the type of the application was assumed')
        err.toString().contains('The record the application has, with its baseline, is as it was. Nothing was written')
        tree(dir) == before
    }

    void "--dry-run says what the record and its baseline would be, and writes nothing"() {
        given:
        EarlierApplication.copy(EarlierApplication.GRAILS_8_SHELL, dir)
        EarlierApplication.upgrade(dir)
        Map<String, String> before = tree(dir)

        when:
        int exit = migrate('--dry-run')

        then:
        exit == 0
        out.toString() =~ /\| The baseline would be the \d+ files this version generates/
        out.toString().readLines().last() == '| Nothing was written'
        tree(dir) == before
    }

    // a command that ended as it should, or what it said
    private boolean done(int exit) {
        assert exit == 0 && err.toString().isEmpty(): "${out}\n${err}"
        return true
    }

    private int migrate(String... arguments) {
        return CliRunner.run(ProjectMigrateCommand, beanContext, arguments)
    }

    private int show(String... arguments) {
        return CliRunner.run(ProjectShowCommand, beanContext, arguments)
    }

    private int health(String... arguments) {
        return CliRunner.run(ProjectHealthCommand, beanContext, arguments)
    }
}
