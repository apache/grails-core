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
import org.apache.grails.forge.core.state.BaselineIndex
import org.apache.grails.forge.core.state.BaselineIndex.BaselineFile
import org.apache.grails.forge.core.state.ProjectState
import org.apache.grails.forge.core.state.ProjectStateException
import org.apache.grails.forge.core.state.StateLimits
import org.grails.forge.io.OutputUtils
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.attribute.UserPrincipal
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.function.Consumer
import java.util.function.Predicate
import java.util.function.Supplier
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.stream.Stream

/**
 * Writes several files of a project as one change. No file system replaces many files at once, so the
 * change is made of steps that can be undone:
 *
 * <ol>
 * <li>the lock of the project, {@code .grails/transactions/lock}, is taken, which another command
 * that changes the project is refused by;</li>
 * <li>a record that a change begins is written to the directory of whoever runs the command, outside
 * the project: {@code ~/.grails/forge/transactions/<digest of the path of the project>/journal};</li>
 * <li>the new content of every file, a copy of every file that is replaced, and the state the change
 * comes to are written to a directory of the change in the project, {@code .grails/transactions/<a random name>}, beside the
 * files, so that a file is replaced and put back by a rename within one file system. Every file is
 * compared with what it was when the change was planned;</li>
 * <li>the record becomes the journal: the name of that directory and every file with the digests of
 * what it was and of what it becomes. Until the journal is there, nothing of the project was
 * touched;</li>
 * <li>each file is compared once more and replaced by a rename; a file the change takes away is
 * removed, and its copy is what puts it back;</li>
 * <li>the journal is removed, which ends the change, and then the directory.</li>
 * </ol>
 *
 * <p>Only the journal says that there is a change to undo and which files are its: it is not in the
 * project, so that nothing a project comes with (a clone, an archive) can make a command replace or
 * remove a file. A directory under {@code .grails/transactions} that no journal of this user names is
 * never read.</p>
 *
 * <p>When a file cannot be written, the files the change wrote are put back, and a file it had not
 * come to is left as it is. A change that was interrupted is found by its journal, and what is done
 * about it goes by the digests of its files alone, see {@code settle}: complete when every file is
 * what the change makes it, nothing to undo when every file is what it was, nothing put back when a
 * file is neither, and the written files put back otherwise.</p>
 *
 * <p>The lock is between the commands that change a project. It does not keep an editor from writing
 * a file while the change is made or undone: a file is compared with what it is to be just before it
 * is replaced or put back, which leaves only the moment between the two.</p>
 */
@CompileStatic
@PackageScope
final class ProjectTransaction {

    static final String DIRECTORY = '.grails/transactions'

    private static final String LOCK = 'lock'
    private static final String JOURNAL = 'journal'
    private static final String HEADER = 'grails-forge-transaction 3'
    private static final String STAGING = 'staging'
    private static final String END = 'end'
    private static final int MAX_JOURNAL = 16 * 1024 * 1024
    private static final int MAX_NAMED = 10
    private static final Pattern PROJECT = Pattern.compile(/project ([A-Za-z0-9_-]+)/)
    private static final Pattern NAME = Pattern.compile(/id (t[0-9a-f]{24})/)
    private static final Pattern PUT = Pattern.compile(/put (\d{1,6}) (-|[0-9a-f]{64}) (-|[0-9a-f]{64}) (.+)/)
    private static final Pattern MADE = Pattern.compile(/dir (.+)/)
    private static final Set<PosixFilePermission> EXECUTE = EnumSet.of(PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.OTHERS_EXECUTE)
    private static final List<String> STATE = [ProjectState.MANIFEST, ProjectState.LOCK, ProjectState.BASELINE_INDEX]

    private ProjectTransaction() {
    }

    /**
     * A file to write, or, without content, a file to remove.
     */
    @CompileStatic
    static final class Write {

        final String path
        // what the file becomes, or null for a file that is removed, which has to be there
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
     * @param home the home directory of the user, which the journals are kept in
     * @return whether a change of the project was begun by this user and not ended
     */
    static boolean isInterrupted(Path project, Path home) {
        if (unusable(home, project) != null) {
            // nothing is looked for where no record of this user can be: what is found by a home
            // directory that is none, such as a relative one, could have come with the project
            return false
        }
        Path journal = journal(project, home, false)
        return journal != null && Files.exists(journal, LinkOption.NOFOLLOW_LINKS)
    }

