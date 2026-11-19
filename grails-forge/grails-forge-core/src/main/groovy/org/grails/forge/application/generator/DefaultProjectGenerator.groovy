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
import org.apache.grails.forge.core.pack.PackFeatures
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.ContextFactory
import org.grails.forge.application.OperatingSystem
import org.grails.forge.application.Project
import org.grails.forge.feature.AvailableFeatures
import org.grails.forge.feature.FeatureRegistry
import org.springframework.stereotype.Component
import org.grails.forge.feature.FeatureContext
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.OutputHandler
import org.grails.forge.options.BuildTool
import org.grails.forge.options.Language
import org.grails.forge.options.Options
import org.grails.forge.template.DefaultTemplateRenderer
import org.grails.forge.template.RenderResult
import org.grails.forge.template.Template
import org.grails.forge.template.TemplateRenderer
import org.grails.forge.template.GspTemplate
import org.grails.forge.template.GspView
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
                        GspView.of('/forge/feature/cli.gsp', [language: Language.DEFAULT_OPTION, reloading: generatorContext.getDevelopmentReloading(), buildTool: BuildTool.DEFAULT_OPTION, gormImpl: generatorContext.getGorm(), servletImpl: generatorContext.getServlet(), project: generatorContext.getProject(), features: features, applicationType: applicationType])))

        generatorContext.applyFeatures()

        // of two templates for one file only the first is written, and nothing says so. The paths are
        // compared as they are written, with their placeholders replaced, and as a file system that
        // ignores the case of letters sees them
        Map<String, String> replacements = project.getProperties()
        Set<String> paths = new HashSet<>()
        for (Template template : generatorContext.getTemplates().values()) {
            String path = DefaultTemplateRenderer.resolvePath(template.getPath(), replacements)
            if (!paths.add(path.toUpperCase(Locale.ROOT).toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("The features write the file ${path} more than once")
            }
        }

        try (TemplateRenderer templateRenderer = TemplateRenderer.create(project, outputHandler)) {
            for (Template template: generatorContext.getTemplates().values()) {
                RenderResult renderResult = templateRenderer.render(template)
                if (renderResult.getError() != null) {
                    throw renderResult.getError()
                }
            }
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
    GeneratorContext createGeneratorContext(
            ApplicationType applicationType,
            Project project,
            Options options,
            @Nullable OperatingSystem operatingSystem,
            List<String> selectedFeatures,
            ConsoleOutput consoleOutput,
            Collection<Pack> packs) {
        AvailableFeatures availableFeatures = featureRegistry.availableFeatures(applicationType)
        if (!packs.isEmpty()) {
            availableFeatures = new PackFeatures(availableFeatures, featureRegistry.all(), packs, applicationType)
        }

        FeatureContext featureContext = contextFactory.createFeatureContext(availableFeatures, selectedFeatures, applicationType, options, operatingSystem)
        return contextFactory.createGeneratorContext(project, featureContext, consoleOutput)
    }
}
