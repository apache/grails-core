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
package org.apache.grails.forge.cli.console

import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import groovy.transform.CompileStatic
import picocli.CommandLine

/**
 * {@code bug-report}: a ZIP file of the sources of the project, to attach to an issue. It holds the
 * files under {@code grails-app} and {@code src} and the {@code build.gradle} of the project, and is
 * written to the {@code build} directory. Links are not followed and a file that has another name
 * elsewhere is left out, so nothing from outside the project gets into it; files that are keys by
 * their name are left out, and so is what a build left behind. The configuration of the application
 * is part of the sources and is in it, which the command says before anything else.
 *
 * @since 9.0
 */
@CommandLine.Command(name = 'bug-report',
        description = [
                'Creates a ZIP file of the sources of the Grails project.',
                'It is to attach to an issue, holds what is under grails-app and src, and build.gradle, and is written to the build directory.',
                'The configuration under grails-app/conf is in it as it is: look through the ZIP for passwords before it is given to anyone.'
        ])
@CompileStatic
class BugReportCommand extends InProjectCommand {

    /** How many files the archive holds, at most. */
    public static final int MAX_FILES = 10_000

    /** How many bytes the files of the archive have together, at most, before compression. */
    public static final long MAX_BYTES = 256L * 1024 * 1024

    private static final int MAX_DEPTH = 64
    private static final List<String> DIRECTORIES = ['grails-app', 'src']
    private static final String BUILD_FILE = 'build.gradle'
    private static final String BUILD_DIRECTORY = 'build'

    // what a version control system or an editor keeps beside the sources
    private static final Set<String> NOT_SOURCES_DIRECTORIES = ['.git', '.svn', '.hg', '.bzr', 'CVS', 'SCCS'] as Set<String>
    private static final Set<String> NOT_SOURCES = ['.DS_Store', '.gitignore', '.gitattributes', '.gitmodules', '.hgignore', '.hgtags', '.cvsignore', 'vssver.scc'] as Set<String>
    // a file of one of these names is a key, a store of keys or a list of secrets, and never needed to reproduce a bug
    private static final List<String> SECRET_ENDINGS = ['.env', '.jks', '.p12', '.pfx', '.pem', '.key', '.keystore', '.p8', '.ppk', '.kdbx', '.der', '.asc', '.gpg']
    private static final List<String> SECRET_STARTS = ['.env.', 'id_rsa', 'id_dsa', 'id_ecdsa', 'id_ed25519']
    // words a source can start with as well: these names are secrets unless they are code
    private static final List<String> SECRET_WORDS = ['truststore', 'credentials', 'secrets']
    private static final Set<String> SECRET_NAMES = ['.netrc', '.npmrc', '.pypirc', 'cacerts'] as Set<String>
    // a copy of such a file that was put aside is one as well
    private static final List<String> COPY_ENDINGS = ['.bak', '.old', '~']
    // a class named for what it handles is a source: CredentialsController.groovy holds no credentials
    private static final List<String> CODE_ENDINGS = ['.groovy', '.java', '.gsp', '.gson', '.kt']
    // what a build leaves behind, wherever it ran
    private static final Set<String> BUILD_DIRECTORIES = ['build', '.gradle'] as Set<String>
    private static final Pattern CONFIGURATION = Pattern.compile(/grails-app\/conf\/application[^\/]*\.(yml|yaml|groovy|properties)/)
    private static final Pattern NOT_SHOWN = Pattern.compile(/[\p{Cc}\p{Cf}\p{Zl}\p{Zp}]/)

    @CommandLine.Option(names = ['-f', '--force'], description = 'Replace the ZIP file when there is one of the same name, as there is after the command was run earlier on the same day.')
    boolean force

    @Override
    protected String notDone() {
        return 'No bug report was created'
    }

    @Override
    protected int run(ConsoleProject project) {
        Path directory = project.directory
        Path name = directory.getFileName()
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern('ddMMyyyy'))
        String zipName = "${name == null ? 'project' : name}-bug-report-${date}.zip"
        Path build = directory.resolve(BUILD_DIRECTORY)
        Path zip = build.resolve(zipName)
        String shown = GradleRunner.line("${BUILD_DIRECTORY}/${zipName}")

