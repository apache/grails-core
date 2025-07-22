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
import org.grails.forge.build.dependencies.Scope;
import org.grails.forge.feature.Category;
import org.grails.forge.feature.Feature;
import org.grails.forge.feature.FeatureContext;

import java.util.Map;

@Singleton
public class EmbeddedMongo implements Feature {

    private MongoFeature mongoFeature;

    public EmbeddedMongo(MongoFeature mongoFeature) {
        this.mongoFeature = mongoFeature;
    }

    @Override
    public String getName() {
        return "embedded-mongodb";
    }

    @Override
    public String getTitle() {
        return "Embedded MongoDB Grails Plugin";
    }

    @Override
    public String getDescription() {
        return "Executes an embedded mongo database for integration or functional testing";
    }

    @Override
    public boolean supports(ApplicationType applicationType) {
        return true;
    }

    @Override
    public String getCategory() {
        return Category.DATABASE;
    }

    @Nullable
    @Override
    public String getThirdPartyDocumentation() {
        return "https://grails-plugins.github.io/grails-embedded-mongodb/latest";
    }

    @Override
    public void processSelectedFeatures(FeatureContext featureContext) {
        if (!featureContext.isPresent(MongoFeature.class) && mongoFeature != null) {
            featureContext.addFeature(mongoFeature);
        }
    }

    @Override
    public void apply(GeneratorContext generatorContext) {
        Map<String, Object> config = generatorContext.getConfiguration();
        config.put("environments.test.grails.mongodb.version", "${EMBEDDED_MONGO_VERSION:3.2.1}");
        config.put("environments.test.grails.mongodb.port", "${EMBEDDED_MONGO_PORT:27018}");
        generatorContext.getBuildProperties().put("embedded-mongo.version", "2.2.0");
        generatorContext.addDependency(Dependency.builder()
                .groupId("org.grails.plugins")
                .lookupArtifactId("embedded-mongodb")
                .scope(Scope.TEST_RUNTIME_ONLY));
    }
}
