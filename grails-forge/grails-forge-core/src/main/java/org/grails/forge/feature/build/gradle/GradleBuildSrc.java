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
package org.grails.forge.feature.build.gradle;

import jakarta.inject.Singleton;
import org.grails.forge.application.ApplicationType;
import org.grails.forge.application.generator.GeneratorContext;
import org.grails.forge.build.dependencies.CoordinateResolver;
import org.grails.forge.build.gradle.GradleBuild;
import org.grails.forge.build.gradle.GradleBuildCreator;
import org.grails.forge.feature.build.gradle.templates.buildSrcBuildGradle;
import org.grails.forge.options.BuildTool;
import org.grails.forge.template.RockerTemplate;
import org.grails.forge.util.VersionInfo;

@Singleton
public class GradleBuildSrc implements GradleBuildSrcFeature {

    private final GradleBuildCreator dependencyResolver;
    private final CoordinateResolver resolver;

    public GradleBuildSrc(GradleBuildCreator dependencyResolver, CoordinateResolver resolver) {
        this.dependencyResolver = dependencyResolver;
        this.resolver = resolver;
    }

    @Override
    public String getName() {
        return "gradle-build-src";
    }

    @Override
    public String getTitle() {
        return "Gradle buildSrc";
    }

    @Override
    public String getDescription() {
        return "Use Gradle buildSrc/build.gradle instead of buildscript{} in main build.gradle";
    }

    @Override
    public boolean isVisible() {
        return true;
    }

    @Override
    public void apply(GeneratorContext generatorContext) {
        BuildTool buildTool = BuildTool.DEFAULT_OPTION;
        GradleBuild build = dependencyResolver.create(generatorContext);

        generatorContext.addTemplate("buildSrc/build", new RockerTemplate("buildSrc/" + buildTool.getBuildFileName(), buildSrcBuildGradle.template(
                generatorContext.getApplicationType(),
                generatorContext.getProject(),
                generatorContext.getFeatures(),
                build,
                VersionInfo.getGrailsVersion()
        )));
    }

    @Override
    public boolean supports(ApplicationType applicationType) {
        return true;
    }
}
