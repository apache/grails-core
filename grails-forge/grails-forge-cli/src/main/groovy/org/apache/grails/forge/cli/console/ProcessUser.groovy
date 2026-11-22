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

import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.GroupPrincipal
import java.nio.file.attribute.UserPrincipal
import groovy.transform.CompileStatic

/**
 * The user the process runs as, taken from the process itself and from nothing the process could be
 * told or could find changed: not from a file it creates where temporary files go, and not from the
 * owner of a home directory (on Windows such a file tells one thing more, which is not who the user
 * is; see below). It is asked for in this order, and the first that answers is it:
 *
 * <ol>
 * <li>{@link #UID}: the user id of the process, where the files have the user id of their owner
 * (POSIX), as the system module of the JDK for it says</li>
 * <li>{@link #PROC}: on Linux, the user id that owns {@code /proc/self}</li>
 * <li>{@link #NAME}: the name of the user of the process as the JDK reports it, which is compared
 * with the name of the owner of a file; the one there is on Windows, where the two are compared
 * as the security identifiers they are, see below</li>
 * </ol>
 *
 * When none answers, the user is not known, and no file is one of theirs.
 *
 * <p>On Windows an owner is a security identifier, and the name of one is in the language of the
 * system, so an owner is compared as the identifier it is wherever the JDK allows it: the owner of a
 * file and the user of the process are both taken as the principals the file system gives, which are
 * equal when their identifiers are. A file there need not be owned by the user who created it:
 * Windows gives what an elevated administrator creates to the group of the administrators
 * ({@link #ADMINISTRATORS}), and to no user. A file that a group owns is the user's own when all of
 * this holds, and never otherwise:</p>
 *
 * <ul>
 * <li>the access token of the process has the group of the administrators among its groups, which
 * {@code com.sun.security.auth.module.NTSystem} of the JDK reads from the process</li>
 * <li>the group that owns the file is the one the system gives the files that this process creates,
 * as a file the process creates for the purpose shows; that is the group of the administrators for
 * a process that is elevated, and the user for one that is not</li>
 * </ul>
 *
 * <p>So what the administrators own is the own of an administrator in an elevated process, as git
 * holds it for {@code safe.directory} on Windows, and of nobody else; and a process that is not
 * elevated does not take it for its own, although its user is an administrator. No file is taken
 * for the user's own by the name of a group.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class ProcessUser {

    /** The user id of the process. */
    public static final String UID = 'the user id of the process'

    /** The owner of {@code /proc/self}. */
    public static final String PROC = 'the owner of /proc/self'

    /** The name of the user of the process. */
    public static final String NAME = 'the name of the user of the process'

    /** The sources, in the order they are asked. */
    public static final List<String> SOURCES = Collections.unmodifiableList([UID, PROC, NAME])

    /** The security identifier of the group of the administrators of a Windows system, which is the same on every one. */
    public static final String ADMINISTRATORS = 'S-1-5-32-544'

    private static final String USER_ID = 'unix:uid'
    private static final String ACCESS_CONTROL = 'acl'

    private static volatile ProcessUser replaced

    private final Integer uid
    private final String name
    private final String source
    private final Windows windows

    private ProcessUser(Integer uid, String name, String source, Windows windows) {
        this.uid = uid
        this.name = name
        this.source = source
        this.windows = windows
    }

    /**
     * What Windows says of the process and of the owner of a file. A specification gives its own, since
     * no test can create a file that another user or the group of the administrators owns.
     */
    static interface Windows {

        /**
         * @return the user of the process as the file system names an owner, or {@code null} when the
         * system does not find one for the name of the user
         */
        UserPrincipal user()

        /**
         * @return the security identifiers of the groups in the access token of the process, also of
         * those that only deny; empty when they cannot be read
         */
        Collection<String> groups()

        /**
         * @return the owner the system gives a file that this process creates, or {@code null} when
         * that cannot be told
         */
        UserPrincipal ownerOfCreated()

        /**
         * @param path a file or a directory; a link is the link itself
         * @return its owner
         */
        UserPrincipal ownerOf(Path path) throws IOException
    }

    /**
     * @return the user of this process, established once
     */
    static ProcessUser current() {
        ProcessUser instead = replaced
        return instead == null ? Holder.CURRENT : instead
    }

    /**
     * Replaces the user of this process, for a specification: on the systems a specification runs on
     * a source always answers, so the user is never one that is not known there.
     *
     * @param user who {@link #current} is taken to be, as {@link #establish} or {@link #onWindows}
     * gives one; {@code null} for the user of the process
     */
    static void setCurrent(ProcessUser user) {
        replaced = user
    }

    /**
     * @param name the name of the user of the process, as the system gives it
     * @param windows what Windows says of the process and of the owners of files
     * @return the user of a process on Windows, as {@link #establish} gives one there
     */
    static ProcessUser onWindows(String name, Windows windows) {
        return new ProcessUser(null, name, NAME, windows)
    }

    /**
     * Establishes the user of this process anew.
     *
     * @param without the sources that are not asked, as when they do not answer
     * @return the user, who is not known when no source answered
     */
    static ProcessUser establish(Collection<String> without) {
        boolean ids = FileSystems.getDefault().supportedFileAttributeViews().contains('unix')
        if (ids && !(UID in without)) {
            Integer id = idOfProcess()
            if (id != null) {
                return new ProcessUser(id, null, UID, null)
            }
        }
        if (ids && !(PROC in without)) {
            try {
                Object owner = Files.getAttribute(Path.of('/proc/self'), USER_ID)
                if (owner instanceof Number) {
                    return new ProcessUser(((Number) owner).intValue(), null, PROC, null)
                }
            } catch (IOException | RuntimeException ignored) {
                // there is no such directory but on Linux
            }
        }
        if (!(NAME in without)) {
            try {
                String user = ProcessHandle.current().info().user().orElse(null)
                if (user != null && !user.isEmpty()) {
                    // the file system of Windows has access control lists and no user ids
                    boolean lists = !ids && FileSystems.getDefault().supportedFileAttributeViews().contains(ACCESS_CONTROL)
                    return new ProcessUser(null, user, NAME, lists ? new ProcessToken(user) : null)
                }
            } catch (RuntimeException ignored) {
                // then it is not known
            }
        }
        return new ProcessUser(null, null, null, null)
    }

    /**
     * @return whether the user could be established
     */
    boolean isKnown() {
        return source != null
    }

    /**
     * @return which of {@link #SOURCES} the user was established by, or {@code null}
     */
    String getSource() {
        return source
    }

    /**
     * @param path a file or a directory; a link is the link itself
     * @return whether it is owned by the user of the process, or on Windows by the group of the
     * administrators where that is the own of the user as the class says; false when either is not known
     */
    boolean owns(Path path) {
        try {
            if (uid != null) {
                Object owner = Files.getAttribute(path, USER_ID, LinkOption.NOFOLLOW_LINKS)
                return owner instanceof Number && ((Number) owner).intValue() == uid.intValue()
            }
            if (windows != null) {
                UserPrincipal owner = windows.ownerOf(path)
                if (owner == null) {
                    return false
                }
                UserPrincipal user = windows.user()
                if (user != null ? owner == user : named(owner.getName())) {
                    return true
                }
                return isOwnOfAdministrators(owner)
            }
            if (name != null) {
                return named(Files.getOwner(path, LinkOption.NOFOLLOW_LINKS).getName())
            }
        } catch (IOException | RuntimeException ignored) {
            // an owner that cannot be read is not this user
        }
        return false
    }

    /**
     * @param path a file or a directory that {@link #owns} says is not the user's own
     * @return on Windows, what the owner was compared with, to follow the words that say whom the file
     * is not owned by: the name of the user, and for a file of a group whether the process has the
     * group of the administrators and whom the files it creates are given to. Empty elsewhere, and
     * when nothing can be said
     */
    String comparedWith(Path path) {
        if (windows == null) {
            return ''
        }
        try {
            String compared = " (${name})"
            if (windows.ownerOf(path) instanceof GroupPrincipal) {
                UserPrincipal created = windows.ownerOfCreated()
                compared += '. What a group owns is the own of an administrator in an elevated process only: ' +
                        "this process ${ADMINISTRATORS in windows.groups() ? 'has' : 'does not have'} the group of the administrators (${ADMINISTRATORS}) in its access token, " +
                        "and the files it creates are owned by ${created == null ? 'an owner that cannot be read' : created.getName()}"
            }
            return compared
        } catch (IOException | RuntimeException ignored) {
            return ''
        }
    }

    /**
     * @param process a process
     * @return whether it runs as the user this process runs as, by the names the system gives for
     * the two; false when either is not known
     */
    static boolean isOfThisUser(ProcessHandle process) {
        try {
            Optional<String> owner = process.info().user()
            Optional<String> user = ProcessHandle.current().info().user()
            return owner.isPresent() && user.isPresent() && owner.get() == user.get()
        } catch (RuntimeException ignored) {
            return false
        }
    }

    // Windows names the kind of the owner after the name, as in HOST\me (User)
    private boolean named(String owner) {
        return owner.equalsIgnoreCase(name) || owner.toLowerCase(Locale.ROOT).startsWith(name.toLowerCase(Locale.ROOT) + ' (')
    }

    // A user cannot give a file to a group at will: the system makes the owner of a new file the default
    // owner in the access token of the process that creates it, which is the user, or for an elevated
    // administrator the group of the administrators. So a file of the group that this process creates its
    // own files for was created by a process like this one, or was given to that group by an administrator.
    private boolean isOwnOfAdministrators(UserPrincipal owner) {
        if (!(owner instanceof GroupPrincipal) || !(ADMINISTRATORS in windows.groups())) {
            return false
        }
        UserPrincipal created = windows.ownerOfCreated()
        return created instanceof GroupPrincipal && owner == created
    }

    // com.sun.security.auth.module.UnixSystem is there in the JDKs for POSIX systems and in no other
    private static Integer idOfProcess() {
        try {
            Class<?> type = Class.forName('com.sun.security.auth.module.UnixSystem')
            Object system = type.getConstructor().newInstance()
            Object id = type.getMethod('getUid').invoke(system)
            return id instanceof Number ? Integer.valueOf(((Number) id).intValue()) : null
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null
        }
    }

    // What Windows says, read from the process, from the file system and from one file the process
    // creates and deletes where its temporary files go. Each is read once.
    private static final class ProcessToken implements Windows {

        private final String name
        private UserPrincipal user
        private boolean userLooked
        private Collection<String> groups
        private UserPrincipal created
        private boolean createdLooked

        ProcessToken(String name) {
            this.name = name
        }

        @Override
        synchronized UserPrincipal user() {
            if (!userLooked) {
                userLooked = true
                try {
                    user = FileSystems.getDefault().getUserPrincipalLookupService().lookupPrincipalByName(name)
                } catch (IOException | RuntimeException ignored) {
                    // then the owner of a file is compared by its name
                }
            }
            return user
        }

        // com.sun.security.auth.module.NTSystem is a supported class of the module jdk.security.auth, and
        // reads the access token of the process with the library of the JDK for it, which Windows alone has
        @Override
        synchronized Collection<String> groups() {
            if (groups == null) {
                groups = Collections.<String>emptySet()
                try {
                    Class<?> type = Class.forName('com.sun.security.auth.module.NTSystem')
                    Object ids = type.getMethod('getGroupIDs').invoke(type.getConstructor().newInstance())
                    if (ids instanceof String[]) {
                        groups = Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList((String[]) ids)))
                    }
                } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                    // a runtime without the module: no file of a group is the user's own then
                }
            }
            return groups
        }

        // The JDK does not say who the default owner in the access token is, and the file system does: it
        // is the owner of a file the process creates. The file gets a name that was not there, on a file
        // system that keeps owners (FAT keeps none, and everybody is named as the owner of every file on it).
        @Override
        synchronized UserPrincipal ownerOfCreated() {
            if (!createdLooked) {
                createdLooked = true
                Path file = null
                try {
                    file = Files.createTempFile('grails-owner-', '.tmp')
                    if (Files.getFileStore(file).supportsFileAttributeView(ACCESS_CONTROL)) {
                        created = Files.getOwner(file, LinkOption.NOFOLLOW_LINKS)
                    }
                } catch (IOException | RuntimeException ignored) {
                    // then it cannot be told, and no file of a group is the user's own
                } finally {
                    if (file != null) {
                        try {
                            Files.deleteIfExists(file)
                        } catch (IOException ignored) {
                            // an empty file where temporary files go
                        }
                    }
                }
            }
            return created
        }

        @Override
        UserPrincipal ownerOf(Path path) throws IOException {
            return Files.getOwner(path, LinkOption.NOFOLLOW_LINKS)
        }
    }

    private static final class Holder {

        private static final ProcessUser CURRENT = establish(Collections.<String>emptyList())
    }
}
