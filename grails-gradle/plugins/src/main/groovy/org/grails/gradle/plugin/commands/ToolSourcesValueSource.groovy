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
package org.grails.gradle.plugin.commands

import java.nio.file.FileVisitOption
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

import groovy.transform.CompileStatic

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

/**
 * Whether a project has a source of a tool in some folders: a file of one of the endings below one
 * of the folders of sources, or any file below one of the folders where every file counts. A file
 * whose name starts with a dot, such as a {@code .gitkeep}, is no source.
 *
 * <p>The build decides with it whether a companion of a plugin is made at all. As a value source it
 * is asked again whenever a build reuses its configuration cache entry, so adding the first tool to
 * a folder, or deleting the last one, makes the build configure the project again, as adding a
 * source set would. No more than {@link #MAX_WALKED} entries of each folder are looked at; a folder
 * that has more is taken to have a source.</p>
 *
 * @since 9.0
 */
@CompileStatic
abstract class ToolSourcesValueSource implements ValueSource<Boolean, Parameters> {

    /** The endings of a source of a tool. */
    public static final List<String> SOURCE_ENDINGS = ['.groovy', '.java'].asImmutable()

    /** How many entries of a folder are looked at, at most. */
    public static final int MAX_WALKED = 100_000

    /** How deep a folder is looked at, at most. */
    public static final int MAX_WALK_DEPTH = 64

    /** What is looked at. */
    static interface Parameters extends ValueSourceParameters {

        /** The directory of the project. */
        DirectoryProperty getProjectDirectory()

        /** The folders of sources, in the directory of the project, in which a file of {@link #SOURCE_ENDINGS} counts. */
        ListProperty<String> getSourceFolders()

        /** The folders, in the directory of the project, in which every file counts. */
        ListProperty<String> getAnyFileFolders()
    }

    @Override
    Boolean obtain() {
        Path project = parameters.projectDirectory.get().asFile.toPath()
        for (String folder : parameters.sourceFolders.get()) {
            if (has(project.resolve(folder), SOURCE_ENDINGS)) {
                return true
            }
        }
        for (String folder : parameters.anyFileFolders.get()) {
            if (has(project.resolve(folder), null)) {
                return true
            }
        }
        false
    }

    /**
     * @param top a folder
     * @param endings the endings of the files that count, or {@code null} for any file
     * @return whether there is a file that counts below the folder
     */
    static boolean has(Path top, List<String> endings) {
        if (!Files.isDirectory(top, LinkOption.NOFOLLOW_LINKS)) {
            return false
        }
        Find find = new Find(endings)
        try {
            Files.walkFileTree(top, Collections.<FileVisitOption> emptySet(), MAX_WALK_DEPTH, find)
        } catch (IOException | RuntimeException ignored) {
            return true
        }
        find.found
    }

    @CompileStatic
    private static final class Find extends SimpleFileVisitor<Path> {

        private final List<String> endings
        private int seen
        boolean found

        Find(List<String> endings) {
            this.endings = endings
        }

        @Override
        FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
            if (++seen > MAX_WALKED) {
                found = true
                return FileVisitResult.TERMINATE
            }
            String name = file.fileName.toString()
            if (!name.startsWith('.') && !attributes.isDirectory() && (endings == null || endings.any { String ending -> name.endsWith(ending) })) {
                found = true
                return FileVisitResult.TERMINATE
            }
            FileVisitResult.CONTINUE
        }

        @Override
        FileVisitResult visitFileFailed(Path file, IOException e) {
            FileVisitResult.CONTINUE
        }
    }
}