        if (Files.exists(build, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(build, LinkOption.NOFOLLOW_LINKS)) {
            err("The ${BUILD_DIRECTORY} directory of the project in ${GradleRunner.line(directory)} is a file or a link, so the bug report cannot be written to it. ${notDone()}")
            return 1
        }
        if (Files.exists(zip, LinkOption.NOFOLLOW_LINKS)) {
            if (!force) {
                err("${shown} exists already and was not replaced. Give --force to replace it")
                return 1
            }
            if (!Files.isRegularFile(zip, LinkOption.NOFOLLOW_LINKS)) {
                err("${shown} is not a file, and was not replaced. ${notDone()}")
                return 1
            }
        }

        Collected collected = collect(directory)
        if (collected.refusal != null) {
            err("${collected.refusal}. ${notDone()}")
            return 1
        }
        if (collected.files.isEmpty()) {
            err("The project in ${GradleRunner.line(directory)} has no files under ${DIRECTORIES.join(' or ')} and no ${BUILD_FILE}. ${notDone()}")
            return 1
        }

        Path written = null
        try {
            Files.createDirectories(build)
            written = Files.createTempFile(build, 'bug-report', '.zip.part')
            String refusal = write(directory, collected.files, written)
            if (refusal != null) {
                err("${refusal}. ${notDone()}")
                return 1
            }
            if (force) {
                Files.move(written, zip, StandardCopyOption.REPLACE_EXISTING)
            } else {
                Files.move(written, zip)
            }
            written = null
        } catch (FileAlreadyExistsException ignored) {
            err("${shown} exists already and was not replaced. Give --force to replace it")
            return 1
        } catch (IOException e) {
            err("The bug report could not be written to ${shown}: ${GradleRunner.line(e.getMessage())}")
            return 1
        } finally {
            if (written != null) {
                Files.deleteIfExists(written)
            }
        }

        List<String> configuration = collected.files.collect { Path file -> directory.relativize(file).join('/') }.findAll { String file -> CONFIGURATION.matcher(file).matches() }
        if (configuration) {
            warning("The bug report holds the configuration of the application, which is where passwords usually are: ${GradleRunner.line(configuration.join(', '))}. " +
                    'Look through it before the ZIP is given to anyone')
        }
        out("| Created bug report ZIP: ${shown}")
        out("| It holds ${collected.files.size()} file(s): what is under ${DIRECTORIES.join(' and ')}, and ${BUILD_FILE}, as they are")
        if (collected.links > 0 || collected.secrets > 0) {
            out("| Left out: ${collected.links} link(s), which are not followed, and ${collected.secrets} file(s) that are keys by their name: keys, stores of keys and files of credentials")
        }
        if (collected.directories > 0 || collected.shared > 0) {
            out("| Left out: ${collected.directories} directory(ies) named ${BUILD_DIRECTORIES.join(' or ')}, and ${collected.shared} file(s) that have another name as well, as a hard link, and so may be a file from elsewhere")
        }
        if (collected.unnamed > 0) {
            warning("Left out: ${collected.unnamed} file(s) with a control character in the name, which a ZIP file is not given, the first of them ${GradleRunner.line(collected.firstUnnamed)}")
        }
        return 0
    }

