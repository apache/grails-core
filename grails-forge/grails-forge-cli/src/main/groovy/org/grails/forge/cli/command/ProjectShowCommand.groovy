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

import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.console.ConsoleProject
import org.apache.grails.forge.cli.console.EarlierGrailsProject
import org.apache.grails.forge.cli.console.GradleRunner
import org.apache.grails.forge.cli.console.ProjectCommand
import org.apache.grails.forge.cli.console.UsageHelp
import org.apache.grails.forge.core.change.RecordMigration
import org.apache.grails.forge.core.pack.PackPreset
import org.apache.grails.forge.core.state.GenerationOptions
import org.apache.grails.forge.core.state.ProjectLock
import org.apache.grails.forge.core.state.ProjectLock.LockedFeature
import org.apache.grails.forge.core.state.ProjectLock.LockedRepository
import org.apache.grails.forge.core.state.ProjectManifest
import org.apache.grails.forge.core.state.ProjectManifest.RequestedFeature
import org.apache.grails.forge.core.state.ProjectState
import org.apache.grails.forge.core.state.ProjectStateException
import org.grails.forge.application.ApplicationType
import org.grails.forge.feature.FeatureSelectionException
import org.grails.forge.io.FileSystemOutputHandler
import org.grails.forge.options.BuildTool
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.GormImpl
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.ServletImpl
import org.grails.forge.options.TestFramework
import org.grails.forge.util.NameUtils
import org.grails.forge.util.VersionInfo
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import picocli.CommandLine
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.regex.Pattern

/**
 * Says how the application in the current directory was generated, from the record the generator
 * left under {@code .grails}, and with which command line it is created again.
 *
 * @since 9.0
 */
@CommandLine.Command(name = ProjectShowCommand.NAME, description = [
        'Says how the application in the current directory was generated: its type, name and package, its options, the features that were asked for ' +
                'and left out, and every feature it has.',
        'Every option of the create command is listed with the value the record has for it, also one that was not given, and with whether that value ' +
                'is the default of this version of the command line: a default can change from one version to the next.',
        'The last line is the command that creates the application again, with the version of the command line that generated it.'])
@Component
@Scope('prototype')
@CompileStatic
class ProjectShowCommand extends BaseCommand implements Callable<Integer>, ProjectCommand {

    public static final String NAME = 'show'

    private static final Map<ApplicationType, String> CREATE = [(ApplicationType.WEB): CreateAppCommand.NAME, (ApplicationType.REST_API): CreateRestApiCommand.NAME,
                                                                (ApplicationType.WEB_PLUGIN): CreateWebPluginCommand.NAME,
                                                                (ApplicationType.REST_API_PLUGIN): CreateRestApiPluginCommand.NAME,
                                                                (ApplicationType.PLUGIN): CreatePluginCommand.NAME] as Map<ApplicationType, String>

    /** The value is what a create command of this version takes when the option is not given. */
    public static final String DEFAULT = 'default'

    /** The value is another one than a create command of this version takes when the option is not given. */
    public static final String DIFFERS = 'differs'

    /** The record does not have the value: what is shown is what this version takes or generates. */
    public static final String NOT_RECORDED = 'not-recorded'

    /** The value is of the record and there is no default for it. */
    public static final String RECORDED = 'recorded'

    /** The options of a create command that are shown with their value, in the order they are shown. */
    public static final List<String> SHOWN_OPTIONS = Collections.unmodifiableList([
            '--jdk', '--servlet', '--data', '--reloading', '--subproject', '--features', '--exclude-features', '--feature-pack-dir',
            '--feature-pack-repository', '--feature-pack-sha256', '--feature-catalog', '--preset'])

    /**
     * The options of a create command that are not shown, because they say nothing of the application:
     * where it is written, what the command writes of itself and what it asks of the network.
     * {@code --feature-pack-jar} is not told from {@code --feature-pack-dir} by the record. An option
     * of a create command that is neither here nor in {@link #SHOWN_OPTIONS} is shown as one the record
     * does not have.
     */
    public static final Set<String> OPTIONS_NOT_OF_THE_APPLICATION = Collections.unmodifiableSet([
            '--inplace', '--list-features', '--list-catalog-features', '--refresh', '--offline', '--feature-pack-jar', '--help', '--version',
            '--stacktrace', '--verbose', '--plain-output', '--non-interactive'] as Set<String>)

