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
import org.apache.grails.forge.core.pack.Pack
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
                    'selected like one of the framework. For trying a pack out before it is published. Can be given more than once.')
    List<File> featurePackDirectories = new ArrayList<>()

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
        if (listFeatures) {
            List<Pack> packs = featurePacks()
            new ListFeatures(packs.isEmpty() ? availableFeatures : projectGenerator.availableFeatures(applicationType, packs),
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
        List<Pack> packs = featurePacks()
        if (packs.isEmpty()) {
            projectGenerator.generate(applicationType, project, options, getOperatingSystem(), getSelectedFeatures(), outputHandler, this)
            return
        }
        // a pack is not part of the framework: say which ones this application is made with
        for (Pack pack : packs) {
            out("@|blue ||@ Feature pack ${pack.descriptor.name} from ${pack.origin}")
        }
        GeneratorContext context = projectGenerator.createGeneratorContext(applicationType, project, options, getOperatingSystem(),
                getSelectedFeatures(), this, packs)
        projectGenerator.generate(applicationType, project, outputHandler, context)
    }

    /**
     * The feature packs of the directories given with {@code --feature-pack-dir}.
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
