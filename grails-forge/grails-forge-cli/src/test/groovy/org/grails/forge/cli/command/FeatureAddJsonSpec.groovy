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
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import org.apache.grails.forge.core.change.FeatureAddition
import org.apache.grails.forge.core.state.ProjectLock
import org.apache.grails.forge.core.state.ProjectState
import org.grails.forge.application.OperatingSystem
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.cli.ForgeCli
import org.grails.forge.io.ConsoleOutput
import spock.lang.IgnoreIf
import spock.lang.Unroll

/**
 * {@code feature add --dry-run --json}: what adding features comes to, as one JSON document on the
 * standard output and nothing else, with an outcome and a reason for every way the command ends, the
 * files by their paths in the project, and whether the change can be made.
 */
class FeatureAddJsonSpec extends AbstractFeatureAddCommandSpec {

    static final List<String> MEMBERS = ['format', 'command', 'projectDirectory', 'outcome', 'reason', 'message', 'hasChanges', 'applicable', 'requested',
                                         'alreadyPresent', 'added', 'replaced', 'replacements', 'lostFeatures', 'changedOptions', 'files', 'notes']

    void "what a feature would change is the document, with every file by its path and what would be done to it, and nothing is changed"() {
        given:
        create(dir, CreateAppCommand, [])
        Map<String, String> before = tree(dir)

        when:
        int exit = add('postgres', '--dry-run', '--json')
        Map document = document()

        then:
        exit == 0
        err.toString().isEmpty()
        document.keySet().toList() == MEMBERS
        document.format == 1
        document.command == 'feature add'
        new File((String) document.projectDirectory).canonicalPath == dir.canonicalPath
        document.outcome == 'changes'
        document.reason == null
        document.message == null
        document.hasChanges == true
        document.applicable == true
        document.requested == ['postgres']
        document.alreadyPresent == []
        document.added == ['postgres', 'testcontainers']
        document.replaced == ['h2']
        document.replacements == ['postgres replaces h2, the default of the group database-driver']
        document.lostFeatures == []
        document.changedOptions == []
        document.files == [
                [path: 'README.md', action: 'update', reason: null, details: []],
                [path: 'build.gradle', action: 'update', reason: null, details: []],
                [path: 'grails-app/conf/application.yml', action: 'update', reason: null, details: []],
                [path: 'src/test/resources/application-test.yml', action: 'add', reason: null, details: []]]
        document.notes == []

        and:
        tree(dir) == before
        !new File(dir, '.grails/transactions').exists()
    }

    void "features the application has are no change, and the change can be made"() {
        given:
        create(dir, CreateAppCommand, ['--features', 'grails-quartz'])
        Map<String, String> before = tree(dir)

        when:
        int exit = add('grails-quartz,gsp', '--dry-run', '--json')
        Map document = document()

        then:
        exit == 0
        document.outcome == 'no-changes'
        document.reason == null
        document.hasChanges == false
        document.applicable == true
        document.requested == []
        document.alreadyPresent == ['grails-quartz', 'gsp']
        document.files == []
        tree(dir) == before
    }

    void "a conflict is its own outcome, with the file, why, and the lines of both, and the change cannot be made"() {
        given:
        create(dir, CreateAppCommand, [])
        File config = new File(dir, 'grails-app/conf/application.yml')
        config.text = config.text.replace('url: jdbc:h2:mem:devDb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE', 'url: jdbc:h2:file:./ourDevDb')
        new File(dir, 'build.gradle').text = '// our build\n' + new File(dir, 'build.gradle').text
        Map<String, String> before = tree(dir)

        when:
        int exit = add('postgres', '--dry-run', '--json')
        Map document = document()

        then: 'the exit code is that of --dry-run without --json'
        exit == 1
        err.toString().isEmpty()
        document.outcome == 'conflicts'
        document.reason == 'conflicts'
        document.message.startsWith('The feature was not added: 1 file cannot be written without losing what the application has. Nothing was changed.')
        document.hasChanges == true
        document.applicable == false
        Map conflict = document.files.find { it.action == 'conflict' }
        conflict.path == 'grails-app/conf/application.yml'
        conflict.reason.startsWith('the feature changes')
        conflict.details*.trim().contains('url: jdbc:h2:file:./ourDevDb')
        document.files.find { it.path == 'build.gradle' }.action == 'merge'
        tree(dir) == before
    }

