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
import org.apache.grails.forge.core.pack.PackContent
import org.apache.grails.forge.core.pack.PackDescriptorParser
import org.apache.grails.forge.core.pack.PackFeatures
import org.apache.grails.forge.core.pack.PackParameter
import org.apache.grails.forge.core.state.ProjectState
import org.apache.grails.forge.core.state.ProjectStateRecorder
import org.grails.forge.io.OutputFailedException
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.ContextFactory
import org.grails.forge.application.OperatingSystem
import org.grails.forge.application.Project
import org.grails.forge.feature.AvailableFeatures
import org.grails.forge.feature.Feature
import org.grails.forge.feature.FeatureSelectionException
import org.grails.forge.feature.FeatureRegistry
import org.springframework.stereotype.Component
import org.grails.forge.feature.FeatureContext
import org.grails.forge.feature.build.gradle.GradleSettingsFileFeature
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.OutputHandler
import org.grails.forge.io.OutputUtils
import org.grails.forge.options.Options
import org.grails.forge.template.DefaultTemplateRenderer
import org.grails.forge.template.RenderResult
import org.grails.forge.template.Template
import org.grails.forge.template.TemplateRenderer
import java.text.Normalizer

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
        if (generatorContext.getProject() != null && generatorContext.getProject().getProperties() != project.getProperties()) {
            // what the features add was made for the project of the context, its name in it
            throw new IllegalArgumentException("The application ${project.getName()} in ${project.getPackageName()} cannot be generated " +
                    "from what was prepared for the application ${generatorContext.getProject().getName()} " +
                    "in ${generatorContext.getProject().getPackageName()}")
        }
        generatorContext.applyFeatures()

        Collection<Template> templates = ofApplication(project, generatorContext)
        checkPaths(project, templates)

        // Everything is rendered, and the state of the project under .grails is read back from what
        // it will be, before the first file is written: a template that fails and a state that could
        // not be read leave nothing behind. The state is written last, its manifest at the very end,
        // and holds what was rendered for the application, so it is not part of its own baseline
        ProjectStateRecorder state = new ProjectStateRecorder(applicationType, project, generatorContext, outputHandler)
        state.check(templates)
        List<Template> rendered = []
        for (Template template : templates) {
            rendered << state.record(template)
        }
        List<Template> stateFiles = state.state()
        List<String> written = []
        try {
            state.clear()
        } catch (IOException e) {
            throw OutputFailedException.of(ProjectState.BASELINE, e, written)
        }

        Map<String, Template> kept = new LinkedHashMap<>()
        try (TemplateRenderer templateRenderer = TemplateRenderer.create(project, outputHandler)) {
            for (Template template : rendered) {
                RenderResult renderResult = templateRenderer.render(template)
                if (renderResult.getError() != null) {
                    throw failed(renderResult, written)
                }
                if (renderResult.isSkipped()) {
                    kept.put(renderResult.getPath(), template)
                } else {
                    written << renderResult.getPath()
                }
            }
            for (Template template : stateFiles) {
                RenderResult renderResult = templateRenderer.render(template, true)
                if (renderResult.getError() != null) {
                    throw failed(renderResult, written)
                }
                written << renderResult.getPath()
            }
            if (!kept.isEmpty()) {
                outputHandler.kept(kept)
            }
        }
    }

    /**
     * The files that are written. An application that is a project of a Gradle build that is not its
     * own has its own files only: the Gradle wrapper, the settings and the other files that are read
     * at the root of a build, or that are there for the wrapper scripts, are those of the build it is
     * put into, and are not written a second time where nothing reads them.
     */
    private static Collection<Template> ofApplication(Project project, GeneratorContext generatorContext) {
        Collection<Template> templates = generatorContext.getTemplates().values()
        if (!generatorContext.getOptions().isSubproject()) {
            return templates
        }
        String settings = generatorContext.getFeatures().getFeatures().find { Feature feature -> feature instanceof GradleSettingsFileFeature }?.getName()
        if (settings != null) {
            throw new FeatureSelectionException("The feature ${settings} writes the settings of a Gradle build, and an application that is created " +
                    'as a project of an existing build has none: the settings are those of that build')
        }
        Map<String, String> replacements = project.getProperties()
        return templates.findAll { Template template ->
            !ProjectState.isOfBuildRoot(DefaultTemplateRenderer.resolvePath(template.getPath(), replacements))
        }
    }

    // what the file system refused is said by the path of the file in the project, not by where the project is
    private static Exception failed(RenderResult renderResult, List<String> written) {
        Exception error = renderResult.getError()
        return error instanceof IOException ? OutputFailedException.of(renderResult.getPath(), (IOException) error, written) : error
    }

    /**
     * Before anything is written: of two templates for one file only the first is written, and nothing
     * says so, and a file where another template needs a directory fails half way. The paths are
     * compared as they are written, with their placeholders replaced, and as a file system that ignores
     * the case and the composed form of letters sees them. The file of a feature pack that is not one of
     * Forge is also held to the paths a pack can write, now that its placeholders have the values of this
     * project.
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
            if (template instanceof PackContent && ((PackContent) template).isHeld() && !PackDescriptorParser.isPathBelowRoot(path)) {
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
        return createGeneratorContext(applicationType, project, options, operatingSystem, selectedFeatures, consoleOutput, packs, [])
    }

    @Override
    GeneratorContext createGeneratorContext(
            ApplicationType applicationType,
            Project project,
            Options options,
            @Nullable OperatingSystem operatingSystem,
            List<String> selectedFeatures,
            ConsoleOutput consoleOutput,
            Collection<Pack> packs,
            Collection<String> excludedFeatures) {
        AvailableFeatures availableFeatures = availableFeatures(applicationType, packs)

        FeatureContext featureContext = contextFactory.createFeatureContext(availableFeatures, selectedFeatures, applicationType, options, operatingSystem, excludedFeatures)
        GeneratorContext generatorContext = contextFactory.createGeneratorContext(project, featureContext, consoleOutput)
        // an option that was given for a feature is one a feature of the application takes
        PackParameter.check(options, generatorContext.getFeatures().getFeatures())
        return generatorContext
    }
}
