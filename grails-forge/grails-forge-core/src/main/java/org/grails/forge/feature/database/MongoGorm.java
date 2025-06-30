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
package org.grails.forge.feature.database;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;
import org.grails.forge.application.ApplicationType;
import org.grails.forge.application.generator.GeneratorContext;
import org.grails.forge.build.dependencies.Dependency;
import org.grails.forge.feature.Feature;
import org.grails.forge.feature.FeatureContext;
import org.grails.forge.options.GormImpl;
import org.grails.forge.options.Options;

import java.util.Map;
import java.util.Set;

@Singleton
public class MongoGorm extends GormOneOfFeature {

    private final TestContainers testContainers;

    public MongoGorm(TestContainers testContainers) {
        this.testContainers = testContainers;
    }

    @Override
    public String getName() {
        return "gorm-mongodb";
    }

    @Override
    public String getTitle() {
        return "GORM for MongoDB";
    }

    @Override
    public String getDescription() {
        return "Configures GORM for MongoDB for Groovy applications";
    }

    @Override
    public void processSelectedFeatures(FeatureContext featureContext) {
        super.processSelectedFeatures(featureContext);
        if (!featureContext.isPresent(TestContainers.class) && testContainers != null) {
            featureContext.addFeature(testContainers);
        }
    }

    @Override
    public void apply(GeneratorContext generatorContext) {
        applyDefaultGormConfig(generatorContext.getConfiguration());
        Map<String, Object> config = generatorContext.getConfiguration();
        config.put("grails.mongodb.url", "mongodb://${MONGO_HOST:localhost}:${MONGO_PORT:27017}/foo");
        generatorContext.addDependency(Dependency.builder()
                .groupId("org.apache.grails")
                .artifactId("grails-data-mongodb")
                .implementation());
    }

    @Override
    public boolean shouldApply(ApplicationType applicationType, Options options, Set<Feature> selectedFeatures) {
        return selectedFeatures.stream().anyMatch(f -> f instanceof MongoGorm) || options.getGormImpl() == GormImpl.MONGODB;
    }

    @Nullable
    @Override
    public String getThirdPartyDocumentation() {
        return "https://gorm.grails.org/latest/mongodb/manual/";
    }
}
