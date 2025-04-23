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

import grails.gorm.rx.collection.RxPersistentCollection
import grails.gorm.rx.collection.RxUnidirectionalCollection
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.grails.datastore.mapping.collection.PersistentSortedSet
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.rx.RxDatastoreClient
import org.grails.datastore.rx.exceptions.BlockingOperationException
import org.grails.datastore.rx.internal.RxDatastoreClientImplementor
import org.grails.datastore.rx.query.QueryState
import org.grails.datastore.rx.query.RxQuery
import rx.Observable
import rx.Subscriber
import rx.Subscription

/**
 * Represents a reactive sorted set that can be observed in order to allow non-blocking lazy loading of associations
 *
 * @author Graeme Rocher
 * @since 6.0
 */

@CompileStatic
@Slf4j
class RxPersistentSortedSet<T> extends PersistentSortedSet implements RxPersistentCollection<T>, RxUnidirectionalCollection, RxCollection<T> {
    final RxDatastoreClient datastoreClient
    final Association association

    protected final QueryState queryState

    RxPersistentSortedSet( RxDatastoreClient datastoreClient, Association association, Serializable associationKey, QueryState queryState = null) {
        super(association, associationKey, null)
        this.datastoreClient = datastoreClient
        this.association = association
        this.queryState = queryState
        this.observable = resolveObservable()
    }

    RxPersistentSortedSet( RxDatastoreClient datastoreClient, Association association, List<Serializable> entitiesKeys, QueryState queryState = null) {
        super(entitiesKeys, association.associatedEntity.javaClass, null)
        this.datastoreClient = datastoreClient
        this.association = association
        this.queryState = queryState
        this.observable = resolveObservable()
    }

    RxPersistentSortedSet( RxDatastoreClient datastoreClient, Association association, Query initializerQuery, QueryState queryState = null) {
        super(association, null, null)
        this.datastoreClient = datastoreClient
        this.association = association
        this.queryState = queryState
        this.observable = resolveObservable(initializerQuery)
    }

    protected Observable resolveObservable() {
        def query = ((RxDatastoreClientImplementor)datastoreClient).createQuery(childType, queryState)
        if(associationKey != null) {
            query.eq( association.inverseSide.name, associationKey )
        }
        else {
            query.in(association.associatedEntity.identity.name, keys.toList())
        }
        return resolveObservable(query)
    }

    protected Observable resolveObservable(Query query) {
        ((RxQuery) query).findAll()
    }

    @Override
    void initialize() {
        if(initializing != null) return
        initializing = true


        try {
            def observable = toListObservable()

            if(((RxDatastoreClientImplementor)datastoreClient).isAllowBlockingOperations()) {
                log.warn("Association $association initialised using blocking operation. Consider using subscribe(..) or an eager query instead")

                addAll observable.toBlocking().first()
            }
            else {
                throw new BlockingOperationException("Cannot initialize $association using a blocking operation. Use subscribe(..) instead.")
            }
        } finally {
            initializing = false
            initialized = true
        }
    }

    @Override
    Subscription subscribe(Subscriber subscriber) {
        return toObservable().subscribe(subscriber)
    }

    @Override
    Comparator comparator() {
        return ((SortedSet)collection).comparator()
    }

    @Override
    SortedSet subSet(Object fromElement, Object toElement) {
        return ((SortedSet)collection).subSet(fromElement, toElement)
    }

    @Override
    SortedSet headSet(Object toElement) {
        return ((SortedSet)collection).headSet(toElement)
    }

    @Override
    SortedSet tailSet(Object fromElement) {
        return ((SortedSet)collection).tailSet(fromElement)
    }

    @Override
    Object first() {
        return ((SortedSet)collection).first()
    }

    @Override
    Object last() {
        return ((SortedSet)collection).last()
    }

    @Override
    List<Serializable> getAssociationKeys() {
        if(keys != null) {
            return keys.toList() as List<Serializable>
        }
        else {
            return Collections.emptyList()
        }
    }
}
