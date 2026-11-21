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

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.security.CodeSource
import java.util.jar.JarFile
import java.util.jar.Manifest
import groovy.transform.CompileStatic
import org.grails.forge.io.FileSystemOutputHandler
import org.grails.forge.util.VersionInfo

/**
 * The Grails wrapper a project has committed, {@code grailsw}, {@code grailsw.bat} and
 * {@code grails-wrapper.jar}, and its replacement by the wrapper of this version of Grails: the
 * three files a new application is generated with, which this command line has with it.
 *
 * <p>A file is written beside the one it replaces and then moved over it, so that a run that is
 * interrupted leaves the wrapper that was there or the new one, and never a part of one. The mode of
 * the file that was there is kept. A link is not followed and not replaced, and neither is a file
 * that cannot be written to: each is said, and the others are replaced all the same.</p>
 *
 * <p>A file that is in use is not replaced. On Windows that is {@code grails-wrapper.jar} when the
 * command line was started by it, which the system refuses to replace while it is open, and
 * {@code grailsw.bat} then, which the command interpreter is still reading. The new file is written
 * beside it, with {@link #BESIDE} after its name, and the user is told the one thing that is left
 * to do.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class WrapperFiles {

    /** The command that replaces the wrapper of a project, which the Grails wrapper has read as its first argument since Grails 7. */
    public static final String COMMAND = 'update-wrapper'

    /** The script that starts the wrapper on Linux and macOS. */
    public static final String SCRIPT = 'grailsw'

    /** The script that starts the wrapper on Windows. */
    public static final String BATCH = 'grailsw.bat'

    /** The jar the scripts start, which downloads the command line and starts it. */
    public static final String JAR = 'grails-wrapper.jar'

    /** The files of a wrapper, as they are named in a project. */
    public static final List<String> NAMES = Collections.unmodifiableList([SCRIPT, BATCH, JAR])

    /** What follows the name of a new file that is written beside the one it could not replace. */
    public static final String BESIDE = '.new'

    private static final String RESOURCES = 'grails-wrapper/'
    private static final String START_CLASS = 'grails.init.Start'
    private static final long MAX_JAR = 16L * 1024 * 1024
    private static final Mover MOVE = { Path from, Path to ->
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE)
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING)
        }
    } as Mover

    private final Path directory

    /** What moves a written file to its place. */
    Mover mover = MOVE

    /** Whether the batch file of the directory is being read by the command interpreter that started this command line. */
    boolean batchFileRuns

    /**
     * @param directory the directory the wrapper is in
     */
    WrapperFiles(Path directory) {
        this.directory = directory
        this.batchFileRuns = File.separatorChar == ('\\' as char) && startedBy(directory)
    }

    /** Moves a file that was written over the one it replaces. */
    static interface Mover {

        void move(Path from, Path to) throws IOException
    }

    /**
     * @param project a Grails project
     * @return the directory its wrapper is in: the directory of the project or, for a project that is
     * a part of a larger build, the root of that build; {@code null} when neither has a
     * {@code grails-wrapper.jar}
     */
    static Path directoryOf(ConsoleProject project) {
        for (Path each : [project.directory, project.getBuildRoot()]) {
            if (each != null && Files.exists(each.resolve(JAR), LinkOption.NOFOLLOW_LINKS)) {
                return each
            }
        }
        return null
    }

    /**
     * @param current the directory a command is run in
     * @return whether the directory is in a Grails project of a version this command line works with
     * that has a wrapper, which is where {@code update-wrapper} replaces one
     */
    static boolean applies(Path current) {
        ConsoleProject project = ConsoleProject.find(current)
        return project != null && directoryOf(project) != null && EarlierGrailsProject.earlierVersion(current) == null
    }

    /**
     * @return whether {@code update-wrapper} replaces a wrapper in the directory this command line is run in
     * @see #applies(Path)
     */
    static boolean appliesHere() {
        try {
            return applies(FileSystemOutputHandler.getDefaultBaseDirectory().toPath())
        } catch (IOException ignored) {
            // without a current directory there is no project
            return false
        }
    }

    /**
     * @param directory a directory that has a wrapper
     * @return whether the jar of that wrapper started this JVM, as the scripts of a wrapper do: the
     * class the scripts start is then on the class path, and is of that jar
     */
    static boolean startedBy(Path directory) {
        try {
            CodeSource source = Class.forName(START_CLASS, false, ClassLoader.getSystemClassLoader()).getProtectionDomain().getCodeSource()
            return source != null && source.getLocation() != null && Files.isSameFile(Path.of(source.getLocation().toURI()), directory.resolve(JAR))
        } catch (ClassNotFoundException | LinkageError | IOException | URISyntaxException | RuntimeException ignored) {
            // not started by a wrapper, or by one whose jar cannot be told from here
            return false
        }
    }

    /**
     * Replaces the files of the wrapper that differ from those of this version of Grails.
     *
     * @return what was done
     */
    Result update() {
        Result result = new Result(directory, formerVersion(), VersionInfo.getGrailsVersion())
        for (String name : NAMES) {
            replace(name, result)
        }
        return result
    }

    private void replace(String name, Result result) {
        Path target = directory.resolve(name)
        if (Files.isSymbolicLink(target)) {
            result.failed.put(name, 'it is a symbolic link, and a link is not followed')
            return
        }
        boolean there = Files.exists(target, LinkOption.NOFOLLOW_LINKS)
        if (there && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            result.failed.put(name, 'it is not a file')
            return
        }
        byte[] content = content(name)
        Path written = null
        try {
            if (there && Files.size(target) == content.length && Arrays.equals(Files.readAllBytes(target), content)) {
                return
            }
            if (there && !Files.isWritable(target)) {
                result.failed.put(name, 'it is read-only')
                return
            }
            written = Files.createTempFile(directory, name + '.', '.tmp')
            Files.write(written, content)
            mode(written, there ? target : null, name)
            if (name == BATCH && batchFileRuns) {
                Files.move(written, beside(name), StandardCopyOption.REPLACE_EXISTING)
                result.beside.put(name, 'the command interpreter that started this command is reading it')
                return
            }
            try {
                mover.move(written, target)
                result.replaced.add(name)
            } catch (IOException refused) {
                // the system does not replace a file that is open, as the jar of the wrapper that runs is on Windows
                Files.move(written, beside(name), StandardCopyOption.REPLACE_EXISTING)
                result.beside.put(name, "it could not be replaced (${reason(refused)})".toString())
            }
        } catch (IOException e) {
            result.failed.put(name, reason(e))
            if (written != null) {
                try {
                    Files.deleteIfExists(written)
                } catch (IOException ignored) {
                    // what could not be moved cannot be deleted either: it is named *.tmp and is no file of the wrapper
                }
            }
        }
    }

    private Path beside(String name) {
        return directory.resolve(name + BESIDE)
    }

    // the mode of the file that is replaced, and for a file that was not there the one a generated application has
    private static void mode(Path written, Path former, String name) throws IOException {
        if (Files.getFileAttributeView(written, PosixFileAttributeView, LinkOption.NOFOLLOW_LINKS) == null) {
            return
        }
        Set<PosixFilePermission> permissions = former != null ? Files.getPosixFilePermissions(former, LinkOption.NOFOLLOW_LINKS) :
                PosixFilePermissions.fromString(name == SCRIPT ? 'rwxr-xr-x' : 'rw-r--r--')
        Files.setPosixFilePermissions(written, permissions)
    }

    private static byte[] content(String name) {
        InputStream stream = WrapperFiles.getClassLoader().getResourceAsStream(RESOURCES + name)
        if (stream == null) {
            throw new IllegalStateException("This command line has no ${name} of the Grails wrapper with it")
        }
        return stream.withCloseable { InputStream opened -> opened.readAllBytes() }
    }

    // the version of Grails the jar that is there is of, as its manifest says; null where it does not say
    private String formerVersion() {
        Path jar = directory.resolve(JAR)
        try {
            if (!Files.isRegularFile(jar, LinkOption.NOFOLLOW_LINKS) || Files.size(jar) > MAX_JAR) {
                return null
            }
            return new JarFile(jar.toFile()).withCloseable { JarFile opened ->
                Manifest manifest = opened.getManifest()
                String version = manifest == null ? null : manifest.getMainAttributes().getValue('Implementation-Version')
                return version != null && version ==~ /[A-Za-z0-9][A-Za-z0-9.\-]{0,60}/ ? version : null
            }
        } catch (IOException | RuntimeException ignored) {
            return null
        }
    }

    private static String reason(IOException e) {
        String message = e.getMessage()
        return GradleRunner.line(message == null || message.isEmpty() ? e.getClass().getSimpleName() : "${e.getClass().getSimpleName()}: ${message}")
    }

    /** What an update of the wrapper of a directory did. */
    static final class Result {

        /** The directory the wrapper is in. */
        final Path directory

        /** The version of Grails the wrapper was of, or {@code null} where its jar does not say. */
        final String former

        /** The version of Grails the wrapper is of now. */
        final String version

        /** The files that were replaced, or written where there was none. */
        final List<String> replaced = []

        /** The files that are in use and whose replacement is written beside them, each with why. */
        final Map<String, String> beside = [:]

        /** The files that were not replaced and are not written anywhere, each with why. */
        final Map<String, String> failed = [:]

        Result(Path directory, String former, String version) {
            this.directory = directory
            this.former = former
            this.version = version
        }

        /**
         * @return whether the wrapper of the directory is that of this version of Grails now, in all its files
         */
        boolean isComplete() {
            return beside.isEmpty() && failed.isEmpty()
        }

        /**
         * @return one line that says what was replaced, or that nothing had to be
         */
        String done() {
            String where = "The Grails wrapper of the project in ${GradleRunner.line(directory)}"
            if (replaced.isEmpty()) {
                return isComplete() ? "${where} is that of Grails ${version}: nothing was changed" : null
            }
            String was = former != null && former != version ? " was that of Grails ${former} and" : ''
            return "${where}${was} is now that of Grails ${version}: ${replaced.join(', ')} ${replaced.size() == 1 ? 'was' : 'were'} replaced. " +
                    "Commit ${replaced.size() == 1 ? 'it' : 'them'}."
        }

        /**
         * @return a line for each file that was not replaced, which says why and what is left to do by hand
         */
        List<String> left() {
            List<String> lines = []
            beside.each { String name, String why ->
                lines.add(("${name} was not replaced: ${why}. The one of Grails ${version} is written beside it as ${name}${BESIDE}. " +
                        "Once this command has ended, move it over ${name} in ${GradleRunner.line(directory)} (${moveCommand(name)}) and commit it").toString())
            }
            failed.each { String name, String why ->
                lines.add(("${name} was not replaced: ${why}. Replace it by hand in ${GradleRunner.line(directory)} with the ${name} of Grails ${version}, " +
                        "which is at the root of an application that create-app creates and in apache-grails-wrapper-${version}-bin.zip of the release").toString())
            }
            return lines
        }

        private static String moveCommand(String name) {
            return File.separatorChar == ('\\' as char) ? "move /y ${name}${BESIDE} ${name}" : "mv -f ${name}${BESIDE} ${name}"
        }
    }
}
