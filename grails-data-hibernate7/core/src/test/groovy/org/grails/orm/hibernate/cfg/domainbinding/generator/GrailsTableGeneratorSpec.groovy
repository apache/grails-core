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
import org.hibernate.id.enhanced.TableGenerator
import org.hibernate.generator.GeneratorCreationContext
import org.hibernate.mapping.Property
import org.hibernate.mapping.Table
import org.hibernate.boot.model.relational.Database
import org.hibernate.boot.model.relational.Namespace
import org.hibernate.boot.model.naming.Identifier

class GrailsTableGeneratorSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.addAllDomainClasses([
            TableGeneratorSpecEntity
        ])
    }

    def "test constructor with null mappedId"() {
        given:
        def binder = getGrailsDomainBinder()
        def context = Mock(GeneratorCreationContext)
        def property = Mock(Property)
        
        context.getProperty() >> property
        property.getName() >> "id"
        context.getDatabase() >> binder.getMetadataBuildingContext().getMetadataCollector().getDatabase()
        context.getServiceRegistry() >> binder.getMetadataBuildingContext().getBuildingOptions().getServiceRegistry()

        when:
        def generator = new GrailsTableGenerator(context, null, binder.getJdbcEnvironment())

        then:
        generator.getSegmentValue() == "default.id"
        generator.getIncrementSize() == 50
    }

    def "test constructor with existing segment value and parameters"() {
        given:
        def binder = getGrailsDomainBinder()
        def context = Mock(GeneratorCreationContext)
        def property = Mock(Property)
        def mappedId = Mock(HibernateSimpleIdentity)
        def props = new Properties()
        props.put(TableGenerator.SEGMENT_VALUE_PARAM, "custom_segment")
        props.put(TableGenerator.INCREMENT_PARAM, "100")
        props.put(TableGenerator.OPT_PARAM, "none")

        context.getProperty() >> property
        property.getName() >> "id"
        context.getDatabase() >> binder.getMetadataBuildingContext().getMetadataCollector().getDatabase()
        context.getServiceRegistry() >> binder.getMetadataBuildingContext().getBuildingOptions().getServiceRegistry()
        mappedId.getProperties() >> props

        when:
        def generator = new GrailsTableGenerator(context, mappedId, binder.getJdbcEnvironment())

        then:
        generator.getSegmentValue() == "custom_segment"
        generator.getIncrementSize() == 100
    }

    def "test constructor with mappedId but null name"() {
        given:
        def binder = getGrailsDomainBinder()
        def context = Mock(GeneratorCreationContext)
        def property = Mock(Property)
        def mappedId = Mock(HibernateSimpleIdentity)

        context.getProperty() >> property
        property.getName() >> "id"
        context.getDatabase() >> binder.getMetadataBuildingContext().getMetadataCollector().getDatabase()
        context.getServiceRegistry() >> binder.getMetadataBuildingContext().getBuildingOptions().getServiceRegistry()
        mappedId.getProperties() >> new Properties()
        mappedId.getName() >> null

        when:
        def generator = new GrailsTableGenerator(context, mappedId, binder.getJdbcEnvironment())

        then:
        generator.getSegmentValue() == "default.id"
    }

    def "test constructor with mappedId name"() {
        given:
        def binder = getGrailsDomainBinder()
        def context = Mock(GeneratorCreationContext)
        def property = Mock(Property)
        def mappedId = Mock(HibernateSimpleIdentity)

        context.getProperty() >> property
        property.getName() >> "id"
        context.getDatabase() >> binder.getMetadataBuildingContext().getMetadataCollector().getDatabase()
        context.getServiceRegistry() >> binder.getMetadataBuildingContext().getBuildingOptions().getServiceRegistry()
        mappedId.getProperties() >> new Properties()
        mappedId.getName() >> "myEntity"

        when:
        def generator = new GrailsTableGenerator(context, mappedId, binder.getJdbcEnvironment())

        then:
        generator.getSegmentValue() == "myEntity.id"
    }
}

@Entity
class TableGeneratorSpecEntity {
    Long id
}
