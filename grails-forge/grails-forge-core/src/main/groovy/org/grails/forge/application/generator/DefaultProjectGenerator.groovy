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
package org.grails.forge.application.generator

import groovy.transform.CompileStatic
import jakarta.annotation.Nullable
import org.apache.grails.forge.core.pack.Pack
import org.apache.grails.forge.core.pack.PackArtifact
import org.apache.grails.forge.core.pack.PackContent
import org.apache.grails.forge.core.pack.PackDescriptorParser
import org.apache.grails.forge.core.pack.PackFeature
import org.apache.grails.forge.core.pack.PackFeatures
import org.apache.grails.forge.core.state.ProjectStateRecorder
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.ContextFactory
import org.grails.forge.application.OperatingSystem
import org.grails.forge.application.Project
import org.grails.forge.feature.AvailableFeatures
import org.grails.forge.feature.Feature
import org.grails.forge.feature.FeatureRegistry
import org.springframework.stereotype.Component
import org.grails.forge.feature.FeatureContext
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.OutputHandler
import org.grails.forge.io.OutputUtils
import org.grails.forge.options.BuildTool
import org.grails.forge.options.Language
import org.grails.forge.options.Options
import org.grails.forge.template.DefaultTemplateRenderer
import org.grails.forge.template.RenderResult
import org.grails.forge.template.Template
import org.grails.forge.template.TemplateRenderer
import org.grails.forge.template.GspTemplate
import org.grails.forge.template.GspView
import java.text.Normalizer
import java.util.function.Function

@Component('projectGenerator')
@CompileStatic
class DefaultProjectGenerator implements ProjectGenerator {

    private final ContextFactory contextFactory
    private final FeatureRegistry featureRegistry

    DefaultProjectGenerator(ContextFactory contextFactory, FeatureRegistry featureRegistry) {
        this.contextFactory = contextFactory
        this.featureRegistry = featureRegistry
    }

    @Override
    void generate(ApplicationType applicationType,
                         Project project,
                         Options options,
                         @Nullable OperatingSystem operatingSystem,
                         List<String> selectedFeatures,
                         OutputHandler outputHandler,
                         ConsoleOutput consoleOutput) throws Exception {

        GeneratorContext generatorContext = createGeneratorContext(
                applicationType,
                project,
                options,
                operatingSystem,
                selectedFeatures,
                consoleOutput
        )

        generate(applicationType, project, outputHandler, generatorContext)
    }

    @Override
    void generate(
            ApplicationType applicationType,
            Project project,
            OutputHandler outputHandler,
            GeneratorContext generatorContext) throws Exception {
        List<String> features = new ArrayList<>(generatorContext.getFeatures().size())
        features.addAll(generatorContext.getFeatures())
        features.sort(Comparator.comparing(Function.identity()))

        generatorContext.addTemplate('grailsCli',
                new GspTemplate('grails-forge-cli.yml',
                        GspView.of('/forge/feature/cli.gsp', [language: Language.DEFAULT_OPTION, reloading: generatorContext.getDevelopmentReloading(), buildTool: BuildTool.DEFAULT_OPTION, gormImpl: generatorContext.getGorm(), servletImpl: generatorContext.getServlet(), project: generatorContext.getProject(), features: features, applicationType: applicationType, packArtifacts: packArtifacts(generatorContext)])))

        if (generatorContext.getProject() != null && generatorContext.getProject().getProperties() != project.getProperties()) {
            // what the features add was made for the project of the context, its name in it
            throw new IllegalArgumentException("The application ${project.getName()} in ${project.getPackageName()} cannot be generated " +
                    "from what was prepared for the application ${generatorContext.getProject().getName()} " +
                    "in ${generatorContext.getProject().getPackageName()}")
        }
        generatorContext.applyFeatures()

        checkPaths(project, generatorContext.getTemplates().values())

        // the state of the project under .grails is written last and holds what was rendered for the
        // application, so it is not part of its own baseline
        ProjectStateRecorder state = new ProjectStateRecorder(applicationType, project, generatorContext)
        state.check(generatorContext.getTemplates().values(), outputHandler)

        try (TemplateRenderer templateRenderer = TemplateRenderer.create(project, outputHandler)) {
            for (Template template: generatorContext.getTemplates().values()) {
                RenderResult renderResult = templateRenderer.render(state.record(template))
                if (renderResult.getError() != null) {
                    throw renderResult.getError()
                }
            }
            for (Template template : state.state()) {
                RenderResult renderResult = templateRenderer.render(template, true)
                if (renderResult.getError() != null) {
                    throw renderResult.getError()
                }
            }
        }
    }

