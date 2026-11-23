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
import org.apache.grails.forge.cli.console.ProjectCommand
import org.apache.grails.forge.cli.console.ProjectEvidence
import org.apache.grails.forge.cli.console.UsageHelp
import org.apache.grails.forge.core.pack.PackCondition
import org.apache.grails.forge.core.pack.PackDescriptor.PackDependency
import org.apache.grails.forge.core.pack.PackFeature
import org.apache.grails.forge.core.state.AdoptedProjectRecord
import org.apache.grails.forge.core.state.GenerationOptions
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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import picocli.CommandLine
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.function.Supplier

/**
 * Writes the record of the application in the current directory, for an application that has none:
 * one that the Grails shell created, one that Forge of an earlier version generated, and one that
 * was put together by hand. What the application is, is worked out from its files, see
 * {@link ProjectEvidence}, and from what is given on the command line, which counts for more.
 *
 * <p>Only the files of the record under {@code .grails} are written. The record says that it was
 * written for an application that was there ({@code generationState: untracked}) and has a baseline
 * without a file: the commands that create the classes of an application read it as they read the
 * record of a generated one, and no feature is added to the application by a command.</p>
 *
 * @since 9.0
 */
@CommandLine.Command(name = ProjectMigrateCommand.NAME, description = [
        'Writes the record of the application in the current directory, for an application that has none: one that was created with the Grails shell or ' +
                'by an earlier version, or was not generated at all. The commands that create its classes are there once it has the record.',
        'What the application is, is worked out from its files, which are read and not changed: the record an earlier version wrote, the profile ' +
                'it had, the plugins and the dependencies of build.gradle, and its configuration. What was found, where, and what was assumed is said; ' +
                'the options say it otherwise. Only the directory .grails is written to.',
        'The record says that it was written for an application that was there. No feature is added to such an application by a command, and it is ' +
                'not generated again: there is no copy of what the generator wrote to tell its changes from yours.'])
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
    private static final String GIVEN = 'given with '

    @CommandLine.Parameters(arity = '0..1', paramLabel = 'NAME',
            description = 'The name of the application, as a create command takes it: with its package in front, as com.example.shop, for the ' +
                    'package its classes are created in, or alone.')
    String name

    @CommandLine.Option(names = ['--type'], paramLabel = 'TYPE',
            description = 'The type of the application: web (what create-app creates), rest-api, plugin, web-plugin or rest-api-plugin.')
    String type

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

    @CommandLine.Option(names = ['--dry-run'], description = 'Say what the record would be, and write nothing.')
    boolean dryRun

    @CommandLine.Option(names = ['--force'],
            description = 'Write the record again over one this command wrote. The record of how an application was generated is never replaced.')
    boolean overwrite

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
        AvailableFeatures available = projectGeneratorSource.get().availableFeatures(applicationType.value, [])
        Map<String, String> found = featuresFound(available, evidence, notes)
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
        Map<String, Found<String>> options = options(applicationType.value, has.keySet(), evidence, notes)
        // a feature that is an option of the application is there with the option, as it is in an application that is created
        addFeatureOf(available, has, excluded, options.get('servlet'), ServletImpl.values().find { ServletImpl each -> written(each) == options.get('servlet').value }?.getName())
        addFeatureOf(available, has, excluded, options.get('data'), GormImpl.values().find { GormImpl each -> written(each) == options.get('data').value }?.getName())
        addFeatureOf(available, has, excluded, options.get('reloading'),
                DevelopmentReloading.values().find { DevelopmentReloading each -> written(each) == options.get('reloading').value }?.getFeatureName())

        List<Feature> applied = has.keySet().collect { String each -> available.findFeature(each, true).get() }
        applied.sort(FeatureContext.FEATURE_ORDER)
        Map<String, byte[]> files
        try {
            files = AdoptedProjectRecord.files(applicationType.value, identity.name.value, identity.defaultPackage.value,
                    new GenerationOptions(JdkVersion.valueOf(Integer.parseInt(options.get('jdk').value)),
                            ServletImpl.values().find { ServletImpl each -> written(each) == options.get('servlet').value },
                            GormImpl.values().find { GormImpl each -> written(each) == options.get('data').value },
                            DevelopmentReloading.values().find { DevelopmentReloading each -> written(each) == options.get('reloading').value },
                            !evidence.ownBuild),
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
        if (dryRun) {
            out('@|blue ||@ Nothing was written')
            return 0
        }
        return write(directory, files)
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
        if (manifest.isTracked()) {
            return "The application has the record of how it was generated (${ProjectState.MANIFEST}), which this command does not replace: ${UsageHelp.toType(spec, 'project show')} " +
                    'says what it has. Nothing was written'
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

    // the four options of the record: each as it was given, as the features that were found say it, or as it is assumed
    private Map<String, Found<String>> options(ApplicationType applicationType, Set<String> has, ProjectEvidence evidence, List<String> notes) {
        Map<String, Found<String>> options = new LinkedHashMap<>()
        if (javaVersion != null) {
            if (!JdkVersion.values().any { JdkVersion each -> each.majorVersion() == javaVersion }) {
                throw new CommandLine.ParameterException(spec.commandLine(), "--jdk is one of ${JdkVersion.values().collect { JdkVersion each -> each.majorVersion() }.join(', ')}")
            }
            options.put('jdk', new Found<String>(String.valueOf(javaVersion), GIVEN + '--jdk'))
        } else if (evidence.javaVersion != null && JdkVersion.values().any { JdkVersion each -> each.majorVersion() == evidence.javaVersion }) {
            options.put('jdk', new Found<String>(String.valueOf(evidence.javaVersion), "the version of Java ${evidence.buildFile} names"))
        } else {
            if (evidence.javaVersion != null) {
                notes << "${evidence.buildFile} names Java ${evidence.javaVersion}, which this version of Grails does not generate for".toString()
            }
            options.put('jdk', new Found<String>(String.valueOf(JdkVersion.DEFAULT_OPTION.majorVersion()), ASSUMED, true))
        }
        ServletImpl servlet = ServletImpl.values().find { ServletImpl each -> each.getName() in has }
        options.put('servlet', servletImpl != null ? new Found<String>(written(servletImpl), GIVEN + '--servlet') :
                servlet != null ? new Found<String>(written(servlet), "the feature ${servlet.getName()}") :
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
        if (applicationType.assumed) {
            assumed << 'the type (--type)'
        }
        options.each { String option, Found<String> value ->
            if (value.assumed) {
                assumed << "${option} (--${option})".toString()
            }
        }
        out('@|blue ||@ ' + (assumed.isEmpty() ? 'Nothing was assumed. ' : "Assumed, because nothing in the project says it: ${assumed.join(', ')}. ") +
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
                "The record says what the application is taken to be, not how it was generated: ${UsageHelp.toType(spec, 'feature add')} and ${UsageHelp.toType(spec, 'project regenerate')} are for an " +
                'application this version generated, and change nothing here')
        return 0
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

        Found(T value, String where, boolean assumed = false) {
            this.value = value
            this.where = where
            this.assumed = assumed
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
