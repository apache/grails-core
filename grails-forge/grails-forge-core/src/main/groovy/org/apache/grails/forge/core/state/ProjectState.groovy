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
package org.apache.grails.forge.core.state

import groovy.transform.CompileStatic
import org.apache.grails.forge.core.state.BaselineIndex.BaselineFile
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest

/**
 * The state of a generated project, which is kept in the project under {@code .grails} and committed
 * with it: the manifest (what was asked for), the lock (what that came to) and the baseline (every
 * file the generator wrote, as it wrote it). The three are read as a set. The directory of a project
 * that was cloned is not trusted: the files are bounded in size, read without following a link, and
 * refused when one of them is missing, is of a format this version does not read, or is not the one
 * the others were written with.
 *
 * @since 9.0
 */
@CompileStatic
final class ProjectState {

    /** The directory of the state in a project */
    public static final String DIRECTORY = '.grails'

    /** The manifest: what was asked for */
    public static final String MANIFEST = '.grails/project.yml'

    /** The lock: what the manifest came to */
    public static final String LOCK = '.grails/forge.lock.yml'

    /** The index of the baseline: the files the generator wrote, with their digests */
    public static final String BASELINE_INDEX = '.grails/baseline/index.yml'

    /** The directory that holds the content of the files of the baseline, each under its own path */
    public static final String BASELINE_FILES = '.grails/baseline/files'

    /**
     * Tells Git to keep the bytes of the state as they are: a clone that converts line endings would
     * otherwise have a baseline that is not the one the index has the digests of. It is written with
     * the state and is not needed to read it.
     */
    public static final String ATTRIBUTES = '.grails/.gitattributes'

    /** The directory of the baseline, the index and the content */
    public static final String BASELINE = '.grails/baseline'

    /** The most bytes the manifest can have */
    public static final int MAX_MANIFEST = 256 * 1024

    /** The most bytes the lock can have */
    public static final int MAX_LOCK = 1024 * 1024

    /** The most bytes the index of the baseline can have */
    public static final int MAX_INDEX = 8 * 1024 * 1024

    /** The most bytes one file of the baseline can have */
    public static final long MAX_BASELINE_FILE = 16L * 1024 * 1024

    /** The most bytes the files of the baseline can have together */
    public static final long MAX_BASELINE = 256L * 1024 * 1024

    final ProjectManifest manifest
    final ProjectLock lock
    final BaselineIndex baseline

    private ProjectState(ProjectManifest manifest, ProjectLock lock, BaselineIndex baseline) {
        this.manifest = manifest
        this.lock = lock
        this.baseline = baseline
    }

    /**
     * @param path the path of a file of a project, with {@code /} between its steps
     * @return whether the file is in the directory of the state, also where the case of letters is
     * ignored
     */
    static boolean isStatePath(String path) {
        String first = path.split('/').find { String step -> !step.isEmpty() && step != '.' }
        return first != null && BaselineIndex.folded(first) == DIRECTORY
    }

    /**
     * @param path the path of a file of a project, with {@code /} between its steps
     * @return whether the file is of the baseline, which repeats the files of the application
     */
    static boolean isBaselinePath(String path) {
        List<String> steps = path.split('/').findAll { String step -> !step.isEmpty() && step != '.' }
        return steps.size() > 1 && BaselineIndex.folded(steps[0] + '/' + steps[1]) == BASELINE
    }

    /**
     * @param projectDirectory the directory of a project
     * @return whether there is a manifest in it, read or not
     */
    static boolean exists(Path projectDirectory) {
        return Files.exists(projectDirectory.resolve(MANIFEST), LinkOption.NOFOLLOW_LINKS)
    }

