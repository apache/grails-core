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
class MySQL extends DatabaseDriverFeature {

    MySQL(GrailsDataHibernate5 grailsDataHibernate5, TestContainers testContainers) {
        super(grailsDataHibernate5, testContainers)
    }

    @Override
    @Nonnull
    String getName() {
        return 'mysql'
    }

    @Override
    String getTitle() {
        return 'MySQL'
    }

    @Override
    String getDescription() {
        return 'Add MySQL driver and default configuration'
    }

    @Override
    String getJdbcDevUrl() {
        return 'jdbc:mysql://localhost:3306/devDb?tcpKeepAlive=true'
    }

    @Override
    String getJdbcTestUrl() {
        return 'jdbc:mysql://localhost:3306/testDb?tcpKeepAlive=true'
    }

    @Override
    String getJdbcProdUrl() {
        return 'jdbc:mysql://localhost:3306/prodDb?tcpKeepAlive=true'
    }

    @Override
    String getDriverClass() {
        return 'com.mysql.cj.jdbc.Driver'
    }

    @Override
    String getDefaultUser() {
        return 'root'
    }

    @Override
    String getDefaultPassword() {
        return ''
    }

    @Override
    String getDataDialect() {
        return 'MYSQL'
    }

    @Override
    boolean embedded() {
        return false
    }

    @Override
    void apply(GeneratorContext generatorContext) {
        generatorContext.addDependency(Dependency.builder()
                .groupId('com.mysql')
                .artifactId('mysql-connector-j')
                .runtimeOnly())
    }
}
