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
import org.grails.forge.io.FileSystemOutputHandler
import org.grails.forge.io.OutputHandler
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.GormImpl
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.Options
import org.grails.forge.options.ServletImpl
import org.grails.forge.util.NameUtils
import picocli.CommandLine
import java.util.concurrent.Callable

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
            // nothing was written: the packs are taken before the application is
            err(e.getMessage().replaceAll(/\p{Cntrl}/, '?').replace('@|', '@ |'))
            return 1
        }
    }

    private Integer create() throws Exception {
        if (listFeatures) {
            List<Pack> packs = selection().packs
            new ListFeatures(packs.isEmpty() ? availableFeatures : withPacks(packs),
                    new Options(reloading, gormImpl, servletImpl, getJdkVersion(), getOperatingSystem()),
                    applicationType,
                    getOperatingSystem(),
                    contextFactory).output(this)
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
        List<Pack> packs = selection().packs
        if (packs.isEmpty()) {
            projectGenerator.generate(applicationType, project, options, getOperatingSystem(), selection().features, outputHandler, this)
            return
        }
        withPacks(packs)
        GeneratorContext context = projectGenerator.createGeneratorContext(applicationType, project, options, getOperatingSystem(),
                selection().features, this, packs)
        // a pack is not part of the framework: say which ones this application is made with, which are
        // the selected ones and those they require. What a descriptor says is not markup of this command
        for (Pack pack : packs) {
            if (context.getFeatures().contains(pack.descriptor.name)) {
                PackArtifact artifact = pack.artifact
                out("@|blue ||@ Feature pack ${pack.descriptor.name} from ${pack.origin.replaceAll(/\p{Cntrl}/, '?').replace('@|', '@ |')}" +
                        (artifact == null ? '' : ", sha256 ${artifact.sha256}"))
                if (artifact != null) {
                    // the digest says which jar was read; only a checksum from elsewhere says it is the one that was published
                    if (artifact.pinned) {
                        out('    the jar has the SHA-256 that was asked for with --feature-pack-sha256')
                    }
                    out(artifact.checkedAgainst == null ? '    the repository publishes no checksum the jar could be checked against' :
                            "    the jar matches the ${artifact.checkedAgainst} checksum the repository publishes for it")
                }
                for (String line : pack.summary()) {
                    out("    ${line.replace('@|', '@ |')}")
                }
                if (artifact != null && !dependsOnItsPlugin(pack)) {
                    warning("The feature pack ${pack.descriptor.name} declares no dependency on " +
                            "${artifact.coordinates.substring(0, artifact.coordinates.lastIndexOf(':'))}, the plugin it was taken from: " +
                            'the application is generated without that plugin')
                }
            }
        }
        projectGenerator.generate(applicationType, project, outputHandler, context)
    }

    /**
     * The features to choose from with the packs among them. Packs that cannot be used together, such
     * as one directory given twice, are a mistake in the command line.
     */
    private AvailableFeatures withPacks(List<Pack> packs) {
        try {
            return projectGenerator.availableFeatures(applicationType, packs)
        } catch (IllegalArgumentException e) {
            throw new CommandLine.ParameterException(this.spec.commandLine(), e.getMessage().replace('@|', '@ |'))
        }
    }

    private static boolean dependsOnItsPlugin(Pack pack) {
        return pack.descriptor.dependencies.any { PackDependency dependency ->
            pack.artifact.coordinates.startsWith("${dependency.groupId}:${dependency.artifactId}:")
        }
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
            repositories = PackArtifacts.repositories(featurePackRepositories, plugins.isEmpty() ? null : System.getenv('GRAILS_REPO_URL'))
        } catch (IllegalArgumentException e) {
            throw new CommandLine.ParameterException(this.spec.commandLine(), e.getMessage())
        }
        Map<String, String> names = new HashMap<>()
        for (PackArtifacts.Coordinates coordinates : plugins.values()) {
            Pack pack
            try {
                pack = packArtifacts.resolve(coordinates, repositories, digests.get(coordinates.toString()))
            } catch (PackDescriptorException e) {
                throw new CommandLine.ParameterException(this.spec.commandLine(), e.getMessage())
            }
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
