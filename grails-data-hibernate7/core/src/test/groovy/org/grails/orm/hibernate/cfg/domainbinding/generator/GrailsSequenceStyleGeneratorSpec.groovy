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
package org.grails.orm.hibernate.cfg.domainbinding.generator

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.HibernateSimpleIdentity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.hibernate.boot.model.relational.Database
import org.hibernate.boot.model.relational.SqlStringGenerationContext
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment
import org.hibernate.generator.GeneratorCreationContext
import org.hibernate.id.enhanced.DatabaseStructure
import org.hibernate.mapping.RootClass

class GrailsSequenceStyleGeneratorSpec extends HibernateGormDatastoreSpec {

    static DatabaseStructure staticMockStructure

    static class TestGrailsSequenceStyleGenerator extends GrailsSequenceStyleGenerator {
        Properties capturedProps
        Database capturedDatabase
        SqlStringGenerationContext capturedSqlContext

        TestGrailsSequenceStyleGenerator(GeneratorCreationContext context, HibernateSimpleIdentity mappedId, JdbcEnvironment jdbcEnvironment) {
            super(context, mappedId, jdbcEnvironment)
        }

        @Override
        void configure(GeneratorCreationContext context, Properties params) {
            this.capturedProps = params
        }

        @Override
        void registerExportables(Database database) {
            this.capturedDatabase = database
        }

        @Override
        void initialize(SqlStringGenerationContext context) {
            this.capturedSqlContext = context
        }

        @Override
        DatabaseStructure getDatabaseStructure() {
            return staticMockStructure
        }
    }

    void setupSpec() {
        manager.addAllDomainClasses([
            SequenceStyleGeneratorSpecEntity
        ])
    }

    def "test constructor logic with default parameters"() {
        given:
        def binder = getGrailsDomainBinder()
        def context = Mock(GeneratorCreationContext)
        def persistentEntity = getPersistentEntity(SequenceStyleGeneratorSpecEntity) as GrailsHibernatePersistentEntity
        def rootClass = new RootClass(binder.getMetadataBuildingContext())
        persistentEntity.setPersistentClass(rootClass)
        
        def database = binder.getMetadataBuildingContext().getMetadataCollector().getDatabase()
        def jdbcEnvironment = binder.getJdbcEnvironment()
        def mappedId = Mock(HibernateSimpleIdentity)
        def props = new Properties()

        context.getDatabase() >> database
        context.getServiceRegistry() >> binder.getMetadataBuildingContext().getBuildingOptions().getServiceRegistry()
        mappedId.getProperties() >> props

        when:
        def generator = new TestGrailsSequenceStyleGenerator(context, mappedId, jdbcEnvironment)

        then:
        generator.capturedProps.getProperty("increment_size") == "50"
        generator.capturedProps.getProperty("optimizer") == "pooled-lo"
    }

    def "test constructor with null mappedId and null jdbcEnvironment"() {
        given:
        def binder = getGrailsDomainBinder()
        def context = Mock(GeneratorCreationContext)

        context.getServiceRegistry() >> binder.getMetadataBuildingContext().getBuildingOptions().getServiceRegistry()

        when:
        def generator = new TestGrailsSequenceStyleGenerator(context, null, null)

        then:
        generator.capturedProps.getProperty("increment_size") == "50"
        generator.capturedProps.getProperty("optimizer") == "pooled-lo"
        generator.capturedDatabase == null
    }

    def "test constructor with database structure and physical names"() {
        given:
        def binder = getGrailsDomainBinder()
        def context = Mock(GeneratorCreationContext)
        def database = binder.getMetadataBuildingContext().getMetadataCollector().getDatabase()
        def jdbcEnvironment = binder.getJdbcEnvironment()
        def structure = Mock(DatabaseStructure)
        staticMockStructure = structure

        context.getDatabase() >> database
        context.getServiceRegistry() >> binder.getMetadataBuildingContext().getBuildingOptions().getServiceRegistry()

        when:
        def generator = new TestGrailsSequenceStyleGenerator(context, null, jdbcEnvironment)

        then:
        generator.capturedDatabase == database
        generator.capturedSqlContext != null

        cleanup:
        staticMockStructure = null
    }
}

@Entity
class SequenceStyleGeneratorSpecEntity {
    Long id
}