    // how many characters of the identity of a build a line shows: the document has all of it
    private static final int SHORT_SHA256 = 12

    // what a shell takes as it is
    private static final Pattern PLAIN = Pattern.compile('[A-Za-z0-9_@%+=:,./-]+')

    @CommandLine.Option(names = ['--json'], description = 'Write the record and the command as one JSON document for a tool to read.')
    boolean json

    @Override
    Integer call() throws Exception {
        Path current = FileSystemOutputHandler.getDefaultBaseDirectory().toPath()
        ConsoleProject.Search search = ConsoleProject.search(current)
        if (search.project == null) {
            err(search.none('project show'))
            return 1
        }
        Path directory = search.project.directory
        if (!ProjectState.exists(directory) && !Files.exists(directory.resolve(ProjectState.LOCK), LinkOption.NOFOLLOW_LINKS) &&
                !Files.exists(directory.resolve(ProjectState.BASELINE_INDEX), LinkOption.NOFOLLOW_LINKS)) {
            err(noRecord(spec))
            return 1
        }
        ProjectState state
        try {
            // an option the record lacks is shown as the default, marked, and is no reason not to show the others
            state = ProjectState.readSelection(search.project.directory, true)
        } catch (ProjectStateException e) {
            err(GradleRunner.line(e.getMessage() ?: e.getClass().getSimpleName()))
            if (showStacktrace()) {
                e.printStackTrace(spec.commandLine().getErr())
            }
            return 1
        }
        List<String> arguments = arguments(state)
        List<String> notes = notes(spec, state, directory)
        String line = commandLine(spec.root().name(), arguments)
        List<Setting> settings = settings(state, createOptions(arguments.first()))
        if (json) {
            // written as it is: a document is not markup of the command line, and JSON writes a control character as an escape
            spec.commandLine().getOut().println(JsonOutput.toJson(document(state, arguments, line, notes, settings)))
            return 0
        }
        describe(state)
        describe(state, arguments.first(), settings)
        for (String note : notes) {
            out("@|blue ||@ ${GradleRunner.line(note)}")
        }
        out(state.manifest.isTracked() ? '@|blue ||@ The application is created again with:' :
                '@|blue ||@ A new application of this type, with these options and features, is created with:')
        // what a shell reads, and no markup of this command
        spec.commandLine().getOut().println(line)
        return 0
    }

    /**
     * @param state the state of a project
     * @return the arguments that create the application again, the name of the create command first:
     * the name with the package; each option whose value in the record is not what a create command of
     * this version takes when the option is not given, whatever the default was when the application
     * was generated, and for an application made from a preset every option of the record that a preset
     * can give, since the preset gives it a default of its own, which the record does not have; the
     * features that were asked for; a plugin by its coordinates with the SHA-256 of its jar and the
     * repository it came from; and the features that were left out
     */
    static List<String> arguments(ProjectState state) {
        ProjectManifest manifest = state.manifest
        GenerationOptions options = manifest.options
        List<String> arguments = [CREATE.get(manifest.applicationType), "${manifest.defaultPackage}.${manifest.name}".toString()]
        if (options.jdk != JdkVersion.DEFAULT_OPTION || ofPreset(manifest, 'jdk')) {
            arguments << '--jdk' << String.valueOf(options.jdk.majorVersion())
        }
        if (options.servlet != ServletImpl.DEFAULT_OPTION || ofPreset(manifest, 'servlet')) {
            arguments << '--servlet' << written(options.servlet)
        }
        if (options.data != GormImpl.DEFAULT_OPTION || ofPreset(manifest, 'data')) {
            arguments << '--data' << written(options.data)
        }
        if (options.reloading != DevelopmentReloading.DEFAULT_OPTION || ofPreset(manifest, 'reloading')) {
            arguments << '--reloading' << written(options.reloading)
        }
        if (options.subproject) {
            arguments << '--subproject'
        }
        Plugins plugins = new Plugins(state)
        if (plugins.preset != null) {
            arguments << '--preset' << plugins.preset
        }
        if (!plugins.features.isEmpty()) {
            arguments << '--features' << plugins.features.join(',')
        }
        for (String repository : plugins.repositories) {
            arguments << '--feature-pack-repository' << repository
        }
        for (String digest : plugins.digests) {
            arguments << '--feature-pack-sha256' << digest
        }
        if (!manifest.excludedFeatures.isEmpty()) {
            arguments << '--exclude-features' << manifest.excludedFeatures*.name.join(',')
        }
        return arguments
    }

