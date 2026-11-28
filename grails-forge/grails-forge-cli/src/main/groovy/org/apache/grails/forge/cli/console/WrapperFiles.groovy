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

import java.nio.charset.StandardCharsets
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
import java.util.regex.Matcher
import java.util.regex.Pattern
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
 * <p>A file that is in use is not replaced under what uses it. On Windows that is
 * {@code grails-wrapper.jar} when the command line was started by it, which the system refuses to
 * replace while it is open, and {@code grailsw.bat} then, which the command interpreter is still
 * reading. The new file is written beside it, with {@link #BESIDE} after its name, and the wrapper
 * replaces it by itself when it is next run: the scripts of this version move a jar that is written
 * beside the one in the directory over it before they start Java. A batch file of this version is
 * replaced while it is read, since what it does after Java has ended it finds by a label, in the
 * file that is there then; a batch file of Grails 7 or 8 is replaced by one that ends the run where
 * the command interpreter goes on reading, see {@link #betweenRuns}, and that puts the new wrapper
 * in its place when it is next run. Only where neither can be done is the user told the one thing
 * that is left to do.</p>
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
    private static final long MAX_SCRIPT = 1024L * 1024
    private static final String LINE_END = '\r\n'
    // the command interpreter reads a line of at most 8191 characters
    private static final int MAX_FILL_LINE = 4000
    // what the scripts of this version, and the batch file that stands in between two runs, have in the line that moves the jar
    private static final String MOVES_THE_JAR = "move /y \"%~dp0${JAR}${BESIDE}\" \"%~dp0${JAR}\""
    private static final String COMMAND_INTERPRETER = 'cmd.exe'
    // the last command of the batch file: its exit code is then the one of the batch file also where the
    // batch file is one side of an operator of the command interpreter, which is how the scripts of Gradle 9 end
    private static final String ENDS_THE_RUN = '@"%COMSPEC%" /c exit %ERRORLEVEL%'
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
        this.batchFileRuns = File.separatorChar == ('\\' as char) && startedBy(directory) && startedByCommandInterpreter()
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
     * @return whether the process that started this JVM is the command interpreter of Windows, which is
     * what reads a batch file, also for a batch file that is started from PowerShell; true as well where
     * the system does not say what that process is. A wrapper that was started by its other script, as
     * in a shell of Git for Windows, has no batch file that is being read
     */
    static boolean startedByCommandInterpreter() {
        try {
            Optional<ProcessHandle> parent = ProcessHandle.current().parent()
            Optional<String> program = parent.isPresent() ? parent.get().info().command() : Optional.<String>empty()
            return !program.isPresent() || program.get().toLowerCase(Locale.ROOT).endsWith(COMMAND_INTERPRETER)
        } catch (RuntimeException ignored) {
            return true
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
                // what an earlier run wrote beside it would be moved over it by the scripts when the wrapper is next run
                Files.deleteIfExists(beside(name))
                return
            }
            if (there && !Files.isWritable(target)) {
                result.failed.put(name, 'it is read-only')
                return
            }
            written = Files.createTempFile(directory, name + '.', '.tmp')
            Files.write(written, content)
            mode(written, there ? target : null, name)
            if (name == BATCH && batchFileRuns && there && !endsByLabel(target, content)) {
                Files.move(written, beside(name), StandardCopyOption.REPLACE_EXISTING)
                String reading = 'the command interpreter that started this command is reading it'
                byte[] between = betweenRuns(target, result.version)
                if (between != null) {
                    written = Files.createTempFile(directory, name + '.', '.tmp')
                    Files.write(written, between)
                    try {
                        mover.move(written, target)
                        result.pending.put(name, reading)
                        return
                    } catch (IOException ignored) {
                        // then the batch file stays the one it was, and the new one is beside it
                        Files.deleteIfExists(written)
                    }
                }
                result.beside.put(name, reading)
                return
            }
            try {
                mover.move(written, target)
                result.replaced.add(name)
                Files.deleteIfExists(beside(name))
            } catch (IOException refused) {
                // the system does not replace a file that is open, as the jar of the wrapper that runs is on Windows
                Files.move(written, beside(name), StandardCopyOption.REPLACE_EXISTING)
                String why = "it could not be replaced (${reason(refused)})".toString()
                if (name == JAR && completesItself(result)) {
                    result.pending.put(name, why)
                } else {
                    result.beside.put(name, why)
                }
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

    // Whether the scripts that are in the directory now move a jar that is written beside the one in use
    // over it when the wrapper is next run, which the scripts of this version do before they start Java:
    // the batch file is the one of this version or the one that stands in for it between two runs.
    private boolean completesItself(Result result) {
        if (result.failed.containsKey(BATCH) || result.beside.containsKey(BATCH)) {
            return false
        }
        try {
            Path batch = directory.resolve(BATCH)
            return Files.isRegularFile(batch, LinkOption.NOFOLLOW_LINKS) && Files.size(batch) <= MAX_SCRIPT &&
                    new String(Files.readAllBytes(batch), StandardCharsets.ISO_8859_1).contains(MOVES_THE_JAR)
        } catch (IOException ignored) {
            return false
        }
    }

    // Whether the command interpreter comes to the end of the batch file it is reading by labels alone,
    // which it looks for in the file that is there then: the scripts of Gradle 9, and so those of this
    // version, end the line that starts Java with a call of a label and a jump to it, and say that the
    // label is kept so that a script can be replaced while it runs. Such a file is replaced as any other.
    private static boolean endsByLabel(Path running, byte[] content) throws IOException {
        if (Files.size(running) > MAX_SCRIPT) {
            return false
        }
        List<String> starts = new String(Files.readAllBytes(running), StandardCharsets.ISO_8859_1).readLines().findAll { String line -> line.contains(START_CLASS) }
        return starts.size() == 1 && starts[0].trim() ==~ /.*& *call +:exitWithErrorLevel *& *goto +:?exitWithErrorLevel/ &&
                new String(content, StandardCharsets.ISO_8859_1).readLines().any { String line -> line.trim() == ':exitWithErrorLevel' }
    }

    /**
     * The batch file that stands in for the one the command interpreter is reading, until the wrapper is
     * next run. The command interpreter reads a batch file a command at a time: it keeps how far into
     * the file it has read, runs the command, and opens the file again at that place for the next one.
     * While Java runs, that place is the end of the line that started it, as the scripts of Gradle 8 and
     * earlier, and so the wrappers of Grails 7 and 8, have that line. So the file that is put there has,
     * at that very place, a line that ends the batch file with the exit code of Java, which is all the
     * rest of the earlier file did; and from its first line it is a wrapper: it moves the files that are
     * written beside the ones in use over them and starts the batch file that is then there.
     *
     * @param running the batch file that is being read
     * @param version the version of Grails the new wrapper is of
     * @return the content, or {@code null} for a batch file whose line that starts Java is not found or
     * is too near the beginning for what stands before it
     */
    static byte[] betweenRuns(Path running, String version) throws IOException {
        if (Files.size(running) > MAX_SCRIPT) {
            return null
        }
        Integer place = placeAfterStart(new String(Files.readAllBytes(running), StandardCharsets.ISO_8859_1))
        if (place == null) {
            return null
        }
        String first = [
                "@rem The Grails wrapper of this project is being replaced by that of Grails ${version}: this file ended the run that",
                '@rem replaced it, and puts the new wrapper in its place when grailsw.bat is next run. It is not to be committed.',
                "@if exist \"%~dp0${JAR}${BESIDE}\" move /y \"%~dp0${JAR}${BESIDE}\" \"%~dp0${JAR}\" >NUL 2>&1",
                "@if exist \"%~dp0${BATCH}${BESIDE}\" move /y \"%~dp0${BATCH}${BESIDE}\" \"%~dp0${BATCH}\" >NUL 2>&1 && \"%~dp0${BATCH}\" %*",
                "@echo The Grails wrapper of this project is half replaced, and ${BATCH}${BESIDE}, which completes it, is not there or cannot be moved over ${BATCH}. 1>&2",
                "@echo Run update-wrapper of an installed grails of Grails ${version} in this directory, or take ${BATCH} from an application that its create-app creates. 1>&2",
                '@exit /b 1',
        ].join(LINE_END) + LINE_END
        // lines of spaces up to the place, which nothing reads: the run from the first line has ended above
        int fill = place - first.length()
        if (fill == 1 || fill < 0) {
            return null
        }
        StringBuilder text = new StringBuilder(first)
        while (fill > 0) {
            int line = fill - MAX_FILL_LINE == 1 ? MAX_FILL_LINE - 1 : Math.min(fill, MAX_FILL_LINE)
            text.append(' ' * (line - LINE_END.length())).append(LINE_END)
            fill -= line
        }
        text.append(ENDS_THE_RUN).append(LINE_END)
        return text.toString().getBytes(StandardCharsets.ISO_8859_1)
    }

    // Where the command interpreter goes on reading once Java has ended: after the one line that starts
    // the class of the wrapper with the arguments of the batch file, which is a command by itself and in
    // no block. Null where there is no such line, or more than one.
    private static Integer placeAfterStart(String text) {
        Integer place = null
        int depth = 0
        int at = 0
        while (at < text.length()) {
            int end = text.indexOf('\n', at)
            int next = end < 0 ? text.length() : end + 1
            String line = text.substring(at, end < 0 ? text.length() : end).trim()
            String lower = line.toLowerCase(Locale.ROOT)
            boolean comment = lower.startsWith('rem ') || lower.startsWith('@rem ') || lower == 'rem' || lower == '@rem' || line.startsWith('::')
            if (!comment) {
                if (line.contains(START_CLASS)) {
                    if (place != null || depth != 0 || !line.endsWith('%*') || !line.startsWith('"')) {
                        return null
                    }
                    place = next
                }
                if (line.endsWith('(')) {
                    depth++
                }
                if (line.startsWith(')')) {
                    depth--
                }
            }
            at = next
        }
        return place
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
        return versionOf(directory)
    }

    /**
     * @param directory a directory that has a wrapper
     * @return the version of Grails the {@code grails-wrapper.jar} of the directory is of, as its
     * manifest says; {@code null} where it does not say, and where the jar is no file of a size a
     * wrapper has
     * @since 9.0
     */
    static String versionOf(Path directory) {
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

    /**
     * @param directory a directory that has a wrapper
     * @return the files of the wrapper that are not those of this version of Grails, which
     * {@code update-wrapper} would write, in the order of {@link #NAMES}: a file that is missing, a
     * link, and a file that cannot be read among them
     * @since 9.0
     */
    static List<String> differing(Path directory) {
        return NAMES.findAll { String name ->
            Path file = directory.resolve(name)
            try {
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > (name == JAR ? MAX_JAR : MAX_SCRIPT)) {
                    return true
                }
                byte[] content = content(name)
                return Files.size(file) != content.length || !Arrays.equals(Files.readAllBytes(file), content)
            } catch (IOException ignored) {
                return true
            }
        }
    }

    private static String reason(IOException e) {
        String message = e.getMessage()
        return GradleRunner.line(message == null || message.isEmpty() ? e.getClass().getSimpleName() : "${e.getClass().getSimpleName()}: ${message}")
    }

    /** What an update of the wrapper of a directory did. */
    static final class Result {

        private static final Pattern MAJOR = Pattern.compile(/^(\d{1,4})\./)

        /** The directory the wrapper is in. */
        final Path directory

        /** The version of Grails the wrapper was of, or {@code null} where its jar does not say. */
        final String former

        /** The version of Grails the wrapper is of now. */
        final String version

        /** The files that were replaced, or written where there was none. */
        final List<String> replaced = []

        /**
         * The files that are in use, whose replacement is written beside them and is moved over them by
         * the wrapper itself when it is next run, each with why.
         */
        final Map<String, String> pending = [:]

        /** The files that are in use and whose replacement is written beside them for the user to move, each with why. */
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
            return pending.isEmpty() && beside.isEmpty() && failed.isEmpty()
        }

        /**
         * @return whether nothing is left for the user to do but to commit the wrapper: every file is
         * that of this version, or is made so by the wrapper when it is next run
         */
        boolean isSettled() {
            return beside.isEmpty() && failed.isEmpty()
        }

        /**
         * @return whether a wrapper of an earlier major version of Grails than this one was replaced, or
         * one that does not say its version, as a step of an upgrade
         * @since 9.0
         */
        boolean isFromEarlierMajor() {
            if (replaced.isEmpty() && pending.isEmpty()) {
                return false
            }
            Integer was = majorOf(former)
            Integer now = majorOf(version)
            return was == null || (now != null && was < now)
        }

        private static Integer majorOf(String version) {
            Matcher matched = version == null ? null : MAJOR.matcher(version)
            return matched != null && matched.find() ? Integer.valueOf(matched.group(1)) : null
        }

        /**
         * @return one line that says what was replaced, or that nothing had to be
         */
        String done() {
            String where = "The Grails wrapper of the project in ${GradleRunner.line(directory)}"
            String was = former != null && former != version ? " was that of Grails ${former} and" : ''
            if (!pending.isEmpty()) {
                // one line for all of it: what was replaced, what the wrapper replaces itself, and when to commit
                String names = pending.keySet().join(' and ')
                String inUse = "${names} ${pending.size() == 1 ? 'is' : 'are'} in use by the wrapper that runs this command and ${pending.size() == 1 ? 'is' : 'are'} " +
                        'replaced by the wrapper itself when it is next run'
                String done = replaced.isEmpty() ? '' : "${replaced.join(', ')} ${replaced.size() == 1 ? 'was' : 'were'} replaced, and "
                return "${where}${was} is being replaced by that of Grails ${version}: ${done}${inUse}. " +
                        "Run the wrapper once more, and then commit ${NAMES.join(', ')}; the files named ${BESIDE} are gone by then."
            }
            if (replaced.isEmpty()) {
                return isComplete() ? "${where} is that of Grails ${version}: nothing was changed" : null
            }
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
            // as it is typed in the command interpreter and in PowerShell alike, where move is another command
            return File.separatorChar == ('\\' as char) ? "cmd /c move /y ${name}${BESIDE} ${name}" : "mv -f ${name}${BESIDE} ${name}"
        }
    }
}
