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
package org.grails.forge.cli.command

import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.console.ConsoleProject
import org.apache.grails.forge.cli.console.OptionRelation
import org.apache.grails.forge.cli.console.ProjectCommand
import org.apache.grails.forge.cli.console.UsageHelp
import org.apache.grails.forge.cli.pack.FeatureOptions
import org.apache.grails.forge.cli.pack.PackArtifactException
import org.apache.grails.forge.cli.pack.PackArtifacts
import org.apache.grails.forge.cli.pack.PackCatalogs
import org.apache.grails.forge.cli.pack.PackSelection
import org.apache.grails.forge.cli.pack.RecordedPackOptions
import org.apache.grails.forge.core.bom.BomVersions
import org.apache.grails.forge.core.change.ChangePlan
import org.apache.grails.forge.core.change.ChangePlan.Action
import org.apache.grails.forge.core.change.ChangePlan.PlannedFile
import org.apache.grails.forge.core.change.FeatureAddition
import org.apache.grails.forge.core.change.ProjectChangeException
import org.apache.grails.forge.core.pack.Pack
import org.apache.grails.forge.core.pack.PackInstructions
import org.apache.grails.forge.core.state.ProjectLock
import org.apache.grails.forge.core.state.ProjectLock.LockedFeature
import org.apache.grails.forge.core.state.ProjectState
import org.apache.grails.forge.core.state.ProjectStateException
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.cli.ForgeContext
import org.grails.forge.feature.AvailableFeatures
import org.grails.forge.feature.Feature
import org.grails.forge.feature.FeatureSelectionException
import org.grails.forge.feature.grails.GrailsBase
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.FileSystemOutputHandler
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import picocli.CommandLine
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.Callable
import java.util.function.Consumer
import java.util.function.Supplier

/**
 * Adds features to the application in the current directory, which has to have the state the
 * generator wrote under {@code .grails}.
 *
 * @since 9.0
 */
@CommandLine.Command(name = FeatureAddCommand.NAME, description = [
        'Adds features to the application in the current directory: its files and its record become those of an application that was created with them. ' +
                'A feature the application already has is left as it is.',
        'A feature takes the place of one the application has by default, as postgres takes that of h2, and the command says which it replaces; ' +
                'a feature that was asked for by name, when the application was created or added to, is replaced only with --replace, ' +
                'and an option the application was created with is never replaced.',
        'The files you changed keep your changes. When a feature changes or takes out what you changed too, or a file it writes is there already, ' +
                'nothing is written and the files are named.',
        'The feature of a plugin is added by the coordinates of the plugin, group:artifact:version, whose jar is taken from a repository as the create ' +
                'commands take it; by its name with the directory of its feature pack (--feature-pack-dir); or by its name in a feature catalog. The record ' +
                'then names the pack as it does for an application that was created with it. A pack is code: use one only when you trust where it comes from.',
        'An application with the feature of a feature pack that is not part of Grails gets a feature only with that very pack: the jar of a plugin is ' +
                'fetched again by the coordinates and the SHA-256 its record has, and the pack of a directory is given again with --feature-pack-dir.'])
@Component
@Scope('prototype')
@CompileStatic
class FeatureAddCommand extends BaseCommand implements Callable<Integer>, ProjectCommand, OptionRelation.Declared {

    public static final String NAME = 'add'

    private static final Map<Action, String> ACTIONS = [(Action.ADD): 'add', (Action.UPDATE): 'update', (Action.MERGE): 'merge', (Action.ADOPT): 'keep',
                                                        (Action.CONFLICT): 'conflict', (Action.REMOVE): 'remove', (Action.LEAVE): 'leave'] as Map<Action, String>

    @CommandLine.Parameters(arity = '1..*', paramLabel = 'FEATURE', split = ',', completionCandidates = FeatureNameCandidates,
            description = 'The features to add: the names feature list lists; the coordinates of a plugin with a feature pack, group:artifact:version; ' +
                    'the name of the feature of a pack that is given with --feature-pack-dir; or a name, or a full name group:artifact#name, of a feature catalog.')
    List<String> features = new ArrayList<>()