    /**
     * Before anything is written: of two templates for one file only the first is written, and nothing
     * says so, and a file where another template needs a directory fails half way. The paths are
     * compared as they are written, with their placeholders replaced, and as a file system that ignores
     * the case and the composed form of letters sees them. The file of a feature pack is also held to
     * the paths a pack can write, now that its placeholders have the values of this project.
     */
    private static void checkPaths(Project project, Collection<Template> templates) {
        Map<String, String> replacements = project.getProperties()
        Set<String> files = new HashSet<>()
        Set<String> directories = new HashSet<>()
        for (Template template : templates) {
            String path = DefaultTemplateRenderer.resolvePath(template.getPath(), replacements)
            if (!OutputUtils.isRelativePath(path)) {
                throw new IllegalArgumentException("The features write the file ${path}, which is not a path inside the application")
            }
            if (template instanceof PackContent && !PackDescriptorParser.isPathBelowRoot(path)) {
                throw new IllegalArgumentException("A feature pack would write the file ${template.getPath()} to ${path}, " +
                        'which is not a path a feature pack can write for this application')
            }
            // a/b, a//b and a/./b are one file
            String steps = path.split('/').findAll { String step -> !step.isEmpty() && step != '.' }.join('/')
            String folded = Normalizer.normalize(steps, Normalizer.Form.NFC).toUpperCase(Locale.ROOT).toLowerCase(Locale.ROOT)
            List<String> above = []
            for (int slash = folded.indexOf('/'); slash >= 0; slash = folded.indexOf('/', slash + 1)) {
                above << folded.substring(0, slash)
            }
            if (files.contains(folded)) {
                throw new IllegalArgumentException("The features write the file ${path} more than once")
            }
            if (directories.contains(folded) || above.any { String directory -> files.contains(directory) }) {
                throw new IllegalArgumentException("The features write ${path} both as a file and as a directory of other files")
            }
            files << folded
            directories.addAll(above)
        }
    }

    @Override
    GeneratorContext createGeneratorContext(
            ApplicationType applicationType,
            Project project,
            Options options,
            @Nullable OperatingSystem operatingSystem,
            List<String> selectedFeatures,
            ConsoleOutput consoleOutput) {
        return createGeneratorContext(applicationType, project, options, operatingSystem, selectedFeatures, consoleOutput, [])
    }

    @Override
    AvailableFeatures availableFeatures(ApplicationType applicationType, Collection<Pack> packs) {
        AvailableFeatures framework = featureRegistry.availableFeatures(applicationType)
        return packs.isEmpty() ? framework : new PackFeatures(framework, featureRegistry.all(), packs, applicationType)
    }

    @Override
    GeneratorContext createGeneratorContext(
            ApplicationType applicationType,
            Project project,
            Options options,
            @Nullable OperatingSystem operatingSystem,
            List<String> selectedFeatures,
            ConsoleOutput consoleOutput,
            Collection<Pack> packs) {
        AvailableFeatures availableFeatures = availableFeatures(applicationType, packs)

        FeatureContext featureContext = contextFactory.createFeatureContext(availableFeatures, selectedFeatures, applicationType, options, operatingSystem)
        return contextFactory.createGeneratorContext(project, featureContext, consoleOutput)
    }

    /**
     * The published jars the packs of the application were read from, in the order of their names, which
     * are written next to the features so that it can be seen later what the application was made with.
     */
    private static List<PackArtifact> packArtifacts(GeneratorContext generatorContext) {
        List<PackArtifact> artifacts = []
        for (Feature feature : generatorContext.getFeatures().getFeatures()) {
            if (feature instanceof PackFeature && ((PackFeature) feature).getPack().getArtifact() != null) {
                artifacts << ((PackFeature) feature).getPack().getArtifact()
            }
        }
        return artifacts.sort { PackArtifact artifact -> artifact.name }
    }
}
