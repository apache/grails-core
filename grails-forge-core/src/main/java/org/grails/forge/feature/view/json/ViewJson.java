/*
 * Copyright 2017-2024 original authors
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
package org.grails.forge.feature.view.json;

import io.micronaut.core.annotation.NonNull;
import jakarta.inject.Singleton;
import org.grails.forge.application.ApplicationType;
import org.grails.forge.application.generator.GeneratorContext;
import org.grails.forge.build.dependencies.Dependency;
import org.grails.forge.build.gradle.GradlePlugin;
import org.grails.forge.feature.DefaultFeature;
import org.grails.forge.feature.Feature;
import org.grails.forge.feature.view.GrailsViews;
import org.grails.forge.feature.view.json.templates.*;
import org.grails.forge.feature.web.GrailsWeb;
import org.grails.forge.options.Options;
import org.grails.forge.template.RockerTemplate;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

@Singleton
public class ViewJson extends GrailsViews implements DefaultFeature {

    public ViewJson(GrailsWeb grailsWeb) {
        super(grailsWeb);
    }

    @Override
    @NonNull
    public String getName() {
        return "views-json";
    }

    @Override
    public String getTitle() {
        return "JSON Views";
    }

    @Override
    @NonNull
    public String getDescription() {
        return "JSON views are written in Groovy, end with the file extension gson and reside in the grails-app/views directory. They provide a DSL for producing output in the JSON format.";
    }

    @Override
    public void apply(GeneratorContext generatorContext) {
        final Map<String, Object> config = generatorContext.getConfiguration();
        config.put("grails.mime.disable.accept.header.userAgents", Arrays.asList("Gecko", "WebKit", "Presto", "Trident"));
        config.put("grails.mime.types.json", Arrays.asList("application/json", "text/json"));
        config.put("grails.mime.types.hal", Arrays.asList("application/hal+json", "application/hal+xml"));
        config.put("grails.mime.types.xml", Arrays.asList("text/xml", "application/xml"));
        config.put("grails.mime.types.atom", "application/atom+xml");
        config.put("grails.mime.types.css", "text/css");
        config.put("grails.mime.types.csv", "text/csv");
        config.put("grails.mime.types.js", "text/javascript");
        config.put("grails.mime.types.rss", "application/rss+xml");
        config.put("grails.mime.types.text", "text/plain");
        config.put("grails.mime.types.all", "*/*");

        generatorContext.addBuildPlugin(GradlePlugin.builder()
                .id("org.apache.grails.gradle.grails-gson")
                .useApplyPlugin(true)
                .build());

        generatorContext.addDependency(Dependency.builder()
                .groupId("org.apache.grails")
                .artifactId("grails-views-gson")
                .implementation());
        generatorContext.addDependency(Dependency.builder()
                .groupId("org.apache.grails")
                .artifactId("grails-data-mongodb-gson-templates")
                .implementation());

        generatorContext.addDependency(Dependency.builder()
                .groupId("org.apache.grails")
                .artifactId("grails-testing-support-views-gson")
                .testImplementation());

        generatorContext.addTemplate("application_index_gson", new RockerTemplate(getViewFolderPath() + "application/index.gson", index.template()));
        generatorContext.addTemplate("_errors_gson", new RockerTemplate(getViewFolderPath() + "errors/_errors.gson", _errors.template()));
        generatorContext.addTemplate("_object_gson", new RockerTemplate(getViewFolderPath() + "object/_object.gson", _object.template()));
        generatorContext.addTemplate("error_gson", new RockerTemplate(getViewFolderPath() + "error.gson", error.template()));
        generatorContext.addTemplate("notFound_gson", new RockerTemplate(getViewFolderPath() + "notFound.gson", notFound.template()));
    }

    @Override
    public boolean shouldApply(ApplicationType applicationType, Options options, Set<Feature> selectedFeatures) {
        return applicationType == ApplicationType.REST_API && selectedFeatures.stream().noneMatch(f -> f instanceof GrailsViews);
    }
}