    @CommandLine.Option(names = ['--dry-run'], description = 'Say which files would be added, changed and removed, which features would be replaced, and which conflicts there are, and change nothing.')
    boolean dryRun

    @CommandLine.Option(names = ['--replace'], description = 'Replace a feature that was asked for by name with the feature that takes its place, ' +
            'as mysql takes that of a postgres the application was created with. Without it only a feature the application has by default is replaced. ' +
            'It changes nothing when no such feature is in the way, and it does not choose between features of one exclusive group that are named together: ' +
            'those are refused.')
    boolean replace

    @CommandLine.Option(names = ['--diff'], description = 'Show the changes that would be made to the files, as a diff, and change nothing.')
    boolean diff

    @CommandLine.Option(names = ['--json'], description = 'With --dry-run: write what adding the features comes to as one JSON document for a tool to read, ' +
            'and nothing else: the outcome, the reason, the files with their paths in the project and what would be done to each, and whether the change can be made.')
    boolean json

    @CommandLine.Option(names = ['--feature-option'], paramLabel = 'FEATURE.NAME=VALUE',
            description = 'An option of a feature that is added, which the feature pack of the feature declares: the name of the feature, a dot, the ' +
                    'name of the option and its value. An option that is not given has the value the pack gives it. The options of the features the ' +
                    'application has stay what its record says. Can be given more than once.')
    List<String> featureOptions = new ArrayList<>()

    @CommandLine.Mixin
    RecordedPackOptions packOptions = new RecordedPackOptions()

    @CommandLine.Option(names = ['--feature-pack-sha256'], paramLabel = 'COORDINATES=SHA256',
            description = 'The SHA-256 the jar of a plugin that is added by its coordinates has to have, as group:artifact:version=sha256. ' +
                    'A jar with another one is refused before it is opened. Can be given more than once.')
    List<String> featurePackDigests = new ArrayList<>()

    @CommandLine.Option(names = ['--feature-catalog'], paramLabel = 'URL',
            description = 'A feature catalog, in which a name that is neither a feature of Grails nor that of a feature pack that was given is ' +
                    'looked up, as the create commands look it up: the catalog says which plugin has the feature, in which version and with which ' +
                    'SHA-256. The address is https, or file: for a file; none is for no catalog. Without the option, the catalogs of the ' +
                    'GRAILS_FORGE_CATALOG environment variable are read, else the plugin index the GRAILS_PLUGIN_INDEX environment variable names, ' +
                    'and without either none. Can be given more than once.')
    List<String> featureCatalogs = new ArrayList<>()

    @CommandLine.Option(names = ['--refresh'],
            description = 'Read each feature catalog anew, and not the copy of it that is kept on this computer for a day, and fetch ' +
                    'the jar of a plugin that is added, and not the copy of it that is kept on this computer')
    boolean refresh

    // asked of the Forge application when the command first needs it
    private final Supplier<ProjectGenerator> projectGeneratorSource

    private PackArtifacts packArtifacts
    // how the packs that are asked for are taken, as the create commands take them
    private PackSelection packSelection
    // the packs whose features are added, which are said before anything is written
    private List<Pack> addedPacks = []

    @Autowired
    FeatureAddCommand(ProjectGenerator projectGenerator) {
        this.projectGeneratorSource = { projectGenerator } as Supplier<ProjectGenerator>
    }

    /** On a Forge application that is started when the command first needs it. */
    FeatureAddCommand(ForgeContext context) {
        this.projectGeneratorSource = { context.get().getBean(ProjectGenerator) } as Supplier<ProjectGenerator>
    }

    @Override
    List<OptionRelation> getOptionRelations() {
        // what call checks first
        return [OptionRelation.requires('--json', '--dry-run'), OptionRelation.exclusive(false, '--json', '--diff')]
    }

