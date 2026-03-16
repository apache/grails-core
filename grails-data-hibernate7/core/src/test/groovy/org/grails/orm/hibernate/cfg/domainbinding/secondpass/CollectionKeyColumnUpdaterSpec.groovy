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
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty
import org.hibernate.mapping.Column
import org.hibernate.mapping.DependantValue
import spock.lang.Subject

class CollectionKeyColumnUpdaterSpec extends HibernateGormDatastoreSpec {

    @Subject
    CollectionKeyColumnUpdater updater = new CollectionKeyColumnUpdater()

    void "test forceNullableAndCheckUpdateable with single unidirectional association"() {
        given:
        def owner = createPersistentEntity(CollectionKeyColumnUpdaterSpecParent)
        def property = (HibernateToManyProperty) owner.getPropertyByName("children")
        
        Column column = new Column("test")
        column.setNullable(false)
        
        DependantValue key = Mock(DependantValue)
        key.getColumns() >> [column]

        when:
        updater.forceNullableAndCheckUpdatable(key, property)

        then:
        column.isNullable()
        1 * key.setUpdateable(true)
    }

    void "test forceNullableAndCheckUpdateable with multiple unidirectional associations"() {
        given:
        def owner = createPersistentEntity(CollectionKeyColumnUpdaterSpecMultiParent)
        def property = (HibernateToManyProperty) owner.getPropertyByName("children1")
        
        Column column = new Column("test")
        
        DependantValue key = Mock(DependantValue)
        key.getColumns() >> [column]

        when:
        updater.forceNullableAndCheckUpdatable(key, property)

        then:
        1 * key.setUpdateable(false)
    }

    void "test configure with bidirectional association"() {
        given:
        def owner = createPersistentEntity(CollectionKeyColumnUpdaterSpecBiParent)
        def property = (HibernateToManyProperty) owner.getPropertyByName("children")

        Column column = new Column("keyCol")

        DependantValue key = Mock(DependantValue)
        key.getColumns() >> [column]

        when:
        updater.forceNullableAndCheckUpdatable(key, property)

        then:
        column.isNullable()
        1 * key.setUpdateable(true)
    }
}

@Entity
class CollectionKeyColumnUpdaterSpecParent {
    Long id
    static hasMany = [children: CollectionKeyColumnUpdaterSpecChild]
}

@Entity
class CollectionKeyColumnUpdaterSpecChild {
    Long id
}

@Entity
class CollectionKeyColumnUpdaterSpecMultiParent {
    Long id
    static hasMany = [children1: CollectionKeyColumnUpdaterSpecChild, children2: CollectionKeyColumnUpdaterSpecChild]
}

@Entity
class CollectionKeyColumnUpdaterSpecBiParent {
    Long id
    static hasMany = [children: CollectionKeyColumnUpdaterSpecBiChild]
}

@Entity
class CollectionKeyColumnUpdaterSpecBiChild {
    Long id
    CollectionKeyColumnUpdaterSpecBiParent parent
    static belongsTo = [parent: CollectionKeyColumnUpdaterSpecBiParent]
}
