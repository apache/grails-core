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

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.apache.grails.forge.core.pack.PackDescriptorParser
import org.apache.grails.forge.core.state.ProjectState
import org.grails.forge.io.OutputUtils
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFilePermission
import java.security.MessageDigest
import java.util.function.Consumer
import java.util.function.Supplier
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Writes several files of a project as one change. No file system replaces many files at once, so the
 * change is made of steps that can be undone:
 *
 * <ol>
 * <li>the lock of the project, {@code .grails/transactions/lock}, is taken, which another command
 * that changes the project waits for or is refused by;</li>
 * <li>every file to write is compared with what it was when the change was planned;</li>
 * <li>the new content of every file, and a copy of every file that is replaced, are written to
 * {@code .grails/transactions/current}, and then the journal, which names each file with the digests
 * of what it was and of what it becomes. Until the journal is there, nothing of the project was
 * touched;</li>
 * <li>each file is compared once more and replaced by a rename;</li>
 * <li>the journal is removed, which ends the change, and then the directory.</li>
 * </ol>
 *
 * <p>When a file cannot be written, the files that were written are put back, each only when it still
 * is what the change wrote. A change that was interrupted leaves its journal, and {@link #recover(Path)}
 * puts the files back the same way. A file that is neither what it was before nor what the change
 * wrote was changed by somebody else: it is left as it is, the journal stays, and nothing else changes
 * the project until that file is what it was.</p>
 *
 * <p>The lock is between the commands that change a project. It does not keep an editor from writing
 * a file while the change is made.</p>
 */
@CompileStatic
@PackageScope
final class ProjectTransaction {

    static final String DIRECTORY = '.grails/transactions'
    static final String CURRENT = '.grails/transactions/current'
    static final String BACKUPS = '.grails/transactions/current/backup'

    private static final String JOURNAL = 'journal'
    private static final String HEADER = 'grails-forge-transaction 1'
    private static final String END = 'end'
    private static final int MAX_JOURNAL = 16 * 1024 * 1024
    private static final int MAX_NAMED = 10
    private static final Pattern PUT = Pattern.compile(/put (\d{1,6}) (-|[0-9a-f]{64}) ([0-9a-f]{64}) (.+)/)
    private static final Pattern MADE = Pattern.compile(/dir (.+)/)
    private static final Set<PosixFilePermission> EXECUTE = EnumSet.of(PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.OTHERS_EXECUTE)

    private ProjectTransaction() {
    }

    /**
     * A file to write.
     */
    @CompileStatic
    static final class Write {

        final String path
        final byte[] content
        // whether the file is to be executable, or null for a file that stays as it is in that
        final Boolean executable
        // the digest of the file as it is, or null when there is none
        final String before

        Write(String path, byte[] content, Boolean executable, String before) {
            this.path = path
            this.content = content
            this.executable = executable
            this.before = before
        }
    }

    /**
     * @return whether a change of the project was begun and not ended
     */
    static boolean isInterrupted(Path project) {
        return Files.exists(project.resolve(CURRENT), LinkOption.NOFOLLOW_LINKS)
    }

    /**
     * Writes the files, all of them or none.
     *
     * @param installed told the path of each file after it was written
     * @throws ProjectChangeException when the files could not be written; it says whether the project
     * is as it was
     */
    static void run(Path project, List<Write> writes, Consumer<String> installed) {
        locked(project) {
            Path current = project.resolve(CURRENT)
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                throw new ProjectChangeException("An earlier change of the project was interrupted (${CURRENT}); it has to be undone before the project is changed again")
            }
            List<Put> puts = []
            Set<String> made = new TreeSet<>()
            try {
                stage(project, current, writes, puts, made)
            } catch (IOException e) {
                discard(current)
                throw new ProjectChangeException("The change could not be prepared in ${DIRECTORY} (${e.getClass().getSimpleName()}); nothing of the project was changed", e)
            } catch (ProjectChangeException e) {
                discard(current)
                throw e
            }
            for (int index = 0; index < writes.size(); index++) {
                Write write = writes[index]
                String failure = null
                Exception cause = null
                try {
                    Path target = target(project, write.path)
                    if (digest(target) != write.before) {
                        failure = "${write.path} was changed while the project was being changed"
                    } else {
                        Files.createDirectories(target.getParent())
                        move(current.resolve('new').resolve(String.valueOf(index)), target)
                    }
                } catch (IOException e) {
                    failure = "${write.path} could not be written (${e.getClass().getSimpleName()})"
                    cause = e
                }
                if (failure != null) {
                    List<String> left = undo(project, current, puts, made)
                    throw new ProjectChangeException("${failure}; " + (left.isEmpty() ? 'the project is as it was before' : leftAsTheyAre(left)), cause)
                }
                // what the listener throws is not caught: the change then stays where it is, as it
                // does when the program ends here, and is undone by recover
                installed.accept(write.path)
            }
            try {
                Files.delete(current.resolve(JOURNAL))
            } catch (IOException e) {
                throw new ProjectChangeException("The project was changed, and the record of the change could not be removed (${e.getClass().getSimpleName()}); remove the directory ${CURRENT}", e)
            }
            discard(current)
            return null
        }
    }

    /**
     * Undoes a change that was interrupted.
     *
     * @return whether there was one whose files had to be put back
     * @throws ProjectChangeException when a file was changed since and is left as it is
     */
    static boolean recover(Path project) {
        Path current = project.resolve(CURRENT)
        if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
            return false
        }
        return locked(project) {
            if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                return false
            }
            if (!Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                throw new ProjectChangeException("${CURRENT} is not a directory; remove it")
            }
            Path journal = current.resolve(JOURNAL)
            if (!Files.isRegularFile(journal, LinkOption.NOFOLLOW_LINKS)) {
                // the journal is written after everything else of the change and before any file of
                // the project is: without it nothing was changed, or all of it was
                discard(current)
                return false
            }
            List<Put> puts = []
            Set<String> made = new TreeSet<>()
            try {
                read(journal, puts, made)
            } catch (IOException | IllegalArgumentException e) {
                throw new ProjectChangeException("The record of a change of the project that was interrupted (${CURRENT}/${JOURNAL}) cannot be read; " +
                        "restore the project from version control and remove the directory ${CURRENT}", e)
            }
            List<String> left = undo(project, current, puts, made)
            if (!left.isEmpty()) {
                throw new ProjectChangeException('A change of the project was interrupted and could not be undone: ' + leftAsTheyAre(left))
            }
            return true
        }
    }

    private static String leftAsTheyAre(List<String> left) {
        String more = left.size() > MAX_NAMED ? " and ${left.size() - MAX_NAMED} more" : ''
        return "${left.size() == 1 ? 'a file that was changed since is' : 'files that were changed since are'} left as ${left.size() == 1 ? 'it is' : 'they are'}: " +
                "${left.take(MAX_NAMED).join(', ')}${more}. Put ${left.size() == 1 ? 'it' : 'each'} back as it was before the change (the files that were replaced are " +
                "kept in ${BACKUPS}, and a file that was new is to be removed), or restore the project from version control and remove the directory ${CURRENT}; " +
                'then run the command again'
    }

    private static void stage(Path project, Path current, List<Write> writes, List<Put> puts, Set<String> made) throws IOException {
        Files.createDirectory(current)
        Path staged = Files.createDirectory(current.resolve('new'))
        Path backups = Files.createDirectory(current.resolve('backup'))
        boolean posix = project.getFileSystem().supportedFileAttributeViews().contains('posix')
        StringBuilder journal = new StringBuilder(HEADER).append('\n')
        for (int index = 0; index < writes.size(); index++) {
            Write write = writes[index]
            if (!isWritable(write.path)) {
                throw new ProjectChangeException("${write.path} is not a path of the project that can be written")
            }
            Path target = target(project, write.path)
            String directory = ''
            Path parent = project
            List<String> steps = write.path.split('/').toList()
            for (String step : steps.subList(0, steps.size() - 1)) {
                parent = parent.resolve(step)
                directory = directory.isEmpty() ? step : directory + '/' + step
                if (!Files.exists(parent, LinkOption.NOFOLLOW_LINKS)) {
                    made.add(directory)
                }
            }
            Path file = staged.resolve(String.valueOf(index))
            store(file, write.content)
            if (write.before != null) {
                Path backup = backups.resolve(String.valueOf(index))
                Files.copy(target, backup, StandardCopyOption.COPY_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS)
                if (digest(backup) != write.before) {
                    throw new ProjectChangeException("${write.path} was changed while the change of the project was being prepared; nothing of the project was changed")
                }
            } else if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new ProjectChangeException("${write.path} was created while the change of the project was being prepared; nothing of the project was changed")
            }
            if (posix) {
                Set<PosixFilePermission> permissions = new HashSet<>(Files.getPosixFilePermissions(write.before != null ? target : file, LinkOption.NOFOLLOW_LINKS))
                if (write.executable == Boolean.TRUE) {
                    permissions.add(PosixFilePermission.OWNER_EXECUTE)
                } else if (write.executable == Boolean.FALSE) {
                    permissions.removeAll(EXECUTE)
                }
                Files.setPosixFilePermissions(file, permissions)
            }
            Put put = new Put(index, write.before, sha256(write.content), write.path)
            puts << put
            journal.append("put ${put.index} ${put.before ?: '-'} ${put.after} ${put.path}\n")
        }
        for (String directory : made) {
            journal.append("dir ${directory}\n")
        }
        journal.append(END).append('\n')
        Path unfinished = current.resolve(JOURNAL + '.tmp')
        store(unfinished, journal.toString().getBytes(StandardCharsets.UTF_8))
        move(unfinished, current.resolve(JOURNAL))
    }

    // Puts back, last first, every file the change wrote that still is what it wrote, and removes the
    // directories it made. Returns the files that are neither what they were nor what was written;
    // when there is none, nothing of the change is left
    private static List<String> undo(Path project, Path current, List<Put> puts, Set<String> made) {
        List<String> left = []
        for (Put put : puts.reverse()) {
            try {
                Path target = target(project, put.path)
                String now = digest(target)
                Path backup = current.resolve('backup').resolve(String.valueOf(put.index))
                if (now == put.after && put.before == null) {
                    Files.delete(target)
                } else if (now == put.after && Files.isRegularFile(backup, LinkOption.NOFOLLOW_LINKS) && digest(backup) == put.before) {
                    move(backup, target)
                } else if (now != put.before) {
                    left << put.path
                }
            } catch (IOException ignored) {
                left << put.path
            }
        }
        for (String directory : made.toList().reverse()) {
            try {
                Path path = target(project, directory)
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    Files.delete(path)
                }
            } catch (IOException ignored) {
                // a directory that has other files now stays
            }
        }
        if (left.isEmpty()) {
            try {
                Files.deleteIfExists(current.resolve(JOURNAL))
            } catch (IOException e) {
                throw new ProjectChangeException("The project is as it was before, and the record of the change could not be removed (${e.getClass().getSimpleName()}); " +
                        "remove the directory ${CURRENT}", e)
            }
            discard(current)
        }
        return left.reverse()
    }

    private static void read(Path journal, List<Put> puts, Set<String> made) throws IOException {
        if (Files.size(journal) > MAX_JOURNAL) {
            throw new IllegalArgumentException('too large')
        }
        byte[] bytes
        try (InputStream input = Files.newInputStream(journal, LinkOption.NOFOLLOW_LINKS)) {
            bytes = input.readNBytes(MAX_JOURNAL + 1)
        }
        List<String> lines = new String(bytes, StandardCharsets.UTF_8).split('\n', -1).toList()
        if (bytes.length > MAX_JOURNAL || lines.size() < 3 || lines.first() != HEADER || lines[lines.size() - 2] != END || !lines.last().isEmpty()) {
            throw new IllegalArgumentException('not a journal')
        }
        Set<Integer> seen = new HashSet<>()
        for (String line : lines.subList(1, lines.size() - 2)) {
            Matcher put = PUT.matcher(line)
            Matcher directory = MADE.matcher(line)
            if (put.matches() && isWritable(put.group(4)) && seen.add(Integer.valueOf(put.group(1)))) {
                puts << new Put(Integer.parseInt(put.group(1)), put.group(2) == '-' ? null : put.group(2), put.group(3), put.group(4))
            } else if (directory.matches() && isWritable(directory.group(1))) {
                made.add(directory.group(1))
            } else {
                throw new IllegalArgumentException('not a journal')
            }
        }
    }

    // a file of the application, or of its state: nothing of Git, and nothing of the changes themselves
    private static boolean isWritable(String path) {
        if (!OutputUtils.isRelativePath(path) || !PackDescriptorParser.isPathBelowRoot(path)) {
            return false
        }
        String folded = path.toLowerCase(Locale.ROOT)
        if (folded == '.git' || folded.startsWith('.git/')) {
            return false
        }
        return !ProjectState.isStatePath(path) || ProjectState.isBaselinePath(path) || path == ProjectState.LOCK || path == ProjectState.MANIFEST
    }

    private static <T> T locked(Path project, Supplier<T> work) {
        Path directory = project
        for (String step : DIRECTORY.split('/')) {
            directory = directory.resolve(step)
            if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS) && step != ProjectState.DIRECTORY) {
                try {
                    Files.createDirectory(directory)
                } catch (IOException e) {
                    throw new ProjectChangeException("The directory ${DIRECTORY} could not be created (${e.getClass().getSimpleName()}); nothing of the project was changed", e)
                }
            }
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                throw new ProjectChangeException("${DIRECTORY} is not a directory of the project; nothing of the project was changed")
            }
        }
        FileChannel channel
        try {
            channel = FileChannel.open(directory.resolve('lock'), StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)
        } catch (IOException e) {
            throw new ProjectChangeException("The lock of the project, ${DIRECTORY}/lock, could not be opened (${e.getClass().getSimpleName()}); nothing of the project was changed", e)
        }
        try {
            FileLock lock
            try {
                lock = channel.tryLock()
            } catch (OverlappingFileLockException | IOException ignored) {
                lock = null
            }
            if (lock == null) {
                throw new ProjectChangeException('Another command is changing the project; nothing was changed by this one. Run it again when the other has ended')
            }
            try {
                return work.get()
            } finally {
                lock.release()
            }
        } finally {
            channel.close()
        }
    }

    // where a file of the project is, when every directory on the way that is there is a directory
    // of the project and not a link out of it
    private static Path target(Path project, String path) throws IOException {
        Path target = project
        List<String> steps = path.split('/').toList()
        for (String step : steps.subList(0, steps.size() - 1)) {
            target = target.resolve(step)
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException('Not a directory')
            }
        }
        return target.resolve(steps.last())
    }

    /**
     * @return the SHA-256 of a file, {@code null} when there is no file, and what is no digest for
     * what is there and is no file
     */
    static String digest(Path file) throws IOException {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            return null
        }
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            return 'not a file'
        }
        MessageDigest digest = MessageDigest.getInstance('SHA-256')
        try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer = new byte[65536]
            for (int read = input.read(buffer); read >= 0; read = input.read(buffer)) {
                digest.update(buffer, 0, read)
            }
        }
        return HexFormat.of().formatHex(digest.digest())
    }

    static String sha256(byte[] bytes) {
        return HexFormat.of().formatHex(MessageDigest.getInstance('SHA-256').digest(bytes))
    }

    private static void store(Path file, byte[] content) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(content)
            while (buffer.hasRemaining()) {
                channel.write(buffer)
            }
            channel.force(true)
        }
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private static void discard(Path current) {
        if (!Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
            return
        }
        try {
            Files.walkFileTree(current, new SimpleFileVisitor<Path>() {

                @Override
                FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    Files.delete(file)
                    return FileVisitResult.CONTINUE
                }

                @Override
                FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                    if (failure != null) {
                        throw failure
                    }
                    Files.delete(directory)
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (IOException e) {
            throw new ProjectChangeException("What a change of the project left in ${CURRENT} could not be removed (${e.getClass().getSimpleName()}); remove the directory", e)
        }
    }

    @CompileStatic
    private static final class Put {

        final int index
        final String before
        final String after
        final String path

        Put(int index, String before, String after, String path) {
            this.index = index
            this.before = before
            this.after = after
            this.path = path
        }
    }
}