    @Override
    Integer call() throws Exception {
        if (json && (!dryRun || diff)) {
            throw new CommandLine.ParameterException(spec.commandLine(), '--json writes what --dry-run says as one JSON document: give it with --dry-run, and without --diff')
        }
        // the project the current directory is in, so that the command does the same in every directory of it
        Path current = FileSystemOutputHandler.getDefaultBaseDirectory().toPath()
        if (json) {
            return document(current)
        }
        ConsoleProject.Search search = ConsoleProject.search(current)
        if (search.project == null) {
            err(search.none('feature add'))
            return 1
        }
        Path directory = search.project == null ? current : search.project.directory
        boolean preview = dryRun || diff
        try {
            if (!ProjectState.exists(directory)) {
                throw new ProjectStateException(noRecord(spec))
            }
            FeatureAddition addition = new FeatureAddition(projectGeneratorSource.get())
            addition.featureOptions = optionsOfFeatures()
            for (String message : (preview ? Collections.<String>emptyList() : addition.recover(directory).getMessages())) {
                out("@|blue ||@ ${message}")
            }
            List<Pack> packs = packOptions.packs(directory, packArtifacts(), grailsRepoUrl(), { String line -> out("@|blue ||@ ${shown(line)}") } as Consumer<String>,
                    { String line -> warning(shown(line)) } as Consumer<String>)
            Asked asked = asked(directory, packs, { String line -> out(line) } as Consumer<String>, { String line -> warning(line) } as Consumer<String>)
            ChangePlan plan = addition.plan(directory, asked.features, getOperatingSystem(), this, replace, asked.ofTheApplication, asked.added)
            heldToItsBom(directory, plan)
            addedPacks = asked.added.findAll { Pack pack -> plan.added.contains(pack.descriptor.name) }
            int exit = apply(plan, preview)
            if (exit == 0 && !preview && !plan.isNothingToAdd()) {
                // what the packs of the features that were added say is to be done, those that were added with them among them
                CreateCommand.said(this, instructions(directory, plan, asked.ofTheApplication + asked.added))
            }
            return exit
        } catch (FeatureSelectionException e) {
            // features an application cannot have are a mistake in the command line, as they are when it is created
            throw new CommandLine.ParameterException(spec.commandLine(), shown(e.getMessage()) + (packSelection == null ? '' : packSelection.getNotInCatalog()))
        } catch (ProjectStateException | ProjectChangeException | IllegalArgumentException | PackArtifactException e) {
            // a record that another version wrote is moved to this one by a command that is named
            err(shown(ProjectMigrateCommand.withMove(spec, directory, e)))
            if (showStacktrace()) {
                e.printStackTrace(spec.commandLine().getErr())
            }
            return 1
        }
    }

    private Map<String, Map<String, String>> optionsOfFeatures() {
        try {
            return FeatureOptions.parse(featureOptions)
        } catch (IllegalArgumentException e) {
            throw new CommandLine.ParameterException(spec.commandLine(), e.getMessage())
        }
    }

    static String noRecord(CommandLine.Model.CommandSpec spec) {
        return "There is no application here that a feature can be added to: ${ProjectState.MANIFEST} is missing. " +
                'Run the command in the directory of an application that Grails 9 created. For an application that was there before, ' +
                "${UsageHelp.toType(spec, "${ProjectGroupCommand.NAME} ${ProjectMigrateCommand.NAME}")} writes the record, with the baseline a feature is added against"
    }

