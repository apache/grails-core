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
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import groovy.transform.CompileStatic

/**
 * The directory {@code .grails/run} of a project: what the commands of the CLI keep while something
 * they started runs, as the record of the application {@code run-app} started, its output, and the
 * arguments of an application command, and the init script with which Gradle checks the path of the
 * project ({@link ProjectPathCheck}), which stays. Nothing in it is part of the project: the directory holds a
 * {@code .gitignore} that leaves all of it out, and it and its files are readable and writable by
 * their owner only, where the file system has such permissions.
 *
 * <p>The directory is in a project that may have come from anywhere. It is used only when it and
 * {@code .grails} are directories, not links, and it is owned by the user who runs the command; a
 * file in it is read and replaced only when it is a regular file.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class RunDirectory {

    /** Where the directory is in a project. */
    public static final String LOCATION = '.grails/run'

    private static final String STATE = '.grails'
    private static final String IGNORE = '.gitignore'
    private static final Set<PosixFilePermission> OWNER_DIRECTORY = PosixFilePermissions.fromString('rwx------')
    private static final Set<PosixFilePermission> OWNER_FILE = PosixFilePermissions.fromString('rw-------')

    private RunDirectory() {
    }

    /**
     * @param project the project
     * @return the directory when it is there, and {@code null} when it is not
     * @throws IOException with one line, when what is there is not a directory of the user's own
     */
    static Path existing(ConsoleProject project) throws IOException {
        Path state = project.directory.resolve(STATE)
        if (!Files.exists(state, LinkOption.NOFOLLOW_LINKS)) {
            return null
        }
        check(state, STATE)
        Path run = project.directory.resolve(LOCATION)
        if (!Files.exists(run, LinkOption.NOFOLLOW_LINKS)) {
            return null
        }
        check(run, LOCATION)
        if (!ConsoleProject.isOwnedByUser(run, project)) {
            throw new IOException("${LOCATION} is not owned by the user who runs the command${GradleRunner.line(ProcessUser.current().comparedWith(run))}")
        }
        // the command creates it for its owner alone: where others can write, what is in it is not the command's
        if (run.getFileSystem().supportedFileAttributeViews().contains('posix')) {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(run, LinkOption.NOFOLLOW_LINKS)
            if (permissions.contains(PosixFilePermission.GROUP_WRITE) || permissions.contains(PosixFilePermission.OTHERS_WRITE)) {
                throw new IOException("${LOCATION} can be written to by others than its owner. The command creates it for its owner alone: " +
                        "see what is in it, and delete it or take the permission away (chmod go-w ${LOCATION})")
            }
        }
        return run
    }

    /**
     * @param project the project
     * @return the directory, which is created when it is not there
     * @throws IOException with one line, when what is there is not a directory of the user's own, or none can be created
     */
    static Path of(ConsoleProject project) throws IOException {
        Path run = existing(project)
        if (run == null) {
            Path state = project.directory.resolve(STATE)
            run = project.directory.resolve(LOCATION)
            // Another command of the project may create them at the same moment: a directory that is
            // there by now is not a failure, and is held to what an existing one is held to below.
            try {
                try {
                    Files.createDirectory(state)
                } catch (FileAlreadyExistsException ignored) {
                    // made since it was looked for
                }
                try {
                    Files.createDirectory(run, ownerOnly(run, OWNER_DIRECTORY))
                } catch (FileAlreadyExistsException ignored) {
                    // made since it was looked for
                }
            } catch (IOException e) {
                throw new IOException("${LOCATION} cannot be created (${e.getClass().getSimpleName()}: ${GradleRunner.line(e.getMessage())})")
            }
            if (existing(project) == null) {
                throw new IOException("${LOCATION} cannot be created: it is not there after it was made")
            }
        }
        Path ignore = run.resolve(IGNORE)
        if (!Files.exists(ignore, LinkOption.NOFOLLOW_LINKS)) {
            try {
                // for its owner alone, as everything else in the directory; another command may have written it by now
                Path created = createNew(run, IGNORE)
                if (created != null) {
                    Files.writeString(created, '*\n')
                }
            } catch (IOException ignored) {
                // the directory is used all the same: the file only keeps it out of a commit
            }
        }
        return run
    }

    /**
     * Creates a file of the directory that only its owner reads and writes. What has the name already
     * is removed first when it is a file or a link, so that nothing is written through a link.
     *
     * @param run the directory
     * @param name the name of the file
     * @return the file, which is empty
     * @throws IOException with one line, when there is a directory of that name or the file cannot be created
     */
    static Path create(Path run, String name) throws IOException {
        Path file = run.resolve(name)
        try {
            if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException('it is a directory')
            }
            Files.deleteIfExists(file)
            return Files.createFile(file, ownerOnly(run, OWNER_FILE))
        } catch (IOException e) {
            throw new IOException("${LOCATION}/${name} cannot be written: ${GradleRunner.line(e.getMessage())}")
        }
    }

    /**
     * Creates a file of the directory that only its owner reads and writes, when nothing has its name.
     *
     * @param run the directory
     * @param name the name of the file
     * @return the file, which is empty, or {@code null} when something has the name already
     */
    static Path createNew(Path run, String name) throws IOException {
        try {
            return Files.createFile(run.resolve(name), ownerOnly(run, OWNER_FILE))
        } catch (FileAlreadyExistsException ignored) {
            return null
        }
    }

    private static void check(Path directory, String name) throws IOException {
        if (Files.isSymbolicLink(directory)) {
            throw new IOException("${name} is a link, and nothing is read or written through it")
        }
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("${name} is not a directory")
        }
    }

    private static FileAttribute<?>[] ownerOnly(Path directory, Set<PosixFilePermission> permissions) {
        boolean posix = directory.getFileSystem().supportedFileAttributeViews().contains('posix')
        return (posix ? [PosixFilePermissions.asFileAttribute(permissions)] : []) as FileAttribute<?>[]
    }
}
