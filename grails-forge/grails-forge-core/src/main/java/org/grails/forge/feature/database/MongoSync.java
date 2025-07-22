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
import org.grails.forge.feature.Category;

import java.util.Map;

@Singleton
public class MongoSync extends MongoFeature {

    public MongoSync(TestContainers testContainers) {
        super(testContainers);
    }

    @Override
    public String getName() {
        return "mongo-sync";
    }

    @Override
    public String getTitle() {
        return "MongoDB Synchronous Driver";
    }

    @Override
    public String getDescription() {
        return "Adds support for the MongoDB Synchronous Driver";
    }

    @Override
    public void apply(GeneratorContext generatorContext) {
        Map<String, Object> config = generatorContext.getConfiguration();
        config.put("grails.mongodb.url", "mongodb://${MONGO_HOST:localhost}:${MONGO_PORT:27017}/foo");
        generatorContext.addDependency(Dependency.builder()
                .groupId("org.mongodb")
                .artifactId("mongodb-driver-sync")
                .implementation()
        );
    }

    @Override
    public boolean supports(ApplicationType applicationType) {
        return true;
    }

    @Override
    public String getCategory() {
        return Category.DATABASE;
    }

    @Override
    public String getDocumentation() {
        return "https://docs.mongodb.com/drivers/java/sync/current/";
    }

    @Override
    public String getThirdPartyDocumentation() {
        return "https://docs.mongodb.com";
    }
}