    /**
     * Reads the manifest, the lock and the index of the baseline of a project and checks that they
     * belong together. The content of the baseline is not read: see {@link #verifyBaseline(Path)}.
     *
     * @param projectDirectory the directory of a project
     * @throws ProjectStateException with what is wrong, in one line
     */
    static ProjectState read(Path projectDirectory) {
        List<String> files = [MANIFEST, LOCK, BASELINE_INDEX]
        List<String> missing = files.findAll { String file -> !Files.exists(projectDirectory.resolve(file), LinkOption.NOFOLLOW_LINKS) }
        if (missing.size() == files.size()) {
            throw new ProjectStateException("There is no state of a generated project here: ${MANIFEST} is missing")
        }
        if (missing) {
            throw new ProjectStateException("The state of the project is not complete: ${missing.join(' and ')} ${missing.size() == 1 ? 'is' : 'are'} missing")
        }
        try {
            byte[] manifestBytes = bytes(projectDirectory, MANIFEST, MAX_MANIFEST)
            byte[] lockBytes = bytes(projectDirectory, LOCK, MAX_LOCK)
            byte[] indexBytes = bytes(projectDirectory, BASELINE_INDEX, MAX_INDEX)
            ProjectManifest manifest = ProjectManifest.read(manifestBytes)
            ProjectLock lock = ProjectLock.read(lockBytes)
            if (lock.manifestSha256 != StateYaml.sha256(manifestBytes) || lock.applicationType != manifest.applicationType ||
                    lock.grailsVersion != manifest.grailsVersion) {
                throw new ProjectStateException("${LOCK} does not belong to ${MANIFEST}: one of them was changed without the other")
            }
            BaselineIndex baseline = BaselineIndex.read(indexBytes)
            if (lock.baselineIndexSha256 != StateYaml.sha256(indexBytes)) {
                throw new ProjectStateException("${BASELINE_INDEX} is not the baseline ${LOCK} was written with")
            }
            return new ProjectState(manifest, lock, baseline)
        } catch (IOException e) {
            throw new ProjectStateException("The state of the project cannot be read: ${e.class.simpleName}")
        }
    }

    /**
     * Checks the content of the baseline against its index: every file of the index is there with the
     * content the index names, and nothing else is.
     *
     * @param projectDirectory the directory of the project this state was read from
     * @throws ProjectStateException with the first file that is not as the index says, in one line
     */
    void verifyBaseline(Path projectDirectory) {
        try {
            Path root = directory(projectDirectory, BASELINE_FILES)
            long total = 0
            Set<String> listed = new HashSet<>()
            for (BaselineFile file : baseline.files) {
                listed << file.path
                String shown = "${BASELINE_FILES}/${StateYaml.shown(file.path, StateYaml.MAX_ECHO)}"
                Path path = root
                List<String> steps = file.path.split('/').toList()
                for (String step : steps.subList(0, steps.size() - 1)) {
                    path = path.resolve(step)
                    if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                        throw new ProjectStateException("${shown} is missing from the baseline")
                    }
                }
                path = path.resolve(steps.last())
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw new ProjectStateException("${shown} is missing from the baseline")
                }
                long size = Files.size(path)
                total += size
                if (size > MAX_BASELINE_FILE || total > MAX_BASELINE) {
                    throw new ProjectStateException("${shown} is larger than a baseline can hold")
                }
                if (digest(path) != file.sha256) {
                    throw new ProjectStateException("${shown} is not what the generator wrote: its content does not have the digest of the index")
                }
            }
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {

                private int seen

                @Override
                FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    String path = root.relativize(file).join('/')
                    if (++seen > BaselineIndex.MAX_FILES || !listed.contains(path)) {
                        throw new ProjectStateException("${BASELINE_FILES}/${StateYaml.shown(path, StateYaml.MAX_ECHO)} is not a file of the baseline index")
                    }
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (IOException e) {
            throw new ProjectStateException("The baseline of the project cannot be read: ${e.class.simpleName}")
        }
    }

    private static String digest(Path path) {
        MessageDigest digest = MessageDigest.getInstance('SHA-256')
        byte[] buffer = new byte[8192]
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            for (int read = input.read(buffer); read >= 0; read = input.read(buffer)) {
                digest.update(buffer, 0, read)
            }
        }
        return HexFormat.of().formatHex(digest.digest())
    }

    // every directory on the way is a directory of the project, not a link out of it
    private static Path directory(Path projectDirectory, String relative) {
        Path path = projectDirectory
        for (String step : relative.split('/')) {
            path = path.resolve(step)
            if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new ProjectStateException("${relative} is not a directory of the project")
            }
        }
        return path
    }

    private static byte[] bytes(Path projectDirectory, String relative, int most) {
        int slash = relative.lastIndexOf('/')
        Path path = directory(projectDirectory, relative.substring(0, slash)).resolve(relative.substring(slash + 1))
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new ProjectStateException("${relative} is not a file")
        }
        if (Files.size(path) > most) {
            throw new ProjectStateException("${relative} is larger than it can be: it has ${Files.size(path)} bytes, and can have ${most}")
        }
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            // the size was asked for before the file was opened, and the file can have grown since
            byte[] bytes = input.readNBytes(most + 1)
            if (bytes.length > most) {
                throw new ProjectStateException("${relative} is larger than it can be: it can have ${most} bytes")
            }
            return bytes
        }
    }
}
