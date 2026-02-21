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
package org.grails.forge.feature.groovydoc;

import io.micronaut.core.annotation.NonNull;
import jakarta.inject.Singleton;
import org.grails.forge.application.ApplicationType;
import org.grails.forge.application.generator.GeneratorContext;
import org.grails.forge.build.dependencies.Dependency;
import org.grails.forge.build.gradle.GradlePlugin;
import org.grails.forge.feature.Category;
import org.grails.forge.feature.DefaultFeature;
import org.grails.forge.feature.Feature;
import org.grails.forge.options.Options;

import java.util.Set;

@Singleton
public class GroovydocEnhancer implements DefaultFeature {

    @NonNull
    @Override
    public String getName() {
        return "groovydoc-enhancer";
    }

    @Override
    public String getTitle() {
        return "Groovydoc Enhancer";
    }

    @NonNull
    @Override
    public String getDescription() {
        return "Enables Groovydoc generation for projects using modern Java features (17+). " +
                "Without this plugin, Groovydoc fails to parse Java sources that use sealed classes, " +
                "records, pattern matching, and other post-Java 11 language features.";
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
    public String getCategory() {
        return Category.DOCUMENTATION;
    }

    @Override
    public boolean shouldApply(ApplicationType applicationType, Options options, Set<Feature> selectedFeatures) {
        return true;
    }

    @Override
    public void apply(GeneratorContext generatorContext) {
        generatorContext.addBuildscriptDependency(Dependency.builder()
                .groupId("org.apache.grails.gradle")
                .artifactId("grails-gradle-groovydoc")
                .buildSrc());

        generatorContext.addBuildPlugin(GradlePlugin.builder()
                .id("org.apache.grails.gradle.groovydoc")
                .useApplyPlugin(true)
                .build());
    }
}
