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
package org.grails.forge.feature.grails;

import jakarta.inject.Singleton;
import org.grails.forge.application.ApplicationType;
import org.grails.forge.application.generator.GeneratorContext;
import org.grails.forge.build.dependencies.Dependency;
import org.grails.forge.feature.DefaultFeature;
import org.grails.forge.feature.Feature;
import org.grails.forge.options.Options;
import org.grails.forge.template.URLTemplate;

import java.util.Set;

@Singleton
public class GrailsBase implements DefaultFeature {

    @Override
    public boolean shouldApply(ApplicationType applicationType, Options options, Set<Feature> selectedFeatures) {
        return true;
    }

    @Override
    public String getName() {
        return "base";
    }

    @Override
    public boolean supports(ApplicationType applicationType) {
        return true;
    }

    @Override
    public boolean isVisible() {
        return false;
    }

    @Override
    public void apply(GeneratorContext generatorContext) {

        // This ensures that grails-bom forces milestones to override snapshots
        generatorContext.addDependency(Dependency.builder()
                .groupId("org.apache.grails")
                .artifactId("grails-bom")
                .pom(true)
                .version("$grailsVersion")
                .implementation());

        generatorContext.addBuildscriptDependency(Dependency.builder()
                .groupId("org.apache.grails")
                .artifactId("grails-bom")
                .pom(true)
                .version("$grailsVersion")
                .classpath());

        generatorContext.addDependency(Dependency.builder()
                .groupId("org.apache.grails")
                .artifactId("grails-core")
                .implementation());
        generatorContext.addDependency(Dependency.builder()
                .groupId("org.apache.grails")
                .artifactId("grails-web-boot")
                .implementation());
        generatorContext.addDependency(Dependency.builder()
                .groupId("org.apache.grails")
                .artifactId("grails-logging")
                .implementation());

        final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        generatorContext.addTemplate("src/main/groovy", new URLTemplate("src/main/groovy/.gitkeep", classLoader.getResource(".gitkeep")));
        generatorContext.addTemplate("src/test/groovy", new URLTemplate("src/test/groovy/.gitkeep", classLoader.getResource(".gitkeep")));
        generatorContext.addTemplate("src/integration-test/groovy", new URLTemplate("src/integration-test/groovy/.gitkeep", classLoader.getResource(".gitkeep")));

        generatorContext.addTemplate("grails-app/services", new URLTemplate("grails-app/services/.gitkeep", classLoader.getResource(".gitkeep")));
        generatorContext.addTemplate("grails-app/domain", new URLTemplate("grails-app/domain/.gitkeep", classLoader.getResource(".gitkeep")));

        if (generatorContext.getApplicationType() != ApplicationType.REST_API) {
            generatorContext.addTemplate("grails-app/taglib", new URLTemplate("grails-app/taglib/.gitkeep", classLoader.getResource(".gitkeep")));
        }
    }
}
