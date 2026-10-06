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

/**
 * Regression test: {@code CollectionKeyColumnUpdater} made the key of a collection not updatable when its owner had
 * more than one unidirectional to-many property. Hibernate then writes no rows for the collection, so the elements of a
 * collection of basic values were silently lost. The key of a collection of basic values must stay updatable.
 */
class BasicCollectionKeyPersistenceSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(BCKPOneCollection, BCKPTwoCollections)
    }

    void "the elements of the only collection of an owner are persisted"() {
        given:
        BCKPOneCollection owner = new BCKPOneCollection(name: 'one', tags: ['a', 'b'] as Set)
        owner.save(flush: true, failOnError: true)
        session.clear()

        expect:
        BCKPOneCollection.get(owner.id).tags == ['a', 'b'] as Set
    }

    void "the elements of each of two collections of an owner are persisted"() {
        given:
        BCKPTwoCollections owner = new BCKPTwoCollections(name: 'two', tags: ['a', 'b'] as Set, scores: [1, 2, 3])
        owner.save(flush: true, failOnError: true)
        session.clear()
        BCKPTwoCollections loaded = BCKPTwoCollections.get(owner.id)

        expect:
        loaded.tags == ['a', 'b'] as Set
        loaded.scores.size() == 3
        loaded.scores as Set == [1, 2, 3] as Set
    }
}

@Entity
class BCKPOneCollection {

    String name
    Set<String> tags

    static hasMany = [tags: String]
}

@Entity
class BCKPTwoCollections {

    String name
    Set<String> tags
    List<Integer> scores

    static hasMany = [tags: String, scores: Integer]
}
