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
package org.grails.orm.hibernate.cfg.domainbinding.secondpass

import grails.gorm.annotation.Entity
import grails.gorm.tests.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty
import org.hibernate.mapping.Column
import org.hibernate.mapping.DependantValue
import spock.lang.Subject

class CollectionKeyColumnUpdaterSpec extends HibernateGormDatastoreSpec {

    @Subject
    CollectionKeyColumnUpdater updater

    CollectionKeyBinder collectionKeyBinder = Mock(CollectionKeyBinder)

    void setupSpec() {
        manager.registerDomainClasses(
            CKCUOwnerOne,
            CKCUItemOne,
            CKCUOwnerMany,
            CKCUItemMany1,
            CKCUItemMany2
        )
    }

    void setup() {
        updater = new CollectionKeyColumnUpdater(collectionKeyBinder)
    }

    private HibernateToManyProperty propertyFor(Class ownerClass, String name = "items") {
        (getPersistentEntity(ownerClass) as GrailsHibernatePersistentEntity).getPropertyByName(name) as HibernateToManyProperty
    }

    def "bind delegates to collectionKeyBinder and forces nullability and updateability"() {
        given:
        def property = propertyFor(CKCUOwnerOne)
        def column = new Column("test_col")
        column.setNullable(false)
        def key = new DependantValue(getGrailsDomainBinder().getMetadataBuildingContext(), null, null)
        key.addColumn(column)
        key.setUpdateable(false)

        when:
        updater.bind(property)

        then:
        1 * collectionKeyBinder.bind(property) >> key
        column.isNullable()
        key.isUpdateable()
    }

    def "bind sets updateable false when multiple unidirectional"() {
        given:
        def property = propertyFor(CKCUOwnerMany, "items1")
        def column = new Column("test_col")
        def key = new DependantValue(getGrailsDomainBinder().getMetadataBuildingContext(), null, null)
        key.addColumn(column)
        key.setUpdateable(true)

        when:
        updater.bind(property)

        then:
        1 * collectionKeyBinder.bind(property) >> key
        !key.isUpdateable()
        column.isNullable()
    }
}

@Entity
class CKCUOwnerOne {
    Long id
    static hasMany = [items: CKCUItemOne]
}

@Entity
class CKCUItemOne {
    Long id
}

@Entity
class CKCUOwnerMany {
    Long id
    static hasMany = [items1: CKCUItemMany1, items2: CKCUItemMany2]
}

@Entity
class CKCUItemMany1 {
    Long id
}

@Entity
class CKCUItemMany2 {
    Long id
}
