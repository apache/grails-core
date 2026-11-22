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
package org.apache.grails.forge.cli.artefact

import groovy.transform.CompileStatic
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.nio.charset.StandardCharsets
import java.nio.file.DirectoryStream
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.SecureDirectoryStream
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.security.SecureRandom
import java.time.Instant
import org.apache.grails.forge.core.change.FilesInUse
import org.grails.forge.feature.FeatureSelectionException

/**
 * The place of one file in a project, with the directory it is in held open.
 *
 * <p>What {@link ProjectOutputHandler#refusal} finds out about a path is true when it looks, and a
 * directory can be made a link afterwards. So the directories from the project down to the file are
 * opened one from the other, none of them through a link, and the file is then written, moved aside
 * and put back by its name in the directory that is open. The Java runtime has that way on Linux; on
 * macOS and on Windows it has not, and there every directory on the way is looked at again right
 * before each step, to be the very directory that was opened, which leaves the one step itself. A file is written under another
 * name first and takes its place in one step, which replaces a file or a link that is there and
 * never writes through one.</p>
 *
 * <p>The directories that are missing are made by their paths, since there is no other way: a
 * directory that was changed at that moment gets an empty directory made where it leads, and the
 * place is then not opened.</p>
 *
 * @since 9.0
 */
@CompileStatic
class ProjectPlace implements Closeable {

    private static final SecureRandom RANDOM = new SecureRandom()
    private static final String ASIDE = 'aside'
    private static final String NEW = 'new'
    private static final Set<? extends OpenOption> NEW_FILE = [StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS] as Set

    /** The path of the file in the project */
    final String path
    private final String name
    private final List<Level> levels = []
    // whether there was something under the name of the file when the place was opened, and whether it is aside now
    private boolean original
    private boolean moved
    private String aside

    protected ProjectPlace(String path) {
        this.path = path
        this.name = path.substring(path.lastIndexOf('/') + 1)
    }

    /**
     * What the name of a file moved aside starts with, and with {@code new} the name a file is first
     * written under: the name of the file itself is in it, so that what a command left that did not
     * end is known for what it is. A long name is cut, so that the whole is a name a file system takes.
     *
     * @param name the name of a file
     * @param kind {@code aside} or {@code new}
     */
    static String besideName(String name, String kind) {
        String kept = name
        while (kept.getBytes(StandardCharsets.UTF_8).length > ProjectOutputHandler.MAX_NAME_BYTES - 40) {
            kept = kept.substring(0, kept.offsetByCodePoints(0, kept.codePointCount(0, kept.length()) - 1))
        }
        return ".${kept}.grails-${kind}-"
    }

    /**
     * @param directory a directory
     * @param name the name of a file in it
     * @return the names of what a command that did not end left there of what the file was before, moved aside
     */
    static List<String> leftAside(Path directory, String name) {
        String[] names = directory.toFile().list()
        String start = besideName(name, ASIDE)
        return names == null ? [] as List<String> : names.findAll { String each -> each.startsWith(start) }.toList()
    }

    // a directory on the way, which is open, and whether it was made for this place
    private static final class Level {

        final Path directory
        final DirectoryStream<Path> open
        final boolean made
        // what the file system knows the directory by, where it says: another directory of the same path has another
        final Object key

        Level(Path directory, DirectoryStream<Path> open, boolean made) throws IOException {
            this.directory = directory
            this.open = open
            this.made = made
            this.key = Files.readAttributes(directory, BasicFileAttributes, LinkOption.NOFOLLOW_LINKS).fileKey()
        }

        boolean isWhatItWas() {
            try {
                BasicFileAttributes now = Files.readAttributes(directory, BasicFileAttributes, LinkOption.NOFOLLOW_LINKS)
                return now.isDirectory() && now.fileKey() == key
            } catch (IOException ignored) {
                return false
            }
        }

        SecureDirectoryStream<Path> getSecure() {
            return open instanceof SecureDirectoryStream ? (SecureDirectoryStream<Path>) open : null
        }
    }

    /**
     * Opens the directory a file of a project is in, and makes the directories that are missing.
     *
     * @param project the directory of the project
     * @param path the path of the file in the project, with {@code /} between its steps
     * @throws ProjectOutputHandler.Refused when the file cannot be written there, see {@link ProjectOutputHandler#refusal}
     */
    static ProjectPlace open(File project, String path) throws IOException {
        return new ProjectPlace(path).opened(project)
    }

