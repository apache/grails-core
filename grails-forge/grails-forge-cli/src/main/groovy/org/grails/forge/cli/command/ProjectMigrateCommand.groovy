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
import org.apache.grails.forge.cli.artefact.ProjectFiles
import org.apache.grails.forge.cli.artefact.ProjectOutputHandler
import org.apache.grails.forge.cli.console.ConsoleProject
import org.apache.grails.forge.cli.console.EarlierGrailsProject
import org.apache.grails.forge.cli.console.GradleRunner
import org.apache.grails.forge.cli.console.OptionChoices
import org.apache.grails.forge.cli.console.ProjectCommand
import org.apache.grails.forge.cli.console.ProjectEvidence
import org.apache.grails.forge.cli.console.UsageHelp
import org.apache.grails.forge.cli.pack.PackArtifactException
import org.apache.grails.forge.cli.pack.PackArtifacts
import org.apache.grails.forge.cli.pack.RecordedPackOptions
import org.apache.grails.forge.core.change.FeatureAddition
import org.apache.grails.forge.core.change.ProjectChangeException
import org.apache.grails.forge.core.change.RecordAdoption
import org.apache.grails.forge.core.change.RecordMigration
import org.apache.grails.forge.core.pack.Pack
import org.apache.grails.forge.core.pack.PackCondition
import org.apache.grails.forge.core.pack.PackDescriptor.PackDependency
import org.apache.grails.forge.core.pack.PackFeature
import org.apache.grails.forge.core.state.AdoptedProjectRecord
import org.apache.grails.forge.core.state.GenerationOptions
import org.apache.grails.forge.core.state.ProjectLock
import org.apache.grails.forge.core.state.ProjectLock.LockedFeature
import org.apache.grails.forge.core.state.ProjectManifest
import org.apache.grails.forge.core.state.ProjectState
import org.apache.grails.forge.core.state.ProjectStateException
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.Project
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.cli.ForgeContext
import org.grails.forge.feature.AvailableFeatures
import org.grails.forge.feature.Feature
import org.grails.forge.feature.FeatureContext
import org.grails.forge.feature.FeatureSelectionException
import org.grails.forge.io.FileSystemOutputHandler
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.GormImpl
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.ServletImpl
import org.grails.forge.util.NameUtils
import org.grails.forge.util.VersionInfo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import picocli.CommandLine
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.function.Consumer
import java.util.function.Supplier

/**
 * Writes the record of the application in the current directory, for an application that has none:
 * one that the Grails shell created, one that Forge of an earlier version generated, and one that
 * was put together by hand. What the application is, is worked out from its files, see
 * {@link ProjectEvidence}, and from what is given on the command line, which counts for more.
 *
 * <p>Only the files of the record under {@code .grails} are written. The record says that it was
 * written for an application that was there, and has the baseline this version generates for what the
 * application is taken to be ({@code generationState: adopted}, see {@link FeatureAddition#adopt}):
 * the commands that create the classes of an application read it as they read the record of a
 * generated one, and a feature is added against that baseline, which takes what the application has
 * otherwise for a change of the application. Where no such baseline can be generated, as where the
 * type of the application was assumed, the record has none ({@code generationState: untracked}) and
 * no feature is added by a command until the command is given what it could not work out.</p>
 *
 * <p>The record of an application that Grails 9 generated, which an earlier version of the command
 * line wrote, or another build of this version that generates other files, is moved to this version:
 * the application is generated again from the choices the record has, by this version, and the
 * baseline and the record become what this version writes for them, see {@link FeatureAddition#migrate}.
 * No file of the application outside {@code .grails} is written.</p>
 *
 * @since 9.0
 */
@CommandLine.Command(name = ProjectMigrateCommand.NAME, description = [
        'Writes the record of the application in the current directory, for an application that has none: one that was created with the Grails shell or ' +
                'by an earlier version, or was not generated at all. The commands that create its classes are there once it has the record.',
        'What the application is, is worked out from its files, which are read and not changed: the record an earlier version wrote, the profile ' +
                'it had, the plugins and the dependencies of build.gradle, and its configuration. What was found, where, and what was assumed is said; ' +
                'the options say it otherwise. Only the directory .grails is written to.',
        'The record says that it was written for an application that was there, and has a copy of every file this version generates for an application ' +
                'of the type, the options and the features that were found or given: feature add adds a feature against that copy, takes what the application ' +
                'has otherwise for a change of yours, and writes nothing where a feature changes the same lines. Where the type was assumed, or this version does ' +
                'not generate an application from what was found, the record has no such copy and no feature is added until the command is told what it could not find.',
        'Run again with --force, the command reads the files again and keeps the features the record names, those that feature add added among them; ' +
                '--exclude-features takes one out, and the options and --exclude-features are worked out again or given again.',
        'An application that Grails 9 generated has the record of how it was generated. When an earlier version of the command line wrote it, the command ' +
                'moves it to this version: the application is generated again from the choices the record has, and the copy of every generated file and the ' +
                'record become what this version writes for them. No other file is changed; feature add then takes what the application has otherwise than ' +
                'this version generates for a change of yours.'])
@Component
@Scope('prototype')
@CompileStatic
class ProjectMigrateCommand extends BaseCommand implements Callable<Integer>, ProjectCommand {

    public static final String NAME = 'migrate'

    /** The types of application, as {@code --type} takes them and as the record has them. */
    public static final List<String> TYPES = Collections.unmodifiableList(ApplicationType.values().collect { ApplicationType each -> written(each) })

    // the profiles of Grails an application was created with, by the type of application each is now
    private static final Map<String, ApplicationType> PROFILES = [web: ApplicationType.WEB, 'rest-api': ApplicationType.REST_API, plugin: ApplicationType.PLUGIN,
                                                                  'web-plugin': ApplicationType.WEB_PLUGIN,
                                                                  'rest-api-plugin': ApplicationType.REST_API_PLUGIN] as Map<String, ApplicationType>
    private static final String ASSUMED = 'assumed'
    // how many characters of the identity of a build a line shows
    private static final int SHORT_SHA256 = 12
    private static final String GIVEN = 'given with '

    @CommandLine.Parameters(arity = '0..1', paramLabel = 'NAME',
            description = 'The name of the application, as a create command takes it: with its package in front, as com.example.shop, for the ' +
                    'package its classes are created in, or alone.')
    String name

    @CommandLine.Option(names = ['--type'], paramLabel = 'TYPE', completionCandidates = TypeChoices,
            description = 'The type of the application: web (what create-app creates), rest-api, plugin, web-plugin or rest-api-plugin.')
    String type

    /** What {@code --type} takes, which is all it takes. */
    @CompileStatic
    static class TypeChoices extends ArrayList<String> implements OptionChoices {

        TypeChoices() {
            super(TYPES)
        }
    }

    @CommandLine.Option(names = ['--features'], paramLabel = 'FEATURE', split = ',',
            description = 'Features the application has besides those that were found, by the names the create commands list with --list-features.')
    List<String> features = new ArrayList<>()

    @CommandLine.Option(names = ['--exclude-features'], paramLabel = 'FEATURE', split = ',',
            description = 'Features the application does not have, to leave out of those that were found.')
    List<String> excludedFeatures = new ArrayList<>()

    @CommandLine.Option(names = ['--jdk', '--java-version'], paramLabel = 'VERSION', description = 'The version of Java the application is for.')
    Integer javaVersion

