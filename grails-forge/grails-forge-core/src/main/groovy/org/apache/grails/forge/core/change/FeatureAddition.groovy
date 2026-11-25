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
import org.apache.grails.forge.core.pack.Pack
import org.apache.grails.forge.core.pack.PackArtifact
import org.apache.grails.forge.core.state.AdoptedProjectRecord
import org.apache.grails.forge.core.state.BaselineIndex
import org.apache.grails.forge.core.state.BaselineIndex.BaselineFile
import org.apache.grails.forge.core.state.GenerationOptions
import org.apache.grails.forge.core.state.ProjectLock
import org.apache.grails.forge.core.state.ProjectLock.LockedFeature
import org.apache.grails.forge.core.state.ProjectManifest
import org.apache.grails.forge.core.state.ProjectManifest.RequestedFeature
import org.apache.grails.forge.core.state.ProjectState
import org.apache.grails.forge.core.state.ProjectStateException
import org.apache.grails.forge.core.state.StateLimits
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.OperatingSystem
import org.grails.forge.application.Project
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.feature.AvailableFeatures
import org.grails.forge.feature.Feature
import org.grails.forge.feature.FeatureSelectionException
import org.grails.forge.feature.OneOfFeature
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.options.OptionName
import org.grails.forge.options.Options
import org.grails.forge.template.YamlKeyOrder
import org.grails.forge.template.YamlTemplate
import org.grails.forge.util.NameUtils
import org.grails.forge.util.VersionInfo
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.error.YAMLException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.function.Consumer
import java.util.function.Predicate

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
 * <p>Features of Grails are added, and the features of feature packs that are not part of Grails,
 * which the caller gives with the features to add: the application is then one that was created
 * with the pack, and its record names the pack as the record of such an application does. An
 * application with the feature of a feature pack that is not part of Grails is generated again only
 * with that very pack, which the caller gives: the pack of a jar has to be the jar of the coordinates
 * and the SHA-256 the lock records, and a pack that was given as a directory or a file one whose
 * content has the SHA-256 the lock records; the application generated again is then held to its
 * baseline as every other is. A feature takes the place of one the application has by default, as a database
 * driver takes that of the default one: what the default wrote is taken out of the files, as a
 * difference like any other, so that lines of it that were changed in the project are a conflict, and
 * a file only it wrote is removed when the project has it as it was generated. A feature that was
 * asked for by name, when the application was created or when it was added, is replaced in the same
 * way when the caller says so and is refused otherwise; an option the application was created with is
 * never replaced: adding a feature that would is refused.</p>
 *
 * <p>The keys of the YAML configuration files are written in an order that does not depend on the
 * features, see {@link YamlKeyOrder}. A project that has the keys of such a file in another order,
 * because an earlier build of the generator wrote them so or because they were moved, keeps its
 * order: the two generated versions of the file are put into the order of the project's before they
 * are compared, so that the keys of the feature arrive among those that are there and nothing
 * moves.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class FeatureAddition {

    /**
     * Gives what tells whether a directory is the user's own where the file system has no POSIX
     * attributes, as on Windows, for the directories the records of changes are kept in below the home
     * directory of the user. Whose a file is there takes the access token of the process to say, which
     * the command line reads and this library does not; until a caller gives it, no owner is looked
     * at on such a system, and on the others the owner and the modes are, whatever is given here.
     *
     * @param owned given a directory, whether the user who runs the command owns it; {@code null}
     * for no such check
     */
    static void setOwnedWithoutModes(Predicate<Path> owned) {
        ProjectTransaction.setOwnedWithoutModes(owned)
    }

    /** The option of the create commands that leaves features out of an application, as a message names it. */
    public static final String EXCLUDE_OPTION = '--exclude-features'

    private static final int MAX_NAMED = 10
    private static final int MAX_MESSAGE = 1000
    private static final int MAX_SCANNED = 20000
    private static final int MAX_SCANNED_LINE = 2000
    private static final String BUILD_FILE = 'build.gradle'
    private static final String CONFIGURATION_DIRECTORY = 'grails-app/conf/'
    private static final Pattern DEPENDENCY = Pattern.compile(/^\s*([A-Za-z][A-Za-z0-9]*)[ (]+["']([^:"'\s]+:[^:"'\s]+)(?::[^"']*)?["']/)
    private static final Pattern TOP_LEVEL_KEY = Pattern.compile(/([A-Za-z0-9_.-]+):(\s.*)?/)
    private static final int MAX_PLACES = 5
    private static final int MAX_QUOTED = 8
    private static final int MAX_QUOTED_LINE = 160

    private final ProjectGenerator generator
    private final Path home

    /**
     * @param generator the generator of the applications
     */
    FeatureAddition(ProjectGenerator generator) {
        this(generator, Path.of(System.getProperty('user.home') ?: ''))
    }

    /**
     * @param generator the generator of the applications
     * @param home the home directory of whoever runs the command. The journal of a change is kept in
     * it while the change is made, under {@code .grails/forge/transactions}, which is how a change
     * that was interrupted is found again: what a project comes with never says that there is a
     * change to undo. It has to be an absolute path of a directory of the user; with another, nothing
     * is looked for and nothing is changed
     */
    FeatureAddition(ProjectGenerator generator, Path home) {
        this.generator = generator
        this.home = home
    }

    /**
     * Ends a change of the project that this user began and that was interrupted, which has to be
     * done before the project is changed again. Every file of the change is looked at first. When
     * all of them are what the change wrote, the change is complete and stays. Otherwise the files it
     * wrote are put back; a file it wrote whose content, or whose mode where the file system has
     * modes, was changed since stops that, and then nothing is put back. A file that is changed while
     * the files are put back stops it there: the files that were put back stay so, the file is left
     * as it is, and the rest is put back by a later call once the file is what it was before the
     * change or what the change made of it.
     *
     * @param projectDirectory the directory of the project
     * @return what was done; nothing, when no change was interrupted
     * @throws ProjectChangeException when the change cannot be undone, with the files and what to do
     */
    ProjectRecovery recover(Path projectDirectory) {
        return recover(projectDirectory) { String path -> }
    }

    /**
     * Whether a change of the project that this user began was interrupted and left the project half
     * changed, which {@link #recover(Path)} ends. The record of the project can then be half written
     * too, and not read until the change is ended: a change writes the lock of the record before its
     * manifest.
     *
     * @param projectDirectory the directory of the project
     * @return whether the project is half changed by a change of this user; {@code false} also where
     * the home directory cannot keep the records of changes, where none is looked for
     * @since 9.0
     */
    boolean isHalfChanged(Path projectDirectory) {
        return ProjectTransaction.isHalfMade(projectDirectory, home)
    }

    /**
     * @param putBack told the path of each file once it is put back. What it throws is not handled:
     * the rest is then put back when this is called again
     * @see #recover(Path)
     */
    ProjectRecovery recover(Path projectDirectory, Consumer<String> putBack) {
        return ProjectTransaction.recover(projectDirectory, home, putBack)
    }

    /**
     * The options that are given for the features that are added, by the name of the feature and then of
     * the option, as a feature pack declares them as {@code parameters}. An option of a feature the
     * application has is refused: those are what its record says.
     */
    Map<String, Map<String, String>> featureOptions = [:]

    /**
     * Works out what adding features to a project comes to. Nothing is written.
     *
     * @param projectDirectory the directory of the project
     * @param names the names of the features to add
     * @param operatingSystem the operating system the generator is told of, as when an application is created
     * @param console where the features say what they have to say
     * @throws ProjectStateException when the state of the project cannot be used
     * @throws FeatureSelectionException when the application cannot have the features, as when it is created with them; a
     * {@link FeatureRequestException}, which says why with a code, when they cannot be added as they were asked for
     * @throws ProjectChangeException when features cannot be added to this project, or not these; {@link ProjectChangeException#getReason()}
     * says why with a code where the project is one that no feature is added to as it is
     */
    ChangePlan plan(Path projectDirectory, List<String> names, @Nullable OperatingSystem operatingSystem, ConsoleOutput console) {
        return plan(projectDirectory, names, operatingSystem, console, false)
    }

    /**
     * @param replace whether a feature that was asked for by name makes way for a feature of its
     * exclusive group that is added, as a default does. The application and its record are then those
     * of an application that was created with the one in the place of the other. Without it, such an
     * addition is refused. It changes nothing when no feature that was asked for is in the way
     * @throws FeatureSelectionException also when a feature that would make way is required by a
     * feature the application keeps or gets, and when two features of one exclusive group are asked
     * for, whatever the application has: a {@link FeatureRequestException} each
     * @see #plan(Path, List, OperatingSystem, ConsoleOutput)
     */
    ChangePlan plan(Path projectDirectory, List<String> names, @Nullable OperatingSystem operatingSystem, ConsoleOutput console, boolean replace) {
        return plan(projectDirectory, names, operatingSystem, console, replace, Collections.<Pack>emptyList())
    }

    /**
     * @param packs the feature packs that are not part of Grails the application was generated with,
     * given again: each has to be the pack the lock records, see {@link #recordedPacks}. A feature is
     * not added from them: they are what the application has
     * @see #plan(Path, List, OperatingSystem, ConsoleOutput, boolean)
     */
    ChangePlan plan(Path projectDirectory, List<String> names, @Nullable OperatingSystem operatingSystem, ConsoleOutput console, boolean replace, List<Pack> packs) {
        return plan(projectDirectory, names, operatingSystem, console, replace, packs, Collections.<Pack>emptyList())
    }

    /**
     * @param added the feature packs that are not part of Grails whose features are to be added: each
     * is the pack of a feature that is named, or of one that a feature which is added requires. The
     * application is then generated with them as one that is created with them is, and its record
     * names each as the record of such an application does: by the coordinates and the SHA-256 of
     * its jar, or by the digest of its content. A preset is not added, and neither is a pack whose
     * feature the application has
     * @throws FeatureSelectionException also for a preset among {@code added}: a {@link FeatureRequestException}
     * @throws ProjectChangeException also for a pack among {@code added} whose feature the application
     * has, or does not get with the features that are added ({@link ProjectChangeException#PACK_DIFFERS})
     * @see #plan(Path, List, OperatingSystem, ConsoleOutput, boolean, List)
     * @since 9.0
     */
    ChangePlan plan(Path projectDirectory, List<String> names, @Nullable OperatingSystem operatingSystem, ConsoleOutput console, boolean replace, List<Pack> packs,
                    List<Pack> added) {
        ProjectState state = replayable(projectDirectory)
        ProjectManifest manifest = state.manifest
        ProjectLock lock = state.lock
        for (String feature : featureOptions.keySet()) {
            // what the application has it keeps as it was created: an option of a feature it has is of its record
            if (lock.featureNames.contains(feature)) {
                throw new FeatureSelectionException("An option was given for the feature ${FeatureSelectionException.shown(feature)}, which the application has: " +
                        'adding a feature does not change what the application was created with, and the options of its features are those of its record')
            }
        }
        List<Pack> ofTheLock = recordedPacks(lock, manifest.preset, packs)
        if (names.isEmpty()) {
            throw new FeatureSelectionException('Name the feature to add')
        }
        List<Pack> withAdded = ofTheLock + notOfTheApplication(lock, added)
        AvailableFeatures available = generator.availableFeatures(manifest.applicationType, withAdded)
        List<String> asked = []
        for (String name : names) {
            if (name != null && name.contains(':')) {
                throw new FeatureRequestException("${FeatureSelectionException.shown(name)} is no name of a feature: the feature of a plugin is added by its name, " +
                        'with the feature pack of the plugin, which feature add takes by the coordinates of the plugin', FeatureRequestException.PLUGIN_COORDINATES)
            }
            Feature feature = name == null ? null : available.findFeature(name).orElse(null)
            // a name that is no feature is refused by the selection, in the words it has for it
            String canonical = feature == null ? String.valueOf(name) : feature.getName()
            if (!asked.contains(canonical)) {
                asked << canonical
            }
        }
        // The request is looked at as a whole before what the application has is set aside: a feature
        // the application has is no choice of its own among the others, so that the same request
        // gives the same application whatever the application had
        notContradictory(available, asked, replace)
        List<String> alreadyThere = asked.findAll { String name -> lock.featureNames.contains(name) }
        List<String> toAdd = asked.findAll { String name -> !lock.featureNames.contains(name) }
        if (toAdd.isEmpty()) {
            return new ChangePlan(projectDirectory, home, [], alreadyThere, [], [], [], null, [], [], [], [])
        }
        // what the application was created without stays out of it: the selection would refuse it as selected and excluded at once
        List<String> leftOut = leftOut(available, manifest, toAdd)
        if (!leftOut.isEmpty()) {
            throw new FeatureSelectionException("${leftOut.size() == 1 ? 'The feature' : 'The features'} ${named(leftOut)} ${leftOut.size() == 1 ? 'was' : 'were'} " +
                    "left out with ${EXCLUDE_OPTION} when the application was created: adding a feature does not change what the application was created with")
        }

        // The record as it was read, and not as it is now: the plan is worked out from what was read,
        // and is written only to a project whose record is still that
        byte[] manifestBytes = state.content(ProjectState.MANIFEST)
        byte[] lockBytes = state.content(ProjectState.LOCK)
        byte[] indexBytes = state.content(ProjectState.BASELINE_INDEX)
        List<String> recorded = manifest.requestedFeatures*.name
        // a feature that was asked for by name makes way when that was said, and is no more one that was asked for
        Map<String, String> inThePlace = inThePlaceOfOnesAskedFor(available, recorded, toAdd)
        List<String> making = inThePlace.keySet().toList()
        List<String> selected = recorded.findAll { String name -> !making.contains(name) } + toAdd
        // before the option is named as the way: it is none for a feature that cannot make way
        notRequiredByWhatStays(available, selected, making)
        if (!making.isEmpty() && !replace) {
            throw new FeatureRequestException(notReplaced(available, inThePlace, toAdd), FeatureRequestException.REPLACE_REQUIRED)
        }
        Set<String> inAnotherOrder = new TreeSet<>()
        RenderedProject before = replayed(projectDirectory, state, manifestBytes, lockBytes, indexBytes, operatingSystem, inAnotherOrder, ofTheLock)

        RenderedProject after
        try {
            after = render(manifest, selected, operatingSystem, console, withAdded, true, featureOptions)
        } catch (FeatureSelectionException e) {
            if (e.getOption() == null) {
                throw e
            }
            // The feature does not agree with an option the application has. What the application
            // would be with the feature in the place of that option is worked out all the same, to
            // show it: it is refused below, because it takes away what the application has
            try {
                after = render(manifest, selected, operatingSystem, ConsoleOutput.NOOP, withAdded, false, featureOptions)
            } catch (FeatureSelectionException ignored) {
                throw e
            }
        }
        ProjectState proposed = stateOf(after)
        // a pack that was given to add its feature, and whose feature the application does not get, was given by mistake
        List<String> notAdded = added.findAll { Pack pack -> !proposed.lock.featureNames.contains(pack.descriptor.name) }*.descriptor*.name
        if (!notAdded.isEmpty()) {
            throw new ProjectChangeException("The feature ${notAdded.size() == 1 ? 'pack' : 'packs'} ${named(notAdded)} ${notAdded.size() == 1 ? 'was' : 'were'} given, and " +
                    "${notAdded.size() == 1 ? 'its feature is' : 'their features are'} not among the features that are added: a feature pack is given to add its feature, " +
                    'or again when the application has it. Nothing was changed', ProjectChangeException.PACK_DIFFERS, null)
        }
        List<String> changedOptions = changedOptions(state.manifest.options, proposed.manifest.options)
        List<String> has = proposed.lock.featureNames
        List<String> lostFeatures = state.lock.featureNames.findAll { String name -> !has.contains(name) }
        List<String> lostFiles = state.baseline.files.findAll { BaselineFile file -> proposed.baseline.find(file.path) == null }*.path
        // A feature the application has without having been asked for it, by default, makes way for
        // the feature that is added. What the application was created with by name does not: an
        // option, and a feature that was asked for, unless it is one of those that were to be replaced
        List<String> askedFor = lostFeatures.findAll { String name -> recorded.contains(name) && !making.contains(name) }
        String refusal = !changedOptions.isEmpty() ? refusal(toAdd, changedOptions, lostFeatures) : askedFor.isEmpty() ? null : refusal(toAdd, [], askedFor)
        List<String> had = lock.featureNames
        List<String> gained = proposed.lock.featureNames.findAll { String name -> !had.contains(name) }

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
        // the record the tools of earlier versions read, which a project can have beside its state, is
        // no file the generator writes: it is not looked at, and stays as it is
        writes << new Write(ProjectState.BASELINE_INDEX, after.content(ProjectState.BASELINE_INDEX), null, ProjectTransaction.sha256(indexBytes))
        writes << new Write(ProjectState.LOCK, after.content(ProjectState.LOCK), null, ProjectTransaction.sha256(lockBytes))
        // the manifest last, as when the application was generated
        writes << new Write(ProjectState.MANIFEST, after.content(ProjectState.MANIFEST), null, ProjectTransaction.sha256(manifestBytes))

        // A file only the feature that makes way wrote goes with it, and its copy in the baseline
        // does. What a feature that is refused would take away is shown with what it would add
        for (String path : lostFiles) {
            BaselineFile generated = state.baseline.find(path)
            Working working = working(projectDirectory, path)
            if (refusal != null) {
                files << new PlannedFile(path, Action.REMOVE, null, generated.binary, working.content, new byte[0], null, before.content(path), new byte[0], [])
                continue
            }
            PlannedFile removed = removed(path, generated.binary, working, before.content(path))
            if (removed != null) {
                files << removed
            }
            writes.add(0, new Write("${ProjectState.BASELINE_FILES}/${BaselineIndex.copyPath(path)}".toString(), null, null, generated.sha256))
        }
        List<String> replaced = refusal == null ? lostFeatures : Collections.<String>emptyList()
        return new ChangePlan(projectDirectory, home, toAdd, alreadyThere, gained,
                files.sort(false) { PlannedFile file -> file.path }, writes, refusal, refusal == null ? Collections.<String>emptyList() : lostFeatures, changedOptions,
                replaced, replaced.collect { String name -> replacement(available, name, gained, toAdd, making) })
    }

    /**
     * The features that were asked for by name, when the application was created or when they were
     * added, whose place a feature that is added would take: of an exclusive group, an application
     * has one.
     *
     * @return the feature that would make way and the one that would take its place, in the order of the record
     */
    private static Map<String, String> inThePlaceOfOnesAskedFor(AvailableFeatures available, List<String> recorded, List<String> toAdd) {
        Map<String, String> inThePlace = new LinkedHashMap<>()
        for (String asked : recorded) {
            Feature other = available.findFeature(asked, true).orElse(null)
            if (!(other instanceof OneOfFeature)) {
                continue
            }
            String group = ((OneOfFeature) other).getGroup()
            // a name that is no feature that can be asked for is refused by the selection, in the words it has for it
            String by = toAdd.find { String name ->
                Feature feature = available.findFeature(name).orElse(null)
                feature instanceof OneOfFeature && feature.getName() != other.getName() && ((OneOfFeature) feature).getGroup() == group
            }
            if (by != null) {
                inThePlace.put(asked, by)
            }
        }
        return inThePlace
    }

    /**
     * Two features of one exclusive group that are asked for at once contradict each other, whichever
     * of them the application has: the request is refused, as creating an application with both is.
     * {@code replace} puts a feature in the place of one the application has, and does not choose
     * between the features of one request.
     */
    private static void notContradictory(AvailableFeatures available, List<String> asked, boolean replace) {
        Map<String, List<String>> byGroup = new LinkedHashMap<>()
        for (String name : asked) {
            // a default that cannot be asked for by name, as sitemesh3, is no less of its group
            Feature feature = available.findFeature(name, true).orElse(null)
            if (feature instanceof OneOfFeature) {
                byGroup.computeIfAbsent(((OneOfFeature) feature).getGroup()) { String group -> new ArrayList<String>() }.add(feature.getName())
            }
        }
        Map.Entry<String, List<String>> contradiction = byGroup.find { String group, List<String> members -> members.size() > 1 }
        if (contradiction == null) {
            return
        }
        List<String> names = contradiction.value
        String listed = names.size() == 2 ? "${names[0]} and ${names[1]}" : "${named(names.subList(0, names.size() - 1))} and ${names.last()}"
        throw new FeatureRequestException("The features ${listed} are ${names.size() == 2 ? 'both' : 'all'} of the group ${contradiction.key}, and an application " +
                'has one feature of such a group: name one of them. ' +
                (replace ? '--replace puts a feature in the place of one the application has; it does not choose between features named together. ' : '') +
                'Nothing was changed', FeatureRequestException.CONTRADICTORY_REQUEST)
    }

    // A feature that was asked for by name is replaced when that is said, and not by adding another alone
    private static String notReplaced(AvailableFeatures available, Map<String, String> inThePlace, List<String> toAdd) {
        List<String> asked = inThePlace.keySet().toList()
        boolean one = asked.size() == 1
        List<String> each = inThePlace.collect { String name, String by -> "${by} would take the place of ${name} (both are of the group ${groupOf(available, name)})".toString() }
        return "The application has the ${one ? 'feature' : 'features'} ${named(asked)}, which ${one ? 'was' : 'were'} asked for by name, and " +
                "${named(each)}: an application has one feature of such a group. A feature that was asked for is replaced only with --replace: " +
                "feature add ${toAdd.join(',')} --replace takes ${named(asked)} out of the application and its record, as if the application had been created without " +
                "${one ? 'it' : 'them'}, and what you changed in the lines that go is a conflict. Nothing was changed"
    }

    /**
     * A feature that makes way cannot be one that a feature of the application requires: the
     * selection would bring it back, beside the feature that was to take its place.
     */
    private static void notRequiredByWhatStays(AvailableFeatures available, List<String> selected, List<String> making) {
        if (making.isEmpty()) {
            return
        }
        for (String from : selected) {
            Map<String, String> requiredBy = new HashMap<>()
            Deque<String> pending = new ArrayDeque<>([from])
            while (!pending.isEmpty()) {
                String name = pending.pop()
                Feature feature = available.findFeature(name, true).orElse(null)
                for (String required : (feature == null ? Collections.<String>emptyList() : feature.getDependentFeatures())) {
                    if (required == from || requiredBy.containsKey(required)) {
                        continue
                    }
                    requiredBy.put(required, name)
                    if (making.contains(required)) {
                        List<String> way = []
                        for (String step = required; step != null; step = requiredBy.get(step)) {
                            way.add(0, step)
                        }
                        throw new FeatureRequestException("The feature ${required} is not replaced: the feature ${from}, which the application has or gets, requires it " +
                                "(${FeatureSelectionException.chain(way)}), and an application has one feature of the group ${groupOf(available, required)}. " +
                                "An application without ${from} can have another feature in the place of ${required}. Nothing was changed",
                                FeatureRequestException.REQUIRED_BY_ANOTHER_FEATURE)
                    }
                    pending.push(required)
                }
            }
        }
    }

    private static String groupOf(AvailableFeatures available, String name) {
        Feature feature = available.findFeature(name, true).orElse(null)
        return feature instanceof OneOfFeature ? ((OneOfFeature) feature).getGroup() : ''
    }

    // what is said of a feature that makes way: which feature takes its place, when one does
    private static String replacement(AvailableFeatures available, String name, List<String> gained, List<String> toAdd, List<String> askedFor) {
        Feature feature = available.findFeature(name, true).orElse(null)
        if (feature instanceof OneOfFeature) {
            String group = ((OneOfFeature) feature).getGroup()
            String by = gained.find { String each ->
                Feature other = available.findFeature(each, true).orElse(null)
                other instanceof OneOfFeature && ((OneOfFeature) other).getGroup() == group
            }
            if (by != null) {
                return askedFor.contains(name) ? "${by} replaces ${name}, which was asked for by name, in the group ${group}".toString() :
                        "${by} replaces ${name}, the default of the group ${group}".toString()
            }
        }
        String does = "${named(toAdd)} ${toAdd.size() == 1 ? 'takes' : 'take'} out ${name}"
        if (!askedFor.isEmpty()) {
            return "${does}, which the application does not have with ${toAdd.size() == 1 ? 'it' : 'them'} in the place of ${named(askedFor)}".toString()
        }
        return "${does}, which the application had by default and does not have with ${toAdd.size() == 1 ? 'it' : 'them'}".toString()
    }

    /**
     * What becomes of a file that is not generated with the features: it is removed when the project
     * has it as it was generated, and nothing is done when the project does not have it any more.
     *
     * @return what is to be done, or {@code null} when the project has no such file
     */
    private static PlannedFile removed(String path, boolean binary, Working working, byte[] base) {
        if (working.obstacle != null) {
            return conflict(path, "the feature takes this file out of the application, and in the project ${working.obstacle}", binary, working, base, new byte[0], working.advice)
        }
        if (working.content == null) {
            return null
        }
        if (!(binary ? Arrays.equals(working.content, base) : ThreeWayMerge.sameText(working.content, base))) {
            return conflict(path, 'the feature takes this file out of the application, and it was changed in the project', binary, working, base, new byte[0],
                    [("Remove the file, or put it back as it was generated (its copy is ${ProjectState.BASELINE_FILES}/${BaselineIndex.copyPath(path)}), and add the feature again; " +
                             'what you changed in it is for you to keep elsewhere').toString()])
        }
        return new PlannedFile(path, Action.REMOVE, null, binary, working.content, new byte[0], null, base, new byte[0], [])
    }

    /**
     * Generates the application of a project again as its state records it, in memory, and holds the
     * result to the state: the files have to be those of the baseline and the manifest, the lock and
     * the index those of the project, byte for byte. Nothing is written. It is the first half of
     * adding a feature, and refuses what that refuses, in the same words.
     *
     * @param projectDirectory the directory of the project
     * @param operatingSystem the operating system the generator is told of, as when an application is created
     * @throws ProjectStateException when the state of the project cannot be used
     * @throws ProjectChangeException when the project cannot be generated again as it was
     */
    RegeneratedProject regenerate(Path projectDirectory, @Nullable OperatingSystem operatingSystem) {
        return regenerate(projectDirectory, operatingSystem, Collections.<Pack>emptyList())
    }

    /**
     * @param packs the feature packs that are not part of Grails the application was generated with,
     * given again: each has to be the pack the lock records, see {@link #recordedPacks}
     * @see #regenerate(Path, OperatingSystem)
     */
    RegeneratedProject regenerate(Path projectDirectory, @Nullable OperatingSystem operatingSystem, List<Pack> packs) {
        ProjectState state = replayable(projectDirectory)
        List<Pack> ofTheLock = recordedPacks(state.lock, state.manifest.preset, packs)
        RenderedProject rendered = replayed(projectDirectory, state, state.content(ProjectState.MANIFEST), state.content(ProjectState.LOCK),
                state.content(ProjectState.BASELINE_INDEX), operatingSystem, null, ofTheLock)
        return new RegeneratedProject(rendered, state)
    }

    /**
     * The feature packs that are not part of Grails an application was generated with, as they were
     * given again, held to what the lock records of each: the pack of a jar of a repository has to be
     * of the coordinates and the version, and of the SHA-256, the lock has, and is then the pack of
     * that jar as the lock has it, whichever repository or file it was read from now; a pack that was
     * given as a directory or a file has to have the digest of its content the lock has. Every such
     * feature of the lock needs its pack, and a pack the lock has no feature of is not taken. The
     * preset an application was made from is such a pack, and is named a preset.
     *
     * @param lock the lock of the application
     * @param preset the name of the preset the application was made from, as its manifest has it, or {@code null}
     * @param given the packs that were given
     * @return the packs, as the generator is to be given them to generate the application as it was
     * @throws ProjectChangeException when a pack is missing ({@link ProjectChangeException#PACK_MISSING}),
     * or is another than the one the lock records or one it has none of ({@link ProjectChangeException#PACK_DIFFERS})
     */
    static List<Pack> recordedPacks(ProjectLock lock, String preset, List<Pack> given) {
        List<LockedFeature> ofPacks = lock.features.findAll { LockedFeature feature -> feature.source == LockedFeature.PACK }
        for (Pack pack : given) {
            if (!ofPacks.any { LockedFeature feature -> feature.name == pack.descriptor.name }) {
                throw new ProjectChangeException("The feature pack ${pack.descriptor.name} was given, and the application has no feature of it: a feature pack is given " +
                        'again only when the application has its feature',
                        ProjectChangeException.PACK_DIFFERS, null)
            }
        }
        List<String> missing = []
        List<String> unlike = []
        List<Pack> packs = []
        boolean presetMissing = false
        for (LockedFeature feature : ofPacks) {
            Pack pack = given.find { Pack each -> each.descriptor.name == feature.name }
            String ofJar = feature.coordinates == null ? null : "${feature.coordinates}:${feature.version}".toString()
            String kind = feature.name == preset ? 'preset' : 'feature pack'
            if (pack == null) {
                presetMissing = presetMissing || feature.name == preset
                missing << (ofJar != null ? "${feature.name}, the ${kind} of the jar ${ofJar} with the SHA-256 ${feature.sha256}" :
                        "${feature.name}, a ${kind} that was given as a directory or a file" +
                                (feature.contentSha256 == null ? '' : " whose content has the SHA-256 ${feature.contentSha256}")).toString()
            } else if (ofJar != null) {
                if (pack.artifact == null || pack.artifact.sha256 != feature.sha256 || pack.artifact.coordinates != ofJar) {
                    unlike << ("${feature.name} is the ${kind} of the jar ${ofJar} with the SHA-256 ${feature.sha256}, and the one that was given is " +
                            (pack.artifact == null ? 'no jar of a repository' : "${pack.artifact.coordinates} with the SHA-256 ${pack.artifact.sha256}")).toString()
                    continue
                }
                // the jar the lock records, wherever it was read from now: the lock says where it was found, and the digest that it is the same
                PackArtifact recorded = new PackArtifact(feature.name, ofJar, feature.origin, pack.artifact.file, feature.sha256, pack.artifact.checkedAgainst, true)
                packs << pack.withArtifact(feature.catalogSha256 == null ? recorded : recorded.withCatalog(feature.catalog, feature.catalogSha256))
            } else if (pack.artifact != null) {
                unlike << "${feature.name} is a ${kind} that was given as a directory or a file, and the one that was given is the jar ${pack.artifact.coordinates}".toString()
            } else if (feature.contentSha256 != null && pack.contentSha256 != feature.contentSha256) {
                unlike << ("${feature.name} is a ${kind} whose content has the SHA-256 ${feature.contentSha256}, and the content of the one that was given, " +
                        "${pack.origin}, has ${pack.contentSha256 ?: 'none that was read'}").toString()
            } else {
                packs << pack
            }
        }
        if (!unlike.isEmpty()) {
            throw new ProjectChangeException(bounded("The feature ${unlike.size() == 1 ? 'pack that was given is' : 'packs that were given are'} not ${unlike.size() == 1 ? 'the one' : 'those'} " +
                    "the application was generated with, so it is not generated again with ${unlike.size() == 1 ? 'it' : 'them'}: ${unlike.take(MAX_NAMED).join('; ')}. " +
                    'Nothing was changed. Give the pack the application has, which its record names by its digest'), ProjectChangeException.PACK_DIFFERS, null)
        }
        if (!missing.isEmpty()) {
            String what = presetMissing ? "The application was made from the preset ${preset}${missing.size() > 1 ? ' and has the features of feature packs' : ''}, which " +
                    "${missing.size() == 1 ? 'is' : 'are'} not part of Grails, and ${missing.size() == 1 ? 'that preset was' : 'those were'} not given" :
                    "The application has the ${missing.size() == 1 ? 'feature' : 'features'} of feature packs that are not part of Grails, and " +
                            "${missing.size() == 1 ? 'that pack was' : 'those packs were'} not given"
            throw new ProjectChangeException(bounded("${what}: such an application is generated again, to add a feature to it or to compare it, only with the very " +
                    "${presetMissing ? 'preset and packs' : 'packs'} it was generated with. ${missing.take(MAX_NAMED).join('; ')}. Nothing was changed. The jar of a " +
                    'plugin is fetched again by its coordinates from the repositories, and a pack of a directory is given with --feature-pack-dir'),
                    ProjectChangeException.PACK_MISSING, null)
        }
        return packs
    }

    private static String bounded(String message) {
        return message.length() > MAX_MESSAGE * 2 ? message.substring(0, MAX_MESSAGE * 2) + '...' : message
    }

    /**
     * The feature packs whose features are to be added, which are none the application has: a preset
     * is what an application is created from, and a pack whose feature the application has is
     * given again as the pack of the application, and is not put in its place by another.
     */
    private static List<Pack> notOfTheApplication(ProjectLock lock, List<Pack> added) {
        for (Pack pack : added) {
            String name = pack.descriptor.name
            if (pack.descriptor.preset) {
                throw new FeatureRequestException("${FeatureSelectionException.shown(name)} is a preset, and a preset is not added to an application: an application is " +
                        'made from a preset when it is created, with --preset, which gives it the options and the features of the preset. Nothing was changed',
                        FeatureRequestException.PRESET)
            }
            LockedFeature has = lock.features.find { LockedFeature feature -> feature.name == name }
            if (has != null) {
                String ofJar = has.coordinates == null ? null : "${has.coordinates}:${has.version}".toString()
                throw new ProjectChangeException("The application has the feature ${name} already" + (has.source != LockedFeature.PACK ? ', which is a feature of Grails' :
                        ofJar != null ? ", of the feature pack of the jar ${ofJar} with the SHA-256 ${has.sha256}" : ', of a feature pack that was given as a directory or a file') +
                        ', and the pack that was given to add it' + (pack.artifact == null ? '' : ", ${pack.artifact.coordinates} with the SHA-256 ${pack.artifact.sha256},") +
                        ' is another: a feature pack is not put in the place of the one an application has by a command. Nothing was changed',
                        ProjectChangeException.PACK_DIFFERS, null)
            }
        }
        return added
    }

    // the state of a project that can be generated again by this generator
    private ProjectState replayable(Path projectDirectory) {
        if (ProjectTransaction.isHalfMade(projectDirectory, home)) {
            throw new ProjectChangeException('An earlier change of the project was interrupted; it is undone when a feature is added without --dry-run and --diff, ' +
                    'and has to be before anything else is done', ProjectChangeException.INTERRUPTED_CHANGE, null)
        }
        ProjectState state = ProjectState.read(projectDirectory)
        ProjectManifest manifest = state.manifest
        ProjectLock lock = state.lock
        // said before the baseline is looked at: such a record has none, and that is not what is wrong with it
        if (!manifest.hasBaseline()) {
            throw new ProjectChangeException('The record of the application was written for an application that was there, and not by the generator (generationState in ' +
                    "${ProjectState.MANIFEST} is ${ProjectManifest.UNTRACKED}, as project migrate writes it). There is no baseline the generator wrote, and without one what a " +
                    'feature changes cannot be told from what the application changed: no feature is added to such an application by a command, and it is not generated ' +
                    'again. Nothing was changed. project migrate --force writes the record with a baseline once it is told what it could not work out, which it names; ' +
                    'until then, add to the build and the configuration by hand what the feature adds to an application that is created with it',
                    ProjectChangeException.UNTRACKED_RECORD, null)
        }
        state.verifyBaseline(projectDirectory)
        if (!RecordMigration.isThisVersion(lock)) {
            String generated = "The application was generated by ${lock.generatorName} ${lock.generatorVersion} for Grails ${lock.grailsVersion}, and this is " +
                    "${ProjectLock.GENERATOR} ${VersionInfo.getVersion()} for Grails ${VersionInfo.getGrailsVersion()}: the application is generated again, to add a " +
                    'feature to it or to compare it, by the generator whose record it has'
            // a record of an earlier version is moved to this one; the command that moves it is the caller's to name
            throw new ProjectChangeException(RecordMigration.isMovable(lock) ? "${generated}, and its record is moved to this version first. Nothing was changed" :
                    "${generated}. Use the command line of Grails ${lock.generatorVersion}, which wrote the record. Nothing was changed", ProjectChangeException.OTHER_GENERATOR, null)
        }
        return state
    }

    /**
     * The application as its state records it, which has to come to that very state.
     *
     * @param inAnotherOrder {@code null} when every file has to be the file of the baseline, byte for
     * byte. Otherwise a YAML file whose keys the baseline has in another order than the generator
     * writes them in, and that is the same file otherwise, is taken for the file of the baseline, and
     * its path is added to this set: an earlier build of the generator wrote the keys in the order the
     * features gave them in
     */
    private RenderedProject replayed(Path projectDirectory, ProjectState state, byte[] manifestBytes, byte[] lockBytes, byte[] indexBytes, @Nullable OperatingSystem operatingSystem,
                                     @Nullable Set<String> inAnotherOrder, List<Pack> packs) {
        RenderedProject before
        try {
            before = render(state.manifest, state.manifest.requestedFeatures*.name, operatingSystem, ConsoleOutput.NOOP, packs)
        } catch (ProjectChangeException e) {
            throw e
        } catch (Exception e) {
            throw new ProjectChangeException("The application cannot be generated again as it was recorded: ${line(e)}", ProjectChangeException.NOT_REGENERABLE, e)
        }
        List<String> unlike = unlike(state.baseline, stateOf(before).baseline)
        if (inAnotherOrder != null) {
            for (String path : unlike) {
                BaselineFile generated = state.baseline.find(path)
                byte[] now = before.content(path)
                if (generated == null || now == null || !isYaml(path)) {
                    continue
                }
                // the copy the index that was read names, and not one another command has written since
                byte[] copy = bytes(projectDirectory, "${ProjectState.BASELINE_FILES}/${BaselineIndex.copyPath(path)}".toString())
                if (ProjectTransaction.sha256(copy) == generated.sha256 && sameButForOrder(copy, now)) {
                    inAnotherOrder.add(path)
                }
            }
            unlike = unlike.findAll { String path -> !inAnotherOrder.contains(path) }
        }
        if (!unlike.isEmpty()) {
            throw new ProjectChangeException("The application cannot be generated again as it was: ${unlike.size()} ${unlike.size() == 1 ? 'file is' : 'files are'} " +
                    "not what the baseline of the project has (${named(unlike)}). A feature is added to what the same generator makes of the same choices; " +
                    "the repositories given with GRAILS_REPO_URL are among those choices${anotherBuild(state.lock)}", ProjectChangeException.BASELINE_DIFFERS, null)
        }
        // the index has the digest of every file, and the lock that of the index, so both are others
        // when a file has its keys in another order; every file of the index was compared above. Another
        // build of the same version that generates the same files writes the same record but for its identity
        boolean reordered = inAnotherOrder != null && !inAnotherOrder.isEmpty()
        if (!Arrays.equals(before.content(ProjectState.MANIFEST), manifestBytes) ||
                !(reordered ? sameLockButForTheIndex(before.content(ProjectState.LOCK), lockBytes) : ProjectLock.sameButForTheBuild(before.content(ProjectState.LOCK), lockBytes)) ||
                (!reordered && !Arrays.equals(before.content(ProjectState.BASELINE_INDEX), indexBytes))) {
            throw new ProjectChangeException("The record of the application under ${ProjectState.DIRECTORY} is not what this generator writes for the same choices, " +
                    "so a feature cannot be added to it; the repositories given with GRAILS_REPO_URL are among those choices${anotherBuild(state.lock)}", ProjectChangeException.RECORD_DIFFERS, null)
        }
        return before
    }

    // a lock ends with the digest of the index and its seal, which is the digest of what is before it
    private static boolean sameLockButForTheIndex(byte[] one, byte[] other) {
        String first = ProjectLock.withoutTheBuild(one)
        String second = ProjectLock.withoutTheBuild(other)
        int firstEnd = first.lastIndexOf('\nbaselineIndex:\n')
        int secondEnd = second.lastIndexOf('\nbaselineIndex:\n')
        return firstEnd >= 0 && secondEnd >= 0 && first.substring(0, firstEnd) == second.substring(0, secondEnd)
    }

    // what is said of a record that another build of this version wrote, when it does not come to the same files
    private static String anotherBuild(ProjectLock lock) {
        String running = VersionInfo.getGeneratorSha256()
        if (lock.generatorSha256 == null || running == null || lock.generatorSha256 == running) {
            return ''
        }
        return ". The record was written by another build of ${ProjectLock.GENERATOR} ${lock.generatorVersion} than this one (the generator sha256 " +
                "${lock.generatorSha256} there, ${running} here), which does not generate the same files: use the build that generated the application, or move " +
                'the record to this build'
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

    // What the application was created with by name is not taken away by adding to it. What would be, and how to have it
    private static String refusal(List<String> toAdd, List<String> changedOptions, List<String> lostFeatures) {
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
        return "${adding} would take the ${lostFeatures.size() == 1 ? 'feature' : 'features'} ${named(lostFeatures)} out of the application, which " +
                "${lostFeatures.size() == 1 ? 'was' : 'were'} asked for when it was created: a feature that was asked for is not replaced by adding another.${how}"
    }

    private RenderedProject render(ProjectManifest manifest, List<String> features, OperatingSystem operatingSystem, ConsoleOutput console, List<Pack> packs,
                                   boolean optionsAreChoices = true, Map<String, Map<String, String>> optionsOfAddedFeatures = [:]) {
        Project project = NameUtils.parse(manifest.defaultPackage + '.' + manifest.name)
        GeneratorContext context = context(manifest, features, operatingSystem, console, packs, optionsAreChoices, optionsOfAddedFeatures)
        RenderedProject rendered = new RenderedProject()
        generator.generate(manifest.applicationType, project, rendered, context)
        // The record of an application that was there says so in every version of it: the generator
        // writes the record of an application it generates, and here it is that of one it did not
        if (manifest.isAdopted() && rendered.content(ProjectState.MANIFEST) != null && rendered.content(ProjectState.LOCK) != null) {
            AdoptedProjectRecord.adopted(rendered.content(ProjectState.MANIFEST), rendered.content(ProjectState.LOCK)).each { String path, byte[] content ->
                rendered.replace(path, content)
            }
        }
        return rendered
    }

    // what the generator selects for the choices of a manifest and the features that are asked for
    private GeneratorContext context(ProjectManifest manifest, List<String> features, OperatingSystem operatingSystem, ConsoleOutput console, List<Pack> packs,
                                     boolean optionsAreChoices, Map<String, Map<String, String>> optionsOfAddedFeatures = [:]) {
        Project project = NameUtils.parse(manifest.defaultPackage + '.' + manifest.name)
        GenerationOptions recorded = manifest.options
        // The options of the manifest are what the application has, so each of them is a choice: a
        // feature that would put another servlet container, reloader or Grails Data implementation in
        // the place of the one the application has is refused, and replaces nothing
        Options options = new Options(recorded.reloading, recorded.data, recorded.servlet, recorded.jdk, operatingSystem)
                // the options the features were given are those of the record, and those of the features that are added
                .withFeatureOptions(recorded.featureOptions + optionsOfAddedFeatures)
                .withGiven(optionsAreChoices ? EnumSet.allOf(OptionName) : EnumSet.noneOf(OptionName))
                // an application that was created without the files of the root of a build stays without them
                .withSubproject(recorded.subproject)
        return generator.createGeneratorContext(manifest.applicationType, project, options, operatingSystem, features, console, packs,
                manifest.excludedFeatures*.name)
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
        List<String> notes = []
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
            // a file of keys that the project has in another order is compared in the order of the project
            byte[][] inItsOrder = isYaml(path) ? inTheOrderOf(working.content, base, proposed) : null
            if (inItsOrder != null) {
                base = inItsOrder[0]
                proposed = inItsOrder[1]
                notes << 'the keys of this file are in another order than the generator writes them in; they stay where they are, and what the feature changes is put among them'
            }
            if (ThreeWayMerge.sameText(working.content, proposed)) {
                return new PlannedFile(path, Action.ADOPT, null, binary, working.content, working.content, null, base, proposed, [])
            }
            ThreeWayMerge merge = ThreeWayMerge.of(base, working.content, proposed)
            if (merge.isTooLarge()) {
                return conflict(path, "the feature changes this file, and the file of the project has more lines than are merged (${ThreeWayMerge.MAX_LINES}) or differs " +
                        "from the generated one in more than one place and in more lines than are merged (${ThreeWayMerge.MAX_CHANGED})", binary, working, base, proposed,
                        [("Put the file aside, put the generated one in its place (its copy is ${ProjectState.BASELINE_FILES}/${BaselineIndex.copyPath(path)}), add the feature, " +
                                 'and bring your changes into the new file').toString()])
            }
            if (merge.getConflicts() > 0) {
                boolean elsewhere = merge.getConflictingChanges().every { ThreeWayMerge.Conflict place -> place.elsewhere }
                return conflict(path, elsewhere ? "the project and the feature both moved or added ${merge.getConflicts() == 1 ? 'a line' : "${merge.getConflicts()} lines"} of this file, " +
                        'each to another place' : "the feature changes ${merge.getConflicts() == 1 ? 'lines' : "${merge.getConflicts()} places"} of this file that were changed " +
                        'in the project too, or lines next to them', binary, working, base, proposed, places(merge))
            }
            result = merge.getMerged()
            edited = !ThreeWayMerge.sameText(working.content, base)
            if (merge.getChangedAlike() > 0) {
                notes << ("your change and the feature's are the same in ${merge.getChangedAlike() == 1 ? 'one place' : "${merge.getChangedAlike()} places"} of this file, " +
                        'and what both have there is there once').toString()
            }
            // The merge compares lines. What a line means in the two files that nearly everything of a
            // feature goes into is looked at once more, in the result: for a word to whoever adds the
            // feature, never for a change of the file
            if (edited && path == BUILD_FILE) {
                List<String> twice = declaredTwice(result) - declaredTwice(working.content) - declaredTwice(proposed)
                for (String dependency : twice.take(MAX_PLACES)) {
                    notes << ("the file now declares ${dependency} more than once: the project and the feature each have a line for it, and the merge, which " +
                            'compares lines, cannot know that the two are one dependency. Keep one of them').toString()
                }
            }
            if (edited && path.startsWith(CONFIGURATION_DIRECTORY) && isYaml(path)) {
                PlannedFile twice = setTwice(path, working, base, proposed, result)
                if (twice != null) {
                    return twice
                }
            }
        }
        // whether a file can be executed is one value: the project has it as it was generated, or as
        // the feature makes it
        Boolean executable = posix && baseExecutable != proposedExecutable && working.executable != proposedExecutable ? Boolean.valueOf(proposedExecutable) : null
        if (executable == null && Arrays.equals(result, working.content)) {
            return new PlannedFile(path, Action.ADOPT, null, binary, working.content, working.content, null, base, proposed, [])
        }
        return new PlannedFile(path, edited ? Action.MERGE : Action.UPDATE, null, binary, working.content, result, executable, base, proposed, notes)
    }

    private static boolean isYaml(String path) {
        return path.endsWith('.yml') || path.endsWith('.yaml')
    }

    // one document that is a map, read without making anything but maps, lists and values of it
    private static Map<?, ?> yamlOf(byte[] content) {
        try {
            Object read = new Yaml(new SafeConstructor(new LoaderOptions())).load(new String(content, StandardCharsets.UTF_8))
            return read instanceof Map ? (Map<?, ?>) read : null
        } catch (YAMLException | StackOverflowError ignored) {
            return null
        }
    }

    // whether a file is what the generator writes for its content, so that the content can be
    // written again in another order of its keys and nothing else of it changes
    private static boolean isWrittenByTheGenerator(byte[] content, Map<?, ?> read) {
        try {
            return ThreeWayMerge.sameText(YamlTemplate.dump((Map<String, Object>) read).getBytes(StandardCharsets.UTF_8), content)
        } catch (YAMLException | ClassCastException ignored) {
            return false
        }
    }

    /**
     * @return whether two YAML files have the same content with their keys in another order, one of
     * them, the second, being what the generator writes
     */
    private static boolean sameButForOrder(byte[] one, byte[] generated) {
        Map<?, ?> first = yamlOf(one)
        Map<?, ?> second = yamlOf(generated)
        return first != null && second != null && first == second && isWrittenByTheGenerator(generated, second) && isWrittenByTheGenerator(one, first)
    }

    /**
     * The two generated versions of a YAML file with their keys in the order the file of the project
     * has them in, see {@link YamlKeyOrder#following}: a line of the project then has the same lines
     * around it in all three, and what the features change is the only difference between the two.
     *
     * @return the file as it was generated and as it is generated with the features, in that order,
     * or {@code null} when the project has the keys in the order of the generator, or when one of the
     * three is not a file of keys that can be written in another order
     */
    private static byte[][] inTheOrderOf(byte[] working, byte[] base, byte[] proposed) {
        Map<?, ?> has = yamlOf(working)
        Map<?, ?> was = yamlOf(base)
        Map<?, ?> becomes = yamlOf(proposed)
        if (has == null || was == null || becomes == null || !isWrittenByTheGenerator(base, was) || !isWrittenByTheGenerator(proposed, becomes)) {
            return null
        }
        byte[] wasInOrder = YamlTemplate.dump(YamlKeyOrder.following(was, has)).getBytes(StandardCharsets.UTF_8)
        if (Arrays.equals(wasInOrder, base)) {
            return null
        }
        return [wasInOrder, YamlTemplate.dump(YamlKeyOrder.following(becomes, has)).getBytes(StandardCharsets.UTF_8)] as byte[][]
    }

    /**
     * The dependencies a build file declares more than once for one configuration, by a scan of the
     * lines of its {@code dependencies} blocks for the forms {@code implementation "group:artifact"}
     * and {@code implementation "group:artifact:version"}, with either kind of quote and with or
     * without parentheses. It is for a note, and reads no more than {@link #MAX_SCANNED} lines.
     *
     * @return each as {@code group:artifact for configuration}, in the order of the file
     */
    private static List<String> declaredTwice(byte[] buildFile) {
        Map<String, Integer> declared = new LinkedHashMap<>()
        boolean inBlock = false
        for (String line : new String(buildFile, StandardCharsets.UTF_8).split('\n', MAX_SCANNED + 1).toList().take(MAX_SCANNED)) {
            if (line.startsWith('dependencies {')) {
                inBlock = true
            } else if (line.startsWith('}')) {
                inBlock = false
            } else if (inBlock && line.length() <= MAX_SCANNED_LINE) {
                Matcher dependency = DEPENDENCY.matcher(line)
                if (dependency.find()) {
                    declared.merge("${dependency.group(2)} for ${dependency.group(1)}".toString(), 1) { Integer one, Integer other -> one + other }
                }
            }
        }
        return declared.findAll { String dependency, Integer times -> times > 1 }.keySet().toList()
    }

    /**
     * The merge compares lines. What the merged configuration file sets is looked at once more, as
     * YAML and Grails read it, see {@link RepeatedSettings}: a key or a setting that the merged file
     * has twice, and that neither the file of the project nor that of the feature has twice, is one
     * the two each have, and the file is not written with both. Neither is a merged file that is not
     * YAML any more when the two it was merged from are. A file of the project that is not YAML that
     * reads is looked at by its lines, for a key at the beginning of a line.
     *
     * @return the conflict, or {@code null} when there is none
     */
    private static PlannedFile setTwice(String path, Working working, byte[] base, byte[] proposed, byte[] result) {
        RepeatedSettings mine = RepeatedSettings.of(working.content)
        RepeatedSettings theirs = RepeatedSettings.of(proposed)
        List<String> keys
        List<String> settings = []
        if (mine == null || theirs == null) {
            keys = keysTwice(result) - keysTwice(working.content) - keysTwice(proposed)
        } else {
            RepeatedSettings merged = RepeatedSettings.of(result)
            if (merged == null) {
                return conflict(path, 'the file of the project and the file of the feature are YAML, and merged by their lines they are not', false, working, base, proposed,
                        [("Put the file aside, put the generated one in its place (its copy is ${ProjectState.BASELINE_FILES}/${BaselineIndex.copyPath(path)}), add the feature, " +
                                 'and bring your changes into the new file').toString()])
            }
            keys = merged.topLevelKeysBeyond(mine, theirs)
            settings = merged.settingsBeyond(mine, theirs)
        }
        if (!keys.isEmpty()) {
            return conflict(path, "the project and the feature each have the top-level ${keys.size() == 1 ? 'key' : 'keys'} ${named(keys)} in one document of this file, " +
                    'in different places', false, working, base, proposed,
                    [("Merged, the file would have ${keys.size() == 1 ? 'that key' : 'those keys'} twice, and only one of the two would count. Put what the project has under " +
                             "${keys.size() == 1 ? 'it' : 'them'} where the generated file has ${keys.size() == 1 ? 'the key' : 'the keys'}, or take it out, add the feature " +
                             'again, and put it back under the one key').toString()])
        }
        if (!settings.isEmpty()) {
            boolean one = settings.size() == 1
            return conflict(path, "the project and the feature each have the ${one ? 'setting' : 'settings'} ${named(settings)} in one document of this file, " +
                    'in different places or written in different ways', false, working, base, proposed,
                    [("Merged, the file would have ${one ? 'that setting' : 'those settings'} twice, as a key with dots in it and as nested keys or twice under one key, " +
                             "and only one of the two would count. Take ${one ? 'it' : 'them'} out of the file of the project, add the feature again, and give the one " +
                             "${one ? 'setting' : 'place of each setting'} the value it is to have").toString()])
        }
        return null
    }

    /**
     * The top-level keys a YAML file has more than once in one of its documents, by a scan of its
     * lines for a key at the beginning of a line; a line of {@code ---} begins a document. It reads no
     * more than {@link #MAX_SCANNED} lines. It is what is left to do for a file that is not YAML that reads.
     */
    private static List<String> keysTwice(byte[] yaml) {
        List<String> twice = []
        Set<String> ofDocument = new HashSet<>()
        for (String line : new String(yaml, StandardCharsets.UTF_8).split('\n', MAX_SCANNED + 1).toList().take(MAX_SCANNED)) {
            String text = line.endsWith('\r') ? line.substring(0, line.length() - 1) : line
            if (text.startsWith('---')) {
                ofDocument.clear()
            } else if (text.length() <= MAX_SCANNED_LINE) {
                Matcher key = TOP_LEVEL_KEY.matcher(text)
                if (key.matches() && !ofDocument.add(key.group(1)) && !twice.contains(key.group(1))) {
                    twice << key.group(1)
                }
            }
        }
        return twice
    }

    private static PlannedFile conflict(String path, String reason, boolean binary, Working working, byte[] base, byte[] proposed, List<String> details) {
        return new PlannedFile(path, Action.CONFLICT, reason, binary, working.content, null, null, base, proposed, details)
    }

    // Each place the two changed, with the lines the generated file, the project and the feature have
    // there and nothing that the three do not show, and what to do about it
    private static List<String> places(ThreeWayMerge merge) {
        List<String> lines = []
        boolean elsewhere = false
        boolean samePlace = false
        for (ThreeWayMerge.Conflict place : merge.getConflictingChanges().take(MAX_PLACES)) {
            if (place.elsewhere) {
                elsewhere = true
                lines << ((place.line > 0 ? "line ${place.line} of the project" : 'a line of the feature') + ' is a line the project and the feature both moved or added, ' +
                        'each to another place; merged, the file would have it more often, or less often, than any of them has it:').toString()
                lines.addAll(quoted(place.current.isEmpty() ? (place.proposed.isEmpty() ? place.base : place.proposed) : place.current))
                continue
            }
            samePlace = true
            lines << "at line ${place.line} the generated file had ${counted(place.base)}".toString()
            lines.addAll(quoted(place.base))
            lines << "the project has ${counted(place.current, ' there')}".toString()
            lines.addAll(quoted(place.current))
            lines << "and the feature has ${counted(place.proposed, ' there')}".toString()
            lines.addAll(quoted(place.proposed))
        }
        if (merge.getConflicts() > MAX_PLACES) {
            lines << "and ${merge.getConflicts() - MAX_PLACES} more".toString()
        }
        if (samePlace) {
            lines << 'Both changed the same place, so neither is taken for the other. Take the lines of the project out of that place, or move them past a line that ' +
                    'neither changed, add the feature again, and then put them where they belong'
        }
        if (elsewhere) {
            lines << 'A line both have in another place than the generated file is to be in one place: put it in the file of the project where the feature has it, ' +
                    'or where the generated file had it, and add the feature again'
        }
        return lines
    }

    private static String counted(List<String> lines, String where = '') {
        return lines.isEmpty() ? "no lines${where}" : "${lines.size() == 1 ? '1 line' : "${lines.size()} lines"}${where}:"
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
            throw new ProjectChangeException("${path} cannot be read (${e.getClass().getSimpleName()}); nothing was changed", ProjectChangeException.UNREADABLE_FILE, e)
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

    /**
     * @param available the features of the application
     * @param manifest the manifest of the application
     * @param names the names of features
     * @return those of the features that the application was created without, as {@link #EXCLUDE_OPTION} asked
     */
    static List<String> leftOut(AvailableFeatures available, ProjectManifest manifest, List<String> names) {
        Set<String> excluded = manifest.excludedFeatures.collect { RequestedFeature each ->
            Feature feature = available.findFeature(each.name, true).orElse(null)
            return feature == null ? each.name : feature.getName()
        } as Set<String>
        return names.findAll { String name -> excluded.contains(name) }
    }

    private static String named(List<String> names) {
        return names.take(MAX_NAMED).join(', ') + (names.size() > MAX_NAMED ? " and ${names.size() - MAX_NAMED} more" : '')
    }

    private static String line(Exception e) {
        String message = (e.getMessage() ?: e.getClass().getSimpleName()).replaceAll(/[\p{Cc}\p{Cf}\p{Zl}\p{Zp}]/, ' ')
        return message.length() > MAX_MESSAGE ? message.substring(0, MAX_MESSAGE) + '...' : message
    }

    /**
     * Moves the record of an application that another generator wrote to this one: Forge of an
     * earlier version, or another build of this version that generates other files. The application
     * is generated again in memory, by this generator, from the choices the record has: the type, the
     * name and the package, the options, the features that were asked for and those that were left
     * out, and the feature packs that are not part of Grails and the preset, which are given again
     * and held to the record as they are when a feature is added. The baseline, its index, the lock
     * and the manifest are then what this generator writes for those choices, and name this generator.
     * No other file is written: where a file of the application is not what this generator makes of
     * the choices, as where the earlier version generated it otherwise, a feature that is added later
     * takes it for a change of the application. Nothing is written here, see {@link RecordMigration#apply()}.
     *
     * @param projectDirectory the directory of the project
     * @param operatingSystem the operating system the generator is told of, as when an application is created
     * @param packs the feature packs that are not part of Grails the application was generated with,
     * given again, see {@link #recordedPacks}
     * @return what moving the record comes to, which is nothing when this build wrote it, or another
     * build of this version that writes the same record for its choices
     * @throws ProjectStateException when the record cannot be read, or its baseline is not what its index says
     * @throws ProjectChangeException when the record is not moved: a change of the project was
     * interrupted ({@link ProjectChangeException#INTERRUPTED_CHANGE}), the record was written for an
     * application that was there ({@link ProjectChangeException#UNTRACKED_RECORD}), by a later version
     * or by another generator than Forge ({@link ProjectChangeException#OTHER_GENERATOR}), a pack is
     * missing or is another ({@link ProjectChangeException#PACK_MISSING}, {@link ProjectChangeException#PACK_DIFFERS}),
     * this generator does not have a feature the record names or cannot generate the application from
     * its choices ({@link ProjectChangeException#NOT_REGENERABLE}), or it gives another record for them,
     * as with other repositories ({@link ProjectChangeException#RECORD_DIFFERS})
     * @since 9.0
     */
    RecordMigration migrate(Path projectDirectory, @Nullable OperatingSystem operatingSystem, List<Pack> packs) {
        if (ProjectTransaction.isHalfMade(projectDirectory, home)) {
            throw new ProjectChangeException('An earlier change of the project was interrupted; it is undone when the record is moved, or a feature is added, without ' +
                    '--dry-run, and has to be before anything else is done', ProjectChangeException.INTERRUPTED_CHANGE, null)
        }
        ProjectState state = ProjectState.read(projectDirectory)
        ProjectManifest manifest = state.manifest
        ProjectLock lock = state.lock
        // The record as it was read here, before anything is worked out from it. Generating the
        // application takes time, and a feature that another command adds meanwhile writes another
        // record: read again afterwards, that record would be taken for the one the move was worked
        // out from, and be overwritten by a baseline that knows nothing of the feature
        byte[] manifestBytes = state.content(ProjectState.MANIFEST)
        byte[] lockBytes = state.content(ProjectState.LOCK)
        byte[] indexBytes = state.content(ProjectState.BASELINE_INDEX)
        if (!manifest.hasBaseline()) {
            throw new ProjectChangeException("The record of the application was written for an application that was there (generationState in ${ProjectState.MANIFEST} " +
                    "is ${ProjectManifest.UNTRACKED}): it has no baseline the generator wrote, and there is nothing of a generator to move. Nothing was changed",
                    ProjectChangeException.UNTRACKED_RECORD, null)
        }
        // what is moved is a record that reads, the copies of the baseline with it: they are the ones the change replaces
        state.verifyBaseline(projectDirectory)
        String notMovable = RecordMigration.notMovable(lock)
        if (notMovable != null) {
            throw new ProjectChangeException("${notMovable}. Nothing was changed", ProjectChangeException.OTHER_GENERATOR, null)
        }
        boolean thisVersion = RecordMigration.isThisVersion(lock)
        if (thisVersion && lock.generatorSha256 != null && lock.generatorSha256 == VersionInfo.getGeneratorSha256()) {
            return new RecordMigration(RecordMigration.Outcome.SAME_BUILD, lock, projectDirectory, home, [], [], [], [])
        }
        List<Pack> ofTheLock = recordedPacks(lock, manifest.preset, packs)
        List<String> requested = manifest.requestedFeatures*.name
        AvailableFeatures available = generator.availableFeatures(manifest.applicationType, ofTheLock)
        List<String> unknown = (requested + manifest.excludedFeatures*.name).findAll { String name -> !available.findFeature(name, true).isPresent() }.unique()
        if (!unknown.isEmpty()) {
            throw new ProjectChangeException("The record ${unknown.size() == 1 ? 'names the feature' : 'names the features'} ${named(unknown)}, which this version of " +
                    "Grails does not have for an application of the type ${manifest.applicationType.name().toLowerCase(Locale.ROOT).replace('_', '-')}: ${unknown.size() == 1 ? 'it was' : 'they were'} " +
                    'taken out of Grails or renamed, and the application is not generated by this version from its record. The upgrade notes of Grails say what ' +
                    "became of ${unknown.size() == 1 ? 'it' : 'them'}. Until then the application is generated again by the command line of Grails " +
                    "${lock.generatorVersion}, which wrote the record. Nothing was changed", ProjectChangeException.NOT_REGENERABLE, null)
        }
        RenderedProject rendered
        try {
            rendered = render(manifest, requested, operatingSystem, ConsoleOutput.NOOP, ofTheLock)
        } catch (ProjectChangeException e) {
            throw e
        } catch (Exception e) {
            throw new ProjectChangeException("This version of Grails does not generate the application from the choices its record has: ${line(e)}. The record is not " +
                    "moved; the command line of Grails ${lock.generatorVersion}, which wrote it, generates the application again. Nothing was changed",
                    ProjectChangeException.NOT_REGENERABLE, e)
        }
        ProjectState proposed = stateOf(rendered)
        String unlike = otherChoices(state, proposed.manifest, proposed.lock)
        if (unlike != null) {
            throw new ProjectChangeException("${unlike}. Nothing was changed", unlike.contains('GRAILS_REPO_URL') ? ProjectChangeException.RECORD_DIFFERS :
                    ProjectChangeException.NOT_REGENERABLE, null)
        }
        // another build of this version that writes the same record but for its own identity is the same generator to the record
        if (thisVersion && Arrays.equals(rendered.content(ProjectState.MANIFEST), manifestBytes) &&
                ProjectLock.sameButForTheBuild(rendered.content(ProjectState.LOCK), lockBytes) && Arrays.equals(rendered.content(ProjectState.BASELINE_INDEX), indexBytes)) {
            return new RecordMigration(RecordMigration.Outcome.SAME_FILES, lock, projectDirectory, home, [], [], [], [])
        }
        List<String> added = []
        List<String> removed = []
        List<String> changed = []
        List<Write> writes = []
        for (BaselineFile file : proposed.baseline.files) {
            BaselineFile generated = state.baseline.find(file.path)
            if (generated == null) {
                added << file.path
            } else if (generated.sha256 != file.sha256 || generated.kind != file.kind || generated.executable != file.executable) {
                changed << file.path
            }
            if (generated == null || generated.sha256 != file.sha256) {
                String copy = "${ProjectState.BASELINE_FILES}/${BaselineIndex.copyPath(file.path)}".toString()
                writes << new Write(copy, rendered.content(copy), null, generated == null ? null : generated.sha256)
            }
        }
        // the copies of the files this generator does not write go first, so that a copy of another
        // name that a file system takes for the same is written after it
        for (BaselineFile file : state.baseline.files) {
            if (proposed.baseline.find(file.path) == null) {
                removed << file.path
                writes.add(0, new Write("${ProjectState.BASELINE_FILES}/${BaselineIndex.copyPath(file.path)}".toString(), null, null, file.sha256))
            }
        }
        writes << new Write(ProjectState.BASELINE_INDEX, rendered.content(ProjectState.BASELINE_INDEX), null, ProjectTransaction.sha256(indexBytes))
        writes << new Write(ProjectState.LOCK, rendered.content(ProjectState.LOCK), null, ProjectTransaction.sha256(lockBytes))
        // the manifest last, as when the application was generated
        writes << new Write(ProjectState.MANIFEST, rendered.content(ProjectState.MANIFEST), null, ProjectTransaction.sha256(manifestBytes))
        return new RecordMigration(RecordMigration.Outcome.MOVED, lock, projectDirectory, home, added, removed, changed, writes)
    }

    /**
     * Works out the record of an application that was there before it had one, with a baseline: the
     * application is generated in memory, by this generator, from what it is taken to be, and the
     * baseline, its index, the lock and the manifest are what the generator writes for that, the
     * manifest with {@code generationState: adopted}. No file of the application is changed: where
     * the application is not what the generator makes of the choices, a feature that is added later
     * takes it for a change of the application, and where such a change is in the lines a feature
     * changes, adding the feature is a conflict and writes nothing. Nothing is written here, see
     * {@link RecordAdoption#apply()}.
     *
     * <p>A project that has such a record already, or one without a baseline, gets the new one in its
     * place. The record of an application that was generated is never replaced by one that is worked
     * out: it is moved to this generator, see {@link #migrate}.</p>
     *
     * @param projectDirectory the directory of the project
     * @param applicationType the type of the application
     * @param name the name of the application, as a create command takes it
     * @param defaultPackage the package its classes are created in
     * @param options the options it is taken to have, each of which is held to as a choice
     * @param requested the names of the features that are asked for by name, as with {@code --features}
     * @param found the names of the features that were found in the files of the application. Each is
     * asked for by name only where the type, the options and the features that are asked for do not
     * bring it, so that one the application would have by default stays a default, which another
     * feature of its group replaces; one that cannot be asked for by name is not in the record
     * @param excluded the names of the features it does not have
     * @param operatingSystem the operating system the generator is told of, as when an application is created
     * @param packs the feature packs that are not part of Grails whose features are among {@code requested}
     * @throws ProjectStateException when the project has a record that cannot be read
     * @throws FeatureSelectionException when an application cannot be created with these choices
     * @throws ProjectChangeException when a change of the project was interrupted
     * ({@link ProjectChangeException#INTERRUPTED_CHANGE}), the project has the record of a generated
     * application ({@link ProjectChangeException#GENERATED_RECORD}), or the generator does not make
     * an application of the choices ({@link ProjectChangeException#NOT_REGENERABLE})
     * @since 9.0
     */
    RecordAdoption adopt(Path projectDirectory, ApplicationType applicationType, String name, String defaultPackage, GenerationOptions options, List<String> requested,
                         List<String> found, List<String> excluded, @Nullable OperatingSystem operatingSystem, List<Pack> packs) {
        if (ProjectTransaction.isHalfMade(projectDirectory, home)) {
            throw new ProjectChangeException('An earlier change of the project was interrupted; it is undone when the record is written without --dry-run, ' +
                    'and has to be before anything else is done', ProjectChangeException.INTERRUPTED_CHANGE, null)
        }
        ProjectState existing = ProjectState.exists(projectDirectory) ? ProjectState.read(projectDirectory) : null
        if (existing != null && existing.manifest.isTracked()) {
            throw new ProjectChangeException("The application has the record of how it was generated (${ProjectState.MANIFEST}), which is not written over with one " +
                    'that is worked out from its files. Nothing was changed', ProjectChangeException.GENERATED_RECORD, null)
        }
        // the copies that are replaced are those the index names, and nothing else is there
        if (existing != null && !existing.baseline.files.isEmpty()) {
            existing.verifyBaseline(projectDirectory)
        }
        List<String> asked = new ArrayList<String>(requested).unique()
        RenderedProject rendered
        try {
            if (!found.isEmpty()) {
                ProjectManifest without = new ProjectManifest(VersionInfo.getGrailsVersion(), applicationType, name, defaultPackage, options, ProjectManifest.ADOPTED,
                        asked.collect { String each -> new RequestedFeature(each) }, excluded.toSorted().unique().collect { String each -> new RequestedFeature(each) }, [:])
                Set<String> selected = context(without, asked, operatingSystem, ConsoleOutput.NOOP, packs, true).getFeatures().getFeatures()*.getName().toSet()
                AvailableFeatures available = generator.availableFeatures(applicationType, packs)
                for (String each : found) {
                    if (!selected.contains(each) && !asked.contains(each) && available.findFeature(each).isPresent()) {
                        asked << each
                    }
                }
            }
            ProjectManifest choices = new ProjectManifest(VersionInfo.getGrailsVersion(), applicationType, name, defaultPackage, options, ProjectManifest.ADOPTED,
                    asked.collect { String each -> new RequestedFeature(each) }, excluded.toSorted().unique().collect { String each -> new RequestedFeature(each) }, [:])
            rendered = render(choices, asked, operatingSystem, ConsoleOutput.NOOP, packs)
        } catch (ProjectChangeException | FeatureSelectionException e) {
            throw e
        } catch (Exception e) {
            throw new ProjectChangeException("This version of Grails does not generate an application from these choices: ${line(e)}. Nothing was changed",
                    ProjectChangeException.NOT_REGENERABLE, e)
        }
        ProjectState proposed = stateOf(rendered)
        if (proposed.manifest.options != options || proposed.manifest.applicationType != applicationType || proposed.manifest.name != name ||
                proposed.manifest.defaultPackage != defaultPackage) {
            throw new ProjectChangeException('This version of Grails generates an application from these choices with other choices' +
                    (proposed.manifest.options == options ? '' : " (${changedOptions(options, proposed.manifest.options).join(', ')})") + '. Nothing was changed',
                    ProjectChangeException.NOT_REGENERABLE, null)
        }
        List<Write> writes = []
        List<String> same = []
        List<String> otherwise = []
        List<String> absent = []
        for (BaselineFile file : proposed.baseline.files) {
            BaselineFile had = existing?.baseline?.find(file.path)
            if (had == null || had.sha256 != file.sha256) {
                String copy = "${ProjectState.BASELINE_FILES}/${BaselineIndex.copyPath(file.path)}".toString()
                writes << new Write(copy, rendered.content(copy), null, had == null ? null : had.sha256)
            }
            Working working = working(projectDirectory, file.path)
            if (working.content == null) {
                absent << file.path
            } else if (file.binary ? Arrays.equals(working.content, rendered.content(file.path)) : ThreeWayMerge.sameText(working.content, rendered.content(file.path))) {
                same << file.path
            } else {
                otherwise << file.path
            }
        }
        // the copies of the files this record does not have go first, so that a copy of another
        // name that a file system takes for the same is written after it
        for (BaselineFile file : (existing == null ? Collections.<BaselineFile>emptyList() : existing.baseline.files)) {
            if (proposed.baseline.find(file.path) == null) {
                writes.add(0, new Write("${ProjectState.BASELINE_FILES}/${BaselineIndex.copyPath(file.path)}".toString(), null, null, file.sha256))
            }
        }
        // what tells Git how to keep the record is not needed to read it, and is no file of a change: one that is there is left as it is
        Map<String, byte[]> forGit = new LinkedHashMap<>()
        for (String path : [ProjectState.IGNORE, ProjectState.ATTRIBUTES]) {
            if (!Files.exists(projectDirectory.resolve(path), LinkOption.NOFOLLOW_LINKS) && rendered.content(path) != null) {
                forGit.put(path, rendered.content(path))
            }
        }
        writes << new Write(ProjectState.BASELINE_INDEX, rendered.content(ProjectState.BASELINE_INDEX), null,
                existing == null ? null : ProjectTransaction.sha256(existing.content(ProjectState.BASELINE_INDEX)))
        writes << new Write(ProjectState.LOCK, rendered.content(ProjectState.LOCK), null, existing == null ? null : ProjectTransaction.sha256(existing.content(ProjectState.LOCK)))
        // the manifest last, as when an application is generated
        writes << new Write(ProjectState.MANIFEST, rendered.content(ProjectState.MANIFEST), null,
                existing == null ? null : ProjectTransaction.sha256(existing.content(ProjectState.MANIFEST)))
        return new RecordAdoption(proposed, projectDirectory, home, same, otherwise, absent, writes, forGit)
    }

    /**
     * What this generator made of the choices of a record that is not those choices: the generator
     * gave another selection of the features or other options for them, or the repositories of
     * {@code GRAILS_REPO_URL} are not those the application was generated with.
     *
     * @return what differs, in one sentence that ends without a full stop; {@code null} when nothing does
     */
    private static String otherChoices(ProjectState state, ProjectManifest proposed, ProjectLock proposedLock) {
        ProjectManifest manifest = state.manifest
        ProjectLock lock = state.lock
        if (shownRepositories(lock) != shownRepositories(proposedLock)) {
            List<String> had = shownRepositories(lock)
            List<String> has = shownRepositories(proposedLock)
            return "The application was generated with ${had.isEmpty() ? 'the repositories of Grails' : "the repositories of GRAILS_REPO_URL (${named(had)})"}, and " +
                    "${has.isEmpty() ? 'GRAILS_REPO_URL gives none now' : "GRAILS_REPO_URL gives others now (${named(has)})"}: the record is moved to this version " +
                    'with the choices it has, and the repositories the build is generated with are among them. Run the command with GRAILS_REPO_URL as it was when ' +
                    'the application was generated'
        }
        List<String> differ = []
        if (proposed.applicationType != manifest.applicationType || proposed.name != manifest.name || proposed.defaultPackage != manifest.defaultPackage) {
            differ << 'the type, the name or the package'
        }
        if (proposed.options != manifest.options) {
            differ << "the options (${changedOptions(manifest.options, proposed.options).join(', ')})".toString()
        }
        if (requestedOf(proposed.requestedFeatures) != requestedOf(manifest.requestedFeatures)) {
            List<String> gone = manifest.requestedFeatures*.name - proposed.requestedFeatures*.name
            List<String> come = proposed.requestedFeatures*.name - manifest.requestedFeatures*.name
            differ << ("the features that were asked for${gone.isEmpty() ? '' : ', without ' + named(gone)}${come.isEmpty() ? '' : ', with ' + named(come)}").toString()
        }
        if (proposed.excludedFeatures*.name != manifest.excludedFeatures*.name) {
            differ << 'the features that were left out'
        }
        if (proposed.preset != manifest.preset) {
            differ << 'the preset'
        }
        if (differ.isEmpty()) {
            return null
        }
        return "This version of Grails generates the application from the choices of its record with other choices: ${differ.join('; ')}. The record is not moved; " +
                "the command line of Grails ${lock.generatorVersion}, which wrote it, generates the application again"
    }

    // the repositories a lock names, as they are said: a directory of the machine without its path
    private static List<String> shownRepositories(ProjectLock lock) {
        List<String> shown = lock.repositories.collect { ProjectLock.LockedRepository each -> each.url == null ? each.kind : "${each.kind} ${each.url}".toString() }
        if (lock.repositoriesNotNamed > 0) {
            shown << "and ${lock.repositoriesNotNamed} more".toString()
        }
        return shown
    }

    // what was asked for, with the artifact a feature of a pack was taken by
    private static List<String> requestedOf(List<RequestedFeature> features) {
        return features.collect { RequestedFeature each -> "${each.name} ${each.coordinates} ${each.version}".toString() }
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
