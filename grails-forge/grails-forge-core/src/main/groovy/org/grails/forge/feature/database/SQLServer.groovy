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
import org.springframework.stereotype.Component
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.build.dependencies.Dependency

@Component
@CompileStatic
class SQLServer extends DatabaseDriverFeature {

    SQLServer(GrailsDataHibernate5 grailsDataHibernate5, TestContainers testContainers) {
        super(grailsDataHibernate5, testContainers)
    }

    @Override
    @Nonnull
    String getName() {
        return 'sqlserver'
    }

    @Override
    String getTitle() {
        return 'Microsoft SQL Server'
    }

    @Override
    String getDescription() {
        return 'Add Microsoft SQL Server driver and default configuration.'
    }

    @Override
    String getJdbcDevUrl() {
        return 'jdbc:sqlserver://localhost:1433;databaseName=devDb;socketKeepAlive=true'
    }

    @Override
    String getJdbcTestUrl() {
        return 'jdbc:sqlserver://localhost:1433;databaseName=testDb;socketKeepAlive=true'
    }

    @Override
    String getJdbcProdUrl() {
        return 'jdbc:sqlserver://localhost:1433;databaseName=prodDb;socketKeepAlive=true'
    }

    @Override
    String getDriverClass() {
        return 'com.microsoft.sqlserver.jdbc.SQLServerDriver'
    }

    @Override
    String getDefaultUser() {
        return 'sa'
    }

    @Override
    String getDefaultPassword() {
        return ''
    }

    @Override
    String getDataDialect() {
        return 'SQL_SERVER'
    }

    @Override
    boolean embedded() {
        return false
    }

    @Override
    void apply(GeneratorContext generatorContext) {
        generatorContext.addDependency(Dependency.builder()
                .groupId('com.microsoft.sqlserver')
                .artifactId('mssql-jdbc')
                .runtimeOnly())
    }
}