    void "a feature that would change an option is refused with what it would come to, and the change cannot be made"() {
        given:
        create(dir, CreateAppCommand, [])
        Map<String, String> before = tree(dir)

        when:
        int exit = add('gorm-hibernate7', '--dry-run', '--json')
        Map document = document()

        then:
        exit == 1
        document.outcome == 'refused'
        document.reason == 'changes-option'
        document.message.startsWith('Adding gorm-hibernate7 would change what the application was created with (--data hibernate7 in the place of hibernate5)')
        document.hasChanges == true
        document.applicable == false
        document.changedOptions == ['--data hibernate7 in the place of hibernate5']
        document.files.find { it.path == 'build.gradle' }.action == 'update'
        document.replaced == []
        tree(dir) == before
    }

    @Unroll
    void "features that cannot be added as they were asked for, #reason, are refused with exit code 2 and a document"() {
        given:
        create(dir, CreateAppCommand, created)
        Map<String, String> before = tree(dir)

        when:
        int exit = add(([features, '--dry-run', '--json'] + options) as String[])
        Map document = document()

        then: 'nothing was compared, so nothing is said of the files'
        exit == 2
        err.toString().isEmpty()
        document.keySet().toList() == MEMBERS
        document.outcome == 'refused'
        document.reason == reason
        document.message.contains(message)
        document.hasChanges == null
        document.applicable == false
        document.files == null
        document.requested == null
        tree(dir) == before

        where:
        created                                                    | features                           | options       || reason                        | message
        []                                                         | 'h2,postgres'                      | ['--replace'] || 'contradictory-request'       | 'The features h2 and postgres are both of the group database-driver'
        ['--features', 'h2']                                       | 'postgres'                         | []            || 'replace-required'            | 'feature add postgres --replace takes h2 out of the application'
        ['--features=grails-spring-security,grails-spring-security-ui'] | 'spring-boot-starter-security' | ['--replace'] || 'required-by-another-feature' | 'The feature grails-spring-security is not replaced'
        []                                                         | 'no-such-feature'                  | []            || 'feature-selection'           | 'The requested feature does not exist: no-such-feature'
        []                                                         | 'com.acme:acme-audit-grails:2.3.0' | []            || 'plugin-coordinates'          | 'is no name of a feature of Grails'
    }

    void "an application that another version generated is refused, and the reason names it"() {
        given:
        create(dir, CreateAppCommand, [])
        ProjectLock lock = ProjectState.read(dir.toPath()).lock
        new File(dir, '.grails/forge.lock.yml').bytes = new ProjectLock(lock.manifestSha256, lock.generatorName, '9.0.0-M1', lock.grailsVersion, lock.templateApi,
                lock.applicationType, lock.options, lock.lineEndings, lock.repositories, lock.repositoriesNotNamed, lock.features, lock.parameters,
                lock.baselineIndexSha256).toBytes()

        when:
        int exit = add('grails-quartz', '--dry-run', '--json')
        Map document = document()

        then:
        exit == 1
        document.outcome == 'refused'
        document.reason == 'other-generator'
        document.message.contains('Use the command line of Grails 9.0.0-M1 to add the feature')
        document.applicable == false
    }

