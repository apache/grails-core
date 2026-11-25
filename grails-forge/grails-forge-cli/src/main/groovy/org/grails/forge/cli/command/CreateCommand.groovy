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
import jakarta.annotation.Nonnull
import org.apache.grails.forge.cli.console.ConsoleProject
import org.apache.grails.forge.cli.console.GradleRunner
import org.apache.grails.forge.cli.console.OptionRelation
import org.apache.grails.forge.cli.console.UsageHelp
import org.apache.grails.forge.cli.pack.FeatureOptions
import org.apache.grails.forge.cli.pack.PackArtifactException
import org.apache.grails.forge.cli.pack.PackArtifacts
import org.apache.grails.forge.cli.pack.PackCatalogs
import org.apache.grails.forge.cli.pack.PackSelection
import org.apache.grails.forge.cli.pack.PresetReference
import org.apache.grails.forge.core.bom.BomVersions
import org.apache.grails.forge.core.pack.Pack
import org.apache.grails.forge.core.pack.PackDescriptorException
import org.apache.grails.forge.core.pack.PackInstructions
import org.apache.grails.forge.core.pack.PackLoader
import org.apache.grails.forge.core.pack.PackPreset
import org.apache.grails.forge.core.pack.PackRefusedException
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.ContextFactory
import org.grails.forge.application.Project
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.cli.ForgeContext
import org.grails.forge.feature.AvailableFeatures
import org.grails.forge.feature.FeatureSelectionException
import org.grails.forge.feature.grails.GrailsBase
import org.grails.forge.io.FileSystemOutputHandler
import org.grails.forge.io.OutputHandler
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.GormImpl
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.OptionName
import org.grails.forge.options.Options
import org.grails.forge.options.ServletImpl
import org.grails.forge.util.NameUtils
import org.grails.forge.util.VersionInfo
import picocli.CommandLine
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.Callable
import java.util.function.Supplier
import java.util.regex.Pattern

@CompileStatic
abstract class CreateCommand extends BaseCommand implements Callable<Integer>, OptionRelation.Declared {

    /** The environment variable that names the plugin index, which the create commands read as a feature catalog */
    public static final String PLUGIN_INDEX_VARIABLE = 'GRAILS_PLUGIN_INDEX'

    /** What {@code --list-catalog-features} says when no catalog is named */
    public static final String NO_CATALOG = 'No feature catalog is read: --feature-catalog, GRAILS_FORGE_CATALOG or GRAILS_PLUGIN_INDEX names one'

    /** The create command of each type of application, which a preset of that type is given to */
    static final Map<ApplicationType, String> CREATE_COMMANDS = Collections.unmodifiableMap([
            (ApplicationType.WEB): CreateAppCommand.NAME,
            (ApplicationType.REST_API): CreateRestApiCommand.NAME,
            (ApplicationType.PLUGIN): CreatePluginCommand.NAME,
            (ApplicationType.WEB_PLUGIN): CreateWebPluginCommand.NAME,
            (ApplicationType.REST_API_PLUGIN): CreateRestApiPluginCommand.NAME] as Map<ApplicationType, String>)

    @CommandLine.Parameters(arity = '0..1', paramLabel = 'NAME', description = 'The name of the application to create.')
    String name

    @Override
    List<OptionRelation> getOptionRelations() {
        // a digest is of a plugin that is asked for: see what the jars of the packs are taken with
        return [OptionRelation.requires('--feature-pack-sha256', '--features')]
    }

    @CommandLine.Option(names = ['-r', '--reloading'], paramLabel = 'RELOADING', description = 'Which development reloading option to use. Possible values: ${COMPLETION-CANDIDATES}.', completionCandidates = DevelopmentReloadingCandidates, converter = DevelopmentReloadingConverter)
    DevelopmentReloading reloading

    @CommandLine.Option(names = ['-d', '--data', '-g', '--gorm'], paramLabel = 'Grails Data Implementation', description = 'Which Grails Data implementation to configure (-g, --gorm are legacy aliases). Possible values: ${COMPLETION-CANDIDATES}.', completionCandidates = GormImplCandidates, converter = GormImplConverter)
    GormImpl gormImpl

    @CommandLine.Option(names = ['-s', '--servlet'], paramLabel = 'Servlet Implementation', description = 'Which Servlet Implementation to configure. Possible values: ${COMPLETION-CANDIDATES}.', completionCandidates = ServletImplCandidates, converter = ServletImplConverter)
    ServletImpl servletImpl

    @CommandLine.Option(names = ['-i', '--inplace'], description = 'Create the application in the current directory, and not in a new directory named after it. ' +
            'Without NAME, the application is named after the current directory.')
    boolean inplace

    @CommandLine.Option(names = ['--subproject'],
            description = 'Create the application as a project of the Gradle build the directory is in: only the files of the application ' +
                    'are written, and not those of the root of a build (the Gradle wrapper, the settings, the Grails wrapper), which that ' +
                    'build has. The settings of the build are not changed: the command says which line includes the application.')
    boolean subproject

    @CommandLine.Option(names = ['--list-features'], description = 'Output the available features and their descriptions')
    boolean listFeatures

    @CommandLine.Option(names = ['--jdk', '--java-version'], description = 'The JDK version the project should target')
    Integer javaVersion

    @CommandLine.Option(names = ['--exclude-features'], paramLabel = 'FEATURE', split = ',',
            description = 'The features to leave out of those the application would have by default, which --list-features marks ' +
                    '(with this option, as they are without what is left out). A feature that is selected, that a feature of the ' +
                    'application requires or that the application cannot be without cannot be left out.')
    List<String> excludedFeatures = new ArrayList<>()

    @CommandLine.Option(names = ['--feature-option'], paramLabel = 'FEATURE.NAME=VALUE',
            description = 'An option of a feature, which the feature pack of the feature declares: the name of the feature, a dot, the name of the ' +
                    'option and its value, as in acme-audit.store=collection. An option that is not given has the value the pack gives it. What a ' +
                    'feature takes is said with what its pack adds, and by --list-features for a pack that is given. Can be given more than once.')
    List<String> featureOptions = new ArrayList<>()

