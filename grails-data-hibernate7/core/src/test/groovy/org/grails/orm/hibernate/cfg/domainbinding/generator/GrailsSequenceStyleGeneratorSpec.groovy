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
import grails.gorm.tests.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.HibernateSimpleIdentity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.hibernate.boot.model.relational.Database
import org.hibernate.boot.model.relational.SqlStringGenerationContext
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment
import org.hibernate.generator.GeneratorCreationContext
import org.hibernate.id.PersistentIdentifierGenerator
import org.hibernate.id.enhanced.DatabaseStructure
import org.hibernate.id.enhanced.SequenceStyleGenerator
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Table

import spock.lang.Unroll

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
        manager.registerDomainClasses(
            SequenceStyleGeneratorSpecEntity
        )
    }

    def "test constructor logic with default parameters"() {
        given:
        def binder = getGrailsDomainBinder()
        def context = Mock(GeneratorCreationContext)
        def persistentEntity = getPersistentEntity(SequenceStyleGeneratorSpecEntity) as GrailsHibernatePersistentEntity
        def rootClass = new RootClass(binder.getMetadataBuildingContext())
        rootClass.setTable(new Table('orm', 'sequence_style_generator_spec_entity'))
        persistentEntity.setPersistentClass(rootClass)

        def database = binder.getMetadataBuildingContext().getMetadataCollector().getDatabase()
        def jdbcEnvironment = binder.getJdbcEnvironment()
        def mappedId = Mock(HibernateSimpleIdentity)
        def props = new Properties()

        context.getDatabase() >> database
        context.getServiceRegistry() >> binder.getMetadataBuildingContext().getBuildingOptions().getServiceRegistry()
        context.getRootClass() >> rootClass
        mappedId.getProperties() >> props

        when:
        def generator = new TestGrailsSequenceStyleGenerator(context, mappedId, jdbcEnvironment)

        then:
        generator.capturedProps.getProperty("increment_size") == "50"
        generator.capturedProps.getProperty("optimizer") == "pooled-lo"

        and: "the mapping names no sequence, so the target table is supplied for Hibernate to derive one from"
        generator.capturedProps.getProperty(PersistentIdentifierGenerator.TABLE) == 'sequence_style_generator_spec_entity'
    }

    @Unroll
    def "test no target table is supplied when the mapping names the sequence itself (#description)"() {
        given:
        def binder = getGrailsDomainBinder()
        def context = Mock(GeneratorCreationContext)
        def rootClass = new RootClass(binder.getMetadataBuildingContext())
        rootClass.setTable(new Table('orm', 'sequence_style_generator_spec_entity'))
        def mappedId = Mock(HibernateSimpleIdentity)
        def props = new Properties()
        params.each { key, value -> props.setProperty(key, value) }

        context.getDatabase() >> binder.getMetadataBuildingContext().getMetadataCollector().getDatabase()
        context.getServiceRegistry() >> binder.getMetadataBuildingContext().getBuildingOptions().getServiceRegistry()
        context.getRootClass() >> rootClass
        mappedId.getProperties() >> props

        when:
        def generator = new TestGrailsSequenceStyleGenerator(context, mappedId, binder.getJdbcEnvironment())

        then: "Hibernate resolves the name from these params itself, so it needs no table to derive one"
        generator.capturedProps.getProperty(PersistentIdentifierGenerator.TABLE) == null

        where:
        description                   | params
        "'sequence' names it"         | [(SequenceStyleGenerator.ALT_SEQUENCE_PARAM): 'my_seq']
        "'sequence_name' names it"    | [(SequenceStyleGenerator.SEQUENCE_PARAM): 'my_seq']
        "'sequence_name' wins over 'sequence'" | [(SequenceStyleGenerator.SEQUENCE_PARAM): 'my_seq',
                                                  (SequenceStyleGenerator.ALT_SEQUENCE_PARAM): 'other_seq']
    }

    @Unroll
    def "test the target table is supplied when the named sequence is blank (#description)"() {
        given:
        def binder = getGrailsDomainBinder()
        def context = Mock(GeneratorCreationContext)
        def rootClass = new RootClass(binder.getMetadataBuildingContext())
        rootClass.setTable(new Table('orm', 'sequence_style_generator_spec_entity'))
        def mappedId = Mock(HibernateSimpleIdentity)
        def props = new Properties()
        params.each { key, value -> props.setProperty(key, value) }

        context.getDatabase() >> binder.getMetadataBuildingContext().getMetadataCollector().getDatabase()
        context.getServiceRegistry() >> binder.getMetadataBuildingContext().getBuildingOptions().getServiceRegistry()
        context.getRootClass() >> rootClass
        mappedId.getProperties() >> props

        when:
        def generator = new TestGrailsSequenceStyleGenerator(context, mappedId, binder.getJdbcEnvironment())

        then: "a blank name is no name to Hibernate either, so it still has to derive one from the table"
        generator.capturedProps.getProperty(PersistentIdentifierGenerator.TABLE) == 'sequence_style_generator_spec_entity'

        where:
        description                | params
        "'sequence' is blank"      | [(SequenceStyleGenerator.ALT_SEQUENCE_PARAM): '']
        "'sequence_name' is blank" | [(SequenceStyleGenerator.SEQUENCE_PARAM): '']
    }

    def "test no target table is supplied when there is no root class"() {
        given: "a generator for something without one, such as a component identifier"
        def binder = getGrailsDomainBinder()
        def context = Mock(GeneratorCreationContext)
        def mappedId = Mock(HibernateSimpleIdentity)

        context.getDatabase() >> binder.getMetadataBuildingContext().getMetadataCollector().getDatabase()
        context.getServiceRegistry() >> binder.getMetadataBuildingContext().getBuildingOptions().getServiceRegistry()
        context.getRootClass() >> null
        mappedId.getProperties() >> new Properties()

        when:
        def generator = new TestGrailsSequenceStyleGenerator(context, mappedId, binder.getJdbcEnvironment())

        then: "there is no table to name, and resolving one anyway would fail"
        generator.capturedProps.getProperty(PersistentIdentifierGenerator.TABLE) == null
        generator.capturedProps.getProperty("increment_size") == "50"
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