    void "an application whose record project migrate wrote is refused, and the reason says so"() {
        given:
        new File(dir, 'grails-app/conf').mkdirs()
        new File(dir, 'grails-app/init/com/example/legacy').mkdirs()
        new File(dir, 'grails-app/conf/application.yml').text = ProjectMigrateCommandSpec.SHELL_CONFIGURATION
        new File(dir, 'grails-app/init/com/example/legacy/Application.groovy').text = 'package com.example.legacy\n\nclass Application {\n}\n'
        new File(dir, 'build.gradle').text = ProjectMigrateCommandSpec.SHELL_BUILD
        new File(dir, 'settings.gradle').text = "rootProject.name = 'legacy'\n"
        ForgeCli.execute(beanContext, 'project', 'migrate') == 0
        reset()

        when:
        int exit = add('grails-web-console', '--dry-run', '--json')
        Map document = document()

        then:
        exit == 1
        document.outcome == 'refused'
        document.reason == 'untracked-record'
    }

    void "an application with the feature of a pack that is not of Grails is refused without that pack, and the reason says so"() {
        given:
        File pack = Files.createTempDirectory(with.toPath(), 'pack').toFile()
        new File(pack, 'templates').mkdirs()
        new File(pack, 'feature.yml').setText('format: 1\nkind: feature\ntemplateApi: 1\nname: acme-audit\n' +
                "compatibility:\n  grails: '[9.0.0,10.0.0)'\n  forge: '[9.0.0,10.0.0)'\n" +
                "files:\n  - from: audit.txt\n    to: 'grails-app/conf/audit.txt'\n", 'UTF-8')
        new File(pack, 'templates/audit.txt').setText('kept as it is', 'UTF-8')
        create(dir, CreateAppCommand, ['--features', 'acme-audit', '--feature-pack-dir', pack.absolutePath])
        reset()

        when:
        int exit = add('grails-quartz', '--dry-run', '--json')
        Map document = document()

        then:
        exit == 1
        document.outcome == 'refused'
        document.reason == 'pack-missing'
        document.message.contains('acme-audit, a feature pack that was given as a directory or a file whose content has the SHA-256 ')

        when: 'with the pack'
        reset()
        exit = add('grails-quartz', '--dry-run', '--json', '--feature-pack-dir', pack.absolutePath)
        // the method, and not the variable of the document above
        Map withThePack = this.document()

        then:
        exit == 0
        withThePack.outcome == 'changes'
        withThePack.reason == null
    }

    void "an application that is half changed by an interrupted change is refused until the change is ended"() {
        given:
        create(dir, CreateAppCommand, [])
        try {
            new FeatureAddition(beanContext.getBean(ProjectGenerator)).plan(dir.toPath(), ['grails-quartz'], OperatingSystem.LINUX, ConsoleOutput.NOOP).apply { String path ->
                if (path == 'build.gradle') {
                    throw new IllegalStateException('the program ended')
                }
            }
        } catch (IllegalStateException ignored) {
        }

        when:
        int exit = add('grails-quartz', '--dry-run', '--json')
        Map document = document()

        then:
        exit == 1
        document.outcome == 'refused'
        document.reason == 'interrupted-change'
        document.applicable == false
    }

    void "a directory in no project is refused, and the document has no project"() {
        when:
        int exit = add('grails-quartz', '--dry-run', '--json')
        Map document = document()

        then:
        exit == 1
        err.toString().isEmpty()
        document.keySet().toList() == MEMBERS
        document.projectDirectory == null
        document.outcome == 'refused'
        document.reason == 'no-project'
        document.message == "feature add is a command of a Grails project; there is none in ${dir.canonicalPath} or above it".toString()
    }