    /**
     * Whether the create command is given an option of the record whatever its value: the application
     * was made from a preset, which can give the option a default of its own, and the record has the
     * value the application has, not whether it was given or what the preset gives. Left out, the option
     * would be the preset's again. An option the record does not have is left to the default, as for
     * any application.
     */
    private static boolean ofPreset(ProjectManifest manifest, String option) {
        return manifest.preset != null && PackPreset.OPTIONS.contains(option) && !manifest.options.notRecorded.contains(option)
    }

    /**
     * @param cli the name of the command line
     * @param arguments its arguments
     * @return one line that a POSIX shell reads as those arguments: an argument with anything in it
     * that a shell acts on is put between single quotes. What a terminal acts on is not written
     */
    static String commandLine(String cli, List<String> arguments) {
        return ([cli] + arguments).collect { String argument -> quoted(argument.replaceAll(/[\p{Cc}\p{Cf}\p{Zl}\p{Zp}]/, '?')) }.join(' ')
    }

    private static String quoted(String argument) {
        return PLAIN.matcher(argument).matches() ? argument : "'" + argument.replace("'", "'\\''") + "'"
    }

    /**
     * @param spec the command that says it
     * @return what is said in a project that has nothing of a record, with the command that writes one
     */
    static String noRecord(CommandLine.Model.CommandSpec spec) {
        return "There is no state of a generated project here: ${ProjectState.MANIFEST} is missing. For an application that was not generated by this version, " +
                "${UsageHelp.toType(spec, "${ProjectGroupCommand.NAME} ${ProjectMigrateCommand.NAME}")} writes the record"
    }

