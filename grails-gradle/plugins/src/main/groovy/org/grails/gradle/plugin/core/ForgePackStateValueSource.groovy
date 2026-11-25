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
package org.grails.gradle.plugin.core

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.stream.Collectors
import java.util.stream.Stream

import groovy.transform.CompileStatic

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

/**
 * What there is of the Forge feature pack of a plugin, as Forge would read it: whether the directory
 * of the pack is there, or a digest of the descriptor and of everything under {@code templates}.
 *
 * <p>Gradle cannot be asked. As the input of a task, a directory is without the files Gradle leaves
 * out of every file tree, such as {@code .gitignore}, which a descriptor can name; and a file is the
 * same to it as a link to a file with that content, which Forge refuses. The digest is of every entry
 * by its path, with what it is: a file with its content and the number of names it has, a link with
 * where it leads, a directory, or something else.</p>
 *
 * <p>A link is not followed, and what it leads to is no part of the digest. It does not have to be:
 * the tasks that have this value as their input refuse a pack with a link before they read it, see
 * {@link ForgePackLinks}, so none of them has a result to keep for a pack that is read through one.</p>
 *
 * <p>Read while the build is configured, the value is an input of the configuration cache. Read as the
 * input of a task only, it is read again by every build.</p>
 *
 * @since 9.0
 */
@CompileStatic
abstract class ForgePackStateValueSource implements ValueSource<String, Parameters> {

    /** The value for a project without the directory of a pack. */
    public static final String ABSENT = 'absent'

    /** The value for a project with the directory of a pack, when no digest is asked for. */
    public static final String PRESENT = 'present'

    interface Parameters extends ValueSourceParameters {

        /** The directory of the pack, {@code src/main/forge}. */
        DirectoryProperty getPackDirectory()

        /** Whether the value is a digest of the pack, and not only whether there is one. */
        Property<Boolean> getDigest()
    }

    @Override
    String obtain() {
        Path root = parameters.packDirectory.get().asFile.toPath()
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return ABSENT
        }
        if (!parameters.digest.getOrElse(false)) {
            return PRESENT
        }
        MessageDigest digest = MessageDigest.getInstance('SHA-256')
        add(digest, root, root)
        if (Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            add(digest, root, root.resolve(GrailsPluginGradlePlugin.FORGE_PACK_DESCRIPTOR))
            Path templates = root.resolve('templates')
            if (Files.isDirectory(templates, LinkOption.NOFOLLOW_LINKS)) {
                // links are not followed, and in one order whatever the file system gives
                List<Path> entries = Files.walk(templates).withCloseable { Stream<Path> paths ->
                    paths.sorted().collect(Collectors.toList())
                }
                for (Path entry : entries) {
                    add(digest, root, entry)
                }
            } else {
                add(digest, root, templates)
            }
        }
        return digest.digest().encodeHex().toString()
    }

    private static void add(MessageDigest digest, Path root, Path entry) {
        text(digest, root.relativize(entry).join('/'))
        if (Files.isSymbolicLink(entry)) {
            text(digest, 'link')
            text(digest, Files.readSymbolicLink(entry).toString())
        } else if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
            text(digest, 'directory')
        } else if (Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
            text(digest, 'file')
            text(digest, names(entry))
            long length = 0
            new DigestInputStream(Files.newInputStream(entry, LinkOption.NOFOLLOW_LINKS), digest).withCloseable { DigestInputStream input ->
                byte[] buffer = new byte[8192]
                int read
                while ((read = input.read(buffer)) >= 0) {
                    length += read
                }
            }
            text(digest, Long.toString(length))
        } else {
            text(digest, Files.exists(entry, LinkOption.NOFOLLOW_LINKS) ? 'other' : 'none')
        }
    }

    // Forge refuses a file that has a second name somewhere else, where the file system counts them
    private static String names(Path file) {
        try {
            return String.valueOf(Files.getAttribute(file, 'unix:nlink', LinkOption.NOFOLLOW_LINKS))
        } catch (UnsupportedOperationException | IllegalArgumentException ignored) {
            return 'uncounted'
        }
    }

    // with its length, so that no two sequences of entries give the same bytes
    private static void text(MessageDigest digest, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8)
        digest.update("${bytes.length}:".getBytes(StandardCharsets.UTF_8))
        digest.update(bytes)
    }
}
