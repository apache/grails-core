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

import org.grails.forge.feature.Feature;
import org.grails.forge.feature.FeatureContext;

/**
 * Common ground for the features that point a generated application at a MongoDB.
 *
 * <p>The database names below are the ones a generated application already uses for its SQL
 * database, so an application with both does not call them different things, and are held here
 * rather than in each feature so that two MongoDB features cannot drift apart. {@code
 * GrailsDataMongoDB} uses them without extending this, since it is a GORM feature first.
 */
public abstract class MongoFeature implements Feature {

    static final String DEV_DATABASE = "devDb";

    static final String TEST_DATABASE = "testDb";

    static final String PROD_DATABASE = "prodDb";

    private final TestContainers testContainers;

    public MongoFeature(TestContainers testContainers) {
        this.testContainers = testContainers;
    }

    @Override
    public void processSelectedFeatures(FeatureContext featureContext) {
        if (!featureContext.isPresent(TestContainers.class) && testContainers != null) {
            featureContext.addFeature(testContainers);
        }
    }

    /**
     * The url of a MongoDB the application has to reach, which deploying with {@code MONGO_HOST}
     * and {@code MONGO_PORT} set points at that server.
     *
     * @param database the database to use on it
     * @return the connection url
     */
    static String externalUrl(String database) {
        return "mongodb://${MONGO_HOST:localhost}:${MONGO_PORT:27017}/" + database;
    }

}
