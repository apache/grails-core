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

import org.grails.forge.application.ApplicationType;
import org.grails.forge.feature.Category;
import org.grails.forge.feature.FeatureContext;
import org.grails.forge.feature.OneOfFeature;

import java.util.Collections;
import java.util.Map;

public abstract class DatabaseDriverFeature implements OneOfFeature {

    private final TestContainers testContainers;
    private final GrailsDataHibernate5 grailsDataHibernate5;

    public DatabaseDriverFeature() {
        this.testContainers = null;
        this.grailsDataHibernate5 = null;
    }

    public DatabaseDriverFeature(GrailsDataHibernate5 grailsDataHibernate5, TestContainers testContainers) {
        this.grailsDataHibernate5 = grailsDataHibernate5;
        this.testContainers = testContainers;
    }

    @Override
    public Class<?> getFeatureClass() {
        return DatabaseDriverFeature.class;
    }

    @Override
    public boolean supports(ApplicationType applicationType) {
        return true;
    }

    @Override
    public void processSelectedFeatures(FeatureContext featureContext) {
        if (!featureContext.isPresent(TestContainers.class) && testContainers != null) {
            featureContext.addFeature(testContainers);
        }
        if (!featureContext.isPresent(DatabaseDriverConfigurationFeature.class) && grailsDataHibernate5 != null) {
            featureContext.addFeature(grailsDataHibernate5);
        }
    }

    @Override
    public String getCategory() {
        return Category.DATABASE;
    }

    public abstract boolean embedded();

    public abstract String getJdbcDevUrl();

    public abstract String getJdbcTestUrl();

    public abstract String getJdbcProdUrl();

    public abstract String getDriverClass();

    public abstract String getDefaultUser();

    public abstract String getDefaultPassword();

    public abstract String getDataDialect();

    public Map<String, Object> getAdditionalConfig() {
        return Collections.emptyMap();
    }

}