    // what the command line does not say and the command needs all the same to give the same application
    private static List<String> notes(CommandLine.Model.CommandSpec spec, ProjectState state, Path directory) {
        ProjectLock lock = state.lock
        List<String> notes = []
        if (!state.manifest.isTracked()) {
            notes << ("The record was written for an application that was there, by ${ProjectGroupCommand.NAME} ${ProjectMigrateCommand.NAME}, and not when the application " +
                    'was generated (its state is untracked): it says what the application is taken to be. The command creates a new application of that kind, not ' +
                    'this one, and no feature is added to this one by a command').toString()
        }
        Set<String> notRecorded = state.manifest.options.notRecorded
        if (!notRecorded.isEmpty()) {
            notes << ("The record does not have the ${notRecorded.size() == 1 ? 'option' : 'options'} ${notRecorded.collect { String key -> '--' + key }.join(', ')}: " +
                    "the command takes the default of this version of the command line for ${notRecorded.size() == 1 ? 'it' : 'them'}, which need not be what " +
                    'the application was generated with').toString()
        }
        String preset = state.manifest.preset
        if (preset != null && lock.features.any { LockedFeature feature -> feature.name == preset && feature.coordinates == null }) {
            notes << ("The application was made from the preset ${FeatureSelectionException.shown(preset)}, given as a directory or a jar, which the record " +
                    'does not name: give it to the command with --preset').toString()
        }
        List<String> given = lock.features.findAll { LockedFeature feature -> feature.source == LockedFeature.PACK && feature.coordinates == null && feature.name != preset }
                .collect { LockedFeature feature -> FeatureSelectionException.shown(feature.name) }
        if (!given.isEmpty()) {
            notes << ("The ${given.size() == 1 ? 'feature' : 'features'} ${given.join(', ')} ${given.size() == 1 ? 'is that of a feature pack' : 'are those of feature packs'} " +
                    'given as a directory or a jar, which the record does not name: give it to the command with --feature-pack-dir').toString()
        }
        List<String> unnamed = lock.features.findAll { LockedFeature feature -> feature.coordinates != null && feature.origin == null }
                .collect { LockedFeature feature -> FeatureSelectionException.shown("${feature.coordinates}:${feature.version}") }
        if (!unnamed.isEmpty()) {
            notes << ("The record does not name the repository ${unnamed.join(', ')} was taken from: give it to the command with --feature-pack-repository, " +
                    'unless it is one of those the command asks anyway').toString()
        }
        List<String> ofPacks = lock.features.findAll { LockedFeature feature -> feature.source == LockedFeature.PACK }
                .collect { LockedFeature feature -> FeatureSelectionException.shown(feature.name) }
        if (!ofPacks.isEmpty()) {
            notes << CreateCommand.laterCommands(ofPacks)
        }
        if (!lock.repositories.isEmpty() || lock.repositoriesNotNamed > 0) {
            notes << 'The application was generated with the repositories of the environment variable GRAILS_REPO_URL, which are written into its build: ' +
                    'set the variable to the same repositories for the command'
        }
        // the record of an earlier version, or of another build of this one, is moved to this version by a command that is named
        boolean movable = state.manifest.isTracked() && RecordMigration.isMovable(lock)
        String move = movable ? "; ${ProjectMigrateCommand.moveAdvice(spec)}" : ''
        if (lock.generatorVersion != VersionInfo.getVersion()) {
            notes << ("The application was generated by version ${FeatureSelectionException.shown(lock.generatorVersion)} of the command line, and this is " +
                    "${VersionInfo.getVersion()}: the command is for that version${move}").toString()
        } else if (isOtherBuild(lock)) {
            notes << ("The record was written by another build of version ${FeatureSelectionException.shown(lock.generatorVersion)} of the command line than this " +
                    "one (the generator sha256 ${lock.generatorSha256} there, ${VersionInfo.getGeneratorSha256()} here): the command gives the same application " +
                    "where the two builds generate the same files, and project regenerate --diff says whether they do${move}").toString()
        }
        // the record says how the application was generated, and is not changed when the project moves to another version by hand
        String projectVersion = EarlierGrailsProject.projectVersion(directory)
        if (projectVersion != null && projectVersion != state.manifest.grailsVersion) {
            String generated = "The project is on Grails ${FeatureSelectionException.shown(projectVersion)} (gradle.properties), and the record is of Grails " +
                    "${FeatureSelectionException.shown(state.manifest.grailsVersion)}: it says how the application was generated, and was not changed when the project " +
                    'moved to another version'
            if (movable) {
                notes << ("${generated}. ${UsageHelp.toType(spec, "${ProjectGroupCommand.NAME} ${ProjectMigrateCommand.NAME}")} moves it to this version of the " +
                        'command line, after which project regenerate and feature add run with it').toString()
            } else if (RecordMigration.isThisGenerator(lock)) {
                notes << ("${generated}. The record is that of this version of the command line: where the project is not what it generates, gradle.properties " +
                        'among it, project regenerate --diff names the file, and feature add keeps it as a change of yours').toString()
            } else {
                notes << ("${generated}. project regenerate and feature add are run by the command line of ${FeatureSelectionException.shown(lock.generatorVersion)}, " +
                        'installed, as ./grailsw starts the one of the version the project is on').toString()
            }
        }
        return notes
    }

    // a record that another build of the version of this command line wrote, as another snapshot of it does
    private static boolean isOtherBuild(ProjectLock lock) {
        return lock.generatorSha256 != null && VersionInfo.getGeneratorSha256() != null && lock.generatorSha256 != VersionInfo.getGeneratorSha256()
    }