    /**
     * @return whether a change of this user was interrupted and left the project half changed. A
     * record of a change that had not begun to write, or whose every file is what it was before the
     * change, as in a fresh checkout at the same path, or what the change makes of it, is in the way
     * of nothing
     */
    static boolean isHalfMade(Path project, Path home) {
        if (!isInterrupted(project, home)) {
            return false
        }
        try {
            Journal change = read(journal(project, home, false), project)
            if (change.staging) {
                return false
            }
            boolean asBefore = true
            boolean asAfter = true
            for (Put put : change.puts) {
                String now = digest(target(project, put.path))
                asBefore = asBefore && now == put.before
                asAfter = asAfter && now == put.after
            }
            return !asBefore && !asAfter
        } catch (IOException | IllegalArgumentException ignored) {
            return true
        }
    }

    /**
     * @return the names of what is under {@code .grails/transactions} and is not of a change this
     * user began: it came with the project, or is left of a change another user did not end
     */
    static List<String> strangers(Path project, Path home) {
        Path directory = project.resolve('.grails').resolve('transactions')
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return []
        }
        String own = null
        try {
            Path journal = unusable(home, project) == null ? journal(project, home, false) : null
            if (journal != null && Files.isRegularFile(journal, LinkOption.NOFOLLOW_LINKS)) {
                own = read(journal, project).name
            }
        } catch (IOException | IllegalArgumentException ignored) {
            // a journal that cannot be read is said to be so when the change is undone
        }
        List<String> names = []
        try {
            Files.newDirectoryStream(directory).withCloseable { stream ->
                for (Path each : stream) {
                    String name = each.getFileName().toString()
                    if (name != LOCK && name != own && names.size() < MAX_NAMED) {
                        names << name
                    }
                }
            }
        } catch (IOException ignored) {
            return []
        }
        return names.sort()
    }

