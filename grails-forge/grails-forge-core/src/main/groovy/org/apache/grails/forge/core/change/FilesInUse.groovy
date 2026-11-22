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
package org.apache.grails.forge.core.change

import java.nio.file.AccessDeniedException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import groovy.transform.CompileStatic

/**
 * Replaces and removes a file of a project that another program may have open for a moment.
 *
 * <p>On Windows a file that a program has open is not replaced and not removed: the system refuses,
 * and an editor, an indexer or a scanner for viruses opens a file of a project whenever it changes,
 * for a fraction of a second. So what the system refuses there is tried again a few times, for a
 * second and a half at most, before it is a failure. Elsewhere a file that is open is replaced and
 * removed as any other, and nothing is tried twice.</p>
 *
 * <p>Only what can be the use of a file by another program is tried again. The JDK says which error
 * of Windows it was only in the words of the system, which are in its language, so it is told by
 * what it is not: the exception is the plain one of the file system or the one for access that is
 * denied, which is what Windows says of a file that is open when it is replaced, and not one that
 * says the file is not there, is there already or is a directory that is not empty; and the file, or
 * the directory it is to be in, can be written to as its attributes and its access control list have
 * it, so that a file that is read-only or of another user fails at once.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class FilesInUse {

    /** How long is waited before each further try, in milliseconds. */
    public static final List<Long> PAUSES = Collections.unmodifiableList([50L, 100L, 200L, 400L, 800L])

    private static volatile boolean tried = File.separatorChar == ('\\' as char)
    private static volatile Sleeper sleeper = { long millis -> Thread.sleep(millis) } as Sleeper

    private FilesInUse() {
    }

    /** What is done with a file. */
    static interface Operation {

        void run() throws IOException
    }

    /** What waits between two tries. */
    static interface Sleeper {

        void sleep(long millis) throws InterruptedException
    }

    /**
     * Replaces what the system is and how the wait is done, for a specification.
     *
     * @param again whether what is refused is tried again, as on Windows; {@code null} for what the system is
     * @param waits what waits; {@code null} for the wait of the thread
     */
    static void set(Boolean again, Sleeper waits) {
        tried = again == null ? File.separatorChar == ('\\' as char) : again.booleanValue()
        sleeper = waits == null ? ({ long millis -> Thread.sleep(millis) } as Sleeper) : waits
    }

    /**
     * @param file the file or directory that is replaced or removed
     * @param operation what replaces or removes it
     * @throws IOException what the last try said, when none succeeded
     */
    static void attempt(Path file, Operation operation) throws IOException {
        int tries = 0
        while (true) {
            try {
                operation.run()
                return
            } catch (IOException e) {
                if (tries >= PAUSES.size() || !mayBeInUse(e, file)) {
                    throw e
                }
                try {
                    sleeper.sleep(PAUSES[tries++].longValue())
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt()
                    throw e
                }
            }
        }
    }

    /**
     * @param e what the system said
     * @param file the file or directory that was to be replaced or removed
     * @return whether it can be that another program had the file open, and it is worth another try
     */
    static boolean mayBeInUse(IOException e, Path file) {
        if (!tried || !(e.getClass() == FileSystemException || e instanceof AccessDeniedException)) {
            return false
        }
        try {
            Path asked = Files.exists(file, LinkOption.NOFOLLOW_LINKS) ? file : file.toAbsolutePath().getParent()
            return asked != null && Files.isWritable(asked)
        } catch (RuntimeException ignored) {
            return false
        }
    }
}
