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
import org.apache.grails.forge.core.state.ProjectLock
import org.apache.grails.forge.core.state.ProjectState
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.Project
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.cli.ForgeCli
import org.grails.forge.cli.GrailsPicocliFactory
import org.grails.forge.io.OutputHandler
import picocli.CommandLine
import spock.lang.IgnoreIf
import spock.lang.Unroll

/**
 * {@code project regenerate --diff --json}: the files of the project that are not as generated, as
 * one JSON document on the standard output and nothing else, with an outcome and a reason that tell a
 * comparison that found differences from one that could not be made.
 */
class ProjectRegenerateJsonSpec extends AbstractFeatureAddCommandSpec {

    static final List<String> MEMBERS = ['format', 'command', 'projectDirectory', 'outcome', 'reason', 'message', 'hasChanges', 'files']

    static final String MESSAGE = '.grails/baseline/files/build.gradle is not what the generator wrote: its content does not have the digest of the index'

    void "a project as it was generated has no changes"() {
        given:
        create(dir, CreateAppCommand, [])
        reset()

        when:
        int exit = regenerate('--diff', '--json')
        Map document = document()

        then:
        exit == 0
        err.toString().isEmpty()
        document.keySet().toList() == MEMBERS
        document.format == 1
        document.command == 'project regenerate'
        new File((String) document.projectDirectory).canonicalPath == dir.canonicalPath
        document.outcome == 'no-changes'
        document.reason == null
        document.message == null
        document.hasChanges == false
        document.files == []
    }

    void "the files that were added, removed and changed are listed by their exact paths, sorted, and the exit code is that of --diff"() {
        given:
        create(dir, CreateAppCommand, [])
        new File(dir, 'build.gradle') << '// ours\n'
        assert new File(dir, 'grails-app/conf/application.yml').delete()
        new File(dir, 'src/main/groovy/com/example').mkdirs()
        new File(dir, 'src/main/groovy/com/example/Mine Too.groovy').text = 'class Mine {}\n'
        new File(dir, 'build/classes').mkdirs()
        new File(dir, 'build/classes/Ignored.class').text = 'left out by the .gitignore'
        Map<String, String> before = tree(dir)
        reset()

        when:
        int exit = regenerate('--diff', '--json')
        Map document = document()

        then:
        exit == 1
        err.toString().isEmpty()
        document.outcome == 'changes'
        document.reason == null
        document.hasChanges == true
        document.files == [
                [path: 'build.gradle', status: 'changed'],
                [path: 'grails-app/conf/application.yml', status: 'removed'],
                [path: 'src/main/groovy/com/example/Mine Too.groovy', status: 'added']]
        tree(dir) == before
    }

    @IgnoreIf({ os.windows })
    void "a path with a character that acts on a terminal is in the document as it is, where the text has a question mark"() {
        given:
        create(dir, CreateAppCommand, [])
        new File(dir, 'notes\u001b[31m.txt').text = 'x'
        reset()

        when:
        int exit = regenerate('--diff', '--json')
        String written = out.toString()
        Map document = document()

        then:
        exit == 1
        !written.contains('\u001b')
        written.contains('notes\\u001b[31m.txt')
        document.files == [[path: 'notes\u001b[31m.txt', status: 'added']]

        when:
        reset()
        regenerate('--diff')

        then:
        out.toString().contains('added    notes?[31m.txt')
    }

    void "an application that another version generated is refused, and the reason names it"() {
        given:
        create(dir, CreateAppCommand, [])
        ProjectLock lock = ProjectState.read(dir.toPath()).lock
        new File(dir, '.grails/forge.lock.yml').bytes = new ProjectLock(lock.manifestSha256, lock.generatorName, '9.0.0-M1', lock.grailsVersion, lock.templateApi,
                lock.applicationType, lock.options, lock.lineEndings, lock.repositories, lock.repositoriesNotNamed, lock.features, lock.parameters,
                lock.baselineIndexSha256).toBytes()
        reset()

        when:
        int exit = regenerate('--diff', '--json')
        Map document = document()

        then:
        exit == 1
        document.keySet().toList() == MEMBERS
        document.outcome == 'refused'
        document.reason == 'other-generator'
        document.message.contains('its record is moved to this version first. Nothing was changed. ')
        document.message.endsWith(' project migrate moves the record to this version, from the choices it has, and changes no file of the application outside .grails')
        document.hasChanges == null
        document.files == null
    }

    void "an application whose record project migrate wrote is refused, and the reason says so"() {
        given:
        new File(dir, 'grails-app/conf').mkdirs()
        new File(dir, 'grails-app/init/com/example/legacy').mkdirs()
        new File(dir, 'grails-app/conf/application.yml').text = ProjectMigrateCommandSpec.SHELL_CONFIGURATION
        new File(dir, 'grails-app/init/com/example/legacy/Application.groovy').text = 'package com.example.legacy\n\nclass Application {\n}\n'
        new File(dir, 'build.gradle').text = ProjectMigrateCommandSpec.SHELL_BUILD
        new File(dir, 'settings.gradle').text = "rootProject.name = 'legacy'\n"
        ForgeCli.execute(beanContext, 'project', 'migrate')
        reset()

        when:
        int exit = regenerate('--diff', '--json')
        Map document = document()

        then:
        exit == 1
        document.outcome == 'refused'
        document.reason == 'untracked-record'
    }

    void "a directory in no project is refused, and the document has no project"() {
        when:
        int exit = regenerate('--diff', '--json')
        Map document = document()

        then:
        exit == 1
        err.toString().isEmpty()
        document.projectDirectory == null
        document.outcome == 'refused'
        document.reason == 'no-project'
        document.message == "project regenerate is a command of a Grails project; there is none in ${dir.canonicalPath} or above it".toString()
    }

