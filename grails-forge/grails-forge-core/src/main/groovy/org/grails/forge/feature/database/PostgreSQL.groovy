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
class PostgreSQL extends DatabaseDriverFeature {

    PostgreSQL(GrailsDataHibernate5 grailsDataHibernate5, TestContainers testContainers) {
        super(grailsDataHibernate5, testContainers)
    }

    @Override
    @Nonnull
    String getName() {
        return 'postgres'
    }

    @Override
    String getTitle() {
        return 'PostgresSQL'
    }

    @Override
    String getDescription() {
        return 'Add PostgresSQL driver and default configuration.'
    }

    @Override
    String getJdbcDevUrl() {
        return 'jdbc:postgresql://localhost:5432/devDb?tcpKeepAlive=true'
    }

    @Override
    String getJdbcTestUrl() {
        return 'jdbc:postgresql://localhost:5432/testDb?tcpKeepAlive=true'
    }

    @Override
    String getJdbcProdUrl() {
        // postgres docker image uses default db name and username of postgres so we use the same
        return 'jdbc:postgresql://localhost:5432/postgres?tcpKeepAlive=true'
    }

    @Override
    String getDriverClass() {
        return 'org.postgresql.Driver'
    }

    @Override
    String getDefaultUser() {
        return 'postgres'
    }

    @Override
    String getDefaultPassword() {
        return ''
    }

    @Override
    String getDataDialect() {
        return 'POSTGRES'
    }

    @Override
    boolean embedded() {
        return false
    }

    @Override
    void apply(GeneratorContext generatorContext) {
        generatorContext.addDependency(Dependency.builder()
                .groupId('org.postgresql')
                .artifactId('postgresql')
                .runtimeOnly())
    }
}
