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
package org.grails.forge.feature.test

import groovy.transform.CompileStatic
import jakarta.annotation.Nonnull
import org.springframework.stereotype.Component
import org.grails.forge.application.Project
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.build.dependencies.Dependency
import org.grails.forge.feature.FeatureContext
import org.grails.forge.options.DefaultTestViewProvider
import org.grails.forge.options.TestFramework
import org.grails.forge.options.TestViewProvider
import org.grails.forge.template.GspTemplate
import org.grails.forge.template.GspView
import java.util.stream.Stream

@Component
@CompileStatic
class GebWithLocalBrowsers implements GebFeature {

    private final Spock spock

    GebWithLocalBrowsers(Spock spock) {
        this.spock = spock
    }

    @Nonnull
    @Override
    String getName() {
        return 'geb-with-local-browsers'
    }

    @Override
    String getTitle() {
        return 'Geb Functional Testing with locally installed browsers'
    }

    @Nonnull
    @Override
    String getDescription() {
        return 'Configures Geb to run functional tests against browsers installed on your machine. Selenium Manager downloads the WebDriver binary that matches each browser.'
    }

    @Override
    String getDocumentation() {
        return 'https://github.com/apache/grails-geb#readme'
    }

    @Override
    String getThirdPartyDocumentation() {
        return 'https://groovy.apache.org/geb/manual/current/'
    }

    @Override
    void processSelectedFeatures(FeatureContext featureContext) {
        if (!featureContext.isPresent(Spock) && spock != null) {
            featureContext.addFeature(spock)
        }
    }

    @Override
    void apply(GeneratorContext generatorContext) {
        generatorContext.addDependency(Dependency.builder()
                .groupId('org.apache.grails')
                .artifactId('grails-geb')
                .integrationTestImplementationTestFixtures())

        Stream.of('api', 'support', 'remote-driver')
                .map(name -> 'selenium-' + name)
                .forEach(name -> generatorContext.addDependency(Dependency.builder()
                        .groupId('org.seleniumhq.selenium')
                        .artifactId(name)
                        .testImplementation()
                ))

        generatorContext.addDependency(
                Dependency.builder()
                        .groupId('org.seleniumhq.selenium')
                        .artifactId('selenium-firefox-driver')
                        .testRuntimeOnly()
        )
        generatorContext.addDependency(
                Dependency.builder()
                        .groupId('org.seleniumhq.selenium')
                        .artifactId('selenium-safari-driver')
                        .testRuntimeOnly()
        )

        Project project = generatorContext.getProject()
        TestViewProvider provider = new DefaultTestViewProvider(
                GspView.of('/forge/feature/test/template/gebSpec.gsp', [project: project])
        )
        generatorContext.addTemplate('applicationTest',
                new GspTemplate(
                        generatorContext.getIntegrationTestSourcePath('/{packagePath}/{className}'),
                        provider.findView(TestFramework.SPOCK)
                )
        )
        generatorContext.addTemplate('gebConfig',
                new GspTemplate(
                        'src/integration-test/resources/GebConfig.groovy',
                        GspView.of('/forge/feature/test/template/gebConfig.gsp', [project: project])
                )
        )
    }
}
