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
import org.apache.grails.forge.cli.pack.PackArtifactException
import org.apache.grails.forge.cli.pack.PackArtifacts
import org.apache.grails.forge.cli.pack.PackCatalogs
import org.apache.grails.forge.cli.pack.PresetReference
import org.apache.grails.forge.core.bom.BomVersions
import org.apache.grails.forge.core.pack.Pack
import org.apache.grails.forge.core.pack.PackArtifact
import org.apache.grails.forge.core.pack.PackDescriptor.PackDependency
import org.apache.grails.forge.core.pack.PackDescriptorException
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

    @CommandLine.Option(names = ['-i', '--inplace'], description = 'Create a service using the current directory')
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
            description = 'Read each feature catalog anew, and not the copy of it that is kept on this computer for a day')
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
                    'the feature pack of a plugin is taken only from a repository that is a directory')
    boolean offline

    private PackArtifacts packArtifacts
    private Selection selection
    // the preset the application is made from, once it is taken: its options and features come before those of the framework
    private Pack presetPack
    private String notInCatalog = ''
    private String bomOfCatalogPacks

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
            return 0
        }
        Project project
        try {
            project = NameUtils.parse(name)
        } catch (IllegalArgumentException e) {
            throw new CommandLine.ParameterException(this.spec.commandLine(), (name == null || name.isEmpty()) ? 'Specify an application name or use --inplace to create an application in the current directory' : e.getMessage())
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
        if (subproject) {
            included(place, build)
        } else if (build != null && ConsoleProject.build(directory).getBuildRoot() != null) {
            // Gradle looks for the settings of a build in the directories above, and fails in a directory they do not include
            warning("${GradleRunner.line(directory)} is inside the Gradle build of ${GradleRunner.line(build)} and has no settings of its own, " +
                    'so Gradle takes it for a part of that build, which does not include it: its Gradle wrapper fails until it is. ' +
                    'An application that is to be a project of that build is created with --subproject, ' +
                    'and one that is to be a build of its own with the feature gradle-settings-file')
        }
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
        generate(NameUtils.parse(name), outputHandler)
    }

    void generate(Project project, OutputHandler outputHandler) throws Exception {
        // a version of Java there is not is said before anything is taken from a repository
        getJdkVersion()
        checkExcludedNames()
        List<Pack> packs = selection().packs
        // an option that is given is a choice, which the features that are selected do not replace; one a preset gives is a default
        Options options = options().withGiven(given()).withSubproject(subproject)
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
        PackArtifact artifact = pack.artifact
        out("@|blue ||@ ${pack.descriptor.preset ? 'Preset' : 'Feature pack'} ${pack.descriptor.name} from ${pack.origin.replaceAll(/\p{Cntrl}/, '?').replace('@|', '@ |')}" +
                (artifact == null ? '' : ", sha256 ${artifact.sha256}"))
        if (artifact != null) {
            // the digest says which jar was read; only a checksum from elsewhere says it is the one that was published
            if (artifact.catalog != null) {
                String catalog = artifact.catalog.replaceAll(/\p{Cntrl}/, '?').replace('@|', '@ |')
                out("    the coordinates are those the feature catalog ${catalog.length() > 200 ? catalog.substring(0, 200) + '...' : catalog} " +
                        "(sha256 ${artifact.catalogSha256}) has for the ${pack.descriptor.preset ? 'preset' : 'feature'} ${pack.descriptor.name}")
                // the catalog chose the jar and said what its digest is: that the two agree is no word of anyone else
                out('    the SHA-256 is the one that same catalog gives for the jar: the jar is the one the catalog means, which nobody else has checked')
            } else if (artifact.pinned) {
                out('    the jar has the SHA-256 that was asked for with --feature-pack-sha256')
            }
            out(artifact.checkedAgainst == null ? '    the jar was not checked: the repository gave no checksum for it' :
                    "    the jar matches the ${artifact.checkedAgainst} checksum the repository publishes for it")
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
        return new CommandLine.ParameterException(this.spec.commandLine(), (message + taken(message)).replaceAll(/\p{Cntrl}/, '?').replace('@|', '@ |') + notInCatalog)
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
        int at = pack.artifact.coordinates.lastIndexOf(':')
        String plugin = pack.artifact.coordinates.substring(0, at)
        String version = pack.artifact.coordinates.substring(at + 1)
        List<PackDependency> declared = pack.descriptor.dependencies.findAll { PackDependency dependency ->
            "${dependency.groupId}:${dependency.artifactId}".toString() == plugin
        }
        if (declared.isEmpty()) {
            return "The feature pack ${pack.descriptor.name} declares no dependency on ${plugin}, the plugin it was taken from: " +
                    'the application is generated without that plugin'
        }
        if (declared.any { PackDependency dependency -> dependency.version == null || dependency.version == version }) {
            return null
        }
        return "The feature pack ${pack.descriptor.name} was taken from version ${version} of ${plugin} and declares that plugin in " +
                "version ${declared*.version.unique().join(' and ')}: the application is generated with ${declared*.version.unique().join(' and ')}, " +
                "not with ${version}"
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
        // every mistake in the command line is found before a repository is asked for anything
        Map<String, String> digests = new LinkedHashMap<>()
        Map<String, PackArtifacts.Coordinates> plugins = new LinkedHashMap<>()
        List<URI> repositories
        String presetDigest = null
        List<String> unused = []
        // what a catalog can be asked for: a name that is not known here, or the full name of a feature of a plugin
        List<String> sought = getSelectedFeatures().findAll { String feature ->
            PackCatalogs.isId(feature) || (PackCatalogs.isName(feature) && !isOfTheFramework(feature) &&
                    !packs.any { Pack pack -> pack.descriptor.name == feature })
        }.unique()
        try {
            if (featureCatalogs) {
                PackCatalogs.addresses(featureCatalogs, null, false)
            }
            if (offline && refresh) {
                throw new IllegalArgumentException('--refresh reads a feature catalog anew and --offline asks nothing of the network: give one of them')
            }
            packArtifacts().offline = offline
            for (String given : featurePackDigests) {
                Map.Entry<String, String> digest = PackArtifacts.digest(given)
                if (digests.containsKey(digest.key) && digests.get(digest.key) != digest.value) {
                    throw new IllegalArgumentException("--feature-pack-sha256 gives two different SHA-256 for ${digest.key}")
                }
                digests.put(digest.key, digest.value)
            }
            for (String feature : getSelectedFeatures()) {
                if (PackArtifacts.isCoordinates(feature) && !PackCatalogs.isId(feature)) {
                    PackArtifacts.Coordinates coordinates = PackArtifacts.coordinates(feature)
                    plugins.put(coordinates.toString(), coordinates)
                }
            }
            // the SHA-256 of the artifact of a preset that is given by its coordinates is that of the preset, and of no feature
            presetDigest = presetReference?.form == PresetReference.Form.COORDINATES ? digests.remove(presetReference.coordinates.toString()) : null
            for (String pinned : digests.keySet()) {
                // a catalog can still give these coordinates for a name, of a feature or of the preset: then this is said after it was read
                if (!plugins.containsKey(pinned) && sought.isEmpty() && presetReference?.form != PresetReference.Form.NAME) {
                    throw new IllegalArgumentException("--feature-pack-sha256 names ${pinned}, which is not among the features asked for with --features")
                }
            }
            // the environment is only read when there is something to look for
            repositories = PackArtifacts.repositories(featurePackRepositories,
                    plugins.isEmpty() && presetReference?.form != PresetReference.Form.COORDINATES ? null : grailsRepoUrl(), unused)
        } catch (IllegalArgumentException e) {
            throw new CommandLine.ParameterException(this.spec.commandLine(), e.getMessage())
        }
        for (String each : unused) {
            warning(each.replaceAll(/\p{Cntrl}/, '?').replace('@|', '@ |'))
        }
        // the preset first: what it gives decides the BOM the features of a catalog are chosen for
        if (presetReference?.form == PresetReference.Form.COORDINATES) {
            presetPack = aPreset(packArtifacts().resolve(presetReference.coordinates, repositories, presetDigest), presetReference)
        } else if (presetReference?.form == PresetReference.Form.NAME) {
            presetPack = presetFromCatalogs(presetReference, digests)
        }
        if (presetPack != null && !presetPack.descriptor.applicationTypes.contains(applicationType)) {
            ApplicationType ofPreset = presetPack.descriptor.preset.applicationType
            throw new CommandLine.ParameterException(this.spec.commandLine(), "The preset ${presetPack.descriptor.name} is for an application of the type " +
                    "${PackPreset.nameOf(ofPreset)}, and ${this.spec.name()} creates one of the type ${PackPreset.nameOf(applicationType)}: create the application " +
                    "with ${CREATE_COMMANDS.get(ofPreset)} --preset ${FeatureSelectionException.shown(presetReference.given)}")
        }
        Map<String, String> names = new HashMap<>()
        for (PackArtifacts.Coordinates coordinates : plugins.values()) {
            Pack pack = notAPreset(packArtifacts().resolve(coordinates, repositories, digests.get(coordinates.toString())), coordinates.toString())
            packs << pack
            names.put(coordinates.toString(), pack.descriptor.name)
        }
        // a name the pack of a plugin that was given by its coordinates has is not looked for anywhere
        sought.removeAll { String feature -> packs.any { Pack pack -> pack.descriptor.name == feature } }
        if (!sought.isEmpty() || digests.keySet().any { String pinned -> !plugins.containsKey(pinned) }) {
            fromCatalogs(sought, packs, names, digests, plugins.isEmpty() ? null : repositories)
        }
        // the preset is a feature of the application, applied as the feature of a pack is, and asks for its features as their requirements
        List<String> features = presetPack == null ? [] : [presetPack.descriptor.name]
        if (presetPack != null) {
            packs.add(0, presetPack)
        }
        Set<String> taken = new HashSet<>()
        for (String feature : getSelectedFeatures()) {
            // the feature of a pack is one selection, however often and in whichever ways it was asked for:
            // by the coordinates of its plugin, by its full name and by its name
            String selected = PackArtifacts.isCoordinates(feature) && names.containsKey(feature) ? names.get(feature) : feature
            if (!names.containsValue(selected) || taken.add(selected)) {
                features << selected
            }
        }
        return new Selection(packs, features)
    }

    /**
     * Looks the names up that nothing here knows. A catalog says which plugin has the feature of a name
     * and which jar that is; the pack is then taken as the pack of a plugin that was given by its
     * coordinates is, with the digest of the catalog as the one the jar has to have. A name no catalog
     * has stays as it is, and is refused with the others that do not exist.
     */
    private void fromCatalogs(List<String> sought, List<Pack> packs, Map<String, String> names, Map<String, String> digests, List<URI> asked) {
        PackCatalogs catalogs = sought.isEmpty() ? null : catalogs()
        List<String> shown = sought.collect { String feature -> FeatureSelectionException.shown(feature) }
        Set<String> pinned = new HashSet<>()
        List<String> notFound = []
        if (catalogs != null && !catalogs.isEmpty()) {
            try {
                catalogs.read()
            } catch (PackArtifactException e) {
                throw new PackArtifactException(PackCatalogs.bounded("${shown.take(3).join(', ')}${shown.size() > 3 ? " and ${shown.size() - 3} more" : ''} " +
                        "${shown.size() == 1 ? 'is' : 'are'} not of Grails or of a feature pack that was given, " +
                        "and ${shown.size() == 1 ? 'was' : 'were'} looked for in a feature catalog. ${e.getMessage()}. A plugin can be given by its " +
                        'coordinates, group:artifact:version, and --feature-catalog names the catalog to read, or none'))
            }
            saidOf(catalogs)
            List<URI> repositories = asked
            // a full name first: the name alone is then the feature of the plugin that was named in full
            for (String feature : sought.findAll { String each -> PackCatalogs.isId(each) } + sought.findAll { String each -> !PackCatalogs.isId(each) }) {
                String name = PackCatalogs.nameOf(feature)
                String plugin = PackCatalogs.pluginOf(feature)
                // what was already taken for this command, by coordinates or by another way of naming it, is not taken again
                if (packs.any { Pack pack ->
                    pack.descriptor.name == name && (plugin == null || (pack.artifact != null && pack.artifact.coordinates.startsWith(plugin + ':')))
                }) {
                    names.put(feature, name)
                    continue
                }
                PackCatalogs.Choice choice
                try {
                    choice = catalogs.choose(feature, applicationTypeName(), VersionInfo.getGrailsVersion(), VersionInfo.getVersion(),
                            "${BomVersions.GROUP}:${bomOfTheSelection()}", this.&managedVersion)
                } catch (IllegalArgumentException e) {
                    throw new CommandLine.ParameterException(this.spec.commandLine(), e.getMessage().replaceAll(/\p{Cntrl}/, '?').replace('@|', '@ |'))
                }
                if (choice == null) {
                    notFound << feature
                    continue
                }
                if (repositories == null) {
                    List<String> unused = []
                    repositories = PackArtifacts.repositories(featurePackRepositories, grailsRepoUrl(), unused)
                    for (String each : unused) {
                        warning(each.replaceAll(/\p{Cntrl}/, '?').replace('@|', '@ |'))
                    }
                }
                PackArtifacts.Coordinates coordinates = choice.version.coordinates
                String from = choice.catalog.address
                // the artifact a catalog has for a version can be another than the plugin it is listed under, and one that was already taken
                if (packs.any { Pack pack -> pack.artifact != null && pack.artifact.coordinates == coordinates.toString() && pack.descriptor.name == name }) {
                    names.put(feature, name)
                    continue
                }
                String asPinned = digests.get(coordinates.toString())
                if (asPinned != null && asPinned != choice.version.sha256) {
                    throw new PackArtifactException("The feature catalog ${from} records the SHA-256 ${choice.version.sha256} for ${coordinates}, " +
                            "and ${asPinned} was asked for with --feature-pack-sha256")
                }
                pinned << coordinates.toString()
                List<URI> where = new ArrayList<>(repositories)
                // the repository the catalog names is asked last, when none of those the user has gave the jar, and not at all when repositories were given
                if (featurePackRepositories.isEmpty() && choice.version.repository != null && !where.contains(choice.version.repository)) {
                    where << choice.version.repository
                }
                // said before anything of the plugin is asked for: which plugin a catalog made of the name, and where it is looked for
                boolean ofTheCatalog = featurePackRepositories.isEmpty() && choice.version.repository != null && where.last() == choice.version.repository &&
                        !repositories.contains(choice.version.repository)
                List<URI> ofTheUser = ofTheCatalog ? where.take(where.size() - 1) : where
                boolean underOther = !coordinates.toString().startsWith(choice.plugin.coordinates + ':')
                out('@|blue ||@ ' + ("The feature ${name} is taken as ${coordinates}, " +
                        (underOther ? "the coordinates the catalog gives for version ${choice.version.version} of the plugin ${choice.plugin.coordinates}, " : '') +
                        "which the feature catalog ${from} (sha256 ${choice.catalog.sha256}) " +
                        "names for it, with the SHA-256 ${choice.version.sha256}. Asking ${ofTheUser.take(4).collect { URI each -> PackArtifacts.shownAddress(each) }.join(', ')}" +
                        "${ofTheUser.size() > 4 ? " and ${ofTheUser.size() - 4} more" : ''}" +
                        (ofTheCatalog ? "${ofTheUser.isEmpty() ? '' : ', and then '}${PackArtifacts.shownAddress(choice.version.repository)}, the repository the catalog names" : '')).replaceAll(/\p{Cntrl}/, '?').replace('@|', '@ |'))
                Pack pack = packArtifacts().resolve(coordinates, where, choice.version.sha256)
                if (pack.descriptor.name != choice.version.name) {
                    throw new PackArtifactException("The feature catalog ${from} says that ${coordinates} has the feature ${choice.version.name}, " +
                            "and the jar has the feature pack ${pack.descriptor.name}")
                }
                packs << pack.withArtifact(pack.artifact.withCatalog(choice.catalog.recordableAddress ?: choice.catalog.address, choice.catalog.sha256))
                bomOfCatalogPacks = bomOfTheSelection()
                names.put(feature, pack.descriptor.name)
            }
            // a name of Grails is the feature of Grails, whatever a catalog has under it
            for (String feature : getSelectedFeatures().unique(false)) {
                if (PackCatalogs.isName(feature) && isOfTheFramework(feature)) {
                    List<String> others = catalogs.pluginsWith(feature)
                    if (!others.isEmpty()) {
                        out("@|blue ||@ The feature ${feature} is that of Grails. The feature catalog has a feature of that name for " +
                                "${others.take(3).join(', ')}${others.size() > 3 ? " and ${others.size() - 3} more" : ''}, which is not used")
                    }
                }
            }
            notFound.sort { String feature -> sought.indexOf(feature) }
            if (catalogs.getUnread() != null) {
                // the catalog nobody named: a name that is not known is what it was without a catalog, and a line says what was not looked at
                notInCatalog = '\n' + PackCatalogs.bounded("${catalogs.getUnread()}, so ${shown.take(3).join(', ')}${shown.size() > 3 ? " and ${shown.size() - 3} more" : ''} " +
                        "${shown.size() == 1 ? 'was' : 'were'} not looked for there").replaceAll(/\p{Cntrl}/, '?').replace('@|', '@ |')
            } else if (!notFound.isEmpty()) {
                List<String> missed = notFound.collect { String feature -> FeatureSelectionException.shown(feature) }
                notInCatalog = PackCatalogs.bounded(". ${missed.take(3).join(', ')}${missed.size() > 3 ? " and ${missed.size() - 3} more" : ''} " +
                        "${missed.size() == 1 ? 'was' : 'were'} not found in ${catalogs.searched()} either").replaceAll(/\p{Cntrl}/, '?').replace('@|', '@ |')
            }
        }
        for (String each : digests.keySet()) {
            if (!pinned.contains(each) && !names.containsKey(each)) {
                throw new CommandLine.ParameterException(this.spec.commandLine(),
                        "--feature-pack-sha256 names ${each}, which is not among the features asked for with --features")
            }
        }
    }

    private void saidOf(PackCatalogs catalogs) {
        for (String each : catalogs.takeWarnings()) {
            warning(each.replaceAll(/\p{Cntrl}/, '?').replace('@|', '@ |'))
        }
    }

    private PackCatalogs catalogs() {
        try {
            if (offline && refresh) {
                throw new IllegalArgumentException('--refresh reads a feature catalog anew and --offline asks nothing of the network: give one of them')
            }
            packArtifacts().offline = offline
            String setting = featureCatalogSetting()
            // the plugin index the environment names for the commands that list plugins is the catalog of these too
            String index = pluginIndexSetting()
            boolean pluginIndex = readsPluginIndexUnnamed()
            return new PackCatalogs(packArtifacts(), PackCatalogs.addresses(featureCatalogs, setting, index, pluginIndex), featureCatalogHome(), refresh,
                    PackCatalogs.isByDefault(featureCatalogs, setting, index, pluginIndex), defaultCatalogWithin())
        } catch (IllegalArgumentException e) {
            throw new CommandLine.ParameterException(this.spec.commandLine(), e.getMessage())
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
        return GrailsBase.bomArtifactId(selected.contains('grails-micronaut'), data == GormImpl.HIBERNATE7 || selected.contains(GormImpl.HIBERNATE7.getName()))
    }

    /**
     * @return the version the BOM the application gets manages an artifact at, or {@code null} for one
     * it does not manage
     * @throws IllegalArgumentException when this Forge does not have the versions of that BOM: a name
     * is then not made a version of, since the build of the application could have the plugin in another
     */
    private String managedVersion(String artifact) {
        String bom = bomOfTheSelection()
        BomVersions versions = bomVersions(bom)
        if (versions == null) {
            throw new IllegalArgumentException("A feature is not taken from a feature catalog for an application with ${BomVersions.GROUP}:${bom}: this Forge " +
                    "does not have the versions that BOM manages (it has those of ${BomVersions.SHIPPED.collect { String each -> "${BomVersions.GROUP}:${each}" }.join(' and ')}), " +
                    'and so cannot tell which version of the plugin the application would have. Give the plugin by its coordinates, group:artifact:version')
        }
        return versions.versionOf(artifact)
    }

    // for a list, which says what would be taken where that is known and does not fail where it is not
    private String managedIfKnown(String artifact) {
        return bomVersions(bomOfTheSelection())?.versionOf(artifact)
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
        String actual = GrailsBase.bomArtifactId(context.getFeatures().contains('grails-micronaut'), context.getFeatures().contains('gorm-hibernate7'))
        if (bomOfCatalogPacks != null && bomOfCatalogPacks != actual) {
            throw new CommandLine.ParameterException(this.spec.commandLine(), 'The features that were found by their names in a feature catalog were chosen for an ' +
                    "application with ${BomVersions.GROUP}:${bomOfCatalogPacks}, and with all its features the application gets ${BomVersions.GROUP}:${actual}, " +
                    'which can manage their plugins at other versions. Name the feature that makes it so in --features, or the option for Grails Data, so that ' +
                    'the versions are chosen for that BOM')
        }
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
        Pack pack = packArtifacts().resolve(coordinates, where, choice.version.sha256)
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
