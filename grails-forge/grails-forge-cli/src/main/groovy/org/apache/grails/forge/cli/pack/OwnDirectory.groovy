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
package org.apache.grails.forge.cli.pack

import groovy.transform.CompileStatic
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import org.apache.grails.forge.cli.console.ProcessUser

/**
 * Whether a directory under {@code .grails/forge} of the home of the user, in which the command line
 * keeps copies of what it read from the network, is the user's own: what is in a directory someone
 * else can write to, or that is reached through a link, is not a copy this command made, and is not
 * read as one.
 *
 * @since 9.0
 */
@CompileStatic
final class OwnDirectory {

    private static final String POSIX = 'posix'
    private static final int MAX_NAMED = 200

    private OwnDirectory() {
    }

    /**
     * @param directory a directory two levels under the home of the user, such as {@code .grails/forge/catalogs}
     * @return the first of the directory and the two above it that is there and is not a directory, is
     * a link, belongs to someone else or can be written to by others; {@code null} when there is none
     */
    static Path notOurs(Path directory) {
        boolean posix = FileSystems.default.supportedFileAttributeViews().contains(POSIX)
        // who runs the command, and not who owns the home: the two differ under sudo and in a container
        ProcessUser user = posix && ProcessUser.current().isKnown() ? ProcessUser.current() : null
        for (Path each : [directory.parent.parent, directory.parent, directory]) {
            try {
                if (!Files.exists(each, LinkOption.NOFOLLOW_LINKS)) {
                    continue
                }
                if (Files.isSymbolicLink(each) || !Files.isDirectory(each, LinkOption.NOFOLLOW_LINKS)) {
                    return each
                }
                if (posix) {
                    Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(each, LinkOption.NOFOLLOW_LINKS)
                    if (permissions.contains(PosixFilePermission.GROUP_WRITE) || permissions.contains(PosixFilePermission.OTHERS_WRITE) ||
                            (user != null && !user.owns(each))) {
                        return each
                    }
                }
            } catch (IOException | RuntimeException ignored) {
                return each
            }
        }
        return null
    }

    /**
     * @param found what {@link #notOurs} found
     * @return what is wrong with it, and what makes it right where something does
     */
    static String whyNotOurs(Path found) {
        try {
            if (Files.isSymbolicLink(found)) {
                return 'is a link, and copies are kept only in a directory that is reached without one'
            }
            if (!Files.isDirectory(found, LinkOption.NOFOLLOW_LINKS)) {
                return 'is not a directory'
            }
            if (FileSystems.default.supportedFileAttributeViews().contains(POSIX)) {
                Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(found, LinkOption.NOFOLLOW_LINKS)
                if (permissions.contains(PosixFilePermission.GROUP_WRITE) || permissions.contains(PosixFilePermission.OTHERS_WRITE)) {
                    return 'can be written to by its group or by others, so what is in it need not be a copy this command made; chmod go-w on it makes it yours alone'
                }
            }
        } catch (IOException | RuntimeException ignored) {
            return 'cannot be looked at'
        }
        return 'is not owned by the user who runs the command'
    }

    /**
     * @param found what {@link #notOurs} found
     * @return a sentence that names what was found and says what is wrong with it
     */
    static String refusal(Path found) {
        String named = found.toString().replaceAll(/\p{Cntrl}/, '?').replace('@|', '@ |')
        return "${named.length() > MAX_NAMED ? named.substring(0, MAX_NAMED) + '...' : named} is not a directory of yours that nobody else can write to: it ${whyNotOurs(found)}".toString()
    }
}
