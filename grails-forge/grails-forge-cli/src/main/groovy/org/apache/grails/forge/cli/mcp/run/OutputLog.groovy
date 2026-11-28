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
package org.apache.grails.forge.cli.mcp.run

import java.nio.charset.StandardCharsets
import java.nio.file.DirectoryStream
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileSystemNotFoundException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern
import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.console.ConsoleProject
import org.apache.grails.forge.cli.console.GradleRunner
import org.apache.grails.forge.cli.console.RunDirectory
import org.apache.grails.forge.cli.mcp.McpException
import org.apache.grails.forge.cli.mcp.McpNames

/**
 * The whole output of each call of {@code grails mcp} in a project, one file for each call in
 * {@code .grails/run/mcp}, named {@code <yyyyMMdd-HHmmss-SSS>-<tool>.log}: what the result of a call
 * links when it shortens the output, and a resource the client can read. The directory is kept under
 * the rules of {@code .grails/run}: it and its files are their owner's alone, nothing is read or written
 * through a link, and it is left out of a commit. The newest {@value #KEPT} files are kept.
 *
 * <p>Outside a project nothing is kept.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class OutputLog {

    /** How many files are kept: the newest. */
    public static final int KEPT = 50

    /** What a file of the directory is named: the moment the call ended, the name of its tool, and a number where two came at once. */
    public static final Pattern NAME = Pattern.compile('[0-9]{8}-[0-9]{6}-[0-9]{3}-[A-Za-z0-9_-]{1,64}(-[0-9]{1,3})?\\.log')

    /** The media type of a file of the directory, read as a resource. */
    public static final String MIME_TYPE = 'text/plain'

    /** Where the directory is in a project. */
    public static final String LOCATION = RunDirectory.LOCATION + '/' + McpNames.LOG_DIRECTORY

    private static final DateTimeFormatter MOMENT = DateTimeFormatter.ofPattern('yyyyMMdd-HHmmss-SSS', Locale.ROOT)
    private static final Set<PosixFilePermission> OWNER_DIRECTORY = PosixFilePermissions.fromString('rwx------')
    private static final int MOST_AT_ONCE = 100

    private final Path directory

    /**
     * @param directory the directory of the server: the project it is in has the files
     */
    OutputLog(Path directory) {
        this.directory = Objects.requireNonNull(directory, 'directory')
    }

    /**
     * Keeps the whole output of a call, in the project of the directory, and deletes the oldest files
     * beyond the newest {@value #KEPT}.
     *
     * @param tool the name of the tool called
     * @param output the whole output of the call
     * @return the file, or why there is none
     */
    Kept keep(String tool, String output) {
        ConsoleProject project
        try {
            project = ConsoleProject.find(directory)
        } catch (IOException e) {
            return Kept.none("the project could not be found: ${GradleRunner.line(e.message)}".toString())
        }
        if (project == null) {
            return Kept.none('grails mcp keeps no file of the output outside a project')
        }
        Path file
        try {
            Path logs = logDirectory(project, true)
            file = create(logs, MOMENT.format(LocalDateTime.now()), tool)
            Files.write(file, output.getBytes(StandardCharsets.UTF_8))
            prune(logs)
        } catch (IOException e) {
            return Kept.none("the whole output could not be kept: ${GradleRunner.line(e.message)}".toString())
        }
        return new Kept(file, null)
    }

    /**
     * Reads a file of the directory, by its URI, as a resource.
     *
     * @param uri the URI of a file, {@code file:} and the path
     * @return the file as the protocol's {@code ReadResourceResult}, or {@code null} for a URI that is not
     * one of a file of the directory
     * @throws McpException when the file is not there any more, or cannot be read
     */
    Map<String, Object> read(String uri) throws McpException {
        Path file = fileOf(uri)
        if (file == null) {
            return null
        }
        String text
        try {
            text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
        } catch (IOException e) {
            throw new McpException(McpException.INVALID_PARAMS, "There is no resource ${uri}: ${GradleRunner.line(e.message)}".toString())
        }
        Map<String, Object> content = new LinkedHashMap<>()
        content.put('uri', uri)
        content.put('mimeType', MIME_TYPE)
        content.put('text', text)
        Map<String, Object> result = new LinkedHashMap<>()
        result.put('contents', [content])
        return result
    }

    // the file of the directory a URI names, where it is one: a regular file that is no link, named as a file of the
    // directory is, in the directory of the project of the server, which is no link and is the user's own
    private Path fileOf(String uri) {
        if (uri == null || !uri.startsWith('file:')) {
            return null
        }
        Path named
        try {
            named = Paths.get(URI.create(uri))
        } catch (IllegalArgumentException | FileSystemNotFoundException ignored) {
            return null
        }
        if (named.fileName == null || !NAME.matcher(named.fileName.toString()).matches()) {
            return null
        }
        try {
            ConsoleProject project = ConsoleProject.find(directory)
            Path logs = project == null ? null : logDirectory(project, false)
            if (logs == null || named.parent == null || !Files.isSameFile(named.parent, logs)) {
                return null
            }
            Path file = logs.resolve(named.fileName.toString())
            return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) ? file : null
        } catch (IOException ignored) {
            return null
        }
    }

    // .grails/run/mcp, under the rules of .grails/run: created for its owner alone where asked to, never a link
    private static Path logDirectory(ConsoleProject project, boolean create) throws IOException {
        Path run = create ? RunDirectory.of(project) : RunDirectory.existing(project)
        if (run == null) {
            return null
        }
        Path logs = run.resolve(McpNames.LOG_DIRECTORY)
        if (create && !Files.exists(logs, LinkOption.NOFOLLOW_LINKS)) {
            try {
                boolean posix = logs.fileSystem.supportedFileAttributeViews().contains('posix')
                if (posix) {
                    Files.createDirectory(logs, PosixFilePermissions.asFileAttribute(OWNER_DIRECTORY))
                } else {
                    Files.createDirectory(logs)
                }
            } catch (FileAlreadyExistsException ignored) {
                // made since it was looked for: held to what an existing one is held to below
            }
        }
        if (!Files.exists(logs, LinkOption.NOFOLLOW_LINKS)) {
            return null
        }
        if (Files.isSymbolicLink(logs) || !Files.isDirectory(logs, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("${LOCATION} is not a directory, or is a link, and nothing is read or written through it")
        }
        if (!ConsoleProject.isOwnedByUser(logs, project)) {
            throw new IOException("${LOCATION} is not owned by the user who runs grails mcp")
        }
        if (logs.fileSystem.supportedFileAttributeViews().contains('posix')) {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(logs, LinkOption.NOFOLLOW_LINKS)
            if (permissions.contains(PosixFilePermission.GROUP_WRITE) || permissions.contains(PosixFilePermission.OTHERS_WRITE)) {
                throw new IOException("${LOCATION} can be written to by others than its owner: see what is in it, and delete it or take the permission away (chmod go-w ${LOCATION})")
            }
        }
        return logs
    }

    // a new file for the call, for its owner alone; a second call of the tool in the same millisecond gets a number
    private static Path create(Path logs, String moment, String tool) throws IOException {
        for (int number = 1; number <= MOST_AT_ONCE; number++) {
            String name = number == 1 ? "${moment}-${tool}.log" : "${moment}-${tool}-${number}.log"
            Path created = RunDirectory.createNew(logs, name)
            if (created != null) {
                return created
            }
        }
        throw new IOException("${MOST_AT_ONCE} calls of ${tool} ended in the same millisecond")
    }

    // the newest files are kept, which are the last by their names
    private static void prune(Path logs) throws IOException {
        List<Path> files = []
        DirectoryStream<Path> stream = Files.newDirectoryStream(logs)
        try {
            for (Path each : stream) {
                if (NAME.matcher(each.fileName.toString()).matches() && Files.isRegularFile(each, LinkOption.NOFOLLOW_LINKS)) {
                    files.add(each)
                }
            }
        } finally {
            stream.close()
        }
        if (files.size() <= KEPT) {
            return
        }
        files.sort { Path one, Path other -> one.fileName.toString() <=> other.fileName.toString() }
        for (Path old : files.subList(0, files.size() - KEPT)) {
            Files.deleteIfExists(old)
        }
    }

    /**
     * Where the whole output of a call was kept, or why it was not.
     *
     * @since 9.0
     */
    @CompileStatic
    static final class Kept {

        /** The file; {@code null} where none was kept. */
        final Path file

        /** Why no file was kept; {@code null} where one was. */
        final String reason

        private Kept(Path file, String reason) {
            this.file = file
            this.reason = reason
        }

        private static Kept none(String reason) {
            return new Kept(null, reason)
        }

        /**
         * @return the URI of the file, as a resource of the server; {@code null} where none was kept
         */
        String getUri() {
            return file == null ? null : file.toUri().toString()
        }
    }
}
