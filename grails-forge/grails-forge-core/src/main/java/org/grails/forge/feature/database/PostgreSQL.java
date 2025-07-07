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
import org.grails.forge.application.generator.GeneratorContext;
import org.grails.forge.build.dependencies.Dependency;

@Singleton
public class PostgreSQL extends DatabaseDriverFeature {

    public PostgreSQL(HibernateGorm hibernateGorm, TestContainers testContainers) {
        super(hibernateGorm, testContainers);
    }

    @Override
    @NonNull
    public String getName() {
        return "postgres";
    }

    @Override
    public String getTitle() {
        return "PostgresSQL";
    }

    @Override
    public String getDescription() {
        return "Adds the PostgresSQL driver and default config";
    }

    @Override
    public String getJdbcDevUrl() {
        return "jdbc:postgresql://localhost:5432/devDb?tcpKeepAlive=true";
    }

    @Override
    public String getJdbcTestUrl() {
        return "jdbc:postgresql://localhost:5432/testDb?tcpKeepAlive=true";
    }

    @Override
    public String getJdbcProdUrl() {
        // postgres docker image uses default db name and username of postgres so we use the same
        return "jdbc:postgresql://localhost:5432/postgres?tcpKeepAlive=true";
    }

    @Override
    public String getDriverClass() {
        return "org.postgresql.Driver";
    }

    @Override
    public String getDefaultUser() {
        return "postgres";
    }

    @Override
    public String getDefaultPassword() {
        return "";
    }

    @Override
    public String getDataDialect() {
        return "POSTGRES";
    }

    @Override
    public boolean embedded() {
        return false;
    }

    @Override
    public void apply(GeneratorContext generatorContext) {
        generatorContext.addDependency(Dependency.builder()
                .groupId("org.postgresql")
                .artifactId("postgresql")
                .runtimeOnly());
    }
}
