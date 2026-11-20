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
import jakarta.annotation.Nonnull
import jakarta.annotation.Nullable
import org.springframework.stereotype.Component
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.build.dependencies.Dependency
import org.grails.forge.feature.Category
import org.grails.forge.feature.Feature
import org.grails.forge.feature.Features
import org.grails.forge.feature.config.ApplicationConfiguration
import org.grails.forge.feature.config.Configuration
import org.grails.forge.template.StringTemplate

@Component
@CompileStatic
class TestContainers implements Feature {

    private static final String TESTCONTAINERS_GROUP_ID = 'org.testcontainers'
    // the database drivers Testcontainers has a database for, by the names of their features, which are feature packs
    private static final Map<String, String> ARTIFACTS = [mysql: 'testcontainers-mysql', postgres: 'testcontainers-postgresql',
                                                          sqlserver: 'testcontainers-mssqlserver']
    private static final Map<String, String> URLS = [mysql: 'jdbc:tc:mysql:8:///db', postgres: 'jdbc:tc:postgresql:12:///postgres',
                                                     sqlserver: 'jdbc:tc:sqlserver:2019-CU4-ubuntu-16.04://databaseName=tempdb']

    @Nonnull
    @Override
    String getName() {
        return 'testcontainers'
    }

    @Override
    String getTitle() {
        return 'Testcontainers'
    }

    @Override
    String getCategory() {
        return Category.TESTING
    }

    @Override
    String getDescription() {
        return 'Leverage Testcontainers to run databases and other services in Docker containers during testing.'
    }

    @Override
    void apply(GeneratorContext generatorContext) {
        generatorContext.addDependency(testContainerTestDependency('testcontainers'))
        Features features = generatorContext.getFeatures()
        String driverFeature = ARTIFACTS.keySet().find { String name -> features.contains(name) }
        // for a Grails Data implementation that connects through the driver
        if (driverFeature != null && (features.contains('gorm-hibernate5') || features.contains('gorm-hibernate7'))) {
            if (driverFeature == 'sqlserver') {
                generatorContext.addTemplate('sqlserverEula', new StringTemplate('src/test/resources/container-license-acceptance.txt', 'mcr.microsoft.com/mssql/server:2019-CU4-ubuntu-16.04'))
            }
            Configuration testConfig = generatorContext.getConfiguration('test', ApplicationConfiguration.testConfig())
            testConfig.put('dataSource.url', URLS.get(driverFeature))
            testConfig.put('dataSource.driverClassName', 'org.testcontainers.jdbc.ContainerDatabaseDriver')
            generatorContext.addDependency(testContainerTestDependency(ARTIFACTS.get(driverFeature)))
        }

        if (features.contains('spock')) {
            generatorContext.addDependency(testContainerTestDependency('testcontainers-spock'))
        }

        if (generatorContext.isFeaturePresent(MongoFeature) || generatorContext.isFeaturePresent(GrailsDataMongoDB)) {
            generatorContext.addDependency(testContainerTestDependency('testcontainers-mongodb'))
        }
    }

    @Nonnull
    private static Dependency.Builder testContainerTestDependency(@Nonnull String artifactId) {
        return Dependency.builder()
            .groupId(TESTCONTAINERS_GROUP_ID)
            .artifactId(artifactId)
            .testImplementation()
    }

    @Override
    boolean supports(ApplicationType applicationType) {
        return true
    }

    @Nullable
    @Override
    String getThirdPartyDocumentation() {
        return 'https://java.testcontainers.org/'
    }
}