    @CommandLine.Option(names = ['-s', '--servlet'], paramLabel = 'Servlet Implementation',
            description = 'The servlet implementation the application has. Possible values: ${COMPLETION-CANDIDATES}.',
            completionCandidates = ServletImplCandidates, converter = ServletImplConverter)
    ServletImpl servletImpl

    @CommandLine.Option(names = ['-d', '--data', '-g', '--gorm'], paramLabel = 'Grails Data Implementation',
            description = 'The Grails Data implementation the application has. Possible values: ${COMPLETION-CANDIDATES}.',
            completionCandidates = GormImplCandidates, converter = GormImplConverter)
    GormImpl gormImpl

    @CommandLine.Option(names = ['-r', '--reloading'], paramLabel = 'RELOADING',
            description = 'How the application reloads in development. Possible values: ${COMPLETION-CANDIDATES}.',
            completionCandidates = DevelopmentReloadingCandidates, converter = DevelopmentReloadingConverter)
    DevelopmentReloading reloading

    @CommandLine.Option(names = ['--dry-run'], description = 'Say what the record would be, or what moving it to this version would change in it, and write nothing.')
    boolean dryRun

    @CommandLine.Option(names = ['--force'],
            description = 'Write the record again over one this command wrote, with what the files and the command line say now. The record of how an ' +
                    'application was generated is never written over with one that is worked out from the files: it is moved to this version, which needs no --force.')
    boolean overwrite

    @CommandLine.Mixin
    RecordedPackOptions packOptions = new RecordedPackOptions()

    // asked of the Forge application when the command first needs it
    private final Supplier<ProjectGenerator> projectGeneratorSource

    @Autowired
    ProjectMigrateCommand(ProjectGenerator projectGenerator) {
        this.projectGeneratorSource = { projectGenerator } as Supplier<ProjectGenerator>
    }

    /** On a Forge application that is started when the command first needs it. */
    ProjectMigrateCommand(ForgeContext context) {
        this.projectGeneratorSource = { context.get().getBean(ProjectGenerator) } as Supplier<ProjectGenerator>
    }

