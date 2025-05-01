/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.forge.application.generator;

import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Singleton;
import org.grails.forge.application.ApplicationType;
import org.grails.forge.application.ContextFactory;
import org.grails.forge.application.OperatingSystem;
import org.grails.forge.application.Project;
import org.grails.forge.feature.AvailableFeatures;
import org.grails.forge.feature.FeatureContext;
import org.grails.forge.feature.cli;
import org.grails.forge.io.ConsoleOutput;
import org.grails.forge.io.OutputHandler;
import org.grails.forge.options.BuildTool;
import org.grails.forge.options.Language;
import org.grails.forge.options.Options;
import org.grails.forge.template.RenderResult;
import org.grails.forge.template.RockerTemplate;
import org.grails.forge.template.Template;
import org.grails.forge.template.TemplateRenderer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

@Singleton
public class DefaultProjectGenerator implements ProjectGenerator {
    private final ContextFactory contextFactory;
    private final BeanContext beanContext;

    public DefaultProjectGenerator(ContextFactory contextFactory, BeanContext beanContext) {
        this.contextFactory = contextFactory;
        this.beanContext = beanContext;
    }

    @Override
    public void generate(ApplicationType applicationType,
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
        );

        generate(applicationType, project, outputHandler, generatorContext);
    }

    @Override
    public void generate(
            ApplicationType applicationType,
            Project project,
            OutputHandler outputHandler,
            GeneratorContext generatorContext) throws Exception {
        List<String> features = new ArrayList<>(generatorContext.getFeatures().size());
        features.addAll(generatorContext.getFeatures());
        features.sort(Comparator.comparing(Function.identity()));

        generatorContext.addTemplate("grailsCli",
                new RockerTemplate("grails-forge-cli.yml",
                        cli.template(Language.DEFAULT_OPTION,
                                generatorContext.getTestFramework(),
                                BuildTool.DEFAULT_OPTION,
                                generatorContext.getGorm(),
                                generatorContext.getServlet(),
                                generatorContext.getProject(),
                                features,
                                applicationType)));

        generatorContext.applyFeatures();

        try (TemplateRenderer templateRenderer = TemplateRenderer.create(project, outputHandler)) {
            for (Template template: generatorContext.getTemplates().values()) {
                RenderResult renderResult = templateRenderer.render(template);
                if (renderResult.getError() != null) {
                    throw renderResult.getError();
                }
            }
        }
    }

    @Override
    public GeneratorContext createGeneratorContext(
            ApplicationType applicationType,
            Project project,
            Options options,
            @Nullable OperatingSystem operatingSystem,
            List<String> selectedFeatures,
            ConsoleOutput consoleOutput) {
        AvailableFeatures availableFeatures = beanContext.getBean(AvailableFeatures.class, Qualifiers.byName(applicationType.getName()));

        FeatureContext featureContext = contextFactory.createFeatureContext(availableFeatures, selectedFeatures, applicationType, options, operatingSystem);
        return contextFactory.createGeneratorContext(project, featureContext, consoleOutput);
    }
}
