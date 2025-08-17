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
public class SQLServer extends DatabaseDriverFeature {

    public SQLServer(HibernateGorm hibernateGorm, TestContainers testContainers) {
        super(hibernateGorm, testContainers);
    }

    @Override
    @NonNull
    public String getName() {
        return "sqlserver";
    }

    @Override
    public String getTitle() {
        return "Microsoft SQL Server";
    }

    @Override
    public String getDescription() {
        return "Adds the SQL Server driver and default config";
    }

    @Override
    public String getJdbcDevUrl() {
        return "jdbc:sqlserver://localhost:1433;databaseName=devDb;socketKeepAlive=true";
    }

    @Override
    public String getJdbcTestUrl() {
        return "jdbc:sqlserver://localhost:1433;databaseName=testDb;socketKeepAlive=true";
    }

    @Override
    public String getJdbcProdUrl() {
        return "jdbc:sqlserver://localhost:1433;databaseName=prodDb;socketKeepAlive=true";
    }

    @Override
    public String getDriverClass() {
        return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
    }

    @Override
    public String getDefaultUser() {
        return "sa";
    }

    @Override
    public String getDefaultPassword() {
        return "";
    }

    @Override
    public String getDataDialect() {
        return "SQL_SERVER";
    }

    @Override
    public boolean embedded() {
        return false;
    }

    @Override
    public void apply(GeneratorContext generatorContext) {
        generatorContext.addDependency(Dependency.builder()
                .groupId("com.microsoft.sqlserver")
                .artifactId("mssql-jdbc")
                .runtimeOnly());
    }
}
