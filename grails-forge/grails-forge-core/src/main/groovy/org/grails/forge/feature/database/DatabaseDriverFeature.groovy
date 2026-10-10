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
package org.grails.forge.feature.database

import groovy.transform.CompileStatic
import org.grails.forge.application.ApplicationType
import org.grails.forge.feature.Category
import org.grails.forge.feature.FeatureContext
import org.grails.forge.feature.OneOfFeature

@CompileStatic
abstract class DatabaseDriverFeature implements OneOfFeature {

    private final TestContainers testContainers
    private final GrailsDataHibernate5 grailsDataHibernate5

    DatabaseDriverFeature() {
        this.testContainers = null
        this.grailsDataHibernate5 = null
    }

    DatabaseDriverFeature(GrailsDataHibernate5 grailsDataHibernate5, TestContainers testContainers) {
        this.grailsDataHibernate5 = grailsDataHibernate5
        this.testContainers = testContainers
    }

    @Override
    Class<?> getFeatureClass() {
        return DatabaseDriverFeature
    }

    @Override
    boolean supports(ApplicationType applicationType) {
        return true
    }

    @Override
    void processSelectedFeatures(FeatureContext featureContext) {
        if (!featureContext.isPresent(TestContainers) && testContainers != null) {
            featureContext.addFeature(testContainers)
        }
        if (!featureContext.isPresent(DatabaseDriverConfigurationFeature) && grailsDataHibernate5 != null) {
            featureContext.addFeature(grailsDataHibernate5)
        }
    }

    @Override
    String getCategory() {
        return Category.DATABASE
    }

    abstract boolean embedded()

    abstract String getJdbcDevUrl()

    abstract String getJdbcTestUrl()

    abstract String getJdbcProdUrl()

    abstract String getDriverClass()

    abstract String getDefaultUser()

    abstract String getDefaultPassword()

    abstract String getDataDialect()

    Map<String, Object> getAdditionalConfig() {
        return Collections.emptyMap()
    }

}