    /**
     * Opens this place, see {@link #open(File, String)}.
     */
    protected ProjectPlace opened(File project) throws IOException {
        return opened(project, true)
    }

    /**
     * @param leftAside whether a file that a command which did not end moved aside keeps this place
     * from being opened, see {@link ProjectOutputHandler#refusal(File, String, boolean)}
     */
    protected ProjectPlace opened(File project, boolean leftAside) throws IOException {
        String refusal = ProjectOutputHandler.refusal(project, path, leftAside)
        if (refusal != null) {
            throw new ProjectOutputHandler.Refused(refusal)
        }
        ProjectPlace place = this
        try {
            Path directory = project.toPath().toRealPath()
            place.levels.add(new Level(directory, Files.newDirectoryStream(directory), false))
            List<String> steps = path.split('/').toList()
            for (int i = 0; i < steps.size() - 1; i++) {
                Level above = place.levels.last()
                Path next = above.directory.resolve(steps[i])
                boolean made = false
                if (!Files.exists(next, LinkOption.NOFOLLOW_LINKS)) {
                    try {
                        Files.createDirectory(next)
                        made = true
                    } catch (FileAlreadyExistsException ignored) {
                        // made by another in the meantime, and opened like one that was there
                    }
                }
                String said = FeatureSelectionException.shown(steps.subList(0, i + 1).join('/'))
                DirectoryStream<Path> open
                try {
                    if (above.secure != null) {
                        open = above.secure.newDirectoryStream(next.getFileName(), LinkOption.NOFOLLOW_LINKS)
                    } else {
                        if (!Files.isDirectory(next, LinkOption.NOFOLLOW_LINKS)) {
                            throw new ProjectOutputHandler.Refused("${said} is not the directory it was when the command looked")
                        }
                        open = Files.newDirectoryStream(next)
                    }
                    Level level = new Level(next, open, made)
                    if (!level.isWhatItWas()) {
                        open.close()
                        throw new ProjectOutputHandler.Refused("${said} is not the directory it was when the command looked")
                    }
                    place.levels.add(level)
                } catch (ProjectOutputHandler.Refused e) {
                    throw e
                } catch (IOException e) {
                    throw new ProjectOutputHandler.Refused("${said} is not the directory it was when the command looked")
                }
            }
            original = exists(name)
            String[] names = levels.last().directory.toFile().list()
            String unfinished = besideName(name, NEW)
            for (String each : (names == null ? [] as List<String> : names.toList())) {
                if (each.startsWith(unfinished)) {
                    try {
                        delete(each)
                    } catch (IOException ignored) {
                        // it is in nobody's way
                    }
                }
            }
            return place
        } catch (IOException | RuntimeException e) {
            place.removeMade()
            place.close()
            throw e
        }
    }

    /**
     * @return whether there is something under the name of the file, a link included
     */
    boolean isThere() throws IOException {
        return exists(name)
    }

    /**
     * Moves what is there under the name of the file aside, under another name in the same
     * directory: it stays what it is, with its content, its mode and its other names.
     */
    void moveAside() throws IOException {
        if (original && !moved) {
            String other = besideName(name, ASIDE) + Long.toHexString(RANDOM.nextLong())
            move(name, other)
            aside = other
            moved = true
        }
    }

    /**
     * Writes the file: under another name first, which then takes the name of the file in one step.
     *
     * @param content the content of the file
     * @param modified when the file is to have been modified, or null for now
     */
    void put(byte[] content, Instant modified) throws IOException {
        String fresh = besideName(name, NEW) + Long.toHexString(RANDOM.nextLong())
        Level leaf = levels.last()
        try {
            SeekableByteChannel channel = leaf.secure != null ? leaf.secure.newByteChannel(Path.of(fresh), NEW_FILE) :
                    Files.newByteChannel(checked(leaf).resolve(fresh), NEW_FILE)
            try {
                ByteBuffer buffer = ByteBuffer.wrap(content)
                while (buffer.hasRemaining()) {
                    channel.write(buffer)
                }
            } finally {
                channel.close()
            }
            if (modified != null) {
                FileTime time = FileTime.from(modified)
                BasicFileAttributeView view = leaf.secure != null ? leaf.secure.getFileAttributeView(Path.of(fresh), BasicFileAttributeView, LinkOption.NOFOLLOW_LINKS) :
                        Files.getFileAttributeView(checked(leaf).resolve(fresh), BasicFileAttributeView, LinkOption.NOFOLLOW_LINKS)
                view.setTimes(time, null, null)
            }
            move(fresh, name)
        } catch (IOException | RuntimeException e) {
            try {
                delete(fresh)
            } catch (IOException ignored) {
                // it was not made, or took the name of the file
            }
            throw e
        }
    }

