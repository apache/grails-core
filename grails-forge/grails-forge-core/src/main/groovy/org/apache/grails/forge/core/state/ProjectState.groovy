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
import org.apache.grails.forge.core.state.ProjectLock.LockedFeature
import org.apache.grails.forge.core.state.ProjectManifest.RequestedFeature
import java.nio.charset.StandardCharsets
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
 * <p>What binds the three: the lock has the digest of the manifest and the digest of the index of the
 * baseline, and ends with the digest of itself; the options of the lock are those of the manifest,
 * every feature the manifest asks for is in the lock and none it leaves out is; and the index has
 * the digest of every file of the baseline. That shows a change made to one file by hand. It is no
 * proof of where a project came from: whoever writes all the digests again has a record that reads.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class ProjectState {

    /** The directory of the state in a project */
    public static final String DIRECTORY = '.grails'

    /** The manifest: what was asked for. It is written last: a state without it was not written to the end */
    public static final String MANIFEST = '.grails/project.yml'

    /** The lock: what the manifest came to */
    public static final String LOCK = '.grails/forge.lock.yml'

    /** The index of the baseline: the files the generator wrote, with their digests */
    public static final String BASELINE_INDEX = '.grails/baseline/index.yml'

    /** The directory that holds the content of the files of the baseline, see {@link BaselineIndex#copyPath(String)} */
    public static final String BASELINE_FILES = '.grails/baseline/files'

    /**
     * Tells Git to keep the bytes of the state as they are: a clone that converts line endings would
     * otherwise have a baseline that is not the one the index has the digests of. It is written with
     * the state and is not needed to read it.
     */
    public static final String ATTRIBUTES = '.grails/.gitattributes'

    /**
     * Tells Git to commit every file of the baseline, also one that a pattern of the application's
     * {@code .gitignore}, or of the one of whoever commits, would leave out. It is written with the
     * state and is not needed to read it.
     */
    public static final String IGNORE = '.grails/.gitignore'

    /**
     * What {@link #IGNORE} says: every file of the state that has to be committed is committed, also
     * where a pattern of a {@code .gitignore} above it, or of the one of whoever commits, would leave
     * it out. A pattern that leaves out the directory {@code .grails} itself is not undone by it: Git
     * does not look into a directory that is ignored. And what the commands keep under {@code .grails}
     * that is of one machine is left out: {@code cache}, {@code history}, {@code run} and
     * {@code transactions}, also in a project whose own {@code .gitignore} has no line for them, as one
     * that an earlier version created and {@code project migrate} gave its record.
     */
    public static final String IGNORE_CONTENT = '!/project.yml\n!/forge.lock.yml\n!/.gitattributes\n!/.gitignore\n!/baseline/\n!/baseline/**\n' +
            '/cache/\n/history\n/run/\n/transactions/\n'

    private static final Set<String> OF_BUILD_ROOT = ['gradlew', 'gradlew.bat', 'gradle/wrapper/gradle-wrapper.jar', 'gradle/wrapper/gradle-wrapper.properties',
                                                     'settings.gradle', 'settings.gradle.kts', 'grailsw', 'grailsw.bat', 'grails-wrapper.jar', '.gitattributes'].toSet().asImmutable()

    // how Git marks the lines of the two sides of a conflict it leaves in a file
    private static final String CONFLICT_START = '<<<<<<<'
    private static final String CONFLICT_END = '>>>>>>>'

    /** The directory of the baseline, the index and the content */
    public static final String BASELINE = '.grails/baseline'

    /**
     * The record of the features of an application that earlier versions wrote. It is read for a
     * project that has no state, and is not written.
     */
    public static final String EARLIER_RECORD = 'grails-forge-cli.yml'

    final ProjectManifest manifest
    final ProjectLock lock
    private final BaselineIndex baseline

    private ProjectState(ProjectManifest manifest, ProjectLock lock, BaselineIndex baseline) {
        this.manifest = manifest
        this.lock = lock
        this.baseline = baseline
    }

    /**
     * @return the index of the baseline
     * @throws IllegalStateException for a state that was read without it, see {@link #readSelection(Path)}
     */
    BaselineIndex getBaseline() {
        if (baseline == null) {
            throw new IllegalStateException('The state was read without the index of its baseline')
        }
        return baseline
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
     * @return whether the file is one of the root of a Gradle build and not of an application in it:
     * the Gradle wrapper, the settings, the Grails wrapper, and the rules for Git that are there for
     * the wrapper scripts. An application that is created as a project of an existing build is
     * written without them
     * @since 9.0
     */
    static boolean isOfBuildRoot(String path) {
        String steps = path.split('/').findAll { String step -> !step.isEmpty() && step != '.' }.join('/')
        return BaselineIndex.folded(steps) in OF_BUILD_ROOT
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
        return read(projectDirectory, true)
    }

    /**
     * Reads the manifest and the lock of a project and checks that they belong together, for what
     * needs to know what the application is and has, and nothing of its files. The index of the
     * baseline has to be there and is not read.
     *
     * @param projectDirectory the directory of a project
     * @throws ProjectStateException with what is wrong, in one line
     */
    static ProjectState readSelection(Path projectDirectory) {
        return read(projectDirectory, false, false)
    }

    /**
     * Reads the manifest and the lock as {@link #readSelection(Path)} does, for what shows the record
     * and changes nothing.
     *
     * @param projectDirectory the directory of a project
     * @param missingOptionsAsDefaults whether a record that lacks an option, as one written before the
     * option was recorded does, is read with what a create command takes when the option is not given,
     * the option named by {@link GenerationOptions#notRecorded}; otherwise such a record is refused
     * @throws ProjectStateException with what is wrong, in one line
     */
    static ProjectState readSelection(Path projectDirectory, boolean missingOptionsAsDefaults) {
        return read(projectDirectory, false, missingOptionsAsDefaults)
    }

    private static ProjectState read(Path projectDirectory, boolean withBaseline) {
        return read(projectDirectory, withBaseline, false)
    }

    private static ProjectState read(Path projectDirectory, boolean withBaseline, boolean missingOptionsAsDefaults) {
        List<String> files = [MANIFEST, LOCK, BASELINE_INDEX]
        List<String> missing = files.findAll { String file -> !Files.exists(projectDirectory.resolve(file), LinkOption.NOFOLLOW_LINKS) }
        if (missing.size() == files.size()) {
            throw new ProjectStateException("There is no state of a generated project here: ${MANIFEST} is missing")
        }
        if (missing) {
            // The manifest is written last, after the baseline, so a generation that did not come to
            // its end has no manifest. A clone whose manifest or lock was not committed looks the same
            // but for its baseline, which is all there: that is what an ignore rule of Git leaves behind
            String it = missing.size() == 1 ? 'it' : 'them'
            String advice = !missing.contains(BASELINE_INDEX) && Files.isDirectory(projectDirectory.resolve(BASELINE_FILES), LinkOption.NOFOLLOW_LINKS) ?
                    "the baseline is there, as it is when ${it == 'it' ? 'the file was' : 'the files were'} not committed with the project: see whether a rule of a .gitignore, " +
                            "also a global one, leaves ${it} out, and commit ${it} from where the project was generated" :
                    missing.contains(MANIFEST) ?
                            'the project was not generated to the end, and creating it again in this directory with --inplace completes it' :
                            "restore ${it} from version control"
            throw new ProjectStateException("The state of the project is not complete: ${missing.join(' and ')} ${missing.size() == 1 ? 'is' : 'are'} missing; ${advice}")
        }
        try {
            byte[] manifestBytes = bytes(projectDirectory, MANIFEST, StateLimits.MAX_MANIFEST)
            byte[] lockBytes = bytes(projectDirectory, LOCK, StateLimits.MAX_LOCK)
            byte[] indexBytes = withBaseline ? bytes(projectDirectory, BASELINE_INDEX, StateLimits.MAX_INDEX) : null
            notMergedByLines([(MANIFEST): manifestBytes, (LOCK): lockBytes, (BASELINE_INDEX): indexBytes])
            return of(manifestBytes, lockBytes, indexBytes, missingOptionsAsDefaults)
        } catch (IOException e) {
            throw new ProjectStateException("The state of the project cannot be read: ${e.class.simpleName}")
        }
    }

    /**
     * What a merge of two branches that both changed the state leaves in it: the lines of both sides,
     * between the markers of a conflict. The files hold the digests of each other, so the state is not
     * merged line by line, by hand or by a tool; what is said instead is the way back to a state that
     * reads, which the guide describes as the state after a merge.
     */
    private static void notMergedByLines(Map<String, byte[]> files) {
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            if (file.value == null) {
                continue
            }
            List<String> lines = new String(file.value, StandardCharsets.UTF_8).readLines()
            int at = lines.findIndexOf { String line -> line.startsWith(CONFLICT_START) || line.startsWith(CONFLICT_END) }
            if (at >= 0) {
                throw new ProjectStateException("${file.key} has the markers of a merge conflict in it (line ${at + 1}). The files of ${DIRECTORY} hold the digests of " +
                        'each other and are not merged line by line: take them as one side of the merge committed them, as git restore --source=HEAD --staged ' +
                        "--worktree -- ${DIRECTORY} does for this branch, resolve the conflicts of the other files, and then add the features the other side added " +
                        'with feature add')
            }
        }
    }

    /**
     * The state the three files hold, when they belong together. What writes the state reads it back
     * with this before it writes anything, and what adds a feature to a project reads with it the
     * state the generator writes for the project with the feature.
     *
     * @param indexBytes the index of the baseline, or {@code null} to read the state without it
     * @throws ProjectStateException with what is wrong, in one line
     */
    static ProjectState of(byte[] manifestBytes, byte[] lockBytes, byte[] indexBytes) {
        return of(manifestBytes, lockBytes, indexBytes, false)
    }

    private static ProjectState of(byte[] manifestBytes, byte[] lockBytes, byte[] indexBytes, boolean missingOptionsAsDefaults) {
        for (Map.Entry<String, Integer> each : [(MANIFEST): manifestBytes.length - StateLimits.MAX_MANIFEST, (LOCK): lockBytes.length - StateLimits.MAX_LOCK,
                                               (BASELINE_INDEX): (indexBytes == null ? 0 : indexBytes.length) - StateLimits.MAX_INDEX].entrySet()) {
            if (each.value > 0) {
                throw new ProjectStateException("${each.key} is larger than it can be, by ${each.value} bytes")
            }
        }
        ProjectManifest manifest = ProjectManifest.read(manifestBytes, missingOptionsAsDefaults)
        ProjectLock lock = ProjectLock.read(lockBytes, missingOptionsAsDefaults)
        String apart = apart(manifest, lock, manifestBytes)
        if (apart != null) {
            throw new ProjectStateException("${LOCK} does not belong to ${MANIFEST}: ${apart}")
        }
        if (indexBytes == null) {
            return new ProjectState(manifest, lock, null)
        }
        BaselineIndex baseline = BaselineIndex.read(indexBytes)
        if (lock.baselineIndexSha256 != StateYaml.sha256(indexBytes)) {
            throw new ProjectStateException("${BASELINE_INDEX} is not the baseline ${LOCK} was written with")
        }
        return new ProjectState(manifest, lock, baseline)
    }

    private static String apart(ProjectManifest manifest, ProjectLock lock, byte[] manifestBytes) {
        if (lock.manifestSha256 != StateYaml.sha256(manifestBytes) || lock.applicationType != manifest.applicationType ||
                lock.grailsVersion != manifest.grailsVersion) {
            return 'one of them was changed without the other'
        }
        if (lock.options != manifest.options) {
            return 'the options of the two are not the same'
        }
        Map<String, LockedFeature> locked = lock.features.collectEntries { LockedFeature each -> [(each.name): each] }
        for (RequestedFeature requested : manifest.requestedFeatures) {
            LockedFeature feature = locked.get(requested.name)
            if (feature == null) {
                return "the feature ${requested.name} was asked for and is not among the features of the lock"
            }
            if ((requested.coordinates != null && requested.coordinates != feature.coordinates) || (requested.version != null && requested.version != feature.version)) {
                return "the feature ${requested.name} is not of the artifact that was asked for"
            }
        }
        for (RequestedFeature excluded : manifest.excludedFeatures) {
            if (locked.containsKey(excluded.name)) {
                return "the feature ${excluded.name} was left out and is among the features of the lock"
            }
        }
        // a preset is a pack: the lock has where it came from as it has it for a feature pack
        if (manifest.preset != null && locked.get(manifest.preset)?.source != LockedFeature.PACK) {
            return "the preset ${manifest.preset} is not the feature of a pack in the lock"
        }
        return null
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
            Set<String> copies = new HashSet<>()
            for (BaselineFile file : getBaseline().files) {
                copies << BaselineIndex.copyPath(file.path)
                total += copy(root, file).length
                if (total > StateLimits.MAX_BASELINE) {
                    throw new ProjectStateException("${shown(file)} is larger than a baseline can hold")
                }
            }
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {

                private int seen

                @Override
                FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    String path = root.relativize(file).join('/')
                    if (++seen > StateLimits.MAX_FILES || !copies.contains(path)) {
                        throw new ProjectStateException("${BASELINE_FILES}/${StateYaml.shown(path, StateYaml.MAX_ECHO)} is not a file of the baseline index")
                    }
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (IOException e) {
            throw new ProjectStateException("The baseline of the project cannot be read: ${e.class.simpleName}")
        }
    }

    /**
     * Whether a file of the project is as the generator wrote it. A file the index calls text is
     * compared without regard to how its lines end, because a clone can have the line endings of its
     * platform (Git's {@code core.autocrlf}); a file it calls binary is compared byte by byte. Whether
     * the file can be executed is not compared.
     *
     * @param projectDirectory the directory of the project this state was read from
     * @param path the path of a file of the application, with {@code /} between its steps
     * @return whether the file is there with the content the baseline has for it
     * @throws ProjectStateException for a path the baseline has not, and for a baseline that is not
     * what its index says
     */
    boolean isAsGenerated(Path projectDirectory, String path) {
        BaselineFile file = getBaseline().find(path)
        if (file == null) {
            throw new ProjectStateException("${StateYaml.shown(path, StateYaml.MAX_ECHO)} is not a file the baseline of the project has")
        }
        try {
            byte[] generated = copy(directory(projectDirectory, BASELINE_FILES), file)
            Path working = projectDirectory
            for (String step : path.split('/')) {
                working = working.resolve(step)
            }
            if (!Files.isRegularFile(working, LinkOption.NOFOLLOW_LINKS) || Files.size(working) > StateLimits.MAX_BASELINE_FILE) {
                return false
            }
            byte[] current = Files.readAllBytes(working)
            return file.binary ? Arrays.equals(generated, current) : Arrays.equals(withLineFeeds(generated), withLineFeeds(current))
        } catch (IOException e) {
            throw new ProjectStateException("The baseline of the project cannot be read: ${e.class.simpleName}")
        }
    }

    // a carriage return before a line feed is dropped; one that stands alone is content
    private static byte[] withLineFeeds(byte[] bytes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(bytes.length)
        for (int index = 0; index < bytes.length; index++) {
            if (bytes[index] != (byte) 13 || index + 1 == bytes.length || bytes[index + 1] != (byte) 10) {
                out.write(bytes[index])
            }
        }
        return out.toByteArray()
    }

    private static String shown(BaselineFile file) {
        return "${BASELINE_FILES}/${StateYaml.shown(BaselineIndex.copyPath(file.path), StateYaml.MAX_ECHO)}"
    }

    // the copy of a file of the index, when it is there and is what the index says
    private static byte[] copy(Path root, BaselineFile file) {
        Path path = root
        List<String> steps = BaselineIndex.copyPath(file.path).split('/').toList()
        for (String step : steps.subList(0, steps.size() - 1)) {
            path = path.resolve(step)
            if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new ProjectStateException("${shown(file)} is missing from the baseline")
            }
        }
        path = path.resolve(steps.last())
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new ProjectStateException("${shown(file)} is missing from the baseline")
        }
        if (Files.size(path) > StateLimits.MAX_BASELINE_FILE) {
            throw new ProjectStateException("${shown(file)} is larger than a baseline can hold")
        }
        byte[] bytes
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            bytes = input.readNBytes((int) StateLimits.MAX_BASELINE_FILE + 1)
        }
        if (bytes.length > StateLimits.MAX_BASELINE_FILE) {
            throw new ProjectStateException("${shown(file)} is larger than a baseline can hold")
        }
        if (HexFormat.of().formatHex(MessageDigest.getInstance('SHA-256').digest(bytes)) != file.sha256) {
            throw new ProjectStateException("${shown(file)} is not what the generator wrote: its content does not have the digest of the index")
        }
        return bytes
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