    @Override
    Integer call() throws Exception {
        Path current = FileSystemOutputHandler.getDefaultBaseDirectory().toPath()
        ConsoleProject.Search search = ConsoleProject.search(current)
        if (search.project == null) {
            err(search.none('project migrate'))
            return 1
        }
        Path directory = search.project.directory
        ApplicationType given = givenType()
        // The record of a generated application is moved to this version, by the choices it has. So is one that a change
        // which was interrupted left half written, with the lock of the change and the manifest from before it: the move
        // ends the change first, after which the record reads, and nothing refuses the record before that
        ProjectState recorded = recordWithBaseline(directory)
        // The record this command wrote with a baseline is moved to this version as that of a generated application is,
        // unless the command is told to write it anew or what the application is otherwise
        ProjectState adopted = recorded != null && recorded.manifest.isAdopted() ? recorded : null
        if ((recorded != null && adopted == null) || halfWritten(directory) || (adopted != null && !overwrite && choicesGiven().isEmpty())) {
            return move(directory)
        }
        boolean ofPacks = adopted != null && adopted.lock.features.any { LockedFeature feature -> feature.source == LockedFeature.PACK }
        String packsGiven = packOptionsGiven()
        if (packsGiven != null && !ofPacks) {
            err("${packsGiven} ${packsGiven.contains(', ') ? 'are' : 'is'} for the record of an application that has the features of feature packs that are " +
                    'not part of Grails, and this application has no such record. Nothing was written')
            return 1
        }
        String refusal = refusal(directory)
        if (refusal != null) {
            err(refusal)
            return 1
        }
        ProjectEvidence evidence = ProjectEvidence.of(directory)
        List<String> notes = []
        if (evidence.earlierRecordRefused != null) {
            notes << "${GradleRunner.line(evidence.earlierRecordRefused)}: nothing of the file was used".toString()
        }
        Found<ApplicationType> applicationType = applicationType(given, evidence, notes)
        if (applicationType == null) {
            err("What type of application this is could not be worked out: ${notes.isEmpty() ? '' : notes.join('. ') + '. '}" +
                    "Say it with --type, which is one of ${TYPES.join(', ')}. Nothing was written")
            return 1
        }
        Identity identity = identity(directory, evidence)
        if (identity.refusal != null) {
            err("${identity.refusal}. Nothing was written")
            return 1
        }
        // the feature packs that are not part of Grails a feature was added from since the record was written, given again
        List<Pack> packs = []
        if (ofPacks) {
            try {
                packs = FeatureAddition.recordedPacks(adopted.lock, adopted.manifest.preset, packOptions.packs(directory, createPackArtifacts(), grailsRepoUrl(),
                        { String line -> out("@|blue ||@ ${GradleRunner.line(line)}") } as Consumer<String>, { String line -> warning(GradleRunner.line(line)) } as Consumer<String>))
            } catch (ProjectChangeException | IllegalArgumentException | PackArtifactException e) {
                err(GradleRunner.line(e.getMessage() ?: e.getClass().getSimpleName()))
                stacktrace(e)
                return 1
            }
        }
        AvailableFeatures available = projectGeneratorSource.get().availableFeatures(applicationType.value, packs)
        Map<String, String> found = featuresFound(available, evidence, notes)
        // What the record this command wrote names is kept: a feature that was added since is among it, and the files
        // of the application do not show every feature
        List<String> kept = []
        if (adopted != null) {
            List<String> gone = []
            for (String name : adopted.manifest.requestedFeatures*.name) {
                Feature feature = available.findFeature(name, true).orElse(null)
                if (feature == null) {
                    gone << name
                } else {
                    kept << feature.getName()
                    found.put(feature.getName(), "the record, ${ProjectState.MANIFEST}".toString())
                }
            }
            if (gone) {
                notes << ("The record names ${gone.size() == 1 ? 'a feature' : 'features'} an application of the type ${written(applicationType.value)} does not have in this " +
                        "version, which ${gone.size() == 1 ? 'is' : 'are'} not in the new record: ${shown(gone.join(', '))}").toString()
            }
        }
        // what is known not to have been read is said, so that no feature is taken to be absent because it was not found
        if (!evidence.buildRead) {
            notes << "The project has no ${ProjectEvidence.BUILD} or ${ProjectEvidence.KOTLIN_BUILD} that could be read, so no feature was looked for in its build".toString()
        }
        notes.addAll(evidence.notRead)
        String earlier = EarlierGrailsProject.earlierVersion(current)
        if (earlier != null) {
            notes << ("The project is on Grails ${earlier} (gradle.properties): the record is that of an application of Grails ${EarlierGrailsProject.FIRST_MAJOR}, and the " +
                    "other commands of a project are run once the project is on Grails ${EarlierGrailsProject.FIRST_MAJOR} or later").toString()
        }
        if (!evidence.ownBuild) {
            notes << ("The project has no ${ProjectEvidence.SETTINGS} of its own, so it is part of a build above it: what that build declares for it, in a subprojects or " +
                    'allprojects block or in a convention plugin, was not read').toString()
        }
        if (evidence.earlierRecord != null && evidence.earlierRecordRefused == null) {
            notes << ("${evidence.earlierRecord} was read and is left as it is. With a record under ${ProjectState.DIRECTORY} the commands go by that, and warn when " +
                    "${evidence.earlierRecord} does not name the same package and features: remove the file when nothing else reads it").toString()
        }
        List<String> stated = named(available, features, '--features')
        List<String> excluded = named(available, excludedFeatures, '--exclude-features')
        List<String> both = stated.findAll { String each -> each in excluded }
        if (both) {
            throw new CommandLine.ParameterException(spec.commandLine(), "${shown(both.join(', '))} cannot be given to --features and to --exclude-features both")
        }
        Map<String, String> has = new LinkedHashMap<>()
        found.each { String feature, String where ->
            if (!(feature in excluded) && !(feature in stated)) {
                has.put(feature, where)
            }
        }
        stated.each { String feature -> has.put(feature, GIVEN + '--features') }
        Map<String, Found<String>> options = options(available, applicationType.value, has.keySet(), evidence, notes)
        // a feature that is an option of the application is there with the option, as it is in an application that is created
        addFeatureOf(available, has, excluded, options.get('servlet'), ServletImpl.values().find { ServletImpl each -> written(each) == options.get('servlet').value }?.getName())
        addFeatureOf(available, has, excluded, options.get('data'), GormImpl.values().find { GormImpl each -> written(each) == options.get('data').value }?.getName())
        addFeatureOf(available, has, excluded, options.get('reloading'),
                DevelopmentReloading.values().find { DevelopmentReloading each -> written(each) == options.get('reloading').value }?.getFeatureName())

        List<Feature> applied = has.keySet().collect { String each -> available.findFeature(each, true).get() }
        applied.sort(FeatureContext.FEATURE_ORDER)
        GenerationOptions generationOptions = new GenerationOptions(JdkVersion.valueOf(Integer.parseInt(options.get('jdk').value)),
                ServletImpl.values().find { ServletImpl each -> written(each) == options.get('servlet').value },
                GormImpl.values().find { GormImpl each -> written(each) == options.get('data').value },
                DevelopmentReloading.values().find { DevelopmentReloading each -> written(each) == options.get('reloading').value },
                !evidence.ownBuild)
        // The baseline: what this version generates for an application of what was found and given. Where it
        // generates none, the record is written without one, and says what the command has to be told
        FeatureAddition addition = new FeatureAddition(projectGeneratorSource.get())
        List<String> byName = (stated + kept.findAll { String each -> has.containsKey(each) }).unique()
        RecordAdoption adoption = null
        String noBaseline = null
        if (applicationType.assumed) {
            noBaseline = "the type of the application was assumed (${applicationType.where}): say it with --type"
        } else {
            try {
                if (!dryRun) {
                    // a change that was interrupted is ended first, as a feature that is added ends it
                    for (String message : addition.recover(directory).getMessages()) {
                        out("@|blue ||@ ${message}")
                    }
                }
                adoption = addition.adopt(directory, applicationType.value, identity.name.value, identity.defaultPackage.value, generationOptions, byName,
                        has.keySet().findAll { String each -> !(each in byName) }.toList(), excluded, getOperatingSystem(), packs)
            } catch (FeatureSelectionException e) {
                noBaseline = "this version does not create an application of the type, the options and the features above (${GradleRunner.line(e.getMessage())}): say with " +
                        '--exclude-features which of the features the application does not have, or with the options what it has otherwise'
            } catch (ProjectChangeException e) {
                if (e.getReason() != ProjectChangeException.NOT_REGENERABLE) {
                    err(GradleRunner.line(e.getMessage() ?: e.getClass().getSimpleName()))
                    stacktrace(e)
                    return 1
                }
                noBaseline = "${GradleRunner.line(e.getMessage()).replaceFirst(/\. Nothing was changed$/, '')}: say with --exclude-features which of the features the application " +
                        'does not have, or with the options what it has otherwise'
            } catch (ProjectStateException e) {
                err("The record could not be written as this version reads it: ${GradleRunner.line(e.getMessage())}. Give the name of the application with its " +
                        'package to the command, as com.example.shop. Nothing was written')
                return 1
            }
        }
        if (adoption != null) {
            describe(applicationType, identity, options, applied, has, excluded, notes, evidence)
            return adopt(adoption, has.keySet())
        }
        if (adopted != null) {
            describe(applicationType, identity, options, applied, has, excluded, notes, evidence)
            err("The record is not written anew: ${noBaseline}. The record the application has, with its baseline, is as it was. Nothing was written")
            return 1
        }
        Map<String, byte[]> files
        try {
            files = AdoptedProjectRecord.files(applicationType.value, identity.name.value, identity.defaultPackage.value, generationOptions,
                    stated, excluded,
                    applied.collect { Feature feature ->
                        new LockedFeature(feature.getName(), feature instanceof PackFeature ? LockedFeature.BUNDLED_PACK : LockedFeature.FRAMEWORK)
                    })
        } catch (ProjectStateException e) {
            err("The record could not be written as this version reads it: ${GradleRunner.line(e.getMessage())}. Give the name of the application with its " +
                    'package to the command, as com.example.shop. Nothing was written')
            return 1
        }
        describe(applicationType, identity, options, applied, has, excluded, notes, evidence)
        out("@|blue ||@ The record ${dryRun ? 'would be' : 'is'} written without a baseline (generationState ${ProjectManifest.UNTRACKED}), and ${UsageHelp.toType(spec, 'feature add')} " +
                "adds no feature to the application until it has one: ${noBaseline}, and run ${UsageHelp.toType(spec, 'project migrate --force')} with it")
        if (dryRun) {
            out('@|blue ||@ Nothing was written')
            return 0
        }
        return write(directory, files)
    }

    // The record with its baseline: what the baseline is and how the application compares with it, and then the files, all of them or none
    private Integer adopt(RecordAdoption adoption, Collection<String> has) {
        List<String> ofBaseline = adoption.record.lock.featureNames
        List<String> notThere = has.findAll { String each -> !(each in ofBaseline) }.toList()
        if (notThere) {
            out("@|blue ||@ Not in the baseline, because this version does not select ${notThere.size() == 1 ? 'it' : 'them'} by name for an application of these " +
                    "choices: ${shown(notThere.join(', '))}")
        }
        int files = adoption.record.baseline.files.size()
        out("@|blue ||@ The baseline ${dryRun ? 'would be' : 'is'} the ${files} files this version generates for an application of the type, the options and the features above " +
                "(${ofBaseline.size()} features with those of the type; ${UsageHelp.toType(spec, 'project show')} lists them). The application has ${adoption.asGenerated.size()} of " +
                "them as generated, ${adoption.otherwise.size()} otherwise and ${adoption.absent.size()} not at all: what it has otherwise is taken for a change of yours")
        if (dryRun) {
            out('@|blue ||@ Nothing was written')
            return 0
        }
        try {
            adoption.apply()
        } catch (ProjectChangeException e) {
            err(GradleRunner.line(e.getMessage() ?: e.getClass().getSimpleName()))
            stacktrace(e)
            return 1
        }
        out("@|blue ||@ The record was written, with its baseline under ${ProjectState.BASELINE}: ${ProjectState.MANIFEST}, ${ProjectState.LOCK} and ${ProjectState.BASELINE_INDEX}. " +
                "No other file of the project was changed. Commit the directory ${ProjectState.DIRECTORY}")
        out("@|blue ||@ ${UsageHelp.toType(spec, 'help')} lists the commands that create the classes of the application, and ${UsageHelp.toType(spec, 'project show')} says what the record has. " +
                "${UsageHelp.toType(spec, 'feature add')} adds a feature against the baseline and keeps what the application has otherwise; where a feature changes lines the application " +
                "has otherwise, it names them and writes nothing. ${UsageHelp.toType(spec, 'project regenerate --diff')} names the files that are not as this version generates them, " +
                "and ${UsageHelp.toType(spec, 'project migrate --force')} writes the record anew when the type, an option or a feature above is not what the application has")
        out("@|blue ||@ ${ProjectHealthCommand.advice(spec)}")
        return 0
    }

