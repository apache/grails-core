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
 * Regression test for a defect of the domain binder: {@code CollectionKeyColumnUpdater} made the key of a collection
 * not updatable when its owner had more than one unidirectional to-many property. Hibernate's collection persister then
 * disables inserting and deleting the rows of the collection, so the elements of a collection of basic values were never
 * written. The key of a collection of basic values must stay updatable.
 */
class BasicCollectionKeyDefectSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(DefectOneCollection, DefectTwoCollections)
    }

    void "the elements of the only collection of an owner are persisted"() {
        given:
        DefectOneCollection owner = new DefectOneCollection(name: 'one', tags: ['a', 'b'] as Set)
        owner.save(flush: true, failOnError: true)
        session.clear()

        expect:
        DefectOneCollection.get(owner.id).tags == ['a', 'b'] as Set
    }

    void "the elements of each of two collections of an owner are persisted"() {
        given:
        DefectTwoCollections owner = new DefectTwoCollections(name: 'two', tags: ['a', 'b'] as Set, scores: [1, 2, 3])
        owner.save(flush: true, failOnError: true)
        session.clear()
        DefectTwoCollections loaded = DefectTwoCollections.get(owner.id)

        expect:
        loaded.tags == ['a', 'b'] as Set
        loaded.scores == [1, 2, 3]
    }
}

@Entity
class DefectOneCollection {

    String name
    Set<String> tags

    static hasMany = [tags: String]
}

@Entity
class DefectTwoCollections {

    String name
    Set<String> tags
    List<Integer> scores

    static hasMany = [tags: String, scores: Integer]
}