    /**
     * Forgets what was moved aside: the file that was written stays.
     */
    void keep() {
        if (moved) {
            try {
                delete(aside)
            } catch (IOException ignored) {
                // a file of another name is left, which is not the file of the command
            }
            moved = false
            original = false
        }
    }

    /**
     * Takes back what was done at this place: what was moved aside has its name again, a file that
     * was written where there was none is removed, and the directories that were made are removed.
     * What was there and was not moved aside is not touched: nothing was written in its place.
     *
     * @return nothing when the place is what it was, and otherwise what it is now, in a sentence
     */
    String restore() {
        String left = null
        try {
            if (moved) {
                // takes the name in one step, whatever was written under it
                move(aside, name)
                moved = false
            } else if (!original) {
                try {
                    delete(name)
                } catch (NoSuchFileException ignored) {
                    // nothing was written
                }
            }
        } catch (ProjectOutputHandler.Refused ignored) {
            left = "${FeatureSelectionException.shown(path)} was not looked at again, because a directory it is in is not the directory it was" +
                    (moved ? ": what it was is there under the name ${FeatureSelectionException.shown(aside)}" : '')
        } catch (IOException e) {
            String shown = FeatureSelectionException.shown(path)
            left = moved ? "${shown} is not what it was: what it was is beside it under the name ${FeatureSelectionException.shown(aside)}" :
                    "${shown} could not be removed and is what the command wrote of it"
        }
        if (left == null) {
            removeMade()
        }
        return left
    }

    @Override
    void close() {
        levels.reverse().each { Level level ->
            try {
                level.open.close()
            } catch (IOException ignored) {
                // nothing depends on it
            }
        }
    }

    // the deepest first, and only those that are empty again
    private void removeMade() {
        for (int i = levels.size() - 1; i > 0 && levels[i].made; i--) {
            Level above = levels[i - 1]
            try {
                levels[i].open.close()
                if (above.secure != null) {
                    above.secure.deleteDirectory(levels[i].directory.getFileName())
                } else {
                    Files.delete(levels[i].directory)
                }
            } catch (IOException ignored) {
                return
            }
        }
    }

    private boolean exists(String entry) throws IOException {
        Level leaf = levels.last()
        if (leaf.secure == null) {
            return Files.exists(checked(leaf).resolve(entry), LinkOption.NOFOLLOW_LINKS)
        }
        try {
            leaf.secure.getFileAttributeView(Path.of(entry), BasicFileAttributeView, LinkOption.NOFOLLOW_LINKS).readAttributes()
            return true
        } catch (NoSuchFileException ignored) {
            return false
        }
    }

    /**
     * Gives an entry of the directory of the file another name there, in one step.
     */
    protected void move(String from, String to) throws IOException {
        Level leaf = levels.last()
        if (leaf.secure != null) {
            leaf.secure.move(Path.of(from), leaf.secure, Path.of(to))
        } else {
            // where the system has no directory that is held open, as on Windows, a file that an editor or
            // a scanner has open for a moment is tried again
            Path directory = checked(leaf)
            FilesInUse.attempt(directory.resolve(to)) {
                Files.move(checked(leaf).resolve(from), checked(leaf).resolve(to), StandardCopyOption.ATOMIC_MOVE)
            }
        }
    }

    private void delete(String entry) throws IOException {
        Level leaf = levels.last()
        if (leaf.secure != null) {
            leaf.secure.deleteFile(Path.of(entry))
        } else {
            FilesInUse.attempt(checked(leaf).resolve(entry)) {
                Files.delete(checked(leaf).resolve(entry))
            }
        }
    }

    // without a directory that is held open, the way to it is looked at again right before it is used
    private Path checked(Level leaf) throws IOException {
        for (int i = 1; i < levels.size(); i++) {
            if (!levels[i].isWhatItWas()) {
                throw new ProjectOutputHandler.Refused('a directory it is in is not the directory it was when the command looked')
            }
        }
        return leaf.directory
    }
}