    @CommandLine.Option(names = ['--feature-pack-dir'], paramLabel = 'DIRECTORY',
            description = 'A directory that holds a feature pack (feature.yml and its templates directory), whose feature can then be ' +
                    'selected like one of the framework. For trying a pack out before it is published. A pack adds dependencies, ' +
                    'build plugins, build script code and files to the application: use a pack only when you trust where it comes from. ' +
                    'Can be given more than once.')
    List<File> featurePackDirectories = new ArrayList<>()

    // not shown either: a pack is to come from the plugin an application depends on, by its coordinates
    @CommandLine.Option(names = ['--feature-pack-jar'], paramLabel = 'JAR', hidden = true,
            description = 'The jar of a plugin that holds a feature pack under META-INF/grails-forge, whose feature can then be selected ' +
                    'like one of the framework. Use a pack only when you trust where it comes from. Can be given more than once.')
    List<File> featurePackJars = new ArrayList<>()

    @CommandLine.Option(names = ['--feature-pack-repository'], paramLabel = 'URL',
            description = 'A Maven repository to take the feature pack of a plugin from, when the plugin is given to --features by its ' +
                    'coordinates, as group:artifact:version. The address is https, or file: for a directory. When the option is given, ' +
                    'only the repositories it names are asked; without it, those of GRAILS_REPO_URL and then Maven Central are. ' +
                    'Can be given more than once.')
    List<String> featurePackRepositories = new ArrayList<>()

    @CommandLine.Option(names = ['--feature-pack-sha256'], paramLabel = 'COORDINATES=SHA256',
            description = 'The SHA-256 the jar of a plugin that is given to --features by its coordinates has to have, as ' +
                    'group:artifact:version=sha256. A jar with another one is refused before it is opened. Can be given more than once.')
    List<String> featurePackDigests = new ArrayList<>()

    @CommandLine.Option(names = ['--feature-catalog'], paramLabel = 'URL',
            description = 'A feature catalog, in which a name given to --features that is neither a feature of Grails nor that of a ' +
                    'feature pack that was given is looked up: the catalog says which plugin has the feature, in which version, with ' +
                    'which SHA-256, and can name a repository to take it from, and the feature pack of that plugin is then used. A pack is ' +
                    'code: name a catalog only when you trust whoever writes it as you would trust the plugins themselves. The address is ' +
                    'https, or file: for a file; none is for no catalog. Without the option, the catalogs of the GRAILS_FORGE_CATALOG ' +
                    'environment variable are read, else the plugin index the GRAILS_PLUGIN_INDEX environment variable names, and without ' +
                    'either none. Can be given more than once.')
    List<String> featureCatalogs = new ArrayList<>()

    @CommandLine.Option(names = ['--list-catalog-features'],
            description = 'Output the features of the feature catalogs that can be selected by their name, with the plugin and the ' +
                    'version each would be taken from')
    boolean listCatalogFeatures

    @CommandLine.Option(names = ['--refresh'],
            description = 'Read each feature catalog anew, and not the copy of it that is kept on this computer for a day, and fetch ' +
                    'the jar of each plugin whose feature pack is used, and not the copy of it that is kept on this computer')
    boolean refresh

    @CommandLine.Option(names = ['--preset'], paramLabel = 'PRESET',
            description = 'The preset the application is made from: a pack, of a company or a team, that is for this type of application, ' +
                    'gives the options their defaults, asks for features, and can add dependencies, configuration and files as a feature pack does. ' +
                    'It is given as the coordinates of its artifact, group:artifact:version, which are taken as those of a feature pack are; as a ' +
                    'jar or a directory, by a path with a / in it, such as ./acme-preset; or by its name in a feature catalog. An option that is ' +
                    'given on the command line is used in place of the one of the preset. A preset is code: use one only when you trust where it comes from.')
    String preset

    @CommandLine.Option(names = ['--offline'],
            description = 'Ask nothing of the network: a feature catalog is read from its copy on this computer or from a file, and ' +
                    'the feature pack of a plugin is taken from a repository that is a directory, or from the copy of its jar that is ' +
                    'kept on this computer since it was last fetched')
    boolean offline

    private PackArtifacts packArtifacts
    private Selection selection
    // the preset the application is made from, once it is taken: its options and features come before those of the framework
    private Pack presetPack
    // how the packs that are asked for are taken, which feature add shares
    private PackSelection packSelection
    // the features of the application that are of packs that are not part of Grails
    private List<String> notOfGrails = []
    // what the feature packs of the application say is to be done now that it is created
    private List<PackInstructions> instructions = []

    private final ApplicationType applicationType
    // what the Forge application has, asked of it when the command first needs it
    private final Supplier<? extends AvailableFeatures> availableFeaturesSource
    private final Supplier<ContextFactory> contextFactorySource
    private final Supplier<ProjectGenerator> projectGeneratorSource

    CreateCommand(AvailableFeatures availableFeatures,
                         ContextFactory contextFactory,
                         ApplicationType applicationType,
                         ProjectGenerator projectGenerator) {
        this.availableFeaturesSource = { availableFeatures } as Supplier<AvailableFeatures>
        this.contextFactorySource = { contextFactory } as Supplier<ContextFactory>
        this.applicationType = applicationType
        this.projectGeneratorSource = { projectGenerator } as Supplier<ProjectGenerator>
    }

    /**
     * The command on a Forge application that is started when the command first needs it: when it
     * creates an application or lists the features, and not when it is only put on the command line
     * or says how it is used.
     *
     * @param context the Forge application
     * @param featuresType the features an application of the type can have, as the application has them
     */
    protected CreateCommand(ForgeContext context, Class<? extends AvailableFeatures> featuresType, ApplicationType applicationType) {
        this.availableFeaturesSource = { context.get().getBean(featuresType) } as Supplier<AvailableFeatures>
        this.contextFactorySource = { context.get().getBean(ContextFactory) } as Supplier<ContextFactory>
        this.applicationType = applicationType
        this.projectGeneratorSource = { context.get().getBean(ProjectGenerator) } as Supplier<ProjectGenerator>
    }

