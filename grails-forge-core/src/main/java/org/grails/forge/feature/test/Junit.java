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
package org.grails.forge.feature.test;

import io.micronaut.core.annotation.NonNull;
import jakarta.inject.Singleton;
import org.grails.forge.application.ApplicationType;
import org.grails.forge.application.generator.GeneratorContext;
import org.grails.forge.build.dependencies.Dependency;
import org.grails.forge.feature.DefaultFeature;
import org.grails.forge.feature.Feature;
import org.grails.forge.options.Options;
import org.grails.forge.options.TestFramework;

import java.util.Set;

@Singleton
public class Junit implements TestFeature, DefaultFeature {

    @Override
    public String getTitle() {
        return "JUnit 5";
    }

    @NonNull
    @Override
    public String getName() {
        return "junit";
    }

    @NonNull
    @Override
    public String getDescription() {
        return "Configure JUnit 5 for Grails application.";
    }

    @Override
    public void doApply(GeneratorContext generatorContext) {
        generatorContext.addDependency(Dependency.builder()
                .groupId("org.junit.jupiter")
                .artifactId("junit-jupiter")
                .testImplementation());
    }

    @Override
    public TestFramework getTestFramework() {
        return TestFramework.JUNIT;
    }

    @Override
    public boolean shouldApply(ApplicationType applicationType, Options options, Set<Feature> selectedFeatures) {
        return options.getTestFramework() == TestFramework.JUNIT;
    }
}