    /**
     * What {@code --dry-run} says, as one JSON document and nothing else. The exit code is the one the
     * command ends with without {@code --json}: 2 for features that cannot be added as they were
     * asked for, 1 for a conflict and any other refusal or failure, and 0 otherwise.
     */
    private Integer document(Path current) {
        ConsoleProject.Search search = ConsoleProject.search(current)
        if (search.project == null) {
            return written(ChangeDocument.ofFeatureAdd(null).noProject(search.untrusted, search.none('feature add')), 1)
        }
        Path directory = search.project.directory
        ChangeDocument document = ChangeDocument.ofFeatureAdd(directory)
        if (!ProjectState.exists(directory)) {
            return written(document.ended(ChangeDocument.INVALID_RECORD, ChangeDocument.NO_RECORD, noRecord(spec)), 1)
        }
        Notes notes = new Notes()
        ChangePlan plan
        List<Pack> packs
        try {
            // which jar of a pack is fetched again is said among the notes: the document is all that is written
            List<Pack> given = packOptions.packs(directory, packArtifacts(), grailsRepoUrl(), { String line -> notes.out(line) } as Consumer<String>,
                    { String line -> notes.warning(line) } as Consumer<String>)
            Asked asked = asked(directory, given, { String line -> notes.out(plain(line)) } as Consumer<String>, { String line -> notes.warning(line) } as Consumer<String>)
            packs = asked.ofTheApplication + asked.added
            FeatureAddition addition = new FeatureAddition(projectGeneratorSource.get())
            addition.featureOptions = FeatureOptions.parse(featureOptions)
            plan = addition.plan(directory, asked.features, getOperatingSystem(), notes, replace, asked.ofTheApplication, asked.added)
            heldToItsBom(directory, plan)
            // which packs the features come from is among the notes: the document is all that is written
            for (Pack pack : asked.added.findAll { Pack each -> plan.added.contains(each.descriptor.name) }) {
                for (String line : PackSelection.origin(pack, packArtifacts()) + pack.summary()) {
                    notes.out(plain(line))
                }
                String unlike = pack.artifact == null ? null : PackSelection.unlikeItsPlugin(pack, 'changed')
                if (unlike != null) {
                    notes.warning(unlike)
                }
            }
        } catch (Exception e) {
            if (showStacktrace()) {
                e.printStackTrace(spec.commandLine().getErr())
            }
            return written(document.ended(e, ProjectMigrateCommand.withMove(spec, directory, e)), e instanceof FeatureSelectionException ? 2 : 1)
        }
        List<PlannedFile> conflicts = plan.getConflicts()
        document.planned(plan, notes.lines, conflicts.isEmpty() ? null : notAdded(plan, conflicts))
        document.instructions(instructions(directory, plan, packs))
        return written(document, document.outcome in [ChangeDocument.NO_CHANGES, ChangeDocument.CHANGES] ? 0 : 1)
    }

    private int written(ChangeDocument document, int exitCode) {
        // written as it is: a document is not markup of the command line
        spec.commandLine().getOut().println(document.toJson())
        return exitCode
    }

    /**
     * What the feature packs of the features that are added say is to be done once they are: of a pack
     * of Grails and of one that is not alike.
     */
    private List<PackInstructions> instructions(Path directory, ChangePlan plan, List<Pack> packs) {
        if (plan.added.isEmpty()) {
            return []
        }
        AvailableFeatures available = projectGeneratorSource.get().availableFeatures(ProjectState.readSelection(directory).manifest.applicationType, packs)
        List<Feature> added = []
        for (String name : plan.added) {
            available.findFeature(name, true).ifPresent { Feature feature -> added << feature }
        }
        return PackInstructions.of(added)
    }

    static String notAdded(ChangePlan plan, List<PlannedFile> conflicts) {
        return "The ${plan.requested.size() == 1 ? 'feature was' : 'features were'} not added: ${conflicts.size()} ${conflicts.size() == 1 ? 'file' : 'files'} " +
                'cannot be written without losing what the application has. Nothing was changed. What is in the way is said with each file; ' +
                '--diff shows what the feature would change. Make room for it and add the feature again'
    }