    protected AvailableFeatures getAvailableFeatures() {
        return availableFeaturesSource.get()
    }

    private ContextFactory getContextFactory() {
        return contextFactorySource.get()
    }

    private ProjectGenerator getProjectGenerator() {
        return projectGeneratorSource.get()
    }

    /**
     * @return The selected features.
     */
    protected abstract @Nonnull List<String> getSelectedFeatures()

    @Override
    Integer call() throws Exception {
        try {
            return create()
        } catch (PackArtifactException e) {
            // nothing was written: the packs are taken before the application is. The command line was
            // right and what a repository gave is not, so the usage of the command is not shown
            return failed(e)
        } catch (FeatureSelectionException e) {
            // features that cannot be had as they were asked for are a mistake in the command line,
            // wherever in the command the refusal comes from
            throw mistake(e)
        } catch (IllegalArgumentException e) {
            // anything else an application cannot be made with, which a pack of a repository can cause
            // as well as the command line: said in its own words, not as a failure of this program, and
            // with the coordinates of the packs it can be about
            return failed(e, ofRepositories(e.getMessage() ?: ''))
        }
    }

    private Integer failed(RuntimeException e, String after = '') {
        List<String> lines = ((e.getMessage() ?: e.getClass().getSimpleName()) + after).readLines()
                .collect { String line -> line.replaceAll(/\p{Cntrl}/, '?').replace('@|', '@ |') }
        err(lines ? lines.first() : e.getClass().getSimpleName())
        for (String line : lines.drop(1)) {
            spec.commandLine().getErr().println(line)
        }
        if (showStacktrace()) {
            e.printStackTrace(spec.commandLine().getErr())
        }
        return 1
    }

    private Integer create() throws Exception {
        if (listCatalogFeatures) {
            PackCatalogs catalogs = catalogs()
            if (!catalogs.isEmpty() && catalogs.getUnread() != null) {
                // the list was asked for, and there is none
                throw new PackArtifactException(catalogs.getUnread())
            }
            List<String> lines = catalogs.isEmpty() ? [NO_CATALOG] :
                    catalogs.listing(applicationTypeName(), VersionInfo.getGrailsVersion(), VersionInfo.getVersion(), this.&isOfTheFramework,
                            "${BomVersions.GROUP}:${bomOfTheSelection()}", this.&managedIfKnown)
            saidOf(catalogs)
            for (String line : lines) {
                out(line.replaceAll(/\p{Cntrl}/, '?').replace('@|', '@ |'))
            }
            return 0
        }
        if (listFeatures) {
            checkExcludedNames()
            List<Pack> packs = selection().packs
            // a jar was taken from a repository for this list too: which one, and from where
            for (Pack pack : packs) {
                if (pack.artifact != null) {
                    origin(pack)
                }
            }
            try {
                new ListFeatures(packs.isEmpty() ? availableFeatures : withPacks(packs),
                        options(),
                        applicationType,
                        getOperatingSystem(),
                        contextFactory,
                        excludedFeatures).output(this)
            } catch (FeatureSelectionException e) {
                throw mistake(e)
            }
            // what the features of the packs that were given take, which no other list says
            for (String line : FeatureOptions.listing(packs.findAll { Pack pack -> !pack.bundled })) {
                String shown = line.replaceAll(/\p{Cntrl}/, '?').replace('@|', '@ |')
                out(line.startsWith(' ') ? shown : "@|blue ||@ ${shown}")
            }
            return 0
        }
        Project project
        try {
            project = toCreate()
        } catch (IllegalArgumentException e) {
            throw new CommandLine.ParameterException(this.spec.commandLine(), e.getMessage())
        }

        OutputHandler outputHandler = new FileSystemOutputHandler(project, inplace, this)
        Path directory = Path.of(outputHandler.getOutputLocation())
        ConsoleProject place = ConsoleProject.build(directory)
        Path build = place == null ? null : place.getBuildRoot()
        if (subproject && build == null) {
            // nothing is written: without a build above there is nothing the application would be a project of
            throw new CommandLine.ParameterException(this.spec.commandLine(), place != null ?
                    "--subproject creates the application as a project of a Gradle build, and ${GradleRunner.line(directory)} has the settings of a build itself: " +
                            'it is the root of one. Create the application in a directory below it' :
                    '--subproject creates the application as a project of an existing Gradle build, and there is no settings.gradle or settings.gradle.kts ' +
                            "in a directory above ${GradleRunner.line(directory)}, as far as the repository it is in goes. " +
                            'Run the command in a directory of the build, or leave the option out for an application that is a build of its own')
        }

        generate(project, outputHandler)

        out('@|blue ||@ Application created at ' + outputHandler.getOutputLocation())
        if (!notOfGrails.isEmpty()) {
            out('@|blue ||@ ' + laterCommands(notOfGrails))
        }
        if (subproject) {
            included(place, build)
        } else if (build != null && ConsoleProject.build(directory).getBuildRoot() != null) {
            // Gradle looks for the settings of a build in the directories above, and fails in a directory they do not include
            warning("${GradleRunner.line(directory)} is inside the Gradle build of ${GradleRunner.line(build)} and has no settings of its own, " +
                    'so Gradle takes it for a part of that build, which does not include it: its Gradle wrapper fails until it is. ' +
                    'An application that is to be a project of that build is created with --subproject, ' +
                    'and one that is to be a build of its own with the feature gradle-settings-file')
        }
        said(this, instructions)
        return 0
    }

    // The settings of a build are the user's: what includes the application is said, and nothing is written there
    private void included(ConsoleProject place, Path build) {
        String settings = ['settings.gradle', 'settings.gradle.kts'].find { String file -> build.resolve(file).toFile().isFile() }
        String path = place.getGradlePath()
        if (path == null) {
            out("@|blue ||@ The application is not a part of the build yet. Include its directory in ${GradleRunner.line(build.resolve(settings))}, " +
                    'under a name Gradle takes for a project: the names of the directories from there to the application are not such names, ' +
                    'so the commands of the application are then run by Gradle from its directory, and not by its path in the build')
            return
        }
        String project = path.substring(1)
        out('@|blue ||@ The application is not a part of the build yet. Add this line to ' + GradleRunner.line(build.resolve(settings)) + ':')
        out('    ' + (settings.endsWith('.kts') ? "include(\"${project}\")" : "include '${project}'"))
        out("@|blue ||@ Its tasks are then those of the project ${path}, run with the Gradle wrapper of the build, as the commands of the application do")
    }

