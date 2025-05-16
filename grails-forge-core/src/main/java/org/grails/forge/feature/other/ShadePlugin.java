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
package org.grails.forge.feature.other;

import io.micronaut.core.annotation.NonNull;
import jakarta.inject.Singleton;
import org.grails.forge.application.ApplicationType;
import org.grails.forge.application.generator.GeneratorContext;
import org.grails.forge.build.gradle.GradlePlugin;
import org.grails.forge.feature.Category;
import org.grails.forge.feature.Feature;

/**
 * Adds a shaded JAR feature.
 */
@Singleton
public class ShadePlugin implements Feature {

    @NonNull
    @Override
    public String getName() {
        return "shade";
    }

    @Override
    public boolean isVisible() {
        return true;
    }

    @Override
    public boolean supports(ApplicationType applicationType) {
        return true;
    }

    @Override
    public String getTitle() {
        return "Fat/Shaded JAR Support";
    }

    @Override
    public String getDescription() {
        return "Adds the ability to build a Fat/Shaded JAR";
    }

    @Override
    public String getCategory() {
        return Category.PACKAGING;
    }

    @Override
    public void apply(GeneratorContext generatorContext) {
        generatorContext.addHelpLink("Shadow Gradle Plugin", "https://plugins.gradle.org/plugin/com.gradleup.shadow");
        GradlePlugin.Builder builder = GradlePlugin.builder()
                .id("com.gradleup.shadow")
                .lookupArtifactId("shadow");
        generatorContext.addBuildPlugin(builder.build());

    }
}