    private ApplicationType givenType() {
        if (type == null) {
            return null
        }
        ApplicationType given = ApplicationType.values().find { ApplicationType each -> written(each) == type.toLowerCase(Locale.ROOT).replace('_', '-') }
        if (given == null) {
            throw new CommandLine.ParameterException(spec.commandLine(), "${shown(type)} is no type of application: --type is one of ${TYPES.join(', ')}")
        }
        return given
    }

    // Why no record is written for the project, whatever its files say: it has a record already that is
    // not this command's to replace. A project on an earlier version of Grails is given its record all
    // the same: writing it is a step of the upgrade, before the version is changed or after
    private String refusal(Path directory) {
        List<String> parts = [ProjectState.LOCK, ProjectState.BASELINE].findAll { String each -> Files.exists(directory.resolve(each), LinkOption.NOFOLLOW_LINKS) }
        if (!ProjectState.exists(directory)) {
            return parts.isEmpty() ? null : "The project has ${parts.join(' and ')} and no ${ProjectState.MANIFEST}: that is what is left of a record that was not " +
                    "written to its end or not committed whole, and it is not written over. Restore ${ProjectState.MANIFEST} from version control, or remove " +
                    "${parts.join(' and ')}, and run the command again. Nothing was written"
        }
        ProjectManifest manifest
        try {
            manifest = ProjectState.readSelection(directory, true).manifest
        } catch (ProjectStateException e) {
            return "The project has a record under ${ProjectState.DIRECTORY} that cannot be read (${GradleRunner.line(e.getMessage())}), and it is not written over. " +
                    "Restore it from version control, or remove ${ProjectState.MANIFEST}, ${ProjectState.LOCK} and ${ProjectState.BASELINE}, and run the command again. " +
                    'Nothing was written'
        }
        if (!overwrite && !dryRun) {
            return "The application already has a record that ${UsageHelp.toType(spec, 'project migrate')} wrote (${ProjectState.MANIFEST}). Run again with --force to write it anew, " +
                    'or with --dry-run to see what it would be. Nothing was written'
        }
        return null
    }

    // the type that was given, or the one the files of the project say, by what says it most directly
    private Found<ApplicationType> applicationType(ApplicationType given, ProjectEvidence evidence, List<String> notes) {
        if (given != null) {
            return new Found<ApplicationType>(given, GIVEN + '--type')
        }
        if (evidence.recordedType != null) {
            ApplicationType recorded = ApplicationType.values().find { ApplicationType each -> written(each) == evidence.recordedType.toLowerCase(Locale.ROOT).replace('_', '-') }
            if (recorded != null) {
                return new Found<ApplicationType>(recorded, "applicationType of ${evidence.earlierRecord}")
            }
            notes << "applicationType of ${evidence.earlierRecord} is ${shown(evidence.recordedType)}, which is no type of application".toString()
        }
        Map<String, String> profiles = new LinkedHashMap<>()
        if (evidence.recordedProfile != null) {
            profiles.put("profile of ${evidence.earlierRecord}".toString(), evidence.recordedProfile)
        }
        if (evidence.configuredProfile != null) {
            profiles.put("grails.profile of ${ProjectEvidence.CONFIGURATION}".toString(), evidence.configuredProfile)
        }
        if (evidence.profileDependency != null) {
            if (evidence.profileOfGrails != null) {
                profiles.put("the profile dependency of ${evidence.buildFile}".toString(), evidence.profileOfGrails)
            } else {
                notes << "The profile ${shown(evidence.profileDependency)} of ${evidence.buildFile} is not one of Grails, and says nothing of the type here".toString()
            }
        }
        for (Map.Entry<String, String> each : profiles.entrySet()) {
            ApplicationType ofProfile = PROFILES.get(each.value)
            if (ofProfile != null) {
                return new Found<ApplicationType>(ofProfile, "the profile ${each.value}, ${each.key}")
            }
            notes << "The profile ${shown(each.value)} (${each.key}) has no type of application in this version".toString()
        }
        Set<String> plugins = evidence.gradlePlugins
        if ('grails-plugin' in plugins) {
            return 'grails-gsp' in plugins ?
                    new Found<ApplicationType>(ApplicationType.WEB_PLUGIN, "the Gradle plugins grails-plugin and grails-gsp of ${evidence.buildFile}") :
                    new Found<ApplicationType>(ApplicationType.PLUGIN, "the Gradle plugin grails-plugin of ${evidence.buildFile}, without grails-gsp; " +
                            'a plugin with controllers and no views is --type rest-api-plugin', true)
        }
        if ('grails-web' in plugins) {
            return 'grails-gsp' in plugins ?
                    new Found<ApplicationType>(ApplicationType.WEB, "the Gradle plugins grails-web and grails-gsp of ${evidence.buildFile}") :
                    new Found<ApplicationType>(ApplicationType.REST_API, "the Gradle plugin grails-web of ${evidence.buildFile}, without grails-gsp")
        }
        if (!evidence.buildRead) {
            notes << "The project has no ${ProjectEvidence.BUILD} that could be read".toString()
        }
        return null
    }

    // the name and the package, which together have to be what a create command takes
    private Identity identity(Path directory, ProjectEvidence evidence) {
        Found<String> named = null
        Found<String> ofPackage = null
        if (name != null) {
            int dot = name.lastIndexOf('.')
            named = new Found<String>(name.substring(dot + 1), 'given')
            if (dot > 0) {
                ofPackage = new Found<String>(name.substring(0, dot), 'given')
            }
        }
        if (named == null && evidence.rootProjectName != null) {
            named = new Found<String>(evidence.rootProjectName, "rootProject.name of ${evidence.settingsFile}")
        }
        if (named == null) {
            named = new Found<String>(directory.getFileName()?.toString() ?: '', 'the name of the directory')
        }
        Map<String, String> packages = new LinkedHashMap<>()
        if (evidence.recordedPackage != null) {
            packages.put("defaultPackage of ${evidence.earlierRecord}".toString(), evidence.recordedPackage)
        }
        if (evidence.configuredPackage != null) {
            packages.put("grails.codegen.defaultPackage of ${ProjectEvidence.CONFIGURATION}".toString(), evidence.configuredPackage)
        }
        if (evidence.group != null) {
            packages.put("group of ${evidence.buildFile}".toString(), evidence.group)
        }
        if (evidence.applicationClassPackage != null) {
            packages.put('the package of the Application class', evidence.applicationClassPackage)
        }
        if (ofPackage == null) {
            Map.Entry<String, String> first = packages.entrySet().find { Map.Entry<String, String> each -> NameUtils.isValidJavaPackage(each.value) }
            ofPackage = first == null ? null : new Found<String>(first.value, first.key)
        }
        if (ofPackage == null) {
            return new Identity(null, null, 'The package the classes of the application are created in could not be worked out: neither ' +
                    "grails.codegen.defaultPackage of ${ProjectEvidence.CONFIGURATION} nor the group of ${evidence.buildFile} nor an Application class under " +
                    'grails-app/init names one. Give the name of the application with its package to the command, as com.example.shop')
        }
        // what the record takes: a name and a package a create command would have made the application with
        boolean taken
        try {
            Project parsed = NameUtils.parse("${ofPackage.value}.${named.value}".toString())
            taken = NameUtils.isValidJavaPackage(ofPackage.value) && parsed.getName() == named.value && parsed.getPackageName() == ofPackage.value
        } catch (IllegalArgumentException ignored) {
            taken = false
        }
        if (!taken) {
            return new Identity(null, null, "${shown(ofPackage.value + '.' + named.value)} (the package: ${ofPackage.where}; the name: ${named.where}) is not a name an " +
                    'application can be created with. Give the name of the application with its package to the command, as com.example.shop')
        }
        return new Identity(named, ofPackage, null)
    }