    // which build of its version wrote the record, as far as it says, and whether that is this one
    private static String build(ProjectLock lock) {
        if (lock.generatorSha256 == null) {
            return '(not recorded)'
        }
        String running = VersionInfo.getGeneratorSha256()
        String which = lock.generatorVersion != VersionInfo.getVersion() || running == null ? '' :
                lock.generatorSha256 == running ? '  (this build)' : "  (another build than this one, which is ${running.take(SHORT_SHA256)})"
        return "generator sha256 ${lock.generatorSha256.take(SHORT_SHA256)}${which}"
    }

    private void describe(ProjectState state) {
        ProjectManifest manifest = state.manifest
        ProjectLock lock = state.lock
        GenerationOptions options = manifest.options
        out(manifest.isTracked() ? "@|blue ||@ How the application ${shown(manifest.name)} was generated, from its record in ${ProjectState.DIRECTORY}" :
                "@|blue ||@ What the application ${shown(manifest.name)} is taken to be, from the record that was written for it in ${ProjectState.DIRECTORY}")
        row('type', written(manifest.applicationType))
        row('name', shown(manifest.name))
        row('package', shown(manifest.defaultPackage))
        row('Grails', shown(manifest.grailsVersion))
        row(manifest.isTracked() ? 'generated by' : 'recorded by', "${shown(lock.generatorName)} ${shown(lock.generatorVersion)}")
        row('build', build(lock))
        row('state', shown(manifest.generationState))
        row('jdk', String.valueOf(options.jdk.majorVersion()))
        row('servlet', written(options.servlet))
        row('data', written(options.data))
        row('reloading', written(options.reloading))
        RequestedFeature preset = manifest.requestedFeatures.find { RequestedFeature feature -> manifest.preset != null && feature.name == manifest.preset }
        if (preset != null) {
            row('preset', preset.coordinates == null ? shown(preset.name) : "${shown(preset.name)} (${shown(preset.coordinates + ':' + preset.version)})".toString())
        }
        row('asked for', manifest.requestedFeatures.collect { RequestedFeature feature ->
            feature.coordinates == null ? shown(feature.name) : "${shown(feature.name)} (${shown(feature.coordinates + ':' + feature.version)})".toString()
        }.join(', ') ?: '(no features)')
        row('left out', manifest.excludedFeatures.collect { RequestedFeature feature -> shown(feature.name) }.join(', ') ?: '(no features)')
        for (LockedRepository repository : lock.repositories) {
            row('repository', repository.url == null ? shown(repository.kind) : "${shown(repository.kind)} ${shown(repository.url)}")
        }
        if (lock.repositoriesNotNamed > 0) {
            row('repository', "and ${lock.repositoriesNotNamed} more")
        }
        out("@|blue ||@ The ${lock.features.size()} features it has, in the order they are applied")
        for (LockedFeature feature : lock.features) {
            String plugin = feature.coordinates == null ? '' : ", ${shown(feature.coordinates + ':' + feature.version)}, sha256 ${shown(feature.sha256)}" +
                    (feature.origin == null ? '' : ", from ${shown(feature.origin)}")
            if (feature.contentSha256 != null) {
                plugin = ", content sha256 ${shown(feature.contentSha256)}"
            }
            out("    ${shown(feature.name)} (${shown(feature.source)}${plugin})")
        }
    }

    private void row(String what, String value) {
        out("    ${what.padRight(13)} ${value}")
    }

    /**
     * The options of the create command of the application, as picocli has them: what is shown is
     * every one of them, so that an option a create command gains is shown without a change here.
     */
    private List<String> createOptions(String create) {
        CommandLine command = spec.root().subcommands().get(create)
        return command == null ? SHOWN_OPTIONS : command.getCommandSpec().options().collect { CommandLine.Model.OptionSpec option -> nameOf(option.names().toList()) }
    }

    /**
     * @param names the names of an option of a create command
     * @return the one it is shown by: the one {@link #SHOWN_OPTIONS} or
     * {@link #OPTIONS_NOT_OF_THE_APPLICATION} has, and the longest one for an option neither has
     */
    static String nameOf(List<String> names) {
        return names.find { String name -> name in SHOWN_OPTIONS || name in OPTIONS_NOT_OF_THE_APPLICATION } ?: names.max { String name -> name.length() }
    }