    void generate(OutputHandler outputHandler) throws Exception {
        generate(toCreate(), outputHandler)
    }

    /**
     * @return the application the command creates: the one of the name that is given, and with
     * {@code --inplace} and no name the one that is named after the current directory
     * @throws IllegalArgumentException when there is no name, or it is not one an application can have
     */
    private Project toCreate() throws IOException {
        if (name != null && !name.isEmpty()) {
            return NameUtils.parse(name)
        }
        if (!inplace) {
            throw new IllegalArgumentException('Specify an application name or use --inplace to create an application in the current directory')
        }
        return namedAfter(FileSystemOutputHandler.getDefaultBaseDirectory())
    }

    /**
     * The application that {@code --inplace} creates without a name, which is named after the directory
     * it is created in, as the Grails shell named it. The name of the directory is taken as a name that
     * is given without a package, and the package is made of it as it is of such a name. What the name
     * of an application cannot be is refused, and so are two things a name on the command line can have:
     * a dot, which there ends the package, and a character that the name of a class cannot have, since
     * classes of the application are named after it. The name is then given on the command line.
     */
    private Project namedAfter(File directory) {
        String named = directory.getName()
        String why
        if (named.isEmpty()) {
            why = 'the root of a file system has no name'
        } else if (named.contains('.')) {
            why = 'it has a dot, which in the name of an application ends its package'
        } else {
            try {
                Project project = NameUtils.parse(named)
                if (NameUtils.isValidJavaIdentifier(project.getClassName())) {
                    return project
                }
                why = "it has a character that the name of a class cannot have: only letters, digits, '_' and '-' between words"
            } catch (IllegalArgumentException e) {
                why = (e.getMessage() ?: e.getClass().getSimpleName()).replaceAll('\\.$', '')
            }
        }
        throw new IllegalArgumentException("With --inplace and no name the application is named after the current directory, ${GradleRunner.line(directory)}, " +
                "and its name is not one an application can have: ${why}. Give the name: ${UsageHelp.toType(spec, "${spec.name()} --inplace NAME")}")
    }

    void generate(Project project, OutputHandler outputHandler) throws Exception {
        // a version of Java there is not is said before anything is taken from a repository, and so is an option that is not written as one
        getJdkVersion()
        optionsOfFeatures()
        checkExcludedNames()
        List<Pack> packs = selection().packs
        // an option that is given is a choice, which the features that are selected do not replace; one a preset gives is a default
        Options options = options().withGiven(given()).withSubproject(subproject).withFeatureOptions(optionsOfFeatures())
        if (!packs.isEmpty()) {
            withPacks(packs)
        }
        GeneratorContext context
        try {
            context = projectGenerator.createGeneratorContext(applicationType, project, options, getOperatingSystem(),
                    selection().features, this, packs, excludedFeatures)
        } catch (FeatureSelectionException e) {
            throw mistake(e)
        }
        heldToItsBom(context)
        instructions = PackInstructions.of(context.getFeatures().getFeatures())
        notOfGrails = packs.findAll { Pack pack -> !pack.bundled && context.getFeatures().contains(pack.descriptor.name) }
                .collect { Pack pack -> FeatureSelectionException.shown(pack.descriptor.name) }
        List<String> warnings = []
        // a pack is not part of the framework: say which ones this application is made with, which are
        // the selected ones and those they require. What a descriptor says is not markup of this command
        for (Pack pack : packs) {
            if (context.getFeatures().contains(pack.descriptor.name)) {
                origin(pack)
                for (String line : pack.summary()) {
                    out("    ${line.replace('@|', '@ |')}")
                }
                // a preset is not a plugin the application is to depend on: its artifact carries the preset and nothing else
                if (pack.artifact != null && !pack.descriptor.preset) {
                    String unlike = unlikeItsPlugin(pack)
                    if (unlike != null) {
                        warnings << unlike
                    }
                }
            }
        }
        projectGenerator.generate(applicationType, project, outputHandler, context)
        // of the application that was made: one that is refused is not warned of as if it had been
        for (String each : warnings) {
            warning(each)
        }
    }

    /**
     * Says what the feature packs say is to be done next, after everything the command itself says. The
     * text is that of a descriptor: it is printed as text, and nothing of it is markup of this command.
     */
    static void said(BaseCommand command, List<PackInstructions> instructions) {
        for (PackInstructions each : instructions) {
            command.out("@|blue ||@ The feature ${each.feature.replace('@|', '@ |')} says what to do next:")
            for (String line : each.lines) {
                command.out("    ${line.replace('@|', '@ |')}")
            }
        }
    }

    /**
     * What an application with features of feature packs that are not part of Grails needs of the
     * commands that generate it again, which is said when it is created and by {@code project show}.
     *
     * @param features the names of those features, as they can be shown
     * @return one line
     */
    static String laterCommands(List<String> features) {
        return ("The ${features.size() == 1 ? 'feature' : 'features'} ${features.take(5).join(', ')}${features.size() > 5 ? " and ${features.size() - 5} more" : ''} " +
                "${features.size() == 1 ? 'is that of a feature pack' : 'are those of feature packs'} that ${features.size() == 1 ? 'is' : 'are'} not part of Grails: " +
                'feature add and project regenerate work for the application only with that very pack, which its record names by its SHA-256 ' +
                '(the jar of a plugin is fetched again by its coordinates; the pack of a directory is given again with --feature-pack-dir)').toString()
    }

