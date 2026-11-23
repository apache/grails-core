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
package org.apache.grails.gradle.model.cli

import java.nio.file.FileVisitOption
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime

import groovy.transform.CompileStatic

/**
 * When a catalog of the commands of a project may be out of date: when something it is made from,
 * as far as that is in a fixed place of the project, was changed after the catalog was written. The
 * command line of Grails reads the catalog by this rule, by the times files were changed and without
 * running the build; the task that writes the catalog holds itself to the same rule, so that a build
 * leaves no catalog behind that the command line would take for out of date.
 *
 * <p>What is looked at is the files of {@link #BUILD_FILES} in the directory of the project, and every
 * directory below {@link #COMMAND_SOURCES}, and below the directory of the descriptors of each directory
 * of classes, with every file of the ending each has there: a new or a deleted file changes its
 * directory, a file that was written again only itself. No link is followed, and no more is looked at
 * than {@link #MAX_WALKED} entries, {@link #MAX_WALK_DEPTH} deep; a directory that has more is taken
 * for changed, since it cannot be said that it is not.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class CommandCatalogFreshness {

    /** The files of the directory of the project the commands of the project depend on. */
    public static final List<String> BUILD_FILES = ['build.gradle', 'build.gradle.kts', 'settings.gradle', 'settings.gradle.kts', 'gradle.properties'].asImmutable()

    /** The directory of the sources of the commands of an application, in the directory of the project. */
    public static final String COMMAND_SOURCES = 'grails-app/commands'

    /** The ending of a source of a command. */
    public static final String SOURCE_ENDING = '.groovy'

    /** The directory of the descriptors the compiler writes, in a directory of classes. */
    public static final String DESCRIPTORS = 'META-INF/grails-commands'

    /** The ending of a descriptor. */
    public static final String DESCRIPTOR_ENDING = '.json'

    /** How many entries of a directory are looked at, at most. */
    public static final int MAX_WALKED = 4096

    /** How deep a directory is looked at, at most. */
    public static final int MAX_WALK_DEPTH = 24

    private CommandCatalogFreshness() {
    }

    /**
     * @param projectDirectory the directory of the project
     * @param classesDirectories the directories the compiler of the project writes classes and descriptors to
     * @param since when the catalog was written, as precisely as the file system says
     * @return the first of the places the catalog is made from that was changed after it was written,
     * as a path in the project, or {@code null} when none was
     */
    static String changedSince(File projectDirectory, Collection<File> classesDirectories, FileTime since) {
        Path project = projectDirectory.toPath()
        for (String name : BUILD_FILES) {
            try {
                if (Files.getLastModifiedTime(project.resolve(name), LinkOption.NOFOLLOW_LINKS) > since) {
                    return name
                }
            } catch (IOException ignored) {
                // it is not there
            }
        }
        if (isChangedBelow(project.resolve(COMMAND_SOURCES), SOURCE_ENDING, since)) {
            return COMMAND_SOURCES
        }
        for (File classes : classesDirectories) {
            Path descriptors = classes.toPath().resolve(DESCRIPTORS)
            if (isChangedBelow(descriptors, DESCRIPTOR_ENDING, since)) {
                return descriptors.startsWith(project) ? project.relativize(descriptors).toString().replace(File.separatorChar, '/' as char) : descriptors.toString()
            }
        }
        return null
    }

    /**
     * @param top a directory
     * @param ending the ending of the files that are looked at
     * @param since an instant
     * @return whether the directory, a directory below it or a file of the ending in one of them was
     * changed after the instant; false for a directory that is not there
     */
    static boolean isChangedBelow(Path top, String ending, FileTime since) {
        if (!Files.isDirectory(top, LinkOption.NOFOLLOW_LINKS)) {
            return false
        }
        Walk walk = new Walk(ending, since)
        try {
            Files.walkFileTree(top, Collections.<FileVisitOption> emptySet(), MAX_WALK_DEPTH, walk)
        } catch (IOException | RuntimeException ignored) {
            return true
        }
        return walk.changed
    }

    @CompileStatic
    private static final class Walk extends SimpleFileVisitor<Path> {

        private final String ending
        private final FileTime since
        private int seen
        boolean changed

        Walk(String ending, FileTime since) {
            this.ending = ending
            this.since = since
        }

        @Override
        FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
            return look(changedAt(directory, attributes), true)
        }

        @Override
        FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
            // at the depth that is the last looked at, a directory is given as a file
            boolean counts = attributes.isDirectory() || (attributes.isRegularFile() && file.getFileName().toString().endsWith(ending))
            return look(attributes.isDirectory() ? changedAt(file, attributes) : attributes.lastModifiedTime(), counts)
        }

        @Override
        FileVisitResult visitFileFailed(Path file, IOException e) {
            return FileVisitResult.CONTINUE
        }

        // When a directory was changed is asked of the directory itself. What the walk has of it is what the
        // directory above lists, and NTFS brings that up to date only later.
        private static FileTime changedAt(Path directory, BasicFileAttributes listed) {
            try {
                return Files.getLastModifiedTime(directory, LinkOption.NOFOLLOW_LINKS)
            } catch (IOException ignored) {
                return listed.lastModifiedTime()
            }
        }

        private FileVisitResult look(FileTime modified, boolean counts) {
            if (++seen > MAX_WALKED || (counts && modified > since)) {
                changed = true
                return FileVisitResult.TERMINATE
            }
            return FileVisitResult.CONTINUE
        }
    }
}
