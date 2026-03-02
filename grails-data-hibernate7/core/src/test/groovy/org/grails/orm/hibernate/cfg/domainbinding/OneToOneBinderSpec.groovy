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
import org.grails.datastore.mapping.model.types.OneToOne as GormOneToOne
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToOneProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.hibernate.FetchMode
import org.hibernate.mapping.OneToOne as HibernateOneToOne
import org.hibernate.mapping.RootClass
import org.hibernate.type.ForeignKeyDirection
import spock.lang.Subject

import org.grails.orm.hibernate.cfg.domainbinding.binder.OneToOneBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueBinder

class OneToOneBinderSpec extends HibernateGormDatastoreSpec {

    @Subject
    OneToOneBinder binder

    SimpleValueBinder mockSimpleValueBinder = Mock(SimpleValueBinder)

    def setup() {
        binder = new OneToOneBinder(getGrailsDomainBinder().getMetadataBuildingContext(), mockSimpleValueBinder)
    }

    def "should bind one-to-one mapping with defaults"() {
        given:
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def table = new org.hibernate.mapping.Table("OWNER_TABLE")
        def ownerRoot = new RootClass(metadataBuildingContext)

        def gormOneToOne = Mock(TestOneToOne)
        def otherSide = Mock(GormOneToOne)
        def owner = Mock(PersistentEntity)
        def otherOwner = Mock(PersistentEntity)

        gormOneToOne.getName() >> "myOneToOne"
        gormOneToOne.getOwner() >> owner
        gormOneToOne.getMappedForm() >> new PropertyConfig()

        otherSide.isHasOne() >> false
        otherSide.getOwner() >> otherOwner
        otherSide.getName() >> "otherSide"

        otherOwner.getName() >> "OtherEntity"

        when:
        def hibernateOneToOne = binder.bindOneToOne(gormOneToOne, ownerRoot, null, "")

        then:
        hibernateOneToOne instanceof HibernateOneToOne
        !hibernateOneToOne.isConstrained()
        hibernateOneToOne.getForeignKeyType() == ForeignKeyDirection.TO_PARENT
        hibernateOneToOne.isAlternateUniqueKey()
        hibernateOneToOne.getFetchMode() == FetchMode.DEFAULT
        hibernateOneToOne.getReferencedEntityName() == "OtherEntity"
        hibernateOneToOne.getPropertyName() == "myOneToOne"
        hibernateOneToOne.getReferencedPropertyName() == "otherSide"
    }

    def "should bind constrained one-to-one mapping when other side is hasOne"() {
        given:
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def table = new org.hibernate.mapping.Table("OWNER_TABLE")
        def ownerRoot = new RootClass(metadataBuildingContext)

        def gormOneToOne = Mock(TestOneToOne)
        def otherSide = Mock(GormOneToOne)
        def owner = Mock(PersistentEntity)
        def otherOwner = Mock(PersistentEntity)

        gormOneToOne.getName() >> "myOneToOne"
        gormOneToOne.getOwner() >> owner
        gormOneToOne.getMappedForm() >> new PropertyConfig()

        otherSide.isHasOne() >> true
        otherSide.getOwner() >> otherOwner

        otherOwner.getName() >> "OtherEntity"

        when:
        def hibernateOneToOne = binder.bindOneToOne(gormOneToOne, ownerRoot, null, "")

        then:
        hibernateOneToOne.isConstrained()
        hibernateOneToOne.getForeignKeyType() == ForeignKeyDirection.FROM_PARENT
        hibernateOneToOne.getReferencedEntityName() == "OtherEntity"
    }

    def "should respect fetch mode from mapping"() {
        given:
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def table = new org.hibernate.mapping.Table("OWNER_TABLE")
        def ownerRoot = new RootClass(metadataBuildingContext)

        def propertyConfig = new PropertyConfig()
        propertyConfig.setFetch("join")

        def gormOneToOne = Mock(TestOneToOne)
        def otherSide = Mock(GormOneToOne)
        def owner = Mock(PersistentEntity)
        def otherOwner = Mock(PersistentEntity)

        gormOneToOne.getInverseSide() >> otherSide
        gormOneToOne.getOwner() >> owner
        gormOneToOne.getMappedForm() >> propertyConfig

        otherSide.getOwner() >> otherOwner
        otherSide.isHasOne() >> false

        when:
        def hibernateOneToOne = binder.bindOneToOne(gormOneToOne, ownerRoot, null, "")

        then:
        hibernateOneToOne.getFetchMode() == FetchMode.JOIN
    }
}

abstract class TestOneToOne extends HibernateOneToOneProperty {
    TestOneToOne(PersistentEntity owner, MappingContext context, java.beans.PropertyDescriptor descriptor) {
        super(owner, context, descriptor)
    }
}