    /**
     * The features the files of the project show: those the record of an earlier version names that
     * this version has, and those of Grails that add a dependency to a build which no other feature
     * adds, when the build file declares every such dependency of the feature. A dependency several
     * features add says nothing of which of them the application has, except where the features are
     * those of one group an application has one of and each of the others is told by a dependency of
     * its own: the one that adds nothing of its own is then the one the build has.
     *
     * @return where each feature was found, by its name
     */
    private Map<String, String> featuresFound(AvailableFeatures available, ProjectEvidence evidence, List<String> notes) {
        Map<String, String> found = new LinkedHashMap<>()
        List<String> unknown = []
        for (String recorded : (evidence.recordedFeatures ?: [] as List<String>)) {
            Feature feature = available.findFeature(recorded, true).orElse(null)
            if (feature == null) {
                unknown << recorded
            } else {
                found.put(feature.getName(), evidence.earlierRecord)
            }
        }
        if (unknown) {
            notes << ("${evidence.earlierRecord} names ${unknown.size() == 1 ? 'a feature' : 'features'} this version of Grails does not have for the type, " +
                    "which ${unknown.size() == 1 ? 'is' : 'are'} not in the record: ${shown(unknown.join(', '))}").toString()
        }
        List<Feature> ofPacks = available.getAllFeatures().filter { Feature each -> each instanceof PackFeature && ((PackFeature) each).getPack().isBundled() }.toList()
        Map<String, Integer> added = [:]
        for (Feature feature : ofPacks) {
            List<PackDependency> all = ((PackFeature) feature).getPack().getDescriptor().dependencies
            for (String each : all.collect { PackDependency dependency -> "${dependency.groupId}:${dependency.artifactId}".toString() }.unique()) {
                added.put(each, added.getOrDefault(each, 0) + 1)
            }
        }
        for (Feature feature : ofPacks.toSorted(FeatureContext.FEATURE_ORDER)) {
            List<String> adds = ((PackFeature) feature).getPack().getDescriptor().dependencies
                    .findAll { PackDependency each -> each.scope != null && each.when == PackCondition.ALWAYS }
                    .collect { PackDependency each -> "${each.groupId}:${each.artifactId}".toString() }
                    .findAll { String each -> added.get(each) == 1 }
            if (!adds.isEmpty() && evidence.dependencies.containsAll(adds) && !found.containsKey(feature.getName())) {
                found.put(feature.getName(), "${evidence.buildFile} declares ${adds.join(' and ')}".toString())
            }
        }
        // A feature that is one of several an application has one of, and adds nothing the others of them do not
        // add: the build declares what it adds, and what tells any of the others is not there. That is how the
        // Geb feature for containers is told from the one for local browsers, which adds the drivers to it.
        for (Feature feature : ofPacks.toSorted(FeatureContext.FEATURE_ORDER)) {
            String group = ((PackFeature) feature).getPack().getDescriptor().oneOf
            List<String> adds = ((PackFeature) feature).getPack().getDescriptor().dependencies
                    .findAll { PackDependency each -> each.scope != null && each.when == PackCondition.ALWAYS }
                    .collect { PackDependency each -> "${each.groupId}:${each.artifactId}".toString() }.unique()
            if (group == null || adds.isEmpty() || found.containsKey(feature.getName()) || !evidence.dependencies.containsAll(adds)) {
                continue
            }
            List<Feature> others = ofPacks.findAll { Feature each -> !each.is(feature) && ((PackFeature) each).getPack().getDescriptor().oneOf == group }
            boolean sharedInGroupOnly = adds.every { String dependency ->
                ofPacks.findAll { Feature each ->
                    ((PackFeature) each).getPack().getDescriptor().dependencies.any { PackDependency it -> "${it.groupId}:${it.artifactId}".toString() == dependency }
                }.every { Feature each -> each.is(feature) || others.contains(each) }
            }
            boolean othersTold = !others.isEmpty() && others.every { Feature each ->
                ((PackFeature) each).getPack().getDescriptor().dependencies.any { PackDependency it -> added.get("${it.groupId}:${it.artifactId}".toString()) == 1 }
            }
            if (sharedInGroupOnly && othersTold && others.every { Feature each -> !found.containsKey(each.getName()) }) {
                found.put(feature.getName(), ("${evidence.buildFile} declares ${adds.join(' and ')}, and nothing that ${others.size() == 1 ? 'the feature' : 'the features'} " +
                        "${others*.getName().join(', ')} would add to it").toString())
            }
        }
        return found
    }

    // the features that were given, by the names they have in this version; one that is no feature is a mistake in the command line
    private List<String> named(AvailableFeatures available, List<String> given, String option) {
        List<String> names = []
        for (String each : given) {
            Feature feature = each == null || each.contains(':') ? null : available.findFeature(each, true).orElse(null)
            if (feature == null) {
                throw new CommandLine.ParameterException(spec.commandLine(), "${shown(String.valueOf(each))}, given to ${option}, is no feature of Grails for this type of " +
                        "application; ${UsageHelp.toType(spec, 'create-app --list-features')} lists the features")
            }
            if (!names.contains(feature.getName())) {
                names << feature.getName()
            }
        }
        return names
    }

