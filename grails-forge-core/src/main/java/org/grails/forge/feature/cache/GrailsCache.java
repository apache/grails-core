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
package org.grails.forge.feature.cache;

import jakarta.inject.Singleton;
import org.grails.forge.application.ApplicationType;
import org.grails.forge.application.generator.GeneratorContext;
import org.grails.forge.build.dependencies.Dependency;
import org.grails.forge.feature.Category;
import org.grails.forge.feature.Feature;

import java.util.Map;

@Singleton
public class GrailsCache implements Feature {

    @Override
    public String getName() {
        return "cache";
    }

    @Override
    public String getTitle() {
        return "Grails Cache Plugin";
    }

    @Override
    public String getDescription() {
        return "The Grails Cache plugin provides powerful and easy to use caching functionality to Grails applications and plugins.";
    }

    @Override
    public void apply(GeneratorContext generatorContext) {
        Map<String, Object> config = generatorContext.getConfiguration();
        config.put("grails.cache.enabled", true);
        config.put("grails.cache.cleanAtStartup", false);
        config.put("grails.cache.cacheManager", "grails.plugin.cache.GrailsConcurrentMapCacheManager");
        generatorContext.addDependency(Dependency.builder()
                .groupId("org.grails.plugins")
                .artifactId("cache")
                .implementation());
    }

    @Override
    public boolean supports(ApplicationType applicationType) {
        return true;
    }

    @Override
    public String getCategory() {
        return Category.CACHE;
    }

    @Override
    public String getDocumentation() {
        return "https://grails-plugins.github.io/grails-cache/latest/guide/index.html";
    }

}
