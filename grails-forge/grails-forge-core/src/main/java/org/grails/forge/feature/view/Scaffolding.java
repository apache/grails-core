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
package org.grails.forge.feature.view;

import jakarta.inject.Singleton;
import org.grails.forge.application.ApplicationType;
import org.grails.forge.application.generator.GeneratorContext;
import org.grails.forge.build.dependencies.Dependency;
import org.grails.forge.feature.DefaultFeature;
import org.grails.forge.feature.Feature;
import org.grails.forge.feature.FeatureContext;
import org.grails.forge.options.Options;
import org.grails.forge.util.VersionInfo;

import java.util.Set;

@Singleton
public class Scaffolding implements DefaultFeature {

    private final GrailsGsp grailsGsp;

    public Scaffolding(GrailsGsp grailsGsp) {
        this.grailsGsp = grailsGsp;
    }

    @Override
    public boolean shouldApply(ApplicationType applicationType, Options options, Set<Feature> selectedFeatures) {
        return applicationType == ApplicationType.WEB || applicationType == ApplicationType.WEB_PLUGIN;
    }

    @Override
    public String getName() {
        return "scaffolding";
    }

    @Override
    public String getTitle() {
        return "Grails Scaffolding Plugin";
    }

    @Override
    public String getDescription() {
        return "The Grails® framework Scaffolding plugin replicates much of the functionality from Grails 2, but uses the fields plugin instead.";
    }

    @Override
    public String getDocumentation() {
        return "https://docs.grails.org/" + VersionInfo.getGrailsVersion() + "/guide/scaffolding.html";
    }

    @Override
    public String getThirdPartyDocumentation() {
        return "https://github.com/gpc/fields#readme";
    }

    @Override
    public boolean supports(ApplicationType applicationType) {
        return applicationType == ApplicationType.WEB || applicationType == ApplicationType.WEB_PLUGIN;
    }

    @Override
    public void processSelectedFeatures(FeatureContext featureContext) {
        if (!featureContext.isPresent(GrailsGsp.class) && grailsGsp != null) {
            featureContext.addFeature(grailsGsp);
        }
    }

    @Override
    public void apply(GeneratorContext generatorContext) {
        generatorContext.addDependency(Dependency.builder()
                .groupId("org.fusesource.jansi")
                .artifactId("jansi")
                .runtimeOnly());

        generatorContext.addDependency(Dependency.builder()
                .groupId("org.apache.grails")
                .artifactId("grails-scaffolding")
                .implementation());
    }
}