    // the four options of the record: each as it was given, as the features that were found say it, as the build says
    // it otherwise, or as it is assumed
    private Map<String, Found<String>> options(AvailableFeatures available, ApplicationType applicationType, Set<String> has, ProjectEvidence evidence,
                                               List<String> notes) {
        Map<String, Found<String>> options = new LinkedHashMap<>()
        options.put('jdk', jdk(evidence, notes))
        ServletImpl servlet = ServletImpl.values().find { ServletImpl each -> each.getName() in has }
        Found<String> started = servletImpl != null || servlet != null ? null : servletOfStarter(available, applicationType, evidence, notes)
        options.put('servlet', servletImpl != null ? new Found<String>(written(servletImpl), GIVEN + '--servlet') :
                servlet != null ? new Found<String>(written(servlet), "the feature ${servlet.getName()}") :
                        started != null ? started :
                                new Found<String>(written(applicationType == ApplicationType.PLUGIN ? ServletImpl.NONE : ServletImpl.DEFAULT_OPTION), ASSUMED, true))
        GormImpl data = GormImpl.values().find { GormImpl each -> each.getName() in has }
        options.put('data', gormImpl != null ? new Found<String>(written(gormImpl), GIVEN + '--data') :
                data != null ? new Found<String>(written(data), "the feature ${data.getName()}") :
                        new Found<String>(written(GormImpl.DEFAULT_OPTION), ASSUMED, true))
        DevelopmentReloading reloads = DevelopmentReloading.values().find { DevelopmentReloading each -> each.getFeatureName() != null && each.getFeatureName() in has }
        DevelopmentReloading recorded = DevelopmentReloading.values().find { DevelopmentReloading each -> each.getName() == evidence.recordedReloading }
        options.put('reloading', reloading != null ? new Found<String>(written(reloading), GIVEN + '--reloading') :
                reloads != null ? new Found<String>(written(reloads), "the feature ${reloads.getFeatureName()}") :
                        recorded != null ? new Found<String>(written(recorded), "reloading of ${evidence.earlierRecord}") :
                                new Found<String>(written(DevelopmentReloading.NONE), ASSUMED, true))
        return options
    }

    // The version of Java of the record: the one that was given, or the one the build names when this version generates
    // for it. A version of Java the build names that Grails does not run on is said, whatever the record takes, for
    // the build has to be changed before it builds with this version
    private Found<String> jdk(ProjectEvidence evidence, List<String> notes) {
        List<Integer> generated = JdkVersion.values().collect { JdkVersion each -> each.majorVersion() }
        int lowest = generated.min()
        String grails = "Grails ${VersionInfo.getGrailsVersion().tokenize('.').first()}"
        List<ProjectEvidence.JavaSetting> below = evidence.javaSettings.findAll { ProjectEvidence.JavaSetting each -> each.version < lowest }
        if (below) {
            notes << ("${evidence.buildFile} names ${below.collect { ProjectEvidence.JavaSetting each -> "Java ${each.version} (${each})" }.join(' and ')}, and ${grails} " +
                    "needs Java ${lowest} or later: change the build to Java ${lowest} or later, or the application does not build with ${grails}").toString()
        }
        if (javaVersion != null) {
            if (!(javaVersion in generated)) {
                throw new CommandLine.ParameterException(spec.commandLine(), "--jdk is one of ${generated.join(', ')}")
            }
            return new Found<String>(String.valueOf(javaVersion), GIVEN + '--jdk')
        }
        ProjectEvidence.JavaSetting named = evidence.javaSetting
        if (named == null) {
            return new Found<String>(String.valueOf(JdkVersion.DEFAULT_OPTION.majorVersion()), ASSUMED, true)
        }
        if (!(named.version in generated)) {
            if (named.version >= lowest) {
                notes << "${evidence.buildFile} names Java ${named.version} (${named}), which this version of Grails does not generate for".toString()
            }
            return new Found<String>(String.valueOf(JdkVersion.DEFAULT_OPTION.majorVersion()), "assumed in place of Java ${named.version}: ${evidence.javaVersionWhere}", true, true)
        }
        // what other settings name otherwise, which is not what the classes are compiled for, as a toolchain that is newer
        List<ProjectEvidence.JavaSetting> otherwise = evidence.javaSettings.findAll { ProjectEvidence.JavaSetting each -> each.version != named.version && each.version >= lowest }
        if (otherwise) {
            notes << ("${evidence.buildFile} also names ${otherwise.collect { ProjectEvidence.JavaSetting each -> "Java ${each.version} (${each})" }.join(' and ')}; the record takes " +
                    "Java ${named.version}, of ${named.setting}, which is what the classes are compiled for").toString()
        }
        return new Found<String>(String.valueOf(named.version), evidence.javaVersionWhere)
    }

    // The servlet container whose starter the build declares, where no feature of one was found: a build that
    // declares only the starter has the container, as the build of an application the Grails shell created
    // does. A starter of a container the type of application is not generated with, and the starters of more
    // than one container, name none, and that is said.
    private static Found<String> servletOfStarter(AvailableFeatures available, ApplicationType applicationType, ProjectEvidence evidence, List<String> notes) {
        Map<ServletImpl, String> starters = new LinkedHashMap<>()
        evidence.servletStarters.each { ServletImpl servlet, String declared ->
            if (available.findFeature(servlet.getName(), true).isPresent()) {
                starters.put(servlet, declared)
            } else {
                notes << ("${evidence.buildFile} declares ${declared}, the starter of the servlet container ${written(servlet)}, which an application of the type " +
                        "${written(applicationType)} is not generated with, so the servlet was not taken from it").toString()
            }
        }
        if (starters.size() > 1) {
            notes << ("${evidence.buildFile} declares the starters of more than one servlet container (${starters.values().join(', ')}), " +
                    'so the servlet was not taken from them').toString()
            return null
        }
        if (starters.isEmpty()) {
            return null
        }
        Map.Entry<ServletImpl, String> declared = starters.entrySet().iterator().next()
        return new Found<String>(written(declared.key), "${evidence.buildFile} declares ${declared.value}".toString())
    }

    // An option that was given says which of the features of its kind the application has: the others
    // of that kind that were found are not in the record, and the one of the option is
    private static void addFeatureOf(AvailableFeatures available, Map<String, String> has, List<String> excluded, Found<String> option, String featureName) {
        if (!option.where.startsWith(GIVEN)) {
            return
        }
        String kind = option.where.substring(GIVEN.length())
        List<String> ofKind = kind == '--servlet' ? ServletImpl.values().collect { ServletImpl each -> each.getName() } :
                kind == '--data' ? GormImpl.values().collect { GormImpl each -> each.getName() } :
                        DevelopmentReloading.values().collect { DevelopmentReloading each -> each.getFeatureName() }.findAll { String each -> each != null }
        has.keySet().removeAll(ofKind.findAll { String each -> each != featureName })
        if (featureName != null && !(featureName in excluded) && !has.containsKey(featureName) && available.findFeature(featureName, true).isPresent()) {
            has.put(featureName, "the option ${kind}".toString())
        }
    }

    private void describe(Found<ApplicationType> applicationType, Identity identity, Map<String, Found<String>> options, List<Feature> applied,
                          Map<String, String> has, List<String> excluded, List<String> notes, ProjectEvidence evidence) {
        out("@|blue ||@ The record of the application ${shown(identity.name.value)}, ${dryRun ? 'as it would be written to' : 'as it is written to'} ${ProjectState.DIRECTORY}")
        row('type', written(applicationType.value), applicationType.where)
        row('name', identity.name.value, identity.name.where)
        row('package', identity.defaultPackage.value, identity.defaultPackage.where)
        options.each { String option, Found<String> value -> row(option, value.value, value.where) }
        if (!evidence.ownBuild) {
            row('layout', GenerationOptions.SUBPROJECT, "the project has no ${ProjectEvidence.SETTINGS} of its own")
        }
        out("@|blue ||@ The ${applied.size()} ${applied.size() == 1 ? 'feature' : 'features'} of Grails it is taken to have" +
                (applied.isEmpty() ? ': none was found' : ', each with where it was found'))
        for (Feature feature : applied) {
            out("    ${shown(feature.getName())} (${GradleRunner.line(has.get(feature.getName()))})")
        }
        if (excluded) {
            out("@|blue ||@ Left out with --exclude-features: ${shown(excluded.join(', '))}")
        }
        for (String note : notes) {
            out("@|blue ||@ ${GradleRunner.line(note)}")
        }
        List<String> assumed = []
        List<String> instead = []
        if (applicationType.assumed) {
            assumed << 'the type (--type)'
        }
        options.each { String option, Found<String> value ->
            if (value.assumed) {
                (value.instead ? instead : assumed) << "${option} (--${option})".toString()
            }
        }
        out('@|blue ||@ ' + (assumed.isEmpty() && instead.isEmpty() ? 'Nothing was assumed. ' : '') +
                (assumed.isEmpty() ? '' : "Assumed, because nothing in the project says it: ${assumed.join(', ')}. ") +
                (instead.isEmpty() ? '' : "Assumed in place of what the project says, which this version does not generate for: ${instead.join(', ')}. ") +
                'The files of the project were read as text and nothing of the build was run, so a feature the build does not declare in a line of its own was ' +
                "not found. To say it otherwise, run ${UsageHelp.toType(spec, 'project migrate --force')} with the name and the package (com.example.shop), --type, --jdk, --servlet, " +
                '--data, --reloading, --features for features that were not found and --exclude-features for features the application does not have')
    }

