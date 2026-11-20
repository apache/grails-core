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
import org.apache.grails.forge.cli.pack.PackArtifactException
import org.apache.grails.forge.cli.pack.PackArtifacts
import org.apache.grails.forge.core.pack.Pack
import org.apache.grails.forge.core.pack.PackArtifact
import org.apache.grails.forge.core.pack.PackDescriptor.PackDependency
import org.apache.grails.forge.core.pack.PackDescriptorException
import org.apache.grails.forge.core.pack.PackLoader
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.ContextFactory
import org.grails.forge.application.Project
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.feature.AvailableFeatures
import org.grails.forge.feature.FeatureSelectionException
import org.grails.forge.io.FileSystemOutputHandler
import org.grails.forge.io.OutputHandler
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.GormImpl
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.OptionName
import org.grails.forge.options.Options
import org.grails.forge.options.ServletImpl
import org.grails.forge.util.NameUtils
import picocli.CommandLine
import java.util.concurrent.Callable
import java.util.regex.Pattern

@CompileStatic
abstract class CreateCommand extends BaseCommand implements Callable<Integer> {

    protected final AvailableFeatures availableFeatures

    @CommandLine.Parameters(arity = '0..1', paramLabel = 'NAME', description = 'The name of the application to create.')
    String name

    @CommandLine.Option(names = ['-r', '--reloading'], paramLabel = 'RELOADING', description = 'Which development reloading option to use. Possible values: ${COMPLETION-CANDIDATES}.', completionCandidates = DevelopmentReloadingCandidates, converter = DevelopmentReloadingConverter)
    DevelopmentReloading reloading

    @CommandLine.Option(names = ['-d', '--data', '-g', '--gorm'], paramLabel = 'Grails Data Implementation', description = 'Which Grails Data implementation to configure (-g, --gorm are legacy aliases). Possible values: ${COMPLETION-CANDIDATES}.', completionCandidates = GormImplCandidates, converter = GormImplConverter)
    GormImpl gormImpl

    @CommandLine.Option(names = ['-s', '--servlet'], paramLabel = 'Servlet Implementation', description = 'Which Servlet Implementation to configure. Possible values: ${COMPLETION-CANDIDATES}.', completionCandidates = ServletImplCandidates, converter = ServletImplConverter)
    ServletImpl servletImpl

    @CommandLine.Option(names = ['-i', '--inplace'], description = 'Create a service using the current directory')
    boolean inplace

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

    private final PackArtifacts packArtifacts = new PackArtifacts()
    private Selection selection

    private final ContextFactory contextFactory
    private final ApplicationType applicationType
    private final ProjectGenerator projectGenerator