    private Integer apply(ChangePlan plan, boolean preview) {
        for (String name : plan.alreadyThere) {
            out("@|blue ||@ The application already has the feature ${shown(name)}")
        }
        if (plan.isNothingToAdd()) {
            out('@|blue ||@ Nothing was changed')
            return 0
        }
        if (plan.isRefused() && !preview) {
            err(shown(plan.refusal))
            return 1
        }
        // a pack is not part of Grails: where it comes from and what it adds is said before anything is written
        for (Pack pack : addedPacks) {
            for (String line : PackSelection.origin(pack, packArtifacts())) {
                out(line)
            }
            for (String line : pack.summary()) {
                out("    ${line.replace('@|', '@ |')}")
            }
        }
        List<String> required = plan.added.findAll { String name -> !plan.requested.contains(name) }
        out("@|blue ||@ ${preview ? 'To add' : 'Adding'} the ${plan.requested.size() == 1 ? 'feature' : 'features'} ${shown(plan.requested.join(', '))}" +
                (required.isEmpty() ? '' : ", and with ${plan.requested.size() == 1 ? 'it' : 'them'} ${shown(required.join(', '))}"))
        for (String replacement : plan.replacements) {
            out("    ${shown(replacement)}")
        }
        for (PlannedFile file : plan.files) {
            out("    ${ACTIONS.get(file.action).padRight(8)} ${shown(file.path)}" +
                    (file.action == Action.MERGE ? ' (your changes are kept)' : '') +
                    (file.action == Action.ADOPT ? ' (it is what the feature makes it already)' : '') +
                    (file.action in [Action.CONFLICT, Action.LEAVE] ? ": ${shown(file.reason)}" : ''))
            for (String line : file.details) {
                out("             ${shown(line)}")
            }
        }
        for (String lost : plan.lostFeatures) {
            out("    remove   the feature ${shown(lost)}")
        }
        for (String option : plan.changedOptions) {
            out("    change   ${shown(option)}")
        }
        if (diff) {
            // what a file has is not markup of this command
            for (String line : plan.diff()) {
                spec.commandLine().getOut().println(line)
            }
        }
        if (plan.isRefused()) {
            err(shown(plan.refusal))
            return 1
        }
        List<PlannedFile> conflicts = plan.getConflicts()
        if (!conflicts.isEmpty()) {
            err(notAdded(plan, conflicts))
            return 1
        }
        if (preview) {
            out('@|blue ||@ Nothing was changed')
            return 0
        }
        plan.apply()
        out("@|blue ||@ ${plan.requested.size() == 1 ? 'Feature' : 'Features'} ${shown(plan.requested.join(', '))} added" +
                (plan.replaced.isEmpty() ? '' : ", in the place of ${shown(plan.replaced.join(', '))}") +
                ". Commit the changes together with the directory ${ProjectState.DIRECTORY}")
        if (!addedPacks.isEmpty()) {
            out('@|blue ||@ ' + CreateCommand.laterCommands(addedPacks.collect { Pack pack -> FeatureSelectionException.shown(pack.descriptor.name) }))
        }
        // of the application that was changed: one that is refused is not warned of as if it had been
        for (Pack pack : addedPacks) {
            String unlike = pack.artifact == null ? null : PackSelection.unlikeItsPlugin(pack, 'changed')
            if (unlike != null) {
                warning(unlike)
            }
        }
        return 0
    }

    /**
     * What was asked for, once the packs it needs are taken: the packs the application has, given
     * again, the packs whose features are added, and the features by their names. The pack of a
     * directory or a jar that was given is one of the application when the record has a feature of
     * its name, and one to add otherwise. A plugin that is named by its coordinates, a full name and a
     * name nothing here knows are taken as the create commands take them, see {@link PackSelection};
     * what the application has by those coordinates already is the feature it has.
     */
    Asked asked(Path directory, List<Pack> given, Consumer<String> said, Consumer<String> warned) {
        ProjectState state
        try {
            state = ProjectState.readSelection(directory, true)
        } catch (ProjectStateException ignored) {
            // what is wrong with the record is said by what adds the feature
            return new Asked(features, given, Collections.<Pack>emptyList())
        }
        ProjectLock lock = state.lock
        List<LockedFeature> ofPacks = lock.features.findAll { LockedFeature feature -> feature.source == LockedFeature.PACK }
        List<Pack> ofTheApplication = given.findAll { Pack pack -> ofPacks.any { LockedFeature feature -> feature.name == pack.descriptor.name } }
        List<Pack> toAdd = given.findAll { Pack pack -> !ofTheApplication.contains(pack) }
        // the plugin the application has the feature of, named by its coordinates or by the full name of the feature, is that feature
        List<String> named = features.collect { String feature ->
            if (feature == null) {
                return feature
            }
            LockedFeature has = null
            if (PackCatalogs.isId(feature)) {
                has = ofPacks.find { LockedFeature each -> each.coordinates == PackCatalogs.pluginOf(feature) && each.name == PackCatalogs.nameOf(feature) }
            } else if (PackArtifacts.isCoordinates(feature)) {
                has = ofPacks.find { LockedFeature each -> each.coordinates != null && "${each.coordinates}:${each.version}".toString() == feature }
            }
            return has == null ? feature : has.name
        }
        boolean nothingToTake = featurePackDigests.isEmpty() && named.every { String feature ->
            feature == null || (!PackArtifacts.isCoordinates(feature) && (isKnown(state, feature) || toAdd.any { Pack pack -> pack.descriptor.name == feature }))
        }
        if (nothingToTake) {
            return new Asked(named, ofTheApplication, toAdd)
        }
        packSelection = new PackSelection(new SelectionHost(state, named, said, warned))
        PackSelection.Taken taken = packSelection.select(toAdd, null)
        return new Asked(taken.features, ofTheApplication, taken.packs)
    }

