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

import org.hibernate.mapping.Column

import grails.gorm.tests.HibernateGormDatastoreSpec
import grails.persistence.Entity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.Table
import spock.lang.Shared

import org.grails.orm.hibernate.cfg.domainbinding.binder.PropertyBinder
import org.grails.orm.hibernate.cfg.domainbinding.util.CascadeBehaviorFetcher

class PropertyBinderSpec extends HibernateGormDatastoreSpec {

    @Shared PropertyBinder binder = new PropertyBinder(new CascadeBehaviorFetcher())

    void setupSpec() {
        manager.registerDomainClasses(PBEntity, PBAuthor)
    }

    void "test property binding with real objects"() {
        given:
        def entity = (HibernatePersistentEntity) getMappingContext().getPersistentEntity(PBEntity.name)
        def persistentProperty = (HibernatePersistentProperty) entity.getPropertyByName("name")
        def table = new Table("PB_ENTITY")
        def value = new BasicValue(getGrailsDomainBinder().getMetadataBuildingContext(), table)
        def column = new Column("TEST_COL")
        value.addColumn(column)

        when:
        def property = binder.bindProperty(persistentProperty, value)

        then:
        property.getName() == "name"
        !property.isOptional()
        // In Hibernate 7, the Property object's insertable/updatable state
        // is derived from the Value object provided to the binder.
        property.isInsertable()
        property.isUpdatable()
        property.getPropertyAccessorName() == "property"
        !property.isLazy()
    }

    void "test association binding laziness"() {
        given:
        def entity = (HibernatePersistentEntity) getMappingContext().getPersistentEntity(PBEntity.name)
        def persistentProperty = (HibernatePersistentProperty) entity.getPropertyByName("author")
        def table = new Table("PB_ENTITY")
        def value = new BasicValue(getGrailsDomainBinder().getMetadataBuildingContext(), table)

        when:
        def property = binder.bindProperty(persistentProperty, value)

        then:
        property.getName() == "author"
        property.isLazy()
    }

    void "test explicit lazy false binding"() {
        given:
        def entity = (HibernatePersistentEntity) getMappingContext().getPersistentEntity(PBEntity.name)
        def persistentProperty = (HibernatePersistentProperty) entity.getPropertyByName("eagerAuthor")
        def table = new Table("PB_ENTITY")
        def value = new BasicValue(getGrailsDomainBinder().getMetadataBuildingContext(), table)

        when:
        def property = binder.bindProperty(persistentProperty, value)

        then:
        property.getName() == "eagerAuthor"
        !property.isLazy()
    }

    void "test default constructor"() {
        expect:
        new PropertyBinder() != null
    }

    void "test accessorName for field access"() {
        given:
        def entity = (HibernatePersistentEntity) getMappingContext().getPersistentEntity(PBEntity.name)
        def persistentProperty = (HibernatePersistentProperty) entity.getPropertyByName("name")
        def table = new Table("PB_ENTITY")
        def value = new BasicValue(getGrailsDomainBinder().getMetadataBuildingContext(), table)
        
        def mockConfig = new org.grails.orm.hibernate.cfg.PropertyConfig()
        mockConfig.setAccessType(jakarta.persistence.AccessType.FIELD)
        
        def spyProp = Spy(persistentProperty)
        spyProp.getHibernateMappedForm() >> mockConfig

        when:
        def property = binder.bindProperty(spyProp, value)

        then:
        property.getPropertyAccessorName() == "field"
    }
}

@Entity
class PBEntity {
    Long id
    String name
    PBAuthor author
    PBAuthor eagerAuthor

    static mapping = {
        name nullable: false
        eagerAuthor lazy: false
    }
}

@Entity
class PBAuthor {
    Long id
    String name
}