    private static Collected collect(Path directory) {
        Collected collected = new Collected()
        // how many names a file has is known where the file system is one of unix
        boolean hardLinks = directory.getFileSystem().supportedFileAttributeViews().contains('unix')
        Path buildFile = directory.resolve(BUILD_FILE)
        if (Files.isSymbolicLink(buildFile)) {
            collected.links++
        } else if (Files.isRegularFile(buildFile, LinkOption.NOFOLLOW_LINKS)) {
            // the build file is held to what every other file is: one with another name elsewhere is left out
            if (hardLinks && names(buildFile) > 1) {
                collected.shared++
            } else {
                collected.add(buildFile, Files.size(buildFile))
            }
        }
        for (String each : DIRECTORIES) {
            Path root = directory.resolve(each)
            if (Files.isSymbolicLink(root)) {
                collected.links++
                continue
            }
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                continue
            }
            // without the option to follow links, a link is visited as a file and what it points to is not read
            Files.walkFileTree(root, Collections.emptySet(), MAX_DEPTH, new SimpleFileVisitor<Path>() {

                @Override
                FileVisitResult preVisitDirectory(Path visited, BasicFileAttributes attributes) {
                    String name = visited.getFileName().toString()
                    if (name in NOT_SOURCES_DIRECTORIES) {
                        return FileVisitResult.SKIP_SUBTREE
                    }
                    if (name in BUILD_DIRECTORIES) {
                        collected.directories++
                        return FileVisitResult.SKIP_SUBTREE
                    }
                    return FileVisitResult.CONTINUE
                }

                @Override
                FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    String fileName = file.getFileName().toString()
                    if (attributes.isSymbolicLink()) {
                        collected.links++
                    } else if (attributes.isDirectory()) {
                        collected.refusal = "The sources of the project are more than ${MAX_DEPTH} directories deep at ${GradleRunner.line(directory.relativize(file).join('/'))}, which is more than a bug report holds"
                    } else if (!attributes.isRegularFile() || notSource(fileName)) {
                        return FileVisitResult.CONTINUE
                    } else if (secret(fileName)) {
                        collected.secrets++
                    } else if (NOT_SHOWN.matcher(directory.relativize(file).toString()).find()) {
                        collected.unnamed++
                        String unnamed = directory.relativize(file).toString()
                        if (collected.firstUnnamed == null || unnamed < collected.firstUnnamed) {
                            collected.firstUnnamed = unnamed
                        }
                    } else if (hardLinks && names(file) > 1) {
                        collected.shared++
                    } else {
                        collected.add(file, attributes.size())
                    }
                    return collected.refusal == null ? FileVisitResult.CONTINUE : FileVisitResult.TERMINATE
                }

                @Override
                FileVisitResult visitFileFailed(Path file, IOException e) {
                    collected.refusal = "${GradleRunner.line(directory.relativize(file).join('/'))} cannot be read"
                    return FileVisitResult.TERMINATE
                }
            })
            if (collected.refusal != null) {
                break
            }
        }
        collected.files.sort()
        return collected
    }

    private static boolean notSource(String name) {
        return name in NOT_SOURCES || name.endsWith('~') || name.startsWith('._') || name.startsWith('.#') ||
                (name.length() > 1 && name.startsWith('#') && name.endsWith('#')) ||
                (name.length() > 1 && name.startsWith('%') && name.endsWith('%'))
    }

    private static boolean secret(String name) {
        String lower = name.toLowerCase(Locale.ROOT)
        String copy
        while ((copy = COPY_ENDINGS.find { String ending -> lower.length() > ending.length() && lower.endsWith(ending) }) != null) {
            lower = lower.substring(0, lower.length() - copy.length())
        }
        String original = lower
        if (original in SECRET_NAMES || SECRET_ENDINGS.any { String ending -> original.endsWith(ending) }) {
            return true
        }
        if (SECRET_STARTS.any { String start -> original.startsWith(start) }) {
            return true
        }
        return SECRET_WORDS.any { String start -> original.startsWith(start) } && !CODE_ENDINGS.any { String ending -> original.endsWith(ending) }
    }

    // how many names the file has: more than one for a file that is linked to from elsewhere
    private static int names(Path file) {
        try {
            Object count = Files.getAttribute(file, 'unix:nlink', LinkOption.NOFOLLOW_LINKS)
            return count instanceof Number ? ((Number) count).intValue() : 1
        } catch (IOException | UnsupportedOperationException | IllegalArgumentException ignored) {
            return 1
        }
    }

    // The files are read again here, and may have grown since they were listed: the bound holds for what is written
    private static String write(Path directory, List<Path> files, Path target) {
        long total = 0
        byte[] buffer = new byte[8192]
        ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target))
        try {
            for (Path file : files) {
                zip.putNextEntry(new ZipEntry(directory.relativize(file).join('/')))
                InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)
                try {
                    int count
                    while ((count = input.read(buffer)) != -1) {
                        total += count
                        if (total > MAX_BYTES) {
                            return tooLarge()
                        }
                        zip.write(buffer, 0, count)
                    }
                } finally {
                    input.close()
                }
                zip.closeEntry()
            }
        } finally {
            zip.close()
        }
        return null
    }

    static String tooLarge() {
        return "The sources of the project are larger than ${MAX_BYTES.intdiv(1024 * 1024)} MiB, which is more than a bug report holds"
    }

    @CompileStatic
    private static class Collected {

        final List<Path> files = []
        long bytes
        int links
        int secrets
        int directories
        int shared
        int unnamed
        String firstUnnamed
        String refusal

        void add(Path file, long size) {
            files.add(file)
            bytes += size
            if (files.size() > MAX_FILES) {
                refusal = "The sources of the project are more than ${MAX_FILES} files, which is more than a bug report holds"
            } else if (bytes > MAX_BYTES) {
                refusal = tooLarge()
            }
        }
    }
}
