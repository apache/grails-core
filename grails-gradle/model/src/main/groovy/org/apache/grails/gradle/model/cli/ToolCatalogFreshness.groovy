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
 * When a catalog of the tools of a project may be out of date: when something it is made from,
 * as far as that is in a fixed place of the project, was changed after the catalog was written. The
 * command line of Grails reads the catalog by this rule, by the times files were changed and without
 * running the build; the task that writes the catalog holds itself to the same rule, so that a build
 * leaves no catalog behind that the command line would take for out of date.
 *
 * <p>What is looked at, in this order, is the files of {@link #BUILD_FILES} in the directory of the
 * project; every directory below each of {@link #TOOL_SOURCES}, with every file of one of
 * {@link #SOURCE_ENDINGS}; and every directory below the directory of the descriptors,
 * {@link #DESCRIPTORS}, of each directory of classes, with every file of {@link #DESCRIPTOR_ENDING}. A
 * reader that does not know where the build puts its classes looks at the directories of
 * {@link #CLASSES_DIRECTORIES}. A new or a deleted file changes its directory, a file that was written
 * again only itself. No more is looked at than {@link #MAX_WALKED} entries of each directory,
 * {@link #MAX_WALK_DEPTH} deep; a directory that has more is taken for changed, since it cannot be said
 * that it is not.</p>
 *
 * <p>A file that is a symbolic link counts with the time of the file the link leads to, which is what
 * the build reads: when the link itself was made says nothing of it. A link that leads to no file is
 * no file. A link to a directory below one of the directories is not followed.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class ToolCatalogFreshness {

    /** The files of the directory of the project the tools of the project depend on. */
    public static final List<String> BUILD_FILES = ['build.gradle', 'build.gradle.kts', 'settings.gradle', 'settings.gradle.kts', 'gradle.properties'].asImmutable()

    /** The deprecated directory of the sources of tools, in the directory of the project: one of {@link #TOOL_SOURCES}. */
    public static final String COMMAND_SOURCES = ToolBuildNames.LEGACY_SOURCE_FOLDER

    /**
     * The directories of the sources of the tools of a project, in the directory of the project, in the
     * order they are looked at: {@code src/cli}, {@code src/standalone} and the deprecated
     * {@code grails-app/commands}.
     */
    public static final List<String> TOOL_SOURCES = [ToolBuildNames.CLI_SOURCE_FOLDER, ToolBuildNames.STANDALONE_SOURCE_FOLDER,
                                                     ToolBuildNames.LEGACY_SOURCE_FOLDER].asImmutable()

    /** The ending of a source of a tool in Groovy: one of {@link #SOURCE_ENDINGS}. */
    public static final String SOURCE_ENDING = '.groovy'

    /** The endings of the sources of tools that are looked at: Groovy and Java. */
    public static final List<String> SOURCE_ENDINGS = [SOURCE_ENDING, '.java'].asImmutable()

    /** The directory of the descriptors the compiler writes, in a directory of classes. */
    public static final String DESCRIPTORS = ToolDescriptor.DIRECTORY

    /** The ending of a descriptor. */
    public static final String DESCRIPTOR_ENDING = '.json'

    /**
     * The directories of classes of a project in which the compiler writes the descriptors, in the
     * directory of the project, as the Grails Gradle plugin has them when the build does not move its
     * build directory: those of the source sets {@code main}, {@code cli} and {@code standalone}.
     */
    public static final List<String> CLASSES_DIRECTORIES = ['build/classes/groovy/main', 'build/classes/groovy/cli',
                                                            'build/classes/groovy/standalone'].asImmutable()

    /** How many entries of a directory are looked at, at most. */
    public static final int MAX_WALKED = 4096

    /** How deep a directory is looked at, at most. */
    public static final int MAX_WALK_DEPTH = 24

    private ToolCatalogFreshness() {
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
            FileTime modified = modifiedBehind(project.resolve(name))
            if (modified != null && modified > since) {
                return name
            }
        }
        for (String sources : TOOL_SOURCES) {
            if (isChangedBelow(project.resolve(sources), SOURCE_ENDINGS, since)) {
                return sources
            }
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
     * @param projectDirectory the directory of the project
     * @param since when the catalog was written, as precisely as the file system says
     * @return what {@link #changedSince(File, Collection, FileTime)} says with the directories of classes
     * of {@link #CLASSES_DIRECTORIES}, for a reader that does not know where the build puts its classes
     */
    static String changedSince(File projectDirectory, FileTime since) {
        changedSince(projectDirectory, CLASSES_DIRECTORIES.collect { String classes -> new File(projectDirectory, classes) }, since)
    }

    /**
     * @param path a file, or a link to one
     * @return when the file the path leads to was changed, whatever links lead to it; {@code null}
     * when there is no file, which a link that leads nowhere is
     */
    static FileTime modifiedBehind(Path path) {
        try {
            return Files.getLastModifiedTime(path)
        } catch (IOException ignored) {
            return null
        }
    }

    /**
     * @param top a directory
     * @param ending the ending of the files that are looked at
     * @param since an instant
     * @return whether the directory, a directory below it or a file of the ending in one of them was
     * changed after the instant; false for a directory that is not there
     */
    static boolean isChangedBelow(Path top, String ending, FileTime since) {
        isChangedBelow(top, [ending], since)
    }

    /**
     * @param top a directory
     * @param endings the endings of the files that are looked at
     * @param since an instant
     * @return whether the directory, a directory below it or a file of one of the endings in one of them
     * was changed after the instant; false for a directory that is not there
     */
    static boolean isChangedBelow(Path top, Collection<String> endings, FileTime since) {
        if (!Files.isDirectory(top, LinkOption.NOFOLLOW_LINKS)) {
            return false
        }
        Walk walk = new Walk(new ArrayList<String>(endings), since)
        try {
            Files.walkFileTree(top, Collections.<FileVisitOption> emptySet(), MAX_WALK_DEPTH, walk)
        } catch (IOException | RuntimeException ignored) {
            return true
        }
        return walk.changed
    }

    @CompileStatic
    private static final class Walk extends SimpleFileVisitor<Path> {

        private final List<String> endings
        private final FileTime since
        private int seen
        boolean changed

        Walk(List<String> endings, FileTime since) {
            this.endings = endings
            this.since = since
        }

        @Override
        FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
            return look(changedAt(directory, attributes), true)
        }

        @Override
        FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
            if (attributes.isSymbolicLink()) {
                // a link to a file of the ending counts as that file does; any other link is an entry and no more
                FileTime behind = hasEnding(file) ? fileBehind(file) : null
                return look(behind ?: attributes.lastModifiedTime(), behind != null)
            }
            // at the depth that is the last looked at, a directory is given as a file
            boolean counts = attributes.isDirectory() || (attributes.isRegularFile() && hasEnding(file))
            return look(attributes.isDirectory() ? changedAt(file, attributes) : attributes.lastModifiedTime(), counts)
        }

        private boolean hasEnding(Path file) {
            String name = file.getFileName().toString()
            for (String ending : endings) {
                if (name.endsWith(ending)) {
                    return true
                }
            }
            return false
        }

        @Override
        FileVisitResult visitFileFailed(Path file, IOException e) {
            return FileVisitResult.CONTINUE
        }

        private static FileTime fileBehind(Path link) {
            try {
                BasicFileAttributes behind = Files.readAttributes(link, BasicFileAttributes)
                return behind.isRegularFile() ? behind.lastModifiedTime() : null
            } catch (IOException ignored) {
                return null
            }
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
