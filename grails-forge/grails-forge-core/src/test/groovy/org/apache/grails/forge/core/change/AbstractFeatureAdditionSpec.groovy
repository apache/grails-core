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

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
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
import spock.lang.TempDir

/**
 * What the specs of adding a feature share: applications that are generated into a directory of the
 * test, a home directory of the test for the journals, generators that add files to what Quartz
 * writes and so stand in for a feature that has them, and the whole of a project as one value to
 * compare.
 */
abstract class AbstractFeatureAdditionSpec extends ApplicationContextSpec {

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

    static boolean ignoresCase() {
        Path probe = Files.createTempFile('probe', '.case')
        boolean ignores = Files.exists(probe.resolveSibling(probe.fileName.toString().toUpperCase(Locale.ROOT)))
        Files.delete(probe)
        return ignores
    }

    protected FeatureAddition addition(ProjectGenerator generator) {
        return new FeatureAddition(generator, home)
    }

    protected ChangePlan planOf(ProjectGenerator generator, Path project, List<String> features = [FEATURE]) {
        return addition(generator).plan(project, features, OperatingSystem.LINUX, ConsoleOutput.NOOP)
    }

    protected ProjectGenerator generator(Closure extra = { }) {
        return new WithFiles(real: beanContext.getBean(ProjectGenerator), extra: extra)
    }

    protected ProjectGenerator changingIcon() {
        return generator { GeneratorContext context ->
            context.addTemplate('icon', new Fixed('icon.bin', context.features.contains(FEATURE) ? OTHER_ICON : ICON, true, false))
        }
    }

    // a binary file, and a file that can be executed in a directory there was not
    protected ProjectGenerator withNewFiles() {
        return generator { GeneratorContext context ->
            if (context.features.contains(FEATURE)) {
                context.addTemplate('icon', new Fixed('src/main/resources/icon.bin', ICON, true, false))
                context.addTemplate('tool', new Fixed('bin/tool.sh', '#!/bin/sh\necho tool\n'.bytes, false, true))
            }
        }
    }

    // files that an application has without the feature only: a text file, a binary file in directories of its own, and beside them one it always has
    protected ProjectGenerator leavingFilesOut(ProjectGenerator on = beanContext.getBean(ProjectGenerator)) {
        return new WithFiles(real: on, extra: { GeneratorContext context ->
            context.addTemplate('stays', new Fixed('kept/stays.txt', 'stays\n'.bytes, false, false))
            if (!context.features.contains(FEATURE)) {
                context.addTemplate('only', new Fixed('only-without.txt', 'x\n'.bytes, false, false))
                context.addTemplate('gone', new Fixed('old/only/gone.bin', ICON, true, false))
            }
        })
    }

    // makes a file of a project, its copy in the baseline and the digests the record has of it what another generator would have written
    protected static void rewriteGenerated(Path project, String path, String text) {
        File index = project.resolve('.grails/baseline/index.yml').toFile()
        File lock = project.resolve('.grails/forge.lock.yml').toFile()
        String was = sha(project.resolve(path))
        assert index.text.count(was) == 1
        project.resolve(path).toFile().text = text
        project.resolve(".grails/baseline/files/${path}").toFile().text = text
        String indexWas = sha(index.bytes)
        index.text = index.text.replace(was, sha(text.bytes))
        assert lock.text.count(indexWas) == 1
        String sealed = lock.text.substring(0, lock.text.indexOf('seal:\n')).replace(indexWas, sha(index.bytes))
        lock.text = sealed + "seal:\n  sha256: ${sha(sealed.bytes)}\n"
    }

    protected Path create(ProjectGenerator generator, String into, List<String> features) {
        Path project = Files.createDirectory(directory.resolve(into))
        Project named = NameUtils.parse('com.acme.shop')
        GeneratorContext context = generator.createGeneratorContext(ApplicationType.WEB, named, new Options(), OperatingSystem.LINUX, features, ConsoleOutput.NOOP)
        generator.generate(ApplicationType.WEB, named, new FileSystemOutputHandler(project.toFile(), ConsoleOutput.NOOP), context)
        return project
    }

    protected Path copy(Path project, String into) {
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
    protected static List<String> left(Path project) {
        Path transactions = project.resolve('.grails/transactions')
        return Files.isDirectory(transactions) ? (transactions.toFile().list() as List<String>).findAll { it != 'lock' }.sort() : []
    }

    // the journals there are, of every project
    protected List<Path> journals() {
        if (!Files.isDirectory(records)) {
            return []
        }
        List<Path> journals = []
        Files.walk(records).withCloseable { stream -> stream.filter { Path path -> Files.isRegularFile(path) }.each { Path path -> journals << path } }
        return journals
    }

    protected static String sha(Path file) {
        return sha(Files.readAllBytes(file))
    }

    protected static String sha(byte[] bytes) {
        return MessageDigest.getInstance('SHA-256').digest(bytes).encodeHex().toString()
    }

    protected static List<String> differing(Map<String, String> one, Map<String, String> other) {
        return new ArrayList<String>(one.keySet() + other.keySet()).findAll { one.get(it) != other.get(it) }.sort()
    }

    /**
     * Everything of a project: every directory with its mode, and every file with the digest of its
     * content and its mode, a link as a link. The directory of the changes and the lock in it are
     * left out, because a project has them once a feature was added to it and not before; what else
     * is in that directory is not.
     */
    protected static Map<String, String> tree(Path project) {
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
