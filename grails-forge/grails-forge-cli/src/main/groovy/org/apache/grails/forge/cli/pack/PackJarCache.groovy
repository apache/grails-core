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
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.DirectoryStream
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.regex.Pattern

/**
 * The jars of plugins the command line fetched for their feature packs, kept in
 * {@code .grails/forge/packs} of the home of the user so that a jar that is asked for by its SHA-256 is
 * not fetched again, and so that {@code --offline} can use it.
 *
 * <p>A jar is kept in a file that is named by its SHA-256, and is read only by that name: its bytes
 * are hashed again each time it is read, and a file whose bytes do not have the digest of its name is
 * removed. Beside the jars, one small file for each repository and coordinates a jar was fetched from
 * says which jar that was: it is what tells where a jar of the directory was found, and it is how a
 * jar is found without its digest when nothing is asked of the network.</p>
 *
 * <p>The directory is that of the copies of the feature catalogs in every other way: nothing is read
 * from it or written to it unless it and the two above it are directories of the user who runs the
 * command that nobody else can write to and that are reached without a link; a file is written
 * beside its place and then moved into it; and what is in it is a copy, which can be removed at any
 * time.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class PackJarCache {

    /** Where the jars are kept, under the home of the user */
    public static final String LOCATION = '.grails/forge/packs'

    private static final Pattern SHA256 = Pattern.compile(/[0-9a-f]{64}/)
    private static final Pattern FILE_NAME = Pattern.compile(/[A-Za-z0-9][A-Za-z0-9_.\-]{0,500}\.jar/)
    private static final Set<String> CHECKSUMS = ['sha256', 'sha1'] as Set<String>
    private static final String JAR = '.jar'
    private static final String LAST = '.last'
    private static final String TEMPORARY = '.tmp'
    private static final String PREFIX = 'pack'
    private static final String POSIX = 'posix'
    private static final int MAX_RECORD_BYTES = 8 * 1024
    private static final int MAX_SWEPT = 100
    private static final Duration LEFT_BEHIND = Duration.ofHours(1)

    private final Path home
    private final List<String> warnings = []
    private boolean lookedAt
    private Path directory
    private String refused

    /**
     * @param home the home of the user, under which the jars are kept
     */
    PackJarCache(Path home) {
        this.home = home
    }

    /**
     * @return the directory of the jars as a message names it, whether it is used or not
     */
    String getShownDirectory() {
        return shown(home.resolve('.grails').resolve('forge').resolve('packs'))
    }

    /**
     * @return why no jar is read from or kept in the directory, or {@code null} when it is used
     */
    String getRefused() {
        directory()
        return refused
    }

    /**
     * @return what there is to say about the copies that were read, each once
     */
    List<String> takeWarnings() {
        List<String> taken = new ArrayList<>(warnings)
        warnings.clear()
        return taken
    }

    /**
     * @param sha256 the SHA-256 of a jar, in lowercase hexadecimal
     * @return the file the jar of that digest is kept in, as a message names it
     */
    String shownFile(String sha256) {
        return shown(home.resolve('.grails').resolve('forge').resolve('packs').resolve(sha256 + JAR))
    }

    /**
     * Reads the jar that has a digest. The bytes are hashed, and a file whose bytes do not have the
     * digest it is named by is removed and is no copy.
     *
     * @param sha256 the SHA-256 of the jar, in lowercase hexadecimal
     * @param limit the most bytes the jar can have
     * @return the bytes of the jar, or {@code null} when no jar of that digest is kept
     */
    byte[] jar(String sha256, long limit) {
        Path kept = fileOf(sha256)
        if (kept == null) {
            return null
        }
        try {
            if (!Files.isRegularFile(kept, LinkOption.NOFOLLOW_LINKS)) {
                return null
            }
            byte[] bytes = Files.size(kept) > limit ? null : Files.readAllBytes(kept)
            if (bytes != null && HexFormat.of().formatHex(MessageDigest.getInstance('SHA-256').digest(bytes)) == sha256) {
                return bytes
            }
            bytes = null
            discard(kept)
            warnings << "The copy of a jar that is kept as ${shown(kept)} did not have the SHA-256 it is named by, and was removed".toString()
        } catch (IOException | RuntimeException | OutOfMemoryError ignored) {
            // a copy that cannot be read is no copy
        }
        return null
    }

    /**
     * @param repository the address of a repository
     * @param coordinates the coordinates of a plugin, as {@code group:artifact:version}
     * @return which jar was last fetched from that repository for those coordinates, or {@code null}
     * when none was, or what says so cannot be read
     */
    Fetched last(URI repository, String coordinates) {
        Path record = recordOf(repository, coordinates)
        if (record == null) {
            return null
        }
        try {
            if (!Files.isRegularFile(record, LinkOption.NOFOLLOW_LINKS) || Files.size(record) > MAX_RECORD_BYTES) {
                return null
            }
            Map<String, String> said = new HashMap<>()
            for (String line : new String(Files.readAllBytes(record), StandardCharsets.UTF_8).split('\n')) {
                int at = line.indexOf('=')
                if (at > 0) {
                    said.putIfAbsent(line.substring(0, at), line.substring(at + 1))
                }
            }
            String sha256 = said.get('sha256') ?: ''
            String file = said.get('file') ?: ''
            String checkedAgainst = said.get('checkedAgainst') ?: null
            // a record is of the repository and the coordinates it says it is of, and of nothing else
            if (said.get('repository') != repository.toString() || said.get('coordinates') != coordinates || !SHA256.matcher(sha256).matches() ||
                    !FILE_NAME.matcher(file).matches() || (checkedAgainst != null && !CHECKSUMS.contains(checkedAgainst))) {
                return null
            }
            return new Fetched(sha256, file, checkedAgainst)
        } catch (IOException | RuntimeException ignored) {
            return null
        }
    }

    /**
     * Keeps a jar that was fetched, and that it was fetched from a repository for coordinates. That
     * it could not be kept changes nothing of the command.
     *
     * @param bytes holds the bytes of the jar, from its start
     * @param length how many of them are the jar
     * @param sha256 the SHA-256 of those bytes, in lowercase hexadecimal
     * @param file the name of the file in the repository
     * @param checkedAgainst the kind of checksum of the repository the jar was checked against, or {@code null}
     */
    void keep(byte[] bytes, int length, String sha256, URI repository, String coordinates, String file, String checkedAgainst) {
        Path kept = home.resolve('.grails').resolve('forge').resolve('packs').resolve(sha256 + JAR)
        if (!write(kept, bytes, length)) {
            return
        }
        String record = "repository=${repository}\ncoordinates=${coordinates}\nsha256=${sha256}\nfile=${file}\ncheckedAgainst=${checkedAgainst ?: ''}\n"
        byte[] text = record.getBytes(StandardCharsets.UTF_8)
        write(kept.resolveSibling(nameOf(repository, coordinates)), text, text.length)
    }

    private Path fileOf(String sha256) {
        Path directory = directory()
        return directory == null || !SHA256.matcher(sha256).matches() ? null : directory.resolve(sha256 + JAR)
    }

    private Path recordOf(URI repository, String coordinates) {
        Path directory = directory()
        return directory == null ? null : directory.resolve(nameOf(repository, coordinates))
    }

    private static String nameOf(URI repository, String coordinates) {
        byte[] key = "${repository}\n${coordinates}".toString().getBytes(StandardCharsets.UTF_8)
        return HexFormat.of().formatHex(MessageDigest.getInstance('SHA-256').digest(key)) + LAST
    }

    // the directory, when it and the two above it, as far as they are there, are the user's own
    private Path directory() {
        if (!lookedAt) {
            lookedAt = true
            Path packs = home.resolve('.grails').resolve('forge').resolve('packs')
            Path notOurs = OwnDirectory.notOurs(packs)
            if (notOurs == null) {
                directory = packs
            } else {
                refused = OwnDirectory.refusal(notOurs)
                warnings << "No copy of the jar of a plugin is read or kept: ${refused}".toString()
            }
        }
        return directory
    }

    // a file is written whole or not at all: beside its place first, and then moved into it
    private boolean write(Path file, byte[] bytes, int length) {
        if (directory() == null) {
            return false
        }
        Path written = null
        try {
            boolean posix = FileSystems.default.supportedFileAttributeViews().contains(POSIX)
            FileAttribute<?>[] ofDirectory = posix ? [PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString('rwx------'))] as FileAttribute<?>[] : new FileAttribute<?>[0]
            FileAttribute<?>[] ofFile = posix ? [PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString('rw-------'))] as FileAttribute<?>[] : new FileAttribute<?>[0]
            Files.createDirectories(file.parent, ofDirectory)
            // what was made just now is looked at as what was there before
            if (OwnDirectory.notOurs(file.parent) != null) {
                return false
            }
            sweep(file.parent)
            written = Files.createTempFile(file.parent, PREFIX, TEMPORARY, ofFile)
            try (OutputStream output = Files.newOutputStream(written)) {
                output.write(bytes, 0, length)
            }
            try {
                Files.move(written, file, StandardCopyOption.ATOMIC_MOVE)
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(written, file, StandardCopyOption.REPLACE_EXISTING)
            }
            return true
        } catch (IOException | RuntimeException ignored) {
            discard(written)
            return false
        }
    }

    // what a command that was ended while it wrote a file left behind
    private static void sweep(Path directory) {
        Instant before = Instant.now() - LEFT_BEHIND
        List<Path> leftBehind = []
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, PREFIX + '*' + TEMPORARY)) {
            for (Path file : files) {
                if (leftBehind.size() < MAX_SWEPT && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) &&
                        Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS).toInstant() < before) {
                    leftBehind << file
                }
            }
        }
        for (Path file : leftBehind) {
            discard(file)
        }
    }

    private static void discard(Path file) {
        if (file != null) {
            try {
                Files.deleteIfExists(file)
            } catch (IOException | RuntimeException ignored) {
                // nothing more to do about it
            }
        }
    }

    private static String shown(Path path) {
        String named = path.toString().replaceAll(/\p{Cntrl}/, '?').replace('@|', '@ |')
        return named.length() > 300 ? named.substring(0, 300) + '...' : named
    }

    /**
     * The jar that was last fetched from a repository for coordinates.
     */
    static final class Fetched {

        /** The SHA-256 of the jar, in lowercase hexadecimal */
        final String sha256

        /** The name of the file in the repository */
        final String file

        /** The kind of checksum of the repository the jar was checked against, or {@code null} */
        final String checkedAgainst

        private Fetched(String sha256, String file, String checkedAgainst) {
            this.sha256 = sha256
            this.file = file
            this.checkedAgainst = checkedAgainst
        }
    }
}
