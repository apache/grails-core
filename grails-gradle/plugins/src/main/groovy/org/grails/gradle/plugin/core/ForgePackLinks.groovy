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

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.stream.Stream

import groovy.transform.CompileStatic

import org.gradle.api.GradleException

/**
 * A Forge feature pack of a plugin has no symbolic link: not the directory of the pack, and nothing
 * under its {@code templates}. The tasks that put the pack into the jar, that check it and that test
 * it all refuse one, with the same words.
 *
 * <p>A link would put a file from somewhere else into the jar, and Forge reads none below
 * {@code templates}. It would read a directory of a pack that is itself a link, and what the link
 * leads to is not what {@link ForgePackStateValueSource} takes the digest of: a pack that was checked
 * through a link would be taken for checked after a template was changed where the link leads. A
 * link is never followed here, so links that lead to each other are refused like any other.</p>
 *
 * @since 9.0
 */
@CompileStatic
class ForgePackLinks {

    private static final String SOURCE = GrailsPluginGradlePlugin.FORGE_PACK_SOURCE_DIR

    /**
     * @param directory the directory of the pack
     * @throws GradleException when the directory is a link or has one under {@code templates}
     */
    static void refuse(File directory) {
        refuseLinkedDirectory(directory)
        refuseLinkedTemplates(directory)
    }

    /**
     * @param directory the directory of the pack
     * @throws GradleException when the directory is a link
     */
    static void refuseLinkedDirectory(File directory) {
        if (Files.isSymbolicLink(directory.toPath())) {
            throw new GradleException("${SOURCE} is a link, which the directory of a feature pack cannot be")
        }
    }

    /**
     * @param directory the directory of the pack
     * @throws GradleException when {@code templates} of the directory is a link or has one
     */
    static void refuseLinkedTemplates(File directory) {
        Path root = directory.toPath()
        Path templates = root.resolve('templates')
        if (Files.exists(templates, LinkOption.NOFOLLOW_LINKS)) {
            // a walk that follows no link: one that leads back into the pack is an entry like any other
            Files.walk(templates).withCloseable { Stream<Path> paths ->
                Path link = paths.filter { Path path -> Files.isSymbolicLink(path) }.findFirst().orElse(null)
                if (link != null) {
                    throw new GradleException("The feature pack in ${SOURCE} has a link, which a pack cannot have: " +
                            root.relativize(link).join('/'))
                }
            }
        }
    }
}