    /**
     * What is said after a refusal that names no pack, when packs of repositories are among what the
     * application was to be made with: a pack can be what is refused, and the command line names it by
     * coordinates only.
     */
    private String ofRepositories(String message) {
        String named = taken(message)
        if (!named.isEmpty() || selection == null) {
            return named
        }
        List<String> origins = selection.packs.findAll { Pack pack -> pack.artifact != null }
                .collect { Pack pack -> "${pack.descriptor.name} of ${pack.origin}".toString() }
        return origins.isEmpty() ? '' : ". The application was to be made with the feature ${origins.size() == 1 ? 'pack' : 'packs'} ${origins.join(', ')}"
    }

    /**
     * Says where a pack came from, and of one that was taken from a repository which jar it was.
     */
    private void origin(Pack pack) {
        for (String line : PackSelection.origin(pack, packArtifacts())) {
            out(line)
        }
    }

    // the pack of the jar of a plugin, from a repository or from the copy of the jar that is kept on this computer
    private Pack jarOf(PackArtifacts.Coordinates coordinates, List<URI> repositories, String sha256) {
        return packSelection().jarOf(coordinates, repositories, sha256)
    }

    // the options of features, which are held to the features of the application when it is generated
    private Map<String, Map<String, String>> optionsOfFeatures() {
        try {
            return FeatureOptions.parse(featureOptions)
        } catch (IllegalArgumentException e) {
            throw new CommandLine.ParameterException(this.spec.commandLine(), e.getMessage())
        }
    }

    private Set<OptionName> given() {
        Set<OptionName> given = EnumSet.noneOf(OptionName)
        if (gormImpl != null) {
            given.add(OptionName.DATA)
        }
        if (servletImpl != null) {
            given.add(OptionName.SERVLET)
        }
        if (reloading != null) {
            given.add(OptionName.RELOADING)
        }
        return given
    }

    // an exclusion is the name of a feature; a plugin is not left out by its coordinates, and this is
    // said before a repository is asked for anything
    private void checkExcludedNames() {
        for (String excluded : excludedFeatures) {
            if (excluded.contains(':')) {
                throw new CommandLine.ParameterException(this.spec.commandLine(),
                        "--exclude-features takes the names of features, and ${FeatureSelectionException.shown(excluded)} " +
                                'is not one: the feature of a plugin is excluded by the name of the feature, not by the coordinates of the plugin')
            }
        }
    }

    /**
     * Features and options that cannot be had as they were asked for are a mistake in the command line,
     * like an option that does not exist. Of an option that does not agree with the features the
     * message names what was typed, which need not be the name and the value the generator knows it by.
     */
    private CommandLine.ParameterException mistake(FeatureSelectionException e) {
        String message = e.getMessage()
        if (e.getOption() != null) {
            message = "The option ${typed(e.getOption())} does not agree with the features that are selected or excluded: " +
                    'the application would be made ' + (e.getWithout() != null ?
                    "without ${e.getWithout()}. Leave out the option, or the feature that takes its place" :
                    "with ${e.getWith()}. Leave out the option, or the feature that requires it")
        }
        return new CommandLine.ParameterException(this.spec.commandLine(), (message + taken(message)).replaceAll(/\p{Cntrl}/, '?').replace('@|', '@ |') + packSelection().getNotInCatalog())
    }

    /**
     * A refusal names a feature, and the feature of a pack that was taken from a repository is one the
     * command line does not name: it gave coordinates. Which coordinates each such feature of the message
     * came by is said after it, so that it can be found in what was typed.
     */
    private String taken(String message) {
        StringBuilder taken = new StringBuilder()
        for (Pack pack : (selection == null ? Collections.<Pack>emptyList() : selection.packs)) {
            if (pack.artifact != null &&
                    Pattern.compile('(?<![A-Za-z0-9-])' + Pattern.quote(pack.descriptor.name) + '(?![A-Za-z0-9-])').matcher(message).find()) {
                taken.append(". The feature ${pack.descriptor.name} is that of the feature pack of ${pack.origin}")
            }
        }
        return taken.toString()
    }

    private String typed(OptionName option) {
        List<String> names = option == OptionName.DATA ? ['-d', '--data', '-g', '--gorm'] :
                option == OptionName.SERVLET ? ['-s', '--servlet'] : ['-r', '--reloading']
        CommandLine.ParseResult parsed = this.spec.commandLine().getParseResult()
        List<String> arguments = parsed == null ? Collections.<String>emptyList() : parsed.originalArgs()
        for (int i = 0; i < arguments.size(); i++) {
            String argument = arguments.get(i)
            for (String name : names) {
                if (argument == name && i + 1 < arguments.size()) {
                    return "${name} ${FeatureSelectionException.shown(arguments.get(i + 1))}"
                }
                if (argument.startsWith(name + '=') || (name.length() == 2 && argument.length() > 2 && argument.startsWith(name))) {
                    return FeatureSelectionException.shown(argument)
                }
            }
        }
        return names.last()
    }

    /**
     * The features to choose from with the packs among them. Packs that cannot be used together, such
     * as one directory given twice, are a mistake in the command line.
     */
    private AvailableFeatures withPacks(List<Pack> packs) {
        try {
            return projectGenerator.availableFeatures(applicationType, packs)
        } catch (PackRefusedException e) {
            // two packs of one name were both asked for, which is the command line's doing. A pack that
            // cannot be used whatever it is asked for with (the name of a feature of the framework, a
            // group of it, versions it is not for) is, when it was taken from a repository, a fault of
            // what the repository gave: the message names the pack by its coordinates and repository
            if (e.isOfThePack() && e.getPacks().any { Pack pack -> pack.artifact != null }) {
                throw new PackArtifactException(e.getMessage())
            }
            throw new CommandLine.ParameterException(this.spec.commandLine(), e.getMessage().replace('@|', '@ |'))
        } catch (IllegalArgumentException e) {
            throw new CommandLine.ParameterException(this.spec.commandLine(), (e.getMessage() ?: '').replace('@|', '@ |'))
        }
    }

    /**
     * A pack adds its plugin to the application only by declaring it. What it declares can be something
     * else than the plugin it was taken from: no dependency on it, or one on another version of it.
     *
     * @return what to warn of, or {@code null} when the application gets the plugin the pack was taken
     * from, in that version or in the one a platform of the build gives it
     */
    private static String unlikeItsPlugin(Pack pack) {
        return PackSelection.unlikeItsPlugin(pack)
    }

