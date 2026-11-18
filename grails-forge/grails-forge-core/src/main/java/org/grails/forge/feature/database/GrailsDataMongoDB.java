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
public class GrailsDataMongoDB extends GormOneOfFeature {

    /**
     * The host that asks grails-data-mongodb-embedded for a server rather than naming one to
     * reach, so an environment says which database it wants in the one place it already says
     * where the database is.
     */
    private static final String EMBEDDED_HOST = "mongodb://embedded/";

    private final TestContainers testContainers;

    public GrailsDataMongoDB(TestContainers testContainers) {
        this.testContainers = testContainers;
    }

    @Override
    public String getName() {
        return "gorm-mongodb";
    }

    @Override
    public String getTitle() {
        return "Grails Data for MongoDB";
    }

    @Override
    public String getDescription() {
        return "Configure Grails Data for using MongoDB.";
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
        config.put("grails.mongodb.url", MongoFeature.externalUrl(MongoFeature.PROD_DATABASE));
        generatorContext.addDependency(Dependency.builder()
                .groupId("org.apache.grails")
                .artifactId("grails-data-mongodb")
                .implementation());
        applyEmbeddedMongo(generatorContext, config);
    }

    /**
     * Without this a generated application only starts when a MongoDB happens to be listening on
     * the url above, so it is wired in by default rather than offered as a separate feature.
     *
     * <p>Development and test name the embedded server where they would name a host, the way a
     * generated JPA application names an in-memory H2 there. Production keeps the url above, so
     * deploying with MONGO_HOST and MONGO_PORT set reaches that database. An application that
     * wants an embedded one in production says so the same way, by naming it in that url.
     */
    private void applyEmbeddedMongo(GeneratorContext generatorContext, Map<String, Object> config) {
        generatorContext.addDependency(Dependency.builder()
                .groupId("org.apache.grails")
                .artifactId("grails-data-mongodb-embedded")
                .implementation());

        config.put("environments.development.grails.mongodb.url", EMBEDDED_HOST + MongoFeature.DEV_DATABASE);
        config.put("environments.test.grails.mongodb.url", EMBEDDED_HOST + MongoFeature.TEST_DATABASE);
    }

    @Override
    public boolean shouldApply(ApplicationType applicationType, Options options, Set<Feature> selectedFeatures) {
        return selectedFeatures.stream().anyMatch(f -> f instanceof GrailsDataMongoDB) || options.getGormImpl() == GormImpl.MONGODB;
    }

    @Nullable
    @Override
    public String getThirdPartyDocumentation() {
        return "https://grails.apache.org/docs/latest/grails-data/mongodb/manual/";
    }
}
