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

import liquibase.database.Database
import liquibase.snapshot.DatabaseSnapshot
import liquibase.snapshot.SnapshotGeneratorChain
import liquibase.structure.core.Column
import liquibase.structure.core.Table
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.cfg.HibernateMappingContext
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.Identity
import org.hibernate.boot.Metadata
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.internal.BootstrapContextImpl
import org.hibernate.boot.internal.InFlightMetadataCollectorImpl
import org.hibernate.boot.internal.MetadataBuilderImpl
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.boot.spi.MetadataBuildingContext
import org.hibernate.boot.spi.MetadataBuildingOptions
import org.hibernate.dialect.H2Dialect
import org.hibernate.mapping.PersistentClass
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.Table as HibernateTable
import spock.lang.Specification

class GormColumnSnapshotGeneratorSpec extends Specification {

    GormColumnSnapshotGenerator generator = new GormColumnSnapshotGenerator()

    protected MetadataBuildingContext createMetadataBuildingContext() {
        def serviceRegistry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", H2Dialect.class.getName())
                .build()
        MetadataBuildingOptions options = new MetadataBuilderImpl(
                new MetadataSources(serviceRegistry)
        ).getMetadataBuildingOptions()
        
        def bootstrapContext = new BootstrapContextImpl(serviceRegistry, options)
        def collector = new InFlightMetadataCollectorImpl(bootstrapContext, options)
        
        MetadataBuildingContext buildingContext = Mock(MetadataBuildingContext)
        buildingContext.getMetadataCollector() >> collector
        buildingContext.getBootstrapContext() >> bootstrapContext
        buildingContext.getMetadataBuildingOptions() >> options
        buildingContext.getBuildingOptions() >> options
        
        return buildingContext
    }

    def "test getPriority"() {
        expect:
        generator.getPriority(Column, Mock(GormDatabase)) == 110
        generator.getPriority(Table, Mock(GormDatabase)) == -1
        generator.getPriority(Column, Mock(Database)) == -1
    }

    def "snapshot delegates to chain first"() {
        given:
        Column example = new Column()
        Column resultFromChain = new Column(name: "test")
        SnapshotGeneratorChain chain = Mock()
        DatabaseSnapshot snapshot = Mock()

        when:
        Column result = generator.snapshot(example, snapshot, chain)

        then:
        1 * chain.snapshot(example, snapshot) >> resultFromChain
        result == resultFromChain
    }

    def "applyGormPropertySettings sets nullable false if property is not nullable"() {
        given:
        Column column = new Column()
        PersistentProperty prop = Mock()

        when:
        generator.applyGormPropertySettings(column, prop)

        then:
        1 * prop.isNullable() >> false
        !column.isNullable()
    }

    def "applyGormPropertySettings does not change nullable if property is nullable"() {
        given:
        Column column = new Column()
        column.setNullable(true)
        PersistentProperty prop = Mock()

        when:
        generator.applyGormPropertySettings(column, prop)

        then:
        1 * prop.isNullable() >> true
        column.isNullable()
    }

    def "applyGormIdentitySettings sets non-nullable and auto-increment for identity strategy"() {
        given:
        Column column = new Column()
        GrailsHibernatePersistentEntity gpe = Mock()
        Mapping mapping = Mock()
        Identity identity = Mock()

        when:
        generator.applyGormIdentitySettings(column, gpe)

        then:
        !column.isNullable()
        1 * gpe.getMappedForm() >> mapping
        1 * mapping.getIdentity() >> identity
        1 * mapping.isTablePerConcreteClass() >> false
        1 * identity.determineGeneratorName(false) >> "identity"
        column.getAutoIncrementInformation() != null
    }

    def "test findPersistentClass"() {
        given:
        Metadata metadata = Mock()
        MetadataBuildingContext buildingContext = createMetadataBuildingContext()
        RootClass pc1 = new RootClass(buildingContext)
        HibernateTable table1 = new HibernateTable("hibernate", "TEST_TABLE")
        pc1.setTable(table1)
        
        when:
        PersistentClass result = generator.findPersistentClass(metadata, "test_table")

        then:
        1 * metadata.getEntityBindings() >> [pc1]
        result == pc1
    }

    def "test isIdentifier"() {
        given:
        MetadataBuildingContext buildingContext = createMetadataBuildingContext()
        RootClass pc = new RootClass(buildingContext)
        HibernateTable hTable = new HibernateTable("hibernate", "test")
        pc.setTable(hTable)
        BasicValue identifier = new BasicValue(buildingContext, hTable)
        org.hibernate.mapping.Column hibernateColumn = new org.hibernate.mapping.Column("id")
        identifier.addColumn(hibernateColumn)
        pc.setIdentifier(identifier)

        expect:
        generator.isIdentifier(pc, "id")
        !generator.isIdentifier(pc, "other")
    }

    def "snapshot applies GORM settings for identifier"() {
        given:
        Column example = new Column(name: "id")
        Table table = new Table(name: "test_table")
        example.setRelation(table)
        
        Column chainResult = new Column(name: "id")
        chainResult.setRelation(table)
        chainResult.setNullable(true)

        SnapshotGeneratorChain chain = Mock()
        DatabaseSnapshot snapshot = Mock()
        GormDatabase database = Mock()
        HibernateDatastore datastore = Mock()
        Metadata metadata = Mock()
        HibernateMappingContext mappingContext = Mock()
        MetadataBuildingContext buildingContext = createMetadataBuildingContext()
        
        // Hibernate objects
        RootClass pc = new RootClass(buildingContext)
        pc.setEntityName("TestEntity")
        pc.setClassName("com.example.TestEntity")
        HibernateTable hTable = new HibernateTable("hibernate", "test_table")
        pc.setTable(hTable)
        BasicValue identifier = new BasicValue(buildingContext, hTable)
        identifier.addColumn(new org.hibernate.mapping.Column("id"))
        pc.setIdentifier(identifier)

        // GORM mocks
        GrailsHibernatePersistentEntity gpe = Mock()
        Mapping gormMapping = Mock()
        Identity gormIdentity = Mock()

        when:
        Column result = generator.snapshot(example, snapshot, chain)

        then:
        1 * chain.snapshot(example, snapshot) >> chainResult
        snapshot.database >> database
        database.gormDatastore >> datastore
        database.metadata >> metadata
        datastore.mappingContext >> mappingContext
        
        metadata.getEntityBindings() >> [pc]
        mappingContext.getPersistentEntity("com.example.TestEntity") >> gpe
        gpe.getMappedForm() >> gormMapping
        gormMapping.getIdentity() >> gormIdentity
        gormIdentity.determineGeneratorName(_) >> "identity"

        !result.isNullable()
        result.getAutoIncrementInformation() != null
    }
}
