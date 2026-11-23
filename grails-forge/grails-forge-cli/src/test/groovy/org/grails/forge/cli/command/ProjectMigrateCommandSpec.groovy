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
import java.security.MessageDigest
import groovy.json.JsonSlurper
import org.apache.grails.forge.core.state.ProjectState
import org.grails.forge.application.ApplicationType
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.GormImpl
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.ServletImpl
import org.grails.forge.util.VersionInfo
import spock.lang.Unroll

/**
 * {@code project migrate}: the record of an application that was there before it had one, worked out
 * from the files of the project and from the command line, and what the other commands make of it.
 */
class ProjectMigrateCommandSpec extends CommandSpec {

    static final String SHELL_CONFIGURATION = '''\
---
grails:
    profile: web
    codegen:
        defaultPackage: com.example.legacy
    gorm:
        reactor:
            events: false
info:
    app:
        name: legacy
---
dataSource:
    pooled: true
'''

    static final String SHELL_BUILD = '''\
buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath "org.apache.grails:grails-gradle-plugins"
    }
}

version "0.1"
group "legacy"

apply plugin: "war"
apply plugin: "org.apache.grails.gradle.grails-web"
apply plugin: "org.apache.grails.gradle.grails-gsp"

dependencies {
    profile "org.apache.grails.profiles:web"
    implementation platform("org.apache.grails:grails-bom:$grailsVersion")
    implementation "org.apache.grails:grails-core"
    implementation "org.apache.grails:grails-data-hibernate5"
    implementation "org.apache.grails:grails-quartz"
    // implementation "org.apache.grails:grails-data-mongodb"
    runtimeOnly "org.postgresql:postgresql"
    testImplementation "org.testcontainers:testcontainers"
}
'''

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()
    File home
    String previousHome

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out))
        System.setErr(new PrintStream(err))
        // where a change of a project that was interrupted is looked for
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

    void "an application the Grails shell created gets a record from its profile, its configuration and its build, and no other file is touched"() {
        given:
        shellProject()
        Map<String, String> before = tree()

        when:
        int exit = ForgeCli.execute(beanContext, 'project', 'migrate')
        List<String> said = out.toString().readLines()
        ProjectState state = ProjectState.read(dir.toPath())

        then: 'what was found is said with where it was found, and what was assumed is said to be'
        exit == 0
        err.toString().isEmpty()
        said[0] == '| The record of the application legacy, as it is written to .grails'
        row(said, 'type') == ['web', '(the profile web, grails.profile of grails-app/conf/application.yml)']
        row(said, 'name') == ['legacy', '(rootProject.name of settings.gradle)']
        row(said, 'package') == ['com.example.legacy', '(grails.codegen.defaultPackage of grails-app/conf/application.yml)']
        row(said, 'jdk') == [String.valueOf(JdkVersion.DEFAULT_OPTION.majorVersion()), '(assumed)']
        row(said, 'servlet') == ['tomcat', '(assumed)']
        row(said, 'data') == ['hibernate5', '(the feature gorm-hibernate5)']
        row(said, 'reloading') == ['none', '(assumed)']
        said.contains('    grails-quartz (build.gradle declares org.apache.grails:grails-quartz)')
        said.contains('    postgres (build.gradle declares org.postgresql:postgresql)')
        said.any { it.startsWith('| Assumed, because nothing in the project says it: jdk (--jdk), servlet (--servlet), reloading (--reloading). ') }
        said.any { it.contains('run grails-forge-cli project migrate --force with the name and the package (com.example.shop), --type, --jdk, --servlet, --data, ' +
                '--reloading, --features for features that were not found and --exclude-features for features the application does not have') }
        said.any { it.startsWith('| The record was written: .grails/.gitignore, .grails/.gitattributes, .grails/baseline/index.yml, .grails/forge.lock.yml, ' +
                '.grails/project.yml. No other file of the project was changed.') }

        and: 'the record says that it was written for an application that was there, and has no baseline'
        state.manifest.generationState == 'untracked'
        !state.manifest.tracked
        state.manifest.applicationType == ApplicationType.WEB
        state.manifest.name == 'legacy'
        state.manifest.defaultPackage == 'com.example.legacy'
        state.manifest.grailsVersion == VersionInfo.getGrailsVersion()
        state.manifest.requestedFeatures.isEmpty()
        state.manifest.options.jdk == JdkVersion.DEFAULT_OPTION
        state.manifest.options.servlet == ServletImpl.TOMCAT
        state.manifest.options.data == GormImpl.HIBERNATE5
        state.manifest.options.reloading == DevelopmentReloading.NONE
        !state.manifest.options.subproject
        state.baseline.files.isEmpty()
        state.lock.featureNames.containsAll(['gorm-hibernate5', 'grails-quartz', 'postgres'])
        !state.lock.featureNames.contains('gorm-mongodb')

        and: 'only the record was written'
        tree().findAll { !it.key.startsWith('.grails') } == before
        tree().keySet().findAll { it.startsWith('.grails') } ==
                ['.grails', '.grails/.gitattributes', '.grails/.gitignore', '.grails/baseline', '.grails/baseline/index.yml', '.grails/forge.lock.yml', '.grails/project.yml'] as Set
    }

    void "the commands that create classes are there once the record is written, and write into the package of the record"() {
        given:
        shellProject()

        when: 'before there is a record'
        int exit = ForgeCli.execute(beanContext, 'create-controller', 'Book')

        then: 'the command says which command writes one'
        exit == 1
        err.toString().contains('create-controller is a command of a Grails project whose record is read')
        err.toString().contains('grails-forge-cli project migrate writes the record of an application that has none')
        !new File(dir, 'grails-app/controllers').exists()

        when:
        err.reset()
        int migrated = ForgeCli.execute(beanContext, 'project', 'migrate')
        out.reset()
        exit = ForgeCli.execute(beanContext, 'create-controller', 'Book')

        then:
        migrated == 0
        exit == 0
        err.toString().isEmpty()
        new File(dir, 'grails-app/controllers/com/example/legacy/BookController.groovy').text.contains('package com.example.legacy')
        new File(dir, 'src/test/groovy/com/example/legacy/BookControllerSpec.groovy').isFile()

        when: 'the feature grails-quartz was found in the build, so its command is there too'
        out.reset()
        exit = ForgeCli.execute(beanContext, 'create-job', 'Nightly')

        then:
        exit == 0
        new File(dir, 'grails-app/jobs/com/example/legacy/NightlyJob.groovy').isFile()

        when:
        out.reset()
        exit = ForgeCli.execute(beanContext, 'help')

        then:
        exit == 0
        out.toString().contains('create-domain-class')
        !err.toString().contains('Warning')
    }

    void "no feature is added to an application whose record was written for it, and it is not generated again: nothing is changed"() {
        given:
        shellProject()
        ForgeCli.execute(beanContext, 'project', 'migrate')
        Map<String, String> before = tree()
        out.reset()

        when:
        int exit = ForgeCli.execute(beanContext, (['feature', 'add', 'grails-web-console'] + options) as String[])

        then:
        exit == 1
        err.toString().contains('The record of the application was written for an application that was there, and not by the generator ' +
                '(generationState in .grails/project.yml is untracked, as project migrate writes it)')
        err.toString().contains('no feature is added to such an application by a command, and it is not generated again. Nothing was changed. ' +
                'Add to the build and the configuration by hand what the feature adds to an application that is created with it')
        tree() == before

        when:
        err.reset()
        exit = ForgeCli.execute(beanContext, 'project', 'regenerate', '--diff')

        then:
        exit == 1
        err.toString().contains('it is not generated again')
        tree() == before

        where:
        options << [[], ['--dry-run'], ['--diff']]
    }

    void "project show says of such a record what it is, in words and in the document"() {
        given:
        shellProject()
        ForgeCli.execute(beanContext, 'project', 'migrate', '--features', 'grails-web-console')
        out.reset()

        when:
        int exit = ForgeCli.execute(beanContext, 'project', 'show')
        List<String> said = out.toString().readLines()

        then:
        exit == 0
        err.toString().isEmpty()
        said[0] == '| What the application legacy is taken to be, from the record that was written for it in .grails'
        said.any { it ==~ / +state +untracked/ }
        said.any { it ==~ / +recorded by +grails-forge .*/ }
        said.any { it ==~ / +asked for +grails-web-console/ }
        said.any { it.startsWith('| The record was written for an application that was there, by project migrate, and not when the application was generated') }
        said[-2] == '| A new application of this type, with these options and features, is created with:'
        said[-1].startsWith('grails-forge-cli create-app com.example.legacy.legacy ')

        when:
        out.reset()
        exit = ForgeCli.execute(beanContext, 'project', 'show', '--json')
        Map document = (Map) new JsonSlurper().parseText(out.toString())

        then:
        exit == 0
        document.generationState == 'untracked'
        document.requestedFeatures*.name == ['grails-web-console']
        ((List<String>) document.notes).any { it.startsWith('The record was written for an application that was there') }
    }

    void "an application Forge of an earlier version generated gets its record from grails-forge-cli.yml, which is left where it is"() {
        given:
        new File(dir, 'grails-app/conf').mkdirs()
        new File(dir, 'settings.gradle').text = "rootProject.name = 'shop'\n"
        new File(dir, 'build.gradle').text = 'plugins {\n    id "org.apache.grails.gradle.grails-web"\n}\n'
        new File(dir, 'grails-forge-cli.yml').text = 'applicationType: rest_api\ndefaultPackage: com.acme.shop\nreloading: devtools\nsourceLanguage: groovy\n' +
                'features: [gorm-hibernate5, grails-quartz, a-feature-of-grails-7, spring-boot-starter-jetty]\n'
        Map<String, String> before = tree()

        when:
        int exit = ForgeCli.execute(beanContext, 'project', 'migrate')
        List<String> said = out.toString().readLines()
        ProjectState state = ProjectState.read(dir.toPath())

        then:
        exit == 0
        row(said, 'type') == ['rest-api', '(applicationType of grails-forge-cli.yml)']
        row(said, 'package') == ['com.acme.shop', '(defaultPackage of grails-forge-cli.yml)']
        row(said, 'servlet') == ['jetty', '(the feature spring-boot-starter-jetty)']
        row(said, 'reloading') == ['devtools', '(reloading of grails-forge-cli.yml)']
        said.contains('    grails-quartz (grails-forge-cli.yml)')
        said.contains('| grails-forge-cli.yml names a feature this version of Grails does not have for the type, which is not in the record: a-feature-of-grails-7')
        said.contains('| grails-forge-cli.yml was read and is left as it is. With a record under .grails the commands go by that, and warn when grails-forge-cli.yml ' +
                'does not name the same package and features: remove the file when nothing else reads it')
        state.manifest.applicationType == ApplicationType.REST_API
        state.manifest.options.servlet == ServletImpl.JETTY
        state.manifest.options.reloading == DevelopmentReloading.DEVTOOLS
        state.lock.featureNames.containsAll(['gorm-hibernate5', 'grails-quartz', 'spring-boot-starter-jetty'])
        !state.lock.featureNames.contains('a-feature-of-grails-7')
        tree().findAll { !it.key.startsWith('.grails') } == before

        when: 'both records are there, and say the same of the package and the features'
        out.reset()
        new File(dir, 'grails-forge-cli.yml').text = 'applicationType: rest_api\ndefaultPackage: com.acme.shop\n' +
                "features: [${state.lock.featureNames.join(', ')}]\n"
        exit = ForgeCli.execute(beanContext, 'create-service', 'Stock')

        then: 'the commands go by the record under .grails, without a word'
        exit == 0
        err.toString().isEmpty()
        new File(dir, 'grails-app/services/com/acme/shop/StockService.groovy').isFile()
    }

    @Unroll
    void "the type of a build that names no profile is told by its Grails Gradle plugins: #plugins is #type"() {
        given:
        new File(dir, 'grails-app/init/org/acme').mkdirs()
        new File(dir, 'grails-app/init/org/acme/Application.groovy').text = 'package org.acme\n\nclass Application {\n}\n'
        new File(dir, 'settings.gradle').text = 'rootProject.name = "audit"\n'
        new File(dir, 'build.gradle').text = 'plugins {\n' + plugins.collect { "    id \"${it}\"\n" }.join('') + '}\n'

        when:
        int exit = ForgeCli.execute(beanContext, 'project', 'migrate')
        List<String> said = out.toString().readLines()

        then:
        exit == 0
        row(said, 'type')[0] == type
        row(said, 'package') == ['org.acme', '(the package of the Application class)']
        ProjectState.read(dir.toPath()).manifest.applicationType.name() == type.toUpperCase().replace('-', '_')
        said.any { it.contains('Assumed, because nothing in the project says it: the type (--type)') } == assumed
        row(said, 'servlet')[0] == servlet

        where:
        plugins                                                                             || type         | assumed | servlet
        ['org.apache.grails.gradle.grails-web', 'org.apache.grails.gradle.grails-gsp']       || 'web'        | false   | 'tomcat'
        ['org.apache.grails.gradle.grails-web']                                              || 'rest-api'   | false   | 'tomcat'
        ['org.apache.grails.gradle.grails-plugin', 'org.apache.grails.gradle.grails-gsp']    || 'web-plugin' | false   | 'tomcat'
        ['org.apache.grails.gradle.grails-plugin']                                           || 'plugin'     | true    | 'none'
        ['org.grails.grails-plugin']                                                         || 'plugin'     | true    | 'none'
    }

    void "a plugin the Grails shell created is a plugin by its profile, of whichever kind the profile was"() {
        given:
        new File(dir, 'grails-app/conf').mkdirs()
        new File(dir, 'grails-app/conf/application.yml').text = "grails.profile: 'rest-api-plugin' # as the shell wrote it\ngrails.codegen.defaultPackage: org.acme.audit\n"
        new File(dir, 'build.gradle').text = 'group = "org.acme"\napply plugin: "org.apache.grails.gradle.grails-plugin"\n' +
                'dependencies {\n    profile "org.apache.grails.profiles:rest-api-plugin"\n}\n'

        when:
        int exit = ForgeCli.execute(beanContext, 'project', 'migrate', 'audit')
        List<String> said = out.toString().readLines()
        ProjectState state = ProjectState.read(dir.toPath())

        then:
        exit == 0
        row(said, 'type') == ['rest-api-plugin', '(the profile rest-api-plugin, grails.profile of grails-app/conf/application.yml)']
        row(said, 'name') == ['audit', '(given)']
        row(said, 'layout') == ['subproject', '(the project has no settings.gradle of its own)']
        state.manifest.applicationType == ApplicationType.REST_API_PLUGIN
        state.manifest.defaultPackage == 'org.acme.audit'
        state.manifest.options.subproject
    }

    void "what is given on the command line counts for more than what the files say, and is recorded as asked for"() {
        given:
        shellProject()

        when:
        int exit = ForgeCli.execute(beanContext, 'project', 'migrate', 'org.other.store', '--type', 'rest-api', '--jdk', '25', '--servlet', 'undertow',
                '--data', 'hibernate7', '--reloading', 'devtools', '--features', 'grails-web-console,cache', '--exclude-features', 'postgres')
        List<String> said = out.toString().readLines()
        ProjectState state = ProjectState.read(dir.toPath())

        then:
        exit == 0
        row(said, 'type') == ['rest-api', '(given with --type)']
        row(said, 'name') == ['store', '(given)']
        row(said, 'package') == ['org.other', '(given)']
        row(said, 'jdk') == ['25', '(given with --jdk)']
        row(said, 'servlet') == ['undertow', '(given with --servlet)']
        row(said, 'data') == ['hibernate7', '(given with --data)']
        row(said, 'reloading') == ['devtools', '(given with --reloading)']
        said.contains('    grails-web-console (given with --features)')
        said.contains('    gorm-hibernate7 (the option --data)')
        said.contains('| Left out with --exclude-features: postgres')
        said.any { it.startsWith('| Nothing was assumed. ') }
        state.manifest.applicationType == ApplicationType.REST_API
        state.manifest.name == 'store'
        state.manifest.defaultPackage == 'org.other'
        state.manifest.options.jdk == JdkVersion.JDK_25
        state.manifest.options.servlet == ServletImpl.UNDERTOW
        state.manifest.options.data == GormImpl.HIBERNATE7
        state.manifest.options.reloading == DevelopmentReloading.DEVTOOLS
        state.manifest.requestedFeatures*.name == ['cache', 'grails-web-console']
        state.manifest.excludedFeatures*.name == ['postgres']
        state.lock.featureNames.containsAll(['cache', 'grails-web-console', 'gorm-hibernate7', 'grails-undertow', 'spring-boot-devtools', 'grails-quartz'])
        !state.lock.featureNames.contains('postgres')
        !state.lock.featureNames.contains('gorm-hibernate5')
    }

    void "--dry-run says what the record would be and writes nothing"() {
        given:
        shellProject()
        Map<String, String> before = tree()

        when:
        int exit = ForgeCli.execute(beanContext, 'project', 'migrate', '--dry-run')
        List<String> said = out.toString().readLines()

        then:
        exit == 0
        said[0] == '| The record of the application legacy, as it would be written to .grails'
        said[-1] == '| Nothing was written'
        tree() == before
    }

    void "a record this command wrote is written again with --force only, and the record of a generated application never"() {
        given:
        shellProject()
        ForgeCli.execute(beanContext, 'project', 'migrate')
        Map<String, String> before = tree()
        out.reset()

        when:
        int exit = ForgeCli.execute(beanContext, 'project', 'migrate', '--type', 'rest-api')

        then:
        exit == 1
        err.toString().readLines() == ['| Error The application already has a record that grails-forge-cli project migrate wrote (.grails/project.yml). ' +
                                               'Run again with --force to write it anew, or with --dry-run to see what it would be. Nothing was written']
        out.toString().isEmpty()
        tree() == before

        when:
        err.reset()
        exit = ForgeCli.execute(beanContext, 'project', 'migrate', '--type', 'rest-api', '--force')

        then:
        exit == 0
        err.toString().isEmpty()
        ProjectState.read(dir.toPath()).manifest.applicationType == ApplicationType.REST_API
        tree().findAll { !it.key.startsWith('.grails') } == before.findAll { !it.key.startsWith('.grails') }
    }

    @Unroll
    void "the record of an application this version generated is not written over, and is this version's already: project migrate #options"() {
        given:
        ForgeCli.execute(beanContext, 'create-app', 'com.example.shop')
        File generated = new File(dir, 'shop')
        System.setProperty('user.dir', generated.absolutePath)
        Map<String, String> before = tree(generated)
        out.reset()

        when:
        int exit = ForgeCli.execute(beanContext, (['project', 'migrate'] + options) as String[])

        then: 'nothing is worked out from the files: the record says that there is nothing to move to this version'
        exit == 0
        err.toString().isEmpty()
        out.toString().readLines() == ["| The record of the application shop was written by this build of the command line, grails-forge ${VersionInfo.version} for " +
                                               "Grails ${VersionInfo.grailsVersion}: it is that of this version already, and there is nothing to move. Nothing was written".toString()]
        tree(generated) == before

        where:
        options << [[], ['--force'], ['--dry-run']]
    }

    @Unroll
    void "what is left of a record, and a record that cannot be read, is not written over: #what"() {
        given:
        shellProject()
        ForgeCli.execute(beanContext, 'project', 'migrate')
        change.call(dir)
        Map<String, String> before = tree()
        out.reset()

        when:
        int exit = ForgeCli.execute(beanContext, 'project', 'migrate', '--force')

        then:
        exit == 1
        err.toString().contains(message)
        err.toString().contains('Nothing was written')
        out.toString().isEmpty()
        tree() == before

        where:
        what                    | change                                                                       || message
        'no manifest'           | { File project -> new File(project, '.grails/project.yml').delete() }         || 'The project has .grails/forge.lock.yml and .grails/baseline and no .grails/project.yml'
        'a manifest by hand'    | { File project -> new File(project, '.grails/project.yml') << '# mine\n' }   || 'The project has a record under .grails that cannot be read'
    }

    @Unroll
    void "what is found in the build of an application of #command #options is nothing the application does not have"() {
        given: 'an application this version generates, which has the record of every feature it has'
        ForgeCli.execute(beanContext, ([command, 'com.example.shop'] + options) as String[])
        File generated = new File(dir, 'shop')
        ProjectState generatedState = ProjectState.read(generated.toPath())
        new File(generated, '.grails').deleteDir()
        System.setProperty('user.dir', generated.absolutePath)
        out.reset()

        when: 'its record is written from its files alone'
        int exit = ForgeCli.execute(beanContext, 'project', 'migrate')
        ProjectState state = ProjectState.read(generated.toPath())

        then: 'the type, the name, the package and the features that were found are those it was generated with'
        exit == 0
        state.manifest.applicationType == generatedState.manifest.applicationType
        state.manifest.name == 'shop'
        state.manifest.defaultPackage == 'com.example'
        state.manifest.options.data == generatedState.manifest.options.data
        (state.lock.featureNames - generatedState.lock.featureNames).isEmpty()
        state.lock.featureNames.containsAll(found)

        where:
        command             | options                                                          || found
        'create-app'        | []                                                               || ['gorm-hibernate5', 'h2', 'gsp', 'scaffolding']
        'create-app'        | ['--data', 'hibernate7', '--features', 'postgres,grails-quartz'] || ['gorm-hibernate7', 'postgres', 'grails-quartz']
        'create-restapi'    | ['--data', 'mongodb', '--servlet', 'undertow']                   || ['gorm-mongodb', 'grails-undertow']
        'create-web-plugin' | []                                                               || ['gsp']
        'create-plugin'     | []                                                               || []
    }

    void "what tells Git how to keep the record is left as it is when the project has it"() {
        given:
        shellProject()
        new File(dir, '.grails').mkdirs()
        new File(dir, '.grails/.gitignore').text = '/run/\n'

        when:
        int exit = ForgeCli.execute(beanContext, 'project', 'migrate')

        then:
        exit == 0
        new File(dir, '.grails/.gitignore').text == '/run/\n'
        out.toString().contains('| The record was written: .grails/.gitattributes, .grails/baseline/index.yml, .grails/forge.lock.yml, .grails/project.yml. ')
    }

    @Unroll
    void "a project the type or the package of cannot be worked out is not given a record by a guess: #what"() {
        given:
        new File(dir, 'grails-app').mkdirs()
        files.each { String path, String text -> new File(dir, path).text = text }
        Map<String, String> before = tree()

        when:
        int exit = ForgeCli.execute(beanContext, 'project', 'migrate')

        then:
        exit == 1
        err.toString().contains(message)
        err.toString().contains('Nothing was written')
        tree() == before

        where:
        what                    | files                                                                                              || message
        'no build file'         | [:]                                                                                                || 'What type of application this is could not be worked out: The project has no build.gradle that could be read. Say it with --type, which is one of web, rest-api, web-plugin, plugin, rest-api-plugin'
        'a profile of another'  | ['build.gradle': 'dependencies {\n    profile "com.acme.profiles:intranet"\n}\n']                || 'The profile com.acme.profiles:intranet of build.gradle is not one of Grails'
        'a profile without type' | ['build.gradle': 'dependencies {\n    profile "org.grails.profiles:angular"\n}\n']               || 'The profile angular (the profile dependency of build.gradle) has no type of application in this version'
        'no package'            | ['build.gradle': 'apply plugin: "org.apache.grails.gradle.grails-web"\n']                         || 'The package the classes of the application are created in could not be worked out'
        'a name that is none'   | ['build.gradle': 'group "org.acme"\napply plugin: "org.apache.grails.gradle.grails-web"\n',
                                   'settings.gradle': "rootProject.name = 'a/b'\n"]                                                   || "is not a name an application can be created with"
    }

    void "a project that is on Grails 7 or 8 gets its record, and the other commands of a project once it is on Grails 9"() {
        given:
        shellProject()
        new File(dir, 'gradle.properties').text = "grailsVersion=${version}\n"
        Map<String, String> before = tree()

        when:
        int exit = ForgeCli.execute(beanContext, 'project', 'migrate')

        then: 'the record is written, and what is said says which version the project is on'
        exit == 0
        err.toString().isEmpty()
        out.toString().contains("The project is on Grails ${version} (gradle.properties): the record is that of an application of Grails 9")
        new File(dir, '.grails/project.yml').file
        tree().findAll { it.key != '.grails' && !it.key.startsWith('.grails/') } == before

        when: 'a command that reads the record, while the project is on the earlier version'
        out.reset()
        err.reset()
        exit = ForgeCli.execute(beanContext, 'create-controller', 'Book')

        then:
        exit == 1
        err.toString().contains("This project is on Grails ${version} (gradle.properties)")
        !new File(dir, 'grails-app/controllers').exists()

        when: 'a command that creates another application has nothing to do with the project it is typed in'
        out.reset()
        err.reset()
        exit = ForgeCli.execute(beanContext, 'create-app', '--list-features')

        then:
        exit == 0
        !(out.toString() + err.toString()).contains('This project is on Grails')

        when: 'the project is on Grails 9'
        new File(dir, 'gradle.properties').text = 'grailsVersion=9.0.0\n'
        out.reset()
        err.reset()
        exit = ForgeCli.execute(beanContext, 'create-controller', 'Book')

        then:
        exit == 0
        !(out.toString() + err.toString()).contains('This project is on Grails')

        where:
        version << ['7.0.4', '8.1.2']
    }

    @Unroll
    void "project migrate #arguments is a mistake in the command line"() {
        given:
        shellProject()
        Map<String, String> before = tree()

        when:
        int exit = ForgeCli.execute(beanContext, (['project', 'migrate'] + arguments) as String[])

        then:
        exit == 2
        err.toString().readLines()[0].contains(message)
        tree() == before

        where:
        arguments                                                   || message
        ['--type', 'angular']                                       || 'angular is no type of application: --type is one of web, rest-api, web-plugin, plugin, rest-api-plugin'
        ['--features', 'no-such-feature']                           || 'no-such-feature, given to --features, is no feature of Grails for this type of application'
        ['--features', 'org.acme:audit:1.0']                        || 'org.acme:audit:1.0, given to --features, is no feature of Grails'
        ['--exclude-features', 'no-such-feature']                   || 'no-such-feature, given to --exclude-features, is no feature of Grails'
        ['--features', 'cache', '--exclude-features', 'cache']      || 'cache cannot be given to --features and to --exclude-features both'
        ['--jdk', '8']                                              || '--jdk is one of 21, 25, 26'
        ['--servlet', 'websphere']                                  || '--servlet'
        ['--no-such-option']                                        || 'project migrate has no option --no-such-option'
    }

    void "project migrate outside a Grails project says that there is none"() {
        when:
        int exit = ForgeCli.execute(beanContext, 'project', 'migrate')

        then:
        exit == 1
        err.toString().readLines() == ["| Error project migrate is a command of a Grails project; there is none in ${dir.canonicalPath} or above it".toString()]
        dir.list() as List == []
    }

    void "a record that names a profile and nothing else of the application is read, and says which command writes the record of this version"() {
        given:
        new File(dir, 'grails-app').mkdirs()
        new File(dir, 'grails-forge-cli.yml').text = 'profile: web\ndefaultPackage: example\n'

        when:
        int exit = ForgeCli.execute(beanContext, 'help')

        then:
        exit == 0
        out.toString().contains('create-controller')
        err.toString().readLines() == ['| Warning grails-forge-cli.yml names a profile, as the record of an earlier command line did, and not the features of the ' +
                                               'application: the commands take the application to have the features an application of the type web has by default. ' +
                                               'project migrate writes the record of this version, from what the project has']

        when: 'a profile there is no type of application for'
        out.reset()
        err.reset()
        new File(dir, 'grails-forge-cli.yml').text = 'profile: angular\ndefaultPackage: example\n'
        exit = ForgeCli.execute(beanContext, 'help')

        then:
        exit == 0
        !out.toString().contains('create-controller')
        err.toString().readLines() == ['| Warning grails-forge-cli.yml: the profile angular has no type of application in this version of Grails; ' +
                                               'the commands that create the classes of an application are not there without it']
    }

    void "a dependency whose group and name are said each by its own is a dependency"() {
        given:
        shellProject()
        new File(dir, 'build.gradle').text = '''\
apply plugin: "org.apache.grails.gradle.grails-web"
apply plugin: "org.apache.grails.gradle.grails-gsp"

dependencies {
    implementation group: 'org.apache.grails', name: 'grails-data-hibernate5'
    implementation group: "org.apache.grails", name: "grails-quartz", version: "5.0.0"
    runtimeOnly(group: 'org.postgresql', name: 'postgresql')
    testImplementation name: 'no-group-is-no-dependency'
}
'''

        when:
        int exit = ForgeCli.execute(beanContext, 'project', 'migrate', '--dry-run')
        List<String> said = out.toString().readLines()

        then:
        exit == 0
        row(said, 'data') == ['hibernate5', '(the feature gorm-hibernate5)']
        said.contains('    gorm-hibernate5 (build.gradle declares org.apache.grails:grails-data-hibernate5)')
        said.contains('    grails-quartz (build.gradle declares org.apache.grails:grails-quartz)')
        said.contains('    postgres (build.gradle declares org.postgresql:postgresql)')
    }

    void "what a build file comments out is no plugin and no dependency of the application, and what stands beside a comment is one"() {
        given:
        shellProject()
        new File(dir, 'grails-app/conf/application.yml').text = 'grails.codegen.defaultPackage: com.example.legacy\n'
        new File(dir, 'build.gradle').text = '''\
/*
 * apply plugin: "org.apache.grails.gradle.grails-plugin"
 */
apply plugin: "org.apache.grails.gradle.grails-web" // not "org.apache.grails.gradle.grails-gsp"
/* apply plugin: "org.apache.grails.gradle.grails-gsp" */

repositories {
    maven { url "https://repo.example.org/maven" } /* the address has two slashes, and is no comment
    implementation "org.apache.grails:grails-data-mongodb"
    group "com.example.commented" */
}

dependencies {
    /*
    implementation "org.apache.grails:grails-quartz"
    runtimeOnly group: 'org.postgresql', name: 'postgresql'
    */
    /* implementation "org.apache.grails:grails-data-neo4j" */ implementation "org.apache.grails:grails-data-hibernate5"
    // implementation "org.apache.grails:grails-data-hibernate7"
    runtimeOnly "com.h2database:h2" // implementation "org.apache.grails:grails-quartz"
}
'''

        when:
        int exit = ForgeCli.execute(beanContext, 'project', 'migrate', '--dry-run')
        List<String> said = out.toString().readLines()

        then: 'the type is that of the plugins that are applied'
        exit == 0
        row(said, 'type') == ['rest-api', '(the Gradle plugin grails-web of build.gradle, without grails-gsp)']
        row(said, 'package') == ['com.example.legacy', '(grails.codegen.defaultPackage of grails-app/conf/application.yml)']

        and: 'and the features are those of the dependencies that are declared'
        row(said, 'data') == ['hibernate5', '(the feature gorm-hibernate5)']
        said.contains('    gorm-hibernate5 (build.gradle declares org.apache.grails:grails-data-hibernate5)')
        said.contains('    h2 (build.gradle declares com.h2database:h2)')
        !said.any { it.startsWith('    grails-quartz ') || it.startsWith('    postgres ') || it.startsWith('    gorm-mongodb ') || it.startsWith('    gorm-neo4j ') ||
                it.startsWith('    gorm-hibernate7 ') }
    }

    void "a build that is written in Kotlin is read from build.gradle.kts and settings.gradle.kts, which are named as where things were found"() {
        given:
        new File(dir, 'grails-app/conf').mkdirs()
        new File(dir, 'build.gradle.kts').text = '''\
plugins {
    id("org.apache.grails.gradle.grails-web")
    id("org.apache.grails.gradle.grails-gsp")
}

group = "com.example.kotlin"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

dependencies {
    implementation(platform("org.apache.grails:grails-bom:$grailsVersion"))
    implementation("org.apache.grails:grails-data-hibernate5")
    implementation("org.apache.grails:grails-quartz")
    runtimeOnly(group = "org.postgresql", name = "postgresql")
    // implementation("org.apache.grails:grails-data-mongodb")
}
'''
        new File(dir, 'settings.gradle.kts').text = '// rootProject.name = "commented"\nrootProject.name = "kshop"\n'
        Map<String, String> before = tree()

        when:
        int exit = ForgeCli.execute(beanContext, 'project', 'migrate')
        List<String> said = out.toString().readLines()

        then:
        exit == 0
        err.toString().isEmpty()
        row(said, 'type') == ['web', '(the Gradle plugins grails-web and grails-gsp of build.gradle.kts)']
        row(said, 'name') == ['kshop', '(rootProject.name of settings.gradle.kts)']
        row(said, 'package') == ['com.example.kotlin', '(group of build.gradle.kts)']
        row(said, 'jdk') == ['25', '(the version of Java build.gradle.kts names)']
        said.contains('    grails-quartz (build.gradle.kts declares org.apache.grails:grails-quartz)')
        said.contains('    postgres (build.gradle.kts declares org.postgresql:postgresql)')
        !said.any { it.startsWith('    gorm-mongodb ') }
        !said.any { it.startsWith('    layout ') }
        !said.any { it.contains('was not read') }

        and: 'the record is that of such an application, and no other file is touched'
        ProjectState state = ProjectState.read(dir.toPath())
        state.manifest.name == 'kshop'
        state.manifest.defaultPackage == 'com.example.kotlin'
        state.manifest.options.jdk.majorVersion() == 25
        !state.manifest.options.subproject
        state.lock.featureNames.containsAll(['gorm-hibernate5', 'grails-quartz', 'postgres'])
        tree().findAll { !it.key.startsWith('.grails') } == before
    }

    void "a build.gradle that is there is the one that is read, also where a build.gradle.kts is beside it"() {
        given:
        shellProject()
        new File(dir, 'build.gradle.kts').text = 'dependencies {\n    implementation("org.apache.grails:grails-data-mongodb")\n}\n'

        when:
        int exit = ForgeCli.execute(beanContext, 'project', 'migrate', '--dry-run')
        List<String> said = out.toString().readLines()

        then:
        exit == 0
        said.contains('    grails-quartz (build.gradle declares org.apache.grails:grails-quartz)')
        !said.any { it.contains('build.gradle.kts') || it.startsWith('    gorm-mongodb ') }
    }

    void "what the build file has that was not read is said: another build file, a version catalog, and what it declares for other projects"() {
        given: 'a project whose type and package are said by its build file alone'
        new File(dir, 'grails-app').mkdirs()
        new File(dir, 'settings.gradle').text = "rootProject.name = 'legacy'\n"
        new File(dir, 'build.gradle').text = '''\
apply plugin: "org.apache.grails.gradle.grails-web"
apply plugin: "org.apache.grails.gradle.grails-gsp"
apply from: "gradle/dependencies.gradle"
apply from: rootProject.file("gradle/publishing.gradle")

subprojects {
    group "com.example.modules"
    apply plugin: "org.apache.grails.gradle.grails-plugin"
    dependencies {
        implementation "org.apache.grails:grails-quartz"
        implementation group: 'org.apache.grails', name: 'grails-data-mongodb'
    }
}

configure(subprojects.findAll { it.name.startsWith('web') }) { dependencies { runtimeOnly "org.postgresql:postgresql" } }

group "com.example"

allprojects {
    dependencies {
        implementation "org.apache.grails:grails-data-hibernate5"
    }
}

dependencies {
    implementation libs.grails.quartz
    implementation(libs.spring.security)
    runtimeOnly "com.h2database:h2"
}
'''

        when:
        int exit = ForgeCli.execute(beanContext, 'project', 'migrate', '--dry-run')
        List<String> said = out.toString().readLines()

        then: 'what is declared for every project, and for this one, is found'
        exit == 0
        row(said, 'type') == ['web', '(the Gradle plugins grails-web and grails-gsp of build.gradle)']
        said.contains('    gorm-hibernate5 (build.gradle declares org.apache.grails:grails-data-hibernate5)')
        said.contains('    h2 (build.gradle declares com.h2database:h2)')

        and: 'what is declared for other projects is not taken for the application'
        row(said, 'package') == ['com.example', '(group of build.gradle)']
        !said.any { it.startsWith('    grails-quartz ') || it.startsWith('    gorm-mongodb ') || it.startsWith('    postgres ') }

        and: 'and each thing that was not read is said in a line of its own'
        said.contains('| build.gradle applies 2 other build files (apply from), which were not read')
        said.contains('| build.gradle names 2 dependencies by a version catalog (libs.), which was not read: what they are is not known here')
        said.contains('| build.gradle declares dependencies for other projects (in a subprojects, configure or project block), which are not those of this application ' +
                'and were not counted')
    }

    void "a project that is part of a build above it, and one without a build file, are said not to have been read there"() {
        given: 'a project with no settings and no build file of its own, whose type and name are given'
        new File(dir, 'grails-app').mkdirs()

        when:
        int exit = ForgeCli.execute(beanContext, 'project', 'migrate', 'com.example.shop', '--type', 'web', '--dry-run')
        List<String> said = out.toString().readLines()

        then:
        exit == 0
        row(said, 'layout') == ['subproject', '(the project has no settings.gradle of its own)']
        said.contains('| The 0 features of Grails it is taken to have: none was found')
        said.contains('| The project has no build.gradle or build.gradle.kts that could be read, so no feature was looked for in its build')
        said.contains('| The project has no settings.gradle of its own, so it is part of a build above it: what that build declares for it, in a subprojects or ' +
                'allprojects block or in a convention plugin, was not read')

        when: 'the same with one applied file and one dependency'
        out.reset()
        new File(dir, 'build.gradle').text = 'apply from: "../gradle/grails.gradle"\ndependencies {\n    implementation libs.grails.core\n}\n'
        exit = ForgeCli.execute(beanContext, 'project', 'migrate', 'com.example.shop', '--type', 'web', '--dry-run')
        said = out.toString().readLines()

        then:
        exit == 0
        said.contains('| build.gradle applies another build file (apply from), which was not read')
        said.contains('| build.gradle names a dependency by a version catalog (libs.), which was not read: what it is is not known here')
        !said.any { it.contains('that could be read') }
    }

    @Unroll
    void "an earlier record that #what is said to be, and the record is worked out from the rest of the project"() {
        given:
        shellProject()
        new File(dir, 'grails-forge-cli.yml').setText(record, 'UTF-8')

        when:
        int exit = ForgeCli.execute(beanContext, 'project', 'migrate', '--dry-run')
        List<String> said = out.toString().readLines()

        then:
        exit == 0
        row(said, 'type') == type
        said.any { it.startsWith('| ') && it.contains(note) }

        where:
        what                           | record                                                    || type                                                                          | note
        'cannot be read'               | 'features: [a\n'                                          || ['web', '(the profile web, grails.profile of grails-app/conf/application.yml)'] | 'grails-forge-cli.yml is not YAML that can be read: '
        'names a type that is none'    | 'applicationType: nonsense\ndefaultPackage: com.other\n'  || ['web', '(the profile web, grails.profile of grails-app/conf/application.yml)'] | 'applicationType of grails-forge-cli.yml is nonsense, which is no type of application'
        'names a profile'              | 'profile: rest-api\ndefaultPackage: com.other\n'          || ['rest-api', '(the profile rest-api, profile of grails-forge-cli.yml)']        | 'grails-forge-cli.yml was read and is left as it is'
        'names a profile of no type'   | 'profile: angular\ndefaultPackage: com.other\n'           || ['web', '(the profile web, grails.profile of grails-app/conf/application.yml)'] | 'The profile angular (profile of grails-forge-cli.yml) has no type of application in this version'
    }

    void "the version of Java the build names is that of the record when this version generates for it, and is said not to be otherwise"() {
        given:
        shellProject()
        new File(dir, 'build.gradle') << '\njava {\n    toolchain {\n        languageVersion = JavaLanguageVersion.of(25)\n    }\n}\n'

        when:
        int exit = ForgeCli.execute(beanContext, 'project', 'migrate', '--dry-run')

        then:
        exit == 0
        row(out.toString().readLines(), 'jdk') == ['25', '(the version of Java build.gradle names)']

        when:
        out.reset()
        new File(dir, 'build.gradle').text = SHELL_BUILD + '\nsourceCompatibility = 11\n'
        exit = ForgeCli.execute(beanContext, 'project', 'migrate', '--dry-run')
        List<String> said = out.toString().readLines()

        then:
        exit == 0
        row(said, 'jdk') == [String.valueOf(JdkVersion.DEFAULT_OPTION.majorVersion()), '(assumed)']
        said.contains('| build.gradle names Java 11, which this version of Grails does not generate for')
    }

    void "a record that cannot be written is said not to be, and nothing is left of it"() {
        given: 'a file where the directory of the record goes'
        shellProject()
        new File(dir, '.grails').text = 'not a directory'
        Map<String, String> before = tree()

        when:
        int exit = ForgeCli.execute(beanContext, 'project', 'migrate')

        then:
        exit == 1
        err.toString().readLines().size() == 1
        err.toString().startsWith('| Error Cannot write .grails/')
        err.toString().trim().endsWith(': .grails is a file where a directory is needed')
        !out.toString().contains('The record was written')
        tree() == before
    }

    @Unroll
    void "the servlet of an application is the one whose starter its build declares: #declared"() {
        given: 'the build of an application the Grails shell created, which declares the starter of its servlet container'
        shellProject()
        new File(dir, 'build.gradle').text = SHELL_BUILD.replace('    implementation "org.apache.grails:grails-core"\n',
                '    implementation "org.apache.grails:grails-core"\n' + declared.collect { String each -> "    implementation \"${each}\"\n" }.join(''))

        when:
        int exit = ForgeCli.execute(beanContext, 'project', 'migrate')
        List<String> said = out.toString().readLines()
        ProjectState state = ProjectState.read(dir.toPath())

        then:
        exit == 0
        err.toString().isEmpty()
        row(said, 'servlet') == [servlet, where]
        said.any { it.startsWith("| Assumed, because nothing in the project says it: ${assumed}. ") }
        state.manifest.options.servlet == ServletImpl.valueOf(servlet.toUpperCase(Locale.ROOT))
        notes.every { String note -> said.contains('| ' + note) }

        where:
        declared                                                    | servlet    | where                                                                             | assumed
        ['org.springframework.boot:spring-boot-starter-tomcat']     | 'tomcat'   | '(build.gradle declares org.springframework.boot:spring-boot-starter-tomcat)'     | 'jdk (--jdk), reloading (--reloading)'
        ['org.springframework.boot:spring-boot-starter-jetty']      | 'jetty'    | '(the feature spring-boot-starter-jetty)'                                         | 'jdk (--jdk), reloading (--reloading)'
        ['org.springframework.boot:spring-boot-starter-undertow']   | 'undertow' | '(build.gradle declares org.springframework.boot:spring-boot-starter-undertow)'   | 'jdk (--jdk), reloading (--reloading)'
        ['org.apache.grails:grails-undertow']                       | 'undertow' | '(the feature grails-undertow)'                                                   | 'jdk (--jdk), reloading (--reloading)'
        ['org.springframework.boot:spring-boot-starter-tomcat',
         'org.springframework.boot:spring-boot-starter-undertow']   | 'tomcat'   | '(assumed)'                                                                       | 'jdk (--jdk), servlet (--servlet), reloading (--reloading)'
        []                                                          | 'tomcat'   | '(assumed)'                                                                       | 'jdk (--jdk), servlet (--servlet), reloading (--reloading)'
        notes = declared.size() < 2 ? [] : ['build.gradle declares the starters of more than one servlet container ' +
                '(org.springframework.boot:spring-boot-starter-tomcat, org.springframework.boot:spring-boot-starter-undertow), so the servlet was not taken from them']
    }

    void "the build of a Grails 8 application the shell created names its servlet container by its starter, and so does a Kotlin build"() {
        given:
        new File(dir, 'grails-app/conf').mkdirs()
        new File(dir, 'grails-app/conf/application.yml').text = SHELL_CONFIGURATION
        new File(dir, 'settings.gradle').text = "rootProject.name = 'legacy'\n"
        new File(dir, 'gradle.properties').text = 'grailsVersion=8.0.0\n'
        new File(dir, 'build.gradle').text = '''\
            plugins {
                id "groovy"
                id "war"
                id "org.apache.grails.gradle.grails-web"
                id "org.apache.grails.gradle.grails-gsp"
            }

            group = "com.example.legacy"

            dependencies {
                profile "org.apache.grails.profiles:web"
                implementation platform("org.apache.grails:grails-bom:$grailsVersion")
                implementation "org.apache.grails:grails-core"
                implementation "org.springframework.boot:spring-boot-autoconfigure"
                implementation "org.springframework.boot:spring-boot-starter"
                implementation "org.springframework.boot:spring-boot-starter-actuator"
                implementation "org.springframework.boot:spring-boot-starter-logging"
                implementation "org.springframework.boot:spring-boot-starter-tomcat"
                implementation "org.springframework.boot:spring-boot-starter-validation"
                implementation "org.apache.grails:grails-data-hibernate5"
            }
            '''.stripIndent()

        when:
        int exit = ForgeCli.execute(beanContext, 'project', 'migrate', '--dry-run')

        then:
        exit == 0
        row(out.toString().readLines(), 'servlet') == ['tomcat', '(build.gradle declares org.springframework.boot:spring-boot-starter-tomcat)']

        when: 'a plugin, which is generated without a servlet container that is Tomcat, declares the starter of Tomcat'
        out.reset()
        new File(dir, 'grails-app/conf/application.yml').text = 'grails:\n    profile: plugin\n    codegen:\n        defaultPackage: com.example.legacy\n'
        new File(dir, 'build.gradle').text = new File(dir, 'build.gradle').text.replace('grails-web', 'grails-plugin').replace('profiles:web', 'profiles:plugin')
        exit = ForgeCli.execute(beanContext, 'project', 'migrate', '--dry-run')
        List<String> said = out.toString().readLines()

        then: 'the servlet is not taken from it, and that is said'
        exit == 0
        row(said, 'type') == ['plugin', '(the profile plugin, grails.profile of grails-app/conf/application.yml)']
        row(said, 'servlet') == ['none', '(assumed)']
        said.contains('| build.gradle declares org.springframework.boot:spring-boot-starter-tomcat, the starter of the servlet container tomcat, ' +
                'which an application of the type plugin is not generated with, so the servlet was not taken from it')

        when: 'the build is written in Kotlin'
        out.reset()
        new File(dir, 'build.gradle').delete()
        new File(dir, 'grails-app/conf/application.yml').text = SHELL_CONFIGURATION
        new File(dir, 'build.gradle.kts').text = 'plugins {\n    id("org.apache.grails.gradle.grails-web")\n}\n\ndependencies {\n' +
                '    implementation("org.springframework.boot:spring-boot-starter-jetty")\n' +
                '    implementation("org.springframework.boot:spring-boot-starter-undertow")\n}\n'
        exit = ForgeCli.execute(beanContext, 'project', 'migrate', '--dry-run')

        then: 'the feature of Jetty is found, which says more than the starter of another servlet container'
        exit == 0
        row(out.toString().readLines(), 'servlet') == ['jetty', '(the feature spring-boot-starter-jetty)']
    }

    private void shellProject() {
        new File(dir, 'grails-app/conf').mkdirs()
        new File(dir, 'grails-app/init/com/example/legacy').mkdirs()
        new File(dir, 'grails-app/conf/application.yml').text = SHELL_CONFIGURATION
        new File(dir, 'grails-app/init/com/example/legacy/Application.groovy').text = 'package com.example.legacy\n\nclass Application {\n}\n'
        new File(dir, 'build.gradle').text = SHELL_BUILD
        new File(dir, 'settings.gradle').text = "rootProject.name = 'legacy'\n"
    }

    // the value of a row of what the command says, and what it says of where the value is from
    private static List<String> row(List<String> said, String what) {
        String line = said.find { String each -> each.startsWith("    ${what} ") }
        assert line != null: "no row ${what} in ${said}"
        String rest = line.substring(4 + what.length()).trim()
        int open = rest.indexOf(' (')
        return [rest.substring(0, open).trim(), rest.substring(open + 1).trim()]
    }

    private Map<String, String> tree() {
        return tree(dir)
    }

    private static Map<String, String> tree(File project) {
        Map<String, String> entries = new TreeMap<>()
        Files.walk(project.toPath()).withCloseable { stream ->
            stream.each { java.nio.file.Path path ->
                String name = project.toPath().relativize(path).join('/')
                if (name.isEmpty()) {
                    return
                }
                entries.put(name, Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) ? 'directory' :
                        MessageDigest.getInstance('SHA-256').digest(Files.readAllBytes(path)).encodeHex().toString())
            }
        }
        return entries
    }
}