    @Unroll
    void "a project whose record #what is an invalid record, #reason"() {
        given:
        if (generated) {
            create(dir, CreateAppCommand, [])
        } else {
            new File(dir, 'grails-app').mkdirs()
        }
        change.call(dir)
        Map<String, String> before = tree(dir)

        when:
        int exit = add('grails-quartz', '--dry-run', '--json')
        Map document = document()

        then:
        exit == 1
        document.outcome == 'invalid-record'
        document.reason == reason
        document.message.contains(message)
        document.hasChanges == null
        document.files == null
        tree(dir) == before

        where:
        what                                        | generated | change                                                                              || reason              | message
        'is missing'                                | false     | { File it -> }                                                                      || 'no-record'         | 'There is no application here that a feature can be added to'
        'has a baseline that was edited'            | true      | { File it -> new File(it, '.grails/baseline/files/build.gradle') << '// edited\n' } || 'unreadable-record' | '.grails/baseline/files/build.gradle is not what the generator wrote'
        'has parameters, which this version writes none of' | true | { File it ->
            File manifest = new File(it, '.grails/project.yml')
            manifest.text = manifest.text.replace('parameters: {}\n', 'parameters:\n  region: eu\n')
            resealed(it)
        } || 'record-differs' | 'The record of the application under .grails is not what this generator writes for the same choices'
        'has a file the generator does not write so' | true    | { File it -> rewriteGenerated(it, 'README.md', '# another readme\n') }              || 'baseline-differs'  | 'The application cannot be generated again as it was: 1 file is not what the baseline of the project has (README.md)'
    }

    @IgnoreIf({ os.windows || System.getProperty('user.name') == 'root' })
    void "a file of the project that cannot be read is a failure, and the reason says so"() {
        given:
        create(dir, CreateAppCommand, [])
        File build = new File(dir, 'build.gradle')
        Files.setPosixFilePermissions(build.toPath(), PosixFilePermissions.fromString('---------'))

        when:
        int exit = add('grails-quartz', '--dry-run', '--json')
        Map document = document()

        then:
        exit == 1
        document.outcome == 'failed'
        document.reason == 'unreadable-file'
        document.message == 'build.gradle cannot be read (AccessDeniedException); nothing was changed'

        cleanup:
        Files.setPosixFilePermissions(build.toPath(), PosixFilePermissions.fromString('rw-r--r--'))
    }

    @Unroll
    void "--json is refused as a mistake in the command line #how"() {
        given:
        create(dir, CreateAppCommand, [])
        Map<String, String> before = tree(dir)

        when:
        int exit = add((['grails-quartz', '--json'] + options) as String[])

        then:
        exit == 2
        err.toString().contains('--json writes what --dry-run says as one JSON document: give it with --dry-run, and without --diff')
        out.toString().isEmpty()
        tree(dir) == before

        where:
        how                      | options
        'without --dry-run'      | []
        'with --diff'            | ['--diff']
        'with --dry-run and --diff' | ['--dry-run', '--diff']
    }

    // the standard output is the document, on one line, and nothing else
    private Map document() {
        List<String> lines = out.toString().readLines()
        assert lines.size() == 1: out.toString()
        return (Map) new JsonSlurper().parseText(lines[0])
    }

    // the lock of a project with the digest of its manifest as it is now, and sealed again
    static void resealed(File project) {
        File lock = new File(project, '.grails/forge.lock.yml')
        String body = lock.text.replaceFirst('manifest:\n  sha256: [0-9a-f]{64}', "manifest:\n  sha256: ${sha(new File(project, '.grails/project.yml').bytes)}")
        body = body.substring(0, body.lastIndexOf('seal:\n'))
        lock.text = body + "seal:\n  sha256: ${sha(body.bytes)}\n"
    }

    // a file of a project, its copy in the baseline and the digests the record has of it as another generator would have written them
    static void rewriteGenerated(File project, String path, String text) {
        File index = new File(project, '.grails/baseline/index.yml')
        File lock = new File(project, '.grails/forge.lock.yml')
        String was = sha(new File(project, path).bytes)
        assert index.text.count(was) == 1
        new File(project, path).text = text
        new File(project, ".grails/baseline/files/${path}").text = text
        String indexWas = sha(index.bytes)
        index.text = index.text.replace(was, sha(text.bytes))
        assert lock.text.count(indexWas) == 1
        String sealed = lock.text.substring(0, lock.text.indexOf('seal:\n')).replace(indexWas, sha(index.bytes))
        lock.text = sealed + "seal:\n  sha256: ${sha(sealed.bytes)}\n"
    }
}