    /**
     * What was asked for, read once: the packs, and the names of the features with the name of its pack
     * in the place of each plugin that was given by its coordinates.
     */
    private Selection selection() {
        if (selection == null) {
            selection = select()
        }
        return selection
    }

    private Selection select() {
        List<Pack> packs = featurePacks()
        PresetReference presetReference = presetReference()
        if (presetReference != null && presetReference.form in [PresetReference.Form.DIRECTORY, PresetReference.Form.JAR]) {
            presetPack = presetOfPath(presetReference)
        }
        // the features and the preset are taken together, as every command takes the feature of a pack
        PackSelection.Taken taken = packSelection().select(packs, new PackSelection.Preset() {

            @Override
            String getCoordinates() {
                return presetReference?.form == PresetReference.Form.COORDINATES ? presetReference.coordinates.toString() : null
            }

            @Override
            boolean isByName() {
                return presetReference?.form == PresetReference.Form.NAME
            }

            @Override
            void take(List<URI> repositories, String digest, Map<String, String> digests) {
                takePreset(presetReference, repositories, digest, digests)
            }

            @Override
            Pack getPack() {
                return presetPack
            }
        })
        return new Selection(taken.packs, taken.features)
    }

    // the preset where it was given by its coordinates or its name, and that it is one for this type of application
    private void takePreset(PresetReference presetReference, List<URI> repositories, String presetDigest, Map<String, String> digests) {
        if (presetReference?.form == PresetReference.Form.COORDINATES) {
            presetPack = aPreset(jarOf(presetReference.coordinates, repositories, presetDigest), presetReference)
        } else if (presetReference?.form == PresetReference.Form.NAME) {
            presetPack = presetFromCatalogs(presetReference, digests)
        }
        if (presetPack != null && !presetPack.descriptor.applicationTypes.contains(applicationType)) {
            ApplicationType ofPreset = presetPack.descriptor.preset.applicationType
            throw new CommandLine.ParameterException(this.spec.commandLine(), "The preset ${presetPack.descriptor.name} is for an application of the type " +
                    "${PackPreset.nameOf(ofPreset)}, and ${this.spec.name()} creates one of the type ${PackPreset.nameOf(applicationType)}: create the application " +
                    "with ${CREATE_COMMANDS.get(ofPreset)} --preset ${FeatureSelectionException.shown(presetReference.given)}")
        }
    }

    private void saidOf(PackCatalogs catalogs) {
        packSelection().saidOf(catalogs)
    }

    private PackCatalogs catalogs() {
        return packSelection().catalogs()
    }

    private PackSelection packSelection() {
        if (packSelection == null) {
            packSelection = new PackSelection(new SelectionHost())
        }
        return packSelection
    }

    /** What the command gives the taking of its packs: its options, what it says, and what it reads of the environment. */
    private final class SelectionHost implements PackSelection.Host {

        @Override
        List<String> getAskedFeatures() {
            return CreateCommand.this.getSelectedFeatures()
        }

        @Override
        String getAskedWith() {
            return 'asked for with --features'
        }

        @Override
        List<String> getPackRepositories() {
            return featurePackRepositories
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
            return offline
        }

        @Override
        String getApplicationTypeName() {
            return CreateCommand.this.applicationTypeName()
        }

        @Override
        String getBomArtifact() {
            return CreateCommand.this.bomOfTheSelection()
        }

        @Override
        boolean isKnownFeature(String name) {
            return CreateCommand.this.isOfTheFramework(name)
        }

        @Override
        Pack notAPreset(Pack pack, String given) {
            return CreateCommand.this.notAPreset(pack, given)
        }

        @Override
        PackArtifacts getPackArtifacts() {
            return CreateCommand.this.packArtifacts()
        }

        @Override
        String getCatalogSetting() {
            return CreateCommand.this.featureCatalogSetting()
        }

        @Override
        String getPluginIndexSetting() {
            return CreateCommand.this.pluginIndexSetting()
        }

        @Override
        boolean isPluginIndexReadUnnamed() {
            return CreateCommand.this.readsPluginIndexUnnamed()
        }

        @Override
        Path getCatalogHome() {
            return CreateCommand.this.featureCatalogHome()
        }

        @Override
        String getGrailsRepoUrl() {
            return CreateCommand.this.grailsRepoUrl()
        }

        @Override
        Duration getDefaultCatalogWithin() {
            return CreateCommand.this.defaultCatalogWithin()
        }

        @Override
        BomVersions bomVersionsOf(String artifact) {
            return CreateCommand.this.bomVersions(artifact)
        }

        @Override
        CommandLine.Model.CommandSpec getCommandSpec() {
            return CreateCommand.this.spec
        }

        @Override
        void say(String line) {
            CreateCommand.this.out(line)
        }

        @Override
        void warn(String line) {
            CreateCommand.this.warning(line)
        }
    }

    /**
     * The BOM of Grails the application gets, as far as the command line says it: by the option for
     * Grails Data and by the features of the framework it names, which are known before any catalog is
     * read, and by the option and the features of the preset, once it is taken. A feature that another
     * one requires can make it a different one, which {@link #heldToItsBom} finds once the features of
     * the application are known.
     *
     * @return the artifact of the BOM
     */
    protected String bomOfTheSelection() {
        List<String> selected = getSelectedFeatures() + (presetPack == null ? Collections.<String>emptyList() : presetPack.descriptor.requires)
        GormImpl data = gormImpl ?: presetPack?.descriptor?.preset?.data
        return GrailsBase.bomArtifactId(selected.contains(GrailsBase.MICRONAUT), data == GormImpl.HIBERNATE7 || selected.contains(GrailsBase.HIBERNATE7),
                data == GormImpl.NEO4J || selected.contains(GrailsBase.NEO4J))
    }

    // the version the BOM the application gets manages an artifact at, which is refused where this Forge does not have the versions of that BOM
    private String managedVersion(String artifact) {
        return packSelection().managedVersion(artifact)
    }

