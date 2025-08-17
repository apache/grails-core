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
package org.grails.forge.feature.test;

import io.micronaut.core.annotation.NonNull;
import jakarta.inject.Singleton;
import org.grails.forge.application.ApplicationType;
import org.grails.forge.application.Project;
import org.grails.forge.application.generator.GeneratorContext;
import org.grails.forge.build.dependencies.Dependency;
import org.grails.forge.feature.DefaultFeature;
import org.grails.forge.feature.Feature;
import org.grails.forge.feature.FeatureContext;
import org.grails.forge.feature.FeaturePhase;
import org.grails.forge.feature.test.template.groovyJunit;
import org.grails.forge.options.*;
import org.grails.forge.template.RockerTemplate;

import java.util.Set;

@Singleton
public class GebWithTestcontainers implements DefaultFeature {

    private final Spock spock;

    public GebWithTestcontainers(Spock spock) {
        this.spock = spock;
    }

    @Override
    public boolean shouldApply(ApplicationType applicationType, Options options, Set<Feature> selectedFeatures) {
        return applicationType == ApplicationType.WEB && options.getTestFramework() != TestFramework.JUNIT;
    }

    @NonNull
    @Override
    public String getName() {
        return "geb-with-testcontainers";
    }

    @Override
    public String getTitle() {
        return "Geb Functional Testing for Grails with Testcontainers";
    }

    @NonNull
    @Override
    public String getDescription() {
        return "This plugins configure Geb for Grails framework to write automation tests that run with Testcontainers.";
    }

    @Override
    public int getOrder() {
        return FeaturePhase.TEST.getOrder();
    }

    @Override
    public boolean supports(ApplicationType applicationType) {
        return applicationType == ApplicationType.WEB || applicationType == ApplicationType.WEB_PLUGIN;
    }

    @Override
    public String getDocumentation() {
        return "https://github.com/apache/grails-geb#readme";
    }

    @Override
    public String getThirdPartyDocumentation() {
        return "https://www.gebish.org/manual/current/";
    }

    @Override
    public void processSelectedFeatures(FeatureContext featureContext) {
        if (!featureContext.isPresent(Spock.class) && spock != null) {
            featureContext.addFeature(spock);
        }
    }

    @Override
    public void apply(GeneratorContext generatorContext) {
        generatorContext.addDependency(Dependency.builder()
                .groupId("org.apache.grails")
                .artifactId("grails-geb")
                .integrationTestImplementationTestFixtures());

        Project project = generatorContext.getProject();
        TestRockerModelProvider provider = new DefaultTestRockerModelProvider(
                org.grails.forge.feature.test.template.spock.template(project),
                groovyJunit.template(project)
        );
        generatorContext.addTemplate("applicationTest",
                new RockerTemplate(
                        generatorContext.getIntegrationTestSourcePath("/{packagePath}/{className}"),
                        provider.findModel(Language.DEFAULT_OPTION, generatorContext.getTestFramework())
                )
        );
    }
}