    private void row(String what, String value, String where) {
        out("    ${what.padRight(13)} ${shown(value).padRight(16)} (${GradleRunner.line(where)})")
    }

    // The files of the record, all of them or none, and nothing else: read back as every command reads them
    private Integer write(Path directory, Map<String, byte[]> files) {
        Map<String, String> texts = new LinkedHashMap<>()
        files.each { String path, byte[] content ->
            // what tells Git how to keep the record is not needed to read it: one that is there is left as it is
            if (!(path in [ProjectState.IGNORE, ProjectState.ATTRIBUTES]) || !Files.exists(directory.resolve(path), LinkOption.NOFOLLOW_LINKS)) {
                texts.put(path, new String(content, StandardCharsets.UTF_8))
            }
        }
        try {
            ProjectFiles.write(directory.toFile(), new ProjectOutputHandler(directory.toFile(), this), texts)
            ProjectState.read(directory)
        } catch (ProjectFiles.Failed e) {
            err(e.getMessage())
            return 1
        } catch (ProjectStateException e) {
            err("The record was written and cannot be read again: ${GradleRunner.line(e.getMessage())}")
            return 1
        }
        out("@|blue ||@ The record was written: ${texts.keySet().join(', ')}. No other file of the project was changed. Commit the directory ${ProjectState.DIRECTORY}")
        out("@|blue ||@ ${UsageHelp.toType(spec, 'help')} lists the commands that create the classes of the application, and ${UsageHelp.toType(spec, 'project show')} says what the record has. " +
                "The record says what the application is taken to be and has no baseline: ${UsageHelp.toType(spec, 'feature add')} and ${UsageHelp.toType(spec, 'project regenerate')} " +
                'need one, and change nothing here')
        out("@|blue ||@ ${ProjectHealthCommand.advice(spec)}")
        return 0
    }

    // the record of the project when it reads and has a baseline of the generator: that of a generated application, or
    // one this command wrote with a baseline; what else the project has is for the rest of the command
    private static ProjectState recordWithBaseline(Path directory) {
        if (!ProjectState.exists(directory)) {
            return null
        }
        try {
            ProjectState state = ProjectState.readSelection(directory, true)
            return state.manifest.hasBaseline() ? state : null
        } catch (ProjectStateException ignored) {
            return null
        }
    }

    // A record that does not read because a change of this user that was interrupted left the project half changed. What
    // tells it is started with the generator, which is asked for only here, when the record does not read
    private boolean halfWritten(Path directory) {
        if (!ProjectState.exists(directory)) {
            return false
        }
        try {
            ProjectState.readSelection(directory, true)
            return false
        } catch (ProjectStateException ignored) {
            return new FeatureAddition(projectGeneratorSource.get()).isHalfChanged(directory)
        }
    }

    // the options of the feature packs that were given, by their names; null when none was
    private String packOptionsGiven() {
        List<String> given = []
        if (!packOptions.directories.isEmpty()) {
            given << '--feature-pack-dir'
        }
        if (!packOptions.jars.isEmpty()) {
            given << '--feature-pack-jar'
        }
        if (!packOptions.repositories.isEmpty()) {
            given << '--feature-pack-repository'
        }
        if (packOptions.offline) {
            given << '--offline'
        }
        return given.isEmpty() ? null : given.join(', ')
    }

    // what says what an application without a record is, which the record of a generated application says itself
    private List<String> choicesGiven() {
        List<String> given = []
        if (name != null) {
            given << 'the name'
        }
        if (type != null) {
            given << '--type'
        }
        if (!features.isEmpty()) {
            given << '--features'
        }
        if (!excludedFeatures.isEmpty()) {
            given << '--exclude-features'
        }
        if (javaVersion != null) {
            given << '--jdk'
        }
        if (servletImpl != null) {
            given << '--servlet'
        }
        if (gormImpl != null) {
            given << '--data'
        }
        if (reloading != null) {
            given << '--reloading'
        }
        return given
    }

    /**
     * Moves the record of a generated application to this version, as {@link FeatureAddition#migrate}
     * does: from the choices the record has, and with the feature packs it has given again.
     */
    private Integer move(Path directory) {
        List<String> choices = choicesGiven()
        if (!choices.isEmpty()) {
            err("The application has the record of how it was generated (${ProjectState.MANIFEST}), which is moved to this version with the choices it has: " +
                    "${choices.join(', ')} ${choices.size() == 1 ? 'is' : 'are'} for an application without such a record. " +
                    "${UsageHelp.toType(spec, 'project show')} says what the record has. Nothing was written")
            return 1
        }
        try {
            FeatureAddition addition = new FeatureAddition(projectGeneratorSource.get())
            if (!dryRun) {
                // a change that was interrupted is ended first, as a feature that is added ends it
                for (String message : addition.recover(directory).getMessages()) {
                    out("@|blue ||@ ${message}")
                }
            }
            Consumer<String> said = { String line -> out("@|blue ||@ ${GradleRunner.line(line)}") } as Consumer<String>
            Consumer<String> warned = { String line -> warning(GradleRunner.line(line)) } as Consumer<String>
            List<Pack> packs = packOptions.packs(directory, createPackArtifacts(), grailsRepoUrl(), said, warned)
            RecordMigration migration = addition.migrate(directory, getOperatingSystem(), packs)
            // the record as it reads once a change that was interrupted is ended, which names the application
            return moved(directory, ProjectState.readSelection(directory, true).manifest, migration)
        } catch (ProjectStateException e) {
            err("The record of the application under ${ProjectState.DIRECTORY} is not moved to this version, because it does not read: " +
                    "${GradleRunner.line(e.getMessage() ?: e.getClass().getSimpleName())}. Nothing was written")
            stacktrace(e)
            return 1
        } catch (ProjectChangeException | IllegalArgumentException | PackArtifactException e) {
            err(GradleRunner.line(e.getMessage() ?: e.getClass().getSimpleName()))
            stacktrace(e)
            return 1
        }
    }

