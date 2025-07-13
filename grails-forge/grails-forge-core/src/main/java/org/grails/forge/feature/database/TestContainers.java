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
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;
import org.grails.forge.application.ApplicationType;
import org.grails.forge.application.generator.GeneratorContext;
import org.grails.forge.build.dependencies.Dependency;
import org.grails.forge.feature.Category;
import org.grails.forge.feature.Feature;
import org.grails.forge.feature.config.ApplicationConfiguration;
import org.grails.forge.feature.config.Configuration;
import org.grails.forge.options.TestFramework;
import org.grails.forge.template.StringTemplate;

import java.util.Optional;

@Singleton
public class TestContainers implements Feature {

    private static final String TESTCONTAINERS_GROUP_ID = "org.testcontainers";

    @NonNull
    @Override
    public String getName() {
        return "testcontainers";
    }

    @Override
    public String getTitle() {
        return "Testcontainers";
    }

    @Override
    public String getDescription() {
        return "Use Testcontainers to run a database or other software in a Docker container for tests";
    }

    @Override
    public void apply(GeneratorContext generatorContext) {
        generatorContext.addDependency(testContainerTestDependency("testcontainers"));
        generatorContext.getFeature(DatabaseDriverFeature.class).ifPresent(driverFeature -> {
            generatorContext.getFeature(DatabaseDriverConfigurationFeature.class).ifPresent(driverConfiguration -> {
                String driver = "org.testcontainers.jdbc.ContainerDatabaseDriver";
                if (driverFeature instanceof SQLServer) {
                    generatorContext.addTemplate("sqlserverEula", new StringTemplate("src/test/resources/container-license-acceptance.txt", "mcr.microsoft.com/mssql/server:2019-CU4-ubuntu-16.04"));
                }
                urlForDatabaseDriverFeature(driverFeature).ifPresent(url -> {
                    Configuration testConfig = generatorContext.getConfiguration("test", ApplicationConfiguration.testConfig());
                    testConfig.put(driverConfiguration.getUrlKey(), url);
                    testConfig.put(driverConfiguration.getDriverKey(), driver);
                });
                artifactIdForDriverFeature(driverFeature).ifPresent(dependencyArtifactId ->
                        generatorContext.addDependency(testContainerTestDependency(dependencyArtifactId)));
            });
        });
        testContainerArtifactIdByTestFramework(generatorContext.getTestFramework()).ifPresent(testArtifactId -> {
            generatorContext.addDependency(testContainerTestDependency(testArtifactId));
        });

        if (generatorContext.isFeaturePresent(MongoFeature.class) || generatorContext.isFeaturePresent(MongoGorm.class)) {
            generatorContext.addDependency(testContainerTestDependency("mongodb"));
        }
    }

    @NonNull
    private static Dependency.Builder testContainerTestDependency(@NonNull String artifactId) {
        return Dependency.builder()
                .groupId(TESTCONTAINERS_GROUP_ID)
                .artifactId(artifactId)
                .testImplementation();
    }

    @NonNull
    private static Optional<String> testContainerArtifactIdByTestFramework(TestFramework testFramework) {
        if (testFramework == TestFramework.SPOCK) {
            return Optional.of("spock");
        } else if (testFramework == TestFramework.JUNIT) {
            return Optional.of("junit-jupiter");
        }
        return Optional.empty();
    }

    @NonNull
    private static Optional<String> artifactIdForDriverFeature(@NonNull DatabaseDriverFeature driverFeature) {
        if (driverFeature instanceof MySQL) {
            return Optional.of("mysql");
        } else if (driverFeature instanceof PostgreSQL) {
            return Optional.of("postgresql");
        } else if (driverFeature instanceof SQLServer) {
            return Optional.of("mssqlserver");
        }
        return Optional.empty();
    }

    @NonNull
    private static Optional<String> urlForDatabaseDriverFeature(@NonNull DatabaseDriverFeature driverFeature) {
        if (driverFeature instanceof MySQL) {
            return Optional.of("jdbc:tc:mysql:8:///db");

        } else if (driverFeature instanceof PostgreSQL) {
            return Optional.of("jdbc:tc:postgresql:12:///postgres");

        } else if (driverFeature instanceof SQLServer) {
            return Optional.of("jdbc:tc:sqlserver:2019-CU4-ubuntu-16.04://databaseName=tempdb");
        }
        return Optional.empty();
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
        return "https://www.testcontainers.org/";
    }
}
