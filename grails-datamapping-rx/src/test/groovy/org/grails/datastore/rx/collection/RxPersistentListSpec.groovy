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

class RxPersistentListSpec extends Specification {

    private static class TestChild {
    }

    void "constructing with an association key builds an equality query against the inverse side"() {
        given:
        Association association = createAssociation()
        Query query = Mock(Query, additionalInterfaces: [RxQuery])
        RxDatastoreClientImplementor client = Mock(RxDatastoreClientImplementor)

        when:
        RxPersistentList<TestChild> list = new RxPersistentList<>(client, association, 5L, new QueryState())

        then:
        1 * client.createQuery(TestChild, _ as QueryState) >> query
        1 * query.eq('owner', 5L) >> query
        1 * query.findAll() >> Observable.empty()
        list.datastoreClient.is(client)
        list.association.is(association)
        list.associationKeys == []
    }

    void "constructing with a collection of entity keys builds an inclusion query against the identifier"() {
        given:
        Association association = createAssociation()
        Query query = Mock(Query, additionalInterfaces: [RxQuery])
        RxDatastoreClientImplementor client = Mock(RxDatastoreClientImplementor)

        when:
        RxPersistentList<TestChild> list = newFromKeys(client, association, [1L, 2L], new QueryState())

        then:
        1 * client.createQuery(TestChild, _ as QueryState) >> query
        1 * query.in('id', [1L, 2L]) >> query
        1 * query.findAll() >> Observable.empty()
        list.associationKeys == [1L, 2L]
    }

    void "constructing with an initializer query resolves it directly without asking the client to build one"() {
        given:
        Association association = createAssociation()
        Query initializerQuery = Mock(Query, additionalInterfaces: [RxQuery])
        RxDatastoreClientImplementor client = Mock(RxDatastoreClientImplementor)

        when:
        RxPersistentList<TestChild> list = newFromQuery(client, association, initializerQuery, new QueryState())

        then:
        0 * client.createQuery(_, _)
        1 * initializerQuery.findAll() >> Observable.empty()
        list.associationKeys == []
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
        RxPersistentList<TestChild> list = new RxPersistentList<>(client, association, 5L, new QueryState())

        when:
        int size = list.size()
        boolean empty = list.isEmpty()
        Iterator iterator = list.iterator()

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
        RxPersistentList<TestChild> list = new RxPersistentList<>(client, association, 5L, new QueryState())

        when:
        list.size()

        then:
        thrown(BlockingOperationException)
    }

    private static RxPersistentList newFromKeys(RxDatastoreClient client, Association association, List keys, QueryState queryState) {
        RxPersistentList.getDeclaredConstructor(RxDatastoreClient, Association, List, QueryState).newInstance(client, association, keys, queryState)
    }

    private static RxPersistentList newFromQuery(RxDatastoreClient client, Association association, Query query, QueryState queryState) {
        RxPersistentList.getDeclaredConstructor(RxDatastoreClient, Association, Query, QueryState).newInstance(client, association, query, queryState)
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
