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

import jakarta.inject.Singleton;
import org.grails.forge.application.ApplicationType;
import org.grails.forge.application.generator.GeneratorContext;
import org.grails.forge.build.dependencies.Dependency;
import org.grails.forge.feature.Feature;
import org.grails.forge.options.GormImpl;
import org.grails.forge.options.Options;

import java.util.Map;
import java.util.Set;

@Singleton
public class Neo4jGorm extends GormOneOfFeature {

    @Override
    public String getName() {
        return "gorm-neo4j";
    }

    @Override
    public String getTitle() {
        return "GORM for Neo4j";
    }

    @Override
    public String getDescription() {
        return "Configures GORM for Neo4j for Groovy applications";
    }

    @Override
    public void apply(GeneratorContext generatorContext) {
        Map<String, Object> config = generatorContext.getConfiguration();
        applyDefaultGormConfig(config);
        config.put("grails.neo4j.type", "embedded");
        config.put("grails.neo4j.location", "build/data/neo4j");
        generatorContext.addDependency(Dependency.builder()
                .groupId("org.grails.plugins")
                .artifactId("neo4j")
                .implementation());

        generatorContext.addDependency(Dependency.builder()
                .groupId("org.neo4j.test")
                .artifactId("neo4j-harness")
                .testRuntimeOnly());
    }

    @Override
    public boolean shouldApply(ApplicationType applicationType, Options options, Set<Feature> selectedFeatures) {
        return selectedFeatures.stream().anyMatch(f -> f instanceof Neo4jGorm) || options.getGormImpl() == GormImpl.NEO4J;
    }
}