    CreateCommand(AvailableFeatures availableFeatures,
                         ContextFactory contextFactory,
                         ApplicationType applicationType,
                         ProjectGenerator projectGenerator) {
        this.availableFeatures = availableFeatures
        this.contextFactory = contextFactory
        this.applicationType = applicationType
        this.projectGenerator = projectGenerator
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
            // as well as the command line: said in its own words, not as a failure of this program
            return failed(e)
        }
    }

    private Integer failed(RuntimeException e) {
        List<String> lines = (e.getMessage() ?: e.getClass().getSimpleName()).readLines()
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
                        new Options(reloading, gormImpl, servletImpl, getJdkVersion(), getOperatingSystem()),
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

        generate(project, outputHandler)

        out('@|blue ||@ Application created at ' + outputHandler.getOutputLocation())
        return 0
    }

    void generate(OutputHandler outputHandler) throws Exception {
        generate(NameUtils.parse(name), outputHandler)
    }

    void generate(Project project, OutputHandler outputHandler) throws Exception {
        Options options = new Options(reloading, gormImpl, servletImpl, getJdkVersion(), getOperatingSystem())
        checkExcludedNames()
        // an option that is given is a choice, which the features that are selected do not replace
        options = options.withGiven(given())
        List<Pack> packs = selection().packs
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
        // a pack is not part of the framework: say which ones this application is made with, which are
        // the selected ones and those they require. What a descriptor says is not markup of this command
        for (Pack pack : packs) {
            if (context.getFeatures().contains(pack.descriptor.name)) {
                origin(pack)
                for (String line : pack.summary()) {
                    out("    ${line.replace('@|', '@ |')}")
                }
                if (pack.artifact != null) {
                    String unlike = unlikeItsPlugin(pack)
                    if (unlike != null) {
                        warning(unlike)
                    }
                }
            }
        }
        projectGenerator.generate(applicationType, project, outputHandler, context)
    }

    /**
     * Says where a pack came from, and of one that was taken from a repository which jar it was.
     */
    private void origin(Pack pack) {
        PackArtifact artifact = pack.artifact
        out("@|blue ||@ Feature pack ${pack.descriptor.name} from ${pack.origin.replaceAll(/\p{Cntrl}/, '?').replace('@|', '@ |')}" +
                (artifact == null ? '' : ", sha256 ${artifact.sha256}"))
        if (artifact != null) {
            // the digest says which jar was read; only a checksum from elsewhere says it is the one that was published
            if (artifact.pinned) {
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
        return new CommandLine.ParameterException(this.spec.commandLine(), (message + taken(message)).replaceAll(/\p{Cntrl}/, '?').replace('@|', '@ |'))
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
        } catch (IllegalArgumentException e) {
            // two packs of one name were both asked for, which is the command line's doing. A pack that
            // cannot be used whatever it is asked for with (the name of a feature of the framework, a
            // group of it, versions it is not for) is, when it was taken from a repository, a fault of
            // what the repository gave: the message names the pack by its coordinates and repository
            String message = e.getMessage() ?: ''
            if (!message.startsWith('The feature packs ') && packs.any { Pack pack -> pack.artifact != null && message.contains(pack.origin) }) {
                throw new PackArtifactException(message)
            }
            throw new CommandLine.ParameterException(this.spec.commandLine(), message.replace('@|', '@ |'))
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
        // every mistake in the command line is found before a repository is asked for anything
        Map<String, String> digests = new LinkedHashMap<>()
        Map<String, PackArtifacts.Coordinates> plugins = new LinkedHashMap<>()
        List<URI> repositories
        List<String> unused = []
        try {
            for (String given : featurePackDigests) {
                Map.Entry<String, String> digest = PackArtifacts.digest(given)
                if (digests.containsKey(digest.key) && digests.get(digest.key) != digest.value) {
                    throw new IllegalArgumentException("--feature-pack-sha256 gives two different SHA-256 for ${digest.key}")
                }
                digests.put(digest.key, digest.value)
            }
            for (String feature : getSelectedFeatures()) {
                if (PackArtifacts.isCoordinates(feature)) {
                    PackArtifacts.Coordinates coordinates = PackArtifacts.coordinates(feature)
                    plugins.put(coordinates.toString(), coordinates)
                }
            }
            for (String pinned : digests.keySet()) {
                if (!plugins.containsKey(pinned)) {
                    throw new IllegalArgumentException("--feature-pack-sha256 names ${pinned}, which is not among the features asked for with --features")
                }
            }
            // the environment is only read when there is something to look for
            repositories = PackArtifacts.repositories(featurePackRepositories, plugins.isEmpty() ? null : grailsRepoUrl(), unused)
        } catch (IllegalArgumentException e) {
            throw new CommandLine.ParameterException(this.spec.commandLine(), e.getMessage())
        }
        for (String each : unused) {
            warning(each.replaceAll(/\p{Cntrl}/, '?').replace('@|', '@ |'))
        }
        Map<String, String> names = new HashMap<>()
        for (PackArtifacts.Coordinates coordinates : plugins.values()) {
            Pack pack = packArtifacts.resolve(coordinates, repositories, digests.get(coordinates.toString()))
            packs << pack
            names.put(coordinates.toString(), pack.descriptor.name)
        }
        List<String> features = []
        Set<String> taken = new HashSet<>()
        for (String feature : getSelectedFeatures()) {
            // the same coordinates given twice are one selection
            if (!PackArtifacts.isCoordinates(feature)) {
                features << feature
            } else if (taken.add(feature)) {
                features << names.get(feature)
            }
        }
        return new Selection(packs, features)
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
                packs << loader.fromDirectory(directory.toPath().toAbsolutePath().normalize())
            } catch (PackDescriptorException e) {
                throw new CommandLine.ParameterException(this.spec.commandLine(), e.getMessage())
            }
        }
        for (File jar : featurePackJars) {
            try {
                packs << loader.fromJar(jar.toPath().toAbsolutePath().normalize())
            } catch (PackDescriptorException e) {
                throw new CommandLine.ParameterException(this.spec.commandLine(), e.getMessage())
            }
        }
        return packs
    }

    private JdkVersion getJdkVersion() {
        if (javaVersion == null) {
            return JdkVersion.DEFAULT_OPTION
        } else {
            return JdkVersion.valueOf(javaVersion)
        }
    }
}