    // a name the command knows without a pack that is taken: a feature of Grails, for whichever type of application, and a feature the application has
    private boolean isKnown(ProjectState state, String name) {
        ProjectGenerator generator = projectGeneratorSource.get()
        return state.lock.featureNames.contains(name) || ApplicationType.values().any { ApplicationType type ->
            generator.availableFeatures(type, Collections.<Pack>emptyList()).findFeature(name, true).isPresent()
        }
    }

    // the packs a catalog gave for names were chosen for the BOM the application has, which the features that are added can change
    private void heldToItsBom(Path directory, ChangePlan plan) {
        if (packSelection == null || plan.isNothingToAdd()) {
            return
        }
        List<String> has = ProjectState.readSelection(directory, true).lock.featureNames
        packSelection.heldToItsBom(GrailsBase.bomArtifactId(has.findAll { String name -> !plan.replaced.contains(name) } + plan.added),
                'Add the feature that makes it so first, so that the versions are chosen for that BOM')
    }

    private PackArtifacts packArtifacts() {
        if (packArtifacts == null) {
            packArtifacts = createPackArtifacts()
        }
        return packArtifacts
    }

    // a line as a document has it: without the markup of the command line
    private static String plain(String line) {
        return line.replace('@|blue ||@ ', '').trim()
    }

    /**
     * @return what fetches the jar of a plugin the application has the feature pack of
     */
    protected PackArtifacts createPackArtifacts() {
        return new PackArtifacts()
    }

    /**
     * @return the repositories of the build of the application, which are asked for such a jar when
     * the command names none
     */
    protected String grailsRepoUrl() {
        return System.getenv('GRAILS_REPO_URL')
    }

    /**
     * @return the feature catalogs that are read when the command names none: addresses separated by
     * semicolons, {@code none} for no catalog, or {@code null} when the environment names none
     */
    protected String featureCatalogSetting() {
        return System.getenv('GRAILS_FORGE_CATALOG')
    }

    /**
     * @return the plugin index the environment names ({@code GRAILS_PLUGIN_INDEX}): an address,
     * {@code none}, or {@code null} when it names none. It is the catalog when neither
     * {@code --feature-catalog} nor {@link #featureCatalogSetting()} names one
     */
    protected String pluginIndexSetting() {
        return System.getenv(CreateCommand.PLUGIN_INDEX_VARIABLE)
    }

    /**
     * @return whether the plugin index of Grails is read as a catalog when neither the command nor
     * the environment names one, which it is not, as for the create commands
     */
    protected boolean readsPluginIndexUnnamed() {
        return PackCatalogs.PLUGIN_INDEX_UNNAMED
    }

    /**
     * @return the home of the user, in which the copies of the feature catalogs are kept, under
     * {@code .grails/forge/catalogs}
     */
    protected Path featureCatalogHome() {
        return Path.of(System.getProperty('user.home'))
    }

    /**
     * @return how long the feature catalog nobody named can take
     */
    protected Duration defaultCatalogWithin() {
        return PackCatalogs.DEFAULT_WITHIN
    }

    /**
     * @param artifact the artifact of a BOM of Grails
     * @return the versions it manages, or {@code null} when this Forge has none for it
     */
    protected BomVersions bomVersions(String artifact) {
        return BomVersions.of(artifact)
    }