    /**
     * @param state the state of a project
     * @param createOptions the options of the create command of its application type, by their longest names
     * @return what the application was generated with, one entry for each thing that could have been
     * another: the type, the name, the package and the version of Grails, then every option of the
     * create command but those of {@link #OPTIONS_NOT_OF_THE_APPLICATION} with the value the record has
     * and whether it is the default of this version, then the test framework and the build tool, which
     * the record does not have because there is one of each
     */
    static List<Setting> settings(ProjectState state, Collection<String> createOptions) {
        ProjectManifest manifest = state.manifest
        ProjectLock lock = state.lock
        GenerationOptions options = manifest.options
        Plugins plugins = new Plugins(state)
        String ofName = packageOf(manifest.name)
        List<Setting> settings = [
                new Setting('application-type', null, written(manifest.applicationType), null, RECORDED),
                new Setting('name', null, manifest.name, null, RECORDED),
                new Setting('package', null, manifest.defaultPackage, ofName, ofName == null ? RECORDED : manifest.defaultPackage == ofName ? DEFAULT : DIFFERS),
                new Setting('grails-version', null, manifest.grailsVersion, null, RECORDED)]
        // the preset is given with --preset, and not with --feature-pack-dir
        List<String> unnamedPacks = lock.features.findAll { LockedFeature feature ->
            feature.source == LockedFeature.PACK && feature.coordinates == null && feature.name != manifest.preset
        }*.name
        Map<String, Setting> known = new LinkedHashMap<>()
        known.put('--jdk', compared('--jdk', options.jdk.majorVersion(), JdkVersion.DEFAULT_OPTION.majorVersion(), options.notRecorded.contains('jdk')))
        known.put('--servlet', compared('--servlet', written(options.servlet), written(ServletImpl.DEFAULT_OPTION), options.notRecorded.contains('servlet')))
        known.put('--data', compared('--data', written(options.data), written(GormImpl.DEFAULT_OPTION), options.notRecorded.contains('data')))
        known.put('--reloading', compared('--reloading', written(options.reloading), written(DevelopmentReloading.DEFAULT_OPTION), options.notRecorded.contains('reloading')))
        known.put('--subproject', compared('--subproject', options.subproject, false, false))
        known.put('--features', compared('--features', plugins.features, [], false))
        known.put('--exclude-features', compared('--exclude-features', manifest.excludedFeatures*.name, [], false))
        // the record has the features of such a pack and not where the pack was
        known.put('--feature-pack-dir', unnamedPacks.isEmpty() ? compared('--feature-pack-dir', [], [], false) :
                new Setting('feature-pack-dir', '--feature-pack-dir', null, [], NOT_RECORDED))
        known.put('--feature-pack-repository', compared('--feature-pack-repository', plugins.repositories, [], false))
        known.put('--feature-pack-sha256', compared('--feature-pack-sha256', plugins.digests, [], false))
        known.put('--feature-catalog', compared('--feature-catalog', lock.features.findAll { LockedFeature feature -> feature.catalog != null }*.catalog.unique(false), [], false))
        // the preset of a directory or a jar is in the record by its name, and not where it was
        known.put('--preset', manifest.preset != null && plugins.preset == null ? new Setting('preset', '--preset', null, [], NOT_RECORDED) :
                compared('--preset', plugins.preset == null ? [] : [plugins.preset], [], false))
        for (String option : SHOWN_OPTIONS) {
            if (createOptions.contains(option)) {
                settings << known.get(option)
            }
        }
        for (String option : createOptions.findAll { String each -> !(each in SHOWN_OPTIONS) && !(each in OPTIONS_NOT_OF_THE_APPLICATION) }.sort(false)) {
            settings << new Setting(option.replaceFirst(/^-+/, ''), option, null, null, NOT_RECORDED)
        }
        settings << new Setting('test-framework', null, TestFramework.DEFAULT_OPTION.getName(), TestFramework.DEFAULT_OPTION.getName(), NOT_RECORDED)
        settings << new Setting('build-tool', null, BuildTool.DEFAULT_OPTION.getName(), BuildTool.DEFAULT_OPTION.getName(), NOT_RECORDED)
        return settings
    }

