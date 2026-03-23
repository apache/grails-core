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
package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.CompositeIdentity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.*
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.grails.orm.hibernate.cfg.domainbinding.binder.*
import org.hibernate.mapping.ManyToOne
import org.hibernate.mapping.Table
import org.hibernate.mapping.PersistentClass
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Map as HibernateMap // Use non-sealed Map instead of abstract Collection
import org.hibernate.boot.spi.MetadataBuildingContext
import spock.lang.Unroll

class ManyToOneBinderSpec extends HibernateGormDatastoreSpec {

    ManyToOneBinder binder
    PersistentEntityNamingStrategy namingStrategy = Mock()
    SimpleValueBinder simpleValueBinder = Mock()
    ManyToOneValuesBinder manyToOneValuesBinder = Mock()
    CompositeIdentifierToManyToOneBinder compositeBinder = Mock()
    MetadataBuildingContext metadataBuildingContext

    def setup() {
        metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        binder = new ManyToOneBinder(
                metadataBuildingContext,
                namingStrategy,
                simpleValueBinder,
                manyToOneValuesBinder,
                compositeBinder
        )
    }

    @Unroll
    def "Test bindManyToOne (ManyToOneProperty) orchestration for #scenario"() {
        given:
        def association = Mock(HibernateManyToOneProperty)
        def table = Mock(Table)
        def path = "/test"
        def (mapping, refDomainClass) = mockEntity(hasCompositeId)

        association.getHibernateAssociatedEntity() >> refDomainClass
        def propertyConfig = Mock(PropertyConfig)
        association.getMappedForm() >> propertyConfig
        association.getHibernateMappedForm() >> propertyConfig

        when:
        def result = binder.bindManyToOne(association, table, path)

        then:
        result instanceof ManyToOne
        1 * manyToOneValuesBinder.bindManyToOneValues(association, _ as ManyToOne)
        compositeBinderCalls * compositeBinder.bindCompositeIdentifierToManyToOne(association, _ as ManyToOne, _, refDomainClass, path)
        simpleValueBinderCalls * simpleValueBinder.bindSimpleValue(association, null, _ as ManyToOne, path)

        where:
        scenario                 | hasCompositeId | compositeBinderCalls | simpleValueBinderCalls
        "a composite identifier" | true           | 1                    | 0
        "a simple identifier"    | false          | 0                    | 1
    }

    def "Test bindManyToOne (ManyToManyProperty) with circular logic"() {
        given:
        def property = Mock(HibernateManyToManyProperty)
        def otherSide = Mock(HibernateManyToManyProperty)
        def table = Mock(Table)
        def collectionTable = new Table("coll_table")

        // FIX: Provide real objects for the Map constructor
        PersistentClass ownerClass = new RootClass(metadataBuildingContext)
        def realCollection = new HibernateMap(metadataBuildingContext, ownerClass)
        realCollection.setCollectionTable(collectionTable)

        property.getCollection() >> realCollection
        property.getHibernateInverseSide() >> otherSide

        def (mapping, ownerEntity) = mockEntity(false)
        mapping.setColumns([:])

        def propertyConfig = Mock(PropertyConfig)
        propertyConfig.hasJoinKeyMapping() >> false

        otherSide.getHibernateOwner() >> ownerEntity
        otherSide.getOwner() >> ownerEntity
        ownerEntity.getName() >> "OwnerEntity"

        otherSide.isCircular() >> true
        otherSide.getName() >> "circularProp"
        otherSide.getMappedForm() >> propertyConfig
        otherSide.getHibernateMappedForm() >> propertyConfig
        mapping.getColumns().put("circularProp", propertyConfig)

        namingStrategy.resolveColumnName("circularProp") >> "circular_prop"

        when:
        def result = binder.bindManyToOne(property, "/test")

        then:
        result instanceof ManyToOne
        result.getReferencedEntityName() == "OwnerEntity"
        result.getTable() == collectionTable
        1 * manyToOneValuesBinder.bindManyToOneValues(otherSide, _ as ManyToOne)
        1 * simpleValueBinder.bindSimpleValue(otherSide, null, _ as ManyToOne, "/test")

        mapping.getColumns().get("circularProp") == propertyConfig
        1 * propertyConfig.setJoinTable({ it.key.name == "circular_prop_id" })
    }

    def "Test bindManyToOne (OneToOneProperty)"() {
        given:
        def property = Mock(HibernateOneToOneProperty)
        def table = Mock(Table)
        def (mapping, refDomainClass) = mockEntity(false)

        property.getTable() >> table
        property.getHibernateAssociatedEntity() >> refDomainClass
        def propertyConfig = Mock(PropertyConfig)
        property.getMappedForm() >> propertyConfig
        property.getHibernateMappedForm() >> propertyConfig

        when:
        def result = binder.bindManyToOne(property, "/test/path")

        then:
        result instanceof ManyToOne
        1 * manyToOneValuesBinder.bindManyToOneValues(property, _ as ManyToOne)
        1 * simpleValueBinder.bindSimpleValue(property, null, _ as ManyToOne, "/test/path")
    }

    private List mockEntity(boolean composite) {
        def mapping = new Mapping()
        def compositeId = composite ? new CompositeIdentity() : null
        mapping.setIdentity(compositeId)

        def entity = Mock(GrailsHibernatePersistentEntity) {
            getMappedForm() >> mapping
            getHibernateCompositeIdentity() >> Optional.ofNullable(compositeId)
        }
        return [mapping, entity]
    }
}