    /** What was asked for, once the packs it needs are taken. */
    static final class Asked {

        final List<String> features
        final List<Pack> ofTheApplication
        final List<Pack> added

        Asked(List<String> features, List<Pack> ofTheApplication, List<Pack> added) {
            this.features = features
            this.ofTheApplication = ofTheApplication
            this.added = added
        }
    }

    /** What the command gives the taking of the packs it adds: its options, what it says, and what it reads of the environment. */
    private final class SelectionHost implements PackSelection.Host {

        private final ProjectState state
        private final List<String> asked
        private final Consumer<String> said
        private final Consumer<String> warned

        SelectionHost(ProjectState state, List<String> asked, Consumer<String> said, Consumer<String> warned) {
            this.state = state
            this.asked = asked
            this.said = said
            this.warned = warned
        }

        @Override
        List<String> getAskedFeatures() {
            return asked
        }

        @Override
        String getAskedWith() {
            return 'to add'
        }

        @Override
        List<String> getPackRepositories() {
            return packOptions.repositories
        }

        @Override
        List<String> getPackDigests() {
            return featurePackDigests
        }

        @Override
        List<String> getPackCatalogs() {
            return featureCatalogs
        }

        @Override
        boolean isCatalogRefresh() {
            return refresh
        }

        @Override
        boolean isPackOffline() {
            return packOptions.offline
        }

        @Override
        String getApplicationTypeName() {
            return state.manifest.applicationType.getName().replace('_', '-')
        }

        @Override
        String getBomArtifact() {
            return GrailsBase.bomArtifactId(state.lock.featureNames + asked.findAll { String each -> each != null })
        }

        @Override
        boolean isKnownFeature(String name) {
            return FeatureAddCommand.this.isKnown(state, name)
        }

        @Override
        Pack notAPreset(Pack pack, String given) {
            if (pack.descriptor.preset) {
                throw new CommandLine.ParameterException(spec.commandLine(), "${FeatureSelectionException.shown(given)} has the preset ${pack.descriptor.name}, " +
                        'which is not a feature: an application is made from a preset when it is created, with --preset, and a preset is not added to one that is there')
            }
            return pack
        }

        @Override
        PackArtifacts getPackArtifacts() {
            return FeatureAddCommand.this.packArtifacts()
        }

        @Override
        String getCatalogSetting() {
            return FeatureAddCommand.this.featureCatalogSetting()
        }

        @Override
        String getPluginIndexSetting() {
            return FeatureAddCommand.this.pluginIndexSetting()
        }

        @Override
        boolean isPluginIndexReadUnnamed() {
            return FeatureAddCommand.this.readsPluginIndexUnnamed()
        }

        @Override
        Path getCatalogHome() {
            return FeatureAddCommand.this.featureCatalogHome()
        }

        @Override
        String getGrailsRepoUrl() {
            return FeatureAddCommand.this.grailsRepoUrl()
        }

        @Override
        Duration getDefaultCatalogWithin() {
            return FeatureAddCommand.this.defaultCatalogWithin()
        }

        @Override
        BomVersions bomVersionsOf(String artifact) {
            return FeatureAddCommand.this.bomVersions(artifact)
        }

        @Override
        CommandLine.Model.CommandSpec getCommandSpec() {
            return spec
        }

        @Override
        void say(String line) {
            said.accept(line)
        }

        @Override
        void warn(String line) {
            warned.accept(line)
        }
    }

    private static String shown(String text) {
        return text.replaceAll(/\p{Cntrl}/, '?').replace('@|', '@ |')
    }

    /** What the features say while the application is generated with them, kept for the document. */
    private static final class Notes implements ConsoleOutput {

        final List<String> lines = []

        @Override
        void out(String message) {
            lines << message
        }

        @Override
        void err(String message) {
            lines << message
        }

        @Override
        void warning(String message) {
            lines << message
        }

        @Override
        boolean showStacktrace() {
            return false
        }

        @Override
        boolean verbose() {
            return false
        }
    }
}