    /**
     * @param state the state of a project
     * @return the features the application has that were not asked for: they came with the application
     * type, with an option, or because another feature needs them, which the record does not tell apart
     */
    static List<String> addedFeatures(ProjectState state) {
        Set<String> requested = state.manifest.requestedFeatures*.name.toSet()
        return state.lock.featureNames.findAll { String feature -> !(feature in requested) }
    }

    private static Setting compared(String option, Object value, Object defaultValue, boolean notRecorded) {
        return new Setting(option.replaceFirst(/^-+/, ''), option, value, defaultValue, notRecorded ? NOT_RECORDED : value == defaultValue ? DEFAULT : DIFFERS)
    }

    // the package a create command gives an application whose name is given without one
    private static String packageOf(String name) {
        try {
            return NameUtils.parse(name).getPackageName()
        } catch (IllegalArgumentException ignored) {
            return null
        }
    }

    private void describe(ProjectState state, String create, List<Setting> settings) {
        boolean tracked = state.manifest.isTracked()
        out("@|blue ||@ Every option of ${create} with the value ${tracked ? 'the application was generated with' : 'the record has for it'}, and whether that is the default of this version " +
                "(${shown(VersionInfo.getVersion())}) of the command line")
        int width = settings.collect { Setting setting -> setting.label().length() }.max()
        for (Setting setting : settings) {
            out("    ${setting.label().padRight(width)}  ${text(setting.value)}${setting.remark() ? '  (' + setting.remark() + ')' : ''}")
        }
        List<String> added = addedFeatures(state)
        int asked = state.manifest.requestedFeatures.size()
        out("@|blue ||@ Of the ${state.lock.features.size()} features, ${asked} ${asked == 1 ? 'was' : 'were'} asked for (--features above) and " +
                "${added.size()} ${tracked ? 'came with the application type, with an option or with another feature' : 'were found in the project or came with an option'}:")
        out("    ${added.collect { String feature -> shown(feature) }.join(', ') ?: '(no features)'}")
    }

    private static String text(Object value) {
        if (value instanceof Collection) {
            return ((Collection) value).collect { Object each -> shown(String.valueOf(each)) }.join(', ') ?: '(none)'
        }
        if (value instanceof Boolean) {
            return value ? 'yes' : 'no'
        }
        return value == null ? '(not known)' : shown(String.valueOf(value))
    }

    /**
     * One thing an application was generated with: its name, the option of the create commands that
     * decides it where there is one, the value, the default of this version of the command line where
     * there is one, and how the two compare: {@link #DEFAULT}, {@link #DIFFERS}, {@link #NOT_RECORDED}
     * or {@link #RECORDED}.
     */
    @CompileStatic
    static final class Setting {

        final String name
        final String option
        final Object value
        final Object defaultValue
        final String status

        Setting(String name, String option, Object value, Object defaultValue, String status) {
            this.name = name
            this.option = option
            this.value = value
            this.defaultValue = defaultValue
            this.status = status
        }

        String label() {
            return option ?: name.replace('-', ' ').replace('grails', 'Grails')
        }

        String remark() {
            switch (status) {
                case DEFAULT:
                    return 'default'
                case DIFFERS:
                    return "differs from the current default: ${text(defaultValue)}"
                case NOT_RECORDED:
                    return option == null ? 'not recorded: the only one this version generates' :
                            value == null ? 'not recorded' : 'not recorded: the current default'
                default:
                    return null
            }
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>()
            map.put('name', name)
            if (option != null) {
                map.put('option', option)
            }
            if (value != null) {
                map.put('value', value)
            }
            if (defaultValue != null) {
                map.put('default', defaultValue)
            }
            map.put('status', status)
            return map
        }
    }

