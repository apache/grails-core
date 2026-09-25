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
import org.grails.forge.application.Project;
import org.grails.forge.application.generator.GeneratorContext;
import org.grails.forge.build.dependencies.Dependency;
import org.grails.forge.feature.FeatureContext;
import org.grails.forge.feature.test.template.gebConfig;
import org.grails.forge.feature.test.template.gebSpec;
import org.grails.forge.options.DefaultTestRockerModelProvider;
import org.grails.forge.options.TestFramework;
import org.grails.forge.options.TestRockerModelProvider;
import org.grails.forge.template.RockerTemplate;

import java.util.stream.Stream;

@Singleton
public class GebWithLocalBrowsers implements GebFeature {

    private final Spock spock;

    public GebWithLocalBrowsers(Spock spock) {
        this.spock = spock;
    }

    @NonNull
    @Override
    public String getName() {
        return "geb-with-local-browsers";
    }

    @Override
    public String getTitle() {
        return "Geb Functional Testing with locally installed browsers";
    }

    @NonNull
    @Override
    public String getDescription() {
        return "Configures Geb to run functional tests against browsers installed on your machine. Selenium Manager downloads the WebDriver binary that matches each browser.";
    }

    @Override
    public String getDocumentation() {
        return "https://github.com/apache/grails-geb#readme";
    }

    @Override
    public String getThirdPartyDocumentation() {
        return "https://groovy.apache.org/geb/manual/current/";
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

        Stream.of("api", "support", "remote-driver")
                .map(name -> "selenium-" + name)
                .forEach(name -> generatorContext.addDependency(Dependency.builder()
                        .groupId("org.seleniumhq.selenium")
                        .artifactId(name)
                        .testImplementation()
                ));

        generatorContext.addDependency(
                Dependency.builder()
                        .groupId("org.seleniumhq.selenium")
                        .artifactId("selenium-firefox-driver")
                        .testRuntimeOnly()
        );
        generatorContext.addDependency(
                Dependency.builder()
                        .groupId("org.seleniumhq.selenium")
                        .artifactId("selenium-safari-driver")
                        .testRuntimeOnly()
        );

        Project project = generatorContext.getProject();
        TestRockerModelProvider provider = new DefaultTestRockerModelProvider(
                gebSpec.template(project)
        );
        generatorContext.addTemplate("applicationTest",
                new RockerTemplate(
                        generatorContext.getIntegrationTestSourcePath("/{packagePath}/{className}"),
                        provider.findModel(TestFramework.SPOCK)
                )
        );
        generatorContext.addTemplate("gebConfig",
                new RockerTemplate(
                        "src/integration-test/resources/GebConfig.groovy",
                        gebConfig.template(project)
                )
        );
    }
}