    // for a list, which says what would be taken where that is known and does not fail where it is not
    private String managedIfKnown(String artifact) {
        return packSelection().managedIfKnown(artifact)
    }

    /**
     * @param artifact the artifact of a BOM of Grails
     * @return the versions it manages, or {@code null} when this Forge has none for it
     */
    protected BomVersions bomVersions(String artifact) {
        return BomVersions.of(artifact)
    }

    /**
     * Holds the packs that a catalog gave for names to the BOM they were chosen for. Which version of
     * a plugin was taken depends on the BOM; the features of the application, once all are known, can
     * give it another BOM than the command line said, and then the choice is not made again: the
     * command says so and creates nothing.
     */
    private void heldToItsBom(GeneratorContext context) {
        packSelection().heldToItsBom(GrailsBase.bomArtifactId(context.getFeatures()), 'Name the feature that makes it so in --features, or the option for ' +
                'Grails Data, so that the versions are chosen for that BOM')
    }

    /**
     * @return how long the feature catalog nobody named can take
     */
    protected Duration defaultCatalogWithin() {
        return PackCatalogs.DEFAULT_WITHIN
    }

    private String applicationTypeName() {
        return applicationType.getName().replace('_', '-')
    }

    private PackArtifacts packArtifacts() {
        if (packArtifacts == null) {
            packArtifacts = createPackArtifacts()
        }
        return packArtifacts
    }

    /**
     * @return what takes the jar of a plugin and a feature catalog from where they are
     */
    protected PackArtifacts createPackArtifacts() {
        return new PackArtifacts()
    }

    /**
     * @return the feature catalogs that are read when the command names none: addresses separated by
     * semicolons, {@code none} for no catalog, or {@code null} when the environment names none
     */
    protected String featureCatalogSetting() {
        return System.getenv('GRAILS_FORGE_CATALOG')
    }

    /**
     * @return the plugin index the environment names for every command that reads one
     * ({@code GRAILS_PLUGIN_INDEX}): an address, {@code none}, or {@code null} when it names none. It
     * is the catalog when neither {@code --feature-catalog} nor {@link #featureCatalogSetting()} names one
     */
    protected String pluginIndexSetting() {
        return System.getenv(PLUGIN_INDEX_VARIABLE)
    }

    /**
     * Whether the plugin index of Grails is read as a catalog when neither the command nor the
     * environment names one. It is not, and nothing a user sets makes it so: this is here for the day
     * the index records packs, and for the specifications of what the command then does.
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
     * @return whether a name is that of a feature of the framework, for whichever type of application:
     * such a name means that feature whatever is created, and is never looked for in a catalog
     */
    private boolean isOfTheFramework(String name) {
        return availableFeatures.findFeature(name, true).isPresent() || ApplicationType.values().any { ApplicationType type ->
            projectGenerator.availableFeatures(type, Collections.<Pack>emptyList()).findFeature(name, true).isPresent()
        }
    }

    /**
     * @return the repositories of the build of a generated application, which are asked for a feature
     * pack when the command names none
     */
    protected String grailsRepoUrl() {
        return System.getenv('GRAILS_REPO_URL')
    }

    private static final class Selection {

        final List<Pack> packs
        final List<String> features

        Selection(List<Pack> packs, List<String> features) {
            this.packs = packs
            this.features = features
        }
    }

    /**
     * The feature packs of the directories given with {@code --feature-pack-dir} and of the jars given
     * with {@code --feature-pack-jar}.
     */
    private List<Pack> featurePacks() {
        List<Pack> packs = []
        PackLoader loader = new PackLoader()
        for (File directory : featurePackDirectories) {
            try {
                packs << notAPreset(loader.fromDirectory(directory.toPath().toAbsolutePath().normalize()), directory.path)
            } catch (PackDescriptorException e) {
                throw new CommandLine.ParameterException(this.spec.commandLine(), e.getMessage())
            }
        }
        for (File jar : featurePackJars) {
            try {
                packs << notAPreset(loader.fromJar(jar.toPath().toAbsolutePath().normalize()), jar.path)
            } catch (PackDescriptorException e) {
                throw new CommandLine.ParameterException(this.spec.commandLine(), e.getMessage())
            }
        }
        return packs
    }

    /**
     * @return what {@code --preset} names, or {@code null} when the option was not given
     */
    private PresetReference presetReference() {
        if (preset == null) {
            return null
        }
        try {
            return PresetReference.of(preset)
        } catch (IllegalArgumentException e) {
            throw new CommandLine.ParameterException(this.spec.commandLine(), e.getMessage().replace('@|', '@ |'))
        }
    }

    /**
     * The options of the application: each one that was given, else the one of the preset, else the
     * default of the framework. None is marked as given here.
     */
    private Options options() {
        JdkVersion jdk = javaVersion == null ? null : getJdkVersion()
        if (presetPack == null) {
            return new Options(reloading, gormImpl, servletImpl, jdk ?: JdkVersion.DEFAULT_OPTION, getOperatingSystem())
        }
        return presetPack.descriptor.preset.optionsWith(reloading, gormImpl, servletImpl, jdk, getOperatingSystem())
    }

    /**
     * The preset of a directory or a jar that was given with {@code --preset}, read as the feature pack
     * of one is read with {@code --feature-pack-dir} or {@code --feature-pack-jar}.
     */
    private Pack presetOfPath(PresetReference reference) {
        PackLoader loader = new PackLoader()
        try {
            Pack pack = reference.form == PresetReference.Form.DIRECTORY ? loader.fromDirectory(reference.path) : loader.fromJar(reference.path)
            return aPreset(pack, reference)
        } catch (PackDescriptorException e) {
            throw new CommandLine.ParameterException(this.spec.commandLine(), e.getMessage().replace('@|', '@ |'))
        }
    }

