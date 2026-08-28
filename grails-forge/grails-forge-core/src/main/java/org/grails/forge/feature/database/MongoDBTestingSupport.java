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

import io.micronaut.core.annotation.NonNull;
import jakarta.inject.Singleton;
import org.grails.forge.application.ApplicationType;
import org.grails.forge.application.generator.GeneratorContext;
import org.grails.forge.build.dependencies.Dependency;
import org.grails.forge.feature.Category;
import org.grails.forge.feature.Feature;
import org.grails.forge.feature.FeaturePhase;

@Singleton
public class MongoDBTestingSupport implements Feature {

    @NonNull
    @Override
    public String getName() {
        return "mongodb-testing-support";
    }

    @Override
    public String getTitle() {
        return "MongoDB Testing Support";
    }

    @Override
    public String getDescription() {
        return "Adds Grails MongoDB testing support with Testcontainers for unit and integration tests.";
    }

    @Override
    public String getCategory() {
        return Category.TESTING;
    }

    @Override
    public int getOrder() {
        return FeaturePhase.TEST.getOrder();
    }

    @Override
    public boolean supports(ApplicationType applicationType) {
        return true;
    }

    @Override
    public void apply(GeneratorContext generatorContext) {
        generatorContext.addDependency(Dependency.builder()
                .groupId("org.apache.grails.testing")
                .artifactId("grails-testing-support-mongodb")
                .testImplementation());
    }

    @Override
    public String getDocumentation() {
        return "https://grails.apache.org/docs/latest/guide/testing.html#testingMongodb";
    }
}
