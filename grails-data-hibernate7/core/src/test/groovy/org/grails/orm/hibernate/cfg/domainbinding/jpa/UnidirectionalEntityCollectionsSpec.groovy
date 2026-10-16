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
package org.grails.orm.hibernate.cfg.domainbinding.jpa

import grails.gorm.annotation.Entity
import grails.gorm.tests.HibernateGormDatastoreSpec

/**
 * The elements of a unidirectional collection of entities are written whether or not the owner has another unidirectional
 * to-many property. The classic binding of Grails 8 made the key of such a collection not updatable when the owner had more than
 * one, so Hibernate's collection persister never wrote the rows of the join table (see {@link UnidirectionalBasicCollectionsSpec}
 * for collections of basic values); the generated classes state the join column updatable.
 */
class UnidirectionalEntityCollectionsSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(DefectItem, DefectOtherItem, DefectItemOneOwner, DefectItemTwoOwner)
    }

    void "the items of the only unidirectional collection of an owner are persisted"() {
        given:
        DefectItemOneOwner owner = new DefectItemOneOwner(name: 'one')
        owner.addToItems(new DefectItem(name: 'a'))
        owner.addToItems(new DefectItem(name: 'b'))
        owner.save(flush: true, failOnError: true)
        session.clear()

        expect:
        DefectItemOneOwner.get(owner.id).items*.name.toSet() == ['a', 'b'].toSet()
    }

    void "the items of each of two unidirectional collections of an owner are persisted"() {
        given:
        DefectItemTwoOwner owner = new DefectItemTwoOwner(name: 'two')
        owner.addToItems(new DefectItem(name: 'a'))
        owner.addToOthers(new DefectOtherItem(name: 'x'))
        owner.save(flush: true, failOnError: true)
        session.clear()
        DefectItemTwoOwner loaded = DefectItemTwoOwner.get(owner.id)

        expect:
        loaded.items*.name == ['a']
        loaded.others*.name == ['x']
    }
}

@Entity
class DefectItem {

    String name
}

@Entity
class DefectOtherItem {

    String name
}

@Entity
class DefectItemOneOwner {

    String name
    Set<DefectItem> items

    static hasMany = [items: DefectItem]
}

@Entity
class DefectItemTwoOwner {

    String name
    Set<DefectItem> items
    Set<DefectOtherItem> others

    static hasMany = [items: DefectItem, others: DefectOtherItem]
}