    private Integer moved(Path directory, ProjectManifest manifest, RecordMigration migration) {
        String running = "${ProjectLock.GENERATOR} ${VersionInfo.getVersion()} for Grails ${VersionInfo.getGrailsVersion()}"
        // the record this command wrote with a baseline says what the application is taken to be, which the command writes anew when it is told to
        String anew = !manifest.isAdopted() ? '' : '. The record was written for an application that was there, with the baseline of what it is taken to be: ' +
                "${UsageHelp.toType(spec, 'project migrate --force')} reads the files again and writes it anew, with the type, the options, --features and --exclude-features " +
                'where the record is not what the application has'
        if (migration.outcome == RecordMigration.Outcome.SAME_BUILD) {
            out("@|blue ||@ The record of the application ${shown(manifest.name)} was written by this build of the command line, ${running}: it is that of " +
                    "this version already, and there is nothing to move. Nothing was written${anew}")
            return 0
        }
        if (migration.outcome == RecordMigration.Outcome.SAME_FILES) {
            out("@|blue ||@ The record of the application ${shown(manifest.name)} was written by another build of ${running} (the generator sha256 " +
                    "${migration.recordedSha256 ?: 'not recorded'} there, ${VersionInfo.getGeneratorSha256() ?: 'none'} here), which writes the same record for " +
                    "its choices: it is that of this version already, and there is nothing to move. Nothing was written${anew}")
            return 0
        }
        out("@|blue ||@ The record of ${manifest.isAdopted() ? 'what' : 'how'} the application ${shown(manifest.name)} ${manifest.isAdopted() ? 'is taken to be' : 'was generated'}, " +
                "${dryRun ? 'as it would be moved' : 'moved'} to this version: " +
                'the application is generated again from the choices the record has, and nothing but the record is written')
        out("    generator  ${ProjectLock.GENERATOR} ${shown(migration.recordedVersion)} for Grails ${shown(migration.recordedGrailsVersion)}" +
                "${migration.recordedSha256 == null ? '' : " (generator sha256 ${migration.recordedSha256.take(SHORT_SHA256)})"}, now " +
                "${running}${VersionInfo.getGeneratorSha256() == null ? '' : " (generator sha256 ${VersionInfo.getGeneratorSha256().take(SHORT_SHA256)})"}")
        Map<String, String> files = new TreeMap<>()
        for (String path : migration.added) {
            files.put(path, 'added')
        }
        for (String path : migration.removed) {
            files.put(path, 'removed')
        }
        for (String path : migration.changed) {
            files.put(path, 'changed')
        }
        for (Map.Entry<String, String> file : files.entrySet()) {
            out("    ${file.value.padRight(8)} ${shown(file.key)}")
        }
        out('@|blue ||@ ' + (files.isEmpty() ? 'Every file of the baseline is as this version generates it. The record names this version' :
                "${files.size()} ${files.size() == 1 ? 'file' : 'files'} of the baseline, in ${ProjectState.BASELINE_FILES}, ${files.size() == 1 ? 'is' : 'are'} " +
                        "not as this version generates ${files.size() == 1 ? 'it' : 'them'}: ${counted(migration)}. The record names this version"))
        if (dryRun) {
            out('@|blue ||@ Nothing was written')
            return 0
        }
        try {
            migration.apply()
        } catch (ProjectChangeException e) {
            err(GradleRunner.line(e.getMessage() ?: e.getClass().getSimpleName()))
            stacktrace(e)
            return 1
        }
        out("@|blue ||@ The record was moved to this version: ${ProjectState.MANIFEST}, ${ProjectState.LOCK} and the baseline under ${ProjectState.BASELINE} " +
                "were written. No other file of the project was changed. Commit the directory ${ProjectState.DIRECTORY}")
        out('@|blue ||@ Where a file of the application is not what this version generates, as where an earlier version generated it otherwise, ' +
                "${UsageHelp.toType(spec, 'feature add')} takes it for your change and keeps it; ${UsageHelp.toType(spec, 'project regenerate --diff')} names those files")
        String projectVersion = EarlierGrailsProject.projectVersion(directory)
        if (projectVersion != null && projectVersion != VersionInfo.getGrailsVersion()) {
            out("@|blue ||@ The project is on Grails ${shown(projectVersion)} (gradle.properties), and the record is now of Grails ${VersionInfo.getGrailsVersion()}: " +
                    'the upgrade notes of the Grails guide say what changes with the version, gradle.properties among it')
        }
        out("@|blue ||@ ${ProjectHealthCommand.advice(spec)}")
        return 0
    }

    private static String counted(RecordMigration migration) {
        List<String> counts = []
        if (!migration.changed.isEmpty()) {
            counts << "${migration.changed.size()} changed".toString()
        }
        if (!migration.added.isEmpty()) {
            counts << "${migration.added.size()} added".toString()
        }
        if (!migration.removed.isEmpty()) {
            counts << "${migration.removed.size()} removed".toString()
        }
        return counts.join(', ')
    }

    private void stacktrace(Exception e) {
        if (showStacktrace()) {
            e.printStackTrace(spec.commandLine().getErr())
        }
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
     * What a command that generates the application again says when it refuses to, with the command
     * that moves the record to this version where that is what would let it go on: the record of an
     * earlier version, or of another build of this version that generates other files.
     *
     * @param spec the command that refuses
     * @param directory the directory of the project
     * @param failure why it refuses
     * @return the message of the failure, and where the record can be moved, the command line to type
     * for it
     */
    static String withMove(CommandLine.Model.CommandSpec spec, Path directory, Exception failure) {
        String message = failure.getMessage() ?: failure.getClass().getSimpleName()
        if (!(failure instanceof ProjectChangeException) ||
                !(((ProjectChangeException) failure).getReason() in [ProjectChangeException.OTHER_GENERATOR, ProjectChangeException.BASELINE_DIFFERS,
                                                                     ProjectChangeException.RECORD_DIFFERS])) {
            return message
        }
        ProjectLock lock
        try {
            lock = ProjectState.readSelection(directory, true).lock
        } catch (ProjectStateException ignored) {
            return message
        }
        return RecordMigration.isMovable(lock) ? "${message}. ${moveAdvice(spec)}".toString() : message
    }

    /**
     * @param spec the command that says it
     * @return the sentence that names the command that moves a record to this version, and what it does
     */
    static String moveAdvice(CommandLine.Model.CommandSpec spec) {
        return "${UsageHelp.toType(spec, "${ProjectGroupCommand.NAME} ${NAME}")} moves the record to this version, from the choices it has, and changes no file " +
                "of the application outside ${ProjectState.DIRECTORY}"
    }

    private static String written(Enum value) {
        return value.name().toLowerCase(Locale.ROOT).replace('_', '-')
    }

    private static String shown(String text) {
        return FeatureSelectionException.shown(text)
    }

    // a value with where it is from
    @CompileStatic
    private static final class Found<T> {

        final T value
        final String where
        final boolean assumed
        // assumed in place of what the project says, which this version does not generate for
        final boolean instead

        Found(T value, String where, boolean assumed = false, boolean instead = false) {
            this.value = value
            this.where = where
            this.assumed = assumed
            this.instead = instead
        }
    }

    @CompileStatic
    private static final class Identity {

        final Found<String> name
        final Found<String> defaultPackage
        final String refusal

        Identity(Found<String> name, Found<String> defaultPackage, String refusal) {
            this.name = name
            this.defaultPackage = defaultPackage
            this.refusal = refusal
        }
    }
}