    /**
     * A pack that was given with {@code --preset} has to be a preset: a feature pack is selected with
     * {@code --features}, and is no preset of an application.
     */
    private Pack aPreset(Pack pack, PresetReference reference) {
        if (!pack.descriptor.preset) {
            throw new CommandLine.ParameterException(this.spec.commandLine(), "--preset ${FeatureSelectionException.shown(reference.given)} " +
                    "has the feature pack ${pack.descriptor.name}, which is not a preset (its kind is feature): the feature of a pack is selected " +
                    'with --features, by the coordinates of its plugin or its name in a feature catalog, or with --feature-pack-dir')
        }
        return pack
    }

    /**
     * A pack that was given as a feature pack, by its coordinates, a directory or a jar, has to be one:
     * a preset is given with {@code --preset}, and is not selected as a feature.
     */
    private Pack notAPreset(Pack pack, String given) {
        if (pack.descriptor.preset) {
            throw new CommandLine.ParameterException(this.spec.commandLine(), "${FeatureSelectionException.shown(given)} has the preset " +
                    "${pack.descriptor.name}, which is not a feature: an application is made from a preset with --preset")
        }
        return pack
    }

    /**
     * The preset of a name, looked up in the feature catalogs as the name of a feature is: the catalog
     * says which plugin has it, in which version and with which SHA-256, and the preset is then taken
     * from the jar as the preset of coordinates is, with the SHA-256 of the catalog as the one the jar
     * has to have.
     */
    private Pack presetFromCatalogs(PresetReference reference, Map<String, String> digests) {
        String shown = FeatureSelectionException.shown(reference.given)
        PackCatalogs catalogs = catalogs()
        if (catalogs.isEmpty()) {
            boolean there = Files.exists(Path.of(reference.given), LinkOption.NOFOLLOW_LINKS)
            throw new CommandLine.ParameterException(this.spec.commandLine(), "--preset ${shown} is the name of a preset, which is looked up in a feature " +
                    'catalog, and no catalog is read: --feature-catalog or GRAILS_FORGE_CATALOG names one. A preset is also given by the coordinates of ' +
                    "its artifact, group:artifact:version, or by a path with a / in it${there ? ", such as ./${shown} for the one of that name here" : ''}")
        }
        try {
            catalogs.read()
        } catch (PackArtifactException e) {
            throw new PackArtifactException(PackCatalogs.bounded("The preset ${shown} was looked for in a feature catalog. ${e.getMessage()}"))
        }
        saidOf(catalogs)
        if (catalogs.getUnread() != null) {
            throw new CommandLine.ParameterException(this.spec.commandLine(), PackCatalogs.bounded("The preset ${shown} was not looked for: " +
                    catalogs.getUnread()).replaceAll(/\p{Cntrl}/, '?').replace('@|', '@ |'))
        }
        PackCatalogs.Choice choice
        try {
            choice = catalogs.choosePreset(reference.given, applicationTypeName(), VersionInfo.getGrailsVersion(), VersionInfo.getVersion(),
                    "${BomVersions.GROUP}:${bomOfTheSelection()}", this.&managedVersion)
        } catch (IllegalArgumentException e) {
            throw new CommandLine.ParameterException(this.spec.commandLine(), e.getMessage().replaceAll(/\p{Cntrl}/, '?').replace('@|', '@ |'))
        }
        if (choice == null) {
            throw new CommandLine.ParameterException(this.spec.commandLine(), PackCatalogs.bounded("There is no preset ${shown} in ${catalogs.searched()}. " +
                    'A preset is also given by the coordinates of its artifact, group:artifact:version, or by a path with a / in it')
                    .replaceAll(/\p{Cntrl}/, '?').replace('@|', '@ |'))
        }
        List<String> unused = []
        List<URI> repositories = PackArtifacts.repositories(featurePackRepositories, grailsRepoUrl(), unused)
        for (String each : unused) {
            warning(each.replaceAll(/\p{Cntrl}/, '?').replace('@|', '@ |'))
        }
        PackArtifacts.Coordinates coordinates = choice.version.coordinates
        String from = choice.catalog.address
        String asPinned = digests.remove(coordinates.toString())
        if (asPinned != null && asPinned != choice.version.sha256) {
            throw new PackArtifactException("The feature catalog ${from} records the SHA-256 ${choice.version.sha256} for ${coordinates}, " +
                    "and ${asPinned} was asked for with --feature-pack-sha256")
        }
        List<URI> where = new ArrayList<>(repositories)
        // the repository the catalog names is asked last, and not at all when repositories were given
        boolean ofTheCatalog = featurePackRepositories.isEmpty() && choice.version.repository != null && !where.contains(choice.version.repository)
        if (ofTheCatalog) {
            where << choice.version.repository
        }
        out('@|blue ||@ ' + ("The preset ${choice.version.name} is taken as ${coordinates}, which the feature catalog ${from} (sha256 ${choice.catalog.sha256}) " +
                "names for it, with the SHA-256 ${choice.version.sha256}. Asking ${repositories.take(4).collect { URI each -> PackArtifacts.shownAddress(each) }.join(', ')}" +
                "${repositories.size() > 4 ? " and ${repositories.size() - 4} more" : ''}" +
                (ofTheCatalog ? "${repositories.isEmpty() ? '' : ', and then '}${PackArtifacts.shownAddress(choice.version.repository)}, the repository the catalog names" : ''))
                .replaceAll(/\p{Cntrl}/, '?').replace('@|', '@ |'))
        Pack pack = jarOf(coordinates, where, choice.version.sha256)
        if (pack.descriptor.name != choice.version.name || !pack.descriptor.preset) {
            throw new PackArtifactException("The feature catalog ${from} says that ${coordinates} has the preset ${choice.version.name}, " +
                    "and the jar has the ${pack.descriptor.preset ? 'preset' : 'feature pack'} ${pack.descriptor.name}")
        }
        return pack.withArtifact(pack.artifact.withCatalog(choice.catalog.recordableAddress ?: choice.catalog.address, choice.catalog.sha256))
    }

    private JdkVersion getJdkVersion() {
        if (javaVersion == null) {
            return JdkVersion.DEFAULT_OPTION
        } else {
            try {
                return JdkVersion.valueOf(javaVersion)
            } catch (IllegalArgumentException e) {
                throw new CommandLine.ParameterException(this.spec.commandLine(), e.getMessage())
            }
        }
    }
}
