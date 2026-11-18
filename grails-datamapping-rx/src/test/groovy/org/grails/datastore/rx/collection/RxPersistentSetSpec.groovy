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
import spock.lang.Specification

class RxPersistentSetSpec extends Specification {

    private static class TestChild {
    }

    void "constructing with an association key builds an equality query against the inverse side"() {
        given:
        Association association = createAssociation()
        Query query = Mock(Query, additionalInterfaces: [RxQuery])
        RxDatastoreClientImplementor client = Mock(RxDatastoreClientImplementor)

        when:
        RxPersistentSet<TestChild> set = new RxPersistentSet<>(client, association, 5L, new QueryState())

        then:
        1 * client.createQuery(TestChild, _ as QueryState) >> query
        1 * query.eq('owner', 5L) >> query
        1 * query.findAll() >> Observable.empty()
        set.datastoreClient.is(client)
        set.association.is(association)
        set.associationKeys == []
    }

    void "constructing with an association key and a pre-populated target set uses that set as the backing collection"() {
        given:
        Association association = createAssociation()
        TestChild existingChild = new TestChild()
        RxDatastoreClientImplementor client = Stub(RxDatastoreClientImplementor) {
            createQuery(_, _) >> Stub(Query, additionalInterfaces: [RxQuery]) {
                findAll() >> Observable.empty()
            }
            isAllowBlockingOperations() >> true
        }

        when:
        RxPersistentSet<TestChild> set = new RxPersistentSet<>(client, association, 5L, [existingChild] as Set, new QueryState())

        then:
        set.contains(existingChild)
    }

    void "constructing with a collection of entity keys builds an inclusion query against the identifier"() {
        given:
        Association association = createAssociation()
        Query query = Mock(Query, additionalInterfaces: [RxQuery])
        RxDatastoreClientImplementor client = Mock(RxDatastoreClientImplementor)

        when:
        RxPersistentSet<TestChild> set = newFromKeys(client, association, [1L, 2L], new QueryState())

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
        RxPersistentSet<TestChild> set = newFromQuery(client, association, initializerQuery, new QueryState())

        then:
        0 * client.createQuery(_, _)
        1 * initializerQuery.findAll() >> Observable.empty()
        set.associationKeys == []
    }

    void "initialize subscribes to the resolved observable once no matter how many collection operations trigger it"() {
        given:
        Association association = createAssociation()
        int subscriptions = 0
        Observable<TestChild> observable = Observable.just(new TestChild()).doOnSubscribe { subscriptions++ }
        RxDatastoreClientImplementor client = Stub(RxDatastoreClientImplementor) {
            createQuery(_, _) >> Stub(Query, additionalInterfaces: [RxQuery]) {
                findAll() >> observable
            }
            isAllowBlockingOperations() >> true
        }
        RxPersistentSet<TestChild> set = new RxPersistentSet<>(client, association, 5L, new QueryState())

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
        RxPersistentSet<TestChild> set = new RxPersistentSet<>(client, association, 5L, new QueryState())

        when:
        set.size()

        then:
        thrown(BlockingOperationException)
    }

    private static RxPersistentSet newFromKeys(RxDatastoreClient client, Association association, List keys, QueryState queryState) {
        RxPersistentSet.getDeclaredConstructor(RxDatastoreClient, Association, List, QueryState).newInstance(client, association, keys, queryState)
    }

    private static RxPersistentSet newFromQuery(RxDatastoreClient client, Association association, Query query, QueryState queryState) {
        RxPersistentSet.getDeclaredConstructor(RxDatastoreClient, Association, Query, QueryState).newInstance(client, association, query, queryState)
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