    /**
     * Writes the files, all of them or none.
     *
     * @param home the home directory of the user, which the journals are kept in
     * @param installed told the path of each file after it was written
     * @throws ProjectChangeException when the files could not be written; it says whether the project
     * is as it was
     */
    static void run(Path project, Path home, List<Write> writes, Consumer<String> installed) {
        usable(home, project)
        locked(project) {
            Path journal = journal(project, usable(home, project), true)
            if (Files.exists(journal, LinkOption.NOFOLLOW_LINKS)) {
                throw new ProjectChangeException('An earlier change of the project was interrupted; it has to be undone before the project is changed again')
            }
            byte[] random = new byte[12]
            new SecureRandom().nextBytes(random)
            Journal change = new Journal(project.toRealPath().toString(), 't' + HexFormat.of().formatHex(random))
            Path directory = project.resolve('.grails').resolve('transactions').resolve(change.name)
            try {
                // first that a change begins and where it keeps its files, so that the directory is
                // known to be of this user when the change ends before its journal is written
                record(journal, change, false)
                stage(project, directory, writes, change)
                record(journal, change, true)
            } catch (IOException e) {
                abandon(directory, journal)
                throw new ProjectChangeException("The change could not be prepared (${e.getClass().getSimpleName()}); nothing of the project was changed", e)
            } catch (ProjectChangeException e) {
                abandon(directory, journal)
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
                    } else if (write.content == null) {
                        remove(target)
                    } else {
                        Files.createDirectories(target.getParent())
                        move(directory.resolve('new').resolve(String.valueOf(index)), target)
                    }
                } catch (IOException e) {
                    failure = "${write.path} could not be written (${e.getClass().getSimpleName()})"
                    cause = e
                }
                if (failure != null) {
                    ProjectRecovery undone
                    try {
                        undone = settle(project, journal, change, index) { String path -> }
                    } catch (ProjectChangeException e) {
                        throw new ProjectChangeException("${failure}, and the files that were written could not be put back: ${e.getMessage()}", cause)
                    }
                    throw new ProjectChangeException("${failure}; nothing was added, and the project is as it was before" +
                            (undone.keptAsTheyAre.isEmpty() ? '' : ", with what was changed in ${named(undone.keptAsTheyAre)} meanwhile"), cause)
                }
                // what the listener throws is not caught: the change then stays where it is, as it
                // does when the program ends here, and is undone by recover
                installed.accept(write.path)
            }
            // the names of the new files reach the disk before the journal, by which they could be put back, is removed
            Set<Path> written = new LinkedHashSet<>()
            for (Write write : writes) {
                written.add(target(project, write.path).getParent())
            }
            for (Path each : written) {
                force(each)
            }
            finish(directory, journal)
            // the directories that only held what was removed go with it, once the change has ended:
            // a change that is undone puts its files back into them
            for (Write write : writes) {
                if (write.content == null) {
                    removeEmptied(project, write.path)
                }
            }
            return null
        }
    }

    /**
     * Ends a change that was interrupted: undoes it, or removes its journal when every file of it
     * was written.
     *
     * @param home the home directory of the user, which the journals are kept in
     * @param restored told the path of each file after it was put back
     * @throws ProjectChangeException when the change cannot be undone, in which case nothing was
     * put back
     */
    static ProjectRecovery recover(Path project, Path home, Consumer<String> restored) {
        usable(home, project)
        List<String> others = strangers(project, home)
        if (!isInterrupted(project, home)) {
            return new ProjectRecovery(false, false, false, [], [], others)
        }
        return locked(project) {
            Path journal = journal(project, home, false)
            if (journal == null || !Files.exists(journal, LinkOption.NOFOLLOW_LINKS)) {
                return new ProjectRecovery(false, false, false, [], [], others)
            }
            Journal change
            try {
                if (!Files.isRegularFile(journal, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IllegalArgumentException('not a file')
                }
                change = read(journal, project)
            } catch (IOException | IllegalArgumentException e) {
                throw new ProjectChangeException('The record of a change of the project that was interrupted cannot be read; restore the project from version control ' +
                        'and ' + removal(journal), e)
            }
            ProjectRecovery recovery = settle(project, journal, change, -1, restored)
            return new ProjectRecovery(recovery.undone, recovery.completed, recovery.discarded, recovery.putBack, recovery.keptAsTheyAre, strangers(project, home))
        }
    }

    /**
     * The end of a change that did not come to its end. Every file of the change is looked at before
     * anything is done, and what is done goes by what the files are, not by what is left of the
     * change in its directory:
     *
     * <ul>
     * <li>every file is what the change makes it: the change is complete, by whoever completed it,
     * and only its journal and its directory are removed;</li>
     * <li>every file is what it was before the change: nothing is to undo, and the journal and the
     * directory are removed;</li>
     * <li>a file is neither: it was changed by somebody else. Nothing is put back, and the files are
     * named;</li>
     * <li>otherwise some files are what the change makes them and the others what they were: the
     * first are put back from their copies, each of which has to be what the file was. Each file is
     * compared once more just before it is put back, as one is before the change writes it: a file
     * that was changed since it was looked at stops the undoing there, the files that were put back
     * stay so, and the journal stays for the rest.</li>
     * </ul>
     *
     * @param reached for a change that fails while it is made, how many of its files it wrote: only
     * those are looked at and put back, and a file it had not come to is left as it is, whatever it
     * is now; -1 for a change that is found by its journal
     */
    private static ProjectRecovery settle(Path project, Path journal, Journal change, int reached, Consumer<String> restored) {
        Path directory = project.resolve('.grails').resolve('transactions').resolve(change.name)
        if (change.staging) {
            // the change ended before its journal was written: nothing of the project was touched
            abandon(directory, journal)
            return new ProjectRecovery(false, false, true, [], [], [])
        }
        for (Path each : [directory, directory.resolve('new'), directory.resolve('backup'), directory.resolve('after')]) {
            if (Files.exists(each, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(each, LinkOption.NOFOLLOW_LINKS)) {
                throw new ProjectChangeException("${DIRECTORY}/${change.name}, where the change that was interrupted keeps the files it replaced, is not the directory " +
                        'it was; nothing was put back. Restore the project from version control and ' + removal(journal))
            }
        }
        List<Put> written = []
        List<String> neither = []
        List<String> kept = []
        boolean allAfter = true
        boolean allBefore = true
        boolean posix = project.getFileSystem().supportedFileAttributeViews().contains('posix')
        try {
            for (Put put : change.puts) {
                Path target = target(project, put.path)
                String now = digest(target)
                if (reached >= 0 && put.index >= reached) {
                    if (now != put.before) {
                        kept << put.path
                    }
                } else if (put.before == put.after && now == put.after) {
                    // Only the mode of the file changes, so its digest is what it was and what it
                    // becomes at once. The copy that was kept has the mode it had: the file was
                    // written when its mode is another, and without the copy nothing says which
                    Path backup = directory.resolve('backup').resolve(String.valueOf(put.index))
                    if (Files.isRegularFile(backup, LinkOption.NOFOLLOW_LINKS)) {
                        if (posix && Files.getPosixFilePermissions(backup, LinkOption.NOFOLLOW_LINKS) != Files.getPosixFilePermissions(target, LinkOption.NOFOLLOW_LINKS)) {
                            written << put
                            allBefore = false
                        } else {
                            allAfter = false
                        }
                    }
                } else if (now == put.after) {
                    written << put
                    allBefore = false
                } else if (now == put.before) {
                    allAfter = false
                } else {
                    neither << put.path
                }
            }
        } catch (IOException e) {
            throw new ProjectChangeException("The files of the change that was interrupted cannot be read (${e.getClass().getSimpleName()}); nothing was put back", e)
        }
        if (!neither.isEmpty()) {
            throw new ProjectChangeException("${neither.size() == 1 ? 'A file' : 'Files'} of a change that was interrupted ${neither.size() == 1 ? 'is' : 'are'} neither what " +
                    "${neither.size() == 1 ? 'it was' : 'they were'} before the change nor what the change makes of ${neither.size() == 1 ? 'it' : 'them'}: " +
                    "${named(neither)}. Nothing was put back. Make ${neither.size() == 1 ? 'it' : 'each'} what it was before the change (the files that were replaced " +
                    "are kept in ${DIRECTORY}/${change.name}/backup, and a file that was new is to be removed), or what the change makes of it, and run the command again; " +
                    'or restore the project from version control and ' + removal(journal))
        }
        if (reached < 0 && allAfter) {
            // every file is what the change makes it: all that was missing is the end of its journal
            finish(directory, journal)
            for (Put put : change.puts) {
                if (put.after == null) {
                    removeEmptied(project, put.path)
                }
            }
            return new ProjectRecovery(false, true, false, [], [], [])
        }
        if (reached < 0 && allBefore) {
            // nothing of this tree was changed: the journal is of a change that was undone by other
            // means, or of another checkout at this path. The directories the change made for its
            // files are removed when nothing is in them
            removeMade(project, change)
            abandon(directory, journal)
            return new ProjectRecovery(false, false, true, [], [], [])
        }
        if (reached < 0) {
            permitted(project, directory, change, journal)
        }
        try {
            for (Put put : written) {
                Path backup = directory.resolve('backup').resolve(String.valueOf(put.index))
                if (put.before != null && !(Files.isRegularFile(backup, LinkOption.NOFOLLOW_LINKS) && digest(backup) == put.before)) {
                    throw new ProjectChangeException("The copy of ${put.path} that was kept in ${DIRECTORY}/${change.name}/backup is not what the file was; nothing was " +
                            'put back. Restore the project from version control and ' + removal(journal))
                }
            }
            List<String> putBack = []
            for (Put put : written.reverse()) {
                Path target = target(project, put.path)
                // Each file is compared once more just before it is put back, as it is before it is
                // written: what was saved in it since the files were looked at is not overwritten. The
                // files put back so far stay so, and the journal stays for the rest
                if (digest(target) != put.after) {
                    throw new ProjectChangeException("${put.path} was changed while the change that was interrupted was being undone, and is left as it is. " +
                            "${putBack.size()} ${putBack.size() == 1 ? 'file was' : 'files were'} put back${putBack.isEmpty() ? '' : " (${named(putBack)})"}; " +
                            'the others were not, and the record of the change is kept. ' +
                            "Keep what was changed in ${put.path} elsewhere, make the file what it was before the change (the files that were replaced are kept in " +
                            "${DIRECTORY}/${change.name}/backup, and a file that was new is to be removed) or what the change makes of it, and run the command again; " +
                            'or restore the project from version control and ' + removal(journal))
                }
                if (put.before == null) {
                    remove(target)
                } else {
                    // a file that was removed goes back where it was, also when its directory went since
                    Files.createDirectories(target.getParent())
                    move(directory.resolve('backup').resolve(String.valueOf(put.index)), target)
                }
                putBack << put.path
                restored.accept(put.path)
            }
            removeMade(project, change)
            finish(directory, journal)
            return new ProjectRecovery(true, false, false, putBack.reverse(), kept, [])
        } catch (IOException e) {
            throw new ProjectChangeException("A file of the change that was interrupted could not be put back (${e.getClass().getSimpleName()}); run the command again", e)
        }
    }

    // the directories above a file that was removed, the innermost first, as far as they are empty
    private static void removeEmptied(Path project, String path) {
        List<String> steps = path.split('/').toList()
        List<Path> directories = []
        Path each = project
        for (String step : steps.subList(0, steps.size() - 1)) {
            each = each.resolve(step)
            // only directories of the project: none that is reached by a link
            if (!Files.isDirectory(each, LinkOption.NOFOLLOW_LINKS)) {
                return
            }
            directories << each
        }
        for (Path directory : directories.reverse()) {
            try {
                try (Stream<Path> entries = Files.list(directory)) {
                    if (entries.findAny().isPresent()) {
                        return
                    }
                }
                Files.delete(directory)
            } catch (IOException ignored) {
                // a directory that stays is in the way of nothing
                return
            }
        }
    }

    // the directories a change made for its files, the innermost first, when they are empty
    private static void removeMade(Path project, Journal change) {
        for (String made : change.made.toList().reverse()) {
            try {
                Path path = target(project, made)
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    Files.delete(path)
                }
            } catch (IOException ignored) {
                // a directory that has other files now stays
            }
        }
    }

    /**
     * What a journal names has to be what the generator writes: the three files of the state, which
     * are read as they were before the change and as they are after it and have to be a state each,
     * the files of the two baselines, and their copies.
     */
    private static void permitted(Path project, Path directory, Journal change, Path journal) {
        Set<String> paths = new HashSet<>(STATE)
        try {
            for (boolean after : [false, true]) {
                List<byte[]> state = []
                for (String file : STATE) {
                    Put put = change.puts.find { Put each -> each.path == file }
                    String wanted = put == null ? null : after ? put.after : put.before
                    Path kept = put == null ? null : directory.resolve(after ? 'after' : 'backup').resolve(String.valueOf(put.index))
                    Path source = put == null ? null : digest(target(project, file)) == wanted ? target(project, file) :
                            Files.isRegularFile(kept, LinkOption.NOFOLLOW_LINKS) && digest(kept) == wanted ? kept : null
                    if (source == null || Files.size(source) > StateLimits.MAX_INDEX) {
                        throw new ProjectChangeException("The state of the project ${after ? 'after' : 'before'} the change that was interrupted cannot be read: " +
                                "${file} is not what it was, and neither is its copy. Nothing was put back. Restore the project from version control and " +
                                removal(journal))
                    }
                    state << Files.readAllBytes(source)
                }
                BaselineIndex index = ProjectState.of(state[0], state[1], state[2]).getBaseline()
                for (BaselineFile file : index.files) {
                    paths.add(file.path)
                    paths.add("${ProjectState.BASELINE_FILES}/${BaselineIndex.copyPath(file.path)}".toString())
                }
            }
        } catch (IOException e) {
            throw new ProjectChangeException("The state of the project the change that was interrupted began with cannot be read (${e.getClass().getSimpleName()}); " +
                    'nothing was put back. Restore the project from version control and ' + removal(journal), e)
        } catch (ProjectStateException e) {
            throw new ProjectChangeException("The state of the project the change that was interrupted began with cannot be read (${e.getMessage()}); " +
                    'nothing was put back. Restore the project from version control and ' + removal(journal), e)
        }
        List<String> others = change.puts*.path.findAll { String path -> !paths.contains(path) }
        Set<String> above = new HashSet<>()
        for (String path : paths) {
            for (int slash = path.indexOf('/'); slash >= 0; slash = path.indexOf('/', slash + 1)) {
                above.add(path.substring(0, slash))
            }
        }
        others.addAll(change.made.findAll { String path -> !above.contains(path) })
        if (!others.isEmpty()) {
            throw new ProjectChangeException("The record of the change that was interrupted names what is no file of the generated application (${named(others)}); " +
                    'nothing was put back. Restore the project from version control and ' + removal(journal))
        }
    }

    private static String named(List<String> names) {
        return names.take(MAX_NAMED).join(', ') + (names.size() > MAX_NAMED ? " and ${names.size() - MAX_NAMED} more" : '')
    }

    private static String removal(Path journal) {
        return "remove the file ${JOURNAL} from the directory ${journal.getParent().getFileName()} of .grails/forge/transactions in the home directory of the user"
    }

    private static void stage(Path project, Path directory, List<Write> writes, Journal change) throws IOException {
        Files.createDirectory(directory)
        Path staged = Files.createDirectory(directory.resolve('new'))
        Path backups = Files.createDirectory(directory.resolve('backup'))
        // the state the change comes to, kept until the change has ended: the new files are moved
        // into their places, and what a journal may name is read from the state before and after
        Path after = Files.createDirectory(directory.resolve('after'))
        boolean posix = project.getFileSystem().supportedFileAttributeViews().contains('posix')
        for (int index = 0; index < writes.size(); index++) {
            Write write = writes[index]
            if (!isWritable(write.path)) {
                throw new ProjectChangeException("${write.path} is not a path of the project that can be written")
            }
            Path target = target(project, write.path)
            String at = ''
            Path parent = project
            List<String> steps = write.path.split('/').toList()
            for (String step : steps.subList(0, steps.size() - 1)) {
                parent = parent.resolve(step)
                at = at.isEmpty() ? step : at + '/' + step
                if (!Files.exists(parent, LinkOption.NOFOLLOW_LINKS)) {
                    change.made.add(at)
                }
            }
            if (write.content == null && write.before == null) {
                throw new ProjectChangeException("${write.path} is neither written nor removed by the change")
            }
            Path file = staged.resolve(String.valueOf(index))
            if (write.content != null) {
                store(file, write.content)
            }
            if (write.content != null && write.path in STATE) {
                store(after.resolve(String.valueOf(index)), write.content)
            }
            Set<PosixFilePermission> permissions = null
            if (write.before != null) {
                if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) || Files.size(target) > StateLimits.MAX_BASELINE_FILE) {
                    throw new ProjectChangeException("${write.path} was changed while the change of the project was being prepared; nothing of the project was changed")
                }
                // the copy is the file that is put back: with its bytes, and with its mode
                byte[] current
                try (InputStream input = Files.newInputStream(target, LinkOption.NOFOLLOW_LINKS)) {
                    current = input.readAllBytes()
                }
                if (sha256(current) != write.before) {
                    throw new ProjectChangeException("${write.path} was changed while the change of the project was being prepared; nothing of the project was changed")
                }
                Path backup = backups.resolve(String.valueOf(index))
                store(backup, current)
                if (posix) {
                    permissions = new HashSet<>(Files.getPosixFilePermissions(target, LinkOption.NOFOLLOW_LINKS))
                    Files.setPosixFilePermissions(backup, permissions)
                }
            } else if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new ProjectChangeException("${write.path} was created while the change of the project was being prepared; nothing of the project was changed")
            }
            if (posix && write.content != null) {
                permissions = permissions ?: new HashSet<>(Files.getPosixFilePermissions(file, LinkOption.NOFOLLOW_LINKS))
                if (write.executable == Boolean.TRUE) {
                    permissions.add(PosixFilePermission.OWNER_EXECUTE)
                } else if (write.executable == Boolean.FALSE) {
                    permissions.removeAll(EXECUTE)
                }
                Files.setPosixFilePermissions(file, permissions)
            }
            change.puts << new Put(index, write.before, write.content == null ? null : sha256(write.content), write.path)
        }
        force(staged)
        force(backups)
        force(after)
        force(directory)
    }

    // the journal, written whole and renamed into its place, and on disk before a file of the
    // project is replaced
    private static void record(Path journal, Journal change, boolean complete) throws IOException {
        StringBuilder text = new StringBuilder(HEADER).append('\n')
        // the path of a project can have any character, also one that ends a line
        text.append("project ${Base64.getUrlEncoder().withoutPadding().encodeToString(change.project.getBytes(StandardCharsets.UTF_8))}\n")
        text.append("id ${change.name}\n")
        if (complete) {
            for (Put put : change.puts) {
                text.append("put ${put.index} ${put.before ?: '-'} ${put.after ?: '-'} ${put.path}\n")
            }
            for (String made : change.made) {
                text.append("dir ${made}\n")
            }
            text.append(END).append('\n')
        } else {
            text.append(STAGING).append('\n')
        }
        Path unfinished = journal.resolveSibling(JOURNAL + '.tmp')
        Files.deleteIfExists(unfinished)
        store(unfinished, text.toString().getBytes(StandardCharsets.UTF_8))
        move(unfinished, journal)
        force(journal.getParent())
    }

    private static Journal read(Path journal, Path project) throws IOException {
        if (Files.size(journal) > MAX_JOURNAL) {
            throw new IllegalArgumentException('too large')
        }
        byte[] bytes
        try (InputStream input = Files.newInputStream(journal, LinkOption.NOFOLLOW_LINKS)) {
            bytes = input.readNBytes(MAX_JOURNAL + 1)
        }
        List<String> lines = new String(bytes, StandardCharsets.UTF_8).split('\n', -1).toList()
        if (bytes.length > MAX_JOURNAL || lines.size() < 5 || lines.first() != HEADER || !lines.last().isEmpty()) {
            throw new IllegalArgumentException('not a journal')
        }
        Matcher of = PROJECT.matcher(lines[1])
        Matcher name = NAME.matcher(lines[2])
        String last = lines[lines.size() - 2]
        // the journal is found by the path of the project and says it again: a journal that was put
        // where another project's is looked for is not that project's
        if (!of.matches() || !name.matches() || !(last in [STAGING, END])) {
            throw new IllegalArgumentException('not a journal')
        }
        String path = new String(Base64.getUrlDecoder().decode(of.group(1)), StandardCharsets.UTF_8)
        if (path != project.toRealPath().toString()) {
            throw new IllegalArgumentException('not a journal of this project')
        }
        Journal change = new Journal(path, name.group(1))
        change.staging = last == STAGING
        Set<Integer> seen = new HashSet<>()
        for (String line : lines.subList(3, lines.size() - 2)) {
            Matcher put = PUT.matcher(line)
            Matcher directory = MADE.matcher(line)
            if (!change.staging && put.matches() && isWritable(put.group(4)) && !(put.group(2) == '-' && put.group(3) == '-') && seen.add(Integer.valueOf(put.group(1)))) {
                change.puts << new Put(Integer.parseInt(put.group(1)), put.group(2) == '-' ? null : put.group(2), put.group(3) == '-' ? null : put.group(3), put.group(4))
            } else if (!change.staging && directory.matches() && isWritable(directory.group(1))) {
                change.made.add(directory.group(1))
            } else {
                throw new IllegalArgumentException('not a journal')
            }
        }
        return change
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

    private static String key(String project) {
        return sha256(project.getBytes(StandardCharsets.UTF_8))
    }

    /**
     * @return why a home directory cannot keep the records of changes, or {@code null} for one that
     * can: it is an absolute path of a directory that is there, that is not the project or in it, also
     * not by way of a link, and, where the file system says whose a directory is, that of this user. A home directory that is none, as the {@code ?} a user
     * without an entry has in a container, would be looked for in the current directory, which is
     * the project
     */
    static String unusable(Path home, Path project) {
        try {
            if (home == null || !home.isAbsolute()) {
                return 'it is not an absolute path'
            }
            if (!Files.isDirectory(home)) {
                return 'there is no such directory'
            }
            if (home.getFileSystem().supportedFileAttributeViews().contains('posix') && Files.getOwner(home.toRealPath()) != self()) {
                return 'the directory is not that of the user who runs the command'
            }
            // The journal says which files a change replaces and removes. It is believed because a
            // project cannot bring it along, and that is so only where it is kept outside the project
            if (home.toRealPath().startsWith(project.toRealPath())) {
                return 'it is the directory of the project, or a directory in it, or a link to one, and the record of a change must not be where a project could bring one along'
            }
            return null
        } catch (IOException e) {
            return "it cannot be read (${e.getClass().getSimpleName()})"
        }
    }

    private static Path usable(Path home, Path project) {
        String reason = unusable(home, project)
        if (reason != null) {
            throw new ProjectChangeException('Adding a feature needs a home directory to keep the record of the change in, and the one the system property user.home ' +
                    "names cannot be used: ${reason}. Nothing of the project was changed")
        }
        return home
    }

    private static UserPrincipal whoRuns
    private static volatile Predicate<Path> ownedWithoutModes

    /**
     * Gives what tells whether a directory is the user's own where the file system has no POSIX
     * attributes, as on Windows: there an owner is no user id, what an elevated administrator creates
     * is owned by a group, and whose a file of that group is takes the access token of the process to
     * say, which the command line reads and this library does not. The directories the records of
     * changes are kept in are held to it; the home directory itself is not, which Windows gives to
     * the system or to the administrators as often as to the user.
     *
     * @param owned given a directory, whether the user who runs the command owns it; {@code null}
     * for no such check, which is how it is until a caller gives one
     */
    static void setOwnedWithoutModes(Predicate<Path> owned) {
        ownedWithoutModes = owned
    }

    // whose the files are that this program makes
    private static synchronized UserPrincipal self() throws IOException {
        if (whoRuns == null) {
            Path probe = Files.createTempFile('grails-forge', '.owner')
            try {
                whoRuns = Files.getOwner(probe, LinkOption.NOFOLLOW_LINKS)
            } finally {
                Files.delete(probe)
            }
        }
        return whoRuns
    }

    /**
     * Where the journal of a project is: {@code .grails/forge/transactions/<digest of the path of the
     * project>/journal} in the home directory. Every directory on the way that is there has to be a
     * directory and not a link and, where the file system says so, that of this user and writable by
     * nobody else; one that is made is made for the user alone.
     *
     * @param make whether the directories that are not there are made
     * @return the journal, or {@code null} when a directory is not there and none is made
     * @throws ProjectChangeException for a directory that is not what it has to be
     */
    private static Path journal(Path project, Path home, boolean make) {
        try {
            boolean posix = home.getFileSystem().supportedFileAttributeViews().contains('posix')
            FileAttribute<?>[] ofDirectory = posix ? [PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString('rwx------'))] as FileAttribute<?>[] :
                    new FileAttribute<?>[0]
            Path directory = home
            for (String step : ['.grails', 'forge', 'transactions', key(project.toRealPath().toString())]) {
                directory = directory.resolve(step)
                if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
                    if (!make) {
                        return null
                    }
                    try {
                        Files.createDirectory(directory, ofDirectory)
                    } catch (FileAlreadyExistsException ignored) {
                        // made by a command that runs beside this one
                    }
                }
                if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("${home.relativize(directory).join('/')} is not a directory")
                }
                if (posix) {
                    Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(directory, LinkOption.NOFOLLOW_LINKS)
                    if (Files.getOwner(directory, LinkOption.NOFOLLOW_LINKS) != self()) {
                        throw new IOException("${home.relativize(directory).join('/')} is not a directory of the user who runs the command")
                    }
                    if (permissions.contains(PosixFilePermission.GROUP_WRITE) || permissions.contains(PosixFilePermission.OTHERS_WRITE)) {
                        throw new IOException("${home.relativize(directory).join('/')} can be written by others than the user")
                    }
                } else {
                    // the owner alone: there are no modes, and no access control list is read
                    Predicate<Path> owned = ownedWithoutModes
                    if (owned != null && !owned.test(directory)) {
                        throw new IOException("${home.relativize(directory).join('/')} is not a directory of the user who runs the command")
                    }
                }
            }
            return directory.resolve(JOURNAL)
        } catch (IOException e) {
            throw new ProjectChangeException('The directory the records of changes are kept in, .grails/forge/transactions in the home directory of the user, cannot be used: ' +
                    "${e.getMessage()?.startsWith('.grails') ? e.getMessage() : e.getClass().getSimpleName()}. Nothing of the project was changed", e)
        }
    }

    private static <T> T locked(Path project, Supplier<T> work) {
        Path directory = project
        for (String step : DIRECTORY.split('/')) {
            directory = directory.resolve(step)
            if (step != ProjectState.DIRECTORY) {
                try {
                    Files.createDirectory(directory)
                } catch (FileAlreadyExistsException ignored) {
                    // made before, or by a command that runs beside this one
                } catch (IOException e) {
                    throw new ProjectChangeException("The directory ${DIRECTORY} could not be created (${e.getClass().getSimpleName()}); nothing of the project was changed", e)
                }
            }
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                throw new ProjectChangeException("${DIRECTORY} is not a directory of the project; nothing of the project was changed")
            }
        }
        Path lockFile = directory.resolve(LOCK)
        // what is opened is a file: a pipe of the name would keep the command waiting for its other end
        if (Files.exists(lockFile, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(lockFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new ProjectChangeException("The lock of the project, ${DIRECTORY}/${LOCK}, is not a file; remove it. Nothing of the project was changed")
        }
        FileChannel channel
        try {
            channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)
        } catch (IOException e) {
            throw new ProjectChangeException("The lock of the project, ${DIRECTORY}/${LOCK}, could not be opened (${e.getClass().getSimpleName()}); nothing of the project was changed", e)
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

    // the names in a directory reach the disk with the directory, where a directory can be opened
    private static void force(Path directory) {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true)
        } catch (IOException ignored) {
            // not on every platform
        }
    }

    // a file of the project that another program has open for a moment is tried again, see FilesInUse
    private static void move(Path from, Path to) throws IOException {
        FilesInUse.attempt(to) {
            try {
                Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(from, to, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    private static void remove(Path file) throws IOException {
        FilesInUse.attempt(file) {
            Files.delete(file)
        }
    }

    // the end of a change: its journal first, with which there is no change any more, then its files
    private static void finish(Path directory, Path journal) {
        try {
            Files.deleteIfExists(journal)
            force(journal.getParent())
        } catch (IOException e) {
            throw new ProjectChangeException("The record of the change could not be removed (${e.getClass().getSimpleName()}); ${removal(journal)}", e)
        }
        discard(directory)
        try {
            Files.deleteIfExists(journal.getParent())
        } catch (IOException ignored) {
            // an empty directory that stays is found again
        }
    }

    // a change that touched nothing of the project: its files first, then the record of it
    private static void abandon(Path directory, Path journal) {
        discard(directory)
        try {
            Files.deleteIfExists(journal.resolveSibling(JOURNAL + '.tmp'))
            Files.deleteIfExists(journal)
            Files.deleteIfExists(journal.getParent())
        } catch (IOException e) {
            throw new ProjectChangeException("The record of a change that changed nothing could not be removed (${e.getClass().getSimpleName()}); ${removal(journal)}", e)
        }
    }

    // only what is in the directory is removed: a link in it is, and what it leads to is not
    private static void discard(Path directory) {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return
        }
        try {
            Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {

                @Override
                FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    Files.delete(file)
                    return FileVisitResult.CONTINUE
                }

                @Override
                FileVisitResult postVisitDirectory(Path visited, IOException failure) throws IOException {
                    if (failure != null) {
                        throw failure
                    }
                    Files.delete(visited)
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (IOException e) {
            throw new ProjectChangeException("What a change of the project left in ${DIRECTORY} could not be removed (${e.getClass().getSimpleName()}); remove " +
                    "the directory ${directory.getFileName()} there", e)
        }
    }

    @CompileStatic
    private static final class Journal {

        final String project
        final String name
        final List<Put> puts = []
        final Set<String> made = new TreeSet<>()
        boolean staging

        Journal(String project, String name) {
            this.project = project
            this.name = name
        }
    }

    @CompileStatic
    private static final class Put {

        final int index
        // the digest of the file as it was, or null for a file the change adds
        final String before
        // the digest of the file as the change makes it, or null for a file the change removes
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
