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
import jakarta.annotation.Nullable
import org.apache.grails.forge.core.change.ChangePlan.Action
import org.apache.grails.forge.core.change.ChangePlan.PlannedFile
import org.apache.grails.forge.core.change.ProjectTransaction.Write
import org.apache.grails.forge.core.pack.PackDescriptor
import org.apache.grails.forge.core.state.BaselineIndex
import org.apache.grails.forge.core.state.BaselineIndex.BaselineFile
import org.apache.grails.forge.core.state.GenerationOptions
import org.apache.grails.forge.core.state.ProjectLock
import org.apache.grails.forge.core.state.ProjectLock.LockedFeature
import org.apache.grails.forge.core.state.ProjectManifest
import org.apache.grails.forge.core.state.ProjectState
import org.apache.grails.forge.core.state.ProjectStateException
import org.apache.grails.forge.core.state.StateLimits
import org.grails.forge.application.OperatingSystem
import org.grails.forge.application.Project
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.feature.AvailableFeatures
import org.grails.forge.feature.Feature
import org.grails.forge.feature.FeatureSelectionException
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.options.OptionName
import org.grails.forge.options.Options
import org.grails.forge.util.NameUtils
import org.grails.forge.util.VersionInfo
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/**
 * Adds features to an application that was generated, from the state the generator left under
 * {@code .grails}: what was asked for (the manifest), what that came to (the lock) and every file as
 * it was generated (the baseline).
 *
 * <p>The application is generated twice in memory, by the generator that makes new applications: once
 * as it was recorded, which has to give the very files of the baseline, and once with the features
 * added. Every file the second differs in is then looked at in three versions: as it was generated,
 * as it would be generated now, and as the project has it. A file the project has unchanged gets the
 * new content. A text file that was edited gets the changes of the features merged into it and keeps
 * its edits; where the two changed the same lines, where a file was removed, replaced by something
 * that is no file, or is binary and changed on both sides, and where a new file meets another of its
 * name, there is a conflict, and a plan with a conflict writes nothing. A file the features do not
 * change is not looked at.</p>
 *
 * <p>Only features of Grails are added, to an application that has no others and that this very
 * version generated. Nothing is taken out: a feature that would replace another, or leave a generated
 * file out, is refused.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class FeatureAddition {

    private static final int MAX_NAMED = 10
    private static final int MAX_MESSAGE = 1000

    private final ProjectGenerator generator

    FeatureAddition(ProjectGenerator generator) {
        this.generator = generator
    }

    /**
     * Undoes a change of the project that was interrupted, which has to be done before the project is
     * changed again.
     *
     * @param projectDirectory the directory of the project
     * @return whether there was such a change, and the project is now as it was before it
     * @throws ProjectChangeException when a file was changed since the interruption and is left as
     * it is, with what to do
     */
    static boolean recover(Path projectDirectory) {
        return ProjectTransaction.recover(projectDirectory)
    }

    /**
     * Works out what adding features to a project comes to. Nothing is written.
     *
     * @param projectDirectory the directory of the project
     * @param names the names of the features to add
     * @param operatingSystem the operating system the generator is told of, as when an application is created
     * @param console where the features say what they have to say
     * @throws ProjectStateException when the state of the project cannot be used
     * @throws FeatureSelectionException when the application cannot have the features, as when it is created with them
     * @throws ProjectChangeException when features cannot be added to this project, or not these
     */
    ChangePlan plan(Path projectDirectory, List<String> names, @Nullable OperatingSystem operatingSystem, ConsoleOutput console) {
        if (ProjectTransaction.isInterrupted(projectDirectory)) {
            throw new ProjectChangeException("An earlier change of the project was interrupted (${ProjectTransaction.CURRENT}); it is undone when a feature is added, " +
                    'and has to be before anything else is done')
        }
        ProjectState state = ProjectState.read(projectDirectory)
        state.verifyBaseline(projectDirectory)
        ProjectManifest manifest = state.manifest
        ProjectLock lock = state.lock
        if (!manifest.isTracked()) {
            throw new ProjectChangeException("The project has no baseline the generator wrote (generationState in ${ProjectState.MANIFEST} is not " +
                    "${ProjectManifest.TRACKED}), so a feature cannot be added to it")
        }
        if (lock.generatorName != ProjectLock.GENERATOR || lock.generatorVersion != VersionInfo.getVersion() || lock.grailsVersion != VersionInfo.getGrailsVersion() ||
                lock.templateApi != PackDescriptor.TEMPLATE_API) {
            throw new ProjectChangeException("The application was generated by ${lock.generatorName} ${lock.generatorVersion} for Grails ${lock.grailsVersion}, and this is " +
                    "${ProjectLock.GENERATOR} ${VersionInfo.getVersion()} for Grails ${VersionInfo.getGrailsVersion()}: a feature is added by the version the application " +
                    "was generated with. Use the command line of Grails ${lock.generatorVersion} to add the feature")
        }
        List<String> ofPacks = lock.features.findAll { LockedFeature feature -> feature.source == LockedFeature.PACK }*.name
        if (!ofPacks.isEmpty()) {
            throw new ProjectChangeException("The application has the ${ofPacks.size() == 1 ? 'feature' : 'features'} ${named(ofPacks)} of a feature pack that is not part " +
                    'of Grails; a feature cannot yet be added to such an application')
        }
        if (names.isEmpty()) {
            throw new FeatureSelectionException('Name the feature to add')
        }
        AvailableFeatures available = generator.availableFeatures(manifest.applicationType, [])
        List<String> alreadyThere = []
        List<String> toAdd = []
        for (String name : names) {
            if (name != null && name.contains(':')) {
                throw new FeatureSelectionException("${FeatureSelectionException.shown(name)} is no name of a feature of Grails: the feature of a plugin cannot yet be " +
                        'added to an application by the coordinates of the plugin')
            }
            Feature feature = name == null ? null : available.findFeature(name).orElse(null)
            // a name that is no feature is refused by the selection, in the words it has for it
            String canonical = feature == null ? String.valueOf(name) : feature.getName()
            if (lock.featureNames.contains(canonical)) {
                alreadyThere << canonical
            } else if (!toAdd.contains(canonical)) {
                toAdd << canonical
            }
        }
        if (toAdd.isEmpty()) {
            return new ChangePlan(projectDirectory, [], alreadyThere.unique(), [], [], [])
        }

        byte[] manifestBytes = bytes(projectDirectory, ProjectState.MANIFEST)
        byte[] lockBytes = bytes(projectDirectory, ProjectState.LOCK)
        byte[] indexBytes = bytes(projectDirectory, ProjectState.BASELINE_INDEX)
        List<String> recorded = manifest.requestedFeatures*.name
        RenderedProject before
        try {
            before = render(manifest, recorded, operatingSystem, ConsoleOutput.NOOP)
        } catch (ProjectChangeException e) {
            throw e
        } catch (Exception e) {
            throw new ProjectChangeException("The application cannot be generated again as it was recorded: ${line(e)}", e)
        }
        ProjectState replayed = stateOf(before)
        List<String> unlike = unlike(state.baseline, replayed.baseline)
        if (!unlike.isEmpty()) {
            throw new ProjectChangeException("The application cannot be generated again as it was: ${unlike.size()} ${unlike.size() == 1 ? 'file is' : 'files are'} " +
                    "not what the baseline of the project has (${named(unlike)}). A feature is added to what the same generator makes of the same choices; " +
                    'the repositories given with GRAILS_REPO_URL are among those choices')
        }
        if (!Arrays.equals(before.content(ProjectState.MANIFEST), manifestBytes) || !Arrays.equals(before.content(ProjectState.LOCK), lockBytes) ||
                !Arrays.equals(before.content(ProjectState.BASELINE_INDEX), indexBytes)) {
            throw new ProjectChangeException("The record of the application under ${ProjectState.DIRECTORY} is not what this generator writes for the same choices, " +
                    'so a feature cannot be added to it; the repositories given with GRAILS_REPO_URL are among those choices')
        }

        RenderedProject after
        try {
            after = render(manifest, recorded + toAdd, operatingSystem, console)
        } catch (FeatureSelectionException e) {
            if (e.getOption() != null) {
                throw new FeatureSelectionException("${e.getMessage()}. The application was created with that option, and adding a feature does not change " +
                        'the options of an application', e)
            }
            throw e
        }
        ProjectState proposed = stateOf(after)
        additive(toAdd, state, proposed)

        boolean posix = projectDirectory.getFileSystem().supportedFileAttributeViews().contains('posix')
        List<PlannedFile> files = []
        List<Write> writes = []
        for (BaselineFile file : proposed.baseline.files) {
            BaselineFile generated = state.baseline.find(file.path)
            PlannedFile planned = planned(projectDirectory, file.path, generated == null ? null : before.content(file.path), generated != null && generated.executable,
                    after.content(file.path), file.executable, file.binary || (generated != null && generated.binary), posix)
            if (planned != null) {
                files << planned
            }
            if (generated == null || generated.sha256 != file.sha256) {
                String copy = "${ProjectState.BASELINE_FILES}/${BaselineIndex.copyPath(file.path)}".toString()
                writes << new Write(copy, after.content(copy), null, generated == null ? null : generated.sha256)
            }
        }
        // the record the tools of earlier versions read is written beside the state and is no part of
        // the baseline: what it was generated as is what was generated again. A project without it stays without
        byte[] earlier = before.content(ProjectState.EARLIER_RECORD)
        if (earlier != null && after.content(ProjectState.EARLIER_RECORD) != null &&
                Files.exists(projectDirectory.resolve(ProjectState.EARLIER_RECORD), LinkOption.NOFOLLOW_LINKS)) {
            PlannedFile planned = planned(projectDirectory, ProjectState.EARLIER_RECORD, earlier, false, after.content(ProjectState.EARLIER_RECORD), false, false, posix)
            if (planned != null) {
                files << planned
            }
        }
        writes << new Write(ProjectState.BASELINE_INDEX, after.content(ProjectState.BASELINE_INDEX), null, ProjectTransaction.sha256(indexBytes))
        writes << new Write(ProjectState.LOCK, after.content(ProjectState.LOCK), null, ProjectTransaction.sha256(lockBytes))
        // the manifest last, as when the application was generated
        writes << new Write(ProjectState.MANIFEST, after.content(ProjectState.MANIFEST), null, ProjectTransaction.sha256(manifestBytes))

        List<String> had = lock.featureNames
        return new ChangePlan(projectDirectory, toAdd, alreadyThere.unique(), proposed.lock.featureNames.findAll { String name -> !had.contains(name) },
                files.sort(false) { PlannedFile file -> file.path }, writes)
    }

    private RenderedProject render(ProjectManifest manifest, List<String> features, OperatingSystem operatingSystem, ConsoleOutput console) {
        Project project = NameUtils.parse(manifest.defaultPackage + '.' + manifest.name)
        GenerationOptions recorded = manifest.options
        // The options of the manifest are what the application has, so each of them is a choice: a
        // feature that would put another servlet container, reloader or Grails Data implementation in
        // the place of the one the application has is refused, and replaces nothing
        Options options = new Options(recorded.reloading, recorded.data, recorded.servlet, recorded.jdk, operatingSystem)
                .withGiven(EnumSet.allOf(OptionName))
        GeneratorContext context = generator.createGeneratorContext(manifest.applicationType, project, options, operatingSystem, features, console, [],
                manifest.excludedFeatures*.name)
        RenderedProject rendered = new RenderedProject()
        generator.generate(manifest.applicationType, project, rendered, context)
        return rendered
    }

    private static ProjectState stateOf(RenderedProject rendered) {
        byte[] manifest = rendered.content(ProjectState.MANIFEST)
        byte[] lock = rendered.content(ProjectState.LOCK)
        byte[] index = rendered.content(ProjectState.BASELINE_INDEX)
        if (manifest == null || lock == null || index == null) {
            throw new ProjectChangeException('The generator wrote no state for the application, so a feature cannot be added with it')
        }
        return ProjectState.of(manifest, lock, index)
    }

    // the files of the baseline that are not what is generated for the same choices now
    private static List<String> unlike(BaselineIndex baseline, BaselineIndex replayed) {
        Set<String> unlike = new TreeSet<>()
        Map<String, BaselineFile> again = replayed.files.collectEntries { BaselineFile file -> [(file.path): file] }
        for (BaselineFile file : baseline.files) {
            BaselineFile other = again.remove(file.path)
            if (other == null || other.sha256 != file.sha256 || other.kind != file.kind || other.executable != file.executable) {
                unlike << file.path
            }
        }
        unlike.addAll(again.keySet())
        return unlike.toList()
    }

    // nothing the application has is taken away by adding to it
    private static void additive(List<String> toAdd, ProjectState state, ProjectState proposed) {
        String adding = "Adding ${named(toAdd)}"
        if (proposed.manifest.options != state.manifest.options) {
            throw new ProjectChangeException("${adding} would change the options the application was created with, which adding a feature does not do; nothing was changed")
        }
        List<String> has = proposed.lock.featureNames
        List<String> lost = state.lock.featureNames.findAll { String name -> !has.contains(name) }
        if (!lost.isEmpty()) {
            throw new ProjectChangeException("${adding} would take the ${lost.size() == 1 ? 'feature' : 'features'} ${named(lost)} out of the application: " +
                    'a feature that replaces another cannot be added to an application, only chosen when it is created; nothing was changed')
        }
        List<String> gone = state.baseline.files.findAll { BaselineFile file -> proposed.baseline.find(file.path) == null }*.path
        if (!gone.isEmpty()) {
            throw new ProjectChangeException("${adding} would leave out ${gone.size() == 1 ? 'a file' : 'files'} the application was generated with (${named(gone)}), " +
                    'which adding a feature does not do; nothing was changed')
        }
    }

    /**
     * What becomes of one file.
     *
     * @param base the file as it was generated, or {@code null} for a file the features add
     * @param proposed the file as it is generated with the features
     * @return what is to be done, or {@code null} when the features leave the file as it is
     */
    private static PlannedFile planned(Path project, String path, byte[] base, boolean baseExecutable, byte[] proposed, boolean proposedExecutable, boolean binary, boolean posix) {
        if (base != null && Arrays.equals(base, proposed) && baseExecutable == proposedExecutable) {
            return null
        }
        Working working = working(project, path)
        String does = base == null ? 'adds' : 'changes'
        if (working.obstacle != null) {
            return conflict(path, "the feature ${does} this file, and in the project ${working.obstacle}", binary, working, base, proposed)
        }
        if (base == null) {
            if (working.content == null) {
                return new PlannedFile(path, Action.ADD, null, binary, null, proposed, proposedExecutable ? Boolean.TRUE : null, null, proposed)
            }
            if (!(binary ? Arrays.equals(working.content, proposed) : ThreeWayMerge.sameText(working.content, proposed))) {
                return conflict(path, 'the feature adds this file, and the project has another file of its name', binary, working, base, proposed)
            }
            if (posix && working.executable != proposedExecutable) {
                return conflict(path, "the feature adds this file as one that ${proposedExecutable ? 'can' : 'cannot'} be executed, and the file of the project " +
                        "${working.executable ? 'can' : 'cannot'} be", binary, working, base, proposed)
            }
            return new PlannedFile(path, Action.ADOPT, null, binary, working.content, working.content, null, base, proposed)
        }
        if (working.content == null) {
            return conflict(path, 'the feature changes this file, and it was removed from the project', binary, working, base, proposed)
        }
        byte[] result
        boolean edited
        if (Arrays.equals(base, proposed)) {
            result = working.content
            edited = false
        } else if (binary) {
            if (Arrays.equals(working.content, base)) {
                result = proposed
            } else if (Arrays.equals(working.content, proposed)) {
                result = working.content
            } else {
                return conflict(path, 'the feature changes this binary file, and it was changed in the project too', binary, working, base, proposed)
            }
            edited = false
        } else if (ThreeWayMerge.sameText(working.content, proposed)) {
            result = working.content
            edited = false
        } else {
            ThreeWayMerge merge = ThreeWayMerge.of(base, working.content, proposed)
            if (merge.getConflicts() > 0) {
                return conflict(path, "the feature changes ${merge.getConflicts() == 1 ? 'lines' : "${merge.getConflicts()} places"} of this file that were changed " +
                        'in the project too, or lines next to them', binary, working, base, proposed)
            }
            result = merge.getMerged()
            edited = !ThreeWayMerge.sameText(working.content, base)
        }
        // whether a file can be executed is one value: the project has it as it was generated, or as
        // the feature makes it
        Boolean executable = posix && baseExecutable != proposedExecutable && working.executable != proposedExecutable ? Boolean.valueOf(proposedExecutable) : null
        if (executable == null && Arrays.equals(result, working.content)) {
            return new PlannedFile(path, Action.ADOPT, null, binary, working.content, working.content, null, base, proposed)
        }
        return new PlannedFile(path, edited ? Action.MERGE : Action.UPDATE, null, binary, working.content, result, executable, base, proposed)
    }

    private static PlannedFile conflict(String path, String reason, boolean binary, Working working, byte[] base, byte[] proposed) {
        return new PlannedFile(path, Action.CONFLICT, reason, binary, working.content, null, null, base, proposed)
    }

    // The file of the project: its content, nothing when there is no file, or what is there in its
    // place. A link is not followed, on the way to the file either
    private static Working working(Path project, String path) {
        try {
            Path file = project
            List<String> steps = path.split('/').toList()
            for (String step : steps.subList(0, steps.size() - 1)) {
                file = file.resolve(step)
                if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                    return new Working(null, false, null)
                }
                if (!Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) {
                    return new Working(null, false, Files.isSymbolicLink(file) ? 'a directory on the way to it is a symbolic link' : 'a directory on the way to it is a file')
                }
            }
            file = file.resolve(steps.last())
            if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                return new Working(null, false, null)
            }
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                return new Working(null, false, Files.isSymbolicLink(file) ? 'it is a symbolic link' : Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS) ? 'it is a directory' : 'it is not a file')
            }
            if (Files.size(file) > StateLimits.MAX_BASELINE_FILE) {
                return new Working(null, false, 'it is larger than a generated file can be')
            }
            return new Working(Files.readAllBytes(file), Files.isExecutable(file), null)
        } catch (IOException e) {
            throw new ProjectChangeException("${path} cannot be read (${e.getClass().getSimpleName()}); nothing was changed", e)
        }
    }

    private static byte[] bytes(Path project, String path) {
        try {
            return Files.readAllBytes(project.resolve(path))
        } catch (IOException e) {
            throw new ProjectStateException("The state of the project cannot be read: ${e.getClass().getSimpleName()}")
        }
    }

    private static String named(List<String> names) {
        return names.take(MAX_NAMED).join(', ') + (names.size() > MAX_NAMED ? " and ${names.size() - MAX_NAMED} more" : '')
    }

    private static String line(Exception e) {
        String message = (e.getMessage() ?: e.getClass().getSimpleName()).replaceAll(/[\p{Cc}\p{Cf}\p{Zl}\p{Zp}]/, ' ')
        return message.length() > MAX_MESSAGE ? message.substring(0, MAX_MESSAGE) + '...' : message
    }

    @CompileStatic
    private static final class Working {

        final byte[] content
        final boolean executable
        final String obstacle

        Working(byte[] content, boolean executable, String obstacle) {
            this.content = content
            this.executable = executable
            this.obstacle = obstacle
        }
    }
}
