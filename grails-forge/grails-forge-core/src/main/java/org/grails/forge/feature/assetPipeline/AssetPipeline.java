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
package org.grails.forge.feature.assetPipeline;

import io.micronaut.core.annotation.NonNull;
import jakarta.inject.Singleton;
import org.grails.forge.application.ApplicationType;
import org.grails.forge.application.generator.GeneratorContext;
import org.grails.forge.build.dependencies.CoordinateResolver;
import org.grails.forge.build.dependencies.Dependency;
import org.grails.forge.build.gradle.GradlePlugin;
import org.grails.forge.feature.Category;
import org.grails.forge.feature.DefaultFeature;
import org.grails.forge.feature.Feature;
import org.grails.forge.options.Options;
import org.grails.forge.template.URLTemplate;

import java.util.Set;
import java.util.List;

@Singleton
public class AssetPipeline implements DefaultFeature {

    private final CoordinateResolver coordinateResolver;

    public AssetPipeline(CoordinateResolver coordinateResolver) {
        this.coordinateResolver = coordinateResolver;
    }

    @NonNull
    @Override
    public String getName() {
        return "asset-pipeline-grails";
    }

    @Override
    public String getTitle() {
        return "Asset Pipeline";
    }

    @NonNull
    @Override
    public String getDescription() {
        return "Asset Pipeline is used for managing and processing static assets " +
                "(such as JavaScript, CSS and image files) in Grails applications. " +
                "Read more at https://github.com/wondrify/asset-pipeline.";
    }

    @Override
    public void apply(GeneratorContext generatorContext) {

        generatorContext.addBuildscriptDependency(Dependency.builder()
                .groupId("cloud.wondrify")
                .artifactId("asset-pipeline-gradle")
                .buildSrc());

        generatorContext.addBuildPlugin(GradlePlugin.builder().id("cloud.wondrify.asset-pipeline").useApplyPlugin(true).build());

        generatorContext.addDependency(Dependency.builder()
                .groupId("cloud.wondrify")
                .artifactId("asset-pipeline-grails")
                .runtimeOnly());

        generatorContext.addDependency(Dependency.builder()
                .groupId("org.webjars.npm")
                .artifactId("bootstrap")
                .testAndDevelopmentOnly());

        generatorContext.addDependency(Dependency.builder()
                .groupId("org.webjars.npm")
                .artifactId("bootstrap-icons")
                .testAndDevelopmentOnly());

        generatorContext.addDependency(Dependency.builder()
                .groupId("org.webjars.npm")
                .artifactId("jquery")
                .testAndDevelopmentOnly());

        var assetPaths = List.of(
                // Keep categories separate for readability.
                "grails-app/assets/images/advancedgrails.svg",
                "grails-app/assets/images/community.svg",
                "grails-app/assets/images/documentation.svg",
                "grails-app/assets/images/favicon.ico",
                "grails-app/assets/images/grails.svg",
                "grails-app/assets/images/groovy.svg",
                "grails-app/assets/images/java.svg",
                "grails-app/assets/images/spring.svg",
                "grails-app/assets/images/spring-boot.svg",

                "grails-app/assets/javascripts/application.js",
                "grails-app/assets/javascripts/welcome.js",

                "grails-app/assets/stylesheets/application.css",
                "grails-app/assets/stylesheets/errors.css",
                "grails-app/assets/stylesheets/grails.css",
                "grails-app/assets/stylesheets/welcome.css"
        );

        var classLoader = Thread.currentThread().getContextClassLoader();
        for (var assetTemplate : assetPaths) {
            addAssetTemplate(generatorContext, classLoader, assetTemplate);
        }
    }

    private static void addAssetTemplate(GeneratorContext generatorContext, ClassLoader classLoader, String assetPath) {

        // The template key is the filename with extension dot replaced by underscore,
        // e.g. application.css -> application_css
        var fileName = assetPath.substring(assetPath.lastIndexOf('/') + 1);
        var templateKey = fileName.replaceFirst("\\.", "_");

        // Resource paths are relative to the classpath root (no leading slash)
        var resourcePath = assetPath.replaceFirst("^grails-app/", "");
        generatorContext.addTemplate(
                templateKey,
                new URLTemplate(
                        assetPath,
                        classLoader.getResource(resourcePath)
                )
        );
    }

    @Override
    public boolean supports(ApplicationType applicationType) {
        return applicationType == ApplicationType.WEB || applicationType == ApplicationType.WEB_PLUGIN;
    }

    @Override
    public String getCategory() {
        return Category.VIEW;
    }

    @Override
    public String getDocumentation() {
        return "https://github.com/wondrify/asset-pipeline#readme";
    }

    @Override
    public boolean shouldApply(ApplicationType applicationType, Options options, Set<Feature> selectedFeatures) {
        return applicationType != ApplicationType.REST_API && applicationType != ApplicationType.PLUGIN;
    }
}
