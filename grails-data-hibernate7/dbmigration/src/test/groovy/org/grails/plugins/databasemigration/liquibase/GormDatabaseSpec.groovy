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
package org.grails.plugins.databasemigration.liquibase

import liquibase.database.DatabaseConnection
import liquibase.database.jvm.JdbcConnection
import liquibase.snapshot.DatabaseSnapshot
import liquibase.snapshot.JdbcDatabaseSnapshot
import liquibase.structure.DatabaseObject

import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.boot.Metadata
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.internal.MetadataBuilderImpl
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.dialect.H2Dialect
import spock.lang.Specification

class GormDatabaseSpec extends Specification {

    protected Metadata createRealMetadata() {
        def serviceRegistry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", H2Dialect.class.getName())
                .build()
        return new MetadataBuilderImpl(
                new MetadataSources(serviceRegistry)
        ).build()
    }

    def "test GormDatabase initialization and properties"() {
        given:
        def dialect = new H2Dialect()
        Metadata metadata = createRealMetadata()
        HibernateDatastore datastore = Mock {
            getMetadata() >> metadata
        }

        when:
        GormDatabase gormDb = Spy(GormDatabase, constructorArgs: [dialect, datastore])
        gormDb.getMetadata() >> metadata

        then:
        gormDb.getDialect().getClass() == dialect.getClass()
        gormDb.getMetadata() == metadata
        gormDb.getGormDatastore() == datastore
        gormDb.getShortName() == 'GORM'
        gormDb.getDefaultDatabaseProductName() == 'getDefaultDatabaseProductName'
        gormDb.supportsAutoIncrement()
        !gormDb.isCorrectDatabaseImplementation(Mock(DatabaseConnection))
    }

    def "test GormDatabase connection and snapshot"() {
        given:
        def dialect = new H2Dialect()
        Metadata metadata = createRealMetadata()
        HibernateDatastore datastore = Mock()
        datastore.getMetadata() >> metadata

        when:
        GormDatabase gormDb = new GormDatabase(dialect, datastore)

        then:
        gormDb.getDatabaseConnection() instanceof JdbcConnection
        gormDb.getDatabaseConnection().getURL() == 'hibernate:gorm'

        when: "creating a snapshot for the database"
        def snapshot = new JdbcDatabaseSnapshot([] as DatabaseObject[], gormDb)

        then: "it returns a DatabaseSnapshot"
        snapshot instanceof DatabaseSnapshot
        snapshot.database == gormDb
    }
}
