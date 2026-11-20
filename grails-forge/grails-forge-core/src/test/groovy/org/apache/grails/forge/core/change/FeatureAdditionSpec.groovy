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
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
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
import spock.lang.TempDir

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

    @TempDir
    Path directory

    void "a feature is added as if the application had been created with it"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        Path with = create(generator, 'with', [FEATURE])

        when:
        ChangePlan plan = new FeatureAddition(generator).plan(project, [FEATURE], OperatingSystem.LINUX, ConsoleOutput.NOOP)

        then:
        plan.requested == [FEATURE]
        plan.added.contains(FEATURE)
        plan.conflicts.isEmpty()
        plan.files.find { it.path == 'build.gradle' }.action == Action.UPDATE
        plan.files.every { it.action in [Action.ADD, Action.UPDATE] }

        and: 'nothing is written by a plan'
        tree(project) != tree(with)
        !Files.exists(project.resolve('.grails/transactions'))

        when:
        plan.apply()

        then: 'every file, and the state, is that of the application that was created with the feature'
        tree(project) == tree(with)
        ProjectState.read(project).verifyBaseline(project)
        !Files.exists(project.resolve('.grails/transactions/current'))

        when: 'again'
        ChangePlan again = new FeatureAddition(generator).plan(project, [FEATURE], OperatingSystem.LINUX, ConsoleOutput.NOOP)
        again.apply()

        then:
        again.nothingToAdd
        again.alreadyThere == [FEATURE]
        again.files.isEmpty()
        tree(project) == tree(with)
    }

    void "a feature adds a binary file and a file that can be executed"() {
        given:
        ProjectGenerator generator = generator { GeneratorContext context ->
            if (context.features.contains(FEATURE)) {
                context.addTemplate('icon', new Fixed('src/main/resources/icon.bin', ICON, true, false))
                context.addTemplate('tool', new Fixed('bin/tool.sh', '#!/bin/sh\necho tool\n'.bytes, false, true))
            }
        }
        Path project = create(generator, 'app', [])
        Path with = create(generator, 'with', [FEATURE])

        when:
        ChangePlan plan = new FeatureAddition(generator).plan(project, [FEATURE], OperatingSystem.LINUX, ConsoleOutput.NOOP)
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
    void "the file a feature adds can be executed when the feature says so"() {
        given:
        ProjectGenerator generator = generator { GeneratorContext context ->
            if (context.features.contains(FEATURE)) {
                context.addTemplate('tool', new Fixed('bin/tool.sh', '#!/bin/sh\necho tool\n'.bytes, false, true))
            }
        }
        Path project = create(generator, 'app', [])

        when:
        new FeatureAddition(generator).plan(project, [FEATURE], OperatingSystem.LINUX, ConsoleOutput.NOOP).apply()

        then:
        Files.isExecutable(project.resolve('bin/tool.sh'))
        !Files.isExecutable(project.resolve('build.gradle'))
    }

    void "a binary file a feature changes is replaced when the project has it as it was generated"() {
        given:
        ProjectGenerator generator = changingIcon()
        Path project = create(generator, 'app', [])
        Path with = create(generator, 'with', [FEATURE])

        when:
        ChangePlan plan = new FeatureAddition(generator).plan(project, [FEATURE], OperatingSystem.LINUX, ConsoleOutput.NOOP)
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
        ChangePlan plan = new FeatureAddition(generator).plan(project, [FEATURE], OperatingSystem.LINUX, ConsoleOutput.NOOP)

        then:
        plan.conflicts*.path == ['icon.bin']
        plan.conflicts[0].reason == 'the feature changes this binary file, and it was changed in the project too'
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
        ChangePlan plan = new FeatureAddition(generator).plan(project, [FEATURE], OperatingSystem.LINUX, ConsoleOutput.NOOP)
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
        ChangePlan plan = new FeatureAddition(generator).plan(project, [FEATURE], OperatingSystem.LINUX, ConsoleOutput.NOOP)
        plan.apply()

        then:
        plan.files.find { it.path == 'tool.sh' }.action == Action.UPDATE
        plan.diff().contains('tool.sh: whether the file can be executed changes')
        Files.isExecutable(project.resolve('tool.sh'))
        project.resolve('tool.sh').toFile().text == '#!/bin/sh\necho mine\n'
        ProjectState.read(project).baseline.find('tool.sh').executable
        ProjectState.read(project).verifyBaseline(project)
    }

    void "a feature that would leave a generated file out is refused"() {
        given:
        ProjectGenerator generator = generator { GeneratorContext context ->
            if (!context.features.contains(FEATURE)) {
                context.addTemplate('only', new Fixed('only-without.txt', 'x\n'.bytes, false, false))
            }
        }
        Path project = create(generator, 'app', [])
        Map<String, String> before = tree(project)

        when:
        new FeatureAddition(generator).plan(project, [FEATURE], OperatingSystem.LINUX, ConsoleOutput.NOOP)

        then:
        ProjectChangeException e = thrown()
        e.message == 'Adding grails-quartz would leave out a file the application was generated with (only-without.txt), which adding a feature does not do; nothing was changed'
        tree(project) == before
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
        new FeatureAddition(generator).plan(project, [FEATURE], OperatingSystem.LINUX, ConsoleOutput.NOOP)

        then:
        ProjectChangeException e = thrown()
        e.message.startsWith('The application cannot be generated again as it was: 1 file is not what the baseline of the project has (count.txt).')
        tree(project) == before
    }

    void "a change that is interrupted is undone before the next one"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        Path with = create(generator, 'with', [FEATURE])
        Map<String, String> before = tree(project)
        ChangePlan plan = new FeatureAddition(generator).plan(project, [FEATURE], OperatingSystem.LINUX, ConsoleOutput.NOOP)
        List<String> written = []

        when: 'the program ends after the build file and before the state is written'
        plan.apply { String path ->
            written << path
            if (path == 'build.gradle') {
                throw new Stopped()
            }
        }

        then:
        thrown(Stopped)
        written.last() == 'build.gradle'
        tree(project) != before
        Files.exists(project.resolve('.grails/transactions/current/journal'))

        when: 'nothing is planned on a project that is half changed'
        new FeatureAddition(generator).plan(project, [FEATURE], OperatingSystem.LINUX, ConsoleOutput.NOOP)

        then:
        ProjectChangeException e = thrown()
        e.message.startsWith('An earlier change of the project was interrupted (.grails/transactions/current)')

        when:
        boolean undone = FeatureAddition.recover(project)

        then:
        undone
        tree(project) == before
        !Files.exists(project.resolve('.grails/transactions/current'))
        !FeatureAddition.recover(project)

        when:
        new FeatureAddition(generator).plan(project, [FEATURE], OperatingSystem.LINUX, ConsoleOutput.NOOP).apply()

        then:
        tree(project) == tree(with)
    }

    void "a change that was interrupted after its last file is undone too"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        Map<String, String> before = tree(project)

        when:
        new FeatureAddition(generator).plan(project, [FEATURE], OperatingSystem.LINUX, ConsoleOutput.NOOP).apply { String path ->
            if (path == ProjectState.MANIFEST) {
                throw new Stopped()
            }
        }

        then:
        thrown(Stopped)

        when:
        boolean undone = FeatureAddition.recover(project)

        then:
        undone
        tree(project) == before
        ProjectState.read(project).verifyBaseline(project)
    }

    void "a file that was changed after a change was interrupted is left as it is, until it is put back"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        Map<String, String> before = tree(project)
        ChangePlan plan = new FeatureAddition(generator).plan(project, [FEATURE], OperatingSystem.LINUX, ConsoleOutput.NOOP)
        List<String> written = []
        try {
            plan.apply { String path ->
                written << path
                if (path == ProjectState.BASELINE_INDEX) {
                    throw new Stopped()
                }
            }
        } catch (Stopped ignored) {
        }
        project.resolve('build.gradle').toFile() << '// written by an editor meanwhile\n'

        when:
        FeatureAddition.recover(project)

        then:
        ProjectChangeException e = thrown()
        e.message.startsWith('A change of the project was interrupted and could not be undone: a file that was changed since is left as it is: build.gradle. ')
        e.message.contains('.grails/transactions/current/backup')
        project.resolve('build.gradle').toFile().text.endsWith('// written by an editor meanwhile\n')

        and: 'every other file is as it was'
        tree(project).findAll { it.key != 'build.gradle' && !it.key.startsWith('.grails/transactions/') } == before.findAll { it.key != 'build.gradle' }

        when: 'the file is put back from the copy that was kept'
        int index = written.indexOf('build.gradle')
        Files.copy(project.resolve(".grails/transactions/current/backup/${index}"), project.resolve('build.gradle'), java.nio.file.StandardCopyOption.REPLACE_EXISTING)

        then:
        FeatureAddition.recover(project)
        tree(project) == before
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
        ChangePlan plan = new FeatureAddition(generator).plan(project, [FEATURE], OperatingSystem.LINUX, ConsoleOutput.NOOP)
        List<String> written = []

        when:
        plan.apply { String path -> written << path }

        then:
        ProjectChangeException e = thrown()
        e.message == 'sealed/new.txt could not be written (AccessDeniedException); the project is as it was before'
        written.contains('build.gradle')
        tree(project) == before
        !Files.exists(project.resolve('.grails/transactions/current'))
        ProjectState.read(project).verifyBaseline(project)

        cleanup:
        Files.setPosixFilePermissions(sealed, PosixFilePermissions.fromString('rwxr-xr-x'))
    }

    void "a file that is changed between the plan and its application stops the change before anything is written"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        ChangePlan plan = new FeatureAddition(generator).plan(project, [FEATURE], OperatingSystem.LINUX, ConsoleOutput.NOOP)
        project.resolve('build.gradle').toFile() << '// written by an editor meanwhile\n'
        Map<String, String> before = tree(project)

        when:
        plan.apply()

        then:
        ProjectChangeException e = thrown()
        e.message == 'build.gradle was changed while the change of the project was being prepared; nothing of the project was changed'
        tree(project) == before
        !Files.exists(project.resolve('.grails/transactions/current'))
    }

    void "one command changes a project at a time"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        ChangePlan plan = new FeatureAddition(generator).plan(project, [FEATURE], OperatingSystem.LINUX, ConsoleOutput.NOOP)
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

    @IgnoreIf({ os.windows })
    void "a link where a feature changes a file is a conflict"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        Path elsewhere = Files.writeString(directory.resolve('elsewhere.gradle'), project.resolve('build.gradle').toFile().text)
        Files.delete(project.resolve('build.gradle'))
        Files.createSymbolicLink(project.resolve('build.gradle'), elsewhere)

        when:
        ChangePlan plan = new FeatureAddition(generator).plan(project, [FEATURE], OperatingSystem.LINUX, ConsoleOutput.NOOP)

        then:
        plan.conflicts.find { it.path == 'build.gradle' }.reason == 'the feature changes this file, and in the project it is a symbolic link'
    }

    private ProjectGenerator generator(Closure extra = { }) {
        return new WithFiles(real: beanContext.getBean(ProjectGenerator), extra: extra)
    }

    private ProjectGenerator changingIcon() {
        return generator { GeneratorContext context ->
            context.addTemplate('icon', new Fixed('icon.bin', context.features.contains(FEATURE) ? OTHER_ICON : ICON, true, false))
        }
    }

    private Path create(ProjectGenerator generator, String into, List<String> features) {
        Path project = Files.createDirectory(directory.resolve(into))
        Project named = NameUtils.parse('com.acme.shop')
        GeneratorContext context = generator.createGeneratorContext(ApplicationType.WEB, named, new Options(), OperatingSystem.LINUX, features, ConsoleOutput.NOOP)
        generator.generate(ApplicationType.WEB, named, new FileSystemOutputHandler(project.toFile(), ConsoleOutput.NOOP), context)
        return project
    }

    // every file of a project but the lock of its changes: the digest of its content, and whether it can be executed
    private static Map<String, String> tree(Path project) {
        Map<String, String> files = new TreeMap<>()
        Files.walk(project).withCloseable { stream ->
            stream.filter { Path path -> !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) }.each { Path path ->
                String name = project.relativize(path).join('/')
                if (name != '.grails/transactions/lock') {
                    files.put(name, MessageDigest.getInstance('SHA-256').digest(Files.readAllBytes(path)).encodeHex().toString() + (Files.isExecutable(path) ? ' x' : ''))
                }
            }
        }
        return files
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