    @Unroll
    void "a project whose record #what is an invalid record, #reason, and not a difference"() {
        given:
        if (generated) {
            create(dir, CreateAppCommand, [])
        } else {
            new File(dir, 'grails-app').mkdirs()
        }
        change.call(dir)
        reset()

        when:
        int exit = regenerate('--diff', '--json')
        Map document = document()

        then:
        exit == 1
        document.outcome == 'invalid-record'
        document.reason == reason
        document.message.contains(message)
        document.hasChanges == null
        document.files == null

        where:
        what                             | generated | change                                                                              || reason              | message
        'is missing'                     | false     | { File it -> }                                                                      || 'no-record'         | 'There is no state of a generated project here: .grails/project.yml is missing'
        'has no lock'                    | true      | { File it -> new File(it, '.grails/forge.lock.yml').delete() }                      || 'unreadable-record' | 'The state of the project is not complete: .grails/forge.lock.yml is missing'
        'has a baseline that was edited' | true      | { File it -> new File(it, '.grails/baseline/files/build.gradle') << '// edited\n' } || 'unreadable-record' | '.grails/baseline/files/build.gradle is not what the generator wrote'
        'has parameters, which this version writes none of' | true | { File it ->
            File manifest = new File(it, '.grails/project.yml')
            manifest.text = manifest.text.replace('parameters: {}\n', 'parameters:\n  region: eu\n')
            FeatureAddJsonSpec.resealed(it)
        } || 'record-differs' | 'The record of the application under .grails is not what this generator writes for the same choices'
        'has a file the generator does not write so' | true | { File it -> FeatureAddJsonSpec.rewriteGenerated(it, 'README.md', '# another readme\n') } || 'baseline-differs' | '1 file is not what the baseline of the project has (README.md)'
    }

    @Unroll
    @IgnoreIf({ os.windows || System.getProperty('user.name') == 'root' })
    void "#what that cannot be read is a failure, not a difference"() {
        given:
        create(dir, CreateAppCommand, [])
        File closed = new File(dir, path)
        String mode = PosixFilePermissions.toString(Files.getPosixFilePermissions(closed.toPath()))
        Files.setPosixFilePermissions(closed.toPath(), PosixFilePermissions.fromString('---------'))
        reset()

        when:
        int exit = regenerate('--diff', '--json')
        Map document = document()

        then:
        exit == 1
        err.toString().isEmpty()
        document.keySet().toList() == MEMBERS
        document.outcome == 'failed'
        document.reason == 'unreadable-file'
        document.message == "The files of the project cannot be read: ${path} cannot be opened".toString()
        document.hasChanges == null
        document.files == null

        cleanup:
        Files.setPosixFilePermissions(closed.toPath(), PosixFilePermissions.fromString(mode))

        where:
        what                                                       | path
        'a directory of the project'                               | 'grails-app/views'
        'a file of the project that the baseline has'              | 'build.gradle'
        'a file of the record that the baseline has no copy of'    | ProjectState.IGNORE
    }

    @Unroll
    void "a copy of the baseline that is changed once the application was generated again is a record that does not read #how"() {
        given: 'a generator that changes the copy of the build file while it generates the application again, after the baseline was verified'
        create(dir, CreateAppCommand, [])
        File copy = new File(dir, '.grails/baseline/files/build.gradle')
        Changing changing = new Changing(real: beanContext.getBean(ProjectGenerator), file: copy)
        reset()

        when:
        String current = System.getProperty('user.dir')
        System.setProperty('user.dir', dir.absolutePath)
        int exit
        try {
            exit = new CommandLine(new ProjectRegenerateCommand(changing), new GrailsPicocliFactory(beanContext)).execute((['--diff'] + options) as String[])
        } finally {
            System.setProperty('user.dir', current)
        }

        then: 'the comparison reads the copy again, and says what the record is, as the command does before it generates the application'
        changing.changed
        exit == 1
        if (options) {
            Map document = document()
            assert err.toString().isEmpty()
            assert document.outcome == 'invalid-record'
            assert document.reason == 'unreadable-record'
            assert document.message == MESSAGE
            assert document.files == null
        } else {
            assert out.toString().isEmpty()
            assert err.toString().readLines() == ["| Error ${MESSAGE}".toString()]
        }

        where:
        how                | options
        'in the document'  | ['--json']
        'in the text'      | []
    }

    @Unroll
    void "--json is refused as a mistake in the command line #how"() {
        given:
        create(dir, CreateAppCommand, [])
        reset()

        when:
        int exit = regenerate((['--json'] + options) as String[])

        then:
        exit == 2
        err.toString().contains(message)
        out.toString().isEmpty()
        !new File(dir, 'again').exists()

        where:
        how               | options                || message
        'with --output'   | ['--output', 'again']  || '--json writes what --diff says as one JSON document: give it with --diff'
        'without --diff'  | []                     || 'Give --output with the directory to write the application to, or --diff'
    }

    // the standard output is the document, on one line, and nothing else
    private Map document() {
        List<String> lines = out.toString().readLines()
        assert lines.size() == 1: out.toString()
        return (Map) new JsonSlurper().parseText(lines[0])
    }

    // generates as the generator of Forge does, and adds a line to a file while it does
    static class Changing implements ProjectGenerator {

        @Delegate
        ProjectGenerator real
        File file
        boolean changed

        @Override
        void generate(ApplicationType applicationType, Project project, OutputHandler outputHandler, GeneratorContext generatorContext) throws Exception {
            file << '// changed while the application was generated again\n'
            changed = true
            real.generate(applicationType, project, outputHandler, generatorContext)
        }
    }
}
