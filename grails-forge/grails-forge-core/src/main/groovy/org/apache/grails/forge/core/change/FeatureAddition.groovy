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
import java.text.Normalizer
import java.util.function.Consumer

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
    private static final int MAX_PLACES = 5
    private static final int MAX_QUOTED = 8
    private static final int MAX_QUOTED_LINE = 160

    private final ProjectGenerator generator
    private final Path records

    /**
     * @param generator the generator of the applications
     */
    FeatureAddition(ProjectGenerator generator) {
        this(generator, Path.of(System.getProperty('user.home'), '.grails', 'forge', 'transactions'))
    }

    /**
     * @param generator the generator of the applications
     * @param records the directory the record of a change is kept in while the change is made, which
     * is how a change that was interrupted is found again. It is a directory of whoever runs the
     * command and not of the project: what a project comes with never says that there is a change to
     * undo. Without it, it is {@code .grails/forge/transactions} in the home directory of the user
     */
    FeatureAddition(ProjectGenerator generator, Path records) {
        this.generator = generator
        this.records = records
    }

    /**
     * Ends a change of the project that this user began and that was interrupted, which has to be
     * done before the project is changed again. Every file of the change is looked at first. When
     * all of them are what the change wrote, the change is complete and stays. Otherwise the files it
     * wrote are put back; a file it wrote that was changed since stops that, and then nothing is
     * put back.
     *
     * @param projectDirectory the directory of the project
     * @return what was done; nothing, when no change was interrupted
     * @throws ProjectChangeException when the change cannot be undone, with the files and what to do
     */
    ProjectRecovery recover(Path projectDirectory) {
        return recover(projectDirectory) { String path -> }
    }

    /**
     * @param putBack told the path of each file once it is put back. What it throws is not handled:
     * the rest is then put back when this is called again
     * @see #recover(Path)
     */
    ProjectRecovery recover(Path projectDirectory, Consumer<String> putBack) {
        return ProjectTransaction.recover(projectDirectory, records, putBack)
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
        if (ProjectTransaction.isInterrupted(projectDirectory, records)) {
            throw new ProjectChangeException('An earlier change of the project was interrupted; it is undone when a feature is added without --dry-run and --diff, ' +
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
            return new ChangePlan(projectDirectory, records, [], alreadyThere.unique(), [], [], [], null, [], [])
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
            after = render(manifest, recorded + toAdd, operatingSystem, console, true)
        } catch (FeatureSelectionException e) {
            if (e.getOption() == null) {
                throw e
            }
            // The feature does not agree with an option the application has. What the application
            // would be with the feature in the place of that option is worked out all the same, to
            // show it: it is refused below, because it takes away what the application has
            try {
                after = render(manifest, recorded + toAdd, operatingSystem, ConsoleOutput.NOOP, false)
            } catch (FeatureSelectionException ignored) {
                throw e
            }
        }
        ProjectState proposed = stateOf(after)
        List<String> changedOptions = changedOptions(state.manifest.options, proposed.manifest.options)
        List<String> has = proposed.lock.featureNames
        List<String> lostFeatures = state.lock.featureNames.findAll { String name -> !has.contains(name) }
        List<String> lostFiles = state.baseline.files.findAll { BaselineFile file -> proposed.baseline.find(file.path) == null }*.path
        String refusal = refusal(toAdd, changedOptions, lostFeatures, lostFiles)

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

        // what a feature that is refused would take away is shown with what it would add
        for (String path : lostFiles) {
            BaselineFile generated = state.baseline.find(path)
            Working working = working(projectDirectory, path)
            files << new PlannedFile(path, Action.REMOVE, null, generated.binary, working.content, new byte[0], null, before.content(path), new byte[0], [])
        }
        List<String> had = lock.featureNames
        return new ChangePlan(projectDirectory, records, toAdd, alreadyThere.unique(), proposed.lock.featureNames.findAll { String name -> !had.contains(name) },
                files.sort(false) { PlannedFile file -> file.path }, writes, refusal, lostFeatures, changedOptions)
    }

    private static List<String> changedOptions(GenerationOptions has, GenerationOptions would) {
        List<String> changed = []
        if (has.servlet != would.servlet) {
            changed << "--servlet ${would.servlet.name().toLowerCase(Locale.ROOT)} in the place of ${has.servlet.name().toLowerCase(Locale.ROOT)}".toString()
        }
        if (has.data != would.data) {
            changed << "--data ${would.data.name().toLowerCase(Locale.ROOT)} in the place of ${has.data.name().toLowerCase(Locale.ROOT)}".toString()
        }
        if (has.reloading != would.reloading) {
            changed << "--reloading ${would.reloading.name().toLowerCase(Locale.ROOT).replace('_', '-')} in the place of ${has.reloading.name().toLowerCase(Locale.ROOT).replace('_', '-')}".toString()
        }
        if (has.jdk != would.jdk) {
            changed << "--jdk ${would.jdk.majorVersion()} in the place of ${has.jdk.majorVersion()}".toString()
        }
        return changed
    }

    // Nothing the application has is taken away by adding to it. What would be, and how to have it
    private static String refusal(List<String> toAdd, List<String> changedOptions, List<String> lostFeatures, List<String> lostFiles) {
        if (changedOptions.isEmpty() && lostFeatures.isEmpty() && lostFiles.isEmpty()) {
            return null
        }
        String adding = "Adding ${named(toAdd)}"
        String how = " An application has it when it is created with it (--features ${toAdd.join(',')}" +
                "${changedOptions.isEmpty() ? '' : ', ' + changedOptions.collect { String option -> option.substring(0, option.indexOf(' in the place')) }.join(', ')}); " +
                "to change this application by hand, feature add ${toAdd.join(',')} --diff shows what would be added to its files and what would be removed, " +
                'and the record under .grails then does not know of the change. Nothing was changed'
        if (!changedOptions.isEmpty()) {
            return "${adding} would change what the application was created with (${changedOptions.join(', ')})" +
                    "${lostFeatures.isEmpty() ? '' : " and take the ${lostFeatures.size() == 1 ? 'feature' : 'features'} ${named(lostFeatures)} out of it"}: " +
                    "adding a feature does not change the options of an application.${how}"
        }
        if (!lostFeatures.isEmpty()) {
            return "${adding} would take the ${lostFeatures.size() == 1 ? 'feature' : 'features'} ${named(lostFeatures)} out of the application: " +
                    "a feature that replaces another cannot be added to an application.${how}"
        }
        return "${adding} would leave out ${lostFiles.size() == 1 ? 'a file' : 'files'} the application was generated with (${named(lostFiles)}), " +
                "which adding a feature does not do.${how}"
    }

    private RenderedProject render(ProjectManifest manifest, List<String> features, OperatingSystem operatingSystem, ConsoleOutput console, boolean optionsAreChoices = true) {
        Project project = NameUtils.parse(manifest.defaultPackage + '.' + manifest.name)
        GenerationOptions recorded = manifest.options
        // The options of the manifest are what the application has, so each of them is a choice: a
        // feature that would put another servlet container, reloader or Grails Data implementation in
        // the place of the one the application has is refused, and replaces nothing
        Options options = new Options(recorded.reloading, recorded.data, recorded.servlet, recorded.jdk, operatingSystem)
                .withGiven(optionsAreChoices ? EnumSet.allOf(OptionName) : EnumSet.noneOf(OptionName))
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
            return conflict(path, "the feature ${does} this file, and in the project ${working.obstacle}", binary, working, base, proposed, working.advice)
        }
        if (base == null) {
            if (working.content == null) {
                return new PlannedFile(path, Action.ADD, null, binary, null, proposed, proposedExecutable ? Boolean.TRUE : null, null, proposed, [])
            }
            if (!(binary ? Arrays.equals(working.content, proposed) : ThreeWayMerge.sameText(working.content, proposed))) {
                return conflict(path, 'the feature adds this file, and the project has another file of its name', binary, working, base, proposed,
                        ['Rename or remove the file of the project, add the feature, and bring into the new file what was in yours'])
            }
            if (posix && working.executable != proposedExecutable) {
                return conflict(path, "the feature adds this file as one that ${proposedExecutable ? 'can' : 'cannot'} be executed, and the file of the project " +
                        "${working.executable ? 'can' : 'cannot'} be", binary, working, base, proposed,
                        ["Make the file ${proposedExecutable ? '' : 'not '}executable and add the feature again".toString()])
            }
            return new PlannedFile(path, Action.ADOPT, null, binary, working.content, working.content, null, base, proposed, [])
        }
        if (working.content == null) {
            return conflict(path, 'the feature changes this file, and it was removed from the project', binary, working, base, proposed,
                    ["Put the file back as it was generated (its copy is ${ProjectState.BASELINE_FILES}/${BaselineIndex.copyPath(path)}), add the feature, and remove it again".toString()])
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
                return conflict(path, 'the feature changes this binary file, and it was changed in the project too', binary, working, base, proposed,
                        ["Put the file back as it was generated (its copy is ${ProjectState.BASELINE_FILES}/${BaselineIndex.copyPath(path)}), add the feature, and make your change to the new file".toString()])
            }
            edited = false
        } else if (ThreeWayMerge.sameText(working.content, proposed)) {
            result = working.content
            edited = false
        } else {
            ThreeWayMerge merge = ThreeWayMerge.of(base, working.content, proposed)
            if (merge.isTooLarge()) {
                return conflict(path, "the feature changes this file, and the file of the project has more lines than are merged (${ThreeWayMerge.MAX_LINES}) or differs " +
                        "from the generated one in more of them (${ThreeWayMerge.MAX_CHANGED})", binary, working, base, proposed,
                        [("Put the file aside, put the generated one in its place (its copy is ${ProjectState.BASELINE_FILES}/${BaselineIndex.copyPath(path)}), add the feature, " +
                                 'and bring your changes into the new file').toString()])
            }
            if (merge.getConflicts() > 0) {
                return conflict(path, "the feature changes ${merge.getConflicts() == 1 ? 'lines' : "${merge.getConflicts()} places"} of this file that were changed " +
                        'in the project too, or lines next to them', binary, working, base, proposed, places(merge))
            }
            result = merge.getMerged()
            edited = !ThreeWayMerge.sameText(working.content, base)
        }
        // whether a file can be executed is one value: the project has it as it was generated, or as
        // the feature makes it
        Boolean executable = posix && baseExecutable != proposedExecutable && working.executable != proposedExecutable ? Boolean.valueOf(proposedExecutable) : null
        if (executable == null && Arrays.equals(result, working.content)) {
            return new PlannedFile(path, Action.ADOPT, null, binary, working.content, working.content, null, base, proposed, [])
        }
        return new PlannedFile(path, edited ? Action.MERGE : Action.UPDATE, null, binary, working.content, result, executable, base, proposed, [])
    }

    private static PlannedFile conflict(String path, String reason, boolean binary, Working working, byte[] base, byte[] proposed, List<String> details) {
        return new PlannedFile(path, Action.CONFLICT, reason, binary, working.content, null, null, base, proposed, details)
    }

    // Each place the two changed, with the lines of both, and what to do about it
    private static List<String> places(ThreeWayMerge merge) {
        List<String> lines = []
        for (ThreeWayMerge.Conflict place : merge.getConflictingChanges().take(MAX_PLACES)) {
            lines << (place.current.isEmpty() ? "before line ${place.line} the project removed lines, and the feature has there:" :
                    "at line ${place.line} the project has:").toString()
            if (!place.current.isEmpty()) {
                lines.addAll(quoted(place.current))
                lines << (place.proposed.isEmpty() ? 'and the feature removes lines there' : 'and the feature has there:')
            }
            lines.addAll(quoted(place.proposed))
        }
        if (merge.getConflicts() > MAX_PLACES) {
            lines << "and ${merge.getConflicts() - MAX_PLACES} more places".toString()
        }
        lines << 'Both changed the same place, so neither is taken for the other. Move the lines of the project that are shown to another place of the file, ' +
                'at least one unchanged line away (or take them out), add the feature again, and then put them where they belong'
        return lines
    }

    private static List<String> quoted(List<String> lines) {
        List<String> quoted = lines.take(MAX_QUOTED).collect { String line -> '    ' + (line.length() > MAX_QUOTED_LINE ? line.substring(0, MAX_QUOTED_LINE) + '...' : line) }
        if (lines.size() > MAX_QUOTED) {
            quoted << "    ... ${lines.size() - MAX_QUOTED} more lines".toString()
        }
        return quoted
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
            // on a file system that takes Build.gradle for build.gradle, the file would come back under
            // the name the generator has for it
            String named = nameThere(file)
            if (named != steps.last()) {
                return new Working(null, false, "it is named ${named}".toString(),
                        ["Give the file the name ${steps.last()}, add the feature, and give it its name back".toString()])
            }
            return new Working(Files.readAllBytes(file), Files.isExecutable(file), null)
        } catch (IOException e) {
            throw new ProjectChangeException("${path} cannot be read (${e.getClass().getSimpleName()}); nothing was changed", e)
        }
    }

    // the name a file has in its directory, which is not the name it was found by where the file
    // system takes one case of a letter, or one composition of it, for another
    private static String nameThere(Path file) throws IOException {
        String name = file.getFileName().toString()
        String other = null
        Files.newDirectoryStream(file.getParent()).withCloseable { stream ->
            for (Path each : stream) {
                String there = each.getFileName().toString()
                if (Normalizer.normalize(there, Normalizer.Form.NFC) == Normalizer.normalize(name, Normalizer.Form.NFC)) {
                    other = name
                    break
                }
                if (there.equalsIgnoreCase(name)) {
                    other = there
                }
            }
        }
        return other ?: name
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

        final List<String> advice

        Working(byte[] content, boolean executable, String obstacle, List<String> advice = []) {
            this.content = content
            this.executable = executable
            this.obstacle = obstacle
            this.advice = advice
        }
    }
}
