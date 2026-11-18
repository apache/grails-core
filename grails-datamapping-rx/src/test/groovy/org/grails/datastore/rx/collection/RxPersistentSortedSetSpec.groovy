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
package org.grails.datastore.rx.collection

import org.grails.datastore.mapping.config.Property
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.PropertyMapping
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.rx.RxDatastoreClient
import org.grails.datastore.rx.exceptions.BlockingOperationException
import org.grails.datastore.rx.internal.RxDatastoreClientImplementor
import org.grails.datastore.rx.query.QueryState
import org.grails.datastore.rx.query.RxQuery
import rx.Observable
import rx.observers.TestSubscriber
import spock.lang.Specification

class RxPersistentSortedSetSpec extends Specification {

    private static class TestChild {
    }

    void "constructing with an association key builds an equality query against the inverse side"() {
        given:
        Association association = createAssociation()
        Query query = Mock(Query, additionalInterfaces: [RxQuery])
        RxDatastoreClientImplementor client = Mock(RxDatastoreClientImplementor)

        when:
        RxPersistentSortedSet<TestChild> set = new RxPersistentSortedSet<>(client, association, 5L, new QueryState())

        then:
        1 * client.createQuery(TestChild, _ as QueryState) >> query
        1 * query.eq('owner', 5L) >> query
        1 * query.findAll() >> Observable.empty()
        set.datastoreClient.is(client)
        set.association.is(association)
        set.associationKeys == []
    }

    void "constructing with a collection of entity keys builds an inclusion query against the identifier"() {
        given:
        Association association = createAssociation()
        Query query = Mock(Query, additionalInterfaces: [RxQuery])
        RxDatastoreClientImplementor client = Mock(RxDatastoreClientImplementor)

        when:
        RxPersistentSortedSet<TestChild> set = newFromKeys(client, association, [1L, 2L], new QueryState())

        then:
        1 * client.createQuery(TestChild, _ as QueryState) >> query
        1 * query.in('id', [1L, 2L]) >> query
        1 * query.findAll() >> Observable.empty()
        set.associationKeys == [1L, 2L]
    }

    void "constructing with an initializer query resolves it directly without asking the client to build one"() {
        given:
        Association association = createAssociation()
        Query initializerQuery = Mock(Query, additionalInterfaces: [RxQuery])
        RxDatastoreClientImplementor client = Mock(RxDatastoreClientImplementor)

        when:
        RxPersistentSortedSet<TestChild> set = newFromQuery(client, association, initializerQuery, new QueryState())

        then:
        0 * client.createQuery(_, _)
        1 * initializerQuery.findAll() >> Observable.empty()
        set.associationKeys == []
    }

    void "initialize subscribes to the resolved observable once no matter how many collection operations trigger it"() {
        given:
        Association association = createAssociation()
        int subscriptions = 0
        Observable observable = Observable.just(1).doOnSubscribe { subscriptions++ }
        RxDatastoreClientImplementor client = Stub(RxDatastoreClientImplementor) {
            createQuery(_, _) >> Stub(Query, additionalInterfaces: [RxQuery]) {
                findAll() >> observable
            }
            isAllowBlockingOperations() >> true
        }
        RxPersistentSortedSet set = new RxPersistentSortedSet(client, association, 5L, new QueryState())

        when:
        int size = set.size()
        boolean empty = set.isEmpty()
        Iterator iterator = set.iterator()

        then:
        size == 1
        !empty
        iterator.hasNext()
        subscriptions == 1
    }

    void "initialize throws a BlockingOperationException when the client does not allow blocking operations"() {
        given:
        Association association = createAssociation()
        RxDatastoreClientImplementor client = Stub(RxDatastoreClientImplementor) {
            createQuery(_, _) >> Stub(Query, additionalInterfaces: [RxQuery]) {
                findAll() >> Observable.just(new TestChild())
            }
            isAllowBlockingOperations() >> false
        }
        RxPersistentSortedSet<TestChild> set = new RxPersistentSortedSet<>(client, association, 5L, new QueryState())

        when:
        set.size()

        then:
        thrown(BlockingOperationException)
    }

    void "first, last, comparator, subSet, headSet and tailSet all trigger lazy initialization exactly once"() {
        given:
        Association association = createAssociation()
        int subscriptions = 0
        Observable observable = Observable.from([1, 2, 3]).doOnSubscribe { subscriptions++ }
        RxDatastoreClientImplementor client = Stub(RxDatastoreClientImplementor) {
            createQuery(_, _) >> Stub(Query, additionalInterfaces: [RxQuery]) {
                findAll() >> observable
            }
            isAllowBlockingOperations() >> true
        }
        RxPersistentSortedSet set = new RxPersistentSortedSet(client, association, 5L, new QueryState())

        expect: 'first() alone triggers initialization of the underlying set'
        set.first() == 1

        and:
        set.last() == 3
        set.comparator() == null
        set.subSet(1, 3) == [1, 2] as SortedSet
        set.headSet(2) == [1] as SortedSet
        set.tailSet(2) == [2, 3] as SortedSet
        subscriptions == 1
    }

    void "subscribe delegates to the resolved observable"() {
        given:
        Association association = createAssociation()
        Observable<TestChild> observable = Observable.just(new TestChild())
        RxDatastoreClientImplementor client = Stub(RxDatastoreClientImplementor) {
            createQuery(_, _) >> Stub(Query, additionalInterfaces: [RxQuery]) {
                findAll() >> observable
            }
        }
        RxPersistentSortedSet<TestChild> set = new RxPersistentSortedSet<>(client, association, 5L, new QueryState())
        TestSubscriber<TestChild> subscriber = new TestSubscriber<>()

        when:
        set.subscribe(subscriber)

        then:
        subscriber.assertCompleted()
        subscriber.assertValueCount(1)
    }

    private static RxPersistentSortedSet newFromKeys(RxDatastoreClient client, Association association, List keys, QueryState queryState) {
        RxPersistentSortedSet.getDeclaredConstructor(RxDatastoreClient, Association, List, QueryState).newInstance(client, association, keys, queryState)
    }

    private static RxPersistentSortedSet newFromQuery(RxDatastoreClient client, Association association, Query query, QueryState queryState) {
        RxPersistentSortedSet.getDeclaredConstructor(RxDatastoreClient, Association, Query, QueryState).newInstance(client, association, query, queryState)
    }

    private Association createAssociation() {
        Stub(Association) {
            getAssociatedEntity() >> Stub(PersistentEntity) {
                getJavaClass() >> TestChild
                getIdentity() >> Stub(PersistentProperty) {
                    getName() >> 'id'
                }
            }
            getInverseSide() >> Stub(Association) {
                getName() >> 'owner'
            }
            getMapping() >> Stub(PropertyMapping) {
                getMappedForm() >> Stub(Property) {
                    isLazy() >> false
                }
            }
        }
    }
}
