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
import spock.lang.PendingFeature

/**
 * Pins a defect of the domain binder that {@link GrailsDomainGenerator} deliberately does not copy.
 *
 * <p>{@code CollectionKeyColumnUpdater} makes the key of a collection not updatable when its owner has more than one
 * unidirectional to-many property. Hibernate's collection persister then disables inserting and deleting the rows of
 * every collection of that owner, so the elements of a collection of basic values are never written. The feature that
 * shows it is pending: it fails today and the spec reports it as soon as the binder is fixed.</p>
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

    @PendingFeature(reason = 'CollectionKeyColumnUpdater makes the key not updatable once an owner has two unidirectional collections, so Hibernate writes no rows')
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