    // the features as --features takes them, and what the command needs to find the plugins among them again
    @CompileStatic
    private static final class Plugins {

        final List<String> features = []
        final List<String> digests = []
        final List<String> repositories = []
        /** The preset as --preset takes it, by its coordinates, or {@code null} when there is none or the record does not say where it was */
        String preset

        Plugins(ProjectState state) {
            for (RequestedFeature requested : state.manifest.requestedFeatures) {
                boolean isPreset = requested.name == state.manifest.preset
                if (requested.coordinates == null || requested.version == null) {
                    if (!isPreset) {
                        features << requested.name
                    }
                    continue
                }
                String plugin = "${requested.coordinates}:${requested.version}".toString()
                if (isPreset) {
                    preset = plugin
                } else {
                    features << plugin
                }
                LockedFeature locked = state.lock.features.find { LockedFeature feature -> feature.name == requested.name }
                if (locked != null && locked.sha256 != null) {
                    digests << "${plugin}=${locked.sha256}".toString()
                }
                if (locked != null && locked.origin != null && !repositories.contains(locked.origin)) {
                    repositories << locked.origin
                }
            }
        }
    }

    private static Map<String, Object> document(ProjectState state, List<String> arguments, String line, List<String> notes, List<Setting> settings) {
        ProjectManifest manifest = state.manifest
        ProjectLock lock = state.lock
        Map<String, Object> document = new LinkedHashMap<>()
        document.put('format', 1)
        document.put('applicationType', written(manifest.applicationType))
        document.put('name', manifest.name)
        document.put('defaultPackage', manifest.defaultPackage)
        document.put('grailsVersion', manifest.grailsVersion)
        document.put('generator', entries(name: lock.generatorName, version: lock.generatorVersion, sha256: lock.generatorSha256))
        document.put('templateApi', lock.templateApi)
        document.put('generationState', manifest.generationState)
        document.put('options', entries(jdk: manifest.options.jdk.majorVersion(), servlet: written(manifest.options.servlet), data: written(manifest.options.data),
                reloading: written(manifest.options.reloading)))
        document.put('requestedFeatures', manifest.requestedFeatures.collect { RequestedFeature feature ->
            entries(name: feature.name, coordinates: feature.coordinates, version: feature.version)
        })
        document.put('excludedFeatures', manifest.excludedFeatures*.name)
        document.put('repositories', lock.repositories.collect { LockedRepository repository -> entries(kind: repository.kind, url: repository.url) })
        document.put('repositoriesNotNamed', lock.repositoriesNotNamed)
        document.put('features', lock.features.collect { LockedFeature feature ->
            entries(name: feature.name, source: feature.source, coordinates: feature.coordinates, version: feature.version, sha256: feature.sha256, origin: feature.origin,
                    catalog: feature.catalog, catalogSha256: feature.catalogSha256, contentSha256: feature.contentSha256)
        })
        document.put('command', entries(name: arguments.first(), arguments: arguments.drop(1), line: line))
        document.put('notes', notes)
        // added after the first version of the document, at its end: the format is the same, and a reader that does not know them leaves them
        document.put('effectiveOptions', settings.collect { Setting setting -> setting.toMap() })
        document.put('addedFeatures', addedFeatures(state))
        // only of an application that was made from a preset
        RequestedFeature preset = manifest.requestedFeatures.find { RequestedFeature feature -> manifest.preset != null && feature.name == manifest.preset }
        if (preset != null) {
            document.put('preset', entries(name: preset.name, coordinates: preset.coordinates, version: preset.version))
        }
        return document
    }

    // in the order given, without what there is not
    private static Map<String, Object> entries(Map<String, Object> all) {
        Map<String, Object> entries = new LinkedHashMap<>()
        for (Map.Entry<String, Object> each : all.entrySet()) {
            if (each.value != null) {
                entries.put(each.key, each.value)
            }
        }
        return entries
    }

    private static String written(Enum value) {
        return value.name().toLowerCase(Locale.ROOT).replace('_', '-')
    }

    private static String shown(String text) {
        return FeatureSelectionException.shown(text)
    }
}
