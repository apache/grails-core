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
import groovy.transform.CompileStatic

/**
 * The user the process runs as, taken from the process itself and from nothing the process could be
 * told or could find changed: not from a file it creates where temporary files go, and not from the
 * owner of a home directory. It is asked for in this order, and the first that answers is it:
 *
 * <ol>
 * <li>{@link #UID}: the user id of the process, where the files have the user id of their owner
 * (POSIX), as the system module of the JDK for it says</li>
 * <li>{@link #PROC}: on Linux, the user id that owns {@code /proc/self}</li>
 * <li>{@link #NAME}: the name of the user of the process as the JDK reports it, which is compared
 * with the name of the owner of a file; the one there is on Windows</li>
 * </ol>
 *
 * When none answers, the user is not known, and no file is one of theirs.
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

    private static final String USER_ID = 'unix:uid'

    private final Integer uid
    private final String name
    private final String source

    private ProcessUser(Integer uid, String name, String source) {
        this.uid = uid
        this.name = name
        this.source = source
    }

    /**
     * @return the user of this process, established once
     */
    static ProcessUser current() {
        return Holder.CURRENT
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
                return new ProcessUser(id, null, UID)
            }
        }
        if (ids && !(PROC in without)) {
            try {
                Object owner = Files.getAttribute(Path.of('/proc/self'), USER_ID)
                if (owner instanceof Number) {
                    return new ProcessUser(((Number) owner).intValue(), null, PROC)
                }
            } catch (IOException | RuntimeException ignored) {
                // there is no such directory but on Linux
            }
        }
        if (!(NAME in without)) {
            try {
                String user = ProcessHandle.current().info().user().orElse(null)
                if (user != null && !user.isEmpty()) {
                    return new ProcessUser(null, user, NAME)
                }
            } catch (RuntimeException ignored) {
                // then it is not known
            }
        }
        return new ProcessUser(null, null, null)
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
     * @return whether it is owned by the user of the process; false when either is not known
     */
    boolean owns(Path path) {
        try {
            if (uid != null) {
                Object owner = Files.getAttribute(path, USER_ID, LinkOption.NOFOLLOW_LINKS)
                return owner instanceof Number && ((Number) owner).intValue() == uid.intValue()
            }
            if (name != null) {
                String owner = Files.getOwner(path, LinkOption.NOFOLLOW_LINKS).getName()
                // Windows names the kind of the owner after the name, as in HOST\me (User)
                return owner.equalsIgnoreCase(name) || owner.toLowerCase(Locale.ROOT).startsWith(name.toLowerCase(Locale.ROOT) + ' (')
            }
        } catch (IOException | RuntimeException ignored) {
            // an owner that cannot be read is not this user
        }
        return false
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

    private static final class Holder {

        private static final ProcessUser CURRENT = establish(Collections.<String>emptyList())
    }
}
